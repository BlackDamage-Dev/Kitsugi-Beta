package com.kitsugi.animelist.data.cloudstream

import org.junit.Assert.assertEquals
import org.junit.Test

class AniziumAuthHelperTest {
    @Test
    fun cfControlEncodingMatchesTheAuditedV25DexFixture() {
        val payload = "{\"4ndqbd\":1755783986427}"
        val encoded = AniziumAuthHelper.encodeCfControlPayload(payload, weekday = "thursday")

        assertEquals("134e4c040840015655080906544d030f004b4c075b54500f", encoded)
    }
}
