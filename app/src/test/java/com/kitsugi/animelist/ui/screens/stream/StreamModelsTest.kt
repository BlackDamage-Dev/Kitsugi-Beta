package com.kitsugi.animelist.ui.screens.stream

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamModelsTest {
    private val states = listOf(
        AddonFetchState(addonName = "⚡ Shared name", addonId = "cs:first"),
        AddonFetchState(addonName = "⚡ Shared name", addonId = "cs:second")
    )

    @Test fun sameDisplayNameStillSelectsTheCorrectPlugin() {
        assertEquals(0, states.indexForAddon("⚡ Shared name", "cs:first"))
        assertEquals(1, states.indexForAddon("⚡ Shared name", "cs:second"))
    }

    @Test fun unknownIdentityNeverFallsBackToAnotherPlugin() {
        assertEquals(-1, states.indexForAddon("⚡ Shared name", "cs:missing"))
    }

    @Test fun nameOnlyMatchesMustBeUnambiguous() {
        assertEquals(-1, states.indexForAddon("⚡ Shared name", null))
        assertEquals(0, states.take(1).indexForAddon("⚡ Shared name", null))
    }
}
