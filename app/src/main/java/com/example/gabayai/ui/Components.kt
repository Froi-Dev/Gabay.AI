package com.example.gabayai.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.gabayai.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.WifiOff

fun word(fil: Boolean, filipino: String, english: String) = if (fil) filipino else english

@Composable
fun Screen(scrollKey: Any? = Unit, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.runtime.key(scrollKey) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp), content = content)
    }
}
@Composable
fun Brand() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(painterResource(R.drawable.gabay_mark), null, Modifier.size(48.dp))
        Column {
            Row { Text("Gabay", style = MaterialTheme.typography.titleLarge, color = GabayBlue); Text(".AI", style = MaterialTheme.typography.titleLarge, color = GabayRed) }
            Text("Gabay mo, kahit offline.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun AppHeader(fil: Boolean, onLanguage: (() -> Unit)? = null) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Image(painterResource(R.drawable.gabay_mark), null, Modifier.size(38.dp))
            Text(buildAnnotatedString {
                withStyle(SpanStyle(color = GabayBlue)) { append("Gabay") }
                withStyle(SpanStyle(color = GabayRed)) { append(".AI") }
            }, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            if (onLanguage != null && !largeText) LanguageButton(fil, onLanguage)
            else if (onLanguage == null && !largeText) {
                Icon(Icons.Outlined.WifiOff, null, Modifier.size(20.dp), tint = GabayBlue)
                Text("Offline", style = MaterialTheme.typography.bodyMedium, color = GabayBlue)
            }
        }
        if (onLanguage != null && largeText) LanguageButton(fil, onLanguage, Modifier.fillMaxWidth())
    }
}

@Composable
private fun LanguageButton(fil: Boolean, action: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(action, modifier.heightIn(min = 56.dp).semantics {
        contentDescription = word(fil, "Palitan ang wika. Kasalukuyang wika: Filipino.", "Change language. Current language: English.")
    }, shape = RoundedCornerShape(8.dp)) {
        Text(if (fil) "Filipino" else "English", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Outlined.SwapHoriz, null, Modifier.size(22.dp))
    }
}

/** Keep actions reachable in landscape and at very large Android font settings. */
@Composable
fun ActionDock(content: @Composable ColumnScope.() -> Unit) {
    val maximumHeight = (LocalConfiguration.current.screenHeightDp * .45f).dp
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        Column(Modifier.fillMaxWidth().heightIn(max = maximumHeight).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
@Composable
fun Heading(text: String) { Text(text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite }) }
@Composable
fun Primary(text: String, action: () -> Unit, enabled: Boolean = true, height: Int = 64) {
    Button(action, Modifier.fillMaxWidth().heightIn(min = height.coerceAtLeast(64).dp), enabled = enabled, shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(16.dp)) {
        Text(text, textAlign = TextAlign.Center)
    }
}
@Composable
fun Secondary(text: String, action: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(action, Modifier.fillMaxWidth().heightIn(min = 64.dp), enabled = enabled, shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(16.dp)) {
        Text(text, textAlign = TextAlign.Center)
    }
}
@Composable
fun Notice(text: String, error: Boolean = false) {
    Surface(color = if (error) MaterialTheme.colorScheme.errorContainer else Guidance, shape = MaterialTheme.shapes.medium) {
        Text(text, Modifier.fillMaxWidth().padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
    }
}
