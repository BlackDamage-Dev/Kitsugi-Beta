package com.kitsugi.animelist.ui.screens.search
import com.kitsugi.animelist.ui.components.KitsugiButton
import com.kitsugi.animelist.ui.utils.uniqueItems

import androidx.compose.foundation.BorderStroke
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.utils.tvClickable
import com.kitsugi.animelist.utils.TaxonomyCatalog
import com.kitsugi.animelist.utils.isTurkish
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenresTagsSheet(
    currentFilters: SearchFilters,
    onApplyFilters: (SearchFilters) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    val accentColor = LocalKitsugiAccent.current

    var searchGenreQuery by remember { mutableStateOf("") }
    var selectedGenres by remember { mutableStateOf(currentFilters.genres) }
    var excludedGenres by remember { mutableStateOf(currentFilters.excludedGenres) }
    var selectedTags by remember { mutableStateOf(currentFilters.tags) }

    val context = LocalContext.current
    val turkish = isTurkish()
    // The catalog is parsed at most once and never on the Compose/UI thread.
    val catalogResult by produceState<Result<TaxonomyCatalog>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            runCatching { TaxonomyCatalog.load(context.applicationContext) }
        }
    }
    val catalog = catalogResult?.getOrNull()
    val filteredGenres = remember(catalog, searchGenreQuery, turkish) {
        val needle = TaxonomyCatalog.fold(searchGenreQuery)
        catalog?.genres.orEmpty().let { entries ->
            (if (needle.isEmpty()) entries else entries.filter { it.matchesFolded(needle) })
                .sortedBy { TaxonomyCatalog.fold(it.displayName(turkish)) }
        }
    }
    val filteredTags = remember(catalog, searchGenreQuery, turkish) {
        val needle = TaxonomyCatalog.fold(searchGenreQuery)
        catalog?.tags.orEmpty().distinctBy { TaxonomyCatalog.fold(it.en) }.let { entries ->
            (if (needle.isEmpty()) entries else entries.filter { it.matchesFolded(needle) })
                .sortedBy { TaxonomyCatalog.fold(it.displayName(turkish)) }
        }
    }

    KitsugiSheetOrDialog(
        onDismiss = onDismiss,
        heightFraction = 0.85f,
        innerScrollState = listState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            // ── Header ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.inline_genres_tags_c957e42),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = KitsugiColors.TextPrimary
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Close,
                        contentDescription = stringResource(R.string.kapat),
                        tint = KitsugiColors.TextMuted
                    )
                }
            }

            Text(
                text = stringResource(
                    R.string.search_taxonomy_selected_count,
                    selectedGenres.size + excludedGenres.size,
                    selectedTags.size
                ),
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(10.dp))

            // ── Search Box ───────────────────────────────────────────────────
            OutlinedTextField(
                value = searchGenreQuery,
                onValueChange = { searchGenreQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.inline_search_genres_or_tags_772cd17), color = KitsugiColors.TextMuted) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Search,
                        contentDescription = null,
                        tint = KitsugiColors.TextMuted
                    )
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = KitsugiColors.Surface,
                    unfocusedContainerColor = KitsugiColors.Surface,
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = KitsugiColors.Border,
                    focusedTextColor = KitsugiColors.TextPrimary,
                    unfocusedTextColor = KitsugiColors.TextPrimary
                ),
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.search_taxonomy_pending_notice),
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.height(8.dp))

            // ── Scrollable genre & tag chips ──────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (catalogResult == null) {
                    item(key = "loading") { CircularProgressIndicator(color = accentColor) }
                } else if (catalog == null) {
                    item(key = "error") {
                        Text(stringResource(R.string.search_catalog_load_error), color = KitsugiColors.TextSecondary)
                    }
                } else {
                    if (filteredGenres.isNotEmpty()) {
                        item(key = "genres_header") {
                            Text(
                                text = stringResource(R.string.inline_genres_tap_to_add_double_7321dc6),
                                color = KitsugiColors.TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        // Lazy rows rather than one 1,400-chip FlowRow: only visible chips compose.
                        uniqueItems(filteredGenres.chunked(3), key = { "genre_${it.first().key}" }) { row ->
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { genre ->
                                    val name = genre.en // Legacy search API expects the English label.
                                    GenreTagChip(
                                        label = genre.displayName(turkish),
                                        isIncluded = selectedGenres.contains(name),
                                        isExcluded = excludedGenres.contains(name),
                                        accentColor = accentColor,
                                        onToggle = {
                                            when {
                                                name !in selectedGenres && name !in excludedGenres ->
                                                    selectedGenres = selectedGenres + name
                                                name in selectedGenres -> {
                                                    selectedGenres = selectedGenres - name
                                                    excludedGenres = excludedGenres + name
                                                }
                                                else -> excludedGenres = excludedGenres - name
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (filteredTags.isNotEmpty()) {
                        item(key = "tags_header") {
                            Text(text = stringResource(R.string.search_filter_tags),
                                color = KitsugiColors.TextSecondary, fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold)
                        }
                        uniqueItems(filteredTags.chunked(3), key = { "tag_${it.first().key}" }) { row ->
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { tag ->
                                    val name = tag.en
                                    SimpleTagChip(
                                        label = tag.displayName(turkish),
                                        isSelected = name in selectedTags,
                                        accentColor = accentColor,
                                        onToggle = {
                                            selectedTags = if (name in selectedTags) selectedTags - name
                                                else selectedTags + name
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (filteredGenres.isEmpty() && filteredTags.isEmpty()) {
                        item(key = "empty") {
                            Text(stringResource(R.string.search_catalog_no_matches),
                                color = KitsugiColors.TextMuted)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Action Buttons ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        selectedGenres = emptyList()
                        excludedGenres = emptyList()
                        selectedTags = emptyList()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = KitsugiColors.TextSecondary),
                    border = BorderStroke(1.dp, KitsugiColors.Border)
                ) {
                    Text(stringResource(R.string.search_taxonomy_clear_all), fontSize = 14.sp)
                }

                KitsugiButton(
                    onClick = {
                        onApplyFilters(
                            currentFilters.copy(
                                genres = selectedGenres,
                                excludedGenres = excludedGenres,
                                tags = selectedTags
                            )
                        )
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(stringResource(R.string.search_filter_apply), color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}

// ─── Private Chip Helpers ─────────────────────────────────────────────────────

@Composable
private fun GenreTagChip(
    label: String,
    isIncluded: Boolean,
    isExcluded: Boolean,
    accentColor: androidx.compose.ui.graphics.Color,
    onToggle: () -> Unit
) {
    val includeColor = Color(0xFF10B981)
    val excludeColor = Color(0xFFEF4444)

    val chipColor = when {
        isIncluded -> includeColor
        isExcluded -> excludeColor
        else -> KitsugiColors.Border
    }
    val chipBg = when {
        isIncluded -> includeColor.copy(alpha = 0.12f)
        isExcluded -> excludeColor.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    val chipShape = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier
            .clip(chipShape)
            .background(chipBg)
            .border(1.dp, chipColor.copy(alpha = 0.6f), chipShape)
            .tvClickable(shape = chipShape, onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when {
            isIncluded -> Icon(
                imageVector = Icons.Check,
                contentDescription = null,
                tint = includeColor,
                modifier = Modifier.size(14.dp)
            )
            isExcluded -> Icon(
                imageVector = Icons.Remove,
                contentDescription = null,
                tint = excludeColor,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = label,
            color = when {
                isIncluded -> includeColor
                isExcluded -> excludeColor
                else -> KitsugiColors.TextPrimary
            },
            fontSize = 13.sp,
            fontWeight = if (isIncluded || isExcluded) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun SimpleTagChip(
    label: String,
    isSelected: Boolean,
    accentColor: androidx.compose.ui.graphics.Color,
    onToggle: () -> Unit
) {
    val chipColor = if (isSelected) accentColor else KitsugiColors.Border
    val chipBg = if (isSelected) accentColor.copy(alpha = 0.12f) else Color.Transparent
    val chipShape = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier
            .clip(chipShape)
            .background(chipBg)
            .border(1.dp, chipColor.copy(alpha = 0.6f), chipShape)
            .tvClickable(shape = chipShape, onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Check,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = label,
            color = if (isSelected) accentColor else KitsugiColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
