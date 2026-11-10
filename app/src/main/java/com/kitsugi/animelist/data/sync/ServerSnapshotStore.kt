package com.kitsugi.animelist.data.sync

import android.content.Context
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaIdentity

/**
 * Bir kaynağın **son TAM çekimde sunucuda görülen** kimlik anahtarlarını tutar
 * ("sunucuda en son ne vardı?" defteri).
 *
 * Neden gerekli: liste yenilemesinde "yerelde var ama sunucu yanıtında yok" tek başına
 * **silinmiş** demek değildir. Şu satırlar da yanıtta görünmez:
 *  - yerelde eklenip sunucuya **henüz gönderilememiş** kayıt (kuyruk temizlenmiş olabilir),
 *  - sunucuya gönderimi "atlandı" denen kayıt (kimlik güvenle çözülemedi),
 *  - MAL'da `nsfw=false` iken +18 kayıtlar,
 *  - yarım/kesik yanıtlar.
 *
 * Bu yüzden silme kararı "sunucuda **daha önce görülmüş** ve şimdi görülmeyen" satırlarla
 * sınırlanır. Bir satır ne zaman sunucuda görüldüyse (tam çekim) deftere yazılır; sonraki
 * tam çekimde yoksa kullanıcı gerçekten çıkarmıştır → silinebilir.
 *
 * Defter SharedPreferences'ta `StringSet` olarak tutulur: Room şeması büyümez, delta
 * çekimleri defteri **küçültmez** (yoksa ikinci tam çekim her şeyi "bilinmiyor" sayardı).
 */
class ServerSnapshotStore(private val context: Context) {

    private val prefs by lazy {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** Kaynağın son tam çekimde görülen kimlik anahtarları (boş = hiç tam çekim yok). */
    fun read(source: String): Set<String> =
        runCatching { prefs.getStringSet(key(source), emptySet()).orEmpty().toSet() }
            .getOrDefault(emptySet())

    /** Tam çekim sonrası defteri tazeler. */
    fun write(source: String, entries: List<MediaEntry>) {
        if (entries.isEmpty()) return
        val keys = entries.asSequence()
            .flatMap { MediaIdentity.keys(it).asSequence() }
            .filter { it.isNotBlank() }
            .take(MAX_KEYS)
            .toSet()
        if (keys.isEmpty()) return
        runCatching {
            prefs.edit().putStringSet(key(source), keys).apply()
        }
    }

    fun clear(source: String? = null) {
        runCatching {
            val editor = prefs.edit()
            if (source == null) {
                prefs.all.keys
                    .filter { it.startsWith(KEY_PREFIX) }
                    .forEach { editor.remove(it) }
            } else {
                editor.remove(key(source))
            }
            editor.apply()
        }
    }

    private fun key(source: String) = KEY_PREFIX + source.lowercase()

    private companion object {
        const val PREFS_NAME = "kitsugi_server_snapshot"
        const val KEY_PREFIX = "snapshot_"

        /** Emniyet tavanı: 20 bin anahtar ≈ 500 KB üst sınır. */
        const val MAX_KEYS = 20_000
    }
}
