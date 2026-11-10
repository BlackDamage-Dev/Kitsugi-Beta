package com.kitsugi.animelist.utils

import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Bangumi şirket adları sözlüğü — **stüdyo / yapımcı / yayıncı ağ / yayımcı / dergi**.
 *
 * Veri kaynağı: `app/src/main/resources/bangumi/orgs.tsv` (`<etiket>\t<anahtar>\t<en>[\t<tr>]`).
 * Classpath kaynağından tembel (`by lazy`) okunur; hem APK'da hem JVM birim testlerinde Context
 * gerektirmeden çalışır ve JVM sınıf sabit havuzu sınırına takılmaz.
 */
internal object BangumiOrgDictionary {

    private const val STORE_RESOURCE = "bangumi/orgs.tsv"

    private class Row(
        val label: String,
        val key: String,
        val english: String,
        val turkish: String
    )

    private val rows: List<Row> by lazy { loadRows() }

    private fun loadRows(): List<Row> {
        val stream = Thread.currentThread().contextClassLoader?.getResourceAsStream(STORE_RESOURCE)
            ?: BangumiOrgDictionary::class.java.getResourceAsStream("/$STORE_RESOURCE")
            ?: error("Bangumi şirket sözlüğü kaynağı bulunamadı: $STORE_RESOURCE")
        val loaded = ArrayList<Row>(65536)
        stream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
            for (raw in lines) {
                if (raw.isEmpty()) continue
                val parts = raw.split('\t')
                if (parts.size < 3) continue
                val tr = if (parts.size >= 4 && parts[3].isNotEmpty()) parts[3] else parts[2]
                loaded.add(Row(parts[0], parts[1], parts[2], tr))
            }
        }
        check(loaded.isNotEmpty()) { "Bangumi şirket sözlüğü boş yüklendi: $STORE_RESOURCE" }
        return loaded
    }

    private val byLabel: Map<String, Row> by lazy {
        val map = HashMap<String, Row>(rows.size * 2)
        for (row in rows) {
            val key = normalize(row.label)
            if (key.isNotEmpty() && !map.containsKey(key)) map[key] = row
        }
        map
    }

    private val byCompact: Map<String, Row> by lazy {
        val map = HashMap<String, Row>(rows.size * 2)
        for (row in rows) {
            val key = compact(row.label)
            if (key.length >= 3 && !map.containsKey(key)) map[key] = row
        }
        map
    }

    private val byKey: Map<String, String> by lazy {
        val map = HashMap<String, String>(rows.size * 2)
        for (row in rows) if (!map.containsKey(row.key)) map[row.key] = row.english
        map
    }

    private val byKeyLabels: Map<String, List<String>> by lazy {
        val map = HashMap<String, MutableList<String>>(rows.size)
        for (row in rows) {
            val list = map.getOrPut(row.key) { ArrayList(4) }
            if (row.label !in list) list.add(row.label)
        }
        map
    }

    /** Sözlük anahtarına bağlı tüm bilinen etiketler (Latin + CJK). */
    fun labelsForKey(key: String?): List<String> =
        if (key == null) emptyList() else byKeyLabels[key] ?: emptyList()

    /** Gelen şirket adı için `bangumi_*` dil dosyası anahtarı; bilinmiyorsa `null`. */
    fun resourceKeyFor(raw: String?): String? = rowFor(raw)?.key

    /** Gelen adın küresel İngilizce yazımı (`京阿尼` → `Kyoto Animation`); bilinmiyorsa `null`. */
    fun latinNameFor(raw: String?): String? = rowFor(raw)?.english

    /**
     * Kurumun sözlükteki kimliği (`bangumi_producer_shueisha`); bilinmiyorsa `null`.
     *
     * Aynı şirketin farklı yazımlarını (SHUEISHA / 集英社, NBCユニバーサル・エンターテイメント /
     * ジェネオン・ユニバーサル・エンタテイメント) **tek çipte** toplamak için kullanılır.
     */
    internal fun identityKey(raw: String?): String? = rowFor(raw)?.key

    /** Gelen adın arayüz diline göre yerelleştirilmiş gösterimi; bilinmiyorsa `null`. */
    fun localizedNameFor(raw: String?, turkish: Boolean): String? =
        rowFor(raw)?.let { if (turkish) it.turkish else it.english }

    /** Anahtarın küresel İngilizce adı; yoksa `null`. */
    fun canonicalName(key: String): String? = byKey[key]

    /**
     * Eşleşen satır; bilinmiyorsa `null`.
     *
     * Yalnızca ham dize aranmaz: [lookupVariants] ile üretilen tüm yazımlar denenir. Bu sayede
     * `ジェネオン・ユニバーサル・エンターテイメント(NBCユニバーサル・エンターテイメントジャパン)`
     * gibi parantezli/uzun ünlü farklı yazımlar da doğru satıra bağlanır. Eşleşme olmadan
     * arayüzde ham Japonca ad görünüyordu.
     */
    private fun rowFor(raw: String?): Row? {
        val cleaned = raw?.trim().orEmpty()
        if (cleaned.isEmpty()) return null
        lookupVariants(cleaned).forEach { variant ->
            byLabel[normalize(variant)]?.let { return it }
            val compactKey = compact(variant)
            if (compactKey.length >= 2) byCompact[compactKey]?.let { return it }
        }
        return null
    }

    /**
     * Bir kurum adı için sözlükte aranacak yazım varyantları.
     *
     * Bangumi infobox'ı tek bir kurumu birden çok yazımla verebilir; bu yüzden ham
     * dize tek başına aranmaz:
     *  - `ジェネオン・ユニバーサル・エンターテイメント(NBCユニバーサル・エンターテイメントジャパン)`
     *    → parantez dışı ad **ve** parantez içi takma ad ayrı ayrı denenir,
     *  - `エンタテイメント` / `エンターテインメント` / `エンタテインメント` yazım farkları
     *    birbirine çevrilerek denenir,
     *  - `…ジャパン`, `株式会社`, `Inc.` gibi unvan ekleri atılarak denenir.
     *
     * Sözlük satırı üretilmez; yalnızca **sorgu** çoğaltılır (bellek maliyeti sabittir).
     * Sıra önemlidir: en özgül yazım başta gelir.
     */
    internal fun lookupVariants(raw: String): List<String> {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()

        fun addValue(value: String) {
            val trimmed = value.trim().trim(' ', '-', '・', '·', ':', '：', '、', ',', '/', '|')
            if (trimmed.isEmpty()) return
            if (trimmed.length > 120) return
            out += trimmed
        }

        addValue(cleaned)
        val outer = stripBracketed(cleaned)
        addValue(outer)
        // Parantez içi takma ad ("(NBCユニバーサル…)") kendi başına bir arama yazımıdır:
        // Bangumi kaydı bazen yalnızca takma adla eşleşir.
        bracketedContents(cleaned).forEach(::addValue)
        addValue(removeSeparators(outer))
        bracketedContents(cleaned).forEach { alias -> addValue(removeSeparators(alias)) }

        // Unvan ekleri (yalnızca sonda) ve yazım farkları — her taban için iki tur.
        val bases = out.toList()
        for (base in bases) {
            katakanaVariants(base).forEach(::addValue)
            stripCompanySuffix(base)?.let(::addValue)
        }
        val secondPass = out.toList()
        for (base in secondPass) {
            stripCompanySuffix(base)?.let(::addValue)
            katakanaVariants(base).forEach { variant -> stripCompanySuffix(variant)?.let(::addValue) }
        }
        return out.take(MAX_LOOKUP_VARIANTS)
    }

    /** `・` / `·` / boşluk ayraçlarını kaldırılmış yazım (`ジェネオン・ユニバーサル` → `ジェネオンユニバーサル`). */
    private fun removeSeparators(value: String): String =
        value.replace("・", "").replace("·", "").replace(" ", "")

    /**
     * Latin (İngilizce) kurum adının Bangumi'deki CJK/lokal yazımı.
     *
     * Infobox bazı yapımlarda kurumu Latin adıyla verir (`SHUEISHA`, `TBS`,
     * `avex entertainment`) ama Bangumi kurum kaydı CJK adla açılır (`集英社`). Bu köprü
     * olmadan ad→kimlik araması boş dönüyor ve kurum sayfası açılmıyordu. Eşleşme
     * yalnızca sözlüğün **İngilizce** sütunu üzerinden yapılır; uydurma ad üretilmez.
     * Sonuç 256 girişlik küçük bir bellekte tutulur (arama seyrek çalışır).
     */
    internal fun localNamesForLatin(latinName: String?): List<String> {
        val key = compact(latinName.orEmpty())
        if (key.length < 2) return emptyList()
        latinLookupCache[key]?.let { return it }
        val row = byEnglishCompact[key]
        val matches = if (row == null) emptyList() else listOf(row.label)
        if (latinLookupCache.size > 256) latinLookupCache.clear()
        latinLookupCache[key] = matches
        return matches
    }

    /**
     * İngilizce yazım → sözlük satırı (ters arama). Yalnızca ilk erişimde kurulur; sözlükteki
     * `byLabel`/`byCompact` indeksleriyle aynı büyüklükte tek bir `HashMap`'tir.
     */
    private val byEnglishCompact: Map<String, Row> by lazy {
        val map = HashMap<String, Row>(rows.size * 2)
        for (row in rows) {
            val key = compact(row.english)
            if (key.length >= 2 && !map.containsKey(key)) map[key] = row
        }
        map
    }

    private val latinLookupCache = java.util.concurrent.ConcurrentHashMap<String, List<String>>()

    /** `A(B)` → `A`; iç içe parantezler de atılır (tam genişlik biçimler dahil). */
    private fun stripBracketed(value: String): String {
        val sb = StringBuilder(value.length)
        var depth = 0
        for (ch in value) {
            when (ch) {
                '（', '(', '[', '【', '《', '「', '『' -> depth++
                '）', ')', ']', '】', '》', '」', '』' -> if (depth > 0) depth--
                else -> if (depth == 0) sb.append(ch)
            }
        }
        return sb.toString().trim()
    }

    /**
     * `A(B)` → `B` (birden çok parantez grubu ayrı ayrı döner).
     *
     * Kaynakta kapanmayan parantez de olur (`…エンターテイメント(NBCユニバーサル…`); bu durumda
     * kapanmamış grubun içeriği de bir takma ad olarak döner, yoksa ad hiç çözülemez.
     */
    private fun bracketedContents(value: String): List<String> {
        val groups = ArrayList<String>(2)
        var depth = 0
        val current = StringBuilder()
        for (ch in value) {
            when (ch) {
                '（', '(', '[', '【', '《', '「', '『' -> {
                    if (depth == 0) current.setLength(0) else current.append(ch)
                    depth++
                }
                '）', ')', ']', '】', '》', '」', '』' -> {
                    if (depth > 0) depth--
                    if (depth == 0) {
                        val group = current.toString().trim()
                        if (group.isNotEmpty()) groups += group
                        current.setLength(0)
                    } else {
                        current.append(ch)
                    }
                }
                else -> if (depth > 0) current.append(ch)
            }
        }
        if (depth > 0) {
            val group = current.toString().trim()
            if (group.isNotEmpty()) groups += group
        }
        return groups
    }

    /**
     * Katakana uzun ünlü / hece yazımı aynı şirket için farklı kaydedilmiş olabilir
     * (`ジェネオン・ユニバーサル・エンタテイメント` ↔ `…エンターテイメント` ↔
     * `…エンターテインメント`). Grup içindeki her yazım diğerlerine çevrilerek denenir.
     */
    private val katakanaSpellingGroup = listOf(
        "エンタテイメント",
        "エンタテインメント",
        "エンターテイメント",
        "エンターテインメント"
    )

    private val katakanaSpellingPairs = listOf(
        "コミュニケーションズ" to "コミュニケーション",
        "ピクチャーズ" to "ピクチャース",
        "アニメーション" to "アニメ"
    )

    /** Yaygın uzun ünlü / hece yazım farklarını çeviren varyantlar. */
    private fun katakanaVariants(value: String): List<String> {
        val out = ArrayList<String>(4)
        val matched = katakanaSpellingGroup.firstOrNull { value.contains(it) }
        if (matched != null) {
            katakanaSpellingGroup.forEach { other ->
                if (other != matched) out += value.replace(matched, other)
            }
        }
        for ((from, to) in katakanaSpellingPairs) {
            if (value.contains(from)) {
                val replaced = value.replace(from, to)
                if (replaced != value) out += replaced
            }
        }
        return out
    }

    private val companySuffixes = listOf(
        "ジャパン", "株式会社", "有限会社", "合同会社", "股份有限公司", "有限公司",
        "co., ltd.", "co.,ltd.", "co. ltd.", "co ltd", "inc.", "inc", "ltd.", "ltd",
        "llc", "corporation", "corp.", "corp", "company", "k.k."
    )

    /** Sondaki unvan/ülke ekini atar; değişim yoksa `null`. */
    private fun stripCompanySuffix(value: String): String? {
        val trimmed = value.trim()
        for (suffix in companySuffixes) {
            if (trimmed.length <= suffix.length + 1) continue
            if (!trimmed.endsWith(suffix, ignoreCase = true)) continue
            // Latin unvanlar yalnızca sözcük sınırındaysa atılır: "Zinc" → "Z" olmasın.
            if (suffix.any { it.code < 0x300 }) {
                val before = trimmed[trimmed.length - suffix.length - 1]
                if (before.isLetterOrDigit()) continue
            }
            val stripped = trimmed.dropLast(suffix.length).trim().trimEnd('・', '·', ',', ' ')
            if (stripped.length >= 2) return stripped
        }
        return null
    }

    /** Arka plan iş parçacığında (Application.onCreate) tabloları önceden ısıtır. */
    fun prewarm() {
        byLabel.size
        byCompact.size
    }

    /** Girdi sayısı (test/telemetri için). */
    val size: Int get() = rows.size

    /** Boşluk, büyük/küçük harf, tam genişlik noktalama ve sarımları yok sayan anahtar. */
    private fun normalize(value: String): String {
        if (value.isBlank()) return ""
        val sb = StringBuilder(value.length)
        var prevSpace = true
        for (ch in value) {
            val mapped = when (ch) {
                '　', '：', ':', '・', '·', '。', '、', '，', ',' -> ' '
                '（', '(', '[', '【', '《', '「', '『',
                '）', ')', ']', '】', '》', '」', '』' -> '\u0000'
                else -> if (ch.isWhitespace()) ' ' else ch
            }
            if (mapped == '\u0000') continue
            if (mapped == ' ') {
                if (!prevSpace) {
                    sb.append(' ')
                    prevSpace = true
                }
            } else {
                sb.append(mapped)
                prevSpace = false
            }
        }
        if (sb.isNotEmpty() && sb[sb.length - 1] == ' ') {
            sb.setLength(sb.length - 1)
        }
        return sb.toString().lowercase(Locale.ROOT)
    }

    /** Yalnızca harf/rakam bırakan sıkı anahtar: `Production.IG` → `productionig`. */
    private fun compact(value: String): String {
        if (value.isBlank()) return ""
        val sb = StringBuilder(value.length)
        for (ch in value) if (ch.isLetterOrDigit()) sb.append(ch)
        return sb.toString().lowercase(Locale.ROOT)
    }

    /** Tek bir ad için denenecek en fazla yazım (istek sayısını sınırlar). */
    private const val MAX_LOOKUP_VARIANTS = 16
}
