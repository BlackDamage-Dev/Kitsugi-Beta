package com.kitsugi.animelist.data.music

import com.kitsugi.animelist.data.music.remote.MusicCatalogClient

/**
 * 🔑 Tek Noktadan Anahtar Doğrulama
 *
 * Ayarlar ekranı ("Harici Entegrasyonlar" → Müzik) ve okuyucu paneli
 * aynı doğrulama mantığını kullanır. Böylece bir yerde "geçerli" denen
 * anahtar diğerinde de geçerli sayılır — iki ayrı kod yolu yoktur.
 *
 * Anahtarsız kaynaklar (Openverse, Archive) her zaman `true` döner:
 * onlar için "doğrulama" kavramı yoktur, kota vardır.
 */
object MusicSourceValidator {

    private val client = MusicCatalogClient()

    /** Boş anahtar → false, anahtarsız kaynak → true. */
    suspend fun validate(sourceId: MusicSourceId, key: String): Boolean {
        val def = MusicSources.byId(sourceId) ?: return false
        if (def.keyless) return true
        if (key.isBlank()) return false
        return runCatching { client.validateKey(sourceId, key) }.getOrDefault(false)
    }

    /**
     * Kaynak için "hazır" koşulunu değerlendirir.
     * Anahtarsız kaynaklar daima hazır; anahtarlılar anahtar + doğrulama ister.
     */
    fun isReady(sourceId: MusicSourceId, key: String, validated: Boolean): Boolean {
        val def = MusicSources.byId(sourceId) ?: return false
        return if (def.keyless) true else key.isNotBlank() && validated
    }
}
