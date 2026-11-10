package com.kitsugi.animelist.data.account

import android.content.Context
import com.kitsugi.animelist.data.local.SearchHistoryDao
import com.kitsugi.animelist.data.local.SearchHistoryEntity
import com.kitsugi.animelist.data.local.SearchHistoryMapper
import com.kitsugi.animelist.data.notifications.NotificationArchiveStore
import com.kitsugi.animelist.data.settings.SettingsDataStore
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionSource
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Auth, RLS-protected search/notification sync, and coordination with the encrypted vault.
 * All disk/network work runs on IO. Auth replacement and transfers share the vault's
 * session lock so an in-flight read for A can never be restored under B's session.
 */
object KitsugiAccountRepository {
    private suspend fun <T> accountResult(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: LinkageError) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private const val TABLE = "user_data"
    private const val KEY_SEARCH_HISTORY = "search_history"
    private const val KEY_NOTIFICATIONS = "notifications"
    private const val SEARCH_HISTORY_LIMIT = 50
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var settingsObserver: Job? = null
    @Volatile private var appContext: Context? = null

    @Serializable
    private data class UserDataRow(
        @SerialName("user_id") val userId: String? = null,
        val key: String,
        val value: JsonElement
    )

    @Serializable
    private data class RemoteSearchEntry(val query: String, val type: String, val timestamp: Long)

    private val searchListSerializer: KSerializer<List<RemoteSearchEntry>> =
        ListSerializer(RemoteSearchEntry.serializer())

    @Volatile var searchSyncEnabled: Boolean = false
        private set
    @Volatile var notificationSyncEnabled: Boolean = false
        private set

    /** Do not assume sync is enabled until the user's persisted choices are loaded. */
    fun startSettingsObserver(context: Context) {
        appContext = context.applicationContext
        if (settingsObserver != null) return
        settingsObserver = scope.launch {
            try {
                SettingsDataStore(context.applicationContext).settingsFlow.collect { settings ->
                    searchSyncEnabled = settings.accountSearchSyncEnabled
                    notificationSyncEnabled = settings.accountNotificationSyncEnabled
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("KitsugiAccount", "Sync preferences unavailable: ${e.javaClass.simpleName}")
            }
        }
    }

    fun currentEmail(): String? =
        runCatching { KitsugiAccountClient.client.auth.currentUserOrNull()?.email }.getOrNull()

    internal fun currentUserId(): String? =
        runCatching { KitsugiAccountClient.client.auth.currentUserOrNull()?.id }.getOrNull()

    fun isLoggedIn(): Boolean = currentUserId() != null

    private fun requireOwner(expectedOwner: String?): String {
        val uid = currentUserId() ?: throw AccountSessionChangedException()
        if (expectedOwner == null || uid != expectedOwner) throw AccountSessionChangedException()
        return uid
    }

    /** true = signed in; false = confirmation email required. A failed signup never erases a key. */
    suspend fun signUp(context: Context, email: String, password: String): Result<Boolean> = accountResult {
        require(AccountFormValidation.isValidEmail(email)) { "Geçerli bir e-posta adresi gir." }
        require(password.length >= AccountFormValidation.MIN_NEW_PASSWORD) { "Yeni şifre en az 8 karakter olmalı." }
        val auth = KitsugiAccountClient.client.auth
        val owner = LinkedAccountVault.changeSession {
            auth.awaitInitialization()
            check(auth.currentUserOrNull() == null) { "Yeni bir hesap açmadan önce mevcut hesaptan çıkış yap." }
            auth.signUpWith(Email, redirectUrl = KitsugiAccountClient.AUTH_CALLBACK_URL) {
                this.email = email.trim()
                this.password = password
            }
            auth.currentUserOrNull()?.id
        }
        if (owner != null) LinkedAccountVault.onSignedIn(context, password, expectedOwner = owner)
        owner != null
    }

    suspend fun signIn(context: Context, email: String, password: String): Result<Unit> = accountResult {
        require(AccountFormValidation.isValidEmail(email)) { "Geçerli bir e-posta adresi gir." }
        val auth = KitsugiAccountClient.client.auth
        val owner = LinkedAccountVault.changeSession {
            auth.awaitInitialization()
            val previousEmail = auth.currentUserOrNull()?.email
            check(previousEmail == null || previousEmail.equals(email.trim(), ignoreCase = true)) {
                "Farklı bir hesaba girmeden önce mevcut hesaptan çıkış yap."
            }
            auth.signInWith(Email) {
                this.email = email.trim()
                this.password = password
            }
            auth.currentUserOrNull()?.id ?: throw AccountSessionChangedException()
        }
        // Authentication and vault readiness are separate; the vault exposes its own failure.
        LinkedAccountVault.onSignedIn(context, password, expectedOwner = owner)
        Unit
    }

    suspend fun resendConfirmation(email: String): Result<Unit> = accountResult {
        require(AccountFormValidation.isValidEmail(email)) { "Geçerli bir e-posta adresi gir." }
        LinkedAccountVault.changeSession {
            val auth = KitsugiAccountClient.client.auth
            auth.awaitInitialization()
            check(auth.currentUserOrNull() == null) { "Bu cihazda zaten giriş yapılmış." }
            // Supabase 3.0.3's resend API has no redirect argument. The server's Site URL
            // is kitsugi://account-confirm, and legacy/non-PKCE links use normal sign-in.
            auth.resendEmail(OtpType.Email.SIGNUP, email.trim())
        }
    }

    /** Await the PKCE exchange instead of launching an unobserved SDK coroutine. */
    suspend fun confirmEmailCode(code: String): Result<Unit> = accountResult {
        require(code.isNotBlank() && code.length <= 4096)
        LinkedAccountVault.changeSession {
            val auth = KitsugiAccountClient.client.auth
            auth.awaitInitialization()
            val previousOwner = auth.currentUserOrNull()?.id
            val session = auth.exchangeCodeForSession(code, saveSession = false)
            val newOwner = session.user?.id ?: throw AccountSessionChangedException()
            if (previousOwner != null && previousOwner != newOwner) throw AccountSessionChangedException()
            auth.importSession(session, source = SessionSource.External)
        }
    }

    suspend fun signOut(context: Context): Result<Unit> = accountResult {
        // Flush + sign-out + local key removal are one session-locked operation.
        LinkedAccountVault.signOutAndForget(context) {
            KitsugiAccountClient.client.auth.signOut(SignOutScope.LOCAL)
        }.getOrThrow()
    }

    /** Executes selected categories independently; a locked vault never blocks search/notification sync. */
    suspend fun syncSelected(
        context: Context,
        dao: SearchHistoryDao,
        includeVault: Boolean = true
    ): AccountSyncReport = withContext(Dispatchers.IO) {
        val settings = SettingsDataStore(context).settingsFlow.first()
        val owner = currentUserId()
        val steps = linkedMapOf<AccountSyncSection, suspend () -> Result<Unit>>()
        if (settings.accountSearchSyncEnabled) steps[AccountSyncSection.SEARCH_HISTORY] = {
            pullAndMergeSearchHistory(context, dao, owner)
        }
        if (settings.accountNotificationSyncEnabled) steps[AccountSyncSection.NOTIFICATIONS] = {
            pullAndMergeNotificationArchive(context, owner)
        }
        if (includeVault && settings.accountBackupEnabled) steps[AccountSyncSection.VAULT] = {
            if (owner == null) Result.failure(AccountSessionChangedException())
            else LinkedAccountVault.backupNow(
                context,
                expectedOwner = owner,
                syncScope = VaultSyncScope.LINKED_ACCOUNTS
            )
        }
        if (settings.accountAddonSyncEnabled) steps[AccountSyncSection.ADDONS] = {
            if (owner == null) Result.failure(AccountSessionChangedException())
            else LinkedAccountVault.backupNow(
                context,
                expectedOwner = owner,
                syncScope = VaultSyncScope.ADDONS
            )
        }
        runAccountSyncSteps(steps)
    }

    /** Re-install binaries from trusted repositories only after an explicit in-screen confirmation. */
    suspend fun restorePendingAddons(context: Context): Result<AddonRestoreReport> = accountResult {
        val owner = currentUserId() ?: throw AccountSessionChangedException()
        LinkedAccountVault.changeSession {
            requireOwner(owner)
            if (!LinkedAccountVault.hasLocalVault(context)) throw VaultLockedException()
            if (!SettingsDataStore(context).settingsFlow.first().accountAddonSyncEnabled) {
                throw IllegalStateException("Eklenti eşitlemesi kapalı.")
            }
            val report = AddonVaultBackup.restorePending(context, owner)
            requireOwner(owner)
            report
        }
    }

    suspend fun trustPendingAddonSources(context: Context): Result<Int> = accountResult {
        val owner = currentUserId() ?: throw AccountSessionChangedException()
        LinkedAccountVault.changeSession {
            requireOwner(owner)
            if (!LinkedAccountVault.hasLocalVault(context)) throw VaultLockedException()
            if (!SettingsDataStore(context).settingsFlow.first().accountAddonSyncEnabled) {
                throw IllegalStateException("Eklenti eşitlemesi kapalı.")
            }
            val trustedCount = AddonVaultBackup.trustPendingSources(context, owner)
            requireOwner(owner)
            trustedCount
        }
    }

    /** Keep explicit local deletion semantics; the session gate serializes pushes and pulls. */
    private suspend fun pushSearchHistory(
        context: Context, dao: SearchHistoryDao, expectedOwner: String
    ): Result<Unit> = accountResult {
        LinkedAccountVault.changeSession {
            val uid = requireOwner(expectedOwner)
            if (!SettingsDataStore(context).settingsFlow.first().accountSearchSyncEnabled) return@changeSession
            val local = dao.getRecentSearchHistory().first()
            val payload = local.map { RemoteSearchEntry(it.query, it.type, it.timestamp) }
            writeKey(uid, KEY_SEARCH_HISTORY, json.encodeToJsonElement(searchListSerializer, payload))
        }
    }

    private suspend fun pullAndMergeSearchHistory(
        context: Context, dao: SearchHistoryDao, expectedOwner: String?
    ): Result<Unit> = accountResult {
        LinkedAccountVault.changeSession {
            val uid = requireOwner(expectedOwner)
            val remoteValue = fetchValue(uid, KEY_SEARCH_HISTORY)
            // A malformed response must not be mistaken for an empty cloud backup and overwritten.
            val remote = remoteValue?.let { json.decodeFromJsonElement(searchListSerializer, it) }.orEmpty()
            val local = dao.getRecentSearchHistory().first().map { RemoteSearchEntry(it.query, it.type, it.timestamp) }
            val merged = (local + remote).groupBy { it.query.trim().lowercase(java.util.Locale.ROOT) }
                .map { (_, entries) ->
                    val newest = entries.maxBy { it.timestamp }
                    val anyPinned = entries.any { SearchHistoryMapper.isPinnedType(it.type) }
                    if (anyPinned && !SearchHistoryMapper.isPinnedType(newest.type)) {
                        val pinnedSource = entries.first { SearchHistoryMapper.isPinnedType(it.type) }
                        newest.copy(type = pinnedSource.type)
                    } else {
                        newest
                    }
                }
                .sortedWith(
                    compareByDescending<RemoteSearchEntry> { SearchHistoryMapper.isPinnedType(it.type) }
                        .thenByDescending { it.timestamp }
                )
                .take(SEARCH_HISTORY_LIMIT)
            requireOwner(uid)
            merged.forEach { entry ->
                dao.insertSearchQuery(SearchHistoryEntity(query = entry.query, timestamp = entry.timestamp, type = entry.type))
            }
            dao.pruneToLimit(SEARCH_HISTORY_LIMIT)
            writeKey(uid, KEY_SEARCH_HISTORY, json.encodeToJsonElement(searchListSerializer, merged))
        }
    }

    fun onSearchHistoryChanged(dao: SearchHistoryDao, expectedOwner: String? = currentUserId()) {
        val owner = expectedOwner ?: return
        val context = appContext ?: return
        scope.launch { pushSearchHistory(context, dao, owner) }
    }

    /** Called only AFTER the archive's atomic local write, never in parallel with it. */
    fun onNotificationArchiveChanged(context: Context, expectedOwner: String?) {
        val owner = expectedOwner ?: return
        scope.launch {
            accountResult {
                if (SettingsDataStore(context).settingsFlow.first().accountNotificationSyncEnabled) {
                    pullAndMergeNotificationArchive(context, owner).getOrThrow()
                }
            }
        }
    }

    private suspend fun pullAndMergeNotificationArchive(context: Context, expectedOwner: String?): Result<Unit> = accountResult {
        LinkedAccountVault.changeSession {
            val uid = requireOwner(expectedOwner)
            val remote = NotificationArchiveStore.fromCloudJsonElement(fetchValue(uid, KEY_NOTIFICATIONS))
            requireOwner(uid)
            NotificationArchiveStore.mergeSources(context, remote)
            writeKey(uid, KEY_NOTIFICATIONS, NotificationArchiveStore.toJsonElement(context))
        }
    }

    private suspend fun fetchValue(uid: String, key: String): JsonElement? =
        KitsugiAccountClient.client.from(TABLE)
            .select(columns = Columns.list("user_id", "key", "value")) {
                filter { eq("user_id", uid); eq("key", key); eq("deleted", false) }
            }.decodeList<UserDataRow>().firstOrNull()?.value

    private suspend fun writeKey(uid: String, key: String, value: JsonElement) {
        requireOwner(uid)
        KitsugiAccountClient.client.from(TABLE).upsert(UserDataRow(userId = uid, key = key, value = value))
    }
}
