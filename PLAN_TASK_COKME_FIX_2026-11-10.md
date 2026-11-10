# PLAN & TASK — Çökme Düzeltme Paketi (2026-11-10)

**Kaynak:** Drive `1Nf8iKsXA9q6oOBswR-6nQ6LBcBvJXiRS` (13 dosya, 2 oturum)
**Dal:** `arena/e60c1f6e-kitsugi-beta`
**Ana rapor:** `COKME_RAPORU_2026-11-10_DRIVE_CRASH_ANALIZI_VE_FIX.md`

---

## Görev listesi

| # | Görev | Durum | Dosya |
|---|-------|-------|-------|
| T1 | Loglardan çökme imza envanteri çıkar | ✅ | 13 dosya (2 oturum) |
| T2 | LazyList tekrarlı anahtar çökmesini kapat (`Key "1622_20"`) | ✅ | `DownloadsScreen.kt`, `WatchHistoryScreen.kt`, `KitsugiLazyKeys.kt` |
| T3 | Manga kaynak kimliği ↔ aktif domain özyinelemesini kapat (`StackOverflowError`) | ✅ | `MangaSourceIdentity.kt`, `SourceConfigStore.kt`, `MihonSourceWrapper.kt` |
| T4 | Log yankısı (echo) ve dosya şişmesini kapat | ✅ | `LogEchoGuard.kt` (yeni), `FileLoggingTree.kt` |
| T5 | Kırıntı (breadcrumb) tekrarlarını ayıkla | ✅ | `KitsugiSessionSupervisor.kt` |
| T6 | Derleme doğrulaması (`./gradlew :app:assembleBetaDebug`) | ⏳ Bekliyor | — |
| T7 | Cihaz testi (indirme + manga domain + uzun oynatma) | ⏳ Bekliyor | — |
| T8 (TAMAMLANDI) | Kalan LazyList anahtarlarını topluca sertleştirme (opsiyonel) | ⏳ Bekliyor | `MangaBrowseScreen`, `MangaExtensionTab` … |

---

## T6 — Derleme kontrolü

```bash
./gradlew :app:assembleBetaDebug
# hata olursa ilgili dosya + satırı paylaş
```

## T7 — Cihaz testi (kabul kriterleri)

1. **İndirilenler:** Aynı bölümü 2 farklı kalitede indir → **İndirilenler → Videolar** aç.
   - Beklenen: çökme yok (`Key "…" was already used` görünmemeli), iki satır ayrı ayrı listelenir.
2. **Altyazılar sekmesi:** Aynı dosya iki klasörde varsa da açılmalı.
3. **Manga:** Mihon/Keiyoushi kaynağı seç → arama yap → kaynak ayarlarından alan adı değiştir.
   - Beklenen: `StackOverflowError` yok, alan adı kalıcı (uygulama kapanıp açıldığında korunur).
4. **Oynatıcı:** 1–2 saat oynat, arka plana al, tekrar aç.
   - Beklenen: `kitsugi_debug.log` ≤ 5 MB ve artmıyor; `crash_history` içinde yığın kopyası yok.
5. **Bellek:** Android Studio Memory Profiler → galeri (300+ görsel) gezin + oynatıcı aç.
   - Beklenen: PSS 350 MB altında kalıyor, `onTrimMemory` temizliği çalışıyor.

## T8 (TAMAMLANDI) — Kalan anahtar riski (opsiyonel)

- `ui/screens/manga/MangaBrowseScreen.kt` → `items(states, key = { it.source.stableSourceKey() })`
- `ui/components/addons/MangaExtensionTab.kt` → `items(sources, key = { "${'$'}{src.name}_${'$'}{src.lang}" })`

Bu iki liste, kimliği benzersiz olmayan kaynaklarda (aynı paket/adres) çakışabilir.
`uniqueLazyKeys()` ile sertleştirilmesi önerilir.

---

## Geri alma (rollback)

Bu paketteki tüm değişiklikler tek commit'te:
```bash
git revert ace61fd   # veya: git revert a5f5dad ace61fd
```
