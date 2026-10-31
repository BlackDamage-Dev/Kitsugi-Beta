package com.kitsugi.animelist.ui.screens.profile.tabs

import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.HorizontalDivider
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.Context
import android.widget.Toast
import com.kitsugi.animelist.ui.screens.profile.OtherUserProfileState
import com.kitsugi.animelist.ui.screens.profile.StatCard
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.utils.KitsugiTranslateUtils.openTranslator
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.components.KitsugiTranslateIconButton
import com.kitsugi.animelist.utils.KitsugiStrings

@Composable
fun UserProfileInfoTab(
    state: OtherUserProfileState,
    accentColor: Color,
    username: String,
    onImageClick: (urls: List<String>, idx: Int, title: String) -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(KitsugiColors.Surface)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        val displayAbout = state.about
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.inline_user_bio_46ed806),
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (displayAbout.isNotBlank()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    KitsugiTranslateIconButton(
                        onClick = { context.openTranslator(displayAbout) },
                        modifier = Modifier.size(28.dp),
                        tint = accentColor,
                        contentDescription = stringResource(R.string.cevir),
                        iconSize = 16.dp,
                        ringSize = 24.dp
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
        }
        if (displayAbout.isNotBlank()) {
            com.kitsugi.animelist.ui.components.KitsugiHtmlWebView(
                html = displayAbout,
                modifier = Modifier.fillMaxWidth(),
                onImageClick = { urls, idx ->
                    onImageClick(urls, idx, KitsugiStrings.get(R.string.profile_bio_title, username))
                }
            )
        } else {
            Text(
                text = stringResource(R.string.inline_this_user_hasn_t_added_3eb83d5),
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        HorizontalDivider(color = KitsugiColors.SurfaceStrong)

        Row(modifier = Modifier.fillMaxWidth()) {
            StatCard(stringResource(R.string.profile_anime_entries), state.animeStats?.count?.toString() ?: "0")
            StatCard(stringResource(R.string.profile_manga_entries), state.mangaStats?.count?.toString() ?: "0")
        }
    }
}
