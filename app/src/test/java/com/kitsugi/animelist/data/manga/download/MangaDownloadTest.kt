package com.kitsugi.animelist.data.manga.download

import com.kitsugi.animelist.data.manga.MangaChapter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class MangaDownloadTest {
    private fun download(total: Int = 12) = MangaDownload(
        id = "0123456789abcdef", sourceKey = "pkg.manga", sourceName = "Kaynak", sourceLang = "tr",
        mangaUrl = "/series/a", mangaTitle = "Chainsaw Man", chapterUrl = "/chapter/12.5", chapterName = "Bölüm 12.5",
        chapterNumber = 12.5f, relativePath = "Pictures/Kitsugi/Manga/test/", totalPages = total
    )

    @Test fun pageNamesContainMangaChapterAndZeroPaddedSourceOrder() {
        val d = download(1200)
        assertEquals("Chainsaw Man_Bölüm_12.5_Sayfa_0001.png", MangaDownloadNaming.pageName(d, 0, "png"))
        val names = (0 until d.totalPages).map { MangaDownloadNaming.pageName(d, it, "jpg") }
        assertEquals(names, names.sorted())
        assertEquals("Chainsaw Man_Bölüm_12.5_Sayfa_1200.jpg", names.last())
    }

    @Test fun fractionalLabelsDoNotExposeFloatRoundingArtifacts() {
        assertEquals("Bölüm_12.1", MangaDownloadNaming.chapterLabel("Test", "Bölüm 12.1", 12.1f))
        assertEquals("Bölüm_42.5", MangaDownloadNaming.chapterLabel("Test", "Bölüm 42.5", -1f))
        assertEquals("Bölüm_0", MangaDownloadNaming.chapterLabel("Test", "Önsöz", 0f))
    }

    @Test fun foldersAreSafeUnicodeAndCollisionResistant() {
        val malicious = "../../Manga: A/B\\C\u0000?"
        val segment = MangaDownloadNaming.safeSegment(malicious)
        assertFalse(segment.contains('/'))
        assertFalse(segment.contains('\\'))
        assertFalse(segment.contains('\u0000'))
        assertNotEquals("..", segment)
        assertTrue(MangaDownloadNaming.safeSegment("漢😀".repeat(100)).toByteArray(Charsets.UTF_8).size <= 80)
        assertEquals("Manga", MangaDownloadNaming.safeSegment(".."))
        assertEquals("Türkçe 漫画", MangaDownloadNaming.safeSegment("Türkçe 漫画"))
        val c = MangaChapter("/chapter/1", "Bölüm 1", 1f)
        val a = MangaDownloadNaming.relativePath("pkg.a", "Kaynak", "A/B", "/manga/1", c)
        val b = MangaDownloadNaming.relativePath("pkg.a", "Kaynak", "A:B", "/manga/2", c)
        assertNotEquals(a, b)
        assertTrue(a.startsWith("Pictures/Kitsugi/Manga/"))
        assertTrue(a.endsWith('/'))
    }

    @Test fun chapterFoldersAlsoSortNumericallyInFileManagers() {
        val folders = listOf(1f, 2f, 2.5f, 2.55f, 10f).map { number ->
            MangaDownloadNaming.relativePath("pkg", "Source", "Title", "/manga",
                MangaChapter("/chapter/$number", "Bölüm $number", number))
        }
        assertEquals(folders, folders.sorted())
    }

    @Test fun duplicateRequestsHaveSameIdButOtherSourcesAndChaptersDoNot() {
        val a = MangaDownloadNaming.id("pkg", "Name", "tr", "/manga", "/1")
        assertEquals(a, MangaDownloadNaming.id("pkg", "Name", "tr", "/manga", "/1"))
        assertNotEquals(a, MangaDownloadNaming.id("pkg", "Name", "en", "/manga", "/1"))
        assertNotEquals(a, MangaDownloadNaming.id("pkg", "Name", "tr", "/manga", "/2"))
    }

    @Test fun incompleteDuplicateOrGappedManifestsAreNeverReadyOffline() {
        val d = download(2)
        val p0 = MangaDownloadedPage(0, "content://media/1", "1.png", "image/png", 100, true)
        val p1 = p0.copy(index = 1, uri = "content://media/2")
        assertFalse(download(0).hasAllPages)
        assertFalse(d.copy(pages = listOf(p0)).hasAllPages)
        assertFalse(d.copy(pages = listOf(p0, p0)).hasAllPages)
        assertFalse(d.copy(pages = listOf(p0, p1.copy(index = 2))).hasAllPages)
        assertFalse(d.copy(pages = listOf(p0, p1.copy(complete = false))).hasAllPages)
        assertTrue(d.copy(pages = listOf(p1, p0)).hasAllPages)
    }

    @Test fun manifestRoundTripsPendingAndCompletePagesForResumption() {
        val d = download(2).copy(workId = "work-123", status = MangaDownloadStatus.Paused, pages = listOf(
            MangaDownloadedPage(0, "content://media/1", "0001.png", "image/png", 2048, true),
            MangaDownloadedPage(1, "content://media/2", "0002.png", "image/png")
        ))
        val json = Json { encodeDefaults = true }
        assertEquals(d, json.decodeFromString<MangaDownload>(json.encodeToString(d)))
        assertEquals(2048L, d.sizeBytes)
        assertEquals(1, d.completedPages)
    }
}
