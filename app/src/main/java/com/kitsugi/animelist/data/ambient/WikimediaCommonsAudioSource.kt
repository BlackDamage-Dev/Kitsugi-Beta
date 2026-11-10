package com.kitsugi.animelist.data.ambient

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

/** Direct Wikimedia Commons file search, separate from Openverse's Wikimedia index. */
internal class WikimediaCommonsAudioSource(
    private val client: OkHttpClient = ambientAudioHttpClient,
) {
    suspend fun search(query: String, plan: AmbientAudioPlan): List<AmbientAudioTrack> = withContext(Dispatchers.IO) {
        val queryTerms = query.trim().take(MAX_QUERY_LENGTH)
            .split(Regex("[^\\p{L}\\p{N}-]+"))
            .map(String::trim)
            .filter(String::isNotBlank)
        val searchTerms = if (query.trim().equals(plan.query, ignoreCase = true)) {
            (plan.matchTerms + queryTerms).take(3).joinToString(" ")
        } else {
            queryTerms.take(3).joinToString(" ")
        }.ifBlank { "ambient audio" }
        val found = LinkedHashMap<String, AmbientAudioTrack>()
        MIME_TYPES.forEach { mime ->
            WikimediaCommonsRequestLimiter.awaitSlot()
            searchMime(searchTerms, mime).forEach { track -> found.putIfAbsent(track.id, track) }
        }
        found.values.toList()
    }

    private fun searchMime(searchTerms: String, mime: String): List<AmbientAudioTrack> {
        val url = "$API_BASE/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("format", "json")
            .addQueryParameter("formatversion", "2")
            .addQueryParameter("generator", "search")
            .addQueryParameter("gsrnamespace", "6")
            .addQueryParameter("gsrlimit", PAGE_SIZE.toString())
            .addQueryParameter("gsrsearch", "$searchTerms filemime:$mime")
            .addQueryParameter("prop", "imageinfo")
            .addQueryParameter("iiprop", "url|size|mime|mediatype|extmetadata|metadata")
            .addQueryParameter("iiextmetadatafilter", "Artist|ObjectName|ImageDescription")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Wikimedia Commons search failed: HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) throw IOException("Wikimedia Commons returned an empty response")
            val pages = JSONObject(body).optJSONObject("query")?.optJSONArray("pages") ?: JSONArray()
            buildList {
                for (index in 0 until pages.length()) {
                    val page = pages.optJSONObject(index) ?: continue
                    page.toAmbientTrack()?.let(::add)
                }
            }
        }
    }

    internal fun parseTrack(page: JSONObject): AmbientAudioTrack? = page.toAmbientTrack()

    private fun JSONObject.toAmbientTrack(): AmbientAudioTrack? {
        val titleFromPage = textValue("title").removePrefix("File:").trim()
        val imageInfo = optJSONArray("imageinfo")?.optJSONObject(0) ?: return null
        val streamUrl = imageInfo.textValue("url")
        if (!streamUrl.startsWith("https://", ignoreCase = true)) return null
        val mediaType = imageInfo.textValue("mediatype")
        val mime = imageInfo.textValue("mime").lowercase(Locale.ROOT)
        if (!mediaType.equals("AUDIO", ignoreCase = true) && !mime.startsWith("audio/")) return null

        val extension = streamUrl.substringBefore('?').substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension !in SUPPORTED_EXTENSIONS) return null
        val extMetadata = imageInfo.optJSONObject("extmetadata") ?: JSONObject()
        val title = extMetadata.metadataText("ObjectName").ifBlank { titleFromPage }
            .take(MAX_TITLE_LENGTH)
        if (title.isBlank()) return null
        val creator = extMetadata.metadataText("Artist").ifBlank { "Wikimedia contributor" }
            .take(MAX_CREATOR_LENGTH)
        val description = extMetadata.metadataText("ImageDescription")
        val pageTitle = textValue("title")
        val landingUrl = imageInfo.textValue("descriptionurl").ifBlank {
            "$API_BASE/wiki/${pageTitle.encodePathSegment()}"
        }
        val fileSize = imageInfo.longValue("size")?.takeIf { it > 0L }
        val durationMs = imageInfo.durationMs()
        val id = "wikimedia_commons:${pageTitle.ifBlank { streamUrl.hashCode().toString() }}"

        return AmbientAudioTrack(
            id = id.cappedAmbientAudioValue(),
            title = title,
            creator = creator,
            streamUrl = streamUrl,
            landingUrl = landingUrl.cappedAmbientAudioValue(),
            provider = "wikimedia_commons",
            source = "wikimedia_commons",
            durationMs = durationMs,
            fileType = extension,
            tags = description.split(Regex("[,;|\\n]")).mapNotNull {
                it.trim().cappedAmbientAudioValue().takeIf(String::isNotBlank)
            }.take(MAX_TAGS),
            fileSizeBytes = fileSize,
            downloadUrl = streamUrl.cappedAmbientAudioValue(),
        )
    }

    private fun JSONObject.longValue(key: String): Long? = when (val value = opt(key)) {
        is Number -> value.toLong()
        is String -> value.cappedAmbientAudioValue().toLongOrNull()
        else -> null
    }

    private fun JSONObject.durationMs(): Long? {
        val metadata = optJSONArray("metadata") ?: return null
        for (index in 0 until metadata.length()) {
            val item = metadata.optJSONObject(index) ?: continue
            val key = item.optString("name", "").cappedAmbientAudioValue().lowercase(Locale.ROOT)
            if (key !in DURATION_FIELDS) continue
            val raw = when (val value = item.opt("value")) {
                is String -> value.cappedAmbientAudioValue()
                is Number -> value.toString()
                else -> ""
            }
            raw.toDoubleOrNull()?.let { seconds ->
                return (seconds * 1_000.0).toLong().takeIf { it > 0L }
            }
            val parts = raw.split(':').mapNotNull(String::toDoubleOrNull)
            if (parts.isNotEmpty() && parts.size <= 3) {
                val seconds = parts.reversed().mapIndexed { partIndex, part ->
                    part * when (partIndex) {
                        0 -> 1.0
                        1 -> 60.0
                        else -> 3_600.0
                    }
                }.sum()
                return (seconds * 1_000.0).toLong().takeIf { it > 0L }
            }
        }
        return null
    }

    private fun JSONObject.metadataText(key: String): String {
        val raw = optJSONObject(key)?.optString("value", "") ?: ""
        return stripAmbientAudioHtml(raw)
    }

    private fun JSONObject.textValue(key: String): String = when (val value = opt(key)) {
        is String -> value.cappedAmbientAudioValue()
        is Number, is Boolean -> value.toString().cappedAmbientAudioValue()
        else -> ""
    }

    private fun String.encodePathSegment(): String =
        "$API_BASE".toHttpUrl().newBuilder().addPathSegment(this.cappedAmbientAudioValue()).build()
            .encodedPath.removePrefix("/")

    private companion object {
        const val API_BASE = "https://commons.wikimedia.org"
        const val PAGE_SIZE = 25
        const val MAX_QUERY_LENGTH = 120
        const val MAX_TITLE_LENGTH = 220
        const val MAX_CREATOR_LENGTH = 180
        const val MAX_TAGS = 24
        const val USER_AGENT = "KitsugiBeta/2.x (Android ambient audio; https://github.com/BlackDamage-Dev/Kitsugi-Beta)"
        val MIME_TYPES = listOf("audio/ogg", "audio/mpeg")
        val SUPPORTED_EXTENSIONS = setOf(
            "ogg", "oga", "opus", "mp3", "flac", "m4a", "aac", "wav", "aif", "aiff", "m4b",
            "alac", "amr", "3gp", "mka", "webm", "ape", "wv", "tta", "caf", "au", "wma", "ac3", "eac3",
        )
        val DURATION_FIELDS = setOf("length", "duration", "playtime_seconds", "length_seconds")
    }
}

private object WikimediaCommonsRequestLimiter {
    private const val MIN_INTERVAL_MS = 1_000L
    private val mutex = Mutex()
    private var nextSlotAt = 0L

    suspend fun awaitSlot() {
        val waitMs = mutex.withLock {
            val now = System.currentTimeMillis()
            val scheduledAt = maxOf(now, nextSlotAt)
            nextSlotAt = scheduledAt + MIN_INTERVAL_MS
            scheduledAt - now
        }
        if (waitMs > 0L) delay(waitMs)
    }
}
