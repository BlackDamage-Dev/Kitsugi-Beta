package com.kitsugi.animelist.core.player

import com.kitsugi.animelist.core.player.AutoTrackPolicy.Action
import com.kitsugi.animelist.core.player.AutoTrackPolicy.Kind
import com.kitsugi.animelist.core.player.AutoTrackPolicy.Snapshot
import com.kitsugi.animelist.core.player.AutoTrackPolicy.Status
import com.kitsugi.animelist.core.player.AutoTrackPolicy.TrackedState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Otomatik liste takibi politikasının sözleşmesi.
 *
 * Bu testler kullanıcı raporundaki maddeleri tek tek sabitler:
 * 1. İzlemeye başlayınca liste "İzleniyor" olur.
 * 2. Bölüm bitince ilerlemeye BÖLÜM NUMARASI yazılır (kaç bölüm atlanmış olursa olsun).
 * 3. Son bölüm / film bitince kayıt "Tamamlandı" olur.
 * 4. İlerleme asla azalmaz; "Bırakıldı" kayıt ezilmez.
 * 5. Fragman ve çok kısa videolar takip edilmez.
 * 6. Jeneriği (outro) atlamak tamamlanma sayılır.
 */
class AutoTrackPolicyTest {

    private val episodeDuration = 24 * 60 * 1000L // 24 dk

    private fun snapshot(
        position: Long = 0,
        duration: Long = episodeDuration,
        episode: Int = 1,
        total: Int? = 12,
        kind: Kind = Kind.EPISODE,
        hasNext: Boolean = true,
        endReached: Boolean = false,
        outroStartMs: Long? = null,
        isTrailer: Boolean = false
    ) = Snapshot(
        positionMs = position,
        durationMs = duration,
        episode = episode,
        totalEpisodes = total,
        kind = kind,
        hasNextEpisode = hasNext,
        endReached = endReached,
        outroStartMs = outroStartMs,
        isTrailer = isTrailer
    )

    @Test
    fun `starting an episode marks the planned entry as watching without moving progress`() {
        val decision = AutoTrackPolicy.evaluate(
            settings = AutoTrackPolicy.Settings(enabled = true),
            snapshot = snapshot(position = 90_000),
            current = TrackedState(status = Status.PLANNED, progress = 0, total = 12)
        )
        assertEquals(Action.START_WATCHING, decision.action)
        assertEquals(Status.WATCHING, decision.status)
        assertEquals(0, decision.progress)
        assertFalse(decision.showQuickRate)
    }

    @Test
    fun `episode that reaches the threshold advances progress to the episode number`() {
        val decision = AutoTrackPolicy.evaluate(
            settings = AutoTrackPolicy.Settings(enabled = true),
            snapshot = snapshot(position = 21 * 60 * 1000L, episode = 4),
            current = TrackedState(status = Status.WATCHING, progress = 3, total = 12)
        )
        assertEquals(Action.ADVANCE_EPISODE, decision.action)
        assertEquals(Status.WATCHING, decision.status)
        assertEquals(4, decision.progress)
        assertTrue(decision.showQuickRate)
    }

    @Test
    fun `first watched episode of an untracked entry advances to episode one`() {
        val decision = AutoTrackPolicy.evaluate(
            settings = AutoTrackPolicy.Settings(enabled = true),snapshot = snapshot(position = 22 * 60 * 1000L, episode = 1), current = null)
        assertEquals(Action.ADVANCE_EPISODE, decision.action)
        assertEquals(1, decision.progress)
    }

    @Test
    fun `last episode completes the entry and fills progress with total`() {
        val decision = AutoTrackPolicy.evaluate(
            settings = AutoTrackPolicy.Settings(enabled = true),
            snapshot = snapshot(position = 23 * 60 * 1000L, episode = 12, total = 12, hasNext = false),
            current = TrackedState(status = Status.WATCHING, progress = 11, total = 12)
        )
        assertEquals(Action.COMPLETE, decision.action)
        assertEquals(Status.COMPLETED, decision.status)
        assertEquals(12, decision.progress)
    }

    @Test
    fun `movie completes on first end regardless of episode numbering`() {
        val decision = AutoTrackPolicy.evaluate(
            settings = AutoTrackPolicy.Settings(enabled = true),
            snapshot = snapshot(
                position = 100 * 60 * 1000L,
                duration = 110 * 60 * 1000L,
                episode = 1,
                total = 1,
                kind = Kind.MOVIE,
                hasNext = false
            ),
            current = TrackedState(status = Status.PLANNED, progress = 0, total = 1)
        )
        assertEquals(Action.COMPLETE, decision.action)
        assertEquals(Status.COMPLETED, decision.status)
        assertEquals(1, decision.progress)
    }

    @Test
    fun `engine ENDED always completes even without position progress`() {
        val decision = AutoTrackPolicy.evaluate(
            settings = AutoTrackPolicy.Settings(enabled = true),
            snapshot = snapshot(position = 0, endReached = true, episode = 3),
            current = TrackedState(status = Status.WATCHING, progress = 2, total = 12)
        )
        assertEquals(Action.ADVANCE_EPISODE, decision.action)
        assertEquals(3, decision.progress)
    }

    @Test
    fun `watching beyond an already completed entry never lowers progress`() {
        val decision = AutoTrackPolicy.evaluate(
            settings = AutoTrackPolicy.Settings(enabled = true),
            snapshot = snapshot(position = 23 * 60 * 1000L, episode = 2),
            current = TrackedState(status = Status.COMPLETED, progress = 12, total = 12)
        )
        assertEquals(Action.NONE, decision.action)
        assertEquals(12, decision.progress)
        // Kullanıcı yeniden izledi: puan vermek isteyebilir, çubuk yine sunulur.
        assertTrue(decision.showQuickRate)
    }

    @Test
    fun `dropped entry is respected when the setting is on`() {
        val dropped = TrackedState(status = Status.DROPPED, progress = 4, total = 12)
        val respected = AutoTrackPolicy.evaluate(
            settings = AutoTrackPolicy.Settings(enabled = true),
            snapshot = snapshot(position = 23 * 60 * 1000L, episode = 5),
            current = dropped
        )
        assertEquals(Action.NONE, respected.action)
        assertEquals(4, respected.progress)

        val overridden = AutoTrackPolicy.evaluate(
            snapshot = snapshot(position = 23 * 60 * 1000L, episode = 5),
            settings = AutoTrackPolicy.Settings(enabled = true, respectDropped = false),
            current = dropped
        )
        assertEquals(Action.ADVANCE_EPISODE, overridden.action)
        assertEquals(5, overridden.progress)
    }

    @Test
    fun `trailers and short clips are never tracked`() {
        val trailer = AutoTrackPolicy.evaluate(
            settings = AutoTrackPolicy.Settings(enabled = true),
            snapshot = snapshot(position = 300_000, duration = 300_000, endReached = true, isTrailer = true),
            current = TrackedState(status = Status.PLANNED, progress = 0, total = 1)
        )
        assertEquals(Action.NONE, trailer.action)

        val clip = AutoTrackPolicy.evaluate(
            settings = AutoTrackPolicy.Settings(enabled = true),
            snapshot = snapshot(position = 30_000, duration = 45_000, endReached = true, kind = Kind.MOVIE),
            current = TrackedState(status = Status.PLANNED, progress = 0, total = 1)
        )
        assertEquals(Action.NONE, clip.action)
    }

    @Test
    fun `skipping past the outro counts as finishing the episode`() {
        val decision = AutoTrackPolicy.evaluate(
            settings = AutoTrackPolicy.Settings(enabled = true),
            snapshot = snapshot(position = 21 * 60 * 1000L, episode = 2, outroStartMs = 21 * 60 * 1000L + 5_000L),
            current = TrackedState(status = Status.WATCHING, progress = 1, total = 12)
        )
        assertEquals(Action.ADVANCE_EPISODE, decision.action)
        assertEquals(2, decision.progress)
        assertEquals(1f, decision.fraction)
    }

    @Test
    fun `auto tracking is OFF by default until the user opts in`() {
        // Deneysel özellik: kullanıcı Ayarlar → Deneysel bölümünden onay vererek açmadıkça
        // hiçbir karar üretilmemeli.
        assertFalse(AutoTrackPolicy.Settings().enabled)
        val decision = AutoTrackPolicy.evaluate(
            snapshot = snapshot(position = 23 * 60 * 1000L, episode = 3, endReached = true),
            current = TrackedState(status = Status.WATCHING, progress = 2, total = 12)
        )
        assertEquals(Action.NONE, decision.action)
        assertFalse(decision.showQuickRate)
    }

    @Test
    fun `auto tracking disabled leaves everything untouched`() {
        val decision = AutoTrackPolicy.evaluate(
            snapshot = snapshot(position = 23 * 60 * 1000L, episode = 6, endReached = true),
            settings = AutoTrackPolicy.Settings(enabled = false),
            current = TrackedState(status = Status.WATCHING, progress = 5, total = 12)
        )
        assertEquals(Action.NONE, decision.action)
        assertEquals(5, decision.progress)
        assertFalse(decision.showQuickRate)
    }

    @Test
    fun `progress never exceeds the known total`() {
        val decision = AutoTrackPolicy.evaluate(
            settings = AutoTrackPolicy.Settings(enabled = true),
            // Kaynak 13. bölümü oynatıyor ama seri 12 bölüm: taşma olmamalı.
            snapshot = snapshot(position = 23 * 60 * 1000L, episode = 13, total = 12, hasNext = true),
            current = TrackedState(status = Status.WATCHING, progress = 12, total = 12)
        )
        assertEquals(12, decision.progress)
    }

    @Test
    fun `unknown total advances to the raw episode number`() {
        val decision = AutoTrackPolicy.evaluate(
            settings = AutoTrackPolicy.Settings(enabled = true),
            snapshot = snapshot(position = 23 * 60 * 1000L, episode = 7, total = null),
            current = TrackedState(status = Status.WATCHING, progress = 6, total = null)
        )
        assertEquals(Action.ADVANCE_EPISODE, decision.action)
        assertEquals(7, decision.progress)
    }

    @Test
    fun `score normalisation rejects impossible values`() {
        assertEquals(null, AutoTrackPolicy.normalizeScore(0))
        assertEquals(null, AutoTrackPolicy.normalizeScore(11))
        assertEquals(null, AutoTrackPolicy.normalizeScore(null))
        assertEquals(7, AutoTrackPolicy.normalizeScore(7))
    }

    @Test
    fun `scoring a planned movie completes it but leaves dropped entries alone`() {
        assertEquals(
            Status.COMPLETED,
            AutoTrackPolicy.statusAfterScore(
                TrackedState(status = Status.PLANNED, progress = 0, total = 1),
                score = 8,
                kind = Kind.MOVIE
            )
        )
        assertEquals(
            Status.WATCHING,
            AutoTrackPolicy.statusAfterScore(
                TrackedState(status = Status.PLANNED, progress = 0, total = 12),
                score = 8,
                kind = Kind.EPISODE
            )
        )
        assertEquals(
            Status.DROPPED,
            AutoTrackPolicy.statusAfterScore(
                TrackedState(status = Status.DROPPED, progress = 2, total = 12),
                score = 9,
                kind = Kind.EPISODE
            )
        )
    }

    @Test
    fun `paused entry resumes as watching on new playback`() {
        val decision = AutoTrackPolicy.evaluate(
            settings = AutoTrackPolicy.Settings(enabled = true),
            snapshot = snapshot(position = 120_000, episode = 5),
            current = TrackedState(status = Status.PAUSED, progress = 4, total = 12)
        )
        assertEquals(Action.START_WATCHING, decision.action)
        assertEquals(Status.WATCHING, decision.status)
        assertEquals(4, decision.progress)
    }

    @Test
    fun `live streams without duration are ignored until playback ends`() {
        val live = snapshot(position = 900_000, duration = 0, episode = 1, total = null, hasNext = false)
        assertEquals(Action.NONE, AutoTrackPolicy.evaluate(
            settings = AutoTrackPolicy.Settings(enabled = true),snapshot = live, current = TrackedState(Status.WATCHING, 0, null)).action)
    }
}
