package com.kitsugi.animelist.ui.screens.search

import org.junit.Assert.assertEquals
import org.junit.Test

class PluginPortalSearchTest {
    @Test
    fun normalizationIgnoresTurkishAccentsAndDottedI() {
        assertEquals(
            "film makinesi turkce",
            normalizePluginPortalSearchText("FİLM Makİnesi Türkçe")
        )
    }

    @Test
    fun normalizationMakesUrlsAndSourceNamesSearchable() {
        val normalized = normalizePluginPortalSearchText("https://hdfilmcehennemi.to Film-Makinesi")
        assertEquals(
            "https hdfilmcehennemi to film makinesi",
            normalized
        )
        org.junit.Assert.assertTrue(normalized.contains(normalizePluginPortalSearchText("hdfilmcehennemi")))
        org.junit.Assert.assertTrue(normalized.contains(normalizePluginPortalSearchText("Film Makinesi")))
    }
}
