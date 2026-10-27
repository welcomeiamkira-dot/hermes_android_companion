package com.kirti.jarvis.companion

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Surface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class VisionService : Service() {
    private var imageReader: ImageReader? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private var mediaProjection: android.media.projection.MediaProjection? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
    }

    fun startCapture(resultCode: Int, data: Intent) {
        val manager = getSystemService(DisplayManager::class.java)
        val display = manager.getDisplay(Display.DEFAULT_DISPLAY)
        val metrics = resources.displayMetrics
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

                // Save screenshot
                saveScreenshot(bitmap)

                // Send to Hermes for OCR processing
                sendToHermes(bitmap)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Capture error", e)
        } finally {
            image?.close()
        }
    }

    private fun saveScreenshot(bitmap: Bitmap) {
        val file = File(getExternalFilesDir(null), "screenshot_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        Log.d(TAG, "Screenshot saved: ${file.absolutePath}")
    }

    private fun sendToHermes(bitmap: Bitmap) {
        // Convert bitmap to base64 and send to Hermes via WebSocket/HTTP
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 80, stream)
        val bytes = stream.toByteArray()
        // TODO: Implement sending to Hermes gateway
        Log.d(TAG, "Screenshot to Hermes: ${bytes.size} bytes")
    }

    fun stopCapture() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopCapture()
    }

    companion object {
        const val TAG = "VisionService"
    }
}
