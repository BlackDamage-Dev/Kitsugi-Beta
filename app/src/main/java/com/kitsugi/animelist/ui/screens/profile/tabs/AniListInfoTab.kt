package com.kitsugi.animelist.ui.screens.profile.tabs

import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

import android.content.Context
import android.widget.Toast
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.ui.app.AniListProfileState
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.utils.KitsugiTranslateUtils.openTranslator
import com.kitsugi.animelist.ui.screens.profile.StatCard
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.components.KitsugiOriginalToggleChip
import com.kitsugi.animelist.ui.components.KitsugiTranslateIconButton
import com.kitsugi.animelist.ui.components.rememberAutoTranslatedHtml
import com.kitsugi.animelist.utils.KitsugiStrings

@Composable
fun AniListInfoTab(
    state: AniListProfileState,
    accentColor: Color,
    autoTranslateEnabled: Boolean = false,
    preferredTranslator: String = "DEFAULT",
    onImageClick: ((urls: List<String>, initialIndex: Int, title: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val displayAbout = state.about
    // Wiki mantığı: otomatik çeviri açıksa "Hakkında" metni uygulama içinde çevrilir.
    val aboutTranslation = rememberAutoTranslatedHtml(displayAbout, autoTranslateEnabled)
    val renderedAbout = if (displayAbout.isBlank()) "" else aboutTranslation.displayText

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (displayAbout.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(KitsugiColors.Surface)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.hakkinda),
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (aboutTranslation.hasTranslation) {
                            KitsugiOriginalToggleChip(
                                showingOriginal = aboutTranslation.showingOriginal,
                                onClick = aboutTranslation.toggleOriginal,
                                tint = accentColor
                            )
                        }
                        KitsugiTranslateIconButton(
                            onClick = { context.openTranslator(displayAbout, preferredTranslator) },
                            modifier = Modifier.size(28.dp),
                            tint = accentColor,
                            contentDescription = stringResource(R.string.cevir),
                            iconSize = 16.dp,
                            ringSize = 24.dp,
                            isLoading = aboutTranslation.isLoading
                        )
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("about", displayAbout))
                                Toast.makeText(context, KitsugiStrings.get(R.string.toast_copied_clipboard), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.ContentCopy,
                                contentDescription = stringResource(R.string.action_copy),
                                tint = KitsugiColors.TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                com.kitsugi.animelist.ui.components.KitsugiHtmlWebView(
                    html = renderedAbout,
                    modifier = Modifier.fillMaxWidth(),
                    onImageClick = { urls, idx ->
                        onImageClick?.invoke(urls, idx, KitsugiStrings.get(R.string.profile_bio_title, state.name))
                    }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(KitsugiColors.Surface)
                .padding(18.dp)
        ) {
            Text(
                text = stringResource(R.string.inline_profile_overview_9458157),
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCard("Anime", state.animeStats?.count?.toString() ?: "0")
                StatCard("Manga", state.mangaStats?.count?.toString() ?: "0")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCard(stringResource(R.string.profile_followers), state.socialState.followers.size.toString())
                StatCard(stringResource(R.string.profile_following), state.socialState.following.size.toString())
            }
        }
    }
}
