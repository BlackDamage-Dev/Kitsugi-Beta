package com.kitsugi.animelist.ui.screens.mylist

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaIdentity
import com.kitsugi.animelist.ui.utils.plainLabel

/**
 * Immutable search documents for one library snapshot. Build on Dispatchers.Default, not
 * in composition. A keystroke only prepares the query; titles/aliases are never normalized
 * again until the entries change. Documents are position-aligned (not keyed by provider ID,
 * which can collide between sources).
 */
internal class MyListSearchIndex private constructor(
    val entries: List<MediaEntry>,
    private val documents: List<MyListSearchDocument>
) {
    fun matchesAt(index: Int, query: MyListSearchQuery): Boolean = documents[index].matches(query)

    companion object {
        fun build(
            entries: List<MediaEntry>,
            checkActive: () -> Unit = {}
        ): MyListSearchIndex {
            val documents = entries.mapIndexed { index, entry ->
                if (index % 32 == 0) checkActive()
                MyListSearchDocument(entry).also { it.prepare() }
            }
            checkActive()
            return MyListSearchIndex(entries, documents)
        }
    }
}

/** Per-request state, including reusable edit-distance buffers; never shared between jobs. */
internal class MyListSearchQuery(text: String, fuzzySearchEnabled: Boolean) {
    val literal = text.trim().lowercase()
    val isBlank = literal.isBlank()
    val normalizedTitle = if (isBlank) "" else MediaIdentity.normalizedTitle(literal)
    private val fuzzyNeedle = if (fuzzySearchEnabled) normalizeFuzzyText(literal) else ""

    // One/two-letter searches must remain selective. Subsequence/edit-distance matching
    // for "re" otherwise accepts almost the entire library (including one-letter words).
    // An empty Latin-normalized needle (e.g. a Japanese query) must NOT match every title.
    val canMatchFuzzy = fuzzyNeedle.length >= 3
    private val tolerance = when {
        fuzzyNeedle.length <= 4 -> 1
        fuzzyNeedle.length <= 8 -> 2
        else -> 3
    }
    private var previous = IntArray(0)
    private var current = IntArray(0)

    fun matchesFuzzy(text: MyListFuzzyText): Boolean {
        if (!canMatchFuzzy || text.value.isEmpty()) return false
        if (text.value.contains(fuzzyNeedle) || text.initials.contains(fuzzyNeedle)) return true

        var matched = 0
        for (ch in text.value) {
            if (ch == fuzzyNeedle[matched]) {
                matched++
                if (matched == fuzzyNeedle.length) return true
            }
        }
        return text.words.any { withinEditDistance(it) }
    }

    /** Banded Levenshtein: O(word length × tolerance), with length and row cutoffs. */
    private fun withinEditDistance(word: String): Boolean {
        val length = fuzzyNeedle.length
        if (kotlin.math.abs(word.length - length) > tolerance) return false
        if (previous.size != length + 1) {
            previous = IntArray(length + 1)
            current = IntArray(length + 1)
        }
        val outside = tolerance + 1
        for (j in 0..length) previous[j] = minOf(j, outside)
        for (i in 1..word.length) {
            current[0] = i
            val start = maxOf(1, i - tolerance)
            val end = minOf(length, i + tolerance)
            if (start > 1) current[start - 1] = outside
            var minimum = outside
            for (j in start..end) {
                val cost = if (word[i - 1] == fuzzyNeedle[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
                minimum = minOf(minimum, current[j])
            }
            if (end < length) current[end + 1] = outside
            if (minimum > tolerance) return false
            val swap = previous
            previous = current
            current = swap
        }
        return previous[length] <= tolerance
    }
}

/** Also used lazily by the shared, non-indexed filter on other users' lists. */
internal class MyListSearchDocument(entry: MediaEntry) {
    private val titles = listOfNotNull(entry.title, entry.titleEnglish, entry.titleJapanese)
        .filter { it.isNotBlank() }.distinct()
    private val literalFields = (titles + listOfNotNull(
        entry.subtitle,
        entry.type.name,
        entry.status.plainLabel(),
        entry.source,
        entry.year?.toString(),
        entry.malId?.toString()
    )).map { it.lowercase() }
    private val normalizedTitles by lazy(LazyThreadSafetyMode.NONE) {
        titles.map(MediaIdentity::normalizedTitle)
    }
    private val fuzzyTitles by lazy(LazyThreadSafetyMode.NONE) {
        titles.map { MyListFuzzyText(it) }
    }

    fun prepare() {
        normalizedTitles
        fuzzyTitles
    }

    fun matches(query: MyListSearchQuery): Boolean =
        query.isBlank || literalFields.any { it.contains(query.literal) } ||
            (query.normalizedTitle.length >= 2 && normalizedTitles.any { it.contains(query.normalizedTitle) }) ||
            (query.canMatchFuzzy && fuzzyTitles.any(query::matchesFuzzy))
}

internal class MyListFuzzyText(text: String) {
    val value = normalizeFuzzyText(text)
    val words = value.split(' ').filter { it.isNotEmpty() }
    val initials = buildString { words.forEach { append(it.first()) } }
}

// Same character contract as KitsugiContentPrefs, but compile the regex ONCE, not three
// times per entry per keystroke. Native/CJK titles still use literal + NFKC matching above.
private val fuzzySeparators = Regex("[^a-z0-9ğüşıöçâîûêáéíóúàèìòùäëïöü]+")
private fun normalizeFuzzyText(text: String): String =
    text.lowercase().replace(fuzzySeparators, " ").trim()
