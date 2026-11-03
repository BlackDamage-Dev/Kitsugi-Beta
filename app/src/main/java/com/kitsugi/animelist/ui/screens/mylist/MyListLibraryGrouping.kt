package com.kitsugi.animelist.ui.screens.mylist

import com.kitsugi.animelist.data.remote.BangumiIdNamespace
import com.kitsugi.animelist.data.remote.BangumiTitleCache
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaIdentity
import com.kitsugi.animelist.utils.PreferenceHelpers
import com.kitsugi.animelist.model.MediaType

/**
 * Sekme sırası: 0 = Tümü (birleşik kütüphane), 1..7 = platform kütüphaneleri.
 * "Tümü" artık en başta durur; pager ve hap butonlar aynı sırayı paylaşır.
 */
internal const val MY_LIST_ALL_TAB_INDEX = 0
internal const val MY_LIST_ANILIST_TAB_INDEX = 1
internal const val MY_LIST_MAL_TAB_INDEX = 2
internal const val MY_LIST_SIMKL_TAB_INDEX = 3
internal const val MY_LIST_KITSU_TAB_INDEX = 4
internal const val MY_LIST_SHIKIMORI_TAB_INDEX = 5
internal const val MY_LIST_BANGUMI_TAB_INDEX = 6
internal const val MY_LIST_TMDB_TAB_INDEX = 7
internal const val MY_LIST_TAB_COUNT = 8

/** Verilen kaynağın hangi platform sekmesine ait olduğunu döner (Tümü için null). */
internal fun myListTabIndexForSource(source: String): Int? = when (sourceBadgeId(source)) {
    "anilist" -> MY_LIST_ANILIST_TAB_INDEX
    "mal" -> MY_LIST_MAL_TAB_INDEX
    "simkl" -> MY_LIST_SIMKL_TAB_INDEX
    "kitsu" -> MY_LIST_KITSU_TAB_INDEX
    "shikimori" -> MY_LIST_SHIKIMORI_TAB_INDEX
    "bangumi" -> MY_LIST_BANGUMI_TAB_INDEX
    "tmdb" -> MY_LIST_TMDB_TAB_INDEX
    else -> null
}

/**
 * Bangumi sekmesi yalnızca bir Bangumi subject ID'si taşıyan (Bangumi koleksiyonundan
 * içe aktarılmış veya Bangumi'ye başarıyla eklenmiş) kayıtları gösterir. Eski manuel
 * `source = bangumi` kayıtları hesap koleksiyon üyeliği gibi gösterilmemelidir.
 */
internal fun isBangumiListMember(source: String, malId: Int?): Boolean =
    sourceBadgeId(source) == "bangumi" && malId != null && malId in 500_000_001..599_999_999

/** Only a real account row is membership; a catalog-only TMDB row is not. */
internal fun isTmdbAccountListMember(source: String): Boolean =
    com.kitsugi.animelist.data.auth.isTmdbAccountSource(source)

/** Bir kaydın verilen platform sekmesinde görünüp görünmeyeceğini söyler. */
internal fun myListSourceMatchesTab(tabIndex: Int, source: String, malId: Int? = null): Boolean = when {
    tabIndex == MY_LIST_ALL_TAB_INDEX -> false
    tabIndex == MY_LIST_BANGUMI_TAB_INDEX -> isBangumiListMember(source, malId)
    tabIndex == MY_LIST_TMDB_TAB_INDEX -> isTmdbAccountListMember(source)
    else -> myListTabIndexForSource(source) == tabIndex
}

/**
 * Bir sekmede yeni/manüel kayıt oluşturulurken kullanılacak varsayılan kaynak.
 * Tümü sekmesinde varsayılan AniList'tir; Simkl yalnızca açıkça seçildiğinde kullanılır.
 */
internal fun defaultMyListSourceForTab(tabIndex: Int): String = when (tabIndex) {
    MY_LIST_MAL_TAB_INDEX -> "mal"
    MY_LIST_SIMKL_TAB_INDEX -> "simkl"
    MY_LIST_KITSU_TAB_INDEX -> "kitsu"
    MY_LIST_SHIKIMORI_TAB_INDEX -> "shikimori"
    // Manual entries have no verified Bangumi collection membership.
    MY_LIST_BANGUMI_TAB_INDEX -> "manual"
    MY_LIST_TMDB_TAB_INDEX -> com.kitsugi.animelist.data.auth.TMDB_ACCOUNT_SOURCE
    else -> "anilist"
}

/** Kullanıcıya gösterilecek kısa kaynak adı. */
internal fun myListSourceDisplayName(source: String): String = when (sourceBadgeId(source)) {
    "anilist" -> "AniList"
    "mal" -> "MAL"
    "simkl" -> "Simkl"
    "kitsu" -> "Kitsu"
    "shikimori" -> "Shikimori"
    "bangumi" -> "Bangumi"
    "tmdb" -> "TMDB"
    else -> source
}

/**
 * "Zaten listende var" uyarısı tek yerde üretilir.
 *
 * Mesaj GELEN başlığı değil, listede BULUNAN kaydı ve hangi sekmede olduğunu söyler —
 * kullanıcı uyarıyı görünce Listem'de tam olarak neyi, nerede arayacağını bilsin diye.
 */
internal fun duplicateListMessage(entry: MediaEntry): String =
    "\"${entry.title}\" zaten listende var (${myListSourceDisplayName(entry.source)} sekmesi)."

/**
 * Birleşik kütüphanede bir başlığın temsilci kaydının önceliği.
 * AniList en hızlı ve en zengin kaynak olduğu için ilk sırada, Simkl ise
 * (API'si gecikmeli yanıt verdiğinden) son sırada yer alır.
 * Dizi/film kayıtları yalnızca Simkl/TMDB'de bulunduğu için bu türlerde
 * Simkl doğal olarak temsilci olur.
 */
internal fun myListRepresentativeRank(entry: MediaEntry): Int = when (sourceBadgeId(entry.source)) {
    "anilist" -> 0
    "mal" -> 1
    "kitsu" -> 2
    "shikimori" -> 3
    "bangumi" -> 4
    "simkl" -> 5
    "tmdb" -> 6
    else -> 7
}

/** Daha düşük öncelik numarası kazanır; eşitlikte en son güncellenen kayıt temsilci olur. */
private fun isBetterRepresentative(candidate: MediaEntry, current: MediaEntry): Boolean {
    val candidateRank = myListRepresentativeRank(candidate)
    val currentRank = myListRepresentativeRank(current)
    return when {
        candidateRank != currentRank -> candidateRank < currentRank
        candidate.updatedAt != current.updatedAt -> candidate.updatedAt > current.updatedAt
        else -> candidate.id > current.id
    }
}

/** One visible item in the combined library, with the provider logos that contain it. */
internal data class MyListLibraryItem(
    val entry: MediaEntry,
    val sourceIds: List<String>
)

/**
 * Tek geçişte üretilen kütüphane görünümü:
 * - [combinedItems] / [combinedEntries]: "Tümü" sekmesi için tekilleştirilmiş kartlar ve kaynak rozetleri,
 * - [entriesByTab]: `0..6` her sekme için kesin kaynak izolasyonlu ve kardeş başlık varyantlarıyla
 *   zenginleştirilmiş kayıt listeleri (`0 = Tümü`, `1 = AniList`, `2 = MAL`, `3 = Simkl`,
 *   `4 = Kitsu`, `5 = Shikimori`, `6 = Bangumi`),
 * - [searchAliasesByEntryId]: bir kaydın kendi sekmesinden çıkmadan tüm doğrulanmış kardeş başlıklarıyla
 *   (Romaji, İngilizce, Japonca/Çince/Özgün ve Bangumi önbelleği) aranabilmesini sağlayan eşad kümesi.
 */
internal data class MyListLibrarySnapshot(
    val combinedItems: List<MyListLibraryItem>,
    val combinedEntries: List<MediaEntry>,
    val sourceBadgesByEntryId: Map<Int, List<String>>,
    val entriesByTab: List<List<MediaEntry>>,
    val searchAliasesByEntryId: Map<Int, Set<String>>,
    val isComputed: Boolean = true
) {
    fun entriesForTab(tabIndex: Int): List<MediaEntry> =
        entriesByTab.getOrElse(tabIndex) { emptyList() }

    fun countForTab(tabIndex: Int): Int =
        entriesForTab(tabIndex).size

    companion object {
        val EMPTY = MyListLibrarySnapshot(
            combinedItems = emptyList(),
            combinedEntries = emptyList(),
            sourceBadgesByEntryId = emptyMap(),
            entriesByTab = List(MY_LIST_TAB_COUNT) { emptyList() },
            searchAliasesByEntryId = emptyMap(),
            isComputed = false
        )
    }
}

private val PLACEHOLDER_LIBRARY_TITLE_REGEX =
    Regex("^(Bangumi|Shikimori|Kitsu Anime|Kitsu Manga)\\s*#\\d+$", RegexOption.IGNORE_CASE)

internal fun isPlaceholderLibraryTitle(title: String?): Boolean {
    if (title.isNullOrBlank()) return true
    val trimmed = title.trim()
    if (trimmed.equals("Başlıksız", ignoreCase = true) || trimmed.equals("Untitled", ignoreCase = true)) {
        return true
    }
    return PLACEHOLDER_LIBRARY_TITLE_REGEX.matches(trimmed)
}

private fun isLatinOrNumericTitle(title: String?): Boolean {
    if (title.isNullOrBlank()) return false
    if (PreferenceHelpers.isLatinText(title)) return true
    return title.none { it.isLetter() } && title.any { it.isDigit() }
}

private data class MutableMyListLibraryGroup(
    var representative: MediaEntry,
    val members: MutableList<IdentityKeysOf> = mutableListOf(),
    val sourceIds: LinkedHashSet<String> = linkedSetOf()
)

private class ClusteredGroupsResult(
    val groups: List<MutableMyListLibraryGroup>,
    val parent: MutableList<Int>
) {
    fun root(index: Int): Int {
        var current = index
        while (parent[current] != current) {
            parent[current] = parent[parent[current]] // path halving
            current = parent[current]
        }
        return current
    }
}

private fun clusterMyListGroups(
    entries: List<MediaEntry>,
    identityExtras: Map<Int, Set<String>> = emptyMap()
): ClusteredGroupsResult {
    val groupIndexesByIdentity = mutableMapOf<String, MutableList<Int>>()
    val groups = mutableListOf<MutableMyListLibraryGroup>()

    entries.forEachIndexed { originalIndex, entry ->
        val identity = IdentityKeysOf(
            originalIndex = originalIndex,
            entry = entry,
            keys = if (identityExtras.isEmpty()) MediaIdentity.keys(entry)
            else MediaIdentity.keys(entry, identityExtras[entry.id].orEmpty())
        )

        val candidateKeys = identityCandidateKeys(identity)
        val candidateGroups = candidateKeys
            .asSequence()
            .flatMap { groupIndexesByIdentity[it].orEmpty().asSequence() }
            .distinct()
            .sorted()

        val matchingGroupIndex = candidateGroups.firstOrNull { groupIndex ->
            val groupEntries = groups[groupIndex].members
            groupEntries.none { existing ->
                MediaIdentity.authoritativeConflictingIdentityKeys(
                    a = existing.entry,
                    b = entry,
                    keysA = existing.keys,
                    keysB = identity.keys
                ).isNotEmpty()
            } && groupEntries.any { existing ->
                MediaIdentity.librarySameMedia(
                    a = existing.entry,
                    b = entry,
                    keysA = existing.keys,
                    keysB = identity.keys
                )
            }
        }

        val groupIndex = matchingGroupIndex ?: groups.size.also {
            groups += MutableMyListLibraryGroup(representative = entry)
        }
        val group = groups[groupIndex]

        if (matchingGroupIndex != null) {
            group.members += identity
            if (isBetterRepresentative(entry, group.representative)) {
                group.representative = entry
            }
        } else {
            group.members += identity
        }

        sourceBadgeId(entry.source)?.let(group.sourceIds::add)
        candidateKeys.forEach { key ->
            val indexedGroups = groupIndexesByIdentity.getOrPut(key) { mutableListOf() }
            if (groupIndex !in indexedGroups) indexedGroups += groupIndex
        }
    }

    // ── 2. tur: aynı yapımın farklı gruplarını birleştir ───────────────────────
    val parent = MutableList(groups.size) { it }

    fun root(index: Int): Int {
        var current = index
        while (parent[current] != current) {
            parent[current] = parent[parent[current]] // path halving
            current = parent[current]
        }
        return current
    }

    if (groups.size > 1) {
        fun compatible(left: Int, right: Int): Boolean {
            val leftMembers = groups[left].members
            val rightMembers = groups[right].members
            for (existing in leftMembers) {
                for (candidate in rightMembers) {
                    val conflict = MediaIdentity.authoritativeConflictingIdentityKeys(
                        a = existing.entry,
                        b = candidate.entry,
                        keysA = existing.keys,
                        keysB = candidate.keys
                    )
                    if (conflict.isNotEmpty()) return false
                }
            }
            return leftMembers.any { existing ->
                rightMembers.any { candidate ->
                    MediaIdentity.librarySameMedia(
                        a = existing.entry,
                        b = candidate.entry,
                        keysA = existing.keys,
                        keysB = candidate.keys
                    )
                }
            }
        }

        for (groupIndexes in groupIndexesByIdentity.values) {
            if (groupIndexes.size < 2) continue
            for (i in 0 until groupIndexes.size) {
                for (j in i + 1 until groupIndexes.size) {
                    val leftRoot = root(groupIndexes[i])
                    val rightRoot = root(groupIndexes[j])
                    if (leftRoot == rightRoot) continue
                    if (!compatible(leftRoot, rightRoot)) continue
                    val survivor = minOf(leftRoot, rightRoot)
                    val absorbed = maxOf(leftRoot, rightRoot)
                    val target = groups[survivor]
                    val source = groups[absorbed]
                    target.members += source.members
                    source.sourceIds.forEach { badge ->
                        if (badge !in target.sourceIds) target.sourceIds += badge
                    }
                    if (isBetterRepresentative(source.representative, target.representative)) {
                        target.representative = source.representative
                    }
                    parent[absorbed] = survivor
                }
            }
        }
    }

    return ClusteredGroupsResult(groups, parent)
}

/** Deduplicate inside each provider, never borrow another provider's status or progress. */
internal fun groupMyListProviderTabs(
    entries: List<MediaEntry>,
    identityExtras: Map<Int, Set<String>> = emptyMap()
): Map<Int, List<MediaEntry>> = (1 until MY_LIST_TAB_COUNT).associateWith { tab ->
    groupMyListEntries(
        entries.filter { myListSourceMatchesTab(tab, it.source, it.malId) },
        identityExtras
    ).map { it.entry }
}

/**
 * Collapses copies of the same title across the connected list providers without modifying
 * the underlying records. The representative is the highest-priority provider record; progress and
 * status changes remain scoped to that provider, while [sourceIds] exposes every provider
 * that has a matching record.
 *
 * [identityExtras] maps a row's `MediaEntry.id` to cross-provider identity keys learned
 * elsewhere (`MyListIdentityCrosswalk`: the cross-provider ID cache and the MAL ↔ Bangumi / Kitsu
 * mappings).
 */
internal fun groupMyListEntries(
    entries: List<MediaEntry>,
    identityExtras: Map<Int, Set<String>> = emptyMap(),
    checkCancelled: () -> Unit = {}
): List<MyListLibraryItem> {
    if (entries.isEmpty()) return emptyList()
    val clustered = clusterMyListGroups(entries, identityExtras)
    return clustered.groups.mapIndexedNotNull { groupIndex, group ->
        if (clustered.root(groupIndex) != groupIndex) return@mapIndexedNotNull null
        MyListLibraryItem(
            entry = group.representative.copy(
                isAdult = group.members.any { it.entry.isAdult },
                isFavorite = group.members.any { it.entry.isFavorite }
            ),
            sourceIds = group.sourceIds.toList()
        )
    }
}

/**
 * Tekil sağlayıcı sekmelerinde (`Simkl`, `AniList`, `MAL`, `Kitsu`, `Shikimori`, `Bangumi`)
 * veya profil listelerinde aynı kaynağa ait yinelenen satırları arayüz katmanında da tekilleştirir.
 */
fun deduplicateSingleSourceEntries(
    entries: List<MediaEntry>,
    identityExtras: Map<Int, Set<String>> = emptyMap()
): List<MediaEntry> {
    if (entries.size <= 1) return entries
    return groupMyListEntries(entries, identityExtras).map { it.entry }
}

internal fun buildMyListLibrarySnapshot(
    entries: List<MediaEntry>,
    identityExtras: Map<Int, Set<String>> = emptyMap(),
    includeAdult: Boolean = true,
    bangumiTitleLookup: (Int) -> BangumiTitleCache.LatinTitles? = { null }
): MyListLibrarySnapshot {
    if (entries.isEmpty()) {
        return MyListLibrarySnapshot.EMPTY.copy(isComputed = true)
    }

    val clustered = clusterMyListGroups(entries, identityExtras)
    val groups = clustered.groups

    val combinedItems = ArrayList<MyListLibraryItem>(groups.size)
    val enrichedByOriginalIndex = arrayOfNulls<MediaEntry>(entries.size)
    val searchAliasesByEntryId = LinkedHashMap<Int, Set<String>>(entries.size)

    groups.forEachIndexed { groupIndex, group ->
        if (clustered.root(groupIndex) != groupIndex) return@forEachIndexed

        val groupIsAdult = group.members.any { it.entry.isAdult }
        if (!includeAdult && groupIsAdult) {
            group.members.forEach { member ->
                enrichedByOriginalIndex[member.originalIndex] = member.entry.copy(isAdult = true)
            }
            return@forEachIndexed
        }
        val rankedMembers = group.members.sortedWith { left, right ->
            val rankDiff = myListRepresentativeRank(left.entry) - myListRepresentativeRank(right.entry)
            if (rankDiff != 0) rankDiff else right.entry.id.compareTo(left.entry.id)
        }

        val bangumiCachedList = group.members.mapNotNull { member ->
            if (sourceBadgeId(member.entry.source) == "bangumi") {
                BangumiIdNamespace.rawIdOrNull(member.entry.malId)?.let(bangumiTitleLookup)
            } else {
                null
            }
        }

        val bestLatinTitle = rankedMembers.firstNotNullOfOrNull { member ->
            member.entry.title.takeIf { !isPlaceholderLibraryTitle(it) && isLatinOrNumericTitle(it) }
        } ?: bangumiCachedList.firstNotNullOfOrNull { cached ->
            cached.romaji?.takeIf { !isPlaceholderLibraryTitle(it) && isLatinOrNumericTitle(it) }
        }

        val bestEnglishTitle = rankedMembers.firstNotNullOfOrNull { member ->
            member.entry.titleEnglish?.takeIf { !isPlaceholderLibraryTitle(it) && isLatinOrNumericTitle(it) }
        } ?: bangumiCachedList.firstNotNullOfOrNull { cached ->
            cached.english?.takeIf { !isPlaceholderLibraryTitle(it) && isLatinOrNumericTitle(it) }
        }

        val bestNativeTitle = rankedMembers.firstNotNullOfOrNull { member ->
            member.entry.titleJapanese?.takeIf { !isPlaceholderLibraryTitle(it) && PreferenceHelpers.hasCjkCharacters(it) }
        } ?: rankedMembers.firstNotNullOfOrNull { member ->
            member.entry.title.takeIf { !isPlaceholderLibraryTitle(it) && PreferenceHelpers.hasCjkCharacters(it) }
        } ?: bangumiCachedList.firstNotNullOfOrNull { cached ->
            cached.native?.takeIf { !isPlaceholderLibraryTitle(it) }
        } ?: rankedMembers.firstNotNullOfOrNull { member ->
            member.entry.titleJapanese?.takeIf { !isPlaceholderLibraryTitle(it) }
        }

        val groupSearchAliases = LinkedHashSet<String>()
        fun addAlias(value: String?) {
            val trimmed = value?.trim()?.takeIf { it.isNotEmpty() && !isPlaceholderLibraryTitle(it) } ?: return
            groupSearchAliases += trimmed
        }
        group.members.forEach { member ->
            addAlias(member.entry.title)
            addAlias(member.entry.titleEnglish)
            addAlias(member.entry.titleJapanese)
        }
        bangumiCachedList.forEach { cached ->
            addAlias(cached.romaji)
            addAlias(cached.english)
            addAlias(cached.native)
        }

        // "Tümü" kartı, yapının herhangi bir kaynakta kütüphaneye İLK girdiği andır.
        // Okuma yolu, tek sekmedeki sıralamayla aynı çözümleyiciyi kullanmak zorunda:
        // aksi halde aynı yapım AniList sekmesinde tarihli, "Tümü"de "bilinmiyor"
        // kovasında görünüyordu (kopyalardan birinin alanı boş olduğu için).
        val earliestAddedAt = group.members
            .map { it.entry.effectiveAddedSeconds() }
            .filter { it > 0L }
            .minOrNull()
            ?: group.representative.effectiveAddedSeconds()

        val enrichedRepresentative = enrichEntryFromGroup(
            entry = group.representative,
            bestLatinTitle = bestLatinTitle,
            bestEnglishTitle = bestEnglishTitle,
            bestNativeTitle = bestNativeTitle,
            groupIsAdult = groupIsAdult
        ).copy(
            addedAt = earliestAddedAt,
            isFavorite = group.members.any { it.entry.isFavorite }
        )
        val badges = group.sourceIds.toList()
        combinedItems += MyListLibraryItem(
            entry = enrichedRepresentative,
            sourceIds = badges
        )
        if (groupSearchAliases.isNotEmpty()) {
            val existingRepAliases = searchAliasesByEntryId[enrichedRepresentative.id]
            searchAliasesByEntryId[enrichedRepresentative.id] =
                if (existingRepAliases == null) groupSearchAliases else existingRepAliases + groupSearchAliases
        }

        group.members.forEach { member ->
            val enrichedMember = enrichEntryFromGroup(
                entry = member.entry,
                bestLatinTitle = bestLatinTitle,
                bestEnglishTitle = bestEnglishTitle,
                bestNativeTitle = bestNativeTitle,
                groupIsAdult = groupIsAdult
            )
            enrichedByOriginalIndex[member.originalIndex] = enrichedMember
            if (groupSearchAliases.isNotEmpty()) {
                val existingAliases = searchAliasesByEntryId[enrichedMember.id]
                searchAliasesByEntryId[enrichedMember.id] =
                    if (existingAliases == null) groupSearchAliases else existingAliases + groupSearchAliases
            }
        }
    }

    val combinedEntries = combinedItems.map { it.entry }
    val sourceBadgesByEntryId = combinedItems.associate { it.entry.id to it.sourceIds }

    val tabBuckets = Array(MY_LIST_TAB_COUNT) { ArrayList<MediaEntry>() }
    tabBuckets[MY_LIST_ALL_TAB_INDEX].addAll(combinedEntries)

    val enrichedEntries = entries.mapIndexed { index, entry -> enrichedByOriginalIndex[index] ?: entry }
    val candidateEntries = if (includeAdult) enrichedEntries else enrichedEntries.filterNot { it.isAdult }
    val providerTabs = groupMyListProviderTabs(candidateEntries, identityExtras)
    for (tab in 1 until MY_LIST_TAB_COUNT) {
        tabBuckets[tab].addAll(providerTabs[tab].orEmpty())
    }

    return MyListLibrarySnapshot(
        combinedItems = combinedItems,
        combinedEntries = combinedEntries,
        sourceBadgesByEntryId = sourceBadgesByEntryId,
        entriesByTab = tabBuckets.map { it },
        searchAliasesByEntryId = searchAliasesByEntryId,
        isComputed = true
    )
}

private fun enrichEntryFromGroup(
    entry: MediaEntry,
    bestLatinTitle: String?,
    bestEnglishTitle: String?,
    bestNativeTitle: String?,
    groupIsAdult: Boolean
): MediaEntry {
    val currentTitle = entry.title
    val shouldUpgradePrimaryTitle = isPlaceholderLibraryTitle(currentTitle) || !isLatinOrNumericTitle(currentTitle)
    val resolvedTitle = if (shouldUpgradePrimaryTitle) {
        bestLatinTitle ?: bestEnglishTitle ?: currentTitle
    } else {
        currentTitle
    }

    val currentEnglish = entry.titleEnglish
    val resolvedEnglish = if (currentEnglish.isNullOrBlank() || !isLatinOrNumericTitle(currentEnglish)) {
        bestEnglishTitle ?: currentEnglish
    } else {
        currentEnglish
    }

    val currentJapanese = entry.titleJapanese
    val resolvedJapanese = if (currentJapanese.isNullOrBlank()) {
        bestNativeTitle ?: currentTitle.takeIf { PreferenceHelpers.hasCjkCharacters(it) }
    } else {
        currentJapanese
    }

    return if (
        resolvedTitle == entry.title &&
        resolvedEnglish == entry.titleEnglish &&
        resolvedJapanese == entry.titleJapanese &&
        groupIsAdult == entry.isAdult
    ) {
        entry
    } else {
        entry.copy(
            title = resolvedTitle,
            titleEnglish = resolvedEnglish,
            titleJapanese = resolvedJapanese,
            isAdult = groupIsAdult
        )
    }
}

/** A row together with its original position and resolved identity keys (own provider IDs + crosswalk extras). */
private data class IdentityKeysOf(
    val originalIndex: Int,
    val entry: MediaEntry,
    val keys: Set<String>
)

/** Canonical IDs are shared by source logos, source tabs, and search-result aliases. */
internal fun sourceBadgeId(source: String): String? = when (source.trim().lowercase()) {
    "anilist", "al" -> "anilist"
    "mal", "myanimelist", "jikan", "jikan (mal)", "mal (jikan)" -> "mal"
    "simkl" -> "simkl"
    "kitsu" -> "kitsu"
    "shikimori", "shiki" -> "shikimori"
    "bangumi", "bgm", "bgm.tv" -> "bangumi"
    "tmdb", "themoviedb", "tmdb_account" -> "tmdb"
    else -> null
}

/**
 * Keys used to *find* candidate groups. They are a superset of the identity keys (normalized
 * titles included) — the identity check below still has to confirm the match, so a loose title
 * key can only ever produce a candidate, never a merge.
 */
private fun identityCandidateKeys(identity: IdentityKeysOf): Set<String> = buildSet {
    val entry = identity.entry
    val watchable = entry.type == MediaType.Anime || entry.type == MediaType.Movie || entry.type == MediaType.TvShow
    identity.keys.forEach { key ->
        add("id:$key")
        if (watchable) {
            val parts = key.split(':')
            if (parts.size == 3) add("xid:${parts[1]}:${parts[2]}")
        }
    }
    listOfNotNull(entry.title, entry.titleEnglish, entry.titleJapanese).forEach { title ->
        MediaIdentity.normalizedTitleVariants(title).forEach { normalized ->
            if (normalized.isNotBlank()) {
                add("title:${entry.type.name}:$normalized")
                if (watchable) add("xtitle:${entry.year ?: 0}:$normalized")
            }
        }
    }
    // Anime filmler TMDB/Simkl'da Movie, anime sağlayıcılarında Anime olarak bulunabilir:
    // yalnızca film/tek-bölüm uyumlu satırlarda tür-bağımsız aday anahtarı da üretilir.
    val canMatchAsAnimeMovie = entry.type == MediaType.Movie ||
        (entry.type == MediaType.Anime &&
            (entry.total == null || entry.total <= 1) &&
            entry.progress <= 1)
    if (canMatchAsAnimeMovie) {
        entry.simklId?.takeIf { it > 0 }?.let { add("amovie:simkl:$it") }
        entry.tmdbId?.takeIf { it > 0 }?.let { add("amovie:tmdb:$it") }
        MediaIdentity.realMalId(entry)?.let { add("amovie:mal:$it") }
        identity.keys.forEach { key ->
            val parts = key.split(':')
            if (parts.size == 3 && parts[1] == "mal") add("amovie:mal:${parts[2]}")
        }
        listOfNotNull(entry.title, entry.titleEnglish, entry.titleJapanese).forEach { title ->
            MediaIdentity.normalizedTitleVariants(title).forEach { normalized ->
                if (normalized.isNotBlank()) add("amovie:title:$normalized")
            }
        }
    }
}
