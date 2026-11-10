package com.kitsugi.animelist.data.local

import java.util.concurrent.atomic.AtomicLong

/** In-process revision of identity bridges used to build the combined Listem page. */
internal object IdentityCacheRevision {
    private val revision = AtomicLong()

    fun current(): Long = revision.get()

    fun changed() {
        revision.incrementAndGet()
    }
}
