package com.kitsugi.animelist.core.player.audio.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import com.kitsugi.animelist.core.player.MpvAudioFocusController
import com.kitsugi.animelist.core.player.MpvTrustStore
import com.kitsugi.animelist.core.player.engine.applyMpvTlsOptions
import com.kitsugi.animelist.core.player.engine.MpvPlaybackPolicy
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.MPVNode
import `is`.xyz.mpv.Utils
import java.io.File
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Yüzeysiz (`vo=null`, `vid=no`) çalışan evrensel `libmpv` ses oynatma arka ucu.
 *
 * Media3 + FFmpeg uzantısının açamadığı tüm konteyner ve kodekleri (`.wma`, `.ape`, `.wv`,
 * `.mpc`, `.tta`, `.tak`, ham `.dts`/`.thd`, `.caf`, `.aiff`, `.dsf`/`.dff`, bozuk başlıklı
 * konteynerler vb.) FFmpeg `libavformat` + `libavcodec` zinciriyle sorunsuz oynatır.
 */
internal class KitsugiMpvAudioHost(
    context: Context,
    private val listener: Listener
) : BaseMPVView(context.applicationContext, null), MPV.EventObserver {

    interface Listener {
        fun onPlayingChanged(playing: Boolean)
        fun onBufferingChanged(buffering: Boolean)
        fun onProgressChanged(positionMs: Long, durationMs: Long)
        fun onStreamSpecsChanged(specs: AudioStreamSpecs)
        fun onEnded()
        fun onError(message: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val contentDescriptors = LinkedHashMap<String, ParcelFileDescriptor>()
    private var caFile: File? = null
    private var initialized = false
    private var fileLoaded = false
    private var awaitingStartFile = false
    private var eofDispatched = false
    private var ducked = false
    private var boostDb = 0f
    private var currentSpeed = 1.0f
    private var preservePitch = true

    private val audioFocus = MpvAudioFocusController(
        context = context.applicationContext,
        isPlaying = { isPlayingNow() },
        pauseForFocus = { setPausedInternal(true, abandonFocus = false) },
        resumeForFocus = { setPausedInternal(false, abandonFocus = false) },
        onDuck = { shouldDuck ->
            ducked = shouldDuck
            applyEffectiveVolume()
        },
        onNoisy = {
            setPausedInternal(true, abandonFocus = true)
        }
    )

    private val observedProps = mapOf(
        "time-pos" to MPV.mpvFormat.MPV_FORMAT_DOUBLE,
        "duration" to MPV.mpvFormat.MPV_FORMAT_DOUBLE,
        "pause" to MPV.mpvFormat.MPV_FORMAT_FLAG,
        "paused-for-cache" to MPV.mpvFormat.MPV_FORMAT_FLAG,
        "core-idle" to MPV.mpvFormat.MPV_FORMAT_FLAG,
        "eof-reached" to MPV.mpvFormat.MPV_FORMAT_FLAG,
        "audio-codec-name" to MPV.mpvFormat.MPV_FORMAT_STRING
    )

    fun ensureReady() {
        if (initialized) return
        caFile = runCatching { MpvTrustStore.install(context) }.getOrNull()
        runCatching { Utils.copyAssets(context) }
        val audioCacheDir = File(context.cacheDir, "mpv_audio_cache").apply { mkdirs() }
        val audioConfigDir = File(context.filesDir, "mpv_audio_cfg").apply { mkdirs() }
        initialize(
            configDir = audioConfigDir.path,
            cacheDir = audioCacheDir.path
        )
        initialized = true
        mpv.addObserver(this)
        observedProps.forEach { (prop, format) ->
            mpv.observeProperty(prop, format)
        }
    }

    override fun initOptions() {
        setVo("null")
        mpv.setOptionString("vo", "null")
        mpv.setOptionString("vid", "no")
        mpv.setOptionString("sid", "no")
        mpv.setOptionString("audio-display", "no")
        mpv.setOptionString("config", "no")
        mpv.setOptionString("load-scripts", "no")
        mpv.setOptionString("ytdl", "no")
        mpv.setOptionString("pause", "yes")
        mpv.setOptionString("keep-open", "yes")
        mpv.setOptionString("ao", "audiotrack,opensles")
        mpv.setOptionString("audio-set-media-role", "yes")
        mpv.setOptionString("softvol", "yes")
        mpv.setOptionString("volume-max", "400")
        mpv.setOptionString("demuxer-max-bytes", "${32 * 1024 * 1024}")
        mpv.setOptionString("demuxer-max-back-bytes", "${16 * 1024 * 1024}")
        applyMpvTlsOptions(caFile?.absolutePath) { name, value ->
            mpv.setOptionString(name, value)
        }
    }

    override fun postInitOptions() {
        mpv.setOptionString("save-position-on-quit", "no")
    }

    override fun observeProperties() {
        // Özellik gözlemleri ensureReady() içinde kaydedilir.
    }

    fun load(uriOrPath: String, startPositionMs: Long = 0L, autoPlay: Boolean = true) {
        ensureReady()
        val resolved = try {
            resolveSource(uriOrPath)
        } catch (t: Throwable) {
            Log.w(TAG, "MPV ses kaynağı açılamadı: ${t.message}")
            listener.onError(t.localizedMessage ?: "Ses dosyası açılamadı")
            return
        }

        awaitingStartFile = true
        fileLoaded = false
        eofDispatched = false
        listener.onBufferingChanged(true)

        runCatching {
            mpv.setPropertyString("vid", "no")
            mpv.setPropertyString("aid", "auto")
            applySpeedAndPitch()
            applyEffectiveVolume()
            val startOpt = startPositionMs.takeIf { it > 0L }
                ?.let { String.format(Locale.US, "start=%.3f", it / 1000.0) }
            val version = mpv.getPropertyString("mpv-version") ?: ""
            mpv.command(*MpvPlaybackPolicy.loadfile(resolved, startOpt, version))
            if (autoPlay && audioFocus.request()) {
                mpv.setPropertyBoolean("pause", false)
            } else {
                mpv.setPropertyBoolean("pause", true)
            }
        }.onFailure { t ->
            Log.w(TAG, "MPV loadfile başarısız: ${t.message}")
            listener.onBufferingChanged(false)
            listener.onError(t.localizedMessage ?: "Ses oynatılamadı")
        }
    }

    fun play() {
        if (!initialized) return
        if (!audioFocus.request()) return
        runCatching {
            if (mpv.getPropertyBoolean("eof-reached") == true) {
                eofDispatched = false
                mpv.setPropertyDouble("time-pos", 0.0)
            }
            mpv.setPropertyBoolean("pause", false)
        }
    }

    fun pause() {
        setPausedInternal(true, abandonFocus = true)
    }

    fun stopPlayback() {
        if (!initialized) return
        audioFocus.abandonFocus()
        runCatching {
            mpv.setPropertyBoolean("pause", true)
            mpv.command("stop")
        }
        closeAllContentDescriptors()
        fileLoaded = false
        eofDispatched = false
    }

    fun seekTo(positionMs: Long) {
        if (!initialized) return
        eofDispatched = false
        val seconds = (positionMs.coerceAtLeast(0L)) / 1000.0
        runCatching { mpv.setPropertyDouble("time-pos", seconds) }
    }

    fun setSpeedAndPitch(speed: Float, preservePitch: Boolean) {
        this.currentSpeed = speed.coerceIn(0.25f, 3.0f)
        this.preservePitch = preservePitch
        if (initialized) applySpeedAndPitch()
    }

    fun setVolumeBoostDb(db: Float) {
        this.boostDb = db.coerceIn(0f, 12f)
        if (initialized) applyEffectiveVolume()
    }

    fun isPlayingNow(): Boolean {
        if (!initialized) return false
        return runCatching { mpv.getPropertyBoolean("pause") == false }.getOrDefault(false)
    }

    fun currentPositionMs(): Long {
        if (!initialized) return 0L
        val sec = runCatching {
            mpv.getPropertyDouble("time-pos/full") ?: mpv.getPropertyDouble("time-pos") ?: 0.0
        }.getOrDefault(0.0)
        return (sec * 1000.0).roundToLong().coerceAtLeast(0L)
    }

    fun durationMs(): Long {
        if (!initialized) return 0L
        val sec = runCatching { mpv.getPropertyDouble("duration/full") ?: 0.0 }.getOrDefault(0.0)
        return (sec * 1000.0).roundToLong().coerceAtLeast(0L)
    }

    fun releaseHost() {
        audioFocus.release()
        if (initialized) {
            runCatching { mpv.removeObserver(this) }
            runCatching { destroy() }
                .onFailure { Log.w(TAG, "MPV ses motoru kapatma uyarısı: ${it.message}") }
            initialized = false
        }
        closeAllContentDescriptors()
    }

    private fun setPausedInternal(paused: Boolean, abandonFocus: Boolean) {
        if (!initialized) return
        if (paused && abandonFocus) {
            audioFocus.userPaused()
            audioFocus.abandonFocus()
        }
        runCatching { mpv.setPropertyBoolean("pause", paused) }
    }

    private fun applySpeedAndPitch() {
        runCatching {
            mpv.setPropertyBoolean("audio-pitch-correction", preservePitch)
            mpv.setPropertyDouble("speed", currentSpeed.toDouble())
        }
    }

    private fun applyEffectiveVolume() {
        val linearGain = 10.0.pow(boostDb.toDouble() / 20.0)
        val duckFactor = if (ducked) 0.25 else 1.0
        val mpvPercent = (100.0 * linearGain * duckFactor).coerceIn(0.0, 400.0)
        runCatching { mpv.setPropertyDouble("volume", mpvPercent) }
    }

    private fun resolveSource(uriOrPath: String): String {
        val stale = contentDescriptors.keys.filter { it != uriOrPath }
        stale.forEach { key ->
            contentDescriptors.remove(key)?.let { pfd -> runCatching { pfd.close() } }
        }
        if (uriOrPath.startsWith("/")) return uriOrPath
        val uri = Uri.parse(uriOrPath)
        return when (uri.scheme?.lowercase(Locale.ROOT)) {
            "content" -> {
                val pfd = contentDescriptors.getOrPut(uriOrPath) {
                    requireNotNull(context.contentResolver.openFileDescriptor(uri, "r")) {
                        "İçerik bağlantısı açılamadı"
                    }
                }
                "fd://${pfd.fd}"
            }
            "file" -> uri.path ?: uriOrPath
            else -> uriOrPath
        }
    }

    private fun closeAllContentDescriptors() {
        contentDescriptors.values.forEach { pfd -> runCatching { pfd.close() } }
        contentDescriptors.clear()
    }

    private fun readSpecsSnapshot(): AudioStreamSpecs {
        if (!initialized) return AudioStreamSpecs()
        val codec = runCatching { mpv.getPropertyString("audio-codec-name") }.getOrNull()
        val sampleRate = runCatching { mpv.getPropertyInt("audio-params/samplerate") }.getOrNull() ?: 0
        val channels = runCatching { mpv.getPropertyInt("audio-params/channel-count") }.getOrNull() ?: 0
        val bitrate = runCatching { (mpv.getPropertyInt("audio-bitrate") ?: 0) / 1000 }.getOrDefault(0)
        val container = runCatching {
            mpv.getPropertyString("file-format")?.substringBefore(',')
        }.getOrNull()
        return AudioStreamSpecs(
            codec = codec,
            sampleRateHz = sampleRate,
            channelCount = channels,
            bitrateKbps = bitrate.coerceAtLeast(0),
            container = container
        )
    }

    private inline fun onMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post { block() }
        }
    }

    override fun eventProperty(property: String) {}

    override fun eventProperty(property: String, value: Boolean) = onMain {
        when (property) {
            "pause" -> listener.onPlayingChanged(!value)
            "paused-for-cache" -> listener.onBufferingChanged(value)
            "eof-reached" -> {
                if (value && fileLoaded && !awaitingStartFile && !eofDispatched) {
                    eofDispatched = true
                    listener.onEnded()
                }
            }
        }
    }

    override fun eventProperty(property: String, value: Double) = onMain {
        if (awaitingStartFile) return@onMain
        when (property) {
            "time-pos", "duration" -> {
                if (value.isFinite()) {
                    listener.onProgressChanged(currentPositionMs(), durationMs())
                }
            }
        }
    }

    override fun eventProperty(property: String, value: Long) {}

    override fun eventProperty(property: String, value: String) = onMain {
        if (property == "audio-codec-name" && value.isNotBlank()) {
            listener.onStreamSpecsChanged(readSpecsSnapshot())
        }
    }

    override fun eventProperty(property: String, value: MPVNode) {}

    override fun event(eventId: Int, data: MPVNode) = onMain {
        when (eventId) {
            MPV.mpvEvent.MPV_EVENT_START_FILE -> {
                awaitingStartFile = false
                fileLoaded = false
                eofDispatched = false
            }
            MPV.mpvEvent.MPV_EVENT_FILE_LOADED -> {
                fileLoaded = true
                listener.onBufferingChanged(false)
                listener.onProgressChanged(currentPositionMs(), durationMs())
                listener.onStreamSpecsChanged(readSpecsSnapshot())
            }
            MPV.mpvEvent.MPV_EVENT_END_FILE -> {
                listener.onBufferingChanged(false)
                if (!fileLoaded && !awaitingStartFile) {
                    listener.onError("Ses biçimi çözümlenemedi")
                } else if (fileLoaded && !eofDispatched) {
                    val eof = runCatching { mpv.getPropertyBoolean("eof-reached") == true }.getOrDefault(false)
                    if (eof) {
                        eofDispatched = true
                        listener.onEnded()
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "KitsugiMpvAudioHost"
    }
}
