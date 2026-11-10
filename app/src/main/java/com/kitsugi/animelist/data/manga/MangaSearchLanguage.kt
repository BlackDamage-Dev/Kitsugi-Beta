package com.kitsugi.animelist.data.manga

import java.text.Normalizer
import java.util.Locale

/**
 * KAYNAK ARAMA DİLİ.
 *
 * Sorun: "Oku" butonuna basınca uygulama, kaynak aramasını **başlık dili**
 * ayarına bakmadan yapıyordu. Örneğin kullanıcı başlık dilini `ENGLISH` seçtiği
 * hâlde arama, listede görünen/elde kalan rastgele bir adla (çoğu zaman Romaji
 * ya da orijinal CJK ad) yapılıyordu. Sonuç: arama boş dönüyor veya alakasız
 * seriler geliyordu.
 *
 * Çözüm: arama sorgusu tek bir yerden ([MangaSearchLanguage.buildQueries])
 * üretilir. Sıra, kullanıcının seçimine göre belirlenir ve kaynakların çoğunun
 * (MangaDex türevleri, Mihon/Kotatsu eklentileri) beklediği ada göre sıralanır:
 *
 *   ENGLISH        → İngilizce ad → Romaji → alternatif adlar → orijinal ad
 *   ROMAJI         → Romaji → Latin orijinal → İngilizce
 *   NATIVE         → Orijinal (CJK) ad → Romaji → İngilizce
 *   LOCAL (Türkçe) → Türkçe/yerel ad (varsa) → İngilizce → Romaji
 *
 * Her durumda son çare olarak tüm adlar denenir, böylece arama asla boş kalmaz.
 */
enum class MangaSearchLanguage {
    /** Uygulamanın arayüz/başlık dilinden bağımsız, her zaman İngilizce ad. */
    English,

    /** Latin harfli orijinal ad (çoğu kaynağın ana kataloğu). */
    Romaji,

    /** Orijinal ad (Japonca/Çince/Korece olabilir). */
    Native,

    /** Yerel/çevrilmiş ad (ör. Türkçe yayın adı). */
    Local;

    val id: String
        get() = when (this) {
            English -> "ENGLISH"
            Romaji -> "ROMAJI"
            Native -> "NATIVE"
            Local -> "LOCAL"
        }

    companion object {
        fun fromId(id: String?): MangaSearchLanguage = when (id?.trim()?.uppercase()) {
            "EN", "ENG", "ENGLISH" -> English
            "NATIVE", "JAPANESE", "ORIGINAL" -> Native
            "LOCAL", "TURKISH", "TR" -> Local
            else -> Romaji
        }

        fun idOf(language: MangaSearchLanguage): String = language.id

        /** İnsan okur etiketi (ayar ekranı için, dile çevrilebilir anahtar). */
        fun labelKey(language: MangaSearchLanguage): String = when (language) {
            English -> "manga_search_lang_english"
            Romaji -> "manga_search_lang_romaji"
            Native -> "manga_search_lang_native"
            Local -> "manga_search_lang_local"
        }
    }
}

/**
 * Arama sorgusu üreticisi — tamamen saf (ağ/UI bağımsız), birim testleriyle doğrulanır.
 */
object MangaSearchQueryBuilder {

    /** Sorgu uzunluğu alt sınırı: tek harflik aramalar kaynakları boşa yorar. */
    const val MIN_QUERY_LENGTH = 2

    /**
     * Seçili dile göre denenecek sorguları **öncelik sırasıyla** üretir.
     *
     * @param titleLanguage Uygulamanın başlık dili ayarı (`ENGLISH`/`ROMAJI`/`NATIVE`).
     * @param titles Yerel, İngilizce, Romaji, orijinal ad ve alternatif adlar.
     * @param searchLanguage Kaynak araması için seçilmiş dil (ayarlardan).
     * @param followTitleLanguage Arama dili "başlık dilini takip et" modundaysa true.
     */
    fun buildQueries(
        titleLanguage: String,
        searchLanguage: MangaSearchLanguage?,
        followTitleLanguage: Boolean,
        localTitle: String? = null,
        englishTitle: String? = null,
        romajiTitle: String? = null,
        nativeTitle: String? = null,
        synonyms: List<String> = emptyList()
    ): List<String> {
        val english = clean(englishTitle)
        val romaji = clean(romajiTitle)
        val native = clean(nativeTitle)
        val local = clean(localTitle)
        // `localTitle` çoğu zaman Romaji/Latin orijinal addır; CJK ise orijinal say.
        val localIsNative = local != null && containsCjk(local)
        val latinLocal = if (localIsNative) null else local
        val original = native ?: if (localIsNative) local else null

        val synonymsClean = synonyms.mapNotNull { clean(it) }

        val ordered: List<String?> = when {
            followTitleLanguage -> orderedFor(titleLanguage, latinLocal, english, romaji, original)
            searchLanguage != null -> orderedFor(searchLanguage.id, latinLocal, english, romaji, original)
            else -> orderedFor(titleLanguage, latinLocal, english, romaji, original)
        }

        // Yedek zincir: seçili dil boş dönerse kalan tüm adlar denenir.
        val fallback = listOf(latinLocal, english, romaji, original) + synonymsClean

        return (ordered + fallback + synonymsClean)
            .mapNotNull { clean(it) }
            .distinctBy { it.lowercase(Locale.ROOT) }
    }

    /**
     * Bir dil için aday sırası.
     * `LOCAL` = yerel/çevrilmiş ad; yoksa İngilizce'ye düşer.
     */
    fun orderedFor(
        languageId: String?,
        localTitle: String?,
        englishTitle: String?,
        romajiTitle: String?,
        nativeTitle: String?
    ): List<String?> = when (languageId?.trim()?.uppercase(Locale.ROOT)) {
        "EN", "ENG", "ENGLISH" -> listOf(englishTitle, romajiTitle, localTitle, nativeTitle)
        "NATIVE", "JAPANESE", "ORIGINAL" -> listOf(nativeTitle, romajiTitle, localTitle, englishTitle)
        "LOCAL", "TURKISH", "TR" -> listOf(localTitle, englishTitle, romajiTitle, nativeTitle)
        else -> listOf(romajiTitle, localTitle, englishTitle, nativeTitle)
    }

    /** Boşlukları sadeleştirir; boş/anlamsız sorguları eler. */
    fun clean(raw: String?): String? {
        val value = raw?.trim()?.replace(Regex("\\s+"), " ") ?: return null
        if (value.length < MIN_QUERY_LENGTH) return null
        if (value.all { !it.isLetterOrDigit() }) return null
        return value
    }

    /** Türkçe karakterleri sadeleştirir (kaynak aramaları ASCII bekler). */
    fun ascii(raw: String?): String? {
        val value = clean(raw) ?: return null
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        val mapped = buildString(normalized.length) {
            normalized.forEach { c ->
                append(
                    when (c) {
                        'ı' -> 'i'; 'İ' -> 'I'; 'ş' -> 's'; 'Ş' -> 'S'
                        'ğ' -> 'g'; 'Ğ' -> 'G'; 'ç' -> 'c'; 'Ç' -> 'C'
                        'ö' -> 'o'; 'Ö' -> 'O'; 'ü' -> 'u'; 'Ü' -> 'U'
                        else -> c
                    }
                )
            }
        }
        return clean(mapped)
    }

    fun containsCjk(text: String?): Boolean {
        val value = text ?: return false
        return value.any { c ->
            c.code in 0x3040..0x30FF ||      // Hiragana / Katakana
            c.code in 0x3400..0x4DBF ||      // CJK ext A
            c.code in 0x4E00..0x9FFF ||      // CJK unified
            c.code in 0xF900..0xFAFF ||      // CJK compatibility
            c.code in 0xAC00..0xD7AF ||      // Hangul
            c.code in 0xFF65..0xFF9F         // Half-width katakana
        }
    }
}
