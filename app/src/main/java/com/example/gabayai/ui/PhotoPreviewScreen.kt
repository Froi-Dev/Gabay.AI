package com.example.gabayai.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*

@Composable
fun PhotoPreviewScreen(state: ScanState, fil: Boolean, analyze: () -> Unit, cancel: () -> Unit,
    retake: () -> Unit, back: () -> Unit, choosePhoto: () -> Unit) {
    val busy = state.busy
    Scaffold(containerColor = Color.White, contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { AppHeader(fil) }, bottomBar = {
            ActionDock {
                if (busy) Secondary(if (state.stage == ScanStage.CANCELLING) word(fil, "Itinitigil…", "Stopping…") else word(fil, "Kanselahin", "Cancel"), cancel, state.stage != ScanStage.CANCELLING)
                else {
                    if (state.photo != null) Primary(if (state.error) word(fil, "Subukan Muli", "Try Again") else word(fil, "Gamitin ang Picture", "Use Photo"), analyze, height = 72)
                    Secondary(word(fil, "Ulitin ang Picture", "Retake Photo"), retake)
                    if (state.photo == null || state.error) Secondary(word(fil, "Pumili ng Picture", "Choose Photo"), choosePhoto)
                }
            }
        }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(word(fil, "Hakbang 2 · Tingnan ang picture", "Step 2 · Check your photo"), color = GabayBlue, style = MaterialTheme.typography.bodyMedium)
            Heading(word(fil, "Malinaw ba ang picture?", "Is the photo clear?"))
            Text(word(fil, "Kaya mo bang basahin ang mga salita sa picture? Kung malinaw, pindutin ang “Gamitin ang Picture” sa ibaba.", "Can you read the words in the photo? If they are clear, press “Use Photo” below."))
            state.photo?.let { Image(it.bitmap.asImageBitmap(), word(fil, "Picture ng form", "Photo of the form"), Modifier.fillMaxWidth().heightIn(max = 380.dp)) }
            if (busy) {
                CircularProgressIndicator(Modifier.size(28.dp))
                Text(when (state.stage) {
                    ScanStage.PREPARING -> word(fil, "Inihahanda ang picture…", "Preparing the photo…")
                    ScanStage.OCR -> word(fil, "Binabasa ang form…", "Reading the form…")
                    ScanStage.MODEL_LOADING -> word(fil, "Inihahanda ang dagdag na pagkilala. Maaaring tumagal ito nang ilang sandali…", "Preparing extra recognition. This may take a little while…")
                    ScanStage.SEMANTIC -> word(fil, "Sinusubukan ang iba pang label…", "Checking other labels…")
                    ScanStage.CANCELLING -> word(fil, "Itinitigil ang pagbasa. Sandali lamang…", "Stopping the reading. Please wait…")
                    else -> word(fil, "Hinahanap ang mga label…", "Recognizing labels…")
                }, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            state.problem?.let { Notice(it.message(fil), error = true) }
            TextButton(back, Modifier.heightIn(min = 56.dp), enabled = state.stage != ScanStage.CANCELLING) { Text(word(fil, "Bumalik", "Back")) }
        }
    }
}
