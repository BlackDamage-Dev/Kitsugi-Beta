package com.kitsugi.animelist.data.repository

import java.util.Locale

/**
 * Torrent swarm istatistikleri — **yalnızca kaynak gerçekten sayı verdiyse** dolar.
 *
 * NEREDEN GELİR?
 *  - Stremio eklentileri: Stremio stream protokolünde `seeders`/`peers` alanları standart
 *    DEĞİLDİR; Torrentio gibi eklentiler bu sayıları stream `title`/`description` metnine
 *    gömerek verir (ör. `👤 124 💾 1.7 GB ⚙ YTS` veya `👥 124 💾 1.7 GB`). Bu yüzden hem
 *    JSON alanı hem de metin ayrıştırması desteklenir.
 *  - CloudStream eklentileri: `ExtractorLink` swarm bilgisi taşımaz; bu durumda rozet
 *    basılmaz (uydurma sayı yazılmaz).
 *
 * KURAL: Sayı yoksa [hasAny] false olur ve arayüz hiçbir seed/peer rozeti göstermez.
 * Eskiden olduğu gibi "0 seeder" gibi uydurma bir değer gösterilmez.
 */
data class TorrentSwarmStats(
    val seeders: Int? = null,
    val leechers: Int? = null,
    /** Kaynağın bildirdiği kaynak/indeksleyici etiketi (Torrentio `⚙️ YTS` gibi). */
    val providerTag: String? = null
) {
    val hasAny: Boolean get() = seeders != null || leechers != null

    /** Sağlık puanı: seeder ağırlıklı (sıralama ve renk için). */
    val healthScore: Int
        get() = (seeders ?: 0) * 2 + (leechers ?: 0)

    companion object {
        val NONE = TorrentSwarmStats()

        // ── Metin biçimleri ──────────────────────────────────────────────────
        // Torrentio:  "👤 124 💾 1.7 GB ⚙ YTS"
        // Varyantlar: "👥 124", "🌱 124", "⬆️ 124", "S:124", "Seeds: 124 / Peers: 12",
        //             "124 seeds 12 peers", "124 seeders, 12 leechers"
        private val ICON_SEEDERS = Regex(
            """(?:👤|👥|🌱|⬆️|⬆|🔺|🧲)\s*([0-9][0-9.,]*\s*[kKmM]?)""",
            RegexOption.IGNORE_CASE
        )
        private val ICON_LEECHERS = Regex(
            """(?:📥|⬇️|⬇|🔻|💧)\s*([0-9][0-9.,]*\s*[kKmM]?)""",
            RegexOption.IGNORE_CASE
        )
        private val WORD_SEEDERS = Regex(
            """(?:^|[^a-zçğıöşü])(?:seeders?|seeds?|seedit|seed)\s*[:=]?\s*([0-9][0-9.,]*\s*[kKmM]?)""",
            RegexOption.IGNORE_CASE
        )
        private val WORD_LEECHERS = Regex(
            """(?:^|[^a-zçğıöşü])(?:leechers?|leechs?|peers?|peer)\s*[:=]?\s*([0-9][0-9.,]*\s*[kKmM]?)""",
            RegexOption.IGNORE_CASE
        )
        /** "S:124 P:12" / "S: 124 / L: 12" kısa biçimi. */
        private val SHORT_SEEDERS = Regex("""(?:^|[^a-z0-9])s\s*[:=]\s*([0-9][0-9.,]*\s*[kKmM]?)""", RegexOption.IGNORE_CASE)
        private val SHORT_LEECHERS = Regex("""(?:^|[^a-z0-9])[lp]\s*[:=]\s*([0-9][0-9.,]*\s*[kKmM]?)""", RegexOption.IGNORE_CASE)
        /** "124↑ 12↓" biçimi. */
        private val ARROW_SEEDERS = Regex("""([0-9][0-9.,]*\s*[kKmM]?)\s*(?:↑|⬆️|⬆)""")
        private val ARROW_LEECHERS = Regex("""([0-9][0-9.,]*\s*[kKmM]?)\s*(?:↓|⬇️|⬇)""")
        /** Torrentio `⚙️` / `⚙` kaynak etiketi. */
        private val PROVIDER_TAG = Regex("""(?:⚙️|⚙)\s*([A-Za-z0-9 ._\-]{2,24})""")

        /**
         * Metinden (stream title/description) swarm bilgisini okur. Hiçbir sayı yoksa
         * [NONE] döner — arayüz bu durumda rozet basmaz.
         */
        fun parse(text: String?): TorrentSwarmStats {
            if (text.isNullOrBlank()) return NONE
            val seeders = firstNumber(text, ICON_SEEDERS, WORD_SEEDERS, SHORT_SEEDERS, ARROW_SEEDERS)
            val leechers = firstNumber(text, ICON_LEECHERS, WORD_LEECHERS, SHORT_LEECHERS, ARROW_LEECHERS)
            val provider = PROVIDER_TAG.find(text)?.groupValues?.getOrNull(1)?.trim()
                ?.takeIf { it.isNotBlank() && !it.contains("seed", true) }
            if (seeders == null && leechers == null) return NONE
            return TorrentSwarmStats(
                seeders = seeders,
                leechers = leechers,
                providerTag = provider
            )
        }

        private fun firstNumber(text: String, vararg regexes: Regex): Int? {
            for (regex in regexes) {
                // Yalnızca ilk eşleşme değil, geçerli sayı üreten ilk eşleşme kullanılır.
                val match = regex.findAll(text)
                    .firstOrNull { it.groupValues.getOrNull(1)?.isNotBlank() == true }
                    ?: continue
                val value = parseCompactNumber(match.groupValues[1])
                if (value != null) return value
            }
            return null
        }

        /** "1.2K" → 1200, "3,4k" → 3400, "124" → 124. Anlamsız/çok büyük değerler null. */
        fun parseCompactNumber(raw: String?): Int? {
            val cleaned = raw?.trim()?.lowercase(Locale.ROOT) ?: return null
            if (cleaned.isEmpty()) return null
            val multiplier = when {
                cleaned.endsWith("k") -> 1_000.0
                cleaned.endsWith("m") -> 1_000_000.0
                else -> 1.0
            }
            val numberPart = cleaned.removeSuffix("k").removeSuffix("m").replace(",", ".")
            val value = numberPart.toDoubleOrNull() ?: return null
            val result = (value * multiplier).toLong()
            // 2 milyardan büyük "seeder" sayısı gerçekçi değildir → ayrıştırma hatası say.
            if (result < 0 || result > 2_000_000_000L) return null
            return result.toInt()
        }

        /** Arayüz için kısa sayı: 999 → "999", 1200 → "1.2K", 1_500_000 → "1.5M". */
        fun formatCompact(value: Int?): String? {
            val v = value ?: return null
            return when {
                v < 1_000 -> v.toString()
                v < 10_000 -> String.format(Locale.ROOT, "%.1fK", v / 1_000.0)
                v < 1_000_000 -> "${v / 1_000}K"
                v < 10_000_000 -> String.format(Locale.ROOT, "%.1fM", v / 1_000_000.0)
                else -> "${v / 1_000_000}M"
            }
        }
    }
}
