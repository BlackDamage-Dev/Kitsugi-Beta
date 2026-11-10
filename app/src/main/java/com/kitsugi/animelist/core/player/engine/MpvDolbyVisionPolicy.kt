package com.kitsugi.animelist.core.player.engine

import com.kitsugi.animelist.core.player.DolbyVisionHint

/**
 * Bu akış için MPV'nin `vo` / `hwdec` seçeneklerine uygulanacak geçersiz
 * kılmalar. `null` alan = kullanıcının kendi ayarı korunur.
 *
 * @property vo Önerilen video çıkışı (ör. `gpu-next`).
 * @property hwdec Önerilen donanım çözme modu (ör. `no`).
 * @property reason Teşhis/log nedeni.
 */
internal data class MpvDolbyVisionOptions(
    val vo: String?,
    val hwdec: String?,
    val reason: String
) {
    /** Uygulama sırasında değişiklik var mı diye karşılaştırmak için anahtar. */
    val key: String get() = "${vo ?: "-"}|${hwdec ?: "-"}|$reason"

    companion object {
        val NONE = MpvDolbyVisionOptions(vo = null, hwdec = null, reason = "no-override")
    }
}

/**
 * Android/JNI'den bağımsız Dolby Vision → MPV seçenek politikası.
 *
 * Neden var:
 *  - mpv'nin `vo=gpu` çıkışı DV RPU'sunu uygulamaz. P5 (IPTPQc2) içerikte renkler
 *    yeşil/mor çıkar. `vo=gpu-next` (libplacebo) RPU reshaping yapar.
 *  - RPU'nun kareye eklenmesi FFmpeg'in yazılım çözücüsüne bağlıdır; MediaCodec
 *    yolu RPU'yu taşımaz, dolayısıyla hwdec açıkken gpu-next de P5'i düzeltemez.
 *  - P7/P8 taban katmanları geçerli HDR10'dur; onlarda donanım çözme korunur
 *    (4K HEVC'yi telefonda yazılım çözmek gereksiz yere takılma üretir).
 */
internal object MpvDolbyVisionPolicy {

    const val VO_GPU_NEXT = "gpu-next"
    const val HWDEC_SOFTWARE = "no"

    fun resolve(
        hint: DolbyVisionHint?,
        autoFixEnabled: Boolean,
        configuredVo: String,
        configuredHwdec: String,
        gpuNextAvailable: Boolean
    ): MpvDolbyVisionOptions {
        if (hint == null) return MpvDolbyVisionOptions.NONE
        if (!autoFixEnabled) {
            return MpvDolbyVisionOptions(vo = null, hwdec = null, reason = "auto-fix-disabled")
        }
        if (!gpuNextAvailable) {
            // Bu libmpv derlemesinde gpu-next yok: hiçbir şeyi değiştirmeyip
            // kullanıcıyı uyarıyoruz (engine tarafı OSD gösterir).
            return MpvDolbyVisionOptions(vo = null, hwdec = null, reason = "gpu-next-unavailable")
        }

        // Kullanıcı zaten gpu-next seçtiyse vo'yu yeniden kurmaya gerek yok.
        val vo = if (configuredVo.trim().equals(VO_GPU_NEXT, ignoreCase = true)) null else VO_GPU_NEXT

        // P5 (ve profili bilinmeyen DV) için RPU şart → yazılım çözme zorunlu.
        // P7/P8 taban katmanı kendi başına geçerli HDR10'dur → donanım korunur.
        val hwdec = when {
            hint.isProfile5 || hint.isProfileUnknown ->
                if (configuredHwdec.trim().equals(HWDEC_SOFTWARE, ignoreCase = true)) null
                else HWDEC_SOFTWARE
            else -> null
        }

        val profileLabel = hint.profile?.toString() ?: "unknown"
        return MpvDolbyVisionOptions(
            vo = vo,
            hwdec = hwdec,
            reason = "dv-profile-$profileLabel"
        )
    }
}
