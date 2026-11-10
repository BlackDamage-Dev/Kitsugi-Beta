package com.kitsugi.animelist.core.player

/**
 * "Kalite" sayfasında gösterilen **tek** seçilebilir kalite.
 *
 * İki farklı uygulama biçimi vardır ve ikisi de bu modelle temsil edilir:
 *
 *  1. [switchUrl] dolu → HLS master playlist'teki gerçek bir varyant (medya playlist URL'si).
 *     Seçildiğinde oynatıcı **o URL** ile aynı saniyeden devam eder (Media3 ve MPV'de çalışır).
 *  2. [switchUrl] boş → akışın kendisi tek bir URL (DASH `.mpd` gibi) ve kalite seçimi
 *     oynatıcı motorunun **çözünürlük tavanı** ile yapılır ([PlayerEngineCapabilities]).
 *
 * Etiket her zaman ölçülen/okunan değerden üretilir; bilinmeyen kalite için uydurma
 * etiket basılmaz.
 */
data class QualityOption(
    val label: String,
    val height: Int? = null,
    val bandwidth: Long? = null,
    /** Dolu ise doğrudan oynatılabilir varyant URL'si; boş ise motor tavanı uygulanır. */
    val switchUrl: String? = null,
    /**
     * Kalite **kaynaktan** geldi (master/DASH ayrıştırılamadı, eklenti kaliteyi bildirdi).
     * Arayüzde "tek kalite" olarak gösterilir; seçim yapılamaz.
     */
    val declaredBySource: Boolean = false,
) {
    /**
     * Bu seçenek gerçekten uygulanabilir mi?
     *
     * - [declaredBySource] → yalnızca bilgi (kaynak kalite bildirmiş, seçilecek alternatif yok)
     * - DASH (switchUrl yok) → en az bir yükseklik olmalı ki motor tavanı uygulanabilsin
     */
    val isSelectable: Boolean
        get() = !declaredBySource && (switchUrl != null || (height != null && height > 0))

    /** Aynı kalite iki kez listelenmesin diye kullanılan anahtar. */
    val key: String
        get() = "${height ?: -1}|${bandwidth ?: 0L}|${label}|$declaredBySource"

    /** "4,5 Mbps" — yalnızca ölçüm varsa. */
    val bandwidthText: String?
        get() = bandwidth?.takeIf { it > 0 }?.let { HlsVariant.formatBandwidth(it) }

    companion object {
        /**
         * HLS master varyantından seçenek üretir.
         *
         * Ayrı ses grubu olan varyantlarda ([HlsVariant.requiresMasterAudio]) URL
         * **değiştirilmez** (ses kaybı olur); seçim motorun çözünürlük tavanıyla uygulanır.
         */
        fun fromHls(variant: HlsVariant): QualityOption = QualityOption(
            label = variant.label,
            height = variant.height,
            bandwidth = variant.bandwidth,
            switchUrl = variant.url.takeIf { variant.canSwitchUrl },
        )
    }
}
