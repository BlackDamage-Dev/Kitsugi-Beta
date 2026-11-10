package com.kitsugi.animelist.data.ambient

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

/** Public live-station search; only recently checked, HTTPS direct streams are returned. */
internal class RadioBrowserAudioSource(
    private val client: OkHttpClient = ambientAudioHttpClient,
) {
    suspend fun search(query: String, plan: AmbientAudioPlan): List<AmbientAudioTrack> = withContext(Dispatchers.IO) {
        val url = "$API_BASE/stations/search".toHttpUrl().newBuilder()
            .addQueryParameter("hidebroken", "true")
            .addQueryParameter("order", "votes")
            .addQueryParameter("reverse", "true")
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .apply {
                val preferredTag = preferredTag(query, plan)
                if (preferredTag != null) {
                    addQueryParameter("tag", preferredTag)
                } else {
                    addQueryParameter("name", query.trim().take(MAX_QUERY_LENGTH))
                }
            }
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Radio Browser station search failed: HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) throw IOException("Radio Browser returned an empty response")
            val stations = JSONArray(body)
            buildList {
                for (index in 0 until stations.length()) {
                    val station = stations.optJSONObject(index) ?: continue
                    station.toAmbientTrack()?.let(::add)
                }
            }
        }
    }

    private fun preferredTag(query: String, plan: AmbientAudioPlan): String? {
        val manualTerms = query.trim().take(MAX_QUERY_LENGTH)
            .split(Regex("[^\\p{L}\\p{N}-]+"))
            .map { it.lowercase(Locale.ROOT).trim() }
            .filter(String::isNotBlank)
        val queryTerms = if (query.trim().equals(plan.query, ignoreCase = true)) {
            (plan.matchTerms + manualTerms).toList()
        } else {
            manualTerms + plan.matchTerms
        }
        queryTerms.firstNotNullOfOrNull { term ->
            PREFERRED_TAGS.firstOrNull { tag -> term == tag || tag in term }
        }?.let { return it }
        return when {
            queryTerms.any { it in setOf("romantic", "soft", "cozy", "lofi", "lo-fi", "piano") } -> "chillout"
            queryTerms.any { it in setOf("action", "epic", "cinematic", "orchestral", "adventure") } -> "soundtrack"
            queryTerms.any { it in setOf("nature", "rain", "forest", "water", "ocean") } -> "nature"
            else -> "ambient"
        }
    }

    internal fun parseStation(station: JSONObject): AmbientAudioTrack? = station.toAmbientTrack()

    private fun JSONObject.toAmbientTrack(): AmbientAudioTrack? {
        if (optInt("lastcheckok", 0) != 1) return null
        val id = textValue("stationuuid")
        val title = textValue("name").trim().take(MAX_TITLE_LENGTH)
        val streamUrl = textValue("url_resolved").ifBlank { textValue("url") }
        if (id.isBlank() || title.isBlank() || !streamUrl.startsWith("https://", ignoreCase = true)) return null

        val codec = textValue("codec").trim().lowercase(Locale.ROOT)
        val urlExtension = streamUrl.substringBefore('?').substringAfterLast('.', "").lowercase(Locale.ROOT)
        val isHls = optBoolean("hls", false) || optInt("hls", 0) == 1
        val normalizedCodec = codec.removeSuffix("+")
        val canonicalCodec = when (normalizedCodec) {
            "vorbis" -> "ogg"
            "mpeg", "mpeg layer 3", "mpeg-1 layer 3" -> "mp3"
            else -> AmbientAudioFormats.canonicalType(normalizedCodec)
        }
        val fileType = when {
            isHls || urlExtension == "m3u8" -> "m3u8"
            canonicalCodec in SUPPORTED_TYPES -> canonicalCodec
            AmbientAudioFormats.canonicalType(urlExtension) in SUPPORTED_TYPES ->
                AmbientAudioFormats.canonicalType(urlExtension)
            else -> return null
        }
        if (fileType !in SUPPORTED_TYPES) return null

        val country = textValue("country")
        val language = textValue("language")
        val creator = listOf(country, language).filter(String::isNotBlank)
            .joinToString(" · ").ifBlank { "Live radio" }
        val tags = textValue("tags")
            .split(',', ';')
            .mapNotNull { it.trim().cappedAmbientAudioValue().takeIf(String::isNotBlank) }
            .take(MAX_TAGS)
        val landingUrl = textValue("homepage").ifBlank { DEFAULT_LANDING_URL }

        return AmbientAudioTrack(
            id = "radio_browser:$id".cappedAmbientAudioValue(),
            title = title,
            creator = creator.cappedAmbientAudioValue(),
            streamUrl = streamUrl.cappedAmbientAudioValue(),
            landingUrl = landingUrl.cappedAmbientAudioValue(),
            provider = "radio_browser",
            source = "radio_browser",
            fileType = fileType,
            tags = tags,
        )
    }

    private fun JSONObject.textValue(key: String): String = when (val value = opt(key)) {
        is String -> value.cappedAmbientAudioValue()
        is Number, is Boolean -> value.toString().cappedAmbientAudioValue()
        else -> ""
    }

    private companion object {
        const val API_BASE = "https://de1.api.radio-browser.info/json"
        const val DEFAULT_LANDING_URL = "https://www.radio-browser.info"
        const val PAGE_SIZE = 30
        const val MAX_QUERY_LENGTH = 120
        const val MAX_TITLE_LENGTH = 220
        const val MAX_TAGS = 32
        const val USER_AGENT = "KitsugiBeta/2.x (Android ambient audio; https://github.com/f26901964-eng/Kitsugi-Beta)"
        val SUPPORTED_TYPES = AmbientAudioFormats.supportedPlaybackTypes
        val PREFERRED_TAGS = listOf(
            "ambient", "chillout", "lofi", "lo-fi", "classical", "instrumental", "jazz",
            "nature", "relax", "meditation", "soundtrack", "darkambient"
        )
    }
}
