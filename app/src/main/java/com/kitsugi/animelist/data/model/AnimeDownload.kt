package com.kitsugi.animelist.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class AnimeDownload(
    val animeId: String,
    val animeTitle: String,
    val posterUrl: String?,
    val episode: Int,
    val season: Int,
    val url: String,
    val quality: String,
    /** HTTP request headers to use when downloading (e.g. Cookie, Referer, User-Agent from CS3 addons). */
    val requestHeaders: Map<String, String> = emptyMap(),
    val subtitles: List<com.kitsugi.animelist.core.player.SubtitleInput> = emptyList(),
    val status: Status = Status.QUEUE,
    val progress: Int = 0,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val localPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val downloadedSegments: Int = 0,
    val malId: Int? = null,
    val aniListId: Int? = null,
    val tmdbId: Int? = null,
    val source: String? = null,
    val streamTitle: String? = null,
    val streamName: String? = null,

    // ── Küçük video (fragman / kamera arkası / tema) indirmeleri ──────────────
    /**
     * İndirme türü. Varsayılan [Kind.EPISODE] → eski kayıtlar (Gson ile okunan) aynen
     * bölüm indirmesi olarak davranır; şema geriye dönük uyumludur.
     */
    val kind: Kind = Kind.EPISODE,
    /**
     * Detay sayfasındaki küçük videonun **tekil kimliği** (`clip|yt:<videoId>` gibi).
     * Yalnızca videonun kendisinden üretilir; aynı video farklı bağlantı biçimleriyle veya
     * farklı sekmelerden istendiğinde bu anahtar üzerinden var olan kayıt bulunur.
     */
    val clipKey: String? = null,
    /** Detay sayfasında görünen video adı (ör. "Kamera Arkası - Behind the Scenes"). */
    val clipTitle: String? = null,
    /**
     * Klip dosyasının **uzantısız** taban adı (`<Başlık>_<Video>_1`). Uzantı indirme sırasında
     * kaynağa göre belirlenir (mp4 / webm / ts); silme ve listeleme bu taban adı kullanır.
     */
    val clipBaseName: String? = null,
    /** Kullanıcıya gösterilen son hata nedeni (yalnızca [Status.ERROR]). */
    val errorMessage: String? = null
) {
    enum class Status {
        QUEUE,
        DOWNLOADING,
        COMPLETED,
        ERROR,
        PAUSED
    }

    enum class Kind {
        /** Bölüm indirmesi (mevcut akış). */
        EPISODE,
        /** Detay sayfasındaki küçük video (fragman, kamera arkası, tema…). */
        CLIP
    }

    /** Bu kayıt bir küçük video mu? UI ve silme akışı bunu kullanır. */
    val isClip: Boolean get() = kind == Kind.CLIP
}

