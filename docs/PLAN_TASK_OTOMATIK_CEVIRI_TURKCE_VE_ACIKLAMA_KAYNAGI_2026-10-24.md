# PLAN & TASK — Otomatik Çeviri (Türkçe→Türkçe) ve Açıklama Kaynağının Sabitlenmesi

**Tarih:** 2026-10-24
**Durum:** ✅ Tamamlandı
**Sürüm:** v2.4.341
**Rapor:** `COZUM_RAPORU_OTOMATIK_CEVIRI_TURKCE_VE_ACIKLAMA_KAYNAGI_2026-10-24.md`

---

## 1. Şikâyet (kullanıcı bildirimi)

1. **Türkçe metin tekrar Türkçeye çevriliyor.**
   Karakter biyografisi zaten Türkçe iken ekranda "Orijinal" yazıyor; "Çeviri"ye basınca metin değişmiyor
   (yalnızca boşluk/noktalama farkıyla aynı metin görünüyor).
2. **Açıklama bir anlık başka kaynaktan geliyor.**
   Detay sayfasındaki açıklama, sayfa açıldıktan kısa süre sonra başka bir kaynağın metnine dönüşüyor.

---

## 2. Tespit edilen kök nedenler

### A. Türkçe→Türkçe çeviri
- `TranslationManager.isLikelyTurkish()` mutlak kelime **sayısına** bakıyordu (`englishCount >= 2`).
  Türkçe cümle içindeki `The Whale`, `All Too Well` gibi yapım adları metni "İngilizce" yapıyordu.
- Kelime ayırıcı kesme işaretini böldüğü için Türkçe ekler (`Things'teki`) hiç eşleşmiyordu.
  Set içinde bozuk `"गöre"` kaydı vardı.
- Google yanıtındaki **algılanan kaynak dil** (`src` / klasik biçim 3. eleman) okunmuyordu.
- `translateWebPageText()` (HTML/wiki yolu) hiç dil denetimi yapmıyordu.
- ViewModel'ler çeviri sonucu kaynağa eşit olsa bile `_translatedBio` / `_translatedSynopsis` alanını dolduruyordu;
  UI `!= null` kontrolüyle "Çeviri" düğmesi gösteriyordu.
- `MediaEntryDetailViewModel` (otomatik çeviri kapalı dalı) ve `StudioDetailViewModel`,
  orijinal metni doğrudan `_translated*` alanına yazıyordu.

### B. Açıklamanın kaynak değiştirmesi
- `KitsugiDetailClient.enrichDetail()`: `updatedSynopsis = trMeta.synopsis`
  → TMDB Türkçe özeti, kaynağın (AniList/MAL/Bangumi) özetini koşulsuz eziyordu.
  "Source Authority Preservation" kuralı başlık/tür/stüdyo/kapağı kapsıyor, özeti kapsamıyordu.
- `KitsugiDetailClient.fetchSynopsis()`: TMDB özeti, kaynak özetinden **önce** dönüyordu.
- `MediaEntryDetailViewModel`: açıklama üç yerden (`entry.synopsis`, `fetchSynopsis`, `fetchDetail`)
  yazılıyordu; **kaynak önceliği yoktu**, son yazan kazanıyordu.
- `fetchSynopsis()`: önbellekteki çeviriyi metin doğrulamadan dönüyor, `_originalSynopsis`'i güncellemiyordu.
- `DetailCache` çeviri anahtarı kaynak metni içermediği için eski çeviri yeni metne yapışıyordu.

---

## 3. Görev listesi

### 3.1 Ortak yardımcılar
- [x] `normalizeTranslationText()` — harf/rakam dışını at (harf büyüklüğü, boşluk, noktalama yok sayılır)
- [x] `translationMatchesSource()` — çeviri kaynakla eşdeğer mi?
- [x] `isLikelyTurkishText()` — kelime **oranı** + Türkçe harf/kelime/ek puanı; CJK/Kiril/İbrani/Arapça eleme
- [x] Kesme işaretiyle ayrılan Türkçe ekleri tanı (`Things'teki`)
- [x] Bozuk `"गöre"` kaydını temizle

### 3.2 TranslationManager
- [x] `parseTranslationResponse()` → `ParsedTranslation(text, detectedSourceLang)`
      (`dj=1` için `src`, klasik dizi için 3. eleman)
- [x] `fetchSingleChunk()` / `fetchSingleChunkWithRetry()` → `ParsedTranslation?`
- [x] `GoogleTranslationResult` → `detectedSourceLang` + `alreadyInTargetLanguage`
- [x] `translateTo()`: kaynak dil == hedef dil ise istek atma
- [x] `translateTo()`: algılanan dil == hedef dil ise orijinali dön, önbelleğe yazma
- [x] `translateTo()`: normalize eşdeğerlik denetimi
- [x] `translateWebPageText()`: aynı denetimler (HTML/wiki yolu)
- [x] "Zaten hedef dilde" 256 kayıtlık LRU defteri (`accessOrder` LinkedHashMap)

### 3.3 DetailCache
- [x] `getTranslation` / `putTranslation` → `sourceText: String?` parametresi
- [x] Kaynak metin kimliği ayrı LRU haritada; eşleşmeyen çeviri `null`
- [x] `removeTranslation` ve `clear()` bu haritayı da temizler
- [x] Geriye dönük uyumluluk: `sourceText` verilmezse eski davranış

### 3.4 Detay ViewModel'leri
- [x] `CharacterDetailViewModel` — eşdeğerlik koruması + `sourceText` + hatalı kayıt temizliği
- [x] `StaffDetailViewModel` — aynı
- [x] `StudioDetailViewModel` — aynı + `_translatedAbout = rawAbout` hatası kaldırıldı
- [x] `ApiResultDetailViewModel` — aynı
- [x] `MediaEntryDetailViewModel` — aynı + `SynopsisOrigin` önceliği (LIST < API = DETAIL)
- [x] `MediaEntryDetailViewModel` — aynı metin tekrar gelirse state yeniden yazılmaz
- [x] `MediaEntryDetailViewModel` — otomatik çeviri kapalıyken `_translatedSynopsis = null`

### 3.5 KitsugiDetailClient
- [x] `fetchSynopsis()` — önce kaynağın kendi özeti, TMDB yalnızca yedek
- [x] `enrichDetail()` — TMDB özeti kaynak özetini ezmez

### 3.6 UI
- [x] `ApiResultDetailPage` — normalize "çeviri var mı" kararı
- [x] `MediaEntryDetailPage` — aynı
- [x] `StudioDetailPage` — aynı
- [x] `KitsugiAutoTranslation` — aynı

### 3.7 Test & dokümantasyon
- [x] `TranslationLanguageDetectionTest` (8 test)
- [x] `COZUM_RAPORU_..._2026-10-24.md`
- [x] `release_notes_v2.4.341.md`
- [x] Bu plan/task dosyası
- [x] Değişen dosyaların ZIP paketi

---

## 4. Değişen dosyalar (14 + 2 doküman + 1 test)

```
app/src/main/java/com/kitsugi/animelist/data/local/TranslationManager.kt
app/src/main/java/com/kitsugi/animelist/data/remote/DetailCache.kt
app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiDetailClient.kt
app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiAutoTranslation.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailPage.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailViewModel.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/detail/CharacterDetailViewModel.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/detail/MediaEntryDetailPage.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/detail/MediaEntryDetailViewModel.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/detail/StaffDetailViewModel.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/detail/StudioDetailPage.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/detail/StudioDetailViewModel.kt
app/src/main/java/com/kitsugi/animelist/utils/KitsugiTranslateUtils.kt
app/src/test/java/com/kitsugi/animelist/utils/TranslationLanguageDetectionTest.kt  (yeni)
COZUM_RAPORU_OTOMATIK_CEVIRI_TURKCE_VE_ACIKLAMA_KAYNAGI_2026-10-24.md  (yeni)
release_notes_v2.4.341.md  (yeni)
PLAN_TASK_OTOMATIK_CEVIRI_TURKCE_VE_ACIKLAMA_KAYNAGI_2026-10-24.md  (yeni)
```

---

## 5. Kabul kriterleri

- [x] Türkçe biyografide "Orijinal / Çeviri" düğmesi **görünmez**.
- [x] İçinde İngilizce yapım adı geçen Türkçe metin Türkçe kabul edilir.
- [x] Saf İngilizce / Japonca metin yanlışlıkla Türkçe sayılmaz.
- [x] Açıklama, kaydın açıldığı kaynağın kendi metni üzerinde kalır.
- [x] Kısa liste özeti, uzun kaynak açıklamasını ezmez.
- [x] Özet değişince eski metnin çevirisi gösterilmez.
- [x] `sourceText` verilmeyen eski `DetailCache` çağrıları bozulmaz.

---

## 6. Doğrulama notu

- Değişen tüm Kotlin dosyaları sözdizimi (blok/parantez) denetiminden geçirildi.
- `isLikelyTurkishText` algoritması Python'da birebir simüle edilerek 6 örnekle doğrulandı
  (Sadie Sink biyografisi, kısa Türkçe, İngilizce, Japonca, Türkçe+İngilizce yapım adları).
- Bu ortamda Android SDK / Kotlin derleyicisi bulunmadığı için **`./gradlew` derlemesi çalıştırılamadı**.
  Yerelde doğrulama komutu:
  ```bash
  ./gradlew :app:testDebugUnitTest --tests "*TranslationLanguageDetectionTest"
  ```

---

## 7. Riskler / geri dönüş

- `DetailCache` imzaları varsayılan parametreyle genişletildi → mevcut çağrı yerleri etkilenmez.
- Eski hatalı çeviri kayıtları otomatik göz ardı edilir; kullanıcı verisi temizliği gerekmez.
- Kaynakta özet yoksa TMDB yedeği aynen çalışmaya devam eder.
- Geri alma: yalnızca bu commit'teki 14 Kotlin dosyası geri alınır; dokümanlar bağımsızdır.
