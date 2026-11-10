package com.kitsugi.animelist.core.player.engine

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.Utils
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToLong
import com.kitsugi.animelist.ui.screens.fullscreen.components.SubtitleStyleSettings
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

enum class MpvHardwareDecodeMode {
    LEGACY_DIRECT_COPY, AUTO_SAFE, HARDWARE_COPY, HARDWARE_DIRECT, DISABLED
}

enum class AspectMode {
    ORIGINAL,
    FULL_SCREEN,
    STRETCH,
    SLIGHT_ZOOM,
    CINEMA_ZOOM,
    VERTICAL_STRETCH,
    HORIZONTAL_STRETCH
}

class KitsugiMpvSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : BaseMPVView(context, attrs) {

    private var initialized = false
    private var caFile: java.io.File? = null
    private val contentDescriptors = mutableMapOf<String, android.os.ParcelFileDescriptor>()

    private fun resolveUrl(url: String): String {
        // Close descriptors from previous media URLs so multi-episode playback never leaks FDs.
        val staleKeys = contentDescriptors.keys.filter { it != url }
        staleKeys.forEach { key ->
            contentDescriptors.remove(key)?.let { pfd -> runCatching { pfd.close() } }
        }
        if (url.startsWith("file://")) {
            return url.removePrefix("file://")
        }
        val uri = android.net.Uri.parse(url)
        if (uri.scheme != "content") return url
        val descriptor = contentDescriptors.getOrPut(url) {
            requireNotNull(context.contentResolver.openFileDescriptor(uri, "r")) { "Unable to open content URI" }
        }
        return "fd://${descriptor.fd}"
    }

    private fun loadMedia(url: String, startOption: String?) {
        val version = mpv.getPropertyString("mpv-version") ?: ""
        mpv.command(*MpvPlaybackPolicy.loadfile(resolveUrl(url), startOption, version))
    }
    private var hasQueuedInitialMedia = false
    private var lastMediaRequestKey: String? = null
    private var pendingInitialMediaUrl: String? = null
    private var pendingInitialStartOption: String? = null
    /**
     * BaseMPVView attaches the native Android surface in surfaceCreated(). A fallback can
     * queue media after that callback, so we sometimes invoke the same path manually.
     * Remember the attached instance to avoid attaching one Surface twice (which can leave
     * mpv with audio but a stale/black video output).
     */
    private var attachedSurface: android.view.Surface? = null
    private var hardwareDecodeMode: MpvHardwareDecodeMode = MpvHardwareDecodeMode.AUTO_SAFE

    /**
     * Kullanıcının elle seçtiği ses parçası (mpv track id). 2026-10-12 kullanıcı raporu:
     * "3 ses seçeneği hiçbir şey değiştirmiyor, hep aynı İngilizce ses oynuyor."
     * Seçim yapıldıktan sonra dil tercihi/otomatik seçim yolları bu değeri EZMEZ.
     */
    private var userSelectedAudioId: Int? = null
    private var currentAspectMode: AspectMode = AspectMode.ORIGINAL
    private var pendingAspectRetryCount = 0
    /** En son uygulanan görüntü ölçeği (view ölçeği DEĞİL, mpv video-scale-x/y). */
    private var appliedVideoScaleX = 1.0f
    private var appliedVideoScaleY = 1.0f
    /** Altyazı çizim alanı pencereye sabitlendi mi? null = henüz uygulanmadı. */
    private var subtitleAreaPinnedToWindow: Boolean? = null
    private val aspectReapplyRunnable = Runnable {
        applyAspectModeInternal(currentAspectMode, allowRetry = true)
    }

    fun ensureInitialized() {
        if (initialized) return
        caFile = com.kitsugi.animelist.core.player.MpvTrustStore.install(context)
        val scripts = java.io.File(context.filesDir, "scripts").apply { mkdirs() }
        java.io.File(context.filesDir, "script-opts").mkdirs()
        java.io.File(context.filesDir, "shaders").mkdirs()
        java.io.File(context.filesDir, "fonts").mkdirs()
        // Sync user-edited mpv.conf and input.conf before libmpv reads configDir
        val prefs = context.getSharedPreferences("kitsugi_prefs", Context.MODE_PRIVATE)
        prefs.getString("mpv_conf_content", null)?.let { conf ->
            runCatching { java.io.File(context.filesDir, "mpv.conf").writeText(conf) }
        }
        prefs.getString("mpv_input_conf_content", null)?.let { inputConf ->
            runCatching { java.io.File(context.filesDir, "input.conf").writeText(inputConf) }
        }
        // Generated handlers are rebuilt from Room, never auto-load an old copy.
        java.io.File(scripts, "custombuttons.lua").delete()
        context.assets.open("mpv/aniyomi.lua").use { source ->
            java.io.File(scripts, "aniyomi.lua").outputStream().use { source.copyTo(it) }
        }
        Utils.copyAssets(context)
        initialize(
            configDir = context.filesDir.path,
            cacheDir = context.cacheDir.path
        )
        initialized = true
    }

    fun setMedia(url: String, headers: Map<String, String>, startPositionMs: Long = 0L, forceReload: Boolean = false) {
        ensureInitialized()
        val requestKey = buildMediaRequestKey(url = url, headers = headers) +
            "#start=${startPositionMs.coerceAtLeast(0L)}"
        if (!forceReload && hasQueuedInitialMedia && requestKey == lastMediaRequestKey) {
            return
        }
        applyHeaders(headers)
        applyDefaultTrackSelectionForNewLoad()
        pendingInitialMediaUrl = url
        pendingInitialStartOption = startPositionMs.takeIf { it > 0L }
            ?.let { String.format(Locale.US, "start=%.3f", it / 1000.0) }
        hasQueuedInitialMedia = true
        // Own the pending queue; BaseMPVView.playFile could retain an older request.
        if (holder.surface?.isValid == true) ensureSurfaceAttachedIfAlreadyAvailable()
        lastMediaRequestKey = requestKey
        scheduleAspectModeRefresh(resetRetryCount = true)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val surface = holder.surface
        if (surface != null && attachedSurface !== surface) {
            // Let BaseMPVView attach the surface and restore its saved VO. Do this once per
            // actual Surface, including PiP/fullscreen recreation, not once per loadfile.
            super.surfaceCreated(holder)
            attachedSurface = surface
        }

        val url = pendingInitialMediaUrl
        if (url != null) {
            val startOption = pendingInitialStartOption
            pendingInitialMediaUrl = null
            pendingInitialStartOption = null
            loadMedia(url, startOption)
        }
        // No extra video-reconfig here: BaseMPVView already restores `vo` on a recreated
        // surface. Reconfiguring a second time can tear down the just-created video output.
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        attachedSurface = null
        super.surfaceDestroyed(holder)
    }

    fun setMediaUsingLoadfile(url: String, headers: Map<String, String>) {
        setMedia(url, headers, forceReload = true)
    }

    private fun ensureSurfaceAttachedIfAlreadyAvailable() {
        if (!initialized) return
        val currentHolder = holder
        val currentSurface = currentHolder.surface ?: return
        if (!currentSurface.isValid) return
        runCatching {
            // Some fallback transitions initialize mpv after the Surface is already alive.
            // In that path, SurfaceHolder callback may not fire again, so force attach.
            surfaceCreated(currentHolder)
        }.onFailure {
            Log.w(TAG, "Failed to force MPV surface attach: ${it.message}")
        }
    }

    private fun applyDefaultTrackSelectionForNewLoad() {
        runCatching {
            // Yeni medya yüklendi → kullanıcı seçimi artık geçersiz, mpv kendi seçsin.
            userSelectedAudioId = null
            // Let mpv choose the default streams for every new media load.
            mpv.setPropertyString("aid", "auto")
            mpv.setPropertyString("sid", "auto")
            mpv.setPropertyBoolean("sub-visibility", true)
        }.onFailure {
            Log.w(TAG, "Failed to reset default A/V track selection: ${it.message}")
        }
    }

    fun setPaused(paused: Boolean) {
        if (!initialized) return
        mpv.setPropertyBoolean("pause", paused)
    }

    fun isPlayingNow(): Boolean {
        if (!initialized) return false
        return mpv.getPropertyBoolean("pause") == false
    }

    fun isPausedForCacheNow(): Boolean {
        if (!initialized) return false
        return mpv.getPropertyBoolean("paused-for-cache") == true
    }

    fun isCoreIdleNow(): Boolean {
        if (!initialized) return false
        return mpv.getPropertyBoolean("core-idle") == true
    }

    fun seekToMs(positionMs: Long) {
        if (!initialized) return
        val seconds = (positionMs.coerceAtLeast(0L) / 1000.0)
        mpv.setPropertyDouble("time-pos", seconds)
    }

    fun currentPositionMs(): Long {
        if (!initialized) return 0L
        val seconds = mpv.getPropertyDouble("time-pos/full")
            ?: mpv.getPropertyDouble("time-pos")
            ?: 0.0
        return (seconds * 1000.0).roundToLong().coerceAtLeast(0L)
    }

    fun durationMs(): Long {
        if (!initialized) return 0L
        val seconds = mpv.getPropertyDouble("duration/full") ?: 0.0
        return (seconds * 1000.0).roundToLong().coerceAtLeast(0L)
    }

    fun hasVideoTrackSelectedNow(): Boolean {
        if (!initialized) return false
        val vid = mpv.getPropertyString("vid")?.trim()
        return !vid.isNullOrBlank() && !vid.equals("no", ignoreCase = true)
    }

    /**
     * Görüntü gerçekten ekrana çıkıyor mu?
     *
     * [hasVideoTrackSelectedNow] yalnızca parçanın seçili olduğunu söyler. Android'de
     * video çıkışı (VO) kurulamazsa mpv sesi çalmaya devam eder ama hiç kare üretmez;
     * kullanıcı bunu "sesi var, görüntüsü yok" olarak görür. `vo-configured` özelliği
     * tam bu durumu ayırt eder. Özellik okunamazsa yanlış yedek geçişi yapmamak için
     * `true` (görüntü var) kabul edilir.
     */
    fun isVideoOutputActiveNow(): Boolean {
        if (!initialized) return false
        if (!hasVideoTrackSelectedNow()) return false
        val configured = runCatching { mpv.getPropertyBoolean("vo-configured") }.getOrNull()
        return configured ?: true
    }

    /**
     * Görüntü çıkışı kurulu VE yüzey gerçekten görünür/geçerli mi?
     *
     * `vo-configured` yalnızca mpv'nin video çıkışını kurduğunu söyler; karenin ekranda
     * görünen bir yüzeye gittiğini garanti etmez. SurfaceView, Compose'un kırpılmış
     * (`clipToBounds`) veya ölçüsü animasyonla değişen bir kabında 0×0 kalırsa/pencereden
     * düşerse mpv sesi çalmaya devam eder, kare üretmez — kullanıcı "ses var, görüntü yok"
     * görür ve eski bekçi bunu yakalayamaz. Bu kontrol o boşluğu kapatır.
     */
    fun hasVisibleVideoSurface(): Boolean {
        if (!initialized) return false
        if (!isShown || width <= 0 || height <= 0) return false
        return runCatching {
            val surface = holder.surface
            surface?.isValid == true && attachedSurface === surface
        }.getOrDefault(false)
    }

    /**
     * libmpv produced a real video-output configuration, not merely demuxer metadata.
     *
     * `video-params` becomes available as soon as a stream is recognized, even when its
     * decoder later fails. `video-out-params` is populated by the video output chain and
     * therefore distinguishes actual video output from an audio-only session. Some mpv
     * builds expose only dw/dh, so both representations are accepted.
     */
    fun hasVideoOutputFrameNow(): Boolean {
        if (!initialized || !isVideoOutputActiveNow()) return false
        val width = runCatching {
            mpv.getPropertyInt("video-out-params/w")?.takeIf { it > 0 }
                ?: mpv.getPropertyInt("video-out-params/dw")?.takeIf { it > 0 }
        }.getOrNull()
        val height = runCatching {
            mpv.getPropertyInt("video-out-params/h")?.takeIf { it > 0 }
                ?: mpv.getPropertyInt("video-out-params/dh")?.takeIf { it > 0 }
        }.getOrNull()
        return width != null && height != null
    }

    fun setPlaybackSpeed(speed: Float) {
        if (!initialized) return
        mpv.setPropertyDouble("speed", speed.toDouble())
    }

    fun applyAudioAmplificationDb(db: Int) {
        if (!initialized) return
        val clampedDb = db.coerceIn(AUDIO_AMPLIFICATION_MIN_DB, AUDIO_AMPLIFICATION_MAX_DB)
        val linearScale = 10.0.pow(clampedDb / 20.0)
        val targetVolumePercent = (100.0 * linearScale).coerceIn(0.0, MPV_MAX_VOLUME_PERCENT)
        runCatching {
            mpv.setPropertyDouble("volume", targetVolumePercent)
        }.onFailure {
            Log.w(TAG, "Failed to apply audio amplification on mpv (db=$clampedDb): ${it.message}")
        }
    }

    fun applyAudioLanguagePreferences(languages: List<String>) {
        if (!initialized) return
        val normalized = languages
            .mapNotNull { language ->
                language.trim().takeIf { it.isNotBlank() }
            }
            .distinct()
        runCatching {
            // Empty value resets language preference back to default behavior.
            mpv.setPropertyString("alang", normalized.joinToString(","))
            val userPick = userSelectedAudioId
            if (userPick == null) {
                // Re-run automatic audio selection with the latest preferences.
                mpv.setPropertyString("aid", "auto")
            } else {
                // Kullanıcı elle bir ses parçası seçtiyse dil tercihi onu EZMEMELİ.
                applyAudioSelection(userPick)
                Log.i(TAG, "alang güncellendi; kullanıcının ses seçimi (id=$userPick) korundu.")
            }
        }.onFailure {
            Log.w(TAG, "Failed to set audio language preference: ${it.message}")
        }
    }

    fun applyHardwareDecodeMode(mode: MpvHardwareDecodeMode) {
        hardwareDecodeMode = mode
        if (!initialized) return
        runCatching {
            mpv.setPropertyString("hwdec", mode.toMpvHwdecValue())
        }.onFailure {
            Log.w(TAG, "Failed to apply mpv hardware decode mode ($mode): ${it.message}")
        }
    }

    fun setSubtitleDelayMs(delayMs: Int) {
        if (!initialized) return
        runCatching {
            mpv.setPropertyDouble("sub-delay", delayMs / 1000.0)
        }.onFailure {
            Log.w(TAG, "Failed to set subtitle delay on mpv: ${it.message}")
        }
    }

    fun applyAspectMode(mode: AspectMode) {
        currentAspectMode = mode
        pendingAspectRetryCount = 0
        removeCallbacks(aspectReapplyRunnable)
        applyAspectModeInternal(mode, allowRetry = true)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == oldw && h == oldh) return
        pendingAspectRetryCount = 0
        removeCallbacks(aspectReapplyRunnable)
        post {
            applyAspectModeInternal(currentAspectMode, allowRetry = true)
        }
    }

    private fun applyAspectModeInternal(mode: AspectMode, allowRetry: Boolean) {
        val viewAspect = readViewAspectRatio(width, height)
        val videoAspect = readVideoAspectRatio()
        val scale = resolveAspectScale(
            mode = mode,
            viewAspect = viewAspect,
            videoAspect = videoAspect
        )
        applyVideoScale(scale)
        applySubtitleWindowPolicy(scale)
        if (
            allowRetry &&
            aspectModeNeedsVideoAspect(mode) &&
            (viewAspect <= 0f || videoAspect == null || videoAspect <= 0f)
        ) {
            scheduleAspectModeRefresh(resetRetryCount = false)
        }
    }

    /**
     * Görüntü modunu (Doldur / Yakınlaştır / 16:9 / 4:3 / Orijinal) mpv tarafında uygular.
     *
     * NEDEN VIEW ÖLÇEĞİ DEĞİL (2026-10-21 kullanıcı raporu):
     * Eskiden burada `View.scaleX/scaleY` atanıyordu. Bir `SurfaceView` ölçeklendiğinde
     * yüzeyin TAMAMI (video + mpv'nin çizdiği altyazı/OSD katmanı) birlikte büyüyordu.
     * Kullanıcı "Doldur" seçtiğinde altyazı da video ile birlikte esniyor, büyüyor ve
     * view merkezden ölçeklendiği için alt satırlar ekranın altına taşıyordu.
     *
     * Artık yalnızca video katmanı ölçeklenir (`video-scale-x`/`video-scale-y`); altyazı
     * yerleşimi [applySubtitleWindowPolicy] ile pencere sınırlarına sabitlenir. Böylece
     * harici/gömülü altyazılar görüntü modundan bağımsız kalır.
     */
    private fun applyVideoScale(scale: AspectScale) {
        // Kalıntı view ölçeği varsa (eski davranış) mutlaka sıfırla: aksi hâlde altyazı
        // bir kez daha ölçeklenir.
        if (super.getScaleX() != 1.0f) super.setScaleX(1.0f)
        if (super.getScaleY() != 1.0f) super.setScaleY(1.0f)

        if (!initialized) return
        val targetX = scale.scaleX.coerceIn(MIN_VIDEO_SCALE, MAX_VIDEO_SCALE)
        val targetY = scale.scaleY.coerceIn(MIN_VIDEO_SCALE, MAX_VIDEO_SCALE)
        if (kotlin.math.abs(targetX - appliedVideoScaleX) < 0.001f &&
            kotlin.math.abs(targetY - appliedVideoScaleY) < 0.001f
        ) {
            return
        }
        appliedVideoScaleX = targetX
        appliedVideoScaleY = targetY
        runCatching {
            mpv.setPropertyDouble("video-scale-x", targetX.toDouble())
            mpv.setPropertyDouble("video-scale-y", targetY.toDouble())
        }.onFailure {
            Log.w(TAG, "Video ölçeği uygulanamadı (${it.message})")
        }
    }

    /**
     * Altyazı çizim alanını (libass OSD alanı) pencereye sabitler.
     *
     * mpv'de `sub-use-margins` / `sub-ass-force-margins` açıkken libass, çizim alanını
     * "pencereden videoya kalan boşluk" olarak hesaplar. Video pencereyi taşıyorsa
     * (Doldur / Yakınlaştır / 16:9 / 4:3 → `video-scale > 1`) bu boşluk NEGATİF olur;
     * libass alanı pencereden büyük sanıp alt yazıyı ekranın altına yerleştirir.
     * Video taştığında margin'leri kapatıp çizim alanını tam pencere yapıyoruz; altyazı
     * böylece görüntü modu ne olursa olsun ekran içinde ve aynı boyutta kalır.
     * Video pencereye sığdığında eski davranış (altyazı video alanı içinde) korunur.
     */
    private fun applySubtitleWindowPolicy(scale: AspectScale) {
        if (!initialized) return
        val videoOverflowsWindow = scale.scaleX > 1.001f || scale.scaleY > 1.001f
        if (subtitleAreaPinnedToWindow == videoOverflowsWindow) return
        subtitleAreaPinnedToWindow = videoOverflowsWindow
        val marginsValue = if (videoOverflowsWindow) "no" else "yes"
        runCatching {
            mpv.setPropertyString("sub-use-margins", marginsValue)
            mpv.setPropertyString("sub-ass-force-margins", marginsValue)
            // Yazı boyutu videoya değil pencereye göre ölçeklensin: esnetilen görüntüde
            // altyazı da büyümesin.
            mpv.setPropertyString("sub-scale-by-window", "yes")
            mpv.setPropertyString("sub-scale-with-window", "yes")
            mpv.setPropertyString("sub-ass-scale-with-window", "no")
        }.onFailure {
            Log.w(TAG, "Altyazı alanı politikası uygulanamadı (${it.message})")
        }
        Log.d(
            TAG,
            "Altyazı çizim alanı: ${if (videoOverflowsWindow) "pencereye sabitlendi" else "video alanına bağlı"} " +
                "(scale=${scale.scaleX}x${scale.scaleY})"
        )
    }

    private fun scheduleAspectModeRefresh(resetRetryCount: Boolean) {
        if (resetRetryCount) {
            pendingAspectRetryCount = 0
        }
        removeCallbacks(aspectReapplyRunnable)
        if (pendingAspectRetryCount >= MAX_ASPECT_RETRY_COUNT) {
            return
        }
        val delayMs = if (pendingAspectRetryCount == 0) 0L else ASPECT_RETRY_DELAY_MS
        pendingAspectRetryCount += 1
        postDelayed(aspectReapplyRunnable, delayMs)
    }

    fun applySubtitleStyle(style: SubtitleStyleSettings) {
        if (!initialized) return
        runCatching {
            val scale = (style.size / 20.0).coerceIn(0.5, 3.0)
            // Default outline size if enabled
            val outlineSize = if (style.outlineEnabled) 2.0 else 0.0
            
            // Format colors into hex strings
            val textColorStr = toMpvColor(style.textColor)

            mpv.setPropertyDouble("sub-scale", scale)
            mpv.setPropertyBoolean("sub-bold", style.bold)
            mpv.setPropertyDouble("sub-outline-size", outlineSize)
            mpv.setPropertyString("sub-color", textColorStr)

            val subPos = (100.0 - (style.verticalOffset.toDouble() / 5.0)).coerceIn(0.0, 150.0)
            mpv.setPropertyDouble("sub-pos", subPos)
        }.onFailure {
            Log.w(TAG, "Failed to apply subtitle style on mpv: ${it.message}")
        }
    }

    private fun isAssOrSsaSubtitleSelectedNow(): Boolean {
        if (!initialized) return false
        val trackCount = mpv.getPropertyInt("track-list/count") ?: return false
        if (trackCount <= 0) return false

        val selectedSubtitleId = mpv.getPropertyString("sid")?.toIntOrNull()
            ?: mpv.getPropertyInt("current-tracks/sub/id")

        for (i in 0 until trackCount) {
            val type = mpv.getPropertyString("track-list/$i/type")?.lowercase(Locale.US) ?: continue
            if (type != "sub") continue

            val isSelectedByFlag = mpv.getPropertyBoolean("track-list/$i/selected") == true
            val trackId = mpv.getPropertyInt("track-list/$i/id")
            val isSelected = isSelectedByFlag || (selectedSubtitleId != null && trackId == selectedSubtitleId)
            if (!isSelected) continue

            val codec = mpv.getPropertyString("track-list/$i/codec")
                ?.trim()
                ?.lowercase(Locale.US)
                ?: return false
            return codec.contains("ass") || codec.contains("ssa")
        }
        return false
    }

    fun selectAudioTrackById(trackId: Int): Boolean {
        if (!initialized) return false
        userSelectedAudioId = trackId
        return applyAudioSelection(trackId)
    }

    /**
     * Ses parçasını seçer ve GERÇEKTEN uygulandığını doğrular.
     *
     * mpv'de `aid` bazı sürümlerde yalnız `setPropertyInt` ile, bazılarında yalnız `command`
     * yoluyla uygulanır. Tek yol denendiğinde seçim sessizce başarısız olabiliyor ve kullanıcı
     * "hiçbir şey değişmiyor" görüyordu — bu yüzden iki yol da denenir ve sonuç doğrulanır.
     */
    private fun applyAudioSelection(trackId: Int): Boolean {
        if (!initialized) return false

        fun currentAid(): Int? = runCatching { mpv.getPropertyString("aid")?.toIntOrNull() }.getOrNull()

        // 1) Doğrudan aid özelliği
        val propertyOk = runCatching { mpv.setPropertyInt("aid", trackId) }.isSuccess
        if (currentAid() == trackId) {
            Log.i(TAG, "Ses parçası seçildi (aid=$trackId)")
            return true
        }

        // 2) Komut yolu — bazı libmpv sürümlerinde aid yalnız komutla uygulanır
        val commandOk = runCatching { mpv.command("set", "aid", trackId.toString()) }.isSuccess
        val applied = currentAid()
        if (applied == trackId) {
            Log.i(TAG, "Ses parçası seçildi (command set aid=$trackId)")
            return true
        }

        // 3) Son çare: aid'i metin olarak yaz (bazı sürümler int kabul etmez)
        val previousAid = currentAid()
        runCatching { mpv.setPropertyString("aid", trackId.toString()) }
        val finalAid = currentAid()

        if (finalAid == trackId) {
            Log.i(TAG, "Ses parçası seçildi (string aid=$trackId)")
            return true
        }

        // Seçim uygulanmadıysa sesi sessiz bırakmamak için önceki parçaya dön.
        if (previousAid != null && previousAid != finalAid) {
            runCatching { mpv.setPropertyInt("aid", previousAid) }
        }
        Log.w(
            TAG,
            "Ses parçası seçilemedi: istenen=$trackId, aid=${finalAid ?: "?"}, " +
                "setProperty=${propertyOk}, command=${commandOk}"
        )
        return propertyOk || commandOk
    }

    fun selectSubtitleTrackById(trackId: Int): Boolean {
        if (!initialized) return false
        return runCatching {
            mpv.setPropertyBoolean("sub-visibility", true)
            mpv.setPropertyInt("sid", trackId)
            true
        }.getOrElse {
            Log.w(TAG, "Failed to select subtitle track id=$trackId: ${it.message}")
            false
        }
    }

    fun disableSubtitles(): Boolean {
        if (!initialized) return false
        return runCatching {
            mpv.setPropertyString("sid", "no")
            mpv.setPropertyBoolean("sub-visibility", false)
            true
        }.getOrElse {
            Log.w(TAG, "Failed to disable subtitles: ${it.message}")
            false
        }
    }

    /**
     * Harici altyazı ekler. ÖNEMLİ: [url] uzak bir adres ise mpv dosyayı bu çağrının içinde
     * senkron indirir — ana iş parçacığından yalnızca YEREL yol verin
     * (bkz. [MpvSubtitlePrefetcher]; uzak altyazı ana iş parçacığını saniyelerce kilitliyordu).
     *
     * @param select true → `cached` (altyazıyı seç, aynı dosya zaten ekliyse onu seç);
     *               false → `auto` (mevcut seçimi ve görünürlüğü değiştirmeden ekle; oynatma
     *               sırasında sonradan gelen altyazılar için).
     */
    fun addAndSelectExternalSubtitle(
        url: String,
        title: String? = null,
        language: String? = null,
        select: Boolean = true
    ): Boolean {
        if (!initialized) return false
        if (url.isBlank()) return false
        return runCatching {
            // "cached" avoids duplicate re-loads for the same external subtitle.
            val resolvedSubPath = if (url.startsWith("file://")) url.removePrefix("file://") else url
            val flag = if (select) "cached" else "auto"
            val safeTitle = title?.takeIf { it.isNotBlank() }
            val safeLanguage = language?.takeIf { it.isNotBlank() }
            when {
                safeTitle != null && safeLanguage != null ->
                    mpv.command("sub-add", resolvedSubPath, flag, safeTitle, safeLanguage)
                safeTitle != null ->
                    mpv.command("sub-add", resolvedSubPath, flag, safeTitle)
                else ->
                    mpv.command("sub-add", resolvedSubPath, flag)
            }
            if (select) mpv.setPropertyBoolean("sub-visibility", true)
            true
        }.getOrElse {
            Log.w(TAG, "Failed to add external subtitle: ${it.message}")
            false
        }
    }

    fun applySubtitleLanguagePreferences(preferred: String, secondary: String?) {
        if (!initialized) return
        val rawLanguages = listOfNotNull(
            preferred.takeIf { it.isNotBlank() && !it.equals("none", ignoreCase = true) },
            secondary?.takeIf { it.isNotBlank() && !it.equals("none", ignoreCase = true) }
        )
        if (rawLanguages.isEmpty()) {
            disableSubtitles()
            return
        }

        // Genişletilmiş dil kodları (MPV slang için: tr -> tr,tur,turkish,türkçe,turk)
        val expandedLanguages = mutableListOf<String>()
        rawLanguages.forEach { langGroup ->
            langGroup.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.forEach { code ->
                when (code) {
                    "tr", "tur" -> {
                        expandedLanguages.add("tr")
                        expandedLanguages.add("tur")
                        expandedLanguages.add("turkish")
                        expandedLanguages.add("türkçe")
                        expandedLanguages.add("turk")
                    }
                    "en", "eng" -> {
                        expandedLanguages.add("en")
                        expandedLanguages.add("eng")
                        expandedLanguages.add("english")
                    }
                    else -> expandedLanguages.add(code)
                }
            }
        }

        runCatching {
            mpv.setPropertyString("slang", expandedLanguages.distinct().joinToString(","))
        }.onFailure {
            Log.w(TAG, "Failed to set subtitle language preference: ${it.message}")
        }
    }

    fun readTrackSnapshot(): MpvTrackSnapshot {
        if (!initialized) return MpvTrackSnapshot(emptyList(), emptyList())
        val trackCount = runCatching { mpv.getPropertyInt("track-list/count") ?: 0 }
            .getOrDefault(0)
        if (trackCount <= 0) {
            return MpvTrackSnapshot(emptyList(), emptyList())
        }

        val selectedAudioTrackId = mpv.getPropertyString("aid")?.toIntOrNull()
            ?: mpv.getPropertyInt("current-tracks/audio/id")
        val rawSubtitleId = mpv.getPropertyString("sid")
        val selectedSubtitleTrackId = if (rawSubtitleId == "no") -1 else rawSubtitleId?.toIntOrNull()
            ?: mpv.getPropertyInt("current-tracks/sub/id")

        val audioTracks = mutableListOf<MpvTrack>()
        val subtitleTracks = mutableListOf<MpvTrack>()

        for (i in 0 until trackCount) {
            val type = mpv.getPropertyString("track-list/$i/type")?.lowercase() ?: continue
            val id = mpv.getPropertyInt("track-list/$i/id") ?: continue
            val language = mpv.getPropertyString("track-list/$i/lang")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            val title = mpv.getPropertyString("track-list/$i/title")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            val codec = mpv.getPropertyString("track-list/$i/codec")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            val selectedByFlag = mpv.getPropertyBoolean("track-list/$i/selected") == true
            val external = mpv.getPropertyBoolean("track-list/$i/external") == true
            // Kanal sayısı yalnızca SES parçalarında tanımlıdır.
            // Eski kod bu üç sorguyu video ve altyazı parçaları için de yapıyordu:
            // her biri libmpv'de "property not found" üretip logcat'i sel ile
            // dolduruyor ve parça başına 3 boş JNI çağrısı (ana iş parçacığında)
            // harcıyordu. Artık yalnızca audio parçalarında, 2 sorguyla okunuyor.
            val channelCount = if (type == "audio") {
                mpv.getPropertyInt("track-list/$i/demux-channel-count")
                    ?: mpv.getPropertyInt("track-list/$i/audio-channels")
            } else {
                null
            }
            val forced = (mpv.getPropertyBoolean("track-list/$i/forced") == true) || listOfNotNull(title, language).any {
                it.contains("forced", ignoreCase = true)
            }
            val selected = when (type) {
                "audio" -> (selectedAudioTrackId != null && selectedAudioTrackId == id) || selectedByFlag
                "sub" -> selectedSubtitleTrackId?.let { it == id } ?: selectedByFlag
                else -> selectedByFlag
            }

            when (type) {
                "audio" -> {
                    audioTracks += MpvTrack(
                        id = id,
                        type = type,
                        name = title ?: language ?: "Ses #$id",
                        language = language,
                        codec = codec,
                        channelCount = channelCount,
                        isSelected = selected,
                        isForced = false,
                        isExternal = external,
                        externalFilename = if (external) mpv.getPropertyString("track-list/$i/external-filename") else null,
                        title = title,
                        bitrate = mpv.getPropertyInt("track-list/$i/demux-bitrate")?.takeIf { it > 0 }
                    )
                }

                "sub" -> {
                    subtitleTracks += MpvTrack(
                        id = id,
                        type = type,
                        name = title ?: language ?: KitsugiStrings.get(R.string.altyazi_x, id),
                        language = language,
                        codec = codec,
                        channelCount = null,
                        isSelected = selected,
                        isForced = forced,
                        isExternal = external,
                        externalFilename = if (external) mpv.getPropertyString("track-list/$i/external-filename") else null,
                        title = title,
                        bitrate = mpv.getPropertyInt("track-list/$i/demux-bitrate")?.takeIf { it > 0 }
                    )
                }
            }
        }

        return MpvTrackSnapshot(
            audioTracks = audioTracks,
            subtitleTracks = subtitleTracks
        )
    }

    fun releasePlayer() {
        if (!initialized) return
        removeCallbacks(aspectReapplyRunnable)
        runCatching { destroy() }
            .onFailure { Log.w(TAG, "Failed to destroy libmpv view cleanly: ${it.message}") }
        contentDescriptors.values.forEach { runCatching { it.close() } }
        contentDescriptors.clear()
        initialized = false
        attachedSurface = null
        hasQueuedInitialMedia = false
        lastMediaRequestKey = null
        pendingInitialMediaUrl = null
        pendingInitialStartOption = null
    }

    override fun initOptions() {
        mpv.setOptionString("pause", "yes")
        mpv.setOptionString("config", "yes")
        val userFiles = context.getSharedPreferences("kitsugi_prefs", Context.MODE_PRIVATE)
            .getBoolean("mpv_user_files_enabled", true)
        mpv.setOptionString("load-scripts", if (userFiles) "yes" else "no")
        mpv.setOptionString("profile", "fast")
        mpv.setOptionString("ytdl", "no")
        // Match Aniyomi's AV1 film-grain workaround (mpv issue #14651).
        mpv.setOptionString("vd-lavc-film-grain", "cpu")
        setVo("gpu")
        mpv.setOptionString("gpu-context", "android")
        mpv.setOptionString("opengl-es", "yes")
        mpv.setOptionString("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
        // Preserve native ASS/SSA styling behavior on MPV.
        mpv.setOptionString("sub-ass-override", "no")
        mpv.setOptionString("sub-font", "Roboto")
        mpv.setOptionString("sub-use-margins", "yes")
        mpv.setOptionString("sub-ass-force-margins", "yes")
        // Altyazı yazı boyutu pencereye göre ölçeklenir; video esnetildiğinde (Doldur /
        // Yakınlaştır) altyazının da büyümesini engeller. Margin'ler görüntü moduna göre
        // applySubtitleWindowPolicy() içinde açılıp kapanır.
        mpv.setOptionString("sub-scale-by-window", "yes")
        mpv.setOptionString("sub-scale-with-window", "yes")
        mpv.setOptionString("sub-ass-scale-with-window", "no")
        mpv.setOptionString("hwdec", hardwareDecodeMode.toMpvHwdecValue())
        mpv.setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
        mpv.setOptionString("ao", "audiotrack,opensles")
        mpv.setOptionString("audio-set-media-role", "yes")

        // libmpv cannot consume AndroidCAStore directly. Never disable TLS verification.
        applyMpvTlsOptions(caFile?.absolutePath) { name, value -> mpv.setOptionString(name, value) }
        mpv.setOptionString("input-default-bindings", "yes")
        mpv.setOptionString("demuxer-max-bytes", "${64 * 1024 * 1024}")
        mpv.setOptionString("demuxer-max-back-bytes", "${64 * 1024 * 1024}")
        mpv.setOptionString("keep-open", "yes")
        mpv.setOptionString("softvol", "yes")
        mpv.setOptionString("volume-max", MPV_MAX_VOLUME_PERCENT.toInt().toString())
    }

    override fun postInitOptions() {
        mpv.setOptionString("save-position-on-quit", "no")
    }

    override fun observeProperties() {
        // Progress is polled by PlayerRuntimeController / MpvPlayerEngine.
    }

    private fun applyHeaders(headers: Map<String, String>) {
        if (headers.isEmpty()) {
            mpv.setPropertyString("http-header-fields", "")
            return
        }
        val raw = headers.entries
            // Range başlığı mpv'ye gitmez — mpv kendi range/seek isteklerini yönetir
            .filter { !it.key.equals("Range", ignoreCase = true) }
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .sortedWith(compareBy({ it.key.lowercase(Locale.ROOT) }, { it.value }))
            .joinToString(separator = ",") { (key, value) ->
                val escapedHeader = "$key: $value"
                    .replace("\\", "\\\\")
                    .replace(",", "\\,")
                escapedHeader
            }
        mpv.setPropertyString("http-header-fields", raw)
    }

    private fun buildMediaRequestKey(url: String, headers: Map<String, String>): String {
        val normalizedHeaders = headers.entries
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .sortedWith(compareBy({ it.key.lowercase(Locale.ROOT) }, { it.value }))
            .joinToString(separator = "|") { "${it.key.trim()}:${it.value.trim()}" }
        return "$url#$normalizedHeaders"
    }

    private fun MpvHardwareDecodeMode.toMpvHwdecValue(): String {
        return when (this) {
            MpvHardwareDecodeMode.LEGACY_DIRECT_COPY -> "mediacodec-copy"
            MpvHardwareDecodeMode.AUTO_SAFE -> "mediacodec-copy"
            MpvHardwareDecodeMode.HARDWARE_COPY -> "mediacodec-copy"
            MpvHardwareDecodeMode.HARDWARE_DIRECT -> "mediacodec,mediacodec-copy"
            MpvHardwareDecodeMode.DISABLED -> "no"
        }
    }

    private fun toMpvColor(color: Int): String {
        return String.format(Locale.US, "#%08X", color)
    }

    private fun applyCoverAspectScale() {
        val viewAspect = if (width > 0 && height > 0) {
            width.toFloat() / height.toFloat()
        } else {
            0f
        }
        val videoAspect = readVideoAspectRatio()

        if (videoAspect != null && videoAspect > 0f && viewAspect > 0f) {
            if (videoAspect > viewAspect) {
                scaleX = 1.0f
                scaleY = videoAspect / viewAspect
            } else {
                scaleX = viewAspect / videoAspect
                scaleY = 1.0f
            }
            return
        }

        // Fallback to a visible zoom when video metadata/aspect is unavailable.
        scaleX = MPV_COVER_FALLBACK_SCALE
        scaleY = MPV_COVER_FALLBACK_SCALE
    }

    private fun readVideoAspectRatio(): Float? {
        if (!initialized) return null

        // ÖNEMLİ: Önce `video-params/*` (kaynak video) okunur. `video-out-params/*` çıkış
        // katmanını yansıtır ve uyguladığımız `video-scale-x/y` değerlerinden etkilenebilir;
        // oradan okumak "Doldur" modunda ölçeğin kendi kendini büyütmesine yol açıyordu.
        val directAspect = runCatching {
            mpv.getPropertyDouble("video-params/aspect")
                ?: mpv.getPropertyDouble("video-out-params/aspect")
        }.getOrNull()
        if (directAspect != null && directAspect > 0.0) {
            return directAspect.toFloat()
        }

        val width = runCatching {
            mpv.getPropertyInt("video-params/w")
                ?: mpv.getPropertyInt("video-out-params/dw")
        }.getOrNull() ?: return null
        val height = runCatching {
            mpv.getPropertyInt("video-params/h")
                ?: mpv.getPropertyInt("video-out-params/dh")
        }.getOrNull() ?: return null
        if (width <= 0 || height <= 0) return null

        return width.toFloat() / height.toFloat()
    }

    companion object {
        private const val TAG = "KitsugiMpvSurfaceView"
        private const val MPV_COVER_FALLBACK_SCALE = 1.15f
        private const val MPV_MAX_VOLUME_PERCENT = 400.0
        private const val ASPECT_RETRY_DELAY_MS = 120L
        private const val MAX_ASPECT_RETRY_COUNT = 10
        private const val MIN_VIDEO_SCALE = 0.1f
        private const val MAX_VIDEO_SCALE = 10.0f
        private const val AUDIO_AMPLIFICATION_MIN_DB = 0
        private const val AUDIO_AMPLIFICATION_MAX_DB = 10
        private const val SUBTITLE_VERTICAL_OFFSET_MIN = -20
        private const val SUBTITLE_VERTICAL_OFFSET_MAX = 50
        private const val MPV_SUB_POS_AT_BOTTOM = 103.4
        private const val MPV_SUB_POS_AT_TOP = 72.4
        private const val MPV_SUB_MARGIN_Y_MIN = 0
        private const val MPV_SUB_MARGIN_Y_MAX = 60
    }
}

internal data class AspectScale(val scaleX: Float, val scaleY: Float)

internal fun aspectModeNeedsVideoAspect(mode: AspectMode): Boolean {
    return when (mode) {
        AspectMode.FULL_SCREEN,
        AspectMode.STRETCH,
        AspectMode.VERTICAL_STRETCH,
        AspectMode.HORIZONTAL_STRETCH -> true

        AspectMode.ORIGINAL,
        AspectMode.SLIGHT_ZOOM,
        AspectMode.CINEMA_ZOOM -> false
    }
}

internal fun readViewAspectRatio(width: Int, height: Int): Float {
    return if (width > 0 && height > 0) {
        width.toFloat() / height.toFloat()
    } else {
        0f
    }
}

internal fun resolveAspectScale(mode: AspectMode, viewAspect: Float, videoAspect: Float?): AspectScale {
    if (viewAspect <= 0f) {
        return AspectScale(scaleX = 1.0f, scaleY = 1.0f)
    }

    return when (mode) {
        AspectMode.ORIGINAL -> AspectScale(scaleX = 1.0f, scaleY = 1.0f)

        AspectMode.FULL_SCREEN -> {
            val safeVideoAspect = videoAspect?.takeIf { it > 0f }
                ?: return AspectScale(scaleX = 1.0f, scaleY = 1.0f)
            val uniformScale = if (safeVideoAspect > viewAspect) {
                safeVideoAspect / viewAspect
            } else {
                viewAspect / safeVideoAspect
            }
            AspectScale(scaleX = uniformScale, scaleY = uniformScale)
        }

        AspectMode.STRETCH -> {
            val safeVideoAspect = videoAspect?.takeIf { it > 0f }
                ?: return AspectScale(scaleX = 1.0f, scaleY = 1.0f)
            if (safeVideoAspect > viewAspect) {
                AspectScale(scaleX = 1.0f, scaleY = safeVideoAspect / viewAspect)
            } else {
                AspectScale(scaleX = viewAspect / safeVideoAspect, scaleY = 1.0f)
            }
        }

        AspectMode.SLIGHT_ZOOM -> AspectScale(scaleX = 1.15f, scaleY = 1.15f)

        AspectMode.CINEMA_ZOOM -> AspectScale(scaleX = 1.33f, scaleY = 1.33f)

        AspectMode.VERTICAL_STRETCH -> {
            val safeVideoAspect = videoAspect?.takeIf { it > 0f }
                ?: return AspectScale(scaleX = 1.0f, scaleY = 1.0f)
            if (safeVideoAspect > viewAspect) {
                val uniformScale = safeVideoAspect / viewAspect
                AspectScale(scaleX = uniformScale, scaleY = uniformScale)
            } else {
                AspectScale(scaleX = 1.0f, scaleY = 1.0f)
            }
        }

        AspectMode.HORIZONTAL_STRETCH -> {
            val safeVideoAspect = videoAspect?.takeIf { it > 0f }
                ?: return AspectScale(scaleX = 1.0f, scaleY = 1.0f)
            if (safeVideoAspect < viewAspect) {
                val uniformScale = viewAspect / safeVideoAspect
                AspectScale(scaleX = uniformScale, scaleY = uniformScale)
            } else {
                AspectScale(scaleX = 1.0f, scaleY = 1.0f)
            }
        }
    }
}
