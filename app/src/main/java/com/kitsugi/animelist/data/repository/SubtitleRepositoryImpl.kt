package com.kitsugi.animelist.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kitsugi.animelist.R
import com.kitsugi.animelist.core.player.OpenSubtitlesHasher
import com.kitsugi.animelist.data.local.AddonPresets
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.local.ManagedAddonEntity
import com.kitsugi.animelist.data.remote.AddonStreamClient
import com.kitsugi.animelist.data.remote.StremioSubtitleResourceSpec
import com.kitsugi.animelist.data.remote.StremioSubtitleRoute
import com.kitsugi.animelist.data.remote.StremioSubtitleTask
import com.kitsugi.animelist.data.remote.SubtitleIdentityCandidates
import com.kitsugi.animelist.data.remote.StremioSubtitleRouting as UnifiedSubtitleRouting
import com.kitsugi.animelist.utils.KitsugiStrings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class Subtitle(
    val id: String,
    val url: String,
    val lang: String,
    val addonName: String,
    val addonLogo: String? = null,
    val format: String? = null // "ass", "srt", "vtt" vb.
)

/**
 * Birleşik Stremio Altyazı Ortaklık Protokolü ("Unified Subtitle Protocol") sorgu girdisi.
 * Çözümlenmiş tüm harici kimlikleri ve aday başlıkları tek seferde taşır; böylece her
 * eklenti yalnızca kendi manifest yeteneklerine (`subtitles.types` + `idPrefixes`) uyan
 * kanonik kimliklerle sorgulanır.
 */
data class UnifiedSubtitleQuery(
    val isMovie: Boolean,
    val season: Int = 1,
    val episode: Int = 1,
    val imdbId: String? = null,
    val tmdbId: Int? = null,
    val kitsuId: Int? = null,
    val aniListId: Int? = null,
    val malId: Int? = null,
    val tvdbId: Int? = null,
    val bangumiId: Int? = null,
    val simklId: Int? = null,
    val primaryTitle: String? = null,
    val candidateTitles: List<String> = emptyList(),
    val videoUrl: String? = null,
    val videoHeaders: Map<String, String>? = null,
    val filename: String? = null,
    val precomputedHash: OpenSubtitlesHasher.HashResult? = null
)

/**
 * SubtitleRepository — Stremio ortaklık protokolü uyumlu altyazı eklenti yönlendiricisi.
 *
 *  1. Veritabanında hiç altyazı eklentisi yoksa (ör. kullanıcı Eklentiler ekranını hiç açmadıysa
 *     veya TV arayüzündeyse) varsayılan altyazı eklentilerini anında tohumlar.
 *  2. Her eklentiyi manifestindeki `subtitles` kaynak tipleri ve `idPrefixes` bildirimiyle
 *     eşleşen kanonik kimliklerle (`tt...`, `tmdb:...`, `kitsu:...`, özel önekler) sorgular.
 *  3. `AniSub.co` için IMDb + AniList + çoklu başlık adaylarıyla tek bir birleşik sorgu çalıştırır.
 *  4. OpenSubtitles hash hesabını akış ipucundan (`behaviorHints`) sıfır gecikmeyle alır veya
 *     yalnızca uygun doğrudan HTTP akışlarında kısa zaman bütçesiyle (2.5 sn) hesaplar.
 */
class SubtitleRepositoryImpl(private val context: Context) {

    private val appContext = context.applicationContext
    private val addonDao = KitsugiDatabase.getDatabase(appContext).managedAddonDao()
    private val addonClient = sharedAddonClient

    companion object {
        private const val TAG = "SubtitleRepository"
        private const val PER_ADDON_TIMEOUT_MS = 15_000L
        private const val ANISUB_TIMEOUT_MS = 20_000L
        private const val HASH_COMPUTE_TIMEOUT_MS = 2_500L
        private const val CIRCUIT_BREAKER_THRESHOLD = 5
        private const val CIRCUIT_BREAKER_COOLDOWN_MS = 90_000L

        private val sharedAddonClient = AddonStreamClient()
        private val gson = Gson()
        private val seedMutex = Mutex()
        private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // Circuit breaker: manifestUrl -> ardışık zaman aşımı/ağ hatası sayısı
        private val circuitBreakers = ConcurrentHashMap<String, CircuitBreakerState>()

        private data class CircuitBreakerState(
            @Volatile var failureCount: Int = 0,
            @Volatile var lastFailureTimeMs: Long = 0L
        )
    }

    /**
     * Birleşik ortaklık protokolü üzerinden tüm aktif altyazı eklentilerini paralel sorgular.
     */
    suspend fun fetchUnifiedSubtitles(query: UnifiedSubtitleQuery): List<Subtitle> = coroutineScope {
        ensureDefaultSubtitleAddonsSeeded()

        val enabledAddons = addonDao.getEnabledAddons()
        val subtitleAddons = enabledAddons.filter { isSubtitleCapableAddon(it) }
        if (subtitleAddons.isEmpty()) {
            Log.w(TAG, "Hiç aktif altyazı eklentisi yok (enabled=${enabledAddons.size})")
            return@coroutineScope emptyList()
        }

        val cleanTitles = (listOfNotNull(query.primaryTitle) + query.candidateTitles)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }
        val mainTitle = cleanTitles.firstOrNull()

        val identityCandidates = SubtitleIdentityCandidates.build(
            movie = query.isMovie,
            season = query.season.coerceAtLeast(1),
            episode = query.episode.coerceAtLeast(1),
            imdbId = query.imdbId,
            kitsuId = query.kitsuId,
            aniListId = query.aniListId,
            malId = query.malId,
            tmdbId = query.tmdbId,
            tvdbId = query.tvdbId,
            bangumiId = query.bangumiId,
            simklId = query.simklId
        )

        // Anime film/OVA kayıtları bazen dizi (1x1), bazen movie olarak indekslenir:
        // birincil görevler boş dönerse karşı türdeki IMDb/TMDB adayını yedek olarak hazır tut.
        val alternateModeCandidates = if (query.episode <= 1 && query.season <= 1) {
            SubtitleIdentityCandidates.build(
                movie = !query.isMovie,
                season = 1,
                episode = 1,
                imdbId = query.imdbId,
                kitsuId = query.kitsuId,
                aniListId = null,
                malId = null,
                tmdbId = query.tmdbId
            )
        } else {
            emptyList()
        }

        // OpenSubtitles hash: akıştan hazır geldiyse 0 ms; değilse yalnızca uygun HTTP URL'de en çok 2.5 sn.
        val hashJob = async(Dispatchers.IO) {
            if (query.precomputedHash != null) return@async query.precomputedHash
            val url = query.videoUrl?.takeIf { OpenSubtitlesHasher.canProbeUrl(it) } ?: return@async null
            withTimeoutOrNull(HASH_COMPUTE_TIMEOUT_MS) {
                runCatching { OpenSubtitlesHasher.compute(url, query.videoHeaders) }.getOrNull()
            }
        }

        val cleanFilename = sanitizeSubtitleFilenameHint(query.filename)

        val primaryContentType = if (query.isMovie) "movie" else "series"
        val secondaryContentType = if (query.isMovie) "series" else "movie"

        Log.d(
            TAG,
            "Unified subtitle fetch: addons=${subtitleAddons.size}, movie=${query.isMovie}, " +
                "S=${query.season}E=${query.episode}, imdb=${query.imdbId}, tmdb=${query.tmdbId}, " +
                "kitsu=${query.kitsuId}, aniList=${query.aniListId}, mal=${query.malId}, " +
                "candidates=${identityCandidates.map { "${it.type}:${it.id}" }}, file=$cleanFilename"
        )

        val deferredByAddon = subtitleAddons.map { addon ->
            async(Dispatchers.IO) {
                if (isCircuitOpen(addon)) {
                    Log.w(TAG, "Circuit breaker açık, eklenti atlandı: '${addon.name}'")
                    return@async emptyList()
                }

                // AniSub.co: özel paket + API + Stremio hibrit istemcisi — tek birleşik çağrı.
                if (addon.manifestUrl.contains("anisub.co", ignoreCase = true)) {
                    return@async queryAniSubOnce(
                        addon = addon,
                        query = query,
                        mainTitle = mainTitle,
                        cleanTitles = cleanTitles,
                        cleanFilename = cleanFilename
                    )
                }

                val spec = resolveSubtitleSpec(addon)
                val route = StremioSubtitleRoute(
                    name = addon.name,
                    manifestUrl = addon.manifestUrl,
                    types = spec.types?.joinToString(",") ?: "*",
                    idPrefixes = spec.idPrefixes
                )
                var primaryTasks = UnifiedSubtitleRouting.buildTasks(
                    routes = listOf(route),
                    contentType = primaryContentType,
                    candidates = identityCandidates
                )

                // Hiç harici kimlik çözülemediyse ve eklenti belirli bir prefix zorunlu kılmıyorsa
                // son çare olarak Latin başlık sorgusu oluştur.
                if (primaryTasks.isEmpty() && spec.idPrefixes.isNullOrEmpty() && cleanTitles.isNotEmpty()) {
                    val fallbackTitle = SubtitleSearchQueries.latinQueries(cleanTitles).firstOrNull()
                    if (!fallbackTitle.isNullOrBlank()) {
                        val rawTitleId = if (query.isMovie) {
                            fallbackTitle
                        } else {
                            "$fallbackTitle:${query.season.coerceAtLeast(1)}:${query.episode.coerceAtLeast(1)}"
                        }
                        val titleType = UnifiedSubtitleRouting.resolveRequestType(route.types, primaryContentType)
                        if (titleType != null) {
                            primaryTasks = listOf(
                                StremioSubtitleTask(
                                    addonName = addon.name,
                                    manifestUrl = addon.manifestUrl,
                                    requestType = titleType,
                                    videoId = rawTitleId
                                )
                            )
                        }
                    }
                }

                val secondaryTasks = if (alternateModeCandidates.isNotEmpty()) {
                    UnifiedSubtitleRouting.buildTasks(
                        routes = listOf(route),
                        contentType = secondaryContentType,
                        candidates = alternateModeCandidates
                    ).filterNot { alt -> primaryTasks.any { it.requestType == alt.requestType && it.videoId == alt.videoId } }
                } else {
                    emptyList()
                }

                if (primaryTasks.isEmpty() && secondaryTasks.isEmpty()) {
                    Log.d(
                        TAG,
                        "Addon '${addon.name}' için uyumlu kimlik yok (types=${spec.types}, prefixes=${spec.idPrefixes})"
                    )
                    return@async emptyList()
                }

                val hashResult = runCatching { hashJob.await() }.getOrNull()
                val extraParams = buildMap<String, String> {
                    if (hashResult != null) {
                        put("videoHash", hashResult.hash)
                        put("videoSize", hashResult.size.toString())
                    }
                    if (!cleanFilename.isNullOrBlank()) {
                        put("filename", cleanFilename)
                    }
                }.ifEmpty { null }

                var anyAttemptCompleted = false
                val collected = mutableListOf<Subtitle>()

                val completedWithinBudget = withTimeoutOrNull(PER_ADDON_TIMEOUT_MS) {
                    for (task in primaryTasks) {
                        val items = addonClient.fetchSubtitles(
                            context = appContext,
                            manifestUrl = addon.manifestUrl,
                            type = task.requestType,
                            id = task.videoId,
                            extraParams = extraParams,
                            aniListId = query.aniListId,
                            animeTitle = mainTitle,
                            episode = query.episode,
                            season = query.season,
                            animeTitles = cleanTitles
                        )
                        anyAttemptCompleted = true
                        if (items.isNotEmpty()) {
                            collected += mapResponseItems(addon, items)
                            // Birincil yüksek güvenilirlikli kimlik (ör. tt...) sonuç döndürdüyse
                            // aynı eklentiye gereksiz ikinci kimlik sorgusu atma.
                            break
                        }
                    }

                    if (collected.isEmpty() && secondaryTasks.isNotEmpty()) {
                        for (task in secondaryTasks) {
                            val items = addonClient.fetchSubtitles(
                                context = appContext,
                                manifestUrl = addon.manifestUrl,
                                type = task.requestType,
                                id = task.videoId,
                                extraParams = extraParams,
                                aniListId = query.aniListId,
                                animeTitle = mainTitle,
                                episode = query.episode,
                                season = query.season,
                                animeTitles = cleanTitles
                            )
                            anyAttemptCompleted = true
                            if (items.isNotEmpty()) {
                                collected += mapResponseItems(addon, items)
                                break
                            }
                        }
                    }
                    true
                }

                if (completedWithinBudget == true || anyAttemptCompleted) {
                    recordAddonOutcome(addon.manifestUrl, succeeded = true)
                } else {
                    recordAddonOutcome(addon.manifestUrl, succeeded = false)
                    Log.w(TAG, "Addon '${addon.name}' ${PER_ADDON_TIMEOUT_MS}ms zaman aşımına uğradı")
                }

                Log.d(TAG, "Addon '${addon.name}': ${collected.size} altyazı döndü")
                collected
            }
        }

        val allSubtitles = deferredByAddon.awaitAll().flatten()
        Log.d(TAG, "Unified toplam ${allSubtitles.size} altyazı bulundu (${subtitleAddons.size} eklenti)")
        deduplicateSubtitles(allSubtitles)
    }

    /**
     * Geriye dönük uyumlu tekil sorgu (örn. AnimeDownloader vb. çağrıcılar için).
     */
    suspend fun getSubtitles(
        type: String,
        id: String,
        videoUrl: String? = null,
        videoHeaders: Map<String, String>? = null,
        filename: String? = null,
        aniListId: Int? = null,
        animeTitle: String? = null,
        episode: Int? = null,
        season: Int? = null,
        precomputedHash: OpenSubtitlesHasher.HashResult? = null,
        hashAlreadyComputed: Boolean = false
    ): List<Subtitle> = coroutineScope {
        ensureDefaultSubtitleAddonsSeeded()

        val normalizedType = if (type.equals("tv", ignoreCase = true)) "series" else type.lowercase(Locale.ROOT)
        val allAddons = addonDao.getEnabledAddons()
        val subtitleAddons = allAddons.filter { addon ->
            if (!isSubtitleCapableAddon(addon)) return@filter false
            if (addon.subtitleTypes != null) {
                StremioSubtitleRouting.requestType(addon.subtitleTypes, normalizedType) != null
            } else {
                true
            }
        }

        if (subtitleAddons.isEmpty()) {
            Log.w(TAG, "Hiç aktif altyazı addon'u bulunamadı — type=$normalizedType, id=$id")
            return@coroutineScope emptyList()
        }

        val cleanFilename = sanitizeSubtitleFilenameHint(filename)

        val hashJob = async(Dispatchers.IO) {
            if (hashAlreadyComputed || precomputedHash != null) return@async precomputedHash
            val url = videoUrl?.takeIf { OpenSubtitlesHasher.canProbeUrl(it) } ?: return@async null
            withTimeoutOrNull(HASH_COMPUTE_TIMEOUT_MS) {
                runCatching { OpenSubtitlesHasher.compute(url, videoHeaders) }.getOrNull()
            }
        }

        val deferredResults = subtitleAddons.map { addon ->
            async(Dispatchers.IO) {
                if (isCircuitOpen(addon)) return@async emptyList()

                // Prefix uyumsuzsa (ör. yts-subtitles "tt" isterken "kitsu:..." gelmişse) boşuna sorgulama.
                val spec = resolveSubtitleSpec(addon)
                if (!addon.manifestUrl.contains("anisub.co", ignoreCase = true) &&
                    !spec.idPrefixes.isNullOrEmpty() &&
                    spec.idPrefixes.none { id.startsWith(it) }
                ) {
                    return@async emptyList()
                }

                val hashResult = runCatching { hashJob.await() }.getOrNull()
                val extraParams = buildMap<String, String> {
                    if (hashResult != null) {
                        put("videoHash", hashResult.hash)
                        put("videoSize", hashResult.size.toString())
                    }
                    if (!cleanFilename.isNullOrBlank()) put("filename", cleanFilename)
                }.ifEmpty { null }

                val timeoutMs = if (addon.manifestUrl.contains("anisub.co", ignoreCase = true)) {
                    ANISUB_TIMEOUT_MS
                } else {
                    PER_ADDON_TIMEOUT_MS
                }

                val items = withTimeoutOrNull(timeoutMs) {
                    try {
                        addonClient.fetchSubtitles(
                            context = appContext,
                            manifestUrl = addon.manifestUrl,
                            type = StremioSubtitleRouting.requestType(addon.subtitleTypes, normalizedType)
                                ?: normalizedType,
                            id = id,
                            extraParams = extraParams,
                            aniListId = aniListId,
                            animeTitle = animeTitle,
                            episode = episode,
                            season = season
                        )
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (e: Exception) {
                        Log.w(TAG, "Addon '${addon.name}' fetch hatası: ${e.message}")
                        null
                    }
                }

                if (items != null) {
                    recordAddonOutcome(addon.manifestUrl, succeeded = true)
                    mapResponseItems(addon, items)
                } else {
                    recordAddonOutcome(addon.manifestUrl, succeeded = false)
                    emptyList()
                }
            }
        }

        deduplicateSubtitles(deferredResults.awaitAll().flatten())
    }

    private suspend fun queryAniSubOnce(
        addon: ManagedAddonEntity,
        query: UnifiedSubtitleQuery,
        mainTitle: String?,
        cleanTitles: List<String>,
        cleanFilename: String?
    ): List<Subtitle> {
        val safeSeason = query.season.coerceAtLeast(1)
        val safeEpisode = query.episode.coerceAtLeast(1)
        val requestType = if (query.isMovie) "movie" else "series"
        val requestId = when {
            !query.imdbId.isNullOrBlank() ->
                if (query.isMovie) query.imdbId.trim() else "${query.imdbId.trim()}:$safeSeason:$safeEpisode"
            query.kitsuId != null && query.kitsuId > 0 ->
                if (query.isMovie) "kitsu:${query.kitsuId}" else "kitsu:${query.kitsuId}:$safeEpisode"
            !mainTitle.isNullOrBlank() ->
                if (query.isMovie) mainTitle else "$mainTitle:$safeSeason:$safeEpisode"
            else -> return emptyList()
        }
        val extraParams = cleanFilename?.let { mapOf("filename" to it) }

        val items = withTimeoutOrNull(ANISUB_TIMEOUT_MS) {
            try {
                addonClient.fetchSubtitles(
                    context = appContext,
                    manifestUrl = addon.manifestUrl,
                    type = requestType,
                    id = requestId,
                    extraParams = extraParams,
                    aniListId = query.aniListId,
                    animeTitle = mainTitle,
                    episode = if (query.isMovie) null else safeEpisode,
                    season = if (query.isMovie) null else safeSeason,
                    animeTitles = cleanTitles
                )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (e: Exception) {
                Log.w(TAG, "AniSub sorgu hatası: ${e.message}")
                null
            }
        }

        return if (items != null) {
            recordAddonOutcome(addon.manifestUrl, succeeded = true)
            Log.d(TAG, "Addon '${addon.name}': ${items.size} altyazı döndü")
            mapResponseItems(addon, items)
        } else {
            recordAddonOutcome(addon.manifestUrl, succeeded = false)
            Log.w(TAG, "Addon '${addon.name}' ${ANISUB_TIMEOUT_MS}ms zaman aşımına uğradı")
            emptyList()
        }
    }

    /**
     * Veritabanında hiç altyazı eklentisi yoksa (`AddonViewModel` hiç açılmamış taze kurulum veya
     * Android TV oturumu) varsayılan Stremio altyazı eklentilerini anında veritabanına ekler ve
     * manifest detaylarını arka planda tazeler.
     */
    private suspend fun ensureDefaultSubtitleAddonsSeeded() {
        val currentAll = runCatching { addonDao.getAllAddons() }.getOrDefault(emptyList())
        if (currentAll.any { isSubtitleCapableAddon(it) }) return

        seedMutex.withLock {
            val recheck = runCatching { addonDao.getAllAddons() }.getOrDefault(emptyList())
            if (recheck.any { isSubtitleCapableAddon(it) }) return

            Log.i(TAG, "Veritabanında altyazı eklentisi bulunamadı — varsayılan altyazı eklentileri tohumlanıyor")
            val urlsToRefresh = mutableListOf<String>()

            for ((idx, rawUrl) in AddonPresets.DEFAULT_SUBTITLE_ADDONS.withIndex()) {
                val normalized = addonClient.normalizeManifestUrl(rawUrl)
                if (normalized.isBlank()) continue
                val exists = recheck.any { it.manifestUrl.trim().equals(normalized, ignoreCase = true) }
                if (exists) continue

                val isYts = normalized.contains("yts-subtitles", ignoreCase = true)
                val fallbackEntity = ManagedAddonEntity(
                    manifestUrl = normalized,
                    name = when {
                        normalized.contains("opensubtitles-v3", ignoreCase = true) -> "OpenSubtitles v3"
                        normalized.contains("yts-subtitles", ignoreCase = true) -> "YTS Subtitles"
                        normalized.contains("turkcealtyaziorg", ignoreCase = true) -> "TurkceAltyazi.org"
                        normalized.contains("altyazidb", ignoreCase = true) -> "AltyaziDB"
                        normalized.contains("anisub.co", ignoreCase = true) -> "AniSub.co"
                        else -> "Subtitle Addon"
                    },
                    description = KitsugiStrings.get(R.string.sistem_altyazi_eklentisi),
                    icon = if (normalized.contains("anisub.co", ignoreCase = true)) {
                        "https://anisub.co/assets/logo.png"
                    } else {
                        null
                    },
                    isEnabled = true,
                    orderIndex = 90 + idx,
                    idPrefixes = if (isYts) gson.toJson(listOf("tt")) else null,
                    streamTypes = null,
                    subtitleTypes = if (isYts) "movie" else "movie,series"
                )
                runCatching { addonDao.insertAddon(fallbackEntity) }
                urlsToRefresh += normalized
            }

            if (urlsToRefresh.isNotEmpty()) {
                backgroundScope.launch {
                    for (manifestUrl in urlsToRefresh) {
                        val resolved = runCatching { addonClient.fetchManifest(manifestUrl) }.getOrNull()
                            ?: continue
                        val existing = runCatching { addonDao.getAllAddons() }
                            .getOrDefault(emptyList())
                            .find { it.manifestUrl.trim().equals(manifestUrl, ignoreCase = true) }
                            ?: continue
                        runCatching {
                            addonDao.updateAddon(
                                resolved.copy(
                                    isEnabled = existing.isEnabled,
                                    orderIndex = existing.orderIndex,
                                    subtitleTypes = resolved.subtitleTypes ?: existing.subtitleTypes ?: "movie,series"
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun isSubtitleCapableAddon(addon: ManagedAddonEntity): Boolean {
        if (!addon.subtitleTypes.isNullOrBlank()) return true
        return looksLikeSubtitleAddon(addon) && addon.streamTypes.isNullOrBlank()
    }

    private fun resolveSubtitleSpec(addon: ManagedAddonEntity): StremioSubtitleResourceSpec {
        val normalizedManifest = addonClient.normalizeManifestUrl(addon.manifestUrl)
        AddonStreamClient.cachedSubtitleSpec(normalizedManifest)?.let { return it }

        val rawTypes = addon.subtitleTypes?.trim()
        val parsedTypes: List<String>? = when {
            rawTypes.isNullOrEmpty() -> listOf("movie", "series")
            rawTypes == "*" -> null
            else -> rawTypes.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .ifEmpty { listOf("movie", "series") }
        }

        // Hem stream hem altyazı sunan birleşik eklentilerde `addon.idPrefixes` alanı stream
        // kaynağının prefix'lerini taşıyabilir; salt-altyazı eklentilerindeyse doğrudan
        // altyazı prefix listesidir.
        val parsedPrefixes: List<String>? = when {
            normalizedManifest.contains("yts-subtitles", ignoreCase = true) -> listOf("tt")
            addon.streamTypes.isNullOrBlank() -> parseIdPrefixes(addon)
            else -> null
        }

        return StremioSubtitleResourceSpec(
            types = parsedTypes,
            idPrefixes = parsedPrefixes
        )
    }

    private fun mapResponseItems(
        addon: ManagedAddonEntity,
        items: List<com.kitsugi.animelist.data.remote.SubtitleResponseItem>
    ): List<Subtitle> {
        return items.mapNotNull { item ->
            val url = item.url?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val lang = item.lang?.trim()?.ifEmpty { "und" } ?: "und"
            Subtitle(
                id = item.id?.takeIf { it.isNotBlank() } ?: url,
                url = url,
                lang = lang,
                addonName = addon.name,
                addonLogo = addon.icon,
                format = normalizeDeclaredFormat(item.format) ?: guessFormat(url)
            )
        }
    }

    private fun deduplicateSubtitles(allSubtitles: List<Subtitle>): List<Subtitle> {
        val seen = LinkedHashSet<String>()
        val deduped = mutableListOf<Subtitle>()
        for (sub in allSubtitles) {
            val key = "${sub.url}|${sub.lang.lowercase(Locale.ROOT)}"
            if (seen.add(key)) {
                deduped += sub
            }
        }
        return deduped
    }

    private fun isCircuitOpen(addon: ManagedAddonEntity): Boolean {
        val state = circuitBreakers[addon.manifestUrl] ?: return false
        if (state.failureCount < CIRCUIT_BREAKER_THRESHOLD) return false
        val elapsed = System.currentTimeMillis() - state.lastFailureTimeMs
        if (elapsed >= CIRCUIT_BREAKER_COOLDOWN_MS) {
            state.failureCount = 0
            return false
        }
        return true
    }

    private fun recordAddonOutcome(manifestUrl: String, succeeded: Boolean) {
        val state = circuitBreakers.getOrPut(manifestUrl) { CircuitBreakerState() }
        if (succeeded) {
            state.failureCount = 0
        } else {
            state.failureCount += 1
            state.lastFailureTimeMs = System.currentTimeMillis()
        }
    }

    /**
     * Yapay veya anlamsız dosya adlarını (ör. uydurma "Title - S01E01.mkv", "index.m3u8", "master.m3u8")
     * temizler; yalnızca gerçek medya sürüm/dosya adlarını Stremio extra segmentine geçirir.
     */
    internal fun sanitizeSubtitleFilenameHint(rawFilename: String?): String? {
        val clean = rawFilename
            ?.trim()
            ?.substringAfterLast('/')
            ?.substringBefore('?')
            ?.substringBefore('#')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val lower = clean.lowercase(Locale.ROOT)
        if (lower in GENERIC_PLAYLIST_FILENAMES) return null
        if (lower.endsWith(".m3u8") || lower.endsWith(".mpd")) return null
        return clean
    }

    /**
     * Addon'un isim veya URL'sine bakarak altyazı addon'u olup olmadığını tahmin eder.
     * subtitleTypes = null olan eski kayıtlar için fallback filtre.
     */
    private fun looksLikeSubtitleAddon(addon: ManagedAddonEntity): Boolean {
        val combined = (addon.name + " " + addon.manifestUrl).lowercase(Locale.ROOT)
        return combined.contains("subtitle") ||
            combined.contains("altyaz") ||
            combined.contains("opensubtitle") ||
            combined.contains("yts-subtitle") ||
            combined.contains("turkcealtyazi") ||
            combined.contains("subdl") ||
            combined.contains("wyzie") ||
            combined.contains("subhero") ||
            combined.contains("anisub") ||
            combined.contains("assrt") ||
            combined.contains("jimaku") ||
            combined.contains("kitsunekko") ||
            combined.contains("caption") ||
            combined.contains("sub.strem")
    }

    /**
     * Addon'un idPrefixes JSON array'ini parse eder.
     * Null veya boş → prefix filtresi yok.
     */
    private fun parseIdPrefixes(addon: ManagedAddonEntity): List<String>? {
        val json = addon.idPrefixes?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            val listType = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(json, listType)
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.w(TAG, "idPrefixes parse hatası (addon='${addon.name}'): ${e.message}")
            null
        }
    }

    /** Addon'un beyan ettiği format adını iç temsile ("ass"/"srt"/"vtt") çevirir. */
    private fun normalizeDeclaredFormat(raw: String?): String? {
        val normalized = raw?.trim()?.lowercase(Locale.ROOT)?.trimStart('.').orEmpty()
        return when (normalized) {
            "ass", "ssa" -> "ass"
            "srt", "subrip", "application/x-subrip" -> "srt"
            "vtt", "webvtt" -> "vtt"
            "ttml", "dfxp" -> "ttml"
            else -> null
        }
    }

    /**
     * URL uzantısından format tahmin eder.
     */
    private fun guessFormat(url: String): String? {
        val path = url.substringBefore("?").substringBefore('#').lowercase(Locale.ROOT)
        return when {
            path.endsWith(".ass") -> "ass"
            path.endsWith(".ssa") -> "ssa"
            path.endsWith(".srt") -> "srt"
            path.endsWith(".vtt") -> "vtt"
            path.endsWith(".ttml") -> "ttml"
            path.endsWith(".dfxp") -> "dfxp"
            path.endsWith(".sub") -> "sub"
            path.endsWith(".smi") || path.endsWith(".sami") -> "smi"
            else -> null // Bilinmeyeni SRT diye etiketleme; indirilen içerik imzasıyla tanınır.
        }
    }
}

private val GENERIC_PLAYLIST_FILENAMES = setOf(
    "index.m3u8",
    "master.m3u8",
    "playlist.m3u8",
    "manifest.mpd",
    "stream",
    "video",
    "play",
    "playback"
)
