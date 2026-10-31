package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.utils.KitsugiTranslateUtils
import com.kitsugi.animelist.utils.KitsugiTranslatePrefs
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

import android.content.Context
import android.widget.Toast
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.kitsugi.animelist.data.local.TranslationManager
import com.kitsugi.animelist.data.remote.JikanApiClient
import com.kitsugi.animelist.data.remote.KitsugiReview
import com.kitsugi.animelist.data.remote.SocialFeedCache
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogo
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.utils.KitsugiTranslateUtils.openTranslator
import kotlinx.coroutines.launch

import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitsugiAllReviewsBottomSheet(
    source: String,
    externalId: Int,
    mediaType: MediaType,
    apiClient: JikanApiClient,
    tmdbId: Int? = null,
    realMalId: Int? = null,
    initialReviews: List<KitsugiReview> = emptyList(),
    onUserProfileClick: ((userId: Int?, username: String, avatarUrl: String?, source: String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val accentColor = LocalKitsugiAccent.current
    val coroutineScope = rememberCoroutineScope()
    val translationManager = remember { TranslationManager(context) }

    val socialFeedKey = remember(source, externalId, mediaType, tmdbId, realMalId) {
        SocialFeedCache.feedKey(source, externalId, mediaType, tmdbId, realMalId)
    }
    val cachedFeed = remember(socialFeedKey) { SocialFeedCache.allReviews(socialFeedKey) }

    // Seed the sheet from the reviews already rendered in the detail tab. This avoids
    // showing an empty sheet when the full-feed request uses a different ID/source alias.
    var reviewsList by remember(socialFeedKey, initialReviews) {
        mutableStateOf(cachedFeed?.items?.takeIf { it.isNotEmpty() } ?: initialReviews)
    }
    var isLoading by remember(socialFeedKey, initialReviews) {
        mutableStateOf(cachedFeed?.items.isNullOrEmpty() && initialReviews.isEmpty())
    }
    var isLoadingMore by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(cachedFeed?.page ?: 1) }
    var hasMore by remember { mutableStateOf(cachedFeed?.hasMore ?: true) }

    // Translation & source filter states
    var selectedLanguage by rememberSaveable { mutableStateOf("original") }
    var selectedSourceFilter by rememberSaveable { mutableStateOf("all") }
    val translatedSummaries = remember { mutableStateMapOf<String, String>() }

    /**
     * Süren çeviri işi sayısı. Sıfırdan büyükken listedeki çeviri butonları galeri
     * butonuyla aynı yüklenme (nabız + dönen halka) animasyonunu gösterir.
     */
    var activeTranslations by remember { mutableStateOf(0) }

    val filteredReviews = remember(reviewsList, selectedSourceFilter) {
        if (selectedSourceFilter == "all") {
            reviewsList
        } else {
            reviewsList.filter { it.source.equals(selectedSourceFilter, ignoreCase = true) }
        }
    }

    var activeReviewForDetail by remember { mutableStateOf<KitsugiReview?>(null) }

    val listState = rememberLazyListState()

    // Initial Load — profil ekranından geri dönüşte liste önbellekten gelir, yeniden yüklenmez.
    LaunchedEffect(source, externalId, mediaType, tmdbId, realMalId) {
        if (reviewsList.isNotEmpty()) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        translatedSummaries.clear()
        activeReviewForDetail = null
        page = 1
        hasMore = true
        val fresh = apiClient.fetchReviews(
            source, externalId, mediaType, page = 1, tmdbId = tmdbId, realMalId = realMalId
        )
        reviewsList = fresh
        SocialFeedCache.putAllReviews(socialFeedKey, SocialFeedCache.FeedState(fresh, page, hasMore))
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
                val newReviews = apiClient.fetchReviews(
                    source, externalId, mediaType, page = nextPage, tmdbId = tmdbId, realMalId = realMalId
                )
                if (newReviews.isEmpty()) {
                    hasMore = false
                } else {
                    reviewsList = (reviewsList + newReviews).distinctBy { it.username + "_" + it.summary }
                    page = nextPage
                }
                SocialFeedCache.putAllReviews(socialFeedKey, SocialFeedCache.FeedState(reviewsList, page, hasMore))
            } catch (e: Exception) {
                // T3-05: printStackTrace → Log.e
                android.util.Log.e("AllReviewsSheet", "loadNextPage failed: ${e.message}", e)
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

    // Translation orchestration
    LaunchedEffect(selectedLanguage, reviewsList) {
        if (selectedLanguage == "turkish") {
            reviewsList.forEach { rev ->
                val revKey = "${rev.source}_${rev.username}_${rev.dateText}"
                if (!translatedSummaries.containsKey(revKey)) {
                    coroutineScope.launch {
                        activeTranslations++
                        try {
                            val tr = translationManager.translateToTarget(rev.summary)
                            if (tr.isNotBlank()) {
                                translatedSummaries[revKey] = tr
                            }
                        } finally {
                            activeTranslations--
                        }
                    }
                }
            }
        }
    }

    KitsugiSheetOrDialog(
        onDismiss = onDismiss
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
                    onClick = onDismiss,
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
                    text = stringResource(R.string.tum_incelemeler),
                    color = KitsugiColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Language Switcher Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isOrigSelected = selectedLanguage == "original"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isOrigSelected) accentColor else KitsugiColors.SurfaceStrong)
                        .tvClickable(shape = RoundedCornerShape(12.dp)) { selectedLanguage = "original" }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Orijinal",
                        color = if (isOrigSelected) KitsugiColors.Background else KitsugiColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                val isTrSelected = selectedLanguage == "turkish"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isTrSelected) accentColor else KitsugiColors.SurfaceStrong)
                        .tvClickable(shape = RoundedCornerShape(12.dp)) { selectedLanguage = "turkish" }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = KitsugiTranslateUtils.targetLanguageName(KitsugiTranslatePrefs.resolvedTarget()),
                        color = if (isTrSelected) KitsugiColors.Background else KitsugiColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Source Filter Pills (Tümü / AniList / MAL / Kitsu / TMDB / Shikimori / Bangumi)
            val availableSources = remember(reviewsList) {
                reviewsList.map { it.source.lowercase() }.distinct()
            }
            if (availableSources.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val allOptions = listOf(
                        "all" to KitsugiStrings.get(R.string.notif_filter_all),
                        "anilist" to "AniList",
                        "jikan" to "MAL",
                        "kitsu" to "Kitsu",
                        "tmdb" to "TMDB",
                        "shikimori" to "Shikimori",
                        "bangumi" to "Bangumi"
                    ).filter { (id, _) -> id == "all" || id in availableSources }

                    allOptions.forEach { (sourceId, label) ->
                        val isSelected = selectedSourceFilter == sourceId
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) accentColor.copy(alpha = 0.22f) else KitsugiColors.SurfaceStrong)
                                .tvClickable(shape = RoundedCornerShape(10.dp)) { selectedSourceFilter = sourceId }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (sourceId != "all") {
                                KitsugiPlatformLogo(platformId = sourceId, size = 12.dp)
                            }
                            Text(
                                text = label,
                                color = if (isSelected) accentColor else KitsugiColors.TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (isLoading && reviewsList.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    KitsugiPlasmaLoader(size = 46.dp)
                }
            } else if (filteredReviews.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(text = stringResource(R.string.inceleme_bulunamadi), color = KitsugiColors.TextMuted, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredReviews.size) { index ->
                        val rev = filteredReviews[index]
                        val revKey = "${rev.source}_${rev.username}_${rev.dateText}"
                        val displaySummary = if (selectedLanguage == "turkish") translatedSummaries[revKey] ?: rev.summary else rev.summary
                        
                        // Theme-aligned Review Card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(KitsugiColors.SurfaceStrong)
                                .tvClickable(shape = RoundedCornerShape(16.dp)) {
                                    Toast.makeText(context, KitsugiStrings.get(R.string.loading), Toast.LENGTH_SHORT).show()
                                    activeReviewForDetail = rev
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
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onUserProfileClick(rev.userId, rev.username, rev.avatarUrl, rev.source) }
                                            .padding(4.dp)
                                    } else Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(KitsugiColors.SurfaceSoft)
                                    ) {
                                        if (!rev.avatarUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = rev.avatarUrl,
                                                contentDescription = rev.username,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = rev.username.take(1).uppercase(),
                                                    color = KitsugiColors.TextMuted,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Column {
                                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                            Text(
                                                text = rev.username,
                                                color = KitsugiColors.TextPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            KitsugiPlatformLogo(platformId = rev.source, size = 12.dp)
                                        }
                                        if (!rev.dateText.isNullOrBlank()) {
                                            Text(
                                                text = rev.dateText,
                                                color = KitsugiColors.TextSecondary,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }

                                if (rev.score != null) {
                                    KitsugiEmojiLabel(
                                        label = "${rev.score}",
                                        color = accentColor,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        iconSize = 11.dp,
                                        icon = Icons.StarSolid
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = displaySummary,
                                color = KitsugiColors.TextPrimary,
                                fontSize = 13.sp,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )

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
                                        // Çeviri sürerken galeri butonuyla aynı yüklenme animasyonu.
                                        KitsugiTranslateIconButton(
                                            onClick = { context.openTranslator(rev.summary, sourceLanguage = rev.languageCode) },
                                            modifier = Modifier.size(28.dp),
                                            tint = accentColor,
                                            contentDescription = stringResource(R.string.cevir),
                                            iconSize = 14.dp,
                                            isLoading = activeTranslations > 0,
                                            ringSize = 24.dp
                                        )
                                        IconButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("review_summary", rev.summary))
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
                                            if (rev.source != "anilist" || rev.id == null) {
                                                Toast.makeText(context, KitsugiStrings.get(R.string.begeni_sadece_anilist_incelemelerinde_desteklenir), Toast.LENGTH_SHORT).show()
                                                return@tvClickable
                                            }
                                            coroutineScope.launch {
                                                val currentRating = rev.userRating
                                                val newRating = if (currentRating == "UP_VOTE") "NO_RATING" else "UP_VOTE"
                                                val success = apiClient.rateReview(rev.id ?: 0, newRating)
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
                                                    SocialFeedCache.putAllReviews(socialFeedKey, SocialFeedCache.FeedState(reviewsList, page, hasMore))
                                                } else {
                                                    Toast.makeText(context, KitsugiStrings.get(R.string.lutfen_once_giris_yapin), Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.ThumbUp,
                                            contentDescription = stringResource(R.string.faydali),
                                            tint = if (rev.userRating == "UP_VOTE") accentColor else KitsugiColors.TextSecondary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.rev_helpfulcount_0_kisi_oyladi, rev.helpfulCount ?: 0),
                                            color = if (rev.userRating == "UP_VOTE") accentColor else KitsugiColors.TextSecondary,
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

    activeReviewForDetail?.let { rev ->
        KitsugiReviewDetailBottomSheet(
            review = rev,
            apiClient = apiClient,
            onUserProfileClick = onUserProfileClick,
            onDismiss = { activeReviewForDetail = null }
        )
    }
}
