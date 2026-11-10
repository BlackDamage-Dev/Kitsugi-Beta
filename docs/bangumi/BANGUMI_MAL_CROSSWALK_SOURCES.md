# Bangumi ↔ MyAnimeList paketlenmiş eşleme verisi

## Kaynak ve sürüm

- Birleştiren proje: https://github.com/LinkPass888/bangumi-mal
- Kullanılan dosyalar:
  - `data/bangumi-mal.min.json` → `app/src/main/res/raw/bangumi_mal_crosswalk.json`
  - `data/conflicts.json` → `app/src/main/res/raw/bangumi_mal_conflicts.json`
- Yukarı akım veri commit'i: `b2c0f44c8aeb9b443700627005dce06534af71e1`.
- Yukarı akım `data/meta.json` üretim zamanı: **2026-10-19T09:35:51Z**.
- SHA-256: crosswalk `655adc912fab75c5c8f36dc8396f84716fbab1c715c89027cb98cb95f89d16a0`; conflicts `b7ac451c43147287b36a59e179d58ff12a643bc69b79e1cc874cbc08c339795f`.
- Veri kümesi: `23.527` Bangumi satırı, `13.270` null-olmayan Bangumi→MAL çifti, `77` açık çelişki kaydı.
- Özgün veri kaynakları:
  - https://github.com/Rhilip/BangumiExtLinker
  - https://github.com/bangumi-data/bangumi-data

## Lisans ve atıf

Birleştiren projenin `LICENSE` dosyası kod için MIT, `data/` altındaki türetilmiş veri için **Creative Commons Attribution 4.0 International (CC BY 4.0)** belirtir. Özgün kaynaklar da CC BY 4.0 olarak belgelenmiştir.

- Lisans metni: https://creativecommons.org/licenses/by/4.0/
- Atıf: **LinkPass888/bangumi-mal; Rhilip/BangumiExtLinker; bangumi-data/bangumi-data**.
- Değişiklik: Uygulamada yalnız null-olmayan çiftler okunur. `conflicts.json` içindeki her kaydın hem `chosen` hem `rejected` çifti çıkarılır; çakışma taraflarından biri otomatik seçilmez. JSON dosyalarının içeriği başka biçimde değiştirilmemiştir.

## Güvenlik sınırı

Bu veri resmî Bangumi/MAL API alanı değildir ve tek başına yazma yetkisi sayılmaz. Yalnız aday üretir:

1. MAL→Bangumi adayı, Bangumi subject API yanıtında başlık aliası, subject türü ve yıl ile yeniden doğrulanır.
2. Bangumi→MAL adayı, MAL kataloğu `CrossSyncIdentityGuard.verifyMalId` kontrolünden geçmeden kullanılmaz.
3. Ağ/katalog doğrulaması yapılamazsa veya birden çok belirsiz aday kalırsa kayıt atlanır.
4. Bilinen çelişkilerin iki tarafı da otomatik yazım dışıdır.

## Yenileme

Veri güncellenirken yukarı akım commit'i, `meta.json`, dosya SHA-256 değerleri ve çelişki sayısı bu belgeye işlenmeli; parser ve gerçek-çelişki regresyon testleri yeniden çalıştırılmalıdır.
