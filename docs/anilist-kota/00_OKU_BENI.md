# AniList Kota Paketi — 2026-10-24

Bu paket, AniList GraphQL API'sinin **30 istek/dk (IP başına)** kotasının Kitsugi'de neden sürekli
tükendiğini açıklayan raporu, uygulanabilir yamayı ve **değişmiş dosyaların tamamını** içerir.

## Klasör içeriği

| Dosya / klasör | İçerik |
|---|---|
| `ANILIST_KOTA_RAPORU_2026-10-24.md` | Ana rapor: gerçek limitler, kod röntgeni (satır satır), ekosistem araştırması (Mihon, animitchures, SeerrNG, uchiyomi…), çözüm mimarisi, yol haritası, kaynaklar |
| `PLAN_TASK_ANILIST_KOTA_GATE_2026-10-24.md` | Faz 0–3 task listesi, uygulama adımları, kabul kriterleri, riskler |
| `kitsugi-beta-anilist-quota-gate.patch` | Kitsugi-Beta'ya uygulanabilir yama (8 dosya, +578/−33) |
| `degisen-dosyalar/` | Aynı 8 dosyanın **tam hâli** (yamayı uygulamadan elle kopyalamak isteyenler için) |
| `AniListQuotaGate.kt` | Yeni kota kapısı dosyası (tek başına da kullanılabilir) |
| `anilist_kota_sim.py` | Algoritmanın Python portu: 5 garanti testi + 3 mimarinin karşılaştırması |
| `sim-cikti.txt` | Simülasyonun referans çıktısı |

## Nasıl uygulanır

```bash
# Seçenek A — yama (önerilen)
cd /yol/Kitsugi-Beta
git checkout -b fix/anilist-quota-gate
git apply --check /yol/paket/kitsugi-beta-anilist-quota-gate.patch
git apply         /yol/paket/kitsugi-beta-anilist-quota-gate.patch
./gradlew :app:compileDebugKotlin

# Seçenek B — dosyaları elle kopyala
# degisen-dosyalar/ içindeki ağacı klonun köküne kopyala (yollar birebir aynı)
cp -r degisen-dosyalar/app /yol/Kitsugi-Beta/
```

## Doğrulama

```bash
python3 anilist_kota_sim.py     # 5/5 garanti testi geçmeli
```

## Tek cümlelik özet

Kota 30 istek/dk ve **IP başına**; çözüm daha fazla IP değil:
**tek kota kapısı + daha az istek (birleştirme) + önbellek + 429'da beklemek.**

> ⚠️ Bu paketteki yama `git apply --check` ile doğrulandı ve yapı (blok/parantez) kontrolünden geçti,
> ancak bu ortamda Android SDK olmadığı için **derlenmedi**. Yayın öncesi `./gradlew :app:compileDebugKotlin`
> çalıştırın.
