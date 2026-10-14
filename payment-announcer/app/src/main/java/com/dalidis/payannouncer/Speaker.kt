package com.dalidis.payannouncer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Wraps [TextToSpeech]: queues text until the engine is ready, and ducks other audio
 * (music, videos) while speaking.
 */
class Speaker(context: Context) : TextToSpeech.OnInitListener {

    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(audioAttributes)
        .build()

    private val pending = mutableListOf<String>()
    private val inFlight = AtomicInteger(0)
    private var ready = false
    private var nextId = 0

    // Declared last so every field above is initialized before onInit can run.
    private val tts = TextToSpeech(context.applicationContext, this)

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.e(TAG, "TextToSpeech init failed: $status")
            return
        }
        val result = tts.setLanguage(Locale.getDefault())
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.US)
        }
        tts.setAudioAttributes(audioAttributes)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = finished()
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finished()
            override fun onError(utteranceId: String?, errorCode: Int) = finished()
        })
        ready = true
        pending.forEach(::speakNow)
        pending.clear()
    }

    /** Must be called on the main thread (TTS init callback and service callbacks run there). */
    fun speak(text: String) {
        if (ready) speakNow(text) else pending += text
    }

    private fun speakNow(text: String) {
        if (inFlight.getAndIncrement() == 0) {
            audioManager.requestAudioFocus(focusRequest)
        }
        // QUEUE_ADD so back-to-back payments are all announced in order.
        val result = tts.speak(text, TextToSpeech.QUEUE_ADD, null, "pay-${nextId++}")
        if (result != TextToSpeech.SUCCESS) finished()
    }

    private fun finished() {
        if (inFlight.decrementAndGet() <= 0) {
            inFlight.set(0)
            audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    private companion object {
        const val TAG = "Speaker"
    }
}
