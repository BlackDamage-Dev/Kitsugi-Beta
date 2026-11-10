package com.kitsugi.animelist.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Puan ölçeği sınırları (T07/T08 kod düzeyi).
 *
 * A10: her hedefin API aralığı farklıdır ve aralık dışı değer 400 üretir:
 * - **AniList** `scoreRaw` 0..100 (profil biçiminden bağımsız).
 * - **Kitsu** `ratingTwenty` 2..20; 0 ve 1 reddedilir. Boş puan 0/1'e KELEPÇELENEMEZ —
 *   dönüşüm `null` üretir ve yazıcı bunu "temizleme" olarak gönderir. (Yazıcının HTTP
 *   gövdesi burada değil, `KitsuSyncManager`da; bu dosya dönüşüm sözleşmesini kilitler.)
 * - **Bangumi** `rate` 0..10; ama 0 pratikte "puan yok"tur ve koleksiyon gövdesine
 *   konmaz (E41 bilinçli kısıtı: Bangumi'de puan temizleme yapılmaz). Dönüşüm boş/
 *   sıfır/negatif için `null` üretir, 100'lük ölçekten sızan taşmaları 1..10'a kelepçeler.
 *
 * Çalışma-zamanı (rate=0'ın sunucuda 400 mü döndüğü) bu dosyanın konusu değildir;
 * T07'nin o yarısı gerçek API çağrısıyla doğrulanır.
 */
class ProviderScoreBoundaryTest {

    // ── AniList (scoreRaw 0..100) ───────────────────────────────────────────────

    @Test
    fun aniListRawScalesTenPointToHundred() {
        assertEquals(0, SyncScores.aniListRaw(null))
        assertEquals(0, SyncScores.aniListRaw(0))
        assertEquals(10, SyncScores.aniListRaw(1))
        assertEquals(70, SyncScores.aniListRaw(7))
        assertEquals(100, SyncScores.aniListRaw(10))
    }

    @Test
    fun aniListRawClampsOutOfRange() {
        assertEquals(0, SyncScores.aniListRaw(-3))
        assertEquals(100, SyncScores.aniListRaw(15))
    }

    // ── 10'luk ölçek (MAL / Shikimori / Simkl) ──────────────────────────────────

    @Test
    fun tenPointTreatsZeroAndNullAsUnscored() {
        assertNull(SyncScores.tenPoint(null))
        assertNull(SyncScores.tenPoint(0))
        assertNull(SyncScores.tenPoint(-5))
    }

    @Test
    fun tenPointKeepsLegalValuesAndScalesHundredPointLeaks() {
        assertEquals(1, SyncScores.tenPoint(1))
        assertEquals(7, SyncScores.tenPoint(7))
        assertEquals(10, SyncScores.tenPoint(10))
        assertEquals(1, SyncScores.tenPoint(11))
        assertEquals(5, SyncScores.tenPoint(50))
        assertEquals(8, SyncScores.tenPoint(84))
        assertEquals(9, SyncScores.tenPoint(86))
        assertEquals(10, SyncScores.tenPoint(100))
        assertEquals(10, SyncScores.tenPoint(999))
    }

    // ── Kitsu (ratingTwenty 2..20; T08) ─────────────────────────────────────────

    @Test
    fun kitsuTwentyNeverProducesZeroOrOne() {
        assertNull(SyncScores.kitsuTwenty(null))
        assertNull(SyncScores.kitsuTwenty(0))
        assertEquals(2, SyncScores.kitsuTwenty(1))
        assertEquals(10, SyncScores.kitsuTwenty(5))
        assertEquals(20, SyncScores.kitsuTwenty(10))
        assertEquals(10, SyncScores.kitsuTwenty(50))
    }

    @Test
    fun kitsuTwentyStaysInsideEveryLegalBand() {
        for (raw in -20..220) {
            val value = SyncScores.kitsuTwenty(raw)
            assertTrue(
                "kitsuTwenty($raw)=$value, 2..20 aralığı dışında",
                value == null || value in 2..20
            )
        }
    }

    // ── Bangumi (rate 1..10 gövdede; T07) ───────────────────────────────────────

    @Test
    fun bangumiRateTreatsEmptyAsAbsent() {
        assertNull(BangumiSyncManager.toBangumiRate(null))
        assertNull(BangumiSyncManager.toBangumiRate(0))
        assertNull(BangumiSyncManager.toBangumiRate(-3))
    }

    @Test
    fun bangumiRateKeepsLegalAndScalesOverflow() {
        assertEquals(1, BangumiSyncManager.toBangumiRate(1))
        assertEquals(8, BangumiSyncManager.toBangumiRate(8))
        assertEquals(10, BangumiSyncManager.toBangumiRate(10))
        assertEquals(1, BangumiSyncManager.toBangumiRate(11))
        assertEquals(8, BangumiSyncManager.toBangumiRate(75))
        assertEquals(10, BangumiSyncManager.toBangumiRate(95))
        assertEquals(10, BangumiSyncManager.toBangumiRate(250))
    }

    @Test
    fun bangumiRateStaysInsideLegalBand() {
        for (raw in -50..300) {
            val value = BangumiSyncManager.toBangumiRate(raw)
            assertTrue(
                "toBangumiRate($raw)=$value, 1..10 aralığı dışında",
                value == null || value in 1..10
            )
        }
    }
}
