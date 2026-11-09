package com.kitsugi.animelist.core.download

import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Cihazın **gerçekten** çalabildiği video/ses formatlarını tespit eder.
 *
 * ## Neden gerekli?
 *
 * İndirici, playlist'teki en yüksek bitrate'li varyantı indiriyordu. Ama
 * HDFilmCehennemi/FilmMakinesi gibi kaynaklar aynı bölümü bazen **AV1** veya
 * **HEVC (H.265)** olarak sunuyor; ses tarafında **AC-3 / E-AC-3 / DTS** de
 * gelebiliyor. Bunların hiçbiri her telefonda yok:
 *
 *  - AV1 → yalnızca yeni amiral gemisi cihazlarda donanım decoder'ı var
 *  - AC-3/E-AC-3/DTS → lisans gerektirdiği için çoğu telefonda **yok**
 *    (sonuç: "görüntü var, ses yok")
 *
 * Böyle bir dosya indirilirse disk dolar, kullanıcı açtığında oynamaz →
 * "inmiş ama bir anlamı yok". Bu sınıf, indirmeden **önce** cihaza sorup
 * uygun varyantı seçmemizi sağlar.
 *
 * ## Neden MediaCodecList?
 *
 * `MediaCodecList(REGULAR_CODECS)` cihazın sistemde kayıtlı decoder'larını
 * döndürür. Bu, "cihaz bu formatı oynatabilir mi" sorusunun en güvenilir
 * cevabıdır (marka/model tahmini yapmaktan çok daha doğru).
 *
 * Sonuçlar [ConcurrentHashMap] ile önbelleklenir: bölüm başına tekrar tarama
 * yapılmaz (bazı cihazlarda bu tarama 20-40 ms sürebiliyor).
 */
object DeviceCodecSupport {

    private const val TAG = "DeviceCodecSupport"

    /** HLS `CODECS` niteliğindeki adlandırma → MediaFormat MIME eşlemesi. */
    private val VIDEO_MIME_BY_CODEC: List<Pair<String, String>> = listOf(
        "avc1" to MediaFormat.MIMETYPE_VIDEO_AVC,
        "avc3" to MediaFormat.MIMETYPE_VIDEO_AVC,
        "hvc1" to MediaFormat.MIMETYPE_VIDEO_HEVC,
        "hev1" to MediaFormat.MIMETYPE_VIDEO_HEVC,
        "dvh1" to MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION,
        "dvhe" to MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION,
        "av01" to MediaFormat.MIMETYPE_VIDEO_AV1,
        "vp09" to MediaFormat.MIMETYPE_VIDEO_VP9,
        "vp8" to "video/x-vnd.on2.vp8",
        "mp4v" to MediaFormat.MIMETYPE_VIDEO_MPEG4
    )

    private val AUDIO_MIME_BY_CODEC: List<Pair<String, String>> = listOf(
        "mp4a" to MediaFormat.MIMETYPE_AUDIO_AAC,
        "ac-3" to MediaFormat.MIMETYPE_AUDIO_AC3,
        "ec-3" to MediaFormat.MIMETYPE_AUDIO_EAC3,
        "ac-4" to "audio/ac4",
        "opus" to MediaFormat.MIMETYPE_AUDIO_OPUS,
        "vorbis" to MediaFormat.MIMETYPE_AUDIO_VORBIS,
        "mp3" to MediaFormat.MIMETYPE_AUDIO_MPEG,
        "flac" to MediaFormat.MIMETYPE_AUDIO_FLAC,
        // DTS: Android'de resmî MIME sabiti yok; isimlendirme "dtsc"/"dtsh"/"dtsl"/"dtse".
        "dtsc" to "audio/vnd.dts",
        "dtsh" to "audio/vnd.dts.hd",
        "dtsl" to "audio/vnd.dts.hd",
        "dtse" to "audio/vnd.dts.hd"
    )

    /** MIME → cihazda decoder var mı (null = henüz bakılmadı). */
    private val decoderCache = ConcurrentHashMap<String, Boolean>()

    /** Cihazın tüm decoder MIME'leri (tek seferde okunur). */
    private val availableDecoderMimes: Set<String> by lazy {
        runCatching {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            list.codecInfos
                .filter { it.isEncoder.not() }
                .flatMap { info ->
                    info.supportedTypes.map { it.lowercase(Locale.ROOT) }
                }
                .toSet()
        }.getOrElse {
            Log.w(TAG, "MediaCodecList okunamadı, iyimser varsayım kullanılacak: ${it.message}")
            emptySet()
        }
    }

    /** Hiç codec bilgisi okunamadıysa (ör. test ortamı) temkinli davran. */
    private val hasCodecInventory: Boolean by lazy { availableDecoderMimes.isNotEmpty() }

    /** Belirli bir MIME için donanım/yazılım decoder var mı? */
    fun isMimeSupported(mime: String): Boolean {
        if (!hasCodecInventory) return true // Bilgi yoksa indirmeyi engellemeyelim.
        return decoderCache.getOrPut(mime.lowercase(Locale.ROOT)) {
            availableDecoderMimes.contains(mime.lowercase(Locale.ROOT))
        }
    }

    /**
     * HLS `CODECS` niteliğindeki tek bir codec kimliğini yorumlar.
     *
     * Örnek: `avc1.64001f` → H.264, `hvc1.1.6.L93.B0` → HEVC,
     *        `av01.0.05M.08` → AV1, `ec-3` → E-AC-3.
     *
     * @return `null` → tanınmayan/önemsiz codec (karar veremiyoruz)
     */
    fun isCodecStringSupported(codec: String?): Boolean? {
        val raw = codec?.trim()?.lowercase(Locale.ROOT) ?: return null
        if (raw.isEmpty()) return null

        VIDEO_MIME_BY_CODEC.firstOrNull { raw.startsWith(it.first) }?.let { (_, mime) ->
            return isMimeSupported(mime)
        }
        AUDIO_MIME_BY_CODEC.firstOrNull { raw.startsWith(it.first) }?.let { (_, mime) ->
            return isMimeSupported(mime)
        }
        return null
    }

    /**
     * HLS `CODECS="avc1.64001f,mp4a.40.2"` listesinin **tamamı** destekleniyor mu?
     *
     * Bilinmeyen codec'ler sorun sayılmaz; yalnızca tanınıp **desteklenmediği**
     * kesinleşen bir codec varsa `false` döner.
     */
    fun isCodecListSupported(codecs: String?): Boolean {
        val list = codecs?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: return true
        for (codec in list) {
            if (isCodecStringSupported(codec) == false) return false
        }
        return true
    }

    /**
     * İndirilecek dosyanın uzantısı: kapsayıcı gerçekten ne ise o.
     *
     * Eski kod her şeyi `.mp4` yazıyordu; TS birleştirmeleri `.mp4` adıyla
     * kaydediliyordu. Oynatıcılar genelde içeriğe bakıp çalar ama bunu
     * garanti etmek için doğru uzantıyı yazmak daha güvenli.
     */
    fun extensionFor(hlsSegmentExtension: String?): String {
        val ext = hlsSegmentExtension?.lowercase(Locale.ROOT)?.trim()
        return when {
            ext == null || ext.isEmpty() -> "mp4"
            ext in setOf("ts", "m2ts", "mts") -> "ts"
            ext == "m4s" || ext == "mp4" || ext == "m4v" -> "mp4"
            ext == "aac" -> "m4a"
            else -> ext
        }
    }

    /** Teşhis metni (ayarlar > eklenti tanılama benzeri ekranlarda kullanılabilir). */
    fun describe(): String {
        if (!hasCodecInventory) return "Codec bilgisi okunamadı (cihaz API'si yanıt vermedi)"
        val checks = listOf(
            "H.264" to MediaFormat.MIMETYPE_VIDEO_AVC,
            "HEVC" to MediaFormat.MIMETYPE_VIDEO_HEVC,
            "AV1" to MediaFormat.MIMETYPE_VIDEO_AV1,
            "VP9" to MediaFormat.MIMETYPE_VIDEO_VP9,
            "AAC" to MediaFormat.MIMETYPE_AUDIO_AAC,
            "AC-3" to MediaFormat.MIMETYPE_AUDIO_AC3,
            "E-AC-3" to MediaFormat.MIMETYPE_AUDIO_EAC3,
            "Opus" to MediaFormat.MIMETYPE_AUDIO_OPUS
        )
        return "Android ${Build.VERSION.RELEASE} • " + checks.joinToString(" · ") { (label, mime) ->
            "$label=${if (isMimeSupported(mime)) "✓" else "✗"}"
        }
    }
}
