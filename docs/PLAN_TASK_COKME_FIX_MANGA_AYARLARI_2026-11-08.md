# PLAN & TASK — Manga/Ayarlar Çökmesi (iç içe dikey kaydırma) · 2026-11-08

**Branch:** `arena/aee3a35b-kitsugi-beta` · **PR:** #41 · **Durum:** ✅ Tamamlandı (cihaz testi bekliyor)

---

## 0. Özet

| Alan | Değer |
|---|---|
| Hata | `java.lang.IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints` |
| Nerede | Manga ayarları / eklenti-kaynak ayarları / ayar alt sayfaları / detay sekmeleri / wiki okuyucu (sheet + pager kombinasyonları) |
| Kök neden | Bir `Column(Modifier.verticalScroll)` içine ikinci bir dikey kaydırma konteyneri giriyor → üst konteyner çocuklarını `maxHeight = Infinity` ile ölçüyor → Compose süreci öldürüyor |
| Kanıt | `Kitsugi_Crash_Report (2026-11-07 19:46:19)`, `logcat_at_crash`: `ScrollNode.measure` → `ScrollNode.measure` → `DraggableAnchorsNode.measure` (ModalBottomSheet) |
| Çözüm | Kaydırma düğümüne **hiçbir zaman sınırsız yükseklik verilmemesi** (merkezî güvenli modifier) + tüm çağrı noktalarının taşınması |

---

## 1. Görev listesi

| # | Görev | Durum | Dosya / kapsam |
|---|---|---|---|
| T1 | Crash log'larını (Drive) indir, imzayı çıkar | ✅ | `Kitsugi_Crash_Report`, `logcat_at_crash`, `kitsugi_debug`, `crash_history` |
| T2 | Stack'i okuyup hiyerarşiyi çöz (sheet → Column(verticalScroll) → Column(verticalScroll)) | ✅ | — |
| T3 | Kod tabanında iç içe kaydırma envanteri (statik tarama: fonksiyon çağrı grafiği + lexiksel iç içelik) | ✅ | `app/src/main/java/**` |
| T4 | `HorizontalPager` sayfalarının `Constraints.Infinity` ile ölçüldüğü 8 noktayı tespit et | ✅ | `DetailPageScaffold`, `CharacterDetailPage`, `StaffDetailPage`, profil ekranları |
| T5 | Çökme korumalı kaydırma yardımcısını yaz | ✅ | **YENİ:** `ui/utils/KitsugiSafeScroll.kt` |
| T6 | Tüm `Modifier.verticalScroll(...)` çağrılarını güvenli sürüme taşı | ✅ | 59 dosya / 100+ çağrı |
| T7 | Kullanılmayan `androidx.compose.foundation.verticalScroll` import'larını temizle | ✅ | 60 dosya |
| T8 | Kalan ham `.verticalScroll(` olmadığını doğrula (grep) | ✅ | 0 sonuç |
| T9 | `MangaSourceHealthScreen` içindeki `editingDomainSource!!` NPE riskini kaldır | ✅ | `MangaSourceHealthScreen.kt` |
| T10 | Kök neden + çözüm raporu | ✅ | `COKME_FIX_MANGA_AYARLARI_IC_ICE_KAYDIRMA_2026-11-08.md` |
| T11 | Commit + push + PR | ✅ | PR #41 |
| T12 | **Cihazda doğrulama** (aşağıdaki senaryolar) | ⏳ Bekliyor | — |
| T13 | (Gerekirse) Lazy* bileşenleri için `kitsugiClampUnboundedHeight()` uygulaması | ⏳ Opsiyonel | `KitsugiSafeScroll.kt` içinde hazır |

---

## 2. Teknik çözüm

### 2.1 Yeni yardımcılar — `ui/utils/KitsugiSafeScroll.kt`

```kotlin
Modifier.kitsugiVerticalScroll(state: ScrollState): Modifier
Modifier.kitsugiClampUnboundedHeight(): Modifier
```

* `verticalScroll` düğümünün **dışına** bir `layout` düğümü eklenir.
* `constraints.hasBoundedHeight == true` → kısıtlar **aynen** geçirilir (sıfır davranış değişikliği).
* `hasBoundedHeight == false` → `maxHeight` **ekran yüksekliğine** kırpılır → `checkScrollableContainerConstraints`
  asla tetiklenmez, kaydırma çalışmaya devam eder.
* `kitsugiClampUnboundedHeight()`: kaydırma eklemeyen sürüm; kendi kaydırmasını yapan
  `LazyColumn` / `LazyVerticalGrid` gibi bileşenler için.

### 2.2 Değişiklik deseni (her dosyada 1–2 satır)

```diff
- .verticalScroll(scrollState)
+ .kitsugiVerticalScroll(scrollState)
+ import com.kitsugi.animelist.ui.utils.kitsugiVerticalScroll
- import androidx.compose.foundation.verticalScroll
```

---

## 3. Etkilenen alanlar (özet)

* **Manga:** `MangaScreen`, `MangaSourceHealthScreen`, okuyucu/keşfet yolları
* **Ayarlar:** `KitsugiPreferencesSettingsDialog` (Görünüm / Liste-Puan / **Manga Okuyucu** sekmeleri),
  `KitsugiSystemSettingsDialog`, `KitsugiIntegrationsSettingsDialog`, `StorageSettingsContent`,
  `AccountSettingsSubPages`, `SettingsScreen`
* **Eklenti/kaynak:** `MangaExtensionTab`, `KitsugiAddonsSettingsDialog` (Manga sekmesi), `SettingsScreen`
* **Detay/Wiki:** `DetailPageScaffold`, `ApiResultDetailPage`, `MediaEntryDetailPage`, `EntryDetailTabContents`,
  `CharacterDetailPage`, `StaffDetailPage`, `FandomWikiReader`, `MediaWikiTabContent`, `WikiPageTools`
* **Diğer:** galeri/medya sheet'leri, stream ekranı, bildirimler, profil ekranları, TV ekranları

Tam liste: zip içindeki `DEGISEN_DOSYALAR_LISTESI.txt`

---

## 4. Cihazda doğrulama senaryoları

- [ ] Ayarlar → Eklentiler/Kaynaklar → **Manga** sekmesi → Repolar / Kurulu alt sekmeleri
- [ ] Ayarlar → Tercihler → **Manga Okuyucu** sekmesi (yön, renk filtresi, sığdırma, parlaklık) + açılır seçim pencereleri
- [ ] Manga okuyucu → ⚙ → Okuma Modu Ayarları paneli (yön/filtre/sığdırma/parlaklık)
- [ ] Manga keşfet → boş kaynak uyarısı → "Ayarlar" → Manga kaynakları dialogu
- [ ] Manga keşfet → kaynak yönetimi: Repolar, Sağlık raporu, domain düzenleme dialogu
- [ ] Detay sayfası sekmeleri (bilgi/karakter/wiki) — **portre ve yatay**
- [ ] Wiki okuyucu (sade görünüm) + sayfa araçları sheet'i
- [ ] Hakkında → sürüm notları kartı (kendi içinde kaydırma)
- [ ] TV düzeni (varsa) aynı ekranlar

**Beklenen:** Hiçbir adımda `Vertically scrollable component was measured with an infinity maximum height constraints` yok; kaydırma hissi/performansı öncekiyle aynı.

---

## 5. Risk & geri alma

* **Risk:** Düşük. Sınırlı kısıtlarda kod yolu birebir eskisiyle aynı; yalnızca *zaten çökecek* yerleşimlerde
  yükseklik ekran yüksekliğine kırpılır (çökme yerine çalışan kaydırma).
* **Geri alma:** Commit'i revert etmek yeterli (`fix(ui): iç içe dikey kaydırma çökmesini kalıcı olarak engelle`).
* **Derleme notu:** Bu ortamda Android SDK olmadığı için derleme yapılamadı; değişiklikler mekanik ve
  imza korumalıdır (tek parametreli `verticalScroll` → aynı imzalı yardımcı).

---

## 6. Paket içeriği

```
PLAN_TASK_COKME_FIX_MANGA_AYARLARI_2026-11-08.md   ← bu dosya
COKME_FIX_MANGA_AYARLARI_IC_ICE_KAYDIRMA_2026-11-08.md ← kök neden + çözüm raporu
degisen_dosyalar.patch      ← 65 dosyanın tam diff'i (git apply ile uygulanır)
yeni_dosyalar/KitsugiSafeScroll.kt ← yeni yardımcı (tam içerik)
DEGISEN_DOSYALAR_LISTESI.txt       ← değişen dosya yolları
UYGULAMA_ADIMLARI.txt              ← patch'i uygulama / geri alma komutları
```
