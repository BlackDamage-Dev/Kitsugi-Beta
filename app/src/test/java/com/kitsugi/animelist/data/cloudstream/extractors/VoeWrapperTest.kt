package com.kitsugi.animelist.data.cloudstream.extractors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoeWrapperTest {

    @Test
    fun decodesVoeF7ApplicationJsonAndPreservesExactEmbedHeaders() {
        // F7 payload produced from the JSON below using the same reversible
        // page transformation used by Voe's public player script.
        val encoded =
            "DQAkGUjmsI1mHGMGZ0EoMKt7Jy1ho3OarIuXMKb0Jyj3JKOCrSH1KUx7oSW9EHgaZ2gjGQyZEzI8JGMosISoKJ1EJykcIGMpo01oMT5AEx9fFSqGpJ81MwWdI2q9Z3OCrIyXKKAqAx9ir0czq0yXnT84oTIhHGICrKW9MacIF2qlGJkFoSt1KUkMAzI9GKkb"
        val html = """
            <script type="application/json">["$encoded"]</script>
        """.trimIndent()

        val result = VoeWrapper.extractFromHtml(html, "https://voe.sx/e/episode-123")

        assertEquals(2, result.size)
        assertTrue(result.any { it.url == "https://cdn.example/video.m3u8?sig=1" && it.isM3u8 })
        assertTrue(result.any { it.url == "https://cdn.example/video.mp4?sig=2" && !it.isM3u8 })
        result.forEach {
            assertEquals("https://voe.sx/e/episode-123", it.headers["Referer"])
            assertEquals("https://voe.sx", it.headers["Origin"])
        }
    }

    @Test
    fun acceptsVoeHostAliasesWithoutTreatingLookalikesAsVoe() {
        assertTrue(VoeWrapper.isVoeUrl("https://voe.sx/e/abc"))
        assertTrue(VoeWrapper.isVoeUrl("https://voeload.com/e/abc"))
        assertTrue(VoeWrapper.isVoeUrl("https://tubelessceliolymph.com/e/abc"))
        assertTrue(VoeWrapper.isVoeUrl("https://crystaltreatmenteast.com/e/abc"))
        assertTrue(VoeWrapper.isVoeUrl("https://ralphysuccessfull.org/e/abc"))
        assertTrue(VoeWrapper.isVoeUrl("https://cdn.voe.sx/e/abc"))
        assertTrue(!VoeWrapper.isVoeUrl("https://voe.sx.example/e/abc"))
    }
}
