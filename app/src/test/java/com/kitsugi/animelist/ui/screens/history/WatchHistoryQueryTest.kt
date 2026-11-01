package com.kitsugi.animelist.ui.screens.history

import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.model.WatchHistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * İzleme geçmişi ekranının hesap katmanı ([WatchHistoryQuery]) birim testleri.
 *
 * Buradaki kurallar arayüzün tamamını belirliyor: hangi kayıt "izlendi" sayılır,
 * sıralama/filtreler nasıl davranır, "Bugün/Dün" bölümleri nasıl oluşur.
 */
class WatchHistoryQueryTest {

    private val hour = 60 * 60 * 1000L
    private val day = 24 * hour

    private fun entry(
        animeId: String = "anime-1",
        title: String = "Test",
        episode: Int = 1,
        season: Int = 1,
        positionMs: Long = 0L,
        durationMs: Long = 0L,
        watchedAtMs: Long = 0L,
        isMovie: Boolean = false,
        source: String? = null,
        streamUrl: String? = null,
        streamName: String? = null,
        streamTitle: String? = null
    ) = WatchHistoryEntry(
        animeId = animeId,
        animeTitle = title,
        posterUrl = null,
        episode = episode,
        season = season,
        isMovie = isMovie,
        source = source,
        watchedAtMs = watchedAtMs,
        durationMs = durationMs,
        positionMs = positionMs,
        streamUrl = streamUrl,
        streamName = streamName,
        streamTitle = streamTitle
    )

    // ── İlerleme kuralları ────────────────────────────────────────────────────

    @Test
    fun `ilerleme yuzdesi ve tamamlanma esigi`() {
        val half = entry(positionMs = 500, durationMs = 1000)
        assertEquals(0.5f, WatchHistoryQuery.progressOf(half), 0.0001f)
        assertTrue(WatchHistoryQuery.isInProgress(half))
        assertFalse(WatchHistoryQuery.isCompleted(half))

        val almostDone = entry(positionMs = 950, durationMs = 1000)
        assertTrue(WatchHistoryQuery.isCompleted(almostDone))
        assertFalse(WatchHistoryQuery.isInProgress(almostDone))
    }

    @Test
    fun `suresi bilinmeyen kayit baslanmamis sayilir`() {
        val unknown = entry(positionMs = 120_000, durationMs = 0)
        assertEquals(0f, WatchHistoryQuery.progressOf(unknown), 0.0001f)
        assertTrue(WatchHistoryQuery.isNotStarted(unknown))
        assertFalse(WatchHistoryQuery.isInProgress(unknown))
        assertEquals(0L, WatchHistoryQuery.remainingMs(unknown))
    }

    @Test
    fun `kalan sure hesaplanir`() {
        assertEquals(600L, WatchHistoryQuery.remainingMs(entry(positionMs = 400, durationMs = 1000)))
        // Konum süreyi aşmışsa negatif değer dönmez.
        assertEquals(0L, WatchHistoryQuery.remainingMs(entry(positionMs = 1200, durationMs = 1000)))
    }

    @Test
    fun `konum bicimlendirmesi`() {
        assertEquals("00:00", WatchHistoryQuery.formatPosition(0))
        assertEquals("01:05", WatchHistoryQuery.formatPosition(65_000))
        assertEquals("00:59", WatchHistoryQuery.formatPosition(59_000))
        assertEquals("1:02:05", WatchHistoryQuery.formatPosition(3_725_000))
    }

    // ── Arama / filtre ────────────────────────────────────────────────────────

    @Test
    fun `arama turkce karakter duyarsiz`() {
        val entries = listOf(
            entry(animeId = "a", title = "Kişi"),
            entry(animeId = "b", title = "Naruto")
        )
        assertEquals(1, WatchHistoryQuery.applyFilters(entries, query = "kisi").size)
        assertEquals(1, WatchHistoryQuery.applyFilters(entries, query = "KİŞİ").size)
        assertEquals(1, WatchHistoryQuery.applyFilters(entries, query = "naruto").size)
        assertEquals(2, WatchHistoryQuery.applyFilters(entries, query = "").size)
    }

    @Test
    fun `arama kaynak adinda da calisir`() {
        val entries = listOf(
            entry(animeId = "a", title = "Dizi", source = "HDFilmCehennemi"),
            entry(animeId = "b", title = "Baska", source = "Rapidrame")
        )
        assertEquals(1, WatchHistoryQuery.applyFilters(entries, query = "hdfilm").size)
    }

    @Test
    fun `ilerleme ve medya filtreleri`() {
        val inProgress = entry(animeId = "a", positionMs = 300, durationMs = 1000)
        val completed = entry(animeId = "b", positionMs = 980, durationMs = 1000)
        val notStarted = entry(animeId = "c")
        val movie = entry(animeId = "d", isMovie = true, positionMs = 500, durationMs = 1000)
        val entries = listOf(inProgress, completed, notStarted, movie)

        assertEquals(
            2,
            WatchHistoryQuery.applyFilters(
                entries,
                progressFilter = WatchHistoryProgressFilter.CONTINUE
            ).size
        )
        assertEquals(
            1,
            WatchHistoryQuery.applyFilters(
                entries,
                progressFilter = WatchHistoryProgressFilter.COMPLETED
            ).size
        )
        assertEquals(
            1,
            WatchHistoryQuery.applyFilters(
                entries,
                mediaFilter = WatchHistoryMediaFilter.MOVIES
            ).size
        )
        assertEquals(
            3,
            WatchHistoryQuery.applyFilters(
                entries,
                mediaFilter = WatchHistoryMediaFilter.SERIES
            ).size
        )
        // Ortak başlık "Test" dört kayıtta da geçiyor.
        assertEquals(4, WatchHistoryQuery.applyFilters(entries, sourceFilter = null, query = "test").size)
    }

    @Test
    fun `kaynak filtresi buyuk kucuk harf duyarsiz`() {
        val entries = listOf(
            entry(animeId = "a", source = "Rapidrame"),
            entry(animeId = "b", source = "HDFilmCehennemi")
        )
        assertEquals(1, WatchHistoryQuery.applyFilters(entries, sourceFilter = "rapidrame").size)
        assertEquals(2, WatchHistoryQuery.applyFilters(entries, sourceFilter = null).size)
    }

    // ── Sıralama ──────────────────────────────────────────────────────────────

    @Test
    fun `son izlenenler ve en eski siralari`() {
        val oldest = entry(animeId = "a", watchedAtMs = 100)
        val middle = entry(animeId = "b", watchedAtMs = 200)
        val newest = entry(animeId = "c", watchedAtMs = 300)
        val entries = listOf(middle, oldest, newest)

        assertEquals(
            listOf("c", "b", "a"),
            WatchHistoryQuery.sort(entries, WatchHistorySortMode.RECENT).map { it.animeId }
        )
        assertEquals(
            listOf("a", "b", "c"),
            WatchHistoryQuery.sort(entries, WatchHistorySortMode.OLDEST).map { it.animeId }
        )
    }

    @Test
    fun `devam edenler once siralamasi`() {
        val inProgress = entry(animeId = "in", positionMs = 500, durationMs = 1000, watchedAtMs = 100)
        val notStarted = entry(animeId = "fresh", watchedAtMs = 200)
        val completed = entry(animeId = "done", positionMs = 990, durationMs = 1000, watchedAtMs = 300)
        val sorted = WatchHistoryQuery.sort(
            listOf(completed, notStarted, inProgress),
            WatchHistorySortMode.CONTINUE_FIRST
        )
        assertEquals(listOf("in", "fresh", "done"), sorted.map { it.animeId })
    }

    // ── Gruplama ──────────────────────────────────────────────────────────────

    @Test
    fun `diziye gore gruplama bolumleri siralar`() {
        val episode3 = entry(animeId = "x", episode = 3, watchedAtMs = 300)
        val episode1 = entry(animeId = "x", episode = 1, watchedAtMs = 100)
        val other = entry(animeId = "y", episode = 1, watchedAtMs = 500)

        val groups = WatchHistoryQuery.groupBySeries(listOf(episode3, episode1, other))

        assertEquals(2, groups.size)
        assertEquals("y", groups[0].animeId)
        assertEquals("x", groups[1].animeId)
        assertEquals(listOf(1, 3), groups[1].entries.map { it.episode })
        assertEquals(episode3, groups[1].latestEntry)
        assertEquals("S1 · E3", groups[1].latestEpisodeCode)
    }

    @Test
    fun `film grubunda bolum kodu null`() {
        val movie = entry(animeId = "m", isMovie = true, watchedAtMs = 10)
        val group = WatchHistoryQuery.groupBySeries(listOf(movie)).single()
        assertNull(group.latestEpisodeCode)
    }

    // ── Tarih bölümleri ───────────────────────────────────────────────────────

    @Test
    fun `tarih bolumleri bugunden eskiye ayrilir`() {
        val noon = WatchHistoryQuery.startOfDay(System.currentTimeMillis()) + 12 * hour
        val entries = listOf(
            entry(animeId = "today", watchedAtMs = noon),
            entry(animeId = "yesterday", watchedAtMs = noon - day),
            entry(animeId = "week", watchedAtMs = noon - 3 * day),
            entry(animeId = "month", watchedAtMs = noon - 10 * day),
            entry(animeId = "older", watchedAtMs = noon - 400 * day)
        )

        val sections = WatchHistoryQuery.withSections(entries, nowMs = noon)

        assertEquals(
            listOf(
                R.string.wh_section_today,
                R.string.wh_section_yesterday,
                R.string.wh_section_this_week,
                R.string.wh_section_this_month,
                R.string.wh_section_older
            ),
            sections.map { it.titleRes }
        )
        assertEquals(1, sections.first().entries.size)
        assertEquals("today", sections.first().entries.first().animeId)
    }

    @Test
    fun `ayni gun icindeki kayitlar tek baslik altinda toplanir`() {
        val noon = WatchHistoryQuery.startOfDay(System.currentTimeMillis()) + 12 * hour
        val entries = listOf(
            entry(animeId = "a", watchedAtMs = noon),
            entry(animeId = "b", watchedAtMs = noon - hour),
            entry(animeId = "c", watchedAtMs = noon - day)
        )
        val sections = WatchHistoryQuery.withSections(entries, nowMs = noon)
        assertEquals(2, sections.size)
        assertEquals(2, sections.first().entries.size)
    }

    @Test
    fun `gun farki hesaplanir`() {
        val noon = WatchHistoryQuery.startOfDay(System.currentTimeMillis()) + 12 * hour
        assertEquals(0, WatchHistoryQuery.dayOffset(noon, noon))
        assertEquals(1, WatchHistoryQuery.dayOffset(noon - day, noon))
        assertEquals(5, WatchHistoryQuery.dayOffset(noon - 5 * day, noon))
    }

    // ── İstatistik ────────────────────────────────────────────────────────────

    @Test
    fun `ozet istatistikleri`() {
        val entries = listOf(
            entry(animeId = "x", episode = 1, positionMs = 50, durationMs = 100),
            entry(animeId = "x", episode = 2),
            entry(animeId = "y", isMovie = true, positionMs = 1000, durationMs = 1000)
        )
        val stats = WatchHistoryQuery.stats(entries)

        assertEquals(3, stats.totalEntries)
        assertEquals(2, stats.titleCount)
        assertEquals(1, stats.seriesCount)
        assertEquals(1, stats.movieCount)
        assertEquals(1, stats.continueCount)
        assertEquals(1, stats.completedCount)
        assertEquals(1050L, stats.watchedMs)
    }

    // ── Doğrudan oynatma penceresi ────────────────────────────────────────────

    @Test
    fun `dogrudan oynatma penceresi`() {
        val launch = 1_000_000L
        val now = launch + 5 * 60 * 1000L
        assertTrue(
            WatchHistoryQuery.isDirectPlayable(
                entry(streamUrl = "https://example.com/a.m3u8", watchedAtMs = now),
                nowMs = now,
                appLaunchTimeMs = launch
            )
        )
        assertFalse(
            WatchHistoryQuery.isDirectPlayable(
                entry(streamUrl = "https://example.com/a.m3u8", watchedAtMs = now - 31 * 60 * 1000L),
                nowMs = now,
                appLaunchTimeMs = launch
            )
        )
        assertFalse(
            WatchHistoryQuery.isDirectPlayable(entry(streamUrl = null, watchedAtMs = now), nowMs = now, appLaunchTimeMs = launch)
        )
        assertFalse(
            WatchHistoryQuery.isDirectPlayable(
                entry(streamUrl = "https://example.com/a.m3u8", watchedAtMs = launch - 1000L),
                nowMs = now,
                appLaunchTimeMs = launch
            )
        )
    }

    // ── Kaynak etiketi ────────────────────────────────────────────────────────

    @Test
    fun `kaynak etiketi tekrarsiz ve temiz`() {
        val e = entry(
            streamName = "HDFilmCehennemi · Close · .fa-secondary{opacity:.4} DUAL",
            streamTitle = "Rapidrame · .fa-secondary{opacity:.4} DUAL"
        )
        val label = WatchHistoryQuery.sourceLabel(e)
        assertEquals("HDFilmCehennemi · Close · DUAL · Rapidrame", label)
        assertEquals("Dual", WatchHistoryQuery.audioLabel(e))
    }
}
