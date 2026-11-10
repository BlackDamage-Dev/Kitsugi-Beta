package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.core.memory.BoundedCache

import android.util.Log
import org.json.JSONObject
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.kitsugi.animelist.data.fandom.WikidataClient
import com.kitsugi.animelist.data.fandom.WikidataParsers
import com.kitsugi.animelist.model.MediaType

/**
 * Holds all resolved external IDs for a given anime entry.
 */
data class ResolvedIds(
    val imdbId: String? = null,
    val kitsuId: Int? = null,
    val tmdbId: Int? = null,
    val aniListId: Int? = null,
    val malId: Int? = null,
    val tvdbId: Int? = null,
    val wikidataId: String? = null
)

/**
 * Bir TMDB/Simkl yapımının eşleştiği tüm anime katalog kimlikleri (çok sezonlu dizilerde
 * tüm sezonların MAL / AniList / Kitsu kimlikleri sırayla korunur).
 */
data class TmdbAnimeIdCandidates(
    val malIds: List<Int> = emptyList(),
    val aniListIds: List<Int> = emptyList(),
    val kitsuIds: List<Int> = emptyList(),
    val bangumiId: Int? = null,
    val simklId: Int? = null,
    val imdbId: String? = null,
    val wikidataId: String? = null
)

object KitsugiIdResolver {
    private const val TAG = "KitsugiIdResolver"

    /**
     * Shikimori ID → MAL ID eşleme önbelleği. Değer 0 ise "eşleme yok" (negatif önbellek):
     * ARM ve Shikimori API'sine aynı ID için tekrar tekrar sorulmaz.
     */
    private val shikimoriMalIdCache = BoundedCache<Int, Int>("shikimori.malId", 3000)
    private val malShikimoriIdCache = BoundedCache<Int, Int>("mal.shikimoriId", 3000)
    private val tmdbAnimeCandidatesCache = BoundedCache<String, TmdbAnimeIdCandidates>("tmdb.animeIds", 1000)
    private const val SHIKIMORI_MAL_UNRESOLVED = 0

    /**
     * v2.4.250: resolveIds SONUÇ önbelleği. runGetStreams HER eklenti için (164 paralel!)
     * resolveIds çağırıyordu; her biri 8'e kadar ardışık HTTP (ARM + TMDB) demekti —
     * İzle'ye her basışta ~1000 istek, 75 sn'lik provider bütçesi aramaya sıra gelmeden
     * eriyordu ("sağlam eklentiden bile veri gelmiyor"). İlk eklenti öder, kalanı
     * önbellekten okur. Yalnızca zenginleşmiş sonuçlar önbelleğe alınır; geçici ağ
     * arızaları tekrar deneyebilir kalır. ID'ler değişmez veri → eskitme gerekmez.
     */
    private val resolvedIdsCache = BoundedCache<String, ResolvedIds>("ids.resolved", 500)

    fun clearCache() {
        tmdbAnimeCandidatesCache.clear()
        resolvedIdsCache.clear()
        shikimoriMalIdCache.clear()
        malShikimoriIdCache.clear()
    }

    /**
     * Resolves both IMDb ID and Kitsu ID in a single ARM API call.
     * Falls back to TMDB for the IMDb ID if ARM doesn't have one.
     */
    suspend fun resolveIds(
        malId: Int?,
        aniListId: Int?,
        tmdbId: Int? = null,
        mediaType: MediaType? = null,
        kitsuId: Int? = null,
        bangumiId: Int? = null,
        /**
         * ARM ve TMDB IMDb vermezse Wikidata'dan (P345) IMDb kimliğini dene. Akış (stream) yolu bunu
         * açar: IMDb ile sorgulanan Stremio addon'ları ve kimlik doğrulaması bu değere bağlı.
         * Detay sayfaları varsayılan olarak kapalı (ek ağ gecikmesi olmasın).
         */
        wikidataImdbFallback: Boolean = false,
        expectedSeason: Int? = null
    ): ResolvedIds = withContext(Dispatchers.IO) {
        val cacheKey = "m=$malId|a=$aniListId|t=$tmdbId|mt=${mediaType?.name}|k=$kitsuId|b=$bangumiId|wd=$wikidataImdbFallback|s=$expectedSeason"
        resolvedIdsCache[cacheKey]?.let { return@withContext it }
        val result = resolveIdsUncached(malId, aniListId, tmdbId, mediaType, kitsuId, bangumiId, wikidataImdbFallback, expectedSeason)
        // Yalnızca girdi tmdbId'nin kendisini geri yansıtan boş bir yanıtı (ör. geçici ağ arızası)
        // kalıcı önbelleğe yazma; en az bir çapraz kimlik çözülmüş olmalı.
        val hasCrossMapping = result.malId != null ||
            result.aniListId != null ||
            result.kitsuId != null ||
            result.imdbId != null ||
            result.tvdbId != null ||
            (tmdbId == null && result.tmdbId != null)
        if (hasCrossMapping) {
            resolvedIdsCache[cacheKey] = result
        }
        result
    }

    private suspend fun resolveIdsUncached(
        malId: Int?,
        aniListId: Int?,
        tmdbId: Int? = null,
        mediaType: MediaType? = null,
        kitsuId: Int? = null,
        bangumiId: Int? = null,
        wikidataImdbFallback: Boolean = false,
        expectedSeason: Int? = null
    ): ResolvedIds = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting ID resolution: malParam=$malId, aniListParam=$aniListId, tmdbParam=$tmdbId, mediaType=$mediaType, kitsuParam=$kitsuId, bangumiParam=$bangumiId")

        // Bangumi's 500M stable IDs are not MAL IDs and ARM has no Bangumi namespace.
        // Resolve Bangumi through its cached, title/year-aware cross-reference layer first.
        // NOT: Bu köprü manga erken dönüşünden ÖNCE çalışmalı — eskiden dönüş köprüden önce
        // olduğu için Bangumi kitap/manga satırları MAL/AniList kimliği hiç çözemiyor ve
        // diğer kaynaklara asla yayılamıyordu.
        val bangumiCrossIds = if (
            bangumiId != null && bangumiId > 0 &&
            malId == null && aniListId == null && tmdbId == null && kitsuId == null
        ) {
            try {
                KitsugiBangumiDetailClient.resolveCrossIds(
                    stableOrRawId = bangumiId,
                    mediaType = mediaType ?: MediaType.Anime
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Bangumi ID cross-resolution failed for $bangumiId: ${e.message}")
                null
            }
        } else null

        val lookupMalId = malId ?: bangumiCrossIds?.malId?.takeIf { it in 1..99_999_999 }
        val lookupAniListId = aniListId ?: bangumiCrossIds?.aniListId?.takeIf { it > 0 }
        val lookupTmdbId = tmdbId ?: bangumiCrossIds?.tmdbId?.takeIf { it > 0 }
        val lookupKitsuId = kitsuId ?: bangumiCrossIds?.kitsuId?.takeIf { it > 0 }

        if (mediaType == MediaType.Manga) {
            // Manga ARM'da yer almaz; Bangumi köprüsünün çözdüğü kimlikler aynen döner.
            Log.d(TAG, "Manga yapımları ARM API'de yer almaz; alakasız anime eşleştirmelerini önlemek için ARM sorgusu atlanıyor.")
            return@withContext ResolvedIds(
                malId = lookupMalId,
                aniListId = lookupAniListId,
                kitsuId = lookupKitsuId,
                tmdbId = lookupTmdbId,
                imdbId = bangumiCrossIds?.imdbId,
                tvdbId = bangumiCrossIds?.tvdbId
            )
        }

        var armJson: JSONObject? = null
        val isNonAnime = mediaType == MediaType.Movie || mediaType == MediaType.TvShow
        
        if (lookupKitsuId != null && lookupKitsuId > 0) {
            armJson = fetchArmJson("kitsu", lookupKitsuId)
            if (armJson != null) {
                Log.d(TAG, "Kitsu mapping prioritized: Successfully fetched ARM JSON for kitsu: $lookupKitsuId -> $armJson")
            }
        }
        
        if (armJson == null) {
            if (isNonAnime) {
                armJson = fetchArmJson("themoviedb", lookupTmdbId, mediaType, expectedSeason = expectedSeason)
                if (armJson != null) {
                    Log.d(TAG, "Non-anime mapping prioritized: Successfully fetched ARM JSON for themoviedb: $lookupTmdbId -> $armJson")
                } else {
                    armJson = fetchArmJson("myanimelist", lookupMalId, mediaType)
                    if (armJson != null) {
                        Log.d(TAG, "Successfully fetched ARM JSON for myanimelist: $lookupMalId -> $armJson")
                    } else {
                        armJson = fetchArmJson("anilist", lookupAniListId, mediaType)
                        if (armJson != null) {
                            Log.d(TAG, "Successfully fetched ARM JSON for anilist: $lookupAniListId -> $armJson")
                        } else {
                            Log.w(TAG, "ARM lookup failed for all sources (tmdbId=$lookupTmdbId, malId=$lookupMalId, aniListId=$lookupAniListId)")
                        }
                    }
                }
            } else {
                armJson = fetchArmJson("myanimelist", lookupMalId, mediaType)
                if (armJson != null) {
                    Log.d(TAG, "Successfully fetched ARM JSON for myanimelist: $lookupMalId -> $armJson")
                } else {
                    armJson = fetchArmJson("anilist", lookupAniListId, mediaType)
                    if (armJson != null) {
                        Log.d(TAG, "Successfully fetched ARM JSON for anilist: $lookupAniListId -> $armJson")
                    } else {
                        armJson = fetchArmJson("themoviedb", lookupTmdbId, mediaType)
                        if (armJson != null) {
                            Log.d(TAG, "Successfully fetched ARM JSON for themoviedb: $lookupTmdbId -> $armJson")
                        } else {
                            Log.w(TAG, "ARM lookup failed for all sources (malId=$lookupMalId, aniListId=$lookupAniListId, tmdbId=$lookupTmdbId)")
                        }
                    }
                }
            }
        }

        var imdbFromArm = armJson?.optNullableString("imdb")
        var kitsuFromArm = armJson?.let {
            val v = it.optInt("kitsu", -1)
            if (v > 0) v else null
        }
        val tmdbFromArm = armJson?.let {
            val v = it.optInt("themoviedb", -1)
            if (v > 0) v else null
        }
        var aniListFromArm = armJson?.let {
            val v = it.optInt("anilist", -1)
            if (v > 0) v else null
        }
        var malFromArm = armJson?.let {
            val v = it.optInt("myanimelist", -1)
            if (v > 0) v else null
        }
        val tvdbFromArm = armJson?.let {
            val v = it.optInt("thetvdb", -1)
            if (v > 0) v else null
        }
        val wikidataFromArm = armJson?.optNullableString("wikidata")

        // Use provided tmdbId directly if ARM didn't find one
        val finalTmdbId = tmdbFromArm ?: lookupTmdbId ?: resolveTmdbId(lookupMalId, lookupAniListId, lookupKitsuId)

        // If ARM didn't give us an IMDb ID, fall back through TMDB
        var finalImdb = if (!imdbFromArm.isNullOrBlank()) imdbFromArm else bangumiCrossIds?.imdbId
        var finalTvdb = tvdbFromArm ?: bangumiCrossIds?.tvdbId
        var finalWikidata = wikidataFromArm

        if (finalTmdbId != null && finalTmdbId > 0) {
            val isTv = mediaType == MediaType.TvShow || (mediaType != MediaType.Movie && !isNonAnime)
            var extIds = fetchExternalIdsFromTmdb(finalTmdbId, isTv)
            if (extIds.imdbId.isNullOrBlank() && extIds.tvdbId == null && extIds.wikidataId.isNullOrBlank()) {
                // Try the other type just in case
                extIds = fetchExternalIdsFromTmdb(finalTmdbId, !isTv)
            }
            if (!extIds.imdbId.isNullOrBlank()) {
                finalImdb = extIds.imdbId
            }
            if (extIds.tvdbId != null && extIds.tvdbId > 0) {
                finalTvdb = extIds.tvdbId
            }
            if (!extIds.wikidataId.isNullOrBlank()) {
                finalWikidata = extIds.wikidataId
            }
        }

        // Eğer TMDB ID ARM'da doğrudan eşleşmediyse (ör. yeni anime filmleri / Doraemon filmleri),
        // TMDB external_ids üzerinden gelen benzersiz IMDb ID ile ARM v2 `/api/v2/imdb` ucunu dene.
        if (malFromArm == null && aniListFromArm == null && !finalImdb.isNullOrBlank()) {
            val byImdb = fetchArmJsonString("imdb", finalImdb, mediaType, finalImdb)
            if (byImdb != null) {
                malFromArm = byImdb.optInt("myanimelist", -1).takeIf { it > 0 } ?: malFromArm
                aniListFromArm = byImdb.optInt("anilist", -1).takeIf { it > 0 } ?: aniListFromArm
                kitsuFromArm = byImdb.optInt("kitsu", -1).takeIf { it > 0 } ?: kitsuFromArm
                imdbFromArm = byImdb.optNullableString("imdb") ?: imdbFromArm
            }
        }

        // Hâlâ anime katalog kimliği yoksa TMDB'nin verdiği kesin Wikidata QID (P4086/P8729/P11495)
        // kaydından oku: başlık tahmini yapmadan %100 kesin eşleşme sağlar.
        if (malFromArm == null && lookupMalId == null && !finalWikidata.isNullOrBlank() &&
            WikidataParsers.isQid(finalWikidata)
        ) {
            val wdClaims = runCatching {
                WikidataClient.getEntities(listOf(finalWikidata))?.get(finalWikidata)
            }.getOrNull()
            if (wdClaims != null) {
                malFromArm = wdClaims.malAnimeId ?: malFromArm
                aniListFromArm = wdClaims.aniListAnimeId ?: aniListFromArm
                kitsuFromArm = wdClaims.kitsuAnimeId ?: kitsuFromArm
            }
        }

        val finalAniList = aniListFromArm ?: lookupAniListId
        val finalMal = malFromArm ?: lookupMalId

        // Son çare: Wikidata P345 (IMDb). ARM/TMDB'nin kaçırdığı anime/dizi kayıtları için köprü.
        if (finalImdb.isNullOrBlank() && wikidataImdbFallback) {
            finalImdb = imdbViaWikidata(
                wikidataQid = finalWikidata,
                malId = finalMal,
                aniListId = finalAniList,
                kitsuId = kitsuFromArm ?: lookupKitsuId,
                tmdbId = finalTmdbId,
                isMovie = mediaType == MediaType.Movie
            )
        }

        Log.d(TAG, "resolveIds complete → imdb=$finalImdb kitsu=${kitsuFromArm ?: lookupKitsuId} tmdb=$finalTmdbId anilist=$finalAniList mal=$finalMal tvdb=$finalTvdb wikidata=$finalWikidata")
        ResolvedIds(
            imdbId = finalImdb,
            kitsuId = kitsuFromArm ?: lookupKitsuId,
            tmdbId = finalTmdbId,
            aniListId = finalAniList,
            malId = finalMal,
            tvdbId = finalTvdb,
            wikidataId = finalWikidata
        )
    }

    /**
     * Wikidata üzerinden IMDb (P345) çözümü. Önce bilinen QID doğrudan okunur; yoksa MAL / AniList /
     * Kitsu / TMDB kimlikleri Wikidata'da benzersiz bir kayda eşlenir ve o kaydın P345'i alınır.
     * Her adım ağ hatasında/çelişkide sessizce bir sonrakine geçer; hiçbiri vermezse null.
     */
    private suspend fun imdbViaWikidata(
        wikidataQid: String?,
        malId: Int?,
        aniListId: Int?,
        kitsuId: Int?,
        tmdbId: Int?,
        isMovie: Boolean
    ): String? {
        val qid = wikidataQid?.takeIf { WikidataParsers.isQid(it) }
        if (qid != null) {
            val direct = try {
                WikidataClient.getEntities(listOf(qid))?.get(qid)?.imdbId
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Wikidata QID $qid okunamadı: ${e.message}")
                null
            }
            if (direct != null) return direct
        }

        val lookups = buildList {
            if (malId != null && malId in 1..99_999_999) add("P4086" to malId.toString())
            if (aniListId != null && aniListId > 0) add("P8729" to aniListId.toString())
            if (kitsuId != null && kitsuId > 0) add("P11495" to kitsuId.toString())
            if (tmdbId != null && tmdbId > 0) add((if (isMovie) "P4947" else "P4983") to tmdbId.toString())
        }
        for ((property, value) in lookups.take(3)) {
            val claims = try {
                WikidataClient.identityClaims(property, value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Wikidata $property=$value araması başarısız: ${e.message}")
                null
            }
            val imdb = claims?.imdbId
            if (imdb != null) {
                Log.d(TAG, "IMDb Wikidata üzerinden bulundu: $property=$value → $imdb")
                return imdb
            }
        }
        return null
    }

    /**
     * Resolves the IMDb ID for a media entry, using either MAL ID or AniList ID.
     */
    suspend fun getImdbId(malId: Int?, aniListId: Int?): String? =
        resolveIds(malId, aniListId).imdbId

    /**
     * MAL ID → Shikimori ID.
     *
     * NOT: ARM v2 şemasında (`BeeeQueue/arm-server`) `"shikimori"` alanı hiç yoktur —
     * eski kod `optInt("shikimori", -1)` ile %100 `null` döndürüyordu. Shikimori anime/manga
     * kataloğu MAL ile aynı kimlik uzayını paylaşır; `/api/animes/{malId}` yanıtındaki
     * `myanimelist_id` (varsa) doğrulanarak önbelleğe alınır.
     */
    fun resolveShikimoriIdFromMal(malId: Int?): Int? {
        if (malId == null || malId <= 0 || malId >= 100_000_000) return null
        malShikimoriIdCache[malId]?.let { cached ->
            return if (cached > 0) cached else null
        }

        val verified = runCatching {
            val url = URL("https://shikimori.io/api/animes/$malId")
            val response = KitsugiApiBase.executeGetRequest(url) ?: return@runCatching malId
            val json = JSONObject(response)
            val shikiId = json.optInt("id", malId).takeIf { it > 0 } ?: malId
            val reportedMal = json.optInt("myanimelist_id", -1)
            if (reportedMal <= 0 || reportedMal == malId) shikiId else null
        }.getOrDefault(malId)

        malShikimoriIdCache[malId] = verified ?: SHIKIMORI_MAL_UNRESOLVED
        return verified
    }

    /**
     * Shikimori ID → MAL ID eşlemesi.
     *  1. Shikimori detay önbelleğindeki `myanimelist_id`,
     *  2. Doğrudan Shikimori REST yanıtındaki `myanimelist_id`.
     */
    fun resolveMalIdFromShikimori(shikimoriId: Int?): Int? {
        if (shikimoriId == null || shikimoriId <= 0) return null

        shikimoriMalIdCache[shikimoriId]?.let { cached ->
            return if (cached > 0) cached else null
        }

        val fromDetailCache = DetailCache.getMediaDetail("shikimori", shikimoriId)?.realMalId?.takeIf { it > 0 }
        if (fromDetailCache != null) {
            shikimoriMalIdCache[shikimoriId] = fromDetailCache
            return fromDetailCache
        }

        val malId = fetchMalIdFromShikimoriApi(shikimoriId)
        shikimoriMalIdCache[shikimoriId] = malId ?: SHIKIMORI_MAL_UNRESOLVED
        return malId
    }

    /** Shikimori REST yanıtındaki `myanimelist_id` alanını okur (anime → manga sırasıyla). */
    private fun fetchMalIdFromShikimoriApi(shikimoriId: Int): Int? {
        for (endpoint in listOf("animes", "mangas")) {
            val result = runCatching {
                val url = URL("https://shikimori.io/api/$endpoint/$shikimoriId")
                val response = KitsugiApiBase.executeGetRequest(url) ?: return@runCatching null
                val json = JSONObject(response)
                val v = json.optInt("myanimelist_id", -1)
                if (v > 0) v else null
            }.getOrNull()
            if (result != null) return result
        }
        return null
    }

    /**
     * Bir TMDB (veya biliniyorsa IMDb) kaydına karşılık gelen tüm anime katalog kimliklerini
     * (çok sezonlu yapımlarda sezon 1, sezon 2 ... dâhil) tek seferde çözer.
     */
    suspend fun resolveAllTmdbAnimeIds(
        tmdbId: Int,
        mediaType: MediaType,
        knownImdbId: String? = null
    ): TmdbAnimeIdCandidates = withContext(Dispatchers.IO) {
        if (tmdbId <= 0 || mediaType == MediaType.Manga) return@withContext TmdbAnimeIdCandidates()
        val cacheKey = "${mediaType.name}:$tmdbId:${knownImdbId.orEmpty()}"
        tmdbAnimeCandidatesCache[cacheKey]?.let { return@withContext it }

        val isTv = mediaType == MediaType.TvShow
        var extIds = fetchExternalIdsFromTmdb(tmdbId, isTv)
        if (extIds.imdbId.isNullOrBlank() && extIds.wikidataId.isNullOrBlank() && extIds.tvdbId == null &&
            mediaType == MediaType.Anime
        ) {
            extIds = fetchExternalIdsFromTmdb(tmdbId, true)
        }
        val effectiveImdb = knownImdbId?.takeIf { it.isNotBlank() } ?: extIds.imdbId
        val wikidataId = extIds.wikidataId?.takeIf { it.isNotBlank() }

        var relations = fetchArmRelations("themoviedb", tmdbId.toString(), mediaType, effectiveImdb)
        if (relations.isEmpty() && !effectiveImdb.isNullOrBlank()) {
            relations = fetchArmRelations("imdb", effectiveImdb, mediaType, effectiveImdb)
        }

        val malIds = LinkedHashSet<Int>()
        val aniListIds = LinkedHashSet<Int>()
        val kitsuIds = LinkedHashSet<Int>()
        var simklId: Int? = null
        var resolvedImdb = effectiveImdb
        var bangumiId: Int? = null

        for (rel in relations) {
            rel.optInt("myanimelist", -1).takeIf { it in 1..99_999_999 }?.let { malIds += it }
            rel.optInt("anilist", -1).takeIf { it > 0 }?.let { aniListIds += it }
            rel.optInt("kitsu", -1).takeIf { it > 0 }?.let { kitsuIds += it }
            if (simklId == null) simklId = rel.optInt("simkl", -1).takeIf { it > 0 }
            if (resolvedImdb.isNullOrBlank()) resolvedImdb = rel.optNullableString("imdb")
        }

        // Wikidata QID üzerinden kesin dış kimlik takviyesi (P4086 MAL / P8729 AniList / P11495 Kitsu / Bangumi)
        if (!wikidataId.isNullOrBlank() && WikidataParsers.isQid(wikidataId) &&
            (malIds.isEmpty() || kitsuIds.isEmpty() || bangumiId == null)
        ) {
            val wdClaims = runCatching {
                WikidataClient.getEntities(listOf(wikidataId))?.get(wikidataId)
            }.getOrNull()
            if (wdClaims != null) {
                wdClaims.malAnimeId?.takeIf { it in 1..99_999_999 }?.let { malIds += it }
                wdClaims.aniListAnimeId?.takeIf { it > 0 }?.let { aniListIds += it }
                wdClaims.kitsuAnimeId?.takeIf { it > 0 }?.let { kitsuIds += it }
                if (bangumiId == null) bangumiId = wdClaims.bangumiSubjectId
                if (resolvedImdb.isNullOrBlank()) resolvedImdb = wdClaims.imdbId
            }
        }

        val result = TmdbAnimeIdCandidates(
            malIds = malIds.toList(),
            aniListIds = aniListIds.toList(),
            kitsuIds = kitsuIds.toList(),
            bangumiId = bangumiId,
            simklId = simklId,
            imdbId = resolvedImdb,
            wikidataId = wikidataId
        )
        if (result.malIds.isNotEmpty() || result.aniListIds.isNotEmpty() ||
            result.kitsuIds.isNotEmpty() || result.bangumiId != null ||
            !result.imdbId.isNullOrBlank() || !result.wikidataId.isNullOrBlank()
        ) {
            tmdbAnimeCandidatesCache[cacheKey] = result
        }
        result
    }

    /**
     * ARM v2 OpenAPI kuralları (`BeeeQueue/arm-server`):
     *  - `themoviedb`, `thetvdb`, `imdb` kaynakları 1:N eşleme döndürdüğü için `/api/v2/{source}?id={id}`
     *    uçlarındadır (`/api/v2/ids?source=themoviedb` her zaman HTTP 400 döner!).
     *  - Tekil anime katalog kaynakları (`myanimelist`, `anilist`, `kitsu`, `anidb`, ...)
     *    `/api/v2/ids?source={source}&id={id}` ucundadır.
     */
    internal fun buildArmUrl(source: String, id: String): String {
        val cleanSource = source.trim().lowercase()
        val cleanId = id.trim()
        return when (cleanSource) {
            "themoviedb", "thetvdb", "imdb" -> "https://arm.haglund.dev/api/v2/$cleanSource?id=$cleanId"
            else -> "https://arm.haglund.dev/api/v2/ids?source=$cleanSource&id=$cleanId"
        }
    }

    /**
     * ARM yanıtını (tekil nesne veya dizi) ayrıştırır ve beklenen medya türüne (`Movie` vs `TvShow`)
     * göre sayısal TMDB ID çakışmalarını eler, en uygun kaydı (ör. Sezon 1 veya IMDb eşleşmesi) öne alır.
     */
    internal fun parseArmRelations(
        rawBody: String?,
        expectedMediaType: MediaType? = null,
        expectedImdbId: String? = null,
        expectedSeason: Int? = null
    ): List<JSONObject> {
        val trimmed = rawBody?.trim() ?: return emptyList()
        if (trimmed.isEmpty() || trimmed == "null" || trimmed == "[]") return emptyList()

        val rawList = mutableListOf<JSONObject>()
        if (trimmed.startsWith("[")) {
            val arr = runCatching { org.json.JSONArray(trimmed) }.getOrNull() ?: return emptyList()
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { rawList += it }
            }
        } else if (trimmed.startsWith("{")) {
            val obj = runCatching { JSONObject(trimmed) }.getOrNull() ?: return emptyList()
            if (obj.optInt("statusCode", 200) >= 400) return emptyList()
            rawList += obj
        }
        if (rawList.isEmpty()) return emptyList()

        val normImdb = expectedImdbId?.trim()?.lowercase()?.takeIf { it.startsWith("tt") }

        val compatible = rawList.filter { rel ->
            val relImdb = rel.optNullableString("imdb")?.trim()?.lowercase()
            if (normImdb != null && relImdb == normImdb) return@filter true

            val relMedia = rel.optNullableString("media")?.trim()?.uppercase().orEmpty()
            val hasTmdbSeason = !rel.isNull("themoviedb-season") && rel.optInt("themoviedb-season", -1) > 0
            when (expectedMediaType) {
                MediaType.Movie -> {
                    // TMDB movie/{id} ile tv/{id} çakışmasını engelle: TV sezonu olan kayıtları reddet.
                    if (hasTmdbSeason) false
                    else relMedia != "TV"
                }
                MediaType.TvShow -> {
                    relMedia != "MOVIE"
                }
                else -> true
            }
        }

        return compatible.sortedWith(
            compareByDescending<JSONObject> { rel ->
                expectedSeason != null && rel.optInt("themoviedb-season", -1) == expectedSeason
            }.thenByDescending { rel ->
                normImdb != null && rel.optNullableString("imdb")?.trim()?.lowercase() == normImdb
            }.thenBy { rel ->
                val season = if (rel.isNull("themoviedb-season")) Int.MAX_VALUE else rel.optInt("themoviedb-season", Int.MAX_VALUE)
                if (season <= 0) Int.MAX_VALUE else season
            }
        )
    }

    private fun fetchArmRelations(
        source: String,
        id: String?,
        expectedMediaType: MediaType? = null,
        expectedImdbId: String? = null,
        expectedSeason: Int? = null
    ): List<JSONObject> {
        val cleanId = id?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
        if (source == "shikimori") return emptyList() // ARM v2'de shikimori kaynağı yoktur
        val url = runCatching { URL(buildArmUrl(source, cleanId)) }.getOrNull() ?: return emptyList()
        return try {
            val response = KitsugiApiBase.executeGetRequest(url) ?: return emptyList()
            parseArmRelations(response, expectedMediaType, expectedImdbId, expectedSeason)
        } catch (e: Exception) {
            Log.e(TAG, "fetchArmRelations: Exception for source=$source id=$cleanId: ${e.message}", e)
            emptyList()
        }
    }

    /** Fetches the best matching ARM JSON object for a given source+id pair. */
    private fun fetchArmJson(
        source: String,
        id: Int?,
        expectedMediaType: MediaType? = null,
        expectedImdbId: String? = null,
        expectedSeason: Int? = null
    ): JSONObject? {
        if (id == null || id <= 0) return null
        return fetchArmRelations(source, id.toString(), expectedMediaType, expectedImdbId, expectedSeason).firstOrNull()
    }

    private fun fetchArmJsonString(
        source: String,
        id: String?,
        expectedMediaType: MediaType? = null,
        expectedImdbId: String? = null
    ): JSONObject? {
        if (id.isNullOrBlank()) return null
        return fetchArmRelations(source, id.trim(), expectedMediaType, expectedImdbId).firstOrNull()
    }

    private suspend fun resolveTmdbId(malId: Int?, aniListId: Int?, kitsuId: Int? = null): Int? = withContext(Dispatchers.IO) {
        val kitsuJson = if (kitsuId != null && kitsuId > 0) fetchArmJson("kitsu", kitsuId) else null
        val kitsuTmdbVal = kitsuJson?.optInt("themoviedb", -1)
        if (kitsuTmdbVal != null && kitsuTmdbVal > 0) {
            return@withContext kitsuTmdbVal
        }

        val malJson = if (malId != null && malId > 0) fetchArmJson("myanimelist", malId) else null
        val malTmdbVal = malJson?.optInt("themoviedb", -1)
        if (malTmdbVal != null && malTmdbVal > 0) {
            return@withContext malTmdbVal
        }

        val aniJson = if (aniListId != null && aniListId > 0) fetchArmJson("anilist", aniListId) else null
        val aniTmdbVal = aniJson?.optInt("themoviedb", -1)
        if (aniTmdbVal != null && aniTmdbVal > 0) {
            return@withContext aniTmdbVal
        }
        null
    }

    private fun fetchImdbFromTmdb(tmdbId: Int): String? {
        val apiKey = TmdbApiClient.getActiveApiKey()

        // Probe TV external IDs first
        try {
            val tvUrl = URL("https://api.themoviedb.org/3/tv/$tmdbId/external_ids?api_key=$apiKey")
            val tvResponse = KitsugiApiBase.executeGetRequest(tvUrl)
            if (tvResponse != null) {
                val json = JSONObject(tvResponse)
                val imdbId = json.optNullableString("imdb_id")
                if (!imdbId.isNullOrBlank()) return imdbId
            }
        } catch (e: Exception) {
            // Ignore and try movie
        }

        // Try movie details
        try {
            val movieUrl = URL("https://api.themoviedb.org/3/movie/$tmdbId?api_key=$apiKey")
            val movieResponse = KitsugiApiBase.executeGetRequest(movieUrl)
            if (movieResponse != null) {
                val json = JSONObject(movieResponse)
                val imdbId = json.optNullableString("imdb_id")
                if (!imdbId.isNullOrBlank()) return imdbId
            }
        } catch (e: Exception) {
            // Ignore
        }

        return null
    }

    data class TmdbExternalIds(
        val imdbId: String? = null,
        val tvdbId: Int? = null,
        val wikidataId: String? = null
    )

    private fun fetchExternalIdsFromTmdb(tmdbId: Int, isTv: Boolean): TmdbExternalIds {
        val apiKey = TmdbApiClient.getActiveApiKey()
        val typePath = if (isTv) "tv" else "movie"
        return try {
            val url = URL("https://api.themoviedb.org/3/$typePath/$tmdbId/external_ids?api_key=$apiKey")
            val response = KitsugiApiBase.executeGetRequest(url) ?: return TmdbExternalIds()
            val json = JSONObject(response)
            val imdbId = json.optNullableString("imdb_id")
            val tvdbId = json.optInt("tvdb_id", -1).takeIf { it > 0 }
            val wikidataId = json.optNullableString("wikidata_id")
            TmdbExternalIds(imdbId = imdbId, tvdbId = tvdbId, wikidataId = wikidataId)
        } catch (e: Exception) {
            Log.w(TAG, "fetchExternalIdsFromTmdb failed for tmdbId=$tmdbId isTv=$isTv: ${e.message}")
            TmdbExternalIds()
        }
    }

    /**
     * KitsugiEpisodeRatingsRepository gibi dış sınıflar için TMDB external_ids erişimi.
     * isTv=true → /tv/{id}/external_ids, isTv=false → /movie/{id}/external_ids
     */
    fun fetchExternalIds(tmdbId: Int, isTv: Boolean): TmdbExternalIds =
        fetchExternalIdsFromTmdb(tmdbId, isTv)

    // JSON Helper
    private fun JSONObject.optNullableString(key: String): String? {
        if (isNull(key)) return null
        return optString(key)
    }
}
