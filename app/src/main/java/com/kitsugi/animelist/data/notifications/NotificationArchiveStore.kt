package com.kitsugi.animelist.data.notifications

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.data.account.KitsugiAccountRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.File

/**
 * Bildirim arşivi — kaynak tarafında tutulmayan bildirimlerin yerel deposu.
 *
 * MyAnimeList, Simkl, Kitsu ve Bangumi gibi platformların resmî API'lerinde
 * kişisel bildirim ucu YOKTUR; bu kaynakların "bildirimleri" Kitsugi tarafında
 * (yayın takvimi + izleme listesi eşleşmesi) üretilir ve kaynağın sunucusunda
 * hiçbir zaman saklanmaz. Kullanıcı bu kaynağa sonradan baktığında 7 günlük
 * pencereden düşen bildirimler kayboluyordu.
 *
 * Bu depo o bildirimleri cihazda kalıcı olarak tutar:
 *  • Dosya: `filesDir/notifications_archive.json`
 *  • Yapı:  kaynak adı → arşiv kaydı listesi (kaynak başına [MAX_PER_SOURCE] kayıt)
 *  • Birleştirme: aynı `id` için en yeni kayıt kazanır; liste `tsMs`'ye göre
 *    yeni → eski sıralanır.
 *
 * Buluta yedekleme ayrı bir katmandır: [com.kitsugi.animelist.data.account.KitsugiAccountRepository]
 * bu dosyanın içeriğini `user_data` tablosundaki `notifications` anahtarıyla
 * eşitler (giriş yapılmışsa).
 */
object NotificationArchiveStore {

    private const val TAG = "NotifArchive"
    private const val FILE_NAME = "notifications_archive.json"
    private const val MAX_PER_SOURCE = 300

    private val json = Json { ignoreUnknownKeys = true }
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private val mutex = Mutex()

    /**
     * Arşivlenen tekil bildirim. Alanlar [KitsugiNotificationsViewModel.NotifItem]
     * ile birebir örtüşür (sadece `isUnread`/`userId` gibi anlık UI alanları hariç).
     */
    @Serializable
    data class ArchivedNotif(
        val id: String,
        val source: String = "",
        val title: String = "",
        val body: String = "",
        val dateText: String? = null,
        /** Sıralama ve birleştirme için epoch ms; yoksa kayıtların sonunda kalır. */
        val tsMs: Long? = null,
        val imageUrl: String? = null,
        val mediaId: Int? = null,
        val mediaType: String? = null,
        /**
         * Medyanın gerçek adı (kategori adı olmayan). Arşivden gelen kayıtlar da doğru
         * başlıkla detay açabilsin diye saklanır; eski arşiv dosyalarında bulunmaz.
         */
        val mediaTitle: String? = null,
        val typeIdentifier: String? = null,
        val categoryName: String? = null
    )

    @Serializable
    internal data class ArchiveFile(
        val version: Int = 1,
        val sources: Map<String, List<ArchivedNotif>> = emptyMap()
    )

    private fun archiveFile(ctx: Context): File =
        File(ctx.applicationContext.filesDir, FILE_NAME)

    /** Dosyayı okur; bozuk/eksik dosya boş arşiv olarak ele alınır. */
    fun loadAll(ctx: Context): Map<String, List<ArchivedNotif>> {
        return try {
            val file = archiveFile(ctx)
            if (!file.exists()) return emptyMap()
            val text = file.readText()
            if (text.isBlank()) return emptyMap()
            val parsed = json.decodeFromString(ArchiveFile.serializer(), text)
            parsed.sources
        } catch (e: Exception) {
            Log.w(TAG, "Arşiv okunamadı (boş kabul edildi): ${e.message}")
            emptyMap()
        }
    }

    fun loadSource(ctx: Context, source: String): List<ArchivedNotif> =
        loadAll(ctx)[source].orEmpty()

    /**
     * Bir kaynağın yeni canlı listesini arşive birleştirir (arka planda).
     * Çağran tarafı sonucunu beklemez; akışta gecikme yaratmaz.
     *
     * @param retainOldWhere Verilirse eski arşiv kayıtlarından yalnızca koşulu sağlayanlar
     *   korunur (ör. gerçek MAL web akışı aktifken eski takvim yedek kayıtlarının temizlenmesi).
     */
    fun mergeSourceAsync(
        ctx: Context,
        source: String,
        fresh: List<ArchivedNotif>,
        retainOldWhere: ((ArchivedNotif) -> Boolean)? = null
    ) {
        if (fresh.isEmpty() && retainOldWhere == null) return
        val owner = KitsugiAccountRepository.currentUserId()
        ioScope.launch {
            try {
                mutex.withLock { mergeLocked(ctx, source, fresh, retainOldWhere) }
                // The previous caller launched an upload before this asynchronous write
                // finished, repeatedly backing up the PREVIOUS archive instead.
                KitsugiAccountRepository.onNotificationArchiveChanged(ctx, owner)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Arşiv birleştirilemedi ($source): ${e.javaClass.simpleName}")
            }
        }
    }

    /** Awaitable variant for delivery pipelines that must not acknowledge a baseline early. */
    suspend fun mergeSource(
        ctx: Context,
        source: String,
        fresh: List<ArchivedNotif>,
        retainOldWhere: ((ArchivedNotif) -> Boolean)? = null
    ): List<ArchivedNotif> {
        if (fresh.isEmpty() && retainOldWhere == null) return loadSource(ctx, source)
        val owner = KitsugiAccountRepository.currentUserId()
        val merged = mutex.withLock {
            mergeLocked(ctx, source, fresh, retainOldWhere)
        }
        KitsugiAccountRepository.onNotificationArchiveChanged(ctx, owner)
        return merged
    }

    /** Suspend instead of runBlocking: account synchronization never blocks the UI thread. */
    suspend fun mergeSources(
        ctx: Context,
        entries: Map<String, List<ArchivedNotif>>
    ): Map<String, List<ArchivedNotif>> = mutex.withLock {
        val current = readLocked(ctx)
        val mergedSources = current.sources.toMutableMap()
        entries.forEach { (source, fresh) ->
            mergedSources[source] = mergeLists(mergedSources[source].orEmpty(), fresh)
        }
        if (entries.isNotEmpty()) writeLocked(ctx, ArchiveFile(version = 1, sources = mergedSources))
        mergedSources
    }

    /** Kaynağın arşivini sıfırlar (test/geliştirme için; arayüzden çağrılmaz). */
    fun clearSource(ctx: Context, source: String) {
        ioScope.launch {
            mutex.withLock {
                val current = readLocked(ctx)
                writeLocked(ctx, current.copy(sources = current.sources - source))
            }
        }
    }

    // ── Dosya içi yardımcıları (mutex ile çağrılır) ──────────────────────────

    private suspend fun mergeLocked(
        ctx: Context,
        source: String,
        fresh: List<ArchivedNotif>,
        retainOldWhere: ((ArchivedNotif) -> Boolean)? = null
    ): List<ArchivedNotif> {
        val current = readLocked(ctx)
        val existing = current.sources[source].orEmpty()
        val filteredExisting = if (retainOldWhere != null) existing.filter(retainOldWhere) else existing
        val merged = mergeLists(filteredExisting, fresh)
        writeLocked(ctx, current.copy(sources = current.sources + (source to merged)))
        return merged
    }

    private fun readLocked(ctx: Context): ArchiveFile {
        val file = archiveFile(ctx)
        if (!file.exists()) return ArchiveFile()
        return try {
            val text = file.readText()
            if (text.isBlank()) ArchiveFile() else json.decodeFromString(ArchiveFile.serializer(), text)
        } catch (e: Exception) {
            // Never overwrite the cloud or the only local copy with an empty archive
            // just because decoding failed. UI-only loadAll may still show an empty list.
            throw IllegalStateException("Yerel bildirim arşivi okunamadı; mevcut yedek korundu.", e)
        }
    }

    private fun writeLocked(ctx: Context, archive: ArchiveFile) {
        val file = archiveFile(ctx)
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(ArchiveFile.serializer(), archive))
        check(tmp.renameTo(file)) { "Bildirim arşivi kaydedilemedi; önceki dosya korundu." }
    }

    /**
     * Birleştirme kuralı: `id` anahtarında tekilleştir; aynı id için
     * [fresh] (yani en yeni çekim) kazanır. `tsMs`'ye göre yeni → eski sırala,
     * kaynaktan [MAX_PER_SOURCE] kayıtla sınırla.
     */
    internal fun mergeLists(
        old: List<ArchivedNotif>,
        fresh: List<ArchivedNotif>
    ): List<ArchivedNotif> {
        val byId = LinkedHashMap<String, ArchivedNotif>()
        old.forEach { byId[it.id] = it }
        fresh.forEach { byId[it.id] = it }
        return byId.values
            .sortedWith(
                compareByDescending<ArchivedNotif> { it.tsMs ?: Long.MIN_VALUE }
            )
            .take(MAX_PER_SOURCE)
    }

    // ── JSON ↔ bulut aktarımı (KitsugiAccountRepository kullanır) ────────────

    /**
     * Buluta yazılacak zarf — [ArchiveFile] ile aynı yapıda (sürüm alanı zorunlu
     * tutulmadı; eski kayıtlar da okunabilsin diye decode tarafı toleranslı).
     */
    @Serializable
    internal data class CloudArchive(
        val sources: Map<String, List<ArchivedNotif>> = emptyMap()
    )

    /** Yerel arşivi buluta yazılabilir JSON öğesine çevirir. */
    suspend fun toJsonElement(ctx: Context): JsonElement = mutex.withLock {
        json.encodeToJsonElement(CloudArchive.serializer(), CloudArchive(readLocked(ctx).sources))
    }

    /** Strict cloud decoding: a corrupt response must fail sync, not erase the backup. */
    fun fromCloudJsonElement(element: JsonElement?): Map<String, List<ArchivedNotif>> {
        if (element == null) return emptyMap()
        require(element is JsonObject && (element.isEmpty() || "sources" in element)) {
            "Buluttaki bildirim arşivi tanınamadı; mevcut yedek korundu."
        }
        return json.decodeFromJsonElement(CloudArchive.serializer(), element).sources
    }

    /** Buluttan gelen JSON öğesini arşiv kayıtlarına çözer; bozuk veri boş harita. */
    fun fromJsonElement(element: JsonElement?): Map<String, List<ArchivedNotif>> {
        if (element == null) return emptyMap()
        return try {
            json.decodeFromJsonElement(CloudArchive.serializer(), element).sources
        } catch (e: Exception) {
            Log.w(TAG, "Bulut arşivi çözümlenemedi: ${e.message}")
            emptyMap()
        }
    }
}
