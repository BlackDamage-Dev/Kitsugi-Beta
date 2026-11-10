package com.kitsugi.animelist.core.diagnostics

import com.kitsugi.animelist.core.diagnostics.PreviousExitClassifier.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.4.369 `crash_history` raporlarındaki yanlış "SESSİZ ÇÖKME" alarmlarının sınıflandırılması.
 */
class PreviousExitClassifierTest {

    private fun classify(
        foreground: Boolean = false,
        recorded: Boolean = false,
        updated: Boolean = false,
        reason: Int? = null,
        description: String? = null,
        importance: Int? = null
    ) = PreviousExitClassifier.classify(foreground, recorded, updated, reason, description, importance)

    // ── Güncelleme tespiti ─────────────────────────────────────────────────────

    @Test
    fun `surum adi degistiyse guncelleme sayilir`() {
        // Rapor: 14:57 — pid=11641 (2.4.367) → yeni süreç 2.4.369.
        assertTrue(
            PreviousExitClassifier.isAppUpdate(
                previousBuild = "2.4.367-beta.29881326",
                currentBuild = "2.4.369-beta.29882115",
                packageLastUpdateMs = 0L,
                previousLastAliveMs = 1_000L
            )
        )
    }

    @Test
    fun `ayni surumde paket son kalp atisindan sonra guncellendiyse guncelleme sayilir`() {
        assertTrue(PreviousExitClassifier.isAppUpdate("2.4.369", "2.4.369", packageLastUpdateMs = 10_000L, previousLastAliveMs = 9_000L))
    }

    @Test
    fun `ayni surum ve eski paket zamani guncelleme degildir`() {
        assertFalse(PreviousExitClassifier.isAppUpdate("2.4.369", "2.4.369", packageLastUpdateMs = 1_000L, previousLastAliveMs = 90_000L))
    }

    @Test
    fun `bilinmeyen onceki surum tek basina guncelleme sayilmaz`() {
        assertFalse(PreviousExitClassifier.isAppUpdate("?", "2.4.369", packageLastUpdateMs = 0L, previousLastAliveMs = 5_000L))
        assertFalse(PreviousExitClassifier.isAppUpdate(null, "2.4.369", packageLastUpdateMs = 0L, previousLastAliveMs = 5_000L))
    }

    // ── Sınıflandırma ──────────────────────────────────────────────────────────

    @Test
    fun `cokme yakalayicisi zaten raporladiysa yineleme ve uyari yok`() {
        // Rapor: 15:07:40 ve 16:20:54 — aynı ön plan çökmesinin ikinci kopyası.
        val v = classify(foreground = true, recorded = true, reason = PreviousExitClassifier.REASON_CRASH)
        assertEquals(Kind.RECORDED_CRASH, v.kind)
        assertFalse(v.surface)
    }

    @Test
    fun `guncelleme on planda olsa bile uyari gostermez`() {
        val v = classify(foreground = true, updated = true)
        assertEquals(Kind.APP_UPDATE, v.kind)
        assertFalse(v.surface)
    }

    @Test
    fun `sistem kaydi paket guncellendi derse guncelleme sayilir`() {
        val v = classify(foreground = true, reason = PreviousExitClassifier.REASON_PACKAGE_UPDATED)
        assertEquals(Kind.APP_UPDATE, v.kind)
        assertFalse(v.surface)
    }

    @Test
    fun `ANR her zaman kullaniciya gosterilir`() {
        val v = classify(foreground = false, reason = PreviousExitClassifier.REASON_ANR)
        assertEquals(Kind.ANR, v.kind)
        assertTrue(v.surface)
    }

    @Test
    fun `native cokme gosterilir`() {
        assertTrue(classify(reason = PreviousExitClassifier.REASON_CRASH_NATIVE).surface)
    }

    @Test
    fun `kullanici kapattiysa uyari yok`() {
        assertFalse(classify(foreground = true, reason = PreviousExitClassifier.REASON_USER_REQUESTED).surface)
        val swiped = classify(foreground = true, reason = PreviousExitClassifier.REASON_OTHER, description = "remove task")
        assertEquals(Kind.USER_CLOSED, swiped.kind)
        assertFalse(swiped.surface)
    }

    @Test
    fun `arka planda bellek icin kapatilma normaldir, on planda ise gosterilir`() {
        assertFalse(classify(reason = PreviousExitClassifier.REASON_LOW_MEMORY, importance = 400).surface)
        assertTrue(classify(reason = PreviousExitClassifier.REASON_LOW_MEMORY, importance = 100).surface)
    }

    @Test
    fun `sistem kaydindaki onem degeri kalp atisi bayragini gecersiz kilar`() {
        // Kalp atışı ~15 sn gecikmeli yazılır: "ön planda" görünen ama aslında önbellekteki süreç.
        val v = classify(foreground = true, reason = PreviousExitClassifier.REASON_SIGNALED, importance = 400)
        assertFalse(v.foreground)
        assertFalse(v.surface)
    }

    @Test
    fun `sistem kaydi yoksa eski davranis korunur`() {
        assertTrue(classify(foreground = true).surface)
        val background = classify(foreground = false)
        assertEquals(Kind.UNKNOWN, background.kind)
        assertFalse(background.surface)
    }

    @Test
    fun `neden adlari okunur`() {
        assertEquals("ANR", PreviousExitClassifier.reasonName(PreviousExitClassifier.REASON_ANR))
        assertEquals("kayıt yok", PreviousExitClassifier.reasonName(null))
        assertEquals("REASON_99", PreviousExitClassifier.reasonName(99))
    }

    @Test
    fun `system ANR is not downgraded to unknown when logcat is empty`() {
        val verdict = classify(foreground = true, reason = PreviousExitClassifier.REASON_ANR, description = "Input dispatching timed out")
        val headline = PreviousExitClassifier.combinedHeadline(verdict, "SEBEP BULUNAMADI (sessiz SIGKILL olabilir)")
        assertTrue(headline.startsWith("ANR (SİSTEM KAYDI)"))
        assertFalse(headline.startsWith("SEBEP BULUNAMADI"))
    }

}
