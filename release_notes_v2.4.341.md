# Kitsugi Beta v2.4.341

## 🇹🇷 Türkçe

### 🔑 MDBList API Kotası Artık Gerçek
- **Sabit "1.000 / 1.000" bitti:** Kota kutusu artık hesabın gerçek kalan istek sayısını gösteriyor. Değer, MDBList'in her yanıtta döndürdüğü `X-RateLimit-Limit` / `X-RateLimit-Remaining` / `X-RateLimit-Reset` başlıklarından ve `GET https://api.mdblist.com/user` uç noktasından okunuyor.
- **Yenile düğmesi:** Ayarlar → Entegrasyonlar → MDBList sekmesindeki "API Kotası" kartından tek dokunuşla sunucudan güncel değer alınır; sıfırlanmaya kalan süre canlı geri sayılır.
- **Doğru plan bilgisi:** Ücretsiz (1.000/gün) ve destekçi kademeleri (10.000–1.500.000/gün) sunucudan gelen `plan` ve `rate_limit` alanlarıyla gösterilir.
- **Detay sayfası rozeti:** "Harici Puanlar" başlığındaki MDBList rozeti artık gerçek kalan hakkı yazar (ör. `987/1.000`). Sunucudan doğrulanmış değer yoksa sayı uydurulmaz, rozet gizlenir.
- **Kota dostu istekler:** 429 yanıtlarında yeniden deneme kaldırıldı; her yeniden deneme günlük kotadan bir istek daha harcıyordu. Kota yenileme istekleri 15 dakikada bir ile sınırlandırıldı.

### 🧮 Sayım ve Kalıcılık
- Kota; sunucudan okunan kesin değer + o okumadan sonra uygulamanın yaptığı istekler olarak izlenir. Uç nokta başlık döndürmezse değer "tahmini" olarak etiketlenir, kesin gibi gösterilmez.
- Son değer API anahtarının SHA-256 parmak iziyle cihazda saklanır; uygulama yeniden açıldığında anında gösterilir. Anahtarın kendisi bu depoya yazılmaz.
- Gece yarısı (UTC) geçişinde kota otomatik olarak günlük limite döner; kullanıcı hiçbir şey yapmak zorunda değildir.

### 🔔 Bildirimler — Tüm Kaynaklar İçin Ortak Görünüm ve Ortak Mantık
- **Tek iskelet, altı kaynak:** AniList, MyAnimeList, TMDB & Simkl, Kitsu, Shikimori ve Bangumi sayfaları artık aynı üst bölümü (başlık barı + kaynak hapı + filtre şeridi), aynı listeyi ve aynı boş/hata/sonsuz kaydırma davranışını kullanıyor. Önceki farklı boşluklar, farklı hizalamalar ve "bir kaynakta çalışıp diğerinde çalışmayan" davranışlar kaldırıldı.
- **Kayan başlık (soft collapse):** Aşağı kaydırırken başlık barı yumuşak bir animasyonla yukarı kayıp gidiyor; **kaynak hapı ve filtre şeridi sabit kalıyor**. Yukarı kaydırınca liste başına yaklaşıldığında başlık geri geliyor. Kaydırma bittiğinde yarım kalan durum en yakın uca sıçramasız oturuyor.
- **Ortak, yatay kaydırılabilir filtre şeridi:** Tüm kaynaklarda aynı bileşen; seçili sekme kendiliğinden görünür alana geliyor, seçim rengi yumuşak animasyonla değişiyor. MAL'da ekran dışına taşan kategoriler (Forum Alıntıları, Bahisler, Takip Konuları …) artık sağa kaydırarak erişilebilir.
- **Filtresiz kalan kaynak kalmadı:** Bildirim ucu olmayan kaynaklara da istemci tarafı sekmeler eklendi — Simkl: **Tümü / Yayınlanan / Yaklaşan**, Kitsu: **Tümü / Yayın / Anime / Manga**, Shikimori: **Tümü / Okunmamış / Bildirimler / Haberler / Medya**, Bangumi: **Tümü / Yayın / İlerleme**. MAL'a **Okunmamış** sekmesi eklendi.
- **Sekmelerde sayı rozetleri:** Her sekme kaç kayıt içerdiğini gösteriyor (MAL'da sunucudan gelen gerçek sayılar korunur).
- **Zaman grupları:** Liste tüm kaynaklarda **Yaklaşan / Bugün / Dün / Bu Hafta / Daha Eski / Tarihsiz** başlıklarıyla gruplandı; her başlıkta o gruptaki kayıt sayısı yazıyor.
- **Tema uyumlu özet:** Kaynak hapının sağında **"47 bildirim • 25 yeni"** özeti görünüyor (filtreden bağımsız, doğru sayı).
- **Aşağı çekip yenileme:** Altı kaynakta da aynı plazma göstergesiyle çalışıyor; gösterge, istek hiç başlamazsa 4 saniye sonra kendini kapatıyor.
- **Sekmeye tekrar dokun → listenin başına dön.**
- **Boş/hata durumunda tek dokunuşla "Tekrar dene"** (üst bara dönmek gerekmiyor).
- **Kaynak notu artık listeyi kaplamıyor:** Uzun "bu API kişisel bildirim sunmaz" açıklamaları iki satırlık özete indi, dokununca animasyonla açılıyor.
- **Tarihsiz kalan kayıtlar düzeltildi:** AniList bildirimleri ve Shikimori mesaj/hareket satırları artık gerçek zaman damgası taşıyor; "en yeni üstte" sıralaması ilk kez gerçekten çalışıyor.
- **Yeni "Yaklaşan" grubu:** Gelecek tarihli yayın satırları (Simkl/Kitsu/MAL) artık "Bugün" altında görünmüyor, listenin en üstünde **Yaklaşan** başlığında toplanıyor.
- **Dolu listede oluşan yenileme hatası sessiz kalmıyor:** aşağıda kısa bir hata satırı + "Tekrar dene" çıkıyor; kaydırma konumu bozulmuyor.
- **MAL eylem kartı yalnızca gerekince görünüyor:** hesap eşleşmiyorsa veya web oturumu yoksa; her şey yolundaysa liste başında boşuna yer kaplamıyor.
- **MAL "Okunmamış" sekmesi boş kalırsa** bir sonraki sayfayı kendisi getiriyor.
- **Üst bardaki yenile simgesi artık duruma bağlı:** yalnızca gerçekten yüklenirken dönüyor (sürekli dönen animasyon kaldırıldı).
- **Sekme/sayfa değişince başlık yumuşakça geri açılıyor**; ekran okuyucular için sekmeler `Tab` rolünde ve seçili olarak işaretli; kaynak notu kartında aç/kapat oku var.
- **AniList filtreleri arasında yatay kaydırma korundu**; filtre değişiminde akış kimliği sabitlendi (sekmeye dönüşte liste bir an boş görünmüyor).

### 🧪 Test
- `NotifDateGroupingTest`: gün sınırları (Yaklaşan/Bugün/Dün/Bu Hafta/Daha Eski/Tarihsiz) ve liste sıralaması bozulmadan başlık ekleme davranışı doğrulanıyor.

---

## 🇬🇧 English

### 🔑 Real MDBList API Quota
- **No more hard-coded "1,000 / 1,000":** the quota card now shows the account's actual remaining requests, read from MDBList's `X-RateLimit-Limit` / `X-RateLimit-Remaining` / `X-RateLimit-Reset` response headers and from `GET https://api.mdblist.com/user`.
- **Refresh button:** one tap on the "API Quota" card (Settings → Integrations → MDBList) reads the live value; the time until reset counts down in real time.
- **Correct plan information:** Free (1,000/day) and supporter tiers (10,000–1,500,000/day) are displayed from the server's `plan` and `rate_limit` fields.
- **Detail page badge:** the MDBList chip next to "External Ratings" now shows the real remaining quota (e.g. `987/1,000`) and stays hidden until an authoritative value exists — no invented numbers.
- **Quota-friendly requests:** retries on HTTP 429 were removed (each retry burned another daily request); quota refreshes are limited to once every 15 minutes.

### 🧮 Accounting and Persistence
- The quota combines the last authoritative server reading with the requests this app made afterwards. When an endpoint returns no rate-limit headers the value is labelled "estimated" instead of being presented as exact.
- The last value is stored per SHA-256 API-key fingerprint on the device and shown immediately after a restart; the key itself is never written to that store.
- At UTC midnight the quota rolls over to the daily limit automatically.

### 🧪 Tests
- `MdbListQuotaTest`: header parsing (including `RateLimit-*` variants, epoch-millis and delta-seconds resets), `/user` JSON parsing, local decrement, UTC rollover, 429 handling and the on-disk format.

### 🔔 Notifications — One Shared Layout and Logic for Every Source
- **One skeleton, six sources:** AniList, MyAnimeList, TMDB & Simkl, Kitsu, Shikimori and Bangumi now share the same header (title bar + source pill + filter band), the same list and the same empty/error/infinite-scroll behaviour. The previous mismatched spacing, alignment and "works in one source only" quirks are gone.
- **Soft collapsing header:** Scrolling down slides the title bar away with a soft animation while the **source pill and filter band stay pinned**. Scrolling back up reveals it when the list reaches the top, and a partly collapsed state settles smoothly to the nearest edge.
- **Shared scrollable filter band:** One component everywhere; the selected tab scrolls itself into view and the selection colour animates softly. MAL categories that used to overflow off-screen (Forum Quotes, Bets, Watched Topics…) are now reachable by swiping.
- **No unfiltered source left:** Sources without a notification endpoint gained client-side tabs — Simkl: **All / Aired / Upcoming**, Kitsu: **All / Release / Anime / Manga**, Shikimori: **All / Unread / Notifications / News / Media**, Bangumi: **All / Airing / Progress**. MAL gained an **Unread** tab.
- **Count badges on every tab** (server totals are still honoured on MAL).
- **Date groups:** Every source now groups rows under **Upcoming / Today / Yesterday / This Week / Earlier / Undated** with per-group counts.
- **Theme-aware summary:** The source pill row shows **"47 notifications • 25 new"**, computed independently of the active filter.
- **Pull to refresh** on all six sources with the shared plasma indicator, plus a safety timeout so the spinner can never hang.
- **Tapping the active tab scrolls the list back to the top.**
- **One-tap "Try again"** on empty/error states.
- **Source notes no longer take over the list:** long "this API has no notification endpoint" explanations collapse to a two-line summary that expands on tap.
- **Undated rows fixed:** AniList notifications and Shikimori message/activity rows now carry real timestamps, so "newest first" ordering finally works.
- **New "Upcoming" group:** future-dated airing rows (Simkl/Kitsu/MAL) no longer appear under "Today" — they collect at the top under **Upcoming**.
- **Refresh failures on a non-empty list are no longer silent:** a compact error row with "Try again" appears and the scroll position is preserved.
- **The MAL action card only shows when it matters** (account mismatch or missing web session) instead of occupying the top of the list.
- **The MAL "Unread" tab loads the next page by itself** when the loaded pages contain no unread rows.
- **The header refresh icon reflects state:** it only spins while actually loading (the constant animation is gone).
- **Switching pages/tabs smoothly re-expands the header**; tabs are exposed to screen readers as `Tab` with a selected state, and the source-note card gained an expand/collapse chevron.
- **Horizontal swiping between AniList filters is preserved**, and the flow identity is now stable so returning to a tab no longer flashes an empty list.

### 🧪 Tests
- `NotifDateGroupingTest` covers day boundaries and verifies that grouping inserts headers without reordering rows.
