package com.example.gabayai.data

import android.content.Context
import androidx.room.withTransaction
import com.example.gabayai.domain.Explanation
import com.example.gabayai.domain.FieldDefinition
import com.example.gabayai.domain.FieldMatcher
import com.example.gabayai.domain.FieldSource
import com.example.gabayai.domain.FormDefinition
import com.example.gabayai.domain.Grounding
import com.example.gabayai.domain.GuideLanguage
import com.example.gabayai.domain.OcrLine
import com.example.gabayai.domain.RecognitionResult
import com.example.gabayai.domain.TranslationReview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** Only public definitions persist. Photos, OCR text and answers are never stored here. */
class FormRepository(private val database: FormDatabase) {
    constructor(context: Context) : this(FormDatabase.get(context))

    private data class Catalogue(val fields: List<FieldDefinition>, val forms: List<FormDefinition>)
    private data class StoredRows(
        val forms: List<FormEntity>,
        val fields: List<FieldEntity>,
        val aliases: List<AliasEntity>,
        val explanations: List<ExplanationEntity>,
    ) {
        fun sameContent(other: StoredRows): Boolean =
            forms.toSet() == other.forms.toSet() && fields.toSet() == other.fields.toSet() &&
                aliases.toSet() == other.aliases.toSet() && explanations.toSet() == other.explanations.toSet()
    }

    private val matcher = FieldMatcher()
    private val catalogueMutex = Mutex()
    @Volatile private var cachedCatalogue: Catalogue? = null

    suspend fun recognize(lines: List<OcrLine>): RecognitionResult = withContext(Dispatchers.Default) {
        val catalogue = loadCatalogue()
        matcher.recognize(lines, catalogue.fields, catalogue.forms)
    }

    suspend fun definitions(): List<FieldDefinition> = loadCatalogue().fields
    suspend fun forms(): List<FormDefinition> = loadCatalogue().forms

    private suspend fun loadCatalogue(): Catalogue {
        cachedCatalogue?.let { return it }
        return catalogueMutex.withLock {
            cachedCatalogue ?: withContext(Dispatchers.IO) {
                database.withTransaction {
                    val dao = database.formDao()
                    val expected = bundledRows()
                    var stored = readRows(dao)
                    // Compare every public row, including text, aliases, provenance
                    // and review status. Equal counts alone cannot detect a wording
                    // fix. Avoid rewriting 200+ unchanged rows at every app start.
                    if (!stored.sameContent(expected)) {
                        // The catalogue is immutable public bundled content.
                        // Remove retired rows too; REPLACE alone would leave a
                        // withdrawn generic definition or alias recognizable.
                        // Cascades clear aliases/explanations in this transaction.
                        dao.deleteFields()
                        dao.deleteForms()
                        dao.insertForms(expected.forms)
                        dao.insertFields(expected.fields)
                        dao.insertAliases(expected.aliases)
                        dao.insertExplanations(expected.explanations)
                        stored = readRows(dao)
                    }
                    toCatalogue(stored)
                }
            }.also { cachedCatalogue = it }
        }
    }

    private suspend fun readRows(dao: FormDao): StoredRows =
        StoredRows(dao.forms(), dao.fields(), dao.aliases(), dao.explanations())

    private fun bundledRows(): StoredRows = StoredRows(
        forms = SeedContent.forms.map { form ->
            FormEntity(form.id, form.name, JSONArray(form.anchors).toString(), JSONArray(form.mandatoryAnchors).toString(), form.minimumAnchorCount)
        },
        fields = SeedContent.fields.map { field ->
            FieldEntity(field.id, field.kind, field.label, field.formId, field.source.grounding.name,
                field.source.excerpt, field.source.url, field.source.assetPath, field.source.sha256, field.source.sourceReviewedOn)
        },
        aliases = SeedContent.fields.flatMap { field -> field.aliases.map { AliasEntity(field.id, it) } },
        explanations = SeedContent.fields.flatMap { field ->
            field.explanations.map { ExplanationEntity(field.id, it.language.code, it.title, it.text, it.example, it.review.name) }
        },
    )

    private fun toCatalogue(rows: StoredRows): Catalogue {
        val aliases = rows.aliases.groupBy { it.fieldId }
        val explanations = rows.explanations.groupBy { it.fieldId }
        return Catalogue(
            fields = rows.fields.map { field ->
                FieldDefinition(
                    id = field.id, kind = field.kind, label = field.label, formId = field.formId,
                    aliases = aliases[field.id].orEmpty().map { it.value },
                    explanations = explanations[field.id].orEmpty().map { row ->
                        Explanation(GuideLanguage.fromCode(row.language), row.title, row.text, row.example,
                            TranslationReview.valueOf(row.translationReview))
                    },
                    source = FieldSource(Grounding.valueOf(field.grounding), field.sourceExcerpt, field.sourceUrl,
                        field.sourceAsset, field.sourceSha256, field.sourceReviewedOn),
                )
            },
            forms = rows.forms.map { row ->
                FormDefinition(row.id, row.name, row.anchorsJson.asStringList(),
                    row.mandatoryAnchorsJson.asStringList(), row.minimumAnchorCount)
            },
        )
    }

    private fun String.asStringList(): List<String> =
        JSONArray(this).let { array -> List(array.length()) { array.getString(it) } }
}

