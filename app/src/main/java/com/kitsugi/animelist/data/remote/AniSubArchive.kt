package com.kitsugi.animelist.data.remote

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.zip.ZipInputStream
import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry

/** Read untrusted subtitle archives without writing paths or links from the archive. */
internal object AniSubArchive {
    private const val MAX_ENTRY_BYTES = 5 * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 30 * 1024 * 1024
    private const val MAX_ENTRIES = 200
    private const val MAX_FILES = 100
    private val extensions = setOf("ass", "ssa", "srt", "vtt")

    private class Collector(private val dir: File) {
        val files = mutableListOf<File>()
        private var entryCount = 0
        private var totalBytes = 0
        fun visit() { if (++entryCount > MAX_ENTRIES) throw IOException("Too many archive entries") }
        fun accepts(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in extensions
        fun account(count: Int) {
            totalBytes += count
            if (totalBytes > MAX_TOTAL_BYTES) throw IOException("Archive exceeds total size limit")
        }
        fun save(name: String, data: ByteArray) {
            if (files.size >= MAX_FILES || data.size > MAX_ENTRY_BYTES) {
                throw IOException("Subtitle archive exceeds limits")
            }
            val base = File(name.replace('\\', '/')).name
            if (base.isBlank() || base == "." || base == "..") throw IOException("Invalid archive entry name")
            val file = File(dir, base)
            if (file.exists()) throw IOException("Duplicate subtitle filename")
            file.writeBytes(data)
            files.add(file)
        }
    }

    private fun InputStream.readEntry(): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) return out.toByteArray()
            if (out.size() + count > MAX_ENTRY_BYTES) throw IOException("Subtitle entry too large")
            out.write(buffer, 0, count)
        }
    }

    fun extractZip(bytes: ByteArray, dir: File): List<File> {
        val collector = Collector(dir)
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                collector.visit()
                if (!entry.isDirectory) {
                    val data = zip.readEntry() // Bound skipped entries too; closeEntry otherwise drains an unbounded zip bomb.
                    collector.account(data.size)
                    if (collector.accepts(entry.name)) collector.save(entry.name, data)
                }
                zip.closeEntry()
            }
        }
        return collector.files
    }

    fun extractRar(bytes: ByteArray, dir: File): List<File> {
        val collector = Collector(dir)
        // libarchive-android 1.1.6 retains a JNI local reference for heap buffers
        // and later uses it in free(), which aborts Android with CheckJNI enabled.
        // Direct memory avoids that dangling reference; keep it alive until free.
        val archiveBuffer = ByteBuffer.allocateDirect(bytes.size).put(bytes).apply { flip() }
        val archive = Archive.readNew()
        try {
            Archive.readSupportFilterAll(archive)
            Archive.readSupportFormatRar(archive)
            Archive.readSupportFormatRar5(archive)
            Archive.readOpenMemory(archive, archiveBuffer)
            while (true) {
                val entry = Archive.readNextHeader(archive)
                if (entry == 0L) break
                collector.visit()
                val name = ArchiveEntry.pathnameUtf8(entry)
                    ?: ArchiveEntry.pathname(entry)?.toString(Charsets.UTF_8) ?: ""
                if (ArchiveEntry.sizeIsSet(entry) && ArchiveEntry.size(entry) > MAX_ENTRY_BYTES) {
                    throw IOException("Archive entry too large")
                }
                if (ArchiveEntry.filetype(entry) != ArchiveEntry.AE_IFREG) {
                    Archive.readDataSkip(archive)
                    continue
                }
                if (ArchiveEntry.isEncrypted(entry)) throw IOException("Encrypted subtitle archive")
                val out = ByteArrayOutputStream()
                val buffer = ByteBuffer.allocate(8192)
                while (true) {
                    buffer.clear()
                    Archive.readData(archive, buffer)
                    val count = buffer.position()
                    if (count == 0) break
                    if (out.size() + count > MAX_ENTRY_BYTES) throw IOException("Subtitle entry too large")
                    out.write(buffer.array(), 0, count)
                }
                collector.account(out.size())
                if (collector.accepts(name)) collector.save(name, out.toByteArray())
            }
        } finally {
            try {
                Archive.free(archive)
            } finally {
                archiveBuffer.clear() // Keep the direct buffer strongly reachable until native cleanup.
            }
        }
        return collector.files
    }
}
