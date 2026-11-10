# Kitsugi Beta v2.4.342

## 🇹🇷 Türkçe

### 🚦 "AniList kota doldu" — Kök Neden Düzeltmesi
- **Sorunun kaynağı uygulamaydı, AniList değil.** AniList'in fiilî limiti **30 istek/dk**
  (dokümante edilen 90 değil; API yıllardır "degraded"). Kitsugi bu kotayı birbirinden
  habersiz **dört ayrı kapıdan** harcıyordu: okuma yolu 350 ms aralıkla (~171 istek/dk),
  liste yazmaları (ekleme/düzenleme/silme) hız sınırı **olmadan**, liste yenileme hız sınırı
  **olmadan**, profil ekranları yine sınırsız.
- **Tek kota kapısı (`AniListGate`):** 60 sn'lik kayan pencere (limit − güvenlik payı),
  400 ms asgari aralık, 2 sn'de en fazla 5 istek, `Retry-After` / `X-RateLimit-Reset`
  destekli 429 soğuması ve `X-RateLimit-Remaining: 0` erken uyarısı. Artık **tüm** AniList
  trafiği bu kapıdan geçiyor.
- **Öncelik:** arka plan (bildirimler, kişi görselleri, Fandom zenginleştirmesi) kullanıcıya
  ayrılmış kotayı yiyemez; liste yenileme sürerken yapılan düzenlemeler beklemez.
- **429 artık thread kilitlemiyor:** eskiden ceza penceresinde çağıran thread 60–120 sn
  uyutuluyordu (kilit altında). Artık iş **bekleyen-senkron kuyruğuna** giriyor ve ceza
  bitince **kendiliğinden** gönderiliyor.

### ✍️ Düzenleme Başına İstek Sayısı 4–7 → 1
- **Kimlik doğrulama önbelleği (6 saat):** her düzenlemede tekrar eden `Media(id)` sorgusu
  kaldırıldı.
- **Favori sorgusu gereksizse atlanıyor:** yalnızca bölüm/puan değiştiren düzenlemelerde
  artık `isFavourite` sorgusu ve `ToggleFavourite` mutasyonu çıkmıyor.
- **Yazma birleştirme:** aynı kayda 1,2 sn içinde gelen düzenlemeler (bölüm +1'e art arda
  basmak, puan kaydırıcısı) **tek** uzak yazmaya indirgeniyor; tüm uzak yazmalar sırayla
  çalışıyor. Yerel kayıt her zaman anında yazılıyor.
- **Düzenleyici açılışı:** 4 AniList isteği → ilk açılışta 1, sonraki açılışlarda 0–1
  (30 dk Viewer/liste seçenekleri önbelleği, 5 dk liste meta önbelleği).

### 🔄 Liste Yenileme
- Profil bilgisi her yenilemede yeniden çekilmiyor (15 dk önbellek), `Viewer` kimliği
  24 saat önbellekte. Sayfalar arası istekler tempolu gidiyor: 429 yerine biraz daha
  yavaş ama **tamamlanan** bir yenileme.
- Kota nedeniyle yarıda kalan yenilemede mesaj artık doğru:
  **"⏳ AniList istek sınırı doldu — N sn sonra tekrar denenebilir"** (eskiden
  "çevrimdışı" deniyordu).

### ✅ Hata Mesajları
- Kota hatası ile çevrimdışı olma artık ayrı mesajlarla gösteriliyor.
- Kota nedeniyle ertelenen kayıtlar ceza bitiminde kendiliğinden gönderiliyor; kullanıcının
  aynı düğmeye tekrar basmasına gerek yok.

### 🧩 "Günlerdir hiçbir şey AniList'e gitmiyor" — Sessiz Veri Kaybı Düzeltmesi
- **24 saatlik otomatik yenileme yerel kayıtları siliyordu:** açılışta listeler
  `replaceSourcePreservingAdultFlags` ile **sil-yeniden-yaz** yapıyordu. Uzak listede
  bulunmayan (yani henüz AniList'e gönderilememiş) **yeni kayıtlar** ve o kaynak için
  bekleyen **kuyruk işleri** her 24 saatte bir yok oluyordu. Artık birleştirme yapılıyor
  (`smartImport`, `allowDelete = false`) ve **önce bekleyen değişiklikler gönderiliyor**.
- **Bekleyen değişiklik uzak kopyayla ezilmiyor:** kuyrukta işi olan satır, liste
  yenilemesinde/otomatik senkronda korunur.
- **Deneme sayacı artık "ölüm cezası" değil:** 5 denemeyi aşmış işler uygulama açılışında
  ve **"Şimdi dene"** ile tazelenir; kota hatası sayacı hiç artırmaz.
- **Kimlik doğrulaması her şeyi sessizce kilitleyemez:** MAL kataloğuna ulaşılamadığında
  (429/403/timeout) yazma sessizce atlanıyordu; artık yalnızca guard'ın **açıkça
  reddettiği** eşleşmeler durdurur, katalog erişilemezse native MAL satırlarında yazma
  devam eder ve durum kullanıcıya bildirilir.

### 💬 "Neden eşitlenmiyor?" — Doğru ve tek anlamlı uyarılar
- Listem ekranına **durum şeridi** eklendi: kota · oturum geçersiz · bağlı değil ·
  eşitleme kapalı · internet yok · AniList API kapalı · kimlik doğrulanamadı.
  Şerit bekleyen değişiklik sayısını ve (kotada) kalan saniyeyi gösterir, **"Şimdi dene"**
  düğmesiyle kuyruğu zorlar.
- Tek tek işlemlerde çıkan mesajlar da nedene göre ayrıldı; eskiden her hata
  "📵 Çevrimdışı kaydedildi" diyordu. Aynı gerekçe 2 dakikada birden sık gösterilmez.
- Başarılı görünen ama AniList'in **atlandığı** turlar (bağlı değil / kimlik / kapalı)
  artık sessiz değil.

### 🌍 "Arka planda ne oluyor?" — Artık **tüm** kaynaklar için, sade dille
- **AniList ile sınırlı değil:** MyAnimeList, Kitsu, Shikimori, Simkl ve Bangumi'nin
  eşitleme durumu da aynı yerde, aynı cümleyle gösteriliyor. Listem ekranındaki şerit ve
  Bildirimler ekranındaki yeni kart kaynak kaynak yazıyor:
  - `⏳ AniList istek sınırı doldu — 12 değişiklik 43 sn sonra kendiliğinden gönderilecek`
  - `🔑 MyAnimeList oturumu geçersiz — 3 değişiklik gönderilemiyor. Ayarlar > Platformlar üzerinden yeniden giriş yapın.`
  - `🔌 Kitsu hesabı bağlı değil — 5 değişiklik yalnızca bu cihazda duruyor`
  - `⚙️ Shikimori eşitlemesi ayarlardan kapalı — 2 değişiklik yalnızca bu cihazda duruyor`
  - `📵 İnternet yok — Simkl için 1 değişiklik bağlantı gelince gönderilecek`
  - `🛠 Bangumi servisi geçici olarak yanıt vermiyor — 4 değişiklik daha sonra gönderilecek`
  - `🔎 AniList: 2 değişikliğin yapım karşılığı doğrulanamadı (yanlış kayda yazmamak için beklemede)`
- **Bilgi, hata gibi bağırmıyor:** "kapalı" ve "bağlı değil" satırları nötr renkte;
  kırmızı yalnızca gerçek arızalarda (kota, oturum, internet, servis, kimlik).
- **Sessiz kalan yerler kapandı:** açılıştaki otomatik eşitleme ve liste yenileme artık
  kaynak başına sonuç yazıyor; liste içe aktarma hataları ham `HTTP 401` yerine
  "oturum geçersiz, yeniden giriş yapın" diyor; eşitleme hataları hata kodu kaydına da
  düşüyor, böylece arama/detay ekranlarındaki kod rozetinden aynı açıklama okunabiliyor.
- **Profil ekranları:** kota uyarısı artık kaynağı ve kalan saniyeyi söylüyor
  (eskiden yalnız "bekleyin" diyordu).

### 🧾 Günlükler — artık **her şeyi** alıyor, çökmeden ve dondurmadan
- **Kalıcı günlük:** uygulama artık kendi günlüğünü `files/diagnostics/kitsugi_runtime.log`
  dosyasına **sürekli** yazıyor (4 MB'da döner, en fazla ~8 MB). Eski "Günlükler" ekranı
  yalnızca sistem logcat'ini okuyordu; Android 10+ üzerinde logcat halka tamponu dolunca
  **eski satırlar silinir** ve "az önce arka planda ne oldu" kanıtı kaybolurdu. Artık kaybolmuyor.
- **Neler kaydediliyor:** liste eşitleme (AniList · MyAnimeList · Kitsu · Shikimori · Simkl ·
  Bangumi) sonuçları, her ağ isteğinin host/yol/HTTP kodu/süresi ve kota başlıkları
  (`Retry-After`, `X-RateLimit-*`), kuyruk boşaltma turları, çağrı içe aktarma hataları,
  manga/eklenti kaynak tanıları, çökmeler, **donmalar** ve hata kodu kayıtları.
- **Donma tespiti:** ana iş parçacığı 2,5 sn'den uzun yanıt vermezse o anki **yığın izi**
  (hangi kod kilitledi) günlüğe yazılır. ANR'de süreç öldürülse bile sebep elimizde kalır.
- **Sistem günlüğü köprüsü:** uygulamanın kendi logcat satırları da kalıcı dosyaya aktarılır
  (saniyede en fazla 300 satır, tekrarlar sıkıştırılır) — böylece yüzlerce `Log.*` çağrısını
  tek tek değiştirmek gerekmeden her şey kaydedilir.
- **Donma yapmaz:** günlük yazımı tek bir düşük öncelikli arka plan iş parçacığında; kuyruk
  dolarsa satır **atılır ve sayılır** — ana ekran asla beklemez. Ayrıca kırıntı (ekmek
  kırıntısı) dosyası artık her dokunuşta değil, en fazla 1,5 sn'de bir yazılıyor.
- **Görüntüleyici:** Ayarlar ▸ Sistem ▸ Geliştirici günlükleri. Yeni "Uygulama günlüğü"
  (varsayılan) ve "Sistem (logcat)" kaynak seçimi; **konu** filtreleri (Eşitleme · Ağ ·
  Kaynak · Arka plan · Donma · Çökme · Sistem · Diğer), seviye filtreleri, arama, kopyala,
  kaydet, paylaş, temizle. Günlük boyutu ve atlanan tekrar sayısı üstte yazar.
- **Çökme raporuna eklenir:** çökme ekranındaki "paylaş" artık bu kalıcı günlüğü de gönderir.

### 🔔 Telefon Bildirimleri — "Gerçekten Düşüyor mu?" Turu

Bu sürüm, uygulama **içindeki** Bildirimler sayfası ile telefonun **kendi** bildirim alanı arasındaki farkı kapatıyor. Artık altı kaynağın tamamı için cihaz bildirimi üretiliyor ve üretilmediği durumda **neden** üretilmediği görülebiliyor.

- **Kaynak başına ayrı bildirim kanalı:** AniList, MyAnimeList, TMDB & Simkl, Kitsu, Shikimori ve Bangumi için ayrı kanallar oluşturuluyor. Ayarlar > Bildirimler'de AniList'i kapatıp MAL'ı açık tutabilirsiniz. (Önceden tek bir "Kitsugi Bildirimleri" kanalı vardı.)
- **Bildirimler artık ekranın üstünde beliriyor:** Eski kanal `IMPORTANCE_DEFAULT` ile açılıyordu, bu yüzden `PRIORITY_HIGH` yok sayılıyor ve bildirimler yalnızca gölgelikte sessizce duruyordu. Yeni kanallar **HIGH** önemle açılıyor.
- **Bildirime dokunmak doğru ekranı açıyor:** Önceden yalnızca `NAV_TARGET` adlı bir extra konuyordu ve bu extra uygulamada **hiçbir yerde okunmuyordu** — dokununca rastgele bir ekran açılıyordu. Artık `Kitsugianimelist://v1/notifications?source=…` bağlantısı kullanılıyor ve Bildirimler sayfası **bildirimin geldiği kaynağın sekmesinde** açılıyor.
- **Sürümlü bağlantı çözümleyicisindeki hata düzeltildi:** Uygulamanın kendi ürettiği `Kitsugianimelist://v1/...` bağlantılarında sürüm bilgisi URI'nin *authority* kısmında durur; çözümleyici ise ilk yol parçasında arıyordu. Bu yüzden TV kanalı ve benzeri bağlantılar sessizce çalışmıyordu.
- **İzin yoksa boşa ağ isteği yok:** Android 13+ bildirim izni verilmemişse worker hiçbir istek atmadan çıkıyor ve sebebi kaydediyor. Önceden her `notify()` çağrısı yakalanıp yutulan bir `SecurityException` üretiyordu.
- **Açılış seli engellendi:** Bir kaynak ilk kez etkinleştirildiğinde geçmiş bildirimler artık tek tek düşmüyor; yalnızca "görüldü" işaretleniyor. Sonraki bildirimler normal akıyor. Ayrıca bir çalıştırmada kaynak başına en fazla **6** bildirim gönderiliyor.
- **Bangumi de artık cihaz bildirimi üretiyor:** Bangumi API'sinde bildirim ucu yok; uygulama içi sayfayla aynı yolu kullanıyor — bgm subject → MAL kimliği çözümü + yayın takvimi eşleşmesi.
- **Aynı bildirim iki kez düşmüyor:** Bildirimler kaynak anahtarıyla etiketleniyor; farklı kaynakların aynı sayısal kimliği birbirini ezmiyor.
- **Bildirim görselleri ve zamanı:** Büyük görsel (kapak), doğru "ne zaman" bilgisi, kategori (etkinlik/sosyal) ve tema vurgu rengi; zil ikonu için ayrı tek renkli küçük simge.
- **Yeni "Cihaz bildirimleri" teşhis bölümü:** Bildirimler > 🐞 panelinin en üstünde:
  - izin / cihaz ayarı durumu (eksikse **"İzin ver / Ayarları aç"**),
  - arka plan denetiminin planlı olup olmadığı ve aralığı,
  - son çalışma zamanı,
  - kaynak başına son çalıştırmada **gönderilen** bildirim sayısı ve varsa hata,
  - **"Test bildirimi gönder"** düğmesi (uçtan uca kanıt).
- **İzin durumu değişince planlı iş uzlaştırılıyor:** Bildirim izni/cihaz ayarı uygulama kapalıyken değişmişse (ör. sistem ayarlarından kapatıldıysa) uygulama açılışında planlı iş yeniden değerlendirilir; her açılışta gereksiz WorkManager isteği kurulmaz.

- **Aynı bölüm iki kez bildirilmiyor:** Aynı yapıt hem MAL hem Kitsu (veya Bangumi) listesinde izleniyorsa her kaynak kendi defterini tuttuğu için aynı bölüm iki ayrı bildirim olarak düşüyordu. Bir çalıştırma içinde başlık + bölüm eşleşmesiyle tekrar engelleniyor.
- **"Gönderildi" defteri artık zaman damgalı:** Eski `StringSet` defteri okunduğunda sıra bilgisi kaybolduğu için budama rastgele kayıt siliyor, silinen bölüm 24 saatlik pencerede hâlâ göründüğü için **tekrar bildiriliyordu**. Artık `anahtar|zaman` biçiminde tutulup en eski kayıt atılıyor.
- **Bangumi listesi sunucudan da okunuyor:** Cihaz bildirimleri yalnızca yerel veritabanındaki Bangumi kayıtlarını görüyordu; hiç içe aktarılmamış (yalnızca Bangumi'de duran) kayıtlar sessizce kaçıyordu. Artık uygulama içi sayfayla aynı yol izleniyor: Bangumi "在看" koleksiyonu → MAL kimliği → takvim eşleşmesi, yerel liste yedek olarak duruyor.
- **Simkl tam liste senkronu 6 saatte bir:** Bildirim için her 30 dakikada bir izleme listesini baştan yazmak boşa pil/DB yüküydü; takvim kontrolü ise her çalıştırmada devam ediyor.

### 🧪 Test
- `DeepLinkTest` yeni vakalar: sürümün *authority*'de olduğu `Kitsugianimelist://v1/detail|play` bağlantıları ve `…/v1/notifications?source=…` bağlantısının kaynakla/kaynaksız çözümü.

---

## 🇬🇧 English

### 🚦 "AniList rate limit reached" — Root Cause Fix
- **The app was the cause, not AniList.** AniList's real limit is **30 req/min** (not the
  documented 90; the API has run degraded for a long time). Kitsugi was spending that budget
  through **four independent doors**: the read path at 350 ms intervals (~171 req/min), list
  writes (add/edit/delete) with **no** pacing at all, list refresh with **no** pacing, and
  profile screens also unpaced.
- **One quota gate (`AniListGate`):** rolling 60 s window (limit − safety margin), 400 ms
  minimum spacing, at most 5 requests per 2 s, `Retry-After` / `X-RateLimit-Reset` aware
  429 cool-down, and an early `X-RateLimit-Remaining: 0` guard. **All** AniList traffic now
  flows through it.
- **Priority:** background work (notifications, person images, Fandom enrichment) can no
  longer consume the budget reserved for the user, and a list refresh no longer blocks edits.
- **429 no longer blocks a thread:** previously the caller was slept for 60–120 s (while
  holding a lock). Now the write is queued and **retried automatically** when the penalty
  window ends.

### 🧩 "Nothing has synced to AniList for days" — Silent Data Loss Fix
- **The 24-hour auto refresh was deleting local rows:** on startup, lists were written with
  `replaceSourcePreservingAdultFlags`, a **delete-and-reinsert** cycle. New entries that were
  not yet on AniList (i.e. never pushed) and the **pending queue jobs** for that source were
  wiped every 24 hours. The auto refresh now **merges** (`smartImport`, `allowDelete = false`)
  and **pushes pending changes first**.
- **Pending changes are no longer overwritten** by the remote copy during refresh/auto sync.
- **The retry counter is no longer a death sentence:** jobs past 5 attempts are refreshed on
  app start and via **"Retry now"**; quota errors never consume attempts.
- **Identity verification can no longer block everything silently:** when the MAL catalog is
  unreachable (429/403/timeout) writes used to be skipped with no message. Now only explicit
  *rejections* stop a write; unreachable-catalog cases proceed for native MAL rows and the
  reason is surfaced to the user.

### 💬 "Why isn't it syncing?" — Accurate, single-cause warnings
- New **status banner** in the Library screen: quota · invalid session · not connected ·
  sync disabled · no internet · AniList API down · identity unverified. It shows the pending
  change count (and remaining seconds for quota) and offers **"Retry now"**.
- Per-action messages are now reason-specific (the old build always said
  "📵 Saved offline"), and the same reason is not repeated more than once every 2 minutes.
- Turns that *look* successful but skipped AniList (not connected / identity / disabled) are
  no longer silent.

### ✍️ Requests per Edit: 4–7 → 1
- **Identity verification cache (6 h):** the repeated `Media(id)` lookup per edit is gone.
- **Favourite sync skipped when unnecessary:** edits that only change progress/score no
  longer trigger `isFavourite` + `ToggleFavourite`.
- **Write coalescing:** edits arriving within 1.2 s (rapid "+1", score slider) collapse into
  a **single** remote write, and all remote writes are serialized. Local writes stay instant.
- **Editor open:** 4 AniList requests → 1 on first open, 0–1 afterwards (30 min viewer
  options cache, 5 min list-metadata cache).

### 🔄 List Refresh
- Profile info is no longer re-fetched on every refresh (15 min cache) and the `Viewer` id is
  cached for 24 h. Pages are paced: a slightly slower refresh that **finishes** instead of
  hitting 429.
- If a refresh is interrupted by the quota window, the message is now accurate:
  **"⏳ AniList rate limit reached — retry in N s."**

### ✅ Error Messages
- Rate limiting and being offline are now reported separately.
- Edits deferred by the quota are pushed automatically once the penalty window ends — no
  repeated tapping required.

### 🌍 "What is happening in the background?" — now for **every** source, in plain words
- **Not just AniList:** the sync state of MyAnimeList, Kitsu, Shikimori, Simkl and Bangumi is
  shown in the same place with the same wording. The Library banner and a new card on the
  Notifications screen list them source by source:
  - `⏳ AniList rate limit reached — 12 changes will be sent automatically in 43 s`
  - `🔑 MyAnimeList session expired — 3 changes can't be sent. Sign in again in Settings > Platforms.`
  - `🔌 Kitsu is not connected — 5 changes exist only on this device`
  - `⚙️ Shikimori sync is turned off — 2 changes exist only on this device`
  - `📵 No internet — 1 change for Simkl will be sent when you're back online`
  - `🛠 Bangumi is temporarily unavailable — 4 changes will be sent later`
  - `🔎 AniList: 2 changes could not be matched to a title (held back to avoid writing to the wrong entry)`
- **Informational, not alarming:** "turned off" and "not connected" lines use a neutral
  colour; red is reserved for real failures (quota, session, offline, service, identity).
- **Silent paths closed:** startup auto-sync and list refresh now record a per-source
  outcome; import errors say "session expired, sign in again" instead of raw `HTTP 401`;
  sync failures are also written to the error-code registry, so the code chip on search and
  detail screens shows the same explanation.
- **Profile screens:** the quota notice now names the source and the remaining seconds.

### 🧾 Logs — now capture **everything**, without crashing or freezing
- **Persistent log:** the app continuously writes its own log to
  `files/diagnostics/kitsugi_runtime.log` (rotates at 4 MB, ~8 MB max). The old "Logs" screen
  read only system logcat; on Android 10+ the logcat ring buffer drops old lines, so the
  evidence of "what just happened in the background" was usually gone. Not any more.
- **What is recorded:** list sync outcomes (AniList · MyAnimeList · Kitsu · Shikimori · Simkl ·
  Bangumi), every network request's host/path/HTTP code/duration plus quota headers
  (`Retry-After`, `X-RateLimit-*`), queue-drain rounds, import failures, manga/plugin source
  diagnostics, crashes, **freezes**, and error-code records.
- **Freeze detection:** if the main thread stops responding for more than 2.5 s, the current
  stack trace is written to the log — so even an ANR kill leaves the culprit behind.
- **System-log bridge:** the app's own logcat lines are streamed into the persistent file
  (max 300 lines per second, repeats collapsed), so all the existing `Log.*` calls are covered
  without touching hundreds of files.
- **No freezes:** logging happens on a single low-priority background thread; when the queue is
  full a line is **dropped and counted**, never blocking the UI. Breadcrumb writes are also
  throttled to once per 1.5 s.
- **Viewer:** Settings ▸ System ▸ Developer logs. New "App log" (default) and "System (logcat)"
  sources, topic filters (Sync · Network · Source · Background · Freeze · Crash · System ·
  Other), level filters, search, copy, save, share, clear. Log size and suppressed-repeat counts
  are shown on top.
- **Included in crash reports:** the crash screen's share action now attaches this persistent log.

### 🔔 Device Notifications — The "Does It Actually Land?" Pass

This release closes the gap between the in-app **Notifications** page and the phone's **own** notification shade. All six sources can now produce device notifications, and when they cannot, the reason is visible.

- **One channel per source:** Separate channels for AniList, MyAnimeList, TMDB & Simkl, Kitsu, Shikimori and Bangumi, so you can mute one source without muting all. (There used to be a single "Kitsugi Notifications" channel.)
- **Notifications now pop on screen:** The old channel was created with `IMPORTANCE_DEFAULT`, so `PRIORITY_HIGH` was ignored and notifications only appeared silently in the shade. New channels use **HIGH** importance.
- **Tapping opens the right screen:** The old code attached a `NAV_TARGET` extra that nothing in the app ever read — a tap opened whatever screen happened to be there. Notifications now carry `Kitsugianimelist://v1/notifications?source=…`, and the Notifications page opens **on the tab of the source that notified you**.
- **Fixed the versioned deep-link parser:** For the app's own `Kitsugianimelist://v1/...` links the version lives in the URI *authority*, while the parser looked for it in the first path segment. TV-channel links and friends were silently dead.
- **No pointless network calls without permission:** On Android 13+ without notification permission the worker exits before any request and records the reason. Previously every `notify()` threw a `SecurityException` that was swallowed.
- **First-run flood prevented:** Enabling a source for the first time seeds history as "seen" instead of posting it, and each run posts at most **6** notifications per source.
- **Bangumi now produces device notifications too:** Bangumi has no notification endpoint; the worker uses the same path as the in-app page — bgm subject → MAL id resolution plus airing-calendar matching.
- **No duplicate deliveries:** Notifications are tagged per source, so equal numeric ids from different sources no longer overwrite each other.
- **Images, timestamps and styling:** Cover art as a big picture, correct "when" value, category (event/social), theme accent colour, and a dedicated monochrome bell small icon.
- **New "Device notifications" diagnostics section** at the top of the 🐞 panel:
  - permission / device setting state (with **"Grant / Open settings"** when missing),
  - whether the background check is scheduled and at which interval,
  - last run time,
  - per-source count **posted** in the last run, plus any error,
  - a **"Send a test notification"** button (end-to-end proof).
- **The scheduled check is reconciled when the permission state changes** (for example after toggling notifications in system settings), without re-enqueuing WorkManager on every launch.

- **No duplicate alerts for the same episode:** If a show is on both your MAL and Kitsu (or Bangumi) list, each source kept its own ledger and the same episode arrived twice. During one run, title + episode matches are now suppressed.
- **The "already notified" ledger is timestamped:** The old `StringSet` ledger lost its order when read, so trimming dropped random entries — and a dropped episode, still inside the 24-hour window, was **notified again**. Entries are now stored as `key|timestamp` and the oldest are pruned.
- **Bangumi lists are read from the server too:** Device notifications only saw locally imported Bangumi entries; anything that lived only on Bangumi was silently missed. The worker now follows the same path as the in-app page — Bangumi "在看" collection → MAL id → airing match — with the local list as a fallback.
- **Simkl full-list sync runs every 6 hours:** Rewriting the watch list every 30 minutes just for airing checks was wasted battery and DB writes; the calendar check still runs on every pass.

### 🧪 Tests
- `DeepLinkTest` gained cases for authority-style `Kitsugianimelist://v1/detail|play` links and for `…/v1/notifications?source=…` with and without a source.
