package com.kitsugi.animelist.data.ambient

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Openverse's anonymous, read-only audio API, requested one upstream dataset at a time. */
internal class OpenverseAudioSource(
    private val client: OkHttpClient = ambientAudioHttpClient,
) {
    suspend fun search(query: String, source: AmbientAudioSource): List<AmbientAudioTrack> {
        OpenverseRequestLimiter.awaitSlot()
        return withContext(Dispatchers.IO) {
            val openverseSource = source.openverseSource
                ?: throw IllegalArgumentException("Not an Openverse audio source")
            val url = "https://api.openverse.org/v1/audio/".toHttpUrl().newBuilder()
                .addQueryParameter("q", query.take(MAX_QUERY_LENGTH))
                .addQueryParameter("source", openverseSource)
                .addQueryParameter("page_size", PAGE_SIZE.toString())
                .build()
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val reason = if (response.code == 429) "rate limited" else "HTTP ${response.code}"
                    throw IOException("Openverse audio search failed: $reason")
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) throw IOException("Openverse returned an empty response")
                val results = JSONObject(body).optJSONArray("results") ?: JSONArray()
                buildList {
                    for (index in 0 until results.length()) {
                        val item = results.optJSONObject(index) ?: continue
                        val track = item.toAmbientTrack(source) ?: continue
                        if (!track.source.contains("jamendo", ignoreCase = true) &&
                            !track.provider.contains("jamendo", ignoreCase = true)
                        ) {
                            add(track)
                        }
                    }
                }
            }
        }
    }

    internal fun parseTrack(item: JSONObject, requestedSource: AmbientAudioSource): AmbientAudioTrack? =
        item.toAmbientTrack(requestedSource)

    private fun JSONObject.toAmbientTrack(requestedSource: AmbientAudioSource): AmbientAudioTrack? {
        val title = textValue("title").trim().take(MAX_TITLE_LENGTH)
        val streamUrl = textValue("url").ifBlank { textValue("download_url") }
        val downloadUrl = textValue("download_url").ifBlank { streamUrl }
        if (title.isBlank() || streamUrl.isBlank()) return null

        val provider = textValue("provider").ifBlank { requestedSource.openverseSource.orEmpty() }
        val source = textValue("source").ifBlank { requestedSource.openverseSource.orEmpty() }
        val fileType = textValue("filetype").ifBlank {
            streamUrl.substringBefore('?').substringAfterLast('.', "")
        }.lowercase()
        val id = textValue("id").ifBlank { "$source:${streamUrl.hashCode()}" }
        val tags = arrayValues(optJSONArray("tags"))

        return AmbientAudioTrack(
            id = "openverse:$id".cappedAmbientAudioValue(),
            title = title,
            creator = textValue("creator").ifBlank { "Unknown creator" }.take(MAX_CREATOR_LENGTH),
            streamUrl = streamUrl,
            landingUrl = textValue("foreign_landing_url").ifBlank { textValue("detail_url") },
            provider = provider,
            source = source,
            durationMs = longValue("duration")?.let { seconds ->
                seconds.takeIf { it > 0L }?.coerceAtMost(Long.MAX_VALUE / 1_000L)?.times(1_000L)
            },
            fileType = fileType,
            tags = tags,
            fileSizeBytes = longValue("filesize")?.takeIf { it > 0L },
            downloadUrl = downloadUrl.takeIf { it.startsWith("https://", ignoreCase = true) }
                ?.cappedAmbientAudioValue(),
        )
    }

    private fun JSONObject.textValue(key: String): String = when (val value = opt(key)) {
        is String -> value.cappedAmbientAudioValue()
        is Number, is Boolean -> value.toString().cappedAmbientAudioValue()
        is JSONObject -> value.optString("name", "").cappedAmbientAudioValue()
        else -> ""
    }

    private fun JSONObject.longValue(key: String): Long? = when (val value = opt(key)) {
        is Number -> value.toLong()
        is String -> value.cappedAmbientAudioValue().toLongOrNull()
        else -> null
    }

    private fun arrayValues(array: JSONArray?): List<String> = buildList {
        if (array == null) return@buildList
        for (index in 0 until minOf(array.length(), MAX_TAGS)) {
            val tag = when (val value = array.opt(index)) {
                is String -> value
                is JSONObject -> value.optString("name", "")
                else -> ""
            }.trim().take(MAX_TAG_VALUE_LENGTH)
            if (tag.isNotBlank()) add(tag)
        }
    }

    private companion object {
        const val PAGE_SIZE = 20
        const val MAX_QUERY_LENGTH = 120
        const val MAX_TITLE_LENGTH = 220
        const val MAX_CREATOR_LENGTH = 180
        const val MAX_TAGS = 32
        const val MAX_TAG_VALUE_LENGTH = 180
        const val USER_AGENT = "KitsugiBeta/2.x (Android ambient audio; Openverse API client)"
    }
}

internal val ambientAudioHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(22, TimeUnit.SECONDS)
        .followSslRedirects(false)
        .build()
}
