package com.kitsugi.animelist.ui.screens.search

import android.app.Application
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsugi.animelist.data.remote.ApiSearchSelection
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.ui.app.SourceSearchOwner
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogo
import com.kitsugi.animelist.ui.theme.KitsugiColors
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

/**
 * "Tümünü Gör" ile açılan kaynağa özel tam arama sayfası.
 *
 * Çoklu arama sonuçlarındaki bir platform rafının "Tümünü Gör" butonu,
 * o kaynağın filtreleri ve arama sonuçlarıyla birlikte AYRI bir sayfada
 * açılır. Sayfa, ana arama sayfasının ViewModel'inden izole own ViewModel
 * instance'ı kullanır — böylece burada değiştirilen motor/kapsam/filtre
 * durumu ana sayfaya sızmaz ve geri dönüldüğünde çoklu arama olduğu gibi kalır.
 */
@Composable
fun SourceSearchPage(
    engine: SearchSourceEngine,
    initialQuery: String,
    initialScope: SearchScope,
    initialResults: List<JikanSearchResult>,
    mediaEntries: List<MediaEntry>,
    showAdultContent: Boolean,
    titleLanguage: String = "ENGLISH",
    scoreFormat: String = "POINT_10",
    hideScores: Boolean = false,
    onBackClick: () -> Unit,
    onOpenApiDetail: (JikanSearchResult) -> Unit,
    onAddSelectionToList: (ApiSearchSelection) -> Unit,
    onOpenPluginPicker: () -> Unit = {},
    onOpenAddonExplore: (String) -> Unit = {},
    onOpenCharacterDetail: (Int, String?, String?, String?) -> Unit = { _, _, _, _ -> },
    onOpenStaffDetail: (Int, String?, String?, String?) -> Unit = { _, _, _, _ -> },
    onOpenStudioDetail: (Int, String, String?, String?) -> Unit = { _, _, _, _ -> },
    retainedOwner: SourceSearchOwner? = null
) {
    // ── İzole ViewModel ────────────────────────────────────────────────────
    val context = LocalContext.current
    val localOwner = remember { SourceSearchOwner() }
    val owner = retainedOwner ?: localOwner
    val app = context.applicationContext as Application
    val factory = remember(app) {
        ViewModelProvider.AndroidViewModelFactory.getInstance(app)
    }
    val pageViewModel: SearchViewModel = viewModel(viewModelStoreOwner = owner, factory = factory)
    DisposableEffect(owner) {
        onDispose { if (retainedOwner == null) localOwner.viewModelStore.clear() }
    }

    LaunchedEffect(owner, engine, initialQuery, initialScope) {
        if (!owner.initialized) {
            owner.initialized = true
            pageViewModel.openSourceSearch(engine, initialQuery, initialScope, initialResults)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KitsugiColors.Background)
    ) {
        // ── Üst Bar: Geri + Kaynak Başlığı ─────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.ArrowBack,
                    contentDescription = stringResource(R.string.geri),
                    tint = KitsugiColors.TextPrimary
                )
            }
            // Kaynak adından ÖNCE emoji değil, platformun ORİJİNAL logosu gösterilir
            // (KitsugiPlatformLogo: AniList, MAL, TMDB, Shikimori, Kitsu, Simkl, Bangumi).
            // "Tümü" motorunda logo bulunmadığı için küre simgesine düşer.
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KitsugiPlatformLogo(
                    platformId = engine.id,
                    size = 26.dp,
                    fallbackTint = KitsugiColors.TextPrimary
                )
                Text(
                    text = "${engine.localizedLabel()} ${stringResource(R.string.arama)}",
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        // ── Kaynağa Özel Arama Sayfası (filtreler + sonuçlar) ─────────────
        SearchScreen(
            currentEntries = mediaEntries,
            showAdultContent = showAdultContent,
            onOpenApiDetail = onOpenApiDetail,
            onAddSelectionToList = onAddSelectionToList,
            viewModel = pageViewModel,
            titleLanguage = titleLanguage,
            scoreFormat = scoreFormat,
            hideScores = hideScores,
            onOpenPluginPicker = onOpenPluginPicker,
            onOpenAddonExplore = onOpenAddonExplore,
            onOpenCharacterDetail = onOpenCharacterDetail,
            onOpenStaffDetail = onOpenStaffDetail,
            onOpenStudioDetail = onOpenStudioDetail,
            // Bu sayfa içinde "Tümü" motoruna geçilirse rafların "Tümünü Gör"ü
            // sayfayı ilgili kaynağa geçirir (yeni sayfa yığmadan kaçınır).
            onOpenSourceSearch = { subEngine, scope, results ->
                pageViewModel.openSourceSearch(subEngine, pageViewModel.uiState.value.query, scope, results)
            },
            isBottomBarVisible = false,
            pageTitle = null
        )
    }
}
