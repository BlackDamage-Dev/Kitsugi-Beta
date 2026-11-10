# Plan / Task — Kitsugi Hesabı Eklenti Eşitleme ve Geri Yükleme

**Tarih:** 25 Ekim 2026  
**Dal:** `arena/f687912c-kitsugi-beta`  
**Kapsam:** Yüklü eklenti listelerinin Kitsugi hesabıyla eşitlenmesi ve yeni cihazda güvenli biçimde geri yüklenmesi.

## Amaç

Kullanıcı Kitsugi hesabına giriş yaptığında Stremio, CloudStream ve Manga eklentilerine ait ayar/tanımları mevcut uçtan uca şifreli kasayla eşitlemek; yerel kurulum dosyalarını buluta yüklemeden, güvenli ve kullanıcı onaylı geri yükleme sunmak.

## Yapılanlar

- [x] `accountAddonSyncEnabled` ayarı, DataStore anahtarı ve varsayılanı eklendi; hesap ekranında eklenti eşitlemesi ayrı açılıp kapatılabiliyor.
- [x] Stremio eklenti tanımları, CloudStream repo/eklenti tanımları ve Manga repo/eklenti bilgileri mevcut şifreli `linked_accounts_vault` payload'ına eklendi. Yeni Supabase tablo/RPC/migration gerekmedi.
- [x] Her eklenti kaydı bağımsız merge grubu oldu. Eklenti, bağlı hesap, arama ve bildirim bölümlerinin başarısı/hatasi ayrı raporlanıyor; kapalı kategori mevcut bulut verisini koruyor.
- [x] Room, manga repo tercihleri/sağlık durumu ve eklenti dosya değişiklikleri otomatik yedek işini tetikliyor.
- [x] Mevcut cihazda bulunan güvenli metadata geri yükleniyor; CloudStream/Manga çalıştırılabilir dosyaları kasaya veya buluta eklenmiyor.
- [x] Eksik eklentiler için yerel bekleyen geri yükleme planı tutuluyor. Dosya indirme/yükleme, hesap ekranındaki açık kullanıcı onayıyla başlıyor.
- [x] Depo güveni başka cihaza taşınmıyor. Bilinmeyen kaynakları güvenilenlere eklemek ayrı onay gerektiriyor; CloudStream kaynak kontrolü ve Manga APK imza güveni korunuyor.
- [x] Otomatik metadata uygulaması fingerprint ile idempotent yapıldı; gereksiz Room observer/WorkManager döngüsü önleniyor.
- [x] Merge testleri ve bağımsız ADDONS rapor testi eklendi.

## Değişen dosyalar

- `app/src/main/java/com/kitsugi/animelist/data/account/AddonVaultBackup.kt` (yeni)
- `app/src/main/java/com/kitsugi/animelist/data/account/AccountSyncReport.kt`
- `app/src/main/java/com/kitsugi/animelist/data/account/KitsugiAccountRepository.kt`
- `app/src/main/java/com/kitsugi/animelist/data/account/LinkedAccountVault.kt`
- `app/src/main/java/com/kitsugi/animelist/data/account/VaultBackupWorker.kt`
- `app/src/main/java/com/kitsugi/animelist/data/account/VaultMerge.kt`
- `app/src/main/java/com/kitsugi/animelist/data/settings/AppSettings.kt`
- `app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/account/KitsugiAccountContent.kt`
- `app/src/main/res/values/account_strings.xml`
- `app/src/main/res/values-en/account_strings.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`
- `app/src/test/java/com/kitsugi/animelist/data/account/AccountSyncReportTest.kt`
- `app/src/test/java/com/kitsugi/animelist/data/account/VaultMergeTest.kt`

## Doğrulama durumu

- `git diff --check`: geçti.
- Değiştirilen Android string XML dosyalarının XML parse kontrolü: geçti.
- `./gradlew :app:compileDebugKotlin` denendi; sandbox'ta `java`/JDK ve `JAVA_HOME` bulunmadığından Gradle başlamadan hata verdi. Bu nedenle Kotlin derlemesi ve testler çalıştırılamadı; başarılı oldukları iddia edilmiyor.

## Güvenlik ve teslim notu

- Mevcut Supabase şeması/izinleri değiştirilmedi; eklenti metadata'sı mevcut kasa anahtarıyla AES-GCM şifrelenmiş yükün içinde kalır.
- Eklenti APK/CS3/MEX dosyaları, tokenlar veya şifreler ZIP'e dahil edilmez. ZIP yalnızca değişen kaynak/test/çeviri dosyalarının tam içeriklerini ve bu planı taşır.
- Bu belge, ZIP içindeki kaynak değişiklikleriyle beraber verilmek üzere oluşturulmuştur.
