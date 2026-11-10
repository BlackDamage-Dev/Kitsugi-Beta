package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kardeş satır aynasının (E57) davranış sözleşmesi.
 *
 * Ayna bir *önbellek* işlemidir: kullanıcının düzenlemesini diğer yerel kopyalara taşır, ama
 * birleştirmenin "hiçbir alan gerilemez" ve "en yeni kazanır" kurallarını bozamaz.
 */
class SiblingMirrorPolicyTest {

    private fun row(
        id: Int,
        source: String,
        status: WatchStatus = WatchStatus.Watching,
        progress: Int = 0,
        total: Int? = 12,
        score: Int? = null,
        volumeProgress: Int = 0,
        startDate: String? = null,
        endDate: String? = null,
        repeatCount: Int = 0,
        repeatValue: Int = 0,
        isRepeating: Boolean = false,
        notes: String? = null,
        tags: String? = null,
        priority: Int? = null,
        isFavorite: Boolean = false,
        isPrivate: Boolean = false,
        isHidden: Boolean = false,
        updatedAt: Long = 100L
    ) = MediaEntry(
        id = id,
        title = "Test",
        type = MediaType.Anime,
        status = status,
        score = score,
        progress = progress,
        total = total,
        source = source,
        startDate = startDate,
        endDate = endDate,
        notes = notes,
        tags = tags,
        priority = priority,
        isRepeating = isRepeating,
        repeatCount = repeatCount,
        repeatValue = repeatValue,
        volumeProgress = volumeProgress,
        isFavorite = isFavorite,
        isPrivate = isPrivate,
        isHiddenFromStatusLists = isHidden,
        updatedAt = updatedAt
    )

    // ── 1. Monotonluk: ilerleme gerilemez ───────────────────────────────────────

    @Test
    fun `dusuk ilerleme kardes satirin ilerlemesini dusurmez`() {
        val sibling = row(id = 1, source = "anilist", progress = 12)
        val edited = row(id = 2, source = "kitsu", progress = 11)

        val out = SiblingMirrorPolicy.mirrored(sibling, edited, setOf(SyncField.PROGRESS))

        assertEquals(12, out.progress)
    }

    @Test
    fun `yuksek ilerleme kardes satira tasinir`() {
        val sibling = row(id = 1, source = "anilist", progress = 3)
        val edited = row(id = 2, source = "kitsu", progress = 7)

        val out = SiblingMirrorPolicy.mirrored(sibling, edited, setOf(SyncField.PROGRESS))

        assertEquals(7, out.progress)
    }

    @Test
    fun `cilt ilerlemesi ve tekrar sayisi da gerilemez`() {
        val sibling = row(id = 1, source = "kitsu", volumeProgress = 5, repeatCount = 3, repeatValue = 2)
        val edited = row(id = 2, source = "anilist", volumeProgress = 2, repeatCount = 1, repeatValue = 1)

        val out = SiblingMirrorPolicy.mirrored(
            sibling,
            edited,
            setOf(SyncField.VOLUME_PROGRESS, SyncField.REWATCH_COUNT, SyncField.REPEAT_VALUE)
        )

        assertEquals(5, out.volumeProgress)
        assertEquals(3, out.repeatCount)
        assertEquals(2, out.repeatValue)
    }

    // ── 2. Yalnız değişen alan taşınır ──────────────────────────────────────────

    @Test
    fun `puan duzenlemesi kardes satirin durumunu degistirmez`() {
        val sibling = row(id = 1, source = "mal", status = WatchStatus.Completed, progress = 12)
        val edited = row(id = 2, source = "anilist", status = WatchStatus.Watching, progress = 12, score = 8)

        val out = SiblingMirrorPolicy.mirrored(sibling, edited, setOf(SyncField.SCORE))

        assertEquals(WatchStatus.Completed, out.status)
        assertEquals(8, out.score)
    }

    @Test
    fun `durum acikca degistirildiyse yapiskanlik kurali ezilir`() {
        val sibling = row(id = 1, source = "mal", status = WatchStatus.Dropped)
        val edited = row(id = 2, source = "anilist", status = WatchStatus.Completed, progress = 12)

        val out = SiblingMirrorPolicy.mirrored(sibling, edited, setOf(SyncField.STATUS))

        assertEquals(WatchStatus.Completed, out.status)
    }

    @Test
    fun `durum degismediyse yapiskan Dropped korunur`() {
        val sibling = row(id = 1, source = "mal", status = WatchStatus.Dropped, progress = 12)
        val edited = row(id = 2, source = "anilist", status = WatchStatus.Watching, progress = 12, isRepeating = true)

        val out = SiblingMirrorPolicy.mirrored(
            sibling,
            edited,
            setOf(SyncField.REWATCH_COUNT)
        )

        assertEquals(WatchStatus.Dropped, out.status)
        assertTrue(out.isRepeating)
    }

    @Test
    fun `ilerlemesi olan satir planlandiya cekilmez`() {
        val sibling = row(id = 1, source = "mal", status = WatchStatus.Watching, progress = 5)
        val edited = row(id = 2, source = "anilist", status = WatchStatus.Planned, progress = 5)

        val out = SiblingMirrorPolicy.mirrored(sibling, edited, SyncField.ALL)

        assertEquals(WatchStatus.Watching, out.status)
    }

    // ── 3. Puan silme yayılmaz ──────────────────────────────────────────────────

    @Test
    fun `bos puan kardes satirin puanini silmez`() {
        val sibling = row(id = 1, source = "kitsu", score = 8)
        val edited = row(id = 2, source = "mal", score = null)

        val out = SiblingMirrorPolicy.mirrored(sibling, edited, setOf(SyncField.SCORE))

        assertEquals(8, out.score)
    }

    // ── 4. Tarihler ─────────────────────────────────────────────────────────────

    @Test
    fun `tam yazimda tarihler birlestirme kuraliyla birlestirilir`() {
        val sibling = row(id = 1, source = "mal", startDate = "2024-03-01", endDate = "2024-06-01")
        val edited = row(id = 2, source = "anilist", startDate = "2024-01-05", endDate = "2024-05-01")

        val out = SiblingMirrorPolicy.mirrored(sibling, edited, SyncField.ALL)

        assertEquals("2024-01-05", out.startDate)
        assertEquals("2024-06-01", out.endDate)
    }

    @Test
    fun `acik tarih degisikligi aynen tasinir ve silinebilir`() {
        val sibling = row(id = 1, source = "mal", startDate = "2024-03-01")
        val edited = row(id = 2, source = "anilist", startDate = null)

        val out = SiblingMirrorPolicy.mirrored(sibling, edited, setOf(SyncField.START_DATE))

        assertNull(out.startDate)
    }

    // ── 5. updatedAt korunur ────────────────────────────────────────────────────

    @Test
    fun `ayna kardes satirin updatedAt degerini degistirmez`() {
        val sibling = row(id = 1, source = "mal", updatedAt = 50L)
        val edited = row(id = 2, source = "anilist", progress = 6, updatedAt = 900L)

        val out = SiblingMirrorPolicy.mirrored(sibling, edited, setOf(SyncField.PROGRESS))

        assertEquals(50L, out.updatedAt)
    }

    // ── 6. Tekrar bayrağı ───────────────────────────────────────────────────────

    @Test
    fun `daha yeni satir tekrari bitirdiyse bayat bayrak temizlenir`() {
        val sibling = row(id = 1, source = "anilist", status = WatchStatus.Completed, isRepeating = true, updatedAt = 100L)
        val edited = row(id = 2, source = "mal", status = WatchStatus.Completed, isRepeating = false, updatedAt = 200L)

        val out = SiblingMirrorPolicy.mirrored(
            sibling,
            edited,
            setOf(SyncField.REWATCH_COUNT, SyncField.STATUS)
        )

        assertFalse(out.isRepeating)
    }

    @Test
    fun `kardes satir daha yeniyse acik tekrar korunur`() {
        val sibling = row(id = 1, source = "anilist", status = WatchStatus.Watching, isRepeating = true, updatedAt = 500L)
        val edited = row(id = 2, source = "mal", status = WatchStatus.Watching, isRepeating = false, updatedAt = 200L)

        val out = SiblingMirrorPolicy.mirrored(sibling, edited, setOf(SyncField.REWATCH_COUNT))

        assertTrue(out.isRepeating)
    }

    // ── 7. Metin alanları ───────────────────────────────────────────────────────

    @Test
    fun `tam yazimda bos not kardes satirin notunu silmez`() {
        val sibling = row(id = 1, source = "mal", notes = "kardeş not")
        val edited = row(id = 2, source = "anilist", notes = null)

        val out = SiblingMirrorPolicy.mirrored(sibling, edited, SyncField.ALL)

        assertEquals("kardeş not", out.notes)
    }

    @Test
    fun `acik not degisikligi bosaltma dahil tasinir`() {
        val sibling = row(id = 1, source = "mal", notes = "eski")
        val edited = row(id = 2, source = "anilist", notes = "")

        val out = SiblingMirrorPolicy.mirrored(sibling, edited, setOf(SyncField.NOTES))

        assertEquals("", out.notes)
    }

    @Test
    fun `favori yalniz ekleme yonunde tasinir`() {
        val favorite = row(id = 1, source = "anilist", isFavorite = true)
        val notFavorite = row(id = 2, source = "mal", isFavorite = false)

        val out = SiblingMirrorPolicy.mirrored(favorite, notFavorite, setOf(SyncField.FAVORITE))

        assertTrue(out.isFavorite)
    }

    // ── 8. Dejenere durumlar ────────────────────────────────────────────────────

    @Test
    fun `bos alan kumesi satiri degistirmez`() {
        val sibling = row(id = 1, source = "mal", progress = 4)

        val out = SiblingMirrorPolicy.mirrored(sibling, row(id = 1, source = "mal", progress = 9), emptySet())

        assertEquals(sibling, out)
    }

    @Test
    fun `ayni satir kendini aynalamaz`() {
        val same = row(id = 7, source = "mal", progress = 4)

        val out = SiblingMirrorPolicy.mirrored(same, same.copy(progress = 9), SyncField.ALL)

        assertEquals(same, out)
    }

    @Test
    fun `gizlilik acik degisiklikte iki yone de tasinir`() {
        val sibling = row(id = 1, source = "kitsu", isPrivate = true)
        val edited = row(id = 2, source = "mal", isPrivate = false)

        val out = SiblingMirrorPolicy.mirrored(sibling, edited, setOf(SyncField.PRIVATE))

        assertFalse(out.isPrivate)
    }
}
