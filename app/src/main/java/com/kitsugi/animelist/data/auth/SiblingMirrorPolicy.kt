package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.WatchStatus

/**
 * KARDEŞ SATIR AYNASI — saf kural (yan etkisiz, test edilebilir).
 *
 * Aynı yapımın birden çok yerel satırı olduğunda (ör. AniList satırı + MAL satırı) kullanıcı
 * birini düzenlediğinde diğer satırlar da **kullanıcının o düzenlemesini** görmelidir. Bu,
 * listeyi tutarlı gösteren bir *önbellek* işlemidir; uzak eşitleme değildir.
 *
 * ### E57 — eski hâlin üç ölçülebilir hatası
 * 1. **İlerleme düşürülüyordu.** Kullanıcı 10 ilerlemedeki Kitsu satırını düzenleyip 11
 *    yaptığında, 12 ilerlemedeki AniList satırı da 11'e çekiliyordu. Birleştirme en yüksek
 *    ilerlemeyi aldığı için sonuç 11 oluyor ve "ilerleme asla gerilemez" kuralı **yerel veri
 *    katmanında** delinip AniList'e 12 → 11 geri yazımı üretiliyordu (E71'in kardeşi).
 * 2. **Durum, düzenlenmeyen bir alan yüzünden değişiyordu.** Yalnız puan düzenlendiğinde
 *    `status` de kopyalanıyor, böylece `Dropped`/`Paused` gibi "yapışkan" kararlar ve
 *    tamamlanmış kayıtlar sessizce başka bir duruma çekiliyordu.
 * 3. **`updatedAt` eziliyordu.** Ayna, hiçbir alanı değiştirmediği hâlde satırı "az önce
 *    kullanıcı düzenledi" gibi damgalıyor; `CrossSyncMergePolicy`'nin "en yeni kazanır"
 *    kuralı bu yüzden yanlış satırı seçebiliyordu.
 *
 * ### Yeni kural
 * - Ayna yalnızca **gerçekten değişen alanları** taşır (`changed`); dokunulmayan alanlar
 *   kardeş satırda olduğu gibi kalır.
 * - **Monotonluk:** ilerleme, cilt ilerlemesi, tekrar sayısı ve tekrar değeri asla düşürülmez
 *   (birleştirme kuralıyla aynı yön).
 * - **Yapışkanlık:** kullanıcı durumu açıkça değiştirmediyse `Dropped`/`Paused` satırın durumu
 *   ezilmez; ilerlemesi olan bir satır `Planned`a çekilmez.
 * - **Puan silme yayılmaz:** `null` puan kardeş satıra yazılmaz — bir kaynağın gerçek puanı
 *   yerelde silinirse bir sonraki birleştirme o kaynağa başka bir puanı yazar (veri kaybı).
 * - **`updatedAt` korunur:** pasif bir ayna satırı "az önce düzenlendi" gibi gösteremez.
 */
object SiblingMirrorPolicy {

    /** [sibling] satırının, [entry] üzerindeki [changed] alanlarıyla aynalanmış yeni hâli. */
    fun mirrored(
        sibling: MediaEntry,
        entry: MediaEntry,
        changed: Set<SyncField>
    ): MediaEntry {
        if (sibling.id == entry.id) return sibling
        if (changed.isEmpty()) return sibling

        // `SyncField.ALL` bir kümedir; "tam yazım" hem tüm alanları içeren küme hem de
        // `ALL`'ın kendisinin geçirilmesi olarak gelebilir.
        val full = changed.containsAll(SyncField.ALL)
        fun touches(field: SyncField) = full || changed.contains(field)

        // Kullanıcı DURUMU açıkça değiştirdiyse (tam yazım değilse) kararı aynen taşırız:
        // açık bir kullanıcı işlemi yapışkanlık kuralını da ezer.
        val explicitStatus = !full && changed.contains(SyncField.STATUS)
        val statusRelated = touches(SyncField.STATUS) || touches(SyncField.REWATCH_COUNT)

        var out = sibling

        if (statusRelated) {
            val nextStatus = when {
                explicitStatus -> entry.status
                sibling.status == entry.status -> sibling.status
                // Yapışkan kararlar: kullanıcı bunları başka bir alanı düzenleyerek kaybetmez.
                sibling.status == WatchStatus.Dropped ||
                    sibling.status == WatchStatus.Paused -> sibling.status
                // İlerlemesi olan bir kayıt "planlandı"ya geri çekilmez.
                entry.status == WatchStatus.Planned && sibling.progress > 0 -> sibling.status
                else -> entry.status
            }
            out = out.copy(status = nextStatus)
        }

        if (touches(SyncField.PROGRESS)) {
            out = out.copy(progress = maxOf(sibling.progress, entry.progress))
        }
        if (touches(SyncField.VOLUME_PROGRESS)) {
            out = out.copy(volumeProgress = maxOf(sibling.volumeProgress, entry.volumeProgress))
        }
        if (touches(SyncField.REWATCH_COUNT)) {
            out = out.copy(
                repeatCount = maxOf(sibling.repeatCount, entry.repeatCount),
                isRepeating = when {
                    explicitStatus -> entry.isRepeating
                    entry.isRepeating -> true
                    // Tekrar bitirildiyse (kullanıcı daha yeni) bayat bayrak temizlenir;
                    // kardeş satır daha yeniyse açık tekrar korunur (E67).
                    sibling.isRepeating && sibling.updatedAt > entry.updatedAt -> true
                    else -> false
                }
            )
        }
        if (touches(SyncField.REPEAT_VALUE)) {
            out = out.copy(repeatValue = maxOf(sibling.repeatValue, entry.repeatValue))
        }

        // Puan: `null` (temizleme) taşınmaz — bkz. sınıf dokümanı ve E41.
        if (touches(SyncField.SCORE) && entry.score != null) {
            out = out.copy(score = entry.score)
        }

        if (touches(SyncField.START_DATE)) {
            out = out.copy(
                startDate = if (full) earliest(sibling.startDate, entry.startDate) else entry.startDate
            )
        }
        if (touches(SyncField.END_DATE)) {
            out = out.copy(
                endDate = if (full) latest(sibling.endDate, entry.endDate) else entry.endDate
            )
        }

        // Serbest metin / bayrak alanları: yalnız değiştiğinde taşınır. Tam yazımda boş değer,
        // kardeş satırın dolu değerini silmesin (yeni satır eklemek başka kaynağın notunu
        // silmemeli); açık değişiklikte boşaltma kullanıcı niyetidir ve taşınır.
        if (touches(SyncField.NOTES)) {
            out = out.copy(notes = text(entry.notes, sibling.notes, full))
        }
        if (touches(SyncField.TAGS)) {
            out = out.copy(tags = text(entry.tags, sibling.tags, full))
        }
        if (touches(SyncField.PRIORITY) && (entry.priority != null || !full)) {
            out = out.copy(priority = entry.priority)
        }
        if (touches(SyncField.FAVORITE)) {
            out = out.copy(isFavorite = sibling.isFavorite || entry.isFavorite)
        }
        if (touches(SyncField.PRIVATE) && (full || entry.isPrivate != sibling.isPrivate)) {
            out = out.copy(isPrivate = if (full) sibling.isPrivate || entry.isPrivate else entry.isPrivate)
        }
        if (touches(SyncField.HIDDEN_FROM_STATUS_LISTS) &&
            (full || entry.isHiddenFromStatusLists != sibling.isHiddenFromStatusLists)
        ) {
            out = out.copy(
                isHiddenFromStatusLists = if (full) {
                    sibling.isHiddenFromStatusLists || entry.isHiddenFromStatusLists
                } else {
                    entry.isHiddenFromStatusLists
                }
            )
        }

        // `updatedAt` bilinçli olarak KOPYALANMAZ: pasif ayna satırı "az önce düzenlendi"
        // görünürse birleştirmenin "en yeni kazanır" kuralı yanlış satırı seçer (E57/3).
        return out
    }

    private fun text(next: String?, current: String?, full: Boolean): String? =
        if (full && next.isNullOrBlank()) current else next

    private fun earliest(a: String?, b: String?): String? {
        val left = a?.takeIf(String::isNotBlank)
        val right = b?.takeIf(String::isNotBlank)
        return when {
            left == null -> right
            right == null -> left
            else -> minOf(left, right)
        }
    }

    private fun latest(a: String?, b: String?): String? {
        val left = a?.takeIf(String::isNotBlank)
        val right = b?.takeIf(String::isNotBlank)
        return when {
            left == null -> right
            right == null -> left
            else -> maxOf(left, right)
        }
    }
}
