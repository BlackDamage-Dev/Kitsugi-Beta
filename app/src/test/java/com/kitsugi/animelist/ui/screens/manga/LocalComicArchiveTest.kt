package com.kitsugi.animelist.ui.screens.manga

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LocalComicArchiveTest {
    @Test fun listsImagesInNaturalOrderAndFiltersUnsafeOrHiddenPaths() {
        val file = File.createTempFile("comic", ".cbz")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                for (name in listOf("10.jpg", "2.jpg", "01.png", "1.png", "__MACOSX/x.jpg", "../evil.jpg", ".hidden/a.jpg", "note.txt")) {
                    zip.putNextEntry(ZipEntry(name)); zip.write(byteArrayOf(1, 2, 3)); zip.closeEntry()
                }
            }
            val entries = LocalComicArchive.list(file)
            assertEquals(listOf("1.png", "01.png", "2.jpg", "10.jpg"), entries.map { it.name })
            LocalComicArchive.open(file, entries.first()).use { assertArrayEquals(byteArrayOf(1, 2, 3), it.readBytes()) }
        } finally { file.delete() }
    }
    @Test fun comparatorHandlesLongNumbersAndCase() {
        assertTrue(naturalComicCompare("p9.jpg", "p10.jpg") < 0)
        assertTrue(naturalComicCompare("p" + "9".repeat(200), "p1" + "0".repeat(200)) < 0)
        assertTrue(naturalComicCompare("A1.jpg", "a1.jpg") < 0)
    }
}
