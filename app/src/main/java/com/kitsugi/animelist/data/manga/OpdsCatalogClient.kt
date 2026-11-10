package com.kitsugi.animelist.data.manga

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID

data class OpdsNavigationItem(
    val title: String,
    val url: String,
    val subtitle: String? = null,
    val iconUrl: String? = null,
)

data class OpdsAcquisition(
    val title: String,
    val author: String?,
    val url: String,
    val mimeType: String,
    val summary: String? = null,
    val coverUrl: String? = null,
)

data class OpdsFeed(
    val title: String,
    val navigationItems: List<OpdsNavigationItem> = emptyList(),
    val acquisitions: List<OpdsAcquisition> = emptyList(),
    val nextPageUrl: String? = null,
    val searchTemplateUrl: String? = null,
)

data class OpdsCredentials(
    val username: String,
    val password: String,
)

/**
 * OPDS 1 (Atom) & OPDS 2 (JSON) client with navigation feeds, OpenSearch,
 * Basic Auth support, pagination, download progress, 512 MB safety limits,
 * and SHA-256 deduplication.
 */
class OpdsCatalogClient(private val client: OkHttpClient = OkHttpClient()) {

    suspend fun fetchFeed(
        url: String,
        credentials: OpdsCredentials? = null,
    ): OpdsFeed = withContext(Dispatchers.IO) {
        requireHttp(url)
        val (body, contentType) = get(url, credentials)
        val isJson = "json" in contentType || body.trimStart().startsWith('{')
        if (isJson) parseJsonFeed(body, url) else parseAtomFeed(body, url)
    }

    /** Backward-compatible helper returning only acquisitions */
    suspend fun fetch(
        url: String,
        credentials: OpdsCredentials? = null,
    ): List<OpdsAcquisition> = fetchFeed(url, credentials).acquisitions

    suspend fun search(
        templateUrl: String,
        query: String,
        credentials: OpdsCredentials? = null,
    ): OpdsFeed = withContext(Dispatchers.IO) {
        val targetUrl = resolveSearchUrl(templateUrl, query, credentials)
        fetchFeed(targetUrl, credentials)
    }

    suspend fun download(
        acquisition: OpdsAcquisition,
        directory: File,
        credentials: OpdsCredentials? = null,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null,
    ): File = withContext(Dispatchers.IO) {
        requireHttp(acquisition.url)
        val extension = when {
            "epub" in acquisition.mimeType -> "epub"
            "pdf" in acquisition.mimeType -> "pdf"
            "rar" in acquisition.mimeType || "cbr" in acquisition.mimeType -> "cbr"
            "zip" in acquisition.mimeType || "cbz" in acquisition.mimeType -> "cbz"
            else -> URI(acquisition.url).path?.substringAfterLast('.', "")?.lowercase()
        }
        require(extension in setOf("epub", "pdf", "cbz", "cbr", "zip", "rar")) {
            "Bu yayın desteklenen bir dosya bağlantısı sunmuyor"
        }
        directory.mkdirs()
        val temporary = File(directory, "opds-${UUID.nameUUIDFromBytes(acquisition.url.toByteArray())}.tmp")
        val digest = MessageDigest.getInstance("SHA-256")

        val reqBuilder = Request.Builder().url(acquisition.url)
        if (credentials != null && credentials.username.isNotBlank()) {
            reqBuilder.header("Authorization", Credentials.basic(credentials.username, credentials.password))
        }
        val request = reqBuilder.build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("OPDS indirme hatası: HTTP ${response.code}")
                val body = response.body ?: throw IOException("OPDS boş dosya yanıtı")
                val contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
                if (contentLength > 0) {
                    require(contentLength <= MangaReaderStorage.MAX_IMPORT_BYTES) { "Dosya 512 MB sınırını aşıyor" }
                }
                var total = 0L
                temporary.outputStream().buffered().use { outputStream ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MangaReaderStorage.MAX_IMPORT_BYTES) error("Dosya 512 MB sınırını aşıyor")
                            digest.update(buffer, 0, count)
                            outputStream.write(buffer, 0, count)
                            onProgress?.invoke(total, contentLength)
                        }
                    }
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val output = File(directory, "$hash.$extension")
            if (output.exists()) temporary.delete()
            else if (!temporary.renameTo(output)) error("İndirilen dosya depolanamadı")
            output
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    private suspend fun resolveSearchUrl(templateUrl: String, query: String, credentials: OpdsCredentials?): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        if (templateUrl.contains("{searchTerms}")) {
            return templateUrl.replace("{searchTerms}", encoded)
        }
        // If template points to an OpenSearch description XML document:
        if (templateUrl.endsWith(".xml") || templateUrl.contains("opensearch")) {
            return runCatching {
                val (xml, _) = get(templateUrl, credentials)
                val doc = Jsoup.parse(xml, templateUrl, Parser.xmlParser())
                val urlTag = doc.select("Url[type*=opds], Url[type*=atom], Url").firstOrNull()
                val t = urlTag?.attr("template")
                if (t != null && t.contains("{searchTerms}")) {
                    resolve(templateUrl, t.replace("{searchTerms}", encoded)) ?: templateUrl
                } else templateUrl
            }.getOrDefault(templateUrl)
        }
        val separator = if (templateUrl.contains('?')) '&' else '?'
        return "$templateUrl${separator}q=$encoded"
    }

    private fun parseAtomFeed(xml: String, baseUrl: String): OpdsFeed {
        val document = Jsoup.parse(xml, baseUrl, Parser.xmlParser())
        val feedTitle = document.selectFirst("feed > title")?.text().orEmpty().ifBlank { "Katalog" }

        val searchLink = document.select("feed > link[rel=search]").firstOrNull()?.let {
            resolve(baseUrl, it.attr("href"))
        }

        val nextLink = document.select("feed > link[rel=next]").firstOrNull()?.let {
            resolve(baseUrl, it.attr("href"))
        }

        val navItems = mutableListOf<OpdsNavigationItem>()
        val acquisitions = mutableListOf<OpdsAcquisition>()

        // Check top-level navigation links in feed
        document.select("feed > link").forEach { link ->
            val rel = link.attr("rel")
            val type = link.attr("type")
            val title = link.attr("title").ifBlank { link.text() }
            if (isNavRel(rel, type) && title.isNotBlank()) {
                val href = resolve(baseUrl, link.attr("href"))
                if (href != null) {
                    navItems.add(OpdsNavigationItem(title = title, url = href))
                }
            }
        }

        // Parse entries
        document.select("feed > entry").forEach { entry ->
            val title = entry.selectFirst("title")?.text().orEmpty().ifBlank { "İsimsiz yayın" }
            val author = entry.selectFirst("author > name")?.text()
            val summary = entry.selectFirst("summary, content")?.text()

            val coverHref = entry.select("link[rel*=image], link[rel*=thumbnail]").firstOrNull()?.let {
                resolve(baseUrl, it.attr("href"))
            }

            val acquisitionLink = entry.select("link").firstOrNull { link ->
                val rel = link.attr("rel")
                val type = link.attr("type")
                (rel.contains("acquisition") || rel == "alternate") && supportedMime(type)
            }

            if (acquisitionLink != null) {
                val href = resolve(baseUrl, acquisitionLink.attr("href"))
                if (href != null) {
                    acquisitions.add(
                        OpdsAcquisition(
                            title = title,
                            author = author,
                            url = href,
                            mimeType = acquisitionLink.attr("type"),
                            summary = summary,
                            coverUrl = coverHref,
                        )
                    )
                }
            } else {
                // Navigation entry (e.g. subsection or category)
                val navLink = entry.select("link").firstOrNull { link ->
                    val rel = link.attr("rel")
                    val type = link.attr("type")
                    isNavRel(rel, type) || rel == "alternate"
                }
                if (navLink != null) {
                    val href = resolve(baseUrl, navLink.attr("href"))
                    if (href != null) {
                        navItems.add(
                            OpdsNavigationItem(
                                title = title,
                                url = href,
                                subtitle = summary ?: author,
                                iconUrl = coverHref,
                            )
                        )
                    }
                }
            }
        }

        return OpdsFeed(
            title = feedTitle,
            navigationItems = navItems.distinctBy { it.url },
            acquisitions = acquisitions.distinctBy { it.url },
            nextPageUrl = nextLink,
            searchTemplateUrl = searchLink,
        )
    }

    private fun parseJsonFeed(json: String, baseUrl: String): OpdsFeed {
        val root = JSONObject(json)
        val metadata = root.optJSONObject("metadata")
        val feedTitle = metadata?.optString("title").orEmpty().ifBlank { "Katalog" }

        val links = root.optJSONArray("links") ?: JSONArray()
        var nextLink: String? = null
        var searchLink: String? = null
        val navItems = mutableListOf<OpdsNavigationItem>()

        for (i in 0 until links.length()) {
            val link = links.getJSONObject(i)
            val rel = link.optString("rel")
            val href = resolve(baseUrl, link.optString("href")) ?: continue
            val title = link.optString("title")
            when (rel) {
                "next" -> nextLink = href
                "search" -> searchLink = href
                "self" -> Unit
                else -> if (title.isNotBlank()) navItems.add(OpdsNavigationItem(title, href))
            }
        }

        // Sub-navigation groups in OPDS 2 (navigation, groups)
        val navigation = root.optJSONArray("navigation")
        if (navigation != null) {
            for (i in 0 until navigation.length()) {
                val navObj = navigation.getJSONObject(i)
                val title = navObj.optString("title").ifBlank { navObj.optJSONObject("metadata")?.optString("title").orEmpty() }
                val href = resolve(baseUrl, navObj.optString("href")) ?: continue
                if (title.isNotBlank()) {
                    navItems.add(OpdsNavigationItem(title, href))
                }
            }
        }

        val publications = root.optJSONArray("publications")
            ?: root.optJSONObject("publications")?.optJSONArray("items")
            ?: JSONArray()

        val acquisitions = mutableListOf<OpdsAcquisition>()
        for (i in 0 until publications.length()) {
            val publication = publications.getJSONObject(i)
            val pubMeta = publication.optJSONObject("metadata") ?: JSONObject()
            val title = pubMeta.optString("title").ifBlank { "İsimsiz yayın" }
            val author = pubMeta.opt("author")?.let(::jsonName)
            val summary = pubMeta.optString("description")

            val pubLinks = publication.optJSONArray("links") ?: continue
            val images = publication.optJSONArray("images")
            val coverHref = if (images != null && images.length() > 0) {
                resolve(baseUrl, images.getJSONObject(0).optString("href"))
            } else null

            for (linkIndex in 0 until pubLinks.length()) {
                val link = pubLinks.getJSONObject(linkIndex)
                val type = link.optString("type")
                val rel = link.opt("rel")?.toString().orEmpty()
                if (!supportedMime(type) || ("acquisition" !in rel && rel.isNotBlank())) continue
                val href = resolve(baseUrl, link.optString("href")) ?: continue
                acquisitions.add(
                    OpdsAcquisition(
                        title = title,
                        author = author,
                        url = href,
                        mimeType = type,
                        summary = summary,
                        coverUrl = coverHref,
                    )
                )
                break
            }
        }

        return OpdsFeed(
            title = feedTitle,
            navigationItems = navItems.distinctBy { it.url },
            acquisitions = acquisitions.distinctBy { it.url },
            nextPageUrl = nextLink,
            searchTemplateUrl = searchLink,
        )
    }

    private fun jsonName(value: Any): String? = when (value) {
        is String -> value
        is JSONObject -> value.optString("name").ifBlank { null }
        is JSONArray -> if (value.length() > 0) jsonName(value.get(0)) else null
        else -> null
    }

    private fun get(url: String, credentials: OpdsCredentials?): Pair<String, String> {
        val reqBuilder = Request.Builder().url(url)
        if (credentials != null && credentials.username.isNotBlank()) {
            reqBuilder.header("Authorization", Credentials.basic(credentials.username, credentials.password))
        }
        client.newCall(reqBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("OPDS katalog hatası: HTTP ${response.code}")
            val body = response.body ?: throw IOException("OPDS boş katalog yanıtı")
            return body.string() to response.header("Content-Type").orEmpty()
        }
    }

    private fun isNavRel(rel: String, type: String): Boolean {
        if (rel in setOf("subsection", "start", "collection", "shelf", "sort-new", "sort-popular")) return true
        if (type.contains("atom+xml") || type.contains("opds-catalog") || type.contains("opds+json")) return true
        return false
    }

    private fun supportedMime(type: String) =
        listOf("epub", "pdf", "zip", "cbz", "rar", "cbr", "octet-stream").any(type::contains)

    private fun requireHttp(url: String) =
        require(url.startsWith("https://") || url.startsWith("http://")) { "Yalnız HTTP(S) OPDS adresleri desteklenir" }

    private fun resolve(base: String, href: String): String? = runCatching {
        URI(base).resolve(href).toString().takeIf { it.startsWith("http") }
    }.getOrNull()
}
