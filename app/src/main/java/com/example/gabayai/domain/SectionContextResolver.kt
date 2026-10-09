package com.example.gabayai.domain

import kotlin.math.abs
import kotlin.math.max

/** Resolves only explicit, readable section words and conservative layout evidence. */
internal class SectionContextResolver {
    private data class HeaderCandidate(val role: PersonRole, val lines: List<OcrLine>) {
        val box = OcrBox(lines.minOf { it.box.left }, lines.minOf { it.box.top },
            lines.maxOf { it.box.right }, lines.maxOf { it.box.bottom })
        val text = lines.joinToString(" ") { it.text.trim() }
    }

    fun resolve(lines: List<OcrLine>, normalized: Map<OcrLine, String>): SectionContexts {
        val detected = lines.mapNotNull { line ->
            val label = normalized.getValue(line)
            if (label.startsWith("requester s details") || label == "requester details") {
                return@mapNotNull HeaderCandidate(PersonRole.REQUESTER, listOf(line))
            }
            val role = when {
                fatherHeader.matches(label) -> PersonRole.FATHER
                motherHeader.matches(label) -> PersonRole.MOTHER
                else -> return@mapNotNull null
            }
            val fragments = mutableListOf(line)
            var hasName = "name" in label.split(' ')
            // ML Kit commonly splits a narrow printed header across two or three
            // lines. Only literal Name/Maiden fragments in that same narrow column
            // may complete it. A damaged/unreadable role is never inferred.
            repeat(2) {
                if (!hasName) {
                    val previous = fragments.last()
                    val next = lines.filter { other ->
                        other !in fragments && normalized.getValue(other) in headerFragments &&
                            other.box.top > previous.box.top &&
                            other.box.top <= previous.box.bottom + max(12, line.box.height) &&
                            abs(other.box.left - line.box.left) <= max(10, line.box.height / 2) &&
                            other.box.right <= line.box.right + max(25, line.box.height * 2)
                    }.minByOrNull { it.box.top }
                    if (next != null) {
                        fragments += next
                        hasName = "name" in normalized.getValue(next).split(' ')
                    }
                }
            }
            if (hasName) HeaderCandidate(role, fragments) else null
        }.distinctBy { it.lines }
        // Contradictory role headers in the same narrow space are unreadable
        // section evidence, not a reason to select the last OCR line.
        val candidates = detected.filterNot { header -> detected.any { other ->
            other.role != header.role && abs(other.box.left - header.box.left) <= 12 &&
                other.box.top < header.box.bottom && header.box.top < other.box.bottom
        } }

        val sections = candidates.mapNotNull { header ->
            val requester = header.role == PersonRole.REQUESTER
            val anchor = lines.filter { line ->
                line !in header.lines && nameField.containsMatchIn(normalized.getValue(line)) &&
                    line.box.top >= header.box.top - max(12, line.box.height) &&
                    line.box.top <= header.box.bottom + max(40, line.box.height * 3) &&
                    if (requester) {
                        line.box.top >= header.box.bottom - line.box.height &&
                            abs(line.box.left - header.box.left) <= max(40, header.box.width)
                    } else {
                        val gap = line.box.left - header.box.right
                        gap >= 5 && gap <= max(48, line.box.height * 6)
                    }
            }.minWithOrNull(compareBy<OcrLine> { abs(it.box.top - header.box.top) }
                .thenBy { if (requester) abs(it.box.left - header.box.left) else it.box.left - header.box.right }) ?: return@mapNotNull null
            val tolerance = max(18, anchor.box.height * 2)
            val boundaries = lines.filter { line ->
                line !in header.lines && line.box.top > header.box.top + 2 &&
                    (boundary.containsMatchIn(normalized.getValue(line)) || candidates.any { line in it.lines }) &&
                    (abs(line.box.left - header.box.left) <= max(20, header.box.width / 2) ||
                        (line.box.left <= anchor.box.left + tolerance && line.box.right >= anchor.box.left - tolerance))
            }
            val endY = minOf(boundaries.minOfOrNull { it.box.top } ?: Int.MAX_VALUE,
                header.box.top + max(160, anchor.box.height * 12))
            SectionContexts.Section(
                context = FieldContext(header.role, header.text, header.box),
                headerLines = header.lines.toSet(),
                fieldLeft = anchor.box.left,
                tolerance = tolerance,
                // OCR can place the adjacent first field a few pixels above its
                // printed section title. Use that actual row, not an arbitrary
                // upward margin that makes consecutive sections overlap.
                startY = minOf(header.box.top, anchor.box.top),
                endY = endY,
            )
        }
        val boundedSections = sections.map { section ->
            val nextStart = sections.filter { other ->
                other.context.box.top > section.context.box.top && other.startY > section.startY &&
                    abs(other.fieldLeft - section.fieldLeft) <= max(18, minOf(other.tolerance, section.tolerance)) &&
                    abs(other.context.box.left - section.context.box.left) <=
                        max(20, minOf(other.context.box.width, section.context.box.width) / 2)
            }.minOfOrNull { it.startY }
            // A successor in a parallel column must not truncate this lane.
            // Only an explicit same-column section with its own field anchor
            // can move the boundary above the successor's title coordinates.
            section.copy(endY = minOf(section.endY, nextStart ?: Int.MAX_VALUE))
        }
        // A literal section title remains a title when its fields are cropped
        // out or its layout is ambiguous. In particular, a split "Name" must
        // not become an extra full-name field merely because no anchor survived.
        return SectionContexts(boundedSections, detected.flatMap { it.lines }.toSet())
    }

    companion object {
        private val fatherHeader = Regex("^(father|fathers)( s)?( name)?$")
        private val motherHeader = Regex("^(mother|mothers)( s)?( maiden)?( name)?$")
        private val headerFragments = setOf("name", "maiden", "maiden name")
        private val nameField = Regex("^(first name|first names|firstname|given name|last name|lastname|surname|family name|middle name|middle initial)\\b")
        private val boundary = Regex("^(purpose|requester s details|requester details|birth certificate details|privacy notice|acknowledgement|contact details|emergency contact|education|employment|declaration|instructions|section|part)\\b")
    }
}

internal class SectionContexts(
    private val sections: List<Section>,
    private val headerLines: Set<OcrLine>,
) {
    internal data class Section(
        val context: FieldContext,
        val headerLines: Set<OcrLine>,
        val fieldLeft: Int,
        val tolerance: Int,
        val startY: Int,
        val endY: Int,
    )

    fun isHeader(line: OcrLine): Boolean = line in headerLines

    fun contextFor(line: OcrLine, kind: String): FieldContext? = sections.filter { section ->
        val roleApplies = kind in nameKinds || (section.context.role == PersonRole.REQUESTER && kind in requesterKinds)
        roleApplies && line !in section.headerLines && line.box.top >= section.startY && line.box.top < section.endY &&
            line.box.bottom <= section.endY &&
            abs(line.box.left - section.fieldLeft) <= section.tolerance
    }.singleOrNull()?.context

    companion object {
        private val nameKinds = setOf("first_name", "last_name", "middle_name", "middle_initial", "full_name")
        private val requesterKinds = setOf("mobile", "address", "email")
    }
}
