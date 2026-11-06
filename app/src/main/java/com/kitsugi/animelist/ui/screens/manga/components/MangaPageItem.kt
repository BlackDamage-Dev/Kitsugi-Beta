package com.kitsugi.animelist.ui.screens.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Scale
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.diagnostics.PlatformErrorCode
import com.kitsugi.animelist.data.manga.ColorFilterType
import com.kitsugi.animelist.data.manga.MangaFitMode
import com.kitsugi.animelist.data.manga.MangaPage
import com.kitsugi.animelist.data.manga.MangaPageStatus
import com.kitsugi.animelist.data.manga.ReadingMode
import com.kitsugi.animelist.data.manga.loader.MangaPageLoaderV2
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

/**
 * Tek bir manga sayfası — hem yatay/dikey sayfalama hem webtoon modunda kullanılır.
 *
 * Özellikler
 * ----------
 *  • Telephoto `ZoomableAsyncImage`: pinch-to-zoom, çift dokunma ile yakınlaştırma,
 *    dev görsellerde kiremit (subsampling) çizim → OOM riski yok.
 *  • Dokunma bölgeleri (Mihon paritesi): sol/orta/sağ. Yön okuma moduna göre
 *    otomatik: RTL'de sol = ileri, LTR'de sağ = ileri. `invertTapZones` ile ters çevrilebilir.
 *  • Uzun basma → okuyucu menüsü/ayarları (Mihon "show on long tap").
 *  • Yatay aynalama (`mirrorPages`): aynalanmış servis edilen sayfaları düzeltir.
 *  • Sığdırma modları: FitScreen / FitWidth / FitHeight / Crop / Original.
 *  • Renk filtreleri: Normal / Warm / Cool / Grayscale / Sepia / Invert.
 *  • Sağ altta sayfa numarası rozeti (kapatılabilir).
 */
@Composable
fun MangaPageItem(
    page: MangaPage,
    loader: MangaPageLoaderV2,
    readingMode: ReadingMode = ReadingMode.RightToLeft,
    colorFilterType: ColorFilterType = ColorFilterType.Normal,
    fitMode: MangaFitMode = MangaFitMode.FitScreen,
    cropBorders: Boolean = false,
    mirrorPages: Boolean = false,
    showPageNumber: Boolean = true,
    tapZonesEnabled: Boolean = true,
    invertTapZones: Boolean = false,
    onCenterTap: () -> Unit = {},
    onLongPress: () -> Unit = {},
    /** Sayfaya uzun basınca (kullanıcı isteği: "resimler tıklanabilir olsun") galeri açılır. */
    onLongPressPage: ((Int) -> Unit)? = null,
    onNextPage: () -> Unit = {},
    onPrevPage: () -> Unit = {},
    onSourceLogin: (() -> Unit)? = null,
    /** Webtoon'da yer tutucu boyutu için paylaşılan oran hafızası. */
    sizeMemory: MangaPageSizeMemory? = null,
    modifier: Modifier = Modifier,
) {
    val isScrollMode = readingMode == ReadingMode.Webtoon
    val haptics = LocalHapticFeedback.current

    // Sayfa nesnesi ilk kez görüldüğünde yüklemeyi başlat.
    LaunchedEffect(page, loader) {
        // Cache maintenance may have evicted a page which was already marked Ready.
        val imageUrl = page.imageUrl
        val missing = page.status == MangaPageStatus.Ready && imageUrl != null &&
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                !loader.cache.getImageFile(loader.cacheKey(page, imageUrl)).isFile
            }
        if (missing) loader.retryPage(page) else loader.loadPage(page)
    }

    val rootModifier = if (isScrollMode) {
        modifier.fillMaxWidth().wrapContentHeight()
    } else {
        modifier.fillMaxSize()
    }

    Box(
        modifier = rootModifier
            .background(Color.Black)
            .readerTapZones(
                readingMode = readingMode,
                tapZonesEnabled = tapZonesEnabled,
                invertTapZones = invertTapZones,
                haptics = haptics,
                onCenterTap = onCenterTap,
                onLongPress = {
                    onLongPress()
                    onLongPressPage?.invoke(page.index)
                },
                onNextPage = onNextPage,
                onPrevPage = onPrevPage,
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (page.status) {
            MangaPageStatus.Ready -> {
                val imageUrl = page.imageUrl ?: page.url
                val cacheFile = loader.cache.getImageFile(loader.cacheKey(page, imageUrl))
                val modelToLoad: Any = cacheFile

                val context = LocalContext.current
                val displayError = remember(page, modelToLoad) { mutableStateOf<Throwable?>(null) }
                val retryGeneration = remember(page, modelToLoad) { mutableIntStateOf(0) }
                val imageRequest = remember(modelToLoad, retryGeneration.intValue) {
                    ImageRequest.Builder(context)
                        .data(modelToLoad)
                        // Telephoto's subsampling supplies detail for supported formats. Keep
                        // Coil's preview bounded for very tall pages and older GPU canvases.
                        .size(2048, 2048)
                        .scale(Scale.FIT)
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .crossfade(200)
                        .listener(
                            onError = { _, result -> displayError.value = result.throwable },
                            onSuccess = { _, _ -> displayError.value = null }
                        )
                        .build()
                }

                val contentScale = fitMode.toContentScale()
                val composeColorFilter = colorFilterType.toComposeColorFilter()

                if (displayError.value == null) {
                    key(modelToLoad, retryGeneration.intValue) {
                        val zoomableState = rememberZoomableState()
                        val imageState = rememberZoomableImageState(zoomableState)

                        val imageModifier = (if (isScrollMode) {
                            Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .onSizeChanged { size -> sizeMemory?.record(size.width, size.height) }
                        } else {
                            Modifier.fillMaxSize()
                        }).then(
                            if (mirrorPages) Modifier.graphicsLayer { scaleX = -1f } else Modifier
                        ).then(
                            if (cropBorders) {
                                Modifier
                                    .graphicsLayer {
                                        scaleX = 1.04f
                                        scaleY = 1.04f
                                    }
                                    .clipToBounds()
                            } else {
                                Modifier
                            }
                        )

                        ZoomableAsyncImage(
                            model = imageRequest,
                            contentDescription = stringResource(R.string.manga_sayfasi_x, page.index + 1),
                            state = imageState,
                            contentScale = contentScale,
                            colorFilter = composeColorFilter,
                            modifier = imageModifier,
                        )
                    }
                } else {
                    ErrorPageContent(
                        pageIndex = page.index,
                        isScrollMode = isScrollMode,
                        placeholderAspect = sizeMemory?.aspectRatio,
                        errorCode = page.errorCode,
                        onSourceLogin = onSourceLogin,
                        onRetry = {
                            displayError.value = null
                            retryGeneration.intValue++
                            loader.retryPage(page, invalidateCache = true)
                        }
                    )
                }
            }

            MangaPageStatus.Error -> {
                ErrorPageContent(
                    pageIndex = page.index,
                    isScrollMode = isScrollMode,
                    placeholderAspect = sizeMemory?.aspectRatio,
                    errorCode = page.errorCode,
                    onSourceLogin = onSourceLogin,
                    onRetry = { loader.retryPage(page, invalidateCache = true) },
                )
            }

            else -> {
                LoadingPageContent(
                    page = page,
                    isScrollMode = isScrollMode,
                    placeholderAspect = sizeMemory?.aspectRatio,
                )
            }
        }

        // Sayfa numarası rozeti (webtoon'da her sayfada göstermek gürültü yapar)
        if (showPageNumber && !isScrollMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${page.index + 1}",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}


/**
 * Dokunma bölgeleri (Mihon "tap zones" paritesi).
 *
 * NEDEN `detectTapGestures` DEĞİL?
 * --------------------------------
 * Sayfa görseli Telephoto'nun çizdiği `ZoomableAsyncImage`'dır ve kendi
 * dokunma işleyicisi vardır. Üst katman (görsel) bir dokunuşu "tükettiğinde"
 * alttaki/üstteki ebeveynin `detectTapGestures` çağrısı o dokunuşu hiç görmez
 * — bu yüzden merkeze dokunarak menüyü açma/kapatma güvenilir çalışmıyordu.
 *
 * Bu yardımcı, olayları **Initial** geçişte (çocuklardan önce) yalnızca
 * *gözlemler* ve hiçbir olayı tüketmez:
 *  • tek parmak + kaymama + uzun basmama → dokunma bölgesi eylemi,
 *  • uzun basma → ayarlar,
 *  • iki parmak / kaydırma → hiçbir şey (pinch-zoom ve pan bozulmaz).
 *
 * Olaylar tüketilmediği için çift dokunarak zoom gibi Telephoto özellikleri
 * aynen çalışmaya devam eder.
 */
private fun Modifier.readerTapZones(
    readingMode: ReadingMode,
    tapZonesEnabled: Boolean,
    invertTapZones: Boolean,
    haptics: HapticFeedback,
    onCenterTap: () -> Unit,
    onLongPress: () -> Unit,
    onNextPage: () -> Unit,
    onPrevPage: () -> Unit,
): Modifier = pointerInput(readingMode, tapZonesEnabled, invertTapZones) {
    awaitPointerEventScope {
        while (true) {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            val downPosition: Offset = down.position
            val downTime = down.uptimeMillis
            var multiTouch = false
            var moved = false
            var longPressFired = false
            var upPosition: Offset = downPosition

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.size > 1) multiTouch = true
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if ((change.position - downPosition).getDistance() > viewConfiguration.touchSlop) {
                    moved = true
                }
                if (!longPressFired && !multiTouch && !moved &&
                    change.uptimeMillis - downTime >= viewConfiguration.longPressTimeoutMillis
                ) {
                    longPressFired = true
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                }
                if (!change.pressed) {
                    upPosition = change.position
                    break
                }
            }

            // Çoklu dokunma (pinch zoom) veya kaydırma (pan) yapıldıysa tıklama sayma
            if (multiTouch || moved || longPressFired) continue

            // Dokunma bölgeleri devre dışıysa yalnızca merkez dokunuşunu bildir
            val width = size.width.toFloat()
            if (!tapZonesEnabled || width <= 0f) {
                onCenterTap()
                continue
            }

            val leftEdge = width * 0.33f
            val rightEdge = width * 0.66f
            // İleri yön: RTL'de sol taraf, LTR/dikey/webtoon'da sağ taraf.
            val forwardOnLeft = (readingMode == ReadingMode.RightToLeft) != invertTapZones
            when {
                upPosition.x < leftEdge -> if (forwardOnLeft) onNextPage() else onPrevPage()
                upPosition.x > rightEdge -> if (forwardOnLeft) onPrevPage() else onNextPage()
                else -> onCenterTap()
            }
        }
    }
}

/**
 * Webtoon modunda yer tutucu boyutu için "son bilinen en-boy oranı" hafızası.
 *
 * Webtoon sayfasının yüksekliği görsel inmeden bilinemediği için önceden sabit
 * 480.dp kullanılıyordu; bu, uzun şerit sayfalarda kaydırma sıçramasına yol
 * açıyordu. Yüklenen her sayfanın oranı burada hatırlanır ve sonraki yer
 * tutucular bu orana göre çizilir → liste yüksekliği daha kararlı.
 */
class MangaPageSizeMemory {
    /** Genişlik / yükseklik oranı (varsayılan: dikey manga sayfası ~0.7). */
    var aspectRatio by mutableStateOf(0.7f)
        private set

    fun record(widthPx: Int, heightPx: Int) {
        if (widthPx <= 0 || heightPx <= 0) return
        val ratio = widthPx.toFloat() / heightPx.toFloat()
        // Aşırı uç değerleri (bozuk ölçüm / tek piksellik şerit) yok say.
        if (ratio.isFinite() && ratio > 0.08f && ratio < 4f) aspectRatio = ratio
    }
}

// ─── Ölçek / filtre eşlemeleri (tek yerden) ──────────────────────────────────

/** [MangaFitMode] → Compose [ContentScale]. */
internal fun MangaFitMode.toContentScale(): ContentScale = when (this) {
    MangaFitMode.FitScreen -> ContentScale.Fit
    MangaFitMode.FitWidth -> ContentScale.FillWidth
    MangaFitMode.FitHeight -> ContentScale.FillHeight
    MangaFitMode.Crop -> ContentScale.Crop
    MangaFitMode.Original -> ContentScale.None
}

/**
 * [ColorFilterType] → Compose [ColorFilter].
 *
 * Renk matrisleri Android'in 0..255 aralıklı kanal ofsetini kullanır.
 */
internal fun ColorFilterType.toComposeColorFilter(): ColorFilter? = when (this) {
    ColorFilterType.Normal -> null

    ColorFilterType.Warm -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                1.06f, 0.02f, 0.00f, 0f, 6f,
                0.01f, 1.00f, 0.00f, 0f, 2f,
                0.00f, 0.00f, 0.90f, 0f, -6f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )

    ColorFilterType.Cool -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.92f, 0.00f, 0.00f, 0f, -4f,
                0.00f, 1.00f, 0.02f, 0f, 2f,
                0.00f, 0.03f, 1.08f, 0f, 8f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )

    ColorFilterType.Grayscale -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.2126f, 0.7152f, 0.0722f, 0f, 0f,
                0.2126f, 0.7152f, 0.0722f, 0f, 0f,
                0.2126f, 0.7152f, 0.0722f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )

    ColorFilterType.Sepia -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )

    ColorFilterType.Invert -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )
}

// ─── Yükleme / hata yer tutucuları ───────────────────────────────────────────

@Composable
private fun LoadingPageContent(
    page: MangaPage,
    isScrollMode: Boolean,
    placeholderAspect: Float?,
) {
    Box(
        modifier = if (isScrollMode) {
            // Webtoon'da sayfa yüklenene kadar boyut bilinmiyor; son bilinen
            // sayfa oranına göre yer tutulur (sabit 480.dp yerine).
            Modifier.fillMaxWidth().aspectRatio(placeholderAspect ?: 0.7f)
        } else {
            Modifier.fillMaxSize()
        }.background(Color(0xFF0A0A0A)),
        contentAlignment = Alignment.Center,
    ) {
        when (page.status) {
            MangaPageStatus.DownloadImage,
            MangaPageStatus.LoadPage -> KitsugiPlasmaLoader(size = 46.dp)

            MangaPageStatus.Queue -> Icon(
                imageVector = Icons.HourglassBottom,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(40.dp),
            )

            else -> CircularProgressIndicator(
                color = KitsugiColors.Accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun ErrorPageContent(
    pageIndex: Int,
    isScrollMode: Boolean,
    placeholderAspect: Float?,
    errorCode: String? = null,
    onSourceLogin: (() -> Unit)? = null,
    onRetry: () -> Unit,
) {
    Box(
        modifier = if (isScrollMode) {
            Modifier.fillMaxWidth().aspectRatio(placeholderAspect ?: 0.7f)
        } else {
            Modifier.fillMaxSize()
        }.background(Color(0xFF120A0A)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.BrokenImage,
                contentDescription = null,
                tint = Color(0xFFEF5350),
                modifier = Modifier.size(52.dp),
            )
            Text(
                text = stringResource(R.string.inline_page_1_s_couldn_t_11d924a, pageIndex + 1),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp),
            )
            if (PlatformErrorCode.fromId(errorCode) == PlatformErrorCode.MangaAuth && onSourceLogin != null) {
                TextButton(onClick = onSourceLogin) {
                    Text(stringResource(R.string.manga_source_login_action), color = Color(0xFFEF5350))
                }
            }
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.manga_reader_retry), color = KitsugiColors.Accent)
            }
        }
    }
}
