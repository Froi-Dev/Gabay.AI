package com.example.gabayai.semantic

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.LongBuffer
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Compatibility research only: this class is packaged in the test APK, never in the application APK. */
@RunWith(AndroidJUnit4::class)
class BgeModelProbeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun createProbeDirectory() {
        val directory = context.filesDir
        check(directory.isDirectory || directory.mkdirs())
        report("BGE_PROBE_DIRECTORY=${directory.absolutePath}")
    }

    @Test fun cpuProbe() {
        val halfPrecision = InstrumentationRegistry.getArguments().getString("bgePrecision") == "fp16"
        val precision = if (halfPrecision) "FP16" else "FP32"
        val expectedBytes = if (halfPrecision) FP16_BYTES else FP32_BYTES
        val expectedSha = if (halfPrecision) FP16_SHA256 else FP32_SHA256
        val model = File(context.filesDir, "bge-probe-${precision.lowercase()}.onnx")
        // Ordinary semantic tests do not require the separate research fixture.
        assumeTrue("Manual BGE probe fixture has not been transferred", model.isFile)
        check(model.length() == expectedBytes) { "Transferred BGE model size failed" }
        val peakPss = AtomicLong(Debug.getPss())
        val reportedPss = AtomicLong(0)
        val sampling = Executors.newSingleThreadScheduledExecutor()
        sampling.scheduleAtFixedRate({
            val sampled = peakPss.updateAndGet { maxOf(it, Debug.getPss()) }
            if (sampled - reportedPss.get() >= 256_000) {
                reportedPss.set(sampled)
                report("BGE_${precision}_PSS_SAMPLE peak_kb=$sampled")
            }
        }, 0, 250, TimeUnit.MILLISECONDS)
        val beforePss = Debug.getPss()
        try {
            val verifyStarted = SystemClock.elapsedRealtime()
            check(sha256(model) == expectedSha) { "Transferred BGE model checksum failed" }
            val verifyMilliseconds = SystemClock.elapsedRealtime() - verifyStarted
            val tokenizer = context.assets.open("models/minilm/vocab.txt").bufferedReader().use {
                BertWordPieceTokenizer(it.readLines())
            }
            val environment = OrtEnvironment.getEnvironment()
            val firstStarted = SystemClock.elapsedRealtime()
            OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(2)
                options.setInterOpNumThreads(1)
                options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                options.setMemoryPatternOptimization(false)
                options.setCPUArenaAllocator(false)
                environment.createSession(model.absolutePath, options).use { session ->
                    val postal = embedding(environment, session, tokenizer, "postal code")
                    val firstMilliseconds = SystemClock.elapsedRealtime() - firstStarted
                    val warmStarted = SystemClock.elapsedRealtime()
                    val zip = embedding(environment, session, tokenizer, "zip code")
                    val warmMilliseconds = SystemClock.elapsedRealtime() - warmStarted
                    val birth = embedding(environment, session, tokenizer, "date of birth")
                    val repeated = embedding(environment, session, tokenizer, "postal code")
                    val related = cosine(postal, zip)
                    val unrelated = cosine(postal, birth)
                    assertEquals(1024, postal.size)
                    assertEquals(1.0, sqrt(postal.sumOf { it.toDouble() * it }), 0.0001)
                    assertTrue("BGE related labels must rank above unrelated labels: $related vs $unrelated", related > unrelated + 0.10f)
                    assertTrue(cosine(postal, repeated) > 0.999f)
                    report("BGE_${precision}_CPU_PASS model_bytes=${model.length()} dimensions=${postal.size} pooling=CLS verify_ms=$verifyMilliseconds first_session_ms=$firstMilliseconds warm_ms=$warmMilliseconds baseline_pss_kb=$beforePss loaded_pss_kb=${Debug.getPss()} sampled_peak_pss_kb=${peakPss.get()} postal_zip_cosine=$related postal_birth_cosine=$unrelated")
                    for ((query, expected, other) in listOf(
                        Triple("home address", "residential address", "date of birth"),
                        Triple("family name", "surname", "phone number"),
                        Triple("mobile contact", "phone number", "occupation"),
                    )) {
                        val queryVector = embedding(environment, session, tokenizer, query)
                        val expectedScore = cosine(queryVector, embedding(environment, session, tokenizer, expected))
                        val otherScore = cosine(queryVector, embedding(environment, session, tokenizer, other))
                        report("BGE_LABEL_COMPARISON query=$query related=$expected cosine=$expectedScore unrelated=$other cosine=$otherScore")
                        assertTrue(expectedScore > otherScore)
                    }
                }
            }
        } catch (failure: Throwable) {
            report("BGE_${precision}_CPU_FAILURE type=${failure.javaClass.name} message=${failure.message} sampled_peak_pss_kb=${peakPss.get()}")
            throw failure
        } finally {
            sampling.shutdownNow()
            report("BGE_${precision}_CPU_AFTER_CLOSE pss_kb=${Debug.getPss()} sampled_peak_pss_kb=${peakPss.get()}")
        }
    }

    private fun embedding(environment: OrtEnvironment, session: OrtSession, tokenizer: BertWordPieceTokenizer, text: String): FloatArray {
        val ids = tokenizer.encode(text)
        val shape = longArrayOf(1, ids.size.toLong())
        val inputs = linkedMapOf<String, OnnxTensor>()
        try {
            inputs["input_ids"] = OnnxTensor.createTensor(environment, LongBuffer.wrap(ids), shape)
            inputs["attention_mask"] = OnnxTensor.createTensor(environment, LongBuffer.wrap(LongArray(ids.size) { 1 }), shape)
            if ("token_type_ids" in session.inputNames) inputs["token_type_ids"] = OnnxTensor.createTensor(environment, LongBuffer.wrap(LongArray(ids.size)), shape)
            session.run(inputs).use { output ->
                val tensor = output.get(0) as OnnxTensor
                require((tensor.info as TensorInfo).shape.contentEquals(longArrayOf(1, ids.size.toLong(), 1024)))
                val values = requireNotNull(tensor.floatBuffer)
                val cls = FloatArray(1024) { values.get(it).also { value -> require(value.isFinite()) } }
                val magnitude = sqrt(cls.sumOf { it.toDouble() * it })
                require(magnitude.isFinite() && magnitude > 1e-9)
                return FloatArray(1024) { (cls[it] / magnitude).toFloat() }
            }
        } finally { inputs.values.forEach { it.close() } }
    }

    private fun cosine(first: FloatArray, second: FloatArray) = first.indices.sumOf { first[it].toDouble() * second[it] }.toFloat()

    private fun report(message: String) = instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\n$message\n") })

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            var count = input.read(buffer)
            while (count >= 0) {
                if (count > 0) digest.update(buffer, 0, count)
                count = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val FP16_BYTES = 668_718_129L
        private const val FP16_SHA256 = "9ae72817201be6949aa1f454272ace916cc740f469420e04b4c8365f0466945c"
        private const val FP32_BYTES = 1_336_749_542L
        private const val FP32_SHA256 = "deb82329ff0e9725a0d8407e434be40a7793725ce4e3032d8091f615130d0105"
    }
}
