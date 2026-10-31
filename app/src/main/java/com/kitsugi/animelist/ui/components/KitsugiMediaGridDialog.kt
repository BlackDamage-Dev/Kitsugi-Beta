@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.kitsugi.animelist.ui.components

import androidx.compose.foundation.layout.Arrangement
import com.kitsugi.animelist.ui.utils.uniqueLazyKeys
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.data.remote.JikanSearchResult
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.CompositionLocalProvider
import com.kitsugi.animelist.ui.utils.dpadVerticalFastScroll
import com.kitsugi.animelist.ui.utils.kitsugiLazyKey
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.components.KitsugiEmptyState
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KitsugiMediaGridDialog(
    title: String,
    results: List<JikanSearchResult>,
    alreadyInList: (JikanSearchResult) -> Boolean,
    onItemClick: (JikanSearchResult) -> Unit,
    onDismiss: () -> Unit,
    getMediaEntry: (JikanSearchResult) -> com.kitsugi.animelist.model.MediaEntry? = { null }
) {
    var searchQuery by rememberSaveable {
        mutableStateOf("")
    }

    val filteredResults = results.filter { result ->
        if (searchQuery.isBlank()) {
            true
        } else {
            val query = searchQuery.trim().lowercase()

            result.title.lowercase().contains(query) ||
                    result.subtitle.lowercase().contains(query) ||
                    result.source.lowercase().contains(query) ||
                    result.year?.toString()?.contains(query) == true ||
                    result.malId.toString().contains(query)
        }
    }

    val isTvDevice = com.kitsugi.animelist.ui.theme.LocalIsTvDevice.current

    if (isTvDevice) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = onDismiss,
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(KitsugiColors.Background)
                    .padding(horizontal = 48.dp, vertical = 32.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            color = KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        TextButton(
                            onClick = onDismiss
                        ) {
                            Text(
                                text = stringResource(R.string.kapat),
                                color = KitsugiColors.TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    KitsugiSearchField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                        },
                        placeholder = stringResource(R.string.bu_rafta_ara)
                    )

                    Text(
                        text = stringResource(R.string.filteredresults_size_results_size_icerik, filteredResults.size, results.size),
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (filteredResults.isEmpty()) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                    KitsugiEmptyState(
                                title = stringResource(R.string.sonuc_bulunamadi),
                                subtitle = if (searchQuery.isBlank()) {
                                    stringResource(R.string.bu_rafta_gosterilecek_icerik_yok)
                                } else {
                                    "\"$searchQuery\" araması için sonuç bulunamadı."
                                },
                                icon = Icons.SearchOff
                            )
                        }
                    } else {
                        val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                        val tvSpec = com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults.rememberTvCenteredSpec()

                        CompositionLocalProvider(
                            LocalBringIntoViewSpec provides tvSpec
                        ) {
                            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(com.kitsugi.animelist.ui.utils.rememberKitsugiGridColumns()),
                                state = gridState,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .dpadVerticalFastScroll(gridState),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                val dialogKeys = filteredResults.uniqueLazyKeys { r ->
                                    "dlg_tv_${r.source}_${r.malId}_${r.tmdbId ?: 0}_${r.cs3Url.orEmpty().hashCode()}"
                                }
                                items(
                                    count = filteredResults.size,
                                    // PERF: index'siz sabit identity key — sıralama/filtreleme
                                    // sonrası kartlar yeniden kullanılabilir. cs3Url hash'i
                                    // eklenti kaynaklı aynı malId'li ama farklı URL'li kartlar
                                    // için benzersizlik sağlar.
                                    // Yinelenen sonuçlarda bile benzersiz (bkz. uniqueLazyKeys).
                                    key = { index -> dialogKeys[index] },
                                    contentType = { _ -> "media_card" }
                                ) { index ->
                                    val result = filteredResults[index]
                                    KitsugiExploreMediaCard(
                                        result = result,
                                        alreadyInList = alreadyInList(result),
                                        mediaEntry = getMediaEntry(result),
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            onItemClick(result)
                                        }
                                    )
                                }
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
            title = {
                Text(
                    text = title,
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                // PERF: verticalScroll + FlowRow ile TÜM kartlar aynı anda
                // compose ediliyordu (40+ kart → ciddi jank). LazyVerticalGrid
                // yalnızca görünen kartları compose eder.
                val gridState = rememberLazyGridState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 640.dp)
                ) {
                    KitsugiSearchField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                        },
                        placeholder = stringResource(R.string.bu_rafta_ara)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.filteredresults_size_results_size_icerik, filteredResults.size, results.size),
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (filteredResults.isEmpty()) {
                        KitsugiEmptyState(
                            title = stringResource(R.string.sonuc_bulunamadi),
                            subtitle = if (searchQuery.isBlank()) {
                                stringResource(R.string.bu_rafta_gosterilecek_icerik_yok)
                            } else {
                                "\"$searchQuery\" araması için sonuç bulunamadı."
                            },
                            icon = Icons.SearchOff
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(com.kitsugi.animelist.ui.utils.rememberKitsugiGridColumns()),
                            state = gridState,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(
                                items = filteredResults,
                                key = { idx, result -> result.kitsugiLazyKey("dlg", idx) },
                                contentType = { _, _ -> "media_card" }
                            ) { _, result ->
                                KitsugiExploreMediaCard(
                                    result = result,
                                    alreadyInList = alreadyInList(result),
                                    mediaEntry = getMediaEntry(result),
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        onItemClick(result)
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = onDismiss
                ) {
                    Text(
                        text = stringResource(R.string.kapat),
                        color = KitsugiColors.TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        )
    }
}