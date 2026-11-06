package com.kitsugi.animelist.data.manga

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.data.local.TranslationManager
import com.kitsugi.animelist.data.remote.KitsugiApiBase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.Locale

/**
 * Manga Keşfet aramasının "hangi adla arayacağım" sözleşmesi.
 *
 * Eklentiler (Manga-TR, Mangitto, MangaciX…) kendi dillerinde — çoğunlukla Türkçe —
 * başlık tutar. Kullanıcı ise sıklıkla İngilizce/Romaji/Korece adla gelir
 * (ör. "The Greatest Estate Developer" / "역대급 영지 설계사"). Tek bir adla arama
 * yapıldığında eklenti tarafı boş dönüyordu. Bu plan, aynı eserin bilinen tüm adlarını
 * öncelik sırasına koyar ve kaynak başına sırayla denenmelerini sağlar.
 */
enum class MangaSearchTitleMode {
    /** Eklentilerin diline uygun yerel/çevrilmiş ad önce denenir. */
    LOCALIZED,

    /** Eserin orijinal (Japonca/Çince/Korece/Romaji) adı önce denenir. */
    ORIGINAL,
}

/**
 * Bir arama için çözümlenmiş başlık planı.
 *
 * [variants] kaynaklara **sırayla** gönderilecek adlardır (öncelik sırası korunur).
 * [matchTitles] ise gelen sonuçları elemede kullanılan kabul listesidir; böylece
 * "Yerel ad" ile aranıp "Orijinal ad"la dönen kartlar da hak ettiği yerde kalır.
 */
data class MangaSearchPlan(
    val query: String,
    val mode: MangaSearchTitleMode,
    val variants: List<String>,
    val localizedTitles: List<String>,
    val originalTitles: List<String>,
) {
    /** Sonuç elemede kabul edilen tüm adlar (sorgu + denenen varyantlar). */
    val matchTitles: List<String>
        get() = (listOf(query) + variants).map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase(Locale.ROOT) }

    /** Başlık çözümlemesi başarısız olsa bile en az sorgunun kendisi aranır. */
    fun safeVariants(): List<String> = variants.ifEmpty { listOf(query) }.filter { it.isNotBlank() }

    /** Aynı ada farklı imla ile denk gelen adları teke indirger. */
    fun trimmedForDisplay(maxLength: Int = 42): String {
        val first = if (mode == MangaSearchTitleMode.LOCALIZED) {
            localizedTitles.firstOrNull() ?: query
        } else {
            originalTitles.firstOrNull() ?: query
        }
        return if (first.length <= maxLength) first else first.take(maxLength - 1).trimEnd() + "…"
    }

    companion object {
        fun identity(query: String, mode: MangaSearchTitleMode): MangaSearchPlan =
            MangaSearchPlan(
                query = query,
                mode = mode,
                variants = listOf(query).filter { it.isNotBlank() },
                localizedTitles = emptyList(),
                originalTitles = emptyList(),
            )
    }
}

/**
 * Başlık listesinden aranabilir varyantları üretir. Ağ/çeviri içermez; bu yüzden
 * birim testleriyle doğrulanabilir.
 */
object MangaTitleVariantBuilder {

    /** Kaynak başına denenecek en fazla ad sayısı (ağ maliyeti sınırı). */
    const val MAX_VARIANTS = 5

    fun build(
        query: String,
        mode: MangaSearchTitleMode,
        originalTitles: List<String> = emptyList(),
        localizedTitles: List<String> = emptyList(),
    ): MangaSearchPlan {
        val cleanedQuery = query.trim()
        val originals = dedupe(originalTitles)
        val localized = dedupe(localizedTitles)
        // Noktalama/stop-word temizlenmiş çekirdek her zaman EN SON çare olarak denenir.
        val fallbacks = dedupe(canonicalFallbacks(cleanedQuery))

        // Sıra moda göre belirlenir: "Yerel ad" modunda çevrilmiş ad İLK sırada olmalı,
        // yoksa buton hiçbir şey yapmıyor gibi görünür (önce sorgu denenip boş dönüyordu).
        // Arındırılmış çekirdek her zaman sorgunun hemen ardından, karşı modun adlarından
        // ÖNCE denenir; böylece "Orijinal ad" modunda yerel ad gerçekten en sonda kalır.
        val ordered = when (mode) {
            MangaSearchTitleMode.LOCALIZED ->
                localized + listOf(cleanedQuery) + fallbacks + originals
            MangaSearchTitleMode.ORIGINAL ->
                originals + listOf(cleanedQuery) + fallbacks + localized
        }

        val variants = dedupe(ordered)
            .filter { it.length >= 2 }
            .take(MAX_VARIANTS)

        return MangaSearchPlan(
            query = cleanedQuery,
            mode = mode,
            variants = variants.ifEmpty { listOf(cleanedQuery) },
            localizedTitles = localized,
            originalTitles = originals,
        )
    }

    /**
     * Sorgunun noktalama/stop-word'lerden arındırılmış çekirdeği. Kaynak siteler
     * "The Greatest Estate Developer" yerine "Greatest Estate Developer" tutabildiği
     * için son çare olarak denenir.
     */
    fun canonicalFallbacks(query: String): List<String> {
        if (query.isBlank()) return emptyList()
        val canonical = runCatching { CanonicalMangaResolver.resolve(query) }.getOrNull() ?: return emptyList()
        return listOf(canonical.core, canonical.ascii)
            .map { it.trim() }
            .filter { it.length >= 3 && !it.equals(query.trim(), ignoreCase = true) }
            .distinct()
    }

    /**
     * Aynı adı (büyük/küçük harf, noktalama, Türkçe karakter farkıyla) tekilleştirir.
     * Sıralama korunur: ilk gören kazanır.
     */
    fun dedupe(titles: List<String>): List<String> {
        val seen = LinkedHashMap<String, String>()
        titles.forEach { raw ->
            val title = raw.trim().replace(Regex("\\s+"), " ")
            if (title.length < 2) return@forEach
            val key = runCatching { CanonicalMangaResolver.resolve(title).compact }.getOrNull()
                .orEmpty()
                .ifBlank { title.lowercase(Locale.ROOT) }
            if (!seen.containsKey(key)) seen[key] = title
        }
        return seen.values.toList()
    }
}

/**
 * Eserin diğer adlarını (Romaji / İngilizce / Orijinal + çevrilmiş ad) çözer.
 *
 * - AniList GraphQL'den aday eserin `title` alanları ve `synonyms` listesi alınır.
 * - Yerel/çevrilmiş ad için uygulamanın çeviri hattı (Google) kullanılır.
 * - Sonuçlar 30 dakika bellekte tutulur; aynı sorgu tekrar ağa çıkmaz.
 *
 * Ağ veya çeviri başarısız olursa **asla arama engellenmez**: sorgunun kendisi
 * tek varyant olarak döner.
 */
object MangaTitleVariantResolver {

    private const val TAG = "MangaTitleVariants"
    private const val CACHE_TTL_MS = 30 * 60 * 1000L
    private const val MAX_CACHE_ENTRIES = 64
    private const val ANILIST_TIMEOUT_MS = 7_000L
    private const val TRANSLATE_TIMEOUT_MS = 7_000L
    private const val MAX_TRANSLATIONS = 3

    private data class CacheEntry(val plan: MangaSearchPlan, val createdAt: Long)

    private val cache = object : LinkedHashMap<String, CacheEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean =
            size > MAX_CACHE_ENTRIES
    }

    private val lock = Any()

    suspend fun planFor(
        context: Context,
        query: String,
        mode: MangaSearchTitleMode,
        /**
         * Eklentilerin baskın dili (ör. yüklü manga kaynaklarının çoğu `"tr"` ise `"tr"`).
         * "Yerel ad" modunda çeviri bu dile yapılır; böylece İngilizce ad yerine
         * eklentilerin gerçekten indekslediği Türkçe ad aranır.
         */
        extensionLanguage: String? = null,
    ): MangaSearchPlan {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return MangaSearchPlan.identity("", mode)

        val target = extensionLanguage?.takeIf { it.isNotBlank() }
            ?: com.kitsugi.animelist.utils.KitsugiTranslatePrefs.resolvedTarget()
        val key = "${mode.name}|${target.lowercase(Locale.ROOT)}|${trimmed.lowercase(Locale.ROOT)}"
        val now = System.currentTimeMillis()
        synchronized(lock) {
            cache[key]?.takeIf { now - it.createdAt < CACHE_TTL_MS }?.let { return it.plan }
        }

        val plan = runCatching { resolve(context, trimmed, mode, target) }
            .getOrElse { error ->
                Log.w(TAG, "Başlık çözümlemesi başarısız (${trimmed}): ${error.message}")
                MangaSearchPlan.identity(trimmed, mode)
            }

        synchronized(lock) { cache[key] = CacheEntry(plan, now) }
        return plan
    }

    /** Önbelleği temizler (yeni eklenti kurulumu / dil değişimi sonrası). */
    fun clearCache() {
        synchronized(lock) { cache.clear() }
    }

    private suspend fun resolve(
        context: Context,
        query: String,
        mode: MangaSearchTitleMode,
        extensionLanguage: String,
    ): MangaSearchPlan {
        val appContext = context.applicationContext
        val aniList = fetchAniListTitles(query)

        val targetLanguage = extensionLanguage.ifBlank {
            com.kitsugi.animelist.utils.KitsugiTranslatePrefs.resolvedTarget()
        }

        val originals = LinkedHashSet<String>()
        aniList.native?.let { originals += it }
        aniList.romaji?.let { originals += it }
        aniList.english?.let { originals += it }

        val localized = LinkedHashSet<String>()
        val translationTargets = LinkedHashSet<String>()

        // AniList eşanlamlıları arasında hazır yerel ad varsa (ör. "Dahi Malikane
        // Geliştiricisi") çeviri ağına hiç çıkmadan kullanılır: çeviri servisi
        // kapalı/yavaş olsa bile "Yerel ad" modu Türkçe adla arama yapabilir.
        aniList.synonyms.forEach { synonym ->
            if (looksLocalizedAlready(synonym, targetLanguage) && !isNonLatin(synonym)) {
                localized += synonym
            } else {
                originals += synonym
            }
        }

        when {
            // Sorgu zaten Latin dışı (Japonca/Çince/Korece/Rusça): orijinaldir, çevirisi
            // eklentilerin diline denk gelir.
            isNonLatin(query) -> {
                originals.add(query)
                translationTargets += query
            }
            // Sorgu Türkçe görünüyor: orijinal adı AniList'ten, çevirisini gerekmez.
            looksLocalizedAlready(query, targetLanguage) -> {
                localized += query
                (aniList.english ?: aniList.romaji)?.let { translationTargets += it }
            }
            else -> {
                // İngilizce/Romaji sorgu: asıl yerel ad çeviriden gelir.
                (aniList.english ?: aniList.romaji ?: query).let { translationTargets += it }
                aniList.native?.takeIf { isNonLatin(it) }?.let { translationTargets += it }
            }
        }

        if (translationTargets.isEmpty()) {
            translationTargets += query
        }

        var translations = 0
        for (candidate in translationTargets.toList()) {
            if (translations >= MAX_TRANSLATIONS) break
            val translated = translate(appContext, candidate, targetLanguage)
            if (translated != null) {
                localized += translated
                translations++
            }
        }

        // Orijinal ad hiç bulunamadıysa ve sorgu yerel görünüyorsa İngilizce karşılığını dene.
        if (originals.isEmpty() && mode == MangaSearchTitleMode.ORIGINAL) {
            translateTo(appContext, query, "en")?.let { originals += it }
        }

        Log.d(
            TAG,
            "Başlık planı hazır: query='$query' mode=${mode.name} " +
                "orijinal=${originals.size} yerel=${localized.size}"
        )

        return MangaTitleVariantBuilder.build(
            query = query,
            mode = mode,
            originalTitles = originals.toList(),
            localizedTitles = localized.toList(),
        )
    }

    private class AniListTitles(
        val romaji: String?,
        val english: String?,
        val native: String?,
        val synonyms: List<String>,
    )

    private suspend fun fetchAniListTitles(query: String): AniListTitles = withContext(Dispatchers.IO) {
        val gql = """
            query (${'$'}search: String, ${'$'}perPage: Int) {
                Page(page: 1, perPage: ${'$'}perPage) {
                    media(search: ${'$'}search, type: MANGA, sort: [SEARCH_MATCH]) {
                        title {
                            romaji
                            english
                            native
                        }
                        synonyms
                    }
                }
            }
        """.trimIndent()

        val body = runCatching {
            withTimeoutOrNull(ANILIST_TIMEOUT_MS) {
                KitsugiApiBase.executeAniListQuery(
                    query = gql,
                    variables = JSONObject().put("search", query).put("perPage", 6)
                )
            }
        }.getOrNull() ?: return@withContext AniListTitles(null, null, null, emptyList())

        val media = runCatching {
            JSONObject(body)
                .optJSONObject("data")
                ?.optJSONObject("Page")
                ?.optJSONArray("media")
        }.getOrNull() ?: return@withContext AniListTitles(null, null, null, emptyList())

        val first = media.optJSONObject(0) ?: return@withContext AniListTitles(null, null, null, emptyList())
        val titleObject = first.optJSONObject("title")

        val synonyms = mutableListOf<String>()
        val synonymArray = first.optJSONArray("synonyms")
        if (synonymArray != null) {
            for (index in 0 until synonymArray.length()) {
                synonymArray.optString(index).takeIf { it.isNotBlank() }?.let { synonyms += it }
            }
        }

        AniListTitles(
            romaji = titleObject?.optStringOrNull("romaji"),
            english = titleObject?.optStringOrNull("english"),
            native = titleObject?.optStringOrNull("native"),
            synonyms = synonyms.take(3),
        )
    }

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotBlank() && it != "null" }

    private suspend fun translate(context: Context, text: String, target: String): String? =
        translateTo(context, text, target)

    private suspend fun translateTo(context: Context, text: String, target: String): String? {
        if (text.isBlank() || target.isBlank()) return null
        return runCatching {
            withTimeoutOrNull(TRANSLATE_TIMEOUT_MS) {
                TranslationManager(context).translateTo(text, target)
            }
        }.getOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals(text.trim(), ignoreCase = true) }
    }

    /** CJK / Hangul / Kiril içeren metin Latin dışıdır. */
    fun isNonLatin(text: String): Boolean = text.any { ch ->
        val code = ch.code
        code in 0x3040..0x30FF ||   // Hiragana / Katakana
            code in 0x3400..0x4DBF || // CJK uzantı A
            code in 0x4E00..0x9FFF || // CJK birleşik
            code in 0xAC00..0xD7AF || // Hangul
            code in 0xF900..0xFAFF || // CJK uyumluluk
            code in 0x0400..0x04FF    // Kiril
    }

    /** Metin zaten hedef dilde (Türkçe) görünüyor mu? */
    fun looksLocalizedAlready(text: String, targetLanguage: String): Boolean {
        if (isNonLatin(text)) return false
        if (!targetLanguage.startsWith("tr", ignoreCase = true)) return false
        val turkishOnly = text.any { it in "ğĞışŞİ" }
        if (turkishOnly) return true
        // Türkçede çok geçen ama İngilizcede bulunmayan kelimeler: kaba ama etkili ipucu.
        // (Yalnızca tam kelime eşleşmesi; "son" gibi İngilizcede de geçenler listeye girmez.)
        val words = text.lowercase(Locale.ROOT).split(Regex("[^a-zçğıöşü]+")).filter { it.length >= 2 }
        val turkishHints = setOf(
            "ve", "ile", "için", "gibi", "çok", "yok", "değil", "ama", "dünyanın", "kralı",
            "efendisi", "geliştiricisi", "malikane", "yüce", "tanrı", "kılıç", "okulu",
            "hayat", "aşk", "gizem", "savaş", "güç", "yolculuk", "dönüş", "kader",
            "kız", "oğlan", "hoca", "dahi", "iyileştirici", "mucize", "üniversite"
        )
        return words.count { it in turkishHints } >= 1 && words.size <= 8
    }
}
