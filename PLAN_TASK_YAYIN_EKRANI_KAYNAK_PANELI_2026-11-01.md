# PLAN & TASK — Yayın (Kaynak) Ekranı Yenileme — v2.4.341

**Tarih:** 2026-11-01
**Dal:** `arena/44f6d6bd-kitsugi-beta` (taban: `main` @ `c320ff6`)
**Kapsam:** `ui/screens/stream` ekranı — üst bar hareketi, arama/filtre bloğu,
kaynak seçimi + sıralama paneli, `StreamViewModel` yeniden arama yolu.

---

## 1. Kullanıcı isteği (girdi)

Ekran görüntüsündeki 4 işaretli bölge için:

| # | İşaretli bölge | İstek |
|---|---|---|
| 1 | En üst bar (başlık + geri/imdb/indirme/geçmiş/arama) | Kaydırırken **daha düzgün animasyon**, soft giriş-çıkış; diğer sayfalarla **uyumlu** olmalı |
| 2 | Arama alanı + filtre çipleri | **Yazılar garip duruyor**, düzelt |
| 3 | "Tüm kaynaklar / 2/9 eklenti sonuç buldu…" kartı | **Kaldır** (anlamsız) |
| 4 | "Sıralama: kaynak sırası" satırı | **Alttan açılır panele taşı**, paneli **eklentilere uygun ve çalışır** içerikle doldur |

---

## 2. Kök neden analizi (kod tarafı)

### 2.1 Üst bar
- `CollapsibleTopBarBox` / `HideOnScrollConnection` (eski kod) barı `translationY` ile
  kaydırma mesafesine **birebir** bağlıyordu. `onPreScroll` her delta'da ofset yazdığı
  için parmak titremesi doğrudan bara yansıyor, `alpha` değişimi de (1 → 0.75) sert
  bir solma üretiyordu.
- Aynı uygulamada **zaten ortak bir hareket** var:
  `rememberScrollAwareHeaderState` + `KitsugiStickyHeaderReveal`
  (`ui/components/KitsugiAnimations.kt`) — Listem ve kullanıcı listesi ekranları bunu
  kullanıyor (eşik + `expandVertically/shrinkVertically` + fade + slide,
  `KitsugiMotion.stickyHeaderEnter/ExitMillis`).
- Yatay düzende sol kontrol rayı aynı ofseti elle `graphicsLayer { translationY }` ile
  taklit ediyordu → bar ile senkron kayması riskli.

### 2.2 Arama / filtre bloğu
- Placeholder `kaynak_eklenti_veya_video_ara_dizilla_sibnet_dublaj`
  = "Kaynak, eklenti veya video ara... (Dizilla, Sibnet, Dublaj)" → tek satır
  `OutlinedTextField` içinde **kesiliyor** ("Kaynak eklenti vev.").
- Çipler `FilterChipDefaults` (M3 varsayılan yüksekliği ~32dp, büyük tipografi) ile
  çiziliyordu; eklenti ekranlarında ise kompakt `FilterChip` (11sp, 12dp köşe)
  kullanılıyor → **stil tutarsızlığı**.
- Etiket "1080p+" gösterilirken filtre `>= 1080` idi (etiket/kod uyumsuzluğu izlenimi).

### 2.3 "Tüm kaynaklar" kartı
- `AddonChipsRow` içinde: dokununca modal açan tam genişlikte kart +
  altında ayrı `Sort` satırı. Listenin üstünde sürekli yer kaplıyor, özet metni
  ("2/9 eklenti sonuç buldu · 48 video") `ResultsSummaryRow` ile **çiftleniyordu**.

### 2.4 Sıralama
- Sıralama `CompositionLocal` + tek boolean (`qualitySort`) idi → tek mod, panelde
  gösterilecek gerçek bir seçim yok.
- Kalite/Boyut sıralaması yalnızca **grup içinde** uygulanıyordu; grup sırası
  değişmediği için "yüksekten düşüğe" vaadi yanıltıcıydı.

---

## 3. Uygulanan çözüm

### 3.1 Üst bar → ortak hareket (dosya: `StreamScreenContent.kt`)
- `CollapsibleTopBarBox` + `HideOnScrollConnection` **tamamen kaldırıldı** (~95 satır).
- Yerine: `rememberScrollAwareHeaderState(listState)` + `KitsugiStickyHeaderReveal`.
- Sticky bar artık listenin dışında, `Column` akışının ilk elemanı; `LazyColumn`
  `Modifier.weight(1f)` ile kalan alanı alıyor (bar gizlenince boşluk kalmıyor).
- Yatay düzende sol rayın elle `translationY`'si kaldırıldı; ray da bar ile aynı akışta.

### 3.2 Arama/filtre bloğu (aynı dosya, `StreamSearchBar`)
- Yeni kısa string `stream_search_placeholder` = **"Kaynak veya video ara…"**
  (+ EN "Search sources or videos…"), `textStyle = bodySmall`.
- Çipler eklenti ekranlarının `ui.components.addons.FilterChip` bileşenine geçti
  (kompakt 11sp + 12dp köşe, accent uyumlu). Etiketler: Altyazı · Dublaj · 4K ·
  1080p+ · 720p · ⚡ Debrid.
- Arama alanının **sağına** 48dp kompakt panel butonu (`StreamSourcePanelButton`)
  eklendi: aktif filtre/sıralama varken accent + nokta göstergesi, arama sürerken
  ince `CircularProgressIndicator`.
- Panelden tek eklenti seçilince arama alanının altında **aktif eklenti çipi** çıkıyor
  (dokun → filtre kalkar).

### 3.3 "Tüm kaynaklar" kartı
- `AddonChipsRow` ve `SourcePickerRow` silindi; özet tek yerde (`ResultsSummaryRow`).

### 3.4 Yeni alt panel — `StreamSourceSheet.kt` (yeni dosya, 639 satır)
İçerik eklenti odaklı ve **gerçekten çalışır**:

| Bölüm | İşlev |
|---|---|
| Başlık | "Kaynaklar ve sıralama" + `%1$d/%2$d eklenti · %3$d video` özeti + **"Yeniden ara"** aksiyonu + kapat |
| Sıralama | `StreamSortMode`: **Kaynak sırası · Kalite · Boyut** (boyut yeni, `streamSizeBytes` ile GB/MB → bayt) |
| Eklenti başlığı | eklenti sayacı, **"Eklenti ayarları"** kısayolu, hata varsa **"Hatalıları yenile (n)"** |
| Arama | 5'ten fazla eklenti varsa eklenti içi arama |
| Filtre | **"Yalnızca sonuç verenler"** anahtarı + yükleniyor göstergesi |
| Liste | "Tüm kaynaklar" + her eklenti: durum (yükleniyor / doğrulama / hata / N video), tek dokunuşla **yenile** ve **doğrula** ikonları |

### 3.5 `StreamViewModel` mantık düzeltmeleri
- **Sorun:** `retryPlugin()` eklentiyi `"⚡ <ad>"` ile eşliyor. Stremio eklentilerinin
  adı düz metin → paneldeki yenile/doğrula **sessizce hiçbir şey yapmıyordu**.
  **Çözüm:** `String.isCloudstreamAddon()` (`⚡` ön eki) ile aksiyonlar yalnızca CS
  eklentilerinde gösteriliyor; "Hatalıları yenile" listesi de buna göre süzülüyor.
- **Sorun:** `startFetch()` aynı anahtar için cache-hit döndüğü ve tek-eklenti retry
  yalnızca CS'de çalıştığı için *tüm* kaynaklar patladığında tam yeniden arama yolu
  yoktu. **Çözüm:** `refreshAll()` eklendi — `currentFetchKey = null`,
  `CsPluginStatusTracker.clear()`, `CsStreamRunner.clearUnsupportedMethodsCache()`,
  ardından son parametrelerle `startFetch`.
- `LastFetchParams`'a `cs3Url` / `cs3ApiName` eklendi (refresh sonrası direct API yolu
  kaybolmasın).
- **Sorun:** kalite/boyut sıralaması grup içinde kalıyordu. **Çözüm:**
  `sortedByStreamMode()` artık önce grup içini, sonra grupları kendi en iyi değerine
  göre sıralıyor; `when` dal tipleri `Long`'a sabitlendi.

### 3.6 Kaynaklar (TR/EN)
Eklenen string'ler (`values/strings.xml` + `values-en/strings.xml`, 13'er adet):

```
stream_search_placeholder      stream_panel_title        stream_panel_sort_title
stream_open_source_panel       stream_panel_summary      stream_panel_addons_title
stream_panel_only_results      stream_panel_retry_failed stream_panel_addon_settings
stream_panel_refresh_all       stream_sort_source_order  stream_sort_quality_short
stream_sort_size_short
```

`scripts/check_localization_resource_parity.py` → **17709 string senkron**.

---

## 4. Değişen dosyalar

| Dosya | + | − | Not |
|---|---|---|---|
| `app/src/main/java/.../stream/StreamScreenContent.kt` | 823 | 536 | üst bar, arama, layout'lar |
| `app/src/main/java/.../stream/StreamSourceSheet.kt` | 639 | 0 | **yeni** — panel + sıralama modeli |
| `app/src/main/java/.../stream/StreamViewModel.kt` | 45 | 0 | `refreshAll`, `LastFetchParams` |
| `app/src/main/java/.../stream/KitsugiStreamScreen.kt` | 3 | 1 | `onRefreshAll` bağlandı |
| `app/src/main/res/values/strings.xml` | 13 | 0 | TR |
| `app/src/main/res/values-en/strings.xml` | 13 | 0 | EN |
| `app/build.gradle.kts` | 1 | 1 | `2.4.340 → 2.4.341` |
| `release_notes_v2.4.341.md` | 31 | 0 | sürüm notu |

Toplam: **8 dosya, +1068 / −537**

---

## 5. Doğrulama

| Kontrol | Sonuç |
|---|---|
| GitHub Actions — `🏗️ Android Derleme Doğrulama` (foss debug + R8 release + DEX bytecode) | ✅ run `41286193171` |
| `scripts/check_localization_resource_parity.py` | ✅ 17709 string + 4 array senkron |
| `scripts/audit_hardcoded_turkish.py` (değişen dosyalar) | ✅ temiz |
| XML iyi-biçimli (`strings.xml` ×2) | ✅ |
| tree-sitter Kotlin parse (4 dosya) | ✅ 0 ERROR node |
| `R.string.*` referansları ↔ kaynak eşleşmesi | ✅ eksik yok |

> `🎌 Bangumi MASTER Doğrulama` workflow'u kırmızı, ancak **tüm dallarda** (dfc064fc,
> 4f65cf10, 10430a8e …) kırmızı — bu değişiklikle ilgisiz, sözlük üreticisi kaynaklı
> mevcut bir sorun.

---

## 6. Bilinen sınırlar / sonraki adımlar (opsiyonel)

1. **Sıralama modu kalıcı değil** — `rememberSaveable` ile yalnızca ekran/rotasyon
   boyunca hatırlanıyor. İstenirse `DataStore` anahtarına bağlanır.
2. **Liste eklenti başlıklarına bölünmüş** — sıralama gruplar arası doğru çalışıyor,
   başlıklar korunuyor. Tamamen düz liste için "Gruplama" anahtarı eklenebilir.
3. **Stremio için tek-eklenti retry yok** — ViewModel yalnızca CS eklentilerini
   yeniden çalıştırabiliyor; Stremio tarafı için task-registry eklenmesi gerekir.
   Şimdilik global "Yeniden ara" bu boşluğu kapatıyor.
