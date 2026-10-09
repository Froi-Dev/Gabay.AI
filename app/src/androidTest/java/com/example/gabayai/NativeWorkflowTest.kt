package com.example.gabayai

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Drives the installed app with its public blank sample, real ML Kit and real Room. */
@RunWith(AndroidJUnit4::class)
class NativeWorkflowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun actualOfflineSampleSupportsOriginalLabelsReviewLanguageSwitchAndCompletion() {
        // Preferences may have survived a previous install/run. Choose explicitly.
        chooseHomeLanguage(filipino = true)
        waitForText("Subukan sa Blankong PSA Form")
        click("Subukan sa Blankong PSA Form")
        waitForText("Gamitin ang Picture")
        compose.onNodeWithText("Gamitin ang Picture").assertIsEnabled()
        compose.onNodeWithContentDescription("Picture ng form").assertExists()
        click("Gamitin ang Picture")

        // This timeout waits for actual bundled OCR and repository work. No OCR
        // outputs, model callbacks or navigation state are replaced by a fixture.
        waitForText("Anong wika ang mas madali?", 120_000)
        listOf("Cebuano", "Ilocano", "Hiligaynon", "Bikol").forEach { unavailable ->
            compose.onNodeWithText(unavailable).assertIsNotEnabled()
        }
        compose.onNodeWithText(
            "Ang kasalukuyang Filipino at English na wording ay kailangan pa ng human language review.",
        ).assertExists()
        click("Tingnan ang Paliwanag")

        findBirthDate(filipino = true)
        compose.onNode(dateHeading).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Orihinal na label sa form").assertExists()
        compose.onNodeWithText("Ilagay ang buwan, araw, at taon ng birthday.").assertExists()
        compose.onNodeWithText("Halimbawa lamang").assertExists()
        compose.onNodeWithContentDescription("Bahagi ng orihinal na picture: Date of Birth", substring = true).assertExists()
        click("Tungkol sa gabay")
        compose.onNodeWithText("Wording: kailangan pa ng language review.").assertExists()
        compose.onNodeWithText("Batay sa PSA source form: Date of Birth / Month / Day / Year").assertExists()

        click("Tingnan ang Buong Picture")
        compose.onNodeWithContentDescription("Orihinal na picture. I-scroll para makita ang ibang bahagi.").assertExists()
        compose.onNodeWithContentDescription("Naihahambing ko na sa orihinal")
            .performScrollTo().performClick().assertIsOn()
        compose.onNodeWithContentDescription("Ipakita ang picture sa 2×").performScrollTo().performClick().assertIsSelected()
        click("Susunod")
        click("Nauna")
        compose.onNode(dateHeading).assertExists()
        compose.onNodeWithContentDescription("Naihahambing ko na sa orihinal").assertIsOn()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { compose.onAllNodes(dateHeading).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(dateHeading).assertExists()
        compose.onNodeWithContentDescription("Naihahambing ko na sa orihinal").assertIsOn()

        click("Palitan ang Wika")
        click("English")
        waitForText("Which language is easier?")
        listOf("Cebuano", "Ilocano", "Hiligaynon", "Bikol").forEach { unavailable ->
            compose.onNodeWithText(unavailable).assertIsNotEnabled()
        }
        click("Show Explanations")
        compose.waitUntil(15_000) { compose.onAllNodes(dateHeading).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(dateHeading).assertExists()
        compose.onNodeWithContentDescription("I have compared it with the original").assertIsOn()
        compose.onNodeWithText("Original label on the form").assertExists()
        compose.onNodeWithText("Enter the person’s birth month, day, and year.").assertExists()
        click("About this guidance")
        compose.onNodeWithText("Wording: human language review pending.").assertExists()

        finishGuide()
        compose.onNodeWithText("You finished the guide").assertExists()
        compose.onNodeWithText(
            "Review the form before submitting it. The completeness and correctness of your answers have not been checked.",
        ).assertExists()
        click("Read the Guide Again")
        compose.onNodeWithText("Original label on the form").assertExists()
    }

    @Test
    fun savedEnglishPreferenceSurvivesActivityRecreation() {
        chooseHomeLanguage(filipino = false)
        waitForText("Understand your form,\none part at a time.")
        compose.activityRule.scenario.recreate()
        waitForText("Understand your form,\none part at a time.")
        compose.onNodeWithText("Take Photo").assertIsEnabled()
        compose.onNodeWithText("Choose Photo").assertIsEnabled()
        compose.onNodeWithText("Try the Blank PSA Form").assertExists()
    }

    private val dateHeading = hasText("Date of Birth", substring = true, ignoreCase = true) and
        SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    private fun findBirthDate(filipino: Boolean) {
        repeat(50) {
            if (compose.onAllNodes(dateHeading).fetchSemanticsNodes().isNotEmpty()) return
            val skip = if (filipino) "Hindi Tugma — Laktawan" else "Does Not Match — Skip"
            val next = if (filipino) "Susunod" else "Next"
            when {
                exists(skip) -> click(skip)
                exists(next) -> click(next)
                else -> throw AssertionError("Real OCR guide did not contain a recognized birth-date label")
            }
        }
        throw AssertionError("The public sample guide exceeded the bounded navigation check")
    }

    private fun finishGuide() {
        repeat(50) {
            when {
                exists("You finished the guide") -> return
                exists("Does Not Match — Skip") -> click("Does Not Match — Skip")
                exists("Finish Guide") -> click("Finish Guide")
                exists("Next") -> click("Next")
                else -> throw AssertionError("Guide navigation has no reachable next/finish action")
            }
        }
        assertTrue("Guide did not finish within the bounded field count", exists("You finished the guide"))
    }

    private fun click(text: String) {
        val node = compose.onNodeWithText(text)
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
        compose.waitForIdle()
    }

    /** The compact header shows the current language and toggles the home locale. */
    private fun chooseHomeLanguage(filipino: Boolean) {
        compose.waitUntil(15_000) { exists("Filipino") || exists("English") }
        val desired = if (filipino) "Filipino" else "English"
        if (!exists(desired)) click(if (filipino) "English" else "Filipino")
        waitForText(desired)
    }

    private fun exists(text: String): Boolean =
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun waitForText(text: String, timeout: Long = 15_000) {
        compose.waitUntil(timeoutMillis = timeout) { exists(text) }
    }
}
