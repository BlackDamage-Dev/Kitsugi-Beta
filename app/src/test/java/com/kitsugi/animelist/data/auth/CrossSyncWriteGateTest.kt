package com.kitsugi.animelist.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Çapraz yazım kapısı: "hangi kaynaktan eklersem ekleyeyim diğer kaynaklara da yazılmalı"
 * şikâyetinin kök nedenini sabitler.
 *
 * Eski davranış: `verifyMalId` `Verified` değilse (yıl alanı boş / katalog okunamadı) HİÇBİR
 * hedefe yazılmıyordu. Yeni davranış: yalnızca katalog **çelişkisi** durdurur; kanıt yokluğunda
 * kararı hedefin kendi resmî kimliği verir.
 */
class CrossSyncWriteGateTest {

    private fun ids(
        malId: Int? = 1,
        aniListId: Int? = null,
        kitsuId: Int? = null,
        shikimoriId: Int? = null,
        bangumiSubjectId: Int? = null,
        malVia: String? = null
    ) = CrossProviderIds(
        malId = malId,
        aniListId = aniListId,
        kitsuId = kitsuId,
        shikimoriId = shikimoriId,
        bangumiSubjectId = bangumiSubjectId,
        via = malVia?.let { mapOf("mal" to it) } ?: emptyMap()
    )

    @Test
    fun malRowWritesToEveryTargetEvenWhenCatalogCouldNotVerify() {
        // Satır MAL'ın kendisi: kimlik kaynağından gelir, çapraz doğrulama gereksiz.
        val identity = ids(malId = 20, malVia = "kayit")
        listOf("anilist", "mal", "simkl", "kitsu", "shikimori", "bangumi").forEach { target ->
            assertTrue(
                "MAL satırı $target hedefine yazılabilmeli",
                CrossSyncPolicy.allowsWrite(target, "mal", identity, verdictBlocked = false)
            )
        }
        assertEquals(CrossSyncPolicy.MalIdOrigin.NATIVE, CrossSyncPolicy.malIdOrigin("mal", identity))
    }

    @Test
    fun catalogContradictionStopsEveryCrossWrite() {
        val identity = ids(malId = 20, malVia = "ARM")
        assertFalse(CrossSyncPolicy.allowsWrite("anilist", "kitsu", identity, verdictBlocked = true))
        assertFalse(CrossSyncPolicy.allowsWrite("shikimori", "kitsu", identity, verdictBlocked = true))
        // Kendi kaynağına yazım da çelişki varsa durur (satırın kimliği yanlış olabilir).
        assertFalse(CrossSyncPolicy.allowsWrite("kitsu", "kitsu", identity, verdictBlocked = true))
    }

    @Test
    fun providerReferenceAllowsEveryTarget() {
        val identity = ids(malId = 20, malVia = "ARM")
        assertEquals(CrossSyncPolicy.MalIdOrigin.REFERENCE, CrossSyncPolicy.malIdOrigin("kitsu", identity))
        listOf("anilist", "mal", "simkl", "kitsu", "shikimori", "bangumi").forEach { target ->
            assertTrue(CrossSyncPolicy.allowsWrite(target, "kitsu", identity, verdictBlocked = false))
        }
    }

    @Test
    fun titleGuessOnlyWritesWhereTargetCanSelfVerify() {
        // MAL kimliği yalnızca başlık aramasıyla tahmin edildi.
        val identity = ids(malId = 20, malVia = "Jikan başlık araması")
        assertEquals(CrossSyncPolicy.MalIdOrigin.GUESS, CrossSyncPolicy.malIdOrigin("bangumi", identity))
        // Kendi kataloğunda birebir teyit edebilen hedefler yazabilir.
        assertTrue(CrossSyncPolicy.allowsWrite("anilist", "bangumi", identity, verdictBlocked = false))
        assertTrue(CrossSyncPolicy.allowsWrite("kitsu", "bangumi", identity, verdictBlocked = false))
        assertTrue(CrossSyncPolicy.allowsWrite("simkl", "bangumi", identity, verdictBlocked = false))
        // Teyit edemeyenler (Shikimori `target_id` = MAL ID, Bangumi başlık araması) yazmaz.
        assertFalse(CrossSyncPolicy.allowsWrite("shikimori", "bangumi", identity, verdictBlocked = false))
        // Kendi kaynağına yazım her zaman serbest.
        assertTrue(CrossSyncPolicy.allowsWrite("bangumi", "bangumi", identity, verdictBlocked = false))
    }

    @Test
    fun unresolvedMalIdLetsEachTargetTryItsOwnCatalog() {
        val identity = ids(malId = null, kitsuId = 900)
        assertTrue(CrossSyncPolicy.allowsWrite("bangumi", "kitsu", identity, verdictBlocked = false))
        assertTrue(CrossSyncPolicy.allowsWrite("anilist", "kitsu", identity, verdictBlocked = false))
    }

    @Test
    fun unknownProvenanceIsTreatedAsGuess() {
        val identity = ids(malId = 20, malVia = null)
        assertEquals(CrossSyncPolicy.MalIdOrigin.GUESS, CrossSyncPolicy.malIdOrigin("anilist", identity))
    }

    @Test
    fun rejectedVerdictIsTheOnlyBlockingVerdict() {
        val noLocalTitle = CrossSyncIdentityGuard.Verdict.Unverifiable("yıl bilinmiyor")
        assertFalse(CrossSyncIdentityGuard.blocksCrossWrite(noLocalTitle))
        assertTrue(
            CrossSyncIdentityGuard.blocksCrossWrite(
                CrossSyncIdentityGuard.Verdict.Rejected("başlık uyuşmuyor", "Remote", 2019)
            )
        )
    }
}
