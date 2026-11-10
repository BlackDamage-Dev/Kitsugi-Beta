package com.kitsugi.animelist.data.remote

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AniSubArchiveTest {
    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().use { out ->
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, data) -> zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry() }
        }
        out.toByteArray()
    }

    @Test fun extractsOnlySubtitlesWithoutTrustingPaths() {
        val dir = java.nio.file.Files.createTempDirectory("anisub-test-").toFile()
        try {
            val files = AniSubArchive.extractZip(zip("../../Episode 01.ass" to "[Script Info]".toByteArray(), "note.txt" to byteArrayOf(1)), dir)
            assertEquals(listOf("Episode 01.ass"), files.map(File::getName))
            assertEquals(dir.canonicalFile, files.single().canonicalFile.parentFile)
        } finally { dir.deleteRecursively() }
    }

    @Test fun refusesOversizedIgnoredEntriesToo() {
        val dir = java.nio.file.Files.createTempDirectory("anisub-test-").toFile()
        try {
            try {
                AniSubArchive.extractZip(zip("large.txt" to ByteArray(5 * 1024 * 1024 + 1)), dir)
                fail("oversized entry accepted")
            } catch (_: IOException) { }
        } finally { dir.deleteRecursively() }
    }

    @Test fun refusesDuplicateBasenames() {
        val dir = java.nio.file.Files.createTempDirectory("anisub-test-").toFile()
        try {
            try {
                AniSubArchive.extractZip(zip("a/ep1.srt" to byteArrayOf(1), "b/ep1.srt" to byteArrayOf(2)), dir)
                fail("duplicate basename accepted")
            } catch (_: IOException) { }
        } finally { dir.deleteRecursively() }
    }
}
