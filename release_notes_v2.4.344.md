# Kitsugi Beta v2.4.344

## 🇹🇷 Türkçe

### 🎭 Simkl & TMDB Kurgusal Karakter Portre ve Biyografi Ayrımı Düzeltmesi
- **Seslendirmen Profili İzolasyonu:** The Simpsons gibi Batı animasyonlarında TMDB jeneriğinden çekilen kurgusal karakterlerin (Homer, Marge, Bart vb.) seslendirmen (Dan Castellaneta, Julie Kavner, Nancy Cartwright) fotoğrafı, biyografisi ve takma adlarıyla doldurulması tamamen engellendi. Seslendirmen yalnızca *Seslendirmenler* listesine atanır.
- **Detay ve Önbellek Sızıntısı Koruması (`DetailCache`):** Seslendirmen fotoğraflarının karakter portresi olarak `resolvedCharacterPortraits` ve `mediaCharacters` önbelleklerine sızması engellendi. TMDB URL'lerindeki boyut segmentlerini (`/w185/`, `/w300/`, `/w500/`) normalize eden `DetailCache.isSameImageResource` ile aynı aktör fotoğrafı kopyaları yakalanıp elenir.
- **Fandom & Vikipedi Kurgusal Karakter Çözümlemesi:** Kurgusal karakterler için AniList/Jikan/Bangumi kataloglarına ek olarak Fandom ve Vikipedi (`wikipedia.tr`, `wikipedia.en`) portre ve biyografi çözümleme zinciri eklendi.
- **Vikipedi Adil Kullanım (Fair-Use) Portre Desteği (`FandomApi`):** MediaWiki `PageImages` sorgularına `pilicense=any` eklenerek Vikipedi'deki adil kullanım lisanslı kurgusal karakter görsellerinin (`Homer_Simpson_2006.png`, `Marge_Simpson.png`, `Bart_Simpson_200px.png`) API tarafından thumbnail olarak dönmesi sağlandı. Toplu sorgulara `pilimit=50` ve `exlimit=20` limitleri eklendi.
- **Çoklu Rol Adı Ayrıştırma (`/`):** Tek seslendirmenin canlandırdığı birleşik karakter adları ("Homer Simpson / Abe Simpson / Barney Gumble") `/` üzerinden ayrıştırıldı; birincil ad ana karakter, ikincil adlar ise alternatif roller olarak korundu.
- **Menşe Ülke Filtresi:** Simkl `country` alanı `countryOfOrigin` olarak modele bağlandı; Batı animasyonlarının Asya anime kataloglarında boşuna aranması önlendi.
- **Seslendirmenler Sekmesi:** TMDB kurgusal karakterleri (`!isRealMediaRole`) için Karakter Detay sayfasında *Seslendirmenler* sekmesi görünür kılındı.
- **Wiki Sekmesi Canlı Entegrasyonu:** Wiki sekmesi karakter sayfasını çözdüğünde ana detaydaki eksik portre ve biyografi otomatik olarak tamamlanır.

### 📊 MDBList Canlı API Kotası Takibi ve Kota Aşımı Koruması
- **Gerçek Zamanlı ve Dinamik Kota (`MdbListQuotaCenter`):** API kotası artık sabit 1.000 varsayımı yerine `https://api.mdblist.com/user` ve `X-RateLimit-*` başlıklarından dinamik olarak okunur. Ücretsiz (1.000) ve Destekçi (10.000 / 25.000 / 100.000 / 250.000) planlarının tamamı otomatik tanınır.
- **Ayarlar Paneli:** Ayarlar → Harici Entegrasyonlar → MDBList sekmesine kalan istek sayısı, doluluk çubuğu, yerel saat diliminde sıfırlanma zamanı ve yenileme butonu eklendi.
- **Detay Sayfası Harici Puanlar Kartı:** Rozet yanında küçük kota göstergesi (`998/1.000`, %10 altında turuncu, bitince kırmızı "Kota doldu").
- **Kota Aşımı Koruması & Sessiz Önbellek:** Günlük kota dolduğunda ağa çıkılmadan önbellekteki son puanlar gösterilir (429 döngüsü önlenir), kartta bilgilendirici uyarı gösterilir ve tek seferlik bildirim sağlanır. HTTP 429 artık anahtar geçersiz hatası değil kota dolumu olarak ele alınır.
- **Birim Testleri:** `TmdbAnimeCharacterPortraitEnrichmentTest`, `FandomApiParsersTest`, `FandomCharacterQueriesTest`, `MdbListClientTest` ve `MdbListQuotaTest` ile doğrulandı.

---

## 🇬🇧 English

### 🎭 Simkl & TMDB Fictional Character Portrait and Biography Isolation Fix
- **Voice Actor Profile Separation:** For Western animations (e.g. *The Simpsons*) sourced via TMDB credits, fictional character records (Homer, Marge, Bart, etc.) are strictly prevented from inheriting voice actor (Dan Castellaneta, Julie Kavner, Nancy Cartwright) photographs, biographies, or aliases. Voice actors remain cleanly positioned in the *Voice Actors* roster.
- **Detail & Cache Poisoning Prevention (`DetailCache`):** Voice actor headshots are barred from leaking into `resolvedCharacterPortraits` and `mediaCharacters` caches. `DetailCache.isSameImageResource` normalizes TMDB image URL resolution prefixes (`/w185/`, `/w300/`, `/w500/`) to detect duplicate actor photos across different sizes.
- **Fandom & Wikipedia Fictional Fallback Pipeline:** Fictional characters missing in anime databases (AniList, Jikan, Bangumi) now query Fandom and Wikipedia (`wikipedia.en`, `wikipedia.tr`) for portraits, biographies, and infobox metadata.
- **Wikipedia Non-Free / Fair-Use Image Support (`FandomApi`):** Added `pilicense=any` to MediaWiki `prop=pageimages` requests so Wikipedia non-free character portraits (`Homer_Simpson_2006.png`, etc.) return thumbnails correctly. Batch limits (`pilimit=50`, `exlimit=20`) were also expanded.
- **Multi-Role Slash Splitting (`/`):** Compound role strings (e.g. "Homer Simpson / Abe Simpson / Barney Gumble") are split by `/`; the primary role becomes the main character name while secondary roles are preserved as alternatives.
- **Origin Country Attribution:** Simkl's `country` field is mapped to `countryOfOrigin`, skipping redundant East Asian anime catalog searches for Western animations.
- **Voice Actors Tab Visibility:** The *Voice Actors* tab is now displayed on the Character Detail screen for fictional TMDB roles (`!isRealMediaRole`).
- **Live Wiki Tab Sync:** Resolving character wiki pages dynamically completes missing character portraits and summaries in the main detail page.

### 📊 MDBList Live API Quota Tracking & Exhaustion Guard
- **Dynamic Real-Time Quota (`MdbListQuotaCenter`):** API quota is now dynamically tracked from `https://api.mdblist.com/user` responses and `X-RateLimit-*` headers instead of hardcoded limits, supporting all Free (1,000) and Supporter tiers (10,000+).
- **Settings Dashboard:** Integrations → MDBList displays remaining requests, progress bar, localized daily reset time, and manual refresh controls.
- **Detail Screen External Ratings Card:** Live quota badge (`998/1,000`, orange under 10%, red "Quota used up").
- **Exhaustion Guard & Cached Fallback:** Prevents 429 cascades by serving cached ratings when quota is depleted, accompanied by inline recovery times and deduplicated notifications. HTTP 429 is treated as quota exhaustion rather than an invalid API key.
- **Unit Tests:** Validated via `TmdbAnimeCharacterPortraitEnrichmentTest`, `FandomApiParsersTest`, `FandomCharacterQueriesTest`, `MdbListClientTest`, and `MdbListQuotaTest`.
