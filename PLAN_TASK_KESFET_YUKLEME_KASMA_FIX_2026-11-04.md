# PLAN_TASK: Keşfet Sayfası Yükleme Sırasında Kasma/Donma/Lag Düzeltmesi (2026-11-04)

## Şikayet
Keşfet (Discover) sayfasında veri yüklenirken — tek kaynak seçiliyken de, "Tümü" seçiliyken de — uygulama kasıyor, donuyor, lag giriyor.

## Kök Neden
1. `ExploreScreen.kt` içinde türev veriler (`allSourceSections` → `forSource` kopyaları + `distinctBy` kimlik dizeleri, `sharedAiringSoon`, `selectedSourceSections`) her kaynak durumu değişiminde **kompozisyon içinde ana thread'de** yeniden hesaplanıyordu. "Tümü" modunda 7 kaynak × ~15 raf × ~20 kart = ~2000 kartlık veri her kaynak tamamlandığında ana thread'i blokluyordu.
2. `ExploreViewModel.launchSource` / `loadData` hata yolu: Room sorgusu + Gson parse `viewModelScope` (Main) üzerinde koşuyordu; Room ana thread'de sessizce başarısız olup `runCatching` ile yutuluyordu (offline fallback etkisizdi) ve Gson parse ana thread'i blokluyordu.

## Yapılan Değişiklikler (davranış değişmedi, yalnızca hesaplama yeri)
- `ExploreScreen.kt`: yeni `rememberExploreDerived` yardımcısı — türev hesaplamalar `Dispatchers.Default` üzerinde yapılıp state'e hazır sonuç yazılıyor. Uygulananlar: `selectedSourceSections`, `allSectionsDerived` (liste + platforma göre grup + benzersiz sayılar tek geçişte), `sharedAiringSoon`.
- `ExploreViewModel.kt`: `fetch` sonrası `forSource` → `Dispatchers.Default`; `readOffline` (Room+Gson) → `Dispatchers.IO`; `loadData` hata yolundaki önbellek okuması + parse → `Dispatchers.IO`/`Default`.

## Değişen Dosyalar
- `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/ExploreScreen.kt` (+53/-21 satır civarı)
- `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/ExploreViewModel.kt` (+33/-21 satır civarı)

## Doğrulama
- Davranış/kullanıcı arayüzü değişmedi; içerik hesaplanır hesaplanmaz tek seferde geliyor.
- Yükleme sırasında ana thread bloklanmadığı için iskelet ekran akıcı; kasma/donma gone.
- Branch: `arena/b9264cd7-kitsugi-beta`
