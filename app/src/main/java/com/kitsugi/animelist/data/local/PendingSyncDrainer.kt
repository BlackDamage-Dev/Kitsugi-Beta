package com.kitsugi.animelist.data.local

import com.kitsugi.animelist.data.auth.runSyncCatching
import android.content.Context
import com.kitsugi.animelist.data.auth.ExternalListSyncManager
import com.kitsugi.animelist.data.auth.SyncField
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
 *
 * Karar kuralları (hangi iş düşer, hangi sayaç artar, hangi alanlar birleşir/düşülür)
 * SAF olarak [PendingSyncPolicy] içindedir ve birim testleriyle sabitlenmiştir (T02);
 * bu nesne yalnızca JSON/Room/ağ kablolamasını yapar.
 */
object PendingSyncDrainer {

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

    /**
     * @param fields kuyruğa alınan işin taşıdığı ALANLAR. Yeniden denemede yalnızca bu alanlar
     * yazılır; aksi hâlde çevrimdışıyken kuyruğa giren bir "ilerleme" işi, bağlantı gelince
     * satırın TAMAMINI yazıp hedeflerdeki puan/not/tarihi ezebilirdi.
     */
    suspend fun enqueue(
        dao: PendingSyncDao,
        operation: String,
        entry: MediaEntry,
        fields: Set<SyncField> = SyncField.ALL,
        tmdbFields: com.kitsugi.animelist.data.auth.TmdbWriteFields = com.kitsugi.animelist.data.auth.TmdbWriteFields.All,
        expectedTmdbAccountId: Int? = null
    ) = withContext(Dispatchers.IO) {
        drainMutex.withLock {
            // Aynı satır için bekleyen işler bulunur. Yeni iş eskileri EZER (satır en güncel
            // hâliyle yazılır) — ama ALAN KÜMELERİ BİRLEŞTİRİLİR: aksi hâlde çevrimdışıyken
            // önce "ilerleme", sonra "not" değiştirilirse ikinci iş birincisini siler ve
            // ilerleme güncellemesi sessizce kaybolurdu.
            val all = dao.getAll()
            val older = mutableListOf<PendingSyncEntity>()
            val olderFieldSets = mutableListOf<Set<SyncField>>()
            all.forEach { pending ->
                val sameRow = runSyncCatching {
                    val previous = JSONObject(pending.entryJson)
                    PendingSyncPolicy.sameRow(
                        entryId = entry.id,
                        entrySource = entry.source,
                        entryTypeName = entry.type.name,
                        queuedId = previous.optInt("id", 0),
                        queuedSource = previous.optString("source"),
                        queuedTypeName = previous.optString("type")
                    ).also { matched ->
                        if (matched) olderFieldSets += previous.readFields()
                    }
                }.getOrDefault(false)
                if (sameRow) older += pending
            }
            val mergedFields = PendingSyncPolicy.mergedFields(fields, olderFieldSets)
            // Insert first: a failed insert must not destroy the previous recoverable job.
            dao.insert(PendingSyncEntity(operation = operation, entryJson = entry.toJson(mergedFields, tmdbFields, expectedTmdbAccountId)))
            older.forEach { dao.deleteById(it.id) }

            // ── "Sildim, sonra tekrar ekledim" koruması ────────────────────────────
            // Yerel silme satırı kaldırdığı için yeniden eklenen kayıt YENİ bir yerel id
            // alır; kuyrukta kalan eski DELETE işi yukarıdaki id eşleşmesine takılmaz ve
            // bir sonraki boşaltmada yeni kaydı uzaktan SİLERDİ (kullanıcı: "tekrar
            // ekledim ama yine silindi"). UPDATE kuyruğa girerken aynı yapımı hedefleyen
            // bekleyen DELETE işleri düşürülür.
            if (operation.equals("UPDATE", ignoreCase = true)) {
                all.filter { it.operation.equals("DELETE", ignoreCase = true) }
                    .filter { pending ->
                        runSyncCatching { sameProduction(JSONObject(pending.entryJson), entry) }
                            .getOrDefault(false)
                    }
                    .forEach { dao.deleteById(it.id) }
            }
        }
    }

    /**
     * Doğrudan (çevrimiçi) yazım başarılı olduktan sonra aynı satırın bekleyen işlerinden
     * **yalnızca az önce gönderildiği bilinen alanları** düşer (E73).
     *
     * Eski davranış işi bütün olarak siliyordu: çevrimdışıyken kuyruğa giren bir not
     * değişikliği (`{NOTES}`), çevrimiçiyken yalnız ilerleme (`{PROGRESS}`) içeren
     * başarılı bir düzenlemeden sonra siliniyor ve not hedeflere hiç gitmiyordu.
     * Artık alan kümesi küçültülür; küme boşalırsa iş silinir, kalırsa işin JSON'ındaki
     * `_syncFields` geri kalan alanlarla güncellenir. Anlık görüntü olduğu gibi kalır:
     * geri kalan alanların değerleri arada değişseydi o değişiklik ya doğrudan
     * gönderilmiş (ve buradan düşülmüş) ya da yeni bir [enqueue] ile görüntüyü
     * tazelemiş olurdu.
     *
     * `DELETE` için çağıran [SyncField.ALL] geçirir: başarılı bir doğrudan silmenin
     * ardından bekleyen `DELETE` işi bütünüyle gereksizdir.
     */
    suspend fun removeSyncedFields(
        dao: PendingSyncDao,
        operation: String,
        entry: MediaEntry,
        syncedFields: Set<SyncField>
    ) = withContext(Dispatchers.IO) {
        if (syncedFields.isEmpty()) return@withContext
        drainMutex.withLock {
            dao.getAll().filter { it.operation == operation }.forEach { pending ->
                runSyncCatching {
                    val json = JSONObject(pending.entryJson)
                    val sameRow = PendingSyncPolicy.sameRow(
                        entryId = entry.id,
                        entrySource = entry.source,
                        entryTypeName = entry.type.name,
                        queuedId = json.optInt("id", 0),
                        queuedSource = json.optString("source"),
                        queuedTypeName = json.optString("type")
                    )
                    if (sameRow) {
                        val queuedFields = json.readFields()
                        val remaining = PendingSyncPolicy.remainingFields(queuedFields, syncedFields)
                        when {
                            remaining.isEmpty() -> dao.deleteById(pending.id)
                            remaining != queuedFields -> {
                                val names = org.json.JSONArray()
                                remaining.forEach { field -> names.put(field.name) }
                                // REPLACE ile aynı id'ye yazılır: createdAt / retryCount korunur.
                                dao.insert(
                                    pending.copy(entryJson = json.put(KEY_FIELDS, names).toString())
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * İki kuyruk kaydı **aynı yapımı** mı gösteriyor?
     *
     * Yalnızca kanıt sayılan alanlara bakılır: tür + ortak dolu sağlayıcı kimliği; kimlik
     * yoksa (nadiren) aynı normalize başlık + aynı yıl. Başlık benzerliği tek başına
     * yetmez — aynı adı taşıyan remake'ler birbirinin işini silmemeli.
     */
    private fun sameProduction(previous: JSONObject, entry: MediaEntry): Boolean {
        if (previous.optString("type") != entry.type.name) return false
        val prevMalId = previous.optInt("malId", 0).takeIf { it > 0 }
        val prevSimklId = previous.optInt("simklId", 0).takeIf { it > 0 }
        val prevTmdbId = previous.optInt("tmdbId", 0).takeIf { it > 0 }
        if (prevMalId != null && prevMalId == entry.malId) return true
        if (prevSimklId != null && prevSimklId == entry.simklId) return true
        if (prevTmdbId != null && prevTmdbId == entry.tmdbId) return true
        val prevYear = previous.optInt("year", 0).takeIf { it > 0 }
        if (prevYear != null && entry.year != null && prevYear != entry.year) return false
        val prevTitle = com.kitsugi.animelist.model.MediaIdentity
            .normalizedTitle(previous.optString("title"))
        val entryTitle = com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(entry.title)
        return prevTitle.isNotBlank() && prevTitle == entryTitle
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
    ): Int = drain(context, dao, freshBudget) { operation, entry, fields, tmdbFields, expectedTmdbAccountId ->
        when (operation) {
            "UPDATE" -> ExternalListSyncManager.syncEntry(
                context = context,
                entry = entry,
                fields = fields,
                tmdbFields = tmdbFields,
                expectedTmdbAccountId = expectedTmdbAccountId
            )
            "DELETE" -> ExternalListSyncManager.deleteEntry(
                context = context,
                entry = entry,
                expectedTmdbAccountId = expectedTmdbAccountId
            )
            else -> null
        }
    }

    /**
     * Geriye uyumluluk: TMDB alanları gerekmeyen testler ve çağrıcılar için 3 parametreli aşırı yükleme.
     */
    @JvmName("drainLegacy")
    internal suspend fun drain(
        dao: PendingSyncDao,
        freshBudget: Boolean = false,
        execute: suspend (
            operation: String,
            entry: MediaEntry,
            fields: Set<SyncField>
        ) -> ExternalListSyncManager.SyncResult?
    ): Int = drain(null, dao, freshBudget) { operation, entry, fields, _, _ ->
        execute(operation, entry, fields)
    }

    internal suspend fun drain(
        dao: PendingSyncDao,
        freshBudget: Boolean = false,
        execute: suspend (
            operation: String,
            entry: MediaEntry,
            fields: Set<SyncField>,
            tmdbFields: com.kitsugi.animelist.data.auth.TmdbWriteFields,
            expectedTmdbAccountId: Int?
        ) -> ExternalListSyncManager.SyncResult?
    ): Int = drain(null, dao, freshBudget, execute)

    /**
     * Boşaltma döngüsünün çalıştırıcıdan ayrılmış çekirdeği (T02): `Context` ve ağ
     * yazıcıları olmadan, sahte DAO + sahte çalıştırıcıyla JVM birim testinde koşulur.
     *
     * [execute] tanınmayan işlemler için ÇAĞRILMAZ (sonuç `null` sayılır ve iş bütçe
     * yakarak korunur — bilinmeyen iş sessizce silinmez). `CancellationException`
     * asla bir "başarısız deneme" sayılmaz: `runSyncCatching` onu yeniden fırlatır ve
     * boşaltma bütçe yakmadan yarıda kalır (kilit `finally` ile bırakılır).
     */
    internal suspend fun drain(
        context: Context? = null,
        dao: PendingSyncDao,
        freshBudget: Boolean = false,
        execute: suspend (
            operation: String,
            entry: MediaEntry,
            fields: Set<SyncField>,
            tmdbFields: com.kitsugi.animelist.data.auth.TmdbWriteFields,
            expectedTmdbAccountId: Int?
        ) -> ExternalListSyncManager.SyncResult?
    ): Int = withContext(Dispatchers.IO) {
        // Exhausted jobs remain inspectable/recoverable; never silently delete user intent.
        if (!drainMutex.tryLock()) return@withContext 0
        try {
        if (freshBudget) dao.resetRetries()
        val pending = dao.getAll().filter { PendingSyncPolicy.isEligible(it.retryCount) }
        if (pending.isEmpty()) return@withContext 0
        // v2.4.342: arka plan kuyruğu boşaltma turu kalıcı günlüğe yazılır. Kullanıcı
        // "kaç iş bekliyordu, kaçı gitti" sorusunu sonradan cevaplayabilir.
        runCatching {
            com.kitsugi.animelist.core.diagnostics.KitsugiLog.i(
                com.kitsugi.animelist.core.diagnostics.KitsugiLog.Channel.WORK,
                "kuyruk",
                "boşaltma başladı",
                "bekleyen=${pending.size} | yeni bütçe=$freshBudget | " +
                    "anilist soğuma=${AniListGate.cooldownRemainingMs()}ms"
            )
        }

        var successCount = 0
        for (item in pending) {
            // Kota cezası sürüyorsa kalan işleri denemek cezayı uzatmaktan başka işe yaramaz.
            val cooldown = AniListGate.cooldownRemainingMs()
            if (cooldown > 3_000L) {
                context?.let { scheduleResume(it, dao, cooldown + 2_000L) }
                break
            }

            val decoded = runSyncCatching { item.entryJson.decodeJob() }.getOrNull()
            val entry = decoded?.entry
            if (entry == null) {
                // Preserve malformed jobs for recovery rather than destroying them.
                dao.incrementRetry(item.id)
                continue
            }

            val result = runSyncCatching {
                val fields = decoded.fields
                val tmdbFields = decoded.tmdbFields
                val expectedTmdbAccountId = decoded.expectedTmdbAccountId
                when (item.operation) {
                    "UPDATE", "DELETE" -> execute(item.operation, entry, fields, tmdbFields, expectedTmdbAccountId)
                    else -> null
                }
            }

            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            val syncResult = result.getOrNull()
            when (
                PendingSyncPolicy.decision(
                    hasResult = result.isSuccess && syncResult != null,
                    needsRetry = syncResult?.needsRetry ?: true,
                    errorTexts = syncResult?.errors.orEmpty(),
                    thrownMessage = result.exceptionOrNull()?.message
                )
            ) {
                PendingSyncPolicy.RetryDecision.DROP -> {
                    dao.deleteById(item.id)
                    successCount++
                }
                PendingSyncPolicy.RetryDecision.KEEP_INCREMENT -> {
                    val allErrors = syncResult?.errors.orEmpty().joinToString(" | ")
                    runCatching {
                        com.kitsugi.animelist.core.diagnostics.KitsugiLog.w(
                            com.kitsugi.animelist.core.diagnostics.KitsugiLog.Channel.WORK,
                            "kuyruk",
                            "iş gönderilemedi (${item.operation})",
                            "deneme=${item.retryCount + 1} | " +
                                (allErrors.ifBlank { result.exceptionOrNull()?.message }.orEmpty().take(400))
                        )
                    }
                    val isQuota = AniListGate.isQuotaMessage(allErrors) || AniListGate.isCoolingDown()
                    if (!isQuota) {
                        dao.incrementRetry(item.id)
                    } else {
                        context?.let { scheduleResume(it, dao, AniListGate.cooldownRemainingMs() + 2_000L) }
                        break
                    }
                }
                PendingSyncPolicy.RetryDecision.KEEP_WITHOUT_INCREMENT -> Unit
            }
        }

        // Tur özeti: kaç iş gönderildi, kaç iş kaldı.
        runCatching {
            val remaining = dao.getAll().size
            com.kitsugi.animelist.core.diagnostics.KitsugiLog.i(
                com.kitsugi.animelist.core.diagnostics.KitsugiLog.Channel.WORK,
                "kuyruk",
                "boşaltma bitti",
                "gönderilen=$successCount | kalan=$remaining"
            )
        }

        successCount
        } finally {
            drainMutex.unlock()
        }
    }

    // ────────────────────────────────────────────────
    // JSON Serializasyon / Deserializasyon
    // ────────────────────────────────────────────────

    private data class DecodedJob(
        val entry: MediaEntry,
        val fields: Set<SyncField>,
        val tmdbFields: com.kitsugi.animelist.data.auth.TmdbWriteFields,
        val expectedTmdbAccountId: Int?
    )

    /** İşin kendi alan kümesi JSON'a `_syncFields` ve `_tmdbWriteFields` olarak gömülür (şema değişikliği gerekmez). */
    private fun MediaEntry.toJson(
        fields: Set<SyncField>,
        tmdbFields: com.kitsugi.animelist.data.auth.TmdbWriteFields = com.kitsugi.animelist.data.auth.TmdbWriteFields.All,
        expectedTmdbAccountId: Int? = null
    ): String {
        val names = org.json.JSONArray()
        fields.forEach { names.put(it.name) }
        return JSONObject(MediaEntryBackup.exportToJson(listOf(this)))
            .getJSONArray("entries").getJSONObject(0)
            .put("id", id)
            .put(KEY_FIELDS, names)
            .put(TMDB_FIELDS_KEY, tmdbFields.toToken())
            .apply {
                if (expectedTmdbAccountId != null) put(TMDB_ACCOUNT_ID_KEY, expectedTmdbAccountId)
            }
            .toString()
    }

    /**
     * Kuyruk kaydını satır, alanlar, tmdbFields ve expectedTmdbAccountId bilgilerine çevirir.
     */
    private fun String.decodeJob(): DecodedJob {
        val item = JSONObject(this)
        val fields = item.optJSONArray(KEY_FIELDS)?.let { names ->
            (0 until names.length()).mapNotNull { index ->
                runSyncCatching { SyncField.valueOf(names.getString(index)) }.getOrNull()
            }.toSet()
        }?.takeIf { it.isNotEmpty() } ?: SyncField.ALL

        val tmdbFields = com.kitsugi.animelist.data.auth.TmdbWriteFields.fromToken(
            if (item.has(TMDB_FIELDS_KEY)) item.optString(TMDB_FIELDS_KEY) else null
        ) ?: com.kitsugi.animelist.data.auth.TmdbWriteFields.All

        val expectedTmdbAccountId = if (item.has(TMDB_ACCOUNT_ID_KEY)) item.optInt(TMDB_ACCOUNT_ID_KEY, 0) else null

        item.remove(KEY_FIELDS)
        item.remove(TMDB_FIELDS_KEY)
        item.remove(TMDB_ACCOUNT_ID_KEY)

        val backup = JSONObject().put("schemaVersion", 2)
            .put("entries", org.json.JSONArray().put(item))
        val entry = MediaEntryBackup.importFromJson(backup.toString()).single()
            .copy(id = item.optInt("id", 0))
        return DecodedJob(entry, fields, tmdbFields, expectedTmdbAccountId)
    }

    /** Kayıttaki alan kümesini okur (yoksa tam yazım). */
    private fun JSONObject.readFields(): Set<SyncField> = optJSONArray(KEY_FIELDS)?.let { names ->
        (0 until names.length()).mapNotNull { index ->
            runSyncCatching { SyncField.valueOf(names.getString(index)) }.getOrNull()
        }.toSet()
    }?.takeIf { it.isNotEmpty() } ?: SyncField.ALL

    private const val KEY_FIELDS = "_syncFields"
    private const val TMDB_FIELDS_KEY = "_tmdbWriteFields"
    private const val TMDB_ACCOUNT_ID_KEY = "_tmdbAccountId"
}
