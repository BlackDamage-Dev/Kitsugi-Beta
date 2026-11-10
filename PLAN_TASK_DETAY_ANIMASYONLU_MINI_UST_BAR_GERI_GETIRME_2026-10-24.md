# PLAN / TASK — Detay Sayfalarında Animasyonlu Mini Üst Barın Geri Getirilmesi

**Tarih:** 2026-10-24  
**Dal:** `arena/9745db89-kitsugi-beta`  
**Sürüm:** 2.4.340 → **2.4.341**  
**Durum:** Uygulandı

## 1. Kullanıcı isteği

Detay sayfasında aşağı kaydırınca, yatay sekme bandının üzerinde daha önce bulunan kompakt üst bar geri getirilecek. Bar; geri, yapım başlığı, galeri, paylaş ve favori aksiyonlarını gösterecek, yumuşak animasyonla açılıp kapanacak ve tüm detay sayfalarında çalışacak.

## 2. Kök neden

`DetailPageScaffold.kt` içindeki mini üst barın görünürlüğü `showAtIndex = 3` ve `hideAtIndex = 1` değerlerine bağlanmıştı.

Dikey medya detayının gerçek `LazyColumn` yapısı ise yalnızca şu üç öğeden oluşuyor:

| İndeks | İçerik |
|---:|---|
| 0 | Vitrin / hero ve hızlı aksiyonlar |
| 1 | Yapışkan sekme bandı |
| 2 | Sekme içeriklerini taşıyan pager |

Sayfada `3.` öğe bulunmadığı için `KitsugiStickyHeaderReveal` ve `floatingHeaderActions` kodları mevcut olmasına rağmen mini bar hiç tetiklenmiyordu. Başka bir deyişle aksiyonlar silinmemiş, görünürlük eşiği erişilemez hâle gelmişti.

## 3. Uygulanan çözüm

### Ortak görünürlük politikası

`rememberDetailStickyHeaderVisible` için standart detay düzenine uygun ortak varsayılanlar tanımlandı:

- **Göster:** `firstVisibleItemIndex >= 2`
- **Gizle:** `firstVisibleItemIndex <= 0`
- **Ara durum:** Önceki görünürlüğü koru (histerezis).

Böylece bar sekme içeriğine geçildiğinde açılır, vitrine dönüldüğünde kapanır ve yapışkan sekme sınırında titremez.

### Animasyon ve aksiyonlar

Mevcut `KitsugiStickyHeaderReveal` animasyonu korundu:

- dikey açılma / kapanma,
- yukarı-aşağı kayma,
- solma,
- ortak `KitsugiMotion` süre ve easing token'ları.

Mini bar içindeki mevcut aksiyonlar korundu:

- geri,
- yapım / kişi başlığı,
- galeri,
- paylaş,
- favori (ayar ve kaynak destekliyorsa).

### Kapsam

- `ApiResultDetailPage`: arama ve keşfetten açılan medya detayları.
- `MediaEntryDetailPage`: Listem ve kayıtlı koleksiyonlardan açılan medya detayları.
- `CharacterDetailPage`: karakter detayları aynı ortak görünürlük politikasını kullanır.
- `StaffDetailPage`: ekip / seslendiren detayları aynı ortak görünürlük politikasını kullanır.
- `StudioDetailPage`: grid/liste yapısına özel mevcut animasyonlu üst bar davranışı korunur.

Yatay ekran düzeni ve TV odak davranışı değiştirilmedi.

## 4. Regresyon testleri

`DetailStickyHeaderVisibilityTest.kt` içine beş test eklendi:

1. Standart detayda içerik öğesi (`2`) mini barı açar; olmayan `3.` öğe beklenmez.
2. Yapışkan sekme sınırında açık bar görünür kalır.
3. Vitrine dönüldüğünde bar kapanır.
4. İçerik eşiğine ulaşmadan kapalı bar açılmaz.
5. Derin kaydırma konumu geri yüklendiğinde bar doğru başlangıç durumunu alır.

Test edilebilirliği artırmak için görünürlük geçişi saf `resolveDetailStickyHeaderVisibility` fonksiyonuna ayrıldı.

## 5. Değişen dosyalar

| Dosya | Değişiklik |
|---|---|
| `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiAnimations.kt` | Ortak varsayılan eşikler ve saf görünürlük geçiş fonksiyonu |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/DetailPageScaffold.kt` | Erişilemeyen `3 / 1` eşikleri kaldırıldı; ortak `2 / 0` politikasına geçildi |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/CharacterDetailPage.kt` | Ortak görünürlük politikasına bağlandı |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/StaffDetailPage.kt` | Ortak görünürlük politikasına bağlandı |
| `app/src/test/java/com/kitsugi/animelist/ui/components/DetailStickyHeaderVisibilityTest.kt` | Beş regresyon testi eklendi |
| `app/build.gradle.kts` | Sürüm **2.4.341** |
| `release_notes_v2.4.341.md` | Türkçe / İngilizce sürüm notları |
| `RELEASE_NOTES.md` | Yeni sürüm notu en üste eklendi |
| `PLAN_TASK_DETAY_ANIMASYONLU_MINI_UST_BAR_GERI_GETIRME_2026-10-24.md` | Bu plan / task belgesi |

## 6. Doğrulama

- `git diff --check`: başarılı.
- Statik kontroller: medya scaffold'unda eski `showAtIndex = 3` kalmadığı, ortak eşiklerin kullanıldığı, galeri / paylaş / favori / geri aksiyonlarının korunduğu, sürüm ve sürüm notlarının tutarlı olduğu doğrulandı.
- Beş test kaynak dosyada mevcut ve test sayısı statik olarak doğrulandı.
- Gradle birim testleri bu sandbox'ta **çalıştırılamadı**: `JAVA_HOME` tanımlı değil ve `java` komutu bulunmuyor.
- Cihazda önerilen kontrol: dikey medya detayını sekme içeriğine kadar kaydır, mini barın sekmelerin üstünde animasyonla geldiğini doğrula; vitrine dönerek kapandığını, ardından galeri / paylaş / favori / geri düğmelerini ve farklı sekmeleri dene.

## 7. Paketleme

ZIP, değişen dosyaları ve bu plan / task belgesini depo köküne göre klasör yapısı korunarak içerir. Tam proje veya APK değildir.
