package com.kitsugi.animelist.core.player.engine

import android.content.Context
import android.util.Log
import android.view.View
import com.kitsugi.animelist.core.player.DolbyVisionHint
import com.kitsugi.animelist.core.player.DolbyVisionStreamHints
import com.kitsugi.animelist.core.player.SubtitleInput
import com.kitsugi.animelist.ui.screens.fullscreen.components.SubtitleStyleSettings
import com.kitsugi.animelist.ui.screens.fullscreen.components.TrackOption
import com.kitsugi.animelist.ui.screens.fullscreen.components.StreamInfoData
import com.kitsugi.animelist.data.settings.AppSettings
import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.MPVNode
import com.kitsugi.animelist.data.local.KitsugiDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/**
 * Aniyomi PlayerActivity yaklaşımından ilham alınarak yeniden yazılmış MPV motor.
 *
 * Özellikler:
 * - Güçlü property observation (Aniyomi observeProperties modeli)
 * - İkincil altyazı (secondary-sid) desteği
 * - GPU renderer, debanding, demuxer cache, hwdec, YUV420P init seçenekleri
 * - eof-reached, seeking, hwdec-current gözlemi
 * - Volume boost cap (volume-max ayarı)
 */
class MpvPlayerEngine(
    private val context: Context,
    private var settings: AppSettings
) : PlayerEngine, MPV.EventObserver {

    private val TAG = "MpvPlayerEngine"
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<PlayerEngine.Listener>()

    /**
     * Motor yaşam döngüsüne bağlı kapsam. Eskiden [createVideoView] içinde her seferinde
     * `CoroutineScope(Dispatchers.Main)` oluşturuluyor ve hiç iptal edilmiyordu; motor
     * serbest bırakılsa bile script kurulumu çalışmaya devam ediyordu. Artık tek kapsam var
     * ve [release] içinde iptal ediliyor.
     */
    private val engineScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + Dispatchers.Main
    )
    private var released = false
    @Volatile private var loadGeneration = 0L
    private var ducked = false
    private var customButtonsLoaded = false
    private var statisticsPageApplied = 0
    /** Konum bildirimlerini 4 Hz'e indiren damga (bkz. [notifyPositionChanged]). */
    private var lastPositionNotifyAtMs = -1L
    /** Görüntü çıkışını (vo) kuran ağır init seçenekleri yalnızca bir kez uygulanır. */
    private var initOptionsApplied = false

    // ── Dolby Vision otomatik düzeltmesi ─────────────────────────────────────
    // Varsayılan çıkış `vo=gpu` DV RPU'sunu uygulamaz; P5 içerik yeşil/mor çıkar.
    // Akış "DV" olarak etiketliyse gpu-next + (P5/bilinmez profilde) yazılım
    // çözme uygularız. Bkz. [MpvDolbyVisionPolicy].
    /** Bu akışın başlık/ad/URL'den çıkarılan DV ipucu. */
    private var dvHint: DolbyVisionHint? = null
    /** applyInitOptions'un uygulayacağı vo (DV planı dahil). */
    private var resolvedVoOption: String? = null
    /** applyInitOptions'un uygulayacağı hwdec (DV planı dahil). */
    private var resolvedHwdecOption: String? = null
    /** En son uygulanan DV planı; gereksiz vo yeniden kurulumunu önler. */
    private var appliedDvPlanKey: String = ""
    /** `gpu-next` bu libmpv derlemesinde gerçekten var mı (bir kez ölçülür). */
    private var gpuNextSupported: Boolean? = null
    /** DV var ama gpu-next yok uyarısı bu akış için gösterildi mi? */
    private var dvWarningShownForLoad: Boolean = false
    private var focusController: com.kitsugi.animelist.core.player.MpvAudioFocusController? = null
    private fun audioFocus(): com.kitsugi.animelist.core.player.MpvAudioFocusController =
        focusController ?: com.kitsugi.animelist.core.player.MpvAudioFocusController(
            context, { isPlaying }, { setPlaying(false) }, { setPlaying(true) },
            { value -> ducked = value; setVolume(_currentVolume) }, { pause() }
        ).also { focusController = it }

    /**
     * Parça listesi (track-list) güncellemelerini tek seferde toplar.
     *
     * SORUN: mpv her özellik yazımında (`sid`, `aid`, `slang`, harici altyazı ekleme...)
     * ayrı bir `track-list` değişim olayı tetikliyor; her olay da `readTrackSnapshot()`
     * ile onlarca JNI özellik sorgusu yapıyordu. Dosya yüklenirken bu olaylar
     * zincirleme patlıyor (logcat'te aynı milisaniyede ~7 tam anlık görüntü) ve ana
     * iş parçacığını 1 saniyeden uzun süre bloke ediyordu ("PerfMonitor longMsg
     * wall=1222ms" kayıtları bu yüzden).
     *
     * ÇÖZÜM: güncelleme istekleri aynı Looper turunda birleştirilir (coalesce);
     * böylece N olay için N yerine 1 anlık görüntü okunur.
     */
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile private var tracksUpdatePending = false
    private val tracksUpdateRunnable = Runnable {
        tracksUpdatePending = false
        performUpdateTracks()
    }

    /** Parça listesini yenilemek için TERCİH EDİLEN yol — istekleri birleştirir. */
    private fun scheduleUpdateTracks() {
        if (released) return
        if (tracksUpdatePending) return
        tracksUpdatePending = true
        mainHandler.post(tracksUpdateRunnable)
    }

    private fun onMain(block: () -> Unit) {
        val generationAtDispatch = loadGeneration
        if (released) return
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) block()
        else engineScope.launch(Dispatchers.Main.immediate) { if (!released && generationAtDispatch == loadGeneration) block() }
    }

    private fun setPlaying(value: Boolean) {
        mpvView?.setPaused(!value)
        if (isPlaying != value) {
            isPlaying = value
            listeners.forEach { it.onPlayingChanged(value) }
        }
        checkBufferingState()
    }

    internal var mpvView: KitsugiMpvSurfaceView? = null

    override val engineType: PlayerEngineType = PlayerEngineType.MPV

    // MPV (libmpv) altyazı gecikmesi, negatif gecikme, ikincil altyazı, altyazı hızı ve
    // renk filtrelerinin tamamını destekler.
    override val supportsExternalTracks: Boolean get() = true
    override val supportsSecondarySubtitles: Boolean get() = true
    override val selectedSecondarySubtitleId: Int get() = secondarySubtitleTrackId
    override fun selectSecondarySubtitle(trackId: Int) = selectSecondarySubtitleTrack(trackId)
    override val supportsSubtitleDelay: Boolean get() = true
    override val supportsNegativeSubtitleDelay: Boolean get() = true
    override val supportsSecondarySubtitleDelay: Boolean get() = true
    override val supportsSubtitleSpeed: Boolean get() = true
    override val supportsVideoFilters: Boolean get() = true
    override val supportsNegativeAudioDelay: Boolean get() = true

    /**
     * MPV'de ses açılırken video parçasının hiç seçilmemesi sessiz bir hatadır: ses devam
     * eder, süre ilerler, ekran siyah kalır. Küçük oynatıcı bekçisi bunu görebilsin diye
     * seçili video parçası durumu doğrudan libmpv'den okunur.
     */
    override val hasVideoTrack: Boolean
        get() {
            if (released) return false
            val view = mpvView ?: return false
            return runCatching { view.isVideoOutputActiveNow() }.getOrDefault(false)
        }

    /**
     * Video çıkışı kurulu, seçili parça var VE yüzey görünür durumda mı?
     *
     * Küçük oynatıcı bu bayrağı ses-only bekçisinde kullanır. SurfaceView görünmez
     * (0×0, kırpılmış, pencereden düşmüş) ise mpv kare üretse bile kullanıcı siyah
     * ekran görür; bu durumda yedek motor denenmeli ya da tam ekrana yönlendirilmeli.
     */
    override val hasRenderedVideoFrame: Boolean
        get() {
            if (released) return false
            val view = mpvView ?: return false
            return runCatching {
                // A valid Surface + `vo-configured` can still describe a black screen:
                // no decoded frame may have reached the VO yet. Require video-out
                // dimensions as well, and re-check the currently attached Surface so
                // PiP/fullscreen recreation cannot reuse a stale healthy state.
                view.isVideoOutputActiveNow() &&
                    view.hasVisibleVideoSurface() &&
                    view.hasVideoOutputFrameNow()
            }.getOrDefault(false)
        }
    override var currentState: PlayerEngine.State = PlayerEngine.State.IDLE
        private set

    override var currentPosition: Long = 0L
        private set

    override var bufferedPosition: Long = 0L
        private set

    override var duration: Long = 0L
        private set

    override var isPlaying: Boolean = false
        private set

    private var _currentSpeed: Float = settings.playerSpeed
    override val currentSpeed: Float
        get() = _currentSpeed

    private var _currentVolume: Float = 1.0f
    override val currentVolume: Float
        get() = _currentVolume

    override var subtitleDelayMs: Long = 0L
        private set

    override var audioDelayMs: Long = 0L
        private set

    private var _isSubtitleDisabled: Boolean = false
    override val isSubtitleDisabled: Boolean
        get() = _isSubtitleDisabled

    /** Aktif ikincil altyazı parça ID'si; -1 = kapalı */
    var secondarySubtitleTrackId: Int = settings.secondarySubtitleTrackId
        private set

    /** İkincil altyazı gecikme değeri (saniye cinsinden MPV'ye iletilir) */
    override var secondarySubtitleDelayMs: Long = settings.secondarySubtitleDelayMs
        private set

    /** MPV donanım çözücü türü - hwdec-current gözleminden okunur */
    var activeHwdecMode: String = "none"
        private set

    /** true = buffer dolmayı bekliyor */
    private var pausedForCache: Boolean = false

    /** true = video verisi okunmadan önce idle konumunda */
    private var coreIdle: Boolean = false

    /** true = kullanıcı seek işlemi yapıyor */
    private var isSeeking: Boolean = false

    private var currentAddonName: String? = null
    private var streamTitle: String? = null
    private var videoUrl: String? = null
    private var pendingHeaders: Map<String, String> = emptyMap()
    private var pendingSubtitles: List<SubtitleInput> = emptyList()
    private var pendingStartPositionMs: Long = 0L
    private var pendingAudioTracks: List<com.kitsugi.animelist.core.player.ExternalAudioTrackInput> = emptyList()
    private var appliedVideoFilters: String? = null
    private var appliedDeband: String? = null
    private var appliedHwdec: String? = null
    private var appliedRenderer: String? = null

    /** Hazırlık sırasında motorun aldığı SubtitleInput listesi (dahili/harici bayrağını taşır). */
    private var preparedSubtitles: List<SubtitleInput> = emptyList()
    /**
     * İlk otomatik parça seçimi tamamlandı mı?
     * true  = tamamlandı (kullanıcı manuel değişiklik yapabilir, tekrar tetiklenmez)
     * false = henüz yapılmadı (bir sonraki updateTracks'de çalışacak)
     */
    private var initialSelectionDone: Boolean = false

    /** Mevcut medya dosyası açıldı mı (MPV_EVENT_FILE_LOADED alındı mı)? */
    private var fileLoaded: Boolean = false
    /** Bu medya yükleme döngüsü için oynatıcı hatası zaten bildirildi mi? */
    private var errorReportedForLoad: Boolean = false
    /** Ignore the previous file's terminal event until START_FILE of the replacement. */
    private var awaitingStartFile = false
    /** Ayrı ses akışı (audioUrl) eklendi mi? true ise MPV'nin ses otomatik seçimi ezilmez. */
    private var hasExternalAudio: Boolean = false
    /** Harici altyazılar beklenmeden de ilk seçim yapılsın diye zorlanmış değerlendirme. */
    private var selectionForced: Boolean = false
    /**
     * Kullanıcı bu medya için ses/altyazı parçasını ELLE değiştirdi mi? Oynatma sırasında
     * sonradan gelen altyazılar eklendiğinde tercih politikası yalnızca kullanıcı seçim
     * yapmadıysa yeniden uygulanır (kullanıcının seçimi asla ezilmez).
     */
    private var userChangedTracks: Boolean = false

    // ──── Observed properties (Aniyomi modeli) ───────────────────────────────
    // Bu liste, MPV başlatılırken tek seferlik kayıt yapılır.
    private val observedProps = mapOf(
        "time-pos"          to MPV.mpvFormat.MPV_FORMAT_DOUBLE,
        "duration"          to MPV.mpvFormat.MPV_FORMAT_DOUBLE,
        "demuxer-cache-time" to MPV.mpvFormat.MPV_FORMAT_DOUBLE,
        "pause"             to MPV.mpvFormat.MPV_FORMAT_FLAG,
        "paused-for-cache"  to MPV.mpvFormat.MPV_FORMAT_FLAG,
        "core-idle"         to MPV.mpvFormat.MPV_FORMAT_FLAG,
        "seeking"           to MPV.mpvFormat.MPV_FORMAT_FLAG,
        "eof-reached"       to MPV.mpvFormat.MPV_FORMAT_FLAG,
        "track-list"        to MPV.mpvFormat.MPV_FORMAT_NONE,
        "chapter"           to MPV.mpvFormat.MPV_FORMAT_INT64,
        "chapter-list"      to MPV.mpvFormat.MPV_FORMAT_NONE,
        "speed"             to MPV.mpvFormat.MPV_FORMAT_DOUBLE,
        "volume"            to MPV.mpvFormat.MPV_FORMAT_DOUBLE,
        "sub-visibility"    to MPV.mpvFormat.MPV_FORMAT_FLAG,
        "sid"               to MPV.mpvFormat.MPV_FORMAT_STRING,
        "secondary-sid"     to MPV.mpvFormat.MPV_FORMAT_STRING,
        "aid"               to MPV.mpvFormat.MPV_FORMAT_STRING,
        "hwdec-current"     to MPV.mpvFormat.MPV_FORMAT_STRING,
        "video-params/w"    to MPV.mpvFormat.MPV_FORMAT_INT64,
        "video-params/h"    to MPV.mpvFormat.MPV_FORMAT_INT64,
        "user-data/current-anime/intro-length" to MPV.mpvFormat.MPV_FORMAT_INT64
    ) + MpvPlaybackPolicy.scriptProperties.associateWith { MPV.mpvFormat.MPV_FORMAT_STRING }

    override val activeStreamInfo: StreamInfoData
        get() {
            val vCodec = runCatching { mpvView?.mpv?.getPropertyString("video-codec") }.getOrNull()
            val aCodec = runCatching { mpvView?.mpv?.getPropertyString("audio-codec") }.getOrNull()
            val vw = runCatching { mpvView?.mpv?.getPropertyInt("video-params/w") }.getOrNull()
            val vh = runCatching { mpvView?.mpv?.getPropertyInt("video-params/h") }.getOrNull()
            val fps = runCatching { mpvView?.mpv?.getPropertyDouble("estimated-vf-fps") }.getOrNull()
            val bitrate = runCatching { mpvView?.mpv?.getPropertyInt("video-bitrate") }.getOrNull()
            return StreamInfoData(
                addonName = currentAddonName ?: "Dahili",
                streamName = "MPV",
                streamDescription = streamTitle,
                filename = videoUrl?.substringAfterLast('/'),
                playerEngine = "MPV ($activeHwdecMode)",
                videoWidth = vw ?: mpvView?.width,
                videoHeight = vh ?: mpvView?.height,
                videoCodec = vCodec,
                audioCodec = aCodec,
                videoFrameRate = fps?.toFloat(),
                videoBitrate = bitrate
            )
        }

    override fun addListener(listener: PlayerEngine.Listener) { listeners.add(listener) }
    override fun removeListener(listener: PlayerEngine.Listener) { listeners.remove(listener) }

    override suspend fun prepare(
        videoUrl: String,
        audioUrl: String?,
        externalAudioTracks: List<com.kitsugi.animelist.core.player.ExternalAudioTrackInput>,
        streamTypeHint: String?,
        headers: Map<String, String>,
        subtitles: List<SubtitleInput>,
        startPositionMs: Long,
        addonName: String?,
        isCS: Boolean,
        streamTitle: String?,
        qualityValue: Int?,
        mediaType: com.kitsugi.animelist.core.player.StreamMediaType?
    ) {
        this.videoUrl = videoUrl
        this.currentAddonName = addonName
        this.streamTitle = streamTitle
        // Dolby Vision ipucu (başlık / eklenti adı / dosya adı) — vo/hwdec planı için.
        dvHint = DolbyVisionStreamHints.detect(streamTitle, addonName, videoUrl)
        dvWarningShownForLoad = false
        dvHint?.let {
            Log.i(TAG, "DV_HINT: profile=${it.profile ?: "unknown"} match=\"${it.matched}\" host=${videoUrl.safeHost()}")
        }
        // Yeni ortam için otomatik parça seçimini sıfırla
        this.preparedSubtitles = subtitles
        this.initialSelectionDone = false
        this._isSubtitleDisabled = false

        updateState(PlayerEngine.State.BUFFERING)

        this.pendingHeaders = headers
        this.pendingSubtitles = subtitles
        this.pendingStartPositionMs = startPositionMs

        this.pendingAudioTracks = mergeAudioTracks(audioUrl, externalAudioTracks)
        val view = mpvView
        if (view != null) {
            syncDolbyVisionOptions(view)
            applyInitOptions(view)
            beginLoad(videoUrl, pendingAudioTracks, headers, startPositionMs)
            view.setMedia(videoUrl, headers, startPositionMs, forceReload = true)
            setupCustomButtons()
        }
    }

    override fun play() {
        if (mpvView == null) return
        if (audioFocus().request()) setPlaying(true)
    }

    override fun pause() {
        focusController?.abandonFocus()
        setPlaying(false)
    }

    override fun seekTo(positionMs: Long) {
        if (settings.preciseSeeking) {
            // Hassas mod — tam kare araması (daha yavaş ama doğru)
            runCatching {
                mpvView?.mpv?.command(
                    "seek",
                    (positionMs / 1000.0).toString(),
                    "absolute",
                    "exact"
                )
            }
        } else {
            mpvView?.seekToMs(positionMs)
        }
        currentPosition = positionMs
        notifyPositionChanged(force = true)
    }

    override fun setPlaybackSpeed(speed: Float) {
        _currentSpeed = speed
        mpvView?.setPlaybackSpeed(speed)
    }

    override fun setVolume(volume: Float) {
        val cap = settings.volumeBoostCap.coerceIn(100, 200)
        _currentVolume = MpvPlaybackPolicy.volume(volume, cap)
        runCatching {
            mpvView?.mpv?.setPropertyInt("volume-max", cap)
            mpvView?.mpv?.setPropertyDouble("volume", _currentVolume * 100.0 * if (ducked) 0.5 else 1.0)
        }
        // Do not applyAudioAmplificationDb(0) here: it writes volume=100 and
        // undoes normal volume adjustments, including mute.
    }

    override fun setSubtitleDelay(delayMs: Long) {
        subtitleDelayMs = delayMs
        runCatching {
            mpvView?.mpv?.setPropertyDouble("sub-delay", delayMs / 1000.0)
        }
    }

    /** İkincil altyazı gecikmesini ayarla */
    override fun setSecondarySubtitleDelay(delayMs: Long) {
        secondarySubtitleDelayMs = delayMs
        runCatching {
            mpvView?.mpv?.setPropertyDouble("secondary-sub-delay", delayMs / 1000.0)
        }
    }

    /** Altyazı hızı (mpv: sub-speed). 1.0 = normal. */
    override fun setSubtitleSpeed(speed: Float) {
        val safeSpeed = speed.coerceIn(0.1f, 10f)
        runCatching {
            mpvView?.mpv?.setPropertyDouble("sub-speed", safeSpeed.toDouble())
        }.onFailure {
            Log.w(TAG, "sub-speed uygulanamadı: ${it.message}")
        }
    }

    /** Video renk filtreleri (mpv: brightness/contrast/saturation/gamma/hue, -100..100). */
    override fun setVideoFilters(values: Map<String, Int>) {
        if (values.isEmpty()) return
        values.forEach { (property, value) ->
            runCatching {
                mpvView?.mpv?.setPropertyInt(property, value.coerceIn(-100, 100))
            }.onFailure {
                Log.w(TAG, "Video filtresi uygulanamadı ($property=$value): ${it.message}")
            }
        }
    }

    /** İkincil altyazı parça ID'sini seç; -1 = kapat */
    fun selectSecondarySubtitleTrack(trackId: Int) {
        if (trackId >= 0 && mpvView?.mpv?.getPropertyString("sid")?.toIntOrNull() == trackId) return
        initialSelectionDone = true
        selectionForced = true
        userChangedTracks = true
        secondarySubtitleTrackId = trackId
        if (trackId >= 0) {
            mpvView?.mpv?.setPropertyBoolean("sub-visibility", true)
            _isSubtitleDisabled = false
        }
        runCatching {
            if (trackId == -1) {
                mpvView?.mpv?.setPropertyString("secondary-sid", "no")
            } else {
                mpvView?.mpv?.setPropertyString("secondary-sid", trackId.toString())
            }
        }
        listeners.forEach { it.onSecondarySubtitleChanged(secondarySubtitleTrackId) }
    }

    override fun setAudioDelay(delayMs: Long) {
        audioDelayMs = delayMs
        runCatching {
            mpvView?.mpv?.setPropertyDouble("audio-delay", delayMs / 1000.0)
        }
    }

    private var activeSubtitleStyle = SubtitleStyleSettings()

    fun setStatisticsPage(page: Int) {
        val safe = page.coerceIn(0, 5)
        if ((statisticsPageApplied == 0) != (safe == 0))
            executeCommand(arrayOf("script-binding", "stats/display-stats-toggle"))
        if (safe != 0) executeCommand(arrayOf("script-binding", "stats/display-page-$safe"))
        statisticsPageApplied = safe
    }

    fun updateSettings(value: AppSettings) {
        settings = value
        mpvView?.let { applyInitOptions(it) }
        setSubtitleStyle(activeSubtitleStyle)
        if (fileLoaded) setStatisticsPage(settings.playerStatisticsPage)
    }

    override fun setSubtitleStyle(style: SubtitleStyleSettings) {
        activeSubtitleStyle = style
        val view = mpvView ?: return
        // Aniyomi paritesi: tüm tipografi/renk/diğer ayarlar doğrudan SubtitleStyleSettings'ten gelir.
        val effectiveFont = style.font.takeIf { it.isNotBlank() } ?: settings.subtitleFont.ifBlank { "Sans Serif" }
        val effectiveFontSize = style.fontSize.takeIf { it in 1..200 } ?: 45
        val effectiveScale = style.fontScale.takeIf { it in 0.1f..10f } ?: ((style.size / 20.0f) * settings.subtitleFontScale).coerceIn(0.1f, 10f)
        val effectiveBorderSize = style.borderSize.takeIf { it in 0..100 } ?: settings.subtitleBorderSize.toInt()
        val effectiveBorderStyle = style.borderStyle.takeIf { it.isNotBlank() } ?: settings.subtitleBorderStyle
        val effectiveShadowOffset = style.shadowOffset.takeIf { it in 0..100 } ?: settings.subtitleShadowOffset.toInt()
        val effectiveBackgroundColor = style.backgroundColor
        val effectiveBorderColor = style.borderColor
        val effectivePos = style.pos.takeIf { it in 0..150 } ?: settings.subtitlePos
        val finalPos = (effectivePos - style.verticalOffset / 5.0).coerceIn(0.0, 150.0)
        val effectiveJustification = style.justification.takeIf { it.isNotBlank() } ?: settings.subtitleJustification
        val effectiveOverrideAss = style.overrideAss || settings.subtitleOverrideAss
        val options = mapOf(
            "sub-font" to effectiveFont,
            "sub-font-size" to effectiveFontSize.toString(),
            "sub-scale" to effectiveScale.toString(),
            "sub-bold" to if (style.bold) "yes" else "no",
            "sub-italic" to if (style.italic) "yes" else "no",
            "sub-color" to String.format(java.util.Locale.US, "#%08X", style.textColor),
            "sub-back-color" to String.format(java.util.Locale.US, "#%08X", effectiveBackgroundColor),
            "sub-border-color" to String.format(java.util.Locale.US, "#%08X", effectiveBorderColor),
            "sub-border-size" to if (style.outlineEnabled) effectiveBorderSize.toString() else "0",
            "sub-border-style" to effectiveBorderStyle,
            "sub-shadow-offset" to if (style.outlineEnabled) effectiveShadowOffset.toString() else "0",
            "sub-pos" to finalPos.toString(),
            "sub-justify" to effectiveJustification,
            "sub-ass-justify" to if (effectiveOverrideAss) "yes" else "no",
            "sub-ass-override" to if (effectiveOverrideAss) "force" else "no"
        )
        options.forEach { (name, value) ->
            runCatching { view.mpv.setPropertyString(name, value) }
                .onFailure { Log.w(TAG, "Subtitle option $name: ${it.message}") }
        }
    }

    override fun setResizeMode(resizeMode: Int) {
        val aspectMode = when (resizeMode) {
            0 -> AspectMode.ORIGINAL
            4 -> AspectMode.FULL_SCREEN
            3 -> AspectMode.STRETCH
            else -> AspectMode.ORIGINAL
        }
        mpvView?.applyAspectMode(aspectMode)
    }

    override fun setAspectMode(mode: com.kitsugi.animelist.core.player.PlayerAspectMode) {
        val aspectProp = com.kitsugi.animelist.core.player.PlayerAspectScaleUtils.getMpvAspectProperty(mode)
        runCatching {
            mpvView?.mpv?.setPropertyString("video-aspect-override", aspectProp)
        }
        val mpvAspectMode = when (mode) {
            com.kitsugi.animelist.core.player.PlayerAspectMode.ORIGINAL -> AspectMode.ORIGINAL
            com.kitsugi.animelist.core.player.PlayerAspectMode.FIT -> AspectMode.ORIGINAL
            com.kitsugi.animelist.core.player.PlayerAspectMode.FILL -> AspectMode.STRETCH
            com.kitsugi.animelist.core.player.PlayerAspectMode.ZOOM -> AspectMode.FULL_SCREEN
            com.kitsugi.animelist.core.player.PlayerAspectMode.CROP_16_9 -> AspectMode.FULL_SCREEN
            com.kitsugi.animelist.core.player.PlayerAspectMode.CROP_4_3 -> AspectMode.FULL_SCREEN
        }
        mpvView?.applyAspectMode(mpvAspectMode)
    }

    override fun handleKey(event: android.view.KeyEvent): Boolean {
        if (mpvView == null || android.view.KeyEvent.isModifierKey(event.keyCode) ||
            event.action == android.view.KeyEvent.ACTION_MULTIPLE) return false
        val key = when (event.keyCode) {
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> "LEFT"
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> "RIGHT"
            android.view.KeyEvent.KEYCODE_DPAD_UP -> "UP"
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> "DOWN"
            android.view.KeyEvent.KEYCODE_ENTER, android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> "ENTER"
            android.view.KeyEvent.KEYCODE_SPACE -> "SPACE"
            android.view.KeyEvent.KEYCODE_TAB -> "TAB"
            android.view.KeyEvent.KEYCODE_DEL -> "BS"
            android.view.KeyEvent.KEYCODE_FORWARD_DEL -> "DEL"
            android.view.KeyEvent.KEYCODE_INSERT -> "INS"
            android.view.KeyEvent.KEYCODE_MOVE_HOME -> "HOME"
            android.view.KeyEvent.KEYCODE_MOVE_END -> "END"
            android.view.KeyEvent.KEYCODE_PAGE_UP -> "PGUP"
            android.view.KeyEvent.KEYCODE_PAGE_DOWN -> "PGDWN"
            android.view.KeyEvent.KEYCODE_ESCAPE -> "ESC"
            android.view.KeyEvent.KEYCODE_F1 -> "F1"
            android.view.KeyEvent.KEYCODE_F2 -> "F2"
            android.view.KeyEvent.KEYCODE_F3 -> "F3"
            android.view.KeyEvent.KEYCODE_F4 -> "F4"
            android.view.KeyEvent.KEYCODE_F5 -> "F5"
            android.view.KeyEvent.KEYCODE_F6 -> "F6"
            android.view.KeyEvent.KEYCODE_F7 -> "F7"
            android.view.KeyEvent.KEYCODE_F8 -> "F8"
            android.view.KeyEvent.KEYCODE_F9 -> "F9"
            android.view.KeyEvent.KEYCODE_F10 -> "F10"
            android.view.KeyEvent.KEYCODE_F11 -> "F11"
            android.view.KeyEvent.KEYCODE_F12 -> "F12"
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "PLAYPAUSE"
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> "PLAY"
            android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> "PAUSE"
            android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> "NEXT"
            android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "PREV"
            android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> "FORWARD"
            android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> "REWIND"
            else -> {
                val code = event.unicodeChar
                if (!event.isPrintingKey || code == 0 || code and android.view.KeyCharacterMap.COMBINING_ACCENT != 0) return false
                String(Character.toChars(code))
            }
        }
        if (event.repeatCount > 0) return true
        val modifiers = buildList {
            if (event.isShiftPressed) add("shift")
            if (event.isCtrlPressed) add("ctrl")
            if (event.isAltPressed) add("alt")
            if (event.isMetaPressed) add("meta")
            add(key)
        }.joinToString("+")
        executeCommand(arrayOf(if (event.action == android.view.KeyEvent.ACTION_DOWN) "keydown" else "keyup", modifiers))
        return true
    }

    override fun selectTrack(trackOption: TrackOption) {
        var applied = true
        // KULLANICI elle seçti → ilk otomatik parça seçimi bir daha devreye girmemeli.
        // Aksi hâlde hemen ardından çalışan `updateTracks()` (harici altyazılar henüz
        // "hazır" sayılmadıysa `initialSelectionDone` hâlâ false olduğu için) otomatik
        // tercihi uygulayıp kullanıcının seçimini geri alabiliyordu — kullanıcının
        // "3 ses seçeneği hiçbir şey değiştirmiyor" gözleminin olası nedenlerinden biri.
        initialSelectionDone = true
        selectionForced = true
        userChangedTracks = true
        if (trackOption.groupIndex == 0) {
            applied = mpvView?.selectAudioTrackById(trackOption.trackIndex) ?: false
        } else if (trackOption.groupIndex == 1) {
            if (trackOption.trackIndex == secondarySubtitleTrackId) selectSecondarySubtitleTrack(-1)
            applied = mpvView?.selectSubtitleTrackById(trackOption.trackIndex) ?: false
            _isSubtitleDisabled = false
        }
        if (!applied) {
            Log.w(TAG, "Parça seçimi uygulanamadı: label='${trackOption.label}' id=${trackOption.trackIndex}")
            com.kitsugi.animelist.data.cloudstream.diag.CsTrace.warn(
                "Player",
                "track",
                "Ses/altyazı parçası uygulanamadı: ${trackOption.label} (id=${trackOption.trackIndex})"
            )
        }
        scheduleUpdateTracks()
        // mpv özelliği asenkron uygular; kısa bir gecikmeyle listede GERÇEK seçimi tazele
        // (aksi hâlde arayüz eski seçimi göstermeye devam ediyor ve "hiçbir şey değişmiyor"
        // izlenimi doğuyordu).
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            scheduleUpdateTracks()
        }, 350L)
    }

    override fun disableSubtitles() {
        initialSelectionDone = true
        selectionForced = true
        userChangedTracks = true
        selectSecondarySubtitleTrack(-1)
        mpvView?.disableSubtitles()
        _isSubtitleDisabled = true
        scheduleUpdateTracks()
    }

    override fun createVideoView(context: Context): View {
        if (mpvView == null) {
            mpvView = KitsugiMpvSurfaceView(context).apply {
                this@MpvPlayerEngine.mpvView = this
                ensureInitialized()

                // Tüm gözlenen özellikleri tek seferinde kaydet (Aniyomi modeli)
                mpv.addObserver(this@MpvPlayerEngine)
                observedProps.forEach { (prop, format) ->
                    mpv.observeProperty(prop, format)
                }

                // Gelişmiş init seçenekleri uygula
                applyInitOptions(this)

                val pendingUrl = videoUrl
                if (!pendingUrl.isNullOrBlank()) {
                    beginLoad(pendingUrl, pendingAudioTracks, pendingHeaders, pendingStartPositionMs)
                    setMedia(pendingUrl, pendingHeaders, pendingStartPositionMs)
                    engineScope.launch {
                        setupCustomButtons()
                    }
                }
            }
        }
        return mpvView!!
    }

    override fun release() {
        // Motor kapanırken bekleyen script/custom-button kurulum coroutine'leri de iptal edilir.
        released = true
        loadGeneration++
        // Bekleyen birleştirilmiş parça listesi güncellemesi varsa iptal et:
        // aksi hâlde serbest bırakılmış mpv örneğine JNI üzerinden erişilebilir.
        runCatching { mainHandler.removeCallbacks(tracksUpdateRunnable) }
        tracksUpdatePending = false
        focusController?.release()
        focusController = null
        engineScope.cancel()
        runCatching { mpvView?.mpv?.removeObserver(this) }
        runCatching { mpvView?.releasePlayer() }
        mpvView = null
        listeners.clear()
    }

    // ──── MPV.EventObserver callbacks ────────────────────────────────────────

    override fun eventProperty(property: String) = onMain {
        when (property) {
            "track-list" -> scheduleUpdateTracks()
            "chapter-list" -> updateChapters()
        }
    }

    override fun eventProperty(property: String, value: Boolean) = onMain {
        when (property) {
            "pause" -> {
                if (isPlaying != !value) {
                    isPlaying = !value
                    listeners.forEach { it.onPlayingChanged(isPlaying) }
                }
                checkBufferingState()
            }
            "paused-for-cache" -> {
                pausedForCache = value
                checkBufferingState()
            }
            "core-idle" -> {
                coreIdle = value
                checkBufferingState()
            }
            "seeking" -> {
                isSeeking = value
                if (value) {
                    updateState(PlayerEngine.State.BUFFERING)
                } else {
                    checkBufferingState()
                }
            }
            "eof-reached" -> {
                if (value && !awaitingStartFile) {
                    Log.d(TAG, "eof-reached=true → State.ENDED")
                    updateState(PlayerEngine.State.ENDED)
                }
            }
            "sub-visibility" -> {
                _isSubtitleDisabled = !value
            }
        }
    }

    override fun eventProperty(property: String, value: Double) = onMain {
        when (property) {
            "time-pos" -> {
                if (awaitingStartFile) return@onMain
                val posMs = (value * 1000).toLong().coerceAtLeast(0L)
                if (posMs != currentPosition) {
                    currentPosition = posMs
                    notifyPositionChanged()
                }
            }
            "demuxer-cache-time" -> {
                if (value.isFinite()) {
                    bufferedPosition = (value * 1000).toLong().coerceAtLeast(0)
                    listeners.forEach { it.onBufferedPositionChanged(bufferedPosition) }
                }
            }
            "duration" -> {
                if (awaitingStartFile) return@onMain
                val durMs = (value * 1000).toLong().coerceAtLeast(0L)
                if (durMs != duration) {
                    duration = durMs
                    notifyPositionChanged(force = true)
                }
            }
            "speed" -> {
                _currentSpeed = value.toFloat()
            }
            "volume" -> {
                val mpvVol = (value / 100.0).toFloat()
                if (_currentVolume <= 1.0f || mpvVol < 1.0f) {
                    _currentVolume = mpvVol
                }
            }
        }
    }

    override fun eventProperty(property: String, value: Long) = onMain {
        when (property) {
            // "sid" ve "aid" MPV_FORMAT_STRING olarak gözleniyor (observedProps) →
            // aşağıdaki String overload'unda işlenir. Burada da yakalanmaları aynı
            // değişiklik için parça listesini iki kez yenilemeye yol açıyordu.
            "secondary-sid" -> {
                secondarySubtitleTrackId = value.toInt()
            }
            "video-params/w", "video-params/h" -> {
                // Video boyutları değişti — streamInfo güncellenecek
            }
            "chapter" -> updateChapters()
            "user-data/current-anime/intro-length" -> {
                listeners.forEach { it.onEngineEvent(property, value.toString()) }
            }
        }
    }

    override fun eventProperty(property: String, value: String) = onMain {
        when (property) {
            "hwdec-current" -> {
                activeHwdecMode = value
                Log.d(TAG, "hwdec-current=$value")
            }
            "sid", "aid" -> scheduleUpdateTracks()
            "secondary-sid" -> {
                secondarySubtitleTrackId = value.toIntOrNull() ?: -1
                listeners.forEach { it.onSecondarySubtitleChanged(secondarySubtitleTrackId) }
            }
            in MpvPlaybackPolicy.scriptProperties -> {
                if (value.isNotEmpty()) listeners.forEach { it.onEngineEvent(property, value) }
            }
        }
    }

    override fun eventProperty(property: String, value: MPVNode) {}

    override fun event(eventId: Int, data: MPVNode) = onMain {
        when (eventId) {
            MPV.mpvEvent.MPV_EVENT_START_FILE -> {
                awaitingStartFile = false
                fileLoaded = false
            }
            MPV.mpvEvent.MPV_EVENT_FILE_LOADED -> {
                if (awaitingStartFile) return@onMain
                Log.d(TAG, "MPV_EVENT_FILE_LOADED")
                fileLoaded = true
                setPlaying(audioFocus().request())
                updateChapters()
                // The surface may not have existed when prepare queued loadfile.
                // Add tracks only once the new file is actually open (not to the old file).
                mpvView?.let { view ->
                    streamTitle?.takeIf { it.isNotBlank() }?.let {
                        view.mpv.setPropertyString("force-media-title", it)
                        view.mpv.setPropertyString("user-data/current-anime/episode-title", it)
                    }
                    applyExternalAudio(view, pendingAudioTracks)
                    view.applySubtitleLanguagePreferences(settings.preferredSubtitleLanguages, null)
                    applySecondarySubtitle(view)
                }
                setSubtitleStyle(activeSubtitleStyle)
                setStatisticsPage(settings.playerStatisticsPage)
                checkBufferingState()
                scheduleUpdateTracks()
                // v2.4.369 HANG düzeltmesi: harici altyazılar eskiden burada, ANA iş parçacığında
                // uzak URL ile `sub-add` yapılarak (her biri ağdan senkron indirilerek) ekleniyordu
                // → 2,5–5 sn donma. Artık önce IO'da önbelleğe indirilip yerel dosya olarak eklenir;
                // zorunlu seçim zamanlayıcısı yükleme bittikten sonra başlar.
                loadExternalSubtitlesAsync(pendingSubtitles, loadGeneration, select = true)
            }
            MPV.mpvEvent.MPV_EVENT_END_FILE -> {
                Log.d(TAG, "MPV_EVENT_END_FILE")
                handleEndFile()
            }
        }
    }

    // ──── Internal helpers ────────────────────────────────────────────────────

    // ── Dolby Vision: vo / hwdec planı ────────────────────────────────────────

    /** Aktif akış + ayarlar için DV planını hesaplar (gpu-next desteği bir kez ölçülür). */
    private fun currentDvPlan(view: KitsugiMpvSurfaceView): MpvDolbyVisionOptions {
        val hint = dvHint
        val autoFix = settings.mpvDolbyVisionAutoFix
        // DV değilse veya düzeltme kapalıysa pahalı `vo` yoklaması hiç yapılmaz:
        // her açılışta video çıkışını yeniden kurmak istemiyoruz.
        if (hint == null || !autoFix) {
            return MpvDolbyVisionPolicy.resolve(
                hint = hint,
                autoFixEnabled = autoFix,
                configuredVo = settings.mpvGpuRenderer.ifBlank { "gpu" },
                configuredHwdec = settings.mpvHwdecMode.ifBlank { "auto-safe" },
                gpuNextAvailable = false
            )
        }
        val available = gpuNextSupported
            ?: probeGpuNextSupport(view).also { gpuNextSupported = it }
        return MpvDolbyVisionPolicy.resolve(
            hint = hint,
            autoFixEnabled = true,
            configuredVo = settings.mpvGpuRenderer.ifBlank { "gpu" },
            configuredHwdec = settings.mpvHwdecMode.ifBlank { "auto-safe" },
            gpuNextAvailable = available
        )
    }

    /**
     * `vo=gpu-next` bu libmpv derlemesinde gerçekten kullanılabilir mi?
     *
     * Seçenek yazılabiliyor ve geri okunabiliyorsa var kabul ederiz; hemen
     * ardından kullanıcının önceki seçimini geri yazarız (henüz dosya
     * yüklenmediği için VO'nun yeniden kurulması zararsızdır).
     */
    private fun probeGpuNextSupport(view: KitsugiMpvSurfaceView): Boolean =
        runCatching {
            val previous = view.mpv.getPropertyString("vo")
            try {
                view.mpv.setPropertyString("vo", MpvDolbyVisionPolicy.VO_GPU_NEXT)
                val applied = view.mpv.getPropertyString("vo")
                previous?.takeIf { it.isNotBlank() }?.let { view.mpv.setPropertyString("vo", it) }
                applied?.contains(MpvDolbyVisionPolicy.VO_GPU_NEXT, ignoreCase = true) == true
            } catch (t: Throwable) {
                runCatching {
                    previous?.takeIf { it.isNotBlank() }?.let { view.mpv.setPropertyString("vo", it) }
                }
                Log.w(TAG, "gpu-next probe failed: ${t.message}")
                false
            }
        }.getOrDefault(false)

    /**
     * DV planını vo/hwdec seçeneklerine uygular.
     *
     * İlk kurulumdan önce yalnızca çözülen değerleri hazırlar (yazmayı
     * [applyInitOptions] yapar); sonrasında plan değiştiyse anında yazar.
     * Böylece DV içerikten normal içeriğe geçerken kullanıcının kendi vo/hwdec
     * seçimlerine dönülür.
     */
    private fun syncDolbyVisionOptions(view: KitsugiMpvSurfaceView) {
        val plan = currentDvPlan(view)
        resolvedVoOption = plan.vo ?: settings.mpvGpuRenderer.ifBlank { "gpu" }
        resolvedHwdecOption = plan.hwdec ?: settings.mpvHwdecMode.ifBlank { "auto-safe" }

        Log.i(
            TAG,
            "DV_PLAN: ${plan.reason} vo=${plan.vo ?: "keep"} hwdec=${plan.hwdec ?: "keep"} " +
                "gpuNext=${gpuNextSupported}"
        )

        if (!initOptionsApplied) return
        if (plan.key == appliedDvPlanKey) return
        appliedDvPlanKey = plan.key

        val vo = resolvedVoOption ?: settings.mpvGpuRenderer.ifBlank { "gpu" }
        runCatching { view.mpv.setPropertyString("vo", vo) }
            .onFailure { Log.w(TAG, "DV vo uygulanamadı: ${it.message}") }
        resolvedHwdecOption?.let { hwdec ->
            runCatching { view.mpv.setPropertyString("hwdec", hwdec) }
                .onFailure { Log.w(TAG, "DV hwdec uygulanamadı: ${it.message}") }
        }

        // DV var ama bu derlemede gpu-next yok → kullanıcıya tek seferlik bilgi.
        if (plan.reason == "gpu-next-unavailable" && !dvWarningShownForLoad) {
            dvWarningShownForLoad = true
            runCatching {
                view.mpv.command(
                    "show-text",
                    "Dolby Vision: bu oynatıcı derlemesinde gpu-next yok - renkler bozuk görünebilir.",
                    "6000"
                )
            }
        }
    }

    private fun String.safeHost(): String =
        runCatching { android.net.Uri.parse(this).host ?: "unknown" }.getOrDefault("unknown")

    /**
     * Aniyomi'den uyarlama: Tüm MPV başlatma seçeneklerini ayarla.
     * GPU renderer, hwdec, debanding, demuxer cache, volume-max gibi gelişmiş ayarlar.
     */
    private fun applyInitOptions(view: KitsugiMpvSurfaceView) {
        // DV planı vo/hwdec'i belirleyebilir; tek seferlik bloktan ÖNCE hesapla.
        syncDolbyVisionOptions(view)
        // Isolate writes: an unavailable option in a libmpv build must not prevent
        // speed, decoder, cache and volume settings from being applied.
        fun option(name: String, value: String) {
            runCatching { view.mpv.setPropertyString(name, value) }
                .onFailure { Log.w(TAG, "MPV option $name could not be applied: ${it.message}") }
        }
        // ── Her yüklemede güvenle yeniden uygulanabilenler ────────────────────
        // Ayarlar kullanıcı tarafından değiştirilebildiği için bunlar her prepare'da
        // tazelenir; hiçbiri görüntü çıkışını yeniden kurmaz.
        option("speed", _currentSpeed.toString())
        val deband = if (settings.mpvDebandMode == "gpu") "yes" else "no"
        if (appliedDeband != deband) {
            runCatching { view.mpv.setPropertyString("deband", deband) }
                .onSuccess { appliedDeband = deband }
                .onFailure { Log.w(TAG, "MPV option deband could not be applied: ${it.message}") }
        }
        val filters = MpvPlaybackPolicy.videoFilters(settings.mpvDebandMode, settings.mpvForceYuv420p)
        if (appliedVideoFilters != filters) {
            runCatching { view.mpv.setPropertyString("vf", filters) }
                .onSuccess { appliedVideoFilters = filters }
                .onFailure { Log.w(TAG, "MPV option vf could not be applied: ${it.message}") }
        }
        val renderer = resolvedVoOption ?: settings.mpvGpuRenderer.ifBlank { "gpu" }
        if (appliedRenderer != renderer) {
            runCatching { view.mpv.setPropertyString("vo", renderer) }
                .onSuccess { appliedRenderer = renderer }
                .onFailure { Log.w(TAG, "MPV option vo could not be applied: ${it.message}") }
        }
        val hwdec = resolvedHwdecOption ?: settings.mpvHwdecMode.ifBlank { "auto-safe" }
        if (appliedHwdec != hwdec) {
            runCatching { view.mpv.setPropertyString("hwdec", hwdec) }
                .onSuccess { appliedHwdec = hwdec }
                .onFailure { Log.w(TAG, "MPV option hwdec could not be applied: ${it.message}") }
        }
        option("volume-max", settings.volumeBoostCap.coerceIn(100, 200).toString())
        option("alang", settings.preferredAudioLanguages)
        option("audio-pitch-correction", if (settings.audioPitchCorrection) "yes" else "no")
        // Kalite profili → HLS bit hızı seçimi. Aksi hâlde ayarlardaki "720p / Veri tasarrufu"
        // tercihi MPV'de hiçbir şey yapmıyordu.
        val qualityProfile = com.kitsugi.animelist.core.player.QualityProfile
            .deserialize(settings.qualityProfileJson)
        val hlsBitrate = when (qualityProfile.preference) {
            com.kitsugi.animelist.core.player.QualityPreference.DATA_SAVER,
            com.kitsugi.animelist.core.player.QualityPreference.P480 -> "min"
            com.kitsugi.animelist.core.player.QualityPreference.P1080 -> "max"
            else -> "auto"
        }
        option("hls-bitrate", hlsBitrate)
        val channels = settings.audioChannels
        option("audio-channels", if (channels == com.kitsugi.animelist.ui.screens.fullscreen.AudioChannels.ReverseStereo) "stereo" else channels.value)
        option("af", if (channels == com.kitsugi.animelist.ui.screens.fullscreen.AudioChannels.ReverseStereo) channels.value else "")
        // Aniyomi VideoFilters: tum filtreler baslangicta uygulanir (brightness/saturation/contrast/gamma/hue)
        runCatching {
            val json = org.json.JSONObject(settings.videoFiltersJson)
            com.kitsugi.animelist.ui.screens.fullscreen.KitsugiVideoFilters.entries.forEach { filter ->
                val v = json.optInt(filter.mpvProperty, 0)
                if (v != 0) view.mpv.setPropertyInt(filter.mpvProperty, v.coerceIn(-100, 100))
            }
        }

        // ── Görüntü/ses çıkışını kuranlar: YALNIZCA BİR KEZ ───────────────────
        // `vo` çalışma anında yazıldığında libmpv video çıkışını baştan kurar. Bu blok
        // hem createVideoView hem prepare() tarafından çağrıldığı için her videoda VO iki
        // kez kuruluyordu; bu da küçük oynatıcı açılırken siyah kare / ilk saniyelerde
        // donma olarak görünüyordu. Motor ömrü boyunca bir kez uygulanır.
        if (initOptionsApplied) return
        initOptionsApplied = true
        option("vo", resolvedVoOption ?: settings.mpvGpuRenderer.ifBlank { "gpu" })
        option("hwdec", resolvedHwdecOption ?: settings.mpvHwdecMode.ifBlank { "auto-safe" })
        appliedDvPlanKey = currentDvPlan(view).key
        if (settings.mpvDemuxerCacheMb > 0) {
            option("demuxer-max-bytes", "${settings.mpvDemuxerCacheMb}MiB")
            option("demuxer-max-back-bytes", "${(settings.mpvDemuxerCacheMb / 4).coerceAtLeast(8)}MiB")
        }
        option("cache", "yes")
        option("network-timeout", "30")
        // Aniyomi paritesi: ek MPV init secenekleri
        option("profile", "fast")
        option("ytdl", "no")
        option("keep-open", "yes")
        option("input-default-bindings", "yes")
        option("vd-lavc-film-grain", "cpu")
        option("msg-level", "all=warn")
        runCatching {
            val screenshotDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
            screenshotDir.mkdirs()
            view.mpv.setPropertyString("screenshot-directory", screenshotDir.path)
        }

        // Aniyomi AdvancedPlayerPreferences: kullanici mpv.conf / input.conf
        // Dosyalari desteklensin (mpvScriptsEnabled true ise yuzde yuz uyum)
        runCatching {
            val mpvConfDir = java.io.File(context.filesDir, "mpv")
            // mpv.conf: her satir `option=value` seklinde mpv init secenegi olarak uygulanir
            val confFile = java.io.File(mpvConfDir, "mpv.conf")
            if (confFile.exists() && confFile.isFile) {
                confFile.readLines().forEach { raw ->
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("#")) return@forEach
                    val eq = line.indexOf("=")
                    if (eq > 0) {
                        val key = line.substring(0, eq).trim()
                        val value = line.substring(eq + 1).trim().removeSurrounding("\"").removeSurrounding("'")
                        if (key.isNotEmpty()) option(key, value)
                    }
                }
            }
            // input.conf: mpv key binding dosyasi — yalnizca varsa bildir (libmpv native yukler)
            val inputFile = java.io.File(mpvConfDir, "input.conf")
            if (inputFile.exists()) {
                android.util.Log.d(TAG, "MPV input.conf bulundu: ${inputFile.path}")
            }
            // AppSettings mpvConf string'i (kullanici metin kutusundan) — satir satir uygula
            val confString = settings.mpvConf
            if (confString.isNotBlank()) {
                confString.lines().forEach { raw ->
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("#")) return@forEach
                    val eq = line.indexOf("=")
                    if (eq > 0) {
                        val key = line.substring(0, eq).trim()
                        val value = line.substring(eq + 1).trim().removeSurrounding("\"").removeSurrounding("'")
                        if (key.isNotEmpty()) option(key, value)
                    } else if (line.contains(" ")) {
                        // flag form: e.g. "no-video"
                        option(line, "yes")
                    }
                }
            }
            val inputString = settings.mpvInput
            if (inputString.isNotBlank()) {
                android.util.Log.d(TAG, "MPV mpvInput ozel baglamalari var (${inputString.length} char)")
            }
        }

        // TLS / CA güven zinciri — Asla trust-all yok.
        // AndroidCAStore'dan PEM'e çıkarılan CA paketi MPV'ye tls-ca-file olarak verilir.
        val caFile = com.kitsugi.animelist.core.player.MpvTrustStore.install(context)
        applyMpvTlsOptions(caFile.absolutePath) { name, value ->
            option(name, value)
        }
    }

    /** İkincil altyazı seçimini MPV'ye uygula */
    private fun applySecondarySubtitle(view: KitsugiMpvSurfaceView) {
        val sidValue = if (secondarySubtitleTrackId >= 0) secondarySubtitleTrackId.toString() else "no"
        view.mpv.setPropertyString("secondary-sid", sidValue)
        view.mpv.setPropertyDouble("secondary-sub-delay", secondarySubtitleDelayMs / 1000.0)
    }

    private fun updateChapters() {
        val view = mpvView ?: return
        val chapters = (0 until (view.mpv.getPropertyInt("chapter-list/count") ?: 0).coerceIn(0, 10000)).mapNotNull { i ->
            val seconds = view.mpv.getPropertyDouble("chapter-list/$i/time") ?: return@mapNotNull null
            if (!seconds.isFinite()) return@mapNotNull null
            com.kitsugi.animelist.core.player.PlayerChapter(
                view.mpv.getPropertyString("chapter-list/$i/title") ?: "Chapter ${i + 1}",
                (seconds * 1000).toLong()
            )
        }
        val normalized = com.kitsugi.animelist.core.player.PlayerChapterPolicy.normalize(chapters, duration)
        listeners.forEach { it.onChaptersChanged(normalized) }
    }

    /** Yeni bir medya yüklenmeden önce yükleme durumunu hazırlar. */
    private fun beginLoad(
        url: String,
        audioTracks: List<com.kitsugi.animelist.core.player.ExternalAudioTrackInput>,
        headers: Map<String, String>,
        startPositionMs: Long
    ) {
        // Every explicit prepare is a real load, including retry of the exact same URL.
        loadGeneration++
        awaitingStartFile = true
        fileLoaded = false
        errorReportedForLoad = false
        selectionForced = false
        initialSelectionDone = false
        userChangedTracks = false
        currentPosition = startPositionMs.coerceAtLeast(0L)
        duration = 0L
        bufferedPosition = 0L
        pausedForCache = false
        coreIdle = false
        isSeeking = false
        secondarySubtitleTrackId = -1
        listeners.forEach {
            it.onChaptersChanged(emptyList())
            it.onBufferedPositionChanged(0)
            it.onSecondarySubtitleChanged(-1)
        }
        hasExternalAudio = audioTracks.isNotEmpty()
        lastPositionNotifyAtMs = -1L
        notifyPositionChanged(force = true)
    }

    /** Preserve the legacy URL and every CloudStream rendition as selectable MPV tracks. */
    private fun mergeAudioTracks(
        audioUrl: String?,
        tracks: List<com.kitsugi.animelist.core.player.ExternalAudioTrackInput>
    ): List<com.kitsugi.animelist.core.player.ExternalAudioTrackInput> {
        val cleanTracks = tracks.filter { it.url.isNotBlank() }.distinctBy { it.url }
        val legacy = audioUrl?.takeIf { it.isNotBlank() && cleanTracks.none { track -> track.url == it } }
            ?.let { com.kitsugi.animelist.core.player.ExternalAudioTrackInput(url = it, name = "Harici ses") }
        return listOfNotNull(legacy) + cleanTracks
    }

    /** Add all renditions; select the provider's first track and keep the rest user-selectable. */
    private fun applyExternalAudio(
        view: KitsugiMpvSurfaceView,
        tracks: List<com.kitsugi.animelist.core.player.ExternalAudioTrackInput>
    ) {
        if (tracks.isEmpty()) return
        tracks.forEachIndexed { index, track ->
            val flags = if (index == 0) "select" else "no"
            val label = track.name?.takeIf { it.isNotBlank() } ?: "Harici ses ${index + 1}"
            if (track.headers.any { (key, value) ->
                    pendingHeaders.entries.none { (videoKey, videoValue) ->
                        videoKey.equals(key, ignoreCase = true) && videoValue == value
                    }
                }
            ) {
                Log.w(TAG, "MPV audio-add cannot apply headers unique to external audio track '${label}'; Media3 supports per-track headers")
            }
            runCatching {
                view.mpv.command("audio-add", track.url, flags, label, track.lang.orEmpty())
            }.onFailure {
                Log.w(TAG, "audio-add başarısız ($label): ${it.message}")
            }
        }
    }

    /**
     * MPV_EVENT_END_FILE işleyici. Dosya açılamazsa veya oynatma EOF'a ulaşmadan
     * beklenmeyen şekilde kesilirse hata bildirir; PlayerErrorRecoveryController
     * böylece yedek dahili oynatıcıya (MEDIA3) geçebilir.
     */
    private fun handleEndFile() {
        // mpv orders END_FILE(old) before START_FILE(new). An old END_FILE while
        // the replacement is queued must not fail the new request, even if old never loaded.
        if (awaitingStartFile || released) return
        val wasLoaded = fileLoaded
        fileLoaded = false
        val reachedEof = runCatching { mpvView?.mpv?.getPropertyBoolean("eof-reached") == true }
            .getOrDefault(false)
        if (reachedEof || errorReportedForLoad) return

        errorReportedForLoad = true
        val code = if (wasLoaded) 9002 else 9001
        val msg = if (wasLoaded) {
            "MPV oynatma sırasında beklenmeyen şekilde durdu"
        } else {
            "MPV dosyayı açamadı (kaynak erişilemiyor veya format desteklenmiyor)"
        }
        Log.e(TAG, "$msg (code=$code)")
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            listeners.forEach { it.onPlaybackError(code, msg, null) }
        }
    }

    private fun updateState(newState: PlayerEngine.State) {
        if (currentState != newState) {
            currentState = newState
            listeners.forEach { it.onStateChanged(newState) }
        }
    }

    private fun checkBufferingState() {
        // An initial pause=false notification does not mean a file has loaded.
        // Nor may a pause event overwrite ENDED while keep-open holds the last frame.
        if (!fileLoaded || currentState == PlayerEngine.State.ENDED) return
        if (MpvPlaybackPolicy.isReady(fileLoaded, pausedForCache, coreIdle, isPlaying, isSeeking)) {
            updateState(PlayerEngine.State.READY)
        } else {
            updateState(PlayerEngine.State.BUFFERING)
        }
    }

    private fun notifyPositionChanged() {
        notifyPositionChanged(force = false)
    }

    /**
     * MPV, `time-pos` özelliğini her karede değiştirir; dinleyicilere olduğu gibi
     * iletildiğinde küçük (inline) oynatıcı saniyede ~60 kez Compose durumu yazıyor,
     * bu da detay sayfasını kaydırırken görünen "kasıyor / donuyor" tablosunun ana
     * kaynağı oluyordu. Arayüz için 4 Hz yeterlidir; seek ve yükleme gibi anlık
     * geri bildirim gereken yerler [force] = true ile anında iletilir.
     */
    private fun notifyPositionChanged(force: Boolean) {
        if (released) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && lastPositionNotifyAtMs >= 0L && now - lastPositionNotifyAtMs < POSITION_NOTIFY_INTERVAL_MS) return
        lastPositionNotifyAtMs = now
        listeners.forEach { it.onPositionChanged(currentPosition, duration) }
    }

    private fun performUpdateTracks() {
        // Motor serbest bırakıldıysa mpv artık yok — JNI çağrısı yapılmaz.
        if (released) return
        val view = mpvView ?: return
        val snapshot = view.readTrackSnapshot()
        val dummyGroup = createDummyGroup()

        val audioOptions = snapshot.audioTracks.map { track ->
            TrackOption(
                group = dummyGroup,
                groupIndex = 0,
                trackIndex = track.id,
                label = track.name,
                isSelected = track.isSelected,
                audioLanguage = track.language,
                audioName = track.title,
                audioCodec = track.codec,
                audioBitrate = track.bitrate,
                audioSource = track.externalFilename,
                audioIsExternal = track.isExternal,
                audioOriginKnown = true,
                audioMediaUrl = videoUrl
            )
        }

        val subtitleOptions = snapshot.subtitleTracks.map { track ->
            TrackOption(
                group = dummyGroup,
                groupIndex = 1,
                trackIndex = track.id,
                label = track.name,
                isSelected = track.isSelected
            )
        }

        // Harici altyazılar MPV track-list'inde kendi "external" bayrağıyla gelir: isim tahminine gerek yok.
        val expectedExternalSubs = preparedSubtitles.count { it.isExternal }
        val loadedExternalSubs = snapshot.subtitleTracks.count { it.isExternal }
        val externalSubsReady = loadedExternalSubs >= expectedExternalSubs || selectionForced

        // ── İlk otomatik parça seçimi — yalnızca yeni medya yüklendiğinde ve harici altyazılar hazırken ──
        if (!initialSelectionDone && fileLoaded && externalSubsReady &&
            (snapshot.audioTracks.isNotEmpty() || snapshot.subtitleTracks.isNotEmpty())
        ) {
            initialSelectionDone = true
            val preferredLangs = settings.preferredSubtitleLanguages
                .split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }

            val audioLanguages = settings.preferredAudioLanguages.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (!hasExternalAudio) {
                val bestAudio = audioLanguages.firstNotNullOfOrNull { lang ->
                    snapshot.audioTracks.firstOrNull { com.kitsugi.animelist.core.player.PlayerSubtitleUtils.matchesTrackLanguage(it.language, it.name, lang) }
                }
                if (bestAudio != null && !bestAudio.isSelected) view.selectAudioTrackById(bestAudio.id)
            }

            // ── Altyazı: Tek ortak politika (SubtitleSelectionPolicy) — Media3 ile birebir aynı ──
            // 1. Videonun içindeki Türkçe  2. Harici Türkçe  3. Diğer tercih dilleri  4. Yoksa kapat
            if (!_isSubtitleDisabled && snapshot.subtitleTracks.isNotEmpty()) {
                val currentlySelectedSub = snapshot.subtitleTracks.firstOrNull { it.isSelected }
                val isSelectedTurkishSource = currentlySelectedSub != null &&
                    !currentlySelectedSub.isExternal &&
                    com.kitsugi.animelist.core.player.PlayerSubtitleUtils.isTurkish(currentlySelectedSub.language, currentlySelectedSub.name)
                if (!isSelectedTurkishSource || settings.subtitleBlacklist.isNotBlank() || settings.subtitleWhitelist.isNotBlank()) {
                    val denied = settings.subtitleBlacklist.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val favored = settings.subtitleWhitelist.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val candidates = snapshot.subtitleTracks
                        .filterNot { track -> denied.any { track.name.contains(it, ignoreCase = true) } }
                        .sortedByDescending { track -> favored.any { track.name.contains(it, ignoreCase = true) } }
                        .map { track ->
                        com.kitsugi.animelist.core.player.SubtitleSelectionPolicy.Candidate(
                            item = track,
                            lang = track.language,
                            label = track.name,
                            isExternal = track.isExternal,
                        )
                    }
                    val bestSub = com.kitsugi.animelist.core.player.SubtitleSelectionPolicy.pick(candidates, preferredLangs)
                    if (bestSub != null) {
                        if (currentlySelectedSub?.id != bestSub.id) {
                            Log.i(TAG, "Auto-selecting subtitle track: ${bestSub.name} (id=${bestSub.id})")
                            view.selectSubtitleTrackById(bestSub.id)
                        }
                    } else {
                        Log.i(TAG, "No Turkish/preferred subtitle found. Disabling non-preferred track: ${currentlySelectedSub?.name}")
                        view.disableSubtitles()
                    }
                }
            }
        }

        listeners.forEach { it.onTracksChanged(audioOptions, subtitleOptions) }
    }

    private fun createDummyGroup(): androidx.media3.common.Tracks.Group {
        val format = androidx.media3.common.Format.Builder().build()
        val trackGroup = androidx.media3.common.TrackGroup(format)
        return androidx.media3.common.Tracks.Group(
            trackGroup,
            false,
            intArrayOf(androidx.media3.common.C.FORMAT_HANDLED),
            booleanArrayOf(false)
        )
    }

    override suspend fun takeScreenshot(cachePath: String, showSubtitles: Boolean): java.io.InputStream? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            captureScreenshot(cachePath, showSubtitles)
        }

    private fun captureScreenshot(cachePath: String, showSubtitles: Boolean): java.io.InputStream? {
        val view = mpvView ?: return null
        val filename = cachePath + "/${System.currentTimeMillis()}_mpv_screenshot_tmp.png"
        val subtitleFlag = if (showSubtitles) "subtitles" else "video"

        runCatching {
            view.mpv.command("screenshot-to-file", filename, subtitleFlag)
        }.onFailure {
            Log.e(TAG, "screenshot-to-file failed: ${it.message}")
            return null
        }

        // `screenshot-to-file` mpv tarafında asenkron çalışır; dosya hemen oluşmayabilir.
        var waitedMs = 0
        while (!java.io.File(filename).exists() && waitedMs < 800) {
            Thread.sleep(20L)
            waitedMs += 20
        }
        val tempFile = java.io.File(filename).takeIf { it.exists() } ?: run {
            Log.w(TAG, "Screenshot file was not created within ${waitedMs}ms")
            return null
        }
        val newFile = java.io.File("$cachePath/mpv_screenshot_${java.util.UUID.randomUUID()}.png")

        newFile.delete()
        return if (tempFile.renameTo(newFile)) newFile.inputStream() else tempFile.inputStream()
    }

    private suspend fun setupCustomButtons() {
        if (customButtonsLoaded) return
        customButtonsLoaded = true
        withContext(Dispatchers.IO) {
            runCatching {
                val db = KitsugiDatabase.getDatabase(context)
                if (!context.getSharedPreferences("kitsugi_prefs", Context.MODE_PRIVATE)
                        .getBoolean("mpv_user_files_enabled", true)) return@runCatching
                val buttons = db.customButtonDao().getAll()
                if (buttons.isEmpty()) return@runCatching
                val primaryButtonId = buttons.firstOrNull { it.isFavorite }?.id ?: 0L
                val scriptsDir = java.io.File(context.filesDir, "scripts")
                if (!scriptsDir.exists()) {
                    scriptsDir.mkdirs()
                }
                val customButtonsContent = buildString {
                    append(
                        """
                            local lua_modules = mp.find_config_file('scripts')
                            if lua_modules then
                                package.path = package.path .. ';' .. lua_modules .. '/?.lua;' .. lua_modules .. '/?/init.lua;' .. '${scriptsDir.absolutePath.replace("\\", "/")}' .. '/?.lua'
                            end
                        """.trimIndent()
                    )
                    append("\nlocal aniyomi = require('aniyomi')\n")
                    buttons.forEach { button ->
                        append(
                            """
                                -- ${button.name}
                                ${button.getButtonOnStartup(primaryButtonId)}
                                function button${button.id}()
                                    ${button.getButtonContent(primaryButtonId)}
                                end
                                mp.register_script_message('call_button_${button.id}', button${button.id})
                                function button${button.id}long()
                                    ${button.getButtonLongPressContent(primaryButtonId)}
                                end
                                mp.register_script_message('call_button_${button.id}_long', button${button.id}long)
                            """.trimIndent()
                        )
                        append("\n")
                    }
                }
                val file = java.io.File(scriptsDir, "custombuttons.lua")
                file.writeText(customButtonsContent)
                withContext(Dispatchers.Main) {
                    mpvView?.mpv?.command("load-script", file.absolutePath)
                }
            }.onFailure {
                Log.w(TAG, "setupCustomButtons error: ${it.message}")
            }
        }
    }

    /**
     * Harici altyazıları ana iş parçacığını kilitlemeden yükler: uzak dosyalar
     * [MpvSubtitlePrefetcher] ile IO'da önbelleğe indirilir, mpv'ye yerel yol verilir.
     *
     * @param select true → ilk yükleme (`cached`: seç); false → oynatma sırasında sonradan
     *               gelen altyazılar (`auto`: mevcut seçimi değiştirmeden ekle).
     */
    private fun loadExternalSubtitlesAsync(subtitles: List<SubtitleInput>, generation: Long, select: Boolean) {
        if (subtitles.isEmpty()) {
            scheduleForcedTrackSelection(generation)
            return
        }
        val headers = pendingHeaders
        engineScope.launch {
            val resolved = try {
                MpvSubtitlePrefetcher.materialize(context, subtitles, headers)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Harici altyazılar hazırlanamadı: ${error.javaClass.simpleName}: ${error.message}")
                emptyList()
            }
            if (released || generation != loadGeneration) return@launch
            val view = mpvView ?: return@launch
            resolved.forEach { (sub, localPath) ->
                view.addAndSelectExternalSubtitle(localPath, sub.name, sub.lang, select)
            }
            if (resolved.size < subtitles.size) {
                Log.w(TAG, "Harici altyazı: ${subtitles.size - resolved.size}/${subtitles.size} dosya indirilemedi, atlandı")
            }
            scheduleUpdateTracks()
            scheduleForcedTrackSelection(generation)
        }
    }

    /** Harici altyazılar beklenenden az yüklendiyse kısa bir gecikmeden sonra ilk seçimi zorlar. */
    private fun scheduleForcedTrackSelection(generation: Long) {
        engineScope.launch {
            kotlinx.coroutines.delay(1500L)
            if (!released && generation == loadGeneration && !initialSelectionDone) {
                selectionForced = true
                scheduleUpdateTracks()
            }
        }
    }

    /**
     * Aynı medya oynarken sonradan gelen (eklenti) altyazıları akışı YENİDEN HAZIRLAMADAN ekler.
     *
     * Eskiden altyazı listesi her değiştiğinde oynatıcı tamamen yeniden hazırlanıyordu: aynı
     * CDN adresine ikinci istek (HTTP 429 riski), yeniden tamponlama, günlükte çift
     * "[OYNAT] Başladı" ve ikinci bir `sub-add` fırtınası. Liste daraldıysa / değiştiyse
     * (kaynak değişimi) false döner ve çağıran güvenli yola, tam hazırlığa düşer.
     */
    override fun addExternalSubtitles(subtitles: List<SubtitleInput>): Boolean {
        if (released || mpvView == null) return false
        val previous = preparedSubtitles
        val incomingUrls = subtitles.mapTo(HashSet()) { it.url }
        if (!previous.all { it.url in incomingUrls }) return false
        val known = previous.mapTo(HashSet()) { it.url }
        val fresh = subtitles.filter { it.url.isNotBlank() && known.add(it.url) }
        if (fresh.isEmpty()) return true
        preparedSubtitles = previous + fresh
        pendingSubtitles = pendingSubtitles + fresh
        if (fileLoaded) {
            if (!userChangedTracks) {
                // Kullanıcı elle seçim yapmadıysa tercih politikası (Türkçe öncelikli) yeni
                // altyazılarla yeniden uygulanır — eski tam yeniden hazırlamayla aynı sonuç.
                initialSelectionDone = false
                selectionForced = false
            }
            loadExternalSubtitlesAsync(fresh, loadGeneration, select = false)
        }
        // fileLoaded=false ise yeni altyazılar pendingSubtitles içinde; FILE_LOADED'da eklenir.
        return true
    }

    override fun executeCommand(command: Array<String>) {
        runCatching {
            mpvView?.mpv?.command(*command)
        }
    }

    private companion object {
        /** Konum dinleyicilerine gönderim aralığı — arayüz için 4 Hz yeterli. */
        const val POSITION_NOTIFY_INTERVAL_MS = 250L
    }
}

