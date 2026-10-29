package com.kirti.jarvis.companion

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Surface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class VisionService(private val context: Context) {
    private var imageReader: ImageReader? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    var mediaProjection: MediaProjection? = null
    private val handler = Handler(Looper.getMainLooper())

    fun startCapture(resultCode: Int, data: Intent) {
        mediaProjection = context.getSystemService(android.media.projection.MediaProjectionManager::class.java)
            ?.getMediaProjection(resultCode, data)
        if (mediaProjection == null) {
            Log.e(TAG, "Failed to get MediaProjection")
            return
        }

        val manager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = manager.getDisplay(Display.DEFAULT_DISPLAY)
        val metrics = context.resources.displayMetrics
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "JARVIS Screen Capture",
            metrics.widthPixels,
            metrics.heightPixels,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            handler
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            captureScreen(reader)
        }, handler)
    }

    private fun captureScreen(reader: ImageReader) {
        var image: Image? = null
        try {
            image = reader.acquireLatestImage()
            if (image != null) {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * image.width

                val bitmap = Bitmap.createBitmap(
                    image.width + rowPadding / pixelStride,
                    image.height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)

                saveScreenshot(bitmap)
                sendToHermes(bitmap)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Capture error", e)
        } finally {
            image?.close()
        }
    }

    private fun saveScreenshot(bitmap: Bitmap) {
        val file = File(context.getExternalFilesDir(null), "screenshot_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        Log.d(TAG, "Screenshot saved: ${file.absolutePath}")
    }

    private fun sendToHermes(bitmap: Bitmap) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 80, stream)
        val bytes = stream.toByteArray()
        Log.d(TAG, "Screenshot to Hermes: ${bytes.size} bytes")
    }

    fun stopCapture() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
    }

    companion object {
        const val TAG = "VisionService"
    }
}
