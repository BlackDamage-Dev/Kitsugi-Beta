package com.kitsugi.animelist.data.account

import kotlinx.serialization.json.*
import java.security.MessageDigest

/** Three-way merge: access/refresh/expiry/user ID of one service always move together. */
internal object VaultMerge {
    fun groups(payload: JsonArray): Map<String, JsonArray> = payload.groupBy { entry ->
        val obj = entry.jsonObject
        val key = obj.getValue("k").jsonPrimitive.content
        when (obj["section"]?.jsonPrimitive?.content) {
            "settings" -> "settings:$key"
            // Each installed addon/repository is a separate merge group. This lets two devices
            // add different items concurrently without turning the whole inventory into a conflict.
            "addons" -> "addons:$key"
            else -> "service:${key.substringBefore('_')}"
        }
    }.mapValues { (_, entries) ->
        JsonArray(entries.sortedBy { it.jsonObject.getValue("k").jsonPrimitive.content }.map { entry ->
            val obj = entry.jsonObject
            buildJsonObject {
                put("section", obj["section"] ?: JsonPrimitive("tokens"))
                put("k", obj.getValue("k")); put("t", obj.getValue("t")); put("v", obj.getValue("v"))
            }
        })
    }

    fun hash(value: JsonArray?): String = MessageDigest.getInstance("SHA-256")
        .digest((value ?: JsonArray(emptyList())).toString().toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun hashes(groups: Map<String, JsonArray>): Map<String, String> = groups.mapValues { hash(it.value) }

    /** Don't acknowledge a remote token as installed when it was only preserved in the cloud.
     * Otherwise a later username-only local edit could silently re-upload stale credentials. */
    fun remoteBaselineAfterUpload(local: Map<String, JsonArray>, merged: Map<String, JsonArray>,
        previous: Map<String, String>): Map<String, String> =
        (local.keys + merged.keys + previous.keys).associateWith { group ->
            if (hash(local[group]) == hash(merged[group])) hash(merged[group])
            else previous[group] ?: hash(null)
        }

    data class Merged(val payload: JsonArray, val conflicts: Set<String>)

    fun merge(
        local: Map<String, JsonArray>, remote: Map<String, JsonArray>,
        baseLocal: Map<String, String>, baseRemote: Map<String, String>,
        preferLocalOnConflict: Boolean? = null,
        activeGroups: Set<String>? = null
    ): Merged {
        val conflicts = linkedSetOf<String>()
        val merged = (local.keys + remote.keys + baseLocal.keys + baseRemote.keys).sorted().flatMap { group ->
            val l = local[group]; val r = remote[group]
            val lh = hash(l); val rh = hash(r)
            val chosen = when {
                activeGroups != null && group !in activeGroups -> r // preserve categories the user turned off
                lh == rh -> l
                lh == (baseLocal[group] ?: hash(null)) -> r
                rh == (baseRemote[group] ?: hash(null)) -> l
                else -> {
                    conflicts += group
                    if (preferLocalOnConflict == true) l else r
                }
            }
            chosen?.toList().orEmpty()
        }
        return Merged(JsonArray(merged), conflicts)
    }

    data class Baselines(val local: Map<String, String>, val remote: Map<String, String>)

    /** Update only categories that participated in this transfer; disabled categories keep
     * their old fingerprints so edits made while sync is off are reconciled if it is re-enabled. */
    fun baselineAfterUpload(
        local: Map<String, JsonArray>,
        merged: Map<String, JsonArray>,
        baseLocal: Map<String, String>,
        baseRemote: Map<String, String>,
        activeGroups: Set<String>
    ): Baselines {
        val nextLocal = baseLocal.toMutableMap()
        val nextRemote = baseRemote.toMutableMap()
        activeGroups.forEach { group ->
            nextLocal[group] = hash(local[group])
            nextRemote[group] = if (hash(local[group]) == hash(merged[group])) {
                hash(merged[group])
            } else {
                baseRemote[group] ?: hash(null)
            }
        }
        return Baselines(nextLocal, nextRemote)
    }
}

internal class VaultConflictException(val groups: Set<String>) : IllegalStateException(
    "Aynı hesap farklı cihazlarda değişti: ${groups.joinToString()}. Yedek ezilmedi; hangi kopyayı koruyacağını seç."
)
internal class VaultLockedException : IllegalStateException("Kasa kilitli. Kitsugi hesabına tekrar giriş yap.")
