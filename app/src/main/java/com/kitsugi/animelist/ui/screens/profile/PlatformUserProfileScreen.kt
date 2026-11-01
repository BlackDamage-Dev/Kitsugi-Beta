@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kitsugi.animelist.ui.screens.profile

import android.content.res.Configuration
import com.kitsugi.animelist.data.platform.PlatformErrorCode
import com.kitsugi.animelist.data.platform.PlatformFailureRegistry
import com.kitsugi.animelist.ui.components.PlatformErrorCodeChip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.icons.KitsugiIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.remote.OtherUserProfileRepository
import com.kitsugi.animelist.data.settings.AppSettings
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.app.KitsuProfileState
import com.kitsugi.animelist.ui.app.KitsugiProfileViewModel
import com.kitsugi.animelist.ui.app.MalProfileState
import com.kitsugi.animelist.ui.app.ProfileFavoriteItem
import com.kitsugi.animelist.ui.app.SimklProfileState
import com.kitsugi.animelist.ui.components.KitsugiImageGalleryDialog
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogo
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.utils.ShareUtils
import kotlinx.coroutines.launch

/**
 * Yorum / inceleme / tartışma kartlarından açılan, MAL / Kitsu / Simkl KULLANICILARININ
 * uygulama içi profil ekranı.
 *
 * Kullanıcı bir yorumcunun profiline tıklayınca tarayıcı yerine bu ekran açılır ve
 * mevcut zengin profil görünümleri ([MalProfileContent], [KitsuProfileContent],
 * [SimklProfileContent]) o kullanıcının verisiyle doldurulur. AniList kullanıcıları
 * [KitsugiUserProfileScreen] ile açılmaya devam eder.
 */
@Composable
fun PlatformUserProfileScreen(
    source: String,
    userId: Int,
    username: String?,
    avatarUrl: String?,
    appSettings: AppSettings,
    mediaEntries: List<MediaEntry>,
    onBackClick: () -> Unit,
    onEntryClick: (MediaEntry) -> Unit = {},
    onFavoriteMediaClick: (mediaId: Int, mediaType: MediaType, source: String, title: String, imageUrl: String?) -> Unit,
    onFavoriteCharacterClick: (charId: Int, source: String, name: String?, imageUrl: String?) -> Unit = { _, _, _, _ -> },
    onFavoriteStaffClick: (staffId: Int, source: String, name: String?, imageUrl: String?) -> Unit = { _, _, _, _ -> },
    onUserProfileClick: (userId: Int, username: String, avatarUrl: String?) -> Unit = { _, _, _ -> },
    onFavoriteStudioClick: ((studioId: Int, source: String, name: String?, imageUrl: String?) -> Unit)? = null,
    onGenreClick: (String) -> Unit = {},
    onScrollReset: () -> Unit = {}
) {
    val canonicalSource = source.lowercase().let {
        when (it) {
            "jikan", "myanimelist" -> "mal"
            else -> it
        }
    }

    // Yalnızca sekme/kaydırma belleği için ayrı bir ViewModel örneği; profil verisi
    // doğrudan [OtherUserProfileRepository] üzerinden bu ekranın kendi durumunda tutulur.
    val viewModel: KitsugiProfileViewModel = viewModel(key = "platform_profile_${canonicalSource}_${userId}_$username")

    val accentColor = LocalKitsugiAccent.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current

    var malState by remember(canonicalSource, userId, username) { mutableStateOf<MalProfileState?>(null) }
    var kitsuState by remember(canonicalSource, userId, username) { mutableStateOf<KitsuProfileState?>(null) }
    var simklState by remember(canonicalSource, userId, username) { mutableStateOf<SimklProfileState?>(null) }
    var loadError by remember(canonicalSource, userId, username) { mutableStateOf<String?>(null) }
    var loadErrorCode by remember(canonicalSource, userId, username) { mutableStateOf<PlatformErrorCode?>(null) }
    var isLoading by remember(canonicalSource, userId, username) { mutableStateOf(true) }
    var reloadTick by remember(canonicalSource, userId, username) { mutableStateOf(0) }
    val profileScope = rememberCoroutineScope()

    LaunchedEffect(canonicalSource, userId, username, reloadTick) {
        isLoading = true
        loadError = null
        loadErrorCode = null
        val result = runCatching {
            when (canonicalSource) {
                "kitsu" -> kitsuState = OtherUserProfileRepository.fetchKitsuUserState(userId, username, avatarUrl)
                "simkl" -> simklState = OtherUserProfileRepository.fetchSimklUserState(username, avatarUrl, userId)
                else -> malState = OtherUserProfileRepository.fetchMalUserState(username.orEmpty())
            }
        }
        if (result.isFailure) {
            loadError = KitsugiStrings.get(R.string.profile_load_failed)
            loadErrorCode = PlatformFailureRegistry.reportAndResolve(canonicalSource, result.exceptionOrNull())
        }
        isLoading = false
    }

    var activeFavoriteSheet by remember { mutableStateOf<Pair<String, List<ProfileFavoriteItem>>?>(null) }
    var onSheetItemClick by remember { mutableStateOf<((ProfileFavoriteItem) -> Unit)?>(null) }
    var activeGalleryImages by remember { mutableStateOf<Triple<List<String>, Int, String>?>(null) }

    val openFavoriteSheet: (String, List<ProfileFavoriteItem>, (ProfileFavoriteItem) -> Unit) -> Unit = { title, items, onClick ->
        activeFavoriteSheet = title to items
        onSheetItemClick = onClick
    }

    val displayName = when (canonicalSource) {
        "kitsu" -> kitsuState?.name?.takeIf { it.isNotBlank() }
        "simkl" -> simklState?.name?.takeIf { it.isNotBlank() }
        else -> malState?.name?.takeIf { it.isNotBlank() }
    } ?: username ?: ""

    val profileWebUrl = ShareUtils.buildProfileUrl(canonicalSource, displayName.ifBlank { username.orEmpty() })

    Column(modifier = Modifier.fillMaxSize().background(KitsugiColors.Background)) {
        // ── Top bar ──────────────────────────────────────────────────────────
        Surface(modifier = Modifier.fillMaxWidth(), color = KitsugiColors.Surface, shadowElevation = 2.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.ArrowBack,
                            contentDescription = KitsugiStrings.get(R.string.geri),
                            tint = KitsugiColors.TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    KitsugiPlatformLogo(platformId = canonicalSource, size = 18.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = displayName.ifBlank { KitsugiStrings.get(R.string.kullanici) },
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (displayName.isNotBlank()) {
                    IconButton(onClick = {
                        runCatching {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(profileWebUrl)
                            )
                            context.startActivity(intent)
                        }
                    }) {
                        Icon(
                            Icons.OpenInNew,
                            contentDescription = KitsugiStrings.get(R.string.profili_paylas),
                            tint = KitsugiColors.TextPrimary
                        )
                    }
                }
            }
        }

        // ── İçerik ───────────────────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading && (malState == null && kitsuState == null && simklState == null) -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        KitsugiPlasmaLoader(size = 50.dp)
                    }
                }
                loadError != null && malState == null && kitsuState == null && simklState == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = loadError ?: "",
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            loadErrorCode?.let { code -> PlatformErrorCodeChip(code = code) }
                        }
                    }
                }
                else -> {
                    when (canonicalSource) {
                        "kitsu" -> {
                            val state = kitsuState ?: KitsuProfileState(
                                isConnected = true,
                                id = userId.toString(),
                                name = username.orEmpty(),
                                avatarUrl = avatarUrl
                            )
                            ExternalProfileWrapper(
                                isConnected = true,
                                isLoading = isLoading,
                                error = loadError,
                                onConnectClick = {},
                                accentColor = accentColor,
                                platformName = "Kitsu",
                                onRetry = { reloadTick++ }
                            ) {
                                KitsuProfileContent(
                                    viewModel = viewModel,
                                    state = state,
                                    mediaEntries = mediaEntries,
                                    appSettings = appSettings,
                                    onEntryClick = onEntryClick,
                                    onFavoriteMediaClick = onFavoriteMediaClick,
                                    onOpenFavoriteSheet = openFavoriteSheet,
                                    onOpenStatsClick = null,
                                    isLandscape = isLandscape,
                                    accentColor = accentColor,
                                    onImageClick = { urls, idx, title -> activeGalleryImages = Triple(urls, idx, title) }
                                )
                            }
                        }
                        "simkl" -> {
                            val state = simklState ?: SimklProfileState(
                                isConnected = true,
                                name = username.orEmpty(),
                                avatarUrl = avatarUrl
                            )
                            ExternalProfileWrapper(
                                isConnected = true,
                                isLoading = isLoading,
                                error = loadError,
                                onConnectClick = {},
                                accentColor = accentColor,
                                platformName = "Simkl",
                                onRetry = { reloadTick++ }
                            ) {
                                SimklProfileContent(
                                    viewModel = viewModel,
                                    state = state,
                                    mediaEntries = mediaEntries,
                                    appSettings = appSettings,
                                    onEntryClick = onEntryClick,
                                    onFavoriteMediaClick = onFavoriteMediaClick,
                                    onOpenFavoriteSheet = openFavoriteSheet,
                                    onOpenStatsClick = null,
                                    isLandscape = isLandscape,
                                    accentColor = accentColor,
                                    onImageClick = { urls, idx, title -> activeGalleryImages = Triple(urls, idx, title) }
                                )
                            }
                        }
                        else -> {
                            val state = malState ?: MalProfileState(
                                isConnected = true,
                                name = username.orEmpty(),
                                avatarUrl = avatarUrl
                            )
                            ExternalProfileWrapper(
                                isConnected = true,
                                isLoading = isLoading,
                                error = loadError,
                                onConnectClick = {},
                                accentColor = accentColor,
                                platformName = "MyAnimeList",
                                onRetry = { reloadTick++ }
                            ) {
                                MalProfileContent(
                                    viewModel = viewModel,
                                    state = state,
                                    mediaEntries = mediaEntries,
                                    appSettings = appSettings,
                                    onEntryClick = onEntryClick,
                                    onFavoriteMediaClick = onFavoriteMediaClick,
                                    onFavoriteCharacterClick = onFavoriteCharacterClick,
                                    onFavoriteStaffClick = onFavoriteStaffClick,
                                    onOpenFavoriteSheet = openFavoriteSheet,
                                    onOpenStatsClick = null,
                                    isLandscape = isLandscape,
                                    accentColor = accentColor,
                                    onImageClick = { urls, idx, title -> activeGalleryImages = Triple(urls, idx, title) },
                                    onFavoriteStudioClick = onFavoriteStudioClick,
                                    onGenreClick = onGenreClick,
                                    onUserProfileClick = onUserProfileClick,
                                    onLoadMoreFriends = {
                                        malState?.takeIf { it.friendsHasNextPage && !it.isLoadingMoreFriends }?.let { current ->
                                            malState = current.copy(isLoadingMoreFriends = true, friendsLoadMoreFailed = false)
                                            profileScope.launch {
                                                val loaded = runCatching {
                                                    OtherUserProfileRepository.loadMoreMalFriends(current.name, current)
                                                }.getOrElse { current.copy(friendsLoadMoreFailed = true) }
                                                malState = loaded.copy(isLoadingMoreFriends = false)
                                            }
                                        }
                                    },
                                    onLoadMoreReviews = {
                                        malState?.takeIf { it.reviewsHasNextPage && !it.isLoadingMoreReviews }?.let { current ->
                                            malState = current.copy(isLoadingMoreReviews = true, reviewsLoadMoreFailed = false)
                                            profileScope.launch {
                                                val loaded = runCatching {
                                                    OtherUserProfileRepository.loadMoreMalReviews(current.name, current)
                                                }.getOrElse { current.copy(reviewsLoadMoreFailed = true) }
                                                malState = loaded.copy(isLoadingMoreReviews = false)
                                            }
                                        }
                                    },
                                    onLoadMoreRecommendations = {
                                        malState?.takeIf { it.recommendationsHasNextPage && !it.isLoadingMoreRecommendations }?.let { current ->
                                            malState = current.copy(isLoadingMoreRecommendations = true, recommendationsLoadMoreFailed = false)
                                            profileScope.launch {
                                                val loaded = runCatching {
                                                    OtherUserProfileRepository.loadMoreMalRecommendations(current.name, current)
                                                }.getOrElse { current.copy(recommendationsLoadMoreFailed = true) }
                                                malState = loaded.copy(isLoadingMoreRecommendations = false)
                                            }
                                        }
                                    },
                                    onLoadMoreClubs = {
                                        malState?.takeIf { it.clubsHasNextPage && !it.isLoadingMoreClubs }?.let { current ->
                                            malState = current.copy(isLoadingMoreClubs = true, clubsLoadMoreFailed = false)
                                            profileScope.launch {
                                                val loaded = runCatching {
                                                    OtherUserProfileRepository.loadMoreMalClubs(current.name, current)
                                                }.getOrElse { current.copy(clubsLoadMoreFailed = true) }
                                                malState = loaded.copy(isLoadingMoreClubs = false)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Favoriler "Tümünü Gör" sayfası ────────────────────────────────────────
    activeFavoriteSheet?.let { (sheetTitle, sheetItems) ->
        FavoritesExpandedBottomSheet(
            title = sheetTitle,
            items = sheetItems,
            blurAdultMedia = appSettings.blurAdultMedia,
            hasNextPage = false,
            onLoadMore = null,
            onItemClick = { item -> onSheetItemClick?.invoke(item) },
            onDismiss = { activeFavoriteSheet = null; onSheetItemClick = null }
        )
    }

    // ── Görsel galerisi ───────────────────────────────────────────────────────
    activeGalleryImages?.let { (urls, initialIdx, title) ->
        KitsugiImageGalleryDialog(
            imageUrls = urls,
            initialIndex = initialIdx,
            title = title,
            onDismiss = { activeGalleryImages = null }
        )
    }
}
