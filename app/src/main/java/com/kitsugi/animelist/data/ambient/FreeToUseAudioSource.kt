package com.kitsugi.animelist.data.ambient

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Public Free To Use API adapter; excludes premium items that are not offered for free playback. */
internal class FreeToUseAudioSource(
    private val client: OkHttpClient = ambientAudioHttpClient,
) {
    suspend fun search(query: String): List<AmbientAudioTrack> = withContext(Dispatchers.IO) {
        val url = "$API_BASE/music/tracks/search".toHttpUrl().newBuilder()
            .addQueryParameter("query", query.take(MAX_QUERY_LENGTH))
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Free To Use search failed: HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) throw IOException("Free To Use returned an empty response")
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
        if (id.isBlank() || title.isBlank() || optBoolean("is_premium", false)) return null

        val fileUrl = optJSONObject("files")?.textValue("mp3").orEmpty()
        if (!fileUrl.startsWith("https://", ignoreCase = true)) return null
        val artists = tupleValues(optJSONArray("artists"), objectName = "name")
        val creator = artists.joinToString(", ").ifBlank { "Unknown artist" }.take(MAX_CREATOR_LENGTH)
        val tags = buildList {
            textValue("genre").takeIf(String::isNotBlank)?.let(::add)
            addAll(tupleValues(optJSONArray("tags_categories"), objectName = "name"))
            addAll(tupleValues(optJSONArray("tags"), objectName = "name"))
            addAll(tupleValues(optJSONArray("categories"), objectName = "name"))
        }.distinct().take(MAX_TAGS)
        val landingUrl = textValue("permalink").ifBlank { "https://freetouse.com/music" }
        val durationMs = numberValue("duration")?.times(1_000.0)?.toLong()?.takeIf { it > 0L }

        return AmbientAudioTrack(
            id = "free_to_use:$id".cappedAmbientAudioValue(),
            title = title,
            creator = creator,
            streamUrl = fileUrl,
            landingUrl = landingUrl,
            provider = "free_to_use",
            source = "free_to_use",
            durationMs = durationMs,
            fileType = "mp3",
            tags = tags,
            downloadUrl = fileUrl.cappedAmbientAudioValue(),
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

    private fun tupleValues(array: JSONArray?, objectName: String): List<String> = buildList {
        if (array == null) return@buildList
        for (index in 0 until minOf(array.length(), MAX_TAGS)) {
            when (val value = array.opt(index)) {
                is JSONArray -> {
                    val second = value.opt(1)
                    val text = when (second) {
                        is JSONObject -> second.optString(objectName, "")
                        is String -> second
                        else -> ""
                    }.trim().cappedAmbientAudioValue()
                    if (text.isNotBlank()) add(text)
                }
                is JSONObject -> {
                    val text = value.optString(objectName, "").trim().cappedAmbientAudioValue()
                    if (text.isNotBlank()) add(text)
                }
                is String -> value.trim().cappedAmbientAudioValue().takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }

    private companion object {
        const val API_BASE = "https://api.freetouse.com/v3"
        const val PAGE_SIZE = 24
        const val MAX_QUERY_LENGTH = 120
        const val MAX_TITLE_LENGTH = 220
        const val MAX_CREATOR_LENGTH = 180
        const val MAX_TAGS = 32
        const val USER_AGENT = "KitsugiBeta/2.x (Android ambient audio; https://github.com/BlackDamage-Dev/Kitsugi-Beta)"
    }
}
