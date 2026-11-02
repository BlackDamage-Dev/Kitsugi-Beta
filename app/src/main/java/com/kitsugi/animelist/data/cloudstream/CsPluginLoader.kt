package com.kitsugi.animelist.data.cloudstream

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.util.Log
import com.kitsugi.animelist.data.cloudstream.diag.CsTrace
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.extractorApis
import dalvik.system.PathClassLoader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile
import okhttp3.Request
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object CsPluginLoader {

    private const val TAG = "CsPluginLoader"

    /**
     * Ayrı teşhis tag'i — tüm CS3 eklenti yükleme/hata olaylarını ERROR seviyesinde loglar.
     * Logcat'te izole görmek için: adb logcat -s PLUGIN_DIAG
     * Tüm CS3 logları için: adb logcat | findstr "PLUGIN_DIAG\|CS_ERROR\|CS_LOAD"
     */
    private const val PLUGIN_DIAG = "PLUGIN_DIAG"

    // Matches BasePlugin.Manifest structure from cloudstream-master
    private data class PluginManifest(
        @SerializedName("pluginClassName") val pluginClassName: String?,
        @SerializedName("pluginClass")     val pluginClass: String?,
        @SerializedName("internalName")    val internalName: String?,
        @SerializedName("name")            val name: String?,
        @SerializedName("version")         val version: Int? = null,
        @SerializedName("requiresResources") val requiresResources: Boolean = false
    )

    val loadedPlugins = java.util.concurrent.ConcurrentHashMap<String, BasePlugin>()
    val loadedPluginIds = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())
    private val loadMutex = Mutex()
    private val loadErrors = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val loadedManifestVersions = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /** Consume the most recent loader failure so the in-app diagnostic report can classify it. */
    fun takeLastLoadError(scraperId: String): String? = loadErrors.remove(scraperId)

    /**
     * Returns the manifest version read from the actual loaded `.cs3` archive for an API's source.
     * `null` means the API did not come through this loader or its manifest omitted `version`.
     */
    fun manifestVersionForSource(sourcePlugin: String?): Int? {
        if (sourcePlugin.isNullOrBlank()) return null
        val pluginId = runCatching { File(sourcePlugin).nameWithoutExtension }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: return null
        return manifestVersionForPluginId(pluginId)
    }

    /** Returns the actual loaded manifest version for a known extension id, if available. */
    fun manifestVersionForPluginId(pluginId: String?): Int? {
        if (pluginId.isNullOrBlank()) return null
        val normalizedId = runCatching { File(pluginId).nameWithoutExtension }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: return null
        return loadedManifestVersions[normalizedId]
            ?: loadedManifestVersions.entries.firstOrNull { it.key.equals(normalizedId, ignoreCase = true) }?.value
    }

    private fun recordLoadError(scraperId: String, message: String) {
        loadErrors[scraperId] = message.take(500)
    }

    private fun recordLoadErrorIfAbsent(scraperId: String, message: String) {
        loadErrors.putIfAbsent(scraperId, message.take(500))
    }

    /**
     * CS3 bytecode links against the host CloudStream/Kotlinx APIs by their original package names.
     * Keep the loader parent tied to the classloader that defined MainAPI rather than an arbitrary
     * Context wrapper's loader, then verify the shared runtime surface before plugin code runs.
     */
    private fun createPluginClassLoader(context: Context, cs3File: File): PathClassLoader {
        val hostLoader = MainAPI::class.java.classLoader
            ?: context.applicationContext.classLoader
            ?: context.classLoader
        return PathClassLoader(cs3File.absolutePath, hostLoader)
    }

    private fun verifySharedPluginRuntime(scraperId: String, loader: ClassLoader) {
        val requiredClasses = listOf(
            "com.lagradost.cloudstream3.MainAPI",
            "kotlinx.serialization.KSerializer",
            "kotlinx.serialization.json.Json",
        )
        requiredClasses.forEach { className ->
            try {
                loader.loadClass(className)
            } catch (t: Throwable) {
                val message = "CS3 host runtime class is not visible: $className (${t.javaClass.simpleName})"
                recordLoadErrorIfAbsent(scraperId, message)
                Log.e(PLUGIN_DIAG, "❌ CS3 ABI EKSİK: $scraperId — $message", t)
                CsTrace.error(scraperId, "runtime", message, t)
            }
        }
    }

    // ─── Public settings helpers ──────────────────────────────────────────────

    /**
     * Returns the loaded [Plugin] instance for the given scraperId, or null if the
     * plugin is not loaded or does not extend [Plugin] (only BasePlugin).
     * The [Context] is needed to resolve the .cs3 file path.
     */
    fun getPluginInstance(scraperId: String, context: Context): Plugin? {
        val cs3File = File(context.filesDir, "cs_extensions/$scraperId.cs3")
        return loadedPlugins[cs3File.absolutePath] as? Plugin
    }

    /**
     * Returns true if the plugin is loaded AND has registered an [openSettings] callback.
     * Use this to decide whether to show the ⚙️ settings button in the UI.
     */
    fun hasSettings(scraperId: String, context: Context): Boolean {
        return getPluginInstance(scraperId, context)?.openSettings != null
    }

    private fun unloadExtensionInternal(context: Context, scraperId: String) {
        val cs3File = File(context.filesDir, "cs_extensions/$scraperId.cs3")
        val absolutePath = cs3File.absolutePath
        loadedManifestVersions.remove(scraperId)
        Log.i(TAG, "Unloading extension: $scraperId ($absolutePath)")

        // 1. Call beforeUnload() on the plugin instance
        val plugin = loadedPlugins[absolutePath]
        if (plugin != null) {
            try {
                plugin.beforeUnload()
                Log.d(TAG, "Called beforeUnload() on plugin instance for $scraperId")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to run beforeUnload for $scraperId: ${e.message}", e)
            }
            loadedPlugins.remove(absolutePath)
        }
        loadedPluginIds.value = loadedPluginIds.value - scraperId

        // 2. Remove all registered APIs thread-safely
        try {
            val prevApis = APIHolder.allProviders.filter { it.sourcePlugin == absolutePath }
            prevApis.forEach { api ->
                try {
                    APIHolder.removePluginMapping(api)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to remove plugin mapping for API: ${api.name}", e)
                }
            }
            APIHolder.allProviders.removeAll { it.sourcePlugin == absolutePath }
            Log.d(TAG, "Cleared API providers for $scraperId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clean APIHolder providers for $scraperId: ${e.message}", e)
        }

        // 3. Clear orphaned extractorApis thread-safely
        try {
            extractorApis.removeAll { it.sourcePlugin == absolutePath }
            Log.d(TAG, "Cleared orphaned extractorApis for $scraperId")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clean extractorApis for $scraperId: ${e.message}")
        }

        // 4. Clear VideoClickActions thread-safely
        try {
            com.lagradost.cloudstream3.actions.VideoClickActionHolder.allVideoClickActions.removeAll { action ->
                action.sourcePlugin == absolutePath
            }
            Log.d(TAG, "Cleared VideoClickActions for $scraperId")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clean VideoClickActions for $scraperId: ${e.message}")
        }
    }

    /**
     * Unloads a loaded extension fully, clearing its APIs, extractors, and actions
     * to prevent memory leaks and duplicate registrations.
     */
    suspend fun unloadExtension(context: Context, scraperId: String) = withContext(Dispatchers.IO) {
        loadMutex.withLock {
            unloadExtensionInternal(context, scraperId)
        }
    }

    private fun File.sha256(): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        this.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead = input.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    // ─── Download ─────────────────────────────────────────────────────────────

    fun downloadExtension(
        context: Context,
        scraperId: String,
        urlString: String,
        expectedHash: String? = null,
        forceDownload: Boolean = false
    ): Boolean {
        val extensionDir = File(context.filesDir, "cs_extensions")
        if (!extensionDir.exists()) extensionDir.mkdirs()

        val tempFile = File(extensionDir, "$scraperId.cs3.tmp")
        val targetFile = File(extensionDir, "$scraperId.cs3")

        val candidateUrls = com.kitsugi.animelist.utils.CloudstreamUrlHelper.getCandidateDownloadUrls(scraperId, urlString)

        for ((index, candidate) in candidateUrls.withIndex() ) {
            val downloadUrl = if (forceDownload) com.kitsugi.animelist.utils.CloudstreamUrlHelper.withCacheBuster(candidate) else candidate
            Log.d(TAG, "Attempting plugin download ($scraperId) [${index + 1}/${candidateUrls.size}] from: $downloadUrl")

            try {
                if (tempFile.exists()) {
                    try { tempFile.setWritable(true) } catch (_: Exception) {}
                    tempFile.delete()
                }

                val requestBuilder = Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36 Kitsugi/2.4")
                    // api.github.com/.../contents/ uç noktası varsayılan olarak base64 JSON döner;
                    // ham dosya (ikili .cs3) için bu başlık şarttır.
                    .header(
                        "Accept",
                        if (downloadUrl.contains("api.github.com/repos/", ignoreCase = true)) {
                            "application/vnd.github.raw"
                        } else {
                            "*/*"
                        }
                    )

                if (forceDownload) {
                    requestBuilder.cacheControl(okhttp3.CacheControl.FORCE_NETWORK)
                    requestBuilder.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                    requestBuilder.addHeader("Pragma", "no-cache")
                }

                val request = requestBuilder.build()

                val callSuccess = com.kitsugi.animelist.core.network.KitsugiHttpClient.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Download attempt failed with HTTP ${response.code} for $downloadUrl")
                        false
                    } else {
                        response.body?.byteStream()?.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        tempFile.exists() && tempFile.length() > 0
                    }
                }

                if (!callSuccess) {
                    if (tempFile.exists()) {
                        try { tempFile.setWritable(true) } catch (_: Exception) {}
                        tempFile.delete()
                    }
                    continue
                }

                Log.d(TAG, "Downloaded ${tempFile.length()} bytes to temp file from candidate: $downloadUrl")

                // Verify SHA-256 hash when the repository supplies one before installing the CS3.
                if (expectedHash != null) {
                    val calculatedHash = tempFile.sha256()
                    val cleanExpected = expectedHash.trim().removePrefix("sha256-").removePrefix("SHA256-")
                    if (!calculatedHash.equals(cleanExpected, ignoreCase = true)) {
                        // A valid ZIP is not enough: a stale/wrong artefact can still load
                        // successfully and silently hide the provider fix the manifest points
                        // to. Reject this candidate and let the mirror chain try the next one.
                        Log.e(TAG, "Hash verification mismatch for $scraperId. Expected: $expectedHash, got: $calculatedHash — candidate rejected")
                        if (tempFile.exists()) {
                            try { tempFile.setWritable(true) } catch (_: Exception) {}
                            tempFile.delete()
                        }
                        continue
                    } else {
                        Log.d(TAG, "Hash verification passed for $scraperId")
                    }
                }

                // Verify it is a valid ZIP (cs3 = ZIP-wrapped DEX)
                val isValidZip = try { ZipFile(tempFile).use { true } } catch (_: Exception) { false }
                if (!isValidZip) {
                    Log.w(TAG, "Downloaded file is NOT a valid ZIP/CS3 from $downloadUrl: $scraperId — trying next candidate")
                    if (tempFile.exists()) {
                        try { tempFile.setWritable(true) } catch (_: Exception) {}
                        tempFile.delete()
                    }
                    continue
                }

                // Atomic move to target
                if (targetFile.exists()) {
                    try { targetFile.setWritable(true) } catch (_: Exception) {}
                    targetFile.delete()
                }
                val renameSuccess = tempFile.renameTo(targetFile)
                if (!renameSuccess) {
                    try {
                        tempFile.inputStream().use { input ->
                            targetFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        tempFile.delete()
                    } catch (copyEx: Exception) {
                        Log.e(TAG, "Failed to copy temp file to target file: $scraperId", copyEx)
                        if (tempFile.exists()) {
                            try { tempFile.setWritable(true) } catch (_: Exception) {}
                            tempFile.delete()
                        }
                        continue
                    }
                }

                // MUST be read-only for Android 10+ DEX security policy
                targetFile.setReadOnly()
                // Yeni dosya yazıldı: içerik parmak izi önbelleği geçersiz olmalı, aksi halde
                // bir sonraki güncelleme denetimi eski hash'i "güncel" sanıp güncellemeyi atlar.
                CsInstalledFingerprint.invalidate(context, scraperId)
                Log.d(TAG, "Plugin $scraperId downloaded, verified, and installed atomicaly OK from $downloadUrl")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Exception downloading candidate $downloadUrl for $scraperId: ${e.message}")
                if (tempFile.exists()) {
                    try { tempFile.setWritable(true) } catch (_: Exception) {}
                    tempFile.delete()
                }
            }
        }

        Log.e(TAG, "All ${candidateUrls.size} candidate download URLs failed for plugin $scraperId (original: $urlString)")
        return false
    }

    // ─── Load ─────────────────────────────────────────────────────────────────

    /**
     * Mirrors the exact approach used in Cloudstream's PluginManager.loadPlugin():
     *  1. Use PathClassLoader (not DexClassLoader) with the MainAPI-defining host loader as parent
     *     so CloudStream APIs and shared runtime dependencies remain visible to plugin DEX files.
     *  2. Read manifest.json via loader.getResourceAsStream() — same as original.
     *  3. Instantiate the plugin class and call load(context) or load().
     */
    suspend fun loadExtension(context: Context, scraperId: String, forceReload: Boolean = false): List<MainAPI> = withContext(Dispatchers.IO) {
        loadMutex.withLock {
            loadErrors.remove(scraperId)
            val cs3File = File(context.filesDir, "cs_extensions/$scraperId.cs3")
        val absolutePath = cs3File.absolutePath

        if (!cs3File.exists()) {
            recordLoadError(scraperId, "FileNotFoundException: CS3 file not found: ${cs3File.absolutePath}")
            Log.e(TAG, "Plugin file not found: ${cs3File.absolutePath}")
            Log.e(PLUGIN_DIAG, "❌ CS3 DOSYA YOK: $scraperId → ${cs3File.absolutePath}")
            CsTrace.error(scraperId, "load", "CS3 dosyası yok: ${cs3File.absolutePath}")
            return@withLock emptyList()
        }

        if (!forceReload) {
            val alreadyLoaded = loadedPlugins.containsKey(absolutePath)
            if (alreadyLoaded) {
                val registeredApis = APIHolder.allProviders.filter {
                    it.sourcePlugin == absolutePath
                }
                if (registeredApis.isNotEmpty()) {
                    Log.d(TAG, "Plugin $scraperId is already loaded. Returning ${registeredApis.size} cached API provider(s).")
                    if (!loadedPluginIds.value.contains(scraperId)) {
                        loadedPluginIds.value = loadedPluginIds.value + scraperId
                    }

                    // ── Re-apply patches even on cache hit ──────────────────
                    // isAllowedVersion and RequestBlocker patches must be re-applied because
                    // the patch targets static/object-level state in the plugin's DEX which
                    // can be reset between app sessions or coroutine contexts.
                    val cachedPlugin = loadedPlugins[absolutePath]
                    val pluginLoader = cachedPlugin?.javaClass?.classLoader
                    if (pluginLoader != null) {
                        applyHelperPatches(pluginLoader, scraperId, cachedPlugin?.javaClass?.name ?: "")
                    }

                    return@withLock registeredApis
                }
            }
        }

        Log.d(TAG, "Loading extension $scraperId (${cs3File.length()} bytes)")
        Log.i(PLUGIN_DIAG, "⏳ CS3 YÜKLENİYOR: $scraperId (${cs3File.length()} bytes)")

        // Quick ZIP sanity-check before handing to classloader
        val isValidZip = try { ZipFile(cs3File).use { true } } catch (zipEx: Exception) {
            Log.e(PLUGIN_DIAG, "❌ CS3 ZIP BOZUK: $scraperId — ${zipEx.message}")
            CsTrace.error(scraperId, "load", "CS3 ZIP bozuk: ${zipEx.message}", zipEx)
            false
        }
        if (!isValidZip) {
            recordLoadError(scraperId, "ZipException: CS3 file is corrupt or not a plugin archive")
            Log.e(TAG, "CS3 file for $scraperId is not a valid ZIP — corrupt or not a DEX plugin. Re-install it.")
            Log.e(PLUGIN_DIAG, "❌ CS3 GEÇERSİZ ZIP: $scraperId — Yeniden yükleyin!")
            CsTrace.error(scraperId, "load", "CS3 geçersiz ZIP — yeniden yükleyin")
            return@withLock emptyList()
        }

        // Android 10+ requirement: file must be read-only before PathClassLoader opens it
        if (cs3File.canWrite()) {
            cs3File.setReadOnly()
        }

        try {
            // ── Step 1: PathClassLoader with the MainAPI-defining host loader ──
            // The parent must expose both CloudStream interfaces and shared runtime libraries
            // (including kotlinx.serialization) to dynamically loaded plugin DEX files.
            val loader = createPluginClassLoader(context, cs3File)
            verifySharedPluginRuntime(scraperId, loader)

            // ── Step 2: Full cleanup — clear previously registered APIs, extractors, and actions ──
            // Note: unloadExtension already handles extractorApis cleanup internally
            unloadExtensionInternal(context, scraperId)

            // ── Step 3: Read manifest.json from the ZIP ──────────────────────
            val manifest = readManifest(loader, cs3File, scraperId)
            if (manifest == null) {
                recordLoadError(scraperId, "IllegalStateException: manifest.json is missing or invalid")
                return@withLock emptyList()
            }
            if (manifest.version != null) {
                loadedManifestVersions[scraperId] = manifest.version
            } else {
                loadedManifestVersions.remove(scraperId)
            }

            val pluginClassName = manifest.pluginClassName ?: manifest.pluginClass
            if (pluginClassName.isNullOrBlank()) {
                recordLoadError(scraperId, "IllegalStateException: manifest has no pluginClassName/pluginClass")
                Log.e(TAG, "manifest.json for $scraperId has no pluginClassName or pluginClass field")
                Log.e(PLUGIN_DIAG, "❌ MANIFEST HATASI: $scraperId — pluginClassName/pluginClass alanı eksik!")
                CsTrace.error(scraperId, "load", "manifest.json pluginClassName/pluginClass alanı eksik")
                return@withLock emptyList()
            }

            // ── Step 4: Instantiate the plugin class ─────────────────────────
            Log.d(TAG, "Instantiating class: $pluginClassName version=${manifest.version}")
            val pluginClass = loader.loadClass(pluginClassName)
            
            // Override isAllowedVersion + RequestBlocker patches BEFORE instantiating the class.
            // Turkish plugins (like byayzen/kerimmkirac) evaluate version constraints inside
            // static/companion initializer blocks when newInstance() is invoked. If we patch
            // after newInstance(), mainUrl is already cleared/compromised.
            applyHelperPatches(loader, scraperId, pluginClassName)

            val pluginInstance = pluginClass.getDeclaredConstructor().newInstance() as BasePlugin
            pluginInstance.filename = cs3File.absolutePath
            loadedPlugins[cs3File.absolutePath] = pluginInstance

            // ── Step 5: Load dynamic resources if required (requiresResources=true) ──
            // Mirrors PluginManager.loadPlugin() L642-656
            if (manifest.requiresResources) {
                Log.d(TAG, "Loading dynamic resources for $scraperId (requiresResources=true)")
                try {
                    val assets = AssetManager::class.java.getDeclaredConstructor().newInstance()
                    val addAssetPath = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
                    addAssetPath.invoke(assets, cs3File.absolutePath)
                    @Suppress("DEPRECATION")
                    (pluginInstance as? Plugin)?.resources = Resources(
                        assets,
                        context.resources.displayMetrics,
                        context.resources.configuration
                    )
                    Log.d(TAG, "Dynamic resources loaded successfully for $scraperId")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load resources for $scraperId: ${e.message}")
                    // Non-fatal: continue loading even if resource injection fails
                }
            }

            // ── Step 6: Call load() — with Context if it's a full Plugin ─────
            // Cloudstream her zaman Activity context geçiyor load()'a.
            // Eklentiler openSettings içinde bu context'i capture ediyor,
            // bu yüzden Activity context şart (Fragment/startActivity için).
            // Timeout + runCatching: plugin'in load() veya asenkron başlattığı
            // kodların (örn. InatBox'ın runBlocking Jackson çağrısı) uygulamayı
            // çökertmesini engeller.
            // ── Step 6: Plugin load() — dedicated thread to avoid runBlocking deadlock ──
            // Bazı eklentiler (örn. Animeler.getDomain) load() içinde runBlocking kullanır.
            // Bu, withTimeout ile paylaşılan IO coroutine thread pool'unu bloke eder ve
            // APP_SCOUT_HANG (5s+ donma) oluşturur. Çözüm: load()'u ayrı bir thread'de
            // çalıştırmak; böylece runBlocking kendi thread'ini bloke eder, UI/IO pool'unu değil.
            val safeCtx = com.kitsugi.animelist.KitsugiApplication.getDynamicContext(
                com.kitsugi.animelist.KitsugiApplication.activeActivity ?: context
            )
            val loadResult = runCatching {
                // Her eklenti için yeni bir thread açıyoruz ki plugin'in runBlocking çağrısı
                // sadece bu thread'i bloke etsin, coroutine scheduler'ı değil.
                val pluginThread = kotlinx.coroutines.newSingleThreadContext("PluginLoad-$scraperId")
                try {
                    kotlinx.coroutines.withTimeout(30_000L) {
                        kotlinx.coroutines.withContext(pluginThread) {
                            if (pluginInstance is Plugin) {
                                pluginInstance.load(safeCtx)
                            } else {
                                pluginInstance.load()
                            }
                        }
                    }
                } finally {
                    pluginThread.close()
                }
            }
            if (loadResult.isFailure) {
                val loadErr = loadResult.exceptionOrNull()
                val errType = loadErr?.javaClass?.simpleName ?: "UnknownError"
                val errMsg  = loadErr?.message ?: "(mesaj yok)"
                recordLoadError(scraperId, "$errType: $errMsg")
                if (loadErr is kotlinx.coroutines.TimeoutCancellationException) {
                    Log.e(TAG, "Plugin $scraperId load() TIMEOUT (30s) — eklenti çok uzun süre engelledi. Kaldırılıyor.")
                    Log.e(PLUGIN_DIAG, "⏱️ CS3 TIMEOUT: $scraperId — load() 30 saniyede tamamlanamadı (runBlocking deadlock?)")
                    CsTrace.error(scraperId, "load", "load() 30 sn içinde bitmedi (zaman aşımı)")
                } else {
                    Log.e(TAG, "Plugin $scraperId load() failed — unloading. Cause: $errType: $errMsg", loadErr)
                    Log.e(PLUGIN_DIAG, "❌ CS3 LOAD() HATASI: $scraperId — $errType: $errMsg")
                    CsTrace.error(scraperId, "load", "load() hatası: $errType: $errMsg", loadErr)
                }
                unloadExtensionInternal(context, scraperId)
                return@withLock emptyList()
            }

            // ── Step 7: Post-load Helper Patches ────────────────────────────
            // CRITICAL: Turkish plugins (Anizium, Animeler, DiziPal, etc.) execute
            // their own Helper.setup(context) inside pluginInstance.load(safeCtx).
            // That setup() method checks Cloudstream's APK signature, fails on Kitsugi,
            // and resets isAllowedVersion to FALSE. We MUST re-apply helper patches
            // AFTER load() so that isAllowedVersion is permanently true at runtime!
            applyHelperPatches(loader, scraperId, pluginClassName)

            // ── Step 8: Collect registered APIs ─────────────────────────────
            val allRegisteredApis = APIHolder.allProviders.filter {
                it.sourcePlugin == cs3File.absolutePath
            }

            // Fallback Recovery for Anti-Leech / Version Lock compromised mainUrl
            val DEFAULT_PLUGIN_DOMAINS = mapOf(
                // ── Anime siteleri ────────────────────────────────────────────
                "Anizium"            to "https://api.anizium.co",
                "TurkAnime"          to "https://www.turkanime.tv",
                "AnimeciX"           to "https://animecix.tv",
                "Animeler"           to "https://animeler.pw",
                "Animely"            to "https://animely.net",
                "AsyaWatch"          to "https://asyawatch.com",
                "CizgiMax"           to "https://cizgimax.online",
                "AnimeElysium"       to "https://animeelysium.com",
                "TrAnimeci"          to "https://tranimaci.com",
                "TrAnimeIzle"        to "https://www.tranimeizle.live",
                "AnimPow"            to "https://animpow.com",
                "AsyaAnimeleri"      to "https://asyaanimeleri.top",
                "AsyaAnimeleri2"     to "https://asyaanimeleri.top",
                "YoTurkAnime"        to "https://www.yoturkanime.com",
                "AnimeIzle"          to "https://www.animeizle.biz",
                "AsyaFanatiklerim"   to "https://asyafanatiklerim.com",
                "OnePaceTr"          to "https://www.onepacetr.net",
                "OnePaceTR"          to "https://www.onepacetr.net",
                "OpenAnime"          to "https://openani.me",
                // ── Dizi siteleri ─────────────────────────────────────────────
                "Dizilla"            to "https://dizilla.now",
                "DiziBox"            to "https://www.dizibox.live",
                "DiziPal"            to "https://dizipal3008.com",
                "DiziPalOriginal"    to "https://dizipal3008.com",
                "DiziPalOrijinal"    to "https://dizipal3008.com",
                "DDizi"              to "https://www.ddizi.im",
                "Ddizi"              to "https://www.ddizi.im",
                "DiziAsia"           to "https://diziasia.com",
                "DiziAsya"           to "https://api.diziasya.com",
                "DiziKorea"          to "https://dizikorea3.com",
                "DiziLife"           to "https://dizi75.life",
                "DizifilmORG"        to "https://dizifilmizle.to",
                "Dizigecesi"         to "https://dizigecesi.com",
                "DiziGecesi"         to "https://dizigecesi.com",
                "Dizipod"            to "https://dizipod.com",
                "DiziPod"            to "https://dizipod.com",
                "DiziYo"             to "https://www.diziyo.so",
                "SezonlukDizi"       to "https://sezonlukdizi.cc",
                "SineWix"            to "https://ydfvfdizipanel.ru",
                "Sinewix"            to "https://ydfvfdizipanel.ru",
                "Sinezy"             to "https://sinezy.to",
                "DiziMom"            to "https://www.dizimom.help",
                "DiziYou"            to "https://www.diziyou.one",
                "DramaDizilerim"     to "https://dramaflix.net/tr",
                "Dramaizle"          to "https://dramaflix.net/tr",
                "Turkdizileri"       to "https://turkdizileri.com",
                // ── Film siteleri ─────────────────────────────────────────────
                "HDFilmCehennemi"    to "https://www.hdfilmcehennemi.nl",
                "HDFilmDelisi"       to "https://hdfilmdelisi.one",
                "HDFilmIzle"         to "https://www.hdfilmizle.live",
                "SinemaCX"           to "https://sinemacc.com",
                "FilmMakinesi"       to "https://filmmakinesi.to",
                "FilmModu"           to "https://www.filmmodu.one",
                "FilmBOL"            to "https://filmbol.org",
                "FilmBip"            to "https://filmbip.com",
                "FilmEkseni"         to "https://filmekseni.vip",
                "FilmHane"           to "https://www.filmhane.shop",
                "Filmzal"            to "https://filmzal.me",
                "FullHDFilm"         to "https://fullhdfilm.pro",
                "FullHDFilmizlesene" to "https://www.fullhdfilmizlesene.mx",
                "JetFilmizle"        to "https://jetfilmizle.now",
                "WebteIzle"          to "https://webteizle3.xyz",
                "IzleAI"             to "https://720pizle.ai",
                "SetFilmIzle"        to "https://www.setfilmizle.uk",
                "WFilmizle"          to "https://wfilmizle.net",
                "WFilmİzle"          to "https://wfilmizle.net",
                // ── Diğer ─────────────────────────────────────────────────────
                "RecTV"              to "https://m.prectv72.lol",
                "recTV"              to "https://m.prectv72.lol/api",
                "KoreanTurk"         to "https://www.koreanturk.net",
                "KultFilmler"        to "https://kultfilmler.net",
                "BelgeselX"          to "https://belgeselx.com",
                "GinikoCanli"        to "https://www.giniko.com",
                "KickTR"             to "https://kick.com",
                "KraptorPlus"        to "https://a.111477.xyz",
                "MirrorVerse"        to "https://net77.cc",
                "SeiCode"            to "https://seiwatch.net",
                "SelcukFlix"         to "https://selcukflix.com",
                "Syncler"            to "https://syncler.net",
                "TorrentFilm"        to "https://torrentfilmindir.net",
                "Torrential"         to "https://api.real-debrid.com",
                "TRanimaci"          to "https://tranimaci.com",
                "Watch2Movies"       to "https://movies2watch.watch",
                "WebDramaTurkey"     to "https://webdramaturkey2.com",
                "YTS"                to "https://web.yts.gg",
                "YabanciDizi"        to "https://yabancidizi.news",
                "YeniKaynak"         to "https://www.yenikaynak.com",
                "YesilCamTv"         to "https://yesilcamtv.com"
            )
            allRegisteredApis.forEach { api ->
                val fallbackDomain = DEFAULT_PLUGIN_DOMAINS[api.name]
                    ?: DEFAULT_PLUGIN_DOMAINS[scraperId]
                    ?: DEFAULT_PLUGIN_DOMAINS.entries.firstOrNull { it.key.equals(api.name, ignoreCase = true) }?.value
                    ?: DEFAULT_PLUGIN_DOMAINS.entries.firstOrNull { it.key.equals(scraperId, ignoreCase = true) }?.value
                if (api.mainUrl.isBlank() || api.mainUrl == "/" || api.mainUrl.contains("x.anizium.co") || api.mainUrl.contains("anizium.de")) {
                    if (fallbackDomain != null) {
                        api.mainUrl = fallbackDomain
                        Log.w(TAG, "[${api.name}] Domain recovered to: $fallbackDomain")
                    }
                }
            }

            allRegisteredApis.forEach { api ->
                try {
                    CsStreamRunner.applyDomainFix(api)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to apply domain fix for ${api.name}: ${e.message}")
                }
            }

            // Name Fix: some plugins register with broken/empty names (e.g. "NONE").
            // We map the scraperId (plugin file ID) to the expected display name.
            val KNOWN_NAME_FIXES = mapOf(
                "AsyaWatch"       to "AsyaWatch",
                "CizgiMax"        to "CizgiMax",
                "AnimeElysium"    to "AnimeElysium",
                "AnimPow"         to "AnimPow",
                "AsyaAnimeleri"   to "AsyaAnimeleri",
                "AsyaAnimeleri2"  to "AsyaAnimeleri",
                "YoTurkAnime"     to "YoTurkAnime",
                "AnimeIzle"       to "AnimeIzle",
                "DiziMom"         to "DiziMom",
                "DiziYou"         to "DiziYou",
                "FilmModu"        to "FilmModu",
                "FullHDFilm"      to "FullHDFilm",
                "JetFilmizle"     to "JetFilmizle",
                "WebteIzle"       to "WebteIzle",
                "IzleAI"          to "IzleAI",
                "SetFilmIzle"     to "SetFilmIzle",
                "KoreanTurk"      to "KoreanTurk",
                "KultFilmler"     to "KultFilmler",
                "BelgeselX"       to "BelgeselX",
            )
            allRegisteredApis.forEach { api ->
                val fixedName = KNOWN_NAME_FIXES[scraperId]
                if (fixedName != null && (api.name.isBlank() || api.name == "NONE")) {
                    api.name = fixedName
                    Log.w(TAG, "[$scraperId] Name fix applied: '${api.name}' → '$fixedName'")
                }
            }


            Log.d(TAG, "Plugin $scraperId registered ${allRegisteredApis.size} API provider(s): ${allRegisteredApis.map { it.name }}")

            // Log providers that require a WebView/captcha for user/developer awareness,
            // but return all registered providers so they can be queried and verified.
            val KNOWN_WEBVIEW_PLUGINS = setOf(
                "TrAnimeIzle",   // BotKontrol captcha: opens WebView, forgot usesWebView=true
            )
            allRegisteredApis.forEach { api ->
                val blockedByFlag = api.usesWebView
                val blockedByName = KNOWN_WEBVIEW_PLUGINS.any {
                    api.name.contains(it, ignoreCase = true)
                }
                if (blockedByFlag || blockedByName) {
                    val reason = if (blockedByFlag) "usesWebView=true" else "bilinen captcha eklentisi"
                    Log.d(TAG, "  [${api.name}] $reason — WebView doğrulaması gerekebilir")
                }
            }

            if (allRegisteredApis.isEmpty()) {
                recordLoadErrorIfAbsent(scraperId, "IllegalStateException: plugin registered no API providers")
                Log.w(TAG, "Plugin loaded OK but registered 0 providers. allProviders size=${APIHolder.allProviders.size}")
                Log.e(PLUGIN_DIAG, "⚠️ CS3 SIFIR PROVIDER: $scraperId yüklendi ama 0 API kaydetti! manifest.pluginClass=$pluginClassName")
                CsTrace.error(scraperId, "load", "Eklenti yüklendi ama 0 provider kaydetti (pluginClass=$pluginClassName)")
            } else {
                Log.i(PLUGIN_DIAG, "✅ CS3 BAŞARILI: $scraperId → ${allRegisteredApis.size} provider: ${allRegisteredApis.map { it.name }}")
                CsTrace.info(scraperId, "load", "${allRegisteredApis.size} provider yüklendi: ${allRegisteredApis.map { it.name }}")
            }

            loadedPluginIds.value = loadedPluginIds.value + scraperId
            allRegisteredApis
        } catch (t: Throwable) {
            // Catch Throwable (not just Exception) to also handle Error subclasses like
            // NoClassDefFoundError that plugins may throw if they depend on missing libs.
            recordLoadError(scraperId, "${t.javaClass.simpleName}: ${t.message.orEmpty()}")
            Log.e(TAG, "Failed to load extension $scraperId: ${t.javaClass.simpleName}: ${t.message}", t)
            Log.e(PLUGIN_DIAG, "❌ CS3 YÜKLEME HATASI: $scraperId — ${t.javaClass.simpleName}: ${t.message}\n${android.util.Log.getStackTraceString(t)}")
            CsTrace.error(scraperId, "load", "Yükleme istisnası: ${t.javaClass.simpleName}: ${t.message}", t)
            emptyList()
        }
    }
}

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Reads manifest.json and parses it into a PluginManifest object.
     * Tries classloader resource stream first, falls back to direct ZIP reading.
     */
    private fun readManifest(
        loader: PathClassLoader,
        cs3File: File,
        scraperId: String
    ): PluginManifest? {
        // Primary: use classloader resource stream (works for PathClassLoader correctly)
        var manifestJson: String? = null
        try {
            loader.getResourceAsStream("manifest.json")?.use { stream ->
                manifestJson = InputStreamReader(stream).readText()
            }
        } catch (e: Exception) {
            Log.w(TAG, "getResourceAsStream failed for $scraperId, falling back to ZipFile", e)
        }

        // Fallback: read directly from ZIP
        if (manifestJson == null) {
            try {
                manifestJson = ZipFile(cs3File).use { zip ->
                    zip.getEntry("manifest.json")?.let { entry ->
                        zip.getInputStream(entry).reader().use { it.readText() }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "ZIP fallback also failed for $scraperId", e)
            }
        }

        if (manifestJson == null) {
            Log.e(TAG, "No manifest.json found in $scraperId — cannot determine plugin class")
            return null
        }

        return try {
            Gson().fromJson(manifestJson, PluginManifest::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse manifest.json for $scraperId", e)
            null
        }
    }

    /**
     * Türk anime eklentilerinin `isAllowedVersion` kontrolünü bypass eder ve
     * `RequestBlocker.counter`'ı sıfırlar. Hem ilk yükleme hem de cache-hit
     * yolunda çağrılır — böylece her fetch döngüsünde patch geçerli olur.
     *
     * @param loader  Plugin'in DEX'ini yükleyen ClassLoader
     * @param scraperId Plugin ID (ör. "Animeler", "AnimeciX")
     * @param pluginClassName Tam plugin sınıf adı (ör. "com.kraptor.AnimelerPlugin")
     */
    fun applyHelperPatches(loader: ClassLoader, scraperId: String, pluginClassName: String) {
        val packageName = pluginClassName.substringBeforeLast('.')
        val baseName    = pluginClassName.substringAfterLast('.').removeSuffix("Plugin")

        // Tüm bilinen Türk CS3 eklenti paket kalıpları:
        // Her paketin kendi Helper sınıfını ve RequestBlocker eşdeğerini dene.
        val potentialHelperClassNames = listOf(
            // Dynamic: plugin kendi paket+base adından türetilmiş
            "$packageName.${baseName}Helper",
            "$packageName.${scraperId}Helper",
            // com.kraptor.* — AnimeciX, Animeler, AnimeElysium vb.
            "com.kraptor.${baseName}Helper",
            "com.kraptor.${scraperId}Helper",
            "com.kraptor.AnimeciXHelper",
            "com.kraptor.AnimeElysiumHelper",
            "com.kraptor.AnimelerHelper",
            // com.keyiflerolsun.* — DiziPal, RecTV, DDizi, Dizilla vb.
            "com.keyiflerolsun.${baseName}Helper",
            "com.keyiflerolsun.${scraperId}Helper",
            // com.nikyokki.* — AnimPow, AsyaAnimeleri vb.
            "com.nikyokki.${baseName}Helper",
            "com.nikyokki.${scraperId}Helper",
            // com.byayzen.* — DiziBox, HDFilmCehennemi vb.
            "com.byayzen.${baseName}Helper",
            "com.byayzen.${scraperId}Helper",
            // com.kerimmkirac.* — TurkAnime, DiziBox vb.
            "com.kerimmkirac.${baseName}Helper",
            "com.kerimmkirac.${scraperId}Helper",
            "com.kerimmkirac.AniziumHelper",
            // recloudstream.* — Xhamster, XNXX vb.
            "recloudstream.${baseName}Helper",
            "recloudstream.${scraperId}Helper",
        ).distinct()

        val universalSet = object : java.util.AbstractSet<String>() {
            override val size: Int get() = Int.MAX_VALUE
            override fun iterator(): MutableIterator<String> = mutableListOf<String>().iterator()
            override fun contains(element: String?): Boolean = true
        }

        for (helperClassName in potentialHelperClassNames) {
            try {
                val helperClass = loader.loadClass(helperClassName)

                // 1. Direct static field patch
                try {
                    val field = helperClass.getDeclaredField("isAllowedVersion")
                    field.isAccessible = true
                    field.set(null, true)
                    Log.d(TAG, "[$scraperId] Patched static field $helperClassName.isAllowedVersion = true")
                } catch (e: NoSuchFieldException) {
                    // Field not found — try setter method
                }

                // 2. Kotlin object setter — setAllowedVersion(true)
                try {
                    val method = helperClass.getMethod(
                        "setAllowedVersion",
                        Boolean::class.javaPrimitiveType ?: Boolean::class.java
                    )
                    val instanceField = helperClass.getDeclaredField("INSTANCE")
                    instanceField.isAccessible = true
                    val instanceObj = instanceField.get(null)
                    method.invoke(instanceObj, true)
                    Log.d(TAG, "[$scraperId] Invoked $helperClassName.setAllowedVersion(true)")
                } catch (e: Exception) {
                    // Setter not found — field-only patch is enough
                }

                // 3. Patch hataliPaketAdlari & izinliImzaHashleri sets so that even if setup()
                // runs again, it evaluates to true.
                try {
                    val hField = helperClass.getDeclaredField("hataliPaketAdlari")
                    hField.isAccessible = true
                    hField.set(null, universalSet)
                } catch (_: Throwable) {}
                try {
                    val iField = helperClass.getDeclaredField("izinliImzaHashleri")
                    iField.isAccessible = true
                    iField.set(null, universalSet)
                } catch (_: Throwable) {}
            } catch (e: ClassNotFoundException) {
                // Not this plugin type — expected, ignore
            } catch (e: Exception) {
                Log.e(TAG, "[$scraperId] Failed to patch $helperClassName: ${e.message}", e)
            }
        }

        // Patch RequestBlocker eşdeğerleri — farklı paket adlarında dene
        val blockerClassNames = listOf(
            "com.kraptor.RequestBlocker",
            "com.keyiflerolsun.RequestBlocker",
            "com.nikyokki.RequestBlocker",
            "com.byayzen.RequestBlocker",
            "com.kerimmkirac.RequestBlocker",
            "$packageName.RequestBlocker",
        )
        for (blockerClassName in blockerClassNames) {
            try {
                val blockerClass = loader.loadClass(blockerClassName)
                val counterField = blockerClass.getDeclaredField("counter")
                counterField.isAccessible = true
                val counterObj = counterField.get(null) as? java.util.concurrent.atomic.AtomicInteger
                counterObj?.set(0)
                Log.d(TAG, "[$scraperId] Patched $blockerClassName.counter = 0")
            } catch (e: ClassNotFoundException) {
                // Not this package — ignore
            } catch (e: Exception) {
                Log.e(TAG, "[$scraperId] Failed to patch $blockerClassName: ${e.message}", e)
            }
        }
    }
}
