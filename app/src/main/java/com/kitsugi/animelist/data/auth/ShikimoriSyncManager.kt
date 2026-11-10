package com.kitsugi.animelist.data.auth

import android.content.Context
import com.kitsugi.animelist.R
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shikimori.one kütüphane senkronizasyon yöneticisi.
 */
object ShikimoriSyncManager {
    private const val TAG = "ShikimoriSyncManager"

    data class SyncResult(
        val messages: List<String>,
        val errors: List<String> = emptyList(),
        /**
         * Shikimori `target_id` olacak doğrulanmış MAL kimliği çözülemedi.
         * Bu bir API hatası değil, güvenli atlamadır: çağıran taraf kaydı yeniden
         * denenecek hata kuyruğuna yazmamalıdır.
         */
        val unresolvedMedia: Boolean = false
    )

    fun watchStatusToShikimori(status: WatchStatus?): String = when (status) {
        WatchStatus.Watching  -> "watching"
        WatchStatus.Repeating -> "rewatching"
        WatchStatus.Completed -> "completed"
        WatchStatus.Paused    -> "on_hold"
        WatchStatus.Dropped   -> "dropped"
        WatchStatus.Planned   -> "planned"
        else                  -> "planned"
    }

    fun shikimoriStatusToWatchStatus(status: String?): WatchStatus = when (status?.lowercase()) {
        "watching"   -> WatchStatus.Watching
        "rewatching" -> WatchStatus.Repeating
        "completed"  -> WatchStatus.Completed
        "on_hold"    -> WatchStatus.Paused
        "dropped"    -> WatchStatus.Dropped
        "planned"    -> WatchStatus.Planned
        else         -> WatchStatus.Planned
    }

    /**
     * Yazma/silme hedefi (`target_id`). Satırın `malId` alanı kaynak uzayına göre yorumlanır:
     * gerçek MAL kimliği ya da Shikimori kaynaklı satırın kendi ham kimliğidir. Eski kural
     * `0 < id < 100M` idi; eski Kitsu/Bangumi ham kimlikleri de aynı aralığa düşüp Shikimori'de
     * YANLIŞ kaydın silinmesine/yazılmasına yol açabiliyordu. bkz. [CrossProviderIds.decode].
     */
    private fun shikimoriTargetIdOf(entry: MediaEntry, knownMalId: Int? = null): Int? {
        // Çapraz kimlik katmanının doğruladığı MAL kimliği her zaman önce gelir: çözülmüş
        // değer `source = "shikimori"` vb. satırların geçici kopyasına yazılmış olabilir ve
        // saf decode onu kaynağın kendi ham kimliği sanıp yanlış yorumlayabilir.
        CrossProviderIds.realMalIdOf(knownMalId)?.let { return it }
        val decoded = CrossProviderIds.decode(
            storedId = entry.malId,
            source = entry.source,
            type = entry.type,
            entrySimklId = entry.simklId,
            entryTmdbId = entry.tmdbId
        )
        return decoded.malId ?: decoded.shikimoriId
    }

    /**
     * Bir MediaEntry'yi kullanıcının Shikimori kütüphanesine senkronize eder.
     */
    suspend fun syncEntryToShikimori(
        context: Context,
        entry: MediaEntry,
        /**
         * Çapraz kimlik katmanının önceden çözdüğü doğrulanmış MAL kimliği (`target_id` ile
         * aynı uzay). Verildiğinde satır kaynak uzayına göre yeniden yorumlanmaz.
         */
        knownMalId: Int? = null,
        /** Yerel satırda DEĞİŞEN alanlar (bkz. [SyncField]). */
        fields: Set<SyncField> = SyncField.ALL,
        /** Tam yazım mı? Süzme sonrası kümeden anlaşılamaz. */
        fullWrite: Boolean = fields == SyncField.ALL
    ): SyncResult = withContext(Dispatchers.IO) {
        if (entry.type != MediaType.Anime && entry.type != MediaType.Manga) {
            return@withContext SyncResult(emptyList(), listOf(context.getString(R.string.account_sync_service_anime_manga_only)))
        }

        // `E92`: sunucu yerel `expiresAt` geçerli görünürken token'ı iptal etmişse
        // (HTTP 401) refresh token ile zorla yenile ve işlemi BİR KEZ daha dene.
        val initialToken = ExternalAuthManager.getOrRefreshShikimoriToken(context)
        val firstAttempt = runSyncCatching {
            doSyncEntryToShikimori(context, entry, knownMalId, fields, fullWrite, initialToken)
        }
        val firstAuthFailure = firstAttempt.fold(
            onSuccess = { res -> res.errors.any { it.contains("401") || it.contains("Unauthorized", ignoreCase = true) } },
            onFailure = { err -> err is ShikimoriApiClient.ShikimoriTokenException && err.status == 401 }
        )
        if (!firstAuthFailure) {
            return@withContext firstAttempt.getOrElse { err ->
                SyncResult(
                    messages = emptyList(),
                    errors = listOf("Shikimori: ${err.message ?: context.getString(R.string.account_sync_unknown_error)}")
                )
            }
        }
        val refreshed = ExternalAuthManager.forceRefreshShikimoriToken(context)
        if (refreshed.isNullOrBlank()) {
            ExternalAuthManager.markSessionExpired(context, "shikimori")
            return@withContext SyncResult(
                messages = emptyList(),
                errors = listOf("Shikimori oturumu sona erdi (HTTP 401) — Ayarlar > Shikimori bölümünden tekrar giriş yapın")
            )
        }
        runSyncCatching {
            doSyncEntryToShikimori(context, entry, knownMalId, fields, fullWrite, refreshed)
        }.getOrElse { err ->
            SyncResult(
                messages = emptyList(),
                errors = listOf("Shikimori: ${err.message ?: context.getString(R.string.account_sync_unknown_error)}")
            )
        }
    }

    /**
     * Gerçek Shikimori yazım gövdesi; [token] null/boşsa "bağlı değil" sonucu döner.
     * 401 ayrımı [syncEntryToShikimori]'deki yenileme sarmalayıcısı tarafından yapılır (`E92`).
     */
    private suspend fun doSyncEntryToShikimori(
        context: Context,
        entry: MediaEntry,
        knownMalId: Int?,
        fields: Set<SyncField>,
        fullWrite: Boolean,
        token: String?
    ): SyncResult = withContext(Dispatchers.IO) {
        var userId = ExternalAuthManager.getShikimoriUserId(context)
        if (!token.isNullOrBlank() && userId == null) {
            // Kimlik eksikse (girişte whoami geçici yanıt vermediyse) bir kez daha dene.
            ExternalAuthManager.ensureShikimoriUserResolved(context)
            userId = ExternalAuthManager.getShikimoriUserId(context)
        }

        if (token.isNullOrBlank() || userId == null || userId <= 0) {
            return@withContext SyncResult(messages = emptyList(), errors = listOf(context.getString(R.string.account_sync_account_not_connected, "Shikimori")))
        }

        // Shikimori MAL ID'lerini doğrudan hedef ID (target_id) olarak kullanır.
        // Değer satır kaynağına göre yorumlanır: eski Kitsu/Bangumi ham kimlikleri de
        // <100M aralığına düşer ve eski kural onları Shikimori'nin hedefi sanıyordu.
        val targetId = shikimoriTargetIdOf(entry, knownMalId)

        if (targetId == null) {
            return@withContext SyncResult(
                messages = emptyList(),
                errors = listOf(context.getString(R.string.account_sync_shikimori_id_not_found, entry.title)),
                unresolvedMedia = true
            )
        }

        val targetType = if (entry.type == MediaType.Manga) "Manga" else "Anime"
        val status = watchStatusToShikimori(entry.status)
        val score = entry.score?.let { s ->
            if (s > 10) kotlin.math.round(s / 10.0).toInt().coerceIn(0, 10)
            else s.coerceIn(0, 10)
        } ?: 0
        // ani-gui #264 (A16): bilinen toplam varsa ilerleme onu aşıyorsa sıkıştır;
        // toplam bilinmiyorsa (null) olduğu gibi gönderilir — veri kaybı yok.
        val progress = kotlin.math.min(entry.progress, entry.total?.takeIf { it > 0 } ?: Int.MAX_VALUE)

        val messages = mutableListOf<String>()
        val errors = mutableListOf<String>()

        // ── Alan bazlı yazım ────────────────────────────────────────────────────────
        // Kayıt kimliği biliniyorsa PATCH: yalnız değişen alanlar gönderilir, diğerleri korunur.
        // [SyncField.SCORE] yoksa `score` HİÇ gönderilmez (0 göndermek puanı siler).
        // Önce yerel önbellek; yoksa Shikimori'ye "bu kayıt var mı?" diye sorulur.
        // Bu adım olmadan, kayıt Shikimori web'inde oluşturulmuşsa `POST /user_rates`
        // "zaten var" hatası veriyor ve satır Shikimori'ye HİÇ eşitlenmiyordu (sessiz kayıp).
        val knownRateId: Int? = runSyncCatching {
            ExternalAuthManager.getShikimoriRateId(context, targetId, targetType)
        }.getOrNull() ?: runSyncCatching {
            ShikimoriApiClient.fetchUserRateId(
                token = token, userId = userId, targetId = targetId, targetType = targetType
            )
        }.getOrNull()?.also { found ->
            runSyncCatching {
                ExternalAuthManager.saveShikimoriRateId(context, targetId, targetType, found)
            }
        }
        // Kayıt KESİN varsa her zaman PATCH: `POST` "zaten var" davranışına bağımlı kalmayız.
        // Tam yazımda (yeni yerel satır) tüm alanlar gönderilir; kısmi yazımda yalnız diff.
        val patched = if (knownRateId != null) {
            val attributes = org.json.JSONObject()
            if (fullWrite || SyncField.STATUS in fields) attributes.put("status", status)
            if (fullWrite || SyncField.SCORE in fields) attributes.put("score", score.coerceIn(0, 10))
            if (fullWrite || SyncField.PROGRESS in fields) {
                if (targetType == "Anime") attributes.put("episodes", progress)
                else attributes.put("chapters", progress)
            }
            if (targetType == "Manga" && (fullWrite || SyncField.VOLUME_PROGRESS in fields)) {
                attributes.put("volumes", entry.volumeProgress.coerceAtLeast(0))
            }
            // Tam yazımda uzaktaki metni/tekrar sayısını KORU (boş/0 göndermek siler).
            if (SyncField.NOTES in fields) attributes.put("text", entry.notes.orEmpty())
            else if (fullWrite && !entry.notes.isNullOrBlank()) attributes.put("text", entry.notes!!)
            if (SyncField.REWATCH_COUNT in fields) {
                attributes.put("rewatches", entry.repeatCount.coerceAtLeast(0))
            } else if (fullWrite && entry.repeatCount > 0) {
                attributes.put("rewatches", entry.repeatCount)
            }
            ShikimoriApiClient.patchUserRate(token, knownRateId, attributes)
        } else false

        val rateId = if (patched) {
            knownRateId
        } else {
            // Kayıt YOK: `POST /api/v2/user_rates` **create-only** (resmî v2 dokümanı ve
            // bağımsız istemciler bunu doğruluyor; upsert DEĞİL). Bu yüzden yalnız
            // "kayıt bulunamadı" sonucundan sonra çağrılır. Puan yalnız diff'te varsa gider.
            val created = ShikimoriApiClient.createUserRate(
                token = token, userId = userId, targetId = targetId, targetType = targetType,
                status = status,
                score = if (SyncField.SCORE in fields || fullWrite) score else null,
                progress = progress,
                text = entry.notes?.takeIf { it.isNotBlank() },
                rewatches = entry.repeatCount.takeIf { it > 0 },
                volumes = if (targetType == "Manga") entry.volumeProgress.takeIf { it > 0 } else null
            )
            if (created != null) {
                created
            } else {
                // Yarış durumu: kayıt aramadan sonra (başka cihaz/sekme) oluşmuş olabilir.
                // ID'yi bir kez daha sorup PATCH'e düşeriz — POST'u tekrarlamayız.
                val lateId = runSyncCatching {
                    ShikimoriApiClient.fetchUserRateId(
                        token = token, userId = userId, targetId = targetId, targetType = targetType
                    )
                }.getOrNull()
                lateId?.let { id ->
                    val attributes = org.json.JSONObject().apply {
                        put("status", status)
                        if (targetType == "Anime") put("episodes", progress) else put("chapters", progress)
                        if (SyncField.SCORE in fields || fullWrite) put("score", score.coerceIn(0, 10))
                    }
                    if (ShikimoriApiClient.patchUserRate(token, id, attributes)) id else null
                }
            }
        }
        if (rateId != null) {
            ExternalAuthManager.saveShikimoriRateId(context, targetId, targetType, rateId)
            messages.add(context.getString(R.string.account_sync_shikimori_entry_confirmed, entry.title))
        } else {
            errors.add(context.getString(R.string.account_sync_shikimori_entry_unconfirmed, entry.title))
        }

        SyncResult(messages = messages, errors = errors)
    }

    /**
     * Shikimori kütüphanesinden kaydı siler.
     *
     * rate kimliği yerel önbellekte yoksa kullanıcı `user_rates` listesinden hedefe göre
     * aranır (içe aktarılmış ya da başka cihazdan eklenmiş kayıtlar da böyle silinir).
     * Hiçbir şekilde hedef çözülemezse bu bir **atlamadır**, hata değildir.
     */
    suspend fun deleteEntryFromShikimori(
        context: Context,
        entry: MediaEntry,
        /** [syncEntryToShikimori] ile aynı: doğrulanmış çapraz MAL kimliği silmeye de taşınır. */
        knownMalId: Int? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        if (entry.type != MediaType.Anime && entry.type != MediaType.Manga) {
            return@withContext SyncResult(emptyList(), listOf(context.getString(R.string.account_sync_service_anime_manga_only)))
        }

        val initialToken = ExternalAuthManager.getOrRefreshShikimoriToken(context)
        if (initialToken.isNullOrBlank()) {
            return@withContext SyncResult(messages = emptyList(), errors = listOf(context.getString(R.string.account_sync_account_not_connected, "Shikimori")))
        }
        // getShikimoriRateId, kullanıcı kimliği kayıtlı değilse istisna fırlatır; önce çözmeye çalış.
        if (ExternalAuthManager.getShikimoriUserId(context) == null) {
            ExternalAuthManager.ensureShikimoriUserResolved(context)
        }

        val targetId = shikimoriTargetIdOf(entry, knownMalId)

        if (targetId == null) {
            // Doğrulanmış hedef kimlik yok: kaydın Shikimori'de hangi kayda karşılık geldiği
            // bilinmiyor — güvenli atlama (hata değil; kuyrukta yeniden denemesi anlamsız).
            return@withContext SyncResult(
                messages = emptyList(),
                errors = listOf(context.getString(R.string.account_sync_media_id_not_found, "Shikimori")),
                unresolvedMedia = true
            )
        }

        val targetType = if (entry.type == MediaType.Manga) "Manga" else "Anime"
        val cachedRateId = ExternalAuthManager.getShikimoriRateId(context, targetId, targetType)

        // Önbellekte yoksa uzak listeden hedefe göre bul: rateId yalnızca bu uygulama
        // üzerinden oluşturulmuş kayıtlarda saklanıyordu; içe aktarılmış/uzak kayıtlar
        // silinemiyormuş gibi görünüyordu. Uzak liste OKUNAMAZSA bu "yok" demek değildir —
        // gerçek hata olarak raporlanır ki kuyrukta yeniden denensin.
        val token = initialToken
        val userIdForLookup = ExternalAuthManager.getShikimoriUserId(context)
        if (userIdForLookup == null) {
            return@withContext SyncResult(
                messages = emptyList(),
                errors = listOf(context.getString(R.string.account_sync_account_not_connected, "Shikimori"))
            )
        }
        // `E93`: önbellekteki rate kimliği bayat olabilir (kayıt web'de silinip yeniden
        // oluşturulmuş). DELETE 404 dönüyorsa kimliği uzak listeden TAZELEYİP bir kez daha dene;
        // aksi hâlde silme "başarılı" sanılıp gerçek kayıt Shikimori'de kalıyordu.
        suspend fun resolveFreshRateId(): Int? = runSyncCatching {
            ShikimoriApiClient.fetchAllUserRates(token, userIdForLookup)
                .firstOrNull { rate -> rate.targetId == targetId && rate.targetType == targetType }
                ?.id
        }.getOrNull()

        val rateLookup: Result<Int?> = Result.success(cachedRateId ?: resolveFreshRateId())

        val rateId = rateLookup.getOrElse {
            return@withContext SyncResult(
                messages = emptyList(),
                errors = listOf(context.getString(R.string.account_sync_entry_delete_failed, "Shikimori"))
            )
        }

        if (rateId == null) {
            // Kaynak listeye hiç eklenmemiş: silme zaten amaca ulaştı (idempotent başarı).
            return@withContext SyncResult(
                messages = listOf(context.getString(R.string.account_sync_entry_already_missing, "Shikimori", entry.title))
            )
        }

        var deleted = runSyncCatching { ShikimoriApiClient.deleteUserRate(token, rateId) }
            .getOrElse { false }
        if (deleted && cachedRateId != null && cachedRateId != rateId) {
            // Bayat önbellek zaten silinmiş olabilir; gerçek kayıt farklı kimlikte. Yeniden dene.
            val freshId = resolveFreshRateId()
            if (freshId != null && freshId != rateId) {
                deleted = runSyncCatching { ShikimoriApiClient.deleteUserRate(token, freshId) }.getOrElse { false }
            }
        } else if (!deleted && cachedRateId != null) {
            // 401 olmayan bir başarısızlık değilse bile taze kimlikle bir kez daha dene (E93).
            val freshId = resolveFreshRateId()
            if (freshId != null && freshId != rateId) {
                deleted = runSyncCatching { ShikimoriApiClient.deleteUserRate(token, freshId) }.getOrElse { false }
            }
        }
        if (deleted) {
            ExternalAuthManager.removeShikimoriRateId(context, targetId, targetType)
            SyncResult(messages = listOf(context.getString(R.string.account_sync_entry_deleted, "Shikimori", entry.title)))
        } else {
            SyncResult(messages = emptyList(), errors = listOf(context.getString(R.string.account_sync_entry_delete_failed, "Shikimori")))
        }
    }
}
