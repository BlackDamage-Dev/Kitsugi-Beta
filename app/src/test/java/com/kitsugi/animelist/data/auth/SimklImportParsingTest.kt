package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.model.WatchStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Simkl içe aktarma ayrıştırması: durum sözlüğü (E79) ve özel not (E80).
 *
 * E79: Simkl bırakılanları `notinteresting` kovasında tutar; eşleme tablosunda bu
 * değer yokken bırakılan kayıtlar "planlandı" olarak içe aktarılıyordu.
 * E80: Yazıcı `private_memo` yazıyor ama içe aktarma notu hiç okumuyordu; resmî
 * sözleşmede okuma `memos=yes` ile gelen `memo.text` alanıdır
 * (api.simkl.org/guides/sync — alan adı tekil `memo`, boş not `{}`).
 */
class SimklImportParsingTest {

    @Test
    fun notInterestingImportsAsDropped() {
        assertEquals(WatchStatus.Dropped, SimklImportManager.mapSimklStatus("notinteresting"))
        assertEquals(WatchStatus.Dropped, SimklImportManager.mapSimklStatus("dropped"))
    }

    @Test
    fun knownStatusesKeepTheirMeanings() {
        assertEquals(WatchStatus.Watching, SimklImportManager.mapSimklStatus("watching"))
        assertEquals(WatchStatus.Watching, SimklImportManager.mapSimklStatus("reading"))
        assertEquals(WatchStatus.Completed, SimklImportManager.mapSimklStatus("completed"))
        assertEquals(WatchStatus.Paused, SimklImportManager.mapSimklStatus("hold"))
        assertEquals(WatchStatus.Planned, SimklImportManager.mapSimklStatus("plantowatch"))
    }

    @Test
    fun unknownAndMissingStatusesFallBackToPlanned() {
        assertEquals(WatchStatus.Planned, SimklImportManager.mapSimklStatus(null))
        assertEquals(WatchStatus.Planned, SimklImportManager.mapSimklStatus(""))
        assertEquals(WatchStatus.Planned, SimklImportManager.mapSimklStatus("bilinmeyen"))
    }

    @Test
    fun statusMatchingIsCaseInsensitive() {
        assertEquals(WatchStatus.Dropped, SimklImportManager.mapSimklStatus("NotInteresting"))
        assertEquals(WatchStatus.Watching, SimklImportManager.mapSimklStatus("WATCHING"))
    }

    @Test
    fun memoTextIsReadFromMemoObject() {
        val item = JSONObject("""{"status":"watching","memo":{"text":"3. sezonda kaldım","is_private":true}}""")
        assertEquals("3. sezonda kaldım", SimklImportManager.simklMemoText(item))
    }

    @Test
    fun emptyOrMissingMemoMeansNoNote() {
        assertNull(SimklImportManager.simklMemoText(JSONObject("""{"status":"watching","memo":{}}""")))
        assertNull(SimklImportManager.simklMemoText(JSONObject("""{"status":"watching"}""")))
        assertNull(SimklImportManager.simklMemoText(JSONObject("""{"memo":{"text":"  "}}""")))
        assertNull(SimklImportManager.simklMemoText(JSONObject("""{"memo":{"is_private":false}}""")))
    }
}
