package com.kitsugi.animelist.data.auth

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tarih yazma niyeti sözleşmesi (E63).
 *
 * En kritik vaka: kullanıcı tarihe DOKUNMADIĞI hâlde, yerel satırda ayrıştırılamayan bir
 * değer (içe aktarmadan sızmış `"2016"` / `"2016-05"`) bulunduğunda hedefteki tarihin
 * SİLİNMEMESİ. Eski yazıcılar bu iki durumu ayırt etmiyordu.
 */
class SyncDatesActionTest {

    @Test
    fun parsedValueIsWritten() {
        assertEquals(SyncDates.Action.WRITE, SyncDates.action("2024-03-01", Any(), false))
        assertEquals(SyncDates.Action.WRITE, SyncDates.action("2024-03-01", Any(), true))
    }

    @Test
    fun explicitlyClearedDateIsClearedOnIncrementalWrite() {
        assertEquals(SyncDates.Action.CLEAR, SyncDates.action(null, null, false))
        assertEquals(SyncDates.Action.CLEAR, SyncDates.action("", null, false))
        assertEquals(SyncDates.Action.CLEAR, SyncDates.action("   ", null, false))
    }

    @Test
    fun blankDateIsNeverClearedOnFullWrite() {
        // Yeni kayıtta silinecek tarih yok; mevcut kayıtta yerel boşluk uzağı ezmemeli.
        assertEquals(SyncDates.Action.SKIP, SyncDates.action(null, null, true))
        assertEquals(SyncDates.Action.SKIP, SyncDates.action("", null, true))
    }

    @Test
    fun partialDateIsNotTreatedAsCleared() {
        // "2016" / "2016-05" parse edilemez ama "boşaltıldı" DEĞİLDİR: hedefteki tarih korunur.
        assertEquals(SyncDates.Action.SKIP, SyncDates.action("2016", null, false))
        assertEquals(SyncDates.Action.SKIP, SyncDates.action("2016-05", null, false))
        assertEquals(SyncDates.Action.SKIP, SyncDates.action("0000-00-00", null, false))
    }
}
