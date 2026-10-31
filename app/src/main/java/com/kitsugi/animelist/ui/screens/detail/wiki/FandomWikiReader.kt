package com.kitsugi.animelist.ui.screens.detail.wiki

import com.kitsugi.animelist.ui.components.KitsugiChevronIcon
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.key
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import com.kitsugi.animelist.data.fandom.FandomBlockLink
import com.kitsugi.animelist.data.fandom.FandomTextBlock
import com.kitsugi.animelist.data.fandom.FandomUrls
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.fandom.FandomBlockKind
import com.kitsugi.animelist.data.fandom.FandomFullPage
import com.kitsugi.animelist.data.fandom.FandomImage
import com.kitsugi.animelist.data.fandom.FandomRelatedPage
import com.kitsugi.animelist.data.fandom.FandomSectionContent
import com.kitsugi.animelist.data.fandom.FandomSectionRef
import com.kitsugi.animelist.data.fandom.FandomSpoilerRules
import com.kitsugi.animelist.data.remote.GalleryCategory
import com.kitsugi.animelist.data.remote.GalleryItem
import com.kitsugi.animelist.ui.components.DisableDialogWindowForceDark
import com.kitsugi.animelist.ui.components.KitsugiImageGalleryDialog
import com.kitsugi.animelist.ui.components.KitsugiOriginalToggleButton
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.utils.tvClickable
import com.kitsugi.animelist.ui.components.KitsugiWebViewDialog
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Uygulama içi wiki okuyucusunun arayüzle sözleşmesi. İki farklı VM (medya & karakter)
 * aynı arayüzü besler: bu sınıf o paydasızdır.
 */
data class FandomReaderRenderState(
    val wikiId: String,
    val page: FandomFullPage,
    val sections: Map<Int, FandomSectionContent>,
    val related: List<FandomRelatedPage>,
    val loadingSections: Set<Int> = emptySet(),
    val failedSections: Set<Int> = emptySet(),
    val translations: Map<String, String> = emptyMap(),
    val translatingKeys: Set<String> = emptySet(),
    val originalKeys: Set<String> = emptySet(),
    val preferredTranslator: String = "DEFAULT"
)

/**
 * Tam ekran uygulama içi wiki: varsayılan özgün WebView, isteğe bağlı sade okuyucu.
 * Özgün görünüm kaynak sitenin HTML/CSS/JS düzenini korur. Sade görünümde İçindekiler
 * satırları içeriği kendi altında açar;
 * çeviri/orijinal geçişleri, galeri, sekmeler ve ileri-geri sayfa geçmişi desteklenir.
 *
 * KRİTİK (çökme düzeltmesi): Okuyucu KENDİ penceresinde (tam ekran Dialog) çizilir.
 *
 *  1) Detay sayfası sekme içeriği portre düzende `DetailPageScaffold` içindeki
 *     `HorizontalPager` tarafından `constraints.copy(maxHeight = Constraints.Infinity)`
 *     ile ölçülür (sekme yüksekliğini doğal hâliyle ölçebilmek için). Okuyucu bu
 *     öğenin İÇİNE gömülü çizildiğinde `verticalScroll` sonsuz max height alıyor ve
 *     `IllegalStateException: Vertically scrollable component was measured with an
 *     infinity maximum height constraints` ile uygulama kapanıyordu.
 *  2) Yatay/TV düzeninde sayfa içeriği zaten bir `Box.verticalScroll` içindedir;
 *     okuyucunun kendi kaydırması onun içine iç içe girdiğinde AYNI istisna oluşuyordu.
 *
 * Dialog penceresi her koşulda sınırlı (bounded) kısıt verir; bu yüzden okuyucu hangi
 * ekrandan açılırsa açılsın (yapım / karakter / ekip / gömülü wiki kartı) güvenlidir.
 * Geri tuşu: etkin sekmenin geçmişinde geri gider; geçmiş boşsa okuyucuyu kapatır.
 *
 * ÜST ŞERİT (2026-10-21, ikinci tur): görünür tek denetim grubu sekme şerididir. Mod
 * seçimi, geri/ileri, yenile, masaüstü düzeni, sayfa araçları (çeviri + reklam engeli),
 * tarayıcıda aç, yeni sekme ve kapat — hepsi sağ köşedeki `⋮` menüsündedir.
 *
 * BAĞLANTILAR: özgün WebView'de dokunulan düz makale bağlantıları, kaynak sayfa yerinde
 * kalacak şekilde aktif sekmenin hemen sağında YENİ sekmede açılır (Chrome davranışı).
 */
@Composable
internal fun FandomWikiReaderSheet(
    state: FandomReaderRenderState?,
    navigation: FandomReaderBrowserState = FandomReaderBrowserState.Empty,
    accentColor: Color,
    onClose: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit = {},
    onNewTab: () -> Unit = {},
    onSwitchTab: (Long) -> Unit = {},
    onCloseTab: (Long) -> Unit = {},
    onCloseOtherTabs: (Long) -> Unit = {},
    onLoadSection: (Int) -> Unit,
    onRetrySection: (Int) -> Unit,
    onRetryPage: () -> Unit,
    onOpenLink: (String) -> Unit,
    /**
     * WebView içinde dokunulan wiki bağlantısı. `true` dönerse bağlantı yeni sekmede
     * açılmıştır ve WebView aynı sayfada gezinmeyi iptal eder; `false` dönerse mevcut
     * sekmede normal gezinme sürer (makale olmayan sayfalar, 12 sekme sınırı vb.).
     */
    onOpenLinkInNewTab: (String) -> Boolean = { false },
    onOpenInBrowser: () -> Unit,
    onToggleOriginal: (String) -> Unit = {},
    /**
     * Sayfanın TAMAMI için Orijinal/Çeviri geçişi (başlık, İçindekiler ve açılmış tüm
     * bölümler). Çeviri silinmez; "Çeviri"ye dönüldüğünde ağ isteği yapılmadan anında
     * geri gelir ve okuma konumu korunur.
     */
    onSetReaderOriginal: (Boolean) -> Unit = {},
    onSetWebMode: (Boolean) -> Unit,
    onWebLocationChanged: (Long, FandomReaderLocation) -> Unit,
    modifier: Modifier = Modifier,
    /** Yüklenemeyen sayfanın başlığı; doluysa sonsuz yükleme yerine hata kartı gösterilir. */
    failedTitle: String? = null,
    onRetryFailed: (() -> Unit)? = null
) {
    var originalLayout by rememberSaveable { mutableStateOf(true) }
    // Sayfa araçları artık görünür bir satır değil: üst şeritteki `⋮` menüsünden açılan
    // tek bir sayfa. Menü, bu controller üzerinden WebView tarafına sinyal gönderir.
    val pageToolsController = remember { WikiPageToolsController() }
    val webSessions = remember { HashMap<Long, WikiOriginalSession>() }
    // A fresh tab copies the exact document URL (including fragment/query), not just
    // its article title. Do not copy the source tab's WebView or back/forward history.
    val pendingWebDuplicate = remember { arrayOfNulls<WikiOriginalSession>(1) }
    val tab = navigation.activeTab
    val webSession = tab?.let {
        val existing = webSessions[it.id]
        if (existing == null || (originalLayout && existing.readerLocation != it.location)) {
            (pendingWebDuplicate[0]?.takeIf { copy -> existing == null && copy.readerLocation == it.location }
                ?: WikiOriginalSession(FandomUrls.pageUrl(it.location.wikiId, it.location.title), it.location))
                .also { session -> webSessions[it.id] = session }
        } else existing
    }
    val context = LocalContext.current
    var externalUrl by remember { mutableStateOf<String?>(null) }
    val useReader: () -> Unit = {
        if (tab != null && webSession != null &&
            FandomUrls.parseArticleUrl(webSession.url, tab.location.wikiId) == null
        ) {
            android.widget.Toast.makeText(context, R.string.wiki_original_reader_unavailable,
                android.widget.Toast.LENGTH_SHORT).show()
        } else {
            originalLayout = false
            onSetWebMode(false)
        }
    }
    LaunchedEffect(originalLayout) { onSetWebMode(originalLayout) }
    SideEffect {
        val ids = navigation.tabs.map { it.id }.toSet()
        webSessions.keys.retainAll(ids)
        pendingWebDuplicate[0] = null
    }
    val webBack = originalLayout && webSession?.canGoBack == true
    val webForward = originalLayout && webSession?.canGoForward == true
    val hasBackStack = webBack || navigation.canGoBack
    val goBack: () -> Unit = {
        if (webBack) webSession?.view?.goBack() else onBack()
    }
    val goForward: () -> Unit = {
        if (webForward) webSession?.view?.goForward() else onForward()
    }
    // Menüden "Özgün sayfa": okuyucu aynı sekmede başka bir belgeye gitmişse, eski WebView
    // oturumunu yanlışlıkla geri yüklememek için konum karşılaştırması yapılır.
    val switchToOriginal: () -> Unit = {
        if (!originalLayout && tab != null && webSession != null) {
            val saved = FandomUrls.parseArticleUrl(webSession.url, tab.location.wikiId)
            if (saved != (tab.location.wikiId to tab.location.title)) {
                webSession.history = null
                webSession.url = FandomUrls.pageUrl(tab.location.wikiId, tab.location.title)
                webSession.title = null
                webSession.scrollX = 0
                webSession.scrollY = 0
                webSession.canGoBack = false
                webSession.canGoForward = false
            }
        }
        if (tab != null) webSession?.readerLocation = tab.location
        originalLayout = true
        onSetWebMode(true)
    }
    val openWebUrl: (String) -> Unit = { url ->
        if (WikiOriginalPagePolicy.destination(url) != WikiOriginalPagePolicy.Destination.BLOCKED) {
            try {
                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(url)))
            } catch (_: Exception) {
                android.widget.Toast.makeText(context, R.string.fandom_open_failed,
                    android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    var localGalleryItems by remember { mutableStateOf(emptyList<GalleryItem>()) }
    var localGalleryIndex by remember { mutableStateOf(0) }
    // Kaydırma konumları BİLEŞİM (snapshot) state'i DEĞİLDİR: `snapshotFlow` konumu her
    // kaydırma karesinde yazar ve `initialScrollPosition` bileşimde okunur; harita state
    // olsaydı sayfa OKUYUCU HER KAREDE yeniden bileşim girer, otomatik çeviri açıkken
    // (çeviri taramaları + blok yeniden çizimi) bu kaydırmayla birleşip gözle görülür
    // kasıntıya yol açardı. Konumlar yalnızca sekme değişiminde okunur; düz HashMap yeterli.
    val scrollPositions = remember { HashMap<String, Int>() }
    val activeScrollKey = state?.let { page ->
        navigation.activeTabId?.let { tabId -> "$tabId:${page.wikiId}:${page.page.displayTitle}" }
    }
    val displayTitle = (if (originalLayout) webSession?.title ?: tab?.location?.title else null)
        ?: state?.page?.displayTitle
        ?: failedTitle
        ?: navigation.activeTab?.location?.title

    Dialog(
        onDismissRequest = {
            if (originalLayout && webSession?.findOpen == true) webSession.closeFind()
            else if (hasBackStack) goBack() else onClose()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        DisableDialogWindowForceDark()
        Surface(
            modifier = modifier.fillMaxSize(),
            color = KitsugiColors.Background,
            contentColor = KitsugiColors.TextPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .imePadding()
            ) {
                ReaderTabStrip(
                    navigation = if (originalLayout) navigation.copy(tabs = navigation.tabs.map { item ->
                        webSessions[item.id]?.title?.takeIf { it.isNotBlank() }?.let { title ->
                            item.copy(location = item.location.copy(title = title))
                        } ?: item
                    }) else navigation,
                    accentColor = accentColor,
                    onSwitchTab = onSwitchTab,
                    onCloseTab = onCloseTab,
                    onCloseOtherTabs = onCloseOtherTabs,
                    onNewTab = onNewTab,
                    trailing = {
                        ReaderBrowserMenu(
                            hasPage = displayTitle != null,
                            webMode = originalLayout,
                            desktopMode = webSession?.desktop == true,
                            onBack = goBack,
                            onForward = goForward,
                            onReload = { webSession?.reload() },
                            onToggleDesktop = { webSession?.let { it.setDesktopMode(!it.desktop) } },
                            onNewTab = {
                                if (originalLayout && webSession != null) {
                                    pendingWebDuplicate[0] = WikiOriginalSession(webSession.url, webSession.readerLocation).also {
                                        it.desktop = webSession.desktop
                                        it.title = webSession.title
                                        it.translationOverride = webSession.translationOverride
                                        it.lastTranslationPreferenceKey = webSession.lastTranslationPreferenceKey
                                        it.translationDocumentUrl = webSession.translationDocumentUrl
                                    }
                                }
                                onNewTab()
                            },
                            onClose = onClose,
                            onOpenInBrowser = {
                                if (originalLayout && webSession != null) openWebUrl(webSession.url)
                                else onOpenInBrowser()
                            },
                            onSelectOriginal = switchToOriginal,
                            onSelectReader = useReader,
                            onOpenPageTools = { pageToolsController.open() },
                            canGoBack = hasBackStack,
                            canGoForward = webForward || navigation.canGoForward,
                            canOpenNewTab = navigation.canOpenNewTab
                        )
                    }
                )
                WikiChromeHairline()
                when {
                    originalLayout && webSession != null && tab != null -> key(tab.id) {
                        WikiOriginalPage(
                            session = webSession,
                            accentColor = accentColor,
                            onLocationChanged = { url ->
                                // For absolute URLs, use the destination language rather than
                                // inheriting the source wiki language when returning to its root.
                                val destinationWiki = FandomUrls.wikiIdFromUrl(url) ?: tab.location.wikiId
                                FandomUrls.parseArticleUrl(url, destinationWiki)?.let { (wikiId, title) ->
                                    val location = FandomReaderLocation(wikiId, title)
                                    webSession.readerLocation = location
                                    onWebLocationChanged(tab.id, location)
                                }
                            },
                            onExternalLink = { externalUrl = it },
                            onBlockedLink = {
                                android.widget.Toast.makeText(context, R.string.wiki_original_link_blocked,
                                    android.widget.Toast.LENGTH_SHORT).show()
                            },
                            onUseReader = useReader,
                            onOpenLinkInNewTab = onOpenLinkInNewTab,
                            toolsController = pageToolsController,
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                    }
                    state == null && failedTitle != null -> ReaderFailed(
                        title = failedTitle,
                        accentColor = accentColor,
                        onRetry = onRetryFailed,
                        onOpenInBrowser = onOpenInBrowser
                    )
                    state == null -> ReaderLoading(accentColor)
                    else -> ReaderContent(
                        state = state,
                        accentColor = accentColor,
                        onLoadSection = onLoadSection,
                        onRetrySection = onRetrySection,
                        onRetryPage = onRetryPage,
                        onOpenLink = onOpenLink,
                        onToggleOriginal = onToggleOriginal,
                        onSetReaderOriginal = onSetReaderOriginal,
                        scrollKey = activeScrollKey.orEmpty(),
                        initialScrollPosition = activeScrollKey?.let { scrollPositions[it] } ?: 0,
                        onScrollPositionChanged = { key, position ->
                            scrollPositions[key] = position
                            if (scrollPositions.size > 120) {
                                scrollPositions.keys.firstOrNull { it != key }?.let(scrollPositions::remove)
                            }
                        },
                        onOpenGallery = { items, index ->
                            if (items.isNotEmpty()) {
                                localGalleryItems = items
                                localGalleryIndex = index.coerceIn(0, items.lastIndex)
                            }
                        }
                    )
                }
            }
        }
    }

    externalUrl?.let { url ->
        AlertDialog(
            onDismissRequest = { externalUrl = null },
            title = { Text(stringResource(R.string.wiki_external_title)) },
            text = { Text(url) },
            confirmButton = {
                TextButton(onClick = { externalUrl = null; openWebUrl(url) }) {
                    Text(stringResource(R.string.fandom_reader_open_browser))
                }
            },
            dismissButton = {
                TextButton(onClick = { externalUrl = null }) { Text(stringResource(R.string.kapat)) }
            }
        )
    }

    // Wiki sekmelerinin hepsinde galeri açma callback'i olmayabilir (ör. TV / eklenti
    // detayları). Böyle durumlarda okuyucunun kendi galeri penceresi devreye girer.
    if (localGalleryItems.isNotEmpty()) {
        KitsugiImageGalleryDialog(
            galleryItems = localGalleryItems,
            initialIndex = localGalleryIndex,
            title = displayTitle ?: "Wiki",
            onDismiss = { localGalleryItems = emptyList() }
        )
    }
}

/**
 * Tarayıcının TEK menüsü: sağ köşedeki `⋮`.
 *
 * 2026-10-21 (ikinci tur): başlık çubuğu tamamen kaldırıldı. Kullanıcı "hepsini üç noktaya
 * taşı, sadece sekme kısmı kalsın" dedi; o yüzden geri/ileri, mod seçimi, çeviri, reklam
 * engeli, masaüstü düzeni, yenile, sayfa araçları, tarayıcıda aç, yeni sekme ve kapat
 * burada. Ekranda görünür kalan tek denetim grubu sekme şeridi. Hiçbir işlev kaybolmadı.
 */
@Composable
private fun ReaderBrowserMenu(
    hasPage: Boolean,
    webMode: Boolean,
    desktopMode: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onNewTab: () -> Unit,
    onClose: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onToggleDesktop: () -> Unit,
    onReload: () -> Unit,
    onSelectOriginal: () -> Unit,
    onSelectReader: () -> Unit,
    onOpenPageTools: () -> Unit,
    canGoBack: Boolean = false,
    canGoForward: Boolean = false,
    canOpenNewTab: Boolean = true
) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        WikiIconAction(
            icon = Icons.MoreVert,
            contentDescription = stringResource(R.string.wiki_more_actions),
            tint = KitsugiColors.TextSecondary,
            onClick = { showMenu = true }
        )
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            containerColor = KitsugiColors.Surface
        ) {
            // ── Gezinme ──────────────────────────────────────────────────────
            ReaderMenuItem(
                label = stringResource(R.string.fandom_reader_back),
                icon = Icons.ArrowBack,
                enabled = canGoBack,
                onClick = { showMenu = false; onBack() }
            )
            ReaderMenuItem(
                label = stringResource(R.string.fandom_reader_forward),
                icon = Icons.ArrowForward,
                enabled = canGoForward,
                onClick = { showMenu = false; onForward() }
            )
            ReaderMenuDivider()
            // ── Görünüm modu ─────────────────────────────────────────────
            ReaderMenuItem(
                label = stringResource(R.string.wiki_original_page),
                icon = Icons.Language,
                checked = webMode,
                onClick = { showMenu = false; onSelectOriginal() }
            )
            ReaderMenuItem(
                label = stringResource(R.string.wiki_simple_reader),
                icon = Icons.MenuBook,
                checked = !webMode,
                onClick = { showMenu = false; onSelectReader() }
            )
            if (webMode) {
                ReaderMenuDivider()
                // ── Sayfa araçları (çeviri, reklam engeli, görünüm…) ─────
                ReaderMenuItem(
                    label = stringResource(R.string.wiki_page_tools),
                    icon = Icons.Tune,
                    onClick = { showMenu = false; onOpenPageTools() }
                )
                ReaderMenuItem(
                    label = stringResource(
                        if (desktopMode) R.string.wiki_mobile_layout
                        else R.string.wiki_desktop_layout
                    ),
                    icon = Icons.Web,
                    onClick = { showMenu = false; onToggleDesktop() }
                )
                ReaderMenuItem(
                    label = stringResource(R.string.yenile),
                    icon = Icons.Refresh,
                    onClick = { showMenu = false; onReload() }
                )
            }
            ReaderMenuDivider()
            // ── Tarayıcı eylemleri ───────────────────────────────────────
            ReaderMenuItem(
                label = stringResource(R.string.fandom_reader_open_browser),
                icon = Icons.OpenInNew,
                enabled = hasPage,
                onClick = { showMenu = false; onOpenInBrowser() }
            )
            ReaderMenuItem(
                label = stringResource(R.string.fandom_reader_new_tab),
                icon = Icons.Add,
                enabled = canOpenNewTab,
                onClick = { showMenu = false; onNewTab() }
            )
            ReaderMenuItem(
                label = stringResource(R.string.fandom_reader_close),
                icon = Icons.Close,
                onClick = { showMenu = false; onClose() }
            )
        }
    }
}

/**
 * `⋮` menüsündeki tek satır. Tüm okuyucu komutları aynı görsel dilde: 18 dp ikon,
 * tek satır etiket ve seçili modda sağda onay işareti.
 */
@Composable
private fun ReaderMenuItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    checked: Boolean = false,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                color = if (enabled) KitsugiColors.TextPrimary else KitsugiColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) KitsugiColors.TextSecondary
                else KitsugiColors.TextMuted.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        },
        trailingIcon = if (checked) {
            {
                Icon(
                    imageVector = Icons.Check,
                    contentDescription = null,
                    tint = KitsugiColors.TextPrimary,
                    modifier = Modifier.size(17.dp)
                )
            }
        } else null,
        enabled = enabled,
        onClick = onClick
    )
}

@Composable
private fun ReaderMenuDivider() {
    HorizontalDivider(
        color = KitsugiColors.Border.copy(alpha = 0.4f),
        thickness = 1.dp
    )
}

/** Sekmelerin en dar hâli; bunun altına sığılmazsa şerit yatay kaydırılır (Chrome davranışı). */
private val READER_TAB_MIN_WIDTH = 84.dp

/** Az sekme varken bir sekmenin genişleyebileceği üst sınır; artan alan boş kalır. */
private val READER_TAB_MAX_WIDTH = 208.dp
private val READER_TAB_SPACING = 6.dp
private val READER_TAB_HEIGHT = 32.dp

/**
 * Chrome/Brave tarzı sekme şeridi:
 *  - Sekmeler yan yana dizilir ve kullanılabilir genişliği EŞİT paylaşır; sekme sayısı
 *    arttıkça hepsi birlikte daralır, azaldıkça yeniden genişler.
 *  - Eşit pay [READER_TAB_MIN_WIDTH] altına düşerse genişlik orada sabitlenir ve şerit
 *    yatay kaydırılabilir olur; yeni/açılan sekme otomatik görünür alana kaydırılır.
 *  - Aktif sekme sayfa arkaplanıyla aynı renkte ve üst köşeleri yuvarlıdır (içerikle
 *    bütünleşir), pasif sekmeler şerit üzerinde düz durur. Dar sekmelerde kapat (×)
 *    yalnızca aktif sekmede gösterilir — Chrome'un dar sekmede yaptığı gibi.
 */
@Composable
private fun ReaderTabStrip(
    navigation: FandomReaderBrowserState,
    accentColor: Color,
    onSwitchTab: (Long) -> Unit,
    onCloseTab: (Long) -> Unit,
    onCloseOtherTabs: (Long) -> Unit = {},
    onNewTab: () -> Unit = {},
    /** Şeridin sağ köşesine yaslanan eylem(ler): `⋮` menüsü. */
    trailing: @Composable () -> Unit = {}
) {
    // Şerit HER ZAMAN görünür (tek sekmede de) ve tarayıcıda görünür kalan TEK denetim
    // grubudur. Yeni sekme `+` şeridin sonunda; kalan tüm komutlar sağ köşedeki `⋮`'de.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(KitsugiColors.Surface)
            .padding(start = 10.dp, end = 2.dp, top = 3.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(READER_TAB_SPACING)
    ) {
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val count = navigation.tabs.size.coerceAtLeast(1)
            // `+` kare bir çip; sekme genişliklerini hesaplarken yerini baştan ayır.
            val newTabSlot = READER_TAB_HEIGHT
            val equalWidth = (maxWidth - newTabSlot - READER_TAB_SPACING * count) / count
            if (equalWidth >= READER_TAB_MIN_WIDTH) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(READER_TAB_SPACING),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    navigation.tabs.forEach { tab ->
                        ReaderTabChip(
                            title = tab.location.title,
                            selected = tab.id == navigation.activeTabId,
                            accentColor = accentColor,
                            showClose = true,
                            onClick = { onSwitchTab(tab.id) },
                            onClose = { onCloseTab(tab.id) },
                            onLongClick = { onCloseOtherTabs(tab.id) },
                            modifier = if (equalWidth > READER_TAB_MAX_WIDTH) {
                                Modifier.width(READER_TAB_MAX_WIDTH)
                            } else {
                                Modifier.weight(1f)
                            }
                        )
                    }
                    ReaderNewTabChip(
                        enabled = navigation.canOpenNewTab,
                        accentColor = accentColor,
                        onClick = onNewTab
                    )
                }
            } else {
                // Sığmıyor: sabit dar genişlik + yatay kaydırma; aktif sekme görünür tutulur.
                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                val activeIndex = navigation.tabs.indexOfFirst { it.id == navigation.activeTabId }
                LaunchedEffect(navigation.activeTabId, navigation.tabs.size) {
                    if (activeIndex >= 0) listState.animateScrollToItem(activeIndex)
                }
                LazyRow(
                    state = listState,
                    horizontalArrangement = Arrangement.spacedBy(READER_TAB_SPACING),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(navigation.tabs, key = { it.id }) { tab ->
                        ReaderTabChip(
                            title = tab.location.title,
                            selected = tab.id == navigation.activeTabId,
                            accentColor = accentColor,
                            showClose = true,
                            onClick = { onSwitchTab(tab.id) },
                            onClose = { onCloseTab(tab.id) },
                            onLongClick = { onCloseOtherTabs(tab.id) },
                            modifier = Modifier.width(READER_TAB_MIN_WIDTH)
                        )
                    }
                    item {
                        ReaderNewTabChip(
                            enabled = navigation.canOpenNewTab,
                            accentColor = accentColor,
                            onClick = onNewTab
                        )
                    }
                }
            }
        }
        trailing()
    }
}

/** Sekme şeridinin sonundaki `+`: yeni sekme (Chrome/Brave düzeni). */
@Composable
private fun ReaderNewTabChip(
    enabled: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .size(READER_TAB_HEIGHT)
            .clip(shape)
            .background(KitsugiColors.SurfaceSoft.copy(alpha = 0.7f))
            .tvClickable(enabled = enabled, shape = shape, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Add,
            contentDescription = stringResource(R.string.fandom_reader_new_tab),
            tint = if (enabled) accentColor else KitsugiColors.TextMuted.copy(alpha = 0.4f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun ReaderTabChip(
    title: String,
    selected: Boolean,
    accentColor: Color,
    showClose: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
    Row(
        modifier = modifier
            .height(READER_TAB_HEIGHT)
            .clip(shape)
            .background(if (selected) KitsugiColors.Background else KitsugiColors.SurfaceStrong.copy(alpha = 0.55f))
            .then(
                if (selected) {
                    Modifier.border(1.dp, accentColor.copy(alpha = 0.45f), shape)
                } else {
                    Modifier
                }
            )
            .tvClickable(shape = shape, onLongClick = onLongClick) { onClick() }
            .padding(start = 10.dp, end = if (showClose) 2.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = if (selected) accentColor else KitsugiColors.TextSecondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (showClose) {
            // Material `IconButton` 48 dp'lik dokunma hedefi ekleyip 32 dp'lik sekme
            // şeridinin ölçüsünü bozuyordu; 22 dp daire yeterli ve şerit içinde kalıyor.
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .tvClickable(shape = CircleShape, onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Close,
                    contentDescription = stringResource(R.string.fandom_reader_close_tab, title),
                    tint = if (selected) accentColor else KitsugiColors.TextMuted,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

/** Okuyucu sayfası yüklenemedi: ölü sonsuz spinner yerine başlık + tekrar dene. */
@Composable
private fun ReaderFailed(
    title: String,
    accentColor: Color,
    onRetry: (() -> Unit)?,
    onOpenInBrowser: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Text(
                text = title,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.fandom_reader_failed),
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.fandom_reader_failed_hint),
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry) {
                    Text(
                        text = stringResource(R.string.action_retry_label),
                        color = accentColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            TextButton(onClick = onOpenInBrowser) {
                Text(stringResource(R.string.fandom_reader_open_browser), color = accentColor)
            }
        }
    }
}

@Composable
private fun ReaderLoading(accentColor: Color) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = accentColor, strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(stringResource(R.string.fandom_guide_more_loading), color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ReaderContent(
    state: FandomReaderRenderState,
    accentColor: Color,
    onLoadSection: (Int) -> Unit,
    onRetrySection: (Int) -> Unit,
    onRetryPage: () -> Unit,
    onOpenLink: (String) -> Unit,
    onToggleOriginal: (String) -> Unit,
    onSetReaderOriginal: (Boolean) -> Unit,
    scrollKey: String,
    initialScrollPosition: Int,
    onScrollPositionChanged: (String, Int) -> Unit,
    onOpenGallery: (List<GalleryItem>, Int) -> Unit
) {
    val scrollState = remember(scrollKey) { ScrollState(initialScrollPosition) }
    val readerScrollScope = rememberCoroutineScope()
    LaunchedEffect(scrollKey, scrollState) {
        snapshotFlow { scrollState.value }.collect { position ->
            onScrollPositionChanged(scrollKey, position)
        }
    }
    // ── Okuma konumu koruması ────────────────────────────────────────────────
    // Çeviri geçişi metin uzunluğunu değiştirir; eski piksel değeri bırakılırsa kullanıcı
    // başka bir paragrafa bakar. Geçişten önce görünümün üstündeki blok çapa alınır,
    // geçişten sonra aynı bloğun okuma oranı geri kurulur.
    val layoutProbe = remember(scrollKey) {
        WikiReaderLayoutProbe { scrollState.value }
    }
    var pendingRestore by remember(scrollKey) { mutableStateOf<PendingReaderRestore?>(null) }

    LaunchedEffect(pendingRestore, state.translations, state.originalKeys) {
        val pending = pendingRestore ?: return@LaunchedEffect
        // Geçiş henüz uygulanmadıysa bekle; durum değişince bu effect yeniden çalışır.
        if (pending.translations === state.translations && pending.originalKeys === state.originalKeys) {
            withFrameNanos { }
            withFrameNanos { }
            // İki kare içinde geçiş uygulanmadıysa (ör. anahtar zaten o durumdaydı) çapa
            // bırakılır; aksi hâlde bir sonraki çeviri güncellemesinde yanlış kaydırırdı.
            if (pendingRestore === pending) pendingRestore = null
            return@LaunchedEffect
        }
        // Yeni yerleşim ölçülsün diye iki kare beklenir (ölçümler layout sırasında yazılır).
        withFrameNanos { }
        withFrameNanos { }
        val newTop = layoutProbe.topOf(pending.anchor.id)
        if (newTop != null) {
            val target = pending.anchor.restoreScrollValue(
                newTop = newTop,
                newHeight = layoutProbe.heightOf(pending.anchor.id) ?: pending.anchor.height,
                scrollValue = pending.anchorScrollValue,
                maxScrollValue = scrollState.maxValue
            )
            if (target != scrollState.value) scrollState.scrollTo(target)
        }
        pendingRestore = null
    }

    /** Tek bir yüzeyin (bölüm/başlık) Orijinal-Çeviri geçişi; okuma konumu korunur. */
    val anchoredToggle: (String) -> Unit = { key ->
        layoutProbe.anchorFor(scrollState.value)?.let { captured ->
            pendingRestore = PendingReaderRestore(
                anchor = captured,
                anchorScrollValue = scrollState.value,
                translations = state.translations,
                originalKeys = state.originalKeys
            )
        }
        onToggleOriginal(key)
    }

    // ── Sayfa geneli Orijinal/Çeviri ─────────────────────────────────────────
    // Okuyucu sayfası "özel olarak çeviriye gidip gelmeli": başlık açıklaması, İçindekiler
    // satırları ve açılmış tüm bölümler tek dokunuşla orijinale döner, tek dokunuşla
    // (ağ isteği olmadan, bellekteki çeviriden) çeviriye geri gelir.
    val readerLocation = remember(state.wikiId, state.page.displayTitle) {
        FandomReaderLocation(state.wikiId, state.page.displayTitle)
    }
    val readerKeys = remember(readerLocation, state.page.sections) {
        buildSet {
            add(FandomReaderTranslationKeys.description(readerLocation))
            add(FandomReaderTranslationKeys.contents(readerLocation))
            state.page.sections.forEach { section ->
                add(FandomReaderTranslationKeys.section(readerLocation, section.index))
            }
        }
    }
    // ── Çeviri durumundan türetilen değerler ──────────────────────────────────
    // Bu taramalar tam çeviri haritasını (blok akışı boyunca sürekli değişen) her
    // bileşimde dolaşıyordu; otomatik çeviri açıkken kaydırmayla birleşince kasıntı
    // üretiyordu. Yalnızca harita kimliği/değişimi değiştikçe yeniden hesaplanır.
    val translatedReaderKeys = remember(state.translations, readerKeys) {
        readerKeys.filter { state.translations.hasWikiTranslation(it) }
    }
    val translatingReaderKeys = remember(state.translatingKeys, readerKeys) {
        readerKeys.count { it in state.translatingKeys }
    }
    val pageShowingOriginal = translatedReaderKeys.isNotEmpty() &&
        translatedReaderKeys.all { it in state.originalKeys }
    val showTranslationBar = translatedReaderKeys.isNotEmpty() || translatingReaderKeys > 0

    val pageToggle: () -> Unit = {
        layoutProbe.anchorFor(scrollState.value)?.let { captured ->
            pendingRestore = PendingReaderRestore(
                anchor = captured,
                anchorScrollValue = scrollState.value,
                translations = state.translations,
                originalKeys = state.originalKeys
            )
        }
        onSetReaderOriginal(!pageShowingOriginal)
    }

    val expandedSections = remember(scrollKey) { mutableStateListOf<Int>() }
    val galleryItems = remember(state.wikiId, state.page, state.sections, state.related) {
        buildReaderGalleryItems(state)
    }
    var webPageUrl by remember(scrollKey) { mutableStateOf<String?>(null) }
    // Every HTTP(S) link stays inside the app, including File/Category/Help and other wikis.
    //
    // performans: açıcı kimliği her bileşimde YENİDEN oluşturulursa `LocalFandomLinkOpener`
    // değeri değişir ve okuyucudaki TÜM mavi metinler (`ReaderLinkedText`) geçersizleşip
    // AnnotatedString'lerini yeniden kurar — otomatik çeviri akarken her çeviri güncellemesi
    // tüm metinleri yeniden ölçtü. Kimlik, sayfaya (scrollKey = sekme+wiki+başlık) bağlı
    // `remember` ile sabitlenir; güncel değerler `rememberUpdatedState` ile okunur.
    val latestOnOpenLink by rememberUpdatedState(onOpenLink)
    val pageWikiId = state.wikiId
    val pageDisplayTitle = state.page.displayTitle
    val openTarget: (String) -> Unit = remember(scrollKey, pageWikiId, pageDisplayTitle) {
        { url: String ->
            FandomUrls.readerLinkTarget(url, pageWikiId, pageDisplayTitle)?.let { target ->
                val articleTitle = target.articleTitle
                if (articleTitle != null) latestOnOpenLink(articleTitle) else webPageUrl = target.url
            }
        }
    }
    webPageUrl?.let { url ->
        KitsugiWebViewDialog(
            title = state.page.displayTitle,
            url = url,
            // Block non-web schemes instead of handing them off to another application.
            onUrlRedirect = { destination ->
                FandomUrls.readerLinkTarget(destination, state.wikiId, state.page.displayTitle) == null
            },
            onDismiss = { webPageUrl = null }
        )
    }
    CompositionLocalProvider(
        LocalFandomLinkOpener provides openTarget,
        LocalWikiReaderLayoutProbe provides layoutProbe
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
    val showReaderScrollToTop by remember { derivedStateOf { scrollState.value > 400 } }
    Column(
        modifier = Modifier
            .fillMaxSize()
            // Okuma konumu çapası için kaydırma sütununun (görünümün) kök koordinatı;
            // kaydırma sırasında sabit kalır, blok ölçümleri buna göre içerik koordinatına
            // çevrilir. `verticalScroll`'dan ÖNCE bağlanır ki görünümün konumu okunsun.
            .onGloballyPositioned { coordinates ->
                layoutProbe.onContainerPosition(coordinates.positionInRoot().y)
            }
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Sayfa geneli Orijinal / Çeviri şeridi ─────────────────────────────
        // Otomatik çeviri açıkken sayfa çevrilmiş hâlde gelir; bu şerit tüm yüzeyleri
        // (başlık, İçindekiler, açılmış bölümler) tek dokunuşla orijinale döndürür.
        if (showTranslationBar) {
            ReaderTranslationBar(
                showingOriginal = pageShowingOriginal,
                isLoading = translatingReaderKeys > 0,
                accentColor = accentColor,
                onToggle = pageToggle
            )
        }

        ReaderHeaderCard(
            state = state,
            galleryItems = galleryItems,
            accentColor = accentColor,
            onToggleOriginal = anchoredToggle,
            onOpenGallery = onOpenGallery
        )

        // Giriş bölümü (section 0): makale özeti veya ana sayfanın tüm modülleri.
        ReaderLeadCard(
            state = state,
            accentColor = accentColor,
            galleryItems = galleryItems,
            onRetry = { onRetrySection(READER_LEAD_SECTION) },
            onOpenLink = onOpenLink,
            onToggleOriginal = anchoredToggle,
            onOpenGallery = onOpenGallery
        )

        // Her İçindekiler satırı kendi içeriğini hemen altında açar; sayfanın en
        // altına ayrı kartlar yığılmaz. Birden fazla bölüm aynı anda açık kalabilir.
        if (state.page.sectionsFailed) {
            ReaderCard {
                Text(stringResource(R.string.fandom_reader_contents),
                    color = KitsugiColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.fandom_reader_contents_failed),
                    color = KitsugiColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRetryPage) {
                    Text(stringResource(R.string.action_retry_label), color = accentColor)
                }
            }
        } else if (state.page.sections.isNotEmpty()) {
            ReaderContentsCard(
                state = state,
                accentColor = accentColor,
                expandedSections = expandedSections,
                onToggle = { index ->
                    if (index in expandedSections) {
                        expandedSections.remove(index)
                    } else {
                        expandedSections.add(index)
                        when {
                            index in state.failedSections -> onRetrySection(index)
                            index !in state.sections -> onLoadSection(index)
                        }
                    }
                },
                onRetrySection = onRetrySection,
                onOpenLink = onOpenLink,
                onToggleOriginal = anchoredToggle,
                galleryItems = galleryItems,
                onOpenGallery = onOpenGallery
            )
        }

        if (state.related.isNotEmpty()) {
            ReaderRelatedCard(
                wikiId = state.wikiId,
                related = state.related,
                accentColor = accentColor,
                onOpenPage = onOpenLink
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
    // ── Yukarı hızlı kaydırma FAB'ı (tüm wiki okuyucularında tek tip) ─────────
    androidx.compose.animation.AnimatedVisibility(
        visible = showReaderScrollToTop,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 20.dp, bottom = 24.dp)
    ) {
        androidx.compose.material3.FloatingActionButton(
            onClick = { readerScrollScope.launch { scrollState.animateScrollTo(0) } },
            containerColor = accentColor,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier.size(56.dp)
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.KeyboardArrowUp,
                contentDescription = null,
                modifier = Modifier.size(30.dp)
            )
        }
    }
    }
    }
}

/** Okuyucu içindeki bağlantı hedefini sağlar; varsayılan no-op (sekme içi kullanımlar için). */
private val LocalFandomLinkOpener = compositionLocalOf<(String) -> Unit> { { _: String -> } }

/**
 * Okuma konumu çapası için blok ölçümlerini taşır. Çeviri geçişinde konumun korunması
 * yalnızca okuyucu içinde anlamlıdır; bu yüzden varsayılan olarak kapalıdır (null).
 */
private val LocalWikiReaderLayoutProbe = compositionLocalOf<WikiReaderLayoutProbe?> { null }

/**
 * Çeviri geçişi öncesi yakalanan okuma çapası. [translations]/[originalKeys] alanları
 * "geçiş gerçekten uygulandı mı" sorusunu **kimlik** karşılaştırmasıyla yanıtlar; böylece
 * konum düzeltmesi yeni yerleşim ölçülmeden çalışmaz.
 */
private class PendingReaderRestore(
    val anchor: WikiReaderAnchor,
    val anchorScrollValue: Int,
    val translations: Map<String, String>,
    val originalKeys: Set<String>
)

/**
 * Bir bloğu/yüzeyi ölçüp okuyucuya bildirir. Sarmalayıcı yalnızca ölçüm yapar: yerleşime
 * dokunmaz, bu yüzden blokların konumu ve düzeni birebir aynı kalır.
 */
@Composable
private fun ProbeMeasured(id: String, content: @Composable () -> Unit) {
    val probe = LocalWikiReaderLayoutProbe.current
    if (probe == null) {
        content()
        return
    }
    // Blok ekrandan çıktığında (bölüm kapandı, yapı değişti) ölçümü bırakılır; çapa
    // seçimi her zaman gerçekten çizilen bloklar arasından yapılır.
    DisposableEffect(probe, id) {
        onDispose { probe.forget(id) }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                probe.record(id, coordinates.positionInRoot().y, coordinates.size.height)
            }
    ) {
        content()
    }
}

/**
 * Sayfa geneli Orijinal/Çeviri şeridi. Otomatik çeviri açıkken okuyucu sayfaları çevrilmiş
 * hâlde gelir; bu şerit "çeviriye gidip gelme"yi tek dokunuşa indirir ve konum korunur.
 */
@Composable
private fun ReaderTranslationBar(
    showingOriginal: Boolean,
    isLoading: Boolean,
    accentColor: Color,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(KitsugiColors.SurfaceSoft)
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLoading) {
            CircularProgressIndicator(Modifier.size(14.dp), color = accentColor, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = stringResource(
                if (showingOriginal) R.string.fandom_reader_original_notice
                else R.string.fandom_reader_translated_notice
            ),
            color = KitsugiColors.TextSecondary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f)
        )
        KitsugiOriginalToggleButton(
            showingOriginal = showingOriginal,
            onClick = onToggle,
            tint = accentColor
        )
    }
}

/** Giriş bölümü (MediaWiki section 0). Ana sayfa için tüm içerik buradadır. */
private const val READER_LEAD_SECTION = 0

@Composable
private fun ReaderLeadCard(
    state: FandomReaderRenderState,
    accentColor: Color,
    galleryItems: List<GalleryItem>,
    onRetry: () -> Unit,
    onOpenLink: (String) -> Unit,
    onToggleOriginal: (String) -> Unit,
    onOpenGallery: (List<GalleryItem>, Int) -> Unit
) {
    val index = READER_LEAD_SECTION
    val content = state.sections[index]
    val isLoading = index in state.loadingSections
    val isFailed = index in state.failedSections
    if (content == null && !isLoading && !isFailed) return
    val location = FandomReaderLocation(state.wikiId, state.page.displayTitle)
    val key = FandomReaderTranslationKeys.section(location, index)
    ReaderCard {
        Text(
            text = stringResource(R.string.fandom_reader_intro),
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        ReaderSectionContent(
            section = FandomSectionRef(index = index, title = state.page.displayTitle, level = 1),
            content = content,
            isLoading = isLoading,
            isFailed = isFailed,
            translationKey = key,
            translatedText = state.translations[key],
            // Giriş bölümü (maddenin özeti) de blok blok çevrilir; bu parametre
            // geçilmediğinde yapı korumalı çeviri hiç görünmüyor, düz metin yedeği de
            // görselleri metnin altına yığıyordu.
            translations = state.translations,
            isTranslating = key in state.translatingKeys,
            showingOriginal = key in state.originalKeys,
            preferredTranslator = state.preferredTranslator,
            accentColor = accentColor,
            onRetry = onRetry,
            onOpenLink = onOpenLink,
            onToggleOriginal = onToggleOriginal,
            galleryItems = galleryItems,
            onOpenGallery = onOpenGallery
        )
    }
}

@Composable
private fun ReaderHeaderCard(
    state: FandomReaderRenderState,
    galleryItems: List<GalleryItem>,
    accentColor: Color,
    onToggleOriginal: (String) -> Unit,
    onOpenGallery: (List<GalleryItem>, Int) -> Unit
) {
    val location = FandomReaderLocation(state.wikiId, state.page.displayTitle)
    val descriptionKey = FandomReaderTranslationKeys.description(location)
    val translatedDescription = state.translations[descriptionKey]
    val showingOriginal = descriptionKey in state.originalKeys
    val description = state.page.description.orEmpty()
    val displayDescription = translatedDescription?.takeUnless { showingOriginal } ?: description

    ReaderCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            state.page.thumbnailUrl?.let { url ->
                val imageIndex = galleryItems.indexOfFirst { it.url == url }.coerceAtLeast(0)
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(false).build(),
                    contentDescription = state.page.displayTitle,
                    modifier = Modifier
                        .size(width = 96.dp, height = 136.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .tvClickable(shape = RoundedCornerShape(14.dp)) {
                            if (galleryItems.isNotEmpty()) onOpenGallery(galleryItems, imageIndex)
                        },
                    loading = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = accentColor, strokeWidth = 2.dp)
                        }
                    },
                    error = {
                        Box(Modifier.fillMaxSize().background(KitsugiColors.SurfaceStrong), contentAlignment = Alignment.Center) {
                            Icon(Icons.BrokenImage, null, tint = KitsugiColors.TextMuted)
                        }
                    }
                )
                Spacer(modifier = Modifier.width(14.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.page.displayTitle,
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    ProbeMeasured(
                        id = WikiReaderLayoutProbe.surfaceId(
                            descriptionKey,
                            WikiReaderLayoutProbe.SURFACE_DESCRIPTION
                        )
                    ) {
                        Text(
                            text = displayDescription,
                            color = KitsugiColors.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        WikiTranslateButton(
                            text = description,
                            preferredTranslator = state.preferredTranslator,
                            accentColor = accentColor,
                            isLoading = descriptionKey in state.translatingKeys,
                            hasTranslation = !translatedDescription.isNullOrBlank(),
                            showingOriginal = showingOriginal,
                            onToggleOriginal = { onToggleOriginal(descriptionKey) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderContentsCard(
    state: FandomReaderRenderState,
    accentColor: Color,
    expandedSections: List<Int>,
    onToggle: (Int) -> Unit,
    onRetrySection: (Int) -> Unit,
    onOpenLink: (String) -> Unit,
    onToggleOriginal: (String) -> Unit,
    galleryItems: List<GalleryItem>,
    onOpenGallery: (List<GalleryItem>, Int) -> Unit
) {
    ReaderCard {
        Text(
            text = stringResource(R.string.fandom_reader_contents),
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        // İçindekiler satırları da seçili dile çevrilir: okuyucu sayfalarında kalan son
        // İngilizce yüzey bölüm başlıklarıydı. Buradaki başlıklar bu sayfanın KENDİ
        // bölüm adlarıdır; mavi tıklanır bağlantı etiketleri (başka sayfalara giden
        // çipler/kartlar) hiç çevrilmez.
        val location = FandomReaderLocation(state.wikiId, state.page.displayTitle)
        val contentsShowingOriginal = FandomReaderTranslationKeys.contents(location) in state.originalKeys
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.page.sections.forEachIndexed { rowIndex, section ->
                val isExpanded = section.index in expandedSections
                val isLoaded = section.index in state.sections
                val sectionKey = FandomReaderTranslationKeys.section(location, section.index)
                val isLoading = section.index in state.loadingSections
                val isFailed = section.index in state.failedSections
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isExpanded) accentColor.copy(alpha = 0.08f) else Color.Transparent)
                            .tvClickable(shape = RoundedCornerShape(10.dp)) { onToggle(section.index) }
                            .padding(horizontal = 8.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.MenuBook,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            text = readerContentsTitle(
                                state = state,
                                rowIndex = rowIndex,
                                sectionIndex = section.index,
                                rawTitle = section.title,
                                location = location,
                                showingOriginal = contentsShowingOriginal
                            ),
                            color = if (isExpanded || isLoaded) accentColor else KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isExpanded || isLoaded) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        when {
                            isLoading -> CircularProgressIndicator(Modifier.size(15.dp), color = accentColor, strokeWidth = 2.dp)
                            isFailed -> Text("!", color = KitsugiColors.AccentOrange, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(4.dp))
                        KitsugiChevronIcon(
                            expanded = isExpanded,
                            contentDescription = null,
                            tint = if (isExpanded) accentColor else KitsugiColors.TextMuted,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    AnimatedVisibility(visible = isExpanded) {
                        ReaderSectionContent(
                            section = section,
                            content = state.sections[section.index],
                            isLoading = isLoading,
                            isFailed = isFailed,
                            translationKey = sectionKey,
                            translatedText = state.translations[sectionKey],
                            translations = state.translations,
                            isTranslating = sectionKey in state.translatingKeys,
                            showingOriginal = sectionKey in state.originalKeys,
                            preferredTranslator = state.preferredTranslator,
                            accentColor = accentColor,
                            onRetry = { onRetrySection(section.index) },
                            onOpenLink = onOpenLink,
                            onToggleOriginal = onToggleOriginal,
                            galleryItems = galleryItems,
                            onOpenGallery = onOpenGallery
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderSectionContent(
    section: FandomSectionRef,
    content: FandomSectionContent?,
    isLoading: Boolean,
    isFailed: Boolean,
    translationKey: String,
    translatedText: String?,
    /** Bölümün blok blok çevirileri (yapı koruyan çeviri); anahtar [translationKey] önekli. */
    translations: Map<String, String> = emptyMap(),
    isTranslating: Boolean,
    showingOriginal: Boolean,
    preferredTranslator: String,
    accentColor: Color,
    onRetry: () -> Unit,
    onOpenLink: (String) -> Unit,
    onToggleOriginal: (String) -> Unit,
    galleryItems: List<GalleryItem>,
    onOpenGallery: (List<GalleryItem>, Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 4.dp, top = 2.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(KitsugiColors.SurfaceSoft)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when {
            isLoading || content == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(15.dp), color = accentColor, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.fandom_guide_more_loading),
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            isFailed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.fandom_section_error),
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.action_retry_label), color = accentColor, fontWeight = FontWeight.Bold)
                }
            }
            else -> {
                // ── Bölüm bazlı çeviri alt haritası ──────────────────────────────
                // Tam `translations` haritası her blok akışında değişir; bu bölümün
                // çizicisi tam haritayı `remember` anahtarı alırsa BAŞKA bölümlerin
                // çevirisi gelirken de tüm bloklar yeniden hesaplanır ve otomatik
                // çeviri + kaydırma birleşince sayfa kasar. Alt harita yapısal eşitlik
                // koruduğu için yalnızca BU bölümün çevirisi değiştiğinde yeniden hesaplanır.
                val sectionTranslations = remember(translations, translationKey) {
                    translations.wikiTranslationsFor(translationKey)
                }
                val sourceText = remember(content) { content.wikiTranslationSourceText() }
                // YAPI KORUMASI: Çeviri blok blok yapıldığı için aynı yapılandırılmış çizici
                // kullanılır. Görseller kendi konumunda kalır, madde/kart/alıntı düzeni ve
                // bağlantılar (dokunma hedefleri) orijinaldekiyle birebir aynıdır.
                val translatedContent = remember(content, sectionTranslations, translationKey, showingOriginal) {
                    if (showingOriginal) null else content.withTranslatedBlocks(translationKey, sectionTranslations)
                }
                // Yapı bilgisi olmayan (eski önbellek) düz metin çevirisi için yedek yol.
                val flatTranslation = translatedText?.takeIf { content.blocks.isEmpty() }?.takeUnless {
                    it.isBlank() || WikiStructuredTranslationKeys.isCompleteMarker(it)
                }
                if (sourceText.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        WikiTranslateButton(
                            text = sourceText,
                            preferredTranslator = preferredTranslator,
                            accentColor = accentColor,
                            isLoading = isTranslating,
                            hasTranslation = sectionTranslations.hasWikiTranslation(translationKey),
                            showingOriginal = showingOriginal,
                            onToggleOriginal = { onToggleOriginal(translationKey) }
                        )
                    }
                }
                when {
                    translatedContent != null -> translatedContent.blocks.forEachIndexed { index, block ->
                        // Blok kimliği blok SIRASIDIR ve çeviri de aynı sırayı korur; bu
                        // yüzden Orijinal/Çeviri geçişinde aynı blokla eşleşir.
                        ProbeMeasured(id = WikiReaderLayoutProbe.blockId(translationKey, index)) {
                            ReaderBlock(block, accentColor, onOpenLink, galleryItems, onOpenGallery)
                        }
                    }
                    flatTranslation != null && !showingOriginal -> {
                        Text(
                            text = flatTranslation,
                            color = KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        content.blocks.filter { it.kind == FandomBlockKind.IMAGE || it.kind == FandomBlockKind.CARD }
                            .forEach { block -> ReaderBlock(block, accentColor, onOpenLink, galleryItems, onOpenGallery) }
                    }
                    else -> content.blocks.forEachIndexed { index, block ->
                        ProbeMeasured(id = WikiReaderLayoutProbe.blockId(translationKey, index)) {
                            ReaderBlock(block, accentColor, onOpenLink, galleryItems, onOpenGallery)
                        }
                    }
                }
                if (content.links.isNotEmpty() && !FandomSpoilerRules.isHiddenSection(section.title)) {
                    ReaderLinkChips(links = content.links, accentColor = accentColor, onOpenLink = onOpenLink)
                }
            }
        }
    }
}

@Composable
private fun ReaderBlock(
    block: FandomTextBlock,
    accentColor: Color,
    onOpenLink: (String) -> Unit,
    galleryItems: List<GalleryItem>,
    onOpenGallery: (List<GalleryItem>, Int) -> Unit
) {
    val openTarget = LocalFandomLinkOpener.current
    when (block.kind) {
        FandomBlockKind.HEADING -> ReaderLinkedText(
            text = if (block.links.isEmpty()) localizedSectionTitle(block.text) else block.text,
            links = block.links,
            color = KitsugiColors.TextPrimary,
            linkColor = accentColor,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp)
        )
        FandomBlockKind.PARAGRAPH -> ReaderLinkedText(
            text = block.text,
            links = block.links,
            color = KitsugiColors.TextPrimary,
            linkColor = accentColor,
            style = MaterialTheme.typography.bodyMedium
        )
        FandomBlockKind.BULLET -> Row(Modifier.fillMaxWidth()) {
            Text("•", color = KitsugiColors.TextSecondary, modifier = Modifier.padding(end = 8.dp))
            ReaderLinkedText(
                text = block.text,
                links = block.links,
                color = KitsugiColors.TextPrimary,
                linkColor = accentColor,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }
        FandomBlockKind.QUOTE -> Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(KitsugiColors.Surface)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            ReaderLinkedText(
                text = block.text,
                links = block.links,
                color = KitsugiColors.TextSecondary,
                linkColor = accentColor,
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            )
        }
        FandomBlockKind.IMAGE -> {
            val image = block.image
            if (image != null && image.url.isNotBlank()) {
                val imageIndex = galleryItems.indexOfFirst { it.url == image.url }.coerceAtLeast(0)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(KitsugiColors.SurfaceStrong)
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SubcomposeAsyncImage(
                        model = image.url,
                        contentDescription = image.caption,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .tvClickable(shape = RoundedCornerShape(8.dp)) {
                                if (galleryItems.isNotEmpty()) onOpenGallery(galleryItems, imageIndex)
                            },
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                    image.caption?.takeIf { it.isNotBlank() }?.let { caption ->
                        ReaderLinkedText(
                            text = caption,
                            links = block.links,
                            linkColor = accentColor,
                            color = KitsugiColors.TextMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    block.href?.let { href ->
                        Text(
                            text = stringResource(R.string.fandom_open_linked_page),
                            color = accentColor,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .tvClickable(shape = RoundedCornerShape(8.dp)) { openTarget(href) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
        FandomBlockKind.CARD -> ReaderCardTile(block, accentColor, galleryItems, onOpenGallery)
        FandomBlockKind.LINK_GROUP -> ReaderLinkGroup(block, accentColor)
    }
}

/** Metin + satır içi bağlantılar: her bağlantı etiketi tıklanabilir ve okuyucu yönlendirmesine gider. */
@Composable
private fun ReaderLinkedText(
    text: String,
    links: List<FandomBlockLink>,
    color: Color,
    linkColor: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null
) {
    val openTarget = LocalFandomLinkOpener.current
    if (links.isEmpty()) {
        Text(text = text, color = color, style = style, fontWeight = fontWeight, modifier = modifier)
        return
    }
    // AnnotatedString yalnızca METİN + BAĞLANTILAR değişince yeniden kurulur. Bağlantı
    // açıcısının kimliği (bileşimde tazelendikçe) anahtar olursa otomatik çeviri akarken
    // her güncellemede TÜM mavi metinler yeniden çizilir ve sayfa kaydırmada kasar.
    // Açıcı, tıklama anında `rememberUpdatedState` üzerinden okunur.
    val latestOpenTarget by rememberUpdatedState(openTarget)
    val annotated = remember(text, links, linkColor) {
        buildReaderLinkedText(text, links, linkColor) { url -> latestOpenTarget(url) }
    }
    Text(text = annotated, color = color, style = style, fontWeight = fontWeight, modifier = modifier)
}

private fun buildReaderLinkedText(
    text: String,
    links: List<FandomBlockLink>,
    linkColor: Color,
    openTarget: (String) -> Unit
): AnnotatedString = buildAnnotatedString {
    append(text)
    wikiLinkRanges(text, links).forEach { (range, link) ->
        addLink(
            LinkAnnotation.Clickable(
                tag = link.url,
                styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                linkInteractionListener = { openTarget(link.url) }
            ),
            range.first,
            range.last + 1
        )
    }
}

/** Ana sayfa kartı: görsel (varsa) + başlık + alt başlık; tüm kart tıklanabilir. */
@Composable
private fun ReaderCardTile(
    block: FandomTextBlock,
    accentColor: Color,
    galleryItems: List<GalleryItem>,
    onOpenGallery: (List<GalleryItem>, Int) -> Unit
) {
    val openTarget = LocalFandomLinkOpener.current
    val href = block.href
    val image = block.image?.takeIf { it.url.isNotBlank() }
    val imageIndex = image?.let { img -> galleryItems.indexOfFirst { it.url == img.url }.coerceAtLeast(0) } ?: 0
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(KitsugiColors.SurfaceStrong)
            .tvClickable(shape = shape) {
                when {
                    href != null -> openTarget(href)
                    image != null && galleryItems.isNotEmpty() -> onOpenGallery(galleryItems, imageIndex)
                }
            }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (image != null) {
            SubcomposeAsyncImage(
                model = image.url,
                contentDescription = block.text,
                modifier = Modifier
                    .size(width = 84.dp, height = 84.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (block.text.isNotBlank()) {
                Text(
                    text = block.text,
                    color = if (href != null) accentColor else KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            block.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                ReaderLinkedText(
                    text = subtitle,
                    links = block.links,
                    linkColor = accentColor,
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

/** Bağlantı grubu (Contents/navbox/ilgili sayfalar gibi): tüm bağlantılar çip olarak gösterilir. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderLinkGroup(block: FandomTextBlock, accentColor: Color) {
    val openTarget = LocalFandomLinkOpener.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (block.text.isNotBlank()) {
            Text(
                text = localizedSectionTitle(block.text),
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            block.links.forEach { link ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.10f))
                        .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                        .tvClickable(shape = RoundedCornerShape(10.dp)) { openTarget(link.url) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        link.label,
                        color = accentColor,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun buildReaderGalleryItems(state: FandomReaderRenderState): List<GalleryItem> = buildList {
    // Kaynak adı okuyucunun bulunduğu wiki'ye göre: Vikipedi maddelerinde "Fandom" yazması
    // kullanıcıya yanlış kaynak gösterirdi.
    val sourceLabel = FandomUrls.displaySourceName(state.wikiId)
    state.page.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { url ->
        add(GalleryItem(url, sourceLabel, GalleryCategory.OTHER, state.page.displayTitle))
    }
    state.sections.toSortedMap().values.forEach { content ->
        content.blocks.asSequence()
            .filter { it.kind == FandomBlockKind.IMAGE || it.kind == FandomBlockKind.CARD }
            .mapNotNull { it.image }
            .filter { it.url.isNotBlank() }
            .forEach { image -> add(image.toReaderGalleryItem(sourceLabel)) }
    }
    state.related.forEach { page ->
        page.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { url ->
            add(GalleryItem(url, sourceLabel, GalleryCategory.OTHER, page.title))
        }
    }
}.distinctBy { it.url }

private fun FandomImage.toReaderGalleryItem(sourceLabel: String): GalleryItem = GalleryItem(
    url = url,
    source = sourceLabel,
    category = GalleryCategory.OTHER,
    description = caption,
    details = buildMap {
        fileTitle?.takeIf { it.isNotBlank() }?.let { put("File", it) }
        artist?.takeIf { it.isNotBlank() }?.let { put("Artist", it) }
        license?.takeIf { it.isNotBlank() }?.let { put("License", it) }
    }
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderLinkChips(links: List<String>, accentColor: Color, onOpenLink: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        links.forEach { title ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.10f))
                    .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                    .tvClickable(shape = RoundedCornerShape(10.dp)) { onOpenLink(title) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(title, color = accentColor, style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ReaderRelatedCard(
    wikiId: String,
    related: List<FandomRelatedPage>,
    accentColor: Color,
    onOpenPage: (String) -> Unit
) {
    val wikipedia = FandomUrls.isWikipedia(wikiId)
    ReaderCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(
                    if (wikipedia) R.string.fandom_related_pages_title_wikipedia
                    else R.string.fandom_related_pages_title
                ),
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.fandom_related_pages_count, related.size),
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Spacer(Modifier.height(10.dp))
        WikiPreviewStripRow(
            items = related,
            key = { it.title }
        ) { page ->
            WikiPagePreviewCard(
                page = page,
                wikiId = wikiId,
                accentColor = accentColor,
                onClick = { onOpenPage(page.title) }
            )
        }
    }
}

/**
 * İçindekiler satırının görünen başlığı.
 *
 * Öncelik sırası:
 *  1. "Orijinal" görünümü → yalnızca sözlük yerelleştirmesi ([localizedSectionTitle]).
 *  2. Bölüm yüklüyse kendi başlığının çevirisi (satır ile içerikteki başlık birebir aynı
 *     olsun diye).
 *  3. İçindekiler listesinin toplu çevirisi (sayfa açılırken tek istekle gelir).
 *  4. Sözlük yerelleştirmesi.
 *
 * Bölüm başlığı bir **bağlantı etiketi** değildir; satırın dokunma hedefi yine aynı
 * bölümdür. Bu yüzden başlığın çevrilmesi mavi bağlantı korumasını ihlal etmez.
 *
 * `@Composable`: sözlük yerelleştirmesi `stringResource` okur (yerel dil değişince satır
 * kendiliğinden yenilenir).
 */
@Composable
private fun readerContentsTitle(
    state: FandomReaderRenderState,
    rowIndex: Int,
    sectionIndex: Int,
    rawTitle: String,
    location: FandomReaderLocation,
    showingOriginal: Boolean
): String {
    val localized = localizedSectionTitle(rawTitle)
    if (showingOriginal) return localized

    val blocks = state.sections[sectionIndex]?.blocks
    val headingIndex = blocks?.indexOfFirst { it.kind == FandomBlockKind.HEADING } ?: -1
    if (headingIndex >= 0) {
        val headingKey = WikiStructuredTranslationKeys.block(
            FandomReaderTranslationKeys.section(location, sectionIndex),
            headingIndex
        )
        state.translations[headingKey]?.takeIf { it.isNotBlank() }?.let { return it }
    }

    val rowKey = WikiStructuredTranslationKeys.block(
        FandomReaderTranslationKeys.contents(location),
        rowIndex
    )
    return state.translations[rowKey]?.takeIf { it.isNotBlank() } ?: localized
}

@Composable
private fun ReaderCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(KitsugiColors.Surface)
            .padding(14.dp),
        content = content
    )
}

@Composable
private fun localizedSectionTitle(raw: String): String {
    val resource = FandomSectionTitles.resId(raw)
    return if (resource != null) androidx.compose.ui.res.stringResource(resource) else raw
}
