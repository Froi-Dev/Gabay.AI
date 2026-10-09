package com.example.gabayai.domain

import com.example.gabayai.data.SeedContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionContextTest {
    private fun line(text: String, left: Int, top: Int, width: Int = 80, height: Int = 16) =
        OcrLine(text, OcrBox(left, top, left + width, top + height), .98f)
    private fun recognize(lines: List<OcrLine>) = FieldMatcher().recognize(lines, SeedContent.fields, SeedContent.forms)

    private fun splitParentPhoto() = listOf(
        line("Father’s", 14, 20, 62), line("Name", 14, 40, 36),
        line("Last Name", 114, 20), line("First Name (include JR., SR., II, III, IV, etc., if applicable)", 114, 70, 450),
        line("Middle Name", 114, 120),
        line("Mother’s", 14, 176, 64), line("Maiden", 14, 196, 50), line("Name", 14, 216, 36),
        line("Last Name (before marriage)", 114, 176, 220), line("First Name", 114, 226),
        line("Middle Name (before marriage)", 114, 276, 240),
        line("PURPOSE OF YOUR REQUEST", 14, 326, 640),
    )

    @Test fun actualPrintedParentFragmentsResolveEachNameToItsOwnSection() {
        val result = recognize(splitParentPhoto())
        assertNull(result.form)
        val names = result.matches.filter { it.field.kind in setOf("first_name", "last_name", "middle_name") }
        assertEquals(6, names.size)
        assertEquals(3, names.count { it.context?.role == PersonRole.FATHER })
        assertEquals(3, names.count { it.context?.role == PersonRole.MOTHER })
        assertEquals("Father’s Name", names.first().context?.originalLabel)
        val mother = names.last().context!!
        assertEquals("Mother’s Maiden Name", mother.originalLabel)
        assertEquals(OcrBox(14, 176, 78, 232), mother.box)
        assertTrue(names.all { it.field.source.grounding == Grounding.GENERAL_VOCABULARY })
        assertFalse(result.matches.any { it.line.text == "Name" })
    }

    @Test fun actualMlKitBoxesFromUserCropResolveTheSlightlyHigherMotherLastNameRow() {
        // Actual OCR text/boxes from docs/evidence/parent-section-public-golden.json.
        // The live instrumentation test separately runs ML Kit on the real photo.
        val result = recognize(listOf(
            line("Father's", 13, 17, 52, 17), line("Name", 13, 37, 38, 16),
            line("Mother's", 15, 174, 53, 13), line("Maiden", 12, 191, 48, 17), line("Name", 10, 212, 41, 15),
            line("Last Name", 115, 19, 57, 11),
            line("First Name (include JR., SR., I, I, IV, etc., if applicable)", 115, 70, 299, 13),
            line("Middle Name", 114, 120, 71, 11),
            line("Last Name (before marriage)", 114, 170, 158, 19),
            line("First Name", 114, 224, 57, 11),
            line("Middle Name (before marriage)", 113, 272, 172, 18),
            line("PURPOSE OF YOUR REQUEST", 11, 328, 200, 17),
        ))
        assertNull(result.form)
        val names = result.matches.filter { it.field.kind in setOf("first_name", "last_name", "middle_name") }
        assertEquals(6, names.size)
        assertEquals(3, names.count { it.context?.role == PersonRole.FATHER })
        assertEquals(3, names.count { it.context?.role == PersonRole.MOTHER })
        assertEquals(PersonRole.MOTHER, names.single { it.line.box.top == 170 }.context?.role)
        assertNull(result.matches.single { it.field.kind == "purpose" }.context)
    }

    @Test fun aNameBoxStraddlingTheNextSectionBoundaryLeavesNeutralGuidance() {
        val result = recognize(listOf(
            line("Father’s Name", 14, 20, 80), line("Last Name", 114, 20),
            line("Middle Name", 114, 164),
            line("Mother’s Maiden Name", 14, 174, 80), line("Last Name", 114, 170),
            line("First Name", 114, 224),
        ))
        assertNull(result.matches.single { it.line.box.top == 164 }.context)
        assertEquals(PersonRole.MOTHER, result.matches.single { it.line.box.top == 170 }.context?.role)
    }

    @Test fun purposeAndFollowingNamesCannotInheritMotherContext() {
        val result = recognize(splitParentPhoto() + line("First Name", 114, 380))
        assertNull(result.matches.single { it.field.kind == "purpose" }.context)
        assertNull(result.matches.single { it.line.box.top == 380 }.context)
    }

    @Test fun anotherColumnCannotInheritParentContext() {
        val result = recognize(listOf(
            line("Father’s Name", 14, 20, 80), line("Last Name", 114, 20),
            line("First Name", 114, 70), line("First Name", 420, 70),
        ))
        assertEquals(PersonRole.FATHER, result.matches.single { it.line.box.left == 114 && it.field.kind == "first_name" }.context?.role)
        assertNull(result.matches.single { it.line.box.left == 420 }.context)
    }

    @Test fun croppedOrDamagedRoleHeaderLeavesNeutralGuidance() {
        val result = recognize(listOf(
            line("Mother’s", 14, 20, 64), // No adjacent Name evidence in this crop.
            line("Last Name", 114, 20), line("First Name", 114, 70),
        ))
        assertTrue(result.matches.isNotEmpty())
        assertTrue(result.matches.all { it.context == null && it.field.formId == null })
    }

    @Test fun conflictingPrintedRoleEvidenceCannotChooseOneParent() {
        val result = recognize(listOf(
            line("Father’s Name", 14, 20), line("Mother’s Name", 14, 22),
            line("Last Name", 114, 25), line("First Name", 114, 70),
        ))
        assertTrue(result.matches.all { it.context == null })
    }

    @Test fun aDistantFieldColumnCannotSupplyTheParentAnchor() {
        val result = recognize(listOf(
            line("Father’s", 14, 20, 62), line("Name", 14, 40, 36),
            line("Last Name", 420, 20), line("First Name", 420, 70),
        ))
        assertEquals(2, result.matches.size)
        assertTrue(result.matches.all { it.context == null })
        assertFalse(result.matches.any { it.line.text == "Name" })
    }

    @Test fun aTitleWithoutAnyVisibleFieldsCannotBecomeANameQuestion() {
        val result = recognize(listOf(line("Mother’s", 14, 20, 64),
            line("Maiden", 14, 40, 50), line("Name", 14, 60, 36)))
        assertTrue(result.matches.isEmpty())
    }

    @Test fun aLowConfidenceRoleCannotProvideContextForClearNameFields() {
        val result = recognize(listOf(
            line("Father’s Name", 14, 20).copy(confidence = .2f),
            line("Last Name", 114, 20), line("First Name", 114, 70),
        ))
        assertEquals(2, result.matches.size)
        assertTrue(result.matches.all { it.context == null })
    }

    @Test fun farBelowAnUnclosedSectionLeavesNeutralGuidance() {
        val result = recognize(listOf(
            line("Father’s Name", 14, 20), line("Last Name", 114, 20),
            line("First Name", 114, 500),
        ))
        assertEquals(PersonRole.FATHER, result.matches.single { it.field.kind == "last_name" }.context?.role)
        assertNull(result.matches.single { it.field.kind == "first_name" }.context)
    }

    @Test fun parentAndRequesterSectionsInParallelColumnsStayIndependent() {
        val result = recognize(listOf(
            line("Father’s Name", 14, 20), line("Last Name", 114, 20),
            line("First Name", 114, 70),
            line("REQUESTER’S DETAILS", 420, 10, 220), line("First Name", 420, 45),
            line("Mobile Number", 420, 100),
        ))
        assertEquals(PersonRole.FATHER, result.matches.single { it.line.box.left == 114 && it.field.kind == "first_name" }.context?.role)
        assertEquals(PersonRole.REQUESTER, result.matches.single { it.line.box.left == 420 && it.field.kind == "first_name" }.context?.role)
        assertEquals(PersonRole.REQUESTER, result.matches.single { it.field.kind == "mobile" }.context?.role)
    }

    @Test fun roleWordsHaveNoUnavailableRegionalTranslationOrReviewClaim() {
        assertEquals("Tatay", PersonRole.FATHER.label(GuideLanguage.FILIPINO))
        assertEquals("Mother", PersonRole.MOTHER.label(GuideLanguage.ENGLISH))
        assertNull(PersonRole.MOTHER.label(GuideLanguage.CEBUANO))
        assertTrue(SeedContent.fields.flatMap { it.explanations }.all { it.review == TranslationReview.NOT_LINGUISTICALLY_REVIEWED })
    }
}
