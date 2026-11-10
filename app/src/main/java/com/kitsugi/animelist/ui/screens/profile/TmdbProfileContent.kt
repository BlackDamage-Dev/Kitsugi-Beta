package com.kitsugi.animelist.ui.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.R
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.app.TmdbProfileState
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogo
import com.kitsugi.animelist.ui.theme.KitsugiColors

/** Native TMDB account profile: Watchlist, favorites and ratings in one deduplicated view. */
@Composable
fun TmdbProfileContent(
    state: TmdbProfileState,
    accentColor: Color,
    onMediaClick: (mediaId: Int, mediaType: MediaType, title: String, imageUrl: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val snapshot = state.snapshot
    val uriHandler = LocalUriHandler.current
    var filter by rememberSaveable { mutableIntStateOf(0) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                color = KitsugiColors.Surface,
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    KitsugiPlatformLogo(platformId = "tmdb", size = 52.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = state.userName.ifBlank { stringResource(R.string.tmdb_profile_account) },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = KitsugiColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "TMDB · #${state.accountId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = KitsugiColors.TextMuted
                        )
                    }
                    if (state.userName.isNotBlank()) {
                        TextButton(onClick = {
                            uriHandler.openUri("https://www.themoviedb.org/u/${state.userName}")
                        }) {
                            Text(stringResource(R.string.tmdb_profile_open), color = accentColor)
                        }
                    }
                }
            }
        }

        if (snapshot == null) {
            item {
                Text(
                    text = stringResource(R.string.tmdb_profile_empty),
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            return@LazyColumn
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TmdbMetric(
                    label = stringResource(R.string.tmdb_profile_watchlist),
                    value = snapshot.watchlistCount,
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f)
                )
                TmdbMetric(
                    label = stringResource(R.string.tmdb_profile_favorites),
                    value = snapshot.favoriteCount,
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f)
                )
                TmdbMetric(
                    label = stringResource(R.string.tmdb_profile_rated),
                    value = snapshot.ratedCount,
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    R.string.notif_filter_all,
                    R.string.tmdb_profile_watchlist,
                    R.string.tmdb_profile_favorites,
                    R.string.tmdb_profile_rated
                ).forEachIndexed { index, label ->
                    FilterChip(
                        selected = filter == index,
                        onClick = { filter = index },
                        label = { Text(stringResource(label)) }
                    )
                }
            }
        }

        val watchlistKeys = snapshot.watchlist.map { it.accountKey() }.toSet()
        val favoriteKeys = snapshot.favorites.map { it.accountKey() }.toSet()
        val ratedKeys = snapshot.rated.map { it.accountKey() }.toSet()
        val visible = snapshot.entries.filter { entry ->
            when (filter) {
                1 -> entry.accountKey() in watchlistKeys
                2 -> entry.accountKey() in favoriteKeys
                3 -> entry.accountKey() in ratedKeys
                else -> true
            }
        }
        if (visible.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.tmdb_profile_empty),
                    color = KitsugiColors.TextMuted,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            }
        } else {
            items(visible, key = { it.accountKey() }) { entry ->
                TmdbAccountMediaRow(
                    entry = entry,
                    inWatchlist = entry.accountKey() in watchlistKeys,
                    isFavorite = entry.accountKey() in favoriteKeys,
                    accentColor = accentColor,
                    onClick = {
                        entry.tmdbId?.let { id ->
                            onMediaClick(id, entry.type, entry.title, entry.imageUrl)
                        }
                    }
                )
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun TmdbMetric(
    label: String,
    value: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, color = KitsugiColors.Surface, shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("$value", color = accentColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, color = KitsugiColors.TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun TmdbAccountMediaRow(
    entry: MediaEntry,
    inWatchlist: Boolean,
    isFavorite: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        color = KitsugiColors.Surface,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = entry.imageUrl,
                contentDescription = entry.title,
                modifier = Modifier.size(width = 62.dp, height = 88.dp).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    entry.title,
                    color = KitsugiColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(if (entry.type == MediaType.Movie) R.string.tmdb_movie_label else R.string.tmdb_tv_label),
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (inWatchlist) Text(stringResource(R.string.tmdb_profile_watchlist), color = accentColor, style = MaterialTheme.typography.labelSmall)
                    if (isFavorite) Text("★", color = accentColor, style = MaterialTheme.typography.labelSmall)
                    (entry.tmdbRating ?: entry.score?.toDouble())?.let { rating ->
                        val label = if (rating % 1.0 == 0.0) rating.toInt().toString()
                        else String.format(java.util.Locale.US, "%.1f", rating)
                        Text("★ $label", color = accentColor, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

private fun MediaEntry.accountKey(): String = "${type.name}:$tmdbId"
