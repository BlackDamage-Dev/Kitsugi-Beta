package com.kitsugi.animelist.ui.screens.explore

import com.kitsugi.animelist.ui.components.KitsugiGridSortOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Tümünü Gör" filtre sayfasının **Bangumi** yetenekleri.
 *
 * Filtre sayfası, kaynağın sunucuda gerçekten uygulayabildiği tür/sıralamaları
 * `serverGenreIds()` / `serverSorts()` ile öğrenir ve desteklenmeyenleri "📱 yerel"
 * diye işaretler. Bu testler, canlı API problarıyla (F1/F2, Q1–Q4, S/T blokları)
 * doğrulanmış sözleşmeyi kilitler.
 */
class ExploreSourceFiltersBangumiTest {

    @Test
    fun `bangumi tum tur ciplerini sunucuda destekler`() {
        for (category in listOf(
            ExploreCategoryType.TOP_ANIME,
            ExploreCategoryType.TOP_MANGA,
            ExploreCategoryType.TOP_RATED_MANGA,
            ExploreCategoryType.BANGUMI_TV,
            ExploreCategoryType.BANGUMI_MOVIES
        )) {
            val ids = ExploreSourceFilters.serverGenreIds(ExplorePlatform.BANGUMI, category)
            assertEquals("$category — çip sayısı", 19, ids.size)
            assertTrue("$category — SPORTS eksik", "SPORTS" in ids)
            assertTrue("$category — THRILLER eksik", "THRILLER" in ids)
            assertTrue("$category — MILITARY eksik", "MILITARY" in ids)
        }
    }

    @Test
    fun `bangumi puan ve populerlik siralamasini tur secilmeden de sunucuda uygular`() {
        val sorts = ExploreSourceFilters.serverSorts(
            ExplorePlatform.BANGUMI,
            ExploreCategoryType.TOP_ANIME,
            genreId = "ALL"
        )
        // Eskiden `SORT_SUPPORT[BANGUMI] = emptySet()` yüzünden bu küme boştu ve
        // filtre sayfası puan/popülerlik çiplerini "yerel" diye işaretliyordu.
        assertTrue("SCORE_DESC sunucuda yok", KitsugiGridSortOption.SCORE_DESC in sorts)
        assertTrue("POPULARITY_DESC sunucuda yok", KitsugiGridSortOption.POPULARITY_DESC in sorts)
        assertTrue(
            "supportsSort(SCORE_DESC, ALL) false",
            ExploreSourceFilters.supportsSort(
                ExplorePlatform.BANGUMI,
                ExploreCategoryType.TOP_ANIME,
                KitsugiGridSortOption.SCORE_DESC,
                "ALL"
            )
        )
    }

    /**
     * Roman rafı artık Bangumi'de **sunucu tarafında** süzülür: `type=1` kataloğunda romanlar
     * `meta_tags=小说` ile ayrılır (canlı prob Q4/U5: 539 kayıt). Eskiden bu raf
     * `shelfSpec()` tarafından tamamen dışlanıyordu; "Tümü" modunda roman ızgarasına tür
     * çipi uygulandığında Bangumi hiç katkı vermiyordu.
     */
    @Test
    fun `bangumi roman rafinda tur ve siralamayi sunucuda uygular`() {
        val ids = ExploreSourceFilters.serverGenreIds(ExplorePlatform.BANGUMI, ExploreCategoryType.NOVELS)
        assertEquals("NOVELS — çip sayısı", 19, ids.size)
        val sorts = ExploreSourceFilters.serverSorts(
            ExplorePlatform.BANGUMI,
            ExploreCategoryType.NOVELS,
            genreId = "ALL"
        )
        assertTrue("NOVELS — SCORE_DESC sunucuda yok", KitsugiGridSortOption.SCORE_DESC in sorts)
        assertTrue("NOVELS — POPULARITY_DESC sunucuda yok", KitsugiGridSortOption.POPULARITY_DESC in sorts)
    }

    @Test
    fun `bangumi manhwa rafini desteklemez`() {
        // Bangumi'de manhwa/manhua ayrı bir platform yok → raf dışlanır, sahte sonuç üretilmez.
        assertTrue(
            ExploreSourceFilters.supportsGenre(
                ExplorePlatform.BANGUMI,
                ExploreCategoryType.MANHWA_MANHUA,
                "ROMANCE"
            ).not()
        )
    }

    @Test
    fun `bangumi baslik siralmasini sunucuda desteklemez`() {
        // Bangumi v0'da başlık sıralaması yok → sayfa cihazda sıralar.
        assertTrue(
            ExploreSourceFilters.supportsSort(
                ExplorePlatform.BANGUMI,
                ExploreCategoryType.TOP_ANIME,
                KitsugiGridSortOption.TITLE_ASC,
                "ALL"
            ).not()
        )
    }
}
