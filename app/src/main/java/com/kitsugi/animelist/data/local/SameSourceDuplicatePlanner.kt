package com.kitsugi.animelist.data.local

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaIdentity
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Plans legacy same-provider duplicate cleanup without touching the database.
 *
 * The old implementation compared every row with every previous cluster and rebuilt
 * Unicode/title/ID keys inside every comparison. On the main dispatcher this blocked
 * input for 14+ seconds (v2.4.387 ANR reports). This planner indexes exact identity and
 * title tokens once, then evaluates only candidate clusters with the existing identity
 * rules. The index is a LOSSLESS candidate filter: every possible librarySameMedia()
 * match shares at least one indexed token. Final decisions still use MediaIdentity,
 * including year, episode-count and authoritative-ID guards.
 *
 * Cluster order and the non-transitive "no conflict with ALL, match with ANY" rule are
 * deliberately preserved. A title/ID must never cause two different productions to merge
 * merely because both resemble a third row.
 */
internal object SameSourceDuplicatePlanner {
    internal data class Plan(
        val clusters: List<List<MediaEntry>>,
        val candidateComparisons: Int
    )

    private class Fingerprint(val entry: MediaEntry) {
        val keys: Set<String> = MediaIdentity.keys(entry)
        val primaryKeys: Set<String> = keys.filterNotTo(HashSet()) { it.startsWith("Anime:tmdb:") }
        val tokens: Set<String> = buildSet {
            keys.forEach { add("id:$it") }
            MediaIdentity.realMalId(entry)?.let { add("mal-axis:$it") }
            // Anime↔Movie bridging compares these axes WITHOUT the media-type prefix.
            entry.simklId?.takeIf { it > 0 }?.let { add("video:simkl:$it") }
            entry.tmdbId?.takeIf { it > 0 }?.let { add("video:tmdb:$it") }
            // titleMatch accepts an equal primary title even when it is one character.
            MediaIdentity.normalizedTitle(entry.title).takeIf { it.isNotEmpty() }?.let { add("title:$it") }
            listOfNotNull(entry.title, entry.titleEnglish).forEach { title ->
                MediaIdentity.normalizedTitleVariants(title).filter { it.length >= 2 }
                    .forEach { add("title:$it") }
            }
            listOfNotNull(entry.title, entry.titleEnglish, entry.titleJapanese).forEach { title ->
                MediaIdentity.normalizedTitle(title).takeIf { it.length >= 2 }?.let { add("title:$it") }
            }
        }
        val namespaces: Map<String, Set<String>> = keys.groupBy(
            { it.substringBeforeLast(':') }, { it.substringAfterLast(':') }
        ).mapValues { (_, values) -> values.toSet() }
    }

    private class Cluster(val index: Int, val first: Fingerprint) {
        val members = mutableListOf(first)
        /** Common values across members that actually carry the namespace. */
        val commonNamespaceValues = first.namespaces.mapValuesTo(HashMap()) { (_, values) -> values.toSet() }
        var commonPrimaryKeys: Set<String> = first.primaryKeys
        var uniformType: MediaType? = first.entry.type
        var hasAnyKeys: Boolean = first.keys.isNotEmpty()

        fun add(member: Fingerprint) {
            members += member
            hasAnyKeys = hasAnyKeys || member.keys.isNotEmpty()
            if (uniformType != member.entry.type) uniformType = null
            commonPrimaryKeys = commonPrimaryKeys.intersect(member.primaryKeys)
            member.namespaces.forEach { (namespace, values) ->
                val previous = commonNamespaceValues[namespace]
                commonNamespaceValues[namespace] = previous?.intersect(values) ?: values.toSet()
            }
        }

        /**
         * Exact fast path only when a shared value proves compatibility with EVERY member.
         * Otherwise fall back to the original per-member predicate; an aggregate miss is
         * not itself a conflict (an enriched row may share different IDs with each member).
         */
        fun definitelyHasNoIdentityConflict(incoming: Fingerprint): Boolean {
            if (uniformType == null || uniformType != incoming.entry.type) return false
            if (incoming.keys.isEmpty() || !hasAnyKeys) return true
            var hasTmdbDifference = false
            for ((namespace, incomingValues) in incoming.namespaces) {
                val common = commonNamespaceValues[namespace] ?: continue
                if (common.intersect(incomingValues).isEmpty()) {
                    if (uniformType != MediaType.Anime || namespace != "Anime:tmdb") return false
                    hasTmdbDifference = true
                }
            }
            // Check all non-TMDB namespaces before applying the anime exception.
            val hasOtherCommonNamespaceConflict = incoming.namespaces.any { (namespace, values) ->
                namespace != "Anime:tmdb" && commonNamespaceValues[namespace]?.intersect(values)?.isEmpty() == true
            }
            if (hasOtherCommonNamespaceConflict) return false
            return !hasTmdbDifference || commonPrimaryKeys.intersect(incoming.primaryKeys).isNotEmpty()
        }
    }

    suspend fun plan(entries: List<MediaEntry>): Plan {
        if (entries.size <= 1) return Plan(emptyList(), 0)
        val bySource = LinkedHashMap<String, MutableList<MediaEntry>>()
        entries.forEach { entry ->
            bySource.getOrPut(MediaIdentity.canonicalSource(entry.source)) { mutableListOf() } += entry
        }
        val duplicateClusters = mutableListOf<List<MediaEntry>>()
        var candidateComparisons = 0
        var processed = 0
        for ((_, sourceEntries) in bySource) {
            if (sourceEntries.size <= 1) continue
            val clusters = mutableListOf<Cluster>()
            val postings = HashMap<String, MutableSet<Int>>()
            for (entry in sourceEntries) {
                currentCoroutineContext().ensureActive()
                if (++processed % 128 == 0) kotlinx.coroutines.yield()
                val fingerprint = Fingerprint(entry)
                val candidates = sortedSetOf<Int>()
                fingerprint.tokens.forEach { token -> postings[token]?.let(candidates::addAll) }
                var matching: Cluster? = null
                for (clusterIndex in candidates) {
                    val cluster = clusters[clusterIndex]
                    candidateComparisons++
                    val noConflicts = cluster.definitelyHasNoIdentityConflict(fingerprint) ||
                        cluster.members.none { member ->
                            MediaIdentity.authoritativeConflictingIdentityKeys(
                                member.entry, fingerprint.entry, member.keys, fingerprint.keys
                            ).isNotEmpty()
                        }
                    if (!noConflicts) continue
                    val matchesAny = cluster.members.any { member ->
                        MediaIdentity.librarySameMedia(
                            member.entry, fingerprint.entry, member.keys, fingerprint.keys
                        )
                    }
                    if (matchesAny) {
                        matching = cluster
                        break
                    }
                }
                val target = matching ?: Cluster(clusters.size, fingerprint).also {
                    clusters += it
                    fingerprint.tokens.forEach { token ->
                        postings.getOrPut(token) { HashSet() } += it.index
                    }
                }
                if (matching != null) {
                    target.add(fingerprint)
                    // Index the joined member too: a later row may match ONLY this member's
                    // IDs/titles, not the cluster founder's.
                    fingerprint.tokens.forEach { token ->
                        postings.getOrPut(token) { HashSet() } += target.index
                    }
                }
            }
            clusters.forEach { cluster ->
                if (cluster.members.size > 1) duplicateClusters += cluster.members.map { it.entry }
            }
        }
        return Plan(duplicateClusters, candidateComparisons)
    }

    /** Differential-test oracle only; production never calls this quadratic path. */
    internal suspend fun legacyPlanForTest(entries: List<MediaEntry>): Plan {
        val bySource = LinkedHashMap<String, MutableList<MediaEntry>>()
        entries.forEach { bySource.getOrPut(MediaIdentity.canonicalSource(it.source)) { mutableListOf() } += it }
        val clusters = mutableListOf<List<MediaEntry>>()
        for ((_, rows) in bySource) {
            val groups = mutableListOf<MutableList<MediaEntry>>()
            for (entry in rows) {
                currentCoroutineContext().ensureActive()
                val keys = MediaIdentity.keys(entry)
                val match = groups.firstOrNull { group ->
                    group.none { MediaIdentity.authoritativeConflictingIdentityKeys(it, entry).isNotEmpty() } &&
                        group.any { MediaIdentity.librarySameMedia(it, entry) }
                }
                if (match == null) groups += mutableListOf(entry) else match += entry
            }
            groups.filterTo(clusters) { it.size > 1 }
        }
        return Plan(clusters, 0)
    }

}
