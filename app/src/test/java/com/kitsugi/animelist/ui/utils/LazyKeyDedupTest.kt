package com.kitsugi.animelist.ui.utils

import com.kitsugi.animelist.data.remote.KitsugiReview
import com.kitsugi.animelist.data.remote.stableKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.4.369 çökmesi: `IllegalArgumentException: Key "tmdb_null" was already used`.
 * Lazy anahtarları her koşulda benzersiz olmalı; benzersiz olanlar da AYNEN korunmalı.
 */
class LazyKeyDedupTest {

    @Test
    fun `benzersiz temel anahtarlar aynen korunur`() {
        val keys = listOf("a", "b", "c").uniqueLazyKeys { it }
        assertEquals(listOf("a", "b", "c"), keys)
    }

    @Test
    fun `yinelenen temel anahtarlara sirali son ek eklenir`() {
        val keys = listOf("x", "y", "x", "x").uniqueLazyKeys { it }
        assertEquals(listOf("x", "y", "x#2", "x#3"), keys)
    }

    @Test
    fun `null ve bos anahtarlar da benzersizlesir`() {
        val keys = listOf<String?>(null, "", null).uniqueLazyKeys { it }
        assertEquals(3, keys.toSet().size)
        assertEquals("null", keys[0])
    }

    @Test
    fun `uretilen son ek gercek bir temel anahtarla cakisirsa bir sonrakine gecilir`() {
        // "k#2" gerçek bir öğenin anahtarı; ikinci "k" için "k#2" yerine "k#3" üretilmeli.
        val keys = listOf("k", "k#2", "k").uniqueLazyKeys { it }
        assertEquals(listOf("k", "k#2", "k#3"), keys)
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `farkli tipte ama ayni metne sahip anahtarlar cakismaz`() {
        val keys = listOf<Any>(5, "5", 5L).uniqueLazyKeys { it }
        assertEquals(3, keys.toSet().size)
    }

    @Test
    fun `indeksli surum indeksi temel anahtarda kullanabilir`() {
        val keys = listOf("a", "a").uniqueLazyKeysIndexed { index, item -> "$item@${index % 1}" }
        assertEquals(listOf("a@0", "a@0#2"), keys)
    }

    @Test
    fun `bos liste bos anahtar listesi dondurur`() {
        assertTrue(emptyList<String>().uniqueLazyKeys { it }.isEmpty())
    }

    @Test
    fun `rapordaki tmdb_null senaryosu artik benzersiz anahtar uretir`() {
        // TMDB yorumlarının sayısal kimliği yok → id = null (TmdbCreditsClient).
        val reviews = listOf(
            KitsugiReview(id = null, username = "alice", avatarUrl = null, score = 8, summary = "Harika", source = "tmdb"),
            KitsugiReview(id = null, username = "bob", avatarUrl = null, score = 6, summary = "Fena değil", source = "tmdb"),
            KitsugiReview(id = null, username = "carol", avatarUrl = null, score = 9, summary = "Başyapıt", source = "tmdb")
        )
        // ESKİ anahtar: hepsi "tmdb_null" → Compose çöküyordu.
        assertEquals(1, reviews.map { "${it.source}_${it.id}" }.toSet().size)
        // YENİ anahtar: hepsi farklı.
        val keys = reviews.uniqueLazyKeys { it.stableKey() }
        assertEquals(reviews.size, keys.toSet().size)
    }

    @Test
    fun `api ayni yorumu iki kez dondurse bile anahtarlar benzersiz kalir`() {
        val same = KitsugiReview(id = null, username = "dup", avatarUrl = null, score = 7, summary = "Aynı", source = "jikan")
        val keys = listOf(same, same.copy(), same.copy()).uniqueLazyKeys { it.stableKey() }
        assertEquals(3, keys.toSet().size)
        assertEquals(same.stableKey(), keys[0])
    }

    @Test
    fun `cok sayida yinelenen anahtarda performans ve benzersizlik`() {
        val keys = List(5_000) { "tek" }.uniqueLazyKeys { it }
        assertEquals(5_000, keys.toSet().size)
        assertEquals("tek#5000", keys.last())
    }
}
