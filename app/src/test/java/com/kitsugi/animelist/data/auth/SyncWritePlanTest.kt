package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Hedefte kayıt yokken alan süzmesi uygulanmaz" kuralı.
 *
 * Kullanıcı raporu: Kitsu'dan içe aktarılmış bir yapımda yalnızca favori/etiket değiştirildiğinde
 * Simkl · Kitsu · Shikimori · Bangumi için "değişiklik yok" deniyor, oysa kayıt bu kaynaklarda
 * hiç oluşmuyordu. Sebep: değişen alan kümesi bu sağlayıcılar için boş kalıyor ve ağa hiç
 * çıkılmıyordu. Artık varlık doğrulanmadıysa tam yazım yapılır.
 */
class SyncWritePlanTest {

    @Test
    fun verifiedPresenceKeepsFieldFiltering() {
        val plan = planSyncWrite(
            requested = setOf(SyncField.SCORE),
            isFullWrite = false,
            presenceConfirmed = true
        )
        assertEquals(setOf(SyncField.SCORE), plan.fields)
        assertFalse("Doğrulanmış kayıtta tam yazım hedefteki değerleri ezmemeli", plan.fullWrite)
    }

    @Test
    fun unknownPresenceEscalatesToFullWrite() {
        val plan = planSyncWrite(
            requested = setOf(SyncField.SCORE),
            isFullWrite = false,
            presenceConfirmed = false
        )
        assertEquals("Kayıt yokken/dogrulanmamışken tüm alanlar yazılmalı", SyncField.ALL, plan.fields)
        assertTrue("Tam yazım bayrağı boş alanların gönderilmesini engeller", plan.fullWrite)
    }

    @Test
    fun emptyDiffWithUnknownPresenceStillWrites() {
        val plan = planSyncWrite(
            requested = emptySet(),
            isFullWrite = false,
            presenceConfirmed = false
        )
        assertEquals(SyncField.ALL, plan.fields)
        assertTrue(plan.fullWrite)
    }

    @Test
    fun emptyDiffWithVerifiedPresenceIsNoOp() {
        val plan = planSyncWrite(
            requested = emptySet(),
            isFullWrite = true,
            presenceConfirmed = true
        )
        assertTrue("Hedef zaten eşitse ağa çıkılmamalı", plan.fields.isEmpty())
    }

    @Test
    fun ownSourcePresenceKeyIsSharedBySiblingRows() {
        // Aynı yapının farklı kaynak satırları (Kitsu satırı + MAL satırı) aynı varlık anahtarını
        // üretmeli; aksi hâlde her satır diğerlerinin kaydını "yok" sanıp tekrar yazardı.
        val kitsuRow = MediaEntry(
            id = 1,
            title = "Kaiju No. 8",
            type = MediaType.Anime,
            status = WatchStatus.Completed,
            score = 8,
            progress = 12,
            total = 12,
            source = "kitsu",
            malId = 123_456
        )
        val malRow = kitsuRow.copy(id = 2, source = "mal", malId = 52_578)
        val kitsuIds = CrossProviderIds.decode(
            storedId = kitsuRow.malId,
            source = kitsuRow.source,
            type = kitsuRow.type
        )
        val malIds = CrossProviderIds.decode(
            storedId = malRow.malId,
            source = malRow.source,
            type = malRow.type
        )
        // Kitsu satırında ham kimlik Kitsu uzayında, MAL satırında MAL uzayında; ancak çözülmüş
        // kimlik aynı yapıyı gösterdiğinde anahtar eşleşir.
        assertEquals(
            RemoteListPresence.keyFor(kitsuRow, CrossProviderIds(malId = 52_578, kitsuId = 123_456)),
            RemoteListPresence.keyFor(malRow, CrossProviderIds(malId = 52_578, kitsuId = 123_456))
        )
        assertEquals("MAL kimliği olmayan satır kimlik uzayından anahtar üretir",
            "Anime:kitsu:123456", RemoteListPresence.keyFor(kitsuRow, kitsuIds))
        assertEquals("Anime:mal:52578", RemoteListPresence.keyFor(malRow, malIds))
    }
}
