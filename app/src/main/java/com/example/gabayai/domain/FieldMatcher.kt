package com.example.gabayai.domain

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/** Deterministic label recognition. These rules do not run an AI model. */
class FieldMatcher {
    private data class CompiledField(
        val field: FieldDefinition,
        val aliases: Set<String>,
        val canonical: String,
        val firstNameExcerpt: String?,
    )
    private data class PreparedLine(
        val line: OcrLine,
        val label: String,
        val normalized: String,
        val tokens: Set<String>,
        val wordCount: Int,
        val paddedLabel: String,
        val instruction: Boolean,
        val header: Boolean,
    )

    fun recognize(
        lines: List<OcrLine>,
        definitions: List<FieldDefinition>,
        forms: List<FormDefinition>,
    ): RecognitionResult {
        val bounded = mutableListOf<OcrLine>()
        val unknown = mutableListOf<OcrLine>()
        val warnings = mutableListOf<String>()
        var consumed = 0
        var capped = false
        for (line in lines.filter { it.text.isNotBlank() }) {
            if (bounded.size >= MAX_LINES || consumed + line.text.length > MAX_TEXT) {
                capped = true
                unknown += line
                continue
            }
            if (line.confidence != null && (!line.confidence.isFinite() || line.confidence < MIN_CONFIDENCE || line.confidence > 1f)) {
                unknown += line
                continue
            }
            consumed += line.text.length
            bounded += line
        }
        // Normalization and tokenization happen once, including labels examined
        // again by the spatial guards. No Regex is constructed inside a field loop.
        val prepared = bounded.map(::prepareLine)
        val recognizedForms = forms.filter { form ->
            val anchors = form.anchors.map(::normalize)
            val found = anchors.filter { anchor -> prepared.any { containsPhrase(it.normalized, anchor) } }
            found.size >= form.minimumAnchorCount && form.mandatoryAnchors.map(::normalize).all { it in found }
        }
        val form = recognizedForms.singleOrNull()
        val sections = SectionContextResolver().resolve(bounded, prepared.associate { it.line to it.normalized })
        val compiled = definitions.map { field ->
            CompiledField(field, field.aliases.map(::normalize).toSet(), normalize(field.label),
                if (field.kind == "first_name") field.source.excerpt?.let(::normalize) else null)
        }
        val generic = compiled.filter { it.field.formId == null }
        val scoped = compiled.filter { it.field.formId == form?.id && it.field.formId != null }
        val wholeGeneralAliases = generic.flatMap { it.aliases }.toSet()
        val genericCache = mutableMapOf<String, List<FieldDefinition>>()
        val fuzzyCache = mutableMapOf<String, List<Pair<FieldDefinition, Float>>>()
        val usedSourceFields = mutableSetOf<String>()
        val matches = mutableListOf<FieldMatch>()
        if (recognizedForms.size > 1) warnings += "More than one form identity matched. Only general wording is available."

        for (candidate in prepared) {
            val line = candidate.line
            val label = candidate.label
            if (sections.isHeader(line)) continue
            if (matches.size >= MAX_MATCHES) {
                capped = true
                unknown += line
                continue
            }
            if (candidate.instruction || (candidate.header && label !in wholeGeneralAliases)) {
                unknown += line
                continue
            }
            if (label.isEmpty() || label.length > 300 || candidate.wordCount > 14) {
                unknown += line
                continue
            }
            val sourceExact = scoped.filter { field ->
                field.field.id !in usedSourceFields && templateLabel(field, label) &&
                    sourceRoleMatches(field, candidate, prepared, scoped, sections)
            }.map { it.field }
            val exact = if (sourceExact.size == 1) sourceExact else genericCache.getOrPut(label) {
                generalMatches(candidate, generic)
            }
            if (exact.size == 1) {
                val field = exact.single()
                matches += FieldMatch(line, field, MatchMethod.EXACT, false, 1f, sections.contextFor(line, field.kind))
                if (field.formId != null) usedSourceFields += field.id
                continue
            }
            if (exact.size > 1) {
                unknown += line
                warnings += "A printed label has more than one possible meaning. Compare it with the original form."
                continue
            }

            // Fuzzy suggestions remain general and explicitly unconfirmed.
            val ranked = fuzzyCache.getOrPut(label) {
                if (label.length in 6..80 && candidate.wordCount <= 8 && !hasAny(candidate.tokens, specializedTokens)) {
                    generic.filterNot { disallowed(it.field.kind, candidate) }.map { compiledField ->
                        compiledField.field to (compiledField.aliases.maxOfOrNull { similarity(label, it) } ?: 0f)
                    }.sortedByDescending { it.second }
                } else emptyList()
            }
            val best = ranked.firstOrNull()
            val runnerUp = ranked.getOrNull(1)?.second ?: 0f
            if (best != null && best.second >= FUZZY_THRESHOLD && best.second - runnerUp >= MIN_MARGIN) {
                matches += FieldMatch(line, best.first, MatchMethod.FUZZY, true, best.second, sections.contextFor(line, best.first.kind))
            } else {
                unknown += line
            }
        }
        if (capped) warnings += "Only part of this long form was analyzed. Photograph the remaining section separately."
        if (matches.any { it.requiresConfirmation }) warnings += "Some labels are uncertain. Confirm each suggestion before using its explanation."
        if (unknown.isNotEmpty()) warnings += "Some printed text has no confirmed meaning. Check the original or ask the issuer."
        if (form == null) warnings += "The form's identity is unconfirmed. General wording does not verify its requirements."
        if (matches.any { it.field.formId == null }) warnings += "General word meanings do not confirm whose information is requested, required fields, or acceptance rules."
        return RecognitionResult(matches, unknown.distinct(), form, warnings.distinct())
    }

    /** Optional retrieval cannot bypass the rule safeguards or extend the scan's bounds. */
    fun mergeSuggestions(rules: RecognitionResult, suggestions: List<FieldMatch>, lines: List<OcrLine>, definitions: List<FieldDefinition>): RecognitionResult {
        var consumed = 0
        val bounded = lines.filter { it.text.isNotBlank() && usableConfidence(it) }.take(MAX_LINES).filter { line ->
            consumed += line.text.length
            consumed <= MAX_TEXT
        }
        val eligible = bounded.toSet()
        val unknown = rules.unknownLines.toSet()
        val fields = definitions.filter { it.formId == null }.associateBy { it.id }
        val contexts = SectionContextResolver().resolve(bounded, bounded.associateWith { normalize(it.text) })
        val allowed = suggestions.mapNotNull { suggestion ->
            val field = fields[suggestion.field.id] ?: return@mapNotNull null
            val candidate = prepareLine(suggestion.line)
            if (suggestion.line !in unknown || suggestion.line !in eligible || contexts.isHeader(suggestion.line) ||
                candidate.instruction || (candidate.header && candidate.label !in field.aliases.map(::normalize)) ||
                candidate.wordCount > 14 || candidate.label.length !in 1..300 || disallowed(field.kind, candidate)) return@mapNotNull null
            suggestion.copy(field = field, method = MatchMethod.SEMANTIC, requiresConfirmation = true,
                context = contexts.contextFor(suggestion.line, field.kind))
        }.distinctBy { it.line }
        val added = allowed.take((MAX_MATCHES - rules.matches.size).coerceAtLeast(0))
        val order = lines.withIndex().associate { it.value to it.index }
        val addedLines = added.map { it.line }.toSet()
        val warnings = rules.warnings + if (allowed.size > added.size)
            listOf("Only part of this long form was analyzed. Photograph the remaining section separately.") else emptyList()
        return rules.copy(matches = (rules.matches + added).sortedBy { order[it.line] ?: Int.MAX_VALUE },
            unknownLines = rules.unknownLines.filterNot { it in addedLines }, warnings = warnings.distinct())
    }

    private fun prepareLine(line: OcrLine): PreparedLine {
        val label = cleanLabel(line.text)
        val words = label.split(' ')
        return PreparedLine(line, label, normalize(line.text), words.toSet(), words.size, " $label ",
            instructionPattern.containsMatchIn(line.text), headerPattern.containsMatchIn(line.text))
    }

    private fun usableConfidence(line: OcrLine): Boolean = line.confidence == null ||
        (line.confidence.isFinite() && line.confidence in MIN_CONFIDENCE..1f)

    private fun generalMatches(candidate: PreparedLine, fields: List<CompiledField>): List<FieldDefinition> {
        data class Hit(val field: FieldDefinition, val start: Int, val end: Int)
        val hits = fields.filterNot { disallowed(it.field.kind, candidate) }.flatMap { compiled ->
            compiled.aliases.mapNotNull { alias ->
                val start = candidate.paddedLabel.indexOf(" $alias ")
                if (start < 0) null else Hit(compiled.field, start, start + alias.length)
            }
        }
        // Nested aliases such as "Date" in "Date of birth" do not compete;
        // separate complete labels on a single OCR line remain ambiguous.
        val unnested = hits.filter { hit -> hits.none { other ->
            other.field.id != hit.field.id && other.start <= hit.start && other.end >= hit.end &&
                other.end - other.start > hit.end - hit.start
        } }.map { it.field }.distinctBy { it.id }
        if (unnested.map { it.kind }.toSet() == setOf("signature", "date")) {
            return unnested.filter { it.kind == "date" }
        }
        return unnested
    }

    /** Same source-role gates as before optimization; only prepared labels replace repeated parsing. */
    private fun sourceRoleMatches(field: CompiledField, candidate: PreparedLine, lines: List<PreparedLine>, scoped: List<CompiledField>, sections: SectionContexts): Boolean {
        val line = candidate.line
        val label = candidate.label
        val section = sections.contextFor(line, field.field.kind)
        // The PSA child-name templates cannot override a readable parent or
        // requester heading merely because the child's own label is missing.
        if (field.field.kind in childNameKinds && section != null && section.role in conflictingChildRoles) return false
        fun sameColumn(a: OcrLine, b: OcrLine, fraction: Float = .6f): Boolean =
            abs(a.box.left - b.box.left) <= max(30f, max(a.box.width, b.box.width) * fraction)
        fun birthBoundary(surname: OcrLine): PreparedLine? = lines.filter { other ->
            other.line.box.top > surname.box.top && sameColumn(other.line, surname) &&
                other.label in birthBoundaryLabels
        }.minByOrNull { it.line.box.top }

        return when (field.field.kind) {
            "last_name" -> label.startsWith("last name if female") && birthBoundary(line) != null
            "first_name" -> {
                val surnameField = scoped.firstOrNull { it.field.kind == "last_name" }
                val surname = surnameField?.let { definition -> lines.singleOrNull { other ->
                    templateLabel(definition, other.label) && other.label.startsWith("last name if female") && birthBoundary(other.line) != null
                } }
                val boundary = surname?.let { birthBoundary(it.line) }
                if (surname == null || boundary == null) false else {
                    val childCandidates = lines.filter { other ->
                        templateLabel(field, other.label) && other.line.box.top > surname.line.box.top &&
                            other.line.box.top < boundary.line.box.top && sameColumn(other.line, surname.line, .25f)
                    }
                    childCandidates.singleOrNull()?.line == line
                }
            }
            "mobile" -> {
                section?.role == PersonRole.REQUESTER && lines.count { other ->
                    templateLabel(field, other.label) &&
                        sections.contextFor(other.line, field.field.kind)?.role == PersonRole.REQUESTER
                } == 1
            }
            "bren" -> label == "bren if known" || (label == "birth reference number" && lines.any { other ->
                other.label == "bren if known" && abs(other.line.box.top - line.box.top) <= max(60, line.box.height * 4) && sameColumn(line, other.line)
            })
            else -> lines.count { templateLabel(field, it.label) } == 1
        }
    }

    private fun templateLabel(field: CompiledField, label: String): Boolean =
        label in field.aliases || field.canonical == label || field.firstNameExcerpt == label

    private fun disallowed(kind: String, candidate: PreparedLine): Boolean {
        val tokens = candidate.tokens
        if (hasAny(tokens, specializedTokens) && kind !in numberKinds) return true
        if (kind == "date" && candidate.label !in simpleDateLabels && !hasAny(tokens, signatureTokens)) return true
        if (kind == "full_name") {
            if (hasAny(tokens, nonPersonTokens)) return true
            if (candidate.label !in simpleNameLabels && !hasAny(tokens, personTokens)) return true
        }
        if (kind == "school" && hasAny(tokens, schoolModifiers)) return true
        if (kind == "course" && hasAny(tokens, courseModifiers)) return true
        if (kind == "email" && hasAny(tokens, emailModifiers)) return true
        if (kind in qualifiedKinds && hasAny(tokens, unsafeModifiers)) return true
        if (kind == "employer" && hasAny(tokens, employerModifiers)) return true
        return false
    }

    companion object {
        const val MIN_CONFIDENCE = 0.65f
        const val FUZZY_THRESHOLD = 0.84f
        const val MIN_MARGIN = 0.12f
        const val MAX_LINES = 160
        const val MAX_TEXT = 12_000
        const val MAX_MATCHES = 40
        private val combiningMarks = Regex("\\p{M}+")
        private val nonLabelCharacters = Regex("[^a-z0-9]+")
        private val numericAnswer = Regex("\\s+\\d[\\d\\s/+().-]*$")
        private val printedDateFormat = Regex("\\((?:[DMYdmy/ -]{4,}|MMDDYYYY)\\)")
        private val instructionPattern = Regex("\\b(please|do not|don t|for office use|office use only|para sa opisina)\\b|(?:^|[,;:(]\\s*)(?:write|fill|describe|provide|enter|ask|copy|select|choose|tick|isulat|punan|piliin|lagyan|huwag)\\b", RegexOption.IGNORE_CASE)
        private val headerPattern = Regex("\\b(form|registration|application|instructions|guidelines|test fixture|not valid for submission)\\b", RegexOption.IGNORE_CASE)
        private val specializedTokens = setOf("classification", "category", "code", "password", "diagnosis", "allergies", "requirements", "requirement", "consent")
        private val numberKinds = setOf("reference_number", "id_number")
        private val signatureTokens = setOf("signature", "lagda", "pirma")
        private val simpleDateLabels = setOf("date", "petsa")
        private val birthBoundaryLabels = setOf("date of birth", "place of birth")
        private val childNameKinds = setOf("first_name", "last_name")
        private val conflictingChildRoles = setOf(PersonRole.FATHER, PersonRole.MOTHER, PersonRole.REQUESTER)
        private val simpleNameLabels = setOf("name", "full name", "complete name", "pangalan", "buong pangalan")
        private val nonPersonTokens = setOf("bank", "medication", "medicine", "account", "project", "product", "document", "business", "organization", "disease", "file", "pet", "animal")
        private val personTokens = setOf("applicant", "patient", "employee", "student", "child", "mother", "father", "parent", "requester", "guardian", "spouse", "beneficiary", "contact", "person", "kin", "ina", "ama", "anak", "asawa")
        private val schoolModifiers = setOf("year", "code", "number", "id", "type", "district", "fees", "fee")
        private val courseModifiers = setOf("duration", "treatment", "code", "number", "type", "fees", "fee", "length", "credits", "status")
        private val emailModifiers = setOf("subject", "password", "provider", "type", "code", "status")
        private val unsafeModifiers = setOf("format", "prefix", "classification", "category", "type", "code", "status", "requirements", "requirement")
        private val employerModifiers = setOf("number", "no", "id")
        private val qualifiedKinds = setOf("mobile", "employer", "occupation", "age", "birth_date", "birth_place", "address", "signature")

        fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .lowercase(Locale.ROOT)
            .replace(nonLabelCharacters, " ")
            .trim()

        private fun cleanLabel(text: String): String = normalize(text.substringBefore(':')
            .replace(numericAnswer, "").replace(printedDateFormat, ""))
        private fun containsPhrase(text: String, phrase: String): Boolean = " $text ".contains(" $phrase ")
        private fun hasAny(tokens: Set<String>, choices: Set<String>): Boolean = choices.any { it in tokens }

        private fun similarity(left: String, right: String): Float {
            if (left.isEmpty() || right.isEmpty()) return 0f
            val maxLength = max(left.length, right.length)
            // A score below threshold-minus-margin cannot change acceptance:
            // it can neither win nor block an otherwise acceptable runner-up gap.
            if (1f - abs(left.length - right.length).toFloat() / maxLength < FUZZY_THRESHOLD - MIN_MARGIN) return 0f
            var previous = IntArray(right.length + 1) { it }
            for (i in left.indices) {
                val current = IntArray(right.length + 1)
                current[0] = i + 1
                for (j in right.indices) current[j + 1] = minOf(
                    current[j] + 1, previous[j + 1] + 1,
                    previous[j] + if (left[i] == right[j]) 0 else 1,
                )
                previous = current
            }
            return 1f - previous[right.length].toFloat() / maxLength
        }
    }
}

