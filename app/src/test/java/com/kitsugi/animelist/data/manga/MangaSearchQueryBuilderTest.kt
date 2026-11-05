package com.kitsugi.animelist.data.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kaynak arama dili testleri.
 *
 * Kullanıcı şikâyeti: "Oku" butonu, başlık dili İngilizce seçili olmasına rağmen
 * rastgele bir adla (genelde romaji/orijinal) arama yapıyordu. Artık sorgu sırası
 * seçili dile göre kurulur ve ilk sorgu her zaman o dildeki addır.
 */
class MangaSearchQueryBuilderTest {

    private val english = "That Time I Got Reincarnated as a Slime"
    private val romaji = "Tensei Shitara Slime Datta Ken"
    private val native = "転生したらスライムだった件"

    // ─── Sorgu sırası ─────────────────────────────────────────────────────────

    @Test
    fun `english selection searches english title first`() {
        val queries = MangaSearchQueryBuilder.buildQueries(
            titleLanguage = "ENGLISH",
            searchLanguage = MangaSearchLanguage.English,
            followTitleLanguage = true,
            englishTitle = english,
            romajiTitle = romaji,
            nativeTitle = native
        )
        assertEquals(english, queries.first())
        // Yedekler de mevcut — arama asla boş kalmaz.
        assertTrue(queries.contains(romaji))
        assertTrue(queries.contains(native))
    }

    @Test
    fun `english first even when app title language is romaji`() {
        // Ayar "İngilizce" ise başlık dili romaji olsa bile İngilizce adla aranır.
        val queries = MangaSearchQueryBuilder.buildQueries(
            titleLanguage = "ROMAJI",
            searchLanguage = MangaSearchLanguage.English,
            followTitleLanguage = false,
            englishTitle = english,
            romajiTitle = romaji
        )
        assertEquals(english, queries.first())
    }

    @Test
    fun `romaji selection searches latin original first`() {
        val queries = MangaSearchQueryBuilder.buildQueries(
            titleLanguage = "ROMAJI",
            searchLanguage = MangaSearchLanguage.Romaji,
            followTitleLanguage = true,
            englishTitle = english,
            romajiTitle = romaji,
            nativeTitle = native
        )
        assertEquals(romaji, queries.first())
    }

    @Test
    fun `native selection searches original title first`() {
        val queries = MangaSearchQueryBuilder.buildQueries(
            titleLanguage = "NATIVE",
            searchLanguage = MangaSearchLanguage.Native,
            followTitleLanguage = true,
            englishTitle = english,
            romajiTitle = romaji,
            nativeTitle = native
        )
        assertEquals(native, queries.first())
    }

    @Test
    fun `localized selection prefers local title`() {
        val queries = MangaSearchQueryBuilder.buildQueries(
            titleLanguage = "ROMANJI",
            searchLanguage = MangaSearchLanguage.Local,
            followTitleLanguage = false,
            localTitle = "Slime Datta Ken Türkçe",
            englishTitle = english,
            romajiTitle = romaji
        )
        assertEquals("Slime Datta Ken Türkçe", queries.first())
    }

    // ─── Yedek zinciri ────────────────────────────────────────────────────────

    @Test
    fun `falls back to other titles when selected one is missing`() {
        val queries = MangaSearchQueryBuilder.buildQueries(
            titleLanguage = "ENGLISH",
            searchLanguage = MangaSearchLanguage.English,
            followTitleLanguage = true,
            englishTitle = null,
            romajiTitle = romaji,
            nativeTitle = native
        )
        assertEquals(romaji, queries.first())
        assertTrue(queries.contains(native))
    }

    @Test
    fun `never returns empty list when any title exists`() {
        val queries = MangaSearchQueryBuilder.buildQueries(
            titleLanguage = "ENGLISH",
            searchLanguage = MangaSearchLanguage.English,
            followTitleLanguage = true,
            englishTitle = "   ",
            romajiTitle = "",
            nativeTitle = "One Piece"
        )
        assertEquals(listOf("One Piece"), queries)
    }

    @Test
    fun `returns empty list when nothing usable`() {
        assertTrue(
            MangaSearchQueryBuilder.buildQueries(
                titleLanguage = "ENGLISH",
                searchLanguage = MangaSearchLanguage.English,
                followTitleLanguage = true
            ).isEmpty()
        )
    }

    @Test
    fun `queries are de-duplicated case insensitively`() {
        val queries = MangaSearchQueryBuilder.buildQueries(
            titleLanguage = "ENGLISH",
            searchLanguage = MangaSearchLanguage.English,
            followTitleLanguage = true,
            englishTitle = "Solo Leveling",
            romajiTitle = "solo leveling",
            nativeTitle = "나 혼자만 레벨업"
        )
        assertEquals(1, queries.count { it.equals("Solo Leveling", ignoreCase = true) })
    }

    @Test
    fun `synonyms are appended as extra fallbacks`() {
        val queries = MangaSearchQueryBuilder.buildQueries(
            titleLanguage = "ENGLISH",
            searchLanguage = MangaSearchLanguage.English,
            followTitleLanguage = true,
            englishTitle = "Solo Leveling",
            synonyms = listOf("Only I Level Up", "Na Honjaman Level Up")
        )
        assertTrue(queries.contains("Only I Level Up"))
        assertTrue(queries.contains("Na Honjaman Level Up"))
    }

    // ─── Temizleme ────────────────────────────────────────────────────────────

    @Test
    fun `clean trims collapses whitespace and rejects short input`() {
        assertEquals("Solo Leveling", MangaSearchQueryBuilder.clean("  Solo   Leveling "))
        assertNull(MangaSearchQueryBuilder.clean("a"))
        assertNull(MangaSearchQueryBuilder.clean("   "))
        assertNull(MangaSearchQueryBuilder.clean(null))
    }

    @Test
    fun `ascii strips turkish diacritics`() {
        assertEquals("Kara Buyucu", MangaSearchQueryBuilder.ascii("Kara Büyücü"))
        assertEquals("Cin Seddi", MangaSearchQueryBuilder.ascii("Çin Seddi"))
        assertEquals("Islak Sokak", MangaSearchQueryBuilder.ascii("Islak Sokak"))
    }

    @Test
    fun `cjk detection`() {
        assertTrue(MangaSearchQueryBuilder.containsCjk("転生したらスライムだった件"))
        assertTrue(MangaSearchQueryBuilder.containsCjk("나 혼자만 레벨업"))
        assertTrue(MangaSearchQueryBuilder.containsCjk("斗罗大陆"))
        assertFalse(MangaSearchQueryBuilder.containsCjk("Tensei Shitara Slime Datta Ken"))
        assertFalse(MangaSearchQueryBuilder.containsCjk(null))
    }

    // ─── Dil kimlikleri ───────────────────────────────────────────────────────

    @Test
    fun `search language ids round trip`() {
        MangaSearchLanguage.entries.forEach { language ->
            assertEquals(language, MangaSearchLanguage.fromId(MangaSearchLanguage.idOf(language)))
        }
        assertEquals(MangaSearchLanguage.English, MangaSearchLanguage.fromId("en"))
        assertEquals(MangaSearchLanguage.Native, MangaSearchLanguage.fromId("NATIVE"))
        assertEquals(MangaSearchLanguage.Local, MangaSearchLanguage.fromId("turkish"))
        assertEquals(MangaSearchLanguage.Romaji, MangaSearchLanguage.fromId(null))
    }

    @Test
    fun `orderedFor handles turkish plus title language english`() {
        val order = MangaSearchQueryBuilder.orderedFor(
            languageId = "ENGLISH",
            localTitle = "Yerel",
            englishTitle = "English",
            romajiTitle = "Romaji",
            nativeTitle = "ネイティブ"
        )
        assertEquals(listOf("English", "Romaji", "Yerel", "ネイティブ"), order)
    }

    @Test
    fun `every language has a label key`() {
        MangaSearchLanguage.entries.forEach { language ->
            val key = MangaSearchLanguage.labelKey(language)
            assertNotNull(key)
            assertTrue(key.startsWith("manga_search_lang_"))
        }
    }
}
