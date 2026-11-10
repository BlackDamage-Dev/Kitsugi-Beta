# Kitsugi Beta v2.4.364

## [TR] Türkçe Değişiklik Notları

### 1. Otomatik Çeviri İyileştirmesi (Türkçe Metnin Tekrar Türkçeye Çevrilmesi Önleme)
- **Yeni Dil Sezgi Algoritması (`KitsugiTranslateUtils`):**
  - Türkçe biyografilerde geçen yabancı yapım/şarkı adları ("The Whale", "All Too Well" vb.) nedeniyle metnin tamamının İngilizce sanılması engellendi.
  - Salt kelime sayımı yerine kelime oranı, Türkçeye özgü harfler (`ğ`, `ı`, `ş`, `ç`, `ö`, `ü`, `İ`) ve yaygın Türkçe ekler (`-dır`, `-miş`, `-lar`, `-den`, `-nin` vb.) puanlanarak doğru dil tespiti sağlandı.
- **Google Translate Kaynak Dil Doğrulaması (`src` Denetimi):**
  - Google yanıtından algılanan kaynak dil okunarak hedef dil ile eşleşmesi durumunda çeviri geçersiz sayılır; önbelleğe yazılmaz ve orijinal metin sunulur.
  - 256 kayıtlık "zaten hedef dilde" LRU defteri eklenerek aynı Türkçe biyografinin her açılışta tekrar Google'a gitmesi önlendi.
- **Eşdeğer Çeviri ve Sahte Düğme Temizliği:**
  - Çeviri metni kaynakla yalnızca noktalama veya boşluk farkıyla aynıysa `_translatedBio` / `_translatedSynopsis` null bırakılır; "Orijinal / Çeviri" butonu gereksiz yere gösterilmez ve eski hatalı kayıtlar önbellekten temizlenir.
  - Bu koruma Karakter, Personel, Stüdyo ve Medya Detay sayfalarının tamamında devreye alındı.

### 2. Açıklama Kaynağı Önceliği ve Titreme (Flicker) Düzeltmesi
- **Kanonik Kaynak Açıklamasının Korunması:**
  - `enrichDetail` metodunda TMDB özetinin AniList, MAL ve Bangumi gibi birincil sağlayıcıların özetlerini ezmesi engellendi; TMDB artık yalnızca kaynakta açıklama bulunmadığında yedek olarak kullanılır.
  - `fetchSynopsis` sırası düzeltilerek önce kanonik kaynak, ardından TMDB sorgulanacak şekilde güncellendi.
- **Açıklama Kaynağı Öncelik Sıralaması (`SynopsisOrigin`):**
  - `MediaEntryDetailViewModel` içine `SynopsisOrigin` önceliği (`LIST < API = DETAIL`) tanımlandı. Yavaş dönen kısa liste özetinin ekrandaki zengin kaynak açıklamasını geçersiz kılması engellendi.
  - Aynı metin yeniden geldiğinde ekran yeniden tetiklenmez; arayüzdeki titreme ve atlama tamamen ortadan kaldırıldı.
- **Kaynak Metne Bağlı Çeviri Önbelleği:**
  - `DetailCache` çeviri anahtarı kaynak metnin içeriğine bağlandı; açıklama güncellendiğinde eski metne ait çevirinin kalması önlendi.

---

## [EN] English Release Notes

### 1. Translation Engine Fix (Prevent Turkish to Turkish Re-translation)
- **Ratio-Based Language Heuristics (`KitsugiTranslateUtils`):**
  - Resolved false-positive English classification in Turkish biographies containing English titles/names (e.g., Sadie Sink biography with "The Whale", "All Too Well").
  - Replaced raw word counts with ratio scoring, Turkish character weighting (`ğ`, `ı`, `ş`, `ç`, `ö`, `ü`, `İ`), and common Turkish suffixes (`-dır`, `-miş`, `-lar`, `-den`, `-nin`).
- **Google Translate Source Verification (`src` Parameter Check):**
  - Validates `src` parameter in translation responses; if `sourceLang == targetLang`, the result is discarded, excluded from translation cache, and original text is retained.
  - Added a 256-entry "already in target language" LRU cache to prevent re-querying Google on repeated visits.
- **Equivalent Translation Normalization:**
  - If translated text matches source text under whitespace/punctuation normalization, translation state is kept null to avoid rendering redundant "Original / Translated" buttons.
  - Integrated across Character, Staff, Studio, and Media Detail screens.

### 2. Synopsis Origin Prioritization & Flicker Prevention
- **Preserve Primary Source Synopsis:**
  - Fixed `enrichDetail` where TMDB Turkish overviews unconditionally overwrote canonical source synopses from AniList, MAL, and Bangumi. TMDB now serves purely as a fallback.
  - Corrected `fetchSynopsis` lookup order: queries primary provider first, falling back to TMDB.
- **Synopsis Origin Hierarchy (`SynopsisOrigin`):**
  - Introduced `SynopsisOrigin` hierarchy (`LIST < API = DETAIL`) in `MediaEntryDetailViewModel`. Slower list summaries can no longer override richer detail synopsis already rendered.
  - Discard identical synopsis re-emissions to eliminate UI flicker.
- **Content-Keyed Translation Cache:**
  - `DetailCache` now binds translations to both media identity and raw source text, preventing stale translations from sticking when synopses change.
