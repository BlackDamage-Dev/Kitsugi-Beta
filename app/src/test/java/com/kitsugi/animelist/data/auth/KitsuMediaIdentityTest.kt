package com.kitsugi.animelist.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KitsuMediaIdentityTest {
    @Test
    fun parserKeepsOnlyMalMappingsLinkedToTheExactKitsuMedia() {
        val body = """{
            "data": {
                "id": "42",
                "type": "anime",
                "attributes": {
                    "canonicalTitle": "Example Anime",
                    "titles": {"en": "Example Anime"},
                    "startDate": "2021-04-01"
                },
                "relationships": {
                    "mappings": {"data": [{"type": "mappings", "id": "900"}, {"type": "mappings", "id": "902"}]}
                }
            },
            "included": [
                {
                    "id": "900",
                    "type": "mappings",
                    "attributes": {"externalSite": "myanimelist/anime", "externalId": "12345"},
                    "relationships": {"item": {"data": {"id": "42", "type": "anime"}}}
                },
                {
                    "id": "902",
                    "type": "mappings",
                    "attributes": {"externalSite": "myanimelist/anime", "externalId": "77777"},
                    "relationships": {"item": {"data": {"id": "99", "type": "anime"}}}
                },
                {
                    "id": "901",
                    "type": "mappings",
                    "attributes": {"externalSite": "myanimelist/anime", "externalId": "54321"},
                    "relationships": {"item": {"data": {"id": "99", "type": "anime"}}}
                }
            ]
        }"""

        val identity = KitsuApiClient.parseKitsuMediaIdentity(body, 42, isAnime = true)

        assertNotNull(identity)
        assertEquals(setOf(12345), identity?.malIds)
        assertEquals(2021, identity?.year)
    }

    @Test
    fun parserDoesNotPromoteWrongTypeOrUnlinkedMappingsToMalIdentity() {
        val body = """{
            "data": {
                "id": "42",
                "type": "anime",
                "attributes": {"canonicalTitle": "Example Anime", "startDate": "2021-04-01"},
                "relationships": {"mappings": {"data": []}}
            },
            "included": [
                {
                    "id": "900",
                    "type": "mappings",
                    "attributes": {"externalSite": "myanimelist/manga", "externalId": "12345"},
                    "relationships": {"item": {"data": {"id": "42", "type": "anime"}}}
                },
                {
                    "id": "901",
                    "type": "mappings",
                    "attributes": {"externalSite": "myanimelist/anime", "externalId": "54321"},
                    "relationships": {"item": {"data": {"id": "99", "type": "anime"}}}
                }
            ]
        }"""

        val identity = KitsuApiClient.parseKitsuMediaIdentity(body, 42, isAnime = true)

        assertNotNull(identity)
        assertTrue(identity?.malIds.isNullOrEmpty())
        assertNull(KitsuApiClient.parseKitsuMediaIdentity(body, 99, isAnime = true))
        assertNull(KitsuApiClient.parseKitsuMediaIdentity(body, 42, isAnime = false))
    }

    @Test
    fun parserExtractsAniListAnimeMappingAndUsEnglishFallbackTitle() {
        val body = """{
            "data": {
                "id": "49001",
                "type": "anime",
                "attributes": {
                    "canonicalTitle": "Kaijuu 8-gou 2nd Season",
                    "titles": {"en_us": "Kaiju No. 8 Season 2", "ja_jp": "怪獣8号 第2期"},
                    "startDate": "2025-07-19"
                },
                "relationships": {
                    "mappings": {"data": [{"type": "mappings", "id": "501"}, {"type": "mappings", "id": "502"}]}
                }
            },
            "included": [
                {
                    "id": "501",
                    "type": "mappings",
                    "attributes": {"externalSite": "myanimelist/anime", "externalId": "59177"},
                    "relationships": {"item": {"data": {"id": "49001", "type": "anime"}}}
                },
                {
                    "id": "502",
                    "type": "mappings",
                    "attributes": {"externalSite": "anilist/anime", "externalId": "178754"},
                    "relationships": {"item": {"data": {"id": "49001", "type": "anime"}}}
                }
            ]
        }"""

        val identity = KitsuApiClient.parseKitsuMediaIdentity(body, 49001, isAnime = true)

        assertNotNull(identity)
        assertEquals(setOf(59177), identity?.malIds)
        assertEquals(setOf(178754), identity?.aniListIds)
        assertEquals("Kaiju No. 8 Season 2", identity?.titleEnglish)
        assertEquals("怪獣8号 第2期", identity?.titleJapanese)
    }
}
