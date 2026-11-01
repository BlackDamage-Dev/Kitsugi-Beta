# Kitsugi-Beta — Sürüm Notları / Release Notes

## Kitsugi Beta v2.4.393

### 🇹🇷 Türkçe

#### 🎨 1. 3B Cam & Gradyan İkon Seti (3dicons CC0)
- **50 Adet 3B Cam İkon:** Tüm sistem konu ve başlık ikonları (≥20 dp) 192×192 yüksek çözünürlüklü, saydam 3B cam/gradyan nesnelere dönüştürüldü.
- **Otomatik Vurgu Ton Kaydırması (Hue Rotation):** İkonlar yalnızca etrafındaki hâleyle değil, kendileri de seçtiğiniz tema vurgu rengine otomatik olarak döner (LocalKitsugiIconHueShift). Menekşe, mint, turkuaz, pembe, turuncu vb. hangi rengi seçerseniz seçin cam/3B parlaklığı bozulmadan ikon tonu o renge uyarlanır.
- **İki Renkli Gradyan Desteği:** Renk seçicide iki renkli gradyan seçildiğinde ikon tonu otomatik olarak iki rengin ortasından hesaplanır.
- **Renksiz / Düşük Doygunluk Koruması:** Gri, beyaz veya siyah gibi doygunluğu %12'nin altında olan vurgularda ikon paleti rastgele renge dönüştürülmez; doğal canlı mor-pembe kimliğinde kalır.
- **Karanlık, AMOLED ve Açık Tema Desteği:** Koyu ve AMOLED temalarda ikonlar doğal cam parlaklığında durur; açık temada arkalarına plaka konulmaz, zarif bir radyal yumuşak gölge eklenir.
- **Gezinme Efektleri:** Alt bar ve navigasyon rayında seçili sekmede yumuşak vurgu rengi arka plan çipi + radyal vurgu hâlesi (ccentGlow) ve %100 opaklık; seçilmeyen sekmelerde %80–86 opaklık uygulanır.
- **Ayarlar Sayfaları İyileştirmesi:** Ayarlar listelerindeki eski 42 dp daire zemin plakaları kaldırıldı, ikonlar doğrudan satırda saydam ve ferah bir şekilde hizalanır (KitsugiRowIcon).
- **Tema Açma/Kapama Seçeneği:** KitsugiAnimeListTheme(iconsFollowAccent = true) varsayılan olarak açıktır.

#### ⛩️ 2. Bangumi Kapsamlı Düzeltme & API Entegrasyonu (R1–R28)
- **Arama & Profil Navigasyon Düzeltmesi (R28):** Arama ekranında Karakter ve Personel sekmesindeki Bangumi sonuçlarına tıklandığında AniList kimlik uzayında aranması ve yanlış/boş profil açılması hatası düzeltildi; kaynak parametresi uçtan uca taşınarak doğru istemci çağrılır.
- **Canlı API Doğrulamaları:** POST /v0/search/persons ve /v0/search/characters uç noktaları; seslendirmen (seiyu), mangaka, illüstratör ve yapım stüdyosu (şirket) filtreleriyle tam uyumlu hale getirildi.
- **Keşfet, Sıralama ve Detay Sayfaları:** Bangumi kaynaklı anime/manga içeriklerinin sıralama, filtreleme, detay sekmeleri ve kütüphane entegrasyonu optimize edildi.

---

### 🇬🇧 English

#### 🎨 1. 3D Glass & Gradient Icon Set (3dicons CC0)
- **50 High-Definition 3D Glass Icons:** System topic and screen icons (≥20 dp) are upgraded to 192×192 transparent 3D glass/gradient artwork (3dicons, CC0).
- **Dynamic Accent Hue Rotation:** Icons automatically adapt their hue to match your selected theme accent color (LocalKitsugiIconHueShift). Whether using mint, violet, turquoise, pink, or orange, the icons seamlessly blend with your palette while preserving lighting and 3D depth.
- **Two-Color Gradient Support:** When a two-color gradient accent is selected, the icon hue is automatically interpolated from the midpoint of the two colors.
- **Colorless / Low-Saturation Protection:** If a neutral or monochrome accent is picked (saturation < 12%), hue rotation is bypassed, preserving the natural vibrant magenta-violet palette.
- **Dark, AMOLED & Light Theme Tuning:** Icons glow naturally in Dark and AMOLED themes; in Light theme, subtle radial drop shadows ensure clear contrast without clumsy background plates.
- **Tab & Navigation Polish:** Selected navigation items feature a soft accent background pill + radial glow (ccentGlow) at 100% alpha; unselected items sit gently at 80–86% alpha.
- **Refined Settings Rows:** Clunky 42 dp circular background plates have been removed from settings items in favor of clean, transparent KitsugiRowIcon presentation.
- **Theme Option:** Enabled by default via KitsugiAnimeListTheme(iconsFollowAccent = true).

#### ⛩️ 2. Bangumi Overhaul & Navigation Fixes (R1–R28)
- **Character & Staff Navigation Fix (R28):** Fixed an issue where clicking Bangumi character or staff search results queried AniList ID space, leading to wrong or empty profiles. The source context is now passed end-to-end.
- **Verified API Endpoints:** Validated live endpoints for character, staff, mangaka, illustrator, and studio queries against the Bangumi v0 API contract.
- **Explore & Rankings Polish:** Enhanced Discover carousels, See All grids, and rankings for Bangumi entries.

---

## Kitsugi Beta v2.4.392

### 🇹🇷 Türkçe

#### ⚡️ 1. AniList Merkezi Hız Yöneticisi (`AniListGovernor`)
- **Ortak Hız Bütçesi:** Keşfet, arama, detay, bildirimler, takvim, kütüphane içe aktarımı ve liste yazımları (mutasyonlar) artık tek bir hız slotu üzerinden yönetilir. Farklı ekranların birbirini 429'a düşürmesi engellendi.
- **Dinamik ve Uyarlanabilir Aralık:** AniList'in fiilî 30 istek/dakika hız sınırına göre `X-RateLimit-Limit` başlığından istek aralığı dinamik hesaplanır (60 sn / limit ≈ 2,2 sn).
- **Proaktif Kota Koruma:** Kalan istek sayısı 1 veya 0'a düştüğünde, 429 hatası yemeden önce pencerenin sıfırlanması beklenir.
- **Akıllı Devre Kesici (Circuit Breaker):** Sunucu kesintisi ("temporarily disabled") ve 429 soğuma durumlarında tüm uygulama anında fail-fast moduna geçerek kapalı veya aşırı yüklü sunucuya istek yağdırmaz.
- **Platform Hız Sınırlayıcı Delegasyonu:** `PlatformRateLimiter` AniList için doğrudan `AniListGovernor`'a delege edildi; çift slot alma sorunu çözülerek yazma hızı normale döndürüldü.

#### 🔄 2. AniList İçe Aktarım Sonsuz Döngü Düzeltmesi (`AniListImportManager`)
- **Boş Sayfa Sonsuz Döngüsü Giderildi:** AniList'in son sayfada `hasNextPage=true` ve boş liste döndürmesi nedeniyle oluşan sonsuz sayfalama döngüsü engellendi (`emptyPageStreak >= 2` ve 200 sayfa tavanı).
- **Kısmi Aktarım Güvencesi (`ImportResult`):** Hız sınırı veya kesinti nedeniyle yarım kalan içe aktarımlarda kullanıcının mevcut listesi silinmez (`allowDelete = false`), eldeki kayıtlar birleştirilir ve kalanlar sonraki tura bırakılır.
- **Fark (Delta) Senkronizasyonu Korundu:** `fetchListsSince` delta sorgusu ve `updatedSince` filtresi korunarak yalnızca değişen kayıtların çekilmesi sağlandı.

#### ✍️ 3. Liste Yazma & Kimlik Çözümleme İyileştirmeleri (`AniListSyncManager`)
- **Var Olan Kayıtlar İçin Yazma Kilidi Açıldı:** AniList üzerinde zaten var olan liste kayıtları (`aniListEntryId != null`) için yerel kimlik uyuşmazlığı kontrolü nedeniyle yazma işleminin atlanması sorunu giderildi (sunucu zaten kendi listesindeki kimliği biliyor).
- **MAL Kimlik Doğrulama Esnekliği:** AniList'in kendi `Media.idMal` alanı autoriter çapraz referans kabul edildi; başlık/yıl farklarında yazımın engellenmesi önlendi (`synonyms` alias desteği, `yearNear` +/-1 yıl ve null yıl desteği).
- **Oturum Önbellek Temizliği:** Hesap bağlantısı kesildiğinde doğrulanmış medya kimlik önbellekleri sıfırlanır.

#### 🛡 4. Kuyruk ve Ağ İyileştirmeleri
- **`PendingSyncDrainer` Düzeltmesi:** Senkronizasyon kapalıyken veya bekleyen işlem yokken hatalı sayılan ve 5 hakkı tüketen başarı kriteri düzeltildi; 429 durumlarında retry bütçesi harcanmaz.
- **ANR Koruması (`KitsugiApiBase`):** `executeAniListQuery` içindeki bloklayan OkHttp çağrısı `withContext(Dispatchers.IO)` içine alınarak ana iş parçacığı donmaları (ANR) tamamen önlendi.
- **Teşhis Paneli Entegrasyonu:** `NotificationDiagnostics` sorguları ve profil GraphQL istekleri `AniListGovernor` hız havuzuna bağlandı.

---

### 🇬🇧 English

#### ⚡️ 1. Centralized AniList Rate Governor (`AniListGovernor`)
- **Unified Rate Budget:** Explore, search, detail, notification, airing calendar, list import, and mutation writes are now coordinated through a single rate limiter, preventing different subsystems from pushing each other into HTTP 429 limits.
- **Adaptive Request Intervals:** Calculates intervals dynamically from AniList's active degraded limit (30 req/min) via `X-RateLimit-Limit` headers (60s / limit ≈ 2.2s).
- **Proactive Rate Protection:** Pauses proactively when remaining requests reach ≤ 1 to avoid triggering 429 errors.
- **Smart Circuit Breaker:** Implements fail-fast cooldowns during API outages ("temporarily disabled") and 429 cooldowns across all network paths.
- **Rate Limiter Delegation:** `PlatformRateLimiter` delegates AniList slots directly to `AniListGovernor`, eliminating double-slot acquisitions and restoring full write speed.

#### 🔄 2. AniList Import Infinite Loop Fix (`AniListImportManager`)
- **Empty Page Infinite Loop Resolved:** Fixes AniList's edge-case behavior where the last page returns `hasNextPage=true` with an empty `mediaList` (`emptyPageStreak >= 2` break condition and 200 page ceiling).
- **Partial Import Protection (`ImportResult`):** Interrupted imports due to rate limits no longer clear or drop the local library; fetched items are safely merged (`allowDelete = false`).
- **Delta Sync Preserved:** Maintains incremental delta synchronization (`fetchListsSince`) to only query entries updated since the last check.

#### ✍️ 3. Mutation Writes & Identity Resolution (`AniListSyncManager`)
- **Unblocked Existing Entry Writes:** Removed blocking identity hurdles when updating/deleting list entries already verified by AniList (`aniListEntryId != null`).
- **MAL Cross-Identity Flexibility:** Treats AniList's own `Media.idMal` as authoritative, relaxing strict title/year requirements (`synonyms` aliases, `yearNear` +/-1 year, null year tolerance).
- **Session Cache Reset:** Clears verified media caches upon account disconnection.

#### 🛡 4. Queue & Network Performance
- **`PendingSyncDrainer` Fix:** Fixed failure classification when sync is idle or disabled; 429 rate limits no longer burn retry attempts.
- **ANR Protection (`KitsugiApiBase`):** Moved blocking query execution inside `executeAniListQuery` to `withContext(Dispatchers.IO)`, eliminating main-thread freezes across 56 call sites.
- **Diagnostics & Profile Integration:** Routed `NotificationDiagnostics` and profile GraphQL queries through the central rate governor.

---

## Kitsugi Beta v2.4.390

### 🇹🇷 Türkçe

#### 🔍 Arama Ortak Filtre Mimarisi (3. Tur İyileştirmeleri & Düzeltmeleri)
- **Puan Ölçeği Uyuşmazlığı ve Çift Yönlü Normalizasyon:** Ortak model 0–100 puan ölçeği kullanırken MAL, TMDB, Shikimori ve Bangumi 0–10 bekliyordu. "Puan 70+" seçildiğinde Jikan'a `min_score=70` veya TMDB'ye `vote_average.gte=70` gitmesi sebebiyle boş sonuç dönmesi sorunu `canonicalScoreToTen` ve `tenToCanonical` çift yönlü çeviricisi ile kökten çözüldü. Dört motorda da puan doğru ölçekte iletilir; ayrıca kaynakta seçilen 0–10 puan ortak kaydırıcıda otomatik olarak 10 katına (ör. 7.0 → 70) dönüştürülerek tam senkronize görünür.
- **Stüdyo & Kurum Arama Paneli (`StudioProducerSheet`):** Yapımcı, stüdyo ve kurumların seçilebildiği, anlık filtreleme ve arama destekli yeni alt panel entegre edildi:
  - AniList, MAL ve TMDB için kurum filtreleri eksiksiz bağlandı.
  - Kurum aramasını desteklemeyen kaynaklar (Shikimori, Bangumi vb.) için kullanıcı dostu bilgilendirme kartı eklendi.
- **AniList & Bangumi Gelişmiş Filtre Entegrasyonu:**
  - AniList "Lisansörler" çipi (`licensedBy_in`), "minimum etiket güveni" (`minimumTagRank`) ve cilt aralığı (`volumesGreater`/`volumesLesser`) facade üzerinden GraphQL isteğine bağlandı.
  - Bangumi "yalnızca çizgi roman/manga" anahtarı ve minimum puan filtresi motor seviyesinde bağlandı.
- **Ortak Aralıklar:** Yıl ve puan aralıkları tüm desteklenen kaynak motorlarına standart biçimde beslenir.

#### 📺 Yayın Ekranı Kaynak Paneli & Dinamik Sıralama (`StreamSourceSheet`)
- **Modern Kaynak & Sıralama Paneli (`StreamSourceSheet`):** Yayın seçimi ekranında sağ üstteki butonla açılan, kaynakların durumunu ve akış seçeneklerini yöneten yeni alt panel:
  - Kaliteye göre sıralama (4K, 1080p, 720p...)
  - Dosya boyutuna göre sıralama
  - Eklenti ping süresine ve varsayılan sıraya göre filtreleme
- **Kaydırmaya Duyarlı Başlık (`Scroll-Aware Header Reveal`):** Bölüm listesi veya kaynak listesi aşağı kaydırıldığında üst aksiyon çubuğu ve başlık yumuşak bir animasyonla belirir veya gizlenir.
- **Tüm Kaynakları Yeniden Yoklama (`refreshAll`):** Önbelleğe takılmadan veya tek tek yenilemek zorunda kalmadan tüm eklenti ve kaynakları tek dokunuşla sıfırdan yeniden tarama imkânı.
- **Ölü Buton Düzeltmesi:** Stremio eklentilerinde "⚡️" ön eki olmadığı için çalışmayan tekil eklenti yenileme butonu mantıksal olarak düzeltildi; CloudStream ve Stremio eklentileri doğru filtrelenerek yanıt vermeyen buton sorunu giderildi.

#### 📜 İzleme Geçmişi Yenilemesi & Eklenti Metin Temizliği
- **Eklenti Metin Temizleyici (`StreamLabelSanitizer`):** Eklentilerden ve kazıyıcılardan gelen `.fa-secondary{opacity:.4}` gibi stil sınıfları, HTML etiketleri ve CSS çöp karakterlerini temizleyen regex motoru:
  - Oynatıcı ses/altyazı parça adlarında ham stil kodlarının görünmesi engellendi.
  - İndirilenler ekranı ve indirme bildirim özetlerinde çöp metinlerin yer alması önlendi.
  - Eski ve yeni tüm geçmiş verileri gösterim ve yazma anında sterilize edildi.
- **İzleme Geçmişi 6 Sıralama Modu (`WatchHistoryQuery`):**
  - En Son İzlenen (varsayılan)
  - En Eski İzlenen
  - Başlık (A-Z)
  - Başlık (Z-A)
  - İlerleme Yüzdesi
  - İzleme Süresi
- **Dizi/Anime Bazlı Gruplama & Çoklu Seçim:**
  - Aynı dizinin veya animenin bölümleri tek başlık altında toplanabilir.
  - Uzun basışla çoklu seçim moduna geçilebilir, seçilenler topluca geçmişten temizlenebilir.
- **Silme İşlemini Geri Alma (Undo Bar):** Yanlışlıkla silinen geçmiş kayıtlarını anında geri getiren akıcı geri alma çubuğu.
- **İnsansı Zaman Etiketleri:** "5 dakika önce", "Dün", "Geçen hafta" gibi anlaşılır ve yerelleştirilmiş göreceli zaman etiketleri.
- **Geri Tuşu Davranışı:** Çoklu seçim veya arama açıkken sistem geri tuşuna basıldığında ekrandan çıkmak yerine önce aktif seçim veya arama kapatılır.

---

### 🇬🇧 English

#### 🔍 Search Common Filters Architecture (Round 3 Polish & Bugfixes)
- **Score Scale Discrepancy & Bidirectional Normalization:** kitsugi's canonical filter model operates on a 0–100 scale, while MAL, TMDB, Shikimori, and Bangumi expect 0–10. Queries like "Score 70+" were previously forwarding `min_score=70` to Jikan or `vote_average.gte=70` to TMDB, yielding empty results. This is fully resolved with `canonicalScoreToTen` and `tenToCanonical` bidirectional converters. All four search engines now receive correctly scaled scores, and scores set on individual engines reflect accurately on the canonical slider (e.g., 7.0 becomes 70).
- **Studio & Institution Sheet (`StudioProducerSheet`):** A new bottom sheet allowing users to search and select studios and production institutions:
  - Full institution filtering integrated for AniList, MAL, and TMDB.
  - Helpful descriptive state for sources that do not support institution filtering (Shikimori, Bangumi, etc.).
- **AniList & Bangumi Advanced Filters:**
  - AniList "Licensors" chip (`licensedBy_in`), "minimum tag rank" (`minimumTagRank`), and volume range (`volumesGreater`/`volumesLesser`) are wired directly to GraphQL queries.
  - Bangumi "Manga only" switch and minimum score filters are directly passed to the search engine.
- **Standardized Ranges:** Year and score range filtering across all supported search engines.

#### 📺 Stream Screen Source Panel & Dynamic Sorting (`StreamSourceSheet`)
- **Modern Source & Sorting Sheet (`StreamSourceSheet`):** A dedicated bottom sheet accessible from the top-right action button on the stream selection screen:
  - Sort streams by resolution/quality (4K, 1080p, 720p, etc.)
  - Sort by file size
  - Filter and sort by plugin ping latency or default priority
- **Scroll-Aware Header Reveal:** The top action bar and title dynamically and smoothly appear or collapse as the user scrolls through episodes or stream results.
- **Force Refresh All Sources (`refreshAll`):** Perform a complete re-fetch across all plugins and scrapers with a single tap, bypassing cache misses and stale results.
- **Dead Button Resolution:** Fixed a silent failure where Stremio plugins (lacking the "⚡️" prefix) could not be re-probed individually. CloudStream and Stremio plugins are now properly distinguished.

#### 📜 Watch History Overhaul & Stream Text Sanitization
- **Stream Text Sanitizer (`StreamLabelSanitizer`):** Robust regex-based cleaning engine removing scraper artifacts, CSS classes like `.fa-secondary{opacity:.4}`, and HTML fragments:
  - Cleaned stream audio and subtitle track labels inside the video player.
  - Sanitized episode titles in the Downloads queue and system notifications.
  - Retrospective and runtime sanitization across all watch history records.
- **6 Watch History Sorting Modes (`WatchHistoryQuery`):**
  - Recently Watched (default)
  - Oldest Watched
  - Title (A-Z)
  - Title (Z-A)
  - Completion Percentage
  - Watch Duration
- **Series Grouping & Multi-Select:**
  - Cleanly collapse episodes of the same show under a single group header.
  - Multi-select mode with bulk deletion.
- **Undo Snackbar Bar:** Immediate undo action for accidental deletions.
- **Humanized Relative Timestamps:** Friendly localized timestamps ("5 minutes ago", "Yesterday", "Last week").
- **Back Key Navigation:** Pressing the system back button smoothly dismisses multi-selection or search before navigating away.

---

## Kitsugi Beta v2.4.388

### 🇹🇷 Türkçe

#### 📥 İndirilenler Ekranı & Çevrimdışı Deneyim İyileştirmeleri
- **İçeriğe Göre Gruplu Görünüm:** Aynı yapımın bölümleri tek başlık altında toplanır, rozetli bilgi kartı ("n bölüm • m tamamlandı • toplam boyut") sunulur, dokununca kolayca daraltılıp açılabilir ve grup başlığından tüm bölümler tek dokunuşla seçilebilir.
- **Kuyruk Önceliği Taşıma:** Bekleyen indirmeler yukarı/aşağı taşınabilir (`AnimeDownloadManager.moveQueuedDownload()`); kuyruktaki sıra serbestçe belirlenebilir. Sadece bekleyen indirmeler arasında güvenli takas yapılır; devam eden veya tamamlanan kayıtların konumu bozulmaz.
- **Gelişmiş Sıralama Seçenekleri:** Kuyruk sırası (varsayılan), Başlık, Bölüm, Boyut ve En Yeni ölçütlerine göre sıralama imkânı. Yapılan sıralama tercihi kalıcı olarak hatırlanır.
- **Çoklu Seçim & Toplu Silme:** Uzun basışla seçim modu tetiklenir, tümünü seç seçeneğiyle onaylı toplu temizlik yapılabilir. Altyazı ve videolarda Scoped Storage / MediaStore onay akışı eksiksiz korunur.
- **Hız Geçmişi Grafiği (Sparkline):** İndirilenler özet kartında son ~40 saniyenin anlık indirme hızını gösteren dinamik mini Canvas sparkline grafiği eklendi (saniyede 1 örnekleme, sıfır recomposition yükü).
- **Sezgisel Geri Tuşu Yönetimi:** Geri tuşu önce çoklu seçim modunu, ardından arama çubuğunu kapatacak şekilde akıcı hâle getirildi.

#### ⚡ Listem ANR Çözümü & Arka Plan Açılış Bakımı
- **Ana Thread Yükü Sıfırlandı:** Listem açılışındaki tüm ağır bakım işleri (`Dispatchers.IO` / `Dispatchers.Default`) arka plan iş parçacıklarına taşındı:
  - TMDB eski katalog satırı onarımı
  - Simkl subtitle ve kaynak etiketi onarımı
  - Kitsu kimlik onarımı
  - Aynı kaynak duplicate temizliği
  - +18 (yetişkin) bayraklarının kimlik üzerinden tamamlanması
  - `addedAt` eksik zaman damgası onarımı (iptal yutma hatası giderildi)
- **SameSourceDuplicatePlanner (İndeksli Planlama):** Kütüphanedeki aynı kaynak kopyaları artık $O(N^2)$ tarama yerine kimlik/başlık token indekslemesi ile anında filtrelenir; planlama ve temizleme UI thread'ini asla dondurmaz.
- **Atomik Veritabanı İşlemi:** Duplicate satır temizliği ve bayat bekleyen senkronizasyon işleri tek bir Room `@Transaction` altında birleştirilerek veri kaybı riski tamamen ortadan kaldırıldı.

#### 📝 KitsugiLog — Kalıcı ve Bloklamayan Tanı Günlüğü
- **Kalıcı Dosya Kaydı:** Sistem logcat'inin dolup silinmesi veya uygulamanın kapanması durumunda dahi tüm kritik adımlar `files/diagnostics/kitsugi_runtime.log` dosyasında korunur (4 MB döner dosya, ~8 MB tavan disk alanı).
- **Sıfır UI Etkisi:** Disk yazımı tek bir düşük öncelikli arka plan iş parçacığında (`KitsugiLogIO`) bloklamasız kuyrukla gerçekleşir.
- **Canlı Bellek Tamponu:** Bellekte son 3.000 satır saklanır; Ayarlar ▸ Sistem ▸ Geliştirici Günlükleri ekranında anında filtrelenebilir.
- **Tekrar Sıkıştırma:** Arka arkaya gelen mükerrer log satırları birleştirilerek disk ve bellek tasarrufu sağlanır.

#### ⏱️ UiFreezeWatchdog — UI Donma Takibi
- Ana iş parçacığı arka planda hafif pinglerle sürekli gözetlenir.
- 2.5 saniyeyi aşan donmalarda anlık yığın izi (stack trace) yakalanıp `FREEZE` kanalı altında kaydedilir; 8 saniyenin üzerindeki takılmalar doğrudan kritik hata olarak işaretlenir.

#### 🌐 Ağ Denetimi & AniListGate Kota Kapısı
- **KitsugiNetworkLogInterceptor:** Host, uç nokta yolu, HTTP durum kodu, milisaniye cinsinden gecikme ve `Retry-After` / `X-RateLimit-*` başlıkları hassas bilgiler (token/şifre) filtrelenerek denetlenir.
- **AniListGate:** 60 saniyelik kayan pencere (sliding window), HTTP 429 soğuma süresi koruması ve kullanıcı/arka plan önceliklendirmesi.
- **PendingSyncDrainer:** Arka plan kuyruğu kota cezası aldığında gereksiz istek atmak yerine soğuma süresini bekleyip kendiliğinden devam eder.

#### 🌸 Bangumi & Teşhis Doğruluğu İyileştirmeleri
- **Bangumi 404 Negatif Önbelleği:** Kesin 404 yanıtları için kısa ömürlü negatif önbellek uygulandı; zaman aşımı ve iptal durumları önbelleğe alınmaz.
- **Gelişmiş ANR ve Çökme Raporu:** Sistem ANR kaydı varken logcat boş diye "sebep bulunamadı" denmez; ANR yığın izinde ana thread önceliklendirilir; JVM heap miktarı cihaz RAM'i gibi gösterilmez.

---

### 🇬🇧 English

#### 📥 Downloads Screen & Offline Experience Enhancements
- **Content-Grouped Video View:** Episodes of the same anime or show are cleanly grouped under collapsible title headers with badge counters ("n episodes • m completed • total size"). Episodes can also be multi-selected directly from the group header.
- **Queue Priority Reordering:** Change the priority of pending downloads using Move Up / Move Down controls (`AnimeDownloadManager.moveQueuedDownload()`). Swapping strictly adjusts QUEUE records without disturbing active or finished downloads.
- **Persistent Video Sorting:** Sort by Queue Order (default), Title, Episode Number, File Size, or Newest. Preferences are preserved across app restarts.
- **Multi-Select & Batch Deletion:** Long-press to activate multi-selection mode, select all items, and perform confirmed batch deletions with full Android Scoped Storage / MediaStore permission handling.
- **Real-Time Speed Sparkline:** A smooth mini Canvas sparkline on the overview card visualizes download speeds over the last ~40 seconds (sampled once per second with zero recomposition flooding).
- **Refined Back Navigation:** Pressing back dismisses active selection mode first before closing search.

#### ⚡ Listem ANR Resolution & Background Maintenance
- **Off-Main Thread Execution:** All heavy startup maintenance routines are moved off the UI thread to `Dispatchers.IO` / `Dispatchers.Default`:
  - TMDB legacy catalog row migrations
  - Simkl subtitle and source tag repairs
  - Kitsu identity corrections
  - Same-source duplicate cleanup
  - +18 adult flag backfills
  - `addedAt` timestamp backfills (cancellation propagation fixed)
- **SameSourceDuplicatePlanner:** Replaces $O(N^2)$ candidate searches with token-indexed candidate buckets, preventing main-thread stalls on large libraries.
- **Atomic Deduplication Transaction:** Merging survivor rows, deleting duplicates, and clearing stale queued sync jobs now happen inside a single atomic Room transaction.

#### 📝 KitsugiLog — Persistent & Non-Blocking Diagnostics
- **Persistent Storage:** Diagnostics are retained in `files/diagnostics/kitsugi_runtime.log` (4 MB rolling file, ~8 MB max disk footprint) even if system logcat buffers clear or the app restarts.
- **Zero UI Overhead:** Disk I/O runs on a dedicated low-priority daemon worker thread (`KitsugiLogIO`) through a non-blocking queue.
- **Live Memory Buffer:** Retains the last 3,000 log events in memory for real-time inspection via Settings ▸ System ▸ Developer Logs.
- **Repeated Line Compression:** Sequential duplicate log lines are merged with repetition counters.

#### ⏱️ UiFreezeWatchdog — Main Thread Hang Detection
- Probes the main Looper thread with continuous heartbeats.
- Detects UI hangs >2.5s and logs the active stack trace under the `FREEZE` channel; stalls exceeding >8s are elevated to critical errors.

#### 🌐 Network Diagnostics & AniListGate Rate Limiting
- **Network Audit Log:** Logs endpoint paths, HTTP codes, duration, and rate-limit headers (`Retry-After`, `X-RateLimit-*`) while scrubbing auth headers.
- **AniListGate:** Implements a sliding 60-second window, respects HTTP 429 cooldowns, and prioritizes interactive actions over background sync jobs.
- **PendingSyncDrainer:** Cooldown-aware sync queue scheduling avoids rate limit penalties.

#### 🌸 Bangumi & Diagnostic Accuracy Polish
- **Bangumi 404 Negative Cache:** Avoids repeated lookups on definitive 404s without polluting cache on timeouts or network cancellations.
- **Refined Crash & ANR Reporting:** Prioritizes main-thread ANR stack traces, accurately correlates system exit records, and clarifies JVM heap vs device RAM.

---

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

# Kitsugi Beta v2.4.359

## 🇹🇷 Türkçe

### 📱 Detay Sayfalarında Animasyonlu Mini Üst Bar Geri Getirildi
- **Kompakt Üst Bar Aktivasyonu:** Sayfa aşağı kaydırıldığında sekme bandının üzerinde **geri, yapım başlığı, galeri, paylaş ve favori** aksiyonlarını içeren kompakt mini bar yeniden görünür hale getirildi.
- **Kök Neden Düzeltmesi:** Medya detay düzeninde dikey `LazyColumn` vitrin (0), sekmeler (1) ve pager içeriği (2) olmak üzere 3 öğeden oluşuyordu. Eski kodun olmayan 4. öğeyi (`showAtIndex = 3`) beklemesi nedeniyle tetiklenemeyen bar, standart `showAtIndex = 2, hideAtIndex = 0` eşiklerine bağlandı.
- **Akıcı Histerezis Animasyonu:** Mevcut `KitsugiStickyHeaderReveal` (dikey açılma, kayma ve solma) animasyonu korundu; vitrine geri dönüldüğünde yumuşakça kapanır ve sekme sınırında titreme yapmaz.
- **Tüm Detay Sayfaları Kapsamı:** Arama, Keşfet ve Listem'den açılan tüm medya detayları (`DetailPageScaffold`), karakter detayları (`CharacterDetailPage`) ve ekip detayları (`StaffDetailPage`) ortak görünürlük politikasına bağlandı; stüdyo detaylarındaki mevcut animasyonlu bar korundu.
- **Regresyon Testleri:** Eşik, histerezis, geri yükleme ve sınır durumları için beş adet birim testi (`DetailStickyHeaderVisibilityTest`) eklendi ve başarıyla doğrulandı.

---

## 🇬🇧 English

### 📱 Animated Mini Toolbar Restored on Detail Pages
- **Compact Floating Toolbar Activation:** Scrolling down reveals the compact toolbar above the tab strip with **back, media title, gallery, share, and favourite** actions.
- **Root Cause Fix:** The vertical detail `LazyColumn` has exactly 3 rows: hero (0), sticky tabs (1), and pager content (2). The previous code was expecting a non-existent fourth row (`showAtIndex = 3`). Visibility thresholds are now aligned to `showAtIndex = 2, hideAtIndex = 0`.
- **Smooth Hysteresis Motion:** Preserved `KitsugiStickyHeaderReveal` (vertical expansion, sliding, and fade) motion tokens. The toolbar collapses smoothly when returning to the hero without flickering at the tab strip boundary.
- **Universal Detail Page Scope:** Media details from Search, Explore, and My List (`DetailPageScaffold`), character details (`CharacterDetailPage`), and staff details (`StaffDetailPage`) now use the shared visibility policy; existing studio page toolbar behavior is retained.
- **Regression Coverage:** Added five unit tests in `DetailStickyHeaderVisibilityTest` covering thresholds, boundary hysteresis, and restored scroll positions.

---

# Kitsugi Beta v2.4.358

## 🇹🇷 Türkçe

### 📋 Listem Sıralaması — Kaynak Bazlı Eklenme Zamanı ve Deterministik Sıralama
- **Simkl Gerçek Eklenme Zamanı:** Simkl `/sync/all-items` yanıtındaki `added_to_watchlist_at` alanı `addedAt` alanına bağlandı. Önceden `last_watched_at` kullanıldığı için izlenen herhangi bir bölüm diziyi "listeye yeni eklenmiş" gibi en üste taşıyordu; artık gerçek eklenme anı korunuyor.
- **Bilinmeyen Kovası Onarımı (`repairMissingAddedAt`):** Listem açılışında `addedAt` boş ancak `updatedAt` dolu olan satırlar tek geçişte damgalanır. Böylece yapımların "Eklenme Tarihi Bilinmiyor" kovasına rastgele yığılması engellenir. Düzeltilecek satır yoksa veritabanına gereksiz yazım yapılmaz.
- **Deterministik "Son Eklenen / İlk Eklenen" Sıralaması:**
  - Zaman damgası bilinmeyen kayıtlar her iki yönde de listenin **en sonunda** yer alır.
  - Tarihsiz kayıtlar kendi aralarında yerel ID karmaşasına kapılmadan **başlığa göre alfabetik** sıralanır.
  - `updatedAt` artık eklenme sıralamasını bozan bir ikincil kırıcı olarak kullanılmaz.
- **Ortak Çözümleyici (`effectiveAddedSeconds`):** Hem sıralama motoru hem de dönem başlıkları aynı çözümleyiciyi kullanır; aynı kayıt bir sekmede tarihli iken "Tümü" sekmesinde bilinmiyor olamaz.
- **"Tümü" Kartı İlk Giriş Zamanı:** Birleşik kartlarda kopyalar arasındaki en erken bilinen eklenme anı (yapımın kütüphaneye ilk giriş anı) temel alınır.

### 🔤 Simkl İngilizce Başlıklar & Genişletilmiş Varyantlar
- **Genişletilmiş Başlık Ayrıştırıcı (`SimklTitleVariants`):** Metin aramalarına `extended=full` parametresi eklendi. İngilizce, Romaji ve Japonca başlıklar bağımsız olarak çözümlenir ve korunur.
- **Doğru Başlık Önceliği:** Ham Romaji başlığın `titleEnglish` alanına yazılması ve JSON `"null"` dizgilerinin başlık olarak gösterilmesi engellendi.

### ⚡ Detay Sayfası Performans & Akıcılık İyileştirmeleri
- **İzole Sekme Abonelikleri:** Karakter, ekip, ilişki, öneri ve bölüm StateFlow abonelikleri yalnızca ilgili pager sayfasına taşındı; arka plan güncellemelerinin tüm detay sayfasını yeniden kompoze etmesi engellendi.
- **Swipe Ölçüm İyileştirmesi:** Pager yüksekliğinin her swipe karesinde interpolasyon ve sınırsız-yükseklik ölçümü yapması kaldırıldı; kaydırma takılmaları giderildi.
- **Kademeli Yükleme (`DetailIncrementalList`):** Karakter, ekip, öneri ve ilişki listeleri ilk 12 öğeyle açılır; devamı "Daha Fazla Yükle" butonuyla yüklenerek ilk kare render süresi hızlandırıldı.
- **Donanım Hızlandırmalı Görseller:** Hero görsellerinde hardware bitmap etkinleştirildi (bitmap-blur yollarında software bitmap güvenliği korundu).
- **Hemen Açılan Sayfalar:** TMDB/Fanart hero görsel çözümlemesi arka plana taşınarak birincil detay hazır olduğu anda sayfanın anında açılması sağlandı. Liste kaydı taramaları `remember` ile memoize edildi.

### 🏢 Bangumi Kurum & Organizasyon Desteği
- **Güvenilir Kurum Yüklemesi (`BangumiOrganizationFallback`):** Stüdyo, yapımcı, yayıncı, ağ ve dergi detayları resmî v0 person/detail ve subjects uçlarıyla birincil olarak yüklenir; deneysel p1 eksik yapımları tamamlayan yedek olarak konumlandırıldı.
- **Grup ve Organizasyonlar:** Type-3 (grup/organizasyon) kayıtları desteklendi; p1 infobox'undaki `k` alt anahtarları ve ad varyantları indekslendi.

---

## 🇬🇧 English

### 📋 My List Sorting — Source-Based Added Timestamp & Deterministic Order
- **Simkl True Added Timestamp:** Now reads `added_to_watchlist_at` from `/sync/all-items`. Previously `last_watched_at` was used as a proxy, which erroneously pushed watched series to the top of "Recently Added"; actual addition time is now preserved.
- **Unknown Bucket Self-Healing (`repairMissingAddedAt`):** Rows with missing `addedAt` but valid `updatedAt` are stamped in a single pass on opening My List, preventing entries from accumulating in the "Unknown Date" bucket. No unnecessary database writes are performed when no repair is needed.
- **Deterministic "Recently Added / Oldest Added" Sorting:**
  - Items with unknown timestamps are placed at the **end of the list** in both sort directions.
  - Undated entries are sorted **alphabetically by title**, eliminating provider ID collisions across sources.
  - `updatedAt` no longer interferes with added-date ordering.
- **Unified Resolver (`effectiveAddedSeconds`):** Both sort comparators and section headers use the same resolver; an entry will never appear dated in one tab while classified as "Unknown" in "All".
- **Composite "All" Card Earliest Entry:** Combined media cards use the earliest known addition timestamp among provider copies (the exact moment the title first entered the library).

### 🔤 Simkl English Titles & Expanded Variants
- **Title Variants Parser (`SimklTitleVariants`):** Added `extended=full` to text searches. English, Romaji, and native titles are parsed and preserved independently.
- **Title Accuracy:** Prevents raw Romaji from copying into `titleEnglish` and filters out JSON `"null"` strings.

### ⚡ Media Detail Page Performance Optimizations
- **Isolated StateFlow Subscriptions:** Tab subscriptions are localized to active pager pages, preventing background data updates from re-composing the entire scaffold.
- **Swipe Measurement Optimization:** Removed per-frame height interpolation during horizontal pager swipes, eliminating unconstrained layout passes.
- **Incremental Tab Lists (`DetailIncrementalList`):** Characters, staff, recommendations, and relations load the first 12 items upfront with "Load More", reducing initial frame composition overhead.
- **Hardware-Accelerated Bitmaps:** Enabled hardware bitmaps for direct hero artwork while maintaining software bitmaps for blur effects.
- **Instant Page Display:** Offloaded TMDB/Fanart artwork preloading to the background so primary detail pages render instantly. Memoized entry lookup scans.

### 🏢 Bangumi Organization & Studio Enhancements
- **Reliable Fallback Architecture (`BangumiOrganizationFallback`):** Studios, producers, publishers, networks, and magazines load primarily via official v0 person/detail endpoints with p1 serving as a fallback for missing works.
- **Groups & Organizations:** Added type-3 (group/organization) support; indexed `k` subkeys in p1 infobox for comprehensive alias resolution.

---

# Kitsugi Beta v2.4.357

## 🇹🇷 Türkçe

### 🔔 Akıllı Bildirim Butonu — Sekme ve Kaynak Duyarlı Yönlendirme
- **Kaynak Odaklı Açılış:** Hangi sekmede hangi platform seçiliyse 🔔 butonuna basıldığında bildirimler doğrudan o kaynağın sekmesiyle açılır:
  - 🧭 **Keşfet:** Seçili platform sekmesi (AniList, MAL, TMDB/Simkl, Kitsu, Shikimori, Bangumi).
  - 🔍 **Arama:** Arama çubuğundaki seçili motor (Arama sekmesine eksik olan 🔔 butonu eklendi).
  - 👤 **Profil:** Seçili profil sağlayıcısı.
  - 📋 **Listem:** Seçili liste sağlayıcısı (Tümü seçiliyken varsayılan/bağlı ilk kaynak).
- **Akıllı Navigasyon & Durum Koruması:** `DetailScreen.Notifications` ve `AppStateKey.Notifications` artık kaynak bilgisini taşır; aynı kaynakta kaydırma durumu korunurken farklı kaynak taze hedef olarak açılır. Sayfa içinde kullanıcı elle başka sekmeye geçerse seçimi ezilmez.

### 🔁 Altı Kaynak Arası Eşitleme — "Değişiklik Yok" Hatası Giderildi
- **Kök Neden & Çözüm:** Eşitleme alan bazlı çalışırken hedefte kaydın var olup olmadığına bakılmıyordu. Kitsu'dan aktarılmış bir yapımda favori/etiket değiştiğinde diğer kaynaklarda kayıt hiç oluşmuyordu.
- **Kalıcı Varlık Kaydı (`RemoteListPresence`):** Her yapım ve sağlayıcı için uzakta kayıt varlığı kalıcı tutulur. Doğrulanana kadar tam yazım (upsert) yapılır; boş yerel alanlar gönderilmediğinden hedefteki dolu değerler korunur.
- **Onarım Turu:** Hiçbir alan değişmese dahi 10 dakikada en fazla bir kez tüm kaynaklara tam onarım turu çalışır; eksik kayıtlar kendiliğinden tamamlanır.
- **Dürüst Rapor:** "Değişiklik yok" etiketi yalnızca kayıt uzakta doğrulanıp gerçekten eşitse gösterilir ve adı **"zaten eşit"** oldu.

## 🇬🇧 English

### 🔔 Smart Notification Button — Tab & Source Context Routing
- **Context-Aware Navigation:** Tapping the 🔔 notification bell opens the notification screen directly on the currently active provider:
  - 🧭 **Explore:** Matches active explore platform (AniList, MAL, TMDB/Simkl, Kitsu, Shikimori, Bangumi).
  - 🔍 **Search:** Matches active search engine (added missing 🔔 button to Search header).
  - 👤 **Profile:** Matches active profile provider.
  - 📋 **My List:** Matches selected list provider (or default connected provider if "All" is selected).
- **Navigation State & Pager Memory:** `preferredSource` is propagated through `DetailScreen.Notifications` and `AppStateKey.Notifications`. Preserves scroll state when returning to the same source, and honors manual page swipes without forcing revert.

### 🔁 Six-Source Sync — "No Changes" Bug Fixed
- **Root Cause & Fix:** Writes were diff-based without verifying if remote libraries held the record. Entries imported from one provider skipped creation on others when changing unsupported fields like favorites.
- **Presence Tracking (`RemoteListPresence`):** Tracks verified remote entries. Unverified targets receive full upsert writes with non-empty local fields, guaranteeing entry creation without wiping remote custom data.
- **Self-Healing Repair Pass:** Throttled (≤1 per 10 min) full sync runs even with no local diffs so missing remote records self-heal.
- **Accurate Reporting:** Renamed "no changes" to **"already equal"**, displayed only when remote parity is confirmed.

---

# Kitsugi Beta v2.4.355

## 🇹🇷 Türkçe

### 🔁 Altı Kaynak Arası Eşitleme — "Değişiklik Yok" Hatası Giderildi
- **Kök neden:** Eşitleme alan bazlı çalışıyor ve her kaynak için "değişen alan yoksa istek atma" kuralı uygulanıyordu; **hedefte kaydın var olup olmadığı hiç kontrol edilmiyordu**. Kitsu'dan içe aktarılmış bir yapımda yalnızca favori/etiket değiştirdiğinizde Simkl · Kitsu · Shikimori · Bangumi "değişiklik yok" diyor, oysa kayıt bu kaynaklarda **hiç oluşmuyordu**.
- **Varlık kaydı:** Her (yapım, sağlayıcı) çifti için "hedefte kayıt VAR" bilgisi kalıcı tutulur. Kayıt doğrulanmadan alan süzmesi uygulanmaz; hedefe **tam yazım** gider (boş yerel alanlar gönderilmediği için hedefteki değerler silinmez).
- **Onarım turu:** Hiçbir alan değişmese bile 10 dakikada en fazla bir kez tüm kaynaklara tam eşitleme yapılır; eksik/eskimiş kayıtlar kendiliğinden oluşturulur ve düzeltilir.
- **Dürüst rapor:** "değişiklik yok" etiketi artık yalnızca hedefteki kayıt **gerçekten eşitse** gösterilir ve adı **"zaten eşit"** oldu; yazılmamış bir alan için başarı gösterilmez.
- **Hesap değişiminde temizlik:** Hesap koparıldığında/başka hesaba geçildiğinde varlık kayıtları temizlenir; yeni hesapta eksik kayıtlar "zaten eşit" denip atlanmaz.

## 🇬🇧 English

### 🔁 Six-Source Sync — "No changes" Bug Fixed
- **Root cause:** Writes were field-diffed and each provider skipped the request when no local field changed, **without ever checking whether the target actually held the entry**. On a Kitsu-imported title, changing only favourite/tags reported "no changes" for Simkl · Kitsu · Shikimori · Bangumi while those libraries had no record at all.
- **Presence tracking:** A persistent per-(title, provider) presence flag is kept. Until the record is confirmed, field filtering is bypassed and a **full write** is sent (empty local values are never sent, so remote values are preserved).
- **Repair pass:** Even with no local change, a throttled (≤1 per 10 min) full sync runs so missing/stale records self-heal.
- **Honest report:** The old "no changes" label is now only shown when the target really is equal, renamed to **"already equal"**.
- **Account switch cleanup:** Presence data is cleared on disconnect so a new account never inherits "already equal" claims.

---

# Kitsugi Beta v2.4.352

## 🇹🇷 Türkçe

### 🔍 Listem Kaynak Bazlı Arama & Kesin Sağlayıcı İzolasyonu
- **Sekme Arama İzolasyonu:** AniList, MyAnimeList, Simkl, Kitsu, Shikimori ve Bangumi sekmelerinde arama yapıldığında arama kutusunun seçili sağlayıcıyı yok sayarak tüm kütüphaneyi taraması (`useGlobalSearchResults`) tamamen kaldırıldı. Her sekme yalnızca kendi platformuna ait kayıtları arar, listeler, filtreler ve sayar.
- **Bağlantı ve Boş Durum Koruması:** Bağlı olmayan veya henüz senkronize edilmemiş kaynak sekmelerinde arama kutusu dolu olsa dahi "Hesabı Bağla" / "Senkronize Et" durumu korunur; yanlışlıkla boş sonuç listesine düşülmez.
- **Tek Geçişli Kütüphane Anlık Görüntüsü (`MyListLibrarySnapshot`):** Arka planda `Dispatchers.Default` üzerinde tek geçişte hem "Tümü" sekmesinin birleşik/tekilleştirilmiş kartları ve kaynak rozetleri hem de platform sekmelerinin (`entriesByTab`) kendi listeleri üretilir; arayüz kasmaları ve titremeler engellenir.

### 🌐 Kardeş Başlık Varyantları & Otomatik Zenginleştirme
- **Simkl, Shikimori & Bangumi Başlık Tamamlama:** Yalnızca Romaji veya CJK başlık döndüren servislerin eksik İngilizce/Latin başlıkları, aynı yapıma ait doğrulanmış kardeş grup üyeleri ve `MalAxisLibraryTitleHydrator` (AniList `idMal_in` toplu sorgusu) ile tamamlandı ("Kaiju No. 8" araması Simkl, Shikimori ve Bangumi sekmelerinde de ilgili kartı doğru bulur).
- **Kitsu Ters Kimlik Köprüsü:** `MyListIdentityCrosswalk` içerisine `reverseKitsuMapping` eklenerek Kitsu kayıtlarının MAL ekseniyle eksiksiz birleşmesi sağlandı.
- **Kaynak Onarım Koruması:** `MyListViewModel.repairMisclassifiedSources`, gerçek MAL kayıtlarını yanlışlıkla AniList kaynağına taşımayacak şekilde sınırlandırıldı.

### 🎯 Gürültüsüz & Hassas Başlık Araması
- **Meta Alan Gürültüsü Temizlendi:** Arama sorgusu `source`, `type.name`, `status` ve sentetik MAL kimlikleri (100M/300M/400M/500M) gibi alanlardan arındırıldı; yalnızca başlık varyantları ve 4 haneli yapım yılı üzerinde çalışır.
- **Gelişmiş Eşleme Algoritmaları:** Türkçe `İ/I/ı/i` katlama (`foldMyListSearchText`), noktalama duyarsız başlık eşleşmesi, tam sayı belirteç koruması (sezon/numara karışmasını önleme) ve güvenli kelime bazlı yazım hatası / kısaltma toleransı eklendi.

### 🏷️ Listem Kart Köşe Rozetleri (Görsel Netlik)
- **Köşelere Tam Oturan Rozetler:** Durum ve puan rozetleri poster kartının üst ve alt dış köşelerine sıfırlandı (`TopEndCornerShape`, `BottomEndCornerShape`).
- **Minimal Kaplama Alanı:** Dış kenarlardaki yuvarlaklıklar kaldırılıp yalnızca görsele bakan iç köşeler 6.dp yumuşatıldı; rozet içi boşluklar ve yazı boyutları optimize edilerek afişlerin kapanma oranı en aza indirildi.

---

## 🇬🇧 English

### 🔍 Source-Scoped Search & Strict Provider Isolation in MyList
- **Tab Search Isolation:** Eliminated global search leakage (`useGlobalSearchResults`) across individual provider tabs (AniList, MyAnimeList, Simkl, Kitsu, Shikimori, Bangumi). Each tab strictly filters, searches, sorts, and counts only its own provider entries.
- **State Preservation on Disconnected Tabs:** Unconnected or empty provider tabs consistently maintain their "Connect Account" / "Sync" landing state even when a search query is active.
- **Single-Pass Library Snapshot (`MyListLibrarySnapshot`):** Offloaded combined deduplication and per-tab slice generation (`entriesByTab`) to a unified background pass on `Dispatchers.Default`, eliminating UI stutters.

### 🌐 Cross-Provider Title Hydration & Alias Matching
- **Simkl, Shikimori & Bangumi Variant Enrichment:** Missing English/Latin title variants from provider APIs that only return Romaji or CJK titles are now automatically hydrated in-memory from verified sibling records and AniList batch GraphQL queries via `MalAxisLibraryTitleHydrator`.
- **Kitsu Reverse Crosswalk:** Added `reverseKitsuMapping` to `MyListIdentityCrosswalk` to ensure full parity when merging standalone Kitsu records.
- **Source Repair Guard:** Hardened `MyListViewModel.repairMisclassifiedSources` against falsely reclassifying genuine MAL entries to AniList.

### 🎯 Accurate & Noise-Free Title Search
- **Metadata False Positives Eliminated:** Restricted search strictly to validated title variants, verified aliases, and exact 4-digit release years, preventing erroneous matches on metadata (type names, sources, or internal synthetic IDs).
- **Advanced Text Normalization:** Added Turkish `İ/I/ı/i` folding, punctuation-free title matching, exact numeric token preservation (preventing season/number collisions), and typo/initialism tolerance.

### 🏷️ Compact Poster Corner Badges
- **Flush Corner Alignment:** Status and score badges now sit flush against the outer boundaries of poster cards (`TopEndCornerShape`, `BottomEndCornerShape`).
- **Reduced Visual Occlusion:** Removed outer border radius while keeping a soft 6.dp inner corner with tightened padding and typography, maximizing poster artwork visibility.

---

# Kitsugi Beta v2.4.349

## 🇹🇷 Türkçe

### 🎬 Oynatıcı Siyah Ekran & Sesli Takılma Kurtarma Motoru (Audio-Only Playback Detector)
- **Sessiz Video Çözücü Çökmesi Kurtarma:** Ses çalarken görüntünün gelmediği ("audio-only") siyah ekran durumlarını tespit eden `AudioOnlyPlaybackDetector` eklendi. Zaman çizelgesi ilerlemesi ve 6 saniyelik tolerans penceresi ile video karesi üretilmediği anlaşıldığında otomatik olarak alternatif motora veya kaynağa geçilir.
- **Kesintisiz Tam Ekran Bekçisi:** Bekçi yalnızca açılışta değil, oynatma boyunca uyanık kalarak ileri/geri atlama (seek) sonrası veya akış esnasında donan video karelerini de yakalar.
- **Compose `key(playerEngine)` Görünüm Fabrikası Onarımı:** Tam ekran oynatıcıda motor değiştirildiğinde (Media3 ↔ MPV) Compose'un eski motorun ölü görünümünü tutması engellendi. Her motor kendi bağımsız `SurfaceView`/`TextureView` örneğini baştan kurar.
- **MPV Yüzey Yaşam Döngüsü & Mükerrer Reconfig Temizliği:** `attachedSurface` kontrolüyle MPV'nin aynı yüzeye iki kez bağlanması ve yeniden oluşturulan yüzeyi bozan fazladan `video-reconfig` komutları kaldırıldı. `video-out-params` boyutları (w/h, dw/dh) doğrulanarak sahte "hazır" durumları elendi.
- **Fallback Zincir Döngüsü Koruması:** `PlayerFallbackCoordinator` başarısız olan motoru doğrudan kayıt altına alarak Media3 → MPV → Media3 döngülerini engelledi. Her kaynak için her motor en fazla bir kez denenir.
- **Başlangıç Ayar Yarışı Çözümü:** DataStore diskten okunurken `AppSettings` varsayılan değerleri beklenir; kullanıcının MPV/harici motor tercihi yerine Media3'ün erken başlatılması engellendi.
- **Satır İçi (Inline) Yükleme Katmanı:** Fragman ve tema oynatıcılarında yükleme animasyonu, motor `READY` olsa bile gerçek bir video karesi ekrana gelene kadar (`inlineVideoVisible`) ekranda tutulur.

### 📦 AniSub Tam Donanımlı Altyazı Motoru (Full AniSub Engine)
- **Thread-Safe Bellek İçi Çerez Yöneticisi:** `InMemoryCookieJar` ile AniSub ve Cloudflare oturum çerezleri güvenle yönetilir.
- **ZIP & RAR Arşiv Desteği:** Altyazı paketleri (`.zip` ve `.rar`) `junrar` entegrasyonuyla otomatik olarak ayıklanıp önbelleğe alınır.
- **Akıllı Başlık ve Bölüm Eşleştirme:** Sezon ve bölüm numaraları toleranslı desenlerle filtrelenerek doğru Türkçe altyazı dosyası oynatıcıya sunulur.

### 🚀 Otomatik Sürüm Yükseltme Sistemi (Auto-Versioning)
- `version.properties` altyapısı kuruldu. Her APK derlemesinde sürüm numarası otomatik olarak yükseltilir ve standart `Kitsugi-Beta-v<version>-foss.apk` biçiminde paketlenir.

---

## 🇬🇧 English

### 🎬 Audio-Only Black Screen Recovery & Surface Lifecycle
- **Audio-Only Playback Detector:** Added pause/buffering/seek-aware `AudioOnlyPlaybackDetector` requiring real position deltas and a 6-second grace window to automatically recover when audio plays but the screen stays black.
- **Continuous Fullscreen Watchdog:** Watchdog stays active throughout playback to catch mid-stream frame loss and post-seek decoder stalls.
- **Compose `key(playerEngine)` Re-instantiation:** Replaced stale view reuse with `key(playerEngine)` so new video surfaces are created immediately on engine switches.
- **MPV Surface Lifecycle & Dimension Checks:** Tracked `attachedSurface` identity, dropped redundant `video-reconfig`, and verified `video-out-params` dimensions (`w/h` or `dw/dh`).
- **Fallback Loop Prevention:** Bounded engine fallbacks to at most one attempt per engine per source (`triedEngines += currentEngine`), preventing ping-pong loops.
- **Startup Settings Race Resolution:** Gated initial engine preparation on persisted settings from DataStore, preventing uninitialized placeholder engine defaults.
- **Inline Loading Overlay Predicate:** Kept plasma loader visible until `inlineVideoVisible(playerEngine)` confirms rendered video frames.

### 📦 Complete AniSub Subtitle Engine
- **In-Memory Cookie Jar:** Added thread-safe cookie persistence for AniSub requests.
- **ZIP & RAR Unpack Support:** Added native unpacking for both `.zip` and `.rar` packages.
- **Robust Fuzzy Matching:** Improved season/episode normalizer and subtitle caching.

### 🚀 Automated Build Versioning
- Added `version.properties` integration that auto-increments the build version whenever an APK assembly task runs.

---

# Kitsugi Beta v2.4.348

## 🇹🇷 Türkçe

### 🔄 Liste Kaynakları Arası Çapraz Senkronizasyon ve Alan-Bazlı Eşitleme Motoru (Full Cross-Sync Engine)
- **Shikimori - MAL Kimlik Birleşimi:** Shikimori ve MyAnimeList kimlikleri (`myanimelist_id = object.id`) tekil kimlik havuzunda birleştirildi. Artık Shikimori kayıtları tekil grupta izole kalmıyor; MAL, AniList, Kitsu, Bangumi ve Simkl ile ortak eşitleme grubunda senkronize ediliyor.
- **Alan-Bazlı Akıllı Yazım (`SyncField`):** Her eşitlemede tüm kaydı körlemesine karşıya yazmak yerine, yalnızca yerelde değişen alanlar (`STATUS`, `PROGRESS`, `SCORE`, `DATES`, `NOTES`, `REWATCH`) tespit edilerek hedefe iletiliyor. Ağ trafiği ve gereksiz API istekleri en aza indirildi.
- **Son Kullanıcı Hareketi Önceliği (`CrossSyncMergePolicy`):** Bir serviste bölüm ilerlettiğinizde veya bir yapımı bıraktığınızda (`Dropped`/`Paused`), diğer servislerdeki bayat verilerin bunu geriye çekmesi engellendi. Deterministik `NEWEST_FIRST` kuralı ile en güncel işlem daima kazanıyor.
- **Tekrar İzleme (Rewatch) ve Döngü Koruması:** MAL ve Kitsu'nun bayrak modeli (`is_rewatching=true`) ile AniList'in durum modeli (`REPEATING`) ayrıştırıldı. MAL'de dizilerin yanlışlıkla "Tamamlandı"ya dönmesi ve sonsuz yazım döngüleri giderildi.
- **Kardeş Satır Aynalama (`SiblingMirrorPolicy`):** Yerel veritabanında aynı yapımın farklı kaynaklardaki kopyaları güncellenirken ilerleme gerilemesi, tarih kayıpları ve gereksiz yazımlar önlendi.
- **Simkl Çift Yönlü Senkronizasyon & Notlar:** Simkl'de silinen kayıtların doğru listeye çekilmesi (`notinteresting`), `?memos=yes` parametresiyle iki yönlü not senkronizasyonu ve durum normalizasyonu tamamlandı.
- **Çevrimdışı Kuyruk ve Güvenli Tahliye (`PendingSyncDrainer`):** İnternet yokken yapılan değişiklikler yerel kuyrukta saklanıyor ve bağlantı kurulduğunda platform hız limitlerine uygun olarak sırayla hedeflere işleniyor.
- **256 Senkronizasyon Birim Testi:** MAL, AniList, Shikimori, Kitsu, Bangumi ve Simkl sağlayıcıları arasındaki tüm çapraz durumlar ve geriye-yazım testleri firesiz doğrulandı.

---

## 🇬🇧 English

### 🔄 Multi-Provider Cross-Sync & Field-Level Synchronization Engine
- **Shikimori - MAL Identity Unification:** Unified Shikimori and MyAnimeList entry identities (`myanimelist_id = object.id`). Shikimori entries no longer remain isolated in single-source groups and now cross-sync seamlessly across MAL, AniList, Kitsu, Bangumi, and Simkl.
- **Field-Level Diffing (`SyncField`):** Replaced blind full-object writes with granular field diffing (`STATUS`, `PROGRESS`, `SCORE`, `DATES`, `NOTES`, `REWATCH`). Only dirty fields are dispatched to target APIs, eliminating redundant requests and server throttling.
- **Newest Action Priority (`CrossSyncMergePolicy`):** Prevented stale tracker states from rolling back recent progress or overriding `Dropped`/`Paused` statuses. Deterministic `NEWEST_FIRST` resolution ensures the latest user update always wins.
- **Rewatch Semantics & Loop Prevention:** Decoupled flag-based rewatch models (MAL/Kitsu `is_rewatching`) from status-based models (AniList `REPEATING`), eliminating infinite synchronization loops and premature "Completed" transitions on MAL.
- **Deterministic Sibling Mirroring (`SiblingMirrorPolicy`):** Guaranteed monotonic progress and non-destructive metadata propagation across multi-source siblings in local storage.
- **Simkl Two-Way Sync & Memos:** Added bidirectional note synchronization via `?memos=yes` and normalized list status handling (`notinteresting`).
- **Offline Sync Queue & Safe Drain (`PendingSyncDrainer`):** Offline mutations are safely persisted in Room and drained with rate-limiter backoff upon reconnection.
- **256 Unit Tests Passing:** Validated read-back idempotence, merge policies, and cross-provider conversions across all 6 supported platforms.

---

# Kitsugi Beta v2.4.347

## 🇹🇷 Türkçe

### ⚡ Listem Arama ve Yazarken Donma Optimizasyonu (MyList Search Pipeline & Index)
- **Arka Planda Arama:** Binlerce kaydı içeren Listem ekranında her harf girişinde ana iş parçacığında (UI) çalışan ağır filtreleme, NFKC/regex normalizasyonu ve Levenshtein hesaplaması kaldırıldı. Arama `Dispatchers.Default` üzerine taşındı.
- **Yazma Gecikmesi & İptal:** Tuş vuruşları için 180 ms yazma gecikmesi (`debounce`) eklendi; `transformLatest` ile yeni bir harf yazıldığında eski arama işlemi derhal iptal ediliyor. Temizleme ve filtre değişimleri ise beklemeden anında çalışıyor.
- **Arama İndeksi (`MyListSearchIndex`):** Başlıklar, alternatif adlar ve fuzzy normalizasyonları önbelleğe alınıyor; ızgara çiziminde tüm listeyi tekrar tekrar kopyalayan `chunked()` yerine dinamik satır hesabı (`items(count = ...)`) getirildi.
- **Kısa Sorgu Koruması:** 1–2 harfli kısa aramalarda listeyi gereksiz yere şişiren toleranslı eşleşmeler sınırlandırıldı; 2.603 kayıtlı testler dahil 46 birim testiyle doğrulandı.

### 🛡️ LazyVerticalGrid Sonsuz Yükseklik Çökmesi Düzeltmesi (Infinite Max Height Fix)
- **Kök Neden Giderildi:** `java.lang.IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints` çökme hatası çözüldü.
- **Kesin Yükseklik Hesabı (`GridHomeContent`):** `wrapContentHeight()` yerine satır sayısından sonlu yükseklik hesaplayan `rememberGridHomeEmbedHeight` getirildi; `LazyColumn.item` içinde sonlu `height(...)` ile güvenli ölçüm sağlandı.
- **Profil Seçim Izgarası (`ProfileSelection`):** Olası sonsuz yükseklik ölçümünü engellemek için `heightIn(max = 520.dp)` sınırı eklendi.
- **Teşhis Sistemi (`KitsugiApplication`):** `:crash` sürecinin `StartupStage` iz dosyasını ezmesi engellendi; çökme anındaki gerçek aşama raporlara doğru yansıtılıyor.

### 📋 Hakkında Ekranı Sürüm Notları ve Önbellek Düzeltmesi (`ReleaseNotesCache`)
- **Sürüme Bağlı Önbellek:** Sürüm notu önbelleği kurulu uygulama sürümüne (`VERSION_NAME`) bağlandı. Eski uygulama sürümünden kalan bayat kayıtlar ve geleceğe ait zaman damgaları reddediliyor.
- **Kanonik GitHub Kaynağı:** Hakkında ekranındaki sürüm notları doğrudan GitHub Releases API'sinden okunuyor; gecikebilen Codeberg aynasından eski sürüm bilgisi çekilmesi önlendi. Güncelleme denetiminde ise Codeberg yedeği korunuyor.

---

## 🇬🇧 English

### ⚡ MyList Search Pipeline & Keystroke Freeze Optimization
- **Background Search Execution:** Offloaded heavy search filtering, NFKC/regex normalization, and fuzzy string distance calculations from the main UI thread to `Dispatchers.Default`.
- **Typing Debounce & Instant Cancellation:** Added a 180 ms typing debounce with `transformLatest`, ensuring in-flight searches are immediately cancelled upon new keystrokes. Clear actions and filter toggles respond immediately.
- **Search Pre-Indexing (`MyListSearchIndex`):** Precomputed title aliases and metadata keys. Replaced full-list `chunked()` collection copying in grid views with dynamic count-based row rendering (`items(count = ...)`).
- **Short Query Guard:** Constrained broad fuzzy matching for 1–2 character queries to avoid over-matching large lists, fully verified across 46 unit tests.

### 🛡️ LazyVerticalGrid Infinite Height Crash Fix
- **Root Cause Resolved:** Fixed `java.lang.IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints` when embedding vertical grids inside LazyLists.
- **Deterministic Grid Height (`GridHomeContent`):** Replaced unbounded `wrapContentHeight()` with row-count-based finite height calculation (`rememberGridHomeEmbedHeight`) inside `LazyColumn.item`.
- **Profile Selection (`ProfileSelection`):** Bounded grid height via `heightIn(max = 520.dp)`.
- **Crash Diagnostics (`KitsugiApplication`):** Prevented the `:crash` process from overwriting the `StartupStage` trace file, preserving the true crash stage in reports.

### 📋 About Screen Release Notes & Cache Fix (`ReleaseNotesCache`)
- **Version-Scoped Cache:** Tied release notes cache invalidation directly to installed app version (`VERSION_NAME`). Stale records from older versions and future-dated timestamps are discarded.
- **Canonical GitHub Source:** About screen fetches release notes strictly from GitHub Releases API, preventing stale mirror data from Codeberg from appearing as the latest release. Codeberg fallback remains active for APK update checks.

---

# Kitsugi Beta v2.4.346

## 🇹🇷 Türkçe

### 🔄 Shikimori Çapraz Eşitleme (Cross-Sync) ve Durum Önceliği
- **Ortak MAL Kimlik Eşlemesi:** Shikimori anime/manga `target_id` değerinin resmî API sözleşmesine uygun olarak MAL kimlik ekseninde çözümlenmesi sağlandı. Romaji ve İngilizce farklı başlıklara sahip yapımlar (ör. Zom 100) artık aynı eşitleme grubuna dahil ediliyor.
- **Akıllı Durum Birleştirme (`CrossSyncMergePolicy`):** Tamamlanma güvenceleri korunurken, eski kuralın bayat bir "İzleniyor" kaydını daha yeni "Bırakıldı" veya "Duraklatıldı" kararlarına kalıcı olarak tercih etmesi düzeltildi. En güncel kullanıcı kararı tüm platformlara doğru yayılıyor.

### 🌸 Shikimori Detay, Bilgi Kartı ve Topluluk İyileştirmeleri
- **Zenginleştirilmiş Bilgi Bölümü:** Format, sezon, yayınlanan bölüm sayısı, sonraki bölüm tarihi/saati, manga cilt ve yayıncı bilgileri eklendi.
- **Çok Dilli Başlıklar & Temizlik:** İngilizce, Japonca, Romaji, Rusça adlar ayrıştırıldı; `[null]` veya boş dizelerin metin olarak görünmesi engellendi.
- **Lisanslayanlar & Fansub/Fandub Grupları:** İlgili çeviri ve lisans grupları Bilgi sekmesine bağlandı. Film ve dizilerde stüdyo, süre ve yaş sınırının yanlışlıkla gizlenmesi önlendi.
- **Yerel Topluluk İstatistikleri:** Shikimori'nin kendi puan ve liste-durumu dağılım istatistikleri harici yedeklerden önce önceliklendirildi. Boş alanların kutu kirliliği yaratmaması için dinamik istatistik kartı getirildi.
- **Forum ve Harici Bağlantılar:** Shikimori konuları forum sekmesine taşındı ve `/api/comments` yanıt akışı bağlandı. Manga harici bağlantıları etkinleştirildi; Vikipedi linklerine dil etiketi eklendi.

### 🎬 Detay Sayfası Küçük Oynatıcı & MPV / Media3 İyileştirmeleri
- **Gelişmiş Satır İçi Oynatıcı:** Fragman ve açılış/kapanış (OP/ED) temaları için küçük oynatıcı arayüzü pürüzsüzleştirildi. Ses açma/kapama, tam ekran geçişi ve oynatma durumu kontrolleri optimize edildi.
- **MPV ve Media3 Yüzey Desteği:** `PlayerEngine`, `MpvPlayerEngine`, `Media3PlayerEngine` ve `KitsugiMpvSurfaceView` üzerinde küçük oynatıcı yüzey entegrasyonu güçlendirildi; oynatıcı değişimlerinde donma veya kaynak çakışması önlendi.
- **YouTube Bağlantı Tanıma:** Standart watch, embed, shorts, youtu.be ve zaman damgalı video URL'lerinin tamamını hatasız ayrıştıran Regex motoru güncellendi; `YouTubeUrlParsingTest` ve `InlinePlayerSupportTest` ile doğrulandı.

### 🦊 Kitsu Bilgi Kapsamı & Yayın Detayları
- **Eksiksiz Kitsu Yayın Bilgileri:** Kitsu API detaylarında bölüm sayısı, yayın durumu, yayınlandığı sezon/yıl, yaş derecelendirmesi ve içerik uyarıları detay sayfasına tam uyumlu hâle getirildi.
- **Çoklu Dil Etiket Desteği:** Yayın durumu ve yaş etiketleri Türkçe ve İngilizce yerelleştirme kaynaklarıyla senkronize edildi.

### 📝 Ani-Sub Altyazı Eşleme & İyileştirmeler
- **Akıllı Altyazı Eşleştirici (`AniSubSubtitleMatcher`):** Zip paketleri ve arşivlerdeki altyazı dosyaları için bölüm numarası ve başlık benzerlik skoru hesaplama motoru eklendi.
- **Türkçe Altyazı Önceliği:** Ani-Sub üzerinden gelen altyazılarda Türkçe varyantlar (`.srt`, `.ass`) doğru sıra ve zamanlama ile akış istemcisine bağlandı.

---

## 🇬🇧 English

### 🔄 Shikimori Cross-Sync & Status Merging Fix
- **Shared MAL Identity Resolution:** Harmonized Shikimori anime/manga target IDs with the official MAL identity contract. Different localized/Romaji titles (e.g. Zom 100) now cleanly converge into the same cross-sync group.
- **Dynamic Status Merge Policy (`CrossSyncMergePolicy`):** Fixed stale "Watching" records overriding newer "Dropped" or "Paused" statuses, while preserving completion safeguards.

### 🌸 Shikimori Details, Info Card & Community Enhancements
- **Comprehensive Info Fields:** Added format, broadcast season, episodes aired, next episode countdown/date, manga volume count, and publishers.
- **Multi-Language Titles & Cleanups:** Parsed English, Japanese, Romaji, and Russian aliases while stripping any `[null]` artifacts.
- **Licensors & Fansub/Fandub Groups:** Connected licensor and fansub groups to the Info card. Fixed studio, runtime, and age ratings being hidden on movies/TV specials.
- **Native Statistics Priority:** Prioritized Shikimori native rating and list distribution stats over fallback providers, with a dynamic clean statistics card.
- **Forum & External Links:** Connected Shikimori topics to the forum feed with `/api/comments` reply loading. Activated manga external links and added language tags for Wikipedia.

### 🎬 Detail Screen Mini Player & MPV / Media3 Enhancements
- **Enhanced Inline Player:** Smoother playback experience for trailers and opening/ending (OP/ED) themes. Optimized mute/unmute, fullscreen toggle, and playback controls.
- **MPV and Media3 Surface Support:** Strengthened inline player surface integration across `PlayerEngine`, `MpvPlayerEngine`, `Media3PlayerEngine`, and `KitsugiMpvSurfaceView`, preventing freezes or surface collisions during stream transitions.
- **Robust YouTube URL Parsing:** Upgraded regex pattern matching across all YouTube formats (standard watch, shorts, embed, shortlinks, timestamped links), verified via `YouTubeUrlParsingTest` and `InlinePlayerSupportTest`.

### 🦊 Kitsu Information Scope & Airing Details
- **Comprehensive Kitsu Metadata:** Enhanced Kitsu details mapping with full coverage of episode count, airing status, broadcast season/year, age ratings, and content advisories.
- **Localized Badges:** Airing status and age classifications are fully harmonized with Turkish and English locale strings.

### 📝 Ani-Sub Subtitle Matching Engine
- **Smart Subtitle Matcher (`AniSubSubtitleMatcher`):** Introduced title similarity and episode numbering heuristics for matching extracted subtitle streams from zip archives.
- **Turkish Subtitle Priority:** Streamlined Turkish subtitle matching (`.srt`, `.ass`) with prioritized scoring for seamless playback.

---

# Kitsugi Beta v2.4.345

## 🇹🇷 Türkçe

### 📱 Wiki Açılır Sayfası Artık Mobil Düzende Açılıyor
- **Varsayılan görünüm mobil:** Özgün wiki sayfası artık masaüstü düzeniyle değil, sitenin kendi mobil/duyarlı düzeniyle açılır. Metinler okunaklı boyutta gelir, yatay kaydırma gerekmez.
- **Seçim hatırlanıyor:** Menüden "Masaüstü düzeni" veya "Mobil düzen" seçildiğinde tercih kalıcı olarak kaydedilir (`wiki_desktop_layout`). Yeni sekmeler, yeni wiki sayfaları ve uygulama yeniden başlatıldıktan sonra da **en son ne seçildiyse o düzen** açılır.
- **Sekmeler arası tutarlılık:** Yeni sekme ve sekme değişimlerinde düzen tercihi korunur.

### ↔️ Sayfa Kendini Ekrana Sığdırıyor
- **Mobilde site viewport'u korunur:** Yalnızca ekrandan geniş, sabit piksel genişliği dayatan belgeler `width=device-width`'e çevrilir; diğer sayfaların düzenine dokunulmaz.
- **Masaüstü düzeninde otomatik ölçekleme:** 1200 px'lik masaüstü düzeni `initial-scale` ile cihaz genişliğine oturtulur; sayfa 1:1 oranda taşmaz.
- **Taşan öğeler tek tek düzeltilir:** Ekrandan geniş görsel, tablo, `pre` ve geniş bloklar **yalnızca gerçekten taşıyorsa** işaretlenip kendi içinde kaydırılabilir hâle getirilir. Metin, bağlantı, nitelik ve site betikleri değiştirilmez; içerik hiçbir zaman kırpılmaz (`overflow-x: hidden` kullanılmaz).
- **Geç gelen görseller / çeviri sonrası genişleme:** Yerleşim `resize`, `load` ve çeviri yazımlarından sonra yeniden ölçülür; kaydırma konumu bozulmaz.

### ⚡️ Otomatik Çeviri Ciddi Biçimde Hızlandı
- **Paralel boru hattı (pipeline):** Önceden her parti için "iste → bekle → yaz" sırası vardı; artık **aynı anda 3 çeviri partisi** uçuşta olur ve sonuçlar geldikçe yazılır. Ağ beklemesi sayfayı kilitlemez.
- **Daha büyük partiler:** DOM köprüsü tur başına 8 düğüm yerine **12 düğüm** (1200 karaktere kadar) toplar; daha az istek, daha çok metin.
- **Tekrarlar tek istekte:** Aynı metin sayfada birden çok geçiyorsa (gezinme etiketleri, tekrar eden satırlar) **tek** ağ isteği yapılır, sonuç tüm düğümlere dağıtılır.
- **Kaydırırken çeviri durmuyor:** Kaydırma olayları artık tarayıcıyı her karede baştan başlatmıyor (gecikmeli yeniden tarama). Yazma 120 ms'lik kısa bir pencereyle ertelenir ve kaybolmaz, sürekli yeniden denenir.
- **Hata artık boru hattını durdurmuyor:** Tek bir partinin/isteğin hatası yalnızca o partiyi etkiler; düğümler sonra yeniden denenir. Zaman aşımında "bekliyor" işaretleri temizlenir, metin bir daha hiç istenmeyen durumda kalmaz.
- **Boşta daha az yoklama:** Çevrilecek metin kalmadığında 450 ms, tarama sürerken 90 ms yoklama; gereksiz JS çağrıları azaltıldı.

---

## 🇬🇧 English

### 📱 Wiki Page Opens in Mobile Layout
- **Mobile by default:** The original wiki page no longer opens in a cramped desktop layout; it uses the site's own responsive/mobile layout with readable text and no horizontal scrolling.
- **Choice is remembered:** Toggling "Desktop layout"/"Mobile layout" is persisted (`wiki_desktop_layout`). New tabs, new wiki pages and app restarts open in **the last selected layout**.
- **Consistent across tabs:** Layout preference survives tab switches and duplicated tabs.

### ↔️ The Page Fits the Screen Automatically
- **Site viewport preserved on mobile:** Only documents that hard-code a fixed pixel width wider than the device are rewritten to `width=device-width`; untouched pages keep their own layout.
- **Desktop mode is auto-scaled:** The 1200 px desktop layout is fitted with `initial-scale`, so it no longer overflows at 1:1.
- **Only overflowing elements are corrected:** Wide images, tables, `<pre>` and wide blocks are marked **only when they actually overflow** and become scrollable within themselves. Text, links, attributes and site scripts are never rewritten and content is never clipped (no `overflow-x: hidden`).
- **Late images / translated layout changes:** Layout is re-measured on `resize`, `load` and after translation writes without disturbing the scroll position.

### ⚡️ Automatic Translation Is Much Faster
- **Pipelined:** Up to **three DOM batches in flight** at once, applied as they arrive, instead of a strict "request → wait → write" loop.
- **Larger batches:** The DOM bridge now returns up to **12 nodes** (~1200 chars) per round, cutting the number of requests.
- **Repeated text is requested once:** Identical strings are translated with a single request and fanned out to every matching node.
- **Translation keeps flowing while scrolling:** Scroll events no longer restart the scanner on every frame (debounced rescan); writes are briefly deferred (120 ms) and retried, never dropped.
- **Failures are isolated:** A slow or failing batch no longer stalls the pipeline; a time-out clears "pending" flags so text can be retried instead of being stuck forever.
- **Leaner idle polling:** 450 ms idle / 90 ms while scanning, with the cosmetic ad layer refreshed at most once per second.

---

# Kitsugi Beta v2.4.344

## 🇹🇷 Türkçe

### 🎭 Simkl & TMDB Kurgusal Karakter Portre ve Biyografi Ayrımı Düzeltmesi
- **Seslendirmen Profili İzolasyonu:** The Simpsons gibi Batı animasyonlarında TMDB jeneriğinden çekilen kurgusal karakterlerin (Homer, Marge, Bart vb.) seslendirmen (Dan Castellaneta, Julie Kavner, Nancy Cartwright) fotoğrafı, biyografisi ve takma adlarıyla doldurulması tamamen engellendi. Seslendirmen yalnızca *Seslendirmenler* listesine atanır.
- **Detay ve Önbellek Sızıntısı Koruması (`DetailCache`):** Seslendirmen fotoğraflarının karakter portresi olarak `resolvedCharacterPortraits` ve `mediaCharacters` önbelleklerine sızması engellendi. TMDB URL'lerindeki boyut segmentlerini (`/w185/`, `/w300/`, `/w500/`) normalize eden `DetailCache.isSameImageResource` ile aynı aktör fotoğrafı kopyaları yakalanıp elenir.
- **Fandom & Vikipedi Kurgusal Karakter Çözümlemesi:** Kurgusal karakterler için AniList/Jikan/Bangumi kataloglarına ek olarak Fandom ve Vikipedi (`wikipedia.tr`, `wikipedia.en`) portre ve biyografi çözümleme zinciri eklendi.
- **Vikipedi Adil Kullanım (Fair-Use) Portre Desteği (`FandomApi`):** MediaWiki `PageImages` sorgularına `pilicense=any` eklenerek Vikipedi'deki adil kullanım lisanslı kurgusal karakter görsellerinin (`Homer_Simpson_2006.png`, `Marge_Simpson.png`, `Bart_Simpson_200px.png`) API tarafından thumbnail olarak dönmesi sağlandı. Toplu sorgulara `pilimit=50` ve `exlimit=20` limitleri eklendi.
- **Çoklu Rol Adı Ayrıştırma (`/`):** Tek seslendirmenin canlandırdığı birleşik karakter adları ("Homer Simpson / Abe Simpson / Barney Gumble") `/` üzerinden ayrıştırıldı; birincil ad ana karakter, ikincil adlar ise alternatif roller olarak korundu.
- **Menşe Ülke Filtresi:** Simkl `country` alanı `countryOfOrigin` olarak modele bağlandı; Batı animasyonlarının Asya anime kataloglarında boşuna aranması önlendi.
- **Seslendirmenler Sekmesi:** TMDB kurgusal karakterleri (`!isRealMediaRole`) için Karakter Detay sayfasında *Seslendirmenler* sekmesi görünür kılındı.
- **Wiki Sekmesi Canlı Entegrasyonu:** Wiki sekmesi karakter sayfasını çözdüğünde ana detaydaki eksik portre ve biyografi otomatik olarak tamamlanır.

### 📊 MDBList Canlı API Kotası Takibi ve Kota Aşımı Koruması
- **Gerçek Zamanlı ve Dinamik Kota (`MdbListQuotaCenter`):** API kotası artık sabit 1.000 varsayımı yerine `https://api.mdblist.com/user` ve `X-RateLimit-*` başlıklarından dinamik olarak okunur. Ücretsiz (1.000) ve Destekçi (10.000 / 25.000 / 100.000 / 250.000) planlarının tamamı otomatik tanınır.
- **Ayarlar Paneli:** Ayarlar → Harici Entegrasyonlar → MDBList sekmesine kalan istek sayısı, doluluk çubuğu, yerel saat diliminde sıfırlanma zamanı ve yenileme butonu eklendi.
- **Detay Sayfası Harici Puanlar Kartı:** Rozet yanında küçük kota göstergesi (`998/1.000`, %10 altında turuncu, bitince kırmızı "Kota doldu").
- **Kota Aşımı Koruması & Sessiz Önbellek:** Günlük kota dolduğunda ağa çıkılmadan önbellekteki son puanlar gösterilir (429 döngüsü önlenir), kartta bilgilendirici uyarı gösterilir ve tek seferlik bildirim sağlanır. HTTP 429 artık anahtar geçersiz hatası değil kota dolumu olarak ele alınır.
- **Birim Testleri:** `TmdbAnimeCharacterPortraitEnrichmentTest`, `FandomApiParsersTest`, `FandomCharacterQueriesTest`, `MdbListClientTest` ve `MdbListQuotaTest` ile doğrulandı.

---

## 🇬🇧 English

### 🎭 Simkl & TMDB Fictional Character Portrait and Biography Isolation Fix
- **Voice Actor Profile Separation:** For Western animations (e.g. *The Simpsons*) sourced via TMDB credits, fictional character records (Homer, Marge, Bart, etc.) are strictly prevented from inheriting voice actor (Dan Castellaneta, Julie Kavner, Nancy Cartwright) photographs, biographies, or aliases. Voice actors remain cleanly positioned in the *Voice Actors* roster.
- **Detail & Cache Poisoning Prevention (`DetailCache`):** Voice actor headshots are barred from leaking into `resolvedCharacterPortraits` and `mediaCharacters` caches. `DetailCache.isSameImageResource` normalizes TMDB image URL resolution prefixes (`/w185/`, `/w300/`, `/w500/`) to detect duplicate actor photos across different sizes.
- **Fandom & Wikipedia Fictional Fallback Pipeline:** Fictional characters missing in anime databases (AniList, Jikan, Bangumi) now query Fandom and Wikipedia (`wikipedia.en`, `wikipedia.tr`) for portraits, biographies, and infobox metadata.
- **Wikipedia Non-Free / Fair-Use Image Support (`FandomApi`):** Added `pilicense=any` to MediaWiki `prop=pageimages` requests so Wikipedia non-free character portraits (`Homer_Simpson_2006.png`, etc.) return thumbnails correctly. Batch limits (`pilimit=50`, `exlimit=20`) were also expanded.
- **Multi-Role Slash Splitting (`/`):** Compound role strings (e.g. "Homer Simpson / Abe Simpson / Barney Gumble") are split by `/`; the primary role becomes the main character name while secondary roles are preserved as alternatives.
- **Origin Country Attribution:** Simkl's `country` field is mapped to `countryOfOrigin`, skipping redundant East Asian anime catalog searches for Western animations.
- **Voice Actors Tab Visibility:** The *Voice Actors* tab is now displayed on the Character Detail screen for fictional TMDB roles (`!isRealMediaRole`).
- **Live Wiki Tab Sync:** Resolving character wiki pages dynamically completes missing character portraits and summaries in the main detail page.

### 📊 MDBList Live API Quota Tracking & Exhaustion Guard
- **Dynamic Real-Time Quota (`MdbListQuotaCenter`):** API quota is now dynamically tracked from `https://api.mdblist.com/user` responses and `X-RateLimit-*` headers instead of hardcoded limits, supporting all Free (1,000) and Supporter tiers (10,000+).
- **Settings Dashboard:** Integrations → MDBList displays remaining requests, progress bar, localized daily reset time, and manual refresh controls.
- **Detail Screen External Ratings Card:** Live quota badge (`998/1,000`, orange under 10%, red "Quota used up").
- **Exhaustion Guard & Cached Fallback:** Prevents 429 cascades by serving cached ratings when quota is depleted, accompanied by inline recovery times and deduplicated notifications. HTTP 429 is treated as quota exhaustion rather than an invalid API key.
- **Unit Tests:** Validated via `TmdbAnimeCharacterPortraitEnrichmentTest`, `FandomApiParsersTest`, `FandomCharacterQueriesTest`, `MdbListClientTest`, and `MdbListQuotaTest`.

---

# Kitsugi Beta v2.4.343

## 🇹🇷 Türkçe

### 🎵 Detay Sayfası Açılış / Kapanış (OP/ED) Müzikleri Küçük Oynatıcı & Açılma Animasyonu
- **Uygulama İçi Küçük Oynatıcı Deneyimi:** Açılış (OP) ve kapanış (ED) satırlarına dokunulduğunda doğrudan video bağlantısı olmasa bile harici YouTube aramasına çıkılmaz. Video arka planda çözülerek doğrudan fragmandaki gibi satır içi küçük oynatıcıda oynatılır.
- **Arka Planda Video Çözümleme (`InAppYouTubeExtractor`):** Bağlantısız müzik etiketleri için uygulama içi YouTube araması yapılarak ilk gerçek video kimliği (`videoRenderer`) çözülür ve bellek önbelleğinde saklanır.
- **Bağımsız Arama Ayrıştırıcısı (`YouTubeSearchResultParser`):** Kanal veya oynatma listesi sonuçlarını atlayıp doğrudan oynatılabilir video kimliğini ayıklayan, süslü parantez ve kaçışlı metinlere dayanıklı hafif ayrıştırıcı eklendi.
- **Ortak `KitsugiDetailReveal` Açılma Animasyonu:** Oynatıcı, ilgili satırın altında fragmandaki gibi aşağıya doğru yumuşak animasyonla açılır.
- **Anında Oynatıcı Temizliği:** Başka bir müziğe geçişte veya kapatmada eski oynatıcı motoru anında kaldırılır; iki oynatıcının aynı anda bellekte kalması veya ses çakışması yaşanması engellenir.
- **Hata Durumu ve Yedek Seçenek:** Çözümleme başarısız olursa küçük oynatıcı alanı içinde "Yeniden Dene" ve yalnızca isteğe bağlı yedek seçenek olarak "YouTube'da ara" eylemleri gösterilir.
- **Birim Testleri:** `YouTubeSearchResultParserTest` ile video ayıklama, kanal/playlist eleme ve eksik veri durumları doğrulandı.

---

## 🇬🇧 English

### 🎵 Detail Screen Opening / Ending (OP/ED) In-App Mini Player & Reveal Animation
- **Seamless Inline Playback:** Tapping Opening (OP) or Ending (ED) theme rows no longer redirects to an external YouTube search when a direct video link is missing. The video is resolved in the background and played directly inside the inline mini-player.
- **Background Video Resolution (`InAppYouTubeExtractor`):** Unlinked theme labels resolve to playable video IDs via in-app YouTube queries and are cached in bounded memory.
- **Robust Search Parser (`YouTubeSearchResultParser`):** Extracts authentic playable `videoRenderer` results while skipping channels and playlists, resilient against nested braces and escaped quotes.
- **Unified `KitsugiDetailReveal` Animation:** The player expands smoothly downwards below the selected row using the exact same reveal transition as trailers.
- **Immediate Engine Disposal:** Switching rows or collapsing the player immediately tears down the previous player instance to eliminate duplicate audio and heavyweight leaks.
- **Inline Retry & Fallback:** Displays inline "Retry" and fallback "Search on YouTube" buttons if resolution fails.
- **Unit Tests:** Verified via `YouTubeSearchResultParserTest` (renderer selection, playlist filtering, and malformed data handling).

---

# Kitsugi Beta v2.4.342

## 🇹🇷 Türkçe

### 🔞 Bangumi +18 (R18) Görsellerinde Bulanıklık ve Yetişkin İçerik Düzeltmesi
- **Koleksiyon İçe Aktarımında NSFW Bayrağı Çözümlemesi:** Bangumi kullanıcı koleksiyonu yanıtındaki kısaltılmış `subject` nesnesi `nsfw` alanını taşımadığı için kayıtlar `isAdult=false` olarak ekleniyor ve +18 bulanıklık ayarı açık olsa bile afişler bulanıklaşmıyordu. Yeni `BangumiAdultResolver` ile `GET /v0/subjects/{id}` detayından gerçek `nsfw` değeri çözümlenerek içe aktarıma dahil edildi.
- **Arka Plan Taraması (Migration v2):** Mevcut yerel Bangumi kayıtları için göç sürümü 2'ye yükseltildi. Stable ID → Bangumi `subject_id` dönüşümü yapılarak mevcut yerel kayıtların yetişkin bayrakları arka planda taranır ve güncellenir.
- **Güvenli Önbellekleme & Sağlayıcı İzolasyonu:** Başarılı pozitif/negatif sonuçlar sınırlı bellek önbelleğinde tutulur; ağ hataları önbelleğe alınmaz ve yeniden denenebilir. Bangumi, MAL ve TMDB kimlik alanlarının birbirine karışması önlendi.
- **Birim Testleri:** `AdultFlagBackfillMigrationTest` ile stable ID dönüşümü ve sağlayıcı ad alanı izolasyonu doğrulandı (9 testin tamamı geçti).

### 🌐 Bangumi Başlık Dili Sözleşmesi Tüm Yüzeylerde (İlişkiler, Karakterler, Yapımlar, Kişi Adları)
- **Seçili Dil Artık Her Bangumi Yüzeyinde:** İngilizce / Romaji / Özgün dil tercihi; Detay → İlişkiler & Öneriler, Kişi detayı → Karakterler & Yapımlar & Seslendirenler, Karakter detayı → Yapımlar & Seslendirenler ve Detay → Karakterler & Ekip sekmelerinde aynı zincirle uygulanır (İngilizce → Romaji → Özgün / Romaji → İngilizce → Özgün / Özgün).
- **Yeni `BangumiEntityNameResolver`:** Bangumi karakter ve kişi (seiyuu/staff) adları için AniList GraphQL üzerinde aliased toplu arama + **birebir (normalize) ad eşleşmesi** (`name.native / name.full / name.alternative`). Kısmi eşleşme kesinlikle reddedilir, yanlış kişi adı asla gösterilmez. Çözülen adlar disk önbelleğine (`bangumi_entity_name_cache_v1`) kaydedilir.
- **İlişkiler/Öneriler Ortak Çözümleyiciye Bağlandı:** p1 slim subject listeleri artık `BangumiSubjectTitleResolver` (önbellek → AniList toplu arama → subject infobox → TMDB) ile çözülür; eski yavaş infobox döngüsü kaldırıldı.
- **Arka Plan Isınma Deseni:** İlişkiler, öneriler ve kadro sekmeleri için senkron bütçeyi aşan CJK kayıtlar arka planda çözülerek sekme ve `DetailCache` anında güncellenir; ikinci ziyarette liste tamamen seçili dilde açılır.
- **Birim Testleri:** `BangumiEntityNameResolverTest` (10 testin tamamı geçti).

### 🛠️ Derleme ve Kararlılık
- Android AAPT kaynak derleyicisinde kaçışsız apostroflardan kaynaklanan derleme hataları (`values/strings.xml` ve `values-en/strings.xml`) giderildi.

---

## 🇬🇧 English

### 🔞 Bangumi R18 Image Blur & NSFW Flag Resolution
- **NSFW Resolution on Collection Import:** Bangumi collection responses return slim subject objects lacking the `nsfw` property, causing imported items to remain `isAdult=false` and bypassing user blur settings. With `BangumiAdultResolver`, subject details (`GET /v0/subjects/{id}`) are queried to properly detect adult flags on import.
- **Background Backfill Migration (v2):** Upgraded migration version to 2 for existing local Bangumi records. Resolves Bangumi stable IDs to raw `subject_id`s and backfills adult flags in background batches.
- **Safe Caching & Provider Isolation:** Positive and negative adult lookups are cached in-memory with bounded capacity; network failures remain retryable. Stable namespaces prevent collisions between Bangumi, MAL, and TMDB IDs.
- **Unit Tests:** Validated via `AdultFlagBackfillMigrationTest` (all 9 unit tests passed).

### 🌐 Universal Bangumi Title & Entity Language Contract Across All Surfaces
- **Language Preference Honored Universally:** English / Romaji / Native title language preference is consistently applied across Detail → Relations & Recommendations, Person detail → Characters & Works & Voice actors, Character detail → Works & Voice actors, and Detail → Characters & Staff tabs.
- **New `BangumiEntityNameResolver`:** Implements AniList GraphQL aliased batch queries with **strict normalized name matching** (`name.native / name.full / name.alternative`). Partial matches are strictly rejected. Results are cached persistently on disk (`bangumi_entity_name_cache_v1`).
- **Unified Relations & Recommendations Resolution:** Connected p1 slim subject rows to `BangumiSubjectTitleResolver` (persistent cache → AniList batch → infobox → TMDB fallback).
- **Background Warm Pattern:** Names and titles exceeding the synchronous display budget are resolved in the background and dynamically pushed to tab state and `DetailCache`. Subsequent visits open instantly in the preferred language.
- **Unit Tests:** Validated via `BangumiEntityNameResolverTest` (all 10 unit tests passed).

### 🛠️ Build & Stability
- Fixed AAPT resource compilation errors caused by unescaped ASCII apostrophes across localized string resources.

---

# Kitsugi Beta v2.4.341 — Arama taksonomi kataloğu / Search taxonomy catalog

TR: Arama filtresi artık 114 tür, 1.325 etiket ve 9.457 Latin kurum adını ortak
katalogdan okur. TR/EN/Rusça alias arama, kurum rol seçimi ve son seçimler
hazır. **Kurumun kaynak sonuçlarını süzmesi henüz etkin değil (T55–T60);**
arayüz bunu açıkça bildirir. Ayrıntılar: `release_notes_v2.4.341.md`.

EN: Search facets now use a generated catalog with 114 genres, 1,325 tags,
and 9,457 Latin organization names. Turkish/English/Russian aliases and role
picker are available. **Provider-side organization filtering is not active
until T55–T60**; the UI discloses this. See `release_notes_v2.4.341.md`.

v2.4.340 kurum/etiket Latin ad notları: `release_notes_v2.4.340.md`.

---

# Kitsugi Beta v2.4.338

## 🇹🇷 Türkçe

### 🏛️ Bangumi Vault Tam Entegrasyonu & 4 Modüllü Kaynak Mimarisi
- **Sıfır XML Kimlik Artışı (+0 XML ID) ile Modüler Kaynak Ayrımı:**
  - Bangumi çeviri ve meta-etiket sözlüğü devasa boyutu nedeniyle 4 bağımsız Gradle modülüne bölündü (`bangumi-vault-resources-1`, `bangumi-vault-resources-2`, `bangumi-vault-resources-3` ve ana `:app` modülü).
  - Android 65.535 tekil DEX / kaynak kimlik sınırı korunarak tüm CJK kanon, organizasyon ve etiket sözlükleri hatasız ve hafif şekilde entegre edildi.
  - 9 adet Python doğrulayıcısı ve 72 adet JUnit testi eksiksiz başarıyla geçti.

### 🎨 Detay, İnceleme ve Liste Arayüz İyileştirmeleri (UI/UX)
- **Tüm İncelemeler Penceresi Anında Yükleme (`KitsugiAllReviewsBottomSheet` & `ReviewsTab`):**
  - Detay sayfasında önceden yüklenmiş olan inceleme verileri (`reviewsList`) yeni `initialReviews` parametresiyle pencereye aktarılır.
  - Pencere ilk açıldığında ağ veya kaynak/kimlik çözümleme gecikmesi olsa bile boş görünmez; önizleme incelemeleri doğrudan ekrana yansıtılır.
- **Listem Kare Görünümü Dar Ekran Uyumu (`MyListComponents`):**
  - Dar dikey telefon ekranlarında (`screenWidthDp < 600`) satır başına kart sayısı 3'ten 2'ye düşürüldü; kartların aşırı sıkışması önlendi. Tablet (4 sütun) ve yatay geniş ekran düzenleri (5–6 sütun) korundu.
- **Detay Sayfası Dikey Poster Tercih Eşiği (`DetailHeroGallerySelection`):**
  - Dikey cihazlar için poster tercih oranı eşiği `0.55`'ten `0.75`'e yükseltildi. Artık çok daha geniş bir dikey ekran en-boy oranında yatay arka plan yerine dikey posterler öncelikli olarak seçilir.

---

## 🇬🇧 English

### 🏛️ Full Bangumi Vault Architecture & 4-Module Resource Split
- **Modular Resource Split with Zero XML ID Bloat (+0 XML IDs):**
  - Large-scale Bangumi translation and meta-tag dictionary partitioned across 4 distinct Gradle modules (`bangumi-vault-resources-1`, `bangumi-vault-resources-2`, `bangumi-vault-resources-3`, and main `:app`).
  - Preserves Android 65,535 single DEX/resource ID limits while integrating comprehensive CJK canon, studio/org, and tag databases.
  - Verified with 9 Python validators and 72 passing JUnit tests.

### 🎨 Detail, Reviews, and MyList UI/UX Enhancements
- **Instant Reviews Bottom Sheet Seed (`KitsugiAllReviewsBottomSheet` & `ReviewsTab`):**
  - Pre-loaded reviews from the detail screen tab are now seeded into the "All Reviews" bottom sheet via `initialReviews`.
  - Eliminates empty-screen flicker on open, ensuring instant visibility even while remote/alias ID feeds resolve in the background.
- **MyList Card Density on Narrow Screens (`MyListComponents`):**
  - Reduced column count from 3 to 2 on narrow portrait mobile screens (`< 600dp`) to prevent crowded cards. Tablet (4 cols) and landscape (5–6 cols) layouts remain preserved.
- **Hero Artwork Portrait Poster Ratio Threshold (`DetailHeroGallerySelection`):**
  - Raised portrait tall ratio threshold from `0.55` to `0.75`, reliably prioritizing vertical posters over landscape artwork across a wider variety of phone aspect ratios.

---

# Kitsugi Beta v2.4.337

## 🇹🇷 Türkçe

### 🛡️ 6 Kaynaklı Çapraz Eşitleme Güvencesi & Yanlış Eşleşme Ön-Kapısı (Cross-Sync Hardening)
- **Tüm Hedeflere Genişletilen Kimlik Ön-Kapısı (`CrossSyncIdentityGuard` & `crossWriteBlocked`):**
  - Daha önce yalnızca AniList'e yazarken çağrılan MAL resmi katalog doğrulaması (başlık + tür + tam yıl); artık **MAL, Simkl, Kitsu, Shikimori, Bangumi ve AniList** hedeflerinin tamamına genişletildi.
  - Hatalı ID eşlemelerinin (Simkl `ids.mal`, Kitsu mappings vb.) başka bir eserin puanını, izleme durumunu veya bölümünü sessizce ezmesi tamamen engellendi. Doğrulanamayan eşleşmeler sessizce atlanır (`OUTCOME_SKIPPED_IDENTITY`) ve senkron özeti ekranında açıkça belirtilir.
- **Yanlış-Kimlikli Silme Koruması (`deleteEntry`):**
  - Doğrulanmamış veya yanlış ID eşleşmesine sahip öğelerin diğer servislerdeki gerçek eserleri silmesini önlemek amacıyla 6 hedefin tamamına silme ön-kapısı getirildi.
- **Simkl, Shikimori & Bangumi İyileştirmeleri:**
  - Simkl API için saniyede en fazla 10 GET / 1 POST istek kilidi (`awaitSimklRateLimit`) ve bilinen toplam bölümü aşan ilerlemelerin sıkıştırılması (clamp) eklendi.
  - Shikimori domain fallback (`shikimori.tv`) ve Kitsu alternatif host desteği devreye alındı.
- **15 Kapsamlı Kaynak Araştırma ve Denetim Dokümanı:**
  - `ARASTIRMA_KAYNAK_SENKRON/` dizinine 15 parçalık birinci el API semantiği, hız limitleri ve çapraz denetim raporu (`14_CAPRAZ_ESITLEME_DENETIMI.md`) eklendi.

---

## 🇬🇧 English

### 🛡️ 6-Provider Cross-Sync Identity Guard & Mismatch Protection
- **Extended Identity Gate Across All 6 Targets (`CrossSyncIdentityGuard` & `crossWriteBlocked`):**
  - Official MAL catalog verification (strict title + media type + release year matching), previously only enforced when writing to AniList, is now applied across all 6 targets: **MAL, Simkl, Kitsu, Shikimori, Bangumi, and AniList**.
  - Prevents inaccurate third-party mapping IDs from silently overwriting scores, progress, or statuses on unrelated titles. Unverifiable cross-writes are safely skipped (`OUTCOME_SKIPPED_IDENTITY`) with transparent user notices.
- **False-Identity Deletion Guard (`deleteEntry`):**
  - Prevents accidental remote item deletion caused by ambiguous or mismatched cross-provider IDs across all 6 sync destinations.
- **Simkl, Shikimori & Bangumi Protections:**
  - Enforced Simkl 10 GET / 1 POST per second rate-limiters (`awaitSimklRateLimit`) and episode progress clamping to known totals.
  - Added Shikimori domain fallback (`shikimori.tv`) and robust Kitsu host resolution.
- **Research & Cross-Sync Audit Suite:**
  - Added 15 comprehensive audit and API semantics documents under `ARASTIRMA_KAYNAK_SENKRON/` including `14_CAPRAZ_ESITLEME_DENETIMI.md`.

---

# Kitsugi Beta v2.4.336

## 🇹🇷 Türkçe

### 🔔 MyAnimeList (MAL) Bildirimlerinin Devamı & Sayfalama Kök Neden Onarımı
- **`mal_web_` Kimlik Öneki Uyuşmazlığı Giderildi (`KitsugiNotificationsViewModel.kt` & `MalNotificationsClient.kt`):**
  - `loadMalMore()` önceki bildirimleri `mal_web_<id>` önekiyle, `MalNotificationsClient` ise ham `<id>` ile karşılaştırdığı için aday sayfaların tamamı "yeni kayıt" sanılıyor, ilk 25 bildirim sonsuz döngüde tekrar çekilip eleniyor ve gerçek devam motoruna hiç ulaşılamıyordu. `normalizeKnownIds` / `rawNotificationId` ile kimlik eşlemesi tekilleştirildi ve sıfır ilerleme kilidi (`mergedUnfiltered.size > beforeCount`) eklendi.
- **Çok Katmanlı Devam Motoru (`MalNotificationsWebPager.kt` & `MalNotificationsClient.kt`):**
  - `window.MAL.notification` dışında canlı `XMLHttpRequest` / `fetch()` yanıtları, Vue 2/3 bileşen durumu (`__vue__.$data`) ve `#content` içindeki `.notification-item` DOM satırları birlikte toplanır.
  - Üst kapsayıcı `<div>` yerine doğrudan `.notification-pagination` ve yaprak `"View more"` kontrolleri hedeflenir; çok sayfalı geçişlerde ara anlık görüntüler korunur.
- **Gerçek Kategori Sayıları & Arşiv Temizliği:**
  - `.notificaiton-category-nav` sol menü sayaçları (`Tümü: 45`, `Yeni Bağlantılı Anime: 39`, `Yayında: 4`, `Arkadaşlık İstekleri: 1`, `Profil Yorumları: 1` vb.) ve kategori adresleri doğrudan okunur; ilk 25 kayıt ekrana çizilir çizilmez kalan bildirimler arka planda tamamlanıp yerel arşive kaydedilir.
  - Gerçek MAL web oturumu aktifken eski takvim yedek kayıtlarının (`25 + 3 = 28`) `Tümü` sayısını şişirmesi `retainOldWhere` ile temizlendi.
- **Oynatıcı Zaman Damgası İyileştirmesi (`MpvPlayerEngine.kt`):**
  - `lastPositionNotifyAtMs = -1L` ile sıfırıncı saniyede ilk oynatma zaman damgası bildirimi garantilendi.

---

## 🇬🇧 English

### 🔔 MyAnimeList (MAL) Notification Continuation & Pagination Root-Cause Fix
- **Fixed `mal_web_` ID Prefix Mismatch (`KitsugiNotificationsViewModel.kt` & `MalNotificationsClient.kt`):**
  - `loadMalMore()` previously passed `mal_web_<id>` IDs while `MalNotificationsClient` compared raw `<id>` values, causing page-1 duplicates to pass unseen checks in an endless loop without reaching continuation batches. Normalized all ID checks via `rawNotificationId` / `normalizeKnownIds` with strict progress guards (`mergedUnfiltered.size > beforeCount`).
- **Multi-Source Continuation Collector (`MalNotificationsWebPager.kt` & `MalNotificationsClient.kt`):**
  - Captures notifications from live `XMLHttpRequest` / `fetch()` payloads, Vue 2/3 reactive component state (`__vue__.$data`), `window.MAL.notification`, and rendered `#content .notification-item` HTML elements, while targeting leaf `.notification-pagination` / `"View more"` controls accurately.
- **Accurate Sidebar Category Counts & Archive Hygiene:**
  - Parses exact category totals and URLs from `.notificaiton-category-nav` (`All: 45`, `Related Anime: 39`, `Airing: 4`, etc.), auto-completes older notification batches right after rendering the initial page, and purges stale calendar fallback archive rows when an authenticated MAL web feed is active.
- **Player Timestamp Fix (`MpvPlayerEngine.kt`):**
  - Initialized `lastPositionNotifyAtMs = -1L` so that the initial playback position notification is reliably fired.

---

# Kitsugi Beta v2.4.335

## 🇹🇷 Türkçe

### 🛡️ AniList Servis Kesintisi Koruması, Hızlandırma & Hata Sınıflandırması
- **Yeni Resmi Platform Hata Kodu (`KTS-ANL-2008`):**
  - AniList sunucuları kararlılık sorunları nedeniyle GraphQL API'sini geçici olarak kapattığında (`HTTP 403/503 + "temporarily disabled"`), ekranlarda belirsiz `KTS-COM-1020 Sınıflandırılamayan hata` veya yanıltıcı `HTTP 403 Erişim izni yok / VPN deneyin (KTS-COM-1008)` yerine doğrudan **`KTS-ANL-2008 — AniList API geçici olarak devre dışı`** açıklaması gösterilir.
  - `PlatformFailureRegistry` içinde üst katmandaki boş/sarmalanmış hataların ağ katmanının kaydettiği somut platform hatasını `COM_1020` ile ezmesi engellendi.
- **3x Hızlı Uyarlanabilir AniList Katmanı & Devre Kesici (`KitsugiApiBase.kt`, `KitsugiHttpClient.kt`):**
  - Tüm uygulamayı tek sıraya dizen bloklayıcı `Mutex` + `RetryInterceptor` çarpanı kaldırıldı; yerine `RetryInterceptor`'sız, 15 sn zaman tavanlı `aniListClient`, 3 eşzamanlı izinli `Semaphore` ve `X-RateLimit-Limit` / `X-RateLimit-Remaining` başlıklarına duyarlı uyarlanabilir hız ayarı getirildi.
  - Sunucu şalteri (`temporarily disabled`) ve ardışık gateway çökmeleri için otomatik devre kesici (circuit breaker) eklendi; kapalı sunucuya boşuna onlarca istek atılması durduruldu.
- **Keşfet, Takvim, Profil ve Medya Detay Yedek Zincirleri:**
  - Keşfet toplu raf sorguları paralel hale getirildi; AniList kesintisinde 14 tekil rafa düşüp kuyruğu tıkaması önlendi.
  - Medya detay sayfasında (`KitsugiDetailClient.kt`) AniList çöktüğünde hem Anime hem Manga/Manhwa yapımları için otomatik MAL/Jikan ve Kitsu başlık/kimlik yedek zinciri devreye alındı.

---

## 🇬🇧 English

### 🛡️ AniList Outage Resilience, Adaptive Throughput & Error Classification
- **Dedicated Platform Error Code (`KTS-ANL-2008`):**
  - Classifies AniList's emergency API shutdown (`HTTP 403/503 + "temporarily disabled due to severe stability issues"`) as **`KTS-ANL-2008 — AniList API temporarily disabled`** across Notifications, Explore, Media Detail, and Profile instead of `KTS-COM-1020 Unclassified error` or misleading `HTTP 403 Permission denied (KTS-COM-1008)`.
  - Prevents empty/wrapped upper-layer reports in `PlatformFailureRegistry` from overwriting fresh concrete platform error codes with `COM_1020`.
- **Non-Blocking Adaptive Pacing & Circuit Breakers (`KitsugiApiBase.kt`, `KitsugiHttpClient.kt`):**
  - Replaced the global blocking `Mutex` and double retry loop with a dedicated `aniListClient` (bounded 15s `callTimeout`, no `RetryInterceptor` amplification), `Semaphore(3)` concurrency, header-aware pacing (`X-RateLimit-Limit` / `X-RateLimit-Remaining`), and automatic circuit breakers for server outages and gateway failures.
- **Explore, Calendar, Profile & Detail Fallbacks:**
  - Parallelized Explore batch shelf queries and prevented cascading into 14 single-shelf retries during outages.
  - Added full cross-ID and strict title-verified MAL/Jikan + Kitsu fallback chains for both Anime and Manga/Manhwa detail pages (`KitsugiDetailClient.kt`).

---

# Kitsugi Beta v2.4.334

## 🇹🇷 Türkçe

### 🚀 Video Oynatıcı Kasma/Donma, Ses-Var-Görüntü-Yok & AnimeThemes Onarımı (PR #28)
- **Saniyede 60 Kare Recomposition Boğulması Çözüldü (4 Hz Sınırlandırma):**
  - MPV'nin `time-pos` bildirimleri her karede iletildiği için küçük oynatıcı saniyede ~60 kez Compose state'ine yazıyordu; bu durum detay sayfasında aşırı kasmaya yol açıyordu. Bildirimler 4 Hz'e çekilerek akıcılık sağlandı (seek, yükleme ve süre güncellemeleri anında çalışmaya devam eder).
- **Çift Video Çıkışı (VO) Yeniden Başlatma Hatası Giderildi:**
  - `applyInitOptions` hem `createVideoView` hem `prepare()` çağrılarında çalıştığı için `vo` her videoda iki kez yazılıyor ve libmpv video çıkışı yeniden başlatılarak donma ve ilk saniyelerde siyah kareye yol açıyordu. Video çıkışı motor ömründe yalnızca bir kez kurulacak şekilde izole edildi.
- **Sesi Var Görüntüsü Yok Hatası İçin `hasVideoTrack` Denetimi:**
  - Video codec'i çözülemediğinde oynatıcılar sessizce ses çalıp siyah ekran bırakabiliyordu. `PlayerEngine.hasVideoTrack` eklendi; 6 saniye içinde video görüntüsü bağlanamazsa küçük oynatıcı otomatik olarak diğer motora (MPV ↔ Media3) geçer; ikisi de başarısız olursa "Yeniden Dene / Kaynağı Aç" kartı gösterilir.
- **Media3 YouTube Sayacı & Yükleme Titremesi:**
  - Media3 YouTube oynatımı için 4 Hz konum bildirimi eklendi (00:00 donması giderildi) ve ilk kare geldikten sonra yükleme halkası titreşimi kaldırıldı.
- **AnimeThemes Zengin Video Seçimi:**
  - Yalnızca ilk kayda bakma hatası düzeltildi; tüm kayıtlar taranarak temiz > MP4/H.264 > creditsiz (nc) > WEB > 1080p öncelik sıralamasıyla en uyumlu video seçilir.

### ⚙️ Oynatıcı Gelişmiş Ayarlar Menüsü Yenilendi
- **7 Başlıklı Modern Ayar Grubu:**
  - Tek parça dev gri form yerine, uygulamanın genel teması ve vurgu renkleriyle uyumlu 7 kategorili modern kart tasarımı.
- **Açılır Düzenleme Pencereleri & Dokunulabilir Satırlar:**
  - Sürekli açık metin kutuları yerine dokunulduğunda açılan şık diyaloglar, mevcut seçimi gösteren ayar satırları ve uzun metinlere alan bırakan tam dokunulabilir anahtar (switch) satırları. 32 ayarın kayıt entegrasyonu korundu.

### 🗑️ İndirilenler: Scoped Storage Resim ve Altyazı Silme Onarımı
- **Yeni `MediaStoreFileDeleter` Katmanı:**
  - Android 10+ Scoped Storage'da `File.delete()` çağrısının sessizce başarısız olması engellendi. Dosyalar `DISPLAY_NAME` + `RELATIVE_PATH` ile bulunup `ContentResolver.delete` ile silinir.
- **Sahiplik & Sistem Onayı:**
  - Yeniden kurulumda sahipsiz kalan dosyalar Android 11+ `MediaStore.createDeleteRequest` onay penceresiyle silinir.
- **Grup Silme Onayı & Bildirim:**
  - Yanlışlıkla toplu silmeleri önlemek için onay penceresi eklendi; silme hataları toast ile gösterilip liste diskle senkronize yenilenir.

---

## 🇬🇧 English

### 🚀 Video Player Stutter Fix, Audio-Only Recovery & AnimeThemes Selection (PR #28)
- **Eliminated 60fps Recomposition Storm (4 Hz Throttle):**
  - libmpv `time-pos` notifications throttled to 4 Hz, eliminating up to ~60 Compose state writes per second that previously caused severe UI stuttering during scrolling.
- **Single-Pass Video Output Configuration:**
  - `vo` option is now initialized only once per engine lifecycle instead of twice during view creation and preparation, resolving black-frame hitching.
- **Audio-Only No-Video Track Recovery:**
  - Added `PlayerEngine.hasVideoTrack` checks across Media3 and MPV. If no video track is established within 6s, failover to the alternative engine is triggered.
- **Media3 Progress & AnimeThemes Prioritization:**
  - Added 4 Hz progress ticker for Media3 inline playback and enhanced AnimeThemes candidate sorting (Clean > MP4/H.264 > NC > WEB > up to 1080p).

### ⚙️ Player Advanced Settings Redesign
- **7-Category Structured Architecture:**
  - Replaced legacy monochromatic form with a themed, card-based interface adhering to the app's design language.
- **Modal Editing & Polished Controls:**
  - Replaced persistent text fields with responsive modal editors and high-touch surface switch rows for all 32 settings.

### 🗑️ Downloads: Scoped Storage Deletion Fix
- **New `MediaStoreFileDeleter` Engine:**
  - Resolves silent `File.delete()` failures on Android 10+ shared storage via `DISPLAY_NAME` + `RELATIVE_PATH` and `ContentResolver.delete`.
- **System Deletion Confirmation:**
  - Integrates Android 11+ `MediaStore.createDeleteRequest` for orphaned files and adds a bulk deletion confirmation dialog.

---

# Kitsugi Beta v2.4.333

## 🇹🇷 Türkçe

### 🎬 Küçük Video Oynatıcı Zaman Çizgisi Kontrolü & YouTube Önceliği
- **00:00 Siyah Ekran Yedek Motor Geçişi (`InlinePlayerSupport.kt`):**
  - Motorun sadece `READY` durumuna geçmesi yeterli kabul edilmez; video zaman çizgisi 18 saniye boyunca ilerlemezse (`positionMs == 0`) oynatıcı siyah ekranda takılı kalmaz ve otomatik olarak alternatif dahili motora geçer.
- **YouTube İçin Media3 İlk Tercihi:**
  - YouTube videoları tam ekran oynatıcı hattıyla uyumlu olarak önce Media3 ile açılır. Doğrudan OP/ED video dosyalarında ise kullanıcının MPV/Media3 tercihi korunur.

### 🖼️ Bangumi TMDB & Fanart Eşleşme İyileştirmesi
- **Genişletilmiş Infobox Alternatif Başlıkları (`KitsugiBangumiDetailClient.kt`):**
  - Bangumi infobox'ındaki tüm alternatif ve İngilizce adlar arama kümesine dahil edildi.
  - Latin harfli alternatifler önceliklendirildi ve aranabilir başlık sayısı 3'ten 8'e çıkarıldı.
  - Böylece Bangumi kayıtlarının TMDB ve Fanart.tv üzerindeki afiş, arka plan ve görsel galerilerine bağlanma başarı oranı artırıldı.

---

## 🇬🇧 English

### 🎬 Inline Video Player Timeline Progress & YouTube Default Engine
- **00:00 Black Screen Progress Fallback (`InlinePlayerSupport.kt`):**
  - `READY` engine state alone is no longer considered active playback. If timeline progress remains stagnant at `00:00` for 18s, the player automatically triggers engine failover instead of hanging indefinitely on a black screen.
- **Media3 Priority for YouTube:**
  - YouTube video playback aligns with the fullscreen player pipeline by selecting Media3 by default, while direct OP/ED media continues to respect user engine preferences.

### 🖼️ Bangumi TMDB & Fanart Match Accuracy
- **Infobox Alternative Titles Extraction (`KitsugiBangumiDetailClient.kt`):**
  - All infobox alias titles and localized keys are extracted into candidate search sets.
  - Prioritizes Latin title candidates and raises search query limit from 3 to 8, significantly improving TMDB and Fanart.tv image/metadata cross-matching.

---

# Kitsugi Beta v2.4.332

## 🇹🇷 Türkçe

### 📖 Özgün Wiki Sayfa İçi Canlı Çeviri, Tema & Okunabilirlik Araçları
- **Gelişmiş Tema Seçimi:**
  - *Site Teması*, *Uygulamayı İzle*, *Açık* ve *Koyu* tema seçenekleri eklendi. Özgün wiki yerleşimi varsayılan olarak korunur; wikinin kendi tema renkleri ve sınıfları kullanılır. Resimlere tersleme uygulanmaz, özel/desteklenmeyen skinler için uyarı gösterilir.
- **Yazı Boyutu Ölçekleme:**
  - %80–%180 aralığında dinamik yazı boyutu ayarı, sistem font ölçeğine tam uyum ve tek dokunuşla varsayılan boyuta dönüş.
- **Sayfada Bul (In-Page Find):**
  - Eşleşme sayısı sayacı, önceki ve sonraki sonuçlar arasında gezinme ve hızlı kapatma. Orijinal ve çeviri arasında geçiş yapıldığında arama sonuçları otomatik güncellenir.
- **Sadeleştirilmiş Araç Menüsü & UI İyileştirmeleri:**
  - Canlı çeviri ve reklam engeli doğrudan araç çubuğunda görünür; diğer okuma araçları üç nokta menüsü altında toplandı. Açık/koyu tema kontrastı, dar ekran arama çubuğu, klavye boşluğu ve yükleme zeminleri düzenlendi.
- **Çeviri Tercihi Koruma:**
  - Tema veya yazı tipi boyutu değiştirildiğinde kullanıcının manuel çeviri tercihi sıfırlanmaz.
- **Yerleşik Reklam Engelleme (AdBlock):**
  - StevenBlack host tabanlı reklam engelleme ve seçici kozmetik kurallar entegre edildi.

---

## 🇬🇧 English

### 📖 Wiki In-Page Live Translation, Theming & Readability Tools
- **Advanced Theme Modes:**
  - Added *Site Theme*, *Follow App*, *Light*, and *Dark* appearance modes. Preserves original wiki layout without color-inverting images; displays gentle notices for legacy or unsupported skins.
- **Text Zoom & Scaling:**
  - %80 to %180 text scale slider with system font accessibility scaling support and one-tap reset.
- **In-Page Search (Find in Page):**
  - Interactive match counter, previous/next match navigation, and instant dismissal. Matches refresh dynamically when toggling between original and translated states.
- **Streamlined Reader Toolbar:**
  - Live translate and ad block toggles remain front-and-center, while ancillary options tuck neatly into an overflow menu. Contrast, small-screen search widths, IME keyboard insets, and loading backgrounds polished.
- **Translation State Persistence:**
  - Changing themes or text sizes never resets the user's manual translation toggle.
- **Built-in Ad Blocking:**
  - Integrated StevenBlack host-based ad-blocking and scoped cosmetic rules with local wiki exceptions.

---

# Kitsugi Beta v2.4.331

## 🇹🇷 Türkçe

### 🎬 Detay Sayfası Video Oynatıcı & YouTube/Tema Akış Kök Neden Onarımı
- **İstek Başlığı Ortaklaştırması (`YouTubeRequestPolicy.kt`):**
  - Varsayılan MPV motoru dahil küçük oynatıcı ve tam ekran oynatma rotalarına YouTube ve doğrudan video istek başlıkları (`Referer`, `Origin`, `User-Agent`) aktarılıyor. Başlıksız istek kaynaklı 403 ve oynatma reddi engellendi.
- **Media3 YouTube Tek & Çift Akış Ortak Data Source:**
  - Media3'ün tek URL'li (HLS/progressive) akışları da özel factory'ye bağlandı (`YoutubeChunkedDataSourceFactory.kt`). HLS manifest ve segmentlerinde range query chunking devre dışı bırakılırken progressive akışlarda korundu.
- **YouTube HLS Manifest Desteği Genişletildi (`YouTubeHlsManifestParser.kt`):**
  - Yalnızca master playlist değil, doğrudan media playlist, UTF-8 BOM ve göreli varyant URL'leri destekleniyor. Eşit kalite/çözünürlük durumunda genişlik adayları karşılaştırılarak en uygun akış seçiliyor.
- **Fragman Overlay Düzeltmesi (`SharedTrailerOverlay.kt`):**
  - Tek URL'li akışlarda özel factory üzerinden `MediaSource` kuruluyor ve YouTube HLS adreslerine açık HLS MIME türü atanıyor.
- **Zaman Aşımı & Otomatik Motor Yedekleme (Fallback):**
  - Küçük oynatıcılar `READY`/`ENDED` durumunu bekliyor; 18 saniyede veya oynatma hatasında diğer dahili motora (MPV ↔ Media3) bir kez otomatik geçiş yapıyor. İkinci deneme de başarısız olursa "Yeniden Dene" ve "Tarayıcıda Aç" seçenekleri sunuluyor.
- **Birim Testleri:**
  - `PlayerHlsDetectionTest`, `YouTubeHlsManifestParserTest`, `YouTubeRequestPolicyTest` ve `InlinePlayerSupportTest` ile akış tespiti, başlık politikası ve fallback zinciri doğrulandı.

---

## 🇬🇧 English

### 🎬 Detail Video Player & YouTube/Theme Stream Root Cause Fix
- **Unified Request Header Policy (`YouTubeRequestPolicy.kt`):**
  - Small player routes (including default MPV and full-screen playback) now forward authentic YouTube and direct video request headers (`Referer`, `Origin`, `User-Agent`), resolving headerless 403 playback failures.
- **Media3 YouTube Single & Dual Stream Custom Data Source:**
  - Single-URL YouTube HLS/progressive streams now route through `YoutubeChunkedDataSourceFactory.kt`. Range-chunking is disabled for HLS manifests/segments while preserved for progressive streams.
- **Expanded YouTube HLS Manifest Parser (`YouTubeHlsManifestParser.kt`):**
  - Full support for master playlists, direct media playlists, UTF-8 BOMs, and relative variant URLs. Ties in candidate resolutions are properly resolved by width.
- **Trailer Overlay Stream Alignment (`SharedTrailerOverlay.kt`):**
  - Single-URL trailers construct `MediaSource` through the specialized factory and specify explicit HLS MIME types.
- **Timeout & Automatic Engine Fallback (`InlinePlayerSupport.kt`):**
  - The small inline player tracks playback readiness. If unready after 18s or upon a playback error, it automatically fails over once between internal engines (MPV ↔ Media3). If both fail, actionable "Retry" and "Open in Browser" options appear.
- **Comprehensive Unit Tests:**
  - Added test suites for HLS detection, request policies, manifest parsing, and inline player fallback behavior.

---

# Kitsugi Beta v2.4.330

## 🇹🇷 Türkçe

### ⚡ Otomatik Çeviri Açıkken Kaydırma Kasması Kökten Çözüldü (Akıcı Wiki Deneyimi)
- **Kaydırma Konumu İzolasyonu (`FandomWikiReader.kt`):**
  - Kaydırma konumları Compose snapshot state'inden çıkarılarak invalidasyon üretmeyen düz `HashMap` yapısına taşındı; kaydırma esnasında her karede tüm sayfanın yeniden recomposition'a girmesi tamamen engellendi.
- **Bölüm Düzeyinde Alt Haritalama (`wikiTranslationsFor`):**
  - Canlı çeviri akarken tüm blokları baştan hesaplamak yerine, her bölüm sadece kendi alt çeviri haritasını alır (`wikiTranslationsFor`). Değişmeyen bölümler `remember` önbelleğinden verilir.
- **Tahsisatsız `startsWith` Kontrolü & Memoization:**
  - `hasWikiTranslation` taramalarında her blok için yapılan binlerce geçici `String` birleştirmesi (`baseKey + separator`) kaldırıldı; doğrudan tahsisatsız `startsWith` ve sayfa düzeyi memoization ile bellek tahsisatı (allocation) sıfırlandı.
- **Sabit Bağlantı Açıcı & Önbelleklenmiş Metin Ölçümü:**
  - Bağlantı açıcı lambda kimliği sabitlendi; böylece sayfa kaydırılırken tüm `AnnotatedString` ve metin ölçümlerinin baştan oluşturulması engellendi.
- **İzole Çeviri İkonu Nabız Animasyonu (`KitsugiTranslateIconButton.kt`):**
  - Boşta dururken arka planda sürekli kare tüketen sonsuz animasyon kaldırıldı; nabız animasyonu sadece aktif çeviri (`isLoading`) sürerken ve izole bir alt scope'ta çalıştırılıyor.
- **Kapsam:**
  - Açılır wiki okuyucu (`FandomWikiReader.kt`), Medya wiki sekmesi (`MediaWikiTabContent.kt`), Karakter wiki sekmesi (`CharacterWikiTabContent.kt`), ilgili ViewModel'ler ve çeviri koruma katmanı (`WikiTranslationProtection.kt`).

---

## 🇬🇧 English

### ⚡ Wiki Auto-Translate Scroll Stutter Fix (Buttery 60fps Experience)
- **Non-Invalidating Scroll Position Tracking (`FandomWikiReader.kt`):**
  - Replaced Compose snapshot-backed scroll state with a standard `HashMap`. Eliminates full-page recomposition triggers on every scroll frame.
- **Section-Isolated Translation Sub-Maps (`wikiTranslationsFor`):**
  - Subsections now only subscribe to their own translation slice rather than the entire global translation map. Unmodified sections are resolved instantly via memoized `remember`.
- **Zero-Allocation Translation Key Matching:**
  - Removed recurring string concatenations (`baseKey + separator`) inside `hasWikiTranslation` in favor of allocation-free `startsWith` checks and page-level memoization.
- **Stable Link Click Handlers:**
  - Stabilized the link opener lambda identity to prevent repeated measurement and recreation of full-page `AnnotatedString` blocks during scrolling.
- **Isolated Translate Button Pulse Animation (`KitsugiTranslateIconButton.kt`):**
  - Stopped continuous idle animation frame consumption. Pulsing only runs when actively translating (`isLoading`) within an isolated scope.
- **Full Coverage:**
  - Bottom sheet wiki reader (`FandomWikiReader.kt`), Media Wiki tab (`MediaWikiTabContent.kt`), Character Wiki tab (`CharacterWikiTabContent.kt`), and translation protection utilities.

---

# Kitsugi Beta v2.4.329

## 🇹🇷 Türkçe

### 🎬 Detay Videoları YouTube HLS Düzeltmesi & Çift Dokunma Kısayolu
- **YouTube HLS Akış Algılama (`PlayerMediaSourceFactory.kt`):**
  - YouTube'un `.m3u8` uzantısı içermeyen `manifest.googlevideo.com/.../hls_playlist/...` biçimli HLS akışları otomatik olarak HLS formatı olarak tanınır.
  - Media3'ün akışı yanlışlıkla progressive gibi açıp `00:00 / 00:00` süresinde takılı kalma sorunu kökten çözüldü.
- **HLS Önceliği & Geri Dönüş Hattı (`InAppYouTubeExtractor.kt`):**
  - YouTube çıkarımında geçerli bir HLS akışı mevcutsa video/ses akışı yerine HLS öncelenir; HLS bulunamazsa adaptive/progressive akışlar yedek olarak korunur.
- **Küçük Oynatıcı Çift Dokunma Kısayolu (`KitsugiDetailPlayerComponents.kt`):**
  - Detay sayfasındaki küçük oynatıcıya ekranın sağına/soluna çift dokunarak 10 saniye ileri/geri sarma özelliği ve görsel animasyon geri bildirimi eklendi.
- **Regresyon Testleri (`PlayerHlsDetectionTest.kt`):**
  - YouTube HLS ve DASH manifest ayrıştırması için birim testleri eklendi ve başarıyla doğrulandı.

### ☁️ Kitsugi Hesap & Supabase Entegrasyon Tamamlaması
- **Güvenli E-Posta Doğrulama Hattı (`AccountConfirmationLink.kt` & `MainActivity.kt`):**
  - Supabase tek kullanımlık e-posta onay kodları güvenli biçimde ayrıştırılır; Activity yaşam döngüsü yenilenmelerinde kodun mükerrer çalıştırılması engellenir.
  - Hata mesajları hassas bağlantı ve belirteç bilgilerini gizleyecek şekilde `AccountErrorFormatter` ile filtrelenir.
- **Hesap & Senkronizasyon Altyapısı:**
  - Form doğrulamaları (`AccountFormValidation`), oturum kilitleme (`AccountSessionLock`), bağlı hesap kasası (`LinkedAccountVault`) ve arka plan yedekleme işçisi (`VaultBackupWorker`) tamamlandı.
  - Bildirim arşivi bulut yedeklemesi, yerel depolama yazma işlemi bittikten hemen sonra tetiklenecek şekilde `NotificationArchiveStore` yapısına entegre edildi.
  - Hesap ayrıcalık sertleştirme migrasyonu (`20261021_account_privilege_hardening.sql`) eklendi.

---

## 🇬🇧 English

### 🎬 YouTube HLS Stream Detection & Player Double-Tap Seek
- **YouTube HLS URL Recognition (`PlayerMediaSourceFactory.kt`):**
  - Correctly detects YouTube's extensionless HLS manifests (`manifest.googlevideo.com/.../hls_playlist/...`) as HLS MIME type.
  - Fixes the `00:00 / 00:00` stalled playback bug caused by Media3 misidentifying HLS streams as progressive media.
- **HLS Prioritization (`InAppYouTubeExtractor.kt`):**
  - Prioritizes HLS playback when available, while gracefully falling back to adaptive/progressive streams if unavailable.
- **Mini-Player Double-Tap Seeking (`KitsugiDetailPlayerComponents.kt`):**
  - Double-tap on left/right halves of the detail player to seek 10 seconds backward or forward with ripple animation feedback.
- **HLS Detection Regression Tests (`PlayerHlsDetectionTest.kt`):**
  - Unit tests verifying YouTube HLS manifest parsing and DASH exclusion.

### ☁️ Kitsugi Account & Supabase Integration
- **Hardened Email Confirmation (`AccountConfirmationLink.kt` & `MainActivity.kt`):**
  - Single-use confirmation codes are safely parsed and consumed once across Android Activity recreations.
  - Error messages are sanitized through `AccountErrorFormatter` to prevent leaking auth parameters in logs or toasts.
- **Account & Vault Synchronization:**
  - Added robust form validation, session locking, encrypted linked account vault, and background sync worker (`VaultBackupWorker`).
  - Streamlined notification archive cloud sync via `NotificationArchiveStore`.
  - Added account privilege hardening migration (`20261021_account_privilege_hardening.sql`).

---

# Kitsugi Beta v2.4.328

## 🇹🇷 Türkçe

### 🛡️ Kapsamlı Platform Hata Kodları Sistemi (KTS-SRC) & Manga / Simkl Entegrasyonu
- **Tüm Liste Sağlayıcılarında Standart Hata Kodları (`PlatformFailureRegistry`):**
  - 7 ana liste platformu (AniList, MyAnimeList, Kitsu, Shikimori, Bangumi, TMDB, Simkl) için tüm hata durumları tek merkezde sınıflandırıldı ve `PlatformFailureRegistry` kayıt defterine bağlandı.
  - Profil, Kullanıcı Listesi, Bildirimler, Arama, Keşfet (Explore), Yayın Takvimi, Detay ekranları (Anime, Bölümler, Karakterler, Personel, Stüdyo), TV arayüzü ve OAuth kimlik doğrulama akışlarının tamamına şık `PlatformErrorCodeChip` hata rozetleri eklendi.
- **Manga Ekosistemi Kaynak Hata Kodları:**
  - Manga ana sayfası, manga detay, kaynak tarayıcısı ve okuyucu ekranlarında kaynak hataları artık ilgili `KTS-SRC` hata kodlarıyla görüntüleniyor.
  - Kaynak tarayıcısında hata veren kaynağın çipinin yanında doğrudan ilgili hata kodu sunuluyor.
- **Simkl Kimlik Doğrulama Ağ Entegrasyonu (`SimklAuthManager`):**
  - `SimklAuthManager`, uygulamanın ortak HTTP istemcisine (`KitsugiHttpClient`) bağlanarak tüm HTTP ve ağ hatalarının platform hata defterine eksiksiz kaydedilmesi sağlandı.

---

## 🇬🇧 English

### 🛡️ Unified Platform Error Codes System (KTS-SRC) & Manga / Simkl Integration
- **Universal Error Codes Across All 7 Providers (`PlatformFailureRegistry`):**
  - Standardized error classification across AniList, MyAnimeList, Kitsu, Shikimori, Bangumi, TMDB, and Simkl via `PlatformFailureRegistry`.
  - Added dedicated `PlatformErrorCodeChip` widgets to Profiles, User Media Lists, Notifications, Search, Explore, Airing Calendar, Detail subpages (Episodes, Characters, Staff, Studios), TV UI, and OAuth login flows.
- **Manga Source Error Codes:**
  - Integrated `KTS-SRC` error reporting into Manga Home, Manga Detail, Browse, and Chapter Reader views.
  - In the manga source browser, failing sources surface their designated error codes right next to their status chips.
- **Simkl Auth Shared Client Integration (`SimklAuthManager`):**
  - Routed Simkl authentication through the shared `KitsugiHttpClient`, enabling automatic capture of HTTP/network exceptions into the centralized platform registry.

---

# Kitsugi Beta v2.4.327

## 🇹🇷 Türkçe

### 🎬 Detay Sayfası Video & Fragman Oynatıcısı Düzeltmesi
- **YouTube Veri Kaynağı & Başlık İyileştirmesi (`YoutubeChunkedDataSourceFactory.kt`):**
  - YouTube akış isteklerine tarayıcı uyumlu `User-Agent` ve `Referer: https://www.youtube.com/` başlıkları eklendi; başlık eksikliğinden kaynaklanan engelleme ve hız sınırlandırmaları (throttle) engellendi.
  - Parçalı akış URL'lerinde mevcut `range` parametresi temizlenerek yinelenen aralık parametresi sorunu giderildi; videonun `00:00` süresinde takılı kalması çözüldü.
- **Oynatıcı Durum & Yeniden Deneme Arayüzü (`KitsugiDetailPlayerComponents.kt`):**
  - Oynatıcı yükleme durumuna açıklayıcı görsel gösterge ve metin eklendi.
  - Kaynak çekilemediğinde kullanıcıya doğrudan "Yeniden Dene" ve "YouTube'da Aç" seçenekleri sunuldu.

### 🎨 Listem Görünümleri & Ek Kaynak Rozetleri Uyumu
- **5 Görünümde Bütünleşik Ek Kaynak Şeridi (`KitsugiMediaEntryCard.kt` & `LibrarySourceBadgeStrip.kt`):**
  - Kompakt, Rahat, Büyük, Minimalist ve Izgara görünümlerinde ek kaynak rozetleri kart dışına taşan yapıdan çıkarılıp kartın içindeki ortak alt şeride entegre edildi.
  - Orijinal kaynak logoları ve renkleri korunurken ana kaynakla ve birbiriyle tekrarlayan rozetler temizlendi (`LibraryBadgeSources.kt`).
  - Büyük harf ve kaynak adı dönüşümleri Türkçe yerel ayarlardan (`Locale.ROOT`) bağımsızlaştırılarak kararlı hale getirildi.
- **Tutarlı Durum ve Puan Kapsülleri:**
  - Tüm liste görünümlerinde durum rozetleri opak, tema uyumlu ve ince çerçeveli ortak kapsül tasarımına kavuşturuldu; poster renk karmaşasının metin okunurluğunu bozması engellendi.
  - Puan kapsülü ortaklaştırılarak puan gizleme ve biçim tercihleriyle tam uyumlu hale getirildi.
- **Izgara Hizalaması ve Çoklu Satır Desteği (`MyListGridContent.kt`):**
  - Izgara başlık alanı iki satıra sabitlendi; aynı satırdaki kartların yükseklikleri eşitlenerek alt rozet şeridi hizalandı.
  - Dar ekranlarda rozet şeridi ve durum satırları esnek şekilde satır atlayarak taşma yapmaz.

### 📑 Listem Sıralama & Detay Galerisi Bottom Sheet Dönüşümü
- **Listem Sıralama Menüsü Bottom Sheet (`KitsugiMyListSortMenu.kt`):**
  - Eski açılır menü yerine modern, ergonomik ve yuvarlak köşeli `ModalBottomSheet` yapısına geçildi.
  - "Son eklenen" dahil olmak üzere tüm sıralama kriterleri ve sıralama yönü (artan/azalan) alttan açılan şık panel üzerinden seçilebiliyor.
- **Detay > Resimler Kategori & Kaynak Seçici Bottom Sheet (`EntryDetailTabContents.kt`):**
  - Resimler sekmesindeki kategori seçici modern bir bottom sheet'e dönüştürüldü.
  - Görsel kategorileri (Afiş, Fanart, Logo, Banner vb.) ile birlikte MAL, Fanart.tv, TMDB gibi kaynaklar ve görsel adetleri ayrı rozetler halinde listeleniyor.
  - Galeri görünümünde görseller kaynaklarına göre gruplandı; dokunulan kaynağın görselleri doğrudan galeride açılıyor.

### 🌐 Bangumi Bildirimleri Başlık Dili Çözümlemesi
- **Dil Uyumu & Dinamik Güncelleme (`KitsugiNotificationsViewModel.kt`):**
  - Bangumi izleme bildirimlerindeki Japonca/Çince başlıklar kullanıcının tercih ettiği başlık diline (`ROMAJI` veya `ENGLISH`) göre `BangumiTitleCache` ve `BangumiSubjectTitleResolver` ile çözümleniyor (Profil ve Listem sekmeleriyle tam parite).
  - Ayarlar ekranından başlık dili değiştirildiğinde, bildirimler ekranı açıksa başlıklar otomatik olarak yeniden yüklenir.

---

## 🇬🇧 English

### 🎬 Detail Page Video & Trailer Player Fixes
- **YouTube Data Source & Header Parity (`YoutubeChunkedDataSourceFactory.kt`):**
  - Added browser-like `User-Agent` and `Referer: https://www.youtube.com/` headers to prevent YouTube streaming requests from being throttled or rejected.
  - Strips existing `range` query parameters before appending chunk offsets, eliminating the stalled `00:00` playback issue.
- **Player State & Retry UI (`KitsugiDetailPlayerComponents.kt`):**
  - Clear loading feedback and retry actions ("Yeniden Dene" / "Open in YouTube") when source extraction fails.

### 🎨 MyList Views & Additional Source Badges Harmonization
- **Unified In-Card Source Strip Across 5 Views (`KitsugiMediaEntryCard.kt` & `LibrarySourceBadgeStrip.kt`):**
  - Extra source badges across Compact, Comfortable, Large, Minimalist, and Grid views are now housed inside the card within a unified, theme-aware bottom strip.
  - Preserved original source brand colors while deduplicating entries and suppressing the primary source badge (`LibraryBadgeSources.kt`).
  - Locale-safe badge key parsing (`Locale.ROOT`) prevents Turkish locale edge cases.
- **Consistent Status and Score Capsules:**
  - Standardized status badges into crisp, high-contrast, theme-toned capsules unaffected by varied poster colors.
  - Harmonized score capsule across all five layouts, honoring user score display preferences.
- **Grid Alignment & Wrapping (`MyListGridContent.kt`):**
  - Fixed 2-line title bounds with equalized card heights in the same row, aligning source badge strips cleanly.
  - Graceful wrapping for badges and metadata in tight viewports.

### 📑 MyList & Detail Gallery BottomSheet Redesign
- **MyList Sort Options BottomSheet (`KitsugiMyListSortMenu.kt`):**
  - Replaced legacy popup dropdown with a modern, rounded `ModalBottomSheet`.
  - Supports all sorting keys (including "Recently Added") and ascending/descending directions.
- **Detail Gallery Category & Source Picker BottomSheet (`EntryDetailTabContents.kt`):**
  - Transformed the gallery category selector into an interactive `ModalBottomSheet`.
  - Displays image categories alongside provider badges (MAL, Fanart.tv, TMDB) and asset counts per provider.
  - Images are grouped by provider under each category; tapping a source launches the filtered gallery view.

### 🌐 Bangumi Notifications Title Language Resolution
- **Title Language Parity & Dynamic Refresh (`KitsugiNotificationsViewModel.kt`):**
  - Resolves Bangumi notification titles using `BangumiTitleCache` and `BangumiSubjectTitleResolver` to match the user's title language preference (`ROMAJI` / `ENGLISH`).
  - Reactively reloads notification titles when title language is toggled in App Settings.

---

# Kitsugi Beta v2.4.326

## 🇹🇷 Türkçe

### 🔔 MyAnimeList (MAL) Bildirimleri Web Uç Noktaları, Kategori Sistemi & "View More" Sayfalama

Bu sürümde, MyAnimeList bildirim sistemi (`myanimelist.net/notification`) resmi web arayüzü ile tam pariteye kavuşturuldu; 11 ayrı kategori filtresi, poster gösterimi ve sayfa yükleme mekanizması baştan aşağı yenilendi.

- **11 Kategori Filtreleme Sistemi (`MalNotificationCategory.kt`):**
  - MAL sol menüsüyle birebir uyumlu kategori desteği: **Tümü**, **Yeni Bağlantılı Anime**, **Yayında**, **Arkadaşlık İstekleri**, **Profil Yorumları**, **Blog**, **Forum Alıntıları**, **Bahsetmeler**, **Takip Edilen Konular**, **Kulüp Mesajları** ve **Ödeme**.
  - Her kategori `typeIdentifier` bazlı yerel filtreleme ile desteklenir; filtre değişiminde gereksiz ağ trafiği oluşturulmaz.
- **Web Uç Noktaları Tek Merkezde (`MalNotificationWebEndpoints.kt`):**
  - Sayfalama (`/notification?offset=20`), kategori parametreleri (`category=related_anime`) ve okundu işaretleme engelleme kuralı tek bir mimaride toplandı.
- **Poster Ayrıştırması & Sayfalama Düzeltmesi (`MalNotificationsClient.kt`):**
  - "Yeni Bağlantılı Anime" kartlarında generic zil simgesi yerine animeye ait gerçek afiş (`imageUrl` / `image_url` / `images.jpg.image_url`) gösterilir.
  - `hasNextPage` mantığı `items.size >= 20` olarak düzeltildi; sayfa sonlarında sonsuz döngü engellendi.
- **"View more" Tıklama Algoritması Yenilendi (`MalNotificationsWebPager.kt`):**
  - Yalnızca "load more" arayan eski regex genişletildi; "View more", "Load more", "Older", "Daha fazla" ve tüm DOM elemanları (`button`, `a`, `div`, `span`, `[role=button]`) taranarak çok sayfalı bildirimlerin (45+ bildirim) tamamının kesintisiz çekilmesi sağlandı.
- **Yenilenen Bildirim Arayüzü (`KitsugiNotificationsScreen.kt` & `KitsugiNotificationsViewModel.kt`):**
  - MAL sekmesine rozetli sayaçlı şık kategori çipleri (`MalFilterHeader`) eklendi.
  - Bildirim yoksa boş durum mesajı ve akıcı geçişler sağlandı.

---

## 🇬🇧 English

### 🔔 MyAnimeList (MAL) Notification Web Endpoints, Categories & "View More" Pagination

Aligns MyAnimeList notifications (`myanimelist.net/notification`) with full web parity, introducing 11 category filters, cover poster extraction, and robust "View more" multi-page loading.

- **11 Category Filtering Engine (`MalNotificationCategory.kt`):**
  - Complete parity with MAL's sidebar: All, Related Anime, Airing, Friend Requests, Profile Comments, Blog, Forum Quotes, Mentions, Watched Topics, Club Messages, and Payment.
  - Instant client-side filtering via `typeIdentifier` without redundant network roundtrips.
- **Unified Web Endpoints (`MalNotificationWebEndpoints.kt`):**
  - Centralized endpoint builder supporting offset-based pagination (`?offset=20`), category parameters, and read-receipt blocker.
- **Poster Extraction & Pagination Integrity (`MalNotificationsClient.kt`):**
  - Extracts key art from `imageUrl`/`image_url`/`images.jpg` payloads so related anime entries feature real anime posters instead of fallback bell icons.
  - Fixed `hasNextPage` condition to prevent runaway scroll loops.
- **Robust "View more" Click Handler (`MalNotificationsWebPager.kt`):**
  - Expands detection across localized text tokens ("View more", "Load more", "Show more", "Older", "Daha fazla") and element selectors (`button`, `a`, `div`, `span`, `[role=button]`), reliably unlocking 45+ notification backlogs.
- **Refined Notification UI (`KitsugiNotificationsScreen.kt` & `KitsugiNotificationsViewModel.kt`):**
  - Horizontal chip header with badge counters (`MalFilterHeader`) and graceful empty state feedback.

---

# Kitsugi Beta v2.4.325

## 🇹🇷 Türkçe

### 🧯 Kritik Açılış Çökmesi (0. Saniye `java.lang.VerifyError`) ve Çökme Raporlama Hattı Kökten Düzeltildi

Google Drive üzerinden iletilen 13 tanı dosyasının tamamı satır satır incelenerek v2.4.320–v2.4.324 sürümlerinde uygulamayı açıldığı anda (`Uptime: 0s`) çökerten birincil Dalvik DEX bayt kodu hatası ve çökme raporlama hattındaki 6 ayrı ikincil hata kökten giderildi. Ayrıntılı adli analiz ve görev planı:
- `PLAN_TASK_VERIFYERROR_255_YAZMAC_TASTMASI_VE_COKME_SISTEMI_2026-10-21.md`
- `KOK_NEDEN_RAPORU_VERIFYERROR_255_YAZMAC_TASTMASI_VE_COKME_SISTEMI_2026-10-21.md`

- **1. Birincil Kök Neden — `AppSettings` Dalvik DEX 255-Yazmaç Taşması (`VerifyError` @ `0x1FE` & `0x275`):**
  - **Hata:** `java.lang.VerifyError: Verifier rejected class com.kitsugi.animelist.data.settings.AppSettings` (`[0x1FE]`) ve R8 yatay sınıf birleştirmesi (`HorizontalClassMerger`) nedeniyle `androidx.room.r.g(java.lang.Object)` (`[0x275]`): `Rejecting invocation, expected 5 argument registers, method signature has 6 or more`.
  - **Kesin Mekanizma:** v2.4.319'da 241 alanı (sentetik varsayılan yapıcıda 254 yazmaç — sınıra 1 yazmaç kala!) bulunan `data class AppSettings`, v2.4.320 ve sonrasında 248 alana ulaştı (birincil `<init>` 252 yazmaç, Kotlin sentetik `<init>(..., mask0..mask7, DefaultConstructorMarker)` ve `copy$default` ise **261 yazmaç**). Dalvik `invoke-*/range` (`3rc`) buyruğundaki `AA` alanı 8 bitlik işaretsiz tamsayı (`0..255`) olduğundan D8/R8 `261 & 0xFF = 5` taşmasıyla buyruğa `5` yazdı; Android ART `MethodVerifier` ise 261 parametreli imzayla karşılaştırıp sınıfı anında reddetti. Ayrıca R8 `HorizontalClassMerger`, `KitsugiSplashActivity` içindeki `SuspendLambda`'yı Room işlem sınıfı `androidx.room.r` ile birleştirdiği için hem `WM.task-1` (59. ms) hem `main` UI thread'i (198. ms) daha ekran çizilemeden çöktü.
  - **Mimari Çözüm (`AppSettings.kt`, `SettingsDataStore.kt`, `KitsugiSplashActivity.kt`, `SettingsScreen.kt`):** 248 alan; `ProfileAndListSettings` (42), `ThemeAndAppUiSettings` (19), `PlayerPlaybackSettings` (28), `PlayerOverlaySettings` (28), `MpvAndSubtitleSettings` (36), `GestureAndSkipSettings` (24), `IntegrationsAndNotificationsSettings` (40) ve `DownloadAndMangaSettings` (31) olmak üzere 8 alt veri sınıfına ayrıldı. `AppSettings` birincil/sentetik yapıcısı **261 yazmaçtan 11 yazmaca (%95,8 düşüş)**, en büyük alt sınıf ise **46 yazmaca** indirildi; tüm 248 alan sıfır ek bellekli hesaplanan özellik (`get()`) olarak %100 geriye dönük uyumlu korundu.
- **2. Çökme İşleyicisi Çoklu-Thread Yarışı Önlemi (`KitsugiCrashHandler.kt`):**
  - Birden fazla arka plan worker'ının aynı milisaniyede çöktüğü durumlarda (ör. 6+ `DefaultDispatcher-worker` fırtınası) tek uçuş (`AtomicBoolean` + kilitli ön-plan yükseltmesi) koruması eklendi; `crash_log.txt` ezilmesi, çoklu `KitsugiCrashActivity` başlatılması ve İndirilenler'e kopya dosya yağması engellendi.
- **3. Boş (73 Bayt) `logcat_at_crash.txt` Sorunu Çözüldü (`KitsugiCrashLogger.kt`):**
  - 400 ms'lik erken `process.destroy()` zaman aşımı 1500 ms'ye çıkarıldı, `main,system,crash` tamponları birleştirildi ve boş çıktı durumunda taze dosyanın ezilmesi engellenip doğrudan çökme raporu yedeği eklendi.
- **4. 2,27 MB'lık Bayat `native_crash.txt` Sızıntısı Giderildi (`kitsugi_crash_handler.cpp`, `NativeCrashBridge.kt`, `KitsugiSessionSupervisor.kt`):**
  - C++ sinyal yakalayıcısındaki `O_APPEND` bayrağı `O_TRUNC` yapıldı ve `/proc/self/maps` çıktısında boş anonim guard bölgeleri elenerek boyut ~2,3 MB'tan ~15 KB'a düşürüldü.
  - `NativeCrashBridge` artık `pid=` ve `epoch=` alanlarını ayrıştırıp yalnızca çöken sürece ait izi iliştirir; önceki günlerden kalan bayat `native_crash.txt` dosyaları ilk açılışta otomatik temizlenir.
- **5. Post-Mortem Logcat Analizinde Java/ART Fatal İstisna Tespiti (`KitsugiSessionSupervisor.kt`):**
  - `FATAL EXCEPTION:` ve `VerifyError` / `LinkageError` satırları artık doğrudan ölüm sebebi başlığına yazılır; kanıt satırları varken yanlış `"SEBEP BULUNAMADI"` gösterilmez.
- **6. Çökme Ekranı Büyük Metin Render Koruması (`KitsugiCrashActivity.kt`):**
  - Kopyala / Paylaş / İndirilenler'e Kaydet tam metni korurken ekrandaki önizleme 48 KB ile sınırlandırılarak `StaticLayout` takılması ve `GL_MAX_TEXTURE_SIZE` taşması engellendi.
- **7. MAL Web/Bildirim Sayfalama RFC 3986 URL Çözümleme Düzeltmesi (`MalWebParser.kt`, `MalNotificationsClient.kt`):**
  - `java.net.URI.resolve("?p=2")` (RFC 2396) hatası nedeniyle son yol bileşeninin düşmesi sorunu `okhttp3.HttpUrl` (RFC 3986) ile giderildi.
- **8. Kalıcı Regresyon ve İkili DEX Doğrulama Kalkanı (`AppSettingsAndCrashDiagnosticsGuardTest.kt`, `scripts/verify_apk_dex_bytecode.py`):**
  - Hem birim testi düzeyinde yazmaç sınırı denetimi hem de derlenen gerçek R8 Release ve Debug APK'larının içindeki tüm `classes*.dex` buyruklarını ART `MethodVerifier` kurallarıyla doğrulayan CI adımı eklendi.

---

## 🇬🇧 English

### 🧯 Critical Cold-Start Crash (`java.lang.VerifyError` at `Uptime: 0s`) & Crash Diagnostics Pipeline Overhaul

Line-by-line forensic inspection of all 13 diagnostic artifacts identified and permanently resolved the primary Dalvik DEX bytecode overflow killing v2.4.320–v2.4.324 immediately at launch (`Uptime: 0s`), alongside 6 secondary bugs in the crash reporting pipeline and MAL pagination URL resolution.

- **1. Primary Root Cause — `AppSettings` Dalvik DEX 255-Register Overflow (`VerifyError` @ `0x1FE` & `0x275`):**
  - `AppSettings` crossed from 254 synthetic constructor slots in v2.4.319 to **261 register slots** in v2.4.320+, overflowing the 8-bit `AA` count field (`261 & 0xFF = 5`) in Dalvik `3rc` `invoke-*/range` instructions and crashing both `SettingsDataStore` (`AppSettings.<init>`) and R8 horizontally merged `SuspendLambda` classes (`androidx.room.r.g`) on `WM.task-1` and `main`.
  - Refactored `AppSettings` into 8 domain sub-models (`AppSettings` synthetic constructor reduced from **261 -> 11 slots**, max sub-model **46 slots**) while preserving all 248 properties via zero-overhead delegated getters.
- **2. Crash Pipeline Hardening (`KitsugiCrashHandler`, `KitsugiCrashLogger`, `NativeCrashBridge`, `KitsugiSessionSupervisor`, `KitsugiCrashActivity`, `kitsugi_crash_handler.cpp`):**
  - Added single-flight coordination with foreground main-thread upgrade lock against multi-worker crash stampedes.
  - Fixed 73-byte empty `logcat_at_crash.txt` captures (1500 ms join window, `main,system,crash` buffers, non-destructive fallback).
  - Replaced `O_APPEND` with `O_TRUNC` and filtered anonymous `/proc/self/maps` regions in native signal handler; added strict `pid`/`epoch` matching and automatic cleanup of legacy multi-MB `native_crash.txt` files.
  - Added `FATAL EXCEPTION` / `VerifyError` recognition to post-mortem logcat analysis and 48 KB UI render capping in `KitsugiCrashActivity`.
- **3. MAL Web & Notifications RFC 3986 Pagination Resolution (`MalWebParser.kt`, `MalNotificationsClient.kt`) & CI DEX Bytecode Verifier (`scripts/verify_apk_dex_bytecode.py`):**
  - Replaced broken RFC 2396 `java.net.URI.resolve("?...")` calls with RFC 3986 `okhttp3.HttpUrl.resolve` and added full binary `classes*.dex` instruction verification on both Debug and R8 Release APKs in CI.

---

# Kitsugi Beta v2.4.324

## 🇹🇷 Türkçe

### 🎠 Vitrin Göstergesi, Otomatik Kaydırma & Dikey Modda Poster İyileştirmeleri

Bu sürüm, ana sayfadaki vitrin (hero banner) bileşeninde yaşanan gösterge çubuğu sayısı hatasını, 5 saniyede bir otomatik kaydırmayı ve dikey telefondaki görsel yerleşimini kapsamlı şekilde düzeltir.

- **Vitrin barı artık gerçek sayfa sayısıyla birebir (`HomeHeroPolicy.kt` & `KitsugiHeroSection.kt`):**
  - **Sorun:** Eski kodda kayan pencere mantığıyla en fazla 7 bar çiziliyordu. 18 veya daha fazla içerik olsa dahi bar 7 adeti geçmiyor, sayfa kaydırıldıkça barlar kayarak "bug" hissi veriyordu.
  - **Çözüm:** Kayan pencere kaldırıldı; kaç içerik varsa satırda o kadar bar çizilir. İçerik sayısı arttıkça barlar orantılı incelir, toplam sayı her zaman doğrudur.
- **5 saniyede bir kesintisiz otomatik kaydırma (`KitsugiHeroSection.kt`):**
  - **Kök Neden:** `LaunchedEffect` anahtarlarında `pagerState.isScrollInProgress` yer alıyordu. Sayfa kaydırma animasyonu başlar başlamaz bu durum `true` oluyor, anahtar değiştiği için effect anında iptal ediliyor ve animasyonu durduruyordu.
  - **Çözüm:** Efekt yalnızca kararlı anahtarlara bağlandı. Kullanıcı dokunmadığı, sürüklemediği ve sayfayı boşta bıraktığı 5 saniye boyunca sayaç dolar ve sayfa yumuşak bir animasyonla kayar. Kenara ulaşıldığında yön değişir (ping-pong).
- **Dikey telefon modunda yüksek çözünürlüklü poster (`HeroVitrinLayout.kt`, `HomeHeroArtworkLoader.kt`):**
  - **Sorun:** Dikey telefon ekranında vitrin bandı dar ve uzun (en-boy < 1) olduğu için 16:9 yatay görsel bantta ince bir şerit olarak kalıyor, altındaki alan siyah boşluk oluyordu.
  - **Çözüm:** Ekran geometrisine göre dikey modda (`prefersPoster = true`) içeriğin en kaliteli posteri (TMDB/Fanart `original` veya yerli kaynağın en yüksek çözünürlüklü posteri) bandı tam kaplayacak şekilde (`Crop` + scrim) yerleşir.
  - **Kendi Kendini Onarma:** Poster yüklenemezse veya decode edilen görsel dikey değilse sayfa otomatik olarak güvenli yatay şerit görünümüne geri döner. Yatay, tablet ve TV modları yatay görselle devam eder.

---

## 🇬🇧 English

### 🎠 Home Hero Indicator, Autoplay & Vertical Poster Overhaul

Resolves showcase banner pagination bar count, restores 5-second automatic carousel sliding, and adapts vertical smartphone screens with crisp full-bleed posters.

- **Exact Indicator Bar Count (`HomeHeroPolicy.kt` & `KitsugiHeroSection.kt`):**
  - Eliminated the sliding 7-bar window limit. The indicator count now strictly mirrors the actual number of showcase pages with dynamically weighted slot widths.
- **Restored 5-Second Autoplay Loop (`KitsugiHeroSection.kt`):**
  - **Root Cause:** Passing `isScrollInProgress` into the `LaunchedEffect` keys aborted the effect as soon as animation started.
  - Refactored autoplay into a single stable coroutine loop that smoothly slides pages after 5 seconds of uninterrupted user idle, pausing on touch/drag.
- **Adaptive Full-Bleed Posters for Portrait Mode (`HeroVitrinLayout.kt`, `HomeHeroArtworkLoader.kt`):**
  - Portrait smartphones now prioritize maximum-resolution vertical key art (TMDB/Fanart original or upscaled provider cover) over squished 16:9 backdrops.
  - Self-healing fallback: smoothly degrades to ambient horizontal backdrop if the poster fails to load or resolves to a non-vertical aspect ratio.

---

# Kitsugi Beta v2.4.323

## 🇹🇷 Türkçe

### 📐 Detay Sayfası Harici Puanlar (MDBList) & Kart Boşluk Düzeltmesi

Bu sürümde, detay sayfasında (API detay görünümü) "Harici Puanlar" kartının altındaki boşluğun sıfır kalması sorunu düzeltildi ve tüm kart yerleşimi simetrik hale getirildi.

- **Harici Puanlar alt boşluğu eşitlendi (`ApiDetailTabContents.kt`):**
  - **Sorun:** `ApiDetailOverviewTab` görünümünde kartlar manuel `Spacer(14.dp)` ile diziliyordu. Harici Puanlar (MDBList) kartının üstünde 14 dp boşluk varken, altındaki "Türler" veya "Stüdyolar" kartı ile arasında boşluk 0 dp kalıyor ve kartlar birbirine yapışıyordu.
  - **Çözüm:** `ApiDetailOverviewTab` konteyneri, `EntryDetailOverviewTab` ile birebir aynı düzene (`Column(verticalArrangement = Arrangement.spacedBy(14.dp))`) geçirildi.
  - **Otomatik Aralık Korunması:** Manuel Spacer'lar kaldırıldığı için, MDBList harici puanları yüklenmediğinde ya da bulunmadığında da komşu kartlar arasındaki 14 dp mesafe bozulmadan ve fazladan boşluk bırakmadan korunur.
  - **Tüm Detay Sekmeleri Denetlendi:** Detay sayfalarındaki diğer tüm sekmelerin (`StatsTab`, `ReviewsTab`, `CharactersTab`, `RelationsTab`, `RecommendationsTab`) `Arrangement.spacedBy(...)` yapısı kontrol edildi; aralık dengesizliği yaşayan başka bir kart bulunmamaktadır.

---

## 🇬🇧 English

### 📐 Detail Page External Ratings (MDBList) & Card Spacing Fix

Resolves card vertical spacing inconsistency where the "External Ratings" (MDBList) card lacked bottom margin on the API detail overview page.

- **External Ratings bottom spacing aligned (`ApiDetailTabContents.kt`):**
  - **Issue:** `ApiDetailOverviewTab` used ad-hoc `Spacer(14.dp)` between cards. While 14 dp existed above External Ratings, the bottom gap before "Genres" or "Studios" was 0 dp, causing visual clamping.
  - **Fix:** Switched `ApiDetailOverviewTab` to use `Column(verticalArrangement = Arrangement.spacedBy(14.dp))`, fully aligning it with `EntryDetailOverviewTab`.
  - **Dynamic Card Spacing:** Conditional cards like `ApiMdbListRatingCard` cleanly yield spacing when empty or hidden without leaving phantom gaps.
  - **Audited Secondary Tabs:** Verified all other detail tabs (`StatsTab`, `ReviewsTab`, `CharactersTab`, `RelationsTab`, `RecommendationsTab`) for spacing consistency.

---

# Kitsugi Beta v2.4.322

## 🇹🇷 Türkçe

### 🛠️ Geliştirici Günlükleri Seçim Çökmesi & Açılış Sertleştirmesi

Bu sürüm, gönderilen çökme raporundaki **kesin kök nedeni** (`NoSuchElementException: Cannot find value for key 1`) düzeltir ve açılış zincirini olası çökme veya sistem sonlandırmalarına karşı tam izole eder. Ayrıntılı analiz: `KOK_NEDEN_RAPORU_GELISTIRICI_GUNLUKLERI_SECIM_COKMESI_2026-10-21.md` ve `PLAN_TASK_GELISTIRICI_GUNLUKLERI_SECIM_COKMESI_VE_ACILIS_SERTLESTIRMESI_2026-10-21.md`.

- **Geliştirici Günlükleri metin seçimi çökmesi giderildi (`DeveloperLogsDialog.kt`):**
  - **Hata:** `java.util.NoSuchElementException: Cannot find value for key N` (`SelectionManager.getSelectionLayout` -> `MultiWidgetSelectionDelegateKt.getDirectionById`).
  - **Kök Neden:** `SelectionContainer { LazyColumn { ... } }` deseni. `LazyColumn` satırları kaydırılınca kompozisyondan düşüyor, Compose seçim yöneticisi serbest bırakılan öğenin kimliğini ararken süreci düşürüyordu.
  - **Çözüm:** Seçim kapsamı her günlük satırının kendi içine alındı. Artık her `SelectionContainer` tek bir `Text` barındırıyor, çoklu seçim kaybolması ve çökme riski tamamen ortadan kalktı. Satır kopyalama ve seçme eksiksiz çalışmaya devam eder.
- **Çökme uyarısı artık her açılışta tekrar etmiyor (`MainActivity.kt`):**
  - "Beklenmedik Kapanma Tespit Edildi" diyaloğu kapatıldığında "okundu" işaretleniyor; eski raporun her uygulama açılışında ekrana gelmesi engellendi. Dosya tabanlı sessiz kapanma uyarılarına 7 gün tazelik sınırı getirildi.
- **En taze çökme raporu paylaşımı (`KitsugiCrashLogger.kt` & `KitsugiCrashActivity.kt`):**
  - `ensureCrashLogFile` artık her zaman `readCrashLog()` ile en güncel logu üretir; eski `crash_log.txt` arşivi ezilmez, paylaşılan rapor her zaman en taze çökme kanıtını içerir.
- **Açılış aşaması izleme mekanizması (`StartupStage.kt`):**
  - Başlangıç adımları (`filesDir/startup_stage.txt`) anlık takip edilir; ana ekran ilk kareyi çizince silinir. UI'dan önce süreç ölürse son çalışan init adımı rapora kaydedilir.
- **Açılış zinciri izolasyonu (`KitsugiApplication.kt` & `KitsugiSplashActivity.kt`):**
  - İndirme yöneticisi, izleme geçmişi, DNS, P2P motoru vb. tüm alt sistem init'leri `runCatching` ile korumaya alındı; bir sistemdeki hata tüm uygulamanın açılışını engellemez.
  - Açılış animasyonu ve prefetch çağrıları korumaya alındı.

---

## 🇬🇧 English

### 🛠️ Developer Logs Selection Crash & Startup Hardening

Resolves the exact root cause (`NoSuchElementException: Cannot find value for key 1`) from diagnostic crash logs and insulates the startup lifecycle against aborts. Detailed analysis in `KOK_NEDEN_RAPORU_GELISTIRICI_GUNLUKLERI_SECIM_COKMESI_2026-10-21.md` and `PLAN_TASK_GELISTIRICI_GUNLUKLERI_SECIM_COKMESI_VE_ACILIS_SERTLESTIRMESI_2026-10-21.md`.

- **Resolved Text Selection Crash in Developer Logs (`DeveloperLogsDialog.kt`):**
  - **Root Cause:** `SelectionContainer { LazyColumn { ... } }` pattern. When lazy items were scrolled out of view, Compose's `SelectionManager` failed to find evicted selectable IDs (`getDirectionById`), throwing `NoSuchElementException`.
  - **Fix:** Confined `SelectionContainer` into each individual log row. Long-press text selection and copying remain intact without layout leaks.
- **Persistent Crash Dialog Resolved (`MainActivity.kt`):**
  - Crash notices are marked as read on dismissal; stale dialogs will not repeatedly prompt on every cold start. Imposed a 7-day expiration on silent file-based notices.
- **Fresh Crash Log Export (`KitsugiCrashLogger.kt` & `KitsugiCrashActivity.kt`):**
  - `ensureCrashLogFile` now captures the freshest diagnostic dump (`readCrashLog()`) instead of serving stale historical archives.
- **Startup Stage Tracing (`StartupStage.kt`):**
  - Logs progressive initialization steps to `filesDir/startup_stage.txt`. Cleared on first frame render; if a pre-UI death occurs, the fatal stage is captured in the error report.
- **Isolated Initialization Chain (`KitsugiApplication.kt` & `KitsugiSplashActivity.kt`):**
  - Subsystems (Downloads, Watch History, DNS, P2P engine) are wrapped in `runCatching` so isolated startup exceptions cannot block app launch.
  - Splash prefetch and animations are safely guarded.

---

# Kitsugi Beta v2.4.321

## 🇹🇷 Türkçe

### 🗓️ Haftalık Yayın Takvimi: "İlk Açılışta Boş Geliyor" Kök Nedeni Düzeltildi

Takvim hangi kaynak seçili olursa olsun (AniList, MAL, TMDB, Kitsu, Shikimori, Simkl, Bangumi ve "Tümü") ilk açılışta boş görünüyor, geri çıkıp ikinci/üçüncü kez girildiğinde ise veri geliyordu. Sorun ağda değil, arayüz durum yönetiminde bir mantık hatasıydı. Ayrıntılı analiz: `KOK_NEDEN_RAPORU_TAKVIM_ILK_ACILIS_BOS_2026-10-21.md`.

- **Asıl neden — `remember` anahtarı yerinde değişen map:** `weekSchedule` bir `SnapshotStateMap` ve `clear()` + `putAll()` ile **yerinde** güncelleniyor. Map referansı hiç değişmediği için `remember(viewModel.weekSchedule, …)` anahtarı Compose tarafından "değişmedi" sayılıyordu; ilk açılışta (veri henüz yokken) hesaplanan **boş** harita önbellekte kalıyor, ağdan veri gelmesine rağmen ekran boş kalıyordu. Geri çıkıp tekrar girmek ekranı yeniden kurduğu için veri "ikinci açılışta" görünüyordu.
- **Çözüm:** `filteredScheduleMap` artık canlı map yerine içerik kopyası (`toMap()`) anahtarıyla hesaplanıyor. Bu hem snapshot okuması kaydeder (veri gelince recomposition) hem de içerik değiştiğinde `remember` önbelleğini geçersiz kılar. Gün sekmelerindeki rozetler ve başlıktaki toplam sayı da ilk açılışta doğru gelir.
- **Sessiz başarısızlık sona erdi:** AniList 429 cooldown'ı veya ağ hatası önceden "boş harita" olarak yutuluyor, ekran "Bu gün için yayın bulunamadı." diyordu. Artık yükleme başarısızsa **"Takvim yüklenemedi" + Yeniden Dene** durumu gösterilir; yanlış "veri yok" mesajı verilmez. Sonraki sayfa istekleri başarısız olursa eldeki kısmi veri korunur.
- **TMDB tarafı:** 8 isteğin hiçbiri başarılı olamazsa (API anahtarı/ağ) hata bildirilir; önceden takvim sessizce boş kalıyordu.
- **"Tümü" modu dayanıklılığı:** AniList veya TMDB'den biri hata verse bile diğer kaynağın verisiyle kısmi takvim gösterilir; yalnızca ikisi de boş + hatalıysa hata durumuna düşülür.
- **Kısa AniList cooldown'ları bekleniyor:** Keşfet ekranı kotayı doldurduğunda takvim açılışında istek hiç gönderilmeden `null` dönüyordu. 8 saniyeye kadar olan cooldown'lar beklenip istek gerçekten gönderilir (uzun cooldown'da fail-fast korunur).
- **Bayat veri:** Başarılı bir yükleme 10 dakika taze sayılır; süre dolduğunda ekran açılışında arka planda sessizce tazelenir (eski veri ekranda kalır, boş ekran görünmez) → gün/hafta değişimlerinde bayat takvim gösterilmez.

---

## 🇬🇧 English

### 🗓️ Weekly Airing Calendar: "Empty on First Open" Root Cause Fixed

The calendar appeared empty on the first open for every source (AniList, MAL, TMDB, Kitsu, Shikimori, Simkl, Bangumi and "All"), yet data showed up after going back and re-entering the screen. The cause was not the network but a state-management logic bug. Full analysis: `KOK_NEDEN_RAPORU_TAKVIM_ILK_ACILIS_BOS_2026-10-21.md`.

- **Root cause — `remember` keyed on an in-place mutated map:** `weekSchedule` is a `SnapshotStateMap` updated in place via `clear()` + `putAll()`. Because the map reference never changed, Compose treated `remember(viewModel.weekSchedule, …)` as unchanged: the **empty** map computed on first open (before data arrived) stayed cached forever, so the screen remained empty even after the data landed. Leaving and re-entering rebuilt the composition, which is why the data appeared on the second open.
- **Fix:** `filteredScheduleMap` is now derived from a content copy (`toMap()`) used as the `remember` key. That registers a snapshot read (so new data triggers recomposition) and invalidates the cache when the content changes. Day-tab badges and the header total are correct from the very first open.
- **No more silent failures:** AniList 429 cooldowns or network errors used to be swallowed into an empty map and surfaced as "No airings today." Failures now show **"Calendar could not be loaded" + Retry**; partial data is preserved when only later pages fail.
- **TMDB:** If none of the 8 requests succeed (API key/network), an error is reported instead of a silently empty calendar.
- **"All" mode resilience:** If either AniList or TMDB fails, the other source still produces a partial calendar; the error state is used only when both are empty and failing.
- **Short AniList cooldowns are awaited:** When the Explore screen exhausted the quota, the calendar request returned `null` without ever hitting the network. Cooldowns up to 8 seconds are now awaited before sending; longer cooldowns keep the fail-fast behaviour.
- **Stale data:** A successful load counts as fresh for 10 minutes; afterwards the screen silently refreshes in the background (previous data stays on screen, no empty flash), so day/week rollovers no longer show a stale calendar.

---

# Kitsugi Beta v2.4.320

## 🇹🇷 Türkçe

### 🎬 Aniyomi Paritesi: Gelişmiş Altyazı Ayarları & Özelleştirme Paneli

Aniyomi video oynatıcı altyazı ayarları mimarisi eksiksiz olarak Kitsugi'ye uyarlandı. Eski 2 kartlı basit yapı yerine Aniyomi ile birebir 3 kartlı (Topografi, Renkler, Diğer) zengin pager arayüzü ve gelişmiş MPV/Media3 altyazı motoru entegrasyonu getirildi. Ayrıntılı plan: `PLAN_TASK_ANIYOMI_OYNATICI_PARITESI_2026-10-21.md`.

- **1. Topografi Kartı (`TypographyCard`):**
  - **Kalın / İtalik geçişleri:** Tek dokunuşla bağımsız bold ve italic açma/kapama (`IconToggleButton`).
  - **Metin Hizalama:** Sol, Orta, Sağ ve Otomatik hizalama seçenekleri (`FormatAlign` ikonları).
  - **Yazı Tipi Seçici:** 12 temel font (Sans Serif, Serif, Monospace, Arial, Noto, Roboto vb.) + `filesDir/fonts` dizininden dinamik yerel TTF tarama ve yükleme desteği.
  - **Yazı Tipi Boyutu:** 1..100 hassas slider kontrolü (varsayılan: 55).
  - **Sınır Şekli:** Dış hat ve gölge (`outline-and-shadow`), Opak kutu (`opaque-box`), Arka plan kutusu (`background-box`).
  - **Kenar Boyutu & Gölge Uzaklığı:** 0..10 arası hassas slider ayarları ve kartı sıfırlama seçeneği.
- **2. Renkler Kartı (`ColorsCard`):**
  - **Metin, Kenar ve Arka Plan Seçici:** Her öğe için bağımsız renk katmanı.
  - **RGBA Renk Kanalları:** Kırmızı, Yeşil, Mavi ve Alfa (saydamlık) için 0..255 arası renkli slider'lar.
  - **Canlı Önizleme:** Anlık renk önizleme kutusu ve MPV'ye canlı `#AARRGGBB` hex formatında enjeksiyon (`sub-color`, `sub-border-color`, `sub-back-color`).
- **3. Diğer Kartı (`MiscellaneousCard`):**
  - **ASS/SSA Geçersiz Kılma:** `sub-ass-override` (force/scale) toggle desteği.
  - **Ölçek (sub-scale):** 0.2..5.0 aralığında genel altyazı ölçeklendirme.
  - **Konum (sub-pos):** 0..150 dikey yerleşim konumu.
  - **Dikey İnce Ayar:** -200..200 piksel dikey ofset ve sadece tercih edilen dil filtresi.
- **Adaptif Düzen:** Dikey ekranda akıcı `HorizontalPager` ve 3 nokta göstergesi, yatay modda dikey kaydırılabilir 3 kartlı sütun.
- **Media3 & MPV Motor Senkronizasyonu:**
  - MPV tarafında `sub-font`, `sub-font-size`, `sub-bold/italic`, `sub-color/back/border-color`, `sub-border-size/style`, `sub-pos`, `sub-justify` doğrudan stil üzerinden uygulanır.
  - Media3 tarafında `Typeface` kombinasyonu, `CaptionStyleCompat` ile `EDGE_TYPE` kenarlık eşlemesi, SP font hesabı ve `bottomPaddingFraction` konumlandırması tamamlandı.
  - `SettingsDataStore` ile 12 yeni altyazı parametresi yerel olarak kalıcı hale getirildi.

### ⚙️ MPV Yapılandırma & Kullanıcı Dosyaları Desteği (mpv.conf / input.conf)

- **Başlatma Seçenekleri Paritesi:** Aniyomi MPV init profili uygulandı (`profile fast`, `ytdl no`, `keep-open yes`, `input-default-bindings`, `vd-lavc-film-grain cpu`, `msg-level warn`, `screenshot-directory Pictures`).
- **Özel Dosya ve String Desteği:** `SettingsDataStore` üzerinden kullanıcı tanımlı `mpv.conf` ve `input.conf` yapılandırmalarının doğrudan MPV oturumuna yüklenmesi sağlandı.

---

## 🇬🇧 English

### 🎬 Aniyomi Parity: Comprehensive Subtitle Customization Panel

Brings 100% subtitle setting parity with the Aniyomi video player into Kitsugi. Replaces the legacy 2-card popup with Aniyomi's 3-card tabbed pager architecture (Typography, Colors, Miscellaneous) backed by rich MPV and Media3 engine pipelines. Detailed plan in `PLAN_TASK_ANIYOMI_OYNATICI_PARITESI_2026-10-21.md`.

- **1. Typography Card (`TypographyCard`):**
  - **Bold / Italic Toggles:** Independent font weight and slant controls.
  - **Text Justification:** Left, Center, Right, and Auto alignment options.
  - **Font Selector:** 12 curated system fonts with automatic dynamic scanning of custom TTF fonts placed in `filesDir/fonts`.
  - **Font Size:** 1..100 continuous slider (default 55).
  - **Border / Outline Styles:** Outline-and-shadow, opaque-box, and background-box presets.
  - **Border Size & Shadow Offset:** 0..10 sliders with instant reset.
- **2. Colors Card (`ColorsCard`):**
  - **Multi-layer Color Targeting:** Target text, border, or background box.
  - **RGBA Channel Sliders:** Precise Red, Green, Blue, and Alpha (0..255) controls.
  - **Live Preview & MPV Injection:** Real-time color sample box mapped directly to MPV hex formatting (`sub-color`, `sub-border-color`, `sub-back-color`).
- **3. Miscellaneous Card (`MiscellaneousCard`):**
  - **ASS/SSA Override:** Toggle for strict style overriding (`sub-ass-override`).
  - **Scale (sub-scale):** 0.2..5.0 font scale slider.
  - **Position (sub-pos):** 0..150 window position slider.
  - **Vertical Offset:** Fine-grained -200..200 pixel adjustment and preferred language filter.
- **Responsive Layout:** `HorizontalPager` with 3-dot page indicators in portrait; vertical scrolling list in landscape.
- **Engine Parity:** Fully synced across MPV and Media3 engines (`Typeface` bold+italic, `CaptionStyleCompat` edge styles, SP-based size scaling, and persistent `SettingsDataStore` storage).

### ⚙️ MPV Init & Custom Config Support (mpv.conf / input.conf)

- **Initialization Parity:** Adopted Aniyomi MPV init parameters (`profile fast`, `ytdl no`, `keep-open yes`, `input-default-bindings`, `vd-lavc-film-grain cpu`, `msg-level warn`).
- **User Files Support:** Support for custom `mpv.conf` and `input.conf` files and string properties loaded directly via `SettingsDataStore`.

---

# Kitsugi Beta v2.4.319

## 🇹🇷 Türkçe

### 🎬 Video Kaynak Ekranı: Görselli Kartlar, Akan Üst Bar & Oynatma Başlatma Düzeltmeleri

Video kaynak seçim ekranı (`KitsugiStreamScreen`) görselli kartlar, her zaman görünür ayrıntılar, yumuşak kayan üst bar ve oynatmanın sessizce çökmesini önleyen 5 katmanlı sertleştirme ile yeniden yapılandırıldı. Ayrıntılı analiz: `PLAN_TASK_STREAM_GORSEL_KART_AKAN_UST_BAR_2026-10-21.md` ve `docs/STREAM_SELECTION_UX.md`.

- **Görselli kartlar (Dikey + Yatay aynı düzen):**
  - Her kartta solda sabit 74×104 dp (2:3) kapak görseli yer alır (`thumbnailUrl` -> poster -> gradyan + film ikonu; boş kutu kalmaz).
  - Yetişkin içerik bulanıklığı (`KitsugiNsfwImage`) tam uyumlu korunur.
- **Ayrıntılar her zaman görünür:** Kalite, ses/altyazı türü, ses ve altyazı dilleri, dosya boyutu, Debrid/P2P durumu ve kaynak host'u tek bakışta görünür; v2.4.318'deki "Ayrıntılar" akordiyonu kaldırılarak tüm bilgilere doğrudan erişim sağlandı.
- **Yumuşak üst bar:** Başlık barı `NestedScrollConnection` + `graphicsLayer` ile parmak hareketine birebir bağlı olarak yumuşakça gizlenip geri gelir; liste zıplamaları önlendi. Yatayda sol ray da üst bar ile uyumlu akar.
- **Daha az dokunuş:** Kartın herhangi bir yerine dokunmak videoyu doğrudan başlatır; sağda 46 dp yuvarlak oynat ve altındaki indir butonu netleştirildi.
- **"İzle'ye basınca video açılmıyor / ana sayfaya atıyor" düzeltmeleri:**
  - **Binder taşması (TransactionTooLargeException):** Oynatıcıya giden Intent verisi 96 KB'a sınırlandı; taşma durumunda yedeksiz otomatik yeniden deneme eklendi.
  - **Oynatıcı kurulumu korundu:** `KitsugiFullscreenPlayerActivity.onCreate` try/catch ile sarıldı; veri hatası olursa süreç ölmez, kullanıcıya hata bildirilip düzgün biçimde kaynak ekranına dönülür.
  - **İzleme geçmişi hatasız:** `WatchHistoryManager.record` çağrısı `runCatching` içine alındı; geçmiş yazımı çökme yaratmaz.
  - **Sessiz hata sonlandırıldı:** `startWithStreamUrls` artık `Boolean` döner; başlatılamazsa stream ekranında "Oynatıcı açılamadı" diyaloğu gösterilir.
  - **Harici oynatıcı koruması:** Harici oynatıcı açılamadığında tam ekran oynatıcı erkenden kapanmaz, kullanıcıya toast ile bildirilir.
- **Yerelleştirme:** Eksik `values-en/player_parity_strings.xml` eklenerek string parity denetimi temizlendi.

### 🎬 Görüntü Modu ("Doldur" / "Yakınlaştır") Alt Yazıyı Artık Etkilemiyor

"Doldur" veya "Yakınlaştır" seçildiğinde harici altyazıların video ile birlikte esnemesi, büyümesi ve ekranın altına taşması sorunu kökten çözüldü. Alt yazı katmanı video ölçeklendirmesinden tamamen ayrıldı. Ayrıntılı analiz: `COZUM_RAPORU_ALT_YAZI_GORUNTU_MODU_2026-10-21.md`.

- **MPV motoru (varsayılan):**
  - Görüntü modu artık `View.scaleX/scaleY` ile değil, doğrudan mpv'nin `video-scale-x/y` özellikleriyle uygulanıyor; böylece yalnızca **video** katmanı esner/zoomlanır, altyazı katmanına dokunulmaz.
  - Video pencereyi taşıdığında (`Doldur`, `Yakınlaştır`, `16:9`, `4:3`) altyazı çizim alanı pencereye sabitleniyor (`sub-use-margins` / `sub-ass-force-margins` kapatılır). Bu sayede libass'ın negatif kenar boşluğu hesaplayıp altyazıyı ekran dışına taşırması engellenir.
  - Altyazı yazı boyutu pencereye göre ölçekleniyor (`sub-scale-by-window`), esnetilen görüntüde yazı boyutu bozulmuyor.
  - Video en-boy oranı artık kaynak videodan (`video-params/*`) okunuyor; çıkış katmanından okuma "Doldur" modunda ölçeğin kendi kendini büyütmesine yol açabiliyordu.
  - `video-aspect-override` için geçersiz olan `yes` değeri kaldırıldı.
- **Media3 motoru:**
  - Görüntü modu her değiştiğinde altyazı görünümünün içerik çerçevesinden (`exo_content_frame`) ayrık kaldığı doğrulanıp konumu yeniden sabitleniyor.
- **Sonuç:** Altyazı konumu, genişliği ve yazı boyutu hangi görüntü modu seçilirse seçilsin sabit kalır; alt satırlar ekran dışına taşmaz.

### 🔵 Wiki Okuyucuda Mavi (Bağlantı) Metinlerin Otomatik Çevirisi

Wiki açılır sayfalarında otomatik çevirinin **mavi/bağlantı metinlerini atlaması** yüzünden oluşan cümle bölünmeleri ve yarı Türkçe/İngilizce metin sorunları tek kökten çözüldü. Ayrıntılı analiz: `MAVI_BAGLANTI_OTOMATIK_CEVIRI_RAPORU_2026-10-21.md`.

- **Mavi metin artık çevriliyor, konumu birebir korunuyor:** Her bağlantı etiketi çeviriye gönderilmeden önce `⟦L0⟧Characters⟦/0⟧` biçiminde bir yuvaya alınır. Google cümleyi **tek parça** olarak çevirir; etiket tam olarak eski yerinde, cümlenin içinde kalır.
- **Dokunma hedefi (URL) hiç değişmiyor ve hiç gönderilmiyor:** Çeviri yalnızca `label` alanına dokunur; `url` alanı blok verisinde olduğu gibi kalır.
- **Kelimelerin birbirine yapışması çözüldü:** Cümle bağlantıların arasına parçalanıp ayrı ayrı çevrilmediği için "678 articlesve" gibi yapışmalar ortadan kalktı.
- **Çevrilmiş etiket metinle birlikte saklanıyor (`…:l<indeks>` çocuk anahtarı):** Çeviri haritası önbellekten/akıştan geri geldiğinde mavi alan tıklanabilirliğini asla kaybetmez.
- **Jeton bozulursa güvenli yedek:** Sağlayıcı bir yuvayı düşürür veya bozarsa metin eski güvenli yoldan (parça parça, etiketler orijinal) çevrilir; yarım jeton veya kaymış bağlantı ekranda görünmez.
- **Hız ve performans:** Eskiden her bağlantı arası ayrı istek atılıyordu (bir paragrafta 5-8 istek). Artık cümle ve tüm etiketler **tek istekte** gidiyor; 40 tekrarlı bir bağlantı listesi bile tek istek harcıyor.
- **Cümle görünümlü kart başlıkları:** Ana sayfa kartlarındaki cümle niteliğindeki başlıklar çevrilirken `href` korunur.
- **Görsel altı yazıları (figcaption):** Açıklamalardaki mavi bağlantılar da aynı yuvalı akıştan geçer.

### ⭕ Yüklenme Animasyonu Artık Her Yerde Tam Yuvarlak

Video yüklenirken ekranın ortasında görünen yüklenme animasyonunun etrafında **turuncu kare çerçeve** ve içinde **ikinci bir kare** görünmesi sorunu kökten çözüldü. Ayrıntılı analiz: `YUKLENME_ANIMASYONU_YUVARLAK_FIX_RAPORU_2026-10-21.md`.

- **Kare çizim tamamen kaldırıldı:** Gövde artık `drawRect` değil tek bir `drawCircle` ile çiziliyor.
- **Ekstra çember / kenarlık kaldırıldı:** `Modifier.border(...)` çağrısı silindi; animasyonun etrafında kare çerçeve veya yapay halka oluşmaz.
- **Çift kırpma güvencesi:** Kutu, genişlik/yükseklik farklı olsa dahi her zaman tam daire kesen özel `PlasmaOrbShape` ile kırpılıyor; ovalleşme veya kare sızması engellendi.
- **Doğru boyutlama:** İç `Canvas` artık ölçülen kutu boyutuna göre çiziyor; dışarıdan verilen `Modifier.size(80.dp)` ile küre tam olarak 80 dp çizilir.
- **Zenginleştirilmiş küre hacmi:** 3 dönen plazma odağı, beyaz-sıcak akkor merkez, cam highlight ve kenar vinyeti ile tüm katmanlar daire içinde kalır; dışarıya ışıma taşmaz.
- **Tüm uygulama genelinde aktif:** Oynatıcı ortası, tam ekran yüklenme, arama, listeler, sheet'ler, giriş pencereleri, pull-to-refresh, TV ve manga ekranlarının tamamında kusursuz dairesel animasyon.

### 🗑️ İndirmeler: Altyazı Silme İyileştirmesi (Hızlı Silme & Anında UI Tepkisi)

İndirilen altyazıların silinmesindeki hantal animasyonlar kaldırıldı ve art arda hızlı silme durumunda dosyanın ekranda asılı kalması bug'ı düzeltildi. Ayrıntılı plan: `PLAN_TASK_ALTYAZI_SILME_ANIMASYON_VE_HIZLI_SILME_FIX_2026-10-21.md`.

- **Animasyonsuz, tek tıkla silme (`KitsugiAnimatedDeleteButton`):** İki aşamalı genişleyen kapsül onayı, renk geçişi ve 3.5 saniye bekleme kaldırıldı; yerini doğrudan tek dokunuşla silen kompakt kırmızı çöp kutusu ikonuna bıraktı.
- **Anında UI reaktivitesi & İyimser Güncelleme (`DownloadsScreen`):** Silinen altyazı listeden saniyesinde filtrelenerek görsel olarak anında kaldırılır (`mutableStateOf` + `LaunchedEffect`).
- **Geri alma (Rollback) güvencesi:** Dosya sistemi silme işlemi herhangi bir sebeple başarısız olursa öğe otomatik olarak listeye geri eklenir.

### 🧪 Birim Testleri & Altyapı
- `WikiTranslationProtectionTest`: Mavi etiketlerin yerinde çevrilmesi, URL'lerin hiç değişmemesi, jeton bozulduğunda güvenli yedeğe düşülmesi, etiket anahtarlarının metinden önce akması, 40 tekrarlı bağlantıda tek istek, cümle görünümlü kart başlığının çevrilip kısa sayfa adının çevrilmemesi doğrulandı (34 test tamamı başarılı).
- `DeepLinkTest` & `FileLoggingTreeTest`: Derin bağlantı şema ayrıştırması ve asenkron log flush desteği doğrulandı.

---

## 🇬🇧 English

### 🎬 Stream Selection Screen: Thumbnail Cards, Smooth Top Bar & Playback Crash Hardening

Redesigned the stream selection interface (`KitsugiStreamScreen`) with permanent 74×104 dp visual cards, instantly readable audio/subtitle details, a smooth scrolling header, and multi-layered crash hardening against player launch failures. Detailed specifications in `PLAN_TASK_STREAM_GORSEL_KART_AKAN_UST_BAR_2026-10-21.md` and `docs/STREAM_SELECTION_UX.md`.

- **Visual Thumbnail Cards (Unified portrait & landscape layout):** Fixed 74×104 dp (2:3) cover image on the left (`thumbnailUrl` -> media poster -> accent gradient + film icon fallback; no blank boxes). Adult content blur (`KitsugiNsfwImage`) fully preserved.
- **All Details Always Visible:** Video quality, audio/subtitle tracks, languages, file size, Debrid/P2P badges, and host source are immediately visible; removed the cumbersome accordion from v2.4.318.
- **Smooth Translating Header:** Top bar scrolls smoothly using `NestedScrollConnection` and `graphicsLayer` (translation + alpha), eliminating sticky header list jumping. Left control rail smoothly syncs in landscape.
- **Fewer Taps:** Tapping anywhere on the stream card triggers immediate playback; dedicated 46 dp play circle and download buttons provided.
- **"Watch opens nothing / kicks to home screen" hardening:**
  - **Binder Overflow Protection:** Stream intent extra payload capped at 96 KB with automatic fallback retry without extras on `TransactionTooLargeException`.
  - **Guarded Player Bootstrap:** `KitsugiFullscreenPlayerActivity.onCreate` wrapped in robust try/catch blocks; fatal errors display informative UI and return cleanly to the stream picker instead of killing the process.
  - **Safe History Logging:** `WatchHistoryManager.record` wrapped in `runCatching` so disk write errors cannot abort playback.
  - **Failure Transparency:** `startWithStreamUrls` returns `Boolean`, presenting a clear error dialog if player intent cannot be resolved.
  - **External Player Safety:** Fullscreen player does not prematurely dismiss when external player launch fails.
- **Localization:** Added missing `values-en/player_parity_strings.xml` for full string parity.

### 🎬 Aspect Modes ("Fill" / "Zoom") No Longer Distort Subtitles

Resolves the issue where selecting "Fill" or "Zoom" stretched, oversized, and pushed subtitles off-screen. Subtitle rendering has been fully decoupled from video transformation layers. Detailed report in `COZUM_RAPORU_ALT_YAZI_GORUNTU_MODU_2026-10-21.md`.

- **MPV Engine (default):**
  - Aspect modes are now applied directly via MPV's `video-scale-x/y` properties instead of Android `View.scaleX/scaleY`, transforming only the video surface while leaving subtitles untouched.
  - Subtitle bounding is pinned to the window (`sub-use-margins` / `sub-ass-force-margins = no`) whenever the video overflows the display bounds (`Fill`, `Zoom`, `16:9`, `4:3`), preventing libass from calculating negative margins and clipping subtitles off the bottom edge.
  - Subtitle font size is anchored to the window (`sub-scale-by-window`), preventing stretched typography.
  - Video aspect ratio is read directly from container properties (`video-params/*`) rather than output dimensions to avoid recursive magnification.
  - Removed invalid `"yes"` argument from `video-aspect-override`.
- **Media3 Engine:**
  - Re-anchors and refreshes subtitle layout upon aspect mode changes to ensure subtitle views remain decoupled from the video content frame (`exo_content_frame`).
- **Result:** Subtitle placement, scale, and line wrapping remain invariant regardless of selected aspect mode.

### 🔵 Automatic Translation of Blue (Link) Text in the Wiki Reader

Fixes three separate breakages caused by auto-translation skipping **blue / linked text** inside wiki reader pages. Detailed report in `MAVI_BAGLANTI_OTOMATIK_CEVIRI_RAPORU_2026-10-21.md`.

- **Link labels are now translated in place:** Each label is wrapped in a `⟦L0⟧Characters⟦/0⟧` slot before being sent, so the provider translates the whole sentence at once and the label stays exactly where it was.
- **Tap targets (URLs) never change and are never sent:** Only `label` is touched; `url` stays in the block data, so tapping a blue word opens exactly the same page as before.
- **Resolved word agglutination:** Sentences are translated holistically rather than fragmented around links, avoiding awkward spacing and gluing ("678 articlesand").
- **Translated labels are persisted next to the text (`…:l<index>` child keys):** Text and link list always agree after a cache/stream round trip; the blue area never loses its click target.
- **Safe fallback when slots are damaged:** If the provider drops a slot marker, the block falls back to the old per-segment path with original labels.
- **Fewer requests, not more:** One request per block (sentence + labels) instead of one per segment; even a paragraph with 40 repeated links costs a single request.
- **Sentence-like linked card titles are translated:** Fandom main-page card sentences are translated while `href` is preserved; short page titles stay untouched.
- **Figure captions (figcaption):** Blue link text in image captions is translated in place through the same slot pipeline.

### ⭕ Loading Animation Is Now Perfectly Round Everywhere

Fixes the issue where buffering/loading orbs rendered an outer **orange square frame** and an inner **secondary square** instead of a seamless circle. Detailed report in `YUKLENME_ANIMASYONU_YUVARLAK_FIX_RAPORU_2026-10-21.md`.

- **Rectangles removed:** The body is rendered with a single `drawCircle`, eliminating all `drawRect` logic.
- **Extra border removed:** Removed `Modifier.border(...)`; no square outlines or artificial secondary rings.
- **Guaranteed circle clipping:** Enforced `PlasmaOrbShape` ensuring a flawless circle is clipped even on rectangular containers.
- **Accurate box sizing:** Inner canvas binds directly to measured constraints, so `Modifier.size(80.dp)` renders a true 80 dp orb.
- **Volumetric plasma look:** 3 orbiting plasma lobes, a white-hot core, glass highlight and subtle vignette keep all radiance strictly inside the orb boundary.
- **System-wide coverage:** Consistent across video player, fullscreen loading, lists, modal sheets, login dialogs, pull-to-refresh, TV, and manga views.

### 🗑️ Downloads: Subtitle Deletion Overhaul (Instant Deletion & Reactive UI)

Eliminated sluggish animations during subtitle removal and fixed the race condition where rapidly deleted subtitles remained visible on screen. Detailed plan: `PLAN_TASK_ALTYAZI_SILME_ANIMASYON_VE_HIZLI_SILME_FIX_2026-10-21.md`.

- **Direct Single-Tap Deletion (`KitsugiAnimatedDeleteButton`):** Removed the expanding capsule animation, color shifts, and 3.5-second timeout in favor of an instant single-tap icon button.
- **Optimistic UI Updates (`DownloadsScreen`):** Deleted subtitles disappear instantly from the view (`mutableStateOf` + `LaunchedEffect`), followed by a background disk scan for sync.
- **Safe Rollback:** Automatically restores the item in the list if underlying file deletion fails.

### 🧪 Unit Tests & Infrastructure
- `WikiTranslationProtectionTest`: Verified in-place label translation, invariant URLs, slot recovery fallback, pre-flight label key emissions, single request on 40 links, and sentence vs title card discrimination (all 34 tests passing).
- `DeepLinkTest` & `FileLoggingTreeTest`: Verified deep-link URI scheme resolution and thread-safe file logging flush.

---

# Kitsugi Beta v2.4.318

## 🇹🇷 Türkçe

### 🎬 Kompakt & Adaptif Video Kaynak (Stream) Seçim Ekranı

Bu sürüm, çok eklenti kullanan kullanıcılar için video kaynak seçim ekranını (`KitsugiStreamScreen`) daha kompakt, aranabilir, tema uyumlu ve dikey/yatay kullanıma tam adaptif hale getirir. Ayrıntılı tasarım ve test notları: `docs/STREAM_SELECTION_UX.md`.

- **Dikey Kullanım Optimizasyonu:**
  - Aynı başlığı ve görseli tekrar eden büyük poster/başlık alanı kaldırıldı; sonuç alanı doğrudan görünür hale getirildi.
  - Eklenti gruplarına başlık ve canlı sonuç sayacı eklendi.
- **Yatay Kullanım (Landscape) Devrimi:**
  - Büyük poster alanı yerine genişliği sınırlandırılmış (240–300 dp) ve bağımsız kaydırılabilir bir kontrol paneli (sol ray) getirildi.
  - Sol kontrol paneli ile sağdaki video listesi birbirinden tamamen bağımsız olarak kaydırılabiliyor; kontroller ekran döndürüldüğünde dahi erişilebilir kalıyor.
- **Kompakt ve Sade Kaynak Satırları (`StreamCard`):**
  - Kartlardaki tekrarlayan posterler kaldırıldı; video adı, eklenti, kalite ve dil bilgisi en öne alındı.
  - Dosya bilgisi, ses/altyazı dilleri, dosya boyutu ve torrent bilgileri dokunulduğunda akıcı bir şekilde açılan ayrıntılar paneline (`AnimatedVisibility`) taşındı; ayrıntılara dokunmanın yanlışlıkla video başlatması engellendi.
  - Oynat ve indir eylemleri için standart 48 dp dokunma hedefleri ayrıldı.
- **Aranabilir Kaynak Seçim Paneli (`StreamScreenContent`):**
  - Eklenti şeridi, arama yapılabilen modal bir kaynak paneliyle değiştirildi.
  - Kaynak sayısı, sonuç adedi, yüklenme durumu, doğrulama ve hata durumları panelde anlık gösteriliyor.
- **Gelişmiş Sıralama & 4K Filtresi:**
  - Eklenti içinde kaliteleri yüksekten düşüğe sıralama desteği eklendi (bilinmeyen kaliteler en sona yerleştirilir).
  - 4K ve 1080p+ filtreleri netleştirildi.
- **Durum Koruma (`rememberSaveable`):**
  - Arama sorgusu, seçili etiket, sıralama tercihi ve seçilen eklenti filtresi ekran döndürüldüğünde (`orientation change`) eksiksiz olarak korunur.

---

## 🇬🇧 English

### 🎬 Compact & Adaptive Stream Selection Screen

This release delivers an overhauled, responsive, and compact stream selection experience (`KitsugiStreamScreen`) optimized for large add-on catalogs and seamless portrait/landscape adaptability. Detailed specifications in `docs/STREAM_SELECTION_UX.md`.

- **Portrait Layout Optimization:**
  - Eliminated redundant media poster and title headers to prioritize immediate stream result visibility.
  - Added grouped source headers with dynamic stream counts.
- **Landscape Control Rail:**
  - Replaced the full-width poster block with an independently scrollable, bounded control rail (240–300 dp) on the left.
  - Left control panel and right video results list scroll independently, keeping filter controls reachable across wide and ultra-wide screens.
- **Compact Stream Rows (`StreamCard`):**
  - Removed repetitive posters in favor of dense information rows highlighting video title, add-on source, quality badge, and language.
  - Moved secondary details (audio/subtitle tracks, file size, torrent cache state) into an expandable details section; tapping details will not accidentally trigger playback.
  - Provided dedicated 48 dp touch targets for play and download actions.
- **Searchable Source Picker (`StreamScreenContent`):**
  - Replaced horizontal chips with a searchable bottom sheet source picker.
  - Real-time visibility into source status: video count, loading indicators, error notices, and verification requirements.
- **Quality Sorting & 4K Filter:**
  - Introduced toggle for descending quality ordering within each add-on group, positioning unknown qualities at the bottom.
  - Clarified 4K and 1080p+ tag filtering.
- **State Preservation (`rememberSaveable`):**
  - Active search query, quality tags, sorting mode, and active add-on filter survive orientation changes and configuration reloads.

---

# Kitsugi Beta v2.4.317

## 🇹🇷 Türkçe

### 🏷️ Bangumi "Listem" Başlık Dili Düzeltmesi (İngilizce/Romaji)

Bu sürüm, başlık dili **İngilizce** seçili olmasına rağmen Bangumi listesinin yeni veya eski fark etmeksizin Japonca/Çince adlarla görünmesi sorununu 3 kök neden üzerinden tamamen çözer. Ayrıntılı analiz: `KOK_NEDEN_RAPORU_BANGUMI_LISTEM_BASLIK_DILI_INGILIZCEYE_UYMUYOR_2026-10-20.md`.

- **Aktarım Sonrası Anında Tamamlama & Dil Değişim Tetikleyicisi (`AuthViewModel`, `MyListScreen`, `MyListViewModel`):**
  - Bangumi içe aktarımı (ve çapraz senkron doğrulama aktarımı) biter bitmez Latin/İngilizce başlık tamamlama taraması otomatik olarak başlatılır (`AuthViewModel`).
  - Önceden tarama yalnızca `MyListViewModel` ilk oluşturulduğunda çalışıyordu; ekran açıkken aktarılan kayıtlar (ör. 717 kayıt) uygulama kapatılıp açılana kadar taranamıyordu.
  - Başlık dili ayarı değiştirildiğinde de tarama otomatik olarak yeniden tetiklenir (`MyListScreen`, `MyListViewModel`).
- **Kuyruk Tıkanması Çözüldü & Kademeli Tamamlama Motoru (`BangumiLibraryTitleHydrator`):**
  - Tamamlama motoru iki kademeli yapıya geçirildi: önce gösterimi Japonca/Çinceye düşüren "Latin eksik" kayıtlar, ardından (yalnızca İngilizce seçiliyse) ayrı İngilizce ad zenginleştirmesi.
  - İşlem 60'lık dilimler halinde listenin **sonuna kadar** (960 satır kapasite) kesintisiz ilerler; listenin başındaki "yalnızca romaji bulunabilen" kayıtların erken çıkışla tüm kuyruğu tıkaması engellendi.
  - Ağsız bir yerel önbellek geçişi (`cacheOnly`) çalışır; başlıklar ekranda aşamalı ve akıcı bir şekilde İngilizceye döner.
- **Çözülmüş Başlıkların CJK ile Ezilmesi Engellendi (`MediaEntryRepository` → `preserveResolvedTitles`):**
  - `smartImport` güncelleme akışına başlık koruma kuralı eklendi. Bangumi koleksiyon ucu yalnızca özgün adı döndürdüğü için, her yeniden senkron tamamlanmış İngilizce/romaji adları Japonca/Çince adla eziyordu; artık başlıklarda yalnızca yükseltmeye izin verilir ve özgün ad (`titleJapanese`) korunur.
- **Aktarım Anında Kalıcı Önbellek Kullanımı (`BangumiImportManager`):**
  - İçe aktarılan her Bangumi kaydı, daha önce çözülmüş Latin adları tutan kalıcı önbellekle anında zenginleştirilir; bilinen yapımlar ilk çizimden itibaren doğru dilde gelir.
- **Gösterim Yedeği Düzeltmesi (`PreferenceHelpers`):**
  - İngilizce seçiliyken romaji yedeği gösteriliyorsa önbellekteki ayrı İngilizce ad öncelik kazanır.

### 🐞 Oynatıcı Panelinde Null-Safety Güçlendirmesi (`DelayCard`)
- `DelayCard` içindeki ses/altyazı gecikme ayarında kalan potansiyel güvensiz `!!` referansları ve döngü koşulu güvenli yerel sabitlere (`startedAt`, `direction`) taşınarak olası NPE riskleri tamamen temizlendi.

### 🧪 Birim Testleri
- `BangumiLibraryTitleHydratorTest`: Kademe önceliği, dile göre tamamlama tanımı, büyük kütüphanede kırpma olmaması ve yer tutucu başlıklar doğrulandı.
- `MediaEntryRepositoryTitlePreservationTest`: Yeniden aktarımda çözülmüş başlıkların korunması doğrulandı.
- `BangumiImportTitlesTest`: Aktarım anında önbellek başlıklarının atanması doğrulandı.
- `BangumiDisplayTitleLanguageTest`: İngilizce/Romaji/Ana dil gösterim sözleşmesi doğrulandı.

---

## 🇬🇧 English

### 🏷️ Bangumi "My List" Title Language Fix (English / Romaji)

This release addresses the issue where titles in the Bangumi list were displayed in Japanese or Chinese regardless of the preferred title language being set to **English**. Detailed root-cause breakdown in `KOK_NEDEN_RAPORU_BANGUMI_LISTEM_BASLIK_DILI_INGILIZCEYE_UYMUYOR_2026-10-20.md`.

- **Immediate Post-Import Hydration & Preference Change Triggers (`AuthViewModel`, `MyListScreen`, `MyListViewModel`):**
  - Triggered Latin/English title hydration immediately upon Bangumi import completion (and cross-sync verification), eliminating the need to restart the app to see translated titles.
  - Added reactive re-hydration triggers whenever the preferred title language is changed in settings.
- **Unblocked Hydration Pipeline & Tiered Progress (`BangumiLibraryTitleHydrator`):**
  - Structured hydration into two stages: primary Latin fallback resolution followed by English-specific title enrichment.
  - Paginates through 60-item chunks across the entire library (up to 960 rows), preventing romaji-only entries from exhausting the budget and prematurely terminating the hydrator.
  - Runs a fast local cache-only scan first, ensuring progressive, instant UI updates.
- **Preserve Resolved Titles Against CJK Overwrites (`MediaEntryRepository`):**
  - Implemented `preserveResolvedTitles` in `smartImport`. Bangumi collection endpoints return only native names, which previously overwrote resolved English/romaji titles on each sync; updates now retain resolved translations and native variants.
- **Import-Time Persistent Cache Enrichment (`BangumiImportManager`):**
  - Enriched freshly imported Bangumi entries directly with cached Latin titles, ensuring correct titles on initial render.
- **Title Fallback Hierarchy (`PreferenceHelpers`):**
  - Ensured that when English is preferred and a romaji fallback is active, any resolved English title takes precedence.

### 🐞 Video Player Panel Null-Safety Hardening (`DelayCard`)
- Replaced unsafe `!!` force unwraps and loop variables in `DelayCard` (audio/subtitle delay adjustment) with immutable local bindings (`startedAt`, `direction`), eliminating potential null pointer risks.

### 🧪 Unit Tests
- `BangumiLibraryTitleHydratorTest`: Verified tiered stages, language criteria, and large library coverage.
- `MediaEntryRepositoryTitlePreservationTest`: Verified title preservation during repeated sync imports.
- `BangumiImportTitlesTest`: Verified cache application during direct import.
- `BangumiDisplayTitleLanguageTest`: Verified English/Romaji/Native display contracts.

---

# Kitsugi Beta v2.4.316

## 🇹🇷 Türkçe

### ⚡ İndirmeler Ekranı & İndirme Sırasında Donma / Kasma Düzeltmesi

Bu sürüm, arka planda veya aktif olarak bir bölüm indirilirken İndirmeler (`DownloadsScreen`) ekranının kilitlenmesine, kaydırmanın donmasına ve arayüzün saniyede binlerce kez gereksiz yere yeniden çizilmesine (recomposition loop) yol açan kök nedenleri tamamen çözer. Ayrıntılı plan ve analiz: `PLAN_TASK_INDIRMELER_SAYFASI_KASMA_FIX_2026-10-20.md`.

- **İndirme İlerleme Hız Sınırlaması (Throttling) & 64 KB Tamponlama (`AnimeDownloader`):**
  - Doğrudan ve HLS dosya indirmelerinde 8 KB'lık mikro okumalar yerine `64 KB BufferedOutputStream` ve `64 KB` tampona geçildi.
  - Saniyede 500-2.000 kez tetiklenerek ana StateFlow'u ve UI'ı boğan `onProgress` geri çağrıları `SystemClock.elapsedRealtime()` tabanlı 250 ms hız sınırlaması (throttling) ile dengelendi (yüzde değişimlerinde en az 100 ms ve indirme tamamlandığında kesin %100 bildirimi).
  - HLS segment indirmelerinde her segmentte diske `localFile.length()` sormak yerine indirilen bayt bellekte takip edildi.
- **İlerleme Emniyet Kilidi & Asenkron Disk İşlemleri (`AnimeDownloadManager`):**
  - `updateProgress` metoduna 200 ms hız sınırlaması ve değişmeyen değerler için erken çıkış filtresi eklendi.
  - `saveAndEmit`, `deleteDownload` ve `clearAllDownloads` içindeki Gson serileştirme ve dosya/klasör silme işlemleri UI thread'den `Dispatchers.IO` (`ioScope`) üzerine taşındı.
- **Ana İş Parçacığında Disk Taraması Kaldırıldı (`DownloadsScreen`):**
  - `loadAllDownloadedSubtitles` ve `loadAllDownloadedImages` tamamen `Dispatchers.IO` (`produceState`) üzerine alındı.
  - Altyazı disk taraması aktif indirme baytlarından izole edildi; yalnızca tamamlanan indirmelerin anahtarına (`completedDownloadsKey`) bağlandı.
  - `DownloadItemRow` içinde kart bazlı metadata/altyazı disk okuması indirme sürerken (`DOWNLOADING`) devre dışı bırakıldı; yalnızca `COMPLETED` durumunda `Dispatchers.IO`'da çalışacak şekilde optimize edildi.
  - Tüm Regex derlemeleri dosya düzeyinde tek seferlik önbelleğe alındı; $O(N^2)$ `results.none` kontrolleri $O(1)$ `HashSet` ile değiştirildi.
  - Resimler sekmesindeki çoklu görsel grupları `chunked(4)` ile satır bazlı tembel yüklemeye (`LazyRow`) çevrilerek ilk yükleme rahatlatıldı.
- **Tek Geçişli Klasör Taraması (`OfflinePlaybackHelper`):**
  - İndirilen her video için diski baştan sona tekrar tarayan $O(N^2)$ `listFiles()` döngüsü tek geçişli önbelleklemeli yapıya (`primaryEntries`) indirgendi.
- **Kararlı Compose Modeli & Hafif Bildirim IPC'si:**
  - `AnimeDownload` modeli `@Immutable` olarak işaretlendi ve tüm değişken alanları (`var`) değiştirilemez (`val`) yapıldı.
  - Bildirim posteri maksimum `384px` boyuta küçültülerek Binder IPC bellek ve işlemci yükü düşürüldü (`AnimeDownloadService`).
  - `ApiDetailTabContents` ve `EntryDetailTabContents` içerisindeki gereksiz üst seviye `downloads.collectAsState()` abonelikleri doğrudan liste değeri okumasına çevrildi.

---

## 🇬🇧 English

### ⚡ Downloads Screen Performance & Lag Elimination During Active Downloads

This release eliminates UI freezing, frame drops, and severe jank on the Downloads screen (`DownloadsScreen`) while videos are being downloaded in the background. Detailed root-cause breakdown in `PLAN_TASK_INDIRMELER_SAYFASI_KASMA_FIX_2026-10-20.md`.

- **Download Progress Throttling & 64 KB Buffering (`AnimeDownloader`):**
  - Upgraded direct and HLS downloads to `64 KB BufferedOutputStream` with a `64 KB` read buffer (up from 8 KB).
  - Added a 250ms throttling guard (`SystemClock.elapsedRealtime()`) to `onProgress` callbacks, preventing hundreds/thousands of socket-driven recomposition events per second while ensuring 100% completion is reported accurately.
  - Tracked HLS segment byte lengths in memory rather than querying `localFile.length()` from disk on every segment.
- **Progress Safety Gate & IO Thread Offloading (`AnimeDownloadManager`):**
  - Added a 200ms throttle guard and no-op check to `updateProgress`.
  - Moved Gson serialization, export cleanup, and file deletion out of the UI thread into `ioScope` (`Dispatchers.IO`).
- **Main-Thread Disk Scans Removed (`DownloadsScreen`):**
  - Offloaded `loadAllDownloadedSubtitles` and `loadAllDownloadedImages` entirely to `Dispatchers.IO` via `produceState`.
  - Decoupled subtitle scanning from continuous download byte updates, listening only to completed download state keys.
  - Bypassed per-card disk metadata reads in `DownloadItemRow` while downloading, restricting them to completed downloads on IO dispatcher.
  - Cached regex instances at file level and converted $O(N^2)$ checks to $O(1)$ `HashSet` lookups.
  - Partitioned image groups into `chunked(4)` lazy rows to optimize memory and initial render time.
- **Single-Pass Directory Traversal (`OfflinePlaybackHelper`):**
  - Replaced repetitive $O(N^2)$ directory scans across `root.listFiles()` with a single-pass indexed scan.
- **Compose Stability & Notification IPC Optimization:**
  - Marked `AnimeDownload` as `@Immutable` and transitioned all fields from `var` to `val`.
  - Downscaled notification posters to a maximum of `384px`, significantly decreasing Binder IPC transmission overhead.
  - Eliminated high-level `downloads.collectAsState()` subscriptions in `ApiDetailTabContents` and `EntryDetailTabContents`.

---

# Kitsugi Beta v2.4.315

## 🇹🇷 Türkçe

### 🐞 Tam Ekran Oynatıcı Çökmesi (NPE) & Ana İş Parçacığı Donma Düzeltmesi

Bu sürüm, video izlerken veya bölüm/kaynak değiştirirken tam ekran oynatıcının aniden kapanmasına yol açan NullPointerException çökmesini ve parça listesi güncellemelerinden kaynaklanan 1.2 saniyelik arayüz takılmasını tamamen giderir. Ayrıntılı analiz: `KOK_NEDEN_RAPORU_VIDEO_IZLERKEN_COKME_2026-10-20.md`.

- **AnimatedVisibility Çıkış Animasyonu NPE Koruması (`BottomLeftPlayerControls`):** Bölüm başlığı gösteriminde `currentChapter!!` kullanımı, kontroller gizlenirken veya bölüm değişimi sırasında animasyon devam ederken nesne `null`'a düştüğünde çökmeye yol açıyordu (`b7.cc.invoke` NPE). Güvenli yerel kopya yapısına geçildi (`val chapter = currentChapter ?: return@AnimatedVisibility`).
- **Ana İş Parçacığı Donması & JNI Logcat Seli Engellendi (`KitsugiMpvSurfaceView`, `MpvPlayerEngine`):**
  - MPV'de ses kanalı sayısı (`track-list/N/channels`) yalnızca ses parçalarında mevcut olmasına rağmen video ve altyazı parçalarında da sorgulanıyor ve 17 ms'de 27 JNI hatası oluşturuyordu. Artık yalnızca ses parçalarında okunuyor.
  - Parça listesi güncellemeleri (`updateTracks`) ana Looper üzerinde debounced (birleştirilmiş) hale getirildi; N adet parça olayında tek bir yenileme yapılarak Choreographer 30+ kare atlama ve 1222 ms Looper blokajı ortadan kaldırıldı.
  - Mükerrer `sid`/`aid` tetiklemeleri temizlendi; motor serbest bırakıldığında (`release`) bekleyen tüm geri çağrılar iptal edildi.
- **Sıfıra Bölme & NaN Layout Çökme Korumaları:**
  - `SeekBar` ve `VerticalSliders`: Video süresi veya aralık 0 iken oluşan `NaN` değerinin `Modifier.fillMaxWidth(NaN)` / `fillMaxHeight(NaN)` çökmelerine sebep olması engellendi (`durationMs > 0` güvencesi).
  - `SkipIntroButton`: İntro aralığı 0 iken geri sayım çubuğunun çökmesi önlendi.
- **Null Güvenlik Sertleştirmeleri:**
  - `PauseOverlay`, `ScreenshotSheet`, `KitsugiPlayerViewModel`, `KitsugiStreamSelectorBottomSheet` ve `StreamCard` içindeki potansiyel güvensiz `!!` referansları güvenli yerel kopyalar ve elvis operatörleriyle güçlendirildi.
- **Çökme Analitiği Oynatıcı Ekran İzi:**
  - `KitsugiFullscreenPlayerScreen` açıldığında çökme geçmişine `Oynatıcı: <Başlık>` kaydı düşülerek olası arıza tespitleri şeffaflaştırıldı.

### ⚡ Listem Ekranı Kaydırma & Performans Optimizasyonu

Büyük listelerde (1000+ kayıt) hesaplar bağlandığında kaydırmayı kilitleyen ve takılmalara yol açan kök nedenler çözüldü. Ayrıntılı analiz: `KOK_NEDEN_RAPORU_LISTEM_KAYDIRMA_KASMA_2026-10-20.md`.

- **Kare Başı Tüm Ekran Recomposition'ı Kaldırıldı (`MyListScreen`):** Kaydırma konumu Compose State yerine düz `IntArray` üzerinde tutuldu; `LazyListState.Saver` ile tam kalıcılık ve akıcı 60-120 FPS kaydırma sağlandı.
- **Birleşik Kütüphane ("Tümü") Arka Plana Taşındı (`Dispatchers.Default`):** Kimlik köprüsü ve gruplama ana iş parçacığından çıkarıldı, `MyListViewModel` üzerinde önbelleklenerek detay dönüşlerindeki boşalıp dolma önlendi.
- **Arama & Sıralama Optimizasyonları:** Arama filtrelemesinde NFKC normalizasyonu tembel değerlendirmeye alındı; başlık sıralamasında Schwartzian dönüşümü ile on binlerce gereksiz String tahsisi ortadan kaldırıldı.

### 🔗 Paylaşım Bağlantıları — Tüm Kaynaklarda Kalıcı Düzeltme

Bu sürüm, anime/dizi/film/karakter/seslendirmen/ekip/stüdyo sayfalarındaki **paylaş** butonlarının her kaynakta **bozuk veya yanlış** bağlantı üretmesini 12 kök neden üzerinden çözer. Detaylı analiz: `KOK_NEDEN_RAPORU_PAYLASIM_LINKLERI_2026-10-20.md`.

- **Tek Üretici (`utils/ShareLinks.kt`):** Kaynak tanıma, offset çözümleme ve doğrulanmış URL şemaları tek bir motorda birleştirildi.
- **`/anime/0` Kabusu Bitti:** Vitrin paylaş butonu kimlik yerine sabit `0` gönderiyordu. Gerçek çözülmüş kimliğe bağlandı.
- **Offset'li Kimlikler Çözülüyor:** AniList +100M, Simkl +150M, Kitsu +300M, Shikimori +400M, Bangumi +500M offset'leri doğru kaynak şemalarına çevriliyor.
- **Güvenlik Ağı & Doğrulama:** `isTrustworthy` ile kırık bağlantı gönderimi önlendi; çözülemeyen durumlarda güvenli bağlantısız paylaşım yapılıyor.

### 🧪 Birim Testleri
- `ShareLinksTest`: 18 test — altı kaynağın offset çözümlemesi, kaynak-duyarlı ham kimlik, karakter/ekip/stüdyo şemaları ve URL güvenlik ağı.

---

## 🇬🇧 English

### 🐞 Fullscreen Player Crash (NPE) & Main Thread Jank Optimization

This release resolves a critical NullPointerException that caused crashes during playback or episode transitions, while eliminating a 1.2-second main-thread block caused by excessive JNI property queries during track changes. Detailed root-cause breakdown in `KOK_NEDEN_RAPORU_VIDEO_IZLERKEN_COKME_2026-10-20.md`.

- **AnimatedVisibility Exit Transition Crash Fix (`BottomLeftPlayerControls`):** Fixed a fatal NPE (`b7.cc.invoke`) caused by `currentChapter!!` being accessed during Compose exit animations after the chapter had been cleared by episode or source transitions. Protected with safe local extraction (`val chapter = currentChapter ?: return@AnimatedVisibility`).
- **Main Thread Unblock & JNI Query Flooding Resolved (`KitsugiMpvSurfaceView`, `MpvPlayerEngine`):**
  - Constrained `channels` property queries exclusively to audio tracks; previously queried on video and subtitle tracks, triggering rapid repeated JNI error logs.
  - Debounced `updateTracks()` invocations on the main Looper, coalescing multiple property events into a single pass and resolving a 1,222ms Choreographer frame drop.
  - Deduplicated redundant `sid`/`aid` event dispatches and ensured pending track runnables are cancelled during `release()`.
- **Division-by-Zero & NaN Layout Protection:**
  - `SeekBar` & `VerticalSliders`: Guarded against zero duration or volume ranges producing `NaN` dimensions inside `Modifier.fillMaxWidth()` / `fillMaxHeight()`.
  - `SkipIntroButton`: Added safeguards against division by zero in intro countdown progress.
- **Null-Safety Hardening:**
  - Replaced unsafe `!!` operators with guarded local bindings across `PauseOverlay`, `ScreenshotSheet`, `KitsugiPlayerViewModel`, `KitsugiStreamSelectorBottomSheet`, and `StreamCard`.
- **Accurate Player Screen Breadcrumbs:**
  - `KitsugiFullscreenPlayerScreen` now logs active media playback breadcrumbs to crash reports, improving diagnostics for video session failures.

### ⚡ MyList Screen Scrolling & Performance Optimization

Resolved main-thread blocking and frame drops when scrolling large lists (1000+ entries) with connected external providers. Detailed analysis: `KOK_NEDEN_RAPORU_LISTEM_KAYDIRMA_KASMA_2026-10-20.md`.

- **Eliminated Per-Frame Whole-Screen Recomposition (`MyListScreen`):** Stored tab scroll positions in a plain `IntArray` backed by a custom `Saver`, preventing state mutations on every scroll frame from recomposing the entire screen body.
- **Combined Library Grouping Offloaded to Background (`Dispatchers.Default`):** Moved identity crosswalk, grouping, and badge map generation off the main thread, caching results in `MyListViewModel` to eliminate flickering when returning from detail pages.
- **Search & Sort Optimizations:** Deferred expensive string normalizations in search filtering and applied Schwartzian transforms to title sorting.

### 🔗 Share Links — Permanent Fix Across All Sources

This release fixes the **share** buttons on anime/TV/movie/character/voice-actor/staff/studio pages producing **broken or wrong** links for every source, traced to 12 root causes. Full analysis: `KOK_NEDEN_RAPORU_PAYLASIM_LINKLERI_2026-10-20.md`.

- **Single Producer (`utils/ShareLinks.kt`):** Every share URL is now built by `ShareLinks` — source-aware ID decoding, offsets and verified page schemas in one place.
- **No More `/anime/0`:** Fixed the hero share button sending `0` instead of the resolved entry ID.
- **Offset Decoding:** Accurately unmaps AniList, Simkl, Kitsu, Shikimori, and Bangumi namespaced IDs.
- **Safety Net:** `isTrustworthy` blocks broken links from ever reaching external share targets.

### 🧪 Unit Tests
- `ShareLinksTest`: 18 tests covering six-source offset decoding, source-aware raw IDs, character/staff/studio schemas, and the URL safety net.

---

# Kitsugi Beta v2.4.314

## 🇹🇷 Türkçe

### 🔁 Liste Ekleme/Güncelleme ve Altı Kaynak (AniList · MAL · Simkl · Kitsu · Shikimori · Bangumi) Çapraz Senkron Düzeltmesi

Bu sürüm, bir kaynaktan (örn. Bangumi) eklenen veya düzenlenen kaydın seçilen durum (örn. **İzleniyor**), puan, tarih ve bölüm değerleriyle hem kendi kaynağına hem de bağlı tüm kaynaklara aynı anda yazılamaması sorununu 9 kök neden üzerinden tamamen çözer. Ayrıntılı analiz: `KOK_NEDEN_RAPORU_LISTE_SENKRON_SECILEN_DEGERLER_2026-10-20.md`.

- **Yerel-Öncelikli Ekle/Düzenle Hattı:** Bangumi ekleme ve düzenleme işlemlerindeki "önce uzak sunucuya yaz, başarısız olursa kaydetme" kapısı kaldırıldı (`AppRoot.kt`, `MyListViewModel.kt`, `AppViewModel.kt`). Artık tüm kaynaklar aynı hattı kullanır: değişiklik anında yerel veritabanına işlenir, arayüz gecikmeden güncellenir ve arka planda altı kaynağa birden (AniList, MAL, Simkl, Kitsu, Shikimori, Bangumi) dağıtılır. Ağ veya yetkilendirme hatalarında bekleyen senkron kuyruğunda otomatik olarak yeniden denenir.
- **Japonca Başlıkla Doğrulama:** Kimlik doğrulama katmanına satırın özgün Japonca başlığı eklendi (`CrossProviderIdentity.kt`, `KitsugiIdResolver.kt`). Bangumi satırlarında görünen başlık Çince olduğundan ve İngilizce başlık bulunmadığından MAL/AniList doğrulamaları düşüyordu; artık İngilizce → Japonca → Görünen Başlık sırasıyla Jikan/API araması yapılarak eşleşme garanti altına alındı.
- **Çözülen AniList Kimliğinin Korunması:** Çapraz kimlik katmanının çözdüğü AniList kimliği artık doğrudan yazma ve favori hattına aktarılıyor (`AniListSyncManager.kt`). Daha önce çözülen kimlik atılıp başlıkla yeniden arandığı için özellikle Bangumi kökenli kayıtlar düşüyordu.
- **Bangumi Manga/Kitap Desteği:** Kimlik köprüsündeki manga erken dönüşü düzeltildi; Bangumi kitap ve manga kayıtları da artık MAL/AniList kimliklerine çözülüp yayılabiliyor.
- **Simkl Bölüm, Puan ve Durum Sıralaması:** Simkl kimlik aramasındaki mantık hatası düzeltildi; kimlik varken bölüm ve puan adımlarının atlanması engellendi. Canlı senkron hattı toplu hatla eşitlendi: bölümler 1..N olarak işaretlenir, puan yazılır ve durum en son uygulanır (`SimklSyncManager.kt`). Uydurma `S01E<N>` yapısı kaldırıldı; geri yazımın yerel kaynak kimliğini ezmesi önlendi.
- **Shikimori Doğrudan Güncelleme:** Shikimori satırları MAL kimliği çözülemese dahi kendi `target_id` değeriyle güncellenebilir hale getirildi (`MalSyncManager.kt`).
- **Kaynak-Duyarlı Kimlik Ayrıştırma:** Ham Shikimori, Bangumi veya Kitsu kimliklerinin yanlışlıkla MAL kimliği sanılması ve hatalı önbelleğe alınması engellendi. Bangumi film/dizi subject kimliği korundu.
- **AniList Tarih Koruma & MAL Puan Normalizasyonu:** Yerel satırda tarih boşken AniList üzerindeki başlangıç/bitiş tarihlerinin sessizce silinmesi önlendi. MAL için 0..100 puan aralığı 1..10 ölçeğine normalize edilerek 400 Bad Request hataları giderildi.
- **Kardeş Satırların Anında Eşitlenmesi:** Bir kaynak sekmesinde yapılan düzenleme veya silme işlemi aynı yapıma ait diğer kaynak sekmelerine ve "Tümü" görünümüne yerelde anında yansıtılır (`MediaEntryRepository.kt`).

### ⚡ Liste Ekleme Otomasyonları: Durum → Tarih / İlerleme (`EntryAutoRules`)

**İstenen davranış artık varsayılan:** Hangi kaynaktan eklenirse eklensin (AniList, MyAnimeList, Simkl, Kitsu, Shikimori, Bangumi, TMDB veya eklenti), bir yapım **"İzliyorum"a çekildiği anda başlangıç tarihi bugün olarak dolar** — hem de gözünüzün önünde.

- **Tek Kural Motoru (`model/EntryAutoRules.kt`):** Tüm yollar artık aynı motoru çağırıyor — canlı düzenleyici, kaydetme anı, hızlı ekleme (`+` rozeti) ve bölüm artırımı.
- **Anında Geri Bildirim:** Durum hapına basar basmaz tarih satırı bugünün tarihiyle dolar; istemezseniz `X` ile tek dokunuşta temizleyebilirsiniz.
- **Kural Tablosu:**
  | Durum | Başlangıç | Bitiş | İlerleme |
  |-------|-----------|-------|----------|
  | İzliyorum / Tekrar İzliyorum | boşsa → bugün | — | — |
  | Tamamlandı | boşsa → bugün | boşsa → bugün | durum değiştiyse → toplam bölüm |
  | İzleyeceğim / Beklemede / Bırakıldı | — | — | — |
- **Veri Kaybı Koruması:** Dolu bir tarih **asla** üzerine yazılmaz. "İzliyorum"dan "İzleyeceğim"e geri dönüp tekrar "İzliyorum" yapsanız bile ilk tarih korunur.
- **`statusChanged` Koruması:** İlerlemenin otomatik tamamlanması yalnızca durum gerçekten değiştiğinde tetiklenir. Zaten "Tamamlandı" olan bir kaydın sadece puanını düzenlerseniz ilerlemenize dokunulmaz.
- **Yeni Ayarlar (Ayarlar → Tercihler → Liste):**
  - **Listeye eklerken varsayılan durum:** Hızlı ekleme (`+` rozeti) yapımı hangi durumla listeye atsın? *İzleyeceğim* (varsayılan) / *İzliyorum* / *İzledim*. "İzliyorum" seçilirse yapım **hiç panel açılmadan**, bugünün başlangıç tarihiyle doğrudan listeye girer.
  - **Duruma göre tarihi otomatik doldur** (açık) & **Tamamlandı seçilince ilerlemeyi tamamla** (açık).
- **Düzenleyici Paneli:** **Bugün**, **Dün** ve **Temizle** hızlı çipleri, bitiş tarihi başlangıçtan önce seçilirse kırmızı uyarı, yerelleştirilmiş "Tarih Seçilmemiş" metni ve 3 yeni ayar arama kaydı.

### 🧪 Birim Testleri
- `CrossProviderIdentityTest`: Ham Shikimori/Bangumi/Kitsu kimlik izolasyonu, Bangumi subject koruması, kaynak önbellek anahtarları ve kardeş satır kimlik kesişimleri test edildi.
- `CrossSyncIdentityGuardTest`: Özgün Japonca başlığın kimlik eşdeğerliğinde kullanımı doğrulandı.
- `EntryAutoRulesTest`: Durum bazlı başlangıç/bitiş tarihi doldurma, dolu tarih koruması ve durum değişimine bağlı ilerleme tamamlama test edildi.

---

## 🇬🇧 English

### 🔁 List Add/Update & Six-Source (AniList · MAL · Simkl · Kitsu · Shikimori · Bangumi) Cross-Sync Fix

This release resolves the issue where anime/manga items added or edited from one provider (e.g., Bangumi) would lose selected statuses (e.g., **Watching**), ratings, dates, and episode counts when syncing across connected tracking services. Detailed root-cause breakdown in `KOK_NEDEN_RAPORU_LISTE_SENKRON_SECILEN_DEGERLER_2026-10-20.md`.

- **Local-First Add/Edit Pipeline:** Removed the legacy remote-first write barrier for Bangumi (`AppRoot.kt`, `MyListViewModel.kt`, `AppViewModel.kt`). All providers now follow a unified pipeline: changes save immediately to local storage and asynchronously fan out to all 6 providers (AniList, MAL, Simkl, Kitsu, Shikimori, Bangumi), with automatic retries via pending sync queue.
- **Original Japanese Title Verification:** Integrated Japanese titles into identity verification (`CrossProviderIdentity.kt`, `KitsugiIdResolver.kt`). Because Bangumi titles are predominantly Chinese with no English equivalent, cross-provider matching previously failed; search order is now English → Japanese → Display title.
- **Preserved AniList Resolution:** Directly propagated resolved AniList IDs into mutation and favorite flows (`AniListSyncManager.kt`) rather than re-querying by title.
- **Bangumi Manga/Book Cross-Sync:** Lifted premature return in cross-provider bridge for manga entries, enabling Bangumi books to map to MAL/AniList entries.
- **Simkl Episode, Rating & Status Flow:** Inverted flawed Simkl lookup logic, eliminating dropped rating/episode updates (`SimklSyncManager.kt`). Synchronized live pipeline with batch rules: marks episodes 1..N, applies ratings, and commits final status. Removed synthetic `S01E<N>` hacks.
- **Direct Shikimori Mutations:** Allowed Shikimori updates to proceed using native `target_id` even when MAL ID is unresolved (`MalSyncManager.kt`).
- **Provider-Aware Identity Parsing:** Prevented raw Shikimori, Bangumi, and Kitsu IDs from being misidentified as MAL IDs in cache storage. Preserved Bangumi subject IDs on TV/movie records.
- **AniList Date Protection & MAL Rating Normalization:** Prevented null local dates from clearing remote start/finish dates on AniList. Normalized 0..100 ratings to the 1..10 scale for MyAnimeList.
- **Immediate Sibling Tab Synchronization:** Mutations made on one provider tab instantly update sibling entries across all provider tabs and the unified "All" feed (`MediaEntryRepository.kt`).

### ⚡ List-Add Automations: Status → Date / Progress (`EntryAutoRules`)

**The requested behaviour is now the default:** whichever source an item comes from (AniList, MyAnimeList, Simkl, Kitsu, Shikimori, Bangumi, TMDB or an addon), the moment you set it to **Watching the start date fills in with today** — visibly, right in front of you.

- **Single Rule Engine (`model/EntryAutoRules.kt`):** Every path now calls the same engine — live editor, save handler, quick add (`+` badge) and episode increment.
- **Instant Feedback:** Tapping a status pill immediately fills the date row with today; clear it again with a single tap on `X`.
- **Rule Table:**
  | Status | Start | Finish | Progress |
  |--------|-------|--------|----------|
  | Watching / Repeating | if empty → today | — | — |
  | Completed | if empty → today | if empty → today | on status change → total |
  | Planned / Paused / Dropped | — | — | — |
- **Data-loss Guard:** Existing dates are never overwritten. Switching Watching → Planned → Watching again keeps the original date.
- **`statusChanged` Guard:** Progress auto-completion only fires when the status actually changed.
- **New Settings (Settings → Preferences → List):** Default status when adding, auto-fill dates by status, and auto-complete progress on Completed.
- **Editor Sheet Comfort Improvements:** Today / Yesterday / Clear chips, date order warning, localized string labels, and 3 settings search entries.

### 🧪 Unit Tests
- `CrossProviderIdentityTest`: Validates non-MAL ID parsing, Bangumi movie subject preservation, namespaced cache keys, and cross-row identity matching.
- `CrossSyncIdentityGuardTest`: Verifies Japanese title matching in canonical equivalence checks.
- `EntryAutoRulesTest`: Verifies status-driven date auto-fill, non-overwrite protection, and status change progress completion.

---

# Kitsugi Beta v2.4.313

## 🇹🇷 Türkçe

### 🎨 Hızlı Ekleme Butonu Kart Dili Uyumu (`KitsugiQuickAddButton`)
- **Köşe Rozeti Tasarımı:** Posterin üstüne binen 30dp daire ve beyaz halka yerine, kartın diğer köşe rozetleriyle (sıra etiketi `#1`, kaynak logosu, durum rozeti) birebir uyumlu, köşeye sıfır boşlukla oturan ve yalnızca alt-sol köşesi yuvarlatılmış yarı saydam füme cam rozet yapısına geçildi.
- **Dinamik Boyutlandırma:** Kart tipine göre optimize edilmiş boyutlar; Grid ve TV posterlerinde 28dp, liste ve detaylı sezonluk kartlarda 24dp.
- **Sessiz ve Uyumlu Liste Durumu:** Listedeki kartlar için göz yoran parlak yeşil bloklar yerine, füme cam zemin + ince yeşil çerçeve ve yeşil tik tasarımı uygulandı; durum rozetleriyle görsel karmaşa engellendi.
- **Akıcı Animasyonlar:** Zemin, çerçeve ve ikon rengi geçişleri `animateColorAsState` ile yumuşatıldı.
- **TV Odak İyileştirmesi:** Android TV'de poster clip sınırlarında kırpılmaya yol açan büyüme efekti devre dışı bırakıldı (`scaleOnFocus = false`), odak konturu rozetin geometrisine tam kilitlendi.
- **Kart Entegrasyonları (`DetailedSeasonalMediaCard`, `KitsugiExploreMediaCard`, `KitsugiRankingMediaCard`):** Dış boşluklar (padding) kaldırılarak rozetlerin poster köşesine pürüzsüz yapışması sağlandı.

---

## 🇬🇧 English

### 🎨 Quick-Add Button Card Aesthetics Parity (`KitsugiQuickAddButton`)
- **Seamless Corner Badge Redesign:** Replaced the circular 30dp sticker-like badge with an integrated, semi-transparent frosted glass badge featuring a single rounded bottom-start corner, matching existing card chrome (rank `#1`, source logos, status badges).
- **Adaptive Layout Sizing:** Sized contextually per view: 28dp on grid and TV posters, 24dp on list and seasonal card variations.
- **Subtle In-List Representation:** Eliminated jarring bright green fill blocks in favor of a soft dark glass base, subtle green outline, and green checkmark that blends naturally with the existing card status.
- **Fluid Color Transitions:** Animated scrim background, border strokes, and icon tint with `animateColorAsState`.
- **Android TV Focus Polish:** Disabled focus expansion (`scaleOnFocus = false`) to prevent clipping artifacts inside poster clipping boundaries, keeping focus rings tightly contoured.
- **Card Integrations (`DetailedSeasonalMediaCard`, `KitsugiExploreMediaCard`, `KitsugiRankingMediaCard`):** Stripped outer padding to dock the badge flush against poster bounds.

---

# Kitsugi Beta v2.4.312

## 🇹🇷 Türkçe

### 📺 Oynatıcı İçi Harici Oynatıcı Butonu & Konum Eşitleme
- **Doğrudan Harici Oynatıcıda Açma Butonu (`TopRightPlayerControls`, `MoreSheet`):** Oynatıcının sağ üst araç çubuğuna (Altyazı, Ses, Kalite ikonlarının yanına) ve "Daha Fazla" menüsünün üst aksiyon çubuğuna doğrudan harici oynatıcıda aç (`OpenInNew`) butonu eklendi.
- **Kesintisiz Konum, Başlık & Geri Dönüş Senkronizasyonu (`ExternalPlayerResultContract`):** Harici oynatıcıya geçildiğinde dahili oynatıcı duraklatılır; oynatma konumu, HTTP başlıkları (Origin, Referer, User-Agent), altyazılar ve kullanıcının tercih ettiği varsayılan oynatıcı paketi (MX Player, VLC, mpv-android, Just Player vb.) aktarılır. Harici oynatıcı kapatılıp uygulamaya dönüldüğünde kalınan konum ve tamamlanma durumu otomatik olarak oynatıcıya, izleme geçmişine ve scrobble denetleyicisine işlenir.

### 🎬 Aniyomi Oynatıcı Tam Paritesi & Gelişmiş Arayüz/Davranış
- **Media3 Yerel Bölüm (Chapter) Desteği:** ID3 `ChapterFrame` ve VorbisComment / Matroska (`CHAPTER001`, `CHAPTER001NAME`) bölüm metaverileri ayrıştırılarak seekbar ve Chapters menüsüne bağlandı.
- **OSD, Panel & Arayüz Tercihleri:** Kontrollerin otomatik gizlenme süresi (`playerTimeToDisappearMs`), panel/menü opaklığı (`panelOpacity`), hareket azaltma (`reducePlayerMotion`), sistem durum çubuğu görünürlüğü (`showSystemStatusBar`), ekran çentiği kullanımı (`playerFullscreenCutout`), yükleme halkası (`showLoadingCircle`) ve aktif chapter başlığı görünürlüğü (`showCurrentChapter`) ayarları eklendi.
- **Süre Göstergesi & Jest Özelleştirme:** Seekbar sağ süre göstergesine tıklanınca kalan süre (-DK:SN) ile toplam süre arasında kalıcı geçiş (`invertDurationTimer`); sol, orta ve sağ çift dokunma bölgeleri için bağımsız eylem seçimi (SEEK, PLAY_PAUSE, SWITCH_SUB vb.); son kullanılan parlaklık ve ses seviyesini hatırlama.
- **AniSkip Netflix Tarzı Geri Sayım & Otomatik Atlatma:** Kontroller gizliyken sağ altta yüzen "Giriş'i Atla" butonu; Netflix tarzı geri sayım (`enableNetflixStyleIntroSkip`, `waitingTimeIntroSkip`) ile süre sonunda otomatik atlama; videoda gömülü chapter varsa AniSkip çakışmasını engelleme politikası.
- **Seek Önizleme Optimizasyonu & Bölüm Küçük Resmi:** HLS/DASH/P2P akışlarında gereksiz ağ yükü engellendi; `content://` URI geçişlerinde `ParcelFileDescriptor` sızıntısı kapatıldı; ekran görüntüsünden kaydedilen bölüm küçük resmi hem oynatıcı içi hem de detay sayfası bölüm listesine bağlandı.
- **MPV Güvenlik & Ses Odağı İyileştirmesi:** AndroidCAStore sertifikaları PEM formatına aktarılarak MPV güven zincirine bağlandı (`MpvTrustStore`); duraklatma sırasında ses odağının diğer uygulamalara eksiksiz bırakılması sağlandı (`abandonFocus`).

### 📖 Manga Okuyucu Arşiv Desteği (ZIP / CBZ)
- **Yerel Arşiv Sayfa Yükleyici (`ArchivePageLoader`, `MangaPageLoaderV2`):** Çevrimdışı indirilen veya cihazda bulunan ZIP ve CBZ formatındaki manga arşivlerinden doğrudan sayfa okuma ve görüntüleme desteği eklendi.

### 💾 Veri Yedekleme ve Geri Yükleme (`BackupManager`)
- **Eksiksiz Uygulama Yedekleme:** Room veritabanı (izleme geçmişi, manga ilerlemesi, eklentiler), DataStore tercihleri ve indirme listesi tek bir ZIP arşivi halinde yedeklenebilir ve geri yüklenebilir hale getirildi.

### 📱 Ana Ekran İndirmeler Widget'ı (`DownloadWidgetProvider`)
- **Canlı İndirme Sayacı:** Ana ekrana eklenebilen ve anlık aktif indirme durumunu gösteren yeni sistem widget'ı eklendi.

---

## 🇬🇧 English

### 📺 In-Player External Player Launch & Playback Sync
- **Dedicated External Player Launcher (`TopRightPlayerControls`, `MoreSheet`):** Added a prominent "Open in External Player" icon (`OpenInNew`) to the top-right player toolbar (adjacent to Subtitles, Audio, and Quality) as well as the quick-actions row in the "More" bottom sheet.
- **Seamless Position, Headers & Return State Synchronization (`ExternalPlayerResultContract`):** Tapping the external button pauses internal playback and launches the chosen player (MX Player, VLC, mpv-android, Just Player) with full streaming headers (Origin, Referer, User-Agent), active subtitles, and the exact timestamp. Upon returning to Kitsugi, resumed position and completion states are seamlessly merged into playback history and anime scrobbler.

### 🎬 Complete Aniyomi Player Parity & UX Enhancements
- **Native Media3 Chapters Support:** Extracted and mapped ID3 `ChapterFrame` and VorbisComment / Matroska chapters to the timeline seekbar and chapters sheet.
- **OSD, Panels & Visibility Preferences:** Comprehensive controls timeout (`playerTimeToDisappearMs`), panel opacity (`panelOpacity`), reduced player motion (`reducePlayerMotion`), status bar toggling (`showSystemStatusBar`), display cutout handling (`playerFullscreenCutout`), loading spinner visibility (`showLoadingCircle`), and active chapter overlay (`showCurrentChapter`).
- **Time Display & Gesture Customization:** Tap right-hand duration display to toggle remaining time (-MM:SS) vs. total time (`invertDurationTimer`); independent left, center, and right double-tap zones (SEEK, PLAY_PAUSE, SWITCH_SUB, etc.); restore last-used volume and brightness levels.
- **AniSkip Netflix-Style Auto-Skip Countdown:** Floating "Skip Intro" badge displayed even when player controls are hidden; configurable Netflix-style auto-skip countdown (`enableNetflixStyleIntroSkip`); automatic AniSkip suppression when native chapters exist.
- **Seek Preview Optimization & Chapter Thumbnails:** Throttled network probes on HLS/DASH/P2P live streams; fixed `ParcelFileDescriptor` resource leaks; tied screenshot-saved thumbnails to both in-player and anime detail episode rows.
- **MPV Security & Audio Focus:** AndroidCAStore trusted roots exported to PEM for libmpv TLS verification (`MpvTrustStore`); proper audio focus abandonment on pause (`abandonFocus`).

### 📖 Manga Reader Local Archive Support (ZIP / CBZ)
- **Local Archive Page Loader (`ArchivePageLoader`, `MangaPageLoaderV2`):** Direct page reading and rendering from offline ZIP and CBZ manga archive files.

### 💾 Data Backup & Restore (`BackupManager`)
- **Full App Data Migration:** Safe backup and restoration of Room database, DataStore settings, and downloads manifest as a unified ZIP archive.

### 📱 Home Screen Downloads Widget (`DownloadWidgetProvider`)
- **Live Downloads Counter:** Added home screen widget displaying active and stored downloads count.

---

# Kitsugi Beta v2.4.311

## 🇹🇷 Türkçe

### 🎬 Aniyomi Oynatıcı Paritesi & Gelişmiş MPV / Media3 Entegrasyonu
- **Ses Odağı ve Kulaklık Çıkarılınca Duraklatma (`MpvAudioFocusController`):** Sistem ses odağı (audio focus) değişimleri dinlenir; kulaklık veya Bluetooth bağlantısı kesildiğinde (`BECOMING_NOISY`) oynatma otomatik olarak güvenle duraklatılır.
- **Güvenli TLS & CA Sertifika Deposu (`MpvTrustStore`):** Android CA sertifika zinciri güvenli şekilde libmpv'ye aktarılır, geçersiz/boş CA kaynaklı çökmeler engellenir.
- **Bölüm (Chapter) ve Seek Önizlemesi (`PlayerChapter`, `PreviewGenerator`):** Video dosyaları içerisindeki bölümler (chapters) algılanarak seekbar üzerinde görselleştirilir; arama çubuğunda thumbnail / seek önizleme desteği getirildi.
- **Çift Altyazı & İkinci Altyazı Seçimi:** Birincil ve ikincil altyazı bağımsız olarak seçilebilir; ikinci altyazının birinci gibi seçili görünmesi hatası giderildi.
- **Geçici Oynatma Hızı Koruması:** Geçici olarak artırılan oynatma hızının ayar güncellemeleri veya ekran döndürme esnasında sıfırlanması önlendi.
- **Asenkron Ekran Görüntüsü Hattı:** Ekran görüntüsü alma, kodlama, diske kaydetme ve paylaşma işlemleri ana iş parçacığından arka plana (`Dispatchers.IO`) taşınarak arayüz takılmaları giderildi.
- **PiP & MediaSession Köprüsü:** Picture-in-Picture modunda ve sistem MediaSession kontrollerinde durum senkronizasyonu mükemmelleştirildi; MPV Lua betik köprüsü (`aniyomi.lua`) dahil edildi.

### 🧪 Birim Testleri
- `MpvPlaybackPolicyTest`, `MpvPlayerEngineTest` ve `MpvTlsPolicyTest` testleri eklendi/güncellendi.

---

## 🇬🇧 English

### 🎬 Aniyomi Player Parity & Advanced MPV / Media3 Integration
- **Audio Focus & Headphone Disconnect Pause (`MpvAudioFocusController`):** Full Android audio focus management. Playback automatically pauses safely when headphones or Bluetooth audio disconnects (`ACTION_AUDIO_BECOMING_NOISY`).
- **Resilient TLS Trust Store (`MpvTrustStore`):** Safely injects system CA certificates into libmpv without ghost file paths or handshake drops.
- **Chapters & Seek Preview (`PlayerChapter`, `PreviewGenerator`):** Video embedded chapters are parsed and rendered onto the timeline; added seek thumbnail preview rendering.
- **Dual Subtitles & Secondary Track Selection:** Independent secondary subtitle selection with resolved active-state display parity.
- **Temporary Playback Speed Retention:** Fixed temporary playback speed being reset upon setting updates or screen rotation.
- **Asynchronous Screenshot Pipeline:** Screenshot capture, encoding, disk storage, and sharing operations migrated to background I/O coroutines, eliminating UI thread hitching.
- **PiP & MediaSession Sync:** Enhanced Picture-in-Picture lifecycle, system MediaSession notification metadata, and MPV Lua script bridge (`aniyomi.lua`).

### 🧪 Unit Tests
- Added and updated `MpvPlaybackPolicyTest`, `MpvPlayerEngineTest`, and `MpvTlsPolicyTest`.

---

# Kitsugi Beta v2.4.310

## 🇹🇷 Türkçe

### 🚫 Ölü Link & Sahte Kaynak Koruması (`KitsugiCdnJwExtractor` & `CsStreamRunner`)
- **Bayat `contentUrl` Fallback Sarmalı Kırıldı (`KitsugiCdnJwExtractor`):** Embed sayfalarında şifreli `dc_` bloğu bulunup deşifre edilemediğinde (yeni script paketlemeleri, bad base-64 vb.), sistem artık sayfadaki eski ve CDN'de silinmiş olan bayat `contentUrl` adresini sahte kaynak olarak üretmiyor. Yalnızca açık `.m3u8` varsa denenir; aksi halde ölü kaynak üretilmeyip `crypted block çözülemedi` uyarısı `CsTrace` ve `HDCH`/`Kekik_CloseLoad`/`Rapidrame` loglarına aktarılır.
- **URL-Safe Base64 & Padding Desteği:** JWPlayer şifreli yükleri için URL-safe Base64 (`-` ve `_`) karakter desteği ve eksik dolgu (`padding`) otomatik onarımı eklendi.
- **Kesinleşmiş 404 Ölü Link Koruması (`CsStreamRunner`):** Hem HTTP HEAD 404 hem de HTTP GET 404 döndüğü kesinleşen (`trulyDead`) akışlar artık "false positive olabilir" denilerek oynatıcıya zorla gönderilmez. Oynatıcı sahte kaynakla açılıp MPV 9001 hatası vermez; kaynak temiz şekilde elenerek durum Tanı Raporu'na yansıtılır. Ağ zaman aşımı ve belirsiz durumlardaki iyimser fallback korundu.

---

## 🇬🇧 English

### 🚫 Dead Link & Ghost Source Guard (`KitsugiCdnJwExtractor` & `CsStreamRunner`)
- **Broke Stale `contentUrl` Fallback Loop (`KitsugiCdnJwExtractor`):** When an encrypted `dc_` JWPlayer payload is present but fails to decrypt (e.g. newer eval obfuscation or malformed base64), the native extractor no longer falls back to stale JSON-LD `contentUrl` links that have been decommissioned by the CDN. Only plain exposed `.m3u8` links are attempted; otherwise, no phantom stream is emitted, and a clear `crypted block could not be decrypted` alert is routed to diagnostic logs and tags.
- **URL-Safe Base64 & Missing Padding Recovery:** Added automatic handling for URL-safe Base64 characters (`-` and `_`) and automatic padding restoration for encrypted payloads.
- **Hard 404 Dead Stream Discard (`CsStreamRunner`):** Streams verified as conclusively dead (failing both HEAD 404 and GET 404) are no longer re-admitted under false-positive recovery heuristics. This prevents dead links from triggering MPV 9001 playback errors.

---

# Kitsugi Beta v2.4.309

## 🇹🇷 Türkçe

### 🛡️ Oynatıcı & Eklenti Detaylı Hata Yakalayıcı (Diagnostics & Copy)
- **Kapsamlı Hata Ayıklama & Log Aynalama (`KitsugiCdnJwExtractor`):** Close ve Rapid yerleşik çözücüleri (`closeload.*`, `rapidrame.*`, `rplayer.*`) artık hiçbir hatayı yutmuyor. URL, origin, HTTP durum kodu ve stacktrace detayları `CsTrace.error` ile kaydediliyor; boş HTML veya boş master listesi uyarı olarak düşüyor. Close hataları `Kekik_CloseLoad` + `HDCH`, Rapid hataları ise `Rapidrame` + `HDCH` tag'lerine aynalanarak logcat filtrelerinin dolu kalması sağlandı.
- **Akış Çözücü Zaman Aşımı & Hata Takibi (`CsStreamRunner`):** `loadExtractor` çağrılarındaki zaman aşımları ve istisnalar URL ve hata nedeni (`cause`) ile birlikte `CsTrace` ve etiket loglarına işleniyor.
- **Zengin Hata Mesajları & İpuçları (`Media3PlayerEngine`):** Oynatıcı tek satırlık "oynatıcı hatası" yerine HTTP 403, 404, 5004 kodlarını, iç içe hata nedenlerini, URL/host bilgisini ve çözüm ipuçlarını (403 → VPN dene, 5004 → Origin hatası, kodek → MPV motoruna geçiş) kullanıcıya ve tanı raporuna bildirir.
- **Kopyalanabilir Hata Ekranı (`PlayerErrorView`):** Oynatıcı hata ekranına seçilebilir metin, tek tıkla panoya **Kopyala** butonu ve "Detay Ayarlar > Tanı Raporu'na yazıldı" bilgilendirmesi eklendi. Kullanıcı hatayı anında kopyalayıp paylaşabilir.

---

## 🇬🇧 English

### 🛡️ Player & Addon Detailed Error Interceptor & Diagnostics
- **Comprehensive Error Logging & Logcat Tag Mirroring (`KitsugiCdnJwExtractor`):** Native Close/Rapid extractors no longer swallow parsing exceptions. Every failure records full stacktrace, URL, origin, and HTTP status code into `CsTrace.error`. Close errors are mirrored to `Kekik_CloseLoad` + `HDCH`, and Rapid errors to `Rapidrame` + `HDCH` tags.
- **Extractor Timeout & Stream Tracking (`CsStreamRunner`):** `loadExtractor` timeouts and uncaught exceptions now capture the target stream URL and cause chain directly into diagnostic logs.
- **Rich Diagnostic Error Messages & Hints (`Media3PlayerEngine`):** Instead of an opaque single-line "player error", the engine generates detailed diagnostic summaries including HTTP codes (403/404/5004), URL, target host, root causes, and actionable hints (403 → Try VPN, 5004 → Origin mismatch, Codec → MPV engine recommended).
- **Copyable In-Player Error Screen (`PlayerErrorView`):** The player error UI now features selectable text, a one-tap **Copy** button, and guidance directing users to Settings > Diagnostics Report.

---

# Kitsugi Beta v2.4.308

## 🇹🇷 Türkçe

### 🎬 HDFilmCehennemi & FilmMakinesi Oynatıcı Hatası Kök Çözümü (KN-E & KN-U)
- **Embed Origin Başlığı Düzeltmesi (`KN-U`):** `PlayerMediaSourceFactory` ve `CsStreamRunner` oynatıcıya akış hazırlarken `Origin` başlığını ya boş bırakıyor ya da video host'u (`playmix.uno`, `s427.rapidrame.com`) ile dolduruyordu. Bu CDN'ler `Origin`'i embed sayfasının origin'ine (`closeload.filmmakinesi.to`, `rapidrame.com`) göre doğruladığı için CDN tarafında 403/5004 hatası oluşuyor ve ExoPlayer `ParserException` fırlatarak oynatıcı hatasına düşüyordu. `PlayerMediaSourceFactory` artık `Origin`'i `Referer` başlığının origin'inden (`originOfUrl`) türetir; video host yalnızca Referer bulunamadığında son çare olarak kullanılır.
- **Akış Koşucusu Başlık Koruması (`CsStreamRunner`):** Hem doğrudan medya akışında hem de fallback kollarında `Origin` başlığı eksikse otomatik olarak `Referer` veya kaynak URL'sinin origin'i ile tamamlanır. Eklentinin sağladığı özel başlıklar asla ezilmez.
- **Canlı Eklenti Manifest Senkronizasyonu (`KN-E`):** Gömülü `assets/plugins/plugins.json` dosyası canlı `builds` dalındaki en güncel derlemelerle senkronize edildi: FilmMakinesi v66 (`47441 B`, `sha256-0da3db3d...`) ve HDFilmCehennemi v56 (`72972 B`, `sha256-62d1abaf...`). Çevrimdışı ve ilk kurulumlarda cihaz doğrudan güncel `.cs3` eklentileriyle başlar.

---

## 🇬🇧 English

### 🎬 HDFilmCehennemi & FilmMakinesi Player Error Root Fix (KN-E & KN-U)
- **Accurate Embed Origin Derivation (`KN-U`):** `PlayerMediaSourceFactory` and `CsStreamRunner` previously omitted `Origin` or defaulted it to the stream's video host (`playmix.uno`, `s427.rapidrame.com`). Regional CDN clusters validate requests strictly against the embed origin (`closeload.filmmakinesi.to`, `rapidrame.com`); improper Origin caused HTTP 403/5004 responses, prompting ExoPlayer `ParserException` errors. `PlayerMediaSourceFactory` now reliably extracts `Origin` from the `Referer` header's origin via `originOfUrl()`, utilizing video host solely as an ultimate fallback.
- **Header Enrichment in Stream Runner (`CsStreamRunner`):** Both direct-playback and fallback execution paths in `CsStreamRunner` automatically populate missing `Origin` headers from the Referer or sanitized URL, preserving provider-specified tokens.
- **Addon Manifest Parity (`KN-E`):** Synchronized embedded `assets/plugins/plugins.json` with live upstream `builds`: FilmMakinesi v66 (`47441 B`, `sha256-0da3db3d...`) and HDFilmCehennemi v56 (`72972 B`, `sha256-62d1abaf...`). Fresh or offline installs immediately operate with the latest patched addons.

---

# Kitsugi Beta v2.4.307

## 🇹🇷 Türkçe

### 🔒 Asıl Kök Neden (KN-D) — MPV TLS & HTTPS Akış Düzeltmesi
- **MPV Motorunda Hayalet CA Dosyası Kaldırıldı (`KitsugiMpvSurfaceView`):** Uygulamanın varsayılan oynatıcısı olan MPV motoru (`playerPreference = "MPV"`), `initOptions()` içinde `tls-verify = yes` ve `tls-ca-file = <filesDir>/cacert.pem` ayarlıyordu. Ancak `cacert.pem` dosyası hiçbir zaman diske yazılmadığından veya paketlenmediğinden, libmpv'nin CA sertifika deposu boş kalıyordu. Sonuç olarak Türkçe yayın sunucularının tamamı HTTPS olduğu için tüm bağlantılar TLS el sıkışmasında başarısız olup 9001 ("MPV dosyayı açamadı") hatası veriyordu.
- **Media3 İle Tutarlı TLS Politikası:** Media3 motorunun Türkçe yayın CDN'leri için kullandığı esnek SSL politikası (`ignoreSSL = true`) ile uyumlu hale getirildi: `tls-verify = no` yapıldı ve var olmayan `cacert.pem` referansı kaldırıldı.
- **Saf TLS Politika Fonksiyonları & Koruma:** MPV TLS mantığı `mpvTlsOptions` ve `applyMpvTlsOptions` saf yardımcılarına taşındı. Doğrulama yalnızca diskte gerçek ve geçerli bir CA dosyası bulunursa açılacak şekilde mimarilendirildi.

### 🔌 Eklenti Senkronizasyonu (HDFilmCehennemi v55, FilmMakinesi v65)
- **Kitsugi-Plugins Güncellemeleri:** Eklenti tarafındaki `linkTypeFor()` kaynaklı `INFER_TYPE!!` NullPointerException hatası ve meşru stream adreslerini eleyen dar filtreler giderildi.
- **`plugins.json` Manifest Senkronizasyonu:** `app/src/main/assets/plugins/plugins.json` içinde FilmMakinesi v65 (SHA-256: `1dc3aafa...`), HDFilmCehennemi v55 (SHA-256: `4f4876b8...`) güncel hash ve boyutlarıyla güncellendi.

### 🧪 Birim Testleri
- `MpvTlsPolicyTest`: CA yokluğunda `tls-verify=no` davranışı, eksik dosya koruması, boş/whitespace yol kontrolü ve seçenek izolasyonu 11 birim test ile kilitlendi.

---

## 🇬🇧 English

### 🔒 Primary Root Cause (KN-D) — MPV TLS & HTTPS Playback Fix
- **Eliminated Ghost CA File in MPV Engine (`KitsugiMpvSurfaceView`):** The app's default player engine, libmpv (`playerPreference = "MPV"`), previously configured `tls-verify = yes` alongside `tls-ca-file = <filesDir>/cacert.pem`. Because `cacert.pem` was never generated or packaged on Android, mpv's certificate store remained empty, causing all HTTPS TLS handshakes to fail against Turkish CDN hosts (resulting in error 9001 "MPV could not open file").
- **Parity with Media3 SSL Policy:** Aligned MPV with Media3's SSL handling for regional CDN hosts (`ignoreSSL = true`), setting `tls-verify = no` and removing the dangling CA file path.
- **Pure Function TLS Policy Extraction:** Extracted MPV TLS configuration into `mpvTlsOptions` and `applyMpvTlsOptions` pure functions, ensuring certificate verification is only enabled when a valid, accessible CA bundle actually exists on disk.

### 🔌 Addon Sync (HDFilmCehennemi v55, FilmMakinesi v65)
- **Kitsugi-Plugins Upstream Fixes:** Fixed the silent `INFER_TYPE!!` NullPointerException in `linkTypeFor()` and broadened stream pattern filters in `Kitsugi-Plugins`.
- **`plugins.json` Manifest Alignment:** Updated bundled `plugins.json` manifests to FilmMakinesi v65 (SHA-256: `1dc3aafa...`) and HDFilmCehennemi v55 (SHA-256: `4f4876b8...`).

### 🧪 Unit Tests
- `MpvTlsPolicyTest`: Verified 11 test cases covering CA-absent defaults, invalid/blank path handling, and strict option isolation.

---

# Kitsugi Beta v2.4.306

## 🇹🇷 Türkçe

### 🎬 HDFilmCehennemi & FilmMakinesi Video Oynatma & Akış Düzeltmeleri
- **Embed Origin Başlığı Kurtarma ve 5004 Düşme Koruması (`KN-B`):** FilmMakinesi ve HDFilmCehennemi gibi gömülü sağlayıcılarda `rapidrame`, `playmix.uno`, `closeload` akışları embed sayfasının origin'ine göre doğrulanır. `CsStreamRunner` kurtarma yollarında (medya içerik kontrolü, `EmbedMediaScanner` derin HTML taraması, WebView medya yakalama) ve `verifyMediaCandidate` doğrulama çağrısında `Origin` başlığı eksik kalmıyor; embed sayfasının gerçek origin'i (`originOfUrl`, `applyEmbedOriginHeaders`) otomatik dolduruluyor. Eklentinin sağladığı özel token ve başlıklar kesinlikle ezilmiyor.
- **Bozuk URL ve Çift Şema Savunma Katmanı (`CsStreamSanitizer`):** Eski eklenti sürümlerinin ürettiği çift şemalı (`httpshttp://`, `httpshttps://`, `httphttps://`), şemasız veya çözümlenemeyen bozuk adreslerin ExoPlayer'a ulaşıp oynatıcıyı kilitlenmesi engellendi. `isBrokenStreamUrl` filtresi Adım 0 olarak sanitizasyon hattına eklendi; bozuk linkler elenerek oynatıcının çökmesi ve sahte kaynak kartı gösterimi engellendi.

### 🧪 Kapsamlı Birim Testleri
- `CsEmbedOriginHeadersTest`: URL'den origin türetme, başlık ezmeme koruması, büyük/küçük harf duyarsızlığı, rapidrame/closeload/playmix gerçek başlık testleri eklendi.
- `CsStreamSanitizerTest`: Bozuk şema tespiti, `master.txt` gibi meşru manifestlerin filtrelenmemesi ve torrent kaynaklarının korunması doğrulandı.
- `PlayerHlsDetectionTest`: Canlı token ve parametre taşıyan gerçek dünya CDN URL'leri test setine eklendi.

### 🔌 Eklenti Yaması Hazırlandı (`KITSUGI_PLUGINS_HDFC54_FM64_INFER_TYPE_FIX.patch`)
- `Kitsugi-Plugins` deposundaki HDFilmCehennemi (v54) ve FilmMakinesi (v64) için `linkTypeFor()` fonksiyonundaki `INFER_TYPE!!` NullPointerException hatasını gideren, `.ts` içeren meşru manifestlerin elenmesini önleyen eklenti yaması hazırlandı.

---

## 🇬🇧 English

### 🎬 HDFilmCehennemi & FilmMakinesi Playback & Stream Recovery Fixes
- **Embed Origin Header Propagation & 5004 Drop Prevention (`KN-B`):** Embedded streaming hosts such as `rapidrame`, `playmix.uno`, and `closeload` authenticate requests against the embed page's origin. Enhanced `CsStreamRunner` fallback mechanisms (media content check, `EmbedMediaScanner` deep DOM inspection, WebView media sniffer) and `verifyMediaCandidate` to populate the canonical embed origin (`originOfUrl`, `applyEmbedOriginHeaders`) without overwriting provider-specified token headers.
- **Corrupt URL & Double-Scheme Safeguard (`CsStreamSanitizer`):** Prevented malformed URLs produced by legacy addons (e.g. `httpshttp://`, `httpshttps://`, `httphttps://`, or missing schemes) from reaching ExoPlayer and crashing playback. Implemented Step 0 `isBrokenStreamUrl` validation to eliminate broken streams early.

### 🧪 Comprehensive Unit Tests
- `CsEmbedOriginHeadersTest`: Regression tests for canonical origin derivation, header preservation, case-insensitivity, and live rapidrame/closeload/playmix headers.
- `CsStreamSanitizerTest`: Verified corrupt URL detection, safe handling of `master.txt` manifests, and retention of schemeless torrent sources.
- `PlayerHlsDetectionTest`: Expanded test suite with real-world query tokens and dynamic CDN parameters.

### 🔌 Addon Fix Patch Prepared (`KITSUGI_PLUGINS_HDFC54_FM64_INFER_TYPE_FIX.patch`)
- Addon patch prepared for `Kitsugi-Plugins` (HDFilmCehennemi v54 & FilmMakinesi v64) fixing the silent `INFER_TYPE!!` NullPointerException in `linkTypeFor()` and preventing false drops of `.ts`-containing playlist URLs.

---

# Kitsugi Beta v2.4.305

## 🇹🇷 Türkçe

### 🎬 Close & Rapid Kaynakları İçin Yerleşik Çözücü (`KitsugiCdnJwExtractor`)
- **Yerleşik Close/Rapid Çözücüsü:** HDFilmCehennemi ve FilmMakinesi videolarının geldiği gömülü CDN ailesi (`closeload.*`, `rapidrame.*`, `rplayer.*`) için uygulama kendi yerleşik çözücüsünü kazandı. JWPlayer `dc_` JavaScript şifresi native olarak çözülüyor; JSON-LD `contentUrl` ve `master.txt`/`.m3u8` fallback'leri devrede.
- **En Yüksek Çözünürlüklü Varyant & Altyazı Ayıklama:** Master playlist listesinden en yüksek çözünürlüklü video varyantı otomatik seçilir; `Origin` ve `Referer` başlıkları GERÇEK yayın host'una sabitlenerek CDN tarafında yaşanan 5004 düşme hatası engellendi. JWPlayer `tracks:` bloğundaki altyazılar da ayıklanıp oynatıcıya sunuluyor.
- **FilmMakinesi "DUAL Rapid" Artık Link Üretiyor:** `rapidrame` iframe'leri eklenti tarafında `loadExtractor`'a bırakılıyor ama hiçbir eklenti bu host'u tanımıyordu; yerleşik çözücüler CloudStream `extractorApis` listesine kaydedildiği için eklenti sürümünden bağımsız olarak Rapid kaynakları da çözülüyor.

### 🔌 Bayat Eklenti Güncelleme Döngüsü Kırıldı & Canlılık Doğrulaması
- **Gerçek Eklenti Sürümleri ve Hash Senkronizasyonu:** `plugins.json` içindeki FilmMakinesi kaydı v62'ye, HDFilmCehennemi kaydı v52'ye (gerçek `.cs3` dosya boyutu ve SHA-256 değerleriyle) senkronlandı. Cihazlarda kalan eski sürümler artık güncelleme denetimine takılmadan doğrudan yeni sürümlere güncelleniyor.
- **HLS ve Progressive URL Ayrıştırma İyileştirmesi:** `PlayerMediaSourceFactory.isHlsUrl` sıralaması kesin manifest belirteçleri → progressive dosya uzantısı → zayıf yol/host belirteçleri olarak optimize edildi; CDN'lerin `/hls/` altındaki düz MP4/MKV dosyaları doğru MIME tipiyle oynatıcıya veriliyor.
- **Güçlendirilmiş Canlılık Doğrulaması:** HTTP HEAD canlılık kontrolü artık `Origin` dahil tüm başlıklarla yapılıyor; 404 dönen CDN'lerde kaynak hemen düşürülmeyip GET+Range ile ikinci şans verilerek çalışan kaynakların kaybolması engellendi.

### 🧪 Birim Testleri
- `KitsugiCdnJwExtractorTest`: Sentetik şifre çözme turu, varyant seçimi, altyazı filtreleme, origin/header üretimi test edildi.
- `PlayerHlsDetectionTest`: Yeni URL ayrıştırma sıralaması ve regresyon testleri genişletildi.

---

## 🇬🇧 English

### 🎬 Built-in Close & Rapid Extractors (`KitsugiCdnJwExtractor`)
- **Native Close/Rapid Extractor:** Added a dedicated in-app native extractor for embedded CDN domains (`closeload.*`, `rapidrame.*`, `rplayer.*`) serving HDFilmCehennemi and FilmMakinesi. Decrypts JWPlayer `dc_` JS payload natively, supporting JSON-LD `contentUrl` and `master.txt`/`.m3u8` fallbacks.
- **Adaptive Variant Selection & Header Pinning:** Automatically selects the highest-resolution stream from the master playlist and pins `Origin`/`Referer` headers to the actual streaming host (preventing CDN 5004 errors). Embedded JWPlayer `tracks:` subtitles are properly extracted.
- **FilmMakinesi "DUAL Rapid" Stream Resolution:** Rapidrame iframes are now resolved out-of-the-box via app-registered extractors in CloudStream `extractorApis`, regardless of plugin version.

### 🔌 Stale Plugin Update Loop Broken & Enhanced Liveness Checks
- **Accurate Hashes & Manifest Parity:** Synchronized `plugins.json` manifests to actual served upstream builds (FilmMakinesi v62, HDFilmCehennemi v52 with true size and SHA-256 digests), allowing devices on legacy builds to update seamlessly.
- **Refined Progressive / HLS URL Matching:** Strict detection hierarchy ensures progressive MP4/MKV files under `/hls/` paths are not falsely tagged as HLS streams.
- **Resilient Stream Probing:** Probing requests now transmit full header sets including `Origin`; sources returning 404 on HEAD are verified via GET+Range before being dropped.

### 🧪 Unit Tests
- `KitsugiCdnJwExtractorTest`: Tested decryption cycles, variant selection, subtitle extraction, and header generation.
- `PlayerHlsDetectionTest`: Extended regression coverage for URL detection rules.

---

# Kitsugi Beta v2.4.304

## 🇹🇷 Türkçe

### 🔊 Gerçek Ses Gecikmesi & Çoklu Format Ses Kazancı (`AudioDelayProcessor`, `GainAudioProcessor`)
- **Media3 İçin Donanımsal Ses Gecikmesi (Lip-Sync):** Media3/ExoPlayer arayüzünde eksik olan ses gecikmesi işlevi `AudioDelayProcessor` ile çözüldü. PCM (8/16/24/32-bit ve float) formatında kare hizalı örnek ekleme ve atlama ile ±5000 ms aralığında gecikme doğrudan ses hattında uygulanır.
- **Genişletilmiş PCM Kazanç Desteği:** `GainAudioProcessor` artık 8-bit (işaretsiz), 24-bit (little-endian) ve 32-bit PCM formatlarını destekler; kare sınırına uymayan artık baytlar kayıpsız taşınır.
- **Kaydırıcı Debounce Koruması:** Ses gecikmesi kaydırıcısı sürüklenirken diske sürekli yazma engellendi, 400 ms debounce ile DataStore performansı optimize edildi.

### ⚡ Media3 & MPV Motor İyileştirmeleri ve Akıllı Geçiş
- **Oynatma Hızının Korunması:** Kaynak veya bölüm değiştirildiğinde kullanıcının seçtiği oynatma hızı (`currentPlaybackSpeed`) sıfırlanmaz, yeni akışta aynen korunur.
- **ASS Handler Temizliği:** Media3 oynatıcı kapatıldığında (`release`) ASS altyazı işleyicisi bellekten temizlenir, sızıntılar önlenir.
- **Özellik Bazlı MPV'ye Akıllı Geçiş (`switchToMpvForFeature`):** Media3'ün desteklemediği gelişmiş özellikler (negatif ses gecikmesi, altyazı gecikmesi, altyazı hızı, video filtreleri) seçildiğinde oynatıcı mevcut konumu koruyarak otomatik olarak MPV motoruna geçer.
- **MPV Ekran Görüntüsü & Hazır Olma Koruması:** `screenshot-to-file` komutunun dosya yazması asenkron olarak 800 ms'ye kadar beklenir; dosya tam yüklenmeden `READY` durumu yayılmaz, arabelleğe alma göstergesi erken kapanmaz.
- **Gömülü Altyazı Tespiti:** Ekran görüntüsü alma panelinde MKV içi gömülü altyazılar da algılanır ve ekran görüntüsüne dahil edilebilir.

### 🛡️ Hata Toleransı & "Hâlâ İzliyor Musunuz?" Oturum Kontrolü
- **Kaynak Yeniden Çözümleme (`retryCurrentSource`):** Oynatıcı hata ekranındaki "Tekrar dene" butonu süresi dolmuş linklerde kaynağı eklenti üzerinden sıfırdan yeniden çözümler.
- **Etkileşimli "Hâlâ İzliyor Musunuz?" Uyarısı:** Oturum limiti dolduğunda ekranda şık bir onay diyaloğu açılır. Onaylandığında oynatma kaldığı yerden devam eder; dokunma ve çift dokunma jestleri hareketsizlik sayacını sıfırlar.
- **EXTERNAL & TV İyileştirmeleri:** Harici oynatıcı (`EXTERNAL`) seçildiğinde arka planda dahili motorun hazırlanması engellendi. Android TV için "Her zaman sor" tercihi diyalogla motor seçimini kullanıcıya bırakır.
- **Ölü Kod Temizliği:** Kullanılmayan `PlayerSourceController` ve `PlayerTrackController` sınıfları kaldırılarak ViewModel mimarisi sadeleştirildi.

---

## 🇬🇧 English

### 🔊 True Audio Delay & Multi-Format Audio Gain (`AudioDelayProcessor`, `GainAudioProcessor`)
- **Native Lip-Sync Audio Delay for Media3:** Media3/ExoPlayer lack a native audio delay API. Implemented `AudioDelayProcessor` operating directly in the PCM audio pipeline (supporting 8/16/24/32-bit & float) with sample padding and frame-aligned skipping within a ±5000 ms range.
- **Expanded PCM Gain Formats:** `GainAudioProcessor` now supports 8-bit unsigned, 24-bit little-endian, and 32-bit PCM audio streams while preserving residual fractional bytes across buffer boundaries.
- **Debounced Audio Delay Persistence:** Dragging the audio delay slider now debounces DataStore writes by 400 ms to eliminate unnecessary disk I/O.

### ⚡ Media3 & MPV Engine Enhancements & Smart Hand-off
- **Playback Speed Retention:** User-selected playback speed (`currentPlaybackSpeed`) is consistently retained across source, server, and episode switches.
- **ASS Handler Cleanup:** Explicitly unregisters ASS subtitle handler callbacks on player `release()`, preventing memory leaks.
- **Feature-Driven MPV Handoff (`switchToMpvForFeature`):** Selecting features unsupported by Media3 (negative audio delay, subtitle delay, subtitle speed, video filters) automatically switches the active engine to MPV while preserving playback position.
- **MPV Screenshot & Ready State Guards:** Waits up to 800 ms asynchronously for screenshot disk flush; prevents premature `READY` state broadcast before files finish loading.
- **Embedded Subtitle Detection:** Screenshot capture dialog now recognizes embedded MKV text tracks for subtitle overlay rendering.

### 🛡️ Resilient Error Recovery & Interactive Still-Watching
- **Fresh Source Re-Resolution (`retryCurrentSource`):** Clicking "Retry" on player error screens fully re-resolves the underlying stream link via its addon provider.
- **Interactive Still-Watching Dialog:** Replaced silent timeout behavior with a full dialog prompt; tapping confirm resumes playback, and user touch/double-tap gestures reset the inactivity timer.
- **EXTERNAL & Android TV Parity:** Prevents internal engine preparation when `EXTERNAL` player is requested. Android TV "Always Ask" displays a native engine selection dialog.
- **Dead Code Removal:** Deleted unused legacy `PlayerSourceController` and `PlayerTrackController` classes, streamlining the ViewModel orchestration pipeline.

---

# Kitsugi Beta v2.4.303

## 🇹🇷 Türkçe

### 🎬 HDFilmCehennemi & FilmMakinesi Oynatıcı ve Kaynak Düzeltmeleri
- **Düz MP4 Dosyalarının HLS Sanılması Engellendi (`KN-1`):** `PlayerMediaSourceFactory.isHlsUrl` fonksiyonunda host listesi (`closeload`, `rapidrame`, `hdfilmcehennemi`, `filmmakinesi` vb.) dosya uzantısından önce kontrol edildiği için bu sunuculardan gelen düz `.mp4` ve `.mkv` dosyaları HLS MIME türüyle (`application/x-mpegURL`) ExoPlayer'a veriliyordu. Sıralama düzeltilerek önce gerçek manifest belirteçleri (`m3u8`, `master.txt`, `/hls/`), ardından `PROGRESSIVE_PATH_REGEX` (`.mp4/.mkv/.webm/.avi/.m4v`) kontrolü sağlandı. Düz MP4 videoları artık sorunsuz oynatılıyor.
- **`loadLinks` Zaman Aşımı Bütçesi Genişletildi (`KN-2`):** HDFilmCehennemi ve FilmMakinesi gibi her sunucu ve iframe için sıralı istek atan eklentilerde bağlantı çözümleme süresi aşama bazında 25 saniyeden **60 saniyeye**, sağlayıcı genel bütçesi ise 75 saniyeden **100 saniyeye** çıkarıldı. Böylece kaynakların süre yetersizliği nedeniyle düşmesi ve 0–1 kaynak bulunması sorunu giderildi.
- **Dublaj ↔ Orijinal Kaynak Değişiminde Kesintisiz Konum Koruma (`KN-4`):** Eskiden oynatıcıda dublaj veya orijinal ses/sunucu değiştirildiğinde yeni URL 0. saniyeden başlıyordu. Mevcut oynatma konumu `pendingSourceSwitchResumeMs` içinde saklanıp yeni kaynak başlatılırken `startPos` olarak aktarılıyor. Kaynak değiştirildiğinde izleme tam kaldığı yerden devam eder.
- **Harici Ses Akışları Desteği (`KN-3`):** CloudStream eklentilerinin bildirdiği ayrık ses akışları (`ExtractorLink.audioTracks`) artık atılmıyor; `StreamSource.externalAudioUrl` alanına kaydedilerek video ile birlikte ExoPlayer motoruna ses parçası olarak bağlanıyor.

### 📝 Altyazı URL Normalizasyonu ve Çok Dilli ISO Eşleme
- **HDFilmCehennemi Altyazı URL'sindeki Hatalı Sondaki `/` Temizlendi:** HDFilmCehennemi eklentisinin göreli altyazı yollarının sonuna eklediği gereksiz `/` karakteri (`.../altyazi/a.vtt/`) sunucularda 404 hatasına yol açıyordu. `normalizeCsSubtitleUrl` fonksiyonu eklenerek `.vtt`, `.srt`, `.ass` gibi uzantılardan sonraki sondaki eğik çizgi otomatik olarak temizlendi.
- **Türkçe İsimlendirilen Dillerin ISO Koduna Eşlenmesi:** Türk kaynaklarında (ör. Anizium) altyazı dil adları Türkçe olarak yazılmaktadır. "İngilizce", "Almanca", "Fransızca", "İtalyanca", "İspanyolca", "Rusça", "Arapça", "Japonca", "Korece" ve "Çince" adları doğru BCP-47 / ISO kodlarına (`en`, `de`, `fr`, `it`, `es`, `ru`, `ar`, `ja`, `ko`, `zh`) eşlendi.
- **Türkçe `İ` Harfi Birleşik Nokta (U+0307) Düzeltmesi:** Büyük `İ` harfi küçük harfe çevrildiğinde oluşan `i` + birleşik nokta karakteri temizlenerek "İngilizce" ve "İtalyanca" etiketlerinin doğru dille eşleşmesi garanti altına alındı.
- **Bilinmeyen Dillerin Türkçe Sayılması Engellendi (`KN-5`):** Tanınmayan yabancı dillerin varsayılan olarak `tr` etiketlenmesi (`contains("tr")` hatası) önlendi; bilinmeyen diller `und` (undetermined) olarak işaretleniyor. "Altyazı" ve "Dublaj" metinleri ise Türkçe kaynak kabul ediliyor.

### 🔌 Eklenti Tanımları ve Canlı Domain Güncellemeleri
- **HDFilmCehennemi Domaini .nl Olarak Güncellendi:** Park sayfasına düşen ve ölü durumda olan `hdfilmcehennemi.biz` alan adı, canlı ve çalışan `https://www.hdfilmcehennemi.nl` adresine güncellendi.
- **FilmMakinesi Embed Tanıma:** `closeload.filmmakinesi.to` alan adı tanınan gömülü oynatıcı listesine eklendi.
- **Eklenti Kaynak Eşitlemesi:** AnimeciX, FilmMakinesi ve HDFilmCehennemi eklenti tanımları canlı deponun son kararlı sürümlerine eşitlendi.

---

## 🇬🇧 English

### 🎬 HDFilmCehennemi & FilmMakinesi Player and Source Fixes
- **Prevented Progressive MP4 Files from Being Misidentified as HLS (`KN-1`):** In `PlayerMediaSourceFactory.isHlsUrl`, host domain checks (`closeload`, `rapidrame`, `hdfilmcehennemi`, `filmmakinesi`, etc.) previously ran before file extension checks, incorrectly assigning progressive `.mp4` and `.mkv` files the HLS MIME type (`application/x-mpegURL`). Reordered checks to prioritize explicit manifest indicators (`m3u8`, `master.txt`, `/hls/`), followed by `PROGRESSIVE_PATH_REGEX` (`.mp4/.mkv/.webm/.avi/.m4v`). Direct MP4 video sources now play seamlessly in ExoPlayer.
- **Extended `loadLinks` Timeout Budget (`KN-2`):** For providers executing sequential requests across multiple iframes/servers (like HDFC and FilmMakinesi), the stage timeout budget was increased from 25s to **60s**, and overall provider budget from 75s to **100s**, preventing stream links from prematurely timing out and returning 0 or 1 source.
- **Seamless Position Persistence on Dub ↔ Sub Source Switching (`KN-4`):** Switching between dubbed and original streams or changing servers previously reset playback position to second 0. Current playback timestamp is now preserved in `pendingSourceSwitchResumeMs` and supplied as `startPos` when preparing the new stream, resuming playback exactly where you left off.
- **External Audio Tracks Support (`KN-3`):** Disjoint audio tracks declared by CloudStream providers (`ExtractorLink.audioTracks`) are no longer discarded; they are retained in `StreamSource.externalAudioUrl` and piped into ExoPlayer alongside the video stream.

### 📝 Subtitle URL Normalization & Multilingual ISO Detection
- **Stripped Erroneous Trailing Slash from HDFilmCehennemi Subtitle URLs:** Fixed an issue where relative subtitle links appended with a trailing slash (`.../sub.vtt/`) caused HTTP 404 errors on upstream CDN servers. Added `normalizeCsSubtitleUrl` to trim trailing slashes following subtitle extensions (`.vtt`, `.srt`, `.ass`).
- **Mapped Turkish-Named Languages to ISO Codes:** Providers writing language names in Turkish (e.g. "İngilizce", "Almanca", "Fransızca", "İtalyanca", "Arapça", "Çince") are now mapped to standardized BCP-47 / ISO codes (`en`, `de`, `fr`, `it`, `ar`, `zh`, etc.).
- **Turkish `İ` Combining Dot (U+0307) Normalization:** Handled lowercase conversion of Turkish dotted capital `İ` (`i` + U+0307 combining dot) to ensure language tags like "İngilizce" reliably resolve to `en`.
- **Prevented Unknown Languages from Defaulting to Turkish (`KN-5`):** Eliminated the loose fallback where any unidentified language was tagged as `tr`, tagging them as `und` instead while preserving their human-readable name in the player selector.

### 🔌 Plugin Manifests & Live Domain Updates
- **Updated HDFilmCehennemi Domain to .nl:** Switched from the parked/dead `hdfilmcehennemi.biz` address to the active `https://www.hdfilmcehennemi.nl` domain.
- **FilmMakinesi Embed Support:** Added `closeload.filmmakinesi.to` to recognized video embed domains.
- **Plugin Manifest Parity:** Synchronized AnimeciX, FilmMakinesi, and HDFilmCehennemi plugins in `plugins.json` with their latest stable upstream releases.

---

# Kitsugi Beta v2.4.302

## 🇹🇷 Türkçe

### ➕ "Tümünü Gör" Sayfalarında Hızlı Listeye Ekleme Kısayolu (Grid & Liste Görünümü)
- **Kompakt Kart Rozeti (`KitsugiQuickAddButton`):** Posterin sağ üst köşesinde (TopEnd) yer alan 30dp yuvarlak, şık ve yer kaplamayan kısayol butonu.
- **Grid ve Liste Görünümünde Kusursuz Uyum:** `KitsugiExploreMediaCard` (grid — TV/yatay/dikey varyantları), `KitsugiRankingMediaCard` ve `DetailedSeasonalMediaCard` (liste görünümü) bileşenlerinin hepsinde aynı poster köşesinde durur; layout geometrisinde sıfır piksel kayma oluşturur.
- **Durum Duyarlı & Düzenleme Entegrasyonu:** İçerik listede değilse tema vurgulu zemin üzerinde beyaz `+` ikonuyla doğrudan ekleme yapar (Bangumi koleksiyon akışı dahil); içerik zaten kütüphanenizde ise **yeşil zeminli tik** rozeti gösterir ve tıklandığında doğrudan düzenleme panelini açar.
- **Android TV Desteği:** `tvClickable` ile D-pad odağı ve odak çerçevesi tam desteklenir.
- **Başlıkta Toplu Ekleme Butonu:** Grid, liste ve sayfa kaydırılırken beliren yüzen başlıkta filtre butonunun yanına toplu ekleme ikonu (`Icons.Rounded.PlaylistAdd`) eklendi. Tek tıkla sayfadaki tüm sonuçlar listeye eklenir; liste boşken buton otomatik olarak pasifleşir.
- **Birleşik ViewModel Mimarisi & Özet Bildirim:** `AppViewModel` içinde tekil ve toplu ekleme aynı doğrulama çekirdeğini (`insertApiSelection`) paylaşır. Toplu ekleme sonucunda ekran bildirim yağmuruna tutulmaz; tek bir özet bildirim (*"N eklendi • M zaten listede • K eklenemedi"*) sunulur.

### 📌 "Listem" Kaynak Seçimi & Sekme Kalıcılığı Düzeltmesi
- **Açılış Yarış Durumu (Race Condition) Çözüldü:** `loadFilters` çağrısı `LaunchedEffect` gecikmesinden çıkarılarak ilk kompozisyondan önce senkron çalıştırıldı. Listem ekranının pager 0 ("Tümü") değeriyle açılıp kullanıcının kayıtlı seçimini ezmesi sorunu tamamen giderildi.
- **Koşulsuz Sıra Kayması Kaldırıldı:** Eski sürümlerden kalan `migrateLegacyMyListTabIndex` her açılışta koşulsuz çalışarak sekme indeksini bir yana kaydırıyordu; bu göç kodu kaldırılarak kullanıcının son seçtiği kaynak sekmesi (Bangumi, MAL, AniList vb.) tam olarak korunur hâle getirildi.

### 🖼️ Keşfet Vitrini Nuvio Tarzı Yeniden Tasarlandı (`HeroVitrinLayout`)
- **Her Zaman Tam Kaplama (`ContentScale.Crop`):** Vitrin görseli artık kutusuna tam oturur; oran farkından dolayı yanlarda boş kutu veya ince şerit oluşması engellendi.
- **Dikey Telefonda Ayrılmış Bilgi Yuvası:** Üstte sabit oranlı görsel şeridi, altta başlık, logo, alt başlık ve meta satırları için ayrılmış sabit alan sunulur. Başlık ve logo hiçbir zaman görselin üzerine binmez.
- **Bulanık Ambient Arka Plan Katmanı:** Keskin görselin altında kalan alan aynı görselin bulanık kopyasıyla doldurulur (Coil disk önbelleği kullanıldığı için ek ağ trafiği yaratmaz).
- **Nuvio'dan Alınan Scrim'ler:** Nuvio Mobile dikey gradyanı ve Nuvio TV sol yatay gradyanı uygulanarak metin okunabilirliği her arka planda garantiye alındı.
- **Görselden Bağımsız Geometri & Zıplamasız İskelet:** Bant yüksekliği cihaz boyutlarına ve yazı ölçeğine göre türetilir; görsel yüklenirken ekran boyutu değişmez veya zıplamaz.

### 🎬 İzle & Akış (Stream) Eklenti Teşhis İyileştirmeleri
- **Açıklayıcı Hata Mesajları:** CloudStream eklenti yükleme hatalarında genel "DEX/manifest hatası" yerine gerçek kök neden (`NoClassDefFoundError`, `ClassNotFoundException` vb.) doğrudan kartta ve "Yeniden dene" akışında gösterilir.
- **Akış ve Başlık Eşleştirme:** `CsTitleMatcher` başlık ve yıl normalizasyonu, Stremio akış yönlendirmesi (`StremioStreamRouting`, `StremioAddonRoutes`) ve Wikidata IMDB ayrıştırıcı iyileştirmeleri yapıldı.

---

## 🇬🇧 English

### ➕ Quick "Add to List" Shortcut in "See All" Pages (Grid & List Views)
- **Compact Poster Badge (`KitsugiQuickAddButton`):** A sleek, non-intrusive 30dp circular action button placed at the top-right (TopEnd) corner of media posters.
- **Flawless Parity Across Views:** Integrated identically into `KitsugiExploreMediaCard` (grid view — TV, landscape, portrait variants), `KitsugiRankingMediaCard` and `DetailedSeasonalMediaCard` (list view), taking up zero extra layout footprint.
- **State-Aware & Edit Panel Access:** Shows a `+` badge with accent tint to add new items directly (supporting Bangumi flows); transforms into a **green checkmark badge** when already in your list, tapping it immediately opens the edit dialog.
- **Android TV Ready:** Fully navigable via D-pad with focus highlight using `tvClickable`.
- **Batch "Add All to List" Action:** Added a dedicated batch action button (`Icons.Rounded.PlaylistAdd`) next to the filter button in all three headers (grid, list, and floating sticky header). Adds all visible items in a single click and disables automatically when empty.
- **Unified Architecture & Aggregated Notifications:** Single and batch addition share the same robust validation core (`insertApiSelection`) in `AppViewModel`. Displays a single clean summary notification (*"N added • M already in list • K could not be added"*) instead of spamming alerts.

### 📌 "My List" Tab Selection Persistence Fix
- **Race Condition Resolved:** `loadFilters` now executes synchronously prior to first composition, preventing the pager from initializing at 0 ("All") and overwriting user preferences.
- **Eliminated Unconditional Tab Shift:** Removed legacy index migration that shifted the active tab index on every app launch. Your selected library source (Bangumi, MAL, AniList, etc.) is reliably preserved across app restarts.

### 🖼️ Explore Showcase Redesigned (Nuvio-Style Layout - `HeroVitrinLayout`)
- **Full Coverage (`ContentScale.Crop`):** Showcase artworks now consistently fill their frame, eliminating side pillarboxing and collapsed thin strips.
- **Dedicated Information Slot on Phones:** Features a fixed-aspect visual band on top with a dedicated metadata/title zone below, ensuring logos and titles never obscure the artwork.
- **Ambient Blurred Layer:** Spaces below sharp artwork are smoothly filled with a blurred copy using Coil's cache with zero bandwidth penalty.
- **Nuvio Gradient Scrims:** Incorporates Nuvio Mobile's vertical gradient and Nuvio TV's left-hand horizontal gradient for crystal-clear readability.
- **Device-Driven Geometry:** Container height is calculated strictly from screen geometry and font scale; loading skeletons match the final layout to prevent layout jumping.

### 🎬 Stream & Plugin Diagnostic Improvements
- **Transparent Plugin Errors:** CloudStream plugin loader now surfaces the exact root cause (`NoClassDefFoundError`, `ClassNotFoundException`, etc.) on the card and retry flows instead of a generic DEX/manifest failure.
- **Matcher & Routing Enhancements:** Optimized `CsTitleMatcher` title/year heuristics, refined Stremio stream routing (`StremioStreamRouting`, `StremioAddonRoutes`), and improved Wikidata IMDb parsing.

---

# Kitsugi Beta v2.4.301

## 🇹🇷 Türkçe

### 🖼️ Keşfet Vitrini Yeniden Tasarlandı (Nuvio Tarzı Yerleşim)

- **Kök Neden Düzeltildi:** Vitrin görseli `ContentScale.Fit` ile çok geniş bir kutuya sığdırılıyordu. Bu yüzden 16:9 bir görsel **yanlarda boş kutu** bırakıyor, 3:1 bir banner ise **ince bir şeride** düşüp gerisini bulanık olmayan düşük opaklıklı bir "hayalet kopya" ile dolduruyordu. Artık görsel **her zaman kutuyu tamamen kaplıyor** (`ContentScale.Crop`); kaynak ne olursa olsun vitrin aynı, tutarlı görünüyor.
- **Yeni Yerleşim Politikası (`HeroVitrinLayout`):** Dikey telefonda üstte oranı sabit bir **görsel şeridi**, altta başlık/logo, alt başlık ve meta satırları için **ayrılmış sabit yuva** var. Başlık ile logo artık görselin üstüne binmiyor. Yatay mod, tablet ve TV'de ise görsel bandın tamamını kaplıyor ve metin Nuvio TV'deki gibi gradyan scim'in üzerine bindiriliyor.
- **Bulanık Ortam Katmanı:** Keskin görselin altında kalan alan artık aynı görselin **bulanık** kopyasıyla doluyor. Boş kutu, yarım görsel veya bulanık olmayan ikinci kopya bir daha görünmüyor. Coil'in disk cache'i yalnızca URL'e göre çalıştığı için bu katman **ek internet tüketimi yapmıyor**.
- **Nuvio'dan Alınan Scrim'ler:** Nuvio Mobile'in dikey gradyanı ve Nuvio TV'nin sol yatay gradyanı uygulandı; durum çubuğu için üstte hafif bir koyulaştırma eklendi. Metin her kaynakta okunur kalıyor.
- **Ölçüler Görselden Bağımsız:** Bant yüksekliği, görsel şeridi ve metin yuvası yalnızca cihaz geometrisinden (ve yazı ölçeğinden) türetiliyor. Görselin oranı, yükleme durumu veya kaynak tamamlanması sayfayı kaydıramıyor; büyük yazı ölçeğinde bant gerektiği kadar büyüyor.
- **Yükleme İskeleti Eşitlendi:** Vitrin iskeleti artık gerçek vitrinle aynı yerleşimi kullanıyor; görsel geldiğinde sayfa zıplamıyor.
- **Dokunulmayanlar:** Otomatik geçiş, sayfa göstergeleri, logo yuvası, kaynak rozeti, TV kumanda odağı, yetişkin bulanıklığı ve kaynak kotaları aynen korundu.

---

## 🇬🇧 English

### 🖼️ Explore Showcase Redesigned (Nuvio-Style Layout)

- **Root Cause Fixed:** The showcase artwork was fitted (`ContentScale.Fit`) into a very wide box, so a 16:9 image left **empty bars on both sides** while a 3:1 banner collapsed into a **thin strip** backed by a low-opacity, unblurred "ghost copy". The artwork now **always fills its box** (`ContentScale.Crop`), so every source renders identically and consistently.
- **New Layout Policy (`HeroVitrinLayout`):** On portrait phones the artwork occupies a fixed-aspect **strip at the top**, while the title/logo, subtitle and meta rows live in a **dedicated fixed zone below**. The title and logo no longer overlap the artwork. In landscape, on tablets and on TV the artwork fills the whole band and the metadata is layered over a gradient scrim, exactly like Nuvio TV.
- **Blurred Ambient Layer:** The area beneath the sharp artwork is now filled with a **blurred** copy of the same image. Empty boxes, half images and unblurred duplicates are gone. Because Coil's disk cache is keyed by URL only, this layer adds **no extra network traffic**.
- **Nuvio-Derived Scrims:** Nuvio Mobile's vertical gradient and Nuvio TV's left-hand horizontal gradient are applied, plus a subtle top scrim for the status bar. Metadata stays readable on every source.
- **Image-Independent Geometry:** Band height, artwork strip and text zone are derived only from device geometry (and font scale). Artwork aspect, loading state or a late-arriving source can never shift the layout; with a large font scale the band grows instead of clipping.
- **Matching Loading Skeleton:** The showcase skeleton now uses the very same layout policy, so the screen does not jump when artwork arrives.
- **Unchanged:** Auto-advance, page indicators, the logo slot, the source pill, TV remote focus, adult-content blur and the per-source quotas all behave exactly as before.

# Kitsugi Beta v2.4.300

## 🇹🇷 Türkçe

### 🎭 Çok Dilli Spoiler Motoru & Tür/Etiket Paritesi (AniList, Bangumi, MAL, Shikimori, Kitsu, Simkl, TMDB, TVMaze)
- **AniList ile Birebir İki Katmanlı Spoiler Mantığı:** Desteklenen tüm kaynaklarda (AniList, Bangumi, MAL, Shikimori, Kitsu, Simkl, TMDB, TVMaze) hem Etiketler hem de Türler bölümleri AniList'in spoiler standartlarına tam uyumlu hâle getirildi.
- **Ortak Çok Dilli Doğal Spoiler Tespiti (`KitsugiSpoilerRules`):** Bangumi Tag Sözlüğü (907 grup, CJK/EN/TR) ve çok dilli anahtar kümesi üzerinden hikâye sonunu, karakter ölümünü, ihanet/NTR'yi veya kritik ters köşeleri açığa vuran temalar (*Ölüm/Death/便当, İntihar/Suicide/自杀, İhanet/Betrayal/背叛, Sürpriz Gelişme/Plot Twist/神展开, Zaman Döngüsü/Time Loop/轮回, Hafıza Kaybı/Amnesia/失忆, Trajedi/Tragedy/悲剧, Kötü Son/Bad Ending/全灭, Yandere/病娇* vb.) anında spoiler olarak işaretlenir.
- **Türler (Genres) Bölümünde Spoiler Kontrolü & Gizleme:** Türler listesine sızan alt temalar (Tragedy, Time Loop vb.) varsayılan olarak gizlenir. Kart başlığında `🔒 Spoiler'ları Göster / Spoiler'ları Gizle` anahtarı yer alır; spoiler türler açıldığında kırmızı (`KitsugiColors.AccentRed`) çip olarak çizilir. *Aksiyon, Komedi, Dram, Romantik, Fantezi, Bilim Kurgu* gibi ana türler asla yanlışlıkla gizlenmez.
- **Etiketler (Tags) Kartı İyileştirmesi:** Kart daraltılmış (`!expanded`) moddayken bile "Spoiler'ları Göster" tıklandığında 10. sıradan sonraki tüm kırmızı spoiler etiketler anında görünür hale gelir. Mükerrer etiket birleştirmesinde `isSpoiler` bayrağı ve AniList `% rank` alaka düzeyi korunur.
- **AniList GraphQL & Çapraz Zenginleştirme:** AniList sorgusuna `isGeneralSpoiler`, `id`, `description`, `category` eklendi. Bangumi, MAL, Shikimori, Kitsu, Simkl ve TMDB detayları AniList'in yapıma özel spoiler etiketleriyle zenginleştirilir; birincil kaynağın özgün etiket adı korunarak çipe tıklandığında doğru kaynak araması yapılması sağlanır.
- **Kitsu & TMDB Etiket Desteği:** Kitsu anime/manga isteklerine `?include=categories` eklenerek kategori ve etiketler çekildi; TMDB istemcisine `keywords` eklenerek anahtar kelimeler spoiler denetimli etiketlere dönüştürüldü.

### 🖼️ Yenilenen Keşfet Vitrini (Yatay Görsel Doğrulama & Akıcı HorizontalPager Kaydırma)
- **Kitap & Manga Türleri Vitrinden Ayrıldı:** Manga, manhwa/manhua ve romanlar vitrin havuzundan elendi; normal katalog ve keşfet raflarındaki yerleri aynen korundu.
- **Dengeli Kota Dağılımı:** "Tümü" görünümünde mevcut her kaynak/bölümden 1 rastgele öğe; "Tek Kaynak" görünümünde ise her bölümden 2 rastgele öğe seçilir ("Yakında Yayında", popüler, trend, en yüksek puanlı, vizyondaki ve sezon bölümleri dahil).
- **Gerçek Yatay Görsel Şartı (`HomeHeroArtworkLoader`):** Yalnızca gerçek boyutları yatay (`width > height`) olan ve hatasız yüklenebilen görseller kabul edilir. Kare, dikey afiş veya bozuk görseller elenerek aynı bölümün diğer rastgele adayları denenir; vitrin dikey posterle doldurulmaz.
- **Sabit Yükseklik & Zıplamasız Yerleşim:** Vitrinin yüksekliği artık görsele göre değişmez; cihaz bazlı sabit yükseklik kullanılır. Görsel, başlık, logo ve meta veriler gerçek `HorizontalPager` ile tek bir sayfada birlikte pürüzsüzce kayar; eski katman değiştirme ve sıçramalı sürükleme tamamen kaldırıldı.
- **Kararlı Logo Yuvası:** Logo yüklenirken alanı sabit kalır; yüklenemezse veya logo kapalıysa başlık metni bozulmadan ekranda kalmaya devam eder.
- **Akıllı Otomatik Geçiş:** Otomatik kaydırma dokunma, sürükleme ve TV kumanda odağı sırasında duraklar; sona gelindiğinde başa zıplamak yerine tek adımlı ters yön geçişiyle akıcılığı korur.

---

## 🇬🇧 English

### 🎭 Multilingual Spoiler Engine & Genre/Tag Parity (AniList, Bangumi, MAL, Shikimori, Kitsu, Simkl, TMDB, TVMaze)
- **Two-Tier AniList Spoiler Standard Across All Sources:** Unified spoiler logic adhering to AniList's standards across AniList, Bangumi, MyAnimeList, Shikimori, Kitsu, Simkl, TMDB, and TVMaze for both Genres and Tags sections.
- **Intrinsic Multilingual Spoiler Detection (`KitsugiSpoilerRules`):** Powered by Bangumi Tag Dictionary (907 canonical groups, CJK/EN/TR) and multilingual patterns detecting major plot disclosures (*Death, Suicide, Betrayal, Plot Twist, Time Loop, Amnesia, Tragedy, Bad Ending, Yandere*, etc.) across all languages.
- **Genres Card Spoiler Protection & Toggle:** Sub-themes mixed into genres (such as Tragedy or Time Loop) are hidden by default. A `🔒 Show Spoilers / Hide Spoilers` button appears in the card header, revealing spoiler genres as highlighted red chips. Core genres (*Action, Comedy, Drama, Romance, Fantasy, Sci-Fi*) are guaranteed to remain visible.
- **Tags Card Expansion & Deduplication Fix:** Clicking "Show Spoilers" now reveals all spoiler tags immediately even when the card is collapsed. Deduplication preserves `isSpoiler = true` and AniList percentage rank.
- **AniList GraphQL Expansion & Cross-Enrichment:** Added `isGeneralSpoiler`, `id`, `description`, `category` to AniList queries. Bangumi, MAL, Shikimori, Kitsu, Simkl, and TMDB items are cross-enriched with AniList spoiler tags while preserving original source tag names for native search clicks.
- **Kitsu & TMDB Keywords Integration:** Added `?include=categories` to Kitsu anime/manga detail queries and added `keywords` to TMDB to parse spoiler-checked tags.

### 🖼️ Revamped Explore Showcase (Horizontal Artwork Validation & Smooth Pager)
- **Books & Manga Excluded from Showcase:** Manga, manhwa, manhua, and light novels are filtered out of the showcase carousel while preserving their standard catalog shelves.
- **Balanced Showcase Quotas:** "All Sources" selects 1 random item per source/shelf; "Single Source" selects 2 random items per shelf (including "Coming Soon", Trending, Top Rated, Airing, and Seasonal).
- **Strict Landscape Artwork Validation (`HomeHeroArtworkLoader`):** Only images decoded with `width > height` are accepted. Square, vertical posters, or failing images are skipped in favor of alternative candidates from the same shelf without vertical poster fallbacks.
- **Fixed Height & Seamless Motion:** Viewport height remains constant regardless of image aspect ratio. Artwork, title, logo, and meta information glide together in a unified native `HorizontalPager`.
- **Stable Logo Slot:** Reserved logo space prevents layout shifts; failure to load or disabled logos seamlessly preserves the text title.
- **Interactive Auto-Scroll:** Pauses during user drag, touch, or TV focus, reversing direction smoothly at endpoints instead of hard rewinding.

---

# Kitsugi Beta v2.4.290

## 🇹🇷 Türkçe

### Depolama Ekranına "Wiki Önbelleği" Kalemi
- **Görünür Wiki Verisi:** Ayarlar > Depolama ekranında artık **Wiki önbelleği** kalemi de yer alır; Fandom ve Wikipedia maddeleri, bilgi kutuları, karakter sayfaları ve bölüm içerikleri için tutulan kayıtların boyutu ve dosya sayısı buradan görülür.
- **Güvenli Temizlik:** Kalemin "Temizle" eylemi yalnızca **yeniden üretilebilir** wiki kayıtlarını (Room `persistent_details_cache` tablosundaki `fandom:v1:` kayıtları) siler; kullanıcının "doğru / hayır, yanlış" eşleşme kararları korunur. Silinen içerik bir sonraki açılışta ağdan yeniden çekilir.
- **Doğru Ölçüm:** SQLite dosyası silme sonrası küçülmediği için (VACUUM gerekir) kalem, disk dosyası yerine **mantıksal veri boyutunu** raporlar; "Temizle" sonrası geri kazanılan miktar da bu ölçümden hesaplanır. 30 günden eski kayıtlar arka plan bakımında otomatik atılmaya devam eder.

### Uygulama Hakkında → Sürüm Notları
- **GitHub'dan Son Sürüm Notları:** "Uygulama Hakkında" ekranına **Sürüm Notları** kartı eklendi. Kart, GitHub Releases üzerindeki **en son** sürümün başlığını, yayın tarihini ve değişiklik notlarını markdown işaretlerinden arındırılmış biçimde gösterir.
- **Kurulu Sürüm Güncelken de Görünür:** Güncelleme akışı yalnızca yeni sürüm varsa pencere açtığı için, uygulama güncelken sürüm notları hiçbir yerde görünmüyordu. Yeni kart, yeni sürüm koşulu aramaz; "Yeni sürüm hazır" / "Kurulu sürüm en güncel" rozetiyle durumu belirtir.
- **İstek Sınırı Koruması:** Son başarılı yanıt 6 saat süreyle diskte önbelleklenir (GitHub kimliksiz istek sınırı saatte 60 istek); ekran her açıldığında ağa gidilmez. "Yenile" düğmesi önbelleği atlayarak zorla tazeler, "GitHub'da tüm sürümler" bağlantısı tarayıcıda sürüm listesini açar.
- **Ortak Görünüm:** Sürüm notları, güncelleme penceresindeki biçimlendirmenin aynısıyla (başlık, madde imi, ayraç sadeleştirmesi) gösterilir.

### Wiki'de "Orijinal / Çeviri" Geçişi Her Yere
- **Açılır Wiki Sayfası Bilgi Kutusu:** Bilgi kutusunda artık uygulama içi çeviri varsa, harici çeviri uygulamasını açan düğmenin **hemen solunda** "Orijinal" düğmesi çıkar; dokununca bilgi kutusunun tüm alanları wiki'nin özgün diline döner ("Çeviri" ile geri alınır). Bu, karakter wiki sekmesindeki davranışla birebir aynıdır.
- **Açılır Wiki Sayfası Açıklaması ve Bölümleri:** Seçili wiki maddesinin açıklaması ile açılan her madde bölümünde de aynı geçiş düğmesi eklendi; "Orijinal" seçiliyken bölüm gövdesi çevrilmemiş metni gösterir (çeviri bellekte kalır, ağa tekrar gidilmez).
- **Wikipedia Kaynakları Dahil:** Kaynak Fandom ya da Vikipedi (TR) olsun aynı bileşenler kullanıldığı için geçiş düğmesi her iki kaynakta da çalışır; kaynak değiştirildiğinde durum ilgili anahtara göre ayrı tutulur.
- **Bilgi Kutusunu Kopyala:** Açılır wiki sayfasının bilgi kutusuna "bilgi kutusunu kopyala" düğmesi eklendi. Kopyalanan metin yalnızca ekranda görünen alanları içerir, spoiler koruması açıkken gizli alanların değeri `[gizli]` ile maskelenir ve "Orijinal" seçiliyken çeviriler değil özgün metin kopyalanır.

### Hesap Girişi (AniList / MyAnimeList / Simkl) Uygulama İçi Ekran Kök Neden Düzeltmesi
- **Uygulama İçi Giriş Artık Çalışıyor:** Hesap bağlantıları ekranından açılan uygulama içi tarayıcı benzeri OAuth penceresinde giriş yapılamıyor, Cloudflare/MAL WAF "Gerçek kişi misiniz?" doğrulamasında takılıyor ve izin verilmiyordu; yalnızca harici tarayıcıdan giriş çalışıyordu. Bu sürümde uygulama içi akış kök nedeninden düzeltildi.
- **Sahte User-Agent Kaldırıldı (Kök Neden):** OAuth WebView'ine sabit "Chrome/124 (Android 13, Pixel 7)" sahte User-Agent'ı gönderiliyordu. Cloudflare ve MAL WAF, isteğin gerçek TLS + JavaScript parmak izini beyan edilen UA ile karşılaştırır; uyumsuzluk bot sinyalidir ve doğrulama döngüsü üretir. Artık sistem WebView'inin GERÇEK varsayılan UA'sı (cihazın gerçek WebView sürümü ve modeli) kullanılır — parmak iziyle tutarlı olduğu için harici tarayıcıdakine yakın geçiş sağlar. Aynı düzeltme genel uygulama içi web penceresine (TV QR girişi, eklenti/bildirim/profil sayfaları) de uygulandı.
- **OAuth Geri Dönüşü Tek Noktadan, Çift Takas Korumasıyla:** `malapp://auth` geri dönüşü daha önce iki ayrı kanalda (`onPageStarted` + `shouldOverrideUrlLoading`), korumasız ve kopyala-yapıştır kodla işleniyordu; çift tetiklenmede kod iki kez takas edilip "kod zaten kullanılmış" hatası üretebiliyordu. Artık tek bir korumalı işleyici (`handleAuthRedirect`) üzerinden yürür.
- **JS Yönlendirmeleri de Yakalanıyor:** `doUpdateVisitedHistory` kancası eklendi; bazı WebView sürümlerinde `window.location` ile yapılan OAuth geri dönüşleri `shouldOverrideUrlLoading`'e düşmüyordu ve izin sonrası bağlantı sessizce başarısız oluyordu.
- **Yanlış Hata Mesajı Engellendi:** `malapp://` gibi özel şemalı geri dönüşlerde WebView'in ürettiği `ERR_UNKNOWN_URL_SCHEME` artık "Sayfa yüklenemedi" hatası olarak gösterilmez; dönüş yakalanır, yükleme durdurulur ve token takası yapılır.
- **Etkilenen Kaynaklar:** AniList, MyAnimeList, Simkl (OAuth penceresi) ve TV QR girişinin telefon tarafındaki uygulama içi ekranı. Kitsu (kullanıcı adı/şifre), Shikimori, Bangumi ve Simkl PIN akışı zaten harici tarayıcıyı kullandığı için etkilenmiyordu.
- **MAL Tek Oturum Akışı Korundu:** Giriş sonrası OAuth jetonu + web oturumu doğrulaması ve bildirimler için MAL çerez yakalama mekanizması aynen çalışır; çerezler WebView CookieManager'dan okunmaya devam eder.

### Oynatıcı ve Ayar Kaydırıcılarında "Varsayılana Sıfırla" Düğmesi
- **Tek Dokunuşla Fabrika Ayarı:** Oynatıcı genel ayarları (kaydırma hassasiyeti, çift dokunma arama süresi, basılı tutma oynatma hızı, parlaklık/ses hareket hassasiyeti, mini oynatıcı boyutu vb.), tercihler diyaloğu ve manga okuyucu parlaklık kaydırıcılarına doğrudan tıklanabilir sıfırlama butonu eklendi.
- **Kaydırıcı Üzerinde Çift Dokunma:** Ayar değerini hızlıca orijinal değerine döndürmek için kaydırıcı çubuğuna çift dokunma kısayolu da desteklenir.

### +18 Yetişkin İçerik Görünürlük & Bulanıklaştırma Uyumlaştırması
- **Tüm Kaynaklarda Bütünlük:** Eklenti (Cloudstream CS3), manga uzantıları, takvim ve yapım ilişkileri dahil tüm dış kaynaklarda +18 sansür/bulanıklık ve göster/gizle anahtarı tam uyumlu hale getirildi.
- **Korumalı Görsel Başlıkları:** +18 içeriklerin afiş ve görselleri yüklenirken Cloudstream ve manga sunucularının talep ettiği Referer ve User-Agent başlıkları (Coil 3 NetworkHeaders) korundu.

### MyAnimeList (MAL) Sayfalama, Kulüpler ve Ağ Hatası Dayanıklılığı
- **Hata ve Yeniden Deneme (Retry):** MAL arkadaş listesi, incelemeler ve öneriler çekilirken ağ hatası alındığında tüm listenin kesilmesi önlendi; liste altında yerinde "Devamı yüklenemedi · Tekrar dene" butonu sunuldu.
- **Kulüpler (Clubs) Sekmesi ve Sayfalama:** MAL kullanıcı profillerine kullanıcının üye olduğu kulüpleri listeleyen sekme ve çok sayfalı kulüp yükleme desteği eklendi.
- **Dayanıklı MAL Bildirimleri:** MAL web oturumu bildirimlerinde daha eski bildirimleri yüklerken bağlantı kesilirse yerinde tekrar deneme desteği getirildi; sayfa gezintisi güçlendirildi.

---

## 🇬🇧 English

### New "Wiki Cache" Entry on the Storage Screen
- **Visible Wiki Data:** Settings > Storage now lists a **Wiki cache** entry showing the size and file count of the records kept for Fandom/Wikipedia articles, infoboxes, character pages and section contents.
- **Safe Clearing:** Its "Clear" action removes only **reproducible** wiki records (`fandom:v1:` rows in the Room `persistent_details_cache` table) and preserves the user's "correct / wrong match" decisions. Cleared content is re-fetched from the network on the next visit.
- **Accurate Measurement:** Because SQLite does not shrink after deletes (VACUUM is required), the entry reports **logical data size** instead of the database file size, and the reclaimed amount is derived from the same measurement. Records older than 30 days are still pruned automatically by background maintenance.

### About the App → Release Notes
- **Latest Notes Straight from GitHub:** A **Release Notes** card was added to the "About the App" screen. It shows the title, publication date and markdown-stripped changelog of the **latest** GitHub release.
- **Shown Even When Up to Date:** The update flow only opens a dialog when a newer version exists, so release notes were invisible on an up-to-date install. The new card does not require a newer version and labels the state with a "New version available" / "Installed version is up to date" badge.
- **Rate-Limit Friendly:** The last successful response is cached on disk for 6 hours (GitHub's unauthenticated limit is 60 requests/hour), so the screen does not hit the network on every open. "Refresh" bypasses the cache and forces a re-fetch, while "All releases on GitHub" opens the release list in the browser.
- **Consistent Styling:** Release notes are rendered with the exact same formatter as the in-app update dialog (headings, bullets and separators are cleaned up).

### "Original / Translation" Toggle Everywhere in the Wiki
- **Popup Wiki Infobox:** When an in-app translation exists, the infobox now shows an "Original" button **immediately left of** the external-translate button; tapping it restores all infobox fields to the wiki's source language ("Translation" switches back). This matches the character wiki tab behaviour exactly.
- **Popup Article Summary and Sections:** The same toggle was added to the selected article's summary and to every expanded article section; while "Original" is selected the section body shows the untranslated text (the translation stays in memory, no extra network request).
- **Wikipedia Sources Included:** Because the same components render both Fandom and Wikipedia (TR) sources, the toggle works on either source and its state is tracked per translation key when switching sources.
- **Copy Infobox:** The popup infobox gained a "copy infobox" button. The copied text contains only the fields currently on screen, masks hidden fields as `[hidden]` while spoiler protection is on, and copies the source text rather than the translation when "Original" is selected.

### Root-Cause Fix for In-App Account Login (AniList / MyAnimeList / Simkl)
- **In-App Login Now Works:** Signing in from the browser-like in-app OAuth window (opened from the account connections screen) used to fail — Cloudflare/MAL WAF human-verification got stuck and permission was never granted; only the external browser worked. This release fixes the in-app flow at its root cause.
- **Spoofed User-Agent Removed (Root Cause):** The OAuth WebView sent a hardcoded fake User-Agent ("Chrome/124, Android 13, Pixel 7"). Cloudflare and MAL's WAF compare the client's real TLS + JavaScript fingerprint against the declared UA; the mismatch is a bot signal that triggers a verification loop. The system WebView's REAL default UA (device's actual WebView version and model) is now used — consistent with the fingerprint, closely matching the external-browser behavior. The same fix was applied to the generic in-app web window (TV QR login, add-on/notification/profile pages).
- **Single-Point OAuth Redirect Handling with Double-Exchange Guard:** The `malapp://auth` return was previously handled in two separate hooks (`onPageStarted` + `shouldOverrideUrlLoading`) with duplicated, unguarded code; on double firing the code could be exchanged twice, producing an "authorization code already used" error. It now flows through one guarded handler (`handleAuthRedirect`).
- **JS Redirects Are Now Caught:** A `doUpdateVisitedHistory` hook was added; on some WebView versions, OAuth returns performed via `window.location` never reached `shouldOverrideUrlLoading`, silently failing the connection after permission was granted.
- **No More Misleading Error:** The `ERR_UNKNOWN_URL_SCHEME` produced by special-scheme returns (`malapp://`) is no longer shown as a "page failed to load" error; the return is intercepted, loading is stopped, and the token exchange proceeds.
- **Affected Sources:** AniList, MyAnimeList, Simkl (OAuth window) and the phone-side in-app screen of the TV QR login. Kitsu (username/password), Shikimori, Bangumi, and the Simkl PIN flow already used the external browser and were unaffected.
- **MAL Unified Session Preserved:** Post-login OAuth token + web-session verification and the MAL cookie capture for notifications keep working; cookies are still read from the WebView CookieManager.

### "Reset to Default" on Player and Settings Sliders
- **One-Tap Factory Reset:** Added a direct reset button to player settings sliders (scroll sensitivity, double-tap seek duration, hold-to-boost speed, gesture sensitivity, PIP size), preference dialog sliders, and manga reader brightness.
- **Double-Tap Slider to Reset:** Double-tapping directly on any resettable slider resets it immediately to its recommended default value.

### +18 Adult Content Parity & Poster Reliability
- **Unified Across All Sources:** Adult blur and show/hide toggles are now consistently enforced across Cloudstream (CS3) add-ons, manga sources, schedule, and anime relations.
- **Protected Poster Headers:** Upgraded addon poster requests with Coil 3 NetworkHeaders to preserve critical Referer and User-Agent headers required by external image hosts.

### MyAnimeList (MAL) Resilient Pagination & Clubs Support
- **In-Place Pagination Retry:** Network failures while loading additional MAL friends, reviews, or recommendations no longer drop pagination; an in-place "Could not load more · Retry" action is displayed.
- **Clubs Tab & Pagination:** Added full MAL Clubs list support to user profiles with paginated loading.
- **Reliable Notification Pagination:** Loading older MAL notifications now handles connection errors gracefully with an inline retry option and improved next-link traversal.

---

## 🇬🇧 English

### New "Wiki Cache" Entry on the Storage Screen
- **Visible Wiki Data:** Settings > Storage now lists a **Wiki cache** entry showing the size and file count of the records kept for Fandom/Wikipedia articles, infoboxes, character pages and section contents.
- **Safe Clearing:** Its "Clear" action removes only **reproducible** wiki records (`fandom:v1:` rows in the Room `persistent_details_cache` table) and preserves the user's "correct / wrong match" decisions. Cleared content is re-fetched from the network on the next visit.
- **Accurate Measurement:** Because SQLite does not shrink after deletes (VACUUM is required), the entry reports **logical data size** instead of the database file size, and the reclaimed amount is derived from the same measurement. Records older than 30 days are still pruned automatically by background maintenance.

### About the App → Release Notes
- **Latest Notes Straight from GitHub:** A **Release Notes** card was added to the "About the App" screen. It shows the title, publication date and markdown-stripped changelog of the **latest** GitHub release.
- **Shown Even When Up to Date:** The update flow only opens a dialog when a newer version exists, so release notes were invisible on an up-to-date install. The new card does not require a newer version and labels the state with a "New version available" / "Installed version is up to date" badge.
- **Rate-Limit Friendly:** The last successful response is cached on disk for 6 hours (GitHub's unauthenticated limit is 60 requests/hour), so the screen does not hit the network on every open. "Refresh" bypasses the cache and forces a re-fetch, while "All releases on GitHub" opens the release list in the browser.
- **Consistent Styling:** Release notes are rendered with the exact same formatter as the in-app update dialog (headings, bullets and separators are cleaned up).

### "Original / Translation" Toggle Everywhere in the Wiki
- **Popup Wiki Infobox:** When an in-app translation exists, the infobox now shows an "Original" button **immediately left of** the external-translate button; tapping it restores all infobox fields to the wiki's source language ("Translation" switches back). This matches the character wiki tab behaviour exactly.
- **Popup Article Summary and Sections:** The same toggle was added to the selected article's summary and to every expanded article section; while "Original" is selected the section body shows the untranslated text (the translation stays in memory, no extra network request).
- **Wikipedia Sources Included:** Because the same components render both Fandom and Wikipedia (TR) sources, the toggle works on either source and its state is tracked per translation key when switching sources.
- **Copy Infobox:** The popup infobox gained a "copy infobox" button. The copied text contains only the fields currently on screen, masks hidden fields as `[hidden]` while spoiler protection is on, and copies the source text rather than the translation when "Original" is selected.

### Root-Cause Fix for In-App Account Login (AniList / MyAnimeList / Simkl)
- **In-App Login Now Works:** Signing in from the browser-like in-app OAuth window (opened from the account connections screen) used to fail — Cloudflare/MAL WAF human-verification got stuck and permission was never granted; only the external browser worked. This release fixes the in-app flow at its root cause.
- **Spoofed User-Agent Removed (Root Cause):** The OAuth WebView sent a hardcoded fake User-Agent ("Chrome/124, Android 13, Pixel 7"). Cloudflare and MAL's WAF compare the client's real TLS + JavaScript fingerprint against the declared UA; the mismatch is a bot signal that triggers a verification loop. The system WebView's REAL default UA (device's actual WebView version and model) is now used — consistent with the fingerprint, closely matching the external-browser behavior. The same fix was applied to the generic in-app web window (TV QR login, add-on/notification/profile pages).
- **Single-Point OAuth Redirect Handling with Double-Exchange Guard:** The `malapp://auth` return was previously handled in two separate hooks (`onPageStarted` + `shouldOverrideUrlLoading`) with duplicated, unguarded code; on double firing the code could be exchanged twice, producing an "authorization code already used" error. It now flows through one guarded handler (`handleAuthRedirect`).
- **JS Redirects Are Now Caught:** A `doUpdateVisitedHistory` hook was added; on some WebView versions, OAuth returns performed via `window.location` never reached `shouldOverrideUrlLoading`, silently failing the connection after permission was granted.
- **No More Misleading Error:** The `ERR_UNKNOWN_URL_SCHEME` produced by special-scheme returns (`malapp://`) is no longer shown as a "page failed to load" error; the return is intercepted, loading is stopped, and the token exchange proceeds.
- **Affected Sources:** AniList, MyAnimeList, Simkl (OAuth window) and the phone-side in-app screen of the TV QR login. Kitsu (username/password), Shikimori, Bangumi, and the Simkl PIN flow already used the external browser and were unaffected.
- **MAL Unified Session Preserved:** Post-login OAuth token + web-session verification and the MAL cookie capture for notifications keep working; cookies are still read from the WebView CookieManager.

---

# Kitsugi Beta v2.4.289

## 🇹🇷 Türkçe

### 1) Vikipedi (Wikipedia) Entegrasyonu Baştan Sona Düzeltildi — Yanlış Maddeler, Ana Sayfa Açılması ve Çeviri/Orijinal Tutarsızlığı

Bu sürüm, yapım detay sayfasındaki **Wiki** sekmesinin Vikipedi ayağında kullanıcıya yansıyan
tüm hataları kök nedenden çözer.

#### 1) "İlgili wiki sayfaları" şeridi yapımla ilgisiz maddeler gösteriyordu
- **Sorun ve Kök Neden:** İlgili sayfalar, kaynak wiki'nin **ANA SAYFASININ** 0/1 numaralı
  bölümlerindeki bağlantılardan toplanıyordu. Fandom'da ana sayfa bir "hub" olduğu için bu
  yaklaşım çalışıyordu; Vikipedi'de ise ana sayfa `Anasayfa`'dır ve bağlantıları günün seçkin
  maddeleridir. Sonuç: Attack on Titan için "Tarım, Hukuk, Ahlat, Türkçe" gibi 20 alakasız madde.
- **Çözüm:** Vikipedi kaynağında ilgili maddeler artık **yapımın kendi maddesinden** türetilir:
  maddenin girişi + ilk bölümünün wiki içi bağlantıları (normalize başlık tekilleştirme ile).
- **Ansiklopedi iskelet filtresi:** `Anasayfa`, `Portal`, `Yardım`, `Vikipedi:`, `Şablon:`,
  `Dosya:`, `Kategori:`, `Özel:`, `Taslak` gibi ansiklopedi altyapı sayfaları listeye alınmaz.
  Ayrıca maddenin kendisi ve 20 madde sınırı korunur.
- Vikipedi'de artık ana sayfa tabanlı ilgili sayfa akışı hiç çalışmaz (kart "İlgili maddeler"
  başlığıyla çizilir, kart altı etiket "Vikipedi sayfası" olur).

#### 2) "Vikipedi'de aç" düğmesi ve okuyucu, madde yerine ansiklopedinin ana sayfasını açıyordu
- **Sorun ve Kök Neden:** `openReaderMainPage()`, `siteinfo` içindeki `mainpage` alanını (TR için
  `Anasayfa`) açıyordu; bu Vikipedi için anlamsızdır ve kullanıcı aradığı maddeyi bulamıyordu.
- **Çözüm:** Vikipedi kaynağında üst başlıktaki düğme **çözülen maddeyi** açar (madde başlığı
  madde kartından, yoksa çözümleme sonucundan alınır). Ana sayfa yalnızca hiçbir madde
  bulunamadığında gösterilen "Wiki ana sayfasını aç" düğmesinde kalır.
- Üst kaynak kartı Vikipedi'de hangi **maddenin** açıldığını göstermek için madde başlığını da
  yazar; "Wikipedia · Açık Ansiklopedi" yerine dil sürümünün gerçek adı kullanılır
  (TR: "Vikipedi · Açık Ansiklopedi").

#### 3) "Wiki özeti" kartında Orijinal ↔ Çeviri geçişi içeriği tamamen değiştiriyordu
Üç ayrı kök neden vardı ve üçü de giderildi:
- **Çeviriler metne bağlı değildi:** Uygulama içi çeviriler yalnızca anahtarla (`desc`, `sec:1`…)
  saklanıyordu. Kaynak (Fandom ↔ Vikipedi) veya madde (Vikipedi TR ↔ EN) değiştiğinde aynı
  anahtar başka bir metni işaret ettiği hâlde **eski çeviri** ekranda kalıyordu; "Çeviri" düğmesi
  tamamen farklı bir metin gösteriyordu.
  → Her çeviri artık üretildiği **kaynak metne** bağlanır (`translationBases`); kaynak metin
  değişmişse çeviri, çeviri işi ve Orijinal/Çeviri durumu otomatik düşürülür
  (`pruneStaleTranslations`: madde, seri özeti, bölüm, madde içi sayfa, okuyucu bölümü).
- **Kaynak değişiminde türev durum sıfırlanmıyordu:** `switchSource` artık çevirileri, orijinal
  anahtarları, madde bölümlerini (içindekiler), arama durumunu, madde içi sayfayı ve okuyucu
  ilgili listesini tamamen temizler ve madde nesli sayacını (`articleGeneration`) artırır.
  Böylece yeni kaynak, önceki kaynağın içeriğiyle karışamaz.
- **Vikipedi'de dil sürümü sessizce değiştiriliyordu:** "Türkçe wiki önceliği" açıkken İngilizce
  Vikipedi maddesi, kullanıcıya haber verilmeden TR sürümüne çevriliyordu; "Orijinal" Türkçe
  metni, "Çeviri" ise eski İngilizce metnin çevirisini gösteriyordu.
  → Vikipedi'de bu sessiz geçiş kaldırıldı: dil sürümleri artık **ayrı kaynak çipleri** olarak
  sunulur (Vikipedi TR / Wikipedia EN) ve kullanıcı hangi dili okuduğunu bilir.
- **Zaten hedef dilde olan kaynakta çeviri başlatılmaz:** Vikipedi TR maddesi + Türkçe hedef gibi
  durumlarda uygulama içi çeviri hiç çalışmaz (metin değişmediği için "iki farklı içerik"
  yanılsaması oluşmaz).

#### 4) Kaynak listesi: Vikipedi TR ↔ Wikipedia EN geçişi
- Vikipedi birincil kaynak olduğunda artık **kardeş dil sürümü** de kaynak çipinde listelenir
  (öncelik kullanıcı tercihine göre TR→EN veya EN→TR). TR/EN sitelink'i olmayan yapımlarda ilk
  geçerli dil deterministik sırayla gösterilir (`Ad (DİL)` biçiminde).
- Kaynak çipleri artık **yatay kaydırılabilir**: üç kaynak (Fandom + Vikipedi TR + Wikipedia) dar
  ekranlarda taşmaz. Birincil kaynak Vikipedi olduğunda isim araması ikinci kez çalıştırılmaz
  (gereksiz ağ trafiği önlenir).

#### 5) Yanlış madde riskine karşı kimlik denetimi
- İsimle arama yolunda (`WikipediaResolver`) maddenin Wikidata kimliği (`pageprops.wikibase_item`)
  okunur; madde, yapımın (ve üst kayıtlarının) bilinen kimliklerinden birine ait değilse eşleşme
  "kesin" sayılmaz ve kullanıcıya onay kartı gösterilir. Kanıt yoksa (ağ hatası/kimliksiz madde)
  mevcut puan tabanlı davranış korunur — yanlış "bulunamadı" üretilmez.

#### 6) Vikipedi'de anlamsız kartlar kapatıldı
- **Evren rehberi (kategori çipleri)** Vikipedi kaynağında hiç gösterilmez
  (`GuideState.Unsupported`): `Category:Karakterler`, `Category:Kuruluşlar` gibi genel kategoriler
  yapım dışı çipler üretirdi.
- **"Seri özeti"** kartı Vikipedi'de gösterilmez; Vikipedi ana sayfasının özeti yapımın özeti
  değildir (yapım özeti zaten "Wiki özeti" kartındadır).

#### 7) Kaynak/lisans etiketleri ve küçük iyileştirmeler
- Alt bilgiler artık kaynağa göre doğru: `Kaynak: Vikipedi` (Fandom eki yok) ve
  **CC BY-SA 4.0** lisans notu; madde içi sayfa (öğe) alt bilgisi de aynı şekilde düzeltildi.
- Galeri penceresindeki kaynak adı wiki ailesine göre gösterilir ("Fandom" / "Vikipedi" /
  "Wikipedia") — Vikipedi görsellerinde artık "Fandom" yazmaz.
- Vikipedi maddelerinde `prop=images` (madde başına onlarca ikon/logo dosyası) isteği atlanır;
  bu liste arayüzde kullanılmıyordu. Performans ve veri tasarrufu.
- Okuyucuda Vikipedi sayfası açıldığında kısa açıklama satırı artık maddenin giriş metninden
  (`extracts`) doldurulur; Fandom'da kullanılan alan Vikipedi'de bulunmadığı için boş kalıyordu.
- Madde önbellek sürümü `v6`'ya yükseltildi (dil sürümü davranışı değiştiği için eski kayıtlar
  yanlış dilde madde döndürebilirdi).
- `FandomUrlsTest` içine kaynak adı eşlemesini doğrulayan yeni birim testi eklendi.

### 2) Wiki Sayfaları / Okuyucu Güvenilirliği (Fandom & MediaWiki)

- **Eksik/olmayan sayfalar artık tıklanabilir kart değil:** Fandom'un `missing: ""` (eski API) ve `missing: true` (yeni API) biçimleri, geçersiz sayfalar ve sayfa kimliği olmayan sonuçlar ayıklanır. Kırmızı bağlantılar ve başka wiki'lere ait başlıklar ilgili sayfa listesine girmez. Tetsurō Araki gibi *gerçekten o wiki'de bulunmayan* maddeler için uygulama sahte içerik üretmez.
- **Var olan kart daha sağlam açılır:** Doğrulanmış kartın kanonik başlığı/özeti/görseli kısa süreli yeniden kullanılır; görsel/özet API'si hata verirse gerçek sayfa kimliğini doğrulayan yalın MediaWiki sorgusu denenir. Yönlendirmeler birden fazla adımda çözülür; protokol-göreli görsel URL'leri düzeltilir.
- **Fandom ve Wikipedia sorguları ayrıldı:** Fandom'da bulunmayan `prop=extracts` artık yalnızca Wikipedia için istenir. İsteğe bağlı MediaWiki modülleri 200/hata yanıtı verirse daha yalın sorguyla tekrar denenir. Eski olumsuz önbellek anahtarları yenilendi; kalıcı "bulunamadı" süresi 3 günden 1 güne indirildi.
- **Kısmi sayfa kaybolmaz:** İçindekiler yüklenemediğinde sayfanın mevcut özeti ve giriş bölümü görünür; içindekiler için gerçek ağ isteği yapan "Tekrar dene" sunulur, eksik sonuç 7 gün önbelleğe yazılmaz. Karakter/ekip okuyucusunda giriş bölümü de otomatik yüklenir. Sayfa hiç açılamazsa hata açıklaması, yeniden dene ve tarayıcıda aç seçenekleri vardır.
- **Testler:** Eski/yeni `missing` bayrakları, yönlendirmeler, Fandom/Wikipedia parametreleri, başka wiki/kırmızı bağlantı süzgeci için regresyon testleri eklendi.

> Fandom'da hiç yayımlanmamış bir kişiye uygulama wiki maddesi uyduramaz; böyle başlıklar artık yanıltıcı kart olarak gösterilmez. Üçüncü taraf hizmet kesintileri tamamen önlenemez; yeniden dene/tarayıcı yolu bu yüzden korunur.
### 3) Çoklu Kaynaklı Karakter, Seslendirici ve Ekip Wikisi (Multi-Source Character & Staff Wiki)
- AniList person (`P11227`) ve MAL people (`P4084`) kimlikleri ile seslendirmen ve ekip üyeleri Wikidata üzerinden doğrulanır.
- Fandom ile birlikte Vikipedi kişi ve karakter maddeleri desteklenir; çoklu kaynak çipleri (`WikiSourceChips`) ve dinamik kaynak geçişi (`switchSource`) eklendi.

### 4) Düz İndirme Mimarisi & Tek Kopya İndirme Motoru
- **Doğrudan Tek Konuma Yazma:** Video indirmeleri artık ayrı bir geçici dahili alt klasör (`<mediaId>/`) oluşturup sonradan kopyalama adımı gerçekleştirmez; video akışı doğrudan `Downloads/Kitsugi/Video/<Başlık>_Bölüm_N.mp4` dosyasına yazılır. Çift dosya yazma ve diskte gereksiz yer kaplama/kopya sorunu kökten çözüldü.
- **Yan Yana Altyazı Düzeni:** Altyazılar videonun hemen yanına `<Başlık>_Bölüm_N.<dil>_<isim>.<uzantı>` olarak kaydedilir. Ayrı `Subtitles/` klasörü ve ikinci kopyalama adımı kaldırıldı.
- **Yalnızca Tamamlanan İndirmeler:** Metadata (`<taban>.json`) dosyası yalnızca indirme %100 başarıyla tamamlandıktan sonra oluşturulur; böylece yarım kalan veya iptal edilen indirmeler çevrimdışı listesinde görünmez.
- **Hatasız Poster İndirme:** İndirilen animelerin posterleri `Downloads/Kitsugi/Images/<taban>.jpg` olarak indirilir; poster indirmesinde ağ hatası çıksa bile video indirme işlemi kesintiye uğramaz veya başarısız sayılmaz.
- **MediaScanner İle Temiz Kayıt:** `MediaStore.insert` kaldırıldı, tüm medya indeksleme doğrudan `MediaScannerConnection` ile yapılır.
- **Özel SAF Klasörlerinde `(1)` Çiftleme Engeli:** Kullanıcı tanımlı özel indirme (SAF) klasörüne aktarımda hedefte aynı ada sahip dosya varsa önce eski dosya silinir; Android'in `(1)` ekleyerek dosyaları çoğaltması engellendi.
- **Merkezi Ad Yönetimi (`DownloadExportNaming`):** Video, altyazı, metadata ve poster dosya adları tek merkezden yönetilir; bir bölüm silindiğinde tüm ilişkili dosyalar (video, altyazılar, metadata ve poster) diskten eksiksiz temizlenir.
- **Geriye Dönük Çevrimdışı Oynatma:** `OfflinePlaybackHelper` hem yeni düz yapıdaki dosyaları hem de eski sürümlerden kalan `<mediaId>/` klasörlerini otomatik olarak tanır ve sorunsuz oynatır.
- **Altyazı Eşleştirme Düzeltmesi:** `DownloadsScreen` ve `DownloadItemRow` bileşenlerindeki altyazı aramasında nokta öneki (`$baseName.`) kontrolü eklendi; böylece `Bölüm_1` araması artık `Bölüm_12` altyazılarıyla karışmaz.

---

## 🇬🇧 English

### 1) Wikipedia Integration Fixed End to End — Wrong Articles, Main Page Opening, and Original/Translation Inconsistency

This release fixes every user-visible defect in the **Wiki** tab's Wikipedia path at the root cause.

#### 1) The "Related wiki pages" strip showed articles unrelated to the title
- **Issue & Root Cause:** Related pages were collected from sections 0/1 of the source wiki's
  **MAIN PAGE**. That works for Fandom (main pages are hubs) but on Wikipedia the main page is
  `Anasayfa`/`Main Page`, whose links are the day's featured articles. Result: 20 irrelevant
  articles ("Tarım, Hukuk, Ahlat, Türkçe") for Attack on Titan.
- **Fix:** For Wikipedia sources, related pages are now derived from the **resolved article**
  itself (lead + first section wiki links, deduplicated by normalized title).
- **Encyclopedia boilerplate filter:** `Main Page`, `Portal`, `Help`, `Wikipedia:`, `Template:`,
  `File:`, `Category:`, `Special:`, `Stub` style pages are excluded, as is the article itself,
  with a 20-item cap.
- The main-page based related flow no longer runs for Wikipedia at all (the card is titled
  "Related articles" and its action label reads "Wikipedia page").

#### 2) The "Read on Wikipedia" button and the reader opened the encyclopedia main page
- **Issue & Root Cause:** `openReaderMainPage()` opened `siteinfo`'s `mainpage` field (`Anasayfa`
  for tr.wikipedia), which is meaningless on Wikipedia and hid the article the user wanted.
- **Fix:** For Wikipedia sources the header button now opens the **resolved article** (title taken
  from the article card, falling back to the resolution result). The main page remains only as the
  explicit "Open wiki main page" fallback when no article could be matched.
- The source card also shows the article title so users can confirm what they are reading, and the
  subtitle uses the language edition's real name (TR: "Vikipedi · Açık Ansiklopedi").

#### 3) Toggling Original ↔ Translation in the "Wiki summary" card swapped the whole text
Three distinct root causes, all fixed:
- **Translations were not bound to their source text:** In-app translations were stored by key
  only (`desc`, `sec:1`…). When the source (Fandom ↔ Wikipedia) or article (Wikipedia TR ↔ EN)
  changed, the old translation stayed on screen although the key pointed at different text.
  → Every translation is now bound to the **source text it was produced from** (`translationBases`);
  when the text changes, the translation, its job and the Original/Translation state are dropped
  (`pruneStaleTranslations` covering article, series summary, sections, item pages, reader sections).
- **Derived state was not cleared on source switch:** `switchSource` now clears translations,
  original keys, article sections (table of contents), search state, selected item and reader
  related list, and bumps the article generation counter so the new source cannot be mixed with
  the previous one's content.
- **Wikipedia silently switched language editions:** with "Turkish wiki priority" on, an English
  Wikipedia article was silently replaced by its Turkish edition; "Original" showed Turkish text
  while "Translation" showed a translation of the old English text.
  → That silent switch is removed for Wikipedia: language editions are surfaced as **separate
  source chips** (Vikipedi TR / Wikipedia EN) so users know which language they read.
- **No translation is started when the source is already in the target language** (e.g. a Turkish
  Wikipedia article with a Turkish target), removing the "two different contents" illusion.

#### 4) Source list: Vikipedia TR ↔ Wikipedia EN switching
- When Wikipedia is the primary source, its **sibling language edition** is now listed as a source
  chip (TR→EN or EN→TR depending on the user preference). Titles without TR/EN sitelinks fall back
  to the first available language in deterministic order (rendered as `Name (LANG)`).
- The source chip row is now **horizontally scrollable**, so three sources (Fandom + Vikipedi TR +
  Wikipedia) do not overflow on narrow screens. When Wikipedia is already the primary source, the
  name search is not repeated (avoiding redundant network traffic).

#### 5) Identity verification against wrong articles
- On the name-search path (`WikipediaResolver`) the article's Wikidata identity
  (`pageprops.wikibase_item`) is read; when the article does not belong to any known identity of
  the title (or its parent records), the match is no longer treated as "certain" and a confirmation
  card is shown. Without evidence (network error / identity-less article) the existing score-based
  behaviour is preserved so no false "not found" appears.

#### 6) Meaningless cards disabled on Wikipedia
- The **universe guide (category chips)** is never shown for Wikipedia sources
  (`GuideState.Unsupported`): generic categories such as `Category:Characters` would produce
  unrelated chips.
- The **"Series summary"** card is not shown on Wikipedia; the encyclopedia main page blurb is not
  the title's summary (the title summary already lives in the "Wiki summary" card).

#### 7) Source/license labels and small improvements
- Footers are now source-correct: `Source: Wikipedia` (no Fandom suffix) and the **CC BY-SA 4.0**
  license note; the in-tab item sheet footer was fixed the same way.
- The gallery dialog's source name follows the wiki family ("Fandom" / "Vikipedi" / "Wikipedia") —
  Wikipedia images no longer say "Fandom".
- The `prop=images` request (dozens of icon/logo files per article) is skipped for Wikipedia
  articles; the list was unused by the UI. Performance and data saving.
- When the reader opens a Wikipedia page, its short description line is now filled from the
  article lead (`extracts`); the field used for Fandom does not exist on Wikipedia and used to
  stay empty.
- Article cache key bumped to `v6` (the language-edition behaviour changed, so old entries could
  return an article in the wrong language).
- Added a unit test to `FandomUrlsTest` covering the gallery source mapping.

### 2) Wiki Page & Reader Reliability

- Filter missing/invalid pages (both MediaWiki flag formats and absent page IDs), red links and cross-wiki links from related cards.
- Reuse verified previews when opening a card; fall back to a core page-ID query if optional preview modules fail. Handle multi-step redirects and normalize image URLs.
- Request TextExtracts only from Wikipedia, not Fandom; retry unsupported optional-module queries without those modules. Invalidate stale negative caches and shorten negative TTL to one day.
- Keep a valid article readable when its table of contents fails; allow a real retry without caching the incomplete TOC. Auto-load section 0 in character/staff readers and show a clear recovery path when a page cannot open.
- Add regression tests for the cases above. Articles not actually published by a wiki cannot be conjured by the app.

### 3) Multi-Source Character, Voice Actor & Staff Wiki
- Verify voice actors and staff members via AniList person (`P11227`) and MAL people (`P4084`) Wikidata properties.
- Add multi-source candidate chips (`WikiSourceChips`) with dynamic source switching (`switchSource`) between Fandom and Wikipedia.

### 4) Flat Download Architecture & Deduplicated Single-Copy Pipeline
- **Direct Single-Copy Video Pipeline:** Videos are written directly to `Downloads/Kitsugi/Video/<Title>_Episode_N.mp4` without intermediate `<mediaId>/` subdirectories or post-download copy steps, permanently eliminating duplicated disk consumption.
- **Co-Located Subtitle Layout:** Subtitles are saved alongside video files as `<Title>_Episode_N.<lang>_<name>.<ext>`, deprecating the redundant `Subtitles/` directory and secondary copies.
- **Atomic Metadata Finalization:** Metadata (`<base>.json`) is generated only upon 100% successful download completion, ensuring incomplete or cancelled downloads never display in the offline library.
- **Resilient Poster Downloads:** Posters are stored as `Downloads/Kitsugi/Images/<base>.jpg`; any network error during poster fetching is safely caught without failing the video download.
- **Clean MediaScanner Registration:** Removed `MediaStore.insert` in favor of direct `MediaScannerConnection.scanFile` calls for robust media store updates.
- **Duplicate `(1)` Prevention on SAF Storage:** When exporting to user-selected SAF folders, existing files with the same name are deleted first, preventing Android scoped storage from spawning duplicate `(1)` files.
- **Unified Export Naming Engine:** Centralized filename conventions across video, subtitles, metadata, and posters (`DownloadExportNaming`), guaranteeing full cleanup upon episode deletion.
- **Backward-Compatible Offline Playback:** `OfflinePlaybackHelper` seamlessly discovers and plays both flat-layout and legacy `<mediaId>/` directory downloads.
- **Subtitle Matching Bug Fix:** Added dot delimiter (`$baseName.`) filtering in `DownloadsScreen`, preventing `Episode_1` searches from falsely matching `Episode_12` subtitles.

---

# Kitsugi Beta v2.4.288

## 🇹🇷 Türkçe

### Wiki Okuyucu Sayfa İçi Bağlantı & Harici Tarayıcıya Çıkış Düzeltmesi (Uygulama İçi WebView Penceresi)
- **Sorun ve Kök Neden:** Wiki okuyucusunda (Fandom ve Wikipedia) sayfa içi bir bağlantıya (özellikle wiki ana sayfalarındaki logo/kuruluş görselleri, `File:` dosya sayfaları, `Category:` kategori listeleri, `Help:` yardım sayfaları, portal veya harici site bağlantıları) dokunulduğunda, bu bağlantılar standart makale ayrıştırıcısı kapsamına girmediği için doğrudan sistemin harici tarayıcısını (`Intent.ACTION_VIEW`) tetikliyor ve kullanıcı uygulamadan dışarı atılıyordu. Ayrıca bazı göreli URL'lerde dil yolu tekrarlanması veya protokol-göreli (`//`) CDN bağlantılarında adres birleştirme hataları oluşabiliyordu.
- **Uygulama İçi Web Penceresi (`KitsugiWebViewDialog`):**
  - Normal wiki makaleleri yerel sekmeli okuyucuda (`onOpenLink`) kalmaya devam eder.
  - Dosya, kategori, yardım, portal sayfaları, revizyon/düzenleme (`diff`, `oldid`, `action=edit`) ve harici web adresleri artık uygulama içerisindeki şık `KitsugiWebViewDialog` penceresinde açılır.
  - Web penceresi kapatıldığında kullanıcının bulunduğu sayfa, açık bölümler ve kaydırma konumu aynen korunur.
- **Güvenli Adres Çözümleme (`FandomUrls.readerLinkTarget`):**
  - Göreli, kök göreli, protokol göreli (`//static.wikia.nocookie.net/...`), sorgu (`?`) ve bölüm (`#`) bağlantıları `HttpUrl.resolve` ile çözümlenerek dil ön eki (`/tr/`) tekrarları ve hatalı bağlantı birleştirmeleri tamamen giderildi.
  - Web dışı şemalar (`javascript:`, `intent:`, `mailto:`, `file:`, `data:`) harici uygulamalara aktarılmadan uygulama içinde güvenle engellendi.
- **Kapsamlı Regresyon Testleri:** `FandomUrlsTest` içine normal makale ayrımı, dosya/kategori/yardım yönlendirmesi, göreli adres/CDN çözümü ve güvensiz şema engelleme senaryolarını doğrulayan 3 yeni birim testi eklendi.

---

## 🇬🇧 English

### Wiki Reader In-App Link Routing Fix (Internal WebView for Non-Article Wiki Links)
- **Issue & Root Cause:** When tapping certain links in the wiki reader (such as logos/organization badges on main pages, `File:` image pages, `Category:` listings, `Help:` guides, portals, or external site URLs), they fell outside the standard article parser and triggered `Intent.ACTION_VIEW`, launching the external browser and kicking the user out of the app. Additionally, some relative links could duplicate language prefixes or corrupt protocol-relative (`//`) CDN hosts.
- **Internal WebView Dialog (`KitsugiWebViewDialog`):**
  - Standard wiki articles continue opening smoothly in the native tabbed reader (`onOpenLink`).
  - File, Category, Help, revision/edit links (`diff`, `oldid`, `action=edit`), and external web links now open inside the application using `KitsugiWebViewDialog`.
  - Closing the web dialog keeps the originating reader state, active sections, and scroll position completely intact.
- **Robust URL Resolution (`FandomUrls.readerLinkTarget`):**
  - Relative, root-relative, protocol-relative (`//static.wikia.nocookie.net/...`), query (`?`), and fragment (`#`) links are resolved via `HttpUrl.resolve`, eliminating language prefix duplication (`/tr/`) and malformed links.
  - Non-web schemes (`javascript:`, `intent:`, `mailto:`, `file:`, `data:`) are strictly blocked internally rather than dispatched to external applications.
- **Regression Unit Tests:** Added 3 unit tests to `FandomUrlsTest` verifying article versus web-view routing, relative CDN resolution, and non-web scheme rejection.

---

# Kitsugi Beta v2.4.287

## 🇹🇷 Türkçe

### Keşfet "Tümünü Gör" Sayfalarında Tür ve Sıralama Artık Kaynaktan Geliyor
- **Kök Neden Çözüldü:** "Filtre ve Sıralama" sayfasında seçilen tür/sıralama eskiden yalnızca cihazda uygulanıyordu (`displayedResults`). Kaydırarak gelen her yeni sayfa filtresiz çekildiği için liste boşalıyor, otomatik sayfalama durmadan boş istek atarak döngüye giriyor ve sıralama her sayfada yeniden karışıyordu.
- **Kaynak Tarafı Sorgular:** Yeni `ExploreSourceFilterQuery` motoru seçilen tür ve sıralamayı 7 kaynağın **kendi API parametrelerine** çevirir:
  - **AniList:** Resmî `genres` veya `tags` (kanonik: `Sci-Fi`, `Slice of Life`) + `MediaSort` (`SCORE_DESC`, `POPULARITY_DESC`, `START_DATE_DESC`, `TITLE_ROMAJI` vb.).
  - **MAL / Jikan:** `genres=<id>` + `order_by`/`sort` (türsüz durumlarda resmî MAL sıralama ucu; başlık/tarih Jikan liste ucuna yönlendirilir).
  - **TMDB:** `with_genres` (+ anime raflarında `16` ve `ja` filtresi) + `sort_by` (`vote_average.*`, `popularity.desc`, `primary_release_date` vb.).
  - **Kitsu:** `filter[categories]` slug formatında (`sci-fi`, `slice-of-life`) + `sort` (`-averageRating`, `-userCount`, `-startDate` vb.).
  - **Shikimori:** Tür kimliği + `order` (`ranked`, `popularity`, `aired_on`, `name`).
  - **Simkl:** Haritalanmış tür uç noktası + `sort` (`rank`, `popular-this-week`, `release-date`).
  - **Bangumi:** Yerel etiket sözlüğü (科幻, 恋爱, 机战…) + `rank`, `heat`, `score`.
- **Liste Baştan Kurulur:** Filtre uygulandığında mevcut liste temizlenir ve **1. sayfa doğrudan filtrelenmiş istekle** kaynaktan çekilir. Kaydırma başa alınır ve sonraki her sayfa aynı filtre/sıralamayı korur.
- **Uçuştaki İstek Koruması:** `filterGeneration` jetonu sayesinde arka planda geciken eski isteklerin yeni listeyi ezmesi önlenir.
- **Kaynak Desteklemiyorsa (☁️ ve 📱 Ayrımı):** Seçim hangi kaynakta API tarafından uygulanabiliyorsa **☁️**, kaynağın desteklemediği durumlarda (ör. Simkl'de Mecha/Müzik, Shikimori'de artan puan) **📱** ile işaretlenir ve güvenli bir şekilde cihazda uygulanır.
- **Hızlı Tür Çipleri:** Izgara ve liste üstündeki hızlı tür çipleri ile "Temizle" butonu da aynı kaynak tarafı sorgu yolunu kullanır.
- **Kişisel Listeler:** Simkl izleme listesi ve TMDB "İzlemeye Devam / Planladıklarım" rafları API'de filtrelenebilir katalog olmadığından cihazda filtrelenmeye devam eder.
- **Sonsuz Döngü Koruması:** `AUTO_LOAD_BUDGET_PER_SCROLL` ve "aynı görünür öğe sayısı" emniyetleri korundu; boş gelen ilk sayfa artık sayfalamayı kontrollü bir şekilde durdurur.

---

## 🇬🇧 English

### Genre & Sort on Explore "See All" Pages Now Handled Directly by Source APIs
- **Root Cause Resolved:** Previously, genre and sort options chosen in the "Filter & Sort" sheet were only evaluated locally on the device (`displayedResults`). As pages were scrolled, subsequent pages were fetched without filters, resulting in empty lists, infinite auto-pagination loops, and broken ordering.
- **Source-Side Query Engine:** The new `ExploreSourceFilterQuery` engine translates active genres and sorts directly into each source's native API parameters:
  - **AniList:** Canonical `genres` or `tags` (e.g. `Sci-Fi`, `Slice of Life`) + `MediaSort` (`SCORE_DESC`, `POPULARITY_DESC`, `START_DATE_DESC`, `TITLE_ROMAJI`, etc.).
  - **MAL / Jikan:** `genres=<id>` + `order_by`/`sort` parameters with official endpoint routing.
  - **TMDB:** `with_genres` (+ genre 16 and Japanese language for anime shelves) + `sort_by` (`vote_average.*`, `popularity.desc`, etc.).
  - **Kitsu:** `filter[categories]` slug translation (`sci-fi`, `slice-of-life`) + `sort` parameters.
  - **Shikimori:** Native genre IDs + `order` (`ranked`, `popularity`, `aired_on`, `name`).
  - **Simkl:** Mapped genre endpoints + `sort` (`rank`, `popular-this-week`, `release-date`).
  - **Bangumi:** Native tag dictionary (科幻, 恋爱, 机战…) + `rank`, `heat`, `score`.
- **Clean List Rebuilding:** Applying a filter clears the dataset and immediately fetches **page 1 with server-side filters applied**. Scrolling resets to the top and subsequent pages retain the same parameters.
- **In-Flight Request Guard:** Generational `filterGeneration` tokens guarantee that delayed responses from superseded requests never corrupt the updated list.
- **Source Compatibility Badges (☁️ vs 📱):** Options supported natively on the remote API are badged with **☁️**, while options falling back to local client filtering are marked with **📱**.
- **Quick Genre Chips:** Top-bar genre chips and the "Clear" button now trigger full server-side filtered re-fetches.
- **Personal Watchlists:** User-specific shelves (Simkl watchlist, TMDB Continue/Plan to Watch) continue filtering client-side as they lack remote catalog filter endpoints.
- **Safety Guards Retained:** `AUTO_LOAD_BUDGET_PER_SCROLL` and duplicate-item guards prevent runaway network calls on empty filter results.

---

# Kitsugi Beta v2.4.286

## 🇹🇷 Türkçe

### 1. Wikipedia (Vikipedi) & Wikidata 2. Açık Kaynak Ansiklopedi Entegrasyonu
- **Asya Dizileri ve TV Şovları İçin Wikipedia Desteği:** Fandom'da bağımsız sayfası bulunmayan Asya dizileri (K-Drama, C-Drama, J-Drama, Tayland yapımları), TV programları, Türk dizileri ve bağımsız filmler için ikinci açık kaynaklı ansiklopedi kaynağı olarak Wikipedia (Vikipedi) sisteme entegre edildi.
- **Wikidata Sitelinks ile Anında ve Kesin Eşleşme:** TMDb, IMDb veya AniList/MAL kimliğine sahip tüm yapımlar (*Crash Landing on You, Squid Game, Queen of Tears, The Glory, Itaewon Class, Goblin* vb.) Wikidata'nın doğrudan sitelinks verileri üzerinden sıfır gecikmeyle Türkçe (`trwiki`) ve İngilizce (`enwiki`) Wikipedia sayfalarına bağlandı.
- **Yedek Wikipedia Arama Motoru (`WikipediaResolver`):** Wikidata bağlantısı bulunmayan yapımlar için dizi, sezon yılı ve format varyantlarıyla (`(TV series)`, `(South Korean TV series)`, `(film)`, `(drama)` vb.) MediaWiki arama ve akıllı benzerlik doğrulaması eklendi.
- **Wikipedia Bilgi Kutusu & Giriş Ayrıştırıcı:** Standart Wikipedia HTML bilgi kutusu tabloları (`table.infobox`, `table.vevent`), yayın kanalı (tvN, JTBC, Netflix vb.), bölüm sayısı, yönetmen, senarist, orijinal ad (Hangul, Hanja, Kanji) ve afiş görselleri doğrudan tanınıp bilgi kutusu kartlarına dönüştürüldü.
- **Arayüzde Çift Kaynak Çipleri (`[ 🌐 Fandom Wiki ] [ 📖 Wikipedia ]`):** Fandom önceliği %100 korundu. Her iki kaynağı da bulunan yapımlarda başlık kartında şık geçiş butonları yer alıyor; tek dokunuşla kaynaklar arasında geçiş yapılabiliyor. Tek kaynak olduğunda ise kaynağın adı ve açık ansiklopedi alt başlığı gösteriliyor.
- **Tam Uyumluluk:** Google Translate (Türkçe Çeviri), İçindekiler Tablosu ve Sayfa İçi Wiki Okuyucu (`FandomWikiReader`) tüm kaynaklarla kusursuz çalışıyor.

### 2. Fandom Wiki Tam Sayfa / Ana Sayfa Yükleme & Gelişmiş Dönüştürücü
- **Ana Sayfa ve Giriş Bölümü (Section 0) Desteği:** Ana sayfaların hero alanı, öne çıkan makale, video oyunu/film/animasyon kartları, Featured Characters, Contents kutuları, Calendar, Did You Know gibi modüllerinin yüklenmeme ve sayfanın boş görünme sorunu giderildi; giriş bölümü (section 0) açılışta otomatik yükleniyor.
- **Kartlar ve Bağlantı Grupları (CARD & LINK_GROUP):** Görselli hücreler zengin kartlar (başlık, alt satır, kapak görseli, hedef) olarak; navbox ve bağlantı kümeleri ise tüm bağlantılarıyla derli toplu bağlantı grupları halinde korunuyor.
- **Tıklanabilir Satır İçi Bağlantılar:** Paragraf ve liste maddelerindeki tüm makale bağlantıları tıklanabilir hale getirildi. Bağlantılı görseller doğrudan hedef sayfayı açıyor. Aynı wiki makaleleri okuyucu içinde yeni sekmede açılırken; şablon, kategori, dosya ve harici wiki bağlantıları sistem tarayıcısına yönlendiriliyor.
- **"Madde Bulunamadı" Kök Neden Çözümü:** Düz arama başlıklarının beklenmedik bir medyaya çözülmesi (ör. düz "Resident Evil" başlığının 2002 oyununa çözülmesi nedeniyle 2026 dizisinin bulunamaması) önlendi. Yayın yılı biliniyorsa önce `Resident Evil (2026)` gibi yıllı başlıklar deneniyor. Eski negatif önbellek `v5`'e yükseltilerek temizlendi. Ayrıca bulunamayan makale kartına "Wiki ana sayfasını aç" kısayolu eklendi.
- **Genişletilmiş Sınırlar:** Büyük wiki ana sayfalarının eksiksiz işlenmesi için blok ve görsel limitleri yükseltildi (`MAX_BLOCKS = 4000`, `MAX_SECTION_IMAGES = 300`).

### 3. Wiki Özetlerinde "Daha Fazla / Daha Az" Daraltma & Genişletme
- **Detay Sayfası Açıklama Standardı:** Wiki özet kartlarına detay sayfasındaki açıklama standardı getirildi (`WikiCollapsibleText`).
- **110 dp Yumuşak Gradyan:** Karakter/ekip wiki özeti, medya wiki özeti ve seçili wiki sayfası özet kartında 200 karakter veya 4 satırdan uzun metinler 110 dp'de yumuşak geçişle sınırlandırılıyor.
- **Genişletme Butonu & Çeviri Koruması:** "Daha fazla / Daha az" butonuyla metin akıcı bir şekilde açılıp kapanabiliyor. Orijinal metin ile çeviri arasında geçiş yapıldığında açık/kapalı durumu korunuyor.

### 4. Listem Ekranı Profil Tarzı Sade Alt Paneller
- **Sade Tasarıma Geçiş:** Listem'deki Kategori ve Filtrele & Sırala açılır panelleri, profildeki minimalist ve şık alt panel bileşenine (`KitsugiSheetOrDialog`) uyarlandı.
- **Modern ve Yalın Görünüm:** Yoğun gradyanlar ve parlak çerçeveler yerine sade satırlar, yumuşak seçim dolgusu (%15 vurgu rengi) ve profilin sürükleme çubuğu kullanıldı.
- **Dikey Kaydırma & Akıcı Kapanış:** Panellere dikey kaydırma eklendi. Kategori seçimi ve "Bitti" eylemleri `LocalDismissAnimated` ile yumuşak animasyon eşliğinde kapanıyor.

---

## 🇬🇧 English

### 1. Wikipedia & Wikidata 2nd Open-Source Encyclopedia Integration
- **Wikipedia Support for Asian Dramas & TV Shows:** Integrated Wikipedia as the second open encyclopedia source for K-Dramas, C-Dramas, J-Dramas, Thai series, TV shows, and standalone movies that lack independent Fandom wikis.
- **Instant Wikidata Sitelinks Matching:** Media with TMDb, IMDb, or AniList/MAL IDs (*Crash Landing on You, Squid Game, Queen of Tears, The Glory, Itaewon Class, Goblin*, etc.) are accurately linked to Turkish (`trwiki`) and English (`enwiki`) Wikipedia articles via Wikidata sitelinks with zero latency.
- **Fallback Wikipedia Search (`WikipediaResolver`):** Smart title matching and disambiguation for standalone works using MediaWiki search queries with format qualifiers (`(TV series)`, `(South Korean TV series)`, `(film)`, `(drama)`).
- **Wikipedia Infobox & Lead Parsing:** Native parsing for Wikipedia HTML infoboxes (`table.infobox`, `table.vevent`), extracting broadcast networks (tvN, JTBC, Netflix), episode counts, directors, writers, original titles (Hangul, Hanja, Kanji), and posters into structured infobox cards.
- **Dual Source UI Chips (`[ 🌐 Fandom Wiki ] [ 📖 Wikipedia ]`):** Fandom priority is fully preserved. When both sources exist, interactive chips on the header card allow instant one-tap switching. Single-source items display clean encyclopedia branding.
- **Full Compatibility:** Google Translate (Turkish Translation), Table of Contents, and In-App Wiki Reader (`FandomWikiReader`) operate smoothly across all sources.

### 2. Fandom Wiki Full-Page & Home Page Rendering with Rich Converter
- **Home Page & Lead Section (Section 0) Support:** Resolved blank main/home page views (hero sections, featured articles, video game/movie/animation grids, featured characters, contents modules, calendar, Did You Know widgets). Lead Section 0 is now requested and rendered on launch.
- **Cards & Link Groups (CARD & LINK_GROUP):** Visual cells convert into interactive cards (title, subtitle, thumbnail, target); navboxes and link clusters are grouped cleanly, retaining all links.
- **Tappable Inline Links & Linked Images:** All links within paragraphs and bullet points are interactive. Linked images navigate directly to target articles. Same-wiki links open smoothly in new reader tabs, while external wikis, categories, files, and templates open in the system browser.
- **"Article Not Found" Disambiguation Fix:** Fixed general titles resolving to older entries (e.g., bare "Resident Evil" mapping to the 2002 game, hiding the 2026 series). Year-qualified titles such as `Resident Evil (2026)` are tried first when release year is known. Outdated negative cache bumped to `v5`. Added an "Open wiki home page" button.
- **Expanded Limits:** Section limits increased to parse large wiki home pages (`MAX_BLOCKS = 4000`, `MAX_SECTION_IMAGES = 300`).

### 3. Collapsible Wiki Summary Cards ("See More / See Less")
- **Standardized Detail Summary Parity:** Integrated the detail screen summary card standard into wiki summaries via `WikiCollapsibleText`.
- **110 dp Soft Gradient Fade:** Character/staff summaries, media summaries, and selected wiki sheet cards exceeding 200 chars or 4 line breaks are cleanly trimmed at 110 dp with a subtle surface fade.
- **Toggle & Translation Preservation:** Smoothly toggle between collapsed and expanded states with the "See more / See less" button. Expansion state is retained when switching between original and translated text.

### 4. "My List" Profile-Style Subdued Bottom Sheets
- **Profile Bottom Sheet Parity:** Category and Filter & Sort bottom sheets in "My List" now adopt the minimalist profile sheet design (`KitsugiSheetOrDialog`).
- **Clean Aesthetic:** Removed heavy glow borders and loud gradients in favor of refined list rows, gentle selection highlights (15% accent fill), and standard drag handle bars.
- **Vertical Scrolling & Animated Dismissal:** Added vertical scrolling to sheet contents. Category selections and the "Done" action dismiss gracefully using `LocalDismissAnimated`.

---

# Kitsugi Beta v2.4.285

## 🇹🇷 Türkçe

### Wiki Bağlantı Çiplerinde Yüzde-Kodlu Başlık Hatası Düzeltildi
- **Kök Neden:** Bölüm altı bağlantı çipleri, bağlantının görünen metninden değil ham `href` adresinden üretiliyordu (`FandomHtmlText.parseWikiLink`). Bu çözümleyici yalnızca `%20`, `%2F`, `%3A`, `%28`, `%29` kodlarını elle çözüyordu; ō gibi ASCII dışı karakterlerin UTF-8 kodları (`%C5%8D`) olduğu gibi kalıyordu. Bu yüzden çiplerde `Shinj%C5%8D, Yamagata`, `Weekly Sh%C5%8Dnen Jump`, `Tank%C5%8Dbon` gibi başlıklar görünüyordu. Düz ASCII başlıklar (`Shueisha`, `Fine-art`…) şans eseri doğru göründüğü için hata yalnızca özel karakterli başlıklarda fark ediliyordu.
- **Düzeltme:** Başlığın tamamı artık UTF-8 olarak yüzde-kodundan çözülüyor (`Shinjō, Yamagata`, `Weekly Shōnen Jump`, `Tankōbon`, `Ten de Shōwaru Cupid`). `C++` gibi başlıklardaki `+` işareti de boşluğa dönüşmeden korunuyor; bozuk yüzde dizileri uygulamayı düşürmeden eski güvenli davranışa düşüyor.
- **Tıklama da düzeldi:** Çipe tıklanınca kodlu başlık sayfa aramasında tekrar kodlanıyor (`%25…`) ve sayfa bulunamıyordu; artık doğru sayfa açılıyor. Aynı düzelme "İlgili wiki sayfaları" adaylarını da kapsıyor.
- **Eski önbellek temizliği:** Bölüm önbelleği anahtarı `sec:v4` → `sec:v5` yükseltildi; 7 günlük eski, bozuk çipli kayıtlar güncelleme sonrası anında geçersiz sayılıyor, kullanıcının önbellek temizlemesi gerekmiyor.
- **Testler:** `FandomHtmlTextTest`'e kodlanmış ō/ş başlıkları, kodlu noktalama, `+` koruması ve bozuk kaçış dizisi senaryoları eklendi.

### Wiki Sekmeleri Chrome/Brave Düzenine Geçti
- **Bağlantılar yeni sekmede:** Sayfa içi bir bağlantıya, ilgili sayfa kartına veya bölüm çipine tıklamak artık bulunduğun sayfanın üstüne gitmiyor; sayfa, aktif sekmenin **hemen sağında yeni sekme** olarak açılıyor. Kaynak sayfa bir dokunuş uzağında aynen duruyor.
- **Daralıp genişleyen sekmeler:** Sekmeler genişliği eşit paylaşır — sekme açıldıkça hepsi birlikte daralır, kapandıkça yeniden genişler. En dar genişliğe gelinince şerit yatay kaydırılabilir olur ve yeni açılan sekme otomatik görünür alana getirilir.
- **Aktif sekme görünümü:** Chrome'daki gibi aktif sekme içerikle aynı arkaplanda üst köşeleri yuvarlı durur; daralmış pasif sekmelerde kapat (×) gizlenir, sekme genişleyince geri gelir.
- **Kopya önleme:** Aynı sayfa zaten bir sekmede açıksa ikincisi açılmaz, var olan sekmeye geçilir; sayfanın kendi bağlantısına tıklamak hiçbir şey yapmaz.
- **Sekme sınırı koruması:** 12 sekme doluysa bağlantı ölü doğmaz — aynı sekmede geçmişe eklenerek açılır, geri tuşu kaynak sayfaya döndürür.
- **`+` ve kapatma düzeni:** `+` ile çoğaltılan sekme de listenin sonuna değil aktif sekmenin sağına açılır. Aktif sekmeyi kapatmak önce sağ komşuyu, yoksa solu etkinleştirir. Sekmeye **uzun basmak** diğer tüm sekmeleri kapatır.
- **Testler:** `FandomReaderBrowserTest` ile sekme sırası, sağa ekleme, kopya önleme, sınır ve kapatma davranışları kapsama alındı.

---

## 🇬🇧 English

### Fixed Percent-Encoded Titles in Wiki Link Chips
- **Root cause:** Section link chips were derived from the raw `href` instead of a decoded title (`FandomHtmlText.parseWikiLink`), and only a handful of escapes (`%20`, `%2F`, `%3A`, `%28`, `%29`) were decoded manually. UTF-8 sequences for non-ASCII characters (e.g. ō = `%C5%8D`) leaked through, rendering chips like `Shinj%C5%8D, Yamagata`, `Weekly Sh%C5%8Dnen Jump`, `Tank%C5%8Dbon`. Plain-ASCII titles looked fine by luck, masking the bug.
- **Fix:** Titles are now fully percent-decoded as UTF-8 (`Shinjō, Yamagata`, `Weekly Shōnen Jump`, `Tankōbon`, `Ten de Shōwaru Cupid`). Literal `+` (e.g. `C++`) is preserved instead of becoming a space, and malformed escapes fall back safely without crashing.
- **Navigation fixed too:** Tapping a chip previously re-encoded the encoded title (`%25…`) and the page could not be found; chips and "Related wiki pages" candidates now open the correct article.
- **Stale cache invalidated:** Section cache key bumped `sec:v4` → `sec:v5`, so old broken chips (7-day TTL) disappear immediately after updating — no manual cache clear needed.
- **Tests:** Added coverage in `FandomHtmlTextTest` for encoded ō/ş titles, encoded punctuation, plus-sign preservation, and malformed escapes.

### Wiki Tabs Reworked to Chrome/Brave Behaviour
- **Links open in a new tab:** Tapping an in-page link, related-page card or section chip no longer navigates away; the page opens in a **new tab immediately to the right** of the active tab, leaving the source page one tap away.
- **Shrinking/growing tabs:** Tabs share the available width equally — they shrink together as tabs are opened and expand again as they are closed. At minimum width the strip becomes horizontally scrollable and the newly opened tab is auto-scrolled into view.
- **Active tab look:** Like Chrome, the active tab sits on the content background with rounded top corners; on narrow inactive tabs the close (×) button hides and returns when there is room.
- **No duplicates:** If the page is already open, its tab is focused instead of opening a second copy; tapping a page's link to itself is a no-op.
- **Tab-limit guard:** When all 12 tabs are in use, a link still works — it opens in the current tab with history, so Back returns to the source page.
- **`+` and closing order:** Duplicated tabs also open to the right of the active tab. Closing the active tab activates the right neighbour first, then the left. **Long-pressing** a tab closes all other tabs.
- **Tests:** `FandomReaderBrowserTest` covers tab order, right-side insertion, deduplication, the tab limit and close behaviour.

---

# Kitsugi Beta v2.4.284

## 🇹🇷 Türkçe

### 1. Wiki Okuyucusunda Çoklu Sekme & Tarayıcı Gezinmesi (Browser Tabs)
- **Sekme Çubuğu ve Çoklu Görev:** Uygulama içi wiki okuyucu üst çubuğuna yatay kaydırılabilir modern sekme şeridi eklendi. Kullanıcılar birden fazla wiki makalesini eşzamanlı olarak ayrı sekmelerde açık tutabilir (12 sekmeye kadar).
- **Bağımsız Sekme Geçmişi:** Her sekme kendi ileri/geri sayfa geçmişini bağımsız olarak saklar. Üst bardaki geri (←) ve ileri (→) butonlarıyla sayfalar arasında tarayıcı rahatlığında gezinilebilir.
- **Sekme Yönetimi:** Sekmeler arasında tek dokunuşla geçiş yapılabilir, 'x' butonuyla sekme kapatılabilir veya '+' butonuyla yeni sekme başlatılabilir.

### 2. İçindekiler Satır İçi (In-line) Akordeon Görünümü
- **Sayfa Altı Yığılması Giderildi:** İçindekiler bölümündeki başlıklar tıklandığında, içerik artık sayfanın en altına ayrı kartlar halinde yığılmak yerine doğrudan tıklanan başlığın hemen altında akıcı bir şekilde açılır.
- **Çoklu Bölüm Desteği:** Birden fazla bölüm aynı anda açık kalabilir ve her biri bağımsız olarak katlanabilir.

### 3. Uygulama İçi Tam Ekran Galeri Entegrasyonu
- **Dokunulabilir Görseller:** Wiki okuyucu başlık görseli, yüklenen içerik bölümlerindeki tüm resimler ve ilgili wiki sayfalarının küçük kapakları dokunulabilir hale getirildi.
- **Kitsugi Galeri Deneyimi:** Herhangi bir görsele tıklandığında doğrudan uygulamanın zengin tam ekran görsel galerisi (`KitsugiImageGalleryDialog`) açılır; yakınlaştırma, kaydırma ve yüksek kaliteli görsel inceleme imkanı sunulur.

### 4. Çeviri Kontrolleri ve Orijinal Metne Dönüş (TR/EN & Çevirmen Paritesi)
- **WikiTranslateButton Entegrasyonu:** Mevcut otomatik çeviri ayarları ve tercih edilen çevirmen motoruyla (Google, DeepL, Yandex vb.) tam entegrasyon sağlandı.
- **Başlık ve Bölüm Bazlı Geçiş:** Makale özeti ve her içerik bölümü için çevrilmiş metin ile orijinal metin arasında tek dokunuşla geçiş yapılabilir.
- **Eşzamanlılık Koruması:** Eski veya iptal edilmiş çeviri isteklerinin güncel içeriğin üzerine yazmasını engelleyen nesil/iş-kimliği doğrulaması eklendi.

### 5. Sekme ve Sayfa Bazlı Akıllı Kaydırma Konumu Saklama (Scroll Restoration)
- **Kaldığın Yerden Devam Et:** Sekmeler veya sayfalar arasında geçiş yapıldığında kaydırma konumu (scroll position) kaybolmaz; sekme, wiki ve sayfa bazında bellekte saklanarak geri yüklendiğinde tam kalınan konuma odaklanır.

---

## 🇬🇧 English

### 1. In-App Wiki Reader Multi-Tab Browsing & History Navigation
- **Browser Tab Strip:** Added a horizontal tab bar to the wiki reader top bar, allowing up to 12 simultaneous reader tabs.
- **Independent Tab History:** Each tab maintains its own back and forward page stack. Easily navigate wiki articles using dedicated back (←) and forward (→) buttons.
- **Tab Management:** Seamlessly switch tabs, close individual tabs, or open fresh tabs with a single tap.

### 2. In-Line Table of Contents Accordion View
- **No More Bottom Card Stacking:** Tapping a section in the table of contents now expands and renders the section directly underneath its title instead of appending cards at the bottom of the article.
- **Simultaneous Sections:** Multiple sections can remain expanded simultaneously, each folding and unfolding independently.

### 3. Seamless In-App Fullscreen Gallery Integration
- **Tappable Imagery:** Header banners, inline section photos, and related article thumbnails are fully interactive.
- **Kitsugi Gallery Experience:** Tapping any picture instantly launches the in-app fullscreen viewer (`KitsugiImageGalleryDialog`) with zoom, pan, and swipe-through capabilities.

### 4. Translation Controls & Original Text Toggle
- **Translator Parity:** Deeply integrated with user-configured translation settings and preferred translation engines (Google, DeepL, Yandex, etc.).
- **Instant Toggle:** Quickly switch between translated and original text for both article lead blurbs and individual section contents.
- **Stale Coroutine Guards:** Generational job keys prevent lagging translation responses from overwriting updated text.

### 5. Smart Scroll Position Restoration
- **Preserved Scroll Positions:** Reading positions are remembered across tabs, wikis, and pages. Switching tabs or navigating back automatically restores exact scroll offsets.

---

# Kitsugi Beta v2.4.283

## 🇹🇷 Türkçe

### 1. Wiki Makale İçi Mavi Bağlantılar ve İlişkili Sayfalar Artık Uygulama İçinde Açılıyor
- **Kök neden:** Yapım (medya) detayındaki Wiki sekmesi, makale bölümlerinin altındaki mavi bağlantı şeridini (örn. Hunter × Hunter → *Hunters, Hunter License, Hunter Exam, Nen, Phantom Troupe, Gon Freecss, Kite...*) çizerken uygulama içi okuyucu geri çağrısını iletmiyordu. Bu yüzden bağlantılar harici tarayıcıya yönleniyordu; önceki "sıfır tarayıcı yönlendirmesi" medya makalesinin bölüm içi bağlantılarını atlamıştı.
- **Düzeltme:** Medya Wiki sekmesi artık bölüm altı mavi bağlantıları, ilişkili sayfa kartlarını ve makale alt bilgisini doğrudan uygulama içi okuyucuya (`FandomWikiReader`) bağlıyor.
- **Hedef wiki kimliği korundu:** Karakter, ekip ve seslendiren sayfalarında bir bağlantı farklı bir wiki'ye işaret ediyorsa okuyucu o hedef wiki'de açılır.
- **İlişkili sayfa kartları:** "İlgili sayfalar" kartındaki *sayfayı aç* düğmesi ve görselsiz kart dokunuşu da tarayıcı yerine uygulama içi okuyucuyu açar.
- **Okuyucu hata durumu (Tekrar dene):** Bağlanılan sayfa yüklenemezse okuyucu sonsuz yükleme animasyonunda kalmaz; sayfa başlığıyla birlikte *"Sayfa yüklenemedi · Tekrar dene"* kartı gösterilir (medya, karakter ve ekip okuyucu akışlarının tamamında).
- Harici tarayıcı yalnızca okuyucunun üst çubuğundaki dünya (🌐) simgesine bilerek dokunulduğunda açılır.

### 2. Wiki Okuyucu "Fandom sayfasını" Butonu Çökmesi Giderildi (Kritik Düzeltme)
- **Belirti:** Yapım/karakter/ekip detayındaki Wiki sekmesinde "İlgili wiki sayfaları" şeridindeki **"Fandom sayfasını"** butonuna basıldığında veya dahili wiki bağlantısı tıklandığında, okuyucu sayfası yüklenir yüklenmez uygulama çöküyordu (`IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints`).
- **Kök neden:** Detay sayfası sekme içeriği portre düzende `DetailPageScaffold` içindeki `HorizontalPager` tarafından **sınırsız yükseklik** (`Constraints.Infinity`) ile ölçülüyor; yatay/TV düzeninde ise sekme içeriği zaten bir `verticalScroll` kutusunun içinde bulunuyor. Okuyucu bu ağacın içine gömülü çizildiğinde kendi kaydırma alanı sonsuz kısıt alıyor ya da iç içe kaydırma kısıtı nedeniyle Compose yasaklı istisnayı fırlatıyordu.
- **Çözüm:** Uygulama içi wiki okuyucu artık **kendi tam ekran penceresinde** (tam ekran `Dialog`) açılıyor. Dialog penceresi sistem seviyesinde her zaman sınırlı (bounded) kısıt verdiğinden bu çökme sınıfı kökten ortadan kaldırıldı.
- **Gezinme ve Görünüm Korundu:** Başlık çubuğu, içindekiler tablosu, bölüm içerikleri, ilgili sayfalar şeridi ve geri yığını (`BackHandler` — önceki wiki sayfasına dönüş) eksiksiz çalışır; durum çubuğu ve gezinme çubuğu boşlukları (edge-to-edge) ile MIUI/HyperOS force-dark koruması pencereye uygulandı.
- **Çift çizim temizliği:** Karakter detay sayfasında kök seviyedeki mükerrer okuyucu render'ı kaldırıldı, sekme üzerinden tek noktadan pencere açılması sağlandı.

### 2. Galeri "Detaylar" Levhası Sınırsız Yükseklik Koruması
- Galeri ekranındaki "Detaylar" bilgi levhasına, uygulamanın diğer tüm alt sayfalarında bulunan `heightIn(max = ekranın %85'i)` kısıtı eklendi. Böylece alt sayfa sınırsız yükseklikle ölçülse bile aynı dikey kaydırma çökmesinin oluşması engellendi.

### 3. Plazma Küre Yenileme Animasyonu (Pull-to-Refresh)
- **Tüm yenileme göstergeleri plazma küre oldu:** Profil (MyAnimeList / AniList vb. tüm sekmeler), Keşfet, Listem, medya detay iskeleti (`DetailPageScaffold` — film/dizi detaylarının hepsi), karakter detayı, ekip detayı, kullanıcı profili ve kullanıcı listesi ekranlarındaki varsayılan Material göstergesi (siyah yuvarlak içindeki dönen ok) tamamen kaldırıldı.
- **Ortak `KitsugiPullToRefreshIndicator` bileşeni:** Tam ekran yüklenme animasyonundaki parlayan plazma kürenin (`KitsugiPlasmaLoader`) aynısı kullanıldı. Çekmeye başlayınca belirip çekiş mesafesiyle büyür, yenileme sürerken tam boyunda atmaya devam eder, boşta duran sayfalarda hiç çizilmez (sıfır performans maliyeti).

### 2. Wiki "Hayır, yanlış" Eşleşme Kararını Geri Alma & Toplu Kurtarma
- **Geri alma kartı (`WikiRejectedUndoCard`):** "Bu eşleşme doğru mu?" kartında yanlışlıkla "Hayır, yanlış"a basıldığında, Wiki sekmesinin en üstünde **"Eşleşme yanlış işaretlendi — [Geri al] / [Kapat]"** kartı belirir. Sıradaki aday bulunsa da, hiç wiki bulunamasa da kart üstte görünür kalır; "Geri al" tek dokunuşla reddi kaldırıp eşleşmeyi yeniden çözer. Hem yapım Wiki sekmesinde hem de karakter/ekip Wiki sekmelerinde çalışır.
- **Ayarlardan toplu kurtarma:** Kart kapatılsa bile kurtarma yolu mevcuttur: **Ayarlar → Fandom Wiki → "Reddedilen eşleşmeleri geri getir"** seçeneğiyle, onaylanan eşleşmelere dokunulmadan tüm yanlış retler silinir ve wiki'ler tekrar önerilir.
- **Açıklama metni güncellendi:** Onay kartının açıklaması, yanlış işaretlenen eşleşmenin sonradan geri alınabileceğini açıkça belirtir (TR + EN).

### 3. Fandom Wiki Keşif ve Veri Çekme İyileştirmeleri (Canlı Denetimle)
- **Kimliksiz (eklenti) manga/manhwa kayıtları için AniList köprüsü:** Yalnızca başlık gönderen eklenti kayıtları için başlık AniList'te birebir eşleştiğinde kimlikler ve başlangıç yılı otomatik tamamlanır; böylece Wikidata ve karakter doğrulaması devreye girer (Naruto, One Piece, Bleach, Berserk, Solo Leveling, Chainsaw Man vb. çözülür).
- **Gerçek wiki boyutu eşiği düzeltildi:** Fandom topluluk aramasının kaba `pageCount` kovası için alt sınır 10'a çekildi; Barakamon (85), Texhnolyze (74), Uchuu Kyoudai (87) gibi gerçek wikiler artık elenmez.
- **Dil tespiti `siteinfo` ile sağlandı:** Dil öneki olmayan yabancı wikilerin (The Witcher için Lehçe `wiedzmin`, LOTR için Rusça `ru.domb`) İngilizce sanılması önlendi; öncelik sırası `en` > `tr` > diğerleri olarak yapılandırıldı.
- **Karakter kanıtı PROBABLE kuralları:** Ana karakterlerin tamamı veya ezici karakter örtüşmesi olan adaylar PROBABLE olarak kabul edilir (Space Brothers, kiss×sis, Noblesse vb.).
- **Tam giriş metni ve Bölüm 0 (infobox + özet):** Fandom'da desteklenmeyen `prop=extracts` yerine Bölüm 0 tam işlenmiş HTML olarak çekilir; giriş metni kesintisi giderildi ve bağlantılı infobox metinleri (ör. Gintoki seslendirmenleri) eksiksiz alındı.
- **Genişletilmiş sınırlar:** Paragraflar 8.000 karakter, bölümler 400 blok, tablolar 150 satır, görseller 24 adede çıkarıldı; önbellek anahtarları v4'e yükseltildi.

### 4. Fandom Uygulama İçi Wiki Okuyucu, İlgili Sayfa Kartları ve Evren Rehberi Toplu Yükleme
- **Tam ekran uygulama içi wiki okuyucu:** Wiki bağlantılarına veya özet kartlarına dokunulduğunda harici tarayıcıya atlama tamamen kaldırıldı. Sayfalar, içindekiler tablosu, tembel yüklenen bölümler ve geçmiş yığını (`BackHandler`) ile doğrudan uygulama içinde okunabilir.
- **İlgili wiki sayfaları şeridi:** Yapım ve karakter wiki sekmelerinde, ayrıca okuyucu içinde sayfadan çıkarılan dahili wiki bağlantıları yatay kaydırılabilir görsel kartlar olarak sunulur.
- **Evren rehberi tek dokunuşla toplu yükleme:** "Devamını göster" butonu kategorideki kalan tüm yapımları/öğeleri tek tıklamayla yükler.
- **Sıfır tarayıcı yönlendirmesi:** Harici tarayıcı yalnızca kullanıcı üst bardaki dünya simgesine bilerek dokunduğunda açılır.

### 5. Bangumi İngilizce Başlık, Keşfet Şeritleri ve Kitaplık Düzeltmesi
- **Gerçek dil ayrımı:** `BangumiSubjectTitleResolver` ve `BangumiTitleCache` üzerinde İngilizce, Romaji ve Özgün başlıklar birbirinden ayrıldı. Latin harfli Japonca başlıkların sahte İngilizce olarak önbelleğe alınması engellendi.
- **Keşfet ve arama entegrasyonu:** Bangumi arama sonuçları, vitrin rafları, "Tümünü Gör" sayfaları ve profil yapım listeleri kullanıcının başlık dili tercihine (İngilizce / Romaji / Özgün) tam olarak uyar.
- **Kitaplık başlık zenginleştirici:** İçe aktarılan veya takip edilen Bangumi kayıtları için İngilizce başlık çözümlemesi güvenilir kaynaklardan tamamlanır.

### 6. Karakter / Ekip / Seslendiren Wiki Sekmesi: Gerçek "Tekrar Dene" ve Bayat Önbellek Düzeltmesi
- **Ölü "Tekrar dene" düğmesi düzeltildi:** Karakter ve yapım katmanlarındaki "bulunamadı" önbelleği ile süreç içi zehir listesi atlanarak (`forgetMissingWikis()`) çözümleme baştan yapılır.
- **Bayat "bulunamadı" süresi kısaltıldı:** 3 gün → 1 gün.
- **Geçici kesintiler kalıcı "bulunamadı" yazmıyor:** HTTP 403/408 ve kimlik köprüsü kesintileri geçici sayılır; geçici hatalar kalıcı önbelleğe yazılmaz.

---

## 🇬🇧 English

### 1. In-Article Blue Wiki Links and Related Entities Open In-App
- **Root cause:** The media Wiki tab rendered the blue link strip under article sections (e.g. Hunter × Hunter → *Hunters, Hunter License, Hunter Exam, Nen, Phantom Troupe, Gon Freecss, Kite...*) without forwarding the in-app reader callback, silently dropping to the external browser. The previous "zero browser jumping" pass missed section links on media pages.
- **Fix:** The media Wiki tab now routes section links, related-entity cards and the article footer directly through the in-app reader (`FandomWikiReader`).
- **Target wiki id preserved:** On character/staff/voice-actor pages, links pointing to another wiki open the reader on that target wiki.
- **Related page cards:** The *open page* button and tapping imageless entity cards open in the in-app reader instead of launching the browser.
- **Reader failure state (Retry):** If a linked page cannot be loaded, the reader no longer spins forever; it displays the page title alongside *"Page could not be loaded · Retry"* (across media, character and staff flows).
- The external browser opens strictly when explicitly tapping the globe (🌐) icon in the reader header.

### 2. Critical Fix: Wiki Reader "Open Fandom page" Unbounded Height Crash
- **Symptom:** Tapping **"Open Fandom page"** on the related-wiki-pages strip or opening an internal wiki link in the Wiki tab crashed the app the moment the reader content finished loading (`IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints`).
- **Root cause:** In portrait mode, detail tab content is measured by a `HorizontalPager` with **unbounded height** (`Constraints.Infinity`); in landscape/TV layouts, tab content already lives inside a `verticalScroll`. Rendering the reader inline gave its scrollable column an infinite height constraint (or created nested vertical scrolls), triggering Compose's fatal measurement exception.
- **Fix:** The in-app wiki reader now opens in its **own full-screen window (`Dialog`)**. Window constraints are guaranteed to be strictly bounded, permanently eliminating this entire class of crashes.
- **Preserved Navigation & Aesthetics:** The reader maintains its top bar, table of contents, lazy section loading, related pages strip, and back-stack navigation (`BackHandler` returns to previous page). Edge-to-edge system insets and MIUI/HyperOS force-dark prevention are fully integrated.
- **Duplicate render removed:** Eliminated redundant root-level reader composition on character detail pages so multiple stacked dialogs never occur.

### 2. Gallery "Details" Sheet Height Guard
- The image gallery's details sheet now enforces the same `heightIn(max = 85% of screen)` guard used across all other bottom sheets, preventing the identical vertically-scrollable unbounded crash.

### 3. Plasma Orb Pull-to-Refresh Animation
- **All pull-to-refresh indicators are now the plasma orb:** The default Material refresh indicator (spinning arrow in a dark circle) was removed from Profile (all tabs), Explore, My List, media detail scaffold (`DetailPageScaffold` — all movies/series), character detail, staff detail, user profile, and user list screens.
- **Shared `KitsugiPullToRefreshIndicator` component:** Renders the glowing plasma orb from the fullscreen loader (`KitsugiPlasmaLoader`). Smoothly scales with pull distance, keeps pulsing at full size while refreshing, and never draws when idle (zero idle overhead).

### 2. Wiki "No, wrong" Rejection Undo & Bulk Restore
- **Undo rejection card (`WikiRejectedUndoCard`):** If "No, wrong" is tapped by mistake, an **"Undo"** card appears at the top of the Wiki tab. Whether the next candidate is found or no wiki is found at all, the card stays visible; tapping "Undo" immediately clears the rejection and re-resolves the match for both media and character/staff wiki tabs.
- **Bulk recovery in Settings:** Even if the undo card was dismissed, **Settings → Fandom Wiki → "Restore rejected matches"** restores all mistakenly rejected wikis/pages while preserving user-confirmed matches.
- **Confirmation copy updated:** Clearly explains that rejected matches can be undone later (TR + EN).

### 3. Fandom Wiki Discovery & Data Extraction Fixes (Live-Audited)
- **AniList bridge for title-only plugin records:** Titles from plugin manga/manhwa screens without IDs are exact-matched against AniList to hydrate catalog IDs and release year, enabling Wikidata and character proof.
- **Adjusted wiki article count threshold:** Fandom search `pageCount` bucket lower bound reduced to 10 so real wikis (Barakamon, Texhnolyze, Space Brothers) are no longer discarded.
- **Language detection via `siteinfo`:** Disallows non-English prefixless wikis (e.g. Polish `wiedzmin`, Russian `ru.domb`) from masquerading as English; strict ranking: `en` > `tr` > others.
- **Character evidence PROBABLE rules:** Candidates with full main cast overlap or strong multi-character confirmation are accepted as PROBABLE.
- **Section 0 lead & infobox merge:** Fetches Section 0 processed HTML directly, eliminating truncated blurbs and capturing rich linked infobox values (voice actors, staff).
- **Expanded parsing limits:** Limits increased to 8,000 chars/paragraph, 400 blocks/section, 150 table rows, 24 images; cache bumped to v4.

### 4. Fandom In-App Wiki Reader, Related Page Cards & Category Batch Loader
- **Full-screen in-app wiki reader:** External browser redirects replaced with in-app reader featuring TOC, lazy-loaded sections, internal links, and backstack navigation.
- **Related wiki pages strip:** Horizontal preview cards on media and character wiki tabs with contextual internal wiki links, titles, and thumbnails.
- **Category guide batch loader:** Single-tap batch loading for remaining items in a category.
- **Zero browser jumping:** External browser opens only when explicitly tapping the globe icon.

### 5. Bangumi English Title Discovery, Explore Shelves & Library Hydrator Fix
- **Strict title categorization:** Differentiates English, Romaji, and Native titles, preventing Romaji titles from being mistakenly cached as English.
- **Explore & search parity:** Explore shelves, "See All" grids, search results, and profile lists strictly honor user display title preferences.
- **Library title hydrator:** Reliable English title resolution for tracked and imported Bangumi entries.

### 6. Character / Staff / Voice-Actor Wiki: True Retry & Stale Negative Cache Fix
- **True Retry:** Retrying now clears in-memory poison entries (`forgetMissingWikis()`) and force-refreshes bypassing all cached negative states.
- **Stale negative cache reduced:** 3 days → 1 day.
- **Transient error resilience:** HTTP 403/408 and bridge errors are treated as transient and not persisted as permanent misses.

---

# Kitsugi-Beta v2.4.281

## 🇹🇷 Türkçe (v2.4.281)

### 🌟 Fandom Wiki Keşif, Film Türü & Çoklu Kimlik Doğrulamaları

- **Film Formatı ve Tür İyileştirmesi:** Medya detaylarından gelen `format`, `rawFormat`, alt başlık ve film başlığı sinyalleri (örn. *The Movie*, *Reze Arc*, *劇場版*, *映画*, *Film*) tür belirlemeye dahil edildi. Böylece TMDB/Wikidata aramasında dizi (`P4983`) yerine film (`P4947`) kimlikleri önceliklendirildi.
- **Karakter & Ekip Görünümlerinde Tür Normalizasyonu:** Karakter, seslendirmen ve yapım ekibi görünümlerindeki yapım başlıklarına da aynı sinyal tabanlı tür normalizasyonu uygulandı (`normalizedMediaKind()`).
- **Makale Doğrulaması (Article Proof) ve Akışa Aktarım:** Wiki adayları, yapımın Fandom makalesi ve sağlayıcı dış bağlantılarıyla güçlü doğrulamadan (`FandomMediaIdentityProof.hasStrongArticleProof`) geçiriliyor. Eşleşen makale başlığı doğrudan sonraki yükleme akışına aktarılıyor.
- **Genişletilmiş Kimlik Bağlantıları (10 URL Sınırı):** Birebir kimlik bağlantısı arama limiti 10 URL'ye yükseltildi. Bangumi (`bgm.tv/subject/`) ve Simkl (`simkl.com/`) dahil tüm sağlayıcı kimlikleri liste dışı kalmadan taranabiliyor.
- **Wikidata Arama Dayanıklılığı:** Wikidata üzerindeki tek bir sağlayıcı sorgusu (örn. zaman aşımı veya hata) diğer tüm kimliklerin taranmasını engellemiyor; tüm aramalar başarısız olduğunda sonuç geçici hata olarak korunuyor.
- **MediaWiki Dış Bağlantı Kapsamı:** `list=exturlusage` sorgusunda protokol kısıtlaması esnetilerek hem HTTP hem HTTPS bağlantıları eksiksiz taranıyor.
- **Regresyon & Birim Testleri:** Film türü çözümlemesi, karakter/ekip görünüm normalizasyonu, güçlü makale kanıtı ve tüm katalog sağlayıcı kimlik bağlantıları için kapsamlı testler eklendi.

---

## 🇬🇧 English (v2.4.281)

### 🌟 Fandom Wiki Discovery, Movie Format Resolution & Multi-Provider Identity Links

- **Movie Format & Kind Resolution:** Media detail format, rawFormat, subtitle, and movie title signals (e.g. *The Movie*, *Reze Arc*, *劇場版*, *映画*, *Film*) now participate in media kind determination. Movie identifiers in TMDB (`P4947`) and Wikidata are correctly prioritized over TV series (`P4983`).
- **Normalized Media Kinds for Character & Staff Appearances:** Character, staff, and voice actor appearance queries apply the same signal-based normalization (`normalizedMediaKind()`).
- **Article Match Proof & Downstream Passthrough:** Candidate wikis are verified using strong article proof (`FandomMediaIdentityProof.hasStrongArticleProof`) matching provider external links, passing the verified article title into the downstream page loading flow.
- **Expanded Identity Links (10 URLs Limit):** Exact external link lookup boundary increased to 10 URLs, ensuring Bangumi (`bgm.tv/subject/`) and Simkl (`simkl.com/`) identity links are fully evaluated.
- **Resilient Wikidata Lookups:** A failure or timeout on a single provider property lookup does not abort remaining lookups; transient negative errors are cached only if all lookups fail.
- **MediaWiki External Link Protocol Flexibility:** Removed protocol restriction in `list=exturlusage` queries so both HTTP and HTTPS legacy links are matched reliably.
- **Unit & Regression Tests:** Added comprehensive test coverage for theatrical work normalizations, multi-source external identity links, and article proof matching.

---

# Kitsugi-Beta v2.4.280

## 🇹🇷 Türkçe (v2.4.280)

Fandom Wiki entegrasyonu, zengin karakter sayfaları (örn. Eren Yeager), hikâye yayları (Jujutsu Kaisen, My Hero Academia, Kimetsu no Yaiba), manga/anime tabloları ve infobox API yapılarına uyumlu olarak genişletildi.

### 🌟 Yeni Özellikler ve İyileştirmeler

1. **Uygulama İçi Makale Okuyucusu (Fandom Article Reader)**
   - Evren rehberi çiplerine veya wiki içi arama sonuçlarına tıklandığında harici tarayıcıya yönlendirmek yerine doğrudan uygulama içinde açılan zengin makale okuyucu sayfası (`WikiItemSheet`) eklendi.
   - Makale başlığı, wiki kaynak adı, kısa açıklama, bilgi kutusu (infobox), görseller, açılır ve tembel yüklenen bölümler (lazy sections), spoiler koruması ve harici "Fandom'da aç" bağlantısı entegre edildi.
   - Manga bölümleri, anime bölümleri ve hikâye yaylarındaki içerik tabloları taranarak satır yapısını koruyan okunaklı bloklara dönüştürüldü; gereksiz navigasyon tabloları temizlendi.
   - Bölüm içindeki figür ve galeri görselleri güvenli HTTPS URL'leri ve resim alt yazılarıyla birlikte yakalanarak zengin görsel blokları olarak sunuldu.

2. **Genişletilmiş Evren Rehberi & 50 Başlıklı Grup Taraması**
   - Evren rehberine Karakterler (`Characters`), Hikâye Yayları (`Story Arcs`), Anime Bölümleri (`Episodes`) ve Manga Bölümleri (`Chapters`) kategorileri eklendi.
   - Türkçe ve İngilizce kategori adayları `categoryinfo` sorgularında 50 başlıklı API gruplarına bölünerek (`clean.chunked(MAX_TITLES_PER_REQUEST)`) sınırda kesilme sorunu tamamen giderildi.

3. **Hiyerarşik Spoiler ve Gizli Bölüm Kalıtımı**
   - İçindekiler tablosunda (TOC) alt bölümler artık üst bölümlerin spoiler ve gizlilik durumunu kalıtım yoluyla devralır. Gizli galeri veya navigasyon bölümlerinin alt başlıkları içindekilerde görünmez.
   - İçindekiler isteği başarısız olduğunda kullanıcıya yeniden deneme sunulur; eksik içindekiler önbelleğe alınmadığı için yeniden deneme gerçekten API'ye gider.

---

## 🇬🇧 English (v2.4.280)

Expanded Fandom Wiki integration to cater to diverse article structures, including comprehensive character pages (e.g. Eren Yeager), story arcs, manga/anime chapter/episode tables (Jujutsu Kaisen, My Hero Academia, Demon Slayer), and modern infobox API structures.

### 🌟 New Features & Improvements

1. **In-App Article Reader**
   - Tapping any universe guide chip or search hit opens a complete in-app article sheet (`WikiItemSheet`) instead of forcing an external browser jump.
   - Full support for article title, wiki source attribution, lead summary, infobox card, lazy expandable sections, spoiler gating, and external "Open on Fandom" link.
   - Formatted content tables for chapters, episodes, and story arcs while omitting extraneous navigation navboxes.
   - Extracted figure and gallery images with secure HTTPS URLs and captions displayed as rich inline image blocks.

2. **Expanded Universe Guide & 50-Title Chunked Lookups**
   - Added Characters, Story Arcs, Episodes, and Chapters to the guide categories.
   - Category existence checks are now chunked in 50-title batches (`clean.chunked(MAX_TITLES_PER_REQUEST)`), ensuring category scans never truncate at the API limit.

3. **Hierarchical Spoiler & Hidden Section Inheritance**
   - Subsections in the table of contents now inherit spoiler and hidden flags from ancestor sections. Child sections of hidden galleries or navigation blocks no longer leak into the TOC.
   - Incomplete table of contents requests display a retry button and avoid negative caching so retries actually re-query the MediaWiki API.

---

# Kitsugi-Beta v2.4.279

## 🇹🇷 Türkçe (v2.4.279)

### 🎬 Mini Oynatıcı Kontrolleri, Arka Plan Duraklatma & Video Paylaşımı
- **Mini Oynatıcı Kontrol Paneli:** Detay sayfasındaki Videolar sekmesinde ve gömülü oynatıcılarda modern kontroller eklendi: Ortada oynat/duraklat düğmesi, alt kısımda dokunarak ve sürükleyerek sarılabilen seek bar, dakika:saniye / toplam süre etiketi (`01:23 / 03:45`), oynat/duraklat ve tam ekran (fullscreen) düğmesi.
- **Akıllı Otomatik Gizleme & Çift Dokunma:** Video oynatılırken kontroller 3 saniye sonra yumuşakça gizlenir; ekrana dokunulduğunda tekrar açılır. Ekrana çift dokunma (double tap) ile ±5 saniye ileri/geri sarma desteği sağlandı.
- **Arka Plana Alınınca Otomatik Duraklatma:** Videolar sekmesinden başlatılan video oynatımı, uygulama arka plana (`ON_PAUSE` / `ON_STOP`) geçtiğinde otomatik olarak durur. Kullanıcı uygulamaya geri döndüğünde video aniden oynamaya devam etmez; oynatma kontrolü kullanıcıya bırakılır.
- **Videolar Sekmesinde Paylaş Butonu:** Video listesindeki her satırın sağına paylaşım ikonu eklendi. Paylaşımda `"Yapım Adı · Video Başlığı"` ve video bağlantısı (bağlantı bulunmuyorsa YouTube arama bağlantısı) paylaşılır.

### 🌐 Evrensel Orijinal / Çeviri Geçişi (Original / Translation Toggle)
- **Yeni Geçiş Düğmesi:** Çeviri gösterilen her ekranda, çeviri ikonunun hemen solunda "Orijinal" / "Çeviri" geçiş butonu (`KitsugiOriginalToggleButton`) yer alır.
- **Çift Yönlü Görünüm:** Metin çevrilmiş haldeyken buton "Orijinal" metnini gösterir; dokunulduğunda orijinal dile döner ve buton "Çeviri" haline gelir.
- **Kapsam:** Anime ve manga açıklamaları, API arama detayları, karakter ve ekip biyografileri, stüdyo "Hakkında" kartı, Fandom Wiki serisi özeti, wiki öğe panelleri (sheet), karakter/kişi wiki bilgi kutuları ve tüm açılır wiki bölümleri.

### 📚 Fandom Wiki: Wikidata P6262 Entegrasyonu, İlişkili Sayfalar & Orijinal Logo
- **Doğrudan P6262 Başlık Eşleme:** Wikidata P6262 kodu üzerinden doğrudan makale başlığı okunur; catalog ve arama yedekleriyle kusursuz eşleşme sağlanır.
- **Tüm Sağlayıcılarla Uyumluluk:** IMDb (`tt...`), Wikidata (`Q...`), Simkl, TMDB, AniList ve MyAnimeList kimlikleri tek çatı altında desteklenir.
- **İlişkili Sayfalar ve Kadro:** Yapıma ait ilişkili sayfalar, karakterler ve ekip listesi wiki sekmesine entegre edildi (`FandomMediaArticleMatcher`, `FandomRelationClassifier`).
- **Wikidata Karakter Oylaması:** Wikidata'da doğrudan P4073 bulunmadığında karakterlerin Fandom sayfaları üzerinden oylama yapılarak doğru wiki seçilir.
- **Galeride Orijinal Fandom Logosu:** Resim galerisinde kaynak rozetinde düz "Fandom" yazısı yerine yerel `#FA005A` Fandom marka vektör logosu (`ic_logo_fandom.xml`) gösterilir.

### 📋 Listem: Animasyonlu Başlık Barı & Premium Kategori Paneli
- **Jest Tabanlı Animasyonlu Başlık:** "Listem" ve kullanıcı listesi ekranlarında üst bar, aşağı kaydırınca yumuşakça çekilir, yukarı kaydırınca geri gelir (`KitsugiStickyHeaderReveal` + `rememberScrollAwareHeaderState`). Titreme ve zıplama olmadan akıcı jest takibi.
- **Premium Kategori Paneli:** Durum filtresi (Tümü / İzleniyor / Tamamlandı / Planlanan...) paneli "Filtrele & Sırala" paneliyle birebir aynı `KitsugiPremiumSheet` yüzey diline kavuşturuldu (aurora saç çizgisi, gradyan sürükleme kulpu, ikon karosu, rozet, yay fiziği, "Tümü / Bitti" butonları).

### ✨ Işık Küresi (KitsugiPlasmaLoader) & GC / RenderThread Optimizasyonu
- **Tek Noktadan Yüklenme Dili:** Uygulama genelindeki 52 dairesel yüklenme göstergesi (`CircularProgressIndicator`) tek tip akıcı plazma ışık küresi (`KitsugiPlasmaLoader`) ile birleştirildi.
- **Hafif Çizim Yolu & 32 Adım Renk Kuantalaması:** 30 dp ve altındaki küçük göstergeler için hafif çizim yolu (3 gradyan, dönen poligonlar devre dışı) eklendi; renk döngüsü 32 adıma kuantalanarak bellek ve GPU yükü düşürüldü.

---

## 🇬🇧 English (v2.4.279)

### 🎬 Mini Player Controls, Background Pause & Video Sharing
- **Mini Player Controls:** Inline videos in the Videos tab and detail screens now feature center Play/Pause, a bottom drag-and-seek bar, elapsed/total time (`01:23 / 03:45`), play/pause, and a working Fullscreen button.
- **Auto-Hide & Double Tap Seek:** Controls smoothly fade out after 3 seconds of continuous playback and reappear on tap. Double-tapping left/right seeks ±5 seconds.
- **Background Pause:** Inline playback automatically pauses whenever the app goes to the background (`ON_PAUSE` / `ON_STOP`). Playback does not resume automatically upon returning, keeping control with the user.
- **Share Button on Video Rows:** Added a share icon to each video entry in the Videos tab, sharing `"Media Title · Video Title"` with the direct video URL or a YouTube search fallback.

### 🌐 Universal Original / Translation Toggle
- **Toggle Button:** Everywhere translated text is displayed, an "Original" / "Translation" toggle button (`KitsugiOriginalToggleButton`) appears right beside the translation icon.
- **Bidirectional Switch:** Shows "Original" when viewing translated text; tapping reverts to the original language and changes the button text to "Translation".
- **Scope:** Anime/Manga synopsis cards, API result detail synopsis, Character & Staff biographies, Studio "About" sections, Fandom Wiki media summary, item detail sheets, character/staff wiki infoboxes, and all expandable wiki sections.

### 📚 Fandom Wiki: Wikidata P6262 Integration, Related Entities & Official Logo
- **Direct P6262 Article Matching:** Resolves article titles directly from Wikidata P6262 before catalog/name fallback paths.
- **Multi-Provider Support:** Supports IMDb (`tt...`), Wikidata (`Q...`), Simkl, TMDB, AniList, and MyAnimeList seamlessly.
- **Related Entities Extraction:** Surfaces related pages, cast, voice actors, and crew directly in the wiki tab (`FandomMediaArticleMatcher`, `FandomRelationClassifier`).
- **Wikidata Character Voting:** Elects the matching wiki through character page voting when direct P4073 is missing on the media entry.
- **Official Fandom Logo in Gallery:** Replaces plain "Fandom" text on image gallery source badges with the official `#FA005A` vector logo (`ic_logo_fandom.xml`).

### 📋 MyList: Scroll-Aware Animated Header & Premium Category Sheet
- **Gesture-Based Animated Header:** MyList and User Media List headers now smoothly slide out on downward scroll and return on upward scroll (`KitsugiStickyHeaderReveal` + `rememberScrollAwareHeaderState`) without flicker or jumps.
- **Premium Category Sheet:** Redesigned the status filter sheet (All / Watching / Completed / Planned...) to match the "Filter & Sort" sheet using `KitsugiPremiumSheet` (aurora hairline, gradient drag handle, icon tile, badge, spring physics, and "All / Done" buttons).

### ✨ Plasma Orb Loader & GC / RenderThread Optimizations
- **Unified Loading Language:** Replaced 52 circular progress spinners across 40 files with the unified glowing plasma orb (`KitsugiPlasmaLoader`).
- **Compact Draw Path & 32-Step Color Quantization:** Added a lightweight draw path for spinners <= 30 dp and quantized the color cycle to 32 steps to minimize churn and protect the RenderThread and garbage collector.


---

# Kitsugi-Beta v2.4.278

## 🇹🇷 Türkçe (v2.4.278)

### 🪟 Ayarlar Seçim Menüleri AniHyou Stili Modal Pencerelere Dönüştürüldü
- **Ortalanmış Modal Pencereler:** Ayarlar ekranındaki açılır seçimler artık satırların altında/üstünde taşarak açılmıyor; ekranın ortasında, arka planı karartan şık ve modern modal pencereler olarak açılıyor (`KitsugiSettingsDropdownDialog`).
- **Radyo Butonlu Seçenekler:** Tüm seçenekler net radyo düğmeleri ve tema renkleriyle vurgulanıyor.
- **Başlık ve Kapat Butonu:** Her pencereye ilgili ayarın açık başlığı ve sağ altta dokunulabilir / TV odaklanabilir **Kapat** eylemi eklendi.
- **Kaydırılabilir Uzun Listeler:** Uzun dil, format veya filtre listeleri modal pencere içinde kendi alanında dikey kaydırılabiliyor.
- **36 Seçim Menüsü Kapsandı:** Tema modu, uygulama dili, başlangıç sekmesi, anime/dizi başlık dili, ekip/karakter dili, liste düzeni, puanlama adımları, manga okuyucu filtre ve yönleri, varsayılan oynatıcı, intro atlama, MPV ayarları, altyazı stilleri, Stremio ve P2P profilleri dahil 36 aktif ayar menüsü uyarlandı.
- **İzole Davranış:** Arama, liste ve detay sayfalarındaki standart açılır menüler (`AnchoredPopup`) korunarak yalnızca ayarlar ağacı (`SettingsDialog`) dönüştürüldü.
- **Telefon, Tablet ve TV Uyumlu:** Genişlik (en fazla 560 dp) ve yükseklik (%86) sınırlarıyla tüm form faktörlerinde kusursuz görüntü.

### 🛡️ RenderThread Native Çökmesi (MIUI/HyperOS Force Dark) ve Genel Kararlılık Paketi
- **MIUI / HyperOS Force Dark (`TransformCanvas`) Çökmesi:** `values`, `values-v31` ve yeni `values-night` içindeki tüm temalarda `android:forceDarkAllowed="false"` zorlandı; Activity ve Dialog/BottomSheet pencere decorView'larında programatik olarak kapatıldı.
- **AngleLinearGradientBrush Yarışları & 0x0 Boyut Koruması:** `ShaderBrush` singleton yarışları thread-safe LRU shader cache ile çözüldü; Offscreen + SrcIn bileşenlerine `size > 0` kontrolleri eklendi.
- **HorizontalPager & Re-entrant Layout İyileştirmesi:** 9 `HorizontalPager` noktasında `beyondViewportPageCount = 0` yapıldı; profil sekme yükseklikleri `KitsugiProfileViewModel` içinde kalıcı saklanarak re-measure fırtınaları durduruldu.
- **GPU Buffer (AHardwareBuffer) & Bitmap Yaşam Döngüsü:** Coil `.allowHardware(false)` yapılandırılarak galeri kaynaklı GPU buffer tıkanmaları önlendi; boyut sınırları ve bitmap korumaları getirildi.
- **Detay Sayfası Animasyon & State Senkronizasyonu:** Detay sayfalarından çıkış animasyonu tamamlanmadan ekran temizlenmesi engellendi (`delay(350L)`); başlangıç sekmesi disk gecikmesiz senkron yüklendi (Profil sekmesi eklendi).
- **Güvenli Çağrılar & Benzersiz Lazy Anahtarlar:** Lambda/coroutine içi `!!` çağrıları güvenli null kontrollerine çevrildi; listelerde yinelenen anahtar hataları `kitsugiLazyKey` ile engellendi.

---

## 🇬🇧 English (v2.4.278)

### 🪟 Settings Selection Menus Redesigned into AniHyou-Style Modal Dialogs
- **Centered Modal Dialogs:** Settings dropdown panels no longer expand awkwardly below rows; they now present as centered, backdrop-dimmed modal dialogs (`KitsugiSettingsDropdownDialog`).
- **Radio-Button Option Rows:** Options now feature distinct radio buttons colored dynamically by the active theme.
- **Dialog Header & Close Button:** Each modal includes its descriptive setting title and an accessible **Close** action button.
- **Internal Vertical Scrolling:** Long lists (languages, score formats, filters) smoothly scroll within the modal container.
- **Comprehensive Scope (36 Menus):** Covers Theme Mode, App Language, Default Tab, Anime/Drama Title Languages, Staff/Character Name Languages, List Layout, Scoring Steps, Manga Reader Orientation/Color, Default Video Player, Intro Skip, MPV options, Subtitle Styles, Stremio Player, and P2P options.
- **Scoped to Settings:** Keeps search, library, and detail popups compact and unaffected via `LocalKitsugiDropdownPresentation`.
- **Responsive Across Phone, Tablet & TV:** Fitted with responsive maxWidth (560 dp) and maxHeight (86% screen) constraints.

### 🛡️ RenderThread Native Crash (MIUI/HyperOS Force Dark) & Comprehensive Stability Fixes
- **MIUI / HyperOS Force Dark Crash:** Disabled `android:forceDarkAllowed="false"` across all themes (`values`, `values-v31`, `values-night`) and decor views, resolving `libhwui.so` SIGSEGV (0x20).
- **AngleLinearGradientBrush Race Fix & 0x0 Size Protections:** Rewrote gradient brush with thread-safe LRU caching and added `size > 0` checks on Offscreen + SrcIn components.
- **HorizontalPager & Layout Stabilization:** Set `beyondViewportPageCount = 0` across 9 pagers and cached profile tab heights in `KitsugiProfileViewModel` to eliminate layout storms.
- **GPU Hardware Buffer & Bitmap Lifecycle:** Applied `.allowHardware(false)` to prevent `/dev/kgsl-3d0` buffer exhaustion in image galleries, added bounded size caps, and eliminated unsafe bitmap recycling.
- **Detail Screen Transition & Boot Synchronization:** Deferred `SaveableStateHolder` cleanup until the 280ms exit animation finishes (`delay(350L)`) and ensured instant boot tab resolution (Profile tab added).
- **Null-Safety & Unique Lazy Keys:** Replaced unsafe `!!` operators with safe null checks and ensured unique lazy list keys with `kitsugiLazyKey`.

---

# Kitsugi-Beta v2.4.277

## 🇹🇷 Türkçe (v2.4.277)

### 🖼️ Ana Sayfa Vitrini: Dikey Modda Poster Artık Ekrana TAM Oturuyor
- **Kök Neden Giderildi:** Vitrin bandının yüksekliğini gösterilen görselin oranına kilitleyen `homeHeroHeightDp` dikey dalı yazılmış ama daha önce çağrılmıyordu (`KitsugiHeroSection` dikey modda sabit `layout.heroHeight` kullanıyordu). Bu sebeple "otomatik vitrin" yalnızca yatay modda çalışıyor, dikey modda 2:3 bir poster 360×800 ekranda yalnızca ~257 dp genişliğinde (~%71) çiziliyor, iki yanda bulanık dolgu boşluğu kalıyordu.
- **Kutu Görsele Kilitlendi (Full-Bleed):** Dikey mod + dikey görselde bant yüksekliği artık `ekranGenişliği / görselOranı` olarak hesaplanıyor → kutu oranı görsel oranıyla birebir eşitleniyor. Sonuç: **kırpma ≈ %0**, **yan boşluk ≈ 0**; poster kenardan kenağa tek katman olarak çizilir.
- **Yeni Tavan (%74):** `HOME_HERO_PORTRAIT_VERTICAL_MAX_SCREEN_FRACTION` (0.74); 19.5:9 ve daha dar tüm telefonlarda 2:3 poster ekrana tam oturur (20:9 telefonda ekran yüksekliğinin ~%68'i).
- **Tavan Aşılırsa Koruma (Contain):** Kare veya tablet oranlı cihazlarda (veya aşırı uzun posterlerde) tavan aşılırsa hiçbir şey kırpılmaz; eski "bulanık dolgu + ortada kesilmemiş görsel" (contain) çift katman sunumu güvenle korunur.
- **Yatay Görselde Davranış Değişmez:** Dikey modda 16:9 key visual gösterilirken sabit bant korunur; başlık, rozetler, meta ve gösterge çubuğu okunur kalır.
- **Sarsıntı ve Titreme Önlemleri:**
  - Beklenen oran (poster 2:3 / arka plan 16:9) ilk kareden doğru kullanılıyor.
  - %5 ölçüm eşiği korunuyor (ufak ölçüm farklarında bant zıplamaz).
  - Full-bleed kararı animasyonlu yükseklikten değil **hedef** yükseklikten okunuyor; geçiş esnasında katman modu titreme yapmaz.
  - 404 alan bir adaydan sonraki adaya geçerken ölçülen oran sıfırlanıyor.
- **Yatay mod, tablet ve TV sunumu birebir korundu.**

---

## 🇬🇧 English (v2.4.277)

### 🖼️ Home Hero: Portrait Posters Now Fit the Screen Edge to Edge
- **Root Cause Eliminated:** The portrait branch of `homeHeroHeightDp` (locking the hero band to the artwork's aspect ratio) was defined but previously bypassed (`KitsugiHeroSection` used fixed `layout.heroHeight` in portrait). As a result, the dynamic showcase only functioned in landscape; in portrait, a 2:3 poster rendered only ~257 dp wide on a 360×800 screen (~71% width), leaving blurred padding on both sides.
- **Box Locked to Artwork (Full-Bleed):** In portrait mode with vertical artwork, hero band height is now dynamically calculated as `screenWidth / imageAspect` → box aspect matches image aspect exactly. Results: **crop ≈ 0%**, **side gaps ≈ 0%**; poster renders edge-to-edge as a single layer.
- **New Ceiling (74%):** `HOME_HERO_PORTRAIT_VERTICAL_MAX_SCREEN_FRACTION` (0.74); a 2:3 poster fits completely on all 19.5:9 or narrower phones (~68% screen height on 20:9).
- **Graceful Contain Fallback:** On square or tablet displays where the ceiling would be exceeded, artwork is never cropped; the classic dual-layer (atmospheric background + centered uncropped poster) is cleanly preserved.
- **Horizontal Artwork Preserved:** When displaying a 16:9 backdrop in portrait mode, the responsive fixed band is kept so title, badges, meta details, and indicator dots remain legible.
- **Jitter & Flicker Guards:**
  - Expected aspect ratio (2:3 for poster, 16:9 for backdrop) is applied from the very first frame.
  - 5% ratio threshold prevents band jumping on negligible size differences.
  - Full-bleed decision evaluates target height rather than the animating height, avoiding mid-transition flicker.
  - Advance to next candidate clears stale aspect ratio if an image candidate 404s.
- **Landscape, tablet, and Android TV behaviors remain fully intact.**

---

# Kitsugi-Beta v2.4.276

## 🇹🇷 Türkçe (v2.4.276)

### 🔤 Wiki Çeviri Butonları Detay Sayfasıyla Birebir Aynı Oldu
- **Tek Mantık, Üç Yerde:** Karakter/kişi wiki sekmesi (Wiki özeti, Bilgi kutusu, bölümler) ve yapım wiki sekmesi (seri özeti, çip/arama kartı) çeviri butonlarının tamamı, detay sayfasındaki **Açıklama** kartının çeviri butonuyla aynı bileşene (`WikiTranslateButton`) geçti.
- **Buton Artık Kaybolmuyor:** Çeviri tamamlandığında "Orijinali göster" yazı düğmesine dönüşmüyor; ikon her zaman yerinde kalıyor — tıpkı detay sayfasındaki gibi.
- **Harici Çeviri Uygulaması:** İkona dokununca metin, ayarlardaki tercihe göre (Google Translate / DeepL / TranslateYou) **harici çeviri uygulamasında** açılıyor; uygulama kurulu değilse Google Translate web'e düşüyor.
- **Yüklenme Animasyonu:** Uygulama içi (otomatik) çeviri sürerken ikon nabız gibi yanıp sönüyor ve etrafında dönen halka görünüyor; buton o süre dokunulamıyor. Animasyon hem wiki çevirisi hem sayfanın açıklama/biyografi çevirisi sürerken çalışıyor.
- **Parça Parça Akan Çeviri:** Metinler çeviri tamamlanmasını beklemeden geldikçe ekranda tamamlanıyor (detay sayfası açıklamasıyla aynı akış).

### ⚙️ Otomatik Çeviri Wiki'de Uçtan Uca Çalışıyor
- **Ayar Açıkken:** Sayfa çözümlenir çözülmez özet ve **bilgi kutusu** kendiliğinden çevriliyor; bölümler açıldıkça çeviriliyor; medya wiki kartı açıklamaları da otomatik çevriliyor.
- **Anında Tepki:** Çeviri sekmesi açıkken ayar açılırsa yüklü metinler hemen çevriliyor, kapanırsa orijinale dönülüyor, **hedef dil** değiştirilirse tüm çeviriler yeni dile tazeleniyor.
- **Gizlilik Koruması:** Bilgi kutusu harici çeviriye gönderilirken spoiler (gizli) alanların değerleri **gönderilmiyor**.

---

## 🇬🇧 English (v2.4.276)

### 🔤 Wiki Translate Buttons Now Match the Detail Page Exactly
- **One Behavior, Everywhere:** All wiki translate buttons — character/staff wiki tab (summary, infobox, sections) and media wiki tab (series summary, chip/search card) — now use the very same component (`WikiTranslateButton`) as the detail page's **Description** translate button.
- **The Button Never Disappears:** Once a text is translated, the icon no longer swaps to a "Show original" text button; it stays in place exactly like the detail page.
- **External Translator App:** Tapping the icon opens the text in an **external translator app** (Google Translate / DeepL / TranslateYou depending on your setting), falling back to Google Translate web when no app is installed.
- **Loading Animation:** While in-app (automatic) translation runs, the icon pulses and a spinning ring appears around it; the button is disabled meanwhile. The animation also triggers while the page's own description/biography translation is running.
- **Streaming Translation:** Text fills in progressively as translation chunks arrive — the same streaming behavior as the detail page description.

### ⚙️ End-to-End Automatic Translation for Wikis
- **When the Setting Is On:** The summary and **infobox** are translated as soon as the page resolves, sections are translated as they are expanded, and media wiki card blurbs are auto-translated too.
- **Instant Reaction:** Toggling the setting while the tab is open translates the loaded texts immediately, disabling it reverts to the original, and changing the **target language** refreshes every translation.
- **Privacy Guard:** Spoiler (hidden) infobox values are **never** sent to the external translator.

---

# Kitsugi-Beta v2.4.275

## 🇹🇷 Türkçe (v2.4.275)

### 🗂️ "Listem → Tümü" Çift Kart ve Sayı Şişkinliği Düzeltmesi (Bangumi Kimlik Köprüsü)
- **Kök Neden Giderildi:** Bangumi satırları yalnızca kendi kaynak kimliğini (500M ofseti) ve Çince/Japonca katalog adını (`name_cn`) taşıdığı için, AniList/MAL kayıtlarıyla ID veya İngilizce başlık üzerinden eşleşemiyordu ve "Tümü" sekmesinde her Bangumi kaydı ayrı bir kart olarak sayılıyordu (1502 − 716 = 786 şişkinliği).
- **Sıfır Ağ İstekli Yerel Kimlik Köprüsü (`MyListIdentityCrosswalk`):** Çapraz eşitlemenin yerelde sakladığı `cross_provider_identity_v1` ve `bangumi_id_mapping` (MAL↔条目) kayıtları okunarak satırların uzayına dokunulmadan kimlik haritası kurulur. Sıfır ağ isteğiyle çalışır.
- **Konsolidasyon Turu & Union-Find (`MyListLibraryGrouping`):** Sıra bağımlılığı giderildi; üye üye doğrulama ve birleştirme turu eklendi. Kimlik anahtarları satır başına bir kez hesaplanarak binlerce satırlık listelerde gruplama performansı optimize edildi.
- **Çift Yönlü Kalıcı Öğrenme (`CrossProviderIdentity`):** Bangumi satırından MAL'e inildiğinde de MAL↔Bangumi eşlemesi yerel önbelleğe iki yönlü yazılır; içe aktarılmış kayıtlar sonraki turlarda ağa çıkmadan birleştirilir.
- **Zıplamasız Arayüz:** Kimlik köprüsü arka planda kurulur; eski liste korunarak zıplama olmadan akıcı bir şekilde birleşik kartlara dönüştürülür.

---

## 🇬🇧 English (v2.4.275)

### 🗂️ "My List → All" Duplicate Tile & Count Inflation Fix (Bangumi Identity Crosswalk)
- **Root Cause Eliminated:** Bangumi rows exclusively carry their own provider ID namespace (500M offset) and catalog name (`name_cn`), preventing them from intersecting with AniList/MAL records by ID or Latin title. Consequently, every Bangumi entry was rendered as an isolated duplicate tile in "All" tab (1502 − 716 = 786 row inflation).
- **Zero-Network Local Crosswalk (`MyListIdentityCrosswalk`):** Reads locally persisted cross-provider identity cache (`cross_provider_identity_v1`) and Bangumi mappings (`bangumi_id_mapping`, MAL↔Subject) to bridge provider IDs without modifying stored rows or issuing network requests.
- **Order-Independent Consolidation (`MyListLibraryGrouping`):** Replaced order-dependent grouping with a union-find consolidation pass that verifies and merges groups. Identity keys are memoized once per row to ensure optimal performance across large libraries.
- **Bidirectional Cache Learning (`CrossProviderIdentity`):** Bidirectionally persists MAL↔Bangumi mappings when resolving from Bangumi rows, permanently resolving future offline grouping.
- **Jitter-Free UI:** Builds identity extras on an IO dispatcher while maintaining previous states, avoiding layout jumps.

---

# Kitsugi-Beta v2.4.274

## 🇹🇷 Türkçe (v2.4.274)

### 📊 Grafikler Sekmesi Yeniden Tasarlandı (StatsTab)
- **Tek Kart Sıralama:** AniList sıralamaları ("Popülerlik" ve "Puan") ince ayraçlı tek bir kompakt kartta toplandı. Sıra numaraları (#13) popülerlik için kırmızı, puan için turuncu tonlarında renkli rozetlerle belirginleştirildi.
- **Dengeli Durum Dağılımı:** Sağdan taşan neon kapsül butonlar kaldırıldı. Yerine 12 dp yüksekliğinde akıcı yığın gösterge çubuğu ve ekranı saran lejant çipleri (renk noktası + durum adı + adet + yüzde) eklendi; taşma ve kaydırma sorunları tamamen çözüldü.
- **Uyumlu Renk Paleti:** AniList'in çiğ renkleri yerine uygulamanın rafine paleti kullanıldı (İzleniyor `#34D399`, Tamamlanan `#60A5FA`, Planlanan `#818CF8`, Bırakılan `#FB7185`, Ara verilen `#94A3B8`).
- **Tema Uyumlu Puan Dağılımı:** Sabit gökkuşağı kaldırıldı; sütunlar aktif tema vurgusundan türetilerek en yüksek puan tam vurgu renginde, diğerleri koyudan açığa ölçeklendirildi (gradyan temalarla tam uyumlu). 10 eşit kolon kartı dengeli paylaşır; başlıkta toplam oy ve ortalama puan (ör. 725.516 oy · Ort. 81,5) ve TR yüzde formatı (%81,2) sunulur.
- **Sıfır Zıplama İskeleti:** `StatsTabLoadingSkeleton` yeni kart ve çubuk geometrisine tam uyarlandı; yükleme tamamlandığında düzen zıplamaz.

### 🌐 Fandom Evren Rehberi: "Devamını Göster" & Gerçek Kategori Sayfalaması
- **Kompakt Başlangıç & Sayfalama:** Her rehber grubu (Mekânlar, Örgütler, Kavramlar...) katlanmış halde ilk 8 çipi gösterir. "Devamını göster (+6)" tıklandığında hem gizli çipler açılır hem de Fandom API'sinden `cmcontinue` jetonuyla bir sonraki 14'lük sayfa arka planda gerçek zamanlı indirilir.
- **Yüzlerce Çipe Erişim:** Re:Zero gibi devasa evren wikilerinde yüzlerce kavram ve mekân çipine kesintisiz erişim sağlandı.
- **Akıllı Durum Yönetimi & TV Odağı:** Çipler indirilirken buton spinner ve "Yükleniyor…" durumuna geçer; hepsi yüklendiğinde "Daha az göster"e döner. Ağ hatasında mevcut çipler korunarak "Tekrar dene" ipucu sunulur. Çipler ve butonlar Android TV kumanda odağı ile tam uyumludur.
- **Sürümlü Önbellek (`guide:v2:`):** Yeni devam jetonlarını desteklemek için rehber önbellek anahtarı `guide:v2:` olarak güncellendi.

---

## 🇬🇧 English (v2.4.274)

### 📊 Redesigned Stats & Analytics Tab (StatsTab)
- **Unified Rankings Card:** Consolidated AniList popularity and score placements into a single card with subtle dividers. Rank indices (#13) are highlighted with distinctive color badges (red for popularity, orange for score).
- **Proportional Status Distribution:** Replaced overflowing neon pills with a 12dp stacked bar chart paired with wrapping status legend chips (color dot + status name + count + percentage), eliminating horizontal overflow and text clipping.
- **Harmonious Palette:** Replaced harsh upstream colors with Kitsugi's refined palette (Current `#34D399`, Completed `#60A5FA`, Planning `#818CF8`, Dropped `#FB7185`, Paused `#94A3B8`).
- **Theme-Aware Score Distribution:** Removed fixed rainbow styling; score bars now dynamically derive from the active accent color (gradient themes fully supported). Card displays total votes and average score with localized formatting.
- **Zero-Jitter Skeleton:** Synchronized `StatsTabLoadingSkeleton` with the new layout geometry to eliminate layout shifts when data finishes loading.

### 🌐 Fandom World Guide: "Show More" & Real Category Pagination
- **Collapsible Groups & Real Pagination:** Guide groups (Locations, Organizations, Lore...) start collapsed showing the first 8 chips with a "Show more (+count)" action chip. Clicking expands hidden items and fetches the next 14 entries via MediaWiki `cmcontinue` tokens.
- **Deep Lore Coverage:** Large wikis (e.g. Re:Zero) can now paginate through hundreds of categorized topic chips seamlessly.
- **Graceful States & TV Focus Support:** Features inline progress spinners during fetches, switches to "Show less" when fully expanded, and preserves loaded chips with retry hints upon network failure. Fully navigable via Android TV D-pad controllers.
- **Versioned Cache Key (`guide:v2:`):** Upgraded the guide cache key to `guide:v2:` to invalidate legacy tokenless caches cleanly.

---

# Kitsugi-Beta v2.4.273

## 🇹🇷 Türkçe (v2.4.273)

### 📚 Fandom Wiki Faz 3 (Bilgi Kutusu Çevirileri, Görsel Dayanıklılığı, Kopyalama & Tüm Sayfalarda Wiki)
- **Bilgi Kutusu Etiket & Değer Çevirileri:** `FandomInfoboxLabels` ile ~90 bilgi kutusu etiketi (Name, Species, Birthday, Affiliation, Occupation...), grup başlıkları ve kısa durum değerleri (Male/Female, Alive, Deceased...) arayüz diline çevrilir. TR/EN dil dosyalarına 178 yeni anahtar eklendi.
- **Toplu Çeviri & Kopyalama Düğmeleri:** Bilgi kutusuna tek tıkla toplu çeviri ("Orijinal" ile geri dönüş) ve "Etiket: Değer" formatında panoya kopyalama eklendi. Wiki özeti ve her açılan bölüm kartına 36 dp'lik, değer satırlarına ise 13 dp'lik minik kopyalama düğmeleri yerleştirildi.
- **Görsel Yükleme Dayanıklılığı:** Fandom CDN'e yönelik `Referer` ve `Accept` başlık filtreleri (`FandomImageHeaders`), ölçekli → özgün → format=original yedek zinciri (`FandomWikiImage`) ve kırık görsel ikonu eklendi.
- **Tüm Sayfalarda Wiki Desteği:**
  - **Ekip / Seslendirme Sanatçısı:** `StaffDetailPage` ekranına 4. sekme olarak "Wiki" eklendi; sanatçı, seslendirdiği ana yapımların evren wiki'lerinde aranır.
  - **Eklenti Kaynaklı Manga/Manhwa:** `MangaDetailScreen` üzerine açılır "Wiki" kartı eklendi; başlık bazlı çözümleme sağlandı.
  - **Android TV:** TV detay ekranının sonuna kumanda uyumlu açılır "Wiki" kartı eklendi.

### ✨ Detay Sayfalarında Ortak Akıcı Animasyonlar (Smooth Detail Transitions)
- **Akıcı Video Açılışı & Liste Genişlemesi:** Video önizlemeleri ve açılış/kapanış (OP/ED) tema listeleri pat diye açılmak yerine yumuşak yükseklik ve görünürlük geçişiyle (`animateDetailContentSize`, `KitsugiDetailReveal`) genişleyip daralır. "Daha fazla" ile eklenen satırlar da animasyonla belirir.
- **Hafif Oynatıcı Temizliği:** Video satırı kapatılırken oynatıcı motoru gecikmesiz derhal serbest bırakılır; yalnızca satır yüksekliği yumuşakça küçülür.
- **Kaydırmayla Gelen Yapışkan Üst Çubuklar:** Karakter, ekip/seslendiren, medya ve stüdyo sayfalarındaki kaydırma başlıkları (`rememberDetailStickyHeaderVisible`) ortak fade ve kayma hareketiyle akıcı bir şekilde belirip kaybolur.
- **Akordeon & Wiki Bölümleri:** Wiki açılır kartları, sezon akordeonları ve uzun açıklama metinleri aynı merkezi hareket token'larıyla standartlaştırıldı.

### 🎬 TMDB Keşfet Başlık Dil Önceliği (Türkçe → İngilizce → Romaji)
- **Otomatik Keşfet Yenileme:** Ayarlar'dan "Dizi & Film Başlık Dili" (`tmdbLanguage`) değiştirildiğinde Keşfet sayfası artık otomatik olarak tam yeniden yüklenir; önceden eski dildeki başlıklar önbellekte kalıyordu.
- **Takvim Önbelleğinde Dil Desteği:** "Yakında Yayında" takvim şeridi önbellek anahtarına aktif TMDB dili eklendi (`tmdb|anon|tr-tr`), dil değişiminde 90 saniye eski dilde kalması önlendi.
- **Kanji Sızması Engellendi & Backoff:** İngilizce (`en-US`) yedek başlık isteklerine backoff'lu (300ms/700ms) 3 deneme mekanizması eklendi. Paralel isteklerde oluşan 429/zaman aşımı kaynaklı CJK/kanji sızması giderildi.
- **Kırılganlık Giderildi & Önbellek Geçersiz Kılma:** Bozuk tekil JSON kayıtlarının tüm şeridi düşürmesi `optJSONObject` ile engellendi. Eski hatalı başlıklı disk önbelleklerini geçersiz kılmak için `MediaTitleResolver.VERSION` 2'den 3'e yükseltildi.

### 🎌 Bangumi "Listem" Artık Başlık Dili Ayarına Uyuyor
- **Sorun:** Bangumi koleksiyonu içe aktarılırken API yalnızca özgün adı (`name`, çoğu zaman Japonca) döndürüyor; İngilizce/Romaji karşılığı **hiç gelmiyordu**. Bu yüzden "Listem → Bangumi" listesi, Ayarlar → Görünüm & Tercihler → **Anime & Manga Başlık Dili** ne seçilirse seçilsin CJK (Japonca/Çince) adla çiziliyordu.
- **Kalıcı Başlık Tamamlama:** Yeni `BangumiLibraryTitleHydrator`, kütüphanedeki eksik Latin adları arka planda tamamlar. Sıra: bellek önbelleği → Bangumi subject infobox'ı (`英文名` / `罗马字` / `别名`) → AniList birebir eşleşmesi. Sonuç veritabanına yazılır, yani **bir sonraki açılışta hiç ağ isteği yapılmaz**.
- **Anında Uygulama:** Latin ad öğrenildiği anda önbellekten okunur; liste, detay sayfası veya keşfet vitrininden hangisi önce yüklenirse yüklensin başlık aynı dile döner.
- **Özgün Ad Kaybolmaz:** Yeni Latin ad ana başlığa yazılırken özgün ad `titleJapanese` alanına taşınır; "Ana Dilde" seçeneği bozulmaz.
- **Bangumi Profil Listesi:** Profildeki koleksiyon satırları da seçilen başlık diline uyar.
- **Ağ/Performans Koruması:** Tamamlama yalnızca Latin adı gerçekten eksik satırlar için ve yalnızca "Listem" açıldığında çalışır; eşzamanlılık sınırlı, sonuçlar önbelleğe yazıldığı için tekrar denenmez.

### ⭐ Harici Puanlar (MDBList) Kartı Yeniden Tasarlandı
- **Uygulamanın Kendi Dili:** IMDb / Rotten Tomatoes / Metacritic / Letterboxd / TMDB / Trakt kutuları artık tam doygun renkli bloklar değil; "İstatistikler" ve "Türler" bloklarıyla aynı zemin, köşe yarıçapı ve tipografiyi kullanan bir kart. Puanlar marka rengine hafifçe boyanmış yastıklarda, büyük ve okunur biçimde.
- **Kesilme Yok:** Puanlar yatay kaydırılan şeride sığmadığı için kesilmiyor; ekran genişliğine eşit dağılan ızgaraya yerleşiyor (5–7 puan iki satıra dengeli biçimde bölünür).
- **"0 Metacritic" Hatası Giderildi:** MDBList bazı kayıtlar için "puan yok" yerine **0** döndürüyordu ve ekranda `0` puanlı anlamsız kutular görünüyordu. Artık 0 değerleri "puan yok" kabul edilir; hiç puan yoksa kart tamamen gizlenir.
- **Yükleme İskeleti:** Kart iskeleti yeni ızgara geometrisine uyarlandı; yükleme bitince düzen zıplamaz.

### 🗳️ Wikidata Karakter Oylaması ile Akıllı Fandom Wiki Keşfi & Simkl Desteği
- **Karakter Sayfalarından Wiki Oylaması:** Wikidata üzerinde yapımın doğrudan Fandom bağlantısı (P4073) bulunmadığında (yapımların %99'unda eksik), medyanın QID'i bulunur → ters P1441 ("present in work") ile karakterler taranır → karakterlerin P6262 Fandom sayfa kimlikleri üzerinden wiki oylaması (`WikidataCharacterVotes`) yapılır.
- **Güvenli Eşik & Doğrulama:** Bir wiki'nin seçilmesi için en az 2 oy alması, ikinciden kesin fazla olması ve `siteinfo` ile doğrulanması (canlı ve ≥20 makale) şartı getirildi. Dil wikileri ve engelli wikiler elenir.
- **Simkl Entegrasyonu:** `FandomSourceIds` modeline `simklId` eklendi; `fromStableId("simkl", ...)` ofset dönüşümü tamamlandı ve detay sayfalarından Fandom sorgusuna iletildi.
- **Yeniden Deneme:** Wiki kartında "Tekrar dene" tıklandığında eski olumsuz önbellekler temizlenerek çözümleme sıfırdan koşturulur.

---

## 🇬🇧 English (v2.4.273)

### 📚 Fandom Wiki Phase 3 (Infobox Translations, Image Resilience, Copy Actions & Global Wiki Tabs)
- **Infobox Label & Value Localization:** Integrated `FandomInfoboxLabels` supporting ~90 infobox keys (Species, Birthday, Affiliation, Occupation...), group headers, and finite status values (Male/Female, Alive, Deceased...) localized via strings.xml.
- **Batch Translation & One-Tap Copy:** Added batch translation for character infoboxes alongside structured full-infobox copy actions. Added 36dp copy icon buttons for summary & section cards and compact 13dp copy buttons for individual attribute rows.
- **Resilient Image Pipeline:** Introduced `FandomImageHeaders` (`Referer` & AVIF-safe headers), `FandomWikiImage` multifold fallback chain (scaled → original → `format=original`), and broken-image indicator.
- **Ubiquitous Wiki Integration:**
  - **Staff / Voice Actor Screens:** Added a dedicated 4th "Wiki" tab to `StaffDetailPage` querying media wikis for the person's voiced characters.
  - **Extension Manga/Manhwa:** Added a collapsible "Wiki" card on `MangaDetailScreen` with title-fallback cache resolution.
  - **Android TV:** Added a remote-friendly collapsible "Wiki" section card at the end of TV detail screens.

### ✨ Smooth Detail Animations & Transitions
- **Fluid Video & List Expansions:** Video preview panels and opening/ending theme song lists now expand and collapse smoothly (`animateDetailContentSize`, `KitsugiDetailReveal`) rather than popping into view abruptly. Additional loaded rows animate in seamlessly.
- **Instant Media Player Release:** Closing a video row instantly releases the media player engine while the container height gracefully shrinks.
- **Sticky Header Transitions:** Scrolling floating top bars across character, staff/voice actor, media, and studio detail screens (`rememberDetailStickyHeaderVisible`) smoothly fade and slide into view without jitter.
- **Standardized Accordion & Wiki Sections:** Wiki collapsible cards, season accordions, and synopsis expanders now utilize centralized, unified motion tokens.

### 🎬 TMDB Explore Title Language Priority (Turkish → English → Romaji)
- **Automatic Explore Refresh:** Changing the "Series & Movie Title Language" (`tmdbLanguage`) now automatically refreshes the Explore screen, eliminating stale in-memory cached titles.
- **Language-Aware Airing Calendar:** Added the active TMDB language to the upcoming calendar cache key (`tmdb|anon|tr-tr`), preventing stale language serving during the 90-second TTL.
- **Kanji Fallback Elimination:** Implemented 3 retries with backoff (300ms/700ms) for `en-US` fallback title fetching, preventing inadvertent CJK/kanji fallbacks under rate limits (429) or timeouts.
- **Parsing Resilience & Cache Invalidation:** Switched to `optJSONObject` so a single corrupted item no longer breaks entire discover rows. Bumped `MediaTitleResolver.VERSION` from 2 to 3 to invalidate stale legacy title caches on disk.

### 🎌 Bangumi "My List" Now Follows the Title Language Setting
- **Root Cause:** The Bangumi collection API returns only the original name (`name`, usually Japanese) and never provides the English/Romaji counterpart. As a result, the Bangumi tab of My List always rendered CJK titles regardless of **Settings → Appearance & Preferences → Anime & Manga Title Language**.
- **Permanent Title Completion:** A new `BangumiLibraryTitleHydrator` fills in missing Latin titles for library rows in the background. Order: in-memory cache → Bangumi subject infobox (`英文名` / `罗马字` / `别名`) → exact AniList match. Results are written to the database, so **no network request happens on the next launch**.
- **Immediate Application:** Once a Latin title is known, it is served from cache — the list reflects the chosen language no matter which screen (list, detail, or explore) resolved it first.
- **Original Name Preserved:** The original name is moved into the `titleJapanese` field, so the "Native" option keeps working.
- **Bangumi Profile List:** Collection rows on the profile screen now follow the selected title language too.
- **Network/Performance Guard:** Completion only targets rows that actually lack a Latin title and only runs when My List is opened; concurrency is limited and results are cached so nothing is retried.

### ⭐ External Ratings (MDBList) Card Redesigned
- **Native App Language:** IMDb / Rotten Tomatoes / Metacritic / Letterboxd / TMDB / Trakt tiles are no longer fully saturated color blocks. They now use the same surface, corner radius, and typography as the "Statistics" and "Genres" blocks, with large readable scores on brand-tinted tiles.
- **No More Cut-off:** Ratings no longer overflow a horizontally scrolled strip; they are laid out in a grid that spans the screen width (5–7 ratings split across two balanced rows).
- **"0 Metacritic" Fixed:** MDBList returned **0** instead of "no score" for some entries, which rendered meaningless zero boxes. Zero is now treated as "no score", and the card hides entirely when nothing is available.
- **Loading Skeleton:** The skeleton mirrors the new grid geometry so the layout no longer jumps when loading finishes.

### 🗳️ Smart Fandom Wiki Discovery via Wikidata Character Voting & Simkl Support
- **Character Page Wiki Voting:** When an entry lacks a direct Fandom wiki property (P4073) on Wikidata (missing in ~99% of media), media QIDs are looked up → reverse P1441 ("present in work") queries characters → character P6262 Fandom article claims vote on the correct wiki (`WikidataCharacterVotes`).
- **Strict Consensus & Validation:** A wiki candidate must achieve at least 2 votes with a strict plurality over runner-ups, and is validated via `siteinfo` (active and ≥20 articles). Language variants and blocked wikis are filtered out.
- **Simkl Integration:** Added `simklId` to `FandomSourceIds`, integrated Simkl stable ID offset resolution, and piped Simkl IDs from media detail views into wiki queries.
- **Cache Invalidation on Retry:** Tapping "Retry" on wiki cards now flushes negative query caches to perform a clean re-resolution.

---

# Kitsugi-Beta v2.4.272

## 🇹🇷 Türkçe (v2.4.272)

### ⚡ AniList GraphQL Senkronizasyon Düzeltmesi (Direct Interpolation)
- **Doğrudan Dolar İşareti İnterpolasyonu:** AniList `SaveMediaListEntry` GraphQL mutasyon sorgusu artık metin yerine doğrudan Kotlin `${'$'}` interpolasyonu ile derlenir; kırılgan ardışık `.replace()` zinciri kaldırıldı.
- **Puan Güvenliği:** Puanı bulunmayan kayıtlarda `scoreRaw` değişkeni GraphQL mutasyonuna dahil edilmez. Bu sayede AniList'teki mevcut puanların silinmesi veya API'nin hata vermesi engellendi.
- **Kullanıcı Aitlik Doğrulaması:** AniList liste kayıtlarının (`aniListEntryId`) güncellenmesi veya silinmesi öncesinde, kaydın gerçekten oturum açmış kullanıcıya (`Viewer.id`) ait olduğu ve hedeflenen medyayla eşleştiği doğrulanır.

### 🛡️ Çapraz Senkronizasyon Güvenlik Denetimi & Kimlik Koruma Kalkanı (Sync Safety Audit)
- **Resmî Katalog Doğrulama Kapısı:** Farklı hesaplar arası (cross-account) yazmalarda, hedef platforma kayıt eklenmeden önce resmî MAL kataloğundan başlık, medya türü ve yıl doğrulanır; üçüncü parti tahminler ve belirsiz eşleşmeler engellenir.
- **Bangumi Çapraz Yazma Koruması (Fail-Closed):** Desteklenen API yolunda yetkili MAL↔Bangumi kimlik eşlemesi bulunmadığından, yanlış içerik yazılmasını önlemek amacıyla çapraz eklemeler güvenli tarafta kalarak atlanır; yalnızca aynı hesaptaki doğrulanmış kayıtlar güncellenir.
- **Kimlik Uzayı (Namespace) Ayrımı & Taşma Koruması:** Kitsu, Shikimori ve Bangumi sağlayıcı kimliklerinin birbirine ve MAL ID uzayına karışması engellendi. Kitsu ve Bangumi ID dönüşümlerine 32-bit tamsayı taşma kontrolü eklendi.
- **Doğrulama Birim Testleri:** `CrossSyncEntryFactoryTest`, `KitsuMediaIdentityTest`, `KitsuIdNamespaceTest` ve `CrossSyncIdentityGuardTest` ile senkronizasyon sınırlarının tam gerileme testi sağlandı.

### 📚 Fandom Wiki Faz 2 (Anında Sekme, Başlık Çevirileri & Medya Wiki Sekmesi)
- **Sıfır Zıplama & Anında Sekme:** Karakter detay sayfasında wiki sekmesi sayfa açılır açılmaz shimmer iskeletiyle belirir. Çözümleme tamamlandığında düzen hiç zıplamadan içerik doldurulur.
- **24+ Başlık Çeviri Tablosu:** "Role in the Series", "Appearances in Other Media", "Creation and Design", "Legacy", "About" gibi sık karşılaşılan Fandom başlıkları Türkçeleştirildi.
- **Medya Detayında Wiki Sekmesi:** Anime ve manga detay sayfalarına genel özet, içindekiler ve bilgi kutusu içeren Fandom Wiki sekmesi entegre edildi.

---

## 🇬🇧 English (v2.4.272)

### ⚡ AniList GraphQL Mutation Hardening (Direct Interpolation)
- **Direct Variable Interpolation:** The AniList `SaveMediaListEntry` GraphQL mutation now uses direct Kotlin `${'$'}` variable interpolation instead of error-prone string `.replace()` calls.
- **Score Integrity:** The `scoreRaw` variable and mutation argument are only emitted when a valid score exists, preventing inadvertent score resets or schema rejections on unscored media.
- **Viewer Ownership Verification:** List mutations and deletions confirm that the targeted `aniListEntryId` belongs to the authenticated viewer (`Viewer.id`) and matches the requested media identity before mutating.

### 🛡️ Cross-Account Sync Safety Hardening (Audit Implementation)
- **Authoritative Identity Gate:** Cross-account writes strictly require identity proof verified against the authoritative MAL catalog (type, exact title alias compatibility, and release year) before mutating target accounts.
- **Bangumi Fail-Closed Protection:** Because the supported Bangumi API lacks an authoritative MAL crosswalk, cross-account writes fail closed to protect user libraries from mismatched writes. Same-account native updates remain fully supported.
- **Strict Namespace Isolation & Overflow Safeguards:** Provider-native IDs (Kitsu, Shikimori, Bangumi) are strictly segregated from the 1..99M MAL range. 32-bit integer overflow protection applied to ID offsets.
- **Comprehensive Regression Tests:** New regression test suites (`CrossSyncEntryFactoryTest`, `KitsuMediaIdentityTest`, `KitsuIdNamespaceTest`, `CrossSyncIdentityGuardTest`) guarding identity resolution and boundary mutations.

### 📚 Fandom Wiki Phase 2 (Instant Tab Skeleton, Section Translation & Media Wiki)
- **Zero-Layout-Shift Tab:** The wiki tab appears instantly upon screen open with a shimmering placeholder skeleton, eliminating layout jumps and flickering.
- **Expanded Section Title Localization:** Added a dedicated dictionary mapping 24+ common Fandom wiki section headers to Turkish ("Role in the Series", "Appearances in Other Media", "Creation/Design", etc.).
- **Media Detail Wiki Tab:** Integrated encyclopedic Fandom wiki overviews, table of contents, and infoboxes into Anime and Manga detail screens.

---

## 🇹🇷 Türkçe (v2.4.271)

### 📚 Fandom Wiki Entegrasyonu (Faz 1 — Karakter Detayı & Ayarlar)
- **Karakter Sayfasında Wiki Sekmesi:** Karakter detay ekranına Fandom Wiki sekmesi eklendi. Biyografi, görünüş, kişilik, yetenekler ve ilişkiler gibi zengin ansiklopedik bilgiler artık doğrudan karakter sayfasında.
- **Güvenilir Kimlik Eşleştirme (Wikidata Köprüsü):** Eşleştirme rastgele isim araması yerine öncelikle Wikidata ID'leri üzerinden yapılır. Doğrulanamayan kayıtlar gösterilmez.
- **Doğrulama & Bildirim Kartları:** "Bu eşleşme doğru mu? (Evet / Hayır)" onay kartı eklendi. "Yanlışsa bildir" tıklandığında hatalı wiki kaydı kalıcı olarak engellenir ve sıradaki aday denenir.
- **Spoiler Koruması & Türkçe Önceliği:** Karakterin durumu ve hikâye bölümleri istek üzerine spoiler örtüsüyle gizlenir; dokunulduğunda açılır. Türkçe fandom sayfası mevcutsa öncelikli olarak gösterilir.
- **TV & Mobil Ayar Yönetimi:** Hem mobil hem Android TV Ayarlar menüsüne Fandom Wiki kontrol paneli ve tek tıkla önbellek temizleme seçeneği eklendi.

### 🎬 Detay Sayfalarında "Videolar" Sekmesi & Hızlı Oynatıcı
- **Ayrı Videolar Sekmesi:** Fragmanlar, açılış (OP) ve kapanış (ED) müzik videoları "Bilgi" sekmesinden ayrılarak "Resimler" sekmesinin yanındaki yeni "Videolar" sekmesine taşındı.
- **Kesintisiz Oynatıcı Yönetimi:** Videolar arasında hızlı geçiş yapıldığında eski oynatıcı ve devam eden YouTube istekleri anında temizlenir; oynatıcının donması veya çakışması önlenir.

### 🏢 MAL Manga Stüdyo, Yapımcı & Yazar Düzeltmeleri
- **Doğru Rol Eşlemesi:** Manga yazarları (`Kenji Inoue` vb.) yanlışlıkla stüdyo/yapımcı listesinde yer almak yerine artık doğru kişi (Staff) sayfasına yönlenir.
- **Dergi Filtreli Arama:** Manga dergileri (`good! Afternoon` vb.) Jikan `magazines={id}` filtresiyle o dergiye ait tüm mangaları listeler.
- **Yayım Durumu Çevirisi:** MAL manga sayfalarındaki `currently_publishing` ham değeri "Yayımlanıyor" olarak Türkçeleştirildi.

### 🎴 Neon Kart Çerçeveleri & Vitrin İyileştirmesi
- **Opsiyonel Kart Çerçeveleri:** Ayarlar'dan açılabilen neon degrade çerçeveler Keşfet vitrinine, takvim kartlarına ve kaynak kutularına bağlandı.
- **Akıcı Vitrin:** Ana sayfa vitrininde yükseklik sıçramaları ve kaydırma sarsıntıları giderildi.

## 🇬🇧 English (v2.4.271)

### 📚 Fandom Wiki Integration (Phase 1 — Character Detail & Settings)
- **Wiki Tab on Character Pages:** Introduced a dedicated Fandom Wiki tab for character details, providing in-depth infoboxes, biographies, appearances, abilities, personality, and relationships.
- **Verified Identity Matching (Wikidata Bridge):** Matches rely primarily on Wikidata IDs rather than ambiguous name searches, ensuring accurate fandom resolution.
- **Verification & Feedback Cards:** Added "Is this match correct? (Yes / No)" cards. Tapping "Report wrong match" permanently disassociates the incorrect wiki and advances to the next candidate.
- **Spoiler Guard & Turkish Priority:** Character status and story plot sections can be kept hidden behind a spoiler barrier until tapped. Prefers Turkish wikis when localized content is available.
- **TV & Mobile Settings Integration:** Comprehensive Fandom Wiki toggle, spoiler controls, and cache wipe action added to both Mobile and Android TV Settings.

### 🎬 Dedicated "Videos" Tab & Rapid Player Switching
- **Separated Videos Tab:** Trailers, opening themes (OP), and ending themes (ED) have moved from Overview into a new "Videos" tab adjacent to Gallery.
- **Seamless Player Transitions:** Rapid switching between videos cleanly terminates existing playback and pending YouTube network requests, preventing lockups and overlapping audio.

### 🏢 MAL Manga Studio, Producer & Author Resolution Fixes
- **Accurate Role Mapping:** Manga authors are properly attributed with the `AUTHOR` role and link directly to person (Staff) detail pages.
- **Magazine Facet Search:** Manga serialization chips redirect to filtered searches targeting all manga published under that magazine (`magazines={id}`).
- **Publishing Status Localization:** Raw MAL status string `currently_publishing` is now properly localized.

### 🎴 Neon Card Frames & Showcase Polish
- **Neon Glow Frames:** Optional neon border modifiers integrated across Explore hero cards, calendar items, and source tiles.
- **Smooth Showcase:** Stabilized Explore hero dimensions to eliminate layout shifts and stutter during scrolling.

---

## 🇹🇷 Türkçe (v2.4.270)

### 🔄 Çapraz Eşitleme Artık Altı Kaynakta: Kitsu · Shikimori · Bangumi Dahil
- **Tek Kimlik Köprüsü (`CrossProviderIdentityResolver`):** Bir kayıt hangi kaynaktan eklenirse eklensin (AniList, MAL, Simkl, Kitsu, Shikimori, Bangumi) altı kimlik birden çözülür: MAL, AniList, Kitsu, Shikimori, Bangumi `subject_id` ve TMDB/Simkl. Eski zincir yalnızca gerçek MAL ID’yi ve AniList offset’ini tanıyordu; Simkl (150M), Shikimori (400M) ve Bangumi (500M) satırlarında kimlik çözülemediği için Kitsu/Shikimori/Bangumi eşitlemesi sessizce atlanıyordu.
- **Bangumi’den Diğer Kaynaklara Yayılım:** Bangumi koleksiyonuna eklenen/düzenlenen kayıt, doğrulanmış MAL/AniList kimliği çözülebildiğinde artık AniList · MAL · Simkl · Kitsu · Shikimori listelerine de yazılır. Kimlik güvenle çözülemezse yalnızca Bangumi kalır ve gerekçe ekranda gösterilir — yanlış yapım başka listeye eklenmez.
- **„Atlandı” Artık Hata Değil:** Katalogda karşılık bulunamayan kaynak, işlemi başarısız saymıyor; bekleme kuyruğu sonsuz deneme döngüsüne girmiyor. Hata yalnızca ağ/oturum/sunucu reddinde sayılır.
- **Görünür Sonuç:** Snackbar ve rapor artık kaynak bazlı özet gösterir (ör. „AniList: eşitlendi · Kitsu: kimlik çözülemedi · Bangumi: hata”), „neden eşitlenmedi” sorusu raporu açmadan cevaplanır.
- **Kalıcı Eşleme Önbelleği:** Çözülen kimlikler `cross_provider_identity_v1` içinde saklanır; ilerleme her değiştiğinde yeniden katalog araması yapılmaz. MAL↔Bangumi ve MAL↔Kitsu köprüleri başarıdan sonra öğrenilir.
- **Yerel Satırın Kimliği Korunur:** Çözülen gerçek MAL ID yalnızca uzak çağrılarda kullanılır; Kitsu/Shikimori/Bangumi satırları kendi stableId’sini kaybetmediği için o kaynağın liste ekranında görünmeye devam eder.
- **Hız Sınırları ve Ekleme Kapısı:** Canlı eşitleme de `PlatformRateLimiter` kullanır (6 kaynak art arda yazıldığında 429’ları önler); „en az bir hesap bağlı” kontrolü artık Kitsu/Shikimori/Bangumi’yi de tanır, Keşfet’ten ekleme bu kaynaklardan da yapılabilir.
- **Doğrulama & Test:** 13 birim testi ile doğrulandı (`CrossProviderIdentityTest`); `R.string` anahtarları TR/EN için eklendi. Detaylar: `CROSS_SYNC_ALTI_KAYNAK_FIX_RAPORU_2026-10-12.md`.

## 🇬🇧 English (v2.4.270)

### 🔄 Cross-Sync Now Covers All Six Sources: Kitsu · Shikimori · Bangumi Included
- **Single Identity Bridge (`CrossProviderIdentityResolver`):** No matter which source an entry was added from (AniList, MAL, Simkl, Kitsu, Shikimori, Bangumi), all six IDs are resolved — MAL, AniList, Kitsu, Shikimori, Bangumi `subject_id` and TMDB/Simkl. The previous chain only understood a real MAL ID and the AniList offset, so Simkl (150M), Shikimori (400M) and Bangumi (500M) rows silently skipped Kitsu/Shikimori/Bangumi.
- **Fan-Out from Bangumi:** Entries added or edited in the Bangumi collection now also reach AniList · MAL · Simkl · Kitsu · Shikimori whenever a verified MAL/AniList identity can be resolved. If it cannot, only Bangumi is written and the reason is shown — a wrong title is never pushed into another list.
- **„Skipped” is No Longer an Error:** A provider without a catalogue match no longer fails the whole operation, so the pending queue stops retrying forever. Only network/session/server rejections count as errors.
- **Visible Outcome:** The snackbar and report now show a per-source summary (e.g. „AniList: synced · Kitsu: identity unresolved · Bangumi: failed”), answering „why was it not synced?” without opening logs.
- **Persistent Mapping Cache:** Resolved identities are stored in `cross_provider_identity_v1`; progress updates no longer trigger new catalogue searches. MAL↔Bangumi and MAL↔Kitsu bridges are learned after a successful write.
- **Local Row Identity Preserved:** The resolved real MAL ID is used only for remote calls, so Kitsu/Shikimori/Bangumi rows keep their own stableId and stay visible in that provider’s list screen.
- **Rate Limits and the Add Gate:** The live path now respects `PlatformRateLimiter` (prevents 429s when six writes fire back-to-back) and the „at least one account connected” check accepts Kitsu/Shikimori/Bangumi, so adding from Explore works with those sources alone.
- **Verification & Tests:** Verified by 13 unit tests (`CrossProviderIdentityTest`); `R.string` keys added for TR/EN. Details: `CROSS_SYNC_ALTI_KAYNAK_FIX_RAPORU_2026-10-12.md`.

---

## 🇹🇷 Türkçe (v2.4.269)

### 🌸 Yeni Premium Açılır Panel (KitsugiPremiumSheet)
- **Yay Fiziği & Aurora Glow:** Açılır panellere hafifçe sekerek açılan yay (spring) animasyonlu giriş ve yumuşak kayarak kapanış eklendi. Vurgu rengiyle uyumlu parlayan gradyan kenarlık ve renkli aurora gölgesi entegre edildi.
- **Modern Detaylar:** Gradyan sürükleme kulpu, gradyan ikon karosu, saç çizgisi ayracı, aktif rozet ve modern kapatma butonu. Skrim (karartma) dışına dokunulduğunda akıcı animasyonla kapanır; Android TV'de ortalanmış dialog görünümüne otomatik uyum sağlar.
- **Tema Entegrasyonu:** `LocalKitsugiAccent`, `Brush` ve `OnAccent` ile hem düz hem de gradyan temalarda renk uyumu korunur.

### 📋 Listem Filtre Alanı Panele Taşındı (KitsugiMyListFilterSheet)
- **Zıplamasız Liste Deneyimi:** Ekrandaki satır içi filtre alanı kaldırıldı; sağ üstteki filtre butonu artık yeni premium açılır paneli tetikler. Liste boyutu kaybolmaz ve zıplama yaşanmaz.
- **Tüm Filtreler Tek Panelde:** İçerik türü hapları, Durum, Medya Türü, Puan, Yıl, Ekstra ve Sıralama seçenekleri gradyan noktalı modern başlıklarla panele toplandı. "N aktif" rozeti, Temizle ve gradyan Bitti butonları eklendi.
- **Profil Listesi Uyumu:** Profil sekmesindeki kullanıcı listesi de aynı başlığı ve paneli paylaşır.

### ⚙️ Ayarlar Sekmelerinde Yenilenen Açılır Menüler
- **KitsugiDropdownMenu:** Yay animasyonlu "pop" açılış, gradyan kenarlık, aurora ışık bandı ve renkli glow ile premium yüzeye geçirildi.
- **KitsugiMenuItem:** Seçili satırda yay animasyonlu gradyan seçim çubuğu ve sekerek beliren onay işareti entegre edildi (layout kayması engellendi).
- **Kapsam:** Tercihler, Oynatıcı, Eklentiler, P2P, Stremio ve sistem ayarlarındaki tüm açılır menüler güncellendi.

## 🇬🇧 English (v2.4.269)

### 🌸 New Premium Sheet Architecture (KitsugiPremiumSheet)
- **Spring Physics & Aurora Glow:** Bottom sheets now enter with a delightful spring bounce and exit with smooth damping. Features gradient borders and ambient aurora glow tinted by the active theme accent.
- **Polished Sheet Controls:** Features a gradient drag handle, gradient icon tile, hairline divider, active badge, and modern dismiss button. Tap outside triggers animated dismissal; automatically adapts into a centered dialog on Android TV.
- **Theme Native:** Built with `LocalKitsugiAccent`, `Brush`, and `OnAccent` for seamless harmony across solid and gradient themes.

### 📋 MyList Filters Relocated to Premium Sheet (KitsugiMyListFilterSheet)
- **Stable Layout:** Replaced the inline filter section with a dedicated premium filter sheet opened via the top-right filter icon, eliminating list jumps and viewport shifting.
- **All Filter Categories in One Sheet:** Status, Media Type, Score, Year, Extra, and Sorting options organized under gradient-dotted section headers. Includes "N active" indicator, Reset, and Done actions.
- **Profile User List Parity:** Profile screen user list automatically inherits the new header and filter sheet.

### ⚙️ Overhauled Dropdown Menus Across Settings
- **KitsugiDropdownMenu:** Upgraded to the premium surface language featuring a spring "pop" entrance, gradient borders, aurora highlights, and ambient glow.
- **KitsugiMenuItem:** Features an animated spring selection bar and bouncing checkmark on selected items without layout shift.
- **Universal Application:** Applies across Preferences, Player, Extensions, P2P, Stremio, and all settings tabs.

---

## 🇹🇷 Türkçe (v2.4.268)

### 🔄 Çeviri Butonlarında Dönen Halka & Nabız Yüklenme Animasyonu
- **Yeni Ortak Bileşen (`KitsugiTranslateIconButton`):** Galeri butonu (`KitsugiGalleryIconButton`) ile birebir aynı animasyon dili uyarlandı: Çeviri devam ederken ikon nabız gibi yanıp söner (0.30 → 1.0 alpha, 700 ms) ve etrafında tema vurgu renginde dönen bir yüklenme halkası belirir; yükleme sırasında buton dokunulamaz. 28dp, 32dp ve 36dp butonlar için orantılı halka çapları tanımlandı.
- **Kök Sağlayıcı ile Durum Paylaşımı (`LocalKitsugiTranslationLoading`):** Medya, Karakter, Ekip ve Stüdyo detay sayfalarının kök sağlayıcısına `LocalKitsugiTranslationLoading` eklendi. Sayfa içerisindeki tüm çeviri butonları durumu otomatik tüketirken, alt sayfalar ve pencereler (sheet) kendi yerel durumunu geçirerek bağımsız kalır.
- **Akış Uyumlu ViewModel Durumu:** Detay ViewModel'lerine (`ApiResult`, `MediaEntry`, `Character`, `Staff`, `Studio`) `translationLoading: StateFlow<Boolean>` eklendi. Kısmi metin akışlarında halka erken durmaz; metin akarken döner ve çeviri bittiğinde kapanır. Nesil sayacı ile eski iptal edilen isteklerin yeni durumu etkilemesi engellendi.
- **Genişletilmiş Kapsam:** Açıklama kartı, stüdyo hakkında kartı, karakter ve ekip biyografi kartları, spoiler pencereleri, inceleme, konu ve aktivite pencereleri, tüm liste görünümleri, forum yorum kartları ve profil biyografilerindeki çeviri alanlarına entegre edildi.

## 🇬🇧 English (v2.4.268)

### 🔄 Loading Spinner & Pulse Animation for Translation Buttons
- **New Reusable Component (`KitsugiTranslateIconButton`):** Implemented identical animation styling to `KitsugiGalleryIconButton`: While translation is in progress, the icon pulses (0.30 → 1.0 alpha, 700 ms) accompanied by an accent-colored spinning progress ring, and clicks are disabled during translation. Tailored ring dimensions are provided for 28dp, 32dp, and 36dp button variants.
- **Centralized Composition Local (`LocalKitsugiTranslationLoading`):** Added `LocalKitsugiTranslationLoading` to root page providers (Media, Character, Staff, Studio). Descendant translation buttons consume the state automatically, while bottom sheets and dialogs pass their own isolated states to prevent cross-component indicator bleed.
- **Stream-Aware ViewModel State:** Added `translationLoading: StateFlow<Boolean>` to detail ViewModels (`ApiResult`, `MediaEntry`, `Character`, `Staff`, `Studio`). The spinner stays active during partial/streaming text delivery and only stops once the full translation has resolved. Generation counters prevent cancelled requests from clearing newer tasks.
- **Comprehensive Coverage:** Applied to media synopsis cards, studio about cards, character/staff biography cards, spoiler sheets, review/topic/activity detail dialogs and lists, forum comment cards, and profile biography translate buttons.

---

## 🇹🇷 Türkçe (v2.4.267)

### 🛡️ RenderThread (libhwui.so) SIGSEGV ve Bellek İyileştirmeleri
- **GPU RenderNode Yarış Önlemi:** `AsyncImage` üzerinde `onImageAspectResolved` ve `onLoadingFailed` durum güncellemeleri, çizim evresinde RenderNode yıkılmasını/yeniden oluşmasını önlemek amacıyla `postAfterFrame` ile bir sonraki ana iş parçacığı karesine ertelendi.
- **Sorunlu OEM Cihaz Koruması:** Xiaomi/Redmi/POCO (MIUI/HyperOS), Realme, OPPO/OnePlus (ColorOS), Vivo/iQOO (OriginOS/FunTouch), Meizu, Honor, Infinix ve düşük bellekli (<256MB) cihazlarda `libhwui.so` çökmesini önlemek için GPU RenderEffect bulanıklığı otomatik olarak devre dışı bırakılıp optimize edilmiş yazılımsal bitmap stack-blur'a geçirildi. Güvenli azami bulanıklık yarıçapı 12dp olarak sınırlandırıldı.
- **Büyük Görsel Bellek Optimizasyonu:** `BlurTransformation` yüksek çözünürlüklü posterlerde 2×/4×/6× otomatik ölçek küçültme (scale-down) ve ara bitmap temizliği (recycle) ile bellek yükünü ~%80 oranında düşürdü.
- **Donanım Çizici Güvenli Tahliyesi:** Activity sonlanırken (ekran döndürme, dil değişimi veya arka plana atılma anında) bekleyen GPU çizim komutlarının katmanları yıkmadan önce tamamlanmasını sağlayan `safeFreeHardwareRenderer` reflektif koruması devreye alındı.

### 🌟 MyAnimeList Profil Genişletmesi & Oturum İyileştirmeleri
- **Zenginleştirilmiş Profil Ekranı:** MAL kullanıcı profili; Hakkında sekmesi ve harici bağlantılar, etkinlik ve geçmiş akışı, anime/manga ayrıntılı istatistikleri ve durum dağılımı, puan dağılımı, kulüpler, incelemeler, öneriler ve sayfalama destekli arkadaş listesi ile donatıldı.
- **Bildirimler İçin Ayarlar Yönlendirmesi:** Bildirim ekranında MAL web oturumu eksik veya uyuşmadığında doğrudan Ayarlar → MyAnimeList bölümüne yönlendirme eklendi; eski ayrı giriş penceresi kaldırıldı.
- **Yeniden Bağlan / Oturumu Tamamla Eylemi:** MAL ayarlarına, bağlı hesabı koparmadan yalnızca web oturum çerezlerini yenileyip yetkilendirmeyi tazeleyen bağımsız "Yeniden Bağlan" seçeneği eklendi.

## 🇬🇧 English (v2.4.267)

### 🛡️ RenderThread (libhwui.so) SIGSEGV & Memory Optimizations
- **GPU RenderNode Race Prevention:** Deferred `AsyncImage` aspect and failure callbacks using `postAfterFrame`, preventing state mutations during the draw phase from triggering RenderNode tear-down races in `libhwui.so`.
- **Targeted OEM ROM Workaround:** Automatically disables GPU RenderEffect blur and falls back to optimized bitmap stack-blur on known problematic Android OEM builds (MIUI/HyperOS, ColorOS, OriginOS, RealmeUI, etc.) and low-RAM devices (<256MB heap), clamping max safe blur radius to 12dp.
- **Bitmap Blur Allocation Reduction:** Added automatic 2×/4×/6× scale-down and intermediate bitmap recycling to `BlurTransformation`, reducing GC pressure by ~80% on high-res artworks.
- **HardwareRenderer Drain on Teardown:** Added reflective `safeFreeHardwareRenderer` inside `onActivityDestroyed` to drain pending GPU fences before layers are destroyed during rotations, theme changes, or activity exits.

### 🌟 MyAnimeList Profile Expansion & Unified Session Enhancements
- **Enriched Profile Interface:** MAL user profiles now feature external links, activities and history, full anime/manga status and score distributions, clubs, reviews, recommendations, and paginated friend lists.
- **Seamless Notification Reconnect Flow:** Notification views now route directly to Settings → MyAnimeList when the session is missing or mismatched, removing legacy dialog prompts.
- **Session Refresh Action:** Added a dedicated "Complete / Reconnect MAL Session" action in settings to refresh website cookies without unlinking the OAuth account.

---

## 🇹🇷 Türkçe (v2.4.266)

### 🌐 Tüm Hesap Girişi Ekranlarında Her Zaman Görünür 'Tarayıcıda Aç' Butonu
- **OAuth WebView Ekranı (AniList, MAL, Simkl):** "Tarayıcıda Aç" butonu daha önce yalnızca hata oluştuğunda çıkıyordu. Artık taze ekranda da her zaman "Kapat" butonuyla yan yana eşit genişlikte yer alıyor. Butona basıldığında yetki URL'si taze PKCE anahtar çiftiyle yeniden üretilir, varsayılan harici tarayıcı açılır ve dialog kapanır. Tarayıcıda onay verildikten sonra `malapp://auth` deep link şeması sayesinde otomatik olarak uygulamaya dönülüp token takası tamamlanır.
- **TV / QR Giriş Yolu (KitsugiWebViewDialog):** `KitsugiWebViewDialog` bileşenine `onOpenInBrowser` desteği eklendi; TV/QR dialogunda da harici tarayıcı butonu aktif hale getirildi.
- **Kitsu Giriş Ekranı:** API ile kullanıcı adı/şifre alanının altına hesap doğrulama veya şifre sıfırlama işlemleri için `kitsu.app/login` adresini açan "Tarayıcıda Aç" butonu ve bilgilendirme metni eklendi.

## 🇬🇧 English (v2.4.266)

### 🌐 Always-Visible 'Open in Browser' Button on All Account Login Screens
- **OAuth WebView Dialog (AniList, MAL, Simkl):** The "Open in Browser" button is now permanently visible alongside the dismiss button instead of only showing up on network errors. Tapping it re-generates the authorization URL with a fresh PKCE key pair, opens the default external browser, and closes the dialog. Returning via the `malapp://auth` deep link scheme automatically completes token exchange.
- **TV / QR Login Flow:** Added optional `onOpenInBrowser` support to `KitsugiWebViewDialog`, enabling the external browser button in TV/QR authorization dialogs.
- **Kitsu Dialog:** Added an "Open in Browser" button directing to `kitsu.app/login` beneath credentials fields for password resets and verification.

---

## 🇹🇷 Türkçe (v2.4.265)

### 🖼️ Ana Sayfa Vitrini: Dikey Modda Sabit Bant Yüksekliği & Kırpılmayan Poster
- **Sabit Bant Yüksekliği:** Dikey modda vitrin yüksekliği artık gösterilen görselin oranına göre zıplayıp kutuyu uzatıp kısaltmaz; cihazın dikey bant yüksekliğine sabitlendi.
- **Kırpılmayan Poster & Bulanık Dolgu:** Dikey modda poster gösterildiğinde kutuya sığması için kırpılıp zoomlanması engellendi. Tıpkı yatay modda olduğu gibi arkada yumuşak atmosferik bulanık dolgu, ön planda ise kırpılmamış tam boy poster ortalanarak gösterilir.
- **Yatay Mod Uyumu:** Yatay moddaki mevcut ekran-duyarlı dinamik sunum ve manga öncelikleri aynen korundu.

## 🇬🇧 English (v2.4.265)

### 🖼️ Home Hero Showcase: Fixed Portrait Band Height & Uncropped Dual-Layer Poster
- **Stable Container Height:** In portrait mode, the showcase height is now locked to the standard portrait band height rather than recalculating per slide aspect ratio, preventing jumping heights.
- **Uncropped Poster with Atmospheric Fill:** In portrait mode, vertical posters are no longer aggressively cropped or zoomed to fill the frame. They now display using a dual-layer presentation (blurred background fill with centered, uncropped artwork), matching the clean landscape behavior.
- **Landscape Consistency:** Responsive band calculations and manga cover handling in landscape mode remain fully preserved.

---

## 🇹🇷 Türkçe (v2.4.264)

### 🔔 MyAnimeList Bildirimleri: Gerçek Medya Adı, Doğru Detay ve Sonsuz Kaydırma
- **Detay Sayfası Artık Doğru Medyayla Açılıyor:** Bildirim kartının başlığı MAL'ın kategori adıydı (`categoryName` = "New Related Anime") ve tıklamada bu metin detay sayfasına başlık olarak veriliyordu; `ApiResultDetailPage` çağıranın başlığını API başlığına tercih ettiği için ekranda kategori adı kalıyordu. Yönlendirme artık medyanın gerçek adı (`mediaTitle`) ve MAL kimliği ile yapılıyor. Kimlik yoksa ad üzerinden `searchMALOnly` ile çözülüp açılıyor.
- **Daha Okunur Kartlar:** Medya bildirimlerinde başlık artık medyanın adı yapıldı; MAL kategori adı ve format türü ikinci satıra alındı (`Umayuru: Full Gate!` / `New Related Anime • ONA`).
- **Tür Karışması Düzeltildi:** Novel, Light Novel, Manhwa, Manhua, One-shot, Doujin kayıtları artık manga evreninde doğru sınıflandırılıyor; manga kimliğinin anime aramasına gidip yanlış yapımı açması engellendi. Tür payload'da yoksa URL'deki `/manga/` yolundan tespit ediliyor.
- **Sonsuz Kaydırma & Geçmiş:** `historyItems` altındaki okunmuş/eski bildirimler `items` ile birleştirilip id ile tekilleştiriliyor. Liste sonuna geldikçe akışın sonraki sayfası deneniyor (`?page=N`); yeni kayıt gelmezse akış temiz bir şekilde sonlanıyor. MAL web akışı yerel arşive de kaydediliyor.
- **Hızlı Yükleme Bildirimi:** Arama sürerken kısa bir "Detay sayfası hazırlanıyor…" göstergesi eklendi.

## 🇬🇧 English (v2.4.264)

### 🔔 MyAnimeList Notifications: Accurate Detail Routing, Media Title & Infinite Scroll
- **Accurate Media Navigation:** Notification cards previously used MAL category titles (`categoryName` = "New Related Anime") as the detail title. Navigation now passes the true `mediaTitle` and MAL ID. When ID is missing, it dynamically resolves via `searchMALOnly`.
- **Readable Card Layout:** Card headers now display the real media title, moving category and format info to the secondary line (`Umayuru: Full Gate!` / `New Related Anime • ONA`).
- **Media Type Disambiguation:** Novel, Light Novel, Manhwa, Manhua, One-shot, and Doujin items are strictly routed to the manga catalog, preventing anime ID collisions. Fallback detection inspects `/manga/` in the URL.
- **Infinite Scrolling & Deduplication:** Merges and deduplicates read notifications from `historyItems` with live `items`. Supports pagination (`?page=N`) and persists the stream to local archive.
- **Loading Feedback:** Added a clean preparation indicator while resolving media details.

---

## 🇹🇷 Türkçe (v2.4.263)

### 🌐 1. Google Translate Otomatik Çeviri Hattı Baştan Aşağı Yenilendi
Detay sayfası açıklamalarında, karakter ve ekip biyografilerinde, stüdyo sayfalarında, spoiler bloklarında ve inceleme/tartışma/aktivite pencerelerinde çevirinin bazen çalışıp bazen sessizce durmasına yol açan tüm kök nedenler çözüldü:

- **4 Kademeli Yedekli Uç Nokta Zinciri:** Tek bir uç noktaya bağlı kalmak yerine 4 farklı Google Translate uç noktası (`client=gtx&dj=1`, `client=at&dj=1`, `client=dict-chrome-ex&dj=1`, `clients5.google.com/translate_a/t`) tanımlandı. HTTP 429/403 hız sınırında beklemeden anında sıradaki yedek uç noktaya geçiliyor.
- **İptal Edilebilir İstekler & Kilitlenme Koruması:** Bloklayıcı çağrılar yerine `suspendCancellableCoroutine` kullanıldı ve eşzamanlı istek semaforu 4 izne çıkarıldı. Sayfa değiştirildiğinde veya zaman aşımında devam eden istek anında iptal edilip kota serbest bırakılıyor.
- **Parça Bazlı (Chunk-Level) Room Önbelleği:** Uzun biyografi ve incelemelerde her parça çevrildiği anda kendi hash anahtarıyla veritabanına kaydediliyor; çeviri yarıda kesilse bile hazır kısımlar kaybolmuyor.
- **CJK (Çince / Japonca / Korece) Cümle Bölme:** Boşluksuz yazılan Çince/Japonca metinlerde noktalama işaretlerinin (`。！？；…`) cümle sınırı sayılması sağlandı; parça boyutu 1400 karaktere optimize edildi.
- **Bozulmayan Markdown & Spoiler Maskelemesi:** Eski yer tutuculardaki `START`/`END` gibi kelimelerin Türkçeye çevrilerek linkleri ve yapıyı bozması engellendi. Harf içermeyen sembolik yer tutuculara (`⟪#0⟫`, `⟪@0⟫`) geçildi; `img(...)` medya etiketleri ve `~!...!~` spoiler blokları tam korumaya alındı.
- **`isLikelyTurkish` Yanlış Pozitif Koruması:** Başında Türkçe künye ("Merkez: ...", "Ülke: ...") bulunan CJK veya Kiril açıklamaların yanlışlıkla "Türkçe" sanılıp çevrilmeden bırakılması önlendi.
- **Hedef Dil İyileştirmesi:** Sistem dili İngilizce olan cihazlarda hedef dilin hatalı çözümlenmesi düzeltildi.

### 🔔 2. MyAnimeList Bildirimleri: Gerçek Medya Adı, Doğru Detay ve Sonsuz Kaydırma
- **Detay Sayfası Artık Normal MAL Detayı:** Bildirim kartının başlığı MAL'ın kategori adıydı (`categoryName` = "New Related Anime") ve detay sayfasına kategori adı başlık olarak gidiyordu. Yönlendirme artık medyanın gerçek adı (`mediaTitle`) ve MAL kimliği ile yapılıyor; kimlik yoksa `searchMALOnly` ile çözülüp açılıyor.
- **Daha Okunur Kartlar:** Medya bildirimlerinde kart başlığı artık medyanın adı, MAL kategori adı ve türü ikinci satıra indirildi (`Umayuru: Full Gate!` / `New Related Anime • ONA`).
- **Tür Karışması Düzeltildi:** Novel, Light Novel, Manhwa, Manhua, One-shot kayıtları artık manga evreninde doğru sınıflandırılıyor; manga kimliğinin anime aramasına gidip yanlış yapımı açması engellendi.
- **Sonsuz Kaydırma & Geçmiş:** `historyItems` altındaki okunmuş bildirimler `items` ile birleştirilip id ile tekilleştiriliyor. Liste sonuna geldikçe sonraki sayfa deneniyor (`?page=N`); MAL web akışı yerel arşive de kaydediliyor.

### 🖼️ 3. Vitrin (Keşfet Hero Carousel) Otomatik Görsel Boyutlandırması
Detay sayfasındaki akıllı oran mantığı Keşfet ana sayfasındaki vitrine taşındı:
- **Dinamik Kutu:** Vitrin bandı artık sabit bir yükseklik değil, o an gösterilen görselin gerçek en-boy oranına kilitlenen esnek bir kutudur.
- **Dikey Poster:** Dikey poster gösterilirken uzun-dar kutu (ekranın %62'si tavan olacak şekilde) açılır.
- **Yatay Arka Plan:** 16:9 görsel gösterilirken geniş-kısa bant (ekranın %34'ü taban olacak şekilde) oturur; karakterlerin kafasının veya görselin kırpılması önlendi.
- **Yatay Mod & Manga Uyumu:** Manga ve manhwa yapımlarında dikey kapak önceliği korunur; yatay modda arka plan bulunamadığında poster merkezlenmiş yumuşak dolguyla taşmadan oturur.

### 👤 4. MyAnimeList Tek Oturum (Unified Auth) ve Gelişmiş Profil Desteği
- **Tek Giriş Sistemi (`MalUnifiedAuthManager`):** OAuth sayfası uygulama içi WebView ile açılarak tek bir girişle hem Bearer API jetonu hem de web scraping oturum çerezleri (`MALHLOGSESSID`) aynı anda elde edilir; kullanıcının iki kez giriş yapma zorunluluğu ortadan kalktı.
- **5 Yeni MAL Web Uç Noktası:**
  - `/profile/{username}/reviews` — Sayfalanabilir kullanıcı incelemeleri
  - `/profile/{username}/recommendations` — Kullanıcının önerileri
  - `/profile/{username}/clubs` — Üye olunan kulüpler
  - `/profile/{username}/statistics/anime` — Puan, format, tür, stüdyo ve seslendirmen dağılımları
  - `/profile/{username}/statistics/manga` — Manga puan ve format istatistikleri
- **Profil Banner & Dağılım Grafikleri:** MAL profil sayfasına banner desteği, puan grafikleri ve seslendirme/stüdyo sıralama state'leri eklendi.

### 🎭 5. Karakter, Ekip, Stüdyo ve TMDB Veri Entegrasyonu
- **Gereksiz Shikimori Çeviri Fırtınasının Önlenmesi:** Jikan/MAL zaten biyografi döndürdüğünde arka planda atılan onlarca Rusça isim/biyografi çevirisi engellendi. Kiril isimler 12'lik gruplar halinde tek istekte toplu çevriliyor.
- **TMDB İngilizce Biyografi Yedeği:** TMDB oyuncu ve ekip üyelerinde Türkçe biyografi bulunmadığında doğrudan `en-US` yedeğine düşülüyor.
- **Stüdyo Meta ve Açıklama Zenginleştirmesi:** AniList ve Shikimori'de boş gelen stüdyo açıklamaları, logoları ve kuruluş tarihleri Jikan/MAL üzerinden otomatik tamamlanıp çeviriye veriliyor.
- **Eksik Kaynak Tamamlama:** AniList karakter/ekip biyografileri boşsa Jikan üzerinden tamamlanıyor; `KitsugiDetailClient.fetchSynopsis` içine TMDB dalı eklendi.

### ⚡ 6. Anında Çeviri & Canlı Ayar Tepkisi
- **Galeri Beklemesini Kaldırma:** Karakter, ekip ve medya detay sayfalarında çeviri artık 5 farklı kaynaktan görsel toplayan galeri işlemlerini beklemeden sayfa açılır açılmaz anında paralel başlatılıyor.
- **Sayfa İçi Ayar Değişimi:** Detay ekranındayken otomatik çeviri açılıp kapatıldığında veya hedef dil değiştirildiğinde sayfa anında yenilenmeden güncelleniyor.
- **Pencerelerde Otomatik Çeviri:** Spoiler sayfası (`InteractiveSpoilerSheet`), inceleme, konu ve aktivite bottom sheet'leri (ve TV sürümleri) otomatik çeviri sekmesiyle açılıyor ve parçalı akışı (`onPartial`) canlı yansıtıyor. Arama, Hero önizleme ve kütüphane diyaloglarına da çeviri desteği getirildi.

### 🧩 7. Eklenti Yönetimi ve Arayüz Düzeltmeleri
- **Cloudstream Eklenti Başlık Düzeni:** Uzun başlıklı eklentilerde sağdaki butonların başlığı sıkıştırıp harf harf dikey yazdırması sorunu `FlowRow` ve tam genişlikli başlık düzeniyle giderildi.
- **Eklenti Kaldırma:** Kaldırılan eklentilerin dosya sistemindeki kalıntılarının temizlenmesi sağlandı.

## 🇬🇧 English (v2.4.263)

### 🌐 1. Complete Overhaul of Google Translate Engine
- **4-Tier Fallback Endpoints:** 4 Google Translate endpoints (`gtx`, `at`, `dict-chrome-ex`, `clients5`) fail over instantly upon 429/403.
- **Cancellable Requests:** Non-blocking `suspendCancellableCoroutine` with 4-permit semaphore frees concurrency quotas immediately on cancellation.
- **Chunk-Level Room Persistence:** Partially translated paragraphs are preserved in Room cache across interruptions.
- **CJK Sentence Splitting:** Proper CJK punctuation splitting at 1400-char chunks.
- **Symbolic Markdown & Spoiler Masking:** Pure symbolic tokens prevent corrupted links, image tags, and broken `~!...!~` spoilers.
- **`isLikelyTurkish` Accuracy & Language Resolution:** Eliminated false positives on prefixed CJK/Cyrillic texts.

### 🔔 2. MyAnimeList Notifications: Real Title, Detail Routing & Infinite Scroll
- **Accurate Detail Navigation:** Navigates using true `mediaTitle` and MAL id; resolves via `searchMALOnly` if id is absent, eliminating "New Related Anime" category title collisions.
- **Readable Notification Cards:** Card headers prioritize the media name; category and release type reside on the second line.
- **Manga / Light Novel Universe Disambiguation:** Light novels, manhwa, manhua, and one-shots are correctly mapped to manga search queries instead of anime.
- **Infinite Scrolling & Read History:** Merges and deduplicates `historyItems` with live feed, supporting paginated queries (`?page=N`) and local archival.

### 🖼️ 3. Dynamic Hero Carousel Aspect-Ratio Scaling
- **Adaptive Box Height:** Hero carousel container dynamically locks to the resolved aspect ratio of the active page artwork instead of a fixed height.
- **Portrait & Landscape Balance:** Automatically extends up to 62% screen height for portrait posters and narrows to 34% screen height minimum for 16:9 backdrops.
- **Manga & Multi-Platform Fit:** Preserves portrait cover priority for manga/manhwa while maintaining centered contain presentation in landscape mode.

### 👤 4. MyAnimeList Unified Authentication & Expanded Profile Data
- **Single Sign-On (`MalUnifiedAuthManager`):** In-app WebView handles both OAuth token exchange and web session cookie extraction (`MALHLOGSESSID`) in a single login flow.
- **5 New Web Scraping Endpoints:** Integrated paginated user reviews, recommendations, clubs, and detailed anime/manga score/format/genre/studio/voice actor distribution statistics.
- **Banner & Breakdown State:** Added profile banner support and extended state modeling in `KitsugiProfileViewModel`.

### 🎭 5. Character, Staff, Studio & TMDB Enhancements
- Prevented redundant Shikimori translation requests when Jikan provides bios; batched Cyrillic translations.
- TMDB English biography fallback, AniList/Shikimori studio enrichment via Jikan/MAL, and missing AniList bio fallbacks.

### ⚡ 6. Instant Parallel Translation & Reactive Settings
- Immediate translation start decoupled from multi-source gallery aggregation.
- Live reactive updates on in-page settings toggle without reload.
- Full auto-translation and streaming support across spoiler, review, topic, activity sheets, and preview dialogs.

### 🧩 7. Extension Management & UI Layout Fixes
- Fixed extension header layout overflow using `FlowRow`.
- Guaranteed file deletion on extension uninstallation.

## 🇹🇷 Türkçe (v2.4.262)

### 🔔 1. MyAnimeList Bildirimleri Artık Gerçekten Geliyor (Kök Neden Düzeltmesi)
- **Belirti:** MAL hesabı bağlı olmasına rağmen Bildirimler > MyAnimeList sekmesi sürekli "MAL web oturumunu bağla" diyor ve gerçek bildirimler yerine 7 günlük yayın takvimi gösteriyordu.
- **Kök Neden:** Uygulama, MAL'ın hiç yazmadığı `is_signed_in=1` çerezini arıyordu. Bu yüzden WebView'de geçerli bir MAL oturumu dursa bile oturum "yok" sayılıyor ve bildirim isteği hiç atılmıyordu.
- **Düzeltme:** Gerçek MAL oturum çerezleri tanınıyor: `MALHLOGSESSID` ve `MALSESSIONID`. `is_logged_in` / `is_signed_in` bayrakları tek başına oturum sayılmıyor (çıkış kalıntıları eleniyor).
- **Kanıtlı Karar:** Oturum çerez adına bakılarak tahmin edilmiyor; MAL'a istek atılır ve giriş sayfasına düşülürse oturum istenir.

### 🔐 2. MyAnimeList: OAuth Hesabı ⇄ Web Oturumu Eşleşme Kontrolü
- **Sorun:** MAL bildirimleri iki ayrı kimlikle çalışır: bağlı OAuth hesabı (`users/@me`) ve WebView'deki web oturumu. WebView'de başka bir MAL hesabı açık kaldığında bildirim sayfası o yabancı hesabın bildirimlerini döndürüyordu — sessiz kimlik karışması.
- **Çözüm:** Her yüklemede iki kimlik karşılaştırılıyor: Web oturumunun sahibi bildirim HTML'indeki `header-profile` bağlantısından okunuyor; OAuth kullanıcı adı `MalImportManager.fetchUserName()` ile alınıyor.
- **Eşleşmeme Durumu:** Hesaplar eşleşmezse bildirimler gizleniyor, her iki hesabın adı gösteriliyor ve **"Oturumu temizle ve yeniden giriş yap"** butonu sunuluyor.
- **Güvenli Oturum Temizleme:** Yalnızca `myanimelist.net` çerezleri süresi geçmiş olarak silinir; OAuth jetonları ve diğer platformların oturumlarına dokunulmaz.

### ⚡ 3. Otomatik Oturum Penceresi ve Teşhis Ekranı
- MAL OAuth ile bağlı olduğu hâlde web oturumu gerçekten yoksa giriş penceresi **bir kez kendiliğinden** açılır. Giriş tamamlandığında pencere kapanır ve bildirimler otomatik yüklenir.
- MAL tanı satırı artık bulunan çerez adlarını (`MALHLOGSESSID`, vb.) ve kimlik özetini (`hesap: X` / `UYARI: OAuth=X ≠ web=Y`) raporlar; çerez değerleri asla gösterilmez.

### 🔍 4. Arama Ekranı: Dikey ve Yatay Uyumlu Shimmer İskeletleri
- **Duyarlı Yükleme İskeleti:** `KitsugiShimmerResponsiveMediaRow` bileşeni eklendi.
  - Dikey ekranda gerçek sonuç kartlarıyla birebir uyumlu `2:3` poster oranı.
  - Yatay ekranda solda poster ve sağda başlık/meta bilgi düzeni.
  - Android TV için dikey poster düzeni korundu.
- **Yatay Kaydırılabilir Raflar:** Sonuçlar gelmeden önce iskelet rafları `LazyRow` ile yatay kaydırılabiliyor.
- **Bağımsız Kaynak Geçişi:** Çoklu kaynak aramasında her kaynağın kendi sonucu geldikçe yalnızca o raf iskeletten gerçek kartlara geçiyor.

### 🧪 5. Birim Testleri
- `MalWebSessionStoreTest`: Gerçek çerez (`MALHLOGSESSID`), bayrak kalıntıları ve çerez adı ayrıştırma vakaları test edildi.
- `MalAccountMatchTest`: Tam eşleşme, eşleşmeme, büyük/küçük harf toleransı ve kimlik okunamama durumları test edildi.
- `MalNotificationsClientTest`: Oturum sahibinin başlık bağlantısından okunması ve bildirim payload profillerinin dışlanması doğrulandı.

## 🇬🇧 English (v2.4.262)

### 🔔 1. MyAnimeList Notifications Actually Load Now (Root-Cause Fix)
- **Symptom:** With a connected MAL account, the Notifications > MyAnimeList tab kept asking to "Connect MAL web session" and showed the 7-day airing calendar instead of real notifications.
- **Root Cause:** The app looked for an `is_signed_in=1` cookie that MAL never sets. Even with a valid MAL session in the in-app WebView, the session was considered missing, so the notification request was never sent.
- **Fix:** Real MAL session cookies are recognized (`MALHLOGSESSID`, `MALSESSIONID`). The `is_logged_in` / `is_signed_in` flags never count on their own, and stale sign-out leftovers are ignored.
- **Evidence-Based Decision:** The session is verified directly via HTTP requests to MAL rather than guessing cookie names.

### 🔐 2. MyAnimeList: OAuth Account ⇄ Web Session Identity Matching
- **Problem:** MAL notifications rely on two separate identities: the linked OAuth account (`users/@me`) and the WebView web session. A different MAL account left logged in inside WebView resulted in displaying foreign notifications — silent identity collision.
- **Fix:** Both identities are validated on every load: Web session owner parsed from notification page HTML (`header-profile` link); linked OAuth username obtained raw via `MalImportManager.fetchUserName()` from `users/@me`.
- **Mismatch Protection:** Notifications are concealed on mismatch; both usernames are displayed along with a **"Clear session and sign in again"** action.
- **Targeted Session Reset:** Safely expires only `myanimelist.net` cookies without clearing OAuth credentials or other platform sessions.

### ⚡ 3. Automatic Session Prompt & Enhanced Diagnostics
- If MAL OAuth is connected but no website session exists, the sign-in dialog opens automatically once, flushing cookies upon login.
- MAL diagnostic row now reports cookie presence (`MALHLOGSESSID`, etc.) and identity status (`account: X` / `WARNING: OAuth=X ≠ web=Y`) without ever exposing cookie values.

### 🔍 4. Search Screen: Adaptive Portrait & Landscape Shimmer Skeletons
- **Responsive Loading Skeletons:** Introduced `KitsugiShimmerResponsiveMediaRow`.
  - Portrait orientation matches true `2:3` poster aspect ratio and metadata structure.
  - Landscape orientation matches true wide card layout (left poster, right metadata block).
  - Preserved vertical poster orientation for Android TV.
- **Scrollable Placeholders:** Loading shelves now utilize `LazyRow` for fluid horizontal scrolling.
- **Independent Shelf Transitions:** Each provider independently transitions from shimmer to resolved media cards as results stream in.

### 🧪 5. Unit Tests
- `MalWebSessionStoreTest`: Validates session cookie presence (`MALHLOGSESSID`), flag handling, and cookie name sanitization.
- `MalAccountMatchTest`: Verifies exact matching, mismatches, case tolerance, and unreadable identity fallbacks.
- `MalNotificationsClientTest`: Validates session owner extraction from headers while excluding nested notification sender profiles.

## 🇹🇷 Türkçe (v2.4.261)

### 🎌 1. Bangumi: Listeye Ekleme / Güncelleme Düzeltildi (HTTP 415 Çözümü)
- **Belirti:** Bangumi kaynaklı "Listeye Ekle", durum/puan güncellemesi ve bölüm ilerlemesi istekleri `HTTP 415 Unsupported Media Type` hatasıyla reddediliyordu.
- **Kök Neden:** Bangumi sunucusu `Content-Type` başlığını tam dize eşleşmesiyle kontrol eder (`application/json` veya `application/json; charset=UTF-8`). OkHttp `String.toRequestBody()` çağrısında küçük harfli `; charset=utf-8` parametresi ekliyor ve elle verilen başlığı eziyordu.
- **Düzeltme:** `BangumiApiClient.buildJsonRequestBody()` metodu ham UTF-8 bayt dizisi üzerinden tam `application/json` başlığıyla inşa edildi. Tüm koleksiyon, bölüm ve yazma sondası istekleri tek noktadan düzeltildi.
- **Tanı ve Test:** 415 hataları için özel tanı metinleri eklendi ve `BangumiApiClientWriteTest` birim testi ile doğrulandı.

### 🎭 2. Oyuncu ve Kişi Detayı: Galeri Yükleme Animasyonu & Vitrin Tıklaması
- **Galeri Butonu Yükleme Durumu:** Ek görseller yüklenirken galeri butonu artık gizlenmiyor; yüklenme sürecini gösteren dönen animasyon (`KitsugiGalleryIconButton`) yer alıyor.
- **Vitrin Görseli Tıklaması:** Vitrin fotoğrafına diğer görseller henüz yüklenirken de dokunulabiliyor. Galeri mevcut görselle hemen açılıyor ve arka planda yeni görseller geldikçe liste dinamik olarak güncelleniyor.
- **Duyarlı Kontroller:** Dikey ve yatay ekran modlarında galeri kontrolleri optimize edildi (`StaffGalleryItems`).

### 💬 3. Yorum ve İnceleme Profilleri: Platform Bazlı Yönlendirme & Sosyal Önbellek
- **Doğru Platform Yönlendirmesi:** Yorum ve inceleme kartlarındaki profil tıklamalarında kaynak (`source`) ayrımı yapıldı:
  - `mal` / `jikan` → Uygulama içi yerel MAL profili
  - `kitsu` → Uygulama içi yerel Kitsu profili
  - `simkl` → Uygulama içi yerel Simkl profili
  - `anilist` → Uygulama içi yerel AniList profili
  - `TMDB` / `Shikimori` / `Bangumi` → İlgili platformun web profili
- **PlatformUserProfileScreen:** Yeni kullanıcı profili görüntüleyici ile harici kullanıcıların istatistik, favori, galeri ve arkadaş verileri gösteriliyor.
- **Sosyal Akış Önbelleği:** `SocialFeedCache` ile incelemeler ve forum yorumları önbelleğe alınarak sayfalar arası geçişlerde ve geri dönüşlerde sayfa/scroll konumu korunuyor.

### 🛡️ 4. Profil Sayfası: Yükseklik Çökmesi Giderildi & Başlangıç Sekmesi İyileştirmesi
- **Sınırsız Yükseklik Hatası Çözümü:** `FavoritesExpandedBottomSheet` içindeki lazy grid için sınırlandırılmış yükseklik (`height(favGridHeight)`) tanımlanarak Jetpack Compose sınırsız yükseklik çökmesi önlendi.
- **Null Güvenliği:** Profil ve karakter detay sayfalarındaki potansiyel null işaretçi (`!!`) force-unwrap kullanımları güvenli çağrılara dönüştürüldü.
- **Başlangıç Sekmesi Yarış Durumu:** DataStore'dan son kullanılan sekme okunurken yaşanan yarış durumu düzeltildi; uygulama açılışında son kullanılan sekme tutarlı şekilde geri yükleniyor.

### 🖼️ 5. Detay Vitrini: Dinamik Poster / Backdrop Seçimi
- `HeroArtworkPreference` (POSTER / BACKDROP) ve `resolveHeroArtworkPreference(screenWidthDp, screenHeightDp)` mimarisi eklendi.
- Yatay ekranlar ve dar/uzun telefonlar için (oran ≤ 0.55) tam dikey poster önceliği; tablet ve geniş ekranlar için yatay arka plan optimizasyonu getirildi.

### 🔔 6. MyAnimeList: Web Oturumu Destekli Yerel Bildirimler
- WebView çerez deposu üzerinden MAL bildirimlerini çekebilen `MalNotificationsClient` ve `MalWebSessionStore` altyapısı entegre edildi.
- MAL bildirimleri uygulama içindeki yerel bildirim listesinde görüntülenip okunma durumları güncellenebiliyor.

## 🇬🇧 English (v2.4.261)

### 🎌 1. Bangumi: Add to List / Updates Fixed (HTTP 415 Solution)
- **Symptom:** Bangumi "Add to list", status/score updates, and episode progress writes failed with `HTTP 415 Unsupported Media Type — request with body must set "content-type" header to "application/json"`.
- **Root Cause:** Bangumi's server checks `Content-Type` using exact string equality (`application/json` or `application/json; charset=UTF-8`). OkHttp's `String.toRequestBody()` injected lowercase `; charset=utf-8` and overrode explicit headers.
- **Fix:** `BangumiApiClient.buildJsonRequestBody()` now builds requests from raw UTF-8 bytes with exact `application/json` content type. All collection, episode, and probe endpoints are fixed.
- **Diagnostics & Testing:** Added distinct 415 diagnostic messages and verified with `BangumiApiClientWriteTest`.

### 🎭 2. Voice Actor & Staff Detail: Gallery Loading Spinner & Showcase Tap
- **Loading Spinner:** Extra artwork loading no longer hides the gallery button; an active loading indicator (`KitsugiGalleryIconButton`) is displayed.
- **Showcase Image Interaction:** Showcase hero image is clickable even while extra images are loading. The gallery opens instantly with the current photo and appends new images dynamically as they arrive (`StaffGalleryItems`).
- **Responsive Controls:** Updated gallery controls across both portrait and landscape screen modes.

### 💬 3. Comment & Review Profiles: Platform-Aware Routing & Social Cache
- **Accurate Routing:** Profile clicks on comments and reviews now respect the entry's `source`:
  - `mal` / `jikan` → In-app native MAL profile
  - `kitsu` → In-app native Kitsu profile
  - `simkl` → In-app native Simkl profile
  - `anilist` → In-app native AniList profile
  - `TMDB` / `Shikimori` / `Bangumi` → Platform web profile via browser
- **PlatformUserProfileScreen:** View external user statistics, favorites, galleries, and friends natively.
- **SocialFeedCache:** Caches reviews and forum topics so sheet state and scroll positions are retained when returning from user profiles.

### 🛡️ 4. Profile Screen: Unbounded Height Crash Fix & Initial Tab Restoration
- **Height Constraint Fix:** Wrapped `FavoritesExpandedBottomSheet` lazy grid with explicit bounded height (`height(favGridHeight)`), preventing unbounded height Compose runtime crashes.
- **Null Safety:** Cleaned up unsafe force unwraps (`!!`) across profile and character sheets to safe calls.
- **Startup Race Condition:** Fixed race condition where initial default DataStore emissions overwrote `lastUsedTab` before stored settings finished loading.

### 🖼️ 5. Detail Hero: Dynamic Poster vs. Backdrop Preference
- Introduced `HeroArtworkPreference` (POSTER / BACKDROP) and `resolveHeroArtworkPreference(screenWidthDp, screenHeightDp)`.
- Automatically selects vertical poster for narrow portrait devices (ratio ≤ 0.55) and landscape orientations, reserving backdrops for tablets and wide screens.

### 🔔 6. MyAnimeList: Web Session Native Notifications
- Integrated `MalNotificationsClient` and `MalWebSessionStore` using in-app WebView session cookies.
- Fetch and display MAL notifications natively in-app with read status updates.

## 🇹🇷 Türkçe (v2.4.260)

### 🧭 1. Ortak Üst Sekme Bandı (Görünüm & Tercihler Mimarisi)
- "Ayarlar > Görünüm & Tercihler" bandı ortak bileşene taşındı: `KitsugiSettingsSegmentedTabs`.
- **Oynatıcı Ayarları:** Ana menü listesi yerine 8 kaydırmalı sekme (`HorizontalPager`).
- **İndirmeler:** Videolar / Altyazılar / Resimler için segmentli ortak bant.
- **Platform Hata Kodları:** 11 sekme + platform logoları; her kategori yalnızca kendi kodlarını filtreler.
- **Hesap Bağlantıları:** Platformlar ve Senkronizasyon sekmeleri.
- **Duyarlı & TV Uyumlu:** Taşmayan, otomatik ortalanan, kenar solmalı ve D-pad odak takipli bant yapısı.

### 🔢 2. İlerleme Sayacı: Büyütülmüş Dokunma Alanı
- Manga ve anime ilerleme sayacında doğrudan değer girebilmek için dokunma alanı büyütüldü (`48dp × 32dp`) ve tıklamayı yutan katman kaldırıldı.

### 🔲 3. Kart Çerçeveleri
- Keşfet "Yakında Yayında" kartlarına ve detaylı sezonluk medya kartlarına çerçeve eklendi.

### 👤 4. Profil ve Listem: Birleşik Bağlantısız Hesap Durumu
- `KitsugiAccountNotConnectedState` bileşeni ile Listem ve Profil durumları eşitlendi; süresi dolmuş Simkl oturumu uyarısı eklendi.

### 🔔 5. Bildirim Ayarları ve Hesap Kapıları
- Hesapsız kaynak bildirimleri kapalı ve devre dışı; genel Airing en az bir hesap gerektirir.

### 🎯 6. Vitrin Tıklamaları ve Sayfa Katmanı
- Ana sayfa vitrininde öndeki sayfa tıklamayı doğru alır; tek dokunuş detay sayfasına gider, çift dokunuş kopyalar.

### 💀 7. Detay Ekranları: İçerikle Birebir Eşleşen Shimmer İskeletleri
- Detay sayfalarındaki tüm yükleme durumları (ilişkiler, öneriler, karakterler, ekip, istatistikler, bölümler, galeri, puanlar, incelemeler, sosyal akış) içerikle eşleşen shimmer iskeletlerine dönüştürüldü; dikey/yatay/TV uyumlu.

---

## 🇬🇧 English (v2.4.260)

### 🧭 1. Unified Segmented Top Tab Bar
- Responsive segmented tab bar `KitsugiSettingsSegmentedTabs` used in Player Settings (8 swipeable tabs), Downloads, Platform Error Codes (11 tabs with logos), and Account Connections.

### 🔢 2. Progress Counter Direct Input
- Touch targets expanded to `48dp × 32dp`; passive click blocker removed.

### 🔲 3. Card Borders
- Added borders to Explore Airing Soon and detailed seasonal media cards.

### 👤 4. Unified Not-Connected Account States
- Shared `KitsugiAccountNotConnectedState` across My List and Profile for all providers.

### 🔔 5. Notification Gates
- Source notifications disabled without accounts; global airing requires at least one provider.

### 🎯 6. Home Hero Click Improvements
- Visually dominant carousel items capture clicks reliably (`HomeHeroGestureModifiers`).

### 💀 7. Detail Screens: Content-Matching Shimmer Skeletons
- Replaced generic spinners across detail tabs with content-matching shimmer skeletons; fully adaptive across portrait, landscape, and TV dialogs.

---

## 🇹🇷 Türkçe (v2.4.259)

### 🖼️ 1. Detay Vitrini: 220dp Sabit Boşluk Kaldırıldı
- Vitrinin altındaki sabit 220dp boş alan kaldırıldı; başlık, meta bilgileri ve rozetler doğrudan görselin alt bölümüne bindirildi (overlay).

### 🔍 2. Vitrin Görseli Tıklaması ve Galeri Eşleşmesi
- Vitrindeki görsele dokunulduğunda kaynak posteri yerine ekranda o anda gösterilen gerçek görsel (backdrop/poster) galeriye aktarılır.
- Galeri henüz yükleniyor olsa bile tıklanan görsel listenin başına eklenerek beklemeden açılır.
- TMDB, Kitsu, Bangumi, AniList, MAL ve Shikimori CDN çözünürlük ve boyut varyantları (`w500` vs `original`, `medium` vs `large`) aynı görsel olarak eşleştirilir.
- `DetailHeroGallerySelectionTest` ile 4 birim test eklendi.

---

## 🇬🇧 English (v2.4.259)

### 🖼️ 1. Detail Hero: 220dp Blank Gap Removed
- Removed the hardcoded 220dp blank spacing below the hero visual; titles, metadata, and badges are now cleanly overlaid onto the bottom of the visual.

### 🔍 2. Hero Visual Click & Gallery Synchronization
- Tapping the hero visual now opens the exact artwork rendered on screen in the gallery rather than the upstream source poster.
- Clicks open instantly even if the gallery is still loading.
- TMDB, Kitsu, Bangumi, AniList, MAL, and Shikimori CDN quality and size variants are canonicalized and accurately matched.
- 4 unit tests added in `DetailHeroGallerySelectionTest`.

---

## 🇹🇷 Türkçe (v2.4.258)

### 🐙 1. Bangumi: "Listeye hiçbir şey ekleyemiyorum" — Hata Artık Görünür ve Ölçülebilir
- **Gerçek Hata Metni:** `upsertCollection()` istisnayı yutmayı bıraktı. `BangumiSyncManager.describeWriteFailure()` ile HTTP kodu ve sunucunun ham hata mesajı kullanıcıya gösterilir.
- **401 Otomatik Kurtarma:** 401 yanıtında token bir kez otomatik yenilenir ve istek tekrarlanır.
- **400 Önleyici Temizleme:** Etiketler ve notlar sunucu kurallarına göre filtrelenir; boş etiket dizisi (`tags: []`) sunucudaki mevcut etiketleri sileceği için `null` gönderilir.
- **Yan Etkisiz Yazma Testi:** Giriş diyaloğuna "Bağlantıyı ve yazma iznini test et" butonu eklendi (var olmayan yapıta boş PATCH testi).
- **Kişisel Erişim Anahtarı (PAT) Girişi:** Uygulama kaydı gerektirmeyen ve Bangumi sunucusundaki OAuth kapsam uyumsuzluğunu aşan PAT desteği eklendi.

---

## 🇬🇧 English (v2.4.258)

### 🐙 1. Bangumi: "Cannot add anything to list" — Failures Are Now Visible and Measurable
- **Real Error Text:** Exceptions are no longer swallowed; actual HTTP code and server error descriptions are displayed.
- **Self-Healing on 401:** Token is automatically refreshed once upon 401 error.
- **Pre-Flight Sanitizing:** Tags and comments are sanitized; empty tag lists are dispatched as `null` to avoid remote tag deletion.
- **Side-Effect-Free Write Test:** Added connection and write probe in login dialog.
- **Personal Access Token (PAT) Support:** Added direct PAT login bypassing OAuth server scope parsing issues.

---

## 🇹🇷 Türkçe (v2.4.257)

### 🌐 1. Güncellenmiş Domain Tablosu (57 → 88 Domain)
- `domain_fixes.json` tablosu eklenti deposundaki güncel tabloyla eşitlendi: 31 yeni domain eklendi, 7 domain güncellendi.
- **hdfilmcehennemi / hdfilmcehennemi2:** `.nl` yerine doğrudan `.biz` adreslerine güncellendi.
- **recTV:** URL sonundaki mükerrer `/api` eki temizlendi (`/api/api/...` hatası giderildi).

### 🛡️ 2. HDFilmCehennemi2 Zorla Yönlendirme Düzeltmesi
- **Kök Neden:** `KNOWN_BROKEN_DOMAINS` ve `SAFE_NAME_SUFFIXES` içindeki `"2"` eki yüzünden HDFilmCehennemi2 (`hdfilmcehennemi2.site`), ana HDFC sitesine zorla yönlendiriliyordu. Sıfır sonuç dönüyor ve 75 saniyelik zaman aşımına (`KTS-CSP-9104`) yol açıyordu.
- **Çözüm:** `hdfilmcehennemi2.site` ölü domainler listesinden çıkarıldı ve numaralı eklentilerin ana eklenti domainini miras alması engellendi.

### 🔄 3. Eklenti Bazlı "Yeniden Dene" Butonu
- Yayın arama sonuçları ekranında hata veren CloudStream eklentileri için kartın üzerine **"Yeniden Dene"** butonu eklendi.
- Tüm aramayı baştan başlatmadan, yalnızca hata veren eklenti sıfırlanarak yeniden çalıştırılabilir.

### 🧪 4. Testler
- `CsStreamRunnerLogicTest` birim testleri güncellendi (`testKnownBrokenDomains_DoesNotContain_HdFilmCehennemi2Site`, `testResolveBuiltinDomain_NumberedVariant_DoesNotInheritParentDomain`).

---

## 🇬🇧 English (v2.4.257)

### 🌐 1. Expanded Domain Mapping (57 → 88 Domains)
- Synchronized `domain_fixes.json` with the latest plugin repository mapping: 31 new domains added, 7 domains updated.
- **HDFilmCehennemi / HDFilmCehennemi2:** Updated from `.nl` to `.biz` matching active provider sources.
- **recTV:** Removed duplicate trailing `/api` path suffix that caused `/api/api/...` malformed URLs.

### 🛡️ 2. HDFilmCehennemi2 Forced Domain Redirect Fix
- Removed `hdfilmcehennemi2.site` from `KNOWN_BROKEN_DOMAINS` and prevented numbered variant plugins from inheriting parent domains, preventing timeout errors (`KTS-CSP-9104`).

### 🔄 3. Per-Plugin "Retry" Button
- Added a dedicated **"Retry"** button on error cards for failed CloudStream providers in the stream screen.

### 🧪 4. Tests
- Added unit tests in `CsStreamRunnerLogicTest` for domain resolution and numbered variant independence.

---

## 🇹🇷 Türkçe (v2.4.256)

### 🖼️ 1. Detay Vitrini: Yön ⇄ Görsel Otomatik Eşleşmesi ve TAM Kapsama (Kaynak & Listem Detay)
- **Yatay Mod → Dikey POSTER:** TMDB/Fanart kalite sırası → kaynak poster → dikey galeri (2:3 uzun-dar kutu). Dar sol panele yatay görsel sıkıştırma kaldırıldı.
- **Dikey Mod → Yatay ARKA PLAN:** TMDB/Fanart kalite sırası → kaynak arka plan → yatay galeri (16:9 geniş-kısa bant).
- **Otomatik Oran Kilitlenmesi:** Görsel yüklendiğinde `onImageAspectResolved` kutunun en-boy oranını görselin gerçek oranına kilitler.
- **TAM Kapsama (`ContentScale.Crop`):** Bulanık dolgu / boşluk katmanı kaldırıldı. Kutu görsele kilitlendiği için kırpma kaybı olmaksızın alanı tam kaplar.
- **Yedek Koruması:** Eşlerden biri yoksa diğeri güvenli oran sınırlarında ([0.5, 1.6] yatay, [1.0, 2.2] dikey) gösterilir.

---

## 🇬🇧 English (v2.4.256)

### 🖼️ 1. Detail Hero: Orientation ⇄ Artwork Auto-Pairing & Full Coverage (Source & Library Detail)
- **Landscape Mode → Vertical POSTER:** Quality ranked poster candidate in a 2:3 container fitting the tall/narrow left panel.
- **Portrait Mode → Horizontal BACKDROP:** Quality ranked backdrop candidate in a 16:9 cinematic band.
- **Aspect Ratio Locking:** Container locks to the image's exact dimensions via `onImageAspectResolved`.
- **FULL Coverage (`ContentScale.Crop`):** Blurred ambient margins removed; image fills container seamlessly with zero crop loss.
- **Fallback:** Missing pairs safely fall back within clamped bounds ([0.5, 1.6] landscape, [1.0, 2.2] portrait).

---

## 🇹🇷 Türkçe (v2.4.255)

### 🚀 1. Detay Sayfasından Eklenti Video Hattı (Süre, CF Sabrı ve Tip Eşleştirme)
- **15 sn Arama Kutusu ve CF Çözüm İptali:** CloudflareKiller meydan okumayı görünce 90–120 sn'lik WebView başlatıyordu; 15 sn'lik arama kutusu patlayınca çözüm iptal oluyor, çerez hiç kaydedilemiyor ve eklenti pat diye boş dönüyordu. Çerezi olmayan ve sağlıklı diye doğrulanmamış her host için 120 sn aşama ve 240 sn sağlayıcı sabır bütçesi tanındı (`needsCfPatience`). Sağlıklı siteler yine 1–3 sn'de döner, üst sınır beklenmez.
- **Detay İzle Hattında `searchWithCfRetry`:** Detay İzle hattı bot koruması yeniden denemesine bağlandı. İlk arama grubu boş dönüp CF kanıtı tespit edilirse gate açılıp sabırlı tek yeniden deneme yapılıyor.
- **Yanlış Tip Etiketinde Güçlü Eşleşmelerin Korunması:** Yerli eklentilerin dizi sonucunu `Movie` (veya filmi `TvSeries`) olarak etiketlemesi durumunda güçlü başlık benzerliğinde ($\ge 0.85$) aday artık elenmiyor (-0.20 ceza alır); hem film hem dizi varyantı varsa doğru tip kazanır, tek başına gelen mükemmel başlık korunur.

### 🧪 2. Testler
- `CsTitleMatcherTest`'e yalnız Movie etiketli dizi sonucunun korunduğunu doğrulayan birim testi eklendi (`misTaggedExactTitleIsKeptWhenProviderOnlyReturnsTheOtherType`).

---

## 🇬🇧 English (v2.4.255)

### 🚀 1. Detail Page Provider Video Pipeline (Timeout, CF Patience & Type Matcher)
- **15s Search Window Aborting Cloudflare Resolution:** CloudflareKiller launches a 90–120s background WebView when challenged. Uncached/challenged hosts now receive 120s stage patience and 240s overall provider timeout (`needsCfPatience`). Healthy sites still return in 1–3s without waiting.
- **`searchWithCfRetry` in Detail Pipeline:** Detail watch flow now triggers a patient CF retry when the initial batch returns empty with challenge evidence.
- **Preserving Mis-tagged Exact Matches:** Strong similarity ($\ge 0.85$) matches are retained with a slight penalty (-0.20), allowing valid content with incorrect metadata tags to resolve smoothly while prioritizing correctly-typed siblings if available.

### 🧪 2. Tests
- Added unit test in `CsTitleMatcherTest` verifying mis-tagged exact titles are retained (`misTaggedExactTitleIsKeptWhenProviderOnlyReturnsTheOtherType`).

---

## 🇹🇷 Türkçe (v2.4.254)

### 🔄 1. Eklenti Güncellemelerinin Görünmemesi Düzeltildi
- **Kök Neden (depo tarafı):** Uygulama yalnızca `Kitsugi-Plugins` deposunun `builds` dalını okur; bu dal 5 Ekim'den beri güncellenmiyordu (yayında `RoketDizi v15`, kaynakta `v19`). CI'daki yayın adımı yalnızca `main` dalında çalışıyordu ve prebuilt kopyalama/manifest üretimi erken `exit 0` ile atlanıyordu.
- **Kök Neden (uygulama tarafı):** Güncelleme kararı 6 ayrı yerde yalnızca `version > version` karşılaştırmasıyla veriliyordu; sürüm numarası artırılmadan yeniden derlenen eklentiler görünmüyordu. Ayrıca depoya ulaşılamadığında APK içindeki bayat liste sessizce "canlı" sanılıp "Tüm eklentiler güncel" deniyordu.
- **Çözüm:** `CsPluginUpdateChecker` + `CsInstalledFingerprint` ile sürüm **veya** dosya içerik parmak izi (SHA-256) farkı güncelleme sayılıyor. Manifest çekiminde raw → api.github.com → jsDelivr → Codeberg ayna zinciri (20 sn bütçe) kullanılıyor, yedek kaynaktan gelen veriler işaretleniyor ve "denetlenemedi" uyarısı gösteriliyor. Tireli depo adı (`Kitsugi-Plugins`) kontrolü düzeltildi (yedekler artık gerçekten deneniyor).
- **UI:** İçerik güncellemesinde rozet **"⟳ Yeniden derlendi (v15)"**, düğme **"⟳ Yeniden kur (v15)"** olarak gösterilir.
- **Testler:** `CsPluginUpdateCheckerTest`, `CloudstreamRepoMirrorTest`.

---

## 🇬🇧 English (v2.4.254)

### 🔄 1. Plugin Updates Not Appearing — Fixed
- **Root cause (repo side):** The app only reads the `builds` branch of `Kitsugi-Plugins`, which had not been updated since Oct 5 (published `RoketDizi v15` vs source `v19`): the CI publish step ran on `main` only, and prebuilt copying/manifest generation were skipped by an early `exit 0`.
- **Root cause (app side):** Update detection was copy-pasted `version > version` in six places, so rebuilds without a version bump were invisible; and when the repo was unreachable the bundled (frozen) plugin list was treated as live, falsely reporting "everything up to date".
- **Fix:** `CsPluginUpdateChecker` + `CsInstalledFingerprint` treat a VERSION or a CONTENT (SHA-256) mismatch as an update. Manifest fetching uses a robust mirror chain (raw → api.github.com → jsDelivr → Codeberg, 20 s budget); fallback data is flagged and the UI reports "could not check for updates" instead of "all up to date". The hyphenated repo name (`Kitsugi-Plugins`) check was fixed so the fallbacks actually run.
- **UI:** Content-only updates show "⟳ Rebuilt (v15)" / "⟳ Reinstall (v15)".
- **Tests:** `CsPluginUpdateCheckerTest`, `CloudstreamRepoMirrorTest`.

---

## 🇹🇷 Türkçe (v2.4.253)

### 🎬 1. Altyazı Artık Görüntü Modundan Bağımsız (Doldur / Sığdır / 16:9 / Yakınlaştır)
- **Kök Neden:** Media3, altyazı görünümünü (`exo_subtitles`) videoyu taşıyan `exo_content_frame` (AspectRatioFrameLayout) **içine** yerleştirir. Görüntü modu değiştiğinde bu çerçevenin en-boy oranı ve ölçüsü değişiyor; altyazı da onunla birlikte ölçeklenip kayıyor, geriliyor/kısalıyordu.
- **Çözüm:** `Media3PlayerEngine.detachSubtitleViewFromContentFrame` altyazı görünümünü içerik çerçevesinden çıkarıp PlayerView kök katmanına `MATCH_PARENT` olarak taşır; `SUBTITLE_BOTTOM_PADDING_FRACTION = 0.08` ile sabit alt konum uygulanır. Dikey kaydırma değeri altyazı alanının içine sabitlenir (ekran dışına itilemez).
- **Sonuç:** Video nasıl ölçeklenirse ölçeklensin altyazı **hep aynı yerde, aynı genişlikte**.

### 🔊 2. Ses Parçası Seçimi Gerçekten Uygulanıyor (Diziyou: 3 seçenek, hep aynı İngilizce ses)
- **mpv:** Seçim üç yolla uygulanır (`setPropertyInt` → `command set aid` → metinsel `aid`) ve `aid` geri okunarak **doğrulanır**; uygulanamazsa önceki parçaya dönülür ve ayrıntılı log bırakılır. Kullanıcının elle seçtiği parça (`userSelectedAudioId`) dil tercihi/`aid=auto` yolları tarafından artık **ezilmez**.
- **Media3:** Override uygulandıktan 450 ms sonra parçanın gerçekten seçili olup olmadığı kontrol edilir; değilse `disable → enable + override` zorlaması yapılır.
- **Görünürlük:** Cihazın çözemeyeceği parçalar listede `⚠️ (cihaz desteklemiyor)` olarak işaretlenir; seçim yine uygulanmazsa kullanıcıya "Ses parçası değişmedi…" bildirimi gösterilir.

### 📺 3. Sezon Çelişkisi Kapısı (Watch2Movies: içerik doğru, sezon/bölüm yanlış)
- Yüklenen sayfanın başlığı/URL'si **başka bir sezonu** bildiriyorsa doğrudan bölüm eşlemesi atlanır ve önce **doğru sezon sayfasına** inilir.
- URL kalıpları iki yönlü okunur: `/supernatural-2-sezon` (anahtar sözcükten önce) ve `/sezon-2/bolum-3` (sonra). `-2-sezon-3-bolum` adresi artık yanlışlıkla "sezon 3" sayılmaz.
- **Yanlış pozitif koruması:** Sayfanın bölüm listesi istenen sezonu içeriyorsa (çok sezonlu tek sayfa) çelişki sayılmaz. Gezinme başarısız olur ve bölüm listesi boşsa sayfa URL'si son çare olarak kullanılır — kaynak tamamen kaybolmaz.

### 🚫 4. Reklam / HLS Parçası Temizliği (Dizipal: KRALBET reklamı)
- Bahis/reklam markaları ve açık reklam kalıpları hem URL hem görünen ad üzerinden elenir (`kralbet.mp4` vakası).
- Eklenti **yalnızca reklam** döndürdüyse artık orijinal liste geri getirilmez: reklam **asla** oynatılabilir akış olarak sunulmaz, kullanıcı "kaynak yok" görür.
- Aynı hostta gerçek bir playlist (.m3u8/.mpd) varken HLS parçaları (`seg-1-v1-a1.ts`) ve URL tekrarları listeden çıkarılır.

### 🧪 5. Tanı Güvenilirliği: "Eklenti Bozuk" mu, "Uygulama Eksik" mi?
- **Host ABI denetimi:** `kotlinx.serialization.KSerializer`, `json.Json`, `SerialDescriptor`, `com.google.gson.Gson`, `ObjectMapper`, `OkHttpClient`, `Jsoup`, `MainAPI`, `NiceResponse` sınıfları çalışma zamanında yoklanır; eksikse rapora **“⚠️ Host ABI Eksik (UYGULAMA TARAFI)”** bölümü eklenir ve tanı ekranında kırmızı uyarı gösterilir.
- Bağlantı (linkage) hatalarının mesajına kanıt eklenir: `[EKSİK HOST ABI: kotlinx.serialization.KSerializer]` veya `metot imzası: (...)`.
- Rapor satırları kaynağı ayırır: `🧩 Host ABI eksik: … (UYGULAMA tarafı)` / `🧩 Runtime incompatible (eklenti tarafı)`. Böylece "onlarca eklenti patlak" görüntüsünün uygulama paketlemesinden mi kaynaklandığı **kanıtla** görülür.

---

## 🇬🇧 English (v2.4.253)

### 🎬 1. Subtitles Are Now Independent of the Video Resize Mode (Fill / Fit / 16:9 / Zoom)
- **Root cause:** Media3 places the subtitle view (`exo_subtitles`) *inside* `exo_content_frame` (AspectRatioFrameLayout). When the display mode changes, that frame is re-measured/re-scaled, dragging the subtitles along with it.
- **Fix:** `Media3PlayerEngine.detachSubtitleViewFromContentFrame` reparents the subtitle view to the PlayerView root as `MATCH_PARENT`; `SUBTITLE_BOTTOM_PADDING_FRACTION = 0.08` provides a stable bottom anchor, and the manual vertical offset is clamped inside the subtitle area.
- **Result:** The subtitle stays in the same place at the same width regardless of how the video is scaled.

### 🔊 2. Audio Track Selection Actually Applies (Diziyou: 3 options, always the same English audio)
- **mpv:** Selection is applied three ways (`setPropertyInt` → `command set aid` → string `aid`) and **verified** by reading `aid` back; on failure the previous track is restored and a detailed log is written. The user's manual pick (`userSelectedAudioId`) is no longer overwritten by language-preference/`aid=auto` paths.
- **Media3:** 450 ms after the override the engine verifies the track is really selected; if not, it forces `disable → enable + override`.
- **Visibility:** Tracks the device cannot decode are labelled `⚠️ (cihaz desteklemiyor)`; if a switch still fails, the user gets an explicit notification.

### 📺 3. Season Conflict Gate (Watch2Movies: right content, wrong season/episode)
- If the loaded page declares a **different season**, the direct episode match is skipped and the correct season page is visited first.
- URL patterns are read in both directions (`/supernatural-2-sezon` and `/sezon-2/bolum-3`); `-2-sezon-3-bolum` is no longer misread as "season 3".
- **False-positive guard:** if the page's episode list already contains the requested season, no conflict is reported. If navigation fails and the list is empty, the page URL is used as a last resort.

### 🚫 4. Ad / HLS Fragment Cleanup (Dizipal: KRALBET ad)
- Betting/ad brands and explicit ad patterns are filtered by URL *and* display name (`kralbet.mp4` case).
- If a plugin returns **only** ads, the original list is no longer restored: an ad is **never** offered as a playable stream.
- HLS fragments (`seg-1-v1-a1.ts`) are dropped when a real playlist exists on the same host; duplicate URLs are collapsed.

### 🧪 5. Diagnostics Reliability: "Broken Plugin" vs "Missing App ABI"
- **Host ABI probe:** `KSerializer`, `Json`, `SerialDescriptor`, `Gson`, `ObjectMapper`, `OkHttpClient`, `Jsoup`, `MainAPI`, `NiceResponse` are probed at runtime; if any is missing, the report gains an **“⚠️ Host ABI Missing (APP SIDE)”** section and the diagnostics screen shows a red warning.
- Linkage errors now carry evidence in their message: `[EKSİK HOST ABI: kotlinx.serialization.KSerializer]` or `metot imzası: (...)`.
- Report rows now attribute the failure: `🧩 Host ABI missing … (APP side)` vs `🧩 Runtime incompatible (plugin side)`.

---

## 🇹🇷 Türkçe (v2.4.252)

### 🖼️ 1. Detay Sayfası Vitrin Görseli — Tam Kalite + Sayfa Açılmadan Önce Hazır
- **RGB_565 decode kapatıldı (asıl kalite sebebi):** Uygulamanın genel Coil `ImageLoader`'ı bellek tasarrufu için `allowRgb565(true)` ile kuruludur. RGB_565 kanal başına 5/6/5 bit olduğu için gökyüzü, sakura, ışık geçişleri gibi **yumuşak gradyanlarda bantlaşma (banding)** ve renk kaybı oluşuyordu — anime key visual'ları neredeyse tamamen gradyan olduğu için hasar gözle görülür seviyedeydi. Yeni `HeroArtworkImagePolicy` vitrin görsellerini `allowRgb565(false)` (tam 8-bit renk) ve ekrandan türetilmiş **sınırlı** ölçüde (en fazla 2560×1440 px) decode eder. Liste/grid kartları eskisi gibi RGB_565 ile devam eder.
- **Görsel planı sayfa açılmadan ÖNCE çözülüyor:** `KitsugiDetailHero` görsel planını kendi içinde ağ çağrısıyla çözüyordu; sayfa bu çözüm bitmeden render ediliyordu → kullanıcı önce düşük çözünürlüklü kaynak görselini görüp sonra TMDB/Fanart görseliyle **değişim** yaşıyordu. `ApiResultDetailViewModel.resolveHeroArtwork()` artık planı `_detailLoading = false` olmadan önce çözüyor (5 sn zaman tavanı, iptal güvenli) ve en kaliteli adayı decode ederek önbelleğe koyuyor; `KitsugiDetailHero` bunu `preResolvedArtwork` olarak **ilk kareden** kullanıyor.
- **Yatay modda vitrin büyütüldü:** Sol panel dar olduğu için 16:9 bant küçük kalıyordu. Yatayda bant artık 250dp taban ve ekran yüksekliğinin %72'si tavan ile hesaplanıyor.
- **Tam görünürlük garantisi (dikey + yatay):** `heroArtworkFit(..., HERO_BANNER_FIT_TOLERANCE = 1.20)` → 16:9 görsel + 16:9 bant kayıpsız; dikey poster veya ultra geniş afiş banttan saparsa **KIRPILMIYOR**, "tam görünür + yumuşak dolgu" ile gösteriliyor.

### ✅ 2. Testler
- `HeroImageSelectionTest`'e 3 yeni test: hedef ölçü sınırları (4K decode'ı bellek taşmasını engeller), 16:9 bant oranı ve dikey posterin hiç kırpılmaması.

---

## 🇬🇧 English (v2.4.252)

### 🖼️ 1. Detail Page Hero Artwork — Full Quality + Resolved BEFORE the Page Renders
- **RGB_565 decode disabled (primary quality killer):** App-wide Coil `ImageLoader` uses `allowRgb565(true)` for memory savings. RGB_565 is 5/6/5 bits per channel without alpha, causing visible banding in smooth anime gradients. `HeroArtworkImagePolicy` now forces `allowRgb565(false)` (full 8-bit color) with bounded screen size (max 2560×1440 px) for the hero. Grid/list cards remain on RGB_565.
- **Hero artwork plan resolved BEFORE page renders:** Previously resolved in-composition via `produceState`, causing a jarring pop/swap from low-res source image to TMDB/Fanart artwork. `ApiResultDetailViewModel.resolveHeroArtwork()` now resolves and preloads the best artwork prior to `_detailLoading = false`.
- **Enlarged landscape hero:** Landscape mode now uses a 250dp floor and up to 72% of screen height ceiling. Non-standard aspect ratios use soft ambient fill rather than cropping.
- **Full-visibility guarantee:** 16:9 artwork fits cleanly with `COVER`, while vertical posters or ultra-wide art adapt cleanly with `CONTAIN_WITH_FILL`.

### ✅ 2. Tests
- 3 new unit tests in `HeroImageSelectionTest` covering target size bounds, 16:9 cinematic ratio calculation, and zero cropping on vertical posters.

---

## 🇹🇷 Türkçe (v2.4.251)

### 🇹🇷 1. TMDB Türkçe Başlık Varyantı (TR-Only Boşluğu Kapandı)
- **Kök Neden:** Site SADECE Türkçe başlık taşıyorsa (`Örümcek Adam Eve Dönüş Yok`), detay sayfasının EN/Romaji/Native başlıklarıyla eşleşme sağlanamıyor ve `EŞLEŞME YOK` ile sonuçlanıyordu.
- **Çözüm:** `runGetStreams`, çözümlenmiş TMDB kimliğiyle `tr-TR` başlığı çekip (`TmdbTitleLocalizer`) arama varyantlarına ve eşleştirici adaylarına ekler.
- **Önbellek & Performans:** 164 eklenti tek bir TMDB isteğini paylaşır; sonuçlar ve negatif yanıtlar oturum boyunca önbelleğe alınır. Hata/ağ kopması durumunda normal listeyle sessizce devam edilir.

### ⚡ 2. Paralel ID Çözümleme Fırtınasının Engellenmesi
- **Kök Neden:** Her eklenti için (164 paralel) ARM+TMDB ID çözümlemesi yapılıyordu — eklenti başına 8'e kadar ardışık HTTP isteği gidiyor, İzle'ye her basışta ~1000 istek oluşuyor ve 75 saniyelik sağlayıcı bütçesi arama başlamadan tükeniyordu.
- **Çözüm:** `StreamViewModel` üzerinden çözülen kimlikler tüm eklentilere doğrudan aktarılır (`preResolvedIds`). `KitsugiIdResolver.resolveIds` için sonuç önbelleği (`resolvedIdsCache`) eklendi. Ana akışta eklenti başına mükerrer ağ isteği sıfıra indirildi.

### 🎬 3. Anime Tip Filtresi Esnetildi
- Anime detay sayfasında dizi/film eklentileri *"Bu eklenti anime içeriklerini desteklemiyor"* denilerek eleniyordu; oysa yerli dizi siteleri dublajlı anime, film siteleri anime filmleri barındırabiliyor. Filtre gevşetildi; alakasız sonuçlar zaten `CsTitleMatcher` tarafından elenir.

### 🛡️ 4. Başlık Eşleştirici (CsTitleMatcher) & ProGuard Düzeltmesi
- **CsTitleMatcher 1.00 Reddi Giderildi:** Bilinen başlığa benzerliği $\ge 0.90$ olan adayların token filtresi tarafından elenmesi engellendi. Çok dilli veya Japonca ek içeren takma adların (ör. *SUPERNATURAL スーパーナチュラル*) net Latin eşleşmelerini (`Supernatural`) elemesi çözüldü.
- **ProGuard / Gson Keep Kuralı:** CS3 eklentilerinin (ör. Torrential) Gson sınıflarını doğrudan isimle araması nedeniyle R8'in yeniden adlandırmasından kaynaklanan `NoClassDefFoundError: Lcom/google/gson/Gson` hatası keep kurallarıyla giderildi.

### 🧪 5. Eklenti Tanı Koşucusu — Faz 2g (Gerçek Akış / Flow Testi)
- Tanı ekranına her eklentinin KENDİ döndürdüğü başlığı otomatik bulma ve sezon/bölüm eşleştirme hattından geçiren Faz 2g eklendi. Tanı tablosuna `Flow` sütunu eklendi (`🔁✅` / `🔁❌`).

---

## 🇬🇧 English (v2.4.251)

### 🇹🇷 1. TMDB Turkish Title Variant (Closing the TR-Only Gap)
- **Root Cause:** If a Turkish provider carries ONLY the local Turkish title (e.g. `Örümcek Adam Eve Dönüş Yok`), matching against English/Romaji/Native titles was failing (`NO MATCH`).
- **Fix:** `runGetStreams` retrieves the `tr-TR` title using the resolved TMDB ID via `TmdbTitleLocalizer` and injects it into query variants and matcher candidates.
- **Caching:** 164 plugins share a single TMDB request with session caching for both hits and misses, falling back safely without request storms.

### ⚡ 2. Eliminating the Parallel ID Resolution Storm
- **Root Cause:** 164 parallel plugins were each triggering separate ARM+TMDB ID lookups (up to 8 consecutive HTTP calls per plugin), wasting the 75-second timeout budget before searches began.
- **Fix:** Pre-resolved IDs (`preResolvedIds`) are now passed directly from `StreamViewModel`. Added `resolvedIdsCache` in `KitsugiIdResolver`. Duplicate per-plugin network requests dropped to zero.

### 🎬 3. Flexible Anime Type Filter
- Allowed Turkish TV series and movie providers to participate in anime searches, enabling access to Turkish dubbed anime and anime movies hosted on general streaming sites.

### 🛡️ 4. Title Matcher Precision & ProGuard Gson Keep
- **Exact Match Guard:** High-confidence candidate matches ($\ge 0.90$) are now protected from being discarded by multi-script alias noise (e.g. bilingual titles).
- **Gson ProGuard Rules:** Added `-keep` rules for `com.google.gson.**` to prevent `NoClassDefFoundError: Lcom/google/gson/Gson` in plugins like Torrential.

### 🧪 5. Plugin Diagnostics — Phase 2g Real Flow Testing
- Diagnostic runner now tests the complete real stream discovery pipeline using the plugin's own returned title, adding a `Flow` column (`🔁✅` / `🔁❌`) in the report and screen.

---

## 🇹🇷 Türkçe (v2.4.250)

### 🎬 1. Detay Sayfaları — Her İki Yönde de 16:9 Sinematik Bant (POSTER VİTRİN YOK)
- **Vitrin artık her zaman 16:9 sinematik banttır:** Detay sayfası dikey modda da, yatay modda da **aynı tasarımı** kullanır. Poster artık vitrin paneli olarak kullanılmaz.
- **Bant kırpılmaz:** En kaliteli **YATAY** görsel (TMDB / Fanart.tv / galeri arka planları) 16:9 bantta **kesilmeden** gösterilir. Görselin oranı banttan belirgin farklıysa (~%20'den fazla kırpma gerekiyorsa) kırpmak yerine "tam görünür + yumuşak dolgu" devreye girer — ör. çok geniş bir sinema afişi veya dikey bir poster bandın içinde tamamı görünür.
- **En kalitelisi otomatik seçilir:** Aday sırası kalite sıralıdır → çözümleyicinin TMDB/Fanart.tv sıralaması (4K > 2K > 1080p, yazısız, oylu) → kaynağın kendi arka planı → galeri yatay görselleri (çözünürlüğe göre).
- **Boyut uyumu:** Bant yüksekliği panel genişliğinden türetilir (`genişlik × 9/16`) ve ekran yüksekliğinin %62'sini geçmez → telefon, tablet, TV ve hem dikey hem yatay yönde aynı görünüm.
- **Başlık/meta bandın altında:** Logo/başlık, format-tür sayaç satırı, yayın bilgisi ve rozetler (kaynak, yıl, puan, favori, listede, +18) bandın altındaki bilgi alanına akar.
- **Kütüphane (MediaEntry) detay sayfası** da birebir aynı mantığa geçti: en kaliteli yatay galeri görseli bantta, yoksa poster bandın içinde kesilmeden.

### 🖼️ 2. Keşfet Vitrini (Hero) Yatay/Dikey Uyumu
- **Yatay modda bant büyütüldü:** Yatay telefon vitrini ekran yüksekliğinin ~%74'ü (tablet ~%62). 16:9 kapak görselleri artık ince bir şeride kırpılmıyor; görselin yaklaşık %86'sı görünüyor.
- **Dinamik kırpma hizalaması:** Görselin **gerçek** en-boy oranı okunup kırpma penceresi ona göre hesaplanıyor; görselin üstünden yalnızca ~%5 pay kesiliyor ve yatayda tam ortalanıyor → karakterlerin kafası/yüzü kesilmiyor, görsel fazla yukarı kaymıyor.
- **Karartma (scrim) azaltıldı:** Üst üste binen gradyanlar yumuşatıldı; görsel net ve canlı.
- **Manga yatayda tam ortada:** Dikey poster (manga) geniş yatay vitrinde sağa yaslanmıyor; ekranın **tam ortasında**, olabildiğince büyük ve kesilmeden gösteriliyor (arkasında yumuşak atmosferik dolgu).

### ✅ 3. Testler
- `HeroImageSelectionTest`: dinamik hizalama, oran/uyum (COVER ↔ CONTAIN_WITH_FILL) ve cihaz/yön bazlı bant yüksekliği testleri.

---

## 🇬🇧 English (v2.4.250)

### 🎬 1. Detail Pages — 16:9 Cinematic Band in BOTH Orientations (NO poster hero)
- **The hero is always a 16:9 cinematic band:** the detail page uses the **same layout** in portrait and landscape. Vertical posters are no longer used as the hero panel.
- **The band is never sliced:** the highest-quality **WIDE** artwork (TMDB / Fanart.tv / gallery backdrops) is shown **uncropped**. When the artwork ratio differs too much from the band (would require >~20% cropping) the app switches to "fully visible + soft fill" — e.g. an ultra-wide key visual or a vertical poster is shown complete inside the band.
- **Best quality auto-selected:** candidates are quality-ordered → resolver's TMDB/Fanart.tv ranking (4K > 2K > 1080p, textless, voted) → native backdrop → wide gallery images (by resolution).
- **Device fit:** band height derives from the panel width (`width × 9/16`) and never exceeds 62% of screen height → identical look on phones, tablets, TV, portrait and landscape.
- **Title/metadata below the band:** logo/title, format & count row, airing info and badges (source, year, score, favourite, in-list, +18) flow into the info area under the band.
- **Library (MediaEntry) detail page** follows the exact same rule: best wide gallery artwork in the band, otherwise the poster shown complete inside the band.

### 🖼️ 2. Explore Hero Landscape/Portrait Fit
- **Taller landscape band:** landscape phone hero now uses ~74% of the screen height (tablet ~62%); 16:9 key visuals are no longer cropped into a sliver — about 86% of the artwork is visible.
- **Dynamic crop alignment:** the artwork's **real** aspect ratio drives the crop window; only ~5% is trimmed from the top and the image is horizontally centred.
- **Lighter scrims:** overlapping gradients softened; the artwork is crisp and vivid.
- **Manga centred in landscape:** vertical posters are no longer pushed to the right — they sit **dead centre**, as large as possible and uncropped, over a soft ambient fill.

### ✅ 3. Tests
- `HeroImageSelectionTest`: dynamic alignment, fit (COVER ↔ CONTAIN_WITH_FILL) and device/orientation band-height rules.

---

## 🇹🇷 Türkçe (v2.4.249)

### 🖼️ 1. Simkl Görsel Logo Mantığı (Vitrin / Hero & Detay Ekranları)
- **Eksik Çapraz Kimlik (Cross-ID) Çözümlemesi:** `SimklApiClient` keşfet şeritlerindeki Simkl öğelerini `malId = simklId, tmdbId = null, realMalId = null` olarak ürettiğinde vitrin ve detay sayfalarında logoların bulunamaması giderildi. `KitsugiHeroSection.fetchHeroLogo`, `KitsugiHeroArtworkResolver.computeArtworkInternal`, `ApiResultDetailViewModel.fetchLogo` ve `MediaEntryDetailViewModel.fetchLogo` içine Simkl kimliğinden TMDB / MAL / AniList / Kitsu kimliklerini çözen `KitsugiSimklDetailClient.resolveSimklCrossIds` çağrısı entegre edildi (`tmdbId` → `realMalId` → `aniListId` → `kitsuId` zinciri).
- **Hero Artwork Kimliklerinin Vitrine Aktarımı:** `ExploreScreen.kt` (`displayHeroItems`), `HeroArtwork` üzerinden çözülen 16:9 `backdropUrl` ve `posterUrl` görsellerini öğeye kopyalarken `artwork.resolvedTmdbId` ve `artwork.resolvedMalId` alanlarını da vitrin öğesine aktarır. Ayrıca `KitsugiHeroSection` içindeki `logos` durumu `pagerKey` ile bağlandı ve boş logoların `enrichHeroBackdrops` tamamlandıktan sonra otomatik olarak yeniden denenmesi sağlandı.
- **Romaji Başlık & Çok Sezonlu Yıl Toleransı:** Simkl yalnızca Romaji başlık döndürdüğünde (ör. *Re:Zero kara Hajimeru Isekai Seikatsu*, 2026) TMDB kaydı İngilizce `name` + Japonca (CJK) `original_name` ve ilk sezon yılı (2016) taşıdığı için eşleşmenin reddedilmesi sorunu çözüldü. CJK orijinal başlıklı animasyon kayıtları için Romaji başlık uyumluluğu eklendi ve çok sezonlu TV animelerinde ilk sezon yılı ile devam sezonu yılı arasındaki fark için tolerans tanımlandı.
- **Detay Ekranları Çözümleme Zinciri:** Detay sayfası `fetchLogo` akışında Simkl çapraz kimlik çözümlemesi ve `explicitTmdb` eşleşmediğinde kaynak bazlı yedek logo arama zinciri (`TMDB` → `MAL` → `AniList` → `Kitsu`) aktif edildi.

### 📜 2. Kitsu Keşfet ("Tümünü Gör") Sayfalama ve Sonsuz Kaydırma Sorunu
- **Typesense 1-Tabanlı Sayfa Kayması:** Kitsu'nun sunucu arka ucu (`hummingbird-me/kitsu-server`), `filter[...]` parametresi içeren tüm `/anime` ve `/manga` sorgularını (`filter[status]=current`, `filter[status]=upcoming`, `filter[subtype]=movie`, `filter[season]=...`) `TypesenseSearchService` üzerinden çalıştırır. Sunucudaki `_page = (@_offset / @_limit).floor` hesabı `+ 1` eklemediği için 1-tabanlı Typesense'de `page[offset]=0` ve `page[offset]=20` aynı 1. sayfayı döndürüyordu ve liste 20. öğede kilitleniyordu. `KitsuExploreClient.kt` içine `offset > 0` için Typesense 1-tabanlı sayfa kaymasını telafi eden `adjustKitsuTypesenseQuery` (`offset + limit`) eklendi; artık 20. öğe sonrasında yeni sayfalar kesintisiz yükleniyor.
- **Trend Animeler Ucu:** Kitsu'nun `/trending/anime` ucunun sayfalama parametrelerini yok sayıp sabit 10 kayıt döndürmesi problemi çözüldü: ilk sayfada `/trending/anime` sonuçları popüler yayındaki animelerle (`airingAnime` / `topAnime`) 20 öğeye tamamlanır ve sonraki sayfalarda (`offset > 0`) kesintisiz devam eder.
- **KitsuApiHost ve Sonsuz Kaydırma Bekçisi:** Kanonik `https://kitsu.app/api/edge` adresi kalıcı olarak birincil tutuldu; `kitsu.io` yalnızca istek bazında yedek olarak bırakıldı. `FullScreenMediaGridPage.kt` ve `SearchScreen.kt` içinde filtre/sıralama değişimlerinde `autoLoadBudget` ile `lastAutoLoadVisibleCount` sıfırlaması ve `LaunchedEffect` tetikleyici anahtarları düzeltildi.

### 🧪 3. Birim Testleri
- `KitsuPaginationAndSimklLogoTest` ile Kitsu Typesense offset telafisi, kanonik sayfa anahtarları, `KitsuApiHost` önceliği ve Simkl vitrin logo politikası test edilip doğrulandı.

---

## 🇬🇧 English (v2.4.249)

### 🖼️ 1. Simkl Hero & Detail Visual Logos
- **Cross-ID Resolution:** When `SimklApiClient` outputs shelf items with `malId = simklId, tmdbId = null, realMalId = null`, logos were previously failing to resolve. Integrated `KitsugiSimklDetailClient.resolveSimklCrossIds` across `KitsugiHeroSection.fetchHeroLogo`, `KitsugiHeroArtworkResolver.computeArtworkInternal`, `ApiResultDetailViewModel.fetchLogo`, and `MediaEntryDetailViewModel.fetchLogo` (`tmdbId` → `realMalId` → `aniListId` → `kitsuId` chain).
- **Passing Artwork IDs to Hero:** `ExploreScreen.kt` (`displayHeroItems`) now copies `artwork.resolvedTmdbId` and `artwork.resolvedMalId` from `HeroArtwork` into hero items. Furthermore, `logos` state in `KitsugiHeroSection` is keyed to `pagerKey` and automatically retries blank logos once `enrichHeroBackdrops` completes.
- **Romaji Titles & Multi-Season TV Year Discrepancy:** When Simkl returns Romaji-only titles (e.g. *Re:Zero kara Hajimeru Isekai Seikatsu*, 2026), TMDB records carry English `name` + Japanese CJK `original_name` and the initial season release year (2016). Added CJK animation fallback in `TmdbArtworkIdentity.matches` and multi-season year tolerance in `KitsugiHeroArtworkResolver.kt` so sequels match TMDB entries reliably.
- **Detail Screen Logo Fallbacks:** Full fallback chain (`TMDB` → `MAL` → `AniList` → `Kitsu`) activated in `ApiResultDetailViewModel` and `MediaEntryDetailViewModel` when explicit TMDB entries are absent.

### 📜 2. Kitsu Explore "View All" Pagination & Infinite Scroll
- **Typesense 1-Based Page Offset Shift:** Kitsu's backend (`hummingbird-me/kitsu-server`) routes all `/anime` and `/manga` queries containing `filter[...]` to `TypesenseSearchService`. Due to missing `+ 1` in its offset-to-page calculation, `page[offset]=0` and `page[offset]=20` returned the exact same page 1, causing infinite scroll to stop at item 20. Added `adjustKitsuTypesenseQuery` (`offset + limit`) for `offset > 0` on filtered endpoints in `KitsuExploreClient.kt`, enabling seamless infinite scrolling past item 20.
- **Trending Anime Pagination:** Addressed Kitsu's `/trending/anime` endpoint ignoring pagination by backfilling page 1 with currently airing/popular anime to 20 items and cleanly paging subsequent offsets without interruption.
- **Canonical Host Priority & Scroll Budget Reset:** Preserved `https://kitsu.app/api/edge` as the permanent primary host. Fixed `autoLoadBudget` and `lastAutoLoadVisibleCount` resetting upon filter/sort modifications in `FullScreenMediaGridPage.kt` and `SearchScreen.kt`.

### 🧪 3. Unit Tests
- Added `KitsuPaginationAndSimklLogoTest` validating Typesense query adjustment, canonical query anchors, `KitsuApiHost` priority, and hero logo display policies.

---

## 🇹🇷 Türkçe (v2.4.248)

### 🖼️ 1. Simkl Vitrin (Hero) Logoları ve Kimlik Ayrıştırması
- **Kök neden:** Simkl liste/şerit kayıtları anime için yalnızca **romaji başlık** taşır (`Sousou no Frieren`, `Re:Zero kara Hajimeru Isekai Seikatsu`); TMDB kayıtları ise İngilizce/Japonca adlandırılır. `KitsugiEpisodeRatingsRepository.getLogoUrl`, `expectedTitles` ile TMDB kaydını başlık üzerinden doğruluyor ve iki türden (film + dizi) hiçbiri eşleşmezse logoyu vermiyordu. Romaji başlıklar TMDB başlıklarıyla eşleşmediği için Simkl öğeleri bu doğrulamaya takılıp vitrinde düz metin başlık gösteriyordu (logo ayarı açık olsa bile).
- **Düzeltme (vitrin):** `KitsugiHeroSection.fetchHeroLogo` Simkl dalında Simkl'ın kendi çözdüğü kimlikler (`ids.tmdb` / `ids.mal`) otorite kabul edilir ve başlık doğrulaması uygulanmaz — `KitsugiHeroArtworkResolver`'daki `canonicalSource == "tmdb"` kuralıyla aynı yaklaşım.
- **Çapraz kimlik yedeği:** Simkl liste yanıtı kimlik taşımıyorsa vitrin artık Simkl `extended=full` kaydından MAL/TMDB/AniList/Kitsu kimliklerini çözer (Bangumi `resolveCrossIds` deseninin Simkl karşılığı). Çözülen kaydın kendi başlık alanları öğe başlıklarıyla kesişmiyorsa logo istenmez (uydurma/yanlış kimlik koruması).
- **Kimlik okuma düzeltmesi (`SimklApiClient`):** Keşfet raflarını besleyen `getBestMedia` yanıtlarında Simkl kimliği `ids.simkl_id` veya `ids.simkl`, TMDB kimliği `ids.tmdb` veya `ids.tmdb_id`, MAL kimliği sayı veya metin (`"mal": "52991"`) olabilir. Ayrıştırma tek bir test edilebilir işleve taşındı (`parseBestMediaArray`) ve tüm varyantlar okunuyor.
- **Detay sayfaları:** Aynı yanlış negatif Simkl detay sayfası logo aramasını da engelliyordu; `ApiResultDetailViewModel` ve `MediaEntryDetailViewModel` Simkl dalı vitrindeki kuralla hizalandı. Diğer kaynakların (MAL/AniList/Kitsu/Bangumi/TMDB) davranışı değişmedi.
- **Testler:** `SimklExploreParsingTest` (6/6 geçti) — `simkl_id`/`simkl`, `tmdb`/`tmdb_id`, metin `mal`, CJK başlığın Latin alternatife çevrilmesi, anime film "Film" ipucu, poster/fanart CDN adresleri ve kimliksiz kayıtta geçici kimliğin MAL/TMDB sanılmaması.

### 🔍 2. Ayarlar Ekranı Canlı Arama ve Hızlı Gezinme (`SettingsSearch.kt`)
- **Akıllı Arama:** Ayarlar ana sayfasına gerçek zamanlı arama alanı eklendi (`SettingsSearchField`). Türkçe karakterler (`ı`/`i`, `ğ`/`g`, `ş`/`s`) ve aksanlar normalize edilir.
- **Derin Bağlantı:** Arama sonucu yalnızca ilgili ayar sayfasına değil, doğrudan hedef alt sekmeye (ör. *DoH / DNS*, *Manga Okuyucu*, *Altyazı Ayarları*) yönlendirir.
- **Mantıksal Kategoriler:** Ayarlar ekranı Hesap, Uygulama, Kaynaklar & Entegrasyonlar, Veri & Depolama ve Destek & Tanılama bölümlerine ayrıldı.

### 🎨 3. Ortak Sekme Kapsülleri (`KitsugiSettingsSegmentedTabs`) ve Alt Sayfa Uyumlaması
- Görünüm, Eklentiler, Entegrasyonlar ve Sistem sekmeleri modern, ikonlu ve vurgulu `KitsugiSettingsSegmentedTabs` bileşeninde birleştirildi.
- Eklenti sekme adları İngilizce ve Türkçe kaynaklardan alınacak şekilde yerelleştirildi.
- Alt sayfalarda sistem gezinme çubuğu (`navigationBarsPadding`) güvenli alanı eklendi; oynatıcı alt sayfalarında başlık seçili ekrana göre dinamikleşti.
- Depolama ekranındaki iç içe geçmiş kart yapısı sadeleştirildi; İndirilenler (Video, Görsel, Altyazı) yönetimi güvenle korundu.

### 🎬 4. Detay Sayfaları Sinematik Yüklenme Ekranı
- Medya, karakter, ekip/personel, stüdyo ve Android TV detay sayfalarında tam ekran sinematik yüklenme deneyimi yeniden bağlandı (`KitsugiCinematicLoadingScreen`).
- Başlık/ad, kapak görseli ve kaynak rozeti (`KitsugiSourceNamePill`) anında gösterilir. Medya logosu varsa getirilir.
- TMDB stüdyo/ağ varyantları (`tmdb_network`, `tmdb-network`, `tmdbnetwork`) TMDB logosu ve adıyla doğru eşleştirildi.
- Yatay kompakt ekran boyutlandırması ve GPU çökmesini önleyen katman sadeleştirmesi yapıldı.

### 🌄 5. Detay Vitrini Akıllı Görsel Seçimi ve Çözünürlük Sıralaması
- API detay sayfalarında üst görsel ekran boyutuna/yönüne göre seçilir (dikey telefonlarda poster, yatay telefon/tablet/TV'de arka plan, mangada poster).
- TMDB, Fanart.tv ve galeri adayları dikey/yatay yön havuzlarına ayrılıp çözünürlüğe göre sıralanır.
- Görsel yüklenemezse sıradaki adaya otomatik geçilir; geniş ekranda dikey görsel için kesmek yerine çift katmanlı contain kullanılır.

### 📋 6. Platform Hata Kodları Kataloğu (Ayarlar > Geliştirici & Tanılama)
- Ayarlar'daki "Platform Hata Kodları" ekranı (`PlatformErrorCatalogScreen`) artık AniList, MyAnimeList, Kitsu, Shikimori, Bangumi, TMDB, Simkl, Manga/CloudStream ve ortak (`KTS-COM`) hata kodlarının tamamını kapsıyor; filtre çipleri ve arama desteği sunuyor.

### 🌐 7. Anında Dil Değişimi ve Kitsugi Hesabı İyileştirmeleri
- `AndroidManifest.xml`'den `locale|layoutDirection` configChanges girdisi kaldırıldı; dil değiştiğinde Activity anında yeniden kurulur (API 33+ güvenlik tetikleyicili).
- Kitsugi hesabı ekranına 3 bağımsız aç/kapa anahtarı eklendi: "Şifreli hesap yedeği", "Arama geçmişi eşitleme", "Bildirim arşivi eşitleme".
- Kasa açılamazsa şifre bellekte tutulur, tek tıkla tekrar denenebilir.

---

## 🇬🇧 English (v2.4.248)

### 🖼️ 1. Simkl Hero Logos and Identity Parsing
- **Root cause:** Simkl list/shelf records carry **romaji-only** anime titles (`Sousou no Frieren`, `Re:Zero kara Hajimeru Isekai Seikatsu`) while TMDB records are named in English/Japanese. `KitsugiEpisodeRatingsRepository.getLogoUrl` verifies the TMDB record through `expectedTitles` and refuses to return a logo when neither the movie nor the TV record matches. Romaji titles never matched, so Simkl entries always fell back to plain text titles in the hero (even with the logo setting enabled).
- **Fix (hero):** In the Simkl branch of `KitsugiHeroSection.fetchHeroLogo`, identities resolved by Simkl itself (`ids.tmdb` / `ids.mal`) are treated as authoritative and skip the title verification — the same approach as the existing `canonicalSource == "tmdb"` rule in `KitsugiHeroArtworkResolver`.
- **Cross-ID fallback:** When a Simkl shelf record carries no IDs, the hero resolves MAL/TMDB/AniList/Kitsu identities from the Simkl `extended=full` record. A synthetic/wrong-ID guard rejects the record unless its own title fields intersect the item titles.
- **ID parsing fix (`SimklApiClient`):** In `getBestMedia` responses the Simkl ID may be `ids.simkl_id` or `ids.simkl`, the TMDB ID `ids.tmdb` or `ids.tmdb_id`, and the MAL ID a number or a string (`"mal": "52991"`). Parsing now lives in one testable function (`parseBestMediaArray`) that reads every variant.
- **Detail pages:** The same false negative also blocked the Simkl detail-page title logo; the Simkl branch of `ApiResultDetailViewModel` and `MediaEntryDetailViewModel` now follows the hero rule. Every other source (MAL/AniList/Kitsu/Bangumi/TMDB) keeps its current behaviour.
- **Tests:** `SimklExploreParsingTest` (6/6 passing) covers `simkl_id`/`simkl`, `tmdb`/`tmdb_id`, string `mal`, CJK-to-Latin title resolution, anime movie "Film" hint, Simkl CDN poster/fanart URLs, and placeholder ID safety.

### 🔍 2. Real-Time Settings Search and Deep-Linking (`SettingsSearch.kt`)
- **Smart Search:** Live search bar added to Settings (`SettingsSearchField`) with accent- and case-insensitive normalization across Turkish and English.
- **Deep Navigation:** Results navigate directly to target tabs or subpages (e.g. *DoH / DNS*, *Manga Reader*, *Subtitle Settings*).
- **Logical Organization:** Settings categorized cleanly into Account, App, Sources & Integrations, Data & Storage, and Support & Diagnostics.

### 🎨 3. Unified Segmented Tabs (`KitsugiSettingsSegmentedTabs`) & Subpage Polish
- Preferences, Addons, Integrations, and System tabs unified under the modern, icon-accented `KitsugiSettingsSegmentedTabs` component.
- Addon tab labels localized in English and Turkish.
- Subpages respect navigation bar insets (`navigationBarsPadding`), player subscreen headers dynamically update, and redundant embedded "OK" footers removed.
- Storage card hierarchy simplified while preserving Downloads management.

### 🎬 4. Detail Pages Cinematic Loading Screen
- Fullscreen cinematic loading experience restored across media, character, staff, studio, and Android TV detail screens (`KitsugiCinematicLoadingScreen`).
- Title/name, cover artwork, and provider badge (`KitsugiSourceNamePill`) appear immediately while detail queries load.
- TMDB network identifiers correctly map to TMDB logo and brand.
- Responsive scaling for compact landscape screens and duplicate blur layers removed to prevent GPU crashes.

### 🌄 5. Detail Hero Adaptive Visual Resolution
- Top visual on API detail pages selects between poster and backdrop based on screen size and orientation (portrait phone: poster; landscape phone/tablet/TV: backdrop; manga: poster).
- Candidates aggregated from TMDB, Fanart.tv, and gallery items, ranked strictly by resolution.
- Automatic fallback to next candidate if an image fails to load. Dual-layer contain prevents aggressive cropping of vertical artwork on wide screens.

### 📋 6. Complete Platform Error Codes Catalog
- The "Platform Error Codes" screen (`PlatformErrorCatalogScreen`) catalogs AniList, MyAnimeList, Kitsu, Shikimori, Bangumi, TMDB, Simkl, Manga/CloudStream, and common (`KTS-COM`) error codes with filter chips and search in TR/EN.

### 🌐 7. Instant Language Switching & Account UX
- Removed `locale|layoutDirection` from `AndroidManifest.xml` so the activity immediately recreates upon language change. Safety recreate watchdog added for API 33+.
- Kitsugi account screen adds 3 local toggle switches for Encrypted backup, Search history sync, and Notification archive sync.
- In-memory vault password retry and locale-agnostic verification tracking.

---

## 🇹🇷 Türkçe (v2.4.247)

### 🌐 1. Kapsamlı İngilizce Dil Desteği & Arayüz Metinlerinin Yerelleştirilmesi
- **Gömülü (Hardcoded) Türkçe Metinlerin Temizlenmesi:** Kod içerisine doğrudan gömülü kalmış olan yüzlerce Türkçe arayüz metni, Android kaynak sistemine (`R.string`) taşındı. İngilizce dil seçildiğinde Türkçe kalan diyaloglar, ayarlar ve profil sekmeleri tamamen giderildi.
- **41 Dosyada Yerelleştirme Entegrasyonu:** `AuthViewModel`, `KitsugiProfileViewModel`, `AccountSettingsSubPages`, `DownloadsScreen`, `MangaExtensionTab`, `MalProfileExtras`, profil sayfaları (`AniList`, `Bangumi`, `MAL`, `Shikimori`, `Kitsu`, `Simkl`), CrossSync tanı raporları ve bildirim bileşenleri arayüz diline duyarlı hale getirildi.
- **Dinamik Tarih & Göreli Zaman Desteği:** Profil geçmişlerindeki "az önce", "5 dk önce", "3 saat önce", "Dün" gibi zaman ifadeleri ve tarih biçimlendirmeleri (`DateTimeFormatter`) kullanıcının seçtiği yerel ayarlara (`KitsugiStrings.locale()`) bağlandı.
- **Dil Dosyaları Paritesi:** `values/strings.xml` ve `values-en/strings.xml` kaynaklarına 381 yeni simetrik anahtar eklendi; dize kaçış karakterleri (`\'`) AAPT uyumlu olarak standardize edildi.

### 🏢 2. Dinamik Bangumi Kurum Çözümleyici (`BangumiOrgResolver.kt`)
- **API Tabanlı Kurum Adı Çözümleme:** Bangumi infobox alanlarından serbest metin olarak gelen şirket adları (`株式会社マッパ`, `集英社`, vb.), Bangumi'nin resmi `search/persons` (type=2 / Şirket) API ucu üzerinden otomatik olarak küresel adına (`MAPPA`, `Shueisha`, vb.) çözülür.
- **Kesintisiz & Süre Korumalı Mimari:** Kurum çözümleme işlemi 2,5 saniye tavan süre ve negatif önbellek desteğiyle arka planda yürütülür; ağ gecikmelerinde veya bilinmeyen kayıtlarda orijinal ad korunur, sayfa açılışı asla takılmaz.
- **Arama Türü Filtresi (`BangumiApiClient.kt`):** `searchPersons` fonksiyonuna `type` (1=kişi, 2=şirket, 3=grup) filtreleme parametresi eklendi.

### 🎭 3. Bangumi Kadro ve Karakter Rolleri Sözlüğü (`BangumiRoleDictionary.kt`)
- **Kadro ve Rol Çevirisi:** Bangumi API'lerinden ham Çince/Japonca gelen görev ve karakter etiketleri (`主角`, `配角`, `原作`, `监督`, `脚本`...) arayüz diline (TR/EN) uygun olarak yerelleştirildi.
- **Simetrik Rol Kaynakları:** `bangumi_role_*` ve `bangumi_org_*` tanımları için 154 yeni kaynak anahtarı dil dosyalarına eklendi (toplam 6.944 dize %100 parite).

### 🖼️ 4. Vitrin (Hero Section) TMDB + Fanart.tv Görsel Çözümleyici & Akıllı Ekran Uyumu
- **Tüm Kaynaklar İçin TMDB + Fanart.tv Çözümleyici (`KitsugiHeroArtworkResolver.kt`):** Bangumi, AniList, MyAnimeList (Jikan), Kitsu, Shikimori, Simkl, TMDB, Tümü modu, TV vitrini ve Eklenti (Addon) vitrinleri dahil her vitrin öğesi için hem TMDB (`images`, `external_ids`, `alternative_titles`) hem de Fanart.tv (`tvposter`/`movieposter`, `showbackground`/`moviebackground`) paralel olarak sorgulanır.
- **En Kaliteli Dikey Poster & Yatay Arka Plan Otomatik Seçimi:**
  - **Dikey Poster:** TMDB (/original 2000x3000 / 1000x1500) ve Fanart.tv (1000x1426) adayları çözünürlük, 2:3 dikey oran, dil tercihi, sezon uyumu ve topluluk oylarına göre puanlanarak en yüksek kaliteli dikey poster seçilir.
  - **Yatay Arka Plan:** TMDB (/original 4K 3840x2160 / 1080p) ve Fanart.tv adayları çözünürlük (4K > 2K > 1080p), 16:9 sinematik oran, yazısız (textless) temizlik ve oylara göre puanlanarak en yüksek kaliteli yatay arka plan seçilir.
- **Kaynak Bazlı Hatalı/Düşük Çözünürlüklü Görsel Düzeltmeleri:**
  - **Bangumi:** API'nin yalnızca dikey kapak döndürmesine rağmen bunun backdropUrl sanılması düzeltildi; tam boy large (`/pic/cover/l/`) kapak birincil yapıldı.
  - **Kitsu & Shikimori & TMDB:** Kitsu'da medium yerine large/original poster ve cover önceliklendirildi; Shikimori vitrin posterleri fullUrl (originalUrl) sürümüne yükseltildi; TMDB /w500 ve /w1280 adresleri vitrinde /original tam çözünürlüğe yükseltildi.
- **Cihaz Boyutu ve Ekran Yönü Uyumu (`KitsugiHeroSection.kt`):**
  - **Dikey Telefon (`aspect < 1.10`, dikey mod):** Vitrinde birincil olarak en kaliteli dikey poster (`posterUrl`), yedek olarak yatay arka plan kullanılır.
  - **Yatay Telefon, Tablet ve TV (`aspect >= 1.10` veya yatay mod):** Vitrinde birincil olarak en kaliteli 16:9 yatay arka plan (`backdropUrl`), yedek olarak dikey poster kullanılır.
- **Akıllı Hizalama & Çift Katmanlı Kesilme Koruması:**
  - Geniş ekranlarda 16:9 arka planlar ve dikey posterler karakterlerin başını kesmeyecek şekilde üst-orta odaklama (`BiasAlignment(0f, -0.32f)` / `BiasAlignment(0f, -0.25f)`) ile konumlandırılır.
  - Geniş vitrin alanında yalnızca dikey görsel bulunan yapımlarda arkada yumuşak atmosferik dolgu (`alpha 0.30`) + önde `ContentScale.Fit` ile kesilmemiş tam boy dikey görsel sergilenir.
- **Berrak Görünüm (Scrim İyileştirmesi):** Üst üste binen 3 katmanlı koyu gradyan kaldırıldı; görselin üst %60'lık alanı tamamen berrak ve açık bırakıldı. Dikey tablette sol yarıyı karartan yatay gradyan kaldırıldı (yalnızca yatay modda metin okunurluğu için hafifletilmiş geçiş uygulanır).

### 🧩 5. CloudStream Eklenti Çalışma Zamanı ve Tanı Düzeltmeleri
- **Dinamik eklenti ABI'si:** Serialization runtime'ı artık CS3 eklentilerinin bağlandığı host API ile aynı classloader parent'ı üzerinden görünür. `kotlinx.serialization-core` doğrudan bağımlılık olarak eklenip R8 tarafından silinmeye/yeniden adlandırılmaya karşı korunur; bu, raporlarda görülen `KSerializer` `NoClassDefFoundError` sorununu hedefler.
- **Yükleme ön kontrolü:** Eklenti çalıştırılmadan önce `MainAPI`, `KSerializer` ve Serialization `Json` sınıflarının plugin classloader'ından görülebildiği doğrulanır; eksikler canlı tanı izine yazılır.
- **Daha doğru hata tanısı:** Linkage/çalışma zamanı uyumsuzluğu (`KTS-CSP-9110`) ve hesap gereksinimi (`KTS-CSP-9109`) ayrı hata kodları ve tanı sayaçları aldı. HTTP 401 ile raporda görülen giriş gereksinimi metinleri artık yanlışlıkla boş arama olarak gösterilmez; genel HTTP 403 ise Cloudflare kanıtı sayılmaz.
- **Parser imzaları:** Yanıttaki bozuk/uyumsuz içerik belirtileri ve HLS başlık hataları parser kategorisine eklenmiştir.
- **Testler:** Error classifier ve plugin diagnostic runner için çalışma zamanı uyumsuzluğu, kimlik doğrulama ve özet sayaç testleri eklendi.

> Alan adı/DNS erişilebilirliği ve sağlayıcı hesabı gereksinimleri uzak sitelerin durumuna bağlıdır; doğrulanmamış eklenti alan adları bu sürümde tahmin edilerek değiştirilmemiştir.

### 💾 6. Depolama Ekranına "İndirilenler" Bölümü & Toplu Silme
- **Görünmez Dosyalar Artık Ölçülüyor:** Ayarlar > Depolama ekranı yalnızca önbellekleri gösteriyordu; indirdiğiniz videolar, görseller ve altyazılar hiçbir yerde görünmüyordu. Yeni **İndirilenler** bölümü üçünü ayrı ayrı boyut ve dosya sayısı olarak listeler:
  - **İndirilen videolar:** `Download/Kitsugi/Video` altındaki bölümler (uygulama içi kopyalar + dışa aktarılan `.mp4` dosyaları) ve eski `cacheDir/Kitsugi_downloads` klasörü.
  - **İndirilen görseller:** Galeriden cihaza kaydettiğiniz resimler (`Download/Kitsugi/Images`, `Pictures/Kitsugi/Images`, uygulama dış klasörü, eski `Download/Kitsugi`).
  - **İndirilen altyazılar:** Video indirmelerinin yanındaki `subs/` klasörleri + `Download/Kitsugi/Subtitles`.
- **Toplu Silme ve Yıkıcı Onay Penceresi:** Her satır ve yeni **"Tüm indirilenleri sil"** seçeneği, geri alınamaz işlem olduğu için detaylı onay penceresi açar (neyin silineceği, hesap/liste/geçmişin korunacağı açıkça belirtilir). İşlem bittiğinde boyutlar anında tazelenir ve bildirim gösterilir.
- **Güvenli Ayrım:** İndirilen dosyalar önbellek listesine dahil edilmemiştir; "Güvenli önbellekleri temizle" düğmesi ve otomatik bakım döngüsü çevrimdışı arşivinize asla dokunmaz.
- **Kalan "Hayalet" Kopyaların Temizlenmesi (`DownloadExportNaming.kt`):** Tek bölüm silindiğinde dışa aktarılan `.mp4` ve altyazı dosyalarının diskte kalarak gizli şişme yaratması engellendi. Video, altyazı kopyaları ve MediaStore kayıtları tek elden eksiksiz temizlenir.

### 📅 7. Bu Hafta Yayında Takvimi — Izgara Kartı Çerçeve Taşması Düzeltmesi
- **İki Satır Esnek Metin:** Izgara görünümündeki kartların alt satırında yer alan tarih/saat bilgisi (`2026-10-11 01:00 • Yayında`), kart genişliğine sığmadığında `Yayı...` şeklinde kesiliyordu. `AiringTimeText` bileşenine `maxLines = 2` ve esnek ağırlık (`weight(1f)`) eklenerek metnin iki satıra kadar düzgün sarılması sağlandı.
- **Sabit Yükseklik & Düzgün Hizalama:** Izgara bilgi satırı sabit `34.dp` yüksekliğe alınarak tüm kartların aynı boyda kalması garanti edildi; saat ikonu üst satırla hizalandı.

---

## 🇬🇧 English (v2.4.247)

### 🌐 1. Comprehensive English Localization & Hardcoded String Extraction
- **Hardcoded String Elimination:** Migrated hundreds of hardcoded Turkish UI strings across the codebase into standard Android string resources (`R.string`), resolving lingering Turkish text when English is selected.
- **Deep 41-File Integration:** Fully localized dialogs, settings, and profile tabs across `AuthViewModel`, `KitsugiProfileViewModel`, `AccountSettingsSubPages`, `DownloadsScreen`, `MangaExtensionTab`, `MalProfileExtras`, provider profiles (`AniList`, `Bangumi`, `MAL`, `Shikimori`, `Kitsu`, `Simkl`), CrossSync diagnostic summaries, and notification cards.
- **Localized Dates & Relative Timestamps:** Dynamic relative time labels ("just now", "5 min ago", "yesterday") and calendar dates now format using the active user locale (`KitsugiStrings.locale()`).
- **Resource Parity:** Added 381 symmetric string resources across both `values/strings.xml` and `values-en/strings.xml`, ensuring 100% parity and valid AAPT apostrophe escaping.

### 🏢 2. Dynamic Bangumi Organization Resolver (`BangumiOrgResolver.kt`)
- **API-Driven Organization Resolution:** Resolves raw free-text company names from Bangumi infoboxes (e.g. `株式会社マッパ`, `集英社`) to their canonical global names (`MAPPA`, `Shueisha`) via Bangumi's `search/persons` API (`type = 2`, company).
- **Non-Blocking Architecture:** Operates with a strict 2.5-second timeout and negative cache; network failures or unresolvable entries gracefully preserve the original name without blocking page loads.
- **Entity Type Filtering (`BangumiApiClient.kt`):** Added `type` filtering (1=person, 2=company, 3=group) to `searchPersons`.

### 🎭 3. Bangumi Cast & Staff Role Dictionary (`BangumiRoleDictionary.kt`)
- **Role Localization:** Localizes raw Chinese/Japanese role labels from Bangumi endpoints (`主角`, `配角`, `原作`, `监督`, `脚本`...) according to the app UI language.
- **Symmetric Role Resources:** Integrated 154 new `bangumi_role_*` and `bangumi_org_*` string resources (scaling to 6,944 strings with 100% TR/EN symmetry).

### 🖼️ 4. Showcase (Hero Section) TMDB + Fanart.tv Artwork Resolver & Smart Aspect Adaptation
- **Universal TMDB + Fanart.tv Resolver (`KitsugiHeroArtworkResolver.kt`):** Across all providers (Bangumi, AniList, MyAnimeList, Kitsu, Shikimori, Simkl, TMDB, All-in-One mode, TV showcase, and Addon showcases), every hero entry concurrently queries TMDB (`images`, `external_ids`, `alternative_titles`) and Fanart.tv (`tvposter`/`movieposter`, `showbackground`/`moviebackground`).
- **Automated High-Resolution Artwork Ranking:**
  - **Vertical Poster:** Evaluates candidates from TMDB (/original 2000x3000 / 1000x1500) and Fanart.tv (1000x1426) based on resolution, 2:3 ratio, preferred language, season matching, and community votes.
  - **Horizontal Backdrop:** Evaluates candidates from TMDB (4K 3840x2160 / 1080p) and Fanart.tv based on resolution (4K > 2K > 1080p), 16:9 cinematic aspect ratio, textless clarity, and community scores.
- **Provider Resolution Upgrades & Fixes:**
  - **Bangumi:** Resolved issue where vertical covers were treated as horizontal backdrops; prioritized full-resolution large (`/pic/cover/l/`) vertical covers.
  - **Kitsu, Shikimori & TMDB:** Prioritized large/original over medium in Kitsu; upgraded Shikimori showcase posters to fullUrl; upgraded TMDB /w500 and /w1280 assets to /original full fidelity.
- **Orientation & Form-Factor Adaptation (`KitsugiHeroSection.kt`):**
  - **Portrait Phone (`aspect < 1.10`, portrait mode):** Prioritizes vertical poster (`posterUrl`), falling back to backdrop.
  - **Landscape Phone, Tablet & TV (`aspect >= 1.10` or landscape):** Prioritizes cinematic 16:9 backdrop (`backdropUrl`), falling back to vertical poster.
- **Smart Alignment & Dual-Layer Vertical Containment:**
  - Biased crop alignment (`BiasAlignment(0f, -0.32f)` / `BiasAlignment(0f, -0.25f)`) protects character faces and head levels from being cut off on ultra-wide bands.
  - In wide showcase containers with only vertical artwork (e.g. manga covers or titles without backdrops), dual-layer presentation displays an atmospheric background fill with `ContentScale.Fit` uncropped foreground presentation.
- **Crystal-Clear Scrim Presentation:** Removed the 3-layer stacking dark gradients, keeping the top 60% of showcase imagery crisp and bright. Removed the harsh horizontal scrim on portrait tablets.

### 🧩 5. CloudStream Plugin Runtime & Diagnostics Fixes
- **Dynamic plugin ABI:** The serialization runtime is now visible to CS3 plugins through the same host API classloader parent. `kotlinx.serialization-core` is an explicit dependency and is kept from R8 removal/renaming, targeting the reported `KSerializer` `NoClassDefFoundError`.
- **Load preflight:** Before plugin code runs, the loader verifies that `MainAPI`, `KSerializer`, and Serialization `Json` are visible through the plugin classloader; missing classes are recorded in the live diagnostic trace.
- **More accurate classification:** Linkage/runtime incompatibility (`KTS-CSP-9110`) and account requirements (`KTS-CSP-9109`) now have separate codes and diagnostic counters. HTTP 401 and reported login prompts no longer appear as empty searches; a generic HTTP 403 is still not treated as proof of Cloudflare protection.
- **Parser signatures:** Added malformed/unexpected response markers and HLS header errors to the parser category.
- **Tests:** Added classifier and diagnostic-runner coverage for runtime incompatibility, authentication requirements, and summary counts.

> Domain/DNS availability and provider-account requirements depend on remote services. This release does not guess or substitute unverified plugin domains.

### 💾 6. New "Downloads" Section on the Storage Screen & Bulk Deletion
- **Invisible Files Are Now Measured:** Settings > Storage previously only listed caches; downloaded videos, images, and subtitles were unlisted. The new **Downloads** section lists all three with dedicated file size and item counts:
  - **Downloaded videos:** Episode directories under `Download/Kitsugi/Video` (in-app copies + exported `.mp4` files) and legacy `cacheDir/Kitsugi_downloads`.
  - **Downloaded images:** Gallery images saved locally (`Download/Kitsugi/Images`, `Pictures/Kitsugi/Images`, app external folder, legacy `Download/Kitsugi`).
  - **Downloaded subtitles:** `subs/` folders next to video downloads plus `Download/Kitsugi/Subtitles`.
- **Bulk Delete with Destructive Confirmation:** Every download row and the new **"Delete all downloads"** action open an explicit confirmation dialog detailing exactly what will be removed while guaranteeing accounts, lists, and history remain untouched. Sizes refresh dynamically upon completion.
- **Safe Isolation:** Downloaded items are kept strictly separate from the cache lists; "Clear safe caches" and 6-hour automated maintenance routines never touch offline downloads.
- **Fixed "Ghost" Copies (`DownloadExportNaming.kt`):** Single-episode deletion previously left exported `.mp4` files and companion subtitles in public folders, causing silent disk bloat. File naming and lifecycle management are now unified, ensuring video copies, subtitles, and MediaStore records are cleanly expunged together.

### 📅 7. Airing Calendar Grid Card — Frame Overflow & Text Wrap Fix
- **Two-Line Flexible Wrapping:** In the weekly airing calendar's grid layout, the airing date/time stamp (`2026-10-11 01:00 • Airing`) previously clipped with ellipsis (`Air...`) when card widths were narrow. `AiringTimeText` now supports `maxLines = 2` and flexible weighting (`weight(1f)`), wrapping gracefully across two lines without text truncation.
- **Consistent Heights & Alignment:** The metadata info row now uses a fixed `34.dp` height so all grid cards maintain perfectly uniform dimensions; the clock icon aligns with the top text baseline.

---

## 🇹🇷 Türkçe (v2.4.246)

### 🏢 1. Bangumi Master Şirket & Etiket Yerelleştirmesi (TR / EN)
- **Kapsamlı Şirket Sözlüğü (`BangumiOrgDictionary.kt`):** 1013 eşleme ve 365 kayıt ile stüdyo (133), yapımcı (90), yayıncı ağ (96), yayımcı (24) ve dergi (21) adları için küresel Latin ve yerelleştirilmiş kaynak eşlemeleri eklendi.
- **Genişletilmiş Etiket Sözlüğü (`BangumiTagDictionary.kt`):** 907 gruba ulaşıldı (78 yeni anahtar, 95 yeni etiket varyantı). `催淚`/`催泪` (tearjerker), `京都` (Kyoto Animation), `AS`/`After Story` gibi özel etiket boşlukları tamamen kapatıldı.
- **Detay Sayfaları & Çip Entegrasyonu:** Stüdyo, yapımcı ve yayıncı ağ adları detay sayfası bilgi kartlarında, filtre çiplerinde ve stüdyo profil sayfalarında (`toLocalizedOrgName()`) arayüz diline (TR/EN) uygun gösterilir. Monogram avatar, erişilebilirlik etiketi, paylaşım metni ve üst çubuk yerelleştirildi.
- **Dil Dosyaları & Doğrulama:** `strings.xml` ve `values-en/strings.xml` içine 436 yeni simetrik kaynak eklendi. İngilizce dosyadaki 58 hatalı Türkçe değer düzeltildi ve "Çinçe" yazımı "Çince Konuşulan Bölge" olarak güncellendi.
- **Doğrulama & Test:** `BangumiMasterDictionaryTest` birim testleri ve `verify_bangumi_master.py` doğrulama altyapısı eklendi (0 rol çakışması, tam TSV uyumu).

---

## 🇬🇧 English (v2.4.246)

### 🏢 1. Bangumi Master Org & Tag Localization (TR / EN)
- **Comprehensive Org Dictionary (`BangumiOrgDictionary.kt`):** Implemented 1013 mapping lines across 365 distinct organizations: studios (133), producers (90), broadcast networks (96), publishers (24), and magazines (21) with standardized global Latin names.
- **Expanded Tag Dictionary (`BangumiTagDictionary.kt`):** Scaled to 907 canonical tag groups (78 new keys, 95 new variant aliases). Resolved missing Chinese/Japanese aliases such as `催淚`/`催泪` (tearjerker), `京都` (Kyoto Animation), and `AS`/`After Story`.
- **Detail Pages & Chip Integration:** Organization names in anime details, chips, and dedicated studio detail pages now render through `toLocalizedOrgName()` matching the UI language (TR/EN). Monogram initials, accessibility tags, share descriptions, and top bars are fully localized.
- **Resource Symmetry & Cleanups:** Added 436 symmetric string entries across both `values/strings.xml` and `values-en/strings.xml`. Fixed 58 Turkish entries that previously leaked into English resources, and corrected "Çinçe" to "Çince Konuşulan Bölge".
- **Harness & Verification:** Added `BangumiMasterDictionaryTest` JUnit suite and `verify_bangumi_master.py` validator ensuring zero ambiguous role conflicts and 1:1 TSV parity.


---

## 🇹🇷 Türkçe (v2.4.245)

### 🎨 1. Yenilenmiş Duyarlı Vitrin Göstergesi & Çerçeve Tasarımı
- **Sayısal Rozet Kaldırıldı:** Ana sayfa vitrininde (hero slider) yer alan "8 / 10" formatındaki sayısal sayaç rozeti ve `heroCounterIndex` kaldırıldı; arayüz görsel olarak sadeleştirildi ve recomposition yükü azaltıldı.
- **Dinamik Çerçeve Göstergesi (`drawBehind`):** Hangi vitrin sayfasında olunduğunu belirtmek için aktif çizginin etrafını saran yuvarlatılmış estetik bir çerçeve eklendi. Çerçevenin opaklığı sayfa kaydırma geçişinde (`heroPageVisibility`) doğrudan GPU çizim katmanında akıcı şekilde animasyonlanır.
- **Her Ekrana Uyumlu (Responsive) Çizgiler:** Çizgiler `Modifier.weight(1f)` ile yatay alanı eşit paylaşır. `fillMaxWidth` ve `widthIn(max)` kısıtlamaları sayesinde dikey ve yatay telefonlarda veya tabletlerde ekrandan taşma yapmaz; geniş ekranlarda çizgilerin aşırı uzaması engellenir.
- **Çizim Optimizasyonu:** Renk getter'ları (`indicatorFrameColor`) composition kapsamında yakalanarak `drawBehind` içerisindeki çizim güvenliği ve performansı artırıldı.

---

## 🇬🇧 English (v2.4.245)

### 🎨 1. Responsive Hero Slider Indicator & Sleek Active Frame
- **Numeric Counter Removal:** Removed the static "8 / 10" badge and `heroCounterIndex` state from the main hero carousel, delivering a clean, modern aesthetic without recomposition overhead.
- **Active Frame Indicator (`drawBehind`):** Introduced a rounded boundary frame wrapping the active page line. Frame opacity animates smoothly based on page scroll offset (`heroPageVisibility`) directly in the draw phase without recomposing layouts.
- **Fully Responsive Bar:** Indicator lines now evenly distribute width using `Modifier.weight(1f)`. Combined with `fillMaxWidth` and responsive `widthIn(max)`, the indicator perfectly scales across portrait/landscape phones and tablets without overflowing or stretching excessively on large screens.
- **Draw Phase Optimization:** Cleanly captures theme color getters (`indicatorFrameColor`) within the composition scope prior to `drawBehind` canvas execution.


---

## 🇹🇷 Türkçe (v2.4.244)

### 🌐 1. Birleşik Logo Seçim Politikası ve Dil / Sezon Önceliği
- **Ortak Dil ve Eşleşme Politikası (`LogoSelectionPolicy.kt`):** TMDB ve Fanart.tv logo adayları artık tek ve tutarlı bir dil sıralama politikasından geçirilir. Öncelik sırasıyla: seçili uygulama dili → İngilizce → romanize/metinsiz görseller → Japonca/Çince.
- **Sezon Bazlı Logo Uyumu:** Dizi logolarında, aynı dil kademesi içerisinde o an izlenen sezon ile tam eşleşen logo adayları önceliklendirilir.
- **Afiş/Logo İzolasyonu:** Film ve dizi logo sorguları kesin sınırlarla ayrıldı; sezon afişlerinin (posters) logo zannedilip seçilmesi engellendi.
- **Önbellek & Galeri Düzenlemesi:** Galeri sıralamaları ve görsel önbellekleri (`DetailCache.kt`, `GalleryItem.kt`) seçili dil ve sezon bağlamını koruyacak şekilde güncellendi.

### ⚡ 2. P2P Torrent Önbellek Tahliyesi & Otomatik Disk Temizliği
- **Korunan Torrent Tahliyesi Fix (`P2pStreamingEngine.kt`):** `libnuvio_engine` motoru korunan torrent verisini tahliye etmediği için biriken disk boyutu sınırı aşıyordu ("disk cache reclaim limited by protected torrent data"). Oynatıcı kapatıldığında veya akış durduğunda torrent motordan `removeTorrent(id)` ile siliniyor ve `reclaimDiskCache(0L)` ile önbellek anında sıfırlanıyor.
- **Bağlantı Hatası Temizliği:** Akış bağlanamadan hata aldığında eklenen torrent nesnesi de motordan kaldırılarak diskte artık kalması önlendi.
- **Açılışta Artık Süpürme:** Uygulama açılışında, motor boşta ve aktif akış yokken önceki oturumlardan kalan artık payload dosyaları otomatik olarak temizlenir.
- **Önbellek Yönetim Anahtarı (`P2pSettingsSection.kt`):** P2P Depolama ayarları altına *"Video kapanınca önbelleği temizle"* seçeneği eklendi.

### 🏢 3. Bangumi Kurum/Yapımcı Arama Dayanıklılığı & Negatif Önbellek Koruması
- **Genişletilmiş Arama Limiti (`KitsugiBangumiDetailClient.kt`):** Kurum arama limiti 5'ten 10'a yükseltildi, aynı ada sahip birden çok kayıt olduğunda doğru kurumun bulunması sağlandı.
- **Geçici Hata Koruması (Negatif Önbellek Koruması):** Ağ veya zaman aşımı hataları artık "bulunamadı (0)" olarak önbelleğe yazılmaz; geçici hatalar atlatıldığında kurum sayfaları sorunsuz açılır. Bulunamayan geçerli sonuçlar için 10 dakikalık TTL önbelleği (`STUDIO_MISS_TTL_MS`) uygulandı.

### 🔄 4. Profil Hata Ekranı 'Yeniden Dene' Desteği
- **Hızlı Yeniden Deneme (`KitsugiProfileScreen.kt` / `ProfileSharedComponents.kt`):** Profil hata ekranlarına doğrudan tetiklenebilir "Yeniden Dene" butonu eklendi.
- **Hız Sınırı Mesajı:** AniList HTTP 429 hız aşımı yanıtları için net ve anlaşılır uyarı mesajı eklendi.

---

## 🇬🇧 English (v2.4.244)

### 🌐 1. Unified Logo Selection Policy & Language / Season Priority
- **Shared Language Ranking (`LogoSelectionPolicy.kt`):** TMDB and Fanart.tv candidate logos now run through a unified language ranking algorithm: User's selected language → English → Romanized/Textless artwork → Japanese/Chinese.
- **Season-Aware Matching:** For series logos, season-specific artwork matching the current season is preferred within the same language tier.
- **Poster/Logo Isolation:** Strict endpoint and role separation ensures season posters are never mistakenly picked as title logos.
- **Cache & Gallery Sync:** Image gallery ordering and detail caches (`DetailCache.kt`, `GalleryItem.kt`) now take language and season context into account.

### ⚡ 2. P2P Torrent Cache Eviction & Automatic Disk Cleanup
- **Protected Torrent Eviction Fix (`P2pStreamingEngine.kt`):** Addressed root cause where `libnuvio_engine` refused to reclaim disk cache due to active torrent protection ("disk cache reclaim limited by protected torrent data"). Stopping a stream or closing the player now disposes the torrent via `removeTorrent(id)` and calls `reclaimDiskCache(0L)`.
- **Startup & Failure Cleanup:** Any torrent created during a failed connection attempt is proactively unmounted. Leftover cache payloads from previous sessions are swept on application launch.
- **Toggleable Cache Policy (`P2pSettingsSection.kt`):** Added a "Clear cache when video closes" toggle in P2P settings.

### 🏢 3. Bangumi Studio/Person Search Robustness & Cache Safety
- **Expanded Search Limit (`KitsugiBangumiDetailClient.kt`):** Increased studio search limit from 5 to 10 candidates to resolve homonym records accurately.
- **Transient Error Resilience:** Network glitches and timeouts are no longer cached as permanent misses (0 ID), preventing studio pages from staying blocked throughout the session. Legitimate misses use a 10-minute TTL.

### 🔄 4. Profile Error Screen 'Retry' Action
- **Interactive Retry (`KitsugiProfileScreen.kt` / `ProfileSharedComponents.kt`):** Added an explicit Retry button on profile error cards to quickly re-fetch state without navigating away.
- **Rate Limit Messages:** Refined AniList HTTP 429 explanations with dedicated bilingual catalog strings.


---

## 🇹🇷 Türkçe (v2.4.243)

### 🖼️ 1. Shikimori Vitrin Logoları & Doğrudan Görsel Eşleştirme
- **Vitrin Logo Desteği (`HeroLogoPolicy.kt`):** Genel logo ayarı kapalı olsa dahi Shikimori kaynaklı anime içerikleri için vitrin logosu araması etkinleştirildi. Logo bulunamadığında mevcut başlık metni şık bir yedek olarak kalır.
- **MAL Kimliği Eşlemesi (`KitsugiShikimoriClient.kt`):** Arama ve keşfet sonuçlarındaki `myanimelist_id` bilgisi doğrudan `realMalId` alanına aktarıldı. Bu sayede gereksiz aracı sorgular azaltılarak logo eşleştirmesi çok daha hızlı ve doğrudan çalışır.

### 🛡️ 2. Kullanıcı Dostu Profil Hata Ekranı & Hata Sınıflandırması
- **Anlaşılır Hata Kartı (`ProfileErrorFormatter.kt` / `ProfileSharedComponents.kt`):** Ham hata mesajları ve sunucu JSON gövdeleri yerine; HTTP 429 (hız sınırı/rate-limit), oturum, yetki, 404 bulunamadı, zaman aşımı, ağ ve sunucu problemleri için net Türkçe ve İngilizce açıklamalar gösteriliyor.
- **HTTP Durum Rozeti:** Teknik hata detayları kullanıcıyı boğmadan, varsa HTTP kodu küçük ve şık bir durum rozeti olarak arayüzde yer alır.

---

## 🇬🇧 English (v2.4.243)

### 🖼️ 1. Shikimori Hero Logos & Direct Visual Matching
- **Hero Logo Policy (`HeroLogoPolicy.kt`):** Enabled anime hero logo lookups for Shikimori media even when the global logo toggle is disabled. Gracefully falls back to title text if no logo asset exists.
- **Direct MAL ID Mapping (`KitsugiShikimoriClient.kt`):** Mapped `myanimelist_id` to `realMalId` across Shikimori search and explore results, streamlining logo matching without unnecessary intermediary queries.

### 🛡️ 2. User-Friendly Profile Error UI & Classification
- **Clean Error Card (`ProfileErrorFormatter.kt` / `ProfileSharedComponents.kt`):** Replaced raw provider JSON/trace dumps with friendly, localized explanations for HTTP 429 (rate limiting), auth expired, 404 not found, timeouts, network interruptions, and server outages.
- **HTTP Status Badge:** Retains the HTTP status code as a clean, minimal badge alongside the localized description.

---

## 🇹🇷 Türkçe (v2.4.242)

### 🎨 Eklentiler & Kaynaklar Sayfası Tema Uyumu ve Kompakt Arayüz
- **Üst Eklenti Sekmeleri:** Torrent & Akış, Video Kaynakları ve Manga Kaynakları sekmelerinin seçili durumu, kullanıcının ayarladığı özel tema rengi ve iki renkli degrade fırçası (`accentBrushWithAlpha`) ile entegre edildi.
- **Tek Tip Alt Sekme Seçicisi (`AddonSegmentedControl`):** Video ve Manga sayfalarındaki "Repolar" ve "Kurulu" geçişleri için ortak, modern ve kompakt bir seçici bileşeni oluşturuldu.
- **P2P ve Eklenti Anahtarları:** Torrent/P2P ayarları, eklenti açma/kapatma anahtarları, bölüm ikonları ve durum göstergeleri temayla tam uyumlu hale getirildi. Hata ve silme gibi semantik uyarı renkleri Material tema renklerine bağlanarak korundu.
- **Kompakt Kart Ergonomisi & Temalı İkonlar:** Boşluklar, satır yükseklikleri ve ikon boyutları sadeleştirildi; simgesi bulunamayan eklentiler için temalı modern yedek ikonlar getirildi.

---

## 🇬🇧 English (v2.4.242)

### 🎨 Addons & Sources UI Theme Alignment & Compact Layout
- **Top Addon Tabs:** The active tab indicator across Torrent & Stream, Video Sources, and Manga Sources now dynamically adapts to the user's selected accent color and gradient brush (`accentBrushWithAlpha`).
- **Unified Sub-Tab Selector (`AddonSegmentedControl`):** Implemented a shared, compact, and polished segmented selector for switching between "Repositories" and "Installed" across both Video and Manga sources.
- **P2P & Extension Controls:** Switches, section headers, badges, and update actions now align with the current theme palette while preserving semantic error and delete styling.
- **Compact Card Ergonomics & Themed Fallbacks:** Streamlined card paddings, row heights, and icon sizes; added elegant theme-accented placeholder icons when extension icons cannot be loaded.

---

## 🇹🇷 Türkçe (v2.4.241)

### 👤 1. AniList Standartlarında Detaylı MyAnimeList (MAL) Profili
- **Cihaz Üzeri MAL Ayrıştırıcı (`MalWebParser` / `MalWebSource`):** Doğrudan cihaz üzerinden MAL profil, geçmiş ve arkadaş sayfalarını okuyan bağımsız motor. Client ID veya token gerektirmez, IP ban riskine karşı aralıklı istek (rate-limit), akıllı TTL önbelleği ve otomatik bekleme süresi (cooldown) içerir.
- **Veri Akış Önceliği:** Öncelik cihaz üzeri okumada; başarısız olursa miribyou (Vercel), ardından Tenrai devreye girer.
- **Hakkında Sekmesi:** Cinsiyet, doğum günü (yaş hesabı ile), konum, katılım tarihi, son görülme süresi, toplam izleme/okuma gün ve bölüm sayıları, katlanabilir biyografi alanı.
- **Aktivite Sekmesi:** Son güncellemeler (ör. *"12. bölümü izledi • 3 saat önce"*) ve 40 kayda kadar detaylı geçmiş listesi. Kayıtlara dokunulduğunda ilgili anime veya manga sayfası doğrudan açılır.
- **Arkadaşlar & Favoriler:** Profil resimleriyle birlikte arkadaş listesi ve favori anime/manga/karakter listesi.

### 🔑 2. MAL Girişi ve TV QR PKCE Güvenlik Düzeltmesi
- **Giriş İstemcisi Ayrımı:** Tarayıcıda temel kimlik doğrulama ("Oturum açın" / Basic Auth) penceresinin açılması engellendi; `malapp://auth` ile kayıtlı Android OAuth istemcisi (`MAL_OAUTH_CLIENT_ID`) bağlandı.
- **TV QR & Doğrudan Giriş PKCE Fix:** Android TV QR giriş kodunda ve doğrudan tarayıcı oturumunda eksik olan MAL PKCE `code_challenge` ve `codeVerifier` oluşturma/saklama mekanizması düzeltildi.

### ⏱️ 3. Haftalık Yayın Takvimi ("Bu Hafta Yayında") Yenilikleri
- **Dinamik Ekran Uyumu:** Ekran genişliğine göre sütun sayısını ayarlayan `rememberKitsugiGridColumns()` yapısı.
- **Yatay Liste Kartları:** "Tümünü Gör" düzenine uyumlu afiş ve detayları öne çıkaran `AiringEntryWideCard`.
- **Takvim İskelet Animasyonu (`AiringCalendarSkeleton`):** Görünüm türüne (ızgara/liste) göre şekil alan yumuşak yükleme yer tutucuları.
- **İlk Açılış ve Yeniden Deneme:** İlk veriler gelene kadar yaşanan anlık boş ekran parlaması (`hasLoaded`) giderildi; hata durumunda yeniden deneme seçili veri kaynağını koruyarak çalışır.

### 🖼️ 4. Detay Sayfaları İçin Ortak Yükleme İskeletleri ve Kayan Başlık
- **Ortak İskelet (`DetailPageLoadingSkeleton`):** Tüm detay sayfalarında (Stüdyo, Karakter, Personel, Medya Girişi ve API Sonuç) tam ekran dönen çark yerine sayfa düzenini birebir taklit eden shimmer iskeleti.
- **Stüdyo Kayan Üst Çubuk:** Filtre ve görünüm butonları ekrandan çıktıktan sonra 260ms/220ms yumuşak solma animasyonuyla devreye girer; üst çubuğa paylaş butonu eklendi.

### 🌐 5. Kapsamlı Yerelleştirme (i18n) & Arama Motoru Entegrasyonu
- Arama filtreleri, motorlar, kapsamlar ve arama diyalogları (`SearchLocalization.kt`) dahil olmak üzere Türkçe ve İngilizce dil dosyalarında 5.941 dize anahtarı %100 eşleşmeyle korundu.

### ⚡ 6. Eklenti Arama & Cloudflare / DDoS-Guard Motoru (v2.4.240)
- Eklenti isim aramalarında sıfır sonuç ve zaman aşımı sorunları giderildi. 120 sn CF bütçesi, DDOS-Guard negatif önbellek ve CF Gate mekanizması korunmuştur.

---

## 🇬🇧 English (v2.4.241)

### 👤 1. Detailed MyAnimeList (MAL) Profile
- **On-Device MAL HTML Parser (`MalWebParser` / `MalWebSource`):** Self-contained on-device scraping for MAL profile, history, and friends pages. Requires no client ID or token, features rate limiting, TTL caching, cooldowns, and automatic failover.
- **Data Source Hierarchy:** On-device scraping takes priority; falls back to miribyou (Vercel) and then Tenrai.
- **About Tab:** Gender, birthday (with calculated age), location, join date, last online, total watch/read time and episodes/chapters, collapsible about/bio card.
- **Activity Stream:** Recent updates (e.g., *"Watched ep 12 • 3 hours ago"*) and up to 40 history records. Tapping an activity directly navigates to the media entry.
- **Friends & Favorites:** Friends list with profile avatars, favorites for anime, manga, and characters loaded directly.

### 🔑 2. MAL Authentication & TV QR PKCE Security Fix
- **Dedicated Android OAuth Client:** Fixed browser basic authentication popup by routing OAuth logins through the registered Android client (`MAL_OAUTH_CLIENT_ID` with `malapp://auth`).
- **TV QR PKCE Fix:** Resolved missing PKCE parameters (`code_challenge` / `codeVerifier`) in Android TV QR codes and direct device login dialogs.

### ⏱️ 3. Weekly Airing Calendar ("Airing This Week")
- **Adaptive Screen Layout:** Dynamic column layout with `rememberKitsugiGridColumns()`.
- **Wide List Cards:** Redesigned `AiringEntryWideCard` with prominent posters and airtime countdowns.
- **Calendar Skeleton Placeholder (`AiringCalendarSkeleton`):** Shimmer placeholders dynamically matching list or grid view modes.
- **Flicker-Free Initial Load & Retry:** Eliminated initial empty screen flash (`hasLoaded`) and preserved selected data source across retries.

### 🖼️ 4. Shared Detail Skeletons & Floating Sticky Headers
- **Universal Detail Skeleton (`DetailPageLoadingSkeleton`):** Replaced full-screen progress spinners across Studio, Character, Staff, Media, and API detail pages with layout-accurate shimmering skeletons.
- **Studio Floating Header:** Smooth 260ms/220ms fade trigger once controls exit viewport, plus direct share action.

### 🌐 5. Comprehensive Localization (i18n) & Search Parity
- Full parity across 5,941 string keys in `values/strings.xml` and `values-en/strings.xml`, covering search engines, filters, chips, dialogs (`SearchLocalization.kt`), and Explore.

### ⚡ 6. Plugin Search & Cloudflare / DDoS-Guard Resilience (v2.4.240)
- Full CF-aware timeouts (120s), DDoS-Guard host locking, negative caching, and pre-search CF Gate validations.

---

## 🇹🇷 Türkçe (v2.4.238)

### 📊 1. Geliştirici Günlükleri (Developer Logs) Yenilendi
- **Tam Genişlik Başlık:** Başlık alanı tam genişliğe yayıldı, dikey harf kırılması sorunu çözüldü; kapat butonu sağa yerleştirildi.
- **Yatay Kaydırılabilir Araç Çubuğu:** Yenile, Kopyala, Kaydet, Paylaş ve Temizle butonları kompakt ve yatay kaydırılabilir modern bir satıra toplandı.
- **Log Seviye Filtreleri:** Hata, Uyarı, Bilgi ve Hata Ayıklama (Debug) seviyeleri anlık sayaçlarıyla filtrelenebilir hale getirildi.
- **Gelişmiş Seçenekler:** "Sadece Bu Uygulama" filtresi, "Satır Kaydır" ve "Otomatik Yenile" anahtarları ile toplam/hata/uyarı özet satırı eklendi.
- **Düz Metin Paylaşım:** Log paylaşımı doğrudan okunabilir düz metin formatına geçirildi.

### 📐 2. Duyarlı Medya Izgaraları (Responsive Grid Columns)
- **Tek Merkezden Dinamik Sütun Hesabı (`KitsugiGridColumns`):** Tüm medya ızgaraları (Tümünü Gör, Stüdyo, Yapımcı, Favoriler, Koleksiyon Klasörleri, Manga Kaynakları ve Eklenti Izgaraları) standartlaştırıldı.
- **Cihaz & Yön Uyumu:** Dar ekranlarda (telefon dikey, < 600dp) 2 sütun, orta ekranlarda (telefon yatay / küçük tablet, 600–839dp) 3 sütun, geniş ekranlarda (tablet yatay, ≥ 840dp) 4 sütun; dikey/yatay geçişte otomatik yeniden hesaplanır.

### 🌄 3. Vitrin (Hero Section) Karartma ve Okunabilirlik İyileştirmesi
- **Derin Alt Karartma:** Telefon dikey modunda alt karartma yüksekliği 220dp'den 300dp'ye uzatıldı ve degrade opaklığı artırıldı; başlık ve açıklamaların altındaki görsel parlamaları engellendi.
- **Belirgin Meta Satırı:** Tür, yıl ve bölüm bilgisi içeren meta satırının rengi TextSecondary yapılarak koyu zemin üzerinde çok daha rahat okunabilir hale getirildi.

### 🌐 4. GitHub Vekil Sunucu (jsDelivr) Anahtarı Düzeltildi
- **Kök Neden Giderildi:** Eklenti ve akış ayarlarındaki diyalog çağrılarında (akış seçici, manga kaynakları) useGithubProxy / onUseGithubProxyChanged parametreleri geçilmiyordu; diyalog varsayılan (false) değerle açıldığı için switch açılamıyordu. Ayrıca repo katmanı ayarı DataStore'dan okumuyordu.
- **Düzeltme:** Hem telefon hem Android TV diyaloglarına bağlandı (TV için özel ayar satırı eklendi). CloudstreamRepoRepository artık ayarı okur; anahtar açıkken eklenti ve repo listeleri doğrudan jsDelivr CDN üzerinden çekilir. Doğrudan GitHub ve hata durumunda jsDelivr yedeği aynen korunur.

### ⚡ 5. Akış Listesi Kasması Tamamen Çözüldü (4 Ayrı İyileştirme)
- **Animasyon Yükü Kaldırıldı:** Akış kartları her kaydırmada AnimatedVisibility (fadeIn + slideInVertically) ile yeniden çiziliyordu; gereksiz animasyon kaldırılarak liste kaydırması pürüzsüzleştirildi.
- **Akıllı ve Gecikmeli Ölçüm:** Eskiden her kart compose edildiğinde hemen HLS/HEAD ağ ölçümü başlıyordu (60 sonuçta onlarca paralel istek). Artık ölçüm yalnızca **görünür kartlarda** ve **350 ms yerleşme gecikmesinden sonra** başlar; kaynak kalite/ses/altyazı bilgilerini zaten sunuyorsa ölçüm hiç yapılmaz (isProbeNeeded).
- **İptal Edilebilir Ölçüm Kapısı:** Bloklayıcı OkHttp.execute() yerine Call.enqueue ve suspendCancellableCoroutine kullanıldı; kart görüş alanından çıkınca istek gerçekten iptal edilir ve eşzamanlılık izni hemen serbest kalır (eşzamanlılık kapasitesi 3 → 2).
- **Filtresiz Hızlı Yol:** Arama veya etiket filtresi aktif değilken 60+ akış için çalıştırılan regex tabanlı kalite/dil çözümlemesi atlanır; çipler key ile optimize edildi.

### 🖼️ 6. MAL ve MAL'a Bağlı Kaynaklar Galeriye Ortak Edildi
- **Tüm Detay Sayfaları:** Anime, film ve dizi detaylarında gerçek MAL kimliği çözülebiliyorsa MAL (Jikan) /pictures görselleri Fanart.tv, TMDB ve Shikimori görselleriyle **paralel** çekilip galeriye eklenir.
- **Manga Galerisi Zenginleşti:** Manga detay sayfaları eskiden sadece kapak görseliyle sınırlıydı; artık MAL ek görselleri de galeriye katılır.
- **Kimlik Güvenliği:** AniList/Kitsu/Bangumi stable ID'leri ve TMDB/Simkl ID'leri MAL kimliği olarak kullanılmaz; yanlış yapımın görselleri kesinlikle galeriye karışmaz.
- **Önbellek:** 80 kayıtlık LRU önbellek; geçici hataların kalıcı olmaması için boş sonuçlar önbelleğe yazılmaz.
- **MAL OAuth Client Secret Desteği & Harici Tarayıcı:** Web/özel MAL uygulamaları için mal_client_secret desteği local.properties ve BuildConfig'e eklendi; MAL girişi harici tarayıcıya yönlendirilerek giriş güvenliği sağlandı.
- **Çapraz Eşitleme Logoları:** Çapraz eşitleme ayar satırlarında resmi marka logoları (AniList, MAL, Simkl, Kitsu, Shikimori, Bangumi) yerleştirildi.

### 🎌 7. Bangumi Liste Üyeliği ve Sağlayıcı İzolasyonu
- **Yalnızca Gerçek Bangumi Kayıtları:** Bangumi sekmesinde ve sayaçlarında yalnızca kullanıcının Bangumi hesabına ait (source = bangumi ve geçerli Bangumi stable ID'li) kayıtlar gösterilir.
- **Sağlayıcı Çapraz Karışması Engellendi:** Diğer sağlayıcıların (Simkl, AniList, MAL) kayıtları veya yerel kayıtlar Bangumi üyeliği sayılmaz; bgm.tv koleksiyonuyla birebir sayı ve liste tutarlılığı sağlandı.

### 🔄 8. Bangumi Uzak Öncelikli Ekleme ve Düzenleme Akışı
- **Önce Uzak Koleksiyon Güncellemesi:** Bangumi arama sonuçlarından ekleme veya düzenleme yapıldığında istek önce doğrudan Bangumi API'sine iletilir (BangumiSyncManager.syncEntryToBangumi).
- **Veri Güvenliği Garantisi:** Uzak koleksiyon güncellemesi başarılı olmadan (collectionUpdated) yerel veritabanına kayıt eklenmez veya değiştirilmez. Başarılı olunca Bangumi subject ID'si stable alanda saklanır.
- **Doğrudan Bangumi Bağlamı:** Bangumi kayıtları düzenleyicide kendi yerel durumları, puanı ve ilerlemesiyle açılır.

### 🛡️ 9. Bangumi Senkronizasyon İzolasyonu
- **Dış Dağıtım Engeli:** Bangumi üzerindeki koleksiyon işlemleri diğer sağlayıcılara (AniList / MAL / Simkl / Shikimori) fan-out yapmaz; sağlayıcıya özel temiz senkronizasyon korunur.

### 🎬 10. Bangumi REAL İçeriklerde Film / Dizi Ayrımı ve Tür Güvenliği
- **Platform Tabanlı Eşleme:** Bangumi REAL kategorisindeki canlı çekim (live-action) yapımlar platform bilgisine göre doğru bir şekilde Film (Movie) ve Dizi (TV) türlerine dönüştürülür.
- **Anime Varsayılanı Kaldırıldı:** Desteklenmeyen (müzik, oyun vb.) veya belirsiz türdeki Bangumi kayıtlarının otomatik olarak Anime şeklinde içe aktarılması engellendi.
### 🌍 11. Kapsamlı İngilizce Dil Tamamlama (Batch 2)
- **825 Yeni Dil Kaynağı:** `values/strings.xml` ve `values-en/strings.xml` 4.234'ten 5.052 anahtara çıkarıldı; TR/EN tam parite sağlandı.
- **1.685 Kod Noktası Kaynağa Bağlandı:** Kod içine gömülü sabit metinler temizlenerek 891 `stringResource` ve Composable olmayan katmanlar için 794 `KitsugiStrings.get` çağrısına bağlandı (288 dosya).
- **Yeni KitsugiStrings Altyapısı:** ViewModel, servis ve veri katmanlarında güvenli ve erken aşamada başlatılan metin çözücü mimarisi devreye alındı.

---

## 🇬🇧 English (v2.4.238)

### 📊 1. Redesigned Developer Logs Dialog
- **Full Width Header:** Full horizontal title bar avoids awkward vertical text breaking, with close button pinned cleanly to the right.
- **Scrollable Toolbar:** Refresh, Copy, Save, Share, and Clear buttons organized into a sleek horizontal row.
- **Level Filters:** Filter logs by Error, Warning, Info, and Debug with live badge counters.
- **Enhanced Utilities:** "Only This App" process filter, text wrapping, auto-refresh toggles, and total/error/warn summary header.
- **Plain Text Sharing:** Direct plain-text sharing for immediate readability in external apps.

### 📐 2. Responsive Media Grid Layout (KitsugiGridColumns)
- **Centralized Column Calculation:** Standardized across See All, Studios, Producers, Favourites, Folder Collections, Manga Sources, and Addon Grids.
- **Breakpoint Optimization:** 2 columns on phones portrait (<600dp), 3 columns on landscape/foldables (600–839dp), 4 columns on tablets (>=840dp); adapts dynamically on rotation.

### 🌄 3. Hero Section Contrast & Readability
- **Extended Gradient Vignette:** Height expanded to 300dp on phone portrait with heavier gradient stops, preventing background image bleed behind titles.
- **High-Contrast Metadata:** Meta information row styled in TextSecondary for clear legibility over dark backdrop fades.

### 🌐 4. GitHub Proxy (jsDelivr) Toggle Fixed
- **Root Cause Resolved:** Dialog callers across stream picker and manga browse omitted useGithubProxy / onUseGithubProxyChanged, causing the switch to always render off and ignore user input. The repository layer also neglected to read the setting.
- **Fix:** Fully wired on phone and Android TV (with a dedicated TV settings row). CloudstreamRepoRepository now reads the setting; when enabled, repo and plugin requests prefer jsDelivr CDN directly. Fallback mechanisms remain intact.

### ⚡ 5. Stream Picker Performance & Stutter Resolution
- **Removed Replay Animations:** Eliminated per-card AnimatedVisibility enter animations that re-ran on every scroll pass, restoring silky-smooth LazyColumn scrolling.
- **Debounced Smart Probing:** Probing is now gated exclusively to visible cards after a 350 ms settle delay (rapid scrolling incurs zero probe requests). If a provider already supplies quality, audio, and subtitles, probing is bypassed (isProbeNeeded).
- **Cancellable Probing Queue:** Converted blocking OkHttp calls to cancellable Call.enqueue with suspendCancellableCoroutine. Abandoned requests are immediately cancelled, releasing concurrency permits (capacity 3 -> 2).
- **Filterless Fast Path:** Regex-based quality and audio resolution is skipped when no active filters are set; filter chips are keyed.

### 🖼️ 6. MAL & Linked Sources Shared Gallery
- **Unified Media Galleries:** Detail pages for anime, TV series, and movies now resolve genuine MAL IDs and fetch MAL (Jikan) /pictures in parallel with Fanart.tv, TMDB, and Shikimori.
- **Enriched Manga Galleries:** Manga details, previously limited to their single cover, now pull in additional MAL artwork.
- **Strict ID Namespace Safety:** AniList/Kitsu/Bangumi stable IDs and TMDB/Simkl IDs are never forwarded to MAL, guaranteeing no cross-media artwork contamination.
- **LRU Caching:** 80-entry LRU cache prevents duplicate fetches; empty results are never cached to avoid locking out transient network issues.
- **MAL OAuth Client Secret Support & External Browser:** Added mal_client_secret configuration support to local.properties and BuildConfig; directs MAL authorization to external browser for dependable OAuth completion.
- **Cross-Sync Platform Logos:** Official vector logos added to cross-sync toggles for AniList, MAL, Simkl, Kitsu, Shikimori, and Bangumi.

### 🎌 7. Bangumi List Membership & Provider Isolation
- **Pure Bangumi Scope:** The Bangumi tab and collection counters exclusively display authentic Bangumi records (source = bangumi with stable Bangumi subject IDs).
- **Cross-Provider Isolation:** Records from other providers (Simkl, AniList, MAL) and local-only items are never treated as Bangumi members, maintaining exact parity with bgm.tv.

### 🔄 8. Remote-First Add & Edit Flow for Bangumi
- **Remote Update Precedence:** Adding or editing Bangumi search results updates the remote Bangumi collection first (BangumiSyncManager.syncEntryToBangumi).
- **Data Integrity Assurance:** If the remote API call fails (collectionUpdated == false), the local database is not altered. Upon success, the entry is committed with its stable Bangumi subject ID.
- **Native Bangumi Context:** Bangumi entries open directly in Bangumi editor mode, cleanly handling native collection statuses, progress, and scoring.

### 🛡️ 9. Bangumi Isolated Synchronization
- **No External Leakage:** Bangumi updates remain isolated and do not fan out to other tracking services (AniList / MAL / Simkl / Shikimori).

### 🎬 10. REAL Media Type Classification & Safety
- **Platform-Aware Mapping:** Bangumi REAL entries are accurately mapped to Movie or TV based on platform metadata.
- **No False Anime Defaults:** Unsupported categories (music, games) or unknown types are rejected rather than incorrectly defaulting to Anime.
### 🌍 11. Comprehensive English Localization (Batch 2)
- **825 New Localization Keys:** Resource files expanded to 5,052 strings with 100% TR/EN parity across all screens.
- **1,685 Hardcoded Call Sites Cleaned:** Migrated 891 `stringResource` and 794 `KitsugiStrings.get` points across 288 files.
- **KitsugiStrings Architecture:** Non-Composable string resolver providing robust localization across ViewModels and background services.

---

## 🇹🇷 Türkçe (v2.4.237) — Hesap Kasma Çözümü, Vitrin Tıklama, Grid & Navigasyon Düzeltmeleri, MAL Uygulama İçi Giriş, Arama Logoları, Eklenti Bildirimi, Galeri İzolasyonu

### ⚡ 1. Bağlı Hesap Kaynaklı Kasma & Takılma Tamamen Çözüldü (`ExploreMapping`)
- **Kök Neden Giderildi:** Keşfet, "Tümünü Gör" ve Arama ekranlarındaki listede olmayan her bir kart için kullanıcının bağlı hesabındaki TÜM kütüphane taranıyor ve başlıklar ana iş parçacığında (UI thread) her karede yeniden normalize ediliyordu. Hesapta kayıt sayısı arttıkça yaşanan ağırlaşmanın asıl sebebi buydu.
- **Doğrudan İndeks Arama:** Tam liste taraması tamamen kaldırıldı. Eşleşme O(1) karma indeks adaylarıyla sınırlandırıldı. `realMalId` indekse eklenerek doğru eşleşmeler korundu, kaydırma ve yüklemeler yağ gibi akıcı hale getirildi.

### 🎯 2. Vitrin (Hero Section) Tıklama Yönlendirmesi ve Belirgin Sayaç Göstergesi
- **Baskın Öğeyi Çözümleme (`openVisibleHeroItem`):** Vitrinde üst üste binen katmanlarda görünmez komşu sayfaların tıklamaları yutması çözüldü; tıklama anında ekranda görsel olarak baskın olan içerik (`round(currentPage + offset)`) doğrudan açılıyor (One Piece'e tıklayınca One Piece, Frieren'e tıklayınca Frieren).
- **Belirgin Noktalar ve Sayısal Sayaç:** Sayfa gösterge noktaları kalınlaştırılıp opaklaştırıldı (7dp); yanına kaçıncı sıradaki içeriğin gösterildiğini belirten `N / M` (örn. 3 / 11) sayısal sayaç rozeti eklendi. `derivedStateOf` ile performans korundu.

### 🚀 3. "Tümünü Gör" Grid Yarış Durumu (Race Condition) ve İç İçe Geçme Düzeltildi
- **Snapshot Birleştirme Mantığı:** Hızlı kaydırma sırasında 1. sayfa zenginleştirilirken (enrich) 2. sayfa geldiğinde oluşan yarış durumu çözüldü; enrich tamamlandığında mevcut liste snapshot ile akıllıca birleştirilerek (`merge`) kartların kaybolması ve zıplaması önlendi.
- **Benzersiz & Stabil Kimlik (`exploreIdentity`):** `source:type:malId:tmdbId:cs3UrlHash` tabanlı stabil anahtarlama sağlandı. TMDB film/dizi ve eklenti ID çakışmaları tamamen engellendi.
- **Stabil Compose Durumu:** Grid ve listelerdeki `rememberSaveable` kaynaklı Bundle restore çakışması `rememberLazyGridState()` ve `rememberLazyListState()` ile çözüldü; Compose kartları sorunsuz yeniden kullanılıyor.

### 🔑 4. MyAnimeList (MAL) Girişinde Uygulama İçi WebView ve Basic Auth Çözümü
- **Uygulama İçi WebView (`KitsugiOAuthWebViewDialog`):** Harici tarayıcıya yönlendirme yerine uygulama içi modern WebView ile doğrudan giriş sağlandı.
- **HTTP Basic Auth Popup Engellendi:** Brave gibi tarayıcılarda 401 Basic Auth popup çıkmasına sebep olan sorun giderildi; `onReceivedHttpAuthRequest` engellenerek kullanıcının doğrudan standart HTML giriş formuna erişmesi sağlandı.
- **Otomatik Yönlendirme:** `malapp://auth` yönlendirmesi yakalanarak token takası sorunsuz tamamlanıyor. `KitsugiWebViewDialog` için de aynı Basic Auth koruması uygulandı.

### 📖 5. Manga "Oku" Navigasyonu Düzeltildi
- **Detay Sayfası Üstünde Açılış:** Manga detay sayfasından (örn. Berserk) "Oku" butonuna basıldığında kaynak arama ekranı artık detay sayfasının üzerinde açılıyor.
- **Geri Dönüş Korundu:** `detailBackStack` temizlenmesi engellendi; kaynak arama ekranında geri tuşuna basıldığında Keşfet sekmesine düşmek yerine doğruca ilgili manganın detay sayfasına dönülüyor.

### 📐 6. Keşfet Raf Başlıklarına 20dp Kenar Boşluğu
- **Hizalama Düzenlendi:** "Yayındaki Animeler", "Yaklaşan Animeler", "Tümünü Gör" dahil tüm raf başlıklarına ve üst kısımlara kartlarla birebir aynı 20dp iç boşluk (`padding`) verildi; ekran kenarına yapışık görünüm giderildi.

### 🖼️ 7. "Tümünü Gör" Grid Görünümü ve 2:3 Poster Oranı
- **Tam Poster Görünümü:** Poster alanı sabit 210dp yerine 2:3 dikey en-boy oranına (`aspectRatio(2f / 3f)`) geçirildi; afişlerin kırpılması önlendi.
- **Uyumlu Kolon Düzeni:** Stüdyo sayfasıyla eşitlendi: standart telefonlarda 3 sütun, geniş ekranlarda 4 sütun, tabletlerde 5 sütun.

### 🔔 8. Eklenti Kurulumunda Bildirim Çubuğu (`AddonInstallNotifier`)
- **Tam Ekran Engelleyici Panel Kaldırıldı:** Keşfet ekranını kaplayan büyük "Eklentiler Kuruluyor" paneli tamamen kaldırıldı. İlerleme sessizce Android bildirim çubuğunda gösteriliyor ("10 / 165 · İndiriliyor: Anizm"). Ayarlar ekranındaki repo kartı ilerlemesi korundu.

### 🎨 9. TMDB ve Fanart Galeri/Logo İzolasyonu (Uncharted / Family Full House Düzeltmesi)
- **Film ve Dizi ID Alanı Ayrıldı:** TMDB'de film ve dizi ID'leri birbirinden izole edildi; film sayfasına yalnızca film afiş/arkaplanları, dizi sayfasına yalnızca dizi görselleri bağlandı.
- **Başlık Eşleşme Doğrulaması:** TMDB başlığı medya başlığıyla uyuşmadığında logonun yanlış yapıma uygulanması engellendi.

### 🏷️ 10. Arama Sayfasında Orijinal Marka Logoları (Emoji Yerine Vektör Logolar)
- **Orijinal Marka Logoları Entegre Edildi:** Kaynağa özel arama başlıklarındaki emojiler (AniList, MyAnimeList, TMDB, Shikimori, Kitsu, Simkl, Bangumi) kaldırılarak `KitsugiPlatformLogo` vektör çizimleri (`ic_logo_*`) yerleştirildi.
- **Filtre ve Sıralama Panelleri:** Başlıklar ve sonuç kartlarındaki rozetler resmi marka logolarıyla zenginleştirildi; uluslararasılaşma (i18n) tamamlandı.

### 🛡️ 11. "Çok Yönlü Eşitleme" Diyaloğu Uyarı Kartı Sadeleştirildi
- **Katlanabilir Güvenlik Notu (`SafetyNote`):** Tekrarlanan turuncu uyarı kutusu tek bir katlanabilir karta dönüştürüldü; güvenlik ikonu, tek cümle özet ve animasyonlu yasal metinle kompakt hale getirildi.

### 🧩 12. Eklentiler ve Kaynaklar Sayfası Düzenlendi
- **Segment Seçici & Ortak Kartlar:** Mobil ekranda RealDebrid giriş alanı temizlendi; sekmeler segment seçiciye geçirildi ve ortak boş-durum bileşenleriyle zenginleştirildi.

### 🌸 13. Shikimori Stüdyo Kapakları ve Bangumi Düzenleme Düzeltmeleri
- **GraphQL Poster Çözümleme:** Stüdyo yapımlarındaki eksik kapaklar 50'lik gruplarla GraphQL üzerinden çekildi.
- **Bangumi Düzenle Desteği (`editingSourceHint`):** Bangumi detayından düzenleme açıldığında Simkl'e düşme engellendi; Bangumi durum pill'leri ve rozetleri gösterildi.

### 🌐 14. Çeviri Motoru İyileştirmeleri & Harici Çeviri Uygulaması
- **Dinamik Dil:** Ayarlanan hedef dile göre duyarlı etiketleme yapıldı; 429 rate limit koruması sağlandı.
- **Harici Uygulama Açılışı:** Çeviri düğmeleri kullanıcının harici çeviri uygulamasına (`openTranslator`) bağlandı.

---

## 🇬🇧 English (v2.4.237)

### ⚡ 1. Library Mapping Performance Bottleneck Fixed (`ExploreMapping`)
- **Root Cause Eliminated:** For every card not in the user list, the code performed an O(N) full scan over the user's entire connected account library and normalized titles on the UI thread.
- **Direct Index Resolution:** Eliminated full library fallbacks. Candidate searches are strictly index-driven. Added `realMalId` index keys to preserve genuine matches, eliminating frame drops.

### 🎯 2. Hero Section Click Routing & Prominent Counter Badge
- **Dominant Item Resolution (`openVisibleHeroItem`):** In the multi-layer parallax hero pager, invisible neighboring pages no longer intercept touch events. Taps resolve the visually dominant item (`round(currentPage + offset)`) directly (tapping One Piece opens One Piece, tapping Frieren opens Frieren).
- **Thicker Dots & Numerical Counter Badge:** Pagination dots thickened and emphasized (7dp); added an `N / M` (e.g. 3 / 11) numerical badge indicator updating via `derivedStateOf`.

### 🚀 3. "See All" Grid Race Condition & Visual Overlap Fix
- **Enrich Snapshot Merging:** Resolved the race condition during fast scrolling where page 1 enrichment overwritten newly loaded page 2 results. Snapshot merge logic ensures persistent, smooth data flow without flickering or jumping cards.
- **Unique & Stable Identity (`exploreIdentity`):** Switched to robust composite keying (`source:type:malId:tmdbId:cs3UrlHash`), preventing TMDB movie/TV and extension collisions.
- **Stable Compose State:** Replaced `rememberSaveable` state restores with `rememberLazyGridState()` and `rememberLazyListState()` to eliminate Bundle index drift and overlapping layouts.

### 🔑 4. In-App MyAnimeList (MAL) OAuth & Basic Auth Elimination
- **In-App OAuth Dialog (`KitsugiOAuthWebViewDialog`):** Replaced external browser redirection with an optimized in-app WebView for seamless authentication.
- **HTTP Basic Auth Dialog Suppressed:** Intercepted and blocked 401 Basic Auth browser dialogs (`onReceivedHttpAuthRequest`), presenting MAL's native HTML login form directly.
- **Redirect Capture:** Seamlessly catches `malapp://auth` redirects and performs the OAuth token exchange automatically.

### 📖 5. Manga "Read" Navigation Fixed
- **Open on Top of Detail:** Tapping "Read" from a manga detail screen (e.g. Berserk) now presents the source search page on top of the media detail view.
- **Back Navigation Restored:** Prevented clearing `detailBackStack`; pressing the Android Back button now reliably returns to the manga detail screen instead of falling back to Explore.

### 📐 6. Explore Shelf Header 20dp Padding
- **Uniform Alignment:** Added 20dp horizontal padding to shelf headers ("Airing Anime", "Upcoming Anime", "See All", etc.) matching the media cards, resolving edge-clipping.

### 🖼️ 7. "See All" Grid & 2:3 Poster Aspect Ratio
- **Full Poster Presentation:** Switched fixed 210dp height to 2:3 vertical aspect ratio (`aspectRatio(2f / 3f)`), completely preventing poster cropping.
- **Responsive Columns:** Standardized column count to 3 columns on phones, 4 on wide phones, and 5 on tablets.

### 🔔 8. System Notification for Bulk Addon Installs
- **Removed Full-Screen Overlay:** The blocking "Installing Addons" overlay was removed. Real-time progress is now presented quietly via Android notification bar ("10 / 165 · Downloading: Anizm").

### 🎨 9. TMDB & Fanart Artwork Isolation (Uncharted / Family Full House Fix)
- **ID Namespace Separation:** Film and TV show IDs are strictly separated; film views only receive movie artwork, TV views only receive show artwork.
- **Strict Title Verification:** Vector and Fanart logos are rejected if the TMDB title does not match the media title.

### 🏷️ 10. Official Brand Logos in Search (Vector Logos)
- **Official Brand Logos:** Replaced emojis across search engine headers (AniList, MyAnimeList, TMDB, Shikimori, Kitsu, Simkl, Bangumi) with official vector brand icons (`ic_logo_*`).
- **Sheets & Result Badges:** Filter sheets, chips, and result badges now feature platform logos.

### 🛡️ 11. Streamlined Cross-Sync Safety Disclaimer
- **Collapsible Safety Card (`SafetyNote`):** Compacted disclaimer text into a collapsible surface with security icon and expandable legal text.

### 🧩 12. Addons & Sources UI Modernization
- **Segmented Tabs:** Removed mobile RealDebrid token input; added segmented tab selector, counter badges, and shared empty state components.

### 🌸 13. Shikimori Studio Posters & Bangumi Edit Context
- **GraphQL Poster Batching:** Batched 50 items per query to retrieve missing studio production covers.
- **Bangumi Edit Routing:** Preserved Bangumi source context to display native status pills and badges instead of Simkl.

### 🌐 14. Translation Engine & External App Integration
- **Target Language Responsive:** Dynamic target language chip and 429 backoff protection.
- **Direct App Launch:** Universal translator action opens preferred external translation app via `openTranslator`.

---

## 🇹🇷 Türkçe (v2.4.236) — Depolama Şişmesi, Performans & Dil Düzeltmeleri

### 🗄️ 1. Merkezî Depolama Bekçisi (`StorageJanitor`) & Depolama Ayarları (~4 GB Şişme Çözümü)
- **Kök Neden Çözüldü:** P2P torrent önbelleği (2 GB), görsel önbelleği (512 MB), manga önbelleği (~150 MB), video önizlemeleri ve sınırsız büyüyen veritabanı kayıtları merkezî bir temizlik mekanizması olmadığı için kurulumu ~4 GB'a kadar şişiriyordu.
- **Merkezî Bekçi (`StorageJanitor` & `StorageMaintenanceWorker`):** Disk kullanımını kalem kalem ölçen, bütçe ve yaş kurallarına göre sessizce budayan sistem eklendi. Ağ şartı olmayan günlük WorkManager bakımı ve 6 saatte bir hafif açılış bakımı devreye alındı.
- **Ayarlar > Depolama Ekranı (`StorageSettingsContent`):** Kullanıcıların disk kullanımını kalem kalem görmesini ve tek dokunuşla güvenli önbellekleri temizlemesini sağlayan modern depolama paneli eklendi (hesap, indirmeler ve geçmiş asla silinmez).
- **Atıl Temizlik Kodları Canlandırıldı:** Mobil veride çalışmayan detay önbelleği budaması koşulsuz hale getirildi; `video_cache` 30 gün, `translation_cache` 5.000 satır ve arama geçmişi 500 kayıt tavanıyla sınırlandı. Altyazı arşivleri 14 günde bir temizleniyor.

### ⚡ 2. Kasma ve Donma Zinciri Kırıldı (Kare Gecikmesi %96 İyileştirildi)
- **Manga Okuyucu Tarama Yükü Kaldırıldı:** Her manga sayfası yazıldığında tüm klasörün taranıp boyut toplanması yerine süreç genelinde paylaşılan artımlı sayaç getirildi.
- **Kare Gecikmesi ve Jank Azaltıldı:** Logcat/gfxinfo analizlerindeki 1200 ms'lik takılmalar giderildi; p99 kare süresi 1200 ms'den 46 ms'ye düşürüldü, takılma oranı %30'dan %8'e indirildi.
- **İç İçe Looper ve Disk Logcat Kaldırıldı:** `KitsugiApplication` içindeki ek ana looper döngüsü (`Looper.loop()`) ve arka planda diske sürekli yazan `logcat -f` süreci temizlendi.
- **Soğuk Açılış & Oynatma İlerlemesi:** İndirme listesi ve izleme geçmişinin ana iş parçacığında JSON ayrıştırması IO'ya taşındı; oynatma sırasındaki disk yazımları 2,5 sn'lik toplulaştırmaya alındı.
- **Degrade Shader & Brush Önbelleği (`GradientBrushCache`):** Her çizim karesinde yeni shader/fırça tahsisatı engellendi, AniList 429 rate limit bekleme koruması eklendi.

### 🛡️ 3. GPU RenderThread Çökme Koruması (`Modifier.safeBlur`)
- **Donanımsal Bulanıklık Güvenliği:** API 31 altındaki cihazlarda ve riskli GPU sürücülerinde `libhwui` SIGSEGV çökmelerine yol açan koşulsuz `.blur()` çağrıları `Modifier.safeBlur()` ile korumaya alındı; aşırı yarıçap değerleri sınırlandı.
- **Üst Üste Binen GPU Katmanları Temizlendi:** `KitsugiCinematicLoadingScreen` içindeki çift GPU bulanıklık katmanı teke indirildi; `MangaDetailScreen` ve eklenti diyaloglarındaki ağır bulanıklıklar optimize edildi.

### 🌐 4. Android 13+ Dil Değiştirme ve Çökme Düzeltmesi
- **Aktif Dil Senkronizasyonu (`locales_config.xml`):** Android 13+ `LocaleManager` için Türkçe ve İngilizce dilleri açıkça tanımlandı (`locales_config.xml`), manifest entegrasyonu tamamlandı. "İngilizce seçili ama Türkçe görünüyor" sorunu giderildi.
- **API 33+ Modern Dil API'si:** Dil geçişi `AppCompatDelegate.setApplicationLocales()` ile modernize edildi; dil değişimi sonrası çağrılan `recreate()` kuyruğun sonuna alınarak döngü ve çökme engellendi.
- **Dize Formatı Hataları Düzeltildi:** Dil dosyalarındaki hatalı yüzde formatları (`Normal (%0)` -> `Normal (0%)`) düzeltildi.

### 📐 5. Kart Düzeni, Metin Kesilmesi ve Geri Sayım Optimizasyonu
- **Rozet ve Başlık Kesilmesi Çözüldü:** 2 satırlı başlık, alt başlık ve sonraki bölüm rozetinin alt kısımdan taşmasını engellemek için portre kart metin alanı 182 dp'ye (`EXPLORE_CARD_PORTRAIT_TEXT_HEIGHT`) yükseltildi.
- **10 Saniyelik Donma Döngüsü Kırıldı:** `NextAiringChip` içindeki her 10 saniyede bir ekranı tetikleyen sayaç 30–60 saniyeye çıkarıldı. `KitsugiUpcomingAirEnricher` iptal istisnaları (`CancellationException`) doğru yönetildi.

### 🧲 6. Yatay Satırlarda Akıcı Kart Yaslama (Snap Scrolling)
- **Sola Yapışan Kaydırma Geri Getirildi:** Keşfet vitrinleri, arama sonuçları, detay rafları ve profil sayfalarındaki yatay kart listelerine `KitsugiScrollDefaults.rememberRowSnapFling(state)` eklendi; kartlar kaydırma bitiminde sola akıcı ve muntazam biçimde oturuyor.

### 🔑 7. Liste Anahtarları (Key Collision) & "Tümünü Gör" Sonsuz Yükleme Düzeltmesi
- **Tür Bazlı Kimlik Ayrımı (`KitsugiLazyKeys.kt`):** Keşfet, arama ve ızgara listelerinde aynı sayısal kimliğe sahip anime ve mangaların çakışmasını engellemek için anahtarlara medya türü (`type`) dahil edildi.
- **Sonsuz İstek Döngüsü Sınırlandı:** `FullScreenMediaGridPage` sayfasında kullanıcı kaydırmadan tetiklenen sürekli sayfa çekme döngüsüne kaydırma başına bütçe eklendi.

---

## 🇬🇧 English (v2.4.236) — Storage Bloat, Performance & Localization Fixes

### 🗄️ 1. Central Storage Janitor (`StorageJanitor`) & Settings > Storage (~4 GB Bloat Fix)
- **Root Cause Resolved:** P2P torrent cache (2 GB), image cache (512 MB), manga cache (~150 MB), video previews, and unconstrained Room cache rows accumulated to ~4 GB installations due to lack of central lifecycle management.
- **Central Storage Janitor (`StorageJanitor` & `StorageMaintenanceWorker`):** Added a unified storage engine measuring disk usage per bucket, pruning data via age and size thresholds. Added network-unconstrained daily WorkManager jobs and 6-hour interval launch maintenance.
- **Settings > Storage Screen (`StorageSettingsContent`):** Added an interactive storage dashboard showing per-category sizes, item counts, and safe one-tap cleanup (user accounts, downloads, and history are strictly preserved).
- **Revived Dormant Cleanup Routines:** Unblocked detail cache pruning by removing Wi-Fi/battery-only restrictions; pruned `video_cache` beyond 30 days, capped `translation_cache` to 5,000 rows, limited search history to 500 rows, and auto-removed subtitle archives older than 14 days.

### ⚡ 2. Broken Jank Chains & Systemwide Frame Latency Fix
- **Manga Reader Cache Overhead Eliminated:** Switched from costly whole-directory walks on every written page to an incremental memory-backed byte counter.
- **96% Frame Latency Reduction:** Eliminated 1200ms frame stalls seen in logcat/gfxinfo; p99 latency dropped from 1200ms to 46ms, and jank rate fell from ~30% to 8.13%.
- **Removed Nested Looper & Disk Logcat:** Removed redundant nested `Looper.loop()` in `KitsugiApplication` and background `logcat -f` disk redirection.
- **Cold Start & Playback Progress:** Deferred download list and watch history JSON parsing off the main thread; debounced video playback watch progress writes to 2.5s with background flush.
- **Cached Gradient Shaders & Brushes (`GradientBrushCache`):** Cached linear gradients and draw node brushes, stopping continuous per-frame shader allocations. Added AniList 429 rate limit cooldown.

### 🛡️ 3. GPU RenderThread Crash Safety (`Modifier.safeBlur`)
- **Hardware Blur Guard:** Replaced unconditional `.blur()` modifiers across screens with `Modifier.safeBlur()`, skipping blur on API < 31 and clamping blur radius to prevent `libhwui` RenderThread SIGSEGV crashes on vulnerable GPUs.
- **Eliminated Redundant GPU Layers:** Removed nested double GPU blur pass in `KitsugiCinematicLoadingScreen`; optimized heavy blur layers in `MangaDetailScreen` and addon dialogs.

### 🌐 4. Android 13+ Per-App Language Switching & Crash Fix
- **Explicit Locale Configuration (`locales_config.xml`):** Declared English and Turkish locales in `res/xml/locales_config.xml` and manifest to satisfy Android 13+ `LocaleManager` requirements, resolving "English selected but Turkish shown" issues.
- **Modernized Locale API for API 33+:** Switched to `AppCompatDelegate.setApplicationLocales()`. Deferred activity `recreate()` call via main looper handler post to prevent activity recreation crashes.
- **Fixed Malformed String Templates:** Corrected invalid percentage token placements in localized resources (`Normal (%0)` -> `Normal (0%)`).

### 📐 5. Card Layout, Text Clipping & Countdown Timer Optimization
- **Badge & Title Overflow Fix:** Increased portrait card text container height to 182dp (`EXPLORE_CARD_PORTRAIT_TEXT_HEIGHT`), ensuring two-line titles, subtitles, and next-airing chips render cleanly without clipping.
- **Eliminated 10-Second Frame Freeze:** Re-tuned `NextAiringChip` ticker interval from 10s to 30-60s. Handled `CancellationException` properly in `KitsugiUpcomingAirEnricher` to avoid negative caching loops.

### 🧲 6. Horizontal Shelf Snap Scrolling
- **Restored Left-Edge Snap Fling:** Added `KitsugiScrollDefaults.rememberRowSnapFling(state)` (`SnapPosition.Start`) across Explore shelves, search rows, detail recommendations, and profile collections.

### 🔑 7. Lazy Column/Row Key Collision & Infinite Load Loop Fix
- **Type-Aware Identity Keys (`KitsugiLazyKeys.kt`):** Included media type (`type`) in Compose item keys across 11 shelves and grids, preventing collision when anime and manga share the same ID.
- **Bounded Pagination Budget:** Fixed continuous unprompted pagination in `FullScreenMediaGridPage` by requiring active scroll gestures.

---

## 🇹🇷 Türkçe (v2.4.235)

### 🎨 1. Premium Açılır Menü Tasarımı (`KitsugiDropdownMenu`)
- **Özel Popup & Yumuşak Animasyon:** Material3'ün varsayılan menüsü yerine uygulamanın koyu yüzeyine uyumlu, 20dp yuvarlak köşeli, kenarlıklı, gölgeli ve fade + hafif büyüme animasyonlu özel menü bileşeni uygulandı.
- **Vurgulu Seçim & Onay İkonu:** Seçili öğeler yarı saydam vurgu rengi ve onay işaretiyle belirginleştirildi; akıllı konumlandırma ile ekrandan taşma ve klavye çakışmaları engellendi.
- **Evrensel Entegrasyon:** Sezonluk filtreler, Listem sıralama paneli, altyazı gecikmesi, profil etkinlik menüsü ve arama filtreleri dahil tüm açılır menüler yeni bileşene taşındı.

### ⚡ 2. Keşfet, Arama ve Tümünü Gör Kaydırma Performansı Düzeltmesi (v2)
- **Kaydırma Recomposition Döngüsü Kırıldı (`RetainedLazyListState`):** Kaydırma konumu Compose State yerine düz bir nesnede saklanarak her pikselde tüm ekranın yeniden çizilmesi kökten engellendi.
- **Shimmer İçerik Ağacı Kararlılığı (`KitsugiShimmer`):** Yükleme durumu değiştiğinde tüm içerik ağacının ve Hero Pager durumunun sökülüp sıfırlanması durduruldu; dinamik fırça tek bir sabit provider içine alındı.
- **Hero Backdrop Toplu Yayın:** Vitrin arka planlarının TMDB'den çekilmesi 250 ms'lik pencerede gruplandırılarak gereksiz ekran yenilemeleri sonlandırıldı.
- **Fonksiyon Referansı Optimizasyonu:** `AppRoot` içerisindeki liste kontrol fonksiyonları `remember` ile sabitlenerek çocuk kartların fazladan çizilmesi engellendi.

### 🕷️ 3. TMDB & Simkl Kurgusal Karakter Detay Düzeltmesi (Peter Parker / Spider-Man)
- **Doğrulanmış Karakter Eşleşmesi:** Jikan (MAL) yedek aramasında ilk sonucun körlemesine alınması engellendi; yalnızca isim gerçekten eşleştiğinde kabul ediliyor.
- **Birincil Ad Ayrımı:** TMDB'deki "Gerçek Ad / Kurgusal Ad" kalıplarında arama birincil isimle yapılarak genel lakapların (ör. "Spider" -> Makishima Yuusuke) yanlış anime profillerini açması çözüldü.

### 📺 4. TMDB Yayıncı Ağları ve Yapım Şirketi Ayrımı
- TV yayıncı ağları (`/network/{id}`) ile stüdyo/yapım şirketleri (`/company/{id}`) farklı uç noktalara ayrılarak yanlış kimlik yönlendirmeleri düzeltildi.

---

## 🇬🇧 English (v2.4.235)

### 🎨 1. Premium Dropdown Menu Redesign (`KitsugiDropdownMenu`)
- **Custom Popup & Smooth Animations:** Replaced Material default dropdowns with a sleek, dark-themed popup featuring 20dp rounded corners, borders, shadows, and subtle fade-scale animations.
- **Active State Highlights:** Selected items feature accent semi-transparent backgrounds and check icons, with collision-aware screen positioning and vertical scrollability.
- **Universal Migration:** Upgraded seasonal filter sheets, My List sort dialogs, subtitle delay panels, profile activity menus, and search filter chips.

### ⚡ 2. Explore, Search & See All Scroll Jank Fix (v2)
- **Eliminated Scroll Recomposition Loop (`RetainedLazyListState`):** Shifted scroll position tracking from Compose State to a plain object read only during state saving, stopping per-frame full-screen recompositions.
- **Static Shimmer Tree (`KitsugiShimmer`):** Kept the composition hierarchy stable across loading transitions without tearing down the hero pager and shelf tree.
- **Debounced Hero Backdrops:** Grouped backdrop resolution flushes to 250ms windows in `ExploreViewModel`, preventing thrashing during initial loads.
- **Stable Function References:** Stabilized `AppRoot` lambda references with `remember` to preserve composable skippability across grids and cards.

### 🕷️ 3. TMDB & Simkl Fictional Character Details Fix (Spider-Man / Peter Parker)
- **Verified Character Matching:** Replaced unvalidated first-result Jikan fallback with explicit name matching and ambiguity checks.
- **Primary Name Splitting:** TMDB dual-identity names ("Real Name / Fictional Name") now search by primary identity, preventing common alias words like "Spider" from pulling unrelated anime characters (Makishima Yuusuke).

### 📺 4. TMDB TV Networks vs Production Companies Endpoint Separation
- Dedicated `/network/{id}` routing for TV networks and separate company endpoints for studios/producers.

---

## 🇹🇷 Türkçe (v2.4.234)

### 🏢 1. Bangumi Yapımcı / Yayıncı Çipleri Tıklanabilirlik ve Kimlik Çözümleme Düzeltmesi
- **Ad Tabanlı Kurum Çözümleme (`resolveCompanyPersonId`):**
  - Bangumi infobox verilerinde kimliği olmayan kurumlar için çip tıklanabilirliği açıldı. Kimliği olmayan çipler artık tıklanarak hedef sayfaya kurum adını taşır (`KitsugiDetailComponents.kt`).
  - Hedef stüdyo/yapımcı sayfası kurum adıyla Bangumi API'de (`/v0/search/persons`, `type=2`) arama yapar; şirket bulunamazsa yapımcı şahıslar (`type=1`) kabul edilerek kişinin yapım listesi açılır (`KitsugiStudioClient.kt`).
- **Enrich Kapasitesi ve Zaman Aşımı Artırıldı:**
  - `KitsugiBangumiDetailClient.kt`: Ön kimlikleme limiti 8'den 24'e, toplu sorgu zaman aşımı 6 saniyeden 10 saniyeye çıkarıldı. Steins;Gate gibi 15+ kurumlu zengin detay sayfalarında tüm yayıncı ve yapımcılar önceden kimliklenir.
- **Kimliksiz Sayfalarda Yeniden Deneme:**
  - `StudioDetailViewModel.kt`: Sadece `studioId > 0` şartı yerine `name != null` durumu da yeniden deneme mekanizmasına dahil edildi.

### 🌐 2. Kapsamlı UI Yerelleştirmesi (i18n Tamamlama — Batch 1 & Bangumi)
- **Tam TR / EN Dil Dosyası Paritesi:**
  - `values/strings.xml` ve `values-en/strings.xml` dosyalarına 446 yeni kaynak eklendi; toplam **4194 anahtar** ile %100 parite sağlandı.
- **1000'den Fazla Gömülü Türkçe Metnin Taşınması:**
  - Detay sayfası: Stüdyolar, Yapımcılar, Yayıncı Ağlar, Etiketler, Türler, sekmeler, bilgi kartları, puanlama ve spoiler kontrolleri.
  - Stüdyo & Yapımcı sayfası: "Yapımlar", "X içerik", filtreleme çipleri (Tümü/Anime/Manga/Film/Dizi), sıralama seçenekleri, boş durum ve hero rozetleri.
  - Eklenti "Tümünü Gör" sayfası, tam ekran medya ızgarası ve görsel galeri diyaloğu.
- **Merkezi Yardımcılar:**
  - `MediaType.localizedLabel()`, `GalleryCategory.localizedLabel()`, `WatchStatus.localizedLabel()` ile tüm etiketler seçili uygulama diline (`isTurkish()`) göre dinamik olarak üretilir.

---

## 🇬🇧 English (v2.4.234)

### 🏢 1. Bangumi Producer / Network Chips Clickability & Identity Resolution Fix
- **Name-Based Entity Resolution (`resolveCompanyPersonId`):**
  - Unlocked clickability for producer and network chips originating from Bangumi infoboxes without upfront IDs (`KitsugiDetailComponents.kt`).
  - Target studio/producer screens now resolve the company by name via Bangumi API (`/v0/search/persons`, `type=2`); falls back to person records (`type=1`) for individual producers, displaying their production list (`KitsugiStudioClient.kt`).
- **Increased Enrichment Capacity & Timeout:**
  - `KitsugiBangumiDetailClient.kt`: Increased lookup limit from 8 to 24 and batch timeout from 6s to 10s. Large entries like Steins;Gate (15+ organizations) now resolve all producers and TV networks in advance.
- **Retry Support for Name-Resolved Pages:**
  - `StudioDetailViewModel.kt`: Allows retrying failed loads when `name != null`, even if the initial entity ID was zero.

### 🌐 2. Comprehensive UI Localization (i18n Completion — Batch 1 & Bangumi)
- **100% TR / EN String Parity:**
  - Added 446 new string resources across `values/strings.xml` and `values-en/strings.xml`, bringing total synchronized keys to **4194**.
  - Replaced over 1000 hardcoded Turkish UI strings across 185+ Kotlin files with `stringResource` and `context.getString`.
- **Extended Area Coverage:**
  - Detail screens: Studios, Producers, Networks, Tags, Genres, tabs, info grids, ratings, and spoiler controls.
  - Studio & Producer screens: Productions header, item counts, filter chips (All/Anime/Manga/Movie/TV Series), sorting options, empty states, and hero pills.
  - Addon "See All" full-screen grids, global media grids, and image gallery viewer.
- **Centralized Label Helpers:**
  - Introduced `MediaType.localizedLabel()`, `GalleryCategory.localizedLabel()`, and `WatchStatus.localizedLabel()` ensuring all models reflect the active language dynamically.

---

## 🇹🇷 Türkçe (v2.4.233)

### 🚀 1. Kaydırma Takılmaları (Scroll Jank) ve Recomposition Optimizasyonları
- **`derivedStateOf` ile Gereksiz Yeniden Çizimlerin Önlenmesi:**
  - `ExploreScreen.kt`: `lazyListState.firstVisibleItemIndex` doğrudan okunması yerine `derivedStateOf` içine alındı (`isHeroVisible`), her scroll pikselinde tüm vitrin ve alt ağacının yeniden recompose olması engellendi (`ExploreScreen.kt`).
  - `FullScreenMediaGridPage.kt`: Kayan başlık görünürlüğü (`showFloatingHeader`), `derivedStateOf` içine alınarak liste veya ızgara kaydırılırken her karede tetiklenen recomposition döngüsü kesildi (`FullScreenMediaGridPage.kt`).
- **Sabit Identity Tabanlı Lazy Key'ler (Index Kaldırıldı):**
  - Grid ve listelerdeki key yapılarından pozisyon indeksi (`$idx`, `_g$idx`, `_l$idx`) kaldırıldı. Filtreleme, sıralama ve sayfalama esnasında kartların sıfırdan yeniden oluşturulması engellendi; stabil identity (`${source}_${malId}_${tmdbId}_${cs3Url.hashCode()}`) ile kart composable yeniden kullanımı sağlandı (`KitsugiHorizontalMediaSection.kt`, `FullScreenMediaGridPage.kt`, `SearchScreen.kt`, `KitsugiMediaGridDialog.kt`).
- **Coil Crossfade Kaldırıldı (Akıcı Fling Deneyimi):**
  - `KitsugiApplication.kt`: Hızlı kaydırma (fling) anında ekrana giren her kartın 150ms crossfade animasyonu başlatarak ana iş parçacığını (UI thread) kilitlemesi engellendi; görsel yükleme akışı doğrudan hazırlandığında render edilir (`KitsugiApplication.kt`).
- **Mobil "Tümünü Gör" Diyaloğu LazyVerticalGrid Dönüşümü:**
  - `KitsugiMediaGridDialog.kt`: 40+ kartı aynı anda oluşturan ve bellek baskısı yaratan `FlowRow + verticalScroll` yapısı, sadece ekranda görünen öğeleri oluşturan yüksek performanslı `LazyVerticalGrid` bileşenine dönüştürüldü (`KitsugiMediaGridDialog.kt`).
- **`contentType` ile Composable Havuzlama (Item Pooling):**
  - Tüm LazyColumn, LazyRow ve LazyVerticalGrid yapılarına açık `contentType` tanımları eklendi (`media_card`, `h_media_card`, `seasonal_card`, `ranking_card`, `search_row`, `char_staff_row`); ekran dışına çıkan öğelerin Compose tarafından verimli bir şekilde havuzlanıp geri dönüştürülmesi sağlandı.
- **Kart İçi Bellek ve Çizim Tahsislerinin (Allocations) Azaltılması:**
  - `KitsugiExploreMediaCard.kt`, `KitsugiRankingMediaCard.kt`, `DetailedSeasonalMediaCard.kt`, `SearchResultRow.kt`: `getDisplayTitle(titleLanguage)` ve `Shape` nesneleri `remember` ile önbelleğe alındı.
  - `KitsugiNsfwImage.kt`: `AsyncImage` için `onError` lambda'sı `remember` ile sabitlenerek her recomposition'da gereksiz yeniden sorgulama engellendi.

### 🛡️ 2. Sayfa Geri Çekilmesi (Scroll Bounce), Kilitlenme ve Zıplama Düzeltmeleri
- **Dokunmatik Cihazlarda Odak Geri Çekilmesinin Engellenmesi:**
  - `TvFocusModifier.kt`: Telefon ve tabletlerde kartların odak alması kapatıldı (`.focusProperties { canFocus = false }`). Kartların dokunmatik kaydırma sırasında odak alıp `BringIntoView` ile listeyi parmağın tersine geri çekmesi engellendi.
  - `KitsugiScrollDefaults.kt` & `AppRoot.kt`: Telefon ve tablet için no-op `rememberTouchPassthroughSpec()` eklendi; TV cihazları D-pad odak davranışını korurken dokunmatik cihazlarda kaydırma hareketi tamamen parmağa devredildi.
- **Neon Çerçevelerden Yazılım Bulanıklığı Gölgesinin Kaldırılması:**
  - `KitsugiNeonGlowCard.kt`: RenderNode katmanında yazılım tabanlı GPU blur düşmesine ve kaydırmanın kilitlenmesine neden olan `Modifier.shadow` kaldırıldı; kart çerçevesi yalnızca yüksek performanslı degrade kenarlık olarak çizilir.
- **Nested LazyRow'ların Düz `horizontalScroll` Satırlarına Dönüştürülmesi:**
  - `FullScreenMediaGridPage.kt`, `StudioDetailPage.kt`, `AddonFullScreenGridPage.kt`: Ana dikey ızgara/liste kaydırılırken ölçüm döngüsünü tetikleyip kartları ileri-geri zıplatan iç içe tür çipler `Row + horizontalScroll(rememberScrollState())` yapısına geçirildi (`GridGenreChipRow`).
- **Başlık & Sekme Geçişlerinde Histerezis ve Solma Animasyonu:**
  - `DetailPageScaffold.kt`, `CharacterDetailPage.kt`, `StaffDetailPage.kt`, `StreamScreenContent.kt`: Yüzen başlıkların yükseklik değiştirerek viewport'u daraltması engellendi (`fadeIn`/`fadeOut`). Sınır geçişinde histerezis eklendi; sekme değişimindeki `scrollToItem(2)` zıplaması kaldırıldı.
  - `MyListComponents.kt`: Piksel bazlı yükseklik hesabı yerine eşik aşıldığında bir kez kapanan (`headerCollapsed`) histerezisli yapıya geçildi.
- **Yatay Satırlardan Snap Fling Davranışının Kaldırılması:**
  - `ModernHomeRows.kt`, `ExploreComponents.kt`, `ReviewsTab.kt`, `KitsugiHorizontalMediaSection.kt`, `SearchScreen.kt`, `KitsugiUserProfileScreen.kt`, `UserProfileStatsTab.kt`, `AniListProfileContent.kt`, `MalProfileContent.kt`: İç içe satırlardaki `rememberSnapFlingBehavior` kaldırılarak dikey kaydırma hareketinin çalınması ve kilitlenmesi önlendi.
- **Non-Observable Kaydırma Konumu Saklama (Recomposition Döngüsü Kesildi):**
  - `KitsugiProfileViewModel.kt` & `KitsugiUserProfileViewModel.kt`: Kaydırma konumları `mutableIntStateOf` yerine düz `Int` değişkenlere çevrildi; `snapshotFlow` ile toplanarak her pikselde sayfanın baştan kurulması önlendi.
  - `RetainedLazyListState.kt`: `RetainedScrollPosition` ve `listSaver` mimarisiyle yeniden yazılarak sayfa sıfırlanmaları giderildi.
  - `recordStablePageHeight`: Pager ölçümlerindeki 4px altındaki ölçüm gürültüsü filtrelenerek sonsuz yeniden ölçüm önlendi.
- **Teknik Dokümantasyon:** `PLAN_TASK_KAYDIRMA_ZIPLAMA_FIX_2026-10-10.md`, `PLAN_TASK_SCROLL_JANK_PERFORMANS_FIX_2026-10-10.md`, `SCROLL_JANK_FIX_RAPORU_2026-10-10.md`.

---

## 🇬🇧 English (v2.4.233)

### 🚀 1. Scroll Jank Elimination & Recomposition Optimizations
- **`derivedStateOf` Scroll State Decoupling:**
  - `ExploreScreen.kt`: Decoupled `lazyListState.firstVisibleItemIndex` via `derivedStateOf` (`isHeroVisible`), eliminating redundant recompositions of the hero section and entire view tree on every scroll frame (`ExploreScreen.kt`).
  - `FullScreenMediaGridPage.kt`: Wrapped floating header visibility (`showFloatingHeader`) in `derivedStateOf`, preventing frame-by-frame recomposition cascades during list and grid scrolling (`FullScreenMediaGridPage.kt`).
- **Stable Identity-Based Lazy Keys (Eliminated Index Dependency):**
  - Replaced index-dependent keys (`$idx`, `_g$idx`, `_l$idx`) with pure, stable identity keys across all lists and grids (`${source}_${malId}_${tmdbId}_${cs3Url.hashCode()}`). Filtering, sorting, and pagination no longer invalidate composable item state, maximizing reuse (`KitsugiHorizontalMediaSection.kt`, `FullScreenMediaGridPage.kt`, `SearchScreen.kt`, `KitsugiMediaGridDialog.kt`).
- **Disabled Coil Crossfade for Butter-Smooth Fling:**
  - `KitsugiApplication.kt`: Removed 150ms image crossfade transitions during rapid scrolling/flinging, eliminating main UI thread bottlenecks caused by concurrent fade animators on entry cards (`KitsugiApplication.kt`).
- **Converted Mobile "See All" Dialog to `LazyVerticalGrid`:**
  - `KitsugiMediaGridDialog.kt`: Replaced the legacy `FlowRow + verticalScroll` layout (which instantiated 40+ cards simultaneously) with a virtualized `LazyVerticalGrid`, rendering only visible items and drastically cutting composition overhead (`KitsugiMediaGridDialog.kt`).
- **Composable Recycling with `contentType`:**
  - Added explicit `contentType` tags across all LazyColumn, LazyRow, and LazyVerticalGrid items (`media_card`, `h_media_card`, `seasonal_card`, `ranking_card`, `search_row`, `char_staff_row`), enabling Compose item pooling for off-screen cards.
- **Card Allocation and Render Optimization:**
  - `KitsugiExploreMediaCard.kt`, `KitsugiRankingMediaCard.kt`, `DetailedSeasonalMediaCard.kt`, `SearchResultRow.kt`: Memoized `getDisplayTitle(titleLanguage)` and `Shape` instances with `remember`.
  - `KitsugiNsfwImage.kt`: Stabilized the `onError` lambda callback using `remember`, preventing `AsyncImage` from treating callback instantiations as model changes.

### 🛡️ 2. Scroll Jitter, Lockup & Viewport Retraction Fixes
- **Eliminated Focus Retraction on Touch Devices:**
  - `TvFocusModifier.kt`: Disabled focus on phones and tablets (`.focusProperties { canFocus = false }`), preventing clickable cards from taking focus and pulling the viewport backward via `BringIntoView`.
  - `KitsugiScrollDefaults.kt` & `AppRoot.kt`: Injected no-op `rememberTouchPassthroughSpec()` on mobile devices; TV keeps D-pad centering while touch scrolling is entirely restored to user gesture control.
- **Removed Software-Blurred Shadow on Neon Glow Cards:**
  - `KitsugiNeonGlowCard.kt`: Removed `Modifier.shadow` with colored ambient/spot tints that forced software-fallback blurs during rapid scrolling; cards now render crisp, high-performance gradient borders only.
- **Replaced Nested LazyRows with Standard `horizontalScroll`:**
  - `FullScreenMediaGridPage.kt`, `StudioDetailPage.kt`, `AddonFullScreenGridPage.kt`: Converted nested genre chips in grid/list headers to `Row + horizontalScroll(rememberScrollState())` (`GridGenreChipRow`), eliminating the parent grid remeasurement loop.
- **Hysteresis Thresholds & Fade-Only Header Transitions:**
  - `DetailPageScaffold.kt`, `CharacterDetailPage.kt`, `StaffDetailPage.kt`, `StreamScreenContent.kt`: Replaced height-animating headers with fixed-height `fadeIn`/`fadeOut` transitions; added index hysteresis and eliminated tab-jump `scrollToItem(2)`.
  - `MyListComponents.kt`: Replaced continuous pixel-based height calculation with a thresholded collapse boolean (`headerCollapsed`).
- **Removed Snap Fling Behaviors from Nested Rows:**
  - `ModernHomeRows.kt`, `ExploreComponents.kt`, `ReviewsTab.kt`, `KitsugiHorizontalMediaSection.kt`, `SearchScreen.kt`, `KitsugiUserProfileScreen.kt`, `UserProfileStatsTab.kt`, `AniListProfileContent.kt`, `MalProfileContent.kt`: Removed `rememberSnapFlingBehavior` across all horizontal carousels to prevent stealing vertical scroll gestures.
- **Non-Observable Scroll Position Persistence:**
  - `KitsugiProfileViewModel.kt` & `KitsugiUserProfileViewModel.kt`: Converted scroll positions to plain `Int` properties, reading them via `snapshotFlow` without triggering full screen recompositions.
  - `RetainedLazyListState.kt`: Refactored with `RetainedScrollPosition` and `listSaver` to prevent position jumping on restore.
  - `recordStablePageHeight`: Dampened 4px height fluctuations in pager tabs to stop infinite layout loops.
- **Technical Documentation:** `PLAN_TASK_KAYDIRMA_ZIPLAMA_FIX_2026-10-10.md`, `PLAN_TASK_SCROLL_JANK_PERFORMANS_FIX_2026-10-10.md`, `SCROLL_JANK_FIX_RAPORU_2026-10-10.md`.

---

## 🇹🇷 Türkçe (v2.4.232)

### 🎨 1. Gradyan Vurgu Rengi (Accent Gradient) Tam Uygulama Entegrasyonu
- **Vurgu Rengi Ezilmesinin Giderilmesi:** `AppRoot.kt` içinde hazır tema kimliğinin (`mint`) kullanıcının seçtiği özel rengi veya iki renkli gradyanı ezmesi engellendi; `LocalKitsugiAccent` doğrudan korunarak gradyanın tüm telefon/tablet arayüzünde aktif kalması sağlandı (`AppRoot.kt`).
- **Küresel Gradyan Bileşen Sarmalayıcıları (`KitsugiGradientMaterial.kt`):** Uygulama genelindeki tüm bileşenler için açılı lineer gradyan fırçası (`LocalKitsugiAccentBrush`) ve pürüzsüz siyah/beyaz otomatik kontrast desteği (`LocalKitsugiOnAccent`) getirildi:
  - `Modifier.background` ve `Modifier.border`: Non-composable `ModifierNodeElement` mimarisiyle vurgu rengi veya yarı saydam tonları (`AccentMuted`) otomatik olarak gradyan fırçasıyla çizilir.
  - `Text` ve `Icon`: Vurgu renkli metinler `TextStyle.copy(brush = accentBrush)` ile, ikonlar ise `CompositingStrategy.Offscreen` + `BlendMode.SrcIn` ile gradyan boyanır; vurgulu dolguların üzerinde otomatik kontrast rengini alır.
  - `Switch`, `Slider`, `RangeSlider`, `RadioButton`, `Checkbox`, `CircularProgressIndicator`, `LinearProgressIndicator`, `TabRow`, `ScrollableTabRow`, `FilterChip`, `Button`, `FloatingActionButton`: Seçili ve aktif durumlarda tema gradyanını kullanır.
- **Renk Seçici Diyaloğunda Canlı Gradyan Önizlemesi:** `KitsugiAccentColorPickerDialog` içindeki "İki Renkli Gradyan" anahtarı, açı slider'ı, yön ön ayar butonları (→, ↘ vb.), sekme alt çizgileri ve "Uygula" butonu canlı düzenlenen gradyan fırçası ve kontrastıyla dinamik olarak güncellenir (`KitsugiAccentColorPickerDialog.kt`).
- **Gezinme Çubuğu ve Oynatıcı Kontrolleri:** `AppBottomBar` ve `AppNavigationRail` seçili sekme hapı, seçili ikon ve "Kitsugi" başlığı; `PlayerAccentTheme`, `AutoPlaySwitch` ve dikey kaydırıcılar tam gradyan desteğine kavuşturuldu (`AppBottomBar.kt`, `AutoPlaySwitch.kt`, `PlayerAccentTheme.kt`).
- **Teşhis & Entegrasyon Belgeleri:** `docs/PLAN_TASK_GRADYAN_VURGU_RENGI_TAM_ENTEGRASYON_2026-10-10.md` ve `docs/RENG_SECICI_GRADYAN_KONTRAST_RAPORU_2026-10-10.md`.

---

## 🇬🇧 English (v2.4.232)

### 🎨 1. Full Accent Gradient Integration Across the Entire App
- **Preserved Custom Accent & Gradient State:** Eliminated theme-override bug in `AppRoot.kt` where preset themes (`mint`) repeatedly overwrote `LocalKitsugiAccent`, ensuring custom two-color gradients persist across all phone and tablet layouts (`AppRoot.kt`).
- **Global Gradient Material Wrappers (`KitsugiGradientMaterial.kt`):** Bound all primary UI primitives to the active angled linear gradient brush (`LocalKitsugiAccentBrush`) and automatic smooth black/white contrast (`LocalKitsugiOnAccent`):
  - `Modifier.background` & `Modifier.border`: Implemented via non-composable `ModifierNodeElement` architecture to seamlessly render gradient backgrounds and borders whenever the color matches the active accent or muted accent.
  - `Text` & `Icon`: Renders text with gradient brush and icons with offscreen compositing; automatically switches to high-contrast `onAccent` when drawn inside accent surfaces.
  - `Switch`, `Slider`, `RangeSlider`, `RadioButton`, `Checkbox`, `CircularProgressIndicator`, `LinearProgressIndicator`, `TabRow`, `ScrollableTabRow`, `FilterChip`, `Button`, `FloatingActionButton`: Now fully support gradient active tracks, thumbs, chips, and indicators.
- **Live In-Dialog Gradient Preview:** The "Two-Color Gradient" switch, angle slider, direction preset buttons, tab indicators, and the "Apply" button inside `KitsugiAccentColorPickerDialog` now react in real time using the live editing gradient brush and contrast colors (`KitsugiAccentColorPickerDialog.kt`).
- **Navigation Bar & Video Player Polish:** Styled `AppBottomBar` and `AppNavigationRail` selected pill, icons, labels, and the Kitsugi title with the accent gradient; updated `PlayerAccentTheme`, `AutoPlaySwitch`, and player controls (`AppBottomBar.kt`, `AutoPlaySwitch.kt`, `PlayerAccentTheme.kt`).
- **Audit & Architecture Docs:** `docs/PLAN_TASK_GRADYAN_VURGU_RENGI_TAM_ENTEGRASYON_2026-10-10.md` and `docs/RENG_SECICI_GRADYAN_KONTRAST_RAPORU_2026-10-10.md`.

---

## 🇹🇷 Türkçe (v2.4.231)

### 🌸 1. Bangumi Kişi ve Karakter Detayları — Uygulama Dilinde Başlık ve Rol Yerelleştirmesi
- **Üç Kademeli Latin Başlık Çözümleyicisi (`BangumiSubjectTitleResolver`):** Bangumi kişi ve karakter listelerinin yalnızca Japonca/Çince isim döndürmesi nedeniyle "Yapımlar" ve "Karakterler" sekmelerinde başlıkların CJK kalması sorunu giderildi:
  1. `BangumiTitleCache` kalıcı önbelleği (daha önce çözülen başlıklar için sıfır ağ isteği).
  2. Bangumi v0 subject infobox (`英文名` / `罗马字` / `别名`).
  3. **AniList Aliased Toplu Arama:** Tek GraphQL isteğinde çoklu aliased sorgu ile `native`/`synonyms` alanlarında birebir normalize eşleşme doğrulaması; yanlış eşleşme reddedilir ve sonuçlar kalıcı önbelleğe işlenir (`BangumiSubjectTitleResolver.kt`).
- **Karakter ve Kadro Zenginleştirme Hatları:** Karakter detayı için `enrichCharacterDetailNames()` eklendi; `enrichStaffDetailNames()` ortak çözümleyiciye bağlandı. Senkron geçiş `cacheOnly` ile anında çizim sağlarken, ViewModel arka planda kalan başlıkları çözerek ekranı akıcı şekilde günceller (`KitsugiBangumiDetailClient.kt`, `CharacterDetailViewModel.kt`, `StaffDetailViewModel.kt`).
- **Arayüz Diline Duyarlı Rol ve Tür Sözlüğü (`BangumiRoleDictionary`):** Çince ve Japonca rol etiketleri (`主角`, `配角`, `客串`, `主題歌演出`, `原作`, `监督` vb.) uygulamanın diline (TR / EN) çevrilir. Veri katmanındaki sabit Türkçe etiketler kaldırılarak İngilizce arayüzde İngilizce ("Main Character", "Staff Member", "Male"), Türkçe arayüzde Türkçe gösterim sağlandı (`BangumiRoleDictionary.kt`, `KitsugiTranslations.kt`, `KitsugiBangumiCreditsClient.kt`).
- **Birim Testleri & Kararlılık:** `BangumiSubjectTitleResolverTest` ve `KitsugiTranslationsTest` testleri ile stableId/rawId dönüştürücüleri ve rol sözlüğü tam kapsamlı test edildi.

---

## 🇬🇧 English (v2.4.231)

### 🌸 1. Bangumi Staff & Character Details — Localized Titles and Roles
- **Three-Tier Latin Title Resolver (`BangumiSubjectTitleResolver`):** Fixed CJK (Japanese/Chinese) titles in the "Works" and "Characters" tabs of Bangumi person and character details:
  1. Persistent `BangumiTitleCache` (zero network calls for previously resolved items).
  2. Bangumi v0 subject infobox (`英文名` / `罗马字` / `别名`).
  3. **AniList Aliased Batch Search:** Executes batched GraphQL searches in a single request with strict normalized matching on `native` titles and synonyms; incorrect candidates are safely rejected and valid matches are stored in persistent cache (`BangumiSubjectTitleResolver.kt`).
- **Character & Staff Enrichment Pipelines:** Added `enrichCharacterDetailNames()` for character details and routed `enrichStaffDetailNames()` through the shared title resolver. Synchronous paths use `cacheOnly` for instant rendering, while ViewModels resolve background queues asynchronously (`KitsugiBangumiDetailClient.kt`, `CharacterDetailViewModel.kt`, `StaffDetailViewModel.kt`).
- **Locale-Aware Role & Type Dictionary (`BangumiRoleDictionary`):** Chinese/Japanese role and credit labels (`主角`, `配角`, `客串`, `主題歌演出`, `原作`, `监督`, etc.) are now translated according to the active app language (TR / EN). Hardcoded Turkish fallback strings in data models were eliminated in favor of clean English and Turkish labels (`BangumiRoleDictionary.kt`, `KitsugiTranslations.kt`, `KitsugiBangumiCreditsClient.kt`).
- **Unit Testing & Reliability:** Validated with comprehensive tests in `BangumiSubjectTitleResolverTest` and `KitsugiTranslationsTest` verifying ID mapping, alias alignment, and role localization.

---

## 🇹🇷 Türkçe (v2.4.230)

### ⚡ 1. Keşfet Kaynak Yükleme ve Arayüz Performansı İyileştirmeleri
- **AniList Çoklu Raf Toplu GraphQL Sorgusu (4× Hızlanma):** 14 rafın tek tek 700ms hız sınırlı kuyrukta ~11 saniye beklemesi yerine, 3 toplu GraphQL sorgusuyla paralel çekilmesi sağlandı (~2-3 saniyeye indi) (`AniListSearchClient.kt`).
- **MyAnimeList (MAL) Zaman Aşımı ve Raf Güvencesi:** 13 istek ve 350ms kuyruk gecikmesinde 5 saniyelik timeout kuyruktaki süreyi de tükettiğinden son rafların boş kalması engellendi; zaman aşımı 20 saniyeye çıkarılarak tüm rafların eksiksiz dolması sağlandı (`JikanApiClient.kt`).
- **Bangumi İstek Sınırı ve Önbellek Önceliği:** 14 raf için ~56 eşzamanlı istek atılarak bgm.tv tarafından 429/retry döngüsüne ve dakikalarca takılmaya yol açan yapı düzenlendi; paylaşılan 3 eşzamanlılık sınırı, kayıt tekilleştirmesi ve önbellek öncelikli mimari uygulandı (`KitsugiBangumiClient.kt`).
- **Asenkron Vitrin Arka Planı:** Kitsu, Shikimori, MAL ve Bangumi yüklenirken vitrin arka planı için atılan 5 TMDB aramasının sayfayı bloke etmesi engellendi; arka planda asenkron çözülüyor (`ExploreViewModel.kt`).
- **Takvim Sorgusu Tekilleştirmesi:** "Tümü" modunda aynı "Yakında Yayında" takvim sorgusunun 7 kez atılması yerine 90 saniyelik önbellek ve tekilleştirme sağlandı (`KitsugiAiringCalendarClient.kt`).
- **Kompozisyon ve Çizim Hızlandırması:** `allSourceSections()` 7 kaynak filtre ve string hesaplamaları her ekranda baştan hesaplanmak yerine `remember` ile önbelleğe alındı; geri sayım kartlarının saniyede bir tetiklenen 15 zamanlayıcısı 15 saniyelik aralığa optimize edildi (`ExploreComponents.kt`, `AllSourcesExploreContent.kt`).
- **Teşhis Raporu:** `KESFET_KAYNAK_YUKLEME_PERFORMANS_RAPORU_2026-10-10.md`.

---

## 🇬🇧 English (v2.4.230)

### ⚡ 1. Explore Source Loading & Rendering Performance Optimizations
- **AniList Batch GraphQL Queries (~4× Faster):** Replaced 14 sequential queries (which took ~11s over the 700ms rate-limited queue) with 3 batched GraphQL queries, reducing loading time to ~2-3s (`AniListSearchClient.kt`).
- **MyAnimeList (MAL) Shelf Reliability & Timeout Extension:** Increased request timeout from 5s to 20s so queued requests on the 350ms rate limiter don't prematurely time out, preventing missing or empty shelves (`JikanApiClient.kt`).
- **Bangumi Concurrency Throttling & Cache-First Strategy:** Eliminated severe 429/retry loops on bgm.tv caused by up to 56 concurrent detail requests across 14 shelves; added shared 3-concurrency limiting, request deduplication, and cache-first fetching (`KitsugiBangumiClient.kt`).
- **Non-blocking Hero Backdrop:** Decoupled 5 blocking TMDB searches from initial shelf loading in Kitsu, Shikimori, MAL, and Bangumi; hero images are now resolved asynchronously in the background (`ExploreViewModel.kt`).
- **Airing Calendar Deduplication:** Consolidated 7 redundant airing calendar queries in "All" mode into a single query with 90s in-memory caching (`KitsugiAiringCalendarClient.kt`).
- **UI Composition & Frame Drops Elimination:** Cached `allSourceSections()` via `remember` across 7 sources to stop expensive re-computations on every recomposition pass; lowered countdown chip timer ticks from 1s to 15s to eliminate 15 separate per-second render timers (`ExploreComponents.kt`, `AllSourcesExploreContent.kt`).
- **Diagnostic Report:** `KESFET_KAYNAK_YUKLEME_PERFORMANS_RAPORU_2026-10-10.md`.

---

## 🇹🇷 Türkçe (v2.4.229)

### 📖 1. Manga KeiSource & OkHttp 5 Uyumluluk Onarımı (72/77 TR Eklentisi Kurtarıldı)
- **3 Zorunlu KeiSource Interceptor'ı Eklendi:** Keiyoushi `KeiSource` tabanlı eklentilerin host istemcisinden sınıf adına göre şart koştuğu `UncaughtExceptionInterceptor`, `UserAgentInterceptor` ve `CloudflareInterceptor` sınıfları `NetworkHelper` varsayılan zincirine bağlandı. İsimsiz lambda veya sınıf uyuşmazlığı nedeniyle oluşan `IllegalStateException` ve arama/bölüm listesinin boş kalması sorunu giderildi (`NetworkHelper.kt`, `UncaughtExceptionInterceptor.kt`, `UserAgentInterceptor.kt`).
- **OkHttp 5.4 & Brotli/Zstd Uyumluluğu:** Keiyoushi lib 1.6 eklentilerinin talep ettiği `CompressionInterceptor(Brotli, Gzip, Zstd)` ve `zstd-kmp-okio` çağrıları için eksik Brotli / Zstd köprüleri bağlandı; `okhttp-android:5.3.2` ile ikili çakışmalar giderildi.
- **ProGuard Keep Kuralları:** R8 derlemesi sırasında interceptor sınıf adlarının ve OkHttp 5 köprülerinin korunması sağlandı (`proguard-rules.pro`).
- **Bölüm Listesi Hata Şeffaflığı:** `MihonSourceWrapper.fetchChapterList` içerisindeki hatalar artık sessizce yutulup boş liste döndürülmüyor; hata açıkça yüzeye çıkarılarak hatalı kaynak istatistikleri ve sahte "bölüm yok" durumları engellendi (`MihonSourceWrapper.kt`).
- **Kaynak Sağlık Sorgusu Çeşitlendirmesi:** `SourceHealthService` içinde sabit tek sorgu ("one piece") yerine dile göre 3 aday arama sorgusu tanımlandı (`SourceHealthService.kt`).
- **Uçtan Uca Doğrulama & Envanter Raporu:** 1431 eklenti, 2419 kaynak ve 93 Türkçe kaynağın incelendiği teknik rapor ve otomatik tarama script'i eklendi (`docs/audits/MANGA_KAYNAK_UCTAN_UCA_DOGRULAMA_2026-10-10.md`, `scripts/audit_manga_sources.py`).

### 🎬 2. Video Eklenti Kurtarma, Güvenli Arama & Doğru CF Tespiti
- **Otomatik Eklenti Devre Dışı Bırakma Kaldırıldı:** Tanı aracının ağ veya sağlayıcı hatası alan eklentileri veritabanında otomatik kapatarak akış aramasının tamamen boş dönmesine yol açan mekanizma kaldırıldı (`CsPluginDiagnosticRunner.kt`).
- **Tüm Eklentilerin Kapalı Olması Durumu:** Kurulu eklentilerin tamamı kapalıysa akış ekranında net bilgilendirme gösteriliyor; eklenti ayarlarına tüm devre dışı eklentileri tek dokunuşla geri açma seçeneği eklendi (`CloudstreamExtensionTab.kt`, `KitsugiStreamScreen.kt`).
- **Kesintisiz Arama (Failover Search):** Doğrudan seçilen bir eklentinin URL'sinden kaynak çıkmazsa arama orada kesilmiyor; aynı başlık, sezon ve bölüm bilgileriyle etkin tüm eklentilerde aramaya devam ediliyor (`StreamViewModel.kt`).
- **Daraltılmış ve Doğru Cloudflare Tespiti:** Arama sonucu boş olan her eklenti artık CF/WAF engeli sayılmıyor. Yalnızca doğrulanmış bot/WAF koruma imzası saptandığında "Doğrula" butonu gösteriliyor; genel 403/503, DNS veya zaman aşımı hataları ayrı sınıflandırılıyor (`CsProtectionClassifier.kt`).
- **Sunucu Cooldown Süreleri Korunuyor:** Tanı aracı artık yeniden deneme bekleme sürelerini zorla devre dışı bırakmıyor; sunuculara aşırı istek yüklenmesi önlendi.
- **Güvenli Sezon Seçimi:** Sezon aramasında başlıkla eşleşen sonuç bulunamazsa listedeki ilk ilgisiz adayın yüklenmesi engellendi (`CsStreamRunner.kt`).

### 🔐 3. Kullanıcı Hesap Güvenliği ve Veri Erişim Yetkileri
- **Kimliği Doğrulanmış Kullanıcı Veri Erişimi:** Supabase `user_data` tablosuna kimliği doğrulanmış kullanıcılar için CRUD yetkileri ve RLS izinleri tanımlandı (`20261010_user_data_authenticated_grants.sql`).
- **Yedekleme ve Vault CAS Güvenliği:** Hesap senkronizasyonunda Compare-and-Swap güvenlik kontrolleri ve kullanıcı dostu hata mesajı biçimlendiricisi eklendi (`AccountErrorFormatter.kt`, `KitsugiAccountContent.kt`).

---

## 🇬🇧 English (v2.4.229)

### 📖 1. Manga KeiSource & OkHttp 5 Compatibility Fixes (Unblocked 72/77 TR Extensions)
- **Added 3 Required KeiSource Interceptors:** Keiyoushi `KeiSource` extensions enforce strict class-name checks on the host client for `UncaughtExceptionInterceptor`, `UserAgentInterceptor`, and `CloudflareInterceptor`. All three are now properly provided by `NetworkHelper`, eliminating `IllegalStateException` crashes that caused silent empty search and chapter results (`NetworkHelper.kt`, `UncaughtExceptionInterceptor.kt`, `UserAgentInterceptor.kt`).
- **OkHttp 5.4 & Brotli/Zstd Compatibility:** Provided bridge stubs for `CompressionInterceptor(Brotli, Gzip, Zstd)` and `zstd-kmp-okio` APIs expected by extensionLib 1.6; resolved duplicate binary symbols against `okhttp-android:5.3.2`.
- **ProGuard Keep Rules:** Added ProGuard keep entries ensuring interceptor class names and OkHttp 5 bridge classes are preserved during R8 optimization (`proguard-rules.pro`).
- **Chapter Fetch Error Transparency:** `MihonSourceWrapper.fetchChapterList` no longer swallows exceptions into an empty list, ensuring genuine error surfacing and accurate source health metrics (`MihonSourceWrapper.kt`).
- **Localized Health Check Queries:** Enhanced `SourceHealthService` with language-specific candidate query pools instead of a hardcoded single query (`SourceHealthService.kt`).
- **End-to-End Audit & Inventory Report:** Added comprehensive analysis and audit tooling covering 1,431 extensions, 2,419 sources, and 93 Turkish sources (`docs/audits/MANGA_KAYNAK_UCTAN_UCA_DOGRULAMA_2026-10-10.md`, `scripts/audit_manga_sources.py`).

### 🎬 2. Video Plugin Source Recovery & Accurate Protection Classification
- **Removed Auto-Disabling of Plugins:** Diagnostic test runs no longer auto-disable plugins upon network or provider errors, which previously caused all stream sources to go silent (`CsPluginDiagnosticRunner.kt`).
- **Disabled Plugins Safeguard:** Stream screen clearly notifies when all installed plugins are disabled; added a one-tap action in extension settings to re-enable all installed providers (`CloudstreamExtensionTab.kt`, `KitsugiStreamScreen.kt`).
- **Failover Search Across Active Plugins:** If a directly selected plugin URL returns no streams, search continues across all active plugins for the matching title, season, and episode (`StreamViewModel.kt`).
- **Narrowed Cloudflare Classification:** Empty search results are no longer misclassified as Cloudflare/WAF blocks. Verification triggers only on verified bot/WAF challenge signatures; generic 403/503, DNS, and timeouts are reported accurately (`CsProtectionClassifier.kt`).
- **Respected Server Cooldowns:** Diagnostic tool no longer overrides cooldown timers (`ignoreCooldowns = false`), preventing server-side bans.
- **Safe Season Matching:** Prevents loading arbitrary non-matching candidates when season title matching yields no hits (`CsStreamRunner.kt`).

### 🔐 3. Authenticated User Data Access & Account Vault Security
- **User Data Table Permissions:** Added explicit CRUD grants and RLS policies for authenticated users on the `user_data` table (`20261010_user_data_authenticated_grants.sql`).
- **Account Backup Vault CAS:** Integrated compare-and-swap checks and user-friendly error formatting for account sync and backup (`AccountErrorFormatter.kt`, `KitsugiAccountContent.kt`).

---

## 🇹🇷 Türkçe (v2.4.228)

### ⚡ 1. Keşfet & Arama Sayfaları Performans İyileştirmeleri
- **Scroll Geri Besleme Döngüsü Çözüldü (Keşfet & Listem):** Keşfet ve Listem ekranlarında kaydırma yapılırken her pikselde `AppViewModel` üzerindeki durum değişkenlerine (`mutableIntStateOf`) yazılması tüm sekmenin baştan aşağı recomposition'a girmesine ve 100+ öğelik listenin her karede yeniden kurulmasına yol açıyordu. Pozisyonlar düz değişkenlere (`var exploreScrollIndex: Int`) dönüştürüldü; sekmeden ayrılıp geri dönüldüğünde scroll konumu korunurken kaydırma esnasındaki kasma ve takılmalar tamamen ortadan kaldırıldı (`AppViewModel.kt`, `ExploreScreen.kt`).
- **Sonsuz Shimmer Animasyonu Optimizasyonu:** `KitsugiShimmerProvider` bileşenine `active` kontrolü eklendi. Sayfada veri yüklemesi tamamlandığında ve içerik geldiğinde sonsuz animasyon saati (`InfiniteTransition`) tamamen kapatılıyor; statik fırça sağlanarak boşta dururken bile arka planda çalışan ve CPU/pil tüketen gereksiz recomposition döngüleri durduruldu (`KitsugiShimmer.kt`, `ExploreScreen.kt`, `TvAllSourcesHomeContent.kt`).
- **Arama Sayfası Shimmer & Filtreleme Hızlandırması:**
  - "Tümü" aramasında 7 platform aynı anda sorgulanırken her platform rafının kendi sonsuz shimmer animasyonunu kurması yerine sayfa geneli tek paylaşımlı `KitsugiShimmerProvider(active = anySearchLoading)` bağlandı.
  - `filteredResults` (yetişkin içerik ve listemdekiler süzgeci) LazyColumn her çizildiğinde yüzlerce sonucu baştan filtrelemek yerine `remember(...)` ile önbelleğe alındı (`SearchScreen.kt`).
- **Teşhis Raporu:** Keşfet performans analizi ve optimizasyon detayları belgelendi (`KESFET_PERFORMANS_FIX_RAPORU_2026-10-10.md`).

### 📖 2. Manga Hattı Düzeltmeleri & Kompakt Arayüz İyileştirmeleri
- **Kompakt "Oku" Butonu:** Detay sayfasındaki "Oku" butonu, "İzle" butonu gibi kompakt yapıya kavuşturuldu; ekranın tüm satırını kaplaması engellendi (`ApiDetailLeftPanel.kt`, `KitsugiDetailInfoSection.kt`).
- **Siyah Okuyucu Ekranı Giderildi:** Görsel hazır olduğunda ekranın bunu anında gözlemleyebilmesi sağlandı; durum güncelleme (state observation) gecikmelerinden kaynaklanan siyah ekran sorunu çözüldü (`MangaReaderViewModel.kt`, `MangaBrowseViewModel.kt`, `MangaBrowseScreen.kt`).
- **Mihon Görsel İndirme Hattı:** Mihon görselleri artık eklentinin kendi resim isteği (image request), HTTP istemcisi ve oturum çerezleriyle doğrudan indiriliyor (`MihonSourceWrapper.kt`).
- **Kotatsu Önizleme ve Hata İyileştirmeleri:** Kotatsu parser'larında önizleme görsellerinin gerçek sayfa sanılması engellendi, kaybolan bölüm metadataları, bölüm sıralaması ve arka planda gizlenen hatalar giderildi (`KotatsuExtensionAdapter.kt`, `MangaModels.kt`).
- **Doğrudan Kaynak Eşleşmesi:** Manga arama sonuçları artık doğrudan kendi kaynak kimliğiyle açılıyor; aynı isimdeki eklentiler arasında hatalı tahmin ve yanlış kaynak ataması yapılmıyor (`MangaSourceRepository.kt`).
- **HTML Temizliği ve Önbellek Koruması:** Bozuk veya HTML hata yanıtlarının resim olarak önbelleğe alınması engellendi; manga açıklamalarındaki ham HTML etiketleri (`<p>`, `<b>`, `&nbsp;` vb.) arayüzde temizlendi (`MangaCache.kt`, `MangaPageLoaderV2.kt`).
- **121 Türkçe Parser Denetimi & Envanteri:** 121 Türkçe parser statik envantere çıkarıldı ve durum raporu belgelendi (`docs/MANGA_PIPELINE_AUDIT_2026-10-10.md`, `docs/MANGA_TR_PARSER_INVENTORY.md`, `scripts/audit_manga_parsers.py`).
- **Test Kapsamı:** Manga sayfa durumu ve önbellek mekanizmaları için birim testleri entegre edildi (`MangaPageStateTest.kt`, `MangaCacheTest.kt`).

### 🌐 3. Kapsamlı Dil Altyapısı (i18n) Genişletmesi & 3.745 Çift Dilli Kaynak (`strings.xml`, `values-en/strings.xml`, `values-en/arrays.xml`)
- **3.745 Anahtarlık %100 TR / EN Simetrisi:** Kaynak sayısı 2.195'ten 3.745 anahtara çıkarıldı; Türkçe ve İngilizce dil kaynakları arasında birebir anahtar, sıra ve `%1$s` biçim yer tutucusu (placeholder) eşitliği sağlandı.
- **AAPT XML Kaçış & Standardizasyon Onarımları:**
  - 665 adet geçersiz `xml:space="preserve"` attribute'u temizlendi (Android AAPT derleyicisinin `CantBindXML` hatası çözüldü).
  - Metin içi kaçışsız apostroflar (`zoom'u`, `provider's` vb.) Android standartlarına uygun `\'` haline getirildi.
  - Yüzde karakterleri içeren metinler için `formatted="false"` etiketleri düzenlendi.
  - Bangumi etiket senkronizasyon sözlüğü (`sync_bangumi_tag_strings.py`) bozulmadan korundu.

### 📱 4. 14 Yeni Ekran & Diyalog Kaynaklara Bağlandı
- **Sistem ve İndirme Ayarları (`KitsugiSystemSettingsDialog.kt`):**
  - İndirme hız limitleri ("Sınırsız", "50 KB/s", "10 MB/s" vb.), eşzamanlı indirme sayıları.
  - Okuma sonrası otomatik silme ("Devre Dışı", "Son okunan bölüm", "Son 1-10 bölüm").
  - Okurken/İzlerken otomatik ön indirme ("Sonraki 1-10 bölüm / video").
  - Kategori filtreleme & hariç tutma diyalogları ("CURRENT", "PLANNING", "COMPLETED", "ON_HOLD", "DROPPED").
  - DNS sağlayıcıları (Sistem Varsayılanı, Google, Cloudflare, AdGuard, DNS.WATCH, Quad9, DNS.SB, Canadian Shield açıklamaları).
  - Harici indirici tercihleri (Dahili, 1DM, ADM algılama ve durum metinleri).
- **Gelişmiş Arama Filtreleme (`SourceEngineFilterSheet.kt`):** Arama motoru, kaynak filtreleri ve sıralama seçeneklerinde 49 metin yerelleştirildi.
- **Akış ve Video Sayfası (`StreamScreenContent.kt`):** Akış kaynakları, oynatıcı seçimi, hata durumları ve çözünürlük etiketlerinde 24 metin dinamik kaynaklara bağlandı.
- **Eklenti ve Geliştirici Günlükleri (`CsPluginDiagnosticScreen.kt`, `DeveloperLogsDialog.kt`):** Eklenti tanılama raporları, test adımları, log filtreleme ve panoya kopyalama butonları yerelleştirildi.
- **Geri Bildirim (`FeedbackDialog.kt`):** E-posta gönderimi, şablon metinleri ve hata uyarıları.
- **Android TV Ekranları:**
  - TV Aktivite Detayı (`TvActivityDetailDialog.kt`), İnceleme Detayı (`TvReviewDetailDialog.kt`), Manga Eklentileri (`TvMangaExtensionScreen.kt`), QR Giriş Ekranı (`TvQrLoginScreen.kt`), TV Akış Ekranı (`TvStreamScreen.kt`), TV Eklenti Yöneticisi (`TvAddonsScreen.kt`), TV Yardımcı Eşleşme Onayı (`TvCompanionApprovalDialog.kt`) ve QR Kod Ekranı (`TvCompanionQrScreen.kt`).

### 🛠️ 5. Yeni Yerelleştirme Denetim Araçları (`scripts/`)
- **`scripts/audit_hardcoded_turkish.py`:** Kod tabanında kalan Türkçe karakterli hardcoded literal metinleri tarayıp dosya bazında raporlayan denetim aracı eklendi.
- **`scripts/check_localization_resource_parity.py`:** `values` ve `values-en` arasındaki string ve string-array anahtarlarını, format yer tutucularını (`%1$s`) otomatik doğrulayan CI aracı entegre edildi.

---

## 🇬🇧 English (v2.4.228)

### ⚡ 1. Explore & Search Performance Optimizations
- **Resolved Scroll Recomposition Loop (Explore & My List):** Eliminated the feedback loop where updating `mutableIntStateOf` during scrolling triggered full recompositions of the entire tab and rebuilt 100+ items per frame. Replaced scroll positions with plain Kotlin properties (`var exploreScrollIndex: Int`), preserving scroll state upon navigation while delivering stutter-free scrolling (`AppViewModel.kt`, `ExploreScreen.kt`).
- **Infinite Shimmer Animation Optimization:** Added an `active` control parameter to `KitsugiShimmerProvider`. Once content is loaded and placeholders are replaced, the infinite transition clock is completely disabled, substituting a static gradient brush and eliminating wasteful CPU/battery drain (`KitsugiShimmer.kt`, `ExploreScreen.kt`, `TvAllSourcesHomeContent.kt`).
- **Search Screen Shimmer & Filter Caching:**
  - Consolidated multi-platform shimmer instances: wrapped search results with a single shared `KitsugiShimmerProvider(active = anySearchLoading)` across all 7 platform shelves instead of 8 separate infinite transitions.
  - Cached `filteredResults` (adult filter and "in my list" filter) using `remember(...)` to avoid recalculating hundreds of items on every layout pass (`SearchScreen.kt`).
- **Diagnostic Report:** Documented comprehensive analysis and profiling findings (`KESFET_PERFORMANS_FIX_RAPORU_2026-10-10.md`).

### 📖 2. Manga Pipeline Fixes & Compact UI Enhancements
- **Compact "Read" Button:** The "Read" action button on the anime/manga detail screen is now compact like the "Watch" button instead of spanning the entire line (`ApiDetailLeftPanel.kt`, `KitsugiDetailInfoSection.kt`).
- **Resolved Black Reader Screen:** Fixed state update / observation race condition where page states failed to trigger UI rendering upon image readiness (`MangaReaderViewModel.kt`, `MangaBrowseViewModel.kt`, `MangaBrowseScreen.kt`).
- **Mihon Image Pipeline:** Manga page images from Mihon extensions are now fetched using the extension's dedicated image requests, HTTP client, and session headers (`MihonSourceWrapper.kt`).
- **Kotatsu Improvements:** Prevented preview thumbnails from being mistaken as chapter pages, restored missing metadata, fixed chapter ordering, and surfaced previously suppressed error details (`KotatsuExtensionAdapter.kt`, `MangaModels.kt`).
- **Direct Source Mapping:** Manga search results now open strictly with their originating source provider, preventing ambiguous name-based guesses (`MangaSourceRepository.kt`).
- **HTML Stripping & Cache Protection:** Blocked corrupted or HTML error responses from being cached as images; stripped raw HTML tags (`<p>`, `<b>`, `&nbsp;` vb.) from manga synopsis text (`MangaCache.kt`, `MangaPageLoaderV2.kt`).
- **121 Turkish Parser Inventory:** Conducted a comprehensive audit and static inventory of all 121 Turkish manga parsers (`docs/MANGA_PIPELINE_AUDIT_2026-10-10.md`, `docs/MANGA_TR_PARSER_INVENTORY.md`, `scripts/audit_manga_parsers.py`).
- **Unit & Regression Testing:** Added tests for manga page states and cache integrity (`MangaPageStateTest.kt`, `MangaCacheTest.kt`).

### 🌐 3. Comprehensive i18n Expansion & 3,745 Bilingual Resources (`strings.xml`, `values-en/strings.xml`, `values-en/arrays.xml`)
- **Full 100% TR / EN Symmetry with 3,745 Keys:** Resource collection expanded from 2,195 to 3,745 keys, maintaining perfect 1-to-1 key parity and matching `%1$s` positional format tokens across English and Turkish.
- **AAPT XML Escape & Standardization Fixes:**
  - Stripped 665 invalid `xml:space="preserve"` attributes to resolve AAPT compiler XMLStreamException (`CantBindXML`).
  - Escaped raw single quotes (`\'`) for proper Android string compilation (`zoom\'u`, `provider\'s`).
  - Applied `formatted="false"` attributes to strings containing unescaped percentage symbols.
  - Fully maintained Bangumi dictionary generator parity (`sync_bangumi_tag_strings.py --check`).

### 📱 4. Migration of 14 UI Screens & Dialogs to Localized Resources
- **System & Download Settings (`KitsugiSystemSettingsDialog.kt`):**
  - Download speed limits ("Unlimited", "50 KB/s", "10 MB/s", etc.) and concurrent download counts.
  - Auto-removal options ("Disabled", "Last read chapter", "Last 1-10 chapters").
  - Pre-download while watching/reading options.
  - Category inclusion/exclusion dialogs ("Current", "Planning", "Completed", "On Hold", "Dropped").
  - DoH DNS providers and descriptions (System Default, Google, Cloudflare, AdGuard, DNS.WATCH, Quad9, DNS.SB, Canadian Shield).
  - External downloader preferences (Internal, 1DM, ADM detection).
- **Search Engine Filters (`SourceEngineFilterSheet.kt`):** 49 string resources wired for source provider and filter settings.
- **Streaming Screen (`StreamScreenContent.kt`):** 24 string resources wired for stream lists, server states, and resolution tags.
- **Diagnostics & Logs (`CsPluginDiagnosticScreen.kt`, `DeveloperLogsDialog.kt`):** Diagnostic test suites, log filtering, and clipboard actions.
- **Feedback Dialog (`FeedbackDialog.kt`):** Feedback form, email triggers, and templates.
- **Android TV Screens:**
  - TV Activity Detail (`TvActivityDetailDialog.kt`), Review Detail (`TvReviewDetailDialog.kt`), Manga Extensions (`TvMangaExtensionScreen.kt`), TV QR Login (`TvQrLoginScreen.kt`), TV Stream (`TvStreamScreen.kt`), TV Addons (`TvAddonsScreen.kt`), Companion Approval (`TvCompanionApprovalDialog.kt`), and Companion QR (`TvCompanionQrScreen.kt`).

### 🛠️ 5. Localization Verification & Audit Tools (`scripts/`)
- **`scripts/audit_hardcoded_turkish.py`:** Automated scanner identifying remaining hardcoded strings across Kotlin files.
- **`scripts/check_localization_resource_parity.py`:** Automated validator verifying structural, key, and format token parity between locale resource files.

---

## 🇹🇷 Türkçe (v2.4.227)

### 🎯 1. Kaynak Bazlı Arama & Bölüm Mekaniği: Film ve Dizi için Ayrı Boru Hatları (`CsStreamRunner.kt`, `CsTitleMatcher.kt`)
- **Temel İlke:** Tüm kaynaklarda içerik araması artık YALNIZCA çıplak eser adıyla yapılır — sezon/bölüm bilgisi arama sorgusuna ASLA karışmaz. Hiçbir site (Türkçe/yabancı) "X 1. Sezon 1. Bölüm" sorgusunu çözemez; doğru akış oturtuldu: başlık ara → içerik sayfasına gir → (dizi ise) sezon+bölüm sayfasına in → video verilerini çek.
- **Film Mekaniği (ayrı boru hattı):** Filmler sezon mantığıyla depolanmaz; eşleşen içerik sayfası doğrudan video sayfası olarak kullanılır (dataUrl/url → loadLinks), bölüm eşleştirme yapılmaz.
- **Dizi/Anime Mekaniği (ayrı boru hattı):** Yüklenen sayfanın bölüm listesinden hedef S+E seçilir; eklenti bölüm listesi doldurmuyorsa (sayfa-tipli plugin) sayfa URL'si bölüm kapsayıcısı olarak kullanılır.
- **Sezon Sayfası Gezinmesi:** Bölüm listesi dolu ama hedef sezon sayfada yoksa site sezonu ayrı tutuyordur; eklentinin KENDİ search+load akışıyla sezon sayfasına inilir — kaynak-bağımsız, tüm eklentilerde ortak genel adım.
- **Eski Hata Düzeltildi:** Bölüm listesi dolu olup eşleşme yoksa loadLinks'e DİZİ sayfası URL'si veriliyordu — 25 sn'lik link çıkarma bütçesi çöpe gidiyor, kart "akış bulunamadı" diyordu. Artık gerçek sebep tracker'a yazılır ve sezon gezinmesi denenir.
- **Sezon Girdisi Araması (güvenlik ağı):** Sezon ekli sorgular ("X 2. Sezon") asla birincil arama değildir; yalnızca çıplak başlık araması sıfır sonuç döndürürse (sezonları ayrı indeksleyen siteler için) denenir.
- **Sayfa Tipine Göre Boru Hattı:** Yanlış sınıflandırma durumunda (film bayrağıyla gelip kaynak "tek bölüm" dizi girdisi döndürürse — Türkçe sitelerde çok yaygın) boru hattı yüklenen sayfanın gerçek tipine göre seçilir; mekanik bayrağa değil kaynağa bakar.

### 🛡️ 2. Cloudflare WebView Bellek Sızıntısı ve Çökme Riski (`CloudflareInterceptor.kt`, `KitsugiApplication.kt`, `CsCfWarmupManager.kt`)
- **Başarılı Challenge Sonrası WebView Sızıntısı Giderildi:** `cf_clearance` bulunduğunda bekleme sayacı tamamlanıyor, fakat eski timeout kolu yalnızca sayaç hâlâ açıkken `destroy()` çağırıyordu. Başarılı çözüm yolunda WebView açık kalıyor; sonraki challenge'larda native Chromium/RenderThread kaynaklarının birikmesine yol açabiliyordu. Başarı, zaman aşımı ve iptal artık ana thread'de tek seferlik temizliğe gidiyor.
- **Başlangıçta Toplu WebView Açılması Kaldırıldı:** Açılışta 14 adede kadar gizli WebView ile Cloudflare warmup yapılmıyor. Challenge çözümü yalnızca ihtiyaç duyulan host için tembel (lazy) başlatılıyor.
- **Güvenli Teardown:** WebView yok edilmeden hemen önce `about:blank` yüklenmesi kaldırıldı; view hiyerarşisinden sökme, yükü durdurma, client referanslarını kesme ve tek `destroy()` sırası kullanılıyor.

### 🌐 3. Kaynak Bazlı Liste Eşleştirme & Yetişkin İçerik Bulanıklığı (`KitsugiModels.kt`, `AppRoot.kt`, `AppRootDetailPages.kt`)
- **Kaynak Bazlı Ayrım (`matchesInSource`, `firstMatchingInSource`):** Liste aidiyeti ve detay sayfası kontrolleri artık platform kaynağına duyarlıdır. Bangumi (`bgm`, `bgm.tv`) kayıtları Bangumi düzenleyicisine yönlendirilir; Simkl veya AniList eşleşmeleri Bangumi öğelerinin listede görünmesine sebep olmaz.
- **Profil Favorilerinde Başa Dön Butonu:** Profil favorilerindeki "başa dön" butonu, kaydırma öncesinde ve sonrasında paylaşılan alt çubuk görünürlüğünü sıfırlar.
- **Yetişkin İçerik Bulanıklığı Taşıması:** AniList profil favorilerindeki yetişkin medya durumu detay navigasyonuna taşınır; sinematik yükleme posterleri ilk kareden itibaren bulanıklık ayarına uyar.

### 🏷️ 4. Ortak Küresel Etiket ve Tür Çevirileri (`BangumiTagDictionary.kt`, `SearchTranslation.kt`, `GenreLabelFormatter.kt`, `KitsugiTranslations.kt`)
- **Tüm Kaynaklarda Birlik:** AniList, MAL, Kitsu, Shikimori ve Bangumi için etiket ve tür çevirileri tek ortak BangumiTagDictionary ve SearchTranslation tablosu üzerinden hizalandı.
- **Çevrilen ve İyileştirilen Etiketler:** Heteroseksüel, Erkek/Kadın Baş Karakter, Ters Harem, Ağırlıklı Kadro etiketleri, Klon/Klonlar, Uzaylılar, Poliamori, Kuudere, Çıplaklık, Ansambl Kadro, Fantastik, Günlük Yaşam, Romantik, Isekai, Cyberpunk, Kıyamet Sonrası, Orta Çağ ve diğer tüm jargonsal karşılıklar standartlaştırıldı.

### 🗑️ 5. Görsel Galerisinde Üçüncü Buton: Sil (`KitsugiImageGalleryDialog.kt`, `KitsugiImageDownloadHelper.kt`, `DownloadsScreen.kt`)
- **Silme Butonu Eklendi:** Galeri eylem sırası artık **İndir → Paylaş → Sil → Kapat** şeklindedir; kardeşleriyle aynı cam kutu dilinde kırmızı uyarı tonuyla çizilir ve Android TV kumandalarında odaklanabilir.
- **Dikey ve Yatay Eşitliği:** Dikey başlıkta 40.dp, yatay yan panelde 36.dp — boyut/köşe parametreleriyle yönetilen tek `KitsugiGalleryDeleteButton` bileşeni kullanılır.
- **Gerçek Silme:** MediaStore kaydını (`content://`), kullanıcının seçtiği SAF klasöründeki dosyayı ve `Download/Kitsugi/Images` altındaki kopyaları kaldırır; ardından harici galerilerin ölü kareler göstermemesi için `MediaScanner`'ı bilgilendirir.
- **İndirilenler Ekranı Entegrasyonu:** İndirilen resimler galerisinden silme işlemi kareyi anında kaldırır ve listeyi yeniden tarar; yeni grup düzeyinde silme butonu bir başlığın indirilen tüm resimlerini tek işlemde temizler.

### 🖼️ 6. GIF ve Çoklu Format Desteği (`KitsugiImageDownloadHelper.kt`)
- **Yerel Kaynaklar Paylaşılabilir:** `file://` ve `content://` öğeleri doğrudan FileProvider üzerinden iletilir; MediaStore/SAF satırları çözücü üzerinden okunur.
- **Genişletilmiş Format Tespiti:** GIF87a/89a, PNG + APNG, JPEG/MPO, WebP (VP8X), AVIF/AVIS, HEIF/HEIC, BMP ve TIFF formatları tanınır ve hareketli içerikler bozulmadan saklanır.

### 🔞 7. +18 Blur Zorunluluğu Tüm Kaynaklarda (`AdultFlagBackfillMigration.kt`, `ShikimoriAdultResolver.kt`, `TmdbAdultResolver.kt`, `MediaEntryRepository.kt`, `KitsugiApplication.kt`, `AiringNotificationWorker.kt`)
- **Simkl Listesi Onarımı:** Simkl senkronizasyonunun eksik bıraktığı yetişkin bayrağı kanonik kimlik üzerinden `AdultFlagBackfillMigration` ile tamamlanır.
- **Kaynaklar Arası +18 Yayılımı:** `MediaEntryRepository.entriesFlow` satırları `MediaIdentity.keys()` üzerinden eşleştirir; aynı yapımın AniList satırı +18 işaretliyse Simkl/Shikimori/"Tümü" sekmesindeki satırı da bulanıklaşır.
- **İşaretler Asla Silinmez:** `replaceSourcePreservingAdultFlags()` sayesinde otomatik senkron ve bildirim işçisi işaretli bayrakları korur.

### 🧪 8. Testler
- `ImageFormatDetectionTest`, `AdultFlagBackfillMigrationTest`, `MediaEntryRepositoryAdultPropagationTest`.

### 🎨 9. Tam Renk Seçici, Gradyan Vurgu Mekaniği & Otomatik Siyah-Beyaz Kontrast (`KitsugiAccentColorPickerDialog.kt`, `KitsugiAccentSupport.kt`, `Theme.kt`, `KitsugiColors.kt`, `AppSettings.kt`, `SettingsDataStore.kt`)
- **"+" Butonu Tam Panel Açıyor:** Eski "hex kodu girin" dialoğu kaldırıldı; yerine 3 sekmeli zengin panel geldi:
  - **Palet:** 19 renk ailesi × 10 ton (50→900) + siyah/beyaz = **192 renk**.
  - **Özel:** Tam **HSV doygunluk–değer karesi + ton şeridi** (sınırsız renk, isteğe bağlı hex alanı).
  - **Gradyan:** İki renkli açılı lineer gradyan: açı slider'ı + 8 yön ön ayarı (→ ↘ ↓ ↙ ← ↖ ↑ ↗), canlı şerit önizleme, başlangıç/bitiş renk seçimi.
- **Kalıcı Gradyan Mekaniği:** `customAccentColor2` (bitiş rengi) ve `customAccentGradientAngle` (açı) kalıcı olarak saklanır; tüm vurgu zeminlerinde iki renk arasında seçilen açıyla lineer akış uygulanır.
- **Otomatik Siyah ↔ Beyaz Kontrast (`onAccentColor`):** Vurgu zeminindeki TÜM yazı ve ikonlar, seçilen rengin koyuluğuna göre yumuşak (smoothstep) geçişle otomatik siyah veya beyaz kontrast alır (ayar ikonları, aksiyon butonları, oynatıcı overlayleri, FAB'lar vb.).

### 🔄 10. Bangumi Çapraz Eşitleme (Cross-Sync) Tam Entegrasyonu (`AuthViewModel.kt`, `BangumiSyncManager.kt`, `PlatformRateLimiter.kt`, `CrossSyncDisclaimer.kt`)
- **6/6 Platform Desteği:** AniList, MyAnimeList, Simkl, Kitsu, Shikimori ve Bangumi artık tam çapraz senkronizasyon havuzunda birlikte çalışır.
- **Hız Sınırı & Aday İndeksi:** Bangumi API için 400ms hız sınırı tanımlandı; ad eşleştirme ve çoklu başlık aramasında Japonca/orijinal ad desteği eklendi.
- **Akıllı İçe Aktarma:** Senkronizasyon sonrası Bangumi verisi `smartImport` ile yerel veritabanıyla doğrulanır.

### 🈯 11. TMDB/Simkl Kadro Adlarında Yanlış Alfabe Düzeltmesi & Hayali Karakter Görsel Zinciri (`PreferenceHelpers.kt`, `TmdbCreditsClient.kt`, `KitsugiCharacterClient.kt`)
- **Yanlış Alfabe Kökten Çözüldü:** `pickLatinAlias()` artık CJK-olmayan her alfabeyi Latin sanmak yerine `PreferenceHelpers.isLatinText()` ile harflerin gerçekten Latin (Türkçe harfler dahil) olduğunu doğrular; Arapça veya Tayca çeviriler elenerek doğru Latin/Romaji adlar seçilir.
- **Çapraz Kaynak Görsel Zinciri:** TMDB/Simkl animasyon içeriklerinde AniList'in görsel bulamadığı karakterler için sırasıyla MAL/Jikan → Shikimori → Kitsu kaynaklarından doğrulanmış gerçek MAL ID ile görsel çekilir.

### 🏢 12. Stüdyo & Yapımcı Yapım Listesi Sonsuz Sayfalama & Limit Düzeltmesi (`KitsugiStudioClient.kt`, `JikanGateway.kt`, `StudioDetailViewModel.kt`, `StudioDetailPage.kt`, `KitsugiModels.kt`)
- **Tenrai 50 İstek Limiti Koruması:** Tenrai `limit > 50` isteklerinde 400 hatası döndürüyordu; `TENRAI_MAX_LIMIT = 50` ile `JikanGateway.tenraiRest` içinde otomatik sınırlandırılarak yapım listesinin boş gelmesi önlendi.
- **Tüm Kaynaklarda Sayfalama Desteği:** 50'den fazla yapımı olan stüdyolar için MAL/Jikan (`has_next_page`), Shikimori (`page` ve `limit=50`), AniList (`pageInfo.hasNextPage`), TMDB (`movie` ve `tv` discover için `total_pages`) sayfalama desteği entegre edildi.
- **Akıcı Sonsuz Kaydırma & Hata Kurtarma:** Liste ve grid görünümünde sona yaklaşıldığında sonraki sayfa otomatik çekilir; yükleme anında alt ortada minimal yükleme çemberi, ağ hatasında "Devamı yüklenemedi · Tekrar dene" butonu sunulur.

### 🎬 13. Fragman (Ön İzleme) Satır Kalıcılığı & Oynatıcı Kontrolü (`KitsugiDetailThemesTrailerComponents.kt`)
- **Kalıcı Satır Kartı:** Ön İzleme kartında oynatıcı açıldığında "Fragmanı İzle" satırının kaybolması sorunu giderildi; açılış/kapanış müziklerindeki gibi oynatıcının üzerinde görünür kalır.
- **Aktif Durum & Toggle:** Oynatıcı açıkken daire içinde yukarı ok (chevron) ikonu, vurgulu arka plan ve renk gösterilir; satıra tekrar dokunulduğunda oynatıcı kapanır (veya altındaki "Kapat" düğmesi kullanılabilir).
- **Tüm Detay Sayfalarında Geçerli:** Ortak `KitsugiTrailerCard` bileşeni üzerinden hem kütüphaneden açılan medya detayında hem de arama/API detay sayfalarında (anime ve manga) aktif.

---

## 🇬🇧 English (v2.4.227)

### 🎯 1. Per-Source Search & Episode Mechanics: Separate Pipelines for Movies vs Series (`CsStreamRunner.kt`, `CsTitleMatcher.kt`)
- **Core principle:** Content search across ALL sources now uses ONLY the bare work title — season/episode info never enters the search query. No site (Turkish or foreign) can resolve "X Season 1 Episode 1"; the correct flow is established: search title → enter content page → (series) descend to the season+episode page → extract video data.
- **Movie pipeline (separate):** Movies carry no season logic; the matched content page is used directly as the video page (dataUrl/url → loadLinks), with no episode matching.
- **Series/anime pipeline (separate):** The target S/E is picked from the loaded page's episode list; when a plugin fills no episode list (page-style plugin), the page URL is used as the episode container.
- **Season page navigation:** When the episode list is populated but the target season is absent, the site keeps seasons on separate entries; the plugin's OWN search+load flow navigates to the season page — a generic, source-agnostic step applied for every addon.
- **Old bug fixed:** When episodes existed but none matched, the SERIES page URL was fed to loadLinks — the 25s link-extraction budget was wasted and the card showed "no streams found". The real reason is now recorded and season navigation is attempted instead.
- **Season-entry search (safety net):** Season-suffixed queries ("X Season 2") are never primary searches; they run only when the bare-title search returns zero results (sites that index seasons as separate entries).
- **Page-type-driven pipeline:** On misclassification (movie flag but the source returns a "single episode" series entry — very common on Turkish sites), the pipeline is chosen by the loaded page's real type; the mechanics follow the source, not the flag.

### 🛡️ 2. Cloudflare WebView Leak & Crash-Risk Fix (`CloudflareInterceptor.kt`, `KitsugiApplication.kt`, `CsCfWarmupManager.kt`)
- **WebView Leak After Successful Challenges:** The old timeout destroyed the WebView only while the latch was still pending. Once `cf_clearance` succeeded, the latch completed and the timeout skipped destruction, potentially allowing Chromium/RenderThread resources to accumulate. Success, timeout, and cancellation now share one main-thread, exactly-once teardown.
- **No Batch of Startup WebViews:** Startup no longer launches up to 14 hidden WebViews to pre-warm Cloudflare cookies. Challenges are solved lazily for the host that actually needs them.
- **Safer Teardown:** Removed the immediate `about:blank` navigation before destruction; teardown removes the view, stops loading, clears client references, and destroys the WebView once.

### 🌐 3. Source-Scoped List Membership & Adult Content Blur (`KitsugiModels.kt`, `AppRoot.kt`, `AppRootDetailPages.kt`)
- **Source-Scoped Membership (`matchesInSource`, `firstMatchingInSource`):** List membership and detail lookups are scoped by platform source. Bangumi entries open Bangumi's editor, and Simkl or AniList records do not falsely mark Bangumi items as listed.
- **Scroll-to-Top Bottom Bar Reset:** Profile favorites scroll-to-top button now resets the shared bottom-bar visibility before and after scrolling.
- **Adult Content Blur Propagation:** Adult status from AniList profile favorites now reaches detail navigation so cinematic loading animation posters blur properly from the first frame.

### 🏷️ 4. Unified Global Tag & Genre Translations (`BangumiTagDictionary.kt`, `SearchTranslation.kt`, `GenreLabelFormatter.kt`, `KitsugiTranslations.kt`)
- **Consistency Across Providers:** Unified tag and genre definitions for AniList, MAL, Kitsu, Shikimori, and Bangumi across dictionaries, XML string resources, and search maps.
- **Standardized Terms:** Aligned translations for Heterosexual, Protagonist variants, Reverse Harem, Cast types, Kuudere, Nudity, Ensemble Cast, Fantasy, Slice of Life, Romance, Isekai, Cyberpunk, Post-Apocalyptic, and Medieval.

### 🗑️ 5. Third Gallery Button: Delete Downloaded Media (`KitsugiImageGalleryDialog.kt`, `KitsugiImageDownloadHelper.kt`, `DownloadsScreen.kt`)
- **Delete Button Added:** Action row now includes delete with two-step confirmation, MediaStore/SAF cleanup, and index synchronization.
- **Downloads Screen Integration:** Seamless tile removal and group-level bulk deletion.

### 🖼️ 6. Full GIF / Image Format Support (`KitsugiImageDownloadHelper.kt`)
- **Local File Sharing:** FileProvider sharing for local files and raw preservation for animated GIFs, animated WebP, and APNG.

### 🔞 7. Universal +18 Blur Enforcement (`AdultFlagBackfillMigration.kt`, `MediaEntryRepository.kt`, etc.)
- **Cross-Source Propagation:** Canonical identity matching propagates adult flags across all providers and ensures flags are never dropped during sync.

### 🧪 8. Tests
- `ImageFormatDetectionTest`, `AdultFlagBackfillMigrationTest`, `MediaEntryRepositoryAdultPropagationTest`.

### 🎨 9. Full Color Picker, Gradient Accent Mechanics & Auto Black-White Contrast (`KitsugiAccentColorPickerDialog.kt`, `KitsugiAccentSupport.kt`, `Theme.kt`, `KitsugiColors.kt`, `AppSettings.kt`, `SettingsDataStore.kt`)
- **"+" Button Opens Full Panel:** Replaced plain hex dialog with a 3-tab color picker:
  - **Palette:** 19 color families × 10 shades + black/white = **192 colors**.
  - **Custom:** Full **HSV saturation-value square + hue slider** (infinite colors, optional hex input).
  - **Gradient:** Two-color angled linear gradient with slider + 8 direction presets, live preview, start/end color targets.
- **Persistent Gradient Mechanics:** Persisted `customAccentColor2` and `customAccentGradientAngle`; renders gradient flows across buttons, setting icon circles, badges, and previews.
- **Automatic Black ↔ White Contrast (`onAccentColor`):** Contrast on accent backgrounds automatically transitions smoothly between black and white based on luminance.

### 🔄 10. Bangumi Full Cross-Sync Integration (`AuthViewModel.kt`, `BangumiSyncManager.kt`, `PlatformRateLimiter.kt`, `CrossSyncDisclaimer.kt`)
- **6/6 Connected Platform Support:** Full parity across AniList, MyAnimeList, Simkl, Kitsu, Shikimori, and Bangumi.
- **Rate Limiting & Japanese Title Matching:** 400ms rate limiter and candidate title resolution for Bangumi subjects.
- **Post-Sync Verification:** Automated `smartImport` verification for Bangumi library after sync.

### 🈯 11. Fixed Wrong-Script Cast Names & Character Image Fallback Chain (`PreferenceHelpers.kt`, `TmdbCreditsClient.kt`, `KitsugiCharacterClient.kt`)
- **Latin Script Validation:** Replaced naive non-CJK check with `PreferenceHelpers.isLatinText()` to prevent Thai/Arabic aliases from overriding English/Romaji names.
- **Cross-Source Character Image Chain:** For TMDB/Simkl anime entries missing AniList images, resolves portraits in sequence across MAL/Jikan → Shikimori → Kitsu using verified MAL IDs and name tokens.

### 🏢 12. Studio & Producer Works Infinite Pagination & Limit Fix (`KitsugiStudioClient.kt`, `JikanGateway.kt`, `StudioDetailViewModel.kt`, `StudioDetailPage.kt`, `KitsugiModels.kt`)
- **Tenrai 50 Request Limit Safeguard:** Tenrai returned HTTP 400 for `limit > 50`; capped automatically via `TENRAI_MAX_LIMIT = 50` in `JikanGateway.tenraiRest`.
- **Full Cross-Source Works Pagination:** Added continuous pagination for studios with >50 works across MAL/Jikan (`has_next_page`), Shikimori (`page` and `limit=50`), AniList (`pageInfo.hasNextPage`), and TMDB (`total_pages` across movie and tv discover).
- **Infinite Scrolling & Failure Recovery:** Seamless pagination as the user scrolls near the end of grid/list layouts, with a minimal loading indicator and retry button on network failures.

### 🎬 13. Trailer Preview Row Persistence & Player Toggle (`KitsugiDetailThemesTrailerComponents.kt`)
- **Persistent Header Row:** The "Watch Trailer" row remains visible above the expanded player, mimicking the opening/ending theme music player layout.
- **Active State & Tap Toggle:** Features an upward chevron icon and accent highlighting when active. Tapping the row toggles the player closed, alongside the bottom close button.
- **Universal Scope:** Powered by the shared `KitsugiTrailerCard`, functioning seamlessly across both library entries and API search details for anime and manga.

---

## 🇹🇷 Türkçe (v2.4.226)

### 📝 1. Liste Düzenleme Ekranı & Platform Eşitlemesi (`KitsugiEditMediaSheet.kt`, `BangumiSyncManager.kt`, `KitsuSyncManager.kt`, `ShikimoriSyncManager.kt`)
- **Bangumi Platform Entegrasyonu:** Bangumi kayıtları artık "Yerel/Manuel" kitaplık yerine gerçek Bangumi platform şablonuyla açılır; gereksiz manuel başlık ve +18 alanları yerine "Gizli" ve "Etiketler" alanları gösterilir. Notlar ve etiketler Bangumi API'sine senkronize edilir.
- **Kitsu Gizlilik ve Tarih Senkronizasyonu:** Kitsu düzenleme sayfasına "Gizli" seçeneği eklendi. Başlangıç/bitiş tarihleri, kişisel notlar ve gizlilik ayarları hem import edilir hem de Kitsu API'sine gönderilir. Kitsu'da bulunmayan manga cilt satırı gizlendi.
- **Shikimori "Yeniden İzleniyor" ve Tekrar Sayacı:** Shikimori için "Yeniden İzleniyor" durumu ve tekrar sayısı kontrolü eklendi; kişisel metin notu, tekrar sayısı ve cilt bilgileri hem içe aktarılır hem de uzaktaki Shikimori profiline iletilir.
- **Platform Destek Rozetleri & Güvenli Güncelleme:** İlgili platform API'sinin desteklemediği alanlar için düzenleme sayfasında şeffaf bilgilendirme rozetleri gösterilir (örn. Simkl'de not/tarih/favori, MAL'de favori). Yalnızca doldurulan alanlar gönderilir, boş bırakılan alanlar uzaktaki veriyi ezmez.

### 🚀 2. Eklenti Arama & Yayın Sağlayıcı İyileştirmeleri (`CsStreamRunner.kt`, `CsRuntimeInit.kt`)
- **Canlı Domain Koruması & Tablo Güvenlik Ağı:** Yerleşik ve uzak domain tablolarının, eklentinin kendi sağlıklı canlı adresini veya çalışma zamanında bulduğu aynayı (örn. FilmMakinesi, Dizipal, SezonlukDizi) ezmesi engellendi. Eski tablolar artık yalnızca boş, zehirli veya bilinen ölü adresleri kurtarmak için devreye girer; sağlıklı domain'e yalnızca açılışta güncellenen uzak tablo geri-alma korumasıyla dokunabilir.
- **Title-Search Fallback Koruması:** `getLoadUrl()` boş veya tanımsız döndüğünde arama sürecinin kilitlenmesi önlendi; boş dönen native sonuçlar otomatik olarak başlık bazlı aramaya yönlendirilir.
- **Scraper Disk Önbelleği Kaldırıldı:** 50 MB'lık OkHttp disk önbelleği kaldırıldı. Bazı sitelerin arama sayfalarında döndürdüğü `max-age` başlıkları nedeniyle Cloudflare engeline veya boş sonuca takılan aramaların diske kalıcı olarak yazılıp süresiz "0 sonuç" dönmesi sorunu çözüldü (Resmi CloudStream mimarisine hizalandı).
- **Arama Bütçesi & Eşzamanlılık Artışı:** Global `searchSemaphore` bütçesi 12'den 24'e çıkarıldı. 30+ eklenti paralel sorgulanırken sıradaki eklentilerin 20 saniyelik zaman aşımına uğraması engellendi.

### 🎬 3. Kitsu Film Ayrıştırması & TMDB Çakışma Önlemi (`KitsugiEpisodeRatingsRepository.kt`, `ApiResultDetailViewModel.kt`, `MediaEntryDetailViewModel.kt`)
- **Film ve Dizi Kimlik Çakışması Giderildi:** TMDB üzerinde aynı kimlik numarasını paylaşan film ve dizi kayıtları (örneğin *Howl's Moving Castle* filmi ile *Roar* dizisi) birbirinden tamamen ayrıldı. Film kayıtları yalnızca film uç noktalarını sorgular ve TV/SeriesGraph/TVDB yollarına düşmez.
- **Kitsu Alt Tür Tespiti:** Kitsu listelerinde tür bilgisi bulunmayan kayıtlar için Kitsu API üzerinden `subtype` ("movie") sorgusu yapılıp kimlik başına önbelleklenir. Hero, detay logosu ve galeriler doğru türü kullanır.
- **Temiz Medya Galerisi:** Film kayıtlarının Fanart galerilerine TV dizilerinin afiş ve ekran görüntülerinin karışması engellendi.

### 🔔 4. Profil ve Listem Bildirim Butonları & Keşfet Zarı (`AppRootTabPages.kt`, `ExploreScreen.kt`, `MyListComponents.kt`, `MyListScreen.kt`, `KitsugiProfileScreen.kt`)
- **Profil ve Listem Bildirim Entegrasyonu:** AniList, MAL veya Simkl hesaplarından biri bağlı olduğunda, Profil ekranında istatistik butonunun soluna ve Listem ekranında zar butonunun soluna bildirim butonu eklendi.
- **Keşfet Zarı Bağımsızlaştırıldı:** Keşfet ekranında bildirim butonu açıkken de rastgele keşfet (🎲) zarı görünür kalır ve bildirimin solunda konumlandırılır.

### 🌐 5. Bangumi "Diğer Adlar" & CJK Varyantları (`BangumiLocalizedName.kt`, `KitsugiSimklDetailClient.kt`)
- **Japonca, Çince ve Korece Alternatif Adlar Geri Getirildi:** "Diğer Adlar" / eşanlamlılar listesindeki Latin-harf zorunluluğu kaldırılarak Japonca, Çince ve Korece özgün varyantlar yeniden görünür hale getirildi. Yalnızca ana başlıkla birebir aynı olan tekrarlar elenir.

### 🏷️ 6. Bangumi Etiket & Tür Sözlüğü (`BangumiTagDictionary.kt`, `KitsugiTranslations.kt`, `strings.xml`)
- **%100 Resmî Meta Etiket Kapsamı:** Bangumi'nin 158 resmî meta etiketi (kaynak, tip, bölge, tema, hedef kitle, oyun türü, platform, kitap, müzik) eksiksiz olarak Türkçe ve İngilizceye çevrildi.
- **817 Kullanıcı Etiketi Desteği:** En popüler 817 kullanıcı etiketi Türkçe ve İngilizce karşılıklarıyla sözlüğe eklendi. Yazım varyantları (ör. `催泪` / `催涙` / `Duygusal`) tekilleştirildi.
- **Okunabilirlik Sıralaması:** Çevrilmemiş CJK etiketler listenin sonuna ötelenerek önce kullanıcının dilindeki etiketlerin görünmesi sağlandı.

### 🎨 7. Bangumi ve Simkl Vitrin Logoları & Çok Kaynaklı Zengin Galeriler (`HeroLogoPolicy.kt`, `MediaGalleryIdentity.kt`, `KitsugiPersonImageAggregator.kt`)
- **Vitrin Görsel Logoları:** Bangumi ve Simkl vitrinlerinde grafik logo varsa metin başlık yerine logo gösterilir.
- **Güvenli Çapraz Kimlik Eşleştirme:** Bangumi subject ID'si asla MAL ID olarak kullanılmaz; Simkl sonuçlarında TMDB kimliği korunur.
- **Karakter, Ekip ve Seslendirmen Galerileri:** MAL ID yoksa ad ve alternatif adlarla Jikan eşleşmesi yapılarak zengin galeriler oluşturulur.

### 🌟 8. Keşfet: "Yakında Yayında" Şeridi & Mantıksal Kaynak Rafları (`ExploreScreen.kt`, `AllSourcesExplore.kt`, `ExploreCategories.kt`)
- **Şerit Konumlandırma Standardı:** "Yakında Yayında" geri sayımlı şeridi, tüm kaynak sayfalarında kategori kısayollarının hemen altına taşındı.
- **Mantıksal Raf Hiyerarşisi:** Anime Rafları → Manga Rafları → Kaynağa Özgü Raflar (Manhwa, Novel, Simkl) düzenine kavuşturuldu.
- **Eksik Kısayollar:** "En Yüksek Puanlı Animeler" ve "En Yüksek Puanlı Mangalar" kısayolları eklendi.

### 🗓️ 9. Tüm Kaynaklarda Birleşik Yayın Tarihi ve Geri Sayım Mantığı (`NextAiringFormat.kt`, `DetailSharedComponents.kt`, `NextAiringChip.kt`)
- **Tek Ortak Biçim Katmanı (`NextAiringFormat`):** AniList, MAL/Jikan, TMDB, Shikimori, Bangumi, Simkl ve Kitsu kaynaklarında yayın tarihi ve geri sayım tek standart formata getirildi.
- **Detay Sayfaları:** *"Yaklaşan Yayın: Bölüm 2, 2026-10-15 tarihinde yayında (6 gün sonra)"* şeklinde hem tarih hem geri sayım gösterilir.

### 📋 10. Kullanıcı Profili Anime/Manga Listesi ve "Listem" Tam Paritesi (`KitsugiUserMediaListScreen.kt`, `MyListFilterHelpers.kt`)
- **Tasarım ve Üst Kontrol Paritesi:** Başka bir kullanıcının listesi ana Listem ekranıyla aynı başlık, görünüm değiştirici, arama ve filtre kontrollerine kavuştu.
- **Birleşik Anime & Manga Veri Kümesi:** Anime ve Manga listeleri tek havuzda birleştirilerek filtreleme seçenekleri zenginleştirildi.
- **Gelişmiş Filtreler:** Durum, medya türü, puan, yıl ve özel filtreler eklendi; poster önizleme ve rastgele seçim desteklendi.

### 🔐 11. Kalıcı Kasa Yedekleme, Çoklu Cihaz Çakışma Yönetimi & Android Keystore (`VaultBackupWorker.kt`, `VaultMerge.kt`, `LocalVaultKeyStore.kt`)
- **WorkManager Kalıcı Kuyruk:** Ağ kesintilerinde ve süreç kapanmalarında kasa yedekleri kaybolmaz.
- **3 Yönlü Akıllı Birleştirme:** Farklı servis token'ları çakışmasız birleşir; aynı servis token çakışmalarında kullanıcıya seçim sunulur.
- **Supabase CAS Koruması:** `kitsugi_vault_cas` veritabanı fonksiyonu ile yarış durumları engellenir.
- **Kesintiye Dayanıklı Şifre Değiştirme & Keystore Şifreleme:** Kasa anahtarı işlem yarıda kalsa dahi korunur ve yerel anahtar Android Keystore ile şifrelenir.
- **22 Taşınabilir Ayar:** Tema, liste düzeni ve dil ayarları şifreli yedek kapsamına alındı.

### 🧩 12. Eklenti Keşfet Portalı Ana Sayfa Paritesi & Stüdyo Detay İyileştirmeleri (`AddonExploreDialog.kt`, `AddonFullScreenGridPage.kt`, `AddonHomeParity.kt`, `StudioDetailPage.kt`, `StudioDetailComponents.kt`, `StudioDetailViewModel.kt`)
- **Eklenti Vitrin ve Raf Paritesi:** Eklenti portalı içerisindeki eklenti keşfet sayfaları ana sayfa keşfet sayfasıyla birebir aynı görsel dile ve etkileşim standartlarına kavuştu (`KitsugiHeroSection` öne çıkan vitrini, otomatik kayan carousel, çizgi-nokta göstergeleri, neon çerçeveli `KitsugiExploreMediaCard`, snap-fling kaydırma ve "Tümünü Gör" sözleşmesi).
- **"Tümünü Gör" Grid ve Liste Paritesi:** Eklenti keşfet ekranındaki "Tümünü Gör" açılır sayfası (`AddonFullScreenGridPage`), ana stüdyo ve keşfet grid sayfalarıyla aynı tasarım diline geçirildi: emojili tür filtre çipleri (✨ Tümü / 🎌 Anime / 🎥 Film / 📺 Dizi), Filtre+Sıralama alt sayfası (`KitsugiStudioFilterBottomSheet`), grid ↔ liste görünüm geçişi (kalıcı tercih hafızasıyla), içerik sayacı, kaydırınca beliren üst şerit ve hızlı yukarı FAB.
- **CS3 Ortak Model Köprüsü & Temiz Eklenti Rozetleri:** CloudStream 3 arama sonuçları ortak `JikanSearchResult` modeline dönüştürülerek bilinmeyen kaynaklarda sahte `#id` gösterimi kaldırıldı; genel eklenti rozeti (`Icons.Default.Extension`) ve eklenti çipi ile vitrin çipinin boş kalması önlendi.
- **Stüdyo Kuruluş Tarihi Formatı:** Stüdyo detay sayfasındaki ham ISO formatlı kuruluş tarihi (`1998-10-01T00:00:00+00:00`) `KitsugiDateUtils` ile *"Kuruluş: 1 Ekim 1998"* biçiminde okunabilir Türkçe tarihe dönüştürüldü (hem vitrin başlığında hem sol bilgi panelinde).
- **Stüdyo "Hakkında" Kartı Tam Detay Paritesi:** Stüdyo sayfası "Hakkında" kartı medya detay sayfasındaki `DetailSynopsisCard` ile tam pariteye kavuşturuldu: otomatik Türkçe çeviri desteği (`autoTranslateEnabled` veya Rusça metin tespiti), `DetailCache` önbellekleme, uygulama içi çeviri ve 3. parti çevirmen butonu, tek dokunuşla panoya kopyalama ("Panoya kopyalandı") ve uzun metinlerde "Daha fazla / Daha az" genişletme kontrolü.

### 🎯 13. "Listeye Ekle" & "Zaten Listede" Çelişkisi ve Arama Başlık Eşleşmesi (`KitsugiModels.kt`, `AppViewModel.kt`, `ApiResultDetailPage.kt`, `MyListFilterHelpers.kt`, `MyListLibraryGrouping.kt`, `MyListScreen.kt`)
- **Buton ve Kontrol Tutarlılığı:** Detay sayfasındaki "Listeye Ekle" butonu ile ekleme akışındaki mükerrer kontrolü aynı eşleme sonucuna bağlandı (`findExistingEntry`). Kayıt kütüphanede zaten mevcutsa buton dinamik olarak **"✎ Düzenle"** durumuna geçer ve tıklandığında mevcut kaydı doğrudan açar.
- **Açıklayıcı ve Hedef Gösteren Uyarı Mesajı:** Ekleme akışındaki uyarı mesajı artık gelen sorgu adını değil, listede bulunan asıl kaydın adını ve bulunduğu sekmeyi bildirir (*"\"Clannad: After Story\" zaten listende var (AniList sekmesi)."*).
- **Çoklu Başlık Varyantı Eşleşmesi:** `MediaEntry.matches` kontrolü tüm başlık türevlerini (özgün ad, İngilizce, Japonca, Romaji) `MediaIdentity.normalizedTitle` standardıyla karşılaştırarak dil tercihlerinden kaynaklanan eşleşme kayıplarını giderir.
- **Listem Gelişmiş Arama:** Listem ekranında arama yapılırken alternatif İngilizce/Japonca başlıklar ve noktalama/boşluk duyarsız normalleştirilmiş sorgular taranır ("clannad after story", "CLANNAD 〜AFTER STORY〜", "クラナド" gibi tüm yazım varyantları doğru kaydı bulur).
- **Çözülmüş Kimliklerle Güvenlik Ağı:** Erken tıklama durumlarında çift kayıt oluşmaması için kimlik çözümleme sonrasında ikinci bir kontrol eklenerek veri tabanı bütünlüğü korundu (6 yeni birim testiyle doğrulandı).

### 🏢 14. Stüdyo & Yapımcı Kaynak Kimliği Ayrıştırması & Şirket Arama Yönlendirmesi (`KitsugiStudioClient.kt`, `StudioSourceSupport.kt`, `JikanSearchClient.kt`, `SearchScreen.kt`, `SearchResultRow.kt`, `StudioDetailPage.kt`, `StudioDetailViewModel.kt`)
- **Sağlayıcıya Özgü Stüdyo Kimlik Ayrıştırması:** Stüdyo ve yapımcı kimliklerinin farklı platformlarda çakışması (örneğin MAL üzerindeki Aniplex'in AniList ID çakışması nedeniyle Bones olarak açılması) engellendi; her stüdyo ve yapımcı öncelikle kendi kaynak kimliğiyle (`source`) sorgulanır.
- **İsim Doğrulaması & Akıllı Yeniden Arama:** Jikan/MAL üzerinde dönen stüdyo adı tıklanan isimle uyuşmazsa şirket adıyla otomatik doğrulama araması yapılır; eşleşme teyit edilemeyen yanıltıcı stüdyo kayıtları önlenir.
- **Çoklu Kaynak Desteği:** TMDB, Shikimori ve Bangumi stüdyo yolları normalize edildi; Bangumi için yalnızca şirket türündeki (`type = 2`) kayıtlar stüdyo olarak eşleştirilir.
- **Doğrudan Stüdyo Detayına Yönlendirme:** Arama ekranında "Stüdyo" filtresiyle aranan kayıtlar anime/medya detayına değil, doğrudan ilgili sağlayıcının `StudioDetailPage` sayfasına yönlendirilir; stüdyo arama satırında yanıltıcı "listeye ekle" butonu gizlenir.
- **Kesintisiz Yükleme Deneyimi & Görsel Aktarımı:** Stüdyo logosu ve adı navigasyon rotası üzerinden yükleme ekranına taşınarak beyaz/boş ekran titremesi engellenir.

### 📱 15. Profil Favorilerinde Alt Bar (BottomBar) ile Uyumlu Kategori Butonu Hizalaması (`AniListProfileContent.kt`, `KitsugiProfileScreen.kt`, `AppRootTabPages.kt`)
- **Dinamik Alt Gezinme Çubuğu Hizalaması:** Profil ekranı "Favoriler" sekmesinde yer alan yüzen kategori ("Anime" / `☰`) filtre butonu ve hızlı yukarı çıkma FAB'ı, alt gezinme çubuğunun (BottomBar) açık/kapalı durumuna göre dinamik olarak konumlandırılır (`isBottomBarVisible` etkinken `96.dp + insets`, kapalıyken veya yatay modda `16.dp / 20.dp + insets`).
- **Görsel Çakışma ve Dokunma Engeli Giderildi:** Kategori butonunun "Keşfet" sekmesi üzerine binmesi, menü öğelerinin birbirini perdelemesi ve listenin son satırının alt barın altında kalması tamamen çözüldü (`Spacer(140.dp)`).

### 📐 16. Dikey Mod Kenar Taşması (Peek) Düzeltmesi (`DetailPageScaffold.kt`)
- **Kenar Boşluğu Temizliği:** Dikey mod detay sayfalarında (`DetailPageScaffold`), `HorizontalPager` üzerindeki `contentPadding = PaddingValues(horizontal = 16.dp)` kaldırıldı ve dolgu doğrudan sayfa `Box` bileşeni içine taşındı (`padding(horizontal = 16.dp)`).
- **Komşu Sekmelerin Ekrana Taşması Engellendi:** Ekran kenarlarından komşu sekmelerin köşelerinin görünmesi ("peek" sorunu) tamamen giderildi; sekmeler ekran genişliğini tam kaplayarak kenarlardan taşma yapmaz.

### 🌐 17. Cümle Sınırlarından Bölmeli ve Kademeli Uzun Biyografi & Özet Çevirisi (`TranslationManager.kt`, `CharacterDetailViewModel.kt`, `StaffDetailViewModel.kt`, `ApiResultDetailViewModel.kt`, `MediaEntryDetailViewModel.kt`)
- **4 Kademeli Akıllı Metin Bölme:** 2000+ karakterlik uzun metinler (özellikle HTML'den gelen ve satır sonu içermeyen dev tek paragraf karakter/personel biyografileri) sırasıyla paragraf (`\n\n`) → satır (`\n`) → cümle (`. ! ? … 。！？；`) → kelime sınırlarından bölünür. Noktalama sonrası boşluk şartı aranarak `3.14` ve `Mr.` gibi kısaltmalar korunur.
- **Kademeli Çeviri Akışı (Streaming UI):** Uzun metinlerde her parça çevrildikçe ekrana "çevrilen kısım + henüz çevrilmemiş orijinal kuyruk" anında yansıtılır; metin boyutu sıçramaz, dil kullanıcının gözü önünde akıcı şekilde Türkçeye dönüşür.
- **Uygulama Seviyesinde Retry & Sessiz Hata Koruması:** Her parça için 3 denemeli üstel geri çekilme (`delay`) eklendi; HTTP durum kodları loglanır ve `fetchSingleChunk` hata durumunda `null` dönerek başarısızlıkları açıkça bildirir.
- **Önbellek Hijyeni:** Room ve `DetailCache`'e yalnızca %100 eksiksiz tamamlanan çeviriler yazılır; yarım çeviriler önbelleğe alınmaz.

### 🛡️ 18. Medya Detay Sosyal Eşleşme Güvenliği & Keşfet Vitrin Backdrop Önceliği (`KitsugiMediaSocialClient.kt`, `DetailCache.kt`, `KitsugiHeroSection.kt`, `TmdbDiscoverClient.kt`, `ExploreViewModel.kt`)
- **Kaynak ve Tür Bazlı Sosyal Eşleşme:** Detay sayfalarındaki inceleme, forum ve aktivite akışlarında yabancı platform kimlikleri (TMDB, Simkl vb.) doğrudan MAL veya AniList ID'si olarak kabul edilmez. Yalnızca açık ve doğrulanmış çapraz kimlikler kullanılır.
- **Aktivite ve İnceleme Filtresi:** Anime/Manga olmayan içeriklerde Jikan/AniList sosyal sorguları yapılmaz. Aktiviteler hedef AniList ID'si ve türüyle birebir eşleşmek zorundadır; medyasız serbest metin aktiviteleri filtrelenir.
- **Review Cache Tür İzolasyonu:** `DetailCache.getMediaReviews` artık `source + ID + mediaType` üçlüsüyle izole edilir; dizi/anime incelemeleri birbirine taşmaz.
- **Keşfet Vitrininde Yatay Backdrop Önceliği:** Anime, TV ve filmlerde dikey ekranlarda da mevcut yatay backdrop görselleri posterden önce denenir. TMDB movie/TV kayıtları exact ID ile, diğerleri tür ve yıl uyumlu başlık aramasıyla çözülür (Manga oran mantığını korur).

### 🖼️ 19. Fanart API Galeri Detayları, Kalıcı Bildirim Arşivi ve Bangumi Bildirim Kaynağı (`FanartApiClient.kt`, `GalleryItem.kt`, `KitsugiImageGalleryDialog.kt`, `NotificationArchiveStore.kt`, `KitsugiNotificationsViewModel.kt`, `NotificationDiagnostics.kt`)
- **Fanart API Detayları Galeride:** Fanart.tv v3 API'sinden gelen `name` ve `iMDb` alanları galeride "Ad" ve "IMDb ID" satırları olarak sunulur. Yatay modda sağ panelde yer alırken, dikey modda üst bara eklenen ℹ️ "Detaylar" butonuyla açılan alt levhada gösterilir.
- **Kalıcı Bildirim Arşivi (`NotificationArchiveStore`):** MAL, Simkl, Kitsu ve Bangumi gibi API'sinde kişisel bildirim ucu bulunmayan kaynaklar için üretilen yayın takvimi ve izleme listesi bildirimleri yerel `notifications_archive.json` dosyasında (kaynak başına 300 kayıt) ve Kitsugi hesabı bulut yedeğinde (`user_data.notifications`) kalıcı olarak saklanır; 7 günlük takvim penceresinden düşen bildirimler kaybolmaz.
- **Bangumi Bildirim Kaynağı:** Bildirim ekranı ve seçici paneline 6. kaynak olarak Bangumi eklendi. İzleme listesi (在看/DOING) + önbellekli MAL çapraz çözümü + yayın takvimi eşleşmesiyle bildirimler üretilir; hesap bağlı olmadığında yerel kayıtlar gösterilir. Bildirim Teşhis paneli 6 kaynağı canlı olarak test eder.

### 🔞 20. Shikimori Liste Kartları +18 (Adult) Blur Düzeltmesi & GraphQL Çözümlemesi (`ShikimoriAdultResolver.kt`, `ShikimoriImportManager.kt`, `ShikimoriAdultFlagMigration.kt`)
- **Kök Neden Tespiti:** Shikimori'nin `user_rates` REST uç noktası gömülü anime/manga verilerini `AnimeSerializer` ve `MangaSerializer` ile döndürür; bu serializer'lar `rating` ve `genres` alanlarını hiç taşımaz. Eski `fetchAdultMediaIds` fonksiyonu da aynı kısıttan ötürü her zaman boş dönüyor ve Shikimori kayıtları `isAdult = false` olarak içe aktarılıyordu (blur ayarı açık olsa bile liste kartlarında uygulanmıyordu).
- **Yeni `ShikimoriAdultResolver` (GraphQL):** 50'lik gruplar halinde `{ animes(ids: "...", limit: 50, censored: false) { id rating genres { name } } }` sorgusu koşturulur (`censored: false` ile hentai filtresi aşılır, manga için şemada `rating` bulunmadığından tür listesi kullanılır). Sonuçlar önbelleğe alınır ve hız sınırlarına uyulur.
- **Mevcut Veritabanı Kayıtlarının Onarımı (`ShikimoriAdultFlagMigration`):** Listem ekranı açıldığında yerel veritabanında `isAdult = false` kalmış Shikimori kayıtları GraphQL üzerinden taranır ve +18 olanlar (rx/hentai) arka planda otomatik olarak `isAdult = true` yapılır.

### 📦 21. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.226-foss.apk`).

---

## 🇬🇧 English (v2.4.226)

### 📝 1. Media Entry Editor & Provider Parity (`KitsugiEditMediaSheet.kt`, `BangumiSyncManager.kt`, `KitsuSyncManager.kt`, `ShikimoriSyncManager.kt`)
- **Bangumi Platform Alignment:** Bangumi entries open with genuine Bangumi platform templates rather than generic "local library" fields (title, +18, total counts); introduces "Private" and "Tags" fields with bidirectional sync to Bangumi API.
- **Kitsu Privacy & Date Sync:** Added "Private" toggle to Kitsu editor. Start/finish dates, personal notes, and privacy settings are imported and synced to Kitsu; unsupported manga volume counter is hidden.
- **Shikimori Rewatching & Counters:** Added "Rewatching" status and rewatch count field; personal notes, rewatch counts, and manga volume progress are fully synced bidirectionally.
- **Platform Capability Indicators:** Explicit informational labels indicate fields unsupported by remote APIs (e.g. notes/dates on Simkl, favorites on MAL). Only populated values are synced to prevent remote data erasure.

### 🚀 2. Extension Search & Stream Runner Overhaul (`CsStreamRunner.kt`, `CsRuntimeInit.kt`)
- **Live Domain Protection & Table Safety Net:** Hardcoded and remote domain tables no longer overwrite healthy plugin domains or runtime-discovered mirrors (e.g., FilmMakinesi, Dizipal, SezonlukDizi). Stale tables are now only used to rescue empty, poisoned, or dead URLs.
- **Title-Search Fallback Preservation:** Empty native `getLoadUrl()` resolution results no longer swallow the search pipeline; they cleanly fall back to title-based search.
- **Scraper Disk Cache Removed:** Removed the 50 MB OkHttp disk cache from the scraper client. Websites serving `max-age` headers on search queries previously caused Cloudflare challenges or 0-result pages to be cached indefinitely on disk.
- **Concurrency & Budget Increase:** Increased global `searchSemaphore` from 12 to 24 permits, preventing queued plugins from exhausting their 20-second timeout budgets during 30+ plugin parallel searches.

### 🎬 3. Kitsu Movie Subtype Disambiguation & TMDB Conflict Prevention (`KitsugiEpisodeRatingsRepository.kt`, `ApiResultDetailViewModel.kt`, `MediaEntryDetailViewModel.kt`)
- **Movie vs. TV ID Collision Prevention:** Media sharing identical TMDB IDs between movies and TV shows (e.g. *Howl's Moving Castle* film vs *Roar* series) are strictly isolated. Movie entries query only movie endpoints and avoid TV/SeriesGraph fallbacks.
- **Kitsu Subtype Caching:** Queries and caches the Kitsu API `subtype` attribute per entry, allowing hero, detail logos, and galleries to use movie endpoints.
- **Clean Media Galleries:** Prevents TV series artwork from polluting anime movie Fanart galleries.

### 🔔 4. Profile & My List Notification Buttons & Explore Dice Button (`AppRootTabPages.kt`, `ExploreScreen.kt`, `MyListComponents.kt`, `MyListScreen.kt`, `KitsugiProfileScreen.kt`)
- **Notification Navigation in Profile & My List:** When AniList, MAL, or Simkl is connected, a notification icon button appears in Profile (left of stats) and My List (left of the dice button).
- **Independent Explore Dice:** The random picker dice button (🎲) in Explore remains visible even when notifications are active.

### 🌐 5. Bangumi Alternate Titles & CJK Variants Restored (`BangumiLocalizedName.kt`, `KitsugiSimklDetailClient.kt`)
- **CJK Alternate Titles Restored:** Lifted the Latin-only restriction on alternative titles in Bangumi and Simkl, re-enabling Japanese, Chinese, and Korean title variants in the "Other Names" section while discarding exact duplicates of the main title.

### 🏷️ 6. Bangumi Tag & Genre Dictionary (`BangumiTagDictionary.kt`, `KitsugiTranslations.kt`, `strings.xml`)
- **100% Official Meta Tag Coverage:** All 158 official Bangumi meta tags (sources, types, genres, themes, platforms, etc.) are localized in Turkish and English.
- **817 User Tags Dictionary:** 817 popular user tags localized with spelling variant deduplication (e.g. `催泪` / `催涙` / `Tearjerker`).
- **Readability Tag Sorting:** Unmapped CJK tags sort to the end of the tag cloud, prioritizing readable tags.

### 🎨 7. Hero Logos & Multi-Source Galleries (`HeroLogoPolicy.kt`, `MediaGalleryIdentity.kt`, `KitsugiPersonImageAggregator.kt`)
- **Hero Graphical Logos:** Displays graphic logos in the Hero spotlight for Bangumi and Simkl items when available.
- **Cross-Source ID Segregation:** Bangumi subject IDs are never used as MAL IDs; Simkl entries retain valid TMDB IDs.
- **Character, Staff & Voice Actor Galleries:** Resolves people missing MAL IDs using strict name matching against Jikan.

### 🌟 8. Explore: Airing Soon Shelf & Ordered Categories (`ExploreScreen.kt`, `AllSourcesExplore.kt`, `ExploreCategories.kt`)
- **Repositioned Airing Soon Banner:** Relocated immediately below category shortcuts across all views.
- **Logical Shelf Ordering:** Standardized order: Anime shelves → Manga shelves → Source-specific shelves.
- **Category Shortcuts:** Added "Top Rated Anime" and "Top Rated Manga" shortcuts.

### 🗓️ 9. Universal Airing Dates & Countdowns (`NextAiringFormat.kt`, `DetailSharedComponents.kt`, `NextAiringChip.kt`)
- **Unified Formatting Layer:** Standardized air dates and countdowns across AniList, MAL, TMDB, Shikimori, Bangumi, Simkl, and Kitsu.
- **Detail Screens:** Unified label: *"Yaklaşan Yayın: Bölüm 2, 2026-10-15 tarihinde yayında (6 gün sonra)"*.

### 📋 10. User Profile List & "My List" Full Parity (`KitsugiUserMediaListScreen.kt`, `MyListFilterHelpers.kt`)
- **Full Header & Filter Parity:** Viewing another user's list now provides the complete header, view modes, search, and filtering capabilities of the main "My List" screen.
- **Combined Anime & Manga Pool:** Both media lists are loaded concurrently and filterable on the fly.

### 🔐 11. Hardened Account Backup, Multi-Device CAS & Keystore (`VaultBackupWorker.kt`, `VaultMerge.kt`, `LocalVaultKeyStore.kt`)
- **WorkManager Persistent Queue:** Protects pending backups against network drops, process death, and reboots.
- **3-Way Merge & CAS:** Automatic merging for distinct accounts and server-side compare-and-swap on Supabase.
- **Keystore Encryption & Interruption-Proof Password Change:** Hardware-backed encryption and dual-wrap transitions.
- **22 Portable Settings:** Encrypted alongside tokens.

### 🧩 12. Extension Explore Portal Home Parity & Studio Detail Polish (`AddonExploreDialog.kt`, `AddonFullScreenGridPage.kt`, `AddonHomeParity.kt`, `StudioDetailPage.kt`, `StudioDetailComponents.kt`, `StudioDetailViewModel.kt`)
- **Extension Explore Home Screen Parity:** Extension explore pages within the Addon Portal now match the primary Home Explore screen in aesthetics and interaction (`KitsugiHeroSection` featured hero carousel, auto-advancing slides, line-dot indicators, neon-framed `KitsugiExploreMediaCard`, snap-fling physics, and standard "See All" contracts).
- **"See All" Fullscreen Grid & List Parity:** The "See All" destination (`AddonFullScreenGridPage`) adopts the studio/explore grid architecture: emoji-annotated type filter chips (✨ All / 🎌 Anime / 🎥 Movie / 📺 Series), Filter & Sort bottom sheet (`KitsugiStudioFilterBottomSheet`), grid ↔ list view toggle (with persistent layout preference), item count indicator, sticky header on scroll, and scroll-to-top FAB.
- **CS3 Unified Model Bridge & Clean Addon Badges:** CloudStream 3 search results map to standard `JikanSearchResult` models; redundant fake `#id` labels are removed from addon cards, and fallback extension badges/pills (`Icons.Default.Extension`) prevent blank source pills in hero headers.
- **Studio Foundation Date Formatting:** Replaced raw ISO timestamps (`1998-10-01T00:00:00+00:00`) with localized date formatting (*"Founded: 1 October 1998"* via `KitsugiDateUtils`) across both hero and side panels.
- **Studio "About" Synopsis Card Full Parity:** The studio "About" section now matches the media detail `DetailSynopsisCard`: automatic Turkish translation (`autoTranslateEnabled` or Cyrillic auto-detection) backed by `DetailCache`, in-app translation toggle with 3rd-party translator fallback, one-tap copy to clipboard toast, and expandable "Read more / Read less" controls.

### 🎯 13. "Add to List" & "Already in List" Discrepancy & Search Title Matching (`KitsugiModels.kt`, `AppViewModel.kt`, `ApiResultDetailPage.kt`, `MyListFilterHelpers.kt`, `MyListLibraryGrouping.kt`, `MyListScreen.kt`)
- **Button and Action Parity:** The detail page "Add to List" button now checks the exact same identity/duplicate match (`findExistingEntry`) as the add action. If the media entry already exists in the library, the button seamlessly turns into **"✎ Edit"** and opens the existing entry directly upon click.
- **Descriptive Duplicate Notification:** The duplicate snackbar notification now references the existing library entry's title and its tab (*"\"Clannad: After Story\" already in your list (AniList tab)."*), eliminating confusion when names differ across providers.
- **Multi-Variant Title Matching:** `MediaEntry.matches` checks all title variants (canonical, English, Japanese, Romaji) using `MediaIdentity.normalizedTitle` to prevent mismatches caused by display language choices.
- **Robust "My List" Search:** Searching in "My List" scans English and Japanese alternative titles as well as punctuation- and spacing-insensitive normalized strings (e.g. queries for "clannad after story", "CLANNAD 〜AFTER STORY〜", and "クラナド" all find the target entry).
- **Resolved ID Double-Check Safety Net:** Early clicks before cross-platform ID resolution completes are captured by a second check using resolved IDs, preventing duplicate entries (verified by 6 unit tests).

### 🏢 14. Studio & Producer Provider Identity Isolation & Search Routing (`KitsugiStudioClient.kt`, `StudioSourceSupport.kt`, `JikanSearchClient.kt`, `SearchScreen.kt`, `SearchResultRow.kt`, `StudioDetailPage.kt`, `StudioDetailViewModel.kt`)
- **Provider-Scoped Studio ID Resolution:** Cross-provider studio ID collisions (such as Aniplex opening Bones due to mismatched MAL and AniList numeric ID namespaces) are completely resolved; each studio is queried using its origin provider namespace.
- **Name Verification & Fallback Search:** When Jikan returns a company name differing from the clicked label, an automatic name search verification is triggered, preventing incorrect company data from being shown.
- **Multi-Source Support:** Normalized studio endpoints across TMDB, Shikimori, and Bangumi (filtering strictly for company records with `type = 2` on Bangumi).
- **Direct Studio Search Routing:** Searching under the "Studio" scope routes directly to `StudioDetailPage` rather than media details, with "Add to List" buttons disabled for company entries.
- **Seamless Loading Transition:** Studio logo and name are carried across navigation into the loading screen to prevent blank screen flicker.

### 📱 15. Profile Favorites Bottom Navigation Bar Adaptive Alignment (`AniListProfileContent.kt`, `KitsugiProfileScreen.kt`, `AppRootTabPages.kt`)
- **Bottom Bar Adaptive Floating Controls:** The floating category filter button ("Anime" / `☰`) and scroll-to-top FAB on the Profile "Favorites" tab now adapt dynamically to bottom navigation bar visibility (`isBottomBarVisible` sets `96.dp + insets` when visible, animating to `16.dp / 20.dp + insets` when hidden or in landscape).
- **Eliminated Overlap & Click Blocking:** Resolves the visual collision where the floating button sat directly over the "Keşfet" tab, ensuring clean separation and sufficient list bottom padding (`Spacer(140.dp)`).

### 📐 16. Portrait Mode Horizontal Tab Peek Elimination (`DetailPageScaffold.kt`)
- **Clean Margins Without Leakage:** Removed `contentPadding = PaddingValues(horizontal = 16.dp)` on portrait `HorizontalPager` and applied 16.dp padding directly inside the page `Box`. Adjacent tabs no longer peek from the left/right screen edges.

### 🌐 17. Long Biography & Synopsis Progressive Translation (`TranslationManager.kt`, `CharacterDetailViewModel.kt`, `StaffDetailViewModel.kt`, `ApiResultDetailViewModel.kt`, `MediaEntryDetailViewModel.kt`)
- **4-Stage Text Chunking:** Texts exceeding 2000 characters are progressively split along paragraph (`\n\n`) → newline (`\n`) → sentence (`. ! ? … 。！？；`) → word boundaries, with punctuation-whitespace validation protecting numbers (e.g. `3.14`) and titles (e.g. `Mr.`).
- **Progressive Streaming UI:** Translations stream chunk-by-chunk to the UI ("translated head + original tail"), avoiding layout jumps and translating smoothly before the user's eyes.
- **Application-Level Retries & Error Visibility:** 3 attempts with exponential backoff on HTTP/translation failures; `null` return on failure avoids silent degradation, and only 100% complete translations are cached in Room and `DetailCache`.

### 🛡️ 18. Media Detail Social Matching Safety & Explore Backdrop Prioritization (`KitsugiMediaSocialClient.kt`, `DetailCache.kt`, `KitsugiHeroSection.kt`, `TmdbDiscoverClient.kt`, `ExploreViewModel.kt`)
- **Source & Type Isolated Social Mapping:** Forum topics, activities, and reviews no longer conflate third-party IDs (TMDB, Simkl) with MAL or AniList IDs; only verified cross-platform mappings are accepted.
- **Strict Activity Verification:** Social queries are disabled for non-anime/manga media. Activities strictly match the destination AniList ID and media type; unlinked text posts are discarded.
- **Review Cache Type Isolation:** `DetailCache.getMediaReviews` is strictly scoped by `source + ID + mediaType`, preventing cross-medium cache leakage.
- **Explore Hero Backdrop Prioritization:** For anime, TV, and movies, horizontal backdrop artwork is prioritized over posters even on portrait screens. TMDB titles query exact TMDB IDs, while other sources use type- and year-aware matching (Manga preserves aspect-ratio priority).

### 🖼️ 19. Gallery Fanart API Details, Persistent Notification Archive & Bangumi Notification Source (`FanartApiClient.kt`, `GalleryItem.kt`, `KitsugiImageGalleryDialog.kt`, `NotificationArchiveStore.kt`, `KitsugiNotificationsViewModel.kt`, `NotificationDiagnostics.kt`)
- **Fanart API Metadata in Gallery:** Displays `name` and `iMDb` fields from the Fanart.tv v3 API as "Name" and "IMDb ID" metadata. Integrated into the landscape right inspector panel and accessible via a new ℹ️ "Details" top-bar button on portrait screens.
- **Persistent Notification Archive (`NotificationArchiveStore`):** Airing and watchlist notifications generated for platforms without dedicated personal notification endpoints (MAL, Simkl, Kitsu, Bangumi) are stored persistently in `notifications_archive.json` (up to 300 items per provider) and backed up to Supabase (`user_data.notifications`). Notifications no longer vanish once they slide out of the 7-day airing window.
- **Bangumi Notification Provider:** Added Bangumi as the 6th notification source with custom icon, login status, and username pill. Queries the user's DOING (在看) collection, resolves cross-platform IDs against the airing schedule, and displays local entries with helpful notes when unauthenticated. Diagnostics panel upgraded to verify all 6 providers live.

### 🔞 20. Shikimori List Card +18 (Adult) Blur Fix & GraphQL Resolution (`ShikimoriAdultResolver.kt`, `ShikimoriImportManager.kt`, `ShikimoriAdultFlagMigration.kt`)
- **Root Cause Resolution:** Shikimori's `user_rates` endpoint serializes embedded anime and manga models without `rating` or `genres` fields, causing all imported entries to save with `isAdult = false` and bypassing the adult blur setting on library cards.
- **New `ShikimoriAdultResolver` (GraphQL):** Batches up to 50 items per query using `{ animes(ids: "...", limit: 50, censored: false) { id rating genres { name } } }` (`censored: false` lifts the hentai filter; manga resolves via genre tags). In-memory caching, rate-limit awareness, and automatic retries ensure fast, resilient resolution.
- **Database Backfill Migration (`ShikimoriAdultFlagMigration`):** Automatically migrates and flags existing library entries in the background upon opening "My List", immediately applying cover blur to adult media without requiring a manual re-sync.

### 📦 21. Packaging
- Built exclusively as **FOSS** release (`assembleFossRelease`) (`Kitsugi-Beta-v2.4.226-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.225)

### 🌸 1. Bangumi Başlık Dili & Latin Ad Zenginleştirmesi (`KitsugiBangumiClient.kt`, `BangumiLocalizedName.kt`)
- **Liste Kartlarında Latin Ad Desteği:** Bangumi liste ve arama uçları (`/v0/search`, `/v0/subjects`, sıralamalar vb.) infobox dönmediğinden Korece ve Çince dizi/anime başlıkları (ör. "김비서가 왜 그럴까", "눈물의 여왕") yerel dilde görünüyordu. Başlığı CJK olan kayıtlar için `withLatinBangumiNames` mekanizması devreye girerek arka planda (Semaphore 4 sınırlayıcı ile) subject detayını tek seferlik çeker, İngilizce/Romaji adları `BangumiTitleCache`'e yazar ve kart başlıklarını uygulama dil tercihine göre günceller.
- **Tüm Bangumi Keşfet & Arama Listeleri:** En İyi Animeler/Mangalar, Popüler Listeler, Sezonluk Keşif, Real/Dizi Şeritleri ve Normal Arama dahil 15 farklı liste çağrısına uygulandı.
- **Alternatif Adlar (Diğer İsimler) Filtresi:** Detay sayfalarındaki "Diğer Adlar" alanında Japonca, Çince ve Korece karakterli varyantlar gizlendi; kullanıcıya yalnızca temiz Latin harfli adlar sunulur.

### 👤 2. Karakter & Kişi Detay Sayfası İyileştirmeleri (`CharacterDetailPage.kt`, `StaffDetailPage.kt`)
- **Seçilen Dile Bağlı Japonca Alt Satır:** Karakter ve ekip detay sayfalarında adın altındaki Japonca yerel ad satırı, artık yalnızca kullanıcı uygulama dilini "Japonca/Yerel" seçtiğinde gösterilir. Diğer dillerde (İngilizce/Romaji) gereksiz tekrar engellendi.

### 📱 3. Profil & Favoriler UI Düzeltmeleri (`AniListProfileContent.kt`, `ProfileFavoritesListemStyle.kt`)
- **AniList Profil Kategori Butonu Hizalaması:** AniList profil favorilerindeki kategori filtre butonu (`bottomOffset = 96.dp`), kullanıcı profiliyle uyumlu olacak şekilde `20.dp`'ye indirilerek ekranın alt kenarıyla hizalandı.
- **Hızlı Yukarı Kaydırma Butonu:** Favoriler listesinde yukarı dön butonu, kullanıcının 3 öğe beklemesine gerek kalmadan ilk öğeden çıkıldığı anda (~240px kaydırmada) anında görünür hale getirildi.

### 📦 4. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.225-foss.apk`).

---

## 🇬🇧 English (v2.4.225)

### 🌸 1. Bangumi Title Localization & Latin Name Enrichment (`KitsugiBangumiClient.kt`, `BangumiLocalizedName.kt`)
- **Latin Name Resolution for List Cards:** Bangumi list and search endpoints do not return infoboxes, previously causing Korean/CJK titles (e.g. "김비서가 왜 그럴까", "눈물의 여왕") to render in native script. The new `withLatinBangumiNames` pipeline queries subject details for CJK titles (rate-limited via Semaphore 4), caches English/Romaji names in `BangumiTitleCache`, and updates list cards to match user language preferences.
- **Universal Coverage:** Covers Top Anime/Manga, Trending, Seasonal Browse, Real Drama/Movies, and Search lists (15 endpoints).
- **Clean Alternative Names:** Filtered non-Latin scripts from "Other Names" on detail pages; CJK variants are excluded from alternative aliases.

### 👤 2. Character & Staff Detail Localization (`CharacterDetailPage.kt`, `StaffDetailPage.kt`)
- **Language-Aware Native Subtitle:** The Japanese native name subtitle below character and staff headings is now only shown when the user has selected "Native / Japanese" as their display language.

### 📱 3. Profile & Favorites Layout Polish (`AniListProfileContent.kt`, `ProfileFavoritesListemStyle.kt`)
- **AniList Profile Category Button Position:** Adjusted `bottomOffset` from `96.dp` to `20.dp` on the AniList profile tab, matching the user profile layout.
- **Responsive Scroll-to-Top:** The floating scroll-to-top button now activates immediately after passing the first item (~240px) rather than waiting for item 4.

### 📦 4. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.225-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.224)

### 🔐 1. Kitsugi Hesap Senkronizasyonu & Güvenli Kasa (`LinkedAccountVault.kt`, `KitsugiAccountRepository.kt`, `KitsugiAccountContent.kt`)
- **Uçtan Uca Şifreli Bağlı Hesap Yedekleme:** AniList, MyAnimeList, Kitsu, Shikimori, Bangumi ve Simkl bağlantı token'ları (erişim ve yenileme anahtarları ile kullanıcı ID'leri) cihazdan çıkmadan önce istemci tarafında AES-256-GCM ile şifrelenir.
- **Sıfır Bilgi Güvenlik Modeli:** Supabase ve sunucu tarafı yalnızca şifreli veriyi (`CipherBlob`) görür; düz metin token'lar sunucuya asla iletilmez.
- **Şifreden Türetilen Kasa Anahtarı:** Rastgele 256-bit kasa anahtarı üretilir ve kullanıcının Kitsugi hesap şifresinden PBKDF2-SHA256 (210.000 iterasyon) ile türetilen anahtarla sarılarak saklanır (`vault_meta`). Kitsugi şifresi hiçbir yerde saklanmaz.
- **Tek Girişle Otomatik Geri Yükleme:** Yeni bir cihazda veya yeniden kurulumda Kitsugi hesabına giriş yapıldığında, bağlı tüm servis hesapları otomatik olarak çözülüp geri yüklenir; servislere tek tek tekrar giriş yapma ihtiyacı ortadan kalkar.
- **Otomatik Canlı Yedekleme:** Bağlı bir serviste token yenilendiğinde (`refresh_token` rotasyonu dahil), `LinkedAccountVault` değişikliği algılar ve 3 saniyelik güvenli gecikmeyle bulut yedeğini otomatik günceller.
- **Geçici/Hassas Alan İzolasyonu:** OAuth geçici PKCE doğrulayıcıları (`*_code_verifier`, `*_pending_redirect_uri`) ve hesap şifreleri yedekleme kapsamı dışında tutulur.

### 🔍 2. Bulut Arama Geçmişi & Kompakt Arama Çipleri (`SearchHistoryRepository.kt`, `SearchHistorySection.kt`)
- **Cihazlar Arası Geçmiş Senkronizasyonu:** Arama yapıldığında, silindiğinde veya temizlendiğinde arama geçmişi otomatik olarak Kitsugi hesabına aktarılır (`user_data` tablosu `search_history` anahtarı).
- **Zaman Damgalı İki Yönlü Birleştirme:** Giriş yapıldığında yerel ve bulut geçmişi zaman damgalarına göre akıllıca harmanlanır (`pullAndMergeSearchHistory`).
- **Kompakt Arama Geçmişi Tasarımı:** Arama ekranındaki geçmiş çipleri modernize edildi; tek harfli/gürültülü sorgular filtrelenir, ilk 8 çip kompakt blokta gösterilir ve fazla sorgular "+N / Daha az" butonuyla açılıp kapanabilir.

### ⚙️ 3. Ayarlar Menüsü Entegrasyonu (`SettingsScreen.kt`)
- **"Kitsugi Hesabı" Menü Sayfası:** Ayarlar > Hesap altına "Kitsugi Hesabı" alt sayfası eklendi.
- **Hesap Yönetimi:** E-posta ve şifre ile doğrudan kayıt olma, giriş yapma, anlık eşitleme ("Şimdi eşitle") ve güvenli çıkış yapma kontrolleri sağlandı.

### 🖼️ 4. Orijinal Formatında Medya İndirme & Paylaşma (`KitsugiImageDownloadHelper.kt`)
- **Magic Number Format Tespiti:** İndirilen görsellerin gerçek MIME ve formatı bayt başlıklarından (magic number) tespit edilir.
- **Hareketli GIF & Şeffaf PNG Koruması:** Animasyonlu GIF'lerin düz JPEG'e dönüştürülmesi önlendi; GIF'ler `.gif`, PNG'ler `.png`, WebP'ler `.webp` olarak orijinal kalitesinde kaydedilir ve paylaşılır.

### 📦 5. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.224-foss.apk`).

---

## 🇬🇧 English (v2.4.224)

### 🔐 1. Kitsugi Account Synchronization & Secure Vault (`LinkedAccountVault.kt`, `KitsugiAccountRepository.kt`, `KitsugiAccountContent.kt`)
- **End-to-End Encrypted Linked Account Backup:** Access/refresh tokens and user IDs for connected platforms (AniList, MyAnimeList, Kitsu, Shikimori, Bangumi, Simkl) are encrypted on-device via AES-256-GCM before syncing to the cloud.
- **Zero-Knowledge Security Architecture:** Supabase stores only ciphertext blobs; raw authentication tokens are never exposed to the server.
- **Key Wrapping with PBKDF2:** A random 256-bit vault key encrypts token data and is wrapped using a key derived from the user's Kitsugi account password via PBKDF2-SHA256 (210,000 iterations). Passwords are never stored.
- **Seamless Single-Sign-On Restoration:** Logging into a Kitsugi account on a new device automatically unwraps the vault and restores all connected service integrations without manual re-authentication.
- **Real-Time Auto-Backup:** When service tokens refresh, changes trigger an automatic debounced backup (3-second window) to keep cloud vaults up-to-date.
- **Transient Field Isolation:** Ephemeral OAuth data (`*_code_verifier`, `*_pending_redirect_uri`) and user passwords are strictly excluded from backups.

### 🔍 2. Cloud Search History & Compact History Chips (`SearchHistoryRepository.kt`, `SearchHistorySection.kt`)
- **Cross-Device Search Synchronization:** Search additions, deletions, and purges automatically sync with cloud storage under `search_history`.
- **Timestamped Bidirectional Merge:** Account sign-in performs a smart conflict-free merge of local and remote search entries (`pullAndMergeSearchHistory`).
- **Compact Search UI Redesign:** Replaced full-size chips with streamlined, responsive chips. Single-character queries are omitted, queries collapse to 8 items with "+N / Show less" expansion.

### ⚙️ 3. Settings Interface Integration (`SettingsScreen.kt`)
- **"Kitsugi Account" Section:** Added a dedicated "Kitsugi Account" settings destination under Settings > Account.
- **Account Actions:** Sign-up, sign-in, manual forced synchronization ("Sync now"), and secure sign-out.

### 🖼️ 4. True Format Media Preservation (`KitsugiImageDownloadHelper.kt`)
- **Magic Number File Type Detection:** Inspects binary headers to accurately detect image MIME types and extensions.
- **Animated GIF & PNG Preservation:** Prevents animated GIFs and transparent PNGs from being flattened into JPEGs; saves and shares media in its native format (.gif, .png, .webp).

### 📦 5. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.224-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.223)

### 👤 1. Profil Favorileri & Listem Görünüm Uyarlaması (`ProfileFavoritesListemStyle.kt`, `AniListFavoritesTab.kt`, `KitsugiUserMediaListScreen.kt`)
- **Listem Kart Düzenleri:** Kendi profilindeki ve diğer kullanıcıların profillerindeki favori kartları (Anime, Manga, Karakter, Kişi, Stüdyo) artık Listem ekranının kart düzenlerini (`compact`, `comfortable`, `minimalist`, `large`, `grid_2col`) ve kullanıcının Listem ayarlarını kullanır.
- **Otomatik Sayfalama (Sonsuz Kaydırma):** Eski "Daha Fazla Yükle" butonu kaldırıldı; sayfa aşağı kaydırıldıkça sonraki sayfalar akıcı bir şekilde otomatik yüklenir.
- **Dinamik Alt Kontroller:** Sol altta kategori seçici çipi (Anime, Manga, Karakterler, Ekip, Stüdyolar) ve sağ altta yukarı kaydırma butonu eklendi; aşağı kaydırırken kontroller otomatik gizlenir.
- **Diğer Kullanıcıların Medya Listesi:** Diğer kullanıcıların anime/manga listeleri Listem'in yerel kart ve durum başlığı bileşenlerine geçirildi; kart düzeni Listem tercihlerine bağlandı.
- **Stüdyo Görsel Yedekleri:** Görseli bulunmayan yapımcı/stüdyo kartlarında boş kutular yerine stüdyo adının baş harflerini içeren avatar rozetleri gösterilir.

### 🌊 2. Cloudstream Akış Boru Hattı & Kısmi Sonuç Kurtarma (`CsStreamRunner.kt`, `StreamViewModel.kt`)
- **Kısmi Sonuç Kurtarma (Partial Sink):** Eklentiler video linklerini bulmuş olsa bile işlem süresi zaman aşımına (timeout) uğradığında bulunan tüm verilerin silinmesi sorunu kökten çözüldü. Artık zaman aşımı gerçekleşse bile o ana kadar elde edilen kaynaklar (`partialSink`) kurtarılarak kullanıcıya sunulur.
- **Paralel HEAD Canlılık Doğrulaması:** Medya linklerinin canlılık kontrolü eski sıralı (8 sn × N) yapıdan kurtarılarak 6 eşzamanlı worker ve 5 sn zaman aşımıyla paralel hale getirildi. 5 ölü CDN'in 40 saniye boyunca tüm aramayı kilitleyip bütçeyi patlatması engellendi.
- **Zaman Kutulu & Paralel Varyant Araması:** 18 arama varyantı tek tek taranmak yerine 3'lü gruplar halinde paralel taranır. Katı zaman kutusu (normalde 20 sn, Cloudflare korumalı sitelerde 40 sn) uygulanarak bütçenin kalan kısmı `load` ve `loadLinks` (video çıkarma) aşamalarına saklanır.
- **Üst Bütçe Uyarlaması:** Tek sağlayıcı zaman aşımı 40 sn'den 75 sn'ye, Cloudflare korumalı eklentiler için 90 sn'den 120 sn'ye çıkarıldı. Eklentiler paralel çalıştığı için kullanıcı fazladan bekletilmez.
- **Doğrulama Sonrası Gerçek Temizleme:** `CsPluginStatusTracker` kayıtları `api.name` ile tutulduğundan, Cloudflare WebView doğrulamasından sonra sadece `plugin.id` değil; eklenti id'si, eklenti adı ve yüklenen tüm alt API adları temizlenerek eklentinin tekrar denemede bloklu kalması önlendi.
- **Şeffaf Durum Bildirimi:** Arama başarısızlıklarında jenerik "akış bulunamadı" yerine gerçek nedenler kartlara yansıtılır (ör. *"Zaman aşımı — 3 kaynak kurtarıldı"*, *"S1E1 bölümü bulunamadı"*, *"Arama sonuç vermedi (site erişilemiyor veya CF korumalı)"*).

### 🖼️ 3. Tüm Kaynaklardan Birleşik Karakter & Kişi Galerisi (`KitsugiPersonImageAggregator.kt`, `CharacterDetailViewModel.kt`, `StaffDetailViewModel.kt`)
- **Evrensel Galeri Toplayıcı:** Karakter ve seslendirmen/personel detay sayfalarındaki galeri tek kaynak sınırından kurtarıldı.
- **Katkı Sağlayan Kaynaklar:** MAL (Jikan `/pictures` çoklu görsel), AniList, Shikimori, Kitsu (karakterler), Bangumi ve TMDB (kişiler — 12'ye kadar profil fotoğrafı). Her görsel kendi kaynak rozetiyle galeri modalında gösterilir.
- **Sıkı Kimlik & İsim Doğrulaması:** İsim bazlı çapraz aramalarda katı eşleşme (normalize eşitlik veya Doğu/Batı ad sırası jeton-kümesi) aranır; yanlış kişi/karakter görseli riski engellendi.
- **Paralel & Dayanıklı:** Tüm kaynaklar eşzamanlı ~9 sn bütçeyle sorgulanır; süre dolarsa kısmi sonuçlar gösterilir.

### 📅 4. "Yakında Yayında" Her Yerde & TMDB Haftalık Takvim Düzeltmesi (`KitsugiAiringCalendarClient.kt`, `ExploreViewModel.kt`, `AllSourcesExploreContent.kt`)
- **TMDB Günlük Yayın Düzeltmesi:** Uzun soluklu dizilerin ilk yayın yıllarının gününe düşmesi hatası giderildi; `discover/tv?air_date` filtresi haftanın her günü için ayrı sorgulanarak yapımlar gerçek yayın günlerine yerleştirildi. Bilinmeyen bölüm numaraları için "Dizi" etiketi gösterilir.
- **Ortak Geri Sayımlı Şerit (Tümü Keşfet Sayfası):** Keşfet ana sayfasında ("Tümü" modu) tüm kaynakların en üstünde tek ortak "Yakında Yayında" geri sayımlı şerit gösterilir.
- **Tüm Platformlarda Kesintisiz Geri Sayım:** Kitsu, Shikimori, Bangumi ve Simkl sekmelerine de gerçek bölüm saatli takvim verisi entegre edildi. Şerit oku doğrudan haftalık yayın takvimini açar.

### 📦 5. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.223-foss.apk`).

---

## 🇬🇧 English (v2.4.223)

### 👤 1. Profile Favorites & "My List" Layout Parity (`ProfileFavoritesListemStyle.kt`, `AniListFavoritesTab.kt`, `KitsugiUserMediaListScreen.kt`)
- **"My List" Card Layouts:** Profile favorites (Anime, Manga, Characters, Staff, Studios) for both personal and public user profiles now mirror "My List" layout styles (`compact`, `comfortable`, `minimalist`, `large`, `grid_2col`) driven by the user's persistent display preferences.
- **Infinite Scrolling:** Removed legacy "Load More" pagination buttons in favor of smooth automatic infinite scroll.
- **Dynamic Floating Controls:** Added bottom floating category selector (Anime, Manga, Characters, Staff, Studios) and quick scroll-to-top button with scroll-aware auto-hiding.
- **Public User Media Lists:** Third-party user anime/manga lists adopted "My List" native card layouts and section headers.
- **Studio Avatar Fallbacks:** Studios lacking banner artwork display stylized monogram initials rather than blank tiles.

### 🌊 2. Cloudstream Stream Pipeline & Partial Result Rescue (`CsStreamRunner.kt`, `StreamViewModel.kt`)
- **Partial Result Rescue (Partial Sink):** Fixed a critical pipeline issue where found media streams were discarded if provider execution exceeded timeout. When a timeout occurs, any streams extracted up to that point (`partialSink`) are preserved and delivered to the player/user.
- **Parallel HEAD Liveness Verification:** Replaced sequential HTTP HEAD checks (8s × N) with a 6-worker parallel pool (5s timeout). Prevents dead CDNs from burning the provider budget and triggering false negatives.
- **Time-Boxed Parallel Search:** Query variants are now dispatched concurrently in batches of 3 within a strict time-box (20s standard, 40s CF). Early pruning preserves remaining execution budget for page loading and stream extraction.
- **Extended Provider Budget:** Adjusted provider execution budget to 75s (standard) and 120s (Cloudflare-protected). As providers execute concurrently, overall user latency is unaffected.
- **Complete Verification Reset:** Properly evicts `api.name`, `plugin.name`, and `plugin.id` from `CsPluginStatusTracker` following successful Cloudflare WebView verification, preventing false "blocked" state loops on retries.
- **Diagnostic UI Badging:** Cards now articulate the exact underlying cause instead of generic "no streams found" (e.g., *"Timeout — 3 streams rescued"*, *"Episode S1E1 not found"*, *"Search returned 0 results (unreachable or CF/WAF)"*).

### 🖼️ 3. Multi-Source Aggregated Character & Person Gallery (`KitsugiPersonImageAggregator.kt`, `CharacterDetailViewModel.kt`, `StaffDetailViewModel.kt`)
- **Universal Image Aggregator:** Character and staff/voice actor gallery drawers aggregate photos across all connected databases.
- **Integrated Sources:** MAL (Jikan `/pictures`), AniList, Shikimori, Kitsu (characters), Bangumi, and TMDB (persons — up to 12 profile stills). Every image features its respective platform badge.
- **Strict Identity Matching:** Strict normalization and Japanese naming order token-set matching prevent cross-entity image misattribution.
- **Concurrent & Resilient:** All sources query concurrently with a ~9s budget; partial results are gracefully rendered upon deadline.

### 📅 4. "Airing Soon" Everywhere & TMDB Weekly Schedule Fix (`KitsugiAiringCalendarClient.kt`, `ExploreViewModel.kt`, `AllSourcesExploreContent.kt`)
- **TMDB Per-Day Schedule Fix:** Fixed long-running series falling onto old historical premiere dates by querying `discover/tv?air_date` day-by-day for the current week. Unknown episode numbers clearly display "TV" badge.
- **Unified Countdown Shelf on All Explore:** A shared, authoritative "Airing Soon" countdown shelf is rendered directly above sources on the "All" explore tab.
- **Cross-Platform Parity:** Kitsu, Shikimori, Bangumi, and Simkl now feature genuine countdown airing data. Shelf header navigates directly to the weekly calendar view.

### 📦 5. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.223-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.222)

### 🌟 1. Evrensel Sosyal Birleştirme & Platform Rozetleri (`KitsugiMediaSocialClient.kt`, `ReviewsTab.kt`, `KitsugiReviewCard.kt`)
- **Evrensel İncelemeler & Konular:** Hangi kaynakta olunursa olunsun (AniList, MAL, TMDB, SIMKL, Kitsu, Shikimori, Bangumi), çapraz kimlikler çözülerek (ARM / KitsugiIdResolver / Bangumi cross-id) ilgili tüm platformlardaki incelemeler, forum konuları ve aktiviteler tek havuzda birleştirilir.
- **Platform Rozetleri:** Yorum kartları, forum konuları ve aktivitelerde kullanıcı adının yanında platform logosu (`KitsugiPlatformLogo`) gösterilir.
- **Akıllı Beğeni & Profil Yönlendirmesi:** AniList kullanıcıları doğrudan uygulama içi profile yönlendirilir; diğer platform kullanıcıları dış web profillerine açılır. Beğeni desteği AniList kaynaklı gönderiler için sorunsuz çalışır.
- **Çok Dilli İncelemeler:** TMDB incelemelerindeki dil kilidi (`language=tr-TR`) kaldırıldı; tüm dillerdeki incelemeler orijinal dil rozetiyle (TR, EN, JA, DE...) gelir, sayfalama ve çeviri butonu entegre çalışır.

### 🎬 2. Stüdyo & Yapımcı Sayfalarında Keşfet Paritesi (`StudioDetailPage.kt`, `StudioDetailComponents.kt`)
- **Keşfet Tasarım Dili:** Stüdyo detay sayfaları ana keşfet açılır sayfalarıyla birebir görsel ve işlevsel pariteye kavuşturuldu.
- **Grid ↔ Liste Modu & Kalıcı Tercih:** Keşfet poster kartları ile degrade şeritli ızgara modu ve detaylı liste modu arasında geçiş desteği.
- **Duyarlı Kolon Düzeni:** Dikey ve yatay ekran modlarında genişliğe göre akıllı 3–5 kolon hesabı.
- **Gelişmiş Filtreleme:** Üst çip şeridi (Tümü / Anime / Manga / Film / Dizi) ve "Filtre ve Sıralama" bottom sheet penceresi.

### 🎭 3. TMDB Kişi Adlarında Romaji & "Yönetmenlik" Çevirisi (`TmdbCreditsClient.kt`, `strings.xml`)
- **Romaji Önceliği:** TMDB'den Japonca (CJK) gelen yönetmen, oyuncu ve ekip adları için `also_known_as` listesinden Latin/romaji isimler otomatik seçilir (`romanizedName`).
- **Sınırlı Bellek Önbelleği:** Kişi sorguları `BoundedCache` (LRU 500) ile tutulur, yanıt başına en fazla 12 sorgu yapılarak ağ yükü korunur.
- **Rol Çevirisi:** "Directing" görevi için `staff_role_directing` ("Yönetmenlik") çevirisi eklendi.

### 🧩 4. Kitsu Karakter Detayı & MAL İlişki Kapak İyileştirmeleri (`KitsugiCharacterClient.kt`, `KitsugiMediaRelationsClient.kt`)
- **Kitsu Karakter Kurtarma:** Kitsu API'sinden 404/429 dönen veya eşleşmesi eksik yeni yapımdaki karakterlerde kart görseli, AniList isim araması ve Jikan yedekleri devreye girerek "Detaylar yüklenemedi" hatası engellendi.
- **MAL İlişki Kapakları:** Jikan ilişkilerindeki eksik kapak görselleri AniList üzerinden toplu sorguyla çekilerek gri placeholder'lar kaldırıldı ve afişler eklendi.

### 🏮 5. Bangumi Etiket Sözlüğü, Başlık Önbelleği & Tıklanabilir Stüdyolar (`BangumiTagDictionary.kt`, `BangumiTitleCache.kt`, `KitsugiBangumiDetailClient.kt`)
- **Kapsamlı Etiket Sözlüğü:** 523 grup ve 2182 yazım içeren otoriter sözlük; takvim etiketleri regex ile çözülür, arayüz diline göre gösterilir.
- **Kalıcı Latin Başlık Önbelleği:** Keşfet şeritleri ve ilişkilerdeki CJK başlıklar için kalıcı Latin önbellek (`BangumiTitleCache`, LRU 1500).
- **Tıklanabilir Kurumlar & Kanonik Adlar:** Bangumi yapımcı/stüdyoları kişi arama API'siyle eşlenip tıklanabilir hale getirildi; tanınan stüdyolar için 117 kanonik Latin yazım eşlemesi uygulandı.

### 🛡️ 6. Bellek Sızıntısı & Şişme Koruması (`BoundedCache.kt`, `KitsugiMemoryGuard.kt`)
- **LRU Sınırlı Önbellekler:** 18 dosyadaki 30'dan fazla sınırsız harita LRU sınırlı `BoundedCache`'e geçirildi.
- **Bellek Bekçisi:** Android'in `onTrimMemory`/`onLowMemory` geri çağrıları dinlenir; 15 saniyelik bekçi %80 heap doluluğunda %50, %90 doluluğunda %100 tahliye uygular.
- **Negatif Fragman Önbelleği & Çökme Raporu:** Boş fragman sonuçlarının önbelleklenmesi düzeltildi; çökme raporlarına önbellek doluluk metrikleri eklendi.

### ⚡ 7. Derleme Performansı & Gradle İyileştirmeleri (`gradle.properties`)
- **Kotlin Incremental Build:** `kotlin.incremental=true` ve `kotlin.incremental.useClasspathSnapshot=true` aktif edildi.
- **Paralel Görevler:** `kotlin.parallel.tasks.in.project=true` ve `android.r8.optimizedResourceShrinking=true` eklendi.

### 📦 8. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.222-foss.apk`).

---

## 🇬🇧 English (v2.4.222)

### 🌟 1. Universal Social Merge & Platform Badges (`KitsugiMediaSocialClient.kt`, `ReviewsTab.kt`, `KitsugiReviewCard.kt`)
- **Cross-Platform Reviews & Topics:** Regardless of current media source (AniList, MAL, TMDB, SIMKL, Kitsu, Shikimori, Bangumi), cross-ID resolution (ARM / KitsugiIdResolver / Bangumi cross-id) aggregates reviews, discussion topics, and activities from all connected databases into a unified feed.
- **Platform Badges:** Display platform logos (`KitsugiPlatformLogo`) beside author usernames in review cards, discussion topics, and activity feeds.
- **Intelligent Likes & Profile Navigation:** In-app navigation for AniList profiles; external browser intent for third-party platforms. Native liking enabled for AniList-sourced items.
- **Multilingual Reviews:** Unlocked TMDB review language restrictions; reviews in all languages load with original language badges (TR, EN, JA, DE...) and integrated pagination/translation.

### 🎬 2. Studio & Producer Explore Page Parity (`StudioDetailPage.kt`, `StudioDetailComponents.kt`)
- **Explore Design Parity:** Studio detail screens upgraded to mirror explore category sheets with consistent typography and padding.
- **Grid ↔ List View Persistence:** Toggle between poster gradient cards and detailed list rows with persistent user preferences.
- **Responsive Layout:** Dynamic 3-5 column layout calculation for portrait and landscape orientations.
- **Filters & Sorting:** Top chip filter row (All / Anime / Manga / Movie / TV) plus comprehensive sorting bottom sheet.

### 🎭 3. TMDB Romaji Person Names & Directing Translation (`TmdbCreditsClient.kt`, `strings.xml`)
- **Romaji Priority:** Automatic Latin/romaji name selection from `also_known_as` for CJK cast and crew in TMDB (`romanizedName`).
- **Memory-Bounded Cache:** Cached in `BoundedCache` (LRU 500), limited to 12 lookups per credits response.
- **Directing Role:** Added `staff_role_directing` translation ("Yönetmenlik").

### 🧩 4. Kitsu Character Detail & MAL Relations Covers (`KitsugiCharacterClient.kt`, `KitsugiMediaRelationsClient.kt`)
- **Kitsu Character Resiliency:** Fallback to card artwork, AniList name search, and Jikan search on Kitsu 404/429 errors.
- **MAL Relations Cover Art:** Bulk AniList lookup for Jikan relation items to replace grey placeholders with posters.

### 🏮 5. Bangumi Tag Dictionary & Title Cache (`BangumiTagDictionary.kt`, `BangumiTitleCache.kt`)
- **Tag Dictionary:** Authoritative dictionary of 523 groups / 2182 variants with calendar regex translation.
- **Persistent Latin Title Cache:** High-speed LRU-1500 caching for titles across explore and relations.
- **Interactive Studios:** Resolution of Bangumi company entities into clickable studio profiles with 117 canonical name mappings.

### 🛡️ 6. Memory Guard & Leak Protections (`BoundedCache.kt`, `KitsugiMemoryGuard.kt`)
- **Bounded LRU Caches:** Replaced 30+ unbounded maps across 18 files with `BoundedCache`.
- **System Memory Hooks:** Active hooks into `onTrimMemory`/`onLowMemory` with 15s heap watchdog.

### ⚡ 7. Build Performance & Gradle Tuning (`gradle.properties`)
- **Kotlin Incremental Build:** Enabled `kotlin.incremental=true` and `kotlin.incremental.useClasspathSnapshot=true`.
- **Parallel Tasks:** Added `kotlin.parallel.tasks.in.project=true` and `android.r8.optimizedResourceShrinking=true`.

### 📦 8. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.223-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.221)

### 🌐 1. Çok Dilli İncelemeler & Orijinal Dil Rozetleri (`TmdbCreditsClient.kt`, `KitsugiReviewCard.kt`)
- **Tüm Dillerde İncelemeler:** TMDB inceleme isteklerindeki sabit `language=tr-TR` filtresi kaldırıldı. İncelemeler hangi dilde yazıldıysa o dilde eksiksiz listelenir.
- **TMDB İnceleme Sayfalaması:** "Tümünü Gör" ekranında TMDB incelemeleri için sayfalama desteği (`page`) eklendi.
- **Orijinal Dil Rozeti:** İnceleme kartlarına orijinal dil kodu rozeti (EN, JA, DE vb.) eklendi ve çeviri motoruna kaynak dil olarak aktarıldı.

### 💬 2. Evrensel Tartışma Konuları & Aktiviteler (`KitsugiMediaSocialClient.kt`, `ReviewsTab.kt`)
- **TMDB & SIMKL Kısıtlaması Kaldırıldı:** Kaynak fark etmeksizin (TMDB, SIMKL, Kitsu, Shikimori, Bangumi, MAL) gerçek MAL/AniList kimlik eşlemesi çözülebildiğinde forum konuları ve aktiviteler dinamik olarak yüklenir; çözülemezse bölüm gizlenir ("varsa görünür" kuralı).

### 🌍 3. Akıllı Çok Dilli Çeviri Motoru (`KitsugiTranslateUtils.kt`, `SettingsDataStore.kt`)
- **Dinamik Çeviri:** Sabit `en -> tr` kuralı kaldırıldı; kaynak dil = otomatik algılama veya incelemenin dili, hedef dil = kullanıcının seçtiği hedef dil veya uygulamanın aktif dili.
- **Evrensel Web Fallback:** Google Translate uygulaması kurulu olmadığında tüm dilleri destekleyen `translate.google.com/?sl=auto&tl=...` web arayüzü açılır.
- **130+ Dil Desteği:** Ayarlar ekranındaki dil listeleri Google Translate'in 130+ diline genişletildi (kaynakta "Otomatik (Algıla)" seçeneği dahil).

### 📚 4. Keşfet: Yeni Yerel Ek Tür Rafları (`NativeKindExploreSections.kt`, `ExploreViewModel.kt`)
- **MAL:** "Manhwa & Manhua" ve "Noveller & Light Novel" rafları (Jikan filtreli).
- **Shikimori:** "Manhwa & Manhua" (`kinds=manhwa,manhua`) ve "Noveller & Light Novel" (`kinds=light_novel,novel`) rafları.
- **AniList:** "Noveller & Light Novel" (`format: NOVEL`) rafı.
- Desteklemeyen kaynaklarda (Kitsu) raflar temiz bir şekilde gizlenir. Hem "Tümü" hem tek kaynak modunda "Tümünü Gör" sayfalaması ve vitrin puanlaması entegre edildi.

### 🎨 5. Stüdyo & Yapımcı Sayfası Keşfet Paritesi (`StudioDetailPage.kt`, `StudioDetailComponents.kt`)
- **Keşfet Sayfası Uyumlu Düzen:** Keşfet açılır sayfalarıyla ("Shikimori · En İyi Animeler") birebir aynı kolon hesabı, dikey/yatay mod duyarlılığı ve akıcı sayfa yapısı.
- **Gelişmiş Filtreleme & Görünüm:** Emojili tür filtre çipleri (Tümü, Anime, Manga, Film, Dizi), Filtre ve Sıralama bottom sheet'i, içerik sayacı, yukarı kaydırma FAB'ı, kalıcı Grid ↔ Liste geçişi ve stüdyo açıklaması ("Hakkında") desteği.

### 🏷️ 6. Ortak Kaynak Rozetleri & Logo Gösterimi (`KitsugiSourceBadge.kt`, `DetailSharedComponents.kt`)
- **Logo + Doğru İsim Rozeti:** Vitrin (hero), detay açılışındaki yükleme ekranı ve detay sayfası başlığında ham metinler yerine logo + okunabilir isim gösteren `KitsugiSourceNamePill` ve `DetailSourcePill` entegrasyonu (AniList, MAL, TMDB, Kitsu, Shikimori, Simkl, Bangumi).

### 🧪 7. Birim Testleri
- `AllSourcesExploreTest.kt` ile yerel ek tür raflarının filtreleme ve görünürlük mantığı doğrulandı.

### 📦 8. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.221-foss.apk`).

---

## 🇬🇧 English (v2.4.221)

### 🌐 1. Multilingual Reviews & Original Language Badges (`TmdbCreditsClient.kt`, `KitsugiReviewCard.kt`)
- **Unrestricted Reviews:** Removed language lock (`language=tr-TR`) from TMDB review requests so reviews in all languages load intact.
- **TMDB Review Pagination:** Added pagination (`page`) support for TMDB reviews in the "See all" sheet.
- **Language Badge:** Display original language tag (EN, JA, DE, etc.) on review cards, passed as source language to translators.

### 💬 2. Universal Forum Topics & Activities (`KitsugiMediaSocialClient.kt`, `ReviewsTab.kt`)
- **Broadened Social Tabs:** Lifted TMDB/SIMKL restrictions; whenever MAL/AniList cross-mappings resolve, discussions and activities appear seamlessly across all sources.

### 🌍 3. Smart Multilingual Translation Engine (`KitsugiTranslateUtils.kt`, `SettingsDataStore.kt`)
- **Dynamic Language Pairs:** Replaced fixed `en -> tr` with dynamic auto-detection / original review language to user-selected target (or active app locale).
- **Web Fallback:** Added graceful fallback to `translate.google.com/?sl=auto&tl=...` when Google Translate app is absent.
- **130+ Languages:** Expanded language settings catalog to over 130 Google Translate locales.

### 📚 4. Explore: Native Media Subcategory Shelves (`NativeKindExploreSections.kt`, `ExploreViewModel.kt`)
- **MAL:** "Manhwa & Manhua" and "Novels & Light Novels" shelves.
- **Shikimori:** "Manhwa & Manhua" and "Novels & Light Novels" shelves.
- **AniList:** "Novels & Light Novels" shelf.
- Automatically hidden on unsupported catalogs (Kitsu). Full support for "See all" pagination and hero scoring.

### 🎨 5. Studio Detail Page Explore Parity (`StudioDetailPage.kt`, `StudioDetailComponents.kt`)
- **Unified Explore Layout:** Matches explore category grid responsive columns, portrait/landscape continuity, and layout flow.
- **Filtering & Views:** Added emoji chips (All, Anime, Manga, Movies, Series), Filter/Sort sheet, count indicator, scroll-to-top FAB, persistent Grid ↔ List toggle, and studio "About" section.

### 🏷️ 6. Unified Source Badges & Logos (`KitsugiSourceBadge.kt`, `DetailSharedComponents.kt`)
- **Logo + Friendly Name:** Hero showcase, loading screens, and detail headers now render unified `KitsugiSourceNamePill` and `DetailSourcePill` with crisp logos and standardized labels.

### 🧪 7. Unit Tests
- Verified with `AllSourcesExploreTest.kt`.

### 📦 8. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.221-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.220)

### 🧹 1. Kitsu Bozuk Özet (Synopsis) Tespiti ve Temizliği (`KitsuSynopsisValidator.kt`, `KitsugiDetailClient.kt`)
- **Akıllı Bozuk Özet Tespiti:** Kitsu veritabanında vandalize edilmiş veya hatalı birleştirilmiş kayıtlar (örneğin 2004 yapımı anime olan "ROAR" kaydında 1997 yapımı Fox TV dizisi "Roar"ın özetinin bulunması) tespit edilir:
  - Anime veya film türündeki kayıtlarda Amerikan/İngiliz canlı-aksiyon TV dizisi tarif eden ifadeler (`is an American television show`, `aired on the Fox network` vb.) şüpheli olarak yakalanır.
  - Özet yayın tarihi bağlamında ("aired/premiered/released in 19XX") kayıt yılından 2 yıldan fazla sapan tarihler şüpheli kabul edilir.
  - Canlı-aksiyon diziler (`TvShow`), Kore dizileri ve hikaye içi geçen geçmiş yıllar yanlış-pozitif üretmeyecek şekilde korunur.
- **Doğrulanmış Kaynak İle Değiştirme:** Şüpheli Kitsu özetleri MAL/Jikan üzerinden doğrulanmış temiz özetle (`CrossSyncIdentityGuard` sıkı başlık kontrolüyle) otomatik değiştirilir; doğrulanamazsa özet boşaltılır ("Açıklama bulunamadı" gösterilir, alakasız özet engellenir).
- **Önbellek Sürümleme & Çeviri Temizliği:** Kitsu detay önbellek anahtarı `_ks1` olarak sürümlendi; eski bozuk önbellekler geçersiz kılındı. Bozuk özetlerin çevirileri `DetailCache.removeTranslation` ile bellekten temizlenir.
- **Yedek Zinciri Koruması:** AniList/MAL → Kitsu arama zinciri ve `fetchSynopsis` fonksiyonuna (listeye ekleme penceresi, hero önizleme) Kitsu özet doğrulama mantığı entegre edildi.

### 🌐 2. MyAnimeList & Jikan Çoklu Host Zinciri (`JikanGateway.kt`, `JikanSearchClient.kt`)
- **Yeni Kaynak Sıralaması:** Jikan genel API kesintileri ve kapanışına karşı arama ve keşfet zinciri optimize edildi:
  1. Resmi MAL v2 (`api.myanimelist.net`)
  2. miribyou (`https://miribyou-topaz.vercel.app` / `MIRIBYOU_BASE_URL`)
  3. Tenrai (`https://api.tenrai.org/v1`)
  4. AniList (son çare yedek)
- **Çoklu Host Desteği & Devre Kesici:** `JikanGateway` üzerinden host bazında bağımsız hız sınırlaması, 429 soğuması ve 3 ardışık hatada devre kesici (circuit breaker) eklendi.
- **Tenrai Uyumluluğu:** Tenrai API sorgu parametreleri (`sfw=true` → `sfw`) otomatik dönüştürülür.
- **İstek Tekilleştirme & Özel İstemci:** `KitsugiHttpClient.jikanClient` ile harici `RetryInterceptor` devre dışı bırakılarak gereksiz kota tüketimi ve 429 tetiklemeleri engellendi.
- **Detay ve Görsel Entegrasyonu:** `KitsugiMalDetailClient`, `CharacterDetailViewModel` ve `StaffDetailViewModel` `/pictures` istekleri merkezi `JikanGateway` üzerinden yönlendirildi.

### 🧪 3. Birim Testleri
- `KitsuSynopsisValidatorTest.kt` ile 15 birim testi tamamlandı ve başarıyla doğrulandı.

### 📦 4. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.220-foss.apk`).

---

## 🇬🇧 English (v2.4.220)

### 🧹 1. Kitsu Corrupted Synopsis Detection & Sanitization (`KitsuSynopsisValidator.kt`, `KitsugiDetailClient.kt`)
- **Smart Anomaly Detection:** Identifies vandalized or incorrectly merged Kitsu catalog entries (e.g., 2004 anime "ROAR" containing synopsis of the 1997 Fox TV live-action show "Roar"):
  - Flags western live-action TV show phrasing (`is an American television show`, `aired on the Fox network`, etc.) for Anime and Movie media types.
  - Flags broadcasting year discrepancies exceeding 2 years in release verbs (`aired/premiered/released in 19XX`).
  - Safe against false-positives for legitimate `TvShow` entries, K-dramas, and in-universe fictional dates.
- **Verified Replacement Fallback:** Suspicious synopses are replaced by verified summaries fetched from MAL/Jikan guarded by `CrossSyncIdentityGuard.titlesLookRelated`; unverified synopses are safely cleared to avoid misleading descriptions.
- **Cache Versioning & Translation Eviction:** Kitsu detail cache keyed with `_ks1` to bypass contaminated cache entries. Stale synopsis translations are evicted via `DetailCache.removeTranslation`.
- **Fallback Chain Protection:** Integrated into the AniList/MAL → Kitsu fallback chain (15s timeout) and `fetchSynopsis` (add-to-list dialog, hero preview).

### 🌐 2. MyAnimeList & Jikan Multi-Host Chain (`JikanGateway.kt`, `JikanSearchClient.kt`)
- **Optimized Fallback Hierarchy:** Resilient routing against public Jikan downtime:
  1. Official MAL v2 (`api.myanimelist.net`)
  2. miribyou (`https://miribyou-topaz.vercel.app` / `MIRIBYOU_BASE_URL`)
  3. Tenrai (`https://api.tenrai.org/v1`)
  4. AniList (ultimate fallback)
- **Multi-Host Gateway & Circuit Breakers:** `JikanGateway` now coordinates host-level rate limiting, 429 cool-down, and circuit breakers (tripping on 3 consecutive server/network failures).
- **Tenrai Compatibility:** Seamless parameter translation (`sfw=true` → `sfw`).
- **Quota Leak Fix:** Introduced `KitsugiHttpClient.jikanClient` without `RetryInterceptor` to prevent quota burning outside the gateway.
- **Detail & Asset Gateway Routing:** `KitsugiMalDetailClient`, `CharacterDetailViewModel`, and `StaffDetailViewModel` `/pictures` requests now traverse `JikanGateway`.

### 🧪 3. Unit Tests
- 15 unit tests in `KitsuSynopsisValidatorTest.kt` passed successfully.

### 📦 4. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.220-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.219)

### 🔑 1. Harici API Entegrasyonları Doğrulama & Yönetim (`KitsugiIntegrationsSettingsDialog.kt`)
- **Canlı Anahtar Doğrulama:** TMDB ve Fanart.tv kişisel API anahtarları için anlık servis bağlantı testi eklendi; yalnızca doğrulanmış anahtarlar kaydedilir.
- **Güvenli Temizleme:** Alan temizlendiğinde uygulamanın yerleşik ortak anahtarına geri dönüş sağlanır.
- **MDBList Anahtar Desteği:** Giriş sırasında boşluk kırpma ve doğrulama eklendi.
- **Şifre / Maske Görünürlük Kontrolü:** API anahtarlarını gizleme ve gösterme kontrolleri entegre edildi.

### 📊 2. MDBList TMDB ID Yedek Araması & Akıllı Ayrıştırma (`MdbListClient.kt`)
- **Otomatik TMDB Yedeği:** IMDb kimliği bulunmayan içeriklerde pozitif TMDB ID ile otomatik puan sorgulama desteği eklendi.
- **Genişletilmiş Yanıt Formatı:** Hem yeni dizi hem de eski üst-seviye alan formatları eksiksiz ayrıştırılır ve önbelleğe alınır.
- `ApiResultDetailViewModel` ve `MediaEntryDetailViewModel` üzerinde IMDb/TMDB puan çağrıları bu yedek mekanizmasına bağlandı.

### 🎬 3. TMDB Özellik Kapıları & Televizyon Ağları (`TmdbMediaDetailClient.kt`)
- **Granüler İstek Kapıları:** Ayrıntı, afiş/arka plan, fragman, yapım şirketi, ağlar, oyuncu/ekip ve koleksiyon ayar kapıları API isteklerine ve model alanlarına bağlandı.
- **Yayın Ağları (Networks):** TV içeriklerinde yapımcı televizyon kanalları ve yayın ağları detay kartlarına eklendi.
- **Dinamik Keşfet Senkronizasyonu:** Keşfet sayfasındaki modern TMDB içerik rafları ayar değişikliğinde anında güncellenir (`tmdbModernHomeEnabled`).

### 🛡️ 4. Hassas URL Maskeleme & İstek Güvenliği (`SensitiveUrlRedactor.kt`, `KitsugiApiBase.kt`)
- **Günlük Güvenliği:** Ağ isteklerinde ve hata kayıtlarında API anahtarları, token'lar ve hassas parametreler otomatik olarak maskelenir (`***`).

### 🧪 5. Test Kapsamı
- `MdbListClientTest.kt` ve `TmdbMediaDetailClientTest.kt` ile MDBList URL/ayrıştırma ve TMDB özellik filtreleme mantığı için kapsamlı birim testleri eklendi.

### 📦 6. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.219-foss.apk`).

---

## 🇬🇧 English (v2.4.219)

### 🔑 1. External API Integration Validation & Management (`KitsugiIntegrationsSettingsDialog.kt`)
- **Live Key Validation:** Added in-place connection verification for personal TMDB and Fanart.tv API keys; unverified keys are never saved.
- **Safe Key Reset:** Clearing key fields safely falls back to the app's built-in shared credentials.
- **MDBList Trimming & Checks:** Key whitespace trimmed and sanitized automatically.
- **Masking Controls:** Added show/hide toggle buttons for sensitive key fields.

### 📊 2. MDBList TMDB ID Fallback & Smart Response Parsing (`MdbListClient.kt`)
- **Automatic TMDB Fallback:** When IMDb identifiers are absent, ratings are queried via positive TMDB IDs (`/ratings/tmdb/...`).
- **Unified Schema Support:** Smoothly parses both modern list schemas and legacy top-level rating structures with caching.
- Integrated into `ApiResultDetailViewModel` and `MediaEntryDetailViewModel`.

### 🎬 3. TMDB Feature Gating & TV Networks (`TmdbMediaDetailClient.kt`)
- **Granular Feature Toggles:** Respects user toggles for basic info, release dates, artwork, trailers, productions, networks, credits, more-like-this, and collections.
- **Broadcast Networks:** Added TV broadcast networks to detail metadata cards.
- **Instant Explore Sync:** Modern TMDB rails in Explore react immediately to setting changes (`tmdbModernHomeEnabled`).

### 🛡️ 4. Sensitive URL Redaction & Logging Safety (`SensitiveUrlRedactor.kt`, `KitsugiApiBase.kt`)
- **Log Sanitization:** Automatically masks API keys, secrets, and authorization tokens in URLs across logs and exceptions (`***`).

### 🧪 5. Test Suite
- Added regression unit tests in `MdbListClientTest.kt` and `TmdbMediaDetailClientTest.kt`.

### 📦 6. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.219-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.218)

### 🔍 1. Cloudstream Canlı İzleme & Tanı Raporlama Sistemi (`CsTrace`)
- **Otomatik Canlı Kayıt (`CsTrace.kt`, `CsTraceCore.kt`):** Eklenti yükleme, arama, başlık varyantları, eşleşme, bölüm bulma, doğrudan link ve embed çıkarma, `loadLinks` aşamaları `filesDir/cs_trace/` altında otomatik olarak kalıcı günlüğe yazılır.
- **Eşleşme Reddi Şeffaflığı:** Hiçbir adayın eşleşme eşiğini geçemediği durumlarda hedef başlık, sezon/bölüm, yıl ve en iyi 5 adayın benzerlik skorları kaydedilir ("neden 0 sonuç" durumu netleşti).
- **Hata & Çökme Takibi:** `loadLinks` zaman aşımları, DEX yükleme hataları ve kritik istisnalar izleme sistemine aktarılır.
- **Yanıltıcı Durum Mesajı Düzeltildi:** `domain_fixes.json` engelli listesindeki eklentiler denenirken tracker'a yazılan yanıltıcı "skip" yerine durum yalnızca izleme günlüğüne yazılır.
- **Canlı Rapor Paylaşımı:** Ayarlar > Eklenti Tanı ekranına **"Canlı İzleme Raporunu Paylaş (.md)"** düğmesi eklendi; oluşturulan Markdown raporu Android paylaşım penceresiyle doğrudan iletilebilir.

### 🎬 2. Kapsamlı Fragman Zinciri (`DetailTrailerFallback.kt`)
- **Çok Katmanlı Yedek Mekanizması:** Kaynak kendi YouTube fragmanını sağlamadığında arka planda sırayla **TMDB → Diğer Metadata Kaynakları (AniList, Jikan, Kitsu) → Cloudstream Eklentileri** (arama ve load yanıtındaki fragmanlar) taranır.
- **Dinamik Ön İzleme Kartı:** Bulunan fragman detay durumuna ve önbelleğe yazılır; `ApiResultDetailViewModel` ve `MediaEntryDetailViewModel` üzerinden "Ön izleme" kartı otomatik olarak belirir.

### 🔘 3. İzle Butonu Tasarım Hizalaması (`KitsugiUiverseGlowButton.kt`)
- **Tek Tip Eylem Butonları:** `KitsugiDetailActionButton` artık `ApiActionButton` ve `ActionButton` ile tam uyumlu: 999dp hap form, 14×10dp dolgu, `labelMedium` tipografi ve 16dp ikon ile görsel bütünlük sağlandı.

### ❌ 4. Açılır Sayfa ve Oynatıcı Kapatma Butonu Düzenlemeleri
- **Player Dışındaki Yüzen Çarpılar Temizlendi (`KitsugiSheetOrDialog.kt`):** v2.4.215'te eklenen evrensel yüzen çarpı kaldırıldı; sayfaların kendi doğal başlık yapıları korundu.
- **Player İçi Açılır Sayfalara Çarpı Butonu:** `PlayerSheet` sürükleme kolu satırına sağa hizalı kapatma çarpısı eklendi (Altyazılar, Ses, Kalite/Kaynak, Bölümler, Hız, Ekran Görüntüsü vb.).
- `EpisodeListDialog` ve `QualityProfileDialog` için tek dokunuşluk kapatma butonları eklendi.

### 🔙 5. Oynatıcı Geri Tuşu ve Jest Gezinme Zinciri (`KitsugiFullscreenPlayerActivity.kt`)
- **Deterministik Çıkış Zinciri:** Jest gezinme, 3 tuşlu çubuk ve donanım geri tuşlarının tamamında çalışan `OnBackPressedDispatcher` callback'i kuruldu:
  - `dialog` → `panel` → `sheet` → `kontrolleri gizle` → `oynatıcıdan çık`.

### 📦 6. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.218-foss.apk`).

---

## 🇬🇧 English (v2.4.218)

### 🔍 1. Cloudstream Live Trace & Diagnostic Reporting (`CsTrace`)
- **Automated Live Tracing (`CsTrace.kt`, `CsTraceCore.kt`):** Plugin loading, search query variants, title matching, episode extraction, direct links, embed scrapers, and `loadLinks` phases are persisted to `filesDir/cs_trace/`.
- **Match Rejection Transparency:** When candidate items fail matching thresholds, the target title, season, episode, year, and similarity scores of top 5 candidates are logged to reveal the exact reason behind empty results.
- **Fail-Safe Exception Recording:** `loadLinks` timeouts, DEX loading exceptions, and critical errors are automatically captured.
- **Accurate Status Attribution:** Suppressed misleading "skip" tracker entries for plugins on domain blocklists that are still actively probed.
- **One-Tap Report Sharing:** Added **"Share Live Trace Report (.md)"** button in Settings > Plugin Diagnostics to export markdown traces via Android share sheet.

### 🎬 2. Universal Trailer Fallback Pipeline (`DetailTrailerFallback.kt`)
- **Multi-Source Fallback Chain:** If the primary source lacks a YouTube trailer, a fallback ladder queries **TMDB → Metadata Sources (AniList, Jikan, Kitsu) → Cloudstream Plugins**.
- **Dynamic Preview Card:** Resolved trailers are written to detail state and cached, automatically displaying the "Preview" card across `ApiResultDetailViewModel` and `MediaEntryDetailViewModel`.

### 🔘 3. Action Button Visual Alignment (`KitsugiUiverseGlowButton.kt`)
- **Unified Action Styling:** `KitsugiDetailActionButton` now aligns with `ApiActionButton` and `ActionButton`: 999dp pill form factor, 14×10dp padding, `labelMedium` typography, and 16dp icon.

### ❌ 4. Sheet & Player Close Button Refinements
- **Removed Extraneous Floating Close Buttons (`KitsugiSheetOrDialog.kt`):** Reverted universal floating close button outside the player, preserving native sheet headers.
- **Player Sheet Close Buttons:** Added unified close buttons to the `PlayerSheet` header bar across all bottom sheets (Subtitles, Audio Tracks, Quality/Sources, Episodes, Playback Speed, Screenshot).
- Added close buttons to `EpisodeListDialog` and `QualityProfileDialog`.

### 🔙 5. Player Back Gesture & Navigation Dispatcher Chain (`KitsugiFullscreenPlayerActivity.kt`)
- **Deterministic Back Key Handling:** Integrated an `OnBackPressedDispatcher` callback supporting gesture navigation, 3-button navigation, and hardware back keys:
  - `dialog` → `panel` → `sheet` → `hide controls` → `exit player`.

### 📦 6. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.218-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.217)

### 🚪 1. Merkezi Jikan Gateway (`JikanGateway.kt`) & MAL Trafik Optimizasyonu
- **Merkezi Kota ve Hız Sınırı (Rate Limiting):** Tüm Jikan trafiği tek bir kapıdan yönetilir; saniyede 3 ve dakikada 55 istek sınırları aşılmaz.
- **Toplu 429 Koruması:** Jikan'dan 429 (Too Many Requests) yanıtı geldiğinde tüm Jikan istekleri birlikte beklemeye geçer ve `Retry-After` başlığına uyulur.
- **Akıllı Bellek Önbelleği (In-Memory Cache):** Detay uçları 6 saat, liste ve arama uçları 30 dakika boyunca önbelleğe alınır; gereksiz ağ istekleri önlenir.
- **Eşzamanlı İstek Birleştirme (Single-Flight Deduplication):** Aynı URL'ye aynı anda gelen istekler tek bir ağ çağrısını paylaşır; sekmelerin eşzamanlı yüklenmesinde sunucuya tek istek gider.
- **Öncelikli Trafik Yönetimi:** Arka plan görevleri (profil senkronizasyonu, kimlik eşleştirme) kullanıcının aktif gezindiği ekranların kotasını tüketmez.
- **Keşfet & Arama MAL Önceliği:** MAL için Jikan önce denenir, resmi MAL API ikinci, AniList ise son yedek olarak çalışır. Karakter, ekip, ilişki, öneri ve bölüm istekleri gateway üzerinden güvenle akar.

### 🎯 2. AniList Manuel Kayıtlarında Yanlış Öneri ve Kimlik Çakışması Düzeltmesi
- **Kök Neden Giderildi (`MediaEntryDetailViewModel.kt`):** Manuel eklenen AniList kayıtlarında `malId` boş kaldığında yerel veritabanı satır numarasının (`entry.id`) dış servis kimliği gibi okunması ve başka bir yapımın MAL ID'siyle çakışarak alakasız öneriler getirmesi (ör. Rent-a-Girlfriend sayfasında farklı animelerin önerilerinin çıkması) engellendi.
- **Yalnızca Gerçek Kimlik Kullanımı:** AniList kaynaklı kayıtlarda yerel satır numarası (`entry.id`) dış kimlik olarak kabul edilmez; yalnızca geçerli bir `malId` varsa dış ID olarak kullanılır.
- **Başlık Tabanlı Arama Fallback'i (`KitsugiMediaRelationsClient.kt`):** Dış kimliği bulunmayan AniList kayıtlarında temizlenmiş başlık üzerinden doğrudan AniList API'sinde arama yapılarak (`fetchRecommendationsFromAniListBySearch`) doğru öneriler getirilir.

### 🖼️ 3. Vitrin Kaplama & Responsive Görsel Sunumu (`KitsugiHeroSection.kt`)
- **Tam Kaplayan Vitrin (Full Bleed Cover/Crop):** Bulanık arka plan dolgusu ve sığdırma (fit) yapısı kaldırılarak resmin tüm vitrin alanını kaplaması sağlandı (`ContentScale.Crop`).
- **Responsive Kaynak Seçimi (`heroImageCandidates`):**
  - Geniş vitrin bandında (en/boy $\ge$ 1.1, yatay ekran veya tablet): yatay dikdörtgen fanart/backdrop öncelikli.
  - Dikey telefon modunda (en/boy < 1.1): dikey poster öncelikli.
- **Akıllı Hizalama (`BiasAlignment(0f, -0.2f)`):** Yatay arka planlar üst gövde ve yüzleri koruyacak şekilde yukarıdan hafif odaklı hizalanır; dikey posterler ise üstten kırpılır.
- **Yedek Görsel Zinciri:** Birincil görsel yüklenemediğinde otomatik olarak alternatif adaya (backdrop $\leftrightarrow$ poster) geçilir.

### 🌸 4. Bangumi Keşfet, REAL Diziler/Filmler ve Yerelleştirme Güncellemeleri
- **Canlı Çekim (REAL) Rafları (`BangumiRealExploreSections.kt`):** Bangumi TV dizileri/dramaları ve filmleri için Keşfet bölümüne özel raflar eklendi.
- **Filtreleme & Sayfalama:** Yetişkin içerik filtreleme, vitrin ve rastgele yapım seçimi, kaynak belirtimi ve "Tümünü Gör" sayfalaması entegre edildi.
- **Genişletilmiş İsim ve Çeviri Seçimi:** Karakter, seslendirmen ve ekip adları AniList'te eşleşmediğinde doğrudan Bangumi infobox yedeklerine başvurulur; AniList köprüsü 2. sayfayı da denetler.

### 🌐 5. Türkçe Akış & Başlık Temizleme Düzeltmeleri
- **Site Gürültüsü Temizleme (`stripTurkishSiteNoise`):** Türkçe sitelerin başlıklara eklediği gürültü kalıpları 16 özel regex ile ayıklandı.
- **Genişletilmiş Sorgular:** Arama sorguları 12'den 18'e çıkarıldı, geniş arama fallback'i ve Türkçe CDN/Range optimizasyonları korundu.

### 📦 6. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.217-foss.apk`).

---

## 🇬🇧 English (v2.4.217)

### 🚪 1. Centralized Jikan Gateway (`JikanGateway.kt`) & MAL Traffic Pipeline
- **Unified Rate Limiting:** All Jikan traffic routes through a single gateway strictly bounded by 3 req/sec and 55 req/min token-bucket limits.
- **Coordinated 429 Backoff:** Upon receiving HTTP 429 (Too Many Requests), all queued and concurrent Jikan requests hold together, respecting `Retry-After`.
- **In-Memory Caching:** Detail endpoints cached for 6 hours; list and search endpoints cached for 30 minutes.
- **Single-Flight Request Deduplication:** Concurrent identical URL requests share a single underlying network call, eliminating duplicate fetches during multi-tab screen initialization.
- **Priority-Aware Traffic Budgeting:** Background workloads (profile sync, identity mapping) do not exhaust quota allocated for active user UI navigation.
- **Explore & Search MAL Priority:** Jikan is tried first for MAL, with the official MAL API and AniList serving as reliable fallbacks.

### 🎯 2. AniList Manual Entry False ID & Recommendation Mismatch Fix
- **Resolved Database Row ID Leak (`MediaEntryDetailViewModel.kt`):** Fixed an issue where manual AniList entries without a `malId` mistakenly defaulted to using their local database row index (`entry.id`) as an external API identifier, causing erroneous recommendation lookups when clashing with other titles' MAL IDs.
- **Strict External ID Resolution:** Explicitly decoupled internal database row IDs from external identifier logic for AniList sources; only valid `malId` values are passed.
- **Title-Based Recommendation Fallback (`KitsugiMediaRelationsClient.kt`):** When external IDs are absent for AniList entries, recommendations are safely retrieved by title query (`fetchRecommendationsFromAniListBySearch`) instead of leaking erroneous IDs.

### 🖼️ 3. Full Bleed Hero Cover & Responsive Presentation (`KitsugiHeroSection.kt`)
- **Full Bleed Cover/Crop:** Replaced blur-filled letterboxing with edge-to-edge `ContentScale.Crop` presentation.
- **Responsive Media Selection (`heroImageCandidates`):**
  - Wide hero banners (aspect ratio $\ge$ 1.1, landscape & tablet): landscape backdrop/fanart first.
  - Portrait mobile phone viewports: high-res vertical poster first.
- **Smart Subject Framing (`BiasAlignment(0f, -0.2f)`):** Horizontal backdrops align with a slight upward bias to keep heads and focal subjects in frame; portrait posters align to top.
- **Seamless Fallback Chain:** If the primary candidate fails to render, the hero automatically cascades to the alternate candidate (backdrop $\leftrightarrow$ poster).

### 🌸 4. Bangumi Explore REAL Shelves & Localization Updates
- **Live-Action REAL Sections (`BangumiRealExploreSections.kt`):** Introduced dedicated Explore shelves for Bangumi TV dramas and films.
- **Filtering & Pagination:** Integrated adult content filters, hero & random selection, source attribution, and "See all" pagination.
- **Expanded Infobox & Fallbacks:** Direct Bangumi fallback for character, voice-actor, and staff names when AniList lacks a match; AniList bridge checks second-page search results.

### 🌐 5. Turkish Stream Scraper & Noise Cleaning
- **Noise Stripping (`stripTurkishSiteNoise`):** Strips 16 regex noise patterns commonly found on Turkish streaming websites.
- **Broad Search & Query Expansion:** Query variants expanded to 18 with intelligent fallback matching and CDN handling.

### 📦 6. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.217-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.216)

### 🖼️ 1. Vitrin Cover / Baştan Sona Kaplama & Akıllı Görsel Seçimi
- **Tam Ekran Kaplayan Vitrin (`ContentScale.Crop`):** Önceki "Fit + bulanık dolgu" katmanları tamamen kaldırıldı. Vitrin görseli artık ekranı baştan sona kaplar, kenarlarda boşluk veya bulanık zemin kalmaz.
- **Ekran Boyutu ve Oranına Göre Akıllı Seçim (`heroImageCandidates`):**
  - Geniş vitrin bandı (yatay telefon, tablet, TV — en/boy $\ge$ 1.1) $\rightarrow$ yatay dikdörtgen fanart / backdrop öncelikli.
  - Dikey telefon vitrini (kareye yakın kutu) $\rightarrow$ dikey poster öncelikli.
- **Dinamik Hata Yedekleme Zinciri (`onLoadingFailed`):** Birincil görsel yüklenemezse anında sıradaki adaya (backdrop $\leftrightarrow$ poster) geçilir.
- **Kırpma Odak Hizalaması (`heroImageAlignment`):** Geniş vitrinde poster kullanılmak zorunda kalındığında karakter yüzü ve başlık bandının korunması için hafif yukarı bias (`BiasAlignment(0f, -0.2f)`), backdrop görsellerde merkez hizalama uygulandı.
- **Çok Yönlü TMDB Backdrop Zenginleştirmesi:** Sadece yatay mod değil, dikey tablet ve geniş vitrin bantları da TMDB fanartlarından otomatik yararlanır.
- **Yumuşatılmış Gradyanlar:** Metin okunabilirliğini korurken görselin merkezini ve detaylarını kapatmayan yumuşak renk geçişleri.
- **HeroImageSelectionTest:** 13 birim testi %100 başarıyla doğrulandı.

### 🇹🇷 2. Türkçe Akış & Eklenti Pipeline İyileştirmeleri
- **Site Başlık Kirliliği Temizleme (`stripTurkishSiteNoise`):** Türkçe dizi/film sitelerinin başlıklara eklediği reklam ve gürültü metinleri ("Türkçe Dublaj 1080p izle" vb.) 16 regex kuralı ile filtrelenerek temiz başlık eşleştirmesi sağlandı.
- **Genişletilmiş Dil & Arama Varyantları:** Başlık varyantları 8'den 12'ye, arama sorguları 12'den 18'e çıkarıldı; Türkçe karakter transliterasyonu (ğ $\rightarrow$ g, ş $\rightarrow$ s, ı $\rightarrow$ i) eklendi.
- **Geniş Arama Fallback'i (`buildBroadSearchQueries`):** Tüm varyantlar boş döndüğünde anlamlı ilk 2-3 kelime üzerinden genişletilmiş arama yapılarak farklı Türkçe adlandırmalar yakalanır.
- **45+ Yeni Türkçe CDN Tanıma:** AlionsPlayer, VidMoly, TRsTX, CloseLoad, Pichive, Rapidrame, VidGuard, TurboVid, HDVid, PlayTube, VidHide, Embedy gibi 45+ sağlayıcı `isEmbedUrl` kapsamına eklendi.
- **Gelişmiş Stream Canlılık Doğrulaması (`tryGetRangeValidation`):** HEAD 400 hatası veren akışlarda doğrudan başarısız saymak yerine GET Range header ile doğrulama yapılır; zaman aşımı süresi 6s $\rightarrow$ 8s'ye yükseltildi.
- **Türkçe Başlık Model Alanı (`titleTurkish`):** TMDB üzerinden Türkçe başlıklar çekilerek modele ve arama motoruna entegre edildi.

### 📦 3. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.216-foss.apk`).

---

## 🇬🇧 English (v2.4.216)

### 🖼️ 1. Vitrin Cover Presentation & Responsive Hero Asset Selection
- **Full Bleed Cover (`ContentScale.Crop`):** Completely removed letterboxed "Fit + ambient blur" layers. Hero artwork now stretches end-to-end across the banner container without blurred sidebars or margins.
- **Aspect-Aware Asset Prioritization (`heroImageCandidates`):**
  - Wide hero containers (landscape, tablets, TV — aspect $\ge$ 1.1) prioritize horizontal fanart / backdrop artwork.
  - Portrait mobile phone boxes prioritize vertical posters.
- **Resilient Fallback Chain (`onLoadingFailed`):** Automatically cascades to the alternative candidate (backdrop $\leftrightarrow$ poster) upon asset load failure.
- **Focal Alignment Preservation (`heroImageAlignment`):** Applies slight top-vertical bias (`BiasAlignment(0f, -0.2f)`) when vertical posters are cropped in widescreen containers to prevent cropping faces and title text.
- **Omni-Directional TMDB Backdrop Enrichment:** TMDB backdrop enrichment now activates across all orientations, including portrait tablets.
- **Softened Gradients:** Refined horizontal and vertical gradient scrims to preserve artwork visibility while ensuring text readability.
- **Unit Tests (`HeroImageSelectionTest`):** 13 unit tests passed with 100% success.

### 🇹🇷 2. Turkish Stream & Extension Scraper Pipeline Optimization
- **Stripped Turkish Title Noise (`stripTurkishSiteNoise`):** Cleans up title noise and metadata suffixes ("Türkçe Dublaj 1080p izle", etc.) via 16 regex patterns for accurate content matching.
- **Expanded Query & Language Variants:** Increased title variants from 8 to 12 and search queries from 12 to 18 with ASCII transliteration (ğ $\rightarrow$ g, ş $\rightarrow$ s, ı $\rightarrow$ i).
- **Broad Search Fallback (`buildBroadSearchQueries`):** Secondary search pass utilizing the first 2-3 significant title tokens when exact matches return empty.
- **45+ Turkish Embed & CDN Patterns:** Integrated detection for AlionsPlayer, VidMoly, TRsTX, CloseLoad, Pichive, Rapidrame, VidGuard, TurboVid, HDVid, PlayTube, VidHide, Embedy, and others.
- **Enhanced Stream Probing (`tryGetRangeValidation`):** Mitigates HEAD 400 rejections by falling back to GET requests with HTTP Range headers; increased probe timeout from 6s to 8s.
- **Turkish Title Model Field (`titleTurkish`):** Added `titleTurkish` to `KitsugiMediaDetail` populated directly from TMDB localized payloads.

### 📦 3. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.216-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.215)

### 🛡️ 1. Sessiz Çökme Kök Neden Çözümü (RenderThread SIGSEGV & WebView Teardown)
- **Çifte Destroy ve Use-After-Free Giderildi (`CsCfWarmupManager.kt`):** Çökme raporlarındaki `cr_AwContents: Application attempted to call on a destroyed WebView` uyarıları ve ardından `RenderThread` display-list oynatımı sırasında oluşan `SIGSEGV` (null+0x20) hatası tamamen çözüldü.
- **Güvenli Tek Seferlik Teardown (`releaseWebView`):** `AtomicBoolean` korumasıyla WebView yalnızca bir kez sökülür, durdurulur, `about:blank` yüklenir ve güvenle yok edilir.
- **Açılış Rahatlatması (`KitsugiApplication.kt`):** Proaktif Cloudflare warmup işlemi ilk açılışta arayüzle yarışıp frame atlamalarına (`Skipped 35 frames`) ve `DequeueBuffer` zaman aşımlarına yol açmaması için 12 saniye gecikmeli başlatıldı.

### 🎬 2. Oynatıcı, Altyazı ve PiP İyileştirmeleri
- **Dahili Türkçe Altyazı Seçim Döngüsü (`Media3PlayerEngine.kt`):** Harici altyazılar bağlanmadan önce ilk `onTracksChanged` olayında seçimin sonlanması engellendi. Hedef Türkçe parça tespit edilene kadar (maksimum 8 deneme) otomatik seçim politikası çalışır. Kullanıcının elle yaptığı altyazı seçimi korunur (`userSelectedTextTrack`).
- **Altyazı Taşma Koruması:** Dikey konum kaydırıldığında altyazının ekran dışına veya alt çubuğun altına kaçması engellendi; 4dp kenar payı ile görünür alana sabitlendi.
- **PiP Kapatıldığında Sesin Devam Etmesi Engellendi (`KitsugiFullscreenPlayerActivity.kt`):** Mini pencere kapatıldığında veya odak kaybolduğunda sesin arkadan çalmaya devam etmesi çözüldü; `invokePipAction` yedek mekanizmasıyla doğrudan ViewModel'e iletildi, `KeepAliveService.stop()` ve temiz Activity sonlandırması (`finish()`) sağlandı.
- **Kalıcı Tam Ekran (Immersive Mode):** MIUI ve HyperOS gibi arayüzlerde PiP veya odak değişimi sonrası alt gezinme çubuğunun ekranda asılı kalması giderildi (`onResume` ve `onWindowFocusChanged` üzerinde dinamik yenileme).

### 📑 3. Açılır Sayfalar (Sheet/Dialog) ve Çarpı Butonu
- **Kaydırma Kilidi Kaldırıldı (`KitsugiSheetOrDialog.kt`):** Açılır sayfalarda içerik aşağı kaydırıldığında kapatma hareketinin kilitlenmesi sorunu giderildi.
- **Evrensel Kapatma Çarpısı (`KitsugiSheetCloseButton`):** Tüm açılır sayfalara sağ üstte şık ve her koşulda çalışan kapatma çarpısı eklendi.

### 🌐 4. Bangumi Kapsamlı Türkçe Çeviri ve Metadata Zenginleştirmesi
- **95+ Tür ve Etiket Çevirisi:** Çince ve Japonca tür/etiketler (恋爱 → Romantik, 日常 → Günlük Yaşam, 京阿尼 → Kyoto Animation, 神作 → Başyapıt vb.) Türkçe'ye çevrildi (`strings.xml` ve `values-en/strings.xml`). Tür etiketine tıklanarak arama yapıldığında İngilizce API terimine dönüştürme eklendi.
- **Stüdyo Adları:** Çince/Japonca stüdyo adları Latin karşılıklarına dönüştürüldü (京都アニメーション → Kyoto Animation vb.).
- **AniList Kişi & Karakter Köprüsü (`KitsugiAniListPersonBridge`):** Bangumi'de kanji kalan karakter ve seslendirmen isimleri AniList köprüsü üzerinden otomatik olarak Romaji ve İngilizce isimlerle eşleştirildi.
- **Kadro Rolleri Çevirisi:** Episode Direction → Bölüm Yönetmeni, Animation Direction → Animasyon Yönetmeni, 監督 → Yönetmen vb. kadro rolleri eksiksiz Türkçeleştirildi.
- **İlişki ve Öneri Başlıkları:** Önerilen ve ilişkili yapımların Japonca başlıkları İngilizce/Romaji karşılıklarıyla zenginleştirildi.

### 📋 5. Çökme Raporu Dosya (.txt) Paylaşımı
- **FileProvider ile Doğrudan Dosya Paylaşımı (`KitsugiCrashRecoveryDialog.kt`):** Çökme ekranındaki "Paylaş" butonu artık düz metin yerine doğrudan `crash_log.txt` dosyasını paylaştırır.

### 📦 6. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.215-foss.apk`).

---

## 🇬🇧 English (v2.4.215)

### 🛡️ 1. Silent Crash Resolution (RenderThread SIGSEGV & WebView Teardown)
- **Resolved Double-Destroy Use-After-Free (`CsCfWarmupManager.kt`):** Fixed the root cause of `cr_AwContents: Application attempted to call on a destroyed WebView` warnings and subsequent `RenderThread` display-list recursive traversal crashes (`SIGSEGV` at `fault_addr=0x20`).
- **One-Shot Teardown Routine (`releaseWebView`):** Enforced atomic, single teardown guarded by `AtomicBoolean` ensuring WebView is unattached, halted, pointed to `about:blank`, and cleanly destroyed without race conditions.
- **Warmup Startup Throttle (`KitsugiApplication.kt`):** Deferred proactive Cloudflare warmup by 12 seconds to prevent resource contention during initial frame rendering, eliminating frame drops and buffer timeouts.

### 🎬 2. Player, Subtitle & PiP Architecture Fixes
- **Embedded Subtitle Selection Convergence (`Media3PlayerEngine.kt`):** Resolved race condition where initial `onTracksChanged` fired before sideloaded/embedded Turkish subtitle tracks arrived. Retries up to 8 times until preferred language is settled; preserves manual user track overrides (`userSelectedTextTrack`).
- **Subtitle Bounds Clamping:** Prevented vertical subtitle offset adjustments from translating text offscreen or underneath navigation chrome (clamped with 4dp margins).
- **PiP Background Audio Leak Elimination (`KitsugiFullscreenPlayerActivity.kt`):** Fixed issue where closing the mini-player left playback audio active. Replaced dangling Compose callbacks with `invokePipAction` ViewModel fallbacks, stopped `KeepAliveService`, and terminated ghost player activities.
- **Persistent Immersive Mode:** Re-applied system bar hiding on `onResume` and `onWindowFocusChanged` to fix persistent navigation bar overlays on MIUI/HyperOS devices.

### 📑 3. Sheets & Universal Close Button
- **Fixed Sheet Drag Lock (`KitsugiSheetOrDialog.kt`):** Removed bottom-sheet dismissal locking when scrolling content down.
- **Universal Close Button (`KitsugiSheetCloseButton`):** Added persistent top-right close buttons across all sheets and dialogs.

### 🌐 4. Bangumi Full Turkish Localization & Metadata Enrichment
- **95+ Tag & Genre Translations:** Localized Chinese/Japanese Bangumi genres and tags into Turkish and English with fallback dictionaries and search query translation.
- **Studio Name Latinization:** Converted Kanji studio names into recognized Latin branding (e.g. Kyoto Animation, Pony Canyon).
- **AniList Person & Character Bridge (`KitsugiAniListPersonBridge`):** Automatically matches and hydrates Kanji character and voice actor names with Romaji/English names via AniList.
- **Staff Role Translations:** Fully translated production credits (Episode Director, Animation Director, Script, etc.) into Turkish.
- **Relation & Recommendation Title Hydration:** Hydrates Japanese titles in related and recommended lists with English/Romaji metadata.

### 📋 5. Direct Crash Log File Sharing
- **Crash Log File Provider (`KitsugiCrashRecoveryDialog.kt`):** "Share" action on the crash recovery dialog now shares the actual `crash_log.txt` file via FileProvider instead of raw text.

### 📦 6. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.215-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.214)

### 🌟 1. Vitrin v2: Akıllı Seçim ve Skorlama Motoru (`HeroSelection.kt`)
- **Metrik Tabanlı Puanlama:** Vitrin (Hero Section) içerik seçimi rastgelelikten çıkarılıp ağırlıklı metrik algoritmasına geçirildi:
  $$\text{Skor} = (\text{Puan} \times 10) + (\log_{10}(\text{Üye} + 1) \times 8) + (\log_{10}(\text{Favori} + 1) \times 6) + \text{Rank} + \text{Trend (15)} + \text{Sezon (10)} + \text{Manga (8)}$$
- **Katalog ve Format Çeşitliliği:** Vitrinin tek bir türe veya kaynağa boğulması engellendi; en fazla 2 manga, en az 5 anime/dizi/film ve kaynak başına tavan sınırı (en fazla 3) ile dengeli bir vitrin akışı sağlandı.
- **Kaynaklar Arası Metrik Normalizasyonu:** TMDB gibi üye/favori bilgisi içermeyen kaynaklarda oy sayısı (`voteCount`) ve oy ortalaması logaritmik olarak dengelenerek haksız puan kaybı önlendi.
- **Görsel Kalite Filtresi:** Arka planı (backdrop) veya yüksek çözünürlüklü yatay görseli bulunmayan içerikler vitrin önceliğinde elenir.

### 🖼️ 2. Fit + Bulanık Dolgu Katmanlı Görsel Mimarisi (Kırpma Düzeltmesi)
- **Görsel Kırpma / Kesilme Sorunu Çözüldü (`KitsugiHeroSection.kt`):** Eski zorunlu kırpma (`ContentScale.Crop`) nedeniyle dikey afişlerin ve karakter yüzlerinin kesilme problemi tamamen ortadan kaldırıldı.
- **Çift Katmanlı Sunum:**
  - **Ön Plan:** Orijinal en-boy oranını tam koruyan (`ContentScale.Fit`) net ve keskin ana görsel.
  - **Arka Plan:** Ekranı boşluk bırakmadan dolduran, ortam rengini yansıtan bulanık dolgu katmanı (`HeroAmbientBackground` — Crop + Blur + Karartma).
- **Yumuşatılmış Gradyanlar:** Başlık ve meta metinlerinin okunabilirliğini artırırken görseli boğmayan yumuşak alt ve üst geçiş katmanları uygulandı.

### 🔄 3. TMDB Arka Plan Entegrasyonu ve Birleştirme (`ExploreViewModel.kt`)
- **Eksik Arka Planları Tamamlama (`enrichHeroBackdrops`):** MAL, Bangumi veya diğer kaynaklardan gelen popüler animelerde yatay backdrop bulunmadığında, TMDB üzerinden otomatik olarak yatay afiş temin edilir ve `heroBackdropOverrides` ile vitrinde kullanılır.
- **Tüm Bölümlerden Havuz Oluşturma (`ExploreScreen.kt` & `AllSourcesExplore.kt`):** Trend, Popüler, Sezonluk ve Manga listelerinin tamamından tekilleştirilmiş zengin havuz toplanarak yeni vitrin motoruna teslim edildi.

### 📊 4. Zengin Meta ve İstatistik Satırı (`HeroSectionComponents.kt`)
- **Kompakt Metrik Bilgileri:** Vitrin kartlarının altındaki meta satırına "1.2M üye • 45.3K favori" şeklinde biçimlendirilmiş topluluk metrikleri eklendi.

### 🧪 5. Testler ve Doğrulama (`HeroSelectionTest.kt`)
- Vitrin seçim ve skorlama motoru için 10 adet birim testi eklendi; kaynak çeşitliliği, skor hesaplamaları, TMDB normalizasyonu ve fallback senaryoları %100 başarıyla doğrulandı.

### 📦 6. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.214-foss.apk`).

---

## 🇬🇧 English (v2.4.214)

### 🌟 1. Vitrin / Hero Section v2: Intelligent Selection Engine (`HeroSelection.kt`)
- **Weighted Metric Scoring:** Vitrin items are now selected through a multi-factor ranking algorithm:
  $$\text{Score} = (\text{Score} \times 10) + (\log_{10}(\text{Members} + 1) \times 8) + (\log_{10}(\text{Favorites} + 1) \times 6) + \text{Rank} + \text{Trending Bonus (15)} + \text{Seasonal Bonus (10)} + \text{Manga Bonus (8)}$$
- **Format & Source Diversity:** Guaranteed balanced representation across the carousel: max 2 manga, min 5 anime/shows, and provider caps (max 3 per provider) preventing single-source domination.
- **Cross-Source Metric Normalization:** Sources lacking member/favorite counts (e.g. TMDB) are normalized using vote counts and ratings to maintain fair scoring.
- **Backdrop Quality Gate:** Automatically prioritizes titles with high-resolution backdrops or landscape banners over raw vertical posters.

### 🖼️ 2. Layered Fit + Ambient Blur Presentation (Cropping Fix)
- **Eliminated Poster Cropping (`KitsugiHeroSection.kt`):** Fixed the aggressive `ContentScale.Crop` behavior that cropped character heads and vertical poster artwork.
- **Dual-Layer Rendering:**
  - **Foreground:** Pristine uncropped asset rendered with `ContentScale.Fit`, preserving the original aspect ratio.
  - **Background:** Ambient blur fill (`HeroAmbientBackground` — Crop + Blur + Dim) that seamlessly expands to fill widescreen or ultra-wide viewport edges.
- **Refined Gradients:** Softened multi-stop linear gradients ensuring text readability without obscuring artwork.

### 🔄 3. TMDB Backdrop Enrichment (`ExploreViewModel.kt`)
- **Backdrop Fallback Hydration (`enrichHeroBackdrops`):** When popular entries from MAL or Bangumi lack native horizontal backdrops, the ViewModel dynamically queries TMDB for matching fanart/backdrops, merging them via `heroBackdropOverrides`.
- **Multi-Shelf Pool Aggregation (`ExploreScreen.kt` & `AllSourcesExplore.kt`):** Merged items across Trending, Popular, Seasonal, and Manga shelves into an enriched pool fed into the selection engine.

### 📊 4. Compact Community Statistics (`HeroSectionComponents.kt`)
- **Rich Meta Display:** Added formatted community engagement badges (e.g. "1.2M members • 45.3K favorites") to the hero meta bar.

### 🧪 5. Testing & Validation (`HeroSelectionTest.kt`)
- Added 10 unit tests covering selection diversity, score calculation, TMDB fallback normalization, and edge cases with 100% pass rate.

### 📦 6. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.214-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.213)

### 🔍 1. Arama Ekranı: Yükleme Animasyonu Tutarlılığı (Shimmer Fix)
- **Kopya Shimmer Kaldırıldı:** "Tümü" sekmesinde verisi gelmiş rafların üstüne binen 5 sabit shimmer rafı tamamen kaldırıldı. Artık raf başına tek görsel durum var: Verisi gelmeyen raf kendi shimmer'ını gösterir, verisi gelen raf anında karta dönüşür.
- **Titreme Giderildi:** Arama başladığında yükleme bayrakları kapsama göre anında yazılır; ara "boş + yüklenmiyor" karesi ve rafların kaybolup geri gelmesi (titreme) engellendi.
- **Sayfalama Shimmer Çakışması:** Diğer sekmelerde shimmer yalnızca henüz hiç sonuç yokken çizilir; sayfalama ve yenilemede mevcut sonuçların üstüne binmez.

### 🎯 2. Veri Doğruluğu, Kapsam Süzme ve Alakalılık (`SearchRelevance`)
- **Doğru Kapsam Planlaması (`planAllSources`):**
  - Manhwa / Manga / Manhua / Light Novel kapsamında TMDB ve Simkl'e istek atılmaz; çizgi roman aramalarında alakasız live-action filmler çıkmaz.
  - Diziler / Filmler / K-Drama aramaları anime veritabanlarına düşürülmez; yalnızca TMDB ve Simkl sorgulanır.
  - Karakter / Personel araması "Tümü" motorunda medya raflarını tetiklemez.
- **Türe Duyarlı Tekilleştirme:** Aynı ID'ye sahip anime ve manga kayıtlarının birbirini ezmesi (`kaynak_tur_kimlik`) önlendi; Manhwa ve Manga kayıtları anime altında kaybolmaz (dönüşümlü `interleave` birleştirme).
- **Yeni Alakalılık Motoru (`SearchRelevance`):** Sorguyla doğrudan eşleşen başlıklar en öne alınır, kayıt silinmez. Türkçe karakter ve aksan indirgeme ("İstanbul" ↔ "istanbul") ve yabancı takma ad koruması eklendi.
- **Jikan & AniList İyileştirmesi:** Arama sonuçlarını bozan zorunlu popülerlik sıralaması kaldırıldı, metin alakalılığı ön plana çıkarıldı.

### ⌨️ 3. Onaysız Arama Kaldırıldı (Onaylı & Butonlu Arama)
- **Yazarken Kendiliğinden Arama Engellendi:** Her tuş vuruşunda sorgu atan debounce kaldırıldı. Arama artık yalnızca klavyedeki "Ara" onay tuşuna veya arama butonuna basıldığında tetiklenir.
- **Arama Onay Butonu:** Arama çubuğuna metin yazıldığında görünen arama butonu eklendi.
- **Kompakt Motor Hapı:** Küçük ekranlarda metin alanının daralmaması için yazma esnasında motor seçici kompakt moda geçer.

### 📦 4. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.213-foss.apk`).

---

## 🇬🇧 English (v2.4.213)

### 🔍 1. Search Screen: Shelf Shimmer Consistency (Shimmer Fix)
- **Eliminated Duplicate Shimmers:** Removed the redundant 5-shelf shimmer overlay on the "All" tab. Each provider shelf now owns its visual state: displaying shimmer until its data arrives, then immediately transforming into content.
- **Flicker Elimination:** Loading flags are populated immediately upon search initiation, removing the intermediate "empty/idle" flicker frame.
- **Pagination Shimmer Overlap:** Shimmer placeholders in single-engine tabs only render when the result set is completely empty, preventing overlay during pagination.

### 🎯 2. Data Accuracy, Scope Routing & Relevance (`SearchRelevance`)
- **Scoped Provider Planning (`planAllSources`):**
  - Manga / Manhwa / Manhua / Light Novel scopes bypass TMDB and Simkl completely, eliminating live-action film bleed into print searches.
  - TV Shows / Movies / K-Drama queries route exclusively to TMDB and Simkl without querying anime databases.
  - Character / Staff scopes never trigger media shelves.
- **Type-Aware Deduplication:** Multi-engine keys now include media type (`source_type_id`), preventing legitimate manga records from being dropped when sharing an ID with an anime. Anime and manga are interleaved in mixed searches.
- **Search Relevance Engine (`SearchRelevance`):** Query matches are ranked to the top without pruning non-exact matches (preserving synonyms, romaji, and alternate titles). Added Turkish diacritic folding ("İstanbul" ↔ "istanbul").
- **Jikan & AniList Ranking Fix:** Stripped artificial popularity overrides on search queries in favor of pure text relevance.

### ⌨️ 3. Explicit Search Triggering (Debounce Removed)
- **No Keystroke Auto-Search:** Eliminated automatic debounced searching while typing. Searches only execute upon tapping the keyboard action button or the search icon.
- **Search Action Button:** Added an explicit search trigger button in the search bar when query text is present.
- **Compact Engine Selector:** Engine selector switches to compact mode while typing on small screens to preserve input field width.

### 📦 4. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.213-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.212)

### 📥 1. Android 10 (API 29) ve Üzeri İndirme / Kaydetme Düzeltmesi (ENOENT Fix)
- **Kök Neden Giderildi:** Galeri ve detay ekranlarından görsel indirirken Android 10 ve üzeri Scoped Storage kısıtlamaları nedeniyle meydana gelen `open failed: ENOENT (No such file or directory)` hatası çözüldü.
- **Doğru MediaStore Koleksiyonu:** `MediaStore.Images` yerine Android 10 standartlarına uygun olarak `MediaStore.Downloads.EXTERNAL_CONTENT_URI` ile `Download/Kitsugi/Images` yolu kullanıldı; alternatif olarak `Pictures/Kitsugi/Images` desteği eklendi.
- **Kademeli Güvenli Kayıt (Fallback):** Scoped Storage veya özel ROM kısıtlaması olan cihazlarda doğrudan dosya yazımı engellense dahi `getExternalFilesDir` yedekleme mekanizmasıyla görsellerin her koşulda sıfır hatayla kaydedilmesi sağlandı.
- **Depolama İzinleri & Bayraklar:** `AndroidManifest.xml` içerisine `requestLegacyExternalStorage="true"` eklendi ve `WRITE_EXTERNAL_STORAGE` izni Android 10 (`maxSdkVersion=29`) için de geçerli kılındı.
- **Galeri Otomatik Tarama:** İndirilen resimler `MediaScannerConnection` ile anında taranarak cihazın yerel Galeri ve Fotoğraflar uygulamalarında hemen görünür hale getirildi.

### 📦 2. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.212-foss.apk`).

---

## 🇬🇧 English (v2.4.212)

### 📥 1. Android 10+ (API 29) Image Download & Storage Fix (ENOENT Fix)
- **Root Cause Resolved:** Fixed `open failed: ENOENT (No such file or directory)` occurring when downloading gallery artwork on Android 10+ due to Scoped Storage directory restrictions.
- **Proper MediaStore Target:** Switched from `MediaStore.Images` (which rejects `DIRECTORY_DOWNLOADS` on Android 10) to `MediaStore.Downloads.EXTERNAL_CONTENT_URI` for `Download/Kitsugi/Images`, with `Pictures/Kitsugi/Images` fallback.
- **Graceful Multi-Tier Fallback:** Implemented seamless fallback to app-specific external storage (`getExternalFilesDir`) if public storage creation is restricted on customized OEM ROMs, guaranteeing zero download failures.
- **Manifest Storage Compatibility:** Added `requestLegacyExternalStorage="true"` and extended `WRITE_EXTERNAL_STORAGE` to `maxSdkVersion=29`.
- **Immediate Gallery Indexing:** Invoked `MediaScannerConnection` after write so downloaded media shows up immediately in system Gallery and Photos apps.

### 📦 2. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.212-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.211)

### 🎌 1. Kapsamlı Bangumi Başlık ve İsim Yerelleştirmesi (Bangumi Localization)
- **Merkezi İsim Yerelleştirici (`BangumiNameLocalizer`):** Bangumi'den gelen İngilizce (English), Romaji, Japonca/Özgün (Native), Çince ve alternatif adları (`alias`) koruyan merkezi yerelleştirme katmanı eklendi.
- **Kullanıcı Başlık Dili Tercihi:** Başlık dili tercihi (İngilizce / Romaji / Japonca-Özgün) Bangumi için de tam olarak uygulanır; eksik alanlarda güvenli geri dönüş zinciri çalışır.
- **Çince Önceliği Kaldırıldı:** Bölüm isimlerinde ve varsayılan konu başlıklarında Çince ismin zorunlu önceliği kaldırıldı; orijinal ve İngilizce başlıklar ön plana çıkarılır.
- **Bağımsız İngilizce İsim Desteği:** Karakter ve yapım ekibi modellerinde İngilizce ve Romaji adlar birbirinden ayrıldı.
- **Tüm Ekranlarda Aktif:** Arama, keşfet, medya detayları, karakterler, seslendirmenler, ekip, ilişkiler, öneriler, profil/favoriler, içe aktarma ve Android TV ekranlarının tamamında yerelleştirilmiş isimler uygulandı.

### 🎮 2. Oynatıcı Arayüzü, Tema Uyumu ve Media3 ↔ MPV Paritesi
- **Sabit Gösterge Konumları:** Parlaklık göstergesi ekranın sağında, ses göstergesi solunda sabitlendi (kaydırma jest bölgeleri kullanıcının tercihine göre çalışmaya devam eder).
- **Yenilenen Altyazı Stil Paneli:** Yatay modda ekran dışına taşma sorunu giderildi; panel ekran sınırlarına sabit, kaydırılabilir ve çentik/sistem çubuklarını (safe-area) dikkate alacak şekilde yeniden yapılandırıldı.
- **Uygulama Teması ve Vurgu Rengi (`PlayerAccentTheme`):** Oynatıcı artık seçili tema rengini ve AMOLED modunu alıyor; diyalog, panel, kaydırıcı, buton ve sekmelerdeki sabit renkler seçili tema vurgu rengine uyarlandı.
- **Dahili Oynatıcı Yedekleme Zinciri:** Ana oynatıcıda (Media3) oynatma hatası meydana geldiğinde doğrudan ikinci dahili motor olan MPV'ye geçilir (harici uygulamaya düşme kaldırıldı). Motor geçişinde video sıfırdan başlamaz, kalınan saniyeden devam eder.
- **Ortak Altyazı Öncelik Politikası (`SubtitleSelectionPolicy`):** Hem Media3 hem MPV için tek kural: Gömülü Türkçe > Harici Türkçe > Tercih edilen diğer diller. `tr-TR` ve `tr_TR` gibi bölgesel etiketler Türkçe olarak tanınır.
- **Altyazı ve Ses Düzeltmeleri:** Önbellekteki harici altyazıların bozuk `file://$sub.url` yolu düzeltildi; harici altyazılar benzersiz ID ile eşleştirilir. MPV motoruna ayrı ses akışı (`audioUrl`) harici parça olarak eklendi.

### 🌐 3. CloudStream & Akış İyileştirmeleri
- `domain_fixes.json` blocked listesi temizlendi; Türk eklentileri üzerindeki sessiz atlama kapıları kaldırılarak gerçek hata durumu kartlarda gösterildi.

### 📦 4. Dağıtım
- Kullanıcı talimatı doğrultusunda **yalnızca FOSS varyantı** (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.211-foss.apk`). GMS kesinlikle hariç tutuldu.

---

## 🇬🇧 English (v2.4.211)

### 🎌 1. Comprehensive Bangumi Title and Name Localization
- **Central Name Localizer (`BangumiNameLocalizer`):** Dedicated localization engine preserving Bangumi English, Romaji, native/Japanese, Chinese, and alias records.
- **User Language Preference:** The global title language preference (English, Romaji, Native/Japanese) is now fully applied across all Bangumi surfaces with graceful fallback logic.
- **Removed Chinese Default Bias:** Forced Chinese title precedence on episode titles and main subjects has been removed in favor of user preferences.
- **Distinct English Names:** Added dedicated English naming fields for character and staff models so English and Romaji are no longer conflated.
- **Omnipresent UI Support:** Applied across search, explore, media details, characters, voice actors, staff credits, relations, recommendations, profiles/favorites, imports, and Android TV screens.

### 🎮 2. Player UI, Theme Accent & Media3 ↔ MPV Parity
- **Fixed Indicator Placement:** Brightness indicator is fixed on the right screen edge, volume indicator on the left (swipe gesture regions remain configurable).
- **Redesigned Subtitle Style Panel:** Eliminated horizontal overflow in landscape mode; panel is now bounded, scrollable, and aware of notches and system bars.
- **Dynamic Theme Accent (`PlayerAccentTheme`):** Player now inherits the active app theme color and AMOLED settings; hardcoded colors in sheets, dialogs, sliders, and buttons replaced with the chosen accent color.
- **Internal Engine Fallback Chain:** Playback failures on the primary engine (Media3) automatically switch to the secondary internal engine (MPV) rather than dropping to an external player. Playback resumes from the exact failure position.
- **Shared Subtitle Selection Policy (`SubtitleSelectionPolicy`):** Unified policy across both Media3 and MPV: Embedded Turkish > External Turkish > Other preferred languages. Regional language tags (e.g. `tr-TR`, `tr_TR`) properly recognized.
- **Track & Path Fixes:** Resolved broken `file://$sub.url` paths for cached external subtitles; external tracks identified by unique IDs. Added separate audio stream (`audioUrl`) loading support to MPV.

### 🌐 3. CloudStream & Streaming Refinements
- Cleared broken provider blocklists in `domain_fixes.json`, removed silent skip gates, and surfaced diagnostic error messages.

### 📦 4. Distribution
- Strictly built and released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.211-foss.apk`). GMS is completely excluded.

---

## 🇹🇷 Türkçe (v2.4.210)

### 🎌 1. Bangumi Profil Sekmesi ve Koleksiyon Yönetimi
- **Profil Seçicisine Eklendi:** Profil kaynağı seçicisine (Profile Source Picker) "Bangumi" seçeneği eklendi. Hesap bağlıysa `@kullanıcıadı` görüntülenir.
- **Kapsamlı Profil Başlığı:** Kullanıcı avatarı, takma adı, kullanıcı adı, kişisel imzası (`sign`) ve tek tıkla açılan "bgm.tv profili" butonu.
- **Koleksiyon Listesi ve Filtreler:** Tür (Anime, Kitap/Manga, Oyun, Müzik, Dizi/Film) ve izleme durumu filtreleri, özet koleksiyon sayaçları, bölüm/cilt ilerleme göstergesi ve kullanıcı puanı.
- **Favori Karakterler ve Kişiler:** Kullanıcının Bangumi'de favorilediği karakterler ve kişiler profil ekranında listelenir.
- **Doğrudan Detay Ekranı:** Anime ve manga koleksiyon kartlarına dokunulduğunda doğrudan uygulama içi detay ekranı açılır.
- **Bildirimler:** Resmi Bangumi API'sinde bildirim ucu bulunmadığı için uygulama içi WebView ile doğrudan ve güvenli `bgm.tv/notify/all` sayfası açılır.

### 👤 2. Uygulama İçi Karakter ve Kişi Detayları & Favori Eşitleme
- **Uygulama İçi Detay Sayfaları:** Bangumi karakter ve kişi (yapım ekibi/seiyuu) sayfaları artık tarayıcıya yönlendirilmeden doğrudan uygulama içinde açılır (`KitsugiBangumiCreditsClient` ve `KitsugiBangumiDetailClient`).
- **Bangumi Favori (Kalp) Entegrasyonu:** Bangumi hesabı bağlıyken karakter veya kişi sayfasındaki favori durumu canlı olarak Bangumi sunucusuyla eşitlenir; ekleme ve çıkarma anında yazılır, ağ hatasında güvenli bir şekilde eski haline döner.
- **Dolu Karakter ve Ekip Sekmeleri:** Bangumi konularında "Karakterler" ve "Ekip" sekmeleri v0 OpenAPI ve p1 web uçlarıyla eksiksiz doldurulur.
- **Doğru Paylaşım Bağlantıları:** Karakter ve kişi paylaş butonları artık `bgm.tv/character/{id}` ve `bgm.tv/person/{id}` bağlantılarını üretir.

### 🎮 3. Oynatıcı Jest Bölgeleri, PiP Butonları ve Ses Yönetimi
- **Kaydırıcı ve Dokunma Hizalaması:** Ekrandaki kaydırıcı yerleşimiyle dokunma bölgeleri eşitlendi: Sol yarı parlaklık, sağ yarı ses (varsayılan). Ayar menüsündeki yön metinleri düzeltildi.
- **Çalışan PiP Tuşları:** Mini oynatıcı (PiP) penceresindeki Oynat, Duraklat ve Sonraki Bölüm tuşları Compose oynatıcı köprüsüne (`PipPlayerCallback`) bağlandı.
- **Arka Plan Ses Kesilmesi:** PiP kapatıldığında veya uygulama arka plana alındığında sesin çalmaya devam etmesi `onStop` ve `onDestroy` yaşam döngüsü kontrolleriyle engellendi.

### 🌐 4. CloudStream Kaynak Akışı ve Domain İyileştirmeleri
- **Sessiz Atlama Kapıları Kaldırıldı:** `KNOWN_BROKEN_DOMAINS` ve `domain_fixes.json` blocked listesi eşleştiğinde eklentiler artık peşinen atlanıp sıfır sonuca düşürülmüyor; her zaman denenir ve gerçek hata (DNS/Cloudflare/timeout vb.) UI kartında kullanıcıya bildirilir (`CsPluginStatusTracker.recordSkip`).
- **Domain Listesi Çelişkileri Çözüldü:** `4kfilmizlesene.nl` ve `666filmizle.site` ölü domainler listesinden çıkarıldı.
- **Temizlenen Blocked Listesi:** `domain_fixes.json` dosyasındaki gereksiz yere TR eklentilerini öldüren blocked listesi boşaltıldı (+18 filtreleme uygulamada `ADULT_PLUGINS` ile ayrı olarak yönetilir).
- **Gerçek Hata Gösterimi:** Boş akış döndüğünde "Bu anime için akış bulunamadı" yerine `CsPluginStatusTracker`'dan gelen gerçek sebep gösterilir.
- **Taze Domain Önceliği:** Dinamik güncel domain tablosundan gelen taze domainler, sabit `KNOWN_BROKEN_DOMAINS` listesi tarafından engellenmez.
- **Revert Kararlılığı:** Özgün domaine dönüldükten sonra aynı oturumda yerleşik tablonun tekrar zorlanması engellendi.
- **Güncelleme Geri Çekilme (Backoff):** 404 veren eklentiler için 12 saatlik bekleme süresi uygulandı (`CsAutoUpdateBackoff`).

### 🛠️ 5. Gelişmiş Çökme Teşhisi ve Oturum Denetimi (Crash Diagnostics Overhaul)
- **Native İz Genişletmesi:** Native çökme yığın izi sınırı 48'den 96 çerçeveye çıkarıldı; PC (Program Counter) ve LR (Link Register) bilgileri eklendi.
- **Bellek Haritası Desteği (`/proc/self/maps`):** Çökme anında bellek haritası kaydedilerek native adreslerin ilgili `.so` dosya adına ve ofsetine çözülebilmesi sağlandı.
- **Doğru Süreç ve Ekran Tespiti:** Teşhis raporundaki "Ölüm Sebebi" artık sadece çöken oturumun PID'sine odaklanır; "Son Ekran" bilgisi eylem izinden doğrulanarak gösterilir.
- **Genişletilmiş Kanıt Penceresi:** Sessiz kapanmalarda native iz okuma penceresi 30 dakikadan 7 güne çıkarıldı.

### 📦 6. Dağıtım
- Kullanıcı talimatı doğrultusunda **yalnızca FOSS varyantı** (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.210-foss.apk`). GMS kesinlikle hariç tutuldu.

---

## 🇬🇧 English (v2.4.210)

### 🎌 1. Bangumi Profile Tab & Collection Management
- **Added to Profile Picker:** Bangumi is now available in the profile source selector, displaying `@username` when connected.
- **Full Profile Header:** Displays user avatar, nickname, username, personal signature (`sign`), and a direct link to open the bgm.tv web profile.
- **Collections & Filters:** Category (Anime, Manga/Book, Game, Music, Real/Drama) and status filters, collection counters, episode/volume progress, and user ratings.
- **Favorite Characters & People:** Highlights the user's favorited Bangumi characters and staff members directly on the profile screen.
- **In-App Navigation:** Tapping anime or manga entries seamlessly opens the existing in-app media detail page.
- **Notifications:** Since Bangumi does not provide a public notification API endpoint, notifications open via an in-app WebView targeting `bgm.tv/notify/all`.

### 👤 2. In-App Character & Staff Detail Pages & Bangumi Favorites
- **Native In-App Views:** Bangumi character and person/staff pages now render natively in-app (`KitsugiBangumiCreditsClient` & `KitsugiBangumiDetailClient`) rather than opening externally.
- **Live Favorite Toggle:** When logged in with Bangumi, tapping the favorite heart on character/person screens syncs directly to Bangumi with rollback on error.
- **Populated Credits Tabs:** Subjects on Bangumi now populate both "Characters" and "Staff" tabs via official v0 OpenAPI with p1 fallbacks.
- **Accurate Share URLs:** Sharing characters or staff members now correctly generates `bgm.tv/character/{id}` and `bgm.tv/person/{id}` links.

### 🎮 3. Player Gesture Zones, PiP Controls & Audio Lifecycle
- **Slider & Touch Zone Alignment:** Touch regions now match on-screen sliders: Left half controls brightness, right half controls volume (default). Settings labels aligned.
- **Functional PiP Actions:** Mini player (PiP) Play, Pause, and Skip Next buttons are wired to the player engine via `PipPlayerCallback`.
- **Background Audio Stop:** Playback reliably pauses upon closing PiP or backgrounding the activity via `onStop` and `onDestroy`.

### 🌐 4. CloudStream Stream & Domain Enhancements
- **Eliminated Silent Skip Gates:** Extensions matching `KNOWN_BROKEN_DOMAINS` or `domain_fixes.json` blocked list are no longer prematurely skipped; all extensions are executed and real errors (DNS, Cloudflare, timeouts) are surfaced directly on stream cards (`CsPluginStatusTracker.recordSkip`).
- **Domain Contradictions Resolved:** `4kfilmizlesene.nl` and `666filmizle.site` removed from broken domain blocklist.
- **Cleared Blocked List:** Cleared `domain_fixes.json` blocked list that previously incapacitated legitimate Turkish streaming providers (+18 filtering is handled strictly via `ADULT_PLUGINS`).
- **Real Diagnostic Error Display:** When stream discovery yields empty results, the actual root cause from `CsPluginStatusTracker` is displayed instead of a generic "No streams found" placeholder.
- **Fresh Domain Priority:** Fresh remote domains override the hardcoded `KNOWN_BROKEN_DOMAINS` blocklist.
- **Stable Revert Protection:** Restored domains are protected against immediate builtin overrides in the same session.
- **Update Backoff:** 12-hour retry backoff on 404/missing extensions (`CsAutoUpdateBackoff`).

### 🛠️ 5. Enhanced Crash Diagnostics & Session Supervisor
- **Extended Native Trace:** Increased native stack frames limit from 48 to 96, capturing PC and LR registers.
- **Memory Maps Dump (`/proc/self/maps`):** Recorded address maps allow translating raw crash addresses to `.so` library names and offsets.
- **Precise PID & Last Screen Tracking:** Root cause attribution strictly targets the crashing PID session; the last visited screen is inferred from the action breadcrumbs.
- **Extended Evidence Window:** Silent crash native trace retention window prolonged to 7 days.

### 📦 6. Distribution
- Strictly built and released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.210-foss.apk`). GMS is completely excluded.

---

## 🇹🇷 Türkçe (v2.4.209)

### 🎌 1. Bangumi Liste İçe Aktarma ve Eşitleme 404 Kök Neden Çözümü
- **Kullanıcı Adı Çözümlemesi:** Bangumi sunucusunda `"-"` takma adının yalnızca yazma uçlarında geçerli olması ve okuma ucunda (`GET /v0/users/-/collections`) 404 vermesi sorunu çözüldü. Artık saklanan kullanıcı adı, yoksa `/v0/me`, o da yoksa sayısal ID dinamik olarak çözümlenerek gerçek kullanıcı adına istek atılıyor.
- **Koleksiyon Uçları Güvenliği:** `getUserCollections`, `getAllUserCollections` ve `getUserCollection` fonksiyonlarındaki hatalı `"-"` varsayılan değeri kaldırılarak yanlışlıkla 404 hatasına düşülmesi engellendi.
- **Durum Kontrolü:** `BangumiSyncManager.fetchRemoteStatus` içerisindeki aynı hata giderilerek uzaktaki izleme/okuma durumu güvenle alınır hale getirildi.

### 🔍 2. Bangumi Arama Motoru İyileştirmeleri ve Yedek Uç
- **Yetişkin İçerik (NSFW) Filtresi:** Yetişkin içerik açıkken sunucuya metin (`"include"`) olarak gidip 400 hatası oluşturan `filter.nsfw` parametresi, sunucunun beklediği JSON boolean formatına dönüştürüldü (`false` veya filtresiz).
- **Sayfa Boyutu ve Atlama Düzeltmesi:** Sunucu limiti 20 olduğu halde istemcinin 24 kayıt istemesi ve offset hesaplamasında her sayfada 4 kaydın atlanması sorunu giderildi; tüm Bangumi aramalarında sayfa boyutu 20'ye sabitlendi.
- **Eski Uç Yedeği (Legacy Search Fallback):** v0 deneysel arama ucu hata verirse veya boş dönerse `GET /search/subject/{q}` eski arama ucu otomatik devreye girer.
- **Şeffaf Hata Bildirimi:** Arama hataları artık "Sonuç bulunamadı" ardına gizlenmez; sunucudan dönen gerçek hata mesajı ekranda gösterilir ("Tümü" karma rafında da anime ve manga hataları yüzeye çıkarılır).

### 🎬 3. Kapsamlı Bangumi Detay Sayfası ve Çapraz Kimlik Eşleştirme (`KitsugiBangumiDetailClient`)
- **Doğrudan Yerel Veri Doldurma:** Bilgi, karakterler (seslendirmenli), yapım ekibi, ilişkiler, öneriler, puan dağılım grafikleri, incelemeler/yorumlar ve bölümler doğrudan Bangumi'nin kendi verisinden (v0 ve p1 uçları) anında doldurulur.
- **Karakter ve Kişi Detay Sayfaları:** Bangumi kaynaklı karakter ve yapım ekibi detay sayfaları p1 uçları üzerinden eksiksiz çalışır hale getirildi.
- **Akıllı Çapraz Kimlik Eşleme:** Bangumi kaydı başlık, yıl, bölüm sayısı ve format puanlamasıyla AniList, MAL ve ARM üzerinden TMDB/Kitsu/TVDB/IMDb ile güvenli şekilde eşleştirilir.
- **Zenginleştirilmiş Galeri:** Resimler sekmesine Fanart.tv, TMDB, Shikimori, MAL ve AniList görselleri dahil edildi. `lain.bgm.tv` kaynaklı aynı görselin farklı boyut kopyaları kanonik anahtar ile tekil görsele indirildi.
- **Room Önbellek Sürümlemesi:** Önceki sürümlerin eksik/hatalı Kitsu aramasıyla önbelleğe aldığı satırların okunmasını engellemek için önbellek anahtarına `_bgm1` sürüm eki getirildi.
- **Bölüm Puanları ve Logolar:** MDBList, logo ve bölüm puanları eşleşen çapraz kimlikler üzerinden kusursuz çalışır.

### 📦 4. Dağıtım
- Kullanıcı talimatı doğrultusunda **yalnızca `foss` varyantı** derlendi ve GitHub'a yüklendi (`Kitsugi-Beta-v2.4.209-foss.apk`). GMS kesinlikle hariç tutuldu.

---

## 🇬🇧 English (v2.4.209)

### 🎌 1. Bangumi Collection Import & Sync 404 Root Cause Fix
- **Dynamic Username Resolution:** Resolved the issue where Bangumi rejected the `"-"` alias on read endpoints (`GET /v0/users/-/collections`) with 404 Not Found. Real username is now dynamically determined via stored name, `/v0/me`, or numeric ID.
- **Safe Collection Endpoints:** Removed the fallback `"-"` default in `getUserCollections`, `getAllUserCollections`, and `getUserCollection` to prevent accidental regressions.
- **Sync Status Audit:** Fixed identical issue in `BangumiSyncManager.fetchRemoteStatus` so remote progress sync works reliably.

### 🔍 2. Bangumi Search Improvements & Legacy Fallback
- **NSFW Boolean Filtering:** Fixed `filter.nsfw` sending string `"include"` which caused HTTP 400 Bad Request; now correctly sends JSON boolean or omits when inactive.
- **Page Size Alignment:** Standardized page size and pagination offsets to 20 to match Bangumi's hard limit, preventing 4-item skipping and false "no more pages" triggers.
- **Legacy Fallback Endpoint:** If experimental v0 search fails or returns empty, fallback to legacy `GET /search/subject/{q}` seamlessly engages.
- **Surfaced Error Messages:** API errors are no longer silently masked as "No results found"; descriptive server messages are displayed across both isolated and mixed search views.

### 🎬 3. Full Native Bangumi Media Details & Cross-ID Enrichment (`KitsugiBangumiDetailClient`)
- **Native Details & Tabs:** Information, characters with voice actors, staff, relations, recommendations, score distributions, reviews/comments, and episodes load immediately using native Bangumi APIs (v0 and p1).
- **Character & Staff Pages:** Native Bangumi character and person profile pages are fully supported via p1 endpoints.
- **Cross-ID Resolution:** Bangumi subjects are matched with AniList, MAL, and ARM (TMDB/Kitsu/TVDB/IMDb) using strict title, year, episode count, and format scoring.
- **Enhanced Gallery & Dedup:** Gallery tabs are enriched with fanart.tv, TMDB, Shikimori, MAL, and AniList images. Duplicate covers from `lain.bgm.tv` across various size buckets are deduplicated into single items.
- **Room Cache Busting:** Added `_bgm1` suffix to Bangumi detail cache keys, preventing stale/erroneous legacy Kitsu cache entries from loading.
- **Episode Ratings & Logos:** MDBList, clearlogos, and episode rating curves now correctly resolve using the linked cross-IDs.

### 📦 4. Distribution
- Built and published strictly as a **FOSS release** (`Kitsugi-Beta-v2.4.209-foss.apk`). GMS flavor is omitted.

---

## 🇹🇷 Türkçe (v2.4.208)

### 🏷️ 1. AniList İsim Dili (Romaji / İngilizce / Japonca) Yerelleştirmesi
- **Karakter, Seslendirmen & Yapım Ekibi (Staff) İsimleri:** AniList'ten gelen kişi ve karakter adları artık kullanıcının tercih ettiği isim diline (`ROMAJI`, `ENGLISH`, `NATIVE`, `JAPANESE_STAFF`) göre dinamik olarak gösteriliyor.
- **Latin & Orijinal Ad Uyumu (`AniListPersonName.kt`):** AniList sorgularında `userPreferred` yerine `full`, `native` ve alternatif adlar çekilerek Romaji/İngilizce tercihinde Latin harfli isimler önceliklendirildi. Latin harfli karşılık bulunamadığında orijinal ad korunur.
- **Temiz Başlık & Alt Başlık:** Japonca/Yerel ad seçildiğinde alt başlıktaki mükerrer isimler temizlenir ("Karakter" veya doğrudan rol açıklaması gösterilir).
- **Kapsam:** Arama sonuçları, yapım detay sekmeleri (`CharactersTab`), API detay sayfası, yerel kütüphane detay sayfası, karakter detayı ve yapım ekibi detay sayfalarının tümü güncellendi.

### 📜 2. Akıllı Kaydırma Konumu Koruma (Scroll Retention)
- **Detaydan Geri Dönüş Güvencesi:** Bir listenin ortasından detay sayfasına girilip geri dönüldüğünde ekranın başa sarması ve kaydırma konumunun sıfırlanması tamamen önlendi.
- **Listem (My List):** 7 kütüphane sekmesinin (Tümü, İzlediklerim, İzleyeceklerim, Bıraktıklarım vb. ve 🎌 Bangumi) her biri için kaydırma konumu ekran kapansa dahi bağımsız saklanır (`savedTabIndices`, `savedTabOffsets`, `restoredTabs`).
- **Arama & Çoklu Platform Rafları:** Arama sayfası ve kaynağa özel arama (`SourceSearchPage`) için `rememberRetainedLazyListState` entegre edildi. Sonuçlar henüz yüklenirken veya yenilenirken listenin 0'a sıfırlanması engellendi. `SourceSearchOwner` ile izole ViewModel durumu geri dönüşte korunur.
- **Keşfet & Tam Ekran Izgaralar:** `FullScreenMediaGridPage` ve `AddonFullScreenGridPage` `SaveableStateProvider` ve derinlik takibi (`openingStackDepth`) ile korunarak detaydan dönüşte tam kaldığı noktaya döner.

### ✨ 3. Kartlarda Neon Degrade Çerçeve ve Parıltı (List Grid Glow)
- **Kare Izgara Kartları (`KitsugiMediaEntryCard`):** Kare poster görünümündeki kartlara zarif degrade çerçeve ve neon parıltı efekti (`kitsugiNeonGlow`) uygulandı. Listem sekmelerine ve Favorilerim ekranına modern, canlı bir görünüm kazandırıldı.
- **Kullanıcı Listeleri (`UserMediaListCards`):** Kaynak kullanıcı profil listelerindeki hem kare (grid) hem yatay satır (row) kartlarına degrade çerçeve ve parıltı stili dahil edildi.

### 🛠️ 4. MyAnimeList / Jikan Detay Sekmeleri Kök Neden Çözümü (`MalJikanMediaSupport`)
- **Sekme Donması ve Boş Kalma Çözümü:** MAL kaynaklı detay sayfalarında Karakterler, Ekip, Öneriler, İlişkiler, İstatistikler ve Yorumlar sekmelerindeki iskelet takılması ve boş kalma sorunu giderildi.
- **Tek Ortak Kural:** `mal`, `jikan`, `myanimelist` kimlik uzayları `MalJikanMediaSupport.canonicalSource` altında birleştirildi; `Movie` ve `TvShow` tiplerinin Jikan uç noktalarında (`anime`) veya AniList tipinde (`ANIME`) yanlışlıkla `MANGA`ya düşmesi engellendi.
- **Akıllı Hiyerarşi:** Jikan birincil kaynak yapıldı; AniList'ten toplu zenginleştirme için 25 saniye bekleme kaldırıldı (veriler anında ekrana gelir). Jikan boş döndüğünde ise karmaşık ARM araması öncesi doğrudan aynı MAL ID'si ile AniList denenerek anında sonuç üretilir.

### 🎬 5. Çökme ve Kurtarma Pencerelerinde Canlı Animasyon (`KitsugiCrashAnimation`)
- **Canlı Çökme Görseli:** Çökme ekranı (`KitsugiCrashActivity`) ve başlangıç kurtarma penceresinde (`KitsugiCrashRecoveryDialog`) statik hata ikonu yerine `res/raw/crash_animation.mp4` döngüsel animasyonu eklendi.
- **Sıfır Ek Yük ve Güvenlik:** Android'in yerel `TextureView` ve `MediaPlayer` altyapısı kullanılarak sessiz ve kesintisiz döngü sağlandı; donanım düzeyinde hata koruması sayesinde çökme ekranının kendisinin hata vermesi tamamen engellendi.

### 📦 6. Dağıtım
- Kullanıcı talimatı doğrultusunda **yalnızca `foss` varyantı** derlendi ve GitHub'a yüklendi (`Kitsugi-Beta-v2.4.208-foss.apk`). GMS kesinlikle hariç tutuldu.

---

## 🇬🇧 English (v2.4.208)

### 🏷️ 1. AniList Person Name Localization (Romaji / English / Native)
- **Characters, Voice Actors & Staff:** AniList person and character names now dynamically conform to the user's selected name/title language preference (`ROMAJI`, `ENGLISH`, `NATIVE`, `JAPANESE_STAFF`).
- **Latin & Native Fallback (`AniListPersonName.kt`):** Queries now request `full`, `native`, and alternate names instead of solely relying on `userPreferred`. When Romaji or English is selected, Latinized names take precedence; if no Latin name exists, the original name is safely preserved.
- **Clean Subtitles:** Redundant name prefixes in subtitles when native names are active are cleanly stripped (displaying "Character" or role details).
- **Universal Coverage:** Applied across Search results, Media Detail tabs (`CharactersTab`), API and Local library detail pages, Character Detail, and Staff Detail pages.

### 📜 2. Smart Scroll Retention Across All Lists
- **Seamless Navigation Return:** Returning from a detail screen to any list or search view now accurately preserves scroll position instead of snapping to top.
- **My List Tabs:** Each of the 7 library tabs (including 🎌 Bangumi) retains its scroll index and pixel offset independently across tab switching and detail navigation (`savedTabIndices`, `savedTabOffsets`, `restoredTabs`).
- **Search & Source Search:** Integrated `rememberRetainedLazyListState` to prevent intermediate empty/loading states from clamping scroll offsets to 0. `SourceSearchPage` leverages `SourceSearchOwner` to keep isolated ViewModel state alive during nested navigation.
- **Explore & Full-Screen Grids:** Wrapped `FullScreenMediaGridPage` and `AddonFullScreenGridPage` in `SaveableStateProvider` with depth tracking (`openingStackDepth`) for accurate back-stack restoration.

### ✨ 3. Neon Gradient Border & Glow on Media Cards
- **Grid Cards (`KitsugiMediaEntryCard`):** Added subtle neon glow and gradient border accents (`kitsugiNeonGlow`) to poster grid media cards across My List and Favorites.
- **User Profile Lists (`UserMediaListCards`):** Extended neon gradient styling to both grid and row cards within remote user profile lists.

### 🛠️ 4. MyAnimeList / Jikan Detail Tabs Unification (`MalJikanMediaSupport`)
- **Stall & Empty Tab Resolution:** Fixed root causes where Characters, Staff, Recommendations, Relations, Stats, and Reviews tabs on MAL-sourced items remained stuck on skeletons or empty.
- **Unified Identity Space:** Normalized `mal`, `jikan`, and `myanimelist` under `MalJikanMediaSupport.canonicalSource`. Corrected `Movie` and `TvShow` mapping to prevent accidental `MANGA` endpoints.
- **Instant Response Flow:** Made Jikan the primary responsive source without waiting 25s for AniList batch cover enrichment. If Jikan is empty, AniList is queried with the exact MAL ID before escalating to ARM/Shikimori resolvers.

### 🎬 5. Smooth Looping Animation for Crash & Recovery Screens (`KitsugiCrashAnimation`)
- **Dynamic Error Graphic:** Replaced static bug icons in `KitsugiCrashActivity` and `KitsugiCrashRecoveryDialog` with a sleek looping video animation (`res/raw/crash_animation.mp4`).
- **Zero Overhead & Robust Fallbacks:** Built on Android's native `TextureView` and `MediaPlayer` for seamless looping, zero audio intrusion, and complete crash immunity.

### 📦 6. Distribution
- Per user specification, **only the `foss` release APK** was built and released (`Kitsugi-Beta-v2.4.208-foss.apk`). GMS is strictly excluded.

---

## 🇹🇷 Türkçe (v2.4.207)

### 🎌 1. Bangumi (bgm.tv) Tam Platform Entegrasyonu (7. Kaynak)
- **OAuth 2.0 & Kimlik Doğrulama:** `kitsugi://bangumi-auth` ve `aniyomi://bangumi-auth` yönlendirme şemalarıyla modern yetkilendirme altyapısı kuruldu. `BangumiAuthStore` ve `BangumiAuthManager` ile "1-Tık Otomatik Giriş" ve manuel yetki kodu girme seçenekleri sağlandı.
- **Kütüphane & İçe Aktarma:** Bangumi koleksiyon durumları (想看/在看/看过/搁置/抛弃 - İzlemek İstiyorum / İzliyorum / İzledim / Beklemede / Bıraktım), 0-10 puanlama sistemi ve bölüm bazlı ilerleme (打格子) Kitsugi kütüphanesine içe aktarma (`BangumiImportManager`) ve iki yönlü senkronizasyon (`BangumiSyncManager`) ile tam entegre edildi.
- **Listem Sekmesi (7. Sekme):** Listem ekranına 7. sekme olarak 🎌 Bangumi kütüphanesi eklendi (`MyListLibraryGrouping`, `MyListScreen`, `MyListComponents`, `MyListEmptyState`). Özel marka rengi (`#F09199`), rozetler, sayaçlar ve doğrudan sekme içinden açılan hesap bağlama diyaloğu eklendi.
- **Keşfet & Arama Motoru:** Keşfet ekranında 7. platform olarak Bangumi desteği (`AllSourcesExplore`, `ExplorePlatformToggle`, `ExploreViewModel`), Arama motorlarında Bangumi motoru (`KitsugiBangumiClient`, `SearchViewModel`), anime/manga gelişmiş filtreleme, sıralama, karakter ve personel arama kabiliyeti kazandırıldı.
- **Görsel Rozet & Marka:** Gerçek Bangumi vektör logosu (`ic_logo_bangumi.xml`), Hero rozetleri, detay sayfası başlıkları ve galeri rozetleri güncellendi.

### ⚠️ 2. Çapraz Eşitleme (Cross-Sync) Güvenlik & Hukuki Sorumluluk Reddi (Disclaimer)
- **Merkezi Sorumluluk Reddi (`CrossSyncDisclaimer.kt`):** Çapraz eşitlemenin deneysel (beta) bir özellik olduğunu, eşleştirmelerin otomatik yapıldığını ve hiçbir eşleştirmenin %100 doğruluk garantisi taşımadığını; yanlış eşleşme, liste karışması veya platform yaptırımlarından (kısıtlama, askıya alma, hesap yasağı vb.) sorumluluk kabul edilmediğini belirten açık ve kapsamlı uyarı metinleri eklendi.
- **Görünür Uyarılar:**
  - Hesap bağlantıları Cross-Sync satırına "Deneysel ·" etiketi ve kısa uyarı eklendi.
  - Çok Yönlü Eşitleme ve Cross-Sync ayar sayfasına bilgi kartı içine ayrıntılı uyarı, Hızlı İşlem bölümü altına kısa not eklendi.
  - Eşitleme penceresinde (`KitsugiCrossSyncDialog`) güvenlik notunun altına ayrıntılı uyarı yerleştirildi.
- **Çoklu Aday Grup Akrabalık Koruması (`AuthViewModel`):** Birden fazla aday grup bulunduğunda, tekil ortak kimlikle seçilen grup için yıl uyumu (`CrossSyncIdentityGuard.yearsCompatible`) ve başlık akrabalığı kontrolü eklendi; uyuşmayan kayıtların yanlış birleştirilmesi ve başka hesaplardaki doğru kayıtların bozulması engellendi (`identityReviewRequired = true`).
- **Rapor Teşhis Etiketleri:** Eşitleme teşhisinde iç kimlik alanları (`300M+` Kitsu, `100M+` AniList) açıkça etiketlenerek (`describeMalIdField`) MAL kimliği sanılması önlendi.

### ⚡ 3. Jikan Hız Limiti, Shikimori Başlık Dili & Kitsu Paralel İstek İyileştirmeleri
- **Jikan Hız Limiti & 429 Koruması (`KitsugiApiBase`):** Jikan için saniyede 3 ve dakikada 55 istek (kayan pencere) limiti getirildi. 429 yanıtı alındığında host geçici olarak bekletilir ve yeniden deneme sayısı 3'e çıkarılarak sekme/karakter boş kalma sorunları giderildi.
- **Shikimori Rusça Başlık Çözümü (`ShikimoriTitleResolver`):** Shikimori GraphQL API üzerinden tek istekte İngilizce ve Japonca başlıklar çekilerek, kullanıcının ayarladığı tercih diline (Romaji / İngilizce / Japonca) göre gösterilmesi sağlandı.
- **İlişki Tipi Çevirileri (`KitsugiTranslations`):** Shikimori ve Jikan ilişkilerindeki "Sequel", "Side story", "Adaptation", "Prequel" gibi ilişki türleri Türkçeye çevrildi. Jikan ilişkilerine AniList'ten İngilizce/Japonca başlık desteği eklendi.
- **Kitsu Karakter & Seslendirmen Paralel İstekleri (`KitsuClient`):** Karakter ve seslendirmen sayfaları sıralı yerine en fazla 3 eşzamanlı istekle paralel çekilerek sayfa açılış hızı büyük oranda artırıldı.
- **Seslendirmen Birleştirme Zaman Aşımı Optimizasyonu (`KitsugiCharacterClient`):** Kitsu ve Shikimori için seslendirmen bekleme süresi 8 saniyeden 5 saniyeye indirilerek sayfa kilitlenmeleri önlendi.

### 🔍 4. Arama & "Tümünü Gör" Devamlılığı
- `SearchViewModel` ve `SourceSearchPage` arasındaki `openSourceSearch` köprüsü Bangumi motorunu da kapsayacak şekilde yenilendi; raflardan "Tümünü Gör"e tıklandığında sorgu, kapsam ve görünen sonuçlar eksiksiz aktarılır.

### 📦 5. Dağıtım
- Kullanıcı talimatı doğrultusunda **yalnızca `foss` varyantı** derlendi ve yayınlandı (`Kitsugi-Beta-v2.4.207-foss.apk`). GMS kesinlikle hariç tutuldu.

---

## 🇬🇧 English (v2.4.207)

### 🎌 1. Full Bangumi (bgm.tv) Platform Integration (7th Source)
- **OAuth 2.0 & Authentication:** Implemented redirect schemes `kitsugi://bangumi-auth` and `aniyomi://bangumi-auth` with `BangumiAuthStore` and `BangumiAuthManager`, enabling 1-click automatic browser login and manual authorization code entry.
- **Library & Smart Sync:** Full support for Bangumi collections (想看/在看/看过/搁置/抛弃 - Wish/Do/Collect/On Hold/Dropped), 0-10 rating scale, and episode progress grid via `BangumiImportManager` and `BangumiSyncManager`.
- **My List 7th Tab:** Added dedicated 🎌 Bangumi tab to My List (`MyListLibraryGrouping`, `MyListScreen`, `MyListComponents`), complete with signature brand color (`#F09199`), item counters, status chips, and in-tab account connection prompt.
- **Explore & Multi-Engine Search:** Integrated Bangumi as the 7th platform in All Sources Explore (`AllSourcesExplore`, `ExploreViewModel`) and Global Search (`SearchViewModel`, `KitsugiBangumiClient`) with anime/manga advanced filtering, tags, years, and character/staff lookups.
- **Brand Assets & Polish:** Added official vector logo (`ic_logo_bangumi.xml`), hero badges, detail screen headers, and gallery chips.

### ⚠️ 2. Cross-Sync Safety Guards & Legal Disclaimers
- **Centralized Disclaimer (`CrossSyncDisclaimer.kt`):** Clear legal notice that Cross-Sync is an experimental beta feature with no 100% match guarantee, disclaiming liability for mismatched lists, progress desyncs, or upstream platform sanctions (bans, suspensions).
- **Surface Visibility:**
  - Added "Experimental ·" tag and summary disclaimer to Account Connections.
  - Injected full disclaimer inside the Cross-Sync settings info card and quick actions section.
  - Embedded disclaimer directly into the synchronization dialog (`KitsugiCrossSyncDialog`).
- **Multi-Candidate Guard (`AuthViewModel`):** Added year compatibility (`yearsCompatible`) and title kinship checks for single-evidence selections when multiple candidate groups exist, preventing erroneous mergers and cross-account data overwrite (`identityReviewRequired = true`).
- **Diagnostic Clarity:** Explicitly labeled synthetic IDs (`300M+` for Kitsu, `100M+` for AniList) in diagnostics to prevent confusion with real MAL IDs.

### ⚡ 3. Jikan Rate Limiting, Shikimori Titles & Kitsu Concurrency
- **Jikan 429 Prevention (`KitsugiApiBase`):** Enforced a rate limit of 3 req/sec and 55 req/min (sliding window) for Jikan API, with automatic backoff cooldown and 3 retries on HTTP 429.
- **Shikimori English/Japanese Titles (`ShikimoriTitleResolver`):** Resolves English and Japanese titles via Shikimori's GraphQL API instead of defaulting to Russian titles, strictly adhering to user's title language preference.
- **Relation Type Translations (`KitsugiTranslations`):** Translated relation types (Sequel, Side story, Adaptation, etc.) to Turkish. Added AniList title fallback for Jikan relations.
- **Kitsu Concurrency (`KitsuClient`):** Characters and voice actors are now fetched concurrently (up to 3 parallel requests), significantly accelerating page loads.
- **Voice Actor Timeout Optimization (`KitsugiCharacterClient`):** Reduced VA fallback timeout from 8s to 5s for Kitsu and Shikimori to prevent UI stalls.

### 🔍 4. Search Shelf Continuity
- Extended `openSourceSearch` handoff between `SearchViewModel` and `SourceSearchPage` to seamlessly support Bangumi without dropping active query, scope, or cached shelf results.

### 📦 5. Release Distribution
- Built strictly as **FOSS Release** (`Kitsugi-Beta-v2.4.207-foss.apk`) per user instruction. No GMS variant generated.

---

## 🇹🇷 Türkçe (v2.4.206)

### 🛡️ 1. Native & Java Çökme Teşhisi, ANR/OOM Raporlama & Cockroach Çözümü
- **Native Çökme Yakalayıcı (C++ - libkitsugi_crash_handler.so):** Java `UncaughtExceptionHandler`'ın yakalayamadığı native çökmeler (SIGSEGV, SIGABRT, SIGBUS, SIGILL, SIGFPE, SIGTRAP) `_Unwind_Backtrace` ile asenkron sinyal güvenliğinde yakalanır; ham geri iz `native_crash.txt` dosyasına yazılır.
- **Sessiz Ölüm Dedektörü (KitsugiSessionSupervisor):** Uygulama temiz çıkış yapamadan kapandığında (LMKD bellek öldürmesi, ANR sonrası sonlandırma, SIGKILL), bir sonraki açılışta logcat taranarak `unclean_exit.txt` raporu üretilir ve kullanıcıya "Beklenmedik Kapanma" penceresi gösterilir.
- **Eylem İzi (Breadcrumbs):** Son 40 kullanıcı hareketi ve son bulunulan ekran anlık kaydedilir; çökme raporlarında hatanın hangi ekranda ve hangi eylem sırasında gerçekleştiği açıkça görülür.
- **Bağımsız Görevde Açılan Çökme Ekranı:** `Thread.sleep(800)` ana thread bloklaması kaldırıldı. Çökme ekranı (`KitsugiCrashActivity`) ayrı görev (`taskAffinity`) ve süreçte açılarak Android görev temizliğinin çökme ekranını yok etmesi engellendi.
- **Cockroach Sonsuz CPU Döngüsü & Kör Hata Yutma Düzeltildi:** `com.lagradost.cloudstream3` hatalarını körlemesine yutup arayüzü kilitli bırakan mantık düzeltildi; `Looper.loop()` sonlandığında oluşan %100 CPU kilitlenmesi (ANR donmaları) giderildi.
- **Erken Kurulum:** Çökme yakalayıcı `attachBaseContext()` aşamasına çekilerek açılış ve provider çökmeleri de kapsama alındı.
- **Genişletilmiş Teşhis Paylaşımı:** "Geliştiriciye Gönder", tüm teşhis kütüklerini (`crash_log`, `crash_history`, `unclean_exit`, `native_crash`, `breadcrumbs` vb.) tek pakette paylaşır.

### 📋 2. Duplicate Lazy Key Liste Çökmeleri Düzeltildi
- `IllegalArgumentException: Key ... was already used` hatasına yol açabilen 7 arayüz listesi düzeltildi: Ana sayfa ızgarası, modern ana sayfa satırları, yayın takvimi (liste ve ızgara), genişletilmiş medya ızgarası, manga eklenti kaynak listesi, özel liste düzenleyici ve oynatıcı hız seçim sayfası.

### 🖼️ 3. Resim İndirme & Bellek Güvenliği (OOM Önleme)
- **Bellek Koruma (Downsampling):** İndirilen görsellerin bildirim önizlemesi tam boy yerine `inSampleSize` ile küçültülerek decode edilir; 48 MB üzeri devasa görseller filtrelenir.
- **Android 10+ MediaStore Kaydı:** Görseller doğrudan `İndirilenler/Kitsugi/Images` altına MediaStore API ile yazılır (Android 11+ dosya yazma engeli aşıldı).
- Galeri ve indirme adımları eylem izine (breadcrumbs) dahil edildi.

### 🎭 4. Karakter Görselleri & Oyuncu Fotoğrafları İyileştirmeleri
- **Akıllı Başlık Adayları:** TMDB içeriklerinde tüm başlık varyantları (Türkçe, Romaji, İngilizce, Japonca, eşanlamlılar) taranarak doğru anime eşleşmesi bulunur.
- **AniList Karakter Araması Yedeği:** Yapım düzeyinde eşleşmeyen karakterler için doğrudan AniList karakter araması yapılır (`realMalId` doğrulamasıyla).
- **Canlı Çekim Kadrolarında Oyuncu Fotoğrafları Korundu:** Live-action yapımlarda oyuncu fotoğrafları silinmez; `isRealMediaRole` bayrağı ile doğrudan TMDB kişi profili açılır.
- **Detay Sayfasında Anında Görsel (Hint Image):** Karakter sayfasına girildiğinde karttaki görsel anında galeriye ve arka plana alınır, sayfa resimsiz kalmaz.
- **Jikan /pictures Optimizasyonu:** Galeri resim isteği yalnızca MyAnimeList kimlik uzayında çalıştırılır (TMDB 404 çağrıları bitti).

### ⚡ 5. Shikimori Detay Sekmeleri & Hız Optimizasyonları
- **Yerel İlişkiler ve Öneriler:** Shikimori `/similar` ve `/related` uç noktalarıyla tek istekte saniyeler içinde yüklenir; kapaklar toplu çözülür.
- **Ekip Sekmesi Boş Kalma Koruması:** Shikimori personel kaydı bulunmayan yapımlarda gerçek MAL ID'si üzerinden Jikan personel listesi devreye girer.
- **Sonsuz Skeleton Koruması (25 sn Zaman Aşımı):** Sekme yüklemelerine 25 saniye (bölümler için 45 saniye) zaman aşımı ve tek-uçuş (single-flight) koruması eklendi.
- **ID İzolasyonu:** Shikimori ID'sinin MAL ID sanılması engellendi; gerçek MAL ID'si önbellekli çözülür.

### 🎬 6. Detay Sayfası Tasarım & "Bilgiler" Kopyalama Butonları Birleştirmesi
- **Ortak Bilgiler Bileşeni (`DetailInfoValueRow`):** Kütüphane girdisi ve API detay ekranlarındaki "Bilgiler" kartı ortak bileşene kavuşturuldu; her iki ekranda da tüm satırlara (Durum, Başlangıç, Bitiş, Stüdyo, Süre, Yayın, Yaş Sınırı, İngilizce, Japonca vb.) kopyalama butonları eklendi.
- **"Diğer Adlar" Tekil Etiketler & Bağımsız Kopyalama:** "Diğer Adlar" satırında tüm isimleri tek metin halinde birleştirip kopyalama sorunu giderildi. Artık her isim kendi etiketinde (chip) ve bağımsız kopyalama butonuyla yer alır; virgül içeren adlar bozulmaz, boş ve tekrar edenler filtrelenir.
- **Kompakt İzle Butonu:** Sol panelde ve özet bilgi alanında tüm genişliği kaplayan devasa buton yerine içerikle uyumlu (`wrapContent`) şık ve kompakt bir "İzle" butonu tasarlandı.

### 🔍 7. Arama & "Tümünü Gör" Raf Tutarlılığı
- **Raf ile Kaynak Sayfası Uyumu:** Arama ekranında bir raftan "Tümünü Gör"e tıklandığında, raftaki sorgu, arama kapsamı (`SearchScope`) ve mevcut sonuçlar doğrudan `SourceSearchPage`'e aktarılır; sonuçlar kaybolmaz.
- **Tümü Rafından Açıldığında Çift Kapsam (Anime + Manga):** "Tümü" rafında hem anime hem manga listelendiği için, kaynak sayfası açıldığında her iki kapsam da birlikte taranır; rafta görünen içerikler sayfada silinmez.
- **Yenileme Başarısız Olsa Bile İçerik Koruma:** Ağ hatasında raftaki mevcut sonuçlar temizlenmez.
- **MAL Alakasız Popülerler Düşüşü Kaldırıldı:** MAL kaynağında spesifik sorgu sonuçsuz kalınca kullanıcıyı yanıltan genel popülerler listesine düşme engellendi.

### 🧾 Ek
- Sürüm adı `v2.4.206` olarak güncellendi.
- Kullanıcı talimatı doğrultusunda **yalnızca `foss` varyantı** derlendi ve yayınlandı (GMS derlenmedi).

---

## 🇬🇧 English (v2.4.206)

### 🛡️ 1. Crash Diagnostics & Reporting Overhaul (No More Silent Deaths)
- **Native Crash Handler (C++ - libkitsugi_crash_handler.so):** Catches SIGSEGV, SIGABRT, SIGBUS, SIGILL, SIGFPE, SIGTRAP using `_Unwind_Backtrace` in an async-signal-safe manner, writing raw backtraces to `native_crash.txt`.
- **Silent-Death Detector (KitsugiSessionSupervisor):** Discovers unhandled terminations (LMKD, post-ANR kills, SIGKILL) by scraping logcat on the subsequent launch, generating `unclean_exit.txt` and alerting the user with an "Unexpected Shutdown" dialog.
- **Action Breadcrumbs:** Tracks the last 40 user interactions and active screen keys to pinpoint the exact failure location.
- **Isolated Crash Screen:** Removed `Thread.sleep(800)` UI freezes. The crash screen (`KitsugiCrashActivity`) opens with its own `taskAffinity` in a dedicated process, protecting it from OS task teardown.
- **Cockroach Loop & Error Swallowing Fix:** Fixed unchecked swallowing of Cloudstream exceptions and eliminated the 100% CPU spinning loop on `Looper.loop()` exit.
- **Early Setup:** Initialized in `attachBaseContext()` to catch startup and ContentProvider faults.
- **Comprehensive Sharing:** "Send to Developer" bundles all diagnostic artifacts (`crash_log`, `crash_history`, `unclean_exit`, `native_crash`, `breadcrumbs`).

### 📋 2. Duplicate Lazy Key List Crashes Fixed
- Resolved `IllegalArgumentException: Key ... was already used` across 7 lists: Home grid, modern home rows, airing calendar (list & grid), expanded media grid dialog, manga extensions, custom list editor, and playback speed picker.

### 🖼️ 3. Image Downloads & Memory Hardening (OOM Prevention)
- **Sampled Decoding:** Notification icons downsample byte streams using `inSampleSize` instead of full-size bitmap decoding; capped downloads at 48 MB.
- **Android 10+ MediaStore Integration:** Saves downloaded images via MediaStore to `Downloads/Kitsugi/Images`, bypassing scoped storage issues on Android 11+.
- Logged gallery and download interactions into breadcrumbs.

### 🎭 4. Character Images & Live-Action Cast Photos
- **Candidate Title Resolver:** Resolves character images across all title variants (Turkish, Romaji, English, Japanese, synonyms).
- **Direct AniList Character Search Fallback:** Unmatched character records fall back to AniList character search with strict `realMalId` verification.
- **Preserved Cast Photos in Live-Action:** Actor photos are preserved, and live-action characters route directly to TMDB person profiles via `isRealMediaRole`.
- **Instant Detail Image:** Re-uses the list card thumbnail as an immediate placeholder on the detail page.
- **MAL-Only Jikan /pictures:** Prevents wasteful 404 network requests with TMDB IDs.

### ⚡ 5. Shikimori Tabs & Speed Optimization
- **Native Relations & Recommendations:** High-speed single-request fetching with batch cover resolution via Shikimori `/similar` and `/related`.
- **Staff Shelf Fallback:** Automatically queries Jikan staff data when Shikimori returns empty staff records.
- **25s Timeout & Single-Flight Protection:** Eliminates perpetual loading skeletons on unstable networks.
- **ID Resolution Safety:** Distinct caching and resolution between Shikimori IDs and MyAnimeList IDs.

### 🎬 6. Detail Screen Polish & Unified Info Row Copy Buttons
- **Unified Info Component (`DetailInfoValueRow`):** Both library entry and API result info cards now share the same row component with individual copy buttons on every row (Status, Start Date, End Date, Studio, Duration, Broadcast, Rating, English, Japanese).
- **Synonyms Chips with Individual Copy:** "Other Names" are displayed as individual chips, each with its own copy button. Prevents comma concatenation issues and ignores null/blank duplicates.
- **Compact Watch Button:** Replaced full-width watch buttons with clean, content-fitting buttons.

### 🔍 7. Search & "See All" Shelf Consistency
- **Handoff from Shelf to Source Page:** Clicking "See All" preserves the shelf's query, search scope (`SearchScope`), and pre-rendered results directly into `SourceSearchPage`.
- **Dual Scope for "All" Shelves:** Searching from "All" carries over both Anime and Manga scopes so items found on the shelf are never wiped upon opening the full source page.
- **Failure Resilience:** Network refresh failures preserve previously visible shelf results.
- **Removed Irrelevant MAL Popular Fallbacks:** Fixed MAL queries returning unrelated popular anime lists when search yielded zero matches.

### 🧾 Extras
- Version bumped to `v2.4.206`.
- Built and published exclusively as the `foss` release variant.

---

## 🇹🇷 Türkçe (v2.4.200)

### 🌍 TMDB & Simkl Başlık Dili Düzeltmesi (Türkçe → İngilizce → Romaji)

**Sorun:** TMDB sekmesindeki "Trend Animeler", "En Yüksek Puanlı Animeler" ve "Yakında Yayında" şeritlerinde, Türkçe başlık dili seçili olmasına rağmen Türkçesi bulunmayan içerikler Japonca/Çince (CJK) başlıklarla görünüyordu.

**Kök neden:** İngilizce yedek başlık isteği üretilirken `language=` parametresi `Regex("language=[^&]+")` ile değiştiriliyordu. Bu regex `with_original_language=ja` parametresinin içindeki `language=ja` kısmını da yakalayıp `with_original_language=en-US` yapıyordu. Sonuç: anime şeritlerinin (hepsi `with_original_language=ja` kullanır) İngilizce yedek isteği **tamamen farklı bir içerik listesi** döndürüyor, ID eşleşmesi tutmuyor ve Japonca başlıklar ekranda kalıyordu.

**Çözüm:**

- **Güvenli URL üretimi (`TmdbUrlUtils`):** `language` parametresi artık yalnızca tam parametre olarak değiştiriliyor; `with_original_language` ve diğer filtreler korunuyor. Yedek istek aynı içerik listesini döndürdüğü için ID eşleşmesi birebir tutuyor.
- **Merkezi başlık çözümleyici (`MediaTitleResolver`):** Türkçe → İngilizce → Romaji zinciri tek yerde tanımlandı. CJK başlıklar ilk üç adımda asla kabul edilmiyor; yalnızca hiçbir Latin alternatif yoksa (son çare) gösteriliyor.
- **Akıllı yedek istek (`TmdbTitleFallback`):** TMDB istenen dilde başlık bulamazsa orijinal (Japonca) başlığı döndürür. Uygulama bunu "yerelleştirilmiş başlık == orijinal başlık" karşılaştırmasıyla ve CJK taramasıyla yakalıyor; yalnızca gerektiğinde tek bir `en-US` isteği yapıp başlıkları birleştiriyor. Gereksiz ağ trafiği yok.
- **Kapsam:** Keşfet şeritleri (trend/popüler/en yüksek puanlı/yakında yayında + "Tümü" ekranları), arama sonuçları, medya detay sayfası (TMDB `alternative_titles` desteğiyle TR/US/JP alternatifleri), bölüm adları, yayın takvimi, yapım şirketi sayfaları, öneriler/ilişkiler ve oyuncu-ekip çalışmaları.
- **Simkl:** Simkl kayıtlarında önce TMDB üzerinden Türkçe başlık denenir; yoksa Simkl'in İngilizce/romaji alanlarına inilir. Simkl `title` alanı CJK geldiğinde ekrana düşmez.
- **Gösterim güvencesi:** TMDB/Simkl kaynaklı öğeler için kart ve liste başlıkları, seçilen başlık dilinden bağımsız olarak daima Latin alfabesinde bir alternatif bulur (Japonca seçilmediği sürece).
- **Önbellek tazeleme:** Bellek içi ve kalıcı keşfet önbelleği anahtarları artık dil + başlık çözümleme sürümü içeriyor. Böylece eski sürümden kalan hatalı (Japonca) kayıtlar servis edilmiyor, düzeltme anında görünür oluyor.

### 🧾 Ek
- Sürüm adı `2.4.200` olarak güncellendi.

---

## 🇬🇧 English (v2.4.200)

### 🌍 TMDB & Simkl Title Language Fix (Turkish → English → Romaji)

**Problem:** In the TMDB tab, shelves such as "Trend Animeler", "En Yüksek Puanlı Animeler" and "Yakında Yayında" showed Japanese/Chinese (CJK) titles for entries that had no Turkish translation, even though Turkish was the selected title language.

**Root cause:** When building the English fallback request, the `language=` parameter was rewritten with `Regex("language=[^&]+")`. That regex also matched the `language=ja` fragment inside `with_original_language=ja` and turned it into `with_original_language=en-US`. As a result, the English fallback request for anime shelves (all of which use `with_original_language=ja`) returned a **completely different result set**, ID matching failed, and raw CJK titles stayed on screen.

**Fix:**

- **Safe URL building (`TmdbUrlUtils`):** the `language` parameter is now replaced only as a standalone parameter; `with_original_language` and other filters are preserved, so the fallback request returns the same list and IDs match one-to-one.
- **Single central resolver (`MediaTitleResolver`):** the Turkish → English → Romaji chain is defined in one place. CJK titles are never accepted in the first three steps and appear only as a last resort when no Latin alternative exists.
- **Smart fallback fetch (`TmdbTitleFallback`):** TMDB returns the original (Japanese) title when it has no translation for the requested language. The app detects this by comparing localised vs. original titles and scanning for CJK, then performs a single `en-US` request only when needed.
- **Coverage:** explore shelves (trending/popular/top-rated/upcoming + "see all" screens), search results, media detail pages (via TMDB `alternative_titles`, TR/US/JP), episode names, airing calendar, production company pages, recommendations/relations and person credits.
- **Simkl:** Simkl entries try a TMDB lookup first for a Turkish title, then fall back to Simkl's English/romaji fields; CJK `title` values never reach the UI.
- **Display guarantee:** for TMDB/Simkl items, card and list titles always resolve to a Latin alternative regardless of the selected title language (unless Japanese is explicitly selected).
- **Cache refresh:** in-memory and persistent explore cache keys now include the language plus the title-resolution version, so stale (Japanese) entries from previous builds are ignored and the fix is visible immediately.

---

## 🇹🇷 Türkçe (v2.4.198)
### 🔞 +18 Bulanıklık & Yetişkin İçerik Güvenliği (Her Koşulda Tam Koruma)
- **46 Arayüz Yüzeyinde Eksiksiz Uygulama:** Ana sayfa, Keşfet şeritleri, Arama sonuçları, Karakter/Personel detayları, Galeri pencereleri ve Android TV arayüzlerinde +18 afiş ve görseller ayara tam uyumlu olarak maskelenir.
- **Eski Android Sürümleri İçin Yedek Motor (API < 31 Fallback):** Android 12 altındaki cihazlarda sistem düzeyindeki `Modifier.blur` sessizce devre dışı kaldığı için afişlerin açık kalması sorunu, Coil tabanlı `BlurTransformation` ve akıllı görsel boru hattı (`KitsugiNsfwImage`) ile çözüldü. Artık her cihazda bulanıklık garantilidir.
- **Platform Başına Doğrulanmış +18 Kuralları:**
  - **Kitsu:** R18 derecelendirmesi, `nsfw=true` veya hentai alt türü (`KitsuAdultFlags`).
  - **Shikimori:** Yalnızca `rx`/`hentai` yapımlar (`r`/`r_plus` ayrıldı).
  - **Simkl:** `adult=true` bayrağı ve resmi sertifikalar (`NC-17`, `XXX`, `18+`...) (`SimklAdultFlags`).
  - **AniList & TMDB:** `isAdult` ve `adult` alanları keşif takvimleri ve vitrinlere eksiksiz aktarılır.
  - **Manga Eklentileri:** API bayrağı olmayan kaynaklar için etiket ve tür analizi (`hentai`, `doujin`, `xxx`... ecchi hariç tutularak).
- **Kişisel Liste İçe Aktarımı:** Kitsu, Shikimori ve Simkl içe aktarımları `isAdult` bilgisini ilk andan itibaren kaydeder.

### 📌 Kaynak Seçimi Kalıcılığı & Kitsu Kimlik İzolasyonu
- **Keşfet:** Son seçili kaynak (Tümü / AniList / MAL / TMDB / Simkl / Kitsu / Shikimori) `SettingsDataStore` üzerinde saklanır ve uygulama yeniden açıldığında otomatik geri yüklenir. Otomatik kaynak düşüşleri seçim sayılmaz, kaydedilmez.
- **Arama:** Kaynak motoru + kapsam (sekme, platform, medya türü) birlikte hatırlanır; sekme çipleri ile motor seçicisi senkron kalır ve açılışta gereksiz arama tetiklenmez.
- **Listem:** Kaynak sekmesi `tab_index` ile kalıcıdır; Keşfet/Arama ile aynı davranış sergilemesi korunur.
- **Açılış maliyeti:** Son seçim TMDB/Tümü dışında bir kaynaksa splash sırasındaki 11 istekli TMDB önbellek ön-yüklemesi atlanır.
- **Kitsu Alakasız Ayrıntı Düzeltmesi:** Kitsu içe aktarmasında ham MAL ID'lerinin yazılmasından kaynaklanan sayısal çakışma (ör. Keep Your Hands Off Eizouken!) çözüldü. `KitsuIdNamespace` (`300M+1..399M+1`), stableId çözümleyici ve `KitsuIdentityMigration` eklendi.

### ♾️ Simkl Gerçek Sayfalama & Akış İyileştirmeleri (20'şer Sonsuz Kaydırma)
- **100 Sınırı Kaldırıldı:** Simkl artık statik bir sınırda durmak yerine, diğer kaynaklar gibi aşağı kaydırdıkça 20'şer 20'şer yeni içerik getirmeye devam eder.
- **Resmî Simkl API Endpoint Mimarisi:**
  - **Tür & Popülerlik Listeleri:** `genres/...` endpoint'leri üzerinden `page` ve `limit` parametreleriyle 20 sayfaya kadar (~1200 içerik) gerçek sayfalama.
  - **Yakında Yayında:** `anime/premieres/soon` ve `premieres/new` ile 20'şer içeriklik sayfalı akış.
  - **Trend Listeleri:** Sayfa parametresi bulunmayan Simkl trend JSON'ları için 500'lük Top snapshot (`today_500.json`, `week_500.json`) 1 saat boyunca önbelleğe alınır ve 20'şer dilimler halinde kaydırılır.
  - **Yayında Olanlar (Airing):** Takvim endpoint'inden tek seferde eksiksiz liste çekilir.
- **Arama Tarafında Sayfalama:** Simkl arama ve gelişmiş arama (`search`, `searchAdvanced`) `page` desteğine kavuştu. Hatalı path parçaları dokümandaki `all` standartlarına getirildi.
- **Zorunlu API Parametreleri & User-Agent:** Tüm isteklere `client_id`, `app-name=kitsugi`, `app-version` ve `KitsugiApp/... (Android)` User-Agent başlığı eklendi.
- **Kişisel Listeler:** "İzlemeye Devam" ve "Planladıklarım" listeleri tek seferde yüklendiği için gereksiz yere tekrar çekilmez.

---

## 🇹🇷 Türkçe (v2.4.196)
### 🛡️ Yanlış İçerik Eklenmesine Karşı Kimlik Güvencesi (CrossSyncIdentityGuard)
- **6 Sızıntı Vektörünün Kapatılması:**
  - **V1 (TMDB - ARM):** ARM servisine TMDB kimliği aktarımı kaldırıldı (film/dizi ID çakışması ve tüm franchise'ı kapsayan dizi ID'lerinin yanlış anime MAL ID'si üretmesi engellendi; yalnızca birebir MAL/AniList/Kitsu kimlikleri kullanılır).
  - **V2 (Simkl Anime - TMDB):** Simkl'a anime kayıtları için TMDB kimliği gönderilmesi tamamen engellendi. Simkl ID varsa yalnızca o gönderilir; dizi/film çakışmaları önlendi.
  - **V3 (Simkl Başlık Eşleme):** Güvenli kimliği (Simkl/MAL) olmayan animeler için Simkl'da başlık eşleştirmesi kapatıldı (`titleMatchingAllowed = false`). Benzer adlı film veya dizilerin listeye sızması engellendi.
  - **V4 (Kitsu Başlık Eşleme):** Kitsu başlık aramasında kısmi eşleşmeler (ör. "Oni Chichi" ⊂ "Oni Chichi 2") kapatıldı; birebir alias ve uyumlu yıl şartı getirildi.
  - **V5 & V6 (Tek Kaynak Kimlik Doğrulama):** Yeni `CrossSyncIdentityGuard` ile en az iki bağımsız kaynakça doğrulanmayan veya tek bir sağlayıcı eşlemesinden (Simkl `ids.mal`, Kitsu `mappings`, AniList `idMal`) gelen kimlikler için resmi MAL API / Jikan kataloğundan başlık ve yıl doğrulaması yapılır.
- **"Doğrulanamayan Kimlikle Asla Yeni Kayıt Eklenmez" Kuralı:** Başlık akrabalığı ve yıl kontrolünden geçemeyen şüpheli kimliklerle hiçbir platforma (AniList, MAL, Kitsu, Shikimori, Simkl) yeni kayıt eklenmez; atlanır ve raporda gerekçesiyle gösterilir. Mevcut kayıtların güncellenmesi platformun kendi kimliğiyle yapıldığı için bu kısıtlamadan etkilenmez.

### 📊 Platform Bazlı Eşitleme ve Hata Sayımı Düzeltmeleri
- **Simkl Sayım ve Makbuz İyileştirmesi:** 35'lik grupta tek bir eşleşmeyen öğe yüzünden tüm grubun "200 hata" olarak sayılması engellendi; kayıt bazlı makbuz (`Receipt.unmatched`) ile başarılı olanlar "eklendi", Simkl'de bulunamayanlar "atlandı" olarak işlenir. TV dizi ilerlemesi sınırı ve kısmi puan/geçmiş uyarıları artık grup hatası değil; eşitleme sonunda başlık listesiyle tek bir özet uyarı olarak raporlanır. 400M+ Shikimori kimliklerinin yanlışlıkla Kitsu ID olarak Simkl'e gönderilmesi düzeltildi (`300M..400M` aralık kontrolü).
- **Kitsu Resmi Mappings & 429 Yönetimi:** Resmi Kitsu `/mappings` API uç noktası (MAL ve AniList eşlemeleri) entegre edildi; manga ve ARM'da olmayan animeler için ID çözümleme oranı artırıldı. Kitsu istekleri için merkezi limiter ve 429 durumlarında 3 tekrarlı yeniden deneme mekanizması eklendi; API hata gövdeleri (`KitsuWriteResult`) okunarak ayrıntılı teşhis sağlandı. Kitsu içe aktarımına yayın yılı (`startYear`) eklendi.
- **AniList Kota ve Hız Limiti Optimizasyonu:** İstek aralığı 2100 ms'ye ayarlanarak (~28 req/dk) AniList'in fiili 30/dk hız sınırına tam uyum sağlandı. HTTP 429 durumlarında `Retry-After` başlığına duyarlı en fazla 3 kez otomatik yeniden deneme eklendi. İçe aktarımda zaten var olan `idMal` için her kayıtta atılan gereksiz sorgu kaldırılarak kota tasarrufu yapıldı.
- **MyAnimeList & Shikimori Özet Raporlama:** Çözülemeyen her kayıt için tek tek uyarı basılması yerine platform başına tek satırlık özet uyarı eklendi. Shikimori içe aktarımına `aired_on`/`released_on` yayın yılı ve İngilizce başlık eklendi. AniList içe aktarımında `seasonYear` boşsa `startDate.year` yedeği devreye alındı.

### 🧩 Kimlik Eşleştirme ve Çakışma Yönetimi
- **Aynı İsimli Yapımlar & Çelişen Kimlikler:** Berserk (1997/2016), Hunter x Hunter (1999/2011), Golden Time gibi aynı başlığa sahip yapımların tamamen izole edilip hiçbir yere yazılmaması sorunu çözüldü; artık ayrı gruplar halinde kendi kimlikleriyle yazılır.
- **Sahte "Kimlik Doğrulaması Gerekli" Gevşetmesi:** Farklı platformlardaki alias uyumsuzlukları yalnızca her iki tarafta da yıl biliniyor ve yıl farkı 1'den büyükse incelemeye düşer; aksi halde güvenle birleştirilir.
- **Durum ve İlerleme Doğruluğu:** Simkl'in toplam bölüm (`total`) sayısından "Tamamlandı" çıkarımı yapılması engellendi. Tek kaynaklı gruplarda gereksiz güncellemeler engellendi.
- **Temiz Rapor:** "Eşitleme kısmen tamamlandı" satırı hata yerine bilgi formatına dönüştürüldü.

### 📱 Yenilenen Tam Ekran Çapraz Eşitleme Paneli
- **Tam Ekran Dialog & Çentik Uyumu:** Panel artık `fullScreen = true` olarak açılır; durum çubuğu ve ekran çentikleri (`displayCutoutPadding`) ile kusursuz uyum sağlar.
- **Dikey ve Yatay Yönlendirme:** Dikey modda tek sütunlu akıcı hiyerarşi; yatay modda sol panelde ilerleme/hesaplar/eylemler, sağ panelde filtreler ve canlı günlük gösterilir.
- **Filtre Çipleri & Kompakt Hesap Kartları:** Hata ve uyarı sayıları filtre çiplerine rozet olarak eklendi; platform logolu kartlar ve 40 dp optimize butonlarla daha kompakt bir görünüm sunar.

---

## 🇹🇷 Türkçe (v2.4.195)
### 🌐 Keşfet "Tümü" — Kaynak Bazlı Keşif Alanları & Kategori Gruplaması
- **Kaynaklara Özel Bağımsız Alanlar:** AniList, MyAnimeList, TMDB, Simkl, Kitsu ve Shikimori platformları birbirine karıştırılmadan kendi logolu, renk vurgulu bağımsız başlıklarına kavuştu.
- **Kategori Ayrımı:** Trendler, en iyiler, popülerler, sezon içerikleri ve ilgili platformun desteklediği kategoriler doğrudan kendi kaynağının altında listelenir.
- **Sabit Kaynak & Kategori Çubuğu:** Ekranın üstündeki sabit çubukla doğrudan istenilen platforma veya kategori şeridine anında atlanabilir; uzun sayfalarda sayfa başına dönüş düğmesi eklendi.
- **Bağımsız Daraltma & Dayanıklı Durum:** Her kaynak alanı ayrı ayrı daraltılıp genişletilebilir; daraltma durumları ekran döndürmelerinde veya gezinmede korunur.
- **Akıllı "Tümünü Gör":** Çoklu görünümdeki "Tümünü Gör" butonu doğru kaynağın ve kategorinin devam sayfasını açar.
- **İzole Hata Yönetimi & Yeniden Deneme:** Bir kaynak yüklenemezse veya hata verirse diğer kaynaklar görüntülenmeye devam eder; yalnızca sorunlu kaynak tek dokunuşla yeniden denenebilir.
- **Büyük Ekran & TV Uyumu:** Ortak kaynak alanları mobil, tablet ve Android TV D-pad gezinmesiyle tam uyumlu hale getirildi.

### 📋 Listem "Tümü" Sekmesi, Temsilci Kaynak & Simkl TMDB Detay Geçişi
- **Sola Kaydırma & Sekme Sırası Düzeltmesi:** "Tümü" sekmesi gerçekten ilk sıraya (`index 0`) taşındı. Pager ile üst buton dizilimi eşitlenerek Tümü sayfasından sola kaydıramama sorunu giderildi.
- **Öncelikli Temsilci Kaynak:** Birleşik kütüphanede aynı yapımın farklı servislerdeki kayıtları birleştirilirken öncelik zinciri uygulandı: `AniList > MAL > Kitsu > Shikimori > Simkl > TMDB`. Animelerde otomatik olarak AniList öne çıkarken, dizi ve filmlerde doğal olarak Simkl/TMDB temsilci kalır.
- **Hızlı Simkl Detayları (TMDB Entegrasyonu):** Simkl kayıtlarının ayrıntı sayfaları doğrudan TMDB üzerinden açılır (`tmdbId`, ARM ID çözücü veya arama). Simkl'in yavaş API yanıtı detay ekranını geciktirmez.
- **Varsayılan Manuel Kayıt:** Tümü sekmesinde manuel kayıt eklerken varsayılan hedef AniList olarak ayarlandı.

### 🎬 Akış ve Kalite Bilgi Doğruluğu (StreamInfoFix)
- **"400p" Kök Neden Düzeltmesi:** CloudStream eklentilerinin bilinmeyen kalite döndürdüğünde (`Qualities.Unknown = 400`) ekrana "400p" basması sorunu giderildi.
- **Sahte Dil ve Rozet Çıkarmalarının Kaldırılması:** "Subaru" kelimesinden "Altyazılı", "Dubai" kelimesinden "Dublaj" basan hatalı alt dize filtreleri kaldırıldı. "Türkçe eklenti ⇒ Altyazılı" varsayımı ve kanıtsız "🎬 Standart" etiketi temizlendi.
- **Gerçek Ölçüm ve HLS Çözümleme (`StreamProbe`):** HLS master playlist'i üzerinden `#EXT-X-STREAM-INF` (çözünürlük) ve `#EXT-X-MEDIA` (ses/altyazı dilleri) etiketleri incelenerek gerçek veriler rozetlere yansıtılır. Yüklenen altyazılar `CC TR` / `CC EN` olarak gösterilir.
- **Arayüz ve Filtre Uyumu:** Kart rozetleri, alt sayfa akış seçici, filtre çipleri ve TV arayüzü yeni `StreamInfoResolver` verisiyle senkronize edildi.

### 🔐 Shikimori Yetkilendirme & Gradle Bellek Optimizasyonu
- **Shikimori 401 Otomatik Yenileme:** Sunucu tarafında geçersiz kılınan token'larda liste çekimi sırasında otomatik refresh yapılarak kullanıcı oturumunun kopması önlenir.
- **Otomatik RAM Temizliği:** `gradle.properties` içine 60 saniyelik daemon zaman aşımı eklendi; derleme bittikten sonra arkada asılı kalan Gradle/JDK süreçlerinin bellek işgali önlendi.

---

## 🇬🇧 English (v2.4.195)
### 🌐 Explore "All" — Dedicated Source Sections & Grouped Categories
- **Dedicated Platform Sections:** AniList, MyAnimeList, TMDB, Simkl, Kitsu, and Shikimori each receive dedicated branded headers with custom accent colors and descriptions instead of intermixed rails.
- **Source-Scoped Rails:** Trending, top-rated, popular, seasonal, upcoming, and other platform categories stay strictly grouped under their respective provider section.
- **Sticky Platform Navigation:** Sticky header bar allows immediate jumping directly to any provider (AniList, MAL, TMDB, etc.) or category rail, with a quick return-to-top shortcut.
- **Collapsible Sections & Saved State:** Each provider section can be independently collapsed or expanded, preserving state across screen rotations and navigation.
- **Accurate "See All" Routing:** Tapping "See All" navigates to the dedicated category view matching the exact source and filter.
- **Isolated Provider Failures & Retry:** If a single provider fails to load or times out, all other providers remain intact; users can retry individual failed providers independently.
- **TV & Large Screen Support:** Unified source sections fully support Android TV D-pad focus, navigation, and tablet landscape layouts.

### 📋 My List "All" Tab, Representative Hierarchy & Fast Simkl Details
- **Tab Ordering & Swiping Fix:** The unified "All" tab is now index 0. Synchronized pager and pill tabs eliminate the gesture lock where users could not swipe left from the "All" page.
- **Smart Representative Provider Hierarchy:** Multi-provider entries deduplicate using prioritized representative selection: `AniList > MAL > Kitsu > Shikimori > Simkl > TMDB`. Anime defaults to AniList, while TV shows and movies naturally maintain Simkl/TMDB representation.
- **Lightning Fast Simkl Details via TMDB:** Simkl entries now route details through TMDB (`tmdbId`, ARM resolver, or fallback search), removing slow Simkl API bottlenecks.
- **Default Manual Entry:** Manual additions on the "All" tab default to AniList.

### 🎬 Accurate Stream Information (StreamInfoFix)
- **"400p" False Badge Root Fix:** Resolved issue where `Qualities.Unknown = 400` in CloudStream plugins resulted in a "400p" label.
- **Removed Heuristic Audio/Subtitle Inferences:** Fixed substring matching bugs (e.g., "Subaru" triggering Subtitle, "Dubai" triggering Dubbed). Eliminated automatic "Turkish Plugin ⇒ Subtitled" assumption and baseless "🎬 Standard" labels.
- **Real Stream Probing (`StreamProbe`):** Analyzes HLS playlists for actual `#EXT-X-STREAM-INF` resolutions and `#EXT-X-MEDIA` audio/subtitle tracks. Verified subtitle files display as `CC TR` / `CC EN`.
- **Synchronized UI & Filters:** Stream cards, bottom sheets, filter chips, and TV screens are fully wired to the accurate `StreamInfoResolver` output.

### 🔐 Shikimori 401 Silent Token Refresh & Memory Optimizations
- **Shikimori 401 Auto-Recovery:** Automatic token refresh on 401 errors during rate syncing prevents unneeded session expirations.
- **Gradle/JDK Daemon Idle Cleanup:** Added 60s idle timeout to `gradle.properties` and post-build cleanup scripts to stop lingering Java daemons from holding system RAM.

---

## 🇹🇷 Türkçe (v2.4.194)
### ⚡ Çapraz Eşitleme (Cross-Sync) %0 Takılma Düzeltmesi & Gelişmiş Tanılama
- **Aday İndeksleme ve Performans Optimizasyonu:** Eşleştirme aşamasında her yeni içerik için tüm birleştirilmiş kayıtların baştan sona taranması ($O(N^2)$) kaldırıldı. Kimlik ve başlık indeksleriyle (`CrossSyncCandidateIndex`) aday arama daraltıldı; binlerce kayıtta donma ve takılmalar engellendi.
- **Canlı İlerleme Sayacı:** Eşleştirme aşamasında sayaç her 25 kaynak kaydında güncellenir ve coroutine iptal kontrolleri düzenli çalışır.
- **Dinamik Durum ve Duraklama Uyarıları:** Ağ beklerken sahte %0 yerine belirsiz (indeterminate) ilerleme göstergesi sunulur. Ekrana geçen süre, tahmini kalan süre ve uzun süren duraklamalarda uyarı eklendi.
- **Gelişmiş Günlük & Rapor Paylaşımı:** Günlükte Tümü / Sorunlar / Hatalar / Uyarılar filtreleri ve genişletilebilir teknik ayrıntılar yer alır. Eşitlemeyi onaylayarak durdurabilir, sürerken kısmi raporu paylaşabilir veya tamamlanınca raporu dışa aktarabilirsiniz.
- **Otomatik Rapor ve Güvenlik:** Raporlar otomatik olarak `Downloads/Kitsugi/CrossSyncReports` dizinine kaydedilir; cihaz ve ortam bilgileri eklenir, Bearer ve API anahtarları maskelenir (`[REDACTED]`). Hesaplardan içerik silinmez.
- **İstem Dışı Eşitleme Koruması:** "Son Eşitleme Raporu"na dokunulduğunda yanlışlıkla yeni bir eşitleme başlatılması engellendi.

### 🌐 Listem Sayfası Birleşik "Tümü" Görünümü & Dinamik Platform Rozetleri
- **Tüm Kaynakları Tek Ekranda Birleştirme:** Listem sekmesine AniList, MyAnimeList, Simkl, Kitsu ve Shikimori kütüphanelerini tek ekranda toplayan "Tümü" seçeneği eklendi.
- **Tekil Gösterim & Yinelenme Koruması:** Farklı servislerde bulunan aynı yapım yinelenmek yerine tek kartta birleştirilir (`groupMyListEntries`).
- **Dinamik Kaynak Rozetleri:** Kartların altında yapımın hangi platformlarda bulunduğunu gösteren orijinal platform logoları (`FlowRow` ile dar kartlarda taşmadan) sergilenir.
- **Tüm Düzenlerle Uyumlu:** Kompakt, rahat, ayrıntılı, 2 sütunlu grid ve minimalist kart düzenleriyle tam uyumludur.
- **Tek Dokunuşla Yenileme:** Birleşik görünümde aşağı çekip yenileme yapıldığında bağlı tüm aktif sağlayıcılar sırayla eşitlenir.

---

## 🇬🇧 English (v2.4.194)
### ⚡ Cross-Sync 0% Stall Fix & Advanced Diagnostics
- **Candidate Indexing & Performance:** Eliminated $O(N^2)$ quadratic scanning during cross-platform media grouping. Added identity & title candidate index (`CrossSyncCandidateIndex`), speeding up grouping on multi-thousand entry libraries.
- **Live Progress Reporting:** Progress counters update every 25 source records during grouping, with periodic cooperative coroutine cancellation checks.
- **Indeterminate Progress & Stall Warnings:** Displays indeterminate progress indicators during network/verification steps instead of a false 0% determinate bar. Added elapsed time, estimated time, and long-stall warnings.
- **Advanced Log Filtering & Report Sharing:** Logs feature All / Issues / Errors / Warnings filters and expandable technical stacktraces. Allows confirmed cancellation, sharing partial reports while sync is active, and exporting final reports.
- **Auto Report Storage & Credential Redaction:** Diagnostic reports are automatically saved to `Downloads/Kitsugi/CrossSyncReports/` with environment details and redacted credentials (`[REDACTED]`). No items are deleted from upstream accounts.
- **Accidental Sync Trigger Prevention:** Viewing the "Latest Sync Report" no longer accidentally launches a new synchronization.

### 🌐 My List Unified "All" View & Dynamic Platform Badges
- **Unified Library Tab:** Added an "All" option to the My List screen consolidating libraries across AniList, MyAnimeList, Simkl, Kitsu, and Shikimori in one view.
- **Deduplicated Media Grouping:** Identical media titles across multiple connected providers are unified into a single card representation.
- **Dynamic Platform Badges:** Cards display responsive badges (`FlowRow`) indicating all connected platforms hosting the entry.
- **Layout Compatibility:** Fully supported across compact, comfortable, large, 2-column grid, and minimalist card designs.
- **Unified Pull-to-Refresh:** Pulling to refresh in the combined view sequentially syncs all active connected providers.

---

## 🇹🇷 Türkçe (v2.4.193)
### 🔍 Arama Deneyimi (UX) Yenilemesi & "Tümünü Gör" Ayrık Kaynak Sayfası

- **Arama Sayfası Sadeleştirmesi:** Arama çubuğunun altındaki kalabalık kategori çipleri, sıralama seçici, filtreler ve platform bilgi satırı ana sayfadan kaldırılarak sağ üstteki filtre butonunun açtığı `SourceEngineFilterSheet` altında toplandı. Sayfa temizlendi.
- **Kompakt Filtre Özeti:** Varsayılandan farklı bir filtre/kapsam seçildiğinde tek satırlık kompakt özet çipi görüntülenir ve tıklandığında ayar panelini açar.
- **"Tümünü Gör" Artık Ayrık Sayfa (`SourceSearchPage`):** Çoklu arama raflarındaki "Tümünü Gör" artık aynı sayfada sekme değiştirmek yerine geri butonu, başlık ve dikey sonuç listesi içeren bağımsız bir sayfada açılır.
- **İzole ViewModel:** `SourceSearchPage` kendi izole `SearchViewModel` örneğini kullanır; burada yapılan filtre ve arama değişiklikleri ana çoklu arama durumunu etkilemez.
- **Shikimori 1-Tık OAuth Callback Düzeltmesi (`redirect_uri` Uyuşmazlığı):** Shikimori OAuth yetkilendirme isteğindeki callback adresinin uyuşmaması ("The requested redirect uri is malformed or doesn't match client redirect URI") sorunu giderildi. Varsayılan 1-tık girişi resmî `aniyomi://shikimori-auth` callback'ine bağlandı; özel kayıtlı uygulamalar için `kitsugi://shikimori-auth` alternatifi korundu ve regresyon birim testleri eklendi.

---

## 🇬🇧 English (v2.4.193)
### 🔍 Search UX Overhaul, Dedicated "See All" Screen & Shikimori 1-Tap Fix

- **Clean Search Screen:** Cluttered chips, sorting dropdowns, and info banners removed from under the search bar and unified into the top-right `SourceEngineFilterSheet`.
- **Compact Active Filter Summary:** An unobtrusive summary chip appears only when non-default filters or scopes are active.
- **Dedicated "See All" Screen (`SourceSearchPage`):** Tapping "See All" opens a full standalone screen with back navigation, search bar, filters, and vertical results.
- **Isolated ViewModel:** Uses an independent `SearchViewModel` instance so altering filters on source search does not corrupt multi-search state.
- **Shikimori 1-Tap OAuth Callback Fix (`redirect_uri` Mismatch):** Fixed the OAuth authorization rejection ("The requested redirect uri is malformed or doesn't match client redirect URI"). Default 1-tap flow now uses the official registered `aniyomi://shikimori-auth` callback with `kitsugi://shikimori-auth` preserved for custom apps, verified via unit tests.

---

## 🇹🇷 Türkçe (v2.4.194)
### 🔔 Bildirim Altyapısı Denetimi — 5 Kaynak (AniList · MAL · Simkl/TMDB · Kitsu · Shikimori)

- **Bildirim Teşhisi (yeni):** Bildirimler ekranının sağ üstüne 🪲 butonu eklendi. Tek dokunuşla 5 kaynağa **gerçek istek** atılır; hesap bağlı mı, hangi uç nokta kullanılıyor, **HTTP durum kodu**, dönen kayıt sayısı, süre ve varsa hata + çözüm ipucu gösterilir. Artık "geliyor mu gelmiyor mu" tahmine kalmaz.
- **AniList — sessiz hata yutma kaldırıldı:** İstek başarısız olduğunda istemci boş liste döndürüp "bildirim yok" gösteriyordu; artık GraphQL hataları gerçek mesajla gösterilir. Okunmamış bildirim sayacı eklendi ve sekmede rozet olarak görünür.
- **Shikimori — gerçek bildirim akışı:** Uygulama bugüne kadar **izleme geçmişini** bildirim gibi gösteriyordu. Resmî uçlar (`GET /api/users/:id/messages?type=notifications|news` ve `/unread_messages`) eklendi; `messages` izni yoksa ekran bunu ve nasıl verileceğini açıklar, geçmiş listesine güvenli düşer. OAuth izni artık client_id'ye göre seçilir (kendi uygulamanızı girerseniz `user_rates messages`).
- **Simkl — deprecated takvim yolu düzeltildi (kritik):** Eski kod, **1 Şubat 2027'de güncellenmeyi bırakacak** olan v2 olmayan dosyaları okuyor ve sonucu **ilk 50 öğeyle** kesiyordu; eşleşme bu yüzden neredeyse hiç tutmuyordu. Yeni `SimklCalendarClient` resmî `data.simkl.in/calendar/v2/…` dosyalarını (`{calendar, metadata}`) ve kullanıcının `/sync/all-items/*/watching` listesini kullanır (simkl/tmdb/mal ID eşleşmesi, ±7 gün penceresi).
- **MyAnimeList — takvim haftası tuzağı:** Akış, içinde bulunulan takvim haftasına bağlı olduğu için hafta başında boşalıyor ve `id`'ye göre sıralanıyordu. Artık kayan **son 7 gün** penceresi kullanılır, doğru sıralanır ve listenin ne olduğu (MAL API'sinin kişisel bildirim sunmadığı) bilgi bandında yazılır.
- **Kitsu — sonraki bölüm tarihleri:** Kitsu'nun genel API'sinde bildirim ucu yoktur; buna karşılık anime kaynağındaki `nextRelease` alanı okunup "Bölüm N • yayınlandı/yaklaşan" akışı üretilir.
- **Arka plan işçisi güçlendirildi:** Simkl'de bölüm başına ayrı bildirim (eskiden dizi başına 1), yalnızca son 24 saatte yayınlananlar; MAL için hafta sınırına takılmayan pencere; **Kitsu ve Shikimori için yeni arka plan desteği** ve Ayarlar > Bildirimler'de iki yeni anahtar.
- **Ayrıntılı rapor:** `docs/audits/NOTIFICATION_INFRASTRUCTURE_AUDIT_2026-10-07.md`

---

## 🇬🇧 English (v2.4.194)

### 🔔 Notification Infrastructure Audit — All 5 Sources (AniList · MAL · Simkl/TMDB · Kitsu · Shikimori)

- **Notification Diagnostics (new):** a 🪲 action in the Notifications screen fires **real requests** at all five sources and reports connection state, endpoint, **HTTP status**, item count, duration, plus an error and a fix hint. No more guessing whether notifications actually arrive.
- **AniList — silent errors removed:** on failure the client returned an empty list (looking like "no notifications"); GraphQL errors are now surfaced with their real message. The unread notification count is fetched and shown as a tab badge.
- **Shikimori — real notification feed:** the app used to present the **watch history** as notifications. Official endpoints (`GET /api/users/:id/messages?type=notifications|news`, `/unread_messages`) are now used, with a clear in-app explanation and a safe history fallback when the `messages` scope is missing. The OAuth scope is now chosen per client id (use your own app to get `user_rates messages`).
- **Simkl — deprecated calendar path fixed (critical):** the old code read the pre-v2 files that **stop updating on 1 February 2027** and truncated results to the **first 50 items**, which is why matches almost never appeared. The new `SimklCalendarClient` reads the official `data.simkl.in/calendar/v2/…` files (`{calendar, metadata}`) and joins them with the user's `/sync/all-items/*/watching` list (simkl/tmdb/mal ID match, ±7 day window).
- **MyAnimeList — calendar-week trap:** the feed was bound to the current calendar week (empty at the start of a week) and sorted by string id. It now uses a rolling **last 7 days** window, correct ordering, and an info banner explaining that the MAL API provides no personal notifications.
- **Kitsu — next episode dates:** Kitsu's public API has no notification endpoint, but the anime resource's `nextRelease` field is now parsed into an "Episode N • aired/upcoming" feed.
- **Hardened background worker:** per-episode Simkl notifications (previously one per show), last-24-hours only; a week-boundary-free window for MAL; **new background support for Kitsu and Shikimori** with two new toggles in Settings > Notifications.
- **Full report:** `docs/audits/NOTIFICATION_INFRASTRUCTURE_AUDIT_2026-10-07.md`

---

## 🇹🇷 Türkçe (v2.4.194)

### 🛡️ Kitsu Senkronizasyon & Çoklu Kayıt Çökme Koruması (Crash Fix)

- **Kitsu ve Platform Eşitleme Çökmesi Giderildi (`IllegalArgumentException: Belirsiz kitsu eşlemesi...`):** `MediaEntryRepository.smartImport` içerisinde yer alan katı `require(matches.size <= 1)` koşulu kaldırıldı. Veritabanında aynı yapıma ait birden fazla yerel kayıt veya benzer başlık bulunduğunda uygulamanın `KitsugiCrashActivity` ile çökmesi engellendi; ID, ana başlık ve güncellik sırasına göre en uygun kaydı seçip güvenle güncelleyen akıllı çözümleyici uygulandı.
- **Franchise & Çoklu Film Başlık İzolasyonu:** `MediaIdentity.sameMedia` fonksiyonu güçlendirildi. `Date A Bullet: Dead or Bullet` ve `Date A Bullet: Nightmare or Queen` gibi aynı seriye ait yapımların yalnızca genel Japonca franchise adı (`デート・ア・バレット`) paylaşıldığı için yanlışlıkla aynı yapım sanılması engellendi.
- **Yedek Geri Yükleme ve Çapraz Eşitleme Koruması:** `MediaEntryBackup.mergeAndSyncEntries` ve `AuthViewModel.clusterEntry` adımlarındaki tüm fırlatıcı `require` kontrolleri güvenli eşleme mekanizmasıyla değiştirildi.
- **Hata Yakalama & İzolasyon Güvencesi:** `AuthViewModel` içerisindeki tüm harici platform import ve sunucu doğrulama adımları `runCatching` kalkanına alındı; bir platformda beklenmeyen bir durum oluşsa dahi diğer platformlar ve kullanıcı arayüzü kesintiye uğramadan çalışmaya devam ediyor.

### 🚀 Shikimori OAuth Girişi Kök Neden Düzeltmesi (Yönlendirme + Authorization Header Kaybı)

- **Giriş Başarısızlığının Kök Nedeni Çözüldü:** Shikimori production ortamında diğer domain'ler (örn. `shikimori.one`) `shikimori.io` adresine 301 ile yönlendiriliyor. OkHttp, host değiştiren yönlendirmelerde güvenlik nedeniyle `Authorization` header'ını otomatik SİLİYORDU. Bu yüzden token takası başarılı olup geçerli jeton elde edilmesine rağmen, hemen ardından yapılan `whoami` doğrulaması "oturumsuz" istek olarak ulaşıp `200 + null` döndürüyor ve uygulama **geçerli jetonu çöpe atarak** "jeton geçersiz görünüyor" hatasıyla kullanıcıyı yanıltıyordu (kullanıcı aynı kodla yeniden denediğinde kod tükendiği için `invalid_grant` alıyordu).
- **Başlıklar Korunan Yönlendirme Takibi:** Shikimori istemcisine (`ShikimoriApiClient`) özel bir HTTP istemci eklendi; 301/302/303/307/308 yönlendirmeleri, orijinal `Authorization` header'ı korunarak manuel takip ediliyor (en fazla 3 atlayış, RFC 7231'e uygun POST→GET dönüşümüyle). Artık hangi Shikimori domain'ine istek atılırsa atılsın oturum bilgisi kaybolmuyor.
- **Kanonik Host'a Doğrudan İstek:** Tüm API/token uçları artık production'un resmi host'u olan `shikimori.io` adresine doğrudan gider (`shikimori.one` yedek olarak korunur). Gereksiz yönlendirme atlayışı ortadan kalktı.
- **whoami Artık Girişi Düşürmüyor:** Giriş akışında (hem 1-tık deep-link hem manuel kod) yetki kodu takas edilir edilmez token derhal saklanıyor; profil bakımı (whoami, 3 denemeli) geçici olarak başarısız olsa bile oturum korunuyor ve kullanıcı kimliği ilk senkronizasyon/profil/aktarım işleminde tembel olarak çözümleniyor (`ensureShikimoriUserResolved`). Tekrar tekrar "yeniden giriş" döngüsü ve tüketilmiş kod hataları bitti.
- **Ölü Refresh Token Temizliği:** Sunucu refresh token'ı net biçimde reddediyorsa (400/401 `invalid_grant`/`invalid_client`) oturum sessizce saklanıp 401 döngüsü yerine temizleniyor ve kullanıcı yeniden girişe yönlendiriliyor.

### 📊 Kapsamlı Çapraz Eşitleme Hata Raporu ve Eşleştirme Güvenliği

- **Tam Eşitleme ve Hata Raporlama:** Eşitleme penceresindeki 500 satırlık canlı görünüm sınırından bağımsız olarak, tüm oturum boyunca gerçekleşen işlemler, API hataları, eşleştirme kararları ve platform istatistikleri eksiksiz bir rapora kaydedilir.
- **Otomatik Rapor Dosyası ve Dışa Aktarma:** Android 10+ cihazlarda raporlar otomatik olarak `İndirilenler/Kitsugi/CrossSyncReports/` altına `.txt` formatında kaydedilir. Ayrıca eşitleme penceresine eklenen "Raporu Kaydet" düğmesiyle rapor istenen herhangi bir konuma dışa aktarılabilir.
- **Sağlayıcı Kimlik Çatışması Koruması:** Aynı tür ve başlık için farklı servislerden gelen kimlikler birbiriyle çelişiyorsa (`MediaIdentity.conflictingIdentityKeys`) veya belirsiz birden fazla aday grup çözülemiyorsa kayıt diğer platformlara yazılmadan güvenlik için atlanır ve nedeni detaylarıyla rapora yazılır.
- **Hassas Veri İzolasyonu:** Oluşturulan raporda Bearer token, access/refresh token ve client secret gibi tüm hassas kimlik doğrulama anahtarları otomatik olarak maskelenir (`[REDACTED]`).

---

## 🇬🇧 English (v2.4.194)

### 🛡️ Kitsu Sync & Disambiguation Crash Fix

- **Resolved Fatal Crash in Kitsu & Multi-Platform Sync (`IllegalArgumentException: Belirsiz kitsu eşlemesi...`):** Replaced hard-failing `require(matches.size <= 1)` check in `MediaEntryRepository.smartImport` with graceful disambiguation. If multiple local records exist for an entry (or during complex imports), the system prioritizes exact provider key, exact primary title, English title, and latest update timestamp without throwing unhandled exceptions.
- **Franchise Japanese Title Bleed Prevention:** Enhanced `MediaIdentity.sameMedia` comparison so franchise movies sharing generic Japanese series titles (e.g. `Date A Bullet: Dead or Bullet` vs `Nightmare or Queen`) are not conflated as the same media when primary titles differ.
- **Safe Backup Merge & Cross-Sync Clustering:** Replaced assertion throws in `MediaEntryBackup.mergeAndSyncEntries` and `AuthViewModel.clusterEntry` with deterministic matching fallbacks.
- **Fault-Tolerant Coroutine Safety:** Guarded all platform import invocations in `AuthViewModel` with `runCatching` to prevent background coroutine cancellations from bringing down the UI or interrupting remaining platforms.

### 🚀 Shikimori OAuth Login Root-Cause Fix (Redirect + Authorization Header Loss)

- **Login Failure Root Cause Resolved:** In Shikimori production, other domains (e.g. `shikimori.one`) 301-redirect to `shikimori.io`. OkHttp automatically STRIPS the `Authorization` header on host-changing redirects for security. As a result, the token exchange succeeded and produced a valid token, but the immediately-following `whoami` verification arrived as an unauthenticated request, received `200 + null`, and the app **discarded the valid token** with a misleading "token appears invalid" error (retries with the same code then failed with `invalid_grant` because the code was already consumed).
- **Header-Preserving Redirect Following:** The Shikimori client (`ShikimoriApiClient`) now uses a dedicated HTTP client that manually follows 301/302/303/307/308 redirects while preserving the original `Authorization` header (max 3 hops, RFC 7231-compliant POST→GET conversion). The session no longer gets lost regardless of which Shikimori domain the request lands on.
- **Direct Requests to the Canonical Host:** All API/token endpoints now hit `shikimori.io` (the production canonical host) directly; `shikimori.one` is kept as a fallback. Unnecessary redirect hops are eliminated.
- **whoami No Longer Kills Login:** In both the 1-tap deep-link and manual code flows, the token is saved immediately after the authorization-code exchange. If the profile lookup (whoami, with 3 retries) temporarily fails, the session is preserved and the user identity is resolved lazily on first sync/profile/import (`ensureShikimoriUserResolved`). The repeated re-login loop and consumed-code errors are gone.
- **Dead Refresh Token Cleanup:** When the server explicitly rejects the refresh token (400/401 `invalid_grant`/`invalid_client`), the session is cleared and the user is prompted to reconnect instead of silently looping on 401s.

### 📊 Comprehensive Cross-Sync Diagnostic Report & Matching Safety

- **Full Sync & Error Reporting:** Independent of the live dialog's 500-entry limit, complete session operations, API error chains, disambiguation decisions, and platform statistics are written to an exhaustive diagnostic report.
- **Automatic Report Storage & Export:** On Android 10+, reports are automatically persisted to `Downloads/Kitsugi/CrossSyncReports/` as `.txt` files. A "Save Report" button in the cross-sync dialog also allows saving the report to any user-selected location.
- **Provider Conflict Prevention:** If entries of the same type share titles but have conflicting provider IDs (`MediaIdentity.conflictingIdentityKeys`) or cannot be safely disambiguated across multiple candidates, writing to target platforms is skipped for safety and logged to the report.
- **Credential Redaction:** Bearer headers, access/refresh tokens, and client secrets are automatically redacted (`[REDACTED]`) before reports reach disk.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 MyAnimeList Çapraz Senkronizasyon & Shikimori OAuth Doorkeeper Düzeltmesi

- **MyAnimeList Çapraz Eşitleme URL Hatası Çözüldü (`Illegal character in query at index 193`):** MAL API v2 kullanıcı listesi (`/animelist` ve `/mangalist`) isteklerindeki `fields` parametresi içinde yer alan iç içe alan seçicisi (`list_status{...}`) süslü parantezleri (`%7B` ve `%7D`) olarak URL encode edildi. Böylece `java.net.URI`'nin parantezleri reddedip çapraz eşitlemeyi %0'da durdurması tamamen engellendi.
- **Shikimori Doorkeeper Token İstek Düzeltmesi:** Token takas isteğine (`POST /oauth/token`) `redirect_uri` parametresi geri eklendi; `400 Missing required parameter: redirect_uri` hatası tamamen giderildi.
- **Dinamik Pending Redirect URI Eşleştirmesi:** Tarayıcının açıldığı yönlendirme adresi (`kitsugi://`, `urn:ietf:wg:oauth:2.0:oob` veya `aniyomi://`) hafızaya alınarak token takası sırasında sunucuya birebir aynı adres gönderilir.
- **Ham JSON Hatası Engellendi:** Shikimori'nin geçersiz oturumlarda döndürdüğü `null` yanıtının parse edilmesiyle oluşan ham `A JSONObject text must begin with '{'` hatası giderildi; tüm yanıtlar null/HTML kontrolleriyle korumaya alındı.
- **Gelişmiş Giriş Diyaloğu & Alternatif Şema:** Giriş penceresine "Shikimori OAuth Uygulamalarım" yönetim bağlantısı ve eski şemayı kullananlar için "Alternatif Tek Tık (aniyomi://)" butonu eklendi.

---

## 🇬🇧 English (v2.4.194)

### 🚀 MyAnimeList Cross-Sync Query Encoding & Shikimori OAuth Doorkeeper Fix

- **MyAnimeList Cross-Sync Query Parsing Fix (`Illegal character in query at index 193`):** Percent-encoded nested field selector braces (`%7B` and `%7D`) in MAL `/animelist` and `/mangalist` endpoints. Fixed `java.net.URI` query syntax crash that aborted cross-account sync at 0%.
- **Shikimori Doorkeeper Token Payload Fix:** Restored `redirect_uri` parameter in token exchange requests, eliminating `400 Missing required parameter: redirect_uri`.
- **Dynamic Pending Redirect URI Matching:** Pairs the authorization redirect URI (`kitsugi://`, `urn:ietf:wg:oauth:2.0:oob`, or `aniyomi://`) with the token exchange step as required by Doorkeeper OAuth.
- **Guarded JSON Parsing:** Eliminated raw `A JSONObject text must begin with '{'` exceptions when encountering `null` or non-JSON payloads from expired session endpoints.
- **Enhanced Login Dialog & Fallback Flows:** Added direct link to Shikimori OAuth management dashboard and an "Alternative 1-Tap (aniyomi://)" button in the login dialog.

---

---

- **Nuvio Libtorrent P2P Motor Entegrasyonu (libtorrent + Boost.Asio):** Nuvio'nun yüksek performanslı C++ libtorrent ve Boost.Asio çekirdeği (`lib-nuvio-engine-android-0.1.2.aar`) Kitsugi'ye entegre edildi. Artık Debrid üyeliği (RealDebrid, AllDebrid vb.) olmadan da Torrentio ve diğer torrent tabanlı Stremio eklentileri doğrudan cihaz üzerinden eşler arası (P2P) ve yüksek hızla oynatılabilir.
- **Gelişmiş Seeding & Yükleme (Upload) Kontrolleri:** Orijinal Nuvio uygulamasında bulunmayan, arka planda sınırsız çalışan gönderme (seeding) davranışı tamamen kullanıcı denetimine açıldı. Stremio eklentileri ayarlarından seed modu tek tıkla kapatılabilir ya da internet kotasını korumak için 256 KB/s, 512 KB/s, 1 MB/s, 2 MB/s hız sınırları atanabilir.
- **P2P Gizlilik & Swarm Güvenlik Bilgilendirmesi:** P2P motoru ilk kez aktif edildiğinde veya Debrid olmadan bir torrent bağlantısına tıklandığında açılan tek seferlik güvenlik diyaloğu eklendi. IP adresinin torrent swarm havuzunda görünebileceği, telif hakları sorumluluğu ve VPN tavsiyeleri net biçimde sunuldu.
- **Tam Ekran Oynatıcı Canlı Torrent Paneli (TorrentOverlay):** Video oynatıcıya bağlanan canlı gösterge paneli sayesinde anlık indirme hızı, yükleme hızı, bağlı eşler (peers), seed sayısı ve önbellek tampon durumu gerçek zamanlı takip edilebilir. Oynatıcı kapatıldığında veya bölüm değiştiğinde P2P motoru gereksiz arka plan trafiğini önlemek için otomatik sonlandırılır.
- **Simkl Toplu Eşitleme (Batch Sync) & 429 Hız Sınırı Koruması:** Simkl API'sinin saniyede 1 istek kuralına ve `title`/`year` zorunluluklarına tam uyum sağlandı. 35'lik akıllı paketler ve 1.1 saniye gecikme ile yüzlerce animenin tek seferde sorunsuz senkronize edilmesi sağlandı; senkronizasyon sonrası Room DB'ye gerçek sunucu ID'leri işlenerek öğelerin sayfa yenilenince kaybolması engellendi.
- **Simkl Medya Detay Sayfası & 404 Koruması:** Simkl kütüphanesindeki öğelerin üzerine tıklandığında oluşan "Veri Yok" ve 404 hataları giderildi; kimlik çözümleyici Simkl ID'lerini doğru yöne kanalize ederek Jikan ve Simkl arama fallback mekanizmalarıyla güçlendirildi.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Nuvio Libtorrent P2P Streaming Engine, Full Seeding & Upload Controls, Simkl Batch Sync & Media Detail Fixes

- **Nuvio Libtorrent P2P Engine Integration (libtorrent + Boost.Asio):** Embedded Nuvio's high-performance C++ libtorrent and Boost.Asio core (`lib-nuvio-engine-android-0.1.2.aar`) directly into Kitsugi. Users can now stream torrent-based Stremio add-ons (such as Torrentio) entirely for free via peer-to-peer swarms without requiring a Debrid subscription.
- **Full Seeding & Upload Bandwidth Controls:** Addressed a critical privacy and bandwidth shortcoming in the original Nuvio app (which seeded indefinitely with no UI controls). Users can now toggle seeding on/off or limit upload bandwidth to 256 KB/s, 512 KB/s, 1 MB/s, or 2 MB/s directly from the Stremio settings tab.
- **P2P Privacy & Swarm Consent Dialog:** Implemented a one-time transparent consent dialog detailing public swarm IP visibility, copyright compliance responsibilities, and VPN usage recommendations before initiating any P2P torrent stream.
- **Real-Time Video Player Torrent Overlay:** Connected fullscreen player metrics to live engine telemetry, displaying instant download speeds, upload speeds, seeders, peers, and buffering progress, with automatic stream teardown on player exit.
- **Simkl Batch Synchronization & 429 Rate-Limit Prevention:** Adhered strictly to Simkl's 1 request/second policy and mandatory title/year metadata requirements. Synchronizes large libraries in 35-item batches with 1.1s throttling, followed by server-verified Room DB persistence to eliminate phantom entries and list reverts.
- **Simkl Media Detail Navigation & 404 Fallback:** Resolved missing detail screens and 404 errors when opening Simkl entries by prioritizing Simkl external IDs and adding Jikan & Simkl title search fallback strategies.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 İzole Süreç Çökme Ekranı (:crash), Canlı Sistem Teşhisi, Çapraz Senkronizasyon & Kararlılık Güncellemeleri

- **İzole Süreç Tabanlı Çökme & Teşhis Ekranı (Isolated :crash Process):** AndroidManifest'e `:crash` ayrılmış süreci entegre edildi. Ana uygulama süreci beklenmedik bir kritik hatayla veya bellek yetersizliğiyle karşılaşsa dahi, çökme ekranı (`KitsugiCrashActivity`) tamamen izole ve temiz bir süreçte anında açılır. Uygulamanın aniden sessizce kapanması ("pat diye kapanma") önlendi.
- **Hakkında Ekranından Doğrudan Teşhis & Rapor Erişimi:** Ayarlar altındaki Hakkında (About) ekranına yeni "Çökme & Hata Teşhis Raporu" butonu eklendi. Kullanıcılar ve test ekibi istedikleri an geçmiş çökme kayıtlarını, logcat çıktılarını ve cihaz donanım teşhisini inceleyebilir veya tek tıkla panoya kopyalayabilir.
- **Çoklu Platform Çapraz Senkronizasyon (Cross-Sync) Güçlendirmesi:** AniList, MyAnimeList, Simkl, Kitsu ve Shikimori platformları arasındaki kütüphane ve izleme geçmişi senkronizasyonu geliştirildi; diyalog akışı ve durum göstergeleri optimize edildi.
- **Kitsu Bildirim & Detay Entegrasyonu:** Kitsu bildirimlerine dokunulduğunda anime ve manga detay sayfalarının kesintisiz, doğru kimlik çözümlemesiyle ve önbellekten anında kapak aktarımıyla açılması garantilendi.
- **Shikimori 1-Tık Giriş ve Güvenlik Sertleştirmesi:** Shikimori OAuth2 bağlantısında yaşanan 403 ve token çözümleme problemleri kalıcı olarak giderildi.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Isolated :crash Process Architecture, Live System Diagnostics, Cross-Sync & Core Stability

- **Isolated Process Crash & Diagnostics Screen (:crash Process):** Dedicated isolated process architecture (`:crash`) wired into AndroidManifest. Even if the primary application process experiences an unhandled fatal error or OutOfMemory condition, `KitsugiCrashActivity` launches reliably within a clean sandbox without hangs or freeze loops, preventing abrupt app disappearance.
- **Direct Diagnostics & Crash Hub Access:** Added a dedicated "Crash & Diagnostics Report" button to the About screen. Users and testers can review past crash logs, live logcat output, and device hardware diagnostics on demand, or copy full reports to clipboard in one tap.
- **Multi-Platform Cross-Sync Enhancements:** Streamlined library cross-synchronization across AniList, MyAnimeList, Simkl, Kitsu, and Shikimori, complete with dialog feedback and state reconciliation.
- **Kitsu Notification & Media Detail Navigation:** Guaranteed smooth detail page transitions when launching from Kitsu push notifications, handling both raw and offset entity identifiers with instant poster caching.
- **Shikimori 1-Tap OAuth & Authorization Polish:** Strengthened Shikimori authentication flow against 403 Forbidden edge cases and token renewal hiccups.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 Shikimori 1-Tık Otomatik Giriş Çözümü, Kitsu Bildirim Detay Sayfası Düzeltmesi & Kararlılık Güncellemeleri

- **Shikimori 1-Tık Otomatik Giriş ve Deep Link Desteği:** Shikimori OAuth girişinde yaşanan 403 erişim engelleri ve yetkisiz istemci (invalid_client) sorunları kalıcı olarak çözüldü. Resmi `aniyomi://shikimori-auth` deep link yönlendirmesi entegre edildi. Kullanıcı tarayıcıda tek dokunuşla izin verdiğinde uygulama otomatik açılır ve oturumu başlatır.
- **Alternatif Manuel Giriş ve Otomatik Sıfırlama:** Tarayıcıda doğrulama kodunu doğrudan görüntüleyen `urn:ietf:wg:oauth:2.0:oob` protokolü korundu. Gelişmiş ayarlar menüsüne "Varsayılan Anahtarları Geri Yükle" butonu eklenerek cihaz hafızasında kalmış eski/hatalı anahtarların tek tıkla temizlenmesi sağlandı.
- **Kitsu Bildirimlerinden Medya Detayına Geçiş Sorunu Giderildi:** Bildirimler ekranındaki Kitsu bildirimlerine dokunulduğunda oluşan boş ekran veya çökme problemi düzeltildi. `KitsuExploreClient` ve `KitsugiDetailClient` içerisindeki kimlik çözümleme motoru güncellenerek hem ham Kitsu ID'leri hem de ofsetli (`+300,000,000`) ID'ler eksiksiz tanınır hale getirildi.
- **Görsel Bildirim Geçişleri:** Bildirimlerden detay sayfasına giderken anime/manga başlığı ve kapak görseli önbellekten anında aktarılarak sayfa açılışında görsel süreklilik ve akıcı bir deneyim sunuldu.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Shikimori 1-Tap OAuth & Deep Link Fix, Kitsu Notification Detail Resolution & Stability Improvements

- **Shikimori 1-Tap OAuth & Deep Link Integration:** Fixed 403 Forbidden and invalid_client issues during Shikimori authentication. Configured official `aniyomi://shikimori-auth` deep link callback scheme; tapping "Allow" in browser immediately returns to Kitsugi and authenticates seamlessly.
- **Alternative Out-of-Band Auth & Credential Reset:** Preserved manual code generation via `urn:ietf:wg:oauth:2.0:oob`. Added a "Reset to Default Keys" action in advanced settings to purge any corrupted client keys from app storage.
- **Kitsu Notification Media Detail Page Fix:** Resolved an issue where tapping on Kitsu notifications resulted in empty detail screens or failed navigation. Harmonized ID offset mapping in `KitsuExploreClient` and `KitsugiDetailClient` to reliably resolve both raw numeric IDs and offset stable IDs (`+300,000,000`).
- **Seamless Notification Visual Transition:** Titles and cover posters from incoming notifications are now forwarded directly to detail screens, ensuring instantaneous visual feedback without flicker.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 Gelişmiş Arama Filtre Paneli, İzole Arama Motorları, Resmi MAL API Entegrasyonu, Shikimori 1-Tık Giriş & Modern Çökme Raporlama

- **Yeni Nesil Arama Kaynak ve Filtre Seçici Paneli (Source Engine Filter Sheet):** Arama sayfasında motorların durumunu, aktifliklerini ve sonuç sayılarını gösteren, kategorilere (Anime, Dizi, Film, Manga, Roman vb.) göre filtrelenebilen yepyeni bir modal alt panel (`SourceEngineFilterSheet`) eklendi.
- **İzole Edilmiş ve Bağımsız Arama Sağlayıcıları:** Çoklu arama motorları birbirinden tamamen izole edildi; bir sağlayıcıda gecikme veya hata olduğunda diğer motorların (AniList, Kitsu, Shikimori, TMDB, Simkl vb.) arama sonuçları kesintisiz olarak anında ekrana gelir.
- **Resmi MyAnimeList (MAL) API Entegrasyonu:** Eski veya kararsız Jikan uç noktaları yerine MyAnimeList'in resmi OAuth2 API altyapısına geçildi. Arama hızı ve kütüphane güvenilirliği en üst seviyeye çıkarıldı.
- **Shikimori 1-Tık Giriş & 403 Forbidden Çözümü:** Shikimori OAuth girişinde yaşanan 403 erişim engeli ve token sorunları giderildi. Tek tıkla tarayıcı yetkilendirmesiyle sorunsuz profil bağlantısı sağlandı.
- **Kitsu & Shikimori Kapsamlı Profil ve Liste Yönetimi:** Profil ekranında Kitsu ve Shikimori hesapları için kütüphane durumları (İzleniyor/Okunuyor, Tamamlandı, Beklemede, Bırakıldı, Planlandı), puan dağılımı, favoriler ve biyografi eksiksiz görüntülenebilir ve güncellenebilir hale getirildi.
- **Gelişmiş Teşhis ve Çökme Raporlama Ekranı (KitsugiCrashActivity & Logger):** Uygulama beklenmeyen bir hatayla karşılaştığında kapanmak yerine modern bir arayüzle hatanın detayını, cihaz donanım bilgilerini ve bellek durumunu gösterir; logları tek tıkla kopyalama veya uygulamayı güvenle yeniden başlatma imkanı sunar.
- **Ayarlar Ekranı Modüler Hesap Sayfaları:** Ayarlar menüsündeki hesap yönetimi ayrıştırılarak AniList, MAL, Simkl, Kitsu ve Shikimori için özel alt sayfalar (`AccountSettingsSubPages`) oluşturuldu.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Advanced Search Filter Sheet, Isolated Search Engines, Official MAL API, Shikimori 1-Tap OAuth & Modern Crash Activity

- **Next-Gen Source Engine Filter Bottom Sheet:** Introduced a comprehensive modal bottom sheet (`SourceEngineFilterSheet`) on the Search screen with per-source toggles, status indicators, and categories (Anime, Movies, TV Series, Manga, Light Novels).
- **Isolated Multi-Search Provider Pipeline:** Search engines are now completely isolated; network timeouts or throttles on one provider no longer affect parallel search results from others (AniList, Kitsu, Shikimori, TMDB, Simkl).
- **Official MyAnimeList (MAL) API Migration:** Fully migrated search and library operations to official MyAnimeList OAuth2 endpoints, eliminating third-party rate limits and improving result speed.
- **Shikimori 1-Tap OAuth & 403 Forbidden Fix:** Fixed 403 Forbidden errors during Shikimori authorization, enabling an effortless 1-tap browser auth flow.
- **Kitsu & Shikimori Full Profile & Library Management:** Enriched profile screens with full status tracking (Watching/Reading, Completed, On Hold, Dropped, Plan to Watch/Read), score charts, favorites, and profile updates.
- **Advanced Diagnostics & Modern Crash Screen (KitsugiCrashActivity & Logger):** Replaced default unhandled exception crashes with a modern diagnostics screen showing breadcrumbs, system specifications, memory status, stacktrace copying, and 1-tap safe app restart.
- **Modular Account Settings Subpages:** Restructured account management into modular subpages (`AccountSettingsSubPages`) for AniList, MAL, Simkl, Kitsu, and Shikimori with clear connection status and sync actions.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 Zenginleştirilmiş Sistem Bildirimleri, Kitsu & Shikimori Profilleri, Profil Kaynak Seçici Paneli ve Orijinal Logolar

- **Zengin ve Görsel Sistem Bildirimleri (Drawer & Status Bar):** Android bildirim çekmecesi bildirimleri artık çok daha ayrıntılı ve yüksek çözünürlüklü kapak/avatar görselleriyle (`LargeIcon` & `BigPictureStyle`) donatıldı. Bildirimler artık hangi kaynaktan geldiğini açıkça belirtir (`[AniList]`, `[MyAnimeList]`, `[Simkl]`). Sistem çekmecesi bildirimleri ile uygulama içi bildirim ekranı metin ve biçimlendirme açısından birebir senkronize edildi.
- **Profil Sayfası Açılır Kaynak Seçim Paneli (Profile Source Picker Sheet):** Arama ve Keşfet sayfalarında olduğu gibi profil sayfasına da açılır seçim paneli (`ProfileSourcePickerSheet`) ve orijinal logolu hap butonu eklendi. Tek dokunuşla tüm bağlı/bağlantısız profiller arasında geçiş yapılabilir.
- **Kitsu ve Shikimori Profil Desteği Tamamlandı:** Profil sayfasına eksik olan Kitsu ve Shikimori sekmeleri eklendi. Kullanıcı biyografisi, waifu/husbando, takipçi verileri, anime/manga durum istatistikleri ve kütüphane kayıtları eksiksiz görüntülenir.
- **Orijinal Platform Logoları (Sıfır Emoji Kuralı):** Tüm sayfalardaki kaynak butonları, seçim panelleri ve sekmelerdeki emojiler veya boş kutular kaldırılarak yerini yüksek kaliteli orijinal platform logoları (`KitsugiPlatformLogo`) aldı.
- **Yeni GitHub Hesabı & Depoları Entegrasyonu:** Kod tabanı yeni `f26901964-eng/Kitsugi-Beta` ve `f26901964-eng/Kitsugi-Plugins` depolarına uyarlandı. Eklenti deposundan tüm +18 içerikler temizlenerek güvenli havuz devreye alındı.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Rich System Notifications, Kitsu & Shikimori Profiles, Source Picker Sheet & Original Logos

- **Rich Visual System Drawer Notifications:** Android status bar and drawer notifications now include high-resolution poster artwork and avatars via `LargeIcon` and `BigPictureStyle`. Every notification clearly highlights its origin source (`[AniList]`, `[MyAnimeList]`, `[Simkl]`), completely harmonized with the in-app notification center.
- **Profile Source Picker Bottom Sheet:** Added a modal selection sheet and original logo pill button to the Profile screen, matching the Search and Explore user experience.
- **Kitsu & Shikimori Full Profile Integration:** The Profile screen now fully supports Kitsu and Shikimori tabs, displaying bio, social metrics, anime/manga status breakdown charts, score distributions, and library records.
- **Original Platform Logos (Strictly No Emojis):** Replaced all emoji badges and generic colored boxes across buttons, sheets, and tabs with authentic, high-res platform vector logos (`KitsugiPlatformLogo`).
- **New GitHub Ecosystem Integration:** Fully wired the application and plugin repositories to the new `f26901964-eng` GitHub account, with an adult-free safe plugins pool and updated in-app update mechanisms.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 Kitsu Düzeltmeleri, Grid/Liste Hatırlama, Yapışkan Bar Filtresi, TMDB İngilizce Fallback, Simkl Dizi/Film Keşfeti ve Çökme Korumaları

- **Grid / Liste Görünümü Kalıcı Olarak Hatırlanıyor:** Popüler, Trend vb. tam ekran medya sayfalarındaki liste veya grid görünüm seçimi artık kaydedilir; sayfadan çıkıp tekrar girildiğinde kullanıcının son seçtiği görünüm modu korunur.
- **Yapışkan Üst Çubukta Filtre Butonu Düzeltildi:** Sayfa aşağı kaydırıldığında beliren kompakt üst çubukta filtre butonunun kaybolması sorunu çözüldü; filtreleme ve sıralama butonu artık her kategoride ve kaydırma durumunda görünür durumdadır.
- **TMDB Keşfet'te Türkçe Başlığı Olmayan İçerikler İçin Otomatik İngilizce Desteği:** TMDB'den çekilen içeriklerde Türkçe başlık bulunmadığında Japonca/Kanji başlık yerine otomatik olarak İngilizce başlık (örn. "Souryo to Majiwaru Shikiyoku no Yoru ni...", "Puella Magi Madoka Magica...") gösterilir.
- **Simkl Keşfet'e Dizi ve Film Desteği Eklendi:** Simkl platformu artık sadece animelerle kısıtlı kalmayıp TMDB gibi Trend Diziler, Trend Filmler, Popüler Diziler, Popüler Filmler ve en yüksek puanlı içerikleri eksiksiz gösterir.
- **Kitsu Anime Ekleme / Düzenleme Sayfası Düzeltildi:** Kitsu üzerinden açılan animelerde "Listeye Ekle / Düzenle" alt sayfasının MyAnimeList olarak açılması ve MAL alanlarının görünmesi sorunu çözüldü. Artık Kitsu renkleri (turuncu), Kitsu kimlik etiketi ve Kitsu'ya özgü liste durumu seçenekleri açılır.
- **Kitsu Karakterler Sekmesi Takılma / Yüklenmeme Sorunu Giderildi:** Anime detay sayfasında "Resimler" sekmesi eklendikten sonra meydana gelen indeks kayması giderildi; "Karakterler" sekmesine tıklandığında artık iskelet ekranda takılı kalmadan tüm karakterler eksiksiz yüklenir.
- **Kitsu Kişi Bilgisi ve Galeri Kaynak Çelişkisi Düzeltildi:** Kitsu'dan açılan yönetmen/seslendirmen/ekip detayında "ANILIST" yerine doğru platform ("Kitsu") etiketi gösterilir; profil fotoğrafı tam ekran açıldığında da "Jikan" ve "🎭 Karakter" yerine doğru kaynak ve "👤 Kişi" kategorisi kullanılır. Kitsu ID ile kişi adı önce doğrudan Kitsu API'den çözümlenerek doğru eşleştirme sağlanır.
- **Rastgele Uygulama Çökmeleri Engellendi (Crash Shield & Looper Koruması):** Sayfa geçişlerinde, video izlerken veya eklentilerden veri gelmesini beklerken arka plan iş parçacıklarında, ağ zaman aşımlarında ya da harici eklenti kodlarında oluşan beklenmedik istisnaların uygulamayı aniden kapatması engellendi. Ana iş parçacığı döngü koruyucusu (Cockroach Looper Recovery) ve `SupervisorJob` takviyesiyle donatıldı.
- **Eklenti Deposu (Kitsugi Plugins) Sorunsuz Yükleme Garantisi:** "Kitsugi Eklentileri (Önerilen)" deposu eklenirken yaşanan ISS engelleri ve ağ zaman aşımları tamamen çözüldü. Hem doğrudan Codeberg bağlantısı hem REST API hem de dahili çevrimdışı katalog yedeği devreye alındı.
- **Shikimori Giriş Akışı Düzeltildi:** "The requested redirect uri is malformed" hatası giderilerek RFC 6749 uyumlu URL-encode OAuth2 giriş akışı sağlandı.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Grid Persistence, Sticky Filter Button, TMDB English Fallback, Simkl TV/Movies & Crash Shield

- **Grid / List Mode Persistent Memory:** The layout toggle preference on full-screen media grid pages is now saved and remembered across visits.
- **Sticky Top Bar Filter Button Fix:** Fixed the filter and sorting button disappearing from the compact sticky header upon scrolling down; it is now always visible and positioned properly next to the layout toggle.
- **TMDB Explore English Title Fallback:** When Turkish titles are not localized on TMDB, titles automatically fall back to their English/Romaji counterparts instead of Japanese Kanji/Kana.
- **Simkl Explore TV & Movie Expansion:** Simkl explore is no longer limited to anime; it now features full TV show and movie sections (trending, popular, top-rated) matching the TMDB experience.
- **Kitsu Media Editor Sheet Fix:** Fixed Kitsu anime entries opening the edit sheet with "MyAnimeList" branding and MAL status fields. Proper Kitsu branding, theme color, and sync properties are now respected.
- **Kitsu Characters Tab Loading Fix:** Resolved tab index offset bug causing the Characters tab to freeze with skeleton loaders indefinitely; characters now load immediately.
- **Person / Staff Detail & Gallery Attribution Fix:** Resolved source attribution conflicts when opening staff from Kitsu (showing "ANILIST" badge or "Jikan / Character" in fullscreen gallery). Now correctly attributes Kitsu and "Person" category, with pre-resolution against Kitsu's `/people` API.
- **Random Crash Shield & Main Looper Protection:** Prevented random crashes during navigation, video playback, and extension scraping. Installed a resilient main Looper recovery loop and `SupervisorJob` scopes across extension loaders and prefetch coroutines to isolate non-fatal exceptions.
- **Plugin Repository Bulletproof Loading:** Resolved repository manifest loading failures caused by ISP blocks with Codeberg API fallback and bundled 174-plugin offline catalog.
- **Shikimori OAuth Redirect URI Fix:** Fixed "The requested redirect uri is malformed" error with proper RFC 6749 URL encoding and streamlined OAuth flow.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 Keşfet Arayüzü, "Tümü" Butonu, Kitsu & Shikimori Girişleri, Bildirim Önizleme ve Çapraz Eşitleme

- **Keşfet Sayfasında Çift Bar Fazlalığı Giderildi:** Keşfet sayfasında hem açılır seçim menüsü hem de alttaki yatay çip çubuğunun oluşturduğu karmaşa giderildi; gereksiz yatay çip barı kaldırılarak tekil, şık ve fonksiyonel kaynak seçici korundu.
- **Listem Sayfası "Tümü" Butonu Alt Bar ile Uyumlu Hale Getirildi:** "Tümü" kategori seçim butonu artık alt gezinti çubuğunun (Bottom Navigation Bar) üzerinde taşma veya örtüşme yapmaz. Diğer sayfalardaki FAB butonlarıyla birebir aynı biçimde, alt çubuğun tam üzerinde hizalanır ve kaydırma hareketleriyle pürüzsüzce animasyonlanır.
- **Kitsu Giriş Hatası ("Client authentication failed") Çözüldü:** Kitsu API OAuth2 token uç noktasına yönelik istemci anahtarları ve OAuth2 standart form-urlencoded yapısı güncellendi. Kullanıcılar artık e-posta/kullanıcı adı ve şifreleriyle doğrudan ve hatasız giriş yapabilir.
- **Shikimori Giriş Akışı Sadeleştirildi:** Kullanıcıdan karmaşık Client ID, Client Secret oluşturması isteme zorunluluğu kaldırıldı. Dahili kimlik bilgileriyle tek dokunuşla tarayıcıda Shikimori onay sayfası açılır, ekrandaki kod yapıştırılarak anında bağlantı kurulur.
- **AniList Bildirimlerine Tıklayınca Doğrudan Aktivite/Beğeni Detayı Açma:** Beğeni veya yorum bildirimlerine tıklandığında yalnızca körü körüne kullanıcı profiline gitmek yerine, tam olarak hangi paylaşımın/içeriğin beğenildiği veya yorumlandığını gösteren detay sayfası ve ilgili anime/dizi bağlantıları açılır.
- **Tüm Hesapları Birbiriyle Eşitle (5 Platform Çapraz Senkronizasyon) Düzeltildi:** AniList, MyAnimeList, Simkl, Kitsu ve Shikimori hesapları arasında anime, dizi ve film içerikleri eksiksiz olarak birbirine eşitlenir; her bağlı platformun kütüphanesindeki eksik içerikler tespit edilip hem yerel listelere hem de ilgili servislere sorunsuz aktarılır.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Explore UI, "All" FAB, Kitsu & Shikimori Auth, Activity Notifications & Cross-Sync Matrix

- **Explore Top Bar Deduplication:** Removed the redundant horizontal chip row below the platform selector pill on the Explore screen, keeping a clean and unified interface.
- **MyList "All" FAB Bottom Bar Alignment:** The category button now sits properly above the Material3 navigation bar without overlapping, animating smoothly on scroll just like floating action buttons on other screens.
- **Kitsu Authentication Fix:** Resolved the "Client authentication failed" error by adopting official Kitsu public OAuth2 credentials and urlencoded form specifications.
- **Simplified Shikimori OAuth Flow:** Users no longer need to create developer applications or enter client credentials. A 1-tap browser authorization with built-in keys allows pasting the code directly.
- **Notification Activity Preview:** Clicking activity or like notifications now opens the exact activity/post modal showing what was liked/commented with direct media links, instead of merely navigating to the user profile.
- **Full Cross-Platform Synchronization Matrix:** The "Sync All Accounts" feature now equalizes anime, TV shows, and movies across all connected accounts (AniList, MAL, Simkl, Kitsu, Shikimori), properly updating local tabs and cross-syncing missing library items.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 Eklenti Deposu ve Eklenti Yükleme Sorunları Tamamen Giderildi

- **Codeberg Depo ve Eklenti Yüklenememe Sorunu Çözüldü:** Repo URL doğrulayıcı (`RepoVerifier`), `codeberg.org` alan adını artık varsayılan olarak güvenilir listeye aldı. Ek olarak `.git`, depo kök dizini veya eski GitHub bağlantıları otomatik olarak optimize edilmiş Codeberg manifestine yönlendirildi.
- **"Eklentiler Yüklenemedi" Hatası & Otomatik Yenileme:** Ağ gecikmesi veya ilk açılışta başarısız olan depo isteklerinde eklenti listesinin takılı kalması engellendi; otomatik yeniden deneme ve akıllı önbellek yenileme mekanizması devreye alındı.
- **Ağ Zaman Aşımı & Proxy Desteği:** Yavaş bağlantılarda ve ISS kısıtlamalarında eklenti ve repo indirmelerinin zaman aşımına uğramaması için okuma/bağlantı süreleri 30 saniyeye çıkarıldı, jsDelivr CDN yedeği güçlendirildi.
- **Eski ve Bozuk Depoların Temizliği:** Veritabanındaki eski/çift veya hatalı depo adresleri açılışta otomatik olarak temizlenerek resmi `Kitsugi Plugins (Önerilen)` deposu altında birleştirildi.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Extension Repository & Plugin Loading Fixes

- **Codeberg Domain Trust & Repo Normalization:** `codeberg.org` is now unconditionally trusted in `RepoVerifier`. All repository formats (root URLs, `.git`, legacy GitHub links) now seamlessly normalize to canonical Codeberg endpoints.
- **Automatic Retry for Extension Listings:** Fixed an issue where temporary network failures left repo cards stuck on "Eklentiler yüklenemedi". Repos now auto-retry upon opening or refreshing.
- **Extended Timeouts & CDN Fallback:** Connect and read timeouts increased to 30s to prevent dropouts on throttled networks.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 Eklenti Kurulum Hatası Giderildi & Codeberg Eklenti Havuzu Canlıya Alındı

- **Eklenti Kurulumu ve İndirme Hatası Tamamen Çözüldü:** Yeni Codeberg `KitsugiPlugins` deposundaki tüm 174 eklenti (DiziPal, RecTV, FilmMakinesi, InatBox, TurkAnime vb.) artık sorunsuz, doğrudan ve tek tıkla kurulmaktadır. Dosya bütünlüğü ZIP doğrulamasıyla garanti altına alınmıştır.
- **Doğrudan Codeberg URL Koruması:** Eklenti indirme ve repo yenileme sırasında Codeberg URL'lerinin bozulması engellenmiş, eski GitHub bağlantıları otomatik olarak yeni depoya yönlendirilmiştir.
- **Sürüm Güncellemesi:** Önceki derlemelerdeki önbellek çakışmalarını gidermek amacıyla v2.4.194 olarak paketlenmiştir.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Plugin Installation Error Fixed & Codeberg Plugin Pool Live

- **Extension Download & Installation Completely Fixed:** All 174 plugins (DiziPal, RecTV, FilmMakinesi, InatBox, TurkAnime, etc.) in the new Codeberg `KitsugiPlugins` repository now install seamlessly with 1-click installation.
- **Direct Codeberg URL Preservation:** Plugin download URLs are safely preserved and legacy GitHub endpoints are automatically routed to the new repository.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 Codeberg Entegrasyonu & Eklenti Havuzu Güvenliği (+18 Temizlendi)

- **Codeberg Otomatik Güncelleme Entegrasyonu:** Uygulama içi otomatik güncelleyici (`KitsugiUpdateRepository`) artık birincil güncelleme kaynağı olarak Codeberg Releases API'sini (`BlackDamage/Kitsugi-Beta`) kullanır. Olası bir durumda GitHub API'sine yedek (fallback) olarak bağlanır.
- **Güvenli ve Temiz Eklenti Deposu (+18 / NSFW Kaldırıldı):** Eklenti deposu GitHub'dan Codeberg'e (`BlackDamage/Kitsugi-Plugins`) taşındı. Topluluk ve mağaza kurallarına tam uyum için tüm yetişkin (+18/NSFW), ifşa ve deepfake eklentileri havuzdan kalıcı olarak temizlendi; 170+ popüler dizi, film, anime ve belgesel eklentisi (DiziPal, RecTV, FilmMakinesi, InatBox, TurkAnime vb.) optimize edilerek korundu.
- **Eski Depo URL'lerini Otomatik Yönlendirme:** Uygulama açılışında ve eklenti taramasında eski GitHub bağlantıları (`gameras1010-afk`, `KitsugiBeta-dev`, `Kekik-cloudstream`) otomatik olarak yeni Codeberg deposuna yönlendirilir.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Codeberg Migration & Clean Plugin Repository (+18 Excluded)

- **Codeberg In-App Auto-Updates:** Primary update checking and release asset downloading has been migrated to the Codeberg Releases API (`BlackDamage/Kitsugi-Beta`) with GitHub fallback.
- **Clean & Safe Plugin Repository (+18 Excluded):** Migrated cloudstream plugins to Codeberg (`BlackDamage/Kitsugi-Plugins`), completely removing 116+ NSFW / adult / deepfake plugins to guarantee longevity and platform TOS compliance. All 170+ mainstream movie, anime, TV, and documentary sources remain fully available and updated.
- **Automatic Legacy URL Redirection:** Legacy GitHub URLs for plugins are seamlessly redirected to the new Codeberg repository.

---

## 🇹🇷 Türkçe (v2.4.194)

### 📚 Türkçe Manga & Webtoon Altyapısı Kökten Yenilendi (Keiyoushi V2, 77 TR Kaynağı, MangaDex 3.450 TR Başlık, Hotlink Çözümü)

- **Keiyoushi V2 Repo Şeması & Decoy Koruması:** Keiyoushi'nin modern `index.json` V2 şeması (`extensionList.extensions[]`) tam olarak desteklendi. 2 sahte kayıtlı decoy `index.min.json` otomatik bypass edilerek doğrudan `index.json` üzerinden tüm eklentiler listelenir hale getirildi. 404 veren ölü katalog URL'leri temizlendi.
- **2026 Keiyoushi İmza Anahtarı:** Yeni SHA-256 parmak izi (`9add655a...`) `TrustManager`'a eklendi ve repodan gelen eklentiler için dinamik güven zinciri entegre edildi.
- **Modern Eklenti API'sine Geçiş (extensionLib 1.6+):** 77 Türkçe eklentinin 71'inde detay ve bölüm listelerinin boş gelmesine sebep olan eski deprecated API çağrıları kaldırıldı; modern `getMangaUpdate` suspend API'sine geçildi.
- **MangaDex & Global Katalog Aramaya Dahil Edildi (3.450 TR Başlık):** `MangaSearchCoordinator` filtresindeki mantıksal engel kaldırılarak MangaDex gibi çok dilli global kaynaklar arama adaylarına eklendi. TR kaynaklarda bulunamayan mangalar MangaDex'teki 3.450 Türkçe çeviri üzerinden anında bulunabilir hale geldi.
- **Sayfa Görsellerinde Hotlink Koruması Çözüldü:** Manga-TR, TRManga, WebtoonHattı gibi hotlink korumalı CDN'lerin 403 Forbidden dönmesi engellendi. `MangaPageLoaderV2`, görselleri kaynağın kendi `Referer`, özel User-Agent ve Cloudflare interceptor'ları ile indirip doğrudan disk önbelleğine yazar ve Telephoto okuyucuya sunar.
- **Arama Eşzamanlılık Kapısı (Semaphore 6) & Akıllı Zaman Aşımı:** Aynı anda 70+ kaynağa istek atıp 429 veya bağlantı kopması yaşanmasını engellemek için 6'lı eşzamanlılık kapısı eklendi. Yeni nesil Keiyoushi motoru tanınarak zaman aşımı 25 saniyeye optimize edildi.
- **İki Geçişli Arama & Doujinshi Filtresi:** Arama sonuçlarının ilk turda gereksiz doujinshi veya spin-off'larla dolması engellendi. Önce sıkı skorlama yapılır, sonuç yoksa gevşek skorlama devreye girerek doğru manga ilk sıraya yerleştirilir.
- **Kaynak Sağlık Sınıflandırması İyileştirildi:** Arama teriminin bulunamaması (404) veya geçici DNS hataları ("unable to resolve host") artık kaynakları 2 saatlik "Broken" cezasına sokmaz; "Degraded" seviyesinde tutularak kaynakların kaybolması engellendi.
- **Eksik Host Shim Sınıfları Eklendi:** Eklentilerin ihtiyaç duyduğu `AppInfo`, `UnmeteredSource`, `RateLimitInterceptor` gibi sınıflar shim katmanına eklenerek sınıf yükleme çökmeleri giderildi.

---

## 🇬🇧 English (v2.4.194)

### 📚 Turkish Manga & Webtoon Engine Complete Overhaul (Keiyoushi V2, 77 TR Sources, MangaDex 3,450 TR Titles, Hotlink Bypass)

- **Keiyoushi V2 Schema & Decoy Bypass:** Added full support for the modern Keiyoushi V2 `index.json` schema (`extensionList.extensions[]`). Automatically bypasses the decoy 2-entry `index.min.json` and loads extensions directly from `index.json`. Removed dead 404 catalog URLs.
- **2026 Signing Key Trust:** Added the latest Keiyoushi SHA-256 fingerprint (`9add655a...`) to `TrustManager` and established dynamic signature trust for repository extensions.
- **Modern Extension API (extensionLib 1.6+):** Migrated from deprecated `fetchMangaDetails`/`fetchChapterList` stub methods to the modern `getMangaUpdate` suspend API, restoring detail and chapter lists across 71+ Turkish extensions.
- **MangaDex & Global Catalogs in Search (3,450 TR Titles):** Fixed a boolean logic barrier in `MangaSearchCoordinator`, enabling MangaDex and trusted global catalogs to participate in manga searches alongside Turkish sources.
- **Hotlink Protection Bypass in Image Loader:** Resolved 403 Forbidden errors on hotlink-protected CDNs (Manga-TR, TRManga, WebtoonHattı). `MangaPageLoaderV2` now prioritizes the extension's native `source.getImage()` pipeline with correct `Referer`, User-Agent, and Cloudflare cookies directly into disk cache for the Telephoto reader.
- **Concurrency Gate (Semaphore 6) & Smart Timeouts:** Prevented network congestion and 429 rate limits by bounding concurrent searches to 6 parallel requests. Added modern `KEI_SOURCE` engine detection and increased timeout to 25s for stable scraping on mobile networks.
- **Two-Pass Search Scoring & Doujinshi Guard:** Searches now execute a strict first-pass match before relaxing filters, preventing doujinshi and loose spinoffs from polluting main search results.
- **Softened Health Classifier:** Network DNS errors ("unable to resolve host") and search 404s now mark sources as Degraded rather than Broken, preventing false 2-hour cooldown lockouts.
- **Missing Extension Host Shims:** Added missing `AppInfo`, `UnmeteredSource`, and `RateLimitInterceptor` host shims to prevent ClassNotFound exceptions when loading extensions.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🎭 TMDB Kurgusal Karakter Detayları, Seslendirmen Ayrımı, AL Arama ve Kesintisiz Keşfet (Kitsu Fallback)

- **TMDB Kurgusal Karakter Detay Sayfası Düzeltildi:** TMDB yapımlarındaki anime/kurgusal karakterlere (ör. Nobita Nobi, Doraemon, Rudeus Greyrat) tıklandığında seslendirmenin (ör. Megumi Oohara, Wasabi Mizuta) biyografi ve doğum günü yerine doğrudan karakterin kurgusal biyografisi, anime rolleri ve tüm seslendirmenlerinin listelendiği sayfa açılacak şekilde düzeltildi.
- **Karakter Görsel Karışıklığı Kökten Çözüldü:** Mushoku Tensei gibi aynı soyadını ("Greyrat") taşıyan karakterlerin (Rudeus, Former Self, Eris) ve rol varyantlarının yanlış token eşleşmesiyle aynı görseli alması engellendi. Öz isim doğrulaması (given-name guard) ve varyant filtreleri eklendi.
- **AniList (AL) Arama Sorunu Giderildi:** AniList GraphQL sorgusunda eksik olan `$isLicensed: Boolean` tanımı eklenerek sunucudan HTTP 400 Bad Request dönmesi ve aramalarda AL satırının kaybolması düzeltildi.
- **AniList Keşfet Sayfası & Kitsu Otomatik Fallback:** AniList keşfet sayfasındaki genel erişim iyileştirildi; sunucu hatası, hız sınırı veya boş liste durumlarında Kitsu keşfet motorunun otomatik devreye girerek listeleri eksiksiz doldurması sağlandı.

---

## 🇬🇧 English (v2.4.194)

### 🎭 TMDB Fictional Character Details, Voice Actor Separation, AL Search & Seamless Kitsu Fallback

- **Fictional Character Resolution in TMDB:** Tapping anime characters in TMDB entries now reliably opens the character's fictional profile (biography, appearances, and voice actors) via AniList/Jikan rather than displaying the real voice actor's personal biography and birthday.
- **Character Image Pollution Fixed:** Resolved an issue where characters with shared family surnames (e.g., Greyrat in Mushoku Tensei) or role variants ("Former Self") mistakenly received the same image. A robust given-name guard and variant modifier matching algorithm ensures exact individual character mapping.
- **AniList Search Restored:** Fixed an HTTP 400 Bad Request caused by a missing `$isLicensed: Boolean` definition in the AniList GraphQL search query. AniList results now reliably appear in both "All" and "Anime" search tabs.
- **AniList Explore & Seamless Kitsu Fallback:** Restored public AniList explore browsing without token requirements. Whenever AniList returns empty data or encounters issues, Kitsu automatically and seamlessly populates the explore carousels.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🎬 TMDB Film/Dizi ID Eşleme Düzeltmesi & Yorumlardaki Resim Büyüme Sorunu Çözüldü

- **TMDB Anime Filmleri Diziyle Karışma Sorunu Düzeltildi:** TMDB keşfet sayfalarında "Howl's Moving Castle" (Yürüyen Şato) gibi anime filmlerine tıklandığında film yerine aynı TMDB ID numarasına sahip "Roar" adlı TV dizisinin açılması sorunu giderildi. TMDB anime filmlerinin türü doğru şekilde `MediaType.Movie` olarak belirlendi, başlık doğrulaması eklendi ve önbellek film/dizi ayrımıyla güçlendirildi.
- **Yorumlar & Tartışmalardaki Resimlerin Yavaşça Büyümesi Düzeltildi:** Konu detayı ve yorumlarda paylaşılan görsellerin ve GIF'lerin sayfa açıldıkça veya render edildikçe yavaşça kendi kendine büyüyüp genişlemesi (Compose `animateContentSize` döngüsü) durduruldu. Görseller artık anında sabit orantıyla yüklenir, maksimum yükseklik sınırı (240dp) ile kart düzenini bozmaz ve tıklandığında tam ekran galeri açılır.
- **İndirme Ayarları Veri Yönetiminden Ayrıldı:** Veri & Yedekleme içindeki "İndirmeler" sekmesi oradan çıkarıldı; Ayarlar menüsünde doğrudan "İndirme Ayarları" bağımsız bir sayfa olarak yer aldı. Veri & Yedekleme ekranı sadeleştirildi.
- **İndirmeler Ekranı Sağa/Sola Kaydırılabilir Yapıldı:** İndirmeler sayfasındaki Videolar, Altyazılar ve Resimler sekmeleri arasında parmakla sağa ve sola kaydırarak (swipe gesture) geçiş desteği eklendi.
- **Arama Sayfası Yukarı Kaydırma Butonu & Alt Bar Senkronizasyonu:** Arama sayfasında aşağı kaydırırken alt bar ile yukarı kaydırma butonunun (FAB) üst üste binmesi sorunu giderildi. Buton alt barın durumuna göre dinamik olarak barın üzerinde konumlanır, butona tıklandığında alt bar anında geri gelir ve sayfa tepeye ulaştığında alt barın görünür kalması sağlandı.

---

## 🇬🇧 English (v2.4.194)

### 🎬 TMDB Movie/TV ID Mapping Fix, Comment Image Auto-Expansion Resolved & Downloads Navigation

- **TMDB Anime Movie/TV Confusion Resolved:** Fixed an issue where clicking anime movies on TMDB explore (such as *Howl's Moving Castle*) opened a TV show (*Roar*) with the same TMDB ID. Anime movies are now accurately typed as `MediaType.Movie`, verified by title matching, and differentiated in Room cache.
- **Forum & Comment Image Auto-Growing Fixed:** Fixed the issue where inline images and GIFs in discussion threads and comments slowly expanded on their own due to Compose `animateContentSize` layout passes. Inline images now display immediately with fixed bounds (max 240dp height), preserving clean comment layouts.
- **Dedicated Download Settings Subpage:** Removed the download settings tab from inside "Data & Backup". Created a clean, standalone "Download Settings" item in the Settings menu for quick access.
- **Horizontal Swipe for Downloads Screen:** The Downloads screen (Videos, Subtitles, Images) is now horizontally swipeable with smooth paging gestures.
- **Search Screen Scroll-to-Top & Bottom Bar Sync:** Fixed the overlap between the scroll-to-top floating button and the bottom navigation bar on the Search screen. The button dynamically floats above the bottom bar, and tapping it immediately restores the bottom bar while scrolling to the top.
