package com.example.gabayai.imaging

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** Requires an Android device/emulator; uses the actual bundled OCR, never a stub. */
@RunWith(AndroidJUnit4::class)
class OfflineImagingTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository = ImageRepository(context)

    @Test
    fun bundledLatinModelReadsRealBitmapAndReturnsAlignedBoxes() = runBlocking {
        val bitmap = Bitmap.createBitmap(1600, 900, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            drawText("Date of Birth", 100f, 220f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 90f
            })
        }
        val recognizer = OfflineOcr()
        try {
            val lines = recognizer.recognize(bitmap)
            val date = lines.firstOrNull { it.text.contains("Date of Birth", ignoreCase = true) }
            assertTrue("Bundled ML Kit did not read the actual printed label", date != null)
            val box = requireNotNull(date).box
            assertTrue(box.left in 0 until bitmap.width)
            assertTrue(box.right > box.left && box.right <= bitmap.width)
            assertTrue(box.top in 0 until bitmap.height)
            assertTrue(box.bottom > box.top && box.bottom <= bitmap.height)
            assertTrue(box.top < 220 && box.bottom >= 200)
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }

    @Test
    fun exifRotationIsAppliedOnceAndNormalizedFileDropsLocation() = runBlocking {
        val source = repository.createCaptureFile()
        val bitmap = Bitmap.createBitmap(800, 300, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.BLUE)
            drawRect(0f, 0f, 400f, 300f, Paint().apply { color = Color.RED })
        }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 98, it) }
        bitmap.recycle()
        ExifInterface(source).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            setLatLong(14.5995, 120.9842)
            saveAttributes()
        }
        var photo: PreparedPhoto? = null
        try {
            photo = repository.prepare(Uri.fromFile(source))
            assertEquals(300, photo.width)
            assertEquals(800, photo.height)
            assertTrue(Color.red(photo.bitmap.getPixel(150, 100)) > 220)
            assertTrue(Color.blue(photo.bitmap.getPixel(150, 700)) > 220)
            val outputExif = ExifInterface(photo.file)
            assertEquals(0, outputExif.rotationDegrees)
            assertTrue(outputExif.latLong == null)
        } finally {
            repository.clear(photo)
            repository.delete(source)
        }
    }

    @Test
    fun largePhotoIsBoundedAndPrivateTemporaryFileIsDeleted() = runBlocking {
        val source = repository.createCaptureFile()
        val bitmap = Bitmap.createBitmap(4000, 1000, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        val photo = repository.prepare(Uri.fromFile(source))
        val output = photo.file
        try {
            assertEquals(2600, photo.width)
            assertEquals(650, photo.height)
            assertTrue(output.canonicalPath.startsWith(context.cacheDir.canonicalPath + File.separator))
            assertTrue(output.exists())
        } finally {
            repository.clear(photo)
            repository.delete(source)
        }
        assertFalse(output.exists())
        assertTrue(photo.bitmap.isRecycled)
    }

    @Test
    fun invalidAndOversizedFilesFailWithoutRetainingAnIntermediateCopy() = runBlocking {
        val source = repository.createCaptureFile()
        try {
            source.writeText("This is not an image")
            assertProblem(source, ImageProblem.UNREADABLE)
            RandomAccessFile(source, "rw").use { it.setLength(ImageRepository.MAX_INPUT_BYTES + 1) }
            assertProblem(source, ImageProblem.TOO_LARGE)
            assertTrue(File(context.cacheDir, "gabay-photos").listFiles().orEmpty().none {
                it.name.startsWith("source-")
            })
        } finally {
            repository.delete(source)
        }
    }

    private suspend fun assertProblem(file: File, expected: ImageProblem) {
        try {
            repository.prepare(Uri.fromFile(file)).close()
            fail("An invalid picture was accepted")
        } catch (error: ImagePreparationException) {
            assertEquals(expected, error.problem)
        }
    }
}
