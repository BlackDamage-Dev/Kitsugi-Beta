# ÇÖZÜM RAPORU — Altı Kaynak Arası Eşitleme "değişiklik yok" Hatası

**Tarih:** 2026-10-23
**Taban sürüm:** v2.4.354 (kullanıcının kurulu yapısı)
**Dokunulan dosyalar:**
- `data/auth/RemoteListPresence.kt` (yeni)
- `data/auth/ExternalListSyncManager.kt`
- `data/local/MediaEntryRepository.kt`
- `data/auth/ExternalAuthManager.kt`
- `test/.../data/auth/SyncWritePlanTest.kt` (yeni)

---

## 1. Şikâyet (kanıt)

Kaiju No. 8 detay sayfasında Kitsu satırı (Tamamlandı · 12 bölüm) kaydedildikten sonra
üst bilgi şeridi:

```
AniList ve MyAnimeList başarıyla eşitlendi • AniList: eşitlendi, MyAnimeList: eşitlendi,
Simkl: değişiklik yok, Kitsu: değişiklik yok, Shikimori: değişiklik yok, Bangumi: değişiklik yok
```

Yani AniList/MAL dışındaki dört kaynağa **hiç yazılmıyor**, kullanıcı bu kaynaklarda
kaydın hiç oluşmadığını görüyor.

## 2. Kök neden

`ExternalListSyncManager` v2.4.34x'ten itibaren **alan bazlı** yazıyor
(`SyncFieldDiff`): yalnızca yerelde değişen alanlar hedefe gider. Doğru bir fikir
(hedefte elle girilmiş puan/not ezilmesin), ancak uygulaması eksikti:

```kotlin
val fields = run.fieldsFor("simkl")
if (fields.isEmpty()) return SourceResult(SIMKL, OUTCOME_UP_TO_DATE)   // "değişiklik yok"
```

Bu kapı **hedefte kaydın var olup olmadığına bakmıyordu**. Yani:

- Kullanıcı yalnız `FAVORITE` değiştirdi (AniList/MAL destekler; Simkl/Kitsu/Shikimori/Bangumi
  desteklemez) → bu dört kaynak için `fields` boş → **istek atılmadı**.
- Kayıt bu kaynaklarda **yoksa** (ör. Kitsu'dan içe aktarılmış satır) hiç oluşmadı.
- Varsa bile kısmi yazım yarım kayıt bırakabiliyordu (Simkl'de puan var, liste kaydı yok →
  kullanıcı Simkl'de hiçbir şey görmüyor).
- `MediaEntryRepository.update()` ayrıca "hiçbir alan değişmediyse ağa hiç çıkma" diyordu;
  bu yüzden eksik kayıtlar **hiçbir zaman** onarılmıyordu.

Yani "değişiklik yok" mesajı bir *durum tespiti* değil, bir *hiç denememe* idi.

## 3. Çözüm

### 3.1 Varlık kaydı (`RemoteListPresence`)
Her (yapıt, sağlayıcı) çifti için "hedefte kayıt VAR" bilgisi kalıcı tutulur.
Anahtar yapıtın **kanonik kimliğinden** üretilir (MAL → AniList → Kitsu → Shikimori →
Bangumi → Simkl → TMDB); aynı yapının farklı kaynak satırları aynı kaydı paylaşır.

### 3.2 Yazım planı (`planSyncWrite`)
| Hedefte kayıt | Yerel fark | Sonuç |
|---|---|---|
| VAR | boş | istek yok → **"zaten eşit"** |
| VAR | dolu | yalnız değişen alanlar (`fullWrite = false`) |
| YOK / doğrulanmadı | herhangi | **tam yazım** (`fullWrite = true`, boş alanlar gönderilmez) |

Altı yöneticinin tamamı tam yazımda **upsert/create** yapar:
MAL `PATCH my_list_status`, Kitsu `library-entries`, Shikimori `POST/PATCH user_rates`,
Bangumi `upsertCollection`, AniList `SaveMediaListEntry`, Simkl `add-to-list`.
Bu yüzden "tam yazım" gerçekten kaydı oluşturur; kısmi yazımın aksine yarım kayıt bırakmaz.

### 3.3 Onarım turu (`MediaEntryRepository.update`)
Hiçbir alan değişmese bile, aynı satır için **10 dakikada en fazla bir kez** tam yazım
onarımı çalışır. Böylece "veriler diğer kaynaklarda var ama eşitlenmiyor" durumu
kendiliğinden düzelir; her kaydırmada ağa çıkılmaz.

### 3.4 Dürüst rapor
`OUTCOME_UP_TO_DATE` etiketi **"değişiklik yok" → "zaten eşit"** olarak değiştirildi ve
artık yalnızca hedefteki kayıt doğrulandıktan sonra kullanılır. Kullanıcı "bu ne demek"
diye sorduğu mesaj, yazılmamış bir alanın başarısı değil, gerçek bir eşitlik beyanı olur.

### 3.5 Hesap değişimi
`ExternalAuthManager.disconnectAccount` artık varlık kayıtlarını temizler: eski hesabın
kayıtları yeni hesapta yoktur; "zaten eşit" denip eksik kayıt atlanmaz.

## 4. Testler
`SyncWritePlanTest`:
- doğrulanmış varlıkta alan süzmesi korunur,
- varlık yokken tam yazıma yükseltilir,
- boş fark + varlık yok → yine tam yazım,
- boş fark + varlık var → ağa çıkılmaz,
- Kitsu/MAL satırları aynı kanonik anahtarı üretir.

CI bu testleri `--tests 'com.kitsugi.animelist.data.auth.*'` ile koşar.

## 5. Davranış değişikliği notu
İlk kaydetmede (varlık kaydı henüz yokken) hedeflere **tam yazım** gider. Bu, uygulamanın
alan bazlı yazımdan önceki davranışıdır ve kullanıcının beklediği "eşitle" anlamıdır.
Tam yazımda boş yerel alanlar gönderilmediği için hedefteki dolu değerler silinmez.
Başarılı yazımdan sonra o sağlayıcı "VAR" işaretlenir ve sonraki turlarda yeniden alan
süzmesi uygulanır (uygulama dışında yapılmış düzenlemeler artık ezilmez).
