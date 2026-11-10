package com.kitsugi.animelist.ui.screens.manga

import java.io.File
import java.util.zip.ZipFile

internal const val MAX_FB2_ARCHIVE_BYTES = 50L * 1024 * 1024
internal const val MAX_FB2_BYTES = 5 * 1024 * 1024

/** Read exactly one FB2 from a ZIP without extracting any filesystem paths. */
internal fun readFictionBookArchive(file: File): ByteArray {
    require(file.length() <= MAX_FB2_ARCHIVE_BYTES) { "FictionBook archive too large" }
    ZipFile(file).use { zip ->
        require(zip.size() <= 10_000) { "Too many archive entries" }
        val candidates = zip.entries().asSequence().filter { entry ->
            val name = entry.name.replace('\\', '/')
            val segments = name.split('/')
            !entry.isDirectory && name.endsWith(".fb2", ignoreCase = true) &&
                !name.startsWith('/') && segments.none { it == ".." || it.startsWith('.') } &&
                !name.startsWith("__MACOSX/")
        }.take(2).toList()
        require(candidates.size == 1) { "Archive must contain exactly one FB2 file" }
        val entry = candidates.single()
        require(entry.size <= MAX_FB2_BYTES) { "FictionBook too large" }
        return zip.getInputStream(entry).use { readBoundedNovel(it, MAX_FB2_BYTES) }
    }
}
