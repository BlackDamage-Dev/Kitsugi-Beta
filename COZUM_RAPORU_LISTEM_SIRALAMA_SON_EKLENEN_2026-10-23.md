# ÇÖZÜM RAPORU — Listem Sıralaması: "Son Eklenen" ve Tüm Sıralama Mantıkları (2026-10-23)

## Şikâyet
> "Listem sayfasında 'Son eklenen' özelliği düzgün çalışmıyor. Bunu seçtiğimiz
> zaman özel bir mantığı olsun, eklenme tarihine göre listeleyip gösterebilsin.
> Bu seçiliyken Planlandı/Tamamlandı/İzleniyor'a göre sıralanması tüm işi
> bozuyor. Diğer seçeneklere (İlk eklenen, Ada göre vb.) özgü mantıklar da
> eklenmeli."

## Kök nedenler

### 1. Gerçek "eklenme tarihi" diye bir veri yoktu
`applySort("newest")` yerel satır `id`'sine göre sıralıyordu. `id`, kaydın
**yerel DB'ye yazılma** sırasıdır; kullanıcının listesine ekleme sırası değil:
- AniList içe aktarımı `sort: [MEDIA_ID]` ile sayfalıyor → "Son eklenen" fiilen
  "medya ID'si en büyük" demekti.
- Silip yeniden yazan senkronlar id sırasını her seferinde karıştırıyordu.

### 2. Durum bölümleri sıralamayı eziyordu (asıl şikâyet)
Sıralama uygulandıktan sonra liste HER ZAMAN İzleniyor → Tekrar → Planlandı →
Durduruldu → Bırakıldı → Tamamlandı bölümlerine yeniden ayrılıyordu
(`groupMyListEntriesByStatus` + `MyListGroupedContent`). Seçilen sıralama
yalnızca bölüm İÇİNDE geçerliydi: dün eklenen bir "Tamamlandı", aylar önce
eklenmiş "İzleniyor" kayıtlarının altında kalıyordu. Tek istisna, durum
filtresi = "Tamamlandı" iken düz akışa geçen özel durumdu.

### 3. Seçeneklere özgü kusurlar
- **Puan (artan):** puansız kayıtlar başa kaçabiliyordu.
- **İlerleme:** ham bölüm sayısıydı — 100/1000 manga, bitmiş 12/12 animenin
  üstündeydi.
- **Ada göre:** ekrandaki (başlık diline göre) başlık yerine hep depolanan
  romaji başlığa göre sıralıyordu.
- **Son Güncelleme:** Bangumi `updatedAt`'yi milisaniye yazıyor; normalize
  edilmediği için Bangumi satırları (×1000) her zaman haksızca üstteydi.
  Simkl satırları ise `updatedAt = 0` ile geliyordu (hep sonda).
- **Favoriler:** küme içi yalnızca ada göreydi; yeni favoriler kayboluyordu.
- Komşu pager sekmeleri sıralamasız (DB düzeni) çiziliyordu; sekme yerleşince
  sıra "sıçrıyordu".

## Çözüm

### Veri katmanı — `addedAt` (DB v29 → v30)
- `MediaEntry`/`MediaEntryEntity`'ye `addedAt` (epoch sn, 0 = bilinmiyor)
  eklendi; kurucuda bilinçli olarak SON parametre (konumsal kurulumlar kaymasın).
- Göç (`MIGRATION_29_30`): kolon eklenir, mevcut satırlara `updatedAt` geri
  doldurulur (milisaniye olanlar saniyeye çevrilerek). Bu bir tahmindir;
  AniList/Kitsu/Shikimori bir sonraki eşitlemede gerçek oluşturma zamanıyla düzeltir.
- Kaynaklardan gerçek eklenme zamanı:
  - **AniList:** GraphQL'e `createdAt` eklendi.
  - **Kitsu:** `attributes.createdAt`; **Shikimori:** `created_at`.
  - **Simkl:** eklenme yok → `last_watched_at` artık `updatedAt` olarak yazılıyor
    (vekil + "Son Güncelleme" düzeltmesi). MAL/Bangumi: alan yok.
- Kayıp koruması tek yerden: `MediaEntryRepository.resolveAddedAt` —
  (1) kaynağın gerçek değeri > (2) yerelde sabitlenmiş değer > (3) ilk yazımda
  `updatedAt` vekili > (4) yalnızca kullanıcının tekil eklemesinde "şimdi".
  Toplu içe aktarım asla "şimdi" uydurmaz. `smartImport`, `insert/insertAll`,
  `update`, `replaceAll`, `replaceSourcePreservingAdultFlags` (+18 işaretleri
  gibi eklenme zamanları da kimlik üzerinden geri yazılır) ve yedek
  dışa/içe aktarımı aynı çözümleyiciyi kullanır.
- Birleşik "Tümü" kartında eklenme zamanı, yapımın herhangi bir kaynakta
  kütüphaneye **ilk** girdiği andır (kopyalardaki en erken bilinen değer).

### Sıralama motoru (`applySort` yeniden yazımı)
Anahtarlar satır başına bir kez hesaplanır (Schwartzian); her seçeneğin kendi
mantığı + deterministik eşitlik zinciri var:
- **Son/İlk eklenen:** `addedAt`; bilinmeyen iki yönde de sonda (içeride
  güncelleme + yerel id vekili).
- **Ada göre:** başlık dili uygulanmış **ekrandaki** başlık; aynı adda yıl artan.
- **Puan:** puansız iki yönde de sonda.
- **İlerleme:** tamamlanma oranı (progress/total, 0..1 kırpılmış); toplamı
  bilinmeyen sonda, kendi içinde bölüm sayısına göre.
- **Favoriler:** favoriler önde; küme içinde son eklenen üste.
- **Başlangıç/Bitiş/Yıl/Öncelik:** değersiz kayıt iki yönde de sonda;
  tarihler `yyyy-MM-dd`'ye normalize edilir.
- **Son Güncelleme:** `normalizeToEpochSeconds` ile milisaniye → saniye.

### Sunum sözleşmesi (`myListUsesFlatSortedLayout`)
- Yeni **"Varsayılan"** (`default`) sıralama kimliği: klasik durum bölümlü
  gezinme. Yeni kurulum varsayılanı budur; eski kayıtlı "newest" değeri tek
  seferlik göçle varsayılana taşınır (AppViewModel `migrateLegacyDefaultSort`)
  ki güncelleme sonrası kimsenin Listem görünümü aniden değişmesin.
- Kullanıcı HERHANGİ bir sıralama seçerse → **düz global akış; sıralama
  kazanır**, durum yalnızca filtre olarak kalır. Tek durum filtresi aktifse
  de düz (eskiden yalnızca Tamamlandı içindi; asimetri kalktı).
- **"Son eklenen / İlk eklenen"e özel:** düz akış dönem başlıklarıyla
  (Bugün / Dün / Bu Hafta / Geçen Hafta / Bu Ay / Geçen Ay / Son 3 Ay /
  Bu Yıl / Geçen Yıl / Daha Eski / Eklenme Tarihi Bilinmiyor) çizilir
  (`groupMyListEntriesByAddedPeriod` + `MyListAddedPeriodContent`).
- Komşu pager sekmeleri de aynı sıralamayı uygular (sıçrama yok).
- Aynı düzeltme başkasının listesi ekranına da uygulandı
  (`KitsugiUserMediaListScreen`; AniList `createdAt` artık modelde akıyor,
  ekrana özel sıralama dalı silindi). TV kütüphanesi de yeni motordan faydalanır.

## Testler
- `MyListFilterHelpersTest`: eklenme tarihi vs yerel id, bilinmeyen tarihin
  iki yönde de sona düşmesi, puansız/son güncelleme normalize, ilerleme oranı,
  başlık dili, düz-akış karar tablosu, dönem gruplaması.
- `AddedAtResolutionTest` (yeni): `resolveAddedAt` öncelik zinciri.
- Mevcut testlerin beklentileri korunur (örn. puan testi 9 > 4 > puansız).

Bu ortamda Android SDK/JDK olmadığı için derleme+test koşumu yerelde
yapılmalı: `./gradlew :app:testFossDebugUnitTest`. İlk derlemede KSP,
`app/schemas/.../30.json` dosyasını üretecektir; üretilen dosya commit'lenmelidir.

## Bilinen sınırlar
- Göç geri dolgusu tahmindir (`updatedAt`); gerçek tarihler AniList/Kitsu/
  Shikimori'de **bir eşitleme sonrası** netleşir. MAL/Simkl/Bangumi API'leri
  eklenme zamanı vermediği için bu kaynaklarda "ilk görüldüğü an" esastır.
- Dönem başlıkları yalnızca Son/İlk eklenen sıralamasındadır; diğer
  sıralamalar kesintisiz düz akıştır (bilinçli: örn. Puan'da başlık = skor
  kovaları istenirse ayrıca değerlendirilebilir).
