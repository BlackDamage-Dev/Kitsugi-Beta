package com.kitsugi.animelist.core.player.engine

import android.content.Context
import android.view.View
import com.kitsugi.animelist.core.player.SubtitleInput
import com.kitsugi.animelist.ui.screens.fullscreen.components.SubtitleStyleSettings
import com.kitsugi.animelist.ui.screens.fullscreen.components.TrackOption
import com.kitsugi.animelist.ui.screens.fullscreen.components.StreamInfoData

interface PlayerEngine {

    enum class State {
        IDLE,
        BUFFERING,
        READY,
        ENDED
    }

    interface Listener {
        fun onStateChanged(state: State)
        fun onPlaybackError(errorCode: Int, errorMsg: String, cause: Throwable?)
        fun onPositionChanged(positionMs: Long, durationMs: Long) {}
        fun onTracksChanged(audioTracks: List<TrackOption>, subtitleTracks: List<TrackOption>)
        fun onVideoSizeChanged(width: Int, height: Int, unappliedRotationDegrees: Int, pixelWidthHeightRatio: Float) {}
        fun onEngineEvent(property: String, value: String) {}
        fun onPlayingChanged(isPlaying: Boolean) {}
        fun onBufferedPositionChanged(positionMs: Long) {}
        fun onChaptersChanged(chapters: List<com.kitsugi.animelist.core.player.PlayerChapter>) {}
        fun onSecondarySubtitleChanged(trackId: Int) {}
    }

    val engineType: PlayerEngineType
    val currentState: State
    val currentPosition: Long
    val bufferedPosition: Long get() = currentPosition
    val duration: Long
    val isPlaying: Boolean
    val currentSpeed: Float
    val currentVolume: Float
    val subtitleDelayMs: Long
    val audioDelayMs: Long
    val isSubtitleDisabled: Boolean
    val activeStreamInfo: StreamInfoData

    /**
     * Şu anda gerçekten seçili/çözülen bir video parçası var mı?
     *
     * Ses ve görüntü ayrı akışlardan geldiğinde (imzalı YouTube adaptif akışları) ya da
     * cihazın çözemediği bir video kodeği geldiğinde (ör. AnimeThemes'in VP9 `.webm`
     * dosyaları) oynatıcı hata vermez: ses çalmaya devam eder, zaman çizgisi ilerler ama
     * video parçası hiç seçilmez. Sonuç "sesi var, görüntüsü yok" durumudur ve
     * [State.READY] olduğu için hiçbir yedek motor devreye girmez.
     *
     * Bu bayrak, çağıran tarafın (küçük oynatıcı bekçisi) bu durumu yakalayıp diğer
     * motora geçebilmesi için vardır. Varsayılan `true`'dur: bilinmeyen/uygulanmamış
     * motorlar yanlış yere yedek geçiş tetiklemez.
     */
    val hasVideoTrack: Boolean get() = true

    /**
     * Video karesi GERÇEKTEN çizildi mi?
     *
     * [hasVideoTrack] yalnızca "video parçası seçildi" der; seçilen parçanın kare
     * ürettiğini garanti etmez. İki gerçek durum bunu ayırır:
     *
     *  - Media3: video parçası seçilir, ancak çözücü kare üretmezse (ör. VP9 desteği
     *    olmayan donanım) PlayerView siyah kalır; `currentTracks` yine de seçili döner.
     *  - MPV: `vo-configured` görüntü çıkışının KURULDUĞUNU söyler, karenin görünür bir
     *    yüzeye gittiğini söylemez. SurfaceView Compose'un kırpılmış/animasyonlu bir
     *    kabında 0×0 kalırsa ses çalar, görüntü gelmez.
     *
     * Bu yüzden küçük oynatıcının sessiz "ses var, görüntü yok" bekçisi HER İKİ bayrağı
     * birlikte okur. Varsayılan [hasVideoTrack]'tir: bilinmeyen/uygulanmamış motorlar
     * yanlış yedek geçişi tetiklemez.
     */
    val hasRenderedVideoFrame: Boolean get() = hasVideoTrack

    // ── Motor yetenekleri (özellik kapıları) ─────────────────────────────────
    // UI, desteklenmeyen bir kontrolü sessizce "no-op" bırakmak yerine bu bayraklara
    // bakarak motor değiştirebilir veya kontrolü gizleyebilir. Varsayılanlar Media3'tür;
    // MPV bu değerlerin tamamını destekler.
    val supportsExternalTracks: Boolean get() = false
    val supportsSecondarySubtitles: Boolean get() = false
    val selectedSecondarySubtitleId: Int get() = -1
    fun selectSecondarySubtitle(trackId: Int) {}
    val supportsSubtitleDelay: Boolean get() = false
    val supportsNegativeSubtitleDelay: Boolean get() = false
    val supportsSecondarySubtitleDelay: Boolean get() = false
    val supportsSubtitleSpeed: Boolean get() = false
    val supportsVideoFilters: Boolean get() = false
    val supportsNegativeAudioDelay: Boolean get() = false

    /** İkincil altyazı gecikmesi (ms). Yalnızca destekleyen motorlarda anlamlıdır. */
    val secondarySubtitleDelayMs: Long get() = 0L

    /**
     * Oynatmanın arabelleğe alma (seek/buffering) yüzünden değil, gerçekten duraklatılmış
     * durumda olup olmadığını bildirir. Media3'te `isPlaying` her seek/buffering anında
     * geçici olarak `false` olurken `playWhenReady` `true` kalır; OSD'nin seek sırasında
     * kendiliğinden açılıp kapanmaması için bu özellik kullanılır.
     */
    val isPausedByUser: Boolean
        get() = !isPlaying && currentState == State.READY

    fun addListener(listener: Listener)
    fun removeListener(listener: Listener)

    /**
     * Prepares and starts playback of a video stream.
     * This is a suspend function so heavy resolution work (DV policy, codec probing)
     * can be safely offloaded to IO dispatchers without blocking the main thread.
     */
    suspend fun prepare(
        videoUrl: String,
        audioUrl: String? = null,
        externalAudioTracks: List<com.kitsugi.animelist.core.player.ExternalAudioTrackInput> = emptyList(),
        streamTypeHint: String? = null,
        headers: Map<String, String> = emptyMap(),
        subtitles: List<SubtitleInput> = emptyList(),
        startPositionMs: Long = 0L,
        addonName: String? = null,
        isCS: Boolean = false,
        streamTitle: String? = null,
        qualityValue: Int? = null,
        mediaType: com.kitsugi.animelist.core.player.StreamMediaType? = null,
        csStreamType: String? = null,
        csExtractorData: String? = null
    )

    /**
     * Aynı medya oynarken sonradan gelen harici altyazıları akışı yeniden hazırlamadan ekler.
     *
     * @return true → motor altyazıları ekledi; çağıran [prepare] ÇAĞIRMAMALI.
     *         false → motor bunu desteklemiyor (ör. Media3 yan yüklü altyazı için kaynağı
     *         yeniden kurmalı) ya da liste daraldı; çağıran tam [prepare] yapmalı.
     */
    fun addExternalSubtitles(subtitles: List<SubtitleInput>): Boolean = false

    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setPlaybackSpeed(speed: Float)
    fun setVolume(volume: Float)
    fun setSubtitleDelay(delayMs: Long)
    fun setAudioDelay(delayMs: Long)

    /** İkincil altyazı gecikmesi — varsayılan olarak desteklenmez. */
    fun setSecondarySubtitleDelay(delayMs: Long) {}

    /** Altyazı hızı (ör. 1.0 = normal). Varsayılan olarak desteklenmez. */
    fun setSubtitleSpeed(speed: Float) {}

    /**
     * Video renk filtreleri. Anahtar MPV özellik adıdır (brightness/saturation/contrast/
     * gamma/hue), değer -100..100 aralığındadır. Desteklemeyen motorlarda no-op.
     */
    fun setVideoFilters(values: Map<String, Int>) {}
    fun setSubtitleStyle(style: SubtitleStyleSettings)
    /**
     * Video çözünürlüğü tavanı (piksel yüksekliği). `null` → sınırsız (motorun kendi
     * adaptif seçimi).
     *
     * NEDEN: DASH (`.mpd`) akışlarında kaliteler ayrı URL'ler değil, tek manifest içindeki
     * `Representation` düğümleridir. Böyle akışlarda kullanıcının "Kalite" seçimi bu tavanla
     * uygulanır; HLS'te ise seçim doğrudan varyant URL'sine geçilerek yapılır.
     * Desteklemeyen motorlarda no-op'tur (varsayılan).
     */
    fun setMaxVideoHeight(height: Int?) {}

    fun setResizeMode(resizeMode: Int) // Matches AspectRatioFrameLayout.RESIZE_MODE_*
    fun setAspectMode(mode: com.kitsugi.animelist.core.player.PlayerAspectMode)

    fun handleKey(event: android.view.KeyEvent): Boolean = false
    /** Real video renditions of the currently loaded media; empty when unsupported. */
    val videoQualityTracks: List<TrackOption> get() = emptyList()
    val isVideoQualityAuto: Boolean get() = true
    /** null restores adaptive selection. Never changes the provider or media URL. */
    fun selectVideoQuality(track: TrackOption?) {}

    fun selectTrack(trackOption: TrackOption)
    fun disableSubtitles()

    /**
     * Creates or returns the View instance where video will be rendered.
     */
    fun createVideoView(context: Context): View

    /**
     * Stop and release resources.
     */
    fun release()

    /**
     * Takes a screenshot of the video.
     */
    suspend fun takeScreenshot(cachePath: String, showSubtitles: Boolean): java.io.InputStream? = null

    /**
     * Executes an arbitrary engine command (e.g. MPV script-message).
     */
    fun executeCommand(command: Array<String>) {}
}

