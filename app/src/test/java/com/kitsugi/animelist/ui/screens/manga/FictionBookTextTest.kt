package com.kitsugi.animelist.ui.screens.manga

import org.junit.Assert.*
import org.junit.Test

class FictionBookTextTest {
    @Test fun extractsOrderedBodyAndIgnoresMetadataAndBinary() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?><FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0"><description><title-info><book-title>Metadata</book-title></title-info></description><body><section><title><p>Chapter</p></title><p>Hello <emphasis>world</emphasis>!</p><empty-line/><poem><stanza><v>First line</v><v>Second line</v></stanza></poem></section></body><binary id="x">ignored</binary></FictionBook>"""
        assertEquals(listOf("Chapter", "Hello world!", "", "First line", "Second line"), parseFictionBookText(xml.toByteArray()))
    }
    @Test fun supportsDeclaredEncodingAndNamespaces() {
        val xml = """<?xml version="1.0" encoding="windows-1251"?><f:FictionBook xmlns:f="x"><f:body><f:p>Привет</f:p></f:body></f:FictionBook>"""
        assertEquals(listOf("Привет"), parseFictionBookText(xml.toByteArray(charset("windows-1251"))))
    }
    @Test fun rejectsNonBooksAndOversizedInput() {
        assertThrows(IllegalArgumentException::class.java) { parseFictionBookText("<other/>".toByteArray()) }
        assertThrows(IllegalArgumentException::class.java) { parseFictionBookText(ByteArray(5 * 1024 * 1024 + 1)) }
        assertThrows(IllegalArgumentException::class.java) { parseFictionBookText("<FictionBook><body/></FictionBook>".toByteArray()) }
    }
}
