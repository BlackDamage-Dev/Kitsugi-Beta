package com.kitsugi.animelist.data.trailer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubePlayerClientsTest {
    @Test
    fun retiredAndroidVrClientIsNotUsed() {
        assertFalse(YouTubePlayerClients.ALL.any { it.key == "android_vr" })
        assertFalse(YouTubePlayerClients.ALL.any { it.context["clientName"] == "ANDROID_VR" })
    }

    @Test
    fun everyClientHasUniqueIdsAndMatchingContextVersion() {
        val clients = YouTubePlayerClients.ALL

        assertEquals(clients.size, clients.map { it.key }.toSet().size)
        assertEquals(clients.size, clients.map { it.id }.toSet().size)
        assertEquals(clients.size, clients.map { it.priority }.toSet().size)
        clients.forEach { client ->
            assertTrue(client.id.isNotBlank())
            assertTrue(client.userAgent.isNotBlank())
            assertEquals(client.version, client.context["clientVersion"])
            assertTrue(client.context["clientName"].toString().isNotBlank())
        }
    }

    @Test
    fun maintainedClientsHaveExpectedInnertubeContracts() {
        val byKey = YouTubePlayerClients.ALL.associateBy { it.key }

        assertEquals("7", byKey.getValue("tv_samsung").id)
        assertEquals("TVHTML5", byKey.getValue("tv_samsung").context["clientName"])
        assertEquals("101", byKey.getValue("visionos").id)
        assertEquals("VISIONOS", byKey.getValue("visionos").context["clientName"])
        assertEquals("1.02", byKey.getValue("visionos").version)
        assertEquals("5", byKey.getValue("ios").id)
        assertEquals("21.26.4", byKey.getValue("ios").version)
        assertEquals("56", byKey.getValue("web_embedded").id)
        assertEquals("WEB_EMBEDDED_PLAYER", byKey.getValue("web_embedded").context["clientName"])
        assertEquals("5.20260707", byKey.getValue("tv_samsung").version)
        assertTrue(byKey.getValue("tv_samsung").userAgent.contains("Tizen 2.4.0"))
    }

    @Test
    fun prioritiesDefineStableMostReliableFirstOrder() {
        assertEquals(
            listOf("tv_samsung", "visionos", "ios", "web_embedded"),
            YouTubePlayerClients.ALL.sortedBy { it.priority }.map { it.key }
        )
    }
}
