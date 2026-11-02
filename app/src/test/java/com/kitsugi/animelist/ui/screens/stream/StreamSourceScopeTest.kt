package com.kitsugi.animelist.ui.screens.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kullanıcı bir eklentinin kendi sayfasından "İzle" dediğinde arama o eklentide kalmalı;
 * diğer kaynaklar ancak açık "Tüm kaynaklarda ara" tercihiyle devreye girmeli.
 */
class StreamSourceScopeTest {

    @Test
    fun pluginFlowStaysScopedUnlessAllSourcesRequested() {
        assertTrue(StreamSourceScope.useSingleSource("Anizm", allSourcesRequested = false))
        assertFalse(StreamSourceScope.useSingleSource("Anizm", allSourcesRequested = true))
    }

    @Test
    fun sharedFlowWithoutPluginUsesAllSources() {
        assertFalse(StreamSourceScope.useSingleSource(null, allSourcesRequested = false))
        assertFalse(StreamSourceScope.useSingleSource("   ", allSourcesRequested = false))
        assertFalse(StreamSourceScope.useSingleSource("", allSourcesRequested = true))
    }

    @Test
    fun displayNameAddsLightningPrefixOnlyOnce() {
        assertEquals("⚡ Anizm", StreamSourceScope.displayName("Anizm"))
        assertEquals("⚡ Anizm", StreamSourceScope.displayName("  Anizm  "))
        assertEquals("⚡ Anizm", StreamSourceScope.displayName("⚡ Anizm"))
    }
}
