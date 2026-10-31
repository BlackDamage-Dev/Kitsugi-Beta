@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.kitsugi.animelist.ui.screens.mylist

import com.kitsugi.animelist.ui.components.KitsugiPlatformLogo
import com.kitsugi.animelist.ui.components.KitsugiStickyHeaderReveal
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.HorizontalDivider
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.kitsugi.animelist.ui.components.KitsugiPullToRefreshIndicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.WatchStatus
import com.kitsugi.animelist.ui.components.KitsugiSearchField
import com.kitsugi.animelist.ui.components.KitsugiTopAction
import com.kitsugi.animelist.ui.components.KitsugiTopActionRow
import com.kitsugi.animelist.ui.screens.mylist.components.KitsugiMyListFilterSheet
import com.kitsugi.animelist.ui.screens.mylist.components.KitsugiMyListSortMenu
import com.kitsugi.animelist.ui.theme.LocalKitsugiColors
import com.kitsugi.animelist.ui.utils.dpadVerticalFastScroll
import com.kitsugi.animelist.ui.utils.tvClickable
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

@Composable
fun MyListHeaderSection(
    selectedListLayoutId: String,
    onListLayoutChange: (String) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    showSearchField: Boolean,
    onSearchFieldToggle: () -> Unit,
    showFilterPanel: Boolean,
    onFilterPanelToggle: () -> Unit,
    onHideFilters: () -> Unit,
    selectedStatusFilterId: String,
    selectedTypeFilterId: String,
    selectedFavoriteFilterId: String,
    selectedScoreFilterId: String,
    selectedYearFilterId: String,
    selectedExtraFilterId: String,
    selectedSortId: String,
    onStatusFilterChange: (String) -> Unit,
    onTypeFilterChange: (String) -> Unit,
    onFavoriteFilterChange: (String) -> Unit,
    onScoreFilterChange: (String) -> Unit,
    onYearFilterChange: (String) -> Unit,
    onExtraFilterChange: (String) -> Unit,
    onSortChange: (String) -> Unit,
    activeTabScrollState: LazyListState,
    accentColor: Color,
    horizontalPadding: Dp,
    hasActiveFilters: Boolean,
    /**
     * Üst barın kaydırma nedeniyle çekilip çekilmeyeceği. Davranış tek yerden
     * yönetilsin diye kararı çağıran ekran verir
     * (bkz. [com.kitsugi.animelist.ui.components.rememberScrollAwareHeaderState]);
     * bağımsız açılır listelerde (kullanıcı listesi vb.) varsayılan `false`
     * bırakılabilir — başlık o zaman sabit kalır.
     */
    headerCollapsed: Boolean = false,
    headerTitle: String = "Listem",
    headerSubtitle: String? = null,
    onBackClick: (() -> Unit)? = null,
    searchPlaceholder: String = "Listende ara..."
) {
    val KitsugiColors = LocalKitsugiColors.current
    var showSortMenu by rememberSaveable { mutableStateOf(false) }

    val expandedHeaderHeight = if (headerSubtitle.isNullOrBlank()) 60.dp else 68.dp

    Column {
        // Başlık eskiden 60.dp → 0.dp yüksekliğine tek karede atlıyor, bu yüzden üst bar
        // aşağı kaydırınca "pat diye" kaybolup tepede geri geliyordu. Artık uygulamanın
        // ortak hareket dilini (KitsugiMotion sticky header geçişi) kullanıyor: yükseklik,
        // soluklaşma ve yukarı süzülme aynı anda yumuşak easing ile animasyonlanır;
        // liste de başlıkla birlikte akarak yer değiştirir.
        KitsugiStickyHeaderReveal(visible = !headerCollapsed) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(expandedHeaderHeight)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(expandedHeaderHeight)
                        .padding(start = if (onBackClick == null) horizontalPadding else 4.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onBackClick != null) {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    imageVector = Icons.ArrowBack,
                                    contentDescription = stringResource(R.string.geri),
                                    tint = KitsugiColors.textPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = headerTitle,
                                color = KitsugiColors.textPrimary,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!headerSubtitle.isNullOrBlank()) {
                                Text(
                                    text = headerSubtitle,
                                    color = KitsugiColors.textMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val nextLayoutId = when (selectedListLayoutId) {
                                    "compact" -> "comfortable"
                                    "comfortable" -> "large"
                                    "large" -> "grid_2col"
                                    "grid_2col" -> "compact"
                                    else -> "comfortable"
                                }
                                onListLayoutChange(nextLayoutId)
                            }
                        ) {
                            val layoutIcon = when (selectedListLayoutId) {
                                "compact" -> Icons.DensityMedium
                                "comfortable" -> Icons.FormatListBulleted
                                "large" -> Icons.ViewStream
                                "grid_2col" -> Icons.GridView
                                else -> Icons.FormatListBulleted
                            }
                            Icon(
                                imageVector = layoutIcon,
                                contentDescription = stringResource(R.string.gorunum_degistir),
                                tint = KitsugiColors.textSecondary
                            )
                        }

                        IconButton(onClick = onSearchFieldToggle) {
                            Icon(
                                imageVector = Icons.Search,
                                contentDescription = stringResource(R.string.arama),
                                tint = if (showSearchField || searchQuery.isNotBlank()) accentColor else KitsugiColors.textSecondary
                            )
                        }

                        Box {
                            IconButton(onClick = onFilterPanelToggle) {
                                Icon(
                                    imageVector = Icons.FilterList,
                                    contentDescription = "Filtrele ve Sırala",
                                    tint = if (showFilterPanel || hasActiveFilters) accentColor else KitsugiColors.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Filtre & sıralama bölümü artık satır içi alan yerine yeni premium
        // açılır panelde (KitsugiMyListFilterSheet) açılır — liste yerinden
        // oynamaz, panel tema vurgusuyla modern bir sheet olarak belirir.
        if (showFilterPanel) {
            KitsugiMyListFilterSheet(
                selectedStatusFilterId = selectedStatusFilterId,
                selectedTypeFilterId = selectedTypeFilterId,
                selectedScoreFilterId = selectedScoreFilterId,
                selectedYearFilterId = selectedYearFilterId,
                selectedExtraFilterId = selectedExtraFilterId,
                selectedSortId = selectedSortId,
                onStatusSelected = onStatusFilterChange,
                onTypeSelected = onTypeFilterChange,
                onScoreSelected = onScoreFilterChange,
                onYearSelected = onYearFilterChange,
                onExtraSelected = onExtraFilterChange,
                onSortSelected = onSortChange,
                onResetFilters = {
                    onStatusFilterChange("all")
                    onTypeFilterChange("all")
                    onFavoriteFilterChange("all")
                    onScoreFilterChange("all")
                    onYearFilterChange("all")
                    onExtraFilterChange("all")
                    onSortChange("newest")
                },
                onDismiss = onHideFilters
            )
        }

        AnimatedVisibility(
            visible = showSearchField || searchQuery.isNotBlank(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KitsugiSearchField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = searchPlaceholder,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val typeItems = typeFilters.map { it.id to myListLocalizedTitle(it.title, it.titleRes) }
                    typeItems.forEach { (id, label) ->
                        val isSelected = selectedTypeFilterId == id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) accentColor else KitsugiColors.surface)
                                .tvClickable(shape = RoundedCornerShape(12.dp)) {
                                    onTypeFilterChange(id)
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = if (isSelected) "✓ $label" else label,
                                color = if (isSelected) KitsugiColors.background else KitsugiColors.textPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    val activeSortTitle = getSortTitle(selectedSortId)
                    Box {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(KitsugiColors.surface)
                                .tvClickable(shape = RoundedCornerShape(12.dp)) {
                                    showSortMenu = true
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = "≡ $activeSortTitle ▾",
                                color = accentColor,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        KitsugiMyListSortMenu(
                            expanded = showSortMenu,
                            selectedSortId = selectedSortId,
                            onSortSelected = onSortChange,
                            onDismissRequest = { showSortMenu = false }
                        )
                    }

                    if (searchQuery.isNotBlank() || hasActiveFilters) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .tvClickable(shape = RoundedCornerShape(12.dp)) {
                                    onSearchQueryChange("")
                                    onStatusFilterChange("all")
                                    onTypeFilterChange("all")
                                    onFavoriteFilterChange("all")
                                    onScoreFilterChange("all")
                                    onYearFilterChange("all")
                                    onExtraFilterChange("all")
                                    onSortChange("newest")
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.temizle),
                                color = accentColor,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(
            color = KitsugiColors.border.copy(alpha = 0.18f),
            thickness = 0.5.dp
        )
    }
}

data class MyListPlatformSource(
    val index: Int,
    val id: String,
    val name: String,
    val shortName: String,
    val emoji: String,
    val brandColor: Color,
    val isConnected: Boolean,
    val username: String,
    val count: Int,
    val description: String
)

/**
 * Listem kaynak satırının tek bir uzak kaynağa ait, salt-okunur karşılığı.
 * Profilde açılan kullanıcı listelerinde kaynak her zaman AniList olduğu için sahte
 * platform seçenekleri göstermek yerine aynı hap ve rastgele seçim davranışı korunur.
 */
@Composable
fun MyListSingleSourceBar(
    sourceId: String,
    sourceName: String,
    visibleEntries: List<MediaEntry>,
    onEntryClick: (MediaEntry) -> Unit,
    onEmptyMessage: () -> Unit,
    accentColor: Color,
    horizontalPadding: Dp
) {
    val KitsugiColors = LocalKitsugiColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .padding(top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(KitsugiColors.surface)
                .border(
                    width = 1.dp,
                    color = accentColor.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            KitsugiPlatformLogo(
                platformId = sourceId,
                size = 18.dp,
                fallbackTint = accentColor
            )
            Text(
                text = sourceName,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = KitsugiColors.textPrimary
                )
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(KitsugiColors.surface)
                .border(
                    width = 1.dp,
                    color = KitsugiColors.border.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(14.dp)
                )
                .tvClickable(shape = RoundedCornerShape(14.dp)) {
                    if (visibleEntries.isNotEmpty()) onEntryClick(visibleEntries.random())
                    else onEmptyMessage()
                },
            contentAlignment = Alignment.Center
        ) {
            Text(text = "🎲", fontSize = 16.sp)
        }
    }
}

@Composable
fun MyListTabBar(
    selectedTabIndex: Int,
    onTabIndexChange: (Int) -> Unit,
    visibleEntries: List<MediaEntry>,
    allEntries: List<MediaEntry> = emptyList(),
    allLibraryEntryCount: Int = allEntries.size,
    isAniListConnected: Boolean = false,
    isMalConnected: Boolean = false,
    isSimklConnected: Boolean = false,
    isKitsuConnected: Boolean = false,
    isShikimoriConnected: Boolean = false,
    isBangumiConnected: Boolean = false,
    anilistUsername: String = "",
    malUsername: String = "",
    simklUsername: String = "",
    kitsuUsername: String = "",
    shikimoriUsername: String = "",
    bangumiUsername: String = "",
    onEntryClick: (MediaEntry) -> Unit,
    onExternalSyncMessage: (String) -> Unit,
    accentColor: Color,
    horizontalPadding: Dp,
    isNotificationsVisible: Boolean = false,
    onOpenNotifications: () -> Unit = {},
    onOpenWatchHistory: (() -> Unit)? = null,
    onOpenDownloads: (() -> Unit)? = null
) {
    val KitsugiColors = LocalKitsugiColors.current
    var showSourcePickerSheet by rememberSaveable { mutableStateOf(false) }

    val countAniList = remember(allEntries) { allEntries.count { it.source.equals("anilist", ignoreCase = true) } }
    val countMal = remember(allEntries) { allEntries.count { it.source.equals("mal", ignoreCase = true) || it.source.equals("jikan", ignoreCase = true) || it.source.equals("myanimelist", ignoreCase = true) } }
    val countSimkl = remember(allEntries) { allEntries.count { it.source.equals("simkl", ignoreCase = true) } }
    val countKitsu = remember(allEntries) { allEntries.count { it.source.equals("kitsu", ignoreCase = true) } }
    val countShikimori = remember(allEntries) { allEntries.count { it.source.equals("shikimori", ignoreCase = true) } }
    val countBangumi = remember(allEntries) { allEntries.count { isBangumiListMember(it.source, it.malId) } }

    val platforms = listOf(
        MyListPlatformSource(
            index = MY_LIST_ANILIST_TAB_INDEX,
            id = "anilist",
            name = "AniList",
            shortName = "AniList",
            emoji = "⚡",
            brandColor = Color(0xFF02A9FF),
            isConnected = isAniListConnected,
            username = anilistUsername,
            count = countAniList,
            description = "En zengin anime, manga, manhwa ve stüdyo veritabanı"
        ),
        MyListPlatformSource(
            index = MY_LIST_MAL_TAB_INDEX,
            id = "mal",
            name = "MyAnimeList",
            shortName = "MAL",
            emoji = "🏆",
            brandColor = Color(0xFF2E51A2),
            isConnected = isMalConnected,
            username = malUsername,
            count = countMal,
            description = "Klasik MyAnimeList kataloğu, dergiler ve yapımcılar"
        ),
        MyListPlatformSource(
            index = MY_LIST_SIMKL_TAB_INDEX,
            id = "simkl",
            name = "Simkl",
            shortName = "Simkl",
            emoji = "📺",
            brandColor = Color(0xFFE5A00D),
            isConnected = isSimklConnected,
            username = simklUsername,
            count = countSimkl,
            description = "TV dizileri, filmler ve anime takip platformu"
        ),
        MyListPlatformSource(
            index = MY_LIST_KITSU_TAB_INDEX,
            id = "kitsu",
            name = "Kitsu",
            shortName = "Kitsu",
            emoji = "🦊",
            brandColor = Color(0xFFFD755C),
            isConnected = isKitsuConnected,
            username = kitsuUsername,
            count = countKitsu,
            description = "Hızlı, hafif anime ve manga topluluk kütüphanesi"
        ),
        MyListPlatformSource(
            index = MY_LIST_SHIKIMORI_TAB_INDEX,
            id = "shikimori",
            name = "Shikimori",
            shortName = "Shikimori",
            emoji = "🌸",
            brandColor = Color(0xFF8E44AD),
            isConnected = isShikimoriConnected,
            username = shikimoriUsername,
            count = countShikimori,
            description = "Kapsamlı anime, manga ve ranobe veritabanı"
        ),
        MyListPlatformSource(
            index = MY_LIST_BANGUMI_TAB_INDEX,
            id = "bangumi",
            name = "Bangumi",
            shortName = "Bangumi",
            emoji = "🎌",
            brandColor = Color(0xFFF09199),
            isConnected = isBangumiConnected,
            username = bangumiUsername,
            count = countBangumi,
            description = "Çin'in anime/manga topluluk veritabanı (bgm.tv)"
        )
    )

    val allPlatform = MyListPlatformSource(
        index = MY_LIST_ALL_TAB_INDEX,
        id = "all",
        name = "Tümü",
        shortName = "Tümü",
        emoji = "🌐",
        brandColor = accentColor,
        isConnected = false,
        username = "",
        count = allLibraryEntryCount,
        description = "${platforms.size} platformdaki içerikleri tek listede birleştir"
    )
    val displayPlatforms = listOf(allPlatform) + platforms
    val currentPlatform = displayPlatforms.firstOrNull { it.index == selectedTabIndex } ?: platforms[0]

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Arama ekranı kaynak seçimine benzer Seçici Hap Buton
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(KitsugiColors.surface)
                .border(
                    width = 1.dp,
                    color = currentPlatform.brandColor.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp)
                )
                .tvClickable(shape = RoundedCornerShape(16.dp), onClick = { showSourcePickerSheet = true })
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            KitsugiPlatformLogo(
                platformId = currentPlatform.id,
                size = 18.dp,
                fallbackTint = currentPlatform.brandColor
            )
            Text(
                text = currentPlatform.shortName,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = KitsugiColors.textPrimary
                )
            )
            Icon(
                imageVector = Icons.ArrowDropDown,
                contentDescription = "Kaynak Seç",
                tint = currentPlatform.brandColor,
                modifier = Modifier.size(18.dp)
            )
        }

        // Kaynak değişimi zaten soldaki açılır seçicide var; yinelenen sekme çubuğu kaldırıldı.
        Spacer(modifier = Modifier.weight(1f))

        // 🎲 Rastgele + 🔔 Bildirim → Keşfet ile birebir aynı ortak aksiyon satırı.
        // Sıra kanoniktir: rastgele solda, bildirim her zaman en sağda.
        KitsugiTopActionRow(
            actions = buildList {
                if (onOpenWatchHistory != null) add(KitsugiTopAction.HISTORY)
                if (onOpenDownloads != null) add(KitsugiTopAction.DOWNLOADS)
                add(KitsugiTopAction.RANDOM)
                if (isNotificationsVisible) add(KitsugiTopAction.NOTIFICATIONS)
            },
            onActionClick = { action ->
                when (action) {
                    KitsugiTopAction.RANDOM -> {
                        if (visibleEntries.isNotEmpty()) {
                            onEntryClick(visibleEntries.random())
                        } else {
                            onExternalSyncMessage("Gösterilecek bir öğe yok")
                        }
                    }
                    KitsugiTopAction.HISTORY -> onOpenWatchHistory?.invoke()
                    KitsugiTopAction.DOWNLOADS -> onOpenDownloads?.invoke()
                    KitsugiTopAction.NOTIFICATIONS -> onOpenNotifications()
                    else -> Unit
                }
            }
        )
    }

    if (showSourcePickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSourcePickerSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = KitsugiColors.surface,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(KitsugiColors.textMuted.copy(alpha = 0.4f))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Kütüphane Kaynağı Seç",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = KitsugiColors.textPrimary
                    )
                )
                Text(
                    text = "Görüntülemek ve yönetmek istediğin platform kütüphanesini seç",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = KitsugiColors.textMuted
                    ),
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                displayPlatforms.forEach { platform ->
                    val isSelected = selectedTabIndex == platform.index
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) platform.brandColor.copy(alpha = 0.14f)
                                else KitsugiColors.surfaceSoft.copy(alpha = 0.5f)
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) platform.brandColor else Color.Transparent,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .tvClickable(shape = RoundedCornerShape(16.dp)) {
                                onTabIndexChange(platform.index)
                                showSourcePickerSheet = false
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        KitsugiPlatformLogo(
                            platformId = platform.id,
                            size = 30.dp,
                            modifier = Modifier.padding(end = 12.dp),
                            fallbackTint = platform.brandColor
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = platform.name,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isSelected) platform.brandColor else KitsugiColors.textPrimary
                                    )
                                )
                                if (platform.id == "all") {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(platform.brandColor.copy(alpha = 0.16f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${platforms.size} platform",
                                            color = platform.brandColor,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else if (platform.isConnected) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF27AE60).copy(alpha = 0.18f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (platform.username.isNotBlank()) "@${platform.username}" else "Bağlı",
                                            color = Color(0xFF27AE60),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(KitsugiColors.border.copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.bagli_degil),
                                            color = KitsugiColors.textMuted,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "${platform.description} • ${platform.count} içerik",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = KitsugiColors.textMuted
                                ),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        if (isSelected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Check,
                                contentDescription = stringResource(R.string.secili),
                                tint = platform.brandColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MyListContentPage(
    pageTabIndex: Int,
    selectedTabIndex: Int,
    isConnected: Boolean,
    isSimklSessionExpired: Boolean,
    pageEntries: List<MediaEntry>,
    sourceBadgesByEntryId: Map<Int, List<String>>,
    visibleEntries: List<MediaEntry>,
    groupedVisibleEntries: List<Pair<WatchStatus, List<MediaEntry>>>,
    searchQuery: String,
    selectedStatusFilterId: String,
    selectedListLayoutId: String,
    appSettings: com.kitsugi.animelist.data.settings.AppSettings,
    pageScrollState: LazyListState,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onEntryClick: (MediaEntry) -> Unit,
    onIncrementProgress: (MediaEntry) -> Unit,
    onPosterLongClick: (String) -> Unit,
    accentColor: Color,
    horizontalPadding: Dp
) {
    val KitsugiColors = LocalKitsugiColors.current
    val isTvDevice = com.kitsugi.animelist.ui.theme.LocalIsTvDevice.current
    val coroutineScope = rememberCoroutineScope()
    val pageRefreshState = rememberPullToRefreshState()
    var pageIsRefreshing by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenWidthDp = configuration.screenWidthDp
    val gridColumns = when {
        isLandscape -> if (screenWidthDp >= 900) 6 else 5
        else -> if (screenWidthDp >= 600) 4 else 2
    }

    PullToRefreshBox(
        isRefreshing = pageIsRefreshing,
        onRefresh = {
            coroutineScope.launch {
                pageIsRefreshing = true
                onRefresh()
                pageIsRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize(),
        state = pageRefreshState,
        indicator = {
            KitsugiPullToRefreshIndicator(
                state = pageRefreshState,
                isRefreshing = pageIsRefreshing,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    ) {
        if (!isConnected && searchQuery.isBlank()) {
            MyListNotConnectedState(
                selectedTabIndex = pageTabIndex,
                isSimklSessionExpired = isSimklSessionExpired,
                onLogin = onLogin
            )
        } else if (pageEntries.isEmpty() && searchQuery.isBlank()) {
            LazyColumn(
                state = pageScrollState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding)
            ) {
                item { MyListSyncPromptState() }
            }
        } else {
            val useGlobalSearchResults = searchQuery.isNotBlank() && selectedTabIndex != MY_LIST_ALL_TAB_INDEX
            val displayEntries = if (pageTabIndex == selectedTabIndex || useGlobalSearchResults) visibleEntries else pageEntries
            // PERF: aktif olmayan pager sayfası için durum gruplaması her yeniden
            // kompozisyonda yeniden hesaplanıyordu (kompozisyon sırasında 6 × tam liste
            // taraması — kaydırma jesti sırasında komşu sayfalar da kompoze olur).
            val displayGrouped = if (pageTabIndex == selectedTabIndex || useGlobalSearchResults) {
                groupedVisibleEntries
            } else {
                remember(pageEntries) {
                    val statusOrder = listOf(
                        WatchStatus.Watching, WatchStatus.Repeating, WatchStatus.Planned,
                        WatchStatus.Paused, WatchStatus.Dropped, WatchStatus.Completed
                    )
                    statusOrder.map { status ->
                        status to pageEntries.filter { it.status == status }
                    }.filter { it.second.isNotEmpty() }
                }
            }

            LazyColumn(
                state = pageScrollState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding)
                    .then(if (isTvDevice) Modifier.dpadVerticalFastScroll(pageScrollState) else Modifier),
                verticalArrangement = Arrangement.Top
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${displayEntries.size} sonuç",
                        color = KitsugiColors.textMuted,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
                    )
                }
                if (displayEntries.isEmpty()) {
                    item {
                        EmptyListResultCard(
                            searchQuery = if (pageTabIndex == selectedTabIndex) searchQuery else "",
                            selectedStatusFilterId = if (pageTabIndex == selectedTabIndex) selectedStatusFilterId else "all",
                            selectedTypeFilterId = "all",
                            selectedFavoriteFilterId = "all",
                            selectedScoreFilterId = "all",
                            selectedYearFilterId = "all",
                            selectedExtraFilterId = "all",
                            selectedSortId = "newest"
                        )
                        Spacer(modifier = Modifier.height(90.dp))
                    }
                } else if ((selectedStatusFilterId == "completed" && pageTabIndex == selectedTabIndex) ||
                    !appSettings.separatedListStyle
                ) {
                    // AniHyou paritesi: "Ayrılmış liste tarzını kullan" kapalıysa düz akış.
                    MyListFlatContent(
                        visibleEntries = displayEntries,
                        selectedListLayoutId = selectedListLayoutId,
                        titleLanguage = appSettings.titleLanguage,
                        scoreFormat = appSettings.scoreFormat,
                        hideScores = appSettings.hideScores,
                        blurAdultMedia = appSettings.blurAdultMedia,
                        gridColumns = gridColumns,
                        sourceBadgesByEntryId = sourceBadgesByEntryId,
                        onEntryClick = onEntryClick,
                        onIncrementProgress = onIncrementProgress,
                        onPosterLongClick = onPosterLongClick
                    )
                } else {
                    MyListGroupedContent(
                        groupedEntries = displayGrouped,
                        selectedListLayoutId = selectedListLayoutId,
                        titleLanguage = appSettings.titleLanguage,
                        scoreFormat = appSettings.scoreFormat,
                        hideScores = appSettings.hideScores,
                        blurAdultMedia = appSettings.blurAdultMedia,
                        gridColumns = gridColumns,
                        sourceBadgesByEntryId = sourceBadgesByEntryId,
                        onEntryClick = onEntryClick,
                        onIncrementProgress = onIncrementProgress,
                        onPosterLongClick = onPosterLongClick
                    )
                }
                item { Spacer(modifier = Modifier.height(90.dp)) }
            }
        }
    }
}
