package com.kitsugi.animelist.data.remote

import androidx.room.withTransaction
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.local.MediaEntryEntity
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.utils.PreferenceHelpers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

/**
 * Kütüphane (Listem) satırlarındaki Bangumi kayıtlarının **Latin adlarını tamamlar**.
 *
 * Neden gerekli: Bangumi koleksiyon ucu (`GET /v0/users/{user}/collections`) kısaltılmış条目
 * döndürür — yalnızca `name` (çoğu zaman Japonca/Çince) ve `name_cn` vardır, `infobox` **yoktur**.
 * Bu yüzden `BangumiImportManager` satırları `titleEnglish` boş olarak yazar ve "Listem"
 * ekranı, kullanıcı Romaji/İngilizce başlık dilini seçmiş olsa bile CJK adı gösterirdi.
 *
 * ## Başlık dili sözleşmesi (bu sınıfın garanti ettiği tanım)
 *
 * Veritabanı satırı üç başlık varyantını ayrı saklar: `title` (romaji/Latin ana ad),
 * `titleEnglish` (gerçek İngilizce ad), `titleJapanese` (özgün ad). Ekran gösterimi
 * [com.kitsugi.animelist.utils.PreferenceHelpers.getDisplayTitle] ile seçili dile göre yapılır:
 *
 * - `ENGLISH` → İngilizce → romaji (`title`) → özgün ad
 * - `ROMAJI`  → romaji (`title`) → İngilizce → özgün ad
 * - `NATIVE`  → özgün ad (`titleJapanese`) → diğerleri
 *
 * Bu sınıfın işi gösterim seçimi değildir; varyantların **varlığını** garanti etmektir.
 * Bir satır iki kademede değerlendirilir:
 *
 * 1. **LATIN eksik** — hiçbir Latin varyant yok; `ENGLISH`/`ROMAJI` gösterimi zorunlu olarak
 *    CJK ada düşer (kullanıcının bildirdiği hata). Bu kademe dil seçiminden bağımsız olarak
 *    her zaman giderilir; böylece kullanıcı dili sonradan değiştirse bile veri hazırdır.
 *    Özgün ad asla kaybolmaz, `titleJapanese` alanına taşınır; `NATIVE` gösterimi etkilenmez.
 * 2. **İngilizce zenginleştirme** — Latin (romaji) gösterim var ama ondan ayrı, doğrulanmış
 *    bir İngilizce ad yok. Yalnızca `ENGLISH` seçiliyken ve LATIN kademesi bittikten sonra,
 *    tur başına sınırlı sayıda denenir. Romaji'nin kendisi geçerli bir `ENGLISH` yedeğidir;
 *    bu kademe kuyruğu **asla tıkamaz**.
 *
 * ## İlerleme garantisi (v2.4.315 kök düzeltmesi)
 *
 * Eski sürüm tur başına tüm bekleyenleri çözümleyiciye veriyor, çözümleyici de listeyi
 * başından `take(limit)` ile kırpıyordu; üstelik bir turda veritabanı değişikliği olmazsa
 * tüm tarama yarıda bırakılıyordu. Listenin başındaki "yalnızca romaji bulunabilen" satırlar
 * her turda/başlatmada aynı bütçeyi tüketip değişim üretmediği için 717 kayıtlı bir
 * kütüphanede arka sıralara **hiç ulaşılamıyordu** — ekran görüntüsündeki gibi tüm liste
 * kalıcı olarak Japonca/Çince kalıyordu.
 *
 * Artık bekleyenler bir kez anlık görüntü (snapshot) olarak alınır ve [BATCH_SIZE]'lık
 * dilimler halinde, dilim sonucu ne olursa olsun **sonuna kadar** işlenir. Çözümleyicinin
 * arka plan bütçeleri (AniList 96 · infobox 120 · TMDB 60) dilim boyutundan küçük değildir;
 * her dilimdeki her satır en az bir kez gerçekten denenir. Dilim sonuçları ayrı ayrı
 * veritabanına yazılır; Room akışı sayesinde başlıklar ekranda aşamalı olarak Latin adlara
 * döner. Öncesinde ağsız bir `cacheOnly` geçişi, başka ekranlarda öğrenilmiş tüm adları
 * tek işlemde uygular.
 *
 * Yazma yolu doğrudan DAO'dur (`updateAll`): repository güncellemesi kullanılsaydı her
 * başlık düzeltmesi harici kaynaklara (MAL/AniList/…) yazma denemesi tetiklerdi. Başlık
 * yerelleştirmesi bir **yerel görünüm** iyileştirmesidir, senkron olayı değil.
 *
 * Tetikleyiciler: "Listem" ViewModel'i (açılış + başlık dili değişimi) ve her Bangumi içe
 * aktarımının hemen sonrası ([com.kitsugi.animelist.ui.app.AuthViewModel]). Yeniden içe
 * aktarımda çözülmüş adların CJK adla ezilmemesi ayrıca
 * [com.kitsugi.animelist.data.local.preserveResolvedTitles] ile garanti edilir.
 */
internal object BangumiLibraryTitleHydrator {

    private const val TAG = "BangumiLibTitles"

    /**
     * Tek çözümleme dilimindeki satır sayısı. Çözümleyicinin arka plan geçiş limitlerinin
     * (AniList 96, infobox 120, TMDB 60) en küçüğünü aşmaz; böylece dilim içindeki hiçbir
     * satır "limitin dışında kaldı" diye denenmeden geçilmez.
     */
    private const val BATCH_SIZE = 60

    /** Tek taramada işlenecek en fazla LATIN dilimi (60 × 16 = 960 satır). */
    private const val MAX_BATCHES = 16

    /** İngilizce zenginleştirme fırsatçıdır: tur başına en fazla 2 dilim (120 satır). */
    private const val MAX_ENRICHMENT_BATCHES = 2

    /** Aynı anda tek tarama: ekran yeniden açıldığında / içe aktarım bittiğinde mükerrer ağ trafiği olmaz. */
    private val mutex = Mutex()

    /**
     * Geriye dönük uyumlu giriş: dil bilinmiyorsa en talepkâr tanım (`ENGLISH`) varsayılır.
     * LATIN kademesi zaten dilden bağımsızdır; varsayım yalnızca zenginleştirmeyi açar.
     */
    suspend fun hydrate(database: KitsugiDatabase) = hydrate(database, titleLanguage = "ENGLISH")

    /**
     * Veritabanındaki Bangumi satırlarını tarar ve eksik Latin/İngilizce adları tamamlar.
     * Çağıran taraf bunu arka planda (IO) çalıştırmalıdır; hata durumunda sessizce durur.
     *
     * @param titleLanguage kullanıcının güncel başlık dili (`ENGLISH` / `ROMAJI` / `NATIVE`).
     *        Yalnızca İngilizce zenginleştirme kademesinin çalışıp çalışmayacağını belirler.
     */
    suspend fun hydrate(database: KitsugiDatabase, titleLanguage: String) {
        mutex.withLock { hydrateLocked(database, titleLanguage) }
    }

    private suspend fun hydrateLocked(database: KitsugiDatabase, titleLanguage: String) {
        BangumiTitleCache.warmUp()
        val dao = database.mediaEntryDao()

        // 0) Ağsız geçiş: başka ekranlarda (detay, keşfet, profil) öğrenilip kalıcı önbelleğe
        //    yazılmış adlar tek seferde uygulanır. Özellikle yeniden içe aktarımda daha önce
        //    çözülmüş yüzlerce satır ağ isteği olmadan anında Latin ada döner.
        val initialPending = pendingRequests(dao.getAll(), titleLanguage)
        if (initialPending.isEmpty()) return
        try {
            val cachedOnly = BangumiSubjectTitleResolver.resolve(
                initialPending,
                background = true,
                cacheOnly = true
            )
            if (cachedOnly.isNotEmpty()) {
                val applied = applyResolvedTitles(database, cachedOnly)
                android.util.Log.i(TAG, "Bangumi önbellek geçişi: $applied satır güncellendi")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.w(TAG, "Bangumi önbellek geçişi atlandı: ${error.message}")
        }

        // 1) LATIN kademesi: CJK gösterime düşen satırlar. Snapshot bir kez alınır ve dilimler
        //    sonuçtan bağımsız olarak sonuna kadar işlenir (ilerleme garantisi). Önbellek
        //    geçişinde çözülen satırlar taze taramada kendiliğinden elenir.
        val (latinRequests, enrichmentRequests) = pendingRequestsByNeed(dao.getAll(), titleLanguage)
        var latinApplied = 0
        for (batch in latinRequests.chunked(BATCH_SIZE).take(MAX_BATCHES)) {
            latinApplied += resolveAndApply(database, batch)
        }
        if (latinRequests.isNotEmpty()) {
            android.util.Log.i(
                TAG,
                "Bangumi Latin başlık taraması: ${latinRequests.size} aday, $latinApplied satır güncellendi"
            )
        }

        // 2) İngilizce zenginleştirme: yalnızca ENGLISH seçiliyken, LATIN kademesinden sonra
        //    ve sınırlı sayıda. Bulunamazsa satır romaji ile gösterilmeye devam eder; bu
        //    kademe bir sonraki taramada yeniden denenebilir ama kimseyi bekletmez.
        if (titleLanguage.trim().equals("ENGLISH", ignoreCase = true) && enrichmentRequests.isNotEmpty()) {
            var enrichmentApplied = 0
            for (batch in enrichmentRequests.chunked(BATCH_SIZE).take(MAX_ENRICHMENT_BATCHES)) {
                enrichmentApplied += resolveAndApply(database, batch)
            }
            android.util.Log.i(
                TAG,
                "Bangumi İngilizce zenginleştirme: ${enrichmentRequests.size} aday, $enrichmentApplied satır güncellendi"
            )
        }
    }

    /** Tek dilimi çözer ve uygular. Hata yalnızca bu dilimi etkiler; tarama devam eder. */
    private suspend fun resolveAndApply(
        database: KitsugiDatabase,
        batch: List<BangumiSubjectTitleResolver.Request>
    ): Int {
        val resolved = try {
            BangumiSubjectTitleResolver.resolve(batch, background = true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.w(TAG, "Bangumi başlık dilimi çözülemedi: ${error.message}")
            return 0
        }
        if (resolved.isEmpty()) return 0
        return applyResolvedTitles(database, resolved)
    }

    /**
     * Latin adı henüz bilinmeyen Bangumi satırları için çözümleme istekleri
     * (LATIN kademesi önce, ardından İngilizce zenginleştirme adayları).
     *
     * `internal` — birim testi bu seçimin doğru olduğunu doğrular (bkz.
     * `BangumiLibraryTitleHydratorTest`). Network/DB'ye dokunmayan saf bir fonksiyondur;
     * yalnızca bellek içi [BangumiTitleCache] okunur.
     */
    internal fun pendingRequests(
        entities: List<MediaEntryEntity>,
        titleLanguage: String = "ENGLISH"
    ): List<BangumiSubjectTitleResolver.Request> {
        val (latin, enrichment) = pendingRequestsByNeed(entities, titleLanguage)
        return latin + enrichment
    }

    /**
     * Bekleyen satırları ihtiyaca göre ayırır: `(latinEksik, ingilizceZenginlestirme)`.
     *
     * - LATIN eksik: kullanılabilir hiçbir Latin varyant yok → `ENGLISH`/`ROMAJI`
     *   gösterimi CJK ada düşer. Dilden bağımsız olarak her zaman döner.
     * - İngilizce zenginleştirme: romaji var ama ondan ayrı doğrulanmış İngilizce yok.
     *   Yalnızca [titleLanguage] `ENGLISH` ise döner; `ROMAJI`/`NATIVE` için romaji
     *   zaten yeterli gösterimdir.
     *
     * AniList eşleşmesi için elde CJK bir özgün ad gerekir; özgün adı bilinmeyen Latin
     * satırlar aranmaz (yanlış yapımın adı asla gösterilmez). Tek istisna içe aktarımın
     * `Bangumi #<id>` yer tutucusudur: arama terimi olmasa bile subject infobox'ı kimlikle
     * okunabildiği için LATIN kademesine boş terimle girer.
     */
    internal fun pendingRequestsByNeed(
        entities: List<MediaEntryEntity>,
        titleLanguage: String = "ENGLISH"
    ): Pair<List<BangumiSubjectTitleResolver.Request>, List<BangumiSubjectTitleResolver.Request>> {
        val wantsEnglishEnrichment = titleLanguage.trim().equals("ENGLISH", ignoreCase = true)
        val latinRequests = LinkedHashMap<Int, BangumiSubjectTitleResolver.Request>()
        val enrichmentRequests = LinkedHashMap<Int, BangumiSubjectTitleResolver.Request>()
        for (entity in entities) {
            if (!isBangumi(entity)) continue
            val rawId = BangumiIdNamespace.rawIdOrNull(entity.malId) ?: continue
            if (rawId <= 0 || latinRequests.containsKey(rawId) || enrichmentRequests.containsKey(rawId)) continue

            val current = entity.title.trim()
            val cached = BangumiTitleCache.get(rawId)
            val english = entity.titleEnglish?.trim()?.takeIf { PreferenceHelpers.isLatinText(it) }
                ?: cached?.english?.takeIf { PreferenceHelpers.isLatinText(it) }
            val romaji = current.takeIf { PreferenceHelpers.isLatinText(it) }
                ?: cached?.romaji?.takeIf { PreferenceHelpers.isLatinText(it) }
            val nativeTitle = listOfNotNull(
                entity.titleJapanese?.trim()?.takeIf { it.isNotEmpty() },
                current.takeIf { it.isNotEmpty() },
                cached?.native?.trim()?.takeIf { it.isNotEmpty() }
            ).firstOrNull { PreferenceHelpers.hasCjkCharacters(it) }

            val isImportPlaceholder = current == "Bangumi #$rawId"
            val hasAnyLatin = !isImportPlaceholder && (romaji != null || english != null)
            val request = BangumiSubjectTitleResolver.Request(
                rawId = rawId,
                nativeTitle = nativeTitle.orEmpty(),
                mediaType = mediaTypeOf(entity)
            )

            when {
                !hasAnyLatin -> {
                    // Arama terimi (CJK özgün ad) yoksa yalnızca infobox yolu şans verebilir:
                    // o da yer tutucu satırlarda anlamlıdır. Diğer satırlarda elde gösterilecek
                    // bir ad zaten vardır ve uydurma eşleştirme yapılmaz.
                    if (nativeTitle != null || isImportPlaceholder) {
                        latinRequests[rawId] = request
                    }
                }
                wantsEnglishEnrichment && nativeTitle != null &&
                    !BangumiSubjectTitleResolver.hasReliableEnglish(
                        BangumiTitleCache.LatinTitles(romaji, english, nativeTitle)
                    ) -> enrichmentRequests[rawId] = request
                else -> Unit // Seçili dil için gösterim zaten Latin; ağ isteği gerekmez.
            }
        }
        return latinRequests.values.toList() to enrichmentRequests.values.toList()
    }

    /** Çözülen adları veritabanına tek transaction'da yazar; güncellenen satır sayısını döner. */
    private suspend fun applyResolvedTitles(
        database: KitsugiDatabase,
        resolved: Map<Int, BangumiTitleCache.LatinTitles>
    ): Int {
        val dao = database.mediaEntryDao()
        val updates = ArrayList<MediaEntryEntity>()
        for (entity in dao.getAll()) {
            if (!isBangumi(entity)) continue
            val rawId = BangumiIdNamespace.rawIdOrNull(entity.malId) ?: continue
            val latin = resolved[rawId] ?: continue

            val romaji = latin.romaji?.trim()?.takeIf { PreferenceHelpers.isLatinText(it) }
            val english = latin.english?.trim()?.takeIf { PreferenceHelpers.isLatinText(it) }
            val native = latin.native?.trim()?.takeIf { it.isNotEmpty() }

            val currentTitle = entity.title
            val latinTitle = romaji ?: english
            val nextTitle = if (
                latinTitle != null &&
                (PreferenceHelpers.hasCjkCharacters(currentTitle) || currentTitle == "Bangumi #$rawId")
            ) {
                latinTitle
            } else {
                currentTitle
            }
            val nextEnglish = english ?: entity.titleEnglish
            // Özgün ad asla kaybolmaz: "Ana Dilde" seçeneği bu alandan okunur.
            val nextJapanese = entity.titleJapanese?.takeIf { it.isNotBlank() }
                ?: native
                ?: currentTitle.takeIf { PreferenceHelpers.hasCjkCharacters(it) }

            val updated = entity.copy(
                title = nextTitle,
                titleEnglish = nextEnglish,
                titleJapanese = nextJapanese
            )
            if (updated != entity) updates += updated
        }
        if (updates.isEmpty()) return 0
        database.withTransaction { dao.updateAll(updates) }
        return updates.size
    }

    private fun isBangumi(entity: MediaEntryEntity): Boolean =
        entity.source.trim().lowercase(Locale.ROOT) == BangumiIdNamespace.SOURCE

    private fun mediaTypeOf(entity: MediaEntryEntity): MediaType =
        when (entity.type.trim().lowercase(Locale.ROOT)) {
            "manga" -> MediaType.Manga
            "movie" -> MediaType.Movie
            "tvshow", "tv" -> MediaType.TvShow
            else -> MediaType.Anime
        }
}
