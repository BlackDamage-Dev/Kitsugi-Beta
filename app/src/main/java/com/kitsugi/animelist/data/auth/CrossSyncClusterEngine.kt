package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaIdentity
import com.kitsugi.animelist.model.MediaType

/**
 * Pure, deterministic multi-provider cluster engine for bulk cross-sync (`AuthViewModel.syncPlatforms`).
 *
 * Extracted from `AuthViewModel` so every edge case (multi-script `Kaiju No. 8` across
 * AniList + MAL + Simkl + Kitsu + Shikimori + Bangumi, order independence across arbitrary
 * provider permutations, crosswalk-backed Bangumi/Kitsu IDs, and corrupted provider mappings)
 * runs directly under fast JVM unit tests with zero Android framework mocks.
 */
object CrossSyncClusterEngine {

    /** Canonical platform slots on a unified cross-sync record. */
    enum class Slot {
        ANILIST,
        MAL,
        SIMKL,
        KITSU,
        SHIKIMORI,
        BANGUMI,
        TMDB
    }

    data class ClusterGroup(
        var aniList: MediaEntry? = null,
        var mal: MediaEntry? = null,
        var simkl: MediaEntry? = null,
        var kitsu: MediaEntry? = null,
        var shikimori: MediaEntry? = null,
        var bangumi: MediaEntry? = null,
        var tmdb: MediaEntry? = null,
        var identityReviewRequired: Boolean = false,
        var identityReviewDetails: String? = null,
        /** Yalnız şüpheli gelen satırlar; doğrulanabilir küme çekirdeği bunların dışında kalır. */
        var identityReviewEntryIds: Set<Int> = emptySet()
    ) {
        val candidates: List<MediaEntry>
            get() = listOfNotNull(aniList, mal, simkl, kitsu, shikimori, bangumi, tmdb)

        val actionableCandidates: List<MediaEntry>
            get() = candidates.filterNot { it.id in identityReviewEntryIds }

        fun isIdentityReviewed(entry: MediaEntry): Boolean = entry.id in identityReviewEntryIds

        fun setSlot(slot: Slot, entry: MediaEntry) {
            when (slot) {
                Slot.ANILIST -> aniList = entry
                Slot.MAL -> mal = entry
                Slot.SIMKL -> simkl = entry
                Slot.KITSU -> kitsu = entry
                Slot.SHIKIMORI -> shikimori = entry
                Slot.BANGUMI -> bangumi = entry
                Slot.TMDB -> tmdb = entry
            }
        }

        fun getSlot(slot: Slot): MediaEntry? = when (slot) {
            Slot.ANILIST -> aniList
            Slot.MAL -> mal
            Slot.SIMKL -> simkl
            Slot.KITSU -> kitsu
            Slot.SHIKIMORI -> shikimori
            Slot.BANGUMI -> bangumi
            Slot.TMDB -> tmdb
        }

        fun absorbFrom(other: ClusterGroup) {
            if (aniList == null) aniList = other.aniList
            if (mal == null) mal = other.mal
            if (simkl == null) simkl = other.simkl
            if (kitsu == null) kitsu = other.kitsu
            if (shikimori == null) shikimori = other.shikimori
            if (bangumi == null) bangumi = other.bangumi
            if (tmdb == null) tmdb = other.tmdb
            if (other.identityReviewRequired) {
                identityReviewRequired = true
                identityReviewEntryIds = identityReviewEntryIds + other.identityReviewEntryIds
                if (identityReviewDetails == null) {
                    identityReviewDetails = other.identityReviewDetails
                }
            }
        }
    }

    sealed class DiagnosticEvent {
        data class SameTitleIdConflict(
            val entry: MediaEntry,
            val conflictingCandidates: List<MediaEntry>,
            val conflictKeys: List<String>
        ) : DiagnosticEvent()

        data class SharedIdentityMerged(
            val entry: MediaEntry,
            val sharedIds: List<String>,
            val bridgedAgainst: List<MediaEntry>
        ) : DiagnosticEvent()

        data class DisambiguatedFromMultipleCandidates(
            val entry: MediaEntry,
            val byUniqueSharedId: Boolean,
            val selectedGroupIndex: Int,
            val candidateGroups: List<List<MediaEntry>>
        ) : DiagnosticEvent()

        data class MarkedIdentityReview(
            val entry: MediaEntry,
            val sharedIds: List<String>,
            val suspiciousMatches: List<MediaEntry>,
            val reason: ReviewReason,
            val candidateGroups: List<List<MediaEntry>> = emptyList()
        ) : DiagnosticEvent()
    }

    enum class ReviewReason {
        UNRELATED_SHARED_IDS,
        REJECTED_TITLE_OR_YEAR,
        AMBIGUOUS_CANDIDATES
    }

    /** Assigns unique synthetic local IDs (`1..N`) before computing crosswalk keys so `id == 0` rows never collide. */
    fun assignClusterRowIds(batches: List<Pair<Slot, List<MediaEntry>>>): List<Pair<Slot, List<MediaEntry>>> {
        var nextId = 1
        return batches.map { (slot, list) ->
            slot to list.map { entry -> entry.copy(id = nextId++) }
        }
    }

    /** Extracts a verified real MAL ID from candidates or crosswalk keys (`Anime:mal:52588`). */
    fun extractCrosswalkOrCandidateMalId(
        candidates: List<MediaEntry>,
        keysOf: (MediaEntry) -> Set<String>
    ): Int? {
        val animeOrMangaCandidates = candidates.filter { it.type == MediaType.Anime || it.type == MediaType.Manga }
        animeOrMangaCandidates.firstNotNullOfOrNull { MediaIdentity.realMalId(it) }?.let { return it }
        for (candidate in animeOrMangaCandidates) {
            val prefix = "${candidate.type.name}:mal:"
            val fromCrosswalk = keysOf(candidate)
                .firstNotNullOfOrNull { key ->
                    key.takeIf { it.startsWith(prefix) }
                        ?.removePrefix(prefix)
                        ?.toIntOrNull()
                        ?.takeIf { it in 1..99_999_999 }
                }
            if (fromCrosswalk != null) return fromCrosswalk
        }
        return null
    }

    fun titleAliases(entry: MediaEntry): Set<String> = listOfNotNull(
        entry.title,
        entry.titleEnglish,
        entry.titleJapanese
    ).map { MediaIdentity.normalizedTitle(it) }
        .filter { it.length >= 2 }
        .toSet()

    class Session(
        identityExtras: Map<Int, Set<String>> = emptyMap(),
        private val onDiagnostic: ((DiagnosticEvent) -> Unit)? = null
    ) {
        private val index = CrossSyncCandidateIndex<ClusterGroup>(identityExtras)
        private val groups = mutableListOf<ClusterGroup>()

        fun keysOf(entry: MediaEntry): Set<String> = index.keysOf(entry)

        fun groupsSnapshot(): List<ClusterGroup> = groups.toList()

        fun addEntry(slot: Slot, entry: MediaEntry): ClusterGroup {
            val identity = MediaIdentity
            val entryKeys = index.keysOf(entry)
            val entryTitles = titleAliases(entry)

            // Transitive union with conflict protection (mirrors MyListLibraryGrouping):
            // an entry matches a group when NO member conflicts on provider IDs/types and AT LEAST
            // ONE member satisfies MediaIdentity.sameMedia(existing, entry).
            val matches = index.possibleMatches(entry).filter { group ->
                // Daha önce incelemeye ayrılmış satır, sonraki kayıtlar için kimlik/başlık
                // köprüsü olarak kullanılamaz; güvenli çekirdek eşleşmeye devam eder.
                val members = group.actionableCandidates
                members.isNotEmpty() &&
                    members.none { existing ->
                        identity.conflictingIdentityKeys(
                            typeA = existing.type,
                            typeB = entry.type,
                            keysA = index.keysOf(existing),
                            keysB = entryKeys
                        ).isNotEmpty()
                    } &&
                    members.any { existing ->
                        identity.sameMedia(
                            a = existing,
                            b = entry,
                            keysA = index.keysOf(existing),
                            keysB = entryKeys
                        )
                    }
            }

            val primaryEnglishTitles = listOfNotNull(entry.title, entry.titleEnglish)
                .map { identity.normalizedTitle(it) }
                .filter { it.length >= 2 }
                .toSet()

            val sameTitleIdConflicts = index.sharingTitleAlias(entry).filter { group ->
                group.candidates.any { candidate ->
                    candidate.type == entry.type &&
                        identity.conflictingIdentityKeys(
                            typeA = candidate.type,
                            typeB = entry.type,
                            keysA = index.keysOf(candidate),
                            keysB = entryKeys
                        ).isNotEmpty() &&
                        listOfNotNull(candidate.title, candidate.titleEnglish)
                            .map { identity.normalizedTitle(it) }
                            .filter { it.length >= 2 }
                            .toSet()
                            .intersect(primaryEnglishTitles)
                            .isNotEmpty()
                }
            }

            if (sameTitleIdConflicts.isNotEmpty() && matches.isEmpty()) {
                val conflictKeys = sameTitleIdConflicts.flatMap { group ->
                    group.candidates.flatMap { candidate ->
                        identity.conflictingIdentityKeys(
                            typeA = candidate.type,
                            typeB = entry.type,
                            keysA = index.keysOf(candidate),
                            keysB = entryKeys
                        )
                    }
                }.distinct()
                onDiagnostic?.invoke(
                    DiagnosticEvent.SameTitleIdConflict(
                        entry = entry,
                        conflictingCandidates = sameTitleIdConflicts.flatMap { it.candidates }.distinct(),
                        conflictKeys = conflictKeys
                    )
                )
            }

            val targetGroup = when {
                matches.isEmpty() -> ClusterGroup().also { groups.add(it) }
                matches.size == 1 -> {
                    val match = matches.single()
                    val sharedCandidates = match.actionableCandidates.filter { candidate ->
                        index.keysOf(candidate).intersect(entryKeys).isNotEmpty()
                    }
                    val sharedIds = sharedCandidates
                        .flatMap { index.keysOf(it).intersect(entryKeys) }
                        .distinct()
                    val nonIdenticalTitleHolders = sharedCandidates.filter { candidate ->
                        val candTitles = titleAliases(candidate)
                        candTitles.isNotEmpty() && entryTitles.isNotEmpty() &&
                            candTitles.intersect(entryTitles).isEmpty()
                    }
                    if (nonIdenticalTitleHolders.isNotEmpty()) {
                        val suspicious = CrossSyncIdentityGuard.findSuspiciousSharedIdCandidates(
                            existingCandidates = match.actionableCandidates,
                            incoming = entry,
                            sharedIdCandidates = nonIdenticalTitleHolders
                        )
                        if (suspicious.isNotEmpty()) {
                            match.identityReviewRequired = true
                            match.identityReviewEntryIds = match.identityReviewEntryIds + entry.id
                            onDiagnostic?.invoke(
                                DiagnosticEvent.MarkedIdentityReview(
                                    entry = entry,
                                    sharedIds = sharedIds,
                                    suspiciousMatches = suspicious,
                                    reason = ReviewReason.UNRELATED_SHARED_IDS
                                )
                            )
                        } else {
                            onDiagnostic?.invoke(
                                DiagnosticEvent.SharedIdentityMerged(
                                    entry = entry,
                                    sharedIds = sharedIds,
                                    bridgedAgainst = nonIdenticalTitleHolders
                                )
                            )
                        }
                    }
                    match
                }
                else -> {
                    // When a multi-alias bridge entry (e.g. AniList carrying both "Kaijuu 8-gou",
                    // "Kaiju No. 8", and "怪獣８号") arrives AFTER Kitsu ("Kaiju No. 8"), Shikimori
                    // ("Kaijuu 8-gou"), and Bangumi ("怪獣8号") opened separate groups, coalesce all
                    // non-conflicting component groups cleanly into one single group.
                    if (canCoalesceAllMatches(matches, entry, entryKeys)) {
                        val primary = matches.first()
                        for (secondary in matches.drop(1)) {
                            primary.absorbFrom(secondary)
                            groups.remove(secondary)
                            index.replaceGroup(secondary, primary)
                            secondary.candidates.forEach { member -> index.add(primary, member) }
                        }
                        primary
                    } else {
                        val keyMatches = matches.filter { candidateGroup ->
                            candidateGroup.actionableCandidates.any { index.keysOf(it).intersect(entryKeys).isNotEmpty() }
                        }
                        val normalizedEntryTitle = identity.normalizedTitle(entry.title)
                        val exactTitleMatches = matches.filter { candidateGroup ->
                            candidateGroup.actionableCandidates.any { candidate ->
                                val normalizedCandidateTitle = identity.normalizedTitle(candidate.title)
                                normalizedCandidateTitle.isNotBlank() && normalizedCandidateTitle == normalizedEntryTitle
                            }
                        }
                        val resolvedMatch = when {
                            keyMatches.size == 1 -> keyMatches.single()
                            keyMatches.isEmpty() && exactTitleMatches.size == 1 -> exactTitleMatches.single()
                            else -> null
                        }
                        val sharedInResolved = resolvedMatch?.actionableCandidates.orEmpty().filter {
                            index.keysOf(it).intersect(entryKeys).isNotEmpty()
                        }
                        val suspiciousInResolved = if (resolvedMatch != null) {
                            CrossSyncIdentityGuard.findSuspiciousSharedIdCandidates(
                                existingCandidates = resolvedMatch.actionableCandidates,
                                incoming = entry,
                                sharedIdCandidates = sharedInResolved
                            )
                        } else emptyList()

                        if (resolvedMatch != null && suspiciousInResolved.isEmpty()) {
                            onDiagnostic?.invoke(
                                DiagnosticEvent.DisambiguatedFromMultipleCandidates(
                                    entry = entry,
                                    byUniqueSharedId = keyMatches.size == 1,
                                    selectedGroupIndex = matches.indexOf(resolvedMatch) + 1,
                                    candidateGroups = matches.map { it.candidates }
                                )
                            )
                            resolvedMatch
                        } else {
                            onDiagnostic?.invoke(
                                DiagnosticEvent.MarkedIdentityReview(
                                    entry = entry,
                                    sharedIds = emptyList(),
                                    suspiciousMatches = suspiciousInResolved,
                                    reason = if (resolvedMatch != null) ReviewReason.REJECTED_TITLE_OR_YEAR
                                    else ReviewReason.AMBIGUOUS_CANDIDATES,
                                    candidateGroups = matches.map { it.candidates }
                                )
                            )
                            ClusterGroup(
                                identityReviewRequired = true,
                                identityReviewEntryIds = setOf(entry.id)
                            ).also { groups.add(it) }
                        }
                    }
                }
            }

            targetGroup.setSlot(slot, entry)
            index.add(targetGroup, entry)
            return targetGroup
        }

        private fun canCoalesceAllMatches(
            matches: List<ClusterGroup>,
            incoming: MediaEntry,
            incomingKeys: Set<String>
        ): Boolean {
            if (matches.any { it.actionableCandidates.isEmpty() }) return false
            // 1. No two matched groups may already fill the same provider slot with distinct rows,
            //    and no pair across any two groups may conflict on provider IDs or years.
            for (i in matches.indices) {
                for (j in i + 1 until matches.size) {
                    val left = matches[i]
                    val right = matches[j]
                    if (Slot.entries.any { slot -> left.getSlot(slot) != null && right.getSlot(slot) != null }) {
                        return false
                    }
                    for (a in left.actionableCandidates) {
                        for (b in right.actionableCandidates) {
                            if (MediaIdentity.conflictingIdentityKeys(
                                    typeA = a.type,
                                    typeB = b.type,
                                    keysA = index.keysOf(a),
                                    keysB = index.keysOf(b)
                                ).isNotEmpty()
                            ) {
                                return false
                            }
                            if (!CrossSyncIdentityGuard.clusterYearsCompatible(a.year, b.year)) {
                                return false
                            }
                        }
                    }
                }
            }
            // 2. Combined pool must also pass shared-ID relatedness without suspicion.
            val combinedCandidates = matches.flatMap { it.actionableCandidates }
            val sharedWithIncoming = combinedCandidates.filter {
                index.keysOf(it).intersect(incomingKeys).isNotEmpty()
            }
            return CrossSyncIdentityGuard.findSuspiciousSharedIdCandidates(
                existingCandidates = combinedCandidates,
                incoming = incoming,
                sharedIdCandidates = sharedWithIncoming
            ).isEmpty()
        }
    }
}
