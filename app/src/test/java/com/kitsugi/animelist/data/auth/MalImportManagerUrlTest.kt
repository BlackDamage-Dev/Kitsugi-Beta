package com.kitsugi.animelist.data.auth

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class MalImportManagerUrlTest {
    @Test
    fun animeListUrlEncodesNestedFieldsAndSupportsAdultContentSetting() {
        assertValidListUrl(
            url = MalImportManager.buildAnimeListUrl(showAdultContent = false),
            path = "/v2/users/@me/animelist",
            adultContent = false,
            progressField = "num_episodes_watched",
            rewatchField = "is_rewatching"
        )
    }

    @Test
    fun mangaListUrlEncodesNestedFieldsAndSupportsAdultContentSetting() {
        assertValidListUrl(
            url = MalImportManager.buildMangaListUrl(showAdultContent = true),
            path = "/v2/users/@me/mangalist",
            adultContent = true,
            progressField = "num_chapters_read",
            rewatchField = "is_rereading"
        )
    }

    @Test
    fun favoritedFieldCanBeDroppedWhenServerRejectsIt() {
        // MAL API v2 `MyListStatusObject` şemasında `is_favorited` YOK. Sunucu alanı
        // reddederse tüm içe aktarma 400 ile düşerdi; bu yardımcı alanı atıp isteği
        // geçerli (ve istek şemasına uygun) hâle getirir.
        val full = MalImportManager.buildAnimeListUrl(showAdultContent = false)
        assertTrue(full.contains("is_favorited"))

        val safe = MalImportManager.withoutFavoritedField(full)
        assertFalse(safe.contains("is_favorited"))
        // Kalan alan seçicisi bozulmamalı: virgül/parantez artığı kalmamalı.
        assertFalse(safe.contains(",,", ignoreCase = false))
        assertFalse(safe.contains("{,"))
        assertFalse(safe.contains(",}"))

        val fields = requireNotNull(safe.toHttpUrl().queryParameter("fields"))
        assertTrue(fields.contains("list_status{"))
        assertTrue(fields.endsWith("}"))
        assertTrue(fields.contains("num_episodes_watched"))

        val mangaSafe = MalImportManager.withoutFavoritedField(
            MalImportManager.buildMangaListUrl(showAdultContent = false)
        )
        assertFalse(mangaSafe.contains("is_favorited"))
        assertTrue(requireNotNull(mangaSafe.toHttpUrl().queryParameter("fields")).endsWith("}"))
    }

    private fun assertValidListUrl(
        url: String,
        path: String,
        adultContent: Boolean,
        progressField: String,
        rewatchField: String
    ) {
        // This was the failing point during cross-account sync: raw `{` / `}` in the query
        // caused java.net.URI to throw "Illegal character in query" before the HTTP request.
        val uri = URI(url)
        assertEquals("https", uri.scheme)
        assertEquals("api.myanimelist.net", uri.host)
        assertEquals(path, uri.rawPath)
        assertFalse(url.contains('{'))
        assertFalse(url.contains('}'))

        val httpUrl = url.toHttpUrl()
        val fields = requireNotNull(httpUrl.queryParameter("fields"))
        assertTrue(fields.contains("list_status{"))
        assertTrue(fields.contains(progressField))
        // MAL API v2 `list_status` tekrar bayrakları anime/manga için FARKLI adlanır;
        // `is_repeating` diye bir alan yoktur (400 döner).
        assertTrue(fields.contains(rewatchField))
        assertFalse(fields.contains("is_repeating"))
        assertTrue(fields.endsWith("}"))
        assertEquals("100", httpUrl.queryParameter("limit"))
        assertEquals(adultContent.toString(), httpUrl.queryParameter("nsfw"))
    }
}
