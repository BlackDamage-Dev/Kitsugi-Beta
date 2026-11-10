package com.kitsugi.animelist.data.manga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import org.junit.Assert.*
import org.junit.Test

class MihonFiltersTest {
    private class Translation : Filter.Select<String>("Çeviri Durumu", arrayOf("Hepsi", "Tamamlanan", "Devam Eden", "Bırakılan", "Olmayan"))
    private class Content : Filter.Select<String>("İçerik", arrayOf("Hepsi", "Manga", "Webtoon", "Çizgi Roman"))

    @Test fun positionsStayWithExtensionRatherThanBeingGuessedAsServerCodes() {
        val translation = Translation()
        val content = Content()
        val filters = FilterList(Filter.Header("Başlık"), translation, content)
        val descriptions = filters.selectFilterDescriptions()
        assertEquals(listOf(1, 2), descriptions.map { it.index })
        filters.applySelectFilterPositions(mapOf(1 to 2, 2 to 3, 9 to 0))
        assertEquals("Devam Eden", translation.values[translation.state])
        assertEquals("Çizgi Roman", content.values[content.state])
        filters.applySelectFilterPositions(mapOf(1 to 99))
        assertEquals(2, translation.state)
    }
}
