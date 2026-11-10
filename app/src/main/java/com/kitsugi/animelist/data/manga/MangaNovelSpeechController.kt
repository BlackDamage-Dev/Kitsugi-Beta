package com.kitsugi.animelist.data.manga

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque
import java.util.Locale

/** Lifecycle-owned on-device speech. Long chapters are segmented to avoid engine utterance limits. */
class MangaNovelSpeechController(context: Context) {
    enum class State { Unavailable, Initializing, Idle, Speaking, Paused }
    private data class SpeechRequest(val paragraphs: List<String>, val language: Locale, val rate: Float)

    private val _state = MutableStateFlow(State.Initializing)
    val state: StateFlow<State> = _state.asStateFlow()
    private var engine: TextToSpeech? = null
    private var ready = false
    private val queue = ArrayDeque<String>()
    private var pendingRequest: SpeechRequest? = null
    private var currentText: String? = null
    private var activeUtteranceId: String? = null
    private var sequence = 0L

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (!ready) {
                engine = null
                pendingRequest = null
                _state.value = State.Unavailable
            } else {
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId != activeUtteranceId) return
                        activeUtteranceId = null
                        currentText = null
                        speakNext()
                    }
                    @Deprecated("Deprecated Android TTS callback")
                    override fun onError(utteranceId: String?) = handleError(utteranceId)
                    override fun onError(utteranceId: String?, errorCode: Int) = handleError(utteranceId)
                    private fun handleError(utteranceId: String?) {
                        if (utteranceId != activeUtteranceId) return
                        activeUtteranceId = null
                        currentText = null
                        queue.clear()
                        _state.value = State.Idle
                    }
                })
                _state.value = State.Idle
                pendingRequest?.also { pendingRequest = null }?.let { speak(it.paragraphs, it.language, it.rate) }
            }
        }
    }

    fun speak(paragraphs: List<String>, language: Locale = Locale.getDefault(), rate: Float = 1f) {
        val tts = engine ?: return
        if (!ready) {
            pendingRequest = SpeechRequest(paragraphs, language, rate)
            return
        }
        tts.stop()
        activeUtteranceId = null
        currentText = null
        queue.clear()
        paragraphs.flatMap(MangaSpeechSegmenter::segment).filter { it.isNotBlank() }.forEach(queue::add)
        if (queue.isEmpty()) {
            _state.value = State.Idle
            return
        }
        val languageStatus = tts.setLanguage(language)
        if (languageStatus == TextToSpeech.LANG_MISSING_DATA || languageStatus == TextToSpeech.LANG_NOT_SUPPORTED) {
            _state.value = State.Unavailable
            return
        }
        tts.setSpeechRate(rate.coerceIn(0.5f, 2f))
        _state.value = State.Speaking
        speakNext()
    }

    fun pause() {
        if (_state.value == State.Speaking) {
            currentText?.let(queue::addFirst)
            currentText = null
            activeUtteranceId = null
            engine?.stop()
            _state.value = State.Paused
        }
    }

    fun resume() {
        if (_state.value == State.Paused) {
            _state.value = State.Speaking
            speakNext()
        }
    }

    fun stop() {
        pendingRequest = null
        queue.clear()
        currentText = null
        activeUtteranceId = null
        engine?.stop()
        if (ready) _state.value = State.Idle
    }

    fun release() {
        stop()
        engine?.shutdown()
        engine = null
    }

    private fun speakNext() {
        val next = queue.pollFirst()
        if (next == null) {
            currentText = null
            activeUtteranceId = null
            _state.value = State.Idle
            return
        }
        currentText = next
        val id = "kitsugi-novel-${++sequence}"
        activeUtteranceId = id
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) }
        engine?.speak(next, TextToSpeech.QUEUE_FLUSH, params, id)
    }


}
