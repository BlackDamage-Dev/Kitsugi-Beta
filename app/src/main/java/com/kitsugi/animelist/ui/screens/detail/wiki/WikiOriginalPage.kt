package com.kitsugi.animelist.ui.screens.detail.wiki

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.data.settings.SettingsDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kitsugi.animelist.R

/** One lightweight history per tab, but only ONE live WebView for the whole reader. */
internal class WikiOriginalSession(
    initialUrl: String,
    var readerLocation: FandomReaderLocation,
    /** Son seçilen düzen; varsayılan mobil (false). Kullanıcı seçimi kalıcıdır. */
    initialDesktop: Boolean = false
) {
    var url by mutableStateOf(initialUrl)
    var title by mutableStateOf<String?>(null)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var desktop by mutableStateOf(initialDesktop)
    /**
     * Okurken ekranın kapanmaması. Oturumludur (uygulama yeniden başlarsa kapanır) ve
     * yalnızca bu sekmenin WebView'ine uygulanır; ayarlara yazılmaz.
     */
    var keepScreenOn by mutableStateOf(false)
    var progress by mutableStateOf(0)
    var failed by mutableStateOf(false)
    var pageCommitted by mutableStateOf(false)
    var pageThemeSupported by mutableStateOf(true)
    var findOpen by mutableStateOf(false)
    var findQuery by mutableStateOf("")
    var findMatches by mutableStateOf(0)
    var findActiveMatch by mutableStateOf(0)
    var documentTextRevision by mutableStateOf(0)
    var generation by mutableStateOf(0)
    var translationOverride by mutableStateOf<Boolean?>(null)
    /**
     * Sayfa araçlarından dil seçildiğinde işaretlenir. Dil değişimi ayar akışıyla
     * gelince manuel çeviri seçimi normalde sıfırlanır; bu bayrak, kullanıcının
     * "bu dile çevir" niyetinin sıfırlanmayıp çeviriyi açmasını sağlar (tek kullanımlık).
     */
    var pendingTranslationEnable = false
    var translating by mutableStateOf(false)
    var translatedNodes by mutableStateOf(0)
    var translationFailures by mutableStateOf(0)
    var translationLimited by mutableStateOf(false)
    var articleAvailable by mutableStateOf(false)
    var enhancementError by mutableStateOf(false)
    var translationRetry by mutableStateOf(0)
    var blockedRequests by mutableStateOf(0)
    var lastTranslationPreferenceKey: String? = null
    var translationDocumentUrl: String? = null
    var enhancements: WikiPageEnhancements? = null
    var restoreAfterReload = false
    var history: Bundle? = null
    var historyDesktop: Boolean? = null
    var scrollX = 0
    var scrollY = 0
    var view: WebView? = null

    fun closeFind() {
        findOpen = false
        findQuery = ""
        findMatches = 0
        findActiveMatch = 0
        view?.clearMatches()
    }

    fun reload() {
        failed = false
        if (view != null) view?.reload() else generation++
    }

    fun setDesktopMode(value: Boolean) {
        if (desktop == value) return
        view?.let {
            scrollX = it.scrollX
            scrollY = it.scrollY
            history = Bundle().also { state -> it.saveState(state) }
            historyDesktop = desktop
        }
        desktop = value
        // Rebuild using the new UA. History remains available via saveState/restoreState.
        generation++
    }
}

/**
 * Render the actual remote document, not API fragments wrapped in invented CSS.
 * This preserves ResourceLoader, TemplateStyles, site JS, tabs, countdowns, galleries,
 * CSS variables, relative links and lazy-loaded media, including future wiki changes.
 * No JS/native bridge, element replacement or global dark-mode filter. Optional tools
 * change text node values in place and hide narrow advertising containers. Desktop mode
 * overrides viewport metadata, like requesting a desktop site.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun WikiOriginalPage(
    session: WikiOriginalSession,
    accentColor: Color,
    onLocationChanged: (String) -> Unit,
    onExternalLink: (String) -> Unit,
    onBlockedLink: () -> Unit,
    onUseReader: () -> Unit,
    /**
     * WebView içinde dokunulan wiki bağlantısı için "yeni sekmede aç" isteği.
     * `true` dönerse navigasyon iptal edilir (yeni sekme açıldı); `false` dönerse bağlantı
     * mevcut sekmede normal şekilde yüklenir.
     */
    onOpenLinkInNewTab: (String) -> Boolean = { false },
    /** Üst şeritteki `⋮` menüsünden sayfa araçları sayfasını açan köprü. */
    toolsController: WikiPageToolsController? = null,
    modifier: Modifier = Modifier
) {
    val currentLocationChanged by rememberUpdatedState(onLocationChanged)
    val currentExternalLink by rememberUpdatedState(onExternalLink)
    val currentBlockedLink by rememberUpdatedState(onBlockedLink)
    val currentOpenLinkInNewTab by rememberUpdatedState(onOpenLinkInNewTab)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current.applicationContext
    val store = remember(context) { SettingsDataStore(context) }
    var preferenceReadFailed by remember { mutableStateOf(false) }
    val preferences by remember(store, session.generation) {
        store.settingsFlow.map {
            preferenceReadFailed = false
            WikiPagePreferences(it.autoTranslateEnabled, it.translateSourceLanguage,
                it.translateTargetLanguage, it.wikiAdBlockDisabledHosts, it.wikiPageTheme, it.wikiTextZoom)
        }.distinctUntilChanged().catch { error ->
            if (error is CancellationException) throw error
            preferenceReadFailed = true
            // Never infer consent for automatic translation when preferences cannot be read.
            emit(WikiPagePreferences(false, "auto", com.kitsugi.animelist.utils.KitsugiTranslatePrefs.resolvedTarget(), emptySet()))
        }
    }.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var savingPreference by remember { mutableStateOf(false) }
    var preferenceFailed by remember { mutableStateOf(false) }
    var assetFailed by remember { mutableStateOf(false) }
    val assets by produceState<WikiPageAssets?>(null, context, session.generation) {
        try {
            value = withContext(Dispatchers.IO) { WikiPageAssets.load(context) }
            assetFailed = false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { assetFailed = true }
    }
    val host = android.net.Uri.parse(session.url).host.orEmpty().lowercase(java.util.Locale.ROOT)
    LaunchedEffect(preferences?.translationKey) {
        preferences?.translationKey?.let { key ->
            if (session.lastTranslationPreferenceKey != null && session.lastTranslationPreferenceKey != key) {
                // Dil, sayfa araçlarından seçildiyse niyet "bu dile çevir"dir:
                // sıfırlamak yerine çeviriyi aç. Ayarlar sayfasından gelen
                // değişikliklerde eski davranış (otomatiği izle) korunur.
                session.translationOverride = if (session.pendingTranslationEnable) true else null
                session.pendingTranslationEnable = false
            }
            session.lastTranslationPreferenceKey = key
        }
    }
    val config = preferences?.configFor(host, session.translationOverride,
        session.lastTranslationPreferenceKey, session.translationRetry) ?: WikiPageToolConfig()
    val currentConfig by rememberUpdatedState(config)
    val background = KitsugiColors.Background
    val pageMode = WikiReadingAppearance.resolve(preferences?.pageTheme ?: "SITE", background.luminance() < 0.5f)
    val textZoom = WikiReadingAppearance.engineTextZoom(preferences?.textZoom ?: 100, LocalDensity.current.fontScale)
    val currentPageMode by rememberUpdatedState(pageMode)
    val currentTextZoom by rememberUpdatedState(textZoom)
    val currentBackground by rememberUpdatedState(background.toArgb())
    SideEffect {
        session.enhancements?.configure(config)
        session.enhancements?.setAppearance(pageMode)
        session.view?.let { view ->
            if (view.settings.textZoom != textZoom) view.settings.textZoom = textZoom
            if (view.keepScreenOn != session.keepScreenOn) view.keepScreenOn = session.keepScreenOn
            if (!session.pageCommitted) view.setBackgroundColor(currentBackground)
        }
    }

    // Sayfa tercihi yazımlarının ortak iskeleti (kaydetme durumu + hata bayrağı).
    val savePreference: (suspend () -> Unit) -> Unit = { block ->
        scope.launch {
            savingPreference = true
            try {
                block()
                preferenceFailed = false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                preferenceFailed = true
                // Yazılamayan dil seçimi, sonraki alakasız ayar değişiminde
                // çeviriyi kendiliğinden açmasın.
                session.pendingTranslationEnable = false
            } finally {
                savingPreference = false
            }
        }
    }

    Column(modifier) {
        WikiPageTools(session, preferences, assets, config, savingPreference || preferenceReadFailed, preferenceFailed,
            controller = toolsController,
            onSaveAppearance = { mode, zoom ->
                savePreference { store.setWikiReadingAppearance(mode, zoom) }
            },
            onToggleAds = {
                savePreference { store.setWikiAdBlockEnabled(host, !config.blockAds) }
            },
            // Dil seçimi sayfa araçlarından aynı küresel ayarlara yazılır:
            // Ayarlar'a gitmeye gerek kalmaz, iki yer de her zaman senkron kalır.
            onSelectSourceLanguage = { code ->
                savePreference { store.setTranslateSourceLanguage(code) }
            },
            onSelectTargetLanguage = { code ->
                savePreference { store.setTranslateTargetLanguage(code) }
            }
        )
        WikiFindBar(session)
        if (preferenceReadFailed) {
            Text(stringResource(R.string.wiki_tools_preferences_failed), modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.labelSmall)
            TextButton(colors = ButtonDefaults.textButtonColors(contentColor = KitsugiColors.TextPrimary), onClick = { session.generation++ }) { Text(stringResource(R.string.yenile)) }
        }
        if (assetFailed) {
            Text(stringResource(R.string.wiki_tools_assets_failed), modifier = Modifier.padding(12.dp))
            TextButton(colors = ButtonDefaults.textButtonColors(contentColor = KitsugiColors.TextPrimary), onClick = { session.generation++ }) { Text(stringResource(R.string.yenile)) }
        }

        // İlerleme çizgisi her zaman 2 dp yer kaplar: yükleme bitince çizgi kaybolurken
        // WebView'in yukarı sıçraması (içerik kayması) engellenir. Material bileşenin
        // 4 dp yüksekliği 2 dp'lik alandan taşacağı için ince çubuk elle çizilir.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(accentColor.copy(alpha = 0.16f))
        ) {
            if (session.progress < 100 && !session.failed && !assetFailed) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((session.progress.coerceIn(0, 100)) / 100f)
                        .background(accentColor)
                )
            }
        }
        if (session.failed) {
            // Keep the page under the message: a site-provided 403/404/challenge can still
            // contain useful information. Never replace it with an endless spinner.
            Text(
                stringResource(R.string.wiki_original_load_failed),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall
            )
            Row {
                TextButton(colors = ButtonDefaults.textButtonColors(contentColor = KitsugiColors.TextPrimary), onClick = session::reload) { Text(stringResource(R.string.yenile)) }
                TextButton(colors = ButtonDefaults.textButtonColors(contentColor = KitsugiColors.TextPrimary), onClick = onUseReader) { Text(stringResource(R.string.wiki_simple_reader)) }
            }
        }
        val loadedAssets = assets
        if (loadedAssets != null && preferences != null) key(session, session.generation) {
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { context ->
                    val container = FrameLayout(context).apply { setBackgroundColor(currentBackground) }
                    var allocatedView: WebView? = null
                    try {
                        val engine = WebView(context).also { allocatedView = it }.apply {
                            val ownedView = this
                            setBackgroundColor(currentBackground)
                            keepScreenOn = session.keepScreenOn
                            val tools = WikiPageEnhancements(context, this, session, loadedAssets, currentConfig)
                            session.enhancements = tools
                            tools.setAppearance(currentPageMode)
                            tools.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                            var released = false
                            var rendererGone = false
                            val desktopAtCreation = session.desktop
                            var restoreScroll = session.history != null
                            // Used by onRelease below, including the dead-renderer path.
                            tag = {
                                if (!released) {
                                    released = true
                                    tools.close()
                                    if (session.enhancements === tools) session.enhancements = null
                                    if (!rendererGone) {
                                        session.scrollX = scrollX
                                        session.scrollY = scrollY
                                        session.history = Bundle().also { saveState(it) }
                                        session.historyDesktop = desktopAtCreation
                                        stopLoading()
                                    }
                                    if (session.view === ownedView) session.view = null
                                    setFindListener(null)
                                    session.findOpen = false
                                    session.findQuery = ""
                                    webChromeClient = null
                                    webViewClient = WebViewClient()
                                    removeAllViews()
                                    destroy()
                                }
                            }
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = false
                                allowContentAccess = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                safeBrowsingEnabled = true
                                javaScriptCanOpenWindowsAutomatically = false
                                setSupportMultipleWindows(false)
                                mediaPlaybackRequiresUserGesture = true
                                setGeolocationEnabled(false)
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                this.textZoom = currentTextZoom
                                if (session.desktop) {
                                    userAgentString = WikiOriginalPagePolicy.desktopUserAgent(userAgentString)
                                }
                                if (Build.VERSION.SDK_INT >= 29) {
                                    @Suppress("DEPRECATION")
                                    setForceDark(WebSettings.FORCE_DARK_OFF)
                                }
                                if (Build.VERSION.SDK_INT >= 33) isAlgorithmicDarkeningAllowed = false
                            }
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                            setFindListener { active, total, complete ->
                                if (!released && session.findOpen && session.findQuery.isNotBlank() && complete) {
                                    session.findMatches = total
                                    session.findActiveMatch = if (total > 0) active + 1 else 0
                                }
                            }
                            setDownloadListener { _, _, _, _, _ -> currentBlockedLink() }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView, newProgress: Int) {
                                    if (!released) session.progress = newProgress
                                }
                                override fun onReceivedTitle(view: WebView, title: String?) {
                                    if (!released) session.title = title
                                }
                                // Permission requests and file chooser stay denied by WebChromeClient.
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                                    tools.intercept(request) ?: super.shouldInterceptRequest(view, request)

                                private fun update(view: WebView, url: String?) {
                                    if (released || rendererGone) return
                                    session.canGoBack = view.canGoBack()
                                    session.canGoForward = view.canGoForward()
                                    if (url != null && WikiOriginalPagePolicy.destination(url) == WikiOriginalPagePolicy.Destination.WIKI) {
                                        session.url = url
                                        currentLocationChanged(url)
                                    }
                                }

                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    if (!request.isForMainFrame) return false
                                    return when (WikiOriginalPagePolicy.destination(request.url.toString())) {
                                        // 2026-10-21: kullanıcı dokunuşuyla gelen wiki bağlantıları
                                        // YENİ SEKMEDE açılır (Chrome davranışı). Aynı belge içi
                                        // çapalar (#bölüm) ve JS/form yönlendirmeleri yerinde kalır;
                                        // aksi hâlde her otomatik yönlendirme sekme üretirdi.
                                        WikiOriginalPagePolicy.Destination.WIKI -> {
                                            val target = request.url.toString()
                                            val currentDocument = view.url?.substringBefore('#')
                                            val destinationDocument = target.substringBefore('#')
                                            when {
                                                !request.hasGesture() -> false
                                                currentDocument != null &&
                                                    currentDocument == destinationDocument -> false
                                                else -> currentOpenLinkInNewTab(target)
                                            }
                                        }
                                        WikiOriginalPagePolicy.Destination.EXTERNAL -> {
                                            if (request.hasGesture()) currentExternalLink(request.url.toString())
                                            else currentBlockedLink()
                                            true
                                        }
                                        WikiOriginalPagePolicy.Destination.BLOCKED -> {
                                            currentBlockedLink()
                                            true
                                        }
                                    }
                                }

                                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                    if (released) return
                                    session.failed = false
                                    session.progress = 0
                                    session.pageCommitted = false
                                    setBackgroundColor(currentBackground)
                                    session.closeFind()
                                    tools.pageStarted(url)
                                    update(view, url)
                                }

                                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                    update(view, url)
                                }

                                override fun onPageCommitVisible(view: WebView, url: String?) {
                                    if (!released && !rendererGone) {
                                        session.pageCommitted = true
                                        tools.documentAvailable(url)
                                    }
                                }

                                override fun onPageFinished(view: WebView, url: String?) {
                                    if (released || rendererGone) return
                                    session.progress = 100
                                    session.pageCommitted = true
                                    update(view, url)
                                    // Görünüm düzeni (viewport + ekrana sığdırma) WikiPageEnhancements
                                    // içinde, belge görünür olduğu anda uygulanır; burada ikinci kez
                                    // dokunulmaz. Böylece sayfa 1200 px düzende yatay kaydırmaya düşmez.
                                    tools.documentAvailable(url)
                                    if (restoreScroll || session.restoreAfterReload) {
                                        restoreScroll = false
                                        session.restoreAfterReload = false
                                        view.post {
                                            if (!released && !rendererGone) view.scrollTo(session.scrollX, session.scrollY)
                                        }
                                    }
                                    CookieManager.getInstance().flush()
                                }

                                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                    if (!released && request.isForMainFrame) session.failed = true
                                }

                                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                                    if (!released && request.isForMainFrame) session.failed = true
                                }

                                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                                    session.failed = true
                                    session.progress = 100
                                    handler.cancel() // Never bypass invalid certificates.
                                }

                                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                                    rendererGone = true
                                    session.failed = true
                                    session.progress = 100
                                    session.history = null
                                    session.canGoBack = false
                                    session.canGoForward = false
                                    session.view = null
                                    // Remove from the hierarchy immediately; retry creates a fresh engine.
                                    (view.parent as? ViewGroup)?.removeView(view)
                                    @Suppress("UNCHECKED_CAST")
                                    (view.tag as? (() -> Unit))?.invoke()
                                    return true
                                }
                            }
                            session.view = this
                            session.pageCommitted = false
                            session.failed = false
                            session.progress = 0
                            val restored = session.history?.let { restoreState(it) } != null
                            if (!restored) {
                                if (WikiOriginalPagePolicy.destination(session.url) == WikiOriginalPagePolicy.Destination.WIKI) {
                                    loadUrl(session.url)
                                } else session.failed = true
                            } else if (session.historyDesktop != null && session.historyDesktop != desktopAtCreation) {
                                reload()
                            }
                        }
                        container.tag = engine.tag
                        container.addView(engine)
                    } catch (_: RuntimeException) {
                        // Devices without a working WebView provider can still use the native reader.
                        runCatching {
                            allocatedView?.let { view ->
                                @Suppress("UNCHECKED_CAST")
                                val release = view.tag as? (() -> Unit)
                                if (release != null) release() else view.destroy()
                            }
                        }
                        session.enhancements?.close()
                        session.enhancements = null
                        session.view = null
                        session.failed = true
                        session.progress = 100
                    }
                    container
                },
                onRelease = { view ->
                    @Suppress("UNCHECKED_CAST")
                    (view.tag as? (() -> Unit))?.invoke()
                    view.removeAllViews()
                }
            )
            DisposableEffect(session, lifecycle) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_PAUSE -> {
                            session.enhancements?.setForeground(false)
                            session.view?.onPause()
                        }
                        Lifecycle.Event.ON_RESUME -> {
                            session.view?.onResume()
                            session.enhancements?.setForeground(true)
                        }
                        else -> Unit
                    }
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }
        }
    }
}

