package com.kirti.jarvis.companion

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationListener : NotificationListenerService() {
    companion object {
        const val TAG = "JARVIS-Notification"
    }

    override fun onListenerConnected() {
        Log.i(TAG, "NotificationListener connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Placeholder: do not read private message contents here without approval.
        Log.i(TAG, "Notification posted: package=${sbn?.packageName}, id=${sbn?.id}")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        Log.i(TAG, "Notification removed: package=${sbn?.packageName}, id=${sbn?.id}")
    }
}
