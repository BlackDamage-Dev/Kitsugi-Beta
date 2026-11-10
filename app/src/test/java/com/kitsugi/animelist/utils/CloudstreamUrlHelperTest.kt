package com.kitsugi.animelist.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Türkçe eklenti havuzunda yaşanan "eklenti indirilemiyor / boş dönüyor" hatalarının
 * kök nedeni dosya adı uyuşmazlıklarıydı. Bu testler o davranışı sabitler.
 *
 * Gerçek olaylar (Kitsugi-Plugins `builds` dalı, 2026-10):
 *  • `internalName = WFilmİzle`  ↔ depodaki dosya `WFilmizle.cs3`  → 404
 *  • `internalName = CanliTV`    ↔ depodaki dosya `CanliTv.cs3`    → 404
 *  • `internalName = DDizi`      ↔ depodaki dosya `Ddizi.cs3`      → 404
 *  • `internalName = Kanal 7`    ↔ depodaki dosya `Kanal7.cs3`     → 404
 *  • `internalName = CizgiveDizi` ↔ depodaki dosya `CizgiVeDizi.cs3` → 404
 */
class CloudstreamUrlHelperTest {

    // ─── normalizeUrl: eski havuz → güncel havuz ─────────────────────────────

    @Test
    fun `Codeberg havuzu Kitsugi-Plugins deposuna cevrilir`() {
        val codeberg = "https://codeberg.org/BlackDamage/KitsugiPlugins/raw/branch/builds/RecTV.cs3"
        val normalized = CloudstreamUrlHelper.normalizeUrl(codeberg)
        assertEquals(
            "https://raw.githubusercontent.com/BlackDamage-Plugin/Kitsugi-Video-Plugins/builds/RecTV.cs3",
            normalized
        )
    }

    @Test
    fun `Eski ad kayitlari yeni dosya adina cevrilir`() {
        val cases = mapOf(
            "CizgiveDizi" to "CizgiVeDizi",
            "Dizikorea" to "DiziKorea",
            "SineWix" to "Sinewix",
            "Kanal 7" to "Kanal7"
        )
        for ((old, new) in cases) {
            val url = "https://codeberg.org/BlackDamage/KitsugiPlugins/raw/branch/builds/$old.cs3"
            val normalized = CloudstreamUrlHelper.normalizeUrl(url)
            assertTrue(
                "Beklenen dosya adi '$new.cs3' ama gelen: $normalized",
                normalized.endsWith("/$new.cs3")
            )
        }
    }

    @Test
    fun `Kekik eski adresleri guncel forka yonlendirilir`() {
        val legacy = "https://raw.githubusercontent.com/maarrem/cs-Kekik/master/repo.json"
        val normalized = CloudstreamUrlHelper.normalizeUrl(legacy)
        assertTrue(
            "Eski Kekik deposu feroxx forkuna yonlendirilmeli, gelen: $normalized",
            normalized.contains("feroxx/Kekik-cloudstream")
        )
    }

    // ─── Dosya adı varyantları ───────────────────────────────────────────────

    @Test
    fun `Turkce karakterli adlarin ASCII varyanti uretilir`() {
        val variants = CloudstreamUrlHelper.cs3NameVariants("WFilmİzle")
        assertTrue("WFilmizle varyanti uretilmeli: $variants", variants.contains("WFilmizle"))
        assertTrue("kucuk harf varyanti uretilmeli: $variants", variants.contains("wfilmizle"))
    }

    @Test
    fun `Ardisik buyuk harfli adlarin baslik bicimi varyanti uretilir`() {
        assertTrue(CloudstreamUrlHelper.cs3NameVariants("CanliTV").contains("CanliTv"))
        assertTrue(CloudstreamUrlHelper.cs3NameVariants("DDizi").contains("Ddizi"))
    }

    @Test
    fun `Bosluk iceren adlarin bosluksuz varyanti uretilir`() {
        val variants = CloudstreamUrlHelper.cs3NameVariants("Kanal 7")
        assertTrue("Kanal7 varyanti uretilmeli: $variants", variants.contains("Kanal7"))
        assertTrue("kanal7 varyanti uretilmeli: $variants", variants.contains("kanal7"))
    }

    @Test
    fun `Fold ASCII Turkce harfleri dogru indirger`() {
        assertEquals("CinayetSefi", CloudstreamUrlHelper.foldToAscii("CinayetŞefi"))
        assertEquals("GolgeTV", CloudstreamUrlHelper.foldToAscii("GölgeTV"))
        assertEquals("CizgiVeDizi", CloudstreamUrlHelper.foldToAscii("ÇizgiVeDizi"))
        assertEquals("", CloudstreamUrlHelper.foldToAscii("\u0307"))
    }

    @Test
    fun `Kimlik anahtari ayni eklentinin farkli yazimlarini esitler`() {
        val key = CloudstreamUrlHelper::pluginIdentityKey
        assertEquals(key("WFilmİzle"), key("WFilmizle"))
        assertEquals(key("Kanal 7"), key("Kanal7"))
        assertEquals(key("Dizikorea"), key("DiziKorea"))
        assertEquals(key("CizgiveDizi"), key("CizgiVeDizi"))
        assertFalse(key("DiziPal") == key("DiziPalOriginal"))
    }

    // ─── İndirme adayları ────────────────────────────────────────────────────

    @Test
    fun `official current Anizm build is tried before stale prebuilt fallback`() {
        val url = "https://raw.githubusercontent.com/BlackDamage-Plugin/Kitsugi-Video-Plugins/builds/Anizm.cs3"
        val candidates = CloudstreamUrlHelper.getCandidateDownloadUrls("Anizm", url)
        val currentApi = candidates.indexOf("https://api.github.com/repos/BlackDamage-Plugin/Kitsugi-Video-Plugins/contents/Anizm.cs3?ref=builds")
        val oldPrebuilt = candidates.indexOf("https://raw.githubusercontent.com/BlackDamage-Plugin/Kitsugi-Video-Plugins/builds/prebuilt/Anizm.cs3")
        assertTrue(currentApi >= 0 && oldPrebuilt > currentApi)
    }

    @Test
    fun `Indirme adaylarinda varyant ve Codeberg kurtarma yedegi bulunur`() {
        val url = "https://codeberg.org/BlackDamage/KitsugiPlugins/raw/branch/builds/WFilmİzle.cs3"
        val candidates = CloudstreamUrlHelper.getCandidateDownloadUrls("WFilmİzle", url)

        assertTrue(
            "Orijinal Kitsugi-Plugins adresi adaylarda olmali: $candidates",
            candidates.any { it.contains("BlackDamage-Plugin/Kitsugi-Video-Plugins") && it.endsWith("WFilmizle.cs3") || it.endsWith("WFilm\u0130zle.cs3") }
        )
        assertTrue(
            "Codeberg kurtarma yedegi adaylarda olmali: $candidates",
            candidates.any { it.startsWith("https://codeberg.org/BlackDamage/KitsugiPlugins/") }
        )
        assertTrue(
            "Bos aday uretilmemeli",
            candidates.none { it.isBlank() }
        )
        assertEquals("Aday listesi tekrarsiz olmali", candidates.size, candidates.toSet().size)
        // Varyant dosya adları da denenmeli
        assertTrue(
            "WFilmizle (ASCII) varyanti denenmeli: $candidates",
            candidates.any { it.contains("WFilmizle.cs3") }
        )
    }

    @Test
    fun `Kraptor deposu icin plugin listesi yolu uretilir`() {
        val url = "https://raw.githubusercontent.com/Kraptor123/cs-kraptor/master/repo.json"
        val normalized = CloudstreamUrlHelper.normalizeUrl(url)
        assertTrue(
            "cs-kraptor repo.json yayinlamiyor, plugins.json kullanilmali: $normalized",
            normalized.endsWith("builds/plugins.json")
        )
    }
}
