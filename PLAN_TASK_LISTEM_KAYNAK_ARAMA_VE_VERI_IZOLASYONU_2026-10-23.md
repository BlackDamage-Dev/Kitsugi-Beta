# Plan Task — Listem Kaynak Bazlı Arama ve Veri İzolasyonu Düzeltmesi

**Tarih:** 2026-10-23  
**Dal:** `arena/dc72f2a1-kitsugi-beta`  
**Kapsam:** Listem (`MyListScreen`) sayfasında kaynak seçimine (`Tümü`, `AniList`, `MAL`, `Simkl`, `Kitsu`, `Shikimori`, `Bangumi`) göre veri gösterimi, arama izolasyonu, kardeş başlık varyantı zenginleştirmesi ve yanlış pozitif arama eşleşmelerinin giderilmesi (+0 yeni XML `<string>` ID).

---

## 1. Tespit Edilen Sorunlar ve Kök Nedenler

1. **Arama sırasında kaynak filtresinin devre dışı kalması (`MyListScreen.kt`, `MyListComponents.kt`):**
   - `selectedTabEntries` içerisindeki `searchQuery.isNotBlank() && selectedTabIndex != MY_LIST_BANGUMI_TAB_INDEX -> entriesAfterAdultFilter` ve `MyListContentPage` içerisindeki `useGlobalSearchResults` dalları nedeniyle; **AniList**, **MAL**, **Simkl**, **Kitsu** ve **Shikimori** sekmelerinde arama yapıldığında seçili kaynak yok sayılıp veritabanındaki tüm kaynakların ham satırları gösteriliyordu (ör. `"kaiju no 8"` aramasında 5 kaynağın tamamında Kitsu + MAL + AniList kartlarından oluşan aynı `3 sonuç` listesinin çıkması).
   - `MyListContentPage` içerisindeki `if (!isConnected && searchQuery.isBlank())` ve `else if (pageEntries.isEmpty() && searchQuery.isBlank())` koşulları yüzünden, bağlı olmayan veya boş olan sekmelerde arama yapıldığında bağlantı/senkron ekranı gizlenip yanlışlıkla sonuç ekranına düşülüyordu.

2. **Simkl, Shikimori ve Bangumi satırlarında eksik İngilizce / Latin başlık varyantları (`MyListLibraryGrouping.kt`, `MyListIdentityCrosswalk.kt`, `MalAxisLibraryTitleHydrator.kt`):**
   - Simkl (`GET /sync/all-items/anime`) ve Shikimori (`GET /api/v2/user_rates`) API'leri yalnızca Romaji ad (`"Kaijuu 8-gou"`) döndürüp `titleEnglish` ve `titleJapanese` alanlarını `null` bırakıyor; Bangumi koleksiyon API'si ise Çince/Japonca katalog adını (`"怪兽8号"` / `"怪獣8号"`) taşıyordu. Bu nedenle `Tümü` sekmesinde *Kaiju No. 8* kartında birleşen **Simkl** (`[S]`) satırı, `"kaiju no 8"` aramasında eşleşmiyor ve İngilizce başlık dilinde Romaji ad gösteriyordu.
   - `ExternalAuthManager` tercihlerindeki `kitsu_media_<userId>_{a|m}_<malId>` kayıtları `MyListIdentityCrosswalk` içinde tersine çevrilmediğinden Kitsu satırlarının MAL ekseniyle birleşmesi eksik kalabiliyordu.

3. **Başlık dışı meta alanlar ve bulanık aramadan kaynaklanan yanlış pozitifler (`MyListFilterHelpers.kt`):**
   - Arama sorgusu `source`, `subtitle`, `type.name`, `status` ve sentetik `malId` (`100M`/`300M`/`400M`/`500M`) alanlarında da alt-dize olarak aranıyordu.
   - `KitsugiContentPrefs.fuzzyMatches` içindeki dağınık alt-dizilim (`isSubsequence`) ve Latin dışı (CJK/Kiril) sorgularda `normalize(query)` boş döndüğünde `if (needle.isEmpty()) return true` çalışması tüm kütüphaneyi yanlış pozitif olarak döndürebiliyordu.

---

## 2. Kapsam İçindeki Dosyalar

### Yeni Dosyalar (2)
- `app/src/main/java/com/kitsugi/animelist/data/remote/MalAxisLibraryTitleHydrator.kt`
- `PLAN_TASK_LISTEM_KAYNAK_ARAMA_VE_VERI_IZOLASYONU_2026-10-23.md`

### Değişen Dosyalar (10)
- `app/src/main/java/com/kitsugi/animelist/data/local/MyListIdentityCrosswalk.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/app/AuthViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListComponents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListFilterHelpers.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListLibraryGrouping.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListViewModel.kt`
- `app/src/test/java/com/kitsugi/animelist/data/local/MyListIdentityCrosswalkTest.kt`
- `app/src/test/java/com/kitsugi/animelist/ui/screens/mylist/MyListFilterHelpersTest.kt`
- `app/src/test/java/com/kitsugi/animelist/ui/screens/mylist/MyListLibraryGroupingTest.kt`

---

## 3. Uygulanan Çözüm Özeti

- **Tek geçişli `MyListLibrarySnapshot` ve kesin sekme izolasyonu:** `Dispatchers.Default` üzerinde hem `Tümü` sekmesinin tekilleştirilmiş kartları/rozetleri hem de `1..6` platform sekmelerinin yalnızca kendi kaynağına ait listeleri (`entriesByTab`) üretilir. `HorizontalPager` içindeki her sayfa yalnızca kendi `pageEntries` listesini filtreler, sıralar ve gruplar; rastgele seçim (`🎲`), istatistikler, durum alt sayfası ve sekme sayaçları seçili kaynağa kilitlenir.
- **Bağlantı ve boş durum koruması:** Bağlı olmayan (`!isConnected`) veya boş (`pageEntries.isEmpty()`) sekmelerde arama metni girilmiş olsa bile kaynağın kendi bağlantı/senkron ekranı korunur; ilk arka plan hesaplaması sırasında boş ekran titremesi engellenir.
- **Kardeş başlık varyantları ve arka plan tamamlama:** Doğrulanmış aynı yapım grubundaki kardeş satırlar arasında (kaydın `id`, `source`, `malId`, ilerleme ve puan alanlarına dokunmadan) eksik `titleEnglish`, `titleJapanese`, CJK/yer tutucu başlıklar için Latin `title` ve `searchAliasesByEntryId` (`BangumiTitleCache` dahil) tamamlanır. Yalnızca Simkl veya Shikimori bağlı kullanıcılar için `MalAxisLibraryTitleHydrator` ile eksik başlık varyantları AniList `idMal_in` toplu GraphQL sorgusu üzerinden kalıcı olarak doldurulur.
- **Kitsu ters kimlik köprüsü ve kaynak onarım koruması:** `MyListIdentityCrosswalk.reverseKitsuMapping` eklendi; `MyListViewModel.repairMisclassifiedSources` gerçek `mal` kayıtlarını MAL bağlantısı yokken yanlışlıkla `anilist` kaynağına taşımayacak şekilde sınırlandırıldı.
- **Hassas başlık araması:** Türkçe `İ/I/ı/i` katlama, noktalama duyarsız başlık eşleşmesi (`"kaiju no 8"` ↔ `"Kaiju No. 8"`), çok kelimeli ve tam sayı belirteçli başlık eşleşmesi (`"kaiju 8"` ↔ `"Kaijuu 8-gou"`, `"kaiju 2"` eşleşmez), güvenli kısaltma/yazım hatası toleransı ve tam 4 haneli yıl eşleşmesi eklendi.

---

## 4. Kabul Ölçütleri ve Doğrulama

- `Tümü` sekmesi bağlı kaynaklardaki aynı yapımı tek kartta birleştirip kaynak rozetlerini gösterir; her bireysel kaynak sekmesi (`AniList`, `MAL`, `Simkl`, `Kitsu`, `Shikimori`, `Bangumi`) arama açıkken de kapalıyken de yalnızca kendi verisini gösterir.
- `"kaiju no 8"` gibi İngilizce, Romaji veya CJK başlık aramaları Simkl, Shikimori ve Bangumi dahil ilgili kaydı bulunan her kaynakta yalnızca o kaynağın kartını döndürür.
- Bağlı olmayan kaynaklarda arama yapılsa bile başka kaynakların verisi sızmaz; kaynağın kendi bağlantı ekranı görünür.
- `git diff --check` ve `python3 scripts/check_localization_resource_parity.py` (`17696 strings`, `+0` yeni XML ID) temiz geçti; 3 birim test sınıfına yeni regresyon testleri eklendi.
