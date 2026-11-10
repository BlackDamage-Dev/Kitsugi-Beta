# ÇÖZÜM RAPORU — Bölüm Sıralaması Hatası + Mihon/Tachiyomi Uyarlamaları
**Tarih:** 2026-11-06 · **Dal:** `arena/f6a2450e-kitsugi-beta`

---

## 1. Şikâyet edilen hata ve **kök nedeni**

Ekran görüntüsünde Berserk bölüm listesi **386 → 381 → …** diye başlıyordu. Nedeni tek satırdı:

```kotlin
// MangaDetailScreen.kt (önce)
var sortModeName by rememberSaveable { mutableStateOf(ChapterSortMode.NewestFirst.name) }
```

* `ChapterSortMode.NewestFirst` **varsayılan** kabul edilmişti — hem enum yorumunda ("Varsayılan")
  hem iki ekrandaki `getOrDefault(ChapterSortMode.NewestFirst)` yedeğinde.
* `rememberSaveable` sıralamayı yalnızca ekran yaşarken koruyordu; ekran her açıldığında
  **yeniden "en yeni"ye dönüyordu**.
* TV detay ekranında aynı sabit ikinci kez tekrarlanmıştı (`TvMangaDetailScreen.kt`).
* Okuyucu, listeyi **eklentiden geldiği sırayla** (yeniden eskiye) kullanıp "sonraki bölüm"ü
  `list[idx - 1]` ile buluyordu; kullanıcı sıralamayı değiştirse bile geçiş yönü yanlış kalıyordu.
* Bölüm numarası `-1` gelen kayıtlarda sıralama metne düşüp **"1, 10, 100, 11"** üretiyordu.

## 2. Uygulanan düzeltme

1. **Tek doğruluk kaynağı:** `ChapterSortMode.DEFAULT = OldestFirst` (1 → N). Enum sırası da
   `OldestFirst, NewestFirst, RecentlyUpdated` olarak değişti; "varsayılan" artık bir sabit.
2. **Kalıcı tercih:** sıralama `ChapterSortMode.fromName(settings.getMangaChapterSort(url))`
   ile DataStore'dan okunur. Genel tercih + **manga bazlı ezme** (`manga_sort_override_<hash>`)
   desteklenir; cihaz değişse bile tercih bulutla taşınır (`cloudKeys`).
3. **Menü, döngü değil:** ⇅ düğmesi üç seçeneği tek dokunuşta sunar (eskiden üç kez dokunmak
   gerekiyordu). Menüden sonra liste başına dönülür ki Bölüm 1 anında görünsün.
4. **Okuyucu:** `orderedChapters()` listeyi her zaman 1 → N normalize eder; `nextChapter()` /
   `previousChapter()` bu listeyi kullanır. Numarasız kayıtlar kaynak sırasını koruyup sona düşer.
5. **Numara çözümleme:** `withResolvedNumbers()` → `ChapterRecognition.parseChapterNumber`
   (Mihon'un aynı algoritması). "1, 10, 100, 11" sorunu bitti.
6. **"Baştan oku" hedefi:** `lastOrNull()` yerine **en küçük bölüm numarası** seçilir.

## 3. Mihon/Tachiyomi araştırmasından uyarlananlar

| Uyarlanan | Nerede |
|---|---|
| Bölüm görünümü (numara / başlık) | `ChapterDisplayMode`, detay satırı |
| Bölüm yer imi ★ + "yer imli" filtresi | satır + başlık düğmesi |
| Eksik bölüm uyarısı | ilerleme özeti satırı |
| Yinelenen bölüm gizleme | scanlator çip satırındaki ek çip |
| Öncekileri okundu işaretle | satıra uzun basış |
| Kaldığım yere git | başlıktaki konum düğmesi |
| Okunanı atla / otomatik bölüm geçişi | okuyucu paneli + Ayarlar |
| Kenarlık kırpma (crop borders) | okuyucu paneli (telefon + TV) |
| Bölüm indirme (CBZ) + çevrimdışı okuma | yeni indirici + `archiveResolver` |
| Manga kitaplığı (kategoriler, sıralama, ilerleme) | yeni ekran + Room v31 |
| "Devam et" düğmesi | kitaplık + detay |

**Uyarlanmayanlar** (tracker senkronu, kaynak birleştirme, çift sayfa, autoscroll…) için
"gereksiz" denmedi; her biri `PLAN_TASK_MANGA_SIRALAMA_MIHON_2026-11-06.md` §4'te **teknik
gerekçe + izlenecek yol** ile yazıldı.

## 4. Yol boyunca bulunan ve düzeltilen hatalar

1. `SettingsDataStore.setMangaKeepScreenOn` → `context.dataStore` (tanımsız) **derleme hatası**;
   `context.settingsDataStore` yapıldı.
2. Detay ekranına eklenen iki yeni liste öğesi **sticky başlık / üst bar eşiklerini** kaydırdı
   (`showAtIndex = 4`). Kitaplık düğmesi "Oku" satırının sağına, ilerleme özeti araç çubuğunun
   içine alındı → liste yeniden **hero · aksiyon · (açıklama) · wiki · başlık** yapısında.
3. `MangaPageLoaderV2` indirilmiş CBZ'yi sayfa okurken kullanmıyordu (`archiveFile` sabitine
   bakıyordu) → `activeArchive` eklendi; indirilen bölüm **tamamen çevrimdışı** açılıyor.
4. "Filtreleri temizle" sıralamayı eski hatalı varsayılana (`NewestFirst`) döndürüyordu;
   artık `ChapterSortMode.DEFAULT` kullanılıyor.

## 5. Teknik doğrulama

| Kontrol | Sonuç |
|---|---|
| `values` / `values-en` anahtar sayısı | 17.650 / 17.650 — **fark yok** |
| Kodda geçen `R.string.*` eksikleri | **0** (5.010 kullanım / 17.807 tanım) |
| Süslü parantez & parantez dengesi (36 dosya) | **0** fark |
| Room | `version = 31`, `MIGRATION_30_31`, `31.json` şeması |
| Yeni izin / bağımlılık | **Yok** (gson, room, datastore mevcut) |

> Not: Bu ortamda JDK/Android SDK bulunmadığı için `./gradlew assembleDebug` çalıştırılamadı;
> doğrulama statik analiz + şema/parite kontrolüyle yapıldı. CI'da ilk derleme kontrol edilmeli.

## 6. Dosya özeti (bu tur — 36 dosya, +4.209 / −128)

**Yeni (8)**
```
data/local/MangaLibraryEntity.kt
data/manga/download/MangaChapterDownloader.kt
ui/screens/manga/MangaLibraryScreen.kt
ui/screens/manga/MangaLibraryViewModel.kt
app/schemas/…KitsugiDatabase/31.json
```

**Güncellenen ana dosyalar**
```
data/manga/ChapterSorting.kt            → varsayılan + görünüm modu + eksik/yinelenen numara
data/settings/{AppSettings,SettingsDataStore}.kt → 5 yeni tercih, manga bazlı ezme, derleme hatası
ui/screens/manga/MangaDetailScreen.kt   → menü, ★, indirme rozeti, ilerleme özeti, gezinme
ui/screens/manga/MangaDetailViewModel.kt→ kalıcı sıralama, yer imi, markPreviousRead, indirme
ui/screens/manga/MangaReader*.kt        → sıralamaya uyan geçiş, okunanı atla, otomatik geçiş, crop
ui/screens/manga/components/MangaChapterTools.kt → sıralama/görünüm menüleri, ilerleme bileşeni
ui/tv/**                                → TV paritesi (sıralama, kitap, crop, geri tuşu)
ui/components/KitsugiPreferencesSettingsDialog.kt → Ayarlar > Okuyucu yeni satırları
```
