package com.kitsugi.animelist.data.auth

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.data.remote.SimklApiClient
import com.kitsugi.animelist.data.remote.SimklSyncContract
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.R
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.kitsugi.animelist.data.local.toEntity

/**
 * Simkl ↔ Kitsugi uygulaması arasında çift yönlü senkronizasyon yöneticisi.
 *
 * Referans mimariler:
 * - NyanTV/SimklService.kt → updateEntry, fetchUserMovies/Shows, status mapping
 * - scrob → history/sync geri yazma (POST /sync/history)
 * - SyncMeta → dry-run, diagnostics, concurrency kontrolü
 * - Showly → Trakt = Simkl katmanı, TMDB = metadata katmanı ayrımı
 */
object SimklSyncManager {

    private const val TAG = "SimklSyncManager"
    private val simklApiClient = SimklApiClient()

    // ── Durum Dönüşüm Yardımcıları (NyanTV simklStatusToAL referans) ────────────

    /** Simkl status string'ini uygulama WatchStatus'una çevirir */
    fun simklStatusToWatchStatus(simklStatus: String?): WatchStatus = when (simklStatus?.lowercase()) {
        "watching", "reading", "rewatching" -> WatchStatus.Watching
        "completed"                         -> WatchStatus.Completed
        "hold", "on_hold", "paused"         -> WatchStatus.Paused
        "dropped", "notinteresting"         -> WatchStatus.Dropped
        "plantowatch", "plan_to_watch", "planning" -> WatchStatus.Planned
        else                                -> WatchStatus.Planned
    }

    /**
     * Uygulama WatchStatus'unu Simkl status string'ine çevirir.
     *
     * ÖNEMLİ: Simkl'in `/sync/add-to-list` sözleşmesinde izinli `to` değerleri
     * `watching | plantowatch | completed | notinteresting | hold` şeklindedir —
     * `dropped` GEÇERLİ DEĞİLDİR (bırakılanlar `notinteresting` altında tutulur).
     * Eskiden "dropped" gönderiliyordu; Simkl isteği reddediyor ve bırakılan kayıt
     * hiçbir listeye girmiyordu. MAL-Sync `_provider/Simkl/helper.ts` de aynı eşlemeyi
     * kullanır. Tekrar (rewatch) Simkl'de DESTEKLENMEZ: "watching" olarak yazılır.
     */
    fun watchStatusToSimkl(status: WatchStatus?, mediaType: MediaType? = null): String {
        val s = when (status) {
            WatchStatus.Watching, WatchStatus.Repeating -> "watching"
            WatchStatus.Completed -> "completed"
            WatchStatus.Paused    -> "hold"
            WatchStatus.Dropped   -> "notinteresting"
            WatchStatus.Planned   -> "plantowatch"
            else                  -> "plantowatch"
        }
        return if (mediaType == MediaType.Movie) {
            when (s) {
                "watching", "completed" -> "completed"
                "hold", "plantowatch"   -> "plantowatch"
                "notinteresting"        -> "notinteresting"
                else                    -> "plantowatch"
            }
        } else {
            s
        }
    }

    /** MediaType'a göre Simkl API tipini döner */
    fun mediaTypeToSimklType(mediaType: MediaType): String = when (mediaType) {
        MediaType.Movie  -> "movies"
        MediaType.TvShow -> "shows"
        MediaType.Anime  -> "anime" // Logical category; write envelopes use "shows".
        else             -> "shows"
    }

    /**
     * Yayılımın kullanacağı gerçek MAL kimliği: önce çapraz kimlik katmanının DOĞRULANMIŞ
     * sonucu ([knownMalId]), yoksa satırın kendi `malId` alanı KAYNAK uzayına göre yorumlanır.
     *
     * Neden aralık kontrolü (`0 < id < 100M`) yetmiyor: (a) eski Kitsu/Bangumi ham kimlikleri ve
     * film/dizi satırlarındaki TMDB uzay değerleri aynı aralığa düşüp MAL sanılabiliyor;
     * (b) tersine, çapraz eşitlemenin çözdüğü gerçek MAL değeri `source = "shikimori"` gibi
     * kaynaklara sahip geçici kopyaya yazılıyor ve saf [CrossProviderIds.decode] bunu kaynağın
     * kendi ham kimliği sanıp MAL'i **düşürüyordu**. İkisi de yanlış: ilki yanlış yapıma yazar,
     * ikincisi doğru yazımı atlar. bkz. [CrossProviderIds.decode].
     */
    private fun realMalIdOf(entry: MediaEntry, knownMalId: Int? = null): Int? =
        CrossProviderIds.realMalIdOf(knownMalId) ?: CrossProviderIds.decode(
            storedId = entry.malId,
            source = entry.source,
            type = entry.type,
            entrySimklId = entry.simklId,
            entryTmdbId = entry.tmdbId
        ).malId

    /**
     * Simkl'e gönderilecek Kitsu kimliği: 300M stableId penceresi (300_000_001..399_999_999)
     * ya da eski Kitsu satırlarının ham (<100M) kimliği. Eski canlı kontrol `>= 300M` idi;
     * Shikimori (400M) stableId'leri de bu aralığa girip `id - 300M` ile SAHTE Kitsu kimliğine
     * dönüyordu → Simkl her yazmada `not_found` üretiyordu (toplu hatta 2026-10-07'de
     * düzeltilen aynı hata canlı hatta kalmıştı).
     */
    private fun rawKitsuIdOf(entry: MediaEntry): Int? {
        val stored = entry.malId ?: return null
        return when {
            stored in 300_000_001..399_999_999 -> stored - 300_000_000
            entry.source == "kitsu" && stored in 1..99_999_999 -> stored
            else -> null
        }
    }

    // ── Toplu Senkronizasyon (Batch Sync) ──────────────────────────────────────────

    /**
     * Çoklu MediaEntry listesini Simkl API'sine tek veya az sayıda toplu istek ile gönderir.
     * Uses conservative 35-item batches; delays here do not serialize other callers.
     *
     * Sonuç kayıt bazında raporlanır: Simkl'in `not_found` ile geri gönderdiği öğeler
     * [SyncResult.unmatchedTitles] içinde, HTTP/ağ seviyesinde hiç yazılamayan kayıtlar
     * [SyncResult.failedCount] içinde sayılır. Kısmi eşleşme artık tüm grubun hatası değildir.
     */
    suspend fun syncBatchToSimkl(
        context: Context,
        entries: List<MediaEntry>
    ): SyncResult = withContext(Dispatchers.IO) {
        val token = ExternalAuthManager.getSimklToken(context)
        if (token.isNullOrBlank()) {
            return@withContext SyncResult(
                messages = emptyList(),
                errors = listOf(context.getString(R.string.account_sync_account_not_connected, "Simkl")),
                failedCount = entries.size
            )
        }
        if (entries.isEmpty()) {
            return@withContext SyncResult(messages = emptyList(), errors = emptyList())
        }

        val unsupportedTitles = mutableListOf<String>()
        val unsafeIdentityTitles = mutableListOf<String>()
        val batchItems = entries.mapNotNull { entry ->
            if (entry.type == MediaType.Manga) {
                unsupportedTitles.add(entry.displayTitle(context))
                return@mapNotNull null
            }
            // Ham kütüphane satırlarında (kopya DEĞİL) eski aralık kuralı korunur: Shikimori
            // importu MAL uzayındaki target_id'yi ham yazar (<100M) ve toplu hat bu değeri
            // Simkl'e `ids.mal` olarak göndermelidir; [CrossProviderIds.decode] ise kaynak
            // duyarlılığı gereği onu shikimori sayar. Canlı hat, çapraz kimlik kopyalarıyla
            // çalıştığı için [realMalIdOf] (knownMalId + decode) kullanır.
            // GÜVENLİK (2026-11-02): salt aralık kontrolü eski KITSU satırlarının ham kimliğini
            // (<100M) de MAL ID sanıp Simkl'e yanlış anime kimliği gönderiyordu. Artık kaynak
            // duyarlı decode kullanılır: kitsu satırında kitsuId, malId olarak asla çıkmaz.
            val decodedIds = CrossProviderIds.decode(
                storedId = entry.malId,
                source = entry.source,
                type = entry.type,
                entrySimklId = entry.simklId,
                entryTmdbId = entry.tmdbId
            )
            val realMalId = decodedIds.malId
                ?: decodedIds.shikimoriId?.takeIf { entry.type == MediaType.Anime }
            val aniListId = if (entry.source == "anilist" && entry.malId != null && entry.malId >= 100_000_000 && entry.malId < 300_000_000) {
                entry.malId - 100_000_000
            } else null
            // KitsuImportManager encodes IDs with this offset. After cross-sync resolves
            // a real MAL ID, source may still be "kitsu"; never send that MAL ID as kitsu.
            val rawKitsuId = when {
                entry.malId != null && entry.malId in 300_000_001..399_999_999 -> entry.malId - 300_000_000
                entry.source == "kitsu" && entry.malId != null && entry.malId in 1..99_999_999 -> entry.malId
                else -> null
            }
            val simklId = entry.simklId?.takeIf { it > 0 } ?: 0
            val effectiveTitle = entry.titleEnglish?.takeIf { it.isNotBlank() } ?: entry.title

            // GÜVENLİK: Anime için güvenilir kimlik yalnızca Simkl veya MAL kimliğidir. İkisi de yokken
            // başlık/yıl ya da TMDB ile eşleştirme, aynı adlı dizileri/filmleri veya franchise'ın başka
            // sezonunu kullanıcının listesine ekleyebiliyordu. Bu kayıtlar kimliksiz gönderilmez;
            // yalnızca AniList/Kitsu kimliği varsa başlık eşleştirmesi kapalı olarak denenir.
            val isAnime = entry.type == MediaType.Anime
            val hasSafeAnimeIdentity = simklId > 0 || realMalId != null
            val hasSecondaryAnimeIdentity = aniListId != null || rawKitsuId != null
            val hasNonAnimeIdentity = simklId > 0 || (entry.tmdbId != null && entry.tmdbId > 0) || effectiveTitle.isNotBlank()

            when {
                isAnime && !hasSafeAnimeIdentity && !hasSecondaryAnimeIdentity -> {
                    unsafeIdentityTitles.add(entry.displayTitle(context))
                    null
                }
                !isAnime && !hasNonAnimeIdentity -> {
                    unsupportedTitles.add(entry.displayTitle(context))
                    null
                }
                else -> SimklApiClient.SimklBatchEntry(
                    type = mediaTypeToSimklType(entry.type),
                    status = watchStatusToSimkl(entry.status, entry.type),
                    simklId = simklId,
                    malId = realMalId,
                    tmdbId = if (isAnime) null else entry.tmdbId,
                    aniListId = aniListId,
                    kitsuId = rawKitsuId,
                    title = effectiveTitle,
                    year = entry.year,
                    progress = entry.progress,
                    total = entry.total,
                    score = entry.score,
                    titleMatchingAllowed = !isAnime || hasSafeAnimeIdentity
                )
            }
        }

        val messages = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val unmatchedTitles = mutableListOf<String>()
        val unsupportedProgressTitles = mutableListOf<String>()
        val historyUnmatchedTitles = mutableListOf<String>()
        val ratingUnmatchedTitles = mutableListOf<String>()
        var totalAdded = 0
        // Güvenli kimliği olmadığı için gönderilmeyen animeler "bulunamadı/atlandı" sayılır (hata değil).
        var totalNotFound = unsafeIdentityTitles.size
        var totalFailed = unsupportedTitles.size
        if (unsupportedTitles.isNotEmpty()) {
            warnings.add(context.getString(R.string.simkl_sync_unsupported_warning, unsupportedTitles.size, unsupportedTitles.take(5).joinToString()))
        }
        if (unsafeIdentityTitles.isNotEmpty()) {
            unmatchedTitles.addAll(unsafeIdentityTitles)
            warnings.add(context.getString(R.string.simkl_sync_unsafe_identity_warning, unsafeIdentityTitles.size, unsafeIdentityTitles.take(5).joinToString()))
        }

        // Conservative batch size; not a claim about a documented API maximum.
        val chunks = batchItems.chunked(35)
        for ((idx, chunk) in chunks.withIndex()) {
            val groupLabel = if (chunks.size > 1) context.getString(R.string.simkl_sync_group_label, idx + 1, chunks.size) else context.getString(R.string.simkl_sync_group_label_single)
            try {
                // History/ratings can change the watchlist status, so apply the intended status LAST.
                val withProgress = chunk.filter { it.progress > 0 }
                val tvProgress = withProgress.filter { it.type == "shows" }
                if (tvProgress.isNotEmpty()) {
                    unsupportedProgressTitles.addAll(tvProgress.map { it.title ?: context.getString(R.string.simkl_sync_untitled) })
                }
                val supportedProgress = withProgress.filter { it.type != "shows" }
                if (supportedProgress.isNotEmpty()) {
                    val historyRes = simklApiClient.historyBatchReceipt(token, supportedProgress)
                    if (historyRes.transportFailed) {
                        warnings.add(context.getString(R.string.simkl_sync_history_write_failed, groupLabel, historyRes.errorMessage ?: context.getString(R.string.account_sync_unknown_error)))
                    } else if (historyRes.notFoundCount > 0) {
                        historyUnmatchedTitles.addAll(describeUnmatched(context, historyRes.notFoundItems, supportedProgress))
                    }
                    kotlinx.coroutines.delay(1200L)
                }
                val withScore = chunk.filter { it.score != null && it.score > 0 }
                if (withScore.isNotEmpty()) {
                    val ratingRes = simklApiClient.ratingsBatchReceipt(token, withScore)
                    if (ratingRes.transportFailed) {
                        warnings.add(context.getString(R.string.simkl_sync_ratings_write_failed, groupLabel, ratingRes.errorMessage ?: context.getString(R.string.account_sync_unknown_error)))
                    } else if (ratingRes.notFoundCount > 0) {
                        ratingUnmatchedTitles.addAll(describeUnmatched(context, ratingRes.notFoundItems, withScore))
                    }
                    kotlinx.coroutines.delay(1200L)
                }
                val batchRes = simklApiClient.addToListBatchDetailed(token, chunk)
                when {
                    batchRes.transportFailed -> {
                        totalFailed += chunk.size
                        errors.add(context.getString(R.string.simkl_sync_batch_write_failed, groupLabel, batchRes.errorMessage ?: context.getString(R.string.account_sync_unknown_error), chunk.size))
                    }
                    else -> {
                        totalAdded += batchRes.addedCount
                        totalNotFound += batchRes.notFoundCount
                        if (batchRes.notFoundCount > 0) {
                            unmatchedTitles.addAll(describeUnmatched(context, batchRes.notFoundItems, chunk))
                        }
                        val suffix = if (batchRes.notFoundCount > 0) context.getString(R.string.simkl_sync_not_found_suffix, batchRes.notFoundCount) else "."
                        messages.add(context.getString(R.string.simkl_sync_batch_receipt, groupLabel, batchRes.addedCount, suffix))
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: com.kitsugi.animelist.data.repository.SimklAuthException) {
                throw e
            } catch (e: Exception) {
                totalFailed += chunk.size
                errors.add(context.getString(R.string.simkl_sync_group_error, groupLabel, e.message ?: e.javaClass.simpleName, chunk.size))
            }
            if (idx < chunks.size - 1) {
                // Rate limit (1 req/sn) aşmamak için bekle
                kotlinx.coroutines.delay(1200L)
            }
        }

        if (historyUnmatchedTitles.isNotEmpty()) {
            warnings.add(context.getString(R.string.simkl_sync_history_unmatched, historyUnmatchedTitles.size, historyUnmatchedTitles.take(5).joinToString()))
        }
        if (ratingUnmatchedTitles.isNotEmpty()) {
            warnings.add(context.getString(R.string.simkl_sync_rating_unmatched, ratingUnmatchedTitles.size, ratingUnmatchedTitles.take(5).joinToString()))
        }

        SyncResult(
            messages = messages,
            errors = errors,
            addedCount = totalAdded,
            notFoundCount = totalNotFound,
            failedCount = totalFailed,
            warnings = warnings,
            unmatchedTitles = unmatchedTitles.distinct(),
            unsupportedProgressTitles = unsupportedProgressTitles.distinct()
        )
    }

    private fun MediaEntry.displayTitle(context: Context): String =
        titleEnglish?.takeIf { it.isNotBlank() } ?: title.ifBlank { context.getString(R.string.simkl_sync_untitled) }

    /**
     * Simkl'in `not_found` ile geri gönderdiği kimlikleri gönderilen kayıtlarla eşleştirir;
     * kimlik eşleşmezse Simkl'in yankıladığı başlık/kimlik metni kullanılır.
     */
    private fun describeUnmatched(
        context: Context,
        unmatched: List<SimklSyncContract.UnmatchedItem>,
        sent: List<SimklApiClient.SimklBatchEntry>
    ): List<String> {
        if (unmatched.isEmpty()) return emptyList()
        fun normalized(value: String?): String = value.orEmpty().lowercase().filter { it.isLetterOrDigit() }
        return unmatched.map { item ->
            val match = sent.firstOrNull { entry ->
                val simkl = item.ids["simkl"]?.toIntOrNull()
                val mal = item.ids["mal"]?.toIntOrNull()
                val tmdb = item.ids["tmdb"]?.toIntOrNull()
                val anilist = item.ids["anilist"]?.toIntOrNull()
                val kitsu = item.ids["kitsu"]?.toIntOrNull()
                (simkl != null && simkl > 0 && simkl == entry.simklId) ||
                    (mal != null && mal == entry.malId) ||
                    (tmdb != null && tmdb == entry.tmdbId) ||
                    (anilist != null && anilist == entry.aniListId) ||
                    (kitsu != null && kitsu == entry.kitsuId) ||
                    (item.ids.isEmpty() && !item.title.isNullOrBlank() && normalized(item.title) == normalized(entry.title))
            }
            if (match != null) {
                val idText = listOfNotNull(
                    match.malId?.let { "mal=$it" },
                    match.simklId.takeIf { it > 0 }?.let { "simkl=$it" },
                    match.tmdbId?.let { "tmdb=$it" },
                    match.aniListId?.let { "anilist=$it" },
                    match.kitsuId?.let { "kitsu=$it" }
                ).joinToString(", ")
                buildString {
                    append(match.title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.simkl_sync_untitled))
                    match.year?.takeIf { it > 0 }?.let { append(" ($it)") }
                    if (idText.isNotBlank()) append(" [").append(idText).append("]")
                }
            } else {
                item.describe()
            }
        }
    }

    // ── Tek Entry Senkronizasyonu ─────────────────────────────────────────────────

    /**
     * Uygulama listesindeki bir MediaEntry'yi Simkl'e yazar.
     * Simkl bağlı değilse işlem yapılmaz.
     * Referans: NyanTV/SimklService.kt updateEntry()
     */
    suspend fun syncEntryToSimkl(
        context: Context,
        entry: MediaEntry,
        /**
         * Çapraz kimlik katmanının ([CrossProviderIdentityResolver]) önceden çözdüğü gerçek MAL
         * kimliği. Verildiğinde satırın kaynak uzayına göre yeniden yorumlanmaz — özellikle
         * `source = "shikimori"` vb. satırların çözülmüş MAL kopyası ham kimlik sanılıp
         * düşürülmesin. bkz. [realMalIdOf].
         */
        knownMalId: Int? = null,
        /**
         * Yerel satırda DEĞİŞEN alanlar (bkz. [SyncField]). Simkl'de desteklenmeyen alanlar
         * (tarih, cilt, tekrar sayısı) hiç gönderilmez; puan yalnızca değiştiyse yazılır —
         * aksi halde yerel puan boşken `removeRating` çağrılıp **Simkl puanı siliniyordu**.
         */
        fields: Set<SyncField> = SyncField.ALL,
        /** Tam yazım mı? Süzme sonrası kümeden anlaşılamaz; boş puan silinmez. */
        fullWrite: Boolean = fields == SyncField.ALL
    ): SyncResult = withContext(Dispatchers.IO) {
        val token = ExternalAuthManager.getSimklToken(context)
        if (token.isNullOrBlank()) {
            return@withContext SyncResult(messages = emptyList(), errors = listOf(context.getString(R.string.account_sync_account_not_connected, "Simkl")))
        }

        var simklId = entry.simklId
        val realMalId = realMalIdOf(entry, knownMalId)
        val aniListId = if (entry.source == "anilist" && entry.malId != null && entry.malId >= 100_000_000 && entry.malId < 300_000_000) {
            entry.malId - 100_000_000
        } else null
        val rawKitsuId = rawKitsuIdOf(entry)

        // Simkl ID yoksa HER durumda çözülür (kimliklerle birebir sorgu en güvenilir yoldur).
        // Eski koşul tersine çalışıyordu: yalnızca MAL/TMDB kimliği YOKKEN arama yapılıyor,
        // kimlik varken simklId boş kalıyordu. Aşağıdaki bölüm ilerlemesi ve puan adımları
        // effectiveSimklId > 0 istediği için çapraz eşitlemede Simkl'e yalnızca durum gidiyor,
        // kullanıcının seçtiği bölüm ve puan sessizce düşüyordu.
        if (simklId == null || simklId <= 0) {
            val resolvedId = simklApiClient.lookupSimklId(
                malId = realMalId,
                tmdbId = entry.tmdbId,
                aniListId = aniListId,
                title = entry.titleEnglish ?: entry.title,
                year = entry.year,
                mediaType = entry.type
            )
            if (resolvedId != null && resolvedId > 0) {
                simklId = resolvedId
                // Geri yazım HER ZAMAN güncel DB satırı üzerinden yapılır: çağıran taraf
                // (ExternalListSyncManager) buraya malId alanı gerçek MAL kimliğiyle
                // değiştirilmiş geçici bir kopya verebilir; o kopyayı olduğu gibi yazmak
                // satırın kendi kaynak kimliğini (ör. Kitsu 300M stableId) eziyordu.
                if (entry.id > 0) {
                    runCatching {
                        val dao = com.kitsugi.animelist.data.local.KitsugiDatabase.getDatabase(context).mediaEntryDao()
                        val current = dao.getById(entry.id)
                        if (current != null && (current.simklId == null || current.simklId <= 0)) {
                            dao.update(current.copy(simklId = resolvedId))
                        }
                    }
                }
            }
        }

        // Hiçbir kimlik yoksa senkronize edilemez — hata değil, güvenli atlama:
        // katalog eşleşmesi üretilemeden yazma denemesi Retry kuyruğunu şişirir.
        if ((simklId == null || simklId <= 0) && realMalId == null && (entry.tmdbId == null || entry.tmdbId <= 0)) {
            return@withContext SyncResult(
                messages = emptyList(),
                errors = listOf(context.getString(R.string.account_sync_missing_external_ids, entry.title)),
                unresolvedMedia = true
            )
        }

        val effectiveSimklId = simklId ?: 0

        val type = mediaTypeToSimklType(entry.type)
        val simklStatus = watchStatusToSimkl(entry.status, entry.type)
        val messages = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var unresolvedMedia = false

        // Sıra (toplu hatla aynı): geçmiş/puan yazımları Simkl'de liste durumunu
        // değiştirebildiği için istenen durum EN SON uygulanır. Eski sıra durumu önce
        // yazıyordu; bölüm işaretlemesi kaydı Simkl'de yeniden "watching"e çekebiliyordu.

        // 1. Bölüm ilerlemesi (yalnızca anime): 1..N bölümlerin tamamı işaretlenir.
        // Eski canlı hat `S01E<N>` uyduruyordu — toplu hattın özellikle yasakladığı biçim;
        // anime kayıtlarında reddediliyor ya da yanlış bölüm işaretleniyordu. Dizi (TV)
        // ilerlemesi sezon kataloğu olmadan gönderilmez (toplu hatla aynı kural, sessiz atlama).
        val progress = entry.progress
        val writeAll = fullWrite
        // İlerleme değişmediyse geçmiş yazımı atlanır; ancak DURUM değiştiyse (ör. "Tamamlandı")
        // Simkl'in kendi geçmişini tutarlı tutmak için bölümler yine işaretlenir.
        val progressDirty = writeAll || SyncField.PROGRESS in fields || SyncField.STATUS in fields
        if (SyncField.PROGRESS in fields && entry.type != MediaType.Anime) {
            warnings.add(context.getString(R.string.simkl_sync_progress_not_supported_for_type, entry.title))
        } else if (SyncField.PROGRESS in fields && progress <= 0) {
            warnings.add(context.getString(R.string.simkl_sync_progress_clear_unsupported, entry.title))
        }
        if (progress > 0 && entry.type == MediaType.Anime && progressDirty) {
            runCatching {
                simklApiClient.historyBatchReceipt(
                    token,
                    listOf(
                        SimklApiClient.SimklBatchEntry(
                            type = "anime",
                            status = simklStatus,
                            simklId = effectiveSimklId,
                            malId = realMalId,
                            tmdbId = null,
                            aniListId = aniListId,
                            kitsuId = rawKitsuId,
                            title = entry.titleEnglish?.takeIf { it.isNotBlank() } ?: entry.title,
                            year = entry.year,
                            progress = progress,
                            total = entry.total,
                            titleMatchingAllowed = effectiveSimklId > 0 || realMalId != null
                        )
                    )
                )
            }.onSuccess { historyRes ->
                when {
                    historyRes.isSuccess ->
                        messages.add(context.getString(R.string.simkl_sync_episode_progress_updated, progress))
                    historyRes.transportFailed ->
                        errors.add(context.getString(R.string.simkl_sync_episode_progress_failed))
                    else ->
                        errors.add(context.getString(R.string.simkl_sync_episode_progress_failed))
                }
            }.onFailure { e ->
                errors.add(context.getString(R.string.simkl_sync_episode_progress_error, e.message ?: context.getString(R.string.account_sync_unknown_error)))
                Log.e(TAG, "syncEntryToSimkl bölüm geçmişi hatası", e)
            }
        }

        // 2. Puanı Simkl'e yaz (POST /sync/ratings)
        val score = entry.score
        val scoreChanged = writeAll || SyncField.SCORE in fields
        if (scoreChanged && score != null && score > 0 && effectiveSimklId > 0) {
            val normalizedRating = if (score > 10) {
                kotlin.math.round(score / 10.0).toInt().coerceIn(1, 10)
            } else {
                score.coerceIn(1, 10)
            }
            runCatching {
                simklApiClient.setRating(token, effectiveSimklId, entry.type, normalizedRating)
            }.onSuccess { success ->
                if (success) messages.add(context.getString(R.string.simkl_sync_rating_updated, normalizedRating))
                else errors.add(context.getString(R.string.simkl_sync_rating_failed))
            }.onFailure { e ->
                errors.add(context.getString(R.string.simkl_sync_rating_error, e.message ?: context.getString(R.string.account_sync_unknown_error)))
                Log.e(TAG, "syncEntryToSimkl setRating hatası", e)
            }
        } else if (!fullWrite && SyncField.SCORE in fields && (score == null || score == 0) && effectiveSimklId > 0) {
            // Yalnızca kullanıcı puanı SİLDİYSE kaldırılır. Tam yazımda (ALL) puan boşsa
            // `removeRating` çağırmak Simkl'deki puanı gereksiz yere silerdi.
            // Puan silinmişse kaldır. Yalnız 4xx (zaten puan yok) sessiz sayılır;
            // taşıma/5xx/401 hataları kuyruğa girer ki "puanı sil" işlemi kaybolmasın.
            runCatching {
                simklApiClient.removeRating(token, effectiveSimklId, entry.type)
            }.onFailure { e ->
                Log.e(TAG, "Simkl removeRating hatası", e)
                errors.add(context.getString(R.string.simkl_sync_rating_error, e.message ?: context.getString(R.string.account_sync_unknown_error)))
            }
        } else if (!fullWrite && SyncField.SCORE in fields && (score == null || score == 0)) {
            warnings.add(context.getString(R.string.simkl_sync_rating_clear_missing_id, entry.title))
        }

        // 3. İstenen liste durumu EN SON uygulanır (POST /sync/add-to-list): yukarıdaki
        // geçmiş/puan yazımları Simkl'de durumu değiştirebildiğinden kullanıcının seçtiği
        // durum son sözü söyler (toplu hatla aynı sıra). Makbuz ayrıştırılır: `not_found`
        // (medya Simkl kataloğunda yok) hata değil atlamadır; yalnızca taşıma/HTTP
        // başarısızlıkları gerçek hatadır ve yeniden denemeyi haklı çıkarır.
        // Not Simkl'de liste kaydının alanıdır (`private_memo`) → not değiştiyse add-to-list gerekir.
        // Kullanıcı notu SİLDİYSE boş string gönderilir (Simkl'de not temizlenir); tam yazımda
        // ise yalnız dolu not gönderilir — mevcut not kazara silinmesin.
        val memoArg = when {
            writeAll -> entry.notes?.takeIf { it.isNotBlank() }
            SyncField.NOTES in fields -> entry.notes ?: ""
            else -> null
        }
        val listWriteSkipped = !(writeAll || SyncField.STATUS in fields || memoArg != null)
        if (!listWriteSkipped) runCatching {
            simklApiClient.addToListBatchDetailed(
                token = token,
                entries = listOf(
                    SimklApiClient.SimklBatchEntry(
                        type = type,
                        status = simklStatus,
                        simklId = effectiveSimklId,
                        malId = realMalId,
                        tmdbId = entry.tmdbId,
                        aniListId = aniListId,
                        kitsuId = rawKitsuId,
                        title = entry.titleEnglish?.takeIf { it.isNotBlank() } ?: entry.title,
                        year = entry.year,
                        titleMatchingAllowed = type != "anime" || effectiveSimklId > 0 || realMalId != null,
                        privateMemo = memoArg
                    )
                )
            )
        }.onSuccess { receipt ->
            when {
                receipt.isSuccess ->
                    messages.add(context.getString(R.string.simkl_sync_list_updated, entry.title))
                receipt.transportFailed ->
                    errors.add(
                        listOfNotNull(
                            context.getString(R.string.simkl_sync_list_update_failed, entry.title),
                            receipt.errorMessage?.takeIf { it.isNotBlank() }
                        ).joinToString(" — ")
                    )
                else -> {
                    // Kimlik/katalog eksiği: aynı başlık/yıl ile Simkl'e güvenle yazılamaz.
                    unresolvedMedia = true
                    errors.add(context.getString(R.string.account_sync_media_id_not_found, "Simkl"))
                }
            }
        }.onFailure { e ->
            errors.add(context.getString(R.string.simkl_sync_add_error, e.message ?: context.getString(R.string.account_sync_unknown_error)))
            Log.e(TAG, "syncEntryToSimkl addToList hatası", e)
        }

        // Katalog eşleşmesi üretilemediyse yukarıdaki geçmiş/puan "başarısızlıkları" da aynı
        // kök nedenin (medya Simkl'de yok) belirtileridir: atlanan gerekçeye katılır, hata
        // kuyruğuna girmez. Kuyrukta olduğu sürece her açılışta aynı "hata" yeniden üretiliyordu.
        return@withContext if (unresolvedMedia) {
            SyncResult(
                messages = messages,
                errors = errors.distinct(),
                warnings = warnings.distinct(),
                unresolvedMedia = true
            )
        } else {
            SyncResult(messages = messages, errors = errors, warnings = warnings.distinct())
        }
    }

    /**
     * Entry'yi Simkl'den siler.
     * Referans: NyanTV/SimklService.kt deleteEntry() → POST /sync/history/remove
     */
    suspend fun deleteEntryFromSimkl(
        context: Context,
        entry: MediaEntry,
        /** [syncEntryToSimkl] ile aynı: doğrulanmış çapraz MAL kimliği silmeye de taşınır. */
        knownMalId: Int? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        val token = ExternalAuthManager.getSimklToken(context)
        if (token.isNullOrBlank()) {
            return@withContext SyncResult(messages = emptyList(), errors = listOf(context.getString(R.string.account_sync_account_not_connected, "Simkl")))
        }

        var simklId = entry.simklId
        // Hayalet-koruma: id ÖNCEDEN bağlandıysa kayıt kullanıcının Simkl listesindeydi;
        // bu silmede yeni çözülen id'ye dropped taşımak hiç listeye konmamış öğeye
        // sahte satır yaratabilirdi — yalnız önceden bağlılarda uygulanır.
        val linkedBeforeDelete = (entry.simklId ?: 0) > 0
        val realMalId = realMalIdOf(entry, knownMalId)
        val aniListId = if (entry.source == "anilist" && entry.malId != null && entry.malId >= 100_000_000 && entry.malId < 300_000_000) {
            entry.malId - 100_000_000
        } else null

        if (simklId == null || simklId <= 0) {
            val resolvedId = simklApiClient.lookupSimklId(
                malId = realMalId,
                tmdbId = entry.tmdbId,
                aniListId = aniListId,
                title = entry.titleEnglish ?: entry.title,
                year = entry.year,
                mediaType = entry.type
            )
            if (resolvedId != null && resolvedId > 0) {
                simklId = resolvedId
            }
        }

        if (simklId == null || simklId <= 0) {
            // Aramada da bulunamadı: kayıt Simkl kataloğunda eşleştirilemedi — güvenli
            // atlama (hata değil), aksi halde bekleyen-senkron kuyruğunda sonsuza kadar döner.
            return@withContext SyncResult(
                messages = emptyList(),
                errors = listOf(context.getString(R.string.account_sync_media_id_not_found, "Simkl")),
                unresolvedMedia = true
            )
        }

        val type = mediaTypeToSimklType(entry.type)
        val messages = mutableListOf<String>()
        val errors = mutableListOf<String>()

        val rawKitsuId = rawKitsuIdOf(entry)

        runCatching {
            simklApiClient.removeFromList(
                token = token,
                simklId = simklId,
                type = type,
                malId = realMalId,
                tmdbId = entry.tmdbId,
                aniListId = aniListId,
                kitsuId = rawKitsuId
            )
        }.onSuccess { success ->
            if (success) messages.add(context.getString(R.string.simkl_sync_entry_deleted, entry.title))
            else errors.add(context.getString(R.string.simkl_sync_entry_delete_failed))
        }.onFailure { e ->
            errors.add(context.getString(R.string.simkl_sync_entry_delete_error, e.message ?: context.getString(R.string.account_sync_unknown_error)))
            Log.e(TAG, "deleteEntryFromSimkl hatası", e)
        }

        // 2/3 Puan temizliği — resmî POST /sync/ratings/remove. 4xx "zaten puan yok"
        // sessiz; taşıma/5xx hata olarak kuyruğa döner ki puan-silme kaybolmasın
        // (senkron-puan yolundaki aynı kural).
        runCatching {
            simklApiClient.removeRating(token, simklId, entry.type)
        }.onSuccess { ok ->
            if (!ok) Log.w(TAG, "deleteEntryFromSimkl: removeRating sonucu=false (4xx olabilir)")
        }.onFailure { e ->
            Log.e(TAG, "deleteEntryFromSimkl removeRating hatası", e)
            errors.add(context.getString(R.string.simkl_sync_rating_error, e.message ?: context.getString(R.string.account_sync_unknown_error)))
        }

        // 3/3 Kalan liste-durumu → bırakıldı kovası: resmî "listeden tam-silme" ucu yok
        // (API); ekosistem standardı (MALSync vb.) silmeyi bırakıldıya taşımaktır.
        // İlerleme ve puan yukarıda temizlendi; burada kalan satırın durumu kapatılır.
        // Yalnız linkedBeforeDelete (hayalet-koruma). Makbuz: taşıma hatası yeniden
        // denesin, not_found sessiz atlasın (id bulunduysa bu da nadir).
        // (E81) Durum `watchStatusToSimkl` üzerinden gider: ham "dropped" dizgesi
        // Simkl sözleşmesinde GEÇERSİZDİR (`notinteresting`); sert-kodlu gönderim
        // reddediliyor ve silinen satır Simkl'de eski durumunda kalıyordu.
        if (linkedBeforeDelete) {
            runCatching {
                simklApiClient.addToListBatchDetailed(
                    token = token,
                    entries = listOf(
                        SimklApiClient.SimklBatchEntry(
                            type = type,
                            status = watchStatusToSimkl(WatchStatus.Dropped, entry.type),
                            simklId = simklId,
                            malId = realMalId,
                            tmdbId = entry.tmdbId,
                            aniListId = aniListId,
                            kitsuId = rawKitsuId,
                            titleMatchingAllowed = false
                        )
                    )
                )
            }.onSuccess { receipt ->
                when {
                    receipt.isSuccess -> Log.i(TAG, "deleteEntryFromSimkl: kayıt notinteresting kovasına taşındı")
                    receipt.transportFailed -> {
                        Log.e(TAG, "deleteEntryFromSimkl: kovaya taşıma başarısız=${receipt.errorMessage}")
                        errors.add(context.getString(R.string.simkl_sync_entry_delete_failed))
                    }
                    else -> Log.i(TAG, "deleteEntryFromSimkl: kovaya taşıma atlandı (not_found vb.)")
                }
            }.onFailure { e ->
                Log.e(TAG, "deleteEntryFromSimkl kovaya taşıma hatası", e)
                errors.add(context.getString(R.string.simkl_sync_entry_delete_error, e.message ?: context.getString(R.string.account_sync_unknown_error)))
            }
        }

        SyncResult(messages = messages, errors = errors)
    }

    // ── Simkl → Uygulama İçe Aktarma ─────────────────────────────────────────────

    /**
     * Simkl kullanıcı listesini çekip JikanSearchResult listesi olarak döner.
     * type: "shows", "movies", "anime"
     * Referans: NyanTV/SimklService.kt fetchUserMovies() + fetchUserShows()
     */
    suspend fun fetchSimklWatchlist(
        context: Context,
        type: String = "shows"
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        val token = ExternalAuthManager.getSimklToken(context)
        if (token.isNullOrBlank()) {
            Log.w(TAG, "fetchSimklWatchlist: Simkl token bulunamadı")
            return@withContext emptyList()
        }

        runCatching {
            simklApiClient.getUserWatchlist(token, type)
        }.getOrElse { e ->
            Log.e(TAG, "fetchSimklWatchlist $type hatası: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Simkl kullanıcı profil bilgilerini çeker (kullanıcı adı, avatar vb.).
     * Referans: NyanTV/SimklService.kt fetchUserProfile()
     */
    suspend fun fetchSimklUserProfile(context: Context): SimklUserProfile? = withContext(Dispatchers.IO) {
        val token = ExternalAuthManager.getSimklToken(context)
        if (token.isNullOrBlank()) return@withContext null

        runCatching {
            val json = simklApiClient.getUserProfile(token) ?: return@withContext null
            val account = json.optJSONObject("account")
            val user = json.optJSONObject("user")
            SimklUserProfile(
                id = account?.optString("id"),
                name = user?.optString("name"),
                avatarUrl = user?.optString("avatar")
            )
        }.getOrElse { e ->
            Log.e(TAG, "fetchSimklUserProfile hatası: ${e.message}", e)
            null
        }
    }

    // ── Veri Modelleri ────────────────────────────────────────────────────────────

    data class SyncResult(
        val messages: List<String>,
        val errors: List<String> = emptyList(),
        val addedCount: Int = 0,
        val notFoundCount: Int = 0,
        /** Records that could not be written at all (HTTP/network failure or unsupported payload). */
        val failedCount: Int = 0,
        /** Non-fatal findings: partial history/rating receipts, unsupported items. */
        val warnings: List<String> = emptyList(),
        /** Human-readable titles Simkl echoed back as `not_found` for the list write. */
        val unmatchedTitles: List<String> = emptyList(),
        /** TV shows whose aggregate episode count cannot be mapped to Simkl seasons. */
        val unsupportedProgressTitles: List<String> = emptyList(),
        /**
         * Kayıt Simkl kataloğunda hiç yok ya da hiçbir güvenilir kimlikle (Simkl/MAL/TMDB)
         * eşleştirilemedi. Bu bir API hatası değil, güvenli atlamadır: çağıran taraf kaydı
         * "hata" sayıp bekleyen-senkron kuyruğuna yazmamalıdır (bkz. Kitsu/Bangumi
         * `unresolvedMedia`/`unresolvedSubject` sözleşmeleri). [errors] bu durumda gerekçe
         * metinlerini taşır.
         */
        val unresolvedMedia: Boolean = false
    ) {
        val isSuccess: Boolean get() = errors.isEmpty()
        val hasWarnings: Boolean get() = warnings.isNotEmpty() || (messages.isNotEmpty() && errors.isNotEmpty())
    }

    data class SimklUserProfile(
        val id: String?,
        val name: String?,
        val avatarUrl: String?
    )
}
