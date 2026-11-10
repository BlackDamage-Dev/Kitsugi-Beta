package com.kitsugi.animelist.data.cloudstream

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class CsPluginSearchFailureTest {
    @After
    fun cleanup() = CsPluginStatusTracker.clear()

    @Test
    fun failedSearchVariantsKeepDiagnosticsButDoNotBlockOtherAliases() {
        repeat(6) { CsPluginStatusTracker.recordSearchFailure("provider", NullPointerException("query failed")) }
        assertFalse(CsPluginStatusTracker.isBlocked("provider"))
        assertNotNull(CsPluginStatusTracker.getErrorMessage("provider"))
        assertNotNull(CsPluginStatusTracker.getErrorCode("provider"))
    }

    @Test
    fun nonSearchFailuresStillUseCircuitBreaker() {
        repeat(3) { CsPluginStatusTracker.recordFailure("provider", NullPointerException("load failed")) }
        assertTrue(CsPluginStatusTracker.isBlocked("provider"))
        CsPluginStatusTracker.clearPluginStatus("provider")
        assertFalse(CsPluginStatusTracker.isBlocked("provider"))
    }
}
