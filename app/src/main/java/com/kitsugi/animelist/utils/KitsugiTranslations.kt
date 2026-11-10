package com.kitsugi.animelist.utils

import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.data.remote.KitsugiVoiceActor

// ─── Dil Yardımcısı ────────────────────────────────────────────────────────────

/**
 * Aktif uygulama dili Türkçe mi? Önce uygulama içi/per-app dil tercihini, sonra
 * uygulama kaynaklarının yerel ayarını ve son olarak sistem varsayılanını kullanır.
 */
fun isTurkish(): Boolean {
    val savedTag = runCatching { com.kitsugi.animelist.LocaleCache.localeTag }.getOrNull()
        ?.takeIf { it != com.kitsugi.animelist.LocaleCache.UNSET && it.isNotBlank() }
    if (savedTag != null) return java.util.Locale.forLanguageTag(savedTag).language.equals("tr", ignoreCase = true)

    val frameworkTag = runCatching {
        androidx.appcompat.app.AppCompatDelegate.getApplicationLocales().toLanguageTags()
    }.getOrNull()?.takeIf { it.isNotBlank() }
    if (frameworkTag != null) {
        return java.util.Locale.forLanguageTag(frameworkTag.substringBefore(',')).language.equals("tr", ignoreCase = true)
    }

    val appLocale = runCatching {
        com.kitsugi.animelist.KitsugiApplication.getInstance()?.resources?.configuration?.locales?.get(0)
    }.getOrNull()
    return (appLocale ?: java.util.Locale.getDefault()).language.equals("tr", ignoreCase = true)
}

/**
 * Kitsugi Türkçe Çeviri Eşleme Yardımcıları
 *
 * Bu dosya, API kaynaklarından (Jikan/MAL, AniList, TMDB) gelen
 * sabit İngilizce değerleri Türkçeye çeviren saf (pure) eşleme fonksiyonlarını içerir.
 * Google Translate KULLANILMAZ — tüm eşlemeler statik tablolardır.
 */

// ─── Tür / Genre Çevirisi ──────────────────────────────────────────────────────

private val genreMap = mapOf(
    // Temel Türler
    "Action" to "Aksiyon",
    "Adventure" to "Macera",
    "Comedy" to "Komedi",
    "Drama" to "Dram",
    "Fantasy" to "Fantastik",
    "Horror" to "Korku",
    "Mystery" to "Gizem",
    "Romance" to "Romantik",
    "Sci-Fi" to "Bilim Kurgu",
    "Science Fiction" to "Bilim Kurgu",
    "Slice of Life" to "Günlük Yaşam",
    "Sports" to "Spor",
    "Supernatural" to "Doğaüstü",
    "Thriller" to "Gerilim",
    "Psychological" to "Psikolojik",
    "Ecchi" to "Ecchi",
    "Hentai" to "Hentai",
    "Erotica" to "Erotik",
    "Mecha" to "Mecha",
    "Music" to "Müzik",
    "Historical" to "Tarihi",
    "Military" to "Askeri",
    "Space" to "Uzay",
    "Magic" to "Büyü",
    "Martial Arts" to "Dövüş Sanatları",
    "School" to "Okul",
    "Game" to "Oyun",
    "Racing" to "Yarış",
    "Parody" to "Parodi",
    "Samurai" to "Samuray",
    "Vampire" to "Vampir",
    "Demons" to "İblisler",
    "Josei" to "Josei",
    "Seinen" to "Seinen",
    "Shounen" to "Shōnen",
    "Shoujo" to "Shōjo",
    "Kids" to "Çocuklara Yönelik",
    "Avant Garde" to "Avangart",
    "Award Winning" to "Ödüllü",
    "Boys Love" to "Boys Love",
    "Girls Love" to "Girls Love",
    "Suspense" to "Gerilim",
    "Gourmet" to "Gurme",
    "Work Life" to "İş Hayatı",
    "Adult Cast" to "Yetişkin Kadro",
    "Anthropomorphic" to "Antropomorfik",
    "CGDCT" to "CGDCT",
    "Childcare" to "Çocuk Bakımı",
    "Combat Sports" to "Dövüş Sporları",
    "Crossdressing" to "Zıt Cins Kılığı",
    "Delinquents" to "Asi Gençler",
    "Detective" to "Dedektif",
    "Educational" to "Eğitici",
    "Erotica" to "Erotik",
    "Food" to "Yemek",
    "Gore" to "Kan ve Şiddet",
    "Harem" to "Harem",
    "High Stakes Game" to "Yüksek Riskli Oyun",
    "Isekai" to "Isekai",
    "Iyashikei" to "Iyashikei",
    "Love Polygon" to "Aşk Çokgeni",
    "Magical Sex Shift" to "Büyülü Cinsiyet Değişimi",
    "Mahou Shoujo" to "Sihirli Kız",
    "Medical" to "Tıp",
    "Mythology" to "Mitoloji",
    "Organized Crime" to "Organize Suç",
    "Otaku Culture" to "Otaku Kültürü",
    "Performing Arts" to "Sahne Sanatları",
    "Pets" to "Evcil Hayvanlar",
    "Reincarnation" to "Reenkarnasyon",
    "Reverse Harem" to "Ters Harem",
    "Romantic Subtext" to "Romantik Alt Metin",
    "Showbiz" to "Şov Dünyası",
    "Strategy Game" to "Strateji Oyunu",
    "Super Power" to "Süper Güç",
    "Survival" to "Hayatta Kalma",
    "Team Sports" to "Takım Sporu",
    "Time Travel" to "Zaman Yolculuğu",
    "Video Game" to "Video Oyunu",
    "Visual Arts" to "Görsel Sanatlar",
    "Workplace" to "İş Yeri",
    "Mythology" to "Mitoloji",
)

/**
 * API'dan gelen tür adını arayüz diline çevirir. Eşleme yoksa orijinali döner.
 *
 * **Tek giriş noktası** [toLocalizedTaxonomyLabel]'dir: Bangumi (Çince/Japonca), Shikimori
 * (Rusça) ve İngilizce kaynaklar aynı çözümleyiciden geçer; çözülemeyen Kiril metni Latin
 * harflerine çevrilir, böylece ekranda Rusça tür adı görünmez.
 */
fun String.toTurkishGenre(): String {
    val cleaned = this.trim()
    if (cleaned.isEmpty()) return cleaned
    return cleaned.toLocalizedTaxonomyLabel()
}

/** Tür listesini Türkçeye çevirir. */
fun List<String>.toTurkishGenres(): List<String> = map { it.toTurkishGenre() }

// ─── Bangumi Etiketleri (Çince / Japonca) ─────────────────────────────────────

/**
 * Bangumi etiket ve türleri Çince/Japonca gelir (恋爱, 治愈系, 京阿尼, 神作, 2008年 ...).
 * Tüm eşleme tablosu tek yerde, [BangumiTagDictionary] içinde tutulur. Burada yalnızca
 * arayüz dili seçimi (Türkçe / İngilizce) ve `bangumi_tag_*` dil dosyası override'ı yapılır.
 */

/** Uygulama arayüzü İngilizce mi? (Ayar dili → [Locale.setDefault] ile senkron gelir.) */
fun isEnglish(): Boolean = java.util.Locale.getDefault().language.equals("en", ignoreCase = true)

/**
 * İsimle string kaynağı okuma önbelleği (anahtar: `dil:kaynakAdı`).
 *
 * Uygulamada ~40.000 `bangumi_*` dizesi var (üç vault modülü + uygulama). Android'in
 * `Resources.getIdentifier` çağrısı **her seferinde tüm kaynak tablosunu tarar**; isim
 * başına 20-40 ms sürebiliyor ve `Resources` içindeki kilit yüzünden tüm iş parçacıklarını
 * sıraya sokuyor. Bangumi ekranları (etiket çipleri, görev adları, kurum adları) bunu yüzlerce
 * kez tetiklediği için veri "dakikalarca geç" geliyordu.
 *
 * Bu önbellek hem bulunan hem **bulunamayan** isimleri saklar; böylece aynı isim ikinci kez
 * aranmaz. Anahtar arayüz dilini içerir, dil değişince yeni değerler okunur.
 */
private val stringResourceCache = java.util.concurrent.ConcurrentHashMap<String, String>()

/**
 * `com.kitsugi.animelist.R.string` içindeki statik `int` kimliklerinin O(1) haritası.
 *
 * `Resources.getIdentifier` 58.000+ dize içeren kaynak tablosunun tamamını tarayıp global
 * `AssetManager` kilidini tuttuğu için detay sayfasında yüzlerce etiket/stüdyo/görev çipi
 * açılırken ana iş parçacığını saniyelerce dondurabiliyordu. Bu harita sınıf alanlarını tek
 * seferde belleğe indeksler (`getIdentifier` çağrısını sıfırlar).
 */
private val appStringIdMap: Map<String, Int> by lazy {
    try {
        val fields = com.kitsugi.animelist.R.string::class.java.declaredFields
        val map = HashMap<String, Int>(fields.size * 2)
        for (field in fields) {
            if (field.type == Int::class.javaPrimitiveType) {
                map[field.name] = field.getInt(null)
            }
        }
        map
    } catch (_: Throwable) {
        emptyMap()
    }
}

internal fun prewarmTranslationCaches() {
    appStringIdMap.size
    BangumiTagDictionary.prewarm()
    BangumiOrgDictionary.prewarm()
}

/**
 * [name] adlı string kaynağını (önbellekli) okur; yoksa `null` döner.
 *
 * `internal` — aynı modüldeki arama/çeviri yardımcıları da kullanır. Çağıranlar Çince/Japonca
 * etiketler için türetilmiş anahtarın boş kaldığını denetler; boş anahtar hiç aranmaz.
 */
internal fun cachedStringResourceByName(name: String): String? {
    if (name.isBlank()) return null
    val context = com.kitsugi.animelist.KitsugiApplication.getInstance() ?: return null
    val languageTag = runCatching {
        context.resources.configuration.locales[0].toLanguageTag()
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: java.util.Locale.getDefault().language
    val cacheKey = "$languageTag:$name"
    stringResourceCache[cacheKey]?.let { return it.ifEmpty { null } }
    val resolved = try {
        val fastId = appStringIdMap[name]
        val resId = when {
            fastId != null -> fastId
            name.startsWith("bangumi_") -> context.resources.getIdentifier(name, "string", context.packageName)
            else -> 0
        }
        if (resId != 0) context.getString(resId) else null
    } catch (_: Throwable) {
        null
    }
    stringResourceCache[cacheKey] = resolved.orEmpty()
    return resolved
}

/** `bangumi_tag_*` dil dosyası kaynağını ismiyle okur; bulunamazsa null. */
private fun getStringResourceByName(name: String): String? = cachedStringResourceByName(name)

/** Bangumi etiketinin sözlük girişi (Türkçe + İngilizce karşılık); bilinmiyorsa null. */
internal fun bangumiTagEntryOrNull(label: String?): BangumiTagEntry? = BangumiTagDictionary.entryFor(label)

/**
 * Bangumi Çince/Japonca etiketini arayüz diline göre yerelleştirir.
 *
 * Sözlük satırı (`bangumi/tags.tsv` + `values/values-en` aynı kaynaktan üretilir) hem Türkçe
 * hem İngilizce karşılığı taşır. Bu yüzden **`getIdentifier` çağrısı yapılmaz**: uygulamada
 * 50.000'den fazla `bangumi_*` dize kaynağı olduğu için her `getIdentifier` tüm kaynak
 * tablosunu tarar ve detay sayfasındaki onlarca çip bunu tekrar tekrar tetikliyordu.
 * Eşleme yoksa null döner ve çağıran taraf orijinal metni gösterir.
 */
fun String.toLocalizedBangumiTagOrNull(): String? {
    val tag = BangumiTagDictionary.entryFor(this) ?: return null
    return if (isTurkish()) tag.turkish else tag.english
}

/** Bangumi etiketini arayüz diline çevirir (eşleme yoksa orijinal metin döner). */
fun String.toTurkishBangumiTag(): String = toLocalizedBangumiTagOrNull() ?: this

/** Bangumi etiketinin İngilizce karşılığı; eşleme yoksa null döner. */
fun bangumiTagEnglishOrNull(label: String): String? = BangumiTagDictionary.entryFor(label)?.english

/** Bangumi etiketini İngilizce arama terimine çevirir (eşleme yoksa orijinal metin döner). */
fun String.toEnglishBangumiTag(): String = bangumiTagEnglishOrNull(this) ?: this

/** Türkçe/İngilizce/Çince etiketi Bangumi'nin özgün Çince etiketine çevirir; yoksa null. */
fun bangumiNativeTagOrNull(label: String?): String? = BangumiTagDictionary.nativeTagFor(label)

/**
 * `taxonomy_tag_<key>` dil dosyası override'ı (tr/en arayüzünde).
 *
 * Sözlük tablosu koddadır; dil dosyaları çeviri ekibinin **kod değiştirmeden** düzeltme
 * yapabilmesi için üretilir (`scripts/sync_taxonomy_tag_strings.py`). Diğer arayüz
 * dillerinde varsayılan kaynak Türkçe olduğu için yanlış sonuç verirdi — okunmaz.
 */
internal fun taxonomyStringOverride(key: String): String? {
    if (!isTurkish() && !isEnglish()) return null
    return getStringResourceByName("taxonomy_tag_$key")?.takeIf { it.isNotBlank() }
}

/**
 * Etiketin **kanonik İngilizce** adı; bilinmiyorsa null.
 *
 * Türkçe girdi (`Aksiyon`) da İngilizceye çevrilir (`Action`), böylece kanonik anahtar
 * dilden bağımsız üretilebilir. Bilinmeyen değerde null döner — `translateToEnglishForSearch`
 * bilinmeyen değerde girdiyi aynen döndürdüğü için bu ayrım burada yapılır.
 */
internal fun englishTaxonomyName(label: String?): String? {
    val cleaned = label?.trim().orEmpty()
    if (cleaned.isEmpty()) return null
    genreMap[cleaned]?.let { return cleaned }
    genreMap.keys.firstOrNull { it.equals(cleaned, ignoreCase = true) }?.let { return it }
    com.kitsugi.animelist.ui.screens.search.SearchTranslation.englishToTurkish.keys
        .firstOrNull { it.equals(cleaned, ignoreCase = true) }
        ?.let { return it }
    val english = com.kitsugi.animelist.ui.screens.search.SearchTranslation
        .translateToEnglishForSearch(cleaned)
    if (!english.equals(cleaned, ignoreCase = true)) return english
    return null
}

/**
 * Etiketin arayüz dili Türkçe iken gösterilecek karşılığı; bilinmiyorsa null.
 *
 * `SearchTranslation.translateToTurkishForDisplay` dil dosyası kaynaklarını, AniList/MAL/
 * Kitsu ortak tablosunu ve Bangumi sözlüğünü birlikte kullanır. Bilinmeyen değeri
 * değiştirmeden döndürdüğü için "çeviri var mı" ayrımı burada yapılır.
 */
internal fun englishTaxonomyToTurkish(label: String?): String? {
    val cleaned = label?.trim().orEmpty()
    if (cleaned.isEmpty()) return null
    val display = com.kitsugi.animelist.ui.screens.search.SearchTranslation
        .translateToTurkishForDisplay(cleaned)
    if (!display.equals(cleaned, ignoreCase = true)) return display
    return null
}

/**
 * Tür/etiket çiplerinde gösterilen metni arayüz diline çevirir.
 *
 * Tek çözümleyici: [String.toLocalizedTaxonomyLabel] — Bangumi (Çince/Japonca) ve
 * Shikimori (Rusça) sözlükleri + İngilizce yerleşik tablolar. Çözülemeyen Kiril metni
 * Latin harflerine çevrilir (**ekranda Rusça görünmez**); uydurma çeviri üretilmez.
 */
fun String.toLocalizedTagLabel(): String = toLocalizedTaxonomyLabel()


/**
 * Etiket sözlükte karşılık bulamadı ve hâlâ çözülememiş Çince/Japonca mı?
 * Böyle etiketler gösterimden **çıkarılır** (modelde korunur) — uydurma çeviri üretilmez
 * ve ekranda Çince/Japonca metin görünmez.
 */
fun isUntranslatedCjkTag(value: String): Boolean = isUnresolvedForeignLabel(value)

/**
 * Etiket/tür listesini gösterime hazırlar.
 *
 *  • Çeviri tek çözümleyiciden geçer ([String.toLocalizedTaxonomyLabel]): Bangumi
 *    (Çince/Japonca) + Shikimori (Rusça) sözlükleri + İngilizce tablolar.
 *  • Çeviriden sonra aynı anlama gelen yazımlar tekilleştirilir
 *    (`催泪` + `催涙` + `Duygusal` + `Tearjerker` → tek çip).
 *  • Kanonik anahtar karşılaştırması diller arası çalışır
 *    (`Экшен` ≡ `Action` ≡ `动作` ≡ `Aksiyon`).
 *  • Çözülemeyen Çince/Japonca etiketler listeye **alınmaz**; çözülemeyen Kiril metni
 *    Latin harflerine çevrilerek eklenir.
 *
 * Modelde orijinal değer kalır; çipe tıklanınca arama o değerle yapılır.
 */
fun List<String>.localizedDistinctTags(limit: Int = 200): List<String> {
    if (isEmpty()) return emptyList()
    val result = ArrayList<String>(minOf(size, limit))
    val seen = HashSet<String>()
    for (raw in this) {
        val value = raw.trim()
        if (value.isEmpty()) continue
        // Sözlükte olmayan Çince/Japonca etiket gösterilmez.
        if (isUnresolvedForeignLabel(value)) continue
        val label = value.toLocalizedTagLabel().trim()
        if (label.isEmpty()) continue
        val dedupKey = value.taxonomyCanonicalKey()
            .ifEmpty { label.lowercase(java.util.Locale.ROOT) }
        if (!seen.add(dedupKey)) continue
        result.add(value)
        if (result.size >= limit) break
    }
    return result
}

// Tersine dönüşüm: Türkçe → İngilizce (arama API'ları için)
private val reverseGenreMap: Map<String, String> by lazy {
    genreMap.entries.associate { (en, tr) -> tr.lowercase() to en }
}

/**
 * Türkçe veya İngilizce tür adını İngilizce API adına dönüştürür.
 * "Aksiyon" → "Action", "Action" → "Action"
 */
fun String.toEnglishGenreForSearch(): String {
    val cleaned = this.trim()
    if (cleaned.isEmpty()) return cleaned
    // Bangumi Çince/Japonca tür etiketleri doğrudan İngilizce arama terimine çevrilir.
    bangumiTagEnglishOrNull(cleaned)?.let { return it }
    val mapped = reverseGenreMap[cleaned.lowercase()]
        ?: reverseGenreMap.entries.find { it.key.equals(cleaned, ignoreCase = true) }?.value
    if (mapped != null) return mapped
    return com.kitsugi.animelist.ui.screens.search.SearchTranslation.translateToEnglishForSearch(cleaned)
}

// ─── Yayın Durumu / Status ─────────────────────────────────────────────────────

private val statusMap = mapOf(
    // Jikan / MAL
    "Finished Airing" to "Yayın Tamamlandı",
    "Currently Airing" to "Yayında",
    "Not yet aired" to "Henüz Yayınlanmadı",
    "Finished" to "Tamamlandı",
    "Publishing" to "Yayımlanıyor",
    "Currently Publishing" to "Yayımlanıyor",
    "Not yet published" to "Henüz Yayımlanmadı",
    "Discontinued" to "Yayın Durduruldu",
    "On Hiatus" to "Ara Verildi",
    // AniList
    "FINISHED" to "Yayın Tamamlandı",
    "RELEASING" to "Yayında",
    "NOT_YET_RELEASED" to "Henüz Yayınlanmadı",
    "CANCELLED" to "İptal Edildi",
    "HIATUS" to "Ara Verildi",
    // TMDB
    "Ended" to "Yayın Tamamlandı",
    "Returning Series" to "Devam Eden Dizi",
    "In Production" to "Yapımda",
    "Planned" to "Planlandı",
    "Canceled" to "İptal Edildi",
    "Pilot" to "Pilot",
    "Released" to "Yayınlandı",
)

fun String.toTurkishStatus(): String = if (!isTurkish()) this else statusMap[this] ?: this

// ─── Mevsim / Season ──────────────────────────────────────────────────────────

private val seasonNameMap = mapOf(
    "Winter" to "Kış",
    "WINTER" to "Kış",
    "Spring" to "İlkbahar",
    "SPRING" to "İlkbahar",
    "Summer" to "Yaz",
    "SUMMER" to "Yaz",
    "Fall" to "Sonbahar",
    "FALL" to "Sonbahar",
)

/**
 * "Winter 2024" veya "SPRING" gibi sezon metinlerini Türkçeye çevirir.
 * Yıl varsa korunur: "Kış 2024"
 */
fun String.toTurkishSeason(): String {
    if (!isTurkish()) return this
    val parts = this.trim().split(" ")
    val seasonPart = parts.getOrNull(0) ?: return this
    val yearPart = parts.getOrNull(1)
    val translated = seasonNameMap[seasonPart] ?: seasonPart
    return if (yearPart != null) "$translated $yearPart" else translated
}

// ─── Kaynak Materyal / Source Material ────────────────────────────────────────

private val sourceMaterialMap = mapOf(
    // Jikan / MAL
    "4-koma manga" to "4-Koma Manga",
    "Book" to "Kitap",
    "Card game" to "Kart Oyunu",
    "Game" to "Oyun",
    "Light novel" to "Hafif Roman",
    "Manga" to "Manga",
    "Mixed media" to "Karma Medya",
    "Music" to "Müzik",
    "Novel" to "Roman",
    "Original" to "Orijinal",
    "Other" to "Diğer",
    "Picture book" to "Resimli Kitap",
    "Radio" to "Radyo",
    "Unknown" to "Bilinmiyor",
    "Visual novel" to "Görsel Roman",
    "Web manga" to "Web Manga",
    "Web novel" to "Web Roman",
    // AniList
    "ORIGINAL" to "Orijinal",
    "MANGA" to "Manga",
    "LIGHT_NOVEL" to "Hafif Roman",
    "VISUAL_NOVEL" to "Görsel Roman",
    "VIDEO_GAME" to "Video Oyunu",
    "OTHER" to "Diğer",
    "NOVEL" to "Roman",
    "DOUJINSHI" to "Doujinshi",
    "ANIME" to "Anime",
    "ONE_SHOT" to "Tek Bölüm",
    "WEB_NOVEL" to "Web Roman",
    "LIVE_ACTION" to "Canlı Yayın",
    "GAME" to "Oyun",
    "COMIC" to "Çizgi Roman",
    "MULTIMEDIA_PROJECT" to "Çoklu Medya Projesi",
    "PICTURE_BOOK" to "Resimli Kitap",
    "MUSIC" to "Müzik",
)

fun String.toTurkishSourceMaterial(): String = if (!isTurkish()) this else sourceMaterialMap[this] ?: this

// ─── Yaş Sınırı / Rating ──────────────────────────────────────────────────────

private val ratingMap = mapOf(
    "G - All Ages" to "G - Her Yaştan",
    "PG - Children" to "PG - Çocuklar",
    "PG-13 - Teens 13 or older" to "PG-13 - 13+ Gençler",
    "R - 17+ (violence & profanity)" to "R - 17+ (Şiddet & Küfür)",
    "R+ - Mild Nudity" to "R+ - Hafif Müstehcenlik",
    "Rx - Hentai" to "Rx - Hentai",
)

fun String.toTurkishRating(): String = if (!isTurkish()) this else ratingMap[this] ?: this

// ─── Karakter Rolü / Character Role ───────────────────────────────────────────

private val characterRoleMap = mapOf(
    // Jikan
    "Main" to "Ana Karakter",
    "Supporting" to "Yardımcı Karakter",
    "Background" to "Arka Plan",
    // AniList
    "MAIN" to "Ana Karakter",
    "SUPPORTING" to "Yardımcı Karakter",
    "BACKGROUND" to "Arka Plan",
    // Lowercase
    "main" to "Ana Karakter",
    "supporting" to "Yardımcı Karakter",
    "background" to "Arka Plan",
    // Bangumi sözlüğünün ürettiği İngilizce normalleştirilmiş roller (TR arayüz için)
    "Main Character" to "Ana Karakter",
    "Supporting Character" to "Yardımcı Karakter",
    "Guest Character" to "Konuk Karakter",
    "Lead Role" to "Başrol",
)

fun String.toTurkishCharacterRole(): String = if (!isTurkish()) this else characterRoleMap[this] ?: this

/**
 * Bangumi'nin Çince/Japonca karakter rolü etiketlerini (主角 / 配角 / 客串 ...) arayüz
 * diline çevirir. Sözlükte yoksa mevcut [toTurkishCharacterRole] davranışı korunur
 * (İngilizce kaynak değerler İngilizce arayüzde aynen kalır).
 */
private fun localizedBangumiRoleOrNull(label: String): String? {
    val entry = BangumiRoleDictionary.entryFor(label) ?: return null
    return if (isTurkish()) entry.turkish else entry.english
}

fun String.toLocalizedCharacterRole(): String {
    val cleaned = this.trim()
    if (cleaned.isEmpty()) return cleaned
    localizedBangumiRoleOrNull(cleaned)?.let { return it }
    return toTurkishCharacterRole()
}

/**
 * Bangumi'nin Çince/Japonca kadro görev etiketlerini (主題歌演出 / 原作 / 监督 ...) arayüz
 * diline çevirir. Sözlükte yoksa mevcut [toTurkishStaffRole] davranışı korunur; böylece
 * İngilizce arayüzde Çince/Japonca görev metni görünmez.
 */
fun String.toLocalizedStaffRole(): String {
    val cleaned = this.trim()
    if (cleaned.isEmpty()) return cleaned
    // " · " ile birleştirilmiş görev listeleri parça parça yerelleştirilir.
    if (cleaned.contains("·") || cleaned.contains("、") || cleaned.contains("，")) {
        return cleaned.split(Regex("[·、，]"))
            .map { it.trim().toLocalizedStaffRole() }
            .filter { it.isNotEmpty() }
            .joinToString(", ")
    }
    localizedBangumiRoleOrNull(cleaned)?.let { return it }
    return toTurkishStaffRole()
}

/**
 * Medya türü etiketini arayüz diline çevirir. Türkçe dışında İngilizce karşılık verilir;
 * böylece "tv" gibi ham anahtarlar İngilizce arayüzde küçük harfle görünmez.
 */
fun String.toLocalizedMediaTypeString(): String {
    if (isTurkish()) return toTurkishMediaTypeString()
    return when (this.trim().lowercase(java.util.Locale.ROOT)) {
        "tv" -> "TV Series"
        "anime" -> "Anime"
        "manga" -> "Manga"
        "movie" -> "Movie"
        else -> this
    }
}

// ─── Dil Adı / Voice Actor Language ──────────────────────────────────────────

private val languageMap = mapOf(
    "Japanese" to "Japonca",
    "JAPANESE" to "Japonca",
    "English" to "İngilizce",
    "ENGLISH" to "İngilizce",
    "Korean" to "Korece",
    "KOREAN" to "Korece",
    "Chinese" to "Çince",
    "CHINESE" to "Çince",
    "Italian" to "İtalyanca",
    "ITALIAN" to "İtalyanca",
    "Spanish" to "İspanyolca",
    "SPANISH" to "İspanyolca",
    "French" to "Fransızca",
    "FRENCH" to "Fransızca",
    "German" to "Almanca",
    "GERMAN" to "Almanca",
    "Portuguese" to "Portekizce",
    "PORTUGUESE" to "Portekizce",
    "Hungarian" to "Macarca",
    "HUNGARIAN" to "Macarca",
    "Hebrew" to "İbranice",
    "HEBREW" to "İbranice",
    "Arabic" to "Arapça",
    "ARABIC" to "Arapça",
    "Turkish" to "Türkçe",
    "TURKISH" to "Türkçe",
    "Russian" to "Rusça",
    "RUSSIAN" to "Rusça",
    "Dutch" to "Hollandaca",
    "DUTCH" to "Hollandaca",
    "Swedish" to "İsveçce",
    "SWEDISH" to "İsveçce",
    "Polish" to "Lehçe",
    "POLISH" to "Lehçe",
    "Catalan" to "Katalanca",
    "CATALAN" to "Katalanca",
    "Finnish" to "Fince",
    "FINNISH" to "Fince",
    "Czech" to "Çekçe",
    "CZECH" to "Çekçe",
    "Thai" to "Tayca",
    "THAI" to "Tayca",
    "Romanian" to "Rumence",
    "ROMANIAN" to "Rumence",
    "Norwegian" to "Norveçce",
    "NORWEGIAN" to "Norveçce",
    "Danish" to "Danca",
    "DANISH" to "Danca",
    "Indonesian" to "Endonezyaca",
    "INDONESIAN" to "Endonezyaca",
    "Malay" to "Malayca",
    "MALAY" to "Malayca",
    "Vietnamese" to "Vietnamca",
    "VIETNAMESE" to "Vietnamca",
    "Filipino" to "Filipince",
    "FILIPINO" to "Filipince",
    "Greek" to "Yunanca",
    "GREEK" to "Yunanca",
    "Bulgarian" to "Bulgarca",
    "BULGARIAN" to "Bulgarca",
    "Ukrainian" to "Ukraynaca",
    "UKRAINIAN" to "Ukraynaca",
    "Croatian" to "Hırvatça",
    "CROATIAN" to "Hırvatça",
    "Spain" to "İspanya",
    "SPAIN" to "İspanya",
    "Brazil" to "Brezilya",
    "BRAZIL" to "Brezilya",
    "Latin America" to "Latin Amerika",
    "LATIN AMERICA" to "Latin Amerika",
)

fun String.toTurkishLanguage(): String {
    if (!isTurkish()) return this
    val mapped = languageMap[this]
    if (mapped != null) return mapped

    if (this.contains("(")) {
        val baseLang = this.substringBefore("(").trim()
        val suffix = this.substringAfter("(").substringBefore(")").trim()
        val baseMapped = languageMap[baseLang]
        if (baseMapped != null) {
            val suffixMapped = languageMap[suffix] ?: suffix
            return "$baseMapped ($suffixMapped)"
        }
    }
    return this
}

/**
 * Reorders names in "LastName, FirstName" format to "FirstName LastName".
 */
fun String.toFriendlyName(): String {
    if (this.contains(",")) {
        val parts = this.split(",").map { it.trim() }
        if (parts.size == 2) {
            return "${parts[1]} ${parts[0]}"
        }
    }
    return this
}

/**
 * Sorts voice actors: Japanese first, English second, Turkish third, then others alphabetically by language.
 */
fun List<KitsugiVoiceActor>.sortedByLanguagePreference(): List<KitsugiVoiceActor> {
    return this.sortedWith(
        compareByDescending<KitsugiVoiceActor> { it.language.equals("Japonca", ignoreCase = true) }
            .thenByDescending { it.language.equals("İngilizce", ignoreCase = true) }
            .thenByDescending { it.language.equals("Türkçe", ignoreCase = true) }
            .thenBy { it.language }
    )
}

// ─── İlişki Tipi / Relation Type ──────────────────────────────────────────────

private val relationTypeMap = mapOf(
    // Jikan / MAL
    "Sequel" to "Devam",
    "Prequel" to "Öncül",
    "Alternative setting" to "Alternatif Ortam",
    "Alternative version" to "Alternatif Versiyon",
    "Side story" to "Yan Hikaye",
    "Summary" to "Özet",
    "Full story" to "Tam Hikaye",
    "Spin-off" to "Spin-Off",
    "Adaptation" to "Uyarlama",
    "Character" to "Karakter",
    "Other" to "Diğer",
    "Relation" to "İlişkili",
    "Parent story" to "Ana Hikaye",
    // AniList
    "SEQUEL" to "Devam",
    "PREQUEL" to "Öncül",
    "ALTERNATIVE" to "Alternatif",
    "SIDE_STORY" to "Yan Hikaye",
    "CHARACTER" to "Karakter",
    "SUMMARY" to "Özet",
    "PARENT" to "Ana",
    "ADAPTATION" to "Uyarlama",
    "COMPILATION" to "Derleme",
    "CONTAINS" to "İçeriyor",
    "SPIN_OFF" to "Spin-Off",
    "SOURCE" to "Kaynak",
    "OTHER" to "Diğer",
)

fun String.toTurkishRelationType(): String {
    if (!isTurkish()) return this
    // Shikimori "Side story" / "Side Story" gibi farklı büyük-küçük harf kullanabiliyor.
    return relationTypeMap[this]
        ?: relationTypeMap.entries.firstOrNull { it.key.equals(this, ignoreCase = true) }?.value
        ?: this
}

// ─── Medya Tipi / Media Type (API string'den) ─────────────────────────────────

private val mediaTypeStringMap = mapOf(
    "TV" to "TV Dizisi",
    "Movie" to "Film",
    "MOVIE" to "Film",
    "OVA" to "OVA",
    "ONA" to "ONA",
    "Special" to "Özel",
    "SPECIAL" to "Özel",
    "TV Special" to "TV Özel Bölümü",
    "TV_SPECIAL" to "TV Özel Bölümü",
    "Music" to "Müzik",
    "MUSIC" to "Müzik",
    "Manga" to "Manga",
    "MANGA" to "Manga",
    "One-shot" to "Tek Bölüm",
    "ONE_SHOT" to "Tek Bölüm",
    "Manhua" to "Manhua",
    "Manhwa" to "Manhwa",
    "Doujinshi" to "Doujinshi",
    "DOUJINSHI" to "Doujinshi",
    "Novel" to "Roman",
    "NOVEL" to "Roman",
    "Light Novel" to "Hafif Roman",
    "LIGHT_NOVEL" to "Hafif Roman",
    "Unknown" to "Bilinmiyor",
    "ANIME" to "Anime",
    "TV_SHORT" to "Kısa TV Dizisi",
    "GRAVEL" to "Gravel",
    // Lowercase & extra
    "movie" to "Film",
    "tv" to "Dizi",
    "anime" to "Anime",
    "manga" to "Manga",
)

fun String.toTurkishMediaTypeString(): String = if (!isTurkish()) this else mediaTypeStringMap[this] ?: this

// ─── Yayın Günü / Broadcast Day ───────────────────────────────────────────────

private val broadcastDayMap = mapOf(
    "Mondays" to "Pazartesi",
    "Tuesdays" to "Salı",
    "Wednesdays" to "Çarşamba",
    "Thursdays" to "Perşembe",
    "Fridays" to "Cuma",
    "Saturdays" to "Cumartesi",
    "Sundays" to "Pazar",
    "Monday" to "Pazartesi",
    "Tuesday" to "Salı",
    "Wednesday" to "Çarşamba",
    "Thursday" to "Perşembe",
    "Friday" to "Cuma",
    "Saturday" to "Cumartesi",
    "Sunday" to "Pazar",
)

/**
 * Yayın bilgisini Türkçeleştirir.
 * Örnek: "Saturdays at 17:00" → "Cumartesi 17:00"
 */
fun String.toTurkishBroadcast(): String {
    if (!isTurkish()) return this
    var result = this
    broadcastDayMap.forEach { (en, tr) ->
        result = result.replace(en, tr)
    }
    // "at" kelimesini kaldır
    result = result.replace(" at ", " ").trim()
    return result
}

// ─── Cinsiyet / Gender ────────────────────────────────────────────────────────

private val genderMap = mapOf(
    "Male" to "Erkek",
    "Female" to "Kadın",
    "Non-binary" to "Non-binary",
    "Unknown" to "Bilinmiyor",
)

fun String.toTurkishGender(): String = if (!isTurkish()) this else genderMap[this] ?: this

// ─── Kan Grubu / Blood Type ───────────────────────────────────────────────────
// (Zaten kısaltmalar: A, B, AB, O — Türkçe için değişmez)

// ─── Bölüm Süresi / Episode Duration ─────────────────────────────────────────

/**
 * "24 min" → "24 dk" gibi kısaltmaları Türkçeleştirir.
 */
fun String.toTurkishDuration(): String {
    if (!isTurkish()) return this
    return this.replace(" min per ep", " dk/bölüm")
        .replace(" min", " dk")
        .replace("hr", "sa")
        .replace("Unknown", "Bilinmiyor")
}

// ─── Staff Rolü / Staff Role (kısmi Türkçeleştirme) ─────────────────────────

private val staffRoleMap = mapOf(
    "Director" to "Yönetmen",
    "Script" to "Senarist",
    "Screenplay" to "Senarist",
    "Character Design" to "Karakter Tasarımı",
    "Animation Director" to "Animasyon Yönetmeni",
    "Chief Animation Director" to "Baş Animasyon Yönetmeni",
    "Art Director" to "Sanat Yönetmeni",
    "Sound Director" to "Ses Yönetmeni",
    "Music" to "Müzik",
    "Producer" to "Yapımcı",
    "Series Composition" to "Seri Düzenlemesi",
    "Color Design" to "Renk Tasarımı",
    "Photography Director" to "Görüntü Yönetmeni",
    "3D Director" to "3D Yönetmeni",
    "Prop Design" to "Sahne Eşyası Tasarımı",
    "Background Art" to "Arka Plan Sanatı",
    "Key Animation" to "Anahtar Animasyon",
    "In-Between Animation" to "Ara Animasyon",
    "Editing" to "Kurgu",
    "Opening Theme Song Arrangement" to "Açılış Teması Düzeni",
    "Ending Theme Song Arrangement" to "Kapanış Teması Düzeni",
    "Original Character Design" to "Orijinal Karakter Tasarımı",
    "Mechanical Design" to "Mekanik Tasarım",
    "Storyboard" to "Storyboard",
    "Episode Director" to "Bölüm Yönetmeni",
    "Unit Director" to "Birim Yönetmeni",
    "Special Effects" to "Özel Efektler",
    "Voice Actor" to "Seslendirme Sanatçısı",
    "Voice Acting" to "Seslendirme Sanatçısı",
    "Сэю" to "Seslendirmen (Seiyuu)",
    "Сэйю" to "Seslendirmen (Seiyuu)",
    "сэю" to "Seslendirmen (Seiyuu)",
    "сэйю" to "Seslendirmen (Seiyuu)",
    "Seyu" to "Seslendirmen (Seiyuu)",
    "seyu" to "Seslendirmen (Seiyuu)",
    "Seiyuu" to "Seslendirmen (Seiyuu)",
    "seiyuu" to "Seslendirmen (Seiyuu)",
    "Theme Song Performance" to "Tema Şarkısı Performansı",
    "Adaptation" to "Uyarlama",
    "Original Creator" to "Orijinal Yaratıcı",
    "Supervisor" to "Süpervizör",
    "Composition" to "Kompozisyon",
    "Art Design" to "Sanat Tasarımı",
    "CG Director" to "CG Yönetmeni",
    "Conceptual Design" to "Kavramsal Tasarım",
    "Planning" to "Planlama",
    "Action Animation Director" to "Aksiyon Animasyon Yönetmeni",
    "Original Music Composer" to "Orijinal Müzik Bestecisi",
    "Costume Design" to "Kostüm Tasarımı",
    "Director of Photography" to "Görüntü Yönetmeni",
    "Stunt Coordinator" to "Dublör Koordinatörü",
    "First Assistant Director" to "Birinci Yönetmen Yardımcısı",
    "Casting" to "Oyuncu Seçimi",
    "Conceptual Illustrator" to "Kavramsal İllüstratör",
    "Construction Coordinator" to "İnşaat Koordinatörü",
    "Transportation Coordinator" to "Ulaşım Koordinatörü",
    "Production Design" to "Yapım Tasarımı",
    "ADR Director" to "ADR Yönetmeni",
    "ADR Script" to "ADR Senaristi",
    "Executive Producer" to "Yönetici Yapımcı",
    "Associate Producer" to "Ortak Yapımcı",
    "Co-Producer" to "Eş Yapımcı",
    "Line Producer" to "Hat Yapımcısı",
    "Assistant Director" to "Yönetmen Yardımcısı",
    "Second Assistant Director" to "İkinci Yönetmen Yardımcısı",
    "Third Assistant Director" to "Üçüncü Yönetmen Yardımcısı",
    "Writer" to "Yazar",
    "Novel" to "Roman",
    "Original Story" to "Orijinal Hikaye",
    "Creator" to "Yaratıcı",
    "Set Decoration" to "Set Dekorasyonu",
    "Set Designer" to "Set Tasarımcısı",
    "Supervising Art Director" to "Baş Sanat Yönetmeni",
    "Production Director" to "Yapım Yönetmeni",
    "Sound Editor" to "Ses Editörü",
    "Sound Designer" to "Ses Tasarımcısı",
    "Sound Effects Editor" to "Ses Efektleri Editörü",
    "Supervising Sound Editor" to "Baş Ses Editörü",
    "Sound Re-Recording Mixer" to "Ses Miksajı",
    "Sound Mixer" to "Ses Mikseri",
    "Production Sound Mixer" to "Yapım Ses Mikseri",
    "Foley" to "Efekt Sanatçısı (Foley)",
    "Foley Artist" to "Foley Sanatçısı",
    "Foley Editor" to "Foley Editörü",
    "Foley Mixer" to "Foley Mikseri",
    "Dialogue Editor" to "Diyalog Editörü",
    "Visual Effects" to "Görsel Efektler",
    "Visual Effects Supervisor" to "Görsel Efektler Süpervizörü",
    "Visual Effects Producer" to "Görsel Efektler Yapımcısı",
    "Visual Effects Coordinator" to "Görsel Efektler Koordinatörü",
    "Special Effects Coordinator" to "Özel Efektler Koordinatörü",
    "Stunt Double" to "Dublör",
    "Camera Operator" to "Kamera Operatörü",
    "Steadicam Operator" to "Steadicam Operatörü",
    "Still Photographer" to "Set Fotoğrafçısı",
    "Gaffer" to "Işık Şefi",
    "Best Boy Grip" to "Set Amiri Yardımcısı",
    "Best Boy Electric" to "Işık Şefi Yardımcısı",
    "Key Grip" to "Set Amiri",
    "Grip" to "Set İşçisi",
    "Rigging Grip" to "Rigging Set İşçisi",
    "Rigging Gaffer" to "Rigging Işık Şefi",
    "Lighting Technician" to "Işık Teknisyeni",
    "Costume Supervisor" to "Kostüm Süpervizörü",
    "Key Costumer" to "Baş Kostümcü",
    "Costumer" to "Kostümcü",
    "Makeup Artist" to "Makyaj Sanatçısı",
    "Key Makeup Artist" to "Baş Makyaj Sanatçısı",
    "Hair Designer" to "Saç Tasarımcısı",
    "Key Hair Stylist" to "Baş Saç Stilisti",
    "Makeup Department Head" to "Makyaj Bölümü Başkanı",
    "Hair Department Head" to "Saç Bölümü Başkanı",
    "Makeup Designer" to "Makyaj Tasarımcısı",
    "Prosthetic Makeup Artist" to "Protez Makyaj Sanatçısı",
    "Script Supervisor" to "Senaryo Süpervizörü",
    "Publicist" to "Basın Danışmanı",
    "Location Manager" to "Mekan Sorumlusu",
    "Location Assistant" to "Mekan Sorumlusu Yardımcısı",
    "Production Coordinator" to "Yapım Koordinatörü",
    "Production Manager" to "Yapım Müdürü",
    "Production Supervisor" to "Yapım Süpervizörü",
    "Post Production Supervisor" to "Post-Prodüksiyon Süpervizörü",
    "Colorist" to "Renk Uzmanı (Colorist)",
    "Editorial Production Assistant" to "Kurgu Yapım Yardımcısı",
    "First Assistant Editor" to "Birinci Kurgu Yardımcısı",
    "Assistant Editor" to "Kurgu Yardımcısı",
    "Co-Editor" to "Kurgu Ortağı",
    "Music Editor" to "Müzik Editörü",
    "Music Supervisor" to "Müzik Süpervizörü",
    "Conductor" to "Orkestra Şefi",
    "Choreographer" to "Koreograf",
    "Boom Operator" to "Boom Operatörü",
    "Compositing Supervisor" to "Kompozisyon Süpervizörü",
    "Compositor" to "Kompozitör",
    "Lead Animator" to "Baş Animatör",
    "Animator" to "Animatör",
    "Sequence Lead" to "Sekans Şefi",
    "CG Artist" to "CG Sanatçısı",
    "3D Animator" to "3D Animatörü",
    "Character Modeling" to "Karakter Modelleme",
    "Modeler" to "Modelci",
    "Texture Artist" to "Doku Sanatçısı",
    "Concept Artist" to "Konsept Sanatçısı",
    "Storyboard Artist" to "Storyboard Sanatçısı",
    "Layout Artist" to "Layout Tasarımcısı",
    "Background Designer" to "Arka Plan Tasarımcısı",
    "Key Background Artist" to "Baş Arka Plan Sanatçısı",
    "Background Artist" to "Arka Plan Sanatçısı",
    "Inbetween Artist" to "Ara Çizim Sanatçısı",
    "Assistant Animation Director" to "Animasyon Yönetmeni Yardımcısı",
    "Co-Animation Director" to "Animasyon Yönetmeni Ortağı",
    "Co-Director" to "Yardımcı Yönetmen",
    "Theme Song Lyrics" to "Tema Şarkısı Sözleri",
    "Theme Song Arrangement" to "Tema Şarkısı Düzenlemesi",
    "Theme Song Composition" to "Tema Şarkısı Bestesi",
    "Theme Song Music" to "Tema Şarkısı Müziği",
    // Bangumi p1 pozisyon etiketleri (en) — liste uçlarındaki ham karşılıklar
    // (MAL/Jikan karşılıklarıyla aynı terimler kullanılır)
    "Episode Direction" to "Bölüm Yönetmeni",
    "Animation Direction" to "Animasyon Yönetmeni",
    "Script/Screenplay" to "Senarist",
    "Series Direction" to "Seri Yönetmeni",
    "Unit Direction" to "Birim Yönetmeni",
    "Action Direction" to "Aksiyon Yönetmeni",
    // Bangumi v0 `relation` / p1 pozisyon etiketleri (jp / cn)
    "監督" to "Yönetmen",
    "演出" to "Bölüm Yönetmeni",
    "脚本" to "Senarist",
    "シリーズ構成" to "Seri Düzenlemesi",
    "絵コンテ" to "Storyboard",
    "分镜" to "Storyboard",
    "作画監督" to "Animasyon Yönetmeni",
    "作画监督" to "Animasyon Yönetmeni",
    "総作画監督" to "Baş Animasyon Yönetmeni",
    "キャラクターデザイン" to "Karakter Tasarımı",
    "キャラデザイン" to "Karakter Tasarımı",
    "人物设计" to "Karakter Tasarımı",
    "色彩設計" to "Renk Tasarımı",
    "色彩设计" to "Renk Tasarımı",
    "撮影監督" to "Görüntü Yönetmeni",
    "摄影监督" to "Görüntü Yönetmeni",
    "原画" to "Anahtar Animasyon",
    "動画" to "Ara Animasyon",
    "音楽" to "Müzik",
    "音楽制作" to "Müzik Yapımı",
    "音乐" to "Müzik",
    "音响监督" to "Ses Yönetmeni",
    "音響監督" to "Ses Yönetmeni",
    "美術監督" to "Sanat Yönetmeni",
    "美术监督" to "Sanat Yönetmeni",
    "アニメーション制作" to "Animasyon Yapımı",
    "动画制作" to "Animasyon Yapımı",
    "動畫製作" to "Animasyon Yapımı",
    "製作" to "Yapım",
    "制作" to "Yapım",
    "製作委員会" to "Yapım Komitesi",
    "制作委员会" to "Yapım Komitesi",
    "企画" to "Planlama",
    "企划" to "Planlama",
    "原作" to "Orijinal Yaratıcı",
    "原案" to "Orijinal Konsept",
    "監修" to "Süpervizör",
    "プロデューサー" to "Yapımcı",
    "主題歌" to "Tema Şarkısı",
    "主題歌歌唱" to "Tema Şarkısı Performansı",
    "CGI" to "CGI",
    // Bangumi kariyer/görev alanlarının İngilizce normalleştirilmiş karşılıkları (TR arayüz)
    "Writer" to "Yazar",
    "Artist" to "Sanatçı",
    "Illustrator" to "İllüstratör",
    "Actor" to "Oyuncu",
    "Composer" to "Besteci",
    "Mangaka" to "Mangaka",
    "Chief Director" to "Baş Yönetmen",
    "Theme Song Composition" to "Tema Şarkısı Bestesi",
    "Theme Song Lyrics" to "Tema Şarkısı Söz Yazarı",
    "Recording" to "Kayıt",
    "Publisher" to "Yayıncı",
    "Production" to "Yapım",
)

private val commonPhrasesMap = mapOf(
    "Original Character Design" to "Orijinal Karakter Tasarımı",
    "Original Music Composer" to "Orijinal Müzik Bestecisi",
    "First Assistant Director" to "Birinci Yönetmen Yardımcısı",
    "Second Assistant Director" to "İkinci Yönetmen Yardımcısı",
    "Third Assistant Director" to "Üçüncü Yönetmen Yardımcısı",
    "Supervising Art Director" to "Baş Sanat Yönetmeni",
    "Assistant Animation Director" to "Animasyon Yönetmeni Yardımcısı",
    "Co-Animation Director" to "Animasyon Yönetmeni Ortağı",
    "Action Animation Director" to "Aksiyon Animasyon Yönetmeni",
    "Chief Animation Director" to "Baş Animasyon Yönetmeni",
    "Animation Director" to "Animasyon Yönetmeni",
    "Assistant Director" to "Yönetmen Yardımcısı",
    "Technical Director" to "Teknik Yönetmen",
    "Casting Director" to "Oyuncu Seçimi Yönetmeni",
    "Episode Director" to "Bölüm Yönetmeni",
    "Series Director" to "Seri Yönetmeni",
    "Action Director" to "Aksiyon Yönetmeni",
    "Chief Director" to "Baş Yönetmen",
    "Co-Director" to "Yardımcı Yönetmen",
    "Art Director" to "Sanat Yönetmeni",
    "Sound Director" to "Ses Yönetmeni",
    "Music Director" to "Müzik Yönetmeni",
    "Unit Director" to "Birim Yönetmeni",
    "Executive Producer" to "Yönetici Yapımcı",
    "Associate Producer" to "Ortak Yapımcı",
    "Line Producer" to "Hat Yapımcısı",
    "Co-Producer" to "Eş Yapımcı",
    "Music Producer" to "Müzik Yapımcısı",
    "Sound Producer" to "Ses Yapımcısı",
    "Animation Producer" to "Animasyon Yapımcısı",
    "Character Designer" to "Karakter Tasarımcısı",
    "Mechanical Designer" to "Mekanik Tasarımcı",
    "Prop Designer" to "Sahne Eşyası Tasarımcısı",
    "Set Designer" to "Set Tasarımcısı",
    "Costume Designer" to "Kostüm Tasarımcısı",
    "Makeup Designer" to "Makyaj Tasarımcısı",
    "Sound Designer" to "Ses Tasarımcısı",
    "Visual Designer" to "Görsel Tasarımcı",
    "Graphic Designer" to "Grafik Tasarımcı",
    "Background Designer" to "Arka Plan Tasarımcısı",
    "Original Creator" to "Orijinal Yaratıcı",
    "Original Story" to "Orijinal Hikaye",
    "Original Work" to "Orijinal Eser",
    "Theme Song Performance" to "Tema Şarkısı Performansı",
    "Theme Song Lyrics" to "Tema Şarkısı Sözleri",
    "Theme Song Arrangement" to "Tema Şarkısı Düzenlemesi",
    "Theme Song Composition" to "Tema Şarkısı Bestesi",
    "Theme Song Music" to "Tema Şarkısı Müziği",
    "Theme Song" to "Tema Şarkısı",
    "Opening Theme" to "Açılış Teması",
    "Ending Theme" to "Kapanış Teması",
    "Insert Song" to "Bölüm İçi Şarkı",
    "Song Lyrics" to "Şarkı Sözleri",
    "Song Performance" to "Şarkı Performansı",
    "Song Arrangement" to "Şarkı Düzenlemesi",
    "Song Composition" to "Şarkı Bestesi",
    "Sound Effects" to "Ses Efektleri",
    "Special Effects" to "Özel Efektler",
    "Visual Effects" to "Görsel Efektler",
    "VFX Supervisor" to "VFX Süpervizörü",
    "VFX Producer" to "VFX Yapımcısı",
    "VFX Coordinator" to "VFX Koordinatörü",
    "SFX Coordinator" to "SFX Koordinatörü",
    "Stunt Coordinator" to "Dublör Koordinatörü",
    "Stunt Double" to "Dublör",
    "Key Animator" to "Ana Animatör",
    "Lead Animator" to "Baş Animatör",
    "Assistant Animator" to "Yardımcı Animatör",
    "In-between Animator" to "Ara Çizim Animatörü",
    "Production Assistant" to "Yapım Asistanı",
    "Production Manager" to "Yapım Müdürü",
    "Production Supervisor" to "Yapım Süpervizörü",
    "Production Coordinator" to "Yapım Koordinatörü",
    "Location Manager" to "Mekan Sorumlusu",
    "Location Assistant" to "Mekan Sorumlusu Yardımcısı",
    "Public Relations" to "Halkla İlişkiler",
    "Script Supervisor" to "Senaryo Süpervizörü",
    "Dialogue Editor" to "Diyalog Editörü",
    "Sound Editor" to "Ses Editörü",
    "Foley Artist" to "Foley Sanatçısı",
    "Camera Operator" to "Kamera Operatörü",
    "Steadicam Operator" to "Steadicam Operatörü",
    "Still Photographer" to "Set Fotoğrafçısı",
    "Director of Photography" to "Görüntü Yönetmeni",
    "Photography Director" to "Görüntü Yönetmeni",
    "Costume Design" to "Kostüm Tasarımı",
    "Production Design" to "Yapım Tasarımı",
    "Character Design" to "Karakter Tasarımı",
    "Color Design" to "Renk Tasarımı",
    "Prop Design" to "Sahne Eşyası Tasarımı",
    "Mechanical Design" to "Mekanik Tasarım",
    "Conceptual Design" to "Kavramsal Tasarım",
    "Art Design" to "Sanat Tasarımı",
    "Background Art" to "Arka Plan Sanatı",
    "Series Composition" to "Seri Düzenlemesi",
    "Voice Actor" to "Seslendirme Sanatçısı",
    "Special Thanks" to "Özel Teşekkür",
    "Additional Voices" to "Ek Seslendirmeler",
    "Additional Voice" to "Ek Seslendirme",
    "Guest Star" to "Konuk Oyuncu",
    "Guest Role" to "Konuk Rolü",
    "Voice Acting" to "Seslendirme",
    "Voice Director" to "Seslendirme Yönetmeni",
    "Voice Directing" to "Seslendirme Yönetmenliği",
    "Sound Directing" to "Ses Yönetmenliği",
    "Recording Engineer" to "Kayıt Mühendisi",
    "Mixing Engineer" to "Miksaj Mühendisi",
    "Manga Creator" to "Manga Yaratıcısı",
    "Supervising Producer" to "Denetçi Yapımcı",
    "Co-Executive Producer" to "Eş Yönetici Yapımcı",
    "Creative Director" to "Kreatif Direktör",
    "Stage Manager" to "Sahne Amiri",
    "Project Manager" to "Proje Yöneticisi",
    "Social Media" to "Sosyal Medya",
)

private val commonWordsMap = mapOf(
    "Director" to "Yönetmen",
    "Producer" to "Yapımcı",
    "Writer" to "Yazar",
    "Creator" to "Yaratıcı",
    "Supervisor" to "Süpervizör",
    "Coordinator" to "Koordinatör",
    "Manager" to "Müdür",
    "Assistant" to "Yardımcı",
    "Associate" to "Ortak",
    "Executive" to "Yönetici",
    "Chief" to "Baş",
    "Editor" to "Editör",
    "Editing" to "Kurgu",
    "Artist" to "Sanatçı",
    "Animator" to "Animatör",
    "Designer" to "Tasarımcı",
    "Design" to "Tasarım",
    "Illustrator" to "İllüstratör",
    "Choreographer" to "Koreograf",
    "Conductor" to "Orkestra Şefi",
    "Casting" to "Oyuncu Seçimi",
    "Composer" to "Besteci",
    "Composition" to "Kompozisyon",
    "Singer" to "Şarkıcı",
    "Vocalist" to "Vokalist",
    "Actor" to "Oyuncu",
    "Actress" to "Aktris",
    "Host" to "Sunucu",
    "Staff" to "Ekip Üyesi",
    "Crew" to "Ekip",
    "Art" to "Sanat",
    "Music" to "Müzik",
    "Sound" to "Ses",
    "Audio" to "Ses",
    "Voice" to "Ses",
    "Camera" to "Kamera",
    "Photography" to "Fotoğraf",
    "Costume" to "Kostüm",
    "Makeup" to "Makyaj",
    "Hair" to "Saç",
    "Effects" to "Efektler",
    "Effect" to "Efekt",
    "Special" to "Özel",
    "Visual" to "Görsel",
    "Production" to "Yapım",
    "Development" to "Geliştirme",
    "Technical" to "Teknik",
    "Animation" to "Animasyon",
    "Original" to "Orijinal",
    "Character" to "Karakter",
    "Mechanical" to "Mekanik",
    "Concept" to "Konsept",
    "Planning" to "Planlama",
    "Theme" to "Tema",
    "Song" to "Şarkı",
    "Lyrics" to "Sözler",
    "Performance" to "Performans",
    "Arrangement" to "Düzenleme",
    "Background" to "Arka Plan",
    "Location" to "Mekan",
    "Set" to "Set",
    "Foley" to "Foley",
    "Stunt" to "Dublör",
    "Double" to "Dublör",
    "Modeler" to "Modelleyici",
    "Modeling" to "Modelleme",
    "Texture" to "Doku",
    "3D" to "3D",
    "2D" to "2D",
    "CG" to "CG",
    "VFX" to "VFX",
    "Narrator" to "Anlatıcı",
    "Self" to "Kendisi",
    "Guest" to "Konuk",
    "Translator" to "Çevirmen",
    "Translation" to "Çeviri",
    "Illustration" to "İllüstrasyon",
    "Colorist" to "Renk Uzmanı",
    "Author" to "Yazar",
    "Novel" to "Roman",
    "Supervising" to "Baş/Denetçi",
    "Coordinating" to "Koordinasyon",
    "Management" to "Yönetim",
    "Consultant" to "Danışman",
    "Adviser" to "Danışman",
    "Advisor" to "Danışman",
    "Developer" to "Geliştirici",
    "Co-Producer" to "Eş Yapımcı",
    "Co-editor" to "Kurgu Ortağı",
    "Co-director" to "Yardımcı Yönetmen",
    "Co-writer" to "Yazar Ortağı",
    "Cast" to "Oyuncular/Kadro",
    "Support" to "Destek",
    "Helper" to "Yardımcı",
    "Additional" to "Ek",
    "Main" to "Ana",
    "Key" to "Ana/Baş",
    "Lead" to "Baş",
    "Head" to "Şef/Baş",
    "Vocal" to "Vokal",
    // Bangumi p1 pozisyon etiketlerinde geçen tekil sözcükler
    "Direction" to "Yönetmeni",
    "Script" to "Senarist",
    "Screenplay" to "Senarist",
    "Episode" to "Bölüm",
)

private fun translateSuffix(suffix: String): String {
    var result = suffix
    // Translate languages in the suffix
    languageMap.forEach { (en, tr) ->
        if (result.equals(en, ignoreCase = true)) {
            return tr
        }
        val pattern = "\\b${en}\\b".toRegex(RegexOption.IGNORE_CASE)
        result = result.replace(pattern, tr)
    }
    // Translate common abbreviation patterns
    result = result.replace("eps", "böl.", ignoreCase = true)
    result = result.replace("ep ", "böl. ", ignoreCase = true)
    result = result.replace("episode", "bölüm", ignoreCase = true)
    result = result.replace("episodes", "bölümler", ignoreCase = true)
    result = result.replace("Song Lyrics", "Şarkı Sözleri", ignoreCase = true)
    result = result.replace("lyrics", "şarkı sözleri", ignoreCase = true)
    return result
}

fun String.toTurkishStaffRole(): String {
    if (!isTurkish()) return this
    val trimmed = this.trim()
    if (trimmed.isEmpty()) return trimmed

    // Check for parenthesis details, e.g. "ADR Director (English)" or "Storyboard (OP, eps 1-3)"
    if (trimmed.contains("(")) {
        val base = trimmed.substringBefore("(").trim()
        val suffix = trimmed.substringAfter("(").substringBefore(")").trim()
        val baseTranslated = base.toTurkishStaffRole()
        val suffixTranslated = translateSuffix(suffix)
        return if (suffixTranslated.isNotEmpty()) {
            "$baseTranslated ($suffixTranslated)"
        } else {
            baseTranslated
        }
    }

    // Virgülle ayrılmış roller, örn. "Director, Writer". Bangumi v0 liste uçları
    // görevleri " · " (ve Japonca 「、」) ile birleştirir — hepsi tek tek çevrilir.
    if (trimmed.contains(",") || trimmed.contains("·") || trimmed.contains("、") || trimmed.contains("，")) {
        return trimmed.split(Regex("[,·、，;；]"))
            .map { it.trim().toTurkishStaffRole() }
            .filter { it.isNotEmpty() }
            .joinToString(", ")
    }

    // Görev adı dil dosyası (`staff_role_*`, 25 kayıt). Önbellekli okunur: Bangumi ekip
    // listeleri 100 satıra kadar çıkıyor ve her satır eskiden tam kaynak taraması yapıyordu.
    // `lowercase()` yerine ROOT: Türkçe yerelde "I" → "ı" olup kaynak anahtarını bozuyordu.
    val normalizedRole = trimmed.lowercase(java.util.Locale.ROOT)
        .replace(Regex("[^a-z0-9_]"), "_")
        .replace(Regex("__+"), "_")
        .trim('_')
    if (normalizedRole.isNotEmpty()) {
        cachedStringResourceByName("staff_role_$normalizedRole")?.takeIf { it.isNotBlank() }?.let { return it }
    }

    val exactMatch = staffRoleMap[trimmed]
        ?: staffRoleMap[trimmed.lowercase()]
        ?: staffRoleMap[trimmed.replaceFirstChar { it.uppercase() }]
    if (exactMatch != null) return exactMatch

    // Fallback: sub-phrase and word-by-word translation
    var fallbackTranslated = trimmed
    commonPhrasesMap.forEach { (en, tr) ->
        val pattern = "\\b${en}\\b".toRegex(RegexOption.IGNORE_CASE)
        fallbackTranslated = fallbackTranslated.replace(pattern, tr)
    }
    commonWordsMap.forEach { (en, tr) ->
        val pattern = "\\b${en}\\b".toRegex(RegexOption.IGNORE_CASE)
        fallbackTranslated = fallbackTranslated.replace(pattern, tr)
    }
    return fallbackTranslated
}

fun String.parseToMediaType(): MediaType {
    val lower = this.trim().lowercase()
    return when {
        // Manga / Novel / Webtoon
        lower.contains("manga") ||
        lower.contains("novel") ||
        lower.contains("roman") ||
        lower.contains("one-shot") ||
        lower.contains("one shot") ||
        lower.contains("tek bölüm") ||
        lower.contains("tek bolum") ||
        lower.contains("doujinshi") ||
        lower.contains("manhua") ||
        lower.contains("manhwa") -> MediaType.Manga

        // Movie / Film
        lower.contains("movie") ||
        lower.contains("film") -> MediaType.Movie

        // TV / TV Show / Dizi
        lower.contains("tvshow") ||
        lower.contains("tv show") ||
        lower.contains("tv dizisi") ||
        lower.contains("dizi") ||
        lower == "tv" -> MediaType.TvShow

        // Anime / Default (special, ova, ona, etc.)
        else -> MediaType.Anime
    }
}

fun String.toFriendlySourceLabel(): String {
    return when (this.lowercase().trim()) {
        "jikan", "mal", "myanimelist", "jikan (mal)", "mal (jikan)" -> "MyAnimeList"
        "anilist", "al" -> "AniList"
        "tmdb", "themoviedb", "tmdb_network", "tmdb-network", "tmdbnetwork" -> "TMDB"
        "kitsu"        -> "Kitsu"
        "shikimori", "shiki" -> "Shikimori"
        "simkl"        -> "Simkl"
        "bangumi", "bgm" -> "Bangumi"
        "fanart.tv", "fanart" -> "Fanart.tv"
        "animethemes", "animethemes.moe" -> "AnimeThemes"
        "fandom"       -> "Fandom"
        "manual"       -> "Manual"
        else           -> this
    }
}

// ─── Stüdyo / Şirket Adları (Japonca/Çince → Latin) ───────────────────────────

/**
 * Bangumi infobox'ındaki stüdyo/şirket adları Japonca (京都アニメーション) ya da Çince
 * gelir. Bu harita en bilinen şirketleri Latin ada çevirir; bilinmeyenler olduğu gibi
 * kalır (uydurma çeviri üretilmez). Latin ad, stüdyo aramasında da daha iyi sonuç verir.
 */
private val studioLatinNameMap = mapOf(
    "京都アニメーション" to "Kyoto Animation",
    "京都动画" to "Kyoto Animation",
    "京都動画" to "Kyoto Animation",
    "京阿尼" to "Kyoto Animation",
    "スタジオディーン" to "Studio Deen",
    "ディーン" to "Studio Deen",
    "ぴえろ" to "Studio Pierrot",
    "小丑社" to "Studio Pierrot",
    "東宝" to "Toho",
    "東宝映像事業部" to "Toho",
    "松竹" to "Shochiku",
    "ワーナー・ブラザース" to "Warner Bros.",
    "ポニーキャニオン" to "Pony Canyon",
    "ムービック" to "Movic",
    "ジェネオンエンタテインメント" to "Geneon Entertainment",
    "ジェネオン" to "Geneon",
    "創通" to "Sotsu",
    "バンダイビジュアル" to "Bandai Visual",
    "バンダイナムコピクチャーズ" to "Bandai Namco Pictures",
    "アニプレックス" to "Aniplex",
    "キングレコード" to "King Records",
    "日本コロムビア" to "Nippon Columbia",
    "スターチャイルドレコード" to "Starchild Records",
    "ランティス" to "Lantis",
    "フライングドッグ" to "FlyingDog",
    "フジテレビジョン" to "Fuji TV",
    "フジテレビ" to "Fuji TV",
    "日本テレビ" to "Nippon TV",
    "テレビ東京" to "TV Tokyo",
    "朝日放送" to "Asahi Broadcasting",
    "毎日放送" to "Mainichi Broadcasting",
    "TBSテレビ" to "TBS",
    "サンライズ" to "Sunrise",
    "日升动画" to "Sunrise",
    "日昇" to "Sunrise",
    "ボンズ" to "BONES",
    "骨头社" to "BONES",
    "マッドハウス" to "Madhouse",
    "疯房子" to "Madhouse",
    "プロダクション・アイジー" to "Production I.G",
    "ウィットスタジオ" to "WIT STUDIO",
    "霸权社" to "WIT STUDIO",
    "シャフト" to "Shaft",
    "トリガー" to "TRIGGER",
    "扳机社" to "TRIGGER",
    "クローバーワークス" to "CloverWorks",
    "ピーエーワークス" to "P.A.Works",
    "動画工房" to "Doga Kobo",
    "ディオメディア" to "Diomedéa",
    "シルバーリンク" to "Silver Link",
    "テレコム・アニメーションフィルム" to "Telecom Animation Film",
    "東映アニメーション" to "Toei Animation",
    "东映动画" to "Toei Animation",
    "タツノコプロ" to "Tatsunoko Production",
    "日本アニメーション" to "Nippon Animation",
    "J.C.STAFF" to "J.C.Staff",
    "ジブリ" to "Studio Ghibli",
    "スタジオジブリ" to "Studio Ghibli",
    "吉卜力" to "Studio Ghibli",
    "吉卜力工作室" to "Studio Ghibli",
    "カラー" to "khara",
    "オー・エル・エム" to "OLM",
    "シンエイ動画" to "Shin-Ei Animation",
    "手塚プロダクション" to "Tezuka Productions",
    "亜細亜堂" to "Asahi Production",
    "ぎゃろっぷ" to "Gallop",
    "白狐社" to "WHITE FOX",
    "幽浮社" to "ufotable",
    "飞碟社" to "ufotable",
    "プロダクションリード" to "Production Reed",
    "キネマシトラス" to "KINEMA CITRUS",
    "ライデンフィルム" to "LIDENFILMS",
    "ゼクシズ" to "ZEXCS",
    "ゴンゾ" to "GONZO",
    "ゼベック" to "XEBEC",
    "トライアングルスタッフ" to "Triangle Staff",
    "葦プロダクション" to "Ashi Productions",
    "虫プロダクション" to "Mushi Production",
    "東京ムービー" to "TMS Entertainment",
    "トムス・エンタテインメント" to "TMS Entertainment",
    "ブレインズ・ベース" to "Brain's Base",
    "アートランド" to "Artland",
    "エイトビット" to "8bit",
    "パッショーネ" to "Passione",
    "スタジオヴォルン" to "Studio VOLN",
    "トロイカ" to "TROYCA",
    "ラルケ" to "Lerche",
    "A-1 Pictures" to "A-1 Pictures",
    "A-1" to "A-1 Pictures",
    "A1社" to "A-1 Pictures",
    "ダビプロ" to "David Production",
    "feel." to "feel.",
    "FEEL" to "feel.",
    "角川書店" to "Kadokawa Shoten",
    "角川グループ" to "Kadokawa Group",
    "KADOKAWA メディアファクトリー" to "KADOKAWA Media Factory",
    "メディアファクトリー" to "Media Factory",
    "角川つばさ文庫" to "Kadokawa Tsubasa Bunko",
    "電撃文庫" to "Dengeki Bunko",
    "集英社" to "Shueisha",
    "講談社" to "Kodansha",
    "小学館" to "Shogakukan",
    "白泉社" to "Hakusensha",
    "秋田書店" to "Akita Shoten",
    "双葉社" to "Futabasha",
    "芳文社" to "Houbunsha",
    "新潮社" to "Shinchosha",
    "スクウェア・エニックス" to "Square Enix",
    "皮克斯" to "Pixar",
    "皮克斯动画工作室" to "Pixar",
    "迪士尼" to "Disney",
    "迪斯尼" to "Disney",
    "梦工厂" to "DreamWorks",
    "梦工厂动画" to "DreamWorks Animation",
    "漫威" to "Marvel",
    "DC漫画" to "DC Comics",
    "コミックス・ウェーブ・フィルム" to "CoMix Wave Films",
    "CJ ENM" to "CJ ENM"
)

// ─── Şirket Adları — TR/EN Yerelleştirme (MASTER 2026-10-11) ──────────────────

/**
 * Stüdyo / yapımcı / yayıncı ağ / yayımcı / dergi adını arayüz diline göre yerelleştirir.
 *
 * Zincir: gelen ad (`Kyoto Animation` ya da `京阿尼`) → [BangumiOrgDictionary] eşlemesi →
 * `bangumi_studio_*` / `bangumi_producer_*` / `bangumi_network_*` / `bangumi_publisher_*` /
 * `bangumi_magazine_*` dil dosyası kaynağı (tr/en arayüzünde) → küresel Latin ad
 * ([String.toLatinStudioName]) → orijinal metin.
 *
 * Marka adlarında TR = EN olduğu için İngilizce arayüzde de aynı değer okunur; **uydurma
 * çeviri üretilmez**, eşleşmeyen ad (üretim komiteleri dahil) olduğu gibi kalır.
 *
 * Not: Arama/dedup daima Latin adla çalışır (`KitsugiStudio.name`), bu fonksiyon yalnızca
 * **gösterim** içindir.
 */
/**
 * Kurum adını **gösterimden önce** temizler.
 *
 * Bangumi infobox'ı ve kullanıcı katkıları zaman zaman kurum alanına ad değil çöp yazıyor:
 *  • tırnak artefaktları — `"""アフタヌーン`, `I\\'ve`
 *  • noktalı virgülle eklenmiş baskı notları — `芳文社; B6版`
 *  • kaomoji — `っ´Д\`)っゼロ式の家`
 *
 * Bu adlar ekranda stüdyo/yayıncı olarak görünmemeli. Harf/rakam oranı yarıdan az olan
 * değerler (kaomoji, salt sembol) **boş** döner; çağıran taraf bunları listeden düşürür.
 * Gerçek adlar (kısa kısaltmalar dahil: `CR`, `MX`, `B站`) korunur.
 */
/** Ad değil, ifade/kaomoji göstergesi olan karakterler. */
private val KAOMOJI_MARKERS = charArrayOf(
    '\u00B4', '`', '\uFF40', '\u02CA', '\u02CB', '\uFF9F', '\u309C',
    '\u03C9', '\u2200', '\uFF3E', '^', '\uFF5E', '~', '\u2267', '\u2266', '\uFF57'
)

fun String.sanitizeOrgName(): String {
    var value = this.trim()
    if (value.isEmpty()) return value
    value = value.replace(Regex("^[\"'\u2018\u2019\u201C\u201D]+"), "")
        .replace(Regex("[\"'\u2018\u2019\u201C\u201D]+$"), "")
        .trim()
    if (value.contains(';') || value.contains('；')) {
        value = value.substringBefore(';').substringBefore('；').trim()
    }
    if (value.isEmpty()) return value
    // Kaomoji işaretleri: gerçek stüdyo/yayıncı adlarında asla geçmez, infobox çöpünde geçer.
    if (value.any { it in KAOMOJI_MARKERS }) return ""
    val alphanumeric = value.count { it.isLetterOrDigit() }
    if (alphanumeric == 0) return ""
    if (alphanumeric * 2 < value.length) return ""
    return value.replace(Regex("\\s+"), " ").trim()
}

fun String.toLocalizedOrgName(): String {
    val cleaned = this.sanitizeOrgName()
    if (cleaned.isEmpty()) return cleaned
    val turkish = isTurkish()
    BangumiOrgDictionary.localizedNameFor(cleaned, turkish)?.let { return it }
    val latin = cleaned.toLatinStudioName()
    if (latin != cleaned) {
        BangumiOrgDictionary.localizedNameFor(latin, turkish)?.let { return it }
    }
    // Kurum adları özel addır: dil bağımsız Latin yazımı. Kaynak bulunmazsa Kiril romanize edilir.
    val fallback = BangumiOrgDictionary.latinNameFor(cleaned) ?: latin
    return if (fallback.hasCyrillicCharacters()) fallback.transliterateCyrillic() else fallback
}

/**
 * [String.toLocalizedOrgName]'in "eşleşme yoksa null" biçimi — çağıran orijinal metni
 * göstermek isteyebilir (ör. çip tıklamasında ham adı korumak için).
 */
fun String.toLocalizedOrgNameOrNull(): String? {
    val cleaned = this.sanitizeOrgName()
    if (cleaned.isEmpty()) return null
    val turkish = isTurkish()
    BangumiOrgDictionary.localizedNameFor(cleaned, turkish)?.let { return it }
    val latin = cleaned.toLatinStudioName()
    if (latin != cleaned) {
        BangumiOrgDictionary.localizedNameFor(latin, turkish)?.let { return it }
    }
    val fallback = BangumiOrgDictionary.latinNameFor(cleaned)
    return if (fallback?.hasCyrillicCharacters() == true) fallback.transliterateCyrillic() else fallback
}

/**
 * Stüdyo/şirket adını bilinen resmî Latin yazımıyla eşler.
 *
 * Bu bir çeviri tablosu **değildir**: yalnızca Bangumi'nin kendi kaydettiği takma/ana adları
 * (京阿尼 → Kyoto Animation, 骨头社 → BONES) tanınan biçimine döndürür. Eşleşmeyen ad — üretim
 * komisyonları dahil — olduğu gibi kalır; uydurma isim üretilmez. Parantezli notlar ve
 * "株式会社" gibi unvan ekleri lookup için yok sayılır.
 */
fun String.toLatinStudioName(): String {
    val cleaned = this.trim()
    if (cleaned.isEmpty()) return cleaned
    // MASTER 2026-10-11 şirket sözlüğü (1016 eşleme) önce denenir: CJK/Japonca varyantlar
    // (`京アニ`, `木棉花`, `光坂高校演劇部` hariç komite adları) ve İngilizce küresel adlar.
    BangumiOrgDictionary.latinNameFor(cleaned)?.let { return it }
    studioLatinNameMap[cleaned]?.let { return it }
    val normalized = cleaned
        .replace(Regex("【[^】]*】"), " ")
        .replace(Regex("""\([^)]*\)|（[^）]*）"""), " ")
        .replace(Regex("^(合同会社|株式会社|有限会社|有限公司|股份有限公司)"), "")
        .replace(Regex("(合同会社|株式会社|有限会社|有限公司|股份有限公司)$"), "")
        .replace(Regex("""\s+"""), " ")
        .trim()
    if (normalized.isNotEmpty() && normalized != cleaned) {
        studioLatinNameMap[normalized]?.let { return it }
    }
    return studioLatinNameMap.entries.firstOrNull { entry ->
        entry.key.equals(cleaned, ignoreCase = true) || entry.key.equals(normalized, ignoreCase = true)
    }?.value ?: this
}

/** Kurum adının yerelleştirilmiş hâlinden üretilen iki harfli avatar kısaltması. */
fun String.toOrgMonogram(): String = toLocalizedOrgName().take(2).uppercase()

