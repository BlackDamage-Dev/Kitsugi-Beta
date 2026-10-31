package com.kitsugi.animelist.data.local

import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.auth.AniListGate
import com.kitsugi.animelist.data.auth.SourceSyncStatus
import com.kitsugi.animelist.data.auth.runSyncCatching
import android.content.Context
import com.kitsugi.animelist.data.auth.ExternalListSyncManager
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.Dispatchers
import com.kitsugi.animelist.utils.PreferenceHelpers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject

class MediaEntryRepository(
    private val dao: MediaEntryDao,
    private val pendingSyncDao: PendingSyncDao? = null,
    val context: Context? = null,
    private val onExternalSyncMessage: ((String) -> Unit)? = null
) {
    /**
     * Bir kaydın uzak eşitlemesi için bekleyen son istek (v2.4.341).
     * Aynı kayda art arda gelen düzenlemeler [ExternalSyncCoalescer] tarafından tek yazmaya
     * indirilir; burada yalnızca "en son istenen" parametreler tutulur.
     */
    private data class PendingExternalSync(
        val advancedScores: List<Double>? = null,
        val skipBangumiWrite: Boolean = false
    )

    private val pendingExternalSyncs = java.util.concurrent.ConcurrentHashMap<Int, PendingExternalSync>()

    /**
     * "Neden eşitlenmiyor?" bildirimlerinin son gösterilme zamanı. Aynı gerekçe her
     * dokunuşta tekrarlanmaz (2 dk), böylece kullanıcı bildirim yağmuruna tutulmaz.
     */
    private val syncNoticeAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun pendingKey(entryId: Int): String = "entry-$entryId"

    /**
     * Uzak eşitlemeyi [quietMs] sessizlikten sonra, sırayla çalıştırır.
     * Yerel yazım zaten tamamlanmıştır; buradaki gecikme yalnızca ağ tarafındadır.
     */
    private fun scheduleExternalSync(entryId: Int, quietMs: Long = ExternalSyncCoalescer.QUIET_PERIOD_MS) {
        if (entryId <= 0) return
        ExternalSyncCoalescer.schedule(pendingKey(entryId), quietMs) {
            // Kuyrukta beklerken kayıt silinmiş olabilir → yapacak bir şey yok.
            val latest = dao.getById(entryId)?.toDomain() ?: return@schedule
            val pending = pendingExternalSyncs.remove(entryId)
            syncEntryIfPossible(
                latest,
                pending?.advancedScores,
                pending?.skipBangumiWrite ?: false
            )
        }
    }

    /**
     * Kota nedeniyle ertelenen işleri, AniList'in ceza penceresi bittiğinde kendiliğinden
     * yeniden dener. Kullanıcının aynı düğmeye tekrar basmasına gerek kalmaz.
     */
    private suspend fun scheduleQuotaResume() {
        val appContext = context ?: return
        val queueDao = pendingSyncDao ?: return
        val waitMs = (AniListGate.cooldownRemainingMs() + 2_000L).coerceAtLeast(2_000L)
        PendingSyncDrainer.scheduleResume(appContext, queueDao, waitMs)
    }

    /**
     * Kuyrukta bekleyen (henüz gönderilememiş) değişikliği olan **yerel satır id'leri**.
     * Liste içe aktarımı bu satırları uzak veriyle ezmez (v2.4.341).
     */
    private suspend fun pendingEntryIds(): Set<Int> {
        val queue = pendingSyncDao ?: return emptySet()
        return try {
            queue.getAll().mapNotNull { job ->
                runCatching { JSONObject(job.entryJson).optInt("id", 0) }.getOrDefault(0)
            }.filter { it > 0 }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    /** Bekleyen (gönderilememiş) değişiklik sayısı — liste ekranındaki durum şeridi için. */
    suspend fun pendingSyncCount(): Int {
        val queue = pendingSyncDao ?: return 0
        return try {
            queue.getAll().size
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Bekleyen değişikliklerin **kaynak başına** dağılımı.
     *
     * Durum şeridi "AniList: 3, MyAnimeList: 1" gibi konuşabilsin diye kuyruk işlerinin
     * `entryJson` içindeki `source` alanı okunur. (Kaynak bilgisi olmayan işler sayılmaz.)
     */
    suspend fun pendingSyncBySource(): Map<SourceSyncStatus.Source, Int> {
        val queue = pendingSyncDao ?: return emptyMap()
        val counts = mutableMapOf<SourceSyncStatus.Source, Int>()
        try {
            for (job in queue.getAll()) {
                val source = runCatching {
                    SourceSyncStatus.Source.fromEntrySource(JSONObject(job.entryJson).optString("source"))
                }.getOrNull() ?: continue
                counts[source] = (counts[source] ?: 0) + 1
            }
        } catch (e: Exception) {
            return emptyMap()
        }
        return counts
    }

    /**
     * Kullanıcı "Şimdi dene"ye bastığında: deneme sayaçları tazelenir (5 denemeyi aşmış,
     * "asla denenmeyecek" duruma düşmüş işler dahil) ve kuyruk zorla boşaltılır.
     *
     * Gönderim sonrası kuyruğu boşalan kaynakların durumu "sorunsuz"a çekilir; böylece
     * şerit eski hatayı göstermeye devam etmez.
     *
     * @return gönderilen işlem sayısı
     */
    suspend fun retryPendingSyncNow(): Int {
        val sent = drainPendingQueue(freshBudget = true)
        val appContext = context
        if (appContext != null && sent > 0) {
            val remaining = pendingSyncBySource()
            SourceSyncStatus.Source.entries.forEach { source ->
                if ((remaining[source] ?: 0) == 0) {
                    val snapshot = SourceSyncStatus.snapshot(appContext, source)
                    if (!snapshot.isHealthy) SourceSyncStatus.reportSuccess(appContext, source)
                }
            }
        }
        return sent
    }
    /**
     * PERF (2026-10-20): [withCrossSourceAdultFlags] tüm tabloyu entity→domain'e çevirip
     * satır başına kimlik anahtarları hesaplar; büyük kütüphanelerde onlarca-milisaniyelik
     * iştir. Eski sürüm bu işi TOPLAYICI iş parçacığında (stateIn(viewModelScope) → Main)
     * çalıştırıyordu: hesap senkronu/kayit yazımı sırasında her tablo değişimi ana iş
     * parçacığını kilitliyor ve Listem/Keşfet donuyordu. Artık hesap [Dispatchers.Default]
     * üzerinde yapılır; [distinctUntilChanged] içerik değişmemiş emisyonları eler ve
     * [conflate] ardışık yazım patlamalarında (ör. +18 geri yazma turu satır satır günceller)
     * yalnızca EN GÜNCEL anlık görüntünün işlenmesini garanti eder.
     */
    val entriesFlow: Flow<List<MediaEntry>> = kotlinx.coroutines.flow.flow {
        dao.observeAll().collect { entities ->
            emit(entities.withCrossSourceAdultFlags())
        }
    }
        .distinctUntilChanged()
        .conflate()
        .flowOn(Dispatchers.Default)


    /**
     * @param skipBangumiWrite kayıt için Bangumi yazması çağrıda zaten yapıldıysa `true`;
     * canlı çapraz eşitleme diğer beş kaynağa yine yayılır.
     */
    suspend fun insert(
        entry: MediaEntry,
        syncExternal: Boolean = true,
        skipBangumiWrite: Boolean = false
    ) {
        val entryWithTime = if (entry.updatedAt == 0L) {
            entry.copy(updatedAt = System.currentTimeMillis() / 1000L)
        } else {
            entry
        }
        val newId = dao.insert(entryWithTime.copy(id = 0).toEntity())
        // DB'de oluşan gerçek id ile sync yap (AniList entryId geri yazımı doğru entity'yi yakalar)
        val persistedEntry = dao.getById(newId.toInt())?.toDomain() ?: entryWithTime.copy(id = newId.toInt())
        // Aynı yapımın başka kaynaklardaki yerel satırları da aynı kullanıcı değerlerini alsın.
        mirrorUserFieldsToSiblings(persistedEntry)
        if (syncExternal) {
            if (skipBangumiWrite) {
                pendingExternalSyncs[persistedEntry.id] = PendingExternalSync(skipBangumiWrite = true)
            }
            // Uzak yazma kısa bir sessizlikten sonra sıraya girer (kotayı korur).
            scheduleExternalSync(persistedEntry.id)
        }
    }

    suspend fun insertAll(entries: List<MediaEntry>) {
        if (entries.isEmpty()) return
        val entriesWithTime = entries.map { entry ->
            if (entry.updatedAt == 0L) {
                entry.copy(updatedAt = System.currentTimeMillis() / 1000L)
            } else {
                entry
            }
        }
        dao.insertAll(entriesWithTime.map { it.copy(id = 0).toEntity() })
    }

    /**
     * Granüler import: mevcut kayıtlarla karşılaştırır, sadece
     * gerçekten değişen kayıtları günceller. Tüm işlem tek bir
     * @Transaction içinde çalışır → Room Flow yalnızca 1 kez
     * tetiklenir ve Compose yalnızca değişen kartları yeniden çizer.
     *
     * @param source  "anilist", "mal", "simkl" vb.
     * @param importedEntries  Uzak API'dan gelen güncel liste
     */
    suspend fun smartImport(
        source: String,
        importedEntries: List<MediaEntry>,
        allowDelete: Boolean = true
    ) {
        if (importedEntries.isEmpty()) {
            if (allowDelete) {
                clearPendingJobsFor(source = source)
                dao.deleteBySource(source)
            }
            return
        }

        val existing = dao.getAll().filter { it.source.equals(source, ignoreCase = true) }
        // v2.4.341: Henüz uzak tarafa GÖNDERİLEMEMİŞ yerel değişikliği olan satır, içe
        // aktarılan (uzak) kopyayla ezilmez. Aksi halde "düzenledim ama AniList'e gitmedi"
        // durumunda her liste yenilemesi / 24 saatlik arka plan senkronu kullanıcının
        // değişikliğini sessizce geri alıyordu — "hiçbir şey eşitlenmiyor" hissinin kaynağı.
        val protectedEntryIds = pendingEntryIds()
        val updates = linkedMapOf<Int, MediaEntryEntity>()
        val inserts = mutableListOf<MediaEntry>()
        val matchedIds = mutableSetOf<Int>()
        for (entry in importedEntries) {
            val imported = entry.copy(source = source)
            val matches = existing.filter {
                com.kitsugi.animelist.model.MediaIdentity.sameMedia(it.toDomain(), imported)
            }
            val match = when {
                matches.isEmpty() -> null
                matches.size == 1 -> matches.single()
                else -> {
                    // Birden fazla eşleşme durumunda en doğru kaydı tespit et (çökmeyi engelle)
                    val importedKeys = com.kitsugi.animelist.model.MediaIdentity.keys(imported)
                    val keyMatch = matches.firstOrNull { m ->
                        val mKeys = com.kitsugi.animelist.model.MediaIdentity.keys(m.toDomain())
                        importedKeys.intersect(mKeys).isNotEmpty()
                    }
                    val exactTitleMatch = matches.firstOrNull { m ->
                        val normLocal = com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(m.title)
                        val normImp = com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(imported.title)
                        normLocal.isNotBlank() && normLocal == normImp
                    }
                    val exactEngMatch = matches.firstOrNull { m ->
                        val engLocal = m.titleEnglish?.let { com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(it) }
                        val engImp = imported.titleEnglish?.let { com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(it) }
                        !engLocal.isNullOrBlank() && engLocal == engImp
                    }
                    keyMatch ?: exactTitleMatch ?: exactEngMatch ?: matches.maxByOrNull { it.updatedAt } ?: matches.first()
                }
            }
            if (match != null) {
                matchedIds.add(match.id)
                if (match.id in protectedEntryIds) continue
                // +18 işareti GERİ ALINMAZ: örneğin Simkl liste API'si yetişkin bayrağını
                // taşımıyor; güncel kayıt `isAdult = false` getirse bile AniList/MAL gibi
                // birinci elden bilgilendirilmiş satırın işaretini silmek bulanıklığı
                // kaybeder (rapor: "Simkl listesinde 18+ blur uygulanmıyor").
                // Çözülmüş başlıklar da GERİ ALINMAZ: Bangumi koleksiyon ucu yalnızca özgün
                // (Japonca/Çince) adı döndürdüğü için her yeniden içe aktarım, daha önce
                // tamamlanmış Latin/İngilizce adları CJK adla eziyordu — liste, başlık dili
                // İngilizce seçili olsa bile yeniden Japonca/Çinceye dönüyordu (v2.4.315).
                updates[match.id] = preserveResolvedTitles(match, imported).copy(
                    id = match.id,
                    isAdult = imported.isAdult || match.isAdult
                ).toEntity()
            } else {
                val prior = inserts.indexOfFirst {
                    com.kitsugi.animelist.model.MediaIdentity.sameMedia(it, imported, allowTitle = false)
                }
                if (prior >= 0) inserts[prior] = imported else inserts.add(imported)
            }
        }
        // allowDelete=false must really mean NO deletion, including alleged title duplicates.
        val deletions = if (allowDelete) existing.filter { it.id !in matchedIds }.map { it.id } else emptyList()
        // İçe aktarımda listeden DÜŞEN satırların bayat kuyruk işleri de düşürülür:
        // eski kopya sonradan uzaklara geri yazılsa import taze veriyi silerdi.
        if (deletions.isNotEmpty()) clearPendingJobsFor(entryIds = deletions)
        dao.smartImportTransaction(inserts.map { it.copy(id = 0).toEntity() }, updates.values.toList(), deletions)

        if (sourceNeedsAdultRescan(source)) {
            // Liste yeni çekildi → +18 işaretlerini kimliklerden tamamla.
            val appContext = context ?: com.kitsugi.animelist.KitsugiApplication.getInstance()?.applicationContext
            if (appContext != null) {
                runCatching {
                    com.kitsugi.animelist.ui.screens.mylist.AdultFlagBackfillMigration.requestRescan(appContext)
                }
            }
        }
    }

    /**
     * Bir kaynağın listesini BAŞTAN yazarken +18 işaretlerini korur.
     *
     * `deleteBySource(...) + insertAll(...)` kalıbı (gecelik arka plan senkronu ve
     * bildirim işçisi bunu yapıyor) sil-yeniden-yaz döngüsüdür: Simkl/Shikimori/Kitsu
     * liste API'leri `adult` alanını taşımadığından, daha önce kimlik üzerinden
     * işaretlenmiş kayıtların bulanıklığı da o gece silinirdi — "bir düzeldi, sonra
     * yine bozuldu" şikâyetinin kökü. Bu metot eski işaretleri kanonik kimlikler
     * üzerinden yeni listeye geri yazar.
     */
    suspend fun replaceSourcePreservingAdultFlags(source: String, entries: List<MediaEntry>) {
        val preservedKeys = runCatching {
            dao.getAll()
                .filter { it.source.equals(source, ignoreCase = true) && it.isAdult }
                .map { it.toDomain() }
                .flatMap { com.kitsugi.animelist.model.MediaIdentity.keys(it) }
                .toSet()
        }.getOrDefault(emptySet())

        val enriched = if (preservedKeys.isEmpty()) entries else entries.map { entry ->
            if (entry.isAdult ||
                com.kitsugi.animelist.model.MediaIdentity.keys(entry).any { it in preservedKeys }
            ) entry.copy(isAdult = true) else entry
        }

        // Kaynak BAŞTAN yeniden yazılıyor: o kaynağın bayat bekleyen işleri (eski satır
        // id'leri) temizlenmezse drain'de taze içe aktarımı ESKİ kopyayla ezerekti.
        clearPendingJobsFor(source = source)
        dao.deleteBySource(source)
        insertAll(enriched)

        if (sourceNeedsAdultRescan(source)) {
            val appContext = context ?: com.kitsugi.animelist.KitsugiApplication.getInstance()?.applicationContext
            if (appContext != null) {
                runCatching {
                    com.kitsugi.animelist.ui.screens.mylist.AdultFlagBackfillMigration.requestRescan(appContext)
                }
            }
        }
    }

    suspend fun updateAllDirect(entries: List<MediaEntry>) {
        if (entries.isEmpty()) return
        dao.updateAll(entries.map { it.toEntity() })
    }

    suspend fun update(
        entry: MediaEntry,
        syncExternal: Boolean = true,
        advancedScores: List<Double>? = null,
        skipBangumiWrite: Boolean = false
    ) {
        val entryWithTime = entry.copy(updatedAt = System.currentTimeMillis() / 1000L)
        dao.update(
            entryWithTime.toEntity()
        )

        // Kardeş satırlar (aynı yapımın diğer kaynak kopyaları) anında aynı değerleri görür.
        mirrorUserFieldsToSiblings(entryWithTime)

        if (syncExternal) {
            // Aynı kayda art arda gelen düzenlemeler (bölüm +1, puan, not) tek uzak yazmaya
            // birleşir: son hâl gönderilir, ara hâller için istek harcanmaz.
            val previous = pendingExternalSyncs[entryWithTime.id]
            pendingExternalSyncs[entryWithTime.id] = PendingExternalSync(
                advancedScores = advancedScores ?: previous?.advancedScores,
                skipBangumiWrite = skipBangumiWrite || previous?.skipBangumiWrite == true
            )
            scheduleExternalSync(entryWithTime.id)
        }
    }

    suspend fun deleteById(id: Int, syncExternal: Boolean = true) {
        val entry = dao.getById(id)?.toDomain()

        // Uzak silme zaten tüm kaynaklara yayılıyor; aynı yapımın yerel kardeş satırları da
        // silinir, yoksa silinen kayıt diğer kaynak sekmelerinde hayalet gibi kalıyordu.
        val siblingIds = entry?.let { findSiblingRows(it).map { sibling -> sibling.id } }.orEmpty()

        // Yerelde silinen satırların BEKLEYEN kuyruk işleri düşürülür: sonradan drain
        // edilen bayat bir UPDATE, az önce silinen kaydı uzaklarda GERİ DİRİTEBİLİRDİ
        // (özellikle DELETE ilk denemede başarılıysa kuyruğa DELETE de düşmezdi).
        clearPendingJobsFor(entryIds = listOf(id) + siblingIds)

        dao.deleteById(id)
        siblingIds.forEach { siblingId -> dao.deleteById(siblingId) }

        if (syncExternal && entry != null) {
            // Silme de aynı sıraya girer: bekleyen bir güncellemenin arkasından çalışır,
            // böylece silinen kayıt uzakta yeniden görünmez. Sessizlik beklemez.
            ExternalSyncCoalescer.schedule(pendingKey(entry.id), quietMs = 0L) {
                syncDeleteIfPossible(entry)
            }
        }
    }

    suspend fun deleteBySource(source: String) {
        clearPendingJobsFor(source = source)
        dao.deleteBySource(source)
    }

    suspend fun deleteAll() {
        dao.deleteAll()
        // Tüm liste temizlendi: kuyrukta bayat UPDATE/DELETE işi kalırsa eski yerel
        // kopyalar uzaklara geri yazılmış (dirilmiş) olurdu — kullanıcı niyeti: hepsi gitsin.
        runSyncCatching { pendingSyncDao?.clearAll() }
    }

    suspend fun replaceAll(entries: List<MediaEntry>) {
        val now = System.currentTimeMillis() / 1000L
        val entities = entries.map { it.copy(id = 0, updatedAt = it.updatedAt.takeIf { value -> value > 0 } ?: now).toEntity() }
        dao.replaceAllTransaction(entities)
        // Yedek geri yükleme_REPLACE: önceden başarısız olmuş bayat işler, TAZE veriyi
        // eski yerel kopyayla ezerek geri sürerdi — kuyruk temizlenir.
        runSyncCatching { pendingSyncDao?.clearAll() }
    }

    /**
     * Bekleyen kuyruğu boşaltır. AppViewModel.init'ten çağrılır.
     * @param freshBudget true ise yeniden deneme sayaçları önce sıfırlanır
     * (bağlantı geri geldiğinde çağrılır; 5 denemeyi aşmış işler kalıcı donmasın).
     * @return Başarıyla gönderilen işlem sayısı
     */
    suspend fun drainPendingQueue(freshBudget: Boolean = false): Int {
        val ctx = context ?: return 0
        val queueDao = pendingSyncDao ?: return 0
        return PendingSyncDrainer.drain(ctx, queueDao, freshBudget = freshBudget)
    }

    // ────────────────────────────────────────────────
    // Kardeş satır tutarlılığı (aynı yapım, farklı kaynak kopyaları)
    // ────────────────────────────────────────────────

    /** Bir satırın kimlik kümesi: kendi alanı + kalıcı çapraz kimlik önbelleği (ağ yok). */
    private fun resolvedIdsFor(appContext: Context, row: MediaEntry): com.kitsugi.animelist.data.auth.CrossProviderIds {
        val decoded = com.kitsugi.animelist.data.auth.CrossProviderIds.decode(
            storedId = row.malId,
            source = row.source,
            type = row.type,
            entrySimklId = row.simklId,
            entryTmdbId = row.tmdbId
        )
        val key = com.kitsugi.animelist.data.auth.CrossProviderIds.cacheKey(
            type = row.type,
            storedId = row.malId,
            title = row.title,
            titleEnglish = row.titleEnglish,
            year = row.year,
            source = row.source
        )
        val cached = com.kitsugi.animelist.data.auth.CrossProviderIdentityCache.read(appContext, key)?.ids
        return decoded.mergedWith(cached)
    }

    /**
     * [entry] ile aynı yapımı gösteren diğer yerel satırlar. Eşleşme yalnızca doğrulanmış
     * kimlik kesişimiyle olur (aynı tür + ortak MAL/AniList/Kitsu/Shikimori/Bangumi/Simkl
     * kimliği; film/dizide TMDB de). Başlık benzerliği asla kullanılmaz.
     */
    private suspend fun findSiblingRows(entry: MediaEntry): List<MediaEntry> {
        val appContext = context ?: return emptyList()
        if (entry.id <= 0) return emptyList()
        val entryIds = resolvedIdsFor(appContext, entry)
        if (entryIds.isEmpty && entry.aniListEntryId == null) return emptyList()
        val includeTmdb = entry.type == MediaType.Movie || entry.type == MediaType.TvShow
        return dao.getAll()
            .map { it.toDomain() }
            .filter { other ->
                other.id != entry.id && other.type == entry.type &&
                    (
                        (entry.aniListEntryId != null && entry.aniListEntryId == other.aniListEntryId) ||
                            resolvedIdsFor(appContext, other).sharesAnyIdWith(entryIds, includeTmdb = includeTmdb)
                        )
            }
    }

    /**
     * Kullanıcı değerlerini (durum/puan/ilerleme/tarih/not/etiket/favori/gizlilik/tekrar)
     * kardeş satırlara yerel olarak yansıtır. Kimlik/başlık/görsel alanlarına dokunulmaz;
     * uzak eşitleme ayrıca çalışır (bu yalnızca yerel önbellek tutarlılığıdır).
     */
    private suspend fun mirrorUserFieldsToSiblings(entry: MediaEntry) {
        runSyncCatching {
            findSiblingRows(entry).forEach { sibling ->
                dao.update(
                    sibling.copy(
                        status = entry.status,
                        score = entry.score,
                        progress = entry.progress,
                        volumeProgress = entry.volumeProgress,
                        startDate = entry.startDate,
                        endDate = entry.endDate,
                        notes = entry.notes,
                        tags = entry.tags,
                        isFavorite = entry.isFavorite,
                        isPrivate = entry.isPrivate,
                        isHiddenFromStatusLists = entry.isHiddenFromStatusLists,
                        priority = entry.priority,
                        isRepeating = entry.isRepeating,
                        repeatCount = entry.repeatCount,
                        repeatValue = entry.repeatValue,
                        updatedAt = entry.updatedAt
                    ).toEntity()
                )
            }
        }
    }

    // ────────────────────────────────────────────────
    // Private Sync Helpers
    // ────────────────────────────────────────────────

    /**
     * Kaynak başına, **halk diliyle** yazılmış tek satırlık eşitleme durumu (v2.4.341).
     * Örn. "⏳ AniList istek sınırı doldu — 3 değişiklik 42 sn sonra gönderilecek".
     */
    private fun sourceMessage(
        context: Context,
        source: SourceSyncStatus.Source,
        reason: SourceSyncStatus.Reason,
        pending: Int = 1
    ): String = SourceSyncStatus.describeAction(context, source, reason, pending)

    /** Aynı gerekçeyi 2 dakikada birden fazla göstermemek için. */
    private fun shouldNotify(reason: SourceSyncStatus.Reason, source: SourceSyncStatus.Source): Boolean {
        val now = System.currentTimeMillis()
        val key = "${source.key}:${reason.name}"
        val previous = syncNoticeAt[key] ?: 0L
        if (now - previous < 120_000L) return false
        syncNoticeAt[key] = now
        return true
    }

    /** Hata metninden sorumlu kaynağı bulur (outcomes yoksa tahmin). */
    private fun failedSources(
        entry: MediaEntry,
        syncResult: ExternalListSyncManager.SyncResult?
    ): List<SourceSyncStatus.Source> {
        val fromOutcomes = syncResult?.outcomes.orEmpty()
            .filterValues { it == ExternalListSyncManager.OUTCOME_FAILED }
            .keys
            .mapNotNull { SourceSyncStatus.Source.fromOutcomeLabel(it) }
        if (fromOutcomes.isNotEmpty()) return fromOutcomes
        return listOfNotNull(SourceSyncStatus.Source.fromEntrySource(entry.source))
    }

    /**
     * Sonucu **tüm kaynaklar** için kalıcı duruma yazar ve yalnızca gerçekten sorun
     * olanlar için kullanıcıya tek satır gösterir.
     *
     * Eskiden bu bilgi yalnızca AniList için tutuluyor, MAL/Kitsu/Shikimori/Simkl/Bangumi
     * atlamaları **tamamen sessiz** kalıyordu.
     */
    private fun reportOutcomes(
        context: Context,
        syncResult: ExternalListSyncManager.SyncResult?
    ) {
        if (syncResult == null) return
        val notices = mutableListOf<String>()
        for ((label, outcome) in syncResult.outcomes) {
            val source = SourceSyncStatus.Source.fromOutcomeLabel(label) ?: continue
            when (val reason = SourceSyncStatus.classifyOutcome(outcome)) {
                SourceSyncStatus.Reason.OK -> SourceSyncStatus.reportSuccess(context, source)
                SourceSyncStatus.Reason.NONE -> Unit
                else -> {
                    SourceSyncStatus.reportFailure(context, source, reason, syncResult.skipped.firstOrNull())
                    if (shouldNotify(reason, source)) {
                        notices += sourceMessage(context, source, reason)
                    }
                }
            }
        }
        if (notices.isNotEmpty()) notifySyncResult(notices)
    }

    private suspend fun syncEntryIfPossible(
        entry: MediaEntry,
        advancedScores: List<Double>? = null,
        skipBangumiWrite: Boolean = false
    ) {
        val appContext = context ?: return
        // Senkronizasyon için geçerli koşullar:
        // 1) malId var (MAL veya gerçek bir MAL ID'si olan AniList kaydı)
        // 2) aniListEntryId var (kaynak ne olursa olsun AniList liste kaydı)
        // 3) kaynak anilist (mediaId henüz bilinmese bile AniList'e push edilebilir)
        // 4) simklId var (Simkl kaydı)
        val hasAniListLink = entry.aniListEntryId != null || entry.source == "anilist"
        val hasMalLink = entry.malId != null
        val hasSimklLink = entry.simklId != null && entry.simklId > 0
        val hasTmdbLink = entry.tmdbId != null && entry.tmdbId > 0
        val hasTitle = entry.title.isNotBlank() || !entry.titleEnglish.isNullOrBlank()
        if (!hasAniListLink && !hasMalLink && !hasSimklLink && !hasTmdbLink && !hasTitle) return

        val result = runSyncCatching {
            ExternalListSyncManager.syncEntry(
                context = appContext,
                entry = entry,
                advancedScores = advancedScores,
                skipBangumiWrite = skipBangumiWrite
            )
        }

        val syncResult = result.getOrNull()
        // "Atlandı" (kimlik güvenle çözülemedi) başarısızlık sayılmaz: kaynak listeye göre
        // katalogda hiç karşılığı olmayan yapımlar kuyrukta birikip her açılışta tekrar denenirdi.
        val success = result.isSuccess && syncResult != null && syncResult.errors.isEmpty()

        if (success) {
            val messages = syncResult?.messages.orEmpty()
            if (messages.isNotEmpty()) removeFromQueueIfPresent(entry, "UPDATE")
            notifySyncResult(messages)
            reportOutcomes(appContext, syncResult)

            // Dual-write: AniList'ten dönen list entry ID'yi yerel DB'ye kaydet
            val newAniListEntryId = syncResult?.aniListEntryId
            if (newAniListEntryId != null && newAniListEntryId != entry.aniListEntryId) {
                val updatedEntry = (dao.getById(entry.id)?.toDomain() ?: entry).copy(aniListEntryId = newAniListEntryId)
                dao.update(updatedEntry.toEntity())
            }

            if (messages.isNotEmpty()) drainPendingQueue()
        } else {
            enqueuePending("UPDATE", entry)
            val allErrors = syncResult?.errors.orEmpty().joinToString(" | ")
            // v2.4.341: mesaj artık **hangi kaynak** ve **neden** bilgisini birlikte verir.
            // Eskiden her hata "📵 Çevrimdışı kaydedildi" diyordu; kullanıcı internetini
            // kontrol ediyor, sorun değişmiyordu. Kaynak başına gerekçe kalıcı olarak
            // [SourceSyncStatus]'a yazılır → Listem şeridi ve bildirim ekranı gösterir.
            val reason = SourceSyncStatus.classify(allErrors, AniListGate.isCoolingDown())
            val sources = failedSources(entry, syncResult)
            sources.forEach { source -> SourceSyncStatus.reportFailure(appContext, source, reason, allErrors) }
            if (reason == SourceSyncStatus.Reason.QUOTA) scheduleQuotaResume()
            val sourceMessages = sources.map { source -> sourceMessage(appContext, source, reason) }
                .ifEmpty { listOf(sourceMessage(appContext, SourceSyncStatus.Source.ANILIST, reason)) }
            notifySyncResult(
                syncResult?.messages.orEmpty() + sourceMessages +
                    listOfNotNull(syncOutcomeNote(syncResult?.outcomes))
            )
        }
    }

    private suspend fun syncDeleteIfPossible(entry: MediaEntry) {
        val appContext = context ?: return
        // Silme için geçerli koşullar (syncEntryIfPossible ile aynı mantık)
        val hasAniListLink = entry.aniListEntryId != null || entry.source == "anilist"
        val hasMalLink = entry.malId != null
        val hasSimklLink = entry.simklId != null && entry.simklId > 0
        val hasTmdbLink = entry.tmdbId != null && entry.tmdbId > 0
        val hasTitle = entry.title.isNotBlank() || !entry.titleEnglish.isNullOrBlank()
        if (!hasAniListLink && !hasMalLink && !hasSimklLink && !hasTmdbLink && !hasTitle) return

        val result = runSyncCatching {
            ExternalListSyncManager.deleteEntry(
                context = appContext,
                entry = entry
            )
        }

        val syncResult = result.getOrNull()
        val success = result.isSuccess && syncResult != null && syncResult.errors.isEmpty()

        if (success) {
            val messages = syncResult?.messages.orEmpty()
            if (messages.isNotEmpty()) removeFromQueueIfPresent(entry, "DELETE")
            notifySyncResult(messages)
            reportOutcomes(appContext, syncResult)
            if (messages.isNotEmpty()) drainPendingQueue()
        } else {
            enqueuePending("DELETE", entry)
            val allErrors = syncResult?.errors.orEmpty().joinToString(" | ")
            // v2.4.341: mesaj artık **hangi kaynak** ve **neden** bilgisini birlikte verir.
            // Eskiden her hata "📵 Çevrimdışı kaydedildi" diyordu; kullanıcı internetini
            // kontrol ediyor, sorun değişmiyordu. Kaynak başına gerekçe kalıcı olarak
            // [SourceSyncStatus]'a yazılır → Listem şeridi ve bildirim ekranı gösterir.
            val reason = SourceSyncStatus.classify(allErrors, AniListGate.isCoolingDown())
            val sources = failedSources(entry, syncResult)
            sources.forEach { source -> SourceSyncStatus.reportFailure(appContext, source, reason, allErrors) }
            if (reason == SourceSyncStatus.Reason.QUOTA) scheduleQuotaResume()
            val sourceMessages = sources.map { source -> sourceMessage(appContext, source, reason) }
                .ifEmpty { listOf(sourceMessage(appContext, SourceSyncStatus.Source.ANILIST, reason)) }
            notifySyncResult(
                syncResult?.messages.orEmpty() + sourceMessages +
                    listOfNotNull(syncOutcomeNote(syncResult?.outcomes))
            )
        }
    }

    private suspend fun enqueuePending(operation: String, entry: MediaEntry) {
        val queueDao = pendingSyncDao ?: return
        PendingSyncDrainer.enqueue(queueDao, operation, entry)
    }

    /**
     * Aynı yerel kayıt + kaynak + tür + işlem için bekleyen kaydı temizle
     * (örn. çevrimiçiyken yeniden güncelleme yapınca eski UPDATE'i temizle)
     */
    /**
     * Yerelde silinen/yeniden yazılan satırların bekleyen kuyruk işlerini düşürür.
     *
     * [PendingSyncDrainer] işi JSON'daki `entry.id`'yle eşleştirir; satır silindikten
     * sonra drain edilen bayat bir UPDATE, uzak kayda ESKİ yerel kopyayı geri yazar
     * (silineni diriltir ya da taze içe aktarımı geri sürer). Yalnızca hedef satırların
     * işleri silinir; başka kaynakların geçerli işleri kuyrukta kalır.
     */
    private suspend fun clearPendingJobsFor(
        entryIds: Collection<Int> = emptyList(),
        source: String? = null
    ) {
        val queueDao = pendingSyncDao ?: return
        if (entryIds.isEmpty() && source == null) return
        runSyncCatching {
            queueDao.getAll().forEach { job ->
                val matches = runSyncCatching {
                    val json = org.json.JSONObject(job.entryJson)
                    (entryIds.isNotEmpty() && json.optInt("id", -1) in entryIds) ||
                        (source != null && json.optString("source").equals(source, ignoreCase = true))
                }.getOrDefault(false)
                if (matches) queueDao.deleteById(job.id)
            }
        }
    }

    private suspend fun removeFromQueueIfPresent(entry: MediaEntry, operation: String) {
        val queueDao = pendingSyncDao ?: return
        val all = queueDao.getAll()
        all.filter { it.operation == operation }.forEach { pending ->
            // Bare MAL IDs are not unique across anime, manga, or platform libraries.
            runSyncCatching {
                val json = org.json.JSONObject(pending.entryJson)
                val sameLocalRecord = entry.id > 0 && json.optInt("id", 0) == entry.id &&
                    json.optString("type") == entry.type.name && json.optString("source") == entry.source
                if (sameLocalRecord) queueDao.deleteById(pending.id)
            }
        }
    }

    /**
     * Hata durumunda hangi kaynağın neden eşitlenemediğini tek satırda özetler
     * (ör. "AniList: eşitlendi · Kitsu: kimlik çözülemedi") — kullanıcı "neden yok"
     * sorusunu raporu açmadan görebilsin diye.
     */
    private fun syncOutcomeNote(outcomes: Map<String, String>?): String? {
        if (outcomes.isNullOrEmpty()) return null
        val note = com.kitsugi.animelist.data.auth.CrossSyncPolicy.summarize(outcomes)
        return note.takeIf { it.isNotBlank() }
    }

    private fun notifySyncResult(messages: List<String>) {
        val message = messages
            .filter { it.isNotBlank() }
            .joinToString(" • ")

        if (message.isNotBlank()) {
            onExternalSyncMessage?.invoke(message)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// +18 (NSFW blur) işaretleri — kaynaklar arası tutarlılık
// ─────────────────────────────────────────────────────────────────────────────

/**
 * +18 işaretini KAYNAKLAR ARASI yayar.
 *
 * `isAdult` satır bazlı tutulur; oysa +18 bir YAPIM özelliğidir. AniList satırı
 * `isAdult = true` derken Simkl/Shikimori/Bangumi satırı aynı yapım için `false`
 * olabilir (bu kaynakların liste API'leri `adult` alanını taşımıyor). Sonuç: blur
 * ayarı açıkken kullanıcı aynı yapımı bir sekmede bulanık, diğerinde çıplak
 * görüyordu — rapor: "Simkl listem sayfası üzerinde kutucuklara 18+ blur
 * uygulanmıyor".
 *
 * Bu yüzden kanonik kimlikleri ([com.kitsugi.animelist.model.MediaIdentity.keys])
 * eşleşen tüm satırlar +18 sayılır. Yalnızca false→true yönünde çalışır; asla
 * işareti kaldırmaz ve kimliği çakışan (farklı yapım) satırlara dokunmaz.
 */
internal fun List<MediaEntryEntity>.withCrossSourceAdultFlags(): List<MediaEntry> {
    if (isEmpty()) return emptyList()
    val domains = map { it.toDomain() }
    // PERF: kimlik anahtarlarını satır başına BİR kez hesapla. Eski sürüm yetişkin satırlar
    // için bir, yetişkin bayrağı eksik her satır için iki kez hesaplıyordu (bayrak
    // yayarken + kontrol ederken); 2000 satırlık kütüphanede emisyon başına binlerce
    // gereksiz MediaIdentity.keys çağrısı anlamına geliyordu.
    if (domains.none { it.isAdult }) return domains
    val keysByRow = domains.map { entry -> com.kitsugi.animelist.model.MediaIdentity.keys(entry) }
    val adultKeys = HashSet<String>()
    domains.forEachIndexed { index, entry ->
        if (entry.isAdult) adultKeys += keysByRow[index]
    }
    if (adultKeys.isEmpty()) return domains
    return domains.mapIndexed { index, entry ->
        if (entry.isAdult) entry
        else if (keysByRow[index].any { it in adultKeys }) {
            entry.copy(isAdult = true)
        } else entry
    }
}

/**
 * İçe aktarım güncellemesinde **çözülmüş başlıkları korur** (`smartImport` güncelleme kolu).
 *
 * Başlık dili sözleşmesi kütüphane satırında üç varyantla yaşar: `title` (romaji/Latin ana
 * ad), `titleEnglish`, `titleJapanese` (özgün ad). Bazı liste uçları her seferinde yalnızca
 * özgün adı getirir — Bangumi koleksiyon ucu (`name` / `name_cn`, infobox yok) bunun tipik
 * örneğidir ve İngilizce/romaji adlar sonradan
 * [com.kitsugi.animelist.data.remote.BangumiLibraryTitleHydrator] ile tamamlanır.
 * İçe aktarım satırı körü körüne yazılırsa tamamlanmış her ad bir sonraki senkronda CJK ada
 * geri döner ve seçili başlık dili (örn. İngilizce) kalıcı olarak yok sayılırdı.
 *
 * Kurallar (yalnızca yükseltme var, geriletme yok):
 * - `title`: gelen ad CJK iken mevcut ad Latin ise mevcut korunur; gelen Latin ad her
 *   zaman kazanır (kaynak düzeltmesi olabilir); boş gelen ad mevcutla değiştirilmez.
 * - `titleEnglish`: Latin bir İngilizce ad gelmedikçe mevcut korunur.
 * - `titleJapanese`: gelen özgün ad önceliklidir; yoksa mevcut, o da yoksa gelen CJK ana
 *   başlık özgün ad olarak saklanır (sonraki çözümlemelerin arama terimi kaybolmaz).
 */
internal fun preserveResolvedTitles(existing: MediaEntryEntity, imported: MediaEntry): MediaEntry {
    val importedTitle = imported.title.trim()
    val existingTitle = existing.title.trim()
    val title = when {
        importedTitle.isEmpty() -> existing.title
        PreferenceHelpers.hasCjkCharacters(importedTitle) &&
            PreferenceHelpers.isLatinText(existingTitle) &&
            !existingTitle.startsWith("Bangumi #") -> existing.title
        else -> imported.title
    }
    val titleEnglish = imported.titleEnglish?.takeIf { PreferenceHelpers.isLatinText(it) }
        ?: existing.titleEnglish
        ?: imported.titleEnglish
    val titleJapanese = imported.titleJapanese?.takeIf { it.isNotBlank() }
        ?: existing.titleJapanese?.takeIf { it.isNotBlank() }
        ?: importedTitle.takeIf { PreferenceHelpers.hasCjkCharacters(it) }
    return imported.copy(
        title = title,
        titleEnglish = titleEnglish,
        titleJapanese = titleJapanese
    )
}

/**
 * Bu kaynakların liste API'leri +18 bilgisini taşımaz; içe aktarımdan sonra
 * kimlik üzerinden yeniden sorgulanmaları gerekir
 * (bkz. [com.kitsugi.animelist.ui.screens.mylist.AdultFlagBackfillMigration]).
 */
internal fun sourceNeedsAdultRescan(source: String): Boolean =
    source.equals("simkl", ignoreCase = true) ||
        source.equals("shikimori", ignoreCase = true) ||
        source.equals("bangumi", ignoreCase = true)
