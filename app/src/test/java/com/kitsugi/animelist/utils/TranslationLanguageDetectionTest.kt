package com.kitsugi.animelist.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Çeviri katmanının "zaten hedef dilde" sözleşmesi.
 *
 * Kullanıcı hatası: Türkçe biyografi, içinde geçen İngilizce yapım adları yüzünden
 * ("The Whale", "All Too Well", "Stranger Things") İngilizce sanılıyor ve Türkçeye
 * yeniden çevriliyordu. Arayüz bu yüzden Türkçe metinde "Orijinal / Çeviri" düğmesi
 * gösteriyor, düğmeye basınca aynı metin (yalnızca noktalama/boşluk farkıyla) geliyordu.
 *
 * Buradaki testler hem dil sezgisini hem de "çeviri == kaynak" eşdeğerlik denetimini
 * kilitler; [TranslationManager] içindeki Google `src` denetimi ise kesin kararı verir.
 */
class TranslationLanguageDetectionTest {

    private val sadieBiography =
        "Sadie Sink, 2002 doğumlu Amerikalı sinema, dizi ve Broadway oyuncusudur." +
            "Netflix'in fenomen dizisi Stranger Things'teki Max Mayfield rolüyle dünya çapında " +
            "büyük bir üne kavuşmuştur. Sinemada The Whale (Balina) filmindeki dramatik " +
            "performansı ve Taylor Swift'in All Too Well kısa filmindeki başrolüyle " +
            "eleştirmenlerden tam not almıştır. Ayrıca Marvel Sinematik Evreni'ne Jean Grey " +
            "karakteriyle adım atan genç sanatçı, Broadway'deki başarılarıyla Tony Ödülü " +
            "adaylığı da elde etmiştir."

    @Test
    fun `turkish text containing english titles is still turkish`() {
        assertTrue(isLikelyTurkishText(sadieBiography))
    }

    @Test
    fun `short turkish sentence with apostrophe suffixes is turkish`() {
        assertTrue(isLikelyTurkishText("Tokyo Ghoul, Sui Ishida'nın yazdığı bir manga serisidir."))
    }

    @Test
    fun `plain english text is not turkish`() {
        assertFalse(
            isLikelyTurkishText(
                "She is an American actress. She was born in 2002 and is known for her role " +
                    "in Stranger Things. The series became a worldwide phenomenon."
            )
        )
    }

    @Test
    fun `cjk text is not turkish`() {
        assertFalse(isLikelyTurkishText("彼女はアメリカの女優です。"))
    }

    @Test
    fun `blank text is not turkish`() {
        assertFalse(isLikelyTurkishText("   "))
    }

    @Test
    fun `translation equal to source differs only by formatting`() {
        val source = "Sadie Sink oyuncusudur.Netflix'in dizisinde rol aldı."
        val same = "Sadie Sink oyuncusudur. Netflix'in dizisinde rol aldı."
        assertTrue(translationMatchesSource(source, same))
    }

    @Test
    fun `real translation is not equal to source`() {
        val source = "She is an American actress."
        val translation = "O bir Amerikalı oyuncudur."
        assertFalse(translationMatchesSource(source, translation))
    }

    @Test
    fun `normalization drops case punctuation and whitespace`() {
        assertEquals(
            normalizeTranslationText("Merhaba, Dünya!"),
            normalizeTranslationText(" merhaba dünya ")
        )
    }
}
