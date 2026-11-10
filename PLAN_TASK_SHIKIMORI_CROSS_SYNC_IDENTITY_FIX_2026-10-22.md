# Kitsugi — Shikimori Çapraz Eşitleme Kimlik ve Durum Düzeltmesi

**Tarih:** 2026-10-22  
**Dal:** `arena/fb8e11bc-kitsugi-beta`  
**Temel commit:** `26bb0444028e1209ffa7cf64d9c270a330471363`  
**Durum:** Kod ve hedefli regresyon testleri tamamlandı; cihaz üzerinde uçtan uca doğrulama bekleniyor.

## 1. Kullanıcı şikâyeti ve kabul ölçütü

Diğer kaynaklarda güncellenen içeriklerin durum, puan ve bölüm/ilerleme bilgileri zamanla eşitlenirken Shikimori güncellenmiyordu. Ekrandaki Zom 100 örneğinde Shikimori **İzleniyor, 0/12**; diğer kaynaklar **Bırakıldı, 4/12** durumundaydı.

Amaç, yalnızca görünür durumu değiştirmek değil, Shikimori kaydının neden eşitleme dışında kaldığını kök nedeniyle düzeltmek ve desteklenen ortak alanların aynı kayıt üzerinde birleşmesini sağlamaktır.

## 2. Araştırmada doğrulanan kök nedenler

### 2.1 Shikimori kimliği yanlışlıkla MAL kimliğinden ayrıştırılıyordu

Shikimori'nin resmî `AnimeProfileSerializer` ve `MangaProfileSerializer` kodlarında `myanimelist_id`, doğrudan `object.id` döndürür. Canlı `/api/animes/54112` yanıtında da Zom 100 için `id = 54112` ve `myanimelist_id = 54112` değerleri bulunur.

Buna karşılık uygulama:

- MAL kaydı için `Anime:mal:54112`,
- Shikimori kaydı için yalnızca `Anime:shikimori:54112`

anahtarını üretiyordu. Shikimori liste yanıtında İngilizce alternatif başlık da çoğu zaman bulunmadığından Romaji ve İngilizce kartlar başlık üzerinden eşleşemiyordu. Shikimori tek kaynaklı ayrı bir grupta kalıyor ve çapraz yazma atlanıyordu.

Resmî kaynaklar:

- https://github.com/shikimori/shikimori/blob/master/app/serializers/anime_profile_serializer.rb
- https://github.com/shikimori/shikimori/blob/master/app/serializers/manga_profile_serializer.rb
- https://shikimori.io/api/animes/54112

### 2.2 Bayat “İzleniyor” durumu, daha yeni “Bırakıldı” kararını eziyordu

Kimlik gruplaması tek başına yeterli değildi. Önceki sabit durum önceliği, herhangi bir kaynakta `Watching` varsa daha yeni `Dropped` kaydına tercih ediyordu. Shikimori doğru gruba eklense bile eski durum hedef değer olarak seçilebilir ve diğer kaynaklara geri yayılabilirdi.

## 3. Tamamlanan görevler

- [x] Shikimori ham anime/manga `target_id` değerini ortak MAL kimliği olarak çözümle.
- [x] Shikimori `400M` stable-ID/ofset biçimini de aynı MAL kimliğine bağla.
- [x] Eski kaynak-özel Shikimori anahtarını geriye dönük uyumluluk için alias olarak koru.
- [x] Kitsu ve Bangumi kimliklerini kendi namespace'lerinde tut; mevcut conflict güvenlik kontrollerini koru.
- [x] Tekil ekleme/düzenleme fan-out yolundaki `CrossProviderIds.decode` davranışını aynı resmî kimlik sözleşmesiyle uyumlu hale getir.
- [x] Toplu eşitlemede tamamlanma güvencelerini koruyarak diğer durumlar için en güncel kaydın kullanıcı kararını seç.
- [x] Zom 100'ün farklı Romaji/İngilizce başlıklarıyla ortak kimlikten eşleşmesini regresyon testiyle kapsa.
- [x] Yeni `Dropped` kararının bayat Shikimori `Watching` kaydını yenmesini ve tamamlanma davranışının korunmasını test et.
- [x] Yanlış eski test varsayımlarını resmî Shikimori sözleşmesine göre düzelt.

## 4. Uygulama notları

- `MediaIdentity.keys`: Shikimori anime/manga kayıtları artık hem ortak `mal` hem kaynak-özel `shikimori` anahtarını üretir.
- `MediaIdentity.realMalId`: Shikimori ham ve stable-ID biçimlerinden gerçek MAL kimliğini döndürür.
- `CrossProviderIds.decode`: Aynı kimlik çözümü tekil eşitleme/fan-out yolunda da kullanılabilir.
- `CrossSyncMergePolicy`: Tamamlanma kuralları korunur; diğer durumlarda en güncel kaydın durumu seçilir.
- `AuthViewModel`: Toplu eşitlemenin durum birleştirmesi yeni saf politikayı kullanır.
- Shikimori API yazma yöntemi değiştirilmedi; mevcut resmî v2 `createUserRate` upsert'ı ve alan dönüşümleri kullanılmaya devam eder.

## 5. Değişen kaynak ve test dosyaları

1. `app/src/main/java/com/kitsugi/animelist/data/auth/CrossProviderIdentity.kt`
2. `app/src/main/java/com/kitsugi/animelist/data/auth/CrossSyncMergePolicy.kt` — yeni
3. `app/src/main/java/com/kitsugi/animelist/model/MediaIdentity.kt`
4. `app/src/main/java/com/kitsugi/animelist/ui/app/AuthViewModel.kt`
5. `app/src/test/java/com/kitsugi/animelist/data/auth/CrossProviderIdentityTest.kt`
6. `app/src/test/java/com/kitsugi/animelist/data/auth/CrossSyncCandidateIndexTest.kt`
7. `app/src/test/java/com/kitsugi/animelist/data/auth/CrossSyncMergePolicyTest.kt` — yeni
8. `app/src/test/java/com/kitsugi/animelist/model/MediaIdentityDisambiguationTest.kt`
9. `app/src/test/java/com/kitsugi/animelist/ui/screens/mylist/MyListLibraryGroupingTest.kt`

Bu plan/task kaydıyla birlikte paket toplam **10 dosya** içerir.

## 6. Doğrulama sonuçları

- **42 hedefli Kotlin/JVM testi geçti, 0 hata.**
  - 19 `CrossProviderIdentityTest` testi.
  - 8 `MediaIdentityDisambiguationTest` testi.
  - 4 `CrossSyncCandidateIndexTest` testi.
  - 3 `CrossSyncMergePolicyTest` testi.
  - 8 `MyListLibraryGroupingTest` testi.
- Dokuz değişen Kotlin kaynak/test dosyasında Tree-sitter Kotlin sözdizimi kontrolü geçti.
- `git diff --check` temiz.
- Tam Android/Gradle derlemesi bu çalışma ortamında çalıştırılmadı.

## 7. Kalan görevler / kullanıcı doğrulaması

- [ ] Değişikliklerle tam Android debug/release derlemesi çalıştır.
- [ ] Yeni sürümü cihazda aç ve mevcut kayıtlar için bir kez **Kaynakları Eşitle** çalıştır.
- [ ] Zom 100'ün Shikimori kaydının diğer kaynaklarla aynı gruba girdiğini doğrula.
- [ ] En güncel kaynak **Bırakıldı, 4/12** ise Shikimori'nin de aynı durum ve ilerlemeye geldiğini doğrula.
- [ ] Puan ve Shikimori'nin desteklediği diğer ortak alanlarda bir tekil düzenleme yapıp fan-out sonucunu kontrol et.
- [ ] Kitsu/Bangumi kayıtlarında yanlış yapım birleşmesi oluşmadığını mevcut güvenlik senaryolarıyla gözle.

## 8. Kapsam dışı / bilinen sınırlar

- Shikimori `user_rates` API'sinin yazılabilir alanları dışındaki başlangıç/bitiş tarihi gibi alanlarda yeni bir platform davranışı eklenmedi.
- Salt 10 dakika beklemek tam toplu eşitleme garantisi değildir; mevcut kayıtları düzeltmek için yeni sürümde toplu eşitleme bir kez çalıştırılmalıdır.
- Yönlendirilmiş POST davranışı bu olayın kanıtlanmış kök nedeni olmadığından bu pakette değiştirilmedi.
