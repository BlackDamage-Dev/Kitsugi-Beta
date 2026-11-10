package com.kitsugi.animelist.core.player

import android.content.Context
import java.net.URI
import java.security.MessageDigest

/**
 * A resume point belongs to one concrete stream, not merely to a series/episode.
 * This prevents switching mirrors (or opening another video for the same episode)
 * from inheriting a position that belongs to a different file.
 */
data class PlayerResumeIdentity(
    val mediaId: Int,
    val season: Int,
    val episode: Int,
    val addonName: String?,
    val sourceName: String?,
    val sourceUrl: String?
) {
    fun storageKey(): String {
        val components = listOf(
            mediaId.toString(),
            season.toString(),
            episode.toString(),
            addonName.orEmpty().trim().lowercase(),
            sourceName.orEmpty().trim().lowercase(),
            stableUrl(sourceUrl)
        )
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(components.joinToString("\u001f").toByteArray(Charsets.UTF_8))
        return "stream_${digest.joinToString("") { "%02x".format(it) }}"
    }

    private fun stableUrl(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return runCatching {
            val uri = URI(raw)
            val volatileQueryKeys = setOf(
                "token", "auth", "authorization", "signature", "sig", "expires",
                "expire", "exp", "key", "policy", "hdnea", "jwt"
            )
            val stableQuery = uri.rawQuery.orEmpty().split('&')
                .filter(String::isNotBlank)
                .filterNot { part ->
                    part.substringBefore('=').substringBefore('%').lowercase() in volatileQueryKeys
                }
                .sorted()
                .joinToString("&")
            buildString {
                append(uri.scheme.orEmpty().lowercase())
                append("://")
                append(uri.host.orEmpty().lowercase())
                append(uri.rawPath.orEmpty())
                if (stableQuery.isNotEmpty()) append('?').append(stableQuery)
            }
        }.getOrElse {
            // Some plugins return non-standard schemes or opaque identifiers.
            raw.substringBefore('#').trim()
        }
    }
}

class PlayerResumeStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "player_stream_resume_v1",
        Context.MODE_PRIVATE
    )

    fun get(identity: PlayerResumeIdentity): Long =
        preferences.getLong(identity.storageKey(), 0L).coerceAtLeast(0L)

    fun save(identity: PlayerResumeIdentity, positionMs: Long, durationMs: Long) {
        if (positionMs <= 0L || durationMs <= 0L) return
        // Avoid persisting the completed tail as a resume point.
        val safePosition = positionMs.coerceAtMost(durationMs)
        if (durationMs - safePosition <= COMPLETION_TAIL_MS) {
            clear(identity)
        } else {
            preferences.edit().putLong(identity.storageKey(), safePosition).apply()
        }
    }

    fun clear(identity: PlayerResumeIdentity) {
        preferences.edit().remove(identity.storageKey()).apply()
    }

    companion object {
        private const val COMPLETION_TAIL_MS = 10_000L
    }
}
