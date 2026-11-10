package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.utils.KitsugiTranslateUtils
import com.kitsugi.animelist.ui.utils.uniqueItems
import com.kitsugi.animelist.utils.KitsugiTranslatePrefs
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.components.KitsugiButton

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
import com.kitsugi.animelist.data.local.TranslationManager
import com.kitsugi.animelist.data.remote.JikanApiClient
import com.kitsugi.animelist.data.remote.KitsugiForumReply
import com.kitsugi.animelist.data.remote.KitsugiForumTopic
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import com.kitsugi.animelist.utils.KitsugiTranslateUtils.openTranslator

import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitsugiTopicDetailBottomSheet(
    topic: KitsugiForumTopic,
    source: String,
    apiClient: JikanApiClient,
    onUserProfileClick: ((userId: Int?, username: String, avatarUrl: String?, source: String) -> Unit)? = null,
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
    var showReplyEditor by remember { mutableStateOf(false) }
    var replyTargetComment by remember { mutableStateOf<KitsugiForumReply?>(null) }
    var isTopicLikedState by remember(topic.id) { mutableStateOf(topic.isLiked) }
    var topicLikeCountState by remember(topic.id) { mutableStateOf(topic.likeCount) }

    // Translation states
    var selectedLanguage by remember { mutableStateOf("original") }
    var translatedTitle by remember(topic.id) { mutableStateOf<String?>(null) }
    var translatedBody by remember(topic.id) { mutableStateOf<String?>(null) }
    val translatedComments = remember { mutableStateMapOf<Int, String>() }

    /**
     * Süren çeviri işi sayısı. Sıfırdan büyükken konu başlığındaki ve yorumlardaki çeviri
     * butonları galeri butonuyla aynı yüklenme (nabız + dönen halka) animasyonunu gösterir.
     */
    var activeTranslations by remember { mutableStateOf(0) }

    val listState = rememberLazyListState()

    // Load initial comments + enrich MAL OP if needed
    LaunchedEffect(topic.id, topic.source) {
        isLoading = true
        page = 1
        hasMore = true
        activeTopic = topic
        coroutineScope {
            val enrichDef = async { apiClient.enrichForumTopicIfNeeded(topic) }
            val repliesDef = async {
                apiClient.fetchForumTopicReplies(topic.id, page = 1, source = topic.source)
            }
            activeTopic = enrichDef.await()
            commentsList = repliesDef.await()
        }
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
                // T3-05: printStackTrace → Log.e
                android.util.Log.e("TopicDetailSheet", "loadNextPage failed: ${e.message}", e)
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

    LaunchedEffect(topic.id) {
        val autoTranslate = runCatching {
            com.kitsugi.animelist.data.settings.SettingsDataStore(context).settingsFlow.first().autoTranslateEnabled
        }.getOrDefault(false)
        if (autoTranslate) {
            selectedLanguage = "turkish"
        }
    }

    // Translation orchestration
    LaunchedEffect(selectedLanguage, commentsList, activeTopic.body) {
        if (selectedLanguage == "turkish") {
            // Translate topic title
            if (translatedTitle == null && activeTopic.title.isNotBlank()) {
                coroutineScope.launch {
                    activeTranslations++
                    try {
                        val tr = translationManager.translateToTarget(activeTopic.title)
                        if (tr.isNotBlank()) translatedTitle = tr
                    } finally {
                        activeTranslations--
                    }
                }
            }
            // Translate topic OP body
            if (translatedBody == null && activeTopic.body.isNotBlank()) {
                coroutineScope.launch {
                    activeTranslations++
                    try {
                        val tr = translationManager.translateToTarget(activeTopic.body) { partial ->
                            if (partial.isNotBlank()) translatedBody = partial
                        }
                        if (tr.isNotBlank()) translatedBody = tr
                    } finally {
                        activeTranslations--
                    }
                }
            }
            // Recursive translation helper
            fun translateRecursive(comment: KitsugiForumReply) {
                if (!translatedComments.containsKey(comment.id) && comment.comment.isNotBlank()) {
                    coroutineScope.launch {
                        activeTranslations++
                        try {
                            val tr = translationManager.translateToTarget(comment.comment)
                            if (tr.isNotBlank()) {
                                translatedComments[comment.id] = tr
                            }
                        } finally {
                            activeTranslations--
                        }
                    }
                }
                comment.childComments.forEach { child ->
                    translateRecursive(child)
                }
            }
            // Translate comments recursively
            commentsList.forEach { comment ->
                translateRecursive(comment)
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
                    text = KitsugiStrings.get(R.string.inline_topic_details_3d9f851),
                    color = KitsugiColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (isAniListTopic) {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                val success = apiClient.toggleThreadSubscription(activeTopic.id)
                                if (success) {
                                    isSubscribed = !isSubscribed
                                    val msg = if (isSubscribed) "Abone olundu" else "Abonelik iptal edildi"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, KitsugiStrings.get(R.string.islem_basarisiz_lutfen_giris_durumunuzu_kontrol_edin), Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = KitsugiColors.SurfaceStrong
                        )
                    ) {
                        Icon(
                            imageVector = if (isSubscribed) Icons.NotificationsActive else Icons.Notifications,
                            contentDescription = "Abone Ol",
                            tint = if (isSubscribed) accentColor else KitsugiColors.TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Language Switcher Pills + Translate / Copy Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
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

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Konu çevirisi sürerken galeri butonuyla aynı yüklenme animasyonu.
                    KitsugiTranslateIconButton(
                        onClick = {
                            val textToTranslate = if (activeTopic.body.isNotBlank()) {
                                "${activeTopic.title}\n\n${activeTopic.body}"
                            } else {
                                activeTopic.title
                            }
                            context.openTranslator(textToTranslate)
                        },
                        modifier = Modifier.size(36.dp),
                        tint = accentColor,
                        contentDescription = stringResource(R.string.cevir),
                        isLoading = activeTranslations > 0,
                        ringSize = 30.dp
                    )
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clipText = if (activeTopic.body.isNotBlank()) {
                                "${activeTopic.title}\n\n${activeTopic.body}"
                            } else {
                                activeTopic.title
                            }
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("topic_title", clipText))
                            Toast.makeText(context, KitsugiStrings.get(R.string.toast_copied_clipboard), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.ContentCopy,
                            contentDescription = KitsugiStrings.get(R.string.action_copy),
                            tint = KitsugiColors.TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main LazyColumn
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Item 1: Topic title, Author & OP Body Card
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        val displayTitle = if (selectedLanguage == "turkish") translatedTitle ?: activeTopic.title else activeTopic.title
                        Text(
                            text = displayTitle,
                            color = KitsugiColors.TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 26.sp
                        )
                        if (!activeTopic.dateText.isNullOrBlank()) {
                            Text(
                                text = activeTopic.dateText.orEmpty(),
                                color = KitsugiColors.TextSecondary,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = KitsugiColors.Border.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(KitsugiColors.SurfaceSoft)
                                        .then(
                                            if (onUserProfileClick != null) {
                                                Modifier.clickable { onUserProfileClick(activeTopic.userId, activeTopic.username, activeTopic.avatarUrl, activeTopic.source) }
                                            } else Modifier
                                        )
                                ) {
                                    if (!activeTopic.avatarUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = activeTopic.avatarUrl,
                                            contentDescription = activeTopic.username,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text(
                                                text = activeTopic.username.take(1).uppercase(),
                                                color = KitsugiColors.TextMuted,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = activeTopic.username,
                                    color = KitsugiColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = if (onUserProfileClick != null) {
                                        Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable { onUserProfileClick(activeTopic.userId, activeTopic.username, activeTopic.avatarUrl, activeTopic.source) }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    } else Modifier
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                KitsugiPlatformLogo(platformId = activeTopic.source, size = 16.dp)
                            }

                            if (isAniListTopic) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Like/Favorite
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .tvClickable {
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
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isTopicLikedState) Icons.Favorite else Icons.FavoriteBorder,
                                            contentDescription = stringResource(R.string.begen),
                                            tint = if (isTopicLikedState) accentColor else KitsugiColors.TextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = topicLikeCountState.toString(),
                                            color = KitsugiColors.TextSecondary,
                                            fontSize = 14.sp
                                        )
                                    }

                                    // Reply
                                    IconButton(
                                        onClick = { showReplyEditor = true }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Reply,
                                            contentDescription = stringResource(R.string.yanitla),
                                            tint = KitsugiColors.TextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        val displayBody = if (selectedLanguage == "turkish") {
                            translatedBody ?: activeTopic.body
                        } else {
                            activeTopic.body
                        }
                        if (displayBody.isNotBlank()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(KitsugiColors.Surface)
                                    .padding(14.dp)
                            ) {
                                KitsugiMarkdownText(
                                    text = displayBody,
                                    fontSize = 14.sp,
                                    lineHeight = 21.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = KitsugiColors.Border.copy(alpha = 0.3f))
                    }
                }

                // Section Header
                item {
                    val replyTotal = maxOf(activeTopic.commentCount, commentsList.size)
                    Text(
                        text = if (replyTotal > 0) KitsugiStrings.get(R.string.inline_replies_1_s_a790592, replyTotal) else "Yorumlar",
                        color = KitsugiColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                }

                // Loading State
                if (isLoading && commentsList.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                            KitsugiPlasmaLoader(size = 46.dp)
                        }
                    }
                } else if (commentsList.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = KitsugiStrings.get(R.string.inline_no_replies_have_been_posted_7e71b62),
                                color = KitsugiColors.TextMuted,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                } else {
                    // Comments list items
                    uniqueItems(commentsList, key = { it.id }) { comment ->
                        val displayComment = if (selectedLanguage == "turkish") {
                            translatedComments[comment.id] ?: comment.comment
                        } else {
                            comment.comment
                        }
                        KitsugiForumCommentCard(
                            comment = comment,
                            displayText = displayComment,
                            apiClient = apiClient,
                            coroutineScope = coroutineScope,
                            selectedLanguage = selectedLanguage,
                            translatedComments = translatedComments,
                            commentSource = activeTopic.source,
                            isTranslating = activeTranslations > 0,
                            onUserProfileClick = onUserProfileClick,
                            onReplyClick = if (isAniListTopic) {
                                { target ->
                                    replyTargetComment = target
                                    showReplyEditor = true
                                }
                            } else {
                                { }
                            }
                        )
                    }

                    // Load More Indicator
                    if (isLoadingMore) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                KitsugiPlasmaLoader(size = 26.dp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isAniListTopic) {
                KitsugiButton(
                    onClick = { showReplyEditor = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Reply,
                        contentDescription = stringResource(R.string.yanitla),
                        tint = KitsugiColors.Background
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.yanitla), color = KitsugiColors.Background, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showReplyEditor && isAniListTopic) {
        val editorTitle = if (replyTargetComment != null) {
            "${replyTargetComment?.username} Kullanıcısına Yanıt Ver"
        } else {
            "Konuyu Yanıtla"
        }
        val editorPlaceholder = if (replyTargetComment != null) {
            "Yanıtınızı buraya yazın..."
        } else {
            "Mesajınızı buraya yazın..."
        }
        KitsugiReplyEditorBottomSheet(
            title = editorTitle,
            placeholder = editorPlaceholder,
            onPublish = { text ->
                apiClient.postReply(
                    targetId = activeTopic.id,
                    isActivity = false,
                    text = text,
                    parentCommentId = replyTargetComment?.id
                )
            },
            onDismiss = {
                showReplyEditor = false
                replyTargetComment = null
                // Reload comments after posting
                coroutineScope.launch {
                    isLoading = true
                    commentsList = apiClient.fetchForumTopicReplies(
                        activeTopic.id,
                        page = 1,
                        source = activeTopic.source
                    )
                    isLoading = false
                }
            }
        )
    }
}
