# PLAN / TASK — Bangumi Başlık Dili Sözleşmesi: Tüm Yüzeyler (v2.4.339)

**Tarih:** 2026-10-22 · **Branch:** `arena/2f03d60d-kitsugi-beta` · **Commit:** `6a95315`
**Kök neden raporu:** `KOK_NEDEN_RAPORU_BANGUMI_BASLIK_DILI_TUM_YUZEYLER_2026-10-22.md`

---

## Amaç

Kullanıcının seçtiği başlık dili (İngilizce / Romaji / Özgün) Bangumi kaynaklı **her**
yüzeyde geçerli olmalı: adlar, başlıklar ve varyantlar seçili dile zinciriyle gösterilmeli.
Kaynakta Latin karşılık yoksa uydurma transliterasyon yapılmaz ("varsa tabii" ilkesi).

Kapsam ekranları:
- Detay → **İlişkiler** ve **Öneriler**
- Detay → **Karakterler** ve **Ekip** (kadro adları)
- Kişi detayı → **Hakkında / Karakterler / Yapımlar / Seslendirenler**
- Karakter detayı → **Hakkında / Yapımlar / Seslendirenler**

---

## Görevler ve durum

| # | Görev | Dosya | Durum |
|---|---|---|---|
| 1 | Karakter/kişi adları için AniList aliased toplu arama + birebir (normalize) eşleşme + kalıcı önbellek (`bangumi_entity_name_cache_v1`) + olumsuz önbellek | `data/remote/BangumiEntityNameResolver.kt` (yeni) | ✅ |
| 2 | `normalizeKey` görünürlüğünü köprüyle paylaş (`private` → `internal`) | `data/remote/KitsugiAniListPersonBridge.kt` | ✅ |
| 3 | İlişkiler/öneriler zenginleştirmesini ortak `BangumiSubjectTitleResolver`'e bağla (önbellek → AniList → infobox → TMDB); eski kayıt-başına infobox döngüsünü kaldır; saf `applyResolvedLatinTitle` çıkar | `data/remote/KitsugiBangumiDetailClient.kt` | ✅ |
| 4 | Kişi/karakter detay zenginleştirmesine AniList ad kademesi ekle (hero adı + karakter rolleri + VA adları), infobox yedeğe düşsün | `KitsugiBangumiDetailClient.enrichStaffDetailNames`, `enrichCharacterDetailNames` | ✅ |
| 5 | Kadro köprülerine AniList kademesi ekle (`background`/`cacheOnly` parametreleri) | `enrichCharacterNamesFromBangumi`, `enrichStaffNamesFromBangumi`, `enrichVoiceActorNamesFromBangumi` | ✅ |
| 6 | Arka plan ısınma: ilişkiler + öneriler + kadro sekmeleri; StateFlow ve `DetailCache` güncellemesi, kimlik koruması | `ui/screens/detail/ApiResultDetailViewModel.kt`, `MediaEntryDetailViewModel.kt` | ✅ |
| 7 | Birim testleri: normalize anahtar, birebir eşleşme, kısmi/süper-dize reddi, aliased sorgu, dil zinciri | `test/.../BangumiEntityNameResolverTest.kt` (yeni) | ✅ |
| 8 | Sürüm + doküman | `app/build.gradle.kts` (2.4.339), `release_notes_v2.4.339.md`, kök neden raporu | ✅ |

---

## Tasarım kararları

1. **Birebir eşleşme, bulanık değil:** AniList adayının `name.native / name.full /
   name.alternative` alanlarından biri Bangumi özgün adıyla aynı normalize anahtara
   sahipse kabul edilir. Kısmi eşleşme reddedilir → yanlış kişinin adı gösterilmez.
2. **Kalıcı önbellek ilk sınıf:** bir ekranda çözülen ad diğer tüm ekranlarda ağ isteği
   olmadan görünür; olumsuz önbellek yalnızca arama gerçekten yapıldıysa yazılır (6 sa TTL),
   ağ hatası/zaman aşımı yeniden denenebilir kalır.
3. **Senkron bütçe küçük, arka plan cömert:** senkron geç 12 istek / 6 sn (ilk çizimi
   geciktirmez), arka plan 48 istek / 20 sn; değişim üretilirse sekme ve `DetailCache`
   anında güncellenir → ikinci ziyaret tamamen seçili dilde açılır.
4. **Gösterim katmanına dokunulmadı:** `PreferenceHelpers.getDisplayTitle(...)` ve
   `displayPersonName(...)` sözleşmesi aynen korunur; yalnızca varyant alanları
   (`titleRomaji/titleEnglish/titleJapanese`, `romanizedName/englishName/nativeName`)
   doldurulur.

## Kapsam dışı

- Bangumi `summary` (Japonca biyografi) metinleri → otomatik çeviri katmanının konusu.
- Uydurma transliterasyon yok; Latin karşılık bulunamazsa özgün ad gösterilir.

## Doğrulama

- `BangumiEntityNameResolverTest` (JUnit4, saf yardımcılar): eşleşme kuralları + sorgu
  kurulumu + `applyResolvedLatinTitle` dil zinciri.
- Manuel senaryo: CLANNAD → İlişkiler; Koorogi Satomi → Karakterler; Okazaki Tomoya →
  Yapımlar; İngilizce tercihinde ilk açılışta senkron+önbellek, birkaç saniye içinde
  arka plan geçiyle Latin başlık/ad; ikinci açılışta tamamen Latin.
