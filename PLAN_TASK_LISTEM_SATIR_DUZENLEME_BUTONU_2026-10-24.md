# Plan / Task — Listem satır görünümlerindeki düzenleme butonu

**Tarih:** 2026-10-24
**Sürüm:** v2.4.340 (kod değişikliği; sürüm numarası bu turda artırılmadı)
**Durum:** Uygulandı

## İstenen

Listem sayfasında kare (grid) görünüm **hariç** diğer görünümlerde kartın sağında duran **+** butonları vardı ama işlevleri yoktu / yanlıştı.

- Kayıtlar zaten listede; **+** ikonu “listeye tekrar ekle” gibi duruyordu.
- Buton aslında `+1 bölüm` basıyordu. Tamamlanmış kayıtlarda (12/12) ilerleme artırılamadığı için tıklayınca hiçbir şey olmuyordu.
- Asıl ihtiyaç: **detay sayfasına girmeden** liste düzenleme sayfasını açmak.

## Uygulama

### 1) Kart kısayolu (`KitsugiMediaEntryCard.kt`)
- `onIncrementClick` → `onEditClick: (() -> Unit)? = null`
- `QuickIncrementButton` (+) → `QuickEditButton` (kalem / `Icons.Rounded.Edit`)
- Buton yalnızca `onEditClick != null` ve TV değilken gösterilir
- Kare görünüm (`grid_2col`) aynı; orada bu buton yoktu, eklenmedi
- Kartın geri kalanına basınca detay açılmaya devam eder

### 2) Listem içeriği
- `MyListGridContent.kt` / `MyListComponents.kt`: `onIncrementProgress` → `onEditEntry`
- `MyListScreen.kt`: kalem tıklanınca `editingEntry = entry` → mevcut `KitsugiMediaEntryEditorDialog` (durum, bölüm, puan, not) detaya girmeden açılır
- Düzenleyiciye `source` ve `scoreFormat` geçirildi

### 3) Başkasının listesi
- `KitsugiUserMediaListScreen.kt`: düzenleme kısayolu gizlendi (başkasının listesi değiştirilemez; kart zaten detayı açıyor)

## Değişen dosyalar

1. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiMediaEntryCard.kt`
2. `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListComponents.kt`
3. `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListGridContent.kt`
4. `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListScreen.kt`
5. `app/src/main/java/com/kitsugi/animelist/ui/screens/profile/KitsugiUserMediaListScreen.kt`

## Doğrulama

- Bu ortamda cihaz/APK derlemesi yok; görsel doğrulama cihazda yapılmalıdır.
- Listem → kompakt / rahat / büyük / minimalist: kalem butonu düzenleme sayfasını açmalı.
- Listem → kare: buton görünmemeli.
- Kart gövdesine basınca detay açılmalı.
