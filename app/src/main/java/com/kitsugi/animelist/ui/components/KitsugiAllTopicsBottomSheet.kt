package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.utils.KitsugiTranslateUtils
import com.kitsugi.animelist.ui.utils.uniqueItems
import com.kitsugi.animelist.utils.KitsugiTranslatePrefs
import com.kitsugi.animelist.ui.theme.gradient.Icon
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
import com.kitsugi.animelist.data.remote.KitsugiForumTopic
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
fun KitsugiAllTopicsBottomSheet(
    source: String,
    externalId: Int,
    mediaType: MediaType,
    apiClient: JikanApiClient,
    tmdbId: Int? = null,
    realMalId: Int? = null,
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
    val cachedFeed = remember(socialFeedKey) { SocialFeedCache.allTopics(socialFeedKey) }

    var topicsList by remember { mutableStateOf<List<KitsugiForumTopic>>(cachedFeed?.items ?: emptyList()) }
    var isLoading by remember { mutableStateOf(cachedFeed == null) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(cachedFeed?.page ?: 1) }
    var hasMore by remember { mutableStateOf(cachedFeed?.hasMore ?: true) }

    // Translation & source filter states
    var selectedLanguage by rememberSaveable { mutableStateOf("original") }
    var selectedSourceFilter by rememberSaveable { mutableStateOf("all") }
    val translatedTitles = remember { mutableStateMapOf<Int, String>() }

    /**
     * Süren çeviri işi sayısı. Sıfırdan büyükken listedeki çeviri butonları galeri
     * butonuyla aynı yüklenme (nabız + dönen halka) animasyonunu gösterir.
     */
    var activeTranslations by remember { mutableStateOf(0) }

    val filteredTopics = remember(topicsList, selectedSourceFilter) {
        if (selectedSourceFilter == "all") {
            topicsList
        } else {
            topicsList.filter { it.source.equals(selectedSourceFilter, ignoreCase = true) }
        }
    }

    var activeTopicForDetail by remember { mutableStateOf<KitsugiForumTopic?>(null) }

    val listState = rememberLazyListState()

    // Initial Load — profil ekranına gidilip geri dönüldüğünde liste önbellekten
    // anında gelir ve YENİDEN YÜKLENMEZ ("yorumlar sayfası bozuluyor" hatası).
    LaunchedEffect(source, externalId, mediaType, tmdbId, realMalId) {
        if (topicsList.isNotEmpty()) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        translatedTitles.clear()
        activeTopicForDetail = null
        page = 1
        hasMore = true
        val fresh = apiClient.fetchForumTopics(
            source, externalId, mediaType, page = 1, tmdbId = tmdbId, realMalId = realMalId
        )
        topicsList = fresh
        SocialFeedCache.putAllTopics(socialFeedKey, SocialFeedCache.FeedState(fresh, page, hasMore))
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
                val newTopics = apiClient.fetchForumTopics(
                    source, externalId, mediaType, page = nextPage, tmdbId = tmdbId, realMalId = realMalId
                )
                if (newTopics.isEmpty()) {
                    hasMore = false
                } else {
                    topicsList = (topicsList + newTopics).distinctBy { "${it.source}_${it.id}" }
                    page = nextPage
                }
                SocialFeedCache.putAllTopics(socialFeedKey, SocialFeedCache.FeedState(topicsList, page, hasMore))
            } catch (e: Exception) {
                // T3-05: printStackTrace → Log.e
                android.util.Log.e("AllTopicsSheet", "loadNextPage failed: ${e.message}", e)
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
    LaunchedEffect(selectedLanguage, topicsList) {
        if (selectedLanguage == "turkish") {
            topicsList.forEach { topic ->
                if (!translatedTitles.containsKey(topic.id)) {
                    coroutineScope.launch {
                        activeTranslations++
                        try {
                            val tr = translationManager.translateToTarget(topic.title)
                            if (tr.isNotBlank()) {
                                translatedTitles[topic.id] = tr
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
                    text = stringResource(R.string.tum_tartisma_konulari),
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

            // Source Filter Pills (Tümü / AniList / MAL)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val filterOptions = listOf(
                    "all" to KitsugiStrings.get(R.string.notif_filter_all),
                    "anilist" to "AniList",
                    "jikan" to "MAL"
                )
                filterOptions.forEach { (sourceId, label) ->
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

            Spacer(modifier = Modifier.height(4.dp))

            if (isLoading && topicsList.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    KitsugiPlasmaLoader(size = 46.dp)
                }
            } else if (filteredTopics.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(text = stringResource(R.string.tartisma_konusu_bulunamadi), color = KitsugiColors.TextMuted, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    uniqueItems(filteredTopics, key = { "${it.source}_${it.id}" }) { topic ->
                        val displayTitle = if (selectedLanguage == "turkish") translatedTitles[topic.id] ?: topic.title else topic.title
                        
                        // Theme-aligned Topic Card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(KitsugiColors.SurfaceStrong)
                                .tvClickable(shape = RoundedCornerShape(16.dp)) {
                                    Toast.makeText(context, KitsugiStrings.get(R.string.loading), Toast.LENGTH_SHORT).show()
                                    activeTopicForDetail = topic
                                }
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = displayTitle,
                                    color = KitsugiColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Çeviri sürerken galeri butonuyla aynı yüklenme animasyonu.
                                    KitsugiTranslateIconButton(
                                        onClick = { context.openTranslator(topic.title) },
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
                                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("topic_title", topic.title))
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
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
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

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Text(
                                        text = topic.username,
                                        color = KitsugiColors.TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    KitsugiPlatformLogo(platformId = topic.source, size = 12.dp)
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (topic.source.equals("anilist", ignoreCase = true)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.tvClickable(shape = RoundedCornerShape(8.dp)) {
                                                coroutineScope.launch {
                                                    val success = apiClient.toggleLike(topic.id, "THREAD")
                                                    if (success) {
                                                        topicsList = topicsList.map {
                                                            if (it.id == topic.id) {
                                                                val newLiked = !it.isLiked
                                                                val newCount = if (newLiked) it.likeCount + 1 else it.likeCount - 1
                                                                it.copy(isLiked = newLiked, likeCount = newCount)
                                                            } else {
                                                                it
                                                            }
                                                        }
                                                        SocialFeedCache.putAllTopics(socialFeedKey, SocialFeedCache.FeedState(topicsList, page, hasMore))
                                                    } else {
                                                        Toast.makeText(context, KitsugiStrings.get(R.string.lutfen_once_giris_yapin), Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (topic.isLiked) Icons.Favorite else Icons.FavoriteBorder,
                                                contentDescription = stringResource(R.string.begen),
                                                tint = if (topic.isLiked) accentColor else KitsugiColors.TextSecondary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = topic.likeCount.toString(),
                                                color = KitsugiColors.TextSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Comment,
                                            contentDescription = "Yorumlar",
                                            tint = KitsugiColors.TextSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = topic.commentCount.toString(),
                                            color = KitsugiColors.TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    if (topic.viewCount > 0) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.RemoveRedEye,
                                                contentDescription = stringResource(R.string.goruntulenme),
                                                tint = KitsugiColors.TextSecondary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = topic.viewCount.toString(),
                                                color = KitsugiColors.TextSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
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

    activeTopicForDetail?.let { top ->
        KitsugiTopicDetailBottomSheet(
            topic = top,
            source = source,
            apiClient = apiClient,
            onUserProfileClick = onUserProfileClick,
            onDismiss = { activeTopicForDetail = null }
        )
    }
}
