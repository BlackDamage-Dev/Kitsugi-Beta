package com.kitsugi.animelist.ui.screens.manga.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.manga.MangaSearchTitleMode
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.utils.tvClickable

/**
 * Manga Keşfet kompakt üst barı.
 *
 * Video eklentisi seçim ekranındaki (Stream) düzenle aynı mantık: tek satır, küçük
 * geri düğmesi, solda başlık + alt satırda canlı özet, sağda yardımcı eylemler.
 * Dikey/yatay modda yüksekliği sabittir; içerik ne kadar uzun olursa olsun bar
 * büyümez, bu yüzden ızgara her iki yönde de ekranın çoğunu kullanır.
 */
@Composable
fun MangaDiscoverTopBar(
    summary: String,
    accent: Color,
    onBack: () -> Unit,
    onShowInfo: () -> Unit,
    onOpenSettings: () -> Unit,
    isResolving: Boolean,
    modifier: Modifier = Modifier,
    onOpenLibrary: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick  = onBack,
            colors   = IconButtonDefaults.iconButtonColors(
                containerColor = KitsugiColors.SurfaceStrong.copy(alpha = 0.45f)
            ),
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.geri),
                tint = KitsugiColors.TextPrimary,
                modifier = Modifier.size(17.dp)
            )
        }

        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.TravelExplore,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.inline_discover_manga_eb24f62),
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (summary.isNotBlank()) {
                Text(
                    text = summary,
                    color = KitsugiColors.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isResolving) {
            com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = accent,
                strokeWidth = 1.6.dp
            )
            Spacer(Modifier.width(8.dp))
        }

        TopBarGlyphButton(
            onClick = onShowInfo,
            contentDescription = stringResource(R.string.manga_discover_more_info)
        ) {
            Icon(
                Icons.Rounded.Translate,
                contentDescription = null,
                tint = if (isResolving) accent else KitsugiColors.TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(6.dp))
        TopBarGlyphButton(
            onClick = onOpenSettings,
            contentDescription = stringResource(R.string.inline_edit_extensions_134b588)
        ) {
            Icon(
                Icons.Rounded.TravelExplore,
                contentDescription = null,
                tint = KitsugiColors.TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
        if (onOpenLibrary != null) {
            Spacer(Modifier.width(6.dp))
            TopBarGlyphButton(
                onClick = onOpenLibrary,
                contentDescription = stringResource(R.string.manga_library_title)
            ) {
                Icon(
                    Icons.Rounded.CollectionsBookmark,
                    contentDescription = null,
                    tint = KitsugiColors.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun TopBarGlyphButton(
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.4f))
            .tvClickable(shape = CircleShape) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Arama modu düğmesi.
 *
 * Sol ikon `Translate`, etiket moda göre değişir. Seçili mod vurgulu; basınca mod
 * değişir (yerel ↔ orijinal). Uzun basış/yardım düğmesi açıklamayı gösterir.
 */
@Composable
fun MangaTitleModeChip(
    mode: MangaSearchTitleMode,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = when (mode) {
        MangaSearchTitleMode.LOCALIZED -> stringResource(R.string.manga_discover_mode_localized)
        MangaSearchTitleMode.ORIGINAL  -> stringResource(R.string.manga_discover_mode_original)
    }
    val shape = RoundedCornerShape(999.dp)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = if (enabled) accent.copy(alpha = 0.16f) else KitsugiColors.SurfaceSoft.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, if (enabled) accent.copy(alpha = 0.45f) else KitsugiColors.Border.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Row(
            Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Rounded.Translate,
                contentDescription = null,
                tint = if (enabled) accent else KitsugiColors.TextMuted,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                color = if (enabled) KitsugiColors.TextPrimary else KitsugiColors.TextMuted,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Kompakt, tek kart içinde toplanmış arama paneli: mod düğmesi + arama alanı +
 * kaynak çipleri. Tümüyle tek bir `Surface` olduğu için "üst üste yığılmış kocaman
 * kutular" görünümü oluşmaz.
 *
 * [compact] yatay (landscape) modda açılır: mod düğmesi ve arama alanı **aynı satırda**
 * durur, böylece üst bölüm ızgaradan yüksekliği çalmaz.
 */
@Composable
fun MangaDiscoverSearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    mode: MangaSearchTitleMode,
    onToggleMode: () -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onShowInfo: (() -> Unit)? = null,
    onChangeSearchName: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    compact: Boolean = false,
    chips: (@Composable () -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = KitsugiColors.Surface.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, KitsugiColors.Border.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
        ) {

            val modeChip: @Composable () -> Unit = {
                MangaTitleModeChip(
                    mode = mode,
                    accent = accent,
                    enabled = enabled,
                    onClick = onToggleMode
                )
            }
            val hint: @Composable () -> Unit = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (onChangeSearchName != null && query.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.manga_browse_change_search_name),
                            color = accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .tvClickable(shape = RoundedCornerShape(999.dp)) { onChangeSearchName() }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    if (onShowInfo != null) {
                        Text(
                            text = stringResource(R.string.manga_discover_mode_chip_hint),
                            color = KitsugiColors.TextMuted,
                            fontSize = 9.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .tvClickable(shape = RoundedCornerShape(999.dp)) { onShowInfo() }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            if (compact) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(Modifier.widthIn(max = 172.dp)) { modeChip() }
                    MangaSearchField(
                        query = query,
                        onQueryChange = onQueryChange,
                        accent = accent,
                        focusRequester = focusRequester,
                        modifier = Modifier.weight(1f)
                    )
                    if (onShowInfo != null) {
                        Icon(
                            Icons.Rounded.Translate,
                            contentDescription = stringResource(R.string.manga_discover_more_info),
                            tint = KitsugiColors.TextSecondary,
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .tvClickable(shape = CircleShape) { onShowInfo() }
                                .padding(5.dp)
                        )
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    modeChip()
                    Spacer(Modifier.weight(1f))
                    hint()
                }
                MangaSearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    accent = accent,
                    focusRequester = focusRequester,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            chips?.invoke()
        }
    }
}

/** Arama alanı: panelin hem dikey hem yatay yerleşiminde aynı görünür. */
@Composable
private fun MangaSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                stringResource(R.string.manga_discover_title_placeholder),
                color = KitsugiColors.TextMuted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = if (query.isNotBlank()) accent else KitsugiColors.TextMuted,
                modifier = Modifier.size(18.dp)
            )
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.temizle),
                        tint = KitsugiColors.TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = TextFieldDefaults.colors(
            focusedContainerColor    = KitsugiColors.SurfaceStrong.copy(alpha = 0.35f),
            unfocusedContainerColor  = KitsugiColors.SurfaceStrong.copy(alpha = 0.25f),
            focusedIndicatorColor    = accent.copy(alpha = 0.55f),
            unfocusedIndicatorColor  = KitsugiColors.Border.copy(alpha = 0.45f),
            focusedTextColor         = KitsugiColors.TextPrimary,
            unfocusedTextColor       = KitsugiColors.TextPrimary,
            cursorColor              = accent
        ),
        modifier = if (focusRequester != null) modifier.focusRequester(focusRequester) else modifier
    )
}

/** Kaynak çipleri satırı; yatayda kaydırılır ve bar yüksekliğini büyütmez. */
@Composable
fun MangaDiscoverChipRow(
    modifier: Modifier = Modifier,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 1.dp)
    ) { content() }
}

/**
 * Sıralama satırı: "Sırala:" etiketi + iki küçük hap düğme.
 * Video keşif ekranındaki sıralama satırıyla aynı görsel dil.
 */
@Composable
fun MangaDiscoverSortRow(
    relevanceLabel: String,
    titleLabel: String,
    isRelevance: Boolean,
    accent: Color,
    label: String,
    onSelectRelevance: () -> Unit,
    onSelectTitle: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = KitsugiColors.TextMuted,
            fontSize = 11.sp,
            maxLines = 1
        )
        Spacer(Modifier.width(8.dp))

        SortPill(text = relevanceLabel, selected = isRelevance, accent = accent, onClick = onSelectRelevance)
        Spacer(Modifier.width(6.dp))
        SortPill(text = titleLabel, selected = !isRelevance, accent = accent, onClick = onSelectTitle)

        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun SortPill(
    text: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (selected) accent.copy(alpha = 0.28f) else KitsugiColors.Surface.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, if (selected) accent.copy(alpha = 0.55f) else KitsugiColors.Border.copy(alpha = 0.35f))
    ) {
        Text(
            text = text,
            color = if (selected) KitsugiColors.TextPrimary else KitsugiColors.TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            maxLines = 1
        )
    }
}
