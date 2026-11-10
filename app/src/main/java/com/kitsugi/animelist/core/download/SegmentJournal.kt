package com.kitsugi.animelist.core.download

/**
 * T-IND-2 (2026-11-09): HLS indirme için ÇÖKMEYE DAYANIKLI segment defteri (journal).
 *
 * NEDEN GEREKLİ (kasma/donma kök nedeni):
 * İndirme yarıda koptuğunda (429 / DNS / uygulama kapanması) devam ettirme, modelde tutulan
 * `downloadedSegments` sayacına güveniyordu. Bu sayaç UI için kısıklaştırılmış (throttle)
 * ilerleme bildirimlerinden beslenip yalnızca durum değişikliklerinde diske yazıldığından
 * DOSYADAKİ GERÇEK İÇERİKLE UYUŞMUYORDU. Devam ettirmede uyuşmazlık varsa zaten yazılmış
 * segmentler DOSYAYA TEKRAR ekleniyordu: MPEG-TS (seg-N-v1-a1.ts) akışında yinelenen dilimler
 * zaman damgası geri sarması yaratır → video her oynatıcıda (MX Player dahil) "kasıp donar".
 *
 * ÇÖZÜM: her segment dosyaya yazılıp FLUSH edildikten hemen sonra deftere `indis boyut`
 * satırı eklenir (defter de flush edilir). Devam ettirmede:
 *  - başlık satırı (`#plan <segment0YolAnahtarı> <toplam> <initBayt>`) mevcut playlist ile
 *    eşleşmiyorsa → baştan,
 *  - dosya boyu ≠ initBayt + Σ(defter) ise dosya defter toplamına KIRPILIR (yarım segment
 *    kalıntısı atılır),
 *  - indirme kaldığı dilimden sürer; böylece dosya içeriği her zaman defterle birebir aynıdır.
 *
 * `parse` saf JVM fonksiyonudur → birim testli.
 */
object SegmentJournal {

    data class Header(val planKey: String, val total: Int, val initBytes: Long)

    data class State(
        val header: Header?,
        val entries: List<Pair<Int, Long>>
    ) {
        val count: Int get() = entries.size
        val segmentBytes: Long get() = entries.sumOf { it.second }
        val expectedFileLength: Long get() = (header?.initBytes ?: 0L) + segmentBytes
    }

    val EMPTY = State(null, emptyList())

    fun headerLine(planKey: String, total: Int, initBytes: Long): String =
        "#plan $planKey $total $initBytes"

    fun entryLine(index: Int, size: Long): String = "$index $size"

    /** Bozuk/tanımsız satırlar SESSİZCE atlanır; defter asla indirmeyi düşürmez. */
    fun parse(text: String): State {
        var header: Header? = null
        val entries = ArrayList<Pair<Int, Long>>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#plan ")) {
                val parts = line.removePrefix("#plan ").split(' ')
                if (parts.size >= 3) {
                    val key = parts[0]
                    val total = parts[1].toIntOrNull() ?: continue
                    val init = parts[2].toLongOrNull() ?: 0L
                    if (key.isNotBlank() && total > 0) header = Header(key, total, init)
                }
                continue
            }
            if (line.startsWith("#")) continue
            val parts = line.split(' ')
            if (parts.size != 2) continue
            val idx = parts[0].toIntOrNull() ?: continue
            val size = parts[1].toLongOrNull() ?: continue
            if (idx < 0 || size < 0) continue
            entries.add(idx to size)
        }
        return State(header, entries)
    }
}
