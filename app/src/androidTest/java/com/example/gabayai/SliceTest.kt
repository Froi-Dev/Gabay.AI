package com.example.gabayai

import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.gabayai.data.FormDatabase
import com.example.gabayai.data.FormRepository
import com.example.gabayai.data.SeedContent
import com.example.gabayai.domain.Grounding
import com.example.gabayai.domain.GuideLanguage
import com.example.gabayai.domain.MatchMethod
import com.example.gabayai.domain.TranslationReview
import com.example.gabayai.imaging.ImageRepository
import com.example.gabayai.imaging.OfflineOcr
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real bundled OCR + real Room; this public fixture is not a camera/device accuracy benchmark. */
@RunWith(AndroidJUnit4::class)
class SliceTest {
    @Test
    fun bundledPublicFormTraversesImageOcrRoomAndSourceBackedExplanation() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val database = Room.inMemoryDatabaseBuilder(context, FormDatabase::class.java).build()
        val fixture = File.createTempFile("slice-public-", ".png", context.cacheDir)
        try {
            context.assets.open("sample/psa-birth.png").use { input ->
                fixture.outputStream().use { output -> input.copyTo(output) }
            }
            val preparationStarted = SystemClock.elapsedRealtime()
            ImageRepository(context).prepare(Uri.fromFile(fixture)).use { photo ->
                val preparationMs = SystemClock.elapsedRealtime() - preparationStarted
                assertTrue(photo.width > 0 && photo.height > 0)
                assertTrue(maxOf(photo.width, photo.height) <= ImageRepository.MAX_LONG_EDGE)

                OfflineOcr().use { ocr ->
                    val ocrStarted = SystemClock.elapsedRealtime()
                    val lines = ocr.recognize(photo.bitmap)
                    val ocrMs = SystemClock.elapsedRealtime() - ocrStarted
                    assertFalse("Real ML Kit must read this bundled public form.", lines.isEmpty())
                    assertTrue(lines.all { line ->
                        line.box.left >= 0 && line.box.top >= 0 &&
                            line.box.right <= photo.width && line.box.bottom <= photo.height
                    })

                    val matchingStarted = SystemClock.elapsedRealtime()
                    val repository = FormRepository(database)
                    val result = repository.recognize(lines)
                    val matchingMs = SystemClock.elapsedRealtime() - matchingStarted
                    assertEquals("Independent public form anchors must identify the PSA template.", SeedContent.PSA_FORM_ID, result.form?.id)
                    val dob = result.matches.single { it.field.id == "psa.birth_date" }
                    assertEquals(MatchMethod.EXACT, dob.method)
                    assertFalse(dob.requiresConfirmation)
                    assertEquals(Grounding.SOURCE_BACKED, dob.field.source.grounding)
                    assertEquals("Date of Birth / Month / Day / Year", dob.field.source.excerpt)
                    assertEquals("Enter the person’s birth month, day, and year.", dob.field.explanation(GuideLanguage.ENGLISH)?.text)
                    assertEquals(TranslationReview.NOT_LINGUISTICALLY_REVIEWED, dob.field.explanation(GuideLanguage.FILIPINO)?.review)

                    // Check the stored rows through a fresh repository, rather than
                    // treating bundled in-memory definitions as a database test.
                    val persistedFields = FormRepository(database).definitions()
                    assertEquals(30, persistedFields.size)
                    assertEquals(6, persistedFields.count { it.source.grounding == Grounding.SOURCE_BACKED })
                    assertEquals(24, persistedFields.count { it.source.grounding == Grounding.GENERAL_VOCABULARY })
                    assertEquals(60, persistedFields.sumOf { it.explanations.size })
                    assertTrue(persistedFields.all { it.explanation(GuideLanguage.CEBUANO) == null })
                    val persistedDob = persistedFields.single { it.id == dob.field.id }
                    assertEquals(dob.field, persistedDob)
                    val expectedHash = "c777a6389c78cd6fa1fd96281b8094e1a0dba7abe41d0672749892dd70a24060"
                    assertEquals(expectedHash, persistedDob.source.sha256)
                    val digest = MessageDigest.getInstance("SHA-256")
                    context.assets.open(requireNotNull(persistedDob.source.assetPath)).use { source ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = source.read(buffer)
                            if (count == -1) break
                            digest.update(buffer, 0, count)
                        }
                    }
                    val bundledHash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                    assertEquals("The installed PDF must match the audited source.", expectedHash, bundledHash)

                    // Only aggregate metrics for this identified blank public input.
                    // No OCR label, uploaded document or personal value is logged.
                    val metrics = JSONObject().apply {
                        put("fixture", "bundled-public-psa-birth")
                        put("preparation_ms", preparationMs)
                        put("ocr_ms", ocrMs)
                        put("room_seed_and_matching_ms", matchingMs)
                        put("width", photo.width)
                        put("height", photo.height)
                        put("ocr_line_count", lines.size)
                        put("matched_fields", result.matches.size)
                        put("source_pdf_hash_valid", true)
                        put("physical_camera_test", false)
                    }.toString()
                    Log.i("GabaySliceMetrics", metrics)
                    instrumentation.sendStatus(0, Bundle().apply { putString("gabay_slice_metrics", metrics) })
                }
            }
        } finally {
            fixture.delete()
            database.close()
        }
    }
}
