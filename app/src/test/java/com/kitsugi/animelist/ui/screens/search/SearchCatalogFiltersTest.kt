package com.kitsugi.animelist.ui.screens.search

import com.kitsugi.animelist.utils.CatalogOrganization
import com.kitsugi.animelist.utils.CatalogTerm
import com.kitsugi.animelist.utils.TaxonomyOrgRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure facet/state contract; provider filtering is a separate T55–T60 test. */
class SearchCatalogFiltersTest {
    @Test fun oldFiltersRemainDefault() {
        assertTrue(SearchFilters().isDefault())
        val studio = OrgFilter(TaxonomyOrgRole.STUDIO, "bangumi_studio_mappa")
        assertFalse(SearchFilters(organizations = listOf(studio)).isDefault())
        assertEquals(listOf(studio), SearchFilters(organizations = listOf(studio))
            .copy(genres = listOf("Action")).organizations)
    }

    @Test fun selectedOrganizationIsVisibleButNotExecutableUntilSourceMapping() {
        val studio = OrgFilter(TaxonomyOrgRole.STUDIO, "bangumi_studio_mappa")
        val state = SearchUiState(organizations = listOf(studio))
        assertEquals(1, state.activeFilterCount)
        assertFalse(state.hasFiltersApplied)
        assertEquals(listOf(studio), state.toLegacyFilters().organizations)
    }

    @Test fun aliasesMatchSameCanonicalChipWithoutBeingDisplayNames() {
        val action = CatalogTerm("action", "Aksiyon", "Action", listOf("Экшен"), 1)
        for (query in listOf("aksiyon", "ACTION", "экшен")) {
            assertTrue("$query should match action", action.matches(query))
        }
        assertEquals("Aksiyon", action.displayName(turkish = true))
        assertEquals("Action", action.displayName(turkish = false))
        val kyoto = CatalogOrganization("bangumi_studio_kyoto_animation", TaxonomyOrgRole.STUDIO,
            "KyoAni", listOf("京阿尼", "Kyoto Animation"))
        assertTrue(kyoto.matches("京阿尼"))
        assertFalse(kyoto.name.contains("京阿尼"))
    }
}
