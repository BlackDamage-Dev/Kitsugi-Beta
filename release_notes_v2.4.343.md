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

### 🔔 Bildirimler — Kalan Son İki Açık Kapandı

**1) Bangumi'nin kendi bildirim anahtarı var artık**

Önceden Bangumi bildirimleri yalnızca ana **"Yayın bildirimleri"** anahtarı açıkken çalışıyordu; Bangumi'yi tek başına açıp kapatmak mümkün değildi. Artık diğer beş kaynak gibi kendi anahtarı var:

- Ayarlar > Bildirimler'de **Bangumi bildirimleri** anahtarı (hesap bağlı değilse pasif ve "Bangumi hesabını bağlayın" açıklamasıyla),
- Android 13+ için anahtar açılırken bildirim izni istenir (diğer kaynaklarla aynı davranış),
- Arka plan denetimi artık yalnızca Bangumi açıkken de planlanıyor; kapatınca Bangumi için hiç istek atılmaz.

**2) "Okunmamış" artık gerçekten okunuyor**

Sunucuda okundu alanı olmayan kaynaklarda (Simkl, Kitsu, Bangumi, MAL türetilmiş kayıtlar) ve Shikimori'de rozet hiç düşmüyordu, çünkü uygulama okundu durumunu hiçbir yere yazmıyordu. Yeni yerel **görüldü defteri**:

- Bir bildirime dokunduğunuzda o kayıt "görüldü" işaretlenir; rozet ve **Okunmamış** sekmesi anında güncellenir (ağ isteği yok),
- **"Yeni"** sayısı yalnızca görülmemiş **ve** son 72 saat içindeki kayıtları sayar — arşivden gelen aylık eski kayıtlar sayacı şişirmez,
- Filtre şeridinin sonunda **"Tümünü okundu"** çipi: tek dokunuşla kaynağın yüklü tüm bildirimleri okundu sayılır,
- Bu eylem yalnızca okundu durumu yerelde tutulan kaynaklarda görünür; MAL ve AniList'te "yeni" sayısı sunucudan geldiği için yerelde işaretlemek yanıltıcı olurdu, o yüzden oralarda gösterilmez,
- Defter kaynak başına 600 kayıtla sınırlı ve en eskiyi atar; uygulama yeniden başlasa da korunur.

**3) Okunmamış sekmesi artık her kaynakta**

Simkl, Kitsu ve Bangumi'ye de **Okunmamış** sekmesi eklendi. Böylece altı kaynağın filtre şeridi aynı sırayla başlıyor: **Tümü / Okunmamış / …** (MAL ve AniList'te bu sekme sunucu sayaçlarıyla çalışır).

### 🧪 Notlar
- Teknik ayrıntı ve kalan bilinçli sınırlar: `PLAN_TASK_BILDIRIMLER_ORTAK_GORUNUM_2026-10-31.md`.

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

### 🔔 Notifications — The Last Two Gaps Closed

**1) Bangumi has its own notification switch**

Bangumi notifications used to run only while the master **"Airing notifications"** switch was on, so there was no way to enable or mute Bangumi on its own. It now behaves like the other five sources:

- A **Bangumi notifications** switch in Settings > Notifications (disabled with a "connect your Bangumi account" hint when no account is linked),
- On Android 13+ the notification permission is requested when you flip it on (same flow as every other source),
- The background check is scheduled for Bangumi alone too, and no request is made for it once the switch is off.

**2) "Unread" is actually read now**

For sources whose server has no read flag (Simkl, Kitsu, Bangumi, MAL-derived rows) and for Shikimori, the badge never went down because the app never recorded a read state. The new local **seen ledger**:

- Tapping a notification marks it seen; the badge and the **Unread** tab update immediately (no network call),
- The **"New"** count only includes unseen rows from the **last 72 hours**, so month-old archived entries no longer inflate it,
- A **"Mark all read"** chip at the end of the filter band clears every loaded notification of that source in one tap,
- That action is only shown where the read state is stored locally; MAL and AniList get their "new" counts from the server, so a local mark there would be misleading,
- The ledger keeps 600 entries per source, drops the oldest first, and survives app restarts.

**3) Every source now has an Unread tab**

Simkl, Kitsu and Bangumi gained an **Unread** tab, so all six sources start their filter band with **All / Unread / …** (on MAL and AniList that tab is backed by server counts).

### 🧪 Notes
- Implementation details and the deliberate remaining limits live in `PLAN_TASK_BILDIRIMLER_ORTAK_GORUNUM_2026-10-31.md`.
