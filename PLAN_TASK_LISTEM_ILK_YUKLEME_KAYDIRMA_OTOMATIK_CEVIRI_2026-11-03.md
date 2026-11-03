# PLAN / TASK — Listem yüklenmesi, sekme geçişleri ve otomatik açıklama çevirisi

**Tarih:** 2026-11-03  
**Dal:** `arena/9607f46a-kitsugi-beta`  
**Temel commit:** `a6dda9d6df6e6babf3f93d9750ff7331c4bf6dfe`  
**Durum:** Kod değişiklikleri ve odaklı JVM kontrolleri tamamlandı; Android derlemesi ve cihaz doğrulaması bekliyor.

## İstek ve kapsam

- Birden çok kaynak ve çok sayıda kayıt olduğunda Listem'in ilk açılışındaki beklemeyi azaltmak.
- Kaynak sayfaları arasında yatay geçişte görülen zıplama ve tutarsız içeriği gidermek.
- Otomatik çeviri açıkken Listem'den açılan kayıtların açıklamalarının çevrilmesini düzeltmek.
- Bu akışlarda tespit edilen ilgili yarış, önbellek ve veri bütünlüğü sorunlarını gidermek.

## Yapılan görevler

### Listem

- [x] Listem ve uygulama kökü için aynı Room anlık görüntüsü kullanıldı; yinelenen tablo gözlemi kaldırıldı.
- [x] Kaynak dizinleme ve birleşik kütüphane hazırlığı ana iş parçacığı dışında, Listem'e girilmeden önce de çalışacak şekilde düzenlendi. Yeni girdi geldiğinde eski hazırlık iptal edilebilir.
- [x] Kaynak sayfaları aynı filtreleme, sıralama ve durum gruplamasıyla önceden hazırlanıyor. İlk yükleme boş liste olarak sunulmuyor.
- [x] Izgara satırları ihtiyaç duyuldukça hazırlanıyor; sayfa kaydırma konumlarının ve üst başlık durumunun korunması iyileştirildi.
- [x] Kimlik köprüsü önbelleği, Room satırı değişmeden güncellendiğinde yeniden hazırlama için sürüm takibi eklendi. Yetişkin içerik ayarı değişirken eski görünümün sızması engellendi.
- [x] Sadece MAL bağlantısı kapalı diye geçerli MAL/Jikan kayıtlarını AniList kaynağına çeviren güvensiz otomatik onarım kaldırıldı.
- [x] Bekleyen hazırlık sırasında gösterilen eski satırdan ilerleme artırılırken mümkün olan en güncel anlık görüntü kullanıldı; Simkl alt başlık onarımı yalnızca ilgili sütunu ve beklenen eski değeri güncelliyor.

### Otomatik açıklama çevirisi

- [x] Kayıt, detay veya sonradan gelen yerel açıklama üzerinden çeviri başlatılması ve ayar değişikliklerinin izlenmesi düzenlendi.
- [x] Eski detay, açıklama, çeviri ve kısmi yanıtların daha yeni bir isteği ezmesi nesil/kimlik kontrolleriyle engellendi.
- [x] Bellek önbelleği anahtarına açıklama metni, içerik türü ve kaynak/hedef dil bilgileri eklendi; eski tam-metin çeviri önbelleği için yeni sürüm anahtarı kullanıldı.
- [x] Uzun metinlerde korunan bağlantı/spoiler işaretleri her parça için ayrı doğrulanıyor. Eksik çeviri tam çeviri olarak önbelleğe kaydedilmiyor ve daha sonra yeniden denenebiliyor.
- [x] Açıklama kaydı yalnızca synopsis sütununu, beklenen önceki değer hâlâ geçerliyse güncelliyor; ilerleme, puan ve diğer kullanıcı alanları topluca üzerine yazılmıyor.
- [x] Genel arama ve kullanıcının bilerek başlattığı harici çevirmen eylemi korundu.

## Doğrulama

- [x] Kotlin 2.0.21 ile, Android/Compose/JUnit bağımlılıkları için geçici taklitler kullanılarak **41 odaklı JVM test metodu** çalıştırıldı; tümü geçti. Bunlara büyük liste, kaynak kimliği, filtreleme, çeviri önbelleği ve eşzamanlı sonuç testleri dahildir.
- [x] Taklit Android/Room/ana iş parçacığı ile **5** Listem hazırlık senaryosu; taklit ağ ve Room ile gerçek `TranslationManager` üzerinde **4** çeviri senaryosu geçti.
- [x] SQLite üzerinde synopsis ve Simkl alt başlığı için **4** hedefli güncelleme/yarış kontrolü geçti; `git diff --check` temizdi.
- [ ] **Tam Gradle/Android derlemesi yapılmadı.** Bu ortamda Android SDK ve proje bağımlılıkları kullanılamadı; yukarıdaki kontroller gerçek Android derlemesinin yerine geçmez.
- [ ] **Gerçek cihaz/emülatör ve canlı çeviri servisi doğrulanmadı.** Yükleme süresi veya kaydırma akıcılığı için cihaz ölçümü yapılmadı.

## Kalan cihaz/CI kontrol listesi

1. JDK 17, Android SDK 36 ve proje Gradle bağımlılıklarıyla uygun `foss`/`gms` varyantlarının derleme ve birim testlerini çalıştırın.
2. Çok kaynaklı, binlerce kayıt içeren bir kütüphanede ilk Listem açılışını ve eşitleme sürerken yeniden açılışı ölçün.
3. Kaynaklar arasında filtre ve sıralama açıkken yatay geçişi, geri dönüşte kaydırma konumunu ve yetişkin içerik ayarını kontrol edin.
4. Listem kayıtlarında otomatik çeviri açık/kapalı, hedef dil değişimi, uzun bağlantılı açıklama, zayıf bağlantı ve tekrar deneme durumlarını gerçek cihazda deneyin.
5. Ekran görüntüsündeki Kitsu bekleyen eşitleme uyarısını gerekirse ayrıca doğrulayın; bu paket canlı Kitsu eşitlemesinin başarıyla çalıştığını doğrulamaz.

## Paket notu

ZIP, değişen kaynak ve test dosyalarını depo köküne göre özgün yollarında, bu plan/task belgesiyle birlikte içerir. Tam proje, Android SDK, APK veya geçici test taklitleri içermez.
