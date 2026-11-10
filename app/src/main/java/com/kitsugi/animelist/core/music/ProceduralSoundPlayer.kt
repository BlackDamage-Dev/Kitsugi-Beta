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
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

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

    /**
     * Çalar kendi kendine öldüğünde (AudioTrack `DEAD_OBJECT`, tampon taşması,
     * sentez istisnası) çağrılır.
     *
     * Motor bu geri çağırımı kanalı `ERROR` durumuna almak için kullanır.
     * Bu olmadan kanal sonsuza kadar "çalıyor" görünüyor, kullanıcı ses
     * duymuyor ve panelden hiçbir şey yapamıyordu.
     */
    @Volatile
    var onRenderFailure: (() -> Unit)? = null

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
    fun play(id: String): Boolean {
        if (playing && soundId == id) return true
        stopInternal(immediate = true)
        soundId = id
        return startInternal()
    }

    /** Mevcut sesi (veya [play] ile atanan sesi) başlatır. */
    fun start(): Boolean {
        if (playing) return true
        return startInternal()
    }

    fun pause() {
        if (!playing) return
        playing = false
        renderJob?.cancel()
        renderJob = null
        runCatching { audioTrack?.pause() }
    }

    fun resume(): Boolean {
        if (playing) return true
        val track = audioTrack
        return if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
            startInternal()
        } else {
            val ok = runCatching {
                track.play()
                track.state == AudioTrack.STATE_INITIALIZED
            }.getOrElse { false }
            if (ok) {
                playing = true
                renderJob?.cancel()
                renderJob = launchRenderLoop()
                true
            } else {
                startInternal()
            }
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
     * Ses kontrolü tamamen DSP yazılım katmanında örnek bazlı yapıldığı için
     * donanım DAC / mikser kısma hatalarından bağımsızdır.
     */
    fun setVolume(value: Float) {
        val v = value.coerceIn(0f, 1f)
        volume = v
        baseVolume = v
        runCatching { audioTrack?.setVolume(1.0f) }
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

    private fun startInternal(): Boolean {
        val id = soundId ?: run {
            Log.w(TAG, "Ses kimliği yok — play(id) çağırın")
            return false
        }

        val newSynth = SynthFactory.create(id)
        if (newSynth == null) {
            Log.w(TAG, "Bilinmeyen prosedürel ses kimliği: $id")
            return false
        }
        synth = newSynth
        synth?.reset()

        val minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING)
        val safeMin = if (minBuffer > 0) minBuffer else BLOCK_FRAMES * 2 * 2
        val bufferSize = max(safeMin * 2, BLOCK_FRAMES * 2 * 2 * 4)

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
        }

        if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
            Log.e(TAG, "AudioTrack initialize edilemedi: state=${track?.state}")
            runCatching { track?.release() }
            return false
        }

        audioTrack = track
        stopRequested = false
        runCatching { track.setVolume(1.0f) }

        val playOk = runCatching {
            track.play()
            track.state == AudioTrack.STATE_INITIALIZED
        }.getOrElse { false }

        if (!playOk) {
            Log.e(TAG, "AudioTrack play durumuna geçemedi: playState=${track.playState}")
            runCatching { track.release() }
            audioTrack = null
            return false
        }

        playing = true
        renderJob = launchRenderLoop()
        return true
    }

    private fun launchRenderLoop(): Job = scope.launch {
        val track = audioTrack ?: return@launch
        val activeSynth = synth ?: return@launch
        val left = FloatArray(BLOCK_FRAMES)
        val right = FloatArray(BLOCK_FRAMES)
        val pcmShort = ShortArray(BLOCK_FRAMES * 2)

        var failed = false

        try {
            while (playing && !stopRequested) {
                // Stereo üret
                activeSynth.render(left, right, BLOCK_FRAMES)
                val curVol = volume

                var i = 0
                while (i < BLOCK_FRAMES) {
                    val sL = clampSample(left[i] * curVol)
                    val sR = clampSample(right[i] * curVol)
                    pcmShort[i * 2] = (sL * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                    pcmShort[i * 2 + 1] = (sR * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                    i++
                }

                var written = 0
                while (written < pcmShort.size && playing && !stopRequested) {
                    val n = runCatching {
                        track.write(pcmShort, written, pcmShort.size - written)
                    }.getOrElse { -1 }
                    if (n < 0) {
                        // ⚠️ Eskiden burada yalnızca `break` vardı: dış döngü
                        // dönmeye devam ediyor, her turda yeniden sentez yapıp
                        // yine hata alıyordu. Altı kanalda bu, tüm çekirdekleri
                        // %100'de tutup arayüzü kilitliyordu ("uygulama kafayı
                        // yiyor"). Artık çalar kapanır ve motor haberdar edilir.
                        Log.e(TAG, "AudioTrack yazma hatası: $n — çalar kapatılıyor")
                        failed = true
                        break
                    }
                    if (n == 0) {
                        delay(5L)
                        continue
                    }
                    written += n
                }

                if (failed) break

                // Track sistem tarafından öldürüldüyse (ör. başka bir uygulama
                // ses donanımını sıfırladı) döngüyü bitir.
                if (track.state != AudioTrack.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioTrack durumu bozuldu: ${track.state} — çalar kapatılıyor")
                    failed = true
                    break
                }
            }
        } catch (c: kotlinx.coroutines.CancellationException) {
            // pause()/stop() render işini iptal eder — bu bir HATA değil.
            // Eskiden burada `failed = true` yapılsaydı her duraklatma
            // kanalı "hata" durumuna düşürürdü.
            return@launch
        } catch (t: Throwable) {
            Log.w(TAG, "Sentez döngüsü kesintiye uğradı", t)
            failed = true
        }

        if (failed) {
            // Kaynağı bırak, sonra motoru uyar. `stopInternal` içinde
            // `onRenderFailure` çağrılmaz → döngü yok.
            stopInternal(immediate = true)
            onRenderFailure?.invoke()
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
