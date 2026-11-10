package com.kitsugi.animelist.data.auth

import android.content.Context

/** TMDB v3 user session storage. The catalog API key is not a user session. */
object TmdbAuthStore {
    private const val PREFS_NAME = "MyWebViewPrefs"
    private const val SESSION = "tmdb_session_id"
    private const val ACCOUNT_ID = "tmdb_account_id"
    private const val USERNAME = "tmdb_username"
    private const val PENDING_REQUEST = "tmdb_pending_request_token"
    private const val PENDING_REQUEST_SAVED_AT = "tmdb_pending_request_saved_at"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isConnected(context: Context): Boolean =
        !sessionId(context).isNullOrBlank() && accountId(context) != null

    fun sessionId(context: Context): String? = prefs(context).getString(SESSION, null)
    fun accountId(context: Context): Int? = prefs(context).getInt(ACCOUNT_ID, 0).takeIf { it > 0 }

    @Synchronized
    fun matchesSession(context: Context, sessionId: String, accountId: Int): Boolean =
        this.sessionId(context) == sessionId && this.accountId(context) == accountId
    fun username(context: Context): String = prefs(context).getString(USERNAME, null).orEmpty()
    @Synchronized
    fun pendingRequestToken(context: Context): String? {
        val preferences = prefs(context)
        val token = preferences.getString(PENDING_REQUEST, null)?.takeIf { it.isNotBlank() }
        val savedAt = preferences.getLong(PENDING_REQUEST_SAVED_AT, 0L)
        if (token != null && isTmdbRequestTokenUsable(savedAt, System.currentTimeMillis())) return token
        if (preferences.contains(PENDING_REQUEST) || preferences.contains(PENDING_REQUEST_SAVED_AT)) {
            clearPendingRequestToken(context)
        }
        return null
    }

    @Synchronized
    fun savePendingRequestToken(context: Context, token: String) {
        require(token.isNotBlank()) { "TMDB request token boş olamaz" }
        prefs(context).edit()
            .putString(PENDING_REQUEST, token)
            .putLong(PENDING_REQUEST_SAVED_AT, System.currentTimeMillis())
            .apply()
    }

    /**
     * Atomically claims the pending token before the network exchange. A callback token,
     * when supplied by TMDB, must match; a wrong callback does not destroy a valid login.
     */
    @Synchronized
    fun consumePendingRequestToken(context: Context, callbackToken: String? = null): String? {
        val pending = pendingRequestToken(context) ?: return null
        if (!callbackToken.isNullOrBlank() && callbackToken != pending) return null
        // commit() makes the one-time claim durable before a process can die in the exchange.
        val removed = prefs(context).edit()
            .remove(PENDING_REQUEST)
            .remove(PENDING_REQUEST_SAVED_AT)
            .commit()
        return pending.takeIf { removed }
    }

    @Synchronized
    fun clearPendingRequestToken(context: Context) {
        prefs(context).edit()
            .remove(PENDING_REQUEST)
            .remove(PENDING_REQUEST_SAVED_AT)
            .apply()
    }

    @Synchronized
    fun saveSession(context: Context, sessionId: String, accountId: Int, username: String) {
        require(sessionId.isNotBlank()) { "TMDB session boş olamaz" }
        require(accountId > 0) { "TMDB account id geçersiz" }
        prefs(context).edit()
            .putString(SESSION, sessionId)
            .putInt(ACCOUNT_ID, accountId)
            .putString(USERNAME, username)
            .remove(PENDING_REQUEST)
            .remove(PENDING_REQUEST_SAVED_AT)
            .apply()
    }

    @Synchronized
    fun clear(context: Context) {
        prefs(context).edit()
            .remove(SESSION)
            .remove(ACCOUNT_ID)
            .remove(USERNAME)
            .remove(PENDING_REQUEST)
            .remove(PENDING_REQUEST_SAVED_AT)
            .apply()
    }
}
