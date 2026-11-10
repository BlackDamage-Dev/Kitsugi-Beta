package com.kitsugi.animelist.data.local

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Okunan manga / novel bölümlerinin geçmiş yazıcısı.
 *
 * ## Tasarım
 * - **Ateşle-ve-unut**: okuyucu her sayfa değişiminde çağırır; yazma IO dispatcher'ında
 *   yapılır ve çağıran akışı asla bloklamaz. Disk/DB hatası yutulur (okuma deneyimi bozulmaz).
 * - **Serileştirilmiş yazma**: aynı anda gelen kayıtlar [Mutex] ile sıraya girer; böylece
 *   hızlı sayfa geçişlerinde satır sayısı şişmez, son durum kazanır.
 * - **Sınırlı büyüme**: tablo [MAX_ENTRIES] kayıttan fazlasını tutmaz (izleme geçmişiyle aynı
 *   mantık: en yeni 300 kayıt).
 *
 * Kayıt anahtarı `mangaUrl|chapterUrl` olduğu için aynı bölümü tekrar okumak yeni satır
 * açmaz; yalnızca `updatedAt` yenilenir ve bölüm listenin başına geçer.
 */
object MangaReadHistoryManager {

    /** İzleme geçmişindeki 200 sınırıyla uyumlu, biraz daha geniş bir üst sınır. */
    const val MAX_ENTRIES = 300

    private lateinit var dao: MangaReadHistoryDao
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    /** Kaç yazma yapıldı — her [TRIM_EVERY] yazmada bir tablo sınırı uygulanır. */
    private var writeCount = 0
    private const val TRIM_EVERY = 25

    fun init(context: Context) {
        if (::dao.isInitialized) return
        dao = KitsugiDatabase.getDatabase(context.applicationContext).mangaReadHistoryDao()
    }

    /**
     * Bir bölümün okuma durumunu geçmişe yazar (varsa günceller).
     *
     * Çağıran akışı asla bloklamaz: yazma IO dispatcher'ında, mutex ile sıraya sokulmuş
     * şekilde yapılır. Hata durumunda sessizce yutulur (okuma deneyimi bozulmaz).
     */
    fun record(
        sourceName: String,
        mangaUrl: String,
        mangaTitle: String,
        thumbnailUrl: String?,
        chapterUrl: String,
        chapterName: String,
        lastPageIndex: Int,
        totalPages: Int,
        isCompleted: Boolean
    ) {
        if (!::dao.isInitialized) return
        if (mangaUrl.isBlank() || chapterUrl.isBlank()) return
        val entry = MangaReadHistoryEntity(
            id = MangaReadHistoryEntity.key(mangaUrl, chapterUrl),
            sourceName = sourceName,
            mangaUrl = mangaUrl,
            mangaTitle = mangaTitle,
            thumbnailUrl = thumbnailUrl,
            chapterUrl = chapterUrl,
            chapterName = chapterName,
            lastPageIndex = lastPageIndex.coerceAtLeast(0),
            totalPages = totalPages.coerceAtLeast(0),
            isCompleted = isCompleted,
            updatedAt = System.currentTimeMillis()
        )
        runCatching {
            ioScope.launch {
                writeMutex.withLock {
                    dao.upsert(entry)
                    // Şişmeyi engelle: her yazmada COUNT/limit maliyeti olmasın diye
                    // yalnızca TRIM_EVERY yazmada bir sınır uygulanır.
                    writeCount += 1
                    if (writeCount % TRIM_EVERY == 0) {
                        dao.trimTo(MAX_ENTRIES)
                    }
                }
            }
        }
    }

    /** Tek bir mangaya ait okuma geçmişini siler. */
    fun removeForManga(mangaUrl: String) {
        if (!::dao.isInitialized) return
        runCatching { ioScope.launch { dao.removeForManga(mangaUrl) } }
    }

    /** Tüm okuma geçmişini siler. */
    fun clearAll() {
        if (!::dao.isInitialized) return
        runCatching { ioScope.launch { dao.clearAll() } }
    }
}
