package com.kitsugi.animelist.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BangumiSubjectLookupPolicyTest {
    @Test
    fun definitiveMissExpiresAndDoesNotApplyToDifferentAuthVisibility() {
        val now = 1_000_000L
        assertTrue(BangumiSubjectLookupPolicy.isFreshMiss(now, now + 1_000))
        assertFalse(BangumiSubjectLookupPolicy.isFreshMiss(now, now + BangumiSubjectLookupPolicy.MISS_TTL_MS))
        assertFalse(BangumiSubjectLookupPolicy.isFreshMiss(null, now))
        assertNotEquals(
            BangumiSubjectLookupPolicy.missKey(42, null),
            BangumiSubjectLookupPolicy.missKey(42, "token")
        )
    }
}
