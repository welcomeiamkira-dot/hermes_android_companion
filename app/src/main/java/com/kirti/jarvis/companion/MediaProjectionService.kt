package com.kirti.jarvis.companion

import android.app.Service
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.Log

class MediaProjectionService : Service() {
    companion object {
        const val TAG = "JARVIS-MediaProjection"
    }

    private var projection: MediaProjection? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "MediaProjectionService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Placeholder: start projection only after explicit user approval.
        Log.i(TAG, "onStartCommand: awaiting user-triggered capture")
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        projection?.stop()
        projection = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
