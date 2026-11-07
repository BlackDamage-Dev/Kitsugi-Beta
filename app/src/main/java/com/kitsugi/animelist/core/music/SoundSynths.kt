package com.kitsugi.animelist.core.music

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * 🧪 Prosedürel Ses Sentezleyicileri
 *
 * Tümü gerçek zamanlı, durum tabanlı üreticilerdir. Hiçbir dosya,
 * ağ ya da lisans gerektirmez.
 *
 * Ortak kural: `render()` sıcak yolda çalışır — bellek ayırma,
 * log yazma, kilitlenme YAPILMAZ.
 */

private const val SR = 44_100
private const val TWO_PI_F = 6.2831855f
private const val TWO_PI_D = 6.283185307179586

// ═════════════════════════════════════════════════════════════════════════
//  1. GÜRÜLTÜ
// ═════════════════════════════════════════════════════════════════════════

/**
 * Beyaz / pembe / kahverengi gürültü üreticisi.
 *
 * - **Beyaz:** düz spektrum, maskeleme için en iyi
 * - **Pembe:** −3 dB/oktav, insan kulağına daha yumuşak (Voss-McCartney)
 * - **Kahverengi:** −6 dB/oktav, derin ve sıcak uğultu
 */
class NoiseSynth(private val color: Color) : SoundSynth {

    enum class Color { WHITE, PINK, BROWN }

    private val random = Random(System.nanoTime())

    // Voss-McCartney pembe gürültü durumu
    private val vossRows = FloatArray(16)
    private var vossCounter = 0
    private var pinkRunning = 0f

    // Kahverengi gürültü entegratör durumu
    private var brownState = 0f

    /** Yumuşak açılış için zarf. */
    private var envelope = 0f

    override fun reset() {
        vossRows.fill(0f)
        vossCounter = 0
        pinkRunning = 0f
        brownState = 0f
        envelope = 0f
    }

    override fun render(left: FloatArray, right: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            val white = random.nextFloat() * 2f - 1f
            val sample = when (color) {
                Color.WHITE -> white * 0.18f
                Color.PINK -> {
                    // Voss-McCartney: her satır farklı hızda güncellenir
                    vossCounter++
                    var sum = 0f
                    for (row in vossRows.indices) {
                        if (vossCounter and (1 shl row) == 0) {
                            vossRows[row] = random.nextFloat() * 2f - 1f
                        }
                        sum += vossRows[row]
                    }
                    pinkRunning = sum / vossRows.size
                    pinkRunning * 0.32f
                }
                Color.BROWN -> {
                    brownState = (brownState + white * 0.02f) * 0.998f
                    brownState.coerceIn(-1f, 1f) * 0.45f
                }
            }
            // 1.5 saniyelik yumuşak açılış
            if (envelope < 1f) envelope += 1f / (SR * 1.5f)
            val v = sample * envelope
            left[i] = v
            right[i] = v
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════
//  2. YAĞMUR
// ═════════════════════════════════════════════════════════════════════════

/**
 * Yağmur sentezleyicisi.
 *
 * Katmanlar:
 *  1. Bant geçiren gürültü tabanı (yağmurun "tıslaması")
 *  2. Rastgele damla transient'ları (yüksek frekans, kısa zarf)
 *  3. Opsiyonel gök gürültüsü (uzak, alçak frekanslı)
 */
class RainSynth(
    private val intensity: Float = 0.5f,
    private val thunderChance: Float = 0f
) : SoundSynth {

    private val random = Random(System.nanoTime())

    private val bedFilter = BandPassFilter()
    private val dropFilter = BandPassFilter()
    private val thunderLowPass = OnePoleFilter()

    // Damla transient durumu
    private var dropAmp = 0f
    private var dropDecay = 0.999f
    private var dropPitch = 0f

    // Gök gürültüsü durumu
    private var thunderAmp = 0f
    private var thunderDecay = 0.9999f

    private var envelope = 0f
    private var sampleCounter = 0L

    init {
        bedFilter.setParams(1800f, 0.6f, SR)
        dropFilter.setParams(5200f, 1.4f, SR)
    }

    override fun reset() {
        bedFilter.reset(); dropFilter.reset(); thunderLowPass.reset()
        dropAmp = 0f; thunderAmp = 0f; envelope = 0f; sampleCounter = 0
    }

    override fun render(left: FloatArray, right: FloatArray, frames: Int) {
        val bedGain = 0.10f + intensity * 0.16f
        val dropRate = 0.0008f + intensity * 0.0045f

        for (i in 0 until frames) {
            sampleCounter++

            // ── Katman 1: yağmur tabanı ──
            val white = random.nextFloat() * 2f - 1f
            var s = bedFilter.process(white) * bedGain

            // ── Katman 2: damlalar ──
            if (random.nextFloat() < dropRate) {
                dropAmp = 0.12f + random.nextFloat() * 0.22f
                dropDecay = 0.985f + random.nextFloat() * 0.012f
                // Damla frekansı inişi (pitch drop) için başlangıç değeri
                dropPitch = 0.0015f + random.nextFloat() * 0.0025f
            }
            if (dropAmp > 0.0001f) {
                val dropWhite = random.nextFloat() * 2f - 1f
                s += dropFilter.process(dropWhite) * dropAmp * 3f
                dropAmp *= dropDecay
            }

            // ── Katman 3: gök gürültüsü ──
            if (thunderChance > 0f && random.nextFloat() < thunderChance) {
                thunderAmp = 0.35f + random.nextFloat() * 0.4f
                thunderDecay = 0.99985f
            }
            if (thunderAmp > 0.0001f) {
                val rumble = thunderLowPass.lowPass(random.nextFloat() * 2f - 1f, 120f, SR)
                s += rumble * thunderAmp * 6f
                thunderAmp *= thunderDecay
            }

            if (envelope < 1f) envelope += 1f / (SR * 2f)
            val v = (s * envelope).coerceIn(-1f, 1f)
            // Hafif stereo genişlik — yağmur her yerde
            left[i] = v * 0.98f
            right[i] = v * 1.02f
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════
//  3. GÖK GÜRÜLTÜSÜ (tek başına)
// ═════════════════════════════════════════════════════════════════════════

/**
 * Aralıklı, uzaktan gelen gök gürültüsü. Uzun sessizlikler + derin
 * alçak frekans patlamaları.
 */
class ThunderSynth : SoundSynth {

    private val random = Random(System.nanoTime())
    private val lowPass = OnePoleFilter()
    private val lowPass2 = OnePoleFilter()

    private var amp = 0f
    private var decay = 0.9999f
    private var crackAmp = 0f
    private var envelope = 0f
    private var nextStrikeIn = SR * 4L
    private var counter = 0L

    override fun reset() {
        lowPass.reset(); lowPass2.reset()
        amp = 0f; crackAmp = 0f; envelope = 0f; counter = 0
        nextStrikeIn = SR * 4L
    }

    override fun render(left: FloatArray, right: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            counter++

            if (counter >= nextStrikeIn) {
                amp = 0.55f + random.nextFloat() * 0.4f
                decay = 0.99988f
                crackAmp = 0.3f + random.nextFloat() * 0.3f
                // Bir sonraki vuruş 8–28 saniye sonra
                nextStrikeIn = counter + (SR * (8 + random.nextFloat() * 20)).toLong()
            }

            var s = 0f
            if (amp > 0.0001f) {
                val noise = random.nextFloat() * 2f - 1f
                val low = lowPass2.lowPass(lowPass.lowPass(noise, 90f, SR), 70f, SR)
                s += low * amp * 9f
                amp *= decay
            }
            if (crackAmp > 0.0001f) {
                s += (random.nextFloat() * 2f - 1f) * crackAmp * 0.5f
                crackAmp *= 0.94f
            }

            if (envelope < 1f) envelope += 1f / (SR * 3f)
            val v = (s * envelope).coerceIn(-1f, 1f)
            left[i] = v
            right[i] = v * 0.92f
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════
//  4. ŞÖMİNE / ATEŞ
// ═════════════════════════════════════════════════════════════════════════

/**
 * Şömine: sürekli alçak uğultu + rastgele çıtırtı impulsları.
 *
 * Çıtırtılar Poisson dağılımına yakın rastgele zamanlamayla üretilir;
 * her biri kısa, keskin bir gürültü patlamasıdır.
 */
class FireSynth : SoundSynth {

    private val random = Random(System.nanoTime())
    private val rumbleFilter = OnePoleFilter()
    private val crackFilter = BandPassFilter()

    private var crackAmp = 0f
    private var crackDecay = 0.9f

    private var envelope = 0f

    init {
        crackFilter.setParams(2400f, 0.9f, SR)
    }

    override fun reset() {
        rumbleFilter.reset(); crackFilter.reset()
        crackAmp = 0f; envelope = 0f
    }

    override fun render(left: FloatArray, right: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            // Sürekli uğultu
            val noise = random.nextFloat() * 2f - 1f
            val rumble = rumbleFilter.lowPass(noise, 180f, SR)
            var s = rumble * 0.55f

            // Çıtırtı — saniyede ~35 olay
            if (random.nextFloat() < 0.0008f) {
                crackAmp = 0.10f + random.nextFloat() * 0.45f
                crackDecay = 0.86f + random.nextFloat() * 0.1f
            }
            if (crackAmp > 0.0005f) {
                val c = crackFilter.process(random.nextFloat() * 2f - 1f)
                s += c * crackAmp * 2.2f
                crackAmp *= crackDecay
            }

            if (envelope < 1f) envelope += 1f / (SR * 1.8f)
            val v = (s * envelope).coerceIn(-1f, 1f)
            left[i] = v
            right[i] = v * 0.96f
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════
//  5. RÜZGAR
// ═════════════════════════════════════════════════════════════════════════

/**
 * Rüzgar: bant geçiren gürültü, merkez frekansı yavaş bir LFO ile
 * gezinir. Bu "esinti" hissini verir.
 *
 * Filtre parametrelerini her blokta yeniden hesaplamak pahalı olduğu
 * için 256 örnekte bir güncellenir.
 */
class WindSynth : SoundSynth {

    private val random = Random(System.nanoTime())
    private val filter = BandPassFilter()
    private var lfoPhase = 0.0
    private var ampLfoPhase = 0.0
    private var framesSinceUpdate = 0
    private var envelope = 0f

    init {
        filter.setParams(600f, 0.7f, SR)
    }

    override fun reset() {
        filter.reset()
        lfoPhase = 0.0
        ampLfoPhase = 0.0
        framesSinceUpdate = 0
        envelope = 0f
    }

    override fun render(left: FloatArray, right: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            // Merkez frekans 220–1200 Hz arasında 0.06 Hz LFO ile gezinir
            if (framesSinceUpdate++ >= 256) {
                framesSinceUpdate = 0
                lfoPhase += 2.0 * PI * 0.06 * 256.0 / SR
                if (lfoPhase > 2.0 * PI) lfoPhase -= 2.0 * PI
                val center = 220f + (sin(lfoPhase).toFloat() * 0.5f + 0.5f) * 980f
                filter.setParams(center, 0.55f, SR)
            }

            val noise = random.nextFloat() * 2f - 1f
            var s = filter.process(noise) * 0.55f

            ampLfoPhase += 2.0 * PI * 0.11 / SR
            if (ampLfoPhase > 2.0 * PI) ampLfoPhase -= 2.0 * PI
            val ampMod = 0.55f + 0.45f * sin(ampLfoPhase).toFloat()
            s *= ampMod

            if (envelope < 1f) envelope += 1f / (SR * 2f)
            val v = (s * envelope).coerceIn(-1f, 1f)
            left[i] = v * 0.95f
            right[i] = v * 1.05f
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════
//  6. OKYANUS
// ═════════════════════════════════════════════════════════════════════════

/**
 * Okyanus dalgaları: kahverengi gürültü, çok yavaş (0.08 Hz) bir
 * sinüs zarfıyla modüle edilir. Her "dalga" ~12 saniye sürer.
 */
class OceanSynth : SoundSynth {

    private val random = Random(System.nanoTime())
    private val filter = OnePoleFilter()
    private var brown = 0f
    private var phase = 0.0
    private var phase2 = 0.0
    private var envelope = 0f

    override fun reset() {
        filter.reset(); brown = 0f; phase = 0.0; phase2 = 0.0; envelope = 0f
    }

    override fun render(left: FloatArray, right: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            val white = random.nextFloat() * 2f - 1f
            brown = (brown + white * 0.02f) * 0.997f
            brown = brown.coerceIn(-1f, 1f)

            // Ana dalga zarfı (0.08 Hz ≈ 12 sn periyot)
            phase += 2.0 * PI * 0.08 / SR
            if (phase > 2.0 * PI) phase -= 2.0 * PI
            // İkincil dalga (0.13 Hz) — düzensizlik için
            phase2 += 2.0 * PI * 0.13 / SR
            if (phase2 > 2.0 * PI) phase2 -= 2.0 * PI

            val wave = (sin(phase).toFloat() * 0.65f + 0.35f)
            val wave2 = (sin(phase2).toFloat() * 0.25f + 0.75f)
            var s = brown * wave * wave2 * 1.1f

            // Hafif su köpüğü
            s += filter.highPass(white, 3500f, SR) * 0.018f * wave

            if (envelope < 1f) envelope += 1f / (SR * 3f)
            val v = (s * envelope).coerceIn(-1f, 1f)
            left[i] = v
            right[i] = v * 0.97f
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════
//  7. DERE
// ═════════════════════════════════════════════════════════════════════════

/**
 * Dere şırıltısı: yüksek bant geçiren gürültü + rastgele küçük
 * "kabarcık" transient'ları.
 */
class StreamSynth : SoundSynth {

    private val random = Random(System.nanoTime())
    private val band = BandPassFilter()
    private val band2 = BandPassFilter()

    private var bubbleAmp = 0f
    private var bubblePhase = 0.0
    private var bubbleFreq = 900.0
    private var envelope = 0f

    init {
        band.setParams(1500f, 0.8f, SR)
        band2.setParams(3200f, 1.1f, SR)
    }

    override fun reset() {
        band.reset(); band2.reset()
        bubbleAmp = 0f; bubblePhase = 0.0; envelope = 0f
    }

    override fun render(left: FloatArray, right: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            val white = random.nextFloat() * 2f - 1f
            var s = band.process(white) * 0.22f + band2.process(white) * 0.10f

            // Kabarcıklar
            if (random.nextFloat() < 0.0009f) {
                bubbleAmp = 0.05f + random.nextFloat() * 0.10f
                bubbleFreq = 700.0 + random.nextFloat() * 1400.0
                bubblePhase = 0.0
            }
            if (bubbleAmp > 0.0002f) {
                bubblePhase += 2.0 * PI * bubbleFreq / SR
                val osc = sin(bubblePhase).toFloat()
                // Yükselen frekans hissi için zarf
                s += osc * bubbleAmp
                bubbleAmp *= 0.9965f
            }

            if (envelope < 1f) envelope += 1f / (SR * 2f)
            val v = (s * envelope).coerceIn(-1f, 1f)
            left[i] = v * 0.9f
            right[i] = v * 1.1f
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════
//  8. ORMAN KUŞLARI
// ═════════════════════════════════════════════════════════════════════════

/**
 * Kuş cıvıltısı: FM chirp sentezi.
 *
 * Her "ötüş" 3–7 nota içeren kısa bir chirp dizisidir. Frekans
 * 2–5 kHz arasında hızla değişir — kuş cıvıltısının karakteristiği.
 */
class BirdsSynth : SoundSynth {

    private val random = Random(System.nanoTime())

    // Aktif chirp dizisi durumu
    private var notesLeft = 0
    private var notePhase = 0.0
    private var noteFreqStart = 0.0
    private var noteFreqEnd = 0.0
    private var noteDuration = 0.0
    private var noteProgress = 0.0
    private var nextNoteDelay = 0

    private var envelope = 0f

    /** Arka plan orman uğultusu. */
    private val bgFilter = OnePoleFilter()

    override fun reset() {
        notesLeft = 0; notePhase = 0.0; noteProgress = 0.0
        nextNoteDelay = 0; envelope = 0f; bgFilter.reset()
    }

    override fun render(left: FloatArray, right: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            var s = 0f

            // Arka plan yaprak hışırtısı
            s += bgFilter.lowPass(random.nextFloat() * 2f - 1f, 900f, SR) * 0.035f

            if (notesLeft > 0) {
                if (noteProgress >= 1.0) {
                    // Nota bitti — küçük aralık sonra yenisine geç
                    notesLeft--
                    if (notesLeft > 0) {
                        nextNoteDelay = (SR * (0.04 + random.nextFloat() * 0.09)).toInt()
                        noteProgress = 0.0
                        notePhase = 0.0
                        noteFreqStart = 2000.0 + random.nextFloat() * 2200.0
                        noteFreqEnd = noteFreqStart * (0.65 + random.nextFloat() * 0.7)
                        noteDuration = 0.05 + random.nextFloat() * 0.10
                    }
                } else if (nextNoteDelay > 0) {
                    nextNoteDelay--
                } else {
                    val t = noteProgress
                    val freq = noteFreqStart + (noteFreqEnd - noteFreqStart) * t
                    notePhase += 2.0 * PI * freq / SR
                    // Zarf: hızlı yükseliş, yumuşak düşüş
                    val env = sin(PI * t).toFloat().let { it * it }
                    s += sin(notePhase).toFloat() * env * 0.16f
                    noteProgress += 1.0 / (SR * noteDuration)
                }
            } else if (random.nextFloat() < 0.00006f) {
                // Yeni ötüş başlat
                notesLeft = 3 + random.nextInt(5)
                noteProgress = 0.0
                notePhase = 0.0
                noteFreqStart = 2000.0 + random.nextFloat() * 2200.0
                noteFreqEnd = noteFreqStart * (0.65 + random.nextFloat() * 0.7)
                noteDuration = 0.05 + random.nextFloat() * 0.10
                nextNoteDelay = 0
            }

            if (envelope < 1f) envelope += 1f / (SR * 2.5f)
            val v = (s * envelope).coerceIn(-1f, 1f)
            left[i] = v * 1.08f
            right[i] = v * 0.92f
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════
//  9. CIRcir Böcekleri
// ═════════════════════════════════════════════════════════════════════════

/**
 * Cırcır böcekleri: 4–5 kHz civarında kesikli, hızlı ritmik ötüş.
 * Klasik yaz gecesi sesi.
 */
class CricketsSynth : SoundSynth {

    private val random = Random(System.nanoTime())
    private var phase = 0.0
    private var chirpGate = 0.0
    private var gateCounter = 0
    private var envelope = 0f
    private val baseFreq = 4200.0 + random.nextDouble() * 800.0

    override fun reset() {
        phase = 0.0; chirpGate = 0.0; gateCounter = 0; envelope = 0f
    }

    override fun render(left: FloatArray, right: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            // 24 Hz'de aç/kapa kapısı — cırcır karakteri
            if (gateCounter++ >= SR / 24) {
                gateCounter = 0
                chirpGate = 1.0 - chirpGate
            }
            phase += 2.0 * PI * baseFreq / SR
            if (phase > 2.0 * PI) phase -= 2.0 * PI

            val osc = sin(phase).toFloat()
            // Kapı açıkken ve her 3. kapıda çal (daha doğal)
            val gate = if (chirpGate > 0.5) 1f else 0.15f
            var s = osc * gate * 0.055f

            // Hafif gece rüzgarı
            s += (random.nextFloat() * 2f - 1f) * 0.008f

            if (envelope < 1f) envelope += 1f / (SR * 2f)
            val v = (s * envelope).coerceIn(-1f, 1f)
            left[i] = v * 0.85f
            right[i] = v * 1.15f
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════
//  10. KAFE
// ═════════════════════════════════════════════════════════════════════════

/**
 * Kafe uğultusu: formant filtreli gürültü (konuşma benzeri) +
 * ara sıra bardak/kaşık sesleri.
 *
 * Konuşmayı anlaşılır kılmadan "kalabalık" hissi vermek için
 * formant frekanslarında gezinen bant geçiren filtreler kullanılır.
 */
class CafeSynth : SoundSynth {

    private val random = Random(System.nanoTime())
    private val f1 = BandPassFilter()
    private val f2 = BandPassFilter()
    private val f3 = BandPassFilter()
    private val clinkFilter = BandPassFilter()

    private var clinkAmp = 0f
    private var clinkPhase = 0.0
    private var lfo = 0.0
    private var envelope = 0f
    private var updateCounter = 0

    init {
        f1.setParams(500f, 1.2f, SR)
        f2.setParams(1500f, 1.4f, SR)
        f3.setParams(2500f, 1.6f, SR)
        clinkFilter.setParams(4800f, 2.5f, SR)
    }

    override fun reset() {
        f1.reset(); f2.reset(); f3.reset(); clinkFilter.reset()
        clinkAmp = 0f; clinkPhase = 0.0; lfo = 0.0; envelope = 0f; updateCounter = 0
    }

    override fun render(left: FloatArray, right: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            // Formant gezinmesi — "konuşma" hissi
            if (updateCounter++ >= 512) {
                updateCounter = 0
                lfo += 0.05
                val drift = sin(lfo).toFloat() * 120f
                f1.setParams(500f + drift, 1.2f, SR)
                f2.setParams(1500f + drift * 1.8f, 1.4f, SR)
                f3.setParams(2500f + drift * 2.4f, 1.6f, SR)
            }

            val white = random.nextFloat() * 2f - 1f
            var s = f1.process(white) * 0.30f +
                f2.process(white) * 0.18f +
                f3.process(white) * 0.09f

            // Bardak/kaşık
            if (random.nextFloat() < 0.00012f) {
                clinkAmp = 0.05f + random.nextFloat() * 0.12f
                clinkPhase = 0.0
            }
            if (clinkAmp > 0.0002f) {
                clinkPhase += 2.0 * PI * 5200.0 / SR
                s += sin(clinkPhase).toFloat() * clinkAmp
                clinkAmp *= 0.990f
            }

            if (envelope < 1f) envelope += 1f / (SR * 2f)
            val v = (s * envelope).coerceIn(-1f, 1f)
            left[i] = v
            right[i] = v * 0.94f
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════
//  11. GECE ŞEHRİ
// ═════════════════════════════════════════════════════════════════════════

/**
 * Gece şehri: uzak trafik uğultusu (alçak frekans) + ara sıra
 * uzaktan geçen araç ("whoosh").
 */
class CitySynth : SoundSynth {

    private val random = Random(System.nanoTime())
    private val low = OnePoleFilter()
    private val low2 = OnePoleFilter()
    private val passFilter = BandPassFilter()

    private var passAmp = 0f
    private var passProgress = 0.0
    private var passDir = 1.0
    private var envelope = 0f

    override fun reset() {
        low.reset(); low2.reset(); passFilter.reset()
        passAmp = 0f; passProgress = 0.0; envelope = 0f
    }

    override fun render(left: FloatArray, right: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            val white = random.nextFloat() * 2f - 1f

            // Sürekli trafik uğultusu
            var s = low2.lowPass(low.lowPass(white, 220f, SR), 140f, SR) * 3.2f

            // Araç geçişi
            if (passAmp <= 0.0001f && random.nextFloat() < 0.000008f) {
                passAmp = 1f
                passProgress = 0.0
                passDir = 1.0
            }
            if (passAmp > 0.0001f) {
                passProgress += 1.0 / (SR * 5.0)
                if (passProgress >= 1.0) {
                    passAmp = 0f
                } else {
                    // Zarf: tepe ortada (geçiş)
                    val env = sin(PI * passProgress).toFloat()
                    val center = 300f + env * 1400f
                    passFilter.setParams(center, 1.8f, SR)
                    s += passFilter.process(white) * env * 0.10f
                }
            }

            if (envelope < 1f) envelope += 1f / (SR * 3f)
            val v = (s * envelope).coerceIn(-1f, 1f)
            left[i] = v * 0.88f
            right[i] = v * 1.12f
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════
//  12. TREN
// ═════════════════════════════════════════════════════════════════════════

/**
 * Tren: rayların ritmik "tık-tık" sesi + sürekli alçak motor uğultusu.
 * Tık aralığı ~1.4 saniye (tek ray eklemi).
 */
class TrainSynth : SoundSynth {

    private val random = Random(System.nanoTime())
    private val rumbleFilter = OnePoleFilter()
    private val clickFilter = BandPassFilter()

    private var clickAmp = 0f
    private var clickCountdown = (SR * 1.4).toInt()
    private var envelope = 0f

    init {
        clickFilter.setParams(1200f, 0.8f, SR)
    }

    override fun reset() {
        rumbleFilter.reset(); clickFilter.reset()
        clickAmp = 0f; clickCountdown = (SR * 1.4).toInt(); envelope = 0f
    }

    override fun render(left: FloatArray, right: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            val white = random.nextFloat() * 2f - 1f
            var s = rumbleFilter.lowPass(white, 260f, SR) * 0.85f

            // Tık
            if (--clickCountdown <= 0) {
                clickCountdown = (SR * (1.35 + random.nextFloat() * 0.1)).toInt()
                clickAmp = 0.30f + random.nextFloat() * 0.15f
            }
            if (clickAmp > 0.0005f) {
                s += clickFilter.process(white) * clickAmp * 2.5f
                clickAmp *= 0.9925f
            }

            if (envelope < 1f) envelope += 1f / (SR * 2f)
            val v = (s * envelope).coerceIn(-1f, 1f)
            left[i] = v * 0.93f
            right[i] = v * 1.07f
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════
//  13. ÜRETKEN MELODİ (Piyano / Müzik Kutusu / Koto)
// ═════════════════════════════════════════════════════════════════════════

/**
 * Üretken melodi: pentatonik dizi + Markov benzeri geçişler.
 *
 * Her nota FM senteziyle üretilir; timbre'ye göre harmonik içeriği
 * ve zarf şekli değişir. Notalar bir [DelayLine] üzerinden hafif
 * reverb alır.
 */
class GenerativeMelodySynth(private val timbre: Timbre) : SoundSynth {

    enum class Timbre {
        PIANO,      // yumuşak, uzun decay
        MUSIC_BOX,  // metalik, kısa, parlak
        KOTO        // telli, hafif detune
    }

    private val random = Random(System.nanoTime())
    private val reverb = DelayLine(SR * 2)

    // Pentatonik dizi (A minör pentatonik) — yatay ve huzurlu
    private val scale = floatArrayOf(
        220.00f, 246.94f, 261.63f, 293.66f, 329.63f,
        440.00f, 493.88f, 523.25f, 587.33f, 659.25f,
        880.00f, 987.77f
    )

    // Aktif notalar (polifoni için 6 ses)
    private val voiceFreq = FloatArray(6)
    private val voicePhase = FloatArray(6)
    private val voiceAmp = FloatArray(6)
    private val voiceDecay = FloatArray(6)

    private var framesToNextNote = SR / 2

    private var envelope = 0f

    init {
        reverb.configure(
            delaySamples = (SR * 0.28f).toInt(),
            feedback = 0.42f,
            mix = 0.32f
        )
    }

    override fun reset() {
        reverb.reset()
        voiceFreq.fill(0f); voicePhase.fill(0f); voiceAmp.fill(0f); voiceDecay.fill(0.999f)
        framesToNextNote = SR / 2
        envelope = 0f
    }

    override fun render(left: FloatArray, right: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            if (--framesToNextNote <= 0) {
                triggerNote()
                framesToNextNote = when (timbre) {
                    Timbre.PIANO -> (SR * (0.45f + random.nextFloat() * 1.1f)).toInt()
                    Timbre.MUSIC_BOX -> (SR * (0.30f + random.nextFloat() * 0.75f)).toInt()
                    Timbre.KOTO -> (SR * (0.55f + random.nextFloat() * 1.4f)).toInt()
                }
            }

            var s = 0f
            for (v in 0 until 6) {
                if (voiceAmp[v] <= 0.00005f) continue
                voicePhase[v] += (2.0 * PI * voiceFreq[v] / SR).toFloat()
                if (voicePhase[v] > TWO_PI_F) voicePhase[v] -= TWO_PI_F

                val fundamental = sin(voicePhase[v])
                val harmonic2 = sin(voicePhase[v] * 2f)
                val harmonic3 = sin(voicePhase[v] * 3f)

                val voice = when (timbre) {
                    Timbre.PIANO ->
                        fundamental * 0.80f + harmonic2 * 0.14f + harmonic3 * 0.05f
                    Timbre.MUSIC_BOX ->
                        fundamental * 0.45f + harmonic2 * 0.33f + harmonic3 * 0.22f
                    Timbre.KOTO ->
                        fundamental * 0.75f + harmonic2 * 0.20f + harmonic3 * 0.08f
                }
                s += voice * voiceAmp[v]
                voiceAmp[v] *= voiceDecay[v]
            }

            s = reverb.process(s)

            if (envelope < 1f) envelope += 1f / (SR * 3f)
            val v = (s * envelope).coerceIn(-1f, 1f)
            left[i] = v * 0.92f
            right[i] = reverb.process(s * 0.35f).coerceIn(-1f, 1f) * 1.08f * envelope
        }
    }

    private fun triggerNote() {
        // Boş ses bul
        var slot = -1
        for (v in 0 until 6) {
            if (voiceAmp[v] <= 0.00005f) { slot = v; break }
        }
        if (slot < 0) {
            // En zayıf sesi yeniden kullan
            var minAmp = Float.MAX_VALUE
            for (v in 0 until 6) {
                if (voiceAmp[v] < minAmp) { minAmp = voiceAmp[v]; slot = v }
            }
        }
        if (slot < 0) return

        val freq = scale[random.nextInt(scale.size)]
        voiceFreq[slot] = freq
        voicePhase[slot] = 0f
        voiceAmp[slot] = (0.06f + random.nextFloat() * 0.07f)
        voiceDecay[slot] = when (timbre) {
            Timbre.PIANO -> 0.99996f
            Timbre.MUSIC_BOX -> 0.9993f
            Timbre.KOTO -> 0.9997f
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════
//  14. SYNTH PAD
// ═════════════════════════════════════════════════════════════════════════

/**
 * Synth pad: 3 detune'lu osilatörden oluşan yumuşak, sürekli akor.
 * Yavaş LFO ile hafif filtre süpürmesi.
 */
class PadSynth : SoundSynth {

    private val phases = DoubleArray(4)
    // A minör 7 akoru: A2, C3, E3, G3
    private val freqs = doubleArrayOf(110.0, 130.81, 164.81, 196.0)

    private var lfo = 0.0
    private val filter = OnePoleFilter()
    private var envelope = 0f

    override fun reset() {
        phases.fill(0.0); lfo = 0.0; filter.reset(); envelope = 0f
    }

    override fun render(left: FloatArray, right: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            lfo += 2.0 * PI * 0.05 / SR
            if (lfo > 2.0 * PI) lfo -= 2.0 * PI

            var s = 0f
            for (v in freqs.indices) {
                phases[v] += 2.0 * PI * freqs[v] / SR
                if (phases[v] > 2.0 * PI) phases[v] -= 2.0 * PI
                s += sin(phases[v]).toFloat() * 0.18f
            }

            // Yavaş filtre süpürmesi 300–1600 Hz
            val cutoff = 300f + (sin(lfo).toFloat() * 0.5f + 0.5f) * 1300f
            s = filter.lowPass(s, cutoff, SR)

            if (envelope < 1f) envelope += 1f / (SR * 5f)
            val v = (s * envelope * 0.9f).coerceIn(-1f, 1f)
            left[i] = v * 1.05f
            right[i] = v * 0.95f
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════
//  15. KARANLIK DRONE
// ═════════════════════════════════════════════════════════════════════════

/**
 * Karanlık drone: çok alçak frekanslar + hafif disonans.
 * Gerilim ve tehdit hissi yaratır.
 */
class DroneSynth : SoundSynth {

    private val phases = DoubleArray(3)
    // Hafif disonans: D1, A1 ve Eb2 (triton)
    private val freqs = doubleArrayOf(36.71, 55.00, 77.78)

    private val random = Random(System.nanoTime())
    private val rumble = OnePoleFilter()
    private var envelope = 0f
    private var breathLfo = 0.0

    override fun reset() {
        phases.fill(0.0); rumble.reset(); envelope = 0f; breathLfo = 0.0
    }

    override fun render(left: FloatArray, right: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            breathLfo += 2.0 * PI * 0.07 / SR
            if (breathLfo > 2.0 * PI) breathLfo -= 2.0 * PI

            var s = 0f
            for (v in freqs.indices) {
                phases[v] += 2.0 * PI * freqs[v] / SR
                if (phases[v] > 2.0 * PI) phases[v] -= 2.0 * PI
                s += sin(phases[v]).toFloat() * 0.28f
            }

            // Nefes benzeri gürültü
            val noise = rumble.lowPass(random.nextFloat() * 2f - 1f, 90f, SR)
            s += noise * 2.5f

            val breath = 0.65f + 0.35f * sin(breathLfo).toFloat()
            s *= breath

            if (envelope < 1f) envelope += 1f / (SR * 5f)
            val v = (s * envelope).coerceIn(-1f, 1f)
            left[i] = v
            right[i] = v * 0.88f
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════
//  16. KALP ATIŞI
// ═════════════════════════════════════════════════════════════════════════

/**
 * Kalp atışı: 60 BPM'de "lub-dub" çift vuruş.
 * Her vuruş alçak frekanslı bir sinüs zarfıdır.
 */
class HeartbeatSynth : SoundSynth {

    private val beatPeriod = SR * 60 / 60  // 60 BPM → 1 saniye
    private var counter = 0
    private var thumpAmp = 0f
    private var thumpPhase = 0.0
    private var secondBeatCountdown = -1
    private var envelope = 0f

    override fun reset() {
        counter = 0; thumpAmp = 0f; thumpPhase = 0.0
        secondBeatCountdown = -1; envelope = 0f
    }

    override fun render(left: FloatArray, right: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            counter++
            if (counter >= beatPeriod) {
                counter = 0
                thumpAmp = 0.55f
                thumpPhase = 0.0
                secondBeatCountdown = (SR * 0.26f).toInt()
            }
            if (secondBeatCountdown > 0 && --secondBeatCountdown == 0) {
                thumpAmp = 0.38f
                thumpPhase = 0.0
            }

            var s = 0f
            if (thumpAmp > 0.0005f) {
                thumpPhase += 2.0 * PI * 55.0 / SR
                s = sin(thumpPhase).toFloat() * thumpAmp
                thumpAmp *= 0.9992f
            }

            if (envelope < 1f) envelope += 1f / (SR * 2f)
            val v = (s * envelope).coerceIn(-1f, 1f)
            left[i] = v
            right[i] = v
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════
//  17. SAAT
// ═════════════════════════════════════════════════════════════════════════

/**
 * Saat tıkırtısı: her saniye iki kısa tık (tik-tak).
 */
class ClockSynth : SoundSynth {

    private val tickFilter = BandPassFilter()
    private var clickAmp = 0f
    private var counter = 0
    private var altTick = false
    private var envelope = 0f

    init {
        tickFilter.setParams(2600f, 2.0f, SR)
    }

    override fun reset() {
        tickFilter.reset(); clickAmp = 0f; counter = 0; envelope = 0f
    }

    override fun render(left: FloatArray, right: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            counter++
            if (counter >= SR) {
                counter = 0
                clickAmp = if (altTick) 0.22f else 0.30f
                altTick = !altTick
            }

            var s = 0f
            if (clickAmp > 0.0005f) {
                s = tickFilter.process(kotlin.random.Random.nextFloat() * 2f - 1f) * clickAmp
                clickAmp *= 0.90f
            }

            if (envelope < 1f) envelope += 1f / (SR * 1.5f)
            val v = (s * envelope).coerceIn(-1f, 1f)
            left[i] = v
            right[i] = v * 0.85f
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════
//  18. ŞARKI SÖYLEYEN KÂSE
// ═════════════════════════════════════════════════════════════════════════

/**
 * Tibet çanı: uzun, rezonanslı, hafif vuruşlu (beat frequency) ton.
 * İki çok yakın frekans üst üste bindirilerek "şarkı söyleme" etkisi.
 */
class SingingBowlSynth : SoundSynth {

    private var phase1 = 0.0
    private var phase2 = 0.0
    private var amplitude = 0.5f
    private var envelope = 0f
    private val freq1 = 196.0
    private val freq2 = 196.6  // 0.6 Hz vuruş

    override fun reset() {
        phase1 = 0.0; phase2 = 0.0; amplitude = 0.5f; envelope = 0f
    }

    override fun render(left: FloatArray, right: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            phase1 += 2.0 * PI * freq1 / SR
            phase2 += 2.0 * PI * freq2 / SR
            if (phase1 > 2.0 * PI) phase1 -= 2.0 * PI
            if (phase2 > 2.0 * PI) phase2 -= 2.0 * PI

            val tone = (sin(phase1) + sin(phase2)).toFloat() * 0.5f
            // Harmonikler
            val h2 = sin((phase1 * 2.0)).toFloat() * 0.18f
            val h3 = sin((phase1 * 3.0)).toFloat() * 0.06f

            var s = (tone * 0.6f + h2 + h3) * amplitude

            if (envelope < 1f) envelope += 1f / (SR * 8f)
            val v = (s * envelope).coerceIn(-1f, 1f)
            left[i] = v
            right[i] = v * 0.98f
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════
//  19. RÜZGAR ÇANI
// ═════════════════════════════════════════════════════════════════════════

/**
 * Rüzgar çanı: rastgele zamanlarda yumuşak, metalik çınlamalar.
 * Her çınlama iki-üç harmonikten oluşur ve uzun decay'e sahiptir.
 */
class WindChimeSynth : SoundSynth {

    private val random = Random(System.nanoTime())
    private val chimePhases = DoubleArray(4)
    private val chimeAmps = FloatArray(4)
    private val chimeFreqs = FloatArray(4)
    private var framesToNext = (SR * 2.5f).toInt()
    private var breezeFilter = OnePoleFilter()
    private var envelope = 0f

    override fun reset() {
        chimePhases.fill(0.0); chimeAmps.fill(0f); chimeFreqs.fill(0f)
        framesToNext = (SR * 2.5f).toInt(); breezeFilter.reset(); envelope = 0f
    }

    override fun render(left: FloatArray, right: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            if (--framesToNext <= 0) {
                triggerChime()
                framesToNext = (SR * (1.5f + random.nextFloat() * 4.5f)).toInt()
            }

            var s = breezeFilter.lowPass(random.nextFloat() * 2f - 1f, 400f, SR) * 0.04f

            for (v in 0 until 4) {
                if (chimeAmps[v] <= 0.0001f) continue
                chimePhases[v] += 2.0 * PI * chimeFreqs[v] / SR
                if (chimePhases[v] > 2.0 * PI) chimePhases[v] -= 2.0 * PI
                s += sin(chimePhases[v]).toFloat() * chimeAmps[v] * 0.5f
                chimeAmps[v] *= 0.99965f
            }

            if (envelope < 1f) envelope += 1f / (SR * 3f)
            val v = (s * envelope).coerceIn(-1f, 1f)
            left[i] = v * 1.06f
            right[i] = v * 0.94f
        }
    }

    private fun triggerChime() {
        // C majör pentatonik: C5, D5, E5, G5, A5
        val notes = floatArrayOf(523.25f, 587.33f, 659.25f, 783.99f, 880.0f)
        val baseFreq = notes[random.nextInt(notes.size)]

        for (v in 0 until 4) {
            if (chimeAmps[v] <= 0.0001f) {
                chimeFreqs[v] = baseFreq * (1f + v * 0.25f)
                chimePhases[v] = 0.0
                chimeAmps[v] = 0.12f / (v + 1)
                return
            }
        }
        // Hepsi doluysa en zayıfı sıfırla
        chimeAmps[0] = 0.12f
        chimeFreqs[0] = baseFreq
        chimePhases[0] = 0.0
    }
}

/**
 * 🧠 Binaural Beats — iki kulağa farklı frekans
 *
 * Metiq'te bulunan özellik. Sol kulağa [baseHz], sağ kulağa
 * `baseHz + beatHz` verilir. Beyin iki tonun **farkını** duyar
 * (örn. 200 Hz + 210 Hz → 10 Hz "alfa" vuruşu).
 *
 * ⚠️ **Kulaklık gerekir.** Hoparlörde iki kanal havada karışır ve
 * vuruş kaybolur. Panel bunu kullanıcıya söyler.
 *
 * Frekans bantları (yaygın kabul):
 *  - Delta 1–4 Hz: derin uyku
 *  - Theta 4–8 Hz: uykuya geçiş, yaratıcılık
 *  - Alpha 8–13 Hz: gevşeme, sakin uyanıklık
 *  - Beta 13–30 Hz: odaklanma, aktif düşünme
 *  - Gamma 30–100 Hz: yoğun bilişsel yük
 *
 * Ses seviyesi bilinçli olarak düşük tutulur (0.12) — bu bir arka plan
 * aracıdır, müzik değil.
 */
class BinauralSynth(
    private val baseHz: Float = 200f,
    private val beatHz: Float = 10f,
    private val sampleRate: Int = 44_100
) : SoundSynth {

    private var phaseL = 0.0
    private var phaseR = 0.0
    private val stepL = TWO_PI_F * baseHz / sampleRate
    private val stepR = TWO_PI_F * (baseHz + beatHz) / sampleRate

    /** Yumuşak başlangıç — aniden girmesin ama duyulabilir seviyeye hızlı ulaşsın. */
    private var gain = 0f
    private val gainStep = 0.35f / (sampleRate * 1.0f) // 1 saniyede tam seviye (0.35)

    companion object {
        /** Bant adı → vuruş frekansı. */
        val BANDS: List<Pair<String, Float>> = listOf(
            "Delta · derin uyku" to 2.5f,
            "Theta · uykuya geçiş" to 6f,
            "Alfa · gevşeme" to 10f,
            "Beta · odak" to 18f,
            "Gama · yoğun dikkat" to 40f
        )
    }

    override fun reset() {
        phaseL = 0.0
        phaseR = 0.0
        gain = 0f
    }

    override fun render(left: FloatArray, right: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            if (gain < 0.35f) gain = (gain + gainStep).coerceAtMost(0.35f)
            left[i] = (kotlin.math.sin(phaseL).toFloat() * gain)
            right[i] = (kotlin.math.sin(phaseR).toFloat() * gain)
            phaseL += stepL
            phaseR += stepR
            if (phaseL > TWO_PI_D) phaseL -= TWO_PI_D
            if (phaseR > TWO_PI_D) phaseR -= TWO_PI_D
        }
    }

}
