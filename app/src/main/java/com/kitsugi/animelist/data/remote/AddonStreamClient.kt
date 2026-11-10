package com.kitsugi.animelist.data.remote

import android.util.Log
import com.google.gson.Gson
import com.kitsugi.animelist.core.network.IPv4FirstDns
import com.kitsugi.animelist.data.local.ManagedAddonEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class AddonStreamClient {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .dns(IPv4FirstDns())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    private val subtitleClient = client.newBuilder().callTimeout(20, TimeUnit.SECONDS).build()

    companion object {
        private const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    /**
     * Normalizes a user-input manifest URL, converting stremio:// to https:// and appending
     * /manifest.json if missing, while preserving query parameters.
     */
    fun normalizeManifestUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return ""

        val normalizedScheme = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("stremio://") -> "https://${trimmed.removePrefix("stremio://")}"
            else -> "https://$trimmed"
        }

        val withoutFragment = normalizedScheme.substringBefore("#")
        val query = withoutFragment.substringAfter("?", "")
        val path = withoutFragment.substringBefore("?").trimEnd('/')
        val manifestPath = if (path.endsWith("/manifest.json")) {
            path
        } else {
            "$path/manifest.json"
        }

        return if (query.isEmpty()) manifestPath else "$manifestPath?$query"
    }

    suspend fun fetchManifest(manifestUrl: String): ManagedAddonEntity? = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeManifestUrl(manifestUrl)
        if (normalizedUrl.isBlank()) return@withContext null

        val request = Request.Builder()
            .url(normalizedUrl)
            .header("User-Agent", DEFAULT_USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("AddonStreamClient", "HTTP error ${response.code} fetching manifest from $normalizedUrl")
                    return@withContext null
                }
                val bodyString = response.body?.string() ?: return@withContext null
                val manifestMap = gson.fromJson(bodyString, Map::class.java) ?: return@withContext null

                val name = manifestMap["name"] as? String ?: "Bilinmeyen Eklenti"
                val description = manifestMap["description"] as? String
                val icon = (manifestMap["icon"] as? String) ?: (manifestMap["logo"] as? String)

                // Parse top-level idPrefixes (e.g. ["tt", "kitsu:"])
                @Suppress("UNCHECKED_CAST")
                val idPrefixList = (manifestMap["idPrefixes"] as? List<*>)
                    ?.filterIsInstance<String>()
                    ?.filter { it.isNotBlank() }
                    .orEmpty()

                // Stream resource: Stremio spesifikasyonuna göre ayrıştırılır (bkz. StremioStreamRouting).
                // Kısa biçim ("stream") manifest düzeyindeki types/idPrefixes'i miras alır; tam biçim
                // ({name, types, idPrefixes}) kendi değerlerini kullanır. Eskiden tam biçimdeki
                // idPrefixes yok sayılıyordu ve addon'un ilan ettiği tür istek yoluna yansımıyordu.
                @Suppress("UNCHECKED_CAST")
                val resourcesList = manifestMap["resources"] as? List<*>
                val manifestTypes = (manifestMap["types"] as? List<*>)
                    ?.filterIsInstance<String>()
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    .orEmpty()
                val streamSpec = StremioStreamRouting.parseStreamResource(
                    resources = resourcesList,
                    manifestTypes = manifestTypes,
                    manifestIdPrefixes = idPrefixList
                )
                // null → stream resource yok (eski davranış: tür filtresi yok)
                // "*"  → stream var ama tür beyan edilmemiş
                // csv  → addon'un ilan ettiği stream türleri
                val streamTypes: String? = when {
                    streamSpec == null -> null
                    streamSpec.types == null -> StremioStreamRouting.ANY_TYPE
                    else -> streamSpec.types.joinToString(",")
                }
                // Stream için geçerli prefix'ler: stream resource'a özgü değer varsa o, yoksa top-level.
                val streamIdPrefixList = streamSpec?.idPrefixes ?: idPrefixList

                val subtitleTypes = resourcesList?.mapNotNull { res ->
                    when (res) {
                        is String -> if (res == "subtitles") "movie,series" else null
                        is Map<*, *> -> {
                            val resName = res["name"] as? String ?: return@mapNotNull null
                            if (resName != "subtitles") return@mapNotNull null
                            @Suppress("UNCHECKED_CAST")
                            val types = (res["types"] as? List<*>)?.filterIsInstance<String>()
                            val typesStr = types?.joinToString(",")
                            if (typesStr.isNullOrBlank()) "movie,series" else typesStr
                        }
                        else -> null
                    }
                }?.firstOrNull() ?: run {
                    if (resourcesList?.contains("subtitles") == true) "movie,series" else null
                }

                return@withContext ManagedAddonEntity(
                    manifestUrl = normalizedUrl,
                    name = name,
                    description = description,
                    icon = icon,
                    isEnabled = true,
                    orderIndex = 0,
                    // Stream prefix'leri (stream resource'a özgü ya da top-level). Altyazı kodu bu alanı kullanmaz.
                    idPrefixes = if (streamIdPrefixList.isEmpty()) null else gson.toJson(streamIdPrefixList),
                    streamTypes = streamTypes,
                    subtitleTypes = if (subtitleTypes == null) null else subtitleTypes.ifBlank { null }
                )
            }
        } catch (e: Exception) {
            Log.e("AddonStreamClient", "Error fetching manifest from $normalizedUrl", e)
            null
        }
    }

    suspend fun fetchStreams(
        manifestUrl: String,
        type: String, // "series" or "movie"
        id: String // tt1234567:1:5
    ): List<StreamResponseItem> = withContext(Dispatchers.IO) {
        val normalizedManifest = normalizeManifestUrl(manifestUrl)
        if (normalizedManifest.isBlank()) return@withContext emptyList()

        val withoutFragment = normalizedManifest.substringBefore("#")
        val query = withoutFragment.substringAfter("?", "")
        val path = withoutFragment.substringBefore("?").trimEnd('/')
        val baseUrl = path.removeSuffix("/manifest.json").trimEnd('/')

        // URL encode path segments exactly like KitsugiTV-dev's encodePathSegment:
        // URLEncoder replaces spaces with '+', but RFC 3986 path segments need '%20'
        val encodedType = encodePathSegment(type)
        val encodedId = id.split(':').map { encodePathSegment(it) }.joinToString(":")

        val resourceUrl = "$baseUrl/stream/$encodedType/$encodedId.json"
        val finalUrl = if (query.isEmpty()) resourceUrl else "$resourceUrl?$query"

        val request = Request.Builder()
            .url(finalUrl)
            .header("User-Agent", DEFAULT_USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("AddonStreamClient", "HTTP error ${response.code} fetching streams from $finalUrl")
                    return@withContext emptyList()
                }
                val bodyString = response.body?.string() ?: return@withContext emptyList()
                val streamResult = gson.fromJson(bodyString, StreamResponseWrapper::class.java)
                return@withContext streamResult?.streams ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e("AddonStreamClient", "Error fetching streams from $finalUrl", e)
            emptyList()
        }
    }

    suspend fun fetchSubtitles(
        context: android.content.Context,
        manifestUrl: String,
        type: String, // "series" or "movie"
        id: String, // tt1234567:1:5
        extraParams: Map<String, String>? = null,
        aniListId: Int? = null,
        animeTitle: String? = null,
        episode: Int? = null
    ): List<SubtitleResponseItem> = withContext(Dispatchers.IO) {
        if (manifestUrl.contains("anisub.co")) {
            return@withContext fetchAniSubSubtitles(context, id, aniListId, animeTitle, episode)
        }

        val normalizedManifest = normalizeManifestUrl(manifestUrl)
        if (normalizedManifest.isBlank()) return@withContext emptyList()

        val withoutFragment = normalizedManifest.substringBefore("#")
        val query = withoutFragment.substringAfter("?", "")
        val path = withoutFragment.substringBefore("?").trimEnd('/')
        val baseUrl = path.removeSuffix("/manifest.json").trimEnd('/')

        // Stremio subtitle URL formatı (NuvioTV SubtitleRepositoryImpl referansı):
        // {baseUrl}/subtitles/{type}/{id}/{videoHash=x&videoSize=y&filename=z}.json
        // NOT: id segmenti URL-encode edilmelidir çünkü ':' gibi karakterler yönlendirmeyi bozabilir.
        // NOT: extraParams path segment içine eklenir, query string'e değil
        val encodedType = encodePathSegment(type)
        val encodedId = id.split(':').map { encodePathSegment(it) }.joinToString(":")
        val extraParamStr = buildSubtitleExtraParams(extraParams)

        suspend fun requestSubtitles(withExtras: Boolean): List<SubtitleResponseItem> {
            val resourceUrl = if (withExtras && extraParamStr.isNotBlank()) {
                "$baseUrl/subtitles/$encodedType/$encodedId/$extraParamStr.json"
            } else {
                "$baseUrl/subtitles/$encodedType/$encodedId.json"
            }
            val finalUrl = if (query.isEmpty()) resourceUrl else "$resourceUrl?$query"
            val request = Request.Builder().url(finalUrl)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Accept", "application/json").get().build()
            subtitleClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // Some addons do not implement the optional extras path.
                    if (withExtras && response.code in listOf(400, 404, 422)) return emptyList()
                    throw IOException("Subtitle provider returned HTTP ${response.code}")
                }
                val body = response.body?.string() ?: throw IOException("Empty subtitle response")
                return (gson.fromJson(body, SubtitleResponseWrapper::class.java)?.subtitles ?: emptyList()).filter { item ->
                    com.kitsugi.animelist.core.player.SubtitleUrlPolicy.isReachableForProvider(normalizedManifest, item.url)
                }
            }
        }

        val results = requestSubtitles(extraParamStr.isNotBlank())
        if (results.isEmpty() && extraParamStr.isNotBlank()) requestSubtitles(false) else results
    }

    /**
     * Encodes a URL path segment, replacing '+' with '%20' to be RFC 3986-compliant.
     * This matches KitsugiTV-dev's StreamRepositoryImpl.encodePathSegment exactly.
     */
    private fun encodePathSegment(value: String): String {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }

    /**
     * Stremio subtitle extra params path segment oluşturur.
     * NuvioTV SubtitleRepositoryImpl.buildExtraParams() referans alınarak port edildi.
     *
     * Format: "videoHash=abc123&videoSize=1234567890&filename=episode.mkv"
     * Bu string daha sonra: /subtitles/{type}/{id}/{extraParams}.json şeklinde kullanılır.
     */
    private fun buildSubtitleExtraParams(params: Map<String, String>?): String {
        if (params.isNullOrEmpty()) return ""
        val parts = mutableListOf<String>()
        params["videoHash"]?.let { parts.add("videoHash=$it") }
        params["videoSize"]?.let { parts.add("videoSize=$it") }
        params["filename"]?.let {
            // filename değeri URL encode edilmeli (boşluk, Türkçe karakter vb. için)
            parts.add("filename=${encodePathSegment(it)}")
        }
        return parts.joinToString("&")
    }

    private suspend fun fetchAniSubSubtitles(
        context: android.content.Context,
        id: String,
        aniListId: Int?,
        animeTitle: String?,
        episode: Int?
    ): List<SubtitleResponseItem> = withContext(Dispatchers.IO) {
        try {
            Log.d("AddonStreamClient", "fetchAniSubSubtitles: id=$id, aniListId=$aniListId, title=$animeTitle, ep=$episode")
            
            val query = animeTitle?.trim()?.takeIf { it.isNotBlank() } ?: when {
                id.startsWith("tt") || id.startsWith("kitsu:") || id.startsWith("tmdb:") -> ""
                else -> id.substringBefore(":").trim()
            }
            if (query.isBlank()) return@withContext emptyList()

            // Public API does not need the brittle Inertia version/session handshake.
            // Its actual schema is success.data.data + snake_case media_info.
            val items = mutableListOf<AnisubSubtitleItem>()
            var page = 1
            do {
                val url = "https://anisub.co/api/subtitles?q=${URLEncoder.encode(query, "UTF-8")}&page=$page"
                val request = Request.Builder().url(url).header("User-Agent", DEFAULT_USER_AGENT)
                    .header("Accept", "application/json").build()
                val json = subtitleClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("AniSub search HTTP ${response.code}")
                    response.body?.string() ?: throw IOException("Empty AniSub response")
                }
                val result = gson.fromJson(json, AnisubSearchResponse::class.java)
                if (result?.success != true || result.data == null) throw IOException("Invalid AniSub search response")
                items.addAll(result.data.data.orEmpty())
                val lastPage = result.data.meta?.last_page ?: 1
                page++
            } while (page <= lastPage && page <= 5)

            val filteredItems = items.filter { item ->
                val mediaId = item.mediaInfo?.media_anilist_id ?: item.subtitle_anilist_id
                val matches = com.kitsugi.animelist.core.player.AnisubMatching.matchesMedia(
                    aniListId, mediaId, query, listOf(item.mediaInfo?.title_romaji, item.mediaInfo?.title_english)
                )
                val epMatch = com.kitsugi.animelist.core.player.AnisubMatching.episodeMatches(
                    item.content_type, item.episode_description, episode
                )
                // Package episode descriptions can be stale or outright wrong. The
                // filenames inside the archive, not this metadata, decide the episode.
                matches && (com.kitsugi.animelist.core.player.AnisubMatching.isPackage(item.content_type) || epMatch != false)
            }
            Log.d("AddonStreamClient", "AniSub: ${filteredItems.size} matching releases of ${items.size}")
            
            // Step 5: For each filtered item, get the download URL and download/unzip it
            val subItems = mutableListOf<SubtitleResponseItem>()
            for (item in filteredItems) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                try {
                val subId = item.subtitle_id ?: continue
                val releaseName = item.subtitle_release_name ?: "Unknown Release"
                val lang = item.subtitle_language ?: "Turkish"
                
                val dlInfoUrl = "https://anisub.co/api/subtitles/$subId/download"
                val dlInfoRequest = Request.Builder()
                    .url(dlInfoUrl)
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .get()
                    .build()
                
                val dlInfoJson = subtitleClient.newCall(dlInfoRequest).execute().use { response ->
                    if (!response.isSuccessful) null else response.body?.string()
                } ?: continue
                
                val dlResponse = gson.fromJson(dlInfoJson, AnisubDownloadResponse::class.java)
                if (dlResponse?.success != true || dlResponse.data?.download_url == null) continue
                
                val downloadUrl = dlResponse.data.download_url
                val filename = dlResponse.data.filename ?: "sub.zip"
                
                val isPackage = com.kitsugi.animelist.core.player.AnisubMatching.isPackage(item.content_type)
                val confirmedEpisode = com.kitsugi.animelist.core.player.AnisubMatching.episodeMatches(
                    item.content_type, item.episode_description, episode
                ) == true
                // The official Stremio endpoint may serve an arbitrary episode of a pack
                // (observed live). Use it only for a confirmed single-episode release.
                val localUrl = when {
                    filename.endsWith(".rar", true) || filename.endsWith(".zip", true) -> downloadAndUnzipSubtitle(
                        context, downloadUrl, subId, filename, episode, animeTitle,
                        allowUnnumberedSingle = !isPackage && (episode == null || confirmedEpisode)
                    )
                    filename.endsWith(".ass", true) || filename.endsWith(".ssa", true) ||
                        filename.endsWith(".srt", true) || filename.endsWith(".vtt", true) ->
                        downloadUrl.takeIf { episode == null || confirmedEpisode }
                    else -> null
                }
                if (localUrl != null) {
                    subItems.add(
                        SubtitleResponseItem(
                            id = "anisub-$subId",
                            url = localUrl,
                            lang = lang,
                            releaseInfo = releaseName
                        )
                    )
                }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("AddonStreamClient", "AniSub release ${item.subtitle_id} failed: ${e.message}")
                }
            }

            subItems
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("AddonStreamClient", "Error in fetchAniSubSubtitles", e)
            emptyList()
        }
    }

    private fun matchesEpisodeInFileName(fileName: String, targetEp: Int, mediaTitle: String?): Boolean =
        com.kitsugi.animelist.core.player.AnisubMatching.matchesFileEpisode(fileName, targetEp)

    private fun canUseSingleSubtitle(file: java.io.File, targetEp: Int?, mediaTitle: String?): Boolean {
        if (targetEp == null || matchesEpisodeInFileName(file.name, targetEp, mediaTitle)) return true
        // A generic single file may belong to a single-episode release. Do not use it
        // when the filename explicitly names a different episode.
        return com.kitsugi.animelist.core.player.AnisubMatching.fileEpisode(file.name) == null
    }

    private fun java.io.InputStream.readBounded(maxBytes: Int): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) return out.toByteArray()
            if (out.size() + count > maxBytes) return null
            out.write(buffer, 0, count)
        }
    }

    // Different repository/client instances can request the same release at once.
    // Use a process-wide lock so one search cannot delete another's cached files.
    private fun downloadAndUnzipSubtitle(
        context: android.content.Context,
        downloadUrl: String,
        subId: Long,
        filename: String,
        targetEp: Int?,
        mediaTitle: String?,
        allowUnnumberedSingle: Boolean = false
    ): String? = synchronized(AniSubArchive) {
        val cacheDir = java.io.File(context.cacheDir, "anisub_subs/$subId")
        val marker = java.io.File(cacheDir, ".complete")
        var staging: java.io.File? = null
        fun pick(files: List<java.io.File>): java.io.File? {
            if (targetEp != null) {
                val match = com.kitsugi.animelist.core.player.AnisubMatching.uniqueEpisodeFile(files.map { it.name }, targetEp)
                if (match != null) return files.single { it.name == match }
                // More than one possible episode file is not a safe first-match choice.
                if (files.count { matchesEpisodeInFileName(it.name, targetEp, mediaTitle) } > 1) return null
            }
            return files.singleOrNull()?.takeIf {
                targetEp == null || (allowUnnumberedSingle && canUseSingleSubtitle(it, targetEp, mediaTitle))
            }
        }
        try {
            if (marker.isFile) {
                val files = cacheDir.listFiles()?.filter { it.isFile && it.extension.lowercase() in setOf("ass", "ssa", "srt", "vtt") }.orEmpty()
                return pick(files)?.let { android.net.Uri.fromFile(it).toString() }
            }
            cacheDir.mkdirs()
            val request = Request.Builder().url(downloadUrl).header("User-Agent", DEFAULT_USER_AGENT).build()
            val bytes = subtitleClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                if (body.contentLength() > 10 * 1024 * 1024) return null
                body.byteStream().readBounded(10 * 1024 * 1024) ?: return null
            }
            staging = java.io.File(cacheDir, "staging-${java.util.UUID.randomUUID()}")
            if (!staging.mkdirs()) throw IOException("Cannot create subtitle staging directory")
            val files = if (filename.endsWith(".rar", true)) {
                AniSubArchive.extractRar(bytes, staging)
            } else {
                AniSubArchive.extractZip(bytes, staging)
            }
            if (files.isEmpty()) return null
            // Only publish a completely extracted archive. A failed download/extraction must
            // not leave a partial batch that could be mistaken for a single-episode file.
            cacheDir.listFiles()?.filter { it.isFile && it != marker }?.forEach { it.delete() }
            val published = files.map { file ->
                val target = java.io.File(cacheDir, file.name)
                if (!file.renameTo(target)) throw IOException("Cannot publish subtitle")
                target
            }
            marker.writeText("ok")
            return pick(published)?.let { android.net.Uri.fromFile(it).toString() }
        } catch (e: Exception) {
            Log.e("AddonStreamClient", "Error downloading/unzipping AniSub release $subId ($filename)", e)
            return null
        } finally {
            staging?.deleteRecursively()
        }
    }

}

internal data class AnisubSearchResponse(val success: Boolean?, val data: AnisubSubtitlesData?)
internal data class AnisubSubtitlesData(val data: List<AnisubSubtitleItem>?, val meta: AnisubMeta?)
internal data class AnisubMeta(val last_page: Int?)
internal data class AnisubSubtitleItem(
    val subtitle_id: Long?,
    val subtitle_release_name: String?,
    val subtitle_language: String?,
    val subtitle_anilist_id: Int?,
    val content_type: String?,
    val episode_description: String?,
    val format: String?,
    @com.google.gson.annotations.SerializedName(value = "media_info", alternate = ["mediaInfo"])
    val mediaInfo: AnisubMediaInfo?
)
internal data class AnisubMediaInfo(
    val media_anilist_id: Int?,
    val title_romaji: String?,
    val title_english: String?
)
private data class AnisubDownloadResponse(
    val success: Boolean,
    val data: AnisubDownloadData?,
    val message: String?
)
private data class AnisubDownloadData(
    val download_url: String?,
    val filename: String?,
    val file_size: Long?
)


data class SubtitleResponseWrapper(
    val subtitles: List<SubtitleResponseItem>?
)

data class SubtitleResponseItem(
    val id: String?,
    val url: String?,
    val lang: String?,
    val label: String? = null,
    val title: String? = null,
    val subtitleFileName: String? = null,
    val movieReleaseName: String? = null,
    val releaseInfo: String? = null,
    val format: String? = null,
    // Addons sometimes return strings or objects here; malformed optional metadata
    // must not make the entire response fail Gson parsing.
    val season: com.google.gson.JsonElement? = null,
    val episode: com.google.gson.JsonElement? = null
)

data class StreamResponseWrapper(
    val streams: List<StreamResponseItem>?
)

/**
 * Raw Stremio stream object returned by an addon.
 * `behaviorHints.proxyHeaders.request` carries the HTTP headers needed to play the stream.
 */
data class StreamResponseItem(
    val name: String?,
    val title: String?,
    val url: String?,
    val infoHash: String?,
    /**
     * Stremio addon protocol uses "fileIdx".
     * SerializedName ensures Gson correctly parses fileIdx into this field.
     */
    @com.google.gson.annotations.SerializedName("fileIdx")
    val fileIdx: Int? = null,
    /**
     * Stremio "sources" array containing tracker and peer addresses (e.g. tracker:udp://...).
     */
    val sources: List<String>? = null,
    val behaviorHints: StreamBehaviorHintsDto?,
    /** Episode/stream thumbnail URL returned by some addons (e.g. Cloudstream, Torrentio) */
    val thumbnail: String? = null,
    /**
     * Torrent swarm bilgisi — bazı eklentiler (ör. Torrentio türevleri) bu alanları doğrudan
     * gönderir. Stremio şemasında ZORUNLU değildir; yoksa metinden ayrıştırılır.
     */
    val seeders: Int? = null,
    val peers: Int? = null,
    /** Bazı eklentiler `leechers` adını kullanır. */
    val leechers: Int? = null,
    /** Uzun açıklama alanı — swarm/etiket bilgisi bazen burada durur. */
    val description: String? = null
) {
    /** Backward compatibility property for existing references */
    val fileIndex: Int? get() = fileIdx

    /** Clean tracker URLs extracted from "tracker:" prefixed entries in sources */
    val trackers: List<String>
        get() = sources
            ?.asSequence()
            ?.filter { it.startsWith("tracker:", ignoreCase = true) }
            ?.map { it.removePrefix("tracker:").removePrefix("tracker:").trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            ?.toList()
            ?: emptyList()
}

/**
 * Top-level `behaviorHints` object from a Stremio stream response.
 */
data class StreamBehaviorHintsDto(
    val notWebReady: Boolean? = null,
    val bingeGroup: String? = null,
    val filename: String? = null,
    val videoHash: String? = null,
    val videoSize: Long? = null,
    val proxyHeaders: ProxyHeadersDto? = null
)

/** Mirrors `behaviorHints.proxyHeaders` in the Stremio spec. */
data class ProxyHeadersDto(
    /** Headers to attach to every segment/manifest request (e.g. Authorization, Referer). */
    val request: Map<String, String>? = null,
    /** Headers returned by the upstream server — typically not needed by the player. */
    val response: Map<String, String>? = null
)
