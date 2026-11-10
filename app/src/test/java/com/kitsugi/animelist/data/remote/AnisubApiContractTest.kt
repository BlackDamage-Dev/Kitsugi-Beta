package com.kitsugi.animelist.data.remote

import com.google.gson.Gson
import com.kitsugi.animelist.core.player.AnisubMatching
import org.junit.Assert.*
import org.junit.Test

class AnisubApiContractTest {
    @Test fun parsesPublicApiShapeAndFiltersOtherSeasons() {
        val json = """{"success":true,"data":{"data":[
            {"subtitle_id":3606,"subtitle_release_name":"Sokudo","subtitle_language":"Türkçe",
             "subtitle_anilist_id":127720,"content_type":"Paket","episode_description":"1-12",
             "media_info":{"media_anilist_id":127720,"title_romaji":"Mushoku Tensei Part 2"}},
            {"subtitle_id":3605,"subtitle_release_name":"Sokudo","subtitle_language":"Türkçe",
             "subtitle_anilist_id":108465,"content_type":"Paket","episode_description":"1-11",
             "media_info":{"media_anilist_id":108465,"title_romaji":"Mushoku Tensei"}}
          ],"meta":{"current_page":1,"last_page":1}}}"""
        val response = Gson().fromJson(json, AnisubSearchResponse::class.java)
        assertEquals(true, response.success)
        assertEquals(1, response.data?.meta?.last_page)
        val matches = response.data!!.data!!.filter {
            AnisubMatching.matchesMedia(108465, it.mediaInfo?.media_anilist_id, "Mushoku Tensei", listOf(it.mediaInfo?.title_romaji)) &&
                AnisubMatching.episodeMatches(it.content_type, it.episode_description, 2) != false
        }
        assertEquals(listOf(3605L), matches.map { it.subtitle_id })
    }

    @Test fun parsesRealStremioReleaseMetadata() {
        val json = """{"subtitles":[{"id":"72857","url":"https://example.test/file/95005","lang":"tur","subtitleFileName":"Shawshank Redemption.srt","movieReleaseName":"The Shawshank Redemption"},{"id":"x","url":"https://example.test/x","lang":"tur","label":"S01E01 • DVD-Rip"}]}"""
        val items = Gson().fromJson(json, SubtitleResponseWrapper::class.java).subtitles!!
        assertEquals("Shawshank Redemption.srt", items[0].subtitleFileName)
        assertEquals("S01E01 • DVD-Rip", items[1].label)
    }
}
