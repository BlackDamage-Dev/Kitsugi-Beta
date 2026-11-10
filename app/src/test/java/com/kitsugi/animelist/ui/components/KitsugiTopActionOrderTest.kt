package com.kitsugi.animelist.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ortak üst çubuk aksiyonlarının kanonik sırasını kilitler.
 *
 * Kullanıcı sözleşmesi: aynı butonlar her ekranda aynı göreli konumda durur ve
 * **bildirim butonu her zaman en sağdaki** butondur. Bu test, o sözleşmenin
 * ileride yapılan düzenlemelerle sessizce bozulmasını engeller.
 */
class KitsugiTopActionOrderTest {

    @Test
    fun `notifications is always the rightmost action`() {
        val ordered = KitsugiTopAction.canonicalOrder(KitsugiTopAction.entries.toList())
        assertEquals(KitsugiTopAction.NOTIFICATIONS, ordered.last())
    }

    @Test
    fun `explore header keeps notifications right of random`() {
        val ordered = KitsugiTopAction.canonicalOrder(
            listOf(
                KitsugiTopAction.NOTIFICATIONS,
                KitsugiTopAction.RANDOM,
                KitsugiTopAction.HISTORY,
                KitsugiTopAction.DOWNLOADS
            )
        )
        assertEquals(
            listOf(
                KitsugiTopAction.HISTORY,
                KitsugiTopAction.DOWNLOADS,
                KitsugiTopAction.RANDOM,
                KitsugiTopAction.NOTIFICATIONS
            ),
            ordered
        )
    }

    @Test
    fun `duplicate actions collapse to a single button`() {
        val ordered = KitsugiTopAction.canonicalOrder(
            listOf(
                KitsugiTopAction.HISTORY,
                KitsugiTopAction.HISTORY,
                KitsugiTopAction.NOTIFICATIONS
            )
        )
        assertEquals(listOf(KitsugiTopAction.HISTORY, KitsugiTopAction.NOTIFICATIONS), ordered)
    }

    @Test
    fun `input order does not change the result`() {
        val canonical = KitsugiTopAction.canonicalOrder(KitsugiTopAction.entries.toList())
        val reversed = KitsugiTopAction.canonicalOrder(KitsugiTopAction.entries.toList().reversed())
        assertEquals(canonical, reversed)
    }

    @Test
    fun `every action has a distinct order value`() {
        val orders = KitsugiTopAction.entries.map { it.order }
        assertTrue("Aksiyon sıraları tekil olmalı", orders.distinct().size == orders.size)
    }
}
