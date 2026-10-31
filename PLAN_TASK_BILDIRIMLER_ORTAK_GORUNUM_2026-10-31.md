# Bildirimler — Ortak Görünüm ve Ortak Mantık (2026-10-31)

## Sorun
Bildirimler ekranındaki altı kaynak (AniList, MAL, TMDB & Simkl, Kitsu, Shikimori,
Bangumi) kendi üst barını, kendi filtre şeridini ve kendi liste iskeletini
çiziyordu. Sonuç:

- Aynı ekranda kaynaklar arasında farklı boşluk/hizalama.
- Yalnızca AniList ve MAL'da filtre şeridi; diğer dört kaynakta filtre yok.
- AniList filtresi `HorizontalPager`, MAL filtresi ayrı bir `horizontalScroll`
  satırı — iki ayrı görünüm ve iki ayrı davranış.
- Uzun kaynak notları ("bu API kişisel bildirim sunmaz…") listeyi kaplıyordu.
- Kaydırma sırasında üst bölüm için ortak bir davranış yoktu.
- MAL'da ekran dışına taşan kategoriler yalnızca elle kaydırılarak bulunuyordu,
  seçili sekme görünür alana getirilmiyordu.

## Çözüm
Yeni dosya: `app/src/main/java/com/kitsugi/animelist/ui/screens/notifications/KitsugiNotificationChrome.kt`

| Bileşen | Sorumluluk |
| --- | --- |
| `NotifChromeHeader` | Üst bar + kaynak hapı + ortak filtre şeridi. Aşağı kaydırınca **başlık barı** yumuşakça yukarı kayar; kaynak hapı ve şerit sabit kalır. |
| `NotifFilterBand` / `NotifFilterTab` | Soldan sağa kaydırılabilen, seçili sekmeyi görünür alana getiren, yumuşak renk animasyonlu ortak şerit. |
| `NotifFeedList` | Ortak liste: aşağı çekip yenileme, sonsuz kaydırma, hata kodu bloğu, kaynak notu, zaman grupları, boş/hata + "Tekrar dene". |
| `NotifChromeCollapseState` | Kaydırma fiziği: aşağıda topla, listede başa gelince aç, fling sonunda en yakın uca yumuşakça otur. |
| `notifDateGroup` / `groupNotifRows` | Bugün / Dün / Bu Hafta / Daha Eski / Tarihsiz grupları ve grup sayıları. |

## Kaynak başına sekmeler
- **AniList:** Tümü / Yayın / Aktivite / Forum / Takip / Medya (sunucu filtreleri;
  sekmeler arasında yatay kaydırma korunur).
- **MAL:** Tümü / **Okunmamış** / MAL kategorileri (sunucudan gelen gerçek sayılar).
- **TMDB & Simkl:** Tümü / Yayınlanan / Yaklaşan (`typeIdentifier` = aired/upcoming).
- **Kitsu:** Tümü / Yayın / Anime / Manga (`typeIdentifier` = release/list).
- **Shikimori:** Tümü / Okunmamış / Bildirimler / Haberler / Medya.
- **Bangumi:** Tümü / Yayın / İlerleme (`typeIdentifier` = airing/watching).

## Veri modeli eklemeleri
`KitsugiNotificationsViewModel` artık Simkl/Kitsu/Bangumi/Shikimori kayıtlarında
`NotifItem.typeIdentifier` alanını doldurur (arşivden geri okunurken de korunur);
filtre sekmeleri bu alandan türetilir. Ek ağ isteği yoktur.

- `getAniListState(filter)` artık her çağrıda yeni akış üretmiyor — sekme geçişinde
  listenin bir an boş görünmesine yol açan abonelik yenilemesi kalktı.
- `getMalUnreadCount()`: filtre seçiminden bağımsız okunmamış sayısı (başlık özeti).

## Test
`app/src/test/java/.../notifications/NotifDateGroupingTest.kt`
- Gün sınırları (Bugün/Dün/Bu Hafta/Daha Eski/Tarihsiz).
- Grup başlığının yalnızca grup değişiminde eklenmesi ve sıralamanın bozulmaması.
- Boş liste davranışı.

## Sürüm
`appVersionName` 2.4.340 → **2.4.341** (`release_notes_v2.4.341.md`).

---

## Ek tur (v2.4.342) — cihaz bildirim hattı

Soru: "Bildirimler sayfasına her kaynaktan kayıt geliyor; peki **telefonun kendi bildirim
alanına** da gerçekten düşüyor mu?"

### Bulunan gerçek hatalar

| # | Hata | Etki | Düzeltme |
|---|------|------|----------|
| 1 | Tek kanal + `IMPORTANCE_DEFAULT`, bildirimde `PRIORITY_HIGH` | Ekranda üst bildirim hiç görünmüyordu; yalnızca sessiz gölgelik kaydı | Kaynak başına `IMPORTANCE_HIGH` kanal (`KitsugiNotificationDispatcher.ensureChannels`) |
| 2 | `PendingIntent` yalnızca `NAV_TARGET` extra'sı taşıyordu; uygulama bu anahtarı hiç okumuyor | Bildirime dokunmak rastgele ekran açıyordu | `kitsugianimelist://v1/notifications?source=…` + `NotifOpenRequest` |
| 3 | `DeepLinkParser` sürümü ilk yol parçasında arıyordu; `Kitsugianimelist://v1/…` biçiminde sürüm *authority*'de | Uygulamanın kendi v1 bağlantıları (TV kanalı dâhil) sessizce çözümlenmiyordu | `host == "v1"` ise segment listesinin başına sürüm eklenir |
| 4 | Android 13+ izni yokken her `notify()` `SecurityException` atıyor, worker yutuyordu | Sessiz başarısızlık | `KitsugiNotificationDispatcher.gate()` + `NotificationDeliveryTracker.recordBlocked` |
| 5 | İlk çalıştırmada 24 saatlik/25 kayıt birikmiş durumdaydı | Kaynağı yeni açan kullanıcıya onlarca bildirim birden | İlk çalıştırma "seed" + kaynak başına 6/çalıştırma sınırı |
| 6 | Bangumi için cihaz bildirimi hiç yoktu | Bangumi kullanıcısı yalnızca uygulama içinde görüyordu | bgm subject → MAL kimliği çözümü + takvim eşleşmesi (uygulama içi sayfayla aynı yol) |
| 7 | Bildirim `id`'leri kaynaklar arasında çakışabiliyordu (AniList küçük id, diğerleri hash) | Bir bildirim diğerini eziyordu | `notify(tag = kaynakAnahtarı, id, …)` |
| 8 | Hat kapalıyken worker yine tam ağ turu atıyordu | Boşa istek, boşa pil | Erken çıkış + sebep kaydı |

### Yeni bileşenler

- `core/notifications/KitsugiNotificationDispatcher.kt` — kanal yönetimi, ön kontrol (gate),
  etiketli/gruplu gönderim, test bildirimi, sistem ayarlarına yönlendirme.
- `core/notifications/NotificationDeliveryTracker.kt` — kaynak başına son çalıştırma /
  gönderilen sayı / hata kaydı (teşhis paneli okur).
- `ui/screens/notifications/KitsugiNotificationDeviceStatus.kt` — panelin "Cihaz
  bildirimleri" bölümü (izin, WorkManager durumu, son çalışma, kaynak satırları, test).
- `ui/screens/notifications/NotifOpenRequest.kt` — deep link → sekme ön seçimi.
- `res/drawable/ic_notification_bell.xml` — tek renkli küçük simge.
- `NotificationScheduler.WORK_NAME` artık `public` (panel WorkManager durumunu sorgular).

### Kaynak gerçekleri (değişmedi)

AniList ve Shikimori'de **gerçek** bildirim ucu var. MAL, Simkl, Kitsu ve Bangumi'de yok;
cihaz bildirimleri uygulama içi sayfayla aynı türetilmiş yoldan üretilir (takvim + izleme
listesi, gerektiğinde çapraz kimlik). Bu yüzden "bildirim gelmiyor" şikâyeti olduğunda ilk
bakılacak yer: teşhis panelindeki **izin**, **planlı iş** ve **kaynak başına gönderilen** satırı.

### İkinci kontrol turu — kalan açıklar

| # | Bulgu | Durum |
|---|-------|-------|
| 9 | Bildirilen kayıt defteri (`StringSet`) sırasız; budama rastgele silip **tekrar bildirim** üretiyordu | ✔ zaman damgalı `NotifiedKeys` |
| 10 | Aynı bölüm MAL + Kitsu + Bangumi'de izleniyorsa iki-üç ayrı bildirim | ✔ çalıştırma içi `title#episode` tekrar engeli |
| 11 | Bangumi bildirimleri yalnızca yerel DB'yi görüyordu (sunucudaki "在看" listesi kaçıyordu) | ✔ `getUserCollections(DOING)` + yerel yedek |
| 12 | Simkl tam liste senkronu her çalıştırmada DB'yi yeniden yazıyordu | ✔ 6 saatlik eşik (`simkl_full_sync_at`) |
| 13 | **Bangumi'nin kendi bildirim anahtarı yok**; "Yayın bildirimleri" ana anahtarına bağlı | ⏳ bilinçli: ayar eklemek 8 dosyada değişiklik; bir sonraki tura bırakıldı |
| 14 | **Shikimori okundu durumu uygulamada işaretlenmiyor** → "Okunmamış" rozeti hiç düşmüyor | ⏳ API'de okundu işaretleme ucu yok/haritalanmadı; yerel "görüldü" damgası gerekir |
| 15 | MAL'ın web bildirimleri (kulüp/forum) cihaz bildirimi olarak düşmüyor | ⏳ arka planda MAL web çerezi kullanmak riskli (bot koruması) |
| 16 | Kitsu gövdesi `progress + 1` varsayıyor; `nextRelease` 2 bölüm ileriyi gösteriyorsa numara kayabilir | ⏳ düşük etki, API ek alan vermiyor |
