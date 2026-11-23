package com.kitsugi.animelist.ui.screens.explore.mobile

import com.kitsugi.animelist.DetailScreen
import com.kitsugi.animelist.ui.app.AppNavigationState
import com.kitsugi.animelist.ui.app.FullScreenMediaGridState

/**
 * Telefondaki "Tümünü Gör" davranışı.
 *
 * Eski sürümde telefon, raf ne olursa olsun **sonlu** (finite) bir ızgara açıyordu:
 * yalnızca keşfet sayfasında zaten yüklenmiş öğeler gösteriliyor, kaydırınca yeni veri
 * gelmiyordu. Bu yüzden aynı "Tümünü Gör" sayfası tablette/TV'de sonsuz kaydırırken
 * telefonda ilk 20 öğede duruyordu.
 *
 * Yeni kural — kaynak ne olursa olsun aynı davranış:
 *  - Raf temiz bir katalog rafıysa ([MobileExploreShelfData.requiresSpecialContinuation]
 *    `false`) → gerçek sayfalanan ızgara açılır ([FullScreenMediaGridState.mobileFiniteResults]
 *    `false`); MAL, TMDB, AniList, Kitsu, Shikimori, Simkl ve Bangumi için sunucu sayfaları
 *    kaydırdıkça çekilir.
 *  - Raf karışık/kişisel/türetilmiş ise (birden çok sekme, kaynak değişimi, Simkl listesi,
 *    birleşik novel rafı) eski sayfalama sorgusu **yanlış** içerik getireceği için sonlu
 *    ızgara korunur; başlığında da yalnız yüklenen öğelerin gösterildiği belirtilir.
 */
fun AppNavigationState.openMobileExploreShelf(shelf: MobileExploreShelfData, title: String): Boolean {
    when (mobileShelfAction(shelf)) {
        MobileShelfAction.NONE -> return false
        MobileShelfAction.WATCH_HISTORY -> navigateToDetail(DetailScreen.WatchHistory)
        MobileShelfAction.FINITE_GRID_REQUIRES_UI -> {
            fullScreenGridState = if (shelf.canPaginateAsCatalogShelf) {
                mobilePaginatedGridState(shelf, title)
            } else {
                mobileFiniteGridState(shelf, title)
            }
        }
    }
    return true
}

/**
 * Rafın, kaynağın gerçek sayfalama sorgusuna birebir karşılık gelip gelmediği.
 * Karışık raflarda (ör. "Trend Her Şey" = film + dizi, ALL birleşimi) sayfalanan sorgu
 * rafın içeriğinden farklı öğeler getireceği için bu raflar sonlu kalır.
 */
internal val MobileExploreShelfData.canPaginateAsCatalogShelf: Boolean
    get() = !requiresSpecialContinuation && source == originPlatform

/** Sayfalanan sürüm: kaydırdıkça kaynaktan yeni sayfalar çekilir (tüm platformlar). */
fun mobilePaginatedGridState(shelf: MobileExploreShelfData, title: String): FullScreenMediaGridState =
    FullScreenMediaGridState(
        title = title,
        categoryType = shelf.category,
        platform = shelf.source,
        initialResults = shelf.results.toList(),
        // false → FullScreenMediaGridPage loadNextPage() ile sunucudan devam eder.
        mobileFiniteResults = false,
    )

/** Sonlu sürüm: yalnızca hazır sonuçlar; başlıkta bu durum açıkça belirtilir. */
fun mobileFiniteGridState(shelf: MobileExploreShelfData, title: String): FullScreenMediaGridState {
    require(mobileShelfAction(shelf) == MobileShelfAction.FINITE_GRID_REQUIRES_UI)
    return FullScreenMediaGridState(
        title = title,
        categoryType = shelf.category,
        platform = shelf.source,
        initialResults = shelf.results.toList(),
        mobileFiniteResults = true,
    )
}
