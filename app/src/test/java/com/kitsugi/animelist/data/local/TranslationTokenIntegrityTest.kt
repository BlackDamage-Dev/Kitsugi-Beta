package com.kitsugi.animelist.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationTokenIntegrityTest {
    @Test
    fun wholeTextCacheUsesANewNamespaceForVerifiedResults() {
        val oldKeyMaterial = "Story_auto_tr"
        val newKeyMaterial = completedTranslationCacheMaterial("Story", "auto", "tr")
        assertTrue(newKeyMaterial.startsWith("translation-complete-v2_"))
        assertFalse(newKeyMaterial == oldKeyMaterial)
        assertFalse(newKeyMaterial == completedTranslationCacheMaterial("Story", "ja", "tr"))
        assertFalse(newKeyMaterial == completedTranslationCacheMaterial("Story", "auto", "en"))
    }

    @Test
    fun validatesOnlyTheMarkersInTheCurrentChunk() {
        val first = "Text ⟪@0⟫ and ⟪#0⟫link⟪/0⟫"
        val second = "More text ⟪@1⟫"
        assertTrue(protectedTranslationTokensIntact(first, "Metin ⟪ @ 0 ⟫ ve ⟪ # 0 ⟫bağlantı⟪ / 0 ⟫"))
        assertTrue(protectedTranslationTokensIntact(second, "Diğer metin ⟪@1⟫"))
        assertFalse(protectedTranslationTokensIntact(first, "Metin ⟪@0⟫ ⟪#0⟫bağlantı"))
        assertFalse(protectedTranslationTokensIntact(second, "Diğer metin"))
    }

    @Test
    fun validatesRepeatedSpoilersAndRejectsExtraMarkers() {
        val source = "⟪!9990⟫one⟪!9991⟫ ⟪!9990⟫two⟪!9991⟫"
        assertTrue(protectedTranslationTokensIntact(source, "⟪!9990⟫bir⟪!9991⟫ ⟪!9990⟫iki⟪!9991⟫"))
        assertFalse(protectedTranslationTokensIntact(source, "⟪!9990⟫bir⟪!9991⟫ iki"))
        assertFalse(protectedTranslationTokensIntact("ordinary", "normal ⟪@0⟫"))
    }

    @Test
    fun oversizedTextIsNotCutInsideAProtectedMarker() {
        val text = "abcde⟪@12⟫rest"
        val cut = safeTranslationChunkCut(text, 8)
        assertEquals(5, cut)
        assertEquals("abcde", text.substring(0, cut))
        assertTrue(text.substring(cut).startsWith("⟪@12⟫"))
        assertEquals(0, safeTranslationChunkCut("", 8))
    }
}
