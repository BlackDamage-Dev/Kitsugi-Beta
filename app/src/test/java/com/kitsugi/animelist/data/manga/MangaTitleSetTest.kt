package com.kitsugi.animelist.data.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MangaTitleSet] testleri.
 *
 * Kullanıcı akışı: "Oku" → tarayıcı açılır → arama dili çipine dokunulur.
 * Çipin gerçekten yeni dilde arama yapabilmesi için ekran, serinin tüm adlarını
 * taşımak zorunda. Bu testler o sözleşmeyi doğrular.
 */
class MangaTitleSetTest {

    private val set = MangaTitleSet(
        localTitle = "Yerel Ad",
        englishTitle = "That Time I Got Reincarnated as a Slime",
        romajiTitle = "Tensei Shitara Slime Datta Ken",
        nativeTitle = "転生したらスライムだった件",
        synonyms = listOf("Slime Isekai")
    )

    @Test
    fun `english search starts with english title`() {
        val queries = set.queries(
            titleLanguage = "ENGLISH",
            searchLanguage = MangaSearchLanguage.English,
            followTitleLanguage = false
        )
        assertEquals(
            "That Time I Got Reincarnated as a Slime",
            queries.first()
        )
    }

    @Test
    fun `romaji search starts with romaji title`() {
        val queries = set.queries(
            titleLanguage = "ROMAJI",
            searchLanguage = MangaSearchLanguage.Romaji,
            followTitleLanguage = false
        )
        assertEquals("Tensei Shitara Slime Datta Ken", queries.first())
    }

    @Test
    fun `native search starts with native title`() {
        val queries = set.queries(
            titleLanguage = "NATIVE",
            searchLanguage = MangaSearchLanguage.Native,
            followTitleLanguage = false
        )
        assertEquals("転生したらスライムだった件", queries.first())
    }

    @Test
    fun `local search starts with localized title`() {
        val queries = set.queries(
            titleLanguage = "ENGLISH",
            searchLanguage = MangaSearchLanguage.Local,
            followTitleLanguage = false
        )
        assertEquals("Yerel Ad", queries.first())
    }

    @Test
    fun `switching language produces a different primary query`() {
        // Çipin işe yaradığını gösteren asıl test: aynı küme, farklı dil → farklı ilk sorgu.
        val english = set.queries("ENGLISH", MangaSearchLanguage.English, followTitleLanguage = false).first()
        val native = set.queries("ENGLISH", MangaSearchLanguage.Native, followTitleLanguage = false).first()
        assertTrue(english != native)
    }

    @Test
    fun `follow title language uses screen language`() {
        val queries = set.queries(
            titleLanguage = "NATIVE",
            searchLanguage = MangaSearchLanguage.English,
            followTitleLanguage = true
        )
        assertEquals("転生したらスライムだった件", queries.first())
    }

    @Test
    fun `synonyms are included as fallbacks`() {
        val queries = set.queries("ENGLISH", MangaSearchLanguage.English, followTitleLanguage = false)
        assertTrue(queries.contains("Slime Isekai"))
    }

    @Test
    fun `empty set is detected`() {
        assertTrue(MangaTitleSet().isEmpty)
        assertFalse(set.isEmpty)
        assertTrue(MangaTitleSet(localTitle = "  ").isEmpty)
    }

    @Test
    fun `empty set produces no queries`() {
        assertTrue(MangaTitleSet().queries("ENGLISH", MangaSearchLanguage.English, false).isEmpty())
    }

    @Test
    fun `partial set still returns the available title`() {
        val partial = MangaTitleSet(romajiTitle = "Solo Leveling")
        val queries = partial.queries("ENGLISH", MangaSearchLanguage.English, followTitleLanguage = false)
        assertEquals("Solo Leveling", queries.first())
    }
}
