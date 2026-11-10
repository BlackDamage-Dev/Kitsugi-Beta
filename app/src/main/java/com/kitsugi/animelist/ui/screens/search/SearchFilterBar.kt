package com.kitsugi.animelist.ui.screens.search

import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.utils.TaxonomyCatalog
import com.kitsugi.animelist.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Arama filtre çipleri: Anime / Manga ve MAL / AniList seçici.
 * AniHyou SearchView.kt'deki FilterChipRow yaklaşımından ilham alınmıştır.
 */
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SearchFilterBar(
    selectedMediaType: MediaType,
    selectedPlatform: SearchPlatform,
    onMediaTypeChange: (MediaType) -> Unit,
    onPlatformChange: (SearchPlatform) -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = LocalKitsugiAccent.current

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 2
    ) {
        // Anime / Manga toggle (küçük)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(KitsugiColors.Surface)
        ) {
            listOf(MediaType.Anime to "Anime", MediaType.Manga to "Manga").forEach { (type, label) ->
                val isSelected = selectedMediaType == type

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (isSelected) accentColor else KitsugiColors.Surface)
                        .tvClickable(shape = RoundedCornerShape(18.dp)) { onMediaTypeChange(type) }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) KitsugiColors.Background else KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal
                    )
                }
            }
        }

        // MAL / AniList platform toggle (küçük, yatay kaydırma destekli)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(KitsugiColors.Surface)
                .horizontalScroll(rememberScrollState())
        ) {
            val availablePlatforms = if (selectedMediaType == MediaType.Manga) {
                SearchPlatform.entries.filter { it != SearchPlatform.TMDB }
            } else {
                SearchPlatform.entries
            }

            availablePlatforms.forEach { platform ->
                val isSelected = selectedPlatform == platform

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (isSelected) accentColor else KitsugiColors.Surface)
                        .tvClickable(shape = RoundedCornerShape(18.dp)) { onPlatformChange(platform) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = platform.localizedLabel(),
                        color = if (isSelected) KitsugiColors.Background else KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/** Selected proper names are read from the catalog, never translated or inferred from a key. */
@Composable
fun SearchOrganizationChips(
    organizations: List<OrgFilter>,
    onRemove: (OrgFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (organizations.isEmpty()) return
    val context = LocalContext.current
    val catalog by produceState<TaxonomyCatalog?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            runCatching { TaxonomyCatalog.load(context.applicationContext) }.getOrNull()
        }
    }
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        organizations.forEach { filter ->
            val name = catalog?.organization(filter.canonicalKey)?.name
                ?: stringResource(R.string.search_org_loading)
            // Eski etiket "... • henüz etkin değil" idi. Katalog seçimi artık
            // aramaya uygulandığı için (toggleOrganization → requestSearch) bu
            // uyarı yanıltıcıydı; yalnızca rol + kurum adı gösterilir.
            ActiveFilterChip(
                label = "${orgRoleLabel(filter.role)}: $name",
                onCloseClick = { onRemove(filter) },
            )
        }
    }
}
