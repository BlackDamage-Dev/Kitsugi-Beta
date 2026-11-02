package com.kitsugi.animelist.data.cloudstream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CsDriveConfirmationTest {
    private val page = "https://drive.google.com/uc?id=FILE123"

    @Test
    fun `confirmation form retains existing query and hidden fields`() {
        val html = """<form action='https://drive.usercontent.google.com/download?existing=1'>
            <input value='FILE123' name='id'><input name='confirm' value='t'>
            <input value='a+b/=' name='uuid'>
        </form>"""
        assertEquals(
            "https://drive.usercontent.google.com/download?existing=1&id=FILE123&confirm=t&uuid=a%2Bb%2F%3D",
            CsDriveConfirmation.resolve(html, page)
        )
    }

    @Test
    fun `explicit link retains confirmation token`() {
        val html = """<a href="https://drive.google.com/uc?id=A&amp;confirm=token&amp;uuid=B">Download</a>"""
        assertEquals("https://drive.google.com/uc?id=A&confirm=token&uuid=B", CsDriveConfirmation.resolve(html, page))
    }

    @Test
    fun `quota and foreign or post forms are not guessed as downloads`() {
        assertNull(CsDriveConfirmation.resolve("<html>Download quota exceeded</html>", page))
        assertNull(CsDriveConfirmation.resolve("<form action='https://evil.example/download'><input name='id' value='A'></form>", page))
        assertNull(CsDriveConfirmation.resolve("<form method='POST' action='https://drive.google.com/uc'><input name='id' value='A'></form>", page))
        assertNull(CsDriveConfirmation.resolve("<form action='http://drive.google.com/uc'><input name='id' value='A'></form>", page))
    }
}
