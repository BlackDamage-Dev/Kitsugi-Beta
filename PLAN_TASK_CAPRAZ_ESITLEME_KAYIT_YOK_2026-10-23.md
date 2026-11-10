# PLAN & TASK — Çapraz Eşitleme "değişiklik yok" Hatası

**Tarih:** 2026-10-23
**Dal:** `arena/d832ebb9-kitsugi-beta`
**Taban:** v2.4.354 (kullanıcının kurulu APK'sının kaynağı)
**Hedef sürüm:** v2.4.355
**Durum:** ✅ Tamamlandı — CI yeşil, APK üretildi (run 40018323097)

---

## 0) Şikâyet (birebir)

Kaiju No. 8 detay sayfası, Kitsu satırı (TV · 12 bölüm · Tamamlandı) kaydedildikten sonra:

```
AniList ve MyAnimeList başarıyla eşitlendi • AniList: eşitlendi, MyAnimeList: eşitlendi,
Simkl: değişiklik yok, Kitsu: değişiklik yok, Shikimori: değişiklik yok, Bangumi: değişiklik yok
```

Yani altı kaynağın dördüne **hiç yazılmıyor**; kullanıcı o kaynaklarda kaydın oluşmadığını görüyor.

---

## 1) Plan — kök neden analizi

| # | Adım | Bulgu |
|---|---|---|
| 1.1 | Eşitleme yolunu oku (`ExternalListSyncManager`) | v2.4.34x'te yazım **alan bazlı** (`SyncField`/`SyncFieldDiff`) |
| 1.2 | Kapı koşulunu incele | `if (fields.isEmpty()) return SourceResult(..., OUTCOME_UP_TO_DATE)` → **hedefte kayıt var mı diye bakılmıyor** |
| 1.3 | Alan desteği tablosunu doğrula (`ProviderCapabilitiesCatalog`) | `FAVORITE`, `TAGS`, `PRIORITY`, `HIDDEN_FROM_STATUS_LISTS` yalnız AniList/MAL'de var → Simkl/Kitsu/Shikimori/Bangumi için `fields` **boş** kalıyor |
| 1.4 | Çağrı zincirini incele (`MediaEntryRepository.update`) | Hiçbir alan değişmediyse **ağa hiç çıkmıyor** → eksik kayıt asla onarılmıyor |
| 1.5 | Hedef yöneticilerin tam yazım davranışını doğrula | Hepsi upsert/create yapıyor → "tam yazım" güvenli |

**Sonuç:** "değişiklik yok" bir durum tespiti değil, **hiç denememek**ti. Yan etkisi: hedefte kayıt
varsa da kısmi yazım yarım kayıt bırakabiliyordu (Simkl'de puan var, liste kaydı yok).

---

## 2) Tasarım kararları

1. **Varlık kaydı** (`RemoteListPresence`): (yapım, sağlayıcı) → "hedefte kayıt VAR" bilgisi kalıcı.
2. **Yazım planı** (`planSyncWrite`):

   | Hedefte kayıt | Yerel fark | Davranış |
   |---|---|---|
   | VAR | boş | istek yok → **"zaten eşit"** |
   | VAR | dolu | yalnız değişen alanlar (`fullWrite=false`) |
   | YOK / doğrulanmadı | herhangi | **tam yazım** (`fullWrite=true`; boş alan GÖNDERİLMEZ) |

3. **Kanonik anahtar**: MAL → AniList → Kitsu → Shikimori → Bangumi → Simkl → TMDB sırasıyla ilk
   dolu kimlik; hiçbiri yoksa normalize başlık + yıl. Aynı yapının farklı kaynak satırları aynı
   kaydı paylaşır.
4. **Onarım turu**: hiçbir alan değişmese bile satır başına **10 dakikada en fazla bir kez** tam
   yazım; her kaydırmada ağa çıkılmaz.
5. **Hesap değişimi**: koparma/hesap değiştirme varlık kayıtlarını temizler.
6. **Dürüst rapor**: etiket "değişiklik yok" → **"zaten eşit"**, yalnızca doğrulanmış eşitlikte.

**Veri kaybı riski kontrolü:** tam yazımda boş yerel alanlar gönderilmez; MAL/Kitsu/Shikimori/
Bangumi/Simkl yazıcılarında bu kural `fullWrite` bayrağıyla korunuyor. Dolayısıyla hedefte elle
girilmiş puan/not/tarih silinmez.

---

## 3) Task listesi

- [x] **T1** — Kodu v2.4.354 tabanına hizala (`chore(base)`), aksi halde düzeltme kurulu APK'ya girmez
- [x] **T2** — `RemoteListPresence.kt`: kalıcı varlık kaydı + kanonik anahtar + onarım throttle
- [x] **T3** — `planSyncWrite`: varlık doğrulanmadan tam yazıma yükseltme (saf fonksiyon)
- [x] **T4** — `ExternalListSyncManager`: altı kaynak bloğunda `writePlan()` kullanımı
- [x] **T5** — `ExternalListSyncManager.prepare`: satırın kendi kaynağını "VAR" olarak tohumla
- [x] **T6** — `aggregate`: başarılı yazımda / başarılı silmede varlık kaydını güncelle
- [x] **T7** — `OUTCOME_UP_TO_DATE` → "zaten eşit" + dokümantasyon
- [x] **T8** — `MediaEntryRepository.update`: değişiklik yoksa onarım turu (throttle'lı)
- [x] **T9** — `ExternalAuthManager.disconnectAccount`: varlık kayıtlarını temizle
- [x] **T10** — `SyncWritePlanTest` birim testleri (5 senaryo)
- [x] **T11** — Sürüm notları (`RELEASE_NOTES.md` v2.4.355) + kök neden raporu
- [x] **T12** — CI: derleme kırmızıydı (4 bayat `MyListSearch*` testi) → düzeltildi:
      - [x] `filterMyListEntries` istekteki `fuzzySearchEnabled` tercihini kullanır (override)
      - [x] Taramaya 32 kayıtta bir `ensureActive()` iptal noktası
      - [x] Metadata (kaynak/alt başlık/sentetik MAL ID) aramaya katılmaz — test beklentileri güncellendi
- [x] **T13** — Yeşil CI + APK artifact (`kitsugi-foss-apks`)

---

## 4) Doğrulama

| Kontrol | Sonuç |
|---|---|
| `:app:testFossDebugUnitTest` (data.auth.*, data.local.*, ui.screens.mylist.*) | ✅ |
| `:app:assembleFossDebug` / `assembleFossRelease` + DEX bytecode doğrulama | ✅ |
| CI run | https://github.com/f26901964-eng/Kitsugi-Beta/actions/runs/40018323097 |

**Cihazda test adımları**
1. Yeni APK'yı kur (eski sürümün üzerine).
2. Bir Kitsu/AniList satırı aç → küçük bir değişiklik yap (puan veya durum) → kaydet.
3. Beklenen: AniList · MAL · Simkl · Kitsu · Shikimori · Bangumi satırlarının hepsinde
   **"eşitlendi"**; zaten eşit olanlarda **"zaten eşit"**.
4. Kayıt gerçekten oluştu mu: Simkl / Kitsu / Shikimori / Bangumi web listelerinde ilgili yapıtı kontrol et.

---

## 5) Riskler ve geri alma

- **Risk:** İlk kaydetmede hedeflere tam yazım gider (varlık kaydı henüz yokken). Bu, alan bazlı
  yazımdan önceki davranıştır; boş alanlar gönderilmediği için hedefteki dolu değerler korunur.
- **Risk:** Onarım turu, çok sayıda küçük düzenleme yapılan oturumda ekstra istek üretir →
  satır başına 10 dakika throttle ile sınırlandı.
- **Geri alma:** `RemoteListPresence` tamamen kaldırılabilir; `writePlan()` çağrıları
  `run.fieldsFor(...)` + `run.isFullWrite` ile birebir eski davranışa döner (tek dosya).
