package com.kitsugi.animelist.ui.screens.detail.wiki

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.data.fandom.FandomUrls
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.remote.GalleryItem

/**
 * Kişi (karakter / ekip / seslendirme sanatçısı) "Wiki" sekmesinin yuvası.
 *
 * Sekme sayfa açılır açılmaz vardır; çözümleme sürerken iskelet, sayfa gelince içerik,
 * bulunamazsa boş durum + "Tekrar dene" gösterilir. Karakter ve ekip sayfaları aynı
 * [FandomCharacterWikiViewModel]'i kullanır; yalnızca boş durum metni değişir.
 */
@Composable
internal fun CharacterWikiTabSlot(
    wikiState: FandomCharacterWikiViewModel.UiState,
    sections: Map<Int, FandomCharacterWikiViewModel.SectionState>,
    translations: Map<String, String>,
    translatingKeys: Set<String>,
    spoilerGuard: Boolean,
    accentColor: Color,
    onLoadSection: (Int) -> Unit,
    onOpenGallery: (List<GalleryItem>, Int) -> Unit,
    onConfirm: () -> Unit,
    onReportWrong: () -> Unit,
    onRetry: () -> Unit,
    unavailableMessageRes: Int = R.string.fandom_unavailable_character,
    preferredTranslator: String = "DEFAULT",
    originalKeys: Set<String> = emptySet(),
    onToggleOriginal: (String) -> Unit = {},
    onOpenWikiPage: ((String?) -> Unit)? = null,
    onOpenWikiLink: ((com.kitsugi.animelist.data.fandom.FandomWikiLink) -> Unit)? = null,
    relatedPages: List<com.kitsugi.animelist.data.fandom.FandomRelatedPage> = emptyList(),
    rejectedName: String? = null,
    onUndoReject: () -> Unit = {},
    onDismissRejectedUndo: () -> Unit = {},
    onSwitchSource: (Int) -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (rejectedName != null) {
            WikiRejectedUndoCard(
                accentColor = accentColor,
                rejectedName = rejectedName,
                onUndo = onUndoReject,
                onDismiss = onDismissRejectedUndo
            )
        }
        when (wikiState) {
            is FandomCharacterWikiViewModel.UiState.Ready -> CharacterWikiTabContent(
                page = wikiState.page,
                sections = sections,
                translations = translations,
                translatingKeys = translatingKeys,
                spoilerGuard = spoilerGuard,
                accentColor = accentColor,
                onLoadSection = onLoadSection,
                onOpenGallery = onOpenGallery,
                onConfirm = onConfirm,
                onReportWrong = onReportWrong,
                preferredTranslator = preferredTranslator,
                originalKeys = originalKeys,
                onToggleOriginal = onToggleOriginal,
                onOpenWikiPage = onOpenWikiPage,
                onOpenWikiLink = onOpenWikiLink,
                relatedPages = relatedPages,
                onSwitchSource = onSwitchSource
            )
            FandomCharacterWikiViewModel.UiState.Unavailable -> WikiUnavailableCard(
                accentColor = accentColor,
                messageRes = unavailableMessageRes,
                onRetry = onRetry
            )
            else -> WikiTabSkeleton(accentColor = accentColor)
        }
    }
}

/**
 * ViewModel'e doğrudan bağlı kısa yol: durumları kendisi toplar, onay/ret geri bildirimlerini
 * Toast ile verir. Ekip sayfası gibi tek noktadan kullanılan yerler için.
 */
@Composable
internal fun CharacterWikiTabSlot(
    viewModel: FandomCharacterWikiViewModel,
    accentColor: Color,
    onOpenGallery: (List<GalleryItem>, Int) -> Unit,
    modifier: Modifier = Modifier,
    unavailableMessageRes: Int = R.string.fandom_unavailable_character
) {
    val context = LocalContext.current
    val wikiState by viewModel.state.collectAsState()
    val sections by viewModel.sections.collectAsState()
    val translations by viewModel.translations.collectAsState()
    val translating by viewModel.translating.collectAsState()
    val prefs by viewModel.prefs.collectAsState()
    val preferredTranslator by viewModel.preferredTranslator.collectAsState()
    val originalKeys by viewModel.originalKeys.collectAsState()
    val readerState by viewModel.reader.collectAsState()
    val readerBrowser by viewModel.readerBrowser.collectAsState()
    val related by viewModel.relatedPages.collectAsState()
    val lastRejected by viewModel.lastRejected.collectAsState()

    BackHandler(enabled = readerState != FandomCharacterWikiViewModel.ReaderState.Closed) {
        viewModel.readerBack()
    }

    androidx.compose.foundation.layout.Box(modifier = modifier) {
        CharacterWikiTabSlot(
            wikiState = wikiState,
            sections = sections,
            translations = translations,
            translatingKeys = translating,
            spoilerGuard = prefs.spoilerGuard,
            accentColor = accentColor,
            onLoadSection = viewModel::loadSection,
            onOpenGallery = onOpenGallery,
            onConfirm = {
                viewModel.confirmMatch()
                Toast.makeText(context, context.getString(R.string.fandom_confirm_done), Toast.LENGTH_SHORT).show()
            },
            onReportWrong = {
                viewModel.reportWrongMatch()
                Toast.makeText(context, context.getString(R.string.fandom_report_wrong_done), Toast.LENGTH_SHORT).show()
            },
            onRetry = viewModel::retry,
            unavailableMessageRes = unavailableMessageRes,
            preferredTranslator = preferredTranslator,
            originalKeys = originalKeys,
            onToggleOriginal = viewModel::toggleOriginal,
            onOpenWikiPage = { title -> viewModel.openReader(title) },
            onOpenWikiLink = { link ->
                viewModel.openReader(link.pageTitle, wikiIdOverride = link.wikiId)
            },
            relatedPages = related,
            onSwitchSource = viewModel::switchSource,
            rejectedName = lastRejected?.pageTitle,
            onUndoReject = {
                viewModel.undoLastReject()
                Toast.makeText(context, context.getString(R.string.fandom_reject_undo_done), Toast.LENGTH_SHORT).show()
            },
            onDismissRejectedUndo = viewModel::dismissLastRejected
        )

        val readerReady = readerState as? FandomCharacterWikiViewModel.ReaderState.Ready
        val readerFailedTitle = (readerState as? FandomCharacterWikiViewModel.ReaderState.Failed)?.title
        // Ready dışında Loading/Failed da gösterilir: tıklanan mavi bağlantı
        // yüklenemezse sessizce hiçbir şey olmamış gibi kalmaz.
        if (readerState != FandomCharacterWikiViewModel.ReaderState.Closed) {
            FandomWikiReaderSheet(
                state = readerReady?.let { ready ->
                    FandomReaderRenderState(
                        wikiId = ready.wikiId,
                        page = ready.page,
                        sections = ready.sections,
                        related = ready.related,
                        loadingSections = ready.loadingSections,
                        failedSections = ready.failedSections,
                        translations = translations,
                        translatingKeys = translating,
                        originalKeys = originalKeys,
                        preferredTranslator = preferredTranslator
                    )
                },
                navigation = readerBrowser,
                failedTitle = readerFailedTitle,
                onRetryFailed = readerFailedTitle?.let { failed ->
                    { viewModel.retryReaderPage() }
                },
                accentColor = accentColor,
                onClose = viewModel::closeReader,
                onBack = { viewModel.readerBack() },
                onForward = { viewModel.readerForward() },
                onNewTab = { viewModel.newReaderTab() },
                onSwitchTab = viewModel::switchReaderTab,
                onCloseTab = viewModel::closeReaderTab,
                onCloseOtherTabs = viewModel::closeOtherReaderTabs,
                onToggleOriginal = viewModel::toggleOriginal,
                onLoadSection = viewModel::loadReaderSection,
                onRetrySection = viewModel::retryReaderSection,
                onRetryPage = viewModel::retryReaderPage,
                onOpenLink = viewModel::openReaderLink,
                // Sayfa geneli Orijinal/Çeviri: tüm yüzeyler tek dokunuşla gidip gelir.
                onSetReaderOriginal = viewModel::setReaderOriginal,
                onSetWebMode = viewModel::setReaderWebMode,
                onWebLocationChanged = viewModel::updateReaderWebLocation,
                onOpenInBrowser = {
                    val location = readerBrowser.activeTab?.location ?: return@FandomWikiReaderSheet
                    val openUrl: (String) -> Unit = { url ->
                        try {
                            context.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (_: Exception) {
                            Toast.makeText(context, context.getString(R.string.fandom_open_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                    openUrl(FandomUrls.pageUrl(location.wikiId, location.title))
                }
            )
        }
    }
}
