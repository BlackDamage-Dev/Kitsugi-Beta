# Plan / Task — Detay listelerinde otomatik devam gösterimi

Tarih: 2026-10-24
Dal: arena/e9ea8d4e-kitsugi-beta
Temel commit: 3d3242ffb9a99359a7afc4db9bc126a0e511cc48

## İstek
Detay sayfalarının karakter, ekip, öneri ve ilişki listelerinde manuel “Daha Fazla Yükle” yerine aşağı kaydırdıkça devamının otomatik gösterilmesi; aynı tipteki diğer detay listelerinin de kapsanması.

## Uygulama notu
Bu checkout'taki ilgili ortak sekmeler mevcut veriyi Column/forEach ile tek seferde oluşturuyordu. Değişiklik, ViewModel tarafından zaten alınmış verinin ekranda parça parça oluşturulmasını sağlar; yeni bir ağ/API sayfalama mekanizması eklemez ve kaynakların veri alma limitlerini değiştirmez.

## Tamamlanan işler
- [x] Ortak `DetailAutoPagingColumn` bileşeni eklendi.
- [x] İlk 12 kayıt gösterilir; sondan bir önceki satır görünür olduğunda sonraki 12 kayıt otomatik açılır.
- [x] Görünürlük üst kaydırma alanının kırpılmış pencere koordinatlarıyla kontrol edilir.
- [x] Mevcut üst kaydırma düzeni korundu; içine dikey LazyColumn eklenmedi.
- [x] Karakter ve ekip sekmelerinin yatay iki sütunlu görünümü korundu.
- [x] API sonuç detayı ve listedeki medya detayı ortak karakter/ekip/öneri/ilişki sekmelerinden aynı davranışı alır.
- [x] Karakter detayındaki eserler ve seslendirmenler kapsandı.
- [x] Oyuncu/ekip detayındaki karakter rolleri ve eserler kapsandı.
- [x] Seçili olmayan pager sekmelerinde otomatik devam gösterimi engellendi.
- [x] Gösterilen kayıt sayısı rememberSaveable ile saklanır; liste girdisi değiştiğinde sıfırlanır.
- [x] Son parçada liste uzunluğunu aşmayan ve tamsayı taşmasına karşı korunan sayaç eklendi.
- [x] Birim testleri ve Compose cihaz regresyon testleri eklendi.

## Dosyalar
1. app/src/main/java/com/kitsugi/animelist/ui/screens/detail/DetailAutoPagingColumn.kt — yeni ortak bileşen.
2. app/src/main/java/com/kitsugi/animelist/ui/screens/detail/DetailPaging.kt — güvenli parça sayacı.
3. app/src/main/java/com/kitsugi/animelist/ui/screens/detail/CharactersTab.kt — karakter ve ekip sekmeleri.
4. app/src/main/java/com/kitsugi/animelist/ui/screens/detail/RecommendationsTab.kt — öneriler.
5. app/src/main/java/com/kitsugi/animelist/ui/screens/detail/RelationsTab.kt — ilişkiler.
6. app/src/main/java/com/kitsugi/animelist/ui/screens/detail/DetailPageScaffold.kt — aktif sekme kontrolü.
7. app/src/main/java/com/kitsugi/animelist/ui/screens/detail/CharacterDetailComponents.kt — eser/seslendirmen listeleri.
8. app/src/main/java/com/kitsugi/animelist/ui/screens/detail/CharacterDetailPage.kt — karakter detayı aktif sekme kontrolü.
9. app/src/main/java/com/kitsugi/animelist/ui/screens/detail/StaffDetailComponents.kt — rol/eser listeleri.
10. app/src/main/java/com/kitsugi/animelist/ui/screens/detail/StaffDetailPage.kt — ekip detayı aktif sekme kontrolü.
11. app/src/test/java/com/kitsugi/animelist/ui/screens/detail/DetailPagingTest.kt — sayaç birim testleri.
12. app/src/androidTest/java/com/kitsugi/animelist/ui/screens/detail/DetailAutoPagingColumnTest.kt — kaydırma/yerleşim cihaz testleri.
13. PLAN_TASK_DETAY_OTOMATIK_KAYDIRMA_2026-10-24.md — bu plan/task belgesi.

## Kontroller ve sınırlar
- [x] `git diff --check` geçti.
- [x] Değişen/yeni 12 Kotlin dosyası tree-sitter Kotlin ayrıştırıcısıyla sözdizimi kontrolünden geçti. Bu kontrol derleme/tip kontrolü değildir.
- [ ] Gradle birim testleri çalıştırılamadı: ortamda Java/JDK yok; `JAVA_HOME is not set and no 'java' command could be found in your PATH` hatası alındı.
- [ ] Android cihaz/emülatör Compose testleri çalıştırılmadı.
- [ ] APK derlemesi ve gerçek cihaz doğrulaması yapılmadı.

### JDK ve Android SDK bulunan ortamda çalıştırılacak komutlar
```bash
./gradlew :app:testDebugUnitTest --tests 'com.kitsugi.animelist.ui.screens.detail.DetailPagingTest'
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kitsugi.animelist.ui.screens.detail.DetailAutoPagingColumnTest
```

### Cihaz doğrulama listesi
- [ ] Uzun karakter, ekip, öneri ve ilişki listelerinde aşağı kaydırınca devamı gelir.
- [ ] Listenin sonuna ulaşınca tekrar tetikleme veya aynı kartların çoğalması olmaz.
- [ ] Dikey ve yatay görünümlerde kaydırma ve kart tıklamaları çalışır.
- [ ] Sekme değiştirince komşu sekmenin bütün verisi arka planda açılmaz.
- [ ] Karakter ve oyuncu/ekip detaylarındaki eser, rol ve seslendirmen listeleri aynı davranışı gösterir.
- [ ] Ekran dönüşü, veri yenileme ve TV/D-pad kaydırması cihazda doğrulanır.

## ZIP'i uygulama
ZIP içindeki göreli yollar depo köküne göredir. Aynı temel sürümdeki deponun köküne çıkarın; mevcut dosyaların üzerine yazılacaktır. Önce yerel değişikliklerinizi yedekleyin. Paket yalnızca bu çalışmanın 12 Kotlin dosyasını ve bu plan/task belgesini içerir; APK, bağımlılıklar, kimlik bilgileri ve tüm depo dahil değildir.
