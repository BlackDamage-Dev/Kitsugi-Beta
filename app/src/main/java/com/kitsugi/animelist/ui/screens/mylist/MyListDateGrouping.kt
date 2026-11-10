package com.kitsugi.animelist.ui.screens.mylist

import androidx.annotation.StringRes
import com.kitsugi.animelist.R
import com.kitsugi.animelist.model.MediaEntry
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Listem'in "eklenme tarihi" bölümleri.
 *
 * Sıralama çipleri "Son eklenen / İlk eklenen" seçiliyken liste, durum grupları yerine
 * kullanıcının yapımı listesine **ne zaman eklediğine** göre bölümlenir:
 *
 * ```
 * Bugün
 * Dün
 * Bu Hafta
 * Geçen Hafta
 * Daha Eski
 * Eklendiği Tarih Bilinmiyor
 * ```
 *
 * Kök neden notu: bölümler daha önce yerel satır `id`'sine (autoincrement) göre
 * hesaplanıyordu. Her içe aktarma satırları sildiği/yeniden eklediği için aynı yapım bir
 * sekmede "Bugün", diğerinde "Dün" görünüyordu. Artık tek kaynak
 * [MediaEntry.addedAt] (sağlayıcının `created_at` / `added_to_watchlist_at` değeri).
 */
internal enum class LegacyMyListDateBucket(@StringRes val labelRes: Int) {
    TODAY(R.string.mylist_date_today),
    YESTERDAY(R.string.mylist_date_yesterday),
    THIS_WEEK(R.string.mylist_date_this_week),
    LAST_WEEK(R.string.mylist_date_last_week),
    OLDER(R.string.mylist_date_older),
    UNKNOWN(R.string.mylist_date_unknown)
}

/** Hafta başlangıcı Pazartesi'dir (ISO-8601, AniHyou/AniList alışkanlığı). */
private val DESCENDING_BUCKETS = listOf(
    LegacyMyListDateBucket.TODAY,
    LegacyMyListDateBucket.YESTERDAY,
    LegacyMyListDateBucket.THIS_WEEK,
    LegacyMyListDateBucket.LAST_WEEK,
    LegacyMyListDateBucket.OLDER,
    LegacyMyListDateBucket.UNKNOWN
)

private val ASCENDING_BUCKETS = listOf(
    LegacyMyListDateBucket.OLDER,
    LegacyMyListDateBucket.LAST_WEEK,
    LegacyMyListDateBucket.THIS_WEEK,
    LegacyMyListDateBucket.YESTERDAY,
    LegacyMyListDateBucket.TODAY,
    LegacyMyListDateBucket.UNKNOWN
)

/** "Son eklenen / İlk eklenen" ailesindeki sıralama kimlikleri. */
internal fun usesAddedDateGrouping(sortId: String): Boolean = when (sortId) {
    "newest", "oldest", "added", "added_desc", "added_asc" -> true
    else -> false
}

/**
 * Bir zaman damgasının (epoch saniye) hangi bölüme düştüğünü söyler.
 *
 * `timestampSec <= 0` bilinmeyen demektir. Gelecekteki damgalar (cihaz saati kayması)
 * "Bugün" sayılır: kullanıcıyı "Daha Eski" bölümüne atan bir saat kayması göstermeyiz.
 */
internal fun myListDateBucketOf(
    timestampSec: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(zone)
): LegacyMyListDateBucket {
    if (timestampSec <= 0L) return LegacyMyListDateBucket.UNKNOWN
    val date = runCatching {
        Instant.ofEpochSecond(timestampSec).atZone(zone).toLocalDate()
    }.getOrNull() ?: return LegacyMyListDateBucket.UNKNOWN

    if (!date.isBefore(today)) return LegacyMyListDateBucket.TODAY
    if (date == today.minusDays(1)) return LegacyMyListDateBucket.YESTERDAY

    val thisWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    if (!date.isBefore(thisWeekStart)) return LegacyMyListDateBucket.THIS_WEEK

    val lastWeekStart = thisWeekStart.minusWeeks(1)
    if (!date.isBefore(lastWeekStart)) return LegacyMyListDateBucket.LAST_WEEK

    return LegacyMyListDateBucket.OLDER
}

/**
 * Satırları eklenme tarihi bölümlerine ayırır. Girdi sırası korunur; bölümler
 * [ascending] parametresine göre kronolojik/yeni-önce sıralanır. Boş bölüm üretilmez.
 */
internal fun groupMyListEntriesByAddedDate(
    entries: List<MediaEntry>,
    ascending: Boolean = false,
    zone: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(zone)
): List<Pair<LegacyMyListDateBucket, List<MediaEntry>>> {
    if (entries.isEmpty()) return emptyList()

    val buckets = LinkedHashMap<LegacyMyListDateBucket, MutableList<MediaEntry>>()
    entries.forEach { entry ->
        val bucket = myListDateBucketOf(addedSortTimestamp(entry), zone, today)
        buckets.getOrPut(bucket) { mutableListOf() }.add(entry)
    }

    val order = if (ascending) ASCENDING_BUCKETS else DESCENDING_BUCKETS
    return order.mapNotNull { bucket ->
        buckets[bucket]?.takeIf { it.isNotEmpty() }?.let { bucket to it.toList() }
    }
}
