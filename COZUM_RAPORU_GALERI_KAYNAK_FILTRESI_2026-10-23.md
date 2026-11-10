# ÇÖZÜM RAPORU — Galeri "Kaynak" Filtresi (2026-10-23)

## İstek

Açılır resim galerisinde (tam ekran görsel görüntüleyici) iki yerde kaynak bilgisi eksikti:

1. **Yatay mod → sağ panel → "Diğer Resimler" sekmesi** (ekran görüntüsü 1): yalnızca aynı
   kategoriden görseller listeleniyordu; görselin hangi API'den geldiği görünmüyordu.
2. **Dikey mod → ⓘ (bilgi) butonu → "Resim Detayları" levhası** (ekran görüntüsü 2): görselin
   kaynağı tek satır olarak yazılıyordu, ancak galerideki **diğer kaynaklar** listelenmiyordu.

Detay sayfasındaki "Resimler" levhasında (ekran görüntüsü 3) `Tür` ve `Kaynak` bölümleri
(logo + ad + görsel sayısı) zaten vardı. Aynı dil galeriye de taşındı.

## Tasarım kararı

- Galeri artık **kaynak filtresini** destekliyor: `TMDB`, `Fanart.tv`, `AniList`, `Jikan (MAL)`,
  `Shikimori`, `Kitsu`, `Bangumi`, `Simkl`, `Fandom` …
- Kaynak listesi, detay sayfasındaki seçiciyle **birebir aynı görünümde**: platform logosu +
  kaynak adı + görsel sayısı, "Tümü" satırı en üstte, seçili satır vurgulu.
- Kaynak seçimi **hem pager'ı hem kategori sekmelerini** daraltır; kategori sayıları da kaynak
  kapsamına göre yeniden hesaplanır (ör. TMDB seçiliyken "Poster (127)" TMDB posterlerini sayar).
- Etkin kaynak filtresi, kategori çubuğunun başında **kapatılabilir bir çip** olarak gösterilir
  (logo + ad + ✕). Çipe dokunmak filtreyi kaldırır.
- Tek kaynaklı galerilerde liste gereksiz olduğu için gizlenir.

## Değişiklikler

### `ui/components/KitsugiImageGalleryDialog.kt`

- Yeni parametre: `initialSource: String?` — galeri açılışta tek kaynağa filtrelenebilir.
- `availableSources` (kaynak → görsel sayısı) türetilir; alfabetik sıralanır.
- `selectedSourceFilter` durumu; `sourceScopedItems` → `availableCategories` → `filteredItems`
  zinciri kaynak + kategori filtresini birlikte uygular.
- Seçili kategori yeni kaynakta yoksa otomatik olarak "Tümü"ye döner (boş pager koruması).
- Sayfa 0'a dönüş efekti artık `selectedCategoryFilter` **ve** `selectedSourceFilter` değişimini izler.
- LANDSCAPE `GalleryLandscapeLayout`:
  - `galleryItems` parametresi `sourceScopedItems` olarak yeniden adlandırıldı (sayımlar doğru kapsamdan).
  - "Diğer Resimler" sekmesi: **Kaynak** bölümü (Tümü + kaynak satırları) + **Diğer Resimler**
    başlığı + aynı kategoriden görseller; tek `LazyColumn` içinde kaydırılabilir.
  - Kategori çubuğunda etkin kaynak çipi.
- PORTRAIT `GalleryPortraitLayout`: aynı çip; kategori çubuğu yalnızca kaynak filtresi varken de çizilir.
- `GalleryDetailsSheet` (ⓘ): sayfa satırının altına **Kaynak** bölümü eklendi; seçim galeriyi
  filtreler ve levha kapanır (sonuç anında görünür).
- Yeni yardımcılar: `GallerySourceGroup`, `GallerySourceRow`, `GallerySourceSectionTitle`,
  `GalleryActiveSourceChip`.

### `ui/screens/detail/MediaEntryDetailPage.kt` / `ApiResultDetailPage.kt`

- `activeGallerySource` durumu eklendi.
- Detay sayfasındaki kaynak seçicisinden galeri açıldığında artık **tüm galeri** verilir ve kaynak
  `initialSource` ile filtrelenir. Böylece kullanıcı galeri içindeki "Kaynak" listesinden başka
  kaynaklara geçebilir (önce yalnızca seçilen kaynağın öğeleri veriliyordu).
- Galeri kapanınca `activeGallerySource` sıfırlanır (bayat filtre kalmasın).

### `res/values/strings.xml`, `res/values-en/strings.xml`

- `gallery_source_all` → "Tümü" / "All"
- `gallery_clear_source_filter` → "Kaynak filtresini temizle" / "Clear source filter"

## Davranış özeti

| Yer | Önce | Sonra |
|---|---|---|
| Yatay · Diğer Resimler | aynı kategorideki görseller | Tümü + kaynak listesi (logo/ad/sayı) + görseller |
| Dikey · ⓘ Detaylar | tek satır "Kaynak" | detaylar + kaynak listesi (seçilebilir) |
| Kategori çubuğu | — | etkin kaynak çipi (✕ ile temizlenir) |
| Detay sayfası kaynak seçicisi | sadece o kaynağın öğeleri | tüm galeri + kaynak filtresi açık |

## Notlar

- Bu ortamda Android SDK/JDK bulunmadığı için derleme (assembleDebug) çalıştırılamadı; dosyalar
  sözdizimi, parantez dengesi ve dize kaynağı bütünlüğü açısından elle + betikle doğrulandı
  (strings.xml XML olarak ayrıştırıldı, yinelenen anahtar yok).
- Kaynak sayıları `ignoreCase` gruplanır; `"TMDB"` / `"tmdb"` gibi yazım farkları tek satırda toplanır.
