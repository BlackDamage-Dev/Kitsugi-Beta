package com.kitsugi.animelist.data.manga.loader

import android.content.Context
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import com.kitsugi.animelist.data.manga.MangaPage
import com.kitsugi.animelist.data.manga.MangaPageStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/** Sequential CBZ/ZIP and CBR/RAR reader. Image entries are sorted by page number, not raw strings. */
class ArchivePageLoader(private val context: Context, private val archiveFile: File) {
    private val isZip: Boolean get() = archiveFile.extension.equals("zip", true) || archiveFile.extension.equals("cbz", true)
    private val isRar: Boolean get() = archiveFile.extension.equals("rar", true) || archiveFile.extension.equals("cbr", true)

    fun isSupported(): Boolean = archiveFile.exists() && (isZip || isRar)

    fun loadPageList(): List<String> = entries()

    fun loadPage(entryName: String): InputStream? = when {
        isZip -> runCatching {
            ZipFile(archiveFile).use { zip ->
                val entry = zip.getEntry(entryName) ?: return@use null
                zip.getInputStream(entry).use { it.readBytes() }.let(::ByteArrayInputStream)
            }
        }.getOrNull()
        isRar -> runCatching {
            Archive(archiveFile).use { archive ->
                val header = archive.fileHeaders.firstOrNull { it.fileName == entryName } ?: return@use null
                archive.getInputStream(header).use { it.readBytes() }.let(::ByteArrayInputStream)
            }
        }.getOrNull()
        else -> null
    }

    suspend fun loadPages(chapterUrl: String): List<MangaPage> = withContext(Dispatchers.IO) {
        if (!isSupported()) return@withContext emptyList()
        entries().mapIndexed { index, name ->
            MangaPage(index = index, url = "$chapterUrl#archive-$index", imageUrl = "").apply {
                archivePath = name
                status = MangaPageStatus.Queue
            }
        }
    }

    suspend fun openPageStream(entryName: String): InputStream? = withContext(Dispatchers.IO) {
        when {
            isZip -> ZipFile(archiveFile).use { zip ->
                val entry = zip.getEntry(entryName) ?: return@use null
                zip.getInputStream(entry).use { it.readBytes() }.let(::ByteArrayInputStream)
            }
            isRar -> Archive(archiveFile).use { archive ->
                val header = archive.fileHeaders.firstOrNull { it.fileName == entryName } ?: return@use null
                archive.getInputStream(header).use { it.readBytes() }.let(::ByteArrayInputStream)
            }
            else -> null
        }
    }

    /** Streams one entry into a stable private cache file for Coil and subsampling decoders. */
    suspend fun extractPageToCache(entryName: String): File? = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "manga-archive-pages").apply { mkdirs() }
        val key = (archiveFile.absolutePath + archiveFile.length() + archiveFile.lastModified() + entryName)
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val extension = entryName.substringAfterLast('.', "img").take(5).filter { it.isLetterOrDigit() }.ifBlank { "img" }
        val output = File(directory, "$digest.$extension")
        if (output.isFile && output.length() > 0L) {
            com.kitsugi.animelist.data.manga.MangaReaderStorage.pruneDerivedCache(directory, keep = output)
            return@withContext output
        }
        val temporary = File(directory, "$digest.tmp")
        openPageStream(entryName)?.use { input ->
            temporary.outputStream().buffered().use { input.copyTo(it) }
        } ?: return@withContext null
        if (!temporary.renameTo(output)) {
            if (output.exists()) temporary.delete()
            else return@withContext null
        }
        com.kitsugi.animelist.data.manga.MangaReaderStorage.pruneDerivedCache(directory, keep = output)
        output
    }

    private fun entries(): List<String> = when {
        isZip -> ZipFile(archiveFile).use { zip ->
            zip.entries().toList().filter { !it.isDirectory && isImage(it.name) }.map { it.name }
        }
        isRar -> Archive(archiveFile).use { archive ->
            archive.fileHeaders.filter { !it.isDirectory && isImage(it.fileName) }.map { it.fileName }
        }
        else -> emptyList()
    }.sortedWith(::naturalArchivePathCompare)

    private fun isImage(name: String): Boolean = name.substringAfterLast('.').lowercase() in setOf(
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "avif", "heif", "heic",
    )
}

/** Compares numeric runs numerically, so 2 < 10, while folder names remain stable and deterministic. */
fun naturalArchivePathCompare(left: String, right: String): Int {
    var a = 0
    var b = 0
    while (a < left.length && b < right.length) {
        if (left[a].isDigit() && right[b].isDigit()) {
            var aEnd = a
            var bEnd = b
            while (aEnd < left.length && left[aEnd].isDigit()) aEnd++
            while (bEnd < right.length && right[bEnd].isDigit()) bEnd++
            val aNumber = left.substring(a, aEnd).trimStart('0')
            val bNumber = right.substring(b, bEnd).trimStart('0')
            if (aNumber.length != bNumber.length) return aNumber.length.compareTo(bNumber.length)
            val numeric = aNumber.compareTo(bNumber)
            if (numeric != 0) return numeric
            val leading = (aEnd - a).compareTo(bEnd - b)
            if (leading != 0) return leading
            a = aEnd
            b = bEnd
        } else {
            val comparison = left[a].lowercaseChar().compareTo(right[b].lowercaseChar())
            if (comparison != 0) return comparison
            a++
            b++
        }
    }
    return (left.length - a).compareTo(right.length - b)
}
