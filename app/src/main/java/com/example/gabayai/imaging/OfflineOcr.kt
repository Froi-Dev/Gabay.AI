package com.example.gabayai.imaging

import android.graphics.Bitmap
import com.example.gabayai.domain.OcrBox
import com.example.gabayai.domain.OcrLine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.Closeable
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Actual bundled Latin ML Kit inference. The model is included in the APK. */
class OfflineOcr : Closeable {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val requestMutex = Mutex()
    private val lifecycleLock = Any()
    private var running = false
    private var closing = false

    /**
     * The caller owns [bitmap] and must keep it alive until this call finishes.
     * ML Kit cannot cancel an already submitted Task: cancellation waits for that
     * Task to finish before releasing the bitmap and discards its result afterward.
     */
    suspend fun recognize(bitmap: Bitmap): List<OcrLine> = withContext(Dispatchers.Default) {
        requestMutex.withLock {
            coroutineContext.ensureActive()
            require(!bitmap.isRecycled) { "The picture has already been released." }
            synchronized(lifecycleLock) {
                check(!closing) { "The text recognizer has been closed." }
                running = true
            }
            try {
                val task = recognizer.process(InputImage.fromBitmap(bitmap, 0))
                // No background coroutine escapes this operation or holds the image
                // after cancellation has returned to its owner.
                val result = withContext(NonCancellable) { task.await() }
                coroutineContext.ensureActive()
                result.textBlocks.flatMap { block ->
                    block.lines.mapNotNull { line ->
                        val box = line.boundingBox ?: return@mapNotNull null
                        if (line.text.isBlank()) return@mapNotNull null
                        OcrLine(
                            text = line.text,
                            box = OcrBox(box.left, box.top, box.right, box.bottom),
                            confidence = line.confidence.takeIf { it.isFinite() && it in 0f..1f },
                        )
                    }
                }
            } finally {
                synchronized(lifecycleLock) {
                    running = false
                    if (closing) recognizer.close()
                }
            }
        }
    }

    /** A pending operation finishes safely; no new operation is accepted. */
    override fun close() {
        synchronized(lifecycleLock) {
            if (closing) return
            closing = true
            if (!running) recognizer.close()
        }
    }
}
