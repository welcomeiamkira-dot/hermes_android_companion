package com.kirti.jarvis.companion

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.webkit.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isListening = false
    private var wakeWordActive = false

    private val PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.POST_NOTIFICATIONS
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            Toast.makeText(this, "Permissions required for voice features", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.supportZoom = true
        webView.settings.displayZoomControls = false
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
        webView.loadUrl("http://127.0.0.1:9119")

        // Initialize TTS
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.US
            }
        }

        // Setup controls
        setupVoiceButton()
        setupWakeWordToggle()
        setupScreenshotButton()

        // Request permissions
        requestPermissions()
    }

    private fun requestPermissions() {
        val missing = PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun setupVoiceButton() {
        val voiceButton = findViewById<FloatingActionButton>(R.id.voiceButton)
        voiceButton.setOnClickListener {
            if (isListening) {
                stopVoiceInput()
            } else {
                startVoiceInput()
            }
        }
    }

    private fun startVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions()
            return
        }

        isListening = true
        val voiceButton = findViewById<FloatingActionButton>(R.id.voiceButton)
        voiceButton.setImageResource(android.R.drawable.ic_media_pause)

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "Ready for speech")
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "Speech started")
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    stopVoiceInput()
                }

                override fun onError(error: Int) {
                    Log.e(TAG, "Speech error: $error")
                    stopVoiceInput()
                }

                override fun onResults(results: Bundle) {
                    val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        Log.d(TAG, "Recognized: $text")
                        sendMessageToHermes(text)
                        speakResponse("You said: $text")
                    }
                    stopVoiceInput()
                }

                override fun onPartialResults(partialResults: Bundle) {}

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Listening...")
            }
            startListening(intent)
        }
    }

    private fun stopVoiceInput() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
        val voiceButton = findViewById<FloatingActionButton>(R.id.voiceButton)
        voiceButton.setImageResource(android.R.drawable.ic_btn_speak_now)
    }

    private fun sendMessageToHermes(text: String) {
        // Inject JavaScript to send message to Hermes chat input
        val js = """
            (function() {
                // Try to find the chat input and send the message
                const input = document.querySelector('input[type="text"], textarea, [contenteditable="true"]');
                if (input) {
                    input.value = '$text';
                    input.dispatchEvent(new Event('input', { bubbles: true }));
                    input.dispatchEvent(new Event('change', { bubbles: true }));
                }
                
                // Also try to trigger Enter key
                setTimeout(() => {
                    const event = new KeyboardEvent('keydown', {
                        key: 'Enter',
                        code: 'Enter',
                        keyCode: 13,
                        bubbles: true
                    });
                    document.dispatchEvent(event);
                }, 100);
            })();
        """.trimIndent()

        webView.evaluateJavascript(js, null)
    }

    private fun speakResponse(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun setupWakeWordToggle() {
        val toggle = findViewById<ToggleButton>(R.id.wakeWordToggle)
        toggle.setOnCheckedChangeListener { _, isChecked ->
            wakeWordActive = isChecked
            if (isChecked) {
                WakeWordService.startService(this)
                Toast.makeText(this, "Wake word activated: Say 'Hey JARVIS'", Toast.LENGTH_SHORT).show()
            } else {
                WakeWordService.stopService(this)
                Toast.makeText(this, "Wake word deactivated", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupScreenshotButton() {
        val screenshotButton = findViewById<FloatingActionButton>(R.id.screenshotButton)
        screenshotButton.setOnClickListener {
            Toast.makeText(this, "Screenshot feature coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVoiceInput()
        textToSpeech?.shutdown()
        if (wakeWordActive) {
            WakeWordService.stopService(this)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        const val ACTION_WAKE_WORD_DETECTED = "com.kirti.jarvis.WAKE_WORD_DETECTED"
    }
}
