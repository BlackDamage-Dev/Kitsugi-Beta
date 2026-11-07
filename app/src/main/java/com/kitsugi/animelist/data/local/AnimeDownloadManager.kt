package com.kitsugi.animelist.data.local

import android.content.Context
import android.content.Intent
import android.os.Build
import com.kitsugi.animelist.R
import com.kitsugi.animelist.core.download.ClipDownloadSource
import com.kitsugi.animelist.core.download.DownloadExportNaming
import com.kitsugi.animelist.data.model.AnimeDownload
import com.kitsugi.animelist.data.model.DownloadKind
import com.kitsugi.animelist.data.model.ExternalAudioTrack
import com.kitsugi.animelist.utils.KitsugiStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

object AnimeDownloadManager {
    private lateinit var store: AnimeDownloadStore
    private val _downloads = MutableStateFlow<List<AnimeDownload>>(emptyList())
    val downloads: StateFlow<List<AnimeDownload>> = _downloads.asStateFlow()

    /** Diskteki listeyi ana iş parçacığını bloklamadan okumak için (Application.onCreate). */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var localWriteBeforeLoad = false

    @Volatile
    private var lastProgressEmitTimeMs = 0L

    fun init(context: Context) {
        val appContext = context.applicationContext
        store = AnimeDownloadStore(appContext)
        // PERF: Önceden bu satır Application.onCreate içinde (ana iş parçacığında) Gson ile
        // tüm indirme listesini ayrıştırıyordu; liste büyüdükçe soğuk açılış uzuyordu.
        ioScope.launch {
            val loaded = store.getDownloads().map { rawItem ->
                val download = rawItem.normalized()
                if (download.status == AnimeDownload.Status.DOWNLOADING) {
                    download.copy(status = AnimeDownload.Status.PAUSED, progress = 0)
                } else {
                    download
                }
            }.map { download ->
                // Klipler tek dosyadır: dosya kullanıcı tarafından (dosya yöneticisi, temizlik
                // uygulaması) silinmişse kart sonsuza kadar "İndirildi" görünmesin. Kaydı
                // yeniden denenebilir duruma alıyoruz.
                if (download.isClip && download.status == AnimeDownload.Status.COMPLETED) {
                    val file = download.localPath?.let { File(it) }
                    if (file == null || !file.isFile || file.length() <= 0L) {
                        download.copy(
                            status = AnimeDownload.Status.ERROR,
                            progress = 0,
                            localPath = null,
                            errorMessage = KitsugiStrings.get(R.string.kitsugi_clip_error_missing_file)
                        )
                    } else download
                } else download
            }.map { download ->
                // Hiçbir indirme süreci yokken QUEUE'da kalan klip, "sırada" diye asılı kalırdı;
                // yeniden denenebilir (PAUSED) duruma alınır.
                if (download.isClip && download.status == AnimeDownload.Status.QUEUE) {
                    download.copy(status = AnimeDownload.Status.PAUSED)
                } else download
            }

            // Çökme/kapanma sırasında kalan yarım dosyalar (`.part`) temizlenir.
            // Süreç yeni açıldığı için hiçbir indirme bu dosyaları yazmıyor olabilir.
            runCatching {
                DownloadExportNaming.clipDir(appContext, create = false)
                    .listFiles()
                    ?.filter { it.isFile && it.name.endsWith(".part", ignoreCase = true) }
                    ?.forEach { it.delete() }
            }

            _downloads.value = if (localWriteBeforeLoad) {
                // Okuma sürerken yeni indirme eklendiyse eskisini ezmesin: birleştir.
                val seen = _downloads.value.mapTo(HashSet()) { it.uniqueKey }
                _downloads.value + loaded.filterNot { seen.contains(it.uniqueKey) }
            } else {
                loaded
            }
        }
    }

    // ── Küçük video (klip) indirmeleri ────────────────────────────────────────

    /**
     * Detay sayfasındaki bir video için tekil indirme anahtarı.
     *
     * Anahtar **yalnızca videonun kimliğinden** üretilir (YouTube video id'si ya da sorgu
     * parametreleri atılmış URL). Böylece aynı video:
     *  - farklı bağlantı biçimleriyle (`watch?v=`, `youtu.be/…`, `&t=`),
     *  - farklı sekmelerde/ekranlarda (TMDB / Simkl / arama / liste),
     *  - başlığı değişmiş olsa bile (yerelleştirilmiş ad)
     * aynı anahtara düşer ve **ikinci kez indirilmez**.
     */
    fun clipKeyFor(videoUrl: String): String =
        "clip|${ClipDownloadSource.identityKey(videoUrl)}"

    /** Bu video için var olan indirme kaydı (yoksa null). */
    fun findClip(clipKey: String): AnimeDownload? =
        _downloads.value.firstOrNull { it.isClip && it.clipKey == clipKey }

    /**
     * Detay sayfasındaki küçük videoyu indirme sırasına ekler.
     *
     * Tekilleştirme üç katmanlıdır:
     *  1. Aynı video zaten tamamlandıysa **hiçbir şey yapılmaz** (var olan kayıt döner).
     *  2. Aynı video kuyrukta/indiriliyorsa ikinci kayıt **oluşturulmaz**.
     *  3. Hata/duraklama sonrası tekrar istendiğinde aynı kayıt yeniden kuyruğa alınır.
     *
     * @return eklenecek/güncellenecek kayıt; bağlantı geçersizse `null`
     */
    fun addClipDownload(
        context: Context,
        mediaTitle: String,
        posterUrl: String?,
        clipTitle: String,
        videoPageUrl: String
    ): AnimeDownload? {
        val url = videoPageUrl.trim()
        if (url.isBlank()) return null
        localWriteBeforeLoad = true
        val key = clipKeyFor(url)
        val current = _downloads.value
        val existing = current.firstOrNull { it.isClip && it.clipKey == key }

        if (existing != null) {
            val completedFile = existing.localPath?.let { File(it) }
            val alreadyOnDisk = existing.status == AnimeDownload.Status.COMPLETED &&
                completedFile != null && completedFile.isFile && completedFile.length() > 0L
            if (alreadyOnDisk) return existing
            if (existing.status == AnimeDownload.Status.QUEUE ||
                existing.status == AnimeDownload.Status.DOWNLOADING
            ) {
                return existing
            }
            val requeued = existing.copy(
                status = AnimeDownload.Status.QUEUE,
                progress = 0,
                downloadedBytes = 0,
                totalBytes = 0,
                downloadedSegments = 0,
                errorMessage = null,
                localPath = null
            )
            saveAndEmit(current.map {
                if (it.animeId == existing.animeId && it.episode == existing.episode) requeued else it
            })
            startService(context)
            return requeued
        }

        val baseName = uniqueClipBaseName(context, mediaTitle, clipTitle)
        val hash = Integer.toHexString(key.hashCode())
        var animeId = "clip_$hash"
        var suffix = 2
        while (current.any { it.animeId == animeId && it.episode == 0 }) {
            animeId = "clip_${hash}_$suffix"
            suffix++
        }

        val record = AnimeDownload(
            animeId = animeId,
            animeTitle = mediaTitle.trim().ifBlank { "Kitsugi" },
            posterUrl = posterUrl,
            episode = 0,
            season = 0,
            url = url,
            quality = KitsugiStrings.get(R.string.kitsugi_clip_label),
            kind = AnimeDownload.Kind.CLIP,
            clipKey = key,
            clipTitle = clipTitle.trim().ifBlank { "Video" },
            clipBaseName = baseName,
            status = AnimeDownload.Status.QUEUE
        )
        saveAndEmit(current + record)
        startService(context)
        return record
    }

    /**
     * Dosya adı çakışmasını önler: aynı başlığa sahip farklı videolar `_1`, `_2`… ile ayrılır.
     * Disk üzerinde aynı taban adda dosya varsa da bir sonraki sıra seçilir → üzerine yazma yok.
     */
    private fun uniqueClipBaseName(context: Context, mediaTitle: String, clipTitle: String): String {
        val dir = runCatching { DownloadExportNaming.clipDir(context, create = false) }.getOrNull()
        val used = _downloads.value.mapNotNull { it.clipBaseName }.toMutableSet()
        // Klasör listesi yerine birkaç `exists()` kontrolü: ana iş parçacığında liste taraması
        // yapmadan çakışma tespiti (tipik durumda ilk denemede boş çıkar).
        val knownExts = arrayOf("mp4", "ts", "webm", "m4v", "mkv", "mov")
        var index = 1
        while (index < 200) {
            val candidate = DownloadExportNaming.clipBaseName(mediaTitle, clipTitle, index)
            val onDisk = dir != null && knownExts.any { ext ->
                File(dir, "$candidate.$ext").exists() || File(dir, "$candidate.$ext.part").exists()
            }
            if (!onDisk && !used.contains(candidate)) return candidate
            index++
        }
        return DownloadExportNaming.clipBaseName(mediaTitle, clipTitle, 999)
    }

    /**
     * Bir klip indirmesini kaydıyla birlikte siler ve **sonucu döner**.
     *
     * İndirilenler ekranı bu sonucu kullanarak başarı/hata/sistem onayı mesajlarını
     * gösterebilir; `deleteDownload` gibi "ateşle ve unut" davranmaz.
     */
    suspend fun deleteClipRecord(
        context: Context,
        record: AnimeDownload
    ): com.kitsugi.animelist.core.storage.DownloadStorage.SingleDeleteResult {
        saveAndEmit(
            _downloads.value.filterNot {
                it.animeId == record.animeId && it.episode == record.episode
            }
        )
        return runCatching {
            com.kitsugi.animelist.core.storage.DownloadStorage.deleteClip(
                context = context.applicationContext,
                baseName = record.clipBaseName,
                knownPath = record.localPath
            )
        }.getOrElse { throwable ->
            com.kitsugi.animelist.core.storage.DownloadStorage.SingleDeleteResult(
                deleted = false,
                message = throwable.message
            )
        }
    }

    /** Kullanıcı indirmeyi iptal etti: kayıt kalır (yeniden denenebilir), yarım dosya silinir. */
    fun cancelClipDownload(clipKey: String) {
        val current = _downloads.value
        val record = current.firstOrNull { it.isClip && it.clipKey == clipKey } ?: return
        if (record.status == AnimeDownload.Status.COMPLETED) return
        val cancelled = record.copy(
            status = AnimeDownload.Status.PAUSED,
            progress = 0,
            downloadedBytes = 0,
            totalBytes = 0,
            downloadedSegments = 0,
            errorMessage = null
        )
        saveAndEmit(current.map {
            if (it.animeId == record.animeId && it.episode == record.episode) cancelled else it
        })
        // Aktif indirme servisi, kayıt artık DOWNLOADING/QUEUE olmadığı için kendini iptal eder.
    }

    private fun saveAndEmit(list: List<AnimeDownload>) {
        // Disk okuması hâlâ sürüyorsa, gelen eski liste bu yerel değişikliğin üzerine yazmasın.
        localWriteBeforeLoad = true
        _downloads.value = list
        if (::store.isInitialized) {
            ioScope.launch {
                runCatching { store.saveDownloads(list) }
            }
        }
    }

    fun addDownload(
        context: Context,
        animeId: String,
        animeTitle: String,
        posterUrl: String?,
        episode: Int,
        season: Int,
        url: String,
        quality: String,
        requestHeaders: Map<String, String> = emptyMap(),
        subtitles: List<com.kitsugi.animelist.core.player.SubtitleInput> = emptyList(),
        malId: Int? = null,
        aniListId: Int? = null,
        tmdbId: Int? = null,
        source: String? = null,
        streamTitle: String? = null,
        streamName: String? = null,
        externalAudioUrl: String? = null,
        externalAudioTracks: List<ExternalAudioTrack> = emptyList(),
        isCloudStream: Boolean = false,
        qualityValue: Int? = null,
        p2pInfoHash: String? = null,
        p2pFileIdx: Int? = null,
        p2pTrackers: List<String> = emptyList()
    ) {
        val newDownload = AnimeDownload(
            animeId = animeId,
            animeTitle = animeTitle,
            posterUrl = posterUrl,
            episode = episode,
            season = season,
            url = url,
            quality = quality,
            requestHeaders = requestHeaders,
            subtitles = subtitles,
            status = AnimeDownload.Status.QUEUE,
            malId = malId,
            aniListId = aniListId,
            tmdbId = tmdbId,
            source = source,
            streamTitle = streamTitle,
            streamName = streamName,
            downloadKind = DownloadKind.VIDEO,
            externalAudioUrl = externalAudioUrl ?: externalAudioTracks.firstOrNull()?.url,
            externalAudioTracks = externalAudioTracks,
            isCloudStream = isCloudStream,
            qualityValue = qualityValue,
            p2pInfoHash = p2pInfoHash,
            p2pFileIdx = p2pFileIdx,
            p2pTrackers = p2pTrackers
        )
        enqueueDownload(context, newDownload)
    }

    fun addAudioDownload(
        context: Context,
        animeId: String,
        animeTitle: String,
        posterUrl: String?,
        episode: Int,
        season: Int,
        url: String,
        requestHeaders: Map<String, String> = emptyMap(),
        source: String? = null,
        streamTitle: String? = null,
        streamName: String? = null,
        externalAudioTracks: List<ExternalAudioTrack> = emptyList(),
        audioTrackKey: String = "default",
        audioTrackLabel: String = "",
        audioTrackLang: String = "",
        audioTrackIndex: Int = -1,
        isCloudStream: Boolean = false,
        qualityValue: Int? = null,
        p2pInfoHash: String? = null,
        p2pFileIdx: Int? = null,
        p2pTrackers: List<String> = emptyList()
    ): Boolean {
        val newDownload = AnimeDownload(
            animeId = animeId,
            animeTitle = animeTitle,
            posterUrl = posterUrl,
            episode = episode,
            season = season,
            url = url,
            quality = audioTrackLabel.ifBlank { audioTrackLang.uppercase().ifBlank { "AUDIO" } },
            requestHeaders = requestHeaders,
            subtitles = emptyList(),
            status = AnimeDownload.Status.QUEUE,
            source = source,
            streamTitle = streamTitle,
            streamName = streamName,
            downloadKind = DownloadKind.AUDIO,
            externalAudioUrl = externalAudioTracks.firstOrNull()?.url,
            externalAudioTracks = externalAudioTracks,
            audioTrackKey = audioTrackKey,
            audioTrackLabel = audioTrackLabel,
            audioTrackLang = audioTrackLang,
            audioTrackIndex = audioTrackIndex,
            isCloudStream = isCloudStream,
            qualityValue = qualityValue,
            p2pInfoHash = p2pInfoHash,
            p2pFileIdx = p2pFileIdx,
            p2pTrackers = p2pTrackers
        )
        return enqueueDownload(context, newDownload)
    }

    fun enqueueDownload(context: Context, newDownload: AnimeDownload): Boolean {
        localWriteBeforeLoad = true
        val current = _downloads.value.toMutableList()
        val key = newDownload.uniqueKey
        val existingIdx = current.indexOfFirst { it.uniqueKey == key }
        if (existingIdx != -1) {
            val existing = current[existingIdx]
            if (existing.status == AnimeDownload.Status.DOWNLOADING ||
                existing.status == AnimeDownload.Status.QUEUE
            ) {
                return false
            }
            current[existingIdx] = newDownload.copy(
                status = AnimeDownload.Status.QUEUE,
                progress = 0,
                downloadedBytes = 0L,
                errorMessage = null
            )
            saveAndEmit(current)
            startService(context)
            return true
        }
        current.add(newDownload)
        saveAndEmit(current)
        startService(context)
        return true
    }

    fun pauseByKey(uniqueKey: String) {
        val current = _downloads.value.map {
            if (it.uniqueKey == uniqueKey) it.copy(status = AnimeDownload.Status.PAUSED) else it
        }
        saveAndEmit(current)
    }

    fun pauseDownload(animeId: String, episode: Int) {
        pauseByKey(AnimeDownload.videoKey(animeId, episode))
    }

    fun resumeByKey(context: Context, uniqueKey: String) {
        val current = _downloads.value.map {
            if (it.uniqueKey == uniqueKey) {
                it.copy(status = AnimeDownload.Status.QUEUE, errorMessage = null, progress = 0)
            } else it
        }
        saveAndEmit(current)
        startService(context)
    }

    fun resumeDownload(context: Context, animeId: String, episode: Int) {
        resumeByKey(context, AnimeDownload.videoKey(animeId, episode))
    }

    fun deleteByKey(context: Context, uniqueKey: String) {
        val record = _downloads.value.firstOrNull { it.uniqueKey == uniqueKey }
        val current = _downloads.value.filterNot { it.uniqueKey == uniqueKey }
        saveAndEmit(current)
        if (record == null) return

        val appContext = context.applicationContext

        if (record.isClip) {
            ioScope.launch {
                runCatching {
                    com.kitsugi.animelist.core.storage.DownloadStorage.deleteClip(
                        context = appContext,
                        baseName = record.clipBaseName,
                        knownPath = record.localPath
                    )
                }
            }
            return
        }

        ioScope.launch {
            if (record.isAudioOnly) {
                record.localPath?.takeIf { it.isNotBlank() }?.let { path ->
                    runCatching { com.kitsugi.animelist.core.storage.DownloadStorage.deleteAudio(appContext, File(path)) }
                }
            } else {
                runCatching {
                    val mediaId = "${record.animeId}_ep${record.episode}"
                    com.kitsugi.animelist.core.player.OfflinePlaybackHelper.deleteDownload(appContext, mediaId)
                }
                runCatching {
                    com.kitsugi.animelist.core.download.DownloadExportNaming.deleteExports(
                        context = appContext,
                        animeTitle = record.animeTitle,
                        episode = record.episode
                    )
                }
            }
        }
    }

    fun deleteDownload(context: Context, animeId: String, episode: Int) {
        deleteByKey(context, AnimeDownload.videoKey(animeId, episode))
    }

    /** Bağımsız ses indirme kayıtlarını temizler. */
    fun clearAudioDownloads() {
        localWriteBeforeLoad = true
        val remaining = _downloads.value.filterNot { it.isAudioOnly }
        saveAndEmit(remaining)
    }

    /** Video indirme kayıtlarını temizler (Depolama > İndirilen Videoları Sil sonrası). */
    fun clearVideoDownloads() {
        localWriteBeforeLoad = true
        val remaining = _downloads.value.filter { it.isAudioOnly }
        saveAndEmit(remaining)
    }

    fun clearAllDownloads(context: Context) {
        // Disk okuması hâlâ sürüyorsa eski liste geri yazmasın.
        localWriteBeforeLoad = true
        _downloads.value = emptyList()
        if (::store.isInitialized) {
            ioScope.launch {
                runCatching { store.saveDownloads(emptyList()) }
            }
        }
        runCatching {
            context.stopService(
                Intent(context, com.kitsugi.animelist.core.download.AnimeDownloadService::class.java)
            )
        }
    }

    fun updateProgressByKey(
        uniqueKey: String,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        downloadedSegments: Int = 0
    ) {
        val currentList = _downloads.value
        val existing = currentList.firstOrNull { it.uniqueKey == uniqueKey } ?: return
        if (existing.progress == progress &&
            existing.downloadedBytes == downloadedBytes &&
            existing.totalBytes == totalBytes &&
            existing.downloadedSegments == downloadedSegments
        ) {
            return
        }
        val now = android.os.SystemClock.elapsedRealtime()
        if (progress < 100 && existing.progress == progress && now - lastProgressEmitTimeMs < 200L) {
            return
        }
        lastProgressEmitTimeMs = now
        val current = currentList.map {
            if (it.uniqueKey == uniqueKey) {
                it.copy(
                    progress = progress,
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                    downloadedSegments = downloadedSegments
                )
            } else it
        }
        _downloads.value = current
    }

    fun updateProgress(animeId: String, episode: Int, progress: Int, downloadedBytes: Long, totalBytes: Long, downloadedSegments: Int = 0) {
        updateProgressByKey(
            uniqueKey = AnimeDownload.videoKey(animeId, episode),
            progress = progress,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            downloadedSegments = downloadedSegments
        )
    }

    fun updateStatusByKey(
        uniqueKey: String,
        status: AnimeDownload.Status,
        localPath: String? = null,
        errorMessage: String? = null
    ) {
        val current = _downloads.value.map {
            if (it.uniqueKey == uniqueKey) {
                it.copy(
                    status = status,
                    progress = if (status == AnimeDownload.Status.COMPLETED) 100 else it.progress,
                    localPath = localPath ?: it.localPath,
                    errorMessage = errorMessage
                )
            } else it
        }
        saveAndEmit(current)
    }

    fun updateStatus(
        animeId: String,
        episode: Int,
        status: AnimeDownload.Status,
        detail: String? = null
    ) {
        updateStatusByKey(
            uniqueKey = AnimeDownload.videoKey(animeId, episode),
            status = status,
            localPath = if (status == AnimeDownload.Status.COMPLETED) detail else null,
            errorMessage = if (status == AnimeDownload.Status.ERROR) detail else null
        )
    }

    fun getNextDownload(): AnimeDownload? {
        return _downloads.value.firstOrNull { it.status == AnimeDownload.Status.QUEUE }
    }

    /**
     * Kuyrukta bekleyen bir indirmeyi **kuyruk içinde** bir üst/alt sıraya taşır.
     *
     * Neden gerekli? [getNextDownload] sıradaki işi listenin başındaki `QUEUE`
     * kaydından seçer. Dolayısıyla listenin dizilişi = indirme önceliğidir.
     * Burada yalnızca iki `QUEUE` kaydının yerleri değiştirilir; araya giren
     * tamamlanmış/duraklatılmış kayıtların konumu korunur (yarım kalan işler
     * kuyruk sırasını bozmaz).
     *
     * @param direction `-1` yukarı (daha önce indir), `+1` aşağı (daha sonra indir).
     * @return taşıma yapıldıysa `true`; kayıt kuyrukta değilse ya da sınırdaya `false`.
     */
    fun moveQueuedDownload(animeId: String, episode: Int, direction: Int): Boolean {
        if (direction == 0) return false
        val current = _downloads.value.toMutableList()
        val fromIndex = current.indexOfFirst { it.animeId == animeId && it.episode == episode }
        if (fromIndex < 0) return false
        if (current[fromIndex].status != AnimeDownload.Status.QUEUE) return false

        // Hedef: aynı yöndeki ilk QUEUE kaydı.
        var targetIndex = -1
        var i = fromIndex + direction
        while (i in current.indices) {
            if (current[i].status == AnimeDownload.Status.QUEUE) {
                targetIndex = i
                break
            }
            i += direction
        }
        if (targetIndex < 0) return false

        // Yer değiştirme (swap): diğer kayıtların dizilişi bozulmaz, indeks kayması olmaz.
        val moved = current[fromIndex]
        current[fromIndex] = current[targetIndex]
        current[targetIndex] = moved
        saveAndEmit(current)
        return true
    }

    /** Kuyrukta birden fazla bekleyen iş var mı? (Sıralama düğmelerini göstermek için.) */
    fun queuedCount(): Int = _downloads.value.count { it.status == AnimeDownload.Status.QUEUE }

    fun startService(context: Context) {
        val serviceIntent = Intent(context, com.kitsugi.animelist.core.download.AnimeDownloadService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
