package com.kitsugi.animelist.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Liste uzlaştırma kurallarının birim testleri — saf JVM, Android bağımlılığı yok.
 *
 * Bu kurallar "listeye bir şey ekledim / çıkardım, ne olacak?" sorusunun cevabıdır:
 *  - **Eklenen kayıt** (uzakta yeni ya da yerelde yeni): silinmez, eklenir.
 *  - **Çıkarılan kayıt**: yalnızca TAM turda ve yanıtın tam olduğu doğrulandıktan
 *    sonra silinir; delta turunda silinmez.
 *  - **Bekleyen yerel düzenleme/silme**: hiçbir turda ezilmez, diriltilmez.
 *  - **+18 süzgeci** (MAL `nsfw=false`): yanıtta görünmeyen +18 satırlar silinmez.
 */
class ImportReconcilerTest {

    // ── Silme adayları ────────────────────────────────────────────────

    @Test
    fun `uzakta eslesmeyen satir silme adayidir`() {
        val result = ImportReconciler.deletionCandidateIds(
            eligibleIds = listOf(1, 2, 3),
            matchedIds = setOf(1, 3),
            protectedIds = emptySet()
        )
        assertEquals(listOf(2), result)
    }

    @Test
    fun `bekleyen yerel duzenlemesi olan satir silinmez`() {
        val result = ImportReconciler.deletionCandidateIds(
            eligibleIds = listOf(1, 2),
            matchedIds = emptySet(),
            protectedIds = setOf(2)
        )
        assertEquals(listOf(1), result)
    }

    @Test
    fun `hicbir sey eslesmezse bile korunanlar silinmez`() {
        val result = ImportReconciler.deletionCandidateIds(
            eligibleIds = listOf(7, 8, 9),
            matchedIds = emptySet(),
            protectedIds = setOf(7, 8, 9)
        )
        assertTrue(result.isEmpty())
    }

    // ── Yarım yanıt koruması ──────────────────────────────────────────

    @Test
    fun `yarim yanitta silme yapilmaz`() {
        // 1000 satır vardı, sunucu 20 satır döndürdü (sayfa sınırında kesildi / filtre).
        assertFalse(
            ImportReconciler.coverageAllowsDeletion(
                existingCount = 1000,
                fetchedCount = 20,
                enforce = true
            )
        )
    }

    @Test
    fun `tam yanitta silme yapilir`() {
        assertTrue(
            ImportReconciler.coverageAllowsDeletion(
                existingCount = 1000,
                fetchedCount = 990,
                enforce = true
            )
        )
    }

    @Test
    fun `kullanici bastan yenile derse koruma uygulanmaz`() {
        // Kullanıcı bilinçli olarak sıfırdan uzlaştırma istedi → niyet net.
        assertTrue(
            ImportReconciler.coverageAllowsDeletion(
                existingCount = 1000,
                fetchedCount = 3,
                enforce = false
            )
        )
    }

    @Test
    fun `bos kutuphane icin silme karari engellenmez`() {
        assertTrue(
            ImportReconciler.coverageAllowsDeletion(
                existingCount = 0,
                fetchedCount = 0,
                enforce = true
            )
        )
    }

    @Test
    fun `esik sinirinda silme yapilir`() {
        // %30 tam sınır → izin verilir (küçük kütüphanelerde yanlış pozitif olmasın).
        val existing = 100
        val fetched = (existing * ImportReconciler.MIN_DELETION_COVERAGE).toInt()
        assertTrue(
            ImportReconciler.coverageAllowsDeletion(
                existingCount = existing,
                fetchedCount = fetched,
                enforce = true
            )
        )
    }

    // ── "Sunucuda daha önce görüldü" defteri ──────────────────────────

    @Test
    fun `defter bossa hicbir satir silinmez`() {
        // Uygulama yeni kuruldu / bu kaynak için hiç tam çekim yapılmadı.
        assertFalse(
            ImportReconciler.serverOriginConfirmed(
                keys = setOf("Anime:mal:1"),
                previousSnapshot = emptySet()
            )
        )
    }

    @Test
    fun `defterde olan satir silinebilir`() {
        assertTrue(
            ImportReconciler.serverOriginConfirmed(
                keys = setOf("Anime:mal:1"),
                previousSnapshot = setOf("Anime:mal:1", "Anime:mal:2")
            )
        )
    }

    @Test
    fun `yerelde eklenip sunucuda gorulmemis satir silinemez`() {
        // Kullanıcı çevrimdışı ekledi; sunucu listesinde hiç görünmedi → defterde yok.
        assertFalse(
            ImportReconciler.serverOriginConfirmed(
                keys = setOf("Anime:mal:999"),
                previousSnapshot = setOf("Anime:mal:1")
            )
        )
    }

    // ── Diriliş (tombstone) koruması ──────────────────────────────────

    @Test
    fun `bekleyen silme isi olan kayit geri eklenmez`() {
        assertTrue(
            ImportReconciler.isResurrection(
                importedKeys = setOf("Anime:mal:5114"),
                pendingDeleteKeys = setOf("Anime:mal:5114")
            )
        )
    }

    @Test
    fun `bekleyen silme ile alakasiz kayit eklenir`() {
        assertFalse(
            ImportReconciler.isResurrection(
                importedKeys = setOf("Anime:mal:1"),
                pendingDeleteKeys = setOf("Anime:mal:5114")
            )
        )
    }

    @Test
    fun `bos kumelerde dirilis yok`() {
        assertFalse(ImportReconciler.isResurrection(emptySet(), setOf("Anime:mal:1")))
        assertFalse(ImportReconciler.isResurrection(setOf("Anime:mal:1"), emptySet()))
        assertFalse(ImportReconciler.isResurrection(emptySet(), emptySet()))
    }
}
