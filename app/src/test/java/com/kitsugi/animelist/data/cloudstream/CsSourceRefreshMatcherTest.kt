package com.kitsugi.animelist.data.cloudstream

import com.kitsugi.animelist.data.repository.StreamSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CsSourceRefreshMatcherTest {
    private fun source(url: String, name: String = "Anizm • Adonis • SendVid") = StreamSource(
        addonName = "Anizm", name = name, title = name, url = url,
        infoHash = null, fileIndex = null, isCS = true
    )

    @Test
    fun `refreshes signed query but preserves which identically named upload was selected`() {
        val old = source("https://sendvid.example/a.mp4?token=old")
        val other = source("https://sendvid.example/b.mp4?token=old")
        val newOther = source("https://sendvid.example/b.mp4?token=new")
        val fresh = source("https://sendvid.example/a.mp4?token=new")
        assertEquals(fresh, CsSourceRefreshMatcher.match(old, listOf(old, other), listOf(newOther, fresh)))
    }

    @Test
    fun `ambiguous names are not replaced by a random upload`() {
        val old = source("https://old.example/a.mp4")
        val other = source("https://old.example/b.mp4")
        val unrelated = source("https://new.example/another.mp4")
        assertNull(CsSourceRefreshMatcher.match(old, listOf(old, other), listOf(unrelated)))
    }

    @Test
    fun `drive file id is retained when confirmation token changes`() {
        val old = source("https://drive.google.com/uc?id=FILE1&confirm=old")
        val other = source("https://drive.google.com/uc?id=FILE2&confirm=old")
        val fresh = source("https://drive.google.com/uc?id=FILE1&confirm=new")
        val changedOther = source("https://drive.google.com/uc?id=FILE2&confirm=new")
        assertEquals(fresh, CsSourceRefreshMatcher.match(old, listOf(old, other), listOf(changedOther, fresh)))
        assertNull(CsSourceRefreshMatcher.match(old, listOf(old), listOf(changedOther)))
    }

    @Test
    fun `unique label can move to a new cdn`() {
        val old = source("https://old.example/video")
        val fresh = source("https://new.example/new-path")
        assertEquals(fresh, CsSourceRefreshMatcher.match(old, listOf(old), listOf(fresh)))
    }
}
