# Kitsugi Beta v2.4.419

## 🇹🇷 Türkçe

### 🧠 Ortak Küme — Eklentiler Artık Katalog Kaynağına Göre Kopmuyor
- **Öğrenen eklenti hafızası:** Bir CloudStream eklentisi bir film/dizi/animeyi bir kez bulduysa (kendi aramasından, kimlikle veya başlık eşlemesiyle — yol fark etmez), içerik adresi eserin ortak kimliğine kaydedilir. Aynı esere AniList, TMDB, MAL veya başka bir kaynaktan girseniz bile eklenti artık aramayı baştan yapmaz; kanıtlanmış adres doğrudan açılır.
- **Torrentio'nun sırrı uygulamaya taşındı:** Torrentio tek kimlikle (IMDb) tek istekte sonuç verdiği için hızlı ve kesindi; kazıyan eklentiler ise arama → eşleme → bölüm bulma zincirinin her halkasında kopabiliyordu. Yeni çözüm sırası: **hafıza → kimlik → başlık araması**. Zincirin kırılgan kısmı, bir kez başarılmış içerikte tamamen atlanır.
- **Ölü adresler kendini temizler:** Hafızadaki adres çalışmazsa kayıt düşürülür ve eski arama yolları otomatik denenir; hafıza hiçbir zaman engel olmaz.
- Teşhisin tamamı: `KOK_NEDEN_RAPORU_EKLENTI_KAYNAK_BAGIMLILIGI_VE_ORTAK_KUME_2026-11-04.md`

---

## 🇬🇧 English

### 🧠 Unified Resolution Cluster — Providers No Longer Depend on the Catalog Source
- **Learning provider memory:** Once a CloudStream provider finds a title (via its own search, native ID, or title matching), the content URL is stored against the work's unified identity. Re-entering the same title from AniList, TMDB, MAL, or any other source now opens the proven URL directly instead of re-running the fragile search/match chain.
- **New resolution order:** memory → native ID → title search. Stale URLs evict themselves and fall back to the classic pipeline automatically.
- Full diagnosis: `KOK_NEDEN_RAPORU_EKLENTI_KAYNAK_BAGIMLILIGI_VE_ORTAK_KUME_2026-11-04.md`
