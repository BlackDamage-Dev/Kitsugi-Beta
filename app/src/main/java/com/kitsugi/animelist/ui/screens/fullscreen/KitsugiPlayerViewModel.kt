package com.kitsugi.animelist.ui.screens.fullscreen



import android.app.Application

import android.content.Context

import android.content.Intent

import android.util.Log

import android.widget.Toast

import androidx.lifecycle.AndroidViewModel



import androidx.lifecycle.viewModelScope

import com.kitsugi.animelist.core.player.SubtitleInput

import com.kitsugi.animelist.core.player.PlaybackState

import com.kitsugi.animelist.data.cloudstream.CsPluginLoader

import com.kitsugi.animelist.data.cloudstream.CsStreamRunner

import com.kitsugi.animelist.data.local.KitsugiDatabase

import com.kitsugi.animelist.data.local.HistoryRepository

import com.kitsugi.animelist.data.remote.KitsugiMediaTabsClient

import com.kitsugi.animelist.data.remote.KitsugiStreamingEpisode

import com.kitsugi.animelist.data.repository.AddonStreamRepository

import com.kitsugi.animelist.data.repository.StreamSorter

import com.kitsugi.animelist.data.repository.StreamSource

import com.kitsugi.animelist.data.trailer.InAppYouTubeExtractor

import com.kitsugi.animelist.data.trailer.TrailerPlaybackSource

import com.kitsugi.animelist.ui.screens.fullscreen.components.MetaCastMember

import com.kitsugi.animelist.core.player.PlayerLogger

import kotlinx.coroutines.CancellationException

import kotlinx.coroutines.Job

import kotlinx.coroutines.async

import kotlinx.coroutines.launch

import kotlinx.coroutines.withContext

import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.StateFlow

import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.flow.first

import kotlinx.coroutines.flow.combine

import kotlinx.coroutines.flow.map

import kotlinx.coroutines.flow.SharingStarted

import kotlinx.coroutines.flow.stateIn

import com.kitsugi.animelist.core.player.AudioDelayRouteConfig

import com.kitsugi.animelist.core.player.AudioOutputRouteDetector

import com.kitsugi.animelist.core.player.AudioRoute

import com.kitsugi.animelist.data.remote.AniSkipClient

import com.kitsugi.animelist.data.remote.AnimeSkipClient

import com.kitsugi.animelist.data.remote.SkipInterval

import com.kitsugi.animelist.data.settings.AppSettings

import com.kitsugi.animelist.data.settings.SettingsDataStore

import com.kitsugi.animelist.data.local.toDomain

import com.kitsugi.animelist.data.local.CustomButton

import kotlinx.coroutines.flow.update

import com.kitsugi.animelist.core.player.PostPlayMode

import com.kitsugi.animelist.core.player.engine.PlayerEngineType

import com.kitsugi.animelist.core.player.engine.PlayerEngineSelector

import kotlinx.coroutines.async as asyncSkip

import kotlinx.coroutines.delay

import kotlinx.coroutines.awaitAll

import com.kitsugi.animelist.ui.screens.fullscreen.AudioChannels

import com.kitsugi.animelist.utils.KitsugiStrings

import com.kitsugi.animelist.R





class KitsugiPlayerViewModel(application: Application) : AndroidViewModel(application) {





    private val context = application.applicationContext

    private val dataStore = SettingsDataStore(context)



    private val historyRepository by lazy {

        HistoryRepository(KitsugiDatabase.getDatabase(context).historyDao())

    }



    // ── App Settings reactive StateFlow ──────────────────────────────────────

    val appSettings: StateFlow<AppSettings> = dataStore.settingsFlow.stateIn(

        scope = viewModelScope,

        started = SharingStarted.Eagerly,

        initialValue = AppSettings()

    )



    val customButtons: StateFlow<List<CustomButton>> =

        KitsugiDatabase.getDatabase(context).customButtonDao().subscribeAll()

            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())



    private val _primaryButton = MutableStateFlow<CustomButton?>(null)

    val primaryButton: StateFlow<CustomButton?> = _primaryButton.asStateFlow()



    private val _primaryButtonTitle = MutableStateFlow("")

    val primaryButtonTitle: StateFlow<String> = _primaryButtonTitle.asStateFlow()



    private var transientPlaybackSpeed: Float? = null

    private val _playbackSpeed = MutableStateFlow(1f)

    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private var initialPlayerPrefsRestored = false



    init {

        viewModelScope.launch {

            customButtons.collect { buttons ->

                buttons.firstOrNull { it.isFavorite }?.let { fav ->

                    if (_primaryButton.value == null) {

                        _primaryButton.value = fav

                    }

                    if (_primaryButtonTitle.value.isEmpty()) {

                        setPrimaryCustomButtonTitle(fav)

                    }

                }

            }

        }

        viewModelScope.launch {

            appSettings.collect { settings ->

                _playbackSpeed.value = transientPlaybackSpeed ?: settings.playerSpeed

                orchestrator.scrobble.threshold = settings.scrobbleThreshold

                if (!initialPlayerPrefsRestored) {

                    initialPlayerPrefsRestored = true

                    _audioChannels.value = settings.audioChannels

                    _statisticsPage.value = settings.playerStatisticsPage

                    _screenshotShowSubtitles.value = settings.screenshotSubtitles

                    _currentDecoder.value = Decoder.entries.firstOrNull { it.value == settings.mpvHwdecMode } ?: Decoder.Auto

                    if (settings.rememberPlayerBrightness && settings.playerBrightnessValue >= -0.75f) {

                        currentBrightness.value = settings.playerBrightnessValue

                    }

                    if (settings.rememberPlayerVolume && settings.playerVolumeValue >= 0f) {

                        val savedVol = settings.playerVolumeValue.toInt().coerceIn(0, maxVolume)

                        runCatching {

                            audioManager.setStreamVolume(

                                android.media.AudioManager.STREAM_MUSIC,

                                savedVol,

                                0

                            )

                            currentVolume.value = savedVol

                        }

                    }

                }

            }

        }

    }



    // State Variables - transitioned to reactive StateFlow system

    private val _currentVideoUrl = MutableStateFlow<String?>(null)

    val currentVideoUrl: StateFlow<String?> = _currentVideoUrl.asStateFlow()



    private val _currentAudioUrl = MutableStateFlow<String?>(null)

    val currentAudioUrl: StateFlow<String?> = _currentAudioUrl.asStateFlow()



    private val _currentHeaders = MutableStateFlow<Map<String, String>>(emptyMap())

    val currentHeaders: StateFlow<Map<String, String>> = _currentHeaders.asStateFlow()



    private val _currentSubtitles = MutableStateFlow<List<SubtitleInput>>(emptyList())

    val currentSubtitles: StateFlow<List<SubtitleInput>> = _currentSubtitles.asStateFlow()



    enum class SubtitleSearchStatus {

        IDLE,

        LOADING,

        SUCCESS,

        EMPTY

    }



    sealed interface SubtitleEvent {

        object LoadFailed : SubtitleEvent

    }



    private val _subtitleSearchStatus = MutableStateFlow(SubtitleSearchStatus.IDLE)

    val subtitleSearchStatus: StateFlow<SubtitleSearchStatus> = _subtitleSearchStatus.asStateFlow()



    private val _subtitleEvents = kotlinx.coroutines.flow.MutableSharedFlow<SubtitleEvent>()

    val subtitleEvents: kotlinx.coroutines.flow.SharedFlow<SubtitleEvent> = _subtitleEvents



    private val _currentTitle = MutableStateFlow("")

    val currentTitle: StateFlow<String> = _currentTitle.asStateFlow()



    private val _currentSourceIndex = MutableStateFlow(-1)

    val currentSourceIndex: StateFlow<Int> = _currentSourceIndex.asStateFlow()



    private val _currentStreamSources = MutableStateFlow<List<StreamSource>>(emptyList())

    val currentStreamSources: StateFlow<List<StreamSource>> = _currentStreamSources.asStateFlow()



    private val _currentAddonName = MutableStateFlow<String?>(null)

    val currentAddonName: StateFlow<String?> = _currentAddonName.asStateFlow()



    private val _currentEpisode = MutableStateFlow(1)

    val currentEpisode: StateFlow<Int> = _currentEpisode.asStateFlow()



    private val _isMovie = MutableStateFlow(false)

    val isMovie: StateFlow<Boolean> = _isMovie.asStateFlow()



    private val _episodesList = MutableStateFlow<List<KitsugiStreamingEpisode>>(emptyList())

    val episodesList: StateFlow<List<KitsugiStreamingEpisode>> = _episodesList.asStateFlow()



    /**

     * Bölüm listesi (çekmece) arka planda çözülüyor mu?

     * Çekmece boşken kullanıcıya "yükleniyor" göstergesi göstermek için kullanılır.

     */

    private val _episodesLoading = MutableStateFlow(false)

    val episodesLoading: StateFlow<Boolean> = _episodesLoading.asStateFlow()



    /** Çoklu kaynaklı bölüm çözücü (detay önbelleği → MAL/AniList/TMDB → eklenti → sentez). */

    private val episodesResolver by lazy {

        com.kitsugi.animelist.data.repository.PlayerEpisodeListResolver.create(context)

    }



    /** Hangi içerik için çözümleme yapıldı? (aynı içerikte tekrar tekrar ağa çıkılmaz) */

    private var episodesResolvedForKey: String? = null

    private var episodesLoadJob: Job? = null



    private val _userCancelledBinge = MutableStateFlow(false)

    var userCancelledBinge: Boolean

        get() = _userCancelledBinge.value

        set(value) { _userCancelledBinge.value = value }

    val userCancelledBingeFlow = _userCancelledBinge.asStateFlow()



    /**

     * DEEP-015 fix: Binge sayacını iptal eder, kartı gizler ve userCancelledBinge'i true yapar.

     * PlayerControls'dan hem X butonunda hem de "İptal" TextButton'ında çağrılmalı.

     * Doğrudan `userCancelledBinge = true` yerine bu fonksiyon kullanılmalı;

     * aksi hâlde countdown coroutine çalışmaya devam eder ve bölüm otomatik geçer.

     */

    fun cancelBingeCard() {

        _userCancelledBinge.value = true

        _showBingeCardState.value = false

        orchestrator.autoplay.cancelCountdown()

        Log.d("KitsugiPlayerViewModel", "Binge card cancelled by user — countdown stopped")

    }



    private val _isResolvingStream = MutableStateFlow(false)

    var isResolvingStream: Boolean

        get() = _isResolvingStream.value

        set(value) { _isResolvingStream.value = value }

    val isResolvingStreamFlow = _isResolvingStream.asStateFlow()



    private val _nextEpisodeLoading = MutableStateFlow(false)

    val nextEpisodeLoading: StateFlow<Boolean> = _nextEpisodeLoading.asStateFlow()



    private val _playbackSource = MutableStateFlow<TrailerPlaybackSource?>(null)

    val playbackSource: StateFlow<TrailerPlaybackSource?> = _playbackSource.asStateFlow()



    private val _isLoading = MutableStateFlow(true)

    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()



    private val _hasError = MutableStateFlow(false)

    val hasError: StateFlow<Boolean> = _hasError.asStateFlow()



    private val _errorDetails = MutableStateFlow<String?>(null)

    val errorDetails: StateFlow<String?> = _errorDetails.asStateFlow()



    private val _isAutoSwitching = MutableStateFlow(false)

    val isAutoSwitching: StateFlow<Boolean> = _isAutoSwitching.asStateFlow()



    // Active engine type — updated when engine is first selected and when fallback switches occur

    private val _activeEngineType = MutableStateFlow(PlayerEngineType.MEDIA3)

    val activeEngineType: StateFlow<PlayerEngineType> = _activeEngineType.asStateFlow()



    /** Called by the error recovery controller when a fallback engine switch is triggered. */

    /**

     * Yedek motora geçişte, hata veren motorun konumu. Yeni motor bu konumdan devam eder

     * (aksi halde yeni motor 0'dan başlayıp bölüm baştan oynardı).

     */

    private var pendingResumeAfterEngineSwitchMs: Long? = null



    fun onEngineSwitched(to: PlayerEngineType) {

        activeEngine?.let { engine ->

            pendingResumeAfterEngineSwitchMs = engine.currentPosition.takeIf { it > 0L }

        }

        _activeEngineType.value = to

    }



    /**

     * UI, seçili motorda desteklenmeyen bir özellik istediğinde (altyazı gecikmesi, altyazı

     * hızı, renk filtresi, negatif ses gecikmesi) motoru MPV'ye geçirir. Konum korunur;

     * yeni motor kaldığı yerden devam eder.

     */

    fun switchEngineForFeature(to: PlayerEngineType) {

        if (to == PlayerEngineType.EXTERNAL) return

        if (_activeEngineType.value == to) return

        onEngineSwitched(to)

    }



    /** Motor değişiminden kalan devam konumunu bir kez tüketir. */

    fun takePendingResumeAfterEngineSwitch(): Long? {

        val value = pendingResumeAfterEngineSwitchMs

        pendingResumeAfterEngineSwitchMs = null

        return value

    }



    // ── Controls visibility (Aniyomi-style OSD) ──────────────────────────────

    private val _controlsShown = MutableStateFlow(false)

    val controlsShown: StateFlow<Boolean> = _controlsShown.asStateFlow()



    private val _controlsResetTrigger = MutableStateFlow(0)

    val controlsResetTrigger: StateFlow<Int> = _controlsResetTrigger.asStateFlow()



    fun showControls() {

        _controlsShown.value = true

        _controlsResetTrigger.value += 1

    }



    fun hideControls() {

        _controlsShown.value = false

    }



    fun toggleControls() {

        _controlsShown.value = !_controlsShown.value

        _controlsResetTrigger.value += 1

    }



    // ── Playback position and duration reactive StateFlows ────────────────────

    private val _pos = MutableStateFlow(0L)

    val pos: StateFlow<Long> = _pos.asStateFlow()



    private val _duration = MutableStateFlow(0L)

    val duration: StateFlow<Long> = _duration.asStateFlow()



    // ── Paused state ─────────────────────────────────────────────────────────

    private val _paused = MutableStateFlow(false)

    val paused: StateFlow<Boolean> = _paused.asStateFlow()



    // ── Anime title reactive StateFlow ────────────────────────────────────────

    private val _animeTitleFlow = MutableStateFlow("")

    val animeTitleFlow: StateFlow<String> = _animeTitleFlow.asStateFlow()



    // Sleep Timer (Legacy compatibility)

    private val _sleepTimerSecondsLeft = MutableStateFlow(0)

    val sleepTimerSecondsLeft: StateFlow<Int> = _sleepTimerSecondsLeft.asStateFlow()



    private var sleepTimerJob: Job? = null



    fun startSleepTimer(minutes: Int) {

        stopSleepTimer()

        if (minutes <= 0) return

        _sleepTimerSecondsLeft.value = minutes * 60

        sleepTimerJob = viewModelScope.launch {

            while (_sleepTimerSecondsLeft.value > 0) {

                delay(1000)

                _sleepTimerSecondsLeft.value -= 1

            }

            activeEngine?.pause()

        }

    }



    fun stopSleepTimer() {

        sleepTimerJob?.cancel()

        sleepTimerJob = null

        _sleepTimerSecondsLeft.value = 0

    }



    // --- New Aniyomi-style UI StateFlows & Coordinates ---

    private val _sheetShown = MutableStateFlow<KitsugiSheets>(KitsugiSheets.None)

    val sheetShown: StateFlow<KitsugiSheets> = _sheetShown.asStateFlow()



    private val _dismissSheet = MutableStateFlow(false)

    val dismissSheet: StateFlow<Boolean> = _dismissSheet.asStateFlow()



    fun showSheet(sheet: KitsugiSheets) {

        _sheetShown.value = sheet

        if (sheet == KitsugiSheets.None) {

            _dismissSheet.value = false

            showSeekBar()

        } else {

            hideSeekBar()

            _panelShown.value = KitsugiPanels.None

            _dialogShown.value = KitsugiDialogs.None

        }

    }



    fun dismissSheet() {

        _dismissSheet.value = true

    }



    fun resetDismissSheet() {

        _dismissSheet.value = false

    }



    private val _panelShown = MutableStateFlow<KitsugiPanels>(KitsugiPanels.None)

    val panelShown: StateFlow<KitsugiPanels> = _panelShown.asStateFlow()



    fun showPanel(panel: KitsugiPanels) {

        _panelShown.value = panel

        if (panel == KitsugiPanels.None) {

            showSeekBar()

        } else {

            hideSeekBar()

            _sheetShown.value = KitsugiSheets.None

            _dialogShown.value = KitsugiDialogs.None

        }

    }



    private val _dialogShown = MutableStateFlow<KitsugiDialogs>(KitsugiDialogs.None)

    val dialogShown: StateFlow<KitsugiDialogs> = _dialogShown.asStateFlow()



    fun showDialog(dialog: KitsugiDialogs) {

        _dialogShown.value = dialog

        if (dialog == KitsugiDialogs.None) {

            showSeekBar()

        } else {

            hideSeekBar()

            _sheetShown.value = KitsugiSheets.None

            _panelShown.value = KitsugiPanels.None

            // Bölüm listesi çekmecesi boş açılmasın: liste eksikse arka planda tamamla.

            if (dialog == KitsugiDialogs.EpisodeList) {

                ensureEpisodesLoaded()

            }

        }

    }



    private val _seekBarShown = MutableStateFlow(true)

    val seekBarShown: StateFlow<Boolean> = _seekBarShown.asStateFlow()



    fun showSeekBar() {

        if (_sheetShown.value != KitsugiSheets.None) return

        _seekBarShown.value = true

    }



    fun hideSeekBar() {

        _seekBarShown.value = false

    }



    private val _areControlsLocked = MutableStateFlow(false)

    val areControlsLocked: StateFlow<Boolean> = _areControlsLocked.asStateFlow()



    fun lockControls() {

        _areControlsLocked.value = true

    }



    fun unlockControls() {

        _areControlsLocked.value = false

    }



    private val _readAhead = MutableStateFlow(0f)

    val readAhead: StateFlow<Float> = _readAhead.asStateFlow()



    fun updateReadAhead(value: Long) {

        _readAhead.value = value.toFloat()

    }



    private val _currentDecoder = MutableStateFlow<Decoder>(Decoder.Auto)

    val currentDecoder: StateFlow<Decoder> = _currentDecoder.asStateFlow()



    fun updateDecoder(decoder: Decoder) {

        _currentDecoder.value = decoder

        // Apply to MPV engine immediately

        val mpvEngine = activeEngine as? com.kitsugi.animelist.core.player.engine.MpvPlayerEngine

        mpvEngine?.mpvView?.mpv?.setPropertyString("hwdec", decoder.value)

        viewModelScope.launch { dataStore.setMpvHwdecMode(decoder.value) }

    }



    private val _statisticsPage = MutableStateFlow(0)

    val statisticsPage: StateFlow<Int> = _statisticsPage.asStateFlow()



    fun updateStatisticsPage(page: Int) {

        _statisticsPage.value = page.coerceIn(0, 5)

        (activeEngine as? com.kitsugi.animelist.core.player.engine.MpvPlayerEngine)?.setStatisticsPage(_statisticsPage.value)

        viewModelScope.launch { dataStore.setPlayerStatisticsPage(_statisticsPage.value) }

    }



    private val _audioChannels = MutableStateFlow(AudioChannels.Auto)

    val audioChannels: StateFlow<AudioChannels> = _audioChannels.asStateFlow()



    fun updateAudioChannels(channels: AudioChannels) {

        _audioChannels.value = channels

        // Apply to MPV engine: Aniyomi pattern — ReverseStereo uses af filter, others use audio-channels

        val mpvEngine = activeEngine as? com.kitsugi.animelist.core.player.engine.MpvPlayerEngine

        runCatching {

            if (channels == AudioChannels.ReverseStereo) {

                // Clear audio-channels first

                mpvEngine?.mpvView?.mpv?.setPropertyString(AudioChannels.Auto.property, AudioChannels.Auto.value)

            } else {

                // Clear ReverseStereo af filter

                mpvEngine?.mpvView?.mpv?.setPropertyString(AudioChannels.ReverseStereo.property, "")

            }

            mpvEngine?.mpvView?.mpv?.setPropertyString(channels.property, channels.value)

        }

        viewModelScope.launch { dataStore.setAudioChannels(channels) }

    }



    private val _remainingTime = MutableStateFlow(0)

    val remainingTime: StateFlow<Int> = _remainingTime.asStateFlow()



    private var timerJob: Job? = null



    fun startTimer(seconds: Int) {

        timerJob?.cancel()

        _remainingTime.value = seconds

        _sleepTimerSecondsLeft.value = seconds

        if (seconds < 1) return

        timerJob = viewModelScope.launch {

            for (time in seconds downTo 0) {

                _remainingTime.value = time

                _sleepTimerSecondsLeft.value = time

                delay(1000)

            }

            activeEngine?.pause()

        }

    }



    private val _chapters = MutableStateFlow<List<com.kitsugi.animelist.ui.screens.fullscreen.controls.components.IndexedSegment>>(emptyList())

    val chapters: StateFlow<List<com.kitsugi.animelist.ui.screens.fullscreen.controls.components.IndexedSegment>> = _chapters.asStateFlow()



    private var mediaChapters = emptyList<com.kitsugi.animelist.core.player.PlayerChapter>()

    private var skipChapters = emptyList<com.kitsugi.animelist.core.player.PlayerChapterRange>()



    fun setMediaChapters(chapters: List<com.kitsugi.animelist.core.player.PlayerChapter>) {

        mediaChapters = chapters

        if (chapters.isNotEmpty()) {

            orchestrator.skip.onMediaChaptersDetected(true)

            if (appSettings.value.aniSkipDisableOnChapters) {

                skipChapters = emptyList()

            }

        }

        rebuildChapters()

    }



    fun updateSkipChapters(intervals: List<com.kitsugi.animelist.data.remote.SkipInterval>) {

        skipChapters = intervals.map {

            com.kitsugi.animelist.core.player.PlayerChapterRange(it.type, (it.startTime * 1000).toLong(), (it.endTime * 1000).toLong())

        }

        rebuildChapters()

    }



    private fun rebuildChapters() {

        val merged = com.kitsugi.animelist.core.player.mergePlayerChapters(mediaChapters, skipChapters, _duration.value)

        updateChapters(merged.mapIndexed { index, chapter ->

            com.kitsugi.animelist.ui.screens.fullscreen.controls.components.IndexedSegment(chapter.title, chapter.positionMs / 1000f, index = index)

        })

    }



    fun updateChapters(chapterList: List<com.kitsugi.animelist.ui.screens.fullscreen.controls.components.IndexedSegment>) {

        _chapters.value = chapterList.sortedBy { it.start }.distinctBy { it.start }

        updateChapter(_chapters.value.lastOrNull { it.start <= _pos.value / 1000f } ?: _chapters.value.firstOrNull())

    }



    private val _currentChapter = MutableStateFlow<com.kitsugi.animelist.ui.screens.fullscreen.controls.components.IndexedSegment?>(null)

    val currentChapter: StateFlow<com.kitsugi.animelist.ui.screens.fullscreen.controls.components.IndexedSegment?> = _currentChapter.asStateFlow()



    fun updateChapter(chapter: com.kitsugi.animelist.ui.screens.fullscreen.controls.components.IndexedSegment?) {

        _currentChapter.value = chapter

    }



    val doubleTapSeekAmount = MutableStateFlow(0)

    val gestureSeekAmount = MutableStateFlow<Pair<Int, Int>?>(null)

    val seekText = MutableStateFlow<String?>(null)

    val isSeekingForwards = MutableStateFlow(false)



    val isVolumeSliderShown = MutableStateFlow(false)

    val isBrightnessSliderShown = MutableStateFlow(false)

    val currentBrightness = MutableStateFlow(-2.0f)

    val currentMPVVolume = MutableStateFlow(100)



    private val audioManager by lazy { context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager }

    val maxVolume by lazy { audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) }

    val currentVolume by lazy { MutableStateFlow(audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)) }

    val volumeBoostCap: Int get() = appSettings.value.volumeBoostCap



    fun updateSeekAmount(amount: Int) {

        doubleTapSeekAmount.value = amount

    }



    fun updateSeekText(text: String?) {

        seekText.value = text

    }



    fun handleLeftDoubleTap(): Boolean {

        val action = com.kitsugi.animelist.core.player.DoubleTapGestureAction.fromString(

            appSettings.value.leftDoubleTapGesture,

            com.kitsugi.animelist.core.player.DoubleTapGestureAction.SEEK

        )

        return executeDoubleTapAction(action, forward = false)

    }



    fun handleRightDoubleTap(): Boolean {

        val action = com.kitsugi.animelist.core.player.DoubleTapGestureAction.fromString(

            appSettings.value.rightDoubleTapGesture,

            com.kitsugi.animelist.core.player.DoubleTapGestureAction.SEEK

        )

        return executeDoubleTapAction(action, forward = true)

    }



    fun handleCenterDoubleTap(): Boolean {

        val action = com.kitsugi.animelist.core.player.DoubleTapGestureAction.fromString(

            appSettings.value.centerDoubleTapGesture,

            com.kitsugi.animelist.core.player.DoubleTapGestureAction.PLAY_PAUSE

        )

        return executeDoubleTapAction(action, forward = true)

    }



    private fun executeDoubleTapAction(

        action: com.kitsugi.animelist.core.player.DoubleTapGestureAction,

        forward: Boolean

    ): Boolean {

        return when (action) {

            com.kitsugi.animelist.core.player.DoubleTapGestureAction.SEEK -> {

                isSeekingForwards.value = forward

                val step = appSettings.value.doubleTapSeekSeconds

                val delta = if (forward) step else -step

                val newAmount = doubleTapSeekAmount.value + delta

                doubleTapSeekAmount.value = newAmount

                seekBy(delta * 1000L)

                true

            }

            com.kitsugi.animelist.core.player.DoubleTapGestureAction.PLAY_PAUSE -> {

                togglePlay()

                false

            }

            com.kitsugi.animelist.core.player.DoubleTapGestureAction.CUSTOM -> {

                _primaryButton.value?.let { executeCustomButton(it) }

                false

            }

            com.kitsugi.animelist.core.player.DoubleTapGestureAction.SWITCH_SUB -> {

                activeEngine?.executeCommand(arrayOf("cycle", "sub"))

                false

            }

            com.kitsugi.animelist.core.player.DoubleTapGestureAction.NONE -> false

        }

    }



    fun seekBy(offsetMs: Long) {

        val current = _pos.value

        val dur = _duration.value

        val target = (current + offsetMs).coerceIn(0L, dur)

        activeEngine?.seekTo(target)

        _pos.value = target

    }



    fun changeVolumeTo(volume: Int) {

        val newVolume = volume.coerceIn(0..maxVolume)

        audioManager.setStreamVolume(

            android.media.AudioManager.STREAM_MUSIC,

            newVolume,

            0,

        )

        currentVolume.value = newVolume

        if (appSettings.value.rememberPlayerVolume) {

            viewModelScope.launch { dataStore.setPlayerVolumeValue(newVolume.toFloat()) }

        }

    }



    fun changeMPVVolumeTo(volume: Int) {

        currentMPVVolume.value = volume.coerceIn(0, appSettings.value.volumeBoostCap)

        activeEngine?.setVolume(currentMPVVolume.value / 100f)

    }



    private var volumeBeforeMute: Int? = null

    private var mpvVolumeBeforeMute: Int? = null



    fun toggleMute() {

        val muted = currentVolume.value == 0 && currentMPVVolume.value == 0

        if (muted) {

            changeVolumeTo(volumeBeforeMute?.takeIf { it > 0 } ?: maxOf(1, maxVolume / 3))

            changeMPVVolumeTo(mpvVolumeBeforeMute?.takeIf { it > 0 } ?: 100)

            volumeBeforeMute = null

            mpvVolumeBeforeMute = null

        } else {

            volumeBeforeMute = currentVolume.value.takeIf { it > 0 }

            mpvVolumeBeforeMute = currentMPVVolume.value.takeIf { it > 0 }

            changeVolumeTo(0)

            changeMPVVolumeTo(0)

        }

        displayVolumeSlider()

    }



    fun changeBrightnessTo(brightness: Float) {

        val clamped = brightness.coerceIn(-0.75f, 1f)

        currentBrightness.value = clamped

        if (appSettings.value.rememberPlayerBrightness) {

            viewModelScope.launch { dataStore.setPlayerBrightnessValue(clamped) }

        }

        val activity = context.findActivity() ?: return

        val params = activity.window.attributes

        params.screenBrightness = brightness.coerceIn(0f, 1f)

        activity.window.attributes = params

    }



    fun toggleInvertDurationTimer() {

        val next = !appSettings.value.invertDurationTimer

        viewModelScope.launch { dataStore.setInvertDurationTimer(next) }

    }



    fun getSystemBrightness(): Float {

        return try {

            android.provider.Settings.System.getInt(

                context.contentResolver,

                android.provider.Settings.System.SCREEN_BRIGHTNESS

            ) / 255f

        } catch (e: Exception) {

            0.5f

        }

    }



    private fun Context.findActivity(): android.app.Activity? {

        var ctx = this

        while (ctx is android.content.ContextWrapper) {

            if (ctx is android.app.Activity) return ctx

            ctx = ctx.baseContext

        }

        return null

    }



    fun displayVolumeSlider() {

        isVolumeSliderShown.value = true

    }



    fun displayBrightnessSlider() {

        isBrightnessSliderShown.value = true

    }



    val playerUpdate = MutableStateFlow<KitsugiPlayerUpdates>(KitsugiPlayerUpdates.None)



    // ── TASK_042: PlaybackState StateFlow ────────────────────────────────────

    // NuvioTV PlayerRuntimeController.playerState pattern referans alındı.

    // PlayerEngine.Listener.onStateChanged olaylarını üst seviye PlaybackState'e dönüştürür.

    private val _playerState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)

    val playerState: StateFlow<PlaybackState> = _playerState.asStateFlow()



    /** PlayerEngine durumunu PlaybackState'e çevirir ve StateFlow'u günceller. */

    fun updatePlayerState(engineState: com.kitsugi.animelist.core.player.engine.PlayerEngine.State, isPlaying: Boolean) {

        _paused.value = !isPlaying

        _playerState.value = when (engineState) {

            com.kitsugi.animelist.core.player.engine.PlayerEngine.State.IDLE      -> PlaybackState.Idle

            com.kitsugi.animelist.core.player.engine.PlayerEngine.State.BUFFERING -> PlaybackState.Buffering

            com.kitsugi.animelist.core.player.engine.PlayerEngine.State.READY     -> if (isPlaying) PlaybackState.Playing else PlaybackState.Paused

            com.kitsugi.animelist.core.player.engine.PlayerEngine.State.ENDED     -> PlaybackState.Ended

        }

    }



    /** Hata olustugunda PlayerState.Error'a gec - URL/host ve ipucu ile zengin mesaj */

    fun setPlayerError(errorCode: Int, errorMsg: String) {

        val enriched = enrichPlayerError(errorCode, errorMsg, _currentVideoUrl.value, _currentAddonName.value)

        _playerState.value = PlaybackState.Error(errorMessage = enriched.display, errorCode = errorCode)

        _errorDetails.value = enriched.details

        try {

            com.kitsugi.animelist.data.cloudstream.diag.CsTrace.error(

                _currentAddonName.value ?: "Player", "playback",

                "PlayerError code=" + errorCode.toString() + " msg=" + errorMsg.take(200) + " url=" + (_currentVideoUrl.value?.take(100) ?: "-"), null

            )

        } catch (_: Exception) {}

    }



    /**

     * Oynatma hatası durumunu temizler.

     *

     * Kullanım: hata sonrası **otomatik** olarak başka bir akışa geçildiğinde (ör. süresi

     * dolmuş kalite varyantından kaynağın master akışına dönüş) hata ekranı ekranda

     * kalmasın diye. Kaynak değişimini kullanıcı elle yaptığında da çağrılabilir.

     */

    fun clearPlayerError() {

        _hasError.value = false

        _errorDetails.value = null

        _playerState.value = PlaybackState.Idle

    }



    private data class EnrichedError(val display: String, val details: String)



    private fun enrichPlayerError(errorCode: Int, rawMsg: String, url: String?, addon: String?): EnrichedError {

        val host = runCatching { java.net.URI(url ?: "").host }.getOrNull()

        val shortUrl = url?.take(110) ?: "--"

        val httpCode = Regex("Response code:\\s*(\\d{3})").find(rawMsg)?.groupValues?.get(1)

        val hint = when {

            rawMsg.contains("403") || httpCode == "403" -> "IPUCU 403: Kaynak erisime kapali. VPN deneyin veya baska kaynak secin. (Origin/Referer hatasiysa appi guncelleyin)"

            rawMsg.contains("404") || httpCode == "404" -> "IPUCU 404: Bu kaynak artik yok/silindi. Baska kaynak deneyin."

            rawMsg.contains("5004") -> "IPUCU 5004: CDN Origin dogrulamasi basarisiz. Uygulama v2.4.307+ olmali - guncelleyin, olmazsa VPN deneyin."

            rawMsg.contains("UnrecognizedInputFormat") -> "IPUCU Format hatasi: Sunucu HLS yerine HTML dondurdu. Kaynak bozuk olabilir - baska kaynak deneyin."

            rawMsg.contains("Source error") && rawMsg.contains("Unable to connect") -> "IPUCU Baglanti hatasi: Internet/CF korumasi. Biraz bekleyip tekrar deneyin."

            errorCode in 4000..4005 || rawMsg.contains("Decoder") -> "IPUCU Kodek hatasi: Bu cihaz formati cozemiyor. MPV motoru otomatik denenecek."

            else -> null

        }

        val display = buildString {

            append(rawMsg.take(300))

            if (hint != null) append("\n\n" + hint)

        }

        val details = buildString {

            append("Kaynak: " + (addon ?: "bilinmeyen") + "\n")

            if (host != null) append("Host: " + host + "\n")

            append("URL: " + shortUrl + "\n")

            append("Hata kodu: " + errorCode)

            if (httpCode != null) append(" (HTTP " + httpCode + ")")

            append("\nMesaj: " + rawMsg.take(400) + "\n")

            if (hint != null) append("\n" + hint + "\n")

            append("\n-- Bu detay Kopyala ile alinip paylasilabilir. Ayarlar > Tani Raporu'ndan tam log da alinabilir.")

        }

        return EnrichedError(display, details)

    }



    private val _detectedFrameRateRaw = MutableStateFlow(0f)

    val detectedFrameRateRaw: StateFlow<Float> = _detectedFrameRateRaw.asStateFlow()



    private val _detectedFrameRate = MutableStateFlow(0f)

    val detectedFrameRate: StateFlow<Float> = _detectedFrameRate.asStateFlow()



    private val _afrProbeRunning = MutableStateFlow(false)

    val afrProbeRunning: StateFlow<Boolean> = _afrProbeRunning.asStateFlow()



    /** How many consecutive auto-switch attempts have been made for the current episode. */

    private var autoSwitchAttempts = 0

    private val maxAutoSwitchAttempts = 5



    // Meta parameters passed from Activity

    private var malId: Int? = null

    private var aniListId: Int? = null

    private var tmdbId: Int? = null

    private var bangumiId: Int? = null

    private var knownImdbId: String? = null

    private var seasonNum: Int = 1

    private var animeTitle: String = ""

    private var titleEnglish: String? = null

    private var titleRomaji: String? = null

    private var titleNative: String? = null

    private var synonyms: List<String> = emptyList()

    private var startYear: Int? = null

    // CS3 kökenli içerikler için kaynak bilgisi (binge-watch + geçmişten devam)

    private var cs3Url: String? = null

    private var cs3ApiName: String? = null

    private var cs3PluginId: String? = null

    private var historyAnimeId: String? = null

    private var historyEngineEpisode: Int? = null
    private var mediaType: com.kitsugi.animelist.model.MediaType? = null



    private val _isInitialized = MutableStateFlow(false)

    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()



    private var lastInitializedKey: String? = null



    fun initialize(

        videoId: String?,

        videoUrl: String?,

        audioUrl: String?,

        title: String,

        requestHeaders: Map<String, String>,

        initialSubtitles: List<SubtitleInput>,

        streamSources: List<StreamSource>,

        initialIndex: Int,

        malId: Int?,

        aniListId: Int?,

        tmdbId: Int? = null,

        season: Int,

        episode: Int,

        animeTitle: String,

        titleEnglish: String?,

        titleRomaji: String?,

        titleNative: String?,

        startYear: Int?,

        isMovie: Boolean = false,

        activity: android.app.Activity? = null,

        cs3Url: String? = null,

        cs3ApiName: String? = null,

        synonyms: List<String> = emptyList(),

        bangumiId: Int? = null,

        imdbId: String? = null,

        historyAnimeId: String? = null,

        autoResume: Boolean = false,

        resumePositionMs: Long = 0L,

        mediaType: com.kitsugi.animelist.model.MediaType? = null,

        knownImdbId: String? = null

    ) {

        val initKey = "${videoId ?: ""}_${videoUrl ?: ""}_${season}_${episode}_${aniListId ?: 0}_${malId ?: 0}_${tmdbId ?: 0}_${bangumiId ?: 0}_${mediaType?.name}_${knownImdbId ?: imdbId}_${isMovie}_${synonyms.hashCode()}_$historyAnimeId"

        if (_isInitialized.value && lastInitializedKey == initKey) return

        _isInitialized.value = true

        lastInitializedKey = initKey



        this.malId = malId

        this.aniListId = aniListId

        this.tmdbId = tmdbId

        this.bangumiId = bangumiId

        this.knownImdbId = imdbId?.trim()?.takeIf { Regex("tt[0-9]{5,12}").matches(it) }

        this.seasonNum = season

        _isMovie.value = isMovie || (season == 0 && episode <= 1)

        this.animeTitle = animeTitle

        this.titleEnglish = titleEnglish

        this.titleRomaji = titleRomaji

        this.titleNative = titleNative

        this.synonyms = synonyms.map { it.trim() }.filter { it.isNotBlank() }.distinct()

        this.startYear = startYear

        this.cs3Url = cs3Url

        this.cs3ApiName = cs3ApiName

        this.historyAnimeId = historyAnimeId

        this.historyEngineEpisode = null

        this.mediaType = mediaType

        if (knownImdbId != null) {
            this.knownImdbId = knownImdbId.trim().takeIf { Regex("tt[0-9]{5,12}").matches(it) }
        }

        this.cs3PluginId = com.kitsugi.animelist.data.local.WatchHistorySource.cloudStreamPluginId(
            streamSources.getOrNull(initialIndex)?.addonId
                ?: historyAnimeId?.let { com.kitsugi.animelist.data.local.WatchHistoryManager.find(it, season, episode)?.addonId }
        )

        _animeTitleFlow.value = animeTitle



        _currentVideoUrl.value = videoUrl

        _currentAudioUrl.value = audioUrl

        _currentHeaders.value = requestHeaders

        _currentSubtitles.value = initialSubtitles

        _currentStreamSources.value = streamSources

        _currentSourceIndex.value = initialIndex

        _currentTitle.value = title

        _currentEpisode.value = episode

        if (initialIndex in streamSources.indices) {

            _currentAddonName.value = streamSources[initialIndex].addonName

        }

        // Update hasFallback: true if there are multiple sources to switch between

        orchestrator.errorRecovery.hasFallback = streamSources.size > 1

        // Reset engine fallback state and derive initial engine from settings

        orchestrator.errorRecovery.reset()

        _activeEngineType.value = PlayerEngineSelector.selectEngine(

            settings = appSettings.value,

            videoUrl = videoUrl ?: ""

        )



        viewModelScope.launch {

            loadEpisodes()

        }



        // AniSkip: bölüm/seri değiştiğinde intro/outro aralıklarını yükle

        if (malId != null) {

            loadSkipIntervals(malId, episode)

        }



        loadPlaybackSource(videoId, activity)

        if (videoUrl != null) {

            fetchAutoSubtitles()

        }

    }



    private fun loadPlaybackSource(videoId: String?, activity: android.app.Activity?) {

        viewModelScope.launch {

            _isLoading.value = true

            _hasError.value = false

            when {

                videoId != null -> {

                    try {

                        val src = withContext(kotlinx.coroutines.Dispatchers.IO) {

                            InAppYouTubeExtractor()

                                .extractPlaybackSource("https://www.youtube.com/watch?v=$videoId")

                        }

                        if (src != null) {

                            _playbackSource.value = src

                        } else {

                            _hasError.value = true

                        }

                    } catch (e: Exception) {

                        _hasError.value = true

                        _errorDetails.value = e.message

                    } finally {

                        _isLoading.value = false

                    }

                }

                _currentVideoUrl.value != null -> {

                    val settings = SettingsDataStore(context).settingsFlow.first()

                    // Yerel kopya: `when` koşulu ile kullanım arasında akış (StateFlow)

                    // başka bir coroutine tarafından null'a çekilirse `!!` çökme üretiyordu.

                    val resolvedVideoUrl = _currentVideoUrl.value

                    if (resolvedVideoUrl != null) {

                        viewModelScope.launch {

                            runAfrPreflightIfEnabled(

                                context = context,

                                activity = activity,

                                url = resolvedVideoUrl,

                                headers = _currentHeaders.value,

                                frameRateMatchingMode = settings.frameRateMatchingMode,

                                resolutionMatchingEnabled = settings.resolutionMatchingEnabled

                            )

                        }

                        val selectedStream = _currentStreamSources.value.getOrNull(_currentSourceIndex.value)

                        _playbackSource.value = TrailerPlaybackSource(

                            videoUrl = resolvedVideoUrl,

                            audioUrl = _currentAudioUrl.value,

                            externalAudioTracks = selectedStream?.externalAudioTracks.orEmpty(),

                            streamTypeHint = selectedStream?.streamTypeHint

                        )

                    }

                    _isLoading.value = false

                }

                else -> {

                    _hasError.value = true

                    _isLoading.value = false

                }

            }

        }

    }



    suspend fun runAfrPreflightIfEnabled(

        context: Context,

        activity: android.app.Activity?,

        url: String,

        headers: Map<String, String>,

        frameRateMatchingMode: com.kitsugi.animelist.data.settings.FrameRateMatchingMode,

        resolutionMatchingEnabled: Boolean

    ) {

        if (frameRateMatchingMode == com.kitsugi.animelist.data.settings.FrameRateMatchingMode.OFF) {

            _detectedFrameRateRaw.value = 0f

            _detectedFrameRate.value = 0f

            _afrProbeRunning.value = false

            return

        }



        if (url.contains("127.0.0.1") || url.contains("localhost")) {

            Log.d("KitsugiPlayerViewModel", "AFR preflight skipped for local P2P stream: $url")

            _detectedFrameRateRaw.value = 0f

            _detectedFrameRate.value = 0f

            _afrProbeRunning.value = false

            return

        }



        if (activity == null) {

            Log.w("KitsugiPlayerViewModel", "AFR preflight skipped: host activity unavailable")

            return

        }



        if (_afrProbeRunning.value) {

            Log.d("KitsugiPlayerViewModel", "AFR preflight: already running, skipping duplicate execution")

            return

        }



        _afrProbeRunning.value = true

        _detectedFrameRateRaw.value = 0f

        _detectedFrameRate.value = 0f



        val streamHeaders = headers.filterKeys { !it.equals("Range", ignoreCase = true) }

        val probeHeaders = streamHeaders.toMutableMap().apply {

            put("Connection", "close")

        }



        try {

            // ─── T1.4: DisplayCapabilities — ekran yetenek tespiti ve loglama ────

            val displaySnapshot = com.kitsugi.animelist.core.player.DisplayCapabilities.detect(activity)

            com.kitsugi.animelist.core.player.DisplayCapabilities.logSummary(displaySnapshot)



            // Ekran hem AFR hem çözünürlük değiştirmeyi desteklemiyorsa probe'u atla

            if (!displaySnapshot.supportsFrameRateSwitching && !resolutionMatchingEnabled) {

                Log.i("KitsugiPlayerViewModel", "AFR preflight: display does not support frame rate switching, skipping probe")

                return

            }

            // ─────────────────────────────────────────────────────────────────────



            val cached = com.kitsugi.animelist.core.player.FrameRateUtils.getCachedFrameRate(url, headers)

            if (cached != null) {

                Log.d("KitsugiPlayerViewModel", "AFR preflight: cache hit! Using cached FPS=${cached.snapped}")

                _detectedFrameRateRaw.value = cached.raw

                _detectedFrameRate.value = cached.snapped



                val prefer23976ProbeBias = cached.raw in 23.95f..23.999f

                val targetFrameRate = com.kitsugi.animelist.core.player.FrameRateUtils.refineFrameRateForDisplay(

                    activity = activity,

                    detectedFps = cached.snapped,

                    prefer23976Near24 = prefer23976ProbeBias

                )



                com.kitsugi.animelist.core.player.FrameRateUtils.matchFrameRateAndWait(

                    activity = activity,

                    frameRate = targetFrameRate,

                    videoWidth = cached.videoWidth,

                    videoHeight = cached.videoHeight,

                    resolutionMatchingEnabled = resolutionMatchingEnabled

                )

                return

            }



            val nextLibDetection = kotlinx.coroutines.withTimeoutOrNull(6000L) {

                withContext(kotlinx.coroutines.Dispatchers.IO) {

                    com.kitsugi.animelist.core.player.FrameRateUtils.detectFrameRateFromNextLib(

                        context = context,

                        sourceUrl = url,

                        headers = streamHeaders

                    )

                }

            }

            val detection = if (nextLibDetection != null) {

                nextLibDetection

            } else {

                Log.w(

                    "KitsugiPlayerViewModel",

                    "AFR preflight NextLib probe failed/timed out; trying extractor fallback"

                )

                kotlinx.coroutines.withTimeoutOrNull(4000L) {

                    withContext(kotlinx.coroutines.Dispatchers.IO) {

                        com.kitsugi.animelist.core.player.FrameRateUtils.detectFrameRateFromExtractor(

                            context = context,

                            sourceUrl = url,

                            headers = probeHeaders

                        )

                    }

                }

            }



            if (detection == null) {

                Log.w("KitsugiPlayerViewModel", "AFR preflight probe timed out/failed (NextLib + extractor fallback)")

                return

            }



            com.kitsugi.animelist.core.player.FrameRateUtils.cacheFrameRate(url, headers, detection)

            _detectedFrameRateRaw.value = detection.raw

            _detectedFrameRate.value = detection.snapped



            val prefer23976ProbeBias = detection.raw in 23.95f..23.999f

            val targetFrameRate = com.kitsugi.animelist.core.player.FrameRateUtils.refineFrameRateForDisplay(

                activity = activity,

                detectedFps = detection.snapped,

                prefer23976Near24 = prefer23976ProbeBias

            )



            com.kitsugi.animelist.core.player.FrameRateUtils.matchFrameRateAndWait(

                activity = activity,

                frameRate = targetFrameRate,

                videoWidth = detection.videoWidth,

                videoHeight = detection.videoHeight,

                resolutionMatchingEnabled = resolutionMatchingEnabled

            )

        } catch (e: Exception) {

            Log.e("KitsugiPlayerViewModel", "AFR preflight error: ${e.message}", e)

        } finally {

            _afrProbeRunning.value = false

        }

    }



    private val episodeStreamCacheLock = Any()

    private val episodeStreamCache = LinkedHashMap<String, List<StreamSource>>()

    private val episodeStreamFetches = mutableMapOf<String, kotlinx.coroutines.Deferred<List<StreamSource>>>()



    private fun episodeStreamCacheKey(targetEpisode: Int, addonName: String?): String =

        "$seasonNum:$targetEpisode:${addonName.orEmpty().trim().lowercase()}"



    private suspend fun fetchPreferredEpisodeStreams(targetEpisode: Int, addonName: String?): List<StreamSource> {

        val preferred = fetchStreamsForEpisode(targetEpisode, preferredAddonName = addonName)

        return if (preferred.isNotEmpty() || addonName.isNullOrBlank()) {

            preferred

        } else {

            // Provider-specific lookup failed (provider removed or episode unavailable there):

            // only then broaden the search to every installed source.

            fetchStreamsForEpisode(targetEpisode)

        }

    }



    /**

     * Warm the next episode while the current one is playing. The provider name is part

     * of the cache key, so a result fetched for one extension is never treated as a

     * provider-specific result for another extension.

     */

    fun prefetchNextEpisode() {

        if (_isMovie.value) return

        val targetEpisode = _currentEpisode.value + 1

        val addonName = _currentAddonName.value?.takeIf { it.isNotBlank() } ?: return

        val key = episodeStreamCacheKey(targetEpisode, addonName)

        synchronized(episodeStreamCacheLock) {

            if (episodeStreamCache.containsKey(key) || episodeStreamFetches.containsKey(key)) return

            val deferred = viewModelScope.async(kotlinx.coroutines.Dispatchers.IO) {

                fetchPreferredEpisodeStreams(targetEpisode, addonName)

            }

            episodeStreamFetches[key] = deferred

            deferred.invokeOnCompletion { cause ->

                if (cause != null) {

                    synchronized(episodeStreamCacheLock) {

                        if (episodeStreamFetches[key] === deferred) episodeStreamFetches.remove(key)

                    }

                }

            }

        }

    }



    private suspend fun getStreamsForEpisode(targetEpisode: Int): List<StreamSource> {

        val addonName = _currentAddonName.value

        val key = episodeStreamCacheKey(targetEpisode, addonName)

        val deferred = synchronized(episodeStreamCacheLock) {

            episodeStreamCache[key]?.let { return it }

            episodeStreamFetches[key] ?: viewModelScope.async(kotlinx.coroutines.Dispatchers.IO) {

                fetchPreferredEpisodeStreams(targetEpisode, addonName)

            }.also { episodeStreamFetches[key] = it }

        }

        return try {

            deferred.await().also { streams ->

                synchronized(episodeStreamCacheLock) {

                    episodeStreamFetches.remove(key, deferred)

                    if (streams.isNotEmpty()) {

                        episodeStreamCache[key] = streams

                        while (episodeStreamCache.size > 8) {

                            episodeStreamCache.remove(episodeStreamCache.keys.first())

                        }

                    }

                }

            }

        } catch (cancel: CancellationException) {

            synchronized(episodeStreamCacheLock) { episodeStreamFetches.remove(key, deferred) }

            throw cancel

        } catch (error: Exception) {

            synchronized(episodeStreamCacheLock) { episodeStreamFetches.remove(key, deferred) }

            throw error

        }

    }



    /**

     * Bölüm listesini çözer. Eskiden yalnızca MAL/AniList kimliği olan içeriklerde

     * çalışıyordu; TMDB, Simkl ya da yalnızca eklenti (CS3) üzerinden açılan içeriklerde

     * liste boş kalıyor ve "Bölüm Listesi" çekmecesi bomboş açılıyordu.

     *

     * Artık sıra: detay sayfası önbelleği → MAL/AniList/TMDB → açık eklenti → sentez.

     * Sonuç hiçbir zaman boş kalmaz ([PlayerEpisodeListResolver]).

     */

    private suspend fun loadEpisodes(force: Boolean = false) {

        if (_isMovie.value) {

            _episodesList.value = emptyList()

            return

        }

        val key = episodeResolutionKey()

        val current = _currentEpisode.value

        // Bu içerik için liste zaten çözüldüyse ağa tekrar çıkılmaz; yalnızca oynatılan

        // bölüm listede yoksa yerel olarak eklenir (bölüm geçişlerinde liste hep dolu kalır).

        if (!force && key == episodesResolvedForKey) {

            appendEpisodeIfMissing(current)

            return

        }

        if (_episodesLoading.value && !force) return



        _episodesLoading.value = true

        try {

            val request = com.kitsugi.animelist.data.repository.PlayerEpisodeListResolver.Request(

                malId = malId,

                aniListId = aniListId,

                tmdbId = tmdbId,

                season = seasonNum,

                currentEpisode = current,

                isMovie = _isMovie.value,

                totalEpisodes = knownTotalEpisodeCount(),

                title = animeTitle,

                alternativeTitles = (listOfNotNull(titleEnglish, titleRomaji, titleNative) + synonyms)

                    .map { it.trim() }

                    .filter { it.isNotBlank() }

                    .distinct(),

                startYear = startYear,

                cs3Url = cs3Url,

                cs3ApiName = cs3ApiName

            )

            val resolved = episodesResolver.resolve(request)

            if (resolved.isNotEmpty()) {

                _episodesList.value = resolved

                episodesResolvedForKey = key

            } else if (_episodesList.value.isEmpty()) {

                Log.w("KitsugiPlayerViewModel", "Bölüm listesi boş döndü ($key)")

            }

        } catch (cancel: CancellationException) {

            throw cancel

        } catch (e: Exception) {

            Log.e("KitsugiPlayerViewModel", "Error fetching episodes", e)

        } finally {

            _episodesLoading.value = false

        }

    }



    /**

     * Bölüm listesi çekmecesi açıldığında çağrılır: liste eksikse arka planda tamamlar.

     * Çekmece bu sayede hiçbir zaman boş açılmaz.

     */

    fun ensureEpisodesLoaded(force: Boolean = false) {

        if (_episodesLoading.value && !force) return

        episodesLoadJob?.takeIf { it.isActive && !force }?.let { return }

        episodesLoadJob = viewModelScope.launch { loadEpisodes(force = force) }

    }



    /** Kullanıcı "Yeniden Dene"ye bastığında tüm kaynaklar sıfırdan denenir. */

    fun refreshEpisodes() {

        episodesResolvedForKey = null

        ensureEpisodesLoaded(force = true)

    }



    /**

     * Çözülmüş listede oynatılan bölüm yoksa (ör. sentetik liste kısa kaldıysa)

     * numaralı satırları yerelde tamamlar. Böylece "sıradaki/önceki bölüm" her

     * zaman listede görünür.

     */

    private fun appendEpisodeIfMissing(episode: Int) {

        if (episode <= 0) return

        val existing = _episodesList.value

        if (existing.any { it.episodeNumber == episode }) return

        val highest = existing.mapNotNull { it.episodeNumber }.maxOrNull() ?: 0

        val missing = (highest + 1..episode).map { number ->

            KitsugiStreamingEpisode(

                title = KitsugiStrings.get(R.string.inline_episode_1_s_dc662be, number),

                thumbnail = null,

                url = null,

                site = "Kitsugi",

                seasonNumber = seasonNum.takeIf { it > 0 } ?: 1,

                episodeNumber = number

            )

        }

        if (missing.isEmpty()) return

        _episodesList.value = (existing + missing).sortedBy { it.episodeNumber }

    }



    private fun episodeResolutionKey(): String = "$malId|$aniListId|$tmdbId|$seasonNum"



    /** Yerel veritabanından bilinen toplam bölüm sayısı (varsa). */

    private suspend fun knownTotalEpisodeCount(): Int? {

        val candidates = listOfNotNull(malId, aniListId)

        if (candidates.isEmpty()) return null

        return runCatching {

            val dao = KitsugiDatabase.getDatabase(context).mediaEntryDao()

            candidates.firstNotNullOfOrNull { id ->

                dao.getByMalId(id)?.total?.takeIf { it > 0 }

            }

        }.getOrNull()

    }



    suspend fun fetchStreamsForEpisode(

        nextEp: Int,

        preferredAddonName: String? = null

    ): List<StreamSource> = withContext(kotlinx.coroutines.Dispatchers.IO) {

        val repository = AddonStreamRepository(context)



        // ── CS3 direkt URL modu: cs3Url varsa tüm eklentileri aramak yerine

        // doğrudan o URL üzerinden ilgili bölümü çözümle. ─────────────────────────

        //

        // 2026-11-02: Kullanıcı içeriği belirli bir eklentinin içinden açtıysa sonraki

        // bölüm de ÖNCE aynı eklentide aranır (doğrudan URL → aynı eklentinin kendi

        // başlık araması). Diğer eklentiler/Stremio addon'ları ancak bu eklenti bölümü

        // veremezse devreye girer; aksi hâlde kaynak listesi sessizce karışıyordu.

        val localCs3Url = cs3Url

        val localCs3ApiName = cs3ApiName

        if (!localCs3ApiName.isNullOrBlank() &&

            (preferredAddonName == null || preferredAddonName.equals(localCs3ApiName, ignoreCase = true))) {

            val pluginMediaType = if (_isMovie.value) com.kitsugi.animelist.model.MediaType.Movie

                else com.kitsugi.animelist.model.MediaType.Anime

            val db = KitsugiDatabase.getDatabase(context)

            val csPlugin = db.csPluginDao().getEnabledPlugins()

                .firstOrNull {

                    it.id.equals(localCs3ApiName, ignoreCase = true) ||

                        it.name.equals(localCs3ApiName, ignoreCase = true)

                }

            val apis: List<com.lagradost.cloudstream3.MainAPI> = try {

                if (csPlugin != null) CsPluginLoader.loadExtension(context, csPlugin.id) else emptyList()

            } catch (e: Exception) {

                Log.e("KitsugiPlayerViewModel", "fetchStreamsForEpisode: eklenti yüklenemedi (${localCs3ApiName})", e)

                emptyList()

            }

            val scopedApis = apis.ifEmpty {

                com.lagradost.cloudstream3.APIHolder.allProviders

                    .filter { it.name.equals(localCs3ApiName, ignoreCase = true) }

            }



            // 1) Doğrudan sağlayıcı URL'si

            if (!localCs3Url.isNullOrBlank()) {

                for (api in scopedApis) {

                    val resolved = CsStreamRunner.getStreamsForUrl(

                        api = api,

                        url = localCs3Url,

                        season = seasonNum,

                        episode = nextEp,

                        isMovie = _isMovie.value,

                        mediaType = pluginMediaType

                    )

                    if (resolved.isNotEmpty()) {

                        Log.d(

                            "KitsugiPlayerViewModel",

                            "fetchStreamsForEpisode: CS3 direkt URL modu — ${resolved.size} kaynak (${api.name})"

                        )

                        return@withContext StreamSorter.sort(resolved.map { it.copy(addonId = csPlugin?.let { p -> com.kitsugi.animelist.data.local.WatchHistorySource.cloudStreamAddonId(p.id) }) })

                    }

                }

                Log.w(

                    "KitsugiPlayerViewModel",

                    "fetchStreamsForEpisode: doğrudan URL 0 kaynak — aynı eklentinin kendi aramasına geçiliyor"

                )

            }



            // 2) Aynı eklentinin kendi başlık araması (sadece bu eklenti)

            if (scopedApis.isNotEmpty()) {

                val scopedTitles = (listOfNotNull(titleEnglish, titleRomaji, titleNative) + synonyms)

                    .map { it.trim() }

                    .filter { it.isNotBlank() && it != animeTitle }

                    .distinctBy { com.kitsugi.animelist.data.cloudstream.CsTitleMatcher.normalizeTitleForMatch(it) }

                val scopedStreams = mutableListOf<StreamSource>()

                for (api in scopedApis) {

                    try {

                        scopedStreams.addAll(

                            CsStreamRunner.getStreams(

                                api = api,

                                title = animeTitle,

                                alternativeTitles = scopedTitles,

                                year = startYear,

                                season = seasonNum,

                                episode = nextEp,

                                malId = malId,

                                aniListId = aniListId,

                                tmdbId = tmdbId,

                                isMovie = _isMovie.value,

                                mediaType = pluginMediaType

                            )

                        )

                    } catch (cancel: kotlinx.coroutines.CancellationException) {

                        throw cancel

                    } catch (e: Exception) {

                        Log.e("KitsugiPlayerViewModel", "fetchStreamsForEpisode: tek eklenti aramasi hata (${api.name})", e)

                    }

                }

                if (scopedStreams.isNotEmpty()) {

                    Log.d(

                        "KitsugiPlayerViewModel",

                        "fetchStreamsForEpisode: tek eklenti aramasi — ${scopedStreams.size} kaynak (diğer kaynaklar aranmadı)"

                    )

                    return@withContext StreamSorter.sort(scopedStreams)

                }

            }

            // Bu eklenti bölümü veremedi → oynatma tamamen durmasın diye ortak aramaya düşülür.

            Log.w(

                "KitsugiPlayerViewModel",

                "fetchStreamsForEpisode: ${localCs3ApiName} bölümü veremedi, ortak (tüm kaynaklar) aramaya düşülüyor"

            )

        }



        // 1. Stremio stream sources

        val stremioJob = async {

            try {

                repository.getStreamsForEpisode(

                    malId = malId,

                    aniListId = aniListId,

                    season = seasonNum,

                    episode = nextEp,

                    tmdbId = tmdbId,

                    isMovie = _isMovie.value,

                    preferredAddonName = preferredAddonName

                )

            } catch (e: Exception) {

                Log.e("KitsugiPlayerViewModel", "Error fetching Stremio streams", e)

                emptyList<StreamSource>()

            }

        }



        // 2. CS3 plugin stream sources

        val db = KitsugiDatabase.getDatabase(context)

        val enabledCsPlugins = try {

            db.csPluginDao().getEnabledPlugins()

        } catch (_: Exception) {

            emptyList()

        }



        // CloudStream's displayed addonName is the provider/API name (not necessarily the

        // containing extension package name), so load extensions but invoke only the exact

        // active provider during the fast-path lookup.

        val pluginsToQuery = enabledCsPlugins

        val alternativeTitles = (listOfNotNull(titleEnglish, titleRomaji, titleNative) + synonyms)

            .map { it.trim() }

            .filter { it.isNotBlank() && it != animeTitle }

            .distinctBy { com.kitsugi.animelist.data.cloudstream.CsTitleMatcher.normalizeTitleForMatch(it) }

        val csJobs = pluginsToQuery.map { plugin ->

            async {

                val csStreams = mutableListOf<StreamSource>()

                try {

                    val apis = CsPluginLoader.loadExtension(context, plugin.id)

                    val providersToQuery = if (preferredAddonName.isNullOrBlank()) {

                        apis

                    } else {

                        apis.filter { it.name.equals(preferredAddonName, ignoreCase = true) }

                    }

                    for (api in providersToQuery) {

                        val streams = CsStreamRunner.getStreams(

                            api = api,

                            title = animeTitle,

                            alternativeTitles = alternativeTitles,

                            year = startYear,

                            season = seasonNum,

                            episode = nextEp,

                            malId = malId,

                            aniListId = aniListId,

                            tmdbId = tmdbId,

                            isMovie = _isMovie.value

                        )

                        csStreams.addAll(streams.map { it.copy(addonId = com.kitsugi.animelist.data.local.WatchHistorySource.cloudStreamAddonId(plugin.id)) })

                    }

                } catch (cancel: CancellationException) {

                    throw cancel

                } catch (e: Throwable) {

                    Log.e("KitsugiPlayerViewModel", "Error fetching CS plugin ${plugin.name}", e)

                }

                csStreams

            }

        }



        val stremioStreams = stremioJob.await()

        val csStreams = csJobs.map { it.await() }.flatten()



        val combined = stremioStreams + csStreams

        StreamSorter.sort(combined)

    }



    private var episodeRequestGeneration = 0L



    fun playEpisode(targetEp: Int, activity: android.app.Activity?, onAlternativeRequired: () -> Unit, onResolutionFailed: () -> Unit) {

        val requestGeneration = ++episodeRequestGeneration

        userCancelledBinge = false

        _showBingeCardState.value = false

        // DEEP-015 fix: Önceki bölümden kalan countdown coroutine'ini öldür ve session sayacını temizle

        orchestrator.autoplay.resetSession()

        _nextEpisodeLoading.value = true

        _isLoading.value = true

        viewModelScope.launch {

            Log.d("KitsugiPlayerViewModel", "Fetching streams for episode: S${seasonNum}E${targetEp}")

            val streams = try {

                getStreamsForEpisode(targetEp)

            } catch (cancel: CancellationException) {

                throw cancel

            } catch (error: Exception) {

                Log.e("KitsugiPlayerViewModel", "playEpisode: stream fetch failed", error)

                emptyList()

            }

            if (requestGeneration != episodeRequestGeneration) return@launch

            if (streams.isNotEmpty()) {

                val sameProviderStream = streams.firstOrNull {

                    it.addonName.equals(_currentAddonName.value, ignoreCase = true)

                }



                val targetStream = sameProviderStream ?: streams.firstOrNull { !it.url.isNullOrBlank() } ?: streams.first()

                val repository = AddonStreamRepository(context)



                _isResolvingStream.value = true

                val resolvedUrl = try {

                    withContext(kotlinx.coroutines.Dispatchers.IO) {

                        repository.resolveStreamUrl(targetStream)

                    }

                } catch (e: kotlinx.coroutines.CancellationException) {

                    throw e

                } catch (e: Exception) {

                    Log.e("KitsugiPlayerViewModel", "playEpisode: akış çözümleme hatası", e)

                    null

                } finally {

                    isResolvingStream = false

                }



                if (resolvedUrl != null) {

                    if (requestGeneration != episodeRequestGeneration) return@launch

                    activeEngine?.let { engine ->
                        saveHistoryProgress(engine.currentPosition, engine.duration)
                    }
                    pendingSourceSwitchResumeMs = null
                    recordHistorySource(targetStream, resolvedUrl, targetEp)

                    _currentStreamSources.value = streams

                    orchestrator.errorRecovery.hasFallback = streams.size > 1

                    _currentEpisode.value = targetEp

                    _currentVideoUrl.value = resolvedUrl

                    _currentAudioUrl.value = targetStream.externalAudioUrl

                    _currentHeaders.value = targetStream.requestHeaders ?: emptyMap()

                    _currentSubtitles.value = targetStream.subtitles ?: emptyList()

                    _currentTitle.value = KitsugiStrings.get(R.string.title_bolum_episode, animeTitle, targetEp)

                    _currentSourceIndex.value = streams.indexOf(targetStream)

                    _currentAddonName.value = targetStream.addonName

                    // AniSkip: yeni bölüm için zaman damgalarını yeniden yükle

                    val skipMalId = malId

                    if (skipMalId != null) {

                        loadSkipIntervals(skipMalId, targetEp)

                    }

                    

                    fetchAutoSubtitles()

                    

                    // Reset position in prefs for the new url

                    val sharedPrefs = context.getSharedPreferences("MyWebViewPrefs", Context.MODE_PRIVATE)

                    sharedPrefs.edit().putLong("play_pos_" + resolvedUrl.hashCode(), 0L).apply()



                    val settings = SettingsDataStore(context).settingsFlow.first()

                    viewModelScope.launch {

                        runAfrPreflightIfEnabled(

                            context = context,

                            activity = activity,

                            url = resolvedUrl,

                            headers = _currentHeaders.value,

                            frameRateMatchingMode = settings.frameRateMatchingMode,

                            resolutionMatchingEnabled = settings.resolutionMatchingEnabled

                        )

                    }



                    _playbackSource.value = TrailerPlaybackSource(

                        videoUrl = resolvedUrl,

                        audioUrl = targetStream.externalAudioUrl,

                        externalAudioTracks = targetStream.externalAudioTracks,

                        streamTypeHint = targetStream.streamTypeHint

                    )

                    // Start warming the following episode from this newly selected provider.

                    prefetchNextEpisode()



                    if (sameProviderStream == null) {

                        onAlternativeRequired()

                    }

                } else {

                    onResolutionFailed()

                }

            } else {

                _nextEpisodeLoading.value = false

                // No streams found

            }

            _nextEpisodeLoading.value = false

            _isLoading.value = false

        }

    }



    fun playNextEpisode(activity: android.app.Activity?, onAlternativeRequired: () -> Unit, onResolutionFailed: () -> Unit) {

        playEpisode(_currentEpisode.value + 1, activity, onAlternativeRequired, onResolutionFailed)

    }



    /**

     * v2.4.258 / v2.4.303: Kaynak değişimi (dublaj ↔ orijinal, sunucu değişimi) artık KALDIĞI YERDEN devam eder.

     * Eskiden yeni URL 0. saniyeden başlıyordu; sitenin kendi oynatıcısındaki gibi sorunsuz geçiş için

     * mevcut konum [pendingSourceSwitchResumeMs] içinde saklanır ve yeni kaynak hazırlanırken alınır.

     */

    private var pendingSourceSwitchResumeMs: Long? = null



    fun takePendingSourceSwitchResume(): Long? {

        val value = pendingSourceSwitchResumeMs

        pendingSourceSwitchResumeMs = null

        return value

    }



    fun changeStreamSource(index: Int, stream: StreamSource, resolvedUrl: String) {

        PlayerLogger.logSourceChange(

            context   = context,

            fromAddon = _currentAddonName.value,

            toAddon   = stream.addonName,

            newUrl    = resolvedUrl,

            title     = _currentTitle.value

        )

        pendingSourceSwitchResumeMs = activeEngine?.currentPosition?.takeIf { it > 0L }
        activeEngine?.let { engine ->
            if (engine.currentPosition > 0L) saveHistoryProgress(engine.currentPosition, engine.duration)
        }
        recordHistorySource(stream, resolvedUrl, _currentEpisode.value)

        _currentVideoUrl.value = resolvedUrl

        _currentAudioUrl.value = stream.externalAudioUrl

        _currentHeaders.value = stream.requestHeaders ?: emptyMap()

        _currentSubtitles.value = stream.subtitles ?: emptyList()

        _currentSourceIndex.value = index

        _currentAddonName.value = stream.addonName

        _playbackSource.value = TrailerPlaybackSource(

            videoUrl = resolvedUrl,

            audioUrl = stream.externalAudioUrl,

            externalAudioTracks = stream.externalAudioTracks,

            streamTypeHint = stream.streamTypeHint

        )

        // Kaynak değişti: motor fallback zinciri ve retry sayacı yeni kaynak için sıfırlanır.

        // (Eskiden bu sıfırlama yapılmıyor, ilk kaynakta tükenen zincir yüzünden yeni kaynakta

        // MPV'ye geçiş hiç denenmiyordu.)

        orchestrator.errorRecovery.hasFallback = _currentStreamSources.value.size > 1

        orchestrator.errorRecovery.reset()

        fetchAutoSubtitles()

    }



    /**

     * Hata ekranındaki "Tekrar dene" için: kaynağı yeniden çözümler.

     *

     * Eskiden yalnızca aynı URL ile `playEpisode` çağrılıyordu; süresi dolmuş

     * imzalı/tek kullanımlık eklenti linkleri bu yüzden tekrar tekrar aynı hatayı

     * veriyordu. Burada URL yeniden üretilir; değişmezse motor aynı URL ile

     * mevcut konumdan yeniden hazırlanır.

     */

    fun retryCurrentSource() {

        val source = _currentStreamSources.value.getOrNull(_currentSourceIndex.value)

        if (source == null) {

            _hasError.value = true

            _errorDetails.value = "Kaynak bilgisi bulunamadı. Bölümü kapatıp yeniden açın."

            return

        }

        viewModelScope.launch {

            _isLoading.value = true

            _hasError.value = false

            _errorDetails.value = null

            try {

                val repository = AddonStreamRepository(context)

                val resolved = try {

                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {

                        repository.resolveStreamUrl(source)

                    }

                } catch (e: kotlinx.coroutines.CancellationException) {

                    throw e

                } catch (e: Exception) {

                    Log.e("KitsugiPlayerViewModel", "retryCurrentSource: çözümleme hatası: ${e.message}")

                    null

                }

                if (resolved.isNullOrBlank()) {

                    _hasError.value = true

                    _errorDetails.value = "Kaynak yeniden çözümlenemedi. Farklı bir kaynak seçin."

                    return@launch

                }

                val headers = source.requestHeaders ?: emptyMap()

                _currentHeaders.value = headers

                _currentSubtitles.value = source.subtitles ?: emptyList()

                _currentAddonName.value = source.addonName

                orchestrator.errorRecovery.hasFallback = _currentStreamSources.value.size > 1

                orchestrator.errorRecovery.reset()

                if (resolved != _currentVideoUrl.value) {

                    _currentAudioUrl.value = source.externalAudioUrl

                    _currentVideoUrl.value = resolved

                    _playbackSource.value = TrailerPlaybackSource(

                        videoUrl = resolved,

                        audioUrl = source.externalAudioUrl,

                        externalAudioTracks = source.externalAudioTracks,

                        streamTypeHint = source.streamTypeHint

                    )

                } else {

                    activeEngine?.prepare(

                        videoUrl = resolved,

                        audioUrl = source.externalAudioUrl,

                        externalAudioTracks = source.externalAudioTracks,

                        streamTypeHint = source.streamTypeHint,

                        headers = headers,

                        subtitles = _currentSubtitles.value,

                        startPositionMs = activeEngine?.currentPosition ?: 0L,

                        addonName = _currentAddonName.value,

                        isCS = source.isCS,

                        streamTitle = source.title,

                        qualityValue = source.qualityValue,

                        mediaType = source.mediaType,

                        csStreamType = source.csStreamType,

                        csExtractorData = source.csExtractorData

                    )

                }

                fetchAutoSubtitles()

            } catch (e: kotlinx.coroutines.CancellationException) {

                throw e

            } catch (e: Exception) {

                Log.e("KitsugiPlayerViewModel", "retryCurrentSource: yeniden hazırlama hatası", e)

                _hasError.value = true

                _errorDetails.value = "Kaynak yeniden hazırlanamadı: ${e.localizedMessage}"

            } finally {

                _isLoading.value = false

            }

        }

    }



    /**

     * Oynatma hatası (403, SSL, timeout vb.) sonrasında listedeki bir sonraki

     * kaynağa otomatik olarak geçer.

     *

     * Maksimum [maxAutoSwitchAttempts] deneme sonrası hasError = true yapılır.

     * Yeni bölüme geçildiğinde [resetAutoSwitch] ile sıfırlanmalıdır.

     *

     * @return true → kaynak değiştirildi, false → deneme limiti aşıldı / kaynak yok

     */

    fun tryNextSource(activity: android.app.Activity?, onSwitched: (newUrl: String, newSource: StreamSource) -> Unit): Boolean {

        val sources = _currentStreamSources.value

        if (sources.isEmpty() || autoSwitchAttempts >= maxAutoSwitchAttempts) {

            Log.w("KitsugiPlayerViewModel", "tryNextSource: limit aşıldı veya kaynak yok ($autoSwitchAttempts/$maxAutoSwitchAttempts)")

            return false

        }

        autoSwitchAttempts++

        val nextIndex = (_currentSourceIndex.value + 1) % sources.size

        if (nextIndex == _currentSourceIndex.value % sources.size && sources.size == 1) {

            Log.w("KitsugiPlayerViewModel", "tryNextSource: tek kaynak var, geçiş yapılamıyor")

            return false

        }

        Log.d("KitsugiPlayerViewModel", "tryNextSource: ${_currentSourceIndex.value} → $nextIndex (deneme $autoSwitchAttempts/$maxAutoSwitchAttempts)")

        _isAutoSwitching.value = true

        _isLoading.value = true

        viewModelScope.launch {

            try {

                val target = sources[nextIndex]

                val repository = AddonStreamRepository(context)



                // Consent & Debrid guard for automatic source switching

                val hasDebrid = !repository.getDebridApiKey().isNullOrBlank()

                val p2pAllowed = com.kitsugi.animelist.core.p2p.P2pSettingsRepository.isP2pEnabled() &&

                    com.kitsugi.animelist.core.p2p.P2pSettingsRepository.isConsentGranted()

                if (target.isTorrent && !hasDebrid && !p2pAllowed) {

                    Log.w("KitsugiPlayerViewModel", "tryNextSource: torrent kaynağı atlanıyor (rıza yok)")

                    _currentSourceIndex.value = nextIndex

                    tryNextSource(activity, onSwitched)

                    return@launch

                }



                val resolvedUrl = try {

                    withContext(kotlinx.coroutines.Dispatchers.IO) {

                        repository.resolveStreamUrl(target)

                    }

                } catch (e: kotlinx.coroutines.CancellationException) {

                    throw e

                } catch (e: Exception) {

                    Log.e("KitsugiPlayerViewModel", "tryNextSource: çözümleme hatası: ${e.message}")

                    null

                }

                if (resolvedUrl != null) {

                    pendingSourceSwitchResumeMs = activeEngine?.currentPosition?.takeIf { it > 0L }
                    activeEngine?.let { engine ->
                        if (engine.currentPosition > 0L) saveHistoryProgress(engine.currentPosition, engine.duration)
                    }
                    recordHistorySource(target, resolvedUrl, _currentEpisode.value)

                    _currentVideoUrl.value = resolvedUrl

                    _currentAudioUrl.value = target.externalAudioUrl

                    _currentHeaders.value = target.requestHeaders ?: emptyMap()

                    _currentSubtitles.value = target.subtitles ?: emptyList()

                    _currentSourceIndex.value = nextIndex

                    _currentAddonName.value = target.addonName

                    _hasError.value = false

                    _errorDetails.value = null

                    // Keep hasFallback updated: still > 1 source means we can keep trying

                    orchestrator.errorRecovery.hasFallback = sources.size > 1



                    val settings = SettingsDataStore(context).settingsFlow.first()

                    viewModelScope.launch {

                        runAfrPreflightIfEnabled(

                            context = context,

                            activity = activity,

                            url = resolvedUrl,

                            headers = _currentHeaders.value,

                            frameRateMatchingMode = settings.frameRateMatchingMode,

                            resolutionMatchingEnabled = settings.resolutionMatchingEnabled

                        )

                    }



                    _playbackSource.value = TrailerPlaybackSource(

                        videoUrl = resolvedUrl,

                        audioUrl = target.externalAudioUrl,

                        externalAudioTracks = target.externalAudioTracks,

                        streamTypeHint = target.streamTypeHint

                    )

                    // Yeni kaynak = yeni fallback oturumu.

                    orchestrator.errorRecovery.reset()

                    onSwitched(resolvedUrl, target)

                    Log.d("KitsugiPlayerViewModel", "tryNextSource: '${target.addonName}' kaynağına geçildi")

                } else {

                    Log.w("KitsugiPlayerViewModel", "tryNextSource: kaynak çözümlenemedi, tekrar deneniyor...")

                    // Recursively try the one after

                    _currentSourceIndex.value = nextIndex

                    tryNextSource(activity, onSwitched)

                }

            } catch (e: CancellationException) {

                throw e

            } catch (e: Exception) {

                Log.e("KitsugiPlayerViewModel", "tryNextSource hata: ${e.message}")

            } finally {

                _isAutoSwitching.value = false

                _isLoading.value = false

            }

        }

        return true

    }



    /** Yeni bölüm oynatılmaya başlandığında otomatik geçiş sayacını sıfırla. */

    fun resetAutoSwitch() {

        autoSwitchAttempts = 0

        _isAutoSwitching.value = false

    }



    // Keep track of binge state UI

    private val _showBingeCardState = MutableStateFlow(false)

    val showBingeCardState: StateFlow<Boolean> = _showBingeCardState.asStateFlow()



    private val _bingeCountdownSec = MutableStateFlow(10)

    val bingeCountdownSec: StateFlow<Int> = _bingeCountdownSec.asStateFlow()



    /**

     * Scrobble oturumu bölüm başına BİR kez başlatılır.

     *

     * Eskiden hem [setActiveEngine] hem de `onSourceReady` çağrılıyordu. Motor geri dönüşü

     * (MEDIA3→MPV) veya kaynak değişimi her seferinde `onEpisodeStarted` çağırıp

     * `scrobbled=false` yapıyor, %80'de bir kez yapılması gereken AniList güncellemesi

     * aynı bölüm için tekrar tekrar tetiklenebiliyordu.

     */

    private var scrobbleSessionKey: String? = null



    private fun startScrobbleSessionIfNeeded(engine: com.kitsugi.animelist.core.player.engine.PlayerEngine?) {

        val episode = _currentEpisode.value

        val mediaKey = malId ?: aniListId ?: animeTitle.hashCode()

        val key = "$mediaKey:$episode"

        if (key == scrobbleSessionKey) return

        scrobbleSessionKey = key

        viewModelScope.launch {

            val db = KitsugiDatabase.getDatabase(context)

            val entryEntity = malId?.let { db.mediaEntryDao().getByMalId(it) }

                ?: aniListId?.let { db.mediaEntryDao().getById(it) }

            val mediaEntry = entryEntity?.toDomain() ?: com.kitsugi.animelist.model.MediaEntry(

                id = aniListId ?: 0,

                title = animeTitle,

                subtitle = "",

                type = com.kitsugi.animelist.model.MediaType.Anime,

                status = com.kitsugi.animelist.model.WatchStatus.Watching,

                score = null,

                progress = episode,

                total = null,

                malId = malId,

                titleEnglish = titleEnglish

            )

            orchestrator.scrobble.onEpisodeStarted(mediaEntry, episode, engine?.duration ?: 0L)

        }

    }



    fun setActiveEngine(engine: com.kitsugi.animelist.core.player.engine.PlayerEngine?) {

        this.activeEngine = engine

        if (engine != null) {

            _audioChannels.value = appSettings.value.audioChannels

            _statisticsPage.value = appSettings.value.playerStatisticsPage

            startScrobbleSessionIfNeeded(engine)

        }

    }



    private var activeEngine: com.kitsugi.animelist.core.player.engine.PlayerEngine? = null



    // --- Player Runtime Orchestrator ---

    val orchestrator: com.kitsugi.animelist.ui.screens.fullscreen.runtime.PlayerRuntimeOrchestrator = com.kitsugi.animelist.ui.screens.fullscreen.runtime.PlayerRuntimeOrchestrator(

        scope = viewModelScope,

        context = context,

        getAniListToken = { com.kitsugi.animelist.data.auth.ExternalAuthManager.getAniListToken(context) },

        onRetry = { attempt ->

            viewModelScope.launch {

                activeEngine?.let { engine ->

                    _currentVideoUrl.value?.let { url ->

                        runCatching {

                            val currentSource = _currentStreamSources.value.getOrNull(_currentSourceIndex.value)

                            engine.prepare(

                                videoUrl = url,

                                audioUrl = _currentAudioUrl.value,

                                externalAudioTracks = currentSource?.externalAudioTracks.orEmpty(),

                                streamTypeHint = currentSource?.streamTypeHint,

                                headers = _currentHeaders.value,

                                subtitles = _currentSubtitles.value,

                                startPositionMs = engine.currentPosition,

                                addonName = _currentAddonName.value,

                                isCS = currentSource?.isCS == true,

                                streamTitle = currentSource?.title ?: currentSource?.name,

                                qualityValue = currentSource?.qualityValue,

                                mediaType = currentSource?.mediaType,

                                csStreamType = currentSource?.csStreamType,

                                csExtractorData = currentSource?.csExtractorData

                            )

                        }.onFailure {

                            Log.e("KitsugiPlayerViewModel", "onRetry prepare hatası (deneme $attempt)", it)

                        }

                    }

                }

            }

        },

        onFallback = {

            tryNextSource(activity = null) { _, _ -> }

        },

                onFatal = { errorCode, errorMsg ->

            _hasError.value = true

            val enriched = enrichPlayerError(errorCode, errorMsg, _currentVideoUrl.value, _currentAddonName.value)

            _errorDetails.value = buildString {

                append(KitsugiStrings.get(R.string.oynatma_hatasi))

                append(": " + enriched.display + "\n\n")

                append(enriched.details)

                append("\n\nTum kaynaklar ve oynaticilar denendi. Farkli bir kaynak secebilir veya harici oynaticida acabilirsiniz.")

            }

            try {

                com.kitsugi.animelist.data.cloudstream.diag.CsTrace.error(

                    _currentAddonName.value ?: "Player", "fatal",

                    "Fatal code=" + errorCode.toString() + " msg=" + errorMsg.take(200) + " url=" + (_currentVideoUrl.value?.take(100) ?: "-"), null

                )

            } catch (_: Exception) {}

        },

        onAutoPlayNext = {

            playNextEpisode(activity = null, onAlternativeRequired = {}, onResolutionFailed = {})

        },

        onLoop = {

            activeEngine?.let {

                it.seekTo(0L)

                it.play()

            }

        },

        onShowStillWatching = {

            // AutoplayController "still watching" kararı verdiğinde controller'ın prompt'unu

            // gerçekten aç. Eskiden burada yalnızca yorum vardı; UI hiçbir zaman uyarı

            // göstermiyor, oturum limiti sessizce yok sayılıyordu.

            viewModelScope.launch {

                val settings = SettingsDataStore(context).settingsFlow.first()

                orchestrator.stillWatching.applySettings(settings)

                orchestrator.stillWatching.showPromptNow()

            }

        },

        onStillWatchingTimeout = {

            activeEngine?.pause()

        },

        onShowEndPrompt = {

            activeEngine?.pause()

            _showBingeCardState.value = true

        },

        onCountdownTick = { remaining ->

            _bingeCountdownSec.value = remaining

        },

        onSwitchEngine = { to ->

            onEngineSwitched(to)

        },

        getCurrentEngine = { _activeEngineType.value },

        // MPV dahili motoru paketle gelir; hata kurtarmada her zaman ikinci dahili motor olarak kullanılabilir.

        isMpvEnabled = { true }

    )



    // --- Player Skip Settings (Intro/Outro Atlama) ---

    val skipIntervals: StateFlow<List<SkipInterval>> get() = orchestrator.skip.skipIntervals

    val aniSkipLoading: StateFlow<Boolean> get() = orchestrator.skip.isLoading

    val aniSkipAutoSkip: StateFlow<Boolean> get() = orchestrator.skip.autoSkipEnabled

    val aniSkipEnabled: StateFlow<Boolean> get() = orchestrator.skip.aniSkipEnabled

    val animeSkipClientId: StateFlow<String> get() = orchestrator.skip.animeSkipClientId



    // ─── T1.3 – AudioOutputRouteDetector ────────────────────────────────────

    val activeAudioRoute: StateFlow<AudioRoute> get() = orchestrator.audio.activeAudioRoute



    fun startRouteObserver(getActiveEngine: () -> com.kitsugi.animelist.core.player.engine.PlayerEngine?) {

        orchestrator.audio.startObserving(getActiveEngine)

    }



    fun stopRouteObserver() {

        orchestrator.audio.stopObserving()

    }



    fun onPositionChanged(positionMs: Long, durationMs: Long, isPlaying: Boolean) {

        _pos.value = positionMs

        _duration.value = durationMs

        updateChapter(_chapters.value.lastOrNull { it.start <= positionMs / 1000f } ?: _chapters.value.firstOrNull())

        orchestrator.scrobble.onPositionUpdate(positionMs, durationMs)

        orchestrator.stillWatching.onPlaybackTick(positionMs, isPlaying)

    }



    // ── Engine-delegate playback controls ─────────────────────────────────────

    fun play() {

        activeEngine?.play()

        _paused.value = false

    }



    fun pause() {

        activeEngine?.pause()

        _paused.value = true

    }



    fun togglePlay() {

        if (activeEngine?.isPlaying == true) pause() else play()

    }



    fun seekTo(positionMs: Long) {

        activeEngine?.seekTo(positionMs)

        _pos.value = positionMs

    }



    fun setPlaybackSpeed(speed: Float, persist: Boolean = true) {

        val safe = speed.takeIf { it.isFinite() }?.coerceIn(0.1f, 6f) ?: 1f

        transientPlaybackSpeed = if (persist) null else safe

        _playbackSpeed.value = safe

        activeEngine?.setPlaybackSpeed(safe)

        if (persist) viewModelScope.launch { dataStore.setPlayerSpeed(safe) }

    }



    fun setAutoPlay(enabled: Boolean) {

        viewModelScope.launch { dataStore.setAutoplayEnabled(enabled) }

    }



    fun setAspectMode(mode: com.kitsugi.animelist.core.player.PlayerAspectMode) {

        viewModelScope.launch { dataStore.setAspectMode(mode.name) }

    }



    // ── Episode navigation helpers ─────────────────────────────────────────────

    val hasPreviousEpisode: StateFlow<Boolean> = _currentEpisode

        .map { it > 1 }

        .stateIn(viewModelScope, SharingStarted.Eagerly, false)



    val hasNextEpisode: StateFlow<Boolean> = combine(_episodesList, _currentEpisode, _isMovie) { episodes, current, isMovie ->

        !isMovie && (

            episodes.any { it.episodeNumber == current + 1 } ||

                current < (episodes.lastOrNull()?.episodeNumber ?: Int.MAX_VALUE)

            )

    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)



    fun onEpisodeEnded(durationMs: Long, positionMs: Long) {
        val endedEpisode = _currentEpisode.value
        val historyId = historyAnimeId
        if (historyId != null && historyEngineEpisode != endedEpisode) return
        if (historyId != null && durationMs > 0L) {
            com.kitsugi.animelist.data.local.WatchHistoryManager.updateProgress(
                animeId = historyId, season = seasonNum, episode = endedEpisode,
                positionMs = durationMs, durationMs = durationMs
            )
            com.kitsugi.animelist.data.local.WatchHistoryManager.flush()
        }

        viewModelScope.launch {

            try {
                if (historyId == null) {
                    historyRepository.deleteProgress(getResolveMediaId(), endedEpisode)
                }

            } catch (e: Exception) {

                Log.e("KitsugiPlayerViewModel", "Error deleting progress on episode ended", e)

            }



            val settings = SettingsDataStore(context).settingsFlow.first()

            val hasNext = !_isMovie.value && (

                _episodesList.value.any { it.episodeNumber == _currentEpisode.value + 1 } ||

                    _currentEpisode.value < (_episodesList.value.lastOrNull()?.episodeNumber ?: Int.MAX_VALUE)

                )

            val hasOutro = skipIntervals.value.any { it.type == "outro" }

            val outroStart = skipIntervals.value.find { it.type == "outro" }?.startTime?.toLong()



            orchestrator.autoplay.onEpisodeEnded(

                postPlayMode = PostPlayMode.fromString(settings.postPlayMode),

                isAutoplaySettingEnabled = settings.isAutoplayEnabled,

                hasNextEpisode = hasNext,

                durationMs = durationMs,

                positionMs = positionMs,

                hasOutroSkip = hasOutro,

                outroStartSec = outroStart

            )

        }

    }



    init {

        viewModelScope.launch {

            val settings = SettingsDataStore(context).settingsFlow.first()

            orchestrator.start(

                getActiveEngine = { activeEngine },

                liveHelperEnabled = settings.liveHelperEnabled

            )

        }

    }



    override fun onCleared() {

        super.onCleared()

        activeEngine?.let { engine ->
            val pos = engine.currentPosition
            val dur = engine.duration
            if (pos > 0L) {
                saveHistoryProgress(pos, dur)
            }
        }
        com.kitsugi.animelist.data.local.WatchHistoryManager.flush()

        orchestrator.stop()

    }



    fun updateSkipSettings(enabled: Boolean, autoSkip: Boolean, clientId: String) {

        orchestrator.skip.updateSettings(enabled, autoSkip, clientId, malId, _currentEpisode.value)

    }



    fun loadSkipIntervals(targetMalId: Int, targetEpisode: Int) {

        orchestrator.skip.loadIntervals(

            malId = targetMalId,

            episode = targetEpisode,

            hasChapters = mediaChapters.isNotEmpty(),

            disableOnChapters = appSettings.value.aniSkipDisableOnChapters

        )

    }



    private var subtitleSearchJob: kotlinx.coroutines.Job? = null

    fun fetchAutoSubtitles() {

        val currentMalId = malId

        val currentAniList = aniListId

        val currentEp = _currentEpisode.value

        val currentS = seasonNum

        val isMovieType = _isMovie.value || (currentS == 0 && currentEp <= 1)

        subtitleSearchJob?.cancel()
        subtitleSearchJob = viewModelScope.launch {
            _subtitleSearchStatus.value = SubtitleSearchStatus.LOADING
            try {
                // Stable IDs used by cards are not necessarily MAL IDs. Decode the
                // same namespaces as the stream search before resolving subtitles.
                val ids = com.kitsugi.animelist.data.repository.subtitleResolverIds(currentMalId, currentAniList)
                val resolvedIds = try {
                    com.kitsugi.animelist.data.remote.KitsugiIdResolver.resolveIds(
                        malId = ids.malId, aniListId = ids.aniListId, tmdbId = tmdbId,
                        kitsuId = ids.kitsuId, bangumiId = bangumiId,
                        mediaType = when {
                            isMovieType -> com.kitsugi.animelist.model.MediaType.Movie
                            ids.malId != null || ids.aniListId != null || ids.kitsuId != null || bangumiId != null -> com.kitsugi.animelist.model.MediaType.Anime
                            else -> com.kitsugi.animelist.model.MediaType.TvShow
                        },
                        wikidataImdbFallback = true
                    )
                } catch (cancel: kotlinx.coroutines.CancellationException) {
                    throw cancel
                } catch (e: Exception) {
                    Log.w("KitsugiPlayerViewModel", "Subtitle ID resolution failed; searching known IDs and titles", e)
                    com.kitsugi.animelist.data.remote.ResolvedIds()
                }
                var imdbId = knownImdbId ?: resolvedIds.imdbId
                var kitsuId = resolvedIds.kitsuId ?: ids.kitsuId
                val resolvedMovieType = isMovieType
                val titles = listOfNotNull(animeTitle, titleEnglish, titleRomaji, titleNative) + synonyms
                // A title-only route has no cross IDs. Accept a TMDB fallback only
                // for an exact normalized title and compatible type/year; never
                // silently use the first unrelated search result.
                if (imdbId == null && kitsuId == null && titles.any { it.isNotBlank() }) {
                    try {
                        val normalize = com.kitsugi.animelist.data.cloudstream.CsTitleMatcher::normalizeTitleForMatch
                        val names = titles.map(normalize).filter { it.isNotBlank() }.toSet()
                        val expectedType = if (isMovieType) com.kitsugi.animelist.model.MediaType.Movie else com.kitsugi.animelist.model.MediaType.TvShow
                        val match = com.kitsugi.animelist.data.remote.TmdbApiClient().search(animeTitle).firstOrNull {
                            it.type == expectedType && normalize(it.title) in names &&
                                (startYear == null || it.year == null || kotlin.math.abs(it.year - startYear!!) <= 1)
                        }
                        if (match?.tmdbId != null) {
                            val fallback = com.kitsugi.animelist.data.remote.KitsugiIdResolver.resolveIds(
                                malId = null, aniListId = null, tmdbId = match.tmdbId,
                                mediaType = expectedType, wikidataImdbFallback = true
                            )
                            imdbId = fallback.imdbId
                            kitsuId = fallback.kitsuId
                        }
                    } catch (cancel: kotlinx.coroutines.CancellationException) {
                        throw cancel
                    } catch (e: Exception) {
                        Log.w("KitsugiPlayerViewModel", "Subtitle title lookup failed; using title queries", e)
                    }
                }
                val queries = com.kitsugi.animelist.data.repository.buildSubtitleQueries(
                    imdbId, kitsuId, titles, resolvedMovieType, currentS, currentEp
                )

                if (queries.isEmpty()) {
                    Log.w("KitsugiPlayerViewModel", "Altyazı atlandı: ID ve başlık çözümlenemedi (malId=$currentMalId, aniListId=$currentAniList).")
                    _subtitleSearchStatus.value = SubtitleSearchStatus.EMPTY
                    return@launch
                }

                val selectedSource = _currentStreamSources.value.getOrNull(_currentSourceIndex.value)
                val guessedFilename = selectedSource?.title?.takeIf { it.isNotBlank() }
                    ?: _currentVideoUrl.value?.let { url ->
                        try {
                            val lastSeg = android.net.Uri.parse(url).lastPathSegment
                            if (!lastSeg.isNullOrBlank() && lastSeg.contains(".")) lastSeg else null
                        } catch (_: Exception) { null }
                    }
                val cleanedFilename = guessedFilename?.substringBefore("\n")?.substringBefore("\r")?.trim()
                val mediaTitle = animeTitle.trim()
                val isTitleInFilename = !cleanedFilename.isNullOrBlank() && mediaTitle.isNotBlank() && cleanedFilename.lowercase().contains(mediaTitle.lowercase())
                val finalFilename = if (isTitleInFilename) {
                    cleanedFilename
                } else if (mediaTitle.isNotBlank()) {
                    val suffix = if (resolvedMovieType) "" else " - S${currentS.toString().padStart(2, '0')}E${currentEp.toString().padStart(2, '0')}"
                    "$mediaTitle$suffix.mkv"
                } else {
                    cleanedFilename
                }

                Log.d("KitsugiPlayerViewModel", "Fetching subtitles for queries: $queries, filename=$finalFilename (original=$cleanedFilename)")

                val subRepo = com.kitsugi.animelist.data.repository.SubtitleRepositoryImpl(context)
                // Tüm query'ler için paralel sorgu — IMDB + kitsu + title hepsi aynı anda
                val remoteSubs = kotlinx.coroutines.coroutineScope {
                    val videoUrl = _currentVideoUrl.value
                    val videoHeaders = _currentHeaders.value
                    val hash = async(kotlinx.coroutines.Dispatchers.IO) {
                        videoUrl?.let { com.kitsugi.animelist.core.player.OpenSubtitlesHasher.compute(it, videoHeaders) }
                    }
                    queries.map { query ->
                        async(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                subRepo.getSubtitles(
                                    type = query.type,
                                    id = query.id,
                                    videoUrl = _currentVideoUrl.value,
                                    videoHeaders = _currentHeaders.value,
                                    filename = finalFilename,
                                    aniListId = resolvedIds.aniListId ?: ids.aniListId,
                                    animeTitle = query.title ?: animeTitle,
                                    episode = if (resolvedMovieType) null else currentEp,
                                    season = if (resolvedMovieType) null else currentS,
                                    precomputedHash = hash.await(),
                                    hashAlreadyComputed = true
                                )
                            } catch (cancel: kotlinx.coroutines.CancellationException) {
                                throw cancel
                            } catch (e: Exception) {
                                Log.e("KitsugiPlayerViewModel", "Failed to fetch subtitles for queryId=${query.id} type=${query.type}", e)
                                emptyList()
                            }
                        }
                    }.awaitAll().flatten().distinctBy { it.url }
                }.toMutableList()



                    val settings = SettingsDataStore(context).settingsFlow.first()

                    val preferredLangs = settings.preferredSubtitleLanguages.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }

                    val effectivePreferredLangs = if (preferredLangs.any { com.kitsugi.animelist.core.player.PlayerSubtitleUtils.matchesLanguageCode(it, "tr") }) {

                        preferredLangs

                    } else {

                        listOf("tr") + preferredLangs

                    }

                    val startupMode = settings.addonSubtitleStartupMode



                    val filteredSubs = if (startupMode == "PREFERRED_ONLY") {

                        remoteSubs.filter { sub ->

                            effectivePreferredLangs.any { pref -> com.kitsugi.animelist.core.player.PlayerSubtitleUtils.matchesLanguageCode(sub.lang, pref) }

                        }

                    } else {

                        remoteSubs

                    }





                    val processedSubs = kotlinx.coroutines.coroutineScope {

                        filteredSubs.map { sub ->

                            async(kotlinx.coroutines.Dispatchers.IO) {

                                val localFile = com.kitsugi.animelist.core.player.SubtitleFileCache.cacheSubtitle(context, sub.url)

                                if (localFile != null) {

                                    val friendlyLangName = com.kitsugi.animelist.core.player.PlayerSubtitleUtils.getFriendlyLanguageName(sub.lang)

                                    SubtitleInput(

                                        url = localFile.absolutePath,

                                        name = "$friendlyLangName (${sub.addonName})",

                                        lang = sub.lang,

                                        // Addon'dan gelen altyazılar harici olarak işaretlenir.

                                        // Oynatıcı motorları dahili (stream'e gömülü) altyazıları

                                        // bu harici altyazılara göre önceliklendirir.

                                        isExternal = true

                                    )

                                } else {

                                    Log.w("KitsugiPlayerViewModel", "Altyazı indirilemedi: ${sub.url}")

                                    null

                                }

                            }

                        }.awaitAll().filterNotNull()

                    }





                    if (processedSubs.isNotEmpty()) {

                        val merged = (_currentSubtitles.value + processedSubs).distinctBy { it.url }

                        val sorted = com.kitsugi.animelist.core.player.PlayerSubtitleUtils.sortSubtitlesByPreference(

                            merged,

                            effectivePreferredLangs

                        )

                        _currentSubtitles.value = sorted

                        Log.d("KitsugiPlayerViewModel", "Altyaz\u0131lar y\u00fcklendi: toplam=${sorted.size} (${processedSubs.size} yeni)")

                        _subtitleSearchStatus.value = SubtitleSearchStatus.SUCCESS

                    } else {

                        Log.d("KitsugiPlayerViewModel", "Altyaz\u0131 bulunamad\u0131 veya i\u015flenemedi.")

                        _subtitleSearchStatus.value = SubtitleSearchStatus.EMPTY

                        _subtitleEvents.emit(SubtitleEvent.LoadFailed)

                    }

            } catch (e: Exception) {

                Log.e("KitsugiPlayerViewModel", "Failed to fetch auto subtitles", e)

                _subtitleSearchStatus.value = SubtitleSearchStatus.EMPTY

                _subtitleEvents.emit(SubtitleEvent.LoadFailed)

            }

        }

    }



    // ─── T1.7 – StillWatching + PostPlayMode + AutoplaySessionRules ──────────



    /** StillWatching prompt overlay'inin görünür olup olmadığı */

    val showStillWatchingPrompt: Boolean get() = orchestrator.stillWatching.showPrompt



    /** StillWatching geri sayımda kalan saniye */

    val stillWatchingCountdownSec: Int? get() = orchestrator.stillWatching.countdownSec



    /**

     * Oynatma tick'i — her ~ 1 saniyede bir screen tarafından çağrılır.

     * StillWatching threshold dolmuşsa prompt gösterir.

     */

    fun onPlaybackTick(positionMs: Long, isPlaying: Boolean) {

        orchestrator.stillWatching.onPlaybackTick(positionMs, isPlaying)

    }



    /**

     * Kullanıcı "Evet, hâlâ izliyorum" dedi.

     * Prompt'u kapat, session sayacını sıfırla, oynatmaya devam et.

     */

    fun onStillWatchingConfirmed() {

        orchestrator.stillWatching.onConfirmed()

        // Geri sayım bittiğinde oynatma duraklatılmıştı; kullanıcı onaylayınca devam et.

        activeEngine?.play()

    }



    /**

     * Kullanıcı "Hayır, duraksın" dedi.

     * Prompt'u kapat, oynatmayı duraklat signal'i için state bırak.

     */

    fun onStillWatchingDismissed() {

        orchestrator.stillWatching.onDismissed()

        activeEngine?.pause()

    }



    /**

     * Bir bölüm tamamlandığında çağrılır.

     * AutoplaySessionRules kontrol edilir.

     */

    fun onEpisodeCompleted(settings: com.kitsugi.animelist.data.settings.AppSettings): Boolean {

        return orchestrator.stillWatching.onEpisodeCompleted(settings)

    }



    /**

     * Kullanıcı herhangi bir girişimde bulundu (dokunma, seek, vb.)

     * StillWatching sayacını sıfırlar.

     */

    fun onUserInteraction() {

        orchestrator.stillWatching.onUserInteraction()

    }



    /**

     * Settings değiştiğinde StillWatching config'ini yenile.

     */

    fun applyStillWatchingSettings(settings: com.kitsugi.animelist.data.settings.AppSettings) {

        orchestrator.stillWatching.applySettings(settings)

    }



    private fun getResolveMediaId(): Int {

        return malId ?: aniListId ?: animeTitle.hashCode()

    }



    suspend fun getSavedPosition(mediaId: Int, episode: Int, addonName: String? = null): Long {

        if (!appSettings.value.preserveWatchingPosition) return 0L

        return try {

            val progress = historyRepository.getProgress(mediaId, episode)

            if (progress != null && (addonName == null || progress.addonName == addonName)) {

                progress.lastPositionMs

            } else {

                0L

            }

        } catch (e: Exception) {

            Log.e("KitsugiPlayerViewModel", "Error getting saved position from DB", e)

            0L

        }

    }



    fun onHistoryEnginePrepared(episode: Int) {
        historyEngineEpisode = episode
    }

    fun saveHistoryProgress(positionMs: Long, durationMs: Long) {
        if (historyEngineEpisode != _currentEpisode.value || positionMs < 0L) return
        historyAnimeId?.let { id ->
            com.kitsugi.animelist.data.local.WatchHistoryManager.updateProgress(
                animeId = id,
                season = seasonNum,
                episode = _currentEpisode.value,
                positionMs = positionMs,
                durationMs = durationMs
            )
        }
    }

    private fun recordHistorySource(source: StreamSource, resolvedUrl: String, episode: Int) {
        historyAnimeId?.let { id ->
            runCatching {
                com.kitsugi.animelist.data.local.WatchHistoryManager.recordSource(
                    animeId = id, season = seasonNum, previousEpisode = _currentEpisode.value,
                    episode = episode, source = source, resolvedUrl = resolvedUrl
                )
            }.onFailure { Log.w("KitsugiPlayerViewModel", "Could not update history source", it) }
        }
        val nextPluginId = com.kitsugi.animelist.data.local.WatchHistorySource.cloudStreamPluginId(source.addonId)
        val nextApi = source.cs3ApiName ?: source.addonName
        if (source.isCS && !source.cs3PageUrl.isNullOrBlank()) {
            cs3Url = source.cs3PageUrl
        }
        cs3PluginId = nextPluginId.takeIf { source.isCS }
        cs3ApiName = nextApi.takeIf { source.isCS }
    }

    fun saveProgress(

        mediaId: Int,

        episode: Int,

        lastPositionMs: Long,

        durationMs: Long,

        addonName: String? = null

    ) {

        if (!appSettings.value.preserveWatchingPosition) return

        if (lastPositionMs <= 0L || durationMs <= 0L) return

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {

            try {

                historyRepository.saveProgress(

                    mediaId = mediaId,

                    episode = episode,

                    lastPositionMs = lastPositionMs,

                    durationMs = durationMs,

                    addonName = addonName

                )

            } catch (e: Exception) {

                Log.e("KitsugiPlayerViewModel", "Error saving progress to DB", e)

            }

        }

    }



    /**

     * Harici video oynatıcıdan (MX Player, VLC, mpv-android, Just Player vb.) dönüldüğünde

     * izleme konumunu ve tamamlanma durumunu iç geçmişe ve scrobble denetleyicisine eşitler.

     */

    fun onExternalPlayerResult(positionMs: Long, durationMs: Long?, endedByUser: Boolean) {

        val effectiveDuration = (durationMs?.takeIf { it > 0L } ?: _duration.value).coerceAtLeast(positionMs)

        if (positionMs >= 0L) {

            seekTo(positionMs)

            if (historyAnimeId != null) {
                saveHistoryProgress(positionMs, effectiveDuration)
                com.kitsugi.animelist.data.local.WatchHistoryManager.flush()
            } else if (effectiveDuration > 0L) {

                val mediaId = getResolveMediaId()

                val ep = _currentEpisode.value

                saveProgress(

                    mediaId = mediaId,

                    episode = ep,

                    lastPositionMs = positionMs,

                    durationMs = effectiveDuration,

                    addonName = _currentAddonName.value

                )

                com.kitsugi.animelist.data.local.WatchHistoryManager.updateProgress(

                    animeId = mediaId.toString(),

                    episode = ep,

                    positionMs = positionMs,

                    durationMs = effectiveDuration

                )

            }
            if (effectiveDuration > 0L) {
                orchestrator.scrobble.onPositionUpdate(positionMs, effectiveDuration)
            }

        }

        if (!endedByUser && effectiveDuration > 0L) {

            onEpisodeEnded(effectiveDuration, effectiveDuration)

        }

    }



    // ─── Screenshot and Art Management ──────────────────────────────────────────



    private val _screenshotShowSubtitles = MutableStateFlow(true)

    val screenshotShowSubtitles: StateFlow<Boolean> = _screenshotShowSubtitles.asStateFlow()



    fun toggleScreenshotShowSubtitles(show: Boolean) {

        _screenshotShowSubtitles.value = show

        viewModelScope.launch { dataStore.setScreenshotSubtitles(show) }

    }



    val hasSubTracks: StateFlow<Boolean> = _currentSubtitles.map { it.isNotEmpty() }

        .stateIn(viewModelScope, SharingStarted.Eagerly, false)



    val isLocalSource: Boolean

        get() = _currentVideoUrl.value?.let { !it.startsWith("http://") && !it.startsWith("https://") } ?: true



    suspend fun takeScreenshot(cachePath: String, showSubtitles: Boolean): java.io.InputStream? {

        return activeEngine?.takeScreenshot(cachePath, showSubtitles)

    }



    fun saveImage(imageStream: () -> java.io.InputStream, timePos: Int?) {

        viewModelScope.launch {

            try {

                val filename = withContext(kotlinx.coroutines.Dispatchers.IO) {

                    val timeStr = timePos?.let { formatMs(it.toLong()) } ?: System.currentTimeMillis().toString()

                    val safeTitle = animeTitle.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").take(100)

                    val name = "Kitsugi_${safeTitle}_${timeStr.replace(":", "-")}.png"

                    val resolver = context.contentResolver

                    val values = android.content.ContentValues().apply {

                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)

                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")

                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {

                            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/Kitsugi")

                            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)

                        }

                    }

                    val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

                        ?: error("MediaStore insert failed")

                    try {

                        val output = resolver.openOutputStream(uri) ?: error("MediaStore stream unavailable")

                        output.use { out -> imageStream().use { it.copyTo(out) } }

                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {

                            resolver.update(uri, android.content.ContentValues().apply {

                                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)

                            }, null, null)

                        }

                    } catch (error: Exception) {

                        resolver.delete(uri, null, null)

                        throw error

                    }

                    name

                }

                Toast.makeText(context, KitsugiStrings.get(R.string.ekran_goruntusu_kaydedildi_x, filename), Toast.LENGTH_LONG).show()

            } catch (e: kotlinx.coroutines.CancellationException) {

                throw e

            } catch (e: Exception) {

                Log.e("PlayerViewModel", "saveImage failed", e)

                Toast.makeText(context, KitsugiStrings.get(R.string.kaydetme_basarisiz_x, e.localizedMessage), Toast.LENGTH_SHORT).show()

            }

        }

    }



    fun shareImage(imageStream: () -> java.io.InputStream, timePos: Int?) {

        viewModelScope.launch {

            try {

                val uri = withContext(kotlinx.coroutines.Dispatchers.IO) {

                    val dir = java.io.File(context.cacheDir, "shared_images").apply { mkdirs() }

                    val file = java.io.File.createTempFile("screenshot_${timePos ?: 0}_", ".png", dir)

                    try {

                        file.outputStream().use { out -> imageStream().use { it.copyTo(out) } }

                        androidx.core.content.FileProvider.getUriForFile(context, "com.kitsugi.animelist.fileprovider", file)

                    } catch (error: Exception) {

                        file.delete()

                        throw error

                    }

                }

                val intent = Intent(Intent.ACTION_SEND).apply {

                    type = "image/png"

                    putExtra(Intent.EXTRA_STREAM, uri)

                    clipData = android.content.ClipData.newRawUri("Screenshot", uri)

                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                }

                context.startActivity(Intent.createChooser(intent, "Ekran Görüntüsünü Paylaş").apply {

                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                })

            } catch (e: kotlinx.coroutines.CancellationException) {

                throw e

            } catch (e: Exception) {

                Log.e("PlayerViewModel", "shareImage failed", e)

                Toast.makeText(context, KitsugiStrings.get(R.string.paylasim_basarisiz_x, e.localizedMessage), Toast.LENGTH_SHORT).show()

            }

        }

    }



    fun setAsArt(artType: ArtType, imageStream: () -> java.io.InputStream) {

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {

            val db = KitsugiDatabase.getDatabase(context)

            val entryEntity = malId?.let { db.mediaEntryDao().getByMalId(it) }

                ?: aniListId?.let { db.mediaEntryDao().getById(it) }

                ?: db.mediaEntryDao().getAll().firstOrNull { it.title.equals(animeTitle, ignoreCase = true) }



            if (entryEntity == null && artType == ArtType.Cover) {

                withContext(kotlinx.coroutines.Dispatchers.Main) {

                    Toast.makeText(context, KitsugiStrings.get(R.string.resmi_ayarlamak_icin_once_bu_animeyi_kutuphanenize_eklemelisiniz), Toast.LENGTH_LONG).show()

                }

                return@launch

            }



            try {

                when (artType) {

                    ArtType.Cover -> {

                        val entry = entryEntity ?: return@launch

                        val dir = java.io.File(context.filesDir, "covers").apply { mkdirs() }

                        val file = java.io.File(dir, "${entry.id}.png")

                        file.outputStream().use { out ->

                            imageStream().use { input -> input.copyTo(out) }

                        }

                        // Update in DB

                        val updated = entry.copy(imageUrl = "file://${file.absolutePath}")

                        db.mediaEntryDao().update(updated)

                        withContext(kotlinx.coroutines.Dispatchers.Main) {

                            Toast.makeText(context, KitsugiStrings.get(R.string.kapak_resmi_guncellendi), Toast.LENGTH_SHORT).show()

                        }

                    }

                    ArtType.Background -> {

                        // Set wallpaper

                        val wallpaperManager = android.app.WallpaperManager.getInstance(context)

                        imageStream().use { input ->

                            wallpaperManager.setStream(input)

                        }

                        withContext(kotlinx.coroutines.Dispatchers.Main) {

                            Toast.makeText(context, KitsugiStrings.get(R.string.duvar_kagidi_guncellendi), Toast.LENGTH_SHORT).show()

                        }

                    }

                    ArtType.Thumbnail -> {

                        val dir = java.io.File(context.filesDir, "thumbnails").apply { mkdirs() }

                        val ep = _currentEpisode.value

                        val primaryKey = entryEntity?.id ?: getResolveMediaId()

                        val file = java.io.File(dir, "${primaryKey}_${ep}.png")

                        file.outputStream().use { out ->

                            imageStream().use { input -> input.copyTo(out) }

                        }

                        malId?.let { mId ->

                            if (mId != primaryKey) {

                                runCatching { file.copyTo(java.io.File(dir, "mal_${mId}_${ep}.png"), overwrite = true) }

                            }

                        }

                        val localThumbUri = "file://${file.absolutePath}"

                        _episodesList.value = _episodesList.value.map { item ->

                            if (item.episodeNumber == ep) item.copy(thumbnail = localThumbUri) else item

                        }

                        withContext(kotlinx.coroutines.Dispatchers.Main) {

                            Toast.makeText(context, KitsugiStrings.get(R.string.kucuk_resim_guncellendi), Toast.LENGTH_SHORT).show()

                        }

                    }

                }

            } catch (e: Exception) {

                Log.e("KitsugiPlayerViewModel", "setAsArt failed", e)

                withContext(kotlinx.coroutines.Dispatchers.Main) {

                    Toast.makeText(context, KitsugiStrings.get(R.string.resim_ayarlanirken_hata_olustu_x, e.localizedMessage), Toast.LENGTH_SHORT).show()

                }

            }

        }

    }



    private fun formatMs(ms: Long): String {

        val totalSecs = ms / 1000

        val hours = totalSecs / 3600

        val mins = (totalSecs % 3600) / 60

        val secs = totalSecs % 60

        return if (hours > 0) {

            String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, mins, secs)

        } else {

            String.format(java.util.Locale.US, "%02d:%02d", mins, secs)

        }

    }



    fun setPrimaryCustomButtonTitle(button: CustomButton) {

        val title = if (button.name.length <= 8) button.name else button.name.take(7) + "…"

        _primaryButtonTitle.value = title

    }



    fun executeCustomButton(button: CustomButton) {

        activeEngine?.executeCommand(arrayOf("script-message", "call_button_${button.id}"))

    }



    fun executeCustomButtonLongPress(button: CustomButton) {

        activeEngine?.executeCommand(arrayOf("script-message", "call_button_${button.id}_long"))

    }



    fun seekToWithText(positionSec: Int, text: String?) {

        val targetMs = positionSec * 1000L

        activeEngine?.seekTo(targetMs)

        _pos.value = targetMs

        if (!text.isNullOrBlank()) {

            playerUpdate.value = KitsugiPlayerUpdates.ShowText(text)

        }

    }



    fun seekByWithText(deltaSec: Int, text: String?) {

        val current = _pos.value

        val dur = _duration.value

        val target = (current + deltaSec * 1000L).coerceIn(0L, dur)

        activeEngine?.seekTo(target)

        _pos.value = target

        if (!text.isNullOrBlank()) {

            playerUpdate.value = KitsugiPlayerUpdates.ShowText(text)

        }

    }



    fun changeEpisode(previous: Boolean) {

        val target = if (previous) _currentEpisode.value - 1 else _currentEpisode.value + 1

        val activity = context.findActivity()

        playEpisode(target, activity, {}, {})

    }



    fun handleLuaInvocation(property: String, value: String) {

        val data = value

            .removePrefix("\"")

            .removeSuffix("\"")

            .ifEmpty { return }



        val mpvEngine = activeEngine as? com.kitsugi.animelist.core.player.engine.MpvPlayerEngine



        when (property.substringAfterLast("/")) {

            "show_text" -> playerUpdate.value = KitsugiPlayerUpdates.ShowText(data)

            "toggle_ui" -> {

                when (data) {

                    "show" -> showControls()

                    "toggle" -> toggleControls()

                    "hide" -> {

                        _sheetShown.value = KitsugiSheets.None

                        _panelShown.value = KitsugiPanels.None

                        _dialogShown.value = KitsugiDialogs.None

                        hideControls()

                    }

                }

            }

            "show_panel" -> {

                when (data) {

                    "subtitle_settings" -> showPanel(KitsugiPanels.SubtitleSettings)

                    "subtitle_delay" -> showPanel(KitsugiPanels.SubtitleDelay)

                    "audio_delay" -> showPanel(KitsugiPanels.AudioDelay)

                    "video_filters" -> showPanel(KitsugiPanels.VideoFilters)

                }

            }

            "set_button_title" -> {

                _primaryButtonTitle.value = data

            }

            "reset_button_title" -> {

                customButtons.value.firstOrNull { it.isFavorite }?.let {

                    setPrimaryCustomButtonTitle(it)

                }

            }

            "switch_episode" -> {

                when (data) {

                    "n" -> changeEpisode(false)

                    "p" -> changeEpisode(true)

                }

            }

            "launch_int_picker" -> {

                val parts = data.split("|")

                if (parts.size >= 6) {

                    val title = parts[0]

                    val nameFormat = parts[1]

                    val start = parts[2]

                    val stop = parts[3]

                    val step = parts[4]

                    val pickerProperty = parts[5]



                    val defaultValue = mpvEngine?.mpvView?.mpv?.getPropertyInt(pickerProperty) ?: 0

                    showDialog(

                        KitsugiDialogs.IntegerPicker(

                            defaultValue = defaultValue,

                            minValue = start.toIntOrNull() ?: 0,

                            maxValue = stop.toIntOrNull() ?: 100,

                            step = step.toIntOrNull() ?: 1,

                            nameFormat = nameFormat,

                            title = title,

                            onChange = { mpvEngine?.mpvView?.mpv?.setPropertyInt(pickerProperty, it) },

                            onDismissRequest = { showDialog(KitsugiDialogs.None) }

                        )

                    )

                }

            }

            "pause" -> {

                when (data) {

                    "pause" -> pause()

                    "unpause" -> play()

                    "pauseunpause" -> if (activeEngine?.isPlaying == true) pause() else play()

                }

            }

            "seek_by", "seek_to", "seek_by_with_text", "seek_to_with_text" -> {

                val parts = data.split("|", limit = 2)

                val seconds = parts[0].toIntOrNull()

                if (seconds != null) {

                    val text = parts.getOrNull(1)

                    if (property.substringAfterLast("/").startsWith("seek_by")) {

                        seekByWithText(seconds, text)

                    } else {

                        seekToWithText(seconds, text)

                    }

                }

            }

            "toggle_button" -> {

                fun showButton() {

                    if (_primaryButton.value == null) {

                        _primaryButton.update {

                            customButtons.value.firstOrNull { it.isFavorite }

                        }

                    }

                }



                when (data) {

                    "show" -> showButton()

                    "hide" -> _primaryButton.update { null }

                    "toggle" -> if (_primaryButton.value == null) showButton() else _primaryButton.update { null }

                }

            }

            "software_keyboard" -> {

                val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager

                when (data) {

                    "show" -> {

                        val activity = context.findActivity()

                        val view = activity?.currentFocus ?: activity?.window?.decorView

                        if (view != null) {

                            inputMethodManager.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_FORCED)

                        }

                    }

                    "hide" -> {

                        val activity = context.findActivity()

                        val view = activity?.currentFocus

                        if (view != null) {

                            inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)

                        }

                    }

                    "toggle" -> {

                        inputMethodManager.toggleSoftInput(android.view.inputmethod.InputMethodManager.SHOW_FORCED, 0)

                    }

                }

            }

        }



        mpvEngine?.mpvView?.mpv?.setPropertyString(property, "")

    }



    fun getAnimeSkipIntroLength(): Int {

        val sharedPrefs = context.getSharedPreferences("kitsugi_player_prefs", Context.MODE_PRIVATE)

        return sharedPrefs.getInt("intro_length_${getResolveMediaId()}", 85)

    }



    fun setAnimeSkipIntroLength(length: Int) {

        val sharedPrefs = context.getSharedPreferences("kitsugi_player_prefs", Context.MODE_PRIVATE)

        sharedPrefs.edit().putInt("intro_length_${getResolveMediaId()}", length).apply()

        val mpvEngine = activeEngine as? com.kitsugi.animelist.core.player.engine.MpvPlayerEngine

        mpvEngine?.mpvView?.mpv?.let { mpv ->

            val currentVal = mpv.getPropertyInt("user-data/current-anime/intro-length") ?: -1

            if (currentVal != length) {

                mpv.setPropertyInt("user-data/current-anime/intro-length", length)

            }

        }

    }



    fun addAudio(uri: android.net.Uri) {

        val url = uri.toString()

        val isContentUri = url.startsWith("content://")

        val path = if (isContentUri) uri.openContentFd(context) else url

        if (path == null) return

        val name = if (isContentUri) uri.getFileName(context) else null

        if (name == null) {

            activeEngine?.executeCommand(arrayOf("audio-add", path, "cached"))

        } else {

            activeEngine?.executeCommand(arrayOf("audio-add", path, "cached", name))

        }

    }



    fun addSubtitle(uri: android.net.Uri) {

        val url = uri.toString()

        val isContentUri = url.startsWith("content://")

        val path = if (isContentUri) uri.openContentFd(context) else url

        if (path == null) return

        val name = if (isContentUri) uri.getFileName(context) else null

        if (name == null) {

            activeEngine?.executeCommand(arrayOf("sub-add", path, "cached"))

        } else {

            activeEngine?.executeCommand(arrayOf("sub-add", path, "cached", name))

        }

    }



    private fun android.net.Uri.openContentFd(context: Context): String? {

        return context.contentResolver.openFileDescriptor(this, "r")?.detachFd()?.let {

            `is`.xyz.mpv.Utils.findRealPath(it)?.also { _ ->

                android.os.ParcelFileDescriptor.adoptFd(it).close()

            } ?: "fd://$it"

        }

    }



    private fun android.net.Uri.getFileName(context: Context): String? {

        return context.contentResolver.query(this, null, null, null, null)?.use { cursor ->

            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)

            if (nameIndex != -1 && cursor.moveToFirst()) {

                cursor.getString(nameIndex)

            } else null

        }

    }



    // ─────────────────────────────────────────────────────────────────────────

    // Ses İndirme — Downloads/Kitsugi/Audio/

    // ─────────────────────────────────────────────────────────────────────────



    /**

     * Enqueues an audio-only export for a track chosen from the player's audio tracks sheet.

     * The row's language/name/codec are preserved; the UI index and mpv track-list index

     * cannot be used as a MediaExtractor stream index. The job survives leaving the player.

     */

    fun downloadAudioToStorage(

        track: com.kitsugi.animelist.ui.screens.fullscreen.components.TrackOption,

        tracks: List<com.kitsugi.animelist.ui.screens.fullscreen.components.TrackOption>

    ) {

        try {

            val video = _playbackSource.value?.videoUrl ?: _currentVideoUrl.value

            require(!video.isNullOrBlank() && track.audioMediaUrl == video &&

                tracks.all { it.audioMediaUrl == video }) { "Ses listesi eski kaynağa ait; yeniden açın" }

            val separate = _playbackSource.value?.audioUrl ?: _currentAudioUrl.value

            // Media3's MergingMediaSource can present an external track and an embedded

            // track identically. A sole UI row is not evidence that it came from audioUrl.

            require(separate.isNullOrBlank() || track.audioOriginKnown) {

                "Ayrı sesin kaynağı bu oynatıcıda ayırt edilemiyor; mpv veya eklenti ses satırını kullanın"

            }

            require(!track.audioIsExternal || !track.audioSource.isNullOrBlank()) {

                "Seçilen harici sesin URL'si belirlenemedi"

            }

            val identities = tracks.map { option ->

                com.kitsugi.animelist.core.player.audio.AudioTrackIdentity(

                    option.audioLanguage, option.audioName,

                    option.audioSource?.takeIf { option.audioIsExternal && it.isNotBlank() }

                )

            }

            val selected = com.kitsugi.animelist.core.player.audio.AudioTrackIdentity(

                track.audioLanguage, track.audioName,

                track.audioSource?.takeIf { track.audioIsExternal && it.isNotBlank() }

            )

            val source = com.kitsugi.animelist.core.player.audio.AudioExportSpec.source(video, selected)

            val allowed = source.startsWith("https://", true) || source.startsWith("http://", true) ||

                source.startsWith("content://", true) || source.startsWith("file://", true) || source.startsWith('/')

            require(allowed) { "Bu kaynak türü ses dosyasına dönüştürülemiyor" }

            val active = _currentStreamSources.value.getOrNull(_currentSourceIndex.value)

            val headers = audioRequestHeaders(video, source, active)

            val rawTitle = animeTitle.ifBlank { _animeTitleFlow.value.ifBlank { _currentTitle.value } }

            com.kitsugi.animelist.core.player.audio.AudioExportWorker.enqueue(

                context, source, headers, selected, identities,

                independent = selected.externalUrl != null,

                title = rawTitle.ifBlank { "Video" }, episode = _currentEpisode.value,

                movie = _isMovie.value || _currentEpisode.value <= 0, bitrate = track.audioBitrate,

                transport = if (source == video) active?.mediaTransport else null

            )

            Toast.makeText(context, "Ses indirmesi kuyruğa alındı; ilerleme bildirimlerde.", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {

            Toast.makeText(context, "Ses indirilemedi: ${e.message ?: "kaynak belirlenemedi"}", Toast.LENGTH_LONG).show()

        }

    }



    /** CloudStream AudioFile contains URL + headers but no language/name: never invent either. */

    fun downloadProviderAudioToStorage(index: Int, expectedUrl: String?) {

        try {

            val video = _playbackSource.value?.videoUrl ?: _currentVideoUrl.value

            require(!video.isNullOrBlank()) { "Oynatılan video kaynağı bulunamadı" }

            val active = _currentStreamSources.value.getOrNull(_currentSourceIndex.value)

            require(active?.isCS == true && expectedUrl != null) { "Eklenti ses listesi artık geçerli değil" }

            val audio = active.externalAudioSources.getOrNull(index)

                ?: throw IllegalArgumentException("Eklenti ses kaynağı bulunamadı")

            require(audio.url == expectedUrl && audio.url.isNotBlank()) { "Eklenti ses kaynağı değişti" }

            val allowed = audio.url.startsWith("https://", true) || audio.url.startsWith("http://", true)

            require(allowed) { "Eklenti ses adresi desteklenmiyor" }

            val identity = com.kitsugi.animelist.core.player.audio.AudioTrackIdentity(

                language = null, name = "Eklenti sesi ${index + 1}", externalUrl = audio.url

            )

            val title = animeTitle.ifBlank { _animeTitleFlow.value.ifBlank { _currentTitle.value } }

            com.kitsugi.animelist.core.player.audio.AudioExportWorker.enqueue(

                context, audio.url, audioRequestHeaders(video, audio.url, active, index),

                identity, listOf(identity), independent = true,

                title = title.ifBlank { "Video" }, episode = _currentEpisode.value,

                movie = _isMovie.value || _currentEpisode.value <= 0, bitrate = null

            )

            Toast.makeText(context, "Eklenti sesi indirmesi kuyruğa alındı.", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {

            Toast.makeText(context, "Ses indirilemedi: ${e.message ?: "kaynak belirlenemedi"}", Toast.LENGTH_LONG).show()

        }

    }



    private fun audioRequestHeaders(video: String, source: String, active: StreamSource?,

                                    providerIndex: Int? = null): Map<String, String> {

        val base = _currentHeaders.value

        val headers = if (source == video) base.toMutableMap() else

            com.kitsugi.animelist.core.player.audio.AudioExportHttp.headersFor(video, source, base).toMutableMap()

        // Two AudioFile entries may share a URL but require DIFFERENT credentials.

        // The provider-only row carries its exact index; a player row without an index

        // must not guess which credential set to send.

        val sources = active?.externalAudioSources.orEmpty()

        val own = if (providerIndex != null) {

            val entry = sources.getOrNull(providerIndex)

            require(entry?.url == source) { "Eklenti ses başlıkları değişti" }

            entry.requestHeaders

        } else {

            val matches = sources.filter { it.url == source }

            require(matches.map { it.requestHeaders }.distinct().size <= 1) {

                "Aynı ses URL'si için farklı başlıklar var; eklentinin ayrı ses satırını kullanın"

            }

            matches.firstOrNull()?.requestHeaders

        }

        own?.forEach { (key, value) ->

            headers.keys.removeAll { it.equals(key, ignoreCase = true) }

            headers[key] = value

        }

        return headers

    }



    // ─────────────────────────────────────────────────────────────────────────

    // Altyazı İndirme — Downloads/Kitsugi/Subtitles/

    // ─────────────────────────────────────────────────────────────────────────



    /**

     * Verilen [subtitle]'ı cihazın Downloads/Kitsugi/Subtitles/ klasörüne indirir.

     * - Dosya formatını (SRT, ASS, VTT, TTML vb.) URL ve Content-Type'a göre tespit eder.

     * - Android 10+ için MediaStore; eski sürümlerde doğrudan dosya yazımı kullanır.

     * - Aynı dosya zaten varsa cache'den kopyalar, yoksa URL'den indirir.

     * - Tamamlanma/hata durumunda Toast gösterir.

     */

    fun downloadSubtitleToStorage(subtitle: SubtitleInput) {

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {

            try {

                // 1. Önce cache'e al (yerel dosya ise doğrudan kullan, yoksa cacheSubtitle ile indir)

                val cachedFile: java.io.File? = if (subtitle.url.startsWith("file://") || subtitle.url.startsWith("/")) {

                    val local = java.io.File(subtitle.url.removePrefix("file://"))

                    if (local.exists() && local.isFile) local else com.kitsugi.animelist.core.player.SubtitleFileCache.cacheSubtitle(context, subtitle.url)

                } else {

                    com.kitsugi.animelist.core.player.SubtitleFileCache.cacheSubtitle(context, subtitle.url)

                }

                if (cachedFile == null || !cachedFile.exists()) {

                    withContext(kotlinx.coroutines.Dispatchers.Main) {

                        Toast.makeText(context, KitsugiStrings.get(R.string.altyazi_indirilemedi), Toast.LENGTH_SHORT).show()

                    }

                    return@launch

                }



                // 2. Hedef dosya adını video ve bölüm ismini içerecek şekilde oluştur

                val ext = when {

                    cachedFile.extension.isNotBlank() && cachedFile.extension.lowercase() in listOf("srt", "vtt", "ass", "ssa", "sub", "ttml", "dfxp", "txt") ->

                        cachedFile.extension.lowercase()

                    else -> com.kitsugi.animelist.core.player.SubtitleFileCache.guessExtension(subtitle.url)

                }



                val rawTitle = when {

                    animeTitle.isNotBlank() -> animeTitle.trim()

                    _animeTitleFlow.value.isNotBlank() -> _animeTitleFlow.value.trim()

                    _currentTitle.value.isNotBlank() -> _currentTitle.value.trim()

                    else -> "Video"

                }



                val ep = _currentEpisode.value

                val isMovieContent = _isMovie.value || (ep <= 0 && !rawTitle.contains("Bölüm", ignoreCase = true))



                val videoPrefix = if (isMovieContent) {

                    rawTitle

                } else {

                    val epRegex = Regex("""(?i)\b(bölüm|episode|ep\.?)\s*0*$ep\b""")

                    if (ep > 0 && !epRegex.containsMatchIn(rawTitle)) {

                        "$rawTitle - Bölüm $ep"

                    } else {

                        rawTitle

                    }

                }



                val cleanVideoName = videoPrefix.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim()



                val rawLang = subtitle.lang.trim()

                val safeLang = rawLang.lowercase().filter { it.isLetterOrDigit() }

                val rawName = subtitle.name.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim()



                val isGenericName = rawName.isBlank() ||

                    rawName.equals("subtitle", ignoreCase = true) ||

                    rawName.equals("sub", ignoreCase = true) ||

                    rawName.equals("default", ignoreCase = true) ||

                    rawName.equals(safeLang, ignoreCase = true) ||

                    (safeLang in listOf("tr", "tur") && (rawName.equals("turkish", ignoreCase = true) || rawName.equals("türkçe", ignoreCase = true))) ||

                    (safeLang in listOf("en", "eng") && (rawName.equals("english", ignoreCase = true) || rawName.equals("ingilizce", ignoreCase = true)))



                val subPart = when {

                    safeLang.isNotBlank() && !isGenericName -> "${safeLang}_${rawName}"

                    safeLang.isNotBlank() -> safeLang

                    !isGenericName -> rawName

                    else -> "sub"

                }



                val fileName = "${cleanVideoName}.${subPart}.${ext}"



                // 3. Android 10+ MediaStore API ile Downloads/Kitsugi/Subtitles/ klasörüne yaz

                val savedPath: String? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {

                    val mimeType = when (ext.lowercase()) {

                        "ass", "ssa"   -> "text/x-ass"

                        "vtt"          -> "text/vtt"

                        "ttml", "dfxp" -> "application/ttml+xml"

                        "sub", "txt"   -> "text/plain"

                        else           -> "application/x-subrip"  // srt

                    }

                    val values = android.content.ContentValues().apply {

                        put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)

                        put(android.provider.MediaStore.Downloads.MIME_TYPE, mimeType)

                        put(android.provider.MediaStore.Downloads.RELATIVE_PATH,

                            "${android.os.Environment.DIRECTORY_DOWNLOADS}/Kitsugi/Subtitles")

                        put(android.provider.MediaStore.Downloads.IS_PENDING, 1)

                    }

                    val uri = context.contentResolver.insert(

                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values

                    )

                    if (uri != null) {

                        context.contentResolver.openOutputStream(uri)?.use { out ->

                            cachedFile.inputStream().copyTo(out)

                        }

                        values.clear()

                        values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)

                        context.contentResolver.update(uri, values, null, null)

                        "Downloads/Kitsugi/Subtitles/$fileName"

                    } else null

                } else {

                    // Android 9 ve altı: doğrudan Environment.DIRECTORY_DOWNLOADS

                    @Suppress("DEPRECATION")

                    val dir = java.io.File(

                        android.os.Environment.getExternalStoragePublicDirectory(

                            android.os.Environment.DIRECTORY_DOWNLOADS

                        ),

                        "Kitsugi/Subtitles"

                    )

                    dir.mkdirs()

                    val dest = java.io.File(dir, fileName)

                    cachedFile.copyTo(dest, overwrite = true)

                    dest.absolutePath

                }



                withContext(kotlinx.coroutines.Dispatchers.Main) {

                    if (savedPath != null) {

                        Toast.makeText(

                            context,

                            "✅ Altyazı kaydedildi [${ext.uppercase()}]\n$savedPath",

                            Toast.LENGTH_LONG

                        ).show()

                    } else {

                        Toast.makeText(context, KitsugiStrings.get(R.string.altyazi_kaydedilemedi), Toast.LENGTH_SHORT).show()

                    }

                }

            } catch (e: Exception) {

                Log.e("KitsugiPlayer", "Altyazı indirme hatası: ${subtitle.url}", e)

                withContext(kotlinx.coroutines.Dispatchers.Main) {

                    Toast.makeText(context, KitsugiStrings.get(R.string.hata_x_f2ebae5, e.localizedMessage), Toast.LENGTH_SHORT).show()

                }

            }

        }

    }

}





