# PLAN / TASK — LİSTEM BOŞ IZGARA + REGEX ÇÖKMELERİ (2026-11-04)

İlgili kök neden raporu: `KOK_NEDEN_RAPORU_LISTEM_BOS_IZGARA_VE_REGEX_COKMELERI_2026-11-04.md`
Branch: `arena/f306ccf6-kitsugi-beta` · PR: #36 · Commit'ler: `68f2d1b`, `728f170`

## Görevler
- [x] Drive çökme raporlarını baştan sona tara (crash_history, Kitsugi_Crash_Report, logcat_at_crash, live_logcat_report, device_info).
- [x] Listem "844 sonuç ama boş" kök nedenini bul: `IntrinsicSize.Min + weight + fillMaxHeight + aspectRatio` → satır 0px.
- [x] `MyListGridContent.kt` gruplu + düz ızgara kollarını içerikle ölçülen satır + `Alignment.Top` yap.
- [x] `KitsugiMediaEntryCard.kt` ızgara rozet boşluğunu `weight(1f)` yerine sabit `height(4.dp)` yap.
- [x] `KitsugiTranslateUtils.kt` wikitext regex'ini ICU uyumlu yap (`\}\}`) + `safeTranslationRegex` güvenli yedek ekle.
- [x] `CsRuntimeInit.kt` eklenti interceptor'ına çökme kalkanı ekle (`LinkageError`/`RuntimeException` → `IOException`).
- [x] Ekran görüntüsüyle aktif düzenin `grid_2col` olduğunu teyit et.
- [x] Değişen dosyaları + bu plan/task'ı zip'leyip Drive'a yükle.
- [ ] Kullanıcı: güncel kaynaktan yeniden derleyip cihaza kur (aşağıya bak).
- [ ] Kullanıcı: cihazda Listem + DiziBox + çeviri akışını doğrula.

## Değişen dosyalar (bu zip'te tam yollarıyla)
1. `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListGridContent.kt`
2. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiMediaEntryCard.kt`
3. `app/src/main/java/com/kitsugi/animelist/utils/KitsugiTranslateUtils.kt`
4. `app/src/main/java/com/kitsugi/animelist/data/cloudstream/CsRuntimeInit.kt`
5. `KOK_NEDEN_RAPORU_LISTEM_BOS_IZGARA_VE_REGEX_COKMELERI_2026-11-04.md` (rapor)
6. `PLAN_TASK_LISTEM_IZGARA_VE_COKME_FIX_2026-11-04.md` (bu dosya)
7. `fix_listem_cokme_2026-11-04.patch` (git diff — alternatif uygulama)

## Kurulum / Uygulama
> ⚠️ Depo `2.4.340`, cihaz `2.4.410-beta`. Cihaz daha yeni bir kaynaktan derlenmiş.
> Bu yüzden **iki seçenek** var:

**Seçenek A — Temiz (önerilen):** Bu güncellenmiş depoyu (`arena/f306ccf6-kitsugi-beta` / PR #36)
çek, Android Studio'da derle, APK'yı cihaza kur.

**Seçenek B — Daha yeni kaynağa yama:** Elindeki `2.4.410` kaynağında dosyaları körü körüne
üzerine yazmak YENİ değişiklikleri silebilir. Bunun yerine zip'teki `.patch` dosyasını uygula:
```bash
cd <senin-kitsugi-kaynak-kökün>
git apply --check fix_listem_cokme_2026-11-04.patch   # önce dene
git apply fix_listem_cokme_2026-11-04.patch           # uygula
# Çakışma olursa ilgili 4 dosyadaki değişiklikleri elle taşı (rapora bak).
```
Tam dosyaları üzerine yazmak istersen zip'teki `app/...` yollarını kaynak köküne aç.

## Doğrulama (cihazda)
- [ ] Listem → `grid_2col` düzeni: kartlar görünüyor, sayaç ile liste tutarlı.
- [ ] Listem → diğer düzenler (comfortable/compact/large) bozulmadı.
- [ ] DiziBox / başka bir eklenti ile arama-oynatma: bozuk eklenti artık uygulamayı çökertmiyor.
- [ ] Detay sayfası çevirisi (wiki/biyografi): çökme yok; teknik değerler çevrilmiyor.
- [ ] Yatay tablet + dikey telefon: ızgara sütun sayıları doğru.

## Riskler / Notlar
- Sandbox'ta Android SDK/JDK yok → tam Gradle derlemesi yapılamadı; regex'ler Python `re` ile test edildi, sözdizimi gözden geçirildi.
- crash_history başındaki eski bir native çökmenin backtrace'i silinmiş; ona özgü düzeltme yapılamadı. Güncel iki regex çökme sınıfı kapatıldı.
- Izgara kartları artık üstten hizalı (eşit yükseklik yerine); posterler diğer ızgaralarla aynı hizada.