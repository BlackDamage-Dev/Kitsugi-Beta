# Kitsugi Beta v2.4.389

[TR]
### 🚀 Yenilikler ve Öne Çıkan Geliştirmeler

Bu sürümde; bildirim mimarisini baştan aşağı yenileyen **Bangumi Bağımsız Bildirim Anahtarı**, **Yerel Görüldü Defteri (NotifSeenStore)**, **Tüm Kaynaklarda Okunmamış Sekmesi**, **Birleşik Bildirim Kromu & Cihaz Tanı Paneli**, **Lucide HD İkon Seti**, **Listem ANR Kökten Çözümü**, **İndirilenler UX İyileştirmeleri** ve **TMDB Hesap Bildirimleri** yer alıyor!

---

#### 🔔 Bildirim Mimarisi & Yerel Görüldü Defteri (NotifSeenStore)
- **Bangumi Bağımsız Bildirim Anahtarı:** Bangumi bildirimleri artık ana "Yayın bildirimleri" anahtarından bağımsız olarak kendi anahtarıyla yönetilebilir (Ayarlar ▸ Bildirimler). Hesap bağlı değilse bilgilendirici uyarıyla pasif kalır, Android 13+ izin akışını tam destekler ve arka plan işi Bangumi açıkken de bağımsız çalışır.
- **Yerel Görüldü Defteri (`NotifSeenStore`):** Sunucu tarafında okundu bayrağı bulunmayan kaynaklar (Simkl, Kitsu, Bangumi, Shikimori, TMDB) için yerel kalıcı SharedPreferences kayıt defteri:
  - Bildirime dokunulduğu anda kayıt "görüldü" olarak işaretlenir; rozet ve Okunmamış sekmesi sıfır ağ isteğiyle anında güncellenir.
  - "Yeni" sayacı yalnızca görülmemiş **ve** son 72 saat içindeki bildirimleri kapsar; eski arşiv kayıtları sayacı şişirmez.
  - Filtre şeridinde yerel kaynaklar için **"Tümünü okundu"** çipi sunulur.
  - Kaynak başına 600 kayıt kapasiteli LRU mimarisi sayesinde uygulama yeniden başlasa dahi durum korunur.
- **Tüm Kaynaklarda Okunmamış Sekmesi:** Altı ana kaynak ve TMDB üzerinde standart filtre sekme dizilimi: **Tümü / Okunmamış / …**
- **Modern Bildirim Kromu & Cihaz Tanı Kartı:**
  - `KitsugiNotificationChrome` ile sekmeler, kaynak seçici, filtre çipleri ve akıcı yenileme animasyonu tek bir mimaride birleştirildi.
  - Cihaz bildirim izni ve pil optimizasyonu durumunu canlı gösteren `KitsugiNotificationDeviceStatus` tanı paneli eklendi.
  - Bildirimler için gün bazlı sezgisel tarih gruplaması (Bugün, Dün, Bu Hafta, Daha Eski, Tarihsiz).

---

#### ✨ Lucide HD İkon Seti & Üst Aksiyon Çubuğu
- **Lucide Vektör İkonları (`KitsugiIcons`):** Material kalın dolgulu ikonlar yerine şeffaf arka planlı, ince çizgili, 24dp ızgaralı ve tema vurgusunu otomatik alan yüksek çözünürlüklü vektör ikon setine geçildi.
- **Standart Üst Aksiyon Çubuğu:** Tüm ana ekranlarda tutarlı başlık, durum rozetleri ve aksiyon butonları sağlandı.

---

#### ⚡ Listem ANR Çözümü & Arka Plan Bakımı
- **Ana Thread Yükü Sıfırlandı:** Listem açılışındaki tüm ağır bakım işleri (`Dispatchers.IO` / `Dispatchers.Default`) arka plana taşındı.
- **SameSourceDuplicatePlanner:** Kütüphanedeki mükerrer kayıtlar indeksli planlayıcı ile $O(1)$/$O(N)$ seviyesinde taranır, UI asla kilitlenmez.
- **Atomik Room `@Transaction`:** Mükerrer temizliği ve senkronizasyon işleri veri kaybı riski olmadan tek işlemde tamamlanır.

---

#### 📥 İndirilenler Ekranı & Çevrimdışı Deneyim
- **İçeriğe Göre Gruplu Görünüm:** Aynı yapımın bölümleri genişletilebilir başlıklar altında toplanır.
- **Kuyruk Önceliği Değiştirme:** Bekleyen indirmeler yukarı/aşağı serbestçe taşınabilir (`moveQueuedDownload`).
- **Kalıcı Sıralama & Çoklu Seçim:** Kuyruk sırası, başlık, bölüm, boyut ve tarihe göre sıralama; toplu silme modu.
- **Anlık Hız Sparkline Grafiği:** Son ~40 saniyenin indirme hızını gösteren dinamik Canvas mini grafiği.

---

#### 🎬 TMDB Hesap Bildirimleri & Polling
- TMDB tam hesap eşitlemesi ve periyodik bildirim yoklama desteği.
- Bildirimler sayfasına TMDB kaynağı entegre edildi.

---

[EN]
### 🚀 What's New & Highlights

This release brings the comprehensive **Notification Architecture Overhaul** featuring the **Bangumi Independent Notification Switch**, **Local Seen Ledger (NotifSeenStore)**, **Universal Unread Tabs**, **Unified Notification Chrome & Device Diagnostics**, **Lucide HD Vector Icons**, **Listem ANR Resolution**, **Downloads UX Revamp**, and **TMDB Account Notifications**!

---

#### 🔔 Notification Architecture & Local Seen Ledger (NotifSeenStore)
- **Bangumi Independent Notification Switch:** Bangumi notifications can now be toggled independently of the master "Airing notifications" switch (Settings ▸ Notifications). Includes disabled state hint when unlinked, full Android 13+ permission flow, and independent background scheduling.
- **Local Seen Ledger (`NotifSeenStore`):** Persistent SharedPreferences registry for sources lacking server-side read flags (Simkl, Kitsu, Bangumi, Shikimori, TMDB):
  - Tapping any notification immediately marks it seen; live badge counts and the Unread tab update instantly with zero network traffic.
  - "New" count strictly considers unseen items from the last 72 hours, avoiding false inflation from archived history.
  - "Mark all read" chip available for local sources.
  - Bounded capacity (600 entries per source, LRU eviction) persisting across app restarts.
- **Universal Unread Tabs:** Consistent tab ordering across all sources (AniList, MAL, Simkl, Kitsu, Shikimori, Bangumi, TMDB): **All / Unread / …**
- **Modern Notification Chrome & Device Diagnostics:**
  - `KitsugiNotificationChrome` unifies tabs, source selector sheet, filter chips, and smooth refresh animations.
  - Live `KitsugiNotificationDeviceStatus` diagnostic panel displaying notification permissions and battery optimization states.
  - Date-boundary grouping (Today, Yesterday, This Week, Earlier, Undated).

---

#### ✨ Lucide HD Vector Icons & Top Action Bar
- **Lucide Vector Icons (`KitsugiIcons`):** Migrated to crisp, outline-based, 24dp grid HD vector icons with automatic theme tinting.
- **Canonical Top Action Bar:** Consistent styling and action placement across all primary screens.

---

#### ⚡ Listem ANR Resolution & Background Maintenance
- **Zero UI-Thread Overhead:** Startup maintenance routines run strictly on background dispatchers.
- **SameSourceDuplicatePlanner:** Fast token-indexed deduplication preventing UI stalls on large libraries.
- **Atomic Room `@Transaction`:** Merge survivor rows, delete duplicates, and clean stale sync jobs in a single safe transaction.

---

#### 📥 Downloads Screen & Offline Experience
- **Content-Grouped Video View:** Collapsible episode groups per series with badge counters and header selection.
- **Queue Priority Reordering:** Seamless move up / move down controls for pending downloads (`moveQueuedDownload`).
- **Persistent Sorting & Batch Deletion:** Multi-select mode with confirmed batch deletion and Scoped Storage handling.
- **Real-Time Speed Sparkline:** Smooth Canvas mini-graph tracking the last ~40 seconds of download speeds.

---

#### 🎬 TMDB Account Notifications & Polling
- Full TMDB account sync and periodic polling worker integration.
- TMDB integrated as a first-class source tab in Notifications.
