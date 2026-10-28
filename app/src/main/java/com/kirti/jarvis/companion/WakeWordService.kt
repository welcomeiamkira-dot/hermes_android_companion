package com.kirti.jarvis.companion

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.*

class WakeWordService : Service() {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wake Word Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Listening for 'Hey JARVIS'"
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS Listening")
            .setContentText("Say 'Hey JARVIS' to activate")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    fun startListening() {
        if (isListening) return
        isListening = true

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "Wake word ready")
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    if (isListening) startWakeWordLoop()
                }

                override fun onError(error: Int) {
                    Log.e(TAG, "Wake word error: $error")
                    if (isListening) startWakeWordLoop()
                }

                override fun onResults(results: Bundle?) {
                    handleText(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
                    if (isListening) startWakeWordLoop()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    handleText(partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            startWakeWordLoop()
        }
    }

    private fun handleText(matches: ArrayList<String>?) {
        val text = matches?.firstOrNull() ?: return
        if (text.contains("jarvis", ignoreCase = true) ||
            text.contains("hey", ignoreCase = true)) {
            Log.d(TAG, "Wake word detected: $text")
            val intent = Intent(MainActivity.ACTION_WAKE_WORD_DETECTED)
            sendBroadcast(intent)
        }
    }

    private fun startWakeWordLoop() {
        if (!isListening) return
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Wake word loop error", e)
        }
    }

    fun stopListening() {
        isListening = false
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
    }

    companion object {
        const val TAG = "WakeWordService"
        const val CHANNEL_ID = "jarvis_wake_word"
        const val NOTIFICATION_ID = 1001

        fun startService(context: Context) {
            val intent = Intent(context, WakeWordService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, WakeWordService::class.java)
            context.stopService(intent)
        }
    }
}
