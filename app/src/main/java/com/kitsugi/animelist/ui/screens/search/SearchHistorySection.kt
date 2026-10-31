package com.kitsugi.animelist.ui.screens.search

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import com.kitsugi.animelist.ui.components.KitsugiChevronIcon
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.kitsugi.animelist.model.MediaType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogo
import com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalIsTvDevice
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import com.kitsugi.animelist.ui.utils.tvClickable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Varsayılan daraltılmış tarayıcı satırı adedi (kompakt görünüm). */
private const val COLLAPSED_BROWSER_ROW_COUNT = 5

/** Satır içi genişletmede gösterilecek azami satır adedi; fazlası Tüm Geçmiş panelinde açılır. */
private const val EXPANDED_BROWSER_ROW_CAP = 10

/** Yazarken canlı omnibox önerilerinde gösterilecek azami satır adedi. */
private const val OMNIBOX_SUGGESTION_LIMIT = 6

/** Kompakt etiket (çip) modunda daraltılmış adet. */
private const val COLLAPSED_CHIP_COUNT = 6
private const val EXPANDED_CHIP_CAP = 14

private const val PREFS_SEARCH_UI = "kitsugi_search_ui"
private const val KEY_HISTORY_VIEW_MODE = "history_compact_view_mode"
private const val VIEW_MODE_BROWSER_LIST = "list"
private const val VIEW_MODE_COMPACT_CHIPS = "chips"

private enum class HistorySheetFilter {
    ALL,
    PINNED,
    FREQUENT
}

private enum class HistoryDateBucket {
    PINNED,
    TODAY,
    YESTERDAY,
    THIS_WEEK,
    OLDER
}

/**
 * Tarayıcı omnibox mimarisinden ilham alan, Kitsugi temasıyla (düz/gradyan vurgu,
 * koyu/AMOLED/açık yüzeyler ve TV odak sistemi) tam uyumlu kompakt arama geçmişi.
 *
 * Başlıca yetenekler:
 * - **Kompakt Tarayıcı Listesi (Varsayılan):** Arama çubuğuyla hizalı yüzey kartı içinde
 *   tek satırlık ince geçmiş satırları, göreceli zaman bilgisi ve kaynak/kapsam rozeti.
 * - **Canlı Omnibox Önerileri & Eşleşme Vurgusu:** Arama çubuğuna yazarken geçmişi anlık
 *   süzer (Türkçe karakter duyarsız) ve eşleşen harfleri tema vurgusuyla belirginleştirir.
 * - **Tek Dokunuşla Çubuğa Aktarma (`↖`):** Aramayı başlatmadan sorguyu arama çubuğuna
 *   taşır ve klavyeyi açar (yazım hatası düzeltme veya sorgu uzatma için).
 * - **Sabitleme (Pin):** Sık kullanılan aramaları en üstte tutar ve temizlemede korur.
 * - **Uzun Basma Hızlı Eylemler:** Sabitle, çubuğa aktar, kaydedilen kaynakla ara, kopyala, sil.
 * - **Silmede Geri Al (Undo) & Güvenli Temizleme Onayı:** Yanlışlıkla silmeye karşı koruma.
 * - **Gizli Arama Modu Hızlı Anahtarı:** Geçmiş kaydını tek dokunuşla duraklatıp sürdürme.
 * - **Tam Geçmiş & Arama Paneli:** Tarih gruplu (`Bugün`, `Dün`, `Bu Hafta`, `Daha Eski`),
 *   anlık arama ve kaynak filtreli tarayıcı geçmişi alt sayfası.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchHistorySection(
    history: List<SearchHistoryItem>,
    onHistoryItemClick: (SearchHistoryItem) -> Unit,
    onRemoveItem: (SearchHistoryItem) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    currentQuery: String = "",
    currentEngine: SearchSourceEngine = SearchSourceEngine.ALL,
    currentScope: SearchScope = SearchScope.ALL_MIXED,
    searchHistoryEnabled: Boolean = true,
    lastDeletedItem: SearchHistoryItem? = null,
    onFillQuery: (String) -> Unit = {},
    onApplyWithSource: (SearchHistoryItem) -> Unit = onHistoryItemClick,
    onTogglePin: (SearchHistoryItem) -> Unit = {},
    onUndoDelete: () -> Unit = {},
    onDismissUndo: () -> Unit = {},
    onClearHistoryConfirmed: (keepPinned: Boolean) -> Unit = { onClearAll() },
    onToggleHistoryRecording: () -> Unit = {}
) {
    val context = LocalContext.current
    val accentColor = LocalKitsugiAccent.current
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current
    val copiedToastText = stringResource(R.string.toast_copied_clipboard)

    val prefs = remember(context) {
        context.getSharedPreferences(PREFS_SEARCH_UI, Context.MODE_PRIVATE)
    }
    var viewMode by rememberSaveable {
        mutableStateOf(
            prefs.getString(KEY_HISTORY_VIEW_MODE, VIEW_MODE_BROWSER_LIST) ?: VIEW_MODE_BROWSER_LIST
        )
    }
    val isBrowserListMode = viewMode == VIEW_MODE_BROWSER_LIST

    // Gürültüyü azalt: boş/1 harfli sorguları at, tekrarları birleştir, sabitlenenleri öne al
    val deduplicatedHistory = remember(history) {
        val seen = HashSet<String>()
        history
            .filter { item ->
                val q = item.query.trim()
                q.length >= 2 && seen.add(q.foldForMatch())
            }
            .sortedWith(
                compareByDescending<SearchHistoryItem> { it.isPinned }
                    .thenByDescending { it.timestamp }
            )
    }

    val trimmedTypedQuery = remember(currentQuery) { currentQuery.trim() }
    val isFilteringByInput = trimmedTypedQuery.isNotEmpty()

    val filteredSuggestions = remember(deduplicatedHistory, trimmedTypedQuery) {
        if (trimmedTypedQuery.isEmpty()) {
            deduplicatedHistory
        } else {
            rankHistoryMatches(deduplicatedHistory, trimmedTypedQuery)
                .take(OMNIBOX_SUGGESTION_LIMIT)
        }
    }

    var expanded by rememberSaveable { mutableStateOf(false) }
    var actionDrawerQueryKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showFullHistorySheet by rememberSaveable { mutableStateOf(false) }
    var showClearConfirmDialog by rememberSaveable { mutableStateOf(false) }

    val hasCardContent = filteredSuggestions.isNotEmpty() || lastDeletedItem != null
    if (!hasCardContent && searchHistoryEnabled && !showFullHistorySheet && !showClearConfirmDialog) return

    val pinnedCount = remember(deduplicatedHistory) {
        deduplicatedHistory.count { it.isPinned }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Gizli arama modu aktifse ince durum şeridi
        AnimatedVisibility(
            visible = !searchHistoryEnabled,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            IncognitoStatusStrip(
                onResumeClick = onToggleHistoryRecording
            )
        }

        if (hasCardContent) {
            val cardShape = RoundedCornerShape(16.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(KitsugiColors.Surface, cardShape)
                    .border(1.dp, KitsugiColors.Border, cardShape)
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
            ) {

                // ── İnce Tarayıcı Başlık Çubuğu ──────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isFilteringByInput) Icons.ManageSearch else Icons.History,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (isFilteringByInput) {
                                stringResource(R.string.search_history_matching)
                            } else {
                                stringResource(R.string.son_aramalar)
                            },
                            color = if (isFilteringByInput) accentColor else KitsugiColors.TextSecondary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (deduplicatedHistory.isNotEmpty()) {
                            val badgeText = if (isFilteringByInput) {
                                "${filteredSuggestions.size}/${deduplicatedHistory.size}"
                            } else {
                                "${deduplicatedHistory.size}"
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(KitsugiColors.SurfaceSoft, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = badgeText,
                                    color = KitsugiColors.TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Tam geçmiş ve arama paneli
                        if (deduplicatedHistory.size > 3) {
                            HeaderMiniIconButton(
                                onClick = { showFullHistorySheet = true },
                                contentDescription = stringResource(R.string.search_history_all_title)
                            ) {
                                Icon(
                                    imageVector = Icons.ManageSearch,
                                    contentDescription = stringResource(R.string.search_history_all_title),
                                    tint = KitsugiColors.TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Görünüm değiştirici (Tarayıcı Listesi <-> Kompakt Etiketler)
                        if (!isFilteringByInput && deduplicatedHistory.isNotEmpty()) {
                            HeaderMiniIconButton(
                                onClick = {
                                    val next = if (isBrowserListMode) VIEW_MODE_COMPACT_CHIPS else VIEW_MODE_BROWSER_LIST
                                    viewMode = next
                                    prefs.edit().putString(KEY_HISTORY_VIEW_MODE, next).apply()
                                },
                                contentDescription = stringResource(R.string.search_history_view_mode_toggle)
                            ) {
                                Icon(
                                    imageVector = if (isBrowserListMode) Icons.GridView else Icons.ViewList,
                                    contentDescription = stringResource(R.string.search_history_view_mode_toggle),
                                    tint = KitsugiColors.TextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        // Gizli arama (geçmiş kaydını duraklat/aç) hızlı butonu
                        HeaderMiniIconButton(
                            onClick = onToggleHistoryRecording,
                            isActive = !searchHistoryEnabled,
                            contentDescription = stringResource(R.string.search_history_incognito_toggle)
                        ) {
                            Icon(
                                imageVector = if (searchHistoryEnabled) Icons.Visibility else Icons.VisibilityOff,
                                contentDescription = stringResource(R.string.search_history_incognito_toggle),
                                tint = if (!searchHistoryEnabled) accentColor else KitsugiColors.TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // Tümünü temizle (onay pencereli)
                        if (deduplicatedHistory.isNotEmpty()) {
                            HeaderMiniIconButton(
                                onClick = { showClearConfirmDialog = true },
                                contentDescription = stringResource(R.string.tumunu_temizle)
                            ) {
                                Icon(
                                    imageVector = Icons.DeleteOutline,
                                    contentDescription = stringResource(R.string.tumunu_temizle),
                                    tint = KitsugiColors.TextMuted,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }

                // ── Geri Al (Undo) Şeridi ────────────────────────────────────
                AnimatedVisibility(
                    visible = lastDeletedItem != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    val deletedQuery = lastDeletedItem?.query?.trim().orEmpty()
                    Column {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = KitsugiColors.Border.copy(alpha = 0.6f)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(accentColor.copy(alpha = 0.08f))
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Restore,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = stringResource(R.string.search_history_undo_deleted, deletedQuery),
                                color = KitsugiColors.TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .tvClickable(shape = RoundedCornerShape(8.dp), onClick = onUndoDelete)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.search_history_undo_action),
                                    color = accentColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = onDismissUndo,
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Close,
                                    contentDescription = stringResource(R.string.temizle),
                                    tint = KitsugiColors.TextMuted,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }

                if (filteredSuggestions.isNotEmpty()) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = KitsugiColors.Border.copy(alpha = 0.65f)
                    )

                    if (isBrowserListMode || isFilteringByInput) {
                        // ── Mod 1: Tarayıcı Omnibox Kompakt Satırları ────────
                        val shownRows = when {
                            isFilteringByInput -> filteredSuggestions
                            expanded -> filteredSuggestions.take(EXPANDED_BROWSER_ROW_CAP)
                            else -> filteredSuggestions.take(COLLAPSED_BROWSER_ROW_COUNT)
                        }
                        val hiddenRowCount = (filteredSuggestions.size - COLLAPSED_BROWSER_ROW_COUNT).coerceAtLeast(0)

                        shownRows.forEachIndexed { index, item ->
                            val itemKey = item.query.trim().foldForMatch()
                            val isActionDrawerOpen = actionDrawerQueryKey == itemKey

                            BrowserHistoryCompactRow(
                                item = item,
                                highlightQuery = trimmedTypedQuery,
                                currentEngine = currentEngine,
                                currentScope = currentScope,
                                isActionDrawerOpen = isActionDrawerOpen,
                                onClick = {
                                    actionDrawerQueryKey = null
                                    onHistoryItemClick(item)
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    actionDrawerQueryKey = if (isActionDrawerOpen) null else itemKey
                                },
                                onFillClick = {
                                    actionDrawerQueryKey = null
                                    onFillQuery(item.query)
                                },
                                onRemoveClick = {
                                    if (isActionDrawerOpen) actionDrawerQueryKey = null
                                    onRemoveItem(item)
                                },
                                onTogglePinClick = {
                                    actionDrawerQueryKey = null
                                    onTogglePin(item)
                                },
                                onSearchWithSavedSourceClick = {
                                    actionDrawerQueryKey = null
                                    onApplyWithSource(item)
                                },
                                onCopyClick = {
                                    actionDrawerQueryKey = null
                                    clipboardManager.setText(AnnotatedString(item.query.trim()))
                                    Toast.makeText(context, copiedToastText, Toast.LENGTH_SHORT).show()
                                }
                            )

                            if (index < shownRows.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 38.dp, end = 10.dp),
                                    thickness = 0.5.dp,
                                    color = KitsugiColors.Border.copy(alpha = 0.42f)
                                )
                            }
                        }

                        // İnce kart alt çubuğu: Daha fazla / Daha az + Tüm Geçmiş
                        if (!isFilteringByInput && hiddenRowCount > 0) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = KitsugiColors.Border.copy(alpha = 0.6f)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .tvClickable(shape = RoundedCornerShape(8.dp)) {
                                            expanded = !expanded
                                        }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = if (expanded) Icons.ExpandLess else Icons.ExpandMore,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = if (expanded) {
                                            stringResource(R.string.search_history_show_less)
                                        } else {
                                            stringResource(R.string.search_history_show_more_count, hiddenRowCount)
                                        },
                                        color = accentColor,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .tvClickable(shape = RoundedCornerShape(8.dp)) {
                                            showFullHistorySheet = true
                                        }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "${stringResource(R.string.search_history_all_title)} ›",
                                        color = KitsugiColors.TextSecondary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    } else {
                        // ── Mod 2: Ultra-Kompakt Etiket (Çip) Görünümü ───────
                        val canCollapseChips = filteredSuggestions.size > COLLAPSED_CHIP_COUNT
                        val shownChips = if (expanded || !canCollapseChips) {
                            filteredSuggestions.take(EXPANDED_CHIP_CAP)
                        } else {
                            filteredSuggestions.take(COLLAPSED_CHIP_COUNT)
                        }
                        val hiddenChips = (filteredSuggestions.size - COLLAPSED_CHIP_COUNT).coerceAtLeast(0)

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            shownChips.forEach { item ->
                                CompactHistoryChip(
                                    item = item,
                                    onClick = { onHistoryItemClick(item) },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onTogglePin(item)
                                    },
                                    onRemove = { onRemoveItem(item) }
                                )
                            }

                            if (canCollapseChips) {
                                val toggleShape = RoundedCornerShape(8.dp)
                                Row(
                                    modifier = Modifier
                                        .height(28.dp)
                                        .clip(toggleShape)
                                        .background(accentColor.copy(alpha = 0.12f), toggleShape)
                                        .tvClickable(shape = toggleShape) { expanded = !expanded }
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = if (expanded) stringResource(R.string.action_see_less) else "+$hiddenChips",
                                        color = accentColor,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Icon(
                                        imageVector = if (expanded) Icons.ExpandLess else Icons.ExpandMore,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Tam Tarayıcı Geçmişi & Arama Alt Sayfası ─────────────────────────────
    if (showFullHistorySheet) {
        SearchHistoryBrowserSheet(
            history = deduplicatedHistory,
            currentEngine = currentEngine,
            currentScope = currentScope,
            lastDeletedItem = lastDeletedItem,
            onDismiss = { showFullHistorySheet = false },
            onItemClick = { item ->
                showFullHistorySheet = false
                onHistoryItemClick(item)
            },
            onApplyWithSource = { item ->
                showFullHistorySheet = false
                onApplyWithSource(item)
            },
            onFillQuery = { q ->
                showFullHistorySheet = false
                onFillQuery(q)
            },
            onTogglePin = onTogglePin,
            onRemoveItem = onRemoveItem,
            onUndoDelete = onUndoDelete,
            onRequestClearAll = { showClearConfirmDialog = true }
        )
    }

    // ── Güvenli Geçmiş Temizleme Onayı ───────────────────────────────────────
    if (showClearConfirmDialog) {
        ClearSearchHistoryConfirmDialog(
            pinnedCount = pinnedCount,
            onConfirm = { keepPinned ->
                showClearConfirmDialog = false
                if (!keepPinned || deduplicatedHistory.size <= pinnedCount) {
                    showFullHistorySheet = false
                }
                onClearHistoryConfirmed(keepPinned)
            },
            onDismiss = { showClearConfirmDialog = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Alt Bileşenler
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun IncognitoStatusStrip(
    onResumeClick: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(accentColor.copy(alpha = 0.08f), shape)
            .border(1.dp, accentColor.copy(alpha = 0.28f), shape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.VisibilityOff,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = stringResource(R.string.search_history_incognito_banner),
            color = KitsugiColors.TextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                .tvClickable(shape = RoundedCornerShape(8.dp), onClick = onResumeClick)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = stringResource(R.string.search_history_incognito_turn_on_record),
                color = accentColor,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun HeaderMiniIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    isActive: Boolean = false,
    icon: @Composable () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(shape)
            .background(
                if (isActive) accentColor.copy(alpha = 0.14f) else Color.Transparent,
                shape
            )
            .tvClickable(shape = shape, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

@Composable
private fun BrowserHistoryCompactRow(
    item: SearchHistoryItem,
    highlightQuery: String,
    currentEngine: SearchSourceEngine,
    currentScope: SearchScope,
    isActionDrawerOpen: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFillClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onTogglePinClick: () -> Unit,
    onSearchWithSavedSourceClick: () -> Unit,
    onCopyClick: () -> Unit,
    exactTimeText: String? = null
) {
    val accentColor = LocalKitsugiAccent.current
    val accentBrush = KitsugiColors.AccentBrush
    val isGradient = KitsugiColors.IsGradient
    val textPrimary = KitsugiColors.TextPrimary

    val highlightedText = remember(item.query, highlightQuery, accentColor, accentBrush, isGradient, textPrimary) {
        buildOmniboxHighlightedQuery(
            fullQuery = item.query.trim(),
            typedQuery = highlightQuery,
            normalColor = textPrimary,
            accentColor = accentColor,
            accentBrush = if (isGradient) accentBrush else null
        )
    }

    val relativeTime = exactTimeText ?: rememberCompactRelativeTime(item.timestamp)
    val hasDistinctSavedSource = item.engine != SearchSourceEngine.ALL ||
        (item.scope != SearchScope.ALL_MIXED && item.scope != SearchScope.ANIME)
    val differsFromCurrentSelection = item.engine != currentEngine || item.scope != currentScope

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isActionDrawerOpen) KitsugiColors.SurfaceSoft.copy(alpha = 0.65f)
                else Color.Transparent
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 38.dp)
                .tvClickable(
                    shape = RoundedCornerShape(8.dp),
                    scaleOnFocus = false,
                    onLongClick = onLongClick,
                    onClick = onClick
                )
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Sol ikon: Sabitlenmişse vurgu yer imi, değilse saat/geçmiş ikonu
            Box(
                modifier = Modifier.size(18.dp),
                contentAlignment = Alignment.Center
            ) {
                if (item.isPinned) {
                    Icon(
                        imageVector = Icons.Bookmark,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(15.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.History,
                        contentDescription = null,
                        tint = if (highlightQuery.isNotBlank()) accentColor.copy(alpha = 0.85f) else KitsugiColors.TextMuted,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            // Sorgu metni (yazarken eşleşen harfler vurgulu)
            Text(
                text = highlightedText,
                color = textPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Kaynak / kapsam mikro rozeti (yalnızca özel bir kaynak/kapsam seçiliyse)
            if (hasDistinctSavedSource) {
                SavedSourceMicroBadge(
                    item = item,
                    onClick = if (differsFromCurrentSelection) onSearchWithSavedSourceClick else onClick
                )
            }

            // Sık aranan sayacı (3+ kez arandıysa zarif mikro etiket)
            if (item.searchCount >= 3 && highlightQuery.isEmpty()) {
                Text(
                    text = "${item.searchCount}×",
                    color = accentColor.copy(alpha = 0.8f),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }


            // Göreceli zaman bilgisi (5dk, 2sa, Dün, 18 Eki)
            if (relativeTime != null) {
                Text(
                    text = relativeTime,
                    color = KitsugiColors.TextMuted.copy(alpha = 0.72f),
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }

            // ↖ Tarayıcı omnibox "Arama çubuğuna aktar / düzenle" butonu
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .tvClickable(shape = CircleShape, onClick = onFillClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.ArrowUpward,
                    contentDescription = stringResource(R.string.search_history_fill_query),
                    tint = KitsugiColors.TextMuted,
                    modifier = Modifier
                        .size(15.dp)
                        .graphicsLayer { rotationZ = -45f }
                )
            }

            // × Tek dokunuşla kaldır
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .tvClickable(shape = CircleShape, onClick = onRemoveClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Close,
                    contentDescription = stringResource(R.string.kaldir),
                    tint = KitsugiColors.TextMuted.copy(alpha = 0.78f),
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        // Uzun basıldığında açılan satır içi hızlı eylem çubuğu
        AnimatedVisibility(
            visible = isActionDrawerOpen,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 38.dp, end = 12.dp, bottom = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickActionPill(
                    icon = {
                        Icon(
                            imageVector = if (item.isPinned) Icons.Bookmark else Icons.BookmarkBorder,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(13.dp)
                        )
                    },
                    label = if (item.isPinned) {
                        stringResource(R.string.search_history_unpin)
                    } else {
                        stringResource(R.string.search_history_pin)
                    },
                    accentHighlight = true,
                    onClick = onTogglePinClick
                )

                QuickActionPill(
                    icon = {
                        Icon(
                            imageVector = Icons.ArrowUpward,
                            contentDescription = null,
                            tint = KitsugiColors.TextSecondary,
                            modifier = Modifier
                                .size(13.dp)
                                .graphicsLayer { rotationZ = -45f }
                        )
                    },
                    label = stringResource(R.string.search_history_fill_query),
                    onClick = onFillClick
                )

                if (differsFromCurrentSelection && hasDistinctSavedSource) {
                    val sourceLabel = buildSavedSourceSummaryLabel(item)
                    QuickActionPill(
                        icon = {
                            KitsugiPlatformLogo(
                                platformId = item.engine.id,
                                size = 13.dp,
                                fallbackTint = accentColor
                            )
                        },
                        label = stringResource(R.string.search_history_search_with_source, sourceLabel),
                        onClick = onSearchWithSavedSourceClick
                    )
                }

                QuickActionPill(
                    icon = {
                        Icon(
                            imageVector = Icons.ContentCopy,
                            contentDescription = null,
                            tint = KitsugiColors.TextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                    },
                    label = stringResource(R.string.search_history_copy),
                    onClick = onCopyClick
                )

                QuickActionPill(
                    icon = {
                        Icon(
                            imageVector = Icons.DeleteOutline,
                            contentDescription = null,
                            tint = KitsugiColors.AccentRed,
                            modifier = Modifier.size(13.dp)
                        )
                    },
                    label = stringResource(R.string.kaldir),
                    destructive = true,
                    onClick = onRemoveClick
                )
            }
        }
    }
}

@Composable
private fun SavedSourceMicroBadge(
    item: SearchHistoryItem,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(KitsugiColors.SurfaceSoft, shape)
            .tvClickable(shape = shape, onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (item.engine != SearchSourceEngine.ALL) {
            KitsugiPlatformLogo(
                platformId = item.engine.id,
                size = 11.dp
            )
        }
        if (item.scope != SearchScope.ALL_MIXED && item.scope != SearchScope.ANIME) {
            Text(
                text = item.scope.localizedLabel(),
                color = KitsugiColors.TextMuted,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun QuickActionPill(
    icon: @Composable () -> Unit,
    label: String,
    accentHighlight: Boolean = false,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    val shape = RoundedCornerShape(8.dp)
    val bgColor = when {
        destructive -> KitsugiColors.AccentRed.copy(alpha = 0.10f)
        accentHighlight -> accentColor.copy(alpha = 0.12f)
        else -> KitsugiColors.SurfaceStrong.copy(alpha = 0.7f)
    }
    val borderColor = when {
        destructive -> KitsugiColors.AccentRed.copy(alpha = 0.3f)
        accentHighlight -> accentColor.copy(alpha = 0.35f)
        else -> KitsugiColors.Border
    }
    val textColor = when {
        destructive -> KitsugiColors.AccentRed
        accentHighlight -> accentColor
        else -> KitsugiColors.TextSecondary
    }

    Row(
        modifier = Modifier
            .clip(shape)
            .background(bgColor, shape)
            .border(0.75.dp, borderColor, shape)
            .tvClickable(shape = shape, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        icon()
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun CompactHistoryChip(
    item: SearchHistoryItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemove: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    val shape = RoundedCornerShape(8.dp)

    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(shape)
            .background(
                if (item.isPinned) accentColor.copy(alpha = 0.10f) else KitsugiColors.SurfaceSoft,
                shape
            )
            .border(
                width = if (item.isPinned) 1.dp else 0.5.dp,
                color = if (item.isPinned) accentColor.copy(alpha = 0.4f) else KitsugiColors.Border.copy(alpha = 0.6f),
                shape = shape
            )
            .tvClickable(
                shape = shape,
                onLongClick = onLongClick,
                onClick = onClick
            )
            .padding(start = 8.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (item.isPinned) {
            Icon(
                imageVector = Icons.Bookmark,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(11.dp)
            )
        }
        Text(
            text = item.query.trim(),
            color = KitsugiColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 155.dp)
        )
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .tvClickable(shape = CircleShape, onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Close,
                contentDescription = stringResource(R.string.kaldir),
                tint = KitsugiColors.TextMuted,
                modifier = Modifier.size(11.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tam Tarayıcı Arama Geçmişi Paneli (KitsugiSheetOrDialog)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SearchHistoryBrowserSheet(
    history: List<SearchHistoryItem>,
    currentEngine: SearchSourceEngine,
    currentScope: SearchScope,
    lastDeletedItem: SearchHistoryItem?,
    onDismiss: () -> Unit,
    onItemClick: (SearchHistoryItem) -> Unit,
    onApplyWithSource: (SearchHistoryItem) -> Unit,
    onFillQuery: (String) -> Unit,
    onTogglePin: (SearchHistoryItem) -> Unit,
    onRemoveItem: (SearchHistoryItem) -> Unit,
    onUndoDelete: () -> Unit,
    onRequestClearAll: () -> Unit
) {
    val context = LocalContext.current
    val accentColor = LocalKitsugiAccent.current
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current
    val copiedToastText = stringResource(R.string.toast_copied_clipboard)
    val listState = rememberLazyListState()

    var filterQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(HistorySheetFilter.ALL) }
    var selectedEngineFilter by rememberSaveable { mutableStateOf<SearchSourceEngine?>(null) }
    var activeActionKey by rememberSaveable { mutableStateOf<String?>(null) }

    val availableEngines = remember(history) {
        history.map { it.engine }
            .filter { it != SearchSourceEngine.ALL }
            .distinct()
    }
    val pinnedTotal = remember(history) { history.count { it.isPinned } }
    val frequentTotal = remember(history) { history.count { it.searchCount >= 2 } }

    val trimmedFilter = filterQuery.trim()
    val filteredItems = remember(history, trimmedFilter, selectedFilter, selectedEngineFilter) {
        val base = history.filter { item ->
            val passCategory = when (selectedFilter) {
                HistorySheetFilter.ALL -> true
                HistorySheetFilter.PINNED -> item.isPinned
                HistorySheetFilter.FREQUENT -> item.searchCount >= 2
            }
            val passEngine = selectedEngineFilter == null || item.engine == selectedEngineFilter
            passCategory && passEngine
        }
        if (trimmedFilter.isEmpty()) base else rankHistoryMatches(base, trimmedFilter)
    }

    val nowMs = remember(history.size, selectedFilter) { System.currentTimeMillis() }
    val groupedSections = remember(filteredItems, nowMs, selectedFilter, trimmedFilter) {
        groupHistoryByBrowserBuckets(
            items = filteredItems,
            nowMs = nowMs,
            separatePinnedBucket = selectedFilter == HistorySheetFilter.ALL && trimmedFilter.isEmpty()
        )
    }
    val clockFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val shortDateFormatter = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }

    KitsugiSheetOrDialog(
        onDismiss = onDismiss,
        heightFraction = 0.82f,
        innerScrollState = listState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 16.dp)
        ) {
            // Başlık satırı
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.History,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.search_history_sheet_title),
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(KitsugiColors.SurfaceSoft, RoundedCornerShape(8.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${history.size}",
                            color = KitsugiColors.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (history.isNotEmpty()) {
                    TextButton(
                        onClick = onRequestClearAll,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.DeleteOutline,
                            contentDescription = null,
                            tint = KitsugiColors.AccentRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.gecmisi_temizle),
                            color = KitsugiColors.AccentRed,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Geçmiş içinde arama çubuğu
            val searchShape = RoundedCornerShape(12.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(searchShape)
                    .background(KitsugiColors.SurfaceSoft, searchShape)
                    .border(1.dp, KitsugiColors.Border, searchShape)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Search,
                    contentDescription = null,
                    tint = if (filterQuery.isNotBlank()) accentColor else KitsugiColors.TextMuted,
                    modifier = Modifier.size(17.dp)
                )
                BasicTextField(
                    value = filterQuery,
                    onValueChange = { filterQuery = it },
                    singleLine = true,
                    cursorBrush = SolidColor(accentColor),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = KitsugiColors.TextPrimary,
                        fontSize = 13.5.sp
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (filterQuery.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.search_history_filter_placeholder),
                                    color = KitsugiColors.TextMuted,
                                    fontSize = 13.sp
                                )
                            }
                            inner()
                        }
                    }
                )
                if (filterQuery.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .tvClickable(shape = CircleShape) { filterQuery = "" },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Close,
                            contentDescription = stringResource(R.string.temizle),
                            tint = KitsugiColors.TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

            }

            Spacer(modifier = Modifier.height(8.dp))

            // Akıllı filtre çipleri (Tümü, Sabitlenenler, Sık Arananlar + Kaynaklar)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SheetFilterChip(
                    label = "${stringResource(R.string.search_history_filter_all)} (${history.size})",
                    selected = selectedFilter == HistorySheetFilter.ALL && selectedEngineFilter == null,
                    onClick = {
                        selectedFilter = HistorySheetFilter.ALL
                        selectedEngineFilter = null
                    }
                )

                if (pinnedTotal > 0) {
                    SheetFilterChip(
                        label = "${stringResource(R.string.search_history_filter_pinned)} ($pinnedTotal)",
                        selected = selectedFilter == HistorySheetFilter.PINNED,
                        onClick = {
                            selectedFilter = if (selectedFilter == HistorySheetFilter.PINNED) {
                                HistorySheetFilter.ALL
                            } else {
                                HistorySheetFilter.PINNED
                            }
                        }
                    )
                }

                if (frequentTotal > 0) {
                    SheetFilterChip(
                        label = "${stringResource(R.string.search_history_filter_frequent)} ($frequentTotal)",
                        selected = selectedFilter == HistorySheetFilter.FREQUENT,
                        onClick = {
                            selectedFilter = if (selectedFilter == HistorySheetFilter.FREQUENT) {
                                HistorySheetFilter.ALL
                            } else {
                                HistorySheetFilter.FREQUENT
                            }
                        }
                    )
                }

                availableEngines.forEach { engine ->
                    val isEngineSelected = selectedEngineFilter == engine
                    SheetFilterChip(
                        label = engine.localizedLabel(),
                        selected = isEngineSelected,
                        leadingIcon = {
                            KitsugiPlatformLogo(
                                platformId = engine.id,
                                size = 12.dp
                            )
                        },
                        onClick = {
                            selectedEngineFilter = if (isEngineSelected) null else engine
                        }
                    )
                }
            }

            // Panel içi Geri Al şeridi
            AnimatedVisibility(
                visible = lastDeletedItem != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                val deletedQuery = lastDeletedItem?.query?.trim().orEmpty()
                Row(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Restore,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = stringResource(R.string.search_history_undo_deleted, deletedQuery),
                        color = KitsugiColors.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(R.string.search_history_undo_action),
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .tvClickable(shape = RoundedCornerShape(6.dp), onClick = onUndoDelete)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.SearchOff,
                            contentDescription = null,
                            tint = KitsugiColors.TextMuted,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = stringResource(R.string.search_history_empty_filter),
                            color = KitsugiColors.TextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    groupedSections.forEach { (bucket, sectionItems) ->
                        item(key = "header_${bucket.name}") {
                            Text(
                                text = bucket.localizedTitle(),
                                color = if (bucket == HistoryDateBucket.PINNED) accentColor else KitsugiColors.TextMuted,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp)
                            )
                        }

                        item(key = "group_card_${bucket.name}") {
                            val groupShape = RoundedCornerShape(14.dp)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(groupShape)
                                    .background(KitsugiColors.SurfaceSoft.copy(alpha = 0.65f), groupShape)
                                    .border(1.dp, KitsugiColors.Border, groupShape)
                            ) {
                                sectionItems.forEachIndexed { idx, item ->
                                    val rowKey = "${bucket.name}_${item.query.trim().foldForMatch()}"
                                    val isOpen = activeActionKey == rowKey
                                    val timeLabel = remember(item.timestamp, bucket) {
                                        if (item.timestamp <= 0L) null
                                        else when (bucket) {
                                            HistoryDateBucket.TODAY, HistoryDateBucket.YESTERDAY ->
                                                clockFormatter.format(Date(item.timestamp))
                                            else -> shortDateFormatter.format(Date(item.timestamp))
                                        }
                                    }

                                    BrowserHistoryCompactRow(
                                        item = item,
                                        highlightQuery = trimmedFilter,
                                        currentEngine = currentEngine,
                                        currentScope = currentScope,
                                        isActionDrawerOpen = isOpen,
                                        exactTimeText = timeLabel,
                                        onClick = { onItemClick(item) },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            activeActionKey = if (isOpen) null else rowKey
                                        },
                                        onFillClick = { onFillQuery(item.query) },
                                        onRemoveClick = {
                                            if (isOpen) activeActionKey = null
                                            onRemoveItem(item)
                                        },
                                        onTogglePinClick = {
                                            activeActionKey = null
                                            onTogglePin(item)
                                        },
                                        onSearchWithSavedSourceClick = { onApplyWithSource(item) },
                                        onCopyClick = {
                                            activeActionKey = null
                                            clipboardManager.setText(AnnotatedString(item.query.trim()))
                                            Toast.makeText(context, copiedToastText, Toast.LENGTH_SHORT).show()
                                        }
                                    )

                                    if (idx < sectionItems.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(start = 38.dp, end = 10.dp),
                                            thickness = 0.5.dp,
                                            color = KitsugiColors.Border.copy(alpha = 0.45f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetFilterChip(
    label: String,
    selected: Boolean,
    leadingIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) accentColor.copy(alpha = 0.14f) else KitsugiColors.SurfaceSoft,
                shape
            )
            .border(
                width = if (selected) 1.dp else 0.75.dp,
                color = if (selected) accentColor else KitsugiColors.Border,
                shape = shape
            )
            .tvClickable(shape = shape, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (leadingIcon != null) leadingIcon()
        Text(
            text = label,
            color = if (selected) accentColor else KitsugiColors.TextSecondary,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Geçmiş Temizleme Onay Penceresi (Sabitlenenleri Koru Seçenekli)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ClearSearchHistoryConfirmDialog(
    pinnedCount: Int,
    onConfirm: (keepPinned: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    val isTvDevice = LocalIsTvDevice.current
    var keepPinned by rememberSaveable(pinnedCount) { mutableStateOf(pinnedCount > 0) }

    val bodyContent: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.search_history_clear_confirm_body),
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            if (pinnedCount > 0) {
                val toggleShape = RoundedCornerShape(12.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(toggleShape)
                        .background(
                            if (keepPinned) accentColor.copy(alpha = 0.10f) else KitsugiColors.SurfaceSoft,
                            toggleShape
                        )
                        .border(
                            width = 1.dp,
                            color = if (keepPinned) accentColor.copy(alpha = 0.4f) else KitsugiColors.Border,
                            shape = toggleShape
                        )
                        .clickable { keepPinned = !keepPinned }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                if (keepPinned) accentColor else Color.Transparent,
                                RoundedCornerShape(5.dp)
                            )
                            .border(
                                width = 1.2.dp,
                                color = if (keepPinned) accentColor else KitsugiColors.TextMuted,
                                shape = RoundedCornerShape(5.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (keepPinned) {
                            Icon(
                                imageVector = Icons.Check,
                                contentDescription = null,
                                tint = KitsugiColors.OnAccent,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.search_history_keep_pinned, pinnedCount),
                        color = KitsugiColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (isTvDevice) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface),
                    shape = RoundedCornerShape(26.dp),
                    border = BorderStroke(1.dp, KitsugiColors.Border),
                    modifier = Modifier.width(420.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.search_history_clear_confirm_title),
                            color = KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        bodyContent()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text(
                                    text = stringResource(R.string.iptal),
                                    color = KitsugiColors.TextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = { onConfirm(keepPinned && pinnedCount > 0) }) {
                                Text(
                                    text = stringResource(R.string.gecmisi_temizle),
                                    color = KitsugiColors.AccentRed,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = KitsugiColors.Surface,
            titleContentColor = KitsugiColors.TextPrimary,
            textContentColor = KitsugiColors.TextSecondary,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = stringResource(R.string.search_history_clear_confirm_title),
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = { bodyContent() },
            confirmButton = {
                TextButton(onClick = { onConfirm(keepPinned && pinnedCount > 0) }) {
                    Text(
                        text = stringResource(R.string.gecmisi_temizle),
                        color = KitsugiColors.AccentRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.iptal),
                        color = KitsugiColors.TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Metin Vurgulama, Eşleştirme ve Tarih Gruplama Yardımcıları
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 1'e 1 karakter katlama (Türkçe büyük/küçük ve aksan duyarsız):
 * Dizi uzunluğunu ASLA değiştirmez, böylece orijinal metin üzerinde karakter
 * aralığı vurgularken (`addStyle`) indeks kayması yaşanmaz.
 */
private fun Char.foldForMatch(): Char = when (this) {
    'İ', 'I', 'ı', 'i', 'î', 'Î' -> 'i'
    'Ş', 'ş' -> 's'
    'Ğ', 'ğ' -> 'g'
    'Ü', 'ü', 'û', 'Û' -> 'u'
    'Ö', 'ö', 'ô', 'Ô' -> 'o'
    'Ç', 'ç' -> 'c'
    'Â', 'â' -> 'a'
    else -> this.lowercaseChar()
}

private fun String.foldForMatch(): String {
    val sb = StringBuilder(length)
    for (i in indices) sb.append(this[i].foldForMatch())
    return sb.toString()
}

private fun rankHistoryMatches(
    items: List<SearchHistoryItem>,
    rawQuery: String
): List<SearchHistoryItem> {
    val needle = rawQuery.trim().foldForMatch()
    if (needle.isEmpty()) return items
    val tokens = needle.split(' ').filter { it.isNotEmpty() }

    data class Scored(val item: SearchHistoryItem, val tier: Int)

    return items.mapNotNull { item ->
        val hay = item.query.trim().foldForMatch()
        val tier = when {
            hay == needle -> 0
            hay.startsWith(needle) -> 1
            hay.contains(" $needle") -> 2
            hay.contains(needle) -> 3
            tokens.size > 1 && tokens.all { hay.contains(it) } -> 4
            else -> return@mapNotNull null
        }
        Scored(item, tier)
    }.sortedWith(
        compareBy<Scored> { it.tier }
            .thenByDescending { it.item.isPinned }
            .thenByDescending { it.item.searchCount }
            .thenByDescending { it.item.timestamp }
    ).map { it.item }
}

private fun buildOmniboxHighlightedQuery(
    fullQuery: String,
    typedQuery: String,
    normalColor: Color,
    accentColor: Color,
    accentBrush: androidx.compose.ui.graphics.Brush?
): AnnotatedString {
    val cleanNeedle = typedQuery.trim().foldForMatch()
    if (cleanNeedle.isEmpty() || fullQuery.isEmpty()) {
        return buildAnnotatedString {
            pushStyle(SpanStyle(color = normalColor))
            append(fullQuery)
            pop()
        }
    }

    val foldedFull = fullQuery.foldForMatch()
    val highlightMask = BooleanArray(fullQuery.length)

    val directIndex = foldedFull.indexOf(cleanNeedle)
    if (directIndex >= 0) {
        val end = (directIndex + cleanNeedle.length).coerceAtMost(fullQuery.length)
        for (i in directIndex until end) highlightMask[i] = true
    } else {
        cleanNeedle.split(' ')
            .filter { it.isNotEmpty() }
            .forEach { token ->
                var searchFrom = 0
                while (searchFrom < foldedFull.length) {
                    val idx = foldedFull.indexOf(token, startIndex = searchFrom)
                    if (idx < 0) break
                    val end = (idx + token.length).coerceAtMost(fullQuery.length)
                    for (i in idx until end) highlightMask[i] = true
                    searchFrom = end
                }
            }
    }

    val highlightStyle = if (accentBrush != null) {
        SpanStyle(brush = accentBrush, fontWeight = FontWeight.Bold)
    } else {
        SpanStyle(color = accentColor, fontWeight = FontWeight.Bold)
    }
    val normalStyle = SpanStyle(color = normalColor, fontWeight = FontWeight.Medium)

    return buildAnnotatedString {
        var i = 0
        while (i < fullQuery.length) {
            val isHighlighted = highlightMask[i]
            var j = i + 1
            while (j < fullQuery.length && highlightMask[j] == isHighlighted) {
                j++
            }
            pushStyle(if (isHighlighted) highlightStyle else normalStyle)
            append(fullQuery.substring(i, j))
            pop()
            i = j
        }
    }
}

@Composable
private fun buildSavedSourceSummaryLabel(item: SearchHistoryItem): String {
    val enginePart = if (item.engine != SearchSourceEngine.ALL) item.engine.localizedLabel() else null
    val scopePart = if (item.scope != SearchScope.ALL_MIXED) item.scope.localizedLabel() else null
    return when {
        enginePart != null && scopePart != null -> "$enginePart · $scopePart"
        enginePart != null -> enginePart
        scopePart != null -> scopePart
        else -> item.engine.localizedLabel()
    }
}

@Composable
private fun rememberCompactRelativeTime(timestampMs: Long): String? {
    if (timestampMs <= 0L) return null
    val configuration = LocalConfiguration.current
    val justNow = stringResource(R.string.search_history_time_just_now)
    val yesterday = stringResource(R.string.search_history_group_yesterday)
    val nowMs = remember(timestampMs) { System.currentTimeMillis() }
    val diffMs = (nowMs - timestampMs).coerceAtLeast(0L)
    val minutes = diffMs / 60_000L
    val hours = diffMs / 3_600_000L
    val days = diffMs / 86_400_000L

    return when {
        minutes < 1L -> justNow
        minutes < 60L -> stringResource(R.string.search_history_time_minutes, minutes.toInt())
        hours < 24L -> stringResource(R.string.search_history_time_hours, hours.toInt())
        days == 1L -> yesterday
        days < 7L -> stringResource(R.string.search_history_time_days, days.toInt())
        else -> remember(timestampMs, configuration) {
            SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(timestampMs))
        }
    }
}

@Composable
private fun HistoryDateBucket.localizedTitle(): String = stringResource(
    when (this) {
        HistoryDateBucket.PINNED -> R.string.search_history_filter_pinned
        HistoryDateBucket.TODAY -> R.string.search_history_group_today
        HistoryDateBucket.YESTERDAY -> R.string.search_history_group_yesterday
        HistoryDateBucket.THIS_WEEK -> R.string.search_history_group_this_week
        HistoryDateBucket.OLDER -> R.string.search_history_group_older
    }
)

private fun groupHistoryByBrowserBuckets(
    items: List<SearchHistoryItem>,
    nowMs: Long,
    separatePinnedBucket: Boolean
): List<Pair<HistoryDateBucket, List<SearchHistoryItem>>> {
    if (items.isEmpty()) return emptyList()

    val cal = Calendar.getInstance().apply {
        timeInMillis = nowMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val startOfToday = cal.timeInMillis
    val startOfYesterday = startOfToday - 86_400_000L
    val startOfWeek = startOfToday - 6L * 86_400_000L

    val buckets = linkedMapOf<HistoryDateBucket, MutableList<SearchHistoryItem>>()

    items.forEach { item ->
        val bucket = when {
            separatePinnedBucket && item.isPinned -> HistoryDateBucket.PINNED
            item.timestamp >= startOfToday -> HistoryDateBucket.TODAY
            item.timestamp >= startOfYesterday -> HistoryDateBucket.YESTERDAY
            item.timestamp >= startOfWeek -> HistoryDateBucket.THIS_WEEK
            else -> HistoryDateBucket.OLDER
        }
        buckets.getOrPut(bucket) { mutableListOf() }.add(item)
    }

    return HistoryDateBucket.entries.mapNotNull { key ->
        buckets[key]?.takeIf { it.isNotEmpty() }?.let { key to it }
    }
}
