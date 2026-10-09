package com.example.gabayai.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.gabayai.domain.GuideLanguage

@Composable
fun HomeScreen(fil: Boolean, choose: (GuideLanguage) -> Unit, onCamera: () -> Unit,
    onPick: () -> Unit, onSample: () -> Unit, onAbout: () -> Unit) {
    Scaffold(containerColor = Color.White, contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { AppHeader(fil) { choose(if (fil) GuideLanguage.ENGLISH else GuideLanguage.FILIPINO) } },
        bottomBar = {
            ActionDock {
                    Button(onCamera, Modifier.fillMaxWidth().heightIn(min = 72.dp), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(16.dp)) {
                        Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(24.dp)); Spacer(Modifier.width(10.dp))
                        Text(word(fil, "Kuhanan ng Picture", "Take Photo"))
                    }
                    OutlinedButton(onPick, Modifier.fillMaxWidth().heightIn(min = 64.dp), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(16.dp)) {
                        Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(22.dp)); Spacer(Modifier.width(10.dp))
                        Text(word(fil, "Pumili ng Picture", "Choose Photo"))
                    }
            }
        }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(4.dp))
            Heading(word(fil, "Intindihin ang form,\nisang bahagi sa bawat hakbang.", "Understand your form,\none part at a time."))
            Text(word(fil, "Tutulungan ka naming maintindihan ang nakasulat sa form.", "We will help you understand the words on your form."))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeStep("1", word(fil, "Kuhanan ng picture ang form.", "Take a photo of the form."))
                HomeStep("2", word(fil, "Tingnan kung malinaw ang picture.", "Check that the photo is clear."))
                HomeStep("3", word(fil, "Basahin o pakinggan ang paliwanag.", "Read or listen to each explanation."))
            }
            Box(Modifier.fillMaxWidth().height(132.dp).background(Guidance, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                // A simple document illustration, never an OCR result or a fake form.
                Canvas(Modifier.size(100.dp, 112.dp)) {
                    val stroke = 2.dp.toPx(); val left = size.width * .18f; val top = size.height * .09f
                    drawRoundRect(Color.White, Offset(left, top), Size(size.width * .64f, size.height * .82f), androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
                    drawRoundRect(GabayBlue, Offset(left, top), Size(size.width * .64f, size.height * .82f), androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()), style = Stroke(stroke))
                    repeat(4) { i -> val y = size.height * (.29f + i * .13f); drawLine(if (i == 1) GabayBlue else Muted.copy(alpha = .35f), Offset(size.width * .3f, y), Offset(size.width * .68f, y), stroke) }
                    drawLine(GabayYellow, Offset(size.width * .27f, size.height * .48f), Offset(size.width * .71f, size.height * .48f), 5.dp.toPx())
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp), tint = GabayBlue)
                Text(word(fil, "Sa phone lang. Kahit walang internet.", "On this phone. Even without internet."), style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            TextButton(onSample, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.Description, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                Text(word(fil, "Subukan sa Blankong PSA Form", "Try the Blank PSA Form"), style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onAbout, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                Text(word(fil, "Tungkol sa Offline na Gabay", "About Offline Guidance"), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun HomeStep(number: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Text(number, color = GabayBlue, style = MaterialTheme.typography.titleLarge)
        Text(text, Modifier.weight(1f))
    }
}
