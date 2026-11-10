package com.kitsugi.animelist.ui.screens.detail

import android.content.Context
import android.widget.Toast
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import com.kitsugi.animelist.data.remote.stableKey
import com.kitsugi.animelist.ui.utils.uniqueItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.data.remote.JikanApiClient
import com.kitsugi.animelist.data.remote.KitsugiActivity
import com.kitsugi.animelist.data.remote.KitsugiForumTopic
import com.kitsugi.animelist.data.remote.KitsugiReview
import com.kitsugi.animelist.data.remote.SocialFeedCache
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.components.KitsugiActivityDetailBottomSheet
import com.kitsugi.animelist.ui.components.KitsugiAllActivitiesBottomSheet
import com.kitsugi.animelist.ui.components.KitsugiAllReviewsBottomSheet
import com.kitsugi.animelist.ui.components.KitsugiAllTopicsBottomSheet
import com.kitsugi.animelist.ui.components.KitsugiMarkdownText
import com.kitsugi.animelist.ui.components.KitsugiReviewDetailBottomSheet
import com.kitsugi.animelist.ui.components.KitsugiTopicDetailBottomSheet
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogo
import com.kitsugi.animelist.ui.components.KitsugiShimmerSearchResultList
import androidx.compose.foundation.clickable
import com.kitsugi.animelist.utils.ShareUtils
import com.kitsugi.animelist.utils.KitsugiTranslateUtils.openTranslator
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement


@Composable
fun ReviewsTabContent(
    state: DetailTabState<List<KitsugiReview>>,
    source: String,
    externalId: Int,
    mediaType: MediaType,
    apiClient: JikanApiClient,
    tmdbId: Int? = null,
    realMalId: Int? = null,
    titleLanguage: String = "ENGLISH",
    preferredTranslator: String = "DEFAULT",
    onUserProfileClick: (userId: Int, username: String, avatarUrl: String?, source: String) -> Unit,
    onImageGalleryRequest: ((urls: List<String>, index: Int) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    // Alt detay sayfaları (inceleme/konu/aktivite) da profil ekranına gidip geri
    // dönüldüğünde korunur: kimlikler saveable anahtar olarak saklanır, nesneler
    // geri yüklendiği listeden yeniden bulunur.
    var activeReviewDetailKey by rememberSaveable { mutableStateOf("") }
    var activeTopicDetailKey by rememberSaveable { mutableStateOf("") }
    var activeActivityIdForDetail by rememberSaveable { mutableStateOf(-1) }

    val handleUserProfileClick: (userId: Int?, username: String, avatarUrl: String?, profileSource: String) -> Unit = { userId, username, avatarUrl, profileSource ->
        val canonical = com.kitsugi.animelist.data.remote.canonicalMediaSourceId(profileSource)
        when (canonical) {
            // MAL / Kitsu / Simkl kullanıcıları → uygulama içi, platforma özel profil
            "mal", "kitsu", "simkl" -> onUserProfileClick(userId ?: 0, username, avatarUrl, canonical)
            // AniList kullanıcısı (userId varsa) → uygulama içi AniList profili
            "anilist" -> {
                if (userId != null) {
                    onUserProfileClick(userId, username, avatarUrl, "anilist")
                } else if (username.isNotBlank()) {
                    try {
                        val url = ShareUtils.buildProfileUrl("anilist", username)
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, KitsugiStrings.get(R.string.profil_linki_acilamadi), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            // Diğer platformlar (TMDB / Shikimori / Bangumi) → doğru sitede dış profil
            else -> {
                if (username.isNotBlank()) {
                    try {
                        val url = ShareUtils.buildProfileUrl(profileSource, username)
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, KitsugiStrings.get(R.string.profil_linki_acilamadi), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Profil ekranına gidip geri dönüldüğünde yorumlar sayfası bozulmasın: akış
    // verileri [SocialFeedCache] içinde saklanır, alt sayfa açık/kapalı durumu
    // [rememberSaveable] ile korunur.
    var showAllTopicsSheet by rememberSaveable { mutableStateOf(false) }
    var showAllActivitiesSheet by rememberSaveable { mutableStateOf(false) }
    var showAllReviewsSheet by rememberSaveable { mutableStateOf(false) }

    val socialFeedKey = remember(source, externalId, mediaType, tmdbId, realMalId) {
        SocialFeedCache.feedKey(source, externalId, mediaType, tmdbId, realMalId)
    }

    var forumTopics by remember { mutableStateOf<List<KitsugiForumTopic>>(SocialFeedCache.previewTopics(socialFeedKey)?.items ?: emptyList()) }
    var activitiesList by remember { mutableStateOf<List<KitsugiActivity>>(SocialFeedCache.previewActivities(socialFeedKey)?.items ?: emptyList()) }

    LaunchedEffect(socialFeedKey) {
        val cachedTopics = SocialFeedCache.previewTopics(socialFeedKey)?.items
        val cachedActivities = SocialFeedCache.previewActivities(socialFeedKey)?.items
        if (cachedTopics != null && cachedActivities != null) {
            // Geri dönüş: veriler önbellekte, ağa tekrar düşülmez (sayfa "bozulmaz").
            forumTopics = cachedTopics
            activitiesList = cachedActivities
            return@LaunchedEffect
        }
        if (forumTopics.isEmpty()) {
            launch {
                runCatching {
                    val topics = apiClient.fetchForumTopics(
                        source, externalId, mediaType, tmdbId = tmdbId, realMalId = realMalId
                    )
                    if (topics.isNotEmpty()) {
                        forumTopics = topics
                        SocialFeedCache.putPreviewTopics(socialFeedKey, topics)
                    }
                }
            }
        }
        if (activitiesList.isEmpty()) {
            launch {
                runCatching {
                    val acts = apiClient.fetchActivities(
                        source, externalId, mediaType = mediaType, tmdbId = tmdbId, realMalId = realMalId
                    )
                    if (acts.isNotEmpty()) {
                        activitiesList = acts
                        SocialFeedCache.putPreviewActivities(socialFeedKey, acts)
                    }
                }
            }
        }
    }

    var reviewsList by remember(state) {
        mutableStateOf(if (state is DetailTabState.Success) state.data else emptyList())
    }

    // Korunan alt detay sayfalarının nesnelerini listelerden geri bul.
    val activeReviewForDetail: KitsugiReview? = remember(activeReviewDetailKey, reviewsList) {
        if (activeReviewDetailKey.isEmpty()) null
        else reviewsList.find { it.stableKey() == activeReviewDetailKey }
    }
    val activeTopicForDetail: KitsugiForumTopic? = remember(activeTopicDetailKey, forumTopics) {
        if (activeTopicDetailKey.isEmpty()) null
        else forumTopics.find { "${it.source}_${it.id}" == activeTopicDetailKey }
    }

    val topicsListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val activitiesListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val reviewsListState = androidx.compose.foundation.lazy.rememberLazyListState()

    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // --- 1. FORUM TOPICS SECTION ---
        if (forumTopics.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(KitsugiColors.Surface)
                    .border(1.dp, KitsugiColors.Border.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                    .padding(vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.tartisma_konulari),
                        color = KitsugiColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black
                    )

                    IconButton(
                        onClick = { showAllTopicsSheet = true },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = KitsugiColors.SurfaceStrong
                        ),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.List,
                            contentDescription = stringResource(R.string.tumunu_gor),
                            tint = KitsugiColors.TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                LazyRow(
                    state = topicsListState,
                    flingBehavior = com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults.rememberRowSnapFling(topicsListState),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // API sayfaları çakışırsa aynı konu iki kez gelebilir → çökmeyen anahtar.
                    uniqueItems(
                        items = forumTopics,
                        key = { topic -> "topic_${topic.source}_${topic.id}" },
                        contentType = { "forum_topic_card" }
                    ) { topic ->
                        TopicCard(
                            topic = topic,
                            onUserProfileClick = handleUserProfileClick,
                            onClick = {
                                Toast.makeText(context, KitsugiStrings.get(R.string.loading), Toast.LENGTH_SHORT).show()
                                activeTopicDetailKey = "${topic.source}_${topic.id}"
                            },
                            backgroundColor = KitsugiColors.SurfaceSoft,
                            onLikeClick = if (topic.source.equals("anilist", ignoreCase = true)) {
                                {
                                    coroutineScope.launch {
                                        val success = apiClient.toggleLike(topic.id, "THREAD")
                                        if (success) {
                                            forumTopics = forumTopics.map {
                                                if (it.id == topic.id) {
                                                    val newLiked = !it.isLiked
                                                    val newCount = if (newLiked) it.likeCount + 1 else it.likeCount - 1
                                                    it.copy(isLiked = newLiked, likeCount = newCount)
                                                } else {
                                                    it
                                                }
                                            }
                                            SocialFeedCache.putPreviewTopics(socialFeedKey, forumTopics)
                                        } else {
                                            Toast.makeText(context, KitsugiStrings.get(R.string.lutfen_once_giris_yapin), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            } else null
                        )
                    }
                }
            }
        }

        // --- 2. ACTIVITIES SECTION ---
        if (activitiesList.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(KitsugiColors.Surface)
                    .border(1.dp, KitsugiColors.Border.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                    .padding(vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Aktiviteler",
                        color = KitsugiColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black
                    )

                    IconButton(
                        onClick = { showAllActivitiesSheet = true },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = KitsugiColors.SurfaceStrong
                        ),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.List,
                            contentDescription = stringResource(R.string.tumunu_gor),
                            tint = KitsugiColors.TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                LazyRow(
                    state = activitiesListState,
                    flingBehavior = com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults.rememberRowSnapFling(activitiesListState),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    uniqueItems(
                        items = activitiesList,
                        key = { activity -> "activity_${activity.id}" },
                        contentType = { "activity_card" }
                    ) { activity ->
                        ActivityCard(
                            activity = activity,
                            titleLanguage = titleLanguage,
                            onUserProfileClick = handleUserProfileClick,
                            onClick = {
                                Toast.makeText(context, KitsugiStrings.get(R.string.loading), Toast.LENGTH_SHORT).show()
                                activeActivityIdForDetail = activity.id
                            },
                            backgroundColor = KitsugiColors.SurfaceSoft,
                            onLikeClick = {
                                // Aktiviteler her zaman AniList kaynaklıdır (MAL için de ARM ile çözülür) → beğeni her kaynakta denenir
                                coroutineScope.launch {
                                    val success = apiClient.toggleLike(activity.id, "ACTIVITY")
                                    if (success) {
                                        activitiesList = activitiesList.map {
                                            if (it.id == activity.id) {
                                                val newLiked = !it.isLiked
                                                val newCount = if (newLiked) it.likeCount + 1 else it.likeCount - 1
                                                it.copy(isLiked = newLiked, likeCount = newCount)
                                            } else {
                                                it
                                            }
                                        }
                                        SocialFeedCache.putPreviewActivities(socialFeedKey, activitiesList)
                                    } else {
                                        Toast.makeText(context, KitsugiStrings.get(R.string.lutfen_once_giris_yapin), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        // --- 3. REVIEWS SECTION ---
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(KitsugiColors.Surface)
                .border(1.dp, KitsugiColors.Border.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                .padding(vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.incelemeler),
                    color = KitsugiColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black
                )

                IconButton(
                    onClick = { showAllReviewsSheet = true },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = KitsugiColors.SurfaceStrong
                    ),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.List,
                        contentDescription = stringResource(R.string.tumunu_gor),
                        tint = KitsugiColors.TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            when (state) {
                is DetailTabState.Loading -> {
                    Box(modifier = Modifier.padding(horizontal = 14.dp)) {
                        DetailReviewsLoadingSkeleton(itemCount = 2)
                    }
                }
                is DetailTabState.Error -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.inline_an_error_occurred_while_loading_4764095),
                            color = KitsugiColors.AccentRed,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 16.dp, horizontal = 14.dp)
                        )
                        DetailTabErrorCodeChip(platformErrorCode = state.platformErrorCode)
                    }
                }
                is DetailTabState.Success -> {
                    if (reviewsList.isEmpty()) {
                        Text(
                            text = stringResource(R.string.inline_no_reviews_have_been_written_d0351d2),
                            color = KitsugiColors.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 16.dp, horizontal = 14.dp)
                        )
                    } else {
                        LazyRow(
                            state = reviewsListState,
                            flingBehavior = com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults.rememberRowSnapFling(reviewsListState),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // v2.4.369 ÇÖKMESİ: TMDB / MAL(Jikan) / Bangumi incelemelerinde id=null →
                            // her kart "tmdb_null" anahtarını alıp LazyRow'u çökertiyordu.
                            uniqueItems(
                                items = reviewsList,
                                key = { rev -> rev.stableKey() },
                                contentType = { "review_card" }
                            ) { rev ->
                                KitsugiReviewCard(
                                    rev = rev,
                                    modifier = Modifier.width(280.dp),
                                    preferredTranslator = preferredTranslator,
                                    backgroundColor = KitsugiColors.SurfaceSoft,
                                    onUserProfileClick = handleUserProfileClick,
                                    onClick = { activeReviewDetailKey = rev.stableKey() },
                                    onHelpfulClick = {
                                        if (rev.id == null || rev.source != "anilist") {
                                            Toast.makeText(context, KitsugiStrings.get(R.string.begeni_sadece_anilist_incelemelerinde_desteklenir), Toast.LENGTH_SHORT).show()
                                            return@KitsugiReviewCard
                                        }
                                        coroutineScope.launch {
                                            val currentRating = rev.userRating
                                            val newRating = if (currentRating == "UP_VOTE") "NO_RATING" else "UP_VOTE"
                                            val success = apiClient.rateReview(rev.id, newRating)
                                            if (success) {
                                                reviewsList = reviewsList.map {
                                                    if (it.id == rev.id) {
                                                        val diff = if (newRating == "UP_VOTE") 1 else -1
                                                        val oldCount = it.helpfulCount ?: 0
                                                        it.copy(
                                                            userRating = newRating,
                                                            helpfulCount = (oldCount + diff).coerceAtLeast(0)
                                                        )
                                                    } else {
                                                        it
                                                    }
                                                }
                                            } else {
                                                Toast.makeText(context, KitsugiStrings.get(R.string.lutfen_once_giris_yapin), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onImageGalleryRequest = onImageGalleryRequest
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- BOTTOM SHEETS TRIGGERS ---
    if (showAllTopicsSheet) {
        KitsugiAllTopicsBottomSheet(
            source = source,
            externalId = externalId,
            mediaType = mediaType,
            apiClient = apiClient,
            tmdbId = tmdbId,
            realMalId = realMalId,
            onUserProfileClick = handleUserProfileClick,
            onDismiss = { showAllTopicsSheet = false }
        )
    }

    if (showAllActivitiesSheet) {
        KitsugiAllActivitiesBottomSheet(
            source = source,
            externalId = externalId,
            mediaType = mediaType,
            apiClient = apiClient,
            tmdbId = tmdbId,
            realMalId = realMalId,
            titleLanguage = titleLanguage,
            onUserProfileClick = handleUserProfileClick,
            onDismiss = { showAllActivitiesSheet = false }
        )
    }

    if (showAllReviewsSheet) {
        KitsugiAllReviewsBottomSheet(
            source = source,
            externalId = externalId,
            mediaType = mediaType,
            apiClient = apiClient,
            tmdbId = tmdbId,
            realMalId = realMalId,
            initialReviews = reviewsList,
            onUserProfileClick = handleUserProfileClick,
            onDismiss = { showAllReviewsSheet = false }
        )
    }

    activeReviewForDetail?.let { rev ->
        KitsugiReviewDetailBottomSheet(
            review = rev,
            apiClient = apiClient,
            onUserProfileClick = handleUserProfileClick,
            onDismiss = { activeReviewDetailKey = "" }
        )
    }

    activeTopicForDetail?.let { top ->
        KitsugiTopicDetailBottomSheet(
            topic = top,
            source = source,
            apiClient = apiClient,
            onUserProfileClick = handleUserProfileClick,
            onDismiss = { activeTopicDetailKey = "" }
        )
    }

    if (activeActivityIdForDetail >= 0) {
        KitsugiActivityDetailBottomSheet(
            activityId = activeActivityIdForDetail,
            apiClient = apiClient,
            titleLanguage = titleLanguage,
            onUserProfileClick = handleUserProfileClick,
            onDismiss = { activeActivityIdForDetail = -1 }
        )
    }
}

@Composable
private fun TopicCard(
    topic: KitsugiForumTopic,
    onUserProfileClick: ((userId: Int?, username: String, avatarUrl: String?, source: String) -> Unit)? = null,
    onClick: () -> Unit,
    onLikeClick: (() -> Unit)? = null,
    backgroundColor: androidx.compose.ui.graphics.Color = KitsugiColors.Surface
) {
    val accentColor = LocalKitsugiAccent.current

    Column(
        modifier = Modifier
            .width(280.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .tvClickable(shape = RoundedCornerShape(16.dp), onClick = onClick)
            .padding(16.dp)
            .height(144.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Center-aligned italicized title
        Text(
            text = topic.title.replace(Regex("[*_`~]"), ""),
            color = KitsugiColors.TextPrimary,
            fontSize = 13.sp,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        // Bottom row containing stats & author profile info
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Stats (Likes, Comments, Views)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onLikeClick != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.tvClickable(shape = RoundedCornerShape(8.dp), onClick = onLikeClick)
                    ) {
                        Icon(
                            imageVector = if (topic.isLiked) Icons.Favorite else Icons.FavoriteBorder,
                            contentDescription = null,
                            tint = if (topic.isLiked) accentColor else KitsugiColors.TextMuted,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = topic.likeCount.toString(),
                            color = if (topic.isLiked) accentColor else KitsugiColors.TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.ChatBubbleOutline,
                        contentDescription = null,
                        tint = KitsugiColors.TextMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = topic.commentCount.toString(),
                        color = KitsugiColors.TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (topic.viewCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Visibility,
                            contentDescription = null,
                            tint = KitsugiColors.TextMuted,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = topic.viewCount.toString(),
                            color = KitsugiColors.TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Author Profile Clickable
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = if (onUserProfileClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onUserProfileClick(topic.userId, topic.username, topic.avatarUrl, topic.source) }
                        .padding(4.dp)
                } else Modifier
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(KitsugiColors.SurfaceSoft)
                ) {
                    if (!topic.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = topic.avatarUrl,
                            contentDescription = topic.username,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = topic.username.take(1).uppercase(),
                                color = KitsugiColors.TextMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = topic.username,
                    color = KitsugiColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 80.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                KitsugiPlatformLogo(platformId = topic.source, size = 12.dp)
            }
        }
    }
}

@Composable
private fun ActivityCard(
    activity: KitsugiActivity,
    titleLanguage: String = "ENGLISH",
    onUserProfileClick: ((userId: Int?, username: String, avatarUrl: String?, source: String) -> Unit)? = null,
    onClick: () -> Unit,
    onLikeClick: () -> Unit,
    backgroundColor: androidx.compose.ui.graphics.Color = KitsugiColors.Surface
) {
    val accentColor = LocalKitsugiAccent.current

    // Resolve localized title for ListActivity entries
    val localizedTitle = when (titleLanguage) {
        "ENGLISH" -> activity.mediaTitleEnglish?.takeIf { it.isNotBlank() }
            ?: activity.mediaTitleRomaji
            ?: activity.mediaTitleNative
            ?: activity.mediaTitle
        "NATIVE", "JAPANESE_STAFF" -> activity.mediaTitleNative?.takeIf { it.isNotBlank() }
            ?: activity.mediaTitleRomaji
            ?: activity.mediaTitleEnglish
            ?: activity.mediaTitle
        else -> activity.mediaTitleRomaji
            ?: activity.mediaTitleEnglish
            ?: activity.mediaTitleNative
            ?: activity.mediaTitle
    }
    val displayText = if (activity.mediaTitle != null && localizedTitle != null && localizedTitle != activity.mediaTitle) {
        activity.text.replace("**${activity.mediaTitle}**", localizedTitle)
    } else activity.text

    val cleanText = displayText.replace(Regex("[*_`~]"), "")

    Column(
        modifier = Modifier
            .width(280.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .tvClickable(shape = RoundedCornerShape(16.dp), onClick = onClick)
            .padding(16.dp)
            .height(144.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Centered italicized text body with media cover if present
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = cleanText,
                color = KitsugiColors.TextPrimary,
                fontSize = 13.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (!activity.mediaCoverUrl.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                AsyncImage(
                    model = activity.mediaCoverUrl,
                    contentDescription = activity.mediaTitle,
                    modifier = Modifier
                        .size(width = 36.dp, height = 50.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // Bottom row containing stats & author profile info
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Stats (Likes, Date)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.tvClickable(shape = RoundedCornerShape(8.dp), onClick = onLikeClick)
                ) {
                    Icon(
                        imageVector = if (activity.isLiked) Icons.Favorite else Icons.FavoriteBorder,
                        contentDescription = null,
                        tint = if (activity.isLiked) accentColor else KitsugiColors.TextMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = activity.likeCount.toString(),
                        color = if (activity.isLiked) accentColor else KitsugiColors.TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!activity.dateText.isNullOrBlank()) {
                    Text(
                        text = activity.dateText.take(11),
                        color = KitsugiColors.TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Author Profile Clickable
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = if (onUserProfileClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onUserProfileClick(activity.userId, activity.username, activity.avatarUrl, activity.source) }
                        .padding(4.dp)
                } else Modifier
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(KitsugiColors.SurfaceSoft)
                ) {
                    if (!activity.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = activity.avatarUrl,
                            contentDescription = activity.username,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = activity.username.take(1).uppercase(),
                                color = KitsugiColors.TextMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = activity.username,
                    color = KitsugiColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 80.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                KitsugiPlatformLogo(platformId = activity.source, size = 12.dp)
            }
        }
    }
}
