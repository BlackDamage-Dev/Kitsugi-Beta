package com.kitsugi.animelist.ui.screens.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

/**
 * Bildirim listesinin ortak zaman grupları (Bugün / Dün / Bu Hafta / Daha Eski /
 * Tarihsiz) her kaynak için aynı eşiklerle çalışmalıdır.
 */
class NotifDateGroupingTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, 0, 0)
        }.timeInMillis

    private fun item(id: String, tsMs: Long?): NotifItem =
        NotifItem(id = id, imageUrl = null, title = id, body = "", dateText = null, tsMs = tsMs)

    @Test
    fun dateGroupsUseDayBoundaries() {
        val now = at(2026, Calendar.OCTOBER, 31)

        assertEquals(NotifDateGroup.TODAY, notifDateGroup(at(2026, Calendar.OCTOBER, 31, 9), now))
        assertEquals(NotifDateGroup.YESTERDAY, notifDateGroup(at(2026, Calendar.OCTOBER, 30, 23), now))
        assertEquals(NotifDateGroup.THIS_WEEK, notifDateGroup(at(2026, Calendar.OCTOBER, 27, 10), now))
        assertEquals(NotifDateGroup.EARLIER, notifDateGroup(at(2026, Calendar.OCTOBER, 20, 10), now))
        assertEquals(NotifDateGroup.UNDATED, notifDateGroup(null, now))
    }

    @Test
    fun headerOnlyAppearsOnGroupChangeAndCarriesGroupTotal() {
        val now = at(2026, Calendar.OCTOBER, 31)
        val items = listOf(
            item("today-1", at(2026, Calendar.OCTOBER, 31, 11)),
            item("today-2", at(2026, Calendar.OCTOBER, 31, 8)),
            item("yesterday-1", at(2026, Calendar.OCTOBER, 30, 20)),
            item("undated-1", null)
        )

        val rows = groupNotifRows(items, now)

        assertEquals(items.size, rows.size)
        assertEquals(NotifDateGroup.TODAY, rows[0].group)
        assertEquals(2, rows[0].groupCount)
        assertNull(rows[1].group)
        assertEquals(NotifDateGroup.YESTERDAY, rows[2].group)
        assertEquals(1, rows[2].groupCount)
        assertEquals(NotifDateGroup.UNDATED, rows[3].group)

        // Sıralama değişmez: gruplama yalnızca araya başlık ekler.
        assertEquals(items.map { it.id }, rows.map { it.item.id })
    }

    @Test
    fun emptyListProducesNoRows() {
        assertEquals(emptyList<NotifGroupedRow>(), groupNotifRows(emptyList(), at(2026, Calendar.OCTOBER, 31)))
    }
}
