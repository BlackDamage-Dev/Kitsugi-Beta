package com.kitsugi.animelist.core.download

import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.util.Locale

/**
 * İndirilen dosyanın **gerçekten oynatılabilir** olduğunu doğrular.
 *
 * ## Neden?
 *
 * Kullanıcı şikâyeti net: "cihazın desteklemediği formatta inerse bir anlamı kalmaz".
 * Varyant seçimi (bkz. [HlsPlaylistParser.selectVariant]) `CODECS` niteliğine bakar
 * ama bu bilgi:
 *  - Bazı kaynaklarda **hiç yok** (`CODECS` boş),
 *  - Bazı kaynaklarda **yanlış** (ör. `avc1` yazıp AV1 göndermek),
 *  - Ses tarafını hiç söylemez (AC-3 sürprizi).
 *
 * Bu yüzden indirme bittikten sonra dosyayı `MediaExtractor` ile açıp gerçek
 * track'leri ve decoder mevcudiyetini kontrol ederiz. Böylece kullanıcıya
 * "indirildi" deyip oynatamayacağı bir dosya bırakmayız.
 *
 * Doğrulama **hızlıdır**: yalnızca başlığı (moov/PMT) okur, dosyanın tamamını
 * taramaz. 700 MB'lık bir dosyada tipik süre 20-80 ms.
 */
object DownloadVerifier {

    private const val TAG = "DownloadVerifier"

    /** Doğrulama sonucu. */
    data class Result(
        val playable: Boolean,
        /** Kısa, kullanıcıya gösterilebilir açıklama (sorun yoksa null). */
        val warning: String? = null,
        /** Teşhis için track özeti. */
        val details: String = ""
    ) {
        companion object {
            val OK = Result(playable = true)
        }
    }

    /**
     * Dosyayı doğrular.
     *
     * @param file indirilen video dosyası
     * @param isSupported MIME → cihazda decoder var mı (test edilebilirlik için enjekte)
     */
    fun verify(
        file: File,
        mimeSupported: (String) -> Boolean = DeviceCodecSupport::isMimeSupported
    ): Result {
        if (!file.exists() || file.length() == 0L) {
            return Result(playable = false, warning = "Dosya oluşmadı veya boş (0 bayt).")
        }

        val extractor = MediaExtractor()
        val videoMimes = mutableListOf<String>()
        val audioMimes = mutableListOf<String>()
        var tracks = 0

        try {
            extractor.setDataSource(file.absolutePath)
            for (i in 0 until extractor.trackCount) {
                val format = runCatching { extractor.getTrackFormat(i) }.getOrNull() ?: continue
                val mime = format.getString(MediaFormat.KEY_MIME)?.lowercase(Locale.ROOT) ?: continue
                tracks++
                when {
                    mime.startsWith("video/") -> videoMimes += mime
                    mime.startsWith("audio/") -> audioMimes += mime
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Doğrulama başarısız (dosya açılamadı): ${file.name} — ${e.message}")
            return Result(
                playable = false,
                warning = "İndirilen dosya açılamadı (bozuk ya da desteklenmeyen kapsayıcı)."
            )
        } finally {
            runCatching { extractor.release() }
        }

        val details = "tracks=$tracks video=$videoMimes audio=$audioMimes size=${file.length()}B"

        if (tracks == 0) {
            return Result(
                playable = false,
                warning = "Dosyada ses/görüntü izi bulunamadı — indirme bozuk.",
                details = details
            )
        }

        if (videoMimes.isEmpty()) {
            return Result(
                playable = false,
                warning = "Dosyada görüntü izi yok (yalnızca ses indirilmiş olabilir).",
                details = details
            )
        }

        // ── Görüntü: hiçbiri desteklenmiyorsa oynatılamaz ────────────────────
        val supportedVideo = videoMimes.filter(mimeSupported)
        if (supportedVideo.isEmpty()) {
            val names = videoMimes.joinToString(", ") { friendlyName(it) }
            Log.w(TAG, "Desteklenmeyen görüntü formatı: $names ($details)")
            return Result(
                playable = false,
                warning = "Bu bölüm cihazınızın çözemediği bir görüntü formatında ($names). " +
                    "Dosya indirildi ama oynatılamaz; farklı bir kaynak/kalite deneyin.",
                details = details
            )
        }

        // ── Ses: görüntü oynar ama ses çalmazsa yarım deneyim ────────────────
        if (audioMimes.isNotEmpty() && audioMimes.none(mimeSupported)) {
            val names = audioMimes.joinToString(", ") { friendlyName(it) }
            Log.w(TAG, "Desteklenmeyen ses formatı: $names ($details)")
            return Result(
                playable = false,
                warning = "Görüntü oynatılabilir ama ses formatı ($names) cihazınızda desteklenmiyor. " +
                    "Ses duymayacaksınız; farklı bir kaynak deneyin.",
                details = details
            )
        }

        Log.d(TAG, "Doğrulama tamam: ${file.name} ($details)")
        return Result(playable = true, details = details)
    }

    /** MIME → kullanıcıya anlaşılır ad. */
    internal fun friendlyName(mime: String): String {
        val m = mime.lowercase(Locale.ROOT)
        return when {
            m.startsWith("video/avc") -> "H.264"
            m.contains("hevc") || m.contains("h265") -> "H.265/HEVC"
            m.contains("av01") -> "AV1"
            m.contains("vp9") -> "VP9"
            m.contains("vp8") -> "VP8"
            m.contains("dolby") -> "Dolby Vision"
            m.contains("mpeg4") -> "MPEG-4"
            m.contains("eac3") || m.contains("ec-3") || m.contains("e-ac-3") || m.contains("eac-3") -> "E-AC-3"
            m.contains("ac3") || m.contains("ac-3") -> "AC-3"
            m.contains("dts") -> "DTS"
            m.contains("opus") -> "Opus"
            m.contains("vorbis") -> "Vorbis"
            m.contains("aac") || m.contains("mp4a") -> "AAC"
            m.contains("mpeg") -> "MP3"
            m.contains("flac") -> "FLAC"
            else -> mime.substringAfter('/').uppercase(Locale.ROOT)
        }
    }

    /** Cihazın desteklediği codec listesini özetler (teşhis ekranları için). */
    fun describeDevice(): String = DeviceCodecSupport.describe()

    /** Testlerde/teşhiste kullanılabilir: verilen MIME'lerin hepsi destekleniyor mu? */
    fun allSupported(mimes: List<String>, mimeSupported: (String) -> Boolean = DeviceCodecSupport::isMimeSupported): Boolean =
        mimes.all(mimeSupported)

    /** MediaCodecList'i doğrudan sorgular (önbelleği atlar, teşhis için). */
    internal fun hasDecoderFor(mime: String): Boolean = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            !info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
        }
    }.getOrDefault(false)
}
