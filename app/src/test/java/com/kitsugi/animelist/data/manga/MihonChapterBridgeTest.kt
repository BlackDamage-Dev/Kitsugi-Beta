package com.kitsugi.animelist.data.manga

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class MihonChapterBridgeTest {
    @Test fun preservesMemoRequiredByMangittoAndOtherKeiSourcePlugins() {
        val chapter = MangaChapter("chapter-slug", "Bölüm 12", 12f, "Çeviri", 12345, "manga-slug").apply {
            extensionMemo = JsonObject(mapOf("mangaSlug" to JsonPrimitive("chainsaw-man")))
        }
        val sourceChapter = chapter.toExtensionChapter()
        assertEquals(chapter.url, sourceChapter.url)
        assertEquals(chapter.name, sourceChapter.name)
        assertEquals(chapter.scanlator, sourceChapter.scanlator)
        assertEquals(chapter.uploadDate, sourceChapter.date_upload)
        assertEquals(12f, sourceChapter.chapter_number)
        assertEquals(chapter.extensionMemo, sourceChapter.memo)
    }
}
