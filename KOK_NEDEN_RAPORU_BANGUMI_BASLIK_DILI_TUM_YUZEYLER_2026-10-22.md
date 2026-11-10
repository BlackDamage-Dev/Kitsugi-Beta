# Kök Neden Raporu — Bangumi Başlık Dili Sözleşmesi: İlişkiler, Karakterler, Yapımlar ve Kişi/Karakter Adları

**Tarih:** 2026-10-22 · **Sürüm:** v2.4.339 · **Durum:** Düzeltildi (kod + birim testleri)

---

## Kullanıcı bildirimi

> "Bangumi'yi adam akıllı, nerede olursa olsun: ben İngilizce seçmişsem (ya da hangi dili
> seçmişsem) o dile uymalı; verilerin isimlerini, adlarını, bilgilerini o dile ait şekilde
> göstermeli — varsa tabii."

Ekran görüntüleri (22 Eki):
1. **Detay → İlişkiler** (CLANNAD): `CLANNAD もうひとつの世界 智代編`,
   `第6回京都アニメーションファン感謝イベント KYOANI MUSIC FESTIVAL…` — seçili dil İngilizce
   olmasına rağmen tüm ilişkili başlıklar Japonca.
2. **Kişi detayı → Karakterler** (Koorogi Satomi): karakter adları `ニーナ・タッカー`, `清子`,
   rol yapımları `おねがい☆ティーチャー`, `名探偵コナン`, `機動戦士Vガンダム` — Japonca.
3. **Kişi detayı → Yapımlar** (Okazaki Tomoya): `劇場版 CLANNAD`,
   `CLANNAD もうひとつの世界 智代編` — Japonca (Latin kalanlar yalnızca Bangumi özgün adı
   zaten Latin olanlar: `Clannad`, `Kaginado`).

Teşhisin anahtarı 3. ekran görüntüsündedir: Latin görünen satırların tamamı Bangumi'de
**özgün adı zaten Latin** olan kayıtlardır. Yani gösterim (render) katmanı ve dil zinciri
sağlamdır; sorun, CJK kayıtların **Latin ad varyantlarının (`titleRomaji` / `titleEnglish`,
`romanizedName` / `englishName`) hiç doldurulmamasıdır.**

---

## Başlık dili sözleşmesi (hatırlatma)

| Seçim | Gösterim zinciri (başlıklar) | Gösterim zinciri (kişi/karakter adları) |
|---|---|---|
| `ENGLISH` | english → romaji → latin ham → native | english → romaji → ad |
| `ROMAJI` | romaji → latin ham → english → native | romaji → english → ad |
| `NATIVE` / `JAPANESE_STAFF` | native → ham | native → ad |

Sözleşme `PreferenceHelpers.getDisplayTitle(...)` ve `displayPersonName(...)` ile tüm
ekranlarda uygulanır. Sözleşme çalışıyordu; **varyantlar boştu.**

---

## Kök nedenler

### KN-1 — İlişkiler/Öneriler zenginleştirmesi ortak çözümleyiciyi hiç kullanmıyordu

`KitsugiBangumiDetailClient.enrichRelationTitles`, p1 `/subjects/{id}/relations` ucunun
slim subject nesneleri için **yalnızca** `/v0/subjects/{id}` infobox'unu deniyordu
(kayıt başına tek istek, dar sayaç ve süre bütçesi). Bangumi infobox'ında `英文名` /
`罗马字` alanları çoğu kayıtta **yoktur**; AniList toplu araması ve TMDB eşleştirmesi
(`BangumiSubjectTitleResolver`) bu yolda hiç çağrılmıyordu. Sonuç: ilişkiler sekmesi
CJK başlıkla kalıcı olarak kilitleniyordu.

### KN-2 — İlişkiler için arka plan ısınma (warm) yolu yoktu

Kişi/karakter detaylarında bulunan `warmBangumiStaffTitles` / `warmBangumiCharacterTitles`
deseninin ilişkiler karşılığı hiç yazılmamıştı. Senkron geç bütçeyi aştığında CJK başlığı
düzeltip ekrana yansıtan ikinci bir geç yoktu; `DetailCache` de CJK hâliyle yazılıyordu.

### KN-3 — Karakter/kişi adları için tek kaynak Bangumi infobox'uydu

`enrichStaffDetailNames` / `enrichCharacterDetailNames` / `enrich*NamesFromBangumi`
ailesi karakter ve kişi adlarını yalnızca `/p1/characters/{id}` ve `/p1/persons/{id}`
infobox'ından çözüyordu. Bangumi karakter/kişi infobox'ında romaji alanı **çok seyrek**,
İngilizce alanı neredeyse hiç yoktur. AniList'in karakter (`characters(search:)`) ve
kadro (`staff(search:)`) uçlarındaki `name.native / name.full / name.alternative`
alanlarıyla birebir eşleme yapılmıyordu; bu yüzden `ニーナ・タッカー` → `Nina Tucker`
dönüşümü hiçbir zaman gerçekleşmiyordu.

### KN-4 — Konu kadrosu sekmeleri (Karakterler/Ekip) arka plan ısınması almıyordu

Detay sayfası Karakterler/Ekip sekmeleri senkron geçte (süre bütçeli) zenginleştiriliyordu;
bütçeyi aşan CJK adlar için sekme düzeyinde arka plan geçi yoktu.

---

## Çözüm

### 1. Yeni ortak çözümleyici: `BangumiEntityNameResolver`

- AniList GraphQL'te **aliased toplu arama**: tek istekte 6 ad
  (`cN: Page(page:1,perPage:5){ characters(search:$sN){ name{ full native alternative } } }`,
  kişiler için `staff(search:)`).
- **Birebir (normalize) eşleşme**: adayın `native` / `full` / `alternative` adlarından biri
  Bangumi özgün adıyla aynı normalize anahtara sahipse kabul edilir; kısmi/bulanık eşleşme
  reddedilir → yanlış kişinin adı asla gösterilmez.
- **Kalıcı önbellek** `bangumi_entity_name_cache_v1` (`c{id}` / `p{id}` → romaji/english/native)
  + bellek içi indeks + olumsuz önbellek (6 sa TTL; yalnızca arama gerçekten yapıldıysa).
  Böylece kişi detayında çözülen ad, konu kadrosu sekmesinde ağ isteği olmadan görünür.
- Kademe: önbellek → AniList → (kişi/karakter için) Bangumi infobox yedeği.
  Senkron geç `12` istek / 6 sn; arka plan `48` istek / 20 sn.

### 2. İlişkiler/öneriler ortak başlık çözümleyicisine bağlandı

`enrichRelationTitles` artık `BangumiSubjectTitleResolver.resolve(...)` kullanır:
önbellek → AniList toplu araması → subject infobox → TMDB. Eski kayıt-başına infobox
döngüsü kaldırıldı; `applyResolvedLatinTitle` varyantları doldurur (saf, test edilebilir).

### 3. Arka plan ısınma deseninin tamamlanması

- `ApiResultDetailViewModel` ve `MediaEntryDetailViewModel`:
  `warmBangumiRelationTitles(...)` (ilişkiler + öneriler) ve
  `warmBangumiCharacterNames(...)` / `warmBangumiStaffNames(...)` (kadro sekmeleri).
  Arka plan geçi değişim ürettiyse sekme StateFlow'u ve `DetailCache` güncellenir;
  kimlik koruması için aynı veri örneği hâlâ ekrandaysa yazılır.
- Kişi/karakter detay VM'lerindeki mevcut ısınma çağrıları artık adları da kapsar
  (enrich fonksiyonları adımlarını içeride yürütür).

### 4. Zenginleştirme zincirinin tüm Bangumi yüzeylerine uygulanması

`enrichStaffDetailNames`, `enrichCharacterDetailNames`, `enrichCharacterNamesFromBangumi`,
`enrichStaffNamesFromBangumi`, `enrichVoiceActorNamesFromBangumi` artık
**önce** AniList birebir ad eşleşmesini, **sonra** infobox yedeğini kullanır.
Böylece: Detay→İlişkiler/Öneriler/Karakterler/Ekip, Kişi detayı→Hakkında/Karakterler/
Yapımlar/Seslendirenler, Karakter detayı→Hakkında/Yapımlar/Seslendirenler ve Listem
satırları tek sözleşmeye bağlanır.

### 5. Birim testleri (`BangumiEntityNameResolverTest`)

- Normalize anahtar eşitliği (noktalama / boşluk / genişlik / büyük-küçük harf).
- Birebir eşleşme: native, full ve alternative üzerinden; **kısmi ve süper-dize reddi**.
- Aliased sorgu kurulumu (karakter ve kişi; native yoksa liste adı yedeği).
- `applyResolvedLatinTitle` varyant doldurma + `getDisplayTitle` dil zinciri
  (ENGLISH → english, ROMAJI → romaji, NATIVE → native).

---

## Kapsam dışı (bilinçli)

- Bangumi `summary` (Japonca biyografi) metinleri: başlık dili sözleşmesi ad/başlık
  alanları içindir; açıklamalar uygulamadaki otomatik çeviri katmanına aittir.
- Uydurma transliterasyon yapılmaz: kaynakta Latin karşılık yoksa özgün ad gösterilir
  ("varsa tabii" ilkesi).
