package com.kitsugi.animelist.data.auth

import java.util.concurrent.CancellationException

/** Cancellation is control flow, never an offline-sync error to retry. */
inline fun <T> runSyncCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}

object SyncScores {
    /** MediaEntry stores an integer 0..10; AniList scoreRaw is independent of profile format. */
    fun aniListRaw(score: Int?): Int = (score ?: 0).coerceIn(0, 10) * 10
    fun tenPoint(score: Int?): Int? = score?.takeIf { it > 0 }?.let {
        if (it > 10) kotlin.math.round(it / 10.0).toInt().coerceIn(1, 10) else it.coerceIn(1, 10)
    }
    fun kitsuTwenty(score: Int?): Int? = tenPoint(score)?.times(2)
}

/**
 * Tarih alanlarının yazma niyeti.
 *
 * **Neden gerekli (E63):** "tarih ayrıştırılamadı" ile "tarih boşaltıldı" çok farklı iki
 * durumdur ama ikisi de `parse(...) == null` üretir. Eski yazıcılar (`AniList`, `Kitsu`)
 * ayrıştırılamayan değeri (ör. içe aktarmadan sızmış `"2016"`, `"2016-05"`) "boşaltıldı"
 * sanıp hedefteki tarihi SİLİYORDU — kullanıcı tarihe dokunmadığı hâlde. Bu yardımcı,
 * kararı tek yerde toplar:
 *
 *  - [Action.WRITE] : ayrıştırıldı → değer gönderilir
 *  - [Action.CLEAR] : alan GERÇEKTEN boş ve tam yazım değil → hedefte temizlenir
 *  - [Action.SKIP]  : parsiyel/geçersiz değer ya da tam yazımda boş → GÖNDERİLMEZ
 *                     (uzaktaki tarih korunur)
 *
 * Tam yazımda (`fullWrite`) boş alan temizleme göndermez: yeni kayıtta silinecek bir şey
 * yoktur, mevcut kayıtta ise yerel boşluğun uzağı ezmesi veri kaybıdır.
 */
object SyncDates {
    enum class Action { WRITE, CLEAR, SKIP }

    fun action(rawDate: String?, parsed: Any?, fullWrite: Boolean): Action = when {
        parsed != null -> Action.WRITE
        rawDate.isNullOrBlank() && !fullWrite -> Action.CLEAR
        else -> Action.SKIP
    }
}
