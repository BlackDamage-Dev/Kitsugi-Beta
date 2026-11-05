package com.kitsugi.animelist.data.remote

import android.util.Log
import com.google.gson.Gson
import com.kitsugi.animelist.core.network.IPv4FirstDns
import com.kitsugi.animelist.data.local.ManagedAddonEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.remote.anisub.AniSubSubtitleClient

class AddonStreamClient {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .dns(IPv4FirstDns())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

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

        // AniSub.co'nun artık gerçek bir Stremio manifesti var
        // (https://anisub.co/manifest.json). Önce onu okumayı dene;
        // ağ hatasında aşağıdaki ihtiyati tanım kullanılır ki eklenti
        // çevrimdışıyken bile listeden düşmesin.

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
                    return@withContext fallbackAniSubManifest(normalizedUrl)
                }
                val bodyString = response.body?.string() ?: return@withContext fallbackAniSubManifest(normalizedUrl)
                val manifestMap = gson.fromJson(bodyString, Map::class.java) ?: return@withContext fallbackAniSubManifest(normalizedUrl)

                val name = manifestMap["name"] as? String ?: "Bilinmeyen Eklenti"
                val description = manifestMap["description"] as? String
                // Stremio manifestleri görseli "logo" alanında verir (örn. AniSub).
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
            fallbackAniSubManifest(normalizedUrl)
        }
    }

    /**
     * AniSub manifesti ağ hatası/HTTP hatasında okunamazsa kullanılan
     * ihtiyati tanım. Diğer addon URL'leri için null döner.
     */
    private fun fallbackAniSubManifest(normalizedUrl: String): ManagedAddonEntity? {
        if (!normalizedUrl.contains("anisub.co")) return null
        return ManagedAddonEntity(
            manifestUrl = normalizedUrl,
            name = "AniSub.co",
            description = KitsugiStrings.get(R.string.anisub_co_turkce_altyazi_eklentisi),
            icon = "https://anisub.co/assets/logo.png",
            isEnabled = true,
            orderIndex = 99,
            idPrefixes = null,
            streamTypes = null,
            subtitleTypes = "movie,series"
        )
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
        episode: Int? = null,
        season: Int? = null
    ): List<SubtitleResponseItem> = withContext(Dispatchers.IO) {
        if (manifestUrl.contains("anisub.co")) {
            // AniSub paket mantığı (bölüm seçimi / ZIP+RAR açma) standart Stremio
            // akışıyla karşılanamadığı için özel istemciye devredilir.
            // Bkz. AniSubSubtitleClient.
            return@withContext AniSubSubtitleClient.fetchSubtitles(
                context = context,
                type = type,
                id = id,
                aniListId = aniListId,
                animeTitle = animeTitle,
                episode = episode,
                filenameHint = extraParams?.get("filename")
            )
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

        val resourceUrl = if (extraParamStr.isNotBlank()) {
            "$baseUrl/subtitles/$encodedType/$encodedId/$extraParamStr.json"
        } else {
            "$baseUrl/subtitles/$encodedType/$encodedId.json"
        }
        val finalUrl = if (query.isEmpty()) resourceUrl else "$resourceUrl?$query"
        Log.d("AddonStreamClient", "fetchSubtitles URL: $finalUrl")

        val request = Request.Builder()
            .url(finalUrl)
            .header("User-Agent", DEFAULT_USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("AddonStreamClient", "HTTP error ${response.code} fetching subtitles from $finalUrl")
                    return@withContext emptyList()
                }
                val bodyString = response.body?.string() ?: return@withContext emptyList()
                val subResult = gson.fromJson(bodyString, SubtitleResponseWrapper::class.java)
                return@withContext subResult?.subtitles ?: emptyList()
            }
        } catch (e: java.lang.Exception) {
            Log.e("AddonStreamClient", "Error fetching subtitles from $finalUrl", e)
            emptyList()
        }
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
}

data class SubtitleResponseWrapper(
    val subtitles: List<SubtitleResponseItem>?
)

data class SubtitleResponseItem(
    val id: String?,
    val url: String?,
    val lang: String?,
    /**
     * Addon'un beyan ettiği altyazı biçimi ("ass", "srt", "vtt" ...).
     * Bazı addon'lar (örn. AniSub) uzantısız URL döndürür; biçim bilgisi
     * yoksa SubtitleRepositoryImpl URL'den tahmin eder.
     */
    val format: String? = null
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
    val thumbnail: String? = null
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
