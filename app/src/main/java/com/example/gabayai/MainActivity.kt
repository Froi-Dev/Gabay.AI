package com.example.gabayai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.example.gabayai.ui.*
import com.example.gabayai.domain.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GabayAITheme { GabayApp() } }
    }
}

@Composable
fun GabayApp(vm: ScanViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val language by vm.language.collectAsStateWithLifecycle()
    val semanticEnabled by vm.semanticEnabled.collectAsStateWithLifecycle()
    val settingError by vm.settingError.collectAsStateWithLifecycle()
    val fil = language == GuideLanguage.FILIPINO
    val nav = rememberNavController()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.prepare(it); nav.navigate("preview") { popUpTo("home"); launchSingleTop = true } }
    }
    fun restart() { vm.reset(); nav.popBackStack("home", false) }
    fun newPhoto() { vm.reset(); nav.navigate("camera") { popUpTo("home"); launchSingleTop = true } }
    fun pickPhoto() { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    LaunchedEffect(state.stage) {
        // READY is retained across configuration changes. Navigate only for an
        // analysis completed on preview, never over a restored guide entry.
        if (state.stage == ScanStage.READY && nav.currentBackStackEntry?.destination?.route == "preview") {
            nav.navigate("language") { popUpTo("preview"); launchSingleTop = true }
        }
    }
    Scaffold(topBar = {
        if (settingError) Notice(word(fil, "Hindi na-save ang setting. Subukan muli; mananatili muna ang dating setting.", "The setting could not be saved. Try again; the previous setting is still selected."), error = true)
    }) { insets ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(insets)) {
            composable("home") {
                HomeScreen(fil, vm::chooseLanguage, { nav.navigate("camera") },
                    ::pickPhoto,
                    { vm.sample(); nav.navigate("preview") }, { nav.navigate("about") })
            }
            composable("camera") {
                CameraScreen(fil, vm::captureFile, { file ->
                    vm.prepare(android.net.Uri.fromFile(file), file)
                    nav.navigate("preview") { popUpTo("camera") { inclusive = true } }
                }, vm::discardCapture, { nav.popBackStack() }, ::pickPhoto)
            }
            composable("preview") {
                if (state.stage == ScanStage.EMPTY && state.photo == null) {
                    // A restored route cannot recover a private, intentionally transient photo.
                    LaunchedEffect(Unit) { restart() }
                } else {
                    BackHandler { if (state.busy) vm.cancelAnalysis() else restart() }
                    PhotoPreviewScreen(state, fil, vm::analyze, vm::cancelAnalysis, ::newPhoto,
                        { if (state.busy) vm.cancelAnalysis() else restart() }, ::pickPhoto)
                }
            }
            composable("language") {
                if (state.result == null) LaunchedEffect(Unit) { restart() }
                else LanguageScreen(language, vm::chooseLanguage, {
                    if (nav.previousBackStackEntry?.destination?.route == "guide") {
                        // Keep the current field, user confirmations and manual
                        // comparisons when only the wording changes.
                        nav.popBackStack()
                    } else nav.navigate("guide") { popUpTo("preview"); launchSingleTop = true }
                }, { nav.popBackStack() })
            }
            composable("guide") {
                if (state.result == null) LaunchedEffect(Unit) { restart() }
                else GuideScreen(state, language, { nav.navigate("language") }, ::newPhoto)
            }
            composable("about") {
                Screen {
                    Brand()
                    Heading(word(fil, "Gabay mo, kahit offline.", "Guidance, even offline."))
                    Text(word(fil, "Binabasa ng bundled ML Kit model ang printed text sa phone. Eksaktong label at magkahawig na spelling ang unang hinahanap gamit ang mga tuntunin.", "Bundled ML Kit reads printed text on the phone. Exact labels and similar spelling are matched first using rules."))
                    Text(word(fil, "Nakaimbak sa phone ang source-backed PSA meanings at karaniwang kahulugan ng mga field. Hindi nito sinusuri ang sagot, requirements, eligibility o pagtanggap ng form.", "PSA source-backed meanings and common field vocabulary are stored on this phone. Answers, requirements, eligibility and form acceptance are not checked."))
                    Notice(word(fil, "Kailangan pa ng human language review ang Filipino at English wording. Wala pang nasuring Cebuano, Ilocano, Hiligaynon o Bikol na paliwanag.", "Filipino and English wording still needs human language review. Reviewed Cebuano, Ilocano, Hiligaynon and Bikol explanations are not yet available."))
                    if (vm.semanticAvailable) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).toggleable(value = semanticEnabled, role = Role.Switch,
                            onValueChange = vm::enableSemantic), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Switch(semanticEnabled, null)
                            Text(word(fil, "Dagdag na Pagkilala sa English Labels", "Extra Recognition for English Labels"), Modifier.weight(1f))
                        }
                        Text("${vm.semanticModelName}: " + word(fil, "optional na embedding model, nasa phone. Mas malaki ang memory na ginagamit. Tatakbo lang kung kulang ang mga tuntunin; kailangang ikumpirma ang bawat mungkahi. Hindi pa nasusukat ang accuracy sa lahat ng form.", "optional on-phone embedding model. Uses extra memory. Runs only when rules are insufficient; every suggestion needs confirmation. Accuracy across forms has not been measured."), style = MaterialTheme.typography.bodyMedium)
                        val storage = if (vm.semanticStorageBytes >= 1_000_000_000L)
                            "${kotlin.math.ceil((vm.semanticStorageBytes + 64L * 1024 * 1024) / 100_000_000.0) / 10} GB"
                        else "${(vm.semanticStorageBytes + 64L * 1024 * 1024) / 1_000_000} MB"
                        Text(word(fil, "Kailangan ng humigit-kumulang $storage na bakanteng storage sa unang gamit. Kapag kulang ang storage o memory, gagamitin pa rin ang karaniwang gabay.", "First use needs about $storage of free storage. If storage or memory is low, the regular guide remains available."), style = MaterialTheme.typography.bodyMedium)
                    } else Text(word(fil, "Lightweight build: OCR at mga tuntunin. Walang semantic embedding model sa APK na ito.", "Lightweight build: OCR and rules. This APK does not include a semantic embedding model."))
                    Text(word(fil, "Walang cloud inference o server. Pansamantalang picture lamang; nililinis sa bagong scan, pagsara ng session o susunod na pagbukas matapos mapilitang isara. Wika at setting lang ang sine-save.", "No cloud inference or server. Temporary photos are cleared on a new scan, session end or next launch after a forced stop. Only language and settings are saved."))
                    Secondary(word(fil, "Bumalik", "Back"), { nav.popBackStack() })
                }
            }
        }
    }
}
