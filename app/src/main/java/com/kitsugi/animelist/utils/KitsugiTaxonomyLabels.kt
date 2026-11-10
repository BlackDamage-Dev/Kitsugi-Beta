package com.kitsugi.animelist.utils

import java.util.Locale

/**
 * **Birleşik taksonomi (tür / etiket) gösterim katmanı.**
 *
 * Uygulama yedi kaynaktan detay toplar ve her kaynak tür/etiketleri **kendi dilinde** verir:
 *
 * | Kaynak | Tür/etiket dili | Örnek |
 * |---|---|---|
 * | AniList · MAL/Jikan · Kitsu · Simkl · TMDB | İngilizce | `Slice of Life`, `Super Power` |
 * | Bangumi | Basit/geleneksel Çince + Japonca | `治愈系`, `催涙`, `ゲーム改` |
 * | Shikimori | **Rusça** | `Повседневность`, `Экшен`, `Махо-сёдзё` |
 *
 * Bu dosya o üç dili **tek kanonik anahtara** indirger; böylece
 * `Повседневность` · `Slice of Life` · `日常` · `Günlük Yaşam` ekranda **tek çip** olur ve
 * Rusça/Çince metin hiçbir koşulda ekrana düşmez.
 *
 * Çözüm sırası:
 *  1. Bangumi sözlüğü (Çince/Japonca → TR/EN) + `bangumi_tag_*` dil dosyası override'ı,
 *  2. Rusça sözlük ([RussianTaxonomyDictionary]) + `taxonomy_tag_*` dil dosyası override'ı,
 *  3. İngilizce → Türkçe yerleşik tablolar (arayüz Türkçe ise),
 *  4. Çözülemeyen Kiril metni **Latin harflerine çevrilir** (asla Kiril görünmez),
 *  5. Çözülemeyen Çince/Japonca etiket **gösterimden çıkarılır** (modelde korunur).
 */
data class TaxonomyLabelEntry(
    /** Kanonik grup anahtarı (`slice_of_life`, `action` ...). */
    val key: String,
    val turkish: String,
    val english: String,
    /** Girişin geldiği sözlük: `bangumi` · `russian` · `english`. */
    val origin: String
)

/**
 * Etiketin kanonik sözlük girişi (Türkçe + İngilizce + anahtar). Bilinmiyorsa null.
 *
 * Ayrı sözlük girişi olmayan ama İngilizce yerleşik tabloda karşılığı bulunan değerler için
 * anahtar İngilizce addan üretilir (`Super Power` → `super_power`), böylece bu değerler de
 * diller arası tekilleştirmeye katılır.
 */
fun String.taxonomyEntryOrNull(): TaxonomyLabelEntry? {
    val cleaned = this.trim()
    if (cleaned.isEmpty()) return null

    BangumiTagDictionary.entryFor(cleaned)?.let { tag ->
        return TaxonomyLabelEntry(
            key = tag.key,
            turkish = tag.turkish,
            english = tag.english,
            origin = "bangumi"
        )
    }

    RussianTaxonomyDictionary.entryFor(cleaned)?.let { tag ->
        return TaxonomyLabelEntry(
            key = tag.key,
            turkish = tag.turkish,
            english = tag.english,
            origin = "russian"
        )
    }

    // İngilizce yerleşik tablo: anahtar üretilebilir mi?
    val english = englishTaxonomyName(cleaned) ?: return null
    val turkish = englishTaxonomyToTurkish(english) ?: return null
    return TaxonomyLabelEntry(
        key = english.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "_").trim('_'),
        turkish = turkish,
        english = english,
        origin = "english"
    )
}

/**
 * Etiketi arayüz diline çevirir.
 *
 * Sıra: Bangumi sözlüğü (`bangumi_tag_*` override'lı) → Rusça sözlük (`taxonomy_tag_*`
 * override'lı) → İngilizce yerleşik tablolar → Kiril transliterasyonu → özgün metin.
 */
fun String.toLocalizedTaxonomyLabel(): String {
    val cleaned = this.trim()
    if (cleaned.isEmpty()) return cleaned

    // 1) Bangumi (Çince / Japonca)
    cleaned.toLocalizedBangumiTagOrNull()?.let { return it }

    // 2) Rusça (Shikimori)
    RussianTaxonomyDictionary.entryFor(cleaned)?.let { entry ->
        taxonomyStringOverride(entry.key)?.let { return it }
        return if (isTurkish()) entry.turkish else entry.english
    }

    // 3) İngilizce → Türkçe (arayüz Türkçe ise)
    if (isTurkish()) {
        englishTaxonomyToTurkish(cleaned)?.let { return it }
    }

    // 3b) Arayüz İngilizce ise Türkçe etiket kanonik İngilizce adına çevrilir
    // (`Aksiyon` → `Action`); bilinmeyen değer olduğu gibi kalır.
    if (isEnglish()) {
        englishTaxonomyName(cleaned)?.let { return it }
    }

    // 4) Kiril alfabesi: sözlükte yok — okunabilir Latin harflerine çevir.
    if (cleaned.hasCyrillicCharacters()) {
        return cleaned.transliterateCyrillic()
    }

    // 5) Özgün metin (tanınan İngilizce etiket)
    return cleaned
}

/**
 * İki etiketin **aynı anlama** gelip gelmediğini söyleyen kanonik anahtar.
 *
 * `Экшен` · `Action` · `アクション` · `动作` · `Aksiyon` → hepsi `action`.
 * Sözlükte olmayan değerler için normalize edilmiş Latin yazımı kullanılır, böylece
 * yalnızca büyük/küçük harf ya da noktalama farkı olan etiketler de tekilleşir.
 */
fun String.taxonomyCanonicalKey(): String {
    val cleaned = this.trim()
    if (cleaned.isEmpty()) return ""
    taxonomyEntryOrNull()?.let { return it.key }
    val base = if (cleaned.hasCyrillicCharacters()) cleaned.transliterateCyrillic() else cleaned
    return base.lowercase(Locale.ROOT)
        .replace(Regex("[’'`´]"), "")
        .replace(Regex("[^a-z0-9а-я]+"), " ")
        .trim()
}

/** Metin Kiril alfabesi (Rusça/Ukraynaca/Belarusça) içeriyor mu? */
fun String.hasCyrillicCharacters(): Boolean = any { ch ->
    val code = ch.code
    code in 0x0400..0x04FF || code in 0x0500..0x052F
}

/** Metin CJK (Çince/Japonca) karakter içeriyor mu? */
fun String.hasCjkCharacters(): Boolean = any { ch ->
    val code = ch.code
    code in 0x3040..0x30FF ||
        code in 0x3400..0x4DBF ||
        code in 0x4E00..0x9FFF ||
        code in 0xF900..0xFAFF ||
        code in 0xFF66..0xFF9D
}

private val CYRILLIC_LATIN: Map<Char, String> = mapOf(
    'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "yo",
    'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
    'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
    'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "shch",
    'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya",
    // Ukraynaca / Belarusça / Sırpça ek harfler
    'і' to "i", 'ї' to "yi", 'є' to "ye", 'ґ' to "g", 'ў' to "u",
    'ђ' to "dj", 'ј' to "j", 'љ' to "lj", 'њ' to "nj", 'ћ' to "c", 'џ' to "dz"
)

/**
 * Kiril metnini okunabilir Latin harflerine çevirir (`Повседневность` → `Povsednevnost`).
 * Sözlükte bulunamayan Rusça etiket/kurum adları için son çare gösterim biçimidir —
 * ekranda Kiril alfabesi görünmesini engeller.
 */
fun String.transliterateCyrillic(): String {
    if (!hasCyrillicCharacters()) return this
    val sb = StringBuilder(length + 8)
    for (ch in this) {
        val mapped = CYRILLIC_LATIN[ch.lowercaseChar()]
        if (mapped == null) {
            sb.append(ch)
        } else {
            if (ch.isUpperCase()) {
                sb.append(mapped.replaceFirstChar { it.uppercaseChar() })
            } else {
                sb.append(mapped)
            }
        }
    }
    return sb.toString().replace(Regex("\\s+"), " ").trim()
}

/**
 * Kurum (stüdyo / yapımcı / yayıncı / kanal / dergi) adının **ekranda gösterilecek** hâli.
 *
 * Bangumi MASTER sözlüğü (1016 eşleme + küresel Latin adlar) → Kiril transliterasyonu.
 * Çözülemeyen CJK ad son çaredir ve olduğu gibi kalır: Bangumi kurum adları için
 * [BangumiOrgResolver] canlı çözümleme + sözlük birlikte çalışır, kalan sayı ihmal edilebilir.
 */
fun String.toDisplayOrgName(): String {
    val localized = toLocalizedOrgName()
    if (localized.hasCyrillicCharacters()) return localized.transliterateCyrillic()
    return localized
}

/**
 * Etiket sözlükte **hiç** karşılık bulamadı ve hâlâ yabancı alfabede mi?
 * Gösterim katmanı böyle etiketleri çip listesinden çıkarır (modelde korunur).
 */
fun isUnresolvedForeignLabel(value: String): Boolean {
    val cleaned = value.trim()
    if (cleaned.isEmpty()) return false
    if (cleaned.hasCyrillicCharacters()) return false      // transliterasyon mümkün
    if (!cleaned.hasCjkCharacters()) return false
    if (BangumiTagDictionary.entryFor(cleaned) != null) return false
    val localized = cleaned.toLocalizedBangumiTagOrNull()
    return localized == null || localized.trim() == cleaned
}
