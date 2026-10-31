package com.kitsugi.animelist.data.local

/** SDK-independent script predicates shared by title resolution and duplicate merging. */
internal object TitleScriptPolicy {
    fun hasCjkCharacters(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return text.any {
            it.code in 0x3040..0x30ff || // Hiragana & Katakana
            it.code in 0x4e00..0x9fff || // CJK Unified Ideographs (Kanji)
            it.code in 0xac00..0xd7af || // Hangul (Korean)
            it.code in 0x31f0..0x31ff || // Katakana phonetic extensions
            it.code in 0xff00..0xffef    // Halfwidth and Fullwidth Forms
        }
    }

    /**
     * Bir karakterin Latin alfabesine ait olup olmadığını kontrol eder (Temel Latin,
     * Latin-1 Supplement, Latin Extended A/B/Additional — Türkçe "İ/ı/ğ/ş/ü/ö/ç" ve diğer
     * Avrupa dillerindeki aksanlı harfler dahil).
     */
    private fun isLatinLetter(ch: Char): Boolean {
        val code = ch.code
        return code in 0x0041..0x005A || // A-Z
            code in 0x0061..0x007A ||    // a-z
            code in 0x00C0..0x024F ||    // Latin-1 Supplement + Latin Extended A/B
            code in 0x1E00..0x1EFF ||    // Latin Extended Additional (Vietnamese vb.)
            code in 0x2C60..0x2C7F       // Latin Extended C
    }

    /**
     * Bir metnin GERÇEKTEN Latin alfabesiyle yazılmış olup olmadığını kontrol eder.
     *
     * Eskiden "Latin ad" seçimi `!hasCjkCharacters(text)` şartıyla yapılıyordu; bu, CJK
     * (Japonca/Çince/Korece) OLMAYAN her şeyi (Arapça, Tayca, Kiril, İbranice, Devanagari…)
     * yanlışlıkla "Latin" sayıyordu. Örnek: TMDB `also_known_as` listesinde bir kişinin
     * Arapça ("اری کیتامورا") veya Tayca ("ไอ โนะนะกะ") çevirisi varsa, bu fonksiyon
     * olmadan romaji/İngilizce ad yerine o seçiliyordu. Artık yalnızca harflerinin TAMAMI
     * Latin alfabesinde olan adlar "Latin" kabul edilir.
     */
    fun isLatinText(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val letters = text.filter { it.isLetter() }
        if (letters.isEmpty()) return false
        return letters.all { isLatinLetter(it) }
    }

}
