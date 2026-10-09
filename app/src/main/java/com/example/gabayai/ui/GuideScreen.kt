package com.example.gabayai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.example.gabayai.domain.*

@Composable
fun GuideScreen(state: ScanState, language: GuideLanguage, onLanguage: () -> Unit, onNew: () -> Unit) {
    val fil = language == GuideLanguage.FILIPINO
    val result = state.result ?: return
    val matches = result.matches
    val scanKey = state.sessionId
    var index by rememberSaveable(scanKey) { mutableIntStateOf(0) }
    var finished by rememberSaveable(scanKey) { mutableStateOf(false) }
    var accepted by rememberSaveable(scanKey) { mutableStateOf(listOf<Int>()) }
    var skipped by rememberSaveable(scanKey) { mutableStateOf(listOf<Int>()) }
    var compared by rememberSaveable(scanKey) { mutableStateOf(listOf<Int>()) }
    val safeIndex = index.coerceIn(0, (matches.size - 1).coerceAtLeast(0))
    val match = matches.getOrNull(safeIndex)
    // Save only an item position, never OCR labels/answers in Android's state bundle.
    val itemKey = safeIndex
    val context = LocalContext.current
    val speech = remember(scanKey, context) { OfflineSpeech(context) }
    DisposableEffect(speech) { onDispose { speech.close() } }
    LaunchedEffect(itemKey, finished, language) { speech.stop() }
    val needsConfirmation = match?.requiresConfirmation == true && itemKey !in accepted
    val largeText = LocalDensity.current.fontScale >= 1.2f
    fun advance() { if (safeIndex == matches.lastIndex) finished = true else index = safeIndex + 1 }

    Scaffold(containerColor = Color.White, contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { AppHeader(fil, onLanguage) },
        bottomBar = {
            if (!finished && match != null) ActionDock {
                    if (needsConfirmation) {
                        Primary(word(fil, "Tugma — Ipakita ang Paliwanag", "Matches — Show Explanation"), {
                            accepted = accepted + itemKey
                            skipped = skipped - itemKey
                        }, height = 64)
                        Secondary(word(fil, "Hindi Tugma — Laktawan", "Does Not Match — Skip"), {
                            skipped = (skipped + itemKey).distinct()
                            advance()
                        })
                        if (safeIndex > 0) TextButton({ index = safeIndex - 1 }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(word(fil, "Nauna", "Previous"))
                        }
                    } else if (largeText) {
                        Primary(if (safeIndex == matches.lastIndex) word(fil, "Tapusin ang Gabay", "Finish Guide") else word(fil, "Susunod", "Next"), { advance() })
                        Secondary(word(fil, "Nauna", "Previous"), { index = safeIndex - 1 }, enabled = safeIndex > 0)
                    } else {
                        Primary(if (safeIndex == matches.lastIndex) word(fil, "Tapusin ang Gabay", "Finish Guide") else word(fil, "Susunod", "Next"), { advance() }, height = 72)
                        Secondary(word(fil, "Nauna", "Previous"), { index = safeIndex - 1 }, safeIndex > 0)
                    }
            }
        }) { insets ->
        if (matches.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Heading(word(fil, "Wala pang malinaw na label", "No clear label found yet"))
                Text(word(fil, "Kuhanan nang mas malapit ang bahaging kailangan mo. Isama ang heading at mga label; iwasan ang glare at malabong picture.", "Take a closer photo of the section you need. Include its heading and labels; avoid glare and blur."))
                Text(word(fil, "Hindi magbibigay ng paliwanag kapag hindi tiyak ang nabasa.", "An explanation is not shown when the text cannot be identified."), style = MaterialTheme.typography.bodyMedium, color = Muted)
                UnknownText(result.unknownLines, fil)
                Primary(word(fil, "Kuhanan ng Bagong Picture", "Take a New Photo"), onNew, height = 64)
            }
        } else if (finished) {
            Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Icon(Icons.Outlined.TaskAlt, null, Modifier.size(44.dp), tint = GabayBlue)
                Heading(word(fil, "Natapos mo ang gabay", "You finished the guide"))
                Text(word(fil, "Balikan ang form bago isumite. Hindi nasuri kung kumpleto at tama ang mga sagot mo.", "Review the form before submitting it. The completeness and correctness of your answers have not been checked."))
                if (skipped.isNotEmpty()) Text(word(fil, "May ${skipped.size} mungkahing nilaktawan. Ihambing ang mga bahaging iyon sa orihinal na form.", "You skipped ${skipped.size} suggestions. Compare those sections with the original form."), style = MaterialTheme.typography.bodyMedium)
                if (result.unknownLines.isNotEmpty()) Text(word(fil, "May text sa picture na walang nakumpirmang kahulugan. Hindi lahat ng nakuhanan ay kasama sa gabay.", "Some photographed text has no confirmed meaning. The guide does not cover everything in the photo."), style = MaterialTheme.typography.bodyMedium, color = Muted)
                Primary(word(fil, "Basahin Muli ang Gabay", "Read the Guide Again"), { index = 0; finished = false }, height = 64)
                Secondary(word(fil, "Kuhanan ng Bagong Picture", "Take a New Photo"), onNew)
            }
        } else if (match != null) key(scanKey, itemKey) {
            var about by rememberSaveable { mutableStateOf(false) }
            var fullPhoto by rememberSaveable { mutableStateOf(false) }
            val explanation = match.field.explanation(language)
            Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(word(fil, "Bahagi ${safeIndex + 1} sa ${matches.size}", "Part ${safeIndex + 1} of ${matches.size}"), style = MaterialTheme.typography.bodyMedium, color = Muted,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    Box(Modifier.fillMaxWidth().height(6.dp).background(Guidance)) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth((safeIndex + 1f) / matches.size).background(GabayYellow))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(word(fil, "Orihinal na label sa form", "Original label on the form"), style = MaterialTheme.typography.bodyMedium, color = Muted)
                    Text(match.line.text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    match.context?.let { context ->
                        Text("${context.role.label(language) ?: context.originalLabel} · ${context.originalLabel}", style = MaterialTheme.typography.bodyMedium, color = GabayBlue)
                    }
                }
                state.photo?.let { FieldPhotoStrip(it.bitmap, match, fil) }
                if (safeIndex == 0) OptionalMatchingNotice(result, fil)
                if (needsConfirmation) {
                    Text(word(fil, "Ito ba ang label sa picture?", "Is this the label in the photo?"), style = MaterialTheme.typography.titleLarge)
                    Text(match.field.label, color = GabayBlue)
                    Text(word(fil, "Ihambing muna sa picture. Ipapakita ang paliwanag kapag kinumpirma mo ang tugma.", "Compare it with the photo first. The explanation appears after you confirm the match."), style = MaterialTheme.typography.bodyMedium, color = Muted)
                } else if (explanation != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(word(fil, "Ang ibig sabihin", "What it means"), style = MaterialTheme.typography.bodyMedium, color = Muted)
                        Text(explanation.title, style = MaterialTheme.typography.titleLarge, color = GabayBlue)
                        Text(explanation.text, style = MaterialTheme.typography.bodyLarge)
                    }
                    Secondary(if (speech.speaking) word(fil, "Itigil ang Pagbasa", "Stop Reading") else word(fil, "Pakinggan ang Paliwanag", "Listen to Explanation"), {
                        if (speech.speaking) speech.stop()
                        else speech.speak(buildString {
                            match.context?.role?.label(language)?.let { append(it); append(". ") }
                            append(explanation.title); append(". "); append(explanation.text)
                            explanation.example?.let { append(". "); append(word(fil, "Halimbawa lamang. ", "Example only. ")); append(it) }
                        }, language)
                    })
                    if (speech.unavailable) Notice(word(fil, "Walang naka-install na offline na boses para sa wikang ito. Maaari mong basahin ang paliwanag dito.", "An installed offline voice is not available for this language. You can read the explanation here."))
                    explanation.example?.let { example ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.width(3.dp).height(62.dp).background(GabayBlue))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(word(fil, "Halimbawa lamang", "Example only"), style = MaterialTheme.typography.bodyMedium, color = Muted)
                                Text(example)
                            }
                        }
                    }
                    val comparisonLabel = word(fil, "Naihahambing ko na sa orihinal", "I have compared it with the original")
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).toggleable(value = itemKey in compared, role = Role.Checkbox,
                        onValueChange = { checked -> compared = if (checked) (compared + itemKey).distinct() else compared - itemKey })
                        .semantics { contentDescription = comparisonLabel }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(itemKey in compared, null)
                        Text(comparisonLabel, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Text(word(fil, "Wala pang paliwanag sa wikang ito. Pumili ng Filipino o English.", "An explanation is not available in this language. Choose Filipino or English."))
                    Secondary(word(fil, "Palitan ang Wika", "Change Language"), onLanguage)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                state.photo?.let { photo ->
                    TextButton({ fullPhoto = !fullPhoto }, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Outlined.Description, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                        Text(if (fullPhoto) word(fil, "Itago ang Buong Picture", "Hide Full Photo") else word(fil, "Tingnan ang Buong Picture", "View Full Photo"), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (fullPhoto) FullFieldPhoto(photo.bitmap, match, fil)
                }
                TextButton({ about = !about }, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp).semantics { stateDescription = word(fil, if (about) "Bukas" else "Nakasara", if (about) "Expanded" else "Collapsed") }) {
                    Icon(if (about) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                    Text(word(fil, "Tungkol sa gabay", "About this guidance"), style = MaterialTheme.typography.bodyMedium)
                }
                if (about) GuidanceDetails(match, result, explanation, fil)
                TextButton(onLanguage, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(word(fil, "Palitan ang Wika", "Change Language"), style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onNew, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(word(fil, "Kuhanan ng Bagong Picture", "Take a New Photo"), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun OptionalMatchingNotice(result: RecognitionResult, fil: Boolean) {
    when {
        result.warnings.any { it.contains("needs more free storage") } -> Notice(word(fil, "Ginagamit natin ang karaniwang gabay. Kulang ang storage para sa dagdag na pagkilala.", "The regular guide is available. Extra recognition needs more free phone storage."))
        result.warnings.any { it.contains("needs more free memory") } -> Notice(word(fil, "Ginagamit natin ang karaniwang gabay. Isara ang ibang app kung gusto mong subukan ang dagdag na pagkilala.", "The regular guide is available. Close other apps before trying extra recognition."))
        result.warnings.any { it.contains("Optional semantic matching could not run") } -> Notice(word(fil, "Hindi natapos ang dagdag na pagkilala. Nandito pa ang karaniwang gabay.", "Extra recognition did not finish. The regular guide is still available."))
    }
}

@Composable
private fun GuidanceDetails(match: FieldMatch, result: RecognitionResult, explanation: Explanation?, fil: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(when (match.method) {
            MatchMethod.EXACT -> word(fil, "Pagkilala: eksaktong label, gamit ang mga tuntunin.", "Recognition: exact label, using rules.")
            MatchMethod.FUZZY -> word(fil, "Pagkilala: magkahawig na spelling, gamit ang mga tuntunin. Kinailangan ang paghahambing mo.", "Recognition: similar spelling, using rules. Your comparison was needed.")
            MatchMethod.SEMANTIC -> word(fil, "Pagkilala: mungkahi ng local AI embedding model. Ang pagkumpirma mo ay hindi garantiya ng accuracy.", "Recognition: a local AI embedding suggestion. Confirmation does not guarantee accuracy.")
        }, style = MaterialTheme.typography.bodyMedium, color = Muted)
        if (match.field.source.grounding == Grounding.SOURCE_BACKED) {
            Text(word(fil, "Batay sa PSA source form: ${match.field.source.excerpt.orEmpty()}", "Based on the PSA source form: ${match.field.source.excerpt.orEmpty()}"), style = MaterialTheme.typography.bodyMedium)
            match.field.source.url?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Muted) }
        } else {
            Text(word(fil, "Karaniwang kahulugan ng salita ang paliwanag. Hindi nito kinukumpirma ang requirements o pagtanggap ng form.", "The explanation gives the ordinary word meaning. It does not confirm form requirements or acceptance."), style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
        if (match.context == null && match.field.formId == null && match.field.kind in setOf("first_name", "last_name", "middle_name", "full_name")) {
            Text(word(fil, "Hindi tiyak kung kaninong pangalan ang hinihingi. Tingnan ang heading ng bahaging ito.", "Whose name is requested is uncertain. Check this section’s heading."), style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
        Text(if (explanation?.review == TranslationReview.HUMAN_REVIEWED) word(fil, "Wording: may naitalang human language review.", "Wording: a human language review is recorded.")
            else word(fil, "Wording: kailangan pa ng language review.", "Wording: human language review pending."), style = MaterialTheme.typography.bodyMedium, color = Muted)
        if (result.form == null) Text(word(fil, "Hindi pa tiyak ang uri ng form mula sa picture na ito.", "The form’s identity is unconfirmed from this photo."), style = MaterialTheme.typography.bodyMedium, color = Muted)
        if (result.warnings.any { it.contains("part of this long form") }) Text(word(fil, "Bahagi lamang ng mahabang form ang nabasa. Kuhanan nang hiwalay ang natitirang bahagi.", "Only part of this long form was analyzed. Photograph the remaining section separately."), style = MaterialTheme.typography.bodyMedium)
        if (result.warnings.any { it.contains("Optional semantic matching could not run") }) Text(word(fil, "Hindi tumakbo ang dagdag na pagkilala. Mga tuntunin ang ginamit sa gabay na ito.", "Extra recognition could not run. This guide uses rules."), style = MaterialTheme.typography.bodyMedium)
        UnknownText(result.unknownLines, fil)
    }
}

@Composable
private fun UnknownText(lines: List<OcrLine>, fil: Boolean) {
    if (lines.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    TextButton({ expanded = !expanded }, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
        Text(word(fil, "Text na walang paliwanag (${lines.size})", "Text without guidance (${lines.size})"), style = MaterialTheme.typography.bodyMedium)
    }
    if (expanded) {
        Text(word(fil, "Maaaring heading o ibang text ito. Ihambing sa orihinal o magtanong sa nagbigay ng form.", "This may include headings or other text. Compare it with the original or ask the form issuer."), style = MaterialTheme.typography.bodyMedium, color = Muted)
        lines.forEach { Text(it.text, style = MaterialTheme.typography.bodyMedium) }
    }
}
