package com.kitsugi.animelist.ui.screens.explore

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogo
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

@Composable
internal fun ExplorePlatform.localizedLabel(): String = when (this) {
    ExplorePlatform.ALL -> stringResource(R.string.explore_source_all_label)
    else -> label
}

@Composable
internal fun ExplorePlatform.localizedShortName(): String = when (this) {
    ExplorePlatform.ALL -> stringResource(R.string.explore_source_all_label)
    else -> shortName
}

@Composable
internal fun ExplorePlatform.localizedDescription(): String = stringResource(
    when (this) {
        ExplorePlatform.ALL -> R.string.explore_platform_all_desc
        ExplorePlatform.AniList -> R.string.explore_platform_anilist_desc
        ExplorePlatform.MAL -> R.string.explore_platform_mal_desc
        ExplorePlatform.TMDB -> R.string.explore_platform_tmdb_desc
        ExplorePlatform.SIMKL -> R.string.explore_platform_simkl_desc
        ExplorePlatform.KITSU -> R.string.explore_platform_kitsu_desc
        ExplorePlatform.SHIKIMORI -> R.string.explore_platform_shikimori_desc
        ExplorePlatform.BANGUMI -> R.string.explore_platform_bangumi_desc
    }
)

/**
 * Keşfet Sayfası için Brave-Tarzı Kaynak Motoru Seçici Hap Buton.
 * Tıklandığında [ExploreSourcePickerSheet] açılır; Tümü ve 6 kaynak (AniList, MAL/Jikan, TMDB, Simkl, Kitsu, Shikimori) listelenir.
 */
@Composable
fun ExploreSourceEngineSelectorPill(
    selectedPlatform: ExplorePlatform,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = LocalKitsugiAccent.current

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(KitsugiColors.SurfaceElevated)
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.35f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        KitsugiPlatformLogo(
            platformId = selectedPlatform.name,
            size = 18.dp
        )
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = selectedPlatform.localizedLabel(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = KitsugiColors.TextPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.ArrowDropDown,
            contentDescription = stringResource(R.string.explore_source_platform_select),
            tint = accentColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Tümü ve 6 Platformlu Keşfet Kaynak Seçim Bottom Sheet Diyalogu.
 * Arama sayfasındaki kaynak seçici tarzında: AniList, MAL/Jikan, TMDB, Simkl, Kitsu ve Shikimori'yi
 * detaylı açıklamaları ve simgeleriyle gösterir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreSourcePickerSheet(
    selectedPlatform: ExplorePlatform,
    onSelectPlatform: (ExplorePlatform) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accentColor = LocalKitsugiAccent.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = KitsugiColors.Surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(KitsugiColors.TextMuted.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.explore_source_picker_title),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = KitsugiColors.TextPrimary
                )
            )
            Text(
                text = stringResource(R.string.explore_source_picker_subtitle),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = KitsugiColors.TextMuted
                ),
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            ExplorePlatform.entries.forEach { platform ->
                val isSelected = platform == selectedPlatform

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isSelected) accentColor.copy(alpha = 0.12f)
                            else KitsugiColors.SurfaceElevated.copy(alpha = 0.5f)
                        )
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) accentColor else Color.Transparent,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable {
                            onSelectPlatform(platform)
                            onDismiss()
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    KitsugiPlatformLogo(
                        platformId = platform.name,
                        size = 30.dp,
                        modifier = Modifier.padding(end = 12.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = platform.localizedLabel(),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isSelected) accentColor else KitsugiColors.TextPrimary
                            )
                        )
                        Text(
                            text = platform.localizedDescription(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = KitsugiColors.TextMuted
                            )
                        )
                    }

                    if (isSelected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Check,
                            contentDescription = stringResource(R.string.secili),
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tümü ve 6 Kaynaklı Hızlı Yatay Çip Çubuğu.
 * Kullanıcının tek dokunuşla kaydırarak platformlar arasında hızlıca geçiş yapmasını sağlar.
 */
@Composable
fun ExploreSourceChipRow(
    selectedPlatform: ExplorePlatform,
    onPlatformSelected: (ExplorePlatform) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp)
) {
    val accentColor = LocalKitsugiAccent.current

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = contentPadding
    ) {
        items(ExplorePlatform.entries) { platform ->
            val isSelected = platform == selectedPlatform

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) accentColor else KitsugiColors.SurfaceElevated)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) accentColor else KitsugiColors.SurfaceElevated.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .tvClickable(shape = RoundedCornerShape(12.dp)) { onPlatformSelected(platform) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    KitsugiPlatformLogo(
                        platformId = platform.name,
                        size = 16.dp
                    )
                    Text(
                        text = platform.localizedShortName(),
                        color = if (isSelected) KitsugiColors.Background else KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Geriye dönük uyumluluk için ExplorePlatformToggle.
 * TV veya eski çağrılar için yatay çip dizilimini veya açılır menüyü sunar.
 */
@Composable
fun ExplorePlatformToggle(
    selectedPlatform: ExplorePlatform,
    onPlatformSelected: (ExplorePlatform) -> Unit,
    modifier: Modifier = Modifier,
    isVertical: Boolean = false
) {
    if (isVertical) {
        Column(
            modifier = modifier
                .clip(RoundedCornerShape(22.dp))
                .background(KitsugiColors.Surface)
        ) {
            val accentColor = LocalKitsugiAccent.current
            ExplorePlatform.entries.forEach { platform ->
                val isSelected = selectedPlatform == platform

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (isSelected) accentColor else KitsugiColors.Surface)
                        .tvClickable(shape = RoundedCornerShape(22.dp)) { onPlatformSelected(platform) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KitsugiPlatformLogo(platformId = platform.name, size = 18.dp)
                        Text(
                            text = platform.localizedLabel(),
                            color = if (isSelected) KitsugiColors.Background else KitsugiColors.TextMuted,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium
                        )
                    }
                }
            }
        }
    } else {
        ExploreSourceChipRow(
            selectedPlatform = selectedPlatform,
            onPlatformSelected = onPlatformSelected,
            modifier = modifier,
            contentPadding = PaddingValues(0.dp)
        )
    }
}
