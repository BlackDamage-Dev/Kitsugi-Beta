package com.kitsugi.animelist.data.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BangumiKnownMalIdentityTest {

    @Test
    fun crossSyncRealMalIdIsNotMisreadAsLegacyBangumiSubjectId() {
        assertFalse(
            BangumiSyncManager.isLegacyRawBangumiStoredId(
                source = "bangumi",
                storedId = 38186,
                knownMalId = 38186
            )
        )
    }

    @Test
    fun actualLegacyBangumiRowKeepsStrictSubjectValidationPath() {
        assertTrue(
            BangumiSyncManager.isLegacyRawBangumiStoredId(
                source = "bangumi",
                storedId = 257369,
                knownMalId = null
            )
        )
        assertFalse(
            BangumiSyncManager.isLegacyRawBangumiStoredId(
                source = "kitsu",
                storedId = 38186,
                knownMalId = null
            )
        )
    }
}
