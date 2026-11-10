package com.kitsugi.animelist.model

import androidx.annotation.StringRes
import com.kitsugi.animelist.R

enum class MediaType {
    Anime,
    Manga,
    Movie,
    TvShow
}

/**
 * İzleme durumları — etiketler strings.xml üzerinden gelir (values / values-en);
 * böylece rozetler ve liste kartları uygulama dilini takip eder.
 * Gösterim için [com.kitsugi.animelist.ui.utils.localizedLabel] kullanılır.
 */
enum class WatchStatus(
    @StringRes val labelRes: Int
) {
    Watching(R.string.watch_watching),
    Completed(R.string.watch_completed),
    Planned(R.string.watch_planned),
    Dropped(R.string.watch_dropped),
    Paused(R.string.watch_paused),
    Repeating(R.string.watch_repeating);

    val label: String
        get() = when (this) {
            Watching -> "İzleniyor"
            Completed -> "Tamamlandı"
            Planned -> "Planlandı"
            Dropped -> "Bırakıldı"
            Paused -> "Beklemede"
            Repeating -> "Tekrar İzleniyor"
        }
}

data class MediaEntry(
    val id: Int,
    val title: String,
    val subtitle: String = "",
    val type: MediaType,
    val status: WatchStatus,
    val score: Int?,
    val progress: Int,
    val total: Int?,
    val isFavorite: Boolean = false,
    val isAdult: Boolean = false,
    val source: String = "manual",
    val malId: Int? = null,
    val imageUrl: String? = null,
    val year: Int? = null,
    val synopsis: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val notes: String? = null,
    val tags: String? = null,
    val priority: Int? = null,
    val isRepeating: Boolean = false,
    val repeatCount: Int = 0,
    val repeatValue: Int = 0,
    val volumeProgress: Int = 0,
    val isPrivate: Boolean = false,
    val isHiddenFromStatusLists: Boolean = false,
    val updatedAt: Long = 0L,
    val titleEnglish: String? = null,
    val titleJapanese: String? = null,
    val aniListEntryId: Int? = null,
    val malListId: Long? = null,
    val tmdbId: Int? = null,
    val simklId: Int? = null,
    /** Native TMDB Watchlist membership; null means unknown/not applicable to this row. */
    val tmdbWatchlist: Boolean? = null,
    /** Native TMDB rating (0.5..10); the shared Kitsugi score remains an Int. */
    val tmdbRating: Double? = null,
    /**
     * Kaydın listeye EKLENME zamanı (epoch saniye; 0 = bilinmiyor).
     *
     * "Son eklenen / İlk eklenen" sıralamasının tek doğru kaynağı budur: yerel
     * satır `id`'si yalnızca DB'ye yazım sırasıdır (toplu içe aktarımda API'nin
     * sayfa sırası!), `updatedAt` ise her ilerleme/puan değişiminde kayar.
     * Kaynak API eklenme zamanı veriyorsa (AniList `createdAt`, Kitsu
     * `createdAt`, Shikimori `created_at`) o yazılır; vermiyorsa ilk
     * görüldüğü an yerel olarak sabitlenir ve sonraki eşitlemelerde korunur.
     * Bilinçli olarak kurucunun SON parametresidir (konumsal kurulumlar kaymasın).
     */
    val addedAt: Long = 0L,
    /**
     * Liste'ye eklendiği an (**saniye** — [updatedAt] ile aynı birim).
     *
     * "Bugün / Bu Hafta …" varsayılan Listem görünümü ve "Eklenme tarihi" sıralaması bu
     * alanı kullanır. [addedAt] ile senkron çalışır.
     */
    val createdAt: Long = addedAt
)

/**
 * Zaman damgasını epoch SANİYEYE normalize eder.
 *
 * Tarihsel tutarsızlık: Bangumi içe aktarımı `updatedAt`'yi milisaniye,
 * diğer tüm kaynaklar saniye yazıyor. Sıralama/karşılaştırma anahtarları
 * normalize edilmezse Bangumi satırları (×1000 büyük değerleriyle) her
 * tarih sıralamasında haksız yere en üste/en alta yerleşir. Eşik: 10^11 —
 * saniye cinsinden 5138 yılı, milisaniye cinsinden 1973; iki birimi de
 * güvenle ayırır. 0 (bilinmiyor) aynen kalır.
 */
fun normalizeToEpochSeconds(value: Long): Long =
    if (value > 100_000_000_000L) value / 1000L else value

fun normalizeEpochSeconds(value: Long): Long = normalizeToEpochSeconds(value)
