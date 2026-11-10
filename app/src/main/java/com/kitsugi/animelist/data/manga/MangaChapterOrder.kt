package com.kitsugi.animelist.data.manga

/**
 * Bölüm listesi için kullanıcıya açık seçenekler.
 *
 * Mihon / Tachiyomi paritesinde olan, Kitsugi'de eksik olan iki temel kontrol:
 *  - sıralama yönü (en yeni üstte / bölüm numarasına göre artan-azalan)
 *  - okuma durumuna göre filtre (okunmamış / okunmuş / indirilebilir)
 */

/** Bölüm listesi sıralaması. */
enum class ChapterSort {
    /** En yeni eklenen üstte (kaynağın doğal sırası — varsayılan). */
    NewestFirst,

    /** Bölüm numarasına göre azalan (137 → 1). */
    NumberDesc,

    /** Bölüm numarasına göre artan (1 → 137). "Baştan oku" akışı. */
    NumberAsc,

    /** A→Z başlık sıralaması. */
    TitleAsc;

    companion object {
        fun fromId(id: String?): ChapterSort =
            entries.firstOrNull { it.name == id } ?: NewestFirst
    }
}

/** Bölüm listesi filtresi. */
enum class ChapterFilter {
    /** Tüm bölümler. */
    All,

    /** Yalnızca okunmamış + devam edenler. */
    Unread,

    /** Yalnızca okunmuş (bitirilmiş) bölümler. */
    Read,

    /** Tarama grubu (scanlator) veya numarası olmayan "duyuru" bölümlerini gizle. */
    HideMisc;

    companion object {
        fun fromId(id: String?): ChapterFilter =
            entries.firstOrNull { it.name == id } ?: All
    }
}

/**
 * Modelden bağımsız (test edilebilir) bölüm sıralama/filtre motoru.
 *
 * @param T bölüm satırı tipi (UI'da ChapterRow, testlerde basit veri sınıfı).
 */
/**
 * Bölüm geçişi (ileri/geri) için **okuma sırası** yardımcısı.
 *
 * Kaynaklar bölüm listesini kimi zaman yeniden eskiye, kimi zaman eskiden yeniye
 * döndürür. Okuyucudaki "sonraki bölüm" düğmesi bir ara listeyi hep ters kabul
 * ediyordu; artan sırada veren kaynaklarda ileri/geri ters çalışıyordu.
 *
 * Bu yardımcı sırayı kaynaktan bağımsız kurar:
 *  1. Numarası çözülebilen bölümler varsa **numaraya göre artan** (1 → N),
 *  2. Hiç numara yoksa kaynak sırasının tersi (yaygın kaynak davranışı).
 */
object MangaChapterSequence {

    /**
     * @param chapters (url, numara) çiftleri — numara bilinmiyorsa negatif.
     * @return Okuma sırasına göre URL listesi (1. bölüm başta).
     */
    fun readingOrder(chapters: List<Pair<String, Float>>): List<String> {
        if (chapters.isEmpty()) return emptyList()
        val numbered = chapters.filter { it.second >= 0f }
        val unnumbered = chapters.filter { it.second < 0f }
        return if (numbered.size >= 2) {
            numbered.sortedBy { it.second }.map { it.first } + unnumbered.map { it.first }
        } else {
            chapters.map { it.first }.reversed()
        }
    }

    /** Verilen bölümden SONRAKİ bölümün URL'si; yoksa null. */
    fun nextUrl(chapters: List<Pair<String, Float>>, currentUrl: String): String? {
        val ordered = readingOrder(chapters)
        val idx = ordered.indexOf(currentUrl)
        return if (idx >= 0 && idx < ordered.size - 1) ordered[idx + 1] else null
    }

    /** Verilen bölümden ÖNCEKİ bölümün URL'si; yoksa null. */
    fun previousUrl(chapters: List<Pair<String, Float>>, currentUrl: String): String? {
        val ordered = readingOrder(chapters)
        val idx = ordered.indexOf(currentUrl)
        return if (idx > 0) ordered[idx - 1] else null
    }
}

object MangaChapterOrdering {

    /** Tüm tarama gruplarını (scanlator) listeler — çip filtresi için. */
    fun scanlatorsOf(names: List<String?>): List<String> =
        names.asSequence()
            .mapNotNull { it?.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .toList()

    /**
     * Filtre + sıralama uygular.
     *
     * @param rows Kaynak sırası korunmuş bölüm satırları (en yeni → en eski).
     * @param numbers Her satır için çözülmüş bölüm numarası (bilinmiyorsa negatif).
     */
    fun <T> apply(
        rows: List<T>,
        filter: ChapterFilter,
        sort: ChapterSort,
        scanlatorFilter: String? = null,
        number: (T) -> Float = { -1f },
        isCompleted: (T) -> Boolean = { false },
        scanlator: (T) -> String? = { null },
        title: (T) -> String = { "" },
    ): List<T> {
        val filtered = rows.filter { row ->
            val passesFilter = when (filter) {
                ChapterFilter.All -> true
                ChapterFilter.Unread -> !isCompleted(row)
                ChapterFilter.Read -> isCompleted(row)
                ChapterFilter.HideMisc -> scanlator(row)?.isNotBlank() == true && number(row) >= 0f
            }
            if (!passesFilter) return@filter false

            if (scanlatorFilter != null && !containsScanlator(scanlator(row), scanlatorFilter)) return@filter false
            true
        }

        return when (sort) {
            // Kaynak sırası korunur (en yeni üstte) — ANCAK kaynak listeyi eskiden
            // yeniye veriyorsa "en yeni üstte" istediği sonuç ters olur. Bu durumu
            // numaralardan anlar ve düzeltir (bazı HTML/API kaynakları artan sırada
            // döndürüyor; okuyucuda 1. bölüm en üstte görünüyordu).
            ChapterSort.NewestFirst -> if (looksAscending(filtered, number)) {
                sortByNumber(filtered, number, ascending = false)
            } else {
                filtered
            }
            ChapterSort.NumberDesc -> sortByNumber(filtered, number, ascending = false)
            ChapterSort.NumberAsc -> sortByNumber(filtered, number, ascending = true)
            ChapterSort.TitleAsc -> filtered.sortedWith { a, b -> compareTitles(title(a), title(b)) }
        }
    }

    /**
     * Liste numaralara göre **eskiden yeniye** mi sıralı?
     *
     * Yalnızca en az 3 numaralı satır varsa ve ilk satırlar son satırlardan
     * küçükse true döner (salt-artan kontrolü). Numarasız bölümler hesaba katılmaz.
     */
    private fun <T> looksAscending(rows: List<T>, number: (T) -> Float): Boolean {
        val numbers = rows.map(number).filter { it >= 0f }
        if (numbers.size < 3) return false
        var ascendingPairs = 0
        var descendingPairs = 0
        for (i in 0 until numbers.size - 1) {
            if (numbers[i] < numbers[i + 1]) ascendingPairs++
            if (numbers[i] > numbers[i + 1]) descendingPairs++
        }
        return ascendingPairs > descendingPairs
    }

    /**
     * Bir bölümün scanlator alanı "A, B" gibi virgülle çoklu grup içerebilir;
     * çip filtresi her birini ayrı ayrı eşleştirir.
     */
    fun containsScanlator(value: String?, scanlator: String): Boolean {
        if (value.isNullOrBlank() || scanlator.isBlank()) return false
        return value.split(',', '/', '&')
            .map { it.trim() }
            .any { it.equals(scanlator.trim(), ignoreCase = true) }
    }

    private fun <T> sortByNumber(list: List<T>, number: (T) -> Float, ascending: Boolean): List<T> {
        val comparator = Comparator<T> { a, b ->
            val na = number(a)
            val nb = number(b)
            // Numarası bilinmeyenler (örn. "Duyuru") her iki yönde de en sonda kalır.
            when {
                na < 0f && nb < 0f -> 0
                na < 0f -> 1
                nb < 0f -> -1
                else -> if (ascending) na.compareTo(nb) else nb.compareTo(na)
            }
        }
        // Sabit sıralama: aynı numaralı kayıtlar (çoklu çeviri grubu) kaynak sırasını korur.
        return list.sortedWith(comparator)
    }

    private fun compareTitles(a: String, b: String): Int =
        java.text.Collator.getInstance(java.util.Locale("tr", "TR")).let { collator ->
            collator.strength = java.text.Collator.PRIMARY
            collator.compare(a, b)
        }
}
