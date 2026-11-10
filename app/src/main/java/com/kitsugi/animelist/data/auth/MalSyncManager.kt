package com.kitsugi.animelist.data.auth

import android.content.Context
import com.kitsugi.animelist.R
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject

object MalSyncManager {
    /**
     * MAL `start_date`/`finish_date` yalnızca TAM tarih kabul eder (`yyyy-MM-dd`).
     * Eksik/parsiyel tarih (ör. "2016" ya da "2016-05", bazı içe aktarımlardan
     * sızmış olabilir) tüm PATCH'i 400 ile düşürür → eşitleme "hata" sayılıp
     * kuyruğa giriyordu. Biçimi uymayan tarih GÖNDERİLMEZ (uzak değer korunur),
     * uyuşmayan alan için yazma yapılmaz.
     */
    private val fullIsoDate = Regex("""\d{4}-\d{2}-\d{2}""")

    /**
     * MAL'da "tarihi sil" karşılığı olan sentinel. Boş string 400 döndürür; MAL-Sync de
     * aynı sabiti kullanır. Yine de sunucu tarafında davranış değişebileceği için
     * [patchWithDateClearFallback] bu alanı taşıyan PATCH 400 ile düşerse isteği
     * sentinelsiz bir kez tekrarlar: tarihi temizleyememek, aynı istekte taşınan
     * durum/ilerleme/puanın da kaybolmasından iyidir (E62).
     */
    private const val DATE_CLEAR = "0000-00-00"

    private fun String?.sendableDate(): String? = this?.takeIf { fullIsoDate.matches(it) }

    /**
     * @param fields yerel satırda DEĞİŞEN alanlar. Yalnızca bu alanlar gönderilir; böylece
     * hedefteki (MAL web'inde girilmiş) puan/not/tarih EZİLMEZ.
     *
     * Kritik: eskiden `score` her zaman gönderiliyordu ve yerel puan boşken `0` gidiyordu.
     * MAL API sözleşmesi "Set this value to 0 to remove the user's rating" der — yani
     * uygulama, MAL'da verilmiş puanı **siliyordu**. Aynı şekilde `priority=0` de yazılıyordu.
     */
    fun updateMalEntry(
        token: String,
        entry: MediaEntry,
        context: Context,
        fields: Set<SyncField> = SyncField.ALL,
        /** Tam yazım mı? Süzme sonrası kümeden anlaşılamaz; boş puan/öncelik korunur. */
        fullWrite: Boolean = fields == SyncField.ALL
    ): Boolean {
        require(entry.type == MediaType.Anime || entry.type == MediaType.Manga) {
            context.getString(R.string.account_sync_mal_type_unsupported)
        }
        val malId = entry.malId?.takeIf { it.isRealMalId() }
            ?: error(context.getString(R.string.account_sync_mal_id_invalid))

        val endpointType = when (entry.type) {
            MediaType.Anime, MediaType.Movie, MediaType.TvShow -> "anime"
            MediaType.Manga -> "manga"
        }

        val progressKey = when (entry.type) {
            MediaType.Anime, MediaType.Movie, MediaType.TvShow -> "num_watched_episodes"
            MediaType.Manga -> "num_chapters_read"
        }

        val url = URL("https://api.myanimelist.net/v2/$endpointType/$malId/my_list_status")

        // ── KISMİ YAZIM KAYIT OLUŞTURUYORSA TAM YAZIMA GEÇ (E69) ────────────────────
        // `my_list_status` PATCH'i **upsert**'tür. Kayıt yokken yalnız bir alan göndermek
        // (ör. yerel satırda yalnız puan değişti) MAL'de `plan_to_watch` + 0 ilerleme ile
        // KISMİ bir kayıt oluşturur; kullanıcı MAL'de "planlandı 0/12" görür, üstelik sonraki
        // turlarda durum/ilerleme "değişmediği" için bu yarım kayıt asla düzeltilmez.
        // Bu yüzden durum alanı gönderilmeyen (dolayısıyla kaydı oluşturabilecek) yazımlardan
        // önce kaydın VAR olduğu doğrulanır; yoksa bir kez tam yazım yapılır.
        // Sorgu yalnız bu nadir yolda (puan/not/tarih düzenlemesi) çalışır; durum içeren
        // yazımlarda ek istek yoktur.
        if (SyncField.STATUS !in fields && !fullWrite) {
            when (malListEntryExists(token, malId, endpointType, context)) {
                false -> return updateMalEntry(
                    token = token,
                    entry = entry,
                    context = context,
                    fields = SyncField.ALL,
                    fullWrite = true
                )
                // null = doğrulanamadı (ağ/HTTP hatası): davranış değişmez, kısmi yazım denenir.
                true, null -> Unit
            }
        }

        // ── Alan bazlı gövde (bkz. ProviderCapabilitiesCatalog.mal) ──────────────────
        // Gönderilmeyen alan MAL'da DEĞİŞMEZ. Boş değer yalnızca kullanıcı o alanı bilerek
        // boşalttığında (diff'te göründüğünde) gönderilir.
        val postData = buildString {
            if (SyncField.STATUS in fields) {
                // (E77) `Repeating` yazımında MAL'daki MEVCUT durum korunur (E72'nin yazıcı
                // tarafı): mevcut durum yalnız bu nadir yolda bir GET ile okunur.
                val existingMalStatus = if (entry.status == WatchStatus.Repeating) {
                    malCurrentListStatus(token, malId, endpointType)
                } else {
                    null
                }
                append(
                    "status=${
                        URLEncoder.encode(
                            malStatusForWrite(entry.status, entry.type, existingMalStatus),
                            "UTF-8"
                        )
                    }"
                )
                // MAL'de "tekrar izleniyor" ayrı bir durum DEĞİLDİR: durum `completed` kalır ve
                // `is_rewatching` bayrağı set edilir (MAL-Sync MyAnimeList_api/single.ts ile aynı).
                if (entry.type == MediaType.Manga) {
                    append("&is_rereading=${entry.status == WatchStatus.Repeating || entry.isRepeating}")
                } else {
                    append("&is_rewatching=${entry.status == WatchStatus.Repeating || entry.isRepeating}")
                }
            }
            if (SyncField.SCORE in fields) {
                // 0 = "puanı kaldır" (MAL sözleşmesi). Normalde 1..10; eski 100'lük değerler
                // onluk ölçeğe indirgenir. TAM yazımda (ALL) boş puan gönderilmez: yeni kayıtta
                // zaten puan yok, mevcut kayıtta ise uzaktaki puan gereksiz yere silinmemeli.
                val tenPoint = SyncScores.tenPoint(entry.score)
                when {
                    tenPoint != null -> append("&score=$tenPoint")
                    !fullWrite -> append("&score=0")
                }
            }
            if (SyncField.PROGRESS in fields) {
                append("&$progressKey=${entry.progress}")
                // KRİTİK: MAL, ilerleme artırıldığında `is_rewatching` bayrağını KENDİSİ
                // false'a çeker. Sadece ilerleme gönderirsek kullanıcının "şu an tekrar
                // izliyorum" durumu sessizce kaybolur (MAL-Sync tüm `my_list_status`
                // nesnesini gönderdiği için bu tuzağa düşmez). Bayrak yalnız TRUE yönünde
                // eklenir; `false` göndermek, verimizin zaten false olduğu anlamına gelir
                // ve sunucunun varsayılan davranışıyla aynıdır.
                if (entry.status == WatchStatus.Repeating || entry.isRepeating) {
                    if (entry.type == MediaType.Manga) append("&is_rereading=true")
                    else append("&is_rewatching=true")
                }
            }
            if (SyncField.VOLUME_PROGRESS in fields && entry.type == MediaType.Manga) {
                append("&num_volumes_read=${entry.volumeProgress}")
            }
            if (SyncField.START_DATE in fields) {
                // Alan yalnızca kullanıcı tarihi değiştirdiğinde gönderilir:
                //  - tam tarih  → olduğu gibi
                //  - boşaltıldı → `0000-00-00` (MAL'de "tarihi sil" karşılığı; MAL-Sync aynı
                //    sabiti kullanır). Boş string 400 döndürür.
                //  - parsiyel ("2016", "2016-05") → GÖNDERİLMEZ (uzak değer korunur).
                val startDate = entry.startDate.sendableDate()
                when {
                    startDate != null -> append("&start_date=$startDate")
                    entry.startDate.isNullOrBlank() -> append("&start_date=$DATE_CLEAR")
                }
            }
            if (SyncField.END_DATE in fields) {
                val endDate = entry.endDate.sendableDate()
                when {
                    endDate != null -> append("&finish_date=$endDate")
                    entry.endDate.isNullOrBlank() -> append("&finish_date=$DATE_CLEAR")
                }
            }
            if (SyncField.PRIORITY in fields) {
                // MAL önceliği 0..2 (0=düşük, 1=orta, 2=yüksek). ALL'da boş öncelik gönderilmez.
                val priority = entry.priority
                when {
                    priority != null -> append("&priority=${priority.coerceIn(0, 2)}")
                    !fullWrite -> append("&priority=0")
                }
            }
            if (SyncField.REWATCH_COUNT in fields) {
                if (entry.type == MediaType.Manga) append("&num_times_reread=${entry.repeatCount}")
                else append("&num_times_rewatched=${entry.repeatCount}")
            }
            if (SyncField.REPEAT_VALUE in fields) {
                if (entry.type == MediaType.Manga) append("&reread_value=${entry.repeatValue.coerceIn(0, 5)}")
                else append("&rewatch_value=${entry.repeatValue.coerceIn(0, 5)}")
            }
            if (SyncField.TAGS in fields) {
                // Etiketler virgülle ayrılır; boş string etiketleri TEMİZLER. Bu yüzden boş
                // değer yalnızca kullanıcı etiketleri sildiğinde (diff) gönderilir.
                val tags = entry.tags.orEmpty()
                if (tags.isNotBlank() || !fullWrite) {
                    append("&tags=${URLEncoder.encode(tags, "UTF-8")}")
                }
            }
            if (SyncField.NOTES in fields) {
                val notes = entry.notes.orEmpty()
                if (notes.isNotBlank() || !fullWrite) {
                    append("&comments=${URLEncoder.encode(notes, "UTF-8")}")
                }
            }
        }

        // Değişen alan yoksa istek atılmaz (boş PATCH 400 döner).
        // `false` = "hiçbir şey yazılmadı": çağıran bunu "eşitlendi" diye raporlamamalı.
        if (postData.isEmpty()) return false

        // Gövde parçaları "&alan=değer" olarak birleştirildiği için baştaki '&' kırpılır
        // (yalnızca ilk alan gönderildiğinde gövde "&score=..." başlıyordu).
        val body = postData.trimStart('&')

        return patchWithDateClearFallback(url = url, body = body, token = token, context = context)
    }

    /**
     * MAL'de bu yapım için liste kaydı var mı?
     *
     * @return `true` var, `false` yok (MAL 404 "not in list"), `null` bilinmiyor (ağ/HTTP).
     *         `null` durumunda çağıran ESKİ davranışı (kısmi yazım) korur: ağ hatası yüzünden
     *         yazımı tümden engellemek yeni bir sessiz kayıp olurdu.
     */
    private fun malListEntryExists(
        token: String,
        malId: Int,
        endpointType: String,
        context: Context
    ): Boolean? = runSyncCatching {
        val url = URL("https://api.myanimelist.net/v2/$endpointType/$malId?fields=my_list_status")
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .build()
        com.kitsugi.animelist.core.network.KitsugiHttpClient.syncClient.newCall(request).execute().use { response ->
            when {
                response.code == 404 -> false
                !response.isSuccessful -> null
                else -> {
                    val body = response.body?.string().orEmpty()
                    runSyncCatching { JSONObject(body).optJSONObject("my_list_status") != null }
                        .getOrNull()
                }
            }
        }
    }.getOrNull()

    /** [DATE_CLEAR] taşıyan PATCH 400 ile düşerse, tarih temizlemeyi bırakıp bir kez dener. */
    private fun patchWithDateClearFallback(
        url: URL,
        body: String,
        token: String,
        context: Context
    ): Boolean {
        return try {
            executePatch(url = url, body = body, token = token, context = context)
            true
        } catch (e: IllegalStateException) {
            val message = e.message.orEmpty()
            val rejected = message.contains(": 400") || message.contains("bad_request")
            if (!body.contains(DATE_CLEAR) || !rejected) throw e
            val withoutClears = stripDateClears(body)
            // Yalnız tarih temizleme isteniyorduysa gönderilecek başka alan yok: temizleme
            // yapılamadı, ama başka hiçbir alan da kaybedilmedi.
            if (withoutClears.isBlank()) return false
            executePatch(url = url, body = withoutClears, token = token, context = context)
            true
        }
    }

    /**
     * Form gövdesinden YALNIZ tarih temizleme parametrelerini çıkarır.
     *
     * `unit` testleri için görünür: sentinel'i taşıyan PATCH 400 ile düşerse diğer alanlar
     * kaybolmasın diye gövde bu yardımcıyla süzülür (E62).
     */
    internal fun stripDateClears(body: String): String = body.split('&')
        .filterNot { it.endsWith("=$DATE_CLEAR") }
        .joinToString("&")

    private fun executePatch(url: URL, body: String, token: String, context: Context) {
        fun sendOnce(bearerToken: String): okhttp3.Response {
            val request = Request.Builder()
                .url(url)
                .patch(body.toRequestBody("application/x-www-form-urlencoded; charset=utf-8".toMediaTypeOrNull()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Bearer $bearerToken")
                .build()
            return com.kitsugi.animelist.core.network.KitsugiHttpClient.syncClient.newCall(request).execute()
        }

        try {
            val unauthorizedFirst = sendOnce(token).use { response ->
                if (response.code == 401) {
                    true
                } else {
                    if (!response.isSuccessful) {
                        val errorText = response.body?.string().orEmpty()
                        throw IllegalStateException(
                            context.getString(R.string.account_sync_api_error, "MAL", response.code, errorText)
                        )
                    }
                    false
                }
            }
            if (unauthorizedFirst) {
                val refreshed = ExternalAuthManager.refreshMalTokenSync(context)
                if (refreshed.isNullOrBlank()) {
                    ExternalAuthManager.markSessionExpired(context, "mal")
                    throw IllegalStateException("MyAnimeList oturumu sona erdi (HTTP 401)")
                }
                sendOnce(refreshed).use { retryResponse ->
                    if (!retryResponse.isSuccessful) {
                        val errorText = retryResponse.body?.string().orEmpty()
                        if (retryResponse.code == 401) {
                            ExternalAuthManager.markSessionExpired(context, "mal")
                        }
                        throw IllegalStateException(
                            context.getString(R.string.account_sync_api_error, "MAL", retryResponse.code, errorText)
                        )
                    }
                }
            }
        } catch (e: Exception) {
            if (e is IllegalStateException) throw e
            val reason = e.message ?: context.getString(R.string.account_sync_unknown_error)
            throw IllegalStateException(
                context.getString(R.string.account_sync_connection_error, "MAL", reason),
                e
            )
        }
    }

    fun deleteMalEntry(
        token: String,
        entry: MediaEntry,
        context: Context
    ) {
        require(entry.type == MediaType.Anime || entry.type == MediaType.Manga) {
            context.getString(R.string.account_sync_mal_type_unsupported)
        }
        val malId = entry.malId?.takeIf { it.isRealMalId() }
            ?: error(context.getString(R.string.account_sync_mal_id_invalid))

        val endpointType = when (entry.type) {
            MediaType.Anime, MediaType.Movie, MediaType.TvShow -> "anime"
            MediaType.Manga -> "manga"
        }

        val url = URL("https://api.myanimelist.net/v2/$endpointType/$malId/my_list_status")

        fun deleteOnce(bearerToken: String): okhttp3.Response {
            val request = Request.Builder()
                .url(url)
                .delete()
                .header("Authorization", "Bearer $bearerToken")
                .build()
            return com.kitsugi.animelist.core.network.KitsugiHttpClient.syncClient.newCall(request).execute()
        }

        try {
            val unauthorizedFirst = deleteOnce(token).use { response ->
                when {
                    response.code == 404 -> false
                    response.code == 401 -> true
                    !response.isSuccessful -> {
                        val errorText = response.body?.string().orEmpty()
                        throw IllegalStateException(
                            context.getString(R.string.account_sync_mal_delete_api_error, response.code, errorText)
                        )
                    }
                    else -> false
                }
            }
            if (unauthorizedFirst) {
                val refreshed = ExternalAuthManager.refreshMalTokenSync(context)
                if (refreshed.isNullOrBlank()) {
                    ExternalAuthManager.markSessionExpired(context, "mal")
                    throw IllegalStateException("MyAnimeList oturumu sona erdi (HTTP 401)")
                }
                deleteOnce(refreshed).use { retryResponse ->
                    if (retryResponse.code == 404) return
                    if (!retryResponse.isSuccessful) {
                        val errorText = retryResponse.body?.string().orEmpty()
                        if (retryResponse.code == 401) {
                            ExternalAuthManager.markSessionExpired(context, "mal")
                        }
                        throw IllegalStateException(
                            context.getString(R.string.account_sync_mal_delete_api_error, retryResponse.code, errorText)
                        )
                    }
                }
            }
        } catch (e: Exception) {
            if (e is IllegalStateException) throw e
            val reason = e.message ?: context.getString(R.string.account_sync_unknown_error)
            throw IllegalStateException(
                context.getString(R.string.account_sync_connection_error, "MAL", reason),
                e
            )
        }
    }

    /**
     * MAL'deki mevcut liste durumunu okur (`my_list_status.status`).
     *
     * @return API'nin döndürdüğü durum dizgesi; kayıt yoksa (404) ya da okuma
     *         doğrulanamazsa `null` — çağıran bu durumda eski varsayılanı (`completed`)
     *         korur, ağ hatası yazımı engellemez.
     */
    private fun malCurrentListStatus(
        token: String,
        malId: Int,
        endpointType: String
    ): String? = runSyncCatching {
        val url = URL("https://api.myanimelist.net/v2/$endpointType/$malId?fields=my_list_status")
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .build()
        com.kitsugi.animelist.core.network.KitsugiHttpClient.syncClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                null
            } else {
                val body = response.body?.string().orEmpty()
                runSyncCatching {
                    JSONObject(body).optJSONObject("my_list_status")
                        ?.optString("status")
                        ?.takeIf { it.isNotBlank() }
                }.getOrNull()
            }
        }
    }.getOrNull()

    /**
     * MAL'a gönderilecek `status` değeri (E77).
     *
     * (E72) politika katmanında "MAL'de durum korunur, yalnız bayrak yazılır" kararı
     * verilmişti; ancak yazıcı `Repeating`i her zaman `completed`a çeviriyordu. Tekrar
     * sürerken yalnızca bayrak taşınmak istendiğinde bile (STATUS alanı bayrak yüzünden
     * kümede) yayındaki bir yapımın MAL durumu `watching` → `completed`a dönüyordu.
     * Artık tekrar yazımında MAL'daki mevcut durum — `plan_to_*` dışında — aynen
     * korunur; mevcut durum bilinmiyorsa (kayıt yok / okuma başarısız) eski varsayılan
     * `completed` kullanılır. Bilinmeyen dizge API'ye geçirilmez (allowlist).
     *
     * `unit` testleri için görünür (bkz. `stripDateClears` deseni).
     */
    internal fun malStatusForWrite(
        entryStatus: WatchStatus,
        mediaType: MediaType,
        existingMalStatus: String?
    ): String {
        if (entryStatus != WatchStatus.Repeating) return entryStatus.toMalStatus(mediaType)
        val existing = existingMalStatus?.trim()?.lowercase()
        return existing?.takeIf { it in PRESERVABLE_MAL_STATUSES } ?: "completed"
    }

    /** Tekrar yazımında korunabilecek MAL durumları (`plan_to_*` hariç — bkz. E72). */
    private val PRESERVABLE_MAL_STATUSES =
        setOf("watching", "reading", "completed", "on_hold", "dropped")

    private fun WatchStatus.toMalStatus(mediaType: MediaType): String {
        return when (this) {
            WatchStatus.Watching -> if (mediaType == MediaType.Manga) "reading" else "watching"
            WatchStatus.Completed -> "completed"
            WatchStatus.Planned -> if (mediaType == MediaType.Manga) "plan_to_read" else "plan_to_watch"
            WatchStatus.Dropped -> "dropped"
            WatchStatus.Paused -> "on_hold"
            // MAL'de "tekrar izleniyor" AYRI BİR DURUM DEĞİLDİR: kayıt `completed` kalır ve
            // `is_rewatching` bayrağı set edilir (MAL'ın kendi arayüzü ve MAL-Sync
            // `_provider/MyAnimeList_api/single.ts` ile aynı davranış). Eskiden "watching"
            // gönderiliyordu; bu, tamamlanmış bir animeyi MAL'da yeniden "İzleniyor"a
            // düşürüyor ve tamamlanma durumunu bozuyordu.
            WatchStatus.Repeating -> "completed"
        }
    }

    private fun Int.isRealMalId(): Boolean {
        return this > 0 && this < 100_000_000
    }
}
