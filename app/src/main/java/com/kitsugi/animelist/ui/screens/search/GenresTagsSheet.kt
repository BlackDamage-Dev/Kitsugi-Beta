package com.kitsugi.animelist.ui.screens.search
import com.kitsugi.animelist.ui.components.KitsugiButton

import androidx.compose.foundation.BorderStroke
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

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

    // Dahili liste İngilizce — UI'da SearchTranslation ile Türkçe gösterilir
    val allGenres = listOf(
        // AniList resmi türleri
        "Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy",
        "Hentai", "Horror", "Mahou Shoujo", "Mecha", "Music", "Mystery",
        "Psychological", "Romance", "Sci-Fi", "Slice of Life", "Sports",
        "Supernatural", "Thriller",
        // Demografi
        "Shounen", "Shoujo", "Seinen", "Josei", "Kids"
    )

    val allTags = listOf(
        // Aksiyon
        "Battle Royale", "Espionage", "Guns", "Martial Arts", "Swordplay", "Archery",
        // Fantazi
        "Isekai", "Reverse Isekai", "Magic", "Super Power", "Superhero", "Mythology",
        "Alchemy", "Cultivation", "Wuxia", "Fairy Tale", "Youkai", "Steampunk",
        "Body Swapping", "Shapeshifting", "Curses", "Exorcism", "Necromancy", "Henshin",
        // Sci-Fi
        "Cyberpunk", "Space Opera", "Time Loop", "Time Manipulation", "Time Travel", "Post-Apocalyptic",
        // Korku / Gerilim
        "Gore", "Body Horror", "Cosmic Horror", "Survival", "Torture",
        // Romantizm
        "Love Triangle", "Love Polygon", "Harem", "Reverse Harem", "Unrequited Love",
        "Fake Relationship", "Boys' Love", "Yuri", "Cohabitation",
        // Dram / Drama
        "Coming of Age", "Revenge", "Tragedy", "Conspiracy", "Bullying",
        "Class Struggle", "Rehabilitation", "Kingdom Management",
        // Günlük Hayat
        "Iyashikei", "Otaku Culture", "Gourmet", "Family Life", "Parenthood",
        "Agriculture", "School Club", "CGDCT", "Childcare",
        // Sanat / Müzik
        "Band", "Dancing", "Idol", "Idols", "Showbiz", "Performing Arts",
        "Fashion", "Photography", "Modeling",
        // Spor / Oyun
        "Football", "Basketball", "Baseball", "Volleyball", "Tennis", "Swimming",
        "Boxing", "Judo", "Cycling", "Surfing", "Rugby", "Skateboarding",
        "Table Tennis", "Ice Skating", "Sumo", "Wrestling", "Fishing",
        "E-Sports", "Video Games", "Board Game", "Card Battle", "Mahjong",
        // Karakterler
        "Vampire", "Zombie", "Werewolf", "Witch", "Samurai", "Ninja", "Pirates",
        "Vikings", "Demon", "Tsundere", "Yandere", "Villainess", "Tomboy",
        "VTuber", "Twins", "Teacher", "Orphan",
        // Organizasyonlar
        "Mafia", "Yakuza", "Police", "Assassins", "Gangs", "Military", "Firefighters",
        // Ayarlar / Setting
        "School", "Workplace", "Dungeon", "Prison", "Medieval", "Historical",
        "Dystopian", "Space", "Virtual World", "Urban Fantasy", "Afterlife",
        "Alternate Universe", "Ancient China", "Camping", "Restaurant", "College",
        // Diğer temalar
        "Reincarnation", "Death Game", "Gambling", "Politics", "Philosophy",
        "Religion", "War", "Crime", "Drugs", "Medicine", "Pandemic",
        "Environmental", "Economics", "Marriage", "Travel", "Educational",
        "Delinquents", "Gender Bending", "LGBTQ+ Themes", "Found Family",
        "Memory Manipulation", "Noir", "Prophecy", "Crossover", "Chibi",
    )

    val filteredGenres = remember(searchGenreQuery) {
        if (searchGenreQuery.isBlank()) allGenres
        else allGenres.filter {
            it.contains(searchGenreQuery, ignoreCase = true) ||
            SearchTranslation.displayLabel(it).contains(searchGenreQuery, ignoreCase = true)
        }
    }

    val filteredTags = remember(searchGenreQuery) {
        if (searchGenreQuery.isBlank()) allTags
        else allTags.filter {
            it.contains(searchGenreQuery, ignoreCase = true) ||
            SearchTranslation.displayLabel(it).contains(searchGenreQuery, ignoreCase = true)
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

            Spacer(modifier = Modifier.height(14.dp))

            // ── Scrollable genre & tag chips ──────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Genres
                if (filteredGenres.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.inline_genres_tap_to_add_double_7321dc6),
                            color = KitsugiColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            filteredGenres.forEach { genre ->
                                GenreTagChip(
                                    label = SearchTranslation.displayLabel(genre),
                                    isIncluded = selectedGenres.contains(genre),
                                    isExcluded = excludedGenres.contains(genre),
                                    accentColor = accentColor,
                                    onToggle = {
                                        when {
                                            !selectedGenres.contains(genre) && !excludedGenres.contains(genre) -> {
                                                selectedGenres = selectedGenres + genre
                                            }
                                            selectedGenres.contains(genre) -> {
                                                selectedGenres = selectedGenres - genre
                                                excludedGenres = excludedGenres + genre
                                            }
                                            else -> {
                                                excludedGenres = excludedGenres - genre
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Tags
                if (filteredTags.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.search_filter_tags),
                            color = KitsugiColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            filteredTags.forEach { tag ->
                                val isSelected = selectedTags.contains(tag)
                                SimpleTagChip(
                                    label = SearchTranslation.displayLabel(tag),
                                    isSelected = isSelected,
                                    accentColor = accentColor,
                                    onToggle = {
                                        selectedTags = if (isSelected) {
                                            selectedTags - tag
                                        } else {
                                            selectedTags + tag
                                        }
                                    }
                                )
                            }
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
                    Text(stringResource(R.string.sifirla), fontSize = 14.sp)
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
                    Text("Uygula", color = Color.White, fontSize = 14.sp)
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
