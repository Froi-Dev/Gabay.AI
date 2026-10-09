package com.example.gabayai.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.gabayai.domain.GuideLanguage

@Composable
fun LanguageScreen(language: GuideLanguage, choose: (GuideLanguage) -> Unit, onContinue: () -> Unit, onBack: () -> Unit) {
    val fil = language == GuideLanguage.FILIPINO
    Scaffold(containerColor = Color.White, contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = { AppHeader(fil) }, bottomBar = {
        ActionDock {
            Primary(word(fil, "Tingnan ang Paliwanag", "Show Explanations"), onContinue, height = 64)
            Secondary(word(fil, "Bumalik", "Back"), onBack)
        }
    }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Heading(word(fil, "Anong wika ang mas madali?", "Which language is easier?"))
            Text(word(fil, "Mananatili ang orihinal na label ng form.", "The original form labels stay visible."), style = MaterialTheme.typography.bodyMedium)
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GuideLanguage.entries.forEach { option ->
                val available = option == GuideLanguage.FILIPINO || option == GuideLanguage.ENGLISH
                Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).border(2.dp,
                    if (language == option) GabayBlue else MaterialTheme.colorScheme.outline,
                    androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).selectable(
                    selected = language == option, enabled = available, role = Role.RadioButton,
                    onClick = { choose(option) }).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RadioButton(selected = language == option, onClick = null, enabled = available)
                    Text(option.displayName, Modifier.weight(1f), color = if (available) Ink else Muted)
                }
                if (option == GuideLanguage.ENGLISH) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Text(word(fil, "Iba pang wika · wala pang nasuring paliwanag", "Other languages · reviewed explanations pending"), style = MaterialTheme.typography.bodyMedium, color = Muted)
                }
            }
            }
            Text(word(fil, "Ang kasalukuyang Filipino at English na wording ay kailangan pa ng human language review.", "Current Filipino and English wording still needs human language review."), style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
    }
}
