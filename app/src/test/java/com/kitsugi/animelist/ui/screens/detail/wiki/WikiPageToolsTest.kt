package com.kitsugi.animelist.ui.screens.detail.wiki

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WikiPageToolsTest {
    @Test fun automaticPreferenceAndManualOriginalWorkIndependently() {
        val prefs = WikiPagePreferences(true, "auto", "tr", emptySet())
        assertTrue(prefs.configFor("you-zitsu.fandom.com", null, null, 0).translate)
        assertFalse(prefs.configFor("you-zitsu.fandom.com", false, prefs.translationKey, 0).translate)
        assertTrue(prefs.configFor("you-zitsu.fandom.com", false, "old-preferences", 0).translate)
        val off = prefs.copy(autoTranslate = false)
        assertFalse(off.configFor("you-zitsu.fandom.com", null, null, 0).translate)
        assertTrue(off.configFor("you-zitsu.fandom.com", true, off.translationKey, 0).translate)
    }

    @Test fun adExceptionOnlyAffectsExactWikiHost() {
        val prefs = WikiPagePreferences(false, "auto", "tr", setOf("you-zitsu.fandom.com"))
        assertFalse(prefs.configFor("YOU-ZITSU.FANDOM.COM", null, null, 0).blockAds)
        assertTrue(prefs.configFor("naruto.fandom.com", null, null, 0).blockAds)
    }

    @Test fun resourceMatcherUsesHostBoundariesAndProtectsWikiContent() {
        val rules = WikiAdBlockRules.fromHosts("0.0.0.0 adserver.test\n0.0.0.0 wikia.nocookie.net")
        assertTrue(rules.shouldBlock("https://cdn.adserver.test/request", false))
        assertFalse(rules.shouldBlock("https://adserver.test/", true))
        assertFalse(rules.shouldBlock("https://adserver.test.safe.test/image", false))
        assertFalse(rules.shouldBlock("https://static.wikia.nocookie.net/image.png", false))
        assertTrue(rules.shouldBlock("https://services.fandom.com/ads/config", false))
        assertFalse(rules.shouldBlock("https://services.fandom.com/content/config", false))
    }

    @Test fun slotTokensCannotBeReorderedLostOrDuplicated() {
        val rows = listOf(WikiDomText(1, 0, "Hello"), WikiDomText(2, 0, "world"))
        assertEquals(listOf("Hello", "world"), WikiDomTranslationBatch.unpack(WikiDomTranslationBatch.pack(rows), 2))
        assertNull(WikiDomTranslationBatch.unpack("⟪K1⟫world⟪/K1⟫⟪K0⟫Hello⟪/K0⟫", 2))
        assertNull(WikiDomTranslationBatch.unpack("⟪K0⟫Hello⟪/K0⟫", 2))
        assertNull(WikiDomTranslationBatch.unpack("⟪K0⟫Hello⟪/K0⟫⟪K0⟫world⟪/K0⟫", 2))
    }

    @Test fun cachedRowsSkipTheNetworkAndFreshRowsAreStoredIndividually() = runBlocking {
        val rows = listOf(
            WikiDomText(1, 0, "Cached hello"),
            WikiDomText(2, 0, "Fresh world"),
            WikiDomText(3, 0, "Cached hello")
        )
        val networkCalls = mutableListOf<String>()
        val stored = mutableListOf<Pair<String, String>>()
        val results = WikiDomTranslationBatch.translate(
            rows,
            cached = { text -> if (text == "Cached hello") "Önbellek merhaba" else null },
            store = { stored.addAll(it) }
        ) { text ->
            networkCalls.add(text)
            "Çevrildi: $text"
        }
        assertEquals(
            listOf("Önbellek merhaba", "Çevrildi: Fresh world", "Önbellek merhaba"),
            results.map { it.translated }
        )
        // Yalnızca eksik ve tekilleştirilmiş metin ağa gitti.
        assertEquals(listOf("Fresh world"), networkCalls)
        assertEquals(listOf("Fresh world" to "Çevrildi: Fresh world"), stored)
    }

    @Test fun packedBatchUsesOneRequestAndKeepsSlotOrder() = runBlocking {
        val rows = listOf(WikiDomText(1, 0, "Hello"), WikiDomText(2, 0, "world"))
        var calls = 0
        val results = WikiDomTranslationBatch.translate(rows) { text ->
            calls++
            text.replace("Hello", "Merhaba").replace("world", "dünya")
        }
        assertEquals(listOf("Merhaba", "dünya"), results.map { it.translated })
        assertEquals(1, calls)
    }

    @Test fun mangledDelimitersFallBackPerRowWithoutGuessing() = runBlocking {
        val rows = listOf(WikiDomText(1, 0, "Hello"), WikiDomText(2, 0, "world"))
        val results = WikiDomTranslationBatch.translate(rows) { text ->
            // Paket çağrısında ayraçları boz; tekil çağrılarda düz çevir.
            if (text.contains("⟪K")) "Merhaba dünya" else "TR:$text"
        }
        assertEquals(listOf("TR:Hello", "TR:world"), results.map { it.translated })
    }

    @Test fun outageDoesNotFanOutAndResultsStayNull() = runBlocking {
        val rows = listOf(WikiDomText(1, 0, "Hello"), WikiDomText(2, 0, "world"))
        var calls = 0
        val results = WikiDomTranslationBatch.translate(rows) { calls++; null }
        assertEquals(listOf(null, null), results.map { it.translated })
        assertEquals(1, calls)
    }

    @Test fun invalidLanguageValuesNeverEnterTranslationUrl() {
        val prefs = WikiPagePreferences(true, "en&key=oops", "auto", emptySet())
        val config = prefs.configFor("you-zitsu.fandom.com", null, null, 0)
        assertEquals("auto", config.source)
        assertEquals("tr", config.target)
    }
}
