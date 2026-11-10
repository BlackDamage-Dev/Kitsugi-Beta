# MDBList Kota Takibi ve "Kota Doldu" Uyarısı — 2026-10-22

## İstek

> MDBList API anahtarına özgü ne kadar kotası kaldığını bilmeli; API anahtarı girili ve
> özellik açıksa hem Ayarlar → Harici Entegrasyonlar → MDBList sekmesinde hem de detay
> sayfasındaki MDBList puan bloğunun bulunduğu yerde kalan kota gösterilmeli. Kota bitince
> bittiğine dair uyarı verilmeli. Ücretli/ücretsiz hesap ayrımı yapılmamalı.

## Kök neden

Uygulama MDBList'ten yalnızca puan çekiyordu; gelen yanıttaki `X-RateLimit-*` başlıklarını
ve `/user` uç noktasındaki limit bilgisini hiçbir yerde tutmuyordu. Bu yüzden:

- Kaç istek kaldığı bilinmiyordu.
- Günlük kota bittiğinde puanlar sessizce kayboluyordu (kart hiç çizilmiyordu), kullanıcı
  bunun nedenini göremiyordu.
- "Kota doldu" durumunda her detay sayfasında yeniden 429 üreten gereksiz istekler atılıyordu.

## Çözüm

### 1. Kota merkezi — `data/remote/MdbListQuota.kt` (yeni)

`MdbListQuotaCenter`, süreç içinde tek bir `StateFlow<MdbListQuota?>` yayınlar.

- **Anahtara özgü:** kayıt, anahtarın SHA-256 parmak iziyle etiketlenir. Anahtar değişince
  eski sayı gösterilmez, yeni anahtarın kaydı yüklenir.
- **Kalıcı:** `kitsugi_mdb_list_quota` SharedPreferences dosyasında saklanır; uygulama
  yeniden açıldığında son bilinen kota hemen görünür.
- **Sıfırlanma:** MDBList limiti 00:00 UTC'de yenilenir. `MdbListQuota.rolledOver()`
  sıfırlanma anı geçtiyse bayat `0` değerini limite çeker, bir sonraki UTC gece yarısını
  hesaplar. Bilinmeyen alanlar (`null`) asla uydurma sayıyla doldurulmaz.
- **Tek uçuş:** `/user` doğrulaması mutex + `maxAgeMillis` ile kısıtlanır; aynı anda birden
  fazla ekranın istek atması engellenir.

### 2. İstemci — `data/remote/MdbListClient.kt`

- `fetchQuota(apiKey)`: `https://api.mdblist.com/user?apikey=…` çağırır. Hem gövdeyi
  (`api_requests` = limit, `api_requests_count` = kullanılan, `patron_status`) hem de
  `X-RateLimit-*` başlıklarını okur. Başlıklar gövdeden önceliklidir (gerçek zamanlı).
- Puan araması ve anahtar doğrulaması da her yanıtta `X-RateLimit-*` başlıklarını merkeze
  yazar; böylece ek istek olmadan kota güncellenir.
- `429` alındığında kalan `0` olarak işaretlenir, `X-RateLimit-Reset` ya da `Retry-After`
  ile sıfırlanma anı kaydedilir.
- Kota bittiyse puan araması ağa hiç çıkmaz; önbellekteki son bilinen puanlar (süresi
  geçmiş olsa bile) gösterilir. Böylece 429 yağmuru ve gereksiz gecikme olmaz.
- Anahtar doğrulamada `429`, "geçersiz anahtar" değil "anahtar geçerli, kota bitti" kabul
  edilir. Aksi hâlde kotası dolu geçerli bir anahtar kullanıcıya "geçersiz" görünüyordu.
- Ayrıştırma fonksiyonları (`parseQuotaHeaders`, `parseUserQuota`, `normalizeEpochSeconds`)
  ağdan bağımsız ve `internal`; birim testleriyle doğrulanır.

### 3. Ayarlar sayfası — MDBList sekmesi

API anahtarı satırının altında yeni **API Kotası** paneli:

- `998 / 1.000 istek kaldı` + oran çubuğu + `Sıfırlanma: 23 Eki 00:00` (yerel saat).
- Plan rozeti (`Ücretsiz` / `Destekçi`) — yalnızca bilgi amaçlı, **kota hesabı bu rozete
  göre yapılmaz**.
- Sağ üstte yenile düğmesi; sekme açıldığında otomatik tazelenir (5 dk kısa devre).
- Kota bitince çubuk yerine kırmızı uyarı bloğu: *Günlük API kotası doldu — harici puanlar
  … itibarıyla yeniden yüklenecek.*
- Kota okunamazsa uydurma sayı yerine "Kota bilgisi alınamadı" + yenile düğmesi.
- Kota ilk kez tükendiğinde tek seferlik bilgi balonu (Toast) çıkar. Detay kartı ve ayarlar
  paneli aynı bayrağı paylaştığı için iki balon üst üste çıkmaz; sonraki gün kota yeniden
  dolarsa uyarı yine bir kez gösterilir.

### 4. Detay sayfası — "Harici Puanlar" kartı

- Başlıktaki `MDBList` rozetinin yanında küçük kota rozeti: `998/1.000` (nötr),
  yüzde 10 ve altında turuncu, tükendiğinde kırmızı **Kota doldu**.
- Kota bittiyse kartın içinde kırmızı uyarı satırı çizilir.
- Puan bulunamadığı için kart normalde tamamen gizlenirken, kota bittiyse uyarı yine
  gösterilir; kullanıcı puanların neden gelmediğini görebilir.
- Her iki detay ViewModel'i (API sonucu ve kütüphane kaydı) anahtarı bağlar ve `/user`
  doğrulamasını en fazla 10 dakikada bir çalıştırır.

## Ücretsiz / ücretli hesap

Hiçbir yerde sabit limit yok. Limit her zaman API'den gelir:

| Plan | Günlük limit (MDBList) |
|---|---|
| Ücretsiz | 1.000 |
| 1 € | 10.000 |
| 2 € | 25.000 |
| 3 € | 100.000 |
| 5 € VIP | 250.000 |

Bu sayılar yalnızca dokümantasyon amaçlıdır; kod `api_requests` / `X-RateLimit-Limit`
değerini kullanır.

## Değişen dosyalar

- `app/src/main/java/com/kitsugi/animelist/data/remote/MdbListQuota.kt` *(yeni)*
- `app/src/main/java/com/kitsugi/animelist/data/remote/MdbListClient.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiIntegrationsSettingsDialog.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailComponents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/MediaEntryDetailViewModel.kt`
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-en/strings.xml`
- `app/src/test/java/com/kitsugi/animelist/data/remote/MdbListClientTest.kt`
- `app/src/test/java/com/kitsugi/animelist/data/remote/MdbListQuotaTest.kt` *(yeni)*

## Doğrulama

- `scripts/check_localization_resource_parity.py` → 17.709 string, TR/EN yer tutucuları eşit.
- Yeni birim testleri: başlık ayrıştırma, ms→sn normalizasyonu, ücretli plan limitinin
  sabit 1.000 varsayılmaması, ücretsiz planda tükenmiş kota, bozuk gövdede başlık değerlerinin
  korunması, UTC sıfırlanma ve rollover davranışı.
- Ağ testi gerekmez; tüm kota ayrıştırma fonksiyonları saf.

## Notlar

- `/user` uç noktası anahtar başına en fazla 10 dakikada bir çağrılır; puan istekleri zaten
  başlıklarla kotayı güncellediği için bu sayı pratikte çok küçüktür.
- Puan araması hâlâ mevcut (eski) `mdblist.com/api/` uç noktasını kullanır; davranış
  değiştirilmedi. Kota bilgisi hesap düzeyinde (`api.mdblist.com/user`) okunur ve varsa
  her yanıtın başlıklarıyla gerçek zamanlı güncellenir.
