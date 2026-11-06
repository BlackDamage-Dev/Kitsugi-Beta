package com.kitsugi.animelist.data.manga

import org.junit.Assert.assertEquals
import org.junit.Test

class MihonChapterIdentityTest {
    private val pkg = "eu.kanade.tachiyomi.extension.tr.mangitto"

    @Test
    fun legacyNumberUsesItsOwnMangaSlug() {
        assertEquals("/manga/chainsaw-man/2", restoreMangittoChapterUrl(pkg, "2", "chainsaw-man"))
    }

    @Test
    fun relativeAndAbsoluteParentUrlsAreSupported() {
        assertEquals("/manga/chainsaw-man/2", restoreMangittoChapterUrl(pkg, "2", "/manga/chainsaw-man"))
        assertEquals("/manga/chainsaw-man/2", restoreMangittoChapterUrl(pkg, "2", "https://mangtto.com/manga/chainsaw-man"))
    }

    @Test
    fun fractionalChapterKeepsLeadingZeros() {
        assertEquals("/manga/berserk/0.01", restoreMangittoChapterUrl(pkg, "0.01", "https://mangtto.com/manga/berserk"))
    }

    @Test
    fun switchingBetweenMangaCannotLeakParentIdentity() {
        assertEquals("/manga/chainsaw-man/2", restoreMangittoChapterUrl(pkg, "2", "chainsaw-man"))
        assertEquals("/manga/berserk/2", restoreMangittoChapterUrl(pkg, "2", "berserk"))
        assertEquals("/manga/chainsaw-man/2", restoreMangittoChapterUrl(pkg, "2", "chainsaw-man"))
    }

    @Test
    fun completeChapterUrlsArePreserved() {
        for (url in listOf("/manga/berserk/2", "https://mangtto.com/manga/berserk/2", "berserk/2")) {
            assertEquals(url, restoreMangittoChapterUrl(pkg, url, "chainsaw-man"))
        }
    }

    @Test
    fun otherPluginsAreNeverChanged() {
        for (other in listOf("eu.kanade.tachiyomi.extension.tr.mangatr", "eu.kanade.tachiyomi.extension.tr.mangacix", "")) {
            assertEquals("2", restoreMangittoChapterUrl(other, "2", "chainsaw-man"))
        }
    }

    @Test
    fun missingOrMalformedParentDoesNotGuessASeries() {
        for (parent in listOf("", "/", "../other", "https://mangtto.com/", "manga/slug/2", "http://[")) {
            assertEquals("2", restoreMangittoChapterUrl(pkg, "2", parent))
        }
    }

    @Test
    fun nonNumericLegacyInputsAreNotRewritten() {
        for (url in listOf("", "abc", "../2", "-2", "2?query=1")) {
            assertEquals(url, restoreMangittoChapterUrl(pkg, url, "chainsaw-man"))
        }
    }
}
