package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HEDEF SÖZLÜĞÜNDE TEKRAR (E72) — gidiş-dönüş (round-trip) sözleşmesi.
 *
 * Kural: bir sağlayıcının satırı, birleşik durumun o sağlayıcıdaki karşılığına **eşitse**
 * `dirtyFieldsFor` BOŞ küme dönmelidir. Aksi hâlde her eşitleme turunda aynı istek yeniden
 * atılır (sonsuz yazım) ve hedefin durumu bozulur.
 *
 * Sahadaki asıl hata: MAL'de yayında olan bir yapım tekrar izlenirken satır
 * `watching` + `is_rewatching=true` olarak içe aktarılır; eski kural birleşik "tekrar"ı
 * MAL'de `completed`a çeviriyordu. Sonuç: (a) yapım MAL'de "tamamlandı" görünüyordu,
 * (b) içe aktarma tekrar `watching` döndüğü için satır her turda yeniden yazılıyordu.
 */
class RewatchTargetVocabularyTest {

    private fun row(
        id: Int,
        source: String,
        status: WatchStatus,
        progress: Int = 0,
        total: Int? = 12,
        isRepeating: Boolean = false,
        repeatCount: Int = 0,
        updatedAt: Long = 100L
    ) = MediaEntry(
        id = id,
        title = "Test",
        type = MediaType.Anime,
        status = status,
        score = null,
        progress = progress,
        total = total,
        source = source,
        isRepeating = isRepeating,
        repeatCount = repeatCount,
        updatedAt = updatedAt
    )

    private fun mergedOf(vararg rows: MediaEntry) =
        CrossSyncMergePolicy.merge(rows.toList())

    // ── 1. Tekrar zaten hedefte ifade edilmişse yazım YOK ──────────────────────

    @Test
    fun `MAL watching arti bayrak tekrar halinde bos kume doner`() {
        val mal = row(1, "mal", WatchStatus.Watching, progress = 3, isRepeating = true)
        val merged = mergedOf(mal)

        assertEquals(WatchStatus.Repeating, merged.status)
        assertTrue(CrossSyncMergePolicy.dirtyFieldsFor(mal, merged, "mal").isEmpty())
    }

    @Test
    fun `MAL completed arti bayrak tekrar halinde bos kume doner`() {
        val mal = row(1, "mal", WatchStatus.Completed, progress = 12, isRepeating = true)
        val merged = mergedOf(mal)

        assertEquals(WatchStatus.Repeating, merged.status)
        assertTrue(CrossSyncMergePolicy.dirtyFieldsFor(mal, merged, "mal").isEmpty())
    }

    @Test
    fun `Kitsu current arti reconsuming tekrar halinde bos kume doner`() {
        val kitsu = row(1, "kitsu", WatchStatus.Watching, progress = 3, isRepeating = true)
        val merged = mergedOf(kitsu)

        assertEquals(WatchStatus.Repeating, merged.status)
        assertTrue(CrossSyncMergePolicy.dirtyFieldsFor(kitsu, merged, "kitsu").isEmpty())
    }

    @Test
    fun `AniList REPEATING satiri bos kume doner`() {
        val aniList = row(1, "anilist", WatchStatus.Repeating, progress = 3)
        val merged = mergedOf(aniList)

        assertTrue(CrossSyncMergePolicy.dirtyFieldsFor(aniList, merged, "anilist").isEmpty())
    }

    // ── 2. Hedefin durumu tekrar yüzünden BOZULMAZ ─────────────────────────────

    @Test
    fun `MAL deki watching durumu tekrar yuzunden completeda cevrilmez`() {
        val mal = ProviderCapabilitiesCatalog.of("mal")

        assertEquals(
            WatchStatus.Watching,
            mal.statusFor(row(1, "mal", WatchStatus.Watching, progress = 3), WatchStatus.Repeating)
        )
        assertEquals(
            WatchStatus.Completed,
            mal.statusFor(row(1, "mal", WatchStatus.Completed, progress = 12), WatchStatus.Repeating)
        )
    }

    @Test
    fun `MAL de durumu olmayan satir tekrarda completed olur`() {
        val mal = ProviderCapabilitiesCatalog.of("mal")

        assertEquals(
            WatchStatus.Completed,
            mal.statusFor(row(1, "mal", WatchStatus.Planned), WatchStatus.Repeating)
        )
    }

    @Test
    fun `Kitsu tekrari yalniz current durumunda ifade eder`() {
        val kitsu = ProviderCapabilitiesCatalog.of("kitsu")

        // Kitsu `reconsuming` alanını yalnız `status=current` iken kabul eder (E26):
        // completed bir satırda tekrar başlarken durum da `current`a çekilmelidir.
        assertEquals(
            WatchStatus.Watching,
            kitsu.statusFor(row(1, "kitsu", WatchStatus.Completed, progress = 12), WatchStatus.Repeating)
        )
    }

    // ── 3. Tekrar BAŞLARKEN bayrak hedefe taşınır ──────────────────────────────

    @Test
    fun `MAL de baslayan tekrar bayragi yazar`() {
        val aniList = row(1, "anilist", WatchStatus.Repeating, progress = 2, updatedAt = 200L)
        val mal = row(2, "mal", WatchStatus.Completed, progress = 12, updatedAt = 100L)
        val merged = mergedOf(aniList, mal)

        assertEquals(WatchStatus.Repeating, merged.status)
        assertTrue(SyncField.STATUS in CrossSyncMergePolicy.dirtyFieldsFor(mal, merged, "mal"))
    }

    @Test
    fun `Kitsu da baslayan tekrar current durumuna cekilir`() {
        val aniList = row(1, "anilist", WatchStatus.Repeating, progress = 2, updatedAt = 200L)
        val kitsu = row(2, "kitsu", WatchStatus.Completed, progress = 12, updatedAt = 100L)
        val merged = mergedOf(aniList, kitsu)

        assertEquals(
            WatchStatus.Watching,
            ProviderCapabilitiesCatalog.of("kitsu")
                .statusFor(kitsu, merged.status)
        )
        assertTrue(SyncField.STATUS in CrossSyncMergePolicy.dirtyFieldsFor(kitsu, merged, "kitsu"))
    }

    // ── 4. Tekrar ifade EDEMEYEN hedefler ──────────────────────────────────────

    @Test
    fun `Bangumi ve Simkl tekrar yuzunden durum yazmaz`() {
        val aniList = row(1, "anilist", WatchStatus.Repeating, progress = 2, updatedAt = 200L)
        val bangumi = row(2, "bangumi", WatchStatus.Completed, progress = 12, updatedAt = 100L)
        val simkl = row(3, "simkl", WatchStatus.Completed, progress = 12, updatedAt = 100L)
        val merged = mergedOf(aniList, bangumi, simkl)

        assertFalse(SyncField.STATUS in CrossSyncMergePolicy.dirtyFieldsFor(bangumi, merged, "bangumi"))
        assertFalse(SyncField.STATUS in CrossSyncMergePolicy.dirtyFieldsFor(simkl, merged, "simkl"))
    }

    // ── 4b. Tek kaynaklı grup kendine yazmaz (idempotans) ──────────────────────

    /**
     * Tek sağlayıcılı bir kullanıcıda birleşim zaten o satırın kendisidir: ikinci turda
     * **hiçbir** alan yazılmamalıdır. Bu, "her turda aynı istek" sınıfındaki tüm hataların
     * (E47, E54, E67, E72) en genel dedektörüdür.
     */
    @Test
    fun `tek kaynakli grupta saglayici kendine yazmaz`() {
        // Sağlayıcıların gerçekten ÜRETEBİLDİĞİ durumlar (erişilebilir durum uzayı):
        // "tekrar" ya ayrı bir durumdur (AniList/Shikimori) ya bir bayraktır (MAL/Kitsu)
        // ya da hiç ifade edilemez (Bangumi/Simkl).
        fun statesFor(provider: String): List<Triple<WatchStatus, Boolean, String>> = buildList {
            listOf(
                WatchStatus.Watching, WatchStatus.Completed, WatchStatus.Planned,
                WatchStatus.Dropped, WatchStatus.Paused
            ).forEach { status ->
                add(Triple(status, false, "durum"))
            }
            when (provider) {
                "anilist", "shikimori" -> {
                    add(Triple(WatchStatus.Repeating, true, "$provider-REPEATING"))
                    add(Triple(WatchStatus.Repeating, false, "$provider-REPEATING"))
                }
                "mal" -> {
                    add(Triple(WatchStatus.Watching, true, "mal-bayrak"))
                    add(Triple(WatchStatus.Completed, true, "mal-bayrak"))
                }
                "kitsu" -> {
                    add(Triple(WatchStatus.Watching, true, "kitsu-reconsuming"))
                }
                "bangumi", "simkl" -> {
                    // Tekrarı ifade edemez; yalnız temel durumlar test edilir.
                }
            }
        }

        for (provider in listOf("anilist", "mal", "kitsu", "shikimori", "bangumi", "simkl")) {
            for ((status, flag, label) in statesFor(provider)) {
                val single = row(
                    id = 1,
                    source = provider,
                    status = status,
                    progress = 5,
                    total = 12,
                    isRepeating = flag
                )
                val merged = mergedOf(single)
                val dirty = CrossSyncMergePolicy.dirtyFieldsFor(single, merged, provider)
                assertEquals(
                    "$provider / $status / $label → beklenmeyen yazım: $dirty",
                    emptySet<SyncField>(),
                    dirty
                )
            }
        }
    }

    // ── 5. Tekrar BİTİNCE bayrak hedefte kapanır ───────────────────────────────

    @Test
    fun `tekrar bitince MAL durumu guncellenir`() {
        val mal = row(1, "mal", WatchStatus.Watching, progress = 12, isRepeating = true, updatedAt = 100L)
        val aniList = row(2, "anilist", WatchStatus.Completed, progress = 12, updatedAt = 300L)
        val merged = mergedOf(mal, aniList)

        assertEquals(WatchStatus.Completed, merged.status)
        assertTrue(SyncField.STATUS in CrossSyncMergePolicy.dirtyFieldsFor(mal, merged, "mal"))
    }
}
