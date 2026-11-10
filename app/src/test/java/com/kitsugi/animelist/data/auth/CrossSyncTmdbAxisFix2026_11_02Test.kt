package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.data.local.MyListIdentityCrosswalk
import com.kitsugi.animelist.data.remote.BangumiIdNamespace
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaIdentity
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import com.kitsugi.animelist.ui.screens.mylist.groupMyListEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-11-02 cross-sync düzeltmelerinin geriye dönük (regression) testleri:
 *
 *  1. Film/dizi satırlarında TMDB kimliği yalnızca `malId` alanında taşınıyorsa (eski kayıtlar)
 *     `decode` onu TMDB kimliği olarak okumalı — aksi halde TMDB ekseni kimliksiz kalır.
 *  2. Bangumi aramasında yalnızca yıl tutan tek aday ARTIK kabul edilmez (yanlış eşleşme).
 *  3. Kümeleme (groupMyListEntries / CrossSyncCandidateIndex) crosswalk ek anahtarlarını
 *     kullanmalı; çakışan kimlikler hâlâ ayrı grupta kalmalı.
 *  4. `extraKeysFor` satırın KENDİ kimlik uzayını asla ezmez.
 */
class CrossSyncTmdbAxisFix2026_11_02Test {

    private fun movie(
        id: Int, source: String, malId: Int?, tmdbId: Int? = null, simklId: Int? = null,
        title: String = "Fight Club", year: Int? = 1999
    ) = MediaEntry(
        id = id, title = title, type = MediaType.Movie, status = WatchStatus.Watching,
        score = null, progress = 0, total = 1, source = source, malId = malId,
        tmdbId = tmdbId, simklId = simklId, year = year
    )

    // ── 1. decode: TMDB ekseni ──────────────────────────────────────────────

    @Test
    fun legacyVideoRowDecodesTmdbIdFromMalIdField() {
        val ids = CrossProviderIds.decode(storedId = 603, source = "simkl", type = MediaType.Movie)
        assertEquals(603, ids.tmdbId)
        assertNull("film/dizi satırında malId MAL ID değildir", ids.malId)
    }

    @Test
    fun entryTmdbIdWinsOverMalIdFieldOnVideoRows() {
        val ids = CrossProviderIds.decode(
            storedId = 999, source = "simkl", type = MediaType.Movie, entryTmdbId = 603
        )
        assertEquals(603, ids.tmdbId)
    }

    @Test
    fun bangumiVideoRowKeepsSubjectIdAndExposesNoMalId() {
        val ids = CrossProviderIds.decode(storedId = 500_008_291, source = "bangumi", type = MediaType.Movie)
        assertEquals(8291, ids.bangumiSubjectId)
        assertNull(ids.malId)
        assertNull(ids.tmdbId)
    }

    @Test
    fun animeDecodeBehaviorIsUnchanged() {
        assertEquals(21, CrossProviderIds.decode(storedId = 21, source = "mal", type = MediaType.Anime).malId)
        val shiki = CrossProviderIds.decode(storedId = 6789, source = "shikimori", type = MediaType.Anime)
        assertEquals(6789, shiki.shikimoriId)
        assertNull(shiki.malId)
        val al = CrossProviderIds.decode(storedId = 100_057_123, source = "anilist", type = MediaType.Anime)
        assertEquals(57_123, al.aniListId)
        assertNull(al.malId)
    }

    // ── 2. Bangumi arama kabul kuralı (yanlış eşleşme engeli) ────────────────

    private fun subject(
        id: Int, name: String, nameCn: String = "", date: String? = "1999-10-15",
        type: Int = BangumiApiClient.SubjectType.REAL
    ) = BangumiApiClient.BangumiSubject(
        id = id, type = type, name = name, nameCn = nameCn, summary = "", date = date,
        platform = null, nsfw = false, locked = false, eps = 0, totalEpisodes = 0, volumes = 0,
        images = null, rating = null, collection = null, tags = emptyList(), metaTags = emptyList(),
        infobox = emptyMap()
    )

    @Test
    fun uniqueExactTitleAndYearMatchIsAccepted() {
        val picked = BangumiIdNamespace.selectUniqueSubject(
            listOf(subject(8291, "Fight Club", "搏击俱乐部")),
            BangumiApiClient.SubjectType.REAL, "Fight Club", 1999
        )
        assertEquals(8291, picked?.id)
    }

    @Test
    fun yearOnlySingleCandidateIsRejected() {
        // Eski kod bunu kabul edip aynı yıl çıkan alakasız条目'yu yanlış eşleştiriyordu.
        val picked = BangumiIdNamespace.selectUniqueSubject(
            listOf(subject(9999, "Totally Different Movie", date = "1999-05-05")),
            BangumiApiClient.SubjectType.REAL, "Fight Club", 1999
        )
        assertNull(picked)
    }

    @Test
    fun multipleExactMatchesAreAmbiguous() {
        val picked = BangumiIdNamespace.selectUniqueSubject(
            listOf(subject(1, "Fight Club"), subject(2, "Fight Club")),
            BangumiApiClient.SubjectType.REAL, "Fight Club", 1999
        )
        assertNull(picked)
    }

    @Test
    fun exactTitleButWrongYearIsRejected() {
        val picked = BangumiIdNamespace.selectUniqueSubject(
            listOf(subject(7, "Fight Club", date = "2000-01-01")),
            BangumiApiClient.SubjectType.REAL, "Fight Club", 1999
        )
        assertNull(picked)
    }

    @Test
    fun nameCnMatchIsAccepted() {
        val picked = BangumiIdNamespace.selectUniqueSubject(
            listOf(subject(8291, "Fight Club", "搏击俱乐部")),
            BangumiApiClient.SubjectType.REAL, "搏击俱乐部", 1999
        )
        assertEquals(8291, picked?.id)
    }

    @Test
    fun wrongSubjectTypeIsRejected() {
        val picked = BangumiIdNamespace.selectUniqueSubject(
            listOf(subject(5, "Fight Club", type = BangumiApiClient.SubjectType.ANIME)),
            BangumiApiClient.SubjectType.REAL, "Fight Club", 1999
        )
        assertNull(picked)
    }

    // ── 3. Kümeleme: crosswalk ek anahtarları + çakışma güvenliği ────────────

    @Test
    fun videoRowsMergeAcrossSourcesViaCrosswalkExtraKey() {
        val simkl = movie(id = 1, source = "simkl", malId = 603, tmdbId = 603, simklId = 44)
        val bangumi = movie(id = 2, source = "bangumi", malId = 500_008_291, title = "搏击俱乐部")
        val groups = groupMyListEntries(listOf(simkl, bangumi), mapOf(2 to setOf("Movie:tmdb:603")))
        assertEquals(1, groups.size)
        assertTrue(groups[0].sourceIds.containsAll(listOf("simkl", "bangumi")))
    }

    @Test
    fun conflictingTmdbKeysKeepRowsInSeparateGroups() {
        val simkl = movie(id = 1, source = "simkl", malId = 603, tmdbId = 603, simklId = 44)
        val bangumi = movie(id = 2, source = "bangumi", malId = 500_008_291, title = "搏击俱乐部")
        val other = movie(id = 3, source = "bangumi", malId = 500_009_999, title = "搏击俱乐部 2")
        val groups = groupMyListEntries(
            listOf(simkl, bangumi, other),
            mapOf(2 to setOf("Movie:tmdb:603"), 3 to setOf("Movie:tmdb:604"))
        )
        assertEquals(2, groups.size)
    }

    @Test
    fun candidateIndexFindsGroupsThroughExtraKeys() {
        val idx = CrossSyncCandidateIndex<String>()
        val simkl = movie(id = 1, source = "simkl", malId = 603, tmdbId = 603, simklId = 44)
        idx.add("g1", simkl, setOf("Movie:bangumi:8291"))
        val bangumi = movie(id = 2, source = "bangumi", malId = 500_008_291, title = "搏击俱乐部")
        // bangumi satırının KENDİ anahtarı, g1'in extra anahtarı üzerinden eşleşir
        assertTrue("g1" in idx.possibleMatches(bangumi, emptySet()))
        // alakasız kayıt hiçbir şey bulamaz
        val unrelated = movie(id = 4, source = "bangumi", malId = 500_777_777, title = "Başka Film")
        assertFalse("g1" in idx.possibleMatches(unrelated, emptySet()))
    }

    @Test
    fun videoRowKeysNeverExposeMalNamespace() {
        val e = movie(id = 1, source = "simkl", malId = 603, tmdbId = 603, simklId = 44)
        val keys = MediaIdentity.keys(e)
        assertTrue("Movie:tmdb:603" in keys)
        assertTrue("Movie:simkl:44" in keys)
        assertFalse("Movie:mal:603" in keys)
    }

    // ── 4. extraKeysFor: kendi uzayı ezilmez, öğrenilen uzay eklenir ─────────

    @Test
    fun extraKeysNeverOverrideOwnedNamespace() {
        val simkl = movie(id = 1, source = "simkl", malId = 603, tmdbId = 603, simklId = 44)
        val extras = MyListIdentityCrosswalk.extraKeysFor(simkl, CrossProviderIds(tmdbId = 999, bangumiSubjectId = 8291))
        assertFalse("Movie:tmdb:999" in extras)
        assertTrue("Movie:bangumi:8291" in extras)
    }

    @Test
    fun ambiguousBangumiMappingIsDropped() {
        assertTrue(MyListIdentityCrosswalk.reverseBangumiMapping(mapOf("bgm_a_21" to 35658, "bgm_a_99" to 35658)).isEmpty())
        assertEquals(21, MyListIdentityCrosswalk.reverseBangumiMapping(mapOf("bgm_a_21" to 35658))["Anime:35658"])
    }

    @Test
    fun fanOutGateRequiresVerifiedIdentityForBangumiRows() {
        assertFalse(CrossSyncPolicy.allowFanOut(true, CrossProviderIds()))
        assertTrue(CrossSyncPolicy.allowFanOut(true, CrossProviderIds(tmdbId = 603)))
        assertTrue(CrossSyncPolicy.allowFanOut(true, CrossProviderIds(malId = 21)))
        assertTrue(CrossSyncPolicy.allowFanOut(false, CrossProviderIds()))
    }
}
