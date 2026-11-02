# PLAN / TASK — Otomatik Çeviri Düzeltmeleri (Listem Detayı Açıklaması + AniList Profil Biyografisi)

**Tarih:** 2026-11-02
**Dal:** `arena/916d8a81-kitsugi-beta`
**Şikâyet 1:** Listem sayfasından açılan detay sayfalarındaki "Bilgi" sekmesinin açıklama kısmı, "Açıklama Otomatik Çeviri" ayarı açık olmasına rağmen Google Translate ile çevrilmiyordu (orijinal İngilizce kalıyordu).
**Şikâyet 2:** AniList kullanıcı profilindeki biyografi (Hakkında), wiki sayfa çevirisindeki gibi bağlantılar korunarak otomatik çeviriye dahil edilmiyordu.

---

## 1. Kök Nedenler

| # | Kök Neden | Etki |
|---|---|---|
| KN-1 | `MediaEntryDetailViewModel.applyAndTranslateSynopsis` içindeki çeviri işi, otomatik çeviri ayarını arka planda tek seferlik `runCatching { settingsFlow.first().autoTranslateEnabled }.getOrDefault(false)` ile okuyordu; okuma geçici olarak başarısız olursa sessizce `false` sayıp çeviriyi atlıyordu. | Ayar açıkken bile açıklama orijinal dilde kalır. |
| KN-2 | `DetailCache` ve Room `translation_cache` önbelleklerinde, eski sürümlerden kalmış "çeviri" görünümlü ama aslında orijinal metin olan bayat kayıtlar vardı; bu kayıtlar birebir kaynak metne eşit olduğu için sonsuza dek orijinal metin gösteriliyordu. | Açıklama/biyografi kalıcı olarak çevrilmemiş görünür. |
| KN-3 | `rememberAutoTranslatedHtml` (profil biyografisi) kaynak dili sabit `"auto"` kullanıyordu; wiki çevirisi ise kullanıcının seçtiği kaynak/hedef dil ayarlarını kullanıyordu. | Biyografi, wiki ile tutarsız dil ayarlarıyla çevrilmeye çalışıyordu. |

## 2. Yapılan Düzeltmeler

- [x] **T1 — `MediaEntryDetailViewModel.kt`**
  - `loadEntry` anında otomatik çeviri ayarı bir kez okunup `loadTimeAutoTranslateEnabled` olarak saklanıyor.
  - Çeviri işindeki ayar okuması başarısız olursa sırasıyla `lastAutoTranslateSetting` → `loadTimeAutoTranslateEnabled` değerlerine düşüyor; asla körlemesine `false` olmuyor.
  - `applyAndTranslateSynopsis` ve `fetchSynopsis` içindeki önbellek kontrolleri, önbellekteki değer ham metne eşitse (bayat kayıt) yok sayıyor.
- [x] **T2 — `TranslationManager.kt`**
  - `translateTo` (detay açıklaması yolu): Room önbelleği değeri kaynak metnin birebir aynısıysa bayat kabul edilip yeniden çevriliyor.
  - `translateWebPageText` (profil biyografisi / wiki DOM yolu): aynı bayat-kayıt koruması eklendi.
- [x] **T3 — `KitsugiAutoTranslation.kt`**
  - `rememberAutoTranslatedHtml` artık wiki ile aynı kaynak/hedef dil ayarlarını kullanıyor (`KitsugiTranslatePrefs.sourceLanguage` / `resolvedTarget()`).

## 3. Bağlantı Koruması (doğrulandı)

- `KitsugiHtmlTranslation` tüm HTML etiketlerini/özniteliklerini (`<a href>`, `<img src>` vb.) olduğu gibi korur; yalnızca metin düğümleri çevrilir.
- `TranslationManager.fetchFromGoogle` içindeki maskeleme, metin içindeki çıplak URL'leri de korur.
- Simülasyon: örnek AniList biyografisi HTML'inde linkler birebir korunuyor, yalnızca metinler çevriliyor.

## 4. Değişen Dosyalar

| Dosya | Açıklama |
|---|---|
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/MediaEntryDetailViewModel.kt` | Yükleme anında ayar okuması + bayat önbellek koruması |
| `app/src/main/java/com/kitsugi/animelist/data/local/TranslationManager.kt` | `translateTo` ve `translateWebPageText` için bayat önbellek koruması |
| `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiAutoTranslation.kt` | Biyografi çevirisi wiki ile aynı dil ayarlarını kullanıyor |

## 5. Test

- Listem → herhangi bir kayıt → Bilgi sekmesi: açıklama otomatik Türkçe gelmeli.
- Profil → AniList → Hakkında sekmesi: biyografi otomatik Türkçe gelmeli, bağlantılar çalışmalı.
