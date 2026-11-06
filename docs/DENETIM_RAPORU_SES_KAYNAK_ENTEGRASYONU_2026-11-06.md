# 🔍 DENETİM RAPORU — SES KAYNAĞI ENTEGRASYONU

**Tarih:** 2026-11-06
**Soru:** "Bu ses kaynaklarına özgü alanlar düzgün entegre edildi mi?"
**Cevap:** ⚠️ **HAYIR — %40'ı bağlı, %60'ı eksik.**

---

## 1. DÜRÜST DURUM TABLOSU

Kod taramasıyla tek tek doğruladım:

| Katman | Durum | Kanıt |
|---|---|---|
| **24 prosedürel ses** | ✅ Tam çalışır | `ProceduralSoundCatalog` → panel → `AudioTrack` |
| **12 atmosfer preset** | ✅ Tam çalışır | `AtmospherePresets` → katman çalar |
| **Öneri motoru** | ✅ Tam çalışır | `genre` → mood → skor → panel |
| **3 kanallı karıştırıcı** | ✅ Tam çalışır | `setMusicVolume/AmbientVolume/Master` |
| **Ayarlar → Müzik sekmesi** | ✅ Tam çalışır | 5. sekme, tema uyumlu |
| **Anahtar girme + doğrulama** | ✅ Çalışır | `validateKey()` gerçek API çağırır |
| **Anahtarların kaydedilmesi** | ✅ Çalışır | `setMusicJamendoClientId()` vb. |
| **Anahtarların ARAMA için okunması** | ❌ **YOK** | Hiçbir yerden okunmuyor |
| **Panelde uzak katalog listeleme** | ❌ **YOK** | `MangaAmbientPanel` sadece prosedürel gösteriyor |
| **Kaynak durum göstergesi** | ❌ **YOK** | `MusicSourceState` ölü kod |
| **Arama kutusu** | ⚠️ Var ama bağlı değil | `onValueChange` boş |
| **Arka plan çalma / bildirim** | ❌ **YOK** | Servis yok |
| **Uyku zamanlayıcısı** | ❌ **YOK** | — |
| **Atıf/lisans gösterme (UI)** | ❌ **YOK** | `attributionLine()` yazıldı, hiç çağrılmıyor |
| **Kota takibi** | ❌ **YOK** | — |

### Özet
```
YAZILDI:   ~6.200 satır
ÇALIŞIYOR: ~3.700 satır  (%60)
BAĞLI DEĞİL: ~2.500 satır (%40) — istemci, modeller, durum takibi
```

**En kritik: Kullanıcı Jamendo anahtarını girip "Doğrula ve Kaydet" diyor, anahtar kaydediliyor… ve hiçbir şey olmuyor. Çünkü arama katmanı panele bağlanmamış.**

---

## 2. AÇIK KAYNAK ARAŞTIRMASI — NE YAPMALIYIZ

### 2.1 İncelenen projeler

| Proje | Lisans | Ne öğrendik |
|---|---|---|
| **[Metiq](https://github.com/metiq-xyz/android-app)** | GPL-3 | 149 commit, olgun yapı. **Ses dosyalarını `audio-src/masters/ambient/` altında tutup script'le işliyor.** "-22 LUFS" normalizasyonu — tüm sesler eşit yükseklikte. Ambient sesler **gerçek kayıt**, sentez değil. |
| **[Rainfold](https://github.com/butul0ve/rainfold)** | — | **Bize en yakın mimari.** "No loop files, so there is no seam" — tam bizim yaklaşımımız. `Compose → ViewModel → MediaController → MediaSessionService → NoisePlayer → AudioTrack` hattı. **DSP çekirdeği Android'den bağımsız** (`core/dsp/`) → JVM'de test edilebilir. |
| **[Rainfold (2. repo)](https://github.com/shayanmohd/rainfold)** | — | Daha olgun: `core/dsp/` (Rng, noise colours, Biquad, Tilt, Smoother, **Limiter**), `core/engines/` (droplet pool + **Poisson scheduler**), `core/mix/` (Mixer, FadeCurve, **SleepPlan**, MixCodec). **Poisson dağılımı** bizim damla/çıtırtı üretiminde kullandığımız yöntemin aynısı — doğrulama aldık. |
| **[XMSLEEP](https://github.com/Tosencen/XMSLEEP)** | MIT | Basit, dosya tabanlı |
| **[MixNotice](https://github.com/small-rose/MixNotice)** | — | 23 gerçek ses, 6 kanal mix, çift sayaç |
| **Mihon/Tachiyomi eklentileri** | — | **BYOK kalıbı**: API token girilir, **kaydedildiği anda toast ile doğrulanır** ("Connected as …"), sonra ayarlarda yeşil/kırmızı nokta gösterilir |

### 2.2 Bizim mimarimizin neresinde olduğu

```
                    Metiq    Rainfold   Biz (şu an)   Olması gereken
Ses üretimi          Dosya    Sentez    Sentez ✓      Sentez ✓
Katmanlı mix         ✓        ✓         ✓             ✓
Atmosfer preset       ✓        ✓         ✓             ✓
Öneri motoru         ✗        ✗         ✓ (özgün)     ✓
MediaSessionService  ✓        ✓         ✗             ✗ → YAPILMALI
Uyku zamanlayıcısı   ✓        ✓         ✗             ✗ → YAPILMALI
Limiter (clip önleme)✓        ✓         ✗             ✗ → YAPILMALI
Kota/limit takibi    —        —         ✗             ✓ (BYOK için)
Çoklu kaynak         —        —         ✗ (bağlı değil) ✓
Atıf gösterimi       —        —         ✗             ✓ (lisans şartı)
```

### 2.3 Alınacak 4 ders

**1) DSP çekirdeği Android'den bağımsız olmalı**
Rainfold'da `core/dsp/` saf Kotlin — Android importu yok, JVM'de birim testi yapılabiliyor. Bizim `SoundSynths.kt` de `android.*` import etmiyor (sadece `kotlin.math`) → **iyi durumdayız**, test eklenebilir.

**2) Limiter şart**
Ses seviyeleri katman katman toplanınca ±1.0'ı aşabilir → **kırpılma (clipping) = cızırtı**. Rainfold ve Metiq'te limiter var, bizde yok. `coerceIn(-1f, 1f)` kırpma yapar ama sert kırpma distorsiyon üretir.

**3) MediaSessionService zorunlu (Android 17!)**
> *"Android 17 (API 37) itibarıyla: arka planda ses çalmak, ses odağı istemek veya ses seviyesi değiştirmek için **çalışan bir foreground service** şart."*

Bizim `targetSdk = 36`. Android 17'ye çıkınca **arka plan çalma tamamen çalışmaz** olacak. Şu an bile ekran kapanınca ses duruyor.

**4) BYOK kalıbı: kaydederken doğrula, sonra durum göster**
Mihon eklentileri bunu yapıyor: token kaydedilince **anında** doğrulanıyor, sonuç toast + kalıcı yeşil/kırmızı gösterge. Bizim `validateKey()` var ama **durum kalıcı değil** — panel kapanınca kayboluyor.

---

## 3. NE YAPILACAK (öncelik sırasıyla)

### 🔴 P0 — Kritik (bunlar olmadan özellik "yarım")

| # | İş | Dosya | Süre |
|---|---|---|---|
| **1** | **Uzak katalog → panele bağla.** Anahtarları DataStore'dan oku, `MusicCatalogClient`'ı çağır, sonuçları 5. sekmede listele. | `MangaAmbientPanel` + yeni `MusicCatalogViewModel` | 4–5 saat |
| **2** | **Atıf/lisans gösterimi.** Çalan uzak sesin atıf metnini panelde göster (lisans şartı). | `MangaAmbientPanel` | 1 saat |
| **3** | **Kaynak durumu kalıcı olsun.** `MusicSourceState`'i DataStore'a yaz, panelde yeşil/kırmızı nokta. | `SettingsDataStore` + UI | 2–3 saat |

### 🟠 P1 — Önemli (kullanıcı fark eder)

| # | İş | Süre |
|---|---|---|
| **4** | **Arama kutusu** (şu an boş) — kaynaklarda arama | 2 saat |
| **5** | **Limiter** — kırpılma önleme, ses kalitesi | 1 saat |
| **6** | **Kota göstergesi** — kalan istek / günlük limit | 3 saat |

### 🟡 P2 — Arka plan (Android 17 uyumu)

| # | İş | Süre |
|---|---|---|
| **7** | `AmbientPlaybackService` (MediaSessionService) — ekran kapanınca devam | 5–6 saat |
| **8** | Uyku zamanlayıcısı (15/30/60 dk, fade-out) | 2 saat |
| **9** | Bildirim + kilit ekranı kontrolleri | 2 saat |

### ⚪ P3 — Cila

| # | İş | Süre |
|---|---|---|
| 10 | Kaynak renk/logo ayrımı | 2 saat |
| 11 | Yer imi/favori sesler | 2 saat |
| 12 | TV uzaktan kumanda odağı | 3 saat |

---

## 4. ÖNERİLEN KAYNAK-BAZLI UI TASARIMI

Senin istediğin "kaynak bazlı ayrım ve altında kullanışlı şekilde sonuçlar" için:

```
┌──────────────────────────────────────────────────┐
│  🎵  Ortam Sesi                              ✕   │
├──────────────────────────────────────────────────┤
│  [Senin İçin] [Ambiyans] [Müzik] [🌐 Çevrimiçi] [Karışım] │
├──────────────────────────────────────────────────┤
│  🔍  Ses ara (yağmur, piyano, orman…)            │
├──────────────────────────────────────────────────┤
│  ├─ 🟢 Openverse        · Anahtar gerekmez       │
│  │    ├─ Rain Sound and Rainforest  ▶            │
│  │    └─ Thunder Rumble             ▶            │
│  │    ⓘ CC0 · INNORECORDS                       │
│  ├─ 🟢 Internet Archive · Public domain          │
│  │    └─ Forest Ambience 1962       ▶            │
│  ├─ 🟡 Jamendo          · Anahtar girilmedi ⚙    │
│  │    └─ (Ayarlar → Müzik'ten client_id gir)     │
│  ├─ 🔴 Freesound        · Anahtar geçersiz       │
│  │    └─ (Yeniden doğrula)                       │
│  └─ ⚪ Audius           · Kapalı                 │
├──────────────────────────────────────────────────┤
│  Kaynak: Openverse CC0 · Jamendo                 │
└──────────────────────────────────────────────────┘
```

**Durum ikonları:**
- 🟢 Yeşil → anahtar var/geçerli, veya anahtarsız
- 🟡 Sarı → etkin ama anahtar girilmemiş (uyarı)
- 🔴 Kırmızı → anahtar var ama doğrulanamadı (kota/süresi bitmiş)
- ⚪ Gri → kullanıcı kapattı

Her sonucun altında `ⓘ` ikonu → atıf + lisans + "sayfayı aç" bağlantısı.

---

## 5. KOTA TABLOSU (kullanıcıya gösterilecek)

| Kaynak | Anahtarsız | Anahtarlı |
|---|---|---|
| Openverse | 20/dk, 200/gün | Kayıt sonrası sınırsız |
| Internet Archive | ~sınırsız | — |
| Jamendo | ❌ | 35.000/ay |
| Freesound | ❌ | 60/dk, 2.000/gün |
| Audius | 10/sn (sınırlı) | 500.000/ay |

Panelde: `Openverse · bugün 47/200 istek` gibi bir satır.

---

## 6. SONUÇ

**Şu an:** Özellik çalışıyor ama **yarım** — kullanıcı anahtar girip kaydediyor, hiçbir şey olmuyor. Bu, "bozuk" hissi verir.

**P0 tamamlanınca:** Kullanıcı anahtar girip kaydeder → panelde o kaynağın sonuçları yeşil noktayla görünür → atıf gösterilir → çalar. **Tam entegre.**

**Süre:** P0 = 7–9 saat, P0+P1 = 13–15 saat, tamamı = 30–35 saat.

---

*Rapor sonu. Karar: P0'ı hemen başlatayım mı?*
