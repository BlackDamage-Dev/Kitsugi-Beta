# TR Türkçe Sürüm Notları — Kitsugi Beta v2.4.402

### 🌐 Kaynak-Bağımsız Kanonik Başlık Evreni (MAL ↔ AniList Eşitlemesi)

- **Sorun:** Aynı anime AniList kaydından açıldığında eklentiler (Anizm, TrAnimeİzle, AnimeciX vb.) akış sağlarken, MyAnimeList (MAL) kaydından açıldığında akış bulunamıyordu.
- **Kök Neden:** MAL detay istemcisinin (`KitsugiMalDetailClient`) `titleRomaji` ve `titleNative` alanlarını boş bırakması ve eklentilere giden arama varyant kümesinin aşırı daralmasıydı. Benzer başlık eksiklikleri Simkl ve Shikimori istemcilerinde de tespit edildi.
- **MediaDetailTitleHarmonizer:** Tüm veri kaynakları (AniList, MAL, Simkl, Shikimori, TMDB, Bangumi, Kitsu) ortak bir harmonizasyon katmanından geçirildi. İngilizce, Romaji, Native/Japonca, Türkçe ve tüm eş anlamlı varyantlar (`synonyms`) birleştirilip büyük/küçük harf ve noktalama işaretlerine göre normalize edildi.
- **CanonicalTitleAliases:** AniList ve Jikan v4 kimlikleri üzerinden kanonik başlık evreni oturum önbelleğiyle bağlandı; 160+ eklenti tek bir birleşik başlık havuzunu paylaşıyor. Artık anime MAL, AniList ya da başka bir kaynaktan açılsa da eklentilere giden varyant havuzu birebir aynıdır.
- **KitsugiMalDetailClient & Simkl:** MAL istemcisinde `titleRomaji` ve `titleNative` (`titleJapanese`) alanları doğrudan dolduruldu; Simkl istemcisinde romaji ve native başlık atamaları tamamlandı.

---

### ⚡ Kapsamlı Tek Eklenti Akış Araması (Scoped Extension Search)

- **Tek Eklenti Odaklı Arama:** Kullanıcı bir eklentinin kendi sayfası veya keşfet akışı üzerinden "İzle" seçtiğinde, arama yalnızca o eklenti bünyesinde tutulur (`StreamSourceScope`). Diğer eklentiler ve harici kaynaklar kendiliğinden listeye karışmaz.
- **İki Aşamalı Çözümleme:** Önce doğrudan sağlayıcı URL'si (`getStreamsForUrl`), akış dönmezse aynı eklentinin kendi başlık araması (`getStreams`) çalıştırılır.
- **"Tüm kaynaklarda ara" Şeridi:** Akış ekranına "Yalnızca X eklentisinde arandı" bilgi şeridi ve "Tüm kaynaklarda ara" eylem butonu eklendi. Kullanıcı istediği zaman tek tıkla aramayı tüm kaynaklara genişletebilir.
- **Oynatıcı İçi Bölüm Geçiş Uyumu:** Tam ekran oynatıcıda (`KitsugiPlayerViewModel`) sonraki bölüme geçerken de eklenti kapsamı korunur (önce doğrudan URL, sonra aynı eklenti başlığı; kaynak bulunamazsa kesintisiz izleme için ortak aramaya düşülür).

---

# EN English Release Notes — Kitsugi Beta v2.4.402

### 🌐 Cross-Source Canonical Title Universe (MAL ↔ AniList Parity)

- **Problem:** Opening an anime from AniList retrieved streaming sources from extensions (Anizm, TrAnimeIzle, AnimeciX, etc.), whereas opening the exact same anime from MyAnimeList (MAL) yielded zero results.
- **Root Cause:** The MAL detail client (`KitsugiMalDetailClient`) left `titleRomaji` and `titleNative` blank, leading to an overly narrow variant query set sent to video plugins. Similar title discrepancies existed in Simkl and Shikimori clients.
- **MediaDetailTitleHarmonizer:** All detail metadata sources (AniList, MAL, Simkl, Shikimori, TMDB, Bangumi, Kitsu) are now harmonized through a central engine. English, Romaji, Native/Japanese, Turkish, and synonyms are merged, deduplicated, and normalized.
- **CanonicalTitleAliases:** Session-cached canonical title mapping across AniList and Jikan v4 IDs ensures all 160+ plugins share the unified title universe regardless of entry point.
- **Client Alignments:** `KitsugiMalDetailClient` and `KitsugiSimklDetailClient` now properly populate `titleRomaji` and `titleNative`.

---

### ⚡ Scoped Extension Stream Search

- **Dedicated Extension Scope:** When launching playback directly from an extension's details/browse screen, stream searching remains strictly scoped to that extension (`StreamSourceScope`), preventing unwanted provider cross-talk.
- **Two-Tier Resolution:** Executes direct provider URL resolution first, falling back gracefully to the same extension's internal title search.
- **"Search all sources" Banner:** Added a dedicated notice banner with a one-click action to expand search across all installed plugins and Stremio addons on demand.
- **Player Episode Progression:** Fullscreen player (`KitsugiPlayerViewModel`) preserves the scoped provider for subsequent episodes before falling back to shared providers if necessary.
