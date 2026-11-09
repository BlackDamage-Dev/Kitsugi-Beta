package com.kitsugi.animelist.core.download

import org.junit.Assert.*
import org.junit.Test
import java.net.UnknownHostException
import kotlin.random.Random

/**
 * T-IND-1 (2026-11-09): indirme dayanıklılık çekirdeğinin JVM birim testleri.
 * Android bağımlılığı yok — CI'da (unit test task) koşar.
 */
class DownloadResilienceTest {

    // ─── classify ───────────────────────────────────────────────────────────────

    @Test
    fun dnsErrorFromUnknownHostCause() {
        val root = UnknownHostException("Unable to resolve host \"srv8.cdnimages936.cyou\"")
        val wrapped = java.io.IOException("segment alınamadı", root)
        assertEquals(DownloadResilience.ErrorKind.DNS, DownloadResilience.classify(wrapped))
    }

    @Test
    fun dnsErrorFromMessageOnly() {
        val e = Exception("Unable to resolve host \"srv8.cdnimages1762.cyou\": No address associated with hostname")
        assertEquals(DownloadResilience.ErrorKind.DNS, DownloadResilience.classify(e))
    }

    @Test
    fun rateLimitFromHttpException() {
        val e = DownloadResilience.SegmentHttpException(429, 12_000L, "Failed to download segment: 429")
        assertEquals(DownloadResilience.ErrorKind.RATE_LIMIT, DownloadResilience.classify(e))
    }

    @Test
    fun staleUrlFrom404() {
        val e = DownloadResilience.SegmentHttpException(404, null, "playlist: 404")
        assertEquals(DownloadResilience.ErrorKind.STALE_URL, DownloadResilience.classify(e))
    }

    @Test
    fun serverFrom503() {
        val e = DownloadResilience.SegmentHttpException(503, null, "segment: 503")
        assertEquals(DownloadResilience.ErrorKind.SERVER, DownloadResilience.classify(e))
    }

    @Test
    fun worthReResolvingMatrix() {
        assertTrue(DownloadResilience.worthReResolving(DownloadResilience.ErrorKind.DNS))
        assertTrue(DownloadResilience.worthReResolving(DownloadResilience.ErrorKind.STALE_URL))
        assertTrue(DownloadResilience.worthReResolving(DownloadResilience.ErrorKind.RATE_LIMIT))
        assertFalse(DownloadResilience.worthReResolving(DownloadResilience.ErrorKind.FORBIDDEN))
        assertFalse(DownloadResilience.worthReResolving(DownloadResilience.ErrorKind.OTHER))
    }

    // ─── policyFor ──────────────────────────────────────────────────────────────

    @Test
    fun trCdnPolicyBySourceName() {
        val p = DownloadResilience.policyFor("HDFilmCehennemi", "https://srv8.cdnimages936.cyou/hls/x/master.txt")
        assertEquals("TR_CDN", p.name)
        assertTrue(p.minSegmentIntervalMs > 0)
        assertTrue(p.allowReResolve)
    }

    @Test
    fun trCdnPolicyByHostOnly() {
        val p = DownloadResilience.policyFor(null, "https://s12.rapidrame.com/hls2/a/b/master.m3u8?t=1")
        assertEquals("TR_CDN", p.name)
    }

    @Test
    fun defaultPolicyForGenericHost() {
        val p = DownloadResilience.policyFor("AniStream", "https://cdn.example.com/v/123.m3u8")
        assertEquals("DEFAULT", p.name)
        assertEquals(0L, p.minSegmentIntervalMs)
    }

    // ─── backoff ────────────────────────────────────────────────────────────────

    @Test
    fun backoffGrowsAndCaps() {
        val policy = DownloadResilience.POLICY_TR_CDN
        val fixed = Random(42)
        val b1 = DownloadResilience.backoffMs(1, policy, fixed)
        val b3 = DownloadResilience.backoffMs(3, policy, fixed)
        val b9 = DownloadResilience.backoffMs(9, policy, fixed)
        assertTrue("ilk deneme tabana yakın olmalı (${b1}ms)", b1 in (policy.backoffBaseMs * 0.75).toLong()..(policy.backoffBaseMs * 1.25).toLong())
        assertTrue("üçüncü deneme daha büyük olmalı", b3 > b1)
        assertTrue("tavan aşılmamalı (${b9}ms)", b9 <= policy.backoffCapMs)
    }

    // ─── Retry-After ────────────────────────────────────────────────────────────

    @Test
    fun retryAfterSecondsParsed() {
        assertEquals(7_000L, DownloadResilience.parseRetryAfterMs("7"))
    }

    @Test
    fun retryAfterGarbageNull() {
        assertNull(DownloadResilience.parseRetryAfterMs("soon"))
        assertNull(DownloadResilience.parseRetryAfterMs(null))
    }

    // ─── reconcileResumeIndex ───────────────────────────────────────────────────

    @Test
    fun samePathsDifferentHostKeepsIndex() {
        val old = listOf(
            "https://srv8.cdnimages936.cyou/hls/film/seg0.ts",
            "https://srv8.cdnimages936.cyou/hls/film/seg1.ts",
            "https://srv8.cdnimages936.cyou/hls/film/seg2.ts"
        )
        val new = listOf(
            "https://srv11.cdnimages2048.cyou/hls/film/seg0.ts",
            "https://srv11.cdnimages2048.cyou/hls/film/seg1.ts",
            "https://srv11.cdnimages2048.cyou/hls/film/seg2.ts"
        )
        assertEquals(2, DownloadResilience.reconcileResumeIndex(old, new, 2))
    }

    @Test
    fun differentPathsRestart() {
        val old = listOf("https://a/hls/film/seg0.ts", "https://a/hls/film/seg1.ts")
        val new = listOf("https://b/hls/baska/seg0.ts", "https://b/hls/baska/seg1.ts")
        assertEquals(0, DownloadResilience.reconcileResumeIndex(old, new, 1))
    }

    @Test
    fun differentSizeRestart() {
        val old = listOf("https://a/s0.ts", "https://a/s1.ts", "https://a/s2.ts")
        val new = listOf("https://b/s0.ts", "https://b/s1.ts")
        assertEquals(0, DownloadResilience.reconcileResumeIndex(old, new, 2))
    }

    @Test
    fun zeroIndexStaysZero() {
        val old = listOf("https://a/s0.ts")
        val new = listOf("https://a/s0.ts")
        assertEquals(0, DownloadResilience.reconcileResumeIndex(old, new, 0))
    }

    // ─── segmentPathKey ─────────────────────────────────────────────────────────

    @Test
    fun pathKeyIgnoresHostAndQuery() {
        assertEquals(
            DownloadResilience.segmentPathKey("https://srv8.cdnimages936.cyou/hls/f/seg1.ts?v=1"),
            DownloadResilience.segmentPathKey("https://srv9.cdnimages1762.cyou/hls/f/seg1.ts?v=2")
        )
    }
}
