package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.utils.KitsugiTranslateUtils
import com.kitsugi.animelist.utils.KitsugiTranslatePrefs
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.components.KitsugiButton

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.ui.text.style.TextAlign
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
import com.kitsugi.animelist.data.remote.KitsugiReview
import com.kitsugi.animelist.data.remote.JikanApiClient
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.utils.KitsugiTranslateUtils.openTranslator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitsugiReviewDetailBottomSheet(
    review: KitsugiReview,
    apiClient: JikanApiClient,
    onUserProfileClick: ((userId: Int?, username: String, avatarUrl: String?, source: String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val accentColor = LocalKitsugiAccent.current
    val coroutineScope = rememberCoroutineScope()
    val translationManager = remember { TranslationManager(context) }
    
    var selectedLanguage by remember { mutableStateOf("original") }
    var translatedText by remember { mutableStateOf<String?>(null) }
    var isTranslating by remember { mutableStateOf(false) }
    var activeGalleryImages by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }

    var userRatingState by remember(review.id) { mutableStateOf(review.userRating) }
    var helpfulCountState by remember(review.id) { mutableStateOf(review.helpfulCount ?: 0) }
    var ratingAmountState by remember(review.id) { mutableStateOf(review.ratingAmount ?: 0) }

    val onRateClick: (String) -> Unit = { newRating ->
        if (review.id == null) {
            Toast.makeText(context, KitsugiStrings.get(R.string.begeni_ozelligi_mal_kaynagi_icin_desteklenmemektedir), Toast.LENGTH_SHORT).show()
        } else {
            coroutineScope.launch {
                val success = apiClient.rateReview(review.id, newRating)
                if (success) {
                    val oldRating = userRatingState
                    userRatingState = newRating
                    
                    // Adjust helpfulCountState and ratingAmountState based on transition
                    when {
                        oldRating == "UP_VOTE" && newRating == "NO_RATING" -> {
                            helpfulCountState = (helpfulCountState - 1).coerceAtLeast(0)
                            ratingAmountState = (ratingAmountState - 1).coerceAtLeast(0)
                        }
                        oldRating == "DOWN_VOTE" && newRating == "NO_RATING" -> {
                            ratingAmountState = (ratingAmountState - 1).coerceAtLeast(0)
                        }
                        oldRating == "NO_RATING" && newRating == "UP_VOTE" -> {
                            helpfulCountState = helpfulCountState + 1
                            ratingAmountState = ratingAmountState + 1
                        }
                        oldRating == "NO_RATING" && newRating == "DOWN_VOTE" -> {
                            ratingAmountState = ratingAmountState + 1
                        }
                        oldRating == "UP_VOTE" && newRating == "DOWN_VOTE" -> {
                            helpfulCountState = (helpfulCountState - 1).coerceAtLeast(0)
                        }
                        oldRating == "DOWN_VOTE" && newRating == "UP_VOTE" -> {
                            helpfulCountState = helpfulCountState + 1
                        }
                    }
                } else {
                    Toast.makeText(context, KitsugiStrings.get(R.string.lutfen_once_giris_yapin), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(review.id) {
        val autoTranslate = runCatching {
            com.kitsugi.animelist.data.settings.SettingsDataStore(context).settingsFlow.first().autoTranslateEnabled
        }.getOrDefault(false)
        if (autoTranslate) {
            selectedLanguage = "turkish"
        }
    }

    LaunchedEffect(selectedLanguage, review.fullText) {
        if (selectedLanguage == "turkish" && translatedText == null) {
            isTranslating = true
            try {
                val tr = translationManager.translateToTarget(review.fullText) { partial ->
                    // Kısmi (akış) sonuç geldiğinde metin hemen gösterilir; yüklenme
                    // animasyonu ise çeviri tamamen bitene kadar devam eder.
                    if (partial.isNotBlank()) {
                        translatedText = partial
                    }
                }
                if (tr.isNotBlank()) {
                    translatedText = tr
                }
            } finally {
                isTranslating = false
            }
        }
    }
    val scrollState = rememberScrollState()

    KitsugiSheetOrDialog(
        onDismiss = onDismiss,
        innerColumnScrollState = scrollState
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(KitsugiColors.SurfaceSoft)
                            .then(
                                if (onUserProfileClick != null) {
                                    Modifier.clickable { onUserProfileClick(review.userId, review.username, review.avatarUrl, review.source) }
                                } else Modifier
                            )
                    ) {
                        if (!review.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = review.avatarUrl,
                                contentDescription = review.username,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                  Text(
                                    text = review.username.take(1).uppercase(),
                                    color = KitsugiColors.TextMuted,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = review.username,
                            color = KitsugiColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = if (onUserProfileClick != null) {
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { onUserProfileClick(review.userId, review.username, review.avatarUrl, review.source) }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            } else Modifier
                        )
                        Text(
                            text = review.dateText ?: "",
                            color = KitsugiColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Çeviri sürerken (otomatik ya da elle) galeri butonuyla aynı animasyon.
                    KitsugiTranslateIconButton(
                        onClick = { context.openTranslator(review.fullText, sourceLanguage = review.languageCode) },
                        modifier = Modifier.size(36.dp),
                        tint = accentColor,
                        contentDescription = stringResource(R.string.cevir),
                        isLoading = isTranslating,
                        ringSize = 30.dp
                    )

                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("review_text", review.fullText))
                            Toast.makeText(context, KitsugiStrings.get(R.string.toast_copied_clipboard), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.ContentCopy,
                            contentDescription = stringResource(R.string.action_copy),
                            tint = KitsugiColors.TextSecondary
                        )
                    }

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
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title/Summary
            Text(
                text = review.summary,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                fontSize = 20.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = KitsugiColors.TextPrimary
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = KitsugiColors.Border.copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Language switcher pills
            Row(
                modifier = Modifier.fillMaxWidth(),
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

            Spacer(modifier = Modifier.height(12.dp))

            // Scrollable review text area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp)
                    .verticalScroll(scrollState)
                    .padding(vertical = 4.dp)
            ) {
                if (isTranslating && translatedText == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        KitsugiPlasmaLoader(size = 46.dp)
                    }
                } else {
                    val displayText = if (selectedLanguage == "turkish") (translatedText ?: review.fullText) else review.fullText
                    com.kitsugi.animelist.ui.components.KitsugiHtmlWebView(
                        html = displayText,
                        onImageClick = { urls, idx ->
                            activeGalleryImages = Pair(urls, idx)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Score and ratings
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (review.score != null) {
                    TextSubtitleVertical(
                        text = "${review.score}/100",
                        subtitle = "Skor"
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val acceptancePercent = if (ratingAmountState > 0) {
                        (helpfulCountState * 100) / ratingAmountState
                    } else 0
                    val ratingsStr = if (ratingAmountState > 0) {
                        "$acceptancePercent% ($helpfulCountState/$ratingAmountState)"
                    } else {
                        "$helpfulCountState"
                    }

                    TextSubtitleVertical(
                        text = ratingsStr,
                        subtitle = stringResource(R.string.kullanici_begenileri)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Upvote Button
                        val isUpvote = userRatingState == "UP_VOTE"
                        IconButton(
                            onClick = {
                                val newRating = if (isUpvote) "NO_RATING" else "UP_VOTE"
                                onRateClick(newRating)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.ThumbUp,
                                contentDescription = "Upvote",
                                tint = if (isUpvote) accentColor else KitsugiColors.TextSecondary
                            )
                        }

                        // Downvote Button
                        val isDownvote = userRatingState == "DOWN_VOTE"
                        IconButton(
                            onClick = {
                                val newRating = if (isDownvote) "NO_RATING" else "DOWN_VOTE"
                                onRateClick(newRating)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.ThumbDown,
                                contentDescription = "Downvote",
                                tint = if (isDownvote) KitsugiColors.AccentRed else KitsugiColors.TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Footer Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val displayText = if (selectedLanguage == "turkish") (translatedText ?: review.fullText) else review.fullText
                
                KitsugiButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("review", displayText))
                        Toast.makeText(context, KitsugiStrings.get(R.string.yorum_kopyalandi), Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.ContentCopy,
                        contentDescription = stringResource(R.string.action_copy),
                        tint = KitsugiColors.TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_copy), color = KitsugiColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                KitsugiButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Yorumlayan: ${review.username}\n\n$displayText")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Yorumu Paylaş"))
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Share,
                        contentDescription = stringResource(R.string.paylas),
                        tint = KitsugiColors.Background,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.paylas), color = KitsugiColors.Background, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }

    if (activeGalleryImages != null) {
        val (urls, idx) = activeGalleryImages!!
        KitsugiImageGalleryDialog(
            imageUrls = urls,
            initialIndex = idx,
            title = stringResource(R.string.x_yorum_gorseli, review.username),
            onDismiss = { activeGalleryImages = null }
        )
    }
}

@Composable
private fun TextSubtitleVertical(
    text: String?,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text ?: "Bilinmiyor",
            color = KitsugiColors.TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle,
            color = KitsugiColors.TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp
        )
    }
}
