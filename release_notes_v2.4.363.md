# Kitsugi Beta v2.4.363

## 🇹🇷 Türkçe Sürüm Notları

### 🛡️ AniList 30 İstek/Dk Kayan Pencere Kota Kapısı (`AniListQuotaGate`)
- **Resmî Fiili Limite Tam Uyum:** AniList API'sinin fiili 30 istek/dk (IP başına) sınırına ve burst limiter mekanizmasına tam uyumlu 60 saniyelik kayan pencere kota kapısı entegre edildi.
- **Matematiksel Emniyetli Aralık:** İki istek arasına `ceil(60000 / bütçe)` ms (~2223 ms) minimum aralık koyularak 60 sn penceresinde kotanın aşılması matematiksel olarak engellendi.
- **Öncelik Seviyeleri & Rezerv:** Öncelik kuyruğu (`CRITICAL`, `INTERACTIVE`, `PREFETCH`, `BACKGROUND`) uygulandı. Bütçenin %60'ından sonra ön yüklemeler, %40'ından sonra ise arka plan işleri sınırlandırılarak kullanıcının bildirim ve gezinme bütçesi korundu.
- **Tek Belgede Bildirim ve Sayaç:** Bildirim açılışı tek GraphQL belgesinde (`Viewer { unreadNotificationCount }`) sayfa ve okunmamış sayacı bir arada alarak 2 istek yerine 1 HTTP isteğine indirildi.
- **İstemci Tarafı Sekme Filtreleme:** Bildirim sekmeleri arasındaki geçişler yerel önbellek üzerinden filtrelenerek 6 ağ isteği yerine 0 isteğe düşürüldü.
- **Kaçak Yolların Engellenmesi:** Kotayı doğrudan POST atarak görünmez tüketen 5 yol (eşitleme, içe aktarma, teşhis ve kullanıcı profili) tek kota kapısına bağlandı.
- **Kota & Geri Sayım Bilgisi:** Kota dolduğunda kullanıcıya yanıltıcı "ağ hatası" yerine açıkça "~N sn sonra yeniden deneyin" geri sayımı gösterilir.

### 🧭 Keşfet: Kategoriler Çubuğu ve "Tümünü Gör" Paritesi
- **Birebir Eşleşen Kategori Rafları:** Keşfet ekranındaki Kategoriler çubuğu ve altındaki raflar (AniList, MAL, Kitsu, Shikimori, Bangumi, TMDB, Simkl ve Tümü) tek ortak kaynaktan (`singleSourceExploreSections`) beslendi.
- **Dinamik Çipler:** Yalnızca altında gerçekten dolu olan (`results.isNotEmpty()`) raflar çubukta çip olarak gösterilir; kaynağın üretmediği veya boş raflar çubukta yer almaz; eksik kalan raflar (Manhwa & Manhua, Noveller, Bangumi Dizi/Film, Simkl listeleri) ait olduğu gruba (Anime, Manga, Dizi & Film, Listem) eklendi.
- **Ortak Açılış Sözleşmesi:** Kategori çipleri ve raf başlığındaki "Tümünü Gör" butonu aynı nesne ve başlık üzerinden `openExploreSectionSeeAll` sözleşmesine bağlandı.
- **Tam Ekran Grid & Yetişkin Ayarları:** `FullScreenMediaGridPage` sayfasına `showAdultContent` ve `blurAdultMedia` aktarıldı, sayfalama çağrılarına bağlandı; durum çakışmalarını önlemek için bağımlılık anahtarlarına `title` eklendi.

### 📊 MDBList Harici Puanlama ve API Güncellemesi
- **Yeni Uç Nokta Desteği:** `api.mdblist.com/{provider}/{mediatype}/{id}?apikey=...` uç noktası yapılandırıldı.
- **Manga Ayrımı:** MDBList yalnızca film/dizi puanları sağladığından manga içerikleri için gereksiz MDBList istekleri engellendi.
- **Rotten Tomatoes Audience Uyumu:** Rotten Tomatoes seyirci puanı ("Popcornmeter") `popcorn` alanı ile tam uyumlu hale getirildi.
- **Canlı Kota ve /user Takibi:** `/user` uç noktası ve `X-RateLimit-*` başlıkları üzerinden anlık kota takibi muhafaza edildi.

---

## 🇬🇧 English Release Notes

### 🛡️ AniList 30 Req/Min Sliding Window Quota Gate (`AniListQuotaGate`)
- **Full Compliance with De-Facto Limits:** Integrated a 60-second sliding window quota gate specifically tailored for AniList's 30 requests/min per-IP ceiling and burst limiter.
- **Strict Mathematical Spacing:** Enforced a `ceil(60000 / budget)` ms (~2223 ms) minimum interval between consecutive requests to prevent boundary leakage.
- **Priority Reserve & Throttling:** Tiered prioritization (`CRITICAL`, `INTERACTIVE`, `PREFETCH`, `BACKGROUND`) ensures background sync and prefetching do not deplete interactive user quota.
- **Unified Notification Query:** Consolidated notification page and unread count into a single GraphQL document (`Viewer { unreadNotificationCount }`), reducing initial load from 2 HTTP requests to 1.
- **Client-Side Tab Filtering:** Switching notification category tabs is now filtered in-memory from the cached page, cutting 6 redundant network roundtrips to 0.
- **Bypass Route Lockdown:** Routed 5 direct POST endpoints (sync, import, diagnostics, profile VMs) through the unified quota gate.
- **Clear User Countdown:** Replaced generic network error messages with exact retry countdowns when quota is exhausted.

### 🧭 Explore: Category Chips & "See All" Parity
- **Single Source of Truth for Shelves & Chips:** Unified category chips and horizontal media shelves across all providers (AniList, MAL, Kitsu, Shikimori, Bangumi, TMDB, Simkl, and All Sources) via `singleSourceExploreSections`.
- **Dynamic Chip Rendering:** Chips are only displayed for shelves with actual populated content (`results.isNotEmpty()`). Formerly omitted shelves (Manhwa & Manhua, Novels, Bangumi Series/Movies, Simkl personal watchlists) are now properly grouped under Anime, Manga, TV & Movies, and My List.
- **Unified Navigation Contract:** Tapping a category chip and clicking "See All" on a shelf title invokes the exact same `openExploreSectionSeeAll` handler with identical arguments.
- **Full Screen Grid Usability:** Propagated `showAdultContent` and `blurAdultMedia` settings to `FullScreenMediaGridPage`; added `title` to dependency keys to avoid state crosstalk between distinct shelves with shared category types.

### 📊 MDBList External Ratings & Endpoint Migration
- **Modern API Endpoint:** Migrated to `api.mdblist.com/{provider}/{mediatype}/{id}?apikey=...`.
- **Manga Exclusion:** Suppressed fruitless MDBList lookups for manga titles.
- **Rotten Tomatoes Audience Parsing:** Added `popcorn` token mapping to correctly parse RT Popcornmeter ratings.
- **Quota Tracking Preserved:** Maintained real-time daily quota monitoring through `/user` and `X-RateLimit-*` response headers.

---
**File Size:** ~104.7 MB  
**Checksum / Build:** Foss Release v2.4.363  
