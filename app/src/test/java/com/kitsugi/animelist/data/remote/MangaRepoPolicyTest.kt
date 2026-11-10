package com.kitsugi.animelist.data.remote

import org.junit.Assert.*
import org.junit.Test

class MangaRepoPolicyTest {
    @Test fun smallThirdPartyCatalogIsNotADecoy() {
        val body = """[{"name":"Manga-TR","pkg":"eu.kanade.tachiyomi.extension.tr.mangatr"}]"""
        assertFalse(isMangaRepoDecoy(body, "https://raw.githubusercontent.com/BlackDamage-Plugin/Kitsugi-Manga-Plugins/builds/index.json"))
        assertTrue(isMangaRepoDecoy(body, "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json"))
        assertTrue(isMangaRepoDecoy("Update to Mihon", "https://example.org/index.json"))
    }
}
