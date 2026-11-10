package com.kitsugi.animelist.data.account

import com.kitsugi.animelist.data.account.VaultCrypto.CipherBlob
import com.kitsugi.animelist.data.account.VaultCrypto.PasswordWrap
import com.kitsugi.animelist.data.account.VaultCrypto.asBlob
import com.kitsugi.animelist.data.account.VaultCrypto.wrap
import com.kitsugi.animelist.data.account.VaultCrypto.unwrap
import com.kitsugi.animelist.data.account.VaultCrypto.encrypt
import com.kitsugi.animelist.data.account.VaultCrypto.decrypt
import com.kitsugi.animelist.data.account.VaultCrypto.b64
import com.kitsugi.animelist.data.account.VaultCrypto.unb64
import android.content.Context
import android.content.SharedPreferences
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import com.kitsugi.animelist.data.settings.SettingsDataStore
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.SecureRandom
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

/**
 * Bağlı servis hesaplarının (AniList, MAL, Kitsu, Shikimori, Bangumi, Simkl) token'larını
 * uçtan uca şifreli olarak Kitsugi hesabına yedekler ve yeni cihazda geri yükler.
 *
 * Güvenlik modeli:
 *  • Buluta yalnızca ŞİFRELİ veri gider. Supabase bile token'ları okuyamaz.
 *  • Rastgele bir "kasa anahtarı" (vault key) üretilir. Bu anahtar, Kitsugi şifresinden
 *    PBKDF2 ile türetilen anahtarla sarılır (wrap) ve `vault_meta` satırında saklanır.
 *  • Token yedeği kasa anahtarıyla AES-256-GCM şifrelenir (`linked_accounts_vault` satırı).
 *  • Kitsugi şifresi hiçbir yerde saklanmaz.
 *  • Şifre unutulursa yedek çözülemez; servislere yeniden giriş gerekir.
 */
enum class VaultSyncScope { ENABLED_CATEGORIES, LINKED_ACCOUNTS, ADDONS }

object LinkedAccountVault {

    // Token'ların bulunduğu mevcut SharedPreferences dosyası (diğer auth sınıflarıyla aynı)
    private const val TOKEN_PREFS = "MyWebViewPrefs"
    // Kasa anahtarının cihaz kopyası (oturum kapanınca silinir)

    private const val ROW_META = "vault_meta"
    private const val ROW_PAYLOAD = "linked_accounts_vault"
    private const val TABLE = "user_data"


    /** Yedeklenecek anahtar önekleri. Geçici alanlar (code_verifier, pending_redirect) HARİÇ. */
    private val ALLOWED_PREFIXES = listOf(
        "anilist_access_token",
        "mal_access_token", "mal_refresh_token", "mal_token_expires_at",
        "simkl_access_token",
        "kitsu_access_token", "kitsu_refresh_token", "kitsu_user_id", "kitsu_username",
        "shikimori_access_token", "shikimori_refresh_token", "shikimori_token_expires_at",
        "shikimori_user_id", "shikimori_username", "shikimori_client_id", "shikimori_client_secret",
        "bangumi_access_token", "bangumi_refresh_token", "bangumi_token_expires_at",
        "bangumi_token_type", "bangumi_token_scope", "bangumi_user_id", "bangumi_username",
        "bangumi_nickname", "bangumi_avatar_url", "bangumi_client_id", "bangumi_client_secret"
    )

    private fun isAllowed(key: String): Boolean = key in ALLOWED_PREFIXES

    private val json = Json { ignoreUnknownKeys = true }
    private val random = SecureRandom()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var settingsObserver: Job? = null
    private val mutex = Mutex()
    @Volatile private var restoring = false
    private const val INITIAL_STATUS = "Yedek durumu henüz doğrulanmadı."
    private val _status = MutableStateFlow(INITIAL_STATUS)
    private var statusOwner: String? = null
    val status = _status.asStateFlow()
    // Compose observes readiness instead of decrypting Android Keystore data on every frame.
    private val _unlockedOwner = MutableStateFlow<String?>(null)
    val unlockedOwner = _unlockedOwner.asStateFlow()
    private val _conflicts = MutableStateFlow<Set<String>>(emptySet())
    val conflicts = _conflicts.asStateFlow()
    private var conflictRemote: JsonElement? = null
    private var conflictOwner: String? = null

    /** Auth must not replace the singleton client's JWT in the middle of an upload for another owner. */
    suspend fun <T> changeSession(action: suspend () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock { action() }
    }

    private suspend fun operation(context: Context, block: suspend () -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                block()
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: LinkageError) {
                _status.value = AccountErrorFormatter.userMessage(e)
                Result.failure(e)
            } catch (e: Exception) {
                _status.value = AccountErrorFormatter.userMessage(e)
                Result.failure(e)
            } finally {
                _unlockedOwner.value = userId()?.takeIf { hasLocalVault(context) }
                statusOwner = userId()
            }
        }
    }

    /** Kullanıcının elle yönettiği otomatik yedekleme anahtarı (varsayılan: açık). */
    @Volatile
    var autoBackupEnabled: Boolean = false
        private set

    /**
     * Otomatik yedeği aç/kapa. Açılışta bekleyen işler kuyruklanır; kapanışta
     * periyodik + bekleyen WorkManager işleri iptal edilir. Hesap ekranındaki
     * "Şimdi eşitle" de yalnızca seçili veri kategorilerini eşitler.
     */
    fun setAutoBackupEnabled(context: Context, enabled: Boolean) {
        autoBackupEnabled = enabled
        val uid = userId() ?: return
        if (enabled && _unlockedOwner.value == uid) {
            VaultBackupWorker.periodic(context, uid)
            VaultBackupWorker.enqueue(context, uid)
        } else {
            VaultBackupWorker.cancel(context, uid)
        }
    }

    private fun scheduleBackup() {
        if (restoring) return
        if (!autoBackupEnabled) return
        val uid = userId() ?: return
        if (_unlockedOwner.value != uid) return
        appContext?.let { VaultBackupWorker.enqueue(it, uid) }
    }

    /** Add-on repositories and installed-source files are part of the encrypted vault snapshot. */
    fun onAddonInventoryChanged() = scheduleBackup()

    // Listener bir alanda tutulmalı: SharedPreferences listener'ları zayıf referansla saklar,
    // yerel değişkende tutulursa çöp toplayıcı tarafından silinir.
    private val autoBackupListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && !isAllowed(key)) return@OnSharedPreferenceChangeListener
        scheduleBackup()
    }

    @Volatile
    private var appContext: Context? = null

    @Serializable
    private data class MetaRow(
        @SerialName("user_id") val userId: String? = null,
        val key: String,
        val value: JsonElement
    )

    // ─────────────────────────── Token anlık görüntüsü ───────────────────────────

    private fun tokenPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)

    /** Seçili token, taşınabilir ayar ve eklenti kayıtlarını tip bilgisiyle JSON dizisine çevirir. */
    private suspend fun snapshot(context: Context, owner: String, includeAddons: Boolean = true): ByteArray {
        val all = tokenPrefs(context).all
        val settings = SettingsDataStore(context).cloudSettingsFlow.first()
        val addons = if (includeAddons) AddonVaultBackup.snapshot(context, owner) else emptyList()
        val entries = buildJsonArray {
            all.forEach { (k, v) ->
                if (!isAllowed(k) || v == null) return@forEach
                addJsonObject(k, v)
            }
            settings.forEach { (k, v) -> addJsonObject(k, v, "settings") }
            addons.forEach { record ->
                add(buildJsonObject {
                    put("section", "addons")
                    put("k", record.key)
                    put("t", "s")
                    put("v", record.value)
                })
            }
        }
        return entries.toString().toByteArray(Charsets.UTF_8)
    }

    private fun kotlinx.serialization.json.JsonArrayBuilder.addJsonObject(key: String, value: Any, section: String = "tokens") {
        val (type, v) = when (value) {
            is String -> "s" to JsonPrimitive(value)
            is Long -> "l" to JsonPrimitive(value)
            is Int -> "i" to JsonPrimitive(value)
            is Boolean -> "b" to JsonPrimitive(value)
            is Float -> "f" to JsonPrimitive(value)
            else -> return
        }
        add(buildJsonObject {
            put("section", section)
            put("k", key)
            put("t", type)
            put("v", v)
        })
    }

    /** Yedek verisini yerel kasaya geri yazar. Addon girdileri de aynı uçtan uca şifreli yükün içindedir. */
    private suspend fun restore(context: Context, owner: String, plain: ByteArray) {
        val arr = json.parseToJsonElement(plain.toString(Charsets.UTF_8)).jsonArray
        val editor = tokenPrefs(context).edit()
        ALLOWED_PREFIXES.forEach { editor.remove(it) }
        val settings = mutableMapOf<String, Any>()
        val addonRecords = mutableListOf<VaultAddonRecord>()
        val addonsEnabled = SettingsDataStore(context).settingsFlow.first().accountAddonSyncEnabled
        arr.forEach { el ->
            val obj = el.jsonObject
            val section = obj["section"]?.jsonPrimitive?.content
            val key = obj["k"]?.jsonPrimitive?.content ?: return@forEach
            if (section == "addons") {
                if (!addonsEnabled) return@forEach
                require(obj["t"]?.jsonPrimitive?.content == "s") { "Şifreli eklenti yedeği biçimi geçersiz." }
                val value = obj["v"]?.jsonPrimitive?.content ?: error("Şifreli eklenti kaydı boş.")
                addonRecords += VaultAddonRecord(key, value)
                return@forEach
            }
            if (section == "settings") {
                val value = obj["v"]?.jsonPrimitive ?: return@forEach
                settings[key] = when (obj["t"]?.jsonPrimitive?.content) {
                    "s" -> value.content
                    "i" -> value.content.toInt()
                    "b" -> value.content.toBooleanStrict()
                    else -> return@forEach
                }
                return@forEach
            }
            if (!isAllowed(key)) return@forEach
            val v = obj["v"]?.jsonPrimitive ?: return@forEach
            when (obj["t"]?.jsonPrimitive?.content) {
                "s" -> editor.putString(key, v.content)
                "l" -> editor.putLong(key, v.content.toLong())
                "i" -> editor.putInt(key, v.content.toInt())
                "b" -> editor.putBoolean(key, v.content.toBoolean())
                "f" -> editor.putFloat(key, v.content.toFloat())
            }
        }
        if (addonsEnabled) AddonVaultBackup.validateRecords(addonRecords)
        // Restore runs under the vault mutex; no upload may race a partial restore.
        check(editor.commit())
        SettingsDataStore(context).restoreCloudSettings(settings)
        if (addonsEnabled) AddonVaultBackup.restoreIfChanged(context, owner, addonRecords)
    }

    // ─────────────────────────── Yerel kasa anahtarı ───────────────────────────

    private fun localVaultKey(context: Context): ByteArray? =
        userId()?.let { LocalVaultKeyStore.load(context, it) }

    fun hasLocalVault(context: Context): Boolean = runCatching {
        val uid = userId() ?: return@runCatching false
        localVaultKey(context) != null && LocalVaultKeyStore.baseline(context, uid) != null
    }.getOrDefault(false)

    private fun forgetLocalVaultUnlocked(context: Context) {
        LocalVaultKeyStore.owner(context)?.let {
            VaultBackupWorker.cancel(context, it)
            AddonVaultBackup.clearPending(context, it)
        }
        LocalVaultKeyStore.clear(context)
        _unlockedOwner.value = null
        _conflicts.value = emptySet()
        conflictRemote = null
        conflictOwner = null
        _status.value = KitsugiStrings.get(R.string.kasa_kilitli_giris_yaparak_acabilirsin)
    }

    /** Keep the only local decryption key if flushing or server sign-out fails. */
    suspend fun signOutAndForget(context: Context, signOut: suspend () -> Unit): Result<Unit> = operation(context) {
        val uid = userId()
        val settings = SettingsDataStore(context).settingsFlow.first()
        val enabled = settings.accountBackupEnabled || settings.accountAddonSyncEnabled
        if (uid != null && enabled && hasLocalVault(context)) backupUnlocked(context, uid)
        signOut()
        forgetLocalVaultUnlocked(context)
    }

    // ─────────────────────────── Bulut işlemleri ───────────────────────────

    private suspend fun fetchRow(uid: String, key: String): JsonElement? {
        val rows = KitsugiAccountClient.client.from(TABLE)
            .select(columns = Columns.list("user_id", "key", "value")) {
                filter {
                    eq("user_id", uid)
                    eq("key", key)
                }
            }
            .decodeList<MetaRow>()
        return rows.firstOrNull()?.value
    }

    /** Atomic comparison is executed on the server; a client-side read/check is not sufficient. */
    private suspend fun compareAndSwap(key: String, expected: JsonElement?, value: JsonElement): Boolean =
        KitsugiAccountClient.client.postgrest.rpc("kitsugi_vault_cas", buildJsonObject {
            put("p_key", key); put("p_expected", expected ?: JsonNull); put("p_value", value)
        }).decodeAs<Boolean>()

    private fun blobJson(blob: CipherBlob): JsonElement = json.encodeToJsonElement(CipherBlob.serializer(), blob)
    private fun blob(value: JsonElement): CipherBlob = json.decodeFromJsonElement(CipherBlob.serializer(), value)
    private fun payload(value: JsonElement?, key: ByteArray): JsonArray = if (value == null) JsonArray(emptyList())
        else blob(value).let { json.parseToJsonElement(decrypt(key, unb64(it.iv), unb64(it.ct)).toString(Charsets.UTF_8)).jsonArray }

    private fun userId(): String? =
        runCatching { KitsugiAccountClient.client.auth.currentUserOrNull()?.id }.getOrNull()

    /**
     * Giriş sonrası çağrılır (Kitsugi şifresi bu an elimizde).
     *  • Yedek varsa: kasa anahtarını açar, token'ları geri yükler.
     *  • Yoksa: yeni kasa oluşturur ve mevcut token'ları yedekler.
     */
    suspend fun onSignedIn(context: Context, password: String, expectedOwner: String): Result<Unit> = operation(context) {
        restoring = true
        try {
            val uid = userId() ?: throw AccountSessionChangedException()
            if (uid != expectedOwner) throw AccountSessionChangedException()
            // A prior account's conflicts or restore plan must not be shown under this identity.
            _conflicts.value = emptySet()
            conflictRemote = null
            conflictOwner = null
            AddonVaultBackup.refreshPending(context, uid)
            val accountSettings = SettingsDataStore(context).settingsFlow.first()
            autoBackupEnabled = accountSettings.accountBackupEnabled || accountSettings.accountAddonSyncEnabled
            _status.value = KitsugiStrings.get(R.string.account_vault_checking)
            LocalVaultKeyStore.owner(context)?.takeIf { it != uid }?.let { VaultBackupWorker.cancel(context, it) }
            val metaJson = fetchRow(uid, ROW_META)
            if (metaJson == null) {
                check(fetchRow(uid, ROW_PAYLOAD) == null) { "Kasa metadatası eksik; mevcut yedek korunuyor" }
                val key = ByteArray(32).also { random.nextBytes(it) }
                check(compareAndSwap(ROW_META, null, blobJson(wrap(password, key)))) {
                    "Başka cihaz kasayı oluşturdu. Tekrar giriş yap."
                }
                LocalVaultKeyStore.save(context, uid, key)
                LocalVaultKeyStore.saveBaseline(context, uid, emptyMap(), emptyMap())
                if (autoBackupEnabled) {
                    VaultBackupWorker.periodic(context, uid)
                    VaultBackupWorker.enqueue(context, uid)
                    backupUnlocked(context, uid)
                }
            } else {
                val meta = blob(metaJson)
                // After an interrupted password change either wrap can unlock the SAME vault key.
                val pendingKey = meta.pending?.let { runCatching { unwrap(password, it.asBlob()) }.getOrNull() }
                val key = pendingKey ?: unwrap(password, meta)
                val cachedKey = runCatching { LocalVaultKeyStore.load(context, uid) }.getOrNull()
                if (cachedKey != null && cachedKey.contentEquals(key) && LocalVaultKeyStore.baseline(context, uid) != null) {
                    // Preserve unsent offline token rotations on this device instead of restoring stale cloud tokens.
                    if (autoBackupEnabled) backupUnlocked(context, uid)
                } else {
                    val remoteJson = fetchRow(uid, ROW_PAYLOAD)
                    val remoteArray = payload(remoteJson, key)
                    val remoteGroups = VaultMerge.groups(remoteArray)
                    // Add-on sync may be disabled; do not let its local database block token recovery.
                    // Disabled add-on categories start from an empty baseline and merge only when re-enabled.
                    if (remoteJson != null) restore(context, uid, remoteArray.toString().toByteArray(Charsets.UTF_8))
                    LocalVaultKeyStore.save(context, uid, key)
                    if (remoteJson == null) {
                        LocalVaultKeyStore.saveBaseline(context, uid, emptyMap(), emptyMap())
                        if (autoBackupEnabled) backupUnlocked(context, uid)
                    } else {
                        val localAfter = VaultMerge.groups(json.parseToJsonElement(
                            snapshot(context, uid, includeAddons = accountSettings.accountAddonSyncEnabled).toString(Charsets.UTF_8)
                        ).jsonArray)
                        val baselines = baselineAfterRestore(
                            localAfter = localAfter,
                            remote = remoteGroups,
                            restoreAddons = accountSettings.accountAddonSyncEnabled
                        )
                        LocalVaultKeyStore.saveBaseline(context, uid, baselines.local, baselines.remote)
                        _status.value = KitsugiStrings.get(R.string.bagli_hesaplarin_sifreli_yedegi_geri_yuklendi)
                    }
                }
                // Only a successfully authenticated NEW password may finalize a pending transition.
                // An old-password login must not discard a staged new wrap while Auth update is in flight.
                if (pendingKey != null) compareAndSwap(ROW_META, metaJson, blobJson(meta.pending!!.asBlob()))
            }
            _conflicts.value = emptySet()
            if (!autoBackupEnabled) _status.value = KitsugiStrings.get(R.string.account_vault_ready_backup_off)
            if (autoBackupEnabled) {
                VaultBackupWorker.periodic(context, uid)
                VaultBackupWorker.enqueue(context, uid)
            }
        } finally { restoring = false }
    }

    /** On a new device, remote addon entries are applied additively. Keep local-only entries
     * marked as unsynced so the first upload can merge them without overwriting remote changes. */
    private fun baselineAfterRestore(
        localAfter: Map<String, JsonArray>,
        remote: Map<String, JsonArray>,
        restoreAddons: Boolean
    ): VaultMerge.Baselines {
        val local = mutableMapOf<String, String>()
        val remoteBaseline = mutableMapOf<String, String>()
        (localAfter.keys + remote.keys).forEach { group ->
            if (group.startsWith("addons:")) {
                if (restoreAddons) {
                    val cloudValue = remote[group]
                    // A local-only item is intentionally left changed against an empty baseline;
                    // it will be unioned into the cloud on the next enabled sync.
                    val baseline = VaultMerge.hash(cloudValue)
                    local[group] = baseline
                    remoteBaseline[group] = baseline
                } else {
                    // Leave both sides unseen while the category is disabled. On re-enable,
                    // one-sided entries merge automatically and divergent entries require a choice.
                    local[group] = VaultMerge.hash(null)
                    remoteBaseline[group] = VaultMerge.hash(null)
                }
            } else {
                val baseline = VaultMerge.hash(remote[group])
                local[group] = baseline
                remoteBaseline[group] = baseline
            }
        }
        return VaultMerge.Baselines(local, remoteBaseline)
    }

    /** Mevcut token, ayar ve eklenti listesini seçilen kategorilere göre şifreler. Kilitli kasa başarı sayılmaz. */
    suspend fun backupNow(
        context: Context,
        expectedOwner: String? = null,
        automatic: Boolean = false,
        syncScope: VaultSyncScope = VaultSyncScope.ENABLED_CATEGORIES
    ): Result<Unit> = operation(context) {
        // Check again INSIDE the session lock: the preference may have changed
        // while an already-running worker was waiting for an auth operation.
        val settings = SettingsDataStore(context).settingsFlow.first()
        if (automatic && !settings.accountBackupEnabled && !settings.accountAddonSyncEnabled) return@operation
        backupUnlocked(
            context,
            expectedOwner ?: userId() ?: throw VaultLockedException(),
            syncScope = syncScope
        )
    }

    private suspend fun backupUnlocked(
        context: Context,
        uid: String,
        preferLocal: Boolean? = null,
        syncScope: VaultSyncScope = VaultSyncScope.ENABLED_CATEGORIES
    ) {
        if (userId() != uid) throw AccountSessionChangedException()
        val key = localVaultKey(context) ?: throw VaultLockedException()
        val base = LocalVaultKeyStore.baseline(context, uid) ?: throw VaultLockedException()
        val settings = SettingsDataStore(context).settingsFlow.first()
        val includeAddons = settings.accountAddonSyncEnabled && syncScope != VaultSyncScope.LINKED_ACCOUNTS
        val local = VaultMerge.groups(json.parseToJsonElement(
            snapshot(context, uid, includeAddons = includeAddons).toString(Charsets.UTF_8)
        ).jsonArray)
        // CAS retries refetch and merge. Never resend a stale encrypted full snapshot blindly.
        repeat(4) {
            val remoteJson = fetchRow(uid, ROW_PAYLOAD)
            if (preferLocal != null && remoteJson != conflictRemote) {
                _conflicts.value = emptySet()
                error(KitsugiStrings.get(R.string.bulut_yedegi_secim_ekrani_acildiktan_sonra_degisti_simdi_esitle))
            }
            val remote = VaultMerge.groups(payload(remoteJson, key))
            val activeGroups = (local.keys + remote.keys + base.local.keys + base.remote.keys)
                .filterTo(linkedSetOf()) { group ->
                    val isAddon = group.startsWith("addons:")
                    val isLinkedAccountData = group.startsWith("service:") || group.startsWith("settings:")
                    when (syncScope) {
                        VaultSyncScope.LINKED_ACCOUNTS -> isLinkedAccountData && settings.accountBackupEnabled
                        VaultSyncScope.ADDONS -> isAddon && settings.accountAddonSyncEnabled
                        VaultSyncScope.ENABLED_CATEGORIES ->
                            (isAddon && settings.accountAddonSyncEnabled) ||
                                (isLinkedAccountData && settings.accountBackupEnabled)
                    }
                }
            if (activeGroups.isEmpty()) return
            val merged = VaultMerge.merge(local, remote, base.local, base.remote, preferLocal, activeGroups)
            if (merged.conflicts.isNotEmpty() && preferLocal == null) {
                val previous = if (conflictOwner == uid && conflictRemote == remoteJson) _conflicts.value else emptySet()
                val conflicts = (previous - activeGroups) + merged.conflicts
                conflictRemote = remoteJson
                conflictOwner = uid
                _conflicts.value = conflicts
                throw VaultConflictException(conflicts)
            }
            val mergedGroups = VaultMerge.groups(merged.payload)
            val alreadyCurrent = preferLocal == null && remoteJson != null && mergedGroups == remote
            var acceptedRow: JsonElement? = null
            val accepted = if (alreadyCurrent) {
                acceptedRow = remoteJson
                true
            } else {
                val (iv, ct) = encrypt(key, merged.payload.toString().toByteArray(Charsets.UTF_8))
                val uploadedRow = blobJson(CipherBlob(iv = b64(iv), ct = b64(ct)))
                val didUpload = compareAndSwap(ROW_PAYLOAD, remoteJson, uploadedRow)
                if (didUpload) acceptedRow = uploadedRow
                didUpload
            }
            if (accepted) {
                if (activeGroups.any { it.startsWith("addons:") }) {
                    val addonRecords = merged.payload.mapNotNull { entry ->
                        val item = entry.jsonObject
                        if (item["section"]?.jsonPrimitive?.content != "addons") return@mapNotNull null
                        val addonKey = item["k"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val value = item["v"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        VaultAddonRecord(addonKey, value)
                    }
                    AddonVaultBackup.restoreIfChanged(context, uid, addonRecords)
                }
                // Disabled categories retain their previous fingerprints; their edits are reconciled
                // only after the user re-enables that category.
                val baselines = VaultMerge.baselineAfterUpload(
                    local = local,
                    merged = mergedGroups,
                    baseLocal = base.local,
                    baseRemote = base.remote,
                    activeGroups = activeGroups
                )
                LocalVaultKeyStore.saveBaseline(context, uid, baselines.local, baselines.remote)
                val remainingConflicts = if (conflictOwner == uid && conflictRemote == remoteJson) {
                    _conflicts.value - activeGroups
                } else emptySet()
                _conflicts.value = remainingConflicts
                if (remainingConflicts.isEmpty()) {
                    conflictRemote = null
                    conflictOwner = null
                } else {
                    conflictRemote = acceptedRow ?: remoteJson
                    conflictOwner = uid
                }
                _status.value = KitsugiStrings.get(R.string.sifreli_hesap_yedegi_guncel_son_basari) +
                    java.text.DateFormat.getDateTimeInstance().format(java.util.Date(LocalVaultKeyStore.lastSuccess(context)))
                return
            }
        }
        error("Yedek başka cihazda güncelleniyor; işlem yeniden denenecek.")
    }

    /** Explicit user decision; no service credentials are displayed. Other service groups still merge. */
    suspend fun resolveConflict(context: Context, keepThisDevice: Boolean): Result<Unit> = operation(context) {
        check(_conflicts.value.isNotEmpty() && conflictOwner == userId()) { "Çözülecek çakışma yok" }
        backupUnlocked(context, userId() ?: throw VaultLockedException(), keepThisDevice)
        if (!keepThisDevice) _status.value = KitsugiStrings.get(R.string.cakisan_servislerin_bulut_kopyasi_korundu_bu_cihazda_da_kullanma)
    }

    /**
     * Token dosyasında izinli bir anahtar değiştiğinde yedeği (çok sık yazmamak için 3 sn
     * gecikmeyle) günceller. Uygulama açılışında bir kez çağrılır.
     */
    fun startAutoBackup(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        tokenPrefs(app).registerOnSharedPreferenceChangeListener(autoBackupListener)
        AddonVaultBackup.startObservers(app) { onAddonInventoryChanged() }
        scope.launch {
            try {
                val auth = KitsugiAccountClient.client.auth
                auth.awaitInitialization()
                val owners = auth.sessionStatus
                    // A transient refresh/network failure is not a sign-out.
                    .filter { it is SessionStatus.Authenticated || it is SessionStatus.NotAuthenticated }
                    .map { (it as? SessionStatus.Authenticated)?.session?.user?.id }
                    .distinctUntilChanged()
                val enabledFlow = SettingsDataStore(app).settingsFlow
                    .map { it.accountBackupEnabled to it.accountAddonSyncEnabled }.distinctUntilChanged()
                var previousOwner: String? = null
                combine(owners, enabledFlow) { owner, categories -> owner to categories }.collect { (owner, categories) ->
                    val enabled = categories.first || categories.second
                    mutex.withLock {
                        // A queued observer emission must not mutate the next account's state.
                        if (owner != userId()) return@withLock
                        autoBackupEnabled = enabled
                        previousOwner?.takeIf { it != owner }?.let { VaultBackupWorker.cancel(app, it) }
                        if (conflictOwner != owner) {
                            _conflicts.value = emptySet()
                            conflictRemote = null
                            conflictOwner = null
                        }
                        val wasReady = _unlockedOwner.value
                        val ready = owner?.takeIf { hasLocalVault(app) }
                        _unlockedOwner.value = ready
                        // Do not replace a just-finished unlock failure with a generic
                        // "locked" message merely because the auth observer ran afterwards.
                        if (statusOwner != owner || _status.value == INITIAL_STATUS) {
                            _status.value = KitsugiStrings.get(if (ready == null)
                                R.string.kasa_kilitli_giris_yaparak_acabilirsin
                            else if (!enabled) R.string.account_vault_ready_backup_off
                            else R.string.account_vault_ready_check_pending)
                        } else if (ready != null && !enabled) {
                            _status.value = KitsugiStrings.get(R.string.account_vault_ready_backup_off)
                        } else if (ready != null && wasReady != ready) {
                            _status.value = KitsugiStrings.get(R.string.account_vault_ready_check_pending)
                        }
                        statusOwner = owner
                        if (owner != null) {
                            if (enabled && ready != null) {
                                VaultBackupWorker.periodic(app, owner)
                                VaultBackupWorker.enqueue(app, owner, startup = true)
                            } else VaultBackupWorker.cancel(app, owner)
                        }
                        previousOwner = owner
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: LinkageError) {
                _status.value = AccountErrorFormatter.userMessage(e)
            } catch (e: Exception) {
                _status.value = AccountErrorFormatter.userMessage(e)
            }
        }
        if (settingsObserver == null) settingsObserver = scope.launch {
            try {
                SettingsDataStore(app).cloudSettingsFlow.distinctUntilChanged().drop(1).collect { scheduleBackup() }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _status.value = KitsugiStrings.get(R.string.ayar_degisiklikleri_izlenemedi_periyodik_yedek_tekrar_deneyecek) }
        }
    }

    /** Durable two-phase wrap transition. Never roll back on ambiguous network failures. */
    suspend fun changePassword(context: Context, currentPassword: String, newPassword: String): Result<Unit> = operation(context) {
        require(newPassword.length >= 8) { "Yeni şifre en az 8 karakter olmalı." }
        val uid = userId() ?: throw VaultLockedException()
        val auth = KitsugiAccountClient.client.auth
        val email = auth.currentUserOrNull()?.email ?: throw VaultLockedException()
        // Reauthenticate, not just trust an old cached session or an old password wrap.
        auth.signInWith(Email) { this.email = email; password = currentPassword }
        check(userId() == uid) { "Oturum değişti" }
        val originalJson = fetchRow(uid, ROW_META) ?: throw VaultLockedException()
        val original = blob(originalJson)
        val key = original.pending?.let { runCatching { unwrap(currentPassword, it.asBlob()) }.getOrNull() }
            ?: unwrap(currentPassword, original)
        val newWrap: CipherBlob
        val stagedJson: JsonElement
        if (original.pending != null) {
            // Resume exactly the previous transition. A competing device cannot stage a different password.
            newWrap = original.pending.asBlob()
            check(runCatching { unwrap(newPassword, newWrap).contentEquals(key) }.getOrDefault(false)) {
                "Yarım kalan şifre değişikliği var. Önceki denemede seçtiğin yeni şifreyi kullan."
            }
            stagedJson = originalJson
        } else {
            require(currentPassword != newPassword) { "Yeni şifre mevcut şifreden farklı olmalı." }
            newWrap = wrap(newPassword, key)
            stagedJson = blobJson(original.copy(pending = PasswordWrap(newWrap.salt, newWrap.iv, newWrap.ct)))
            check(compareAndSwap(ROW_META, originalJson, stagedJson)) { "Başka cihaz şifre işlemi yapıyor. Tekrar dene." }
        }
        // If process/network dies here, both old and new password wraps remain available.
        // Neither password is stored locally, in WorkManager, or in the database.
        if (currentPassword != newPassword) auth.updateUser { password = newPassword }
        check(compareAndSwap(ROW_META, stagedJson, blobJson(newWrap))) {
            "Şifre güncellendi; kasa geçişini tamamlamak için yeni şifrenle tekrar giriş yap."
        }
        _status.value = KitsugiStrings.get(R.string.sifre_degistirildi_bagli_hesap_yedeginin_anahtari_korundu)
    }
}
