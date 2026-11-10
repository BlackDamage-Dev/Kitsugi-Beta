# Kitsugi Beta v2.4.392

[TR]
### 🚀 Yenilikler ve Öne Çıkan Geliştirmeler

Bu sürümde; **AniList Hız Sınırı, İçe Aktarım ve Senkronizasyon Mimarisi Kapsamlı Yenilemesi** yer alıyor! AniList ile iletişim kuran 280+ dosya taranarak 5 kritik kök neden çözüldü ve **AniListGovernor** merkezi hız yönetim mimarisi devreye alındı.

---

#### ⚡️ 1. AniList Merkezi Hız Yöneticisi (`AniListGovernor`)
- **Ortak Hız Bütçesi:** Keşfet, arama, detay, bildirimler, takvim, kütüphane içe aktarımı ve liste yazımları (mutasyonlar) artık tek bir hız slotu üzerinden yönetilir. Farklı ekranların birbirini 429'a düşürmesi engellendi.
- **Dinamik ve Uyarlanabilir Aralık:** AniList'in fiilî 30 istek/dakika hız sınırına göre `X-RateLimit-Limit` başlığından istek aralığı dinamik hesaplanır (60 sn / limit ≈ 2,2 sn).
- **Proaktif Kota Koruma:** Kalan istek sayısı 1 veya 0'a düştüğünde, 429 hatası yemeden önce pencerenin sıfırlanması beklenir.
- **Akıllı Devre Kesici (Circuit Breaker):** Sunucu kesintisi ("temporarily disabled") ve 429 soğuma durumlarında tüm uygulama anında fail-fast moduna geçerek kapalı veya aşırı yüklü sunucuya istek yağdırmaz.
- **Platform Hız Sınırlayıcı Delegasyonu:** `PlatformRateLimiter` AniList için doğrudan `AniListGovernor`'a delege edildi; çift slot alma sorunu çözülerek yazma hızı normale döndürüldü.

---

#### 🔄 2. AniList İçe Aktarım Sonsuz Döngü Düzeltmesi (`AniListImportManager`)
- **Boş Sayfa Sonsuz Döngüsü Giderildi:** AniList'in son sayfada `hasNextPage=true` ve boş liste döndürmesi nedeniyle oluşan sonsuz sayfalama döngüsü engellendi (`emptyPageStreak >= 2` ve 200 sayfa tavanı).
- **Kısmi Aktarım Güvencesi (`ImportResult`):** Hız sınırı veya kesinti nedeniyle yarım kalan içe aktarımlarda kullanıcının mevcut listesi silinmez (`allowDelete = false`), eldeki kayıtlar birleştirilir ve kalanlar sonraki tura bırakılır.
- **Fark (Delta) Senkronizasyonu Korundu:** `fetchListsSince` delta sorgusu ve `updatedSince` filtresi korunarak yalnızca değişen kayıtların çekilmesi sağlandı.

---

#### ✍️ 3. Liste Yazma & Kimlik Çözümleme İyileştirmeleri (`AniListSyncManager`)
- **Var Olan Kayıtlar İçin Yazma Kilidi Açıldı:** AniList üzerinde zaten var olan liste kayıtları (`aniListEntryId != null`) için yerel kimlik uyuşmazlığı kontrolü nedeniyle yazma işleminin atlanması sorunu giderildi (sunucu zaten kendi listesindeki kimliği biliyor).
- **MAL Kimlik Doğrulama Esnekliği:** AniList'in kendi `Media.idMal` alanı autoriter çapraz referans kabul edildi; başlık/yıl farklarında yazımın engellenmesi önlendi (`synonyms` alias desteği, `yearNear` +/-1 yıl ve null yıl desteği).
- **Oturum Önbellek Temizliği:** Hesap bağlantısı kesildiğinde doğrulanmış medya kimlik önbellekleri sıfırlanır.

---

#### 🛡 4. Kuyruk ve Ağ İyileştirmeleri
- **`PendingSyncDrainer` Düzeltmesi:** Senkronizasyon kapalıyken veya bekleyen işlem yokken hatalı sayılan ve 5 hakkı tüketen başarı kriteri düzeltildi; 429 durumlarında retry bütçesi harcanmaz.
- **ANR Koruması (`KitsugiApiBase`):** `executeAniListQuery` içindeki bloklayan OkHttp çağrısı `withContext(Dispatchers.IO)` içine alınarak ana iş parçacığı donmaları (ANR) tamamen önlendi.
- **Teşhis Paneli Entegrasyonu:** `NotificationDiagnostics` sorguları ve profil GraphQL istekleri `AniListGovernor` hız havuzuna bağlandı.

---

[EN]
### 🚀 What's New & Highlights

This release delivers the **AniList Rate Limiting, Import & Synchronization Architecture Overhaul**! After an extensive audit across 280+ files interacting with AniList, 5 critical root causes were addressed, backed by the new **AniListGovernor** centralized rate-limiting architecture.

---

#### ⚡️ 1. Centralized AniList Rate Governor (`AniListGovernor`)
- **Unified Rate Budget:** Explore, search, detail, notification, airing calendar, list import, and mutation writes are now coordinated through a single rate limiter, preventing different subsystems from pushing each other into HTTP 429 limits.
- **Adaptive Request Intervals:** Calculates intervals dynamically from AniList's active degraded limit (30 req/min) via `X-RateLimit-Limit` headers (60s / limit ≈ 2.2s).
- **Proactive Rate Protection:** Pauses proactively when remaining requests reach ≤ 1 to avoid triggering 429 errors.
- **Smart Circuit Breaker:** Implements fail-fast cooldowns during API outages ("temporarily disabled") and 429 cooldowns across all network paths.
- **Rate Limiter Delegation:** `PlatformRateLimiter` delegates AniList slots directly to `AniListGovernor`, eliminating double-slot acquisitions and restoring full write speed.

---

#### 🔄 2. AniList Import Infinite Loop Fix (`AniListImportManager`)
- **Empty Page Infinite Loop Resolved:** Fixes AniList's edge-case behavior where the last page returns `hasNextPage=true` with an empty `mediaList` (`emptyPageStreak >= 2` break condition and 200 page ceiling).
- **Partial Import Protection (`ImportResult`):** Interrupted imports due to rate limits no longer clear or drop the local library; fetched items are safely merged (`allowDelete = false`).
- **Delta Sync Preserved:** Maintains incremental delta synchronization (`fetchListsSince`) to only query entries updated since the last check.

---

#### ✍️ 3. Mutation Writes & Identity Resolution (`AniListSyncManager`)
- **Unblocked Existing Entry Writes:** Removed blocking identity hurdles when updating/deleting list entries already verified by AniList (`aniListEntryId != null`).
- **MAL Cross-Identity Flexibility:** Treats AniList's own `Media.idMal` as authoritative, relaxing strict title/year requirements (`synonyms` aliases, `yearNear` +/-1 year, null year tolerance).
- **Session Cache Reset:** Clears verified media caches upon account disconnection.

---

#### 🛡 4. Queue & Network Performance
- **`PendingSyncDrainer` Fix:** Fixed failure classification when sync is idle or disabled; 429 rate limits no longer burn retry attempts.
- **ANR Protection (`KitsugiApiBase`):** Moved blocking query execution inside `executeAniListQuery` to `withContext(Dispatchers.IO)`, eliminating main-thread freezes across 56 call sites.
- **Diagnostics & Profile Integration:** Routed `NotificationDiagnostics` and profile GraphQL queries through the central rate governor.
