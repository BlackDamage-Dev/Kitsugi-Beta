package com.kitsugi.animelist.data.ambient

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

/** Read-only Internet Archive audio search with metadata lookup for direct audio files. */
internal class InternetArchiveAudioSource(
    private val client: OkHttpClient = ambientAudioHttpClient,
) {
    suspend fun search(query: String, plan: AmbientAudioPlan): List<AmbientAudioTrack> {
        val ids = withContext(Dispatchers.IO) { searchItemIds(query, plan) }
        if (ids.isEmpty()) return emptyList()
        return coroutineScope {
            ids.take(MAX_ITEMS_TO_INSPECT)
                .map { id ->
                    async(Dispatchers.IO) {
                        try {
                            readItem(id)
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
        }
    }

    private fun searchItemIds(query: String, plan: AmbientAudioPlan): List<String> {
        val queryWords = query.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}-]+"))
        val prioritizedWords = if (query.trim().equals(plan.query, ignoreCase = true)) {
            plan.matchTerms + queryWords
        } else {
            queryWords + plan.matchTerms
        }
        val words = prioritizedWords
            .asSequence()
            .map { it.trim() }
            .filter { it.length >= 3 }
            .filterNot { it in GENERIC_QUERY_WORDS }
            .distinct()
            .take(4)
            .toList()
            .ifEmpty { listOf("ambient") }
        val luceneTerms = words.joinToString(" OR ") { "\"${escapeLucene(it)}\"" }
        val luceneQuery = "mediatype:audio AND ($luceneTerms)"

        val url = "https://archive.org/advancedsearch.php".toHttpUrl().newBuilder()
            .addQueryParameter("q", luceneQuery)
            .addQueryParameter("fl[]", "identifier")
            .addQueryParameter("fl[]", "title")
            .addQueryParameter("fl[]", "creator")
            .addQueryParameter("rows", SEARCH_RESULT_COUNT.toString())
            .addQueryParameter("page", "1")
            .addQueryParameter("output", "json")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Internet Archive search failed: HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) throw IOException("Internet Archive returned an empty response")
            val docs = JSONObject(body).optJSONObject("response")?.optJSONArray("docs") ?: JSONArray()
            buildList {
                for (index in 0 until docs.length()) {
                    val id = docs.optJSONObject(index)?.optString("identifier", "")
                        ?.trim()?.cappedAmbientAudioValue().orEmpty()
                    if (id.isNotBlank() && id !in this) add(id)
                }
            }
        }
    }

    private fun readItem(identifier: String): AmbientAudioTrack? {
        val safeIdentifier = identifier.cappedAmbientAudioValue()
        val url = "https://archive.org/metadata".toHttpUrl().newBuilder()
            .addPathSegment(safeIdentifier)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return@use null
            parseItemMetadata(body, safeIdentifier)
        }
    }

    internal fun parseItemMetadata(body: String, identifier: String): AmbientAudioTrack? = runCatching {
        JSONObject(body).toAmbientTrack(identifier.cappedAmbientAudioValue())
    }.getOrNull()

    private fun JSONObject.toAmbientTrack(identifier: String): AmbientAudioTrack? {
        val metadata = optJSONObject("metadata") ?: return null
        val title = metadata.textValue("title").trim().take(MAX_TITLE_LENGTH)
        if (title.isBlank()) return null
        val creator = metadata.textValue("creator").ifBlank { "Unknown creator" }.take(MAX_CREATOR_LENGTH)
        val files = optJSONArray("files") ?: return null
        val file = selectPlayableFile(files) ?: return null
        val fileName = file.optString("name", "").trim().cappedAmbientAudioValue()
        if (fileName.isBlank()) return null

        val streamUrl = "https://archive.org/download".toHttpUrl().newBuilder()
            .addPathSegment(identifier)
            .addPathSegment(fileName)
            .build()
            .toString()
            .cappedAmbientAudioValue()
        val landingUrl = "https://archive.org/details/${identifier.encodePathSegment()}"
            .cappedAmbientAudioValue()
        val tags = metadata.stringList("subject") + metadata.stringList("description")
        val fileType = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val size = file.optString("size", "").cappedAmbientAudioValue().toLongOrNull()
        val duration = (metadata.textValue("runtime").ifBlank { metadata.textValue("duration") })
            .toDurationMs()

        return AmbientAudioTrack(
            id = "internet_archive:$identifier/$fileName".cappedAmbientAudioValue(),
            title = title,
            creator = creator,
            streamUrl = streamUrl,
            landingUrl = landingUrl,
            provider = "internet_archive",
            source = "internet_archive",
            durationMs = duration,
            fileType = fileType,
            tags = tags.take(MAX_TAGS),
            fileSizeBytes = size,
            downloadUrl = streamUrl,
        )
    }

    private fun selectPlayableFile(files: JSONArray): JSONObject? {
        val supportedExtensions = listOf(
            "mp3", "m4a", "aac", "wav", "flac", "ogg", "oga", "opus", "aif", "aiff", "m4b",
            "alac", "amr", "3gp", "mka", "webm", "ape", "wv", "tta", "caf", "au", "wma", "ac3", "eac3",
        )
        return (0 until files.length())
            .mapNotNull { files.optJSONObject(it) }
            .filter { file ->
                val name = file.optString("name", "").cappedAmbientAudioValue()
                val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                extension in supportedExtensions &&
                    !name.contains("thumb", ignoreCase = true) &&
                    !name.endsWith(".torrent", ignoreCase = true)
            }
            .minByOrNull { file ->
                val extension = file.optString("name", "").cappedAmbientAudioValue()
                    .substringAfterLast('.', "").lowercase(Locale.ROOT)
                supportedExtensions.indexOf(extension).let { if (it < 0) Int.MAX_VALUE else it }
            }
    }

    private fun JSONObject.textValue(key: String): String = when (val value = opt(key)) {
        is String -> value.cappedAmbientAudioValue()
        is Number, is Boolean -> value.toString().cappedAmbientAudioValue()
        is JSONArray -> (0 until value.length()).mapNotNull { index ->
            value.opt(index)?.toString()?.cappedAmbientAudioValue()?.takeIf(String::isNotBlank)
        }.joinToString(" ").cappedAmbientAudioValue()
        else -> ""
    }

    private fun JSONObject.stringList(key: String): List<String> = when (val value = opt(key)) {
        is String -> listOf(value.trim().cappedAmbientAudioValue()).filter(String::isNotBlank)
        is JSONArray -> (0 until minOf(value.length(), MAX_TAGS)).mapNotNull { index ->
            value.opt(index)?.toString()?.trim()?.cappedAmbientAudioValue()?.takeIf(String::isNotBlank)
        }
        else -> emptyList()
    }

    private fun String.toDurationMs(): Long? {
        val value = trim().cappedAmbientAudioValue()
        if (value.isBlank()) return null
        value.toDoubleOrNull()?.let { seconds ->
            return (seconds * 1_000.0).toLong().takeIf { it > 0L }
        }
        val pieces = value.split(':').mapNotNull { it.toDoubleOrNull() }
        if (pieces.isEmpty() || pieces.size > 3) return null
        val seconds = pieces.reversed().mapIndexed { index, part ->
            part * when (index) {
                0 -> 1.0
                1 -> 60.0
                else -> 3_600.0
            }
        }.sum()
        return (seconds * 1_000.0).toLong().takeIf { it > 0L }
    }

    private fun escapeLucene(value: String): String = value.replace('"', ' ').replace(':', ' ').trim()

    private fun String.encodePathSegment(): String =
        "https://example.invalid".toHttpUrl().newBuilder().addPathSegment(this).build().encodedPath.removePrefix("/")

    private companion object {
        const val SEARCH_RESULT_COUNT = 12
        const val MAX_ITEMS_TO_INSPECT = 4
        const val MAX_TITLE_LENGTH = 220
        const val MAX_CREATOR_LENGTH = 180
        const val MAX_TAGS = 24
        const val USER_AGENT = "KitsugiBeta/2.x (Android ambient audio; Internet Archive public API client)"
        val GENERIC_QUERY_WORDS = setOf(
            "instrumental", "background", "music", "ambient", "soundscape", "cinematic", "no", "vocals"
        )
    }
}
