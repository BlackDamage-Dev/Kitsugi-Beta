# Kitsugi Beta v2.4.342

## 🇹🇷 Türkçe

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
