package com.kitsugi.animelist.ui.screens.explore

import com.kitsugi.animelist.ui.icons.KitsugiArt
import com.kitsugi.animelist.ui.icons.KitsugiArtIcon

import androidx.compose.foundation.lazy.itemsIndexed
import com.kitsugi.animelist.ui.components.PlatformErrorExplanationBlock
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import com.kitsugi.animelist.ui.components.kitsugiCardFrame
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.data.remote.AiringEntry
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.utils.tvClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import com.kitsugi.animelist.ui.utils.rememberKitsugiGridColumns
import com.kitsugi.animelist.ui.components.KitsugiShimmerBlock
import com.kitsugi.animelist.ui.components.KitsugiShimmerProvider
import com.kitsugi.animelist.ui.components.LocalShimmerBrush
import com.kitsugi.animelist.R

// ─── Yardımcı sabitler ─────────────────────────────────────────────────────

/** Calendar.DAY_OF_WEEK sırasına göre uygulama dilindeki gün adı. */
@Composable
internal fun airingCalendarDayLabel(day: Int): String = stringResource(
    when (day) {
        Calendar.MONDAY -> R.string.explore_calendar_day_monday
        Calendar.TUESDAY -> R.string.explore_calendar_day_tuesday
        Calendar.WEDNESDAY -> R.string.explore_calendar_day_wednesday
        Calendar.THURSDAY -> R.string.explore_calendar_day_thursday
        Calendar.FRIDAY -> R.string.explore_calendar_day_friday
        Calendar.SATURDAY -> R.string.explore_calendar_day_saturday
        Calendar.SUNDAY -> R.string.explore_calendar_day_sunday
        else -> R.string.explore_calendar_day_monday
    }
)

/** Pazartesi'den Pazar'a sıralı gün listesi */
internal val DAYS_ORDERED = listOf(
    Calendar.MONDAY,
    Calendar.TUESDAY,
    Calendar.WEDNESDAY,
    Calendar.THURSDAY,
    Calendar.FRIDAY,
    Calendar.SATURDAY,
    Calendar.SUNDAY
)

// ─── Header ────────────────────────────────────────────────────────────────

@Composable
internal fun AiringCalendarHeader(
    totalCount: Int,
    isLoading: Boolean,
    showOnlyMyList: Boolean,
    onShowOnlyMyListChange: (Boolean) -> Unit,
    isGridView: Boolean,
    onGridViewChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onBackClick: () -> Unit,
    isTmdb: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.ArrowBack,
                contentDescription = stringResource(R.string.geri),
                tint = KitsugiColors.TextPrimary
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        KitsugiArtIcon(art = KitsugiArt.Calendar, size = 22.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.inline_airing_this_week_88ac172),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = KitsugiColors.TextPrimary
            )
            if (totalCount > 0) {
                Text(
                    text = pluralStringResource(
                        if (isTmdb) R.plurals.explore_calendar_content_count else R.plurals.explore_calendar_episode_count,
                        totalCount,
                        totalCount
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = KitsugiColors.TextSecondary
                )
            }
        }

        // Listemde Toggle Button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (showOnlyMyList) KitsugiColors.AccentGreen.copy(0.18f) else Color.Transparent)
                .border(
                    width = 1.dp,
                    color = if (showOnlyMyList) KitsugiColors.AccentGreen else KitsugiColors.TextSecondary.copy(0.3f),
                    shape = RoundedCornerShape(10.dp)
                )
                .clickable { onShowOnlyMyListChange(!showOnlyMyList) }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (showOnlyMyList) Icons.PlaylistAddCheck else Icons.PlaylistPlay,
                    contentDescription = null,
                    tint = if (showOnlyMyList) KitsugiColors.AccentGreen else KitsugiColors.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.explore_calendar_my_list),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (showOnlyMyList) KitsugiColors.AccentGreen else KitsugiColors.TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Grid/List toggle button
        IconButton(onClick = { onGridViewChange(!isGridView) }) {
            Icon(
                imageVector = if (isGridView) Icons.ViewList else Icons.GridView,
                contentDescription = stringResource(R.string.gorunum_degistir),
                tint = KitsugiColors.TextSecondary
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        IconButton(onClick = onRefresh, enabled = !isLoading) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = KitsugiColors.Accent,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Refresh,
                    contentDescription = stringResource(R.string.yenile),
                    tint = KitsugiColors.TextSecondary
                )
            }
        }
    }
}

// ─── Gün Sekme Satırı ──────────────────────────────────────────────────────

@Composable
internal fun AiringDayTabRow(
    days: List<Int>,
    selectedDay: Int,
    scheduleMap: Map<Int, List<AiringEntry>>,
    accentColor: Color,
    onDaySelected: (Int) -> Unit
) {
    val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
    val listState = rememberLazyListState()

    LaunchedEffect(selectedDay) {
        val index = days.indexOf(selectedDay)
        if (index >= 0) {
            listState.animateScrollToItem(index)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(days) { day ->
            val isSelected = day == selectedDay
            val isToday = day == today
            val count = scheduleMap[day]?.size ?: 0
            AiringDayTab(
                label = airingCalendarDayLabel(day),
                count = count,
                isSelected = isSelected,
                isToday = isToday,
                accentColor = accentColor,
                onClick = { onDaySelected(day) }
            )
        }
    }
}

@Composable
private fun AiringDayTab(
    label: String,
    count: Int,
    isSelected: Boolean,
    isToday: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val bgColor = when {
        isSelected -> accentColor
        else       -> KitsugiColors.Surface
    }
    val textColor = when {
        isSelected -> KitsugiColors.Background
        isToday    -> accentColor
        else       -> KitsugiColors.TextSecondary
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .then(
                if (!isSelected && isToday)
                    Modifier.border(1.dp, accentColor, RoundedCornerShape(12.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
            color = textColor
        )
        if (count > 0) {
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) KitsugiColors.Background.copy(0.15f) else accentColor.copy(0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = count.coerceAtMost(99).toString(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) KitsugiColors.Background else accentColor
                )
            }
        }
    }
}

// ─── Bölüm Listesi ─────────────────────────────────────────────────────────

@Composable
internal fun AiringEntryList(
    entries: List<AiringEntry>,
    currentEntries: List<MediaEntry>,
    titleLanguage: String,
    onEntryClick: (AiringEntry) -> Unit
) {
    if (entries.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.bu_gun_icin_yayin_bulunamadi),
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    val listState = rememberLazyListState()
    val showScrollToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 3
        }
    }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(entries, key = { idx, entry -> "${idx}_${entry.malId}_${entry.aniListId}_${entry.episode}" }) { _, entry ->
                val isInList = remember(currentEntries, entry) {
                    currentEntries.any { me ->
                        (entry.malId != null && me.malId == entry.malId) ||
                                me.malId == entry.aniListId
                    }
                }
                AiringEntryCard(
                    entry = entry,
                    isInWatchingList = isInList,
                    titleLanguage = titleLanguage,
                    onClick = { onEntryClick(entry) }
                )
            }
        }

        AnimatedVisibility(
            visible = showScrollToTop,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(KitsugiColors.Accent)
                    .tvClickable(shape = RoundedCornerShape(16.dp)) {
                        scope.launch {
                            listState.animateScrollToItem(0)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.yukari_git),
                    tint = KitsugiColors.Background,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
internal fun AiringEntryGridList(
    entries: List<AiringEntry>,
    currentEntries: List<MediaEntry>,
    titleLanguage: String,
    onEntryClick: (AiringEntry) -> Unit
) {
    if (entries.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.bu_gun_icin_yayin_bulunamadi),
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    val gridState = rememberLazyGridState()
    val showScrollToTop by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 3
        }
    }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            // Same responsive density as the "Tümünü Gör" media grid.
            columns = GridCells.Fixed(rememberKitsugiGridColumns()),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(entries, key = { idx, entry -> "${idx}_${entry.malId}_${entry.aniListId}_${entry.episode}" }) { _, entry ->
                val isInList = remember(currentEntries, entry) {
                    currentEntries.any { me ->
                        (entry.malId != null && me.malId == entry.malId) ||
                                me.malId == entry.aniListId
                    }
                }
                AiringEntryGridCard(
                    entry = entry,
                    isInWatchingList = isInList,
                    titleLanguage = titleLanguage,
                    onClick = { onEntryClick(entry) }
                )
            }
        }

        AnimatedVisibility(
            visible = showScrollToTop,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(KitsugiColors.Accent)
                    .tvClickable(shape = RoundedCornerShape(16.dp)) {
                        scope.launch {
                            gridState.animateScrollToItem(0)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.yukari_git),
                    tint = KitsugiColors.Background,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
internal fun AiringEntryGridCard(
    entry: AiringEntry,
    isInWatchingList: Boolean,
    titleLanguage: String = "ENGLISH",
    onClick: () -> Unit = {}
) {
    if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        AiringEntryWideCard(entry, isInWatchingList, titleLanguage, onClick)
        return
    }
    val displayTitle = entry.getDisplayTitle(titleLanguage)

    val accentColor = if (isInWatchingList) KitsugiColors.AccentGreen else KitsugiColors.Accent
    val hasAired = entry.hasAired()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(KitsugiColors.Surface)
            .kitsugiCardFrame(
                shape = RoundedCornerShape(18.dp),
                highlightColor = if (isInWatchingList) KitsugiColors.AccentGreen.copy(0.45f) else null,
                drawNeutralWhenDisabled = false
            )
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        // Kapak görseli
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(14.dp))
                .background(KitsugiColors.SurfaceSoft)
        ) {
            com.kitsugi.animelist.ui.components.KitsugiNsfwImage(
                model = entry.coverUrl,
                contentDescription = displayTitle,
                isAdult = entry.isAdult,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Yayında overlay
            if (hasAired) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(0.55f))
                            )
                        )
                )
                KitsugiArtIcon(art = KitsugiArt.CheckCircle,
                modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp),
                contentDescription = stringResource(R.string.yayinda),
                size = 24.dp)
            }
            // İzliyorum ikonu overlay
            if (isInWatchingList) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(KitsugiColors.AccentGreen)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.PlayCircle,
                        contentDescription = stringResource(R.string.izliyorum),
                        tint = KitsugiColors.Background,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Başlık
        Text(
            text = displayTitle,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = KitsugiColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(38.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Episode & Time info — kart çerçevesine sığması için iki satıra kadar sarılır,
        // sabit yükseklik sayesinde tüm kartlar aynı boyda kalır.
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
        ) {
            Icon(
                imageVector = Icons.Schedule,
                contentDescription = null,
                tint = KitsugiColors.TextSecondary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(11.dp)
            )
            AiringTimeText(
                entry = entry,
                episode = entry.episode,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ─── Bölüm Kartı ───────────────────────────────────────────────────────────

@Composable
internal fun AiringEntryCard(
    entry: AiringEntry,
    isInWatchingList: Boolean,
    titleLanguage: String = "ENGLISH",
    onClick: () -> Unit = {}
) = AiringEntryWideCard(entry, isInWatchingList, titleLanguage, onClick)

/** List view and landscape grid share the same see-all-style horizontal media card. */
@Composable
private fun AiringEntryWideCard(
    entry: AiringEntry,
    isInWatchingList: Boolean,
    titleLanguage: String,
    onClick: () -> Unit
) {
    val title = entry.getDisplayTitle(titleLanguage)
    val hasAired = entry.hasAired()
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(KitsugiColors.Surface)
            .kitsugiCardFrame(
                shape = shape,
                highlightColor = if (isInWatchingList) KitsugiColors.AccentGreen.copy(alpha = 0.7f) else null
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 86.dp, height = 120.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(KitsugiColors.SurfaceSoft)
        ) {
            com.kitsugi.animelist.ui.components.KitsugiNsfwImage(
                model = entry.coverUrl,
                contentDescription = title,
                isAdult = entry.isAdult,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (hasAired) {
                Icon(
                    imageVector = Icons.CheckCircle,
                    contentDescription = stringResource(R.string.yayinda),
                    tint = KitsugiColors.AccentGreen,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(title, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold, color = KitsugiColors.TextPrimary,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = when {
                    entry.episode == 0 -> stringResource(R.string.media_type_movie)
                    entry.episode < 0 -> stringResource(R.string.media_type_tv)
                    else -> stringResource(R.string.explore_calendar_episode_badge, entry.episode)
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (isInWatchingList) KitsugiColors.AccentGreen else KitsugiColors.TextSecondary
            )
            Spacer(modifier = Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Schedule, contentDescription = null,
                    tint = KitsugiColors.TextSecondary, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                AiringTimeText(entry = entry)
            }
        }
        if (isInWatchingList) {
            Icon(Icons.PlayCircle, contentDescription = stringResource(R.string.izliyorum),
                tint = KitsugiColors.AccentGreen, modifier = Modifier.size(20.dp))
        }
    }
}

/** Layout-matched placeholders: identical columns, poster geometry and card spacing.
 * One provider drives every block; no per-cell animation/recomposition clocks. */
@Composable
internal fun AiringCalendarSkeleton(isGridView: Boolean) {
    KitsugiShimmerProvider {
        val brush = LocalShimmerBrush.current ?: return@KitsugiShimmerProvider
        val wide = !isGridView || LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(rememberKitsugiGridColumns()),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(8) {
                    AiringSkeletonCard(wide, brush)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(6) { AiringSkeletonCard(true, brush) }
            }
        }
    }
}

@Composable
private fun AiringSkeletonCard(wide: Boolean, brush: Brush) {
    if (wide) {
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(KitsugiColors.Surface)
                .kitsugiCardFrame(RoundedCornerShape(22.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KitsugiShimmerBlock(brush, Modifier.size(width = 86.dp, height = 120.dp), 16.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                KitsugiShimmerBlock(brush, Modifier.fillMaxWidth(0.85f).height(16.dp))
                KitsugiShimmerBlock(brush, Modifier.fillMaxWidth(0.65f).height(12.dp))
                KitsugiShimmerBlock(brush, Modifier.fillMaxWidth(0.40f).height(12.dp))
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(KitsugiColors.Surface)
            .kitsugiCardFrame(RoundedCornerShape(18.dp))
            .padding(8.dp)) {
            KitsugiShimmerBlock(brush, Modifier.fillMaxWidth().aspectRatio(2f / 3f), 14.dp)
            Spacer(Modifier.height(8.dp))
            KitsugiShimmerBlock(brush, Modifier.fillMaxWidth(0.85f).height(16.dp))
            Spacer(Modifier.height(8.dp))
            KitsugiShimmerBlock(brush, Modifier.fillMaxWidth(0.55f).height(14.dp))
            Spacer(Modifier.height(8.dp))
            KitsugiShimmerBlock(brush, Modifier.fillMaxWidth(0.75f).height(12.dp))
        }
    }
}

private fun Long.secondsToLegibleText(context: android.content.Context): String {
    val remaining = this
    val days = remaining / 86400
    val hours = (remaining % 86400) / 3600
    val minutes = (remaining % 3600) / 60
    return when {
        days > 30 -> {
            val months = days / 30
            context.getString(R.string.explore_calendar_countdown_months, months)
        }
        days > 7 -> {
            val weeks = days / 7
            context.getString(R.string.explore_calendar_countdown_weeks, weeks)
        }
        days >= 1 -> {
            if (hours > 0) {
                context.getString(R.string.explore_calendar_countdown_days_hours, days, hours)
            } else {
                context.getString(R.string.explore_calendar_countdown_days, days)
            }
        }
        hours >= 1 -> {
            if (minutes > 0) {
                context.getString(R.string.explore_calendar_countdown_hours_minutes, hours, minutes)
            } else {
                context.getString(R.string.explore_calendar_countdown_hours, hours)
            }
        }
        else -> {
            if (minutes > 0) context.getString(R.string.explore_calendar_countdown_minutes, minutes)
            else context.getString(R.string.explore_calendar_countdown_soon)
        }
    }
}

@Composable
private fun AiringTimeText(
    entry: AiringEntry,
    episode: Int? = null,
    maxLines: Int = 1,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Yayın tarihi + saat + geri sayım — "2026-10-15 14:30 • 19 gün sonra yayında"
    val datePrefix = remember(entry) {
        com.kitsugi.animelist.utils.NextAiringFormat.formatDate(entry.airingAt) + " " + entry.formattedTime()
    }
    var text by remember(entry) { mutableStateOf(datePrefix) }

    if (!entry.hasAired()) {
        LaunchedEffect(entry) {
            while (true) {
                val remaining = entry.airingAt - System.currentTimeMillis() / 1000L
                if (remaining <= 0) {
                    text = context.getString(R.string.dateprefix_yayinda, datePrefix)
                    break
                }
                text = "$datePrefix • ${remaining.secondsToLegibleText(context)}"
                val delayTime = if (remaining > 86400) 60000L else 10000L
                delay(delayTime)
            }
        }
    } else {
        text = context.getString(R.string.dateprefix_yayinda, datePrefix)
    }

    val displayText = when {
        episode == null -> text
        episode == 0 -> "Film • $text"
        episode < 0 -> "Dizi • $text"   // TMDB: bölüm numarası bilinmiyor
        else -> "$episode. Bölüm • $text"
    }

    Text(
        text = displayText,
        style = MaterialTheme.typography.labelSmall,
        color = KitsugiColors.TextSecondary,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

// ─── Hata ekranı ───────────────────────────────────────────────────────────

@Composable
internal fun AiringErrorState(
    message: String,
    onRetry: () -> Unit,
    platformErrorCode: com.kitsugi.animelist.data.platform.PlatformErrorCode? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        KitsugiArtIcon(art = KitsugiArt.Calendar, size = 48.dp)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.explore_calendar_load_failed_title),
            style = MaterialTheme.typography.titleSmall,
            color = KitsugiColors.TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = KitsugiColors.TextSecondary
        )
        platformErrorCode?.let { code ->
            Spacer(modifier = Modifier.height(12.dp))
            PlatformErrorExplanationBlock(code = code, tint = KitsugiColors.AccentRed)
        }
        Spacer(modifier = Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(KitsugiColors.Accent)
                .clickable(onClick = onRetry)
                .padding(horizontal = 24.dp, vertical = 10.dp)
        ) {
            Text(
                text = stringResource(R.string.action_retry),
                color = KitsugiColors.Background,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
