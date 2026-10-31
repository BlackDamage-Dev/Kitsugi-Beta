package com.kitsugi.animelist.ui.tv.detail

import com.kitsugi.animelist.ui.theme.gradient.Button
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

import com.kitsugi.animelist.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.kitsugi.animelist.data.local.TranslationManager
import com.kitsugi.animelist.data.remote.JikanApiClient
import com.kitsugi.animelist.data.remote.KitsugiActivity
import com.kitsugi.animelist.data.remote.KitsugiActivityReply
import com.kitsugi.animelist.ui.components.KitsugiImageGalleryDialog
import com.kitsugi.animelist.ui.components.KitsugiMarkdownText
import com.kitsugi.animelist.ui.screens.detail.DetailActivityDialogLoadingSkeleton
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.utils.tvClickable
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.kitsugi.animelist.utils.KitsugiStrings

@Composable
fun TvActivityDetailDialog(
    activityId: Int,
    apiClient: JikanApiClient,
    titleLanguage: String = "ROMAJI",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val accentColor = LocalKitsugiAccent.current
    val coroutineScope = rememberCoroutineScope()
    val translationManager = remember { TranslationManager(context) }

    var activityDetails by remember { mutableStateOf<KitsugiActivity?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    var selectedLanguage by remember { mutableStateOf("original") }
    var translatedText by remember { mutableStateOf<String?>(null) }
    val translatedReplies = remember { mutableStateMapOf<Int, String>() }

    LaunchedEffect(activityId) {
        isLoading = true
        val autoTranslate = runCatching {
            com.kitsugi.animelist.data.settings.SettingsDataStore(context).settingsFlow.first().autoTranslateEnabled
        }.getOrDefault(false)
        if (autoTranslate) {
            selectedLanguage = "turkish"
        }
        runCatching {
            activityDetails = apiClient.fetchActivityReplies(activityId)
        }
        isLoading = false
    }

    LaunchedEffect(selectedLanguage, activityDetails) {
        if (selectedLanguage == "turkish" && activityDetails != null) {
            val act = activityDetails!!
            if (act.mediaTitle == null && translatedText == null && act.text.isNotBlank()) {
                coroutineScope.launch {
                    val tr = translationManager.translateToTarget(act.text) { partial ->
                        if (partial.isNotBlank()) translatedText = partial
                    }
                    if (tr.isNotBlank() && tr != act.text.trim()) translatedText = tr
                }
            }
            act.replies.forEach { reply ->
                if (!translatedReplies.containsKey(reply.id) && reply.text.isNotBlank()) {
                    coroutineScope.launch {
                        val tr = translationManager.translateToTarget(reply.text)
                        if (tr.isNotBlank() && tr != reply.text.trim()) {
                            translatedReplies[reply.id] = tr
                        }
                    }
                }
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
                    if (activityDetails != null) {
                        val act = activityDetails!!
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(KitsugiColors.SurfaceSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!act.avatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = act.avatarUrl,
                                        contentDescription = act.username,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = act.username.take(1).uppercase(),
                                        color = accentColor,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = act.username,
                                    color = KitsugiColors.TextPrimary,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!act.dateText.isNullOrBlank()) {
                                    Text(
                                        text = act.dateText,
                                        color = KitsugiColors.TextSecondary,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }

                        Divider(color = KitsugiColors.SurfaceSoft)

                        // Beğeni Butonu
                        var isActLikedState by remember(act.id) { mutableStateOf(act.isLiked) }
                        var actLikesState by remember(act.id) { mutableStateOf(act.likeCount) }
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
                                        val success = apiClient.toggleLike(act.id, "ACTIVITY")
                                        if (success) {
                                            isActLikedState = !isActLikedState
                                            actLikesState = if (isActLikedState) actLikesState + 1 else actLikesState - 1
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
                                    imageVector = if (isActLikedState) Icons.Favorite else Icons.FavoriteBorder,
                                    contentDescription = stringResource(R.string.begen),
                                    tint = if (isActLikedState) accentColor else KitsugiColors.TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.inline_1_s_likes_e6e6053, (actLikesState).toString()),
                                    color = KitsugiColors.TextPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.loading),
                            color = KitsugiColors.TextMuted,
                            style = MaterialTheme.typography.titleMedium
                        )
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
                                text = stringResource(R.string.bangumi_tag_original),
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
                                text = stringResource(R.string.inline_turkish_efaad14),
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
                        Text(stringResource(R.string.action_close), color = KitsugiColors.TextPrimary, fontWeight = FontWeight.Bold)
                    }
                }

                // --- Sağ Panel: Aktivite Metni & Yanıtlar ---
                Box(
                    modifier = Modifier
                        .weight(0.65f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(KitsugiColors.Surface)
                        .padding(12.dp)
                ) {
                    if (isLoading) {
                        DetailActivityDialogLoadingSkeleton()
                    } else if (activityDetails == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.inline_activity_details_couldn_t_be_ea72ca1),
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        val act = activityDetails!!
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            // 1. Aktivite Ana Kartı
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(KitsugiColors.Background, RoundedCornerShape(12.dp))
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        val localizedTitle = when (titleLanguage) {
                                            "ENGLISH" -> act.mediaTitleEnglish?.takeIf { it.isNotBlank() }
                                                ?: act.mediaTitleRomaji ?: act.mediaTitleNative ?: act.mediaTitle
                                            "NATIVE", "JAPANESE_STAFF" -> act.mediaTitleNative?.takeIf { it.isNotBlank() }
                                                ?: act.mediaTitleRomaji ?: act.mediaTitleEnglish ?: act.mediaTitle
                                            else -> act.mediaTitleRomaji ?: act.mediaTitleEnglish ?: act.mediaTitleNative ?: act.mediaTitle
                                        }
                                        val localizedDisplayText = if (act.mediaTitle != null && localizedTitle != null && localizedTitle != act.mediaTitle) {
                                            act.text.replace("**${act.mediaTitle}**", "**$localizedTitle**")
                                        } else act.text
                                        val displayText = if (selectedLanguage == "turkish") translatedText ?: localizedDisplayText else localizedDisplayText

                                        var actGalleryImages by remember { mutableStateOf<List<String>>(emptyList()) }
                                        var actGalleryIndex by remember { mutableStateOf(0) }
                                        KitsugiMarkdownText(
                                            text = displayText,
                                            onImageGalleryRequest = { urls, index ->
                                                actGalleryImages = urls
                                                actGalleryIndex = index
                                            }
                                        )
                                        if (actGalleryImages.isNotEmpty()) {
                                            KitsugiImageGalleryDialog(
                                                imageUrls = actGalleryImages,
                                                initialIndex = actGalleryIndex,
                                                title = act.username,
                                                onDismiss = { actGalleryImages = emptyList() }
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
                            }

                            // 2. Yanıtlar Bölümü Başlığı
                            item {
                                Text(
                                    text = stringResource(R.string.inline_replies_1_s_a790592, (act.replies.size).toString()),
                                    color = KitsugiColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                                )
                            }

                            // 3. Yanıt Kartları
                            if (act.replies.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 20.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(stringResource(R.string.inline_no_replies_yet_b4850fd), color = KitsugiColors.TextMuted, fontSize = 13.sp)
                                    }
                                }
                            } else {
                                items(act.replies, key = { reply -> reply.id }) { reply ->
                                    val displayReplyText = if (selectedLanguage == "turkish") {
                                        translatedReplies[reply.id] ?: reply.text
                                    } else {
                                        reply.text
                                    }

                                    TvActivityReplyCard(
                                        reply = reply,
                                        displayText = displayReplyText,
                                        apiClient = apiClient
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

@Composable
private fun TvActivityReplyCard(
    reply: KitsugiActivityReply,
    displayText: String,
    apiClient: JikanApiClient
) {
    var isFocused by remember { mutableStateOf(false) }
    val accentColor = LocalKitsugiAccent.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var likedState by remember(reply.id) { mutableStateOf(reply.isLiked) }
    var likeCountState by remember(reply.id) { mutableStateOf(reply.likeCount) }

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
                    if (!reply.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = reply.avatarUrl,
                            contentDescription = reply.username,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = reply.username.take(1).uppercase(),
                            color = KitsugiColors.TextMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
                Text(
                    text = reply.username,
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                )
            }

            if (!reply.dateText.isNullOrBlank()) {
                Text(
                    text = reply.dateText,
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        var replyGalleryImages by remember { mutableStateOf<List<String>>(emptyList()) }
        var replyGalleryIndex by remember { mutableStateOf(0) }
        KitsugiMarkdownText(
            text = displayText,
            onImageGalleryRequest = { urls, index ->
                replyGalleryImages = urls
                replyGalleryIndex = index
            }
        )
        if (replyGalleryImages.isNotEmpty()) {
            KitsugiImageGalleryDialog(
                imageUrls = replyGalleryImages,
                initialIndex = replyGalleryIndex,
                title = reply.username,
                onDismiss = { replyGalleryImages = emptyList() }
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
                            val success = apiClient.toggleLike(reply.id, "ACTIVITY_REPLY")
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
