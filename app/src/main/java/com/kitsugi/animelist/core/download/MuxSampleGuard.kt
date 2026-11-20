package com.kitsugi.animelist.core.download

/**
 * **MuxSampleGuard** — `MediaMuxer.writeSampleData` çağrıları için son savunma hattı (v2.4.492).
 *
 * ## NEDEN VAR (2026-11-20 çökmesi)
 * 2026-11-20 15:18:10'da uygulama `[UNCLEAN EXIT]` verdi. `crash_history` günlüğünde:
 *
 * ```
 * F libc  : FORTIFY: write: count 18446744073709551615 > SSIZE_MAX
 * KITSUGI NATIVE CRASH ... signal=SIGABRT (6) thread=MPEG4Writer tid=30102
 * ```
 *
 * `count = 18446744073709551615` = `size_t(-1)`. Yani kapsayıcı yazıcısına **-1 bayt**
 * uzunluğunda bir yazma isteği gitti. `MPEG4Writer` bunu `::write()`'a `size_t` olarak
 * geçirdiğinde libc FORTIFY `__write_chk` anında `__fortify_fail()` çağırır ve süreç
 * SIGABRT alır — Java tarafında yakalanabilecek bir istisna YOKTUR, çünkü hata native
 * yazıcı iş parçacığında (`MPEG4Writer`) oluşur.
 *
 * Bu hatalı değer zinciri:
 *  1. `MediaExtractor.readSampleData()` / Media3 `TrackOutput.sampleData()` bozuk bir
 *     MPEG-TS/fMP4 akışında `-1` (ya da buffer'dan büyük) döndürür,
 *  2. bazı çağrı yerleri bu değeri doğrulamadan `MediaCodec.BufferInfo.size`'a yazar,
 *  3. `MPEG4Writer` örnek boyutundan dosya ofsetini hesaplarken negatif değer üretir,
 *  4. libc FORTIFY → SIGABRT.
 *
 * Ek olarak `presentationTimeUs` negatif ya da absürt büyük olduğunda `MPEG4Writer`'ın
 * chunk süre hesabı `int` taşmasına girer ve yine negatif uzunluk üretilir; ses izlerine
 * `BUFFER_FLAG_KEY_FRAME` verilmesi de moov yazımında tutarsız anahtar-kare tabloları
 * oluşturur.
 *
 * Bu nesne SAF JVM Kotlin'dir (Android bağımlılığı yok) → `app/src/test` altında test edilir.
 */
object MuxSampleGuard {

    /** Tek bir örnek için kabul edilen azami boyut (32 MB — 4K IDR için bolca yeterli). */
    const val MAX_SAMPLE_BYTES = 32 * 1024 * 1024

    /**
     * Kabul edilen azami PTS: 24 saat. Gerçek bir bölüm 24 saatten kısa olduğundan bunun
     * üzerindeki değerler bozuk zaman damgasıdır (ör. 32-bit taşma, ID3 çöp verisi,
     * `0x7FFFFFFF00000000` gibi). Bu değerler kırpılmazsa `MPEG4Writer` chunk süresi
     * negatife döner.
     */
    const val MAX_PTS_US = 24L * 60L * 60L * 1_000_000L

    /** Video için varsayılan kare adımı (~30 fps). */
    const val DEFAULT_VIDEO_STEP_US = 33_333L

    /** Ses için varsayılan kare adımı (48 kHz'de 1024 örnek ≈ 21.3 ms). */
    const val DEFAULT_AUDIO_STEP_US = 21_333L

    /**
     * Örnek boyutu `writeSampleData`'ya verilebilir mi?
     *
     * `MediaExtractor.readSampleData` akış bittiğinde `-1`, Media3 `sampleData()` ise okuma
     * yapamadığında `0` döndürür. İkisi de yazılmamalıdır: `-1` yazıldığında native yazıcı
     * `size_t(-1)` üretir ve süreç SIGABRT ile ölür (bkz. sınıf açıklaması).
     */
    fun isWritableSampleSize(size: Int): Boolean = size in 1..MAX_SAMPLE_BYTES

    /**
     * Bozuk bir zaman damgasını güvenli aralığa çeker.
     * Negatif PTS `0`'a, 24 saatten büyük PTS üst sınıra sabitlenir.
     */
    fun clampPtsUs(rawPtsUs: Long): Long = when {
        rawPtsUs <= 0L -> 0L
        rawPtsUs > MAX_PTS_US -> MAX_PTS_US
        else -> rawPtsUs
    }

    /**
     * Aynı iz üzerinde **kesinlikle artan** PTS üretir.
     *
     * `MPEG4Writer` aynı izde eşit/azalan zaman damgası gördüğünde örnekleri birleştirmeye
     * çalışır; bu sırada `sampleDuration` negatife düşebilir. Bu yüzden:
     *  - PTS kırpılır ([clampPtsUs]),
     *  - son yazılan PTS'ten büyük değilse son PTS + [stepUs] kullanılır,
     *  - taşmayı önlemek için üst sınırda kırpılır (pratikte 24 saatlik bir bölüm yok).
     *
     * @param lastWrittenUs bu izde en son yazılan PTS; henüz hiç yazılmadıysa negatif değer.
     */
    fun nextMonotonicPtsUs(rawPtsUs: Long, lastWrittenUs: Long, stepUs: Long): Long {
        val clamped = clampPtsUs(rawPtsUs)
        if (lastWrittenUs < 0L) return clamped
        if (clamped > lastWrittenUs) return clamped
        val step = if (stepUs > 0L) stepUs else 1L
        return (lastWrittenUs + step).coerceAtMost(MAX_PTS_US)
    }

    /**
     * İlk örneği 0'a normalize eden ofset.
     *
     * Ayrı indirilen ses akışları çoğu zaman `#EXT-X-MAP`/ID3 kaynaklı büyük bir başlangıç
     * PTS'i ile gelir. Bu ofset çıkarılmazsa muxer'ın `setStartTimestampUs` hesabı ile
     * örnek PTS'leri çelişir.
     */
    fun baseShiftFor(firstPtsUs: Long): Long = -clampPtsUs(firstPtsUs)

    /**
     * Ses izleri için `BufferInfo.flags`: **her zaman 0**.
     *
     * AAC/Opus/AC-3 örnekleri "anahtar kare" kavramına sahip değildir. MPEG4Writer'a
     * `BUFFER_FLAG_KEY_FRAME` verildiğinde ses izi için de senkron örnek tablosu yazılır;
     * Android 13'te bu tablo ile `stss` atomu çeliştiğinde moov yazımı sırasında okuma
     * taşması görülebiliyor.
     */
    fun audioSampleFlags(): Int = 0

    /**
     * Kaynak bayraklarını yalnızca gerçekten senkron bir örnekse anahtar kareye çevirir.
     *
     * @param sourceFlags kaynak extractor'ın bayrakları
     * @param keyFrameBit kaynaktaki anahtar kare biti (`MediaExtractor.SAMPLE_FLAG_SYNC`
     *   ya da `C.BUFFER_FLAG_KEY_FRAME`)
     * @param codecKeyFrame `MediaCodec.BUFFER_FLAG_KEY_FRAME`
     */
    fun videoSampleFlags(sourceFlags: Int, keyFrameBit: Int, codecKeyFrame: Int): Int =
        if ((sourceFlags and keyFrameBit) != 0) codecKeyFrame else 0

    /**
     * Yazılacak örnek için nihai karar.
     *
     * @return `null` → örnek atlanmalı; aksi halde yazılacak `(size, ptsUs)` çifti.
     */
    fun writableSample(
        size: Int,
        rawPtsUs: Long,
        lastWrittenUs: Long,
        stepUs: Long
    ): Pair<Int, Long>? {
        if (!isWritableSampleSize(size)) return null
        return size to nextMonotonicPtsUs(rawPtsUs, lastWrittenUs, stepUs)
    }
}
