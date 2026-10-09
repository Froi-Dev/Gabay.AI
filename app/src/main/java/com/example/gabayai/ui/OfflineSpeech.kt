package com.example.gabayai.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.gabayai.domain.GuideLanguage

/** Uses only an already installed, non-network Android voice. Document text stays in memory. */
class OfflineSpeech(context: Context) : AutoCloseable {
    var ready by mutableStateOf(false)
        private set
    var speaking by mutableStateOf(false)
        private set
    var unavailable by mutableStateOf(false)
        private set
    private val main = Handler(Looper.getMainLooper())
    private var closed = false
    private var generation = 0
    private var engine: TextToSpeech? = null

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            main.post {
                if (!closed) {
                    ready = status == TextToSpeech.SUCCESS
                    unavailable = !ready
                    engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = update(utteranceId, true, false)
                        override fun onDone(utteranceId: String?) = update(utteranceId, false, false)
                        @Deprecated("Android callback")
                        override fun onError(utteranceId: String?) = update(utteranceId, false, true)
                        override fun onError(utteranceId: String?, errorCode: Int) = update(utteranceId, false, true)
                    })
                }
            }
        }
    }

    private fun update(id: String?, active: Boolean, failed: Boolean) {
        main.post {
            if (!closed && id == generation.toString()) {
                speaking = active
                unavailable = failed
            }
        }
    }

    fun speak(text: String, language: GuideLanguage) {
        stop()
        val tts = engine ?: return
        if (!ready) { unavailable = true; return }
        val codes = when (language) {
            GuideLanguage.ENGLISH -> setOf("en")
            GuideLanguage.FILIPINO -> setOf("fil", "tl")
            else -> emptySet()
        }
        val voice = tts.voices.orEmpty().filter {
            it.locale.language in codes && !it.isNetworkConnectionRequired && "notInstalled" !in it.features.orEmpty()
        }.maxByOrNull { it.quality }
        if (voice == null || tts.setVoice(voice) == TextToSpeech.ERROR) { unavailable = true; return }
        tts.setSpeechRate(.85f)
        unavailable = false
        speaking = true
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, generation.toString()) == TextToSpeech.ERROR) {
            speaking = false
            unavailable = true
        }
    }

    fun stop() {
        generation++
        engine?.stop()
        speaking = false
        unavailable = false
    }

    override fun close() {
        closed = true
        generation++
        engine?.stop()
        engine?.shutdown()
        engine = null
        speaking = false
    }
}
