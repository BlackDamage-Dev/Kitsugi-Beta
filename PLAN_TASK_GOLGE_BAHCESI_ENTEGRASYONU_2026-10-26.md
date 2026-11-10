# Plan / Task — Kitsugi manga eklenti deposu entegrasyonu

Tarih: 2026-10-26
Dal: `arena/2809aa13-kitsugi-beta`

## Hedef

Kitsugi'nin manga bölümünü, video eklenti depolarındaki gibi repo-merkezli çalıştırmak. Varsayılan kaynak Gölge Bahçesi'ni uygulamaya gömmek değil; kullanıcının `Kitsugi-Manga-Plugins` deposunu varsayılan repo olarak eklemek ve eklentileri depodaki katalogdan tek tek veya toplu kurabilmek. Diğer Türkçe eklenti dosyaları korunur ancak ilk varsayılan profilde kapalı kalır. Eski Kotatsu Gölge Bahçesi parser'ı devre dışıdır.

## Yapılan kaynak kodu değişiklikleri

- [x] `Kitsugi-Manga-Plugins` varsayılan depo olarak uygulama başlatılırken manga repo listesine ekleniyor.
- [x] Depo kök URL'si ve kısa repo adı, üretilen `builds/index.json` kataloğuna normalize ediliyor.
- [x] Repo kataloğu Keiyoushi/Mihon uyumlu JSON veya desteklenen APK-listesi formatından okunuyor.
- [x] Tekil APK kurma, mevcut bulk install akışı ve telefon/tablet UI bağlantıları repo kataloğuna bağlı.
- [x] TV repo ekranının toplu kurulum butonu gerçek manga bulk-install callback'ine bağlandı.
- [x] Katalog yoksa harici bir APK ya da gömülü Gölge Bahçesi girdisi uydurulmuyor; repo UI'da index/çıktı eksikliği açıklanıyor.
- [x] Gölge Bahçesi için uygulama içine eklenmiş yerel kaynak/secure reader kaldırıldı; eklenti yalnızca kullanıcının reposundan kurulur.
- [x] Varsayılan profilde Kotatsu kaynakları kapalı; Kotatsu `GOLGEBAHCESI` parser'ı hariç tutuluyor. Mevcut diğer APK/MEX eklentileri silinmiyor ve ilk geçişte devre dışı kalıyor.
- [x] Repo URL normalize/kimlik testleri eklendi.

## Önemli dağıtım bağımlılığı

İnceleme anında GitHub deposunun `main` dalında kaynak kodlar var; uygulamanın indirebileceği `builds/index.json` ve APK yayın çıktıları görünmüyor. Uygulama varsayılan olarak `https://raw.githubusercontent.com/f26901964-eng/Kitsugi-Manga-Plugins/builds/index.json` adresini kullanacak, alternatif uyumlu indeks yollarını da dener. Repo derleme/yayın iş akışı bu katalog ve APK'ları yayınlamadan uygulama eklenti listeleyemez veya indiremez. Başka bir kaynaktan sessiz fallback yapılmaz.

## Doğrulama durumu

- [ ] Gradle unit testleri: bu ortamda Java/JDK ve `JAVA_HOME` yok, çalıştırılamadı.
- [ ] Android derlemesi / APK üretimi: yapılmadı.
- [ ] Cihazda repo fetch, tekli kurulum, bulk kurulum ve kaynak etkinleştirme testi: yapılmadı.

**Sonuç:** Bu teslim app kaynak kodu değişikliğidir, APK değildir. Repo manifesti/APK'ları ve cihaz akışı yayınlandıktan sonra uçtan uca doğrulanmalıdır.

## Paket içeriği

ZIP, bu plan dosyasını ve değişmiş/yeni depo dosyalarını depo içi göreli yollarla içerir. Tam depo veya APK değildir. `PACKAGE_MANIFEST.json`, paket dosyalarının yolları ve SHA-256 özetlerini verir.
