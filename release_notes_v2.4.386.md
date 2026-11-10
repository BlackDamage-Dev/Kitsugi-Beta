# Kitsugi Beta v2.4.386

[TR] **Listem Çift Kart Kök Neden Çözümü, Türkçe Karakter & Fragman Doğrulaması, Eklenti Portalı Araması ve Manga Toplu Silme**
[EN] **My List Duplicate Cards Root Fix, Turkish Character & Trailer Validation, Plugin Portal Search & Manga Bulk Delete**

---

### [TR] Yenilikler ve İyileştirmeler

#### 1. Listem Çift Kart ve Kaynak Tekilleştirme Kök Neden Çözümü
- **Tekil Sağlayıcı Sekmelerinde Çift Kart Engelleme:** Keşfet/Arama/Detay ekranlarından TMDB veya harici kaynaklı bir yapım eklendiğinde veya detay sayfasında kalp/favori tıklandığında, hedef sağlayıcı (`primarySource = "simkl"` / `"anilist"`) `matchesTargetSource` ve `firstMatchingInTargetOrResultSource` ile denetlenir. Kaynak uyuşmazlığı nedeniyle Simkl/AniList altında birebir kopya satır açılması tamamen önlendi.
- **Anime Filmlerinde Movie ↔ Anime Köprüsü:** TMDB üzerinden eklenen tek bölümlük anime filmleri (`MediaType.Movie`), sağlayıcının dizi/anime uç noktasından (`MediaType.Anime`) döndüğünde `sameAnimeMovieAcrossType` ile tanınarak mevcut kaydı günceller; yan yana iki ayrı kart oluşmaz.
- **Veritabanı Katmanında Mutex ve Otomatik Temizlik:** `MediaEntryRepository` katmanına süreç geneli `writeMutex` ve Room `@Transaction` desteği eklendi. Uygulama açılışında ve senkron sonrasında çalışan `deduplicateSameSourceDuplicates()` geçmişten kalan mükerrer satırları kullanıcı puanı, ilerleme ve favori yıldızını koruyarak otomatik temizler.
- **`Listem → 🌐 Tümü` Sekmesinde Kusursuz Birleşme:**
  - *Attack on Titan* ve benzeri serilerde anime sezonlarının TMDB ID uyuşmazlığının doğrulanmış MAL/AniList/Kitsu/Shikimori/Bangumi/Simkl eşleşmesini veto etmesi engellendi (`authoritativeConflictingIdentityKeys`).
  - Pass-2 Union-Find birleştirme algoritmasındaki doğrusal zincirleme hatası ikili kök karşılaştırmasıyla düzeltildi; araya giren uyumsuz satırlar baştaki ve sondaki uyumlu grupların birleşmesini artık engellemez.
  - Birleşik kartlara grup üyelerindeki favori durumu (`isFavorite`) eksiksiz yansıtılır.
- **Çift Yönlü Çapraz Kimlik Köprüsü:** Çözülen tüm kimlik demetleri bellek içi ve kalıcı önbelleğe (`CrossProviderIdentityCache`) çift yönlü indekslenir. Kitsu↔MAL eşlemesi null-güvenli hale getirilerek Tümü kütüphanesine bağlandı. Kitsu API'de `anilist/anime` mapping'leri ve `en_us` başlıkları ayrıştırıldı (`Kaiju No. 8 2nd Season` ↔ `Kaiju No. 8 Season 2` tam eşleşir).
- **TV ve Profil Ekranı Eşliği:** Android TV kütüphane ekranı (`TvLibraryScreen`) ve Profil listesi (`KitsugiUserMediaListScreen`) aynı tekilleştirme ve gruplama hattına dahil edildi.

#### 2. Türkçe Başlık Eşleşmesi, Fragman Doğrulaması ve Eklenti Portalı Araması
- **Türkçe Harf Normalizasyonu:** Türkçe büyük/küçük `İ`/`ı` dönüşümünden kaynaklanan birleşik nokta karakterleri temizlenerek başlıkların doğru eşleşmesi sağlandı; Türkçe karakterlerin ASCII karşılıkları toleranslı karşılaştırılır.
- **Doğrulanmış Fragman Eşleşmesi:** Kitsu ve eklenti aramalarında tek sonuca güvenmek yerine başlık benzerliği, yayın yılı ve medya türü doğrulaması yapılır. Güvenilir eşleşme yoksa alakasız video yerine fragman alanı boş bırakılır.
- **Gelişmiş Eklenti Portalı Araması:** Eklenti adı, adresi, dili, kaynak dosyası ve sınıf adı alanlarında Türkçe harf ve aksan duyarsız tam metin araması etkinleştirildi.

#### 3. Manga Kaynakları "Toplu Sil" ve Yerleşik Kaynak Yönetimi
- **Aktif & Yıkıcı Toplu Silme:** Manga ayarlarındaki "Toplu Sil" butonu 44dp dokunma hedefi, yıkıcı stil ve onay diyaloğu ile mobil, tablet ve TV'de tam işlevsel hale getirildi.
- **Disk Esaslı Dosya Temizliği:** `manga_extensions/` klasöründeki tüm `.apk` ve `.mex` dosyaları kalıcı olarak silinir.
- **Gömülü Kaynak Yönetimi & Geri Getir:** Dosyası bulunmayan gömülü kaynaklar devre dışı bırakılıp listeden gizlenerek sayaç düşürülür; "Geri Getir" butonuyla istendiğinde tek dokunuşla geri açılabilir.

---

### [EN] Release Notes

#### 1. My List Duplicate Cards & Cross-Provider Identity Engine
- **Eliminated Duplicate Cards in Single Provider Tabs:** Resolves canonical provider mismatch when adding from TMDB explore/detail screens or toggling favorites. Uses `matchesTargetSource` and `firstMatchingInTargetOrResultSource` to match existing records under the target provider.
- **Anime Movie ↔ Anime Bridge:** Bridges single-episode anime movies across `MediaType.Movie` and `MediaType.Anime` via `sameAnimeMovieAcrossType`, preventing dual-card splits during provider syncs.
- **Database Write Mutex & Auto-Deduplication:** Process-wide `writeMutex` and Room `@Transaction` prevent concurrency race conditions. Built-in `deduplicateSameSourceDuplicates()` cleans up legacy duplicates preserving progress, scores, and favorite stars.
- **Seamless All View (`🌐 Tümü`) Grouping:**
  - Prevents TMDB season ID conflicts from vetoing verified anime identity matches (*Attack on Titan* fix).
  - Fixed Pass-2 Union-Find grouping algorithm to pairwise evaluate all root clusters.
  - Carries favorite flag across all grouped members to the unified representative card.
- **Bi-Directional Cross-Identity Cache:** Two-way indexed cross-provider identities across MAL, AniList, Kitsu, Shikimori, Bangumi, and Simkl. Resolved ordinal title variants (`2nd Season` ↔ `Season 2`).
- **TV & Profile Screen Parity:** Android TV library and user profile media lists now run through the same unified deduplication pipeline.

#### 2. Turkish Character Normalization, Trailer Verification & Plugin Portal Search
- **Turkish Diacritic Normalization:** Normalizes dotted/dotless `I`/`İ` transformations and ASCII transliterations for accurate catalog matching.
- **Verified Trailer Fallback:** Kitsu and plugin trailer lookups now validate title similarity, release year, and media type before display.
- **Enhanced Plugin Portal Search:** Full-text search across plugin name, URL, language, source file, and class name with accent-insensitive filtering.

#### 3. Manga Sources Bulk Deletion & Built-in Source Management
- **Functional Bulk Delete:** 44dp touch target with confirmation dialog across Phone, Tablet, and Android TV.
- **Disk File Erasure:** Deletes all `.apk` and `.mex` packages from the filesystem.
- **Built-in Source Toggle:** Persistently hides embedded sources with a dedicated restore action.

---

### İndirilebilir Dosyalar / Downloadable Assets
- `Kitsugi-Beta-v2.4.386-foss.apk`
