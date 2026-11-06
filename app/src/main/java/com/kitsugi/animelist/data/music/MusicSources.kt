package com.kitsugi.animelist.data.music

/**
 * 🔌 Uzak Ses Kaynakları — Tanımlar
 *
 * Her kaynağın API anahtarı gereksinimi, lisans notu ve edinme adresi.
 * Tüm kaynaklar **kişisel anahtar (BYOK)** modeliyle çalışır: uygulama
 * hiçbir ortak anahtar taşımaz, dolayısıyla hiçbir lisans sözleşmesini
 * ihlal etmez ve hiçbir kotayı tüketmez.
 */
object MusicSources {

    val all: List<MusicSourceDef> = listOf(

        MusicSourceDef(
            id = MusicSourceId.BUILTIN,
            displayName = "Yerleşik Motor",
            requiresApiKey = false,
            keyless = true,
            nonCommercialOnly = false,
            keyHint = "Anahtar gerekmez — cihaz içinde üretilir",
            notes = "Prosedürel ses sentezi. İnternet, hesap veya kota gerekmez. Telif riski yoktur."
        ),

        MusicSourceDef(
            id = MusicSourceId.JAMENDO,
            displayName = "Jamendo",
            requiresClientId = true,
            keyless = false,
            nonCommercialOnly = true,
            keyHint = "devportal.jamendo.com → Create App → Client ID",
            signupUrl = "https://devportal.jamendo.com/signup",
            notes = "Kişisel anahtarınla ayda 35.000 istek. Parçaları yalnızca AKIŞ olarak dinletebilirsin; " +
                "indirme/önbelleğe alma API şartlarına aykırıdır. Sanatçı ve Jamendo atfı zorunludur."
        ),

        MusicSourceDef(
            id = MusicSourceId.FREESOUND,
            displayName = "Freesound",
            requiresApiKey = true,
            keyless = false,
            nonCommercialOnly = true,
            keyHint = "freesound.org/apiv2/apply → API key",
            signupUrl = "https://freesound.org/apiv2/apply/",
            notes = "Ortam kayıtları ve doğa sesleri. Kişisel anahtarınla dakikada 60, günde 2.000 istek. " +
                "Her sesin kendi CC lisansı vardır; atıf bilgisi otomatik gösterilir."
        ),

        MusicSourceDef(
            id = MusicSourceId.OPENVERSE,
            displayName = "Openverse",
            requiresApiKey = false,
            keyless = true,
            optionalKey = true,
            nonCommercialOnly = false,
            keyHint = "Anahtar gerekmez — OAuth2 client_id girilirse limit 500 kat artar",
            signupUrl = "https://api.openverse.org/v1/auth_tokens/register/",
            notes = "CC0 ve CC-BY lisanslı açık ses arşivi. Anahtarsız dakikada 20, günde 200 istek. " +
                "OAuth2 uygulaması kaydedip client_id girilirse limit dakikada 100, günde 10.000'e çıkar."
        ),

        MusicSourceDef(
            id = MusicSourceId.ARCHIVE,
            displayName = "Internet Archive",
            requiresApiKey = false,
            keyless = true,
            nonCommercialOnly = false,
            keyHint = "Anahtar gerekmez",
            signupUrl = "",
            notes = "Kamu malı (public domain) kayıtlar. Anahtar gerekmez, kota pratikte yoktur. " +
                "Yalnızca public domain lisanslı öğeler listelenir."
        ),

        MusicSourceDef(
            id = MusicSourceId.AUDIUS,
            displayName = "Audius",
            requiresApiKey = true,
            // Anahtarsız da çalışır (app_name yeterli); anahtar yalnızca limit artırır
            keyless = true,
            optionalKey = true,
            nonCommercialOnly = false,
            keyHint = "audius.co/settings → API Keys (anahtarsız da denenebilir)",
            signupUrl = "https://audius.co/settings", // → Developer Apps bölümü
            notes = "Merkeziyetsiz müzik platformu. Anahtar opsiyoneldir; kayıtla daha yüksek limit alırsın. " +
                "İçerik kullanıcı yüklemesidir, parça bazlı lisans kontrolü önerilir."
        )
    )

    /** Anahtar gerektiren kaynaklar (Ayarlar ekranında kart olarak gösterilir). */
    val keyRequired: List<MusicSourceDef> = all.filter { !it.keyless }

    /** Anahtarsız çalışan kaynaklar. */
    val keyless: List<MusicSourceDef> = all.filter { it.keyless }

    fun byId(id: MusicSourceId): MusicSourceDef? = all.firstOrNull { it.id == id }

    /** Adsız erişim için güvenli varsayılan. */
    val fallback: MusicSourceDef = all.first()
}


