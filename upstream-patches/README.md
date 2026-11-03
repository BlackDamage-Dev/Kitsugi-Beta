# Anizm eklentisi: kaynak düzeltmesi

`anizm-v20-to-v21.patch`, **ayrı** `f26901964-eng/Kitsugi-Plugins` deposundaki Anizm kaynakları içindir. Temel sürüm: `main` commit `e6b74840d7361dd035d0d7b846bfe188fd6b7354` (Anizm v20). Yama temiz temel dosyalara uygulanabilirliği açısından kontrol edildi.

Bu dosyanın Kitsugi uygulamasında bulunması eklentiyi **kurmaz veya yayımlamaz**. Eklenti deposuna uygulanıp derlenmesi, `builds` manifestine yayımlanması ve cihazda güncellenmesi gerekir. Uygulamanın yedek manifesti mevcut yayımlanmış v20'ye güncellenmiştir; bu, v21 yamasının uygulanmış olduğu anlamına gelmez.

Eklenti deposunun kökünden, bu dosyanın yerel yolu verilerek:

```sh
git apply --check /Kitsugi-Beta/yolu/upstream-patches/anizm-v20-to-v21.patch
git apply /Kitsugi-Beta/yolu/upstream-patches/anizm-v20-to-v21.patch
./gradlew :Anizm:testDebugUnitTest :Anizm:make makePluginsJson
```

Depo zamanla değişirse yamayı güncel kaynaklara uyarlayın; dağıtımda oluşan `.cs3` ve manifestin sürüm/adreslerini kontrol edin. Test ve derleme için eklentinin mevcut Android/Gradle ortamı gereklidir.

## İçerik

- YourUpload ve diğer extractor çıktılarının kendi Referer'ı, başlıkları, türü, sesi ve ek verisi korunur.
- Aincrad/AnizmPlayer'ın imzalı HLS master listesi varyantlara ayrılmadan sunulur; DASH türü de korunur.
- Google Drive indirme URL'sinin sorgusu korunur, gerçek onay formu işlenir ve kota/HTML yanıtları video diye sunulmaz. Drive yanıtları sınırlı okunur; video dosyasının tamamı metne çevrilmez.
- Sürüm `20` → `21`; odaklı politika ve form testleri eklidir.

## Doğrulama sınırları

Bu ortamda Android SDK/Gradle ve canlı Anizm/video barındırıcılarına erişim yoktu. Eklentinin Kotlin kaynakları, paketli CloudStream API sınıfları ve ağ/Android yer tutucularıyla derleme kontrolünden geçirildi; beş politika/form testi geçti. Ek üç davranış testi CloudStream yer tutucularıyla çalıştırıldı; bu, gerçek `.cs3` paketlemesi veya cihaz testi değildir. Sunucunun döndürdüğü gerçek “video kullanılamıyor” klibi uygulama veya eklenti tarafından onarılamaz.
---

# DiziBox eklentisi: Android ICU Regex Çökme Düzeltmesi

`Kitsugi-Plugins-DiziBox-Android-ICU.patch`, **ayrı** `f26901964-eng/Kitsugi-Plugins` deposundaki DiziBox eklentisi içindir.
Temel commit: `8cb8651baa00257b1423f295fb684c3f8bd6d74e`.

### Çökme Nedeni ve Düzeltme:
DiziBox `DiziBoxPlayerParser` sınıfı başlatılırken `\{[^{}]*}` deseni Android ICU regex motorunda `PatternSyntaxException` oluşturur. Bu durum statik başlatmada `ExceptionInInitializerError` ve ardından sınıfa her erişildiğinde `NoClassDefFoundError` fırlatılmasına yol açar.
Yama, kapatma parantezini `\{[^{}]*\}` şeklinde kaçış karakteriyle koruyarak Android ICU uyumluluğunu sağlar.

Eklenti deposunun kökünden uygulama:
```sh
git apply --check /Kitsugi-Beta/yolu/upstream-patches/Kitsugi-Plugins-DiziBox-Android-ICU.patch
git apply /Kitsugi-Beta/yolu/upstream-patches/Kitsugi-Plugins-DiziBox-Android-ICU.patch
./gradlew :DiziBox:make makePluginsJson
```
