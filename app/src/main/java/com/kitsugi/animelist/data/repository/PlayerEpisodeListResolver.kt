package com.kitsugi.animelist.data.repository

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.data.cloudstream.CsEpisodeMatcher
import com.kitsugi.animelist.data.cloudstream.CsPluginLoader
import com.kitsugi.animelist.data.cloudstream.CsStreamRunner
import com.kitsugi.animelist.data.cloudstream.CsTitleMatcher
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.remote.DetailCache
import com.kitsugi.animelist.data.remote.KitsugiMediaTabsClient
import com.kitsugi.animelist.data.remote.KitsugiStreamingEpisode
import com.kitsugi.animelist.model.MediaType
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Oynatıcıdaki **Bölüm Listesi** çekmecesini her koşulda doldurur.
 *
 * Neden gerekliydi?
 *  - `KitsugiPlayerViewModel.loadEpisodes()` yalnızca `malId` **veya** `aniListId` varsa
 *    çalışıyordu. TMDB/Simkl ile açılan dizilerde, yalnızca eklenti (CS3) URL'siyle
 *    oynatılan içeriklerde ya da kimlik çözümü başarısız olduğunda liste **tamamen boş**
 *    kalıyordu ("Bölüm Listesi" çekmecesi bomboş açılıyordu).
 *
 * Çözüm — sırayla denenip birleştirilen kaynaklar:
 *  1. **Detay sayfası önbelleği** ([DetailCache]) — kullanıcı zaten detay sayfasını açtıysa
 *     bölümler yerelde hazırdır, ağ isteği gerekmez.
 *  2. **Meta veri API'leri** — MAL/Jikan, AniList, TMDB (sezon bölümleri, adlar ve görseller).
 *  3. **Açık eklenti (CS3)** — içerik bir eklentiden açıldıysa eklentinin kendi bölüm listesi
 *     (`LoadResponse.episodes`) okunur; bilinen URL yoksa başlık aramasıyla en iyi eşleşme
 *     bulunup onun bölüm listesi kullanılır.
 *  4. **Sentez** — hiçbiri yanıt vermezse toplam bölüm sayısı (biliniyorsa) ya da en azından
 *     o an oynatılan bölüme kadar numaralı satırlar üretilir; çekmece asla boş kalmaz.
 *
 * Tüm çağrılar IO dispatcher'ında ve zaman aşımı korumalıdır: bir kaynak yavaşlarsa
 * diğerleri devreye girer, çekmece kilitlenmez.
 */
class PlayerEpisodeListResolver private constructor(private val context: Context) {

    /** Bölüm listesini çözmek için gereken oynatıcı bağlamı. */
    data class Request(
        val malId: Int? = null,
        val aniListId: Int? = null,
        val tmdbId: Int? = null,
        val season: Int = 1,
        val currentEpisode: Int = 1,
        val isMovie: Boolean = false,
        val totalEpisodes: Int? = null,
        val title: String? = null,
        val alternativeTitles: List<String> = emptyList(),
        val startYear: Int? = null,
        val cs3Url: String? = null,
        val cs3ApiName: String? = null,
        /** Oynatıcının açıldığı kaynak anahtarı (mal/anilist/tmdb/bangumi/shikimori/kitsu…). */
        val sourceKey: String? = null
    )

    companion object {
        private const val TAG = "PlayerEpisodeList"

        /** Çekmecede "yeterli" sayılan bölüm satırı sayısı. */
        private const val ENOUGH_EPISODES = 8

        /** Meta veri çağrısı için üst sınır (ms). */
        private const val METADATA_TIMEOUT_MS = 18_000L

        /** Eklenti (CS3) yolu için üst sınır (ms) — arama + detay + bölüm listesi. */
        private const val ADDON_TIMEOUT_MS = 28_000L

        /** Sentez sırasında üretilecek azami satır (kontrolsüz liste üretimini engeller). */
        private const val MAX_SYNTHETIC_EPISODES = 200

        fun create(context: Context): PlayerEpisodeListResolver =
            PlayerEpisodeListResolver(context.applicationContext)
    }

    private val mediaTabsClient = KitsugiMediaTabsClient()

    /**
     * Bölüm listesini çözer. Dönen liste **asla `null` değildir**; filmler ve bölümü
     * olmayan içerikler için boş liste döner.
     */
    suspend fun resolve(request: Request): List<KitsugiStreamingEpisode> = withContext(Dispatchers.IO) {
        if (request.isMovie) return@withContext emptyList()

        val merged = sortedMapOf<Int, KitsugiStreamingEpisode>()

        // ── 1) Detay sayfası önbelleği (ağ isteği yok) ──────────────────────────
        absorb(merged, cachedEpisodes(request))
        if (isComplete(merged, request)) return@withContext finalize(merged, request)

        // ── 2) Meta veri API'leri (MAL / AniList / TMDB) ────────────────────────
        absorb(merged, metadataEpisodes(request))
        if (isComplete(merged, request)) return@withContext finalize(merged, request)

        // ── 3) Açık eklenti (CS3) ──────────────────────────────────────────────
        absorb(merged, addonEpisodes(request))
        if (merged.isNotEmpty()) return@withContext finalize(merged, request)

        // ── 4) Hiçbir kaynak yanıt vermedi: en azından numaralı satırlar ────────
        Log.w(TAG, "Bölüm listesi hiçbir kaynaktan gelmedi; sentetik liste üretiliyor (${describe(request)})")
        finalize(merged, request)
    }

    // ─── 1. Detay sayfası önbelleği ────────────────────────────────────────────

    private fun cachedEpisodes(request: Request): List<KitsugiStreamingEpisode> {
        val sourceKey = request.sourceKey?.trim()?.lowercase(Locale.ROOT)
        val candidates = buildList {
            val keyedId = when (sourceKey) {
                "mal", "jikan" -> request.malId
                "anilist" -> request.aniListId
                "tmdb" -> request.tmdbId
                else -> null
            }
            if (!sourceKey.isNullOrBlank() && keyedId != null && keyedId > 0) {
                add(DetailCache.getMediaEpisodes(sourceKey, keyedId))
            }
            request.malId?.takeIf { it > 0 }?.let { add(DetailCache.getMediaEpisodes("mal", it)) }
            request.aniListId?.takeIf { it > 0 }?.let { add(DetailCache.getMediaEpisodes("anilist", it)) }
            request.tmdbId?.takeIf { it > 0 }?.let { add(DetailCache.getMediaEpisodes("tmdb", it)) }
        }
        return candidates.firstOrNull { !it.isNullOrEmpty() }.orEmpty()
    }

    // ─── 2. Meta veri API'leri ────────────────────────────────────────────────

    private suspend fun metadataEpisodes(request: Request): List<KitsugiStreamingEpisode> {
        val season = request.season.takeIf { it > 0 } ?: 1
        // (kaynak, kimlik, gerçek MAL kimliği)
        val attempts = buildList {
            request.malId?.takeIf { it > 0 }?.let { add(Triple("mal", it, it)) }
            request.aniListId?.takeIf { it > 0 }?.let { add(Triple("anilist", it, request.malId)) }
            request.tmdbId?.takeIf { it > 0 }?.let { add(Triple("tmdb", it, request.malId)) }
            // Yalnızca kaynak anahtarı biliniyorsa (ör. bangumi/shikimori) son bir deneme.
            val sourceKey = request.sourceKey?.trim()?.lowercase(Locale.ROOT)
            if (!sourceKey.isNullOrBlank() && none { it.first == sourceKey }) {
                val id = when (sourceKey) {
                    "bangumi", "shikimori", "kitsu", "simkl", "jikan" -> request.malId ?: request.aniListId
                    else -> null
                }
                if (id != null && id > 0) add(Triple(sourceKey, id, request.malId))
            }
        }

        for ((source, id, realMalId) in attempts) {
            val list = runCatching {
                withTimeoutOrNull(METADATA_TIMEOUT_MS) {
                    mediaTabsClient.fetchEpisodes(
                        source = source,
                        externalId = id,
                        mediaType = MediaType.Anime,
                        realMalId = realMalId?.takeIf { it > 0 },
                        totalEpisodes = request.totalEpisodes,
                        context = context,
                        targetSeason = season,
                        tmdbId = request.tmdbId
                    )
                }
            }.getOrElse { error ->
                Log.w(TAG, "Meta veri bölüm çekimi başarısız ($source/$id): ${error.message}")
                null
            }
            if (!list.isNullOrEmpty()) {
                Log.d(TAG, "Bölüm listesi meta veriden geldi: $source/$id → ${list.size} satır")
                return list
            }
        }
        return emptyList()
    }

    // ─── 3. Açık eklenti (CS3) ────────────────────────────────────────────────

    private suspend fun addonEpisodes(request: Request): List<KitsugiStreamingEpisode> {
        val enabledPlugins = runCatching {
            KitsugiDatabase.getDatabase(context).csPluginDao().getEnabledPlugins()
        }.getOrElse { emptyList() }
        if (enabledPlugins.isEmpty()) return emptyList()

        // Eklentileri belleğe al (APIHolder.allProviders'ı doldurur).
        enabledPlugins.forEach { plugin ->
            runCatching { CsPluginLoader.loadExtension(context, plugin.id) }
        }

        return withTimeoutOrNull<List<KitsugiStreamingEpisode>>(ADDON_TIMEOUT_MS) {
            // a) Elimizde eklenti URL'si var: doğrudan detay sayfasını oku.
            val directUrl = request.cs3Url?.takeIf { it.isNotBlank() }
            if (directUrl != null) {
                val api = resolveApi(request.cs3ApiName, enabledPlugins.map { it.name })
                if (api != null) {
                    val episodes = episodesFromLoadResponse(
                        CsStreamRunner.safeLoad(api, directUrl),
                        request
                    )
                    if (episodes.isNotEmpty()) {
                        Log.d(TAG, "Bölüm listesi eklentiden geldi (${api.name}, doğrudan URL): ${episodes.size}")
                        return@withTimeoutOrNull episodes
                    }
                }
            }

            // b) Başlık araması: en iyi eşleşmeyi bul, detay sayfasından bölümleri oku.
            val queries = (listOfNotNull(request.title) + request.alternativeTitles)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinctBy { CsTitleMatcher.normalizeTitleForMatch(it) }
                .take(2)

            for (query in queries) {
                val results = runCatching { CsStreamRunner.searchAllAddons(context, query) }
                    .getOrElse {
                        Log.w(TAG, "Eklenti araması başarısız ($query): ${it.message}")
                        emptyList()
                    }
                for ((api, match) in rankMatches(results, request)) {
                    val episodes = episodesFromLoadResponse(
                        CsStreamRunner.safeLoad(api, match.url),
                        request
                    )
                    if (episodes.isNotEmpty()) {
                        Log.d(TAG, "Bölüm listesi eklentiden geldi (${api.name}, eşleşme=${match.name}): ${episodes.size}")
                        return@withTimeoutOrNull episodes
                    }
                }
            }
            emptyList()
        }.orEmpty()
    }

    private fun resolveApi(apiName: String?, knownNames: List<String>): com.lagradost.cloudstream3.MainAPI? {
        val all = APIHolder.allProviders
        if (all.isEmpty()) return null
        val wanted = apiName?.trim()
        if (!wanted.isNullOrBlank()) {
            all.firstOrNull { it.name.equals(wanted, ignoreCase = true) }?.let { return it }
        }
        // Eklenti adı bilinmiyorsa yüklü eklentilerden ilkini kullan.
        val fallbackName = knownNames.firstOrNull()
        return all.firstOrNull { api ->
            fallbackName != null && api.name.equals(fallbackName, ignoreCase = true)
        }
    }

    /**
     * Arama sonuçlarını başlık benzerliğine göre sıralar. Yıl biliniyorsa eşleşen
     * sonuçlar öne alınır; tamamen alakasız sonuçlar elenir.
     */
    private fun rankMatches(
        results: List<Pair<com.lagradost.cloudstream3.MainAPI, com.lagradost.cloudstream3.SearchResponse>>,
        request: Request
    ): List<Pair<com.lagradost.cloudstream3.MainAPI, com.lagradost.cloudstream3.SearchResponse>> {
        if (results.isEmpty()) return emptyList()
        val wanted = (listOfNotNull(request.title) + request.alternativeTitles)
            .map { CsTitleMatcher.normalizeTitleForMatch(it) }
            .filter { it.isNotBlank() }

        fun score(name: String?): Int {
            val normalized = CsTitleMatcher.normalizeTitleForMatch(name.orEmpty())
            if (normalized.isBlank()) return 0
            var best = 0
            for (target in wanted) {
                best = maxOf(
                    best,
                    when {
                        normalized == target -> 100
                        normalized.contains(target) || target.contains(normalized) -> 70
                        else -> 0
                    }
                )
            }
            return best
        }

        return results
            .map { it to score(it.second.name) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .distinctBy { it.second.url }
            .take(3)
    }

    /**
     * Bir eklenti detay yanıtındaki bölümleri [KitsugiStreamingEpisode]'a çevirir.
     * Sezon bilgisi varsa istenen sezona süzülür.
     */
    private fun episodesFromLoadResponse(
        response: LoadResponse?,
        request: Request
    ): List<KitsugiStreamingEpisode> {
        if (response == null) return emptyList()

        val rawEpisodes: List<Any> = when (response) {
            is AnimeLoadResponse -> response.episodes.values.flatten()
            is TvSeriesLoadResponse -> response.episodes
            else -> emptyList()
        }
        if (rawEpisodes.isEmpty()) return emptyList()

        val mapped = rawEpisodes.mapNotNull { raw ->
            val number = CsEpisodeMatcher.getEpisodeNumber(raw) ?: return@mapNotNull null
            if (number <= 0) return@mapNotNull null
            val season = CsEpisodeMatcher.getEpisodeSeason(raw)
            val episode = raw as? Episode
            val name = episode?.name?.trim()
            KitsugiStreamingEpisode(
                title = name?.takeIf { it.isNotBlank() } ?: "Bölüm $number",
                thumbnail = episode?.posterUrl,
                url = null,
                site = "Eklenti",
                seasonNumber = season ?: request.season.takeIf { it > 0 } ?: 1,
                episodeNumber = number
            ) to season
        }
        if (mapped.isEmpty()) return emptyList()

        val targetSeason = request.season.takeIf { it > 0 } ?: 1
        val seasons = mapped.mapNotNull { it.second }.toSet()
        // Bölümlerde sezon bilgisi varsa ve hedef sezon listede geçiyorsa süz.
        val filtered = if (seasons.isNotEmpty() && seasons.size > 1 && seasons.contains(targetSeason)) {
            mapped.filter { it.second == null || it.second == targetSeason }
        } else {
            mapped
        }

        return filtered
            .map { it.first }
            .distinctBy { it.episodeNumber }
            .sortedBy { it.episodeNumber }
    }

    // ─── Birleştirme / son işleme ─────────────────────────────────────────────

    /** Yeni satırları birleştirir; var olan satırın adı/görseli boşsa zenginleştirir. */
    private fun absorb(target: MutableMap<Int, KitsugiStreamingEpisode>, incoming: List<KitsugiStreamingEpisode>) {
        for (episode in incoming) {
            val number = episode.episodeNumber ?: continue
            if (number <= 0) continue
            val existing = target[number]
            target[number] = when {
                existing == null -> episode
                else -> existing.copy(
                    title = existing.title.takeIf { it.isNotBlank() && !isPlaceholderTitle(it) }
                        ?: episode.title,
                    thumbnail = existing.thumbnail?.takeIf { it.isNotBlank() } ?: episode.thumbnail,
                    seasonNumber = existing.seasonNumber ?: episode.seasonNumber
                )
            }
        }
    }

    private fun isPlaceholderTitle(title: String): Boolean =
        title.matches(Regex("""^#?\d+\s*[–-]\s*.+$""")) ||
            title.matches(Regex("""^(Bölüm|Bolum|Episode|Ep\.?)\s*\d+$""", RegexOption.IGNORE_CASE))

    /** Liste "yeterli" mi? (yeterli satır + oynatılan bölüm + bir sonraki bölüm) */
    private fun isComplete(map: Map<Int, KitsugiStreamingEpisode>, request: Request): Boolean {
        if (map.size < ENOUGH_EPISODES) return false
        if (request.currentEpisode <= 0) return true
        return map.containsKey(request.currentEpisode)
    }

    /**
     * Numaraları 1..N aralığında sürekli hâle getirir, eksik numaraları yer tutucu
     * satırlarla tamamlar. Böylece listede "boşluk" görünmez ve kullanıcı her zaman
     * önceki/sonraki bölüme dokunabilir.
     */
    private fun finalize(
        map: Map<Int, KitsugiStreamingEpisode>,
        request: Request
    ): List<KitsugiStreamingEpisode> {
        val season = request.season.takeIf { it > 0 } ?: 1

        if (map.isEmpty()) {
            return syntheticEpisodes(request, season)
        }

        val highestKnown = map.keys.maxOrNull() ?: 0
        val total = request.totalEpisodes?.takeIf { it > 0 } ?: 0
        // Toplam bölüm sayısı biliniyorsa ve elimizde çok az satır varsa tamamla.
        val targetMax = maxOf(
            highestKnown,
            request.currentEpisode.coerceAtLeast(0),
            if (map.size <= 2 && total > 0) total else 0
        ).coerceAtMost(MAX_SYNTHETIC_EPISODES)

        if (targetMax <= 0) return map.values.sortedBy { it.episodeNumber }

        return (1..targetMax).map { number ->
            map[number] ?: KitsugiStreamingEpisode(
                title = "Bölüm $number",
                thumbnail = null,
                url = null,
                site = "Kitsugi",
                seasonNumber = season,
                episodeNumber = number
            )
        }
    }

    /** Hiçbir kaynak veri vermezse: bilinen toplam ya da en azından oynatılan bölüme kadar. */
    private fun syntheticEpisodes(request: Request, season: Int): List<KitsugiStreamingEpisode> {
        val total = request.totalEpisodes?.takeIf { it > 0 } ?: 0
        val maxEpisode = when {
            total > 0 -> total
            request.currentEpisode > 0 -> request.currentEpisode
            else -> 1
        }.coerceIn(1, MAX_SYNTHETIC_EPISODES)

        return (1..maxEpisode).map { number ->
            KitsugiStreamingEpisode(
                title = "Bölüm $number",
                thumbnail = null,
                url = null,
                site = "Kitsugi",
                seasonNumber = season,
                episodeNumber = number
            )
        }
    }

    private fun describe(request: Request): String =
        "mal=${request.malId} anilist=${request.aniListId} tmdb=${request.tmdbId} " +
            "s=${request.season} e=${request.currentEpisode} cs3=${request.cs3ApiName}"
}
