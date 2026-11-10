# PLAN / TASK — MDBList Detay Sayfası Puanları Fix

**Tarih:** 2026-10-25  
**Branch:** `arena/992f382f-kitsugi-beta`  
**Kod commit'i:** `ced6caa` (`fix(detail): MDBList puan kimligi ve olceklerini duzelt`)  
**Durum:** Kod tamamlandı; cihaz ve kullanıcı testleri kullanıcıya bırakıldı.

## 1. Kullanıcı raporu

MDBList entegrasyonu ve puan servisleri ayarlarda açık olmasına rağmen AniList detay sayfasında
(örnek: **Attack on Titan**) `İstatistikler` ile `Türler` arasındaki **Harici Puanlar** kartı
oluşmuyor; IMDb, Rotten Tomatoes, Metacritic, Letterboxd, TMDB ve Trakt puanları görünmüyordu.

## 2. Kök nedenler

1. **AniList MAL kimliği kaybediliyordu:** MAL eşleşmesi bilinen AniList sonuçları `malId`
   alanında gerçek MAL ID taşır. MDBList yolu bu değeri AniList kaynağında koşulsuz `null`
   yapıyordu. IMDb çözümü kimliksiz başladığı için MDBList isteği atılmıyor ve kart gizleniyordu.
2. **Detay/puan yarışı:** MDBList isteği birincil detayla aynı anda başlıyor; detaydaki IMDb,
   TMDB ve gerçek MAL kimlikleri henüz hazır olmadan sonuçsuz tamamlanabiliyordu.
3. **Çözülen TMDB kimliği kullanılmıyordu:** Kimlik çözücü IMDb bulamasa bile TMDB döndürdüğünde
   bu değer MDBList yedeğine aktarılmıyordu.
4. **Yanıt varyantları:** Gerçek MDBList yüklerindeki TMDB 0–100 değeri, Trakt yüzdesi ve
   Rotten Tomatoes seyirci puanının `popcorn` takma adı eksik/yanlış yorumlanabiliyordu.

## 3. Uygulanan çözüm

- Yeni `MdbListMediaIds.kt` ile kaynak kimlikleri kendi ad alanlarında güvenli biçimde çözülüyor:
  MAL, AniList, TMDB, Kitsu, Bangumi, Shikimori ve Simkl kimlikleri birbirine karıştırılmıyor.
- AniList sonucundaki gerçek MAL ID artık korunuyor; offset'li AniList ID ayrıca çözülüyor.
- API sonucu ve kütüphane kaydı detay ViewModel'lerinin ikisinde de MDBList yüklemesi birincil
  detay state'i yazıldıktan sonra başlatılıyor.
- Kimlik çözücünün döndürdüğü TMDB ID, IMDb yoksa MDBList yedek sorgusunda kullanılıyor.
- Yeni detaya geçişte eski puan/yükleme state'i temizleniyor.
- MDBList ayrıştırıcısı TMDB değerini 0–10 ölçeğine normalleştiriyor, Trakt değerini yüzde olarak
  koruyor ve `popcorn` seyirci puanını `tomatoesAudience` alanına bağlıyor.

## 4. Değişen dosyalar

1. `app/src/main/java/com/kitsugi/animelist/data/remote/MdbListClient.kt`
2. `app/src/main/java/com/kitsugi/animelist/data/remote/MdbListMediaIds.kt` **(yeni)**
3. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailViewModel.kt`
4. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/MediaEntryDetailViewModel.kt`
5. `app/src/test/java/com/kitsugi/animelist/data/remote/MdbListClientTest.kt`
6. `PLAN_TASK_MDBLIST_DETAY_PUAN_FIX_2026-10-25.md` **(bu rapor)**

## 5. Test kapsamı ve doğrulama durumu

Eklenen/güncellenen birim testleri:

- Gerçek MDBList yanıt yapısındaki `value`/`score` alanları ve kaynak ölçekleri.
- `popcorn` → Rotten Tomatoes Audience eşlemesi.
- Attack on Titan örneğindeki gibi gerçek MAL ID taşıyan AniList sonucunun korunması.
- AniList, TMDB ve Kitsu kimlik ad alanlarının birbirine karışmaması.

Bu turda `git diff --check` temiz tamamlandı. Kullanıcının talimatıyla derleme/birim testlerinin
sonucu beklenmedi; cihaz ve kullanıcı doğrulamaları kullanıcıya bırakıldı.

## 6. Cihazda önerilen kontrol

1. MDBList anahtarını ve istenen servisleri açık tut.
2. AniList kaynağından **Attack on Titan** detayını aç.
3. `İstatistikler` altındaki **Harici Puanlar** kartında seçili servislerin puanlarını doğrula.
4. Aynı kontrolü kütüphane/Listem üzerinden açılan detayda da tekrarla.
5. Puanı olmayan bir yapımda kartın boş `0` rozetleri üretmeden gizlendiğini kontrol et.

## 7. ZIP içeriği

ZIP, yukarıdaki beş uygulama/test dosyasını özgün klasör yollarıyla ve bu PLAN/TASK dosyasını
kök dizinde içerir. Derleme çıktısı, API anahtarı, kullanıcı verisi veya geçici dosya içermez.
