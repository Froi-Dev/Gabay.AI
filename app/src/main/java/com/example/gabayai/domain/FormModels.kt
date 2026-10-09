package com.example.gabayai.domain

/** Coordinates in the upright OCR image, never in screen coordinates. */
data class OcrBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
}

/** A null confidence means the OCR provider did not supply a usable score. */
data class OcrLine(val text: String, val box: OcrBox, val confidence: Float? = null)

enum class GuideLanguage(val code: String, val displayName: String) {
    FILIPINO("fil", "Filipino / Taglish"),
    ENGLISH("en", "English"),
    CEBUANO("ceb", "Cebuano"),
    ILOCANO("ilo", "Ilocano"),
    HILIGAYNON("hil", "Hiligaynon"),
    BIKOL("bik", "Bikol");

    companion object {
        fun fromCode(code: String): GuideLanguage = entries.firstOrNull { it.code == code } ?: FILIPINO
    }
}

enum class TranslationReview { NOT_LINGUISTICALLY_REVIEWED, HUMAN_REVIEWED }
enum class Grounding { SOURCE_BACKED, GENERAL_VOCABULARY }
enum class MatchMethod { EXACT, FUZZY, SEMANTIC }

enum class PersonRole {
    FATHER, MOTHER, REQUESTER, CERTIFICATE_OWNER;

    /** Ordinary literal role vocabulary; current wording has no linguistic signoff. */
    fun label(language: GuideLanguage): String? = when (language) {
        GuideLanguage.FILIPINO -> when (this) {
            FATHER -> "Tatay"
            MOTHER -> "Nanay"
            REQUESTER -> "Taong humihiling"
            CERTIFICATE_OWNER -> "Taong kinukuhaan ng certificate"
        }
        GuideLanguage.ENGLISH -> when (this) {
            FATHER -> "Father"
            MOTHER -> "Mother"
            REQUESTER -> "Requester"
            CERTIFICATE_OWNER -> "Person on the certificate"
        }
        else -> null
    }
}

/** Actual printed section evidence, not an agency-policy or form-identity claim. */
data class FieldContext(val role: PersonRole, val originalLabel: String, val box: OcrBox)

data class Explanation(
    val language: GuideLanguage,
    val title: String,
    val text: String,
    val example: String?,
    val review: TranslationReview,
)

data class FieldSource(
    val grounding: Grounding,
    val excerpt: String?,
    val url: String?,
    val assetPath: String?,
    val sha256: String?,
    /** Project metadata, not evidence of an independent linguistic review. */
    val sourceReviewedOn: String?,
)

data class FieldDefinition(
    val id: String,
    val kind: String,
    val label: String,
    val aliases: List<String>,
    val explanations: List<Explanation>,
    val source: FieldSource,
    val formId: String? = null,
) {
    fun explanation(language: GuideLanguage): Explanation? = explanations.firstOrNull { it.language == language }
}

data class FormDefinition(
    val id: String,
    val name: String,
    val anchors: List<String>,
    val mandatoryAnchors: List<String>,
    val minimumAnchorCount: Int,
)

data class FieldMatch(
    val line: OcrLine,
    val field: FieldDefinition,
    val method: MatchMethod,
    val requiresConfirmation: Boolean,
    /** Similarity for rules, not a probability or AI accuracy measurement. */
    val score: Float,
    val context: FieldContext? = null,
)

data class RecognitionResult(
    val matches: List<FieldMatch>,
    val unknownLines: List<OcrLine>,
    val form: FormDefinition?,
    val warnings: List<String>,
) {
    val confirmedMatches: List<FieldMatch> get() = matches.filterNot { it.requiresConfirmation }
}
