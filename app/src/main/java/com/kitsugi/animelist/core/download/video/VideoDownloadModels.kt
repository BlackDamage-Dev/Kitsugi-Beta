package com.kitsugi.animelist.core.download.video

/**
 * Detay sayfasındaki "küçük videolar" (fragman / PV / OP / ED / müzik videosu) için
 * indirme durumu.
 *
 * Tasarım notu: bu videolar tek bir kaynaktan gelmiyor. Aynı satır bir gün doğrudan bir
 * `.mp4` dosyası, başka bir gün imzalı bir YouTube akışı (HLS veya ayrı ses/görüntü),
 * bir gün de bir eklentinin ürettiği `.m3u8` olabiliyor. Bu yüzden durum modeli
 * "hangi yoldan indi" sorusunu değil, **kullanıcıya ne olduğunu** taşır:
 * hazırlanıyor → iniyor → birleştiriliyor → bitti/başarısız.
 */
data class VideoDownloadState(
    val phase: VideoDownloadPhase = VideoDownloadPhase.IDLE,
    /** 0f..1f arası ilerleme. Toplam boyut bilinmiyorsa parça/segment oranıdır. */
    val progress: Float = 0f,
    val message: String? = null,
    val fileName: String? = null,
    val outputPath: String? = null
) {
    val inProgress: Boolean
        get() = phase == VideoDownloadPhase.RESOLVING ||
            phase == VideoDownloadPhase.DOWNLOADING ||
            phase == VideoDownloadPhase.MERGING

    val completed: Boolean get() = phase == VideoDownloadPhase.COMPLETED
    val failed: Boolean get() = phase == VideoDownloadPhase.FAILED

    /** Satırda küçük bir ilerleme göstergesi çizmek için 0..100. */
    val percent: Int get() = (progress.coerceIn(0f, 1f) * 100f).toInt()
}

enum class VideoDownloadPhase {
    IDLE,
    RESOLVING,
    DOWNLOADING,
    MERGING,
    COMPLETED,
    FAILED
}

/**
 * Tek bir video indirme isteği.
 *
 * [alternateUrls] önemli: AnimeThemes gibi kaynaklar aynı temayı birden çok dosyada
 * sunuyor (VP9 `.webm`, H.264 `.mp4`, farklı çözünürlükler). Birinci adres indirilemezse
 * sıradaki denenir; böylece "bu videoyu indiremiyorum" durumu neredeyse ortadan kalkar.
 */
data class VideoDownloadRequest(
    /** UI anahtarı — video satırının `key` alanı ile aynı olmalı. */
    val key: String,
    val title: String,
    val url: String,
    val alternateUrls: List<String> = emptyList()
)
