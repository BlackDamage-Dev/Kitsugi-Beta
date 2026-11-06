package com.kitsugi.animelist.data.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Yerel / çevrilmiş ad" butonunun arkasındaki ad planlama mantığının sözleşmesi.
 *
 * Ağ/çeviri gerektirmeyen [MangaTitleVariantBuilder] ve [MangaTitleVariantResolver]
 * yardımcıları doğrulanır; arama hattının tamamı bu varyant listesini kullanır.
 */
class MangaTitleVariantBuilderTest {

    @Test
    fun localizedModePutsTranslatedTitleFirstThenQueryThenOriginals() {
        val plan = MangaTitleVariantBuilder.build(
            query = "The Greatest Estate Developer",
            mode = MangaSearchTitleMode.LOCALIZED,
            originalTitles = listOf("역대급 영지 설계사", "The Greatest Estate Developer"),
            localizedTitles = listOf("Dahi Malikane Geliştiricisi"),
        )

        assertEquals("Yerel ad modunda ilk deneme çevrilmiş ad olmalı", "Dahi Malikane Geliştiricisi", plan.variants.first())
        assertTrue("Sorgunun kendisi de denenmeli", plan.variants.contains("The Greatest Estate Developer"))
        assertTrue("Orijinal ad son çare olarak denenmeli", plan.variants.contains("역대급 영지 설계사"))
    }

    @Test
    fun originalModePutsOriginalTitleFirst() {
        val plan = MangaTitleVariantBuilder.build(
            query = "The Greatest Estate Developer",
            mode = MangaSearchTitleMode.ORIGINAL,
            originalTitles = listOf("역대급 영지 설계사"),
            localizedTitles = listOf("Dahi Malikane Geliştiricisi"),
        )

        assertEquals("역대급 영지 설계사", plan.variants.first())
        assertEquals(
            "Orijinal modda yerel ad en sonda denenmeli",
            "Dahi Malikane Geliştiricisi",
            plan.variants.last()
        )
    }

    @Test
    fun variantsAreDedupedCaseAndPunctuationInsensitively() {
        val plan = MangaTitleVariantBuilder.build(
            query = "Solo Leveling",
            mode = MangaSearchTitleMode.LOCALIZED,
            originalTitles = listOf("Solo Leveling", "solo  leveling", "Solo Leveling!"),
            localizedTitles = listOf("Tek Başına Seviye Atlama"),
        )

        val soloVariants = plan.variants.filter { it.contains("leveling", ignoreCase = true) }
        assertEquals("Aynı ad farklı imlayla iki kez denenmemeli", 1, soloVariants.size)
    }

    @Test
    fun blankOrOneLetterTitlesAreDropped() {
        val plan = MangaTitleVariantBuilder.build(
            query = "One Piece",
            mode = MangaSearchTitleMode.LOCALIZED,
            originalTitles = listOf("", "  ", "x"),
            localizedTitles = emptyList(),
        )
        assertTrue(plan.variants.all { it.trim().length >= 2 })
        assertTrue(plan.variants.contains("One Piece"))
    }

    @Test
    fun variantsAreCappedToKeepSourceRequestsBounded() {
        val many = (1..20).map { "Alternative Title $it" }
        val plan = MangaTitleVariantBuilder.build(
            query = "Base Query",
            mode = MangaSearchTitleMode.ORIGINAL,
            originalTitles = many,
            localizedTitles = many,
        )
        assertTrue(
            "Kaynak başına denenen ad sayısı sınırlı olmalı (${plan.variants.size})",
            plan.variants.size <= MangaTitleVariantBuilder.MAX_VARIANTS
        )
    }

    @Test
    fun matchTitlesIncludeQueryAndAllVariants() {
        val plan = MangaTitleVariantBuilder.build(
            query = "The Greatest Estate Developer",
            mode = MangaSearchTitleMode.LOCALIZED,
            originalTitles = listOf("역대급 영지 설계사"),
            localizedTitles = listOf("Dahi Malikane Geliştiricisi"),
        )

        assertTrue(plan.matchTitles.contains("The Greatest Estate Developer"))
        assertTrue(plan.matchTitles.contains("Dahi Malikane Geliştiricisi"))
        assertTrue(plan.matchTitles.any { it.contains("영지") })
    }

    @Test
    fun identityPlanStillSearchesTheRawQuery() {
        val plan = MangaSearchPlan.identity("Berserk", MangaSearchTitleMode.LOCALIZED)
        assertEquals(listOf("Berserk"), plan.safeVariants())
        assertEquals(listOf("Berserk"), plan.matchTitles)
    }

    @Test
    fun dedupeKeepsFirstSeenOrder() {
        val result = MangaTitleVariantBuilder.dedupe(listOf("Beta", "alpha", "BETA!", "Gamma"))
        assertEquals(listOf("Beta", "alpha", "Gamma"), result)
    }

    @Test
    fun nonLatinDetectionCoversCjkHangulAndCyrillic() {
        assertTrue(MangaTitleVariantResolver.isNonLatin("역대급 영지 설계사"))
        assertTrue(MangaTitleVariantResolver.isNonLatin("葬送のフリーレン"))
        assertTrue(MangaTitleVariantResolver.isNonLatin("Провожающая в последний путь"))
        assertFalse(MangaTitleVariantResolver.isNonLatin("The Greatest Estate Developer"))
        assertFalse(MangaTitleVariantResolver.isNonLatin("Dahi Malikane Geliştiricisi"))
    }

    @Test
    fun turkishLookingTitlesAreRecognised() {
        assertTrue(MangaTitleVariantResolver.looksLocalizedAlready("Dahi Malikane Geliştiricisi", "tr"))
        assertTrue(MangaTitleVariantResolver.looksLocalizedAlready("Kılıç ve Büyü", "tr"))
        assertFalse(MangaTitleVariantResolver.looksLocalizedAlready("The Greatest Estate Developer", "tr"))
        // Hedef dil Türkçe değilse hiçbir zaman "yerel" sayılmaz.
        assertFalse(MangaTitleVariantResolver.looksLocalizedAlready("Kılıç ve Büyü", "en"))
    }

    @Test
    fun trimmedForDisplayPrefersTheActiveModeTitle() {
        val localized = MangaTitleVariantBuilder.build(
            query = "The Greatest Estate Developer",
            mode = MangaSearchTitleMode.LOCALIZED,
            originalTitles = listOf("역대급 영지 설계사"),
            localizedTitles = listOf("Dahi Malikane Geliştiricisi"),
        )
        assertEquals("Dahi Malikane Geliştiricisi", localized.trimmedForDisplay())

        val original = MangaTitleVariantBuilder.build(
            query = "The Greatest Estate Developer",
            mode = MangaSearchTitleMode.ORIGINAL,
            originalTitles = listOf("역대급 영지 설계사"),
            localizedTitles = listOf("Dahi Malikane Geliştiricisi"),
        )
        assertEquals("역대급 영지 설계사", original.trimmedForDisplay())
    }

    @Test
    fun overlongTitlesAreEllipsizedWithoutBreakingSurrogates() {
        val longTitle = "A".repeat(200)
        val plan = MangaTitleVariantBuilder.build(
            query = longTitle,
            mode = MangaSearchTitleMode.LOCALIZED,
        )
        val shown = plan.trimmedForDisplay(maxLength = 42)
        assertEquals(42, shown.length)
        assertTrue(shown.endsWith("…"))
    }
}
