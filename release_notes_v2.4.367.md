# Kitsugi Beta v2.4.367

[English notes below]

---

## 🇹🇷 Türkçe Sürüm Notları

### 1. Bangumi Stüdyo, Yapımcı ve Yayıncı Çözümleme Düzeltmeleri
- **Çoklu Yazım ve Varyant Desteği (`BangumiOrgDictionary`):**
  - Parantezli adlar (`ジェネオン・ユニバーサル・エンターテイメント(NBCユニバーサル…)`), kapanmamış parantezler ve `・`/`·`/boşluk ayraçları için otomatik arama varyantları türetildi.
  - Katakana yazım varyantları (`エンタテイメント` ↔ `エンターテイメント` ↔ `エンタテインメント`) tam kapsamlı olarak desteklendi.
  - Şirket unvanları (`株式会社`, `ジャパン`, `Co., Ltd.`, `Inc.`, `LLC`) kelime sınırlarına sadık kalınarak (`Zinc` gibi kelimeleri bozmadan) temizlenip arama yapılması sağlandı.
  - Latin isimlerin Japonca/Çince isimlere haritalanması için ters indeks (`byEnglishCompact`) ve bellek içi önbellek (`localNamesForLatin`) eklendi.
  - Şirket tekilleştirme: Aynı şirkete ait farklı dil veya varyantlardaki isimler (`SHUEISHA` ve `集英社` gibi) artık tek bir ortak sözlük anahtarı (`BangumiOrgDictionary.identityKey`) altında birleştirilerek mükerrer çipler engellendi.
- **Üç Kademeli İsim Doğrulama (`StudioSourceSupport`):**
  - İsim doğrulama motoru; normalize metin eşitliği, sözlük kimliği eşitliği ve Latin sözlük köprüsü olmak üzere 3 aşamalı hale getirildi. Yanlış şirket eşleşmeleri engellenirken diller arası meşru eşleşmeler güvence altına alındı.
- **V0 Endpoint Yedek Çözümü & Yapımlar:**
  - Bangumi p1 (`next.bgm.tv`) şirket kayıtlarında boş döndüğünde veya yapım listesi içermediğinde otomatik olarak resmi V0 `/v0/persons/{id}` ve `/v0/persons/{id}/subjects` uç noktalarına geri çekilerek stüdyo sayfası ve yapımlarının boş kalması önlendi.
- **Yapım Komiteleri (Production Committees):**
  - Bangumi üzerinde gerçek bir kurum/kişi kaydı bulunmayan yapım komiteleri (`...製作委員会`, `...Project` vb.) tespit edilerek tıklanamaz çip haline getirildi; böylece tıklandığında "KTS-COM-1020" hata ekranına düşmesi engellendi.
- **Net Hata Bildirimi:**
  - Kayıt bulunamadığında veya ağ arızasında varsayılan hata kodu netleştirildi (`KTS-COM-1021`).

---

### 2. Listem: Bağımsız Çift Eksenli Gruplama ve Sıralama
- **Bağımsız İki Eksen (Gruplama + Sıralama):**
  - Listem ekranı, "tek seçim" kısıtlamasından çıkarılarak gruplama ve sıralamanın birbirini ezmeden bağımsız çalıştığı modern mimariye kavuşturuldu (`MyListSections.kt`).
  - **Gruplama Modları (`MyListGroupingIds`):**
    - *Varsayılan (Eklenme Tarihi):* Bugün, Dün, Bu Hafta, Bu Ay, Bu Yıl, Daha Eski zaman dilimlerine göre bölümler.
    - *Duruma Göre:* İzleniyor, Tamamlandı, Beklemede, Bırakıldı, Planlandı.
    - *Düz Liste (Grupsuz):* Başlıksız, kesintisiz tek liste akışı.
  - **İç Sıralama Bağımsızlığı:**
    - Gruplama aktifken seçilen sıralama (Puan, Başlık, İlerleme, Eklenme Tarihi, Favoriler vb.) grupları kaldırmaz; her bölümün kendi içindeki sıralamasını belirler.
- **Kompakt Çift Yönlü Sıralama Çipi:**
  - Dağınık "Son eklenen" ve "İlk eklenen" iki ayrı çip yerine, tek dokunuşla yön değiştiren (Azalan/Artan) kompakt `Eklenme tarihi ⇅` çipi uygulandı.
- **Gerçek Eklenme Zamanı Desteği:**
  - Veritabanı v30 yapısı (`addedAt` / `createdAt`) ile girdilerin gerçek kütüphaneye eklenme tarihleri baz alınarak kusursuz zaman dilimi ayrımı sağlandı.
- **Kompakt Sıralama ve Gruplama Arayüzü:**
  - Hızlı seçim menüsü ve filtre sayfası (Bottom Sheet) yeni gruplama ve sıralama modellerine göre baştan uca optimize edildi.
  - Profil kullanıcı medya listesi ekranı (`KitsugiUserMediaListScreen`) ile UI tam uyumu sağlandı.

---

## 🇬🇧 English Release Notes

### 1. Bangumi Studio, Producer & Publisher Resolution Fixes
- **Variant & Fuzzy Dictionary Matching (`BangumiOrgDictionary`):**
  - Added support for bracketed names (`ジェネオン・ユニバーサル・エンターテイメント(NBCユニバーサル…)`), unclosed brackets, and punctuation separators (`・`/`·`/spaces).
  - Full Katakana variant equivalence (`エンタテイメント` ↔ `エンターテイメント` ↔ `エンタテインメント`).
  - Word-boundary safe corporate suffix trimming (`株式会社`, `ジャパン`, `Co., Ltd.`, `Inc.`, `LLC`) preventing false positives on words like `Zinc`.
  - Reverse index (`byEnglishCompact`) and local cache (`localNamesForLatin`) bridging Latin and CJK company names.
  - Company deduplication using unified identity keys (`BangumiOrgDictionary.identityKey`), preventing duplicate chips across languages (e.g. `SHUEISHA` and `集英社`).
- **3-Stage Studio Verification (`StudioSourceSupport`):**
  - Name matching now checks normalized strings, dictionary identity keys, and Latin dictionary bridges, guaranteeing authentic company association across languages.
- **V0 Endpoint Fallback & Works:**
  - If Bangumi p1 (`next.bgm.tv`) returns null or lacks works for a company, the client gracefully falls back to V0 endpoints (`/v0/persons/{id}` and `/v0/persons/{id}/subjects`), populating details and media lists reliably.
- **Production Committees:**
  - Production committees (`...製作委員会`, `...Project`, etc.) lacking dedicated Bangumi entity records are rendered as non-clickable chips, preventing navigation into unexpected "KTS-COM-1020" error states.
- **Explicit Failure Codes:**
  - Clarified fallback error code to `KTS-COM-1021` for unresolved studio entities.

---

### 2. MyList: Dual-Axis Independent Grouping & Sorting
- **Dual Independent Axes (Grouping + Sorting):**
  - MyList is now decoupled into two independent axes: Grouping and Sorting (`MyListSections.kt`).
  - **Grouping Modes (`MyListGroupingIds`):**
    - *Default (Date Added):* Today, Yesterday, This Week, This Month, This Year, Older.
    - *By Status:* Watching, Completed, On Hold, Dropped, Plan to Watch.
    - *Flat (No Grouping):* Seamless unsectioned list.
  - **Intra-Section Sorting:**
    - Selecting any sort order (Title, Score, Progress, Added Date, Favorites, etc.) preserves grouping and sorts items within each section.
- **Compact Bi-Directional Sort Chip:**
  - Consolidated separate "Newest" and "Oldest" chips into a single bi-directional toggle chip (`Date added ⇅`).
- **Accurate Addition Timestamps:**
  - Utilizes Room v30 timestamp tracking (`addedAt` / `createdAt`) to place entries into chronological sections accurately.
- **Streamlined UI & Parity:**
  - Updated sort dropdown menu and bottom filter sheet for intuitive selection.
  - Complete UI and behavior parity implemented across `MyListScreen` and `KitsugiUserMediaListScreen`.
