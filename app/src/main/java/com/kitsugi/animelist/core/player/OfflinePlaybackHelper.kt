package com.kitsugi.animelist.core.player

import android.content.Context
import android.util.Log
import java.io.File
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

/**
 * T1.14 – OfflinePlaybackHelper
 *
 * Yerel depolama alanında indirilen video/altyazı dosyalarını yönetir ve
 * bu dosyaları oynatmak için gerekli parametreleri hazırlar.
 *
 * ### Sorumluluğu
 * - İndirilen medya dosyalarını önbellek dizininde listeler.
 * - Bir lokal dosya URI'sini `PlayerEngine.prepare()` için uygun parametrelere
 *   çevirir.
 * - Silinmiş veya bozuk dosyaları tespit eder.
 *
 * ### Dizin Yapısı (güncel, düz)
 * ```
 * Download/Kitsugi/Video/
 *   <Başlık>_Bölüm_N.mp4              (video)
 *   <Başlık>_Bölüm_N.json             (metadata: mediaId, title, downloadedAtMs)
 *   <Başlık>_Bölüm_N.tr_Turkish.srt   (altyazılar: <taban>.<dil>_<isim>.<uzantı>)
 * Download/Kitsugi/Images/
 *   <Başlık>_Bölüm_N.jpg              (poster)
 * ```
 * Eski sürümlerin `<mediaId>/` alt klasörleri (video.mp4 + subs/ + metadata.json) de okunmaya devam eder.
 */
object OfflinePlaybackHelper {

    private const val TAG = "OfflinePlaybackHelper"
    private const val DOWNLOADS_DIR = "Kitsugi_downloads"
    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "webm", "ts", "m2ts")
    private val SUBTITLE_EXTENSIONS = setOf("srt", "vtt", "ass", "ssa")
    private val WHITESPACE_REGEX = Regex("\\s+")

    // ── Data classes ──────────────────────────────────────────────────────────

    /**
     * Oynatmaya hazır yerel medya tanımı.
     */
    data class LocalMedia(
        /** İçerik ID'si (AniList ID veya özel) */
        val mediaId: String,
        /** İnsan okunabilir başlık */
        val title: String,
        /** Yerel video dosyası URI'si (file:// prefix'li) */
        val videoUri: String,
        /** Varsa indirilen altyazı listesi */
        val subtitles: List<LocalSubtitle>,
        /** Video dosyası boyutu (bytes) */
        val fileSizeBytes: Long,
        /** İndirme tarihi (epoch ms) */
        val downloadedAtMs: Long,
        /** Toplam süre tahmini ms (metadata'dan okunur, 0 = bilinmiyor) */
        val durationMs: Long = 0L,
    )

    data class LocalSubtitle(
        val language: String,
        val label: String,
        val uri: String,  // file:// prefix'li
    )

    /**
     * Metadata dosyasında saklanan alan (JSON formatı).
     */
    data class DownloadMetadata(
        val mediaId: String = "",
        val title: String = "",
        val durationMs: Long = 0L,
        val downloadedAtMs: Long = 0L,
    )

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * İndirme kök dizinini döner.
     * Dizin yoksa oluşturulur.
     */
    fun getDownloadsDir(context: Context): File {
        val publicDir = File(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
            "Kitsugi/Video"
        )
        if (publicDir.exists() || publicDir.mkdirs()) {
            return publicDir
        }
        val extDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
        if (extDir != null) {
            val fallback = File(extDir, "Kitsugi/Video").also { it.mkdirs() }
            if (fallback.exists()) return fallback
        }
        return File(context.filesDir, DOWNLOADS_DIR).also { it.mkdirs() }
    }

    /**
     * Tüm indirilen medyaları listeler.
     * Bozuk veya eksik video dosyaları silindi/geçersiz olduğunda atlanır.
     */
    fun listDownloadedMedia(context: Context): List<LocalMedia> {
        val primaryRoot = getDownloadsDir(context)
        val legacyCacheRoot = File(context.cacheDir, DOWNLOADS_DIR)

        val primaryEntries = if (primaryRoot.exists()) primaryRoot.listFiles()?.toList().orEmpty() else emptyList()
        // `Klipler` alt klasörü detay sayfasındaki küçük videolara aittir; içindeki tek dosya
        // "en büyük video" olarak okunup çevrimdışı **bölüm** listesine düşmemeli.
        val primaryDirs = primaryEntries.filter {
            it.isDirectory && it.name != com.kitsugi.animelist.core.download.DownloadExportNaming.CLIP_FOLDER
        }
        val legacyDirs = if (legacyCacheRoot.exists()) legacyCacheRoot.listFiles()?.filter { it.isDirectory }.orEmpty() else emptyList()

        val allDirs = (primaryDirs + legacyDirs).distinctBy { it.name }
        val legacy = allDirs.mapNotNull { dir -> readLocalMedia(dir) }
        val flat = flatVideoFilesFromEntries(primaryEntries).mapNotNull { video ->
            readFlatMedia(primaryRoot, video, primaryEntries)
        }

        return (legacy + flat)
            .distinctBy { it.mediaId }
            .sortedByDescending { it.downloadedAtMs }
    }

    /**
     * Belirli bir [mediaId] için yerel medyayı okur.
     * Dosya yoksa `null` döner.
     */
    fun getLocalMedia(context: Context, mediaId: String): LocalMedia? {
        val primaryRoot = getDownloadsDir(context)
        val primaryDir = File(primaryRoot, mediaId)
        if (primaryDir.isDirectory) return readLocalMedia(primaryDir)

        val legacyDir = File(File(context.cacheDir, DOWNLOADS_DIR), mediaId)
        if (legacyDir.isDirectory) return readLocalMedia(legacyDir)

        val primaryEntries = if (primaryRoot.isDirectory) primaryRoot.listFiles()?.toList().orEmpty() else emptyList()
        for (video in flatVideoFilesFromEntries(primaryEntries)) {
            val base = video.nameWithoutExtension
            val metadata = readFlatMetadata(File(primaryRoot, "$base.json"))
            val resolvedId = metadata?.mediaId?.takeIf { it.isNotBlank() } ?: base
            if (resolvedId == mediaId) {
                return LocalMedia(
                    mediaId        = resolvedId,
                    title          = metadata?.title?.takeIf { it.isNotBlank() } ?: base,
                    videoUri       = "file://${video.absolutePath}",
                    subtitles      = readFlatSubtitles(primaryRoot, base, primaryEntries),
                    fileSizeBytes  = video.length(),
                    downloadedAtMs = metadata?.downloadedAtMs?.takeIf { it > 0 } ?: video.lastModified(),
                    durationMs     = metadata?.durationMs ?: 0L,
                )
            }
        }
        return null
    }

    /**
     * Yerel medyayı `PlayerEngine.prepare()` için hazır parametrelere çevirir.
     *
     * @return `null` eğer video dosyası artık mevcut değilse.
     */
    fun buildPlayerParams(media: LocalMedia): PlayerParams? {
        val videoFile = File(media.videoUri.removePrefix("file://"))
        if (!videoFile.exists() || !videoFile.isFile) {
            Log.w(TAG, "Video file missing for mediaId=${media.mediaId}: ${media.videoUri}")
            return null
        }
        return PlayerParams(
            videoUri   = media.videoUri,
            audioUri   = null,
            headers    = emptyMap(),
            subtitles  = media.subtitles.map {
                SubtitleInput(url = it.uri, lang = it.language, name = it.label)
            },
            title      = media.title,
            isOffline  = true,
        )
    }

    /**
     * Bir indirme dizinini tamamen siler.
     *
     * @return Silme başarılıysa `true`.
     */
    fun deleteDownload(context: Context, mediaId: String): Boolean {
        val root = getDownloadsDir(context)

        // Düz yerleşim: video + metadata + altyazılar (aynı taban ad)
        val flatTargets = flatVideoFiles(root)
            .filter { readFlatMedia(root, it)?.mediaId == mediaId }
        var flatDeleted = false
        for (video in flatTargets) {
            val base = video.nameWithoutExtension
            val related = root.listFiles()?.filter { f ->
                f.isFile && (f == video ||
                    f.name == "$base.json" ||
                    (f.name.startsWith("$base.") && f.extension.lowercase() in SUBTITLE_EXTENSIONS))
            }.orEmpty()
            related.forEach { f -> flatDeleted = f.delete() || flatDeleted }
        }

        // Eski sürüm: <mediaId>/ alt klasörü
        val dir = File(root, mediaId)
        val legacyDeleted = dir.exists() && dir.deleteRecursively()
        Log.i(TAG, "Deleted download mediaId=$mediaId flat=$flatDeleted legacy=$legacyDeleted")
        return flatDeleted || legacyDeleted
    }

    /**
     * Toplam kullanılan disk alanını bytes cinsinden döner.
     */
    fun totalDiskUsageBytes(context: Context): Long {
        val root = getDownloadsDir(context)
        return root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * Belirli bir medya için indirme ilerleme dosyasının varlığını kontrol eder.
     * Yarım kalmış indirme = `.partial` dosyası mevcuttur.
     */
    fun isPartialDownload(context: Context, mediaId: String): Boolean {
        val dir = File(getDownloadsDir(context), mediaId)
        return dir.listFiles()?.any { it.extension == "partial" } ?: false
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun readLocalMedia(dir: File): LocalMedia? {
        val videoFile = findVideoFile(dir) ?: run {
            Log.d(TAG, "No video file in ${dir.name}")
            return null
        }
        val metadata = readMetadata(dir)
        val subtitles = readSubtitles(dir)

        return LocalMedia(
            mediaId        = dir.name,
            title          = metadata?.title?.takeIf { it.isNotBlank() } ?: dir.name,
            videoUri       = "file://${videoFile.absolutePath}",
            subtitles      = subtitles,
            fileSizeBytes  = videoFile.length(),
            downloadedAtMs = metadata?.downloadedAtMs ?: videoFile.lastModified(),
            durationMs     = metadata?.durationMs ?: 0L,
        )
    }

    private fun findVideoFile(dir: File): File? {
        val videoExtensions = setOf("mp4", "mkv", "avi", "webm", "ts", "m2ts")
        return dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in videoExtensions }
            ?.maxByOrNull { it.length() } // Biggest file is likely the video
    }

    private fun readSubtitles(dir: File): List<LocalSubtitle> {
        val subsDir = File(dir, "subs")
        if (!subsDir.exists()) return emptyList()
        val subExtensions = setOf("srt", "vtt", "ass", "ssa")
        return subsDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in subExtensions }
            ?.map { file -> toLocalSubtitle(file, file.nameWithoutExtension) }
            ?: emptyList()
    }

    /**
     * Düz yerleşimde bir videonun yanındaki altyazıları bulur: `<taban>.<dil>_<isim>.<uzantı>`.
     * Önek nokta ile birlikte kontrol edilir; böylece `Bölüm_1` araması `Bölüm_12` altyazılarını almaz.
     */
    private fun readFlatSubtitles(
        root: File,
        baseName: String,
        cachedEntries: List<File>? = null
    ): List<LocalSubtitle> {
        val entries = cachedEntries ?: root.listFiles()?.toList().orEmpty()
        val prefix = "$baseName."
        return entries
            .filter {
                it.isFile && it.name.startsWith(prefix) &&
                    it.extension.lowercase() in SUBTITLE_EXTENSIONS
            }
            .map { file -> toLocalSubtitle(file, file.nameWithoutExtension.removePrefix(prefix)) }
    }

    /** `<dil>_<isim>` biçimindeki ad için dil ve etiket çıkarır. */
    private fun toLocalSubtitle(file: File, name: String): LocalSubtitle {
        val lang = name.substringBefore("_", "en")
        val rest = name.substringAfter("_", "").replace("_", " ").trim().replace(WHITESPACE_REGEX, " ")
        val label = if (rest.isNotEmpty()) rest else when (lang) {
            "tr" -> KitsugiStrings.get(R.string.pd_lang_tr)
            "en" -> "İngilizce"
            "ja" -> "Japonca"
            else -> lang.uppercase()
        }
        return LocalSubtitle(language = lang, label = label, uri = "file://${file.absolutePath}")
    }

    /** Düz yerleşimdeki video dosyaları (yalnızca yanında metadata `.json` dosyası olanlar). */
    private fun flatVideoFiles(root: File): List<File> {
        if (!root.isDirectory) return emptyList()
        val entries = root.listFiles()?.toList().orEmpty()
        return flatVideoFilesFromEntries(entries)
    }

    private fun flatVideoFilesFromEntries(entries: List<File>): List<File> {
        if (entries.isEmpty()) return emptyList()
        val fileNames = entries.asSequence().filter { it.isFile }.mapTo(HashSet()) { it.name }
        return entries.filter {
            it.isFile && it.extension.lowercase() in VIDEO_EXTENSIONS &&
                "${it.nameWithoutExtension}.json" in fileNames
        }
    }

    /** Düz yerleşimdeki bir videoyu [LocalMedia]'ya çevirir. */
    private fun readFlatMedia(
        root: File,
        video: File,
        cachedEntries: List<File>? = null
    ): LocalMedia? {
        val base = video.nameWithoutExtension
        val metadata = readFlatMetadata(File(root, "$base.json"))
        return LocalMedia(
            mediaId        = metadata?.mediaId?.takeIf { it.isNotBlank() } ?: base,
            title          = metadata?.title?.takeIf { it.isNotBlank() } ?: base,
            videoUri       = "file://${video.absolutePath}",
            subtitles      = readFlatSubtitles(root, base, cachedEntries),
            fileSizeBytes  = video.length(),
            downloadedAtMs = metadata?.downloadedAtMs?.takeIf { it > 0 } ?: video.lastModified(),
            durationMs     = metadata?.durationMs ?: 0L,
        )
    }

    private fun readFlatMetadata(metaFile: File): DownloadMetadata? {
        if (!metaFile.isFile) return null
        return try {
            val json = org.json.JSONObject(metaFile.readText())
            DownloadMetadata(
                mediaId        = json.optString("mediaId", ""),
                title          = json.optString("title", ""),
                durationMs     = json.optLong("durationMs", 0L),
                downloadedAtMs = json.optLong("downloadedAtMs", 0L),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse metadata ${metaFile.name}: ${e.message}")
            null
        }
    }

    private fun readMetadata(dir: File): DownloadMetadata? {
        val metaFile = File(dir, "metadata.json")
        if (!metaFile.exists()) return null
        return try {
            val json = metaFile.readText()
            // Simple manual parse – avoid heavy Gson dependency in this layer
            DownloadMetadata(
                mediaId       = extractJsonString(json, "mediaId") ?: dir.name,
                title         = extractJsonString(json, "title") ?: "",
                durationMs    = extractJsonLong(json, "durationMs") ?: 0L,
                downloadedAtMs = extractJsonLong(json, "downloadedAtMs") ?: 0L,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse metadata for ${dir.name}: ${e.message}")
            null
        }
    }

    /** Minimal JSON string field extractor (no external dependency). */
    private fun extractJsonString(json: String, key: String): String? {
        val regex = Regex(""""$key"\s*:\s*"([^"]*)"""")
        return regex.find(json)?.groupValues?.getOrNull(1)
    }

    /** Minimal JSON long field extractor. */
    private fun extractJsonLong(json: String, key: String): Long? {
        val regex = Regex(""""$key"\s*:\s*(\d+)""")
        return regex.find(json)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    // ── PlayerParams ─────────────────────────────────────────────────────────

    /**
     * Oynatıcıya iletilecek hazır parametre paketi.
     * [PlayerEngine.prepare] imzasıyla uyumludur.
     */
    data class PlayerParams(
        val videoUri: String,
        val audioUri: String?,
        val headers: Map<String, String>,
        val subtitles: List<SubtitleInput>,
        val title: String,
        val isOffline: Boolean,
    )
}
