package com.kitsugi.animelist.ui.screens.fullscreen.controls.components.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.TextButton
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.ui.screens.fullscreen.ArtType
import java.io.InputStream
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

@Composable
fun ScreenshotSheet(
    isLocalSource: Boolean,
    hasSubTracks: Boolean,
    showSubtitles: Boolean,
    onToggleShowSubtitles: (Boolean) -> Unit,
    cachePath: String,
    onSetAsArt: (ArtType, (() -> InputStream)) -> Unit,
    onShare: (() -> InputStream) -> Unit,
    onSave: (() -> InputStream) -> Unit,
    takeScreenshot: suspend (String, Boolean) -> InputStream?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var setArtTypeAs: ArtType? by remember { mutableStateOf(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var capturing by remember { mutableStateOf(false) }
    fun capture(action: (() -> InputStream) -> Unit) {
        if (capturing) return
        capturing = true
        scope.launch {
            try {
                val stream = takeScreenshot(cachePath, showSubtitles)
                if (stream == null) {
                    android.widget.Toast.makeText(context, "Ekran görüntüsü alınamadı", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    val bytes = stream.use { input ->
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { input.readBytes() }
                    }
                    action { java.io.ByteArrayInputStream(bytes) }
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.e("ScreenshotSheet", "Screenshot failed", error)
                android.widget.Toast.makeText(context, "Ekran görüntüsü alınamadı", android.widget.Toast.LENGTH_SHORT).show()
            } finally { capturing = false }
        }
    }

    PlayerSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!isLocalSource) {
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        title = "Kapak Yap",
                        icon = Icons.Photo,
                        onClick = { setArtTypeAs = ArtType.Cover },
                    )
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        title = "Arka Plan",
                        icon = Icons.Photo,
                        onClick = { setArtTypeAs = ArtType.Background },
                    )
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.gallery_cat_thumbnail),
                        icon = Icons.Photo,
                        onClick = { setArtTypeAs = ArtType.Thumbnail },
                    )
                }
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.paylas),
                    icon = Icons.Share,
                    onClick = {
                        capture { image -> onShare(image); onDismissRequest() }
                    },
                )
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.kaydet),
                    icon = Icons.Save,
                    onClick = {
                        capture { image -> onSave(image); onDismissRequest() }
                    },
                )
            }

            if (hasSubTracks) {
                SwitchPreference(
                    value = showSubtitles,
                    onValueChange = onToggleShowSubtitles,
                    modifier = Modifier.padding(bottom = 16.dp),
                    content = {
                        Text(
                            text = stringResource(R.string.inline_include_subtitles_5a6ab17),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }
        }
    }

    // Yerel kopya: AlertDialog içerik lambda'ları ertelenmiş bestelenir; bu sırada
    // setArtTypeAs null'a dönerse `!!` NullPointerException atıyordu.
    val artTypeCandidate = setArtTypeAs
    if (artTypeCandidate != null) {
        val artName = when (artTypeCandidate) {
            ArtType.Cover -> "Kapak Resmi"
            ArtType.Background -> "Duvar Kağıdı"
            ArtType.Thumbnail -> "Küçük Resim"
        }
        AlertDialog(
            onDismissRequest = { setArtTypeAs = null },
            title = { Text("Resmi Ayarla") },
            text = { Text(stringResource(R.string.inline_are_you_sure_you_want_0ccf9e8, artName)) },
            confirmButton = {
                TextButton(onClick = {
                    val artType = artTypeCandidate
                    capture { image ->
                        onSetAsArt(artType, image)
                        setArtTypeAs = null
                        onDismissRequest()
                    }
                }) {
                    Text(stringResource(R.string.action_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { setArtTypeAs = null }) {
                    Text(stringResource(R.string.hayir))
                }
            }
        )
    }
}
