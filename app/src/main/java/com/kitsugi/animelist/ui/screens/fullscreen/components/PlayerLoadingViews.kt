package com.kitsugi.animelist.ui.screens.fullscreen.components
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.components.KitsugiButton

import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast

// ─────────────────────────────────────────────────────────────
// Full-screen loading spinner
// ─────────────────────────────────────────────────────────────
@Composable
fun PlayerLoadingView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(color = Color.White)
            Text(stringResource(R.string.player_loading), color = Color.White.copy(alpha = 0.7f))
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Full-screen buffering view
// ─────────────────────────────────────────────────────────────
@Composable
fun PlayerBufferingView(
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isPlaying) return

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Loading spinner with custom message
// ─────────────────────────────────────────────────────────────
@Composable
fun PlayerInlineLoadingOverlay(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(message, color = Color.White)
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Error screen
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerErrorView(
    message: String?,
    canOpenExternal: Boolean,
    onBack: () -> Unit,
    onOpenExternal: () -> Unit,
    onRetry: (() -> Unit)? = null,
    onSwitchSource: (() -> Unit)? = null,
    switchSourceText: String? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(24.dp).widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.PlayCircle,
                null,
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = stringResource(R.string.player_error_title),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = message ?: stringResource(R.string.player_error_desc),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            // Hata yakalayici: detay metni secilebilir + kopyala butonu (kullanici nedenini gorsun)
            if (!message.isNullOrBlank()) {
                val clipboard = LocalClipboardManager.current
                val ctx = LocalContext.current
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Detay metnini secilebilir yap (kullanici uzun basip kopyalayabilir)
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(
                            text = message,
                            color = Color.White.copy(alpha = 0.55f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(message))
                        Toast.makeText(ctx, "Hata detayi kopyalandi", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.ContentCopy, contentDescription = "Kopyala", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                    }
                }
                Text(
                    text = "Detay Ayarlar > Tani Raporu'na da yazildi",
                    color = Color.White.copy(alpha = 0.35f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                maxItemsInEachRow = 3
            ) {
                KitsugiButton(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.player_btn_back), color = Color.White)
                }
                
                if (onRetry != null) {
                    KitsugiButton(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(stringResource(R.string.player_btn_retry), color = Color.White)
                    }
                }

                if (onSwitchSource != null) {
                    KitsugiButton(
                        onClick = onSwitchSource,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(switchSourceText ?: stringResource(R.string.player_btn_switch_source), color = Color.White)
                    }
                }

                if (canOpenExternal) {
                    KitsugiButton(
                        onClick = onOpenExternal,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.15f)
                        )
                    ) {
                        Text(stringResource(R.string.player_btn_external), color = Color.White)
                    }
                }
            }
        }
    }
}
