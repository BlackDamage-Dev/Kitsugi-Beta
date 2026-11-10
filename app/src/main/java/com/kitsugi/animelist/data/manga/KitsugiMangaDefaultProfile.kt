package com.kitsugi.animelist.data.manga

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.core.security.RepoVerifier
import com.kitsugi.animelist.data.manga.model.SourceHealthStatus
import com.kitsugi.animelist.data.remote.MangaRepoClient
import org.json.JSONArray

/** Seeds the user's Kitsugi Manga Plugins repository and applies first-run source defaults. */
object KitsugiMangaDefaultProfile {

    private const val TAG = "KitsugiMangaProfile"
    private const val PROFILE_PREFS = "kitsugi_manga_default_profile"
    private const val KEY_REPO_PROFILE_APPLIED_V2 = "repo_first_manga_profile_v2"
    private const val KEY_DEFAULT_REPO_SEEDED_V2 = "default_kitsugi_manga_repo_seeded_v2"

    private const val MANGA_REPOS_PREFS = "manga_repos"
    private const val KEY_REPO_URLS = "repo_urls"

    /** The repo's Gölge Bahçesi extension is preferred when the user installs it from the repo. */
    const val DEFAULT_ACTIVE_SOURCE_PKG = "eu.kanade.tachiyomi.extension.tr.golgebahcesi"

    fun isDefaultActiveSource(source: MangaSource): Boolean =
        source.pkgName.equals(DEFAULT_ACTIVE_SOURCE_PKG, ignoreCase = true)

    /**
     * Seed the official plugin repository, keep Kotatsu's legacy parser sources disabled by
     * default, and disable previously installed third-party extensions without deleting them.
     * Gölge Bahçesi is only activated after its APK is installed from the repository.
     */
    @Synchronized
    fun ensureApplied(context: Context) {
        val appContext = context.applicationContext
        KotatsuExtensionAdapter.loadSavedPrefs(appContext)

        val prefs = appContext.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
        seedDefaultMangaRepoIfNeeded(appContext, prefs)
        if (prefs.getBoolean(KEY_REPO_PROFILE_APPLIED_V2, false)) return

        Log.i(TAG, "Kitsugi manga repository default profile applying")
        KotatsuExtensionAdapter.setActiveLangs(emptySet(), appContext)

        runCatching {
            val stateStore = MangaSourceStateStore(appContext)
            MangaExtensionLoader.getExternalSourcesSnapshot().forEach { source ->
                if (isDefaultActiveSource(source)) {
                    stateStore.setHealthStatus(source, SourceHealthStatus.Healthy, reason = "repo_default_source")
                } else {
                    stateStore.setHealthStatus(source, SourceHealthStatus.Disabled, reason = "default_profile_disabled")
                    markExternalSourceDefaulted(appContext, source)
                }
            }
        }.onFailure { error ->
            Log.w(TAG, "Could not apply default manga source state: ${error.message}")
        }

        prefs.edit().putBoolean(KEY_REPO_PROFILE_APPLIED_V2, true).apply()
    }

    /** New APKs remain disabled by default, except the repo's preferred Gölge Bahçesi package. */
    fun onExternalSourcesLoaded(context: Context, sources: List<MangaSource>) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_REPO_PROFILE_APPLIED_V2, false)) return

        val stateStore = MangaSourceStateStore(appContext)
        sources.forEach { source ->
            if (source.isBuiltIn || isDefaultActiveSource(source)) return@forEach
            val marker = externalSourceMarker(source)
            if (prefs.getBoolean(marker, false)) return@forEach
            stateStore.setHealthStatus(source, SourceHealthStatus.Disabled, reason = "default_profile_disabled")
            prefs.edit().putBoolean(marker, true).apply()
        }
    }

    private fun markExternalSourceDefaulted(context: Context, source: MangaSource) {
        context.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(externalSourceMarker(source), true)
            .apply()
    }

    private fun externalSourceMarker(source: MangaSource): String =
        "disabled_default_source_${source.stableSourceKey()}"

    /** The user's repository is seeded first so the Manga extension screen opens on their catalog. */
    fun loadOrSeedMangaRepos(context: Context): List<String> {
        val appContext = context.applicationContext
        val profilePrefs = appContext.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
        seedDefaultMangaRepoIfNeeded(appContext, profilePrefs)
        return readRepoUrls(appContext.getSharedPreferences(MANGA_REPOS_PREFS, Context.MODE_PRIVATE))
    }

    private fun seedDefaultMangaRepoIfNeeded(
        appContext: Context,
        profilePrefs: android.content.SharedPreferences,
    ) {
        val officialUrl = MangaRepoClient.KITSUGI_MANGA_REPO_URL
        runCatching {
            RepoVerifier.trustRepo(appContext, officialUrl)
            RepoVerifier.trustRepo(appContext, "https://raw.githubusercontent.com")
        }

        val repoPrefs = appContext.getSharedPreferences(MANGA_REPOS_PREFS, Context.MODE_PRIVATE)
        val existing = readRepoUrls(repoPrefs)
            .map { MangaRepoClient.normalizeMangaRepoUrl(it) }
            .distinct()

        if (!profilePrefs.getBoolean(KEY_DEFAULT_REPO_SEEDED_V2, false)) {
            val merged = buildList {
                add(officialUrl)
                existing.filterNot { MangaRepoClient.isKitsugiMangaRepoUrl(it) }.forEach(::add)
            }
            writeRepoUrls(repoPrefs, merged)
            profilePrefs.edit().putBoolean(KEY_DEFAULT_REPO_SEEDED_V2, true).apply()
        } else if (existing != readRepoUrls(repoPrefs)) {
            writeRepoUrls(repoPrefs, existing)
        }
    }

    private fun readRepoUrls(repoPrefs: android.content.SharedPreferences): List<String> {
        val saved = repoPrefs.getString(KEY_REPO_URLS, null)
        if (saved.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(saved)
            buildList {
                for (i in 0 until arr.length()) {
                    arr.optString(i, "").trim().takeIf(String::isNotEmpty)?.let(::add)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeRepoUrls(repoPrefs: android.content.SharedPreferences, urls: List<String>) {
        repoPrefs.edit().putString(KEY_REPO_URLS, JSONArray(urls).toString()).apply()
    }
}
