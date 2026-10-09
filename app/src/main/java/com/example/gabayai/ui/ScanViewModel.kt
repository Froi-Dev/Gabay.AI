package com.example.gabayai.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gabayai.data.FormRepository
import com.example.gabayai.data.PreferencesRepository
import com.example.gabayai.domain.*
import com.example.gabayai.imaging.*
import com.example.gabayai.semantic.SemanticEngineFactory
import com.example.gabayai.semantic.SemanticProgress
import com.example.gabayai.semantic.SemanticResourceException
import com.example.gabayai.semantic.SemanticResourceProblem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

class ScanViewModel(application: Application) : AndroidViewModel(application) {
    private val images = ImageRepository(application)
    private val ocr = OfflineOcr()
    private val forms = FormRepository(application)
    private val preferences = PreferencesRepository(application)
    private val semanticEngine = SemanticEngineFactory.create(application)
    val semanticAvailable: Boolean get() = semanticEngine.available
    val semanticModelName: String? get() = semanticEngine.modelName
    val semanticStorageBytes: Long get() = semanticEngine.modelStorageBytes
    private val mutableSettingError = MutableStateFlow(false)
    val settingError = mutableSettingError.asStateFlow()
    val language: StateFlow<GuideLanguage> = preferences.language.stateIn(
        viewModelScope, SharingStarted.Eagerly, GuideLanguage.FILIPINO,
    )
    val semanticEnabled: StateFlow<Boolean> = preferences.semanticEnabled
        .map { it && semanticAvailable }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val mutableState = MutableStateFlow(ScanState())
    val state = mutableState.asStateFlow()
    private var work: Job? = null

    init { images.clearTemporaryFiles() }

    fun captureFile(): File = images.createCaptureFile()
    fun discardCapture(file: File?) { deleteTemporary(file) }

    fun chooseLanguage(value: GuideLanguage) {
        viewModelScope.launch {
            try { preferences.setLanguage(value); mutableSettingError.value = false }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: java.io.IOException) { mutableSettingError.value = true }
        }
    }

    fun enableSemantic(value: Boolean) {
        viewModelScope.launch {
            try { preferences.setSemanticEnabled(value && semanticAvailable); mutableSettingError.value = false }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: java.io.IOException) { mutableSettingError.value = true }
        }
    }

    fun sample() {
        var file: File? = null
        beginPreparation(cleanup = { deleteTemporary(file) }) {
                withContext(Dispatchers.IO) {
                    // Keep the file reference outside withContext: prompt cancellation
                    // on its return must still delete an already copied sample.
                    val target = images.createCaptureFile().also { file = it }
                    getApplication<Application>().assets.open("sample/psa-birth.png").use { input ->
                        target.outputStream().buffered().use { output ->
                            val buffer = ByteArray(32 * 1024)
                            while (true) {
                                ensureActive()
                                val count = input.read(buffer)
                                if (count == -1) break
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    Uri.fromFile(target)
                }
        }
    }

    fun prepare(uri: Uri, capture: File? = null) {
        beginPreparation(cleanup = { deleteTemporary(capture) }) { uri }
    }

    private fun beginPreparation(cleanup: (() -> Unit)? = null, source: suspend () -> Uri) {
        val old = mutableState.value.photo
        val preparing = ScanState(stage = ScanStage.PREPARING)
        // Publish before navigation, even while a canceled native request drains.
        mutableState.value = preparing
        // Compose/render threads may retain the previous Bitmap for another frame.
        // Delete the private file and drop references; never recycle a displayed bitmap.
        replaceWork(cleanup = { deleteTemporary(old?.file); cleanup?.invoke() }) {
            try {
                val photo = images.prepare(source())
                currentCoroutineContext().ensureActive()
                mutableState.value = preparing.copy(photo = photo, stage = ScanStage.PREVIEW)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: ImagePreparationException) {
                mutableState.value = preparing.copy(stage = ScanStage.ERROR,
                    problem = ScanProblem.valueOf(failure.problem.name))
            } catch (_: Exception) {
                mutableState.value = preparing.copy(stage = ScanStage.ERROR, problem = ScanProblem.UNREADABLE)
            }
        }
    }

    fun analyze() {
        val photo = mutableState.value.photo ?: return
        if (work?.isCompleted == false) return
        val scanning = mutableState.value.copy(stage = ScanStage.OCR, problem = null, result = null)
        val useSemantic = semanticAvailable && semanticEnabled.value
        replaceWork {
            mutableState.value = scanning
            var failureStage = ScanProblem.OCR_FAILED
            try {
                val lines = ocr.recognize(photo.bitmap)
                if (lines.isEmpty()) {
                    mutableState.value = scanning.copy(stage = ScanStage.ERROR, problem = ScanProblem.NO_TEXT)
                    return@replaceWork
                }
                failureStage = ScanProblem.CATALOGUE_FAILED
                mutableState.value = scanning.copy(stage = ScanStage.MATCHING)
                val rules = forms.recognize(lines)
                val result = if (useSemantic && rules.unknownLines.isNotEmpty()) {
                    addSemanticSuggestions(rules, lines, scanning.sessionId)
                } else rules
                currentCoroutineContext().ensureActive()
                mutableState.value = scanning.copy(stage = ScanStage.READY, result = result)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.value = scanning.copy(stage = ScanStage.ERROR, problem = failureStage) }
        }
    }

    private suspend fun addSemanticSuggestions(rules: RecognitionResult, lines: List<OcrLine>, sessionId: String): RecognitionResult {
        return try {
            val fields = forms.definitions().filter { it.formId == null }
            val suggestions = withContext(Dispatchers.Default) {
                semanticEngine.suggest(rules.unknownLines, fields) { progress ->
                    mutableState.update { current ->
                        if (current.sessionId != sessionId || current.stage == ScanStage.CANCELLING) current
                        else current.copy(stage = if (progress == SemanticProgress.PREPARING_MODEL) ScanStage.MODEL_LOADING else ScanStage.SEMANTIC)
                    }
                }
            }
            FieldMatcher().mergeSuggestions(rules, suggestions, lines, fields)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: SemanticResourceException) {
            rules.copy(warnings = rules.warnings + if (failure.problem == SemanticResourceProblem.STORAGE)
                "Optional semantic matching needs more free storage." else "Optional semantic matching needs more free memory.")
        } catch (_: Exception) {
            // Optional inference failure never discards a valid deterministic guide.
            rules.copy(warnings = rules.warnings + "Optional semantic matching could not run. The available guide uses exact and fuzzy rules.")
        }
    }

    fun cancelAnalysis() {
        if (!mutableState.value.busy || mutableState.value.stage == ScanStage.CANCELLING) return
        mutableState.update { it.copy(stage = ScanStage.CANCELLING, problem = null, result = null) }
        replaceWork {
            mutableState.update { it.copy(stage = if (it.photo == null) ScanStage.EMPTY else ScanStage.PREVIEW, problem = null) }
        }
    }

    fun reset() {
        replaceWork {
            val old = mutableState.value.photo
            mutableState.value = ScanState()
            deleteTemporary(old?.file)
        }
    }

    /** A canceled replacement still drains its predecessor before it completes. */
    private fun replaceWork(cleanup: (() -> Unit)? = null, action: suspend () -> Unit) {
        val previous = work
        previous?.cancel()
        val next = viewModelScope.launch(start = CoroutineStart.LAZY) {
            // Without this, canceling a second request while it joins an OCR task
            // could abandon that join and let a third request overtake the first.
            withContext(NonCancellable) { previous?.join() }
            ensureActive()
            action()
        }
        // Register before starting: cleanup also runs when the scope is already
        // canceled, or the action is canceled before its body is entered.
        next.invokeOnCompletion { cleanup?.invoke() }
        work = next
        next.start()
    }

    private fun deleteTemporary(file: File?) {
        // File deletion is best effort; an OS/cache failure must not crash a late
        // camera callback. Startup cleanup retries any remaining private files.
        runCatching { images.delete(file) }
    }

    override fun onCleared() {
        val pending = work
        pending?.cancel()
        ocr.close() // defers native recognizer closure if an OCR task is still draining
        // Session shutdown must not race an in-flight native ONNX call. Replacing
        // work preserves the complete predecessor-drain chain until it finishes.
        if (pending == null || pending.isCompleted) runCatching { semanticEngine.close() }
        else pending.invokeOnCompletion { runCatching { semanticEngine.close() } }
        deleteTemporary(mutableState.value.photo?.file)
        mutableState.value = ScanState()
    }
}
