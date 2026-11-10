package com.kitsugi.animelist.ui.screens.mylist

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.ui.screens.mylist.components.myListSortEntries
import com.kitsugi.animelist.ui.screens.mylist.components.nextSortId
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * "Son eklenen / İlk eklenen" sıralaması ve tarih bölümleri.
 *
 * Regresyon kaynağı: sıralama yerel satır `id`'sine dayanıyordu; içe aktarma sırası
 * değiştiğinde aynı yapım bir sekmede "Bugün", diğerinde "Dün" görünüyordu.
 */
class MyListDateGroupingTest {

    private val zone: ZoneId = ZoneId.of("Europe/Istanbul")

    /** 2026-10-24 Cumartesi (kullanıcının bildirdiği tarih). */
    private val today: LocalDate = LocalDate.of(2026, 10, 24)

    private fun secondsAt(date: LocalDate, hour: Int = 12): Long =
        ZonedDateTime.of(date.atTime(hour, 0), zone).toEpochSecond()

    private fun entry(
        id: Int,
        title: String = "Başlık $id",
        addedAt: Long = 0L,
        updatedAt: Long = 0L
    ) = MediaEntry(
        id = id,
        title = title,
        type = MediaType.Anime,
        status = WatchStatus.Planned,
        score = null,
        progress = 0,
        total = null,
        updatedAt = updatedAt,
        addedAt = addedAt
    )

    private fun bucketOf(date: LocalDate): LegacyMyListDateBucket =
        myListDateBucketOf(secondsAt(date), zone, today)

    @Test
    fun buckets_areComputedFromCalendarDays() {
        assertEquals(LegacyMyListDateBucket.TODAY, bucketOf(today))
        assertEquals(LegacyMyListDateBucket.TODAY, bucketOf(today.plusDays(1)))      // saat kayması
        assertEquals(LegacyMyListDateBucket.YESTERDAY, bucketOf(today.minusDays(1)))
        assertEquals(LegacyMyListDateBucket.THIS_WEEK, bucketOf(LocalDate.of(2026, 10, 21))) // Çarşamba
        assertEquals(LegacyMyListDateBucket.THIS_WEEK, bucketOf(LocalDate.of(2026, 10, 19))) // Pazartesi
        assertEquals(LegacyMyListDateBucket.LAST_WEEK, bucketOf(LocalDate.of(2026, 10, 18))) // Pazar
        assertEquals(LegacyMyListDateBucket.LAST_WEEK, bucketOf(LocalDate.of(2026, 10, 13)))
        assertEquals(LegacyMyListDateBucket.LAST_WEEK, bucketOf(LocalDate.of(2026, 10, 12))) // pazartesi
        assertEquals(LegacyMyListDateBucket.OLDER, bucketOf(LocalDate.of(2026, 10, 11)))
        assertEquals(LegacyMyListDateBucket.UNKNOWN, myListDateBucketOf(0L, zone, today))
    }

    @Test
    fun sections_areOrderedNewestFirst_andUnknownLast() {
        val entries = listOf(
            entry(1, "Eski", addedAt = secondsAt(LocalDate.of(2020, 3, 1))),
            entry(2, "Bugün", addedAt = secondsAt(today)),
            entry(3, "Bilinmiyor", addedAt = 0L),
            entry(4, "Dün", addedAt = secondsAt(today.minusDays(1))),
            entry(5, "Bu hafta", addedAt = secondsAt(LocalDate.of(2026, 10, 20)))
        )

        val grouped = groupMyListEntriesByAddedDate(entries, zone = zone, today = today)

        assertEquals(
            listOf(
                LegacyMyListDateBucket.TODAY,
                LegacyMyListDateBucket.YESTERDAY,
                LegacyMyListDateBucket.THIS_WEEK,
                LegacyMyListDateBucket.OLDER,
                LegacyMyListDateBucket.UNKNOWN
            ),
            grouped.map { it.first }
        )
        assertEquals(listOf(2), grouped.first { it.first == LegacyMyListDateBucket.TODAY }.second.map { it.id })
    }

    @Test
    fun newestSort_usesAddedAtInsteadOfRowId() {
        // En yüksek id eski kayıt: id'ye göre sıralama yanlış sonuç verirdi.
        val entries = listOf(
            entry(99, "Dün eklenen", addedAt = secondsAt(today.minusDays(1))),
            entry(1, "Bugün eklenen", addedAt = secondsAt(today)),
            entry(7, "Bilinmiyor", addedAt = 0L, updatedAt = 0L)
        )

        val newest = applySort(entries, "newest")
        val oldest = applySort(entries, "oldest")

        assertEquals(listOf(1, 99, 7), newest.map { it.id })
        assertEquals(listOf(99, 1, 7), oldest.map { it.id })
    }

    /**
     * Sıralama menüsü (çipler, çekmece ve filtre sayfası) artık TEK bir listeden besleniyor.
     * Bu test, listedeki her kimliğin `applySort` tarafından gerçekten karşılandığını ve
     * liste uzunluğunun korunduğunu doğrular — yeni bir ölçüt eklenip `applySort`'a
     * bağlanmazsa sıralama sessizce "Son eklenen"e düşerdi.
     */
    @Test
    fun everySortMenuEntryIsHandledByApplySort() {
        val sample = listOf(
            entry(1, "Alfa", addedAt = secondsAt(today)),                                  // puan yok
            entry(2, "Beta", addedAt = secondsAt(today.minusDays(3))).copy(score = 9, progress = 12, isFavorite = true),
            entry(3, "Gama", addedAt = 0L)                                                 // bilinmiyor
        )

        myListSortEntries.forEach { sortEntry ->
            val ids = listOfNotNull(sortEntry.descId, sortEntry.ascId)
            ids.forEach { id ->
                val sorted = applySort(sample, id)
                assertEquals("Sıralama kimliği kayıt kaybetmemeli: $id", sample.size, sorted.size)
                assertEquals("Sıralama kimliği kayıt üretmemeli: $id", sample.map { it.id }.toSet(), sorted.map { it.id }.toSet())
            }
        }
    }

    @Test
    fun dateSectionsOnlyForAddedDateSorts() {
        assertTrue(usesAddedDateGrouping("newest"))
        assertTrue(usesAddedDateGrouping("oldest"))
        listOf("title", "title_desc", "score", "score_asc", "progress", "progress_asc",
            "favorites", "start_date_desc", "start_date_asc", "end_date_desc", "end_date_asc",
            "year_desc", "year_asc", "updated_desc", "updated_asc", "repeat_desc", "repeat_asc",
            "priority_desc", "priority_asc"
        ).forEach { id ->
            assertFalse("Tarih bölümü olmayan sıralama: $id", usesAddedDateGrouping(id))
        }
    }

    @Test
    fun sortToggleFlipsDirectionOnlyWhenThereIsOne() {
        val added = myListSortEntries.first { it.descId == "newest" }
        assertEquals("oldest", added.nextSortId("newest"))
        assertEquals("newest", added.nextSortId("oldest"))

        val favorites = myListSortEntries.first { it.descId == "favorites" }
        assertNull(favorites.ascId)
        assertEquals("favorites", favorites.nextSortId("favorites"))
    }

    @Test
    fun addedAt_fallsBackToUpdatedAtForLegacyRows() {
        val legacy = entry(5, "Eski yedek", addedAt = 0L, updatedAt = secondsAt(today.minusDays(1)))
        val fresh = entry(6, "Bugün", addedAt = secondsAt(today))

        val newest = applySort(listOf(legacy, fresh), "newest")

        assertEquals(listOf(6, 5), newest.map { it.id })
        assertEquals(LegacyMyListDateBucket.YESTERDAY, myListDateBucketOf(addedSortTimestamp(legacy), zone, today))
    }
}
