package com.kitsugi.animelist.data.account

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.FileObserver
import androidx.room.withTransaction
import com.kitsugi.animelist.R
import com.kitsugi.animelist.core.security.RepoVerifier
import com.kitsugi.animelist.core.security.TrustManager
import com.kitsugi.animelist.data.cloudstream.CsPluginLoader
import com.kitsugi.animelist.data.local.AddonPresets
import com.kitsugi.animelist.data.local.CloudstreamRepoEntity
import com.kitsugi.animelist.data.local.CsPluginEntity
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.local.ManagedAddonEntity
import com.kitsugi.animelist.data.manga.MangaExtensionLoader
import com.kitsugi.animelist.data.manga.MangaSourceStateStore
import com.kitsugi.animelist.data.manga.model.SourceHealthStatus
import com.kitsugi.animelist.data.remote.AddonStreamClient
import com.kitsugi.animelist.data.remote.CsPlugin
import com.kitsugi.animelist.data.remote.MangaRepoClient
import com.kitsugi.animelist.data.repository.CloudstreamRepoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import java.io.File
import java.security.MessageDigest

/** One individually mergeable add-on record inside the encrypted vault payload. */
internal data class VaultAddonRecord(val key: String, val value: String)

data class AddonRestoreState(
    val ownerId: String? = null,
    val pendingCloudstream: Int = 0,
    val pendingManga: Int = 0,
    val pendingRepositories: Int = 0,
    val pendingTrustHosts: List<String> = emptyList(),
    val isRestoring: Boolean = false,
    val message: String? = null
) {
    val pendingTotal: Int get() = pendingCloudstream + pendingManga + pendingRepositories
}

data class AddonRestoreReport(
    val restoredCloudstream: Int = 0,
    val restoredManga: Int = 0,
    val restoredRepositories: Int = 0,
    val remainingCloudstream: Int = 0,
    val remainingManga: Int = 0,
    val remainingRepositories: Int = 0,
    val blockedByTrust: Int = 0
)

/**
 * Backs up add-on configuration, never executable binaries. The record values travel only inside
 * LinkedAccountVault's AES-GCM payload. New devices can restore Stremio manifests and repository
 * lists from trusted sources; native plugins are re-downloaded only after the user explicitly asks.
 */
internal object AddonVaultBackup {
    private const val PENDING_PREFS = "kitsugi_addon_restore"
    private const val PENDING_OWNER = "owner"
    private const val PENDING_PLAN = "plan_v1"
    private const val LAST_APPLIED_ADDONS = "last_applied_addons_v1"
    private const val MAX_MANAGED_ADDONS = 300
    private const val MAX_CLOUDSTREAM_REPOS = 100
    private const val MAX_CLOUDSTREAM_PLUGINS = 500
    private const val MAX_MANGA_REPOS = 100
    private const val MAX_MANGA_EXTENSIONS = 500
    private const val MAX_URL_LENGTH = 4096
    private const val MAX_TOTAL_RECORD_BYTES = 2 * 1024 * 1024

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observerJob: Job? = null
    private var changeJob: Job? = null
    private val changeLock = Any()
    private var mangaRepoListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var mangaStateListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private val fileObservers = mutableListOf<FileObserver>()
    private val _restoreState = MutableStateFlow(AddonRestoreState())
    val restoreState = _restoreState.asStateFlow()

    @Serializable
    private data class ManagedAddonSnapshot(
        val manifestUrl: String,
        val name: String,
        val description: String? = null,
        val icon: String? = null,
        val enabled: Boolean = true,
        val orderIndex: Int = 0,
        val idPrefixes: String? = null,
        val streamTypes: String? = null,
        val subtitleTypes: String? = null,
        val repositoryUrl: String? = null
    )

    @Serializable
    private data class CloudstreamRepoSnapshot(
        val repoUrl: String,
        val name: String,
        val description: String? = null,
        val addedAt: Long = 0L
    )

    @Serializable
    private data class CloudstreamPluginSnapshot(
        val pluginId: String,
        val name: String,
        val downloadUrl: String,
        val repositoryUrl: String? = null,
        val version: Int = 0,
        val tvTypes: String = "[]",
        val iconUrl: String? = null,
        val enabled: Boolean = true
    )

    @Serializable
    private data class MangaRepoSnapshot(val repoUrl: String)

    @Serializable
    private data class MangaExtensionSnapshot(
        val extensionId: String,
        val packageName: String,
        val enabled: Boolean = true
    )

    @Serializable
    private data class PendingRestorePlan(
        val version: Int = 1,
        val cloudstreamPlugins: List<CloudstreamPluginSnapshot> = emptyList(),
        val mangaExtensions: List<MangaExtensionSnapshot> = emptyList(),
        val cloudstreamRepos: List<CloudstreamRepoSnapshot> = emptyList(),
        val mangaRepos: List<MangaRepoSnapshot> = emptyList()
    )

    private data class DecodedInventory(
        val managedAddons: List<ManagedAddonSnapshot> = emptyList(),
        val cloudstreamRepos: List<CloudstreamRepoSnapshot> = emptyList(),
        val cloudstreamPlugins: List<CloudstreamPluginSnapshot> = emptyList(),
        val mangaRepos: List<MangaRepoSnapshot> = emptyList(),
        val mangaExtensions: List<MangaExtensionSnapshot> = emptyList()
    )

    /** Snapshot only durable configuration; cache, trust decisions and executable files stay local. */
    suspend fun snapshot(context: Context, owner: String): List<VaultAddonRecord> = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val db = KitsugiDatabase.getDatabase(app)
        val addonClient = AddonStreamClient()
        val records = mutableListOf<VaultAddonRecord>()

        val systemAddonUrls = AddonPresets.DEFAULT_SUBTITLE_ADDONS
            .map(addonClient::normalizeManifestUrl)
            .map(String::lowercase)
            .toSet()
        val managedAddons = db.managedAddonDao().getAllAddons()
            .filterNot { addonClient.normalizeManifestUrl(it.manifestUrl).lowercase() in systemAddonUrls }
        require(managedAddons.size <= MAX_MANAGED_ADDONS) { "Çok fazla Stremio eklentisi var; eşitleme güvenli sınırı aştı." }
        managedAddons.forEach { addon ->
            val manifestUrl = safeWebUrl(addon.manifestUrl, allowHttp = true) ?: return@forEach
            val value = ManagedAddonSnapshot(
                manifestUrl = manifestUrl,
                name = addon.name.take(300),
                description = addon.description?.take(2_000),
                icon = addon.icon?.take(2_000),
                enabled = addon.isEnabled,
                orderIndex = addon.orderIndex.coerceIn(-1_000, 10_000),
                idPrefixes = addon.idPrefixes?.take(2_000),
                streamTypes = addon.streamTypes?.take(500),
                subtitleTypes = addon.subtitleTypes?.take(500),
                repositoryUrl = addon.repositoryUrl?.let { safeWebUrl(it, allowHttp = true) }
            )
            records += record("stremio", manifestUrl, json.encodeToString(value))
        }

        val trustedOrLocalRepos = db.cloudstreamRepoDao().getAllRepos()
        val pendingPlan = readPendingPlan(app, owner)
        val cloudstreamRepos = (trustedOrLocalRepos.map {
            CloudstreamRepoSnapshot(it.repoUrl, it.name, it.description, it.addedAt)
        } + pendingPlan.cloudstreamRepos).distinctBy { it.repoUrl.lowercase() }
        require(cloudstreamRepos.size <= MAX_CLOUDSTREAM_REPOS) { "Çok fazla CloudStream deposu var; eşitleme güvenli sınırı aştı." }
        cloudstreamRepos.forEach { repo ->
            val url = safeWebUrl(repo.repoUrl, allowHttp = false) ?: return@forEach
            records += record(
                "cloudstream_repo", url,
                json.encodeToString(repo.copy(repoUrl = url, name = repo.name.take(300), description = repo.description?.take(2_000)))
            )
        }

        val installedPlugins = db.csPluginDao().getAllPlugins().filter { plugin ->
            isSafeExtensionId(plugin.id) && File(app.filesDir, "cs_extensions/${plugin.id}.cs3").isFile
        }.map(::toPluginSnapshot)
        val cloudstreamPlugins = (installedPlugins + pendingPlan.cloudstreamPlugins)
            .distinctBy { it.pluginId.lowercase() }
        require(cloudstreamPlugins.size <= MAX_CLOUDSTREAM_PLUGINS) { "Çok fazla CloudStream eklentisi var; eşitleme güvenli sınırı aştı." }
        cloudstreamPlugins.forEach { plugin ->
            if (!isSafeExtensionId(plugin.pluginId)) return@forEach
            val downloadUrl = safeWebUrl(plugin.downloadUrl, allowHttp = false) ?: return@forEach
            val repoUrl = plugin.repositoryUrl?.let { safeWebUrl(it, allowHttp = false) }
            val sanitized = plugin.copy(
                name = plugin.name.take(300), downloadUrl = downloadUrl,
                repositoryUrl = repoUrl, tvTypes = plugin.tvTypes.take(1_000), iconUrl = plugin.iconUrl?.take(2_000)
            )
            records += record("cloudstream_plugin", sanitized.pluginId.lowercase(), json.encodeToString(sanitized))
        }

        val mangaPrefs = app.getSharedPreferences("manga_repos", Context.MODE_PRIVATE)
        val mangaUrls = readMangaRepoUrls(mangaPrefs) + pendingPlan.mangaRepos.map { it.repoUrl }
        val distinctMangaUrls = mangaUrls.distinctBy { it.lowercase() }
        require(distinctMangaUrls.size <= MAX_MANGA_REPOS) { "Çok fazla manga deposu var; eşitleme güvenli sınırı aştı." }
        distinctMangaUrls.forEach { repo ->
            val url = safeWebUrl(repo, allowHttp = false) ?: return@forEach
            records += record("manga_repo", url, json.encodeToString(MangaRepoSnapshot(url)))
        }

        val mangaSources = MangaExtensionLoader.getLoadedSources()
        val stateStore = MangaSourceStateStore(app)
        val extensionDir = MangaExtensionLoader.extensionDir(app)
        val extensionFiles = extensionDir.listFiles { _, name ->
            name.endsWith(".apk", ignoreCase = true) || name.endsWith(".mex", ignoreCase = true)
        }.orEmpty().sortedBy { it.nameWithoutExtension }
        val mangaExtensions = pendingPlan.mangaExtensions + extensionFiles.map { file ->
            val extensionId = file.nameWithoutExtension
            val source = mangaSources.firstOrNull {
                MangaExtensionLoader.getExtensionIdForSource(it) == extensionId
            }
            MangaExtensionSnapshot(
                extensionId = extensionId,
                packageName = source?.pkgName?.takeIf(String::isNotBlank)
                    ?: extensionId.replace('_', '.'),
                enabled = source?.let { stateStore.getHealthStatus(it) != SourceHealthStatus.Disabled } ?: true
            )
        } + pendingPlan.mangaExtensions
        val distinctMangaExtensions = mangaExtensions.distinctBy { it.packageName.lowercase() }
        require(distinctMangaExtensions.size <= MAX_MANGA_EXTENSIONS) { "Çok fazla manga eklentisi var; eşitleme güvenli sınırı aştı." }
        distinctMangaExtensions.forEach { extension ->
            if (!isSafeExtensionId(extension.extensionId) || extension.packageName.length !in 1..200) return@forEach
            records += record("manga_extension", extension.packageName.lowercase(), json.encodeToString(extension))
        }

        val snapshot = records.distinctBy { it.key }.sortedBy { it.key }
        require(snapshot.sumOf { it.key.toByteArray(Charsets.UTF_8).size + it.value.toByteArray(Charsets.UTF_8).size } <= MAX_TOTAL_RECORD_BYTES) {
            "Eklenti yedeği güvenli boyut sınırını aştı."
        }
        snapshot
    }

    /** Validate every encrypted add-on record before the vault writes any local preferences. */
    fun validateRecords(records: List<VaultAddonRecord>) {
        decode(records)
    }

    /** Idempotently apply merged cloud metadata without repeatedly invalidating Room observers. */
    suspend fun restoreIfChanged(context: Context, owner: String, records: List<VaultAddonRecord>) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        validateRecords(records)
        val fingerprint = sha256(records.sortedBy { it.key }.joinToString("\n") { "${it.key}\u0000${it.value}" })
        val prefs = pendingPrefs(app)
        if (prefs.getString(PENDING_OWNER, null) == owner && prefs.getString(LAST_APPLIED_ADDONS, null) == fingerprint) {
            return@withContext
        }
        restore(app, owner, records)
        check(prefs.edit().putString(PENDING_OWNER, owner).putString(LAST_APPLIED_ADDONS, fingerprint).commit()) {
            "Eklenti geri yükleme durumu yerel olarak kaydedilemedi."
        }
    }

    /** Apply cloud configuration additively; executable plugins remain a user-approved restore plan. */
    suspend fun restore(context: Context, owner: String, records: List<VaultAddonRecord>) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val inventory = decode(records)
        val db = KitsugiDatabase.getDatabase(app)
        val addonClient = AddonStreamClient()

        val safeManagedAddons = inventory.managedAddons.mapNotNull { item ->
            val url = safeWebUrl(item.manifestUrl, allowHttp = true) ?: return@mapNotNull null
            ManagedAddonEntity(
                manifestUrl = url,
                name = item.name.take(300),
                description = item.description?.take(2_000),
                icon = item.icon?.take(2_000),
                isEnabled = item.enabled,
                orderIndex = item.orderIndex.coerceIn(-1_000, 10_000),
                idPrefixes = item.idPrefixes?.take(2_000),
                streamTypes = item.streamTypes?.take(500),
                subtitleTypes = item.subtitleTypes?.take(500),
                repositoryUrl = item.repositoryUrl?.let { safeWebUrl(it, allowHttp = true) }
            )
        }.filterNot { addon ->
            AddonPresets.DEFAULT_SUBTITLE_ADDONS.any {
                addonClient.normalizeManifestUrl(it).equals(addon.manifestUrl, ignoreCase = true)
            }
        }

        // Never inherit repository trust from another device. A restored repo is added only when
        // this device already trusts its host; otherwise it remains visible as a pending restore.
        val trustedRepos = inventory.cloudstreamRepos.filter { repo ->
            safeWebUrl(repo.repoUrl, allowHttp = false) != null && RepoVerifier.isUrlTrusted(app, repo.repoUrl)
        }
        val untrustedRepos = inventory.cloudstreamRepos.filterNot { it in trustedRepos }

        val pluginsToUnload = mutableListOf<String>()
        db.withTransaction {
            safeManagedAddons.forEach { db.managedAddonDao().insertAddon(it) }
            trustedRepos.forEach { repo ->
                db.cloudstreamRepoDao().insertRepo(
                    CloudstreamRepoEntity(
                        repoUrl = repo.repoUrl,
                        name = repo.name.take(300),
                        description = repo.description?.take(2_000),
                        addedAt = repo.addedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
                    )
                )
            }

            val pluginDao = db.csPluginDao()
            inventory.cloudstreamPlugins.forEach { item ->
                if (!isSafeExtensionId(item.pluginId)) return@forEach
                val fileExists = File(app.filesDir, "cs_extensions/${item.pluginId}.cs3").isFile
                if (fileExists) {
                    val entity = item.toEntity()
                    pluginDao.upsert(entity)
                    if (!item.enabled) pluginsToUnload += item.pluginId
                }
            }
        }
        pluginsToUnload.distinct().forEach { CsPluginLoader.unloadExtension(app, it) }

        val mangaPrefs = app.getSharedPreferences("manga_repos", Context.MODE_PRIVATE)
        val currentMangaRepos = readMangaRepoUrls(mangaPrefs)
        val trustedMangaRepos = inventory.mangaRepos.filter { repo ->
            safeWebUrl(repo.repoUrl, allowHttp = false) != null && RepoVerifier.isUrlTrusted(app, repo.repoUrl)
        }
        val untrustedMangaRepos = inventory.mangaRepos.filterNot { it in trustedMangaRepos }
        val mergedMangaRepos = (trustedMangaRepos.map { it.repoUrl } + currentMangaRepos)
            .mapNotNull { safeWebUrl(it, allowHttp = false) }
            .distinctBy { it.lowercase() }
        check(mangaPrefs.edit().putString("repo_urls", JSONArray(mergedMangaRepos).toString()).commit()) {
            "Manga depo listesi yerel olarak kaydedilemedi."
        }

        val mangaStateStore = MangaSourceStateStore(app)
        inventory.mangaExtensions.forEach { item ->
            MangaExtensionLoader.getSource(item.extensionId, app)?.let { source ->
                mangaStateStore.setHealthStatus(
                    source,
                    if (item.enabled) SourceHealthStatus.Healthy else SourceHealthStatus.Disabled
                )
            }
        }
        val pendingCloudstream = inventory.cloudstreamPlugins.filter { item ->
            !File(app.filesDir, "cs_extensions/${item.pluginId}.cs3").isFile
        }
        val loadedMangaIds = MangaExtensionLoader.getLoadedSources().mapNotNull { source ->
            MangaExtensionLoader.getExtensionIdForSource(source)
        }.toSet()
        val pendingManga = inventory.mangaExtensions.filter { item ->
            // Existing but unloaded binaries still require the explicit restore step before applying
            // the cloud's enable/disable preference or executing the code.
            !hasMangaExtensionFile(app, item.extensionId) || item.extensionId !in loadedMangaIds
        }
        savePendingPlan(
            app,
            owner,
            PendingRestorePlan(
                cloudstreamPlugins = pendingCloudstream,
                mangaExtensions = pendingManga,
                cloudstreamRepos = untrustedRepos,
                mangaRepos = untrustedMangaRepos
            )
        )
    }

    fun refreshPending(context: Context, owner: String?) {
        _restoreState.value = if (owner == null) AddonRestoreState() else stateFor(context.applicationContext, owner)
    }

    /** Trust is device-local and requires a separate confirmation from binary restoration. */
    suspend fun trustPendingSources(context: Context, owner: String): Int = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val plan = readPendingPlan(app, owner)
        val urls = buildList {
            addAll(plan.cloudstreamRepos.map { it.repoUrl })
            addAll(plan.mangaRepos.map { it.repoUrl })
            addAll(plan.cloudstreamPlugins.flatMap { listOfNotNull(it.repositoryUrl, it.downloadUrl) })
        }.mapNotNull { safeWebUrl(it, allowHttp = false) }
            .distinctBy { it.lowercase() }
            .filterNot { RepoVerifier.isUrlTrusted(app, it) }
        urls.forEach { RepoVerifier.trustRepo(app, it) }
        refreshPending(app, owner)
        urls.size
    }

    fun clearPending(context: Context, owner: String) {
        val prefs = pendingPrefs(context.applicationContext)
        if (prefs.getString(PENDING_OWNER, null) == owner) prefs.edit().clear().commit()
        if (_restoreState.value.ownerId == owner) _restoreState.value = AddonRestoreState()
    }

    /**
     * Called only after the user confirms. It fetches fresh manifests from already-trusted repos,
     * validates Cloudstream repository freshness, and relies on the existing manga signature trust
     * check. Unknown repositories/signatures stay pending instead of being silently trusted.
     */
    suspend fun restorePending(context: Context, owner: String): AddonRestoreReport = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val current = readPendingPlan(app, owner)
        if (current.cloudstreamPlugins.isEmpty() && current.mangaExtensions.isEmpty() &&
            current.cloudstreamRepos.isEmpty() && current.mangaRepos.isEmpty()) {
            refreshPending(app, owner)
            return@withContext AddonRestoreReport()
        }
        _restoreState.value = stateFor(app, owner).copy(isRestoring = true, message = null)

        val db = KitsugiDatabase.getDatabase(app)
        val repoRepository = CloudstreamRepoRepository(app)
        val remainingRepos = mutableListOf<CloudstreamRepoSnapshot>()
        val remainingMangaRepos = mutableListOf<MangaRepoSnapshot>()
        var restoredRepos = 0
        var blockedByTrust = 0
        val mangaPrefs = app.getSharedPreferences("manga_repos", Context.MODE_PRIVATE)
        val existingMangaRepos = readMangaRepoUrls(mangaPrefs).toMutableList()
        for (repo in current.mangaRepos) {
            ensureActive()
            val url = safeWebUrl(repo.repoUrl, allowHttp = false)
            if (url == null || !RepoVerifier.isUrlTrusted(app, url)) {
                remainingMangaRepos += repo
                blockedByTrust++
                continue
            }
            if (existingMangaRepos.none { it.equals(url, ignoreCase = true) }) {
                existingMangaRepos += url
                check(mangaPrefs.edit().putString("repo_urls", JSONArray(existingMangaRepos).toString()).commit()) {
                    "Manga depo listesi yerel olarak kaydedilemedi."
                }
            }
            restoredRepos++
        }
        for (repo in current.cloudstreamRepos) {
            ensureActive()
            try {
                if (!RepoVerifier.isUrlTrusted(app, repo.repoUrl)) {
                    remainingRepos += repo
                    blockedByTrust++
                    continue
                }
                val result = repoRepository.addRepo(repo.repoUrl)
                if (result.isSuccess) restoredRepos++ else remainingRepos += repo
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                remainingRepos += repo
            }
        }

        val remainingCs = mutableListOf<CloudstreamPluginSnapshot>()
        var restoredCs = 0
        val fetchedRepos = mutableMapOf<String, CloudstreamRepoRepository.RepoPluginsResult>()
        val pluginDao = db.csPluginDao()
        for (item in current.cloudstreamPlugins) {
            ensureActive()
            try {
                if (!isSafeExtensionId(item.pluginId)) {
                    remainingCs += item
                    continue
                }
                val repoUrl = item.repositoryUrl?.let { safeWebUrl(it, allowHttp = false) }
                val plugin = if (repoUrl != null) {
                    if (!RepoVerifier.isUrlTrusted(app, repoUrl)) {
                        blockedByTrust++
                        remainingCs += item
                        continue
                    }
                    val repoKey = repoUrl.lowercase()
                    val fetched = fetchedRepos[repoKey] ?: repoRepository.fetchPluginsForRepoDetailed(
                        repoUrl, forceRefresh = true
                    ).also { fetchedRepos[repoKey] = it }
                    if (!fetched.liveData) {
                        remainingCs += item
                        continue
                    }
                    fetched.plugins.firstOrNull { it.internalName.equals(item.pluginId, ignoreCase = true) }
                } else {
                    val directUrl = safeWebUrl(item.downloadUrl, allowHttp = false)
                    if (directUrl == null || !RepoVerifier.isUrlTrusted(app, directUrl)) {
                        blockedByTrust++
                        remainingCs += item
                        continue
                    }
                    item.toPlugin(directUrl)
                }

                if (plugin == null) {
                    remainingCs += item
                    continue
                }
                val downloadUrl = safeWebUrl(plugin.url, allowHttp = false)
                if (downloadUrl == null || !RepoVerifier.isUrlTrusted(app, downloadUrl)) {
                    blockedByTrust++
                    remainingCs += item
                    continue
                }
                // Fetch a fresh binary from the locally trusted source; never reuse an unknown file.
                val installed = repoRepository.installCsPlugin(plugin.copy(url = downloadUrl))
                if (!installed) {
                    remainingCs += item
                    continue
                }
                val local = pluginDao.getPluginById(item.pluginId)
                if (local != null) pluginDao.upsert(local.copy(enabled = item.enabled))
                if (item.enabled) {
                    CsPluginLoader.loadExtension(app, item.pluginId, forceReload = true)
                } else {
                    CsPluginLoader.unloadExtension(app, item.pluginId)
                }
                restoredCs++
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                remainingCs += item
            }
        }

        val remainingManga = mutableListOf<MangaExtensionSnapshot>()
        var restoredManga = 0
        val mangaRepoClient = MangaRepoClient(app)
        val mangaRepoCache = mutableMapOf<String, List<com.kitsugi.animelist.data.remote.MangaExtensionInfo>?>()
        val stateStore = MangaSourceStateStore(app)
        for (item in current.mangaExtensions) {
            ensureActive()
            try {
                if (!isSafeExtensionId(item.extensionId)) {
                    remainingManga += item
                    continue
                }
                if (hasMangaExtensionFile(app, item.extensionId)) {
                    val alreadyLoaded = MangaExtensionLoader.getSource(item.extensionId, app)
                    if (alreadyLoaded != null) {
                        stateStore.setHealthStatus(
                            alreadyLoaded,
                            if (item.enabled) SourceHealthStatus.Healthy else SourceHealthStatus.Disabled
                        )
                        restoredManga++
                        continue
                    }
                    // A local but unloaded binary is not trusted merely because it is present.
                    // Continue below and replace it only from a repository trusted on this device.
                }

                var extension: com.kitsugi.animelist.data.remote.MangaExtensionInfo? = null
                for (repoUrl in currentMangaRepoUrls(app)) {
                    if (!RepoVerifier.isUrlTrusted(app, repoUrl)) {
                        blockedByTrust++
                        continue
                    }
                    val repoKey = repoUrl.lowercase()
                    val extensions = if (mangaRepoCache.containsKey(repoKey)) {
                        mangaRepoCache[repoKey]
                    } else {
                        mangaRepoClient.fetchExtensionsAutoDetect(repoUrl).also { mangaRepoCache[repoKey] = it }
                    }
                    extension = extensions?.firstOrNull { candidate ->
                        candidate.pkg.equals(item.packageName, ignoreCase = true) ||
                            candidate.pkg.replace('.', '_').equals(item.extensionId, ignoreCase = true)
                    }
                    if (extension != null) break
                }
                if (extension == null) {
                    remainingManga += item
                    continue
                }

                val downloadUrl = safeWebUrl(extension.apkUrl, allowHttp = false)
                if (downloadUrl == null) {
                    remainingManga += item
                    blockedByTrust++
                    continue
                }
                val downloaded = MangaExtensionLoader.downloadExtension(app, item.extensionId, downloadUrl)
                if (!downloaded) {
                    remainingManga += item
                    continue
                }
                val apk = MangaExtensionLoader.apkFile(app, item.extensionId)
                if (apk.isFile) {
                    val signature = TrustManager.getApkSignatureHash(app, apk)
                    if (signature == null || !TrustManager.isSignatureTrusted(app, signature)) {
                        // Preserve the file and intent, but never execute an unapproved signature.
                        remainingManga += item
                        blockedByTrust++
                        continue
                    }
                }
                // MEX packages do not carry APK signatures; they are only fetched from a repository
                // already trusted on this device, and only after the user's explicit restore action.
                val source = MangaExtensionLoader.loadExtension(app, item.extensionId)
                if (source == null) {
                    remainingManga += item
                    continue
                }
                stateStore.setHealthStatus(
                    source,
                    if (item.enabled) SourceHealthStatus.Healthy else SourceHealthStatus.Disabled
                )
                restoredManga++
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                remainingManga += item
            }
        }

        val remainingPlan = PendingRestorePlan(
            cloudstreamPlugins = remainingCs.distinctBy { it.pluginId.lowercase() },
            mangaExtensions = remainingManga.distinctBy { it.packageName.lowercase() },
            cloudstreamRepos = remainingRepos.distinctBy { it.repoUrl.lowercase() },
            mangaRepos = remainingMangaRepos.distinctBy { it.repoUrl.lowercase() }
        )
        savePendingPlan(app, owner, remainingPlan)
        val report = AddonRestoreReport(
            restoredCloudstream = restoredCs,
            restoredManga = restoredManga,
            restoredRepositories = restoredRepos,
            remainingCloudstream = remainingPlan.cloudstreamPlugins.size,
            remainingManga = remainingPlan.mangaExtensions.size,
            remainingRepositories = remainingPlan.cloudstreamRepos.size + remainingPlan.mangaRepos.size,
            blockedByTrust = blockedByTrust
        )
        _restoreState.value = stateFor(app, owner).copy(
            isRestoring = false,
            message = if (report.remainingCloudstream + report.remainingManga + report.remainingRepositories == 0) {
                app.getString(R.string.account_addon_restore_complete)
            } else {
                app.getString(R.string.account_addon_restore_pending)
            }
        )
        report
    }

    /** Observe add-on metadata, repository preferences, manga enablement and installed binaries. */
    fun startObservers(context: Context, onChanged: () -> Unit) {
        if (observerJob != null) return
        val app = context.applicationContext
        observerJob = scope.launch {
            try {
                val db = KitsugiDatabase.getDatabase(app)
                combine(
                    db.managedAddonDao().getAllAddonsFlow(),
                    db.cloudstreamRepoDao().getAllReposFlow(),
                    db.csPluginDao().getAllPluginsFlow()
                ) { addons, repos, plugins ->
                    listOf(addons.hashCode(), repos.hashCode(), plugins.hashCode())
                }.distinctUntilChanged().drop(1).collect { notifyChanged(onChanged) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Manual account sync remains available if an observer cannot be started.
            }
        }
        val prefs = app.getSharedPreferences("manga_repos", Context.MODE_PRIVATE)
        val repoListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "repo_urls") notifyChanged(onChanged)
        }
        mangaRepoListener = repoListener
        prefs.registerOnSharedPreferenceChangeListener(repoListener)

        val statePrefs = app.getSharedPreferences("manga_source_state", Context.MODE_PRIVATE)
        val stateListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key?.endsWith("::health") == true) notifyChanged(onChanged)
        }
        mangaStateListener = stateListener
        statePrefs.registerOnSharedPreferenceChangeListener(stateListener)

        val binaryEvents = FileObserver.CLOSE_WRITE or FileObserver.CREATE or FileObserver.DELETE or
            FileObserver.MOVED_TO or FileObserver.MOVED_FROM
        listOf(File(app.filesDir, "cs_extensions"), MangaExtensionLoader.extensionDir(app))
            .distinctBy { it.absolutePath }
            .forEach { directory ->
                if (!directory.exists()) directory.mkdirs()
                val observer = object : FileObserver(directory.absolutePath, binaryEvents) {
                    override fun onEvent(event: Int, path: String?) {
                        if ((event and binaryEvents) != 0) notifyChanged(onChanged)
                    }
                }
                fileObservers += observer
                observer.startWatching()
            }
    }

    private fun notifyChanged(onChanged: () -> Unit) {
        synchronized(changeLock) {
            changeJob?.cancel()
            changeJob = scope.launch {
                delay(1_000)
                onChanged()
            }
        }
    }

    private fun record(type: String, identity: String, value: String): VaultAddonRecord =
        VaultAddonRecord("$type:${sha256(identity)}", value)

    private fun decode(records: List<VaultAddonRecord>): DecodedInventory {
        require(records.size <= MAX_MANAGED_ADDONS + MAX_CLOUDSTREAM_REPOS + MAX_CLOUDSTREAM_PLUGINS + MAX_MANGA_REPOS + MAX_MANGA_EXTENSIONS) {
            "Eklenti yedeği güvenli kayıt sınırını aştı."
        }
        require(records.sumOf { it.key.toByteArray(Charsets.UTF_8).size + it.value.toByteArray(Charsets.UTF_8).size } <= MAX_TOTAL_RECORD_BYTES) {
            "Eklenti yedeği güvenli boyut sınırını aştı."
        }
        require(records.map { it.key }.distinct().size == records.size) { "Eklenti yedeğinde yinelenen kayıt var." }
        val managed = mutableListOf<ManagedAddonSnapshot>()
        val repos = mutableListOf<CloudstreamRepoSnapshot>()
        val plugins = mutableListOf<CloudstreamPluginSnapshot>()
        val mangaRepos = mutableListOf<MangaRepoSnapshot>()
        val mangaExtensions = mutableListOf<MangaExtensionSnapshot>()
        records.forEach { record ->
            val type = record.key.substringBefore(':')
            when (type) {
                "stremio" -> {
                    val item = json.decodeFromString<ManagedAddonSnapshot>(record.value)
                    val url = safeWebUrl(item.manifestUrl, allowHttp = true) ?: error("Eklenti manifest URL'si geçersiz")
                    require(record.key == "stremio:${sha256(url)}")
                    managed += item.copy(manifestUrl = url)
                }
                "cloudstream_repo" -> {
                    val item = json.decodeFromString<CloudstreamRepoSnapshot>(record.value)
                    val url = safeWebUrl(item.repoUrl, allowHttp = false) ?: error("CloudStream depo URL'si geçersiz")
                    require(record.key == "cloudstream_repo:${sha256(url)}")
                    repos += item.copy(repoUrl = url)
                }
                "cloudstream_plugin" -> {
                    val item = json.decodeFromString<CloudstreamPluginSnapshot>(record.value)
                    require(isSafeExtensionId(item.pluginId))
                    val url = safeWebUrl(item.downloadUrl, allowHttp = false) ?: error("CloudStream indirme URL'si geçersiz")
                    require(record.key == "cloudstream_plugin:${sha256(item.pluginId.lowercase())}")
                    plugins += item.copy(
                        downloadUrl = url,
                        repositoryUrl = item.repositoryUrl?.let { safeWebUrl(it, allowHttp = false) }
                    )
                }
                "manga_repo" -> {
                    val item = json.decodeFromString<MangaRepoSnapshot>(record.value)
                    val url = safeWebUrl(item.repoUrl, allowHttp = false) ?: error("Manga depo URL'si geçersiz")
                    require(record.key == "manga_repo:${sha256(url)}")
                    mangaRepos += item.copy(repoUrl = url)
                }
                "manga_extension" -> {
                    val item = json.decodeFromString<MangaExtensionSnapshot>(record.value)
                    require(isSafeExtensionId(item.extensionId))
                    require(item.packageName.length in 1..200)
                    require(record.key == "manga_extension:${sha256(item.packageName.lowercase())}")
                    mangaExtensions += item
                }
                else -> error("Eklenti yedeğinde bilinmeyen kayıt türü var.")
            }
        }
        require(managed.size <= MAX_MANAGED_ADDONS)
        require(repos.size <= MAX_CLOUDSTREAM_REPOS)
        require(plugins.size <= MAX_CLOUDSTREAM_PLUGINS)
        require(mangaRepos.size <= MAX_MANGA_REPOS)
        require(mangaExtensions.size <= MAX_MANGA_EXTENSIONS)
        return DecodedInventory(
            managedAddons = managed.distinctBy { it.manifestUrl.lowercase() },
            cloudstreamRepos = repos.distinctBy { it.repoUrl.lowercase() },
            cloudstreamPlugins = plugins.distinctBy { it.pluginId.lowercase() },
            mangaRepos = mangaRepos.distinctBy { it.repoUrl.lowercase() },
            mangaExtensions = mangaExtensions.distinctBy { it.packageName.lowercase() }
        )
    }

    private fun toPluginSnapshot(plugin: CsPluginEntity) = CloudstreamPluginSnapshot(
        pluginId = plugin.id,
        name = plugin.name,
        downloadUrl = plugin.downloadUrl,
        repositoryUrl = plugin.repositoryUrl,
        version = plugin.version,
        tvTypes = plugin.tvTypes,
        iconUrl = plugin.iconUrl,
        enabled = plugin.enabled
    )

    private fun CloudstreamPluginSnapshot.toEntity() = CsPluginEntity(
        id = pluginId,
        name = name,
        downloadUrl = downloadUrl,
        tvTypes = tvTypes,
        iconUrl = iconUrl,
        version = version,
        enabled = enabled,
        repositoryUrl = repositoryUrl
    )

    private fun CloudstreamPluginSnapshot.toPlugin(downloadUrl: String) = CsPlugin(
        name = name,
        internalName = pluginId,
        url = downloadUrl,
        description = null,
        version = version,
        language = null,
        tvTypes = emptyList(),
        iconUrl = iconUrl,
        authors = emptyList(),
        repositoryUrl = repositoryUrl
    )

    private fun readMangaRepoUrls(prefs: SharedPreferences): List<String> {
        val raw = prefs.getString("repo_urls", null) ?: return emptyList()
        val array = JSONArray(raw)
        return (0 until array.length()).mapNotNull { index ->
            array.optString(index).takeIf { it.isNotBlank() }
        }
    }

    private fun currentMangaRepoUrls(context: Context): List<String> = readMangaRepoUrls(
        context.getSharedPreferences("manga_repos", Context.MODE_PRIVATE)
    )

    private fun hasMangaExtensionFile(context: Context, extensionId: String): Boolean {
        if (!isSafeExtensionId(extensionId)) return false
        val dir = MangaExtensionLoader.extensionDir(context)
        return File(dir, "$extensionId.apk").isFile || File(dir, "$extensionId.mex").isFile
    }

    private fun isSafeExtensionId(value: String): Boolean =
        value.length in 1..160 && value.matches(Regex("^[A-Za-z0-9_.-]+$")) &&
            value != "." && value != ".."

    private fun safeWebUrl(raw: String, allowHttp: Boolean): String? {
        val value = raw.trim()
        if (value.isEmpty() || value.length > MAX_URL_LENGTH || value.any(Char::isWhitespace)) return null
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (uri.host.isNullOrBlank()) return null
        if (scheme != "https" && !(allowHttp && scheme == "http")) return null
        return value
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun pendingPrefs(context: Context) = context.getSharedPreferences(PENDING_PREFS, Context.MODE_PRIVATE)

    private fun readPendingPlan(context: Context, owner: String): PendingRestorePlan {
        val prefs = pendingPrefs(context)
        if (prefs.getString(PENDING_OWNER, null) != owner) return PendingRestorePlan()
        val raw = prefs.getString(PENDING_PLAN, null) ?: return PendingRestorePlan()
        return json.decodeFromString<PendingRestorePlan>(raw)
    }

    private fun savePendingPlan(context: Context, owner: String, plan: PendingRestorePlan) {
        check(pendingPrefs(context).edit()
            .putString(PENDING_OWNER, owner)
            .putString(PENDING_PLAN, json.encodeToString(plan))
            .commit()) { "Eklenti geri yükleme listesi yerel olarak kaydedilemedi." }
        _restoreState.value = stateFor(context, owner)
    }

    private fun stateFor(context: Context, owner: String): AddonRestoreState {
        val plan = runCatching { readPendingPlan(context, owner) }.getOrElse {
            return AddonRestoreState(
                ownerId = owner,
                message = context.getString(R.string.account_addon_pending_read_failed)
            )
        }
        val candidateUrls = buildList {
            addAll(plan.cloudstreamRepos.map { it.repoUrl })
            addAll(plan.mangaRepos.map { it.repoUrl })
            addAll(plan.cloudstreamPlugins.flatMap { listOfNotNull(it.repositoryUrl, it.downloadUrl) })
        }.mapNotNull { safeWebUrl(it, allowHttp = false) }.distinctBy { it.lowercase() }
        val untrustedHosts = candidateUrls.filterNot { RepoVerifier.isUrlTrusted(context, it) }
            .mapNotNull { Uri.parse(it).host?.lowercase() }
            .distinct()
        return AddonRestoreState(
            ownerId = owner,
            pendingCloudstream = plan.cloudstreamPlugins.size,
            pendingManga = plan.mangaExtensions.size,
            pendingRepositories = plan.cloudstreamRepos.size + plan.mangaRepos.size,
            pendingTrustHosts = untrustedHosts,
            message = if (untrustedHosts.isNotEmpty()) context.getString(R.string.account_addon_trust_pending) else null
        )
    }

    private suspend fun ensureActive() {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
    }
}
