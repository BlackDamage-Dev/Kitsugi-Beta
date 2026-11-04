package com.kitsugi.animelist.data.cloudstream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CsProviderUrlMemoryTest {

    private val aliases = listOf("mal:100", "anilist:200", "title:show:2022")

    @Before
    fun setUp() {
        CsProviderUrlMemory.clearAllForTest()
    }

    @Test
    fun testRememberedUrlRecalledByAnyAlias() {
        CsProviderUrlMemory.remember(aliases, "Anizm", "https://example.com/show")
        // Başka kaynaktan giriş yalnızca alt küme kimlikleri bilebilir:
        assertEquals("https://example.com/show", CsProviderUrlMemory.recall(listOf("anilist:200"), "Anizm"))
        assertEquals("https://example.com/show", CsProviderUrlMemory.recall(listOf("mal:100"), "Anizm"))
    }

    @Test
    fun testProvidersAreIsolated() {
        CsProviderUrlMemory.remember(aliases, "Anizm", "https://a.example/show")
        assertNull(CsProviderUrlMemory.recall(aliases, "AnimeCix"))
    }

    @Test
    fun testForgetDropsEveryAlias() {
        CsProviderUrlMemory.remember(aliases, "Anizm", "https://example.com/show")
        CsProviderUrlMemory.forget(aliases, "Anizm")
        assertNull(CsProviderUrlMemory.recall(aliases, "Anizm"))
    }

    @Test
    fun testInvalidUrlsAreRejected() {
        CsProviderUrlMemory.remember(aliases, "Anizm", "not-a-url")
        CsProviderUrlMemory.remember(aliases, "Anizm", "")
        assertNull(CsProviderUrlMemory.recall(aliases, "Anizm"))
    }

    @Test
    fun testEmptyAliasesAreRejected() {
        CsProviderUrlMemory.remember(emptyList(), "Anizm", "https://example.com/show")
        assertNull(CsProviderUrlMemory.recall(emptyList(), "Anizm"))
    }
}
