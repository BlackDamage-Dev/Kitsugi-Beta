# Kitsugi Beta v2.4.341

## 🇹🇷 Türkçe

### 🔔 Bildirimler — Tüm Kaynaklar İçin Ortak Görünüm ve Ortak Mantık
- **Tek iskelet, altı kaynak:** AniList, MyAnimeList, TMDB & Simkl, Kitsu, Shikimori ve Bangumi sayfaları artık aynı üst bölümü (başlık barı + kaynak hapı + filtre şeridi), aynı listeyi ve aynı boş/hata/sonsuz kaydırma davranışını kullanıyor. Önceki farklı boşluklar, farklı hizalamalar ve "bir kaynakta çalışıp diğerinde çalışmayan" davranışlar kaldırıldı.
- **Kayan başlık (soft collapse):** Aşağı kaydırırken başlık barı yumuşak bir animasyonla yukarı kayıp gidiyor; **kaynak hapı ve filtre şeridi sabit kalıyor**. Yukarı kaydırınca liste başına yaklaşıldığında başlık geri geliyor. Kaydırma bittiğinde yarım kalan durum en yakın uca sıçramasız oturuyor.
- **Ortak, yatay kaydırılabilir filtre şeridi:** Tüm kaynaklarda aynı bileşen; seçili sekme kendiliğinden görünür alana geliyor, seçim rengi yumuşak animasyonla değişiyor. MAL'da ekran dışına taşan kategoriler (Forum Alıntıları, Bahisler, Takip Konuları …) artık sağa kaydırarak erişilebilir.
- **Filtresiz kalan kaynak kalmadı:** Bildirim ucu olmayan kaynaklara da istemci tarafı sekmeler eklendi — Simkl: **Tümü / Yayınlanan / Yaklaşan**, Kitsu: **Tümü / Yayın / Anime / Manga**, Shikimori: **Tümü / Okunmamış / Bildirimler / Haberler / Medya**, Bangumi: **Tümü / Yayın / İlerleme**. MAL'a **Okunmamış** sekmesi eklendi.
- **Sekmelerde sayı rozetleri:** Her sekme kaç kayıt içerdiğini gösteriyor (MAL'da sunucudan gelen gerçek sayılar korunur).
- **Zaman grupları:** Liste tüm kaynaklarda **Bugün / Dün / Bu Hafta / Daha Eski / Tarihsiz** başlıklarıyla gruplandı; her başlıkta o gruptaki kayıt sayısı yazıyor.
- **Tema uyumlu özet:** Kaynak hapının sağında **"47 bildirim • 25 yeni"** özeti görünüyor (filtreden bağımsız, doğru sayı).
- **Aşağı çekip yenileme:** Altı kaynakta da aynı plazma göstergesiyle çalışıyor; gösterge, istek hiç başlamazsa 4 saniye sonra kendini kapatıyor.
- **Sekmeye tekrar dokun → listenin başına dön.**
- **Boş/hata durumunda tek dokunuşla "Tekrar dene"** (üst bara dönmek gerekmiyor).
- **Kaynak notu artık listeyi kaplamıyor:** Uzun "bu API kişisel bildirim sunmaz" açıklamaları iki satırlık özete indi, dokununca animasyonla açılıyor.
- **AniList filtreleri arasında yatay kaydırma korundu**; filtre değişiminde akış kimliği sabitlendi (sekmeye dönüşte liste bir an boş görünmüyor).

### 🧪 Test
- `NotifDateGroupingTest`: gün sınırları (Bugün/Dün/Bu Hafta/Daha Eski/Tarihsiz) ve liste sıralaması bozulmadan başlık ekleme davranışı doğrulanıyor.

---

## 🇬🇧 English

### 🔔 Notifications — One Shared Layout and Logic for Every Source
- **One skeleton, six sources:** AniList, MyAnimeList, TMDB & Simkl, Kitsu, Shikimori and Bangumi now share the same header (title bar + source pill + filter band), the same list and the same empty/error/infinite-scroll behaviour. The previous mismatched spacing, alignment and "works in one source only" quirks are gone.
- **Soft collapsing header:** Scrolling down slides the title bar away with a soft animation while the **source pill and filter band stay pinned**. Scrolling back up reveals it when the list reaches the top, and a partly collapsed state settles smoothly to the nearest edge.
- **Shared scrollable filter band:** One component everywhere; the selected tab scrolls itself into view and the selection colour animates softly. MAL categories that used to overflow off-screen (Forum Quotes, Bets, Watched Topics…) are now reachable by swiping.
- **No unfiltered source left:** Sources without a notification endpoint gained client-side tabs — Simkl: **All / Aired / Upcoming**, Kitsu: **All / Release / Anime / Manga**, Shikimori: **All / Unread / Notifications / News / Media**, Bangumi: **All / Airing / Progress**. MAL gained an **Unread** tab.
- **Count badges on every tab** (server totals are still honoured on MAL).
- **Date groups:** Every source now groups rows under **Today / Yesterday / This Week / Earlier / Undated** with per-group counts.
- **Theme-aware summary:** The source pill row shows **"47 notifications • 25 new"**, computed independently of the active filter.
- **Pull to refresh** on all six sources with the shared plasma indicator, plus a safety timeout so the spinner can never hang.
- **Tapping the active tab scrolls the list back to the top.**
- **One-tap "Try again"** on empty/error states.
- **Source notes no longer take over the list:** long "this API has no notification endpoint" explanations collapse to a two-line summary that expands on tap.
- **Horizontal swiping between AniList filters is preserved**, and the flow identity is now stable so returning to a tab no longer flashes an empty list.

### 🧪 Tests
- `NotifDateGroupingTest` covers day boundaries and verifies that grouping inserts headers without reordering rows.
