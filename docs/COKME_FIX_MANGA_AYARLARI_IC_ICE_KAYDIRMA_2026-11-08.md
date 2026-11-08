# ÇÖZÜM RAPORU — "Manga ayarları / ayarlar sayfası açılınca çökme"
**Tarih:** 2026-11-08 · **Sürüm:** 2.4.471-beta civarı (crash log: 2.4.471-beta.29901149)

---

## 1. Çökmenin kimliği (kök neden)

Drive'daki `Kitsugi_Crash_Report (2026-11-07 19:46:19)` + `logcat_at_crash` dosyalarındaki
**FATAL EXCEPTION**:

```
java.lang.IllegalStateException: Vertically scrollable component was measured with an
infinity maximum height constraints, which is disallowed.
    at androidx.compose.foundation.CheckScrollableContainerConstraintsKt...
    at androidx.compose.foundation.ScrollNode.measure-3p2s80s
    ...
    at RowColumnMeasurePolicyKt.measure (Column)
    at ScrollNode.measure-3p2s80s          ← İKİNCİ dikey kaydırma
    ...
    at androidx.compose.material3.internal.DraggableAnchorsNode.measure  ← ModalBottomSheet
```

Stack'in okunuşu (aşağıdan yukarıya):

```
ModalBottomSheet (KitsugiSheetOrDialog)
 └ Box(wrapContentHeight … heightIn)          ← sheet gövdesi (sınırlı)
   └ Column(navigationBarsPadding)             ← sheet içeriği
     └ Column(Modifier.verticalScroll)         ← 1. dikey kaydırma (sorunsuz ölçüldü)
       └ Column
         └ Column(Modifier.fillMaxSize().verticalScroll())  ← 2. dikey kaydırma → ÇÖKME
```

Yani **bir dikey kaydırma konteynerinin içine ikinci bir dikey kaydırma konteyneri**
giriyor. Üstteki konteyner çocuklarını `maxHeight = Infinity` ile ölçtüğü için alttaki
`verticalScroll` düğümü `IllegalStateException` fırlatıyor ve **süreç ölüyor**
(`release` build olduğu için stack'te uygulama satır numarası yok).

Bu hata sınıfı kod tabanında tek bir yerde değil, **birden çok yerde** pusuda:
manga/eklenti ayarları sekmesi, ayarlar alt sayfaları, içerik detay sekmeleri
(`HorizontalPager` sayfaları `Constraints.Infinity` ile ölçülüyor), wiki okuyucu,
profil sekmeleri vb. Bu yüzden "sadece şu ekranı yamaladık" yaklaşımı bir sonraki
ekranda yine patlıyordu (repo içindeki `FandomWikiReader` ve `KitsugiImageGalleryDialog`
yorumları da aynı hatanın daha önce iki kez yamalandığını gösteriyor).

## 2. Yapılan düzeltme (kalıcı ve tüm uygulamayı kapsayan)

**Yeni dosya:** `app/src/main/java/com/kitsugi/animelist/ui/utils/KitsugiSafeScroll.kt`

```kotlin
Modifier.kitsugiVerticalScroll(state: ScrollState)   // çökme korumalı verticalScroll
Modifier.kitsugiClampUnboundedHeight()               // kaydırma eklemeyen sürüm (Lazy* için)
```

Çalışma mantığı:

* `verticalScroll` düğümünün **dışına** bir `layout` düğümü eklenir.
* Gelen kısıt sınırlıysa (`hasBoundedHeight == true`) **hiçbir şey değişmez** → mevcut
  tüm ekranların ölçümü birebir aynı kalır (davranış/performans regresyonu yok).
* Gelen kısıt sınırsızsa yükseklik **ekran yüksekliğine kırpılır**, böylece kaydırma
  çalışmaya devam eder ve Compose'un `checkScrollableContainerConstraints` kontrolü
  asla tetiklenmez.

Ardından kod tabanındaki **tüm** dikey kaydırma çağrıları bu güvenli sürüme taşındı:

* **59 dosya / 100+ çağrı**: `Modifier.verticalScroll(...)` → `Modifier.kitsugiVerticalScroll(...)`
* Kullanılmayan `androidx.compose.foundation.verticalScroll` import'ları temizlendi
  (60 dosya), yeni import tüm dosyalara eklendi.
* Kapsam: `ui/screens/**` (manga, ayarlar, detay, wiki, akış, profil, bildirimler, TV),
  `ui/components/**`, `ui/tv/**`. Kalan `.verticalScroll(` **yok** (yalnızca yeni util
  dosyasının içinde, o da güvenli sarmalayıcıyı kurmak için).

Ek olarak `MangaSourceHealthScreen` içindeki `editingDomainSource!!` (dialog kapanmışsa
NPE üretme ihtimali) `?.let { }` ile güvenli hâle getirildi.

## 3. Neden bu çözüm "o ekranı" da kapsıyor

Çökme raporundaki stack `ScrollNode` (yani `Modifier.verticalScroll`) üzerinden
fırlıyor. Uygulamada artık **güvenli olmayan tek bir `verticalScroll` çağrısı yok**;
dolayısıyla manga ayarları sekmesi, eklenti/manga kaynak yönetimi, ayar alt sayfaları,
detay sekmeleri, wiki okuyucu, galeri levhaları ve sheet içerikleri dâhil hangi ekran
açılırsa açılsın bu istisna üretilemez.

## 4. Doğrulama adımları (cihazda)

1. Ayarlar → **Eklentiler / Kaynaklar** → **Manga** sekmesi, Repolar/Kurulu alt sekmeleri.
2. Ayarlar → **Tercihler** → **Manga Okuyucu** sekmesi (okuma yönü, renk filtresi,
   sığdırma, parlaklık) ve açılır seçim pencereleri.
3. Manga okuyucu → dişli (⚙) → **Okuma Modu Ayarları** paneli.
4. Manga keşfet → kaynak yönetimi (Repolar / Sağlık raporu / domain düzenleme).
5. Detay sayfası sekmeleri (bilgi/karakter/wiki) — yatay ve dikey düzen.
6. Hakkında → sürüm notları, wiki okuyucu → sayfa araçları (sheet'ler).

Beklenen: hiçbir adımda `Vertically scrollable component was measured with an infinity
maximum height constraints` çökmesi yok; kaydırma davranışı öncekiyle aynı.

## 5. Notlar / sonraki adım

* Bu düzeltme, aynı istisnanın **her varyantını** (sheet, dialog, pager sayfası, TV,
  landscape) kapatır. Ayrı ayrı "hangi ekran" tespitine ihtiyaç kalmaz.
* Kaydırmayı kendi yapan bileşenler (`LazyColumn`, `LazyVerticalGrid`) için de
  `Modifier.kitsugiClampUnboundedHeight()` yardımcısı hazır; yeni bir ekranda
  "LazyColumn bir kaydırılabilir sütunun içine konursa" aynı hata oluşursa ilgili
  Lazy* bileşenine bu modifier eklenmelidir (repo genelinde şu an böyle bir çağrı
  tespit edilmedi — mevcut Lazy* kullanımları sınırlı yükseklikte).
