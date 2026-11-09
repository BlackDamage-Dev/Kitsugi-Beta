package com.kitsugi.animelist.core.download

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import java.io.File
import java.util.Locale

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

    val SUBTITLE_EXTENSIONS: Set<String> = setOf("srt", "vtt", "ass", "ssa", "sub")
    val VIDEO_EXTENSIONS: Set<String> = setOf("mp4", "mkv", "webm", "avi", "ts", "m2ts", "m4v", "mov")
    val SUPPORTED_AUDIO_EXTENSIONS: Set<String> = setOf(
        "m4a", "aac", "mp3", "opus", "ogg", "oga", "flac", "wav", "ac3", "eac3", "ec3", "dts", "mka"
    )

    /** `Download/Kitsugi` kök klasörü. */
    fun publicRoot(): File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "Kitsugi"
    )

    /** Video, altyazı ve metadata dosyalarının bulunduğu klasör (düz yapı). */
    fun publicVideoDir(): File = File(publicRoot(), "Video")

    /** Ayrı indirilen ses parçalarının bulunduğu klasör (`Download/Kitsugi/Audio`). */
    fun publicAudioDir(): File = File(publicRoot(), "Audio")

    /** Ayrı indirilen altyazı dosyalarının bulunduğu klasör (`Download/Kitsugi/Subtitles`). */
    fun publicSubtitlesDir(): File = File(publicRoot(), "Subtitles")

    /** Poster görsellerinin klasörü (`KitsugiImageDownloadHelper` ile ortak). */
    fun publicImagesDir(): File = File(publicRoot(), "Images")

    /**
     * Standart Kitsugi indirme klasörlerini (`Video`, `Audio`, `Subtitles`, `Images`)
     * oluşturmayı dener ve birincil ses indirme klasörünü döner.
     *
     * Android 10+ Scoped Storage altında ortak `Download/Kitsugi/...` dizinlerinde `mkdirs()`
     * bazı cihazlarda kısıtlı olabilir; hiçbir zaman istisna fırlatmaz ve uygulama özel yedek
     * dizinlerini de hazırlar. IO iş parçacığından çağrılmalıdır.
     */
    fun ensureStandardDownloadDirs(context: Context): File {
        val appContext = context.applicationContext
        val subDirs = listOf("Video", "Audio", "Subtitles", "Images")
        val publicBase = runCatching { publicRoot() }.getOrNull()
        if (publicBase != null) {
            runCatching { publicBase.mkdirs() }
            subDirs.forEach { name ->
                runCatching { File(publicBase, name).mkdirs() }
            }
        }
        val appBase = runCatching {
            appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { File(it, "Kitsugi") }
        }.getOrNull()
        if (appBase != null) {
            runCatching { appBase.mkdirs() }
            subDirs.forEach { name ->
                runCatching { File(appBase, name).mkdirs() }
            }
        }
        val publicAudio = publicBase?.let { File(it, "Audio") }
        return when {
            publicAudio != null && (publicAudio.exists() || runCatching { publicAudio.mkdirs() }.getOrDefault(false)) ->
                publicAudio
            appBase != null -> File(appBase, "Audio").also { runCatching { it.mkdirs() } }
            else -> File(appContext.filesDir, "Kitsugi/Audio").also { runCatching { it.mkdirs() } }
        }
    }

    /**
     * İndirilen bir ses dosyasının adından çözümlenen üst veri.
     *
     * `AudioExportWorker` adlandırması:
     *  - Dizi bölümü: `<Başlık> - Bölüm <N>.<dil etiket>.<uzantı>`
     *  - Film:        `<Başlık>.<dil etiket>.<uzantı>`
     *  - Video tabanı: `<Başlık>_Bölüm_<N>.<dil>.<uzantı>`
     *  - Serbest ses: `<Dosya Adı>.<uzantı>`
     */
    data class ParsedAudioFileName(
        val displayTitle: String,
        val animeTitle: String?,
        val episode: Int?,
        val trackLabel: String?,
        val languageBadge: String?,
        val extension: String
    )

    private val EPISODE_DASH_REGEX = Regex("^(.*?)\\s*-\\s*(?:Bölüm|Bolum|Episode|Ep\\.?)\\s*(\\d+)\\s*$", RegexOption.IGNORE_CASE)
    private val EPISODE_UNDERSCORE_REGEX = Regex("^(.*?)_(?:Bölüm|Bolum|Episode)_(\\d+)$", RegexOption.IGNORE_CASE)
    private val KNOWN_LANG_PREFIX_REGEX = Regex("^([a-zA-Z]{2,3})(?:[_\\s-]+(.+))?$")
    private val COMMON_LANG_CODES = setOf(
        "tr", "tur", "en", "eng", "ja", "jp", "jpn", "de", "deu", "ger",
        "fr", "fra", "fre", "es", "spa", "it", "ita", "ru", "rus", "pt",
        "por", "ar", "ara", "ko", "kor", "zh", "zho", "chi", "hi", "hin",
        "id", "ind", "pl", "pol", "nl", "nld", "uk", "ukr", "vi", "vie",
        "th", "tha", "fa", "per", "az", "aze", "und", "mul"
    )

    fun parseAudioFileName(fileName: String): ParsedAudioFileName {
        val cleanFileName = fileName.trim()
        val ext = cleanFileName.substringAfterLast('.', "").lowercase()
        val withoutExt = if (ext.isNotEmpty() && cleanFileName.contains('.')) {
            cleanFileName.substringBeforeLast('.')
        } else {
            cleanFileName
        }

        // Sondaki ` (1)` gibi MediaStore çakışma ekini ayıkla (ör. `Title.tr (1).m4a` veya `Title.tr.m4a`).
        val normalizedStem = withoutExt.replace(Regex("\\s*\\(\\d+\\)$"), "").trim()

        var mainPart = normalizedStem
        var trackLabel: String? = null
        var languageBadge: String? = null

        val dotIndex = normalizedStem.lastIndexOf('.')
        if (dotIndex > 0 && dotIndex < normalizedStem.lastIndex) {
            val candidateBase = normalizedStem.substring(0, dotIndex).trim()
            val candidateSuffix = normalizedStem.substring(dotIndex + 1).trim()
            val langMatch = KNOWN_LANG_PREFIX_REGEX.matchEntire(candidateSuffix)
            val looksLikeTrackMeta = when {
                langMatch != null && langMatch.groupValues[1].lowercase() in COMMON_LANG_CODES -> true
                EPISODE_DASH_REGEX.matches(candidateBase) || EPISODE_UNDERSCORE_REGEX.matches(candidateBase) -> true
                candidateSuffix.length <= 28 && candidateSuffix.any { it.isLetter() } &&
                    !candidateSuffix.all { it.isDigit() } &&
                    (candidateSuffix.contains("ses", ignoreCase = true) ||
                        candidateSuffix.contains("audio", ignoreCase = true) ||
                        candidateSuffix.contains("dub", ignoreCase = true) ||
                        candidateSuffix.contains("track", ignoreCase = true) ||
                        candidateSuffix.contains("türkçe", ignoreCase = true) ||
                        candidateSuffix.contains("turkce", ignoreCase = true) ||
                        candidateSuffix.contains("english", ignoreCase = true) ||
                        candidateSuffix.contains("japanese", ignoreCase = true) ||
                        candidateSuffix.contains("orijinal", ignoreCase = true)) -> true
                else -> false
            }
            if (looksLikeTrackMeta) {
                mainPart = candidateBase
                trackLabel = candidateSuffix.replace('_', ' ').trim().ifBlank { null }
                if (langMatch != null && langMatch.groupValues[1].lowercase() in COMMON_LANG_CODES) {
                    languageBadge = langMatch.groupValues[1].uppercase()
                }
            }
        }

        val dashMatch = EPISODE_DASH_REGEX.matchEntire(mainPart)
        val underscoreMatch = if (dashMatch == null) EPISODE_UNDERSCORE_REGEX.matchEntire(mainPart) else null

        val animeTitle: String?
        val episode: Int?
        val displayTitle: String

        when {
            dashMatch != null -> {
                val rawTitle = dashMatch.groupValues[1].replace('_', ' ').trim()
                val ep = dashMatch.groupValues[2].toIntOrNull()
                animeTitle = rawTitle.ifBlank { null }
                episode = ep
                displayTitle = if (!animeTitle.isNullOrBlank() && ep != null) {
                    "$animeTitle • Bölüm $ep"
                } else {
                    mainPart
                }
            }
            underscoreMatch != null -> {
                val rawTitle = underscoreMatch.groupValues[1].replace('_', ' ').trim()
                val ep = underscoreMatch.groupValues[2].toIntOrNull()
                animeTitle = rawTitle.ifBlank { null }
                episode = ep
                displayTitle = if (!animeTitle.isNullOrBlank() && ep != null) {
                    "$animeTitle • Bölüm $ep"
                } else {
                    mainPart.replace('_', ' ')
                }
            }
            else -> {
                val cleaned = mainPart.replace('_', ' ').replace(Regex("\\s+"), " ").trim()
                animeTitle = if (trackLabel != null && cleaned.isNotBlank()) cleaned else null
                episode = null
                displayTitle = cleaned.ifBlank { cleanFileName }
            }
        }

        return ParsedAudioFileName(
            displayTitle = displayTitle,
            animeTitle = animeTitle,
            episode = episode,
            trackLabel = trackLabel,
            languageBadge = languageBadge,
            extension = ext
        )
    }

    /** Başlığı dosya adına uygun hâle getirir (boşsa "Video"). */
    fun sanitizeTitle(title: String): String =
        title.replace(ILLEGAL_CHARS, "_").trim().ifBlank { "Video" }

    /** Ör. `Naruto_Bölüm_12.mp4`; film ise `Naruto.mp4`. */
    fun exportFileName(animeTitle: String, episode: Int, extension: String = "mp4"): String {
        val ext = extension.trim().trimStart('.').lowercase(java.util.Locale.ROOT).ifBlank { "mp4" }
        val cleanTitle = sanitizeTitle(animeTitle)
        val isMovie = episode <= 1 &&
            (animeTitle.contains("Film", ignoreCase = true) || animeTitle.contains("Movie", ignoreCase = true))
        return if (isMovie && !cleanTitle.contains("Bölüm", ignoreCase = true)) {
            "$cleanTitle.$ext"
        } else {
            "${cleanTitle}_Bölüm_${episode}.$ext"
        }
    }

    /** Uzantısız taban ad (`Naruto_Bölüm_12`) — diğer tüm dosyalar bu adla başlar. */
    fun exportBaseName(animeTitle: String, episode: Int, extension: String = "mp4"): String =
        exportFileName(animeTitle, episode, extension).substringBeforeLast('.')

    /**
     * Bağımsız ses indirmesi dosya adı (`Download/Kitsugi/Audio` altına yazılır):
     * Ör. `Naruto_Bölüm_12.tr_Türkçe_Dublaj.m4a` veya `Naruto_Bölüm_12.ja.opus`.
     */
    fun audioExportFileName(
        animeTitle: String,
        episode: Int,
        lang: String = "",
        label: String = "",
        extension: String = "m4a"
    ): String {
        val base = exportBaseName(animeTitle, episode)
        val ext = extension.trim().trimStart('.').lowercase(java.util.Locale.ROOT).ifBlank { "m4a" }
        val safeLang = lang.lowercase(java.util.Locale.ROOT).filter { it.isLetterOrDigit() }
        val safeLabel = label.replace(ILLEGAL_CHARS, "_")
            .replace(Regex("\\s+"), "_")
            .trim('_')
            .take(28)
            .takeIf { lbl ->
                lbl.isNotBlank() &&
                    !lbl.equals(safeLang, ignoreCase = true) &&
                    !lbl.startsWith("Ses_", ignoreCase = true) &&
                    !lbl.startsWith("Audio_", ignoreCase = true)
            }
        val middle = when {
            safeLang.isNotBlank() && !safeLabel.isNullOrBlank() -> ".${safeLang}_$safeLabel"
            safeLang.isNotBlank() -> ".$safeLang"
            !safeLabel.isNullOrBlank() -> ".$safeLabel"
            else -> ""
        }
        return "$base$middle.$ext"
    }

    /**
     * Çevrimdışı video oynatımında çoklu ses veya MP4-dışı kodek (ör. E-AC-3) için
     * videonun yanına (`Download/Kitsugi/Video/`) yazılan yan ses dosyası adı.
     */
    fun companionAudioFileName(
        baseName: String,
        lang: String,
        name: String = "",
        extension: String = "m4a"
    ): String {
        val safeLang = lang.lowercase(java.util.Locale.ROOT).filter { it.isLetterOrDigit() }.ifEmpty { "und" }
        val safeName = name.replace(ILLEGAL_CHARS, "_").replace(Regex("\\s+"), "_").trim('_').take(24)
        val tag = if (safeName.isBlank() || safeName.equals(safeLang, ignoreCase = true)) {
            safeLang
        } else {
            "${safeLang}_$safeName"
        }
        val ext = extension.trim().trimStart('.').lowercase(java.util.Locale.ROOT).ifBlank { "m4a" }
        return "$baseName.$tag.$ext"
    }

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

    fun videoMimeType(extension: String): String = when (extension.lowercase(Locale.ROOT).trimStart('.')) {
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "ts", "m2ts" -> "video/mp2t"
        "mov" -> "video/quicktime"
        "avi" -> "video/x-msvideo"
        else -> "video/mp4"
    }

    fun audioMimeType(extension: String): String = when (extension.lowercase(Locale.ROOT).trimStart('.')) {
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "mp3" -> "audio/mpeg"
        "opus" -> "audio/opus"
        "ogg", "oga" -> "audio/ogg"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "ac3" -> "audio/ac3"
        "eac3", "ec3" -> "audio/eac3"
        "dts" -> "audio/vnd.dts"
        "mka" -> "audio/x-matroska"
        "webm" -> "audio/webm"
        else -> "audio/*"
    }

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
                File(name).extension.lowercase(Locale.ROOT) in SUBTITLE_EXTENSIONS

        fun isCompanionAudioOfBase(name: String): Boolean =
            name.startsWith("$baseName.", ignoreCase = true) &&
                File(name).extension.lowercase(Locale.ROOT) in SUPPORTED_AUDIO_EXTENSIONS

        fun isVideoOfBase(name: String): Boolean {
            val f = File(name)
            return f.nameWithoutExtension.equals(baseName, ignoreCase = true) &&
                f.extension.lowercase(Locale.ROOT) in VIDEO_EXTENSIONS
        }

        val deletedPaths = ArrayList<String>()
        var removed = 0
        for (root in roots) {
            // Video klasörü: video + metadata + yan sesler + altyazılar
            removed += deleteMatching(File(root, "Video"), deletedPaths) { name ->
                isVideoOfBase(name) ||
                    name.equals(metadataFileName(baseName), ignoreCase = true) ||
                    isCompanionAudioOfBase(name) ||
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
