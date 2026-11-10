package com.kitsugi.animelist.ui.screens.notifications

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogo
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.components.KitsugiPullToRefreshIndicator
import com.kitsugi.animelist.ui.components.PlatformErrorCodeChip
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.utils.tvClickable
import kotlin.math.roundToInt

/*
 * ─────────────────────────────────────────────────────────────────────────────
 *  Bildirim sayfasının ORTAK iskeleti.
 *
 *  AniList, MAL, TMDB&Simkl, Kitsu, Shikimori ve Bangumi sekmeleri daha önce
 *  kendi üst barını, kendi filtre şeridini ve kendi liste iskeletini çiziyordu.
 *  Sonuç: aynı ekranda farklı boşluklar, farklı hizalama, kaydırınca kaybolan
 *  yalnızca bazı kaynaklarda çalışan davranışlar.
 *
 *  Bu dosya tek bir sözleşme tanımlar:
 *    • [NotifChromeHeader]  → üst bar + kaynak hapı + ortak filtre şeridi.
 *                             Aşağı kaydırınca başlık barı yumuşakça yukarı
 *                             kayıp gider; kaynak hapı ve filtre şeridi sabit
 *                             kalır (kaynak değiştirmek için yukarı dönmek
 *                             gerekmez).
 *    • [NotifFilterBand]    → soldan sağa kaydırılabilen, seçili sekmeyi
 *                             kendiliğinden görünür alana getiren şerit.
 *    • [NotifFeedList]      → aynı liste, aynı boş/hata/sonsuz kaydırma/
 *                             aşağı çekip yenileme/teşhis bloğu ve aynı
 *                             zaman grupları (Bugün / Dün / Bu Hafta …).
 * ─────────────────────────────────────────────────────────────────────────────
 */

// ─── Ortak filtre sekmesi ────────────────────────────────────────────────────

/**
 * Bir kaynağın filtre şeridinde görünen sekme.
 *
 * [count] `null` ise rozet hiç çizilmez (AniList gibi toplam sayıyı API'den
 * vermeyen kaynaklar); `0` ise MAL web davranışına uyularak rozet gizlenir,
 * sekmeye tıklanabilirlik korunur.
 */
internal data class NotifFilterTab(
    val key: String,
    val label: String,
    val count: Int? = null,
    /** Sekmenin soluna küçük "okunmamış" noktası çizilsin mi? */
    val hasUnread: Boolean = false
)

// ─── Zaman grupları ──────────────────────────────────────────────────────────

/** Bildirim listesinin ortak zaman grupları (kaynak fark etmeksizin). */
internal enum class NotifDateGroup(val labelResId: Int) {
    /** Tarihi gelecekte olan kayıtlar (Simkl/Kitsu yayın takvimi, MAL yaklaşan bölüm). */
    UPCOMING(R.string.notif_group_upcoming),
    TODAY(R.string.notif_group_today),
    YESTERDAY(R.string.notif_group_yesterday),
    THIS_WEEK(R.string.notif_group_this_week),
    EARLIER(R.string.notif_group_earlier),
    UNDATED(R.string.notif_group_undated)
}

private fun startOfDayMs(ms: Long): Long =
    java.util.Calendar.getInstance().apply {
        timeInMillis = ms
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun dayOffsetStartMs(nowMs: Long, daysBack: Int): Long =
    java.util.Calendar.getInstance().apply {
        timeInMillis = startOfDayMs(nowMs)
        add(java.util.Calendar.DAY_OF_YEAR, -daysBack)
    }.timeInMillis

/** Zaman damgasına göre grup; tarihsiz kayıtlar (Kitsu/Bangumi liste satırları) sonda. */
internal fun notifDateGroup(tsMs: Long?, nowMs: Long): NotifDateGroup {
    if (tsMs == null) return NotifDateGroup.UNDATED
    return when {
        // Yarın ve sonrası: "Bugün" demek yanlış olur (yaklaşan bölüm/film).
        tsMs >= dayOffsetStartMs(nowMs, -1) -> NotifDateGroup.UPCOMING
        tsMs >= startOfDayMs(nowMs) -> NotifDateGroup.TODAY
        tsMs >= dayOffsetStartMs(nowMs, 1) -> NotifDateGroup.YESTERDAY
        tsMs >= dayOffsetStartMs(nowMs, 6) -> NotifDateGroup.THIS_WEEK
        else -> NotifDateGroup.EARLIER
    }
}

/** Listeye çizilecek satır: [group] yalnızca grubun ilk satırında doludur. */
internal data class NotifGroupedRow(
    val item: NotifItem,
    val group: NotifDateGroup?,
    val groupCount: Int
)

internal fun groupNotifRows(items: List<NotifItem>, nowMs: Long): List<NotifGroupedRow> {
    if (items.isEmpty()) return emptyList()
    // Gün sınırları tek seferde hesaplanır; satır başına takvim nesnesi üretilmez.
    val todayStart = startOfDayMs(nowMs)
    val tomorrowStart = dayOffsetStartMs(nowMs, -1)
    val yesterdayStart = dayOffsetStartMs(nowMs, 1)
    val weekStart = dayOffsetStartMs(nowMs, 6)
    fun groupOf(tsMs: Long?): NotifDateGroup = when {
        tsMs == null -> NotifDateGroup.UNDATED
        tsMs >= tomorrowStart -> NotifDateGroup.UPCOMING
        tsMs >= todayStart -> NotifDateGroup.TODAY
        tsMs >= yesterdayStart -> NotifDateGroup.YESTERDAY
        tsMs >= weekStart -> NotifDateGroup.THIS_WEEK
        else -> NotifDateGroup.EARLIER
    }

    val groups = ArrayList<NotifDateGroup>(items.size)
    val counts = HashMap<NotifDateGroup, Int>()
    for (item in items) {
        val group = groupOf(item.tsMs)
        groups.add(group)
        counts[group] = (counts[group] ?: 0) + 1
    }
    var previous: NotifDateGroup? = null
    return items.mapIndexed { index, item ->
        val group = groups[index]
        val header = if (group != previous) group else null
        previous = group
        NotifGroupedRow(item = item, group = header, groupCount = counts[group] ?: 0)
    }
}

// ─── Üst bölümün yumuşak toplanma durumu ─────────────────────────────────────

/**
 * Başlık barının kaydırmayla yumuşakça toplanmasını yönetir.
 *
 * Fizik kuralı:
 *  • Parmak yukarı (içerik yukarı) → önce başlık barı toplanır, sonra liste kayar.
 *  • Parmak aşağı → liste başa dönene kadar liste kayar; liste başındayken
 *    başlık barı geri açılır. Yani "üst kısım kaybolur, alt kısım kalır" hissi
 *    soft bir animasyonla ve sıçramasız elde edilir.
 */
/** Başlık barının parmak hızına göre yumuşatma katsayısı. */
private const val SOFT_FACTOR = 0.85f

@Stable
internal class NotifChromeCollapseState {

    internal var headerHeightPx by mutableFloatStateOf(0f)

    /** 0 = tamamen açık, [headerHeightPx] = tamamen toplanmış. */
    internal var collapsedPx by mutableFloatStateOf(0f)

    val fraction: Float
        get() = if (headerHeightPx <= 1f) 0f else (collapsedPx / headerHeightPx).coerceIn(0f, 1f)

    private fun collapseBy(amount: Float): Float {
        if (headerHeightPx <= 1f || amount <= 0f) return 0f
        val target = (collapsedPx + amount).coerceIn(0f, headerHeightPx)
        val applied = target - collapsedPx
        collapsedPx = target
        return applied
    }

    private fun expandBy(amount: Float): Float {
        if (headerHeightPx <= 1f || amount <= 0f) return 0f
        val target = (collapsedPx - amount).coerceIn(0f, headerHeightPx)
        val applied = collapsedPx - target
        collapsedPx = target
        return applied
    }

    /** Üst bölümü kullanıcıya hissettirmeden yumuşakça açar. */
    suspend fun expandSmoothly() {
        if (collapsedPx <= 0f) return
        animate(
            initialValue = collapsedPx,
            targetValue = 0f,
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
        ) { value, _ -> collapsedPx = value }
    }

    /**
     * Kaydırma bittiğinde yarım kalmış durumu en yakın uca yumuşakça taşır.
     * Böylece başlık "yarı yolda takılı" kalmaz.
     */
    internal suspend fun settle(velocityY: Float) {
        if (headerHeightPx <= 1f) return
        val current = collapsedPx
        if (current <= 0.5f || current >= headerHeightPx - 0.5f) return
        val target = when {
            velocityY < -400f -> headerHeightPx
            velocityY > 400f -> 0f
            current > headerHeightPx / 2f -> headerHeightPx
            else -> 0f
        }
        animate(
            initialValue = current,
            targetValue = target,
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
        ) { value, _ -> collapsedPx = value }
    }

    internal fun connection(
        canScrollDown: () -> Boolean,
        isAtTop: () -> Boolean
    ): NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val dy = available.y
            if (dy == 0f || headerHeightPx <= 1f) return Offset.Zero
            // Yalnızca kullanıcı jesti üst bölümü toplar/açar; programatik
            // kaydırmalar (listenin başına dönme, geri yükleme) başlığı oynatmaz.
            if (source != NestedScrollSource.UserInput) return Offset.Zero

            // 0.85 katsayısı: bar parmağın hızından biraz daha yavaş hareket eder,
            // böylece "yapıştırılmış" değil "akan" bir his oluşur.
            if (dy < 0f) {
                if (!canScrollDown()) return Offset.Zero
                val applied = collapseBy(-dy * SOFT_FACTOR)
                return if (applied == 0f) Offset.Zero else Offset(0f, -applied)
            }

            if (!isAtTop()) return Offset.Zero
            val applied = expandBy(dy * SOFT_FACTOR)
            return if (applied == 0f) Offset.Zero else Offset(0f, applied)
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            settle(available.y)
            return Velocity.Zero
        }
    }
}

@Composable
internal fun rememberNotifChromeCollapseState(): NotifChromeCollapseState =
    remember { NotifChromeCollapseState() }

/** Listenin kaydırma bağlantısı: tüm kaynaklarda aynı toplanma davranışı. */
@Composable
internal fun rememberNotifChromeConnection(
    state: NotifChromeCollapseState,
    listState: LazyListState
): NestedScrollConnection = remember(state, listState) {
    state.connection(
        canScrollDown = { listState.canScrollForward },
        isAtTop = {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    )
}

// ─── Ortak üst bölüm ─────────────────────────────────────────────────────────

/**
 * Tüm kaynaklarda aynı üst bölüm: geri + başlık + teşhis + yenile, kaynak hapı
 * ve özet, ardından sabit kalan ortak filtre şeridi.
 */
@Composable
internal fun NotifChromeHeader(
    collapse: NotifChromeCollapseState,
    title: String,
    sourceLabel: String,
    sourceLogoId: String,
    isSourceConnected: Boolean,
    sourceBadge: Int,
    summary: String?,
    tabs: List<NotifFilterTab>,
    selectedTabKey: String,
    accentColor: Color,
    onBack: () -> Unit,
    onOpenSourcePicker: () -> Unit,
    onRefresh: () -> Unit,
    onDiagnostics: () -> Unit,
    onSelectTab: (NotifFilterTab) -> Unit,
    /** Seçili kaynak şu anda veri çekiyor mu? (yenile simgesi döner) */
    isRefreshing: Boolean = false,
    /** Okundu durumu yerelde tutulan kaynaklarda "Tümünü okundu" eylemi. */
    onMarkAllRead: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(KitsugiColors.Background)
    ) {
        // Toplanan kısım: yüksekliği yumuşakça küçülür, içerik yukarı kayar.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val collapsed = collapse.collapsedPx.roundToInt()
                    val visibleHeight = (placeable.height - collapsed).coerceAtLeast(0)
                    layout(placeable.width, visibleHeight) {
                        placeable.place(0, -collapsed)
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { collapse.headerHeightPx = it.height.toFloat() }
                    .graphicsLayer { alpha = 1f - collapse.fraction * 0.6f }
            ) {
                NotifTopBarRow(
                    title = title,
                    accentColor = accentColor,
                    isRefreshing = isRefreshing,
                    onBack = onBack,
                    onRefresh = onRefresh,
                    onDiagnostics = onDiagnostics
                )
            }
        }

        // Kaynak hapı bilinçli olarak SABİT: sayfa aşağı kaydırılmışken de kaynak
        // değiştirilebilir kalır (yalnızca başlık barı yukarı kayıp gider).
        NotifSourceRow(
            sourceLabel = sourceLabel,
            sourceLogoId = sourceLogoId,
            isConnected = isSourceConnected,
            unreadBadge = sourceBadge,
            summary = summary,
            accentColor = accentColor,
            onClick = onOpenSourcePicker
        )

        // Sabit kalan alt kısım: filtre şeridi her zaman erişilebilir.
        if (tabs.isNotEmpty()) {
            NotifFilterBand(
                tabs = tabs,
                selectedKey = selectedTabKey,
                accentColor = accentColor,
                onSelect = onSelectTab,
                onMarkAllRead = onMarkAllRead,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        HorizontalDivider(
            color = KitsugiColors.SurfaceStrong.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun NotifTopBarRow(
    title: String,
    accentColor: Color,
    isRefreshing: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onDiagnostics: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.notif_action_back),
                tint = KitsugiColors.TextPrimary
            )
        }
        Text(
            text = title,
            color = KitsugiColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
        )
        IconButton(onClick = onDiagnostics) {
            Icon(
                imageVector = Icons.Rounded.BugReport,
                contentDescription = stringResource(R.string.notif_diag_action),
                tint = KitsugiColors.TextPrimary
            )
        }
        NotifRefreshButton(
            accentColor = accentColor,
            isRefreshing = isRefreshing,
            onClick = onRefresh
        )
    }
}

/**
 * Yenile düğmesi. İstek sürerken simge döner (dokunuşun karşılığı görünür olur);
 * boştayken hiçbir animasyon kurulmaz, böylece gereksiz kare maliyeti oluşmaz.
 */
@Composable
private fun NotifRefreshButton(
    accentColor: Color,
    isRefreshing: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        if (isRefreshing) {
            val spin = rememberInfiniteTransition(label = "notif_refresh_spin").animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 900, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "notif_refresh_angle"
            )
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = stringResource(R.string.notif_action_refresh),
                tint = accentColor,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { rotationZ = spin.value }
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = stringResource(R.string.notif_action_refresh),
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun NotifSourceRow(
    sourceLabel: String,
    sourceLogoId: String,
    isConnected: Boolean,
    unreadBadge: Int,
    summary: String?,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NotificationSourceSelectorPill(
            label = sourceLabel,
            logoId = sourceLogoId,
            isConnected = isConnected,
            unreadCount = unreadBadge,
            accentColor = accentColor,
            onClick = onClick
        )
        Spacer(Modifier.width(10.dp))
        if (!summary.isNullOrBlank()) {
            Text(
                text = summary,
                color = KitsugiColors.TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

// ─── Ortak filtre şeridi ─────────────────────────────────────────────────────

/**
 * Soldan sağa kaydırılabilen ortak sekme şeridi.
 *
 * • Seçili sekme kendiliğinden görünür alana kaydırılır (MAL'da 9 kategori var,
 *   ekranın dışında kalanlar erişilebilir kalır).
 * • Seçim rengi yumuşak animasyonla değişir; tema vurgu rengi kullanılır.
 * • Her kaynak aynı bileşeni kullandığı için boşluk/hizalama farkı kalmaz.
 */
@Composable
internal fun NotifFilterBand(
    tabs: List<NotifFilterTab>,
    selectedKey: String,
    accentColor: Color,
    onSelect: (NotifFilterTab) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Okundu durumu sunucuda tutulmayan kaynaklarda gösterilen son eylem.
     * "Tümünü okundu işaretle" — dokunuş tek seferde rozeti sıfırlar.
     */
    onMarkAllRead: (() -> Unit)? = null
) {
    val listState = rememberLazyListState()
    val selectedIndex = remember(tabs, selectedKey) {
        tabs.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    }
    var firstScroll by remember { mutableStateOf(true) }

    LaunchedEffect(selectedKey, tabs.size) {
        if (tabs.isEmpty()) return@LaunchedEffect
        // Seçili sekmeyi bir öncekiyle birlikte görünür alana getir: kullanıcı
        // hangi bağlamdan geldiğini kaybetmesin.
        val target = (selectedIndex - 1).coerceAtLeast(0)
        if (firstScroll) {
            listState.scrollToItem(target)
            firstScroll = false
        } else {
            listState.animateScrollToItem(target)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(KitsugiColors.Surface)
            .padding(vertical = 4.dp)
    ) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(tabs, key = { it.key }) { tab ->
                val active = tab.key == selectedKey
                NotifFilterChip(
                    tab = tab,
                    active = active,
                    accentColor = accentColor,
                    onClick = { onSelect(tab) }
                )
            }
            onMarkAllRead?.let { action ->
                item(key = "notif_mark_all_read") {
                    MarkAllReadChip(accentColor = accentColor, onClick = action)
                }
            }
        }
    }
}

/** Şeridin sonundaki "Tümünü okundu" eylemi (seçili sekmeyi değiştirmez). */
@Composable
private fun MarkAllReadChip(
    accentColor: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .border(1.dp, accentColor.copy(alpha = 0.35f), shape)
            .tvClickable(shape = shape, onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.DoneAll,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = stringResource(R.string.notif_mark_all_read),
            color = accentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun NotifFilterChip(
    tab: NotifFilterTab,
    active: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    val chipBackground by animateColorAsState(
        targetValue = if (active) accentColor else Color.Transparent,
        animationSpec = tween(durationMillis = 180),
        label = "notif_chip_bg"
    )
    val labelColor by animateColorAsState(
        targetValue = if (active) KitsugiColors.Background else KitsugiColors.TextSecondary,
        animationSpec = tween(durationMillis = 180),
        label = "notif_chip_fg"
    )
    val scale by animateFloatAsState(
        targetValue = if (active) 1f else 0.97f,
        animationSpec = tween(durationMillis = 180),
        label = "notif_chip_scale"
    )

    Row(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(chipBackground)
            .semantics {
                role = Role.Tab
                selected = active
            }
            .tvClickable(shape = shape, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (tab.hasUnread && !active) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
        }
        Text(
            text = tab.label,
            color = labelColor,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
        val count = tab.count
        if (count != null && count > 0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (active) KitsugiColors.Background.copy(alpha = 0.22f)
                        else accentColor.copy(alpha = 0.15f)
                    )
                    .padding(horizontal = 6.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (count > 99) "99+" else count.toString(),
                    color = if (active) KitsugiColors.Background else accentColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ─── Kaynak hapı ─────────────────────────────────────────────────────────────

@Composable
internal fun NotificationSourceSelectorPill(
    label: String,
    logoId: String,
    isConnected: Boolean,
    unreadCount: Int,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(KitsugiColors.Surface)
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.5f),
                shape = shape
            )
            .tvClickable(shape = shape, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        KitsugiPlatformLogo(platformId = logoId, size = 18.dp)
        Text(
            text = label,
            color = KitsugiColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(accentColor)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                    color = KitsugiColors.Background,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else if (isConnected) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(KitsugiColors.AccentGreen)
            )
        }
        Icon(
            imageVector = Icons.Rounded.ArrowDropDown,
            contentDescription = stringResource(R.string.bildirim_kaynagi_sec),
            tint = accentColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

// ─── Ortak liste ─────────────────────────────────────────────────────────────

/**
 * Tüm kaynakların ortak bildirim listesi.
 *
 * Aynı iskelet: aşağı çekip yenileme, sonsuz kaydırma, teşhis/hata kodu bloğu,
 * kısa kaynak notu, zaman grupları ve satır tasarımı.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotifFeedList(
    state: NotifUiState,
    listState: LazyListState,
    collapse: NotifChromeCollapseState,
    accentColor: Color,
    isConnected: Boolean,
    loginRequiredMessage: String,
    emptyMessage: String,
    onItemClick: (NotifItem) -> Unit,
    onRefresh: () -> Unit,
    onUserProfileClick: ((userId: Int, username: String, avatarUrl: String?) -> Unit)? = null,
    hasMore: Boolean = false,
    onLoadMore: (() -> Unit)? = null,
    actionCard: (@Composable () -> Unit)? = null,
    noticeText: String? = state.notice,
    emptyIcon: ImageVector = Icons.Rounded.Notifications
) {
    val connection = rememberNotifChromeConnection(state = collapse, listState = listState)
    val items = remember(state.items) { state.items.distinctBy { it.id } }

    // Bağlantı yoksa ve gösterilecek bir şey de yoksa ortak "giriş gerekli" hâli.
    if (!isConnected && items.isEmpty() && actionCard == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Bağlantı kurulduktan sonra üst bara dönmeden yeniden denenebilsin.
            NotifCenteredEmptyState(
                message = loginRequiredMessage,
                icon = emptyIcon,
                onRetry = onRefresh
            )
        }
        return
    }

    // Sonsuz kaydırma: sona yaklaşınca kaynağın sonraki sayfası istenir.
    val nearListEnd by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            // total == 0 → sayfa henüz ölçülmedi (ör. yandaki AniList filtre sayfası).
            // Bu durumda sayfalama İSTENMEZ; aksi hâlde görünmeyen sekmeler için de
            // ağ isteği tetikleniyordu.
            total > 0 && last >= total - 8
        }
    }
    LaunchedEffect(nearListEnd, hasMore, state.isLoading, state.loadMoreError) {
        if (nearListEnd && hasMore && !state.isLoading && state.loadMoreError == null) {
            onLoadMore?.invoke()
        }
    }

    // Aşağı çekip yenileme: her kaynakta aynı; yalnızca kullanıcı çekince döner.
    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading && isRefreshing) isRefreshing = false
    }
    // Emniyet supabı: kaynak isteği hiç başlamazsa (ör. "zaten yükleniyor" koruması)
    // gösterge sonsuza kadar dönmesin.
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) return@LaunchedEffect
        kotlinx.coroutines.delay(4_000)
        isRefreshing = false
    }
    val pullState = rememberPullToRefreshState()

    val rows = remember(items) { groupNotifRows(items, System.currentTimeMillis()) }
    val errorText = state.error
    val errorCode = state.errorCode

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                onRefresh()
            },
            state = pullState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                KitsugiPullToRefreshIndicator(
                    state = pullState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(connection),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                actionCard?.let { card ->
                    item(key = "notif_action_card") { card() }
                }

                if (!isConnected && items.isEmpty()) {
                    item(key = "notif_login_banner") {
                        NotifNoticeRow(text = loginRequiredMessage, accentColor = accentColor)
                    }
                } else if (!noticeText.isNullOrBlank()) {
                    item(key = "notif_notice") {
                        NotifNoticeRow(text = noticeText, accentColor = accentColor)
                    }
                }

                // Liste dolu ama son istek hata verdiyse sessiz kalma: kompakt bir
                // hata satırı + "Tekrar dene" göster (kaydırma konumu korunur).
                if (errorText != null && items.isNotEmpty()) {
                    item(key = "notif_inline_error") {
                        NotifLoadMoreError(
                            message = errorText,
                            accentColor = accentColor,
                            onRetry = onRefresh
                        )
                    }
                }

                when {
                    state.isLoading && items.isEmpty() -> {
                        item(key = "notif_loading") { NotifLoadingState() }
                    }

                    errorText != null && items.isEmpty() -> {
                        item(key = "notif_error") {
                            NotifCenteredEmptyState(
                                message = errorText,
                                icon = emptyIcon,
                                onRetry = onRefresh
                            )
                        }
                        errorCode?.let { code ->
                            item(key = "notif_error_code") {
                                PlatformErrorCodeChip(
                                    code = code,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }

                    items.isEmpty() -> {
                        item(key = "notif_empty") {
                            NotifCenteredEmptyState(
                                message = emptyMessage,
                                icon = emptyIcon,
                                onRetry = onRefresh
                            )
                        }
                        state.loadMoreError?.let { message ->
                            item(key = "notif_load_more_error_empty") {
                                NotifLoadMoreError(
                                    message = message,
                                    accentColor = accentColor,
                                    onRetry = { onLoadMore?.invoke() }
                                )
                            }
                        }
                    }

                    else -> {
                        itemsIndexed(rows, key = { _, row -> row.item.id }) { _, row ->
                            NotifItemRow(
                                notif = row.item,
                                accentColor = accentColor,
                                onUserProfileClick = onUserProfileClick,
                                group = row.group,
                                groupCount = row.groupCount,
                                onClick = { onItemClick(row.item) }
                            )
                        }
                        // Sayfa zaten aşağı çekilerek yenileniyorsa altta ikinci bir
                        // gösterge çıkmasın (aynı işi iki kez göstermek gürültü).
                        if (state.isLoading && !isRefreshing) {
                            item(key = "notif_footer_loading") {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    KitsugiPlasmaLoader(size = 26.dp)
                                }
                            }
                        }
                        state.loadMoreError?.let { message ->
                            item(key = "notif_load_more_error") {
                                NotifLoadMoreError(
                                    message = message,
                                    accentColor = accentColor,
                                    onRetry = { onLoadMore?.invoke() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "Daha eski bildirimler yüklenemedi • Tekrar dene" satırı (tüm kaynaklarda aynı). */
@Composable
private fun NotifLoadMoreError(
    message: String,
    accentColor: Color,
    onRetry: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = message,
            modifier = Modifier.weight(1f),
            color = KitsugiColors.TextMuted,
            fontSize = 12.sp
        )
        TextButton(onClick = onRetry) {
            Text(
                text = stringResource(R.string.notif_retry),
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ─── Kaynak notu (tek satır, dokununca açılır) ───────────────────────────────

/**
 * Kaynak açıklaması ("bu kaynakta bildirim ucu yoktur…") artık listeyi
 * kaplamıyor: tek satır özet, dokununca yumuşak animasyonla açılır.
 */
@Composable
private fun NotifNoticeRow(
    text: String,
    accentColor: Color
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(KitsugiColors.Surface)
            .tvClickable(shape = RoundedCornerShape(12.dp)) { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(16.dp)
            )
            Text(
                text = text,
                color = KitsugiColors.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            // Açılıp kapanabildiğini belli eden küçük ok (dokunma alanı kartın tamamı).
            Icon(
                imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = KitsugiColors.TextMuted,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(16.dp)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Text(
                text = stringResource(R.string.notif_notice_collapse_hint),
                color = KitsugiColors.TextMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 6.dp, start = 24.dp)
            )
        }
    }
}

// ─── Bildirim satırı ─────────────────────────────────────────────────────────

@Composable
internal fun NotifItemRow(
    notif: NotifItem,
    accentColor: Color,
    onUserProfileClick: ((userId: Int, username: String, avatarUrl: String?) -> Unit)? = null,
    group: NotifDateGroup? = null,
    groupCount: Int = 0,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (group != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(group.labelResId),
                    color = KitsugiColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                if (groupCount > 0) {
                    Text(
                        text = groupCount.toString(),
                        color = KitsugiColors.TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .tvClickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(if (notif.userId != null) CircleShape else RoundedCornerShape(10.dp))
                    .background(KitsugiColors.SurfaceStrong)
                    .then(
                        if (notif.userId != null && notif.userName != null && onUserProfileClick != null) {
                            Modifier.tvClickable(shape = CircleShape) {
                                onUserProfileClick(notif.userId, notif.userName, notif.userAvatarUrl)
                            }
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!notif.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = notif.imageUrl,
                        contentDescription = notif.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsNone,
                        contentDescription = null,
                        tint = KitsugiColors.TextMuted,
                        modifier = Modifier.size(24.dp)
                    )
                }
                // Okunmamış göstergesi
                if (notif.isUnread) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = notif.title,
                        color = KitsugiColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.6f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        KitsugiPlatformLogo(platformId = notif.source, size = 12.dp)
                        Text(
                            text = notif.source,
                            color = KitsugiColors.TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = notif.body,
                    color = KitsugiColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!notif.dateText.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = notif.dateText,
                        color = KitsugiColors.TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }

        HorizontalDivider(
            color = KitsugiColors.SurfaceStrong.copy(alpha = 0.35f),
            modifier = Modifier.padding(start = 80.dp, end = 16.dp)
        )
    }
}

// ─── Ortak boş / yükleniyor hâlleri ─────────────────────────────────────────

@Composable
internal fun NotifLoadingState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            KitsugiPlasmaLoader(size = 48.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.loading),
                color = KitsugiColors.TextMuted,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
internal fun NotifCenteredEmptyState(
    message: String,
    icon: ImageVector = Icons.Rounded.Notifications,
    onRetry: (() -> Unit)? = null
) {
    val accentColor = LocalKitsugiAccent.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = KitsugiColors.TextMuted,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = message,
                color = KitsugiColors.TextMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            if (onRetry != null) {
                Spacer(Modifier.height(16.dp))
                // Boş/hata durumunda tek dokunuşla yenileme: kullanıcı üst bara
                // dönmek zorunda kalmaz (tüm kaynaklarda aynı davranış).
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.14f))
                        .tvClickable(shape = RoundedCornerShape(12.dp), onClick = onRetry)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.notif_retry),
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
