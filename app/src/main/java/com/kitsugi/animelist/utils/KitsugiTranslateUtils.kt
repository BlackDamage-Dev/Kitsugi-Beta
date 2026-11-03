package com.kitsugi.animelist.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Çeviri ayarlarının süreç içi anlık görüntüsü. DataStore akışı her ayar
 * değişiminde [update] çağırır; böylece composable dışındaki (suspend olmayan)
 * çeviri intent'leri ayarlara senkron erişebilir.
 */
object KitsugiTranslatePrefs {
    @Volatile
    var sourceLanguage: String = "auto"
        private set

    @Volatile
    var targetLanguage: String = ""
        private set

    @Volatile
    var isInitialized: Boolean = false
        private set

    fun update(source: String?, target: String?) {
        sourceLanguage = source?.takeIf { it.isNotBlank() } ?: "auto"
        targetLanguage = target?.takeIf { it.isNotBlank() } ?: ""
        isInitialized = true
    }

    /** Hedef dil: kullanıcı ayarı → yoksa uygulamanın aktif dili (varsayılan Türkçe). */
    fun resolvedTarget(): String {
        targetLanguage.takeIf { it.isNotBlank() }?.let { return it }
        val tag = com.kitsugi.animelist.LocaleCache.localeTag
        if (tag != com.kitsugi.animelist.LocaleCache.UNSET && tag.isNotBlank()) {
            return tag.substringBefore('-').ifBlank { "tr" }
        }
        return "tr"
    }
}

object KitsugiTranslateUtils {

    /**
     * Google Translate'in desteklediği TÜM dillerin kataloğu (kod → Türkçe ad).
     * Hem kaynak ("Otomatik" ayrıca eklenir) hem hedef dil seçiminde kullanılır;
     * böylece çeviri tek bir dile kilitlenmez, tüm diller desteklenir.
     */
    val googleLanguageCatalog: List<Pair<String, String>> = listOf(
        "af" to "Afrikaanca", "sq" to "Arnavutça", "am" to "Amharca", "ar" to "Arapça",
        "hy" to "Ermenice", "as" to "Assamca", "ay" to "Aymaraca", "az" to "Azerbaycanca",
        "bm" to "Bambaraca", "eu" to "Baskça", "be" to "Belarusça", "bn" to "Bengalce",
        "bho" to "Bhojpuri", "bs" to "Boşnakça", "bg" to "Bulgarca", "ca" to "Katalanca",
        "ceb" to "Cebuano", "cs" to "Çekçe", "zh" to "Çince (Basitleştirilmiş)",
        "zh-TW" to "Çince (Geleneksel)", "cv" to "Çuvaşça", "da" to "Danca",
        "dv" to "Divehice", "doi" to "Dogri", "nl" to "Felemenkçe", "en" to "İngilizce",
        "eo" to "Esperanto", "et" to "Estonca", "ee" to "Ewe", "fil" to "Filipince",
        "fi" to "Fince", "fr" to "Fransızca", "fy" to "Frizce", "gl" to "Galiçyaca",
        "ka" to "Gürcüce", "de" to "Almanca", "el" to "Yunanca", "gn" to "Guaranice",
        "gu" to "Guceratça", "ht" to "Haiti Creole", "ha" to "Hausaca", "haw" to "Hawaiice",
        "iw" to "İbranice", "hi" to "Hintçe", "hmn" to "Hmong", "hu" to "Macarca",
        "is" to "İzlandaca", "ig" to "Igbo", "ilo" to "Ilocano", "id" to "Endonezce",
        "ga" to "İrlandaca", "it" to "İtalyanca", "ja" to "Japonca", "jv" to "Cavaca",
        "kn" to "Kannada", "kk" to "Kazakça", "km" to "Khmer", "rw" to "Kinyarwanda",
        "ko" to "Korece", "kri" to "Krio", "ku" to "Kürtçe (Kurmanç)", "ckb" to "Kürtçe (Sorani)",
        "ky" to "Kırgızca", "lo" to "Laoca", "la" to "Latince", "lv" to "Letonca",
        "ln" to "Lingala", "lt" to "Litvanca", "lg" to "Luganda", "lb" to "Lüksemburgca",
        "mk" to "Makedonca", "mai" to "Maithili", "mg" to "Malgaşça", "ms" to "Malayca",
        "ml" to "Malayalam", "mt" to "Maltaca", "mi" to "Maori", "mr" to "Marathi",
        "mni-Mtei" to "Meiteilon (Manipuri)", "lus" to "Mizo", "mn" to "Moğolca",
        "my" to "Myanmar (Burmaca)", "ne" to "Nepalce", "no" to "Norveççe",
        "ny" to "Nyanja (Chichewa)", "or" to "Odiya (Oriya)", "om" to "Oromo",
        "ps" to "Peştuca", "fa" to "Farsça", "pl" to "Lehçe", "pt" to "Portekizce",
        "pa" to "Pencapça", "qu" to "Keçuvaça", "ro" to "Rumence", "ru" to "Rusça",
        "sm" to "Samoaca", "sa" to "Sanskritçe", "gd" to "İskoç Galcesi",
        "nso" to "Sepedi", "sr" to "Sırpça", "st" to "Sesotho", "sn" to "Shona",
        "sd" to "Sintçe", "si" to "Seylanca", "sk" to "Slovakça", "sl" to "Slovence",
        "so" to "Somalice", "es" to "İspanyolca", "su" to "Sundaca", "sw" to "Svahili",
        "sv" to "İsveççe", "tl" to "Tagalog", "tg" to "Tacikçe", "ta" to "Tamilce",
        "tt" to "Tatarca", "te" to "Telugu", "th" to "Tayca", "ti" to "Tigrinya",
        "ts" to "Tsongaca", "tr" to "Türkçe", "tk" to "Türkmence", "ak" to "Twi (Akan)",
        "uk" to "Ukraynaca", "ur" to "Urduca", "ug" to "Uygurca", "uz" to "Özbekçe",
        "vi" to "Vietnamca", "cy" to "Galce", "xh" to "Xhosa", "yi" to "Yidiş",
        "yo" to "Yoruba", "zu" to "Zulu"
    )

    /**
     * Hedef dil kodunu (ör. "tr", "en") kullanıcıya gösterilecek dil adına çevirir
     * ("Türkçe", "İngilizce"). Katalogda yoksa kodun büyük harfli hâli döner.
     * "Türkçe" sekmelerinin etiketi bu fonksiyonla ayarlardaki hedef dile göre değişir.
     */
    fun targetLanguageName(code: String): String =
        googleLanguageCatalog.firstOrNull { it.first.equals(code, ignoreCase = true) }?.second
            ?: code.uppercase()

    /**
     * Tries to open the text in available translator apps.
     * If a preferredTranslator is specified (GOOGLE, DEEPL, TRANSLATE_YOU), attempts that app first.
     * If preferred fails or is DEFAULT, cycles through all apps in fallback order.
     * Shows a toast if no translator app is found.
     *
     * [sourceLanguage]: metnin yazıldığı dil (ör. incelemenin orijinal dili). Null ise
     * kullanıcı ayarı / otomatik algılama kullanılır — asla tek bir dile sabitlenmez.
     */
    fun Context.openTranslator(
        text: String,
        preferredTranslator: String = "DEFAULT",
        sourceLanguage: String? = null
    ) {
        val sourceLang = sourceLanguage?.takeIf { it.isNotBlank() }
            ?: KitsugiTranslatePrefs.sourceLanguage.ifBlank { "auto" }
        val targetLang = KitsugiTranslatePrefs.resolvedTarget()

        val handled = when (preferredTranslator.uppercase()) {
            "GOOGLE" -> openInGoogleTranslateMini(text) || openInGoogleTranslate(text, sourceLang, targetLang) ||
                openInGoogleTranslateWeb(text, sourceLang, targetLang)
            "DEEPL" -> openInDeepLMini(text) || openInDeepL(text)
            "TRANSLATE_YOU" -> openInTranslateYou(text)
            else -> false
        }

        if (!handled) {
            val fallbackSuccess = openInDeepLMini(text)
                || openInDeepL(text)
                || openInGoogleTranslateMini(text)
                || openInGoogleTranslate(text, sourceLang, targetLang)
                || openInGoogleTranslateWeb(text, sourceLang, targetLang)
                || openInTranslateYou(text)
            if (!fallbackSuccess) {
                Toast.makeText(this, "Çeviri uygulaması bulunamadı", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun Context.openInGoogleTranslate(text: String, sourceLang: String, targetLang: String): Boolean {
        return try {
            Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra("key_text_input", text)
                putExtra("key_text_output", "")
                // Kaynak dil SABİT DEĞİL: incelemenin kendi dili ya da otomatik algılama.
                putExtra("key_language_from", sourceLang.ifBlank { "auto" })
                putExtra("key_language_to", targetLang.ifBlank { "tr" })
                putExtra("key_suggest_translation", "")
                putExtra("key_from_floating_window", false)
                component = ComponentName(
                    "com.google.android.apps.translate",
                    "com.google.android.apps.translate.TranslateActivity"
                )
                startActivity(this)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Google Translate web fallback'u: uygulama kurulu olmasa bile tüm dilleri ve
     * otomatik kaynak algılamayı (sl=auto) destekler.
     */
    private fun Context.openInGoogleTranslateWeb(text: String, sourceLang: String, targetLang: String): Boolean {
        return try {
            val sl = sourceLang.ifBlank { "auto" }
            val tl = targetLang.ifBlank { "tr" }
            val encoded = Uri.encode(text.take(4500))
            val url = "https://translate.google.com/?sl=$sl&tl=$tl&text=$encoded&op=translate"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun Context.openInGoogleTranslateMini(text: String): Boolean {
        return try {
            Intent(Intent.ACTION_PROCESS_TEXT).apply {
                component = ComponentName(
                    "com.google.android.apps.translate",
                    "com.google.android.apps.translate.copydrop.gm3.TapToTranslateActivity"
                )
                type = "text/plain"
                putExtra(Intent.EXTRA_PROCESS_TEXT, text)
                startActivity(this)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun Context.openInDeepL(text: String): Boolean {
        return try {
            // DeepL doesn't support direct text injection, so we copy to clipboard first
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("synopsis", text))
            Intent(Intent.ACTION_VIEW).apply {
                component = ComponentName(
                    "com.deepl.mobiletranslator",
                    "com.deepl.mobiletranslator.MainActivity"
                )
                startActivity(this)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun Context.openInDeepLMini(text: String): Boolean {
        return try {
            Intent(Intent.ACTION_PROCESS_TEXT).apply {
                component = ComponentName(
                    "com.deepl.mobiletranslator",
                    "com.deepl.mobiletranslator.MiniTranslatorActivity"
                )
                type = "text/plain"
                putExtra(Intent.EXTRA_PROCESS_TEXT, text)
                startActivity(this)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun Context.openInTranslateYou(text: String): Boolean {
        return try {
            Intent(Intent.ACTION_PROCESS_TEXT).apply {
                component = ComponentName(
                    "com.bnyro.translate",
                    "com.bnyro.translate.ui.ShareActivity"
                )
                type = "text/plain"
                putExtra(Intent.EXTRA_PROCESS_TEXT, text)
                startActivity(this)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * İki metni "aynı içerik mi?" diye karşılaştırmak için normalize eder.
 */
fun normalizeTranslationText(text: String): String =
    text.lowercase().filter { it.isLetterOrDigit() }

/**
 * Çeviri, kaynak metnin kendisiyle aynı mı (yalnızca biçim farkı mı)?
 *
 * `true` dönerse sonuç **çeviri olarak gösterilmemelidir**: kullanıcıya aynı metni
 * "Çeviri" diye sunmak ve orijinal/çeviri geçişi açmak yanıltıcıdır.
 */
fun translationMatchesSource(source: String, translation: String): Boolean =
    normalizeTranslationText(source) == normalizeTranslationText(translation)

/**
 * Metnin büyük olasılıkla Türkçe olup olmadığını sezer.
 *
 * Bu yalnızca **hızlı çıkış** için bir ön kontroldür (gereksiz ağ isteğini önler); asıl
 * karar `TranslationManager` içinde Google'ın döndürdüğü algılanan kaynak dille verilir.
 *
 * Eski sürümdeki hata: metin içinde geçen tek bir yabancı kelime ("The Whale",
 * "All Too Well" gibi özel adlar) İngilizce sayılıyor ve **Türkçe biyografi yeniden
 * Türkçeye çevriliyordu**. Burada yabancı kelimeler kelime sayısına oranlanır; ayrıca
 * Türkçeye özgü harfler, Türkçe ekler ve yaygın Türkçe kelimeler puanlanır.
 */
fun isLikelyTurkishText(text: String): Boolean {
    if (text.isBlank()) return false

    var letterCount = 0
    var nonLatinCount = 0
    for (ch in text) {
        if (!ch.isLetter()) continue
        letterCount++
        val code = ch.code
        if (code in 0x0400..0x04FF || // Kiril
            code in 0x3040..0x30FF || // Hiragana / Katakana
            code in 0x3400..0x4DBF || // CJK Ext A
            code in 0x4E00..0x9FFF || // CJK Unified
            code in 0xAC00..0xD7AF || // Hangul
            code in 0x0590..0x05FF || // İbrani
            code in 0x0600..0x06FF    // Arapça
        ) {
            nonLatinCount++
        }
    }
    if (letterCount == 0) return false
    if (nonLatinCount > 0 && (nonLatinCount >= 3 || nonLatinCount * 10 >= letterCount)) {
        return false
    }

    // Kelimeler: kesme işareti Türkçede ek ayırıcıdır ("Netflix'in", "Stranger Things'teki");
    // onu sözcüğün parçası saymak yerine ayırıyoruz ki "things'teki" → "things" + "teki" olsun.
    val words = text.lowercase()
        .split(Regex("""[^\p{L}\p{N}]+"""))
        .filter { it.isNotBlank() }
    if (words.isEmpty()) return false

    val englishWords = setOf(
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "i", "it", "for",
        "not", "on", "with", "he", "as", "you", "do", "at", "this", "but", "his", "by",
        "from", "they", "we", "say", "her", "she", "or", "an", "will", "my", "one",
        "all", "would", "there", "their", "what", "so", "up", "out", "if", "about",
        "who", "get", "which", "go", "me", "when", "make", "can", "like", "time",
        "no", "just", "him", "know", "take", "into", "year", "your", "good", "some",
        "could", "them", "see", "other", "than", "then", "now", "only", "its", "over",
        "also", "after", "two", "how", "work", "first", "even", "new", "because",
        "was", "were", "is", "are", "has", "had", "been", "through", "during", "while"
    )
    val turkishWords = setOf(
        "bir", "için", "olan", "sonra", "ancak", "olarak", "tarafından", "nedeniyle",
        "birlikte", "bulunan", "olduğu", "göre", "kendi", "çünkü", "böylece", "arasında",
        "yapılan", "yılında", "bölüm", "karakter", "karakteri", "biyografi", "hakkında",
        "seslendiren", "sanatçı", "sanatçısı", "doğdu", "başladı", "aldı", "ilgili",
        "üzerine", "karşı", "kadar", "daha", "çok", "ise", "ile", "ve", "bu", "da", "de",
        "gibi", "her", "hem", "ya", "veya", "aynı", "zaman", "yıl", "film", "dizi",
        "oyuncu", "oyuncusu", "oyuncusudur", "yönetmen", "yapım", "sahip", "adlı"
    )

    val englishCount = words.count { it in englishWords }
    val turkishCount = words.count { it in turkishWords }

    // Türkçeye özgü harfler (ç/ö/ü ortak olsa da ğ/ı/ş/İ Türkçeye işaret eder).
    val distinctChars = setOf('ğ', 'Ğ', 'ş', 'Ş', 'ı', 'İ')
    val distinctCount = text.count { it in distinctChars }
    // Türkçede çok sık görülen ekler — kelime sonu.
    val turkishSuffixes = listOf(
        "dır", "dir", "dur", "dür", "tır", "tir", "tur", "tür",
        "mış", "miş", "muş", "müş", "lar", "ler", "dan", "den", "tan", "ten",
        "nın", "nin", "nun", "nün", "yla", "yle", "sı", "si", "su", "sü"
    )
    val suffixCount = words.count { w -> w.length >= 4 && turkishSuffixes.any { w.endsWith(it) } }

    // Yabancı kelime **oranı** önemlidir: Türkçe bir metinde geçen 1-2 özel ad
    // ("The Whale", "Stranger Things") metni İngilizce yapmaz.
    val englishRatio = englishCount.toDouble() / words.size
    if (englishRatio >= 0.28 && englishCount >= 3) return false
    if (englishCount >= 2 && turkishCount == 0 && suffixCount == 0 && distinctCount == 0) return false

    val turkishScore = turkishCount + suffixCount + (distinctCount / 2)
    if (turkishScore >= 2) return true
    if (distinctCount >= 4 && englishRatio < 0.2) return true
    // Kısa metinlerde (tek cümle) tek güçlü işaret yeterli.
    if (words.size <= 12 && (turkishCount >= 1 || suffixCount >= 1) && englishRatio < 0.2) return true
    return turkishScore > englishCount
}
