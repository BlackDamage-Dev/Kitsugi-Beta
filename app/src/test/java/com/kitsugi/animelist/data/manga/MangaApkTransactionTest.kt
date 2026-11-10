package com.kitsugi.animelist.data.manga

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class MangaApkTransactionTest {
    private fun replace(target: File, content: String) {
        val candidate = File.createTempFile("candidate", ".apk", target.parentFile)
        candidate.writeText(content)
        if (target.exists()) target.setWritable(true)
        assertTrue(candidate.renameTo(target))
    }

    @Test fun failedCandidateAndExceptionsRestoreExistingApk() {
        val dir = Files.createTempDirectory("manga-apk-test").toFile()
        try {
            val target = File(dir, "source.apk").apply { writeText("previous") }
            assertFalse(withMangaApkRollback(target) { replace(target, "candidate"); false })
            assertEquals("previous", target.readText())
            val error = runCatching {
                withMangaApkRollback(target) { replace(target, "broken"); error("load failed") }
            }.exceptionOrNull()
            assertTrue(error is IllegalStateException)
            assertEquals("previous", target.readText())
            assertEquals(0, File(dir, ".staging").listFiles()!!.size)
        } finally { dir.deleteRecursively() }
    }

    @Test fun successfulCandidateCommitsAndFailedFirstInstallIsRemoved() {
        val dir = Files.createTempDirectory("manga-apk-test").toFile()
        try {
            val target = File(dir, "source.apk")
            assertFalse(withMangaApkRollback(target) { replace(target, "untrusted"); false })
            assertFalse(target.exists())
            target.writeText("previous")
            assertTrue(withMangaApkRollback(target) { replace(target, "candidate"); true })
            assertEquals("candidate", target.readText())
            assertEquals(0, File(dir, ".staging").listFiles()!!.size)
        } finally { dir.deleteRecursively() }
    }
}
