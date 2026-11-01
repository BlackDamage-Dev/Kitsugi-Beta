package com.kitsugi.animelist.data.auth

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.concurrent.thread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.data.remote.TmdbAccountClient
import com.kitsugi.animelist.data.platform.PlatformErrorCode
import com.kitsugi.animelist.data.platform.PlatformFailureRegistry
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

object ExternalAuthManager {
    private const val PREFS_NAME = "MyWebViewPrefs"

    // ── T3-01: Kimlik bilgileri BuildConfig üzerinden gelir ──────────────────
    // Kaynak: local.properties → app/build.gradle.kts → BuildConfig
    // local.properties (git-ignored) yoksa build.gradle.kts'teki developer
    // fallback değerleri kullanılır; CI'da CI secret'lar geçirilir.
    private val ANILIST_CLIENT_ID    get() = com.kitsugi.animelist.BuildConfig.ANILIST_CLIENT_ID
    private val ANILIST_CLIENT_SECRET get() = com.kitsugi.animelist.BuildConfig.ANILIST_CLIENT_SECRET
    private const val KEY_ANILIST_TOKEN = "anilist_access_token"
    private const val KEY_ANILIST_CODE_VERIFIER = "anilist_code_verifier"

    /**
     * MAL giriş (OAuth) istemcisi — malapp://auth redirect'iyle kayıtlı "Android" tipi uygulama.
     * DİKKAT: BuildConfig.MAL_CLIENT_ID (Web tipi, API/miribyou anahtarı) burada KULLANILMAMALI;
     * o istemciyle authorize ucu 401 Basic Auth döndürüp tarayıcıda "Oturum açın" popup'ı açıyor.
     */
    private val MAL_CLIENT_ID get() = com.kitsugi.animelist.BuildConfig.MAL_OAUTH_CLIENT_ID
    private val MAL_CLIENT_SECRET get() = com.kitsugi.animelist.BuildConfig.MAL_OAUTH_CLIENT_SECRET
    private const val KEY_MAL_TOKEN = "mal_access_token"
    private const val KEY_MAL_REFRESH_TOKEN = "mal_refresh_token"
    private const val KEY_MAL_TOKEN_EXPIRES_AT = "mal_token_expires_at"
    private const val KEY_MAL_CODE_VERIFIER = "mal_code_verifier"

    private val SIMKL_CLIENT_ID    get() = com.kitsugi.animelist.BuildConfig.SIMKL_CLIENT_ID
    private val SIMKL_CLIENT_SECRET get() = com.kitsugi.animelist.BuildConfig.SIMKL_CLIENT_SECRET
    private const val KEY_SIMKL_TOKEN = "simkl_access_token"
    private const val KEY_SIMKL_CODE_VERIFIER = "simkl_code_verifier"

    private const val KEY_KITSU_TOKEN = "kitsu_access_token"
    private const val KEY_KITSU_REFRESH_TOKEN = "kitsu_refresh_token"
    private const val KEY_KITSU_TOKEN_EXPIRES_AT = "kitsu_token_expires_at"
    private const val KEY_KITSU_USER_ID = "kitsu_user_id"
    private const val KEY_KITSU_USERNAME = "kitsu_username"

    private val kitsuRefreshMutex = kotlinx.coroutines.sync.Mutex()
    private val shikimoriRefreshMutex = kotlinx.coroutines.sync.Mutex()
    private val malRefreshMutex = kotlinx.coroutines.sync.Mutex()

    private const val KEY_SHIKIMORI_TOKEN = "shikimori_access_token"
    private const val KEY_SHIKIMORI_REFRESH_TOKEN = "shikimori_refresh_token"
    private const val KEY_SHIKIMORI_TOKEN_EXPIRES_AT = "shikimori_token_expires_at"
    private const val KEY_SHIKIMORI_USER_ID = "shikimori_user_id"
    private const val KEY_SHIKIMORI_USERNAME = "shikimori_username"
    private const val KEY_SHIKIMORI_CLIENT_ID = "shikimori_client_id"
    private const val KEY_SHIKIMORI_CLIENT_SECRET = "shikimori_client_secret"

    /**
     * Son yetkilendirme akışında tarayıcıya verilen redirect_uri.
     * Shikimori/Doorkeeper, token isteğindeki redirect_uri'nin authorize adımındakiyle
     * birebir aynı olmasını ZORUNLU kılar; bu yüzden akış başlatılırken kaydedilir.
     */
    private const val KEY_SHIKIMORI_PENDING_REDIRECT = "shikimori_pending_redirect_uri"

    private const val REDIRECT_URI = "malapp://auth"

    private val _authEvents = MutableSharedFlow<AuthEvent>(
        extraBufferCapacity = 4
    )

    val authEvents = _authEvents.asSharedFlow()

    data class AuthState(
        val isAniListConnected: Boolean,
        val isMalConnected: Boolean,
        val isSimklConnected: Boolean,
        val isKitsuConnected: Boolean = false,
        val isShikimoriConnected: Boolean = false,
        val isBangumiConnected: Boolean = false,
        val isTmdbConnected: Boolean = false
    )

    sealed class AuthEvent {
        data class Success(
            val serviceName: String
        ) : AuthEvent()

        data class Error(
            val message: String
        ) : AuthEvent()

        data class SessionExpired(
            val serviceName: String
        ) : AuthEvent()
    }

    fun getAuthState(context: Context): AuthState {
        val prefs = prefs(context)

        return AuthState(
            isAniListConnected = prefs.getString(KEY_ANILIST_TOKEN, null) != null,
            isMalConnected = prefs.getString(KEY_MAL_TOKEN, null) != null,
            isSimklConnected = prefs.getString(KEY_SIMKL_TOKEN, null) != null,
            isKitsuConnected = prefs.getString(KEY_KITSU_TOKEN, null) != null,
            isShikimoriConnected = prefs.getString(KEY_SHIKIMORI_TOKEN, null) != null,
            isBangumiConnected = BangumiAuthStore.isConnected(context),
            isTmdbConnected = TmdbAuthStore.isConnected(context)
        )
    }

    fun getAniListToken(context: Context): String? {
        return prefs(context).getString(KEY_ANILIST_TOKEN, null)
    }

    fun getMalToken(context: Context): String? {
        return prefs(context).getString(KEY_MAL_TOKEN, null)
    }

    fun getSimklToken(context: Context): String? {
        return prefs(context).getString(KEY_SIMKL_TOKEN, null)
    }

    fun getKitsuToken(context: Context): String? = prefs(context).getString(KEY_KITSU_TOKEN, null)
    fun getKitsuUserId(context: Context): String? = prefs(context).getString(KEY_KITSU_USER_ID, null)
    fun getKitsuUsername(context: Context): String? = prefs(context).getString(KEY_KITSU_USERNAME, null)

    fun saveKitsuAuth(
        context: Context,
        token: String,
        refreshToken: String,
        userId: String,
        username: String,
        expiresInSeconds: Long = 2_592_000L,
        notify: Boolean = true
    ) {
        val expiresAt = System.currentTimeMillis() + (expiresInSeconds.coerceAtLeast(60L) * 1000L)
        prefs(context).edit()
            .putString(KEY_KITSU_TOKEN, token)
            .putString(KEY_KITSU_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_KITSU_TOKEN_EXPIRES_AT, expiresAt)
            .putString(KEY_KITSU_USER_ID, userId)
            .putString(KEY_KITSU_USERNAME, username)
            .remove(sessionExpiredKey("kitsu"))
            .apply()
        if (notify) _authEvents.tryEmit(AuthEvent.Success("kitsu"))
    }

    /**
     * Kitsu erişim token'ını döndürür; süresi dolmuşsa (veya önceki sürümlerden yükseltmede
     * `expiresAt == 0L` iken `refresh_token` mevcutsa) `kitsuRefreshMutex` kilidi altında
     * OAuth yenilemesini çalıştırır (`E85`).
     */
    suspend fun getOrRefreshKitsuToken(context: Context): String? = withContext(Dispatchers.IO) {
        kitsuRefreshMutex.lock()
        try {
            val p = prefs(context)
            val token = p.getString(KEY_KITSU_TOKEN, null) ?: return@withContext null
            val expiresAt = p.getLong(KEY_KITSU_TOKEN_EXPIRES_AT, 0L)
            val refreshToken = p.getString(KEY_KITSU_REFRESH_TOKEN, null)
            val needsProactiveRefresh = !refreshToken.isNullOrBlank() &&
                (expiresAt == 0L || System.currentTimeMillis() + 300_000L >= expiresAt)
            if (needsProactiveRefresh) {
                val refreshed = try {
                    KitsuApiClient.refreshToken(refreshToken!!)
                } catch (e: KitsuApiClient.KitsuTokenException) {
                    if (e.status == 400 || e.status == 401) {
                        markSessionExpired(context, "kitsu")
                        return@withContext null
                    }
                    null
                } catch (_: Exception) {
                    null
                }
                if (refreshed != null) {
                    saveKitsuAuth(
                        context = context,
                        token = refreshed.accessToken,
                        refreshToken = refreshed.refreshToken,
                        userId = p.getString(KEY_KITSU_USER_ID, "") ?: "",
                        username = p.getString(KEY_KITSU_USERNAME, "Kitsu") ?: "Kitsu",
                        expiresInSeconds = refreshed.expiresIn,
                        notify = false
                    )
                    return@withContext refreshed.accessToken
                }
            }
            token
        } finally {
            kitsuRefreshMutex.unlock()
        }
    }

    /**
     * Kitsu uçlarından HTTP 401 alındığında zorla yeni `access_token` üretir (`E85`).
     * Refresh token da geçersizse oturumu "süresi doldu" olarak işaretler.
     */
    suspend fun forceRefreshKitsuToken(context: Context): String? = withContext(Dispatchers.IO) {
        kitsuRefreshMutex.lock()
        try {
            val p = prefs(context)
            val refreshToken = p.getString(KEY_KITSU_REFRESH_TOKEN, null)
            if (refreshToken.isNullOrBlank()) {
                markSessionExpired(context, "kitsu")
                return@withContext null
            }
            val refreshed = try {
                KitsuApiClient.refreshToken(refreshToken)
            } catch (e: KitsuApiClient.KitsuTokenException) {
                if (e.status == 400 || e.status == 401) {
                    markSessionExpired(context, "kitsu")
                }
                null
            } catch (_: Exception) {
                null
            }
            if (refreshed != null) {
                saveKitsuAuth(
                    context = context,
                    token = refreshed.accessToken,
                    refreshToken = refreshed.refreshToken,
                    userId = p.getString(KEY_KITSU_USER_ID, "") ?: "",
                    username = p.getString(KEY_KITSU_USERNAME, "Kitsu") ?: "Kitsu",
                    expiresInSeconds = refreshed.expiresIn,
                    notify = false
                )
                refreshed.accessToken
            } else {
                null
            }
        } finally {
            kitsuRefreshMutex.unlock()
        }
    }

    fun saveKitsuLibraryEntryId(context: Context, mediaId: Int, isAnime: Boolean, entryId: String) {
        val key = "kitsu_entry_${getKitsuUserId(context) ?: error("Kitsu kullanıcı kimliği yok")}_${if (isAnime) "a" else "m"}_$mediaId"
        prefs(context).edit().putString(key, entryId).apply()
    }

    fun getKitsuLibraryEntryId(context: Context, mediaId: Int, isAnime: Boolean): String? {
        val key = "kitsu_entry_${getKitsuUserId(context) ?: error("Kitsu kullanıcı kimliği yok")}_${if (isAnime) "a" else "m"}_$mediaId"
        return prefs(context).getString(key, null)
    }

    fun removeKitsuLibraryEntryId(context: Context, mediaId: Int, isAnime: Boolean) {
        val key = "kitsu_entry_${getKitsuUserId(context) ?: error("Kitsu kullanıcı kimliği yok")}_${if (isAnime) "a" else "m"}_$mediaId"
        prefs(context).edit().remove(key).apply()
    }

    fun saveKitsuMediaIdForMal(context: Context, malId: Int, isAnime: Boolean, mediaId: Int) {
        if (malId <= 0 || mediaId <= 0) return
        val kind = if (isAnime) "a" else "m"
        val userScope = getKitsuUserId(context)?.takeIf { it.isNotBlank() }
        val editor = prefs(context).edit()
            .putInt("kitsu_media_global_${kind}_$malId", mediaId)
        if (userScope != null) {
            editor.putInt("kitsu_media_${userScope}_${kind}_$malId", mediaId)
        }
        editor.apply()
    }

    fun getKitsuMediaIdForMal(context: Context, malId: Int, isAnime: Boolean): Int? {
        if (malId <= 0) return null
        val kind = if (isAnime) "a" else "m"
        val p = prefs(context)
        val userScope = getKitsuUserId(context)?.takeIf { it.isNotBlank() }
        if (userScope != null) {
            p.getInt("kitsu_media_${userScope}_${kind}_$malId", 0).takeIf { it > 0 }?.let { return it }
        }
        return p.getInt("kitsu_media_global_${kind}_$malId", 0).takeIf { it > 0 }
    }

    /**
     * Kayıtlı tüm `kitsu_media_<scope>_<a|m>_<malId> → kitsuMediaId` eşlemelerinin anlık
     * görüntüsü. Katalog kimliği kullanıcıdan bağımsız olduğu için tüm geçerli anahtarlar
     * okunur; `MyListIdentityCrosswalk` bu haritayı tersine çevirerek Kitsu satırlarını
     * çevrimdışı MAL/AniList gruplarına bağlar.
     */
    fun kitsuMediaMappingSnapshot(context: Context): Map<String, Int> = runCatching {
        prefs(context).all.mapNotNull { (key, value) ->
            if (!key.startsWith("kitsu_media_")) return@mapNotNull null
            val id = (value as? Number)?.toInt()?.takeIf { it > 0 } ?: return@mapNotNull null
            key to id
        }.toMap()
    }.getOrDefault(emptyMap())

    fun findMalIdForKitsuMedia(context: Context, kitsuMediaId: Int, isAnime: Boolean): Int? {
        if (kitsuMediaId <= 0) return null
        val suffixPrefix = "_${if (isAnime) "a" else "m"}_"
        val matches = LinkedHashSet<Int>()
        kitsuMediaMappingSnapshot(context).forEach { (key, mappedKitsuId) ->
            if (mappedKitsuId != kitsuMediaId) return@forEach
            val idx = key.lastIndexOf(suffixPrefix)
            if (idx < 0) return@forEach
            val malId = key.substring(idx + suffixPrefix.length).toIntOrNull()
                ?.takeIf { it in 1..99_999_999 }
                ?: return@forEach
            matches += malId
        }
        return matches.singleOrNull()
    }

    fun getShikimoriToken(context: Context): String? = prefs(context).getString(KEY_SHIKIMORI_TOKEN, null)
    fun getShikimoriUserId(context: Context): Int? {
        val id = prefs(context).getInt(KEY_SHIKIMORI_USER_ID, 0)
        return if (id > 0) id else null
    }
    fun getShikimoriUsername(context: Context): String? = prefs(context).getString(KEY_SHIKIMORI_USERNAME, null)

    fun getShikimoriClientId(context: Context): String {
        val saved = prefs(context).getString(KEY_SHIKIMORI_CLIENT_ID, null)?.trim()
        if (saved.isNullOrBlank() || saved.startsWith("aOAY", ignoreCase = true) || saved == "aOAYRqOLwxpA8skpcQIXetNy4cw2rn2fRzScawlcQ5U") {
            // Corrupted or legacy typo'd client ID detected - reset to valid default
            prefs(context).edit().remove(KEY_SHIKIMORI_CLIENT_ID).apply()
            return ShikimoriApiClient.DEFAULT_CLIENT_ID
        }
        return saved
    }

    fun getShikimoriClientSecret(context: Context): String {
        val saved = prefs(context).getString(KEY_SHIKIMORI_CLIENT_SECRET, null)?.trim()
        if (saved.isNullOrBlank() || saved.startsWith("jqjm", ignoreCase = true) || saved == "jqjmORn6bh2046ulkm4lHEwJ3OA1RmO3FD2sR9f6Clw") {
            prefs(context).edit().remove(KEY_SHIKIMORI_CLIENT_SECRET).apply()
            return ShikimoriApiClient.DEFAULT_CLIENT_SECRET
        }
        return saved
    }

    fun saveShikimoriCredentials(context: Context, clientId: String, clientSecret: String) {
        val cleanId = clientId.trim()
        val cleanSecret = clientSecret.trim()
        if (cleanId == ShikimoriApiClient.DEFAULT_CLIENT_ID && cleanSecret == ShikimoriApiClient.DEFAULT_CLIENT_SECRET) {
            prefs(context).edit()
                .remove(KEY_SHIKIMORI_CLIENT_ID)
                .remove(KEY_SHIKIMORI_CLIENT_SECRET)
                .apply()
        } else {
            prefs(context).edit()
                .putString(KEY_SHIKIMORI_CLIENT_ID, cleanId)
                .putString(KEY_SHIKIMORI_CLIENT_SECRET, cleanSecret)
                .apply()
        }
    }

    fun resetShikimoriCredentials(context: Context) {
        prefs(context).edit()
            .remove(KEY_SHIKIMORI_CLIENT_ID)
            .remove(KEY_SHIKIMORI_CLIENT_SECRET)
            .apply()
    }

    /** Yetkilendirme akışı başlatılırken tarayıcıya verilen redirect_uri'yi kaydeder. */
    fun saveShikimoriPendingRedirectUri(context: Context, redirectUri: String) {
        if (redirectUri.isBlank()) return
        prefs(context).edit().putString(KEY_SHIKIMORI_PENDING_REDIRECT, redirectUri.trim()).apply()
    }

    /** Son akışta kullanılan redirect_uri (yoksa null). */
    fun getShikimoriPendingRedirectUri(context: Context): String? =
        prefs(context).getString(KEY_SHIKIMORI_PENDING_REDIRECT, null)?.trim()?.takeIf { it.isNotBlank() }

    fun clearShikimoriPendingRedirectUri(context: Context) {
        prefs(context).edit().remove(KEY_SHIKIMORI_PENDING_REDIRECT).apply()
    }

    /**
     * @param notify false ise Success olayı yayınlamaz (placeholder kayıtlar için;
     * böylece çift list aktarımı tetiklenmez).
     */
    fun saveShikimoriAuth(
        context: Context,
        token: String,
        refreshToken: String,
        expiresInSeconds: Long,
        userId: Int,
        username: String,
        notify: Boolean = true
    ) {
        val expiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000L)
        prefs(context).edit()
            .putString(KEY_SHIKIMORI_TOKEN, token)
            .putString(KEY_SHIKIMORI_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_SHIKIMORI_TOKEN_EXPIRES_AT, expiresAt)
            .putInt(KEY_SHIKIMORI_USER_ID, userId)
            .putString(KEY_SHIKIMORI_USERNAME, username)
            .remove(sessionExpiredKey("shikimori"))
            .apply()
        if (notify) _authEvents.tryEmit(AuthEvent.Success("shikimori"))
    }

    suspend fun getOrRefreshShikimoriToken(context: Context): String? = withContext(Dispatchers.IO) {
        shikimoriRefreshMutex.lock()
        try {
            val p = prefs(context)
            val token = p.getString(KEY_SHIKIMORI_TOKEN, null) ?: return@withContext null
            val expiresAt = p.getLong(KEY_SHIKIMORI_TOKEN_EXPIRES_AT, 0L)
            val refreshToken = p.getString(KEY_SHIKIMORI_REFRESH_TOKEN, null)

            if (System.currentTimeMillis() + 60_000 >= expiresAt && !refreshToken.isNullOrBlank()) {
                val clientId = getShikimoriClientId(context)
                val clientSecret = getShikimoriClientSecret(context)
                if (clientId.isNotBlank() && clientSecret.isNotBlank()) {
                    val refreshResult: ShikimoriApiClient.ShikimoriTokenResponse? = try {
                        ShikimoriApiClient.refreshToken(clientId, clientSecret, refreshToken)
                    } catch (e: ShikimoriApiClient.ShikimoriTokenException) {
                        if (e.status == 400 || e.status == 401) {
                            markSessionExpired(context, "shikimori")
                            return@withContext null
                        }
                        null
                    } catch (_: Exception) {
                        null
                    }
                    if (refreshResult != null) {
                        saveShikimoriAuth(
                            context = context,
                            token = refreshResult.accessToken,
                            refreshToken = refreshResult.refreshToken.ifBlank { refreshToken },
                            expiresInSeconds = refreshResult.expiresIn,
                            userId = p.getInt(KEY_SHIKIMORI_USER_ID, 0),
                            username = p.getString(KEY_SHIKIMORI_USERNAME, "Shikimori") ?: "Shikimori",
                            notify = false
                        )
                        return@withContext refreshResult.accessToken
                    }
                }
            }
            token
        } finally {
            shikimoriRefreshMutex.unlock()
        }
    }

    /**
     * expiresAt kontrolünü atlayarak refresh token ile her koşulda yeni bir
     * Shikimori access token almayı dener (`E90`).
     */
    suspend fun forceRefreshShikimoriToken(context: Context): String? = withContext(Dispatchers.IO) {
        shikimoriRefreshMutex.lock()
        try {
            val p = prefs(context)
            val refreshToken = p.getString(KEY_SHIKIMORI_REFRESH_TOKEN, null)
            if (refreshToken.isNullOrBlank()) {
                markSessionExpired(context, "shikimori")
                return@withContext null
            }

            val clientId = getShikimoriClientId(context)
            val clientSecret = getShikimoriClientSecret(context)
            if (clientId.isBlank() || clientSecret.isBlank()) return@withContext null

            val refreshResult: ShikimoriApiClient.ShikimoriTokenResponse? = try {
                ShikimoriApiClient.refreshToken(clientId, clientSecret, refreshToken)
            } catch (e: ShikimoriApiClient.ShikimoriTokenException) {
                if (e.status == 400 || e.status == 401) {
                    markSessionExpired(context, "shikimori")
                }
                null
            } catch (_: Exception) {
                null
            }

            if (refreshResult != null) {
                saveShikimoriAuth(
                    context = context,
                    token = refreshResult.accessToken,
                    refreshToken = refreshResult.refreshToken.ifBlank { refreshToken },
                    expiresInSeconds = refreshResult.expiresIn,
                    userId = p.getInt(KEY_SHIKIMORI_USER_ID, 0),
                    username = p.getString(KEY_SHIKIMORI_USERNAME, "Shikimori") ?: "Shikimori",
                    notify = false
                )
                refreshResult.accessToken
            } else {
                null
            }
        } finally {
            shikimoriRefreshMutex.unlock()
        }
    }

    /**
     * Kullanıcı kimliği eksikse (giriş sırasında whoami geçici olarak yanıt
     * vermediyse) kayıtlı token ile bir kez çözmeye çalışır.
     * @return Kimlik çözülebildiyse (veya zaten kayıtlıysa) true.
     */
    suspend fun ensureShikimoriUserResolved(context: Context): Boolean = withContext(Dispatchers.IO) {
        val p = prefs(context)
        if (p.getInt(KEY_SHIKIMORI_USER_ID, 0) > 0) return@withContext true
        val token = p.getString(KEY_SHIKIMORI_TOKEN, null)
        if (token.isNullOrBlank()) return@withContext false
        runCatching {
            val user = ShikimoriApiClient.getCurrentUser(token)
            p.edit()
                .putInt(KEY_SHIKIMORI_USER_ID, user.id)
                .putString(KEY_SHIKIMORI_USERNAME, user.nickname)
                .apply()
            SettingsDataStore(context).saveShikimoriProfileInfo(user.nickname, user.avatarUrl)
            true
        }.getOrDefault(false)
    }

    fun saveShikimoriRateId(context: Context, malId: Int, targetType: String, rateId: Int) {
        val key = "shikimori_rate_${getShikimoriUserId(context) ?: error("Shikimori kullanıcı kimliği yok")}_${targetType.lowercase()}_$malId"
        prefs(context).edit().putInt(key, rateId).apply()
    }

    fun getShikimoriRateId(context: Context, malId: Int, targetType: String): Int? {
        val key = "shikimori_rate_${getShikimoriUserId(context) ?: error("Shikimori kullanıcı kimliği yok")}_${targetType.lowercase()}_$malId"
        val id = prefs(context).getInt(key, 0)
        return if (id > 0) id else null
    }

    fun removeShikimoriRateId(context: Context, malId: Int, targetType: String) {
        val key = "shikimori_rate_${getShikimoriUserId(context) ?: error("Shikimori kullanıcı kimliği yok")}_${targetType.lowercase()}_$malId"
        prefs(context).edit().remove(key).apply()
    }

    fun getAuthUrlAndPrepare(context: Context, serviceName: String): String {
        val sharedPreferences = prefs(context)
        if (serviceName != "tmdb") TmdbAuthStore.clearPendingRequestToken(context)
        return when (serviceName) {
            "anilist" -> {
                val codeVerifier = generateCodeVerifier()
                val codeChallenge = generateCodeChallengeS256(codeVerifier)
                sharedPreferences.edit()
                    .putString(KEY_ANILIST_CODE_VERIFIER, codeVerifier)
                    .remove(KEY_MAL_CODE_VERIFIER)
                    .remove(KEY_SIMKL_CODE_VERIFIER)
                    .apply()
                "https://anilist.co/api/v2/oauth/authorize" +
                        "?client_id=$ANILIST_CLIENT_ID" +
                        "&redirect_uri=${URLEncoder.encode(REDIRECT_URI, "UTF-8")}" +
                        "&response_type=code" +
                        "&code_challenge=$codeChallenge" +
                        "&code_challenge_method=S256"
            }
            "mal" -> {
                val codeVerifier = generateCodeVerifier()
                sharedPreferences.edit()
                    .putString(KEY_MAL_CODE_VERIFIER, codeVerifier)
                    .remove(KEY_ANILIST_CODE_VERIFIER)
                    .remove(KEY_SIMKL_CODE_VERIFIER)
                    .apply()
                "https://myanimelist.net/v1/oauth2/authorize?" +
                        "response_type=code" +
                        "&client_id=$MAL_CLIENT_ID" +
                        "&code_challenge=$codeVerifier" +
                        "&code_challenge_method=plain" +
                        "&redirect_uri=${URLEncoder.encode(REDIRECT_URI, "UTF-8")}"
            }
            "simkl" -> {
                val codeVerifier = generateCodeVerifier()
                val codeChallenge = generateCodeChallengeS256(codeVerifier)
                sharedPreferences.edit()
                    .putString(KEY_SIMKL_CODE_VERIFIER, codeVerifier)
                    .remove(KEY_ANILIST_CODE_VERIFIER)
                    .remove(KEY_MAL_CODE_VERIFIER)
                    .apply()
                "https://simkl.com/oauth/authorize" +
                        "?response_type=code" +
                        "&client_id=$SIMKL_CLIENT_ID" +
                        "&redirect_uri=${URLEncoder.encode(REDIRECT_URI, "UTF-8")}" +
                        "&code_challenge=$codeChallenge" +
                        "&code_challenge_method=S256"
            }
            else -> throw IllegalArgumentException("Bilinmeyen servis: $serviceName")
        }
    }

    fun startAuthentication(
        context: Context,
        serviceName: String,
        onError: (String) -> Unit
    ) {
        try {
            val sharedPreferences = prefs(context)
            if (serviceName != "tmdb") TmdbAuthStore.clearPendingRequestToken(context)

            when (serviceName) {
                "tmdb" -> {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val authUrl = TmdbAccountClient.createAuthorizationUrl(context)
                            withContext(Dispatchers.Main) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            val message = authFailureMessage(
                                "tmdb",
                                error.message ?: "TMDB yetkilendirme başlatılamadı",
                                error = error
                            )
                            withContext(Dispatchers.Main) { onError(message) }
                        }
                    }
                }
                "anilist" -> {
                    val codeVerifier = generateCodeVerifier()
                    val codeChallenge = generateCodeChallengeS256(codeVerifier)
                    sharedPreferences.edit()
                        .putString(KEY_ANILIST_CODE_VERIFIER, codeVerifier)
                        .remove(KEY_MAL_CODE_VERIFIER)
                        .remove(KEY_SIMKL_CODE_VERIFIER)
                        .apply()
                    val authUrl = "https://anilist.co/api/v2/oauth/authorize" +
                            "?client_id=$ANILIST_CLIENT_ID" +
                            "&redirect_uri=${URLEncoder.encode(REDIRECT_URI, "UTF-8")}" +
                            "&response_type=code" +
                            "&code_challenge=$codeChallenge" +
                            "&code_challenge_method=S256"
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
                "mal" -> {
                    val codeVerifier = generateCodeVerifier()
                    sharedPreferences.edit()
                        .putString(KEY_MAL_CODE_VERIFIER, codeVerifier)
                        .remove(KEY_ANILIST_CODE_VERIFIER)
                        .remove(KEY_SIMKL_CODE_VERIFIER)
                        .apply()
                    val authUrl = "https://myanimelist.net/v1/oauth2/authorize?" +
                            "response_type=code" +
                            "&client_id=$MAL_CLIENT_ID" +
                            "&code_challenge=$codeVerifier" +
                            "&code_challenge_method=plain" +
                            "&redirect_uri=${URLEncoder.encode(REDIRECT_URI, "UTF-8")}"
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
                "simkl" -> {
                    // Public PKCE flow â€” no client_secret required
                    val codeVerifier = generateCodeVerifier()
                    val codeChallenge = generateCodeChallengeS256(codeVerifier)
                    sharedPreferences.edit()
                        .putString(KEY_SIMKL_CODE_VERIFIER, codeVerifier)
                        .remove(KEY_ANILIST_CODE_VERIFIER)
                        .remove(KEY_MAL_CODE_VERIFIER)
                        .apply()
                    val authUrl = "https://simkl.com/oauth/authorize" +
                            "?response_type=code" +
                            "&client_id=$SIMKL_CLIENT_ID" +
                            "&redirect_uri=${URLEncoder.encode(REDIRECT_URI, "UTF-8")}" +
                            "&code_challenge=$codeChallenge" +
                            "&code_challenge_method=S256"
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
                else -> onError(authFailureMessage(serviceName, "Bilinmeyen servis: $serviceName"))
            }
        } catch (e: Exception) {
            onError(authFailureMessage(serviceName, "Yetkilendirme URL oluÅŸturma hatasÄ±: ${e.message}"))
        }
    }

    fun handleAuthIntent(
        context: Context,
        intent: Intent?,
        onSuccess: (serviceName: String) -> Unit,
        onError: (String) -> Unit
    ) {
        val uri = intent?.data
        if (intent?.action != Intent.ACTION_VIEW || uri == null) return

        // Shikimori deep link (aniyomi://shikimori-auth veya kitsugi://shikimori-auth)
        if ((uri.scheme == "aniyomi" || uri.scheme == "kitsugi") && uri.host == "shikimori-auth") {
            val code = uri.getQueryParameter("code")
            if (code == null) {
                val error = authFailureMessage("shikimori", uri.getQueryParameter("error") ?: "Shikimori yetkilendirme iptal edildi")
                clearShikimoriPendingRedirectUri(context)
                onError(error)
                _authEvents.tryEmit(AuthEvent.Error(error))
                return
            }
            // Token isteğindeki redirect_uri, OAuth yetkilendirmesini başlatan şemayla birebir eşleşmeli.
            val redirectUriUsed = if (uri.scheme == "aniyomi") {
                ShikimoriApiClient.DEEP_LINK_REDIRECT_URI
            } else {
                ShikimoriApiClient.FALLBACK_DEEP_LINK_REDIRECT_URI
            }
            exchangeShikimoriCode(context, code, redirectUriUsed, onSuccess, onError)
            return
        }

        // Bangumi deep link (kitsugi://bangumi-auth veya aniyomi://bangumi-auth)
        if ((uri.scheme == "kitsugi" || uri.scheme == "aniyomi") && uri.host == "bangumi-auth") {
            val bangumiCode = uri.getQueryParameter("code")
            if (bangumiCode == null) {
                val error = authFailureMessage("bangumi", uri.getQueryParameter("error") ?: "Bangumi yetkilendirme iptal edildi")
                BangumiAuthStore.clearPendingRedirectUri(context)
                onError(error)
                _authEvents.tryEmit(AuthEvent.Error(error))
                return
            }
            // Token isteğindeki redirect_uri, authorize adımındaki şemayla birebir eşleşmeli.
            val bangumiRedirect = BangumiAuthStore.redirectUriForScheme(uri.scheme)
            BangumiAuthManager.exchangeCode(
                context = context,
                code = bangumiCode,
                redirectUri = bangumiRedirect,
                onSuccess = onSuccess,
                onError = onError
            )
            return
        }

        if (uri.scheme != "malapp" || uri.host != "auth") return

        // TMDB redirects after approval without an OAuth `code`. Exchange a fresh,
        // callback-bound request token exactly once; never confuse it with the catalog key.
        val pendingTmdbRequest = TmdbAuthStore.pendingRequestToken(context)
        val callbackTmdbRequest = uri.getQueryParameter("request_token")
        val tmdbCallbackShape = pendingTmdbRequest != null || callbackTmdbRequest != null ||
            uri.getQueryParameter("approved") != null
        if (tmdbCallbackShape && uri.getQueryParameter("code") == null) {
            val denied = uri.getQueryParameter("error")
            val notApproved = uri.getQueryParameter("approved")?.equals("false", ignoreCase = true) == true ||
                uri.getQueryParameter("denied")?.equals("true", ignoreCase = true) == true
            if (denied != null || notApproved) {
                // A delayed denial from an old browser must not erase a newer login attempt.
                // The callback-bound token remains usable until its short TTL or a new login.
                val reason = denied ?: "kullanıcı onay vermedi"
                val failure = IllegalStateException("TMDB-41: TMDB yetkilendirme iptal edildi: $reason")
                val message = authFailureMessage("tmdb", failure.message.orEmpty(), error = failure)
                onError(message)
                _authEvents.tryEmit(AuthEvent.Error(message))
                return
            }
            val requestToken = TmdbAuthStore.consumePendingRequestToken(context, callbackTmdbRequest)
            if (requestToken == null) {
                val failure = IllegalStateException(
                    "TMDB-33: TMDB giriş isteğinin süresi doldu, kullanıldı veya callback eşleşmedi. Yeniden giriş yapın."
                )
                val message = authFailureMessage("tmdb", failure.message.orEmpty(), error = failure)
                onError(message)
                _authEvents.tryEmit(AuthEvent.Error(message))
                return
            }
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val session = TmdbAccountClient.createSession(requestToken)
                    TmdbAccountOperationMutex.lock.withLock {
                        TmdbAuthStore.saveSession(
                            context = context,
                            sessionId = session.sessionId,
                            accountId = session.accountId,
                            username = session.username
                        )
                    }
                    withContext(Dispatchers.Main) { onSuccess("tmdb") }
                    _authEvents.tryEmit(AuthEvent.Success("tmdb"))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    // The token was already consumed. Do not clear a newer login attempt.
                    val message = authFailureMessage(
                        "tmdb",
                        error.message ?: "TMDB oturumu oluşturulamadı",
                        error = error
                    )
                    withContext(Dispatchers.Main) { onError(message) }
                    _authEvents.tryEmit(AuthEvent.Error(message))
                }
            }
            return
        }

        val code = uri.getQueryParameter("code")
        if (code == null) {
            val error = uri.getQueryParameter("error")
            val message = authFailureMessage("mal", "Yetkilendirme hatasÄ±: $error")
            onError(message)
            _authEvents.tryEmit(AuthEvent.Error(message))
            return
        }

        val prefs = prefs(context)
        val malVerifier    = prefs.getString(KEY_MAL_CODE_VERIFIER, null)
        val anilistVerifier = prefs.getString(KEY_ANILIST_CODE_VERIFIER, null)
        val simklVerifier  = prefs.getString(KEY_SIMKL_CODE_VERIFIER, null)

        when {
            simklVerifier != null -> exchangeSimklCodeForToken(
                context = context, code = code, codeVerifier = simklVerifier,
                onSuccess = {
                    onSuccess("simkl")
                    _authEvents.tryEmit(AuthEvent.Success("simkl"))
                },
                onError = { msg ->
                    onError(msg)
                    _authEvents.tryEmit(AuthEvent.Error(msg))
                }
            )
            malVerifier != null -> exchangeMalCodeForToken(
                context = context, code = code, codeVerifier = malVerifier,
                onSuccess = {
                    onSuccess("mal")
                    _authEvents.tryEmit(AuthEvent.Success("mal"))
                },
                onError = { msg ->
                    onError(msg)
                    _authEvents.tryEmit(AuthEvent.Error(msg))
                }
            )
            anilistVerifier != null -> exchangeAniListCodeForToken(
                context = context, code = code, codeVerifier = anilistVerifier,
                onSuccess = {
                    onSuccess("anilist")
                    _authEvents.tryEmit(AuthEvent.Success("anilist"))
                },
                onError = { msg ->
                    onError(msg)
                    _authEvents.tryEmit(AuthEvent.Error(msg))
                }
            )
            else -> {
                val message = KitsugiStrings.get(R.string.kod_alindi_ancak_servis_icin_code_verifier_b)
                onError(message)
                _authEvents.tryEmit(AuthEvent.Error(message))
            }
        }
    }

    fun exchangeShikimoriCode(
        context: Context,
        code: String,
        redirectUri: String = ShikimoriApiClient.DEEP_LINK_REDIRECT_URI,
        onSuccess: (serviceName: String) -> Unit,
        onError: (String) -> Unit
    ) {
        val clientId = getShikimoriClientId(context)
        val clientSecret = getShikimoriClientSecret(context)
        CoroutineScope(Dispatchers.IO).launch {
            // Kod tek kullanımlıktır: takas başarılı olduğunda token DERHAL saklanır.
            // Profil (whoami) bakımı geçici olarak başarısız olsa bile geçerli oturum
            // çöpe atılmaz; kullanıcı kimliği ilk kullanımda tembel çözümlenir.
            runCatching {
                ShikimoriApiClient.exchangeCodeForToken(
                    clientId = clientId,
                    clientSecret = clientSecret,
                    code = code,
                    redirectUri = redirectUri
                )
            }.onSuccess { tokenResp ->
                saveShikimoriAuth(
                    context = context,
                    token = tokenResp.accessToken,
                    refreshToken = tokenResp.refreshToken,
                    expiresInSeconds = tokenResp.expiresIn,
                    userId = 0,
                    username = "Shikimori",
                    notify = false
                )
                val user = runCatching { ShikimoriApiClient.getCurrentUser(tokenResp.accessToken) }.getOrNull()
                if (user != null) {
                    saveShikimoriAuth(
                        context = context,
                        token = tokenResp.accessToken,
                        refreshToken = tokenResp.refreshToken,
                        expiresInSeconds = tokenResp.expiresIn,
                        userId = user.id,
                        username = user.nickname
                    )
                    SettingsDataStore(context).saveShikimoriProfileInfo(user.nickname, user.avatarUrl)
                } else {
                    // Kimlik henüz çözülemedi: yine de tek bir Success yayınlamak
                    // otomatik aktarımı başlatır; aktarım kimliği tembel çözer.
                    _authEvents.tryEmit(AuthEvent.Success("shikimori"))
                }
                withContext(Dispatchers.Main) {
                    onSuccess("shikimori")
                }
            }.onFailure { err ->
                val msg = authFailureMessage("shikimori", err.message ?: "Shikimori token değişimi başarısız", error = err)
                withContext(Dispatchers.Main) {
                    onError(msg)
                    _authEvents.tryEmit(AuthEvent.Error(msg))
                }
            }
            clearShikimoriPendingRedirectUri(context)
        }
    }

    private fun exchangeSimklCodeForToken(
        context: Context,
        code: String,
        codeVerifier: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        thread {
            try {
                val jsonPayload = """{"code":"$code","client_id":"$SIMKL_CLIENT_ID","code_verifier":"$codeVerifier","redirect_uri":"$REDIRECT_URI","grant_type":"authorization_code"}"""
                val request = Request.Builder()
                    .url("https://api.simkl.com/oauth/token")
                    .post(jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .build()
                com.kitsugi.animelist.core.network.KitsugiHttpClient.client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val accessToken = JSONObject(response.body?.string().orEmpty()).getString("access_token")
                        prefs(context).edit()
                            .putString(KEY_SIMKL_TOKEN, accessToken)
                            .remove(KEY_SIMKL_CODE_VERIFIER)
                            .remove("simkl_session_expired")
                            .apply()
                        onSuccess()
                    } else {
                        prefs(context).edit().remove(KEY_SIMKL_CODE_VERIFIER).apply()
                        onError(authFailureMessage("simkl", "Simkl token deÄŸiÅŸimi baÅŸarÄ±sÄ±z: ${response.code} - ${response.body?.string().orEmpty()}", httpCode = response.code))
                    }
                }
            } catch (e: Exception) {
                prefs(context).edit().remove(KEY_SIMKL_CODE_VERIFIER).apply()
                onError(authFailureMessage("simkl", "Simkl token exception: ${e.message}", error = e))
            }
        }
    }

    /**
     * Bu sağlayıcıya ait **daha önce kurulmuş bir hesabın izi** duruyor mu?
     *
     * Amaç: "bağlı değil" sonucunun gerçek bir değişiklik kaybı mı (oturum/token süresi
     * dolmuş, hesap hâlâ bağlı) yoksa kullanıcının hiç kullanmadığı bir servis mi olduğunu
     * ayırmak. [disconnectAccount] token'ları **ve** kullanıcı kimliklerini temizlediği için
     * kalan bir yenileme token'ı / kullanıcı kimliği "hesap bağlıydı, oturum düştü" demektir.
     *
     * Yalnız bu durumda değişiklik yeniden deneme kuyruğuna yazılır; hiç bağlanmamış
     * servisler için gereksiz uyarı/kuyruk üretilmez.
     */
    private fun canonicalServiceKey(serviceName: String): String = when (serviceName.trim().lowercase()) {
        "mal", "myanimelist" -> "mal"
        "anilist" -> "anilist"
        "simkl" -> "simkl"
        "kitsu" -> "kitsu"
        "shikimori" -> "shikimori"
        "bangumi", "bgm", "bgm.tv" -> "bangumi"
        else -> serviceName.trim().lowercase()
    }

    internal fun sessionExpiredKey(serviceName: String): String =
        "${canonicalServiceKey(serviceName)}_session_expired"

    fun isSessionExpired(context: Context, serviceName: String): Boolean =
        prefs(context).getBoolean(sessionExpiredKey(serviceName), false)

    fun clearSessionExpired(context: Context, serviceName: String) {
        prefs(context).edit().remove(sessionExpiredKey(serviceName)).apply()
    }

    /**
     * Sunucunun erişim/yenileme anahtarını reddettiği durumda ölü token'ları temizler ama
     * hesap kimliğini ve `${provider}_session_expired` bayrağını korur (`E91`/`E95`).
     *
     * Böylece:
     *  1. Ayarlar ekranı hangi hesabın oturumunun düştüğünü açıkça gösterir,
     *  2. [hasStoredAccount] `true` döner → bu hesap tekrar bağlanana kadar yapılan tüm
     *     düzenlemeler `PendingSyncQueue` içinde kayıpsız bekler,
     *  3. [AuthEvent.SessionExpired] yayını arayüzü anında günceller.
     */
    fun markSessionExpired(context: Context, serviceName: String) {
        val canonical = canonicalServiceKey(serviceName)
        val editor = prefs(context).edit().putBoolean(sessionExpiredKey(canonical), true)
        when (canonical) {
            "anilist" -> editor.remove(KEY_ANILIST_TOKEN)
            "mal" -> {
                editor.remove(KEY_MAL_TOKEN)
                editor.remove(KEY_MAL_REFRESH_TOKEN)
                editor.remove(KEY_MAL_TOKEN_EXPIRES_AT)
            }
            "simkl" -> editor.remove(KEY_SIMKL_TOKEN)
            "kitsu" -> {
                editor.remove(KEY_KITSU_TOKEN)
                editor.remove(KEY_KITSU_REFRESH_TOKEN)
                editor.remove(KEY_KITSU_TOKEN_EXPIRES_AT)
            }
            "shikimori" -> {
                editor.remove(KEY_SHIKIMORI_TOKEN)
                editor.remove(KEY_SHIKIMORI_REFRESH_TOKEN)
                editor.remove(KEY_SHIKIMORI_TOKEN_EXPIRES_AT)
            }
            "bangumi" -> BangumiAuthStore.dropSession(context)
        }
        editor.apply()
        _authEvents.tryEmit(AuthEvent.SessionExpired(canonical))
    }

    fun hasStoredAccount(context: Context, serviceName: String): Boolean {
        val p = prefs(context)
        val canonical = canonicalServiceKey(serviceName)
        if (p.getBoolean(sessionExpiredKey(canonical), false)) return true
        return when (canonical) {
            "mal" ->
                !p.getString(KEY_MAL_REFRESH_TOKEN, null).isNullOrBlank() ||
                    !p.getString(KEY_MAL_TOKEN, null).isNullOrBlank()
            "kitsu" ->
                !p.getString(KEY_KITSU_REFRESH_TOKEN, null).isNullOrBlank() ||
                    !p.getString(KEY_KITSU_USER_ID, null).isNullOrBlank()
            "shikimori" ->
                !p.getString(KEY_SHIKIMORI_REFRESH_TOKEN, null).isNullOrBlank() ||
                    (p.getInt(KEY_SHIKIMORI_USER_ID, 0) > 0)
            "bangumi" ->
                BangumiAuthStore.isConnected(context) ||
                    !BangumiAuthStore.getUsername(context).isNullOrBlank()
            else -> false
        }
    }

    fun disconnectAccount(
        context: Context,
        serviceName: String
    ) {
        com.kitsugi.animelist.data.auth.CrossProviderIdentityCache.clear(context)
        // Başka bir hesaba geçildiğinde "hedefte kayıt VAR" bilgisi geçersizdir: eski hesabın
        // kayıtları yeni hesapta yoktur. Temizlenmezse yeni hesapta eksik kayıtlar hiç
        // oluşturulmaz ("zaten eşit" denip geçilirdi).
        RemoteListPresence.clear(context)
        val canonical = canonicalServiceKey(serviceName)
        val editor = prefs(context).edit()
            .remove(sessionExpiredKey(canonical))
        when (canonical) {
            "anilist" -> editor.remove(KEY_ANILIST_TOKEN)
            "simkl" -> editor.remove(KEY_SIMKL_TOKEN)
            "kitsu" -> {
                editor.remove(KEY_KITSU_TOKEN)
                editor.remove(KEY_KITSU_REFRESH_TOKEN)
                editor.remove(KEY_KITSU_TOKEN_EXPIRES_AT)
                editor.remove(KEY_KITSU_USER_ID)
                editor.remove(KEY_KITSU_USERNAME)
            }
            "shikimori" -> {
                editor.remove(KEY_SHIKIMORI_TOKEN)
                editor.remove(KEY_SHIKIMORI_REFRESH_TOKEN)
                editor.remove(KEY_SHIKIMORI_TOKEN_EXPIRES_AT)
                editor.remove(KEY_SHIKIMORI_USER_ID)
                editor.remove(KEY_SHIKIMORI_USERNAME)
            }
            "bangumi" -> {
                editor.apply()
                BangumiAuthStore.logout(context)
                return
            }
            "tmdb" -> {
                TmdbAuthStore.clear(context)
                return
            }
            else -> {
                editor.remove(KEY_MAL_TOKEN)
                editor.remove(KEY_MAL_REFRESH_TOKEN)
                editor.remove(KEY_MAL_TOKEN_EXPIRES_AT)
            }
        }
        editor.apply()
    }

    /** Suspends instead of blocking the UI while an account snapshot/write is finishing. */
    suspend fun disconnectAccountCoordinated(context: Context, serviceName: String) {
        if (serviceName.equals("tmdb", ignoreCase = true)) {
            TmdbAccountOperationMutex.lock.withLock { TmdbAuthStore.clear(context) }
        } else {
            disconnectAccount(context, serviceName)
        }
    }

    private fun exchangeAniListCodeForToken(
        context: Context,
        code: String,
        codeVerifier: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        thread {
            try {
                // JSON'u elle dizmek yerine JSONObject: kod/secret içinde tırnak veya
                // kaçış karakteri olursa gövde bozuluyor ve AniList "invalid_request"
                // döndürüyordu (gerçek neden hiç görünmüyordu).
                val jsonPayload = JSONObject()
                    .put("grant_type", "authorization_code")
                    .put("client_id", ANILIST_CLIENT_ID)
                    .put("client_secret", ANILIST_CLIENT_SECRET)
                    .put("redirect_uri", REDIRECT_URI)
                    .put("code", code)
                    .put("code_verifier", codeVerifier)
                    .toString()

                val request = Request.Builder()
                    .url("https://anilist.co/api/v2/oauth/token")
                    .post(jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .build()

                com.kitsugi.animelist.core.network.KitsugiHttpClient.client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseText = response.body?.string() ?: ""
                        val accessToken = JSONObject(responseText).getString("access_token")

                        prefs(context).edit()
                            .putString(KEY_ANILIST_TOKEN, accessToken)
                            .remove(KEY_ANILIST_CODE_VERIFIER)
                            .remove(sessionExpiredKey("anilist"))
                            .apply()

                        onSuccess()
                    } else {
                        val errorResponse = response.body?.string().orEmpty()

                        prefs(context).edit()
                            .remove(KEY_ANILIST_CODE_VERIFIER)
                            .apply()

                        val hint = if (response.code == 400 || response.code == 401) {
                            " (Kod tek kullanımlıktır: bağlantıyı tekrar başlatın.)"
                        } else ""
                        onError(authFailureMessage("anilist", "AniList token değişimi başarısız: ${response.code} - $errorResponse$hint", httpCode = response.code))
                    }
                }
            } catch (e: Exception) {
                prefs(context).edit()
                    .remove(KEY_ANILIST_CODE_VERIFIER)
                    .apply()

                onError(authFailureMessage("anilist", "AniList token deÄŸiÅŸimi exception: ${e.message}", error = e))
            }
        }
    }

    private fun exchangeMalCodeForToken(
        context: Context,
        code: String,
        codeVerifier: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        thread {
            try {
                val secretParam = if (MAL_CLIENT_SECRET.isNotBlank()) "&client_secret=$MAL_CLIENT_SECRET" else ""
                val postData =
                    "client_id=$MAL_CLIENT_ID" +
                            secretParam +
                            "&code=$code" +
                            "&code_verifier=$codeVerifier" +
                            "&grant_type=authorization_code" +
                            "&redirect_uri=${URLEncoder.encode(REDIRECT_URI, "UTF-8")}"

                val request = Request.Builder()
                    .url("https://myanimelist.net/v1/oauth2/token")
                    .post(postData.toRequestBody("application/x-www-form-urlencoded; charset=utf-8".toMediaTypeOrNull()))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .build()

                com.kitsugi.animelist.core.network.KitsugiHttpClient.client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseText = response.body?.string() ?: ""
                        val jsonObj = JSONObject(responseText)
                        val accessToken = jsonObj.getString("access_token")
                        val refreshToken = jsonObj.optString("refresh_token", "")
                        val expiresIn = jsonObj.optLong("expires_in", 2419200L)
                        val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)

                        prefs(context).edit()
                            .putString(KEY_MAL_TOKEN, accessToken)
                            .putString(KEY_MAL_REFRESH_TOKEN, refreshToken)
                            .putLong(KEY_MAL_TOKEN_EXPIRES_AT, expiresAt)
                            .remove(KEY_MAL_CODE_VERIFIER)
                            .remove(sessionExpiredKey("mal"))
                            .apply()

                        onSuccess()
                    } else {
                        val errorResponse = response.body?.string().orEmpty()

                        prefs(context).edit()
                            .remove(KEY_MAL_CODE_VERIFIER)
                            .apply()

                        if (errorResponse.contains("invalid_client", ignoreCase = true) ||
                            response.code == 401
                        ) {
                            onError(authFailureMessage("mal",
                                "MAL istemci kimliği (client_id) reddedildi. Bu sürümde kullanılan MAL " +
                                    "API anahtarı geçersiz. Geliştirici: myanimelist.net/apiconfig üzerinden " +
                                    "kendi uygulamanızı oluşturup local.properties içine mal_client_id olarak ekleyin."
                            ))
                        } else {
                            onError(authFailureMessage("mal", "MAL token değişimi başarısız: ${response.code} - $errorResponse", httpCode = response.code))
                        }
                    }
                }
            } catch (e: Exception) {
                prefs(context).edit()
                    .remove(KEY_MAL_CODE_VERIFIER)
                    .apply()

                onError(authFailureMessage("mal", "MAL token deÄŸiÅŸimi exception: ${e.message}", error = e))
            }
        }
    }

    private fun generateCodeVerifier(): String {
        val secureRandom = SecureRandom()
        val codeVerifier = ByteArray(32)
        secureRandom.nextBytes(codeVerifier)
        return Base64.encodeToString(
            codeVerifier,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    private fun generateCodeChallengeS256(
        verifier: String
    ): String {
        val bytes = verifier.toByteArray(StandardCharsets.US_ASCII)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return Base64.encodeToString(
            digest,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    @Synchronized
    fun refreshMalTokenSync(context: Context): String? {
        val prefs = prefs(context)
        val refreshToken = prefs.getString(KEY_MAL_REFRESH_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null

        try {
            val formBuilder = okhttp3.FormBody.Builder()
                .add("client_id", MAL_CLIENT_ID)
                .add("refresh_token", refreshToken)
                .add("grant_type", "refresh_token")
            if (MAL_CLIENT_SECRET.isNotBlank()) {
                formBuilder.add("client_secret", MAL_CLIENT_SECRET)
            }

            val request = Request.Builder()
                .url("https://myanimelist.net/v1/oauth2/token")
                .post(formBuilder.build())
                .header("Accept", "application/json")
                .build()

            com.kitsugi.animelist.core.network.KitsugiHttpClient.client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseText = response.body?.string() ?: ""
                    val jsonObj = JSONObject(responseText)
                    val newAccessToken = jsonObj.getString("access_token")
                    val newRefreshToken = jsonObj.optString("refresh_token", refreshToken).ifBlank { refreshToken }
                    val expiresIn = jsonObj.optLong("expires_in", 2419200L)
                    val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)

                    prefs.edit()
                        .putString(KEY_MAL_TOKEN, newAccessToken)
                        .putString(KEY_MAL_REFRESH_TOKEN, newRefreshToken)
                        .putLong(KEY_MAL_TOKEN_EXPIRES_AT, expiresAt)
                        .remove(sessionExpiredKey("mal"))
                        .apply()

                    return newAccessToken
                } else {
                    if (response.code == 400 || response.code == 401) {
                        markSessionExpired(context, "mal")
                    }
                    return null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ExternalAuthManager", "refreshMalTokenSync failed: ${e.message}", e)
            return null
        }
    }

    suspend fun getOrRefreshMalToken(context: Context): String? = withContext(Dispatchers.IO) {
        malRefreshMutex.lock()
        try {
            val prefs = prefs(context)
            val token = prefs.getString(KEY_MAL_TOKEN, null) ?: return@withContext null
            val expiresAt = prefs.getLong(KEY_MAL_TOKEN_EXPIRES_AT, 0L)

            if (System.currentTimeMillis() + 60_000 >= expiresAt) {
                val refreshed = refreshMalTokenSync(context)
                // Geçici ağ/DNS kesintisinde [refreshMalTokenSync] null döner ama token'ı silmez.
                // Bu durumda mevcut token döndürülür ki çağıran "bağlı değil" yerine gerçek ağ
                // hatası alıp işi çevrimdışı kuyruğa (`PendingSyncQueue`) yazabilsin (`E89`).
                refreshed ?: prefs.getString(KEY_MAL_TOKEN, null)
            } else {
                token
            }
        } finally {
            malRefreshMutex.unlock()
        }
    }

    suspend fun forceRefreshMalToken(context: Context): String? = withContext(Dispatchers.IO) {
        malRefreshMutex.lock()
        try {
            val prefs = prefs(context)
            if (prefs.getString(KEY_MAL_REFRESH_TOKEN, null).isNullOrBlank()) {
                markSessionExpired(context, "mal")
                return@withContext null
            }
            refreshMalTokenSync(context)
        } finally {
            malRefreshMutex.unlock()
        }
    }

    suspend fun getSimklPinSuspend(context: Context): Pair<String, String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.simkl.com/oauth/pin?client_id=$SIMKL_CLIENT_ID")
            .header("Accept", "application/json")
            .build()
        com.kitsugi.animelist.core.network.KitsugiHttpClient.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Simkl PIN alÄ±namadÄ±: ${response.code}")
            val json = JSONObject(response.body?.string().orEmpty())
            val userCode = json.getString("user_code")
            val verificationUrl = json.getString("verification_url")
            userCode to verificationUrl
        }
    }

    /**
     * Polls Simkl once for a token. Returns true if the token was acquired and
     * saved, false if the user hasn't approved yet (400/404). Throws on real errors.
     */
    suspend fun pollSimklTokenSuspend(context: Context, userCode: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.simkl.com/oauth/pin/$userCode?client_id=$SIMKL_CLIENT_ID")
            .header("Accept", "application/json")
            .build()
        com.kitsugi.animelist.core.network.KitsugiHttpClient.client.newCall(request).execute().use { response ->
            when {
                response.isSuccessful -> {
                    val json = JSONObject(response.body?.string().orEmpty())
                    if (json.has("access_token")) {
                        prefs(context).edit()
                            .putString(KEY_SIMKL_TOKEN, json.getString("access_token"))
                            .remove("simkl_session_expired")
                            .apply()
                        true
                    } else false
                }
                response.code == 400 || response.code == 404 -> false  // Not yet authorized
                else -> throw Exception("Simkl polling HTTP ${response.code}")
            }
        }
    }

    // Legacy callback wrappers kept for any remaining call-sites
    fun getSimklPin(
        context: Context,
        onSuccess: (userCode: String, verificationUrl: String) -> Unit,
        onError: (String) -> Unit
    ) {
        thread {
            try {
                val request = Request.Builder()
                    .url("https://api.simkl.com/oauth/pin?client_id=$SIMKL_CLIENT_ID")
                    .header("Accept", "application/json")
                    .build()
                com.kitsugi.animelist.core.network.KitsugiHttpClient.client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = JSONObject(response.body?.string().orEmpty())
                        onSuccess(json.getString("user_code"), json.getString("verification_url"))
                    } else onError(authFailureMessage("simkl", "Simkl PIN alÄ±namadÄ±: ${response.code}", httpCode = response.code))
                }
            } catch (e: Exception) { onError(authFailureMessage("simkl", "Simkl PIN hatasÄ±: ${e.message}", error = e)) }
        }
    }

    fun pollSimklToken(
        context: Context,
        userCode: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        thread {
            try {
                val request = Request.Builder()
                    .url("https://api.simkl.com/oauth/pin/$userCode?client_id=$SIMKL_CLIENT_ID")
                    .header("Accept", "application/json")
                    .build()
                com.kitsugi.animelist.core.network.KitsugiHttpClient.client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = JSONObject(response.body?.string().orEmpty())
                        if (json.has("access_token")) {
                            prefs(context).edit()
                                .putString(KEY_SIMKL_TOKEN, json.getString("access_token"))
                                .remove("simkl_session_expired")
                                .apply()
                            onSuccess()
                        } else onError(authFailureMessage("simkl", "Token bulunamadÄ±."))
                    } else onError(authFailureMessage("simkl", "DoÄŸrulama henÃ¼z tamamlanmadÄ±: ${response.code}", httpCode = response.code))
                }
            } catch (e: Exception) { onError(authFailureMessage("simkl", "DoÄŸrulama hatasÄ±: ${e.message}", error = e)) }
        }
    }

    // ── Bangumi (bgm.tv) olay yayını ─────────────────────────────────────────
    // Bangumi oturumu BangumiAuthStore/BangumiAuthManager'da yönetilir; olaylar ise
    // mevcut tek `authEvents` akışından yayınlanır ki arayüz tarafı tek dinleyici kalsın.

    /** Bangumi bağlantısı kuruldu — otomatik içe aktarmayı tetikler. */
    fun emitBangumiSuccess() {
        _authEvents.tryEmit(AuthEvent.Success("bangumi"))
    }

    /** Bangumi akışında hata — giriş diyaloğu bu mesajı gösterir. */
    fun emitBangumiError(message: String) {
        _authEvents.tryEmit(AuthEvent.Error(message))
    }

    /** Bangumi oturumu düştü (refresh token geçersiz) — arayüz bağlantıyı kapatır. */
    fun emitBangumiSessionExpired() {
        _authEvents.tryEmit(AuthEvent.SessionExpired("bangumi"))
    }

    /**
     * Auth hatasını platform hata koduyla üretir ve kayda geçirir.
     * Kod mesaja eklenir (örn. "... (KTS-SMK-8001)"); toast, giriş diyalogu ve
     * AuthEvent aynı kodu gösterir.
     */
    private fun authFailureMessage(
        platformLabel: String,
        message: String,
        httpCode: Int? = null,
        error: Throwable? = null,
    ): String {
        val code = PlatformFailureRegistry.report(
            platformLabel = platformLabel,
            error = error,
            httpCode = httpCode,
            rawMessage = message,
        ) ?: PlatformErrorCode.COM_1020
        return "$message (${code.code})"
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
    }

    /** Other classes (e.g. MalUnifiedAuthManager) can read the same prefs. */
    internal fun getPrefs(context: Context): SharedPreferences = prefs(context)

    fun handleSimkl401(context: Context) {
        disconnectAccount(context, "simkl")
        val settings = com.kitsugi.animelist.data.settings.SettingsDataStore(context)
        CoroutineScope(Dispatchers.IO).launch {
            settings.clearSimklProfileInfo()
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("simkl_session_expired", true)
            .apply()
        _authEvents.tryEmit(AuthEvent.SessionExpired("simkl"))
    }
}