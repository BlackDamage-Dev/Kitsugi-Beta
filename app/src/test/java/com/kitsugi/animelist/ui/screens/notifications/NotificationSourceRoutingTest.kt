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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationSourceRoutingTest {

    @Test
    fun `notificationPageForSource maps known sources correctly`() {
        assertEquals(NOTIF_PAGE_ANILIST, notificationPageForSource("anilist"))
        assertEquals(NOTIF_PAGE_ANILIST, notificationPageForSource("AniList"))

        assertEquals(NOTIF_PAGE_MAL, notificationPageForSource("mal"))
        assertEquals(NOTIF_PAGE_MAL, notificationPageForSource("jikan"))
        assertEquals(NOTIF_PAGE_MAL, notificationPageForSource("MyAnimeList"))

        assertEquals(NOTIF_PAGE_TMDB_ACCOUNT, notificationPageForSource("tmdb"))
        assertEquals(NOTIF_PAGE_TMDB_ACCOUNT, notificationPageForSource("tmdb_account"))
        assertEquals(NOTIF_PAGE_TMDB_SIMKL, notificationPageForSource("simkl"))
        assertEquals(NOTIF_PAGE_TMDB_SIMKL, notificationPageForSource("tmdb_simkl"))

        assertEquals(NOTIF_PAGE_KITSU, notificationPageForSource("kitsu"))
        assertEquals(NOTIF_PAGE_SHIKIMORI, notificationPageForSource("shikimori"))
        assertEquals(NOTIF_PAGE_BANGUMI, notificationPageForSource("bangumi"))

        assertNull(notificationPageForSource(null))
        assertNull(notificationPageForSource("all"))
        assertNull(notificationPageForSource("unknown_provider"))
    }

    @Test
    fun `ExplorePlatform maps to expected notification source id`() {
        assertEquals("all", ExplorePlatform.ALL.notificationSourceId())
        assertEquals("jikan", ExplorePlatform.MAL.notificationSourceId())
        assertEquals("anilist", ExplorePlatform.AniList.notificationSourceId())
        assertEquals("tmdb", ExplorePlatform.TMDB.notificationSourceId())
        assertEquals("kitsu", ExplorePlatform.KITSU.notificationSourceId())
        assertEquals("shikimori", ExplorePlatform.SHIKIMORI.notificationSourceId())
        assertEquals("simkl", ExplorePlatform.SIMKL.notificationSourceId())
        assertEquals("bangumi", ExplorePlatform.BANGUMI.notificationSourceId())
    }

    @Test
    fun `ProfileTab index maps to expected notification source id`() {
        assertEquals("anilist", notificationSourceIdForProfileTab(0))
        assertEquals("mal", notificationSourceIdForProfileTab(1))
        assertEquals("simkl", notificationSourceIdForProfileTab(2))
        assertEquals("kitsu", notificationSourceIdForProfileTab(3))
        assertEquals("shikimori", notificationSourceIdForProfileTab(4))
        assertEquals("bangumi", notificationSourceIdForProfileTab(5))
        // Out of bounds falls back to anilist
        assertEquals("anilist", notificationSourceIdForProfileTab(99))
    }

    @Test
    fun `MyListTab index maps to expected notification source id`() {
        assertEquals("anilist", notificationSourceIdForMyListTab(MY_LIST_ANILIST_TAB_INDEX))
        assertEquals("mal", notificationSourceIdForMyListTab(MY_LIST_MAL_TAB_INDEX))
        assertEquals("simkl", notificationSourceIdForMyListTab(MY_LIST_SIMKL_TAB_INDEX))
        assertEquals("kitsu", notificationSourceIdForMyListTab(MY_LIST_KITSU_TAB_INDEX))
        assertEquals("shikimori", notificationSourceIdForMyListTab(MY_LIST_SHIKIMORI_TAB_INDEX))
        assertEquals("bangumi", notificationSourceIdForMyListTab(MY_LIST_BANGUMI_TAB_INDEX))
        assertEquals("tmdb", notificationSourceIdForMyListTab(MY_LIST_TMDB_TAB_INDEX))
        // ALL tab returns null (uses default fallback)
        assertNull(notificationSourceIdForMyListTab(MY_LIST_ALL_TAB_INDEX))
    }
}
