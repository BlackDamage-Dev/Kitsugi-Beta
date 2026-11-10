package com.kitsugi.animelist.data.manga

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpdsCatalogClientTest {

    private val client = OpdsCatalogClient()

    @Test
    fun parsesAtomFeedWithBothNavigationAndAcquisitionEntries() = runBlocking {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom">
                <title>Test Calibre Library</title>
                <link rel="search" href="http://example.com/opensearch.xml" type="application/opensearchdescription+xml"/>
                <link rel="next" href="http://example.com/page2.atom"/>
                
                <!-- Navigation Entry -->
                <entry>
                    <title>All Books by Author</title>
                    <summary>Browse books sorted by author name</summary>
                    <link rel="subsection" href="/authors.atom" type="application/atom+xml;profile=opds-catalog"/>
                </entry>
                
                <!-- Acquisition Entry -->
                <entry>
                    <title>Sample Light Novel</title>
                    <author><name>Novel Author</name></author>
                    <summary>A fantasy light novel.</summary>
                    <link rel="http://opds-spec.org/acquisition" href="/download/sample.epub" type="application/epub+zip"/>
                    <link rel="http://opds-spec.org/image" href="/covers/sample.jpg" type="image/jpeg"/>
                </entry>
            </feed>
        """.trimIndent()

        // We test feed parser via internal methods or mock response
        val parserMethod = OpdsCatalogClient::class.java.getDeclaredMethod("parseAtomFeed", String::class.java, String::class.java)
        parserMethod.isAccessible = true
        val feed = parserMethod.invoke(client, xml, "http://example.com/catalog.atom") as OpdsFeed

        assertEquals("Test Calibre Library", feed.title)
        assertEquals("http://example.com/opensearch.xml", feed.searchTemplateUrl)
        assertEquals("http://example.com/page2.atom", feed.nextPageUrl)

        // 1 Navigation item
        assertEquals(1, feed.navigationItems.size)
        assertEquals("All Books by Author", feed.navigationItems[0].title)
        assertEquals("http://example.com/authors.atom", feed.navigationItems[0].url)

        // 1 Acquisition item
        assertEquals(1, feed.acquisitions.size)
        assertEquals("Sample Light Novel", feed.acquisitions[0].title)
        assertEquals("Novel Author", feed.acquisitions[0].author)
        assertEquals("http://example.com/download/sample.epub", feed.acquisitions[0].url)
        assertEquals("application/epub+zip", feed.acquisitions[0].mimeType)
        assertEquals("http://example.com/covers/sample.jpg", feed.acquisitions[0].coverUrl)
    }

    @Test
    fun parsesOpds2JsonFeed() {
        val json = """
            {
                "metadata": { "title": "OPDS 2 Sample Feed" },
                "links": [
                    { "rel": "next", "href": "http://example.com/next.json" },
                    { "rel": "search", "href": "http://example.com/search{?query}" }
                ],
                "navigation": [
                    { "title": "Popular", "href": "http://example.com/popular.json" }
                ],
                "publications": [
                    {
                        "metadata": {
                            "title": "Comic Book One",
                            "author": "Comic Artist",
                            "description": "Episode 1"
                        },
                        "images": [ { "href": "http://example.com/comic1.jpg" } ],
                        "links": [
                            { "rel": "http://opds-spec.org/acquisition", "href": "http://example.com/comic1.cbz", "type": "application/vnd.comicbook+zip" }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val parserMethod = OpdsCatalogClient::class.java.getDeclaredMethod("parseJsonFeed", String::class.java, String::class.java)
        parserMethod.isAccessible = true
        val feed = parserMethod.invoke(client, json, "http://example.com/feed.json") as OpdsFeed

        assertEquals("OPDS 2 Sample Feed", feed.title)
        assertEquals("http://example.com/next.json", feed.nextPageUrl)
        assertEquals(1, feed.navigationItems.size)
        assertEquals("Popular", feed.navigationItems[0].title)

        assertEquals(1, feed.acquisitions.size)
        assertEquals("Comic Book One", feed.acquisitions[0].title)
        assertEquals("Comic Artist", feed.acquisitions[0].author)
        assertEquals("http://example.com/comic1.cbz", feed.acquisitions[0].url)
    }
}
