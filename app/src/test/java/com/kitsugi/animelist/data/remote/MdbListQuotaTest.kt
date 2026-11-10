package com.kitsugi.animelist.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kota modelinin saf hesaplarını doğrular. Ağ yok, Android bağlamı yok.
 */
class MdbListQuotaTest {

    @Test
    fun exhaustedQuotaStaysExhaustedUntilResetInstant() {
        val quota = MdbListQuota(
            limit = 1000,
            remaining = 0,
            resetAtEpochSeconds = 1_700_000_000L
        )

        val beforeReset = quota.rolledOver(nowMillis = 1_699_999_000_000L)
        assertTrue(beforeReset.isExhausted)

        val afterReset = quota.rolledOver(nowMillis = 1_700_000_000_000L)
        assertEquals(1000, afterReset.remaining ?: -1)
        assertFalse(afterReset.isExhausted)
        assertTrue(afterReset.isKnown)
    }

    @Test
    fun rolloverKeepsUnknownValuesUnknownInsteadOfInventingThem() {
        // Kalan sayısı hiç bilinmiyorsa sıfırlanma anı geçse bile uydurma bir değer yazılmaz.
        val quota = MdbListQuota(limit = 1000, remaining = null, resetAtEpochSeconds = 1_700_000_000L)
        assertNull(quota.rolledOver(nowMillis = 1_700_100_000_000L).remaining)

        // Reset başlığı yoksa da dokunulmaz.
        val noReset = MdbListQuota(limit = 1000, remaining = 0, resetAtEpochSeconds = null)
        assertEquals(0, noReset.rolledOver(nowMillis = 1_800_000_000_000L).remaining ?: -1)
    }

    @Test
    fun remainingFractionAndLowFlagFollowTheActualLimit() {
        val paid = MdbListQuota(limit = 25000, remaining = 12500)
        assertEquals(0.5, (paid.remainingFraction ?: -1f).toDouble(), 0.001)
        assertFalse(paid.isLow)

        val almostGone = MdbListQuota(limit = 1000, remaining = 40)
        assertTrue(almostGone.isLow)
        assertFalse(almostGone.isExhausted)

        val gone = MdbListQuota(limit = 1000, remaining = 0)
        assertTrue(gone.isExhausted)
        assertFalse(gone.isLow)
    }

    @Test
    fun nextDailyResetIsAlwaysAUtcMidnightInTheFuture() {
        val now = 1_700_000_000_000L
        val next = MdbListQuota.nextDailyResetEpochSeconds(now)

        assertTrue(next * 1000L > now)
        assertTrue(next * 1000L - now <= 24L * 60L * 60L * 1000L)
        assertEquals(0L, next % (24L * 60L * 60L))
    }

    @Test
    fun quotaWithoutAnyLimitIsNotReportedAsKnown() {
        val empty = MdbListQuota()
        assertFalse(empty.isKnown)
        assertFalse(empty.hasLimit)
        assertNull(empty.remainingFraction)
        assertFalse(empty.isExhausted)
    }
}
