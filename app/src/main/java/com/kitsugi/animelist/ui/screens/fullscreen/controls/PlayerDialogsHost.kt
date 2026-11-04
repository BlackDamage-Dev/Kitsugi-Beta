package com.kitsugi.animelist.ui.screens.fullscreen.controls
import com.kitsugi.animelist.ui.screens.fullscreen.playerSurfaceColor
import com.kitsugi.animelist.ui.components.KitsugiButton

import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Slider
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.kitsugi.animelist.data.remote.KitsugiStreamingEpisode
import com.kitsugi.animelist.ui.screens.fullscreen.KitsugiDialogs
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

@Composable
fun PlayerDialogsHost(
    dialogShown: KitsugiDialogs,
    episodes: List<KitsugiStreamingEpisode>,
    currentEpisode: Int,
    onPlayEpisode: (Int) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    /** Bölüm listesi arka planda çözülüyor mu? (boş çekmece yerine yükleme göstergesi) */
    episodesLoading: Boolean = false,
    /** Film gibi bölümü olmayan içeriklerde boş liste yerine açıklama gösterilir. */
    isMovie: Boolean = false,
    /** Boş/eksik listede "Yeniden Dene" — tüm kaynaklar sıfırdan denenir. */
    onRetryEpisodes: (() -> Unit)? = null,
) {
    when (dialogShown) {
        is KitsugiDialogs.None -> Unit
        is KitsugiDialogs.EpisodeList -> {
            EpisodeListDialog(
                episodes = episodes,
                currentEpisode = currentEpisode,
                onPlayEpisode = onPlayEpisode,
                onDismissRequest = onDismissRequest,
                modifier = modifier,
                isLoading = episodesLoading,
                isMovie = isMovie,
                onRetryEpisodes = onRetryEpisodes,
            )
        }
        is KitsugiDialogs.IntegerPicker -> {
            IntegerPickerDialog(
                picker = dialogShown,
                modifier = modifier,
            )
        }
    }
}

@Composable
fun EpisodeListDialog(
    episodes: List<KitsugiStreamingEpisode>,
    currentEpisode: Int,
    onPlayEpisode: (Int) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    isMovie: Boolean = false,
    onRetryEpisodes: (() -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = playerSurfaceColor(0.95f),
                contentColor = Color.White
            ),
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.inline_episode_list_a73f6b7),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Close,
                            contentDescription = stringResource(R.string.kapat),
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Liste çözülürken kısa bir bilgi satırı (liste zaten doluysa üstte kalır).
                if (episodes.isNotEmpty() && isLoading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.5.dp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.bolum_listesi_guncelleniyor),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                // Bölüm listesi HİÇBİR ZAMAN boş ekran olarak açılmaz:
                //  - liste hazırlanıyorsa yükleme göstergesi,
                //  - film ise açıklama,
                //  - liste gerçekten çözülemediyse sebep + "Yeniden Dene".
                if (episodes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(34.dp),
                                    strokeWidth = 3.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = stringResource(R.string.bolum_listesi_yukleniyor),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.75f)
                                )
                            } else if (isMovie) {
                                Text(
                                    text = stringResource(R.string.bolum_listesi_film),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.75f)
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.bolum_listesi_bos),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                                Text(
                                    text = stringResource(R.string.bolum_listesi_bos_ipucu),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                                if (onRetryEpisodes != null) {
                                    TextButton(onClick = onRetryEpisodes) {
                                        Text(
                                            text = stringResource(R.string.yeniden_dene),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                val episodeListState = rememberLazyListState()
                LaunchedEffect(episodes.size, currentEpisode) {
                    val index = episodes.indexOfFirst { it.episodeNumber == currentEpisode }
                    if (index > 0) {
                        episodeListState.scrollToItem(index)
                    }
                }
                LazyColumn(
                    state = episodeListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(episodes) { episode ->
                        val isCurrent = episode.episodeNumber == currentEpisode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isCurrent) Color.White.copy(alpha = 0.15f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    episode.episodeNumber?.let { onPlayEpisode(it) }
                                    onDismissRequest()
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!episode.thumbnail.isNullOrBlank()) {
                                AsyncImage(
                                    model = episode.thumbnail,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(width = 80.dp, height = 45.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.inline_episode_1_s_dc662be, episode.episodeNumber ?: "?"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White
                                )
                                if (!episode.title.isNullOrBlank()) {
                                    Text(
                                        text = episode.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            if (isCurrent) {
                                Icon(
                                    imageVector = Icons.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(text = stringResource(R.string.kapat))
                    }
                }
            }
        }
    }
}

@Composable
fun IntegerPickerDialog(
    picker: KitsugiDialogs.IntegerPicker,
    modifier: Modifier = Modifier,
) {
    var currentValue by remember { mutableFloatStateOf(picker.defaultValue.toFloat()) }

    AlertDialog(
        onDismissRequest = picker.onDismissRequest,
        title = {
            Text(text = picker.title)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val formattedText = picker.nameFormat.format(currentValue.toInt())
                Text(
                    text = formattedText,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Slider(
                    value = currentValue,
                    onValueChange = {
                        val snapped = (Math.round(it / picker.step) * picker.step).toFloat()
                        currentValue = snapped.coerceIn(picker.minValue.toFloat(), picker.maxValue.toFloat())
                    },
                    valueRange = picker.minValue.toFloat()..picker.maxValue.toFloat(),
                    steps = ((picker.maxValue - picker.minValue) / picker.step) - 1
                )
            }
        },
        confirmButton = {
            KitsugiButton(
                onClick = {
                    picker.onChange(currentValue.toInt())
                    picker.onDismissRequest()
                }
            ) {
                Text(text = "Uygula")
            }
        },
        dismissButton = {
            TextButton(onClick = picker.onDismissRequest) {
                Text(text = stringResource(R.string.iptal))
            }
        },
        modifier = modifier
    )
}
