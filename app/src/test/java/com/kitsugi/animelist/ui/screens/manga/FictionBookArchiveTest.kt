package com.kitsugi.animelist.ui.screens.manga

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FictionBookArchiveTest {
    private fun archive(vararg entries: Pair<String, ByteArray>): File {
        val file = File.createTempFile("fb2-test-", ".zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, contents) -> zip.putNextEntry(ZipEntry(name)); zip.write(contents); zip.closeEntry() }
        }
        return file
    }
    @Test fun readsOnlySafeSingleBook() {
        val file = archive("folder/book.FB2" to "hello".toByteArray(), "../evil.fb2" to "bad".toByteArray(), "__MACOSX/hidden.fb2" to "bad".toByteArray())
        try { assertEquals("hello", readFictionBookArchive(file).toString(Charsets.UTF_8)) } finally { file.delete() }
    }
    @Test fun rejectsAmbiguousAndOversizedBooks() {
        val multiple = archive("a.fb2" to byteArrayOf(1), "b.fb2" to byteArrayOf(2))
        try { assertThrows(IllegalArgumentException::class.java) { readFictionBookArchive(multiple) } } finally { multiple.delete() }
        val large = archive("large.fb2" to ByteArray(MAX_FB2_BYTES + 1))
        try { assertThrows(IllegalArgumentException::class.java) { readFictionBookArchive(large) } } finally { large.delete() }
    }
}
