# Çözüm Raporu — Wiki Mobil Varsayılan, Ekrana Sığdırma ve Çeviri Hızı
**Tarih:** 2026-10-22 · **Sürüm:** 2.4.341 · **Kapsam:** Özgün wiki sayfası (WebView okuyucu)

Kullanıcı bildirimi (3 madde):
1. Wiki açılır sayfası varsayılan olarak mobil görünmeli veya en son seçilen görünüm hatırlanmalı.
2. Sayfa kendini otomatik olarak ekrana sığdırmıyor.
3. Bu tarz sayfalarda otomatik çeviri çok yavaş; çeviri takılmadan, hata verdirmeden hızlanmalı.

---

## 1) Varsayılan görünüm = mobil, seçim kalıcı

### Kök neden
`WikiOriginalSession` içinde `var desktop by mutableStateOf(true)` idi: her yeni oturum **masaüstü** düzende ve masaüstü user-agent ile açılıyordu. Kullanıcının menüden yaptığı seçim hiçbir yere yazılmıyordu; sayfa/sekme yeniden oluşturulduğunda tekrar masaüstüne dönüyordu.

### Yapılanlar
- `WikiOriginalSession(initialUrl, readerLocation, initialDesktop = false)`: düzen artık oturum oluşturulurken verilir, varsayılan **mobil**.
- `AppSettings.IntegrationsAndNotificationsSettings.wikiDesktopLayout` + `AppSettings.wikiDesktopLayout` eklendi (varsayılan `false`).
- `SettingsDataStore`: `wiki_desktop_layout` anahtarı, okuma ve `setWikiDesktopLayout(enabled)` yazıcısı.
- `KitsugiContentPrefs.wikiDesktopLayout`: DataStore akışından beslenen süreç içi önbellek. Oturumlar **bileşim sırasında** oluştuğu için (DataStore askıya alınamaz) senkron okunabilen bu önbellek "en son ne seçildiyse onu aç" davranışını verir.
- `FandomWikiReader`: yeni oturumlar `KitsugiContentPrefs.wikiDesktopLayout` ile başlar; menüdeki "Masaüstü/Mobil düzen" değişimi `setWikiDesktopLayout` ile **kalıcı** yazılır. Yeni sekme kopyası da seçili düzeni taşır.

Sonuç: ilk açılış mobil; kullanıcı masaüstüne geçerse sonraki tüm wiki sayfaları, sekmeler ve uygulama yeniden başlatmaları masaüstünde açılır (ve tersi).

---

## 2) Sayfanın ekrana sığdırılması

### Kök neden
- Masaüstü modunda viewport meta'sı **`onPageFinished`** içinde, yani yerleşim hesaplandıktan sonra `width=1200` yapılıyordu. WebView ilk ölçeği eski (cihaz genişliği) viewport'a göre hesapladığı için sayfa 1200 CSS px genişliğinde, 1:1 oranda kalıyor; ekrana sığmıyor, yatay kaydırma gerekiyordu.
- Mobil varsayılana dönüldüğünde de geniş tablo/görsellerin sayfayı yatayda taşırdığı durumlar için bir düzeltme katmanı yoktu.

### Yapılanlar — yeni `app/src/main/assets/wiki/page-fit.js`
- **Viewport politikası**
  - Mobil: sayfanın **kendi** viewport'u korunur. Yalnızca `width=NNN` (NNN > ekran) dayatan belgeler `width=device-width, initial-scale=1`'e çevrilir.
  - Masaüstü: `width=1200` + `initial-scale = screenWidth/1200` ile 1200 px düzen cihaz genişliğine **ölçeklenir** (1:1 taşma yok).
- **Sığdırma**
  - Sayfa gerçekten taşıyorsa (`scrollWidth > viewport + 2`) önce **yalnızca taşan** `img/video/iframe/canvas/picture/figure/table/pre` öğeleri işaretlenir ve `max-width:100%` ile küçültülür.
  - Hâlâ taşıyorsa makale kökünün **yalnızca geniş olan** doğrudan çocukları `overflow-x:auto` ile kendi içinde kaydırılabilir yapılır.
  - `overflow-x: hidden` **kullanılmaz**; içerik asla kırpılmaz. Metin düğümleri, bağlantılar, nitelikler ve site betikleri değiştirilmez.
- **Yeniden ölçüm:** `resize`, `orientationchange`, `window.load`, yakalanan kaynak `load` olayları ve çeviri yazımlarından gelen `kitsugi:content-changed` olayı ile (gecikmeli) yeniden ölçülür. Kaydırma konumu korunur.
- `WikiPageEnhancements.applyFit()` katmanı `onPageCommitVisible`/`onPageFinished` anında (belge görünür olur olmaz) uygular; `onPageFinished` içindeki geç viewport enjeksiyonu kaldırıldı, `DESKTOP_VIEWPORT_SCRIPT` sabiti silindi.
- `WikiPageAssets` yeni `fit` alanıyla `page-fit.js`'i yükler (tek seferlik, IO thread'inde).

---

## 3) Otomatik çeviri hızı

### Kök nedenler
1. **Sıralı istek döngüsü:** `take → translate (ağ) → apply → delay(120)`; tek seferde tek parti. Sayfa hızı doğrudan ağ gecikmesine bağlıydı (≈8 düğüm / RTT+120 ms).
2. **Küçük partiler:** DOM köprüsü tur başına en fazla 8 düğüm / 1000 karakter veriyordu.
3. **Tekrarlanan metinler:** Aynı metin her düğüm için yeniden isteniyordu.
4. **Kaydırma sırasında durma:** Her `scroll` olayı `rescan = true` yapıp TreeWalker'ı baştan başlatıyordu; ayrıca `apply` son 250 ms'de girdi varsa sürekli erteleniyordu.
5. **Kalıcı "bekliyor" durumu:** `apply` sırasında rev/metin uyuşmazlığında düğümün `pending` işareti temizlenmiyordu; o metin bir daha hiç istenmiyordu.
6. **Zaman aşımı tüm hattı öldürüyordu:** Tek bir evaluate/istek hatası `enhancementError` ile tüm çeviriyi durdurabiliyordu.

### Yapılanlar
- **Boru hattı (pipeline):** `WikiPageEnhancements.translatePipeline`
  - Aynı anda **3 parti** uçuşta (`Semaphore(3)`; global 4 izinli çeviri kapısının 3'ü → UI'ya 1 izin kalır).
  - Sonuçlar `Channel` ile bağımsız bir "applier" coroutine'ine akar; hazır olan sonuç **hemen** yazılır, sıra beklenmez.
  - Parti hatası yalnızca o partiyi etkiler (`null` sonuç → JS 5 sn sonra yeniden dener).
  - `take()` zaman aşımında hat durmaz; kısa bekleyip devam eder.
  - `apply()` zaman aşımında `release(id)` ile "bekliyor" işaretleri temizlenir.
  - Boşta 450 ms, tarama sürerken 90 ms yoklama; kozmetik reklam JS'i saniyede en fazla 1 kez çağrılır.
- **DOM köprüsü (`dom-translator.js`)**
  - Parti: 8 → **12 düğüm**, 1000 → **1200 karakter**, tarama adımı 500 → 700.
  - `scroll`/`wheel`/`touchmove`: anında `rescan` yerine **gecikmeli** (180–240 ms) yeniden tarama → walker kaldığı yerden ilerler, uzun sayfalarda çeviri durmaz.
  - `apply` erteleme penceresi 250 → **120 ms**; yazma sonrası `kitsugi:content-changed` yayınlanır (düzen katmanı yeniden ölçer).
  - Rev/metin uyuşmazlığında **`pending` her durumda temizlenir** → metin tekrar istenebilir.
  - Makale kökü için ek yedekler (`.page-content`, `article`, `main`, `#content`, `body`) → mobil ciltte çeviri köksüz kalmaz.
- **Tekrar eden metin:** `WikiDomTranslationBatch.translate` aynı metni **bir kez** çevirir, sonucu tüm eşleşen düğümlere dağıtır (`MAX_ROWS = 12`, işaret doğrulaması korunuyor).

### Beklenen etki
Öncesi ≈ `1 parti / (RTT + 120 ms)` (≈2–3 parti/sn). Sonrası: 3 paralel parti + %50 daha büyük partiler + tekrar eleme ⇒ **yaklaşık 4–5 kat** çeviri hızı; kaydırma sırasında çeviri akmaya devam eder. Sonuçlar DOM'a yine kaydırma konumu korunarak ve parça parça yazılır (kasma yok).

---

## Dosya listesi

| Dosya | Değişiklik |
|---|---|
| `app/src/main/assets/wiki/page-fit.js` | **Yeni** — viewport + ekrana sığdırma katmanı |
| `app/src/main/assets/wiki/dom-translator.js` | 12'lik parti, gecikmeli rescan, 120 ms erteleme, pending temizliği, kök yedekleri, content-changed |
| `app/src/main/assets/wiki/NOTICE.md` | Yeni varlık bildirimi |
| `WikiPageEnhancements.kt` | Paralel çeviri boru hattı, `applyFit`, hata izolasyonu, varlık yükleme |
| `WikiDomTranslationBatch.kt` | 12 satır sınırı + tekrar eden metin tek istek |
| `WikiOriginalPage.kt` | Oturum varsayılanı mobil, geç viewport enjeksiyonunun kaldırılması |
| `WikiOriginalPagePolicy.kt` | Kullanılmayan `DESKTOP_VIEWPORT_SCRIPT` kaldırıldı |
| `FandomWikiReader.kt` | Kalıcı düzen tercihi + son seçimi açma |
| `AppSettings.kt`, `SettingsDataStore.kt`, `KitsugiContentPrefs.kt` | `wikiDesktopLayout` ayarı |
| `app/build.gradle.kts` | Sürüm 2.4.341 |
| `WikiPageToolsTest.kt` | Tekrar eleme ve parti sınırı testleri |

## Doğrulama notları
- `dom-translator.js` ve `page-fit.js` gerçek bir DOM ortamında (jsdom) senaryo testinden geçirildi: parti sınırları, çift istememe, yerinde çeviri, orijinali geri yükleme, önbellekten anında geri gelme, kaydırma sonrası devam, tüm sayfanın tükenmesi, viewport politikası ve "yalnızca taşan öğeyi işaretleme" davranışları doğrulandı.
- Gradle derlemesi bu ortamda (JDK/Android SDK yok) çalıştırılamadı; Kotlin tarafı derleme öncesi gözden geçirildi.
