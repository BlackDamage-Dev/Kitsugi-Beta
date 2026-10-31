package com.kitsugi.animelist.ui.screens.profile

import com.kitsugi.animelist.ui.theme.gradient.border
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.theme.KitsugiColors
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

@Composable
fun UserProfileHeaderCard(
    state: OtherUserProfileState,
    fallbackUsername: String?,
    fallbackAvatar: String?,
    accentColor: Color,
    onImageClick: (urls: List<String>, idx: Int, title: String) -> Unit
) {
    val avatarUrl = (state.avatarUrl ?: fallbackAvatar)?.takeIf { it.isNotBlank() }
    val bannerUrl = state.bannerUrl?.takeIf { it.isNotBlank() }
    val imageList = listOfNotNull(avatarUrl, bannerUrl)
    val username = state.name.ifBlank { fallbackUsername ?: stringResource(R.string.kullanici) }
    val bannerTitle = stringResource(R.string.profile_banner_title, username)
    val avatarTitle = stringResource(R.string.profile_avatar_title, username)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .then(if (!bannerUrl.isNullOrBlank()) Modifier.clickable {
                onImageClick(imageList, imageList.indexOf(bannerUrl).coerceAtLeast(0), bannerTitle)
            } else Modifier)
    ) {
        if (!bannerUrl.isNullOrBlank()) {
            AsyncImage(model = bannerUrl, contentDescription = bannerTitle,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(modifier = Modifier.fillMaxSize()
                .background(
                    if (KitsugiColors.IsGradient) KitsugiColors.AccentBrush
                    else androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(accentColor, KitsugiColors.AccentBlue))
                ))
        }
        Box(modifier = Modifier.fillMaxSize()
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(Color.Transparent, KitsugiColors.Background.copy(alpha = 0.85f)))))

        Row(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = avatarUrl ?: "",
                contentDescription = avatarTitle,
                modifier = Modifier.size(68.dp).clip(CircleShape)
                    .border(3.dp, KitsugiColors.Background, CircleShape)
                    .then(if (!avatarUrl.isNullOrBlank()) Modifier.clickable {
                        onImageClick(imageList, imageList.indexOf(avatarUrl).coerceAtLeast(0), avatarTitle)
                    } else Modifier),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = state.name.ifBlank { fallbackUsername ?: stringResource(R.string.ui_profile_anilist_fallback) },
                        color = KitsugiColors.TextPrimary, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val badge = state.donatorBadge
                    if (!badge.isNullOrBlank() && state.donatorTier > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = badge, color = accentColor, style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                .background(accentColor.copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                if (state.isFollower) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = stringResource(R.string.profile_follows_you), color = KitsugiColors.AccentGreen,
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun UserProfileActionButtons(
    state: OtherUserProfileState,
    accentColor: Color,
    userId: Int,
    viewModel: KitsugiUserProfileViewModel,
    onOpenUserMediaList: (Int, MediaType) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {

        Box(modifier = Modifier.weight(1.2f).clip(RoundedCornerShape(14.dp))
            .background(if (state.isFollowing) KitsugiColors.SurfaceStrong else accentColor)
            .clickable(enabled = !state.isFollowLoading) { viewModel.toggleFollow() }
            .padding(vertical = 10.dp, horizontal = 12.dp),
            contentAlignment = Alignment.Center) {
            if (state.isFollowLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp),
                    color = if (state.isFollowing) KitsugiColors.TextPrimary else KitsugiColors.Background,
                    strokeWidth = 2.dp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = if (state.isFollowing) Icons.Check else Icons.PersonAdd,
                        contentDescription = null,
                        tint = if (state.isFollowing) KitsugiColors.TextPrimary else KitsugiColors.Background,
                        modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = if (state.isFollowing) stringResource(R.string.profile_following_button) else stringResource(R.string.profile_follow),
                        color = if (state.isFollowing) KitsugiColors.TextPrimary else KitsugiColors.Background,
                        fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        listOf(
            stringResource(R.string.profile_anime_list) to MediaType.Anime,
            stringResource(R.string.profile_manga_list) to MediaType.Manga
        ).forEach { (label, type) ->
            val tint = if (type == MediaType.Anime) accentColor else KitsugiColors.AccentOrange
            Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                .background(KitsugiColors.Surface)
                .clickable { onOpenUserMediaList(userId, type) }
                .padding(vertical = 10.dp, horizontal = 8.dp),
                contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.ListAlt, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = label, color = KitsugiColors.TextPrimary, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
