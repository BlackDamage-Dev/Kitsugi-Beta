package com.kitsugi.animelist.ui.screens.notifications

import com.kitsugi.animelist.ui.screens.explore.ExplorePlatform
import com.kitsugi.animelist.ui.screens.mylist.MY_LIST_ALL_TAB_INDEX
import com.kitsugi.animelist.ui.screens.mylist.MY_LIST_ANILIST_TAB_INDEX
import com.kitsugi.animelist.ui.screens.mylist.MY_LIST_BANGUMI_TAB_INDEX
import com.kitsugi.animelist.ui.screens.mylist.MY_LIST_KITSU_TAB_INDEX
import com.kitsugi.animelist.ui.screens.mylist.MY_LIST_MAL_TAB_INDEX
import com.kitsugi.animelist.ui.screens.mylist.MY_LIST_SHIKIMORI_TAB_INDEX
import com.kitsugi.animelist.ui.screens.mylist.MY_LIST_SIMKL_TAB_INDEX
import com.kitsugi.animelist.ui.screens.mylist.MY_LIST_TMDB_TAB_INDEX
import com.kitsugi.animelist.ui.screens.profile.ProfilePlatform

/**
 * Bildirim sayfasındaki yatay sayfalayıcı (HorizontalPager) sekme indeksleri.
 * Sıra [KitsugiNotificationsScreen] içindeki sayfa sırasıyla birebir aynı olmak zorundadır:
 * AniList → MAL → TMDB & Simkl → Kitsu → Shikimori → Bangumi
 */
const val NOTIF_PAGE_ANILIST = 0
const val NOTIF_PAGE_MAL = 1
const val NOTIF_PAGE_TMDB_SIMKL = 2
const val NOTIF_PAGE_KITSU = 3
const val NOTIF_PAGE_SHIKIMORI = 4
const val NOTIF_PAGE_BANGUMI = 5
const val NOTIF_PAGE_TMDB_ACCOUNT = 6

/**
 * Verilen kaynak kimliğini bildirim sayfasındaki sekme indeksine çevirir.
 *
 * Dönen değer `null` ise kaynak ya "Tümü" ya da bildirim sekmesi bulunmayan bir
 * kaynaktır (ör. eklentiler) — çağıran taraf varsayılan (bağlı ilk hesap) seçime düşer.
 */
fun notificationPageForSource(source: String?): Int? = when (source?.trim()?.lowercase()) {
    "anilist" -> NOTIF_PAGE_ANILIST
    "mal", "jikan", "myanimelist" -> NOTIF_PAGE_MAL
    "simkl", "tmdb_simkl" -> NOTIF_PAGE_TMDB_SIMKL
    "kitsu" -> NOTIF_PAGE_KITSU
    "shikimori" -> NOTIF_PAGE_SHIKIMORI
    "bangumi" -> NOTIF_PAGE_BANGUMI
    "tmdb", "tmdb_account" -> NOTIF_PAGE_TMDB_ACCOUNT
    else -> null
}

/** Keşfet sekmesindeki seçili kaynak → bildirim sayfası kaynak kimliği. */
fun ExplorePlatform.notificationSourceId(): String = when (this) {
    ExplorePlatform.ALL -> "all"
    ExplorePlatform.MAL -> "jikan"
    ExplorePlatform.AniList -> "anilist"
    ExplorePlatform.TMDB -> "tmdb"
    ExplorePlatform.KITSU -> "kitsu"
    ExplorePlatform.SHIKIMORI -> "shikimori"
    ExplorePlatform.SIMKL -> "simkl"
    ExplorePlatform.BANGUMI -> "bangumi"
}

/**
 * Profil sekmesindeki seçili kaynak sekmesi (0..5) → bildirim sayfası kaynak kimliği.
 * Sıra [ProfilePlatform] enum sırasıyla aynıdır: AniList, MAL, Simkl, Kitsu, Shikimori, Bangumi.
 */
fun notificationSourceIdForProfileTab(subTabIndex: Int): String =
    ProfilePlatform.entries.getOrNull(subTabIndex)?.id ?: "anilist"

/**
 * Listem sekmesindeki seçili kaynak sekmesi → bildirim sayfası kaynak kimliği.
 * "Tümü" (birleşik liste) seçiliyse `null` döner: birleşik listenin tek bir bildirim
 * kaynağı yoktur, bu durumda varsayılan seçim uygulanır.
 */
fun notificationSourceIdForMyListTab(tabIndex: Int): String? = when (tabIndex) {
    MY_LIST_ANILIST_TAB_INDEX -> "anilist"
    MY_LIST_MAL_TAB_INDEX -> "mal"
    MY_LIST_SIMKL_TAB_INDEX -> "simkl"
    MY_LIST_KITSU_TAB_INDEX -> "kitsu"
    MY_LIST_SHIKIMORI_TAB_INDEX -> "shikimori"
    MY_LIST_BANGUMI_TAB_INDEX -> "bangumi"
    MY_LIST_TMDB_TAB_INDEX -> "tmdb"
    MY_LIST_ALL_TAB_INDEX -> null
    else -> null
}
