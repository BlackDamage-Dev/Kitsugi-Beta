package com.kitsugi.animelist.utils

import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Bangumi (bgm.tv) **tür ve etiket sözlüğü**.
 *
 * Bangumi条目'larında `类型` (tür) ve `标签` (etiket) alanları kullanıcı kaynaklıdır ve
 * neredeyse her zaman Çince (basit + geleneksel karışık) ya da Japonca gelir:
 * `游戏改`, `治愈系`, `催涙`, `2008年10月`, `GAL改`, `续作` ... Bu sözlük o değerleri
 * uygulamanın arayüz diline (Türkçe / İngilizce) çevirir.
 *
 * Veri kaynağı: `app/src/main/resources/bangumi/tags.tsv` (`<key>\t<tr>\t<en>\t<etiket>...`).
 * Classpath kaynağından tembel (`by lazy`) yüklenir; böylece hem APK'da hem JVM birim
 * testlerinde Context gerektirmeden çalışır ve JVM 65.535 sabit havuzu sınırına takılmaz.
 */
internal data class BangumiTagEntry(
    /** `bangumi_tag_*` dil dosyası anahtarı (snake_case). */
    val key: String,
    val turkish: String,
    val english: String
)

internal object BangumiTagDictionary {

    private const val STORE_RESOURCE = "bangumi/tags.tsv"

    private class Group(val entry: BangumiTagEntry, val labels: List<String>)

    private val groups: List<Group> by lazy { loadGroups() }

    private fun loadGroups(): List<Group> {
        val stream = Thread.currentThread().contextClassLoader?.getResourceAsStream(STORE_RESOURCE)
            ?: BangumiTagDictionary::class.java.getResourceAsStream("/$STORE_RESOURCE")
            ?: error("Bangumi etiket sözlüğü kaynağı bulunamadı: $STORE_RESOURCE")
        val loaded = ArrayList<Group>(4096)
        stream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
            for (raw in lines) {
                if (raw.isEmpty()) continue
                val parts = raw.split('\t')
                if (parts.size < 3) continue
                loaded.add(
                    Group(
                        entry = BangumiTagEntry(parts[0], parts[1], parts[2]),
                        labels = parts.drop(3)
                    )
                )
            }
        }
        check(loaded.isNotEmpty()) { "Bangumi etiket sözlüğü boş yüklendi: $STORE_RESOURCE" }
        return loaded
    }

    /** Grup sayısı (test ve telemetri için). */
    val size: Int get() = groups.size

    /** Normalleştirilmiş kaynak yazımı → giriş. İlk yazım kazanır (aynı anlam, birden çok yazım). */
    private val byLabel: Map<String, Group> by lazy {
        val map = HashMap<String, Group>(groups.sumOf { it.labels.size } * 2)
        for (group in groups) {
            for (label in group.labels) {
                val key = normalize(label)
                if (key.isEmpty()) continue
                if (!map.containsKey(key)) map[key] = group
            }
        }
        // Girişin Latin/TR adı da bir eşleşme anahtarıdır: Bangumi'de aynı etiket hem Çince hem
        // Latin yazımıyla bulunabiliyor (`京阿尼` + `Kyoto Animation`) — tek çipte buluşsunlar.
        // Açık yazımlar önce işlendiği için bir ad asla gerçek bir etiketi gölgeleyemez.
        for (group in groups) {
            for (name in listOf(group.entry.english, group.entry.turkish)) {
                val key = normalize(name)
                if (key.isEmpty()) continue
                if (!map.containsKey(key)) map[key] = group
            }
        }
        map
    }

    /**
     * Noktalama/boşluk duyarsız ikinci tur eşleşme anahtarı → giriş.
     * `Production I.G`, `Production.IG` ve `ProductionI.G` aynı çipte toplansın diye.
     */
    private val byCompact: Map<String, Group> by lazy {
        val map = HashMap<String, Group>(groups.sumOf { it.labels.size } * 2)
        for (group in groups) {
            for (label in group.labels) {
                val key = compact(label)
                if (key.length < 3) continue
                if (!map.containsKey(key)) map[key] = group
            }
        }
        for (group in groups) {
            for (name in listOf(group.entry.english, group.entry.turkish)) {
                val key = compact(name)
                if (key.length < 3) continue
                if (!map.containsKey(key)) map[key] = group
            }
        }
        map
    }

    /**
     * Sorgu eşlemesi iki katmanlıdır: (1) [ALIAS_TAGS] elle sabitlenmiş anlam köprüleri,
     * (2) sözlük grubu → [PREFERRED_TAGS] ya da grubun ilk **yerel** yazımı.
     *
     * Not: Bu tablo yalnızca **sorgu** için kullanılır (`filter.tag`); arayüzde etiketlerin
     * görünen adı `entryFor()` üzerinden çevrilir ve bu değişiklikten etkilenmez.
     */

    /** Tablo eşleşmesi; yıl/ay kalıpları dahil. Bilinmeyen etiket için `null`. */
    fun entryFor(label: String?): BangumiTagEntry? = groupFor(label)?.entry

    /**
     * **Bangumi'nin kendi yazımı** (grup içindeki ilk etiket — neredeyse her zaman Çince).
     *
     * `POST /v0/search/subjects` `filter.tag` alanı sunucuda `tag = "科幻"` biçiminde
     * Meilisearch süzgecine çevrilir; karşılaştırma **birebir** olduğu için uygulamanın
     * Türkçe/İngilizce çip adlarını (`Sci-Fi`, `Bilim Kurgu`) doğrudan göndermek her zaman
     * sıfır sonuç verir. Bu fonksiyon arayüz etiketini sunucunun anladığı yazıma çevirir.
     *
     * Eşleşme yoksa `null` döner; çağıran taraf etiketi göndermemeli (sessizce tüm
     * kataloğu döndürmek, süzgeç uygulanmış gibi görünmesine yol açıyordu).
     */
    fun canonicalTagFor(label: String?): String? {
        val raw = label?.trim().orEmpty()
        if (raw.isEmpty()) return null
        // 1) Arayüzde aynı türün birden çok Bangumi yazımı var; sunucudaki **en geniş**
        //    yazım kazanmalı (aşağıdaki tablo canlı API ölçümleriyle sabitlendi).
        ALIAS_TAGS[normalize(raw)]?.let { return it }
        // 2) Sözlük grubu → grubun tercih edilen Çince yazımı.
        groupFor(raw)?.let { group ->
            PREFERRED_TAGS[group.entry.key]?.let { return it }
            // Gruptaki ilk etiket her zaman Çince olmuyor (`Science-Fiction`,
            // `Bilim Kurgu`); sunucu birebir karşılaştırdığı için Latin yazım
            // göndermek boş liste getirir. Önce yerel (CJK) yazımı seç.
            group.labels.firstOrNull(::looksLikeNativeTag)?.let { return it }
            group.labels.firstOrNull()?.let { if (looksLikeNativeTag(it)) return it }
        }
        // 3) Zaten Bangumi yazımındaysa (Çince/Japonca) olduğu gibi bırak.
        return raw.takeIf(::looksLikeNativeTag)
    }

    /**
     * Grup anahtarı → sunucuda **gerçekten dolu** olan Bangumi yazımı.
     *
     * Sözlükteki ilk etiket yazımı (grup dosyasının sırası) sunucudaki yaygın yazım
     * olmak zorunda değil: `Sports` grubunun ilk yazımı `运动系` (8 kayıt), oysa aynı
     * gruptaki `运动` 516 kayıt veriyor; `Thriller` grubunun ilk yazımı `悬疑惊悚`
     * **0** kayıt, `Military` grubunun ilk yazımı `军事` 63 kayıt. Bu tablo, arayüz
     * çiplerinin boş/kırık ızgara üretmesini engeller.
     *
     * Değerler 2026-10-31/11-01 tarihli canlı `POST /v0/search/subjects` probuyla
     * (`probe/result.txt`, K/L/N/O blokları) doğrulandı: `type=2`, `sort=rank`,
     * `tag=<yazım>` sorgusunun `total` alanı.
     */
    private val PREFERRED_TAGS: Map<String, String> = mapOf(
        "sports_theme" to "运动",      // 运动系 = 8 → 运动 = 516
        "thriller" to "惊悚",          // 悬疑惊悚 = 0 → 惊悚 = 114
        "military" to "战争",          // 军事 = 63 → 战争 = 445
        "supernatural" to "灵异"       // 超自然 = 7, 灵异 = 65
    )

    /**
     * Anlamı sözlükte **ayrı** bir gruba düşen ama Bangumi'de karşılığı başka bir grup
     * olan arayüz terimleri. Örn. `Bilim Kurgu` sözlükte yalnız Latin etiketli
     * `science_fiction` grubuna düşüyor; sunucuda karşılığı `科幻` (`sci_fi`).
     * `Savaş` ise `battle` grubuna (`Battle`, `Savaş`) düşüyor ve Bangumi'de `战争`.
     */
    private val ALIAS_TAGS: Map<String, String> = mapOf(
        normalize("bilim kurgu") to "科幻",
        normalize("science fiction") to "科幻",
        normalize("science-fiction") to "科幻",
        normalize("sci fi") to "科幻",
        normalize("savaş") to "战争",
        normalize("savas") to "战争",
        normalize("war") to "战争",
        normalize("psikoloji") to "心理",
        normalize("romantizm") to "恋爱",
        normalize("romantik") to "恋爱"
    )

    /** Etiket sunucunun kendi dilinde mi yazılmış (CJK ya da sözlükte bilinen yazım)? */
    fun looksLikeNativeTag(label: String): Boolean {
        if (label.isBlank()) return false
        var cjk = 0
        var latin = 0
        for (ch in label) {
            when {
                ch.code in 0x3040..0x30FF || ch.code in 0x3400..0x9FFF || ch.code in 0xF900..0xFAFF -> cjk++
                ch.isLetter() -> latin++
            }
        }
        return cjk > 0 && cjk >= latin
    }

    private fun groupFor(label: String?): Group? {
        val raw = label?.trim().orEmpty()
        if (raw.isEmpty()) return null
        byLabel[normalize(raw)]?.let { return it }
        patternGroup(raw)?.let { return it }
        val compact = compact(raw)
        if (compact.length >= 3) byCompact[compact]?.let { return it }
        return null
    }

    /** Takvim kalıbı eşleşmelerini de bir [Group] olarak döndürür (etiket yazımı = ham metin). */
    private fun patternGroup(raw: String): Group? {
        val entry = patternEntry(raw) ?: return null
        return Group(entry, listOf(raw))
    }

    /** Arka plan iş parçacığında (Application.onCreate) tabloları önceden ısıtır. */
    fun prewarm() {
        byLabel.size
        byCompact.size
    }

    /** Sözlükteki tüm Çince/Japonca yazımlar (test ve araç kullanımı için). */
    fun knownLabels(): Set<String> = byLabel.keys.toSet()

    // ── Yardımcılar ────────────────────────────────────────────────────────────

    /**
     * Karşılaştırma anahtarı. Boşluk, büyük/küçük harf, tam genişlikli noktalama ve
     * `「」『』《》（）()[]` sarımları yok sayılır.
     */
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

    private val YEAR_MONTH = Regex("^(\\d{4})\\s*年\\s*(\\d{1,2})\\s*月?$")
    private val YEAR_ONLY = Regex("^(\\d{4})\\s*年?$")
    private val DECADE = Regex("^(\\d{3})0\\s*(?:年代|年間)$")
    private val MONTH_ONLY = Regex("^(\\d{1,2})\\s*月$")

    private val monthNamesTurkish = listOf(
        "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
        "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"
    )
    private val monthNamesEnglish = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    /** `2008年10月`, `2008年`, `2000年代`, `10月` gibi takvim etiketlerini çevirir. */
    private fun patternEntry(raw: String): BangumiTagEntry? {
        YEAR_MONTH.find(raw)?.let { match ->
            val year = match.groupValues[1]
            val month = match.groupValues[2].toIntOrNull() ?: return@let
            if (month in 1..12) {
                return BangumiTagEntry(
                    "date_${year}_$month",
                    "${monthNamesTurkish[month - 1]} $year",
                    "${monthNamesEnglish[month - 1]} $year"
                )
            }
        }
        YEAR_ONLY.find(raw)?.let { match ->
            val year = match.groupValues[1].toIntOrNull() ?: return@let
            if (year in 1900..2100) {
                return BangumiTagEntry("year_$year", year.toString(), year.toString())
            }
        }
        DECADE.find(raw)?.let { match ->
            val decade = match.groupValues[1].toIntOrNull()?.times(10) ?: return@let
            if (decade in 1900..2100) {
                return BangumiTagEntry("decade_$decade", "${decade}'ler", "${decade}s")
            }
        }
        MONTH_ONLY.find(raw)?.let { match ->
            val month = match.groupValues[1].toIntOrNull() ?: return@let
            if (month in 1..12) {
                return BangumiTagEntry(
                    "month_$month",
                    monthNamesTurkish[month - 1],
                    monthNamesEnglish[month - 1]
                )
            }
        }
        return null
    }
}
