package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kaynak istemcileri başlık alanlarını farklı dolduruyordu (Simkl/TMDB'de `titleRomaji`
 * boş, MAL'da `titleRomaji`/`titleNative` boştu). Akış ekranı arama varyantlarını bu
 * alanlardan kurduğu için aynı anime farklı kaynaktan açıldığında eklentilere farklı
 * sayıda başlık gidiyordu. Harmonizer her kaynakta tam kümeyi garanti eder.
 */
class MediaDetailTitleHarmonizerTest {

    @Test
    fun romajiAndNativeTitlesLandInSynonymsWhenFieldsAreEmpty() {
        // Simkl benzeri kayıt: romaji alanı boş, Türkçe/İngilizce başlık dolu.
        val detail = detail(
            title = "Titanların Saldırısı",
            titleEnglish = "Attack on Titan",
            titleRomaji = null,
            titleNative = "進撃の巨人",
            synonyms = emptyList()
        )

        val result = MediaDetailTitleHarmonizer.harmonize(detail)

        assertTrue(result.synonyms.contains("Attack on Titan"))
        assertTrue(result.synonyms.contains("進撃の巨人"))
        // Birincil başlık kendini tekrar etmez.
        assertTrue(result.synonyms.none { it == "Titanların Saldırısı" })
    }

    @Test
    fun turkishTitleJoinsTheSearchableVariantSet() {
        val detail = detail(
            title = "Attack on Titan",
            titleEnglish = "Attack on Titan",
            titleRomaji = "Shingeki no Kyojin",
            titleTurkish = "Titanların Saldırısı"
        )

        val result = MediaDetailTitleHarmonizer.harmonize(detail)

        assertTrue(result.synonyms.contains("Shingeki no Kyojin"))
        assertTrue(result.synonyms.contains("Titanların Saldırısı"))
        assertEquals(2, result.synonyms.size)
    }

    @Test
    fun nativeAndJapaneseFieldsCompleteEachOther() {
        val detail = detail(
            title = "Shingeki no Kyojin",
            titleJapanese = "進撃の巨人",
            titleNative = null
        )

        val result = MediaDetailTitleHarmonizer.harmonize(detail)

        assertEquals("進撃の巨人", result.titleNative)
        assertEquals("進撃の巨人", result.titleJapanese)
        assertTrue(result.synonyms.contains("進撃の巨人"))
    }

    @Test
    fun duplicatesAreCollapsedIgnoringCaseAndSpacing() {
        val detail = detail(
            title = "Attack on Titan",
            titleEnglish = "attack   on  TITAN",
            synonyms = listOf("Attack on Titan", "AoT", "aot")
        )

        val result = MediaDetailTitleHarmonizer.harmonize(detail)

        assertEquals(listOf("AoT"), result.synonyms)
    }

    @Test
    fun unchangedDetailIsReturnedAsIs() {
        val detail = detail(
            title = "Attack on Titan",
            titleEnglish = "Attack on Titan",
            titleRomaji = "Shingeki no Kyojin",
            synonyms = listOf("Shingeki no Kyojin")
        )

        // Zaten harmonize edilmiş nesne yeniden kopyalanmaz (referans aynı kalır).
        assertTrue(MediaDetailTitleHarmonizer.harmonize(detail) === detail)
        assertNull(detail.titleNative)
    }

    private fun detail(
        title: String,
        titleEnglish: String? = null,
        titleRomaji: String? = null,
        titleNative: String? = null,
        titleJapanese: String? = null,
        titleTurkish: String? = null,
        synonyms: List<String> = emptyList()
    ) = KitsugiMediaDetail(
        synopsis = null,
        title = title,
        type = MediaType.Anime,
        titleEnglish = titleEnglish,
        titleRomaji = titleRomaji,
        titleNative = titleNative,
        titleJapanese = titleJapanese,
        titleTurkish = titleTurkish,
        synonyms = synonyms
    )
}
