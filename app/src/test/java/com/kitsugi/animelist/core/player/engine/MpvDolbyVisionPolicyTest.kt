package com.kitsugi.animelist.core.player.engine

import com.kitsugi.animelist.core.player.DolbyVisionHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * MPV Dolby Vision vo/hwdec politikası testleri.
 *
 * Kural özeti:
 *  - DV değilse hiçbir şeye dokunma.
 *  - gpu-next yoksa hiçbir şeye dokunma (kullanıcıya OSD uyarısı gider).
 *  - P5 / profili bilinmeyen DV: gpu-next + yazılım çözme (RPU şart).
 *  - P7 / P8: yalnızca gpu-next, donanım çözme korunur.
 */
class MpvDolbyVisionPolicyTest {

    private fun resolve(
        hint: DolbyVisionHint?,
        autoFixEnabled: Boolean = true,
        configuredVo: String = "gpu",
        configuredHwdec: String = "auto-safe",
        gpuNextAvailable: Boolean = true
    ) = MpvDolbyVisionPolicy.resolve(
        hint = hint,
        autoFixEnabled = autoFixEnabled,
        configuredVo = configuredVo,
        configuredHwdec = configuredHwdec,
        gpuNextAvailable = gpuNextAvailable
    )

    @Test
    fun `non dv streams keep the user configuration`() {
        val options = resolve(hint = null)
        assertNull(options.vo)
        assertNull(options.hwdec)
        assertEquals("no-override", options.reason)
    }

    @Test
    fun `auto fix can be disabled from settings`() {
        val options = resolve(hint = DolbyVisionHint(5, "dv"), autoFixEnabled = false)
        assertNull(options.vo)
        assertNull(options.hwdec)
        assertEquals("auto-fix-disabled", options.reason)
    }

    @Test
    fun `missing gpu-next leaves options untouched`() {
        val options = resolve(hint = DolbyVisionHint(5, "dv"), gpuNextAvailable = false)
        assertNull(options.vo)
        assertNull(options.hwdec)
        assertEquals("gpu-next-unavailable", options.reason)
    }

    @Test
    fun `profile 5 forces gpu-next and software decoding`() {
        val options = resolve(hint = DolbyVisionHint(5, "dvh1.05"))
        assertEquals("gpu-next", options.vo)
        assertEquals("no", options.hwdec)
        assertEquals("dv-profile-5", options.reason)
    }

    @Test
    fun `unknown profile is treated like profile 5`() {
        val options = resolve(hint = DolbyVisionHint(null, "DV"))
        assertEquals("gpu-next", options.vo)
        assertEquals("no", options.hwdec)
        assertEquals("dv-profile-unknown", options.reason)
    }

    @Test
    fun `profile 7 keeps hardware decoding`() {
        val options = resolve(hint = DolbyVisionHint(7, "dvhe.07"))
        assertEquals("gpu-next", options.vo)
        assertNull(options.hwdec)
    }

    @Test
    fun `profile 8 keeps hardware decoding`() {
        val options = resolve(hint = DolbyVisionHint(8, "dvhe.08"))
        assertEquals("gpu-next", options.vo)
        assertNull(options.hwdec)
    }

    @Test
    fun `already configured gpu-next is not rewritten`() {
        val options = resolve(hint = DolbyVisionHint(8, "dvhe.08"), configuredVo = "gpu-next")
        assertNull(options.vo)
        assertNull(options.hwdec)
    }

    @Test
    fun `already configured software decoding is not rewritten`() {
        val options = resolve(hint = DolbyVisionHint(5, "dvhe.05"), configuredHwdec = "no")
        assertEquals("gpu-next", options.vo)
        assertNull(options.hwdec)
    }

    @Test
    fun `plan key changes when the profile class changes`() {
        val hevcSafe = resolve(hint = DolbyVisionHint(8, "dvhe.08"))
        val needsSoftware = resolve(hint = DolbyVisionHint(5, "dvhe.05"))
        assertEquals(false, hevcSafe.key == needsSoftware.key)
    }
}
