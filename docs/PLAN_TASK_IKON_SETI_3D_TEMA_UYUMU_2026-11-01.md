# PLAN & TASK — 3B İkon Seti, Tema ve Renk Uyumu

**Tarih:** 2026-11-01
**Dal:** `arena/50b23715-kitsugi-beta`
**Durum:** Tamamlandı (derleme doğrulaması hariç — bu ortamda JDK/Android SDK yok)

---

## 1. Görev (kullanıcı isteği)

1. Uygulamadaki ikonlar **gerçek, premium görünen 3B ikonlar** olsun.
2. **Arka plan plakası olmasın** (renkli daire/plaka yok).
3. Her satırın **işleviyle uyumlu** olsun, koyu Kitsugi temasına uysun.
4. İkonlar **uygulamanın her yerinde aynı set** olsun — "eşitsizlik olmasın".
5. Tıklanabilir/açılır tüm alanlarda eksik gedik kalmasın.
6. İkonlar **renk seçimi + tema + gradyan** ile **otomatik** uyumlu çalışsın.
7. Sonuçlar kullanıcıya **görsel olarak** gösterilsin.

---

## 2. Plan (adım adım)

| # | Adım | Durum |
|---|---|---|
| 1 | Aday setleri karşılaştır (Fluent 3D, 3dicons front/premium, front/color, front/gradient, iso) | ✅ |
| 2 | Stil seçimi: **3dicons `front/gradient`** (CC0, cam degrade, saydam zemin) | ✅ |
| 3 | 122 varlık envanteri + 50 mantıksal anahtara eşleme tablosu | ✅ |
| 4 | İndirme betiği (`fetch_3dicons.py`) — GitHub Contents API | ✅ |
| 5 | Normalize boru hattı (`normalize.js`): kırp → optik denge → alfa/gölge temizliği → 192×192 | ✅ |
| 6 | `KitsugiIconAssets.kt`: `KitsugiArt` enum (50 varlık, ~300 Material adı eşlemesi), `KitsugiArtIcon`, `KitsugiRowIcon` | ✅ |
| 7 | Ayar satırları, oynatıcı sekmeleri, alt gezinme çubuğu → 3B | ✅ |
| 8 | Ekranlarda doğrudan çizilen 88 "konu" ikonunu 3B'ye taşı | ✅ |
| 9 | Tema uyumu: koyu/AMOLED olduğu gibi, açık temada yumuşak gölge, seçili durumda vurgu hâlesi | ✅ |
| 10 | **Otomatik renk uyumu**: vurgu rengine (gradyan dâhil) göre ikon ton kaydırması | ✅ |
| 11 | Önizleme sayfaları (`sheet.js`, `mock_previews.js`, `theme_matrix.js`) + ekran görüntüleri | ✅ |
| 12 | Lisans/atıf: CC0 tam metin + ayna depo notu; Fluent MIT dosyasının kaldırılması | ✅ |
| 13 | `docs/IKON_SETI.md` belgesi (kaynak, eşleme tablosu, kurallar, kapsam) | ✅ |
| 14 | Gradle derlemesi / APK doğrulaması | ⛔ bu ortamda yapılamadı |

---

## 3. Teknik kararlar

- **Set:** 3dicons (Vijay Verma) — **CC0 1.0**; render'lar `Vyshnav2255/notion-icons-3d` aynasından (Apache-2.0).
- **Stil:** `front/gradient` — cam yüzey, mor→pembe→turuncu degrade, saydam zemin.
  Reddedilenler: `front/premium` (metallik, renksiz), `front/color` (mat/cansız), `iso/*`.
- **Normalizasyon:** trim → alan tabanlı optik denge (%92 kutu, en fazla 1.45× büyütme,
  tuval sınırı) → alfa tabanı 26 (gölge kalıntısı temizliği) → 192×192 PNG.
- **Kural:** 3B = "konu" ikonu (≥20 dp), 2B = "kontrol" glifi (kapat/geri/onay/chevron/
  filtre/⋮, 10–18 dp).
- **Tema uyumu:** koyu/AMOLED'de dokunulmaz; açık temada plaka değil radyal gölge;
  seçili sekmede vurgu renginden hâle (`accentGlow`).
- **Otomatik renk uyumu:** setin doğal tonu 303,7°; vurgu renginin tonu okunur, ikon
  yalnızca ton ekseninde döndürülür (`ColorFilter.colorMatrix(hueRotation)`), doygunluk
  ve parlaklık korunur. Gradyan vurguda iki rengin ortası; doygunluğu %12 altındaki
  (gri/beyaz) vurgularda kaydırma yapılmaz. Kapatma: `iconsFollowAccent = false`.

---

## 4. Değişen dosya grupları (121 dosya)

| Grup | Adet | Not |
|---|---|---|
| `app/src/main/res/drawable-nodpi/ic_kitsugi_*.png` | 50 | 192×192, saydam, toplam ~390 KB |
| `ui/icons/KitsugiIconAssets.kt` | 1 | Enum + eşleme + çizim + tema/ton mantığı |
| `ui/theme/Theme.kt` | 1 | `iconsFollowAccent` parametresi, ton kaydırmasının sağlanması |
| UI dosyaları (ekran/bileşen/diyalog) | 55 | İkonların 3B'ye taşınması, hâle, gölge |
| `tools/iconpack/*` | 6 | fetch / normalize / map / sheet / mock / theme_matrix |
| `third_party/*` | 2 | CC0 lisans metni + ayna depo notu |
| `docs/*` | 3 | `IKON_SETI.md`, iki önizleme PNG'si |

Tam liste: zip içindeki `DEGISEN_DOSYALAR/` klasörü ve `degisiklikler.patch` (main'e göre).

---

## 5. Commit'ler

| Commit | İçerik |
|---|---|
| `ee584c7` | 3dicons `front/gradient` setine geçiş (50 varlık, normalize, enum) |
| `7d98411` | Önizleme sayfası düzeltmeleri |
| `5ec2c09` | Ekranlardaki "konu" ikonlarının 3B'ye taşınması + tema/hâle uyumu |
| `e7d8b31` | Vurgu rengine göre **otomatik ton kaydırması** |

---

## 6. Kalan işler / riskler

1. **Derleme doğrulaması yapılmadı** — ortamda JDK/Android SDK yok. Android Studio'da
   `./gradlew :app:assembleDebug` çalıştırılmalı; çıkan hata bu turda kapatılmalı.
2. İstenirse Ayarlar → Görünüm altına **"İkon tonu: Vurguya uyarla / Doğal palet"**
   anahtarı eklenebilir (şu an varsayılan açık, tek parametreyle kapatılıyor).
3. İstenirse kalan küçük aksiyon glifleri (kopyala/paylaş/sil/beğen) da 3B'ye çevrilebilir.
4. Açık tema ve AMOLED cihazlarda göz kontrolü önerilir (gölge/hâle katmanı yeni).
