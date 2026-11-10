package com.kitsugi.animelist.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Kaydırmayla birlikte akan (gizlenen / geri gelen) üst bar sarmalayıcısı.
 *
 * ## Neden nested scroll
 * Önceki yaklaşımlar (`firstVisibleItemIndex >= N` → `AnimatedVisibility`) barı bir anda
 * açıp kapatıyor, yüksekliği ani değiştiği için liste zıplıyordu. Burada bar, listenin
 * kaydırma mesafesine **birebir** bağlı olarak yukarı kayar (translationY) — parmakla
 * birlikte akar, animasyon sıçraması olmaz. Liste en başa dönerse bar kendini geri getirir.
 *
 * Bu bileşen ilk olarak video eklenti izleme sayfasında kullanıldı (bkz. StreamScreenContent);
 * ortak bileşene taşındı ki manga bölüm detay/okuma sayfaları da **aynı** mekaniği kullansın.
 *
 * @param listState Barın bağlı olduğu liste. İçerik bu listeden bağımsız kaydırılıyorsa
 *   (ör. webtoon okuyucu) buraya ilgili state verilmelidir.
 * @param barHeight Barın yüksekliği; içerik en az bu kadar üst boşlukla başlamalıdır.
 * @param offsetState Dışarıdan (ör. yatay düzendeki sol ray) paylaşılmak istenen ofset.
 * @param bar Barın kendisi (sarmalayıcı yüksekliği ve konumu o yönetir).
 * @param content İçerik; `topContentPadding` barın kapanmış hâlinde bile korunacak üst boşluktur.
 */
@Composable
fun KitsugiCollapsibleTopBar(
    listState: LazyListState,
    barHeight: Dp,
    bar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    offsetState: MutableFloatState? = null,
    content: @Composable (topContentPadding: Dp) -> Unit
) {
    val density = LocalDensity.current
    val barPx = with(density) { barHeight.toPx() }
    val offset = offsetState ?: remember { mutableFloatStateOf(0f) }
    val connection = remember(barPx, offset, listState) {
        HideOnScrollConnection(offset, barPx, listState)
    }

    // Liste gerçekten en başa döndüyse bar kendini geri getirir.
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 4
        }.collect { atTop -> if (atTop) offset.floatValue = 0f }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .nestedScroll(connection)
    ) {
        content(barHeight + 8.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .graphicsLayer {
                    translationY = -offset.floatValue
                    val progress = (offset.floatValue / barPx).coerceIn(0f, 1f)
                    alpha = 1f - progress * 0.25f
                }
        ) { bar() }
    }
}

/**
 * Kaydırma deltasını bar ofsetine çevirir. 0 = tamamen görünür, maxOffset = tamamen gizli.
 *
 * Kısa listelerde (kaydırılamayan içerik) aşırı kaydırma barı gizlemez: yalnızca liste
 * gerçekten aşağı kayabiliyorsa gizleme tüketilir; geri gösterme her durumda çalışır.
 */
private class HideOnScrollConnection(
    private val offset: MutableFloatState,
    private val maxOffset: Float,
    private val listState: LazyListState
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val dy = available.y
        if (dy == 0f || maxOffset <= 0f) return Offset.Zero
        if (dy < 0f && !listState.canScrollForward) return Offset.Zero
        val target = (offset.floatValue - dy).coerceIn(0f, maxOffset)
        val delta = target - offset.floatValue
        if (delta == 0f) return Offset.Zero
        offset.floatValue = target
        return Offset(0f, -delta)
    }
}
