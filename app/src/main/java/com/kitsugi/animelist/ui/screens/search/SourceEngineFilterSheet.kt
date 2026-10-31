package com.kitsugi.animelist.ui.screens.search
import com.kitsugi.animelist.R
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.ui.components.KitsugiButton

import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import com.kitsugi.animelist.ui.theme.gradient.Slider
import androidx.compose.material3.SliderDefaults
import com.kitsugi.animelist.ui.theme.gradient.Switch
import androidx.compose.material3.SwitchDefaults
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.ui.components.KitsugiEmojiLabel
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogo
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.utils.toLocalizedTagLabel
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent

/**
 * 8 Katalog Motoruna Özel Kapsamlı Filtreleme Bottom Sheet'i.
 * Seçilen motora uygun ince ayarları (puan, yıl, süre, etiket güven oranı, yaş sınırı vb.) sunar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceEngineFilterSheet(
    uiState: SearchUiState,
    viewModel: SearchViewModel,
    onOpenGenresTags: () -> Unit,
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
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Başlık & Sıfırla
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Kaynak emojisi yerine platformun orijinal logosu.
                    KitsugiPlatformLogo(
                        platformId = uiState.selectedEngine.id,
                        size = 26.dp,
                        fallbackTint = KitsugiColors.TextPrimary
                    )
                    Column {
                        Text(
                            text = "${uiState.selectedEngine.localizedLabel()} ${stringResource(R.string.filtreler)}",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = KitsugiColors.TextPrimary
                            )
                        )
                        Text(
                            text = stringResource(R.string.inline_customize_your_search_criteria_7608a6b),
                            style = MaterialTheme.typography.bodySmall.copy(color = KitsugiColors.TextMuted)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = { viewModel.resetEngineFilters(uiState.selectedEngine) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.inline_reset_8fb7f0b), color = KitsugiColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }

            // ── Kategori (Kapsam) Seçimi ─────────────────────────────────────
            // Arama sayfasındaki kategori çipleri buraya taşındı: sağ üstteki
            // buton bu sheet üzerinden tüm kapsam/sıralama/filtre öğelerini kapsar.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                KitsugiEmojiLabel(
                    label = stringResource(R.string.search_filter_category),
                    icon = Icons.FolderOpen,
                    iconSize = 16.dp,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = KitsugiColors.TextPrimary
                    )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    uiState.selectedEngine.availableScopes().forEach { scope ->
                        val isSelected = scope == uiState.selectedScope
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSelected) accentColor.copy(alpha = 0.18f)
                                    else KitsugiColors.SurfaceElevated.copy(alpha = 0.7f)
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) accentColor else Color.Transparent,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { viewModel.setScope(scope) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = scope.localizedDisplayLabel(),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) accentColor else KitsugiColors.TextSecondary
                                )
                            )
                        }
                    }
                }
            }

            // ── Sıralama ─────────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.inline_sort_f3f9004),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = KitsugiColors.TextPrimary
                    )
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sortOptionsForEngine(uiState.selectedEngine).forEach { (key, label) ->
                        val isSelected = key == getCurrentSortKey(uiState)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) accentColor.copy(alpha = 0.2f)
                                    else KitsugiColors.SurfaceElevated.copy(alpha = 0.6f)
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) accentColor else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    applyEngineSort(uiState.selectedEngine, key, viewModel)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            KitsugiEmojiLabel(
                                label = localizedSourceSearchLabel(label),
                                color = if (isSelected) accentColor else KitsugiColors.TextSecondary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // ── Tümü modu bilgisi ────────────────────────────────────────────
            if (uiState.selectedEngine == SearchSourceEngine.ALL) {
                Text(
                    text = stringResource(R.string.inline_searching_all_sources_across_6_817f37d),
                    style = MaterialTheme.typography.bodySmall.copy(color = KitsugiColors.TextMuted)
                )
            }

            // ── Hızlı Filtreler ──────────────────────────────────────────────
            // Ana sayfadan kaldırılan hızlı filtre çipleri (format, durum, ülke
            // vb.) işlev kaybı olmaması için bu sheet'e taşındı.
            if (uiState.selectedEngine != SearchSourceEngine.ALL) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.inline_quick_filters_dee9b0a),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = KitsugiColors.TextPrimary
                        )
                    )
                    when (uiState.selectedEngine) {
                        SearchSourceEngine.ANILIST -> AniListQuickChips(uiState, viewModel)
                        SearchSourceEngine.MAL -> MalQuickChips(uiState, viewModel)
                        SearchSourceEngine.TMDB -> TmdbQuickChips(uiState, viewModel)
                        SearchSourceEngine.SHIKIMORI -> ShikimoriQuickChips(uiState, viewModel)
                        SearchSourceEngine.KITSU -> KitsuQuickChips(uiState, viewModel)
                        SearchSourceEngine.SIMKL -> SimklQuickChips(uiState, viewModel)
                        SearchSourceEngine.BANGUMI -> BangumiQuickChips(uiState, viewModel)
                        SearchSourceEngine.ALL -> Unit
                    }
                }
            }

            // Motor Gövdesi
            when (uiState.selectedEngine) {
                SearchSourceEngine.ANILIST -> AniListFullFiltersContent(uiState, viewModel, onOpenGenresTags)
                SearchSourceEngine.MAL -> MalFullFiltersContent(uiState, viewModel)
                SearchSourceEngine.TMDB -> TmdbFullFiltersContent(uiState, viewModel)
                SearchSourceEngine.SHIKIMORI -> ShikimoriFullFiltersContent(uiState, viewModel)
                SearchSourceEngine.KITSU -> KitsuFullFiltersContent(uiState, viewModel)
                SearchSourceEngine.SIMKL -> SimklFullFiltersContent(uiState, viewModel)
                SearchSourceEngine.BANGUMI -> BangumiFullFiltersContent(uiState, viewModel)
                SearchSourceEngine.ALL -> AllFullFiltersContent()
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Uygula Butonu
            KitsugiButton(
                onClick = {
                    viewModel.search()
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.inline_show_results_f77b61a),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ANILIST İÇERİK
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AniListFullFiltersContent(
    uiState: SearchUiState,
    viewModel: SearchViewModel,
    onOpenGenresTags: () -> Unit
) {
    val filters = uiState.aniListSpecificFilters
    val accentColor = LocalKitsugiAccent.current

    // Türler ve Etiketler Seçici
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KitsugiColors.SurfaceElevated)
            .clickable(onClick = onOpenGenresTags)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = stringResource(R.string.inline_genres_tags_4731985),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = KitsugiColors.TextPrimary
                )
            )
            val selectedCount = filters.genres.size + filters.tags.size + filters.excludedGenres.size
            Text(
                text = if (selectedCount > 0) stringResource(R.string.ui2_filter_selected_count, selectedCount) else stringResource(R.string.ui2_all_filters_selected_hint),
                style = MaterialTheme.typography.bodySmall.copy(color = if (selectedCount > 0) accentColor else KitsugiColors.TextMuted)
            )
        }
        Text(stringResource(R.string.inline_edit_51d5ec9), color = accentColor, style = MaterialTheme.typography.labelMedium)
    }

    // Min Tag Rank
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val currentRank = filters.minimumTagRank ?: 18
        Text(
            text = stringResource(R.string.inline_minimum_tag_confidence_1_s_913e7a7, (currentRank).toString()),
            style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary)
        )
        Slider(
            value = currentRank.toFloat(),
            onValueChange = {
                viewModel.updateAniListFilters(filters.copy(minimumTagRank = it.toInt()))
            },
            valueRange = 0f..100f,
            steps = 9,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor
            )
        )
    }

    // Min Puan
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val minScore = filters.minScore ?: 0
        Text(
            text = stringResource(R.string.inline_minimum_average_score_1_s_d231d85, (if (minScore > 0) "%$minScore" else stringResource(R.string.filter_all)).toString()),
            style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary)
        )
        Slider(
            value = minScore.toFloat(),
            onValueChange = {
                viewModel.updateAniListFilters(filters.copy(minScore = if (it.toInt() == 0) null else it.toInt()))
            },
            valueRange = 0f..100f,
            steps = 9,
            colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
        )
    }

    // Lisansörler (Licensed By)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.inline_official_broadcaster_license_ef77ae4), style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary))
        val platforms = listOf("Crunchyroll", "Netflix", "HIDIVE", "Hulu", "Disney Plus")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            platforms.forEach { lic ->
                val isSelected = filters.licensedBy.contains(lic)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.2f) else KitsugiColors.SurfaceElevated)
                        .border(1.dp, if (isSelected) accentColor else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable {
                            val newLic = if (isSelected) filters.licensedBy - lic else filters.licensedBy + lic
                            viewModel.updateAniListFilters(filters.copy(licensedBy = newLic))
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(lic, style = MaterialTheme.typography.labelSmall.copy(color = if (isSelected) accentColor else KitsugiColors.TextSecondary))
                }
            }
        }
    }

    // 18+ Yetişkin İçerik
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(stringResource(R.string.inline_adult_content_18_hentai_6090744), style = MaterialTheme.typography.bodyMedium.copy(color = KitsugiColors.TextPrimary))
            Text(stringResource(R.string.inline_include_18_titles_in_search_516a97e), style = MaterialTheme.typography.bodySmall.copy(color = KitsugiColors.TextMuted))
        }
        Switch(
            checked = filters.isAdult ?: false,
            onCheckedChange = { viewModel.updateAniListFilters(filters.copy(isAdult = it)) },
            colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.5f))
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MAL İÇERİK
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MalFullFiltersContent(uiState: SearchUiState, viewModel: SearchViewModel) {
    val filters = uiState.malSpecificFilters
    val accentColor = LocalKitsugiAccent.current

    // 1. Format / Tip (Type)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        KitsugiEmojiLabel(label = "Format:", icon = Icons.Clapperboard, iconSize = 13.dp, style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary))
        val types = listOf(
            null to "Tümü",
            "tv" to "TV Dizisi",
            "movie" to "Film",
            "ova" to "OVA",
            "special" to "Özel Bölüm",
            "ona" to "ONA (Web)",
            "music" to "Müzik Videosu"
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            types.forEach { (typeCode, label) ->
                val isSelected = filters.type == typeCode
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.2f) else KitsugiColors.SurfaceElevated)
                        .border(1.dp, if (isSelected) accentColor else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { viewModel.updateMalFilters(filters.copy(type = typeCode)) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    KitsugiEmojiLabel(
                        label = localizedSourceSearchLabel(label),
                        color = if (isSelected) accentColor else KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }

    // 2. Yayın Durumu (Status)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        KitsugiEmojiLabel(label = "Durum:", icon = Icons.Satellite, iconSize = 13.dp, style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary))
        val statuses = listOf(
            null to "Tümü",
            "airing" to "Yayınlanıyor",
            "complete" to "Tamamlandı",
            "upcoming" to "Yakında"
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            statuses.forEach { (statusCode, label) ->
                val isSelected = filters.status == statusCode
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.2f) else KitsugiColors.SurfaceElevated)
                        .border(1.dp, if (isSelected) accentColor else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { viewModel.updateMalFilters(filters.copy(status = statusCode)) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    KitsugiEmojiLabel(
                        label = localizedSourceSearchLabel(label),
                        color = if (isSelected) accentColor else KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }

    // 3. Sıralama Ölçütü (Order By)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.inline_sort_7d09458), style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary))
        val orderOptions = listOf(
            "popularity" to "🔥 Popülerlik",
            "score" to "⭐ Puan",
            "rank" to "🏆 Sıralama",
            "favorites" to "❤️ Favoriler",
            "start_date" to "📅 Çıkış Tarihi",
            "title" to "🔤 Başlık"
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            orderOptions.forEach { (orderCode, label) ->
                val isSelected = filters.orderBy == orderCode
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.2f) else KitsugiColors.SurfaceElevated)
                        .border(1.dp, if (isSelected) accentColor else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { viewModel.updateMalFilters(filters.copy(orderBy = orderCode)) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    KitsugiEmojiLabel(
                        label = localizedSourceSearchLabel(label),
                        color = if (isSelected) accentColor else KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }

    // 4. Sıralama Yönü (Sort Direction)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.inline_sort_direction_cb3eb46), style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary))
        val sortDirs = listOf("desc" to "⬇️ Azalan (Yüksekten Düşüğe)", "asc" to "⬆️ Artan (Düşükten Yükseğe)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sortDirs.forEach { (dir, label) ->
                val isSelected = filters.sortDirection == dir
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.2f) else KitsugiColors.SurfaceElevated)
                        .border(1.dp, if (isSelected) accentColor else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { viewModel.updateMalFilters(filters.copy(sortDirection = dir)) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    KitsugiEmojiLabel(
                        label = localizedSourceSearchLabel(label),
                        color = if (isSelected) accentColor else KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }

    // 5. Yaş Sınırı / Derecelendirme (Rating)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.inline_age_rating_b66743f), style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary))
        val ratings = listOf(
            null to "Tümü",
            "g" to "Genel (G)",
            "pg" to "Çocuk (PG)",
            "pg13" to "Genç (PG-13)",
            "r17" to "17+ (R)",
            "r" to "R+ (Hafif Çıplaklık)",
            "rx" to "Rx (Hentai)"
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ratings.forEach { (code, label) ->
                val isSelected = filters.rating == code
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.2f) else KitsugiColors.SurfaceElevated)
                        .border(1.dp, if (isSelected) accentColor else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { viewModel.updateMalFilters(filters.copy(rating = code)) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    KitsugiEmojiLabel(
                        label = localizedSourceSearchLabel(label),
                        color = if (isSelected) accentColor else KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }

    // 6. Min Puan (0..9)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val minScore = filters.minScore ?: 0.0
        Text(
            text = stringResource(R.string.inline_minimum_mal_score_1_s_ab367f3, (if (minScore > 0.0) "%.1f+".format(minScore) else stringResource(R.string.filter_all)).toString()),
            style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary)
        )
        Slider(
            value = minScore.toFloat(),
            onValueChange = {
                val sc = (it * 10).toInt() / 10.0
                viewModel.updateMalFilters(filters.copy(minScore = if (sc <= 0.0) null else sc))
            },
            valueRange = 0f..9f,
            steps = 8,
            colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
        )
    }

    // 7. Alfabe Harf Filtresi
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.inline_filter_by_initial_44ade31), style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary))
        val letters = listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "R", "S", "T", "U", "V", "W", "Y", "Z")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            letters.forEach { l ->
                val isSelected = filters.letter == l
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) accentColor else KitsugiColors.SurfaceElevated)
                        .clickable { viewModel.updateMalFilters(filters.copy(letter = if (isSelected) null else l)) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(l, style = MaterialTheme.typography.labelSmall.copy(color = if (isSelected) Color.White else KitsugiColors.TextSecondary))
                }
            }
        }
    }

    // 8. Güvenli İçerik / SFW
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(stringResource(R.string.inline_safe_content_sfw_1486369), style = MaterialTheme.typography.bodyMedium.copy(color = KitsugiColors.TextPrimary))
            Text(stringResource(R.string.inline_filter_18_adult_titles_54cae0e), style = MaterialTheme.typography.bodySmall.copy(color = KitsugiColors.TextMuted))
        }
        Switch(
            checked = filters.sfw,
            onCheckedChange = { viewModel.updateMalFilters(filters.copy(sfw = it)) },
            colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.5f))
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TMDB İÇERİK
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TmdbFullFiltersContent(uiState: SearchUiState, viewModel: SearchViewModel) {
    val filters = uiState.tmdbSpecificFilters
    val accentColor = LocalKitsugiAccent.current

    // Minimum Puan
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val minSc = filters.minScore ?: 0.0
        Text(
            text = stringResource(R.string.inline_minimum_tmdb_score_1_s_0753231, (if (minSc > 0.0) "%.1f+".format(minSc) else stringResource(R.string.filter_all)).toString()),
            style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary)
        )
        Slider(
            value = minSc.toFloat(),
            onValueChange = {
                val sc = (it * 10).toInt() / 10.0
                viewModel.updateTmdbFilters(filters.copy(minScore = if (sc <= 0.0) null else sc))
            },
            valueRange = 0f..9f,
            steps = 8,
            colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
        )
    }

    // Min Oy Sayısı
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.inline_trustworthiness_minimum_vote_count_587f07e), style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary))
        val counts = listOf(null to "Tümü", 50 to "50+ Oy", 200 to "200+ Oy", 1000 to "1.000+ Oy")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            counts.forEach { (count, label) ->
                val isSelected = filters.minVoteCount == count
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.2f) else KitsugiColors.SurfaceElevated)
                        .border(1.dp, if (isSelected) accentColor else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { viewModel.updateTmdbFilters(filters.copy(minVoteCount = count)) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    KitsugiEmojiLabel(
                        label = localizedSourceSearchLabel(label),
                        color = if (isSelected) accentColor else KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }

    // 18+ Yetişkin
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.inline_include_adult_content_9c14950), style = MaterialTheme.typography.bodyMedium.copy(color = KitsugiColors.TextPrimary))
        Switch(
            checked = filters.includeAdult,
            onCheckedChange = { viewModel.updateTmdbFilters(filters.copy(includeAdult = it)) },
            colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.5f))
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SHIKIMORI, KITSU, SIMKL, ALL İÇERİKLERİ
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShikimoriFullFiltersContent(uiState: SearchUiState, viewModel: SearchViewModel) {
    val filters = uiState.shikimoriSpecificFilters
    val accentColor = LocalKitsugiAccent.current

    // 1. Format / Medya Türü
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.inline_format_media_type_f98c348), style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary))
        val kinds = listOf(
            "tv" to "TV", "movie" to "Film", "ova" to "OVA", "ona" to "ONA",
            "special" to "Özel", "music" to "Müzik", "manga" to "Manga",
            "manhwa" to "Manhwa", "manhua" to "Manhua", "novel" to "Light Novel"
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            kinds.forEach { (code, label) ->
                val isSelected = filters.kinds.contains(code)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.2f) else KitsugiColors.SurfaceElevated)
                        .border(1.dp, if (isSelected) accentColor else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable {
                            val newKinds = if (isSelected) filters.kinds - code else filters.kinds + code
                            viewModel.updateShikimoriFilters(filters.copy(kinds = newKinds))
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    KitsugiEmojiLabel(
                        label = localizedSourceSearchLabel(label),
                        color = if (isSelected) accentColor else KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }

    // 2. Yayın Durumu
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.inline_airing_status_a87b048), style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary))
        val statuses = listOf(
            null to "Tümü",
            "ongoing" to "Yayında (Ongoing)",
            "released" to "Tamamlandı (Released)",
            "anons" to "Yakında (Anons)"
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            statuses.forEach { (code, label) ->
                val isSelected = if (code == null) filters.statuses.isEmpty() else filters.statuses.contains(code)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.2f) else KitsugiColors.SurfaceElevated)
                        .border(1.dp, if (isSelected) accentColor else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable {
                            val newStatuses = if (code == null) emptyList() else if (filters.statuses.contains(code)) emptyList() else listOf(code)
                            viewModel.updateShikimoriFilters(filters.copy(statuses = newStatuses))
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    KitsugiEmojiLabel(
                        label = localizedSourceSearchLabel(label),
                        color = if (isSelected) accentColor else KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }

    // 3. Sıralama Ölçütü
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.inline_ranking_metric_a98c96c), style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary))
        val orders = listOf(
            "popularity" to "🔥 Popülerlik",
            "ranked" to "⭐ Puan/Sıralama",
            "name" to "🔤 İsim",
            "aired_on" to "📅 Yayın Tarihi",
            "episodes" to "🔢 Bölüm Sayısı",
            "random" to "🎲 Rastgele"
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            orders.forEach { (code, label) ->
                val isSelected = filters.order == code
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.2f) else KitsugiColors.SurfaceElevated)
                        .border(1.dp, if (isSelected) accentColor else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { viewModel.updateShikimoriFilters(filters.copy(order = code)) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    KitsugiEmojiLabel(
                        label = localizedSourceSearchLabel(label),
                        color = if (isSelected) accentColor else KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }

    // 4. Yaş Sınırı (Rating)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.inline_age_rating_b66743f), style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary))
        val ratings = listOf(
            null to "Tümü",
            "g" to "Genel (G)",
            "pg" to "Çocuk (PG)",
            "pg_13" to "Genç (PG-13)",
            "r" to "17+ (R)",
            "r_plus" to "R+ (Hafif Çıplaklık)",
            "rx" to "Rx (Hentai)"
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ratings.forEach { (code, label) ->
                val isSelected = filters.rating == code
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.2f) else KitsugiColors.SurfaceElevated)
                        .border(1.dp, if (isSelected) accentColor else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { viewModel.updateShikimoriFilters(filters.copy(rating = code)) }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    KitsugiEmojiLabel(
                        label = localizedSourceSearchLabel(label),
                        color = if (isSelected) accentColor else KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }

    // 5. Süre (Duration)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.inline_duration_e695f24), style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary))
        val durations = listOf(
            null to "Tümü",
            "S" to "Kısa (< 10 dk)",
            "D" to "Standart (10-30 dk)",
            "F" to "Uzun (> 30 dk / Film)"
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            durations.forEach { (code, label) ->
                val isSelected = filters.duration == code
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.2f) else KitsugiColors.SurfaceElevated)
                        .border(1.dp, if (isSelected) accentColor else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { viewModel.updateShikimoriFilters(filters.copy(duration = code)) }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    KitsugiEmojiLabel(
                        label = localizedSourceSearchLabel(label),
                        color = if (isSelected) accentColor else KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }

    // 6. Minimum Puan
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val currentScore = filters.minScore ?: 0
        Text(
            text = stringResource(R.string.inline_minimum_shikimori_score_1_s_01f4074, (if (currentScore > 0) "$currentScore+" else stringResource(R.string.filter_all)).toString()),
            style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary)
        )
        Slider(
            value = currentScore.toFloat(),
            onValueChange = {
                val sc = it.toInt()
                viewModel.updateShikimoriFilters(filters.copy(minScore = if (sc == 0) null else sc))
            },
            valueRange = 0f..9f,
            steps = 8,
            colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
        )
    }

    // 7. Sansür Switch
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(stringResource(R.string.inline_safe_content_censorship_4eff8fe), style = MaterialTheme.typography.bodyMedium.copy(color = KitsugiColors.TextPrimary))
            Text(stringResource(R.string.inline_filter_18_adult_titles_54cae0e), style = MaterialTheme.typography.bodySmall.copy(color = KitsugiColors.TextMuted))
        }
        Switch(
            checked = filters.censored,
            onCheckedChange = { viewModel.updateShikimoriFilters(filters.copy(censored = it)) },
            colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.5f))
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KitsuFullFiltersContent(uiState: SearchUiState, viewModel: SearchViewModel) {
    val filters = uiState.kitsuSpecificFilters
    val accentColor = LocalKitsugiAccent.current

    // 1. Format / Alt Tür (Subtypes)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.inline_format_subgenre_337d6ed), style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary))
        val subtypes = listOf(
            "tv" to "TV", "movie" to "Film", "ova" to "OVA", "ona" to "ONA",
            "special" to "Özel", "manga" to "Manga", "manhwa" to "Manhwa",
            "manhua" to "Manhua", "novel" to "Novel"
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            subtypes.forEach { (code, label) ->
                val isSelected = filters.subtypes.contains(code)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.2f) else KitsugiColors.SurfaceElevated)
                        .border(1.dp, if (isSelected) accentColor else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable {
                            val newSubs = if (isSelected) filters.subtypes - code else filters.subtypes + code
                            viewModel.updateKitsuFilters(filters.copy(subtypes = newSubs))
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    KitsugiEmojiLabel(
                        label = localizedSourceSearchLabel(label),
                        color = if (isSelected) accentColor else KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }

    // 2. Yayın Durumu (Status)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.inline_airing_status_a87b048), style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary))
        val statuses = listOf(
            null to "Tümü",
            "current" to "Devam Eden",
            "finished" to "Tamamlandı",
            "upcoming" to "Yakında",
            "unreleased" to "Duyurulmadı"
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            statuses.forEach { (code, label) ->
                val isSelected = if (code == null) filters.statuses.isEmpty() else filters.statuses.contains(code)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.2f) else KitsugiColors.SurfaceElevated)
                        .border(1.dp, if (isSelected) accentColor else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable {
                            val newSt = if (code == null) emptyList() else if (filters.statuses.contains(code)) emptyList() else listOf(code)
                            viewModel.updateKitsuFilters(filters.copy(statuses = newSt))
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    KitsugiEmojiLabel(
                        label = localizedSourceSearchLabel(label),
                        color = if (isSelected) accentColor else KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }

    // 3. Sıralama (Sort)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.inline_ranking_metric_a98c96c), style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary))
        val sorts = listOf(
            "trending" to "🔥 Trend",
            "-userCount" to "👥 Popülerlik",
            "-averageRating" to "⭐ Puan",
            "-startDate" to "🆕 En Yeni",
            "startDate" to "⏳ En Eski",
            "-createdAt" to "✨ Son Eklenen"
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            sorts.forEach { (code, label) ->
                val isSelected = filters.sort == code
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.2f) else KitsugiColors.SurfaceElevated)
                        .border(1.dp, if (isSelected) accentColor else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { viewModel.updateKitsuFilters(filters.copy(sort = code)) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    KitsugiEmojiLabel(
                        label = localizedSourceSearchLabel(label),
                        color = if (isSelected) accentColor else KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }

    // 4. Yaş Sınırı (Age Rating)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.inline_age_rating_2e2c113), style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary))
        val ratings = listOf(
            null to "Tümü",
            "G" to "Genel (G)",
            "PG" to "Rehberlik (PG)",
            "R" to "17+ (R)",
            "R18" to "18+ (R18)"
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ratings.forEach { (code, label) ->
                val isSelected = filters.ageRating == code
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.2f) else KitsugiColors.SurfaceElevated)
                        .border(1.dp, if (isSelected) accentColor else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { viewModel.updateKitsuFilters(filters.copy(ageRating = code)) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    KitsugiEmojiLabel(
                        label = localizedSourceSearchLabel(label),
                        color = if (isSelected) accentColor else KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }

    // 5. Yayın Platformu (Streamers)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.inline_official_streaming_platform_2e1a272), style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary))
        val streamers = listOf("Crunchyroll", "Netflix", "Hulu", "HIDIVE", "Funimation")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            streamers.forEach { name ->
                val isSelected = filters.streamers.contains(name)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.2f) else KitsugiColors.SurfaceElevated)
                        .border(1.dp, if (isSelected) accentColor else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable {
                            val newSt = if (isSelected) filters.streamers - name else filters.streamers + name
                            viewModel.updateKitsuFilters(filters.copy(streamers = newSt))
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(name, style = MaterialTheme.typography.labelSmall.copy(color = if (isSelected) accentColor else KitsugiColors.TextSecondary))
                }
            }
        }
    }

    // 6. Minimum Ortalama Puan (minRating 0..100)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        KitsugiEmojiLabel(label = "Minimum Ortalama Puan:", icon = Icons.StarSolid, iconSize = 13.dp, style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary))
        val scoreSteps = listOf(null to "Tümü", 50 to "%50+", 60 to "%60+", 70 to "%70+", 80 to "%80+", 90 to "%90+")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            scoreSteps.forEach { (sc, label) ->
                val isSelected = filters.minRating == sc
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.2f) else KitsugiColors.SurfaceElevated)
                        .border(1.dp, if (isSelected) accentColor else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { viewModel.updateKitsuFilters(filters.copy(minRating = sc)) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(label, style = MaterialTheme.typography.labelSmall.copy(color = if (isSelected) accentColor else KitsugiColors.TextSecondary))
                }
            }
        }
    }
}

@Composable
private fun SimklFullFiltersContent(uiState: SearchUiState, viewModel: SearchViewModel) {
    val filters = uiState.simklSpecificFilters
    val accentColor = LocalKitsugiAccent.current

    // 1. Trend Dönemi
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.inline_trend_period_7d293a0), style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary))
        val periods = listOf(
            "today" to stringResource(R.string.search_period_today),
            "week" to stringResource(R.string.search_period_week),
            "month" to stringResource(R.string.search_period_month)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            periods.forEach { (p, label) ->
                val isSelected = filters.trendingPeriod == p
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.2f) else KitsugiColors.SurfaceElevated)
                        .border(1.dp, if (isSelected) accentColor else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { viewModel.updateSimklFilters(filters.copy(trendingPeriod = p)) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    KitsugiEmojiLabel(
                        label = localizedSourceSearchLabel(label),
                        color = if (isSelected) accentColor else KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }

    // 2. Format / Alt Tür (Subtype)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.inline_format_genre_0cdd587), style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary))
        val subtypes = listOf(
            null to stringResource(R.string.search_scope_all),
            "tv" to stringResource(R.string.search_scope_tv),
            "movies" to stringResource(R.string.search_scope_movies),
            "ovas" to "OVA",
            "onas" to "ONA (Web)",
            "specials" to stringResource(R.string.search_chip_special)
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            subtypes.forEach { (st, label) ->
                val isSelected = filters.subtype == st
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.2f) else KitsugiColors.SurfaceElevated)
                        .border(1.dp, if (isSelected) accentColor else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { viewModel.updateSimklFilters(filters.copy(subtype = st)) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    KitsugiEmojiLabel(
                        label = localizedSourceSearchLabel(label),
                        color = if (isSelected) accentColor else KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }

    // 3. Sıralama Ölçütü (Sort)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.inline_sort_7d09458), style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary))
        val sorts = listOf(
            "rank" to stringResource(R.string.search_ui_rank),
            "popular-today" to stringResource(R.string.search_ui_most_popular),
            "popular-this-week" to stringResource(R.string.search_ui_trending_now),
            "votes" to stringResource(R.string.search_ui_votes),
            "release-date" to stringResource(R.string.search_ui_air_date),
            "a-z" to stringResource(R.string.search_ui_title_az)
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            sorts.forEach { (sortCode, label) ->
                val isSelected = filters.sort == sortCode
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.2f) else KitsugiColors.SurfaceElevated)
                        .border(1.dp, if (isSelected) accentColor else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { viewModel.updateSimklFilters(filters.copy(sort = sortCode)) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    KitsugiEmojiLabel(
                        label = localizedSourceSearchLabel(label),
                        color = if (isSelected) accentColor else KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }

    // 4. Kategori / Tür (Genre)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.inline_category_genre_73db81d), style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary))
        val genres = listOf(
            null to stringResource(R.string.search_scope_all),
            "action" to "Action",
            "comedy" to "Comedy",
            "drama" to "Drama",
            "fantasy" to "Fantasy",
            "sci-fi" to "Sci-Fi",
            "romance" to "Romance",
            "supernatural" to "Supernatural",
            "adventure" to "Adventure",
            "mystery" to "Mystery",
            "horror" to "Horror",
            "sports" to "Sports",
            "slice-of-life" to "Slice of Life"
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            genres.forEach { (genreCode, label) ->
                val isSelected = filters.genre == genreCode
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.2f) else KitsugiColors.SurfaceElevated)
                        .border(1.dp, if (isSelected) accentColor else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { viewModel.updateSimklFilters(filters.copy(genre = genreCode)) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(if (genreCode == null) label else SearchTranslation.displayLabel(label), style = MaterialTheme.typography.labelSmall.copy(color = if (isSelected) accentColor else KitsugiColors.TextSecondary))
                }
            }
        }
    }

    // 5. Yayın Yılı (Year)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.inline_release_year_c60cec7), style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary))
        val years = listOf(
            null to stringResource(R.string.search_scope_all),
            "2026" to "2026",
            "2025" to "2025",
            "2024" to "2024",
            "2023" to "2023",
            "2022" to "2022",
            "2020s" to "2020–2029",
            "2010s" to "2010–2019",
            "2000s" to "2000–2009",
            "1990s" to "1990–1999"
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            years.forEach { (yearCode, label) ->
                val isSelected = filters.year == yearCode
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.2f) else KitsugiColors.SurfaceElevated)
                        .border(1.dp, if (isSelected) accentColor else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { viewModel.updateSimklFilters(filters.copy(year = yearCode)) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    KitsugiEmojiLabel(
                        label = localizedSourceSearchLabel(label),
                        color = if (isSelected) accentColor else KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }

    // 6. Menşei Ülke (Country)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.inline_country_of_origin_91a5f10), style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary))
        val countries = listOf(
            null to stringResource(R.string.search_scope_all),
            "jp" to "🇯🇵 ${stringResource(R.string.search_country_japan)}",
            "kr" to "🇰🇷 ${stringResource(R.string.search_country_south_korea)}",
            "cn" to "🇨🇳 ${stringResource(R.string.search_country_china)}",
            "us" to stringResource(R.string.search_country_us)
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            countries.forEach { (cCode, label) ->
                val isSelected = filters.country == cCode
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.2f) else KitsugiColors.SurfaceElevated)
                        .border(1.dp, if (isSelected) accentColor else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { viewModel.updateSimklFilters(filters.copy(country = cCode)) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    KitsugiEmojiLabel(
                        label = localizedSourceSearchLabel(label),
                        color = if (isSelected) accentColor else KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun AllFullFiltersContent() {
    Text(
        text = stringResource(R.string.inline_when_searching_in_all_mode_9dd3946),
        style = MaterialTheme.typography.bodyMedium.copy(color = KitsugiColors.TextSecondary),
        lineHeight = 20.sp
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 🎌 Bangumi (bgm.tv) Tam Filtreler
// ─────────────────────────────────────────────────────────────────────────────

/** Tek satırlık Bangumi çip grubu; [code] null ise "Tümü" seçeneğidir. */
@Composable
private fun BangumiChipRow(
    title: String,
    options: List<Pair<String?, String>>,
    isSelected: (String?) -> Boolean,
    onSelect: (String?) -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        KitsugiEmojiLabel(
            label = title,
            iconSize = 13.dp,
            style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary)
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { (code, label) ->
                val selected = isSelected(code)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected) accentColor.copy(alpha = 0.2f)
                            else KitsugiColors.SurfaceElevated
                        )
                        .border(
                            1.dp,
                            if (selected) accentColor else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelect(code) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (selected) accentColor else KitsugiColors.TextSecondary
                        )
                    )
                }
            }
        }
    }
}

/** Bangumi anahtar/detik (switch) satırı. */
@Composable
private fun BangumiSwitchRow(
    label: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = KitsugiColors.TextPrimary
                )
            )
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall.copy(color = KitsugiColors.TextMuted),
                fontSize = 11.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
        )
    }
}

/**
 * Bangumi arama filtreleri.
 *
 * Bangumi'nin `POST /v0/search/subjects` gövdesi diğer motorlardan daha dardır:
 * `sort`, `tag`, `air_date`, `rating`, `rank`, `type`, `nsfw`. Tür (动画/书籍) kapsam
 * sekmesinden türetildiği için burada sunulmaz; yıl filtresi `air_date` aralığına,
 * puan filtresi `rating` ifadesine çevrilir (bkz. KitsugiBangumiClient.searchMediaAdvanced).
 */
@Composable
private fun BangumiFullFiltersContent(uiState: SearchUiState, viewModel: SearchViewModel) {
    val filters = uiState.bangumiSpecificFilters

    // 1. Sıralama ölçütü
    BangumiChipRow(
        title = "📊 ${stringResource(R.string.search_filter_sort)}:",
        options = listOf(
            "match" to stringResource(R.string.search_ui_best_match),
            "heat" to stringResource(R.string.search_ui_interest),
            "rank" to stringResource(R.string.search_ui_rank_label),
            "score" to "⭐ Puan"
        ),
        isSelected = { filters.sort == it },
        onSelect = { code -> viewModel.updateBangumiFilters(filters.copy(sort = code ?: "match")) }
    )

    // 2. Wiki etiketleri — Bangumi'de tür/tema filtresi etiketler üzerinden yapılır.
    val accentColor = LocalKitsugiAccent.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "🏷️ ${stringResource(R.string.search_filter_tags)}:",
            style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary)
        )
        if (filters.tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                filters.tags.forEach { tag ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(accentColor.copy(alpha = 0.25f))
                            .border(1.dp, accentColor, RoundedCornerShape(10.dp))
                            .clickable {
                                viewModel.updateBangumiFilters(filters.copy(tags = filters.tags - tag))
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        KitsugiEmojiLabel(
                            label = tag.toLocalizedTagLabel(),
                            icon = Icons.X,
                            iconSize = 10.dp,
                            style = MaterialTheme.typography.labelSmall.copy(color = accentColor)
                        )
                    }
                }
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Kısayol etiketleri: soldaki görünen ad arayüz diline göre dil dosyasından
            // gelir (TR → Türkçe, EN → İngilizce), sağdaki değer Bangumi wiki etiketidir.
            val presets = listOf(
                "科幻", "奇幻", "恋爱", "校园", "搞笑", "治愈", "日常", "机战", "悬疑",
                "推理", "冒险", "战斗", "运动", "音乐", "恐怖", "后宫", "百合", "耽美",
                "异世界", "职场"
            )
            presets.forEach { tag ->
                val label = tag.toLocalizedTagLabel()
                val selected = filters.tags.contains(tag)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected) accentColor.copy(alpha = 0.2f)
                            else KitsugiColors.SurfaceElevated
                        )
                        .border(
                            1.dp,
                            if (selected) accentColor else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            val newTags = if (selected) filters.tags - tag else filters.tags + tag
                            viewModel.updateBangumiFilters(filters.copy(tags = newTags))
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        "$label · $tag",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (selected) accentColor else KitsugiColors.TextSecondary
                        )
                    )
                }
            }
        }
    }

    // 3. Yayın yılı
    val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    BangumiChipRow(
        title = "📅 ${stringResource(R.string.search_filter_release_year)}:",
        options = buildList {
            add(null to stringResource(R.string.search_scope_all))
            (0 until 6).forEach { add((currentYear - it).toString() to (currentYear - it).toString()) }
            add((currentYear - 10).toString() to "${currentYear - 10}+")
            add((currentYear - 20).toString() to "${currentYear - 20}+")
        },
        // "+" ile biten ön ayarlar yalnız alt sınır koyar (yearFrom), diğerleri
        // tek bir yılı seçer (yearFrom == yearTo).
        isSelected = { code ->
            when {
                code == null -> filters.yearFrom == null && filters.yearTo == null
                code.endsWith("+") -> filters.yearFrom == code.dropLast(1).toIntOrNull() && filters.yearTo == null
                else -> filters.yearFrom == code.toIntOrNull() && filters.yearTo == code.toIntOrNull()
            }
        },
        onSelect = { code ->
            if (code == null) {
                viewModel.updateBangumiFilters(filters.copy(yearFrom = null, yearTo = null))
            } else if (code.endsWith("+")) {
                val from = code.dropLast(1).toIntOrNull()
                viewModel.updateBangumiFilters(filters.copy(yearFrom = from, yearTo = null))
            } else {
                val year = code.toIntOrNull()
                viewModel.updateBangumiFilters(filters.copy(yearFrom = year, yearTo = year))
            }
        }
    )

    // 4. Puan alt sınırı
    BangumiChipRow(
        title = "⭐ Minimum Puan (rating):",
        options = listOf(
            null to "Tümü", "6" to "6+", "7" to "7+", "8" to "8+", "9" to "9+"
        ),
        isSelected = { code -> filters.minScore == code?.toIntOrNull() },
        onSelect = { code -> viewModel.updateBangumiFilters(filters.copy(minScore = code?.toIntOrNull())) }
    )

    // 5. Personel mesleği — yalnız 人物 (STAFF) kapsamında etkili
    if (uiState.selectedScope == SearchScope.STAFF) {
        BangumiChipRow(
            title = "🎙️ ${stringResource(R.string.search_filter_career)}:",
            options = listOf(
                null to stringResource(R.string.search_scope_all),
                "seiyu" to stringResource(R.string.search_career_voice_actor),
                "mangaka" to stringResource(R.string.search_career_mangaka),
                "artist" to stringResource(R.string.search_career_artist),
                "illustrator" to stringResource(R.string.search_career_illustrator),
                "writer" to stringResource(R.string.staff_role_writer),
                "producer" to stringResource(R.string.staff_role_producer),
                "actor" to stringResource(R.string.detail_role_actor)
            ),
            isSelected = { code ->
                if (code == null) filters.career.isEmpty() else filters.career.contains(code)
            },
            onSelect = { code ->
                viewModel.updateBangumiFilters(
                    filters.copy(career = if (code == null) emptyList() else listOf(code))
                )
            }
        )
    }

    // 6. İçerik anahtarları
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BangumiSwitchRow(
            label = stringResource(R.string.search_bangumi_include_adult),
            hint = stringResource(R.string.search_bangumi_include_adult_hint),
            checked = filters.nsfw,
            onCheckedChange = { viewModel.updateBangumiFilters(filters.copy(nsfw = it)) }
        )
        BangumiSwitchRow(
            label = stringResource(R.string.search_bangumi_comics_only),
            hint = stringResource(R.string.search_bangumi_comics_only_hint),
            checked = filters.comicsOnly,
            onCheckedChange = { viewModel.updateBangumiFilters(filters.copy(comicsOnly = it)) }
        )
    }
}
