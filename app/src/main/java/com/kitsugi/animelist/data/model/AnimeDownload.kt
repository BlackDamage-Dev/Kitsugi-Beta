package com.kitsugi.animelist.data.model

import androidx.compose.runtime.Immutable
import com.kitsugi.animelist.core.player.SubtitleInput

enum class DownloadKind {
    VIDEO,
    AUDIO
}

@Immutable
data class AnimeDownload(
    val animeId: String,
    val animeTitle: String,
    val posterUrl: String?,
    val episode: Int,
    val season: Int,
    val url: String,
    val quality: String,
    /** HTTP request headers to use when downloading (e.g. Cookie, Referer, Origin, User-Agent from CS3 addons). */
    val requestHeaders: Map<String, String> = emptyMap(),
    val subtitles: List<SubtitleInput> = emptyList(),
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

    // ── Evrensel Ses ve P2P İndirmeleri ──────────────────────────────────────
    val downloadKind: DownloadKind? = DownloadKind.VIDEO,
    val externalAudioUrl: String? = null,
    val externalAudioTracks: List<ExternalAudioTrack>? = emptyList(),
    val audioTrackKey: String? = "",
    val audioTrackLabel: String? = "",
    val audioTrackLang: String? = "",
    val audioTrackIndex: Int = -1,
    val isCloudStream: Boolean = false,
    val qualityValue: Int? = null,
    val p2pInfoHash: String? = null,
    val p2pFileIdx: Int? = null,
    val p2pTrackers: List<String>? = emptyList(),

    /** Kullanıcıya gösterilen son hata nedeni (yalnızca [Status.ERROR]). */
    val errorMessage: String? = null,
    /**
     * Kaynak hız sınırı (HTTP 429) nedeniyle durdurulduysa, tekrar denenebilecek
     * en erken zaman (epoch ms). UI "birazdan devam edecek" bilgisini gösterebilir.
     */
    val retryAtMs: Long? = null,
    /**
     * Hız sınırı nedeniyle otomatik yapılan yeniden deneme sayısı.
     *
     * Sonsuz döngüyü engeller: kaynak sürekli 429 veriyorsa (ör. IP bloklu)
     * 3 otomatik denemeden sonra iş `ERROR` olarak kalır ve kullanıcıya neden
     * gösterilir.
     */
    val autoRetryCount: Int = 0
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

    /** Gson eski kayıtları okurken yeni enum alanını null bırakabilir; her zaman güvenli dön. */
    val effectiveKind: DownloadKind
        get() = downloadKind ?: DownloadKind.VIDEO

    val isAudioOnly: Boolean
        get() = effectiveKind == DownloadKind.AUDIO

    val safeHeaders: Map<String, String>
        get() = requestHeaders ?: emptyMap()

    val safeSubtitles: List<SubtitleInput>
        get() = subtitles ?: emptyList()

    val safeExternalAudioTracks: List<ExternalAudioTrack>
        get() {
            val tracks = externalAudioTracks ?: emptyList()
            if (tracks.isNotEmpty()) return tracks
            val single = externalAudioUrl?.trim()?.takeIf { it.isNotBlank() } ?: return emptyList()
            return listOf(
                ExternalAudioTrack.fromUrlAndHeaders(
                    url = single,
                    fallbackHeaders = safeHeaders
                )
            )
        }

    val safeAudioTrackKey: String
        get() = (audioTrackKey ?: "").ifBlank { "default" }

    val safeAudioTrackLabel: String
        get() = audioTrackLabel ?: ""

    val safeAudioTrackLang: String
        get() = audioTrackLang ?: ""

    /**
     * Gson `Unsafe` ile eski sürümden yüklenen kayıtlarda null kalabilecek tüm koleksiyon ve
     * enum alanlarını güvenli varsayılanlarla yeniden kurar (`.copy(...)` NPE'sini önler).
     */
    @Suppress("SENSELESS_COMPARISON")
    fun normalized(): AnimeDownload = AnimeDownload(
        animeId = animeId ?: "",
        animeTitle = animeTitle ?: "Video",
        posterUrl = posterUrl,
        episode = episode,
        season = season,
        url = url ?: "",
        quality = quality ?: "",
        requestHeaders = safeHeaders,
        subtitles = safeSubtitles,
        status = status ?: Status.QUEUE,
        progress = progress,
        totalBytes = totalBytes,
        downloadedBytes = downloadedBytes,
        localPath = localPath,
        createdAt = if (createdAt > 0L) createdAt else System.currentTimeMillis(),
        downloadedSegments = downloadedSegments,
        malId = malId,
        aniListId = aniListId,
        tmdbId = tmdbId,
        source = source,
        streamTitle = streamTitle,
        streamName = streamName,
        kind = kind ?: Kind.EPISODE,
        clipKey = clipKey,
        clipTitle = clipTitle,
        clipBaseName = clipBaseName,
        downloadKind = effectiveKind,
        externalAudioUrl = externalAudioUrl,
        externalAudioTracks = externalAudioTracks ?: emptyList(),
        audioTrackKey = audioTrackKey ?: "",
        audioTrackLabel = audioTrackLabel ?: "",
        audioTrackLang = audioTrackLang ?: "",
        audioTrackIndex = audioTrackIndex,
        isCloudStream = isCloudStream,
        qualityValue = qualityValue,
        p2pInfoHash = p2pInfoHash,
        p2pFileIdx = p2pFileIdx,
        p2pTrackers = p2pTrackers ?: emptyList(),
        errorMessage = errorMessage,
        retryAtMs = retryAtMs,
        autoRetryCount = autoRetryCount
    )

    /**
     * Kuyruktaki benzersiz anahtar:
     * - Video: `video:<animeId>:<episode>`
     * - Bağımsız ses: `audio:<animeId>:<episode>:<audioTrackKey>`
     */
    val uniqueKey: String
        get() = when {
            isClip -> clipKey?.takeIf { it.isNotBlank() } ?: "clip:$animeId:$episode"
            effectiveKind == DownloadKind.AUDIO -> audioKey(animeId, episode, safeAudioTrackKey)
            else -> videoKey(animeId, episode)
        }

    companion object {
        fun videoKey(animeId: String, episode: Int): String = "video:$animeId:$episode"

        fun audioKey(animeId: String, episode: Int, trackKey: String = "default"): String =
            "audio:$animeId:$episode:${trackKey.ifBlank { "default" }}"
    }
}

