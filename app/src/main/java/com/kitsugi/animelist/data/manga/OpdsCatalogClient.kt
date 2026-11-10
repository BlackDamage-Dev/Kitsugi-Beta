package com.kitsugi.animelist.data.manga

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.io.IOException
import java.net.URI

data class OpdsAcquisition(val title: String, val author: String?, val url: String, val mimeType: String)

/** OPDS 1 Atom and OPDS 2 publication acquisition reader, without pretending authenticated catalogs work. */
class OpdsCatalogClient(private val client: OkHttpClient = OkHttpClient()) {
    suspend fun fetch(url: String): List<OpdsAcquisition> = withContext(Dispatchers.IO) {
        requireHttp(url)
        val (body, contentType) = get(url)
        val isJson = "json" in contentType || body.trimStart().startsWith('{')
        if (isJson) parseJson(body, url) else parseAtom(body, url)
    }

    suspend fun download(acquisition: OpdsAcquisition, directory: File): File = withContext(Dispatchers.IO) {
        requireHttp(acquisition.url)
        val extension = when {
            "epub" in acquisition.mimeType -> "epub"
            "pdf" in acquisition.mimeType -> "pdf"
            "rar" in acquisition.mimeType || "cbr" in acquisition.mimeType -> "cbr"
            "zip" in acquisition.mimeType || "cbz" in acquisition.mimeType -> "cbz"
            else -> URI(acquisition.url).path?.substringAfterLast('.', "")?.lowercase()
        }
        require(extension in setOf("epub", "pdf", "cbz", "cbr", "zip", "rar")) { "Bu yayın desteklenen bir dosya bağlantısı sunmuyor" }
        directory.mkdirs()
        val temporary = File(directory, "opds-${java.util.UUID.nameUUIDFromBytes(acquisition.url.toByteArray())}.tmp")
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val request = Request.Builder().url(acquisition.url).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("OPDS indirme hatası: HTTP ${response.code}")
                val body = response.body ?: throw IOException("OPDS boş dosya yanıtı")
                response.header("Content-Length")?.toLongOrNull()?.let {
                    require(it in 0..MangaReaderStorage.MAX_IMPORT_BYTES) { "Dosya 512 MB sınırını aşıyor" }
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

    private fun parseAtom(xml: String, baseUrl: String): List<OpdsAcquisition> {
        val document = Jsoup.parse(xml, baseUrl, Parser.xmlParser())
        return document.select("feed > entry").mapNotNull { entry ->
            val link = entry.select("link").firstOrNull { link ->
                val rel = link.attr("rel")
                val type = link.attr("type")
                (rel.contains("acquisition") || rel == "alternate") && supportedMime(type)
            } ?: return@mapNotNull null
            val href = resolve(baseUrl, link.attr("href")) ?: return@mapNotNull null
            OpdsAcquisition(entry.selectFirst("title")?.text().orEmpty().ifBlank { "İsimsiz yayın" },
                entry.selectFirst("author > name")?.text(), href, link.attr("type"))
        }
    }

    private fun parseJson(json: String, baseUrl: String): List<OpdsAcquisition> {
        val root = JSONObject(json)
        val publications = root.optJSONArray("publications") ?: root.optJSONObject("publications")?.optJSONArray("items") ?: JSONArray()
        return buildList {
            for (index in 0 until publications.length()) {
                val publication = publications.getJSONObject(index)
                val metadata = publication.optJSONObject("metadata") ?: JSONObject()
                val title = metadata.optString("title").ifBlank { "İsimsiz yayın" }
                val author = metadata.opt("author")?.let(::jsonName)
                val links = publication.optJSONArray("links") ?: continue
                for (linkIndex in 0 until links.length()) {
                    val link = links.getJSONObject(linkIndex)
                    val type = link.optString("type")
                    val rel = link.opt("rel")?.toString().orEmpty()
                    if (!supportedMime(type) || ("acquisition" !in rel && rel.isNotBlank())) continue
                    val href = resolve(baseUrl, link.optString("href")) ?: continue
                    add(OpdsAcquisition(title, author, href, type))
                    break
                }
            }
        }
    }

    private fun jsonName(value: Any): String? = when (value) {
        is String -> value
        is JSONObject -> value.optString("name").ifBlank { null }
        is JSONArray -> if (value.length() > 0) jsonName(value.get(0)) else null
        else -> null
    }

    private fun get(url: String): Pair<String, String> {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("OPDS katalog hatası: HTTP ${response.code}")
            val body = response.body ?: throw IOException("OPDS boş katalog yanıtı")
            return body.string() to response.header("Content-Type").orEmpty()
        }
    }

    private fun supportedMime(type: String) = listOf("epub", "pdf", "zip", "cbz", "rar", "cbr", "octet-stream").any(type::contains)
    private fun requireHttp(url: String) = require(url.startsWith("https://") || url.startsWith("http://")) { "Yalnız HTTP(S) OPDS adresleri desteklenir" }
    private fun resolve(base: String, href: String): String? = runCatching { URI(base).resolve(href).toString().takeIf { it.startsWith("http") } }.getOrNull()
}
