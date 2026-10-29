package com.kirti.jarvis.companion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.d(TAG, "Boot/receive completed, restoring wake word service")
            WakeWordService.startService(context)
        }
    }

    companion object {
        const val TAG = "BootReceiver"
    }
}
