# Kitsugi Beta v2.4.350

## 🇹🇷 Türkçe

### 🔔 Bildirimler: Tüm Kaynaklarda Ortak, Temaya Uyumlu ve Kapsamlı Görünüm
- **Bağlantı Kapısı Hatası Düzeltildi:** Kitsu, Simkl ve Bangumi hesapları bağlı değilken bile ViewModel'deki yerel liste ve takvim yedekleri artık kesintisiz yükleniyor. Gerçekten boş olduğunda ise platform logolu ve doğrudan o kaynağın hesap ayarlarına yönlendiren "Hesabı Bağla" eylem butonu gösterilir.
- **Tek Liste Bileşeni (`NotifListPage`):** AniList'in 6 filtresi dahil tüm kaynaklar artık tek tip yükleniyor, hata ("Tekrar Dene" butonu ve hata kodu desteği) ve boş durum bileşenini kullanıyor.
- **Tarih Grupları:** Bildirimler artık zaman dilimlerine göre düzenleniyor: *Yaklaşan*, *Bugün*, *Dün*, *Bu Hafta*, *Daha Eski* ve *Diğer*.
- **Ortak Tarih Formatı:** AniList, Shikimori ve MAL dahil 6 kaynakta da birleşik zamanlama sözleşmesi ("Bugün 14:32", "Dün 09:10", "12 Eki 14:32") uygulandı ve tüm kayıtlar yeniden eskiye sıralandı.
- **Listem ile Uyumlu Marka Renkleri:** AniList mavisi (`#02A9FF`), MAL laciverti (`#2E51A2`), Simkl sarısı (`#E5A00D`), Kitsu turuncusu (`#FD755C`), Shikimori moru (`#8E44AD`) ve Bangumi pembesi (`#F09199`) kaynak seçiciye, çerçeveye, rozetlere, yenileme ikonuna ve okunmamış noktalarına taşındı. Sabit renkler tema tokenlarına bağlandı.
- **Kapsamlı Kaynak Seçici & Sayaçlar:** Her kaynakta `@kullanıcı`, toplam bildirim sayısı ve okunmamış rozeti; hap satırının sağında "N bildirim • M yeni" özeti sunuldu.
- **Gelişmiş Etkileşimler:** Plazma göstergeli aşağı çekerek yenileme (`KitsugiPullToRefreshIndicator`), sekmeler arası yatay kaydırma ve aktivite beğenilerinde posterlerin kare/dikdörtgen formunu koruyan kart kırpım düzeltmesi yapıldı.

---

## 🇬🇧 English

### 🔔 Notifications: Unified, Theme-Integrated, and Comprehensive Overhaul
- **Connection Gate Resolved:** Unconnected Kitsu, Simkl, and Bangumi accounts now seamlessly load local lists and calendar backups. If genuinely empty, an interactive platform-branded "Connect Account" button navigates directly to that provider's account settings.
- **Unified List Component (`NotifListPage`):** Replaced divergent list implementations with a unified page component supporting loading states, retryable errors with platform error code chips, and consistent empty states.
- **Date Grouping:** Notifications are grouped into standardized date headers: *Upcoming*, *Today*, *Yesterday*, *This Week*, *Older*, and *Other*.
- **Unified Timestamp Normalizer:** Added `tsMs` and unified date formatting ("Today 14:32", "Yesterday 09:10", "12 Oct 14:32") across all 6 providers with chronological (newest to oldest) ordering.
- **Brand Colors Matching My List:** Platform brand colors (AniList blue, MAL navy, Simkl gold, Kitsu orange, Shikimori purple, Bangumi pink) now theme platform selector chips, active borders, unread dots, badges, and refresh spinners.
- **Comprehensive Scope & Summaries:** Added `@user`, notification totals, unread badges to provider selectors, and an inline "N notifications • M new" counter summary.
- **Enhanced Interactions:** Integrated plasma pull-to-refresh, smooth horizontal page swiping, and aspect-ratio preservation for media posters in activity cards.
