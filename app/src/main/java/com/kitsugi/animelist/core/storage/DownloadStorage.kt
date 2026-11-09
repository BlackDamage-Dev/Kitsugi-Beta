package com.kitsugi.animelist.core.storage

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.StringRes
import androidx.work.WorkManager
import com.kitsugi.animelist.R
import com.kitsugi.animelist.core.download.AnimeDownloadService
import com.kitsugi.animelist.core.player.audio.AudioExportWorker
import com.kitsugi.animelist.data.local.AnimeDownloadManager
import com.kitsugi.animelist.data.model.AnimeDownload
import com.kitsugi.animelist.utils.KitsugiImageDownloadHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Kullanıcının **kendi indirdiği dosyaların** (video / görsel / altyazı) depolama bekçisi.
 *
 * ## Neden ayrı bir nesne?
 * [StorageJanitor] yalnızca **yeniden üretilebilir önbellekleri** yönetir ve
 * `cleanAll` çağrısı "Güvenli önbellekleri temizle" düğmesine bağlıdır; oraya indirilen
 * video/görsel/alt yazı eklenirse kullanıcı tek dokunuşla çevrimdışı arşivini kaybederdi.
 * Bu yüzden indirilen dosyalar ayrı tutulur, yalnızca **açık kullanıcı onayıyla** silinir
 * ([clean] / [cleanAll]) ve hiçbir otomatik bakım kuralı bunlara dokunmaz.
 *
 * ## Ölçülen konumlar
 * ```
 * Download/Kitsugi/Video/<mediaId>/video.mp4 + subs (srt) + metadata.json   (uygulama indirmeleri)
 * Download/Kitsugi/Video/<Başlık>_Bölüm_N.mp4                              (dışa aktarılan kopya)
 * Download/Kitsugi/Subtitles (srt)                                         (dışa aktarılan altyazılar)
 * cacheDir/Kitsugi_downloads/…                                            (eski konum)
 * Download/Kitsugi/Images (jpg) · Pictures/Kitsugi/Images (jpg)           ([KitsugiImageDownloadHelper])
 * ```
 *
 * ## Güvenlik kuralları
 *  - Tüm işler `Dispatchers.IO` üzerinde; her dosya işlemi `runCatching` ile korunur,
 *    tek bir dosya hatası toplu silmeyi düşürmez.
 *  - Silinen video dosyaları `MediaScanner` üzerinden sistemden de düşürülür (hayalet
 *    kayıt kalmaz), indirme kayıtları ([AnimeDownloadManager]) aynı anda temizlenir.
 *  - Hiçbir işlem ana iş parçacığında çalışmaz.
 */
object DownloadStorage {

    private const val TAG = "DownloadStorage"

    /** Video indirme köklerinin eski (legacy) kopyası — `OfflinePlaybackHelper` ile aynı ad. */
    private const val LEGACY_DOWNLOADS_DIR = "Kitsugi_downloads"

    private const val PUBLIC_KITSUGI_DIR = "Kitsugi"

    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "avi", "webm", "ts", "m2ts", "mov", "m4v", "flv", "wmv", "partial", "part",
        "m4a", "aac", "mp3", "ac3", "eac3", "ec3", "opus", "ogg", "flac"
    )

    private val SUBTITLE_EXTENSIONS = setOf("srt", "vtt", "ass", "ssa", "sub")

    /**
     * `Download/Kitsugi/Audio` (ve ses klasörleri) altında desteklenen tüm ses uzantıları.
     * `AudioDemuxer` Opus/Vorbis parçalarını `.webm` konteynerine yazdığı için ses klasörleri
     * içinde `webm` / `weba` da ses dosyası olarak kabul edilir.
     */
    val AUDIO_EXTENSIONS: Set<String> = setOf(
        "mp3", "m4a", "mp4a", "m4b", "m4r", "aac", "flac", "wav", "wave",
        "ogg", "oga", "opus", "spx", "webm", "weba", "mka",
        "ac3", "eac3", "ec3", "ac4", "dts", "dtshd", "thd", "mlp",
        "amr", "awb", "3ga", "wma", "asf", "ape", "wv", "mpc", "tta", "tak",
        "alac", "aiff", "aif", "aifc", "caf", "pcm", "dsf", "dff",
        "mp2", "mp1", "mpa", "mid", "midi"
    )

    /** Video klasörü gibi karma dizinlerde video ile karışmayacak saf ses uzantıları. */
    val AUDIO_ONLY_EXTENSIONS: Set<String> = AUDIO_EXTENSIONS - setOf("webm", "asf")

    data class MediaStoreAudioEntry(
        val contentUri: Uri,
        val displayName: String,
        val relativePath: String?,
        val sizeBytes: Long,
        val dateModifiedMillis: Long,
        val durationMs: Long,
        val mimeType: String?
    )

    /** Kullanıcıya gösterilen indirme türü. */
    enum class Kind(
        @StringRes val labelRes: Int,
        @StringRes val descriptionRes: Int
    ) {
        VIDEOS(R.string.storage_downloads_videos, R.string.storage_downloads_videos_desc),
        AUDIOS(R.string.storage_downloads_audios, R.string.storage_downloads_audios_desc),
        IMAGES(R.string.storage_downloads_images, R.string.storage_downloads_images_desc),
        SUBTITLES(R.string.storage_downloads_subtitles, R.string.storage_downloads_subtitles_desc),
    }

    data class Usage(
        val kind: Kind,
        val bytes: Long,
        val fileCount: Int
    )

    data class CleanResult(
        val freedBytes: Long,
        val removedFiles: Int,
        val failures: Int,
        /** Silme engellendi/hata verdi ise kullanıcıya gösterilecek açıklama. */
        val message: String? = null
    )

    /**
     * Tek bir dosya silme sonucu (İndirilenler ekranındaki tekli silmeler).
     *
     * @param deleted     Dosya(lar) diskten gerçekten kalktı mı?
     * @param message     Başarısızlık nedeni (kullanıcıya gösterilir).
     * @param consentUris Android 11+ sistem onayı gereken MediaStore kayıtları.
     */
    data class SingleDeleteResult(
        val deleted: Boolean,
        val message: String? = null,
        val consentUris: List<Uri> = emptyList()
    )

    // ─── Ölçüm ─────────────────────────────────────────────────────────────────

    /** Dört indirme türünü de ölçer (dosya sayısı + bayt). */
    suspend fun scan(context: Context): List<Usage> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        Kind.entries.map { kind ->
            when (kind) {
                Kind.VIDEOS -> usageOf(kind, filesIn(videoRoots(appContext), VIDEO_EXTENSIONS))
                Kind.AUDIOS -> {
                    val (bytes, count) = measureAudios(appContext)
                    Usage(kind, bytes, count)
                }
                Kind.SUBTITLES -> usageOf(kind, filesIn(subtitleRoots(appContext), SUBTITLE_EXTENSIONS))
                Kind.IMAGES -> {
                    val stats = KitsugiImageDownloadHelper.downloadedImagesStats(appContext)
                    Usage(kind, stats.bytes, stats.fileCount)
                }
            }
        }
    }

    /**
     * Bölüm başlığındaki "İndirilen dosyalar: X" için **tekrarsız** toplam.
     *
     * Video klasörlerindeki altyazılar hem [Kind.VIDEOS] hem [Kind.SUBTITLES] tarafından
     * sayıldığı için satırların toplamı gerçek disk kullanımından büyük çıkar; bu fonksiyon
     * her dosyayı yalnızca bir kez sayar.
     */
    suspend fun totalBytes(context: Context): Long = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val seen = HashSet<String>()
        val seenNamesInPublicAudio = HashSet<String>()
        var total = 0L
        (videoRoots(appContext) + subtitleRoots(appContext)).forEach { root ->
            walkFiles(root).forEach { file ->
                if (seen.add(file.absolutePath)) total += file.length()
            }
        }
        audioRoots(appContext).forEach { root ->
            walkFiles(root).forEach { file ->
                if (file.extension.lowercase() in AUDIO_EXTENSIONS && seen.add(file.absolutePath)) {
                    total += file.length()
                    seenNamesInPublicAudio += file.name.lowercase()
                }
            }
        }
        scanMediaStoreAudios(appContext).forEach { entry ->
            if (seenNamesInPublicAudio.add(entry.displayName.lowercase())) {
                total += entry.sizeBytes.coerceAtLeast(0L)
            }
        }
        KitsugiImageDownloadHelper.downloadedImageFiles(appContext).forEach { file ->
            if (seen.add(file.absolutePath)) total += runCatching { file.length() }.getOrDefault(0L)
        }
        total
    }

    // ─── Silme (yalnızca açık kullanıcı onayıyla) ──────────────────────────────

    /** Tek bir türü tamamen siler. */
    suspend fun clean(context: Context, kind: Kind): CleanResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        try {
            when (kind) {
                Kind.VIDEOS -> cleanVideos(appContext)
                Kind.AUDIOS -> cleanAudios(appContext)
                Kind.SUBTITLES -> cleanSubtitles(appContext)
                Kind.IMAGES -> {
                    val summary = KitsugiImageDownloadHelper.deleteAllDownloadedImages(appContext)
                    CleanResult(summary.freedBytes, summary.deletedFiles, summary.failed)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "İndirme silme hatası (${kind.name}): ${t.message}")
            CleanResult(0L, 0, 0, t.message)
        }
    }

    /** Videoları, görselleri ve altyazıları birlikte siler. */
    suspend fun cleanAll(context: Context): CleanResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        var freed = 0L
        var removed = 0
        var failures = 0
        var message: String? = null
        Kind.entries.forEach { kind ->
            val result = clean(appContext, kind)
            freed += result.freedBytes
            removed += result.removedFiles
            failures += result.failures
            if (message == null) message = result.message
        }
        CleanResult(freed, removed, failures, message)
    }

    // ─── Video silme ───────────────────────────────────────────────────────────

    /**
     * İndirilen tüm videoları siler.
     *
     * `<mediaId>/` klasörleri (video + subs + metadata) bütün olarak, kök dizinde düz duran
     * dışa aktarılmış kopyalar dosya olarak silinir. Ardından indirme kayıtları temizlenir —
     * böylece İndirilenler ekranı boşalır ve servis çalışan indirmeyi iptal eder.
     */
    private fun cleanVideos(context: Context): CleanResult {
        // Devam eden indirme varsa önce durdur: dosyalar silinirken yazma sürmesin.
        stopDownloaderIfActive(context)

        var removed = 0
        var freed = 0L
        var failures = 0
        val deletedPaths = ArrayList<String>()

        videoRoots(context).forEach { root ->
            val children = runCatching { root.listFiles() }.getOrNull() ?: return@forEach
            for (child in children) {
                if (child.isDirectory) {
                    // Yalnızca içinde video bulunan klasörler veya .staging_ çalışma klasörleri silinir.
                    if (!child.name.startsWith(".staging_") && !containsVideo(child)) continue
                    val stats = StorageJanitor.dirStats(child)
                    val ok = runCatching { child.deleteRecursively() }.getOrDefault(false)
                    if (ok && !child.exists()) {
                        removed += stats.second
                        freed += stats.first
                    } else {
                        failures++
                    }
                } else if (child.isFile && (
                        child.extension.lowercase() in VIDEO_EXTENSIONS ||
                            child.extension.lowercase() == "json"
                        )
                ) {
                    val size = child.length()
                    if (deleteFile(child)) {
                        removed++
                        freed += size
                        deletedPaths += child.absolutePath
                    } else {
                        failures++
                    }
                }
            }
        }

        if (deletedPaths.isNotEmpty()) notifyMediaRemoved(context, deletedPaths)
        // Kayıtlar (SharedPreferences + StateFlow) temizlenir; servis de durdurulur.
        runCatching { AnimeDownloadManager.clearAllDownloads(context) }

        Log.i(TAG, "İndirilen videolar silindi: $removed dosya, ${freed / 1024} KB (hatalı: $failures)")
        return CleanResult(freed, removed, failures)
    }

    // ─── Tekli silme (İndirilenler ekranı) ────────────────────────────────────

    /**
     * **Tek bir** indirilmiş altyazıyı siler.
     *
     * Altyazılar Android 10+ üzerinde `MediaStore.Downloads` ile
     * `Download/Kitsugi/Subtitles` klasörüne yazıldığı için `File.delete()` sessizce
     * başarısız oluyordu (Scoped Storage). Burada:
     *  1. Aynı ada sahip **tüm** kopyalar bulunur (indirilenler, video klasörü, uygulama
     *     dış klasörü ve `cacheDir/subtitles`),
     *  2. Her kopya önce `File.delete()` ile, olmazsa MediaStore kaydı (ad + göreli klasör)
     *     üzerinden `ContentResolver.delete` ile silinir,
     *  3. Sonuç doğrulanır ([SingleDeleteResult.deleted]) — böylece arayüz "sildim" diyip
     *     listeyi boşuna tazelemez.
     */
    suspend fun deleteSubtitle(context: Context, file: File): SingleDeleteResult =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val fileName = file.name
            val extension = file.extension.lowercase()
            if (fileName.isBlank() || extension !in SUBTITLE_EXTENSIONS) {
                return@withContext SingleDeleteResult(
                    deleted = false,
                    message = appContext.getString(R.string.image_file_delete_failed)
                )
            }

            val copies = subtitleCopies(appContext, fileName)
            var removedAny = false
            var failure: String? = null
            var consentUris = emptyList<Uri>()
            val deletedPaths = ArrayList<String>()

            copies.forEach { copy ->
                if (!copy.exists()) return@forEach
                val size = runCatching { copy.length() }.getOrDefault(0L)
                // 1) Doğrudan dosya silme (uygulama klasörleri + eski Android sürümleri).
                val direct = runCatching { copy.delete() && !copy.exists() }.getOrDefault(false)
                if (direct) {
                    // Doğrudan silme sonrası MediaStore'da hayalet satır kalmasın.
                    runCatching {
                        MediaStoreFileDeleter.deleteByName(
                            context = appContext,
                            fileName = fileName,
                            mode = MediaStoreFileDeleter.Mode.SUBTITLE,
                            relativePath = MediaStoreFileDeleter.relativePathOf(copy)
                        )
                    }
                    removedAny = true
                    deletedPaths += copy.absolutePath
                    Log.d(TAG, "Altyazı silindi ($size B): ${copy.absolutePath}")
                    return@forEach
                }
                // 2) MediaStore kaydı üzerinden silme (Scoped Storage).
                val outcome = MediaStoreFileDeleter.deleteByName(
                    context = appContext,
                    fileName = fileName,
                    mode = MediaStoreFileDeleter.Mode.SUBTITLE,
                    relativePath = MediaStoreFileDeleter.relativePathOf(copy)
                )
                if (outcome.deleted) {
                    removedAny = true
                    deletedPaths += copy.absolutePath
                }
                if (outcome.foreignUris.isNotEmpty()) {
                    consentUris = (consentUris + outcome.foreignUris).distinct()
                }
                if (copy.exists()) {
                    failure = outcome.message
                        ?: appContext.getString(R.string.image_delete_failed_storage, copy.name)
                }
            }

            if (deletedPaths.isNotEmpty()) notifyMediaRemoved(appContext, deletedPaths)

            val stillExists = subtitleCopies(appContext, fileName).any { it.exists() }
            when {
                !stillExists -> SingleDeleteResult(deleted = true)
                consentUris.isNotEmpty() -> SingleDeleteResult(
                    deleted = false,
                    message = failure ?: appContext.getString(R.string.image_file_delete_foreign),
                    consentUris = consentUris
                )
                removedAny -> SingleDeleteResult(deleted = true)
                else -> SingleDeleteResult(
                    deleted = false,
                    message = failure ?: appContext.getString(R.string.image_delete_file_failed, fileName)
                )
            }
        }

    /**
     * Detay sayfasındaki küçük videolardan (klip) **tek birini** siler.
     *
     * Klip dosyaları `Download/Kitsugi/Video/Klipler` altında tek dosyadır; ancak eski
     * sürümlerden kalan kopyalar (farklı uzantı, `.part` kalıntısı, uygulama özel klasörü)
     * da olabileceği için tüm video kökleri **özyinelemeli** taranır ve taban ada uyan
     * (`<taban>.*`) her dosya silinir.
     *
     * Silme iki aşamalıdır: doğrudan `File.delete()` başarısız olursa (Scoped Storage'da
     * MediaStore sahipliği farklıysa sessizce `false` döner) kayıt
     * [MediaStoreFileDeleter] üzerinden kaldırılır. Hâlâ silinemiyorsa sistem onayı
     * gereken URI'ler döner; UI bunu `createDeleteRequest` ile kullanıcıya sorar.
     */
    suspend fun deleteClip(
        context: Context,
        baseName: String?,
        knownPath: String? = null
    ): SingleDeleteResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val base = baseName?.trim().orEmpty()

        val targets = LinkedHashMap<String, File>()
        videoRoots(appContext).forEach { root ->
            walkFiles(root).forEach { file ->
                if (!file.isFile) return@forEach
                val matchesBase = base.isNotBlank() &&
                    file.name.startsWith("$base.", ignoreCase = true)
                val matchesKnown = !knownPath.isNullOrBlank() && file.absolutePath == knownPath
                if (matchesBase || matchesKnown) targets[file.absolutePath] = file
            }
        }

        // Dosya zaten yoksa bu bir hata değildir: liste tazelenip kart kaybolur.
        if (targets.isEmpty()) return@withContext SingleDeleteResult(deleted = true)

        var removedAny = false
        var failure: String? = null
        var consentUris = emptyList<Uri>()
        val deletedPaths = ArrayList<String>()

        targets.values.forEach { file ->
            val direct = runCatching { file.delete() && !file.exists() }.getOrDefault(false)
            if (direct) {
                removedAny = true
                deletedPaths += file.absolutePath
                return@forEach
            }
            val outcome = runCatching {
                MediaStoreFileDeleter.deleteByName(
                    context = appContext,
                    fileName = file.name,
                    mode = MediaStoreFileDeleter.Mode.ANY,
                    relativePath = MediaStoreFileDeleter.relativePathOf(file)
                )
            }.getOrNull()
            if (outcome != null) {
                if (outcome.deleted || !file.exists()) {
                    removedAny = true
                    deletedPaths += file.absolutePath
                }
                if (outcome.foreignUris.isNotEmpty()) {
                    consentUris = (consentUris + outcome.foreignUris).distinct()
                }
                outcome.message?.let { failure = it }
            }
        }

        if (deletedPaths.isNotEmpty()) notifyMediaRemoved(appContext, deletedPaths)

        val stillExists = targets.values.any { it.exists() }
        when {
            !stillExists -> SingleDeleteResult(deleted = true)
            consentUris.isNotEmpty() -> SingleDeleteResult(
                deleted = false,
                message = failure ?: appContext.getString(R.string.image_file_delete_foreign),
                consentUris = consentUris
            )
            removedAny -> SingleDeleteResult(deleted = true)
            else -> SingleDeleteResult(
                deleted = false,
                message = failure
                    ?: appContext.getString(R.string.kitsugi_clip_delete_failed, base)
            )
        }
    }

    /**
     * Aynı ada sahip altyazı kopyaları: tüm video/altyazı kökleri + `cacheDir/subtitles`.
     * (`SUB`/`ASS` gibi dosyalar kullanıcı isteğiyle yalnızca o ada sahip kopyalardan silinir.)
     */
    private fun subtitleCopies(context: Context, fileName: String): List<File> = buildList {
        subtitleRoots(context).forEach { root ->
            walkFiles(root).forEach { f -> if (f.name == fileName) add(f) }
        }
        add(File(File(context.cacheDir, "subtitles"), fileName))
    }.distinctBy { it.absolutePath }.filter { it.exists() }

    // ─── Altyazı silme ─────────────────────────────────────────────────────────

    /**
     * İndirilen altyazı dosyalarını siler; video dosyalarına **dokunmaz**.
     * Ardından boşalan `subs/` klasörleri kaldırılır.
     */
    private fun cleanSubtitles(context: Context): CleanResult {
        var removed = 0
        var freed = 0L
        var failures = 0

        filesIn(subtitleRoots(context), SUBTITLE_EXTENSIONS).forEach { file ->
            val size = file.length()
            val direct = deleteFile(file)
            val deleted = direct || MediaStoreFileDeleter
                .deleteByName(
                    context = context,
                    fileName = file.name,
                    mode = MediaStoreFileDeleter.Mode.SUBTITLE,
                    relativePath = MediaStoreFileDeleter.relativePathOf(file)
                )
                .deleted
            if (deleted) {
                removed++
                freed += size
            } else {
                failures++
            }
        }

        // Boşalan "subs" klasörlerini temizle (video klasörleri kalsın).
        videoRoots(context).forEach { root ->
            val children = runCatching { root.listFiles() }.getOrNull() ?: return@forEach
            children.filter { it.isDirectory }.forEach { mediaDir ->
                val subsDir = File(mediaDir, "subs")
                if (subsDir.isDirectory && (runCatching { subsDir.listFiles() }?.getOrNull()?.isEmpty() != false)) {
                    runCatching { subsDir.delete() }
                }
            }
        }

        Log.i(TAG, "İndirilen altyazılar silindi: $removed dosya, ${freed / 1024} KB (hatalı: $failures)")
        return CleanResult(freed, removed, failures)
    }

    // ─── Ses dosyaları ölçüm ve silme ──────────────────────────────────────────

    /**
     * İndirilen ses dosyalarının bulunduğu tüm dizinleri döner (`Download/Kitsugi/Audio`,
     * uygulama özel `Kitsugi/Audio` ve varsa `Music/Kitsugi/Audio`).
     */
    fun audioRoots(context: Context): List<File> = buildList {
        add(File(publicKitsugiDir(), "Audio"))
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let {
            add(File(it, "$PUBLIC_KITSUGI_DIR/Audio"))
        }
        val publicMusic = runCatching {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        }.getOrNull()
        if (publicMusic != null) {
            add(File(publicMusic, "$PUBLIC_KITSUGI_DIR/Audio"))
            add(File(publicMusic, PUBLIC_KITSUGI_DIR))
        }
        context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.let {
            add(File(it, "$PUBLIC_KITSUGI_DIR/Audio"))
            add(File(it, PUBLIC_KITSUGI_DIR))
        }
    }.distinctBy { it.absolutePath }.filter { it.exists() }

    /**
     * Android 10+ Scoped Storage üzerinde `MediaStore.Downloads` ve `MediaStore.Audio`
     * koleksiyonlarındaki `Kitsugi/Audio` kayıtlarını okur. Böylece FUSE üzerinden
     * `File.listFiles()` çağrısında gizlenen (eski kurulum veya harici yönetici kaynaklı)
     * ses dosyaları da eksiksiz bulunur.
     */
    fun scanMediaStoreAudios(context: Context): List<MediaStoreAudioEntry> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
        val appContext = context.applicationContext
        val byKey = LinkedHashMap<String, MediaStoreAudioEntry>()
        val collections = listOf(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        )
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.IS_PENDING
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%Kitsugi/Audio%")
        val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"

        for (collection in collections) {
            runCatching {
                appContext.contentResolver.query(
                    collection,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val relIdx = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    val sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    val modIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                    val durIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
                    val mimeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                    val pendingIdx = cursor.getColumnIndex(MediaStore.MediaColumns.IS_PENDING)

                    while (cursor.moveToNext()) {
                        if (pendingIdx >= 0 && cursor.getInt(pendingIdx) != 0) continue
                        val displayName = (if (nameIdx >= 0) cursor.getString(nameIdx) else null)
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                            ?: continue
                        val ext = displayName.substringAfterLast('.', "").lowercase()
                        val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) else null
                        if (ext !in AUDIO_EXTENSIONS && mime?.startsWith("audio/") != true) continue

                        val id = cursor.getLong(idIdx)
                        val rel = if (relIdx >= 0) cursor.getString(relIdx)?.trimEnd('/') else null
                        val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx).coerceAtLeast(0L) else 0L
                        val modSec = if (modIdx >= 0) cursor.getLong(modIdx) else 0L
                        val durMs = if (durIdx >= 0) cursor.getLong(durIdx).coerceAtLeast(0L) else 0L
                        val key = "${rel.orEmpty().lowercase()}/${displayName.lowercase()}"
                        val existing = byKey[key]
                        if (existing == null || (existing.durationMs <= 0L && durMs > 0L)) {
                            byKey[key] = MediaStoreAudioEntry(
                                contentUri = ContentUris.withAppendedId(collection, id),
                                displayName = displayName,
                                relativePath = rel,
                                sizeBytes = if (size > 0L) size else (existing?.sizeBytes ?: 0L),
                                dateModifiedMillis = if (modSec > 0L) modSec * 1000L else (existing?.dateModifiedMillis ?: 0L),
                                durationMs = if (durMs > 0L) durMs else (existing?.durationMs ?: 0L),
                                mimeType = mime ?: existing?.mimeType
                            )
                        }
                    }
                }
            }.onFailure {
                Log.w(TAG, "MediaStore ses taraması başarısız ($collection): ${it.message}")
            }
        }
        return byKey.values.toList()
    }

    private fun measureAudios(context: Context): Pair<Long, Int> {
        val files = filesIn(audioRoots(context), AUDIO_EXTENSIONS)
        val seenNames = HashSet<String>()
        var bytes = 0L
        var count = 0
        files.forEach { file ->
            bytes += runCatching { file.length() }.getOrDefault(0L)
            count++
            seenNames += file.name.lowercase()
        }
        scanMediaStoreAudios(context).forEach { entry ->
            if (seenNames.add(entry.displayName.lowercase())) {
                bytes += entry.sizeBytes.coerceAtLeast(0L)
                count++
            }
        }
        return bytes to count
    }

    /**
     * **Tek bir** indirilmiş ses dosyasını diskten ve `MediaStore` üzerinden siler.
     */
    suspend fun deleteAudio(
        context: Context,
        file: File,
        contentUri: Uri? = null
    ): SingleDeleteResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val fileName = file.name
        val extension = file.extension.lowercase()
        if (fileName.isBlank() || (extension !in AUDIO_EXTENSIONS && contentUri == null)) {
            return@withContext SingleDeleteResult(
                deleted = false,
                message = appContext.getString(R.string.image_file_delete_failed)
            )
        }

        val copies = audioCopies(appContext, fileName, file)
        var removedAny = false
        var failure: String? = null
        val consentSet = LinkedHashSet<Uri>()
        val deletedPaths = ArrayList<String>()

        // Varsa doğrudan bilinen contentUri üzerinden de silmeyi dene.
        if (contentUri != null) {
            runCatching {
                if (appContext.contentResolver.delete(contentUri, null, null) > 0) {
                    removedAny = true
                }
            }.onFailure { throwable ->
                if (throwable is SecurityException) {
                    consentSet += contentUri
                } else {
                    failure = throwable.localizedMessage
                }
            }
        }

        copies.forEach { copy ->
            if (!copy.exists()) return@forEach
            val direct = runCatching { copy.delete() && !copy.exists() }.getOrDefault(false)
            if (direct) {
                runCatching {
                    MediaStoreFileDeleter.deleteByName(
                        context = appContext,
                        fileName = fileName,
                        mode = MediaStoreFileDeleter.Mode.AUDIO,
                        relativePath = MediaStoreFileDeleter.relativePathOf(copy)
                    )
                }
                removedAny = true
                deletedPaths += copy.absolutePath
                return@forEach
            }
            val outcome = MediaStoreFileDeleter.deleteByName(
                context = appContext,
                fileName = fileName,
                mode = MediaStoreFileDeleter.Mode.AUDIO,
                relativePath = MediaStoreFileDeleter.relativePathOf(copy)
            )
            if (outcome.deleted || !copy.exists()) {
                removedAny = true
                deletedPaths += copy.absolutePath
            }
            consentSet += outcome.foreignUris
            if (copy.exists() && outcome.message != null) {
                failure = outcome.message
            }
        }

        if (copies.isEmpty() && !removedAny) {
            val outcome = MediaStoreFileDeleter.deleteByName(
                context = appContext,
                fileName = fileName,
                mode = MediaStoreFileDeleter.Mode.AUDIO,
                relativePath = MediaStoreFileDeleter.relativePathOf(file)
            )
            if (outcome.deleted) removedAny = true
            consentSet += outcome.foreignUris
            if (outcome.message != null) failure = outcome.message
        }

        if (deletedPaths.isNotEmpty()) notifyMediaRemoved(appContext, deletedPaths)

        val stillExistsOnDisk = audioCopies(appContext, fileName, file).any { it.exists() }
        when {
            !stillExistsOnDisk && (removedAny || copies.isEmpty() && consentSet.isEmpty()) ->
                SingleDeleteResult(deleted = true)
            consentSet.isNotEmpty() -> SingleDeleteResult(
                deleted = false,
                message = failure ?: appContext.getString(R.string.image_file_delete_foreign),
                consentUris = consentSet.toList()
            )
            removedAny -> SingleDeleteResult(deleted = true)
            else -> SingleDeleteResult(
                deleted = false,
                message = failure ?: appContext.getString(R.string.image_delete_file_failed, fileName)
            )
        }
    }

    private fun audioCopies(context: Context, fileName: String, original: File): List<File> = buildList {
        if (original.exists()) add(original)
        audioRoots(context).forEach { root ->
            walkFiles(root).forEach { f -> if (f.name == fileName) add(f) }
        }
    }.distinctBy { it.absolutePath }.filter { it.exists() }

    /**
     * İndirilen tüm ses dosyalarını siler; video ve altyazı dosyalarına dokunmaz.
     */
    private fun cleanAudios(context: Context): CleanResult {
        runCatching { WorkManager.getInstance(context).cancelAllWorkByTag(AudioExportWorker.WORK_TAG) }

        var removed = 0
        var freed = 0L
        var failures = 0
        val deletedPaths = ArrayList<String>()
        val deletedNames = HashSet<String>()

        filesIn(audioRoots(context), AUDIO_EXTENSIONS).forEach { file ->
            val size = runCatching { file.length() }.getOrDefault(0L)
            val direct = deleteFile(file)
            val outcome = MediaStoreFileDeleter.deleteByName(
                context = context,
                fileName = file.name,
                mode = MediaStoreFileDeleter.Mode.AUDIO,
                relativePath = MediaStoreFileDeleter.relativePathOf(file)
            )
            if (direct || outcome.deleted || !file.exists()) {
                removed++
                freed += size
                deletedPaths += file.absolutePath
                deletedNames += file.name.lowercase()
            } else {
                failures++
            }
        }

        scanMediaStoreAudios(context).forEach { entry ->
            if (entry.displayName.lowercase() in deletedNames) return@forEach
            val ok = runCatching {
                context.contentResolver.delete(entry.contentUri, null, null) > 0
            }.getOrDefault(false) || MediaStoreFileDeleter.deleteByName(
                context = context,
                fileName = entry.displayName,
                mode = MediaStoreFileDeleter.Mode.AUDIO,
                relativePath = entry.relativePath
            ).deleted
            if (ok) {
                removed++
                freed += entry.sizeBytes.coerceAtLeast(0L)
            } else {
                failures++
            }
        }

        if (deletedPaths.isNotEmpty()) notifyMediaRemoved(context, deletedPaths)
        Log.i(TAG, "İndirilen sesler silindi: $removed dosya, ${freed / 1024} KB (hatalı: $failures)")
        return CleanResult(freed, removed, failures)
    }

    // ─── Konum çözümlemesi ─────────────────────────────────────────────────────

    /** `Download/Kitsugi` — görsel/altyazı/video dışa aktarımlarının ortak üst klasörü. */
    private fun publicKitsugiDir(): File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        PUBLIC_KITSUGI_DIR
    )

    /**
     * Video indirmelerinin tutulduğu tüm konumlar.
     * `OfflinePlaybackHelper.getDownloadsDir` sırasıyla birincil (public), uygulama dış
     * klasörü ve `filesDir` yedeğini kullanır; burada üçü birlikte taranır ki kullanıcının
     * diskinde kalan eski kopyalar da görünür ve silinebilir olsun.
     * (Not: `getDownloadsDir` klasörü gerektiğinde oluşturur; yalnızca tarama yapan bu ekran
     * için aynı yollar **oluşturmadan** hesaplanır.)
     */
    private fun videoRoots(context: Context): List<File> = buildList {
        add(File(publicKitsugiDir(), "Video"))
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let {
            add(File(it, "$PUBLIC_KITSUGI_DIR/Video"))
        }
        add(File(context.filesDir, LEGACY_DOWNLOADS_DIR))
        add(File(context.cacheDir, LEGACY_DOWNLOADS_DIR)) // eski sürümlerin konumu
    }.distinctBy { it.absolutePath }.filter { it.exists() }

    /** Altyazı konumları: video kökleri (yanında dışa aktarılanlar dahil) + ortak altyazı klasörü. */
    private fun subtitleRoots(context: Context): List<File> = buildList {
        addAll(videoRoots(context))
        add(File(publicKitsugiDir(), "Subtitles"))
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let {
            add(File(it, "$PUBLIC_KITSUGI_DIR/Subtitles"))
        }
    }.distinctBy { it.absolutePath }.filter { it.exists() }

    // ─── Yardımcılar ───────────────────────────────────────────────────────────

    private fun usageOf(kind: Kind, files: List<File>): Usage =
        Usage(kind, files.sumOf { runCatching { it.length() }.getOrDefault(0L) }, files.size)

    /** Verilen köklerde (özyinelemeli) [extensions] uzantılı dosyaları toplar. */
    private fun filesIn(roots: List<File>, extensions: Set<String>): List<File> =
        roots.flatMap { walkFiles(it) }.filter { it.isFile && it.extension.lowercase() in extensions }

    /** Dizin ağacındaki tüm dosyaları (özyinelemeli) döner; okunamayan klasörler atlanır. */
    private fun walkFiles(root: File): List<File> {
        if (!root.exists()) return emptyList()
        val files = ArrayList<File>()
        val stack = ArrayDeque<File>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val children = runCatching { current.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                if (child.isDirectory) stack.addLast(child) else files += child
            }
        }
        return files
    }

    private fun containsVideo(dir: File): Boolean =
        walkFiles(dir).any { it.extension.lowercase() in VIDEO_EXTENSIONS }

    private fun deleteFile(file: File): Boolean =
        runCatching { file.delete() && !file.exists() }.getOrDefault(false)

    /** Silinen medya dosyalarını sistem kayıtlarından düşürür (hayalet giriş kalmasın). */
    private fun notifyMediaRemoved(context: Context, paths: List<String>) {
        runCatching {
            MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { _, _ -> }
        }.onFailure { Log.w(TAG, "MediaScanner temizliği başarısız: ${it.message}") }
    }

    /** Devam eden (sırada bekleyen dahil) indirme var mı? */
    private fun hasActiveDownloads(): Boolean =
        AnimeDownloadManager.downloads.value.any {
            it.status == AnimeDownload.Status.DOWNLOADING || it.status == AnimeDownload.Status.QUEUE
        }

    /**
     * Devam eden indirme varsa servisi durdurur. Kayıtlar [AnimeDownloadManager.clearAllDownloads]
     * ile temizlendiği için servis içindeki izleyici aktif işi de iptal eder.
     */
    private fun stopDownloaderIfActive(context: Context) {
        if (!hasActiveDownloads()) return
        runCatching { context.stopService(Intent(context, AnimeDownloadService::class.java)) }
    }
}
