package com.kitsugi.animelist.data.local

import com.kitsugi.animelist.data.auth.runSyncCatching
import android.content.Context
import com.kitsugi.animelist.data.auth.ExternalListSyncManager
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import com.kitsugi.animelist.data.auth.AniListGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Bekleyen (başarısız) sync işlemlerini kuyruğa yazar ve boşaltır.
 *
 * Akış:
 *  1. Sync başarısız → [enqueue] ile kuyruğa yaz
 *  2. Uygulama açılışı / her başarılı işlem sonrası → [drain] çağır
 *  3. [drain]: kuyruktaki her işlemi dene, başarılıysa sil, değilse retry++ yap
 *  4. retryCount >= MAX_RETRIES olanları silmeden beklet
 */
object PendingSyncDrainer {

    private const val MAX_RETRIES = 5
    private val drainMutex = Mutex()

    // ── Kota nedeniyle ertelenen kuyruk için kendiliğinden yeniden deneme ──
    private val resumeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val resumeGuard = Any()
    private var resumeJob: Job? = null

    /** Planlanan yeniden deneme zamanı (epoch ms). 0 = plan yok. */
    private var resumeAt: Long = 0L

    /**
     * AniList kota cezası (429) bittiğinde kuyruğu **kendiliğinden** boşaltır.
     *
     * Eskiden bekleme kuyruğu yalnızca uygulama açılışında, ağ geri geldiğinde veya bir
     * sonraki başarılı eşitlemede deneniyordu. 60 sn'lik ceza penceresi bu tetiklemelerin
     * hiçbirine denk gelmediği için kullanıcı aynı düğmeye tekrar tekrar basıyor, her
     * denemede aynı "kota doldu" mesajını görüyordu. Artık ceza bitince iş kendisi döner.
     */
    fun scheduleResume(context: Context, dao: PendingSyncDao, delayMs: Long) {
        val appContext = context.applicationContext ?: context
        val wait = delayMs.coerceIn(1_000L, 120_000L)
        val due = System.currentTimeMillis() + wait
        synchronized(resumeGuard) {
            val existing = resumeJob
            // Zaten daha GEÇ bir zamana planlanmış bir iş varsa onu koru; yeni plan ondan
            // önce biteceği için ek bir iş açmanın anlamı yok.
            if (existing != null && existing.isActive && resumeAt > due) return

            // DİKKAT: `drain()` kendi resume işinin içinden de çağırabilir (kota nedeniyle
            // yarıda kaldığında). O durumda `existing` şu an çalışan iştir; iptal etmek
            // güvenlidir çünkü drain bu noktadan sonra askıya alınmıyor ve kilit `finally`
            // içinde bırakılıyor. Eski kod "iş aktif" diye erken dönüyordu → kuyruk bir
            // daha asla kendiliğinden denenmiyordu.
            existing?.cancel()
            resumeAt = due
            resumeJob = resumeScope.launch {
                delay(wait)
                synchronized(resumeGuard) { if (resumeAt == due) resumeAt = 0L }
                runSyncCatching { drain(appContext, dao, freshBudget = false) }
            }
        }
    }

    // ────────────────────────────────────────────────
    // Kuyruğa Ekleme
    // ────────────────────────────────────────────────

    suspend fun enqueue(
        dao: PendingSyncDao,
        operation: String,
        entry: MediaEntry
    ) = withContext(Dispatchers.IO) {
        drainMutex.withLock {
            val older = dao.getAll().filter { pending ->
                runSyncCatching {
                    val previous = JSONObject(pending.entryJson)
                    entry.id > 0 && previous.optInt("id", 0) == entry.id &&
                        previous.optString("source") == entry.source && previous.optString("type") == entry.type.name
                }.getOrDefault(false)
            }
            // Insert first: a failed insert must not destroy the previous recoverable job.
            dao.insert(PendingSyncEntity(operation = operation, entryJson = entry.toJson()))
            older.forEach { dao.deleteById(it.id) }
        }
    }

    // ────────────────────────────────────────────────
    // Kuyruğu Boşalt
    // ────────────────────────────────────────────────

    /**
     * Kuyruktaki tüm bekleyen işlemleri dener.
     * Başarılı olanları siler, başarısız olanların retryCount'unu artırır.
     * MAX_RETRIES'a ulaşan kayıtlar oturum içinde silmeden bekletilir;
     * [freshBudget] verildiğinde (bağlantı geri geldiğinde) sayaçlar sıfırlanır ve
     * işler yeniden tam deneme hakkına kavuşur — kullanıcı niyeti asla donmaz.
     *
     * @return Başarıyla gönderilen işlem sayısı
     */
    suspend fun drain(
        context: Context,
        dao: PendingSyncDao,
        freshBudget: Boolean = false
    ): Int = withContext(Dispatchers.IO) {
        // Exhausted jobs remain inspectable/recoverable; never silently delete user intent.
        if (!drainMutex.tryLock()) return@withContext 0
        try {
        if (freshBudget) dao.resetRetries()
        val pending = dao.getAll().filter { it.retryCount < MAX_RETRIES }
        if (pending.isEmpty()) return@withContext 0

        var successCount = 0

        for (item in pending) {
            // Kota cezası sürüyorsa kalan işleri denemek cezayı uzatmaktan başka işe yaramaz.
            val cooldown = AniListGate.cooldownRemainingMs()
            if (cooldown > 3_000L) {
                scheduleResume(context, dao, cooldown + 2_000L)
                break
            }

            val entry = runSyncCatching { item.entryJson.toMediaEntry() }.getOrNull()
            if (entry == null) {
                // Preserve malformed jobs for recovery rather than destroying them.
                dao.incrementRetry(item.id)
                continue
            }

            val result = runSyncCatching {
                when (item.operation) {
                    "UPDATE" -> ExternalListSyncManager.syncEntry(context, entry)
                    "DELETE" -> ExternalListSyncManager.deleteEntry(context, entry)
                    else -> null
                }
            }

            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            val syncResult = result.getOrNull()
            // Yalnızca gerçek hata yeniden denemeyi haklı çıkarır; "kimlik çözülemedi"
            // (atlandı) durumları kuyrukta sonsuza kadar dönmez.
            val success = result.isSuccess && syncResult != null && syncResult.errors.isEmpty() &&
                (syncResult.messages.isNotEmpty() || syncResult.skipped.isNotEmpty())

            if (success) {
                dao.deleteById(item.id)
                successCount++
            } else {
                val allErrors = syncResult?.errors.orEmpty().joinToString(" | ")
                val isAuthError = allErrors.contains("401") || allErrors.contains("token geçersiz") || allErrors.contains("Unauthorized")
                val isQuota = AniListGate.isQuotaMessage(allErrors) || AniListGate.isCoolingDown()
                // Kota kaynaklı başarısızlık "kullanıcı hatası" değil: deneme bütçesini
                // yakmadan işi kuyrukta tutup ceza bitince yeniden dene.
                if (!isAuthError && !isQuota) {
                    dao.incrementRetry(item.id)
                }
                if (isQuota) {
                    scheduleResume(context, dao, AniListGate.cooldownRemainingMs() + 2_000L)
                    break
                }
            }
        }

        successCount
        } finally {
            drainMutex.unlock()
        }
    }

    // ────────────────────────────────────────────────
    // JSON Serializasyon / Deserializasyon
    // ────────────────────────────────────────────────

    private fun MediaEntry.toJson(): String = JSONObject(MediaEntryBackup.exportToJson(listOf(this)))
        .getJSONArray("entries").getJSONObject(0).put("id", id).toString()

    private fun String.toMediaEntry(): MediaEntry {
        val item = JSONObject(this)
        val backup = JSONObject().put("schemaVersion", 2)
            .put("entries", org.json.JSONArray().put(item))
        return MediaEntryBackup.importFromJson(backup.toString()).single().copy(id = item.optInt("id", 0))
    }
}
