package com.example.gabayai.semantic

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.app.ActivityManager
import com.example.gabayai.domain.FieldDefinition
import com.example.gabayai.domain.FieldMatch
import com.example.gabayai.domain.MatchMethod
import com.example.gabayai.domain.OcrLine
import com.example.gabayai.domain.FieldMatcher
import java.io.File
import java.nio.LongBuffer
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.sqrt
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Real CPU inference. English field-label suggestions only; no generation, policy inference, or auto-accept. */
internal class OnnxSemanticEngine(
    private val context: Context,
    private val model: SemanticModel = SemanticModel.MINILM,
) : SemanticEngine {
    override val available = true
    override val modelName = model.displayName
    override val modelStorageBytes = model.bytes
    internal val embeddingDimensions get() = model.dimensions
    private val closed = AtomicBoolean(false)
    private val lock = ReentrantLock()
    private var session: OrtSession? = null
    private var tokenizer: BertWordPieceTokenizer? = null
    private val fieldVectors = LinkedHashMap<String, FloatArray>()
    private val publicVectorCache = PublicFieldVectorCache(context, model)

    override suspend fun suggest(lines: List<OcrLine>, fields: List<FieldDefinition>, onProgress: (SemanticProgress) -> Unit): List<FieldMatch> = withContext(Dispatchers.Default) {
        val coroutine = currentCoroutineContext()
        lock.withLock {
            checkOpen()
            try {
                val labels = lines.asSequence().take(MAXIMUM_INPUT_LINES)
                    .filter(::looksLikeLabel).distinct()
                    .take(MAXIMUM_LABELS).toList()
                // Source-specific government roles are deliberately excluded, even if called incorrectly.
                val candidates = fields.asSequence().filter { it.formId == null }
                    .distinctBy { it.id }.take(MAXIMUM_FIELDS).toList()
                if (labels.isEmpty() || candidates.size < 2) return@withLock emptyList()

                onProgress(SemanticProgress.PREPARING_MODEL)

                val candidateVectors = candidates.map { field ->
                    coroutine.ensureActive()
                    checkOpen()
                    val key = "${field.id}\u0000${field.label}"
                    val vector = fieldVectors.getOrPut(key) {
                        publicVectorCache.read(field) ?: embedLocked(field.label, coroutine).also { publicVectorCache.write(field, it) }
                    }
                    field to vector
                }
                // Bound memory if a future caller supplies changing catalogues.
                val validKeys = candidates.mapTo(HashSet()) { "${it.id}\u0000${it.label}" }
                fieldVectors.keys.retainAll(validKeys)

                val suggestions = ArrayList<FieldMatch>()
                val queryVectors = HashMap<String, FloatArray>()
                onProgress(SemanticProgress.MATCHING)
                for (line in labels) {
                    coroutine.ensureActive()
                    checkOpen()
                    val text = line.text.trim().removeSuffix(":")
                    val vector = queryVectors.getOrPut(text) { embedLocked(text, coroutine) }
                    val ranked = candidateVectors.map { (field, candidate) -> field to cosine(vector, candidate) }
                        .sortedByDescending { it.second }
                    val best = ranked[0]
                    val next = ranked[1]
                    // These are conservative suggestion gates, not calibrated probabilities.
                    if (best.second >= model.minimumCosine && best.second - next.second >= model.minimumMargin) {
                        suggestions += FieldMatch(line, best.first, MatchMethod.SEMANTIC, requiresConfirmation = true, score = best.second)
                    }
                    if (suggestions.size == MAXIMUM_SUGGESTIONS) break
                }
                suggestions
            } catch (failure: Throwable) {
                releaseLocked()
                throw failure
            } finally {
                if (closed.get() || model.releaseSessionAfterSuggestion) releaseLocked()
            }
        }
    }

    /** Instrumentation exercises this same path; no test-only inference implementation exists. */
    internal suspend fun embedding(text: String): FloatArray = withContext(Dispatchers.Default) {
        val coroutine = currentCoroutineContext()
        coroutine.ensureActive()
        lock.withLock {
            checkOpen()
            try { embedLocked(text, coroutine) } finally { if (closed.get()) releaseLocked() }
        }
    }

    private fun embedLocked(text: String, coroutine: CoroutineContext): FloatArray {
        coroutine.ensureActive()
        checkOpen()
        val activeSession = session ?: createSessionLocked(coroutine).also { session = it }
        val ids = requireNotNull(tokenizer).encode(text)
        val shape = longArrayOf(1L, ids.size.toLong())
        val environment = OrtEnvironment.getEnvironment()
        val inputs = LinkedHashMap<String, OnnxTensor>()
        try {
            inputs["input_ids"] = OnnxTensor.createTensor(environment, LongBuffer.wrap(ids), shape)
            inputs["attention_mask"] = OnnxTensor.createTensor(environment, LongBuffer.wrap(LongArray(ids.size) { 1L }), shape)
            if ("token_type_ids" in activeSession.inputNames) {
                inputs["token_type_ids"] = OnnxTensor.createTensor(environment, LongBuffer.wrap(LongArray(ids.size)), shape)
            }
            require(inputs.keys == activeSession.inputNames) { "Bundled model input contract changed" }
            activeSession.run(inputs).use { result ->
                val output = result.get(0) as? OnnxTensor ?: error("Model output is not a tensor")
                val outputShape = (output.info as TensorInfo).shape
                require(outputShape.contentEquals(longArrayOf(1, ids.size.toLong(), model.dimensions.toLong()))) {
                    "Bundled model output dimensions changed"
                }
                val values = output.floatBuffer ?: error("Model output is not float32")
                val pooled = FloatArray(model.dimensions)
                if (model.pooling == Pooling.CLS) {
                    // BGE's publisher specifies the first token's last hidden state, not mean pooling.
                    for (dimension in pooled.indices) {
                        pooled[dimension] = values.get(dimension).also { require(it.isFinite()) { "Non-finite local embedding" } }
                    }
                } else {
                    // MiniLM: no padding in single-label inference, so every attention-mask value is 1.
                    for (token in ids.indices) for (dimension in pooled.indices) {
                        val value = values.get(token * model.dimensions + dimension)
                        require(value.isFinite()) { "Non-finite embedding from local model" }
                        pooled[dimension] += value / ids.size
                    }
                }
                val magnitude = sqrt(pooled.sumOf { it.toDouble() * it })
                require(magnitude.isFinite() && magnitude > 1e-9) { "Invalid local embedding magnitude" }
                return FloatArray(model.dimensions) { (pooled[it] / magnitude).toFloat() }
            }
        } finally {
            inputs.values.forEach { it.close() }
        }
    }

    private fun createSessionLocked(coroutine: CoroutineContext): OrtSession {
        val modelFile = prepareModelFile(coroutine)
        val memory = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        val reserve = if (model.bytes > 512L * 1024 * 1024) model.bytes + 256L * 1024 * 1024 else 64L * 1024 * 1024
        if (memory.lowMemory || memory.availMem < reserve) throw SemanticResourceException(SemanticResourceProblem.MEMORY)
        coroutine.ensureActive()
        tokenizer = context.assets.open("${model.assetDirectory}/vocab.txt").bufferedReader(Charsets.UTF_8).use {
            BertWordPieceTokenizer(it.readLines())
        }
        return OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(2)
            options.setInterOpNumThreads(1)
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            options.setMemoryPatternOptimization(false)
            options.setCPUArenaAllocator(false)
            // CPU is deliberate: no NNAPI/GPU driver dependence or unsupported quantized-op fallback.
            val opened = OrtEnvironment.getEnvironment().createSession(modelFile.absolutePath, options)
            try { coroutine.ensureActive(); opened } catch (failure: Throwable) { opened.close(); throw failure }
        }
    }

    private fun prepareModelFile(coroutine: CoroutineContext): File {
        val directory = File(context.noBackupFilesDir, "semantic-models").apply {
            check(isDirectory || mkdirs()) { "Could not prepare local model storage" }
        }
        val modelFile = File(directory, model.cacheFileName)
        if (modelFile.isFile && modelFile.length() == model.bytes && sha256(modelFile, coroutine) == model.sha256) return modelFile
        if (modelFile.exists()) check(modelFile.delete()) { "Could not remove corrupted model cache" }
        if (directory.usableSpace < model.bytes + 64L * 1024 * 1024) throw SemanticResourceException(SemanticResourceProblem.STORAGE)
        val pending = File(directory, "${model.cacheFileName}.pending")
        try {
            context.assets.open("${model.assetDirectory}/${model.assetFileName}").use { input ->
                pending.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutine.ensureActive()
                        checkOpen()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
            }
            check(pending.length() == model.bytes && sha256(pending, coroutine) == model.sha256) { "Bundled semantic model checksum failed" }
            check(!modelFile.exists() || modelFile.delete()) { "Could not replace corrupted model cache" }
            check(pending.renameTo(modelFile)) { "Could not commit local model file" }
            return modelFile
        } finally {
            pending.delete()
        }
    }

    override fun close() {
        closed.set(true)
        // Never wait for a native call on the UI thread. Its finally block performs cleanup instead.
        if (lock.tryLock()) {
            try { releaseLocked() } finally { lock.unlock() }
        }
    }

    private fun releaseLocked() {
        val previous = session
        session = null
        tokenizer = null
        fieldVectors.clear()
        previous?.close()
        // OrtEnvironment is ONNX Runtime's shared process singleton; this engine owns only its session.
    }

    private fun checkOpen() = check(!closed.get()) { "Semantic engine is closed" }

    companion object {
        const val EMBEDDING_DIMENSIONS = 384
        const val MODEL_BYTES = 23_026_053L
        const val MODEL_SHA256 = "4278337fd0ff3c68bfb6291042cad8ab363e1d9fbc43dcb499fe91c871902474"
        private const val MAXIMUM_INPUT_LINES = 160
        private const val MAXIMUM_LABELS = 12
        private const val MAXIMUM_FIELDS = 32
        private const val MAXIMUM_SUGGESTIONS = 5

        internal fun cosine(first: FloatArray, second: FloatArray): Float {
            require(first.isNotEmpty() && second.size == first.size)
            return first.indices.sumOf { first[it].toDouble() * second[it] }.toFloat().coerceIn(-1f, 1f)
        }

        private fun sha256(file: File, coroutine: CoroutineContext): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream ->
                val buffer = ByteArray(64 * 1024)
                var count = stream.read(buffer)
                while (count >= 0) {
                    coroutine.ensureActive()
                    if (count > 0) digest.update(buffer, 0, count)
                    count = stream.read(buffer)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        internal fun looksLikeLabel(line: OcrLine): Boolean {
            if (line.text.length !in 3..84) return false
            val text = line.text.trim().removeSuffix(":").trim()
            if (text.length !in 3..80 || (line.confidence != null && (!line.confidence.isFinite() || line.confidence !in FieldMatcher.MIN_CONFIDENCE..1f))) return false
            if (text.any(Char::isDigit) || text.any { it in "@:;=<>\\\n\r" }) return false
            val words = Regex("[a-z]+").findAll(text.lowercase(Locale.ROOT)).map { it.value }.toList()
            if (words.size !in 1..10 || words.any { it in EXCLUDED_WORDS }) return false
            // A conservative rule filter prevents arbitrary names/values/headings from being interpreted as labels.
            return words.any { it in LABEL_WORDS }
        }

        private val EXCLUDED_WORDS = setOf(
            "requirement", "requirements", "required", "must", "submit", "bring", "document", "documents",
            "certificate", "certification", "application", "instruction", "instructions", "please", "pay", "fee",
            "fees", "read", "ignore", "system", "assistant", "prompt", "applicant", "fill", "complete", "sign",
            "republic", "philippines", "government", "department", "authority", "office", "request", "warning",
            "proof", "supporting", "photocopy", "photocopies", "copy", "original", "valid", "attach", "attached",
            "provide", "issued", "affidavit", "list", "checklist", "should", "legal",
        )
        private val LABEL_WORDS = setOf(
            "name", "surname", "family", "first", "given", "maiden", "middle", "gender", "sex", "birth", "born",
            "birthday", "nationality", "citizenship", "nation", "home", "residence", "residential", "permanent",
            "postal", "zip", "mobile", "telephone", "phone", "contact", "email", "electronic", "occupation", "work",
            "employment", "civil", "marital", "relationship", "status", "place", "location", "address", "street",
            "city", "municipality", "province", "region", "country", "barangay", "unit", "number", "house", "age",
            "when", "where", "spouse", "father", "mother", "parent", "signature", "date", "code",
            "school", "education", "course", "program", "employer", "company", "job", "purpose", "reason",
            "identification", "reference",
        )
    }
}
