# Plan Task: Dolby Vision yaşam döngüsü, torrent tampon göstergesi ve altyazı varsayılanı

**Tarih:** 2026-10-25  
**Dal:** `arena/690b668d-kitsugi-beta`  
**Durum:** Kod değişiklikleri uygulandı; cihaz doğrulaması bekliyor.

## Amaç

1. Arka plana geçip geri dönüldüğünde Dolby Vision/HDR görüntüsünün bozulmasını önlemek.
2. Torrent tampon yüzdesinin hatalı, sıçrayan ya da sınırsız değerlerden etkilenmesini engellemek.
3. Tam ekran altyazı tipografi boyutunun varsayılan ve sıfırlama değerini 45 yapmak; kullanıcının tercihini saklamak.

## Tespit ve uygulama planı

### Dolby Vision / uygulama yaşam döngüsü

- [x] Tam ekran Media3 oynatıcısında `SurfaceView` tabanlı PlayerView kullan. Bu, Android'in HDR/Dolby Vision çıkışını yönetmesine imkân verir; dizi/fragman oynatıcısının mevcut TextureView'ı değiştirilmez.
- [x] Uygulama görünmez olduğunda oynatmayı duraklat, ancak oynatıcı Activity'sini ve decoder oturumunu kapatma. Kullanıcı geri döndüğünde, yalnızca arka plana geçmeden önce oynuyorsa devam et.
- [x] SurfaceView için asenkron PixelCopy ekran görüntüsü yakalamayı ekle; TextureView yakalama desteği de korunur.
- [ ] Dolby Vision donanımlı cihazda oynatma, arka plana alma/geri dönme, PiP ve ekran görüntüsü akışlarını cihaz üzerinde doğrula.

### Torrent tampon göstergesi

- [x] Motor metriğinin `0..1` kesir veya `0..100` yüzde biçiminde gelmesi durumlarını normalize et.
- [x] NaN, sonsuz, negatif ve beklenmeyen aralık dışı örnekleri reddet; geçersiz örnek geldiğinde son geçerli tampon değerini koru.
- [x] Arayüz değerini yüzde `0..100` aralığında sınırla.
- [x] Normalizasyon, bozuk örnekler ve yüzde gösterimi için birim testleri ekle.
- [ ] Gerçek torrent akışında indirme, seek ve geçici bağlantı kesintisi senaryolarını cihazda doğrula.

### Altyazı tipografi boyutu

- [x] Tipografi font boyutunun model varsayılanını ve paneldeki sıfırlama değerini 45 yap.
- [x] `subtitle_font_size` tercihini DataStore'da sakla; ilk kurulum varsayılanı 45, kayıt aralığı 1–100.
- [x] Varsayılan ve kayıt/okuma davranışı için DataStore testi ekle.

## Değişen dosyalar

- `app/src/main/java/com/kitsugi/animelist/core/p2p/P2pModels.kt`
- `app/src/main/java/com/kitsugi/animelist/core/p2p/P2pStreamingEngine.kt`
- `app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt`
- `app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt`
- `app/src/main/java/com/kitsugi/animelist/data/settings/AppSettings.kt`
- `app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PlayerModels.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/panels/SubtitleSettingsPanel.kt`
- `app/src/main/res/layout/fullscreen_player_view.xml`
- `app/src/test/java/com/kitsugi/animelist/core/p2p/P2pBufferProgressTest.kt`
- `app/src/test/java/com/kitsugi/animelist/data/settings/SettingsDataStoreTest.kt`

## Doğrulama

- `git diff --check`: başarılı.
- `fullscreen_player_view.xml`: XML ayrıştırması başarılı.
- `./gradlew :app:testDebugUnitTest --no-daemon`: bu ortamda çalıştırılamadı; Java/JDK veya `JAVA_HOME` tanımlı değil. Android cihaz doğrulaması da bu görev ortamında yapılamadı.
