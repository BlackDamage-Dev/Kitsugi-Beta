package com.kitsugi.animelist.utils

/**
 * ÇEVİRİ KORUMASI — teknik değerler.
 *
 * Sadece URL'ler değil; `File:`, `Category:` gibi ad alanı değerleri, dosya adları
 * (`01_-_Yume_Tourou.ogg`) ve rakam/işaret ağırlıklı jetonlar da çeviriye gönderilmemelidir.
 * Google Translate bunları bozar: URL'ye boşluk ekler, yol parçasını çevirir, dosya adını
 * "Türkçeleştirir"; sonuçta bağlantı erişilemez hâle gelir ve kullanıcı yanlış sayfaya düşer.
 *
 * Bu kontrol hem tek başına bir değer (bilgi kutusu satırı, tablo hücresi) hem de tüm metin
 * için kullanılır; metnin tamamı teknikse hiç çeviri isteği yapılmaz.
 */
fun isTechnicalTranslationValue(value: String): Boolean {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return true
    if (trimmed.length <= 2) return true
    if (!trimmed.any { it.isLetter() }) return true
    if (TRANSLATION_BARE_URL_REGEX.matches(trimmed)) return true
    if (TRANSLATION_FILE_NAME_REGEX.matches(trimmed)) return true
    if (TRANSLATION_WIKI_NAMESPACE_REGEX.matches(trimmed)) return true
    if (TRANSLATION_WIKITEXT_ONLY_REGEX.matches(trimmed)) return true

    // İçinde URL var ama kalan kısımda neredeyse hiç harf yok → değer teknik.
    if (TRANSLATION_INLINE_URL_REGEX.containsMatchIn(trimmed)) {
        val withoutUrls = TRANSLATION_INLINE_URL_REGEX.replace(trimmed, " ")
        if (withoutUrls.count { it.isLetter() } <= 3) return true
    }

    // Boşluksuz ve sayı/işaret yoğun jetonlar (sürüm numarası, kimlik, dosya parçası).
    if (!trimmed.contains(' ')) {
        val digits = trimmed.count { it.isDigit() }
        val letters = trimmed.count { it.isLetter() }
        if (digits >= 3 && letters <= 6) return true
    }

    return false
}

/**
 * Derlenemeyen bir regex, dosya düzeyi `val` içindeyken sınıfın `<clinit>`'inde
 * [ExceptionInInitializerError] fırlatıp TÜM uygulamayı çökertiyordu (bkz. 2026-11-04
 * çökme raporu: bozuk wikitext regex'i). Bu yardımcı, desen derlenemezse asla eşleşmeyen
 * güvenli bir regex'e düşer; çeviri katmanı böyle bir durumda en fazla bazı teknik
 * değerleri çeviriye gönderir, ASLA çökmez.
 */
private val NEVER_MATCH_REGEX = Regex("(?!)")

private fun safeTranslationRegex(pattern: String, vararg options: RegexOption): Regex =
    runCatching { Regex(pattern, options.toSet()) }.getOrElse { NEVER_MATCH_REGEX }

private val TRANSLATION_BARE_URL_REGEX = safeTranslationRegex("""^\s*(?:https?://|www\.)\S+\s*$""", RegexOption.IGNORE_CASE)

private val TRANSLATION_INLINE_URL_REGEX = safeTranslationRegex("""(?:https?://|www\.)\S+""", RegexOption.IGNORE_CASE)

private val TRANSLATION_FILE_NAME_REGEX = safeTranslationRegex(
    """^\s*\S+\.(?:ogg|oga|mp3|wav|flac|m4a|mp4|webm|mkv|png|jpe?g|gif|webp|svg|pdf|txt|csv|json|xml)\s*$""",
    RegexOption.IGNORE_CASE
)

private val TRANSLATION_WIKI_NAMESPACE_REGEX = safeTranslationRegex(
    """^\s*(?:File|Image|Category|Template|Dosya|Resim|Kategori|Şablon)\s*:\s*\S+\s*$""",
    RegexOption.IGNORE_CASE
)

// Yalnızca TAMAMI wiki sözdizimi olan satırlar teknik sayılır ([[...]], {{...}}, [http...]).
// Köşeli ayraçların TÜMÜ Android ICU uyumlu biçimde kaçırıldı: eski sürümdeki kaçırılmamış
// `}}` ICU'da PatternSyntaxException → ExceptionInInitializerError ile uygulamayı çökertiyordu
// (2026-11-04 raporu). JVM'de geçerli olan bu desen ICU'da geçersizdir; `\}\}` şart.
private val TRANSLATION_WIKITEXT_ONLY_REGEX = safeTranslationRegex(
    """^\s*(?:\[\[[^\]]+\]\]|\{\{[^}]+\}\}|\[https?://[^\]]+\])\s*$"""
)
