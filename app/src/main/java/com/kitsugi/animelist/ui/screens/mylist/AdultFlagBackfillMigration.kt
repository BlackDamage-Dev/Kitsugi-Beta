package com.kitsugi.animelist.ui.screens.mylist

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.kitsugi.animelist.data.auth.BangumiAdultResolver
import com.kitsugi.animelist.data.auth.BangumiAuthStore
import com.kitsugi.animelist.data.local.MediaEntryDao
import com.kitsugi.animelist.data.local.MediaEntryEntity
import com.kitsugi.animelist.data.remote.BangumiIdNamespace
import com.kitsugi.animelist.data.remote.ShikimoriAdultResolver
import com.kitsugi.animelist.data.remote.TmdbAdultResolver
import com.kitsugi.animelist.data.remote.TmdbApiClient
import com.kitsugi.animelist.data.settings.SettingsDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Tüm kaynaklar için +18 (blur) işareti onarımı.
 *
 * ## Kök neden
 * +18 bulanıklığı veritabanındaki `isAdult` alanına dayanır. Bazı kaynakların LİSTE
 * API'leri bu bilgiyi hiç taşımaz:
 *
 *  • **Simkl** — `GET /sync/all-items/{movies|shows|anime}` yanıtında `adult` veya
 *    `certification` alanı YOKTUR. [com.kitsugi.animelist.data.remote.SimklAdultFlags]
 *    yalnızca nadiren gelen `adult` bayrağını yakalayabilir; geri kalan her kayıt
 *    `isAdult = false` ile yazılır. Sonuç: **+18 blur ayarı açık olsa bile**
 *    Listem → Simkl ekranında (ve "Tümü" sekmesinde) afişler bulanıklanmaz.
 *  • **Shikimori** — REST liste yanıtları `rating`/`genres` taşımaz (aynı sınıf hata;
 *    bu onarım onun yerini alır).
 *  • **Bangumi** — kullanıcı koleksiyonu içindeki kısaltılmış `subject` nesnesi `nsfw`
 *    alanını taşımaz; detay `GET /v0/subjects/{id}` ile doğrulanması gerekir.
 *
 * ## Çözüm
 * Kaydın taşıdığı kanonik kimlikle, +18 bilgisini güvenilir veren birinci el kaynaktan
 * yeniden sorulur:
 *
 *  • MAL kimliği olan anime/manga → Shikimori GraphQL ([ShikimoriAdultResolver]):
 *    kimlik doğrulaması gerektirmez, 50 kimliklik tek istekle toplu çözümlenir ve
 *    Shikimori anime/manga kimliklerini MAL ile paylaşır.
 *  • TMDB kimliği olan film/dizi → TMDB ayrıntısındaki `adult` bayrağı ([TmdbAdultResolver]).
 *  • Bangumi kaynaklı satır → Bangumi `subject.nsfw` alanı; yerel listedeki stable ID,
 *    gerçek `subject_id` değerine çevrilir.
 *
 * Kurallar:
 *  • Yalnızca `isAdult` false→true yönünde yazılır; mevcut yetişkin işaretleri ASLA kaldırılmaz.
 *  • "+18 değil" cevabı kalıcı olarak not edilir (aynı kimlik tekrar sorgulanmaz).
 *  • Ağ hatasında cevap alınamayan kimlik not EDİLMEZ → bir sonraki turda tekrar sorulur.
 *  • Yeni içe aktarım sonrası [requestRescan] çağrılır; böylece liste çekilir çekilmez
 *    bulanıklık doğru çalışır (12 saatlik soğuma beklenmez).
 */
internal object AdultFlagBackfillMigration {

    private const val TAG = "AdultFlagBackfill"
    private const val PREFS = "Kitsugi_list_filters"
    private const val KEY_DONE_VERSION = "adult_flag_backfill_version"
    private const val KEY_LAST_ATTEMPT = "adult_flag_backfill_last_attempt_ms"
    private const val KEY_RESCAN = "adult_flag_backfill_rescan"
    private const val KEY_CHECKED = "adult_flag_backfill_checked_ids"
    // v2: Bangumi koleksiyon satırlarının NSFW bilgisini de kaynak detayından doldur.
    private const val MIGRATION_VERSION = 2

    /** Yinelenen turlar arasındaki bekleme (yeni içe aktarım [requestRescan] ile sıfırlar). */
    private const val RETRY_COOLDOWN_MS = 12 * 60 * 60 * 1000L

    /** Tek turda işlenecek maksimum satır (Shikimori 50'lik paketlerle gider). */
    private const val MAX_ROWS_PER_RUN = 600

    /** Bangumi detay API'si ID başına istek istediğinden tek turdaki maliyeti sınırla. */
    private const val MAX_BANGUMI_LOOKUPS_PER_RUN = 60

    /** "Çözüldü ve +18 değil" defterinin üst sınırı. */
    private const val MAX_CHECKED_KEYS = 20_000

    /** Yeni içe aktarımdan sonra çağrılır: bir sonraki Listem açılışında yeniden tara. */
    fun requestRescan(context: Context) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_RESCAN, true)
                .putLong(KEY_LAST_ATTEMPT, 0L)
                .apply()
        }
    }

    /** Bir kaydın +18 bilgisinin HANGİ kaynaktan çözüleceğini belirler (saf fonksiyon). */
    internal sealed interface Lookup {
        /** Shikimori GraphQL (kimlik = MAL ID). */
        data class Shikimori(val kind: ShikimoriAdultResolver.Kind, val id: Int) : Lookup
        /** TMDB ayrıntı sorgusu (kimlik = TMDB ID). */
        data class Tmdb(val id: Int, val isMovie: Boolean) : Lookup
        /** Bangumi subject ayrıntısı (kimlik = ham `subject_id`). */
        data class Bangumi(val subjectId: Int) : Lookup
        /** Güvenilir kimlik yok → kayda dokunulmaz. */
        data object None : Lookup
    }

    private val MANGA_TYPES = setOf("Manga", "Manhwa", "Manhua", "Novel", "Oneshot")

    internal fun plan(entity: MediaEntryEntity): Lookup {
        val malId = entity.malId ?: 0
        val tmdbId = entity.tmdbId ?: 0
        val isManga = MANGA_TYPES.any { it.equals(entity.type, ignoreCase = true) }
        // `source == "tmdb"` satırlarında malId alanı TMDB kimliğini taşıyabilir
        // (bkz. MediaGalleryIdentity yorumu) → o satırlarda malId'yi MAL sanmayız.
        val isTmdbSource = entity.source.equals("tmdb", ignoreCase = true)
        val isBangumiSource = entity.source.equals("bangumi", ignoreCase = true) ||
            entity.source.equals("bgm", ignoreCase = true) ||
            entity.source.equals("bgm.tv", ignoreCase = true)
        return when {
            isBangumiSource -> {
                val subjectId = BangumiIdNamespace.rawIdOrNull(malId)
                if (subjectId != null) Lookup.Bangumi(subjectId) else Lookup.None
            }
            isTmdbSource -> {
                val id = if (tmdbId > 0) tmdbId else malId.takeIf { it in 1..99_999_999 } ?: 0
                if (id <= 0) Lookup.None
                else Lookup.Tmdb(id, isMovie = entity.type.equals("Movie", ignoreCase = true))
            }
            malId in 1..99_999_999 ->
                Lookup.Shikimori(
                    if (isManga) ShikimoriAdultResolver.Kind.MANGA else ShikimoriAdultResolver.Kind.ANIME,
                    malId
                )
            tmdbId > 0 -> Lookup.Tmdb(tmdbId, isMovie = entity.type.equals("Movie", ignoreCase = true))
            else -> Lookup.None
        }
    }

    /** Çözülmüş ama +18 OLMAYAN kimliklerin imzası. */
    internal fun checkedKey(lookup: Lookup): String? = when (lookup) {
        is Lookup.Shikimori -> "sk:${lookup.kind.name}:${lookup.id}"
        is Lookup.Tmdb -> "tm:${if (lookup.isMovie) "m" else "t"}:${lookup.id}"
        is Lookup.Bangumi -> "bgm:${lookup.subjectId}"
        Lookup.None -> null
    }

    suspend fun runIfNeeded(context: Context, dao: MediaEntryDao) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val firstRun = prefs.getInt(KEY_DONE_VERSION, 0) < MIGRATION_VERSION
        val rescanRequested = prefs.getBoolean(KEY_RESCAN, false)
        if (!firstRun && !rescanRequested) return

        val now = System.currentTimeMillis()
        val lastAttempt = prefs.getLong(KEY_LAST_ATTEMPT, 0L)
        if (!firstRun && lastAttempt > 0L && now - lastAttempt < RETRY_COOLDOWN_MS) return

        val checked = readChecked(prefs)

        val candidates = try {
            dao.getAll().filter { entity ->
                if (entity.isAdult) return@filter false
                // Kimliği olmayan satırı sorgulayamayız → deftere de yazmayız.
                val key = checkedKey(plan(entity)) ?: return@filter false
                key !in checked
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Liste kayıtları okunamadı: ${e.message}")
            return
        }

        if (candidates.isEmpty()) {
            finish(prefs, completed = true)
            return
        }

        // Bangumi için API'de toplu subject ayrıntı ucu yok; ID başına bir istek gerekir.
        // Son eklenen listeleri önce işlemek için DAO'nun id-desc sırası korunur ve her tur
        // en fazla 60 farklı subject sorgulanır. Kalanlar bir sonraki taramaya bırakılır.
        val bangumiCandidatesById = linkedMapOf<Int, MutableList<MediaEntryEntity>>()
        val otherCandidates = mutableListOf<MediaEntryEntity>()
        candidates.forEach { entity ->
            when (val lookup = plan(entity)) {
                is Lookup.Bangumi -> bangumiCandidatesById
                    .getOrPut(lookup.subjectId) { mutableListOf() }.add(entity)
                else -> otherCandidates.add(entity)
            }
        }
        val selectedBangumiGroups = bangumiCandidatesById.entries.take(MAX_BANGUMI_LOOKUPS_PER_RUN)
        val budgetedOther = otherCandidates.take(MAX_ROWS_PER_RUN)
        val budgeted = budgetedOther + selectedBangumiGroups.flatMap { it.value }
        val deferred = (otherCandidates.size - budgetedOther.size) +
            bangumiCandidatesById.entries.drop(MAX_BANGUMI_LOOKUPS_PER_RUN).sumOf { it.value.size }

        // Kimlik → o kimliği taşıyan satırlar (aynı yapım birden çok kaynakta satır olabilir).
        val shikimoriRows = HashMap<ShikimoriAdultResolver.Kind, MutableMap<Int, MutableList<MediaEntryEntity>>>()
        val tmdbRows = HashMap<Boolean, MutableMap<Int, MutableList<MediaEntryEntity>>>()
        val bangumiRows = LinkedHashMap<Int, MutableList<MediaEntryEntity>>()
        budgeted.forEach { entity ->
            when (val lookup = plan(entity)) {
                is Lookup.Shikimori -> shikimoriRows
                    .getOrPut(lookup.kind) { HashMap() }
                    .getOrPut(lookup.id) { mutableListOf() }.add(entity)
                is Lookup.Tmdb -> tmdbRows
                    .getOrPut(lookup.isMovie) { HashMap() }
                    .getOrPut(lookup.id) { mutableListOf() }.add(entity)
                is Lookup.Bangumi -> bangumiRows
                    .getOrPut(lookup.subjectId) { mutableListOf() }.add(entity)
                Lookup.None -> Unit
            }
        }

        var flagged = 0
        var networkIncomplete = false
        val notAdult = LinkedHashSet<String>()

        shikimoriRows.forEach { (kind, rowsById) ->
            val flags = ShikimoriAdultResolver.resolveAdultFlags(kind, rowsById.keys)
            if (!flags.keys.containsAll(rowsById.keys)) networkIncomplete = true
            flags.forEach { (id, isAdult) ->
                if (isAdult) {
                    rowsById[id].orEmpty().forEach { entity -> flagRow(dao, entity) { flagged++ } }
                } else {
                    notAdult.add("sk:${kind.name}:$id")
                }
            }
        }

        if (bangumiRows.isNotEmpty()) {
            val token = try {
                BangumiAuthStore.getValidToken(context)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Bangumi oturumu okunamadı: ${error.message}")
                null
            }
            if (token.isNullOrBlank()) {
                // Oturum yoksa bu kaynak şu an çözülemez. Kaydı false olarak işaretleme;
                // Bangumi yeniden içe aktarıldığında requestRescan bu taramayı tekrar açar.
                Log.w(TAG, "Bangumi oturumu yok — NSFW taraması sonraki içe aktarıma bırakıldı.")
            } else {
                val flags = BangumiAdultResolver.resolveAdultFlags(token, bangumiRows.keys)
                if (!flags.keys.containsAll(bangumiRows.keys)) networkIncomplete = true
                flags.forEach { (subjectId, isAdult) ->
                    if (isAdult) {
                        bangumiRows[subjectId].orEmpty().forEach { entity ->
                            flagRow(dao, entity) { flagged++ }
                        }
                    } else {
                        notAdult.add("bgm:$subjectId")
                    }
                }
            }
        }

        if (tmdbRows.isNotEmpty()) {
            val apiKey = runCatching {
                TmdbApiClient.resolveApiKey(
                    SettingsDataStore(context).settingsFlow.first().tmdbUserApiKey
                )
            }.getOrElse { TmdbApiClient.BUILT_IN_API_KEY }
            if (apiKey.isBlank()) {
                Log.w(TAG, "TMDB anahtarı yok — film/dizi kayıtları bu turda atlandı.")
                networkIncomplete = true
            } else {
                tmdbRows.forEach { (isMovie, rowsById) ->
                    val flags = TmdbAdultResolver.resolveAdultFlags(apiKey, isMovie, rowsById.keys)
                    if (!flags.keys.containsAll(rowsById.keys)) networkIncomplete = true
                    flags.forEach { (id, isAdult) ->
                        if (isAdult) {
                            rowsById[id].orEmpty().forEach { entity -> flagRow(dao, entity) { flagged++ } }
                        } else {
                            notAdult.add("tm:${if (isMovie) "m" else "t"}:$id")
                        }
                    }
                }
            }
        }

        writeChecked(prefs, checked, notAdult)

        Log.i(
            TAG,
            "+18 işaret onarımı: $flagged satır işaretlendi, $deferred satır sonraki tura kaldı, " +
                if (networkIncomplete) "ağ eksik kaldı → yeniden denenecek" else "tur tamamlandı"
        )

        finish(prefs, completed = !networkIncomplete && deferred == 0)
    }

    private fun finish(prefs: SharedPreferences, completed: Boolean) {
        val editor = prefs.edit()
        if (completed) {
            editor.putInt(KEY_DONE_VERSION, MIGRATION_VERSION)
        } else {
            editor.putLong(KEY_LAST_ATTEMPT, System.currentTimeMillis())
        }
        editor.putBoolean(KEY_RESCAN, false).apply()
    }

    /** Yalnızca false→true yönünde yazar; tek satır hatası turu bozmaz. */
    private suspend fun flagRow(dao: MediaEntryDao, entity: MediaEntryEntity, onFlagged: () -> Unit) {
        try {
            dao.update(entity.copy(isAdult = true))
            onFlagged()
        } catch (e: Exception) {
            Log.w(TAG, "Kayıt güncellenemedi (id=${entity.id}): ${e.message}")
        }
    }

    private fun readChecked(prefs: SharedPreferences): Set<String> {
        val raw = prefs.getString(KEY_CHECKED, "").orEmpty()
        if (raw.isBlank()) return emptySet()
        return raw.split('\n').filter { it.isNotBlank() }.toSet()
    }

    private fun writeChecked(prefs: SharedPreferences, previous: Set<String>, added: Set<String>) {
        if (added.isEmpty()) return
        val seen = HashSet<String>(previous.size + added.size)
        val merged = ArrayList<String>(previous.size + added.size)
        previous.forEach { if (seen.add(it)) merged.add(it) }
        added.forEach { if (seen.add(it)) merged.add(it) }
        // Sınır aşılırsa en eski yarısı düşer → defter sabit büyüklükte kalır.
        val trimmed = if (merged.size > MAX_CHECKED_KEYS) merged.take(MAX_CHECKED_KEYS) else merged
        runCatching {
            prefs.edit().putString(KEY_CHECKED, trimmed.joinToString("\n")).apply()
        }.onFailure { Log.w(TAG, "Kontrol defteri yazılamadı: ${it.message}") }
    }
}
