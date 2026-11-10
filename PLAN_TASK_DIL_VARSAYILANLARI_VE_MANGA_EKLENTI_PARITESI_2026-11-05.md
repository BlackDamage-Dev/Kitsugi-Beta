# PLAN & TASK — Dil Varsayılanları + Manga Eklenti Paritesi (v2.4.341)

**Tarih:** 2026-11-05
**Dal:** `arena/6a33b255-kitsugi-beta`
**Sürüm:** `2.4.341-beta.<timeVersionCode>`
**Durum:** ✅ Tamamlandı — CI yeşil (unit testler + `assembleFossDebug` + R8 `assembleFossRelease` + DEX bytecode doğrulaması)

---

## 1. Amaç (kullanıcı isteği)

1. **Ayarlar → Görünüm & Tercihler → Liste & Puan** bölümündeki üç dil satırı
   varsayılan olarak ekran görüntüsündeki gibi olsun:
   - *Anime & Manga Başlık Dili* → **İngilizce**
   - *Ekip & Karakter adı dili* → **İngilizce**
   - *Dizi & Film Başlık Dili (TMDB)* → **Türkçe**
2. **Manga eklentileri**, video (Cloudstream) eklentilerinin sunduğu tüm eklenti
   başına özellikleri sunsun:
   - her eklenti/depo için **yenileme butonu**
   - **otomatik güncelleme kontrolleri** (global + eklenti bazlı)
   - kurulum / güncelleme / yeniden kurulum / doğrulama / yönetim / kaldırma
   - toplu kurulum ve **toplu kaldırma**

Referans görseller:
- **Resim 1:** üç dil satırı (İngilizce / İngilizce / Türkçe)
- **Resim 2:** video sağlayıcı repo kartı — üstte yenile, altta indir / yenile / sil
- **Resim 3:** manga repo kartı — yalnızca daralt + sil (eksik olan kontroller)

---

## 2. Görev listesi

### A. Dil varsayılanları
| # | Görev | Dosya | Durum |
|---|-------|-------|-------|
| A1 | `ProfileAndListSettings.titleLanguage` varsayılanı `ROMAJI` → `ENGLISH` | `data/settings/AppSettings.kt` | ✅ |
| A2 | `staffNameLanguage` varsayılanı `ROMAJI` → `ENGLISH` | `data/settings/AppSettings.kt` | ✅ |
| A3 | DataStore fallback'leri tek sabitten okunsun (`DEFAULT_TITLE_LANGUAGE`, `DEFAULT_STAFF_NAME_LANGUAGE`, `DEFAULT_TMDB_LANGUAGE`) | `data/settings/SettingsDataStore.kt` | ✅ |
| A4 | TMDB dili için kalan `"en"` varsayılanını `"tr"` yap | `ui/screens/settings/SettingsScreenParameters.kt` | ✅ |
| A5 | Kullanıcı seçimi korunur (yalnızca hiç yazılmamış anahtarlarda varsayılan geçerli) | — | ✅ |

### B. Manga eklentileri — video paritesi
| # | Görev | Nerede | Durum |
|---|-------|--------|-------|
| B1 | Repo kartında **her zaman görünen Yenile** (yüklenirken spinner) | `MangaExtensionTab.kt` (repo başlığı) | ✅ |
| B2 | Repo kartı **alt eylem şeridi**: 🇹🇷 hızlı TR kurulumu + seçili dile göre toplu kurulum + oto-güncelleme çipi | `MangaExtensionTab.kt` | ✅ |
| B3 | Eklenti satırında **Yenile / Yeniden kur** (APK indir + belleğe al) | `MangaExtensionTab.kt` | ✅ |
| B4 | Eklenti başına **oto-güncelleme anahtarı** ve kalıcı depo | yeni `data/manga/MangaExtensionPrefs.kt` | ✅ |
| B5 | Arka plan güncelleyicisi bu anahtarlara **uysun** (kapalı eklenti atlanır) | `data/manga/MangaExtensionAutoUpdater.kt` | ✅ |
| B6 | Global ana anahtar (Kurulu sekmesi güncelleme kartı) — kapalıyken ağ isteği yok | `MangaExtensionTab.kt` + `MangaExtensionPrefs` | ✅ |
| B7 | Kurulu kaynak satırında eylem şeridi: Güncelle (vX) / Yeniden kur / Oto / Doğrula / Yönet + ⬆️ rozet | `MangaExtensionTab.kt` | ✅ |
| B8 | Kurulu kaynak → repo eklentisi eşlemesi (pkg normalize + isim fallback) | `MangaExtensionTab.kt` | ✅ |
| B9 | **Toplu kaldırma** (onay diyaloğu, video ile aynı akış) | `MangaViewModel.kt`, `MangaExtensionTab.kt` | ✅ |
| B10 | Toplu kaldırma callback'inin 5 katmandan geçirilmesi | `MangaSettings`, `SettingsScreen`, `KitsugiAddonsSettingsDialog`, `AppRootSettingsExtras` | ✅ |
| B11 | TR/EN dizeleri (8 yeni anahtar + 2 toplu silme metni) | `res/values/strings.xml`, `res/values-en/strings.xml` | ✅ |

---

## 3. Teknik tasarım notları

### 3.1 Tercih deposu (`MangaExtensionPrefs`)
`MangaExtensionAutoUpdater` uygulama açılışında UI'dan bağımsız çalıştığı için
senkron okunabilen `SharedPreferences` (`manga_extension_prefs`) kullanıldı.
DataStore akışı burada uygun değil (Worker/başlangıç yolu askıya alınamaz).

Anahtarlar:
- `auto_update_enabled` → global ana anahtar (varsayılan **açık**)
- `auto_update_disabled_pkgs` → otomatik güncellemesi kapatılmış paket adları (StringSet)

Ayırt edilen iki API:
- `isAutoUpdateEnabled(ctx, pkg)` → **global ∩ eklenti** (güncelleyici bunu kullanır)
- `isExtensionSwitchOn(ctx, pkg)` → yalnızca eklenti tercihi (UI çipi bunu gösterir;
  global anahtar kapalıyken bile kullanıcı niyeti görünür kalır)

Paket adları `normalize()` ile `nokta → alt çizgi` + `lowercase` eşlenir; böylece
`eu.kanade.tachiyomi.extension.tr.mangatr` ile `eu_kanade_...` aynı anahtara düşer.

### 3.2 Güncelleyici davranışı
```
runIfNeeded(forceCheck=false)
  ├─ 24 saat kuralı (mevcut)
  ├─ global anahtar kapalı  → Skipped (ağ isteği yok)
  └─ her paket için:
       ├─ eklenti anahtarı kapalı → atla (skippedByPreference++)
       └─ versionCode > kurulu  → indir + yeniden yükle
Özet mesajı: güncellendi / tümü güncel / hepsi kapalı / güncellenecek yok
```

### 3.3 Repo → eklenti eşlemesi (kurulu sekmesi)
Kurulu `MangaSource.pkgName` doğrudan repodaki `pkg` ile eşleşmeyebilir.
Bu yüzden:
1. `pkg` normalize edilerek aranır,
2. bulunamazsa **isim** ile aranır.
Eşleşme varsa "Güncelle/Yeniden kur" ilgili APK'yı indirir; yoksa "Yenile"
butonu mirror yenilemeye (`onRefreshSourceMirror`) düşer.

### 3.4 UI yoğunluğu
Satır içi `Switch` (52×32 dp) repo/eklenti satırlarında fazla yer kapladığı için
kompakt `MangaSwitchChip` bileşeni eklendi (seçiliyken `✓ Oto`, aksi halde `Oto`).
Kurulu kaynak eylem şeridi `horizontalScroll` ile taşma yapmaz.

---

## 4. Değişen dosyalar (14)

```
app/build.gradle.kts                                        (v2.4.341)
app/src/main/java/com/kitsugi/animelist/AppRootSettingsExtras.kt
app/src/main/java/com/kitsugi/animelist/data/manga/MangaExtensionAutoUpdater.kt
app/src/main/java/com/kitsugi/animelist/data/manga/MangaExtensionPrefs.kt   (YENİ)
app/src/main/java/com/kitsugi/animelist/data/settings/AppSettings.kt
app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt
app/src/main/java/com/kitsugi/animelist/ui/app/MangaViewModel.kt
app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiAddonsSettingsDialog.kt
app/src/main/java/com/kitsugi/animelist/ui/components/addons/MangaExtensionTab.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/settings/SettingsScreen.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/settings/SettingsScreenParameters.kt
app/src/main/res/values/strings.xml
app/src/main/res/values-en/strings.xml
release_notes_v2.4.341.md                                    (YENİ)
```

## 5. Yeni dize anahtarları

| Anahtar | TR | EN |
|---|---|---|
| `manga_auto_update_toggle` | Oto-güncelleme | Auto-update |
| `manga_auto_update_short` | Oto | Auto |
| `manga_auto_update_global_title` | Otomatik güncelleme (tüm eklentiler) | Auto-update (all extensions) |
| `manga_auto_update_global_desc` | …günde bir kez denetlenir… | …checked once a day… |
| `manga_auto_update_all_disabled` | 🔕 Tüm eklentiler için otomatik güncelleme kapalı (%1$d eklenti atlandı) | 🔕 Auto-update is off for all extensions (%1$d skipped) |
| `manga_extension_refresh` | Eklentiyi yenile | Refresh extension |
| `manga_repo_refresh` | Depoyu yenile | Refresh repository |
| `manga_eklenti_toplu_sil_mesaj` | %1$d kurulu manga eklentisinin tamamı… | All %1$d installed manga extensions… |
| `manga_eklenti_toplu_sil_sonuc` | 🗑️ %1$d manga eklentisi kaldırıldı | 🗑️ %1$d manga extensions removed |

---

## 6. Doğrulama / test

- `./gradlew :app:testFossDebugUnitTest --tests '…'` → **PASS** (mevcut testler; dil
  varsayılanlarına bağlı assertion yok, regresyon yok)
- `:app:assembleFossDebug` → **PASS**
- `:app:assembleFossRelease` (R8) → **PASS**
- `scripts/verify_apk_dex_bytecode.py` (debug + release DEX) → **PASS**
- `🎌 Bangumi MASTER Doğrulama` iş akışı (tam Kotlin derlemesi + testler) → **PASS**
- CI çalışması: `actions/runs/42098688615`

Manuel test önerisi (cihazda):
1. Ayarlar → Liste & Puan: üç satırın **İngilizce / İngilizce / Türkçe** açıldığını gör.
2. Eklentiler → Manga Kaynakları → repo kartı: **Yenile** ikonuna bas, spinner + liste tazelenir.
3. Aynı kartta **Oto-güncelleme** çipini kapat; Kurulu sekmesi → "Şimdi Güncelle" → mesajda
   "%d eklenti atlandı" satırı görünür.
4. Kurulu bir kaynakta **Güncelle (vX)** / **Yeniden kur** / **Doğrula** / **Yönet** akışlarını dene.
5. **Tümünü Sil** onayı → kaynak listesi boşalır, mesajda kaldırılan sayı yazar.

## 7. Riskler ve geri alma

- **Risk:** Eklenti başına anahtar `SharedPreferences`'ta; kullanıcı "tüm anahtarları sıfırla"
  isterse `MangaExtensionPrefs.resetPerExtensionSwitches(ctx)` mevcut (şu an UI'da buton yok).
- **Risk:** `repoExtensionByName` isim çakışmasında yanlış APK indirebilir; sıra önce
  `pkg` eşleşmesi olduğu için pratikte çakışma düşük.
- **Geri alma:** Tek commit; `git revert` yeterli. Dizeler additive olduğu için eski
  sürümlerle çakışmaz.

## 8. Sonraki adımlar (opsiyonel)

- Dil listesinin yalnızca **İngilizce/Türkçe** olarak daraltılması (istenirse) —
  AniList'te Türkçe başlık verisi olmadığından otomatik çeviri hattına bağlanmalı.
- Manga kaynakları için `toggleMangaSource` (etkin/devre dışı) anahtarının Ayarlar
  ekranına taşınması: `getAvailableSources()` `Disabled` kaynakları filtrelediği için
  ayrı bir "tüm kaynaklar" listesi gerekir (TV tarafında mevcut).
