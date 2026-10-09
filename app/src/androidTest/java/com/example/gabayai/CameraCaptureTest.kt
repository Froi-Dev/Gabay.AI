package com.example.gabayai

import android.Manifest
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Requires a physical rear camera aimed at the user-authorized form. This test
 * captures actual CameraX pixels, never a file-backed or substituted camera feed.
 * It records no image, screenshot, OCR text, or scene-specific field result.
 */
@RunWith(AndroidJUnit4::class)
class CameraCaptureTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun actualRearCameraCapturesPreviewAndReachesOfflineOcrOutcome() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant $packageName ${Manifest.permission.CAMERA}",
        ).use { descriptor ->
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                // Drain the permission command without printing device output.
                val result = input.bufferedReader().readText()
                assertTrue("Camera permission could not be granted to the installed app", result.isBlank())
            }
        }

        try {
            if (exists("English")) click("English")
            waitForText("Intindihin ang form,\nisang bahagi sa bawat hakbang.")
            click("Kuhanan ng Picture")
            waitForText("Ipasok sa frame ang form")
            // Enabled requires both a bound ImageCapture and actual STREAMING
            // preview frames; no synthetic camera signal is introduced here.
            compose.waitUntil(timeoutMillis = 45_000) {
                compose.onAllNodes(hasText("Kuhanan ng Picture") and isEnabled())
                    .fetchSemanticsNodes().isNotEmpty()
            }
            click("Kuhanan ng Picture", scroll = false)
            waitForText("Gamitin ang Picture", 45_000)
            compose.onNodeWithContentDescription("Picture ng form").assertExists()
            compose.onNodeWithText("Gamitin ang Picture").assertIsEnabled()
            click("Gamitin ang Picture")

            val recoverableError = "Hindi mabasa ang picture. Ilapit ang camera at iwasan ang anino o silaw, saka subukan muli."
            compose.waitUntil(timeoutMillis = 120_000) {
                exists("Anong wika ang mas madali?") || exists(recoverableError)
            }
            if (exists("Anong wika ang mas madali?")) {
                compose.onNodeWithText("Mananatili ang orihinal na label ng form.").assertExists()
                compose.onNodeWithText("Tingnan ang Paliwanag").assertIsEnabled()
            } else {
                // An actual OCR failure must preserve a corrective action and
                // the captured preview, never a fabricated guide or field match.
                compose.onNodeWithText(recoverableError).assertExists()
                compose.onNodeWithText("Subukan Muli").assertIsEnabled()
                compose.onNodeWithText("Ulitin ang Picture").assertIsEnabled()
                compose.onNodeWithContentDescription("Picture ng form").assertExists()
            }
        } finally {
            // Explicitly end the temporary document session even if an assertion
            // fails. The ViewModel closes native resources and deletes its files.
            compose.activityRule.scenario.onActivity { it.viewModelStore.clear() }
        }
    }

    private fun click(text: String, scroll: Boolean = true) {
        val node = compose.onNodeWithText(text)
        if (scroll && !node.isDisplayed()) node.performScrollTo()
        node.performClick()
        compose.waitForIdle()
    }

    private fun exists(text: String): Boolean =
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun waitForText(text: String, timeout: Long = 15_000) {
        compose.waitUntil(timeoutMillis = timeout) { exists(text) }
    }
}
