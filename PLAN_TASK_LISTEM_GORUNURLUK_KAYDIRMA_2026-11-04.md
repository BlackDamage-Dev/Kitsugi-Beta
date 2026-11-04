# Plan / Task: Listem kartlarının görünürlüğü ve kaydırma

## Kullanıcı tarafından bildirilen sorun
- Listem'de sonuç sayısı (ör. 844) görünürken içerik kartları görünmüyor.
- Kaydırma sırasında öğeler anlık görünüp yeniden kaybolabiliyor.
- Ekran görüntüsünde ayrıca bir Kitsu değişikliğinin gönderilemediği uyarısı bulunuyor.

## Bu pakette yapılan değişiklikler
- [x] Izgara satırlarında kartların genişliklerine göre doğal yükseklikle ölçülmesi; tüm satırı `IntrinsicSize.Min` ve kartları `fillMaxHeight` ile ölçme yaklaşımının kaldırılması.
- [x] Pager'ın komşu sayfalarında filtrelenmiş ve sıralanmış sayfaya özgü verilerin kullanılması.
- [x] Hesap bağlantısı yokken de mevcut yerel kayıtların gösterilmesi ve yeniden bağlanma düğmesinin sunulması.
- [x] Kaydedilmiş kaydırma konumunun yalnızca aktif sayfada, görünür öğeler varken geri yüklenmesi ve boş filtrelenmiş sayfaların kaydedilmiş konumu ezmesinin önlenmesi.
- [x] Birleşik liste önbelleği yenilenirken yetişkin içerik ayarının gösterilen öğelere ve sayaçlara uygulanması.
- [x] Android Compose testleri: ızgara ve düz görünüm, yerel veriler, filtrelenmiş boş sonuç ve 844 öğelik listede ileri-geri kaydırma senaryoları.

## Önemli durum / doğrulama
- [ ] Android cihaz/emülatörde testleri çalıştır. Bu çalışma ortamında Java ve Android SDK bulunmadığı için Gradle ve cihaz testleri çalıştırılamadı.
- [ ] Kullanıcının aynı cihazında yatay/dikey görünümde, 844+ kayıtla, aşağı-yukarı kaydırma ve uygulamadan dönme senaryolarını doğrula.
- [ ] Kullanıcının sürümünü ve ekran görüntüsündeki uygulama koduyla bu çalışma ağacının eşleşmesini doğrula. Görseldeki “Arka planda ne oluyor?” bandı bu checkout'ta bulunamadı.
- [ ] Kaydırırken kartların kaybolmasının gerçek kök nedenini cihaz testi/ekran kaydı ve gerekirse logcat ile doğrula. Yukarıdaki değişiklikler bu semptomun çözüldüğünü henüz kanıtlamaz.
- [ ] Ekrandaki Kitsu bekleyen yazma hatasını ayrı olarak tanıla; bu paket Kitsu senkronizasyon hatasını çözmez.

## Dosyalar
- `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListComponents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListGridContent.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListScreen.kt`
- `app/src/androidTest/java/com/kitsugi/animelist/ui/screens/mylist/MyListContentPageTest.kt`

## Test komutları (Java, Android SDK ve bağlı cihaz/emülatör gerektirir)
```sh
./gradlew :app:testDebugUnitTest --tests 'com.kitsugi.animelist.ui.screens.mylist.MyListFilterHelpersTest'
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kitsugi.animelist.ui.screens.mylist.MyListContentPageTest
```

Bu paket kaynak ve test dosyalarıdır; derlenmiş APK değildir.
