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

private val TRANSLATION_BARE_URL_REGEX = Regex("""^\s*(?:https?://|www\.)\S+\s*$""", RegexOption.IGNORE_CASE)

private val TRANSLATION_INLINE_URL_REGEX = Regex("""(?:https?://|www\.)\S+""", RegexOption.IGNORE_CASE)

private val TRANSLATION_FILE_NAME_REGEX = Regex(
    """^\s*\S+\.(?:ogg|oga|mp3|wav|flac|m4a|mp4|webm|mkv|png|jpe?g|gif|webp|svg|pdf|txt|csv|json|xml)\s*$""",
    RegexOption.IGNORE_CASE
)

private val TRANSLATION_WIKI_NAMESPACE_REGEX = Regex(
    """^\s*(?:File|Image|Category|Template|Dosya|Resim|Kategori|Şablon)\s*:\s*\S+\s*$""",
    RegexOption.IGNORE_CASE
)

private val TRANSLATION_WIKITEXT_ONLY_REGEX = Regex(
    """^\s*(?:\[\[[^\]]+\]\]|\{\{[^}]+}}|\[https?://[^\]]+])\s*$""",
    RegexOption.IGNORE_CASE
)
