# KÖK NEDEN RAPORU — DETAY SAYFASI "BİLGİ" SEKMESİ KAYDIRMA KASMASI / DONMASI (2026-10-23)

## Belirti
Kullanıcı bildirimi: "detay sayfası bilgi kısmı feci kasıyor donuyor … sağa sola yukarı
aşağı kaydırınca". Yatay (landscape) düzenlerde sol vitrin paneli + sağda sekme pager'ı
(Bilgi / Resimler / Videolar / …) açıkken her yönde kaydırma belirgin şekilde kasıyor,
jestler donuk hissediliyor.

## Yöntem
Statik kod analizi (Compose faz sözleşmesi: composition → layout → draw; bir fazdaki
state okuması/yazımı yalnızca o fazı geçersiz kılar). Daha önceki kasma raporlarıyla
aynı hata sınıfları arandı (bkz. `PLAN_TASK_SCROLL_JANK_PERFORMANS_FIX_2026-10-10.md`,
`KOK_NEDEN_RAPORU_LISTEM_KAYDIRMA_KASMA_2026-10-20.md`,
`COZUM_RAPORU_OTOMATIK_CEVIRI_KAYDIRMA_KASMASI_2026-10-21.md`).

## Kök nedenler (önem sırasıyla)

### 1. TEK composition scope'unda ~25 StateFlow + `pagerState.currentPage` okuması (ANA NEDEN)
`MediaEntryDetailPage` gövdesinde tüm sekme durumları (özet/çeviri, karakterler, ekip,
öneriler, ilişkiler, grafikler, yorumlar, bölümler, bölüm puanları…) TEK composable
scope'unda `collectAsState()` ile toplanıyordu. Üstüne `val selectedTab =
pagerState.currentPage` aynı scope'ta okunuyordu.

Sonuç zinciri:
- Herhangi bir sekmenin verisi arka planda geldiğinde (galeri, MDBList, çeviri,
  karakterler…) **TÜM sayfa** — sol vitrin paneli, scaffold, görünür sekme içeriği —
  baştan kompoze oluyordu. Bu emisyonlar sayfa açıldıktan sonraki saniyeler boyunca
  ardışık gelir; kullanıcı tam da o pencerede kaydırmaya başladığı için kasma
  "kaydırınca" hissediliyordu.
- Yatay pager kaydırmasında `currentPage` jestin ortasında değiştiği için **jestin
  ortasında** tüm sayfa recompose oluyordu ("sağa sola kaydırınca donma").
- Sekme içeriklerine parametre olarak geçen callback'ler satır-içi (inline) lambda
  oldukları için her recompose'da yeni instance üretiliyor, Compose sekme
  composable'ını "parametresi değişti" sayıp gövdesini (tüm Bilgi kolonu: markdown
  özet + FlowRow çip satırları + kartlar) baştan kuruyordu.

**Çözüm:**
- Sekme-özel akışlar dosya sonuna eklenen izole slot composable'larına taşındı:
  `OverviewTabSlot`, `CharactersTabSlot`, `StaffTabSlot`, `RecommendationsTabSlot`,
  `RelationsTabSlot`, `StatsTabSlot`, `ReviewsTabSlot`, `EpisodesTabSlot`.
  Bir sekmenin akışı emisyon verdiğinde artık yalnızca o sekmenin alt ağacı geçersiz
  olur; sayfa gövdesi, sol panel ve diğer sekmeler recompose EDİLMEZ.
- `currentPage` okuması sayfa gövdesinden kaldırıldı: sekme yükleme efekti izole
  `DetailTabLoadEffect` composable'ına taşındı; video sekmesi kapısı (`page 2`)
  `currentPage`'i pager ITEM scope'unda okuyor.
- Tüm satır-içi callback'ler `remember` ile stabilize edildi
  (`openGallerySelection`, `onOverviewImageGalleryRequest`, `onOverviewGalleryItemRequest`,
  `onOverviewTranslateClick`, `onToggleSynopsisOriginal`, `onIntegrationsClick`,
  `onSeasonSelected`, `onEpisodeOptionsRequested`, `onUnlinkManga`, `onImagesItemClick`,
  `onImagesOpenGallery`, `onImagesOpenSourceGallery`, `onWikiOpenGallery`,
  `onRelationItemClick`, `onMediaClick`) + `EntryDetailOverviewTab` içindeki
  `studioChipClick`. Parametreler stabil olunca Compose sekme içeriklerini atlar
  (skip), gereksiz yeniden kurulum gerçekleşmez.
- Güncel değer gereken lambdalarda bayat closure yerine `viewModel.galleryLoading.value`
  okunur (davranış birebir aynı).

### 2. Portre modda pager ofsetinin COMPOSITION fazında okunması
`DetailPageScaffold` portre yolunda `item(key = "content")` gövdesi
`pagerState.currentPageOffsetFraction` değerini composition'da okuyup yükseklik
interpolasyonu yapıyordu. Bu değer kaydırma jesti boyunca **her kare** değiştiği için
pager'ı BARINDIRAN LazyColumn item'ı (ve dolayısıyla görünür sayfa içerikleri) her
karede yeniden kompoze oluyordu.

**Çözüm:** ofset + ölçülen sayfa yükseklikleri artık yalnızca `Modifier.layout { }`
içinde (LAYOUT fazı) okunuyor. Compose faz sözleşmesine göre layout fazındaki state
okuması yalnızca o düğümü yeniden ölçer/biçimler; sayfa içeriklerini recompose etmez.
Görsel davranış (sekme geçişinde yükseklik interpolasyonu) birebir aynıdır.

### 3. Boşta çalışan sonsuz animasyon: `KitsugiGalleryIconButton`
Buton, `isLoading` false iken de `rememberInfiniteTransition` kuruyordu; nabız değeri
her karede okunduğu için detay sayfası açık kaldığı sürece buton alt ağacı saniyede
60-120 kez recompose oluyor, Recomposer sürekli uyanık kalıyordu. Aynı hata sınıfı
daha önce `KitsugiTranslateIconButton` için düzeltilmişti ("otomatik çeviri açıkken
sayfa kaydırmada kasıyordu").

**Çözüm:** sonsuz transition izole `PulsingGalleryIconButton` alt ağacına alındı ve
yalnızca `isLoading == true` iken kurulur. Boştayken buton statiktir.

## Değişen dosyalar
- `ui/screens/detail/MediaEntryDetailPage.kt` — sekme slotları, stabil callback'ler,
  izole `DetailTabLoadEffect`, item-scope video kapısı
- `ui/screens/detail/DetailPageScaffold.kt` — portre yükseklik interpolasyonu layout
  fazına taşındı
- `ui/components/KitsugiGalleryIconButton.kt` — sonsuz animasyon yalnızca yüklerken
- `ui/screens/detail/EntryDetailTabContents.kt` — `studioChipClick` remember edildi

## Davranış değişikliği yok
Sekme yükleme tetikleyicileri, özet/çeviri yayılımı (`onSynopsisLoaded`), galeri
açma/kapama semantiği ve video sekmesi kapısı birebir korunmuştur; yalnızca state
okuma/yazma fazları ve recompose kapsamları değiştirildi.

## Doğrulama (cihazda)
- [ ] Detay sayfasını aç → Bilgi sekmesinde yukarı/aşağı hızlı kaydır: jest akıcı.
- [ ] Sekmeler arası yatay kaydırma (Bilgi ⇄ Resimler  …): ortada takılma yok.
- [ ] Sayfa açıldıktan hemen sonra (galeri/çeviri yüklenirken) kaydır: kasma yok.
- [ ] Studio Layout Inspector: kaydırma sırasında sayfa gövdesi recompose olmuyor.
