package com.kitsugi.animelist.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MAL tarih temizleme dayanıklılığı (E49/E62).
 *
 * MAL'da "tarihi sil" karşılığı `0000-00-00` sentinel'idir (boş string 400 döner). Sunucu
 * bir gün bu sentinel'i reddederse isteğin TAMAMI 400 olur ve aynı gövdedeki
 * durum/ilerleme/puan da yazılmaz. Bu yüzden sentinel'i taşıyan PATCH 400 alırsa yalnız
 * tarih temizleme parametreleri çıkarılıp bir kez daha denenir; kalan alanlar korunur.
 */
class MalDateClearBodyTest {

    @Test
    fun onlyDateClearParametersAreDropped() {
        val body = "status=completed&is_rewatching=true&num_watched_episodes=12" +
            "&start_date=0000-00-00&finish_date=0000-00-00&score=8"
        val stripped = MalSyncManager.stripDateClears(body)

        assertFalse(stripped.contains("0000-00-00"))
        assertTrue(stripped.contains("status=completed"))
        assertTrue(stripped.contains("is_rewatching=true"))
        assertTrue(stripped.contains("num_watched_episodes=12"))
        assertTrue(stripped.contains("score=8"))
        assertFalse("boş parametre kalmamalı", stripped.contains("&&"))
        assertFalse("baş/son '&' kalmamalı", stripped.startsWith("&") || stripped.endsWith("&"))
    }

    @Test
    fun aRealDateWithSameDayIsNotMistakenForTheSentinel() {
        // "2000-00-00" gibi geçersiz ama FARKLI bir değer süzülmemeli: yalnız sentinel atılır.
        val body = "start_date=2000-00-00&score=7"
        assertEquals(body, MalSyncManager.stripDateClears(body))
    }

    @Test
    fun bodyThatOnlyClearsDatesBecomesEmpty() {
        // Bu durumda gönderilecek alan kalmaz; çağıran isteği atmaz (bkz. patchWithDateClearFallback).
        assertEquals("", MalSyncManager.stripDateClears("start_date=0000-00-00"))
        assertEquals("", MalSyncManager.stripDateClears("start_date=0000-00-00&finish_date=0000-00-00"))
    }

    @Test
    fun bodyWithoutDateClearIsReturnedUnchanged() {
        val body = "status=watching&num_watched_episodes=3"
        assertEquals(body, MalSyncManager.stripDateClears(body))
    }
}
