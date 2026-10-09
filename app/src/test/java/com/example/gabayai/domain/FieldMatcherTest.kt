package com.example.gabayai.domain

import com.example.gabayai.data.SeedContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldMatcherTest {
    private val matcher = FieldMatcher()
    private fun line(text: String, confidence: Float? = null) = OcrLine(text, OcrBox(0, 0, 100, 20), confidence)
    private fun recognize(vararg text: String) = matcher.recognize(text.map { line(it) }, SeedContent.fields, SeedContent.forms)
    private val psaAnchors = arrayOf("Birth Certificate", "APPLICATION FORM", "PHILIPPINE STATISTICS AUTHORITY", "BIRTH CERTIFICATE DETAILS")

    @Test fun sourceBackedMeaningRequiresIndependentPsaEvidence() {
        val result = recognize(*psaAnchors, "Date of Birth")
        assertEquals(SeedContent.PSA_FORM_ID, result.form?.id)
        val field = result.matches.single()
        assertEquals(Grounding.SOURCE_BACKED, field.field.source.grounding)
        assertEquals("Date of Birth / Month / Day / Year", field.field.source.excerpt)
        assertFalse(field.requiresConfirmation)
    }

    @Test fun genericBirthTextDoesNotEstablishGovernmentIdentity() {
        val result = recognize("Birth Certificate", "Date of Birth")
        assertNull(result.form)
        assertEquals(Grounding.GENERAL_VOCABULARY, result.matches.single().field.source.grounding)
        assertNull(result.matches.single().field.source.url)
    }

    @Test fun repeatedAnchorDoesNotCountAsIndependentEvidence() {
        val result = recognize("Birth Certificate", "APPLICATION FORM", "APPLICATION FORM", "APPLICATION FORM", "Date of Birth")
        assertNull(result.form)
    }

    @Test fun fuzzyOcrTypoIsSuggestionAndRequiresConfirmation() {
        val result = recognize("Date of Birlh")
        assertEquals(MatchMethod.FUZZY, result.matches.single().method)
        assertTrue(result.matches.single().requiresConfirmation)
        assertTrue(result.confirmedMatches.isEmpty())
    }

    @Test fun unknownSpecializedFieldIsNotAccepted() {
        val result = recognize("Office classification code", "Date of Birth requirements")
        assertTrue(result.matches.isEmpty())
        assertEquals(2, result.unknownLines.size)
    }

    @Test fun unreadableTextCannotEstablishFormOrField() {
        val result = matcher.recognize(psaAnchors.map { line(it, .3f) } + line("Date of Birth", .3f), SeedContent.fields, SeedContent.forms)
        assertNull(result.form)
        assertTrue(result.matches.isEmpty())
        assertEquals(5, result.unknownLines.size)
    }

    @Test fun missingProviderConfidenceIsNotFabricatedOrRejected() {
        val result = recognize("Date of Birth:")
        assertEquals(MatchMethod.EXACT, result.matches.single().method)
        assertNull(result.matches.single().line.confidence)
    }

    @Test fun repeatedLabelsUseGeneralMeaningUntilRolesAreKnown() {
        val result = recognize(*psaAnchors, "Date of Birth", "Date of Birth")
        assertEquals(2, result.matches.size)
        assertTrue(result.matches.all { it.field.source.grounding == Grounding.GENERAL_VOCABULARY })
    }

    @Test fun aliasCollisionDoesNotChooseFirstDatabaseRow() {
        val duplicate = SeedContent.fields.single { it.id == "general.birth_date" }.copy(id = "other.birth_date", kind = "other")
        val result = matcher.recognize(listOf(line("Date of Birth")), SeedContent.fields + duplicate, SeedContent.forms)
        assertTrue(result.matches.isEmpty())
        assertEquals(1, result.unknownLines.size)
    }

    @Test fun regionalLanguagesRemainUnavailableAndNoTranslationClaimsReview() {
        assertTrue(SeedContent.fields.all { it.explanation(GuideLanguage.CEBUANO) == null })
        assertTrue(SeedContent.fields.flatMap { it.explanations }.all { it.review == TranslationReview.NOT_LINGUISTICALLY_REVIEWED })
    }

    @Test fun sourcePdfProvenanceHasActualSha256() {
        val source = SeedContent.fields.first().source
        assertEquals("c777a6389c78cd6fa1fd96281b8094e1a0dba7abe41d0672749892dd70a24060", source.sha256)
        assertEquals("2026-10-09", source.sourceReviewedOn)
    }

    private fun positioned(text: String, x: Int, y: Int, width: Int = 260) =
        OcrLine(text, OcrBox(x, y, x + width, y + 20), .98f)

    @Test fun allSixPsaFieldsAreSourceBackedOnlyInTheirSupportedRoles() {
        val lines = psaAnchors.map { positioned(it, 0, 0) } + listOf(
            positioned("BReN, if known", 20, 70),
            positioned("Last Name (if female, last name before marriage)", 20, 100),
            positioned("First Name", 20, 140),
            positioned("Date of Birth", 20, 200),
            positioned("Place of Birth", 20, 240),
            positioned("First Name", 20, 310), // Father's repeated label.
            positioned("REQUESTER’S DETAILS", 500, 30),
            positioned("First Name", 520, 60), // Requester, in the other column.
            positioned("Mobile Number", 520, 180),
        )
        val result = matcher.recognize(lines, SeedContent.fields, SeedContent.forms)
        val sourceBacked = result.matches.filter { it.field.source.grounding == Grounding.SOURCE_BACKED }
        assertEquals(setOf("last_name", "first_name", "birth_date", "birth_place", "bren", "mobile"), sourceBacked.map { it.field.kind }.toSet())
        assertEquals(140, sourceBacked.single { it.field.kind == "first_name" }.line.box.top)
        assertTrue(result.matches.filter { it.line.box.top in setOf(60, 310) }.all { it.field.formId == null })
    }

    @Test fun childFirstNameIsGeneralWhenDistinctiveSurnameIsUnreadable() {
        val lines = psaAnchors.map { positioned(it, 0, 0) } + listOf(
            positioned("Last Name", 20, 100),
            positioned("First Name", 20, 140),
            positioned("Date of Birth", 20, 200),
        )
        val result = matcher.recognize(lines, SeedContent.fields, SeedContent.forms)
        assertTrue(result.matches.filter { it.field.kind == "first_name" }.all { it.field.formId == null })
    }

    @Test fun twoPlausibleChildFirstNamesCannotChooseTheFirstOne() {
        val lines = psaAnchors.map { positioned(it, 0, 0) } + listOf(
            positioned("Last Name (if female, last name before marriage)", 20, 100),
            positioned("First Name", 20, 140),
            positioned("First Name", 20, 170),
            positioned("Date of Birth", 20, 200),
        )
        val result = matcher.recognize(lines, SeedContent.fields, SeedContent.forms)
        assertTrue(result.matches.filter { it.field.kind == "first_name" }.all { it.field.formId == null })
    }

    @Test fun mobileWithoutRequesterSectionNeverGetsRequesterMeaning() {
        val result = recognize(*psaAnchors, "Mobile Number")
        assertEquals("general.mobile", result.matches.single().field.id)
    }

    @Test fun mobileInOtherColumnNeverGetsRequesterMeaning() {
        val lines = psaAnchors.map { positioned(it, 0, 0) } + listOf(
            positioned("REQUESTER’S DETAILS", 500, 30),
            positioned("Mobile Number", 20, 180),
        )
        val result = matcher.recognize(lines, SeedContent.fields, SeedContent.forms)
        assertEquals("general.mobile", result.matches.single().field.id)
    }

    @Test fun mobileBeyondEmergencyContactBoundaryCannotReceiveRequesterMeaning() {
        val lines = psaAnchors.map { positioned(it, 0, 0) } + listOf(
            positioned("REQUESTER’S DETAILS", 500, 30), positioned("First Name", 520, 60),
            positioned("EMERGENCY CONTACT", 500, 100), positioned("Mobile Number", 520, 180),
        )
        val mobile = matcher.recognize(lines, SeedContent.fields, SeedContent.forms).matches.single { it.field.kind == "mobile" }
        assertEquals("general.mobile", mobile.field.id)
        assertNull(mobile.context)
    }

    @Test fun mobileFarBelowUnclosedRequesterSectionRemainsGeneral() {
        val lines = psaAnchors.map { positioned(it, 0, 0) } + listOf(
            positioned("REQUESTER’S DETAILS", 500, 30), positioned("First Name", 520, 60),
            positioned("Mobile Number", 520, 500),
        )
        val mobile = matcher.recognize(lines, SeedContent.fields, SeedContent.forms).matches.single { it.field.kind == "mobile" }
        assertEquals("general.mobile", mobile.field.id)
        assertNull(mobile.context)
    }

    @Test fun explicitFatherHeaderOverridesTheChildFirstNameCoordinateHeuristic() {
        val lines = psaAnchors.map { positioned(it, 0, 0) } + listOf(
            positioned("Last Name (if female, last name before marriage)", 115, 100),
            positioned("Father’s Name", 10, 120, 80), positioned("First Name", 115, 140),
            positioned("Date of Birth", 115, 200),
        )
        val first = matcher.recognize(lines, SeedContent.fields, SeedContent.forms).matches.single { it.field.kind == "first_name" }
        assertEquals("general.first_name", first.field.id)
        assertEquals(PersonRole.FATHER, first.context?.role)
    }

    @Test fun uniqueRequesterPhoneRemainsSourceBackedWhenAnotherSectionHasAPhone() {
        val lines = psaAnchors.map { positioned(it, 0, 0) } + listOf(
            positioned("REQUESTER’S DETAILS", 500, 30), positioned("First Name", 520, 60),
            positioned("Mobile Number", 520, 100), positioned("EMERGENCY CONTACT", 500, 180),
            positioned("Mobile Number", 520, 220),
        )
        val phones = matcher.recognize(lines, SeedContent.fields, SeedContent.forms).matches.filter { it.field.kind == "mobile" }
        assertEquals("psa.mobile", phones.single { it.line.box.top == 100 }.field.id)
        assertEquals("general.mobile", phones.single { it.line.box.top == 220 }.field.id)
    }

    @Test fun allTwentyFourGeneralKindsRetainAnExactOriginalAlias() {
        val general = SeedContent.fields.filter { it.formId == null }
        assertEquals(24, general.size)
        assertEquals(104, general.sumOf { it.aliases.size })
        for (field in general) {
            val result = recognize(field.aliases.first())
            assertEquals(field.id, result.matches.single().field.id)
            assertEquals(Grounding.GENERAL_VOCABULARY, result.matches.single().field.source.grounding)
        }
    }

    @Test fun all104OriginalGeneralAliasesRemainRecognizable() {
        for (field in SeedContent.fields.filter { it.formId == null }) {
            for (alias in field.aliases) {
                val result = recognize(alias)
                assertEquals("Original alias must retain its ordinary meaning: $alias", field.id, result.matches.single().field.id)
                assertFalse(result.matches.single().requiresConfirmation)
            }
        }
    }

    @Test fun applicationNumberIsAReferenceLabelRatherThanAFormHeading() {
        val result = recognize("Application number")
        assertEquals("general.reference_number", result.matches.single().field.id)
        assertNull(result.form)
    }

    @Test fun damagedLongLabelCannotSilentlyBecomeShorterAcceptedAlias() {
        val result = recognize("Date of Birlh")
        assertEquals("general.birth_date", result.matches.single().field.id)
        assertEquals(MatchMethod.FUZZY, result.matches.single().method)
        assertTrue(result.matches.single().requiresConfirmation)
    }

    @Test fun croppedParentSectionNeedsNoDateOfBirthOrPsaIdentityForNeutralGuidance() {
        val lines = listOf(
            positioned("Father’s Name", 10, 20),
            positioned("Last Name", 115, 20),
            positioned("First Name (include JR., SR., II, III, IV, etc., if applicable)", 115, 75),
            positioned("Middle Name", 115, 125),
            positioned("Mother’s Maiden Name", 10, 180),
            positioned("Last Name (before marriage)", 115, 180),
            positioned("First Name", 115, 230),
            positioned("Middle Name (before marriage)", 115, 285),
            positioned("PURPOSE OF YOUR REQUEST", 10, 335),
        )
        val result = matcher.recognize(lines, SeedContent.fields, SeedContent.forms)
        assertNull(result.form)
        assertTrue(result.matches.isNotEmpty())
        assertTrue(result.matches.all { it.field.formId == null })
        assertEquals(2, result.matches.count { it.field.kind == "first_name" })
        assertEquals(2, result.matches.count { it.field.kind == "last_name" })
        assertEquals(2, result.matches.count { it.field.kind == "middle_name" })
        assertTrue(result.matches.filter { it.field.kind == "first_name" }.all {
            it.field.explanation(GuideLanguage.ENGLISH)!!.text.contains("person identified in this part")
        })
        assertTrue(result.matches.none { it.field.kind == "birth_date" })
    }

    @Test fun longerBirthAndNameAliasesDoNotCollideWithDateAndName() {
        assertEquals("general.birth_date", recognize("Date of Birth").matches.single().field.id)
        assertEquals("general.first_name", recognize("First Name").matches.single().field.id)
        assertEquals("general.middle_initial", recognize("Middle Initial").matches.single().field.id)
    }

    @Test fun multipleSeparateLabelsOnOneLineRemainUncertain() {
        val result = recognize("First Name / Last Name")
        assertTrue(result.matches.isEmpty())
        assertEquals(1, result.unknownLines.size)
    }

    @Test fun specializedVocabularyCannotBorrowOrdinaryFieldMeaning() {
        val result = recognize("School code", "Course duration", "Email password", "Nationality classification", "Patient name code", "Date of birth requirements")
        assertTrue(result.matches.isEmpty())
        assertEquals(6, result.unknownLines.size)
    }

    @Test fun printedInstructionsRemainOriginalEvidenceRatherThanFieldRules() {
        val result = recognize("Write your Date of Birth.", "For Purpose of request, describe the service you need.", "Email address: ask reception if unsure.")
        assertTrue(result.matches.isEmpty())
        assertEquals(3, result.unknownLines.size)
    }

    @Test fun labelValueLineRetainsOriginalTextButDoesNotInterpretTheAnswer() {
        val original = "First name: PRIVATE_VALUE"
        val result = recognize(original)
        assertEquals("general.first_name", result.matches.single().field.id)
        assertEquals(original, result.matches.single().line.text)
        assertFalse(result.matches.single().field.explanations.any { "PRIVATE_VALUE" in it.text })
    }

    @Test fun dateFormatOnTheLabelDoesNotBecomeAnInventedRule() {
        val result = recognize("Date of Birth (MMDDYYYY)")
        assertEquals("general.birth_date", result.matches.single().field.id)
        assertTrue(result.matches.single().field.explanation(GuideLanguage.ENGLISH)!!.text.contains("printed on the form"))
    }

    @Test fun longFormsHaveExplicitCapsAndRetainUnprocessedEvidence() {
        val lines = (0 until 45).map { positioned("First name", 20, it * 30) }
        val result = matcher.recognize(lines, SeedContent.fields, SeedContent.forms)
        assertEquals(FieldMatcher.MAX_MATCHES, result.matches.size)
        assertEquals(5, result.unknownLines.size)
        assertTrue(result.warnings.any { it.contains("remaining section") })
    }

    @Test fun nonFiniteConfidenceCannotEstablishSourceMeaning() {
        val result = matcher.recognize(listOf(line("Date of Birth", Float.NaN)), SeedContent.fields, SeedContent.forms)
        assertTrue(result.matches.isEmpty())
        assertEquals(1, result.unknownLines.size)
    }
}
