package com.kitsugi.animelist.data.repository



import android.content.Context

import android.util.Log

import com.kitsugi.animelist.data.local.AddonPresets

import com.kitsugi.animelist.data.local.KitsugiDatabase

import com.kitsugi.animelist.data.remote.AddonStreamClient

import com.kitsugi.animelist.data.remote.DebridResolver

import com.kitsugi.animelist.data.remote.KitsugiIdResolver

import com.kitsugi.animelist.data.remote.StreamResponseItem

import com.kitsugi.animelist.data.remote.buildStremioStreamTasks

import com.kitsugi.animelist.data.remote.toStreamRoute

import kotlinx.coroutines.async

import kotlinx.coroutines.awaitAll

import kotlinx.coroutines.coroutineScope



class AddonStreamRepository(private val context: Context) {

    private val database = KitsugiDatabase.getDatabase(context.applicationContext)

    private val addonDao = database.managedAddonDao()

    private val addonClient = AddonStreamClient()

    private val debridResolver = DebridResolver(context)



    /**

     * Artık varsayılan eklenti seed edilmez.

     * Kullanıcı eklentilerini manuel olarak ekler.

     */

    suspend fun seedPresetsIfEmpty() {

        // No-op: varsayılan eklentiler kaldırıldı.

    }



    /**

     * Fetches stream sources from all active, compatible addons in parallel for the given episode.

     *

     * Resolves both IMDb and Kitsu IDs in a single ARM API call, then routes each addon to the

     * correct video ID based on the ID prefixes it declares in its manifest:

     *   - Addons that support "kitsu:" prefix  → queried with "kitsu:{kitsuId}:{episode}"

     *   - Addons that support "tt" prefix       → queried with "{imdbId}:{season}:{episode}"

     *   - Addons with no prefix restriction     → queried with IMDb ID as a safe default

     *

     * Results from all addons are combined and de-duplicated by infoHash/URL before returning.

     */

    suspend fun getStreamsForEpisode(

        malId: Int?,

        aniListId: Int?,

        season: Int,

        episode: Int,

        tmdbId: Int? = null,

        isMovie: Boolean = false,

        preferredAddonName: String? = null

    ): List<StreamSource> = coroutineScope {

        // Step 1: Resolve IMDb + Kitsu IDs in one ARM API call

        val resolvedIds = KitsugiIdResolver.resolveIds(malId, aniListId, tmdbId, wikidataImdbFallback = true)

        val imdbId   = resolvedIds.imdbId

        val kitsuId  = resolvedIds.kitsuId



        Log.d("AddonStreamRepository", "Resolved IDs → imdb=$imdbId kitsu=$kitsuId")



        if (imdbId == null && kitsuId == null) {

            Log.w("AddonStreamRepository", "Could not resolve any ID for MAL=$malId AniList=$aniListId")

            return@coroutineScope emptyList()

        }



        val stremioLookup = com.kitsugi.animelist.data.remote.StremioVideoId.forContent(

            isMovie = isMovie,

            imdbId = imdbId,

            kitsuId = kitsuId,

            season = season,

            episode = episode

        )

        val imdbVideoId = stremioLookup.imdbVideoId

        val kitsuVideoId = stremioLookup.kitsuVideoId

        val contentType = stremioLookup.contentType



        // Step 2: Her (addon, video ID) çifti için istek görevleri. Addon hangi kimlik ailesini

        // (kitsu / tt) kabul ediyorsa o ID ile sorgulanır ve istek yolunda addon'un KENDİ ilan ettiği

        // tür kullanılır (ör. yalnızca "anime" ilan eden addon /stream/anime/... ile sorgulanır).

        val enabledAddons = addonDao.getEnabledAddons()

        val preferredAddons = preferredAddonName?.takeIf { it.isNotBlank() }?.let { preferred ->

            enabledAddons.filter { it.name.equals(preferred, ignoreCase = true) }

        }.orEmpty()

        // When a provider is specified, keep this lookup scoped to its manifest. An empty

        // result is handled by the caller's explicit broad-search fallback.

        val allAddons = if (preferredAddonName.isNullOrBlank()) enabledAddons else preferredAddons

        val tasks = buildStremioStreamTasks(

            routes = allAddons.map { it.toStreamRoute() },

            contentType = contentType,

            imdbVideoId = imdbVideoId,

            kitsuVideoId = kitsuVideoId

        )



        if (tasks.isEmpty()) {

            Log.w("AddonStreamRepository", "No compatible addon/ID pairs found — imdb=$imdbVideoId kitsu=$kitsuVideoId")

            return@coroutineScope emptyList()

        }



        Log.d("AddonStreamRepository", "Querying ${tasks.size} addon/ID task(s) in parallel")



        // Step 3: Query all tasks in parallel

        val deferredStreams = tasks.map { task ->

            async {

                Log.d("AddonStreamRepository", "Fetching streams: ${task.addonName} / ${task.requestType} / ${task.videoId}")

                val streams = addonClient.fetchStreams(task.manifestUrl, task.requestType, task.videoId)

                streams.map { responseItem ->
                    responseItem.toStreamSource(
                        addonName = task.addonName,
                        addonId = com.kitsugi.animelist.data.local.WatchHistorySource.stremioAddonId(task.manifestUrl)
                    )
                }

            }

        }



        val allStreams = deferredStreams.awaitAll().flatten()



        // Step 4: De-duplicate by infoHash (or url as fallback) so that addons queried with

        // both IMDb and Kitsu IDs don't return the same torrent twice.

        val seen = LinkedHashSet<String>()

        val deduped = mutableListOf<StreamSource>()

        for (stream in allStreams) {

            val key = stream.infoHash?.lowercase()

                ?: stream.url?.lowercase()

                ?: "${stream.addonName}:${stream.name}:${stream.title}"

            if (seen.add(key)) {

                deduped += stream

            }

        }



        Log.d("AddonStreamRepository", "Total streams: ${allStreams.size} → after de-dup: ${deduped.size}")



        // Step 5: Filter to only streams relevant to this episode (removes Torrentio's

        // whole-season dumps). CS and direct-HTTP streams pass through unfiltered.

        val filtered = EpisodeStreamFilter.filterForEpisode(deduped, season, episode)

        Log.d("AddonStreamRepository", "After episode filter: ${filtered.size}")



        // Step 6: Sort by quality — Cached Debrid > resolution > quality type > size

        val sorted = StreamSorter.sort(filtered)

        Log.d("AddonStreamRepository", "Sorted ${sorted.size} streams, top=${sorted.firstOrNull()?.addonName}")



        sorted

    }



    /**

     * Resolves a torrent stream via Debrid or local P2P engine if it's not a direct HTTP link.

     */

    suspend fun resolveStreamUrl(source: StreamSource): String? {

        val rawUrl = source.url?.trim()

        val isMagnet = rawUrl.isMagnetLink()

        val isTorrentScheme = rawUrl.isTorrentSchemeUrl()



        // 1. Doğrudan oynatılabilir HTTP/HTTPS linki (magnet veya torrent linki değil)

        if (!rawUrl.isNullOrBlank() && !isMagnet && !isTorrentScheme) {

            return rawUrl

        }



        // 2. Torrent veya Magnet akışı -> Hash ve tracker'ları çıkar

        val hash = source.p2pHash

        if (!hash.isNullOrBlank()) {

            val fileIndex = source.fileIndex ?: rawUrl.extractTorrentSchemeFileIdx()

            val trackers = (source.trackers + extractTrackersFromMagnet(rawUrl)).distinct()



            // 2a. Debrid API anahtarı varsa öncelikle Debrid üzerinden çözmeyi dene

            if (!debridResolver.getApiKey().isNullOrBlank()) {

                val debridUrl = try {

                    kotlinx.coroutines.withTimeoutOrNull(15_000L) {

                        debridResolver.resolveHash(hash, fileIndex, source.title)

                    }

                } catch (e: Exception) {

                    Log.w("AddonStreamRepository", "Debrid çözümleme hatası: ${e.message}")

                    null

                }

                if (!debridUrl.isNullOrBlank()) {

                    return debridUrl

                }

            }



            // 2b. Debrid yoksa veya çözülemediyse ve P2P etkinse -> Dahili Nuvio P2P Motoru ile stream et

            if (com.kitsugi.animelist.core.p2p.P2pSettingsRepository.isP2pEnabled() &&

                com.kitsugi.animelist.core.p2p.P2pSettingsRepository.isConsentGranted()

            ) {

                return try {

                    com.kitsugi.animelist.core.p2p.P2pStreamingEngine.startStream(

                        com.kitsugi.animelist.core.p2p.P2pStreamRequest(

                            infoHash = hash,

                            fileIdx = fileIndex,

                            filename = source.title,

                            trackers = trackers

                        )

                    )

                } catch (e: kotlinx.coroutines.CancellationException) {

                    throw e

                } catch (e: Exception) {

                    android.util.Log.e("AddonStreamRepository", "P2P akış başlatma hatası: ${e.message}", e)

                    null

                }

            }

        }

        return null

    }



    fun getDebridApiKey(): String? = debridResolver.getApiKey()



    fun setDebridApiKey(key: String?) {

        debridResolver.setApiKey(key)

    }

}



/** Separate audio URLs supplied by a CloudStream ExtractorLink, with their *own* HTTP headers. */

data class ExternalAudioSource(

    val url: String,

    val requestHeaders: Map<String, String> = emptyMap()

) : java.io.Serializable



data class StreamSource(

    val addonName: String,

    val name: String,

    val title: String,

    val url: String?,

    val infoHash: String?,

    val fileIndex: Int?,

    /** Addon sources[] trackers */

    val trackers: List<String> = emptyList(),

    /** HTTP request headers to attach when playing the stream (from behaviorHints.proxyHeaders.request). */

    val requestHeaders: Map<String, String>? = null,

    /** Exact Stremio behaviorHints used by subtitle services; kept separate from display title. */

    val subtitleFilename: String? = null,

    val subtitleVideoHash: String? = null,

    val subtitleVideoSize: Long? = null,

    val isCS: Boolean = false,

    /** Provider transport hint (e.g. CloudStream ExtractorLinkType M3U8/DASH/VIDEO). */

    val streamTypeHint: String? = null,

    /**

     * Kaynağın bildirdiği çözünürlük etiketi (ör. "1080p").

     * Kaynak bilmiyorsa **null** olmalıdır — tahmini bir değer yazılmaz.

     */

    val quality: String? = null,

    /**

     * Kaynağın bildirdiği çözünürlük yüksekliği. Bilinmiyorsa **null**.

     * CloudStream `Qualities.Unknown` (= 400) bir çözünürlük değildir ve null'a çevrilir.

     */

    val qualityValue: Int? = null,

    /**

     * Kaynağın kendi meta verisinden gelen ses türü: "dub" | "sub" | "dual" | null.

     * (CloudStream `DubStatus`, Stremio `behaviorHints` vb.) Tahmin DEĞİLDİR.

     */

    val providerAudioKind: String? = null,

    /** Kaynağın bildirdiği ses dilleri (ISO/BCP-47). Tahmin değildir. */

    val providerLanguages: List<String> = emptyList(),

    val subtitles: List<com.kitsugi.animelist.core.player.SubtitleInput> = emptyList(),

    /** Episode/source thumbnail URL from the stream provider (e.g. episode cover art from Cloudstream). */

    val thumbnailUrl: String? = null,

    /**

     * CS3 eklentisinin ADULT_PLUGINS listesinde olup olmadığını belirtir.

     * true ise ve kullanıcının `blurAdultMedia` ayarı aktifse;

     * stream listesindeki thumbnail/artwork blur’lanır.

     * `showAdultContent = true` iken bile blur uygulaması bu flag ile kontrol edilir.

     */

    val isAdultContent: Boolean = false,

    /**

     * Ayrı CloudStream ses akışlarının eski tek-URL alanı (geriye dönük uyumluluk).

     * Yeni kod [externalAudioTracks] listesini kullanmalıdır.

     */

    val externalAudioUrl: String? = null,

    /** Tüm provider audio renditions; URL başına header'lar korunur. */

    val externalAudioTracks: List<com.kitsugi.animelist.core.player.ExternalAudioTrackInput> = emptyList(),

    /** Yalnızca kaynak/probe açık bitrate bildirdiyse kbps; çözünürlükten türetilmez. */

    val bitrateKbps: Int? = null,

    /** Kaynağın açıkça bildirdiği dosya boyutu (bytes); bitrate/çözünürlük değildir. */

    val videoSizeBytes: Long? = null,

    /** Extractor/HTTP media type, retained even when the URL has no useful extension. */

    val mediaType: com.kitsugi.animelist.core.player.StreamMediaType? = null,

    /** All separately hosted audio tracks; never assume their cookies match the video host. */

    val externalAudioSources: List<ExternalAudioSource> = emptyList(),

    /** Plugin-declared ExtractorLink type, retained for extensionless HLS/DASH endpoints. */

    val mediaTransport: String? = null,

    /** CloudStream adaptive format and interceptor state; null for legacy/non-CS streams. */
    val csStreamType: String? = null,

    val csExtractorData: String? = null,

    /** Stable addon identity; Stremio manifests are hashed so credentials are not copied into history. */
    val addonId: String? = null,

    /** CloudStream content/season page and API needed to resolve a fresh link. */
    val cs3PageUrl: String? = null,

    val cs3ApiName: String? = null,

    /**
     * Videoya bağlı AYRI ses parçası sayısı (CloudStream `ExtractorLink.audioTracks`).
     * 1+ ise video en az iki ses akışı içeriyor demektir → "Çift dil" rozeti kanıta dayalı basılır.
     */
    val externalAudioTrackCount: Int = 0,

    /**
     * Torrent swarm bilgisi (seeder/leecher). **Yalnızca kaynak sayı verdiyse** doludur;
     * boşsa arayüzde hiçbir seed/peer rozeti gösterilmez.
     */
    val swarmStats: TorrentSwarmStats = TorrentSwarmStats.NONE,

    /**
     * Kaynağın kendi dosya adı (Stremio `behaviorHints.filename`). Torrentio gibi eklentiler
     * gerçek dosya adını burada verir; başlık metni kısaltılmış olabilir.
     */
    val fileName: String? = null

) : java.io.Serializable {

    val allExternalAudioTracks: List<com.kitsugi.animelist.data.model.ExternalAudioTrack>
        get() {
            if (externalAudioTracks.isNotEmpty()) {
                return externalAudioTracks.map { input ->
                    com.kitsugi.animelist.data.model.ExternalAudioTrack(
                        url = input.url,
                        label = input.name.orEmpty(),
                        lang = input.lang.orEmpty(),
                        headers = input.headers
                    )
                }
            }
            val single = externalAudioUrl?.trim()?.takeIf { it.isNotBlank() } ?: return emptyList()
            return listOf(
                com.kitsugi.animelist.data.model.ExternalAudioTrack.fromUrlAndHeaders(
                    url = single,
                    fallbackHeaders = requestHeaders ?: emptyMap()
                )
            )
        }

    /**
     * Dosya adı önceliği: kaynağın verdiği gerçek dosya adı → başlık → isim.
     * Regex tabanlı bilgi çıkarımı (kalite, boyut, dil, codec) bu metin üzerinden yapılır;
     * böylece kısaltılmış başlıklarda kaybolan bilgiler kurtarılır.
     */
    val metadataText: String
        get() = listOfNotNull(
            fileName?.takeIf { it.isNotBlank() } ?: subtitleFilename?.takeIf { it.isNotBlank() },
            title.takeIf { it.isNotBlank() },
            name.takeIf { it.isNotBlank() }
        ).joinToString(" ")

    val isTorrent: Boolean

        get() = !infoHash.isNullOrBlank() || url.isMagnetLink() || url.isTorrentSchemeUrl() ||

            streamTypeHint.equals("TORRENT", ignoreCase = true) ||

            streamTypeHint.equals("MAGNET", ignoreCase = true)



    val p2pHash: String?

        get() = com.kitsugi.animelist.core.p2p.normalizeInfoHashToHex(

            infoHash

                ?: url.extractBtihInfoHash()

                ?: url.extractTorrentSchemeInfoHash()

        )

}



/**
 * Stremio stream nesnesini Kitsugi'nin tek akış modeline çevirir.
 *
 * TEK KAYNAK: Bu dönüşüm hem `AddonStreamRepository` hem de `StreamViewModel` tarafından
 * kullanılır; böylece "bir yolda boyut/seed bilgisi geliyor, diğerinde gelmiyor" tutarsızlığı
 * oluşmaz. Kalite, boyut, swarm, dosya adı ve çözünürlük TEK yerden çıkarılır.
 */
fun StreamResponseItem.toStreamSource(
    addonName: String,
    addonId: String? = null
): StreamSource {
    val nameText = name?.takeIf { it.isNotBlank() } ?: "Bilinmeyen Akış"
    val titleText = title?.takeIf { it.isNotBlank() } ?: "İsimsiz Dosya"
    val resolvedFileName = behaviorHints?.filename?.takeIf { it.isNotBlank() }
    val metaText = listOfNotNull(
        titleText,
        nameText,
        resolvedFileName,
        description?.takeIf { it.isNotBlank() }
    ).joinToString("\n")
    val parsedQuality = StreamSorter.parseQualityFromTitle(metaText)
    val parsedQualityValue = StreamSorter.parseQualityValue(parsedQuality)

    val parsedSwarm = TorrentSwarmStats.parse(metaText)
    val swarm = TorrentSwarmStats(
        seeders = seeders ?: parsedSwarm.seeders,
        leechers = peers ?: leechers ?: parsedSwarm.leechers,
        providerTag = parsedSwarm.providerTag
    )

    return StreamSource(
        addonName = addonName,
        name = nameText,
        title = titleText,
        url = url,
        infoHash = infoHash,
        fileIndex = fileIdx ?: fileIndex,
        trackers = trackers,
        requestHeaders = behaviorHints?.proxyHeaders?.request,
        subtitleFilename = behaviorHints?.filename,
        subtitleVideoHash = behaviorHints?.videoHash,
        subtitleVideoSize = behaviorHints?.videoSize,
        quality = parsedQuality,
        qualityValue = parsedQualityValue,
        thumbnailUrl = thumbnail,
        swarmStats = swarm,
        videoSizeBytes = behaviorHints?.videoSize?.takeIf { it > 0 },
        fileName = resolvedFileName,
        addonId = addonId
    )
}

fun String?.isMagnetLink(): Boolean =

    this?.trimStart()?.startsWith("magnet:", ignoreCase = true) == true



fun String?.isTorrentSchemeUrl(): Boolean =

    this?.trimStart()?.startsWith("torrent://", ignoreCase = true) == true



fun String?.extractBtihInfoHash(): String? {

    val raw = this?.trim()?.takeIf { it.startsWith("magnet:", ignoreCase = true) } ?: return null

    val marker = "btih:"

    val markerIndex = raw.indexOf(marker, ignoreCase = true)

    if (markerIndex < 0) return null

    val start = markerIndex + marker.length

    val end = raw.indexOf('&', start).takeIf { it >= 0 } ?: raw.length

    return raw.substring(start, end)

        .trim()

        .takeIf { it.isNotEmpty() }

}



fun String?.extractTorrentSchemeInfoHash(): String? {

    val raw = this?.trimStart()?.takeIf { it.isTorrentSchemeUrl() } ?: return null

    return raw.removeRange(0, "torrent://".length)

        .substringBefore('/')

        .substringBefore('?')

        .trim()

        .takeIf { it.isNotEmpty() }

}



fun String?.extractTorrentSchemeFileIdx(): Int? {

    val raw = this?.trimStart()?.takeIf { it.isTorrentSchemeUrl() } ?: return null

    val path = raw.removeRange(0, "torrent://".length).substringBefore('?')

    if ('/' !in path) return null

    return path.substringAfter('/')

        .trim()

        .takeIf { segment -> segment.isNotEmpty() && segment.all { it.isDigit() } }

        ?.toIntOrNull()

}



fun extractTrackersFromMagnet(magnetUri: String?): List<String> {

    if (magnetUri.isNullOrBlank() || !magnetUri.startsWith("magnet:", ignoreCase = true)) return emptyList()

    return try {

        val uri = android.net.Uri.parse(magnetUri)

        uri.getQueryParameters("tr") ?: emptyList()

    } catch (_: Exception) {

        emptyList()

    }

}

