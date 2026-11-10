package com.kitsugi.animelist.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import coil3.request.ImageRequest
import coil3.size.Precision
import kotlin.math.roundToInt

/**
 * Küçük önizleme (galeri şeritleri, "Resimler" sekmesi kartları, karakter/ekip/bölüm
 * küçük resimleri) için ağ + bellek dostu görsel istek politikası.
 *
 * `GalleryItem.url` gibi asıl model alanlarına DOKUNMAZ; böylece tam ekran galeri
 * yakınlaştırması ve indirme her zaman orijinal (4K / kayıpsız) dosyayı kullanmaya
 * devam ederken, 48–200 dp'lik küçük kutular 4–10 MB'lık `/t/p/original` veya dev PNG
 * dosyalarını tam boyutta çözüp UI iş parçacığını ve bellek önbelleğini kilitlemez.
 */
object GalleryThumbnailImagePolicy {

    private val TMDB_PATH_REGEX =
        Regex("https?://image\\.tmdb\\.org/t/p/(?:original|w\\d+|h\\d+)/", RegexOption.IGNORE_CASE)
    private val KITSU_POSTER_REGEX =
        Regex("(/poster_images/\\d+/)(?:original|large)(\\.[a-zA-Z0-9]+)", RegexOption.IGNORE_CASE)
    private val KITSU_COVER_REGEX =
        Regex("(/cover_images/\\d+/)(?:original|large)(\\.[a-zA-Z0-9]+)", RegexOption.IGNORE_CASE)
    private val BANGUMI_LARGE_COVER_REGEX =
        Regex("https?://lain\\.bgm\\.tv/pic/cover/l/", RegexOption.IGNORE_CASE)

    /**
     * Verilen URL'yi hedef kutu genişliğine ([targetWidthDp]) uygun CDN küçük resim
     * varyantına dönüştürür. Bilinmeyen veya özel CDN'lerde (ör. Fanart.tv) URL
     * korunur; boyut sınırı [rememberThumbnailImageRequest] içindeki `size(...)` ile
     * decode aşamasında uygulanır.
     */
    fun toThumbnailUrl(url: String?, targetWidthDp: Float): String? {
        val trimmed = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val normalized = if (trimmed.startsWith("//")) "https:$trimmed" else trimmed

        if (normalized.contains("image.tmdb.org/t/p/", ignoreCase = true)) {
            val tmdbSize = when {
                targetWidthDp <= 92f -> "w185"
                targetWidthDp <= 145f -> "w342"
                else -> "w500"
            }
            return TMDB_PATH_REGEX.replace(normalized, "https://image.tmdb.org/t/p/$tmdbSize/")
        }

        if (targetWidthDp <= 120f &&
            normalized.contains("anilist.co/", ignoreCase = true) &&
            normalized.contains("/cover/large/", ignoreCase = true)
        ) {
            return normalized.replace("/cover/large/", "/cover/medium/", ignoreCase = true)
        }

        if (targetWidthDp <= 160f &&
            normalized.contains("kitsu", ignoreCase = true) &&
            normalized.contains("/poster_images/", ignoreCase = true)
        ) {
            val sizeToken = if (targetWidthDp <= 85f) "small" else "medium"
            return KITSU_POSTER_REGEX.replace(normalized, "$1$sizeToken$2")
        }

        if (targetWidthDp <= 220f &&
            normalized.contains("kitsu", ignoreCase = true) &&
            normalized.contains("/cover_images/", ignoreCase = true)
        ) {
            return KITSU_COVER_REGEX.replace(normalized, "$1small$2")
        }

        if (targetWidthDp <= 160f && normalized.contains("lain.bgm.tv/pic/cover/l/", ignoreCase = true)) {
            return BANGUMI_LARGE_COVER_REGEX.replace(normalized, "https://lain.bgm.tv/r/400/pic/cover/l/")
        }

        if (targetWidthDp <= 140f &&
            normalized.contains("shikimori", ignoreCase = true) &&
            normalized.contains("/original/", ignoreCase = true)
        ) {
            return normalized.replaceFirst("/original/", "/preview/", ignoreCase = true)
        }

        return normalized
    }

    private fun quantizePx(rawPx: Int): Int {
        val clamped = rawPx.coerceIn(64, 768)
        return ((clamped + 15) / 16) * 16
    }

    @Composable
    fun rememberThumbnailImageRequest(
        url: String?,
        widthDp: Dp,
        heightDp: Dp
    ): ImageRequest? {
        if (url.isNullOrBlank()) return null
        val context = LocalContext.current
        val density = LocalDensity.current
        val widthPx = quantizePx(with(density) { (widthDp * 1.35f).toPx().roundToInt() })
        val heightPx = quantizePx(with(density) { (heightDp * 1.35f).toPx().roundToInt() })
        val widthDpValue = widthDp.value

        return remember(url, widthPx, heightPx, context) {
            val thumbUrl = toThumbnailUrl(url, widthDpValue) ?: return@remember null
            ImageRequest.Builder(context)
                .data(thumbUrl)
                .size(widthPx, heightPx)
                .precision(Precision.INEXACT)
                .memoryCacheKey("thumb_${widthPx}x${heightPx}_$thumbUrl")
                .build()
        }
    }
}
