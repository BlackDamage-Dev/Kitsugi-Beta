package com.kitsugi.animelist.data.ambient

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Jamendo v3.0 — **kişisel Client ID (BYOK)** ile çalışır.
 *
 * Eski (v2.4.466) panelin kaynak listesinde Jamendo vardı; v2.4.471'deki
 * tek ekranlı tasarıma geçişte çok kaynaklı motorda karşılığı yoktu.
 * v2.4.480'de eski panel düzeni geri dönerken bu kaynak motora eklendi,
 * böylece kaynak listesi eski listenin **üst kümesi** olur.
 *
 * Lisans notu: Jamendo API şartları parçaların yalnızca **akış** olarak
 * dinlenmesine izin verir; bu yüzden [downloadUrl] hiçbir zaman dolmaz.
 */
internal class JamendoAudioSource(
    private val client: OkHttpClient = ambientAudioHttpClient,
) {
    suspend fun search(
        clientId: String,
        query: String,
        limit: Int = PAGE_SIZE,
    ): List<AmbientAudioTrack> = withContext(Dispatchers.IO) {
        if (clientId.isBlank() || query.isBlank()) return@withContext emptyList()
        val url = "$API_BASE/tracks/".toHttpUrl().newBuilder()
            .addQueryParameter("client_id", clientId.trim())
            .addQueryParameter("format", "json")
            .addQueryParameter("limit", limit.coerceIn(1, 50).toString())
            .addQueryParameter("search", query.take(MAX_QUERY_LENGTH))
            .addQueryParameter("audioformat", "mp32")
            .addQueryParameter("include", "musicinfo")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Jamendo search failed: HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) throw IOException("Jamendo returned an empty response")
            val results = JSONObject(body).optJSONArray("results") ?: JSONArray()
            buildList {
                for (index in 0 until results.length()) {
                    val item = results.optJSONObject(index) ?: continue
                    item.toAmbientTrack()?.let(::add)
                }
            }
        }
    }

    private fun JSONObject.toAmbientTrack(): AmbientAudioTrack? {
        val id = textValue("id")
        val title = textValue("name").trim().take(MAX_TITLE_LENGTH)
        if (id.isBlank() || title.isBlank()) return null
        val streamUrl = textValue("audio")
        if (!streamUrl.startsWith("https://", ignoreCase = true)) return null
        val creator = textValue("artist_name").ifBlank { "Jamendo artist" }
            .take(MAX_CREATOR_LENGTH)
        val landingUrl = textValue("shareurl")
            .takeIf { it.startsWith("https://", ignoreCase = true) }
            ?: "https://www.jamendo.com"
        val durationMs = numberValue("duration")?.times(1_000.0)?.toLong()?.takeIf { it > 0L }
        val tags = buildList {
            val musicInfo = optJSONObject("musicinfo")?.optJSONObject("tags")
            musicInfo?.let { tagsObject ->
                listOf("genres", "instruments", "vartags").forEach { key ->
                    addAll(stringValues(tagsObject.opt(key)))
                }
            }
            textValue("genre").split(',', ';').map(String::trim)
                .filter(String::isNotBlank).forEach(::add)
        }.distinct().take(MAX_TAGS)

        return AmbientAudioTrack(
            id = "jamendo:$id".cappedAmbientAudioValue(),
            title = title,
            creator = creator,
            streamUrl = streamUrl.cappedAmbientAudioValue(),
            landingUrl = landingUrl.cappedAmbientAudioValue(),
            provider = "jamendo",
            source = "jamendo",
            durationMs = durationMs,
            fileType = "mp3",
            tags = tags,
            // Jamendo lisansı indirmeye izin vermez — yalnızca akış.
            downloadUrl = null,
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
        const val API_BASE = "https://api.jamendo.com/v3.0"
        const val PAGE_SIZE = 20
        const val MAX_QUERY_LENGTH = 120
        const val MAX_TITLE_LENGTH = 220
        const val MAX_CREATOR_LENGTH = 180
        const val MAX_TAGS = 32
        const val USER_AGENT =
            "KitsugiBeta/2.x (Android ambient audio; Jamendo API client, personal client_id)"
    }
}
