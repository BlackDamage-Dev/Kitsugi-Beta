# 01 · PLAN — Kitsugi Beta çapraz kaynak eşitleme (cross-sync)

> Hedef: tek bir kayıt (ekleme / düzenleme / silme) tüm bağlı kaynaklara **doğru, kayıpsız,
> geri alınamaz biçimde bozmadan** yayılsın. Çevrimdışı ve yeniden başlatma sonrası da
> tutarlı kalsın. Sessiz atlasın olmasın.

Sürüm: 2026-10-23 · Dal: `arena/86b99c20-kitsugi-beta` · HEAD: `c8e77e5`

---

## 1. Kabul ölçütleri (Acceptance Criteria)

Bir iş ancak aşağıdakilerin **tamamı** doğruysa "bitti" sayılır.

| # | Ölçüt | Nasıl doğrulanır |
|---|---|---|
| A1 | Her alan (durum, ilerleme+cilt, puan+gelişmiş puan, başlangıç/bitiş tarihi, tekrar bayrağı+sayısı, favori, not, etiket, öncelik, gizlilik) **destekleyen her kaynağa** yazılır | Sağlayıcı yetenek matrisi × yazıcı kodu |
| A2 | Desteklemeyen kaynakta alan **istenmez** (API hatası üretilmez) | Yazıcı gövdesi incelenir |
| A3 | Hiçbir yazım **ilerlemeyi geriye çekmez** | İlerleme monotonluğu kuralı + test |
| A4 | Hiçbir yazım **kullanıcının az önce yaptığını** geri almaz | En yeni kazanır kuralı + test |
| A5 | Aynı veride iki tur üst üste **aynı isteği tekrar üretmez** (sonsuz döngü yok) | Kirli-alan boş küme kuralı |
| A6 | Çevrimdışı değişiklik **kaybolmaz**, bağlantı gelince gider | Kuyruk testleri |
| A7 | Uygulama kapanıp açılsa da bekleyen iş kaybolmaz | Room + drain testi |
| A8 | Her atlanan kaynak için **görünür gerekçe** (metin) vardır | `OUTCOME_*` eşlemesi + UI metni |
| A9 | Eşzamanlılık: 6 kaynak paralel, biri diğerini kilitlemez | `supervisorScope` + `PlatformRateLimiter` |
| A10 | API sözleşmeleri resmî dokümana uyar (alan adları, enum değerleri, tarih biçimi) | Rapor §"Kaynak doğrulaması" |

---

## 2. Mimari harita (kodda nereye bakılır)

```
ui/app/AuthViewModel.kt                 birleştirme + toplu/canlı fan-out orkestrasyonu
ui/app/CrossSyncClusterEngine.kt        6! permütasyon bağımsız kümeleme motoru (E96–E97)
ui/app/CrossSyncCandidateIndex.kt       birleşik başlık/kimlik aday indeksi (groupsByAnyTitle + replaceGroup)
ui/app/CrossSyncIdentityGuard.kt        çapraz yazma güvenlik kapısı (!blocksCrossWrite + clusterYearsCompatible)
ui/mylist/MyListIdentityCrosswalk.kt    sağlayıcılar arası kimlik köprüsü (CrossProviderIdentityCache + bgm_detail_xref_v1)
data/auth/CrossSyncMergePolicy.kt       saf birleştirme (I/O yok, test edilebilir)
data/auth/ExternalListSyncManager.kt    hazırlık, allowedWrites, yerel yansıtma satırı (upsertSyncedLocalMirrors — E98)
data/auth/{AniList,Mal,Kitsu,Shikimori,Bangumi,Simkl}SyncManager.kt   yazıcılar
data/auth/BangumiIdNamespace.kt         NFKC arama + infobox/expectedYear eşadlı ayrımı (E100)
data/auth/BangumiSubjectTitleResolver.kt Bangumi başlık + AniList idMal geri yazımı (E104)
data/auth/remote/ShikimoriApiClient.kt  shikimori.io / .one / .tv otomatik ayna failover (E102)
utils/MediaIdentity.kt                  çapraz alan CJK↔Latin kimlik eşleştirici (E101)
```

---

## 3. Fazlar (Geçiş 16 Sonu İtibarıyla)

| Faz | İçerik | Durum |
|---|---|---|
| P0 | Alan modeli, yetenek matrisi, fark hesabı | ✅ |
| P1 | 6 yazıcı + gövde doğruluğu (E01–E36) | ✅ |
| P2 | Yazıcı çalışma zamanı hataları (tarih, puan, durum haritaları) | ✅ |
| P3 | Kuyruk / çevrimdışı / yeniden başlatma / oturum yenileme dayanıklılığı (E103) | ✅ |
| P4 | Birleştirme + sıradan bağımsız kümeleme algoritması (`6! = 720` permütasyon — E96, E97) | ✅ |
| P5 | Geri-okuma / yerel `media_entries` yansıtma satırı (E69, E98) | ✅ |
| P6 | Favori yazımı + Shikimori/Bangumi CJK↔Latin kimlik çözümü + ayna failover (E99–E102, E104) | ✅ |
| P7 | Gerçek JDK 17 + Android SDK 36 JUnit testleri + FOSS Debug/R8 Release APK + DEX doğrulaması (E105) | ✅ |
| P8 | Fiziksel cihaz ekran gözlemi (`T18`, `T31`, `T32`) | 📱 Kullanıcı cihaz doğrulaması |
