package com.kitsugi.animelist.ui.screens.mylist.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import androidx.compose.foundation.rememberScrollState
import com.kitsugi.animelist.ui.utils.kitsugiVerticalScroll
import com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog
import com.kitsugi.animelist.ui.components.LocalDismissAnimated
import com.kitsugi.animelist.ui.screens.mylist.ExtraFilterRow
import com.kitsugi.animelist.ui.screens.mylist.MyListContentTypeRow
import com.kitsugi.animelist.ui.screens.mylist.ScoreFilterRow
import com.kitsugi.animelist.ui.screens.mylist.MyListGroupingRow
import com.kitsugi.animelist.ui.screens.mylist.MyListSortChipsRow
import com.kitsugi.animelist.ui.screens.mylist.StatusFilterRow
import com.kitsugi.animelist.ui.screens.mylist.TypeFilterRow
import com.kitsugi.animelist.ui.screens.mylist.YearFilterRow
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable

/** Profile-style sheet; filters apply immediately, actions dismiss with the sheet animation. */
@Composable
fun KitsugiMyListFilterSheet(
    selectedStatusFilterId: String,
    selectedTypeFilterId: String,
    selectedScoreFilterId: String,
    selectedYearFilterId: String,
    selectedExtraFilterId: String,
    selectedSortId: String,
    selectedGroupId: String,
    onStatusSelected: (String) -> Unit,
    onTypeSelected: (String) -> Unit,
    onScoreSelected: (String) -> Unit,
    onYearSelected: (String) -> Unit,
    onExtraSelected: (String) -> Unit,
    onSortSelected: (String) -> Unit,
    onGroupSelected: (String) -> Unit,
    onResetFilters: () -> Unit,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    KitsugiSheetOrDialog(
        onDismiss = onDismiss,
        innerColumnScrollState = scrollState
    ) {
        val dismissAnimated = LocalDismissAnimated.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .kitsugiVerticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // ── İçerik türü hapları ──
            MyListContentTypeRow(
                selectedTypeFilterId = selectedTypeFilterId,
                onTypeSelected = onTypeSelected
            )

            FilterSheetSection(title = "Durum") {
                StatusFilterRow(
                    selectedStatusFilterId = selectedStatusFilterId,
                    onStatusSelected = onStatusSelected
                )
            }

            FilterSheetSection(title = "Medya Türü") {
                TypeFilterRow(
                    selectedTypeFilterId = selectedTypeFilterId,
                    onTypeSelected = onTypeSelected
                )
            }

            FilterSheetSection(title = "Puan Aralığı") {
                ScoreFilterRow(
                    selectedScoreFilterId = selectedScoreFilterId,
                    onScoreSelected = onScoreSelected
                )
            }

            FilterSheetSection(title = stringResource(R.string.yayin_yili)) {
                YearFilterRow(
                    selectedYearFilterId = selectedYearFilterId,
                    onYearSelected = onYearSelected
                )
            }

            FilterSheetSection(title = "Ekstra / Özel") {
                ExtraFilterRow(
                    selectedExtraFilterId = selectedExtraFilterId,
                    onExtraSelected = onExtraSelected
                )
            }

            // ── Gruplama (bağımsız eksen) ──────────────────────────────────
            // Sıralama bu gruplamayı KALDIRMAZ; varsayılan görünüm (Bugün / Bu Hafta …)
            // seçili sıralamayla birlikte çalışır.
            FilterSheetSection(title = stringResource(R.string.mylist_group_title)) {
                MyListGroupingRow(
                    selectedGroupId = selectedGroupId,
                    onGroupSelected = onGroupSelected
                )
            }

            // ── Sıralama (kompakt: tek kavram = tek çip) ────────────────────
            FilterSheetSection(title = stringResource(R.string.siralama)) {
                MyListSortChipsRow(
                    selectedSortId = selectedSortId,
                    onSortSelected = onSortSelected
                )
            }

            // ── Alt eylemler: Temizle + Bitti ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val accentColor = LocalKitsugiAccent.current
                val actionShape = RoundedCornerShape(16.dp)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(actionShape)
                        .background(KitsugiColors.SurfaceSoft, actionShape)
                        .tvClickable(shape = actionShape, onClick = onResetFilters)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.temizle),
                        color = accentColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1.3f)
                        .clip(actionShape)
                        .background(accentColor.copy(alpha = 0.15f), actionShape)
                        .tvClickable(shape = actionShape, onClick = dismissAnimated)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.mylist_filter_done),
                        color = accentColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/** Subdued section label above horizontally scrollable filter choices. */
@Composable
private fun FilterSheetSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
        content()
    }
}
