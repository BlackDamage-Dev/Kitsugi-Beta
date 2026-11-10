# PLAN & TASK — Simkl / TMDB Kurgusal Karakter Detayı ve Portre Sızıntısı Düzeltmesi (2026-10-22)

## 1. Sorun Özeti ve Gözlem

- **Belirti (`image-1.png` – `image-4.png` — *The Simpsons (1989)* Simkl Kaydı):**
  1. **Karakter Detay Sayfasında Seslendirmen Profilinin Gösterilmesi (`image-2.png`, `image-3.png`, `image-4.png`):**
     - Simkl üzerinde *The Simpsons* gibi MAL / AniList / Kitsu kimliği bulunmayan Batı animasyonlarında bir karaktere (*Homer Simpson*, *Marge Simpson*, *Bart Simpson*) tıklandığında sayfa başlığında karakterin adı (`Homer Simpson / Abe Simpson / Barney Gumble / Krusty the Clown` vb.) yazmasına rağmen:
       - **Görsel** alanında seslendirmenin kendi insan fotoğrafı (*Dan Castellaneta*, *Julie Kavner*, *Nancy Cartwright*),
       - **Diğer İsimler** alanında seslendirmenin gerçek hayattaki takma adları (`Daniel Louis Castellaneta`, `Nancy Jean Cartwright`, `'Nympho-Nance' Cartwright`, `Bartfright Cartwright`),
       - **Biyografi** alanında ise seslendirmenin kendi biyografisi (*"Dan Castellaneta (29 Ekim 1957 doğumlu), Amerikalı bir film, tiyatro ve televizyon oyuncusu..."*) gösteriliyordu.
  2. **Seslendirmen Fotoğrafının Simkl "Karakterler" Sekmesine Sızması (`image-1.png`):**
     - Kullanıcı *Homer Simpson* ve *Marge Simpson* detay sayfalarını açtıktan sonra Simkl **Karakterler** sekmesine döndüğünde, Homer ve Marge satırlarının solundaki karakter portresi yuvalarına da *Dan Castellaneta* ve *Julie Kavner*'ın fotoğrafları yerleşiyordu.
     - Diğer karakterlerin (*Bart Simpson*, *Lisa Simpson*, *Moe Szyslak*, *Ned Flanders*) portre yuvaları ise boş (yer tutucu ikon) kalıyordu.

---

## 2. Tespit Edilen Kök Nedenler

1. **`KitsugiCharacterClient.fetchCharacterDetail("tmdb", ...)` İçinde Seslendirmen Profiline Düşülmesi:**
   - `isRealMediaRole == false` (kurgusal karakter) durumunda `fetchFictionalCharacterDetailByName` yalnızca **AniList, Jikan ve Bangumi** kataloglarını sorguluyordu; Fandom veya Vikipedi yedeği bulunmuyordu.
   - Anime kataloglarında yer almayan *The Simpsons* karakterleri için `fetchFictionalCharacterDetailByName` `null` döndüğünde, kod `TmdbApiClient().fetchPersonCharacterDetail(characterId)` ile seslendirmenin TMDB kişi kaydını çekip `KitsugiCharacterDetail` nesnesini doğrudan:
     - `imageUrl = fallbackImageUrl ?: personDetail.imageUrl`,
     - `alternativeNames = personDetail.alternativeNames`,
     - `biography = personDetail.biography`
     değerleriyle dolduruyordu.

2. **`CharacterDetailViewModel` ve `DetailCache.propagateCharacterPortrait` Üzerinden Portre Önbelleği Zehirlenmesi:**
   - `CharacterDetailViewModel.buildCharacterGallery`, `detail.imageUrl` içindeki seslendirmen fotoğrafını `bestPortrait` olarak seçip `DetailCache.propagateCharacterPortrait(alias, bestPortrait)` çağrısıyla `resolvedCharacterPortraits` ve `mediaCharacters` önbelleklerine yazıyordu.
   - Ayrıca TMDB görsel URL'leri `TmdbCreditsClient.fetchCredits` içinde `/w185/`, `fetchPersonCharacterDetail` içinde `/w300/` ve `KitsugiPersonImageAggregator` içinde `/w500/` boyut segmentleriyle üretildiğinden, düz `==` karşılaştırması aynı seslendirmen fotoğrafını kopya olarak yakalayamıyordu.

3. **Vikipedi `prop=pageimages` Sorgularında `pilicense=any` ve `pilimit=50` Eksikliği (`FandomApi.kt`):**
   - Vikipedi'de kurgusal ve animasyon karakter görsellerinin neredeyse tamamı (*Homer_Simpson_2006.png*, *Marge_Simpson.png*, *Bart_Simpson_200px.png* vb.) "adil kullanım" (non-free / fair-use) lisanslıdır.
   - MediaWiki `PageImages` eklentisi varsayılan olarak `pilicense=free` kullandığından, `pilicense=any` gönderilmediği sürece Vikipedi API'si kurgusal karakterlerin hiçbirisi için `thumbnail` döndürmüyordu.
   - Ek olarak `titleLookupParams` içinde `pilimit` ve `exlimit` belirtilmediği için toplu karakter sorgularında MediaWiki yalnızca ilk sayfa için görsel/özet döndürüyordu.

4. **Çoklu Rol Adlarının (`/` Ayracı) ve Batı Animasyonlarının Fandom/Vikipedi Çözümlemesi:**
   - TMDB jeneriğinde bir seslendirmenin birden fazla karakteri `"Homer Simpson / Abe Simpson / Barney Gumble / Krusty the Clown"` biçiminde tek dizgede birleştirilmiş olarak gelir.
   - `fetchFandomBatchPortraits` ve `FandomCharacterQueries.from` bu dizgeyi `/` üzerinden ayrıştırmadan aradığı veya yalnızca tek bir çözümlenen wiki'ye (`wikipedia.tr`) baktığı için Fandom topluluk wikisi (`simpsons`) ve İngilizce Vikipedi (`wikipedia.en`) üzerindeki karakter sayfaları eşleşmiyordu.
   - `KitsugiSimklDetailClient.fetchSimklDetailDirect` yapımın `country` (`countryOfOrigin`) alanını modele aktarmadığı için Batı animasyonları gereksiz yere Doğu Asya anime kataloglarında aranıp zaman aşımına takılıyordu.

---

## 3. Uygulanan Çözüm ve Değişen Dosyalar

1. **`app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiCharacterClient.kt`**
   - `fetchCharacterDetail("tmdb", ..., isRealMediaRole = false)` akışında kurgusal karakterlere seslendirmenin fotoğrafının (`personDetail.imageUrl`), biyografisinin (`personDetail.biography`) ve takma adlarının (`personDetail.alternativeNames`) atanması tamamen kaldırıldı.
   - `fetchFictionalCharacterDetailByName` zincirine Fandom + Vikipedi (`wikipedia.tr` ve `wikipedia.en`) üzerinden karakter portresi, biyografisi ve bilgi kutusu alanlarını (cinsiyet, yaş, doğum günü) paralel çözümleyen `fetchWikiFictionalCharacterDetailByName` eklendi.
   - `/` ile birleştirilmiş çoklu rol adlarında birincil karakter adı (`primaryCharacterName`) ana ad olarak ayrıştırıldı; ikincil karakter rolleri (`extractSlashRoleAliases`) "Diğer İsimler" (`alternativeNames`) olarak korundu; seslendirmenin filmografisi ise ilgili karakter rolüne göre (`filterCharacterAppearances`) süzüldü.
   - `fetchFandomBatchPortraits` çoklu rol adlarını ayrıştıracak ve Fandom wiki + Fandom slug adayları + `wikipedia.en` + `wikipedia.tr` sırasıyla portreleri tamamlayacak şekilde güçlendirildi.
   - `isEastAsianOriginOrAnime` ve `enrichFromCrossSourceCharacterListsParallel` içinde Batı kaynaklı animasyonların anime kataloglarında zaman kaybetmesi önlendi.

2. **`app/src/main/java/com/kitsugi/animelist/data/remote/DetailCache.kt`**
   - TMDB görsel URL'lerini boyut segmentinden (`/w185/`, `/w300/`, `/w500/`) bağımsız karşılaştıran `normalizeImageIdentity` ve `isSameImageResource` yardımcıları eklendi.
   - `propagateCharacterPortrait` içine seslendirmen fotoğrafı koruması eklendi; seslendirmen fotoğrafının `resolvedCharacterPortraits` veya `mediaCharacters` içine yazılması engellendi.
   - `getCachedCharacterPortrait` ve `propagateCharacterPortrait` hem tam rol adını hem de `/` öncesi birincil karakter adını eşleştirecek şekilde güncellendi.

3. **`app/src/main/java/com/kitsugi/animelist/ui/screens/detail/CharacterDetailViewModel.kt` & `ApiResultDetailViewModel.kt` & `CharacterDetailPage.kt`**
   - `buildCharacterGallery` içinde seslendirmen fotoğrafları galeriden ve `propagateCharacterPortrait` yayılımından süzüldü; portre yayılımı yalnızca karakterin kendi adıyla sınırlandırıldı (`alternativeNames` içindeki diğer karakterlerin portrelerinin ezilmesi önlendi).
   - `onWikiCharacterPageResolved` eklenerek Wiki sekmesi karakter sayfasını çözdüğünde ana detaydaki eksik portre ve biyografinin otomatik tamamlanması sağlandı.
   - `CharacterDetailPage.kt` üzerinde kurgusal TMDB karakterleri (`!isRealMediaRole`) için **Seslendirmenler** sekmesi görünür yapıldı.
   - `ApiResultDetailViewModel.kt` içinde `DetailCache.isSameImageResource` ile seslendirmen fotoğrafı sızıntısı engellendi.

4. **`app/src/main/java/com/kitsugi/animelist/data/fandom/FandomApi.kt` & `FandomCharacterQuery.kt`**
   - `FandomApi.titleLookupParams`, `pageImages`, `pagePreviews` ve `pageMeta` içinde Vikipedi sorgularına `pilicense=any`, toplu sorgulara `pilimit=50` ve `exlimit=20` eklendi.
   - `FandomCharacterQueries.from` içinde `/` içeren TMDB karakter adlarından birincil karakter adının ilk sıraya alınması, diğer rollerin `alternatives` listesine eklenmesi ve aynı oyuncunun farklı kurgusal rollerinin ayrı `cacheKey()` alması sağlandı.

5. **`app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiPersonImageAggregator.kt` & `KitsugiSimklDetailClient.kt`**
   - `KitsugiPersonImageAggregator` içinde kurgusal karakterler için Vikipedi karakter portresi yedeği (`wikipediaCharacterImage`) eklendi.
   - `KitsugiSimklDetailClient.fetchSimklDetailDirect` içinde Simkl `country` alanı `countryOfOrigin` olarak modele aktarıldı.

6. **Birim Testleri**
   - `app/src/test/java/com/kitsugi/animelist/data/remote/TmdbAnimeCharacterPortraitEnrichmentTest.kt`
   - `app/src/test/java/com/kitsugi/animelist/data/fandom/FandomApiParsersTest.kt`
   - `app/src/test/java/com/kitsugi/animelist/data/fandom/FandomCharacterQueriesTest.kt`
