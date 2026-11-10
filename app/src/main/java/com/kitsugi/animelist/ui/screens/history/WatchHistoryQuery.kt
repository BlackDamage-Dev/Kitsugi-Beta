package com.kitsugi.animelist.ui.screens.history

import androidx.annotation.StringRes
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.model.WatchHistoryEntry
import com.kitsugi.animelist.utils.KitsugiStrings
import java.text.Collator
import java.util.concurrent.TimeUnit

/**
 * İzleme geçmişi ekranının **saf** (Compose'a bağlı olmayan) hesap katmanı:
 * sıralama, filtreleme, diziye göre gruplama, istatistik ve zaman biçimlendirme.
 *
 * Bilinçli olarak `WatchHistoryScreen.kt` dışında tutuldu; böylece aynı kurallar
 * gelecekte başka bir yerde (örn. profil özeti) yeniden kullanılabilir ve davranış
 * tek bir yerde test edilebilir.
 */
enum class WatchHistorySortMode(@StringRes val labelRes: Int) {
    /** En son izlenen en üstte (varsayılan). */
    RECENT(R.string.wh_sort_recent),

    /** Geçmişte en eski kayıt üstte. */
    OLDEST(R.string.wh_sort_oldest),

    /** Yarısı izlenmiş bölümler en üstte — "kaldığım yerden devam" için en kullanışlısı. */
    CONTINUE_FIRST(R.string.wh_sort_continue_first),

    /** Tamamlanmaya en yakın kayıt üstte. */
    PROGRESS(R.string.wh_sort_progress),

    /** Başlık A→Z (dile duyarlı sıralama). */
    TITLE_ASC(R.string.wh_sort_title_asc),

    /** Kaynak/eklenti adı A→Z. */
    SOURCE(R.string.wh_sort_source)
}

enum class WatchHistoryProgressFilter(@StringRes val labelRes: Int) {
    ALL(R.string.wh_filter_all),
    CONTINUE(R.string.wh_filter_continue),
    COMPLETED(R.string.wh_filter_completed),
    NOT_STARTED(R.string.wh_filter_not_started)
}

enum class WatchHistoryMediaFilter(@StringRes val labelRes: Int) {
    ALL(R.string.wh_filter_media_all),
    SERIES(R.string.wh_filter_series),
    MOVIES(R.string.wh_filter_movies)
}

/**
 * Bir animenin/dizinin geçmişteki bütün bölümlerini tek kartta toplar.
 */
data class WatchHistorySeries(
    val animeId: String,
    val title: String,
    val posterUrl: String?,
    /** Bölüm numarasına göre artan sırada. */
    val entries: List<WatchHistoryEntry>,
    val latestEntry: WatchHistoryEntry,
    val completedCount: Int,
    val inProgressEntry: WatchHistoryEntry?
) {
    val episodeCount: Int get() = entries.size

    /**
     * En son izlenen bölümün kısa kodu ("S2 · E5"). Film kayıtlarında `null` döner;
     * çağıran taraf bunun yerine yerelleştirilmiş "Film" etiketini gösterir.
     */
    val latestEpisodeCode: String?
        get() = if (latestEntry.isMovie) null else "S${latestEntry.season} · E${latestEntry.episode}"
}

/**
 * Listede tarih başlığı altında toplanan kayıt grubu ("Bugün", "Dün", "Bu hafta"...).
 */
data class HistorySection(
    @StringRes val titleRes: Int,
    val entries: List<WatchHistoryEntry>
)

data class WatchHistoryStats(
    val totalEntries: Int,
    /** Geçmişteki farklı başlık sayısı (dizi + film, aynı anime kimliği bir kez sayılır). */
    val titleCount: Int,
    val seriesCount: Int,
    val movieCount: Int,
    val continueCount: Int,
    val completedCount: Int,
    val watchedMs: Long
)

object WatchHistoryQuery {

    /** Bu oranın üzerindeki kayıtlar "izlendi" sayılır (jenerik/bitiş payı). */
    const val COMPLETED_THRESHOLD = 0.92f

    /** Kayıtlı akış adresi bu süre boyunca "taze" sayılır (imzalı/CDN adresleri eskisi). */
    const val DIRECT_PLAY_WINDOW_MS = 30 * 60 * 1000L

    private const val ONE_DAY_MS = 24 * 60 * 60 * 1000L

    /**
     * Kayıttaki akış adresi doğrudan oynatma için hâlâ kullanılabilir mi?
     *
     * İki koşul: adres kaydedilmiş olmalı ve **bu uygulama oturumunda** yazılmış olmalı.
     * Bir önceki açılıştan kalan adresler çoğunlukla süresi geçmiş token taşıyor; bu
     * yüzden 30 dakikalık pencere + uygulama açılış zamanı kontrolü birlikte kullanılır.
     */
    fun isDirectPlayable(
        entry: WatchHistoryEntry,
        nowMs: Long = System.currentTimeMillis(),
        appLaunchTimeMs: Long = com.kitsugi.animelist.KitsugiApplication.APP_LAUNCH_TIME
    ): Boolean {
        if (entry.streamUrl.isNullOrBlank()) return false
        if (nowMs - entry.watchedAtMs > DIRECT_PLAY_WINDOW_MS) return false
        return entry.watchedAtMs >= appLaunchTimeMs
    }

    // ── Tek kayıt yardımcıları ────────────────────────────────────────────────

    fun progressOf(entry: WatchHistoryEntry): Float {
        if (entry.durationMs <= 0L) return 0f
        return (entry.positionMs.toFloat() / entry.durationMs.toFloat()).coerceIn(0f, 1f)
    }

    fun hasKnownDuration(entry: WatchHistoryEntry): Boolean = entry.durationMs > 0L

    fun isCompleted(entry: WatchHistoryEntry): Boolean =
        hasKnownDuration(entry) && progressOf(entry) >= COMPLETED_THRESHOLD

    fun isInProgress(entry: WatchHistoryEntry): Boolean {
        if (!hasKnownDuration(entry)) return false
        val p = progressOf(entry)
        return p > 0.01f && p < COMPLETED_THRESHOLD
    }

    fun isNotStarted(entry: WatchHistoryEntry): Boolean = progressOf(entry) <= 0.01f

    fun remainingMs(entry: WatchHistoryEntry): Long =
        if (hasKnownDuration(entry)) (entry.durationMs - entry.positionMs).coerceAtLeast(0L) else 0L

    /** Gerçekten izlendiği tahmin edilen süre (bilinmeyen sürelerde konum kullanılır). */
    fun watchedMs(entry: WatchHistoryEntry): Long =
        if (hasKnownDuration(entry)) entry.positionMs.coerceIn(0L, entry.durationMs)
        else entry.positionMs.coerceAtLeast(0L)

    /**
     * Kaydın tek satırlık kaynak etiketi: eklenti adı + akış başlığı, tekrarsız
     * (bkz. [com.kitsugi.animelist.utils.StreamLabelSanitizer]).
     */
    fun sourceLabel(entry: WatchHistoryEntry): String =
        com.kitsugi.animelist.utils.StreamLabelSanitizer.combine(entry.streamName, entry.streamTitle)

    /** Ad/başlık metninden çıkarılan ses türü (Dual/Dublaj/Altyazılı) — yoksa null. */
    fun audioLabel(entry: WatchHistoryEntry): String? {
        val fromName = com.kitsugi.animelist.utils.StreamLabelSanitizer.detectAudioLabel(entry.streamName)
        if (fromName != null) return fromName
        return com.kitsugi.animelist.utils.StreamLabelSanitizer.detectAudioLabel(entry.streamTitle)
    }

    // ── Filtreleme ────────────────────────────────────────────────────────────

    fun applyFilters(
        entries: List<WatchHistoryEntry>,
        query: String = "",
        progressFilter: WatchHistoryProgressFilter = WatchHistoryProgressFilter.ALL,
        mediaFilter: WatchHistoryMediaFilter = WatchHistoryMediaFilter.ALL,
        sourceFilter: String? = null
    ): List<WatchHistoryEntry> {
        val normalizedQuery = normalize(query)
        return entries.filter { entry ->
            val passesProgress = when (progressFilter) {
                WatchHistoryProgressFilter.ALL -> true
                WatchHistoryProgressFilter.CONTINUE -> isInProgress(entry)
                WatchHistoryProgressFilter.COMPLETED -> isCompleted(entry)
                WatchHistoryProgressFilter.NOT_STARTED -> isNotStarted(entry)
            }
            val passesMedia = when (mediaFilter) {
                WatchHistoryMediaFilter.ALL -> true
                WatchHistoryMediaFilter.SERIES -> !entry.isMovie
                WatchHistoryMediaFilter.MOVIES -> entry.isMovie
            }
            val passesSource = sourceFilter.isNullOrBlank() ||
                entry.source?.equals(sourceFilter, ignoreCase = true) == true
            val passesQuery = normalizedQuery.isEmpty() ||
                normalize(entry.animeTitle).contains(normalizedQuery) ||
                normalize(entry.source.orEmpty()).contains(normalizedQuery) ||
                normalize(sourceLabel(entry)).contains(normalizedQuery)
            passesProgress && passesMedia && passesSource && passesQuery
        }
    }

    /** Geçmişte tekrar eden kaynak/eklenti adları (filtre menüsü için). */
    fun availableSources(entries: List<WatchHistoryEntry>): List<String> =
        entries.mapNotNull { it.source?.trim()?.takeIf(String::isNotBlank) }
            .distinctBy { it.lowercase() }
            .sortedWith(collator())

    // ── Sıralama ──────────────────────────────────────────────────────────────

    fun sort(entries: List<WatchHistoryEntry>, mode: WatchHistorySortMode): List<WatchHistoryEntry> {
        return when (mode) {
            WatchHistorySortMode.RECENT ->
                entries.sortedWith(compareByDescending<WatchHistoryEntry> { it.watchedAtMs })
            WatchHistorySortMode.OLDEST ->
                entries.sortedWith(compareBy<WatchHistoryEntry> { it.watchedAtMs })
            WatchHistorySortMode.CONTINUE_FIRST -> entries.sortedWith(
                compareBy<WatchHistoryEntry>(
                    // 1) devam edenler, 2) başlanmamışlar, 3) tamamlananlar
                    { entry ->
                        when {
                            isInProgress(entry) -> 0
                            isNotStarted(entry) -> 1
                            else -> 2
                        }
                    },
                    // Devam edenlerde en çok süre kalan en üstte: uzun bölümü yarıda
                    // bırakan kullanıcı onu ilk görmek ister.
                    { entry -> -remainingMs(entry) }
                ).thenByDescending { it.watchedAtMs }
            )
            WatchHistorySortMode.PROGRESS -> entries.sortedWith(
                compareByDescending<WatchHistoryEntry> { progressOf(it) }
                    .thenByDescending { it.watchedAtMs }
            )
            WatchHistorySortMode.TITLE_ASC -> entries.sortedWith(titleComparator())
            WatchHistorySortMode.SOURCE -> entries.sortedWith(
                compareBy<WatchHistoryEntry, String>(collator()) { it.source.orEmpty().lowercase() }
                    .then(titleComparator())
            )
        }
    }

    /** Başlık → sezon → bölüm sırası (dile duyarlı). */
    private fun titleComparator(): Comparator<WatchHistoryEntry> =
        compareBy<WatchHistoryEntry, String>(collator()) { it.animeTitle.lowercase() }
            .thenBy { it.season }
            .thenBy { it.episode }

    // ── Gruplama ──────────────────────────────────────────────────────────────

    /**
     * Aynı anime kimliğine (`animeId`) ait kayıtları tek [WatchHistorySeries] altında toplar.
     * Gruplar, **en son izlenen** grup üstte olacak şekilde sıralanır; bölümler kendi
     * içinde sezon/bölüm numarasına göre artan sırada listelenir (izleme sırası gibi).
     */
    fun groupBySeries(entries: List<WatchHistoryEntry>): List<WatchHistorySeries> {
        if (entries.isEmpty()) return emptyList()
        return entries
            .groupBy { it.animeId }
            .map { (animeId, groupEntries) ->
                val ordered = groupEntries.sortedWith(
                    compareBy<WatchHistoryEntry> { it.season }.thenBy { it.episode }
                )
                val latest = groupEntries.maxByOrNull { it.watchedAtMs } ?: ordered.last()
                WatchHistorySeries(
                    animeId = animeId,
                    title = latest.animeTitle,
                    posterUrl = latest.posterUrl ?: ordered.firstNotNullOfOrNull { it.posterUrl },
                    entries = ordered,
                    latestEntry = latest,
                    completedCount = groupEntries.count { isCompleted(it) },
                    inProgressEntry = groupEntries.filter { isInProgress(it) }
                        .maxByOrNull { it.watchedAtMs }
                )
            }
            .sortedByDescending { it.latestEntry.watchedAtMs }
    }

    // ── Tarih bölümleri ───────────────────────────────────────────────────────

    /** Verilen anın yerel gün başlangıcı. */
    fun startOfDay(ms: Long): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = ms
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /** Kaç gün önce? (0 = bugün, 1 = dün...) Yaz saati geçişlerinde yuvarlanır. */
    fun dayOffset(ms: Long, nowMs: Long = System.currentTimeMillis()): Int {
        val diff = startOfDay(nowMs) - startOfDay(ms)
        val days = Math.round(diff.toDouble() / ONE_DAY_MS.toDouble()).toInt()
        return days.coerceAtLeast(0)
    }

    /**
     * Kayıtları, **sıralamayı bozmadan** ardışık tarih başlıklarına böler:
     * Bugün → Dün → Bu hafta → Bu ay → Daha eski.
     */
    fun withSections(
        entries: List<WatchHistoryEntry>,
        nowMs: Long = System.currentTimeMillis()
    ): List<HistorySection> {
        if (entries.isEmpty()) return emptyList()
        val result = mutableListOf<HistorySection>()
        var currentBucket = -1
        var currentEntries = mutableListOf<WatchHistoryEntry>()

        fun flush() {
            if (currentEntries.isNotEmpty()) {
                result += HistorySection(bucketTitleRes(currentBucket), currentEntries.toList())
                currentEntries = mutableListOf()
            }
        }

        for (entry in entries) {
            val bucket = bucketOf(dayOffset(entry.watchedAtMs, nowMs))
            if (bucket != currentBucket) {
                flush()
                currentBucket = bucket
            }
            currentEntries += entry
        }
        flush()
        return result
    }

    private fun bucketOf(dayOffset: Int): Int = when {
        dayOffset <= 0 -> 0
        dayOffset == 1 -> 1
        dayOffset < 7 -> 2
        dayOffset < 30 -> 3
        else -> 4
    }

    @StringRes
    private fun bucketTitleRes(bucket: Int): Int = when (bucket) {
        0 -> R.string.wh_section_today
        1 -> R.string.wh_section_yesterday
        2 -> R.string.wh_section_this_week
        3 -> R.string.wh_section_this_month
        else -> R.string.wh_section_older
    }

    // ── İstatistik ────────────────────────────────────────────────────────────

    fun stats(entries: List<WatchHistoryEntry>): WatchHistoryStats = WatchHistoryStats(
        totalEntries = entries.size,
        titleCount = entries.map { it.animeId }.distinct().size,
        seriesCount = entries.filterNot { it.isMovie }.map { it.animeId }.distinct().size,
        movieCount = entries.count { it.isMovie },
        continueCount = entries.count { isInProgress(it) },
        completedCount = entries.count { isCompleted(it) },
        watchedMs = entries.sumOf { watchedMs(it) }
    )

    // ── Biçimlendirme ─────────────────────────────────────────────────────────

    /** `12:34` ya da `1:02:03`. Süre bilinmiyorsa boş metin. */
    fun formatPosition(positionMs: Long): String {
        if (positionMs <= 0L) return "00:00"
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(positionMs)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(java.util.Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(java.util.Locale.ROOT, "%02d:%02d", minutes, seconds)
        }
    }

    /** Tamamlanma yüzdesi (0-100). */
    fun percentOf(entry: WatchHistoryEntry): Int =
        Math.round(progressOf(entry) * 100f).coerceIn(0, 100)

    /**
     * Dile duyarlı karşılaştırıcı. Arayüz diline göre `Ç/Ğ/İ/Ö/Ş/Ü` doğru sıralanır.
     * Dil çözülemezse (ör. birim testinde kaynak yok) sistem varsayılanına düşer.
     */
    private fun collator(): Collator {
        val locale = runCatching { KitsugiStrings.locale() }.getOrNull()
            ?: java.util.Locale.getDefault()
        return Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    }

    /**
     * Arama karşılaştırması için metni sadeleştirir: küçük harf + Türkçe/aksan
     * farklarını eşitler (İ/ı/I, ş/s, ğ/g ...). Böylece "kisi" yazınca "Kişi" bulunur.
     */
    private fun normalize(raw: String): String {
        if (raw.isEmpty()) return ""
        val lower = raw.lowercase(java.util.Locale.ROOT)
        val builder = StringBuilder(lower.length)
        for (ch in lower) {
            // Türkçe büyük "İ" küçültülünce "i" + birleşik nokta üretir; noktayı at.
            if (ch == '\u0307') continue
            builder.append(
                when (ch) {
                    'ı', 'i', 'î' -> 'i'
                    'ş' -> 's'
                    'ğ' -> 'g'
                    'ç' -> 'c'
                    'ö' -> 'o'
                    'ü', 'û' -> 'u'
                    'â' -> 'a'
                    else -> ch
                }
            )
        }
        return builder.toString().trim()
    }
}
