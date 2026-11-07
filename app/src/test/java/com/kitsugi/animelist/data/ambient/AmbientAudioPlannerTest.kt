package com.kitsugi.animelist.data.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientAudioPlannerTest {

    @Test
    fun `action and fantasy metadata produce a style query while keeping the manga title as a separate primary query`() {
        val plan = AmbientAudioPlanner.createPlan(
            AmbientAudioContext(
                title = "Secret Manga Name",
                chapterTitle = "The final battle",
                description = "A young hero fights monsters in a magical kingdom.",
                genres = listOf("Action", "Fantasy"),
            )
        )

        assertTrue(plan.query.contains("action"))
        assertTrue(plan.query.contains("fantasy"))
        assertTrue(plan.query.contains("instrumental"))
        assertFalse(plan.query.contains("Secret Manga Name", ignoreCase = true))
        assertEquals("Secret Manga Name", plan.titleQuery)
        assertTrue(plan.titleMatchTerms.containsAll(setOf("secret", "manga", "name")))
        assertTrue(plan.reason.contains("Aksiyon"))
        assertTrue(plan.reason.contains("Fantastik"))
    }

    @Test
    fun `title matches are ranked before mood suggestions and context remains relevance ordered`() {
        val plan = AmbientAudioPlanner.createPlan(
            AmbientAudioContext(title = "Moonlit Garden", genres = listOf("Romance"))
        )
        val exactTitle = track(id = "title", title = "Moonlit Garden Original Soundtrack")
        val titlePartial = track(id = "partial", title = "Moonlit instrumental garden")
        val contextTitle = track(id = "context-title", title = "Moonlit Garden Theme")
        val bestMood = track(id = "mood-best", title = "Romantic soft piano emotional instrumental")
        val weakMood = track(id = "mood-weak", title = "Bright summer guitar")

        assertTrue(AmbientAudioPlanner.isTitleMatch(exactTitle, plan))
        assertTrue(AmbientAudioPlanner.isTitleMatch(titlePartial, plan))
        assertFalse(AmbientAudioPlanner.isTitleMatch(bestMood, plan))
        val ordered = AmbientAudioPlanner.orderForAutoplay(
            titleMatches = listOf(titlePartial, exactTitle),
            contextTracks = listOf(weakMood, bestMood, contextTitle),
            plan = plan,
        )

        assertEquals(listOf("title", "context-title", "partial", "mood-best", "mood-weak"), ordered.map { it.id })
    }

    @Test
    fun `recent title match yields to a fresh context item in the combined queue`() {
        val plan = AmbientAudioPlanner.createPlan(
            AmbientAudioContext(title = "Moonlit Garden", genres = listOf("Romance"))
        )
        val recentTitle = track(id = "recent-title", title = "Moonlit Garden original soundtrack")
        val freshContext = track(id = "fresh-context", title = "Romantic soft piano emotional instrumental")

        val ordered = AmbientAudioPlanner.orderForAutoplay(
            titleMatches = listOf(recentTitle),
            contextTracks = listOf(freshContext),
            plan = plan,
            recentlyPlayedIds = recentTitle.historyKeys(),
        )

        assertEquals(listOf("fresh-context", "recent-title"), ordered.map { it.id })
    }

    @Test
    fun `mood context fallback starts only when fewer than five title matches survive`() {
        assertTrue(AmbientAudioPlanner.shouldSearchMoodContext(0))
        assertTrue(AmbientAudioPlanner.shouldSearchMoodContext(4))
        assertFalse(AmbientAudioPlanner.shouldSearchMoodContext(5))
        assertFalse(AmbientAudioPlanner.shouldSearchMoodContext(12))
    }

    @Test
    fun `romance and cozy metadata choose a softer query than action`() {
        val romance = AmbientAudioPlanner.createPlan(
            AmbientAudioContext(
                title = "A romance title",
                description = "A gentle love story set at school.",
                genres = listOf("Romance", "Slice of Life"),
            )
        )
        val action = AmbientAudioPlanner.createPlan(
            AmbientAudioContext(title = "Other", genres = listOf("Action"))
        )

        assertTrue(romance.query.contains("piano") || romance.query.contains("romantic"))
        assertTrue(romance.query.contains("lo-fi") || romance.query.contains("soft"))
        assertTrue(action.query.contains("action"))
        assertFalse(action.query == romance.query)
    }

    @Test
    fun `safe filtering rejects excluded providers short clips insecure URLs and unsupported media`() {
        val accepted = track(id = "ok", url = "https://cdn.example.org/music.mp3")
        val jamendo = track(id = "jamendo", source = "jamendo", provider = "jamendo")
        val unclassifiedAccess = track(id = "unclassified")
        val shortClip = track(id = "short", durationMs = 12_000L)
        val insecure = track(id = "http", url = "http://cdn.example.org/music.mp3")
        val unsupported = track(id = "unsupported", url = "https://cdn.example.org/music.pdf", fileType = "pdf")
        val liveRadio = track(
            id = "radio_browser:1",
            provider = "radio_browser",
            source = "radio_browser",
            durationMs = null,
            fileType = "m3u8",
            url = "https://radio.example.org/live.m3u8",
        )

        assertTrue(AmbientAudioPlanner.isSafeCandidate(accepted))
        assertFalse(AmbientAudioPlanner.isSafeCandidate(jamendo))
        assertTrue(AmbientAudioPlanner.isSafeCandidate(unclassifiedAccess))
        assertFalse(AmbientAudioPlanner.isSafeCandidate(shortClip))
        assertFalse(AmbientAudioPlanner.isSafeCandidate(insecure))
        assertFalse(AmbientAudioPlanner.isSafeCandidate(unsupported))
        assertTrue(AmbientAudioPlanner.isSafeCandidate(liveRadio))
    }

    @Test
    fun `ranking prefers the contextual instrumental and moves recently played items down`() {
        val plan = AmbientAudioPlanner.createPlan(
            AmbientAudioContext(title = "title", genres = listOf("Horror", "Mystery"))
        )
        val matching = track(
            id = "matching",
            title = "Dark mysterious ambient instrumental",
            tags = listOf("suspense", "drone"),
        )
        val unrelated = track(id = "unrelated", title = "Bright summer acoustic guitar")
        val tracks = listOf(unrelated, matching)

        val ranked = AmbientAudioPlanner.filterAndRank(tracks, plan)
        val withHistory = AmbientAudioPlanner.filterAndRank(tracks, plan, setOf("matching"))

        assertEquals("matching", ranked.first().id)
        assertEquals("unrelated", withHistory.first().id)
    }

    @Test
    fun `recent-play history suppresses cross-catalog copies with a different provider id`() {
        val plan = AmbientAudioPlanner.createPlan(
            AmbientAudioContext(title = "title", genres = listOf("Horror", "Mystery"))
        )
        val previouslyPlayed = track(
            id = "openverse:played",
            title = "Dark suspense instrumental",
            creator = "Shared Composer",
        )
        val catalogCopy = track(
            id = "internet_archive:copy",
            title = "Dark suspense instrumental",
            creator = "Shared Composer",
        )
        val unrelated = track(id = "new", title = "Bright summer guitar")

        val ranked = AmbientAudioPlanner.filterAndRank(
            listOf(catalogCopy, unrelated),
            plan,
            previouslyPlayed.historyKeys(),
        )

        assertEquals("new", ranked.first().id)
    }

    @Test
    fun `provider identities deduplicate a recording returned by separate catalogs`() {
        val plan = AmbientAudioPlanner.createPlan(AmbientAudioContext(title = "title"))
        val openverseCopy = track(
            id = "openverse:one",
            title = "Rain on a quiet window",
            creator = "Sample Artist",
        )
        val archiveCopy = track(
            id = "internet_archive:two",
            title = "Rain on a quiet window",
            creator = "Sample Artist",
            provider = "internet_archive",
            source = "internet_archive",
        )

        val ranked = AmbientAudioPlanner.filterAndRank(listOf(openverseCopy, archiveCopy), plan)

        assertEquals(1, ranked.size)
    }

    @Test
    fun `same provider ID suppresses a record even when its normalized metadata differs`() {
        val plan = AmbientAudioPlanner.createPlan(AmbientAudioContext(title = "title"))
        val firstVersion = track(
            id = "audius:123",
            title = "First metadata title",
            creator = "Original Artist",
            provider = "audius",
            source = "audius",
        )
        val changedMetadata = track(
            id = "audius:123",
            title = "A completely different title",
            creator = "Another artist",
            provider = "audius",
            source = "audius",
        )

        val ranked = AmbientAudioPlanner.filterAndRank(listOf(firstVersion, changedMetadata), plan)

        assertEquals(1, ranked.size)
        assertEquals("First metadata title", ranked.single().title)
    }

    @Test
    fun `download URL does not gate safe playback candidates`() {
        val track = track(id = "unknown-license").copy(
            downloadUrl = "https://cdn.example.org/offline.mp3",
        )
        assertTrue(AmbientAudioPlanner.isSafeCandidate(track))
    }

    @Test
    fun `provider scalar and array values are capped at five hundred characters`() {
        val longValue = "x".repeat(MAX_AMBIENT_AUDIO_STRING_LENGTH + 40)
        val cappedScalar = longValue.cappedAmbientAudioValue()
        val cappedArrayValues = listOf(longValue, "small").map { it.cappedAmbientAudioValue() }

        assertEquals(500, cappedScalar.length)
        assertEquals(listOf(500, 5), cappedArrayValues.map { it.length })
    }

    private fun track(
        id: String,
        title: String = "Instrumental ambient music",
        creator: String = "Creator $id",
        url: String = "https://cdn.example.org/audio.mp3",
        source: String = "freesound",
        provider: String = "freesound",
        durationMs: Long? = 180_000L,
        fileType: String = "mp3",
        tags: List<String> = emptyList(),
    ) = AmbientAudioTrack(
        id = id,
        title = title,
        creator = creator,
        streamUrl = url,
        landingUrl = "https://example.org/track/$id",
        provider = provider,
        source = source,
        durationMs = durationMs,
        fileType = fileType,
        tags = tags,
    )
}
