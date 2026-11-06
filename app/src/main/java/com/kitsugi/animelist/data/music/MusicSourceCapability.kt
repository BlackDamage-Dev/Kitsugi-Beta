package com.kitsugi.animelist.data.music

/**
 * 📊 Kaynak Kapasite Karşılaştırması
 *
 * Kullanıcının sorusu: **"Hangi kaynak en çok ve en güzel ses sunuyor?"**
 *
 * Cevap "en iyi kaynak" değil — **her kaynağın güçlü olduğu yer farklı.**
 * Bu tablo, panelde kaynak seçerken ne bekleneceğini söyler.
 *
 * Ölçüm temeli: her kaynağın API'sinin gerçek katalog büyüklüğü ve
 * bizim ona erişim biçimimiz (arama derinliği, sayfa boyutu, lisans
 * filtresi). Sayılar yaklaşıktır ve zamanla değişir; oranlar sabittir.
 */
object MusicSourceCapability {

    /**
     * Bir kaynağın karakteri.
     *
     * @param catalogSize    Yaklaşık katalog büyüklüğü (kullanıcıya "binlerce" gibi)
     * @param variety        Çeşitlilik 0..1 — ne kadar farklı türde ses var
     * @param quality        Ses kalitesi 0..1 — kayıt kalitesi ve tutarlılığı
     * @param reliability    Erişilebilirlik 0..1 — ne sıklıkla çalışır
     * @param bestFor        Ne için en iyi
     * @param weakness       Zayıf noktası (dürüst olmak şart)
     */
    data class Capability(
        val sourceId: MusicSourceId,
        val catalogSize: String,
        val variety: Float,
        val quality: Float,
        val reliability: Float,
        val bestFor: String,
        val weakness: String,
        /** Ortalama bir aramada dönen sonuç sayısı (bizim ayarlarımızla). */
        val resultsPerSearch: Int
    )

    /**
     * Genel puan — ağırlıklı ortalama.
     *
     * Ağırlıklar kullanıcı deneyimine göre seçildi:
     *  - Çeşitlilik %35  (her kitleye hitap için en önemli)
     *  - Kalite     %30  ("güzel ses" isteği)
     *  - Erişim     %35  (çalışmayan kaynak işe yaramaz)
     */
    fun score(cap: Capability): Float =
        cap.variety * 0.35f + cap.quality * 0.30f + cap.reliability * 0.35f

    /** Tüm kaynaklar, puana göre sıralı. */
    val ranked: List<Capability> = listOf(
        Capability(
            sourceId = MusicSourceId.ARCHIVE,
            catalogSize = "milyonlarca kayıt",
            variety = 0.65f,
            quality = 0.45f,
            reliability = 0.95f,
            bestFor = "Eskimiş kayıtlar, caz, klasik, saha kayıtları. Kota yok.",
            weakness = "Kalite tutarsız — amatör kayıtlar çok. Araması ham.",
            resultsPerSearch = 20
        ),
        Capability(
            sourceId = MusicSourceId.OPENVERSE,
            catalogSize = "~120.000 ses",
            variety = 0.85f,
            quality = 0.75f,
            reliability = 0.85f,
            bestFor = "Çeşitlilik ve lisans güvenliği. Anahtarsız çalışır.",
            weakness = "Dakikada 20 istek sınırı. Çok spesifik aramada boş döner.",
            resultsPerSearch = 20
        ),
        Capability(
            sourceId = MusicSourceId.JAMENDO,
            catalogSize = "~600.000 parça",
            variety = 0.80f,
            quality = 0.90f,
            reliability = 0.80f,
            bestFor = "**Müzik dinlemek** için en iyisi. Profesyonel kayıtlar.",
            weakness = "Doğa/ambiyans sesi YOK. Yalnızca müzik.",
            resultsPerSearch = 20
        ),
        Capability(
            sourceId = MusicSourceId.FREESOUND,
            catalogSize = "~600.000 kayıt",
            variety = 0.95f,
            quality = 0.80f,
            reliability = 0.70f,
            bestFor = "**Doğa ve ambiyans** için en iyisi. Gerçek saha kayıtları.",
            weakness = "Çoğu NC lisanslı (ticari kullanım yok). Atıf zorunlu.",
            resultsPerSearch = 15
        ),
        Capability(
            sourceId = MusicSourceId.AUDIUS,
            catalogSize = "~1.000.000 parça",
            variety = 0.70f,
            quality = 0.70f,
            reliability = 0.75f,
            bestFor = "Bağımsız/elektronik müzik. Anahtarsız da çalışır.",
            weakness = "Kullanıcı yüklemesi — kalite değişken. Türkçe içerik az.",
            resultsPerSearch = 20
        ),
        Capability(
            sourceId = MusicSourceId.BUILTIN,
            catalogSize = "29 ses (sınırsız döngü)",
            variety = 0.55f,
            quality = 0.85f,
            reliability = 1.00f,
            bestFor = "**Kesintisiz, telifsiz, çevrimdışı.** Okuma için en güvenli.",
            weakness = "Sınırlı çeşit. Gerçek kayıt değil, sentez.",
            resultsPerSearch = 29
        )
    )

    fun of(sourceId: MusicSourceId): Capability? = ranked.firstOrNull { it.sourceId == sourceId }

    /** Puanı yıldıza çevirir (gösterim için). */
    fun stars(cap: Capability): String {
        val s = (score(cap) * 5f).coerceIn(0f, 5f)
        val full = s.toInt()
        val half = s - full >= 0.5f
        return "★".repeat(full) + if (half) "½" else ""
    }

    /** Kullanıcıya tek satırlık özet. */
    fun summary(cap: Capability): String = when (cap.sourceId) {
        MusicSourceId.FREESOUND -> "Doğa/ambiyans için en zengin"
        MusicSourceId.JAMENDO -> "Müzik kalitesi en yüksek"
        MusicSourceId.OPENVERSE -> "En dengeli, anahtarsız"
        MusicSourceId.ARCHIVE -> "En büyük arşiv, en değişken kalite"
        MusicSourceId.AUDIUS -> "Bağımsız müzik, çok parça"
        MusicSourceId.BUILTIN -> "En güvenilir, çevrimdışı"
    }
}
