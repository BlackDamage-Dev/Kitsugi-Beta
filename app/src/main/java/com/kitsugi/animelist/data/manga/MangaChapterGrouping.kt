package com.kitsugi.animelist.data.manga

import java.util.Locale

/**
 * Aynı bölümün farklı çevirmen (scanlator) sürümlerini temsil eder.
 *
 * Sorun
 * -----
 * Manga kaynakları aynı bölümü birden çok çevirmen üzerinden sunabiliyor:
 *
 * ```
 * Bölüm 3   AnimeWho
 * Bölüm 3   WebtoonTR
 * Bölüm 3   Tempest Fansub
 * ```
 *
 * Liste bu şekilde düz gösterildiğinde kullanıcı aynı bölümü 3 kez görüyor,
 * hangisini açtığını bilmiyor ve "sonraki bölüm" akışı da rastgele bir
 * çevirmene atlıyordu. Bu dosya bölümleri **numara bazında gruplar**; UI
 * grubu tek satır olarak gösterir ve aç/kapa ile çevirmenler açılır.
 *
 * @property key grup kimliği: `n:<numara>` ya da numara yoksa `t:<ad>`
 * @property chapterNumber bölüm numarası (−1 = bilinmiyor)
 * @property displayName grupta gösterilecek ad (en kısa anlamlı varyant adı)
 * @property variants aynı bölümün çevirmen sürümleri (yeni yükleme önce)
 */
data class MangaChapterGroup(
    val key: String,
    val chapterNumber: Float,
    val displayName: String,
    val variants: List<MangaChapter>,
) {
    val variantCount: Int get() = variants.size
    val hasMultipleVariants: Boolean get() = variants.size > 1

    /** Verilen bölüm URL'si bu grubun sürümlerinden biri mi? */
    fun containsUrl(url: String): Boolean = variants.any { it.url == url }
}

object MangaChapterGrouping {

    private val NUMBER_REGEX = Regex("""(\d+(?:[.,]\d+)?)""")

    /** Çevirmen adını karşılaştırma için normalize eder (null/boş → null). */
    fun normalizedScanlator(name: String?): String? =
        name?.trim()?.takeIf { it.isNotEmpty() }

    /** Bölüm numarası: önce `chapterNumber`, yoksa ad içindeki ilk sayı. */
    fun chapterNumberOf(chapter: MangaChapter): Float {
        if (chapter.chapterNumber > 0f) return chapter.chapterNumber
        val match = NUMBER_REGEX.find(chapter.name) ?: return -1f
        return match.groupValues[1].replace(',', '.').toFloatOrNull() ?: -1f
    }

    /** Bölümün grup anahtarı. */
    fun groupKey(chapter: MangaChapter): String {
        val number = chapterNumberOf(chapter)
        if (number > 0f) return "n:" + formatNumber(number)
        return "t:" + chapter.name.trim().lowercase(Locale.ROOT)
    }

    /**
     * Düz bölüm listesini gruplara çevirir.
     * Girdi sırası korunur (kaynaklar genelde yeniden eskiye sıralar).
     */
    fun group(chapters: List<MangaChapter>): List<MangaChapterGroup> {
        if (chapters.isEmpty()) return emptyList()
        val buckets = LinkedHashMap<String, MutableList<MangaChapter>>()
        chapters.forEach { chapter ->
            buckets.getOrPut(groupKey(chapter)) { mutableListOf() }.add(chapter)
        }
        return buckets.map { (key, bucket) ->
            val sorted = bucket.sortedWith(
                compareByDescending<MangaChapter> { it.uploadDate }
                    .thenBy { normalizedScanlator(it.scanlator)?.lowercase(Locale.ROOT) ?: "" }
            )
            val displayName = sorted
                .map { it.name.trim() }
                .filter { it.isNotEmpty() }
                .minByOrNull { it.length }
                ?: sorted.first().name
            MangaChapterGroup(
                key = key,
                chapterNumber = chapterNumberOf(sorted.first()),
                displayName = displayName,
                variants = sorted,
            )
        }
    }

    /**
     * Bir grup için okunacak sürümü seçer.
     *
     * Öncelik:
     *  1. Kullanıcının bu manga için hatırlanan çevirmeni,
     *  2. İlerlemesi (yarım kalmış) olan sürüm,
     *  3. En yeni yüklenen sürüm.
     */
    fun pickVariant(
        group: MangaChapterGroup,
        preferredScanlator: String?,
        hasProgress: (MangaChapter) -> Boolean = { false },
    ): MangaChapter =
        variantForScanlator(group, preferredScanlator)
            ?: group.variants.firstOrNull { hasProgress(it) }
            ?: group.variants.first()

    /** Grupta verilen çevirmene ait sürüm varsa döndürür. */
    fun variantForScanlator(group: MangaChapterGroup, scanlator: String?): MangaChapter? {
        val preferred = normalizedScanlator(scanlator) ?: return null
        return group.variants.firstOrNull {
            normalizedScanlator(it.scanlator)?.equals(preferred, ignoreCase = true) == true
        }
    }

    /** 3.0f → "3", 3.5f → "3.5", 3.50f → "3.5" (anahtarda sondaki sıfır olmasın). */
    private fun formatNumber(value: Float): String {
        val intPart = value.toInt()
        if (value == intPart.toFloat()) return intPart.toString()
        return value.toString().trimEnd('0').trimEnd('.')
    }
}
