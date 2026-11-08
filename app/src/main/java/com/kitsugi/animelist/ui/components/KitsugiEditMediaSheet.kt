package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.ui.utils.localizedLabel
import android.app.DatePickerDialog
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.utils.kitsugiVerticalScroll
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.HorizontalDivider
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kitsugi.animelist.data.auth.AniListSyncManager
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import kotlin.math.roundToInt
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.EntryAutoRules
import com.kitsugi.animelist.model.WatchStatus
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

// ---------------------------------------------------------------------------
//  Renk yardımcısı — puan 0-4 kırmızı, 5-6 sarı, 7-10 yeşil
//  Sabit Color kullanılıyor (KitsugiColors @Composable gerektiriyor)
// ---------------------------------------------------------------------------
private fun scoreColor(score: Int): Color = when {
    score <= 0 -> Color(0xFF8899AA)   // muted grey
    score <= 4 -> Color(0xFFFB7185)   // AccentRed
    score <= 6 -> Color(0xFFFB923C)   // AccentOrange
    else       -> Color(0xFF34D399)   // AccentGreen
}

/**
 * Bangumi koleksiyon durumlarının **Bangumi terminolojisiyle** karşılığı.
 *
 * Bangumi koleksiyon türleri: `wish` (izleyeceğim/okuyacağım), `collect`
 * (izledim/okudum), `doing` (izliyorum/okuyorum), `on_hold` (bekletiyorum),
 * `dropped` (bıraktım). Eşleme [com.kitsugi.animelist.data.auth.BangumiSyncManager]
 * ile birebir aynıdır; böylece düzenleyicide görünen etiket ile sunucuya yazılan
 * koleksiyon türü tutarlı kalır.
 */
private fun bangumiStatusLabel(status: WatchStatus, type: MediaType): String {
    val isManga = type == MediaType.Manga
    return when (status) {
        WatchStatus.Watching  -> if (isManga) "Okuyorum" else "İzliyorum"
        WatchStatus.Completed -> if (isManga) "Okudum" else "İzledim"
        WatchStatus.Paused    -> "Bekletiyorum"
        WatchStatus.Dropped   -> "Bıraktım"
        WatchStatus.Planned   -> if (isManga) "Okuyacağım" else "İzleyeceğim"
        WatchStatus.Repeating -> if (isManga) "Tekrar Okuyorum" else "Tekrar İzliyorum"
    }
}

@Composable
fun KitsugiEditMediaSheet(
    initialEntry: MediaEntry?,
    source: String = initialEntry?.source ?: "manual",
    scoreFormat: String = "POINT_10",
    onDismiss: () -> Unit,
    onDeleteClick: (() -> Unit)? = null,
    onConfirm: (
        title: String,
        subtitle: String,
        type: MediaType,
        status: WatchStatus,
        isAdult: Boolean,
        progress: Int,
        total: Int?,
        score: Int?,
        isFavorite: Boolean,
        startDate: String?,
        endDate: String?,
        notes: String?,
        tags: String?,
        priority: Int?,
        isRepeating: Boolean,
        repeatCount: Int,
        repeatValue: Int,
        volumeProgress: Int,
        isPrivate: Boolean,
        isHiddenFromStatusLists: Boolean,
        advancedScores: List<Double>?,
        tmdbWatchlist: Boolean?,
        tmdbRating: Double?
    ) -> Unit
) {
    val isEditing = initialEntry?.id?.let { it > 0 } == true
    val editorEntryKey = remember(initialEntry?.id, initialEntry?.source, initialEntry?.malId, initialEntry?.title, source) {
        initialEntry?.let { "${it.source}:${it.id}:${it.malId}:${it.title}" } ?: "new:$source"
    }
    val accentColor  = LocalKitsugiAccent.current
    val isTmdbAccount = com.kitsugi.animelist.data.auth.isTmdbAccountSource(source) ||
        initialEntry?.source?.let { com.kitsugi.animelist.data.auth.isTmdbAccountSource(it) } == true
    val isKitsu = when (source.lowercase()) {
        "kitsu" -> true
        else -> initialEntry?.source?.lowercase() == "kitsu"
    }
    val isShikimori = when (source.lowercase()) {
        "shikimori" -> true
        else -> initialEntry?.source?.lowercase() == "shikimori"
    }
    val isBangumi = when (source.lowercase()) {
        "bangumi", "bgm", "bgm.tv" -> true
        else -> when (initialEntry?.source?.lowercase()) {
            "bangumi", "bgm", "bgm.tv" -> true
            else -> false
        }
    }
    val isAniList = when (source.lowercase()) {
        "anilist" -> true
        "mal", "jikan", "myanimelist", "kitsu", "shikimori", "simkl", "tmdb", "tmdb_account", "bangumi", "bgm", "bgm.tv" -> false
        else -> !isTmdbAccount && !isKitsu && !isShikimori && !isBangumi && initialEntry?.aniListEntryId != null
    }
    val isMal = when (source.lowercase()) {
        "mal", "jikan", "myanimelist" -> true
        "anilist", "kitsu", "shikimori", "simkl", "tmdb", "tmdb_account", "bangumi", "bgm", "bgm.tv" -> false
        else -> !isTmdbAccount && !isKitsu && !isShikimori && !isBangumi && !isAniList && (initialEntry?.malId != null || initialEntry?.source == "mal")
    }
    val isSimkl = when (source.lowercase()) {
        "simkl", "tmdb" -> true
        "anilist", "mal", "jikan", "myanimelist", "kitsu", "shikimori", "tmdb_account", "bangumi", "bgm", "bgm.tv" -> false
        else -> !isTmdbAccount && !isKitsu && !isShikimori && !isBangumi && !isAniList && !isMal && (initialEntry?.simklId != null || initialEntry?.source == "simkl")
    }
    val isManual     = !isAniList && !isMal && !isSimkl && !isKitsu && !isShikimori && !isBangumi && !isTmdbAccount

    // ── State ──────────────────────────────────────────────────────────────
    // Otomatik tarih/ilerleme kuralı panel açılır açılmaz uygulanır: böylece
    // "İzliyorum" bir kaydın başlangıç tarihi boşsa kullanıcı satırda zaten
    // bugünün tarihini görür. (Eskiden yalnızca Kaydet'e basılınca, sessizce
    // yazılıyordu; kullanıcı neyin gittiğini hiç görmüyordu.)
    val autoOnOpen = remember(editorEntryKey) {
        EntryAutoRules.resolveOnStatusChange(
            status = initialEntry?.status ?: WatchStatus.Planned,
            startDate = initialEntry?.startDate,
            endDate = initialEntry?.endDate,
            progress = initialEntry?.progress ?: 0,
            total = initialEntry?.total,
            // Panel açılışında durum değişmedi: yalnızca EKSİK tarihler doldurulur,
            // kullanıcının ilerlemesine asla dokunulmaz.
            statusChanged = false
        )
    }

    var title      by rememberSaveable(editorEntryKey) { mutableStateOf(initialEntry?.title.orEmpty()) }
    var subtitle   by rememberSaveable(editorEntryKey) { mutableStateOf(initialEntry?.subtitle.orEmpty()) }
    var selectedType by rememberSaveable(editorEntryKey) { mutableStateOf(initialEntry?.type ?: MediaType.Anime) }
    var selectedStatus by rememberSaveable(editorEntryKey) { mutableStateOf(initialEntry?.status ?: WatchStatus.Planned) }
    var isAdult    by rememberSaveable(editorEntryKey) { mutableStateOf(initialEntry?.isAdult ?: false) }
    var progressText by rememberSaveable(editorEntryKey) { mutableStateOf(autoOnOpen.progress.toString()) }
    var totalText  by rememberSaveable(editorEntryKey) { mutableStateOf(initialEntry?.total?.toString().orEmpty()) }

    var scoreRaw   by rememberSaveable(editorEntryKey) {
        val db = initialEntry?.score
        val ui = if (db != null) when (scoreFormat) {
            "POINT_100"      -> (db * 10)
            "POINT_5"        -> (db / 2)
            "POINT_3"        -> when { db >= 8 -> 3; db >= 5 -> 2; else -> 1 }
            else             -> db
        } else 0
        mutableIntStateOf(ui)
    }

    // AniHyou paritesi: 0,5 puanlama adımı seçiliyken kullanılan kayan puan durumu (0-10)
    var scoreHalf by rememberSaveable(editorEntryKey) {
        mutableFloatStateOf((initialEntry?.tmdbRating ?: initialEntry?.score?.toDouble() ?: 0.0).toFloat())
    }

    var isFavorite     by rememberSaveable(editorEntryKey) { mutableStateOf(initialEntry?.isFavorite ?: false) }
    var tmdbWatchlist  by rememberSaveable(editorEntryKey) {
        mutableStateOf(initialEntry?.tmdbWatchlist ?: (initialEntry?.status == WatchStatus.Planned))
    }
    var showTmdbListsDialog by rememberSaveable(editorEntryKey) { mutableStateOf(false) }
    var isTmdbStateLoading by rememberSaveable(editorEntryKey) {
        mutableStateOf(isTmdbAccount && initialEntry?.tmdbId != null)
    }
    var tmdbWatchlistEdited by rememberSaveable(editorEntryKey) { mutableStateOf(false) }
    var tmdbFavoriteEdited by rememberSaveable(editorEntryKey) { mutableStateOf(false) }
    var tmdbRatingEdited by rememberSaveable(editorEntryKey) { mutableStateOf(false) }
    var tmdbStateError by remember(editorEntryKey) { mutableStateOf<String?>(null) }
    var startDateText  by rememberSaveable(editorEntryKey) { mutableStateOf(autoOnOpen.startDate.orEmpty()) }
    var endDateText    by rememberSaveable(editorEntryKey) { mutableStateOf(autoOnOpen.endDate.orEmpty()) }
    var notes          by rememberSaveable(editorEntryKey) { mutableStateOf(initialEntry?.notes.orEmpty()) }
    var tags           by rememberSaveable(editorEntryKey) { mutableStateOf(initialEntry?.tags.orEmpty()) }
    var priority       by rememberSaveable(editorEntryKey) { mutableIntStateOf(initialEntry?.priority ?: 0) }
    var isRepeating    by rememberSaveable(editorEntryKey) { mutableStateOf(initialEntry?.isRepeating ?: false) }
    var repeatCount    by rememberSaveable(editorEntryKey) { mutableIntStateOf(initialEntry?.repeatCount ?: 0) }
    var repeatValue    by rememberSaveable(editorEntryKey) { mutableIntStateOf(initialEntry?.repeatValue ?: 0) }
    var volumeProgress by rememberSaveable(editorEntryKey) { mutableIntStateOf(initialEntry?.volumeProgress ?: 0) }
    var isPrivate      by rememberSaveable(editorEntryKey) { mutableStateOf(initialEntry?.isPrivate ?: false) }
    var isHiddenFromStatusLists by rememberSaveable(editorEntryKey) { mutableStateOf(initialEntry?.isHiddenFromStatusLists ?: false) }
    val scrollState = rememberScrollState()

    // AniList Metadata / Advanced Scoring / Custom Lists
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(editorEntryKey, isTmdbAccount) {
        if (!isTmdbAccount) return@LaunchedEffect
        val tmdbId = initialEntry?.tmdbId?.takeIf { it > 0 }
        val entryType = initialEntry?.type
        val sessionId = com.kitsugi.animelist.data.auth.TmdbAuthStore.sessionId(context)
        val accountId = com.kitsugi.animelist.data.auth.TmdbAuthStore.accountId(context)
        if (tmdbId == null || entryType == null || sessionId.isNullOrBlank() || accountId == null) {
            isTmdbStateLoading = false
            return@LaunchedEffect
        }
        isTmdbStateLoading = true
        tmdbStateError = null
        try {
            val accountState = withContext(Dispatchers.IO) {
                com.kitsugi.animelist.data.remote.TmdbAccountClient.fetchAccountState(
                    sessionId = sessionId,
                    type = entryType,
                    tmdbId = tmdbId
                )
            }
            if (!com.kitsugi.animelist.data.auth.TmdbAuthStore.matchesSession(context, sessionId, accountId)) {
                return@LaunchedEffect
            }
            if (!tmdbWatchlistEdited) tmdbWatchlist = accountState.watchlist
            if (!tmdbFavoriteEdited) isFavorite = accountState.favorite
            if (!tmdbRatingEdited) scoreHalf = accountState.rating?.toFloat() ?: 0f
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            com.kitsugi.animelist.data.platform.PlatformFailureRegistry.reportAndResolve("tmdb", error)
            tmdbStateError = error.message ?: context.getString(R.string.tmdb_editor_account_state_fallback)
        } finally {
            isTmdbStateLoading = false
        }
    }

    val aniListToken = androidx.compose.runtime.remember { ExternalAuthManager.getAniListToken(context) }
    var showCustomListsDialog by androidx.compose.runtime.remember { mutableStateOf(false) }
    var isLoadingMetadata by androidx.compose.runtime.remember { mutableStateOf(false) }
    var viewerCustomLists by androidx.compose.runtime.remember { mutableStateOf<List<String>>(emptyList()) }
    var customListsMap by androidx.compose.runtime.remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var advancedScoresEnabled by androidx.compose.runtime.remember { mutableStateOf(false) }
    var advancedScoresCategories by androidx.compose.runtime.remember { mutableStateOf<List<String>>(emptyList()) }
    var advancedScoresMap by androidx.compose.runtime.remember { mutableStateOf<Map<String, Double>>(emptyMap()) }

    // AniList'e özgü alanlar (özel listeler, detaylı puanlama) yalnızca kaydın GERÇEKTEN
    // bir AniList kimliği varsa yüklenir. Bu koşul eskiden `isAniList` içinde örtük olarak
    // vardı; düzenleyici artık açıldığı SAYFANIN kaynağıyla da çalışabildiği için (ör. bir
    // MAL kaydı AniList sayfasından düzenlenirken) açıkça korunur — aksi hâlde AniList'te
    // kaydı olmayan bir yapım için detaylı puanlama hesaplanıp yanlış puan yazılabilirdi.
    val hasAniListIdentity = initialEntry?.aniListEntryId != null ||
        initialEntry?.source.equals("anilist", ignoreCase = true)

    LaunchedEffect(editorEntryKey, isAniList, hasAniListIdentity, selectedType) {
        if (isAniList && hasAniListIdentity && aniListToken != null) {
            isLoadingMetadata = true
            withContext(Dispatchers.IO) {
                val userId = runCatching { AniListSyncManager.fetchAniListUserId(aniListToken, context) }.getOrNull()
                val viewerOptions = AniListSyncManager.getViewerOptions(aniListToken)
                val isManga = selectedType == MediaType.Manga
                val userLists = AniListSyncManager.getViewerCustomLists(aniListToken, isManga).orEmpty()
                
                var entryCustomLists: Map<String, Boolean>? = null
                var entryAdvancedScores: Map<String, Double>? = null
                
                val entryId = initialEntry?.aniListEntryId
                if (entryId != null && entryId > 0 && userId != null) {
                    val metadata = AniListSyncManager.getMediaListMetadata(aniListToken, entryId, userId)
                    if (metadata != null) {
                        entryCustomLists = metadata.customLists
                        entryAdvancedScores = metadata.advancedScores
                    }
                }
                
                withContext(Dispatchers.Main) {
                    viewerCustomLists = userLists
                    if (entryCustomLists != null) {
                        customListsMap = entryCustomLists
                    } else {
                        customListsMap = userLists.associateWith { false }
                    }
                    
                    if (viewerOptions != null) {
                        val enabled = if (isManga) viewerOptions.mangaEnabled else viewerOptions.animeEnabled
                        val categories = if (isManga) viewerOptions.mangaCategories else viewerOptions.animeCategories
                        advancedScoresEnabled = enabled
                        advancedScoresCategories = categories
                        
                        val newScoresMap = categories.associateWith { 0.0 }.toMutableMap()
                        if (entryAdvancedScores != null) {
                            entryAdvancedScores.forEach { (cat, valScore) ->
                                if (newScoresMap.containsKey(cat)) {
                                    newScoresMap[cat] = valScore
                                }
                            }
                        }
                        advancedScoresMap = newScoresMap
                    }
                    isLoadingMetadata = false
                }
            }
        }
    }

    // ── Computed ───────────────────────────────────────────────────────────
    val parsedProgress = progressText.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val parsedTotal    = totalText.toIntOrNull()?.takeIf { it > 0 }
    
    val activeAdvancedScores = advancedScoresMap.values.filter { it > 0.0 }
    val advancedAverage = if (activeAdvancedScores.isNotEmpty()) activeAdvancedScores.average() else 0.0
    // AniHyou paritesi: "Puanlama adımları" 0,5 ise yıldızlar yerine ±0,5 stepper geçer.
    val scoreStepPref = com.kitsugi.animelist.data.settings.KitsugiContentPrefs.scoreStep
    val halfStepMode = isTmdbAccount || (scoreStepPref == 0.5f && !advancedScoresEnabled &&
        (scoreFormat == "POINT_10" || scoreFormat == "POINT_100" || scoreFormat == "POINT_5"))
    val effectiveScoreRaw = if (advancedScoresEnabled && advancedScoresCategories.isNotEmpty()) {
        advancedAverage.roundToInt()
    } else if (halfStepMode) {
        scoreHalf.roundToInt()
    } else {
        scoreRaw
    }

    val parsedScore: Int? = if (isTmdbAccount) {
        scoreHalf.roundToInt().takeIf { scoreHalf > 0f }
    } else if (effectiveScoreRaw <= 0) null else when (scoreFormat) {
        "POINT_100"      -> (effectiveScoreRaw / 10).coerceIn(0, 10)
        "POINT_10_DECIMAL" -> effectiveScoreRaw.coerceIn(0, 10)
        "POINT_5"        -> (effectiveScoreRaw * 2).coerceIn(0, 10)
        "POINT_3"        -> when (effectiveScoreRaw) { 3 -> 10; 2 -> 5; else -> 1 }
        else             -> effectiveScoreRaw.coerceIn(0, 10)
    }
    val normalizedStart = startDateText.trim().takeIf { it.isValidDateOrBlank() }
    val normalizedEnd   = endDateText.trim().takeIf { it.isValidDateOrBlank() }
    // Bitiş tarihinin başlangıçtan önce seçilmesi. Biçim sabit uzunlukta ve sıfır
    // dolgulu (yyyy-MM-dd) olduğu için dize karşılaştırması kronolojiktir.
    val dateOrderError = !EntryAutoRules.isBlankDate(startDateText) &&
        !EntryAutoRules.isBlankDate(endDateText) &&
        startDateText.isValidDateOrBlank() && endDateText.isValidDateOrBlank() &&
        endDateText < startDateText
    val canSave = !isTmdbStateLoading && (isManual && title.isNotBlank() || !isManual) &&
            startDateText.trim().isValidDateOrBlank() &&
            endDateText.trim().isValidDateOrBlank()

    val scoreMax = when (scoreFormat) { "POINT_100" -> 100; "POINT_5" -> 5; "POINT_3" -> 3; else -> 10 }
    val starCount = when (scoreFormat) { "POINT_100" -> 10; "POINT_5" -> 5; "POINT_3" -> 3; else -> 10 }

    // ── Platform info ──────────────────────────────────────────────────────
    val platformName = when {
        isTmdbAccount -> "TMDB"
        isKitsu     -> "Kitsu"
        isShikimori -> "Shikimori"
        isBangumi   -> "Bangumi"
        isAniList   -> "AniList"
        isMal       -> "MyAnimeList"
        isSimkl     -> "Simkl"
        else        -> "Yerel Kitaplık"
    }
    val platformColor = when {
        isTmdbAccount -> Color(0xFF01B4E4)
        isKitsu     -> Color(0xFFFD755C)
        isShikimori -> Color(0xFF8E44AD)
        isBangumi   -> Color(0xFFF09199)
        isAniList   -> Color(0xFF02A9FF)
        isMal       -> Color(0xFF2E51A2)
        isSimkl     -> Color(0xFFE21926)
        else        -> KitsugiColors.TextMuted
    }

    fun selectDate(current: String, onSelected: (String) -> Unit) {
        val cal = java.util.Calendar.getInstance()
        val p   = current.split("-")
        val y   = p.getOrNull(0)?.toIntOrNull() ?: cal.get(java.util.Calendar.YEAR)
        val m   = p.getOrNull(1)?.toIntOrNull()?.minus(1) ?: cal.get(java.util.Calendar.MONTH)
        val d   = p.getOrNull(2)?.toIntOrNull() ?: cal.get(java.util.Calendar.DAY_OF_MONTH)
        DatePickerDialog(context, { _, yr, mn, dy ->
            onSelected("$yr-${String.format("%02d", mn + 1)}-${String.format("%02d", dy)}")
        }, y, m, d).show()
    }

    // ══════════════════════════════════════════════════════════════════════
    //  UI
    // ══════════════════════════════════════════════════════════════════════
    KitsugiSheetOrDialog(
        onDismiss = onDismiss,
        innerColumnScrollState = scrollState
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // ── Header ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = LocalDismissAnimated.current) {
                    Text(stringResource(R.string.iptal), color = KitsugiColors.TextSecondary, fontWeight = FontWeight.Medium)
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isManual) title.ifBlank { "Yeni Kayıt" } else initialEntry?.title ?: platformName,
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    // Platform badge
                    Box(
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(platformColor.copy(alpha = 0.15f))
                            .border(1.dp, platformColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = platformName,
                            color = platformColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                TextButton(
                    enabled = canSave,
                    onClick = {
                        val advancedScoresList: List<Double>? = if (advancedScoresEnabled && advancedScoresCategories.isNotEmpty()) {
                            advancedScoresCategories.map { advancedScoresMap[it] ?: 0.0 }
                        } else null
                        onConfirm(
                            if (isManual) title.trim() else initialEntry?.title.orEmpty(),
                            if (isManual) subtitle.trim() else initialEntry?.subtitle.orEmpty(),
                            if (isManual) selectedType else initialEntry?.type ?: MediaType.Anime,
                            selectedStatus,
                            if (isManual) isAdult else initialEntry?.isAdult ?: false,
                            parsedProgress, parsedTotal,
                            if (isTmdbAccount && !tmdbRatingEdited) initialEntry?.score else parsedScore,
                            if (isTmdbAccount && !tmdbFavoriteEdited) initialEntry?.isFavorite ?: false else isFavorite,
                            normalizedStart, normalizedEnd,
                            notes.trim().takeIf { it.isNotBlank() },
                            tags.trim().takeIf { it.isNotBlank() },
                            priority, isRepeating, repeatCount, repeatValue, volumeProgress,
                            isPrivate, isHiddenFromStatusLists,
                            advancedScoresList,
                            if (isTmdbAccount && tmdbWatchlistEdited) tmdbWatchlist else initialEntry?.tmdbWatchlist,
                            if (isTmdbAccount && tmdbRatingEdited) scoreHalf.toDouble().takeIf { it > 0.0 } else initialEntry?.tmdbRating
                        )
                    }
                ) {
                    Text(
                        text = if (isAniList) "Kaydet" else "Uygula",
                        color = if (canSave) accentColor else KitsugiColors.TextMuted,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            HorizontalDivider(color = KitsugiColors.Border, modifier = Modifier.padding(horizontal = 16.dp))

            // ── Scrollable body ────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .kitsugiVerticalScroll(scrollState)
                    .padding(bottom = 32.dp)
            ) {

                // ── Manual title fields ────────────────────────────────────
                if (isManual) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        DialogTextField(
                            value = title, onValueChange = { title = it },
                            label = stringResource(R.string.baslik), placeholder = stringResource(R.string.orn_sousou_no_frieren)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        DialogTextField(
                            value = subtitle, onValueChange = { subtitle = it },
                            label = stringResource(R.string.aciklama_turler), placeholder = "Örn: TV, Action"
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // Media type toggle
                    val typeValue = when (selectedType) {
                        MediaType.Anime  -> "Anime"
                        MediaType.Manga  -> "Manga"
                        MediaType.Movie  -> "Film"
                        MediaType.TvShow -> "Dizi"
                    }
                    val typeIcon = when (selectedType) {
                        MediaType.Manga -> Icons.Book
                        else            -> Icons.PlayArrow
                    }
                    SheetRow(
                        icon = typeIcon, label = stringResource(R.string.tur), value = typeValue,
                        onClick = {
                            selectedType = when (selectedType) {
                                MediaType.Anime  -> MediaType.Manga
                                MediaType.Manga  -> MediaType.Movie
                                MediaType.Movie  -> MediaType.TvShow
                                MediaType.TvShow -> MediaType.Anime
                            }
                        }
                    )
                    HorizontalDivider(color = KitsugiColors.Border, modifier = Modifier.padding(horizontal = 16.dp))
                }

                // ── Status Pills ───────────────────────────────────────────
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Bangumi koleksiyon türleri (wish / collect / doing / on_hold / dropped)
                    // Bangumi'nin kendi terimleriyle gösterilir; kullanıcı kaydın hangi
                    // koleksiyona yazılacağını birebir görür (Kitsugi karşılıkları:
                    // İzliyorum=doing, İzledim=collect, İzleyeceğim=wish,
                    // Bekletiyorum=on_hold, Bıraktım=dropped).
                    @Composable
                    fun statusLabelFor(status: WatchStatus): String =
                        if (isBangumi) bangumiStatusLabel(status, selectedType) else status.localizedLabel()
                    // AniList için Repeating ayrı bir status, MAL için flag
                    val statusPills = if (isAniList || isShikimori) {
                        listOf(
                            WatchStatus.Watching  to Icons.PlayArrow,
                            WatchStatus.Completed to Icons.Check,
                            WatchStatus.Paused    to Icons.Pause,
                            WatchStatus.Dropped   to Icons.Delete,
                            WatchStatus.Planned   to Icons.Bookmark,
                            WatchStatus.Repeating to Icons.Repeat
                        )
                    } else {
                        listOf(
                            WatchStatus.Watching  to Icons.PlayArrow,
                            WatchStatus.Completed to Icons.Check,
                            WatchStatus.Paused    to Icons.Pause,
                            WatchStatus.Dropped   to Icons.Delete,
                            WatchStatus.Planned   to Icons.Bookmark
                        )
                    }
                    statusPills.forEach { (status, icon) ->
                        val selected = selectedStatus == status
                        val pillBg by animateColorAsState(
                            targetValue = if (selected) accentColor else KitsugiColors.SurfaceSoft,
                            animationSpec = tween(200), label = "pillBg"
                        )
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(pillBg)
                                .border(
                                    width = 1.dp,
                                    color = if (selected) accentColor else KitsugiColors.Border,
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .tvClickable(shape = RoundedCornerShape(20.dp)) {
                                    if (selectedStatus != status) {
                                        // Durum → tarih/ilerleme otomasyonu ANINDA uygulanır:
                                        // kullanıcı "İzliyorum"a basar basmaz başlangıç tarihi
                                        // bugün olarak dolar ve satırda görünür; isterse "X" ile
                                        // temizleyebilir. Dolu tarihler asla ezilmez.
                                        val auto = EntryAutoRules.resolveOnStatusChange(
                                            status = status,
                                            startDate = startDateText,
                                            endDate = endDateText,
                                            progress = parsedProgress,
                                            total = parsedTotal,
                                            statusChanged = true
                                        )
                                        selectedStatus = status
                                        startDateText = auto.startDate.orEmpty()
                                        endDateText = auto.endDate.orEmpty()
                                        if (auto.progress != parsedProgress) progressText = auto.progress.toString()
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = statusLabelFor(status),
                                tint = if (selected) KitsugiColors.Background else KitsugiColors.TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = statusLabelFor(status),
                                color = if (selected) KitsugiColors.Background else KitsugiColors.TextSecondary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                if (isTmdbAccount) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.tmdb_editor_account),
                        color = platformColor,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    SheetRow(
                        icon = Icons.Rounded.Bookmark,
                        label = stringResource(R.string.tmdb_editor_watchlist),
                        switchChecked = tmdbWatchlist,
                        onSwitchCheckedChange = {
                            tmdbWatchlist = it
                            tmdbWatchlistEdited = true
                        }
                    )
                    SheetRow(
                        icon = Icons.Rounded.Star,
                        label = stringResource(R.string.tmdb_editor_favorite),
                        switchChecked = isFavorite,
                        onSwitchCheckedChange = {
                            isFavorite = it
                            tmdbFavoriteEdited = true
                        }
                    )
                    SheetRow(
                        icon = Icons.Rounded.List,
                        label = stringResource(R.string.tmdb_editor_custom_lists),
                        onClick = if (initialEntry?.type == MediaType.Movie && initialEntry.tmdbId != null) {
                            { showTmdbListsDialog = true }
                        } else null
                    )
                    if (isTmdbStateLoading) {
                        Text(
                            text = stringResource(R.string.tmdb_editor_account_state_loading),
                            color = KitsugiColors.TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    tmdbStateError?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = if (initialEntry?.type == MediaType.Movie) {
                            stringResource(R.string.tmdb_editor_native_movie_description)
                        } else {
                            stringResource(R.string.tmdb_editor_native_tv_description)
                        },
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                // ── Star Rating ────────────────────────────────────────────
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(KitsugiColors.Surface)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val scoreColor by animateColorAsState(
                        targetValue = scoreColor(effectiveScoreRaw),
                        animationSpec = tween(300), label = "scoreColor"
                    )
                    Text(
                        text = if (halfStepMode) {
                            if (scoreHalf <= 0f) "Puanlanmadı" else String.format(java.util.Locale.US, "%.1f / 10", scoreHalf)
                        } else if (effectiveScoreRaw <= 0) {
                            "Puanlanmadı"
                        } else {
                            "$effectiveScoreRaw / $scoreMax" + (if (advancedScoresEnabled) " (Ortalama)" else "")
                        },
                        color = scoreColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (halfStepMode) {
                        // AniHyou paritesi: 0,5 adımlı puan girişi (kaynaklara tam sayı yuvarlanarak yazılır)
                        SheetRow(
                            icon = Icons.Star,
                            label = "Puan (0,5 adım)",
                            value = String.format(java.util.Locale.US, "%.1f", scoreHalf),
                            onDecrement = {
                                scoreHalf = (scoreHalf - 0.5f).coerceAtLeast(0f)
                                if (isTmdbAccount) tmdbRatingEdited = true
                            },
                            onIncrement = {
                                scoreHalf = (scoreHalf + 0.5f).coerceAtMost(10f)
                                if (isTmdbAccount) tmdbRatingEdited = true
                            }
                        )
                    }
                    if (!halfStepMode) Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 1..starCount) {
                            val filled = i <= effectiveScoreRaw
                            val starScale by animateFloatAsState(
                                targetValue = if (filled) 1.15f else 1f,
                                animationSpec = tween(150), label = "starScale$i"
                            )
                            val starModifier = Modifier
                                .size(28.dp)
                                .scale(starScale)
                            
                            val clickableModifier = if (!advancedScoresEnabled) {
                                starModifier.tvClickable(shape = CircleShape) {
                                    scoreRaw = if (scoreRaw == i) 0 else i
                                }
                            } else {
                                starModifier
                            }
                            
                            Icon(
                                imageVector = if (filled) Icons.Star else Icons.StarBorder,
                                contentDescription = "Puan $i",
                                tint = if (filled) scoreColor else KitsugiColors.TextMuted,
                                modifier = clickableModifier
                            )
                        }
                    }
                }

                // ── Advanced Scoring categories ─────────────────────────────
                if (advancedScoresEnabled && advancedScoresCategories.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Detaylı Puanlama",
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    advancedScoresCategories.forEach { category ->
                        val currentVal = advancedScoresMap[category] ?: 0.0
                        
                        // AniHyou paritesi: detaylı kategoriler de "Puanlama adımları" tercihine uyar
                        val (step, maxVal) = when (scoreFormat) {
                            "POINT_100" -> (10.0 * scoreStepPref.toDouble()) to 100.0
                            "POINT_10_DECIMAL" -> 0.1 to 10.0
                            "POINT_5" -> scoreStepPref.toDouble() to 5.0
                            "POINT_3" -> 1.0 to 3.0
                            else -> scoreStepPref.toDouble() to 10.0
                        }
                        
                        val formattedValue = if (scoreFormat == "POINT_10_DECIMAL" || step < 1.0) {
                            String.format(java.util.Locale.US, "%.1f", currentVal)
                        } else {
                            currentVal.toInt().toString()
                        }
                        
                        SheetRow(
                            icon = Icons.Star,
                            label = category,
                            value = formattedValue,
                            onDecrement = {
                                val newVal = (currentVal - step).coerceAtLeast(0.0)
                                advancedScoresMap = advancedScoresMap.toMutableMap().apply { put(category, newVal) }
                            },
                            onIncrement = {
                                val newVal = (currentVal + step).coerceAtMost(maxVal)
                                advancedScoresMap = advancedScoresMap.toMutableMap().apply { put(category, newVal) }
                            }
                        )
                    }
                }

                // ── Progress & Total ───────────────────────────────────────
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = KitsugiColors.Border, modifier = Modifier.padding(horizontal = 16.dp))

                val progressSuffix = if (parsedTotal != null) " / $parsedTotal" else ""
                val progressLabel  = when {
                    // Bangumi'de ilerleme `ep_status` (izlenen/okunan bölüm) alanına yazılır.
                    isBangumi && selectedType == MediaType.Manga -> "Okunan Bölüm"
                    isBangumi && selectedType == MediaType.Movie -> "İlerleme"
                    isBangumi -> "İzlenen Bölüm"
                    selectedType == MediaType.Manga -> "Bölümler (Chapter)"
                    selectedType == MediaType.Movie -> "İlerleme"
                    else -> "Bölümler"
                }
                val progressIcon = if (selectedType == MediaType.Manga) Icons.Book else Icons.PlayArrow

                SheetRow(
                    icon = progressIcon, label = progressLabel,
                    value = progressText,
                    onValueChange = { v ->
                        if (v.isEmpty()) progressText = ""
                        else v.toIntOrNull()?.let { n ->
                            progressText = if (parsedTotal != null) n.coerceAtMost(parsedTotal).toString() else n.toString()
                        }
                    },
                    valuePlaceholder = "0", valueSuffix = progressSuffix,
                    onDecrement = { progressText = ((progressText.toIntOrNull() ?: 0) - 1).coerceAtLeast(0).toString() },
                    onIncrement = {
                        val cur = progressText.toIntOrNull() ?: 0
                        val nxt = cur + 1
                        progressText = if (parsedTotal != null) nxt.coerceAtMost(parsedTotal).toString() else nxt.toString()
                    }
                )

                // Manga volume progress (MAL & manual)
                if (selectedType == MediaType.Manga && !isAniList && !isKitsu) {
                    SheetRow(
                        icon = Icons.Bookmark, label = "Ciltler (Volume)",
                        value = volumeProgress.toString(),
                        onValueChange = { v -> v.toIntOrNull()?.let { volumeProgress = it } },
                        valuePlaceholder = "0",
                        onDecrement = { volumeProgress = (volumeProgress - 1).coerceAtLeast(0) },
                        onIncrement = { volumeProgress++ }
                    )
                }

                // Total episodes (manual only)
                if (isManual) {
                    SheetRow(
                        icon = Icons.FormatListNumbered, label = stringResource(R.string.toplam_sayi),
                        value = totalText,
                        onValueChange = { v ->
                            if (v.isEmpty()) totalText = ""
                            else v.toIntOrNull()?.let { totalText = it.toString() }
                        },
                        valuePlaceholder = "Bilinmiyor",
                        onDecrement = { val c = totalText.toIntOrNull() ?: 0; totalText = if (c > 1) (c - 1).toString() else "" },
                        onIncrement = { totalText = ((totalText.toIntOrNull() ?: 0) + 1).toString() }
                    )
                }

                HorizontalDivider(color = KitsugiColors.Border, modifier = Modifier.padding(horizontal = 16.dp))

                // ── Dates ──────────────────────────────────────────────────
                SheetRow(
                    icon = Icons.CalendarToday, label = stringResource(R.string.baslangic_tarihi),
                    value = startDateText.ifBlank { stringResource(R.string.tarih_secilmemis) },
                    onClick = { selectDate(startDateText) { startDateText = it } },
                    onClearClick = if (startDateText.isNotBlank()) { { startDateText = "" } } else null
                )
                DateQuickChips(
                    onToday = { startDateText = EntryAutoRules.today() },
                    onYesterday = { startDateText = EntryAutoRules.yesterday() },
                    onClear = if (startDateText.isNotBlank()) { { startDateText = "" } } else null
                )
                SheetRow(
                    icon = Icons.EventAvailable, label = stringResource(R.string.bitis_tarihi),
                    value = endDateText.ifBlank { stringResource(R.string.tarih_secilmemis) },
                    onClick = { selectDate(endDateText) { endDateText = it } },
                    onClearClick = if (endDateText.isNotBlank()) { { endDateText = "" } } else null
                )
                DateQuickChips(
                    onToday = { endDateText = EntryAutoRules.today() },
                    onYesterday = { endDateText = EntryAutoRules.yesterday() },
                    onClear = if (endDateText.isNotBlank()) { { endDateText = "" } } else null
                )
                if (dateOrderError) {
                    Text(
                        text = stringResource(R.string.bitis_tarihi_baslangictan_once_uyari),
                        color = KitsugiColors.AccentRed,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }

                HorizontalDivider(color = KitsugiColors.Border, modifier = Modifier.padding(horizontal = 16.dp))

                // ── Platform-specific fields ───────────────────────────────
                // AniList only
                if (isAniList) {
                    if (viewerCustomLists.isNotEmpty()) {
                        val activeLists = customListsMap.filter { it.value }.keys.toList()
                        val listsValue = if (activeLists.isEmpty()) "Hiçbiri" else activeLists.joinToString(", ")
                        SheetRow(
                            icon = Icons.List,
                            label = stringResource(R.string.ozel_listeler),
                            value = listsValue,
                            onClick = { showCustomListsDialog = true }
                        )
                    }
                    SheetRow(
                        icon = Icons.Star, label = "Favori",
                        switchChecked = isFavorite, onSwitchCheckedChange = { isFavorite = it }
                    )
                    // AniList'te Repeating bir status (pill olarak seçilir), isRepeating toggle'ı
                    // sadece MAL içindir. Ancak repeatCount AniList 'repeat' alanına yazılır.
                    if (selectedStatus == WatchStatus.Repeating) {
                        SheetRow(
                            icon = Icons.RepeatOne,
                            label = if (selectedType == MediaType.Manga) "Toplam Tekrar Okuma" else "Toplam Tekrar İzleme",
                            value = repeatCount.toString(),
                            onDecrement = { repeatCount = (repeatCount - 1).coerceAtLeast(0) },
                            onIncrement = { repeatCount++ }
                        )
                    }
                    SheetRow(
                        icon = Icons.Lock, label = "Gizli",
                        switchChecked = isPrivate, onSwitchCheckedChange = { isPrivate = it }
                    )
                    SheetRow(
                        icon = Icons.VisibilityOff, label = stringResource(R.string.durum_listelerinden_gizle),
                        switchChecked = isHiddenFromStatusLists,
                        onSwitchCheckedChange = { isHiddenFromStatusLists = it }
                    )
                }

                // Kitsu: gizlilik (private) — tarih ve not zaten ortak alanlar
                if (isKitsu) {
                    SheetRow(
                        icon = Icons.Lock, label = "Gizli",
                        switchChecked = isPrivate, onSwitchCheckedChange = { isPrivate = it }
                    )
                }

                // Shikimori: "Yeniden İzleniyor" durumunda tekrar sayısı (user_rate.rewatches)
                if (isShikimori && selectedStatus == WatchStatus.Repeating) {
                    SheetRow(
                        icon = Icons.RepeatOne,
                        label = if (selectedType == MediaType.Manga) "Toplam Tekrar Okuma" else "Toplam Tekrar İzleme",
                        value = repeatCount.toString(),
                        onDecrement = { repeatCount = (repeatCount - 1).coerceAtLeast(0) },
                        onIncrement = { repeatCount++ }
                    )
                }

                // Bangumi: koleksiyon hedefi + gizlilik + etiketler
                // (Bangumi koleksiyon API'si gizlilik ve etiketleri destekler)
                if (isBangumi) {
                    // Hangi Bangumi条目'sunun güncelleneceğini gösterir: kayıt Bangumi
                    // stableId'si taşıyorsa (subject_id + 500M) numara verilir; taşımıyorsa
                    // (ör. eşleşen kayıt başka bir sağlayıcıdan geliyorsa) kimliğin başlık
                    // eşleştirmesiyle çözüleceği açıkça yazılır.
                    val bangumiSubjectId = com.kitsugi.animelist.data.remote.BangumiIdNamespace
                        .rawIdFromStable(initialEntry?.malId)
                    SheetRow(
                        icon = Icons.Tag,
                        label = "Bangumi Koleksiyonu",
                        value = bangumiSubjectId?.let { "#$it" } ?: "Başlıkla eşleştirilir"
                    )
                    SheetRow(
                        icon = Icons.Lock, label = "Gizli",
                        switchChecked = isPrivate, onSwitchCheckedChange = { isPrivate = it }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Label,
                            contentDescription = "Etiketler",
                            tint = KitsugiColors.TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        DialogTextField(
                            value = tags, onValueChange = { tags = it },
                            label = "Etiketler", placeholder = stringResource(R.string.virgulle_ayirin_orn_sevilen_bilim_kurgu),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // MAL only
                if (isMal) {
                    SheetRow(
                        icon = Icons.Star, label = "Favori",
                        switchChecked = isFavorite, onSwitchCheckedChange = { isFavorite = it }
                    )
                    val priorityText = when (priority) { 1 -> "Orta"; 2 -> "Yüksek"; else -> "Düşük" }
                    SheetRow(
                        icon = Icons.PriorityHigh, label = stringResource(R.string.oncelik), value = priorityText,
                        onDecrement = { priority = (priority - 1).coerceAtLeast(0) },
                        onIncrement = { priority = (priority + 1).coerceAtMost(2) }
                    )
                    SheetRow(
                        icon = Icons.Repeat,
                        label = if (selectedType == MediaType.Manga) "Yeniden Okunuyor" else "Yeniden İzleniyor",
                        switchChecked = isRepeating, onSwitchCheckedChange = { isRepeating = it }
                    )
                    SheetRow(
                        icon = Icons.RepeatOne,
                        label = if (selectedType == MediaType.Manga) "Toplam Tekrar Okuma" else "Toplam Tekrar İzleme",
                        value = repeatCount.toString(),
                        onDecrement = { repeatCount = (repeatCount - 1).coerceAtLeast(0) },
                        onIncrement = { repeatCount++ }
                    )
                    val repeatValueText = when (repeatValue) {
                        1 -> "Çok Düşük"; 2 -> "Düşük"; 3 -> "Orta"; 4 -> "Yüksek"; 5 -> "Çok Yüksek"; else -> "─"
                    }
                    SheetRow(
                        icon = Icons.DateRange,
                        label = if (selectedType == MediaType.Manga) "Tekrar Okuma Değeri" else "Tekrar İzleme Değeri",
                        value = repeatValueText,
                        onDecrement = { repeatValue = (repeatValue - 1).coerceAtLeast(0) },
                        onIncrement = { repeatValue = (repeatValue + 1).coerceAtMost(5) }
                    )
                    // Tags
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Label,
                            contentDescription = "Etiketler",
                            tint = KitsugiColors.TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        DialogTextField(
                            value = tags, onValueChange = { tags = it },
                            label = "Etiketler", placeholder = stringResource(R.string.virgulle_ayirin_orn_sevilen_aksiyon),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Simkl only
                if (isSimkl) {
                    SheetRow(
                        icon = Icons.Star, label = "Favori",
                        switchChecked = isFavorite, onSwitchCheckedChange = { isFavorite = it }
                    )
                    SheetRow(
                        icon = Icons.Repeat,
                        label = if (selectedType == MediaType.Manga) "Yeniden Okunuyor" else "Yeniden İzleniyor",
                        switchChecked = isRepeating, onSwitchCheckedChange = { isRepeating = it }
                    )
                    if (isRepeating) {
                        SheetRow(
                            icon = Icons.RepeatOne,
                            label = if (selectedType == MediaType.Manga) "Toplam Tekrar Okuma" else "Toplam Tekrar İzleme",
                            value = repeatCount.toString(),
                            onDecrement = { repeatCount = (repeatCount - 1).coerceAtLeast(0) },
                            onIncrement = { repeatCount++ }
                        )
                    }
                    // Etiketler — lokal organizasyon için
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Label,
                            contentDescription = "Etiketler",
                            tint = KitsugiColors.TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        DialogTextField(
                            value = tags, onValueChange = { tags = it },
                            label = "Etiketler", placeholder = stringResource(R.string.virgulle_ayirin_orn_sevilen_bilim_kurgu),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (isManual) {
                    SheetRow(
                        icon = Icons.Star, label = "Favori",
                        switchChecked = isFavorite, onSwitchCheckedChange = { isFavorite = it }
                    )
                    SheetRow(
                        icon = Icons.Lock, label = stringResource(R.string.s18_icerik),
                        switchChecked = isAdult, onSwitchCheckedChange = { isAdult = it }
                    )
                }

                // ── Notes ──────────────────────────────────────────────────
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Notes,
                        contentDescription = "Notlar",
                        tint = KitsugiColors.TextSecondary,
                        modifier = Modifier.size(24.dp).padding(top = 12.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    TextField(
                        value = notes,
                        onValueChange = { notes = it },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        shape = RoundedCornerShape(18.dp),
                        placeholder = { Text(text = stringResource(R.string.kisisel_dusuncelerinizi_yazin), color = KitsugiColors.TextMuted) },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = KitsugiColors.TextPrimary,
                            unfocusedTextColor = KitsugiColors.TextPrimary,
                            focusedContainerColor = KitsugiColors.SurfaceSoft,
                            unfocusedContainerColor = KitsugiColors.SurfaceSoft,
                            focusedIndicatorColor = accentColor,
                            unfocusedIndicatorColor = KitsugiColors.Border,
                            cursorColor = accentColor
                        )
                    )
                }

                // ── Delete ─────────────────────────────────────────────────
                if (isEditing && onDeleteClick != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = KitsugiColors.Border, modifier = Modifier.padding(horizontal = 16.dp))
                    SheetRow(
                        icon = Icons.Delete, label = "Sil",
                        onClick = onDeleteClick,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                // ── Sync footer ────────────────────────────────────────────
                Spacer(modifier = Modifier.height(18.dp))
                // Bu platforma gönderilmeyen alanları açıkça belirt (kullanıcı yanılmasın)
                val unsyncedHint = when {
                    isTmdbAccount -> stringResource(R.string.tmdb_editor_local_fields_note)
                    isShikimori -> "Tarihler ve favori Shikimori'ye gönderilmez; yalnızca Kitsugi'de saklanır."
                    isBangumi   -> "Tarihler ve favori Bangumi'ye gönderilmez; yalnızca Kitsugi'de saklanır."
                    isSimkl     -> "Not, tarih ve favori Simkl'e gönderilmez; yalnızca Kitsugi'de saklanır."
                    isMal       -> "Favori MyAnimeList'e gönderilmez; yalnızca Kitsugi'de saklanır."
                    else        -> ""
                }
                if (unsyncedHint.isNotBlank()) {
                    Text(
                        text = unsyncedHint,
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                val syncNote = when {
                    isTmdbAccount -> stringResource(R.string.tmdb_editor_sync_note)
                    isKitsu     -> "Not: Değişiklikler otomatik olarak Kitsu hesabınızla senkronize edilecektir."
                    isShikimori -> "Not: Değişiklikler otomatik olarak Shikimori hesabınızla senkronize edilecektir."
                    isBangumi   -> "Not: Değişiklikler otomatik olarak Bangumi hesabınızla senkronize edilecektir."
                    isAniList   -> "Not: Değişiklikler otomatik olarak AniList hesabınızla senkronize edilecektir."
                    isMal       -> "Not: Değişiklikler otomatik olarak MyAnimeList hesabınızla senkronize edilecektir."
                    isSimkl     -> "Not: Değişiklikler otomatik olarak Simkl hesabınızla senkronize edilecektir."
                    else        -> ""
                }
                if (syncNote.isNotBlank()) {
                    Text(
                        text = syncNote,
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }

    if (showTmdbListsDialog && initialEntry != null) {
        KitsugiTmdbListsDialog(
            entry = initialEntry,
            onDismiss = { showTmdbListsDialog = false }
        )
    }

    if (showCustomListsDialog && viewerCustomLists.isNotEmpty()) {
        KitsugiCustomListsSelectionDialog(
            viewerCustomLists = viewerCustomLists,
            customListsMap = customListsMap,
            onDismiss = { showCustomListsDialog = false },
            onConfirm = { updatedMap ->
                customListsMap = updatedMap
                showCustomListsDialog = false
                if (aniListToken != null) {
                    coroutineScope.launch(Dispatchers.IO) {
                        val entryId = initialEntry?.aniListEntryId
                        val resolvedMediaId = if (entryId == null || entryId <= 0) {
                            initialEntry?.let { AniListSyncManager.resolveAniListMediaId(aniListToken, it, context) }
                        } else null
                        
                        val activeLists = updatedMap.filter { it.value }.keys.toList()
                        AniListSyncManager.updateEntryCustomLists(
                            token = aniListToken,
                            mediaId = resolvedMediaId,
                            entryId = entryId,
                            customLists = activeLists
                        )
                    }
                }
            }
        )
    }
}

@Composable
fun KitsugiCustomListsSelectionDialog(
    viewerCustomLists: List<String>,
    customListsMap: Map<String, Boolean>,
    onDismiss: () -> Unit,
    onConfirm: (Map<String, Boolean>) -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    var tempMap by androidx.compose.runtime.remember { mutableStateOf(customListsMap) }
    val isTvDevice = com.kitsugi.animelist.ui.theme.LocalIsTvDevice.current

    val dialogContent = @Composable {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.ozel_listeler),
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .kitsugiVerticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                viewerCustomLists.forEach { listName ->
                    val checked = tempMap[listName] ?: false
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (checked) accentColor.copy(alpha = 0.1f) else KitsugiColors.SurfaceSoft)
                            .border(
                                width = 1.dp,
                                color = if (checked) accentColor else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .tvClickable(shape = RoundedCornerShape(12.dp)) {
                                tempMap = tempMap.toMutableMap().apply { put(listName, !checked) }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = listName,
                            color = KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal
                        )
                        if (checked) {
                            Icon(
                                imageVector = Icons.Check,
                                contentDescription = stringResource(R.string.secili),
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.iptal),
                        color = KitsugiColors.TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                TextButton(onClick = { onConfirm(tempMap) }) {
                    Text(
                        text = stringResource(R.string.kaydet),
                        color = accentColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (isTvDevice) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = onDismiss,
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Card(
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = KitsugiColors.Surface
                    ),
                    shape = RoundedCornerShape(28.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, KitsugiColors.Border),
                    modifier = Modifier
                        .width(420.dp)
                        .padding(24.dp)
                ) {
                    dialogContent()
                }
            }
        }
    } else {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = KitsugiColors.Surface,
            titleContentColor = KitsugiColors.TextPrimary,
            textContentColor = KitsugiColors.TextSecondary,
            shape = RoundedCornerShape(26.dp),
            confirmButton = {},
            dismissButton = {},
            text = {
                dialogContent()
            }
        )
    }
}

/**
 * Tarih satırının hemen altındaki kompakt hızlı kısayollar: **Bugün**, **Dün**, **Temizle**.
 *
 * Tarih seçiminin %90'ı "bugün" ya da "dün" olduğu için her seferinde takvim
 * açtırmamak amaçlanıyor. Çipler yalnızca ilgili satırı etkiler; temizleme çipi
 * tarih boşken gösterilmez (sürekli yer kaplayan ölü bir düğme olmasın).
 */
@Composable
private fun DateQuickChips(
    onToday: () -> Unit,
    onYesterday: () -> Unit,
    onClear: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DateQuickChip(text = stringResource(R.string.date_chip_today), onClick = onToday)
        DateQuickChip(text = stringResource(R.string.date_chip_yesterday), onClick = onYesterday)
        if (onClear != null) {
            DateQuickChip(text = stringResource(R.string.temizle), onClick = onClear)
        }
    }
}

@Composable
private fun DateQuickChip(text: String, onClick: () -> Unit) {
    val accentColor = LocalKitsugiAccent.current
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(KitsugiColors.SurfaceSoft)
            .border(width = 1.dp, color = KitsugiColors.Border, shape = shape)
            .tvClickable(shape = shape, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = accentColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
