package com.kitsugi.animelist.data.local

import android.content.Context
import android.content.Intent
import android.os.Build
import com.kitsugi.animelist.data.model.AnimeDownload
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
        store = AnimeDownloadStore(context.applicationContext)
        // PERF: Önceden bu satır Application.onCreate içinde (ana iş parçacığında) Gson ile
        // tüm indirme listesini ayrıştırıyordu; liste büyüdükçe soğuk açılış uzuyordu.
        ioScope.launch {
            val loaded = store.getDownloads()
            _downloads.value = if (localWriteBeforeLoad) {
                // Okuma sürerken yeni indirme eklendiyse eskisini ezmesin: birleştir.
                val seen = _downloads.value.mapTo(HashSet()) { it.animeId to it.episode }
                _downloads.value + loaded.filterNot { seen.contains(it.animeId to it.episode) }
            } else {
                loaded
            }
        }
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
        streamName: String? = null
    ) {
        localWriteBeforeLoad = true
        val current = _downloads.value.toMutableList()
        val exists = current.any { it.animeId == animeId && it.episode == episode }
        if (exists) return

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
            streamName = streamName
        )
        current.add(newDownload)
        saveAndEmit(current)
        startService(context)
    }

    fun pauseDownload(animeId: String, episode: Int) {
        val current = _downloads.value.map {
            if (it.animeId == animeId && it.episode == episode) {
                it.copy(status = AnimeDownload.Status.PAUSED)
            } else it
        }
        saveAndEmit(current)
    }

    fun resumeDownload(context: Context, animeId: String, episode: Int) {
        val current = _downloads.value.map {
            if (it.animeId == animeId && it.episode == episode) {
                it.copy(status = AnimeDownload.Status.QUEUE)
            } else it
        }
        saveAndEmit(current)
        startService(context)
    }

    fun deleteDownload(context: Context, animeId: String, episode: Int) {
        // Silmeden önce kaydı al: dışa aktarılan (public) kopyaların adı başlıktan üretilir.
        val record = _downloads.value.firstOrNull { it.animeId == animeId && it.episode == episode }
        val current = _downloads.value.filterNot { it.animeId == animeId && it.episode == episode }
        saveAndEmit(current)

        val appContext = context.applicationContext
        ioScope.launch {
            try {
                val mediaId = "${animeId}_ep$episode"
                com.kitsugi.animelist.core.player.OfflinePlaybackHelper.deleteDownload(appContext, mediaId)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Video, altyazı, metadata ve poster (Download/Kitsugi/Video|Subtitles|Images) dahil
            // dışa aktarılan tüm dosyalar temizlenir; aksi hâlde bölüm "silinmiş" görünürken
            // dosya diskte kalıp yer kaplamaya devam ediyordu.
            if (record != null) {
                try {
                    com.kitsugi.animelist.core.download.DownloadExportNaming.deleteExports(
                        context = appContext,
                        animeTitle = record.animeTitle,
                        episode = episode
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Tüm indirme kayıtlarını (diskteki dosyalara dokunmadan) temizler.
     * Ayarlar > Depolama ekranındaki toplu silme sonrası çağrılır: dosyalar gittiği için
     * indirme listesi ve kuyruk da boşalır, devam eden iş varsa servis kendini durdurur.
     */
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

    fun updateProgress(animeId: String, episode: Int, progress: Int, downloadedBytes: Long, totalBytes: Long, downloadedSegments: Int = 0) {
        val currentList = _downloads.value
        val existing = currentList.firstOrNull { it.animeId == animeId && it.episode == episode } ?: return
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
            if (it.animeId == animeId && it.episode == episode) {
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

    fun updateStatus(animeId: String, episode: Int, status: AnimeDownload.Status, localPath: String? = null) {
        val current = _downloads.value.map {
            if (it.animeId == animeId && it.episode == episode) {
                it.copy(
                    status = status,
                    localPath = localPath ?: it.localPath
                )
            } else it
        }
        saveAndEmit(current)
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
