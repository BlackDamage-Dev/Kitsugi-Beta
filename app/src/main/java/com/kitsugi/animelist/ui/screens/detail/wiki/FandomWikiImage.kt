package com.kitsugi.animelist.ui.screens.detail.wiki

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kitsugi.animelist.data.fandom.FandomUrls
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Icon

private const val TAG = "FandomWikiImage"

/**
 * Fandom CDN görseli için dayanıklı yükleyici.
 *
 * Deneme sırası: 1) küçültülmüş (`scale-to-width-down/N`) adres, 2) özgün adres,
 * 3) özgün adres + `format=original` (CDN içerik pazarlığını kapatır). Her adım
 * başarısız olursa bir sonrakine geçilir; hepsi düşerse kırık görsel simgesi gösterilir.
 * Hata nedeni logcat'e `FandomWikiImage` etiketiyle yazılır (teşhis için).
 */
@Composable
internal fun FandomWikiImage(
    url: String,
    width: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    accentColor: Color = KitsugiColors.TextMuted,
    iconSize: Dp = 22.dp,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val candidates = remember(url, width) { FandomImageCandidates.build(url, width) }
    var attempt by remember(url, width) { mutableIntStateOf(0) }
    var failed by remember(url, width) { mutableStateOf(false) }
    val current = candidates.getOrNull(attempt)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (failed || current == null) {
            Icon(
                imageVector = Icons.BrokenImage,
                contentDescription = contentDescription,
                tint = KitsugiColors.TextMuted,
                modifier = Modifier.size(iconSize)
            )
        } else {
            val request = remember(current) {
                ImageRequest.Builder(context)
                    .data(current)
                    .crossfade(false)
                    .build()
            }
            SubcomposeAsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    CircularProgressIndicator(
                        modifier = Modifier.size(iconSize),
                        color = accentColor,
                        strokeWidth = 2.dp
                    )
                },
                onError = { state ->
                    val error = state.result.throwable
                    Log.w(TAG, "Görsel yüklenemedi [${attempt + 1}/${candidates.size}] $current: ${error.message}")
                    if (attempt + 1 < candidates.size) attempt += 1 else failed = true
                },
                error = {
                    // Bir sonraki aday denenirken boş bırakılır; son adayda ikon [failed] ile gelir.
                    Box(modifier = Modifier.fillMaxSize())
                }
            )
        }
    }
}

/** Deneme adreslerini üretir (saf; birim testlenebilir). */
internal object FandomImageCandidates {
    fun build(url: String, width: Int): List<String> {
        val scaled = FandomUrls.scaledImage(url, width)
        val original = url
        val forced = FandomUrls.withOriginalFormat(url)
        return listOf(scaled, original, forced).distinct()
    }
}
