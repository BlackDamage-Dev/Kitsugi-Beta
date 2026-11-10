package com.kitsugi.animelist.ui.screens.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mini oynatıcının "bazı videolar oynatılmıyor" hatasının kök nedenlerinden biri:
 * `extractYouTubeId` yalnızca `watch?v=` (v ilk parametre) ve `/embed/` biçimlerini
 * tanıyordu. Tanınmayan YouTube adresleri doğrudan Media3'e "medya dosyası" olarak
 * veriliyor, oynatıcı bir HTML sayfasını indirmeye çalışıp hata veriyordu.
 */
class YouTubeUrlParsingTest {

    private val id = "dQw4w9WgXcQ"

    @Test
    fun plainWatchUrlIsParsed() {
        assertEquals(id, extractYouTubeId("https://www.youtube.com/watch?v=$id"))
    }

    @Test
    fun videoIdParameterDoesNotHaveToBeFirst() {
        assertEquals(
            id,
            extractYouTubeId("https://www.youtube.com/watch?app=desktop&v=$id&feature=share")
        )
        assertEquals(
            id,
            extractYouTubeId("https://m.youtube.com/watch?feature=youtu.be&v=$id")
        )
    }

    @Test
    fun shortsLiveEmbedAndDotBeFormsAreParsed() {
        assertEquals(id, extractYouTubeId("https://www.youtube.com/shorts/$id"))
        assertEquals(id, extractYouTubeId("https://www.youtube.com/live/$id?si=abc"))
        assertEquals(id, extractYouTubeId("https://www.youtube.com/embed/$id?start=30"))
        assertEquals(id, extractYouTubeId("https://youtu.be/$id?t=42"))
        assertEquals(id, extractYouTubeId("youtu.be/$id"))
    }

    @Test
    fun musicAndNoCookieHostsAreParsed() {
        assertEquals(id, extractYouTubeId("https://music.youtube.com/watch?v=$id"))
        assertEquals(id, extractYouTubeId("https://www.youtube-nocookie.com/embed/$id"))
        assertEquals(id, extractYouTubeId(id))
    }

    @Test
    fun protocolRelativeUrlsAreParsed() {
        // Bazı sağlayıcılar şemayı kırpıyor: "//www.youtube.com/embed/...".
        assertEquals(id, extractYouTubeId("//www.youtube.com/embed/$id"))
        assertEquals(id, extractYouTubeId("//youtu.be/$id"))
    }

    @Test
    fun nonYouTubeUrlsAreNotTreatedAsYouTube() {
        assertNull(extractYouTubeId("https://v.animethemes.moe/ChainsawMan-OP1.webm"))
        assertNull(extractYouTubeId("https://example.com/watch?v=$id"))
        assertNull(extractYouTubeId(""))
    }

    @Test
    fun youtubePagesWithoutVideoIdAreStillRecognisedAsPages() {
        // Kimlik yok ama sayfa YouTube: HTML'i oynatıcıya vermek yerine dışarıda açılır.
        assertTrue(isYouTubePageUrl("https://www.youtube.com/results?search_query=chainsaw+man"))
        assertTrue(isYouTubePageUrl("https://youtube.com/@somechannel"))
        assertTrue(isYouTubePageUrl("https://www.youtube-nocookie.com/embed/"))
        assertTrue(isYouTubePageUrl("//www.youtube.com/results?search_query=x"))
        assertFalse(isYouTubePageUrl("https://v.animethemes.moe/ChainsawMan-OP1.webm"))
    }
}
