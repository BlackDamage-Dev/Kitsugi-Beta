package com.kitsugi.animelist.ui.screens.manga

import com.github.junrar.Archive
import com.github.junrar.ArchiveOptions
import com.github.junrar.rarfile.rar5.Rar5MainHeader
import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.manga.MangaPage
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.MangaSourceResult
import java.io.File
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** RAR reading never extracts an archive path. Single-volume, non-solid and unencrypted only. */
internal object LocalRarComic {
    private val options = ArchiveOptions.builder().maxDictionarySize(32L * 1024 * 1024).build()
    private val extensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    private val extraction = Semaphore(1)

    fun list(file: File): List<LocalComicEntry> = Archive(file, options).use { archive ->
        require(!archive.hasBrokenHeaders() && !archive.isPasswordProtected) { "Invalid or encrypted RAR" }
        val main = archive.mainHeader
        require(main?.isMultiVolume != true && main?.isSolid != true) { "Unsupported multi-volume or solid RAR" }
        require(archive.headers.filterIsInstance<Rar5MainHeader>().none { it.isVolume || it.isSolid }) { "Unsupported multi-volume or solid RAR" }
        val headers = archive.fileHeaders
        require(headers.size <= 10_000 && headers.none { it.isSplitBefore || it.isSplitAfter || it.isSolid }) { "Unsupported multi-volume or solid RAR" }
        val entries = ArrayList<LocalComicEntry>()
        val names = HashSet<String>()
        for (header in headers) {
            val name = header.fileName.replace('\\', '/')
            val parts = name.split('/')
            if (header.isDirectory || name.length > 2048 || name.startsWith('/') ||
                parts.any { it.isEmpty() || it == "." || it == ".." || it.startsWith('.') } ||
                parts.firstOrNull() == "__MACOSX" ||
                name.substringAfterLast('.', "").lowercase(Locale.ROOT) !in extensions) continue
            require(!header.isUnpSizeUnknown && header.fullUnpackSize in 0..LocalComicArchive.MAX_PAGE_BYTES) { "RAR image too large" }
            require(names.add(name)) { "Duplicate RAR entry" }
            entries.add(LocalComicEntry(name, header.fileCRC.toLong() and 0xffffffffL, header.fullUnpackSize))
        }
        entries.sortedWith { a, b -> naturalComicCompare(a.name, b.name) }
    }

    suspend fun open(file: File, entry: LocalComicEntry, cacheDir: File): InputStream {
        val caller = currentCoroutineContext()
        return withContext(Dispatchers.IO) {
        extraction.withPermit {
            val context = currentCoroutineContext()
            val outputFile = File.createTempFile("reader-rar-page-", ".img", cacheDir)
            try {
                Archive(file, options).use { archive ->
                    val header = archive.fileHeaders.singleOrNull { it.fileName.replace('\\', '/') == entry.name }
                        ?: throw IOException("Missing RAR image")
                    if (header.fullUnpackSize != entry.size || (header.fileCRC.toLong() and 0xffffffffL) != entry.crc) throw IOException("RAR image changed")
                    outputFile.outputStream().use { raw ->
                        val bounded = object : FilterOutputStream(raw) {
                            var count = 0L
                            override fun write(b: Int) { context.ensureActive(); if (++count > LocalComicArchive.MAX_PAGE_BYTES) throw IOException("RAR image too large"); out.write(b) }
                            override fun write(b: ByteArray, off: Int, len: Int) {
                                context.ensureActive(); count += len
                                if (count > LocalComicArchive.MAX_PAGE_BYTES) throw IOException("RAR image too large")
                                out.write(b, off, len)
                            }
                        }
                        archive.extractFile(header, bounded)
                    }
                }
                context.ensureActive()
                if (outputFile.length() != entry.size) throw IOException("Incomplete RAR image")
                val stream = outputFile.inputStream()
                val cleanup = caller[kotlinx.coroutines.Job]?.invokeOnCompletion { outputFile.delete() }
                object : FilterInputStream(stream) {
                    override fun close() { try { super.close() } finally { cleanup?.dispose(); outputFile.delete() } }
                }
            } catch (e: Throwable) {
                outputFile.delete()
                context.ensureActive()
                throw e
            }
        }
        }
    }
}

internal class LocalRarComicSource(private val file: File, private val cacheDir: File,
    private val entries: List<LocalComicEntry>, private val identity: String,
    private val chapter: MangaChapter, private val details: MangaDetails) : MangaSource {
    override val name = "Local comic"
    override val baseUrl = "local-comic://reader"
    override val pkgName = "com.kitsugi.localcomic"
    override val lang = "all"
    private val byName = entries.associateBy { it.name }
    override suspend fun fetchPopularManga(page: Int) = MangaSourceResult(emptyList(), false)
    override suspend fun fetchSearchManga(page: Int, query: String) = MangaSourceResult(emptyList(), false)
    override suspend fun fetchMangaDetails(mangaUrl: String) = details
    override suspend fun fetchChapterList(mangaUrl: String) = listOf(chapter)
    override suspend fun fetchPageList(chapter: MangaChapter): List<MangaPage> = entries.mapIndexed { index, entry ->
        val key = readerSeriesId(identity, entry.name, entry.crc.toString(), entry.size.toString())
        MangaPage(index = index, url = entry.name, imageUrl = "local-comic://page/$key")
    }
    override suspend fun getImage(page: MangaPage): InputStream = LocalRarComic.open(file, byName[page.url] ?: throw IOException("Unknown page"), cacheDir)
}
