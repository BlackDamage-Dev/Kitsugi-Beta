# Kitsugi Beta v2.4.368

[English notes below]

---

## 🇹🇷 Türkçe Sürüm Notları

### 1. TMDB Tam Hesap Entegrasyonu (Watchlist, Favoriler, Puanlar ve Listeler)
- **Resmî v3 Oturum ve Kimlik Doğrulama Akışı (`TmdbAuthStore` & `TmdbAccountClient`):**
  - Resmî v3 `request_token` → kullanıcı tarayıcı onayı → `session_id` → `account_id` akışı eksiksiz uygulandı.
  - Süresi dolmuş, kullanılmış veya eşleşmeyen callback yanıtlarına karşı korumalar eklendi.
  - Oturum kimliği cihazda güvenli biçimde saklanır ve hesap durumu anlık olarak doğrulanır.
- **Doğrulanmış Film ve Dizi Ayrımı (`TmdbAccountContract`):**
  - Film (`movie`) ve Dizi (`tv`) türleri TMDB'nin farklı veri yapılarına uygun olarak ayrıştırıldı.
  - Eski katalog kaynağı ile gerçek kullanıcı hesabı kaynağı (`tmdb_account`) ayrılarak Listem ekranındaki mükerrer kartlar ve hatalı birleştirmeler tamamen engellendi.
- **İki Yönlü Senkronizasyon (Watchlist & Favoriler):**
  - Sayfalı olarak çekilen Watchlist ve Favori içerikleri yerel kütüphane ile senkronize edilir.
  - Uzaktan yazma işlemlerinde (Watchlist / Favori ekleme-çıkarma) gereksiz ağ trafiğini önleyen akıllı alan doğrulaması (`CrossWriteVerificationGate`) devrededir; aynı değere tekrar istek atılmaz.
- **0,5 Adımlı Resmî Puanlama Desteği:**
  - TMDB standartlarına uygun 0,5 – 10,0 aralığında puan verme, güncelleme ve puan kaldırma desteği eklendi. Puan silme işlemi resmî `DELETE rating` uç noktasını kullanır.
- **Özel Film Listeleri Yönetimi (`KitsugiTmdbListsDialog`):**
  - Kullanıcının TMDB üzerindeki özel film listeleri taranır; resmî `item_status` ile üyelik kontrolü yapılır.
  - Uygulama içinden yeni TMDB listesi oluşturma, listeye film ekleme ve çıkarma işlemleri tam entegre çalışır.
- **Listem ve Profil Ekranı Deneyimi:**
  - Bağımsız **TMDB** sekmesi eklendi; TMDB'ye özgü içerikler, Watchlist ve puanlar bu sekmede görüntülenir.
  - Çift eksenli gruplama ve sıralama mimarisi TMDB sekmesiyle %100 uyumludur.
  - Profil ekranında TMDB hesap özeti kartı ve istatistikleri sunulur.
- **Akıllı Bildirimler & Hata Kataloğu:**
  - TMDB hesap eklemeleri için yerel bildirim arşivi ve bildirim merkezinde ayrılmış sekme (`NotifPlatform.TMDB_ACCOUNT`) sağlandı.
  - `TMD_7005` (Geçersiz/süresi dolmuş istek), `TMD_7006` (Oturum/yazma izni reddi), `TMD_7007` (Hesap değişikliği reddi) ve `TMD_7008` (TMDB bakımda) hata kodları kullanıcı dostu çözüm açıklamalarıyla sisteme entegre edildi.

---

### 2. Room Veritabanı v31 Mimarisi
- **Kalıcı Alanlar:** `MediaEntryEntity` tablosuna `tmdbWatchlist` (INTEGER) ve `tmdbRating` (REAL) alanları eklendi.
- **Kusursuz Geçiş Yolları:** Üretimdeki v30 şemasından v31'e (`MIGRATION_30_31`) ve doğrudan v29'dan v31'e (`MIGRATION_29_31`) veri kayıpsız geçiş sağlandı.
- **Yedekleme & Geri Yükleme:** JSON yedekleme formatı (`MediaEntryBackup`) TMDB Watchlist ve puan alanlarını destekleyecek şekilde güncellendi.

---

## 🇬🇧 English Release Notes

### 1. TMDB Full Account Integration (Watchlist, Favorites, Ratings & Lists)
- **Official v3 Session & Authentication Flow (`TmdbAuthStore` & `TmdbAccountClient`):**
  - Implemented the official v3 `request_token` → browser user authorization → `session_id` → `account_id` lifecycle.
  - Safeguards against expired, consumed, or mismatched callback tokens.
  - Secure credential storage and verified active account validation.
- **Strict Movie and TV Show Segregation (`TmdbAccountContract`):**
  - Separate handling for `movie` and `tv` subject types matching TMDB API requirements.
  - Decoupled catalog cache from real user account rows (`tmdb_account`), eliminating duplicate cards and erroneous cross-provider merges in MyList.
- **Bi-Directional Watchlist & Favorite Synchronization:**
  - Paginated retrieval and synchronization of remote Watchlist and Favorite items.
  - Smart write verification (`CrossWriteVerificationGate`) prevents redundant network requests for identical values.
- **0.5-Step Rating Support:**
  - Native 0.5 to 10.0 rating increments with official `DELETE rating` support when unrating titles.
- **Custom Movie Lists (`KitsugiTmdbListsDialog`):**
  - Browse personal TMDB lists with official `item_status` membership checks.
  - Create new lists, add movies, or remove entries directly from the media editor.
- **MyList & Profile Screen Integration:**
  - Added dedicated **TMDB** tab to MyList with full pull-to-refresh, login prompts, and empty states.
  - Complete parity with dual-axis grouping and intra-section sorting.
  - TMDB summary, stats, and quick links integrated into the Profile screen.
- **Smart Notifications & Error Management:**
  - Local notification archiving for account additions under a dedicated tab (`NotifPlatform.TMDB_ACCOUNT`).
  - Added diagnostic codes `TMD_7005` (Invalid/expired token), `TMD_7006` (Write permission denied), `TMD_7007` (Rejected change), and `TMD_7008` (TMDB maintenance) with actionable recovery guidance.

---

### 2. Room Database v31 Architecture
- **Schema Updates:** Added `tmdbWatchlist` (INTEGER) and `tmdbRating` (REAL) columns to `MediaEntryEntity`.
- **Seamless Migrations:** Guaranteed zero data loss transitions with `MIGRATION_30_31` and fallback `MIGRATION_29_31`.
- **Backup & Restore:** Full compatibility in `MediaEntryBackup` for archiving and restoring TMDB account attributes.
