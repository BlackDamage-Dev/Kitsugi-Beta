# Kitsugi Beta v2.4.388

[TR]
### 🚀 Yenilikler ve Öne Çıkan Geliştirmeler

Bu sürümde; Listem ekranındaki kilitlenmeleri ve ANR sorunlarını kökten çözen **Listem Açılış Bakımı İyileştirmesi**, **İndeksli Duplicate Planlayıcı**, kapsamlı hata ayıklama sunan **KitsugiLog Kalıcı Tanı Günlüğü**, **UiFreezeWatchdog Donma Bekçisi**, **AniListGate Akıllı Kota Kapısı** ve **Bangumi Negatif Önbellek** mimarisi yer alıyor!

---

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

---

#### 📝 KitsugiLog — Kalıcı ve Bloklamayan Tanı Günlüğü
- **Kalıcı Dosya Kaydı:** Sistem logcat'inin dolup silinmesi veya uygulamanın kapanması durumunda dahi tüm kritik adımlar `files/diagnostics/kitsugi_runtime.log` dosyasında korunur (4 MB döner dosya, ~8 MB tavan disk alanı).
- **Sıfır UI Etkisi:** Disk yazımı tek bir düşük öncelikli arka plan iş parçacığında (`KitsugiLogIO`) bloklamasız kuyrukla gerçekleşir.
- **Canlı Bellek Tamponu:** Bellekte son 3.000 satır saklanır; Ayarlar ▸ Sistem ▸ Geliştirici Günlükleri ekranında anında filtrelenebilir.
- **Tekrar Sıkıştırma:** Arka arkaya gelen mükerrer log satırları birleştirilerek disk ve bellek tasarrufu sağlanır.

---

#### ⏱️ UiFreezeWatchdog — UI Donma Takibi
- Ana iş parçacığı arka planda hafif pinglerle sürekli gözetlenir.
- 2.5 saniyeyi aşan donmalarda anlık yığın izi (stack trace) yakalanıp `FREEZE` kanalı altında kaydedilir; 8 saniyenin üzerindeki takılmalar doğrudan kritik hata olarak işaretlenir.

---

#### 🌐 Ağ Denetimi & AniListGate Kota Kapısı
- **KitsugiNetworkLogInterceptor:** Host, uç nokta yolu, HTTP durum kodu, milisaniye cinsinden gecikme ve `Retry-After` / `X-RateLimit-*` başlıkları hassas bilgiler (token/şifre) filtrelenerek denetlenir.
- **AniListGate:** 60 saniyelik kayan pencere (sliding window), HTTP 429 soğuma süresi koruması ve kullanıcı/arka plan önceliklendirmesi.
- **PendingSyncDrainer:** Arka plan kuyruğu kota cezası aldığında gereksiz istek atmak yerine soğuma süresini bekleyip kendiliğinden devam eder.

---

#### 🌸 Bangumi & Teşhis Doğruluğu İyileştirmeleri
- **Bangumi 404 Negatif Önbelleği:** Kesin 404 yanıtları için kısa ömürlü negatif önbellek uygulandı; zaman aşımı ve iptal durumları önbelleğe alınmaz.
- **Gelişmiş ANR ve Çökme Raporu:** Sistem ANR kaydı varken logcat boş diye "sebep bulunamadı" denmez; ANR yığın izinde ana thread önceliklendirilir; JVM heap miktarı cihaz RAM'i gibi gösterilmez.

#### 📥 İndirilenler Ekranı & Çevrimdışı Deneyim İyileştirmeleri
- **İçeriğe Göre Gruplu Görünüm:** Aynı yapımın bölümleri tek başlık altında toplanır, rozetli bilgi kartı ("n bölüm • m tamamlandı • toplam boyut") sunulur, dokununca kolayca daraltılıp açılabilir ve grup başlığından tüm bölümler tek dokunuşla seçilebilir.
- **Kuyruk Önceliği Taşıma:** Bekleyen indirmeler yukarı/aşağı taşınabilir (`AnimeDownloadManager.moveQueuedDownload()`); kuyruktaki sıra serbestçe belirlenebilir. Sadece bekleyen indirmeler arasında güvenli takas yapılır; devam eden veya tamamlanan kayıtların konumu bozulmaz.
- **Gelişmiş Sıralama Seçenekleri:** Kuyruk sırası (varsayılan), Başlık, Bölüm, Boyut ve En Yeni ölçütlerine göre sıralama imkânı. Yapılan sıralama tercihi kalıcı olarak hatırlanır.
- **Çoklu Seçim & Toplu Silme:** Uzun basışla seçim modu tetiklenir, tümünü seç seçeneğiyle onaylı toplu temizlik yapılabilir. Altyazı ve videolarda Scoped Storage / MediaStore onay akışı eksiksiz korunur.
- **Hız Geçmişi Grafiği (Sparkline):** İndirilenler özet kartında son ~40 saniyenin anlık indirme hızını gösteren dinamik mini Canvas sparkline grafiği eklendi (saniyede 1 örnekleme, sıfır recomposition yükü).
- **Sezgisel Geri Tuşu Yönetimi:** Geri tuşu önce çoklu seçim modunu, ardından arama çubuğunu kapatacak şekilde akıcı hâle getirildi.

---

[EN]
### 🚀 What's New & Highlights

This release features the **Downloads Screen & Offline UX Revamp**, **Listem ANR Resolution & Off-Main Maintenance Chain**, **Indexed Same-Source Deduplication**, the all-new **KitsugiLog Persistent Diagnostics Engine**, **UiFreezeWatchdog**, **AniListGate Rate Gate**, and **Bangumi Negative Caching**!

---

#### 📥 Downloads Screen & Offline Experience Enhancements
- **Content-Grouped Video View:** Episodes of the same anime or show are cleanly grouped under collapsible title headers with badge counters ("n episodes • m completed • total size"). Episodes can also be multi-selected directly from the group header.
- **Queue Priority Reordering:** Change the priority of pending downloads using Move Up / Move Down controls (`AnimeDownloadManager.moveQueuedDownload()`). Swapping strictly adjusts QUEUE records without disturbing active or finished downloads.
- **Persistent Video Sorting:** Sort by Queue Order (default), Title, Episode Number, File Size, or Newest. Preferences are preserved across app restarts.
- **Multi-Select & Batch Deletion:** Long-press to activate multi-selection mode, select all items, and perform confirmed batch deletions with full Android Scoped Storage / MediaStore permission handling.
- **Real-Time Speed Sparkline:** A smooth mini Canvas sparkline on the overview card visualizes download speeds over the last ~40 seconds (sampled once per second with zero recomposition flooding).
- **Refined Back Navigation:** Pressing back dismisses active selection mode first before closing search.

---

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

---

#### 📝 KitsugiLog — Persistent & Non-Blocking Diagnostics
- **Persistent Storage:** Diagnostics are retained in `files/diagnostics/kitsugi_runtime.log` (4 MB rolling file, ~8 MB max disk footprint) even if system logcat buffers clear or the app restarts.
- **Zero UI Overhead:** Disk I/O runs on a dedicated low-priority daemon worker thread (`KitsugiLogIO`) through a non-blocking queue.
- **Live Memory Buffer:** Retains the last 3,000 log events in memory for real-time inspection via Settings ▸ System ▸ Developer Logs.
- **Repeated Line Compression:** Sequential duplicate log lines are merged with repetition counters.

---

#### ⏱️ UiFreezeWatchdog — Main Thread Hang Detection
- Probes the main Looper thread with continuous heartbeats.
- Detects UI hangs >2.5s and logs the active stack trace under the `FREEZE` channel; stalls exceeding >8s are elevated to critical errors.

---

#### 🌐 Network Diagnostics & AniListGate Rate Limiting
- **Network Audit Log:** Logs endpoint paths, HTTP codes, duration, and rate-limit headers (`Retry-After`, `X-RateLimit-*`) while scrubbing auth headers.
- **AniListGate:** Implements a sliding 60-second window, respects HTTP 429 cooldowns, and prioritizes interactive actions over background sync jobs.
- **PendingSyncDrainer:** Cooldown-aware sync queue scheduling avoids rate limit penalties.

---

#### 🌸 Bangumi & Diagnostic Accuracy Polish
- **Bangumi 404 Negative Cache:** Avoids repeated lookups on definitive 404s without polluting cache on timeouts or network cancellations.
- **Refined Crash & ANR Reporting:** Prioritizes main-thread ANR stack traces, accurately correlates system exit records, and clarifies JVM heap vs device RAM.

