package com.kitsugi.animelist.core.player

import com.google.gson.Gson
import com.kitsugi.animelist.data.model.AnimeDownload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadSubtitleIdentityTest {
    private val gson = Gson()
    private val base = AnimeDownload(
        animeId = "bgm_400602", animeTitle = "Sousou no Frieren", posterUrl = null,
        episode = 1, season = 0, url = "https://example.test/video.mkv", quality = "HD"
    )

    @Test fun newDownloadsPreserveMovieAndBangumiIdentity() {
        val original = base.copy(isMovie = false, bangumiId = 400602)
        val restored = gson.fromJson(gson.toJson(original), AnimeDownload::class.java)
        assertEquals(false, restored.isMovie)
        assertEquals(400602, restored.bangumiId)
        val queries = SubtitleQueryPlanner.build(restored.isMovie == true, "tt12345", null, null, restored.season, restored.episode)
        assertEquals(SubtitleQueryPlanner.Query("series", "tt12345:0:1"), queries.single())
    }

    @Test fun legacyDownloadDoesNotPretendToKnowMediaType() {
        val legacy = gson.toJson(base)
        val restored = gson.fromJson(legacy, AnimeDownload::class.java)
        assertNull(restored.isMovie)
        assertNull(restored.bangumiId)
    }
}
