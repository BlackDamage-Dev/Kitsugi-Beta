package com.kitsugi.animelist.data.manga

/**
 * Manga bölüm listesi sıralaması.
 *
 * ## Okuma sırası kuralı
 * Bir manga bölüm listesi **her zaman 1'den başlar** ve numara büyüdükçe ilerler; yeni
 * bölüm listenin sonuna eklenir. Bu yüzden uygulamanın **varsayılan** sıralaması
 * [OldestFirst]'tir. (Önceki sürümde varsayılan [NewestFirst] idi; 386 bölümlük bir seride
 * liste 386 → 381… şeklinde başlıyor ve "bölüm 1 nerede" sorusunu doğuruyordu.)
 *
 * [NewestFirst] hâlâ bir seçenektir (yeni bölümü takip edenler için) ama artık **kullanıcı
 * tercihi**dir, zorunlu başlangıç değil. Tercih Ayarlar > Manga Okuyucu'dan yapılır,
 * [com.kitsugi.animelist.data.settings.SettingsDataStore] içinde kalıcıdır ve istenirse
 * manga bazında ezilebilir.
 */
enum class ChapterSortMode {
    /** Bölüm numarasına göre artan (1 → 232). **Varsayılan.** */
    OldestFirst,

    /** Bölüm numarasına göre azalan (232 → 1). */
    NewestFirst,

    /** Yükleme tarihine göre azalan; tarih yoksa bölüm numarasına düşer. */
    RecentlyUpdated;

    fun next(): ChapterSortMode = when (this) {
        OldestFirst -> NewestFirst
        NewestFirst -> RecentlyUpdated
        RecentlyUpdated -> OldestFirst
    }

    /** Arayüzde "varsayılan" sayılan mod — kullanıcı tercihini sıfırlarken de bu kullanılır. */
    val isDefault: Boolean get() = this == DEFAULT

    companion object {
        /**
         * Uygulamanın varsayılan sıralaması: **1 → N (artan)**.
         *
         * Ekranlarda `ChapterSortMode.NewestFirst` gibi sabit değerler yerine bu sabit
         * kullanılmalıdır; tek satır değişiklikle tüm uygulamanın başlangıç sırası değişir.
         */
        val DEFAULT: ChapterSortMode = OldestFirst

        /** Kaydedilmiş addan güvenli çözümleme; bilinmeyen/boş değer varsayılana düşer. */
        fun fromName(name: String?): ChapterSortMode =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * Bölüm satırının **başlık biçimi**.
 *
 * Mihon/TachiyomiSY'deki "Chapter number / Chapter title" görünüm anahtarıyla aynı işi yapar:
 * bazı kaynaklar başlığı "Bölüm 42 - Kılıçların Gecesi" gibi uzun verir, bazıları yalnızca
 * "42" verir. Kullanıcı listeyi numara odaklı veya başlık odaklı görmeyi seçebilir.
 */
enum class ChapterDisplayMode {
    /** Yalnızca numara: "Bölüm 42" (numara yoksa başlığa düşer). Varsayılan. */
    Number,

    /** Kaynağın verdiği başlık olduğu gibi. */
    Title;

    fun next(): ChapterDisplayMode = if (this == Number) Title else Number

    companion object {
        val DEFAULT: ChapterDisplayMode = Number

        fun fromName(name: String?): ChapterDisplayMode =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * Bölüm listesini istenen sıralamaya göre dizer.
 *
 * Sıralama **kararlıdır**: bölüm numarası bilinmeyen (-1) kayıtlar kendi aralarında
 * eklentiden gelen sırayı korur, böylece her yenilemede liste zıplamaz.
 */
fun List<MangaChapter>.sortedForMode(mode: ChapterSortMode): List<MangaChapter> = when (mode) {
    ChapterSortMode.NewestFirst -> sortedWith(
        compareByDescending<MangaChapter> { it.chapterNumber }.thenByDescending { it.uploadDate }
    )

    ChapterSortMode.OldestFirst -> sortedWith(
        compareBy<MangaChapter> { it.chapterNumber }.thenBy { it.uploadDate }
    )

    ChapterSortMode.RecentlyUpdated -> sortedWith(
        compareByDescending<MangaChapter> { it.uploadDate }.thenByDescending { it.chapterNumber }
    )
}

/**
 * Bölüm numarası bilinmeyen kayıtlar için isimden numara çıkarır.
 *
 * Bazı kaynaklar `chapterNumber` alanını hiç doldurmaz (-1 gelir). O durumda liste
 * alfabetik olarak "1, 10, 100, 11…" diye dizilir ki bu okunamaz bir sıradır. Burada
 * [ChapterRecognition] ile başlıktan numara çıkarılır; bulunamazsa -1 korunur ve kayıt
 * sıralamada sona düşer (kararlı sıralama sayesinde kendi aralarında kaynak sırası kalır).
 */
fun List<MangaChapter>.withResolvedNumbers(mangaTitle: String = ""): List<MangaChapter> =
    map { chapter ->
        if (chapter.chapterNumber >= 0f) {
            chapter
        } else {
            val parsed = ChapterRecognition.parseChapterNumber(
                mangaTitle = mangaTitle,
                chapterName = chapter.name,
                chapterNumber = null
            ).toFloat()
            if (parsed >= 0f) chapter.copy(chapterNumber = parsed) else chapter
        }
    }

/**
 * Bölüm satırı etiketi — [ChapterDisplayMode] ve numara bilgisine göre.
 *
 * Numara bilinmiyorsa ve [ChapterDisplayMode.Number] seçiliyse başlığa düşülür; böylece
 * hiçbir satır "Bölüm -1" gibi anlamsız bir metin göstermez.
 */
fun MangaChapter.displayLabel(mode: ChapterDisplayMode, numberPrefix: String): String {
    val hasNumber = chapterNumber >= 0f
    return when {
        mode == ChapterDisplayMode.Title -> name
        hasNumber -> {
            val formatted = formatChapterNumber(chapterNumber)
            if (numberPrefix.isBlank()) formatted else "$numberPrefix $formatted"
        }
        else -> name
    }
}

/**
 * 42.0 → "42", 42.5 → "42.5" — kayan nokta artıklarını (42.000001) temizler.
 */
fun formatChapterNumber(value: Float): String {
    if (!value.isFinite() || value < 0f) return ""
    val asInt = value.toInt()
    return if (kotlin.math.abs(value - asInt) < 0.001f) asInt.toString()
    else {
        // En fazla 2 ondalık, sondaki sıfırlar atılır.
        String.format(java.util.Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
    }
}

/**
 * Bölüm numaraları dizisinde **eksik** olan tam sayıları bulur (Mihon'daki
 * "indicate missing chapters" davranışı).
 *
 * Yalnızca tam sayı numaralar dikkate alınır; 42.5 gibi ara bölümler ve negatif
 * (bilinmeyen) numaralar yok sayılır. Sonuç artan sıradadır.
 */
fun List<Float>.missingChapterNumbers(maxReported: Int = 20): List<String> {
    val ints = this.filter { it >= 0f && it == kotlin.math.floor(it).toFloat() }
        .map { it.toInt() }
        .distinct()
        .sorted()
    if (ints.size < 2) return emptyList()
    val present = ints.toHashSet()
    val first = ints.first()
    val last = ints.last()
    // 1'den başlamıyorsa başlangıç da eksik sayılır (kaynak 3'ten başlıyorsa 1-2 yok).
    val start = minOf(first, 1)
    val missing = ArrayList<String>()
    for (n in start..last) {
        if (n !in present) {
            missing += n.toString()
            if (missing.size >= maxReported) break
        }
    }
    return missing
}

/**
 * Aynı bölüm numarasının farklı gruplarda (scanlator) tekrarlandığı numaralar.
 *
 * Liste gerçekten "çoğalabiliyor": aynı 42. bölüm hem "MangaciX" hem "Şımarık Fansub"
 * tarafından yüklenmiş olabilir. Mihon bu durumu "duplicate chapters" olarak işaretler ve
 * istenirse gizler; burada da aynı davranış uygulanır.
 */
fun List<MangaChapter>.duplicatedChapterNumbers(): Set<Float> =
    groupingBy { it.chapterNumber }
        .eachCount()
        .filter { (number, count) -> number >= 0f && count > 1 }
        .keys
