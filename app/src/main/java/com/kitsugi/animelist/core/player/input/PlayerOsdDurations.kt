package com.kitsugi.animelist.core.player.input

/**
 * Klavye göstergesinin (OSD) süre politikası.
 *
 * Neden sabit süreler yerine tek bir nesne?
 * Kullanıcı raporu (2026-11-04): "sadece tetiklediğim tuşun özelliği görünüp
 * yok olsun, 1–2 saniyede." Süreler dağınık `delay(650)` / `delay(500)` /
 * `delay(1000)` çağrılarından geliyordu ve her eylemde farklıydı. Artık:
 *
 *  - [PULSE_MS]   → anlık, ikili durum bildiren eylemler (oynat/duraklat, sessize al)
 *  - [NORMAL_MS]  → değer bildiren eylemler (ses, hız, arama, altyazı gecikmesi)
 *  - [LONG_MS]    → sonucu okunması gereken eylemler (ekran görüntüsü, çıkış onayı)
 *  - [HOLD_REFRESH_MS] → tuş basılı tutulurken göstergeyi canlı tutma aralığı
 *
 * Değerler Aniyomi'nin OSD zamanlaması ve Just Player'ın `MESSAGE_TIMEOUT_KEY`
 * (1 sn) / `MESSAGE_TIMEOUT` (2 sn) çiftiyle uyumludur.
 */
object PlayerOsdDurations {
    const val PULSE_MS = 900L
    const val NORMAL_MS = 1_600L
    const val LONG_MS = 2_600L
    const val HOLD_REFRESH_MS = 250L
}
