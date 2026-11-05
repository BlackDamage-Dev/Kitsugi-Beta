# Kitsugi-Beta v2.4.440

## 🌟 MDBList Detay Sayfası Puanları, Sağlayıcı Kimlik Çözümü & Yenileme İyileştirmesi

Bu sürümde, API arama sonuçlarından ve yerel kütüphaneden açılan medya detay sayfalarında MDBList puanlarının (IMDb, Rotten Tomatoes, Metacritic, Letterboxd, TMDB, Trakt) güvenilir şekilde yüklenmesi ve ayar değişikliklerinde anında güncellenmesi sağlandı.

### 🌟 Öne Çıkan Değişiklikler

1. **Dinamik Ayar Takibi & Nesil Kontrolü**:
   - Hem `ApiResultDetailViewModel` hem de `MediaEntryDetailViewModel`, MDBList açık/kapalı durumu ile API anahtarı değişikliklerini reaktif olarak izler (`observedMdbListConfig`).
   - Ayar değiştiğinde aktif içerik için puan sorgusu otomatik olarak yenilenir.
   - İstek nesli (`mdbListFetchGeneration`) ve iptal denetimleri eklenerek eski asenkron yanıtların yeni sayfa state'ine sızması engellendi.

2. **Gelişmiş Sağlayıcı Kimlik Çözümleme**:
   - MAL, AniList, Bangumi, Kitsu, TMDB, Simkl ve Shikimori kimlikleri kendi ad alanlarında ayrıştırılır; kimlikler birbirine karıştırılmaz.
   - Detay metadata isteği henüz tamamlanmadıysa ve IMDb/TMDB kimliği ilk etapta bulunamadıysa, detay tamamlanana kadar zaman tavanlı (`TAB_FETCH_TIMEOUT_MS`) olarak beklenip harici bağlantılardan tekrar taranır.

3. **MDBList İstemci İyileştirmeleri & Yedek Sorgu Zinciri (`MdbListClient`)**:
   - Birleşik ve sağlam uç nokta yapısına geçildi (`https://mdblist.com/api/?apikey=...&i=...` / `&tmdb=...`).
   - IMDb sorgusuyla başlanır; yanıt boşsa veya başarısız olursa TMDB kimliğiyle otomatik olarak yeniden denenir (`selectRatingsWithFallback`).
   - Dizi (`ratings`/`scores` dizisi) ve nesne (`ratings` objesi) formatları, eski alan adları ve yaygın servis takma adları (`popcorn`, `rottentomatoes`, `themoviedb`, `themoviedatabase`) tam olarak desteklenir.
   - Sıfır puanlar "puan yok" olarak ele alınarak kartta anlamsız 0 rozetleri oluşması engellenir.
   - Kalıcı MDBList API kota takibi (`MdbListQuotaCenter`) ve tükenme koruması eksiksiz korundu.

4. **Kapsamlı Test Kapsamı**:
   - `MdbListClientTest` sınıfında 21 izole birim testi (%100 başarı, 0 hata, 0 atlama) doğrulandı.

### 📦 İndirme
- **APK**: `Kitsugi-Beta-v2.4.440-foss.apk` (116.0 MB)
