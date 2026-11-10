# Plan / Task — Bangumi +18 görsellerinde bulanıklık düzeltmesi

**Tarih:** 2026-10-22  
**Durum:** Uygulandı; Gradle unit testleri başarıyla çalıştırıldı ve geçti.  
**Kapsam:** Bangumi Listem kartlarında yetişkin içerik görsellerinin, +18 bulanıklık ayarı açıkken bulanık gösterilmesi.

## Kullanıcı bildirimi

Bangumi kaynağında +18 görsellerini bulanıklaştırma ayarı açık olmasına rağmen Listem'deki bazı yetişkin içeriklerin kapakları bulanık görünmüyordu.

## Kök neden

Bangumi'nin kullanıcı koleksiyonu yanıtındaki kısaltılmış `subject` nesnesi `nsfw` alanını taşımıyor. `BangumiImportManager` kayıtları bu nedenle `isAdult=false` olarak içe aktarıyordu. Liste kartı `isAdult` değerini `KitsugiNsfwImage` bileşenine iletiyor; dolayısıyla ayar açık olsa da yetişkin olduğu işaretlenmemiş görsel blur almıyordu.

## Yapılan değişiklikler

- `app/src/main/java/com/kitsugi/animelist/data/auth/BangumiAdultResolver.kt`
  - Bangumi `GET /v0/subjects/{id}` ayrıntı yanıtındaki `nsfw` değerini çözümler.
  - Başarılı pozitif/negatif sonuçları sınırlı bellek önbelleğinde tutar; ağ hatalarını önbelleğe almaz.
- `app/src/main/java/com/kitsugi/animelist/data/auth/BangumiImportManager.kt`
  - İçe aktarmada yetişkin bayrağı çözümlemesini varsayılan olarak açar.
  - Desteklenen subject türlerini ve benzersiz ID'leri sorgular; maliyet için çağrı başına 60 kayıt sınırı korunur.
- `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/AdultFlagBackfillMigration.kt`
  - Göç sürümü 2'ye yükseltildi; mevcut yerel Bangumi kayıtları da otomatik taranır.
  - Bangumi stable ID'si gerçek `subject_id` değerine çevrilir.
  - Her turda en fazla 60 benzersiz Bangumi kaydı sorgulanır; çözülemeyen ağ istekleri tekrar denenebilir.
- `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListViewModel.kt`
  - Artalan onarımının Bangumi subject ayrıntılarını da kapsadığı belgelendi.
- `app/src/test/java/com/kitsugi/animelist/ui/screens/mylist/AdultFlagBackfillMigrationTest.kt`
  - Stable ID ve eski ham Bangumi ID eşlemesi ile farklı sağlayıcı kimlik alanlarının karışmaması test edildi.

## Kabul ölçütleri

- +18 blur açıkken, yetişkin olduğu Bangumi `nsfw` alanıyla doğrulanan Listem afişleri bulanık görünmeli.
- Yetişkin olmayan Bangumi afişleri bulanıklaşmamalı.
- Eski yerel kayıtlar uygulama güncellemesinden sonra taranmalı; yeni kayıtlar içe aktarımda işaretlenmeli.
- Bangumi, MAL ve diğer sağlayıcıların ID alanları birbirine karıştırılmamalı.

## Doğrulama / kalan iş

- `git diff --check`: başarılı.
- `./gradlew testFossDebugUnitTest --tests 'com.kitsugi.animelist.ui.screens.mylist.AdultFlagBackfillMigrationTest'`: başarılı (9 testin tamamı geçti).
- Cihaz/CI doğrulaması: Bangumi oturumu bağlıyken +18 bulanıklığını aç; yetişkin bir içerik ve normal bir içerik kartını Listem'de karşılaştır.
