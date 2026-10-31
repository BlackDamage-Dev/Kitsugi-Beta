package com.kitsugi.animelist.data.remote

/** Bounded, short-lived negative cache for definitive Bangumi subject 404 responses. */
internal object BangumiSubjectLookupPolicy {
    const val MISS_TTL_MS = 15L * 60L * 1000L

    /** Authenticated and anonymous visibility differ (NSFW); never share their misses. */
    fun missKey(rawId: Int, token: String?): String =
        "$rawId:${token?.hashCode() ?: 0}"

    fun isFreshMiss(cachedAt: Long?, nowMs: Long): Boolean =
        cachedAt != null && nowMs >= cachedAt && nowMs - cachedAt < MISS_TTL_MS
}
