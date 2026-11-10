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
    enum class State { Unavailable, Idle, Speaking, Paused }
    private val _state = MutableStateFlow(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()
    private var engine: TextToSpeech? = null
    private val queue = ArrayDeque<String>()
    private var sequence = 0L

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                engine = null
                _state.value = State.Unavailable
            } else {
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) { speakNext() }
                    @Deprecated("Deprecated Android TTS callback")
                    override fun onError(utteranceId: String?) {
                        queue.clear()
                        _state.value = State.Idle
                    }
                })
            }
        }
    }

    fun speak(paragraphs: List<String>, language: Locale = Locale.getDefault(), rate: Float = 1f) {
        val tts = engine ?: return
        queue.clear()
        paragraphs.flatMap(::segment).filter { it.isNotBlank() }.forEach(queue::add)
        if (queue.isEmpty()) return
        tts.language = language
        tts.setSpeechRate(rate.coerceIn(0.5f, 2f))
        _state.value = State.Speaking
        speakNext()
    }

    fun pause() {
        if (_state.value == State.Speaking) {
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
        queue.clear()
        engine?.stop()
        _state.value = State.Idle
    }

    fun release() {
        stop()
        engine?.shutdown()
        engine = null
    }

    private fun speakNext() {
        val next = queue.pollFirst()
        if (next == null) {
            _state.value = State.Idle
            return
        }
        val id = "kitsugi-novel-${++sequence}"
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) }
        engine?.speak(next, TextToSpeech.QUEUE_FLUSH, params, id)
    }

    private fun segment(text: String): List<String> {
        val result = mutableListOf<String>()
        var remaining = text.trim()
        while (remaining.length > 3000) {
            val split = remaining.substring(0, 3001).lastIndexOfAny(charArrayOf('.', '!', '?', '\n'))
                .takeIf { it > 1000 } ?: remaining.lastIndexOf(' ', 3000).takeIf { it > 1000 } ?: 3000
            result += remaining.substring(0, split + 1).trim()
            remaining = remaining.substring(split + 1).trim()
        }
        if (remaining.isNotEmpty()) result += remaining
        return result
    }
}
