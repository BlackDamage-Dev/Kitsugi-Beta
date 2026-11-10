package com.kitsugi.animelist.data.manga.loader

import com.kitsugi.animelist.data.manga.MangaPage
import com.kitsugi.animelist.data.manga.MangaPageStatus
import com.kitsugi.animelist.data.manga.MangaTextParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

data class EpubMetadata(
    val title: String,
    val author: String? = null,
    val description: String? = null,
    val language: String? = null,
    val coverHref: String? = null,
)

data class EpubTocItem(
    val title: String,
    val href: String,
    val playOrder: Int = 0,
)

/**
 * EPUB 2/3 spine & document reader.
 * OPF order is authoritative; directory entry order is never used.
 * Supports metadata extraction, EPUB 2 NCX / EPUB 3 Navigation Document TOC,
 * safe path traversal resolution, inline images, and image-only EPUBs.
 */
class EpubPageLoader(
    private val file: File,
    private val cacheDir: File? = null,
) {
    suspend fun loadPages(chapterUrl: String): List<MangaPage> = withContext(Dispatchers.IO) {
        ZipFile(file).use { zip ->
            val opfInfo = readOpfDocument(zip)
                ?: throw java.io.IOException("EPUB container.xml içinde OPF bulunamadı")
            val (rootfile, opfDoc) = opfInfo
            val manifest = opfDoc.select("manifest > item").associateBy { it.attr("id") }
            val base = rootfile.substringBeforeLast('/', "")
            val spineItems = opfDoc.select("spine > itemref").mapNotNull { spine ->
                val item = manifest[spine.attr("idref")] ?: return@mapNotNull null
                resolveArchivePath(base, item.attr("href"), zip)
            }
            if (spineItems.isEmpty()) throw java.io.IOException("EPUB spine bölümü boş")

            val effectiveCacheDir = (cacheDir ?: File(System.getProperty("java.io.tmpdir"), "kitsugi-epub-cache")).apply { mkdirs() }
            val bookCacheDir = File(effectiveCacheDir, "epub-${file.name.hashCode()}").apply { mkdirs() }

            val rawPages = spineItems.mapIndexedNotNull { spineIndex, path ->
                val html = zip.read(path) ?: return@mapIndexedNotNull null
                val chapterBase = path.substringBeforeLast('/', "")
                val extractedImages = extractChapterImages(zip, chapterBase, html, bookCacheDir)

                val paragraphs = MangaTextParser.parse(html)
                if (paragraphs.isEmpty() && extractedImages.isNotEmpty()) {
                    // Image-only chapter: produce image page(s)
                    extractedImages.mapIndexed { imgIdx, imgFile ->
                        MangaPage(
                            index = spineIndex,
                            url = "$chapterUrl#epub-$spineIndex-$imgIdx",
                            imageUrl = "file://${imgFile.absolutePath}"
                        ).apply {
                            textParagraphs = null
                            status = MangaPageStatus.Ready
                        }
                    }
                } else if (paragraphs.isNotEmpty()) {
                    // Text chapter, possibly with inline images
                    val inlineParagraphs = if (extractedImages.isNotEmpty()) {
                        val imagesAsMarkup = extractedImages.map { "[img:file://${it.absolutePath}]" }
                        imagesAsMarkup + paragraphs
                    } else {
                        paragraphs
                    }
                    listOf(
                        MangaPage(
                            index = spineIndex,
                            url = "$chapterUrl#epub-$spineIndex",
                            imageUrl = "$chapterUrl#epub-$spineIndex"
                        ).apply {
                            textParagraphs = inlineParagraphs
                            status = MangaPageStatus.Ready
                        }
                    )
                } else {
                    null
                }
            }.flatten()

            if (rawPages.isEmpty()) throw java.io.IOException("EPUB belgesinde okunabilir içerik bulunamadı")

            rawPages.mapIndexed { index, page ->
                MangaPage(index = index, url = page.url, imageUrl = page.imageUrl).apply {
                    textParagraphs = page.textParagraphs
                    status = MangaPageStatus.Ready
                }
            }
        }
    }

    private fun extractChapterImages(zip: ZipFile, chapterBase: String, html: String, targetDir: File): List<File> {
        val doc = Jsoup.parse(html)
        val imageElements = doc.select("img, image")
        val extracted = mutableListOf<File>()
        for (element in imageElements) {
            val rawSrc = element.attr("src").ifBlank {
                element.attr("xlink:href").ifBlank { element.attr("href") }
            }
            if (rawSrc.isBlank()) continue
            val imageArchivePath = resolveArchivePath(chapterBase, rawSrc, zip) ?: continue
            val zipEntry = zip.getEntry(imageArchivePath) ?: continue

            val safeFileName = imageArchivePath.replace('/', '_').replace('\\', '_')
            val destFile = File(targetDir, safeFileName)
            if (!destFile.exists() || destFile.length() != zipEntry.size) {
                // Zip-slip security check: ensure destination is within targetDir
                if (!destFile.canonicalPath.startsWith(targetDir.canonicalPath)) continue
                zip.getInputStream(zipEntry).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            if (destFile.exists()) {
                extracted.add(destFile)
            }
        }
        return extracted
    }

    companion object {
        fun extractMetadata(file: File): EpubMetadata? = runCatching {
            ZipFile(file).use { zip ->
                val (_, opfDoc) = readOpfDocument(zip) ?: return@use null
                val title = opfDoc.select("metadata > dc\\:title, metadata > title").firstOrNull()?.text()
                    ?: file.nameWithoutExtension
                val author = opfDoc.select("metadata > dc\\:creator, metadata > creator").firstOrNull()?.text()
                val description = opfDoc.select("metadata > dc\\:description, metadata > description").firstOrNull()?.text()
                val language = opfDoc.select("metadata > dc\\:language, metadata > language").firstOrNull()?.text()

                val manifest = opfDoc.select("manifest > item").associateBy { it.attr("id") }
                val coverId = opfDoc.select("metadata > meta[name=cover]").firstOrNull()?.attr("content")
                val coverItem = coverId?.let { manifest[it] }
                    ?: opfDoc.select("manifest > item[properties*=cover-image]").firstOrNull()
                    ?: manifest.values.firstOrNull { it.attr("id").contains("cover", ignoreCase = true) && it.attr("media-type").startsWith("image/") }

                val coverHref = coverItem?.attr("href")
                EpubMetadata(
                    title = title,
                    author = author,
                    description = description,
                    language = language,
                    coverHref = coverHref,
                )
            }
        }.getOrNull()

        fun extractCover(file: File, targetDir: File): File? = runCatching {
            ZipFile(file).use { zip ->
                val (rootfile, opfDoc) = readOpfDocument(zip) ?: return@use null
                val base = rootfile.substringBeforeLast('/', "")
                val manifest = opfDoc.select("manifest > item").associateBy { it.attr("id") }
                val coverId = opfDoc.select("metadata > meta[name=cover]").firstOrNull()?.attr("content")
                val coverItem = coverId?.let { manifest[it] }
                    ?: opfDoc.select("manifest > item[properties*=cover-image]").firstOrNull()
                    ?: manifest.values.firstOrNull { it.attr("id").contains("cover", ignoreCase = true) && it.attr("media-type").startsWith("image/") }
                    ?: return@use null

                val archivePath = resolveArchivePath(base, coverItem.attr("href"), zip) ?: return@use null
                val entry = zip.getEntry(archivePath) ?: return@use null
                val coverFile = File(targetDir, "cover-${file.name.hashCode()}.${archivePath.substringAfterLast('.', "jpg")}")
                if (!coverFile.canonicalPath.startsWith(targetDir.canonicalPath)) return@use null
                if (!coverFile.exists() || coverFile.length() != entry.size) {
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(coverFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                coverFile.takeIf { it.exists() }
            }
        }.getOrNull()

        fun extractToc(file: File): List<EpubTocItem> = runCatching {
            ZipFile(file).use { zip ->
                val (rootfile, opfDoc) = readOpfDocument(zip) ?: return@use emptyList()
                val base = rootfile.substringBeforeLast('/', "")
                val manifest = opfDoc.select("manifest > item").associateBy { it.attr("id") }

                // 1. Try EPUB 3 Navigation Document
                val navItem = opfDoc.select("manifest > item[properties*=nav]").firstOrNull()
                    ?: manifest.values.firstOrNull { it.attr("href").contains("nav", ignoreCase = true) && it.attr("media-type").contains("xhtml") }
                if (navItem != null) {
                    val navPath = resolveArchivePath(base, navItem.attr("href"), zip)
                    if (navPath != null) {
                        val navContent = zip.read(navPath)
                        if (navContent != null) {
                            val navDoc = Jsoup.parse(navContent)
                            val navBase = navPath.substringBeforeLast('/', "")
                            val tocLinks = navDoc.select("nav[epub\\:type=toc] ol a, nav#toc ol a, nav ol a")
                            if (tocLinks.isNotEmpty()) {
                                return@use tocLinks.mapIndexed { idx, link ->
                                    val href = resolveArchivePath(navBase, link.attr("href"), zip) ?: link.attr("href")
                                    EpubTocItem(
                                        title = link.text().trim(),
                                        href = href,
                                        playOrder = idx + 1,
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Try EPUB 2 NCX Document
                val ncxItem = manifest["ncx"]
                    ?: manifest.values.firstOrNull { it.attr("media-type") == "application/x-dtbncx+xml" || it.attr("href").endsWith(".ncx", ignoreCase = true) }
                if (ncxItem != null) {
                    val ncxPath = resolveArchivePath(base, ncxItem.attr("href"), zip)
                    if (ncxPath != null) {
                        val ncxContent = zip.read(ncxPath)
                        if (ncxContent != null) {
                            val ncxDoc = Jsoup.parse(ncxContent, "", Parser.xmlParser())
                            val ncxBase = ncxPath.substringBeforeLast('/', "")
                            val navPoints = ncxDoc.select("navMap > navPoint")
                            if (navPoints.isNotEmpty()) {
                                return@use navPoints.mapIndexed { idx, point ->
                                    val title = point.selectFirst("navLabel > text")?.text()?.trim() ?: "Bölüm ${idx + 1}"
                                    val contentSrc = point.selectFirst("content")?.attr("src") ?: ""
                                    val href = resolveArchivePath(ncxBase, contentSrc, zip) ?: contentSrc
                                    val playOrder = point.attr("playOrder").toIntOrNull() ?: (idx + 1)
                                    EpubTocItem(
                                        title = title,
                                        href = href,
                                        playOrder = playOrder,
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Fallback: Spine items as chapters
                opfDoc.select("spine > itemref").mapIndexedNotNull { idx, spine ->
                    val item = manifest[spine.attr("idref")] ?: return@mapIndexedNotNull null
                    val path = resolveArchivePath(base, item.attr("href"), zip) ?: return@mapIndexedNotNull null
                    EpubTocItem(
                        title = "Bölüm ${idx + 1}",
                        href = path,
                        playOrder = idx + 1,
                    )
                }
            }
        }.getOrElse { emptyList() }

        private fun readOpfDocument(zip: ZipFile): Pair<String, Document>? {
            val containerEntry = zip.getEntry("META-INF/container.xml")
            val rootfilePath = if (containerEntry != null) {
                val containerXml = zip.read("META-INF/container.xml") ?: return null
                Jsoup.parse(containerXml, "", Parser.xmlParser())
                    .selectFirst("rootfile")?.attr("full-path")
            } else {
                null
            }

            val candidatePaths = listOfNotNull(
                rootfilePath,
                "OEBPS/content.opf",
                "OPS/package.opf",
                "content.opf",
                "package.opf",
            )

            for (path in candidatePaths) {
                val content = zip.read(path) ?: continue
                val doc = Jsoup.parse(content, "", Parser.xmlParser())
                if (doc.selectFirst("package") != null) {
                    return path to doc
                }
            }
            return null
        }

        private fun ZipFile.read(path: String): String? {
            val entry = getEntry(path) ?: return null
            return getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }

        fun resolveArchivePath(base: String, href: String, zip: ZipFile? = null): String? = runCatching {
            val cleanHref = href.substringBefore('#').substringBefore('?')
            val decoded = URLDecoder.decode(cleanHref, "UTF-8")
            if (decoded.isBlank()) return null

            fun normalize(parts: List<String>): String? {
                val result = mutableListOf<String>()
                for (part in parts) {
                    when (part) {
                        "", "." -> Unit
                        ".." -> if (result.isNotEmpty()) result.removeAt(result.lastIndex) else return null
                        else -> result += part
                    }
                }
                return result.joinToString("/")
            }

            // 1. Relative to base
            val baseCombined = if (decoded.startsWith('/')) decoded.drop(1) else if (base.isNotBlank()) "$base/$decoded" else decoded
            val candidate1 = normalize(baseCombined.split('/'))

            if (zip == null) return candidate1
            if (candidate1 != null && zip.getEntry(candidate1) != null) return candidate1

            // 2. Relative to root
            val rootCombined = if (decoded.startsWith('/')) decoded.drop(1) else decoded
            val candidate2 = normalize(rootCombined.split('/'))
            if (candidate2 != null && zip.getEntry(candidate2) != null) return candidate2

            // 3. Case-insensitive lookup fallback
            val targetLower = (candidate1 ?: candidate2)?.lowercase()
            if (targetLower != null) {
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entryName = entries.nextElement().name
                    if (entryName.lowercase() == targetLower) return entryName
                }
            }

            candidate1 ?: candidate2
        }.getOrNull()
    }
}
