# Kitsugi — Shikimori Bilgi ve Entegrasyon Denetimi

**Tarih:** 2026-10-22  
**Dal:** `arena/d5385221-kitsugi-beta`  
**Temel commit:** `26bb0444028e1209ffa7cf64d9c270a330471363`

## 1. Görev ve kapsam

Shikimori detaylarındaki **Bilgi** ekranının az veri göstermesi sorununu yalnızca görünür ekranla sınırlı kalmadan; anime/manga detay ayrıştırması, ortak model, kalıcı önbellek, istatistikler, bağlantılar ve sosyal veri akışlarıyla birlikte denetlemek.

Kaynakta bulunmayan sıralama, yayın akışı veya kaynak materyali gibi alanlar uydurulmadı. Shikimori'nin sağladığı yararlı alanlar ortak modele ve kullanıcı arayüzüne taşındı.

## 2. Tamamlanan işler

- [x] Shikimori detay alanları için bağımsız ve test edilebilir `ShikimoriDetailParser` eklendi.
- [x] Format, sezon, yayınlanan bölüm, sonraki bölüm zamanı ve manga cilt/yayıncı alanları aktarıldı.
- [x] İngilizce, Japonca, Romaji, Rusça ve diğer başlıklar ayrıştırıldı; `[null]` değerleri temizlendi.
- [x] Lisanslayan, fansub ve fandub grupları Bilgi kartına eklendi.
- [x] Film/dizi kayıtlarında stüdyo, süre ve yaş sınırının yanlışlıkla gizlenmesi düzeltildi.
- [x] Shikimori puan ve liste-durumu dağılımları ile türetilen oy/üye sayıları istatistiklere bağlandı.
- [x] Manga harici bağlantıları etkinleştirildi; yayın platformları ve diğer bağlantılar ayrıldı.
- [x] Vikipedi bağlantılarına dil etiketi eklendi; tekrar eden bağlantılar tekilleştirildi.
- [x] API arama sonucu ve liste detayındaki Bilgi kartları ortak `EntryInfoSection` bileşeninde birleştirildi.
- [x] Shikimori forum konuları inceleme yerine forum akışına taşındı; konu yanıtları doğru `/api/comments` kimliğiyle yüklendi.
- [x] Eski eksik Room kayıtlarını yenilemek için Shikimori detay önbelleği sürümlendirildi.
- [x] Türkçe/İngilizce metinler ve iki dilli süre biçimi eklendi.

## 3. Değişen dosyalar

| Dosya | Değişiklik |
|---|---|
| `app/src/main/java/com/kitsugi/animelist/data/remote/ShikimoriDetailParser.kt` | Yeni saf detay, başlık, sezon, sonraki bölüm, bağlantı ve istatistik ayrıştırıcısı. |
| `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiShikimoriClient.kt` | Yeni alanların ortak detay modeline bağlanması; anime/manga bağlantıları ve iki dilli süre. |
| `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiModels.kt` | Ek Shikimori detay alanları ve yerel istatistik modeli. |
| `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiDetailClient.kt` | Shikimori kalıcı detay önbelleği için yeni sürüm ve eski kayıt fallback engeli. |
| `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiMediaSocialClient.kt` | Yerel istatistik önceliği; Shikimori forum konuları ve yanıtları; kimlik alanı düzeltmeleri. |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailInfoSection.kt` | Yeni Bilgi satırları, gerçek medya türüne göre görünürlük ve dinamik istatistik kartı. |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailComponents.kt` | API sonucu Bilgi kartının ortak bileşene yönlendirilmesi. |
| `app/src/main/java/com/kitsugi/animelist/utils/KitsugiTranslations.kt` | TV Special format çevirisi. |
| `app/src/main/res/values/strings.xml` | Yeni Türkçe alan ve bağlantı metinleri. |
| `app/src/main/res/values-en/strings.xml` | Yeni İngilizce alan ve bağlantı metinleri. |
| `app/src/test/java/com/kitsugi/animelist/data/remote/ShikimoriDetailParserTest.kt` | Anime/manga, başlık, istatistik, sonraki bölüm ve bağlantı testleri. |
| `PLAN_TASK_SHIKIMORI_BILGI_DENETIMI_2026-10-22.md` | Bu plan ve görev özeti. |

## 4. Doğrulama

- `ShikimoriDetailParserTest`: **7 test başarılı**.
  - Anime detay alanları ve `[null]` başlık temizliği.
  - Rusça liste durumları ve yeniden izleme sayıları.
  - İngilizce manga liste durumları ve yeniden okuma sayıları.
  - Manga cilt/yayıncı alanları ve anime alanlarının mangaya uygulanmaması.
  - Yayınlanan bölüm bilinmiyorsa bölüm numarası uydurulmaması.
  - Sıfırdan ibaret puan kovalarının gerçek dağılım sayılmaması.
  - Vikipedi dil etiketleri ve yayın/harici bağlantı ayrımı.
- TR ve EN `strings.xml` dosyaları XML ayrıştırmasından geçti; yinelenen string adı bulunmadı.
- `git diff --check` temiz tamamlandı.

## 5. Ortam sınırlaması ve kalan doğrulama

Tam Android Gradle derlemesi bu çalışma ortamında çalıştırılamadı: Android SDK bulunmuyor ve Gradle dağıtımının indirildiği adres ağ politikası tarafından engelleniyor. Ayrıştırıcı, gerçek kaynak ve test dosyaları bağımsız JVM doğrulama düzeneğiyle derlenip çalıştırıldı.

- [ ] Android SDK ve Gradle erişimi olan ortamda `:app:testDebugUnitTest` ve uygulama derlemesini çalıştır.
- [ ] Cihazda anime, film ve manga Shikimori detaylarını; Bilgi, Grafikler, bağlantılar ve forum sekmeleriyle doğrula.
