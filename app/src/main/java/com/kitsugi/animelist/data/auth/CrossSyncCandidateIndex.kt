package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaIdentity

/**
 * Narrows cross-sync matching to groups that could possibly share a provider ID or title.
 * The caller still applies MediaIdentity.sameMedia() to each candidate, so the index never
 * makes a match decision by itself. This avoids scanning every previous group for every entry.
 */
internal class CrossSyncCandidateIndex<Group : Any>(
    private val identityExtras: Map<Int, Set<String>> = emptyMap()
) {
    private val groupsByIdentityKey = mutableMapOf<String, LinkedHashSet<Group>>()
    private val groupsByPrimaryEnglishTitle = mutableMapOf<String, LinkedHashSet<Group>>()
    private val groupsByAnyTitle = mutableMapOf<String, LinkedHashSet<Group>>()

    fun keysOf(entry: MediaEntry, extraKeys: Set<String> = emptySet()): Set<String> {
        val extras = if (extraKeys.isNotEmpty()) extraKeys else identityExtras[entry.id].orEmpty()
        return MediaIdentity.keys(entry, extras)
    }

    fun add(group: Group, entry: MediaEntry, extraKeys: Set<String> = emptySet()) {
        keysOf(entry, extraKeys).forEach { key ->
            groupsByIdentityKey.getOrPut(key) { linkedSetOf() }.add(group)
        }
        primaryEnglishAliases(entry).forEach { alias ->
            val k = titleKey(entry, alias)
            groupsByPrimaryEnglishTitle.getOrPut(k) { linkedSetOf() }.add(group)
            groupsByAnyTitle.getOrPut(k) { linkedSetOf() }.add(group)
        }
        japaneseAlias(entry)?.let { alias ->
            groupsByAnyTitle.getOrPut(titleKey(entry, alias)) { linkedSetOf() }.add(group)
        }
    }

    /**
     * Replaces every indexed reference to [oldGroup] with [newGroup] when two component clusters
     * coalesce into a single cluster, preventing absorbed stale groups from appearing in later
     * [possibleMatches] / [sharingTitleAlias] lookups.
     */
    fun replaceGroup(oldGroup: Group, newGroup: Group) {
        if (oldGroup === newGroup) return
        fun rewrite(map: MutableMap<String, LinkedHashSet<Group>>) {
            for (bucket in map.values) {
                if (bucket.remove(oldGroup)) {
                    bucket.add(newGroup)
                }
            }
        }
        rewrite(groupsByIdentityKey)
        rewrite(groupsByPrimaryEnglishTitle)
        rewrite(groupsByAnyTitle)
    }

    /** Returns a small superset of groups that may satisfy MediaIdentity.sameMedia(). */
    fun possibleMatches(entry: MediaEntry, extraKeys: Set<String> = emptySet()): Set<Group> = LinkedHashSet<Group>().apply {
        keysOf(entry, extraKeys).forEach { key -> addAll(groupsByIdentityKey[key].orEmpty()) }
        primaryEnglishAliases(entry).forEach { alias -> addAll(groupsByAnyTitle[titleKey(entry, alias)].orEmpty()) }
        japaneseAlias(entry)?.let { alias -> addAll(groupsByAnyTitle[titleKey(entry, alias)].orEmpty()) }
    }

    /** Only primary/English aliases participate in the conflicting-ID safety check. */
    fun sharingTitleAlias(entry: MediaEntry): Set<Group> = LinkedHashSet<Group>().apply {
        primaryEnglishAliases(entry).forEach { alias ->
            addAll(groupsByPrimaryEnglishTitle[titleKey(entry, alias)].orEmpty())
        }
    }

    private fun primaryEnglishAliases(entry: MediaEntry): Set<String> = listOfNotNull(
        entry.title,
        entry.titleEnglish
    ).map { MediaIdentity.normalizedTitle(it) }
        .filter { it.isNotBlank() }
        .toSet()

    private fun japaneseAlias(entry: MediaEntry): String? = entry.titleJapanese
        ?.let { MediaIdentity.normalizedTitle(it) }
        ?.takeIf { it.length >= 2 }

    private fun titleKey(entry: MediaEntry, alias: String): String = "${entry.type.name}:$alias"
}
