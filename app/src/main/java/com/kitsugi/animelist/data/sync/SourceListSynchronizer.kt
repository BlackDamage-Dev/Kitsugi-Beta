package com.kitsugi.animelist.data.sync

import android.content.Context
import com.kitsugi.animelist.data.auth.runSyncCatching
import com.kitsugi.animelist.data.local.ImportStats
import com.kitsugi.animelist.data.local.MediaEntryRepository
import com.kitsugi.animelist.model.MediaEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

/** Tek bir kaynak yenilemesinin sonucu. */
sealed class ListRefreshOutcome {
    /** Veri çekildi (veya çekilen veri yerelle aynıydı). */
    data class Completed(
        val source: String,
        val stats: ImportStats,
        val usedDelta: Boolean,
        val durationMs: Long
    ) : ListRefreshOutcome()

    /**
     * Ağa hiç çıkılmadı.
     * @param reason "fresh" (tazelik penceresi), "backoff", "unchanged" (uzak revizyon aynı)
     */
    data class Skipped(
        val source: String,
        val reason: String,
        val lastSuccessAt: Long
    ) : ListRefreshOutcome()

    data class Failed(val source: String, val message: String) : ListRefreshOutcome()

    data class AlreadyRunning(val source: String) : ListRefreshOutcome()
}

/**
 * Kaynak bazlı liste senkron koordinatörü.
 *
 * TÜM liste yenilemeleri (sekme yenile, "Tümü"yü yenile, ayarlardaki içe aktar,
 * uygulama açılışındaki sessiz yenileme, bildirim işçisi) buradan geçer. Böylece:
 *  - Aynı kaynak için eşzamanlı iki istek ağa iki kez çıkmaz (single-flight),
 *  - Her kaynağın kendi tazelik/TTL durumu vardır (bir kaynağı yenilemek diğerlerini
 *    "taze" göstermez),
 *  - Destekleyen kaynaklarda önce FARK (delta) çekilir, olmazsa tam listeye düşülür,
 *  - Değişmeyen kayıtlar veritabanına YAZILMAZ (bkz. [MediaEntryRepository.smartImport]),
 *  - Başarı/başarısızlık kaynak bazında deftere işlenir (arka plan geri çekilmesi).
 */
object SourceListSynchronizer {

    /**
     * Görsel/durum kodlarında kullanılan kanonik kaynak kimlikleri.
     * `MediaEntry.source` ile aynı yazımdadır.
     */
    const val ANILIST = "anilist"
    const val MAL = "mal"
    const val SIMKL = "simkl"
    const val KITSU = "kitsu"
    const val SHIKIMORI = "shikimori"
    const val BANGUMI = "bangumi"

    private val locks = ConcurrentHashMap<String, Mutex>()

    private fun mutexFor(source: String): Mutex =
        locks.getOrPut(source.lowercase()) { Mutex() }

    /**
     * Kaynağı yeniler.
     *
     * @param source [MediaEntry.source] değeri ("anilist", "mal", ...).
     * @param mode istek türü; bkz. [ListRefreshMode].
     * @param supportsDelta kaynak delta sorgusu destekliyor mu (ör. AniList
     *        `updatedAt_greater`, Simkl `sync/activities`).
     * @param fetchFull listenin TAMAMINI getiren askıya alınabilir fonksiyon.
     * @param fetchDelta verilen epoch saniyesinden sonra değişen kayıtları getiren
     *        fonksiyon. `null` ise delta hiç denenmez.
     * @param revisionProbe uzak revizyon imzası (ucuz bir uç nokta). Aynı imza dönerse
     *        liste çekilmeden "değişiklik yok" denir.
     * @param allowDelete TAM çekimde, sunucuda artık olmayan satırlar silinsin mi?
     *        **Delta turunda yok sayılır**: delta yanıtı silinenleri göremez, bu yüzden
     *        "eşleşmeyen = silinmiş" çıkarımı orada yanlış olur. Silme yalnızca tam
     *        uzlaştırmada, [keepAdultRows] ve yarım-yanıt korumasıyla birlikte yapılır.
     * @param keepAdultRows `true` ise +18 satırlar silme adayı sayılmaz (MAL `nsfw=false`).
     */
    suspend fun refresh(
        context: Context,
        repository: MediaEntryRepository,
        source: String,
        mode: ListRefreshMode,
        supportsDelta: Boolean,
        fetchFull: suspend () -> List<MediaEntry>,
        fetchDelta: (suspend (sinceEpochSeconds: Long) -> List<MediaEntry>)? = null,
        revisionProbe: (suspend () -> String?)? = null,
        allowDelete: Boolean = false,
        keepAdultRows: Boolean = false
    ): ListRefreshOutcome {
        val store = ListSyncStateStore(context)
        val state = store.read(source)
        val deltaAvailable = supportsDelta && fetchDelta != null
        // Yerelde bu kaynağa ait HİÇ satır yoksa "zaten taze" ya da "uzak revizyon aynı"
        // denemez: kütüphane temizlenmiş, yedek geri yüklenmiş ya da ilk kurulum yapılmış
        // olabilir. Bu durumda tam çekim zorunludur, yoksa liste boş kalır.
        val localRowCount = repository.countForSource(source)
        val lostLocalData = localRowCount == 0 && state.lastSuccessAt > 0
        val plan = if (lostLocalData) {
            ListRefreshPlan.FULL
        } else {
            ListRefreshPolicy.plan(
                state = state,
                mode = mode,
                supportsDelta = deltaAvailable
            )
        }
        if (plan == ListRefreshPlan.SKIP_UP_TO_DATE) {
            val backingOff = !mode.isUserInitiated &&
                state.failureCount >= ListRefreshPolicy.MAX_CONSECUTIVE_FAILURES
            return ListRefreshOutcome.Skipped(
                source = source,
                reason = if (backingOff) "backoff" else "fresh",
                lastSuccessAt = state.lastSuccessAt
            )
        }

        // Single-flight: aynı kaynak için ikinci istek ağa çıkmaz, ilkini beklemez —
        // kullanıcıya "zaten yenileniyor" demek için hemen döner.
        val mutex = mutexFor(source)
        if (!mutex.tryLock()) return ListRefreshOutcome.AlreadyRunning(source)

        val startedAt = System.currentTimeMillis()
        store.recordAttempt(source, startedAt)

        try {
            // Ucuz revizyon kontrolü: uzak liste hiç değişmemişse tam listeyi çekmeyiz.
            val revision = if (revisionProbe != null) {
                runSyncCatching { revisionProbe.invoke() }.getOrNull()
            } else {
                null
            }
            if (revision != null && revision == state.remoteRevision && localRowCount > 0) {
                val now = System.currentTimeMillis()
                store.recordSuccess(
                    source = source,
                    fullSync = false,
                    remoteRevision = revision,
                    now = now
                )
                return ListRefreshOutcome.Skipped(source, "unchanged", now)
            }

            var usedDelta = false
            val entries: List<MediaEntry> = if (plan == ListRefreshPlan.DELTA && fetchDelta != null) {
                val since = ListRefreshPolicy.deltaSinceEpochSeconds(state.lastSuccessAt)
                val delta = runSyncCatching { fetchDelta.invoke(since) }
                val deltaEntries = delta.getOrNull()
                if (deltaEntries != null) {
                    usedDelta = true
                    deltaEntries
                } else {
                    // Delta uç noktası reddetti (şema/parametre değişikliği, 5xx, kota...):
                    // sessizce tam listeye düş. Kullanıcı yine de taze veriyi görür.
                    android.util.Log.w(
                        "SourceListSynchronizer",
                        "$source delta çekilemedi, tam listeye düşülüyor: ${delta.exceptionOrNull()?.message}"
                    )
                    fetchFull()
                }
            } else {
                fetchFull()
            }

            // Boş yanıt + yerelde veri var → bu "güncel" değil, şüpheli bir yanıttır
            // (401, kota, kesik sayfa). Repository hiçbir şey silmez; biz de deftere
            // "başarılı senkron" yazmayız ki kullanıcı tekrar deneyebilsin ve 6 saatlik
            // tazelik penceresi bu boş yanıtı önbelleğe almasın.
            if (entries.isEmpty() && localRowCount > 0) {
                store.recordFailure(source, "sunucu boş liste döndürdü")
                return ListRefreshOutcome.Failed(
                    source = source,
                    message = "sunucu boş liste döndürdü; yerel liste korundu"
                )
            }

            // Silme yalnızca TAM uzlaştırmada mümkündür: delta yanıtı "uzakta silinmiş"
            // satırları hiç içermez, o yüzden delta turunda eşleşmeyen satır silinemez.
            // Yarım/kesik yanıt koruması, kullanıcı açıkça "baştan yenile" demediği sürece
            // açıktır (ImportReconciler.coverageAllowsDeletion).
            val allowDeleteThisRound = allowDelete && !usedDelta
            val stats = repository.smartImport(
                source = source,
                importedEntries = entries,
                allowDelete = allowDeleteThisRound,
                keepAdultRows = keepAdultRows,
                guardAgainstPartialPayload = allowDeleteThisRound && mode != ListRefreshMode.USER_FORCE,
                // Delta yanıtı tüm listeyi temsil etmez → "sunucuda ne vardı" defteri
                // yalnızca tam turda tazelenir.
                isFullPull = !usedDelta
            )
            val now = System.currentTimeMillis()
            store.recordSuccess(
                source = source,
                fullSync = !usedDelta,
                remoteRevision = revision,
                now = now
            )
            return ListRefreshOutcome.Completed(
                source = source,
                stats = stats,
                usedDelta = usedDelta,
                durationMs = now - startedAt
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            store.recordFailure(source, error.message)
            return ListRefreshOutcome.Failed(
                source = source,
                message = error.message ?: error.javaClass.simpleName
            )
        } finally {
            mutex.unlock()
        }
    }

    /**
     * Test/teşhis: kaynağın defterini sıfırlar (ör. hesap değişince).
     *
     * "Sunucuda en son ne vardı" defteri de temizlenir: yeni hesabın listesi eski hesabın
     * kimlikleriyle karşılaştırılırsa, ilk tam çekimde eski hesaba ait hiçbir satır
     * "sunucuda görülmüş" sayılmaz — yani yanlışlıkla silme olmaz.
     */
    fun resetState(context: Context, source: String? = null) {
        ListSyncStateStore(context).clear(source)
        ServerSnapshotStore(context).clear(source)
    }

    /** Teşhis: kaynağın son senkron zamanı (ms). */
    fun lastSuccessAt(context: Context, source: String): Long =
        ListSyncStateStore(context).read(source).lastSuccessAt
}
