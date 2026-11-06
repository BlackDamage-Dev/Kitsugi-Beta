package com.kitsugi.animelist.core.music

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * 🎛️ Prosedürel Ses Çalıcısı
 *
 * `AudioTrack` üzerinden gerçek zamanlı PCM üretir. Dosya yoktur,
 * internet yoktur, lisans yoktur — ses matematikle sentezlenir.
 *
 * Mimari:
 *  - Üretim thread'i (yüksek öncelikli) sabit boyutlu PCM blokları yazar
 *  - Her ses tanımı bir [SoundSynth] implementasyonudur
 *  - Sentézleyiciler durum tutabilir (filtre geçmişi gibi)
 *
 * Performans notu: 48 kHz stereo float üretimi günümüz cihazlarında
 * tek çekirdeğin çok küçük bir kısmını kullanır. Batch üretim sayesinde
 * thread uyanma maliyeti de düşüktür.
 */
class ProceduralSoundPlayer(
    private val context: Context,
    initialSoundId: String? = null
) {

    companion object {
        private const val TAG = "ProceduralSoundPlayer"

        /** Örnekleme frekansı — tüm cihazlarda güvenli. */
        private const val SAMPLE_RATE = 44_100

        /** Blok başına örnek sayısı (~23 ms @ 44.1 kHz). */
        private const val BLOCK_FRAMES = 1024

        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_STEREO
        private const val ENCODING = AudioFormat.ENCODING_PCM_FLOAT

        /** Limiter eşiği — altında hiçbir renklendirme yok. */
        private const val LIMITER_KNEE = 0.7f
    }

    private var audioTrack: AudioTrack? = null
    private var renderJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var synth: SoundSynth? = null

    /** Ses seviyesi — [setVolume] ile ana thread'den değiştirilir. */
    @Volatile
    private var volume: Float = 0.5f

    /**
     * Not: `baseVolume`, [AmbientAudioEngine.setMasterVolume] çağrısında
     * preset katmanlarının orijinal seviyesini hatırlamak için tutulur.
     */
    @Volatile
    var baseVolume: Float = 0.5f
        private set

    /**
     * Preset katmanının TANIMDAN gelen ses seviyesi (master çarpanı hariç).
     * [AmbientAudioEngine.setMasterVolume] çağrıldığında katman seviyesi
     * buradan yeniden hesaplanır.
     */
    @Volatile
    var layerVolume: Float = 0.5f

    @Volatile
    private var playing = false

    @Volatile
    private var soundId: String? = initialSoundId

    /** Yumuşak kapanış için. */
    @Volatile
    private var stopRequested = false

    init {
        if (initialSoundId != null) {
            // Henüz start() çağrılmadı; sadece tanımı hazırla.
            soundId = initialSoundId
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Genel API
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Belirtilen ses tanımını çalmaya başlar.
     * Önceden çalan varsa önce durdurulur.
     */
    fun play(id: String) {
        if (playing && soundId == id) return
        stopInternal(immediate = true)
        soundId = id
        startInternal()
    }

    /** Mevcut sesi (veya [play] ile atanan sesi) başlatır. */
    fun start() {
        if (playing) return
        startInternal()
    }

    fun pause() {
        if (!playing) return
        playing = false
        renderJob?.cancel()
        renderJob = null
        runCatching { audioTrack?.pause() }
    }

    fun resume() {
        if (playing) return
        if (audioTrack == null) {
            startInternal()
        } else {
            runCatching { audioTrack?.play() }
            playing = true
            renderJob?.cancel()
            renderJob = launchRenderLoop()
        }
    }

    fun stop() {
        stopInternal(immediate = true)
    }

    /**
     * Sesi kademeli olarak kısıp durdurur (tık sesi olmadan).
     */
    fun fadeOutAndStop(durationMs: Long) {
        if (!playing) {
            stopInternal(immediate = true)
            return
        }
        scope.launch {
            val steps = (durationMs / 50L).coerceAtLeast(1).toInt()
            val startVol = volume
            val delta = startVol / steps
            var current = startVol
            repeat(steps) {
                current -= delta
                setVolume(current.coerceAtLeast(0f))
                delay(50L)
            }
            stopInternal(immediate = true)
        }
    }

    /**
     * Ses seviyesini ayarlar (0..1). Çalma sırasında güvenlidir.
     */
    fun setVolume(value: Float) {
        val v = value.coerceIn(0f, 1f)
        volume = v
        baseVolume = v
        runCatching { audioTrack?.setVolume(v) }
    }

    /** Fade sırasında track'e dokunmadan yalnızca hedef değeri günceller. */
    private fun setVolumeTarget(value: Float) {
        volume = value.coerceIn(0f, 1f)
    }

    /** Şu anda çalıyor mu? */
    fun isPlaying(): Boolean = playing

    /** Çalan sesin kimliği. */
    fun currentSoundId(): String? = soundId

    // ─────────────────────────────────────────────────────────────────────
    //  Dahili
    // ─────────────────────────────────────────────────────────────────────

    private fun startInternal() {
        val id = soundId ?: run {
            Log.w(TAG, "Ses kimliği yok — play(id) çağırın")
            return
        }

        val newSynth = SynthFactory.create(id)
        if (newSynth == null) {
            Log.w(TAG, "Bilinmeyen prosedürel ses kimliği: $id")
            return
        }
        synth = newSynth
        synth?.reset()

        val minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING)
            .let { if (it > 0) it else BLOCK_FRAMES * 2 * 4 }
        val bufferSize = max(minBuffer * 2, BLOCK_FRAMES * 2 * 4 * 4)

        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(ENCODING)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_OUT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.getOrElse { error ->
            Log.e(TAG, "AudioTrack oluşturulamadı", error)
            null
        } ?: return

        audioTrack = track
        stopRequested = false
        // Sesi 0'dan başlat — çağıran taraf fade ile yükseltir.
        // Doğrudan `volume` ile başlatmak "pat" sesine yol açar.
        runCatching { track.setVolume(0f) }
        runCatching { track.play() }
        playing = true
        renderJob = launchRenderLoop()
    }

    private fun launchRenderLoop(): Job = scope.launch {
        val track = audioTrack ?: return@launch
        val activeSynth = synth ?: return@launch
        val left = FloatArray(BLOCK_FRAMES)
        val right = FloatArray(BLOCK_FRAMES)

        try {
            while (playing && !stopRequested) {
                // Stereo üret
                activeSynth.render(left, right, BLOCK_FRAMES)

                // Float dizisini interleaved hale getir
                val interleaved = FloatArray(BLOCK_FRAMES * 2)
                var i = 0
                while (i < BLOCK_FRAMES) {
                    interleaved[i * 2] = clampSample(left[i])
                    interleaved[i * 2 + 1] = clampSample(right[i])
                    i++
                }

                var written = 0
                while (written < interleaved.size && playing && !stopRequested) {
                    val n = runCatching {
                        track.write(interleaved, written, interleaved.size - written, AudioTrack.WRITE_BLOCKING)
                    }.getOrElse { -1 }
                    if (n <= 0) break
                    written += n
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Sentez döngüsü kesintiye uğradı", t)
        }
    }

    private fun stopInternal(immediate: Boolean) {
        stopRequested = true
        playing = false
        renderJob?.cancel()
        renderJob = null

        val track = audioTrack
        audioTrack = null
        if (track != null) {
            runCatching {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            }
            runCatching { track.release() }
        }
        synth = null
        if (!immediate) {
            soundId = null
        }
    }

    /**
     * 🎚️ Yumuşak limiter (soft-knee).
     *
     * Altı kanal aynı anda çalarken toplam genlik 1.0'ı aşabilir. Sert
     * kırpma (`v.coerceIn(-1f, 1f)`) duyulabilir bozulma ("cızırtı")
     * üretir. Bunun yerine:
     *
     *  - |v| < 0.7  → doğrusal (hiç dokunma, tam sadakat)
     *  - |v| ≥ 0.7  → hiperbolik yumuşatma, asimptotik olarak 1.0'a yaklaşır
     *
     * Sonuç: hiçbir zaman 1.0'ı geçmez, ama tepe noktaları kesilmez —
     * taşma yerine sıkışır.
     */
    private fun clampSample(v: Float): Float {
        if (v.isNaN()) return 0f
        val abs = kotlin.math.abs(v)
        if (abs <= LIMITER_KNEE) return v

        // Knee üstünde: y = knee + (limit - knee) * tanh((abs - knee) / (limit - knee))
        val over = abs - LIMITER_KNEE
        val span = 1f - LIMITER_KNEE
        val compressed = LIMITER_KNEE + span * kotlin.math.tanh(over / span)
        return if (v < 0f) -compressed else compressed
    }
}

// ═════════════════════════════════════════════════════════════════════════
//  SENTEZLEYİCİ ARAYÜZÜ
// ═════════════════════════════════════════════════════════════════════════

/**
 * Bir prosedürel sesin üreticisi.
 *
 * Implementasyonlar durum tutabilir (filtre geçmişi, faz vb.).
 * [render] gerçek zamanlı thread'de çağrılır — **bloklamayın**,
 * bellek ayırmayın, log yazmayın.
 */
interface SoundSynth {
    /** Durumu sıfırlar (tekrar başlatma için). */
    fun reset()

    /**
     * [frames] adet stereo örnek üretir.
     * @param left  Sol kanal çıkışı (uzunluk ≥ frames)
     * @param right Sağ kanal çıkışı (uzunluk ≥ frames)
     */
    fun render(left: FloatArray, right: FloatArray, frames: Int)
}

// ═════════════════════════════════════════════════════════════════════════
//  YARDIMCI FİLTRELER
// ═════════════════════════════════════════════════════════════════════════

/**
 * Tek kutuplu (one-pole) alçak/yüksek geçiren filtre.
 * Gürültü şekillendirmede ucuz ve kararlıdır.
 */
internal class OnePoleFilter {
    private var state = 0f

    /** @param cutoffHz kesim frekansı; @param sampleRate örnekleme frekansı */
    fun lowPass(input: Float, cutoffHz: Float, sampleRate: Int): Float {
        val dt = 1f / sampleRate
        val rc = 1f / (2f * PI.toFloat() * cutoffHz)
        val alpha = dt / (rc + dt)
        state += alpha * (input - state)
        return state
    }

    fun highPass(input: Float, cutoffHz: Float, sampleRate: Int): Float {
        return input - lowPass(input, cutoffHz, sampleRate)
    }

    fun reset() { state = 0f }
}

/**
 * İkinci derece bant geçiren filtre (rezonanslı).
 * Yağmur/şırıltı gibi dar bantlı gürültüler için.
 */
internal class BandPassFilter {
    private var x1 = 0f
    private var x2 = 0f
    private var y1 = 0f
    private var y2 = 0f
    private var b0 = 0f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f

    fun setParams(centerHz: Float, q: Float, sampleRate: Int) {
        val w0 = 2f * PI.toFloat() * centerHz / sampleRate
        val cosW0 = kotlin.math.cos(w0)
        val alpha = kotlin.math.sin(w0) / (2f * q)
        val a0 = 1f + alpha
        b0 = alpha / a0
        b1 = 0f
        b2 = -alpha / a0
        a1 = -2f * cosW0 / a0
        a2 = (1f - alpha) / a0
    }

    fun process(input: Float): Float {
        val out = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = input
        y2 = y1; y1 = out
        return out
    }

    fun reset() {
        x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f
    }
}

/**
 * Basit tek satırlık gecikme hattı — reverb/vuruş efektleri için.
 */
internal class DelayLine(private val maxSamples: Int) {
    private val buffer = FloatArray(maxSamples)
    private var index = 0
    private var delaySamples = 0
    private var feedback = 0f
    private var mix = 0f

    fun configure(delaySamples: Int, feedback: Float, mix: Float) {
        this.delaySamples = delaySamples.coerceIn(1, maxSamples - 1)
        this.feedback = feedback.coerceIn(0f, 0.95f)
        this.mix = mix.coerceIn(0f, 1f)
    }

    fun process(input: Float): Float {
        val readIndex = (index - delaySamples + maxSamples) % maxSamples
        val delayed = buffer[readIndex]
        buffer[index] = input + delayed * feedback
        index = (index + 1) % maxSamples
        return input * (1f - mix) + delayed * mix
    }

    fun reset() {
        buffer.fill(0f)
        index = 0
    }
}

// ═════════════════════════════════════════════════════════════════════════
//  SENTEZLEYİCİ FABRİKASI
// ═════════════════════════════════════════════════════════════════════════

/**
 * Ses kimliğinden uygun [SoundSynth] üretir.
 */
object SynthFactory {

    fun create(id: String): SoundSynth? = when (id) {
        com.kitsugi.animelist.data.music.ProceduralSoundCatalog.Ids.WHITE_NOISE ->
            NoiseSynth(NoiseSynth.Color.WHITE)
        com.kitsugi.animelist.data.music.ProceduralSoundCatalog.Ids.PINK_NOISE ->
            NoiseSynth(NoiseSynth.Color.PINK)
        com.kitsugi.animelist.data.music.ProceduralSoundCatalog.Ids.BROWN_NOISE ->
            NoiseSynth(NoiseSynth.Color.BROWN)
        com.kitsugi.animelist.data.music.ProceduralSoundCatalog.Ids.RAIN_SOFT ->
            RainSynth(intensity = 0.45f)
        com.kitsugi.animelist.data.music.ProceduralSoundCatalog.Ids.RAIN_HEAVY ->
            RainSynth(intensity = 0.9f, thunderChance = 0.00006f)
        com.kitsugi.animelist.data.music.ProceduralSoundCatalog.Ids.THUNDER ->
            ThunderSynth()
        com.kitsugi.animelist.data.music.ProceduralSoundCatalog.Ids.FIRE_CRACKLE ->
            FireSynth()
        com.kitsugi.animelist.data.music.ProceduralSoundCatalog.Ids.WIND ->
            WindSynth()
        com.kitsugi.animelist.data.music.ProceduralSoundCatalog.Ids.OCEAN_WAVES ->
            OceanSynth()
        com.kitsugi.animelist.data.music.ProceduralSoundCatalog.Ids.STREAM ->
            StreamSynth()
        com.kitsugi.animelist.data.music.ProceduralSoundCatalog.Ids.FOREST_BIRDS ->
            BirdsSynth()
        com.kitsugi.animelist.data.music.ProceduralSoundCatalog.Ids.CRICKETS ->
            CricketsSynth()
        com.kitsugi.animelist.data.music.ProceduralSoundCatalog.Ids.CAFE ->
            CafeSynth()
        com.kitsugi.animelist.data.music.ProceduralSoundCatalog.Ids.CITY_NIGHT ->
            CitySynth()
        com.kitsugi.animelist.data.music.ProceduralSoundCatalog.Ids.TRAIN ->
            TrainSynth()
        com.kitsugi.animelist.data.music.ProceduralSoundCatalog.Ids.PIANO_AMBIENT ->
            GenerativeMelodySynth(GenerativeMelodySynth.Timbre.PIANO)
        com.kitsugi.animelist.data.music.ProceduralSoundCatalog.Ids.MUSIC_BOX ->
            GenerativeMelodySynth(GenerativeMelodySynth.Timbre.MUSIC_BOX)
        com.kitsugi.animelist.data.music.ProceduralSoundCatalog.Ids.KOTO ->
            GenerativeMelodySynth(GenerativeMelodySynth.Timbre.KOTO)
        com.kitsugi.animelist.data.music.ProceduralSoundCatalog.Ids.SYNTH_PAD ->
            PadSynth()
        com.kitsugi.animelist.data.music.ProceduralSoundCatalog.Ids.DARK_DRONE ->
            DroneSynth()
        com.kitsugi.animelist.data.music.ProceduralSoundCatalog.Ids.HEARTBEAT ->
            HeartbeatSynth()
        com.kitsugi.animelist.data.music.ProceduralSoundCatalog.Ids.CLOCK ->
            ClockSynth()
        // ── Binaural serisi (kulaklık gerekir) ──────────────────────────
        com.kitsugi.animelist.data.music.ProceduralSoundCatalog.Ids.BINAURAL_DELTA ->
            BinauralSynth(beatHz = 2.5f)
        com.kitsugi.animelist.data.music.ProceduralSoundCatalog.Ids.BINAURAL_THETA ->
            BinauralSynth(beatHz = 6f)
        com.kitsugi.animelist.data.music.ProceduralSoundCatalog.Ids.BINAURAL_ALPHA ->
            BinauralSynth(beatHz = 10f)
        com.kitsugi.animelist.data.music.ProceduralSoundCatalog.Ids.BINAURAL_BETA ->
            BinauralSynth(beatHz = 18f)
        com.kitsugi.animelist.data.music.ProceduralSoundCatalog.Ids.BINAURAL_GAMMA ->
            BinauralSynth(beatHz = 40f)
        com.kitsugi.animelist.data.music.ProceduralSoundCatalog.Ids.SINGING_BOWL ->
            SingingBowlSynth()
        com.kitsugi.animelist.data.music.ProceduralSoundCatalog.Ids.WIND_CHIME ->
            WindChimeSynth()
        else -> null
    }
}
