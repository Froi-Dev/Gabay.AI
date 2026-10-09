package com.example.gabayai.ui

import android.Manifest
import android.content.pm.PackageManager
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import android.util.Size
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class CameraSession {
    var active = false
    var generation = 0
    var pendingFile: File? = null

    fun current(token: Int): Boolean = active && generation == token
}

@Composable
fun CameraScreen(fil: Boolean, createFile: () -> File, onPhoto: (File) -> Unit,
    onDiscard: (File?) -> Unit, onBack: () -> Unit, onChoosePhoto: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var denied by rememberSaveable { mutableStateOf(false) }
    val activity = context.findActivity()
    var error by remember { mutableStateOf(false) }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var streamReady by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val session = remember { CameraSession() }
    val scope = rememberCoroutineScope()
    val currentPhoto by rememberUpdatedState(onPhoto)
    val currentDiscard by rememberUpdatedState(onDiscard)
    val currentCreate by rememberUpdatedState(createFile)
    fun leaveCamera() {
        // Invalidate before navigation. A native save can finish before dispose.
        session.active = false
        session.generation++
        onBack()
    }
    BackHandler { leaveCamera() }
    val preview = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    DisposableEffect(preview, camera) {
        preview.setOnTouchListener { view, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                camera?.let { activeCamera ->
                    val point = preview.meteringPointFactory.createPoint(event.x, event.y)
                    runCatching { activeCamera.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(point).build()) }
                }
                view.performClick()
            }
            true
        }
        onDispose { preview.setOnTouchListener(null) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        denied = !it
        if (it) { error = false; retry++ }
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Recheck after Settings or a one-time permission has changed.
                granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(granted, lifecycle, retry, error) {
        var disposed = false
        var provider: ProcessCameraProvider? = null
        var previewUseCase: Preview? = null
        var captureUseCase: ImageCapture? = null
        val token = ++session.generation
        session.active = granted && !error
        capture = null
        camera = null
        capturing = false
        streamReady = false
        val streamObserver = Observer<PreviewView.StreamState> {
            if (!disposed && session.current(token)) streamReady = it == PreviewView.StreamState.STREAMING
        }
        preview.previewStreamState.observe(lifecycle, streamObserver)
        if (granted && !error) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (!disposed && session.current(token)) try {
                    provider = future.get()
                    val viewfinder = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
                    val image = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
                            ResolutionStrategy(Size(1920, 2560), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build())
                        .setTargetRotation(preview.display?.rotation ?: android.view.Surface.ROTATION_0)
                        .build()
                    previewUseCase = viewfinder; captureUseCase = image
                    camera = provider?.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, viewfinder, image)
                    capture = image
                } catch (_: Exception) { if (!disposed && session.current(token)) error = true }
            }, ContextCompat.getMainExecutor(context))
        }
        onDispose {
            disposed = true
            session.active = false
            session.generation++
            capture = null
            camera = null
            streamReady = false
            preview.previewStreamState.removeObserver(streamObserver)
            val useCases = listOfNotNull(previewUseCase, captureUseCase).toTypedArray()
            if (useCases.isNotEmpty()) provider?.unbind(*useCases)
            // A capture can still complete after unbinding. Delete now and again
            // in its terminal callback rather than navigating from a stale result.
            currentDiscard(session.pendingFile)
            session.pendingFile = null
        }
    }
    val viewfinderHeight = (LocalConfiguration.current.screenHeightDp * .4f).coerceIn(220f, 400f).dp
    Scaffold(containerColor = Color.White, contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = { AppHeader(fil) },
        bottomBar = { ActionDock {
        Button(shape = RoundedCornerShape(8.dp), enabled = capture != null && streamReady && !capturing, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp), contentPadding = PaddingValues(16.dp), onClick = capturePhoto@{
            if (capturing) return@capturePhoto
            val imageCapture = capture ?: return@capturePhoto
            val token = session.generation
            if (!session.current(token)) return@capturePhoto
            capturing = true
            scope.launch {
                var file: File? = null
                try {
                    withContext(Dispatchers.IO) { file = currentCreate() }
                    ensureActive()
                    val saved = requireNotNull(file)
                    if (!session.current(token)) {
                        currentDiscard(saved)
                        return@launch
                    }
                    session.pendingFile = saved
                    imageCapture.targetRotation = preview.display?.rotation ?: android.view.Surface.ROTATION_0
                    imageCapture.takePicture(ImageCapture.OutputFileOptions.Builder(saved).build(), ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            if (session.pendingFile == saved) session.pendingFile = null
                            if (session.current(token)) {
                                capturing = false
                                session.active = false
                                session.generation++
                                currentPhoto(saved)
                            } else currentDiscard(saved)
                        }
                        override fun onError(exception: ImageCaptureException) {
                            if (session.pendingFile == saved) session.pendingFile = null
                            currentDiscard(saved)
                            if (session.current(token)) { capturing = false; error = true }
                        }
                    })
                } catch (cancelled: CancellationException) {
                    currentDiscard(file)
                    throw cancelled
                } catch (_: Exception) {
                    currentDiscard(file)
                    if (session.current(token)) { capturing = false; error = true }
                }
            }
        }) {
            if (capturing) CircularProgressIndicator(Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            else Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text(if (capturing) word(fil, "Kinukuhanan…", "Capturing…") else word(fil, "Kuhanan ng Picture", "Take Photo"))
        }
        Secondary(word(fil, "Bumalik", "Back"), ::leaveCamera)
    } }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(word(fil, "Hakbang 1 · Kuhanan ang form", "Step 1 · Photograph your form"), color = GabayBlue, style = MaterialTheme.typography.bodyMedium)
        Heading(if (fil) "Ipasok sa frame ang form" else "Fit the form in the frame")
        Text(if (fil) "Ilapit ang label. Pindutin ang picture para mag-focus, at panatilihing steady ang phone." else "Move close to the label. Tap the picture to focus, and hold the phone steady.", style = MaterialTheme.typography.bodyMedium)
        if (granted && !error) Box(Modifier.fillMaxWidth().height(viewfinderHeight).border(1.dp, MaterialTheme.colorScheme.outline).background(Ink), contentAlignment = Alignment.Center) {
            AndroidView(factory = { preview }, modifier = Modifier.fillMaxSize())
        }
        else {
            if (!granted) {
                Notice(word(fil, "Payagan ang camera para makakuha ng picture. Maaari ka ring pumili ng picture na nasa phone na.", "Allow camera access to take a photo. You can also choose a photo already on this phone."))
                val settingsNeeded = denied && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
                Primary(if (settingsNeeded) word(fil, "Buksan ang Camera Settings", "Open Camera Settings") else word(fil, "Payagan ang Camera", "Allow Camera"), {
                    if (settingsNeeded) runCatching {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    }.onFailure { error = true }
                    else launcher.launch(Manifest.permission.CAMERA)
                })
            } else {
                Notice(word(fil, "Hindi mabuksan ang camera. Isara ang ibang app na gumagamit ng camera, saka subukan muli.", "The camera could not open. Close other apps using the camera, then try again."), error = true)
                Primary(word(fil, "Subukan Muli", "Try Again"), { error = false; retry++ })
            }
            Secondary(word(fil, "Pumili ng Picture", "Choose Photo"), onChoosePhoto)
        }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
