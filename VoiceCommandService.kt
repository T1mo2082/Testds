package com.example.voicecommander

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

class VoiceCommandService : Service(), RecognitionListener {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var fab: FloatingActionButton
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var recognizerIntent: Intent

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var isListening = false

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "VoiceCommanderChannel"
        const val TAG = "VoiceCommandService"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        setupNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        setupFloatingView()
        setupSpeechRecognizer()
    }

    private fun setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use your app's icon
            .build()
    }

    private fun setupFloatingView() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null)
        fab = floatingView.findViewById(R.id.floating_button)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager.addView(floatingView, params)

        setupFabListeners(params)
    }
    
    // Add a simple layout file for the floating widget
    // **File: `app/src/main/res/layout/layout_floating_widget.xml`**
    /*
    <?xml version="1.0" encoding="utf-8"?>
    <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
        xmlns:app="http://schemas.android.com/apk/res-auto"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content">
    
        <com.google.android.material.floatingactionbutton.FloatingActionButton
            android:id="@+id/floating_button"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:src="@android:drawable/ic_btn_speak_now"
            app:tint="@android:color/white" />
    
    </FrameLayout>
    */


    private fun setupFabListeners(params: WindowManager.LayoutParams) {
        fab.setOnClickListener {
            toggleListening()
        }

        // Make the button draggable
        fab.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }
                }
                return false
            }
        })
    }
    
    private fun toggleListening() {
        if (!isListening) {
            startListening()
        } else {
            stopListening()
        }
    }

    private fun startListening() {
        isListening = true
        fab.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_green_dark)
        speechRecognizer.startListening(recognizerIntent)
    }

    private fun stopListening() {
        isListening = false
        fab.backgroundTintList = ContextCompat.getColorStateList(this, com.google.android.material.R.color.design_default_color_primary)
        speechRecognizer.stopListening()
    }

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "Speech recognition not available")
            stopSelf()
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(this)

        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        windowManager.removeView(floatingView)
        serviceScope.coroutineContext.cancelChildren()
    }
    
    // --- RecognitionListener Callbacks ---

    override fun onReadyForSpeech(params: Bundle?) {
        Log.d(TAG, "Ready for speech")
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (matches != null && matches.isNotEmpty()) {
            val command = matches[0].lowercase(Locale.getDefault())
            Log.d(TAG, "Recognized command: $command")
            
            when {
                "scroll down" in command -> serviceScope.launch { GestureDispatch.send(GestureType.SCROLL_DOWN) }
                "like post" in command -> serviceScope.launch { GestureDispatch.send(GestureType.LIKE_POST) }
            }
        }
        // Restart listening to create a continuous loop
        if (isListening) {
            speechRecognizer.startListening(recognizerIntent)
        }
    }

    override fun onError(error: Int) {
        val errorMessage = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
            SpeechRecognizer.ERROR_SERVER -> "Error from server"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Unknown speech recognizer error"
        }
        Log.e(TAG, "onError: $errorMessage")
        
        // If it's a common, non-fatal error, just restart
        if (isListening && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
            speechRecognizer.startListening(recognizerIntent)
        } else {
            stopListening() // Stop on critical errors
        }
    }
    
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}