package com.kitsugi.animelist.core.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesCacheTest {

    @Test
    fun `cache is reused for the same app version until ttl expires`() {
        val fetchedAt = 1_000_000L
        val currentVersion = "2.4.347-beta.29878010"

        assertTrue(
            ReleaseNotesCache.isEntryFresh(
                fetchedAtMs = fetchedAt,
                nowMs = fetchedAt + ReleaseNotesCache.TTL_MS,
                cachedForAppVersion = currentVersion,
                currentAppVersion = currentVersion
            )
        )
        assertFalse(
            ReleaseNotesCache.isEntryFresh(
                fetchedAtMs = fetchedAt,
                nowMs = fetchedAt + ReleaseNotesCache.TTL_MS + 1,
                cachedForAppVersion = currentVersion,
                currentAppVersion = currentVersion
            )
        )
    }

    @Test
    fun `cache from a previous app version is rejected after upgrade`() {
        assertFalse(
            ReleaseNotesCache.isEntryFresh(
                fetchedAtMs = 1_000_000L,
                nowMs = 1_000_001L,
                cachedForAppVersion = "2.4.169-beta.29700000",
                currentAppVersion = "2.4.347-beta.29878010"
            )
        )
    }

    @Test
    fun `future cache timestamp is rejected`() {
        assertFalse(
            ReleaseNotesCache.isEntryFresh(
                fetchedAtMs = 2_000L,
                nowMs = 1_000L,
                cachedForAppVersion = "2.4.347-beta.29878010",
                currentAppVersion = "2.4.347-beta.29878010"
            )
        )
    }

    @Test
    fun `missing app version marker invalidates legacy cache`() {
        assertFalse(
            ReleaseNotesCache.isEntryFresh(
                fetchedAtMs = 1_000L,
                nowMs = 1_001L,
                cachedForAppVersion = null,
                currentAppVersion = "2.4.347-beta.29878010"
            )
        )
    }
}
