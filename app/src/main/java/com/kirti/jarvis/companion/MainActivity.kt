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
    companion object {
        private const val TAG = "MainActivity"
        const val ACTION_WAKE_WORD_DETECTED = "com.kirti.jarvis.WAKE_WORD_DETECTED"
        const val REQUEST_MEDIA_PROJECTION = 101
    }
    private lateinit var webView: WebView
    private lateinit var errorView: LinearLayout
    private lateinit var retryButton: Button
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isListening = false
    private var wakeWordActive = false
    private lateinit var visionService: VisionService
    private var mediaProjectionManager: android.media.projection.MediaProjectionManager? = null
    private val wakeWordReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_WAKE_WORD_DETECTED) {
                Toast.makeText(this@MainActivity, "JARVIS activated", Toast.LENGTH_SHORT).show()
                messageInput.requestFocus()
            }
        }
    }

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
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_main)

            webView = findViewById(R.id.webView)
            errorView = findViewById(R.id.errorView)
            retryButton = findViewById(R.id.retryButton)
            messageInput = findViewById(R.id.messageInput)
            sendButton = findViewById(R.id.sendButton)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.displayZoomControls = true
        webView.settings.builtInZoomControls = true
        webView.settings.setSupportZoom(true)
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true

        @Suppress("DEPRECATION")
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        supportActionBar?.hide()

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                webView.visibility = View.VISIBLE
                errorView.visibility = View.GONE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Page loaded: $url")
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                Log.e(TAG, "WebView error: ${error?.description}")
                if (request?.isForMainFrame == true) {
                    showError("Cannot connect to Hermes server.\nMake sure dashboard is running on port 9119.")
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                Log.e(TAG, "HTTP error: ${errorResponse?.statusCode}")
            }
        }

        webView.webChromeClient = WebChromeClient()

        retryButton.setOnClickListener {
            loadDashboard()
        }

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
        setupTextInput()

        // Request permissions
        requestPermissions()

        // Init screen capture
        visionService = VisionService()
        mediaProjectionManager = getSystemService(android.media.projection.MediaProjectionManager::class.java)

        // Listen for wake word broadcasts
        registerReceiver(wakeWordReceiver, android.content.IntentFilter(ACTION_WAKE_WORD_DETECTED))

        // Load dashboard
        loadDashboard()
    } catch (e: Exception) {
        Log.e(TAG, "Fatal error in onCreate", e)
        Toast.makeText(this, "App startup error: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

    // Class methods continue below
    private fun requestPermissions() {
        val missing = PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun loadDashboard() {
        try {
            webView.visibility = View.VISIBLE
            errorView.visibility = View.GONE
            webView.loadUrl("http://127.0.0.1:9119")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading URL", e)
            showError("Failed to load dashboard: ${e.message}")
        }
    }

    private fun showError(message: String) {
        webView.visibility = View.GONE
        errorView.visibility = View.VISIBLE
        val errorText = findViewById<TextView>(R.id.errorText)
        errorText.text = message
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

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        Log.d(TAG, "Recognized: $text")
                        sendMessageToHermes(text)
                        speakResponse("You said: $text")
                    }
                    stopVoiceInput()
                }

                override fun onPartialResults(partialResults: Bundle?) {}

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
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                val intent = mediaProjectionManager?.createScreenCaptureIntent()
                if (intent != null) {
                    startActivityForResult(intent, REQUEST_MEDIA_PROJECTION)
                } else {
                    Toast.makeText(this, "Screen capture not supported", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Screen capture requires Android 5.0+", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MEDIA_PROJECTION && resultCode == RESULT_OK && data != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                val projection = mediaProjectionManager?.getMediaProjection(resultCode, data)
                if (projection != null) {
                    visionService.mediaProjection = projection
                    visionService.startCapture(resultCode, data)
                    Toast.makeText(this, "Screen control active", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupTextInput() {
        sendButton.setOnClickListener {
            val text = messageInput.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessageToHermes(text)
                messageInput.text.clear()
            }
        }

        messageInput.setOnEditorActionListener { _, actionId, _ ->
            val text = messageInput.text.toString().trim()
            if (text.isNotEmpty() && actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendMessageToHermes(text)
                messageInput.text.clear()
                true
            } else {
                false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVoiceInput()
        textToSpeech?.shutdown()
        if (wakeWordActive) {
            WakeWordService.stopService(this)
        }
        unregisterReceiver(wakeWordReceiver)
    }
}
