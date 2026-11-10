package com.kitsugi.animelist.ui.screens.mylist

import androidx.annotation.StringRes
import com.kitsugi.animelist.R
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.WatchStatus
import java.util.Calendar
import java.util.Locale

/**
 * Listem ekranının **gruplama** ekseni.
 *
 * Gruplama ile sıralama birbirinden BAĞIMSIZDIR: sıralama seçimi (Eklenme tarihi, Puan, Ada
 * göre …) yalnızca bölümlerin **içindeki** dizilişi değiştirir; bölüm başlıkları yerinde
 * kalır. Böylece "varsayılan görünüm" (eklenme tarihine göre Bugün / Bu Hafta …) ile
 * sıralamalar birlikte açık kalabilir — eskiden biri diğerini ezip listeyi düz akışa
 * çeviriyordu.
 */
object MyListGroupingIds {
    /** Varsayılan görünüm: eklenme tarihine göre Bugün / Dün / Bu Hafta … bölümleri. */
    const val ADDED = "added"

    /** İzleme durumuna göre bölümler (İzleniyor / Planlandı / İzlendi …). */
    const val STATUS = "status"

    /** Gruplama yok: tek düz akış. */
    const val NONE = "none"

    val all: List<String> = listOf(ADDED, STATUS, NONE)

    fun normalize(value: String?): String {
        val cleaned = value?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return if (cleaned in all) cleaned else ADDED
    }
}

/** Eklenme tarihi kovaları — sıra gösterim sırasıdır (en yeni önce). */
enum class MyListDateBucket(val id: String, @StringRes val titleRes: Int) {
    TODAY("today", R.string.mylist_bucket_today),
    YESTERDAY("yesterday", R.string.mylist_bucket_yesterday),
    THIS_WEEK("week", R.string.mylist_bucket_this_week),
    THIS_MONTH("month", R.string.mylist_bucket_this_month),
    THIS_YEAR("year", R.string.mylist_bucket_this_year),
    EARLIER("earlier", R.string.mylist_bucket_earlier),
    UNKNOWN("unknown", R.string.mylist_bucket_unknown)
}

/** Durum gruplarının gösterim sırası (eskiden beri aynı). */
val myListStatusOrder: List<WatchStatus> = listOf(
    WatchStatus.Watching,
    WatchStatus.Repeating,
    WatchStatus.Planned,
    WatchStatus.Paused,
    WatchStatus.Dropped,
    WatchStatus.Completed
)

/**
 * Liste'nin bir bölümü. [status] dolu → durum grubu, [bucket] dolu → tarih kovası,
 * ikisi de boşsa bölüm başlıksızdır (gruplama kapalı).
 */
data class MyListSection(
    val id: String,
    val entries: List<MediaEntry>,
    val status: WatchStatus? = null,
    val bucket: MyListDateBucket? = null
) {
    val isHeaderless: Boolean get() = status == null && bucket == null
}

/**
 * Kaydın listeye eklendiği an (milisaniye).
 *
 * Sırayla [MediaEntry.createdAt] (saniye), [MediaEntry.updatedAt] (saniye) ve **0**
 * kullanılır. Eski satırlarda `createdAt` yoktur (v29 → v30 göçünde `updatedAt` ile
 * dolduruldu); `updatedAt` o güne kadarki en iyi yaklaşımdır. İkisi de sıfırsa çağıran
 * kayıt kimliğine (ekleme sırası) düşer.
 */
internal fun MediaEntry.addedAtMillis(): Long {
    val seconds = createdAt.takeIf { it > 0L } ?: addedAt.takeIf { it > 0L } ?: updatedAt.takeIf { it > 0L } ?: 0L
    if (seconds <= 0L) return 0L
    // Bazı eski içe aktarımlar alanı milisaniye olarak yazmış olabilir; saniye değeri
    // 2286'dan büyük olamayacağı için büyük değerler milisaniye kabul edilir.
    return if (seconds > 10_000_000_000L) seconds else seconds * 1000L
}

/** Kaydın düştüğü tarih kovası. */
internal fun myListDateBucket(
    entry: MediaEntry,
    nowMillis: Long = System.currentTimeMillis()
): MyListDateBucket {
    val added = entry.addedAtMillis()
    if (added <= 0L) return MyListDateBucket.UNKNOWN
    // Saat kayması / gelecek tarihli kayıtlar en üstte (Bugün) kalır.
    if (added > nowMillis) return MyListDateBucket.TODAY

    val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val then = Calendar.getInstance().apply { timeInMillis = added }
    val dayDiff = ((startOfDay(nowMillis) - startOfDay(added)) / 86_400_000L).toInt()
    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val sameMonth = sameYear && now.get(Calendar.MONTH) == then.get(Calendar.MONTH)

    return when {
        dayDiff <= 0 -> MyListDateBucket.TODAY
        dayDiff == 1 -> MyListDateBucket.YESTERDAY
        dayDiff < 7 -> MyListDateBucket.THIS_WEEK
        sameMonth -> MyListDateBucket.THIS_MONTH
        sameYear -> MyListDateBucket.THIS_YEAR
        else -> MyListDateBucket.EARLIER
    }
}

private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/**
 * (Sıralanmış) kayıtları seçilen gruplamaya göre bölümlere ayırır. Boş bölüm üretilmez ve
 * girdi sırası **korunur** — böylece aktif sıralama her bölümün içinde geçerli olur.
 */
fun groupMyListEntries(
    entries: List<MediaEntry>,
    groupId: String,
    nowMillis: Long = System.currentTimeMillis()
): List<MyListSection> {
    if (entries.isEmpty()) return emptyList()
    return when (MyListGroupingIds.normalize(groupId)) {
        MyListGroupingIds.NONE -> listOf(MyListSection(id = "all", entries = entries))
        MyListGroupingIds.STATUS -> {
            val order = myListStatusOrder
            order.mapNotNull { status ->
                val bucket = entries.filter { it.status == status }
                if (bucket.isEmpty()) null
                else MyListSection(id = status.name, entries = bucket, status = status)
            }
        }
        else -> {
            val byBucket = entries.groupBy { myListDateBucket(it, nowMillis) }
            // Tarihi bilinmeyen kayıtlar listenin sonunda tek bir bölümde toplanır.
            MyListDateBucket.entries.mapNotNull { dateBucket ->
                val bucket = byBucket[dateBucket] ?: return@mapNotNull null
                MyListSection(id = dateBucket.id, entries = bucket, bucket = dateBucket)
            }
        }
    }
}
