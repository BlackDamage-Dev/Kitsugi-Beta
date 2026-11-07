# ÇÖZÜM RAPORU — Manga Keşfet kaydırma akışkanlığı + okuyucu panel aç/kapa + hızlı parlaklık

**Tarih:** 2026-11-07
**Kapsam:** `ui/screens/manga/MangaBrowseScreen.kt`, `ui/screens/manga/MangaReaderScreen.kt`,
`ui/screens/manga/reader/MangaReaderOverlays.kt`, `ui/screens/manga/reader/MangaReaderShortcutPolicy.kt`,
`res/values*/manga_reader.xml`, `MangaReaderRegressionCases.kt`

---

## 1) Manga Keşfet — başlık artık kaydırmayla birlikte akıyor

### Kök neden
Eski düzen başlığı **overlay** olarak tutuyor ve kaydırma deltasını
`MangaHideOnScrollConnection.onPreScroll` içinde **tüketiyordu**:

* Başlık 188 dp yukarı kayarken ızgara hiç kaymıyordu → kartlar ve kaynak çipleri
  başlıktan bağımsız "yerinde kalıyor", başlık bitince liste **aniden zıplıyordu**.
* Geri kaydırırken başlık yalnızca liste **en tepedeyken** sıfırlanıyordu
  (`firstVisibleItemIndex == 0 && offset < 4`) → his: "yok olup geri geliyor".
* Yapışkan çubuk başlıkla aynı `translationY`'yi paylaşıyor, başlık yüksekliği
  `topContentPadding` üzerinden her karede yeniden hesaplanıyordu.

### Yeni yaklaşım (tek kaydırma ekseni)
1. Üst blok (üst bar + arama paneli + sıralama + kimlik doğrulama bandı) artık
   `LazyVerticalGrid`'in **0. öğesi** (`span = cols`). Dolayısıyla kaydırma tek
   kaynaktan gelir: **kartlar, çipler ve başlık parmakla birebir birlikte akar**.
2. Yapışkan kaynak çubuğu ızgaranın overlay'i olarak kalır, fakat konumu artık
   başlığın **ölçülen** konumundan türetilir:
   `chipsTop = headerTop + headerHeight − gridTop`, `0`'a kenetlenir.
   Konum `Modifier.offset { }` (layout fazı) içinde okunur → **kaydırma sırasında
   recomposition yok**; çubuk ızgaradan sonra ölçüldüğü için aynı karede günceldir.
3. Başlık ile ilk kart sırası arasına çubuk yüksekliğinde bir boşluk öğesi eklendi
   (`çubuk yüksekliği − 14 dp`, ızgara dikey aralığı 14 dp olduğu için) → kartlar
   çubuğun **tam altında** başlar, çakışma olmaz.
4. Başlık zemini (okunurluk perdesi) tam genişlikte ayrı bir katmana taşındı ve
   `zIndex(-1)` ile ızgaranın **altında** çiziliyor: ızgara öğesinin 12 dp yatay iç
   boşluğu kenarlarda iz bırakmıyor, katman ızgaradan sonra bildirildiği için
   konumu gecikmesiz.
5. Yumuşaklık: başlık yukarı kaydıkça içerik `%35`, perde `%25` soluyor; yapışkan
   çubuğun alfa/gölgesi `KitsugiMotion` süreleriyle animasyonlu (değişmedi).
6. `revealHeader` / `changeSearchName` artık `animateScrollToItem(0)` kullanıyor;
   arama alanına odak `requestFocusAfterFrames` ile güvenli isteniyor (başlık
   ızgara öğesi olduğu için önce kompoze edilmesi gerekiyor).
7. `MangaHideOnScrollConnection`, `headerOffsetPx`, `collapsibleHeightPx`,
   `topContentPadding` ve ilgili importlar kaldırıldı.

---

## 2) Okuyucu — alttan açılan paneli açan tuş, paneli tekrar kapatıyor

* `reader/MangaReaderShortcutPolicy.kt`
  * Yeni `enum class ReaderPanel { Settings, Chapters, GoToPage, Shortcuts }`.
  * `ReaderKeyContext.openPanel` alanı (varsayılan `null` → eski davranış bozulmaz).
  * Politika: panel açıkken **yalnızca onu açan tuş** eylem üretir
    (`ReaderKey.panel()` eşlemesi); diğer tuşlar panele bırakılır.
* `reader/MangaReaderOverlays.kt`
  * `ReaderPanel` composable'ına `toggleKeys` eklendi; ön tuş (preview) aşamasında
    yakalandığı için içerideki sürgü/buton odağı ne olursa olsun çalışır.
  * Paneller: Ayarlar → `S`, Bölümler → `C`, Sayfaya git → `G` (diyaloğun kendi
    ön tuş dinleyicisinde), Kısayollar → `H` / `?` / `F1`.
  * Kısayollar panelinde "aynı tuş kapatır" bilgi satırı (`reader_keys_toggle`).
* `MangaReaderScreen.kt`
  * `open()` korunuyor (düğmeler), yeni `toggle()` tuş yolu için kullanılıyor:
    `Settings`, `Chapters`, `GoToPage`, `Help` artık **aç/kapa**.
  * `openPanel` durumu tuş bağlamına geçiriliyor (TV/dialog penceresi tuşu
    okuyucuya ulaştırdığında da doğru davranış).
* Regresyon: `MangaReaderRegressionCases` içine
  "a panel's own shortcut closes it, other keys stay with the panel" testi eklendi
  (kendi tuşu → eylem, diğer tuşlar → tüketilmez, keyUp → eylem yok).

---

## 3) Okuyucu — alt çubukta açılır/kapanır hızlı parlaklık şeridi

* Alt çubuktaki aksiyon sırasına **parlaklık** düğmesi eklendi
  (`Icons.Default.BrightnessMedium`). Kısayol tuşu olmadığı için
  `ReaderToolbarButton` artık boş `key` değerinde etiketi sade yazıyor.
* Düğme, alt çubuğun **üst kenarında** `AnimatedVisibility` ile açılan şeridi açar:
  `expandVertically(240ms) + fadeIn(200ms)` / `shrinkVertically(180ms) + fadeOut(140ms)`.
* Şerit içeriği: `BrightnessLow` ikon → sürgü (%10–%100) → `%` etiketi →
  "Parlaklığı %100'e döndür" düğmesi → `BrightnessHigh` ikon.
* **Ayarlar panelindeki `Parlaklık` sürgüsü aynen korunuyor** (kullanıcı isteği):
  aynı `ui.customBrightness` / `model.setCustomBrightness` durumunu paylaşırlar.
* Kontroller gizlendiğinde veya herhangi bir panel/dialog açıldığında şerit
  kendiliğinden kapanır (`LaunchedEffect(overlayOpen, menuVisible)`).

---

## Doğrulama
> Bu ortamda Android SDK/Gradle bulunmadığı için derleme çalıştırılamadı.
> Yapılan kontroller: brace/paren dengesi (tüm dosyalar 0), kullanılan API'lerin
> Compose BOM 2024.12.01 (foundation 1.7 / material3 1.3) ile uyumu, kaldırılan
> sembollerin başka referansı olmadığı (`grep`) ve kısayol politikası regresyon testi.

1. Manga Keşfet → aşağı/yukarı hızlıca kaydır: başlık + çipler + kartlar tek parça.
2. Başlık tamamen çıkınca çipler tepede sabit; yukarı kaydırınca başlık geri gelir.
3. Okuyucuda `S` → panel açılır, `S` → kapanır. `C`, `G`, `H` için aynı.
   Panel açıkken `M`/`F` gibi diğer tuşlar panele bırakılır (çalınmaz).
4. Alt çubukta parlaklık düğmesi → şerit yumuşakça açılır; sürgü %100 → sıfırla.
5. Ayarlar → `Parlaklık` hâlâ yerinde ve iki kontrol birbirini yansıtıyor.
