package com.kitsugi.animelist.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KitsugiReviewStableKeyTest {

    private fun review(
        id: Int? = null,
        username: String = "user",
        summary: String = "özet",
        fullText: String = "",
        dateText: String? = null,
        source: String = "tmdb"
    ) = KitsugiReview(
        id = id,
        username = username,
        avatarUrl = null,
        score = null,
        summary = summary,
        fullText = fullText,
        dateText = dateText,
        source = source
    )

    @Test
    fun `sayisal kimlik varsa kaynak ve kimlik kullanilir`() {
        assertEquals("rev:anilist:42", review(id = 42, source = "AniList").stableKey())
    }

    @Test
    fun `ayni kimlik farkli kaynakta farkli anahtar uretir`() {
        assertNotEquals(review(id = 7, source = "anilist").stableKey(), review(id = 7, source = "kitsu").stableKey())
    }

    @Test
    fun `kimlik yoksa anahtar null icermez ve icerikten turetilir`() {
        val key = review(id = null, username = "alice", summary = "Harika").stableKey()
        assertTrue(key.startsWith("rev:tmdb:h"))
        assertTrue("anahtar 'null' içermemeli: $key", !key.contains("null"))
    }

    @Test
    fun `ayni icerik her seferinde ayni anahtari uretir`() {
        val a = review(username = "Alice ", summary = "Harika", dateText = "2026-10-25")
        val b = review(username = "alice", summary = "Harika ", dateText = "2026-10-25")
        assertEquals(a.stableKey(), b.stableKey())
    }

    @Test
    fun `ayni yazarin iki farkli yorumu farkli anahtar alir`() {
        val first = review(username = "alice", summary = "İlk izlenim", dateText = null)
        val second = review(username = "alice", summary = "İkinci izlenim", dateText = null)
        assertNotEquals(first.stableKey(), second.stableKey())
    }

    @Test
    fun `bos kaynak adi unknown olarak yazilir`() {
        assertEquals("rev:unknown:1", review(id = 1, source = " ").stableKey())
    }
}
