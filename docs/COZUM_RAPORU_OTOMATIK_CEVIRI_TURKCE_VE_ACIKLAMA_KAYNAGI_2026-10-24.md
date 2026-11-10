# Çözüm Raporu — Otomatik Çeviri (Türkçe→Türkçe) ve Açıklamanın Kaynak Değiştirmesi

**Tarih:** 2026-10-24
**Kapsam:** Karakter / personel / stüdyo / medya detay sayfaları, otomatik çeviri katmanı, açıklama (synopsis) kaynağı
**Durum:** Düzeltildi (birim testleri eklendi, Android derlemesi bu ortamda çalıştırılamadı — bkz. "Doğrulama")

---

## 1. Şikâyet

1. **Türkçe metin tekrar Türkçeye çevriliyor.** Sadie Sink biyografisi zaten Türkçeydi; "Çeviri" düğmesine basınca metin değişmiyor, yalnızca boşluk/noktalama farkıyla aynı metin geliyordu.
2. **Açıklama bir anlık başka kaynaktan geliyor.** Detay sayfasındaki açıklama, sayfa açıldıktan bir süre sonra başka bir kaynağın metnine dönüşüyordu.

---

## 2. Kök nedenler

### 2.1 Türkçe→Türkçe çeviri

| # | Yer | Sorun |
|---|-----|-------|
| 1 | `TranslationManager.isLikelyTurkish()` | Metin içinde geçen **tek bir yaygın İngilizce kelime** (`The Whale` → "the", `All Too Well` → "all") "İngilizce" sayılıyor ve Türkçe metin Google'a gönderiliyordu. `englishCount >= 2` kuralı mutlak eşikti; kelime **oranı** değil **sayısı** bakılıyordu. |
| 2 | `TranslationManager.isLikelyTurkish()` | Kelime ayırıcı `[\s\p{Punct}]+` kesme işaretini ayırıyor, ancak Türkçe ekler (`Things'teki`) bu yüzden hiçbir listede eşleşmiyordu. Ayrıca set içinde bozuk bir `"गöre"` (Devanagari) kaydı vardı. |
| 3 | `TranslationManager.translateTo()` | Google yanıtındaki **algılanan kaynak dil** (`src` / klasik biçimde 3. eleman) hiç okunmuyordu. Oysa `sl=auto` ile gelen bu alan, "kaynak zaten hedef dilde" kararının kesin yoludur. |
| 4 | `TranslationManager.translateWebPageText()` | "Zaten hedef dilde" denetimi **hiç yoktu** (HTML/wiki metinleri bu yoldan geçiyor). |
| 5 | `CharacterDetailViewModel` / `MediaEntryDetailViewModel` / `StaffDetailViewModel` | Çeviri sonucu kaynak metinle aynı olsa bile `_translatedBio` / `_translatedSynopsis` **dolduruluyordu**. UI "çeviri var mı" kararını `!= null` ile verdiği için düğme görünüyordu. |
| 6 | `MediaEntryDetailViewModel` (otomatik çeviri kapalı dalı), `StudioDetailViewModel` | Orijinal metin doğrudan `_translatedSynopsis` / `_translatedAbout` alanına yazılıyordu — yani "çeviri" olarak işaretleniyordu. |
| 7 | `DetailCache` çeviri anahtarı | Anahtar yalnızca `(hedef dil, tür, kaynak, id)` içeriyordu. Özet/biyografi metni değişince eski metnin çevirisi yeni metne "yapışıyordu". |

### 2.2 Açıklamanın kaynak değiştirmesi

| # | Yer | Sorun |
|---|-----|-------|
| 1 | `KitsugiDetailClient.enrichDetail()` | `updatedSynopsis = trMeta.synopsis` — TMDB'nin Türkçe özeti, kaynağın (AniList/MAL/Bangumi/Simkl) kendi özetini **koşulsuz** eziyordu. "Source Authority Preservation" kuralı başlık/tür/stüdyo/kapağı kapsıyor, **özeti kapsamıyordu**. |
| 2 | `KitsugiDetailClient.fetchSynopsis()` | TMDB özeti, kaynağın özeti denenmeden **önce** döndürülüyordu. `fetchSynopsis` ile `fetchDetail` farklı anlarda bittiği için ekranda önce kaynağın metni, sonra TMDB metni (veya tersi) görünüyordu. |
| 3 | `MediaEntryDetailViewModel` | Açıklama üç ayrı yerden geliyor: `entry.synopsis` (liste), `fetchSynopsis` (kaynak API), `fetchDetail` (detay yanıtı). Üçü de `_synopsisState`'e yazıyordu; **kaynak önceliği yoktu**, son yazan kazanıyordu. |
| 4 | `MediaEntryDetailViewModel.fetchSynopsis()` | Önbellekteki çeviri metni doğrulanmadan dönülüyor, `_originalSynopsis` güncellenmiyordu → "Orijinal/Çeviri" geçişi yanlış metni gösteriyordu. |

---

## 3. Yapılan düzeltmeler

### 3.1 Ortak yardımcılar — `utils/KitsugiTranslateUtils.kt`
- `normalizeTranslationText()`: harf/rakam dışını atar (büyük-küçük harf, boşluk, noktalama yok sayılır).
- `translationMatchesSource()`: çeviri ile kaynak metin eşdeğer mi?
- `isLikelyTurkishText()`: yabancı kelime **oranı**, Türkçeye özgü harf sayısı, Türkçe kelime listesi ve Türkçe ek puanı ile karar verir. Kısa metinlerde tek güçlü işaret yeterlidir. CJK/Kiril/İbrani/Arapça metinler doğrudan elenir.

### 3.2 `TranslationManager`
- Google yanıtı ayrıştırıcısı artık **algılanan kaynak dili** döndürür (`ParsedTranslation(text, detectedSourceLang)`); `dj=1` biçiminde `src`, klasik dizi biçiminde 3. eleman okunur.
- `translateTo()` ve `translateWebPageText()`: algılanan dil hedef dile eşitse **orijinal metin** döner, önbelleğe yazılmaz.
- `translateTo()`: kaynak dil = hedef dil ise hiç istek atılmaz.
- Çeviri sonucu normalize olarak kaynağa eşitse orijinal döner.
- "Zaten hedef dilde" bilgisi 256 kayıtlık LRU bellekte tutulur (`accessOrder` LinkedHashMap); aynı Türkçe biyografi için her açılışta istek atılmaz.

### 3.3 `DetailCache`
- `getTranslation` / `putTranslation` yeni `sourceText` parametresi alır. Kaynak metnin normalize hali ayrı bir LRU haritada saklanır; eşleşmeyen çeviri **geçersiz** sayılır ve `null` döner.
- `removeTranslation` ve `clear()` bu haritayı da temizler.
- Geriye dönük uyumlu: `sourceText` verilmezse eski davranış aynen çalışır.

### 3.4 Detay ViewModel'leri
- `CharacterDetailViewModel`, `StaffDetailViewModel`, `StudioDetailViewModel`, `ApiResultDetailViewModel`, `MediaEntryDetailViewModel`:
  - çeviri = kaynak ise `_translated*` **null** bırakılır, eski yanlış önbellek kaydı silinir,
  - önbellekten gelen çeviri de eşdeğerlik denetiminden geçer,
  - `putTranslation` çağrıları `sourceText` ile yapılır.
- `MediaEntryDetailViewModel`: `SynopsisOrigin` (LIST < API = DETAIL) önceliği eklendi. Aynı metin tekrar gelirse hiçbir state yeniden yazılmaz (titreme yok). Daha düşük öncelikli kaynak, gösterilen açıklamayı ezemez.

### 3.5 `KitsugiDetailClient`
- `fetchSynopsis()`: **önce kaynağın kendi özeti**; yalnızca boşsa TMDB yedeği.
- `enrichDetail()`: TMDB özeti kaynağın özetini **ezmez**; yalnızca kaynakta özet yoksa kullanılır. (Çeviri zaten otomatik çeviri katmanında yapılıyor.)

### 3.6 UI
- `ApiResultDetailPage`, `MediaEntryDetailPage`, `StudioDetailPage`: "çeviri var mı" kararı normalize karşılaştırma ile verilir.
- `KitsugiAutoTranslation`: aynı eşdeğerlik denetimi.

---

## 4. Davranış değişiklikleri (kullanıcıya görünen)

- Türkçe biyografi/açıklamada artık **"Orijinal / Çeviri" düğmesi çıkmaz**.
- Açıklama, kaydın açıldığı kaynağın kendi metni üzerinde kalır; TMDB yalnızca kaynak boşsa devreye girer.
- Sayfa açılışında kısa liste özeti görünse bile, uzun/kaynak açıklaması geldiğinde yerini alır; **tersi olmaz** (kısa özet uzun açıklamayı ezmez).
- Özet değiştiğinde eski metnin çevirisi gösterilmez.

## 5. Testler

`app/src/test/java/com/kitsugi/animelist/utils/TranslationLanguageDetectionTest.kt`

- Şikâyet edilen Sadie Sink biyografisi → Türkçe.
- `"Tokyo Ghoul, Sui Ishida'nın yazdığı bir manga serisidir."` → Türkçe.
- Düz İngilizce metin, Japonca metin, boş metin → Türkçe değil.
- `"...oyuncusudur.Netflix"` ↔ `"...oyuncusudur. Netflix"` → aynı metin.
- Gerçek çeviri → farklı metin.

Mantık ayrıca Python'da birebir simüle edilerek doğrulandı (bkz. çalışma notları).

## 6. Doğrulama / riskler

- Bu ortamda Android SDK ve Kotlin derleyicisi yok; **`./gradlew` derlemesi çalıştırılamadı**. Değişen tüm Kotlin dosyaları sözdizimi (parantez/blok) denetiminden geçirildi.
- `DetailCache` imzaları varsayılan parametrelerle genişletildiği için mevcut çağrı yerleri bozulmaz.
- Eski (hatalı) çeviri kayıtları `sourceText` uyuşmazlığı veya eşdeğerlik denetimiyle otomatik olarak göz ardı edilir; kullanıcıdan veri temizliği gerekmez.
- Kaynak otoritesi değişikliği, özeti boş olan kayıtlarda TMDB yedeğine düşmeye devam eder (davranış korunur).
