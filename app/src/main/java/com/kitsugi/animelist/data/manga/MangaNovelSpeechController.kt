package com.kitsugi.animelist.data.manga

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * 🎙️ Novel ve metin okuma için yaşam döngüsüne duyarlı cihaz içi TTS denetleyicisi.
 *
 * Özellikler:
 *  - Foreground Service + kalıcı bildirim + MediaSession desteği (kilit ekranı/kulaklık)
 *  - Cümle düzeyinde segmentasyon ve görsel cümle vurgulama (SpeechHighlightState)
 *  - Session ID bazlı callback izolasyonu (eski takılmış callback'ler yeni istekleri bozamaz)
 *  - Ses ve konuşma hızı seçimi
 *  - Bölüm ve yaşam döngüsü temizliği
 */
class MangaNovelSpeechController(private val context: Context) : MangaSpeechService.Listener {

    enum class State { Unavailable, Initializing, Idle, Speaking, Paused }

    data class SpeechHighlightState(
        val paragraphIndex: Int,
        val sentenceIndex: Int,
        val sentenceText: String,
    )

    private data class SpeechRequest(
        val paragraphs: List<String>,
        val language: Locale,
        val rate: Float,
        val title: String,
        val chapter: String,
        val startIndex: Int = 0,
    )

    private val _state = MutableStateFlow(State.Initializing)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _highlightState = MutableStateFlow<SpeechHighlightState?>(null)
    val highlightState: StateFlow<SpeechHighlightState?> = _highlightState.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<Voice>>(emptyList())
    val availableVoices: StateFlow<List<Voice>> = _availableVoices.asStateFlow()

    private val _selectedVoice = MutableStateFlow<String?>(null)
    val selectedVoice: StateFlow<String?> = _selectedVoice.asStateFlow()

    private var engine: TextToSpeech? = null
    private var ready = false

    private val segments = mutableListOf<MangaSpeechSegmenter.SpeechSegment>()
    private var currentSegmentIndex = 0

    private var pendingRequest: SpeechRequest? = null
    private var currentLanguage = Locale.getDefault()
    private var currentRate = 1f
    private var currentTitle = ""
    private var currentChapter = ""
    private var selectedVoiceName: String? = null

    /** Oturum sayacı: eski isteklerin callback'leri (onDone/onError) bu sayaçla elenir. */
    private var currentSessionId = 0L

    var onNextChapterRequested: (() -> Unit)? = null
    var onPreviousChapterRequested: (() -> Unit)? = null

    init {
        MangaSpeechService.listener = this
        initEngine()
    }

    private fun initEngine() {
        engine = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (!ready) {
                engine = null
                pendingRequest = null
                _state.value = State.Unavailable
            } else {
                updateAvailableVoices()
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        // Utterance başladı
                    }

                    override fun onDone(utteranceId: String?) {
                        if (!isUtteranceValid(utteranceId)) return
                        speakNext()
                    }

                    @Deprecated("Deprecated Android TTS callback")
                    override fun onError(utteranceId: String?) = handleError(utteranceId)

                    override fun onError(utteranceId: String?, errorCode: Int) = handleError(utteranceId)

                    private fun handleError(utteranceId: String?) {
                        if (!isUtteranceValid(utteranceId)) return
                        // Bir cümlede hata olursa sonrakine geçmeyi dene
                        speakNext()
                    }
                })
                _state.value = State.Idle
                pendingRequest?.also { pendingRequest = null }?.let {
                    speak(it.paragraphs, it.language, it.rate, it.title, it.chapter, it.startIndex)
                }
            }
        }
    }

    private fun isUtteranceValid(utteranceId: String?): Boolean {
        if (utteranceId == null) return false
        val prefix = "tts_${currentSessionId}_"
        return utteranceId.startsWith(prefix)
    }

    private fun updateAvailableVoices() {
        runCatching {
            val voices = engine?.voices?.filter {
                it.locale.language.equals(currentLanguage.language, ignoreCase = true)
            }?.sortedBy { it.name }.orEmpty()
            _availableVoices.value = voices
        }
    }

    fun setVoiceByName(voiceName: String?) {
        _selectedVoice.value = voiceName
        selectedVoiceName = voiceName
        if (voiceName.isNullOrBlank()) return
        val voice = engine?.voices?.firstOrNull { it.name == voiceName } ?: return
        runCatching { engine?.voice = voice }
    }

    fun setRate(rate: Float) {
        currentRate = rate
        runCatching { engine?.setSpeechRate(rate.coerceIn(0.5f, 2f)) }
    }

    fun speak(
        paragraphs: List<String>,
        language: Locale = Locale.getDefault(),
        rate: Float = 1f,
        title: String = "",
        chapter: String = "",
        startIndex: Int = 0,
    ) {
        currentTitle = title
        currentChapter = chapter
        currentLanguage = language
        currentRate = rate

        if (!ready) {
            pendingRequest = SpeechRequest(paragraphs, language, rate, title, chapter, startIndex)
            return
        }

        val tts = engine ?: return

        // Yeni oturum: önceki takılmış utterance callback'leri geçersiz kılınır
        val sessionId = ++currentSessionId
        tts.stop()

        segments.clear()
        _highlightState.value = null

        val parsed = MangaSpeechSegmenter.segmentParagraphs(paragraphs)
            .filter { !it.text.startsWith("[img:") }
        segments.addAll(parsed)

        if (segments.isEmpty()) {
            _state.value = State.Idle
            MangaSpeechService.stop(context)
            return
        }
        currentSegmentIndex = startIndex.coerceIn(0, segments.lastIndex)

        val langStatus = tts.setLanguage(language)
        if (langStatus == TextToSpeech.LANG_MISSING_DATA || langStatus == TextToSpeech.LANG_NOT_SUPPORTED) {
            _state.value = State.Unavailable
            MangaSpeechService.stop(context)
            return
        }

        updateAvailableVoices()
        selectedVoiceName?.let { name ->
            tts.voices?.firstOrNull { it.name == name }?.let { tts.voice = it }
        }

        tts.setSpeechRate(rate.coerceIn(0.5f, 2f))
        _state.value = State.Speaking

        // Foreground Service başlat
        MangaSpeechService.start(context, currentTitle, currentChapter, isPlaying = true)

        speakCurrentSegment(sessionId)
    }

    private fun speakCurrentSegment(sessionId: Long) {
        if (sessionId != currentSessionId) return
        if (currentSegmentIndex !in segments.indices) {
            _highlightState.value = null
            _state.value = State.Idle
            MangaSpeechService.updateState(context, currentTitle, currentChapter, isPlaying = false)
            return
        }

        val segment = segments[currentSegmentIndex]
        _highlightState.value = SpeechHighlightState(
            paragraphIndex = segment.paragraphIndex,
            sentenceIndex = segment.sentenceIndex,
            sentenceText = segment.text,
        )

        val utteranceId = "tts_${sessionId}_${currentSegmentIndex}"
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f)
        }
        engine?.speak(segment.text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    private fun speakNext() {
        currentSegmentIndex++
        if (currentSegmentIndex < segments.size) {
            speakCurrentSegment(currentSessionId)
        } else {
            _highlightState.value = null
            _state.value = State.Idle
            MangaSpeechService.updateState(context, currentTitle, currentChapter, isPlaying = false)
        }
    }

    override fun onPlay() {
        resume()
    }

    override fun onPause() {
        pause()
    }

    override fun onStop() {
        stop()
    }

    override fun onNext() {
        skipNext()
    }

    override fun onPrevious() {
        skipPrevious()
    }

    fun pause() {
        if (_state.value == State.Speaking) {
            // Mevcut utterance'ı durdur ama segmentIndex'i koru
            currentSessionId++
            engine?.stop()
            _state.value = State.Paused
            MangaSpeechService.updateState(context, currentTitle, currentChapter, isPlaying = false)
        }
    }

    fun resume() {
        if (_state.value == State.Paused) {
            _state.value = State.Speaking
            MangaSpeechService.updateState(context, currentTitle, currentChapter, isPlaying = true)
            speakCurrentSegment(++currentSessionId)
        }
    }

    fun skipNext() {
        if (segments.isEmpty()) {
            onNextChapterRequested?.invoke()
            return
        }
        if (currentSegmentIndex + 1 < segments.size) {
            currentSegmentIndex++
            if (_state.value == State.Speaking) {
                speakCurrentSegment(++currentSessionId)
            } else {
                val seg = segments[currentSegmentIndex]
                _highlightState.value = SpeechHighlightState(seg.paragraphIndex, seg.sentenceIndex, seg.text)
            }
        } else {
            onNextChapterRequested?.invoke()
        }
    }

    fun skipPrevious() {
        if (segments.isEmpty()) {
            onPreviousChapterRequested?.invoke()
            return
        }
        if (currentSegmentIndex > 0) {
            currentSegmentIndex--
            if (_state.value == State.Speaking) {
                speakCurrentSegment(++currentSessionId)
            } else {
                val seg = segments[currentSegmentIndex]
                _highlightState.value = SpeechHighlightState(seg.paragraphIndex, seg.sentenceIndex, seg.text)
            }
        } else {
            onPreviousChapterRequested?.invoke()
        }
    }

    fun stop() {
        currentSessionId++
        pendingRequest = null
        segments.clear()
        currentSegmentIndex = 0
        _highlightState.value = null
        engine?.stop()
        if (ready) _state.value = State.Idle
        MangaSpeechService.stop(context)
    }

    fun release() {
        stop()
        MangaSpeechService.listener = null
        engine?.shutdown()
        engine = null
        ready = false
    }
}
