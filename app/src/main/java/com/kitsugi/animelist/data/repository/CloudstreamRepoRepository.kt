package com.kitsugi.animelist.data.repository

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.data.local.CloudstreamRepoDao
import com.kitsugi.animelist.data.local.CloudstreamRepoEntity
import com.kitsugi.animelist.data.local.ManagedAddonDao
import com.kitsugi.animelist.data.local.ManagedAddonEntity
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.remote.CloudstreamRepoClient
import com.kitsugi.animelist.data.remote.CsPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

/**
 * Business logic layer for Cloudstream-style repository management.
 *
 * Repos use the same JSON format as Cloudstream (GitHub-hosted manifest),
 * but instead of loading .cs3 plugin binaries, we extract the Stremio
 * manifest URL from each plugin's "url" field and save it as a
 * [ManagedAddonEntity] — the same entity used for regular Stremio addons.
 */
class CloudstreamRepoRepository(private val context: Context) {

    private val db = KitsugiDatabase.getDatabase(context.applicationContext)
    private val repoDao: CloudstreamRepoDao = db.cloudstreamRepoDao()
    private val addonDao: ManagedAddonDao = db.managedAddonDao()
    private val csPluginDao = db.csPluginDao()
    private val client = CloudstreamRepoClient(context.applicationContext)

    /**
     * Kullanıcının "GitHub Vekil Sunucu (jsDelivr)" ayarı.
     *
     * AYARIN GERÇEKTEN UYGULANMASI: anahtar Ayarlar > Eklentiler ekranında açılıp
     * DataStore'a yazılıyordu, ancak repo/eklenti çekme yolları bu değeri hiç okumuyordu —
     * yalnızca "doğrudan istek başarısız olursa" yedeği vardı. Artık kullanıcı anahtarı
     * açtığında ilk istek doğrudan vekil (jsDelivr) üzerinden gider.
     */
    private val settingsDataStore =
        com.kitsugi.animelist.data.settings.SettingsDataStore(context.applicationContext)

    private suspend fun useGithubProxySetting(): Boolean = runCatching {
        settingsDataStore.settingsFlow.first().useGithubProxy
    }.getOrDefault(false)

    companion object {
        private const val TAG = "CloudstreamRepoRepository"
        private const val SYNC_PREFS = "cs_repo_sync"
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
        private const val KEY_LAST_SYNC_LIVE = "last_sync_live"

        /**
         * Aynı süreçte art arda eşitleme yapılmasını engelleyen pencere.
         *
         * ÖNEMLİ: Bu süre artık kalıcı olarak (SharedPreferences) tutulur. Önce yalnızca
         * `@Volatile` bir alandı; uygulama öldürülüp yeniden açıldığında sayaç sıfırlanıyor,
         * ama aynı süreçte sekme değiştirmek/uygulamayı öne almak da eşitlemeyi atlıyordu.
         * Kalıcı kayıt sayesinde "en son ne zaman CANLI veriyle denetlendi" bilgisi tutulur.
         */
        private const val SYNC_THROTTLE_MS = 15 * 60 * 1000L // 15 dakika

        /** Başarısız/çevrimdışı denemeler eşitleme zamanı sayılmaz → daha kısa süre sonra tekrar denenir. */
        private const val OFFLINE_RETRY_MS = 2 * 60 * 1000L // 2 dakika
    }

    private fun syncPrefs() =
        context.applicationContext.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)

    private fun lastSyncAt(): Long = runCatching { syncPrefs().getLong(KEY_LAST_SYNC_AT, 0L) }.getOrDefault(0L)

    private fun lastSyncWasLive(): Boolean = runCatching { syncPrefs().getBoolean(KEY_LAST_SYNC_LIVE, false) }.getOrDefault(false)

    private fun recordSync(liveData: Boolean) {
        runCatching {
            syncPrefs().edit()
                .putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis())
                .putBoolean(KEY_LAST_SYNC_LIVE, liveData)
                .apply()
        }
    }

    /**
     * Depodan alınan eklenti listesi + verinin güvenilirliği.
     *
     * [liveData] false ise liste yedek kaynaktan gelmiştir (Codeberg aynası veya APK içine
     * gömülü bayat `assets/plugins/plugins.json`). Bu durumda "güncelleme yok" sonucu
     * GERÇEK değildir; gömülü liste uygulamanın derlendiği andaki sürümleri taşır.
     */
    data class RepoPluginsResult(
        val plugins: List<CsPlugin>,
        val liveData: Boolean
    )

    /** Flow of all stored repos, ordered by insertion time. */
    fun getReposFlow(): Flow<List<CloudstreamRepoEntity>> = repoDao.getAllReposFlow()

    suspend fun seedDefaultRepoIfEmpty() = withContext(Dispatchers.IO) {
        try {
            val defaultUrl = "https://raw.githubusercontent.com/BlackDamage-Plugin/Kitsugi-Video-Plugins/builds/repo.json"
            com.kitsugi.animelist.core.security.RepoVerifier.trustRepo(context, defaultUrl)
            com.kitsugi.animelist.core.security.RepoVerifier.trustRepo(context, "https://raw.githubusercontent.com")
            com.kitsugi.animelist.core.security.RepoVerifier.trustRepo(context, "https://codeberg.org/BlackDamage/KitsugiPlugins/raw/branch/builds/repo.json")
            com.kitsugi.animelist.core.security.RepoVerifier.trustRepo(context, "https://codeberg.org")

            val allRepos = repoDao.getAllRepos()
            val seenUrls = mutableSetOf<String>()

            // Migrate, clean up, and deduplicate any old or malformed repos
            for (repo in allRepos) {
                val normalized = com.kitsugi.animelist.utils.CloudstreamUrlHelper.normalizeUrl(repo.repoUrl)
                if (!normalized.equals(repo.repoUrl, ignoreCase = true) || seenUrls.contains(normalized.lowercase())) {
                    Log.d(TAG, "Migrating/deduplicating repo on seed: ${repo.repoUrl} -> $normalized")
                    repoDao.deleteRepo(repo)
                    if (!seenUrls.contains(normalized.lowercase())) {
                        seenUrls.add(normalized.lowercase())
                        repoDao.insertRepo(
                            CloudstreamRepoEntity(
                                repoUrl = normalized,
                                name = if (repo.name.contains("gameras", ignoreCase = true) || repo.name.contains("keyifler", ignoreCase = true) || repo.name.contains("kekik", ignoreCase = true) || repo.name.contains("bilinmeyen", ignoreCase = true)) KitsugiStrings.get(R.string.kitsugi_plugins_onerilen) else repo.name,
                                description = repo.description,
                                addedAt = repo.addedAt
                            )
                        )
                    }
                } else {
                    seenUrls.add(normalized.lowercase())
                }
            }

            val refreshedRepos = repoDao.getAllRepos()
            val hasDefault = refreshedRepos.any { it.repoUrl.trim().equals(defaultUrl, ignoreCase = true) }
            if (!hasDefault) {
                Log.d(TAG, "No default repository found, seeding Kitsugi Plugins repository...")
                val fetched = try {
                    client.fetchRepo(defaultUrl, useGithubProxy = useGithubProxySetting(), forceRefresh = true)
                } catch (e: Exception) {
                    null
                }
                val entity = if (fetched != null) {
                    CloudstreamRepoEntity(
                        repoUrl = defaultUrl,
                        name = fetched.name,
                        description = fetched.description
                    )
                } else {
                    CloudstreamRepoEntity(
                        repoUrl = defaultUrl,
                        name = KitsugiStrings.get(R.string.kitsugi_plugins_onerilen),
                        description = KitsugiStrings.get(R.string.dizipal_rectv_ddizi_dizilla_vb_derlenmis_ve)
                    )
                }
                repoDao.insertRepo(entity)
                Log.d(TAG, "Successfully seeded default repository: ${entity.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to seed default repo", e)
        }
    }

    /**
     * Normalizes a Cloudstream repository URL, handling custom redirector wrappers
     * and stripping the cloudstreamrepo:// custom protocol scheme.
     */
    fun normalizeRepoUrl(rawUrl: String): String {
        return com.kitsugi.animelist.utils.CloudstreamUrlHelper.normalizeUrl(rawUrl)
    }

    /**
     * Validates and adds a new repository.
     * @return A [Result] containing the repo on success, or an exception describing the failure.
     */
    suspend fun addRepo(repoUrl: String): Result<CloudstreamRepoEntity> = withContext(Dispatchers.IO) {
        try {
            var inputUrl = repoUrl.trim()

            // Kısa kod mu? (örn. "kekikdevam") → cutt.ly üzerinden gerçek URL'ye çöz
            if (com.kitsugi.animelist.utils.CloudstreamUrlHelper.isShortCode(inputUrl)) {
                Log.d(TAG, "Kısa kod tespit edildi: '$inputUrl' → cutt.ly çözümü deneniyor...")
                val resolved = com.kitsugi.animelist.utils.CloudstreamUrlHelper.resolveShortCode(inputUrl)
                    ?: return@withContext Result.failure(
                        Exception("'$inputUrl' kısa kodu geçersiz veya bulunamadı. Lütfen tam URL'yi deneyin.")
                    )
                Log.d(TAG, "Kısa kod çözüldü: '$inputUrl' → '$resolved'")
                inputUrl = resolved
            }

            val normalizedUrl = normalizeRepoUrl(inputUrl)
            com.kitsugi.animelist.core.security.RepoVerifier.trustRepo(context, normalizedUrl)
            val preferProxy = useGithubProxySetting()
            var fetched = client.fetchRepo(normalizedUrl, useGithubProxy = preferProxy)
            if (fetched == null && !preferProxy && normalizedUrl.contains("raw.githubusercontent.com", ignoreCase = true)) {
                fetched = client.fetchRepo(normalizedUrl, useGithubProxy = true)
            }
            if (fetched == null && (normalizedUrl.contains("KitsugiPlugins", ignoreCase = true) || normalizedUrl.contains("codeberg.org", ignoreCase = true))) {
                Log.i(TAG, "addRepo remote fetch failed for KitsugiPlugins, using bundled asset repo...")
                fetched = client.fetchRepoFromAssets()
            }
            if (fetched == null) {
                return@withContext Result.failure(Exception("Repo manifest alınamadı. URL'yi kontrol edin."))
            }

            val entity = CloudstreamRepoEntity(
                repoUrl = normalizedUrl,
                name = fetched.name,
                description = fetched.description
            )
            repoDao.insertRepo(entity)
            Log.d(TAG, "Added repo: ${entity.name} (${entity.repoUrl})")
            Result.success(entity)
        } catch (e: Exception) {
            Log.e(TAG, "addRepo failed for $repoUrl", e)
            Result.failure(e)
        }
    }

    /** Removes a repo from the database and deletes all its installed plugins and addons. */
    suspend fun deleteRepo(repo: CloudstreamRepoEntity) = withContext(Dispatchers.IO) {
        try {
            // 1. Delete all CS plugins associated with this repo
            val csPluginsToDelete = csPluginDao.getAllPlugins().filter {
                it.repositoryUrl.equals(repo.repoUrl, ignoreCase = true)
            }
            for (pluginEntity in csPluginsToDelete) {
                val csPlugin = CsPlugin(
                    name = pluginEntity.name,
                    internalName = pluginEntity.id,
                    url = pluginEntity.downloadUrl,
                    description = "",
                    version = pluginEntity.version,
                    language = null,
                    tvTypes = null,
                    iconUrl = pluginEntity.iconUrl,
                    authors = emptyList(),
                    repositoryUrl = pluginEntity.repositoryUrl
                )
                uninstallCsPlugin(csPlugin)
            }

            // 2. Delete all Stremio addons associated with this repo
            val addonsToDelete = addonDao.getAllAddons().filter {
                it.repositoryUrl.equals(repo.repoUrl, ignoreCase = true)
            }
            for (addon in addonsToDelete) {
                addonDao.deleteAddon(addon)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cascading delete for repo ${repo.name}", e)
        }

        // 3. Delete the repo itself
        repoDao.deleteRepo(repo)
        Log.d(TAG, "Deleted repo: ${repo.name}")
    }

    suspend fun fetchPluginsForRepo(repoUrl: String, forceRefresh: Boolean = false): List<CsPlugin>? =
        fetchPluginsForRepoDetailed(repoUrl, forceRefresh).plugins.takeIf { it.isNotEmpty() }

    /**
     * [fetchPluginsForRepo] ile aynı çekimi yapar, ancak verinin CANLI depodan mı yoksa
     * yedek kaynaktan mı geldiğini de döndürür. Güncelleme denetimi bu ayrımı kullanır:
     * yedek kaynakla "her şey güncel" demek, kullanıcıya eklentilerinin güncelliği hakkında
     * yanlış bilgi vermek olur.
     */
    suspend fun fetchPluginsForRepoDetailed(repoUrl: String, forceRefresh: Boolean = false): RepoPluginsResult =
        withContext(Dispatchers.IO) {
            val normalizedUrl = normalizeRepoUrl(repoUrl)
            val preferProxy = useGithubProxySetting()
            var result = client.fetchAllPlugins(normalizedUrl, useGithubProxy = preferProxy, forceRefresh = forceRefresh)
            if ((result == null || result.isEmpty()) && !preferProxy && repoUrl.contains("raw.githubusercontent.com", ignoreCase = true)) {
                result = client.fetchAllPlugins(normalizedUrl, useGithubProxy = true, forceRefresh = forceRefresh)
            }
            val all = result.orEmpty()
            // Liste bütünüyle korunur (yedek kaynaktan gelen eklentiler kurulabilir kalsın),
            // ancak güncelleme kararları yalnızca canlı kayıtlara dayanır.
            RepoPluginsResult(
                plugins = all,
                liveData = all.any { !it.fromOfflineFallback }
            )
        }

    /**
     * Installs a [CsPlugin] from a Cloudstream repo into the managed addons
     * table as if it were a regular Stremio addon.
     *
     * The plugin's "url" field must be a Stremio manifest URL (ending in /manifest.json
     * or pointing to a Stremio-compatible addon).
     *
     * @return true if installed successfully, false if the URL is already installed.
     */
    suspend fun installPluginAsAddon(plugin: CsPlugin): Boolean = withContext(Dispatchers.IO) {
        try {
            val existing = addonDao.getAllAddons()
            val alreadyInstalled = existing.any {
                it.manifestUrl.trim().equals(plugin.url.trim(), ignoreCase = true)
            }
            if (alreadyInstalled) {
                Log.d(TAG, "Plugin already installed: ${plugin.name}")
                return@withContext false
            }

            val addonClient = com.kitsugi.animelist.data.remote.AddonStreamClient()
            val fetched = addonClient.fetchManifest(plugin.url.trim())

            val entity = if (fetched != null) {
                fetched.copy(
                    orderIndex = existing.size + 1,
                    isEnabled = true,
                    repositoryUrl = plugin.repositoryUrl
                )
            } else {
                ManagedAddonEntity(
                    manifestUrl = plugin.url.trim(),
                    name = plugin.name,
                    description = plugin.description,
                    icon = plugin.iconUrl,
                    isEnabled = true,
                    orderIndex = existing.size + 1,
                    idPrefixes = null,
                    streamTypes = plugin.tvTypes?.joinToString(",")?.lowercase(),
                    repositoryUrl = plugin.repositoryUrl
                )
            }
            addonDao.insertAddon(entity)
            Log.d(TAG, "Installed plugin as addon: ${entity.name} -> ${entity.manifestUrl} (resolved: ${fetched != null})")
            true
        } catch (e: Exception) {
            Log.e(TAG, "installPluginAsAddon failed for ${plugin.name}", e)
            false
        }
    }

    /**
     * Checks whether a plugin from a repo is already installed as an addon.
     */
    suspend fun isPluginInstalled(pluginUrl: String): Boolean = withContext(Dispatchers.IO) {
        addonDao.getAllAddons().any {
            it.manifestUrl.trim().equals(pluginUrl.trim(), ignoreCase = true)
        }
    }

    fun getEnabledCsPluginsFlow(): Flow<List<com.kitsugi.animelist.data.local.CsPluginEntity>> = csPluginDao.getEnabledPluginsFlow()

    fun getAllCsPluginsFlow(): Flow<List<com.kitsugi.animelist.data.local.CsPluginEntity>> = csPluginDao.getAllPluginsFlow()

    suspend fun toggleCsPlugin(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        csPluginDao.getPluginById(id)?.let {
            csPluginDao.upsert(it.copy(enabled = enabled))
            if (!enabled) {
                com.kitsugi.animelist.data.cloudstream.CsPluginLoader.unloadExtension(context, id)
            }
        }
    }

    suspend fun isCsPluginInstalled(id: String): Boolean = withContext(Dispatchers.IO) {
        csPluginDao.getPluginById(id) != null
    }

    suspend fun installCsPlugin(plugin: CsPlugin): Boolean = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = com.kitsugi.animelist.utils.CloudstreamUrlHelper.normalizeUrl(plugin.url)
            var downloadSuccess = com.kitsugi.animelist.data.cloudstream.CsPluginLoader.downloadExtension(
                context,
                plugin.internalName,
                normalizedUrl,
                plugin.fileHash,
                forceDownload = true
            )
            if (!downloadSuccess) {
                // Retry once without cache buster in case CDN/origin proxy rejected extra query param
                downloadSuccess = com.kitsugi.animelist.data.cloudstream.CsPluginLoader.downloadExtension(
                    context,
                    plugin.internalName,
                    normalizedUrl,
                    plugin.fileHash,
                    forceDownload = false
                )
            }
            if (!downloadSuccess) {
                Log.e(TAG, "Failed to download CS plugin: ${plugin.name} (url: $normalizedUrl)")
                return@withContext false
            }

            val entity = com.kitsugi.animelist.data.local.CsPluginEntity(
                id = plugin.internalName,
                name = plugin.name,
                downloadUrl = normalizedUrl,
                tvTypes = com.google.gson.Gson().toJson(plugin.tvTypes ?: emptyList<String>()),
                iconUrl = plugin.iconUrl,
                version = plugin.version,
                enabled = true,
                installedAt = System.currentTimeMillis(),
                repositoryUrl = plugin.repositoryUrl
            )
            csPluginDao.upsert(entity)
            Log.d(TAG, "Successfully installed CS plugin: ${plugin.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "installCsPlugin failed for ${plugin.name}", e)
            false
        }
    }

    suspend fun uninstallCsPlugin(plugin: CsPlugin): Boolean = withContext(Dispatchers.IO) {
        removeCsPluginFiles(plugin.internalName, plugin.name)
    }

    /**
     * Kurulu tek bir eklentiyi kayıttan ve diskten kaldırır.
     * DB kaydı bulunamasa bile dosya kalıntısı temizlenir; böylece yarım kalan
     * kurulumlarda da "silinemeyen eklenti" durumu oluşmaz.
     */
    private suspend fun removeCsPluginFiles(pluginId: String, pluginName: String?): Boolean =
        withContext(Dispatchers.IO) {
            var deleted = false
            try {
                val entity = csPluginDao.getPluginById(pluginId)
                if (entity != null) {
                    csPluginDao.delete(entity)
                    deleted = true
                }
                removeCsPluginArtifacts(pluginId)
                deleted = true
                Log.d(TAG, "Successfully uninstalled CS plugin: ${pluginName ?: pluginId}")
            } catch (e: Exception) {
                Log.e(TAG, "uninstallCsPlugin failed for ${pluginName ?: pluginId}", e)
            }
            deleted
        }

    /**
     * Bir eklentinin DB dışındaki izlerini temizler: bellekten çıkarma, .cs3 dosyası
     * (ve yarım kalan indirmenin .tmp dosyası), içerik parmak izi kaydı.
     * Tek tek ve toplu kaldırma aynı temizliği kullanır.
     */
    private suspend fun removeCsPluginArtifacts(pluginId: String) = withContext(Dispatchers.IO) {
        // Unload the extension from memory
        try {
            com.kitsugi.animelist.data.cloudstream.CsPluginLoader.unloadExtension(context, pluginId)
        } catch (e: Exception) {
            Log.w(TAG, "uninstallCsPlugin: bellekten kaldırma başarısız ($pluginId)", e)
        }

        val extensionDir = java.io.File(context.filesDir, "cs_extensions")
        val extensionFile = java.io.File(extensionDir, "$pluginId.cs3")
        if (extensionFile.exists()) {
            try { extensionFile.setWritable(true) } catch (_: Exception) {}
            if (!extensionFile.delete()) {
                Log.w(TAG, "uninstallCsPlugin: dosya silinemedi: ${extensionFile.absolutePath}")
            }
        }
        // Yarım kalan indirmelerden arta kalan geçici dosya
        val tempFile = java.io.File(extensionDir, "$pluginId.cs3.tmp")
        if (tempFile.exists()) {
            try { tempFile.setWritable(true) } catch (_: Exception) {}
            tempFile.delete()
        }
        com.kitsugi.animelist.data.cloudstream.CsInstalledFingerprint.invalidate(context, pluginId)
    }

    /**
     * Kurulu tüm CS eklentilerini topluca kaldırır: bellekten çıkarır, .cs3 dosyalarını
     * diskten siler ve veritabanı kayıtlarını temizler. Tek bir eklentide oluşan hata
     * diğerlerinin silinmesini engellemez.
     *
     * @return kaldırılan eklenti sayısı
     */
    suspend fun uninstallAllCsPlugins(): Int = withContext(Dispatchers.IO) {
        var removed = 0
        try {
            val installed = csPluginDao.getAllPlugins()
            val extensionDir = java.io.File(context.filesDir, "cs_extensions")
            // Kayıtlar tek işlemde silinir; dosya temizliği eklenti başına yapılır.
            csPluginDao.deleteAll()
            for (plugin in installed) {
                try {
                    removeCsPluginArtifacts(plugin.id)
                    removed++
                } catch (e: Exception) {
                    Log.e(TAG, "uninstallAllCsPlugins: ${plugin.id} kaldırılamadı", e)
                }
            }
            // Yetim kalan dosyalar (DB kaydı olmayan .cs3 / .tmp artıkları) da temizlenir;
            // aksi halde "sildim ama geri geldi/yeniden yüklendi" hissi oluşuyordu.
            try {
                extensionDir.listFiles()?.forEach { leftover ->
                    if (leftover.name.endsWith(".cs3") || leftover.name.endsWith(".cs3.tmp")) {
                        try { leftover.setWritable(true) } catch (_: Exception) {}
                        if (!leftover.delete()) {
                            Log.w(TAG, "uninstallAllCsPlugins: artık dosya silinemedi: ${leftover.absolutePath}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "uninstallAllCsPlugins: artık dosya taraması başarısız", e)
            }
            com.kitsugi.animelist.data.cloudstream.CsAutoUpdateBackoff.clearAll(context)
            Log.d(TAG, "uninstallAllCsPlugins: $removed eklenti kaldırıldı")
        } catch (e: Exception) {
            Log.e(TAG, "uninstallAllCsPlugins failed", e)
        }
        removed
    }

    suspend fun syncAndAutoUpdate(force: Boolean = false): Int = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val previousSyncAt = lastSyncAt()
            val throttleWindow = if (lastSyncWasLive()) SYNC_THROTTLE_MS else OFFLINE_RETRY_MS
            if (!force && previousSyncAt > 0L && (now - previousSyncAt < throttleWindow)) {
                Log.d(TAG, "syncAndAutoUpdate ertelendi — son denetimden ${(now - previousSyncAt) / 1000}s geçti " +
                    "(pencere ${throttleWindow / 1000}s, canlı=${lastSyncWasLive()}).")
                return@withContext 0
            }
            Log.d(TAG, "Starting syncAndAutoUpdate (force=$force)...")
            val preferProxy = useGithubProxySetting()
            val currentRepos = repoDao.getAllRepos()

            // 1. Automatically migrate legacy repository URLs (e.g. keyiflerolsun/maarrem/gameras -> KitsugiBeta-dev)
            val refreshedRepos = repoDao.getAllRepos()
            for (repo in refreshedRepos) {
                val normalized = com.kitsugi.animelist.utils.CloudstreamUrlHelper.normalizeUrl(repo.repoUrl)
                if (!normalized.equals(repo.repoUrl, ignoreCase = true)) {
                    Log.d(TAG, "Migrating repository in database: ${repo.repoUrl} -> $normalized")
                    repoDao.deleteRepo(repo)
                    
                    // Fetch metadata of the new repository
                    val fetched = client.fetchRepo(normalized, useGithubProxy = preferProxy, forceRefresh = force)
                    val newEntity = if (fetched != null) {
                        CloudstreamRepoEntity(
                            repoUrl = normalized,
                            name = fetched.name,
                            description = fetched.description,
                            addedAt = repo.addedAt
                        )
                    } else {
                        CloudstreamRepoEntity(
                            repoUrl = normalized,
                            name = if (repo.name.contains("keyiflerolsun", ignoreCase = true) || repo.name.contains("KekikAkademi", ignoreCase = true) || repo.name.contains("maarrem", ignoreCase = true) || repo.name.contains("gameras", ignoreCase = true)) {
                                KitsugiStrings.get(R.string.kitsugi_plugins_onerilen)
                            } else {
                                repo.name
                            },
                            description = repo.description,
                            addedAt = repo.addedAt
                        )
                    }
                    repoDao.insertRepo(newEntity)
                    Log.d(TAG, "Successfully migrated repository to $normalized")
                }
            }

            // 2. Fetch all latest plugins from all active repos
            //
            // Yalnızca CANLI depo kayıtları güncelleme kararına girer. Yedek kaynaktan
            // (Codeberg aynası / gömülü asset listesi) gelen kayıtlar bayat sürüm taşır;
            // bunlarla ne güncelleme bulunabilir ne de "güncel" denebilir.
            val updatedRepos = repoDao.getAllRepos()
            val allLatestPluginsMap = mutableMapOf<String, CsPlugin>()
            var liveRepoCount = 0
            for (repo in updatedRepos) {
                try {
                    val result = fetchPluginsForRepoDetailed(repo.repoUrl, forceRefresh = force)
                    if (result.liveData) liveRepoCount++
                    for (plugin in result.plugins) {
                        if (plugin.fromOfflineFallback) continue
                        val existing = allLatestPluginsMap[plugin.internalName]
                        if (existing == null || plugin.version > existing.version) {
                            allLatestPluginsMap[plugin.internalName] = plugin
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fetch plugins for repo: ${repo.repoUrl}", e)
                }
            }
            recordSync(liveData = liveRepoCount > 0)

            // 3. Auto-update installed CS plugins if newer versions are available.
            //    Karar tek merkezden verilir: sürüm numarası VE dosya içerik parmak izi
            //    (yeniden derlenen ama sürümü artırılmayan eklentiler de yakalanır).
            var updatedCount = 0
            val installedCsPlugins = csPluginDao.getAllPlugins()
            for (installedPlugin in installedCsPlugins) {
                val latestPlugin = allLatestPluginsMap[installedPlugin.id] ?: continue
                val updateReason = com.kitsugi.animelist.data.cloudstream.CsPluginUpdateChecker
                    .reason(context, installedPlugin, latestPlugin)
                if (updateReason == com.kitsugi.animelist.data.cloudstream.CsPluginUpdateChecker.Reason.NONE) {
                    continue
                }
                // Bu içerik için yakın zamanda tüm adaylar başarısız olduysa, her eşitlemede
                // yeniden denemek yerine bekle (aksi halde her açılışta ~15–20 gereksiz 404 isteği).
                if (com.kitsugi.animelist.data.cloudstream.CsAutoUpdateBackoff.shouldSkip(
                        context, latestPlugin.internalName, latestPlugin.version, latestPlugin.fileHash
                    )
                ) {
                    Log.d(TAG, "Auto-update skipped (recent failure for v${latestPlugin.version}): ${installedPlugin.name}")
                    continue
                }
                Log.d(TAG, "Auto-updating plugin: ${installedPlugin.name} " +
                    "v${installedPlugin.version} → v${latestPlugin.version} (neden=$updateReason)")

                val downloadSuccess = com.kitsugi.animelist.data.cloudstream.CsPluginLoader.downloadExtension(
                    context,
                    latestPlugin.internalName,
                    latestPlugin.url,
                    latestPlugin.fileHash,
                    forceDownload = true
                )
                if (downloadSuccess) {
                    com.kitsugi.animelist.data.cloudstream.CsAutoUpdateBackoff.clear(context, latestPlugin.internalName)

                    // İndirme BAŞARILI olsa bile dosya, manifestin bildirdiği içerik değilse
                    // (ör. birincil kaynak engelli, yalnızca eski bir ayna erişilebilir) her
                    // eşitlemede yeniden indirmeyi önlemek için bu içerik işaretlenir.
                    // NOT: `clear` ÖNCE çağrılır; aksi halde yeni kayıt silinirdi.
                    val expected = com.kitsugi.animelist.data.cloudstream.CsPluginUpdateChecker
                        .normalizeHash(latestPlugin.fileHash)
                    val actual = com.kitsugi.animelist.data.cloudstream.CsInstalledFingerprint
                        .sha256(context, latestPlugin.internalName)
                    val contentMatched = expected == null || actual == null || expected.equals(actual, ignoreCase = true)

                    if (!contentMatched) {
                        com.kitsugi.animelist.data.cloudstream.CsAutoUpdateBackoff.markFailed(
                            context, latestPlugin.internalName, latestPlugin.version, latestPlugin.fileHash
                        )
                        Log.w(
                            TAG,
                            "İndirilen dosya manifestteki içerikle eşleşmiyor (${installedPlugin.name}): " +
                                "beklenen=${expected?.take(12)}… gelen=${actual?.take(12)}… — " +
                                "12 saat boyunca yeniden denenmeyecek."
                        )
                    }
                    val updatedEntity = installedPlugin.copy(
                        version = latestPlugin.version,
                        downloadUrl = latestPlugin.url,
                        iconUrl = latestPlugin.iconUrl ?: installedPlugin.iconUrl,
                        tvTypes = com.google.gson.Gson().toJson(latestPlugin.tvTypes ?: emptyList<String>()),
                        installedAt = System.currentTimeMillis()
                    )
                    csPluginDao.upsert(updatedEntity)
                    updatedCount++

                    if (installedPlugin.enabled) {
                        Log.d(TAG, "Reloading updated plugin in memory (async with forceReload=true): ${installedPlugin.name}")
                        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                            try {
                                com.kitsugi.animelist.data.cloudstream.CsPluginLoader.loadExtension(context, installedPlugin.id, forceReload = true)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to reload updated plugin: ${installedPlugin.name}", e)
                            }
                        }
                    }
                    Log.d(TAG, "Successfully auto-updated plugin: ${installedPlugin.name}")
                } else {
                    com.kitsugi.animelist.data.cloudstream.CsAutoUpdateBackoff.markFailed(
                        context, latestPlugin.internalName, latestPlugin.version, latestPlugin.fileHash
                    )
                    Log.e(TAG, "Failed to download update for plugin: ${installedPlugin.name}")
                }
            }
            return@withContext updatedCount
        } catch (e: Exception) {
            Log.e(TAG, "syncAndAutoUpdate failed", e)
            return@withContext 0
        }
    }
}
