package com.kitsugi.animelist.data.manga

import com.kitsugi.animelist.data.remote.MangaRepoClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaRepoIdentityTest {

    @Test
    fun kitsugiGithubRepoRootNormalizesToGeneratedPluginIndex() {
        assertEquals(
            MangaRepoClient.KITSUGI_MANGA_REPO_URL,
            MangaRepoClient.normalizeMangaRepoUrl("https://github.com/f26901964-eng/Kitsugi-Manga-Plugins"),
        )
        assertEquals(
            MangaRepoClient.KITSUGI_MANGA_REPO_URL,
            MangaRepoClient.normalizeMangaRepoUrl("f26901964-eng/Kitsugi-Manga-Plugins"),
        )
    }

    @Test
    fun officialRepoIndexIsRecognizedAsKitsugiRepo() {
        assertTrue(MangaRepoClient.isKitsugiMangaRepoUrl(MangaRepoClient.KITSUGI_MANGA_REPO_URL))
    }
}
