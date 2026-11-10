# Kitsugi Beta v2.4.360

## 🇹🇷 Türkçe

### 🎬 Detay Sayfalarında Çok Kaynaklı Video Toplayıcı ve Kaynak Rozetleri
- **Ortak Video Havuzu:** Detay sayfalarındaki Videolar sekmesi; AniList, MyAnimeList (Jikan), TMDB, Shikimori, Kitsu, Simkl ve AnimeThemes platformlarını paralel sorgulayarak fragman, tanıtım videoları (PV/CM), tema şarkıları (OP/ED webm/audio) ve ekstraları tek bir çatı altında birleştirir.
- **Akıllı Tekilleştirme & Kaynak Rozetleri:** Aynı YouTube kimliği ve doğrudan video URL'leri mükemmel biçimde tekilleştirilir; katkıda bulunan tüm platformlar (`[Logo + İsim]`) rozetleri olarak listelenir.
- **İnteraktif Kaynak Filtreleme:** Video grup başlıklarında yer alan kaynak rozetlerine dokunarak listeyi ilgili sağlayıcıya göre anında filtreleme imkanı sağlandı.
- **TV Arayüzü Uyumluluğu:** TV detay ekranında (`TvDetailScreen`) tema kartları ve kaynak logoları yeni çok kaynaklı mimariyle tam uyumlu hale getirildi.
- **AnimeThemes Entegrasyonu:** AnimeThemes platform logosu (`ic_logo_animethemes.xml`) ve doğrudan WebM akış oynatımı entegre edildi.

### 🖼️ Görsel Galerisi "Kaynaklar" Sekmesi
- **Yeni Sekme Adı & Düzeni:** Görsel galerisindeki sağ panel sekmesi, içeriğine tam uygun olarak "Diğer Resimler" yerine **"Kaynaklar"** ("Sources") olarak güncellendi.
- **Zarif Kaynak Listesi:** Küçük resim önizlemeleri kaldırılarak yerine "Tümü" seçeneği ile her sağlayıcı (Bangumi, Fanart.tv, Jikan/MAL, Shikimori, TMDB vb.) için platform logosu, adı ve görsel sayısını listeleyen şık kaynak satırları getirildi.
- **Çift Filtreleme Desteği:** Kaynak satırına dokunulduğunda galeri anında ilgili kaynağa filtrelenir; kategori filtreleriyle (Kapak, Arka Plan vb.) tam uyum içinde çalışır.

### 📋 Listem: Gerçek Eklenme Zamanı (`addedAt`) ve Tarih Bölümleri
- **Gerçek Eklenme Zamanı Sıralaması:** "Son eklenen / İlk eklenen" sıralaması, kararsız yerel DB satır ID'si yerine sağlayıcının resmî `createdAt` / `added_to_watchlist_at` (`addedAt`) sözleşmesine bağlandı. Gece senkronizasyonunda veya ilerleme güncellemelerinde sıralama bozulmaz.
- **Tarih Bölümleri:** Sıralama "Son eklenen / İlk eklenen" olduğunda liste **Bugün, Dün, Bu Hafta, Geçen Hafta, Daha Eski, Eklendiği Tarih Bilinmiyor** başlıklarıyla estetik zaman çizelgesine ayrılır. Bugün eklenen yapım her zaman "Bugün" altında ve en üstte yer alır.
- **Tekilleştirilmiş Sıralama Ölçütleri:** Çipler, filtre sayfası ve sıralama çekmecesi 11 ortak ölçüt içeren `MyListSortEntry` yapısında eşitlendi; yön çevirme ve deterministik eşitlik bozucu eklendi.
- **Veritabanı v30 Göçü:** `addedAt` kolonu eklendi, eski Bangumi milisaniye zaman damgaları saniyeye normalize edildi.

### 🔄 Çapraz Eşitleme İyileştirmeleri (`CrossSyncRetry`)
- Ağ ve sağlayıcı kaynaklı geçici hatalara karşı akıllı yeniden deneme mantığı ve hata izolasyonu eklendi.

---

## 🇬🇧 English

### 🎬 Multi-Source Video Aggregator & Source Badges on Detail Pages
- **Unified Video Aggregation:** The Videos tab on detail pages aggregates official YouTube trailers, promotional videos (PV/CM), opening/ending theme songs (AnimeThemes webm/audio), and extras across AniList, MyAnimeList (Jikan), TMDB, Shikimori, Kitsu, Simkl, and AnimeThemes in parallel.
- **Deduplication & Source Badges:** Identical canonical YouTube IDs and direct video streams are seamlessly deduplicated while preserving all contributing providers as `[Logo + Name]` badges on rows and group headers.
- **Interactive Source Filtering:** Tapping source badges in video group headers filters the list down to the selected provider with video count indicators.
- **TV Interface Compatibility:** Updated `TvDetailScreen` theme cards with canonical video sources and provider logos.
- **AnimeThemes Vector Logo:** Added dedicated AnimeThemes vector asset (`ic_logo_animethemes.xml`) and color accents.

### 🖼️ Image Gallery "Sources" Tab
- **Renamed Tab & Streamlined Layout:** Renamed the right drawer gallery tab from "Other Images" to **"Sources"** to accurately describe its content.
- **Provider Overview Rows:** Replaced thumbnail previews with structured provider rows showing platform logo, name, and total image count for each source (Bangumi, Fanart.tv, Jikan/MAL, Shikimori, TMDB, etc.).
- **Interactive Filtering:** Tapping any source row filters the gallery to that provider; works seamlessly alongside category chips.

### 📋 My List: Real Addition Time (`addedAt`) & Date Sections
- **True Addition Time Sorting:** "Newest / Oldest" sort is now powered by provider-authoritative `createdAt` / `added_to_watchlist_at` (`addedAt`) instead of unstable local database autoincrement row IDs. Progress or score edits never shift addition dates.
- **Calendar Date Sections:** When sorted by addition date, entries group into **Today, Yesterday, This Week, Last Week, Older, and Added Date Unknown**. Media added today stays pinned under "Today" at the top across all provider tabs.
- **Unified Sort Model:** Aligned inline chips, bottom sheet, and filter dialog to 11 shared sort criteria (`MyListSortEntry`) with direction toggling.
- **Database v30 Migration:** Added `addedAt` column with backward migration and normalization of legacy millisecond timestamps.

### 🔄 Cross-Sync Resilience (`CrossSyncRetry`)
- Added retry handling and failure isolation during cross-provider library synchronization.
