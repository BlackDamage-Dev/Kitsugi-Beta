package com.kitsugi.animelist.data.account

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class VaultMergeTest {
    private fun tokens(vararg values: Pair<String, String>) = VaultMerge.groups(buildJsonArray {
        values.forEach { (key, value) -> add(buildJsonObject {
            put("k", key); put("t", "s"); put("v", value)
        }) }
    })
    private fun merge(base: Map<String, JsonArray>, local: Map<String, JsonArray>, remote: Map<String, JsonArray>) =
        VaultMerge.merge(local, remote, VaultMerge.hashes(base), VaultMerge.hashes(base))
    private fun addons(vararg values: Pair<String, String>) = VaultMerge.groups(buildJsonArray {
        values.forEach { (key, value) -> add(buildJsonObject {
            put("section", "addons"); put("k", key); put("t", "s"); put("v", value)
        }) }
    })

    @Test fun changesInDifferentServicesCombine() {
        val base = tokens("mal_access_token" to "a", "kitsu_access_token" to "b")
        val local = tokens("mal_access_token" to "a2", "kitsu_access_token" to "b")
        val remote = tokens("mal_access_token" to "a", "kitsu_access_token" to "b2")
        val result = merge(base, local, remote)
        assertTrue(result.conflicts.isEmpty())
        assertEquals(tokens("mal_access_token" to "a2", "kitsu_access_token" to "b2"), VaultMerge.groups(result.payload))
    }

    @Test fun accessAndRefreshTokensNeverMixAcrossDevices() {
        val base = tokens("mal_access_token" to "a", "mal_refresh_token" to "r")
        val local = tokens("mal_access_token" to "a2", "mal_refresh_token" to "r2")
        val remote = tokens("mal_access_token" to "a3", "mal_refresh_token" to "r3")
        assertEquals(setOf("service:mal"), merge(base, local, remote).conflicts)
        val chosen = VaultMerge.merge(local, remote, VaultMerge.hashes(base), VaultMerge.hashes(base), true)
        assertEquals(local, VaultMerge.groups(chosen.payload))
    }

    @Test fun logoutIsAChangeAndDoesNotResurrectTokens() {
        val base = tokens("mal_access_token" to "a")
        val result = merge(base, emptyMap(), base)
        assertTrue(result.conflicts.isEmpty())
        assertTrue(result.payload.isEmpty())
    }

    @Test fun logoutAgainstConcurrentRefreshRequiresChoice() {
        val base = tokens("mal_access_token" to "a")
        assertEquals(setOf("service:mal"), merge(base, emptyMap(), tokens("mal_access_token" to "a2")).conflicts)
    }

    @Test fun repeatedUploadDoesNotTurnStaleLocalTokenIntoAnEdit() {
        val local = tokens("mal_access_token" to "old")
        val remote = tokens("mal_access_token" to "new")
        val first = merge(local, local, remote)
        val second = VaultMerge.merge(local, remote, VaultMerge.hashes(local), VaultMerge.hashes(VaultMerge.groups(first.payload)))
        assertEquals(remote, VaultMerge.groups(second.payload))
        assertTrue(second.conflicts.isEmpty())
    }

    @Test fun ambiguousUploadRetryWithIdenticalTokensIsIdempotent() {
        val base = tokens("mal_access_token" to "old")
        val latest = tokens("mal_access_token" to "new")
        assertTrue(merge(base, latest, latest).conflicts.isEmpty())
    }

    @Test fun oldPayloadWithoutSectionHasSameFingerprint() {
        val old = tokens("simkl_access_token" to "token")
        val explicit = VaultMerge.groups(Json.parseToJsonElement("""[{"section":"tokens","k":"simkl_access_token","t":"s","v":"token"}]""").jsonArray)
        assertEquals(VaultMerge.hashes(old), VaultMerge.hashes(explicit))
    }

    @Test fun settingsDoNotConflictWithAnUnrelatedService() {
        val base = tokens("mal_access_token" to "a")
        val settings = VaultMerge.groups(Json.parseToJsonElement("""[{"section":"settings","k":"selected_theme_id","t":"s","v":"mint"}]""").jsonArray)
        val result = merge(base, base + settings, tokens("mal_access_token" to "b"))
        assertTrue(result.conflicts.isEmpty())
        assertEquals(2, VaultMerge.groups(result.payload).size)
    }
    @Test fun staleUsernameEditCannotOverwriteRemoteTokenRotation() {
        val base = tokens("kitsu_access_token" to "old", "kitsu_username" to "name")
        val remote = tokens("kitsu_access_token" to "new", "kitsu_username" to "name")
        val preserved = VaultMerge.remoteBaselineAfterUpload(base, remote, VaultMerge.hashes(base))
        val edited = tokens("kitsu_access_token" to "old", "kitsu_username" to "renamed")
        val result = VaultMerge.merge(edited, remote, VaultMerge.hashes(base), preserved)
        assertEquals(setOf("service:kitsu"), result.conflicts)
    }

    @Test fun addonRecordsAreIndependentMergeGroups() {
        val grouped = addons("cloudstream_plugin:alpha" to "one", "manga_repo:beta" to "two")
        assertEquals(setOf("addons:cloudstream_plugin:alpha", "addons:manga_repo:beta"), grouped.keys)
    }

    @Test fun disabledAddonCategoryKeepsRemoteAndDoesNotCreateAConflict() {
        val local = addons("cloudstream_plugin:local" to "local value")
        val remote = addons("cloudstream_plugin:remote" to "remote value")
        val merged = VaultMerge.merge(
            local, remote, emptyMap(), emptyMap(), activeGroups = setOf("service:mal")
        )
        assertTrue(merged.conflicts.isEmpty())
        assertEquals(remote, VaultMerge.groups(merged.payload))
    }

    @Test fun uploadBaselineOnlyAdvancesActiveCategories() {
        val local = addons("plugin:a" to "new")
        val merged = local
        val disabledGroup = "addons:plugin:b"
        val result = VaultMerge.baselineAfterUpload(
            local = local,
            merged = merged,
            baseLocal = mapOf(disabledGroup to "old-local"),
            baseRemote = mapOf(disabledGroup to "old-remote"),
            activeGroups = setOf("addons:plugin:a")
        )
        assertEquals("old-local", result.local[disabledGroup])
        assertEquals("old-remote", result.remote[disabledGroup])
        assertEquals(VaultMerge.hash(local.getValue("addons:plugin:a")), result.local["addons:plugin:a"])
    }
}
