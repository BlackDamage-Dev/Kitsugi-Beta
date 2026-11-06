package com.kitsugi.animelist.ui.screens.manga

import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipFile

internal data class LocalComicEntry(val name: String, val crc: Long, val size: Long)

/** ZIP/CBZ images only. Never extracts paths to the filesystem. */
internal object LocalComicArchive {
    const val MAX_ARCHIVE_BYTES = 500L * 1024 * 1024
    const val MAX_PAGE_BYTES = 50L * 1024 * 1024
    private const val MAX_ENTRIES = 10_000
    private val extensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

    fun list(file: File): List<LocalComicEntry> = ZipFile(file).use { zip ->
        require(zip.size() <= MAX_ENTRIES) { "Too many archive entries" }
        val entries = mutableListOf<LocalComicEntry>()
        val names = hashSetOf<String>()
        val iterator = zip.entries()
        while (iterator.hasMoreElements()) {
            val entry = iterator.nextElement()
            val name = entry.name
            val parts = name.split('/')
            if (entry.isDirectory || name.length > 2048 || '\\' in name || name.startsWith('/') ||
                parts.any { it.isEmpty() || it == "." || it == ".." || it.startsWith('.') } ||
                parts.firstOrNull() == "__MACOSX" ||
                name.substringAfterLast('.', "").lowercase(Locale.ROOT) !in extensions) continue
            // Never silently omit a page from a comic: an oversized entry invalidates the archive.
            require(entry.size <= MAX_PAGE_BYTES) { "Archive image is too large" }
            // Duplicate names make ZipFile.getEntry ambiguous: reject the whole archive.
            require(names.add(name)) { "Duplicate archive entry" }
            entries.add(LocalComicEntry(name, entry.crc, entry.size))
        }
        entries.sortedWith { a, b -> naturalComicCompare(a.name, b.name) }
    }

    fun open(file: File, entry: LocalComicEntry): InputStream {
        val zip = ZipFile(file)
        try {
            val item = zip.getEntry(entry.name) ?: throw IOException("Missing image")
            if (item.isDirectory || item.crc != entry.crc || item.size != entry.size || item.size > MAX_PAGE_BYTES)
                throw IOException("Archive entry changed or is too large")
            val input = zip.getInputStream(item)
            return object : FilterInputStream(input) {
                var count = 0L
                override fun read(): Int {
                    val value = super.read()
                    if (value >= 0 && ++count > MAX_PAGE_BYTES) throw IOException("Image is too large")
                    return value
                }
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    val amount = `in`.read(buffer, offset, length)
                    if (amount > 0) {
                        count += amount
                        if (count > MAX_PAGE_BYTES) throw IOException("Image is too large")
                    }
                    return amount
                }
                override fun close() { try { super.close() } finally { zip.close() } }
            }
        } catch (e: Exception) { zip.close(); throw e }
    }
}

/** Stable, case-insensitive natural ordering without parsing unbounded digit runs. */
internal fun naturalComicCompare(left: String, right: String): Int {
    val a = left.lowercase(Locale.ROOT)
    val b = right.lowercase(Locale.ROOT)
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        if (a[i] in '0'..'9' && b[j] in '0'..'9') {
            val startA = i; val startB = j
            while (i < a.length && a[i] in '0'..'9') i++
            while (j < b.length && b[j] in '0'..'9') j++
            var sigA = startA; var sigB = startB
            while (sigA < i - 1 && a[sigA] == '0') sigA++
            while (sigB < j - 1 && b[sigB] == '0') sigB++
            val lengthCompare = (i - sigA).compareTo(j - sigB)
            if (lengthCompare != 0) return lengthCompare
            for (offset in 0 until i - sigA) {
                val cmp = a[sigA + offset].compareTo(b[sigB + offset])
                if (cmp != 0) return cmp
            }
            // Equal numbers with zero padding sort deterministically.
            val paddingCompare = (i - startA).compareTo(j - startB)
            if (paddingCompare != 0) return paddingCompare
        } else {
            val cmp = a[i].compareTo(b[j])
            if (cmp != 0) return cmp
            i++; j++
        }
    }
    val lengthCompare = (a.length - i).compareTo(b.length - j)
    return if (lengthCompare != 0) lengthCompare else left.compareTo(right)
}
