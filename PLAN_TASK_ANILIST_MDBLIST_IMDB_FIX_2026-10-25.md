# PLAN TASK — AniList Detay Sayfasında MDBList / IMDb Görünmeme Fix'i

**Tarih:** 2026-10-25
**Branch:** `arena/dd221434-kitsugi-beta`
**Base:** `main@af10c04cd03761949d5291c98d80f968679d0e86`
**Fix commit:** `b98831eeabdaf4c941679a2f31ac26ff543139d5`
**Durum:** ✅ Kod düzeltmesi, hedefli testler ve push tamamlandı

---

## 1) Kullanıcı Raporu

- **Ekran:** Detay sayfası → `Bilgi` sekmesi → `İstatistikler` ile `Türler` arası
- **Örnek yapım:** Attack on Titan (AniList kaynağı)
- **Sorun:** MDBList API anahtarı ve API bağlantısı çalışmasına rağmen `Harici Puanlar` kartı ve `IMDb` puanı görünmüyor.
- **Beklenen:** MDBList'ten geçerli IMDb puanı geldiğinde kart, İstatistikler'in altında görünmeli.

## 2) Uçtan Uca Kontrol

Kontrol edilen zincir:

`AniList sonucu / detay kimlikleri → KitsugiIdResolver → MdbListClient → StateFlow → Bilgi sekmesi → ApiMdbListRatingCard → IMDb rozeti`

- `ApiResultDetailPage` ve `MediaEntryDetailPage`, MDBList state'lerini Bilgi sekmesine doğru aktarıyor.
- `ApiDetailTabContents` ve `EntryDetailTabContents`, `ApiMdbListRatingCard` bileşenini doğru konumda çağırıyor.
- `mdbListShowImdb` varsayılanı `true`; IMDb rozeti geçerli (`> 0`) puan geldiğinde oluşturuluyor.
- `MdbListClient` mevcut dizi ve eski alan biçimlerindeki API yanıtlarını ayrıştırabiliyor.
- Dolayısıyla ana hata görünümde değil, **API isteğinden önceki kimlik çözümleme adımında**.

## 3) Kök Neden

`ApiResultDetailViewModel.fetchMdbListRatings()` içinde AniList kaynağı için gerçek MAL kimliği koşulsuz olarak `null` yapılıyordu.

Attack on Titan gibi AniList arama sonuçları, `idMal` bilindiğinde `malId` alanında gerçek MAL kimliğini taşır (`16498`). Bu durumda offset'li AniList kimliği bulunmaz; eski kod hem `aniListId` hem de `realMalId` değerini etkin biçimde boşaltıyordu. Detay verisi henüz gelmediyse TMDB kimliği de yoktu ve akış şu dala düşüyordu:

`MDBList skipped: no IMDb or TMDB identity is available`

Bunun sonucunda:

1. MDBList API'sine istek atılmıyordu.
2. `_mdbListRatings` `null` kalıyordu.
3. Puan kartı boş olduğu için UI tarafından gizleniyordu.

### İkincil kimlik kaybı

`KitsugiIdResolver.resolveIds()` bir TMDB kimliği çözdüğünde bile her iki detay ViewModel'i yalnızca `resolved.imdbId` değerini alıyor, `resolved.tmdbId` değerini yok sayıyordu. Bu da ikinci bir gereksiz “kimlik yok” erken çıkışına neden olabiliyordu.

## 4) Uygulanan Çözüm

### A. AniList kimlik biçimlerini güvenli biçimde ayırma

Yeni `resolveMdbListAniListIds()` yardımcı fonksiyonu:

- Gerçek MAL aralığındaki (`1..99_999_999`) stable ID'yi MAL kimliği olarak korur.
- Açıkça çözülmüş `realMalId` değerini korur.
- `100_000_000 + aniListId` biçimindeki stable ID'den gerçek AniList kimliğini çıkarır.
- Bangumi/Shikimori gibi yabancı kimlik ad alanlarının MAL veya AniList kimliği olarak sızmasını engeller.

`ApiResultDetailViewModel`, artık AniList kaynağının MAL kimliğini koşulsuz silmek yerine bu yardımcıyı kullanıyor.

### B. Çözülen TMDB kimliğini kullanma

Her iki detay akışında da:

- IMDb kimliği bulunmadığında `KitsugiIdResolver` çalıştırılır.
- `resolved.imdbId` kullanılır.
- İlk snapshot'ta TMDB yoksa `resolved.tmdbId` de MDBList sorgusuna aktarılır.

Bu değişiklik hem API/arama detayına hem liste kaydı detayına uygulandı.

## 5) Değişen Üretim ve Test Dosyaları

| Dosya | Değişiklik |
|---|---|
| `app/src/main/java/com/kitsugi/animelist/data/remote/MdbListAniListIdentity.kt` | **Yeni:** MAL-backed ve offset'li AniList kimliklerini ayıran ortak yardımcı |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailViewModel.kt` | AniList MAL kimliği kaybı giderildi; çözülen TMDB kimliği MDBList'e bağlandı |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/MediaEntryDetailViewModel.kt` | Çözülen TMDB kimliğinin MDBList fallback'i olarak kullanılması sağlandı |
| `app/src/test/java/com/kitsugi/animelist/data/remote/MdbListAniListIdentityTest.kt` | **Yeni:** 5 kimlik regresyon testi |

**Fix diff özeti:** 4 dosya, 98 ekleme, 11 silme.

## 6) Regresyon Testleri

`MdbListAniListIdentityTest` kapsamı:

1. `malBackedAniListResultKeepsMalIdForRatingLookup` — Attack on Titan örneğinde gerçek MAL kimliği korunur.
2. `malBackedStableIdIsUsedWhenCrossIdHasNotArrivedYet` — `realMalId` henüz gelmediyse MAL-backed stable ID kullanılır.
3. `offsetAniListIdIsDecodedWithoutLeakingIntoMalId` — offset'li AniList ID çözülür, MAL alanına sızmaz.
4. `explicitlyResolvedMalIdIsPreservedAlongsideOffsetAniListId` — bilinen MAL ve AniList kimlikleri birlikte korunur.
5. `foreignNamespaceAndInvalidIdsAreRejected` — yabancı/geçersiz kimlik ad alanları reddedilir.

**Sonuç:** `5/5 passed`

**Doğrulama yöntemi:** Hedefli üretim ve test kaynakları Kotlin/JVM derleyicisiyle derlenip geçici bir JUnit uyumlu çalışma düzeneğiyle çalıştırıldı. `git diff --check` de temizdir. Sandbox'ta Android SDK bulunmadığından tam Android Gradle/APK derlemesi çalıştırılmadı.

## 7) Beklenen Cihaz Davranışı

Yeni derlemeyle:

1. MDBList entegrasyonu ve IMDb rozeti ayarlarda açık olmalı.
2. Attack on Titan detayını aç.
3. `Bilgi` sekmesinde `İstatistikler` kartının altında `Harici Puanlar` kartı görünmeli.
4. API geçerli puan döndürdüğünde sarı vurgulu `IMDb` rozeti ve puanı listede yer almalı.

## 8) ZIP Paket İçeriği

Arşiv, orijinal klasör yapısını koruyarak aşağıdaki **5 dosyayı** içerir:

- Yukarıdaki 4 üretim/test dosyası
- `PLAN_TASK_ANILIST_MDBLIST_IMDB_FIX_2026-10-25.md`

**Arşiv adı:** `Kitsugi_AniList_MDBList_IMDb_Fix_2026-10-25.zip`

## 9) Geri Alma

Kod fix'ini geri almak gerekirse:

```bash
git revert b98831eeabdaf4c941679a2f31ac26ff543139d5
```

Risk düşüktür; değişiklik MDBList kimlik seçimi ve fallback akışıyla sınırlıdır. UI kart tasarımı veya API yanıt ayrıştırma davranışı değiştirilmemiştir.
