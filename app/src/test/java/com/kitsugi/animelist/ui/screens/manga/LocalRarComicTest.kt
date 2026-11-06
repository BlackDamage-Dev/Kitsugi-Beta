package com.kitsugi.animelist.ui.screens.manga

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LocalRarComicTest {
    private fun archive(name: String, data: ByteArray): File {
        fun le(value: Long, bytes: Int) = ByteArray(bytes) { (value ushr (it * 8)).toByte() }
        fun crc(bytes: ByteArray) = CRC32().apply { update(bytes) }.value
        fun header(body: ByteArray) = le(crc(body), 2) + body
        val marker = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1a, 0x07, 0x00)
        val main = header(byteArrayOf(0x73, 0, 0, 13, 0, 0, 0, 0, 0, 0, 0))
        val path = name.toByteArray(Charsets.UTF_8)
        val body = byteArrayOf(0x74, 0, 0x80.toByte()) + le((32 + path.size).toLong(), 2) +
            le(data.size.toLong(), 4) + le(data.size.toLong(), 4) + byteArrayOf(3) + le(crc(data), 4) +
            le(0, 4) + byteArrayOf(20, 0x30) + le(path.size.toLong(), 2) + le(0x20, 4) + path
        val end = header(byteArrayOf(0x7b, 0, 0x40, 7, 0))
        val file = File.createTempFile("reader-rar-test", ".cbr")
        file.writeBytes(marker + main + header(body) + data + end)
        return file
    }
    @Test fun listsAndStreamsWithoutWritingArchivePaths() = runBlocking {
        val data = byteArrayOf(1, 2, 3, 4)
        val file = archive("pages\\01.png", data)
        val dir = File.createTempFile("reader-rar-dir", "").also { it.delete(); it.mkdir() }
        try {
            val entries = LocalRarComic.list(file)
            assertEquals(listOf("pages/01.png"), entries.map { it.name })
            LocalRarComic.open(file, entries.single(), dir).use { assertArrayEquals(data, it.readBytes()) }
            assertTrue(dir.listFiles().orEmpty().isEmpty())
        } finally { file.delete(); dir.deleteRecursively() }
    }
    @Test fun rejectsTraversalAndSkipsNonImages() {
        for (name in listOf("../evil.png", "folder/.hidden.png", "document.txt")) {
            val file = archive(name, byteArrayOf(1))
            try { assertTrue(LocalRarComic.list(file).isEmpty()) } finally { file.delete() }
        }
    }
    @Test fun rejectsBrokenArchives() {
        val file = archive("1.png", byteArrayOf(1))
        try { file.writeBytes(file.readBytes().copyOf(10)); assertThrows(Exception::class.java) { LocalRarComic.list(file) } }
        finally { file.delete() }
    }
}
