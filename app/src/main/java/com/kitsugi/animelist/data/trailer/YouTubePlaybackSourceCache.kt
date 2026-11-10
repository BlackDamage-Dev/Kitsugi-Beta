package com.kitsugi.animelist.data.trailer

import kotlin.math.min

/**
 * Small synchronized LRU for resolved YouTube URLs.
 *
 * Entries live for at most [ttlMs] and never beyond the signed URL expiry (minus a safety
 * margin). Only successful resolutions are cached; failures must remain retryable.
 */
internal class YouTubePlaybackSourceCache(
    private val maxEntries: Int = 24,
    private val ttlMs: Long = 15 * 60 * 1000L,
    private val expirySafetyMarginMs: Long = 60_000L
) {
    private data class Entry(
        val source: TrailerPlaybackSource,
        val validUntilMs: Long
    )

    private val entries = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean =
            size > maxEntries
    }

    @Synchronized
    fun get(videoId: String, nowMs: Long = System.currentTimeMillis()): TrailerPlaybackSource? {
        val entry = entries[videoId] ?: return null
        if (nowMs >= entry.validUntilMs) {
            entries.remove(videoId)
            return null
        }
        return entry.source
    }

    @Synchronized
    fun put(
        videoId: String,
        source: TrailerPlaybackSource,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val ttlExpiry = nowMs + ttlMs
        val signedExpiry = source.earliestYouTubeExpiryEpochSeconds()
            ?.let { epoch -> epoch * 1000L - expirySafetyMarginMs }
        val validUntil = signedExpiry?.let { min(ttlExpiry, it) } ?: ttlExpiry
        if (validUntil <= nowMs) return
        entries[videoId] = Entry(source = source, validUntilMs = validUntil)
    }

    @Synchronized
    fun remove(videoId: String) {
        entries.remove(videoId)
    }

    @Synchronized
    fun clear() = entries.clear()

    @Synchronized
    fun size(): Int = entries.size
}
