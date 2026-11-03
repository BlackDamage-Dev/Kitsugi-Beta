@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.kitsugi.animelist.ui.screens.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalConfiguration
import com.kitsugi.animelist.ui.app.ProfileFavoriteItem
import com.kitsugi.animelist.ui.components.KitsugiNsfwImage
import com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog
import com.kitsugi.animelist.ui.components.kitsugiNeonGlow
import com.kitsugi.animelist.ui.screens.mylist.cardSpacingForLayout
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.utils.tvClickable
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

/**
 * Profil favorileri için Listem (MyListScreen) ile birebir aynı kart düzenleri,
 * kaydırma davranışı ve alt kontroller. Favorilerde durum/ilerleme bilgisi olmadığı
 * için kartlar sadece poster + başlık gösterir; şekiller Listem layout'larıyla aynıdır.
 */

/** Favori kategorileri — sekme indexi ile aynı sırada. */
@Composable
fun profileFavoriteCategoryLabels(): List<String> = listOf(
    stringResource(R.string.profile_anime_list),
    stringResource(R.string.profile_manga_list),
    stringResource(R.string.profile_favorite_characters),
    stringResource(R.string.profile_section_staff),
    stringResource(R.string.detail_section_studios)
)

val ProfileFavoriteCategoryKeys = listOf("anime", "manga", "characters", "staff", "studios")

// ─────────────────────────────────────────────────────────────────────────────
// Kart
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ProfileFavoriteListemCard(
    item: ProfileFavoriteItem,
    layoutId: String,
    blurAdultMedia: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (layoutId) {
        "compact" -> FavoriteCompactCard(item, blurAdultMedia, onClick, modifier)
        "large" -> FavoriteLargeCard(item, blurAdultMedia, onClick, modifier)
        "minimalist" -> FavoriteMinimalistCard(item, blurAdultMedia, onClick, modifier)
        "grid_2col" -> FavoritePosterGridCard(item, blurAdultMedia, onClick, modifier)
        else -> FavoriteComfortableCard(item, blurAdultMedia, onClick, modifier)
    }
}

@Composable
private fun FavoriteCompactCard(
    item: ProfileFavoriteItem,
    blurAdultMedia: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .kitsugiNeonGlow(shape)
            .clip(shape)
            .background(KitsugiColors.Surface)
            .tvClickable(shape = shape, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FavoritePoster(
            item = item,
            blurAdultMedia = blurAdultMedia,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.width(50.dp).height(70.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = item.title,
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FavoriteComfortableCard(
    item: ProfileFavoriteItem,
    blurAdultMedia: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = modifier
            .kitsugiNeonGlow(shape)
            .clip(shape)
            .background(KitsugiColors.Surface)
            .tvClickable(shape = shape, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FavoritePoster(
            item = item,
            blurAdultMedia = blurAdultMedia,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.width(72.dp).height(112.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = item.title,
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FavoriteMinimalistCard(
    item: ProfileFavoriteItem,
    blurAdultMedia: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = modifier
            .kitsugiNeonGlow(shape)
            .clip(shape)
            .background(KitsugiColors.Surface)
            .tvClickable(shape = shape, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FavoritePoster(
            item = item,
            blurAdultMedia = blurAdultMedia,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.width(64.dp).height(96.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = item.title,
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FavoriteLargeCard(
    item: ProfileFavoriteItem,
    blurAdultMedia: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = modifier
            .kitsugiNeonGlow(shape)
            .clip(shape)
            .background(KitsugiColors.Surface)
            .tvClickable(shape = shape, onClick = onClick)
            .padding(16.dp)
    ) {
        FavoritePoster(
            item = item,
            blurAdultMedia = blurAdultMedia,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth().height(190.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = item.title,
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FavoritePosterGridCard(
    item: ProfileFavoriteItem,
    blurAdultMedia: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val cardShape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .kitsugiNeonGlow(cardShape)
            .clip(cardShape)
            .background(KitsugiColors.Surface)
            .tvClickable(shape = cardShape, onClick = onClick)
    ) {
        FavoritePoster(
            item = item,
            blurAdultMedia = blurAdultMedia,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)
        )
        Text(
            text = item.title,
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun FavoritePoster(
    item: ProfileFavoriteItem,
    blurAdultMedia: Boolean,
    shape: RoundedCornerShape,
    modifier: Modifier
) {
    val accent = LocalKitsugiAccent.current
    Box(
        modifier = modifier
            .clip(shape)
            .background(accent.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        KitsugiNsfwImage(
            model = item.imageUrl.takeIf { it.isNotBlank() },
            contentDescription = item.title,
            isAdult = item.isAdult,
            blurAdultMedia = blurAdultMedia,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            initials = item.title,
            initialsColor = accent,
            initialsStyle = MaterialTheme.typography.titleLarge
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// İçerik (Listem layout'una göre satır / grid)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Favori listesi — ana kaydırma LazyColumn'unun içinde çalıştığı için düz (non-lazy) render edilir.
 * Sayfalama [ProfileFavoritesAutoLoad] ile dış listede yapılır.
 */
@Composable
fun ProfileFavoritesListemContent(
    entries: List<ProfileFavoriteItem>,
    layoutId: String,
    blurAdultMedia: Boolean,
    isLandscape: Boolean,
    onItemClick: (ProfileFavoriteItem) -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    // MyListContentPage ile aynı sütun kuralı
    val gridColumns = when {
        isLandscape -> if (screenWidthDp >= 900) 6 else 5
        else -> if (screenWidthDp >= 600) 4 else 3
    }
    val isGrid = layoutId == "grid_2col"

    Column(modifier = Modifier.fillMaxWidth()) {
        if (isGrid) {
            entries.chunked(gridColumns).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { item ->
                        ProfileFavoriteListemCard(
                            item = item,
                            layoutId = layoutId,
                            blurAdultMedia = blurAdultMedia,
                            onClick = { onItemClick(item) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size < gridColumns) {
                        repeat(gridColumns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        } else {
            val spacing = cardSpacingForLayout(layoutId)
            entries.forEach { item ->
                ProfileFavoriteListemCard(
                    item = item,
                    layoutId = layoutId,
                    blurAdultMedia = blurAdultMedia,
                    onClick = { onItemClick(item) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(spacing))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Otomatik sayfalama (Daha Fazla Yükle butonu yok — aşağı kaydırınca yüklenir)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * [enabled] (favoriler sekmesi açık ve sonraki sayfa var) iken, kullanıcı içeriğin
 * sonuna yaklaşınca [onLoadMore] çağrılır. [count] değiştiğinde (yeni sayfa geldiğinde)
 * tekrar kontrol edilir; böylece ekran dolmadıkça sayfalar arka arkaya yüklenir.
 */
@Composable
fun ProfileFavoritesAutoLoad(
    listState: LazyListState,
    enabled: Boolean,
    count: Int,
    onLoadMore: () -> Unit
) {
    val nearEnd by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            if (last == null || info.totalItemsCount <= 0) {
                false
            } else {
                // Son görünür öğe (favori içeriği) ekranın altına bir ekran mesafeden daha yakınsa
                val distanceToViewportEnd = (last.offset + last.size) - info.viewportEndOffset
                last.index >= info.totalItemsCount - 2 && distanceToViewportEnd < info.viewportSize.height
            }
        }
    }
    LaunchedEffect(nearEnd, enabled, count) {
        if (enabled && nearEnd) onLoadMore()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Alt kontroller: Listem'deki gibi sol altta kategori, sağ altta yukarı kaydırma
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BoxScope.ProfileFavoritesFloatingControls(
    listState: LazyListState,
    visible: Boolean,
    selectedCategory: Int,
    categoryCounts: List<Int>,
    onCategorySelected: (Int) -> Unit,
    bottomOffset: Dp,
    onScrollReset: () -> Unit = {},
    categoryLabels: List<String>? = null,
    showCounts: Boolean = true
) {
    val accent = LocalKitsugiAccent.current
    val scope = rememberCoroutineScope()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = bottomOffset + navBottom
    val resolvedCategoryLabels = categoryLabels ?: profileFavoriteCategoryLabels()

    // Aşağı kaydırınca kategori butonu gizlenir, yukarı çıkınca geri gelir (Listem ile aynı)
    var isFabVisible by remember { mutableStateOf(true) }
    var prevIndex by remember { mutableIntStateOf(0) }
    var prevOffset by remember { mutableIntStateOf(0) }
    var showCategorySheet by remember { mutableStateOf(false) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (index == 0 && offset < 40) {
                    isFabVisible = true
                } else if (index > prevIndex || (index == prevIndex && offset > prevOffset + 15)) {
                    isFabVisible = false
                } else if (index < prevIndex || (index == prevIndex && offset < prevOffset - 15)) {
                    isFabVisible = true
                }
                prevIndex = index
                prevOffset = offset
            }
    }

    // Aşağı inildiği anda görünür olsun: ilk öğe dışına çıkmış ya da birkaç yüz piksel kaydırılmış olması yeterli
    val showScrollToTop by remember(visible) {
        derivedStateOf {
            visible && (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 240)
        }
    }

    // Sol alt: kategori (Tümü butonunun karşılığı)
    AnimatedVisibility(
        visible = visible && isFabVisible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(bottom = bottomPadding, start = 20.dp)
            .zIndex(10f)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(accent)
                .tvClickable(shape = RoundedCornerShape(999.dp), onClick = { showCategorySheet = true })
                .padding(horizontal = 20.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.FormatListBulleted,
                    contentDescription = stringResource(R.string.search_filter_category),
                    tint = KitsugiColors.Background,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = resolvedCategoryLabels.getOrElse(selectedCategory) { resolvedCategoryLabels.firstOrNull().orEmpty() },
                    color = KitsugiColors.Background,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }

    // Sağ alt: yukarı hızlı kaydırma
    AnimatedVisibility(
        visible = showScrollToTop,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(bottom = bottomPadding, end = 20.dp)
            .zIndex(10f)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(accent)
                .tvClickable(shape = RoundedCornerShape(16.dp)) {
                    // Tapping the profile's scroll-to-top control must restore the shared
                    // navigation bar even though programmatic scrolling may not emit a
                    // nested-scroll gesture that makes the bar visible again.
                    onScrollReset()
                    scope.launch {
                        listState.animateScrollToItem(0)
                        onScrollReset()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.KeyboardArrowUp,
                contentDescription = stringResource(R.string.yukari_git),
                tint = KitsugiColors.Background,
                modifier = Modifier.size(24.dp)
            )
        }
    }

    if (showCategorySheet) {
        KitsugiSheetOrDialog(onDismiss = { showCategorySheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                resolvedCategoryLabels.forEachIndexed { idx, label ->
                    val isSelected = idx == selectedCategory
                    val shape = RoundedCornerShape(16.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(
                                if (isSelected) accent.copy(alpha = 0.15f)
                                else KitsugiColors.SurfaceStrong.copy(alpha = 0.4f)
                            )
                            .tvClickable(shape = shape, onClick = {
                                onCategorySelected(idx)
                                showCategorySheet = false
                            })
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) accent else KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold
                        )
                        if (showCounts) {
                            Text(
                                text = categoryCounts.getOrElse(idx) { 0 }.toString(),
                                color = if (isSelected) accent else KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
