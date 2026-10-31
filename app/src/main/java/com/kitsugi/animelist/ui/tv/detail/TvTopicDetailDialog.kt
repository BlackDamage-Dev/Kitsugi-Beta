package com.kitsugi.animelist.ui.tv.detail

import com.kitsugi.animelist.utils.KitsugiTranslateUtils
import com.kitsugi.animelist.utils.KitsugiTranslatePrefs
import com.kitsugi.animelist.ui.theme.gradient.Button
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

import android.widget.Toast
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusTarget
import coil3.compose.AsyncImage
import com.kitsugi.animelist.data.local.TranslationManager
import com.kitsugi.animelist.data.remote.JikanApiClient
import com.kitsugi.animelist.data.remote.KitsugiForumReply
import com.kitsugi.animelist.data.remote.KitsugiForumTopic
import com.kitsugi.animelist.ui.components.KitsugiImageGalleryDialog
import com.kitsugi.animelist.ui.components.KitsugiMarkdownText
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.screens.detail.DetailTopicDialogLoadingSkeleton
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.utils.tvClickable
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

@Composable
fun TvTopicDetailDialog(
    topic: KitsugiForumTopic,
    source: String,
    apiClient: JikanApiClient,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val accentColor = LocalKitsugiAccent.current
    val coroutineScope = rememberCoroutineScope()
    val translationManager = remember { TranslationManager(context) }

    var activeTopic by remember(topic) { mutableStateOf(topic) }
    val isAniListTopic = activeTopic.source.equals("anilist", ignoreCase = true)

    var commentsList by remember { mutableStateOf<List<KitsugiForumReply>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    var isSubscribed by remember { mutableStateOf(false) }
    var isTopicLikedState by remember(topic.id) { mutableStateOf(topic.isLiked) }
    var topicLikeCountState by remember(topic.id) { mutableStateOf(topic.likeCount) }

    var selectedLanguage by remember { mutableStateOf("original") }
    var translatedTitle by remember(topic.id) { mutableStateOf<String?>(null) }
    var translatedBody by remember(topic.id) { mutableStateOf<String?>(null) }
    val translatedComments = remember { mutableStateMapOf<Int, String>() }

    // Yorumları yükle + MAL OP zenginleştirmesi
    LaunchedEffect(topic.id, topic.source) {
        isLoading = true
        page = 1
        hasMore = true
        activeTopic = topic
        runCatching {
            coroutineScope {
                val enrichDef = async { apiClient.enrichForumTopicIfNeeded(topic) }
                val repliesDef = async {
                    apiClient.fetchForumTopicReplies(topic.id, page = 1, source = topic.source)
                }
                activeTopic = enrichDef.await()
                commentsList = repliesDef.await()
            }
        }
        isLoading = false
    }

    LaunchedEffect(topic.id) {
        val autoTranslate = runCatching {
            com.kitsugi.animelist.data.settings.SettingsDataStore(context).settingsFlow.first().autoTranslateEnabled
        }.getOrDefault(false)
        if (autoTranslate) {
            selectedLanguage = "turkish"
        }
    }

    // Çeviri işleme
    LaunchedEffect(selectedLanguage, commentsList, activeTopic.body) {
        if (selectedLanguage == "turkish") {
            if (translatedTitle == null && activeTopic.title.isNotBlank()) {
                coroutineScope.launch {
                    val tr = translationManager.translateToTarget(activeTopic.title)
                    if (tr.isNotBlank() && tr != activeTopic.title.trim()) translatedTitle = tr
                }
            }
            if (translatedBody == null && activeTopic.body.isNotBlank()) {
                coroutineScope.launch {
                    val tr = translationManager.translateToTarget(activeTopic.body) { partial ->
                        if (partial.isNotBlank()) translatedBody = partial
                    }
                    if (tr.isNotBlank() && tr != activeTopic.body.trim()) translatedBody = tr
                }
            }
            fun translateRecursive(comment: KitsugiForumReply) {
                if (!translatedComments.containsKey(comment.id) && comment.comment.isNotBlank()) {
                    coroutineScope.launch {
                        val tr = translationManager.translateToTarget(comment.comment)
                        if (tr.isNotBlank() && tr != comment.comment.trim()) {
                            translatedComments[comment.id] = tr
                        }
                    }
                }
                comment.childComments.forEach { child ->
                    translateRecursive(child)
                }
            }
            commentsList.forEach { comment ->
                translateRecursive(comment)
            }
        }
    }

    fun loadNextPage() {
        if (isLoading || isLoadingMore || !hasMore) return
        isLoadingMore = true
        coroutineScope.launch {
            try {
                val nextPage = page + 1
                val newComments = apiClient.fetchForumTopicReplies(
                    activeTopic.id,
                    page = nextPage,
                    source = activeTopic.source
                )
                if (newComments.isEmpty()) {
                    hasMore = false
                } else {
                    commentsList = (commentsList + newComments).distinctBy { it.id }
                    page = nextPage
                }
            } catch (e: Exception) {
                android.util.Log.e("TvReviewsTabContent", "loadNextPage failed: ${e.message}", e)
            } finally {
                isLoadingMore = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(20.dp))
                .background(KitsugiColors.Background)
                .border(1.dp, KitsugiColors.SurfaceSoft, RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // --- Sol Panel: Metadata & Aksiyonlar ---
                Column(
                    modifier = Modifier
                        .weight(0.35f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val displayTitle = if (selectedLanguage == "turkish") translatedTitle ?: activeTopic.title else activeTopic.title
                    Text(
                        text = displayTitle,
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(KitsugiColors.SurfaceSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!activeTopic.avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = activeTopic.avatarUrl,
                                    contentDescription = activeTopic.username,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    text = activeTopic.username.take(1).uppercase(),
                                    color = KitsugiColors.TextMuted,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Column {
                            Text(
                                text = activeTopic.username,
                                color = KitsugiColors.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (!activeTopic.dateText.isNullOrBlank()) {
                                Text(
                                    text = activeTopic.dateText.orEmpty(),
                                    color = KitsugiColors.TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Divider(color = KitsugiColors.SurfaceSoft)

                    if (isAniListTopic) {
                        // Beğeni Butonu
                        var isLikeFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isLikeFocused) Color.White.copy(alpha = 0.1f) else KitsugiColors.Surface)
                                .border(
                                    width = if (isLikeFocused) 1.5.dp else 0.dp,
                                    color = if (isLikeFocused) Color.White else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .onFocusChanged { isLikeFocused = it.isFocused }
                                .tvClickable(shape = RoundedCornerShape(12.dp)) {
                                    coroutineScope.launch {
                                        val success = apiClient.toggleLike(activeTopic.id, "THREAD")
                                        if (success) {
                                            isTopicLikedState = !isTopicLikedState
                                            topicLikeCountState = if (isTopicLikedState) topicLikeCountState + 1 else topicLikeCountState - 1
                                        } else {
                                            Toast.makeText(context, KitsugiStrings.get(R.string.lutfen_once_giris_yapin), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = if (isTopicLikedState) Icons.Favorite else Icons.FavoriteBorder,
                                    contentDescription = stringResource(R.string.begen),
                                    tint = if (isTopicLikedState) accentColor else KitsugiColors.TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = KitsugiStrings.get(R.string.inline_1_s_likes_e6e6053, topicLikeCountState),
                                    color = KitsugiColors.TextPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Abone Ol Butonu
                        var isSubFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSubFocused) Color.White.copy(alpha = 0.1f) else KitsugiColors.Surface)
                                .border(
                                    width = if (isSubFocused) 1.5.dp else 0.dp,
                                    color = if (isSubFocused) Color.White else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .onFocusChanged { isSubFocused = it.isFocused }
                                .tvClickable(shape = RoundedCornerShape(12.dp)) {
                                    coroutineScope.launch {
                                        val success = apiClient.toggleThreadSubscription(activeTopic.id)
                                        if (success) {
                                            isSubscribed = !isSubscribed
                                            val msg = if (isSubscribed) "Abone olundu" else "Abonelik iptal edildi"
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, KitsugiStrings.get(R.string.islem_basarisiz_lutfen_giris_yapin), Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = if (isSubscribed) Icons.NotificationsActive else Icons.Notifications,
                                    contentDescription = "Abone Ol",
                                    tint = if (isSubscribed) accentColor else KitsugiColors.TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isSubscribed) "Abone Olundu" else "Abone Ol",
                                    color = KitsugiColors.TextPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Dil Seçici
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var isOrigFocused by remember { mutableStateOf(false) }
                        val isOrigSelected = selectedLanguage == "original"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isOrigSelected) accentColor else KitsugiColors.Surface)
                                .border(
                                    width = if (isOrigFocused) 1.5.dp else 0.dp,
                                    color = if (isOrigFocused) Color.White else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .onFocusChanged { isOrigFocused = it.isFocused }
                                .tvClickable(shape = RoundedCornerShape(10.dp)) { selectedLanguage = "original" }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Orijinal",
                                color = if (isOrigSelected) KitsugiColors.Background else KitsugiColors.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        var isTrFocused by remember { mutableStateOf(false) }
                        val isTrSelected = selectedLanguage == "turkish"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isTrSelected) accentColor else KitsugiColors.Surface)
                                .border(
                                    width = if (isTrFocused) 1.5.dp else 0.dp,
                                    color = if (isTrFocused) Color.White else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .onFocusChanged { isTrFocused = it.isFocused }
                                .tvClickable(shape = RoundedCornerShape(10.dp)) { selectedLanguage = "turkish" }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = KitsugiTranslateUtils.targetLanguageName(KitsugiTranslatePrefs.resolvedTarget()),
                                color = if (isTrSelected) KitsugiColors.Background else KitsugiColors.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Kapat
                    var isCloseFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = KitsugiColors.SurfaceStrong),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isCloseFocused = it.isFocused }
                            .border(
                                width = if (isCloseFocused) 1.5.dp else 0.dp,
                                color = if (isCloseFocused) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.kapat), color = KitsugiColors.TextPrimary, fontWeight = FontWeight.Bold)
                    }
                }

                // --- Sağ Panel: Scrollable Yorum Listesi ---
                Box(
                    modifier = Modifier
                        .weight(0.65f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(KitsugiColors.Surface)
                        .padding(12.dp)
                ) {
                    val displayBody = if (selectedLanguage == "turkish") {
                        translatedBody ?: activeTopic.body
                    } else {
                        activeTopic.body
                    }
                    if (isLoading && commentsList.isEmpty() && displayBody.isBlank()) {
                        DetailTopicDialogLoadingSkeleton()
                    } else if (commentsList.isEmpty() && displayBody.isBlank()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = KitsugiStrings.get(R.string.inline_no_comments_found_or_this_bf2d9d6),
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            if (displayBody.isNotBlank()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(KitsugiColors.Background)
                                            .padding(12.dp)
                                    ) {
                                        KitsugiMarkdownText(
                                            text = displayBody,
                                            fontSize = 14.sp,
                                            lineHeight = 21.sp
                                        )
                                    }
                                }
                            }
                            items(commentsList, key = { it.id }) { comment ->
                                val displayCommentText = if (selectedLanguage == "turkish") {
                                    translatedComments[comment.id] ?: comment.comment
                                } else {
                                    comment.comment
                                }

                                TvForumCommentCard(
                                    comment = comment,
                                    displayText = displayCommentText,
                                    apiClient = apiClient
                                )
                            }

                            if (hasMore) {
                                item {
                                    var isLoadMoreFocused by remember { mutableStateOf(false) }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isLoadMoreFocused) Color.White.copy(alpha = 0.1f) else KitsugiColors.SurfaceStrong)
                                            .border(
                                                width = if (isLoadMoreFocused) 1.5.dp else 0.dp,
                                                color = if (isLoadMoreFocused) Color.White else Color.Transparent,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .onFocusChanged { isLoadMoreFocused = it.isFocused }
                                            .tvClickable(shape = RoundedCornerShape(10.dp)) {
                                                loadNextPage()
                                            }
                                            .padding(12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isLoadingMore) {
                                            KitsugiPlasmaLoader(size = 22.dp)
                                        } else {
                                            Text(
                                                text = stringResource(R.string.daha_fazla_yukle),
                                                color = KitsugiColors.TextPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvForumCommentCard(
    comment: KitsugiForumReply,
    displayText: String,
    apiClient: JikanApiClient
) {
    var isFocused by remember { mutableStateOf(false) }
    val accentColor = LocalKitsugiAccent.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var likedState by remember(comment.id) { mutableStateOf(comment.isLiked) }
    var likeCountState by remember(comment.id) { mutableStateOf(comment.likeCount) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(KitsugiColors.Background)
            .border(
                width = 1.dp,
                color = if (isFocused) Color.White else KitsugiColors.SurfaceSoft,
                shape = RoundedCornerShape(10.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusTarget()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(KitsugiColors.SurfaceSoft),
                    contentAlignment = Alignment.Center
                ) {
                    if (!comment.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = comment.avatarUrl,
                            contentDescription = comment.username,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = comment.username.take(1).uppercase(),
                            color = KitsugiColors.TextMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
                Text(
                    text = comment.username,
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                )
            }

            if (!comment.dateText.isNullOrBlank()) {
                Text(
                    text = comment.dateText,
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        var commentGalleryImages by remember { mutableStateOf<List<String>>(emptyList()) }
        var commentGalleryIndex by remember { mutableStateOf(0) }
        KitsugiMarkdownText(
            text = displayText,
            onImageGalleryRequest = { urls, index ->
                commentGalleryImages = urls
                commentGalleryIndex = index
            }
        )
        if (commentGalleryImages.isNotEmpty()) {
            KitsugiImageGalleryDialog(
                imageUrls = commentGalleryImages,
                initialIndex = commentGalleryIndex,
                title = comment.username,
                onDismiss = { commentGalleryImages = emptyList() }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            var isLikeBtnFocused by remember { mutableStateOf(false) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isLikeBtnFocused) Color.White.copy(alpha = 0.1f) else Color.Transparent)
                    .onFocusChanged { isLikeBtnFocused = it.isFocused }
                    .tvClickable(shape = RoundedCornerShape(6.dp)) {
                        coroutineScope.launch {
                            val success = apiClient.toggleLike(comment.id, "THREAD_REPLY")
                            if (success) {
                                likedState = !likedState
                                likeCountState = if (likedState) likeCountState + 1 else likeCountState - 1
                            } else {
                                Toast.makeText(context, KitsugiStrings.get(R.string.lutfen_once_giris_yapin), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = if (likedState) Icons.Favorite else Icons.FavoriteBorder,
                    contentDescription = stringResource(R.string.begen),
                    tint = if (likedState) accentColor else KitsugiColors.TextMuted,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = likeCountState.toString(),
                    color = if (likedState) accentColor else KitsugiColors.TextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}
