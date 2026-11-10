package com.kitsugi.animelist.ui.screens.mylist

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Listem gruplama + "eklenme tarihi" sıralaması sözleşmesi.
 *
 * Kritik kural: gruplama ile sıralama BAĞIMSIZDIR. Sıralama seçmek bölüm başlıklarını
 * kaldırmaz; her bölümün içi seçilen sıralamaya göre dizilir.
 */
class MyListSectionsTest {

    private val now: Long = run {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.OCTOBER, 24, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }

    private fun entry(
        id: Int,
        addedDaysAgo: Int,
        status: WatchStatus = WatchStatus.Watching,
        title: String = "Kayıt $id"
    ): MediaEntry {
        val addedSeconds = (now - addedDaysAgo * 86_400_000L) / 1000L
        return MediaEntry(
            id = id,
            title = title,
            type = MediaType.Anime,
            status = status,
            score = null,
            progress = 0,
            total = null,
            createdAt = addedSeconds
        )
    }

    @Test
    fun addedGroupingUsesTodayYesterdayWeekMonthYearBuckets() {
        val entries = listOf(
            entry(id = 1, addedDaysAgo = 0),
            entry(id = 2, addedDaysAgo = 1),
            entry(id = 3, addedDaysAgo = 3),
            entry(id = 4, addedDaysAgo = 20),
            entry(id = 5, addedDaysAgo = 200)
        )

        val buckets = groupMyListEntries(entries, MyListGroupingIds.ADDED, now)
            .map { it.bucket }

        assertEquals(
            listOf(
                MyListDateBucket.TODAY,
                MyListDateBucket.YESTERDAY,
                MyListDateBucket.THIS_WEEK,
                MyListDateBucket.THIS_MONTH,
                MyListDateBucket.THIS_YEAR
            ),
            buckets
        )
    }

    @Test
    fun sortingReordersInsideSectionsWithoutRemovingHeaders() {
        val entries = listOf(
            entry(id = 1, addedDaysAgo = 0, title = "B"),
            entry(id = 2, addedDaysAgo = 0, title = "A"),
            entry(id = 3, addedDaysAgo = 2, title = "D"),
            entry(id = 4, addedDaysAgo = 2, title = "C")
        )

        val sorted = applySort(entries, "title")
        val sections = groupMyListEntries(sorted, MyListGroupingIds.ADDED, now)

        // Başlıklar korunur (iki bölüm: Bugün + Bu Hafta).
        assertEquals(2, sections.size)
        assertEquals(MyListDateBucket.TODAY, sections[0].bucket)
        assertEquals(MyListDateBucket.THIS_WEEK, sections[1].bucket)
        // Bölüm İÇİ sıralama uygulanmıştır.
        assertEquals(listOf("A", "B"), sections[0].entries.map { it.title })
        assertEquals(listOf("C", "D"), sections[1].entries.map { it.title })
    }

    @Test
    fun defaultSortKeepsNaturalOrderAndStatusGroupingStillWorks() {
        val entries = listOf(
            entry(id = 1, addedDaysAgo = 2, status = WatchStatus.Completed),
            entry(id = 2, addedDaysAgo = 0, status = WatchStatus.Watching),
            entry(id = 3, addedDaysAgo = 0, status = WatchStatus.Watching)
        )

        assertEquals(entries, applySort(entries, "default"))

        val sections = groupMyListEntries(entries, MyListGroupingIds.STATUS, now)
        assertEquals(listOf(WatchStatus.Watching, WatchStatus.Completed), sections.map { it.status })
        assertEquals(listOf(2, 3), sections.first().entries.map { it.id })
    }

    @Test
    fun newlyAddedSortUsesCreatedAtNotRowId() {
        // id sırası ile eklenme zamanı sırası bilinçli olarak TERS.
        val entries = listOf(
            entry(id = 10, addedDaysAgo = 0),
            entry(id = 2, addedDaysAgo = 5)
        )

        assertEquals(listOf(10, 2), applySort(entries, "newest").map { it.id })
        assertEquals(listOf(2, 10), applySort(entries, "oldest").map { it.id })
    }

    @Test
    fun entriesWithoutDateFallIntoUnknownSectionLast() {
        val undated = MediaEntry(
            id = 99,
            title = "Tarihsiz",
            type = MediaType.Anime,
            status = WatchStatus.Planned,
            score = null,
            progress = 0,
            total = null
        )

        val sections = groupMyListEntries(listOf(undated, entry(id = 1, addedDaysAgo = 0)), MyListGroupingIds.ADDED, now)
        assertEquals(MyListDateBucket.TODAY, sections.first().bucket)
        assertEquals(MyListDateBucket.UNKNOWN, sections.last().bucket)
        assertTrue(sections.last().entries.single().id == 99)
    }

    @Test
    fun flatGroupingProducesSingleHeaderlessSection() {
        val sections = groupMyListEntries(
            listOf(entry(id = 1, addedDaysAgo = 0), entry(id = 2, addedDaysAgo = 9)),
            MyListGroupingIds.NONE,
            now
        )
        assertEquals(1, sections.size)
        assertTrue(sections.single().isHeaderless)
    }
}
