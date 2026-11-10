# Çözüm Raporu — Bildirimler: Tüm Kaynaklarda Ortak, Temaya Uyumlu, Kapsamlı Görünüm (2026-10-23)

## İstek
Bildirimler sayfasının (ekran görüntüsündeki kaynak seçici dahil) tüm kaynaklar arasında
ortak, düzgün, sorunsuz, daha kapsamlı ve kullanıcı dostu olması; uygulamanın temasına
(Listem kaynak seçicisi dili) uyması ve eksiklerin tamamlanması.

## Bulunan kök nedenler / eksikler
1. **Kaynak başına ayrı liste kodu:** AniList kendi `LazyColumn`'unu, diğer 5 kaynak
   `SourceNotifPage`'i kullanıyordu; hata durumunda hiçbirinde "Tekrar Dene" yoktu,
   boş durumlar kaynaktan kaynağa farklıydı.
2. **Bağlantı kapısı hatası:** Ekran, ViewModel'i yalnızca hesap bağlıysa çağırıyordu
   (`if (isKitsuConnected)` vb.). Oysa Kitsu/Simkl/Bangumi ViewModel'leri bağlı değilken
   de yerel kayıt + takvim yedeği üretmek için yazılmıştı — bu yedekler **ulaşılamaz**dı;
   kullanıcı çıkmaz bir "giriş yapın" metni görüyordu, eylem butonu da yoktu.
3. **Tarih sözleşmesi yoktu:** Her kaynak kendi biçimini kullanıyordu (MAL sunucu metni,
   Shikimori geçmişinde ham `2026-10-21`, diğerleri `dd MMM yyyy HH:mm`); AniList ve
   Shikimori kayıtlarında `tsMs` hiç yoktu → ortak sıralama/gruplama imkânsızdı.
4. **Sıralama tutarsızdı:** Arşiv birleştirme yalnızca arşiv doluyken sıralıyordu;
   Kitsu yaklaşan bölümler artan, diğerleri azalan sıradaydı.
5. **Tema sapmaları:** Seçici sayfasında sabit `Color(0xFF27AE60)` / teşhiste
   `Color(0xFFFF6B6B)`; tüm kaynaklarda tek tema vurgu rengi (Listem'deki platform
   marka renkleri yoktu); seçili filtre metni koyu marka renklerinde okunaksızdı.
6. **Kapsam eksikleri:** Kaynak seçicide ve hap butonda yalnızca AniList'in okunmamış
   rozeti vardı; toplam/yeni özeti, tarih gruplaması, aşağı çekerek yenileme yoktu;
   kaynaklar arasında kaydırarak geçiş kapalıydı. Medya posteri, `userId` var diye
   avatar gibi yuvarlak kırpılıyordu.

## Yapılanlar

### ViewModel (`KitsugiNotificationsViewModel.kt`)
- Tüm kaynaklara `tsMs` + tek tarih biçimi: `formatNotifTimestamp()` →
  "Bugün 14:32" / "Dün 09:10" / "12 Eki 14:32" / "12 Eki 2025 14:32".
  AniList (`createdAt`), Shikimori mesaj + geçmiş de artık `tsMs` taşıyor;
  MAL'da sunucu metni yalnızca tarih çözülemezse yedek.
- `archiveAndMerge` artık her zaman yeni → eski sıralar (tarihsizler en sonda);
  Shikimori geçmişi de aynı sıralamayla yayınlanır.

### Ekran (`KitsugiNotificationsScreen.kt`)
- **Tek ortak liste:** `NotifListPage` — AniList (6 alt filtre dahil) + 5 kaynak aynı
  bileşeni kullanır: kaynak açıklama notu (platform logolu, marka rengi çerçeveli),
  yükleniyor, hata + **Tekrar Dene** + hata kodu, boş durum, tarih gruplu liste
  (**Yaklaşan / Bugün / Dün / Bu Hafta / Daha Eski / Diğer**), sonsuz kaydırma,
  "daha eski yüklenemedi" satırı ve **aşağı çekerek yenileme** (uygulama geneli
  `KitsugiPullToRefreshIndicator` ile aynı).
- **Bağlantı kapısı düzeltildi:** AniList dışındaki kaynaklar bağlı değilken de
  yüklenir; yerel yedek doluysa liste + açıklama gösterilir, gerçekten boşsa
  platform logolu **"Hesabı Bağla"** eylemli durum ekranı çıkar.
- **Tema:** `NotifPlatform.brandColor` Listem ile birebir aynı renkler
  (AniList `#02A9FF`, MAL `#2E51A2`, Simkl `#E5A00D`, Kitsu `#FD755C`,
  Shikimori `#8E44AD`, Bangumi `#F09199`). Hap buton, seçici sayfası (seçili çerçeve/
  zemin/onay), yenile ikonu, filtre başlıkları, bildirim kartındaki kaynak rozeti ve
  okunmamış noktası marka rengini kullanır. Sabit yeşil/kırmızı →
  `KitsugiColors.AccentGreen` / `AccentRed`; seçili başlık metni okunabilirlik için
  `TextPrimary` kalır, marka rengi çerçeve/rozet/onaydadır.
- **Kapsam:** Seçici sayfasında her kaynakta toplam bildirim sayısı
  ("açıklama • N bildirim") ve okunmamış rozeti; hap satırının sağında
  "N bildirim • M yeni" özeti; hap rozeti artık tüm kaynakların okunmamışını gösterir.
- **Kart düzeltmesi:** Yuvarlak kırpım yalnızca medya bağlantısız kullanıcı
  bildirimlerinde; medya posterleri köşeli. Okunmamış satır: kalın başlık +
  marka rengi zemin/nokta.
- Dış pager artık kaydırılabilir (AniList iç pager'ı kendi alanında önceliklidir).

### Bağlantı (`AppRootDetailPages.kt`)
- Yeni `onConnectPlatform` geri çağrısı: Bildirimler → "Hesabı Bağla" ilgili kaynağın
  hesap ayarlarına gider (MAL'da tek-oturum akışı korunur).

### Stringler (TR + EN)
- `notif_group_*` (6), `notif_date_today/yesterday`, `notif_items_count`,
  `notif_empty_subtitle` eklendi; iki dil dosyası da XML olarak doğrulandı.

## Değişen dosyalar
1. `app/src/main/java/com/kitsugi/animelist/ui/screens/notifications/KitsugiNotificationsScreen.kt`
2. `app/src/main/java/com/kitsugi/animelist/ui/screens/notifications/KitsugiNotificationsViewModel.kt`
3. `app/src/main/java/com/kitsugi/animelist/AppRootDetailPages.kt`
4. `app/src/main/res/values/strings.xml`
5. `app/src/main/res/values-en/strings.xml`
6. Bu rapor.

## Doğrulama
- Sandbox ortamında JDK/Android SDK bulunmadığından Gradle derlemesi çalıştırılamadı;
  bunun yerine: parantez dengesi, XML geçerliliği, `PullToRefreshBox` imzasının repo
  içindeki mevcut kullanımlarla birebir karşılaştırılması, tüm `R.string` anahtarlarının
  TR/EN varlığı ve diff'in tamamının gözden geçirilmesi yapıldı.
- Cihazda önerilen kontrol: 6 kaynağın her birinde (bağlı/bağlı değil) ilk açılış,
  aşağı çekerek yenileme, tarih grup başlıkları, seçicideki sayı/rozetler ve
  "Hesabı Bağla" yönlendirmesi.
