# PLAN_TASK_WIKI_MOBIL_SIGDIRMA_CEVIRI_HIZI_2026-10-22

## 📌 Görev Özeti
Özgün wiki açılır sayfasında (WebView okuyucu) üç sorunun kökten çözülmesi:
1. Sayfa **varsayılan olarak mobil** açılmalı, kullanıcı masaüstüne geçerse **son seçim hatırlanmalı**.
2. Sayfa kendini **otomatik olarak ekrana sığdırmalı** (yatay kaydırma olmadan).
3. Otomatik çeviri bu tarz sayfalarda **çok yavaş**; takılmadan ve hata verdirmeden hızlandırılmalı.

**Sürüm:** 2.4.341 · **Branch:** `arena/e7dffb01-kitsugi-beta` (aktif çalışma dalı) · **Tarih:** 2026-10-22

---

## 🐛 Tespit Edilen Sorunlar

### 1. Varsayılan düzen masaüstü ve seçim kayboluyor
- **Kök neden:** `WikiOriginalSession` içinde `var desktop by mutableStateOf(true)`.
  Her yeni oturum (yeni wiki sayfası, yeni sekme, okuyucunun yeniden açılması) **masaüstü user-agent + masaüstü düzeni** ile başlıyordu.
- Menüdeki "Masaüstü düzeni / Mobil düzen" seçimi hiçbir yere yazılmıyordu → sayfa değişince tercih sıfırlanıyordu.
- Kullanıcı deneyimi: telefon ekranında 1200 px'lik masaüstü düzeni, minicik yazı, sürekli yatay kaydırma.

### 2. Sayfa ekrana sığmıyor
- **Kök neden:** Masaüstü viewport meta'sı **`onPageFinished`** (sayfa yüklendikten sonra) `width=1200` yapılıyordu.
  WebView ilk ölçeklemeyi eski viewport'a göre hesapladığı için belge 1200 CSS px genişliğinde **1:1** kalıyor; `initial-scale` verilmediği için ekrana sığmıyordu.
- Mobil varsayılana geçildiğinde ise geniş tablo/görsellerin sayfayı yatayda taşırdığı durumlar için hiçbir düzeltme katmanı yoktu.

### 3. Otomatik çeviri yavaş (ve bazen takılıyor)
- **Sıralı döngü:** `take → translate (ağ) → apply → delay(120)`; aynı anda **tek** parti. Hız doğrudan ağ gecikmesine bağlıydı.
- **Küçük partiler:** DOM köprüsü tur başına en fazla 8 düğüm / 1000 karakter veriyordu.
- **Tekrar eden metin:** Aynı metin her düğüm için **yeniden** çevriliyordu (gezinme etiketleri, tekrar eden tablo satırları).
- **Kaydırmada durma:** Her `scroll` olayında `rescan = true` → TreeWalker her karede **belgenin başından** yeniden başlıyordu; uzun sayfalarda çeviri fiilen duruyordu.
- **Kalıcı "pending":** `apply` sırasında rev/metin uyuşmazlığında düğümün `pending` işareti temizlenmiyordu → o metin bir daha hiç istenmiyordu (çevrilmemiş metin kalıyordu).
- **Tek hata tüm hattı düşürüyordu:** Bir evaluate/istek zaman aşımı `enhancementError` ile tüm çeviriyi durdurabiliyordu.

---

## ✅ Çözüm Planı ve Uygulama

### Görev 1 — Mobil varsayılan + kalıcı düzen tercihi
| # | Dosya | Değişiklik |
|---|---|---|
| 1.1 | `WikiOriginalPage.kt` | `WikiOriginalSession(initialUrl, readerLocation, initialDesktop = false)`; `desktop` bu parametreyle başlar |
| 1.2 | `AppSettings.kt` | `IntegrationsAndNotificationsSettings.wikiDesktopLayout = false` + `AppSettings.wikiDesktopLayout` erişimcisi |
| 1.3 | `SettingsDataStore.kt` | `Keys.WikiDesktopLayout = booleanPreferencesKey("wiki_desktop_layout")`, okuma + `setWikiDesktopLayout(enabled)` |
| 1.4 | `KitsugiContentPrefs.kt` | Senkron okunabilen `wikiDesktopLayout` önbelleği (oturumlar bileşim sırasında oluşur, DataStore beklenemez) |
| 1.5 | `FandomWikiReader.kt` | Yeni oturumlar `KitsugiContentPrefs.wikiDesktopLayout` ile başlar; menüdeki geçiş `setWikiDesktopLayout` ile **kalıcı** yazılır; yeni sekme kopyası düzeni taşır |

### Görev 2 — Ekrana sığdırma
| # | Dosya | Değişiklik |
|---|---|---|
| 2.1 | **`assets/wiki/page-fit.js` (YENİ)** | Viewport politikası + taşma düzeltme katmanı |
| 2.2 | `WikiPageEnhancements.kt` | `WikiPageAssets.fit` + `applyFit()` (`onPageCommitVisible`/`onPageFinished` anında) |
| 2.3 | `WikiOriginalPage.kt` | Geç viewport enjeksiyonu kaldırıldı (çift uygulama yok) |
| 2.4 | `WikiOriginalPagePolicy.kt` | Kullanılmayan `DESKTOP_VIEWPORT_SCRIPT` silindi |

**page-fit.js davranışı**
- **Mobil:** sitenin **kendi** viewport'u korunur; yalnızca `width=NNN` (NNN > ekran) dayatan belgeler `width=device-width, initial-scale=1`'e çevrilir.
- **Masaüstü:** `width=1200` + `initial-scale = screenWidth/1200` → 1200 px düzen cihaz genişliğine **ölçeklenir**.
- **Taşma:** `scrollWidth > viewport + 2` ise önce **yalnızca taşan** `img/video/iframe/canvas/picture/figure/table/pre` işaretlenir (`max-width:100%`); hâlâ taşıyorsa makale kökünün **yalnızca geniş** doğrudan çocukları `overflow-x:auto` olur.
- `overflow-x: hidden` **kullanılmaz** (içerik kırpılmaz). Metin düğümleri, bağlantılar, nitelikler ve site betikleri **değiştirilmez**.
- `resize`, `orientationchange`, `window.load`, kaynak `load` olayları ve çeviriden gelen `kitsugi:content-changed` ile yeniden ölçüm; kaydırma konumu korunur.

### Görev 3 — Çeviri hızı
| # | Dosya | Değişiklik |
|---|---|---|
| 3.1 | `WikiPageEnhancements.kt` | **Paralel boru hattı:** `Semaphore(3)` ile 3 parti uçuşta, sonuçlar `Channel` üzerinden bağımsız "applier" coroutine'ine akar |
| 3.2 | `WikiPageEnhancements.kt` | Hata izolasyonu: parti hatası sadece o partiyi etkiler; `take()` zaman aşımında hat durmaz; `apply()` zaman aşımında `release(id)` ile pending temizlenir |
| 3.3 | `WikiPageEnhancements.kt` | Boşta 450 ms / tarama sürerken 90 ms yoklama; kozmetik reklam JS'i saniyede en fazla 1 kez |
| 3.4 | `dom-translator.js` | Parti **8 → 12 düğüm**, 1000 → 1200 karakter; tarama adımı 500 → 700 |
| 3.5 | `dom-translator.js` | `scroll/wheel/touchmove` → **gecikmeli** rescan (180–240 ms); walker kaldığı yerden ilerler |
| 3.6 | `dom-translator.js` | `apply` erteleme penceresi 250 → **120 ms**; yazma sonrası `kitsugi:content-changed` |
| 3.7 | `dom-translator.js` | Rev/metin uyuşmazlığında **`pending` her durumda temizlenir** |
| 3.8 | `dom-translator.js` | Makale kökü yedekleri (`.page-content`, `article`, `main`, `#content`, `body`) |
| 3.9 | `WikiDomTranslationBatch.kt` | `MAX_ROWS = 12`; **tekrar eden metin tek istekte** çevrilip tüm düğümlere dağıtılır |

---

## 🧪 Kabul Kriterleri

- [x] Wiki sayfası ilk açılışta **mobil** düzende ve okunaklı yazı boyutunda açılır.
- [x] Menüden masaüstüne geçilince **yeni sekmeler / yeni sayfalar / uygulama yeniden başlatma** masaüstünde açılır; mobil seçimi de aynı şekilde hatırlanır.
- [x] Masaüstü düzeninde sayfa 1:1 taşmaz, ölçeklenerek ekrana sığar.
- [x] Geniş görsel/tablo sayfayı yatayda sürüklemez; içerik kırpılmaz.
- [x] Çeviri kaydırma sırasında durmaz; çevrilmemiş metin "pending" tuzağında kalmaz.
- [x] Tek bir ağ hatası/zaman aşımı çeviriyi tamamen durdurmaz.
- [x] `assembleFossDebug/Release` derlemesi ve DEX doğrulaması başarılı (VerifyError = 0).

## 🔬 Doğrulama
- `dom-translator.js` ve `page-fit.js`, jsdom üzerinde senaryo testleriyle doğrulandı (parti sınırı, çift istememe, yerinde çeviri, orijinali geri yükleme, önbellekten anında geri gelme, kaydırma sonrası devam, sayfanın tükenmesi, viewport politikası, yalnızca taşan öğenin işaretlenmesi).
- Kotlin birim testleri: `repeatedTextIsTranslatedOnceAndFannedOut`, `batchSizeIsBoundedLikeTheDomBridge` eklendi.
- CI: `🏗️ Android Derleme Doğrulama` → BUILD SUCCESSFUL, `[DEX-OK]` (foss debug + R8 release APK).

## 📦 Etkilenen Dosyalar (13)
```
app/build.gradle.kts
app/src/main/assets/wiki/NOTICE.md
app/src/main/assets/wiki/dom-translator.js
app/src/main/assets/wiki/page-fit.js                        (YENİ)
app/src/main/java/com/kitsugi/animelist/data/settings/AppSettings.kt
app/src/main/java/com/kitsugi/animelist/data/settings/KitsugiContentPrefs.kt
app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomWikiReader.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/WikiDomTranslationBatch.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/WikiOriginalPage.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/WikiOriginalPagePolicy.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/WikiPageEnhancements.kt
app/src/test/java/com/kitsugi/animelist/ui/screens/detail/wiki/WikiPageToolsTest.kt
```

## ⚠️ Riskler / Geri Alma
- `pending` temizliği ve boru hattı tamamen JS↔Kotlin veri sözleşmesi içinde; native API yüzeyi **değişmedi** (yeni `JavascriptInterface` yok).
- `page-fit.js` yalnızca **gerektiğinde** stil enjekte eder; sayfa zaten sığıyorsa hiçbir şey eklemez.
- Geri alma: `git revert <commit>` — `page-fit.js` kullanılmadığında sistem eski `onPageFinished` viewport davranışına döner.
