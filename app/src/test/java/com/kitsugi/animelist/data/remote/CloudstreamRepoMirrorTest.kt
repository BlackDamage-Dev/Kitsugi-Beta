package com.kitsugi.animelist.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Depo manifesti / eklenti listesi çekimindeki ayna (mirror) zinciri davranışı.
 *
 * Sorun (2026-10, kullanıcı bildirimi "uygulama eklenti güncellemelerini görmüyor"):
 * resmi depo URL'si `BlackDamage-Plugin/Kitsugi-Video-Plugins/builds/...` biçiminde TİRE içeriyor,
 * ancak yedek kaynak kontrolleri yalnızca `KitsugiPlugins`/`codeberg.org` arıyordu.
 * Bu yüzden `raw.githubusercontent.com` engellendiğinde (Türkiye'de sık görülür)
 * sıradaki yedekler atlanıyor ve uygulama doğrudan APK içindeki BAYAT listeye düşüyordu;
 * bayat liste de "güncelleme yok" diyordu.
 */
class CloudstreamRepoMirrorTest {

    private val client = CloudstreamRepoClient(context = null)

    @Test
    fun `tireli Kitsugi deposu da resmi depo sayilir`() {
        assertTrue(client.isKitsugiRepoUrl("https://raw.githubusercontent.com/BlackDamage-Plugin/Kitsugi-Video-Plugins/builds/repo.json"))
        assertTrue(client.isKitsugiRepoUrl("https://codeberg.org/BlackDamage/KitsugiPlugins/raw/branch/builds/plugins.json"))
        assertFalse(client.isKitsugiRepoUrl("https://raw.githubusercontent.com/Kraptor123/cs-kraptor/builds/plugins.json"))
    }

    @Test
    fun `github ham icerik adresi api muadiline cevrilir`() {
        val api = client.githubApiContentUrl("https://raw.githubusercontent.com/BlackDamage-Plugin/Kitsugi-Video-Plugins/builds/repo.json")
        assertEquals(
            "https://api.github.com/repos/BlackDamage-Plugin/Kitsugi-Video-Plugins/contents/repo.json?ref=builds",
            api
        )
    }

    @Test
    fun `api adresi github disi kaynaklarda uretilmez`() {
        assertNull(client.githubApiContentUrl("https://codeberg.org/BlackDamage/KitsugiPlugins/raw/branch/builds/repo.json"))
        assertNull(client.githubApiContentUrl("https://cdn.jsdelivr.net/gh/BlackDamage-Plugin/Kitsugi-Video-Plugins@builds/repo.json"))
    }

    @Test
    fun `ayna zinciri birincil api jsdelivr ve codeberg sirasini korur`() {
        val chain = client.mirrorChainFor("https://raw.githubusercontent.com/BlackDamage-Plugin/Kitsugi-Video-Plugins/builds/plugins.json")

        assertEquals("Birincil URL ilk sırada olmalı", 0, chain.indexOfFirst { it.endsWith("builds/plugins.json") && it.startsWith("https://raw.githubusercontent.com/") })
        assertTrue("api.github.com muadili zincirde olmalı", chain.any { it.startsWith("https://api.github.com/repos/") })
        assertTrue("jsDelivr aynası zincirde olmalı", chain.any { it.startsWith("https://cdn.jsdelivr.net/gh/") })
        assertTrue("Codeberg yedeği zincirde olmalı", chain.any { it.startsWith("https://codeberg.org/") })
    }

    @Test
    fun `github disi depo icin api ve codeberg aynasi eklenmez`() {
        val chain = client.mirrorChainFor("https://raw.githubusercontent.com/Kraptor123/cs-kraptor/builds/plugins.json")

        assertTrue("Kraptor için api muadili eklenir", chain.any { it.startsWith("https://api.github.com/repos/Kraptor123/") })
        assertFalse("Kitsugi olmayan depo için Codeberg yedeği eklenmez", chain.any { it.contains("codeberg.org") })
    }
}
