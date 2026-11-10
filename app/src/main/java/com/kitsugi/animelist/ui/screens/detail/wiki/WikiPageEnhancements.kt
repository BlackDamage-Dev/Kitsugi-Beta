package com.kitsugi.animelist.ui.screens.detail.wiki

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import com.kitsugi.animelist.data.local.TranslationManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Semaphore
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

internal class WikiPageAssets private constructor(
    val translator: String,
    val cosmetic: String,
    val rules: WikiAdBlockRules,
    val license: String,
    val filterRevision: String,
    val appearance: String,
    /** Sayfayı ekrana sığdıran, yıkıcı olmayan düzen katmanı. */
    val fit: String
) {
    companion object {
        @Volatile private var cached: WikiPageAssets? = null
        /** Call on Dispatchers.IO, before constructing the WebView (no requests escape startup filtering). */
        @Synchronized fun load(context: Context): WikiPageAssets = cached ?: WikiPageAssets(
            context.assets.open("wiki/dom-translator.js").bufferedReader().use { it.readText() },
            context.assets.open("wiki/adblock-cosmetic.js").bufferedReader().use { it.readText() },
            WikiAdBlockRules.fromHosts(context.assets.open("wiki/stevenblack-hosts.txt").bufferedReader().use { it.readText() }),
            context.assets.open("wiki/STEVENBLACK-LICENSE.txt").bufferedReader().use { it.readText() },
            JSONObject(context.assets.open("wiki/hosts-source.json").bufferedReader().use { it.readText() }).getString("commit").take(12),
            context.assets.open("wiki/page-appearance.js").bufferedReader().use { it.readText() },
            context.assets.open("wiki/page-fit.js").bufferedReader().use { it.readText() }
        ).also { cached = it }
    }
}

/** Bir çeviri partisinin sonucu; uygulama (DOM'a yazma) ağdan bağımsız ilerler. */
private class TranslatedBatch(val items: List<WikiDomText>, val results: List<WikiDomResult>)

/** Main-thread DOM coordinator + immutable off-thread request matcher. No JavascriptInterface.
 * Remote documents cannot invoke native APIs. All page text is pulled via bounded evaluateJavascript
 * results and goes to the existing in-app Google service only when automatic/manual translation is on.
 *
 * Translation is pipelined: up to [IN_FLIGHT_BATCHES] DOM batches are in flight at once and results
 * are written as they arrive. Waiting on the network therefore never blocks the WebView, and a
 * batch that fails (or is deferred because the user is scrolling) does not stall the rest.
 */
internal class WikiPageEnhancements(
    context: Context,
    private val view: WebView,
    private val session: WikiOriginalSession,
    private val assets: WikiPageAssets,
    initialConfig: WikiPageToolConfig
) {
    private companion object {
        /** Uçuştaki çeviri partisi sayısı: global 4 izinli çeviri kapısıyla aynı. */
        const val IN_FLIGHT_BATCHES = 4
        /** DOM köprüsünün tek turda verebileceği en fazla düğüm (dom-translator.js ile aynı). */
        const val MAX_BATCH_ITEMS = 16
        /** Çevrilecek metin kalmadığında yoklama aralığı. */
        const val IDLE_POLL_MS = 450L
        /** Belge hâlâ taranıyorsa daha sık yokla. */
        const val SCANNING_POLL_MS = 90L
        /** Kaydırma sırasında ertelenen DOM yazımını yeniden deneme aralığı. */
        const val DEFERRED_RETRY_MS = 120L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val translator by lazy { TranslationManager(context.applicationContext) }
    private val blocking = AtomicBoolean(initialConfig.blockAds)
    private val blocked = AtomicInteger(0)
    private val inFlight = AtomicInteger(0)
    private var config = initialConfig
    private var job: Job? = null
    private var fitJob: Job? = null
    private var runGeneration = 0
    private var appearanceJob: Job? = null
    private var appearanceMode = "SITE"
    private var appearanceGeneration = 0
    private var documentId = UUID.randomUUID().toString()
    private var ready = false
    private var foreground = true
    @Volatile private var disposed = false
    @Volatile private var wikiDocument =
        WikiOriginalPagePolicy.destination(session.url) == WikiOriginalPagePolicy.Destination.WIKI

    /** WebView invokes this on background threads. No UI, IO, regex or JS evaluation here. */
    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        if (disposed || !wikiDocument || !blocking.get() ||
            !assets.rules.shouldBlock(request.url.toString(), request.isForMainFrame)) return null
        blocked.incrementAndGet()
        return WebResourceResponse("text/plain", "UTF-8", 204, "No Content",
            mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(ByteArray(0)))
    }

    fun pageStarted(url: String?) {
        job?.cancel()
        fitJob?.cancel()
        appearanceJob?.cancel()
        session.pageThemeSupported = true
        documentId = UUID.randomUUID().toString()
        ready = false
        wikiDocument = url != null && WikiOriginalPagePolicy.destination(url) == WikiOriginalPagePolicy.Destination.WIKI
        blocking.set(android.net.Uri.parse(url.orEmpty()).host.orEmpty().lowercase(java.util.Locale.ROOT) !in config.disabledAdHosts)
        blocked.set(0)
        inFlight.set(0)
        session.blockedRequests = 0
        session.translating = false
        session.translatedNodes = 0
        session.translationFailures = 0
        session.translationLimited = false
        session.articleAvailable = false
        session.enhancementError = false
        val documentUrl = url?.substringBefore('#')
        if (session.translationDocumentUrl != null && session.translationDocumentUrl != documentUrl) {
            session.translationOverride = null
            config = config.copy(translate = config.automaticTranslate)
        }
        session.translationDocumentUrl = documentUrl
    }

    fun documentAvailable(url: String?) {
        if (disposed || url == null || WikiOriginalPagePolicy.destination(url) != WikiOriginalPagePolicy.Destination.WIKI) return
        wikiDocument = true
        if (ready) { applyAppearance(); applyFit(); return } // Do not erase originals/cache on duplicate finish.
        ready = true
        applyAppearance()
        applyFit()
        restart()
    }

    fun setAppearance(mode: String) {
        if (appearanceMode == mode) return
        appearanceMode = mode
        applyAppearance()
    }

    private fun applyAppearance() {
        val generation = ++appearanceGeneration
        appearanceJob?.cancel()
        if (!ready || disposed || !foreground) return
        val id = documentId
        val mode = appearanceMode
        appearanceJob = scope.launch {
            try {
                evaluate(assets.appearance)
                val result = json("window.__kitsugiWikiAppearanceV1(${JSONObject.quote(mode)})")
                if (!disposed && id == documentId && generation == appearanceGeneration) {
                    session.pageThemeSupported = result.optBoolean("supported")
                    val canvas = result.optString("canvas", "#ffffff")
                    if (canvas.matches(Regex("#[0-9a-fA-F]{6}"))) {
                        view.setBackgroundColor(android.graphics.Color.parseColor(canvas))
                    }
                }
            } catch (_: TimeoutCancellationException) {
                if (!disposed && id == documentId && generation == appearanceGeneration) session.pageThemeSupported = false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (!disposed && id == documentId && generation == appearanceGeneration) session.pageThemeSupported = false
            }
        }
    }

    /**
     * Görünüm düzeni: mobilde site kendi viewport'unu korur, yalnızca ekrandan geniş
     * belgeler düzeltilir; masaüstünde 1200 px düzen ekrana sığdırılır. Sayfa metnine,
     * bağlantılara veya site betiklerine dokunulmaz.
     */
    private fun applyFit() {
        if (disposed || !ready) return
        fitJob?.cancel()
        fitJob = scope.launch {
            try {
                evaluate(assets.fit)
                evaluate("window.__kitsugiWikiFitV1(${session.desktop});")
            } catch (_: TimeoutCancellationException) {
                // Yavaş DOM: düzen katmanı bir sonraki görünürlük olayında yeniden denenir.
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Düzen katmanı çalışmasa bile sayfa okunabilir kalır; kullanıcıya hata gösterilmez.
            }
        }
    }

    fun configure(next: WikiPageToolConfig) {
        if (disposed || next == config) return
        val reloadAds = blocking.get() != next.blockAds
        val retry = config.retry != next.retry
        config = next
        blocking.set(next.blockAds)
        if (reloadAds && wikiDocument) {
            job?.cancel()
            ready = false
            // Previously blocked scripts cannot be resurrected with CSS alone.
            session.scrollX = view.scrollX
            session.scrollY = view.scrollY
            session.restoreAfterReload = true
            view.reload()
        } else restart(retry)
    }

    fun setForeground(value: Boolean) {
        if (foreground == value) return
        foreground = value
        if (value) { applyAppearance(); applyFit(); restart() }
        else { runGeneration++; job?.cancel(); fitJob?.cancel(); appearanceJob?.cancel(); session.translating = false }
    }

    private fun restart(retry: Boolean = false) {
        val run = ++runGeneration
        job?.cancel()
        session.translating = false
        if (!ready || disposed || !foreground) return
        val id = documentId
        val settings = config
        job = scope.launch {
            try {
                evaluate(assets.translator)
                evaluate(assets.cosmetic)
                evaluate("window.__kitsugiWikiDomV1.release(${JSONObject.quote(id)});")
                val status = json("window.__kitsugiWikiDomV1.configure(${JSONObject.quote(id)}," +
                    "${JSONObject.quote(settings.target)},${settings.translate},$retry,${JSONObject.quote(settings.source)})")
                updateStatus(status)
                session.documentTextRevision++
                session.enhancementError = false
                translatePipeline(id, settings, run)
            } catch (_: TimeoutCancellationException) {
                if (!disposed && run == runGeneration && id == documentId) session.enhancementError = true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A broken/unavailable document bridge is not a successful translation.
                if (!disposed && run == runGeneration && id == documentId) session.enhancementError = true
            } finally {
                if (!disposed && run == runGeneration && id == documentId && config == settings) session.translating = false
            }
        }
    }

    /**
     * DOM metinlerini boru hattı hâlinde çevirir: ağ yanıtı beklerken yeni partiler alınır ve
     * hazır olan sonuçlar hemen yazılır. Tek bir yavaş/başarısız istek artık tüm sayfayı
     * sırayla kilitlemez; kaydırma sırasında da çeviri akmaya devam eder.
     */
    private suspend fun translatePipeline(id: String, settings: WikiPageToolConfig, run: Int) = coroutineScope {
        val slots = Semaphore(IN_FLIGHT_BATCHES)
        val results = Channel<TranslatedBatch>(Channel.UNLIMITED)

        suspend fun alive() = currentCoroutineContext().isActive && ready && id == documentId &&
            run == runGeneration && config == settings
        // Kozmetik reklam katmanı saniyede en fazla bir kez tazelenir; her turda JS çağırmak
        // kaydırma sırasında gereksiz ana iş parçacığı yükü üretiyordu.
        var adsRefreshedAt = 0L

        val applier = launch {
            for (batch in results) {
                if (!alive()) break
                applyResults(id, batch)
                if (inFlight.decrementAndGet() == 0) session.translating = false
            }
        }

        try {
            while (alive()) {
                session.blockedRequests = blocked.get()
                val now = System.currentTimeMillis()
                if (now - adsRefreshedAt >= 1000L) {
                    adsRefreshedAt = now
                    evaluate("window.__kitsugiWikiAdsV1(${settings.blockAds});")
                }
                if (!settings.translate) {
                    delay(1000)
                    continue
                }
                // Uçuştaki parti sayısı sınırlı: WebView, ağ ve kaydırma boğulmaz.
                if (!slots.tryAcquire()) {
                    delay(40)
                    continue
                }
                val batch = try {
                    json("window.__kitsugiWikiDomV1.take(${JSONObject.quote(id)})")
                } catch (error: TimeoutCancellationException) {
                    // Tek bir yavaş DOM turu boru hattını öldürmez; kısa bekleyip yeniden denenir.
                    slots.release()
                    delay(200)
                    continue
                } catch (error: Exception) {
                    slots.release()
                    throw error
                }
                updateStatus(batch.optJSONObject("status"))
                val items = decodeItems(batch.optJSONArray("items"))
                if (items.isEmpty()) {
                    slots.release()
                    // Tarama sürüyorsa hızlı, sayfa tükendiyse seyrek yokla (pil/CPU dostu).
                    val scanning = batch.optJSONObject("status")?.optBoolean("scanning") == true
                    delay(if (scanning) SCANNING_POLL_MS else IDLE_POLL_MS)
                    continue
                }
                inFlight.incrementAndGet()
                session.translating = true
                launch {
                    val rows = try {
                        WikiDomTranslationBatch.translate(
                            items,
                            cached = { text ->
                                translator.cachedWebPageText(text, settings.source, settings.target)
                            },
                            store = { pairs ->
                                translator.storeWebPageTexts(pairs, settings.source, settings.target)
                            }
                        ) { text ->
                            translator.translateWebPageText(text, settings.source, settings.target)
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        // Parti hatası boru hattını durdurmaz; düğümler sonra yeniden denenir.
                        items.map { WikiDomResult(it, null) }
                    } finally {
                        slots.release()
                    }
                    results.trySend(TranslatedBatch(items, rows))
                }
            }
        } finally {
            results.close()
        }
        applier.join()
    }

    /** Sonuçları DOM'a yazar. Kaydırma sürerken yazım ertelenir; sonuç kaybolmaz, yeniden denenir. */
    private suspend fun applyResults(id: String, batch: TranslatedBatch) {
        val translated = HashMap<Int, String?>(batch.results.size)
        batch.results.forEach { translated[it.item.id] = it.translated }
        val values = JSONArray().apply {
            batch.items.forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id)
                    put("rev", item.revision)
                    put("source", item.text)
                    put("text", translated[item.id] ?: JSONObject.NULL)
                })
            }
        }
        while (currentCoroutineContext().isActive) {
            val applied = try {
                json("window.__kitsugiWikiDomV1.apply(${JSONObject.quote(id)},$values)")
            } catch (error: TimeoutCancellationException) {
                // Yazma turu zaman aşımına düştü: kayıtları "bekliyor" durumundan çıkar ki
                // sonraki turda yeniden istenebilsinler (sonsuz bekleme olmasın).
                runCatching { evaluate("window.__kitsugiWikiDomV1.release(${JSONObject.quote(id)});") }
                return
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                return
            }
            updateStatus(applied.optJSONObject("status"))
            if (!applied.optBoolean("deferred")) {
                // Results are JSON data, never HTML or executable translator output.
                if (!applied.optBoolean("stale")) session.documentTextRevision++
                return
            }
            delay(DEFERRED_RETRY_MS)
        }
    }

    private fun updateStatus(status: JSONObject?) {
        if (status == null) return
        session.translatedNodes = status.optInt("translated").coerceIn(0, 6000)
        session.translationFailures = status.optInt("failed").coerceIn(0, 6000)
        session.translationLimited = status.optBoolean("limited")
        session.articleAvailable = status.optBoolean("available")
    }

    private fun decodeItems(array: JSONArray?): List<WikiDomText> {
        if (array == null) return emptyList()
        require(array.length() <= MAX_BATCH_ITEMS)
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val text = item.getString("text")
            require(text.length in 1..8000)
            WikiDomText(item.getInt("id"), item.getInt("rev"), text)
        }.also { require(it.map { row -> row.id }.distinct().size == it.size) }
    }

    private suspend fun json(expression: String): JSONObject = JSONObject(evaluate("JSON.stringify($expression)").let { raw ->
        // evaluateJavascript JSON-encodes JS strings a second time.
        JSONArray("[$raw]").getString(0)
    })

    private suspend fun evaluate(script: String): String = withTimeout(7000) {
        suspendCancellableCoroutine { continuation ->
            if (disposed) { continuation.cancel(); return@suspendCancellableCoroutine }
            view.evaluateJavascript(script) { value ->
                if (continuation.isActive) {
                    if (value != null && value.length <= 180_000) continuation.resume(value)
                    else continuation.resume("null")
                }
            }
        }
    }

    fun close() {
        if (session.enhancements === this) session.translating = false
        disposed = true
        blocking.set(false)
        scope.cancel()
    }
}
