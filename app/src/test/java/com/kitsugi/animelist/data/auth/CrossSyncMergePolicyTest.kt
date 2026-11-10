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
 * Çapraz eşitleme birleştirme kuralının davranış sözleşmesi.
 *
 * Her test, sahada görülmüş ya da görülmesi matematiksel olarak mümkün bir çelişkiyi
 * temsil eder; kuralın bu çelişkiyi deterministik biçimde çözmesi beklenir.
 */
class CrossSyncMergePolicyTest {

    private fun e(
        source: String,
        status: WatchStatus = WatchStatus.Watching,
        progress: Int = 0,
        total: Int? = 12,
        score: Int? = null,
        volumeProgress: Int = 0,
        startDate: String? = null,
        endDate: String? = null,
        repeatCount: Int = 0,
        isRepeating: Boolean = false,
        isFavorite: Boolean = false,
        updatedAt: Long = 0L,
        notes: String? = null,
        tags: String? = null,
        priority: Int? = null
    ) = MediaEntry(
        id = source.hashCode(),
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
        volumeProgress = volumeProgress,
        isFavorite = isFavorite,
        updatedAt = updatedAt
    )

    // ── 1. İlerleme asla gerilemez ───────────────────────────────────────────────

    @Test
    fun `en yuksek ilerleme kazanir`() {
        val merged = CrossSyncMergePolicy.merge(
            listOf(
                e("mal", progress = 3, updatedAt = 100),
                e("anilist", progress = 7, updatedAt = 50)
            )
        )
        assertEquals(7, merged.progress)
        assertEquals(7, merged.rawMaxProgress)
    }

    @Test
    fun `ilerleme toplami astiginda yazilabilir deger kirpilir ama raporlama bozulmaz`() {
        // Bir kaynak 25 bölüm bildiriyor, gerçek toplam 12. Kırpılmasaydı MAL PATCH 400
        // döner ve AYNI istekteki durum/puan/tarih yazımı da düşerdi.
        val merged = CrossSyncMergePolicy.merge(
            listOf(
                e("mal", total = 12, progress = 5),
                e("simkl", total = 25, progress = 25)
            )
        )
        assertEquals(12, merged.progress)
        assertEquals(25, merged.rawMaxProgress)
        assertEquals(WatchStatus.Completed, merged.status)
    }

    @Test
    fun `toplam bilinmiyorsa ilerleme kirpilmaz`() {
        val merged = CrossSyncMergePolicy.merge(
            listOf(e("mal", total = null, progress = 40))
        )
        assertEquals(40, merged.progress)
    }

    // ── 2. Toplam çıkarımı ───────────────────────────────────────────────────────

    @Test
    fun `simkl toplami tamamlanma cikariminda kullanilmaz`() {
        // Simkl'in `total` değeri "yayınlanmış bölüm" sayısıdır; devam eden dizilerde
        // gerçek toplam değildir. Tek başına kaynak olduğunda bile çıkarım yapılmamalı.
        val merged = CrossSyncMergePolicy.merge(
            listOf(e("simkl", status = WatchStatus.Watching, progress = 3, total = 3))
        )
        assertEquals(WatchStatus.Watching, merged.status)
        assertNull(merged.total)
    }

    @Test
    fun `toplam yakalandiginda durum tamamlandiya yukselir`() {
        // Tek kaynaklı grup: eskiden `singleSourceGroup` koruması yüzünden bu düzeltme
        // hiç yapılmıyordu (E37).
        val merged = CrossSyncMergePolicy.merge(
            listOf(e("mal", status = WatchStatus.Watching, progress = 12, total = 12))
        )
        assertEquals(WatchStatus.Completed, merged.status)
        assertTrue(SyncField.STATUS in CrossSyncMergePolicy.dirtyFields(
            e("mal", status = WatchStatus.Watching, progress = 12, total = 12), merged
        ))
    }

    // ── 3. Durum çözümü ─────────────────────────────────────────────────────────

    @Test
    fun `bilerek birakilan yapim eski tamamlandi kaydiyla geri acilmaz`() {
        // E39: sabit öncelik zinciri (Watching > Paused > Dropped) yüzünden kullanıcının
        // "bıraktım" kararı sessizce "izliyorum"a çevriliyordu.
        val merged = CrossSyncMergePolicy.merge(
            listOf(
                e("mal", status = WatchStatus.Watching, progress = 4, updatedAt = 10),
                e("anilist", status = WatchStatus.Dropped, progress = 4, updatedAt = 99)
            )
        )
        assertEquals(WatchStatus.Dropped, merged.status)
    }

    @Test
    fun `on ikide on iki izleyip birakan kullanici tamamlandiya cevrilmez`() {
        val merged = CrossSyncMergePolicy.merge(
            listOf(e("mal", status = WatchStatus.Dropped, progress = 12, total = 12))
        )
        assertEquals(WatchStatus.Dropped, merged.status)
    }

    @Test
    fun `duraklatilan yapim izliyora cevrilmez`() {
        val merged = CrossSyncMergePolicy.merge(
            listOf(
                e("kitsu", status = WatchStatus.Watching, progress = 6, updatedAt = 5),
                e("mal", status = WatchStatus.Paused, progress = 6, updatedAt = 20)
            )
        )
        assertEquals(WatchStatus.Paused, merged.status)
    }

    @Test
    fun `planlandi kaydi ilerlemesi olan kaydin durumunu ezmez`() {
        val merged = CrossSyncMergePolicy.merge(
            listOf(
                e("anilist", status = WatchStatus.Watching, progress = 5, updatedAt = 10),
                e("mal", status = WatchStatus.Planned, progress = 0, updatedAt = 99)
            )
        )
        assertEquals(WatchStatus.Watching, merged.status)
    }

    @Test
    fun `tum kaynaklar planlandiysa planlandi kalir`() {
        val merged = CrossSyncMergePolicy.merge(
            listOf(
                e("mal", status = WatchStatus.Planned, progress = 0),
                e("kitsu", status = WatchStatus.Planned, progress = 0)
            )
        )
        assertEquals(WatchStatus.Planned, merged.status)
    }

    @Test
    fun `esit zaman damgasinda sira deterministiktir`() {
        val a = e("anilist", status = WatchStatus.Dropped, progress = 2, updatedAt = 7)
        val b = e("mal", status = WatchStatus.Watching, progress = 2, updatedAt = 7)
        assertEquals(
            CrossSyncMergePolicy.merge(listOf(a, b)).status,
            CrossSyncMergePolicy.merge(listOf(b, a)).status
        )
    }

    // ── 4. Puan ────────────────────────────────────────────────────────────────

    @Test
    fun `BILINEN SINIRLAMA puan silme diger kaynaklarin puaniyla doldurulur`() {
        // Altı API'nin hiçbiri "puanı kaldırdım" ile "hiç puan vermedim" arasında ayrım
        // sunmaz (hepsi `null`/`0` döner). Bu yüzden bir kaynakta puanı silmek, eski
        // puanlı kaydın değerini geri getirir. Bilinçli tercih: rastgele temizlemek
        // kullanıcının diğer servislerdeki puanlarını sessizce silerdi.
        val merged = CrossSyncMergePolicy.merge(
            listOf(
                e("mal", score = 8, updatedAt = 10),
                e("anilist", score = 0, updatedAt = 99)
            )
        )
        assertEquals(8, merged.score)
    }

    @Test
    fun `en guncel gecerli puan kazanir`() {
        val merged = CrossSyncMergePolicy.merge(
            listOf(
                e("mal", score = 9, updatedAt = 10),
                e("anilist", score = 6, updatedAt = 50)
            )
        )
        assertEquals(6, merged.score)
    }

    @Test
    fun `esit zaman damgasinda yuksek puan kazanir`() {
        val merged = CrossSyncMergePolicy.merge(
            listOf(
                e("mal", score = 5, updatedAt = 10),
                e("anilist", score = 8, updatedAt = 10)
            )
        )
        assertEquals(8, merged.score)
    }

    // ── 5. Tarihler ────────────────────────────────────────────────────────────

    @Test
    fun `baslangic en erken bitis en gec tarihtir ve kaynak sirasina bagli degildir`() {
        // E38: `firstNotNullOfOrNull` kaynak sırasına bağlıydı; aynı veri kümesi her
        // turda farklı sonuç üretebiliyordu.
        val list = listOf(
            e("anilist", startDate = "2024-05-01", endDate = "2024-06-01"),
            e("mal", startDate = "2024-03-01", endDate = "2024-07-09"),
            e("kitsu", startDate = "2024-04-01", endDate = "2024-05-20")
        )
        val merged = CrossSyncMergePolicy.merge(list)
        assertEquals("2024-03-01", merged.startDate)
        assertEquals("2024-07-09", merged.endDate)
        assertEquals(merged, CrossSyncMergePolicy.merge(list.reversed()))
    }

    @Test
    fun `bos tarihler yok sayilir`() {
        val merged = CrossSyncMergePolicy.merge(
            listOf(e("mal", startDate = "   ", endDate = ""), e("anilist", startDate = "2023-01-02"))
        )
        assertEquals("2023-01-02", merged.startDate)
        assertNull(merged.endDate)
    }

    // ── 6. Tekrar / favori ─────────────────────────────────────────────────────

    @Test
    fun `tekrar sayisi ve bayragi korunur`() {
        val merged = CrossSyncMergePolicy.merge(
            listOf(
                e("mal", repeatCount = 2, isRepeating = false),
                e("anilist", repeatCount = 1, isRepeating = true)
            )
        )
        assertEquals(2, merged.repeatCount)
        assertTrue(merged.isRepeating)
    }

    @Test
    fun `favori tek kaynakta bile olsa korunur`() {
        val merged = CrossSyncMergePolicy.merge(
            listOf(e("mal", isFavorite = false), e("anilist", isFavorite = true))
        )
        assertTrue(merged.isFavorite)
    }

    // ── 7. Kirli alan kümesi ───────────────────────────────────────────────────

    @Test
    fun `hicbir sey degismediyse bos kume doner`() {
        val current = e("mal", status = WatchStatus.Watching, progress = 3, score = 7)
        val merged = CrossSyncMergePolicy.merge(
            listOf(current, e("anilist", status = WatchStatus.Watching, progress = 3, score = 7))
        )
        assertEquals(emptySet<SyncField>(), CrossSyncMergePolicy.dirtyFields(current, merged))
    }

    @Test
    fun `kaynak daha ileriyse geri yazilmaz`() {
        // Kullanıcı başka bir istemciden MAL'da 11. bölümü işledi; yerel birleşim 5'te
        // kaldıysa MAL'a "5" yazıp ilerlemeyi geri sarmak veri kaybıdır.
        val current = e("mal", progress = 11, total = 12)
        val merged = CrossSyncMergePolicy.merge(
            listOf(current, e("anilist", progress = 5, total = 12))
        )
        assertFalse(SyncField.PROGRESS in CrossSyncMergePolicy.dirtyFields(current, merged))
    }

    @Test
    fun `tekrar bayragi degistiginde durum da yazilir`() {
        // MAL'de "tekrar izleniyor" ayrı bir durum değildir: `status` + `is_rewatching`
        // birlikte gönderilmelidir.
        val current = e("mal", status = WatchStatus.Completed, progress = 12, isRepeating = false)
        val merged = CrossSyncMergePolicy.merge(
            listOf(
                current,
                e(
                    "anilist",
                    status = WatchStatus.Repeating,
                    progress = 2,
                    repeatCount = 1,
                    isRepeating = true
                )
            )
        )
        val dirty = CrossSyncMergePolicy.dirtyFields(current, merged)
        assertTrue(SyncField.STATUS in dirty)
        assertTrue(SyncField.REWATCH_COUNT in dirty)
    }

    @Test
    fun `favori yalniz tek yonde yazilir`() {
        val notFav = e("anilist", isFavorite = false)
        val favMerged = CrossSyncMergePolicy.merge(listOf(notFav, e("mal", isFavorite = false)))
        assertFalse(SyncField.FAVORITE in CrossSyncMergePolicy.dirtyFields(notFav, favMerged))

        val favSource = e("anilist", isFavorite = true)
        val merged = CrossSyncMergePolicy.merge(listOf(notFav, favSource))
        assertTrue(merged.isFavorite)
        // Hedefte zaten favori ise hiç istek atılmaz.
        assertFalse(SyncField.FAVORITE in CrossSyncMergePolicy.dirtyFields(favSource, merged))
        assertTrue(SyncField.FAVORITE in CrossSyncMergePolicy.dirtyFields(notFav, merged))
    }

    @Test
    fun `not etiket ve oncelik asla kirli sayilmaz`() {
        val current = e("mal", notes = "MAL notu", tags = "a,b", priority = 2)
        val merged = CrossSyncMergePolicy.merge(
            listOf(current, e("anilist", notes = "AniList notu", updatedAt = 99))
        )
        val dirty = CrossSyncMergePolicy.dirtyFields(current, merged)
        assertFalse(SyncField.NOTES in dirty)
        assertFalse(SyncField.TAGS in dirty)
        assertFalse(SyncField.PRIORITY in dirty)
    }

    // ── 8. Sağlayıcı süzgeciyle birlikte ──────────────────────────────────────

    @Test
    fun `kirli alanlar saglayici yetenegine gore suzulur`() {
        val current = e("kitsu", status = WatchStatus.Watching, progress = 2, total = 12)
        val merged = CrossSyncMergePolicy.merge(
            listOf(
                current,
                e("mal", status = WatchStatus.Completed, progress = 12, startDate = "2024-01-01")
            )
        )
        val kitsuDirty = CrossSyncMergePolicy.dirtyFieldsFor(current, merged, "kitsu")
        assertTrue(SyncField.STATUS in kitsuDirty)
        assertTrue(SyncField.PROGRESS in kitsuDirty)
        assertTrue(SyncField.START_DATE in kitsuDirty)
        // Kitsu cilt ilerlemesi desteklemez; birleşimde olsa bile yazılmaz.
        assertFalse(SyncField.VOLUME_PROGRESS in kitsuDirty)
    }

    @Test
    fun `tekrar suren yapimda ilerleme max alinmaz`() {
        // 12/12 bitmiş bir yapımı tekrar izlemeye başlayan kullanıcı: MAL 12, AniList 2.
        // `max` alınsaydı AniList'e 12 yazılır ve tekrar anında "bitmiş" görünürdü.
        val malRow = e("mal", status = WatchStatus.Completed, progress = 12, isRepeating = true, repeatCount = 1)
        val aniRow = e("anilist", status = WatchStatus.Repeating, progress = 2, isRepeating = true, repeatCount = 1, updatedAt = 50)
        val merged = CrossSyncMergePolicy.merge(listOf(malRow, aniRow))
        assertEquals(WatchStatus.Repeating, merged.status)
        assertEquals(2, merged.progress)
        assertTrue(merged.isRepeating)
        // Tekrar ilerlemesi MAL'a geri yazılmaz (ilerleme gerilemez kuralı).
        assertFalse(SyncField.PROGRESS in CrossSyncMergePolicy.dirtyFieldsFor(malRow, merged, "mal"))
    }

    @Test
    fun `MAL_da baslayan tekrar AniList'e REPEATING olarak yazilir ve bir kez yazilir`() {
        // Yalnız MAL bayrağı "şu an tekrar izleniyor" diyor. AniList bunu ancak REPEATING
        // durumuyla ifade edebilir. Yazımdan sonra AniList satırı REPEATING okunur ve
        // sonraki turlarda kirli alan kalmaz (sonsuz yazım yok).
        val malRow = e("mal", status = WatchStatus.Completed, progress = 12, isRepeating = true, repeatCount = 1)
        val aniRow = e("anilist", status = WatchStatus.Completed, progress = 12, isRepeating = false, repeatCount = 1)
        val merged = CrossSyncMergePolicy.merge(listOf(malRow, aniRow))
        assertEquals(WatchStatus.Repeating, merged.status)
        assertTrue(SyncField.STATUS in CrossSyncMergePolicy.dirtyFieldsFor(aniRow, merged, "anilist"))

        // Yazım sonrası durum (AniList artık REPEATING) → döngü yok.
        val after = aniRow.copy(status = WatchStatus.Repeating, isRepeating = true)
        assertEquals(
            emptySet<SyncField>(),
            CrossSyncMergePolicy.dirtyFieldsFor(after, CrossSyncMergePolicy.merge(listOf(malRow, after)), "anilist")
        )
    }

    @Test
    fun `tekrar sirasinda AniList ilerlemesi geri sayilir`() {
        // E66: 12/12 bitmiş yapım MAL'da tekrar izlenmeye başlandı (2. bölüm). AniList'te
        // durum REPEATING'e çekildiğinde oradaki ilerleme de 2 olmalı; `dirtyFields`in
        // "ilerleme asla gerilemez" koruması bunu engelliyordu ve AniList sonsuza kadar
        // "REPEATING 12/12" gösteriyordu.
        val malRow = e("mal", status = WatchStatus.Completed, progress = 2, isRepeating = true, repeatCount = 1, updatedAt = 100)
        val aniRow = e("anilist", status = WatchStatus.Repeating, progress = 12, isRepeating = true, repeatCount = 1, updatedAt = 10)
        val merged = CrossSyncMergePolicy.merge(listOf(malRow, aniRow))
        assertEquals(WatchStatus.Repeating, merged.status)
        assertEquals(2, merged.progress)

        val aniDirty = CrossSyncMergePolicy.dirtyFieldsFor(aniRow, merged, "anilist")
        assertTrue(SyncField.PROGRESS in aniDirty)
    }

    @Test
    fun `bayrakli hedefte tekrar sirasinda ilerleme geri yazilmaz`() {
        // MAL'de ilerlemeyi düşürmek `is_rewatching`i sıfırlar; bayrak taşıyan hedefte
        // tekrar ilerlemesi geri yazılmaz (yalnız AS_STATUS hedeflerde geri sayılır).
        val malRow = e("mal", status = WatchStatus.Completed, progress = 12, isRepeating = true, repeatCount = 1, updatedAt = 10)
        val aniRow = e("anilist", status = WatchStatus.Repeating, progress = 2, isRepeating = true, repeatCount = 1, updatedAt = 100)
        val merged = CrossSyncMergePolicy.merge(listOf(malRow, aniRow))
        assertEquals(2, merged.progress)
        assertFalse(SyncField.PROGRESS in CrossSyncMergePolicy.dirtyFieldsFor(malRow, merged, "mal"))
    }

    @Test
    fun `biten tekrar bayat REPEATING satirini ezmez`() {
        // E67: MAL'da tekrar bitti (bayrak kapandı, DAHA YENİ). AniList'te kalan bayat
        // REPEATING satırı birleşimi "tekrar"da tutsaydı her turda MAL'a is_rewatching=true
        // geri yazılır ve tekrar asla bitmezdi.
        val malRow = e("mal", status = WatchStatus.Completed, progress = 12, isRepeating = false, repeatCount = 1, updatedAt = 200)
        val aniRow = e("anilist", status = WatchStatus.Repeating, progress = 3, isRepeating = true, repeatCount = 1, updatedAt = 100)
        val merged = CrossSyncMergePolicy.merge(listOf(malRow, aniRow))

        assertEquals(WatchStatus.Completed, merged.status)
        assertFalse(merged.isRepeating)
        // Bayat satırın düşük ilerlemesi birleşime sızmamalı.
        assertEquals(12, merged.progress)
        // AniList'e "tamamlandı" yazılır; MAL'a dokunulmaz.
        assertTrue(SyncField.STATUS in CrossSyncMergePolicy.dirtyFieldsFor(aniRow, merged, "anilist"))
        assertFalse(SyncField.STATUS in CrossSyncMergePolicy.dirtyFieldsFor(malRow, merged, "mal"))
    }

    @Test
    fun `esit zaman damgasinda tekrar kazanir`() {
        // Tekrarı başlatan turda karşı satır aynı saniyede yazılabilir; eşitlikte tekrar
        // kazanmazsa başlatılan tekrar hemen iptal edilirdi.
        val malRow = e("mal", status = WatchStatus.Completed, progress = 2, isRepeating = true, repeatCount = 1, updatedAt = 100)
        val aniRow = e("anilist", status = WatchStatus.Completed, progress = 12, isRepeating = false, updatedAt = 100)
        assertEquals(WatchStatus.Repeating, CrossSyncMergePolicy.merge(listOf(malRow, aniRow)).status)
    }

    @Test
    fun `esit zaman damgasinda tekrar satiri da deterministik secilir`() {
        // E70: iki kaynak AYNI saniyede güncellenmişse `maxByOrNull` ilk rastlananı seçiyordu
        // → birleşik ilerleme aday sırasına bağlı kalıp 6 ↔ 13 arasında gidip geliyor, her
        // iki yöne de yazım üretiyordu. Eşitlikte yüksek ilerleme (veri kaybı olmasın), sonra
        // kaynak adı ve satır kimliği sıralamayı tekilleştirir.
        val a = e("mal", status = WatchStatus.Completed, progress = 6, total = 20, isRepeating = true, repeatCount = 1, updatedAt = 20)
        val b = e("kitsu", status = WatchStatus.Completed, progress = 13, total = 20, isRepeating = true, repeatCount = 1, updatedAt = 20)
        val first = CrossSyncMergePolicy.merge(listOf(a, b))
        val second = CrossSyncMergePolicy.merge(listOf(b, a))
        assertEquals(first, second)
        assertEquals(13, first.progress)
        assertEquals(WatchStatus.Repeating, first.status)
    }

    @Test
    fun `toplam eldeki ilerlemeyi tasimiyorsa buyuk toplam kullanilir`() {
        // E71: öncelikli kaynağın (AniList 12) toplamı eldeki ilerlemeyi (14) taşımıyor;
        // 12'ye kırpmak ilerlemeyi geri yazmak olurdu. Bu durumda adaylardaki en büyük
        // toplam (Bangumi 13) kullanılır; sonuç yine deterministiktir.
        val ani = e("anilist", progress = 14, total = 12)
        val bangumi = e("bangumi", progress = 14, total = 13)
        assertEquals(13, CrossSyncMergePolicy.merge(listOf(ani, bangumi)).total)
        assertEquals(13, CrossSyncMergePolicy.merge(listOf(bangumi, ani)).total)
    }

    @Test
    fun `toplam kaynagi aday sirasindan bagimsizdir`() {
        // İki kaynak toplamda anlaşmıyorsa seçim aday sırasına bağlı OLMAMALI; aksi hâlde
        // kırpma değeri turlar arasında değişip ilerleme bir aşağı bir yukarı yazılırdı.
        val ani = e("anilist", total = 12)
        val mal = e("mal", total = 13)
        assertEquals(13, CrossSyncMergePolicy.merge(listOf(ani, mal)).total)
        assertEquals(13, CrossSyncMergePolicy.merge(listOf(mal, ani)).total)
    }

    @Test
    fun `tekrar ifade edemeyen hedefin durumu degistirilmez`() {
        // Simkl/Bangumi "tekrar izleniyor" bilgisini TAŞIYAMAZ. Tekrar yüzünden bunların
        // durumunu değiştirmek her turda aynı isteği atardı (sonsuz yazım) ve kullanıcının
        // Simkl'deki "tamamlandı" işaretini bozardı.
        val malRow = e("mal", status = WatchStatus.Completed, progress = 12, isRepeating = true, repeatCount = 1)
        val simklRow = e("simkl", status = WatchStatus.Completed, progress = 12, isRepeating = false)
        val merged = CrossSyncMergePolicy.merge(listOf(malRow, simklRow))
        assertEquals(WatchStatus.Repeating, merged.status)
        assertEquals(
            emptySet<SyncField>(),
            CrossSyncMergePolicy.dirtyFieldsFor(simklRow, merged, "simkl")
        )
    }

    @Test
    fun `Kitsu ve MAL bayrak farkini durum yazimiyla tasir`() {
        val merged = CrossSyncMergePolicy.merge(
            listOf(
                e("mal", status = WatchStatus.Completed, progress = 12, isRepeating = true, repeatCount = 1),
                e("kitsu", status = WatchStatus.Watching, progress = 12, isRepeating = false, repeatCount = 1)
            )
        )
        assertTrue(SyncField.STATUS in CrossSyncMergePolicy.dirtyFieldsFor(
            e("kitsu", status = WatchStatus.Watching, progress = 12, isRepeating = false, repeatCount = 1),
            merged,
            "kitsu"
        ))
        assertTrue(SyncField.STATUS in CrossSyncMergePolicy.dirtyFieldsFor(
            e("mal", status = WatchStatus.Completed, progress = 12, isRepeating = false, repeatCount = 1),
            merged,
            "mal"
        ))
    }

    @Test
    fun `simkl dropped durumunu yazmaz`() {
        val current = e("simkl", status = WatchStatus.Watching, progress = 3)
        val merged = CrossSyncMergePolicy.merge(
            listOf(current, e("mal", status = WatchStatus.Dropped, progress = 3, updatedAt = 99))
        )
        assertEquals(WatchStatus.Dropped, merged.status)
        // Simkl `dropped` bilmez; eşleme `notinteresting` olmalıdır (SimklSyncManager).
        assertTrue(SyncField.STATUS in CrossSyncMergePolicy.dirtyFieldsFor(current, merged, "simkl"))
    }

    // ── E78: `en güncel` sıralaması tek kaynaktan ────────────────────────────────
    // Eskiden AuthViewModel / MediaEntryBackup / smartImport aynı soruya
    // `maxByOrNull { it.updatedAt }` ile (E70 `id` tiebreak'i olmadan) cevap
    // veriyordu; eşit damgalı adaylarda yerel yansıma satırı ile tohum satırı
    // aynı kayda farklı kazananlar seçebiliyordu.

    // NOT (E82): `e()` yardımcısının `id` parametresi yoktur (`id = source.hashCode()`);
    // kimlik gerektiren senaryolar `.copy(id = ...)` ile kurulur.

    @Test
    fun `newest en yuksek damgayı secer`() {
        val older = e("mal", updatedAt = 100).copy(id = 1)
        val newer = e("anilist", updatedAt = 200).copy(id = 2)
        assertEquals(newer, CrossSyncMergePolicy.newestOrNull(listOf(older, newer)))
        assertEquals(newer, CrossSyncMergePolicy.newestOrNull(listOf(newer, older)))
    }

    @Test
    fun `newest esit damgada ilerleme sonra kaynak sonra id ile kirar`() {
        val ahead = e("mal", progress = 9, updatedAt = 100).copy(id = 5)
        val behind = e("anilist", progress = 4, updatedAt = 100).copy(id = 1)
        assertEquals(ahead, CrossSyncMergePolicy.newestOrNull(listOf(behind, ahead)))

        val aniListRow = e("anilist", progress = 4, updatedAt = 100).copy(id = 9)
        val malRow = e("mal", progress = 4, updatedAt = 100).copy(id = 2)
        assertEquals(aniListRow, CrossSyncMergePolicy.newestOrNull(listOf(malRow, aniListRow)))

        val lowId = e("mal", progress = 4, updatedAt = 100).copy(id = 3)
        val highId = e("mal", progress = 4, updatedAt = 100).copy(id = 8)
        assertEquals(lowId, CrossSyncMergePolicy.newestOrNull(listOf(highId, lowId)))
    }

    @Test
    fun `newest girdi sirasindan bagimsizdir`() {
        val rows = listOf(
            e("mal", progress = 6, updatedAt = 300).copy(id = 11),
            e("kitsu", progress = 6, updatedAt = 300).copy(id = 4),
            e("kitsu", progress = 6, updatedAt = 300).copy(id = 2),
            e("anilist", progress = 1, updatedAt = 900).copy(id = 30)
        )
        assertEquals(rows[3], CrossSyncMergePolicy.newestOrNull(rows))
        assertEquals(rows[3], CrossSyncMergePolicy.newestOrNull(rows.reversed()))
        val withoutNewest = rows.dropLast(1)
        assertEquals(rows[2], CrossSyncMergePolicy.newestOrNull(withoutNewest))
        assertEquals(rows[2], CrossSyncMergePolicy.newestOrNull(withoutNewest.reversed()))
    }

    @Test
    fun `newest bos listede null`() {
        assertEquals(null, CrossSyncMergePolicy.newestOrNull(emptyList()))
    }
}
