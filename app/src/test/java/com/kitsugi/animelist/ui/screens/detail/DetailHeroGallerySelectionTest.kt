package com.kitsugi.animelist.ui.screens.detail

import com.kitsugi.animelist.data.remote.GalleryCategory
import com.kitsugi.animelist.data.remote.GalleryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailHeroGallerySelectionTest {

    @Test
    fun `existing clicked backdrop opens at its own index`() {
        val poster = GalleryItem(
            url = "https://cdn.example/poster.jpg",
            source = "TMDB",
            category = GalleryCategory.POSTER
        )
        val backdrop = GalleryItem(
            url = "https://cdn.example/backdrop-123456789.jpg",
            source = "TMDB",
            category = GalleryCategory.BACKDROP
        )

        val selection = selectHeroArtworkInGallery(
            galleryItems = listOf(poster, backdrop),
            clickedUrl = backdrop.url,
            clickedCategory = GalleryCategory.BACKDROP,
            source = "TMDB"
        )

        assertEquals(listOf(poster, backdrop), selection.items)
        assertEquals(1, selection.index)
    }

    @Test
    fun `missing hero artwork is inserted and opened immediately`() {
        val existing = GalleryItem(
            url = "https://cdn.example/existing.jpg",
            source = "Jikan",
            category = GalleryCategory.POSTER
        )
        val clickedUrl = "https://cdn.example/selected-wide-artwork.jpg"

        val selection = selectHeroArtworkInGallery(
            galleryItems = listOf(existing),
            clickedUrl = clickedUrl,
            clickedCategory = GalleryCategory.BACKDROP,
            source = "Fanart.tv"
        )

        assertEquals(0, selection.index)
        assertEquals(clickedUrl, selection.items.first().url)
        assertEquals(GalleryCategory.BACKDROP, selection.items.first().category)
        assertEquals("Fanart.tv", selection.items.first().source)
        assertEquals(existing, selection.items[1])
    }

    @Test
    fun `tmdb size variants resolve to the same gallery item`() {
        val galleryItem = GalleryItem(
            url = "https://image.tmdb.org/t/p/w500/abc123456789.jpg?language=tr",
            source = "TMDB",
            category = GalleryCategory.POSTER
        )

        val selection = selectHeroArtworkInGallery(
            galleryItems = listOf(galleryItem),
            clickedUrl = "https://image.tmdb.org/t/p/original/abc123456789.jpg",
            clickedCategory = GalleryCategory.POSTER,
            source = "TMDB"
        )

        assertEquals(1, selection.items.size)
        assertEquals(0, selection.index)
        assertTrue(heroArtworkUrlsMatch(galleryItem.url, "https://image.tmdb.org/t/p/original/abc123456789.jpg"))
    }

    @Test
    fun `generic CDN file names do not create a false match`() {
        val first = "https://cdn-one.example/poster_images/1/large.jpg"
        val second = "https://cdn-two.example/poster_images/2/large.jpg"

        assertEquals(false, heroArtworkUrlsMatch(first, second))
    }

    @Test
    fun `landscape mode always prefers poster artwork`() {
        val preference = resolveHeroArtworkPreference(screenWidthDp = 800, screenHeightDp = 400)
        assertEquals(HeroArtworkPreference.POSTER, preference)
    }

    @Test
    fun `portrait phones prefer backdrop artwork regardless of aspect ratio`() {
        // Keşfet/Listem tutarlılığı: dikey telefonda (oran ne olursa olsun)
        // yatay arka plan gösterilir; eski <=0.75 eşiği her telefonda postere
        // düşürüp Keşfet detayında yanlışlıkla tam ekran poster gösteriyordu.
        val modernPhone = resolveHeroArtworkPreference(screenWidthDp = 390, screenHeightDp = 844)
        assertEquals(HeroArtworkPreference.BACKDROP, modernPhone)

        val ultraTallPhone = resolveHeroArtworkPreference(screenWidthDp = 360, screenHeightDp = 800)
        assertEquals(HeroArtworkPreference.BACKDROP, ultraTallPhone)

        val standardPhone = resolveHeroArtworkPreference(screenWidthDp = 360, screenHeightDp = 640)
        assertEquals(HeroArtworkPreference.BACKDROP, standardPhone)
    }

    @Test
    fun `portrait tablets and square devices prefer backdrop artwork`() {
        val squareDevice = resolveHeroArtworkPreference(screenWidthDp = 800, screenHeightDp = 900)
        assertEquals(HeroArtworkPreference.BACKDROP, squareDevice)

        val wideTablet = resolveHeroArtworkPreference(screenWidthDp = 600, screenHeightDp = 700)
        assertEquals(HeroArtworkPreference.BACKDROP, wideTablet)

        // Kare ekran (w == h) yatay sayılmaz -> arka plan.
        val exactSquare = resolveHeroArtworkPreference(screenWidthDp = 800, screenHeightDp = 800)
        assertEquals(HeroArtworkPreference.BACKDROP, exactSquare)
    }
}
