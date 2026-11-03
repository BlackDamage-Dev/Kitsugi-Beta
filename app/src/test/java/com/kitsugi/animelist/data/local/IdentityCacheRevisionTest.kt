package com.kitsugi.animelist.data.local

import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityCacheRevisionTest {
    @Test
    fun concurrentIdentityWritesAdvanceTheRevision() {
        val before = IdentityCacheRevision.current()
        val writers = List(4) {
            Thread { repeat(250) { IdentityCacheRevision.changed() } }
        }
        writers.forEach(Thread::start)
        writers.forEach(Thread::join)
        assertTrue(IdentityCacheRevision.current() - before >= 1_000)
    }
}
