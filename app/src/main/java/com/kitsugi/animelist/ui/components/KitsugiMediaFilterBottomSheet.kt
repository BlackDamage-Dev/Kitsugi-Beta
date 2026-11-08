@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kitsugi.animelist.ui.components
import com.kitsugi.animelist.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.ReadOnlyComposable
import androidx.annotation.StringRes
import com.kitsugi.animelist.ui.components.KitsugiButton

import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.utils.kitsugiVerticalScroll
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent

data class KitsugiGenreItem(
    val id: String,
    val icon: ImageVector,
    @StringRes val nameRes: Int,
    val keywords: List<String>
) {
    @Composable
    @ReadOnlyComposable
    fun displayLabel(): String = stringResource(nameRes)
}

val KITSUGI_MEDIA_GENRES = listOf(
    KitsugiGenreItem("ALL", Icons.Sparkles, R.string.studio_type_all, emptyList()),
    KitsugiGenreItem("DRAMA", Icons.Theater, R.string.genre_drama, listOf("drama", "dram")),
    KitsugiGenreItem("ACTION", Icons.Flame, R.string.genre_action, listOf("action", "aksiyon")),
    KitsugiGenreItem("ROMANCE", Icons.HeartSolid, R.string.genre_romance, listOf("romance", "romantik", "romantizm")),
    KitsugiGenreItem("COMEDY", Icons.Laugh, R.string.genre_comedy, listOf("comedy", "komedi")),
    KitsugiGenreItem("FANTASY", Icons.WandSparkles, R.string.genre_fantasy, listOf("fantasy", "fantastik")),
    KitsugiGenreItem("SCIFI", Icons.Rocket, R.string.genre_scifi, listOf("sci-fi", "scifi", "science fiction", "bilim kurgu")),
    KitsugiGenreItem("MYSTERY", Icons.Search, R.string.genre_mystery, listOf("mystery", "gizem")),
    KitsugiGenreItem("HORROR", Icons.Ghost, R.string.genre_horror, listOf("horror", "korku")),
    KitsugiGenreItem("SLICEOFLIFE", Icons.Coffee, R.string.genre_slice_of_life, listOf("slice of life", "günlük yaşam", "iyileştirici", "iyilestirici")),
    KitsugiGenreItem("ADVENTURE", Icons.Compass, R.string.genre_adventure, listOf("adventure", "macera")),
    KitsugiGenreItem("SUPERNATURAL", Icons.Moon, R.string.genre_supernatural, listOf("supernatural", "doğaüstü", "dogaustu")),
    KitsugiGenreItem("PSYCHOLOGICAL", Icons.Brain, R.string.genre_psychological, listOf("psychological", "psikolojik")),
    KitsugiGenreItem("SPORTS", Icons.Dumbbell, R.string.genre_sports, listOf("sports", "spor")),
    KitsugiGenreItem("THRILLER", Icons.VenetianMask, R.string.genre_thriller, listOf("thriller", "suspense", "gerilim")),
    KitsugiGenreItem("MECHA", Icons.Bot, R.string.genre_mecha, listOf("mecha", "robot")),
    KitsugiGenreItem("MUSIC", Icons.AudioLines, R.string.genre_music, listOf("music", "müzik", "muzik")),
    KitsugiGenreItem("HISTORICAL", Icons.Landmark, R.string.genre_historical, listOf("historical", "tarihi", "tarih")),
    KitsugiGenreItem("MILITARY", Icons.Swords, R.string.genre_military, listOf("military", "askeri", "savaş", "savas")),
    KitsugiGenreItem("SCHOOL", Icons.School, R.string.genre_school, listOf("school", "okul"))
)

enum class KitsugiGridSortOption(val icon: ImageVector, @StringRes val titleRes: Int) {
    DEFAULT(Icons.Trophy, R.string.studio_sort_default),
    SCORE_DESC(Icons.TrendingDown, R.string.sort_score_desc),
    SCORE_ASC(Icons.TrendingUp, R.string.sort_score_asc),
    POPULARITY_DESC(Icons.Users, R.string.sort_popularity_desc),
    YEAR_DESC(Icons.CalendarArrowDown, R.string.sort_year_desc),
    YEAR_ASC(Icons.CalendarArrowUp, R.string.sort_year_asc),
    TITLE_ASC(Icons.ArrowDownAZ, R.string.studio_sort_title_asc),
    TITLE_DESC(Icons.ArrowUpZA, R.string.studio_sort_title_desc);

    @Composable
    @ReadOnlyComposable
    fun displayLabel(): String = stringResource(titleRes)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KitsugiMediaFilterBottomSheet(
    initialGenreId: String,
    initialSortOption: KitsugiGridSortOption,
    onDismissRequest: () -> Unit,
    onApply: (genreId: String, sortOption: KitsugiGridSortOption) -> Unit,
    onReset: () -> Unit,
    /**
     * Filtrelerin uygulanacağı kaynağın adı (örn. "AniList"). Verilirse çiplerin hangi kaynakta
     * sunucu tarafında çalıştığı bilgisi başlıkta ve çiplerde gösterilir.
     */
    sourceLabel: String? = null,
    /** Kaynağın kendi API'sinde süzebildiği tür kimlikleri. `null` = bilgi yok (rozet çizilmez). */
    serverGenreIds: Set<String>? = null,
    /** Kaynağın kendi API'sinde sıralayabildiği seçenekler. `null` = bilgi yok (rozet çizilmez). */
    serverSorts: Set<KitsugiGridSortOption>? = null
) {
    val accentColor = LocalKitsugiAccent.current

    var selectedGenreId by remember { mutableStateOf(initialGenreId) }
    var selectedSortOption by remember { mutableStateOf(initialSortOption) }

    fun sortIsServer(sort: KitsugiGridSortOption): Boolean =
        serverSorts == null || serverSorts.contains(sort)

    fun genreIsServer(genreId: String): Boolean =
        serverGenreIds == null || genreId == "ALL" || serverGenreIds.contains(genreId)

    val localGenreLabel: String? =
        if (serverGenreIds != null && !genreIsServer(selectedGenreId)) {
            KITSUGI_MEDIA_GENRES.firstOrNull { it.id == selectedGenreId }?.displayLabel()
        } else null
    val localSortLabel: String? =
        if (serverSorts != null && !sortIsServer(selectedSortOption)) {
            selectedSortOption.displayLabel()
        } else null
    val localSelectionSummary: String = listOfNotNull(localGenreLabel, localSortLabel).joinToString(" · ")

    KitsugiSheetOrDialog(onDismiss = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Header Row (Sıfırla - Başlık - Uygula)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        selectedGenreId = "ALL"
                        selectedSortOption = KitsugiGridSortOption.DEFAULT
                        onReset()
                        onDismissRequest()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.sifirla),
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = stringResource(R.string.filtre_ve_siralama),
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                KitsugiButton(
                    onClick = {
                        onApply(selectedGenreId, selectedSortOption)
                        onDismissRequest()
                    },
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(
                        text = "Uygula",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Kaynak bilgisi: hangi seçenek API'de, hangisi cihazda uygulanıyor?
            if (sourceLabel != null && (serverGenreIds != null || serverSorts != null)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 2.dp)
                ) {
                    KitsugiEmojiLabel(
                        label = stringResource(R.string.filtre_kaynak_sunucu_bilgi, sourceLabel),
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        iconSize = 12.dp,
                        icon = Icons.Cloud
                    )
                    if (localSelectionSummary.isNotBlank()) {
                        KitsugiEmojiLabel(
                            label = stringResource(R.string.filtre_kaynak_yerel_bilgi, localSelectionSummary),
                            color = KitsugiColors.TextMuted,
                            style = MaterialTheme.typography.labelSmall,
                            iconSize = 12.dp,
                            icon = Icons.Smartphone
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .kitsugiVerticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                // ── 1. SIRALAMA VE AYIRMA ────────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Sort,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.inline_sort_group_f713972),
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KitsugiGridSortOption.entries.forEach { sort ->
                        val isSelected = selectedSortOption == sort
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) accentColor.copy(alpha = 0.22f)
                                    else KitsugiColors.SurfaceSoft
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) accentColor else Color.Transparent,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { selectedSortOption = sort }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                KitsugiEmojiLabel(
                                    label = sort.displayLabel(),
                                    color = (if (isSelected) accentColor else KitsugiColors.TextPrimary)
                                        .copy(alpha = if (sortIsServer(sort)) 1f else 0.62f),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    style = MaterialTheme.typography.bodySmall,
                                    iconSize = 13.dp,
                                    icon = if (sortIsServer(sort)) sort.icon else Icons.Smartphone
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Check,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── 2. TÜR FİLTRESİ ──────────────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.FilterAlt,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.inline_genre_filter_ca7a745),
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KITSUGI_MEDIA_GENRES.forEach { genre ->
                        val isSelected = selectedGenreId == genre.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) accentColor.copy(alpha = 0.22f)
                                    else KitsugiColors.SurfaceSoft
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) accentColor else Color.Transparent,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { selectedGenreId = genre.id }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                KitsugiEmojiLabel(
                                    label = genre.displayLabel(),
                                    color = (if (isSelected) accentColor else KitsugiColors.TextPrimary)
                                        .copy(alpha = if (genreIsServer(genre.id)) 1f else 0.62f),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    style = MaterialTheme.typography.bodySmall,
                                    iconSize = 13.dp,
                                    icon = if (genreIsServer(genre.id)) genre.icon else Icons.Smartphone
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Check,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(14.dp)
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
