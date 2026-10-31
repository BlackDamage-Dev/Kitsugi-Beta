package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.utils.uniqueItems
import com.kitsugi.animelist.ui.theme.gradient.Text

import android.content.Context
import android.widget.Toast
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.data.remote.JikanApiClient
import com.kitsugi.animelist.data.remote.KitsugiActivity
import com.kitsugi.animelist.data.remote.SocialFeedCache
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogo
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.utils.PreferenceHelpers
import com.kitsugi.animelist.utils.KitsugiTranslateUtils.openTranslator
import kotlinx.coroutines.launch

import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitsugiAllActivitiesBottomSheet(
    source: String,
    externalId: Int,
    mediaType: MediaType = MediaType.Anime,
    apiClient: JikanApiClient,
    tmdbId: Int? = null,
    realMalId: Int? = null,
    titleLanguage: String = "ENGLISH",
    onUserProfileClick: ((userId: Int?, username: String, avatarUrl: String?, source: String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val accentColor = LocalKitsugiAccent.current
    val coroutineScope = rememberCoroutineScope()

    val socialFeedKey = remember(source, externalId, mediaType, tmdbId, realMalId) {
        SocialFeedCache.feedKey(source, externalId, mediaType, tmdbId, realMalId)
    }
    val cachedFeed = remember(socialFeedKey) { SocialFeedCache.allActivities(socialFeedKey) }

    var activitiesList by remember { mutableStateOf<List<KitsugiActivity>>(cachedFeed?.items ?: emptyList()) }
    var isLoading by remember { mutableStateOf(cachedFeed == null) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(cachedFeed?.page ?: 1) }
    var hasMore by remember { mutableStateOf(cachedFeed?.hasMore ?: true) }

    var activeActivityIdForDetail by remember { mutableStateOf<Int?>(null) }

    val listState = rememberLazyListState()

    // Initial Load — profil ekranından geri dönüşte liste önbellekten gelir, yeniden yüklenmez.
    LaunchedEffect(source, externalId, mediaType, tmdbId, realMalId) {
        if (activitiesList.isNotEmpty()) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        activeActivityIdForDetail = null
        page = 1
        hasMore = true
        val fresh = apiClient.fetchActivities(
            source, externalId, page = 1, mediaType = mediaType, tmdbId = tmdbId, realMalId = realMalId
        )
        activitiesList = fresh
        SocialFeedCache.putAllActivities(socialFeedKey, SocialFeedCache.FeedState(fresh, page, hasMore))
        isLoading = false
    }

    // Infinite scroll detection
    val shouldLoadMore = remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItem >= totalItems - 8 && totalItems > 0
        }
    }

    fun loadNextPage() {
        if (isLoading || isLoadingMore || !hasMore) return
        isLoadingMore = true
        coroutineScope.launch {
            try {
                val nextPage = page + 1
                val newActs = apiClient.fetchActivities(
                    source, externalId, page = nextPage, mediaType = mediaType, tmdbId = tmdbId, realMalId = realMalId
                )
                if (newActs.isEmpty()) {
                    hasMore = false
                } else {
                    activitiesList = (activitiesList + newActs).distinctBy { it.id }
                    page = nextPage
                }
                SocialFeedCache.putAllActivities(socialFeedKey, SocialFeedCache.FeedState(activitiesList, page, hasMore))
            } catch (e: Exception) {
                // T3-05: printStackTrace → Log.e
                android.util.Log.e("AllActivitiesSheet", "loadNextPage failed: ${e.message}", e)
            } finally {
                isLoadingMore = false
            }
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            loadNextPage()
        }
    }

    KitsugiSheetOrDialog(
        onDismiss = onDismiss,
        innerScrollState = listState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = LocalDismissAnimated.current,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = KitsugiColors.SurfaceStrong
                    )
                ) {
                    Icon(
                        imageVector = Icons.Close,
                        contentDescription = stringResource(R.string.kapat),
                        tint = KitsugiColors.TextPrimary
                    )
                }

                Text(
                    text = stringResource(R.string.tum_aktiviteler),
                    color = KitsugiColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading && activitiesList.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    KitsugiPlasmaLoader(size = 46.dp)
                }
            } else if (activitiesList.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(text = stringResource(R.string.aktivite_bulunamadi), color = KitsugiColors.TextMuted, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    uniqueItems(activitiesList, key = { it.id }) { act ->
                        // Resolve title according to user's titleLanguage preference
                        val localizedTitle = when (titleLanguage) {
                            "ENGLISH" -> act.mediaTitleEnglish?.takeIf { it.isNotBlank() }
                                ?: act.mediaTitleRomaji
                                ?: act.mediaTitleNative
                                ?: act.mediaTitle
                            "NATIVE", "JAPANESE_STAFF" -> act.mediaTitleNative?.takeIf { it.isNotBlank() }
                                ?: act.mediaTitleRomaji
                                ?: act.mediaTitleEnglish
                                ?: act.mediaTitle
                            else -> act.mediaTitleRomaji
                                ?: act.mediaTitleEnglish
                                ?: act.mediaTitleNative
                                ?: act.mediaTitle
                        }
                        // For ListActivities: regenerate display text with localized title
                        val displayText = if (act.mediaTitle != null && localizedTitle != null && localizedTitle != act.mediaTitle) {
                            act.text.replace("**${act.mediaTitle}**", "**$localizedTitle**")
                        } else act.text
                        
                        // Theme-aligned Activity Card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(KitsugiColors.SurfaceStrong)
                                .tvClickable(shape = RoundedCornerShape(16.dp)) {
                                    Toast.makeText(context, KitsugiStrings.get(R.string.loading), Toast.LENGTH_SHORT).show()
                                    activeActivityIdForDetail = act.id
                                }
                                .padding(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = if (onUserProfileClick != null) {
                                        Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onUserProfileClick(act.userId, act.username, act.avatarUrl, act.source) }
                                            .padding(4.dp)
                                    } else Modifier
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(KitsugiColors.SurfaceSoft)
                                    ) {
                                        if (!act.avatarUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = act.avatarUrl,
                                                contentDescription = act.username,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = act.username.take(1).uppercase(),
                                                    color = KitsugiColors.TextMuted,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Text(
                                        text = act.username,
                                        color = KitsugiColors.TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                if (!act.dateText.isNullOrBlank()) {
                                    Text(
                                        text = act.dateText,
                                        color = KitsugiColors.TextSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    var activeGalleryImages by remember { mutableStateOf<List<String>>(emptyList()) }
                                    var activeGalleryIndex by remember { mutableStateOf(0) }

                                    KitsugiMarkdownText(
                                        text = displayText,
                                        modifier = Modifier.fillMaxWidth(),
                                        onImageGalleryRequest = { urls, index ->
                                            activeGalleryImages = urls
                                            activeGalleryIndex = index
                                        }
                                    )

                                    if (activeGalleryImages.isNotEmpty()) {
                                        KitsugiImageGalleryDialog(
                                            imageUrls = activeGalleryImages,
                                            initialIndex = activeGalleryIndex,
                                            title = act.username,
                                            onDismiss = { activeGalleryImages = emptyList() }
                                        )
                                    }
                                }
                                if (!act.mediaCoverUrl.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.width(12.dp))
                                    AsyncImage(
                                        model = act.mediaCoverUrl,
                                        contentDescription = act.mediaTitle,
                                        modifier = Modifier
                                            .size(width = 40.dp, height = 56.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Bu listede uygulama içi çeviri yok — buton harici çeviriyi açar,
                                    // bu yüzden yüklenme animasyonu gösterilmez (diğer butonlarla aynı görünüm).
                                    KitsugiTranslateIconButton(
                                        onClick = { context.openTranslator(act.text) },
                                        modifier = Modifier.size(28.dp),
                                        tint = accentColor,
                                        contentDescription = stringResource(R.string.cevir),
                                        iconSize = 14.dp,
                                        isLoading = false,
                                        ringSize = 24.dp
                                    )
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("activity_text", act.text))
                                            Toast.makeText(context, KitsugiStrings.get(R.string.toast_copied_clipboard), Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.ContentCopy,
                                            contentDescription = KitsugiStrings.get(R.string.action_copy),
                                            tint = KitsugiColors.TextSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.tvClickable(shape = RoundedCornerShape(8.dp)) {
                                        coroutineScope.launch {
                                            val success = apiClient.toggleLike(act.id, "ACTIVITY")
                                            if (success) {
                                                activitiesList = activitiesList.map {
                                                    if (it.id == act.id) {
                                                        val newLiked = !it.isLiked
                                                        val newCount = if (newLiked) it.likeCount + 1 else it.likeCount - 1
                                                        it.copy(isLiked = newLiked, likeCount = newCount)
                                                    } else {
                                                        it
                                                    }
                                                }
                                                SocialFeedCache.putAllActivities(socialFeedKey, SocialFeedCache.FeedState(activitiesList, page, hasMore))
                                            } else {
                                                Toast.makeText(context, KitsugiStrings.get(R.string.lutfen_once_giris_yapin), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (act.isLiked) Icons.Favorite else Icons.FavoriteBorder,
                                        contentDescription = stringResource(R.string.begeni),
                                        tint = if (act.isLiked) accentColor else KitsugiColors.TextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = act.likeCount.toString(),
                                        color = KitsugiColors.TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    if (isLoadingMore) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                KitsugiPlasmaLoader(size = 26.dp)
                            }
                        }
                    }
                }
            }
        }
    }

    activeActivityIdForDetail?.let { actId ->
        KitsugiActivityDetailBottomSheet(
            activityId = actId,
            apiClient = apiClient,
            titleLanguage = titleLanguage,
            onUserProfileClick = onUserProfileClick,
            onDismiss = { activeActivityIdForDetail = null }
        )
    }
}
