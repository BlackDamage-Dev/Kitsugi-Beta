# diag/taxonomy_master — Ortak tür / etiket sözlüğü

Bu klasör, uygulamanın **yedi kaynaktan** (AniList, MAL/Jikan, Bangumi, Shikimori, Kitsu,
Simkl, TMDB) topladığı tür ve etiketleri tek kanonik anahtarda birleştiren sözlüğün
çalışma alanıdır.

## Tek doğruluk kaynağı

| Katman | Dosya | Not |
|---|---|---|
| **Rusça** (Shikimori) | `app/src/main/java/com/kitsugi/animelist/utils/RussianTaxonomyDictionary.kt` | Kod içinde; dil dosyası override'ı `taxonomy_tag_*` |
| **Çince / Japonca** (Bangumi) | `app/src/main/java/com/kitsugi/animelist/utils/BangumiTagDictionary.kt` | Kod içinde; override `bangumi_tag_*` |
| **İngilizce** (AniList/MAL/Kitsu/Simkl/TMDB) | `KitsugiTranslations.genreMap` + `SearchTranslation.englishToTurkish` | Mevcut tablolar korunur |

Rusça sözlük **Shikimori `/api/genres` resmî listesinin tamamını** (79 tür) kapsar; MAL tür
kimlikleri (`malGenreId`) ayrıca saklanır, böylece beklenmedik bir Rusça yazım gelse bile
doğru karşılık bulunur.

## Betikler

```bash
# Sözlüğü denetle (K1–K9) + çeviri iş listesini üret
python3 scripts/check_taxonomy_master.py --worklist

# Dil dosyalarını sözlükle eşitle
python3 scripts/sync_taxonomy_tag_strings.py

# Uyum denetimi (CI)
python3 scripts/sync_taxonomy_tag_strings.py --check
python3 scripts/sync_bangumi_tag_strings.py --check
python3 scripts/check_localization_resource_parity.py
```

> **Uyarı:** `sync_bangumi_tag_strings.py` eski sürümlerde işaretçiden sonraki **tüm** dosyayı
> silip yeniden yazıyordu (yaklaşık 8.800 satır). Bu sürümde blok **yerinde** yeniden
> yazılıyor; yine de çalıştırmadan önce `git status` ile temiz çalışma ağacı doğrulanmalı.

## Denetim sınıfları

| Kod | Denetim |
|---|---|
| K1 | Anahtar tekilliği |
| K2 | Takma ad çakışması (bir Rusça yazım iki girişe bağlanamaz) |
| K3 | Boş TR/EN |
| K4 | TR/EN içinde Kiril harf |
| K5 | Shikimori'nin resmî tür kapsamı (79) |
| K6 | Dil dosyası eşitliği (TR/EN + sözlük değerleri) |
| K7 | Android kaynak adı biçimi (`a-z0-9_`) |
| K8 | Bangumi sözlüğüyle TR/EN çelişkisi |
| K9 | MAL tür kimliği tekilliği |

## worklist.tsv

`scripts/check_taxonomy_master.py --worklist` çıktısıdır: her satır bir sözlük girişi
(`mal_id`, `key`, `turkish`, `english`, `aliases`, `status`). Eksik resmî tür varsa
`status = EKSIK` ile sona eklenir. Çeviri ekibi bu dosyayı düzeltip sözlüğe işler;
dil dosyası override'ı sayesinde kod değişikliği **gerekmez**.

Bu dosya Google Drive'a yüklenir (`Kitsugi_Ortak_Taksonomi_v2.4.339.zip` içinde).

## Yeni dil ekleme

`toLocalizedTaxonomyLabel()` sırası: Bangumi → Rusça → İngilizce → Kiril transliterasyonu →
özgün metin. Yeni bir kaynak eklendiğinde yalnızca `taxonomyEntryOrNull()` içine bir sözlük
kontrolü eklenir; gösterim katmanı ve birleştirme motoru değişmez.
