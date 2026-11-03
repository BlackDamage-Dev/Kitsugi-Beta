package com.kitsugi.animelist.core.download

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import java.io.File

/**
 * İndirilen bölümlerin **dosya adlarının** tek kaynağı.
 *
 * Bir bölüm indirildiğinde tüm dosyalar düz bir yapıda, aynı taban adla yazılır:
 *  - video:      `Download/Kitsugi/Video/<Başlık>_Bölüm_N.mp4`
 *  - altyazılar: `Download/Kitsugi/Video/<Başlık>_Bölüm_N.<dil>_<isim>.<uzantı>`
 *  - metadata:   `Download/Kitsugi/Video/<Başlık>_Bölüm_N.json`
 *  - poster:     `Download/Kitsugi/Images/<Başlık>_Bölüm_N.jpg`
 *
 * Ayrı bir çalışma klasörü ve kopyalama adımı yoktur; ad indirmeden önce bir kez üretilir
 * ve silme akışı da aynı fonksiyonlardan beslenir. Böylece yeniden indirmede `"(1)"`
 * yinelenen dosyalar oluşmaz, eski dosya da kalmaz.
 */
object DownloadExportNaming {

    private const val TAG = "DownloadExportNaming"

    /** Dosya adlarında yasak karakterler: \ / : * ? " < > | */
    private val ILLEGAL_CHARS = Regex("[\\\\/:*?\"<>|]")

    private val SUBTITLE_EXTENSIONS = setOf("srt", "vtt", "ass", "ssa", "sub")

    /** `Download/Kitsugi` kök klasörü. */
    fun publicRoot(): File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "Kitsugi"
    )

    /** Video, altyazı ve metadata dosyalarının bulunduğu klasör (düz yapı). */
    fun publicVideoDir(): File = File(publicRoot(), "Video")

    /** Poster görsellerinin klasörü (`KitsugiImageDownloadHelper` ile ortak). */
    fun publicImagesDir(): File = File(publicRoot(), "Images")

    /** Başlığı dosya adına uygun hâle getirir (boşsa "Video"). */
    fun sanitizeTitle(title: String): String =
        title.replace(ILLEGAL_CHARS, "_").trim().ifBlank { "Video" }

    /** Ör. `Naruto_Bölüm_12.mp4`; film ise `Naruto.mp4`. */
    fun exportFileName(animeTitle: String, episode: Int): String {
        val cleanTitle = sanitizeTitle(animeTitle)
        val isMovie = episode <= 1 &&
            (animeTitle.contains("Film", ignoreCase = true) || animeTitle.contains("Movie", ignoreCase = true))
        return if (isMovie && !cleanTitle.contains("Bölüm", ignoreCase = true)) {
            "$cleanTitle.mp4"
        } else {
            "${cleanTitle}_Bölüm_${episode}.mp4"
        }
    }

    /** Uzantısız taban ad (`Naruto_Bölüm_12`) — diğer tüm dosyalar bu adla başlar. */
    fun exportBaseName(animeTitle: String, episode: Int): String =
        exportFileName(animeTitle, episode).removeSuffix(".mp4")

    /**
     * Altyazı dosya adı: `<taban>.<dil>_<isim>.<uzantı>` (isim boşsa `<taban>.<dil>.<uzantı>`).
     * Dil kodu alt çizgi içermediği için okuyucu dili `_`'den önceki kısımdan ayırabilir.
     */
    fun subtitleFileName(baseName: String, lang: String, name: String, extension: String): String {
        val safeLang = lang.lowercase().filter { it.isLetterOrDigit() }.ifEmpty { "en" }
        val safeName = name.replace(ILLEGAL_CHARS, "_").trim()
        val label = if (safeName.isBlank() || safeName.equals(safeLang, ignoreCase = true)) {
            safeLang
        } else {
            "${safeLang}_$safeName"
        }
        return "$baseName.$label.$extension"
    }

    /** Metadata dosyası: `<taban>.json`. */
    fun metadataFileName(baseName: String): String = "$baseName.json"

    /** Poster dosyası: `<taban>.jpg`. */
    fun posterFileName(baseName: String): String = "$baseName.jpg"

    // ── Küçük video (klip) adlandırma ─────────────────────────────────────────

    /**
     * Detay sayfasındaki küçük videoların (fragman, kamera arkası, tema) yazıldığı alt klasör.
     *
     * Neden `Kitsugi/Video` kökünde değil? Bölüm indirmeleri kökte **düz** durur ve
     * [com.kitsugi.animelist.core.player.OfflinePlaybackHelper.listDownloadedMedia] kökteki
     * her videoyu "indirilmiş bölüm" olarak okur. Klipler kendi alt klasöründe tutulunca
     * çevrimdışı bölüm listesi kirlenmez; [com.kitsugi.animelist.core.storage.DownloadStorage]
     * ölçümü özyinelemeli olduğu için depolama sayacı yine doğru çalışır.
     */
    const val CLIP_FOLDER = "Klipler"

    /** Bir dosya/klasör adının güvenli üst sınırı (UTF-8 bayt değil, karakter). */
    private const val MAX_CLIP_NAME_CHARS = 90

    /**
     * Klip klasörü: `Download/Kitsugi/Video/Klipler`.
     *
     * @param create `false` → yalnızca yol hesaplanır, disk işlemi yapılmaz. UI/ana iş parçacığından
     *   çağrılırken bu kullanılmalı; klasörü indirme işçisi (IO) oluşturur.
     */
    fun clipDir(context: Context, create: Boolean = true): File {
        val dir = File(
            com.kitsugi.animelist.core.player.OfflinePlaybackHelper.getDownloadsDir(context),
            CLIP_FOLDER
        )
        if (create) runCatching { dir.mkdirs() }
        return dir
    }

    /**
     * Klip dosyasının uzantısız taban adı: `<Başlık>_<Video>_<sıra>`.
     *
     * Aynı video tekrar indirilmez; ancak aynı **adı** taşıyan farklı videolar (ör. iki ayrı
     * "Fragman") `sıra` ile ayrılır; böylece hiçbir zaman üzerine yazma olmaz.
     */
    fun clipBaseName(mediaTitle: String, clipTitle: String, index: Int): String {
        val media = sanitizeClipPart(mediaTitle).ifBlank { "Kitsugi" }
        val clip = sanitizeClipPart(clipTitle).ifBlank { "Video" }
        val combined = "${media}_${clip}".trim('_', ' ', '.')
        val capped = combined.take(MAX_CLIP_NAME_CHARS).trim('_', ' ', '.')
        val safeBase = capped.ifBlank { "Kitsugi_Video" }
        return "${safeBase}_${index.coerceAtLeast(1)}"
    }

    /**
     * Klip adı parçası: yasak karakterler temizlenir, ardışık boşluklar teklenir.
     */
    private fun sanitizeClipPart(raw: String): String = raw
        .replace(ILLEGAL_CHARS, " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /** Klip dosya adı: `<taban>.<uzantı>`. */
    fun clipFileName(baseName: String, extension: String): String {
        val safeExt = extension.lowercase().filter { it.isLetterOrDigit() }.ifBlank { "mp4" }
        return "$baseName.$safeExt"
    }

    /**
     * Bir bölümün dışa aktarılmış dosyalarını (video, altyazılar, metadata, poster) siler.
     * Uygulama içi eski `«mediaId»/` klasörüne dokunmaz — orası
     * [com.kitsugi.animelist.core.player.OfflinePlaybackHelper] tarafından yönetilir.
     *
     * @return silinen dosya sayısı
     */
    fun deleteExports(context: Context, animeTitle: String, episode: Int): Int {
        val baseName = exportBaseName(animeTitle, episode)
        val appContext = context.applicationContext

        val roots = buildList {
            add(publicRoot())
            appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { add(File(it, "Kitsugi")) }
        }

        fun isSubtitleOfBase(name: String): Boolean =
            name.startsWith("$baseName.", ignoreCase = true) &&
                File(name).extension.lowercase() in SUBTITLE_EXTENSIONS

        val deletedPaths = ArrayList<String>()
        var removed = 0
        for (root in roots) {
            // Video klasörü: video + metadata + altyazılar
            removed += deleteMatching(File(root, "Video"), deletedPaths) { name ->
                name.equals("$baseName.mp4", ignoreCase = true) ||
                    name.equals(metadataFileName(baseName), ignoreCase = true) ||
                    isSubtitleOfBase(name)
            }
            // Eski sürümlerin ayrı Subtitles klasörü (geriye dönük temizlik)
            removed += deleteMatching(File(root, "Subtitles"), deletedPaths) { name -> isSubtitleOfBase(name) }
            // Poster
            removed += deleteMatching(File(root, "Images"), deletedPaths) { name ->
                name.equals(posterFileName(baseName), ignoreCase = true)
            }
        }

        if (deletedPaths.isNotEmpty()) {
            runCatching {
                MediaScannerConnection.scanFile(appContext, deletedPaths.toTypedArray(), null, null)
            }.onFailure { Log.w(TAG, "MediaScanner güncellemesi başarısız: ${it.message}") }
        }
        Log.i(TAG, "Dışa aktarılan dosyalar silindi: $removed dosya (taban: $baseName)")
        return removed
    }

    private inline fun deleteMatching(
        dir: File,
        deletedPaths: MutableList<String>,
        predicate: (String) -> Boolean
    ): Int {
        if (!dir.isDirectory) return 0
        val files = runCatching { dir.listFiles() }.getOrNull() ?: return 0
        var removed = 0
        for (file in files) {
            if (!file.isFile || !predicate(file.name)) continue
            val deleted = runCatching { file.delete() && !file.exists() }.getOrDefault(false)
            if (deleted) {
                removed++
                deletedPaths += file.absolutePath
            }
        }
        return removed
    }
}
