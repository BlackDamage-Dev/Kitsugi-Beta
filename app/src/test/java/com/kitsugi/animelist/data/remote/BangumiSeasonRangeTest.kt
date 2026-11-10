package com.kitsugi.animelist.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Bangumi'de ayrı bir **sezon** alanı yok; v0 `filter.air_date` yalnız tarih aralığı kabul eder.
 * Uygulama "Kış 2025" gibi bir facet seçildiğinde çeyrek aralığa çevirir.
 *
 * Beklenen değerler canlı `POST /v0/search/subjects` probuyla doğrulandı
 * (`probe/result.txt`, V1–V5 blokları): `>=2025-01-01` + `<2025-04-01` → 89 kayıt,
 * `>=2025-04-01` + `<=2025-06-30` → 98 kayıt, `>=2026-07-01` + `<2026-10-01` → 93 kayıt,
 * `>=2025-10-01` + `<2026-01-01` → 90 kayıt. Üst sınır **dışlayıcı** (`<`) de çalışıyor.
 */
class BangumiSeasonRangeTest {

    @Test
    fun `winter maps to first quarter`() {
        assertEquals("2025-01-01" to "2025-04-01", bangumiSeasonRange("WINTER", 2025))
    }

    @Test
    fun `spring maps to second quarter`() {
        assertEquals("2025-04-01" to "2025-07-01", bangumiSeasonRange("SPRING", 2025))
    }

    @Test
    fun `summer maps to third quarter`() {
        assertEquals("2026-07-01" to "2026-10-01", bangumiSeasonRange("SUMMER", 2026))
    }

    @Test
    fun `fall rolls over to the next year`() {
        assertEquals("2025-10-01" to "2026-01-01", bangumiSeasonRange("FALL", 2025))
    }

    @Test
    fun `autumn is accepted as an alias of fall`() {
        assertEquals("2024-10-01" to "2025-01-01", bangumiSeasonRange("autumn", 2024))
    }

    @Test
    fun `season code is case and whitespace insensitive`() {
        assertEquals("2025-04-01" to "2025-07-01", bangumiSeasonRange("  spring ", 2025))
    }

    @Test
    fun `missing season or year yields no range so the year filter keeps working`() {
        assertNull(bangumiSeasonRange(null, 2025))
        assertNull(bangumiSeasonRange("WINTER", null))
        // Geçersiz yıl (0 / negatif) aralık üretmemeli: aksi hâlde `0000-01-01` gönderilirdi.
        assertNull(bangumiSeasonRange("WINTER", 0))
    }

    @Test
    fun `unknown season code is ignored instead of guessing`() {
        assertNull(bangumiSeasonRange("MONSOON", 2025))
    }
}
