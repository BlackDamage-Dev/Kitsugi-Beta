package com.kitsugi.animelist.data.ambient

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.IOException
import java.io.StringReader
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** Bounded RSS search adapter for ccMixter's public Query API. */
internal class CcMixterAudioSource(
    private val client: OkHttpClient = ambientAudioHttpClient,
) {
    suspend fun search(query: String): List<AmbientAudioTrack> = withContext(Dispatchers.IO) {
        val url = "$API_BASE/api/query".toHttpUrl().newBuilder()
            .addQueryParameter("search", query.take(MAX_QUERY_LENGTH))
            .addQueryParameter("f", "rss")
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/rss+xml, application/xml, text/xml")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("ccMixter search failed: HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) throw IOException("ccMixter returned an empty response")
            parseFeed(body)
        }
    }

    internal fun parseFeed(body: String): List<AmbientAudioTrack> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isXIncludeAware = false
            isExpandEntityReferences = false
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(body.take(MAX_FEED_LENGTH))))
        val namespacedItems = document.getElementsByTagNameNS("*", "item")
        val items = if (namespacedItems.length > 0) namespacedItems else document.getElementsByTagName("item")
        return buildList {
            for (itemIndex in 0 until minOf(items.length, PAGE_SIZE)) {
                val item = items.item(itemIndex) as? Element ?: continue
                val entry = FeedEntry()
                for (childIndex in 0 until item.childNodes.length) {
                    val child = item.childNodes.item(childIndex) as? Element ?: continue
                    val name = (child.localName ?: child.nodeName.substringAfter(':')).lowercase(Locale.ROOT)
                    when (name) {
                        "enclosure", "content" -> {
                            val url = child.getAttribute("url").cappedAmbientAudioValue()
                            if (url.isNotBlank() && entry.streamUrl.isBlank()) {
                                entry.streamUrl = url
                                entry.mimeType = child.getAttribute("type").cappedAmbientAudioValue()
                                entry.fileSize = child.getAttribute("length").cappedAmbientAudioValue()
                            }
                        }
                        "title" -> entry.title = child.textContent.trim().cappedAmbientAudioValue()
                        "link" -> entry.landingUrl = child.textContent.trim().cappedAmbientAudioValue()
                        "creator", "author", "artist" -> if (entry.creator.isBlank()) {
                            entry.creator = child.textContent.trim().cappedAmbientAudioValue()
                        }
                        "duration" -> entry.duration = child.textContent.trim().cappedAmbientAudioValue()
                        "category" -> child.textContent.trim().cappedAmbientAudioValue()
                            .takeIf(String::isNotBlank)?.let { if (entry.tags.size < MAX_TAGS) entry.tags += it }
                    }
                }
                entry.toAmbientTrack()?.let(::add)
            }
        }
    }

    private fun FeedEntry.toAmbientTrack(): AmbientAudioTrack? {
        val title = title.trim().take(MAX_TITLE_LENGTH)
        val url = streamUrl.trim().cappedAmbientAudioValue()
        if (title.isBlank() || !url.startsWith("https://", ignoreCase = true)) return null
        val extension = url.substringBefore('?').substringAfterLast('.', "").lowercase(Locale.ROOT)
        val fileType = AmbientAudioFormats.canonicalType(extension.ifBlank { mimeType })
        if (fileType !in SUPPORTED_TYPES) return null
        val creator = creator.trim().ifBlank { "ccMixter artist" }.take(MAX_CREATOR_LENGTH)
        val safeLandingUrl = landingUrl.trim().cappedAmbientAudioValue()
        val durationMs = duration.toDurationMs()
        val size = fileSize.toLongOrNull()?.takeIf { it > 0L }

        return AmbientAudioTrack(
            id = "ccmixter:${url.hashCode()}".cappedAmbientAudioValue(),
            title = title,
            creator = creator,
            streamUrl = url,
            landingUrl = safeLandingUrl,
            provider = "ccmixter",
            source = "ccmixter",
            durationMs = durationMs,
            fileType = fileType,
            tags = tags.map { it.cappedAmbientAudioValue() }.take(MAX_TAGS),
            fileSizeBytes = size,
            downloadUrl = url,
        )
    }

    private fun String.toDurationMs(): Long? {
        val value = trim().cappedAmbientAudioValue()
        if (value.isBlank()) return null
        value.toDoubleOrNull()?.let { return (it * 1_000.0).toLong().takeIf { ms -> ms > 0L } }
        val parts = value.split(':').mapNotNull(String::toDoubleOrNull)
        if (parts.isEmpty() || parts.size > 3) return null
        val seconds = parts.reversed().mapIndexed { index, part ->
            part * when (index) {
                0 -> 1.0
                1 -> 60.0
                else -> 3_600.0
            }
        }.sum()
        return (seconds * 1_000.0).toLong().takeIf { it > 0L }
    }

    private data class FeedEntry(
        var title: String = "",
        var creator: String = "",
        var landingUrl: String = "",
        var streamUrl: String = "",
        var mimeType: String = "",
        var fileSize: String = "",
        var duration: String = "",
        val tags: MutableList<String> = mutableListOf(),
    )

    private companion object {
        const val API_BASE = "https://ccmixter.org"
        const val PAGE_SIZE = 20
        const val MAX_QUERY_LENGTH = 120
        const val MAX_FEED_LENGTH = 1_500_000
        const val MAX_TITLE_LENGTH = 220
        const val MAX_CREATOR_LENGTH = 180
        const val MAX_TAGS = 24
        const val USER_AGENT = "KitsugiBeta/2.x (Android ambient audio; https://github.com/BlackDamage-Dev/Kitsugi-Beta)"
        val SUPPORTED_TYPES = AmbientAudioFormats.supportedPlaybackTypes - setOf("m3u8", "hls")
    }
}
