package com.kitsugi.animelist.data.settings

import com.kitsugi.animelist.model.WatchStatus
import org.json.JSONObject

/**
 * V2-F04 – KitsugiContentPrefs
 *
 * AniHyou ayar ekranından uyarlanan içerik/liste tercihlerinin, Compose dışında
 * çalışan istemciler (arama, manga skorlayıcı, senkron yöneticileri) tarafından
 * senkron okunabilmesi için [SettingsDataStore.settingsFlow] içinde güncellenen
 * uçucu önbellek. [com.kitsugi.animelist.utils.KitsugiTranslatePrefs] ile aynı desen.
 *
 * Kapsam: staffNameLanguage, scoreStep, separatedListStyle, showLowPriority,
 * priorityColorsJson, fuzzySearchEnabled, separateNovelsManga,
 * autoDateOnStatusChange, autoProgressOnComplete, defaultAddStatus.
 */
object KitsugiContentPrefs {
    @Volatile
    var staffNameLanguage: String = "ENGLISH"
        private set

    @Volatile
    var scoreStep: Float = 1f
        private set

    @Volatile
    var separatedListStyle: Boolean = true
        private set

    @Volatile
    var showLowPriority: Boolean = false
        private set

    @Volatile
    var priorityColorsJson: String = ""
        private set

    @Volatile
    var fuzzySearchEnabled: Boolean = true
        private set

    @Volatile
    var separateNovelsManga: Boolean = false
        private set

    /**
     * Wiki açılır sayfasının son seçilen düzeni (false = mobil, varsayılan).
     * Oturumlar bileşim sırasında oluşturulduğu için DataStore akışını bekleyemez;
     * senkron okunabilen bu önbellek "en son ne seçildiyse onu aç" davranışını sağlar.
     */
    @Volatile
    var wikiDesktopLayout: Boolean = false
        private set

    /** Liste ekleme otomasyonu: duruma göre otomatik başlangıç/bitiş tarihi. */
    @Volatile
    var autoDateOnStatusChange: Boolean = true
        private set

    /** Liste ekleme otomasyonu: "Tamamlandı"da ilerlemeyi toplam bölüme tamamla. */
    @Volatile
    var autoProgressOnComplete: Boolean = true
        private set

    /** Hızlı eklemede kullanılacak varsayılan durum: `PLANNED` / `WATCHING` / `COMPLETED`. */
    @Volatile
    var defaultAddStatus: String = "PLANNED"
        private set

    /** Oynatıcıdan otomatik liste takibi açık mı? (Compose dışı hattın hızlı okuması.) */
    @Volatile
    var autoTrackEnabled: Boolean = false
        private set

    /** Listede olmayan içerik oynatılınca otomatik eklensin mi? */
    @Volatile
    var autoTrackAddNew: Boolean = true
        private set

    /** "Bırakıldı" kayıtlar otomatik takiple ezilmesin. */
    @Volatile
    var autoTrackRespectDropped: Boolean = true
        private set

    /** Bitişte hızlı puanlama çubuğu gösterilsin mi? */
    @Volatile
    var quickRateEnabled: Boolean = true
        private set

    /** Hızlı puanlama çubuğunun ekranda kalma süresi (saniye). */
    @Volatile
    var quickRateSeconds: Int = 20
        private set

    /** Otomatik takip değişiklikleri bağlı tüm platformlara yayılsın mı? */
    @Volatile
    var autoTrackSyncSources: Boolean = true
        private set

    fun update(settings: AppSettings) {
        staffNameLanguage = settings.staffNameLanguage
        scoreStep = settings.scoreStep.toFloatOrNull()?.takeIf { it > 0f } ?: 1f
        separatedListStyle = settings.separatedListStyle
        showLowPriority = settings.showLowPriority
        priorityColorsJson = settings.priorityColorsJson
        fuzzySearchEnabled = settings.fuzzySearchEnabled
        separateNovelsManga = settings.separateNovelsManga
        wikiDesktopLayout = settings.wikiDesktopLayout
        autoDateOnStatusChange = settings.autoDateOnStatusChange
        autoProgressOnComplete = settings.autoProgressOnComplete
        defaultAddStatus = settings.defaultAddStatus
        autoTrackEnabled = settings.autoTrackEnabled
        autoTrackAddNew = settings.autoTrackAddNew
        autoTrackRespectDropped = settings.autoTrackRespectDropped
        quickRateEnabled = settings.quickRateEnabled
        quickRateSeconds = settings.quickRateSeconds
        autoTrackSyncSources = settings.autoTrackSyncSources
    }

    /**
     * [defaultAddStatus] dizesinin [WatchStatus] karşılığı.
     * Bilinmeyen/bozuk değerlerde güvenli taraf: [WatchStatus.Planned].
     */
    fun defaultAddStatusValue(): WatchStatus = when (defaultAddStatus) {
        "WATCHING"  -> WatchStatus.Watching
        "COMPLETED" -> WatchStatus.Completed
        else        -> WatchStatus.Planned
    }

    /**
     * Öncelik seviyesi (0 = düşük, 1 = orta, 2 = yüksek) için kullanıcı renk tohumu.
     * JSON bozuksa veya seviye tanımlı değilse null döner → UI varsayılan paleti kullanır.
     */
    fun priorityColorSeed(level: Int): Int? {
        if (priorityColorsJson.isBlank()) return null
        return runCatching {
            val obj = JSONObject(priorityColorsJson)
            if (obj.has(level.toString())) obj.optInt(level.toString()) else null
        }.getOrNull()
    }

    private val NORMALIZE_NON_WORD_REGEX =
        Regex("[^a-z0-9ğüşıöçâîûêáéíóúàèìòùäëïöü]+")

    /**
     * Önceden normalize edilmiş bir metin için tek seferlik hazırlanan arama jetonları.
     * Listem gibi yüzlerce satırlık koleksiyonlarda her tuş vuruşunda Regex/split/tahsis
     * yapılmasını tamamen ortadan kaldırır.
     */
    class NormalizedFuzzyTarget internal constructor(
        val normalized: String,
        val initials: String,
        val words: List<String>
    )

    fun prepareFuzzyTarget(raw: String): NormalizedFuzzyTarget {
        val norm = normalize(raw)
        if (norm.isEmpty()) {
            return NormalizedFuzzyTarget("", "", emptyList())
        }
        val words = ArrayList<String>(8)
        val initialsBuilder = StringBuilder(8)
        var start = 0
        val len = norm.length
        while (start < len) {
            while (start < len && norm[start] == ' ') start++
            if (start >= len) break
            var end = start + 1
            while (end < len && norm[end] != ' ') end++
            val word = norm.substring(start, end)
            words.add(word)
            initialsBuilder.append(word[0])
            start = end
        }
        return NormalizedFuzzyTarget(norm, initialsBuilder.toString(), words)
    }

    fun normalizeForFuzzy(text: String): String = normalize(text)

    /**
     * Önceden hazırlanmış [NormalizedFuzzyTarget] üzerinde sıfır-Regex, minimum-tahsisli
     * fuzzy eşleşme kontrolü.
     */
    fun fuzzyMatchesPrepared(target: NormalizedFuzzyTarget, normalizedQuery: String): Boolean {
        if (normalizedQuery.isEmpty()) return true
        val hay = target.normalized
        if (hay.isEmpty()) return false
        if (hay.contains(normalizedQuery)) return true
        if (!fuzzySearchEnabled) return false
        if (target.initials.contains(normalizedQuery)) return true
        if (isSubsequence(hay, normalizedQuery)) return true
        val tolerance = when {
            normalizedQuery.length <= 4 -> 1
            normalizedQuery.length <= 8 -> 2
            else -> 3
        }
        val qLen = normalizedQuery.length
        for (i in target.words.indices) {
            val word = target.words[i]
            if (kotlin.math.abs(word.length - qLen) <= tolerance &&
                levenshteinBounded(word, normalizedQuery, tolerance) <= tolerance
            ) {
                return true
            }
        }
        return false
    }

    /**
     * Fuzzy eşleşme: önce normalleştirilmiş "içerir" kontrolü; tutmazsa ve fuzzy
     * açıksa kısaltma ("aot" → "attack on titan" harf başları), harf alt dizisi ve
     * Levenshtein toleransı dener. Fuzzy kapalıysa sadece "içerir" sonucu geçerlidir.
     */
    fun fuzzyMatches(haystack: String, query: String): Boolean {
        val needle = normalize(query)
        if (needle.isEmpty()) return true
        if (haystack.isEmpty()) return false
        return fuzzyMatchesPrepared(prepareFuzzyTarget(haystack), needle)
    }

    private fun normalize(text: String): String {
        if (text.isEmpty()) return ""
        return text.lowercase(java.util.Locale.ROOT)
            .replace(NORMALIZE_NON_WORD_REGEX, " ")
            .trim()
    }

    private fun isSubsequence(hay: String, needle: String): Boolean {
        if (needle.length > hay.length) return false
        var i = 0
        for (ch in hay) {
            if (i < needle.length && ch == needle[i]) {
                i++
                if (i == needle.length) return true
            }
        }
        return i == needle.length
    }

    internal fun levenshteinBounded(a: String, b: String, maxDistance: Int): Int {
        if (a == b) return 0
        val n = a.length
        val m = b.length
        if (kotlin.math.abs(n - m) > maxDistance) return maxDistance + 1
        if (n == 0) return m
        if (m == 0) return n
        var prev = IntArray(m + 1) { it }
        var curr = IntArray(m + 1)
        for (i in 1..n) {
            curr[0] = i
            var rowMin = curr[0]
            val ai = a[i - 1]
            for (j in 1..m) {
                val cost = if (ai == b[j - 1]) 0 else 1
                val cell = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
                curr[j] = cell
                if (cell < rowMin) rowMin = cell
            }
            if (rowMin > maxDistance) return maxDistance + 1
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[m]
    }
}
