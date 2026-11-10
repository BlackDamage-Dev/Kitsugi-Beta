# Plan / Task — Simkl film kayıtlarının Bangumi ile eşleşmemesi (Coraline)

**Tarih:** 2026-10-24  
**Dal:** `arena/44250b6a-kitsugi-beta`  
**Durum:** Kod düzeltmesi ve izole doğrulamalar tamamlandı.

## Kullanıcı bildirimi

Coraline filmi hem Simkl hem Bangumi kataloglarında görünmesine rağmen Simkl kaydı Bangumi ile eşitlenmiyordu. Eşitleme özetinde `Bangumi: kimlik çözülemedi` ve `Bangumi eşleşmesi bulunamadı: Coraline` mesajları görünüyordu.

## Araştırma ve canlı kayıt doğrulaması

Bangumi'nin canlı `GET /v0/subjects/103470` yanıtı kontrol edildi:

- `id`: `103470`
- `name`: `Coraline`
- `name_cn`: `鬼妈妈`
- `type`: `2` (`ANIME`)
- `platform`: `剧场版` (film)
- `date`: `2009-02-06`

Bu kayıt, uygulamanın film aramasını yalnızca `REAL` (`type=6`) içinde yapmasının Coraline'i daha aday filtreleme aşamasında dışarıda bıraktığını doğruladı.

## Kök nedenler

1. **Erken tamamlanma:** Film/dizi kimlik çözümünde Simkl veya TMDB kimliği bulunması yeterli sayılıyor; Bangumi bağlı olsa ve `bangumiSubjectId` boş olsa bile pahalı kimlik araması başlamadan `resolve()` dönüyordu.
2. **Eksik tür kapsamı:** `MediaType.Movie` geriye dönük tek tür eşlemesinde `REAL` kabul ediliyordu. Bangumi'de animasyon filmler `ANIME + 剧场版`, gerçek çekim filmler ise `REAL + 电影` olabildiği için Coraline gibi kayıtlar bulunamıyordu.

## Yapılan düzeltmeler

- Bağlı Bangumi kimliği çözülmeden Simkl/TMDB kimliğinin çözümü "tamamlandı" sayması engellendi.
- Yalnız Bangumi bağlıyken gereksiz Simkl/TMDB şartı aranmaması sağlandı.
- Film aramaları hem `ANIME` hem `REAL` subject türlerini kapsayacak şekilde genişletildi.
- Film adaylarında platform doğrulaması eklendi:
  - `ANIME + 剧场版` kabul edilir.
  - `REAL + 电影` kabul edilir.
  - Anime TV ve gerçek çekim TV adayları film yazımında reddedilir.
- Hem `CrossProviderIdentityResolver` hem `BangumiSyncManager` içindeki yedek kimlik çözüm yolu yeni kuralları kullanacak şekilde güncellendi.
- MAL ↔ Bangumi önbelleğinde film için iki olası tür de okunur; yeni öğrenilen eşleme varsayılan tür yerine sunucudan dönen gerçek subject türüyle yazılır.
- Başlık/yıl ve tek-net-aday güvenlik davranışı korundu; belirsiz eşleşmede rastgele kimlik yazılmaz.

## Değişen üretim ve test dosyaları

1. `app/src/main/java/com/kitsugi/animelist/data/auth/BangumiSyncManager.kt`
2. `app/src/main/java/com/kitsugi/animelist/data/auth/CrossProviderIdentity.kt`
3. `app/src/main/java/com/kitsugi/animelist/data/remote/BangumiIdNamespace.kt`
4. `app/src/test/java/com/kitsugi/animelist/data/auth/CrossProviderIdentityTest.kt`
5. `app/src/test/java/com/kitsugi/animelist/data/remote/BangumiIdNamespaceTest.kt`

Bu plan/task dosyasıyla birlikte ZIP toplam **6 dosya** içerir.

## Görev ve kabul kriterleri

- [x] **T1 — Kök neden analizi:** Erken çıkış ve yalnız `REAL` arama hatası kod üzerinde doğrulandı.
- [x] **T2 — Canlı veri kontrolü:** Coraline'in Bangumi `103470 / ANIME / 剧场版` kaydı doğrulandı.
- [x] **T3 — Kimlik çözümü:** Simkl → Bangumi akışı `103470` sonucunu üretecek şekilde düzeltildi.
- [x] **T4 — Yazma yolu:** Bangumi yedek çözümü de aynı kimliği bulup upsert çağrısına veriyor.
- [x] **T5 — Yanlış eşleşme koruması:** TV adayları film olarak kabul edilmiyor; gerçek çekim film desteği korunuyor.
- [x] **T6 — Regresyon testleri:** Coraline ve bağlı-kaynak tamlık kuralları için testler eklendi.

## Doğrulama kaydı

| Kontrol | Sonuç |
|---|---|
| `git diff --check` | Başarılı |
| `CrossProviderIdentityTest` + `BangumiIdNamespaceTest` | **27 test başarılı** |
| Coraline aday seçimi (`ANIME/TV` ve `REAL/TV` yem adayları arasında) | `103470` seçildi |
| Simkl filmi → çapraz kimlik çözümü | `bangumiSubjectId=103470` |
| Bangumi yedek çözümü → koleksiyon upsert | `subjectId=103470` |
| Mevcut `REAL + 电影` eşleşmesi | Başarılı |
| Yalnız TV adayları bulunan film araması | Güvenli biçimde reddedildi |
| Bağlı olmayan kaynakların tamlık kuralları | Başarılı |

Testler, değiştirilen üretim Kotlin dosyaları izole bir JRE 17 / Kotlin ortamında derlenerek ve kontrollü sahte Bangumi yanıtları kullanılarak çalıştırıldı. Bu çalışma tam Android Gradle/APK derlemesi veya cihaz üzerinde gerçek hesap yazımı değildir.

## Cihazda önerilen son kontrol

1. Simkl ve Bangumi hesapları bağlıyken mevcut Simkl Coraline kaydını yeniden eşitle.
2. Eşitleme özetinde Bangumi'nin `kimlik çözülemedi` olarak atlanmadığını doğrula.
3. Bangumi koleksiyonunda `Coraline` / `鬼妈妈` (`subject_id=103470`) kaydının durum ve puanını kontrol et.
4. Aynı ada sahip farklı bir TV kaydının film yerine seçilmediğini gözlemle.
