# 05 — Görev B: Medya türü ve kaynak adaptörü
**Durum:** [ ] Başlanmadı  **Bağımlılık:** 04  **Bitiş kapısı:** 8×4 eşleme unit testi + kimlik testi yeşil.

## Yapılacaklar
- [ ] Mobil'e özgü yeni saf `DiscoverTab` (Diziler/Filmler/Anime/Manga), `MobileShelfDescriptor`/mapper tasarla. `ExplorePlatform`, `ExploreCategoryType`, `ExplorePayload` ve `sourceSections` eski imzalarını değiştirmeden **source + category + durum + results + stable key + devam/query semantiği** taşı. API veya cache veri şeması değiştirmemek temel tercihtir.
- [ ] `02` matrisi için açık `platform × category → tab` haritası yaz. Önce kaynak/kategori semantiği; ardından güvenilir kimlik/tür doğrulaması. TMDB anime filmleri `Movie` gelebilir, yalnız enum tipine güvenme; Bangumi `bangumiSubjectType` ve REAL endpoint ayrımını test et. Karma ve belirsiz raflar için türe özel endpoint/sınırlı davranış seç.
- [ ] Her sonuçta özgün source/type/ID koru. `exploreIdentity`/`matchesInSource` sözleşmesine dokunma; `source=mal` ve `jikan` kanonikleştirme doğrulansın. Cross-source aynı isim/puanla birleştirme yok.
- [ ] Tümü görünümünde raflar platform bağımsız birleşik puan değil kaynak etiketli. Tek seçili AniList modundaki Kitsu fallback'ın gerçek kaynak rozeti ve devam yönlendirmesi yanlışsa bu işi ayrı risk olarak ele al: yanlış kaynakla sayfalama yapma; tutarlı yöntem (güvenli raf bölme veya sonlu/uyarılı davranış) tasarla, mevcut fallback'i sessizce kaldırma.
- [ ] `separateNovelsManga` ve manhwa raflarını aynen koru; kaynakta olmayan rafı üretme. `ALL` veri sahipliği `forSource` filtreleme kuralını gevşetme.

## Testler
Saf mapper unit testleri: tüm mevcut source/category çiftleri, 32 source×tab, TMDB film-anime karışımı, Simkl'in legacy alanları, Bangumi subject 1/2/6, novel ayarı, boş/list ve aynı ID farklı tür/kaynak, Kitsu fallback, yetişkin bayrağı. Devam spec'i belirsiz raf testte FAIL/kapalı olur. Mevcut `AllSourcesExploreTest` ve `ExploreMappingSourceScopeTest` korunur. Ortak işlev davranışı değişikliği TV'ye de yayılacaksa durup kullanıcı onayı iste.
