# Kitsugi Beta v2.4.463 (v2.4.341 Entegrasyonu)

## 🇹🇷 Türkçe

### 🧩 Manga Eklentileri İçin "Eklenti Keşfet" Sayfası
- **Video eklentileriyle birebir parite:** Eklenti Portalı → *Manga* sekmesi → kurulu bir manga eklentisinin satırına dokunduğunda (veya satırdaki **Keşfet** ikonuna bastığında) artık o kaynağa ait **kendi Keşfet sayfası** açılıyor.
- **Kaynağa ait raflar:** "Popüler Mangalar" ve — kaynak gerçekten destekliyorsa — "Son Güncellemeler" rafları yatay poster şeritleri olarak listeleniyor.
- **Tümünü Gör:** Her raf başlığının sağındaki "Tümünü Gör" ile o rafın tam ekran grid görünümü açılıyor; aşağı kaydırdıkça sonraki sayfalar otomatik yükleniyor (sayfalama + yükleniyor göstergesi).
- **Kaynak içi arama:** Üstteki arama alanı yalnızca o eklentide arıyor; yazarken 260 ms debounce ile istek selleri engelleniyor, sonuçlar sayfalanıyor.
- **Türler:** Yüklenen raflardaki türlerden üretilen çip satırı; bir türe dokunmak o tür adıyla kaynak içi aramayı başlatıyor.
- **Manga → bölümler:** Bir mangaya dokunduğunuzda kaynağın **detay + bölüm listesi** sayfası açılıyor; oradan okuyucuya devam edebiliyorsunuz. Geri tuşu sizi keşfet sayfasına döndürür.
- **Uydurma veri yok:** "Son Güncellemeler" rafı yalnızca kaynak `supportsLatest` bildirdiğinde çizilir (Mihon `Source.supportsLatest`, Kotatsu `SortOrder.UPDATED`). Desteklemeyen kaynakta raf hiç görünmez — popüler liste "son güncelleme" diye sunulmaz.
- **Akıllı önbellek ve durum koruma:** Keşfet sayfası LRU (8 kaynak) ile önbelleklenir; bir mangaya girip geri döndüğünüzde ağa tekrar çıkılmaz ve **bıraktığınız görünüm** (raflar, "Tümünü Gör" grid'i, arama sonuçları) aynen geri gelir. Yenile butonu önbelleği atlar.
- **Hız sınırı koruması:** Raflar sırayla yüklenir; eşzamanlı liste isteklerinin tetiklediği HTTP 429 hataları önlenir.
- **Hata görünürlüğü:** Kaynak hatası olduğunda mesaj + `KTS-SRC-*` hata kodu çipi ve "Yeniden Dene" butonu gösterilir; hata kaynak sağlık raporuna da işlenir.

---

## 🇬🇧 English

### 🧩 Explore Page for Manga Extensions
- **Video-plugin parity:** Extension Portal → *Manga* tab → tapping an installed manga extension row (or its **Explore** icon) now opens a dedicated explore page for that source.
- **Source-specific shelves:** "Popular Manga" and — only when the source actually supports it — "Latest Updates" rails rendered as horizontal poster rows.
- **See All:** Each rail offers a "See All" full-screen grid with automatic pagination and a loading indicator.
- **In-source search:** The search field is scoped to that extension, with a 260 ms debounce and paginated results.
- **Genres:** A genre chip row derived from the loaded rails; tapping a chip runs an in-source search for that genre.
- **Manga → chapters:** Tapping a manga opens the source's **detail + chapter list** page and continues into the reader; back returns to the explore page.
- **No fabricated data:** "Latest Updates" is only shown when the source reports `supportsLatest` (Mihon `Source.supportsLatest`, Kotatsu `SortOrder.UPDATED`); otherwise the shelf is hidden entirely.
- **Smart cache & state preservation:** Explore snapshots are memoised in an 8-entry LRU cache, so returning from a manga detail neither refetches nor loses your place (rails, "See All" grid, search results). The refresh button bypasses the cache.
- **Rate-limit protection:** Rails load sequentially, avoiding HTTP 429 responses caused by concurrent list requests.
- **Visible failures:** Failures surface a message, a `KTS-SRC-*` error-code chip and a retry action, and are recorded in the source health report.

## Teknik Notlar

- `MangaSource` kontratına iki **varsayılan uygulamalı** üye eklendi: `supportsLatest` (varsayılan `false`) ve `fetchLatestManga(page)` (varsayılan `fetchPopularManga`). Böylece mevcut üçüncü parti eklentiler değişmeden derlenir.
- `MihonSourceWrapper` → `Source.supportsLatest` + `getLatestUpdates(page)`; hata durumunda popüler listeye düşer.
- `KotatsuMangaSource` → `SortOrder.UPDATED in parser.availableSortOrders` + `getList(order = UPDATED)`; sayfalama ofseti popüler rafıyla çakışmaması için ayrı sanal anahtar kullanır.
- Yeni ekran: `ui/screens/manga/MangaSourceExploreScreen.kt` (durum tutucu + LRU önbellek, ViewModel biriktirmeyen tasarım).
- Navigasyon: `DetailScreen.MangaSourceExplore(sourceKey)` / `AppStateKey.MangaSourceExplore(sourceKey, depth)`; kaynak nesnesi taşınamadığı için `stableSourceKey()` ile çözülür.
- Giriş noktaları: Eklenti Portalı manga sekmesindeki kurulu kaynak satırı ve Manga tarama ekranındaki eklentiler diyaloğu.
