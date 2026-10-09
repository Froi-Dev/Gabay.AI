package com.example.gabayai.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.Closeable
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** One normalized image is shared by the preview and OCR, so their coordinates agree. */
class PreparedPhoto internal constructor(
    val file: File,
    val bitmap: Bitmap,
) : Closeable {
    val width: Int = bitmap.width
    val height: Int = bitmap.height

    /** Call after the preview has been removed and any OCR operation has finished. */
    @Synchronized
    override fun close() {
        file.delete()
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}

enum class ImageProblem { UNREADABLE, UNSUPPORTED, TOO_LARGE, TOO_SMALL, STORAGE }

class ImagePreparationException(val problem: ImageProblem) : IOException(
    when (problem) {
        ImageProblem.UNREADABLE -> "This picture could not be opened. Try a clear JPG or PNG."
        ImageProblem.UNSUPPORTED -> "Use a JPG, PNG, WebP, or a supported HEIF picture."
        ImageProblem.TOO_LARGE -> "This picture is too large. Use the normal camera setting."
        ImageProblem.TOO_SMALL -> "The picture is too small to read. Take a closer, clearer photo."
        ImageProblem.STORAGE -> "More free phone storage is needed to prepare this picture."
    },
)

/**
 * No upload or document database. Files live only in the app's private cache.
 * Bounded streaming copy and header validation happen before pixel allocation.
 */
class ImageRepository(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val directory = File(context.applicationContext.cacheDir, "gabay-photos")

    fun createCaptureFile(): File = temporaryFile("capture-", ".jpg")

    suspend fun prepare(uri: Uri): PreparedPhoto {
        // withContext has prompt cancellation on return. Keep a reference so a completed
        // decode cannot leak if cancellation wins just before ownership reaches the UI.
        var prepared: PreparedPhoto? = null
        try {
            return withContext(Dispatchers.IO) {
                prepareInternal(uri).also { prepared = it }
            }
        } catch (error: Throwable) {
            prepared?.close()
            throw error
        }
    }

    fun clear(photo: PreparedPhoto?) {
        photo?.close()
    }

    /** Deletes only this repository's own direct cache files, including a failed capture. */
    fun delete(file: File?) {
        if (file != null && file.canonicalFile.parentFile == directory.canonicalFile) {
            file.delete()
        }
    }

    /** Use at application/session startup, before retaining a current photo. */
    fun clearTemporaryFiles() {
        directory.listFiles()?.forEach(::delete)
    }

    private fun temporaryFile(prefix: String, suffix: String): File {
        if (!directory.mkdirs() && !directory.isDirectory) {
            throw IOException("The temporary picture could not be created.")
        }
        if (directory.usableSpace < 5L * 1024 * 1024) fail(ImageProblem.STORAGE)
        return File.createTempFile(prefix, suffix, directory)
    }

    private suspend fun prepareInternal(uri: Uri): PreparedPhoto {
        val source = temporaryFile("source-", ".image")
        var decoded: Bitmap? = null
        var normalized: Bitmap? = null
        var output: File? = null
        var transferred = false
        try {
            // Never trust a provider's reported size or keep an unbounded byte array.
            resolver.openInputStream(uri)?.use { input ->
                source.outputStream().buffered().use { destination ->
                    val buffer = ByteArray(32 * 1024)
                    var copied = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count == -1) break
                        copied += count
                        if (copied > MAX_INPUT_BYTES) fail(ImageProblem.TOO_LARGE)
                        destination.write(buffer, 0, count)
                    }
                }
            } ?: fail(ImageProblem.UNREADABLE)

            coroutineContext.ensureActive()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.absolutePath, bounds)
            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0) fail(ImageProblem.UNREADABLE)
            if (bounds.outMimeType !in SUPPORTED_MIME_TYPES) fail(ImageProblem.UNSUPPORTED)
            if (width.toLong() * height > MAX_SOURCE_PIXELS || max(width, height) > MAX_SOURCE_EDGE) {
                fail(ImageProblem.TOO_LARGE)
            }
            if (min(width, height) < MIN_SOURCE_EDGE) fail(ImageProblem.TOO_SMALL)

            var sample = 1
            while (
                width.toLong() * height / sample / sample > MAX_DECODE_PIXELS ||
                max(width, height) / sample > MAX_LONG_EDGE * 2
            ) sample *= 2

            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            decoded = BitmapFactory.decodeFile(source.absolutePath, options)
                ?: fail(ImageProblem.UNREADABLE)
            coroutineContext.ensureActive()

            // BitmapFactory leaves EXIF orientation unapplied. Apply it exactly once,
            // including mirrored orientations, in the same draw as the final resize.
            val exif = ExifInterface(source)
            val transform = Matrix().apply {
                if (exif.isFlipped) setScale(-1f, 1f)
                postRotate(exif.rotationDegrees.toFloat())
            }
            val orientedBounds = RectF(0f, 0f, decoded.width.toFloat(), decoded.height.toFloat())
            transform.mapRect(orientedBounds)
            transform.postTranslate(-orientedBounds.left, -orientedBounds.top)
            val scale = min(1f, MAX_LONG_EDGE / max(orientedBounds.width(), orientedBounds.height()))
            val finalWidth = max(1, (orientedBounds.width() * scale).roundToInt())
            val finalHeight = max(1, (orientedBounds.height() * scale).roundToInt())
            transform.postScale(finalWidth / orientedBounds.width(), finalHeight / orientedBounds.height())
            normalized = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888)
            Canvas(normalized).apply {
                drawColor(Color.WHITE)
                drawBitmap(decoded, transform, Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
            }
            decoded.recycle()
            decoded = null
            coroutineContext.ensureActive()

            output = temporaryFile("prepared-", ".jpg")
            output.outputStream().buffered().use { stream ->
                if (!normalized.compress(Bitmap.CompressFormat.JPEG, 94, stream)) {
                    fail(ImageProblem.UNREADABLE)
                }
            }
            // Encoding a fresh bitmap also drops location/other source EXIF metadata.
            coroutineContext.ensureActive()
            val result = PreparedPhoto(output, normalized)
            transferred = true
            return result
        } catch (error: OutOfMemoryError) {
            throw ImagePreparationException(ImageProblem.TOO_LARGE)
        } catch (error: SecurityException) {
            throw ImagePreparationException(ImageProblem.UNREADABLE)
        } catch (error: IOException) {
            if (error is ImagePreparationException) throw error
            throw ImagePreparationException(if (directory.usableSpace < 1024 * 1024) ImageProblem.STORAGE else ImageProblem.UNREADABLE)
        } finally {
            source.delete()
            decoded?.takeUnless { it.isRecycled }?.recycle()
            if (!transferred) {
                normalized?.takeUnless { it.isRecycled }?.recycle()
                output?.delete()
            }
        }
    }

    private fun fail(problem: ImageProblem): Nothing = throw ImagePreparationException(problem)

    companion object {
        const val MAX_LONG_EDGE = 2600
        const val MAX_INPUT_BYTES = 30L * 1024 * 1024
        const val MAX_SOURCE_PIXELS = 60_000_000L
        private const val MAX_SOURCE_EDGE = 20_000
        private const val MAX_DECODE_PIXELS = 16_000_000L
        private const val MIN_SOURCE_EDGE = 250
        private val SUPPORTED_MIME_TYPES = setOf(
            "image/jpeg", "image/png", "image/webp", "image/heif", "image/heic",
        )
    }
}
