package com.kitsugi.animelist.data.auth

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.remote.KitsuIdNamespace
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Kitsu kütüphane senkronizasyon yöneticisi.
 */
object KitsuSyncManager {
    private const val TAG = "KitsuSyncManager"
    private const val KITSU_OFFSET = 300_000_000

    data class SyncResult(
        val messages: List<String>,
        val errors: List<String> = emptyList(),
        /**
         * True when the record was skipped because no trustworthy Kitsu media ID could be
         * resolved (not an API failure). Callers should report this as a skip, not an error.
         */
        val unresolvedMedia: Boolean = false,
        /** Which resolver produced the Kitsu media ID (diagnostics for the report). */
        val resolvedVia: String? = null
    )

    fun watchStatusToKitsu(status: WatchStatus?): String = when (status) {
        WatchStatus.Watching, WatchStatus.Repeating -> "current"
        WatchStatus.Completed -> "completed"
        WatchStatus.Paused    -> "on_hold"
        WatchStatus.Dropped   -> "dropped"
        WatchStatus.Planned   -> "planned"
        else                  -> "planned"
    }

    fun kitsuStatusToWatchStatus(status: String?): WatchStatus = when (status?.lowercase()) {
        "current"   -> WatchStatus.Watching
        "completed" -> WatchStatus.Completed
        "on_hold"   -> WatchStatus.Paused
        "dropped"   -> WatchStatus.Dropped
        "planned"   -> WatchStatus.Planned
        else        -> WatchStatus.Planned
    }

    private data class ResolvedKitsuMedia(val id: Int, val via: String)

    /**
     * Kitsu medya ID'sini güvenilirlikten düşüğe doğru sırayla çözer:
     * bilinen ID → yerel MAL eşleme önbelleği → Kitsu `mappings` (MAL, AniList) → ARM → sıkı başlık araması.
     * Hiçbiri sonuç vermezse null döner; bu bir API hatası değil, "eşleştirilemedi" durumudur.
     */
    private suspend fun resolveKitsuMediaId(
        context: Context,
        entry: MediaEntry,
        knownKitsuMediaId: Int?,
        isAnime: Boolean,
        knownMalId: Int? = null
    ): ResolvedKitsuMedia? {
        knownKitsuMediaId?.takeIf { it > 0 }?.let { return ResolvedKitsuMedia(it, context.getString(R.string.kitsu_sync_via_known_id)) }

        // Doğrulanmış MAL kimliği (çapraz kimlik katmanı) önce gelir. Yoksa KAYNAK-DUYARLI okuma
        // gerekir: salt aralık kontrolü Shikimori/Bangumi/Kitsu ham kimliklerini (<100M) de MAL
        // ID sanıyordu; Kitsu `mappings` tablosu o zaman yanlış yapımın Kitsu kimliğini
        // döndürüp kullanıcının yanlış anime'sine durum/puan yazıyordu.
        val realMalId = CrossProviderIds.realMalIdOf(knownMalId) ?: com.kitsugi.animelist.model.MediaIdentity.realMalId(entry)
        realMalId?.let { ExternalAuthManager.getKitsuMediaIdForMal(context, it, isAnime) }
            ?.takeIf { it > 0 }
            ?.let { return ResolvedKitsuMedia(it, context.getString(R.string.kitsu_sync_via_mal_cache)) }

        KitsuIdNamespace.rawIdFromStable(entry.malId)?.let {
            return ResolvedKitsuMedia(it, context.getString(R.string.kitsu_sync_via_kitsu_entry))
        }

        val rawAniListId = if (entry.source == "anilist" && entry.malId != null && entry.malId >= 100_000_000 && entry.malId < KITSU_OFFSET) {
            entry.malId - 100_000_000
        } else null
        val typeSegment = if (isAnime) "anime" else "manga"

        // Resmi Kitsu mappings tablosu: MAL ID (önce), sonra AniList ID
        if (realMalId != null) {
            val mapped = runSyncCatching {
                KitsuApiClient.lookupKitsuIdByExternalMapping("myanimelist/$typeSegment", realMalId)
            }.getOrNull()
            if (mapped != null && mapped > 0) {
                ExternalAuthManager.saveKitsuMediaIdForMal(context, realMalId, isAnime, mapped)
                return ResolvedKitsuMedia(mapped, context.getString(R.string.kitsu_sync_via_mal_mapping))
            }
        }
        if (rawAniListId != null) {
            val mapped = runSyncCatching {
                KitsuApiClient.lookupKitsuIdByExternalMapping("anilist/$typeSegment", rawAniListId)
            }.getOrNull()
            if (mapped != null && mapped > 0) {
                return ResolvedKitsuMedia(mapped, context.getString(R.string.kitsu_sync_via_anilist_mapping))
            }
        }

        // ARM (yalnızca anime için veri içerir). GÜVENLİK: TMDB kimliği verilmez — film/dizi kimlik uzayı
        // çakışması ve sezon gruplaması yüzünden yanlış yapımın Kitsu kimliği dönüyordu; yalnızca birebir
        // kimlikler (gerçek MAL / AniList) kullanılır.
        if (isAnime && (realMalId != null || rawAniListId != null)) {
            val armKitsu = runSyncCatching {
                com.kitsugi.animelist.data.remote.KitsugiIdResolver.resolveIds(
                    malId = realMalId,
                    aniListId = rawAniListId,
                    tmdbId = null,
                    mediaType = entry.type
                ).kitsuId
            }.getOrNull()
            if (armKitsu != null && armKitsu > 0) {
                if (realMalId != null) ExternalAuthManager.saveKitsuMediaIdForMal(context, realMalId, isAnime, armKitsu)
                return ResolvedKitsuMedia(armKitsu, context.getString(R.string.kitsu_sync_via_arm))
            }
        }

        // Son çare: sıkı başlık araması (belirsiz eşleşmelerde null döner)
        val searchTitle = entry.titleEnglish?.takeIf { it.isNotBlank() } ?: entry.title
        var byTitle = KitsuApiClient.lookupKitsuId(searchTitle, isAnime = isAnime, expectedYear = entry.year)
        if (byTitle == null && !entry.titleEnglish.isNullOrBlank() && entry.title.isNotBlank() && entry.title != searchTitle) {
            byTitle = KitsuApiClient.lookupKitsuId(entry.title, isAnime = isAnime, expectedYear = entry.year)
        }
        if (byTitle != null && byTitle > 0) {
            if (realMalId != null) ExternalAuthManager.saveKitsuMediaIdForMal(context, realMalId, isAnime, byTitle)
            return ResolvedKitsuMedia(byTitle, context.getString(R.string.kitsu_sync_via_title_search))
        }
        return null
    }

    private fun isKitsuUnauthorized(text: String?): Boolean {
        val msg = text?.lowercase() ?: return false
        return msg.contains("http 401") ||
            msg.contains("unauthorized") ||
            msg.contains("invalid token") ||
            msg.contains("access token is invalid") ||
            msg.contains("access token expired")
    }

    /**
     * Kitsu işlemini geçerli (gerekirse yenilenmiş) token ile çalıştırır; sunucu HTTP 401
     * döndürürse zorla yeni bir `access_token` alıp işlemi **bir kez** yeniden dener (`E85`).
     */
    private suspend fun executeWithKitsuAuthRetry(
        context: Context,
        block: suspend (activeToken: String) -> SyncResult
    ): SyncResult {
        val initialToken = ExternalAuthManager.getOrRefreshKitsuToken(context)
        if (initialToken.isNullOrBlank()) {
            return SyncResult(
                messages = emptyList(),
                errors = listOf("Kitsu: HTTP 401 — Kitsu oturumu sona erdi, Ayarlar > Kitsu bölümünden tekrar giriş yapın")
            )
        }
        suspend fun runOnce(activeToken: String): Result<SyncResult> = runSyncCatching {
            block(activeToken)
        }
        val firstAttempt = runOnce(initialToken)
        val firstUnauthorized = firstAttempt.fold(
            onSuccess = { res -> res.errors.any { isKitsuUnauthorized(it) } },
            onFailure = { err -> isKitsuUnauthorized(err.message) }
        )
        if (!firstUnauthorized) {
            return firstAttempt.getOrThrow()
        }
        val refreshed = ExternalAuthManager.forceRefreshKitsuToken(context)
        if (refreshed.isNullOrBlank()) {
            ExternalAuthManager.markSessionExpired(context, "kitsu")
            return SyncResult(
                messages = emptyList(),
                errors = listOf("Kitsu: HTTP 401 — Kitsu oturumu sona erdi, Ayarlar > Kitsu bölümünden tekrar giriş yapın")
            )
        }
        return runOnce(refreshed).getOrThrow()
    }

    /**
     * Bir MediaEntry'yi kullanıcının Kitsu kütüphanesine senkronize eder.
     */
    suspend fun syncEntryToKitsu(
        context: Context,
        entry: MediaEntry,
        knownKitsuMediaId: Int? = null,
        /** Yerel satırda DEĞİŞEN alanlar (bkz. [SyncField], [ProviderCapabilitiesCatalog]). */
        fields: Set<SyncField> = SyncField.ALL,
        /** Tam yazım mı? Boş puan/not/tarih bu durumda GÖNDERİLMEZ (uzak değer korunur). */
        fullWrite: Boolean = fields == SyncField.ALL,
        knownMalId: Int? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        if (entry.type != MediaType.Anime && entry.type != MediaType.Manga) {
            return@withContext SyncResult(emptyList(), listOf(context.getString(R.string.account_sync_service_anime_manga_only)))
        }

        val userId = ExternalAuthManager.getKitsuUserId(context)
        if (userId.isNullOrBlank()) {
            return@withContext SyncResult(messages = emptyList(), errors = listOf(context.getString(R.string.account_sync_account_not_connected, "Kitsu")))
        }

        // Kitsu sadece Anime ve Manga destekler
        val isAnime = entry.type != MediaType.Manga

        // 1. Kitsu Media Numeric ID'sini bul
        val resolved = resolveKitsuMediaId(context, entry, knownKitsuMediaId, isAnime, knownMalId)
        if (resolved == null) {
            return@withContext SyncResult(
                messages = emptyList(),
                errors = listOf(context.getString(R.string.account_sync_kitsu_match_not_found, entry.title)),
                unresolvedMedia = true
            )
        }
        val kitsuMediaId = resolved.id

        val status = watchStatusToKitsu(entry.status)
        val ratingTwenty = SyncScores.kitsuTwenty(entry.score)
        // Tekrar: Kitsu'da ayrı durum yok → status=current + reconsuming/reconsumeCount.
        val reconsuming = entry.status == WatchStatus.Repeating || entry.isRepeating
        // Tarih / not / gizlilik: yalnızca dolu değerler gönderilir (boş alan uzaktaki değeri silmez).
        val kitsuExtras = KitsuApiClient.KitsuEntryExtras(
            startedAt = KitsuApiClient.toKitsuDateTime(entry.startDate),
            finishedAt = KitsuApiClient.toKitsuDateTime(entry.endDate),
            notes = entry.notes?.takeIf { it.isNotBlank() },
            isPrivate = entry.isPrivate,
            reconsuming = reconsuming,
            reconsumeCount = entry.repeatCount
        )

        /**
         * Güncelleme yolunda YALNIZCA değişen attribute'lar gönderilir. Gönderilmeyen alan
         * Kitsu'da korunur; bu, "yerel puan boş → Kitsu puanını sil" hatasını kapatır.
         * (MAL-Sync `SingleAbstract.getStateDiff` ile aynı yaklaşım.)
         */
        fun changedAttributes(): org.json.JSONObject = org.json.JSONObject().apply {
            if (SyncField.STATUS in fields) {
                put("status", status)
                put("reconsuming", reconsuming && status == "current")
            }
            if (SyncField.PROGRESS in fields) put("progress", entry.progress)
            if (SyncField.SCORE in fields) {
                when {
                    ratingTwenty != null -> put("ratingTwenty", ratingTwenty)
                    !fullWrite -> put("ratingTwenty", org.json.JSONObject.NULL)
                }
            }
            if (SyncField.REWATCH_COUNT in fields) put("reconsumeCount", entry.repeatCount)
            if (SyncField.START_DATE in fields) {
                val startedAt = KitsuApiClient.toKitsuDateTime(entry.startDate)
                when (SyncDates.action(entry.startDate, startedAt, fullWrite)) {
                    SyncDates.Action.WRITE -> put("startedAt", startedAt)
                    SyncDates.Action.CLEAR -> put("startedAt", org.json.JSONObject.NULL)
                    SyncDates.Action.SKIP -> Unit
                }
            }
            if (SyncField.END_DATE in fields) {
                val finishedAt = KitsuApiClient.toKitsuDateTime(entry.endDate)
                when (SyncDates.action(entry.endDate, finishedAt, fullWrite)) {
                    SyncDates.Action.WRITE -> put("finishedAt", finishedAt)
                    SyncDates.Action.CLEAR -> put("finishedAt", org.json.JSONObject.NULL)
                    SyncDates.Action.SKIP -> Unit
                }
            }
            if (SyncField.NOTES in fields) {
                val notes = entry.notes?.takeIf { it.isNotBlank() }
                when {
                    notes != null -> put("notes", notes)
                    !fullWrite -> put("notes", org.json.JSONObject.NULL)
                }
            }
            if (SyncField.PRIVATE in fields) put("private", entry.isPrivate)
        }

        executeWithKitsuAuthRetry(context) { token ->
            val messages = mutableListOf<String>()
            val errors = mutableListOf<String>()

            var cappedProgressTo: Int? = null
            suspend fun writeWithLengthGuard(
                write: suspend (Int) -> KitsuApiClient.KitsuWriteResult
            ): KitsuApiClient.KitsuWriteResult {
                val outcome = KitsuProgressLengthGuard.write(
                    requestedProgress = entry.progress,
                    write = write,
                    fetchLength = {
                        KitsuApiClient.fetchMediaLength(
                            kitsuMediaId = kitsuMediaId,
                            isAnime = isAnime,
                            accessToken = token
                        )
                    }
                )
                cappedProgressTo = outcome.cappedProgressTo
                return outcome.result
            }

            val existingEntryId = KitsuApiClient.findLibraryEntryId(token, userId, kitsuMediaId, isAnime)
            if (existingEntryId != null) {
                ExternalAuthManager.saveKitsuLibraryEntryId(context, kitsuMediaId, isAnime, existingEntryId)
            }

            val cappedNote = {
                cappedProgressTo?.let { context.getString(R.string.kitsu_sync_progress_capped, entry.progress, it) }.orEmpty()
            }

            if (existingEntryId != null) {
                val attributes = changedAttributes()
                if (SyncField.PROGRESS in fields) attributes.put("progress", entry.progress)
                if (attributes.length() == 0) {
                    return@executeWithKitsuAuthRetry SyncResult(messages = emptyList(), errors = emptyList())
                }
                val result = writeWithLengthGuard { p ->
                    if (SyncField.PROGRESS in fields) attributes.put("progress", p)
                    KitsuApiClient.patchLibraryEntry(
                        token = token,
                        entryId = existingEntryId,
                        attributes = attributes
                    )
                }
                if (result.success) messages.add(context.getString(R.string.account_sync_entry_updated, "Kitsu", entry.title) + cappedNote())
                else errors.add(context.getString(R.string.account_sync_entry_update_failed, "Kitsu", entry.title, result.errorMessage ?: context.getString(R.string.account_sync_unknown_error)))
            } else {
                val created = writeWithLengthGuard { p ->
                    KitsuApiClient.createLibraryEntryDetailed(
                        token = token,
                        userId = userId,
                        kitsuMediaId = kitsuMediaId,
                        isAnime = isAnime,
                        status = status,
                        progress = p,
                        ratingTwenty = ratingTwenty,
                        extras = kitsuExtras
                    )
                }
                if (created.success && created.entryId != null) {
                    ExternalAuthManager.saveKitsuLibraryEntryId(context, kitsuMediaId, isAnime, created.entryId)
                    messages.add(context.getString(R.string.account_sync_entry_added, "Kitsu", entry.title) + cappedNote())
                } else {
                    val fallbackId = runSyncCatching {
                        KitsuApiClient.findLibraryEntryId(token, userId, kitsuMediaId, isAnime)
                    }.getOrNull()
                    if (fallbackId != null) {
                        ExternalAuthManager.saveKitsuLibraryEntryId(context, kitsuMediaId, isAnime, fallbackId)
                        val fallbackAttributes = changedAttributes()
                        val updated = writeWithLengthGuard { p ->
                            if (SyncField.PROGRESS in fields) fallbackAttributes.put("progress", p)
                            KitsuApiClient.patchLibraryEntry(
                                token = token,
                                entryId = fallbackId,
                                attributes = fallbackAttributes
                            )
                        }
                        if (updated.success) messages.add(context.getString(R.string.account_sync_entry_updated, "Kitsu", entry.title) + cappedNote())
                        else errors.add(context.getString(R.string.account_sync_entry_update_failed, "Kitsu", entry.title, updated.errorMessage ?: context.getString(R.string.account_sync_unknown_error)))
                    } else {
                        errors.add(context.getString(R.string.account_sync_entry_add_failed, "Kitsu", entry.title, created.errorMessage ?: context.getString(R.string.account_sync_unknown_error), kitsuMediaId, resolved.via))
                    }
                }
            }

            SyncResult(messages = messages, errors = errors, resolvedVia = resolved.via)
        }
    }

    /**
     * Kitsu kütüphanesinden kaydı siler.
     */
    suspend fun deleteEntryFromKitsu(
        context: Context,
        entry: MediaEntry,
        knownKitsuMediaId: Int? = null,
        knownMalId: Int? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        if (entry.type != MediaType.Anime && entry.type != MediaType.Manga) {
            return@withContext SyncResult(emptyList(), listOf(context.getString(R.string.account_sync_service_anime_manga_only)))
        }

        val isAnime = entry.type != MediaType.Manga
        val userId = ExternalAuthManager.getKitsuUserId(context)
        if (userId.isNullOrBlank()) {
            return@withContext SyncResult(messages = emptyList(), errors = listOf(context.getString(R.string.account_sync_account_not_connected, "Kitsu")))
        }

        val resolved = resolveKitsuMediaId(context, entry, knownKitsuMediaId, isAnime, knownMalId)
        if (resolved == null) {
            return@withContext SyncResult(
                messages = emptyList(),
                errors = listOf(context.getString(R.string.account_sync_media_id_not_found, "Kitsu")),
                unresolvedMedia = true
            )
        }
        val kitsuMediaId = resolved.id

        executeWithKitsuAuthRetry(context) { token ->
            val existingEntryId = KitsuApiClient.findLibraryEntryId(token, userId, kitsuMediaId, isAnime)
            if (existingEntryId != null) {
                val deleted = KitsuApiClient.deleteLibraryEntryDetailed(token, existingEntryId)
                if (deleted.success) {
                    ExternalAuthManager.removeKitsuLibraryEntryId(context, kitsuMediaId, isAnime)
                    SyncResult(messages = listOf(context.getString(R.string.account_sync_entry_deleted, "Kitsu", entry.title)))
                } else {
                    val detail = deleted.errorMessage ?: context.getString(R.string.account_sync_entry_delete_failed, "Kitsu")
                    SyncResult(messages = emptyList(), errors = listOf("Kitsu: $detail"))
                }
            } else {
                SyncResult(messages = listOf(context.getString(R.string.account_sync_entry_already_missing, "Kitsu", entry.title)))
            }
        }
    }
}
