# Manga Okuyucu — Tam Ekran + Okuma Deneyimi Kök Düzeltme Raporu

**Tarih:** 2026-11-05
**Kapsam:** Manga okuyucu ekranı (`MangaReaderScreen`), sayfa bileşenleri, okuyucu ayarları
**Durum:** Kod tamam — cihazda `assembleDebug` ile derlenip doğrulanması gerekiyor (bu ortamda Android SDK/Gradle yok)

---

## 1. Şikayet (kullanıcı raporu)

1. "Tam ekran butonuna basıyorum, tam ekran olup alt/üst bar gizlenmiyor."
2. Manga sayfaları **aynalanmış** (ters) görünüyor — konuşma balonları ve başlık yazıları tersten okunuyor.
3. "Bu kısmın desteklediği her şeyi ele al, duplicate olan kısımları temizle, eksik güzel özellikleri ekle."

Ekran görüntülerindeki durum: durum çubuğu (`16:19 5 Kas Prş`) ve alttaki görev/gezinme çubuğu **hiç gizlenmiyor**, üstte uygulamanın kendi başlık barı, altta sayfa şeridi + 4 ikonlu araç çubuğu sürekli açık kalıyor.

---

## 2. Bulunan kök nedenler (kanıtlarıyla)

### 2.1 "Tam ekran" butonu aslında tam ekran değildi
Alt bardaki 3. ikon `Icons.Default.CropFree` idi ve yalnızca **sığdırma modunu** (FitScreen → FitWidth → FitHeight) döndürüyordu. Repoda tam ekran için hiçbir sistem-çubuğu API'si kullanılmıyordu:

```
grep -rn "hide(WindowInsets|WindowInsetsControllerCompat|setDecorFitsSystemWindows" app/src/main/java
→ yalnızca KitsugiSplashActivity'de eski systemUiVisibility bayrakları
```

Yani kullanıcı "tam ekran" sandığı butona basınca yalnızca görsel ölçek değişiyor, sistem çubukları yerinde kalıyordu.

### 2.2 Uygulamanın kendi barları da güvenilir gizlenmiyordu
Barlar yalnızca "ekranın ortasına dokun" ile kapanıyordu. Dokunma algılama `detectTapGestures` ile **ebeveyn** `Box` üzerindeydi, ama çocuk katmanda Telephoto'nun `ZoomableAsyncImage`'ı var. Compose'da alt/çocuk katman dokunuşu tükettiğinde ebeveynin `detectTapGestures` çağrısı olayı hiç görmez → merkeze dokunma çoğu zaman hiçbir şey yapmıyordu.

### 2.3 "Dikey" okuma modu yatay çalışıyordu
```kotlin
when (readingMode) {
    ReadingMode.Webtoon -> MangaWebtoonReader(...)
    else -> MangaPagerReader(...)   // Vertical de buraya düşüyordu
}
```
`MangaPagerReader` yalnızca `HorizontalPager` çiziyor; `ReadingMode.Vertical` seçildiğinde görsel olarak **hiçbir şey değişmiyordu**.

### 2.4 Ayar kimlikleri uyuşmuyordu → seçimler sessizce yok sayılıyordu
| Yazan yer (`KitsugiPreferencesSettingsDialog`) | Okuyan yer (`MangaReaderViewModel`) |
|---|---|
| `GrayScale` | `Grayscale` |
| `Inverted` | `Invert` |
| `Warm`, `Cool` | (karşılığı yok) |
| `WebtoonZoom` | `Webtoon` |

Sonuç: "Gri Ton", "Ters Çevir", "Sıcak", "Soğuk" ve "Webtoon Zoom" seçenekleri seçildiğinde değer `Normal` / `RightToLeft`'e düşüyor, kullanıcıya hata da gösterilmiyordu.

### 2.5 İlerleme (son kaldığın sayfa) hataları
- Pager'daki sayfa değişimini dinleyen `LaunchedEffect(pagerState.currentPage)` **webtoon modunda da** çalışıyordu → webtoon'da yanlış sayfa kaydediliyordu.
- Pager/scroll durumu bölüm değişiminde sıfırlanmıyordu; `rememberPagerState(initialPage = ...)` yalnızca ilk kurulumda dikkate alınır.
- Kayıt 500 ms debounce edildiği için kullanıcı son sayfada okuyucudan hızlıca çıkarsa **son sayfa kaydedilmiyordu**.
- İlk kare çizilir çizilmez "sayfa 0" kaydedilip kayıtlı konum ezilebiliyordu.

### 2.6 Webtoon yerleşimi
Sayfa bileşeni her modda `Modifier.fillMaxSize()` kullanıyordu; `LazyColumn` içinde yükseklik sınırsız olduğu için görsel boyutu çözümlenemiyor, yüklenme yer tutucusu ise sabit `480.dp` olduğu için sayfa yüklenince kaydırma sıçrıyordu.

### 2.7 Kopya / ölü kod
- `data/manga/loader/MangaPageLoader.kt` (v1, 258 satır) — hiçbir yerden kullanılmıyor (yalnızca yorumlarda adı geçiyordu).
- İki `MangaViewModel`: `ui/app/MangaViewModel` (canlı) + `ui/screens/manga/MangaViewModel` (ölü).
- `ui/screens/manga/MangaScreen.kt` (≈600 satır) — yalnızca `AppRoot.kt`'de kullanılmayan bir import'u vardı; hiç çağrılmıyordu.
- `MangaReaderScreen` içinde aynı `FilterChip` bloğu **3 kez** kopyalanmıştı.
- `Context.findActivity()` üç ayrı dosyada kopyala-yapıştır duruyordu.
- Ölü `private fun ReadingMode.label()` (hiç çağrılmıyordu).
- Webtoon'da `loader.loadPage()` iki kez tetikleniyordu (hem reader hem sayfa bileşeni).

### 2.8 Parlaklık sürgüsü
Her sürgü adımı ayrı bir DataStore yazımı tetikliyor, UI değeri ise yalnızca DataStore'dan geri okunduğu için gecikmeli güncelleniyordu (sürgü takılması).

---

## 3. Yapılanlar

### 3.1 Gerçek tam ekran (immersive)
**Yeni:** `ui/screens/manga/components/MangaReaderSystemUi.kt`
- `WindowInsetsControllerCompat.hide(WindowInsetsCompat.Type.systemBars())` → durum çubuğu + gezinme çubuğu + tablet görev çubuğu gizlenir.
- `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` → kenardan kaydırınca çubuklar geçici görünür, kalıcı açılmaz.
- Okuyucudan çıkışta (`onDispose`) çubuklar **geri getirilir** → tam ekran başka ekranlara yapışıp kalmaz.
- `ON_RESUME` ve ayar paneli açılıp kapanınca gizleme yeniden uygulanır.
- `FLAG_KEEP_SCREEN_ON` ile okuma boyunca ekran açık tutulur (kapatılabilir).
- Üst bar `statusBarsPadding()` kullanır: çubuklar gizliyken boşluk kalmaz, görünürken içerik çubuğun altına girmez.

### 3.2 Tam ekran butonu
Üst barda ve alt barda **ayrı** tam ekran düğmesi (`Fullscreen` / `FullscreenExit` ikonu, duruma göre). Basınca:
- sistem çubukları gizlenir/gösterilir,
- uygulamanın kendi barları da gizlenir ("tam ekran" beklentisi),
- sığdırma modu artık ayrı bir ikon olarak kalır (eskiden ikisi karışıyordu).

### 3.3 Dikey sayfalama modu
**Yeni:** `components/MangaVerticalPagerReader.kt` — `VerticalPager` ile gerçek dikey sayfa geçişi (her ekranda bir sayfa, snap'li). Yatay pager ile aynı `PagerState`'i paylaşır, böylece mod değiştirince sayfa konumu korunur.

### 3.4 Dokunma bölgeleri güvenilir hale getirildi
`MangaPageItem` içinde `detectTapGestures` yerine **Initial pass'te gözlem yapan, olay tüketmeyen** bir dokunma dedektörü (`Modifier.readerTapZones`):
- tek parmak + kaymama + uzun basmama → bölge eylemi (sol/orta/sağ),
- iki parmak veya kaydırma → hiçbir şey (pinch-zoom / pan bozulmaz),
- uzun basma → okuyucu ayarları (haptik geri bildirim).
- RTL'de sol = ileri, LTR/dikey/webtoon'da sağ = ileri; "ters çevir" ile değiştirilebilir.

### 3.5 Ayarlar tek doğruluk kaynağına bağlandı
**Yeni:** `data/manga/MangaSettingIds.kt`
- `Earlier "GrayScale"/"Inverted"/"Warm"/"Cool"/"WebtoonZoom"` gibi eski değerler **geriye dönük uyumlu** okunur (mevcut kullanıcıların ayarları bozulmaz).
- Ayar diyaloğu, okuyucu, TV okuyucusu ve ViewModel aynı sabitleri kullanır.
- Bu düzeltmeyle "Sıcak / Soğuk / Gri Ton / Ters Çevir / Webtoon" seçimleri gerçekten uygulanır.

### 3.6 İlerleme takibi
- Tek yol: her okuyucu kendi sayfa değişimini bildirir (`onPageChanged` → ilerleme + prefetch).
- Konum geri yükleme bölüm/mod değişiminde yeniden çalışır; "sayfa 0 ezme" engellendi (`progressTrackingEnabled`).
- `onCleared()` bekleyen (debounce edilmiş) ilerlemeyi ayrı IO kapsamında **yazar** → son sayfa kaybolmaz.

### 3.7 Webtoon yerleşimi
- Sayfalar `fillMaxWidth().wrapContentHeight()` ile gerçek en-boy oranında çizilir (görsel boyutu kendisi belirler).
- Yer tutucu yükseklik korunur (kaydırma sıçramasını azaltır).
- Sayfa numarası rozeti webtoon'da kapatıldı (her sayfada gürültü yapıyordu).

### 3.8 Diğer düzeltmeler
- Parlaklık: UI anında güncellenir, diske yazma 250 ms debounce edilir.
- Okuma modu / sığdırma / filtre değişimleri **iyimser (optimistic)** güncellenir (DataStore gecikmesi hissedilmez).
- Bölüm değişiminde `loader.resetQueue()` + sayfa listesi sıfırlama.
- Hata ekranı: platform hata kodu açıklaması + "Tekrar Dene".
- Yükleme/hata yer tutucuları pager modunda tam ekran, webtoon modunda sabit yükseklik.

---

## 4. Eklenen özellikler

| Özellik | Nerede | Mihon/Aniyomi karşılığı |
|---|---|---|
| Gerçek tam ekran (sistem çubukları) | üst/alt bar düğmesi + varsayılan tercih | Fullscreen |
| Ekranı açık tut | ayar paneli | Keep screen on |
| Dokunma bölgeleri + ters çevirme | ayar paneli | Tap zones / Invert tap zones |
| Ses tuşlarıyla sayfa | ayar paneli | Volume keys |
| Sayfa numarası rozeti | ayar paneli | Page number |
| Sayfaları aynala (mirror düzeltme) | ayar paneli | (Mihon'da yok — kaynak kaynaklı ters taramalar için) |
| Bölüm sonu geçiş kartı aç/kapat | ayar paneli | Chapter transition |
| Bölüm listesi paneli (bölüm seçici) | üst bar / alt bar | Chapter list |
| Dikey sayfalama | okuma modları | Paged (vertical) |
| Sığdırma: Doldur (kırp) / Orijinal boyut | ayar paneli | Crop / Original |
| Renk filtresi: Sıcak / Soğuk / Sepya | ayar paneli | Color filter |
| Uzun basma → ayarlar | sayfa üzerinde | Long tap actions |
| İkonların erişilebilirlik etiketlerinde mod adı | alt bar | — |

---

## 5. Açık kaynak okuyucu araştırması ve uyarlananlar

Karşılaştırılan kaynaklar:

1. **Mihon (Tachiyomi devamı) — Reader settings**: Fullscreen, Keep screen on, Tap zones (Default / L-shaped / Kindle-ish / Edge / Right-and-left / Disabled) + invert, Volume keys (+invert), Page number, Chapter transition, Scale type (Fit screen / Fit width / Fit height / Original / Smart fit), Crop borders, Split wide pages, Rotate wide pages, Long-tap actions, Background color, Double-tap animation speed. Kaynak: <https://mihon.app/docs/guides/reader-settings>
2. **Aniyomi — Reader settings**: Mihon'ın aynısı + long strip için "Side padding", "Sensitivity for hiding menu on scroll", webtoon double-tap zoom. Kaynak: <https://aniyomi.org/docs/guides/reader-settings>
3. **Mihon: Manga Reader (iOS)**: TV reklamı/özellik listesi — webtoon, paged vertical, LTR/RTL, double-page, auto-scroll, rotation, tap zones, crop & padding. Kaynak: <https://apps.apple.com/us/app/mihon-manga-reader/id6806831157>
4. **Turboyomi PR #8 / #10 / #11** (Mihon okuyucusunun Windows'a portu): tap zones + invert, page number, chapter transition, crop borders, split wide pages, rotate to fit, per-manga webtoon side padding, keep screen on. Kaynaklar: <https://github.com/rodriguezrobinj/Turboyomi/pull/8>, <https://github.com/rodriguezrobinj/Turboyomi/pull/10>
5. **AnymeX — "manga reader update" issue #1**: Mihon okuyucusunun envanteri + "Tier 1 / Tier 2" önceliklendirmesi (Tier 1: per-series mode, tap zones + invert, scale type, background, page number, keep screen on, fullscreen, rotation, transition animation, color filters). Kaynak: <https://github.com/goADX/AnymeX/issues/1>
6. **Yomi PR #77** (Mihon okuyucu kontrollerinin Compose'a portu): reader barlarının Mihon yerleşimi, tap-zone önizleme renkleri, fullscreen/keep-awake/volume keys. Kaynak: <https://github.com/Tomex777/Build/pull/77>

**Bu sürümde uyarlananlar (Tier 1 tamamı + Tier 2'den birkaçı):**
Fullscreen, keep screen on, tap zones + invert, volume keys, page number, chapter transition, chapter list, vertical paged mode, scale type genişletmesi (Crop/Original), color filter genişletmesi, long-tap actions, Dikey/Webtoon ayrımı.

**Bilinçli olarak ertelenenler (sonraki adım):** crop borders (piksel analizi, decode zamanı), wide page split/rotate-to-fit, double-page, per-manga overrides, webtoon side padding, otomatik kaydırmada menü gizleme hassasiyeti, sayfa geçiş animasyonu seçimi, sonraki bölümün ilk sayfasının ön yüklenmesi.

---

## 6. Temizlik (kopya/ölü kod)

| Silinen / birleştirilen | Satır | Neden |
|---|---|---|
| `data/manga/loader/MangaPageLoader.kt` | ~258 | v1 loader; hiç kullanılmıyor (V2 tek yol) |
| `ui/screens/manga/MangaScreen.kt` | ~600 | Hiçbir yerden çağrılmıyor (ölü ekran) |
| `ui/screens/manga/MangaViewModel.kt` | 76 | Yalnızca ölü `MangaScreen` tarafından kullanılıyordu |
| `AppRoot.kt` içindeki `MangaScreen` import'u | 1 | Ölü import |
| `MangaReaderScreen` içindeki 3× `FilterChip` bloğu | ~60 | `MangaChoiceChips` tek bileşene indirildi |
| `MangaReaderScreen` içindeki üst bar + alt bar + ayar paneli | ~450 → taşındı | `MangaReaderControls.kt` |
| Üç ayrı `Context.findActivity()` kopyası (okuyucu tarafı) | ~10 | `findActivityOrNull()` tek tanım |
| Ölü `private fun ReadingMode.label()` | 6 | Kullanılmıyordu |
| Webtoon'da çift `loader.loadPage()` | 4 | Tek yerden yüklenir |

---

## 7. Değişen / eklenen dosyalar

**Yeni**
- `app/src/main/java/com/kitsugi/animelist/data/manga/MangaSettingIds.kt`
- `app/src/main/java/com/kitsugi/animelist/data/manga/MangaReaderPreferences.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/manga/components/MangaReaderSystemUi.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/manga/components/MangaReaderControls.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/manga/components/MangaVerticalPagerReader.kt`

**Değişen**
- `MangaReaderScreen.kt` (yeniden yazıldı, 696 → ~500 satır, sorumluluklar ayrıldı)
- `MangaReaderViewModel.kt` (ayar eşlemesi, iyimser güncelleme, parlaklık debounce, ilerleme flush)
- `components/MangaPageItem.kt` (dokunma bölgeleri, mirror, yeni modlar, yer tutucular)
- `components/MangaPagerReader.kt`, `components/MangaWebtoonReader.kt`
- `data/manga/MangaModels.kt` (yeni enum değerleri)
- `data/manga/MangaSource.kt` (yanlış sınıf adı yorumu)
- `ui/components/KitsugiPreferencesSettingsDialog.kt` (kanonik ayar kimlikleri + yeni seçenekler)
- `ui/tv/manga/TvMangaReaderControls.kt` (yeni modlar/filtreler)
- `res/values/strings.xml`, `res/values-en/strings.xml` (28 yeni metin, TR + EN)

**Silinen**
- `data/manga/loader/MangaPageLoader.kt`
- `ui/screens/manga/MangaScreen.kt`
- `ui/screens/manga/MangaViewModel.kt`

---

## 8. Doğrulama durumu ve yapılması gerekenler

Bu çalışma ortamında **Android SDK / JDK / Gradle yok**, bu yüzden `./gradlew assembleDebug` çalıştırılamadı. Kod statik olarak (parantez/blok dengesi, import & API kullanımı, çağrı yerleri) gözden geçirildi. Cihazda/CI'da şu kontrol listesi önerilir:

1. `./gradlew assembleDebug` — derleme.
2. Okuyucuyu aç → sistem çubukları **gizli** mi (varsayılan açık)?
3. Tam ekran düğmesi → sistem + uygulama barları birlikte gizleniyor/görünüyor mu?
4. Ortaya dokun → barlar açılıp kapanıyor mu? Uzun bas → ayarlar açılıyor mu?
5. Pinch-zoom ve çift dokunma zoom'u hâlâ çalışıyor mu (dokunma bölgeleri zoom'u bozmamalı)?
6. Ayarlar → Gri Ton / Ters Çevir / Sıcak / Soğuk / Webtoon → okuyucuda gerçekten uygulanıyor mu?
7. Okuma modu "Dikey" → sayfalar **dikey** geçiyor mu?
8. Webtoon'da sayfa yüklenirken kaydırma sıçraması kabul edilebilir mi?
9. Son sayfada okuyucudan çık → tekrar gir → aynı sayfadan devam ediyor mu?
10. Aynalanmış sayfa görülen kaynakta Ayarlar → "Sayfaları aynala" → düzeliyor mu?
11. Ses tuşları (açıkken) sayfa çeviriyor mu?
12. Okuyucudan çıkınca durum/gezinme çubukları geri geliyor mu (tam ekran yapışmıyor mu)?
13. TV okuyucusunda sığdırma/filtre listeleri crash etmiyor mu (yeni enum değerleri)?

---

## 9. Aynalanmış sayfa (mirror) notu

Kod tarafında sayfayı çeviren hiçbir dönüşüm yoktu (`scaleX`, `graphicsLayer`, EXIF ters çevirme vb. yok). Bu yüzden aynalanma ya
(a) kaynağın görselleri **aynalanmış servis etmesinden** (bazı agregatörler telif taramasını atlatmak için yapar),
(b) görsellerin taşıdığı **EXIF orientation = mirror** etiketinden,
(c) bölümün kaynak tarafında ters taranmış olmasından kaynaklanıyor.

Her üç durumda da kullanıcı çözümü, eklenen **"Sayfaları aynala"** anahtarıdır (kalıcı, tüm okuyucu modlarında çalışır). Kaynak düzeltirse anahtar kapatılır.

---

## 10. Sonraki adım önerileri

- **Crop borders**: decode zamanında piksel analizi (Skia subset) + sayfa başına önbellek.
- **Geniş sayfa**: ikiye bölme (split) ve 90° çevirme.
- **Per-manga override**: başlık bazında okuma modu/sığdırma.
- **Webtoon side padding** ve sonraki bölümün ilk sayfasını ön yükleme.
- **Sayfa geçiş animasyonu** seçimi (yok / kaydır / fade).
- **Auto-scroll** (webtoon'da otomatik kaydırma hızı).
