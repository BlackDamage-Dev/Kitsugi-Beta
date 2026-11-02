package com.kitsugi.animelist.data.local

import android.content.Context
import com.kitsugi.animelist.data.auth.ConnectedSources
import com.kitsugi.animelist.data.auth.CrossProviderIdentityCache
import com.kitsugi.animelist.data.auth.CrossProviderIdentityResolver
import com.kitsugi.animelist.data.auth.CrossProviderIds
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.data.remote.BangumiLocalMappingCache
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaIdentity
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * BİRLEŞİK KÜTÜPHANE (Listem → Tümü) İÇİN KAYNAKLARARASI KİMLİK KÖPRÜSÜ.
 *
 * ## Kök neden
 * Kitsugi'de `MediaEntry.malId` alanı **satırın kendi kaynağının** kimliğini taşır
 * (AniList 100M, Simkl 150M, Kitsu 300M, Shikimori 400M, Bangumi 500M ofsetleri).
 * [MediaIdentity.keys] bu yüzden her kimliği kaynak uzayına yazar: içe aktarılmış bir
 * Bangumi satırı yalnızca `Anime:bangumi:<subject_id>` üretir, aynı yapımın AniList satırı
 * `Anime:mal:<id>` / `Anime:anilist:<id>` üretir. **Kesişim boş** → birleşik listede iki satır
 * yalnızca BAŞLIK ile eşleşebilir. Bangumi'nin liste API'si `name`/`name_cn` (Çince) döndürdüğü
 * için `Sousou no Frieren` ile `葬送的芙莉莲` hiç eşmez; sonuçta Bangumi'deki her kayıt "Tümü"
 * sekmesinde ayrı bir kart olur ve sayaç şişer (rapor: 786 tekil yapım → 1502 kart).
 *
 * ## Çözüm
 * Çapraz eşitlemenin ZATEN çözüp kalıcı yazdığı iki kimlik kaynağı okunur:
 *  1. [CrossProviderIdentityCache] (`cross_provider_identity_v1`): satır başına altı kimlik.
 *  2. [BangumiLocalMappingCache] (`bangumi_id_mapping`): `bgm_a_<malId> → subject_id`.
 *     Bangumi'nin resmî MAL eşleme ucu olmadığı için tek güvenilir köprü budur; burada **tersine**
 *     çevrilir (`subject_id → malId`). Bir eşleme birden fazla MAL ID'ye çıkıyorsa **kullanılmaz**
 *     (belirsiz eşleme, yanlış yapımı birleştirmektense ayrı bırakmayı yeğleriz).
 *
 * Normal okuma ağa çıkmaz. Yalnız aynı başlık/yıl altında Anime↔Movie/TvShow adayı olan
 * satırlar için isteğe bağlı, hesap bağımsız ARM kimlik çözümü yapılır; başlık tek başına
 * birleştirme kanıtı değildir.
 *
 * Bulunan kimlikler satıra YAZILMAZ; eşitleme anında "ek kimlik anahtarı" olarak verilir. Böylece
 *  • platform sekmeleri ve dış API yazmaları kendi kaynağının kimliğini kullanmaya devam eder,
 *  • `MediaEntry.malId` sözleşmesi (ve [isBangumiListMember] gibi aralık kontrolleri) bozulmaz,
 *  • çözülememiş kayıtlar eski davranışını korur.
 *
 * Bu köprü yalnızca *zaten doğrulanmış* eşleşmeleri taşır: `CrossProviderIds` hiç "aramada ilk
 * sonuç" üretmez, `BangumiLocalMappingCache` ise yalnızca tek ve net başlık eşleşmesinde yazılır.
 */
object MyListIdentityCrosswalk {

    private val bangumiMappingKey = Regex("^bgm_([am])_(\\d+)$")
    private val kitsuMappingKey = Regex("^kitsu_media_.+_([am])_(\\d+)$")

    /**
     * `MediaEntry.id` → o satır için bilinen ek kanonik kimlik anahtarları.
     * Kendi MAL kimliği bulunan satırlar da diğer kaynaklara köprü taşıyabilir; boş harita
     * "köprü gerekmiyor/yok" demektir ve çağıran taraf davranışı değiştirmez.
     */
    suspend fun build(
        context: Context,
        entries: List<MediaEntry>,
        allowNetwork: Boolean = false
    ): Map<Int, Set<String>> =
        buildByEntry(context, entries, allowNetwork).mapKeys { (entry, _) -> entry.id }

    /**
     * [build] ile aynı ek anahtarlar, ama kayıt nesnesinin kendisiyle anahtarlanır.
     * İçe aktarılan kayıtların `id` alanı 0 olabildiği (tüm kaynaklarda aynı değer) için
     * `Map<Int, Set<String>>` burada ÇAKIŞIR — yanlış kayda yanlış ek anahtar atanır ve bu
     * yanlış eşleşmeye yol açar. Kayıt nesnesi (data class) ile anahtarlamak güvenlidir.
     */
    suspend fun buildByEntry(
        context: Context,
        entries: List<MediaEntry>,
        allowNetwork: Boolean = false
    ): Map<MediaEntry, Set<String>> {
        if (entries.isEmpty()) return emptyMap()
        return withContext(Dispatchers.IO) {
            val bangumiSubjectToMal = buildMap {
                runCatching { BangumiLocalMappingCache.snapshot(context) }
                    .getOrNull()
                    ?.let { putAll(reverseBangumiMapping(it)) }
                putAll(readDetailXrefBangumiToMal(context))
            }

            val kitsuMediaToMal = runCatching { ExternalAuthManager.kitsuMediaMappingSnapshot(context) }
                .getOrNull()
                ?.let { reverseKitsuMapping(it) }
                .orEmpty()

            val indexedBundles = runCatching { CrossProviderIdentityCache.snapshotAll(context) }
                .getOrNull()
                ?.let { indexCrossIdentitySnapshot(it) }
                .orEmpty()

            if (bangumiSubjectToMal.isEmpty() && kitsuMediaToMal.isEmpty() && indexedBundles.isEmpty()) {
                return@withContext emptyMap()
            }

            val crossTypeCandidates = if (allowNetwork) crossTypeResolutionCandidates(entries) else emptySet()

            val result = LinkedHashMap<MediaEntry, Set<String>>(entries.size)
            entries.forEach { entry ->
                var ids = lookupIndexedIds(entry, indexedBundles) ?: cachedIds(context, entry)
                if (ids == null && allowNetwork && entry.id in crossTypeCandidates) {
                    ids = try {
                        CrossProviderIdentityResolver.resolve(
                            context = context,
                            entry = entry,
                            connected = ConnectedSources.None,
                            allowNetwork = true,
                            requireAnimeVideoBridge = true
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                }
                if (ids == null && !needsCrosswalk(entry)) return@forEach
                val extras = extraKeysFor(
                    entry = entry,
                    ids = ids,
                    bangumiSubjectToMal = bangumiSubjectToMal,
                    kitsuMediaToMal = kitsuMediaToMal
                )
                if (extras.isNotEmpty()) result[entry] = extras
            }
            result
        }
    }

    /**
     * Network work is limited to rows that could actually be the same anime film/show under
     * different provider type labels. Title/year here only selects candidates for ID lookup;
     * it is never sufficient to merge them (MediaIdentity still requires a shared ID).
     */
    internal fun crossTypeResolutionCandidates(entries: List<MediaEntry>): Set<Int> {
        val buckets = HashMap<String, MutableList<MediaEntry>>()
        entries.forEach { entry ->
            val watchable = entry.type == MediaType.Anime || entry.type == MediaType.Movie || entry.type == MediaType.TvShow
            val year = entry.year
            if (!watchable || year == null) return@forEach
            listOfNotNull(entry.title, entry.titleEnglish, entry.titleJapanese)
                .map(MediaIdentity::normalizedTitle)
                .filter { it.length >= 2 }
                .toSet()
                .forEach { normalized ->
                    buckets.getOrPut("$year:$normalized") { mutableListOf() }.add(entry)
                }
        }
        return buckets.values.flatMapTo(LinkedHashSet()) { bucket ->
            val hasAnime = bucket.any { it.type == MediaType.Anime }
            val hasVideo = bucket.any { it.type == MediaType.Movie || it.type == MediaType.TvShow }
            if (hasAnime && hasVideo) bucket.map { it.id } else emptyList()
        }
    }

    /**
     * Satırın kendi kimliği bir eşitleme eksenine yetiyor mu?
     * Anime/manga için MAL ekseni, film/dizi için TMDB/Simkl ekseni aranır — [MediaIdentity.keys]
     * film/dizi satırlarında MAL uzayı üretmediğinden eksenler türle birlikte gelir.
     */
    internal fun needsCrosswalk(entry: MediaEntry): Boolean {
        val namespaces = MediaIdentity.keys(entry).mapTo(HashSet()) { it.substringBeforeLast(':') }
        val prefix = entry.type.name
        return when (entry.type) {
            MediaType.Anime, MediaType.Manga -> "$prefix:mal" !in namespaces
            else -> "$prefix:tmdb" !in namespaces && "$prefix:simkl" !in namespaces
        }
    }

    private fun lookupIndexedIds(
        entry: MediaEntry,
        indexedBundles: Map<String, CrossProviderIds>
    ): CrossProviderIds? {
        if (indexedBundles.isEmpty()) return null
        val directKey = CrossProviderIds.cacheKey(
            type = entry.type,
            storedId = entry.malId,
            title = entry.title,
            titleEnglish = entry.titleEnglish,
            year = entry.year,
            source = entry.source
        )
        var acc: CrossProviderIds? = indexedBundles[directKey]
        MediaIdentity.keys(entry).forEach { ownKey ->
            if (entry.type != MediaType.Movie && ownKey.contains(":tmdb:")) return@forEach
            val hit = indexedBundles[ownKey] ?: return@forEach
            acc = if (acc == null) hit else mergeUnambiguousBundles(acc!!, hit)
        }
        return acc
    }

    /**
     * Önbellekteki paketleri yapım-bazlı tüm sağlayıcı anahtarlarına göre tersine indeksler.
     * Aynı ikincil anahtara iki çelişen birincil MAL kimliği düşerse o anahtar belirsiz kabul
     * edilerek indeks dışı bırakılır.
     */
    internal fun indexCrossIdentitySnapshot(
        snapshot: Map<String, CrossProviderIds>
    ): Map<String, CrossProviderIds> {
        if (snapshot.isEmpty()) return emptyMap()
        val buckets = LinkedHashMap<String, CrossProviderIds>()
        val ambiguousKeys = HashSet<String>()

        fun putKey(indexKey: String, bundle: CrossProviderIds) {
            if (indexKey in ambiguousKeys) return
            val existing = buckets[indexKey]
            if (existing == null) {
                buckets[indexKey] = bundle
                return
            }
            if (existing.malId != null && bundle.malId != null && existing.malId != bundle.malId) {
                buckets.remove(indexKey)
                ambiguousKeys += indexKey
                return
            }
            buckets[indexKey] = mergeUnambiguousBundles(existing, bundle)
        }

        snapshot.forEach { (storedKey, ids) ->
            val typePrefix = storedKey.substringBefore(':', missingDelimiterValue = "")
                .takeIf { it.isNotBlank() }
                ?: return@forEach
            putKey(storedKey, ids)
            ids.malId?.takeIf { it > 0 }?.let { putKey("$typePrefix:mal:$it", ids) }
            ids.aniListId?.takeIf { it > 0 }?.let { putKey("$typePrefix:anilist:$it", ids) }
            ids.kitsuId?.takeIf { it > 0 }?.let { putKey("$typePrefix:kitsu:$it", ids) }
            ids.shikimoriId?.takeIf { it > 0 }?.let { putKey("$typePrefix:shikimori:$it", ids) }
            ids.bangumiSubjectId?.takeIf { it > 0 }?.let { putKey("$typePrefix:bangumi:$it", ids) }
            ids.simklId?.takeIf { it > 0 }?.let { putKey("$typePrefix:simkl:$it", ids) }
            if (typePrefix == MediaType.Movie.name) {
                ids.tmdbId?.takeIf { it > 0 }?.let { putKey("$typePrefix:tmdb:$it", ids) }
            }
        }
        return buckets
    }

    private fun mergeUnambiguousBundles(a: CrossProviderIds, b: CrossProviderIds): CrossProviderIds =
        CrossProviderIds(
            malId = sameOrSingle(a.malId, b.malId),
            aniListId = sameOrSingle(a.aniListId, b.aniListId),
            kitsuId = sameOrSingle(a.kitsuId, b.kitsuId),
            shikimoriId = sameOrSingle(a.shikimoriId, b.shikimoriId),
            bangumiSubjectId = sameOrSingle(a.bangumiSubjectId, b.bangumiSubjectId),
            tmdbId = sameOrSingle(a.tmdbId, b.tmdbId),
            simklId = sameOrSingle(a.simklId, b.simklId),
            via = a.via + b.via
        )

    private fun sameOrSingle(left: Int?, right: Int?): Int? = when {
        left == null -> right
        right == null -> left
        left == right -> left
        else -> null
    }

    private suspend fun cachedIds(context: Context, entry: MediaEntry): CrossProviderIds? {
        val crossProvider = runCatching {
            // Anahtar, CrossProviderIdentityResolver'ın yazdığıyla birebir aynı olmalı.
            CrossProviderIdentityCache.read(
                context = context,
                key = CrossProviderIds.cacheKey(
                    type = entry.type,
                    storedId = entry.malId,
                    title = entry.title,
                    titleEnglish = entry.titleEnglish,
                    year = entry.year,
                    source = entry.source
                )
            )
        }.getOrNull()?.ids

        // Detay sayfalarının daha eski kalıcı köprüsünü de oku. Bu sayede çevrimdışı
        // açılışta, daha önce doğrulanmış MAL/AniList ↔ TMDB eşlemesi varsa kartlar
        // ağ beklemeden birleşir. Film/dizi için legacy malId alanını MAL sanıp sorgulama.
        val meta = runCatching {
            val dao = KitsugiDatabase.getDatabase(context).mediaMetaCacheDao()
            val byTmdb = entry.tmdbId?.takeIf { it > 0 }?.let { dao.getByTmdbId(it) }
            val byAniList = MediaIdentity.aniListId(entry)?.let { dao.getByAniListId(it) }
            val byMal = if (entry.type == MediaType.Anime || entry.type == MediaType.Manga) {
                MediaIdentity.realMalId(entry)?.let { dao.getByMalId(it) }
            } else null
            val byKitsu = MediaIdentity.kitsuId(entry)?.let { dao.getByKitsuId(it.toString()) }
            (byTmdb ?: byAniList ?: byMal ?: byKitsu)?.let { cached ->
                val ownMal = if (entry.type == MediaType.Anime || entry.type == MediaType.Manga) {
                    MediaIdentity.realMalId(entry)
                } else null
                val ownAniList = MediaIdentity.aniListId(entry)
                val consistent = (ownMal == null || cached.malId == null || ownMal == cached.malId) &&
                    (ownAniList == null || cached.aniListId == null || ownAniList == cached.aniListId)
                if (!consistent) return@let null
                CrossProviderIds(
                    malId = cached.malId,
                    aniListId = cached.aniListId,
                    kitsuId = cached.kitsuId?.toIntOrNull(),
                    tmdbId = cached.tmdbId
                )
            }
        }.getOrNull()
        return crossProvider?.mergedWith(meta) ?: meta
    }

    /**
     * Eşitlemeye katılacak ek anahtarlar. [ids] çapraz kimlik önbelleğinden, [bangumiSubjectToMal]
     * ters çevrilmiş Bangumi eşleme önbelleğinden gelir.
     *
     * Kural: satırın **zaten taşıdığı** bir uzay asla ezilmez. Bir satır kendi `malId` alanında
     * gerçek MAL ID'si taşıyorsa (mal/jikan/manual satırları) oradaki değer birinci eldir; önbellek
     * bayatlamış olabilir. Aynı uzaya ikinci bir değer eklemek, çakışma denetiminde iki farklı ID
     * gibi görünüp o eksen üzerindeki **tüm** birleşmeleri engellerdi; bu filtre bunu önler.
     */
    internal fun extraKeysFor(
        entry: MediaEntry,
        ids: CrossProviderIds?,
        bangumiSubjectToMal: Map<String, Int> = emptyMap(),
        kitsuMediaToMal: Map<String, Int> = emptyMap()
    ): Set<String> {
        val prefix = entry.type.name
        val ownedNamespaces = MediaIdentity.keys(entry).mapTo(HashSet()) { it.substringBeforeLast(':') }
        val malId = ids?.malId
            ?: bangumiMalIdOfBangumiRow(entry, prefix, bangumiSubjectToMal)
            ?: kitsuMalIdOfKitsuRow(entry, prefix, kitsuMediaToMal)

        val candidates = listOfNotNull(
            malId?.let { "$prefix:mal:$it" },
            ids?.aniListId?.let { "$prefix:anilist:$it" },
            ids?.kitsuId?.let { "$prefix:kitsu:$it" },
            ids?.shikimoriId?.let { "$prefix:shikimori:$it" },
            ids?.bangumiSubjectId?.let { "$prefix:bangumi:$it" },
            ids?.simklId?.let { "$prefix:simkl:$it" },
            ids?.tmdbId?.let { "$prefix:tmdb:$it" }
        )
        if (candidates.isEmpty()) return emptySet()
        return candidates.filterTo(LinkedHashSet()) { it.substringBeforeLast(':') !in ownedNamespaces }
    }

    /** Bangumi satırının `subject_id`'si için önbellekten bulunan gerçek MAL ID. */
    private fun bangumiMalIdOfBangumiRow(
        entry: MediaEntry,
        typePrefix: String,
        bangumiSubjectToMal: Map<String, Int>
    ): Int? {
        if (bangumiSubjectToMal.isEmpty()) return null
        // Yalnızca Anime/Manga tarafında MAL ekseni üretilir (MediaIdentity.keys sözleşmesi).
        if (entry.type != MediaType.Anime && entry.type != MediaType.Manga) return null
        val subjectId = MediaIdentity.bangumiId(entry) ?: return null
        return bangumiSubjectToMal["$typePrefix:$subjectId"]
    }

    /** Kitsu satırının `kitsuMediaId`'si için önbellekten bulunan gerçek MAL ID. */
    private fun kitsuMalIdOfKitsuRow(
        entry: MediaEntry,
        typePrefix: String,
        kitsuMediaToMal: Map<String, Int>
    ): Int? {
        if (kitsuMediaToMal.isEmpty()) return null
        if (entry.type != MediaType.Anime && entry.type != MediaType.Manga) return null
        val kitsuMediaId = MediaIdentity.kitsuId(entry) ?: return null
        return kitsuMediaToMal["$typePrefix:$kitsuMediaId"]
    }

    /**
     * `bgm_a_<malId> → subject_id` (animeler) ve `bgm_m_<malId> → subject_id` (kitaplar) kayıtlarını
     * `"<Tür>:<subject_id>" → malId` biçimine ters çevirir.
     *
     * Aynı条目 iki farklı MAL kaydına bağlanmışsa o eşleme atılır: belirsiz köprü, yanlış iki
     * yapımı birleştirmektense ikisini ayrı bırakmak daha az zarar verir.
     */
    internal fun reverseBangumiMapping(raw: Map<String, Int>): Map<String, Int> {
        if (raw.isEmpty()) return emptyMap()
        val malIdsBySubject = HashMap<String, MutableSet<Int>>()
        raw.forEach { (key, subjectId) ->
            if (subjectId <= 0) return@forEach
            val match = bangumiMappingKey.matchEntire(key) ?: return@forEach
            val malId = match.groupValues[2].toIntOrNull()
                ?.takeIf { it in CrossProviderIds.REAL_MAL_MIN..CrossProviderIds.REAL_MAL_MAX }
                ?: return@forEach
            val typePrefix = if (match.groupValues[1] == "a") MediaType.Anime.name else MediaType.Manga.name
            malIdsBySubject.getOrPut("$typePrefix:$subjectId") { LinkedHashSet() } += malId
        }
        return malIdsBySubject.mapNotNull { (subjectKey, malIds) ->
            malIds.singleOrNull()?.let { subjectKey to it }
        }.toMap()
    }

    /**
     * `kitsu_media_<userId>_{a|m}_<malId> → kitsuMediaId` kayıtlarını
     * `"<Tür>:<kitsuMediaId>" → malId` biçimine ters çevirir.
     *
     * Bir Kitsu medya kimliği birden fazla MAL kaydına bağlanmışsa belirsizlik nedeniyle elenir.
     */
    internal fun reverseKitsuMapping(raw: Map<String, *>): Map<String, Int> {
        if (raw.isEmpty()) return emptyMap()
        val malIdsByKitsuMedia = HashMap<String, MutableSet<Int>>()
        raw.forEach { (key, value) ->
            val kitsuMediaId = (value as? Number)?.toInt() ?: return@forEach
            if (kitsuMediaId <= 0) return@forEach
            val match = kitsuMappingKey.matchEntire(key) ?: return@forEach
            val malId = match.groupValues[2].toIntOrNull()
                ?.takeIf { it in CrossProviderIds.REAL_MAL_MIN..CrossProviderIds.REAL_MAL_MAX }
                ?: return@forEach
            val typePrefix = if (match.groupValues[1] == "a") MediaType.Anime.name else MediaType.Manga.name
            malIdsByKitsuMedia.getOrPut("$typePrefix:$kitsuMediaId") { LinkedHashSet() } += malId
        }
        return malIdsByKitsuMedia.mapNotNull { (kitsuKey, malIds) ->
            malIds.singleOrNull()?.let { kitsuKey to it }
        }.toMap()
    }

    /** `KitsugiBangumiDetailClient` (`bgm_detail_xref_v1`: `s<id> -> "al=...;mal=...;"`) köprüsü. */
    private fun readDetailXrefBangumiToMal(context: Context): Map<String, Int> = runCatching {
        val prefs = context.getSharedPreferences("bgm_detail_xref_v1", Context.MODE_PRIVATE)
        val out = HashMap<String, Int>()
        prefs.all.forEach { (key, value) ->
            if (!key.startsWith("s")) return@forEach
            val subjectId = key.drop(1).toIntOrNull()?.takeIf { it > 0 } ?: return@forEach
            val encoded = value as? String ?: return@forEach
            val malId = encoded.split(';')
                .firstOrNull { it.startsWith("mal=") }
                ?.removePrefix("mal=")
                ?.toIntOrNull()
                ?.takeIf { it in CrossProviderIds.REAL_MAL_MIN..CrossProviderIds.REAL_MAL_MAX }
                ?: return@forEach
            out["${MediaType.Anime.name}:$subjectId"] = malId
            out["${MediaType.Manga.name}:$subjectId"] = malId
        }
        out
    }.getOrDefault(emptyMap())
}
