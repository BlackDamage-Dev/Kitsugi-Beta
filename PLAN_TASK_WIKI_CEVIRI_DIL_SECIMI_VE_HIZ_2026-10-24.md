# PLAN / TASK — Wiki Sayfa Araçlarından Dil Seçimi + Otomatik Çeviri Hızı

**Tarih:** 2026-10-24
**Dal:** `arena/22d8caa2-kitsugi-beta`
**Commit:** `0e7d584` (taban: `3d3242f`)
**Kapsam:** 11 dosya (10 değişti, 1 rapor yeni) · +915 / −109 satır — ayrıntı: `COZUM_RAPORU_WIKI_CEVIRI_DIL_SECIMI_VE_HIZ_2026-10-24.md`

---

## 1. Kullanıcı Şikâyeti

1. "Wiki açılır sayfasındaki otomatik çeviriye, buradan **çevrilen / çevrilecek** dil
   seçim hakkı sunulsun; illa uygulama ayarlarına girilmesine gerek kalmasın."
   (Ekran görüntüsü: Sayfa araçları → Çeviri satırı "Google → tr · Çevriliyor… (162 metin)".)
2. "Halen daha **feci yavaş çeviriyor**; şu anki çeviri mantığını bozmadan fişek gibi
   hızlı çalışsın — gerekli araştırmaları (açık kaynak kodlar, siteler) yap, sonra düzelt."

---

## 2. Araştırma

- [x] Google Translate tek istek sınırı **5000 karakter** (easygoogletranslate,
      google-translate-api-x "batch endpoint", Google Yardım sayfası).
- [x] KISS Translator / Immersive Translate yaklaşımı: metinleri **paketleme**,
      tekrarları eleme, önbellek, sınırlı eşzamanlılık (kod kopyalanmadı).
- [x] Döngüde `getBoundingClientRect()` / `getComputedStyle()` = zorunlu eşzamanlı
      layout ("layout thrashing"); tek `checkVisibility()` + önbellekleme öneriliyor.

---

## 3. Kök Neden Analizi

| # | Kök neden | Etki |
|---|-----------|------|
| H1 | `WikiPageEnhancements` döngüsü tamamen seri: take(≤8 metin/≤1000 krk) → ağ → apply → **120 ms uyu** | 162 metin ≥21 ardışık ağ turu + tur başı ölü süre |
| H2 | Paket 1000 krk, çevirmen parçası 1400 krk | 5000 sınırının ~1/5'i → ~3x gereksiz istek |
| H3 | JS `eligible()`: düğüm başına tüm atalarda `getComputedStyle` + rect + uzun `closest(skip)` + `closest('[lang]')` | 8 ms tarama bütçesi birkaç düğümde doluyor |
| H4 | Çevrilmiş düğümler her taramada H3'ten tekrar geçiyor; `status()` tüm kayıtları `trim()` ile sayıyor | Tarama O(tüm kayıt × tur) |
| H5 | Önbellek paketin bütününe anahtarlı; diziliş değişince hiç tutmuyor; bellek katmanı yok (her sorgu Room) | Aynı paragraflar tekrar ağda |
| H6 | Maskeleme Regex'leri her çağrıda yeniden derleniyor; düz metin de 6 taramadan geçiyor | CPU/tahsisat |
| H7 | Reklam stili betiği her döngü turunda JS köprüsünde | Gereksiz köprü turu |

---

## 4. Plan / Task Listesi

### 4.1 Çevrilen / Çevrilecek dil seçimi (Sayfa araçları)
- [x] `WikiPageTools.kt`: Çeviri anahtarının altına **"Çevrilen dil"** ve **"Çevrilecek dil"**
      satırları (seçili dil adı + açılır işareti).
- [x] Aranabilir dil seçici dialogu: kaynakta "Otomatik (Algıla)" + 132 dil,
      `LazyColumn` (yalnızca görünür satırlar bileşir), ad/kod filtresi.
- [x] Seçim aynı küresel ayara yazılır (`SettingsDataStore.setTranslateSource/TargetLanguage`)
      → Ayarlar ile her zaman senkron, yeni durum makinesi yok.
- [x] Dil seçmek = "bu dile çevir" niyeti: çeviri kapalıysa seçim sayfada açar
      (`pendingTranslationEnable` tek kullanımlık bayrak; yazım hatasında temizlenir).
- [x] `WikiOriginalPage.kt`: dil yazım geri çağrımları + ortak kayıt iskeleti +
      ayar akışında niyet koruma (Ayarlar'dan değişimde eski davranış korunur).
- [x] Metinler TR+EN: `wiki_translate_source_language`, `wiki_translate_target_language`,
      `wiki_translate_language_search`; `wiki_tools_explanation` güncellendi.

### 4.2 Hız — boru hattı ve paketler
- [x] `WikiPageEnhancements.kt`: seri döngü → **boru hattı** (en çok 4 paket eşzamanlı;
      ağ kapısıyla aynı sınır), JS köprüsü sıralı, sonuçlar belge sırasıyla uygulanır.
- [x] Paket sonrası sabit **120 ms bekleme kaldırıldı** (boşta bekleme süreleri aynı).
- [x] Reklam stili tazeleme en sık 2 saniyede bire indirildi (H7).
- [x] `WikiDomTranslationBatch.kt`: paket **16 satır**, metin bazlı önbellek sorgusu →
      yalnızca eksikler ağa; aynı metin tekilleştirme; bozuk ayraçta paralel tekil yedek;
      kesintide yelpazeleme yok (sözleşme aynı).

### 4.3 Hız — çevirmen katmanı (`TranslationManager.kt`, `TranslationCacheDao.kt`)
- [x] Web parçası 1400 → **3600 krk** (yalnızca web yolu; paket = tek istek).
- [x] Room önüne 600 kayıtlık **süreç içi LRU** bellek önbelleği (aynı anahtarlar);
      `cachedWebPageText` / `storeWebPageTexts` (tek Room işleminde toplu yazım).
- [x] Uzun tek düğüm parçaları paralel (sıra korunur, kapı sınırlı).
- [x] Maskeleme Regex'leri tek kez derlenir; tetikleyicisiz düz metin hızlı yolda
      (şüphede tam maskeleme — koruma davranışı aynı).

### 4.4 Hız — DOM tarayıcısı (`dom-translator.js`)
- [x] Öğe bilgisi (skip/görünürlük/dil/kutu) WeakMap önbelleği; yalnızca değişen
      alt ağaç geçersizleşir, her tarama turu tazelenir; `resize` + `<details open>` izlenir.
- [x] Görünürlükte `checkVisibility()` (yedek: eski hesaplanan-stil yürüyüşü).
- [x] Yerleşmiş (çevrilmiş/bekleyen/denemesi dolmuş) düğümlerde kestirme;
      `status()` bayrak sayar (trim karşılaştırması yok).
- [x] Paket sınırı 16 metin / 3200 krk; 8 ms tarama bütçesi ve -1…+3 ekran penceresi **aynı**.

### 4.5 Korunan sözleşmeler (bilerek dokunulmadı)
- [x] Ayraç bütünlüğü (bozukta konumsal tahmin yok), yalnızca `Text.nodeValue`,
      rev/id doğrulaması, orijinale ağsız dönüş, gizli/form/kod göndermeme,
      6000 düğüm / 1M krk sınırları, deneme sayısı, ağ kapısı (4), önbellek anahtarları.

---

## 5. Testler

- [x] JS işlev testi (jsdom): kök dışı/`display:none`/`opacity:0`/`<pre>`/`translate=no`
      toplanmıyor; çevir → kapat (orijinal geri) → aç (önbellekten) → dil değiştir
      (orijinal + yeniden toplama); sayım doğru. `node --check` temiz.
- [x] 4 yeni birim testi (`WikiPageToolsTest`): önbellek atlama + tekil saklama,
      tek istekte sıra, bozuk ayraç yedeği, kesintide 1 istek.
- [ ] Gradle doğrulaması: **bu ortamda JDK/Android SDK yok** (`java` bulunamadı) →
      ilk fırsatta: `./gradlew :app:testFossDebugUnitTest --tests "*WikiPageToolsTest*"`
- [ ] Cihazda deneme: aynı Wikipedia sayfasında "Çevriliyor… (N metin)" ilerleyişi ve
      Sayfa araçlarından dil değiştirip yeniden çeviri.

---

## 6. Değişen Dosyalar

| Dosya | İş |
|-------|----|
| `ui/screens/detail/wiki/WikiPageTools.kt` | Dil satırları + aranabilir seçici |
| `ui/screens/detail/wiki/WikiOriginalPage.kt` | Dil yazımı, niyet bayrağı, kayıt iskeleti |
| `ui/screens/detail/wiki/WikiPageEnhancements.kt` | Paralel boru hattı |
| `ui/screens/detail/wiki/WikiDomTranslationBatch.kt` | 16'lı paket, önbellek, tekilleştirme |
| `data/local/TranslationManager.kt` | LRU, web 3600, maske hızlı yolu |
| `data/local/TranslationCacheDao.kt` | Toplu yazım |
| `app/src/main/assets/wiki/dom-translator.js` | Öğe önbelleği, kestirme, büyük paket |
| `res/values/strings.xml`, `res/values-en/strings.xml` | 3 yeni metin + açıklama |
| `app/src/test/.../WikiPageToolsTest.kt` | 4 yeni test |
| `COZUM_RAPORU_WIKI_CEVIRI_DIL_SECIMI_VE_HIZ_2026-10-24.md` | Çözüm raporu |
| `PLAN_TASK_WIKI_CEVIRI_DIL_SECIMI_VE_HIZ_2026-10-24.md` | Bu plan/task dosyası |
