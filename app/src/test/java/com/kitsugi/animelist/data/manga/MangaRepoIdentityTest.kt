package com.kitsugi.animelist.data.manga

import com.kitsugi.animelist.data.remote.MangaRepoClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaRepoIdentityTest {

    @Test
    fun kitsugiGithubRepoRootNormalizesToGeneratedPluginIndex() {
        assertEquals(
            "https://raw.githubusercontent.com/BlackDamage-Plugin/Kitsugi-Manga-Plugins/main/index.min.json",
            MangaRepoClient.KITSUGI_MANGA_REPO_URL,
        )
        assertEquals(
            MangaRepoClient.KITSUGI_MANGA_REPO_URL,
            MangaRepoClient.normalizeMangaRepoUrl("https://github.com/BlackDamage-Plugin/Kitsugi-Manga-Plugins"),
        )
        assertEquals(
            MangaRepoClient.KITSUGI_MANGA_REPO_URL,
            MangaRepoClient.normalizeMangaRepoUrl("BlackDamage-Plugin/Kitsugi-Manga-Plugins"),
        )
        assertEquals(
            MangaRepoClient.KITSUGI_MANGA_REPO_URL,
            MangaRepoClient.normalizeMangaRepoUrl("https://raw.githubusercontent.com/BlackDamage-Plugin/Kitsugi-Manga-Plugins/builds/index.json"),
        )
        assertEquals(
            MangaRepoClient.KITSUGI_MANGA_REPO_URL,
            MangaRepoClient.normalizeMangaRepoUrl("https://raw.githubusercontent.com/f26901964-eng/Kitsugi-Manga-Plugins/builds/index.json"),
        )
    }

    @Test
    fun officialRepoIndexIsRecognizedAsKitsugiRepo() {
        assertTrue(MangaRepoClient.isKitsugiMangaRepoUrl(MangaRepoClient.KITSUGI_MANGA_REPO_URL))
    }
}
