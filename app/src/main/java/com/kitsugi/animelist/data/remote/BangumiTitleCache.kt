package com.kitsugi.animelist.data.remote

import android.content.Context
import android.content.SharedPreferences
import com.kitsugi.animelist.KitsugiApplication
import com.kitsugi.animelist.utils.PreferenceHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

import com.kitsugi.animelist.core.memory.BoundedCache

/**
 * Bangumi条目 → **Latin başlık önbelleği** (`romaji` / `english`).
 *
 * Bangumi'nin arama ve keşfet uçları (`POST /v0/search/subjects`, `GET /v0/subjects`) yalnızca
 * özgün adı (`name`, çoğu zaman Japonca/Çince) ve `name_cn` alanını döndürür; infobox'daki
 * `英文名` / `罗马字` alanları LISTELERDE yoktur. Bu yüzden şeritler/ızgaralar CJK başlıkla
 * çizilir ve uygulamanın "İngilizce/Romaji başlık" tercihi o ekranlarda işlemez.
 *
 * Bu önbellek, bir kaydın Latin adını öğrenildiği anda (detay sayfası, ilişki/öneri
 * zenginleştirmesi, karakter/kişi infobox ucu, TMDB/AniList eşleştirmesi) kalıcı olarak yazar.
 * Böylece aynı kayıt hangi listede geçerse geçsin Latin adıyla görünür — ek ağ isteği gerekmez.
 *
 * Depolama: `bangumi_title_cache_v1` tercihleri + bellek içi indeks. Okuma senkron (bellekten),
 * yazma IO kapsamında yapılır.
 */
internal object BangumiTitleCache {

    private const val PREFS_NAME = "bangumi_title_cache_v1"
    private const val KEY_PREFIX = "s"
    private const val MAX_ENTRIES = 1500

    /** Çözülmüş Latin adlar. `native` özgün (CJK olabilir) addır; yalnızca yedek olarak tutulur. */
    data class LatinTitles(
        val romaji: String?,
        val english: String?,
        val native: String?
    ) {
        val isEmpty: Boolean get() = romaji == null && english == null && native == null
    }

    private val memory = BoundedCache<Int, LatinTitles>("bangumi.titleCache", MAX_ENTRIES)
    @Volatile
    private var loaded = false
    @Volatile
    private var loading = false
    private val loadLock = Any()

    /** Disk okumalarını UI thread'inden uzak tutan kapsam. */
    private val warmUpScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Önbelleği arka planda belleğe alır. Keşfet/arama ilk çağrıldığında tetiklenir; böylece
     * ilk liste çiziminde bile Latin adlar hazırdır ve hiçbir çağıran disk beklemek zorunda kalmaz.
     */
    fun warmUp() {
        if (loaded || loading) return
        loading = true
        runCatching { warmUpScope.launch { ensureLoaded(); loading = false } }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun appContext(): Context? = KitsugiApplication.getInstance()?.applicationContext

    private fun usableLatin(value: String?): String? = value?.trim()?.takeIf {
        PreferenceHelpers.isLatinText(it)
    }

    /** Kısmi kaynak güncellemelerini eski alanları silmeden birleştiren saf yardımcı. */
    internal fun mergeTitles(
        previous: LatinTitles?,
        romaji: String?,
        english: String?,
        native: String?
    ): LatinTitles = LatinTitles(
        romaji = usableLatin(romaji) ?: previous?.romaji,
        english = usableLatin(english) ?: previous?.english,
        native = native?.trim()?.takeIf { it.isNotEmpty() } ?: previous?.native
    )

    /** Disk önbelleğini bir kez belleğe alır. */
    private fun ensureLoaded() {
        if (loaded) return
        synchronized(loadLock) {
            if (loaded) return
            val context = appContext()
            if (context == null) {
                loaded = true
                return
            }
            runCatching {
                val all = prefs(context).all
                for ((key, value) in all) {
                    if (!key.startsWith(KEY_PREFIX)) continue
                    val rawId = key.removePrefix(KEY_PREFIX).toIntOrNull() ?: continue
                    val text = value as? String ?: continue
                    val parsed = parse(rawId, text) ?: continue
                    memory[rawId] = parsed
                }
            }
            loaded = true
        }
    }

    private fun parse(rawId: Int, json: String): LatinTitles? = runCatching {
        val obj = JSONObject(json)
        val titles = LatinTitles(
            romaji = obj.optString("r").takeIf { it.isNotBlank() },
            english = obj.optString("e").takeIf { it.isNotBlank() },
            native = obj.optString("n").takeIf { it.isNotBlank() }
        )
        if (titles.isEmpty) {
            memory.remove(rawId)
            null
        } else {
            titles
        }
    }.getOrNull()

    /**
     * Kaydedilmiş Latin adları döner; yoksa null.
     *
     * Senkron disk okuması yapılmaz: bellek aynası henüz dolmadıysa yükleme arka planda
     * başlatılır ve bu çağrı için önbellek boş sayılır (sonraki çizimde dolu olur).
     */
    fun get(rawId: Int?): LatinTitles? {
        if (rawId == null || rawId <= 0) return null
        if (!loaded) {
            warmUp()
            return null
        }
        return memory[rawId]
    }

    /**
     * Liste/ızgara ekranları için gösterim başlığı: kayıt CJK adla geliyorsa ve önbellekte
     * Latin bir karşılık varsa o kullanılır, aksi halde mevcut başlık aynen döner.
     */
    fun latinTitleFor(rawId: Int?, currentTitle: String?): String? {
        val current = currentTitle?.trim().orEmpty()
        if (current.isNotEmpty() && !PreferenceHelpers.hasCjkCharacters(current)) return null
        val cached = get(rawId) ?: return null
        return usableLatin(cached.romaji) ?: usableLatin(cached.english)
    }

    /**
     * Kullanıcının **başlık dili** tercihine göre gösterim adı üretir.
     *
     * - `ENGLISH` → İngilizce ad (yoksa romaji)
     * - `NATIVE` / `JAPANESE_STAFF` → özgün (Japonca/Çince) ad
     * - diğer (ROMAJI) → romaji (yoksa İngilizce)
     *
     * Önbellekte uygun karşılık yoksa ya da üretilen ad [currentTitle] ile aynıysa `null`
     * döner; çağıran taraf bu durumda elindeki adı korur. Böylece "Listem" gibi yalnızca
     * özgün adla gelen (Bangumi koleksiyonundan içe aktarılmış) satırlar da seçilen dile
     * uyar ve gereksiz yere aynı dize yeniden üretilmez.
     */
    fun localizedTitleFor(rawId: Int?, currentTitle: String?, titleLanguage: String): String? {
        val current = currentTitle?.trim().orEmpty()
        val cached = get(rawId) ?: return null
        val candidate = when (titleLanguage.trim().uppercase(Locale.ROOT)) {
            "ENGLISH" -> usableLatin(cached.english) ?: usableLatin(cached.romaji)
            "NATIVE", "JAPANESE_STAFF" ->
                cached.native?.trim()?.takeIf { it.isNotEmpty() } ?: usableLatin(cached.romaji)
            else -> usableLatin(cached.romaji) ?: usableLatin(cached.english)
        } ?: return null
        return candidate.takeIf { !it.equals(current, ignoreCase = true) }
    }

    /** Bir kaydın Latin ad varyantlarını kaydeder (değişim yoksa disk yazması yapılmaz). */
    suspend fun put(rawId: Int?, romaji: String?, english: String?, native: String?) {
        val id = rawId ?: return
        if (id <= 0) return
        ensureLoaded()
        // Bilgi kaynakları alanları farklı zamanlarda tamamlayabilir: örneğin Bangumi infobox
        // yalnızca romaji verirken AniList daha sonra gerçek İngilizceyi bulur. Kısmi bir
        // güncelleme mevcut karşılığı silmemeli; aynı subject farklı keşfet raflarında eşzamanlı
        // çözülebileceğinden birleştirme + bellek yazımı atomik yapılır.
        val record = synchronized(loadLock) {
            val previous = memory[id]
            val merged = mergeTitles(previous, romaji, english, native)
            if (merged.isEmpty || merged == previous) return@synchronized null
            memory[id] = merged
            merged
        } ?: return
        val context = appContext() ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                val store = prefs(context)
                val editor = store.edit()
                if (store.all.size >= MAX_ENTRIES) {
                    // Basit ama güvenli tavır: kota dolarsa yeniden kurulur. Kayıplar yalnızca
                    // "bir daha çözülmesi gereken başlık" maliyeti doğurur, veri hatası doğurmaz.
                    editor.clear()
                }
                // Eşzamanlı kısmi güncellemelerin I/O sırası tersine dönse bile yeni alan silinmesin.
                val latest = synchronized(loadLock) { memory[id] ?: record }
                val payload = JSONObject()
                latest.romaji?.let { payload.put("r", it) }
                latest.english?.let { payload.put("e", it) }
                latest.native?.let { payload.put("n", it) }
                editor.putString("$KEY_PREFIX$id", payload.toString())
                editor.apply()
            }
        }
    }
}
