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
import com.example.gabayai.data.AliasEntity
import com.example.gabayai.data.SeedContent
import com.example.gabayai.domain.Grounding
import com.example.gabayai.domain.GuideLanguage
import com.example.gabayai.domain.PersonRole
import com.example.gabayai.imaging.ImageRepository
import com.example.gabayai.imaging.OfflineOcr
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The user's actual blank cropped photo, not simulated OCR or a generated form. */
@RunWith(AndroidJUnit4::class)
class ParentSectionRegressionTest {
    @Test
    fun aRetiredPublicDefinitionAndItsAliasesAreRemovedOnCatalogueReseed() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, FormDatabase::class.java).build()
        try {
            FormRepository(database).definitions()
            val dao = database.formDao()
            val retiredId = "retired.test"
            dao.insertFields(listOf(dao.fields().first().copy(id = retiredId, formId = null)))
            dao.insertAliases(listOf(AliasEntity(retiredId, "Retired public alias")))
            dao.insertExplanations(listOf(dao.explanations().first().copy(fieldId = retiredId)))
            val replacement = FormRepository(database).definitions()
            assertEquals(SeedContent.fields.map { it.id }.toSet(), replacement.map { it.id }.toSet())
            assertTrue(dao.aliases().none { it.fieldId == retiredId })
            assertTrue(dao.explanations().none { it.fieldId == retiredId })
        } finally {
            database.close()
        }
    }

    @Test
    fun realParentCropUsesPrintedFatherAndMotherSectionsWithoutGovernmentIdentityOrDob() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val database = Room.inMemoryDatabaseBuilder(context, FormDatabase::class.java).build()
        val fixture = File.createTempFile("parent-section-regression-", ".png", context.cacheDir)
        try {
            instrumentation.context.assets.open("parent-section.png").use { input ->
                fixture.outputStream().use { output -> input.copyTo(output) }
            }
            ImageRepository(context).prepare(Uri.fromFile(fixture)).use { photo ->
                OfflineOcr().use { ocr ->
                    val ocrStarted = SystemClock.elapsedRealtime()
                    val lines = ocr.recognize(photo.bitmap)
                    val ocrMs = SystemClock.elapsedRealtime() - ocrStarted
                    assertFalse("Actual ML Kit must read the supplied blank parent-section photograph.", lines.isEmpty())
                    val matchingStarted = SystemClock.elapsedRealtime()
                    val result = FormRepository(database).recognize(lines)
                    val matchingMs = SystemClock.elapsedRealtime() - matchingStarted
                    assertNull("A cropped section cannot establish the complete PSA form identity.", result.form)
                    assertTrue("Known parent-name vocabulary must receive a usable general explanation.", result.matches.isNotEmpty())
                    assertTrue(result.matches.all { it.field.formId == null && it.field.source.grounding == Grounding.GENERAL_VOCABULARY })
                    val names = result.matches.filter { it.field.kind in setOf("first_name", "last_name", "middle_name", "full_name") }
                    assertTrue("The crop must produce relevant name guidance without a birth-date prerequisite.", names.size >= 3)
                    assertTrue(names.any { it.field.kind == "first_name" })
                    assertTrue(names.any { it.field.kind == "last_name" })
                    assertTrue(result.matches.none { it.field.kind == "birth_date" })
                    assertTrue("The parent's labels must never use certificate-owner/requester wording.", names.all { match ->
                        val english = requireNotNull(match.field.explanation(GuideLanguage.ENGLISH)).text.lowercase()
                        "certificate" !in english && "requester" !in english && "child's" !in english
                    })
                    // Explicitly authorized regression research on this identified
                    // blank user-supplied fixture only. Production OCR never writes
                    // text; no arbitrary captured document reaches this debug dump.
                    val golden = JSONObject().apply {
                        put("fixture", "user-blank-parent-section")
                        put("width", photo.width)
                        put("height", photo.height)
                        put("lines", JSONArray().apply {
                            lines.forEach { line -> put(JSONObject().apply {
                                put("text", line.text)
                                put("box", JSONArray(listOf(line.box.left, line.box.top, line.box.right, line.box.bottom)))
                                put("confidence", line.confidence ?: JSONObject.NULL)
                            }) }
                        })
                        put("matches", JSONArray().apply {
                            result.matches.forEach { match -> put(JSONObject().apply {
                                put("field_id", match.field.id)
                                put("label", match.line.text)
                                put("box", JSONArray(listOf(match.line.box.left, match.line.box.top, match.line.box.right, match.line.box.bottom)))
                                put("method", match.method.name)
                                put("context_role", match.context?.role?.name ?: JSONObject.NULL)
                                put("context_label", match.context?.originalLabel ?: JSONObject.NULL)
                            }) }
                        })
                    }
                    val goldenFile = File(context.cacheDir, "parent-section-public-golden.json")
                    goldenFile.writeText(golden.toString(2))
                    instrumentation.sendStatus(0, Bundle().apply { putString("gabay_public_fixture_golden", goldenFile.absolutePath) })
                    assertTrue("Readable father section must contextualize all three printed name labels.",
                        names.filter { it.context?.role == PersonRole.FATHER }.map { it.field.kind }.toSet() ==
                            setOf("first_name", "last_name", "middle_name"))
                    assertTrue("Readable mother section must contextualize all three printed name labels.",
                        names.filter { it.context?.role == PersonRole.MOTHER }.map { it.field.kind }.toSet() ==
                            setOf("first_name", "last_name", "middle_name"))
                    assertTrue("Purpose is a separate section, not a parent's personal field.",
                        result.matches.filter { it.field.kind == "purpose" }.all { it.context == null })
                    val metrics = JSONObject().apply {
                        put("fixture", "user-blank-parent-section")
                        put("ocr_ms", ocrMs)
                        put("room_and_matching_ms", matchingMs)
                        put("ocr_line_count", lines.size)
                        put("matched_fields", result.matches.size)
                        put("name_fields", names.size)
                        put("father_name_fields", names.count { it.context?.role == PersonRole.FATHER })
                        put("mother_name_fields", names.count { it.context?.role == PersonRole.MOTHER })
                        put("source_backed_fields", result.matches.count { it.field.source.grounding == Grounding.SOURCE_BACKED })
                        put("form_identity_confirmed", result.form != null)
                    }.toString()
                    Log.i("GabayParentCropMetrics", metrics)
                    instrumentation.sendStatus(0, Bundle().apply { putString("gabay_parent_crop_metrics", metrics) })
                }
            }
        } finally {
            fixture.delete()
            database.close()
        }
    }
}
