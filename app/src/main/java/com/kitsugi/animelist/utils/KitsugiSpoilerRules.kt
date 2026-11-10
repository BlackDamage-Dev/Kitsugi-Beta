package com.kitsugi.animelist.utils

import com.kitsugi.animelist.data.remote.KitsugiTag
import com.kitsugi.animelist.ui.screens.search.CanonicalFilterBridge
import com.kitsugi.animelist.ui.screens.search.SearchTranslation
import java.util.Locale

/**
 * Tüm desteklenen kaynaklar (AniList, Bangumi, MyAnimeList/Jikan, Shikimori, Kitsu,
 * Simkl, TMDB, TVMaze, Trakt) için ortak etiket (Tags) ve tür (Genres) spoiler kuralları.
 *
 * AniList'teki iki katmanlı spoiler mantığını tüm kaynaklara uygular:
 *  1. Genel / Doğal Spoiler (General Spoiler): Hangi yapımda geçerse geçsin hikâye sonunu,
 *     karakter ölümünü, ihanet/NTR veya kritik ters köşeyi açık eden etiketler/türler.
 *  2. Yapıma Özel Spoiler (Media Spoiler): AniList'ten gelen `isMediaSpoiler` / `isGeneralSpoiler`
 *     bayraklarının çapraz dil/kaynak eşleştirmesiyle diğer kaynakların etiket ve türlerine
 *     aktarılması.
 */
object KitsugiSpoilerRules {

    /**
     * Asla spoiler olarak gizlenmeyecek temel üst-seviye tür anahtarları.
     * Böylece Aksiyon, Komedi, Dram, Romantik, Fantezi gibi ana türler hiçbir zaman
     * yanlışlıkla gizlenmez; yalnızca alt temalar ve spoiler etiketleri denetlenir.
     */
    private val SAFE_PRIMARY_GENRE_KEYS: Set<String> = setOf(
        "action", "adventure", "comedy", "drama", "ecchi", "fantasy", "hentai",
        "horror", "magical_girl", "mahou shoujo", "mecha", "music", "mystery",
        "romance", "sci_fi", "sci-fi", "slice_of_life", "slice of life", "sports",
        "supernatural", "thriller", "suspense", "shounen", "shoujo", "seinen",
        "josei", "kids", "tv", "movie", "ova", "ona", "special", "web",
        "aksiyon", "macera", "komedi", "dram", "fantastik", "korku", "gizem",
        "romantik", "bilim kurgu", "günlük yaşam", "yaşamdan kesitler", "spor",
        "doğaüstü", "gerilim", "müzik", "sihirli kız", "çocuklar", "çocuklara yönelik"
    )

    /**
     * `BangumiTagDictionary` içindeki kanonik grup anahtarlarından doğal spoiler olanlar.
     */
    private val INTRINSIC_SPOILER_DICT_KEYS: Set<String> = setOf(
        "death",
        "suicide",
        "betrayal",
        "ntr",
        "plot_twist",
        "bad_ending",
        "open_ending",
        "happy_ending",
        "sacrifice",
        "genocide",
        "war_crimes",
        "yandere",
        "mind_bending",
        "depressing",
        "tragedy",
        "time_loop"
    )

    /**
     * Çok dilli (İngilizce, Türkçe, Çince, Japonca, Rusça) doğal spoiler etiket/tema kümesi.
     * Küçük harfe çevrilmiş ve normalize edilmiş biçimleriyle eşleştirilir.
     */
    private val INTRINSIC_SPOILER_LABELS: Set<String> = setOf(
        // Doğrudan spoiler belirteçleri
        "spoiler", "spoilers", "spoyler", "剧透", "ネタバレ", "спойлер", "спойлеры",

        // Ölüm / Trajedi / Son (Ending) ifşaları
        "death", "character death", "main character death", "main character dies",
        "death of main character", "everyone dies", "total party kill",
        "ölüm", "karakter ölümü", "ana karakter ölümü", "herkes ölür",
        "死亡", "便当", "发便当", "全灭", "团灭", "死別", "角色死亡",
        "tragedy", "trajedi", "悲剧", "悲劇", "трагедия",
        "bad end", "bad ending", "kötü son", "坏结局", "バッドエンド",
        "happy ending", "mutlu son", "好结局", "ハッピーエンド",
        "open ending", "açık son", "开放式结局", "オープンエンド",
        "bittersweet ending", "acı tatlı son",
        "twist ending", "surprise ending", "sürpriz son",
        "depressing", "downer", "kederli", "karamsar", "ağır dram",
        "致郁", "致郁系", "胃痛", "郁系", "鬱展開", "鬱アニメ", "鬱ゲー",
        "terminal illness", "incurable illness", "ölümcül hastalık", "çaresiz hastalık",
        "绝症", "不治之症", "余命",
        "resurrection", "diriliş", "yeniden dirilme", "复活", "復活",
        "fake death", "sahte ölüm", "假死",
        "sacrifice", "self-sacrifice", "kurban", "fedakarlık", "kendini feda etme", "牺牲", "犠牲",

        // İntihar / Kendine zarar verme
        "suicide", "intihar", "自杀", "自殺", "心中", "суицид",

        // İhanet / NTR / Yozlaşma
        "betrayal", "ihanet", "背叛", "裏切り", "предательство",
        "ntr", "netorare", "cuckold", "aldatma", "aldatma (ntr)", "sadakatsizlik",
        "寝取られ", "牛头人", "绿帽", "ネトラレ",
        "corruption", "moral degeneration", "yozlaşma", "karanlığa kayma",
        "黑化", "堕落", "闇堕ち", "恶堕", "悪堕ち",

        // Ters köşe / Anlatı hilesi / Zaman döngüsü / Hafıza / Kimlik ifşaları
        "plot twist", "sürpriz gelişme", "ters köşe",
        "神展开", "超展开", "反转", "叙事诡计", "叙述性诡计", "どんでん返し",
        "unreliable narrator", "güvenilmez anlatıcı",
        "mind screw", "mind bending", "kafa karıştıran", "烧脑",
        "time loop", "zaman döngüsü", "轮回", "时间循环", "タイムループ", "ループ",
        "amnesia", "memory loss", "hafıza kaybı", "失忆", "記憶喪失", "амнезия",
        "memory manipulation", "hafıza manipülasyonu", "记忆篡改", "記憶操作", "记忆操作",
        "dissociative identities", "split personality", "multiple personalities",
        "çoklu kişilik", "kişilik bölünmesi", "多重人格", "双重人格", "二重人格",
        "secret identity", "hidden identity", "true identity", "gizli kimlik", "gerçek kimlik",
        "false flag", "impostor", "sahtekar",

        // Karanlık / Hassas olay örgüsü sürprizleri
        "yandere", "病娇", "ヤンデレ", "яндере",
        "cannibalism", "yamyamlık", "食人", "カニバリズム",
        "rape", "sexual assault", "non-con", "tecavüz", "cinsel saldırı",
        "强奸", "强暴", "凌辱", "レイプ", "強姦",
        "incest", "ensest", "乱伦", "近親相姦", "骨科", "实妹", "実妹",
        "torture", "işkence", "拷问", "拷問", "虐杀", "断肢", "猎奇",
        "brainwashing", "mind control", "mind manipulation", "hypnosis",
        "beyin yıkama", "zihin kontrolü", "zihin manipülasyonu", "hipnoz",
        "洗脑", "催眠", "调教", "精神控制", "洗脳",
        "stockholm syndrome", "stockholm sendromu", "斯德哥尔摩", "监禁", "監禁",
        "genocide", "massacre", "soykırım", "katliam", "大屠杀", "虐杀", "大量虐殺",
        "war crimes", "savaş suçu", "savaş suçları", "战争罪",
        "matricide", "patricide", "fratricide", "filicide", "familicide",
        "anne katili", "baba katili", "kardeş katili", "aile katliamı",
        "faustian pact", "deal with the devil", "şeytanla anlaşma"
    )

    private val WRAPPER_REGEX = Regex("[【】（）()\\[\\]「」『』《》〈〉\"'`´]+")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val canonicalKeyMemo = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val intrinsicSpoilerMemo = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /**
     * Etiket veya tür metnini karşılaştırma için normalize eder.
     */
    fun normalizeLabel(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw
            .replace('İ', 'i')
            .replace('I', 'ı')
            .replace(WRAPPER_REGEX, " ")
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(WHITESPACE_REGEX, " ")
            .trim()
            .lowercase(Locale.ROOT)
            .replace("\u0307", "")
    }

    /**
     * Farklı kaynak ve dillerden (Çince/Japonca/İngilizce/Türkçe) gelen bir etiket veya
     * tür adını ortak kanonik anahtara indirger.
     */
    fun canonicalTagKey(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""
        val cacheKey = "${java.util.Locale.getDefault().language}:$trimmed"
        canonicalKeyMemo[cacheKey]?.let { return it }

        val computed = run {
            // 1) BangumiTagDictionary kanonik anahtarı (CJK + EN + TR destekler)
            BangumiTagDictionary.entryFor(trimmed)?.let { return@run it.key }

            // 1b) Rusça (Shikimori) sözlük anahtarı — `Экшен` ≡ `Action` ≡ `Aksiyon`
            RussianTaxonomyDictionary.entryFor(trimmed)?.let { return@run it.key }

            // 2) SearchTranslation üzerinden İngilizce karşılığı
            val en = SearchTranslation.translateToEnglishForSearch(trimmed).trim()
            if (en.isNotEmpty() && !en.equals(trimmed, ignoreCase = true)) {
                BangumiTagDictionary.entryFor(en)?.let { return@run it.key }
                return@run normalizeLabel(en)
            }

            // 3) Yerelleştirilmiş etiket üzerinden sözlük eşlemesi
            val localized = trimmed.toLocalizedTagLabel().trim()
            if (localized.isNotEmpty() && !localized.equals(trimmed, ignoreCase = true)) {
                BangumiTagDictionary.entryFor(localized)?.let { return@run it.key }
            }

            normalizeLabel(trimmed)
        }
        if (canonicalKeyMemo.size < 8192) canonicalKeyMemo[cacheKey] = computed
        return computed
    }

    /**
     * Bir etiket veya tür adının tek başına (doğal olarak) spoiler olup olmadığını döner.
     */
    fun isIntrinsicSpoiler(label: String?): Boolean {
        val trimmed = label?.trim().orEmpty()
        if (trimmed.isEmpty()) return false
        intrinsicSpoilerMemo[trimmed]?.let { return it }

        val computed = computeIntrinsicSpoiler(trimmed)
        if (intrinsicSpoilerMemo.size < 8192) intrinsicSpoilerMemo[trimmed] = computed
        return computed
    }

    private fun computeIntrinsicSpoiler(trimmed: String): Boolean {
        val normalized = normalizeLabel(trimmed)
        if (normalized in SAFE_PRIMARY_GENRE_KEYS) return false
        if (normalized in INTRINSIC_SPOILER_LABELS) return true

        // Sözlük girişi üzerinden kontrol
        val dictEntry = BangumiTagDictionary.entryFor(trimmed)
        if (dictEntry != null) {
            if (dictEntry.key in SAFE_PRIMARY_GENRE_KEYS) return false
            if (dictEntry.key in INTRINSIC_SPOILER_DICT_KEYS) return true
            if (normalizeLabel(dictEntry.english) in INTRINSIC_SPOILER_LABELS) return true
            if (normalizeLabel(dictEntry.turkish) in INTRINSIC_SPOILER_LABELS) return true
        }

        // SearchTranslation İngilizce / Türkçe karşılıkları üzerinden kontrol
        val en = SearchTranslation.translateToEnglishForSearch(trimmed)
        val normEn = normalizeLabel(en)
        if (normEn in SAFE_PRIMARY_GENRE_KEYS) return false
        if (normEn in INTRINSIC_SPOILER_LABELS) return true
        BangumiTagDictionary.entryFor(en)?.let { entry ->
            if (entry.key in INTRINSIC_SPOILER_DICT_KEYS) return true
        }

        val tr = SearchTranslation.translateToTurkishForDisplay(trimmed)
        val normTr = normalizeLabel(tr)
        if (normTr in INTRINSIC_SPOILER_LABELS) return true

        return false
    }

    /**
     * "Türler" (Genres) bölümündeki bir öğenin spoiler olup olmadığını belirler.
     *
     * Temel ana türler (Aksiyon, Komedi, Dram, Romantik vb.) hiçbir zaman gizlenmez.
     * Ancak MAL/Jikan, Bangumi, Shikimori, Kitsu gibi kaynakların `genres` listesine
     * eklediği alt temalar (ör. Tragedy/Trajedi, Time Loop/Zaman Döngüsü, Time Travel,
     * Reincarnation, Survival, Zombies vb.):
     *  - Doğal spoiler ise, VEYA
     *  - Bu yapım için AniList/kaynak etiketlerinde (`mediaTags`) spoiler olarak işaretlenmişse
     * spoiler kabul edilir ve "Türler" kartında da tıpkı "Etiketler" kartındaki gibi
     * "Spoiler'ları Göster / Gizle" kontrolüyle gösterilir.
     */
    fun isGenreSpoiler(genre: String?, mediaTags: List<KitsugiTag> = emptyList()): Boolean {
        val trimmed = genre?.trim().orEmpty()
        if (trimmed.isEmpty()) return false

        val norm = normalizeLabel(trimmed)
        if (norm in SAFE_PRIMARY_GENRE_KEYS) return false
        if (CanonicalFilterBridge.isOfficialAniListGenre(trimmed)) return false

        val genreKey = canonicalTagKey(trimmed)
        if (genreKey in SAFE_PRIMARY_GENRE_KEYS) return false

        if (isIntrinsicSpoiler(trimmed)) return true

        if (mediaTags.isEmpty()) return false

        val genreLocalized = normalizeLabel(trimmed.toTurkishGenre())
        return mediaTags.any { tag ->
            val tagIsSpoiler = tag.isSpoiler || isIntrinsicSpoiler(tag.name)
            if (!tagIsSpoiler) return@any false
            val tagKey = canonicalTagKey(tag.name)
            val tagLocalized = normalizeLabel(tag.name.toLocalizedTagLabel())
            (genreKey.isNotEmpty() && genreKey == tagKey) ||
                (genreLocalized.isNotEmpty() && genreLocalized == tagLocalized) ||
                norm == normalizeLabel(tag.name)
        }
    }

    /**
     * Listedeki tüm etiketlere doğal spoiler kurallarını uygular (`isSpoiler = true` korur).
     */
    fun applySpoilerFlags(tags: List<KitsugiTag>): List<KitsugiTag> {
        if (tags.isEmpty()) return tags
        return tags.map { tag ->
            if (tag.isSpoiler || !isIntrinsicSpoiler(tag.name)) {
                tag
            } else {
                tag.copy(isSpoiler = true)
            }
        }
    }

    /**
     * Etiket listesini gösterim etiketine ve kanonik anahtara göre tekilleştirir;
     * aynı etiketin farklı kopyalarından HERHANGİ BİRİ spoiler ise `isSpoiler = true`
     * korunur ve varsa yüzdelik `rank` bilgisi saklanır.
     */
    fun normalizeAndMergeTags(tags: List<KitsugiTag>): List<KitsugiTag> {
        if (tags.isEmpty()) return emptyList()
        val result = ArrayList<KitsugiTag>(tags.size)
        val indexByKey = HashMap<String, Int>(tags.size * 2)

        for (rawTag in tags) {
            val trimmedName = rawTag.name.trim()
            if (trimmedName.isEmpty()) continue
            val tag = if (rawTag.isSpoiler || !isIntrinsicSpoiler(trimmedName)) {
                rawTag
            } else {
                rawTag.copy(isSpoiler = true)
            }

            val localizedKey = normalizeLabel(trimmedName.toLocalizedTagLabel())
            val canonicalKey = canonicalTagKey(trimmedName)

            val existingIdx = (if (localizedKey.isNotEmpty()) indexByKey[localizedKey] else null)
                ?: (if (canonicalKey.isNotEmpty()) indexByKey[canonicalKey] else null)

            if (existingIdx == null) {
                val newIdx = result.size
                result.add(tag)
                if (localizedKey.isNotEmpty()) indexByKey[localizedKey] = newIdx
                if (canonicalKey.isNotEmpty()) indexByKey[canonicalKey] = newIdx
            } else {
                val existing = result[existingIdx]
                val mergedRank = when {
                    existing.rank != null && tag.rank != null -> maxOf(existing.rank, tag.rank)
                    else -> existing.rank ?: tag.rank
                }
                result[existingIdx] = existing.copy(
                    rank = mergedRank,
                    isSpoiler = existing.isSpoiler || tag.isSpoiler,
                    id = existing.id ?: tag.id,
                    description = existing.description ?: tag.description,
                    category = existing.category ?: tag.category
                )
            }
        }
        return result
    }

    /**
     * Birincil kaynağın etiketleriyle (`primary`) ikincil/zenginleştirme kaynağının
     * etiketlerini (`secondary`, ör. AniList) birleştirir.
     *
     * - Birincil kaynağın özgün etiket adı ve `source` alanı korunur (ör. Bangumi'de çipe
     *   tıklanınca arama Bangumi'nin kendi etiketiyle yapılır).
     * - Eşleşen etiketlerde AniList'in `isSpoiler` ve `rank` bilgileri birincil etikete aktarılır.
     * - `limit` uygulanırken spoiler etiketlerinin kesilip kaybolmaması garanti edilir.
     */
    fun mergeTagsPreservingSpoilers(
        primary: List<KitsugiTag>,
        secondary: List<KitsugiTag>,
        limit: Int = 48
    ): List<KitsugiTag> {
        if (primary.isEmpty() && secondary.isEmpty()) return emptyList()
        val combined = normalizeAndMergeTags(primary + secondary)
        if (combined.size <= limit) return combined

        val spoilers = combined.filter { it.isSpoiler }
        val nonSpoilers = combined.filter { !it.isSpoiler }
        val nonSpoilerBudget = (limit - spoilers.size).coerceAtLeast(10)
        return nonSpoilers.take(nonSpoilerBudget) + spoilers
    }
}
