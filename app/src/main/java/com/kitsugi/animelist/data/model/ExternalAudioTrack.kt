package com.kitsugi.animelist.data.model

import java.io.Serializable
import java.util.Locale

/**
 * Harici veya ayrık (demuxed) bir ses parçasını temsil eder.
 *
 * CloudStream `ExtractorLink.audioTracks` (`AudioFile`), HLS `#EXT-X-MEDIA:TYPE=AUDIO`
 * rendisyonları, MPEG-DASH `<AdaptationSet contentType="audio">` temsilleri ve çevrimdışı
 * yan (companion) ses dosyaları için ortak veri modelidir.
 */
data class ExternalAudioTrack(
    val url: String,
    val label: String = "",
    val lang: String = "",
    val headers: Map<String, String> = emptyMap(),
    val codecs: String = "",
    val channels: Int = 0,
    val isDefault: Boolean = false
) : Serializable {

    val safeLabel: String get() = label ?: ""
    val safeLang: String get() = lang ?: ""
    val safeHeaders: Map<String, String> get() = headers ?: emptyMap()
    val safeCodecs: String get() = codecs ?: ""

    /** HTTP isteğine eklenecek gerçek başlıklar (dahili meta başlıkları hariç). */
    fun httpHeaders(): Map<String, String> =
        safeHeaders.filterKeys { key ->
            !key.equals(META_HEADER_LABEL, ignoreCase = true) &&
                !key.equals(META_HEADER_LANG, ignoreCase = true)
        }

    fun displayTitle(turkishUi: Boolean = true, fallbackIndex: Int = 0): String {
        val cleanLabel = safeLabel.trim()
        val normLang = normalizeLangCode(safeLang.ifBlank { inferLanguageFromText("$cleanLabel $url") })
        val langDisplay = languageDisplayName(normLang, turkishUi)
        val base = when {
            cleanLabel.isNotBlank() && langDisplay != null &&
                !cleanLabel.contains(langDisplay, ignoreCase = true) &&
                !cleanLabel.equals(normLang, ignoreCase = true) -> "$cleanLabel ($langDisplay)"
            cleanLabel.isNotBlank() -> cleanLabel
            langDisplay != null -> langDisplay
            else -> if (turkishUi) "Ses #${fallbackIndex + 1}" else "Audio #${fallbackIndex + 1}"
        }
        val chSuffix = when (channels) {
            6 -> " · 5.1"
            8 -> " · 7.1"
            2 -> " · 2.0"
            1 -> " · Mono"
            else -> ""
        }
        return base + chSuffix
    }

    companion object {
        const val META_HEADER_LABEL = "X-Kitsugi-Audio-Label"
        const val META_HEADER_LANG = "X-Kitsugi-Audio-Lang"

        fun fromUrlAndHeaders(
            url: String,
            trackHeaders: Map<String, String>? = null,
            fallbackHeaders: Map<String, String>? = null,
            explicitLabel: String? = null,
            explicitLang: String? = null,
            index: Int = 0
        ): ExternalAudioTrack {
            val merged = LinkedHashMap<String, String>()
            fallbackHeaders?.forEach { (k, v) -> if (k.isNotBlank() && v.isNotBlank()) merged[k] = v }
            trackHeaders?.forEach { (k, v) -> if (k.isNotBlank() && v.isNotBlank()) merged[k] = v }

            val headerLabel = merged.entries
                .firstOrNull { it.key.equals(META_HEADER_LABEL, ignoreCase = true) || it.key.equals("X-Audio-Label", ignoreCase = true) }
                ?.value
            val headerLang = merged.entries
                .firstOrNull { it.key.equals(META_HEADER_LANG, ignoreCase = true) || it.key.equals("X-Audio-Lang", ignoreCase = true) }
                ?.value

            val inferredLang = normalizeLangCode(
                explicitLang?.takeIf { it.isNotBlank() }
                    ?: headerLang?.takeIf { it.isNotBlank() }
                    ?: inferLanguageFromText("${explicitLabel.orEmpty()} $url")
            )
            val resolvedLabel = explicitLabel?.trim()?.takeIf { it.isNotBlank() }
                ?: headerLabel?.trim()?.takeIf { it.isNotBlank() }
                ?: languageDisplayName(inferredLang, turkishUi = true)
                ?: if (index == 0) "Ses #1" else "Ses #${index + 1}"

            return ExternalAudioTrack(
                url = url,
                label = resolvedLabel,
                lang = inferredLang,
                headers = merged
            )
        }

        fun normalizeLangCode(raw: String?): String {
            val clean = raw?.trim()?.lowercase(Locale.ROOT)
                ?.substringBefore('-')
                ?.substringBefore('_')
                .orEmpty()
            return when (clean) {
                "tur", "tr", "turkish", "türkçe", "turkce" -> "tr"
                "eng", "en", "english", "ingilizce" -> "en"
                "jpn", "ja", "jap", "japanese", "japonca" -> "ja"
                "deu", "ger", "de", "german", "almanca" -> "de"
                "fra", "fre", "fr", "french", "fransızca", "fransizca" -> "fr"
                "spa", "es", "spanish", "ispanyolca" -> "es"
                "ita", "it", "italian", "italyanca" -> "it"
                "rus", "ru", "russian", "rusça", "rusca" -> "ru"
                "kor", "ko", "korean", "korece" -> "ko"
                "zho", "chi", "zh", "chinese", "çince", "cince" -> "zh"
                "ara", "ar", "arabic", "arapça", "arapca" -> "ar"
                "hin", "hi", "hindi", "hintçe", "hintce" -> "hi"
                "por", "pt", "portuguese", "portekizce" -> "pt"
                "aze", "az", "azerbaijani", "azerice" -> "az"
                "und", "unknown", "none", "null" -> ""
                else -> if (clean.length in 2..3 && clean.all { it in 'a'..'z' }) clean else ""
            }
        }

        fun inferLanguageFromText(text: String?): String {
            if (text.isNullOrBlank()) return ""
            val lower = text.lowercase(Locale.ROOT)
            val queryLang = Regex("""[?&](?:lang|language|audio|hl)=([a-zA-Z_-]{2,8})""")
                .find(lower)?.groupValues?.getOrNull(1)
            if (!queryLang.isNullOrBlank()) {
                val normalized = normalizeLangCode(queryLang)
                if (normalized.isNotBlank()) return normalized
            }
            return when {
                Regex("""(^|[^a-z])(türkçe|turkce|turkish|dublaj|tr-tr|audio[_/-]?tr|lang[_/-]?tr|[_/.-]tur[_/.-]|[_/.-]tr[_/.-])($|[^a-z])""").containsMatchIn(lower) -> "tr"
                Regex("""(^|[^a-z])(japonca|japanese|ja-jp|audio[_/-]?ja|[_/.-]jpn[_/.-]|[_/.-]jap[_/.-])($|[^a-z])""").containsMatchIn(lower) -> "ja"
                Regex("""(^|[^a-z])(ingilizce|english|en-us|en-gb|audio[_/-]?en|[_/.-]eng[_/.-])($|[^a-z])""").containsMatchIn(lower) -> "en"
                Regex("""(^|[^a-z])(almanca|german|de-de|[_/.-]deu[_/.-]|[_/.-]ger[_/.-])($|[^a-z])""").containsMatchIn(lower) -> "de"
                Regex("""(^|[^a-z])(fransızca|fransizca|french|[_/.-]fra[_/.-]|[_/.-]fre[_/.-])($|[^a-z])""").containsMatchIn(lower) -> "fr"
                Regex("""(^|[^a-z])(ispanyolca|spanish|[_/.-]spa[_/.-])($|[^a-z])""").containsMatchIn(lower) -> "es"
                Regex("""(^|[^a-z])(rusça|rusca|russian|[_/.-]rus[_/.-])($|[^a-z])""").containsMatchIn(lower) -> "ru"
                Regex("""(^|[^a-z])(korece|korean|[_/.-]kor[_/.-])($|[^a-z])""").containsMatchIn(lower) -> "ko"
                else -> ""
            }
        }

        fun languageDisplayName(code: String?, turkishUi: Boolean = true): String? {
            return when (normalizeLangCode(code)) {
                "tr" -> if (turkishUi) "Türkçe" else "Turkish"
                "en" -> if (turkishUi) "İngilizce" else "English"
                "ja" -> if (turkishUi) "Japonca" else "Japanese"
                "de" -> if (turkishUi) "Almanca" else "German"
                "fr" -> if (turkishUi) "Fransızca" else "French"
                "es" -> if (turkishUi) "İspanyolca" else "Spanish"
                "it" -> if (turkishUi) "İtalyanca" else "Italian"
                "ru" -> if (turkishUi) "Rusça" else "Russian"
                "ko" -> if (turkishUi) "Korece" else "Korean"
                "zh" -> if (turkishUi) "Çince" else "Chinese"
                "ar" -> if (turkishUi) "Arapça" else "Arabic"
                "hi" -> if (turkishUi) "Hintçe" else "Hindi"
                "pt" -> if (turkishUi) "Portekizce" else "Portuguese"
                "az" -> if (turkishUi) "Azerice" else "Azerbaijani"
                else -> code?.trim()?.takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT)
            }
        }
    }
}
