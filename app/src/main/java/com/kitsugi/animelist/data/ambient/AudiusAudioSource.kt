package com.kitsugi.animelist.data.ambient

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Anonymous Audius search and stream endpoints for playable, ungated tracks. */
internal class AudiusAudioSource(
    private val client: OkHttpClient = ambientAudioHttpClient,
) {
    suspend fun search(query: String): List<AmbientAudioTrack> = withContext(Dispatchers.IO) {
        val url = "$API_BASE/tracks/search".toHttpUrl().newBuilder()
            .addQueryParameter("query", query.take(MAX_QUERY_LENGTH))
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("app_name", APP_NAME)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Audius search failed: HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) throw IOException("Audius returned an empty response")
            val items = JSONObject(body).optJSONArray("data") ?: JSONArray()
            buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    item.toAmbientTrack()?.let(::add)
                }
            }
        }
    }

    internal fun parseTrack(item: JSONObject): AmbientAudioTrack? = item.toAmbientTrack()

    private fun JSONObject.toAmbientTrack(): AmbientAudioTrack? {
        val id = textValue("id")
        val title = textValue("title").trim().take(MAX_TITLE_LENGTH)
        if (id.isBlank() || title.isBlank()) return null
        if (has("is_streamable") && !optBoolean("is_streamable", false)) return null
        if (optBoolean("is_gated", false)) return null

        val user = optJSONObject("user") ?: JSONObject()
        val creator = user.textValue("name").ifBlank { user.textValue("handle") }
            .ifBlank { "Audius artist" }
            .take(MAX_CREATOR_LENGTH)
        val streamUrl = "$API_BASE/tracks".toHttpUrl().newBuilder()
            .addPathSegment(id)
            .addPathSegment("stream")
            .addQueryParameter("app_name", APP_NAME)
            .build()
            .toString()
        val landingUrl = textValue("permalink").ifBlank { "https://audius.co" }
        val tags = buildList {
            addAll(stringValues(opt("tags")))
            listOf("genre", "mood").forEach { key ->
                textValue(key).split(',', ';').map(String::trim).filter(String::isNotBlank).forEach(::add)
            }
        }.distinct().take(MAX_TAGS)
        val durationMs = numberValue("duration")?.times(1_000.0)?.toLong()?.takeIf { it > 0L }
        val downloadable = optBoolean("is_downloadable", optBoolean("downloadable", false))
        val downloadUrl = if (downloadable) {
            "$API_BASE/tracks".toHttpUrl().newBuilder()
                .addPathSegment(id)
                .addPathSegment("download")
                .addQueryParameter("app_name", APP_NAME)
                .build()
                .toString()
                .cappedAmbientAudioValue()
        } else null

        return AmbientAudioTrack(
            id = "audius:$id".cappedAmbientAudioValue(),
            title = title,
            creator = creator,
            streamUrl = streamUrl.cappedAmbientAudioValue(),
            landingUrl = landingUrl.cappedAmbientAudioValue(),
            provider = "audius",
            source = "audius",
            durationMs = durationMs,
            fileType = "mp3",
            tags = tags,
            downloadUrl = downloadUrl,
        )
    }

    private fun JSONObject.textValue(key: String): String = when (val value = opt(key)) {
        is String -> value.cappedAmbientAudioValue()
        is Number, is Boolean -> value.toString().cappedAmbientAudioValue()
        else -> ""
    }

    private fun JSONObject.numberValue(key: String): Double? = when (val value = opt(key)) {
        is Number -> value.toDouble()
        is String -> value.cappedAmbientAudioValue().toDoubleOrNull()
        else -> null
    }

    private fun stringValues(value: Any?): List<String> = when (value) {
        is JSONArray -> (0 until minOf(value.length(), MAX_TAGS)).mapNotNull { index ->
            value.opt(index)?.toString()?.trim()?.cappedAmbientAudioValue()?.takeIf(String::isNotBlank)
        }
        is String -> value.split(',', ';').mapNotNull {
            it.trim().cappedAmbientAudioValue().takeIf(String::isNotBlank)
        }
        else -> emptyList()
    }

    private companion object {
        const val API_BASE = "https://api.audius.co/v1"
        const val APP_NAME = "KitsugiBeta"
        const val PAGE_SIZE = 24
        const val MAX_QUERY_LENGTH = 120
        const val MAX_TITLE_LENGTH = 220
        const val MAX_CREATOR_LENGTH = 180
        const val MAX_TAGS = 32
        const val USER_AGENT = "KitsugiBeta/2.x (Android ambient audio; https://github.com/f26901964-eng/Kitsugi-Beta)"
    }
}
