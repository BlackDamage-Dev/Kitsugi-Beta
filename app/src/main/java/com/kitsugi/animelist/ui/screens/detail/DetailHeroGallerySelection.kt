package com.kitsugi.animelist.ui.screens.detail

import com.kitsugi.animelist.data.remote.GalleryCategory
import com.kitsugi.animelist.data.remote.GalleryItem
import java.util.Locale
import kotlin.math.max

/**
 * Ekran yönüne göre detay vitrininin hangi görseli birincil göstereceğine
 * karar veren sonuç.
 *
 * Tek kural (Keşfet ve Listem detay sayfaları için ORTAK):
 *  - Yatay (landscape) modda dikey poster tercih edilir; poster dikey-uzun-dar
 *    bir kutuya yerleştirilir.
 *  - Dikey (portrait) modda — ekranın en-boy oranı ne olursa olsun — yatay
 *    arka plan (backdrop/banner) tercih edilir; arka plan geniş-kısa bir
 *    bantta gösterilir.
 *
 * Eski kural dikey modda "uzun-dar telefon (oran <= 0.75) -> poster" diyordu.
 * Güncel tüm telefonlar ~0.43-0.50 oranında olduğu için Keşfet detay sayfası
 * dikeyde HER ZAMAN tam ekran poster gösteriyor, Listem detay sayfası ise
 * (sadece yöne bakan kuralıyla) aynı yapımda doğru yatay arka planı
 * gösteriyordu. İki sayfanın farklı resim göstermesinin kök nedeni buydu.
 * Tercih edilen türde aday yoksa vitrin diğer türe düşer; kutu oranı da
 * gösterilen gerçek görsele göre ayarlanır.
 */
internal enum class HeroArtworkPreference {
    /** Dikey poster (uzun-dar kutu). */
    POSTER,
    /** Yatay arka plan / banner (geniş-kısa bant). */
    BACKDROP
}

/**
 * Ekran yönüne göre vitrinin birincil görsel tercihini döndürür.
 *
 * Yön, genişlik/yükseklik karşılaştırmasıyla belirlenir; en-boy oranına
 * dayalı ek eşik KULLANILMAZ — dikey telefonda oran eşiği her zaman poster
 * tarafına düşüp yatay arka planı hiç göstermiyordu.
 *
 * @param screenWidthDp Ekranın mevcut genişliği (dp).
 * @param screenHeightDp Ekranın mevcut yüksekliği (dp).
 */
internal fun resolveHeroArtworkPreference(
    screenWidthDp: Int,
    screenHeightDp: Int
): HeroArtworkPreference {
    val w = max(1, screenWidthDp)
    val h = max(1, screenHeightDp)
    return if (w > h) HeroArtworkPreference.POSTER else HeroArtworkPreference.BACKDROP
}

/** Galeride açılacak liste ile, bu listedeki başlangıç konumunu birlikte taşır. */
internal data class HeroGallerySelection(
    val items: List<GalleryItem>,
    val index: Int
)

/**
 * Detay vitrininin gösterdiği gerçek görseli galeriye bağlar.
 *
 * Vitrin URL'yi kalite için dönüştürmüş olabilir (ör. TMDB `w500` → `original`,
 * Kitsu `medium` → `large`). Önce bu varyantları aynı görsel olarak eşleriz. Görsel
 * galeride henüz yoksa — galeri hâlâ yükleniyor olsa bile — tıklanan URL'yi listenin
 * başına ekleriz. Böylece galeri her koşulda kullanıcının dokunduğu görselle açılır.
 */
internal fun selectHeroArtworkInGallery(
    galleryItems: List<GalleryItem>,
    clickedUrl: String,
    clickedCategory: GalleryCategory,
    source: String
): HeroGallerySelection {
    val cleanClickedUrl = clickedUrl.trim()
    if (cleanClickedUrl.isEmpty()) {
        return HeroGallerySelection(galleryItems, 0)
    }

    val existingIndex = galleryItems.indexOfFirst { item ->
        heroArtworkUrlsMatch(item.url, cleanClickedUrl)
    }
    if (existingIndex >= 0) {
        return HeroGallerySelection(galleryItems, existingIndex)
    }

    val clickedItem = GalleryItem(
        url = cleanClickedUrl,
        source = source,
        category = clickedCategory
    )
    return HeroGallerySelection(listOf(clickedItem) + galleryItems, 0)
}

/** URL boyut/CDN varyantlarını aynı galeri görseli olarak tanır. */
internal fun heroArtworkUrlsMatch(first: String, second: String): Boolean {
    val firstIdentity = canonicalHeroArtworkUrl(first)
    val secondIdentity = canonicalHeroArtworkUrl(second)
    if (firstIdentity.isEmpty() || secondIdentity.isEmpty()) return false
    if (firstIdentity == secondIdentity) return true

    // Bazı CDN'ler aynı dosyayı farklı dizinlerden verir. Yalnızca yeterince ayırt
    // edici dosya adlarında bu son çareyi kullan; `large.jpg` gibi genel adlar eşleşmez.
    val firstFileName = firstIdentity.substringAfterLast('/')
    val secondFileName = secondIdentity.substringAfterLast('/')
    val firstStem = firstFileName.substringBeforeLast('.')
    return firstFileName == secondFileName && firstStem.length >= 12
}

private fun canonicalHeroArtworkUrl(url: String): String {
    return url.trim()
        .substringBefore('#')
        .substringBefore('?')
        .lowercase(Locale.ROOT)
        .removePrefix("https://")
        .removePrefix("http://")
        // TMDB kalite varyantları
        .replace(
            Regex("image\\.tmdb\\.org/t/p/(?:original|w\\d+|h\\d+)/"),
            "image.tmdb.org/t/p/"
        )
        // Bangumi kapak kalite varyantları
        .replace(
            Regex("lain\\.bgm\\.tv/r/\\d+/pic/cover/[a-z]/"),
            "lain.bgm.tv/pic/cover/"
        )
        .replace(
            Regex("lain\\.bgm\\.tv/pic/cover/[a-z]/"),
            "lain.bgm.tv/pic/cover/"
        )
        // Kitsu poster/cover kalite varyantları
        .replace(
            Regex("(/poster_images/\\d+/)(?:tiny|small|medium|large)(\\.[a-z0-9]+)$"),
            "$1{size}$2"
        )
        .replace(
            Regex("(/cover_images/\\d+/)(?:tiny|small|medium|large|original)(\\.[a-z0-9]+)$"),
            "$1{size}$2"
        )
        // AniList, MAL ve Shikimori kalite varyantları
        .replace(Regex("/cover/(?:small|medium|large)/"), "/cover/{size}/")
        .replace(Regex("myanimelist\\.net/r/\\d+x\\d+/images/"), "myanimelist.net/images/")
        .replace(Regex("/(?:preview|x96|x48|original)/"), "/{size}/")
        .trimEnd('/')
}
