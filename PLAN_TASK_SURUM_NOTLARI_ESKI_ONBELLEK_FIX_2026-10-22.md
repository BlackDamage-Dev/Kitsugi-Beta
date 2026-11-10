# PLAN/TASK — Hakkında ekranında eski sürüm notlarının gösterilmesi

**Tarih:** 2026-10-22  
**Sürüm:** 2.4.340 → **2.4.347**  
**Durum:** ✅ Kod ve test kapsamı eklendi

## Sorun

Ekran görüntüsünde kurulu uygulama `2.4.346-beta.29878010` iken Hakkında kartı `v2.4.169` sürüm notlarını gösteriyor. GitHub Releases API'sindeki güncel son sürüm `v2.4.346`; ekrandaki notlar güncel değil.

## Kök neden

1. Sürüm notu önbelleği yalnızca altı saatlik zaman damgasına göre geçerlilik kontrolü yapıyordu. Önbellek uygulama sürümü yükseltmelerine bağlanmadığı için eski uygulama sürümünden kalan kayıt yeni APK'da da kullanılabiliyordu.
2. Hakkında kartı, GitHub isteği başarısız olduğunda gecikebilen Codeberg aynasına düşüyordu. Ayna yanıtı doğrulanmadan "en son sürüm" olarak gösterilebiliyordu.
3. Önbellek zaman damgası cihaz saatinin ilerisindeyse kayıt süresiz taze kabul edilebiliyordu.

## Plan / tamamlanan işler

- [x] Hakkında ekranının sürüm notlarını kanonik GitHub Releases API'sinden almasını sağla.
- [x] Güncelleme denetimindeki Codeberg yedeğini koru; yalnızca Hakkında sürüm notlarında eski ayna verisinin kullanılmasını engelle.
- [x] Önbelleği tam kurulu uygulama sürümüne bağla; eski uygulama sürümüne ait ve eski biçimli kayıtları geçersiz kıl.
- [x] Geleceğe tarihli veya süresi dolmuş önbellek kayıtlarını reddet.
- [x] TTL, sürüm değişimi, eski kayıt ve gelecek zaman damgası senaryoları için birim testleri ekle.
- [x] Uygulama sürümünü `2.4.347` yap; sürüm notlarını ve yayın iş akışının varsayılan sürümünü eşitle.
- [x] Değişiklik kontrolü ve yerelleştirme kaynak eşitliğini doğrula.

## Değişen dosyalar

- `.github/workflows/release.yml`
- `RELEASE_NOTES.md`
- `app/build.gradle.kts`
- `app/src/main/java/com/kitsugi/animelist/core/update/KitsugiUpdateRepository.kt`
- `app/src/main/java/com/kitsugi/animelist/core/update/ReleaseNotesCache.kt`
- `app/src/test/java/com/kitsugi/animelist/core/update/ReleaseNotesCacheTest.kt`
- `release_notes_v2.4.347.md`
- `PLAN_TASK_SURUM_NOTLARI_ESKI_ONBELLEK_FIX_2026-10-22.md` (bu plan/task dosyası)

## Doğrulama

- `git diff --check` — geçti.
- `python3 scripts/check_localization_resource_parity.py` — geçti (17.696 metin ve 4 dizi eşleşiyor).
- Gradle testleri çalıştırılamadı; çalışma ortamında Java/JDK yüklü değil.

## Beklenen sonuç

Yeni `2.4.347` APK'sı kurulduğunda önceki sürümden kalan `v2.4.169` önbelleği kullanılmaz. Hakkında kartı GitHub'daki güncel sürüm notlarını gösterir; GitHub'a erişilemiyorsa Codeberg'den eski notları güncelmiş gibi sunmak yerine içeriği yükleyemez ve yeniden deneme olanağı verir.
