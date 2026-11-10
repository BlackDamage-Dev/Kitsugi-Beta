package com.kitsugi.animelist.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Liste yenileme politikasının birim testleri — saf JVM, Android bağımlılığı yok.
 *
 * Politika, "her yenilemede listeyi baştan çekme" şikâyetinin kalbinde durur:
 *  - taze veri için ağa çıkılmaz,
 *  - destekleyen kaynakta fark (delta) çekilir,
 *  - tam uzlaştırma yalnızca periyodik olarak yapılır,
 *  - kullanıcı "baştan yenile" derse politika atlanır.
 */
class ListRefreshPolicyTest {

    private val now = 1_700_000_000_000L
    private val minute = 60_000L
    private val hour = 60 * minute

    private fun state(
        lastSuccessAt: Long = now - 2 * hour,
        lastFullSyncAt: Long = now - 2 * hour,
        lastAttemptAt: Long = now - 2 * hour,
        failureCount: Int = 0
    ) = ListSyncState(
        source = "anilist",
        lastSuccessAt = lastSuccessAt,
        lastFullSyncAt = lastFullSyncAt,
        lastAttemptAt = lastAttemptAt,
        failureCount = failureCount
    )

    @Test
    fun `never synced source always does a full sync`() {
        val plan = ListRefreshPolicy.plan(
            state = ListSyncState(source = "anilist"),
            mode = ListRefreshMode.BACKGROUND,
            supportsDelta = true,
            now = now
        )
        assertEquals(ListRefreshPlan.FULL, plan)
    }

    @Test
    fun `user refresh within fresh window is skipped`() {
        val plan = ListRefreshPolicy.plan(
            state = state(lastSuccessAt = now - 10_000L, lastFullSyncAt = now - 10_000L),
            mode = ListRefreshMode.USER_REFRESH,
            supportsDelta = true,
            now = now
        )
        assertEquals(ListRefreshPlan.SKIP_UP_TO_DATE, plan)
    }

    @Test
    fun `user refresh after fresh window uses delta when supported`() {
        val plan = ListRefreshPolicy.plan(
            state = state(lastSuccessAt = now - 10 * minute, lastFullSyncAt = now - 2 * hour),
            mode = ListRefreshMode.USER_REFRESH,
            supportsDelta = true,
            now = now
        )
        assertEquals(ListRefreshPlan.DELTA, plan)
    }

    @Test
    fun `user refresh falls back to full when source has no delta support`() {
        val plan = ListRefreshPolicy.plan(
            state = state(lastSuccessAt = now - 10 * minute, lastFullSyncAt = now - 2 * hour),
            mode = ListRefreshMode.USER_REFRESH,
            supportsDelta = false,
            now = now
        )
        assertEquals(ListRefreshPlan.FULL, plan)
    }

    @Test
    fun `background refresh inside the fresh window is skipped`() {
        val plan = ListRefreshPolicy.plan(
            state = state(lastSuccessAt = now - 10 * minute, lastFullSyncAt = now - 10 * minute),
            mode = ListRefreshMode.BACKGROUND,
            supportsDelta = true,
            now = now
        )
        assertEquals(ListRefreshPlan.SKIP_UP_TO_DATE, plan)
    }

    @Test
    fun `background refresh of a full-only source waits for its long window`() {
        val plan = ListRefreshPolicy.plan(
            state = state(lastSuccessAt = now - 2 * hour, lastFullSyncAt = now - 2 * hour),
            mode = ListRefreshMode.BACKGROUND,
            supportsDelta = false,
            now = now
        )
        assertEquals(ListRefreshPlan.SKIP_UP_TO_DATE, plan)
    }

    @Test
    fun `background refresh of a full-only source runs after its long window`() {
        val plan = ListRefreshPolicy.plan(
            state = state(lastSuccessAt = now - 7 * hour, lastFullSyncAt = now - 7 * hour),
            mode = ListRefreshMode.BACKGROUND,
            supportsDelta = false,
            now = now
        )
        assertEquals(ListRefreshPlan.FULL, plan)
    }

    @Test
    fun `background refresh after fresh window uses delta`() {
        val plan = ListRefreshPolicy.plan(
            state = state(lastSuccessAt = now - 90 * minute, lastFullSyncAt = now - 90 * minute),
            mode = ListRefreshMode.BACKGROUND,
            supportsDelta = true,
            now = now
        )
        assertEquals(ListRefreshPlan.DELTA, plan)
    }

    @Test
    fun `full reconcile happens once the last full sync is a day old`() {
        val plan = ListRefreshPolicy.plan(
            state = state(lastSuccessAt = now - 2 * hour, lastFullSyncAt = now - 25 * hour),
            mode = ListRefreshMode.USER_REFRESH,
            supportsDelta = true,
            now = now
        )
        assertEquals(ListRefreshPlan.FULL, plan)
    }

    @Test
    fun `delta is not used when last successful sync is too old`() {
        val plan = ListRefreshPolicy.plan(
            state = state(lastSuccessAt = now - 30 * hour, lastFullSyncAt = now - 2 * hour),
            mode = ListRefreshMode.USER_REFRESH,
            supportsDelta = true,
            now = now
        )
        assertEquals(ListRefreshPlan.FULL, plan)
    }

    @Test
    fun `background refresh backs off after repeated failures`() {
        val plan = ListRefreshPolicy.plan(
            state = state(
                lastSuccessAt = now - 2 * hour,
                lastFullSyncAt = now - 2 * hour,
                lastAttemptAt = now - minute,
                failureCount = ListRefreshPolicy.MAX_CONSECUTIVE_FAILURES
            ),
            mode = ListRefreshMode.BACKGROUND,
            supportsDelta = true,
            now = now
        )
        assertEquals(ListRefreshPlan.SKIP_UP_TO_DATE, plan)
    }

    @Test
    fun `user refresh ignores failure backoff`() {
        val plan = ListRefreshPolicy.plan(
            state = state(
                lastSuccessAt = now - 2 * hour,
                lastFullSyncAt = now - 2 * hour,
                lastAttemptAt = now - minute,
                failureCount = 10
            ),
            mode = ListRefreshMode.USER_REFRESH,
            supportsDelta = true,
            now = now
        )
        assertEquals(ListRefreshPlan.DELTA, plan)
    }

    @Test
    fun `force always performs a full sync`() {
        val plan = ListRefreshPolicy.plan(
            state = state(lastSuccessAt = now - 5_000L, lastFullSyncAt = now - 5_000L),
            mode = ListRefreshMode.USER_FORCE,
            supportsDelta = true,
            now = now
        )
        assertEquals(ListRefreshPlan.FULL, plan)
    }

    @Test
    fun `clock skew in the ledger forces a full sync instead of skipping forever`() {
        // Cihaz saati geriye alındı: defter gelecekte bir damga taşıyor. Eskiden
        // `now - lastSuccessAt` negatif olduğu için liste sonsuza kadar "taze" sayılıyordu.
        val future = now + 3 * hour
        val plan = ListRefreshPolicy.plan(
            state = state(lastSuccessAt = future, lastFullSyncAt = future, lastAttemptAt = future),
            mode = ListRefreshMode.USER_REFRESH,
            supportsDelta = true,
            now = now
        )
        assertEquals(ListRefreshPlan.FULL, plan)
    }

    @Test
    fun `small clock skew inside tolerance does not force a full sync`() {
        val slightlyAhead = now + 60_000L
        val plan = ListRefreshPolicy.plan(
            state = state(
                lastSuccessAt = slightlyAhead - 2 * hour,
                lastFullSyncAt = now - 2 * hour,
                lastAttemptAt = now - 2 * hour
            ),
            mode = ListRefreshMode.USER_REFRESH,
            supportsDelta = true,
            now = now
        )
        assertEquals(ListRefreshPlan.DELTA, plan)
    }

    @Test
    fun `delta start timestamp includes a safety margin and never goes below one`() {
        assertEquals(1_000L - 120L, ListRefreshPolicy.deltaSinceEpochSeconds(1_000_000L))
        assertEquals(1L, ListRefreshPolicy.deltaSinceEpochSeconds(0L))
        assertEquals(1L, ListRefreshPolicy.deltaSinceEpochSeconds(60_000L))
    }

    @Test
    fun `sync state round trips through json`() {
        val original = ListSyncState(
            source = "simkl",
            lastSuccessAt = now,
            lastFullSyncAt = now - hour,
            lastAttemptAt = now,
            failureCount = 2,
            remoteRevision = "all=2026-10-23T10:00:00Z|anime={...}",
            lastError = "HTTP 500"
        )
        val restored = ListSyncState.fromJson("simkl", org.json.JSONObject(original.toJson().toString()))
        assertEquals(original, restored)
    }

    @Test
    fun `state without successful sync is not considered synced`() {
        assertFalse(ListSyncState(source = "mal").hasSyncedOnce)
        assertTrue(ListSyncState(source = "mal", lastSuccessAt = 1L).hasSyncedOnce)
    }
}
