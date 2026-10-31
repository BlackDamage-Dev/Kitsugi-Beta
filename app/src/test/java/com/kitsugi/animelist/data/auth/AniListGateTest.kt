package com.kitsugi.animelist.data.auth

import okhttp3.Headers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * [AniListGate] kota muhasebesi testleri (v2.4.341).
 *
 * Gerçek saat yerine enjekte edilen bir sayaç kullanılır; böylece 60 sn'lik pencere ve
 * 429 ceza penceresi saniyeler beklemeden doğrulanabilir.
 */
class AniListGateTest {

    private var now = 1_000_000L

    private val okHeaders: Headers = Headers.Builder()
        .add("X-RateLimit-Limit", "30")
        .add("X-RateLimit-Remaining", "20")
        .build()

    @Before
    fun setUp() {
        now = 1_000_000L
        AniListGate.clock = { now }
        AniListGate.resetForTests(now)
    }

    @After
    fun tearDown() {
        AniListGate.clock = { System.currentTimeMillis() }
        AniListGate.resetForTests(System.currentTimeMillis())
    }

    /** Bir isteği "gönderilmiş ve yanıtlanmış" gibi muhasebeye işler. */
    private fun simulateRequest(priority: AniListGate.Priority = AniListGate.Priority.INTERACTIVE) {
        AniListGate.withPermitBlocking(priority = priority, maxWaitMs = 0L) { }
        AniListGate.onResponse(200, okHeaders)
    }

    @Test
    fun `kayan pencere dolunca istek kota hatasi ile ertelenir`() {
        // Limit 30, güvenlik payı 4 → pencere bütçesi 26.
        repeat(26) {
            simulateRequest()
            now += 1_000L
        }

        assertEquals(0, AniListGate.remainingRequests())
        assertEquals(AniListGate.Budget.EXHAUSTED, AniListGate.budget())

        val error = expectQuota {
            AniListGate.withPermitBlocking(priority = AniListGate.Priority.INTERACTIVE, maxWaitMs = 0L) { }
        }
        assertTrue("bekleme süresi pozitif olmalı", error.waitMs > 0L)
        assertTrue(error.waitMs <= 60_000L)
    }

    @Test
    fun `retry-after basligi soguma suresini belirler`() {
        val limited = Headers.Builder()
            .add("X-RateLimit-Limit", "30")
            .add("X-RateLimit-Remaining", "0")
            .add("Retry-After", "42")
            .build()

        AniListGate.onResponse(429, limited)

        val remaining = AniListGate.cooldownRemainingMs()
        assertTrue("42 sn civarı beklenmeliydi, gelen: $remaining", remaining in 41_000L..43_000L)
        assertTrue(AniListGate.isCoolingDown())
        assertEquals(AniListGate.Budget.EXHAUSTED, AniListGate.budget())

        val error = expectQuota {
            AniListGate.withPermitBlocking(priority = AniListGate.Priority.INTERACTIVE, maxWaitMs = 0L) { }
        }
        assertTrue(AniListGate.isQuotaMessage(error.message))
        assertTrue(error.message!!.contains(AniListGate.QUOTA_MARKER))
    }

    @Test
    fun `remaining sifir olan basarili yanit pencere sifirlanana kadar bekletir`() {
        val resetEpochSeconds = (now + 30_000L) / 1_000L
        val exhausted = Headers.Builder()
            .add("X-RateLimit-Limit", "30")
            .add("X-RateLimit-Remaining", "0")
            .add("X-RateLimit-Reset", resetEpochSeconds.toString())
            .build()

        AniListGate.onResponse(200, exhausted)

        val remaining = AniListGate.cooldownRemainingMs()
        assertTrue("reset başlığına göre ~30 sn beklenmeliydi, gelen: $remaining", remaining in 28_000L..31_000L)
    }

    @Test
    fun `arka plan istekleri kullaniciya ayrilmis payi tuketemez`() {
        // 20 arka plan isteği: toplam bütçe (26) dolu değil, arka plan bütçesi (20) doldu.
        repeat(20) {
            simulateRequest(AniListGate.Priority.BACKGROUND)
            now += 1_500L
        }

        val error = expectQuota {
            AniListGate.withPermitBlocking(priority = AniListGate.Priority.BACKGROUND, maxWaitMs = 0L) { }
        }
        assertTrue(error.waitMs > 0L)

        // Kullanıcıya ayrılmış pay hâlâ kullanılabilir olmalı.
        AniListGate.withPermitBlocking(priority = AniListGate.Priority.INTERACTIVE, maxWaitMs = 0L) { }
    }

    @Test
    fun `kota mesaji isareti taninir`() {
        val message = AniListGate.quotaMessage(42_000L)
        assertTrue(message.contains(AniListGate.QUOTA_MARKER))
        assertTrue(message.contains("43 sn"))
        assertTrue(AniListGate.isQuotaMessage(message))
        assertTrue(AniListGate.isQuotaMessage("AniList API hatası: 429 Too Many Requests"))
        assertTrue(AniListGate.isQuotaMessage("HTTP 429 rate limit"))
        assertFalse(AniListGate.isQuotaMessage("HTTP 500"))
        assertFalse(AniListGate.isQuotaMessage(null))
    }

    @Test
    fun `okuma onbellegi ttl sonunda gecersiz olur`() {
        val key = AniListGate.cacheKey("query { Viewer { id } }", "{}", "token")
        AniListGate.putCachedRead(key, "{\"data\":{}}", ttlMs = 1_500L)
        assertNotNull(AniListGate.cachedRead(key))

        now += 1_000L
        assertNotNull(AniListGate.cachedRead(key))

        now += 1_000L
        assertNull(AniListGate.cachedRead(key))
    }

    private fun expectQuota(block: () -> Unit): AniListGate.QuotaExhaustedException {
        try {
            block()
        } catch (quota: AniListGate.QuotaExhaustedException) {
            return quota
        }
        fail("QuotaExhaustedException bekleniyordu")
        throw AssertionError("ulaşılamaz")
    }
}
