package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.data.local.MyListIdentityCrosswalk
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end deterministic verification of cross-platform clustering, identity guard,
 * crosswalk ID extraction, and false-merge protection (`E96`–`E101`).
 */
class CrossSyncClusterEngineTest {

    private fun kaijuSixPlatformBatches(): List<Pair<CrossSyncClusterEngine.Slot, List<MediaEntry>>> = listOf(
        // AniList (primary card in user screenshot: AL, Completed, 12/12, 9/10)
        CrossSyncClusterEngine.Slot.ANILIST to listOf(
            MediaEntry(
                id = 0,
                title = "Kaijuu 8-gou",
                titleEnglish = "Kaiju No. 8",
                titleJapanese = "怪獣８号",
                type = MediaType.Anime,
                status = WatchStatus.Completed,
                score = 9,
                progress = 12,
                total = 12,
                source = "anilist",
                malId = 52588,
                year = 2024
            )
        ),
        // MAL
        CrossSyncClusterEngine.Slot.MAL to listOf(
            MediaEntry(
                id = 0,
                title = "Kaijuu 8-gou",
                titleEnglish = "Kaiju No. 8",
                titleJapanese = "怪獣８号",
                type = MediaType.Anime,
                status = WatchStatus.Completed,
                score = 9,
                progress = 12,
                total = 12,
                source = "mal",
                malId = 52588,
                year = 2024
            )
        ),
        // Simkl (often missing year on list items)
        CrossSyncClusterEngine.Slot.SIMKL to listOf(
            MediaEntry(
                id = 0,
                title = "Kaiju No. 8",
                type = MediaType.Anime,
                status = WatchStatus.Completed,
                score = 9,
                progress = 12,
                total = 12,
                source = "simkl",
                malId = 151_972_969,
                year = null
            )
        ),
        // Kitsu (300M + 46298)
        CrossSyncClusterEngine.Slot.KITSU to listOf(
            MediaEntry(
                id = 0,
                title = "Kaiju No. 8",
                titleJapanese = "怪獣8号",
                type = MediaType.Anime,
                status = WatchStatus.Completed,
                score = 9,
                progress = 12,
                total = 12,
                source = "kitsu",
                malId = 300_046_298,
                year = 2024
            )
        ),
        // Shikimori (targetId == MAL ID == 52588, romaji title without English)
        CrossSyncClusterEngine.Slot.SHIKIMORI to listOf(
            MediaEntry(
                id = 0,
                title = "Kaijuu 8-gou",
                type = MediaType.Anime,
                status = WatchStatus.Completed,
                score = 9,
                progress = 12,
                total = 12,
                source = "shikimori",
                malId = 52588,
                year = 2024
            )
        ),
        // Bangumi (500M + 400602, Simplified Chinese display title, null year)
        CrossSyncClusterEngine.Slot.BANGUMI to listOf(
            MediaEntry(
                id = 0,
                title = "怪兽8号",
                type = MediaType.Anime,
                status = WatchStatus.Completed,
                score = 9,
                progress = 12,
                total = 12,
                source = "bangumi",
                malId = 500_400_602,
                year = null
            )
        )
    )

    private fun <T> permutations(items: List<T>): List<List<T>> {
        if (items.size <= 1) return listOf(items)
        val result = ArrayList<List<T>>()
        for (i in items.indices) {
            val head = items[i]
            val rest = items.subList(0, i) + items.subList(i + 1, items.size)
            for (tail in permutations(rest)) {
                result.add(listOf(head) + tail)
            }
        }
        return result
    }

    @Test
    fun kaijuNo8All720PlatformPermutationsMergeIntoSingleSixPlatformCluster() {
        val rawBatches = kaijuSixPlatformBatches()
        val subjectToMal = MyListIdentityCrosswalk.reverseBangumiMapping(
            mapOf("bgm_a_52588" to 400_602)
        )
        val perms = permutations(rawBatches)
        assertEquals(720, perms.size)

        for (perm in perms) {
            val assignedBatches = CrossSyncClusterEngine.assignClusterRowIds(perm)
            val allAssigned = assignedBatches.flatMap { it.second }
            // Verify every row receives a distinct synthetic positive ID even when input id == 0
            assertEquals(6, allAssigned.map { it.id }.toSet().size)

            val extras = allAssigned.associate { entry ->
                entry.id to MyListIdentityCrosswalk.extraKeysFor(entry, null, subjectToMal)
            }.filterValues { it.isNotEmpty() }

            val session = CrossSyncClusterEngine.Session(identityExtras = extras)
            assignedBatches.forEach { (slot, list) ->
                list.forEach { entry -> session.addEntry(slot, entry) }
            }

            val clusters = session.groupsSnapshot()
            assertEquals(
                "Permutation ${perm.map { it.first }} split Kaiju No. 8 into ${clusters.size} clusters!",
                1,
                clusters.size
            )
            val cluster = clusters.single()
            assertEquals(6, cluster.candidates.size)
            assertFalse(cluster.identityReviewRequired)
            assertEquals(
                52588,
                CrossSyncClusterEngine.extractCrosswalkOrCandidateMalId(cluster.candidates, session::keysOf)
            )
        }
    }

    @Test
    fun allowsBulkAdditionUnblocksTrustedAndVerifiedWhileBlockingRejectedAndUnverifiable() {
        // E96: Native MAL clusters return Verdict.Trusted, which previously blocked 100% of cross-account additions!
        assertTrue(
            CrossSyncIdentityGuard.allowsBulkAddition(
                CrossSyncIdentityGuard.Verdict.Trusted("native MAL source")
            )
        )
        assertTrue(
            CrossSyncIdentityGuard.allowsBulkAddition(
                CrossSyncIdentityGuard.Verdict.Verified("Kaijuu 8-gou", 2024, listOf("Kaiju No. 8"))
            )
        )
        assertTrue(
            CrossSyncIdentityGuard.allowsBulkAddition(
                CrossSyncIdentityGuard.Verdict.Unverifiable("offline")
            )
        )
        assertFalse(
            CrossSyncIdentityGuard.allowsBulkAddition(
                CrossSyncIdentityGuard.Verdict.Rejected("mismatch", "Other Show", 2019)
            )
        )
        assertTrue(CrossSyncIdentityGuard.allowsBulkAddition(null))
    }

    @Test
    fun bridgeEntryCoalescesPreviouslyDisjointProviderClusters() {
        // Bangumi (pure Japanese) and Kitsu (English only, no crosswalk yet) start in separate clusters;
        // AniList arrives third carrying both English ("Kaiju No. 8") and Japanese ("怪獣８号") aliases.
        val bangumi = MediaEntry(
            id = 1,
            title = "怪獣8号",
            type = MediaType.Anime,
            status = WatchStatus.Completed,
            score = 9,
            progress = 12,
            total = 12,
            source = "bangumi",
            malId = 500_400_602,
            year = null
        )
        val kitsu = MediaEntry(
            id = 2,
            title = "Kaiju No. 8",
            type = MediaType.Anime,
            status = WatchStatus.Completed,
            score = 9,
            progress = 12,
            total = 12,
            source = "kitsu",
            malId = 300_046_298,
            year = 2024
        )
        val anilistBridge = MediaEntry(
            id = 3,
            title = "Kaijuu 8-gou",
            titleEnglish = "Kaiju No. 8",
            titleJapanese = "怪獣８号",
            type = MediaType.Anime,
            status = WatchStatus.Completed,
            score = 9,
            progress = 12,
            total = 12,
            source = "anilist",
            malId = 52588,
            year = 2024
        )

        val session = CrossSyncClusterEngine.Session()
        session.addEntry(CrossSyncClusterEngine.Slot.BANGUMI, bangumi)
        session.addEntry(CrossSyncClusterEngine.Slot.KITSU, kitsu)
        session.addEntry(CrossSyncClusterEngine.Slot.ANILIST, anilistBridge)

        val clusters = session.groupsSnapshot()
        assertEquals(1, clusters.size)
        assertEquals(
            setOf("bangumi", "kitsu", "anilist"),
            clusters.single().candidates.map { it.source }.toSet()
        )
    }

    @Test
    fun remakeWithDifferentYearNeverMergesEvenAcrossBridge() {
        val hxh1999 = MediaEntry(
            id = 1,
            title = "Hunter x Hunter",
            type = MediaType.Anime,
            status = WatchStatus.Completed,
            score = 8,
            progress = 62,
            total = 62,
            source = "mal",
            malId = 136,
            year = 1999
        )
        val hxh2011 = MediaEntry(
            id = 2,
            title = "Hunter x Hunter",
            type = MediaType.Anime,
            status = WatchStatus.Completed,
            score = 9,
            progress = 148,
            total = 148,
            source = "kitsu",
            malId = 300_006_448,
            year = 2011
        )

        val session = CrossSyncClusterEngine.Session()
        session.addEntry(CrossSyncClusterEngine.Slot.MAL, hxh1999)
        session.addEntry(CrossSyncClusterEngine.Slot.KITSU, hxh2011)

        assertEquals(2, session.groupsSnapshot().size)
        assertFalse(CrossSyncIdentityGuard.clusterYearsCompatible(hxh1999.year, hxh2011.year))
    }

    @Test
    fun suspiciousSharedIdCandidateWithUnrelatedTitleIsDetectedAndMarkedForReview() {
        val kaijuAl = MediaEntry(
            id = 1,
            title = "Kaijuu 8-gou",
            titleEnglish = "Kaiju No. 8",
            type = MediaType.Anime,
            status = WatchStatus.Completed,
            score = 9,
            progress = 12,
            total = 12,
            source = "anilist",
            malId = 52588,
            year = 2024
        )
        val poisonedEntry = MediaEntry(
            id = 2,
            title = "Unrelated Mystery Show",
            type = MediaType.Anime,
            status = WatchStatus.Watching,
            score = null,
            progress = 3,
            total = 50,
            source = "shikimori",
            malId = 52588,
            year = 2019
        )

        val suspicious = CrossSyncIdentityGuard.findSuspiciousSharedIdCandidates(
            existingCandidates = listOf(kaijuAl),
            incoming = poisonedEntry,
            sharedIdCandidates = listOf(kaijuAl)
        )
        assertTrue(suspicious.isNotEmpty())

        val session = CrossSyncClusterEngine.Session()
        session.addEntry(CrossSyncClusterEngine.Slot.ANILIST, kaijuAl)
        session.addEntry(CrossSyncClusterEngine.Slot.SHIKIMORI, poisonedEntry)
        val reviewedGroup = session.groupsSnapshot().single()
        assertTrue(reviewedGroup.identityReviewRequired)
        assertEquals(setOf(poisonedEntry.id), reviewedGroup.identityReviewEntryIds)
        assertEquals(listOf(kaijuAl), reviewedGroup.actionableCandidates)

        // Şüpheli satır sonradan gelen gerçek eşleşme için köprü olamaz; güvenli çekirdek
        // yine de büyür ve bütün küme yazma dışı kalmaz.
        val verifiedMal = kaijuAl.copy(id = 3, source = "mal")
        session.addEntry(CrossSyncClusterEngine.Slot.MAL, verifiedMal)
        val scopedGroup = session.groupsSnapshot().single()
        assertEquals(setOf(poisonedEntry.id), scopedGroup.identityReviewEntryIds)
        assertEquals(setOf(kaijuAl.id, verifiedMal.id), scopedGroup.actionableCandidates.map { it.id }.toSet())
        assertFalse(scopedGroup.isIdentityReviewed(verifiedMal))
        assertTrue(scopedGroup.isIdentityReviewed(poisonedEntry))
    }

    @Test
    fun structuralTokenGouPreventsNumberedSequelCollision() {
        assertFalse(CrossSyncIdentityGuard.titlesVariantRelated("Kaijuu 8-gou", "Kaijuu 9-gou"))
        assertFalse(CrossSyncIdentityGuard.titlesVariantRelated("Kaijuu 8-gou", "Kaijuu 8-gou 2nd Season"))
        assertTrue(CrossSyncIdentityGuard.titlesVariantRelated("Kaijuu 8-gou", "Kaijuu 8-gou"))
    }
}
