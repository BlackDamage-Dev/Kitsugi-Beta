package com.kitsugi.animelist.data.repository

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.local.MediaEntryRepository
import com.kitsugi.animelist.data.local.toDomain
import com.kitsugi.animelist.data.remote.KitsugiIdResolver
import com.kitsugi.animelist.data.settings.KitsugiContentPrefs
import com.kitsugi.animelist.model.EntryAutoRules
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaIdentity
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * OYNATICI → LİSTE KÖPRÜSÜ (yazma katmanı).
 *
 * Oynatıcı **hangi kaynaktan** (AniList / MAL / Kitsu / Shikimori / Bangumi / Simkl / TMDB)
 * açılırsa açılsın, oynatılan yapımın yerel listesindeki satırını bulur; yoksa ve kullanıcı
 * izin verdiyse oluşturur; sonra durum/ilerleme/puan değişikliğini **tek bir yerden**
 * `MediaEntryRepository.update` üzerinden yazar.
 *
 * ## Neden ayrı bir sınıf?
 * `MediaEntryRepository.update`, yazdıktan sonra `ExternalListSyncManager.syncEntry` ile
 * altı kaynağa birden yayın yapar (Bölüm: "her kaynağa uyumlu köprü"). Oynatıcı tarafında
 * bu zinciri yeniden kurmak; kimlik çözümü, "Bırakıldı"ya saygı, tarih otomasyonu
 * (`EntryAutoRules`) ve bekleyen-senkron kuyruğu mantığını ikinci kez (ve tutarsız biçimde)
 * yazmak demekti. Burada tek kapı var.
 *
 * ## Satır bulma sırası (kanıt gücüne göre)
 * 1. **Kimlik eşleşmesi** — `MediaIdentity.keys` (MAL, AniList, Kitsu, Shikimori, Bangumi,
 *    TMDB, Simkl uzayları) ve `MediaIdentity.sameMedia` (başlık+yıl son çare).
 *    Oynatıcıdan gelen kimlikler (`malId`, `aniListId`, `tmdbId`) önce
 *    `KitsugiIdResolver` ile zenginleştirilir; böylece TMDB'den açılan bir dizi,
 *    AniList'te tutulan aynı animeyi de bulur.
 * 2. **Başlık eşleşmesi** — kimlik yoksa (ör. yalnızca Cloudstream eklentisinden gelen
 *    başlık) normalize edilmiş Türkçe/İngilizce/Romaji başlık + yıl.
 *
 * ## Yazma kuralları
 * - **İlerleme asla azalmaz** (bayat tick, tamamlanmış kaydı bozamaz).
 * - `Dropped` kayıt otomatik takiple `Watching`'e çevrilmez (ayar).
 * - `syncExternal=false` iken yalnızca yerel yazım yapılır (fragman, çevrimdışı vb.).
 * - Kimlik yalnızca gerçekten kanıtlanmışsa yazılır: uydurma `malId` üretilmez.
 */
object PlaybackListWriter {

    private const val TAG = "PlaybackListWriter"

    /**
     * Oynatıcının bildirdiği içerik kimliği.
     *
     * @param kind `EPISODE` (bölümlü) / `MOVIE` (tek parça) — liste ilerleme kuralını belirler
     * @param mediaType liste türü tahmini (Anime / Movie / TvShow); kaynak çözümü bunu kullanır
     */
    data class PlaybackIdentity(
        val title: String,
        val titleEnglish: String? = null,
        val titleRomaji: String? = null,
        val titleNative: String? = null,
        val synonyms: List<String> = emptyList(),
        val malId: Int? = null,
        val aniListId: Int? = null,
        val tmdbId: Int? = null,
        /** Simkl kimliği (varsa satır doğrudan Simkl uzayında açılır). */
        val simklId: Int? = null,
        /**
         * Bangumi subject kimliği. Oynatıcı Bangumi detayından açıldığında taşınır;
         * `malId` alanına **kararlı** (500M + id) biçimde yazılır ki satır kendi
         * listesinde doğru kimlikle dursun.
         */
        val bangumiId: Int? = null,
        val year: Int? = null,
        val posterUrl: String? = null,
        val mediaType: MediaType? = null,
        val isMovie: Boolean = false,
        val season: Int = 1,
        /** Cloudstream eklentisinin adı — yeni satırın kaynağı için ipucu. */
        val sourceHint: String? = null
    )

    /** Yazım sonucu — UI "neden bir şey olmadı?" sorusunu cevaplayabilsin. */
    sealed interface Result {
        data class Updated(val entry: MediaEntry, val changed: Boolean) : Result
        data class Created(val entry: MediaEntry) : Result
        /** Listede yok ve otomatik ekleme kapalı. */
        data object NotTracked : Result
        /** Ayar kapalı / kimlik gerekli / yazım hatası. */
        data class Skipped(val reason: String) : Result
    }

    /**
     * Listede bu yapımın satırını bulur (yoksa `null`).
     *
     * @param identity oynatıcıdan gelen kimlik
     * @param entries  çağıranın elindeki liste (Room `entriesFlow`); `null` verilirse DB'den okunur
     */
    suspend fun resolveEntry(
        context: Context,
        identity: PlaybackIdentity,
        entries: List<MediaEntry>? = null
    ): MediaEntry? = withContext(Dispatchers.IO) {
        val rows = entries ?: runCatching {
            KitsugiDatabase.getDatabase(context).mediaEntryDao().getAll().map { it.toDomain() }
        }.getOrDefault(emptyList())
        if (rows.isEmpty()) return@withContext null

        val resolved = enrichIdentity(identity)
        // Her sağlayıcı kimliği KENDİ uzayında aranır: AniList satırları offset'li kararlı
        // kimlik (100M + mediaId) taşır, MAL/Kitsu/Shikimori/Bangumi satırları ham ID.
        val probes = buildProbes(identity, resolved)
        val probeKeys = probes.map { MediaIdentity.keys(it) }.filter { it.isNotEmpty() }

        // 1) Kimlik eşleşmesi (tür aynı olmak zorunda).
        val byIdentity = rows.filter { row ->
            if (!typesCompatible(row.type, identity, resolved)) return@filter false
            val rowKeys = MediaIdentity.keys(row)
            probeKeys.any { keys -> keys.intersect(rowKeys).isNotEmpty() }
        }
        // Aynı yapının birden fazla kaynaktaki kopyaları olabilir: en güncel satır kazanır.
        byIdentity.maxByOrNull { it.updatedAt }?.let { return@withContext it }

        // 1b) Simkl/TMDB kimliği yalnızca kendi alanında yaşar (malId'de değil).
        if (resolved.tmdbId != null) {
            rows.filter { it.tmdbId != null && it.tmdbId == resolved.tmdbId }
                .maxByOrNull { it.updatedAt }
                ?.let { return@withContext it }
        }

        // 2) Başlık eşleşmesi (kimlik çözülemediyse son çare). Tür uyumu yine aranır.
        val normalizedTitles = normalizedTitleSet(identity)
        if (normalizedTitles.isEmpty()) return@withContext null
        rows.filter { row ->
            typesCompatible(row.type, identity, resolved) &&
                normalizedTitleSetOf(row).any { it in normalizedTitles } &&
                (identity.year == null || row.year == null || identity.year == row.year)
        }.maxByOrNull { it.updatedAt }
    }

    /**
     * Oynatıcının kimliğine göre yerel satırın kimliğini zenginleştirir (TMDB ⇄ MAL ⇄ AniList).
     * Ağ hataları yutulur: çevrimdışıyken oynatma akışı bloklanmaz.
     */
    private suspend fun enrichIdentity(identity: PlaybackIdentity): EnhancedIdentity {
        if (identity.malId != null && identity.aniListId != null && identity.tmdbId != null) {
            return EnhancedIdentity(identity.malId, identity.aniListId, identity.tmdbId)
        }
        return runCatching {
            val resolved = KitsugiIdResolver.resolveIds(
                malId = identity.malId,
                aniListId = identity.aniListId,
                tmdbId = identity.tmdbId,
                mediaType = identity.mediaType ?: if (identity.isMovie) MediaType.Movie else MediaType.Anime
            )
            EnhancedIdentity(
                malId = identity.malId ?: resolved.malId,
                aniListId = identity.aniListId ?: resolved.aniListId,
                tmdbId = identity.tmdbId ?: resolved.tmdbId
            )
        }.getOrElse {
            Log.d(TAG, "Kimlik zenginleştirme atlandı: ${it.message}")
            EnhancedIdentity(identity.malId, identity.aniListId, identity.tmdbId)
        }
    }

    private data class EnhancedIdentity(
        val malId: Int?,
        val aniListId: Int?,
        val tmdbId: Int?
    )

    /**
     * Eşleştirme için sentetik (asla yazılmayan) satırlar: her biri yalnızca **tek** sağlayıcı
     * kimliği taşır, böylece `MediaIdentity.keys` o kimliği doğru uzayda üretir.
     */
    private fun buildProbes(identity: PlaybackIdentity, resolved: EnhancedIdentity): List<MediaEntry> {
        val type = identity.mediaType ?: if (identity.isMovie) MediaType.Movie else MediaType.Anime
        fun probe(source: String, id: Int?) = MediaEntry(
            id = 0,
            title = identity.title,
            type = type,
            status = WatchStatus.Planned,
            score = null,
            progress = 0,
            total = null,
            source = source,
            malId = id,
            year = identity.year,
            titleEnglish = identity.titleEnglish,
            titleJapanese = identity.titleNative
        )
        val probes = mutableListOf<MediaEntry>()
        resolved.malId?.let { probes.add(probe("mal", it)) }
        // Bangumi subject kimliği: `MediaIdentity` ham (1..99M) değeri bangumi uzayında okur.
        identity.bangumiId?.takeIf { it > 0 }?.let { id ->
            if (probes.none { it.source == "bangumi" && it.malId == id }) {
                probes.add(probe("bangumi", id))
            }
        }
        // Simkl kimliği `malId` alanında değil kendi kolonunda yaşar; ayrı satır olarak denenir.
        identity.simklId?.takeIf { it > 0 }?.let { id ->
            probes.add(
                MediaEntry(
                    id = 0,
                    title = identity.title,
                    type = type,
                    status = WatchStatus.Planned,
                    score = null,
                    progress = 0,
                    total = null,
                    source = "simkl",
                    simklId = id
                )
            )
        }
        resolved.aniListId?.let { probes.add(probe("anilist", 100_000_000 + it)) }
        // Ham (zenginleştirilmemiş) kimlikler de denenir: çevrimdışıyken çözümleme boş döner
        // ama oynatıcıdan gelen kimlik yine de satırı bulabilir.
        identity.malId?.takeIf { it in 1..99_999_999 }?.let { id ->
            if (probes.none { it.malId == id && it.source == "mal" }) probes.add(probe("mal", id))
        }
        identity.aniListId?.let { id ->
            if (probes.none { it.malId == 100_000_000 + id }) probes.add(probe("anilist", 100_000_000 + id))
        }
        return probes
    }

    private fun typesCompatible(
        rowType: MediaType,
        identity: PlaybackIdentity,
        resolved: EnhancedIdentity
    ): Boolean {
        val requested = identity.mediaType
        if (requested == null) return true
        if (rowType == requested) return true
        // Anime ↔ Movie/TvShow köprüsü: aynı yapım hem AniList'te (Anime) hem TMDB/Simkl'de
        // (Movie/TvShow) durabilir. Kimlik kanıtı varsa tür farkı eşleşmeyi engellemez.
        val isVideoPair = requested in setOf(MediaType.Movie, MediaType.TvShow) && rowType == MediaType.Anime
        return isVideoPair && (resolved.malId != null || resolved.aniListId != null || resolved.tmdbId != null)
    }

    private fun normalizedTitleSet(identity: PlaybackIdentity): Set<String> =
        listOfNotNull(identity.title, identity.titleEnglish, identity.titleRomaji, identity.titleNative)
            .plus(identity.synonyms)
            .map { it.trim() }
            .filter { it.length >= 2 }
            .map { MediaIdentity.normalizedTitle(it) }
            .filter { it.isNotBlank() }
            .toSet()

    private fun normalizedTitleSetOf(row: MediaEntry): Set<String> =
        listOfNotNull(row.title, row.titleEnglish, row.titleJapanese)
            .map { it.trim() }
            .filter { it.length >= 2 }
            .map { MediaIdentity.normalizedTitle(it) }
            .filter { it.isNotBlank() }
            .toSet()

    /**
     * Listede satır yoksa yeni kayıt üretir (yalnızca otomatik ekleme açıkken çağrılır).
     *
     * Kaynak seçimi: satırın **kendi** kimliği korunur (Kitsu 300M / Shikimori 400M /
     * Bangumi 500M / Simkl 150M aralıkları) — yanlış namespace'e yazılan bir ID, çapraz
     * eşitlemenin yanlış yapımı bulmasına yol açardı.
     */
    suspend fun createEntry(
        context: Context,
        identity: PlaybackIdentity,
        progress: Int,
        status: WatchStatus,
        total: Int?
    ): MediaEntry? = withContext(Dispatchers.IO) {
        val resolved = enrichIdentity(identity)
        val type = identity.mediaType ?: when {
            identity.isMovie -> MediaType.Movie
            resolved.malId != null || resolved.aniListId != null -> MediaType.Anime
            resolved.tmdbId != null -> MediaType.TvShow
            else -> MediaType.Anime
        }
        val connected = connectedSources(context)
        val source = when {
            // Anime/manga uzayı: bağlı ilk anime-manga servisi
            type == MediaType.Anime || type == MediaType.Manga -> when {
                // Oynatma Bangumi'den açıldıysa satır Bangumi uzayında açılır (kullanıcı
                // orada tutuyorsa oraya yazılmalı); diğer kaynaklar çapraz eşitlemeyle alır.
                identity.bangumiId != null && connected.bangumi -> "bangumi"
                resolved.malId != null && connected.mal -> "mal"
                resolved.aniListId != null && connected.aniList -> "anilist"
                connected.aniList -> "anilist"
                connected.mal -> "mal"
                connected.kitsu -> "kitsu"
                connected.shikimori -> "shikimori"
                connected.bangumi -> "bangumi"
                else -> "manual"
            }
            // Film/dizi uzayı: TMDB kimliği varsa satır **tmdb** uzayında açılır. `MediaIdentity`
            // `tmdbId` taşıyan her satırı zaten `tmdb:<id>` anahtarıyla eşliyor; kaynağı `manual`
            // yazmak satırı hiçbir sağlayıcıya yaymaz (TMDB'nin yazma API'si yok, Simkl ise
            // kimliği eşleşen filmleri kendi kütüphanesine yazar → yayma Simkl üzerinden olur).
            identity.tmdbId != null && type != MediaType.Anime && type != MediaType.Manga -> when {
                connected.simkl -> "simkl"
                else -> "tmdb"
            }
            else -> when {
                connected.simkl -> "simkl"
                connected.bangumi -> "bangumi"
                else -> "manual"
            }
        }
        val stableMalId = when (source) {
            "anilist" -> resolved.aniListId?.let { 100_000_000 + it } ?: resolved.malId
            // Bangumi satırı KENDİ subject kimliğini kararlı biçimde taşır; MAL kimliği
            // yazılsaydı satır Bangumi listesinde yanlış yapımı gösterirdi.
            "bangumi" -> identity.bangumiId?.let { 500_000_000 + it }
                ?: resolved.malId
            "kitsu" -> resolved.malId
            "shikimori" -> resolved.malId
            else -> resolved.malId
        }
        val auto = EntryAutoRules.resolveOnStatusChange(
            status = status,
            startDate = null,
            endDate = null,
            progress = progress,
            total = total,
            statusChanged = true
        )
        val entry = MediaEntry(
            id = 0,
            title = identity.title.ifBlank { identity.titleEnglish ?: identity.titleRomaji ?: "" },
            subtitle = "",
            type = type,
            status = status,
            score = null,
            progress = auto.progress,
            total = total,
            isFavorite = false,
            isAdult = false,
            source = source,
            malId = stableMalId,
            imageUrl = identity.posterUrl,
            year = identity.year,
            startDate = auto.startDate,
            endDate = auto.endDate,
            tmdbId = resolved.tmdbId ?: identity.tmdbId,
            simklId = identity.simklId,
            titleEnglish = identity.titleEnglish,
            titleJapanese = identity.titleNative
        )
        if (entry.title.isBlank()) return@withContext null
        val repository = repository(context)
        val inserted = runCatching {
            repository.insert(entry, syncExternal = KitsugiContentPrefs.autoTrackSyncSources)
        }.onFailure { Log.e(TAG, "Otomatik ekleme başarısız: ${it.message}", it) }.isSuccess
        if (!inserted) return@withContext null
        // DB'de oluşan GERÇEK satırı döndür: kimlik zenginleşmiş (aniListEntryId geri
        // yazılmış) ve kardeş satır yansıması uygulanmış olabilir.
        resolveEntry(context, identity, entries = null)
    }

    /**
     * Mevcut satırı günceller (durum/ilerleme/puan/tarih). Anlamlı bir değişiklik yoksa
     * ağ isteği üretmemek için `changed=false` döner.
     */
    suspend fun updateEntry(
        context: Context,
        entry: MediaEntry,
        status: WatchStatus? = null,
        progress: Int? = null,
        score: Int? = null,
        total: Int? = null,
        startDate: String? = null,
        endDate: String? = null,
        syncExternal: Boolean = true
    ): Result = withContext(Dispatchers.IO) {
        val targetStatus = status ?: entry.status
        // İlerleme yalnızca ileri gider; toplam biliniyorsa toplamı aşmaz.
        val targetProgress = progress?.let { requested ->
            val merged = maxOf(requested, entry.progress)
            val cap = (total ?: entry.total)?.takeIf { it > 0 }
            if (cap != null) merged.coerceAtMost(cap) else merged
        } ?: entry.progress
        val auto = EntryAutoRules.resolveOnStatusChange(
            status = targetStatus,
            startDate = startDate ?: entry.startDate,
            endDate = endDate ?: entry.endDate,
            progress = targetProgress,
            total = total ?: entry.total,
            statusChanged = targetStatus != entry.status
        )
        val updated = entry.copy(
            status = targetStatus,
            progress = auto.progress,
            score = score ?: entry.score,
            total = total ?: entry.total,
            startDate = auto.startDate,
            endDate = auto.endDate
        )
        val unchanged = updated.copy(updatedAt = entry.updatedAt) == entry
        if (unchanged) return@withContext Result.Updated(entry, changed = false)
        val repository = repository(context)
        runCatching { repository.update(updated, syncExternal = syncExternal) }
            .onFailure {
                if (it is CancellationException) throw it
                Log.e(TAG, "Liste güncellemesi başarısız: ${it.message}", it)
                return@withContext Result.Skipped("update_failed")
            }
        Result.Updated(updated, changed = true)
    }

    /**
     * Puanı yazar (hızlı puanlama çubuğundan). `0`/`null` puanı **temizler**
     * (`MediaEntry.score = null` = puanlanmadı; 0 puan olarak gönderilmez).
     */
    suspend fun rateEntry(
        context: Context,
        entry: MediaEntry,
        score: Int?,
        syncExternal: Boolean = true
    ): Result = updateEntry(
        context = context,
        entry = entry,
        score = score?.takeIf { it in 1..10 },
        syncExternal = syncExternal
    )

    // ─── Yardımcılar ─────────────────────────────────────────────────────────────

    private suspend fun repository(context: Context): MediaEntryRepository {
        val db = KitsugiDatabase.getDatabase(context)
        return MediaEntryRepository(
            dao = db.mediaEntryDao(),
            pendingSyncDao = db.pendingSyncDao(),
            context = context.applicationContext
        )
    }

    private data class Connected(
        val aniList: Boolean,
        val mal: Boolean,
        val kitsu: Boolean,
        val shikimori: Boolean,
        val simkl: Boolean,
        val bangumi: Boolean
    )

    private suspend fun connectedSources(context: Context): Connected = Connected(
        aniList = !ExternalAuthManager.getAniListToken(context).isNullOrBlank(),
        mal = !ExternalAuthManager.getMalToken(context).isNullOrBlank(),
        kitsu = !ExternalAuthManager.getKitsuToken(context).isNullOrBlank(),
        shikimori = !ExternalAuthManager.getShikimoriToken(context).isNullOrBlank(),
        simkl = !ExternalAuthManager.getSimklToken(context).isNullOrBlank(),
        bangumi = com.kitsugi.animelist.data.auth.BangumiAuthStore.getToken(context).isNullOrBlank().not()
    )
}
