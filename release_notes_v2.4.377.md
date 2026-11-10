# Kitsugi Beta v2.4.377

[TR] **Kategori Kısayolları ve Raf Eşliği, AniList Talep Üzerine Tembel Yükleme ve Zenginleştirilmiş Tür Rafları**
[EN] **Category Shortcut & Shelf Parity, AniList On-Demand Lazy Loading, and Enriched Native Genre Shelves**

---

### [TR] Yenilikler ve İyileştirmeler

#### 1. Keşfet Kategori Kısayolları ve İçerik Rafları Eşliği
- **Tek Doğruluk Kaynağı:** Kategori kısayol çipleri ile aşağıdaki içerik rafları artık aynı `ExploreSourceSection` listesinden dinamik olarak üretilir. Böylece hiçbir kısayol olmayan bir rafı göstermez ya da farklı bir başlık/içerik açmaz.
- **Doğru Medya Gruplaması:** TMDB ve Simkl kataloglarındaki film ve dizi kategorileri artık anime/manga altında değil, kendi doğru medya gruplarında (Diziler, Filmler, Karışık) gösterilir.
- **Sayfalama Senkronizasyonu:** Kategori kısayoluna tıklandığında açılan tam ekran ızgara sayfası (`FullScreenMediaGridPage`), başlangıç listesi boş olsa dahi ilk sayfayı kaynaktan ister ve kullanıcı aşağı kaydırdıkça sonraki sayfaları çeker.

#### 2. AniList Talep Üzerine (Lazy) Yükleme ve Kota Koruması
- **Kompakt Başlangıç İstekleri:** AniList seçildiğinde eskiden tüm rafları çekmek için arka arkaya gönderilen istek fırtınası kaldırıldı. Başlangıçta 6 temel anime kategorisi tek bir GraphQL alias sorgusunda çekilir.
- **Kaydırmaya Bağlı Yükleme:** Kalan raflar kullanıcı LazyColumn içinde aşağı doğru kaydırdıkça 180 ms birleştirme penceresiyle en fazla 6'şar kategori halinde gruplanarak çekilir.
- **Raf Başına Yeniden Deneme:** Ağ veya sunucu kaynaklı bir raf yüklenemezse tüm vitrin kilitlenmez; başarısız olan rafın üzerinde açık ve net bir "Yeniden Dene" butonu görüntülenir.

#### 3. Kitsu, Bangumi ve Shikimori Yerel Tür Rafları
- **AniList Manhwa/Manhua & Romanlar:** Menşei ülke (`countryOfOrigin`: KR & CN) filtresiyle Manhwa & Manhua rafı ve NOVEL formatıyla roman rafı eklendi.
- **Kitsu:** API `filter[subtype]` desteğiyle Manhwa/Manhua (`subtype=manhwa,manhua`) ve Romanlar (`subtype=novel`) rafları bağlandı.
- **Bangumi:** Çizgi roman yayın tarihi sıralamasıyla "Yeni Eklenen Mangalar" ve `BOOK_NOVEL` puan sıralamasıyla "Noveller & Light Novel" rafları eklendi. Ayrıca asılı isteklerin tüm vitrini kitlemesini önlemek için raf başına 15 saniyelik zaman aşımı koruması uygulandı.
- **Shikimori:** `id_desc` sıralamasıyla "Yeni Eklenen Animeler" ve "Yeni Eklenen Mangalar" rafları tamamlandı.

#### 4. Kararlılık ve Önceki Sürümlerin Korunması
- Simkl paylaşımlı hız sınırlayıcı, 429 Retry-After ve 400 `rate_limit` yönetimi korundu (`v2.4.375`).
- Yapışkan arama çubuğu ve üst bar İzleme Geçmişi ile İndirilenler kısayol butonları korundu (`v2.4.376`).
- Dolby Vision profil/codec düzeltmeleri (`v2.4.373`) ve TMDB inceleme çökme koruması (`v2.4.374`) eksiksiz korundu.

---

### [EN] Release Notes

#### 1. Explore Category Shortcut & Shelf Parity
- **Single Source of Truth:** Category navigation chips and their corresponding shelves are now generated from the exact same `ExploreSourceSection` model. Shortcuts can never point to mismatched titles, empty shelves, or invalid fields.
- **Accurate Media Grouping:** TMDB and Simkl TV and movie catalogs are properly categorized under TV, Movies, and Mixed media families rather than being misfiled as Anime/Manga.
- **Seamless Pagination:** Tapping a category shortcut opens the full-screen media grid, fetches the first page from the provider on demand, and continues paginating as the user scrolls.

#### 2. AniList On-Demand Lazy Loading & Rate Limiting
- **Compact Initial Load:** Replaced the legacy heavy network fan-out with a single initial GraphQL alias query covering 6 core anime shelves.
- **Viewport-Triggered Batches:** Remaining AniList shelves load lazily as their rows enter the viewport, debounced with a 180ms window and batched in chunks of up to 6 shelves.
- **Per-Shelf Retry Affordance:** If a shelf request fails, it renders an inline retry button without restarting network storms or blocking the entire feed.

#### 3. Native Shelves for Kitsu, Bangumi, and Shikimori
- **AniList:** Added Manhwa & Manhua (`countryOfOrigin`: KR & CN) and Novels (`format: NOVEL`) shelves with full pagination support.
- **Kitsu:** Added Manhwa/Manhua (`filter[subtype]=manhwa,manhua`) and Novels (`filter[subtype]=novel`) shelves.
- **Bangumi:** Added "Newly Added Manga" (`BOOK_COMIC` sorted by date) and "Novels & Light Novels" (`BOOK_NOVEL` ranked) with a 15-second per-shelf timeout to prevent UI freezes.
- **Shikimori:** Added "Newly Added Anime" and "Newly Added Manga" shelves sorted by `id_desc`.

#### 4. Preserved Features & Stability
- Maintained Simkl shared rate limiter, 429 Retry-After, and 400 `rate_limit` handling (`v2.4.375`).
- Preserved sticky search bar and top bar Watch History & Downloads shortcuts (`v2.4.376`).
- Dolby Vision fixes (`v2.4.373`) and TMDB review null key crash prevention (`v2.4.374`) remain fully active.
