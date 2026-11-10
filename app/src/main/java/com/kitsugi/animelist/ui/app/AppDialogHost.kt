package com.kitsugi.animelist.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.kitsugi.animelist.data.local.MediaEntryRepository
import com.kitsugi.animelist.data.remote.ApiSearchSelection
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.WatchStatus
import com.kitsugi.animelist.ui.components.KitsugiApiSearchDialog
import com.kitsugi.animelist.ui.components.KitsugiConfirmDialog
import com.kitsugi.animelist.ui.components.KitsugiMediaEntryEditorDialog
import com.kitsugi.animelist.ui.components.KitsugiMediaGridDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

/**
 * Uygulama genelinde gösterilen overlay dialog'ları barındıran host composable.
 * AppRoot.kt'nin dialog yığınını temizlemek için çıkarılmıştır.
 */
@Composable
fun AppDialogHost(
    // Global search
    showGlobalSearch: Boolean,
    onDismissGlobalSearch: () -> Unit,
    onGlobalSearchResultSelected: (ApiSearchSelection) -> Unit,

    // Entry editing
    editingEntry: MediaEntry?,
    /** Düzenlenen üyelik kaydının sağlayıcı bağlamı; başka bir sağlayıcının kaydını dönüştürmez. */
    editingSourceHint: String? = null,
    scoreFormat: String,
    onDismissEditing: () -> Unit,
    onDeleteEditingEntry: (MediaEntry) -> Unit,
    onConfirmEdit: (
        title: String,
        subtitle: String,
        type: com.kitsugi.animelist.model.MediaType,
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
    ) -> Unit,

    // Entry deletion confirm
    deletingEntry: MediaEntry?,
    onConfirmDelete: (MediaEntry) -> Unit,
    onDismissDelete: () -> Unit,

    // Exit confirm
    showExitConfirmDialog: Boolean,
    onConfirmExit: () -> Unit,
    onDismissExit: () -> Unit,

    // Media grid dialog
    showMediaGridDialog: Boolean,
    mediaGridDialogTitle: String,
    mediaGridDialogResults: List<JikanSearchResult>,
    isAlreadyInList: (JikanSearchResult) -> Boolean,
    onMediaGridItemClick: (JikanSearchResult) -> Unit,
    onDismissMediaGrid: () -> Unit,
    getMediaEntry: (JikanSearchResult) -> MediaEntry? = { null }
) {
    val context = LocalContext.current


    if (showGlobalSearch) {
        KitsugiApiSearchDialog(
            onDismiss = onDismissGlobalSearch,
            onResultSelected = { selection ->
                onGlobalSearchResultSelected(selection)
                onDismissGlobalSearch()
            }
        )
    }

    editingEntry?.let { entry ->
        val isAniList = androidx.compose.runtime.remember { com.kitsugi.animelist.data.auth.ExternalAuthManager.getAniListToken(context) != null }
        val isMal = androidx.compose.runtime.remember { com.kitsugi.animelist.data.auth.ExternalAuthManager.getMalToken(context) != null }
        val isSimkl = androidx.compose.runtime.remember { com.kitsugi.animelist.data.auth.ExternalAuthManager.getSimklToken(context) != null }
        val isKitsu = androidx.compose.runtime.remember { com.kitsugi.animelist.data.auth.ExternalAuthManager.getKitsuToken(context) != null }
        val isShikimori = androidx.compose.runtime.remember { com.kitsugi.animelist.data.auth.ExternalAuthManager.getShikimoriToken(context) != null }

        // Sayfa ipucu: bilinen bir sağlayıcıya çözülüyorsa kaydın kendi kaynağını geçersiz kılar.
        // Takma adlar (bgm, jikan, themoviedb …) kanonik eşleyiciden geçirilir; düzenleyici
        // "tmdb" kaynağını Simkl olarak ele aldığı için tmdb de simkl'e çözülür.
        val hintedSource = if (com.kitsugi.animelist.data.auth.isTmdbAccountSource(entry.source)) {
            com.kitsugi.animelist.data.auth.TMDB_ACCOUNT_SOURCE
        } else editingSourceHint?.let { hint ->
            when (val canonical = com.kitsugi.animelist.data.remote.canonicalMediaSourceId(hint)) {
                "anilist", "mal", "simkl", "kitsu", "shikimori", "bangumi" -> canonical
                "tmdb" -> "simkl"
                else -> null
            }
        }

        val resolvedSource = hintedSource ?: when (entry.source.lowercase()) {
            "kitsu" -> "kitsu"
            "shikimori" -> "shikimori"
            "anilist" -> if (!isAniList && isMal) "mal" else "anilist"
            // jikan IS the MAL API — always resolve to "mal", only fall back to AniList if MAL isn't connected
            "mal", "jikan" -> if (isMal) "mal" else if (isAniList) "anilist" else "mal"
            "simkl" -> "simkl"
            "tmdb_account" -> "tmdb_account"
            // Preserve the entry's provider editor; linked cross-provider IDs must not pick Simkl.
            "bangumi", "bgm", "bgm.tv" -> "bangumi"
            else -> {
                when {
                    entry.source.lowercase() == "kitsu" -> "kitsu"
                    entry.source.lowercase() == "shikimori" -> "shikimori"
                    entry.source.lowercase() in setOf("bangumi", "bgm", "bgm.tv") -> "bangumi"
                    entry.aniListEntryId != null && isAniList -> "anilist"
                    entry.malId != null && isMal -> "mal"
                    entry.simklId != null && isSimkl -> "simkl"
                    entry.aniListEntryId != null -> "anilist"
                    entry.simklId != null -> "simkl"
                    entry.malId != null -> "mal"
                    else -> entry.source
                }
            }
        }

        KitsugiMediaEntryEditorDialog(
            initialEntry = entry,
            source = resolvedSource,
            scoreFormat = scoreFormat,
            onDismiss = onDismissEditing,
            onDeleteClick = {
                onDismissEditing()
                onDeleteEditingEntry(entry)
            },
            onConfirm = { title, subtitle, type, status, isAdult, progress, total, score, isFavorite, startDate, endDate, notes, tags, priority, isRepeating, repeatCount, repeatValue, volumeProgress, isPrivate, isHiddenFromStatusLists, advancedScores, tmdbWatchlist, tmdbRating ->
                onConfirmEdit(
                    title, subtitle, type, status, isAdult, progress, total, score,
                    isFavorite, startDate, endDate, notes, tags, priority,
                    isRepeating, repeatCount, repeatValue, volumeProgress,
                    isPrivate, isHiddenFromStatusLists, advancedScores, tmdbWatchlist, tmdbRating
                )
            }
        )
    }

    deletingEntry?.let { entry ->
        KitsugiConfirmDialog(
            title = stringResource(R.string.kaydi_sil),
            message = stringResource(R.string.entry_title_listenizden_kalici_olarak_kaldir, entry.title),
            confirmText = stringResource(R.string.action_delete),
            isDestructive = true,
            onConfirm = { onConfirmDelete(entry) },
            onDismiss = onDismissDelete
        )
    }

    if (showExitConfirmDialog) {
        KitsugiConfirmDialog(
            title = stringResource(R.string.uygulamadan_cik),
            message = stringResource(R.string.kitsugi_dan_cikmak_istediginizden_emin_misin),
            confirmText = stringResource(R.string.cik),
            dismissText = stringResource(R.string.iptal),
            isDestructive = true,
            onConfirm = {
                onConfirmExit()
                (context as? android.app.Activity)?.finish()
            },
            onDismiss = onDismissExit
        )
    }

    if (showMediaGridDialog) {
        KitsugiMediaGridDialog(
            title = mediaGridDialogTitle,
            results = mediaGridDialogResults,
            alreadyInList = isAlreadyInList,
            onItemClick = { result ->
                onMediaGridItemClick(result)
                onDismissMediaGrid()
            },
            onDismiss = onDismissMediaGrid,
            getMediaEntry = getMediaEntry
        )
    }
}
