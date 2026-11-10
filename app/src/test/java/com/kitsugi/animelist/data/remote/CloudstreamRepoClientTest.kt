package com.kitsugi.animelist.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Birleşik eklenti listesinde tekilleştirme davranışı.
 *
 * Sorun: Türkçe havuzda 130+ eklenti birden fazla depoda listeleniyor ve kayıtlar
 * farklı sürüm/URL taşıyor. Tekilleştirme olmadan portal aynı eklentiyi iki kez
 * gösteriyordu ve kullanıcı eski/kırık kopyayı kurup "eklenti veri getirmiyor"
 * hatası alıyordu (örnek: gömülü listede RecTV v1, güncel depoda v98).
 */
class CloudstreamRepoClientTest {

    private val client = CloudstreamRepoClient(context = null)

    private fun plugin(
        name: String,
        version: Int,
        url: String,
        repositoryUrl: String? = null
    ) = CsPlugin(
        name = name,
        internalName = name,
        url = url,
        description = null,
        version = version,
        language = "tr",
        tvTypes = null,
        iconUrl = null,
        authors = emptyList(),
        repositoryUrl = repositoryUrl
    )

    @Test
    fun `ayni eklentinin iki kaydindan yuksek surum kazanir`() {
        val stale = plugin("RecTV", 1, "https://codeberg.org/BlackDamage/KitsugiPlugins/raw/branch/builds/RecTV.cs3")
        val fresh = plugin("RecTV", 98, "https://raw.githubusercontent.com/BlackDamage-Plugin/Kitsugi-Video-Plugins/builds/RecTV.cs3")

        val result = client.dedupePlugins(listOf(stale, fresh))

        assertEquals("Tek kayit kalmali", 1, result.size)
        assertEquals("Guncel surum kazanmali", 98, result[0].version)
        assertTrue("Guncel URL korunmali", result[0].url.contains("BlackDamage-Plugin"))
    }

    @Test
    fun `esit surumde ilk gelen kayit korunur`() {
        val primary = plugin("AnimeciX", 21, "https://BlackDamage-Plugin/Kitsugi-Video-Plugins/builds/AnimeciX.cs3")
        val secondary = plugin("AnimeciX", 21, "https://codeberg.org/BlackDamage/KitsugiPlugins/raw/branch/builds/AnimeciX.cs3")

        val result = client.dedupePlugins(listOf(primary, secondary))

        assertEquals(1, result.size)
        assertEquals("pluginLists sirasindaki birincil kaynak kazanmali", primary.url, result[0].url)
    }

    @Test
    fun `Turkce karakter ve buyuk harf farklari ayni eklenti sayilir`() {
        val pairs = listOf(
            "WFilmİzle" to "WFilmizle",
            "Kanal 7" to "Kanal7",
            "Dizikorea" to "DiziKorea",
            "CizgiveDizi" to "CizgiVeDizi",
            "CanliTV" to "CanliTv"
        )
        for ((a, b) in pairs) {
            val result = client.dedupePlugins(
                listOf(
                    plugin(a, 1, "https://example.invalid/$a.cs3"),
                    plugin(b, 9, "https://example.invalid/$b.cs3")
                )
            )
            assertEquals("'$a' ve '$b' ayni eklenti olmali", 1, result.size)
            assertEquals("Yuksek surum kazanmali ($a/$b)", 9, result[0].version)
        }
    }

    @Test
    fun `farkli eklentiler tekillestirilmez`() {
        val list = listOf(
            plugin("DiziPal", 5, "https://example.invalid/DiziPal.cs3"),
            plugin("DiziPalOriginal", 3, "https://example.invalid/DiziPalOriginal.cs3"),
            plugin("AnimeWorld", 5, "https://example.invalid/AnimeWorld.cs3"),
            plugin("AsyaAnimeleri", 1, "https://example.invalid/AsyaAnimeleri.cs3")
        )

        val result = client.dedupePlugins(list)

        assertEquals("Dordunun tamami korunmali", 4, result.size)
    }

    @Test
    fun `bos ve tekrarlayan kimlikler guvenle islenir`() {
        val result = client.dedupePlugins(
            listOf(
                plugin("", 1, "https://example.invalid/empty.cs3"),
                plugin("   ", 1, "https://example.invalid/blank.cs3"),
                plugin("RecTV", 1, "https://example.invalid/RecTV.cs3")
            )
        )
        assertEquals("Kimlik uretilemeyen kayitlar atlanmali", 1, result.size)
        assertEquals("RecTV", result[0].internalName)
    }
}
