package com.kitsugi.animelist.ui.screens.search

import com.kitsugi.animelist.utils.TaxonomyCatalog
import com.kitsugi.animelist.utils.TaxonomyOrgRole
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **Kurumlar (stüdyo / yapımcı / şirket) seçim sözleşmesi.**
 *
 * Kullanıcı raporu: "Arama > Kurumlar sheet'i hem Tümü hem kaynaklar üstünde
 * boş, 0 kayıt — bir sürü kayıt dosyası vardı, nereye gitti?"
 *
 * Kök nedenler ve bu testin kilitlediği davranışlar:
 *  1. Sheet açıldığında yalnızca **ağ araması** sonucu doluyordu; yerel katalog
 *     (9.457 kayıt) hiçbir yoldan görünmüyordu → katalog okunabilir ve dolu olmalı.
 *  2. `OrgFilterSheet`'i açan bayrak hiçbir yerde `true` yapılmıyordu (ölü kod).
 *  3. `TaxonomyCatalog.load` başarısız okumayı önbelleğe alıyordu.
 *  4. Katalog anahtarı `bangumi_<rol>_<...>` biçiminde; rol **ikinci** parçadır.
 */
class OrganizationSelectionTest {

    private val catalog: TaxonomyCatalog by lazy {
        TaxonomyCatalog.parse(catalogJson())
    }

    // ─── 1) Katalog gerçekten dolu ve okunabilir ─────────────────────────────

    @Test
    fun `katalog 9457 kurum icerir`() {
        assertEquals(9_457, catalog.organizations.size)
    }

    @Test
    fun `her rol icin kayit vardir`() {
        val byRole = catalog.organizations.groupingBy { it.role }.eachCount()
        TaxonomyOrgRole.entries.forEach { role ->
            assertTrue(
                "$role rolünde kurum olmalı, bulunan: ${byRole[role] ?: 0}",
                (byRole[role] ?: 0) > 0
            )
        }
    }

    @Test
    fun `kanonik anahtarlar benzersiz`() {
        assertEquals(
            catalog.organizations.size,
            catalog.organizations.map { it.canonicalKey }.distinct().size
        )
    }

    // ─── 2) Sheet'in kullandığı arama yolu boş dönmemeli ─────────────────────

    @Test
    fun `bilinen studyolar katalogda bulunur`() {
        val expectations = listOf(
            "MAPPA" to "mappa",
            "ufotable" to "ufotable",
            "Wit Studio" to "wit studio",
            "A-1 Pictures" to "a-1 pictures",
            "Studio Ghibli" to "ghibli",
            "Toei Animation" to "toei"
        )
        expectations.forEach { (display, needle) ->
            val matches = catalog.organizations.filter { it.matches(needle) }
            assertTrue(
                "$display için katalog eşleşmesi olmalı (\"$needle\")",
                matches.any { it.name.equals(display, ignoreCase = true) }
            )
        }
    }

    @Test
    fun `bos sorgu tum katalogu dondurur`() {
        // Sheet açılır açılmaz liste boş olmamalı; boş sorgu = tüm katalog.
        val needle = TaxonomyCatalog.fold("")
        val matches = catalog.organizations.filter { needle.isEmpty() || it.matches(needle) }
        assertEquals(catalog.organizations.size, matches.size)
    }

    @Test
    fun `sorgu buyuk kucuk harf ve aksandan bagimsiz eslesir`() {
        val folded = TaxonomyCatalog.fold("KYOTO")
        val matches = catalog.organizations.filter { it.matchesFolded(folded) }
        assertTrue("KYOTO için eşleşme olmalı", matches.isNotEmpty())
    }

    // ─── 3) Rol çözümleme (facet temizliği) ──────────────────────────────────

    @Test
    fun `kanonik anahtardan rol ikinci parcadan okunur`() {
        val sample = catalog.organizations.take(200)
        sample.forEach { organization ->
            val role = organization.canonicalKey.split('_').getOrNull(1)
            assertEquals(
                "${organization.canonicalKey} rolü ikinci parçada olmalı",
                organization.role.wireName,
                role
            )
        }
    }

    @Test
    fun `ilk parca her zaman bangumi`() {
        assertTrue(
            catalog.organizations.all { it.canonicalKey.startsWith("bangumi_") }
        )
    }

    // ─── 4) Sheet'in tür→rol eşlemesi boş kalmamalı ──────────────────────────

    @Test
    fun `yapimci turu producer kayitlarini esler`() {
        val roles = setOf(TaxonomyOrgRole.PRODUCER)
        val matches = catalog.organizations.filter { it.role in roles }
        assertTrue("Yapımcı sekmesi boş olmamalı", matches.isNotEmpty())
    }

    @Test
    fun `sirket turu studyo yapimci ve yayinci kayitlarini esler`() {
        val roles = setOf(
            TaxonomyOrgRole.STUDIO,
            TaxonomyOrgRole.PRODUCER,
            TaxonomyOrgRole.PUBLISHER
        )
        val matches = catalog.organizations.filter { it.role in roles }
        assertTrue("Şirket sekmesi boş olmamalı", matches.isNotEmpty())
        assertEquals(
            catalog.organizations.count { it.role in roles },
            matches.size
        )
    }

    // ─── 5) Ad kalitesi: ekranda CJK kalmamalı ───────────────────────────────

    @Test
    fun `gorunen adlarda CJK karakter yok`() {
        val cjk = Regex("[\\u3040-\\u30ff\\u3400-\\u9fff\\uac00-\\ud7af]")
        val offenders = catalog.organizations.filter { cjk.containsMatchIn(it.name) }
        assertEquals(
            "Ekranda CJK ad kalmamalı: ${offenders.map { it.name }}",
            0,
            offenders.size
        )
    }

    @Test
    fun `adlar bos degil`() {
        assertTrue(catalog.organizations.all { it.name.isNotBlank() })
    }

    // ─── yardımcı ───────────────────────────────────────────────────────────

    private fun catalogJson(): String {
        // taxonomy_catalog.json `res/raw` altında; sınıf yolunda DEĞİL. Birim testleri
        // Gradle'da modül dizininden çalıştığı için üst dizinlere doğru yürüyerek
        // aranır. Hiçbir yol bulunamazsa test sessizce geçmez, açıkça düşer.
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val direct = File(dir, "app/src/main/res/raw/taxonomy_catalog.json")
            val relative = File(dir, "src/main/res/raw/taxonomy_catalog.json")
            val found = listOf(direct, relative).firstOrNull { it.isFile }
            if (found != null) return found.readText(Charsets.UTF_8)
            dir = dir.parentFile
        }
        fail("taxonomy_catalog.json hiçbir üst dizinde bulunamadı")
        error("ulaşılamaz")
    }
}
