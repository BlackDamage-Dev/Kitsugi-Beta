# Çözüm Raporu — Manga Keşfet: Yapışkan Kaynak Çubuğu + Toplu Kaynak Seçici

**Tarih:** 2026-11-07
**Ekran:** `Manga Keşfet` (manga eklenti keşfet / `MangaBrowseScreen`)
**Referans ekran:** Video İzle keşfet (`StreamScreenContent.kt` → `CollapsibleTopBarBox`, `AddonChipsRow`, kaynak `ModalBottomSheet`'i)

---

## 1. İstenen

1. Aşağı kaydırınca turuncu ile işaretlenen **kaynak çipi satırı yukarı yapışmalı**; başlık, arama adı, arama kutusu ve sıralama satırı animasyonlu olarak **gizlenmeli**.
2. Çiplerin **hemen yanına açılır (dropdown) bir buton** konmalı; video keşfet sayfasındaki gibi kaynaklar **açılır pencerede toplu ve seçilebilir** gösterilmeli.
3. İşaretlenen üst kısım video keşfet sayfasındaki gibi **daha derli toplu** olmalı.

## 2. Önceki durum

`MangaBrowseScreen` düz bir `Column` kullanıyordu: `TopBar → bilgi kartı → arama kutusu → LazyRow çipleri → divider → LazyVerticalGrid`.
Başlık sabit yer kaplıyordu, kaydırmada hiçbir şey katlanmıyordu; kaynaklar yalnızca **yatay kaydırılabilir tek çip şeridinde** görülebiliyordu (6+ kaynakta sağdakiler gizleniyordu). Video keşfet ekranındaki `HideOnScrollConnection` + kompakt kaynak kartı + `ModalBottomSheet` düzeni manga tarafında yoktu.

## 3. Yapılan değişiklikler

### 3.1 Katlanan başlık / yapışkan çubuk
- Ekran `Box` + `nestedScroll` düzenine geçirildi. Izgara artık **tek bir `LazyVerticalGrid`** (`rememberLazyGridState`), `contentPadding(top = katlananBaşlık + yapışkanÇubuk)`.
- `MangaHideOnScrollConnection`, `StreamScreenContent.HideOnScrollConnection` ile aynı mantıkta çalışır: kaydırma deltası `headerOffsetPx`'e yazılır, ofset `0..katlananYükseklik` arasında tutulur; **yükseklik bir anda değişmediği için liste zıplaması yoktur**, başlık parmakla birlikte akar.
- Kısa liste koruması: `gridState.canScrollForward == false` iken aşağı kaydırma başlığı gizlemez. Liste başına dönünce `snapshotFlow` ofseti sıfırlar.
- Üst overlay **tek bir `Column`**: `[katlanan kısım][yapışkan kaynak çubuğu]`. `graphicsLayer { translationY = -offset }` uygulanınca katlanan kısım tamamen yukarı çıktığında çubuk kendiliğinden `y = 0`'a oturur ve orada kalır.
- Animasyonlar:
  - katlanan kısım `alpha = 1 - progress * 0.85` ile soluklaşır;
  - yapışkan çubuk `animateFloatAsState` (zemin opaklığı 0.92 → 0.985) ve `animateDpAsState` (gölge 0 → 6 dp) ile `KitsugiMotion.normalMillis` süresinde sertleşir;
  - katlanmışken çubukta **aramaya dönüş kısayolu** `AnimatedVisibility` + `fadeIn/scaleIn` / `fadeOut/scaleOut` ile belirir (`KitsugiMotion.stickyHeader*Millis`); dokununca `animateScrollToItem(0)` ile başlık geri gelir.
- `onSizeChanged` ile katlanan kısmın gerçek yüksekliği ölçülür; ilk kare için 200 dp tahmini kullanılır (ızgara üst boşluğu zıplamasın).

### 3.2 Açılır buton + toplu kaynak seçici
- Çip satırının **sağında sabit** 36 dp `Surface` dropdown butonu (`ExpandMore`). Çipler yatay kayarken buton yerinde kalır.
- `MangaSourcePickerSheet`: video keşfet `AddonChipsRow` picker'ının manga uyarlaması — başlık + `sonuç · kaynak` özeti + kaynak içi arama alanı + `Tümü` satırı + tüm kaynaklar tek `LazyColumn`'da; her satırda kaynak rengi noktası, durum (`Aranıyor…` / `Hata · KTS-SRC-…` / `N sonuç`), sonuç sayısı rozeti ve seçili işareti. Seçim `vm.selectSourceFilter(...)`'e bağlı, sheet kapanır.
- Arama modunda kaynaklar `sourceStates`'ten (yükleniyor/hata/sayı), popüler modda `ui.sources`'tan gelir.

### 3.3 Derli toplu üst kısım
- Eski posterli "bilgi kartı" kaldırıldı; onun yerine **kompakt 54 dp başlık**: geri + `Manga Keşfet` + `N sonuç · M kaynak` alt satırı + kaynak yönetimi kısayolu (ayarlar diyaloğu, `initialTab = 2`).
- Arama kutusu: tek satır, 14 dp köşe, **X ile temizleme**, `FocusRequester` ile "Arama adını değiştir" kısayolu.
- Yeni kompakt `Arama: …` hapı + `Arama adını değiştir` metin butonu.
- Yeni `Sırala: İlgi / A-Z` satırı (gerçek sıralama uygulanır: benzerlik puanı ↔ başlık A–Z, `rememberSaveable` ile korunur) ve sağda `Aranan: …`.
- `MangaChip` sadeleştirildi: sabit 34 dp yükseklik, ince kenarlık, kaynağa göre **kararlı renkli nokta** (`sourceDotColor`), sayaç artık küçük rozet içinde, seçili çip vurgulu. Izgaraya 12 dp kenar boşluğu verildi.

## 4. Değişen dosyalar

| Dosya | Değişiklik |
| --- | --- |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/manga/MangaBrowseScreen.kt` | Ekran düzeni, katlanan başlık, yapışkan çubuk, dropdown buton, kaynak picker sheet, sıralama, kompakt çipler |
| `app/src/main/res/values/strings.xml` | 14 yeni `manga_browse_*` metni (TR) |
| `app/src/main/res/values-en/strings.xml` | Aynı 14 metnin İngilizce karşılığı |

Mevcut `ui2_manga_search_summary`, `tumu`, `temizle`, `manga_ara`, `araniyor`, `sonuc_bulunamadi`, `action_back` kaynakları yeniden kullanıldı; çift tanım eklenmedi. `MangaBrowseScreen` imzası ve ayarlar diyaloğu bağlantıları (`KitsugiAddonsSettingsDialog`) **değişmedi** — `AppRootMangaPages` / `AppRoot` etkilenmez. TV ekranı (`TvMangaBrowseScreen`) bu değişikliğin dışında bırakıldı (istek telefon görünümü içindi).

## 5. Davranış özeti

| Jest | Sonuç |
| --- | --- |
| Aşağı kaydırma | Üst kısım yukarı kayıp soluklaşır, kaynak çubuğu + dropdown ekrana yapışır |
| Yukarı kaydırma / başa dönüş | Üst kısım aynı hareketle geri gelir |
| Dropdown | Tüm kaynaklar aranabilir tek pencerede, tek dokunuşla filtre |
| Katlanmışken büyüteç | Başlığı yeniden açar, arama kutusuna döner |
| `Arama adını değiştir` | Başlığı açar + arama alanına odaklanır |
| `İlgi` / `A-Z` | Sonuç sıralaması anında değişir |

## 6. Doğrulama notu

Bu çalışma alanında JDK / Android SDK / Gradle bulunmadığından `./gradlew` derlemesi çalıştırılamadı. Buna karşılık:

- `MangaBrowseScreen.kt` **tree-sitter Kotlin ayrıştırıcısıyla** hatasız (`has_error: False`, 0 ERROR düğümü) doğrulandı;
- süslü/normal parantez dengesi kontrol edildi;
- her iki `strings.xml` `ElementTree` ile geçerli XML olarak ayrıştırıldı;
- kullanılan tüm `R.string.*` anahtarlarının TR + EN dosyalarında **tek** tanımı olduğu kontrol edildi;
- çağrılan VM metotları (`search`, `selectSourceFilter`, `loadNextPage`) ve composable imzaları mevcut kodla karşılaştırıldı.

Cihaz / CI üzerinde `:app:compileDebugKotlin` ile bir derleme ve kaydırma + sheet etkileşim testi önerilir.
