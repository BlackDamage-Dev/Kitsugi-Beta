package com.kitsugi.animelist.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.4.369 `kitsugi_debug.log`: Media3 `prepare()` sırasında aynı milisaniyede önce
 * "Buffering started: rebuffer" sonra "Buffering started: initial-load" yazılıyordu ve HTTP 429
 * ile biten oturumun özeti "buffering: 0 event(s), total 0 ms" gösteriyordu.
 */
class PlayerLoadingDiagnosticsBufferingTest {

    @Test
    fun `hazir olmadan once gelen ikinci baslangic yok sayilir ve etiket initial-load olur`() {
        val diag = PlayerLoadingDiagnostics()
        diag.startSession("s", "url")
        diag.onBufferingStarted("rebuffer")      // prepare() içindeki STATE_BUFFERING dinleyicisi
        diag.onBufferingStarted("initial-load")  // hemen ardından gelen açık çağrı
        diag.onBufferingEnded()
        val events = diag.dump().bufferingEvents
        assertEquals(1, events.size)
        assertEquals("initial-load", events[0].reason)
    }

    @Test
    fun `ilk hazirliktan sonraki tamponlama rebuffer olarak etiketlenir`() {
        val diag = PlayerLoadingDiagnostics()
        diag.startSession("s", "url")
        diag.onBufferingStarted("initial-load")
        diag.onBufferingEnded()
        diag.onBufferingStarted("initial-load")  // yanlış etiketle çağrılsa bile
        diag.onBufferingEnded()
        val events = diag.dump().bufferingEvents
        assertEquals(listOf("initial-load", "rebuffer"), events.map { it.reason })
    }

    @Test
    fun `tamponlama surerken gelen hata ozete eklenir`() {
        val diag = PlayerLoadingDiagnostics()
        diag.startSession("s", "url")
        diag.onBufferingStarted("rebuffer")
        diag.onPlaybackError(2004, "HTTP 429")
        val snap = diag.dump()
        assertEquals(1, snap.bufferingEvents.size)
        assertTrue(snap.bufferingEvents[0].reason.startsWith("initial-load"))
        assertEquals("ERROR", snap.finalState)
    }

    @Test
    fun `yeni oturum hazirlik durumunu sifirlar`() {
        val diag = PlayerLoadingDiagnostics()
        diag.startSession("a", "url")
        diag.onBufferingStarted()
        diag.onBufferingEnded()
        diag.startSession("b", "url2")
        diag.onBufferingStarted("rebuffer")
        diag.onBufferingEnded()
        assertEquals("initial-load", diag.dump().bufferingEvents.single().reason)
    }
}
