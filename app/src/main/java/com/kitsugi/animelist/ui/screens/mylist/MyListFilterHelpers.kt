package com.kitsugi.animelist.ui.screens.mylist

import com.kitsugi.animelist.ui.utils.localizedLabel
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.kitsugi.animelist.R
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.data.settings.KitsugiContentPrefs
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaIdentity
import com.kitsugi.animelist.model.WatchStatus
import java.util.Locale

private val SEARCH_TOKEN_SPLIT_REGEX = Regex("[^\\p{L}\\p{N}]+")

/**
 * Türkçe İ/I/ı/i dönüşümünü ve birleşen nokta imini (`\u0307`) bozmadan arama metnini katlar.
 */
internal fun foldMyListSearchText(value: String): String {
    if (value.isEmpty()) return ""
    val sb = StringBuilder(value.length)
    val lowered = value.lowercase(Locale.ROOT)
    for (ch in lowered) {
        when (ch) {
            '\u0307' -> Unit
            'ı' -> sb.append('i')
            else -> sb.append(ch)
        }
    }
    return sb.toString()
}

/**
 * Listem ekranındaki bütün istemci tarafı filtrelerini tek bir sözleşmede uygular.
 *
 * Kendi listemiz ve salt-okunur kullanıcı listeleri aynı yardımcıyı kullanır. Böylece
 * arama/filtre davranışı ekranlar arasında zamanla birbirinden kopmaz.
 *
 * [searchAliasesByEntryId] verildiğinde, bir kaydın kendi sekmesinden çıkmadan doğrulanmış
 * kardeş başlık varyantlarıyla (ör. Simkl/Shikimori/Bangumi kaydının İngilizce veya Romaji adı)
 * eşleşmesi sağlanır; kaynak/tür/durum/sentetik ID alanları ise yanlış pozitif üretmemesi için
 * başlık aramasına katılmaz.
 */
internal fun filterMyListEntries(
    entries: List<MediaEntry>,
    searchQuery: String,
    selectedStatusFilterId: String,
    selectedTypeFilterId: String,
    selectedFavoriteFilterId: String,
    selectedScoreFilterId: String,
    selectedYearFilterId: String,
    selectedExtraFilterId: String,
    searchAliasesByEntryId: Map<Int, Set<String>> = emptyMap(),
    /**
     * Bulanık (yazım hatası/kısaltma) aramayı zorlar. `null` → kullanıcı ayarı
     * ([KitsugiContentPrefs.fuzzySearchEnabled]) kullanılır. Arama hattı isteğin kendi
     * `fuzzySearchEnabled` alanını geçirir; aksi hâlde istekteki tercih yok sayılıyordu.
     */
    fuzzySearchOverride: Boolean? = null,
    /**
     * Tarama sırasında iptal kontrolü. 2.600+ kayıtlık kütüphanede eski bir arama, yeni
     * tuşa basıldığında ancak tarama BİTİNCE duruyordu (kare atlaması). Checkpoint her 32
     * kayıtta bir `ensureActive()` çağırır.
     */
    checkActive: () -> Unit = {},
    cancellationCheck: (() -> Unit)? = null,
    fuzzySearchEnabled: Boolean? = null,
    checkCancelled: (() -> Unit)? = null
): List<MediaEntry> {
    val onCheckpoint = checkCancelled ?: cancellationCheck ?: checkActive
    val effectiveFuzzyOverride = fuzzySearchEnabled ?: fuzzySearchOverride
    val selectedStatus = statusFilters.firstOrNull { it.id == selectedStatusFilterId }?.status
    val selectedType = typeFilters.firstOrNull { it.id == selectedTypeFilterId }?.type
    val favoritesOnly = selectedFavoriteFilterId == "favorites" || selectedStatusFilterId == "favorites"
    val adultOnly = selectedStatusFilterId == "adult"
    val trimmedQuery = searchQuery.trim()
    val foldedQuery = foldMyListSearchText(trimmedQuery)
    val normQuery = if (foldedQuery.isBlank()) "" else foldMyListSearchText(MediaIdentity.normalizedTitle(trimmedQuery))
    val queryTokens = if (foldedQuery.isBlank()) {
        emptyList()
    } else {
        foldedQuery.split(SEARCH_TOKEN_SPLIT_REGEX).filter { it.isNotEmpty() }
    }
    val exactYearQuery = foldedQuery.takeIf { it.length == 4 && it.all(Char::isDigit) }
    val fuzzyEnabled = effectiveFuzzyOverride ?: KitsugiContentPrefs.fuzzySearchEnabled
    var scanned = 0

    return entries.asSequence()
        .onEach { if (scanned++ % 32 == 0) onCheckpoint() }
        .filter { entry -> selectedStatus == null || entry.status == selectedStatus }
        .filter { entry -> selectedType == null || entry.type == selectedType }
        .filter { entry -> !favoritesOnly || entry.isFavorite }
        .filter { entry -> !adultOnly || entry.isAdult }
        .filter { entry ->
            when (selectedScoreFilterId) {
                "high" -> (entry.score ?: 0) >= 8
                "mid" -> (entry.score ?: 0) in 5..7
                "low" -> (entry.score ?: 0) in 1..4
                "unrated" -> entry.score == null || entry.score == 0
                else -> true
            }
        }
        .filter { entry ->
            when (selectedYearFilterId) {
                "new" -> (entry.year ?: 0) >= 2025
                "2020s" -> (entry.year ?: 0) in 2020..2024
                "2010s" -> (entry.year ?: 0) in 2010..2019
                "2000s" -> (entry.year ?: 0) in 2000..2009
                "classic" -> (entry.year ?: 0) in 1..1999
                else -> true
            }
        }
        .filter { entry ->
            when (selectedExtraFilterId) {
                "repeating" -> entry.isRepeating || entry.status == WatchStatus.Repeating
                "private" -> entry.isPrivate
                "ongoing" -> entry.status != WatchStatus.Completed && entry.status != WatchStatus.Dropped
                else -> true
            }
        }
        .filter { entry ->
            if (foldedQuery.isBlank()) {
                true
            } else {
                entryMatchesMyListSearchQuery(
                    entry = entry,
                    foldedQuery = foldedQuery,
                    normQuery = normQuery,
                    queryTokens = queryTokens,
                    exactYearQuery = exactYearQuery,
                    fuzzyEnabled = fuzzyEnabled,
                    extraAliases = searchAliasesByEntryId[entry.id].orEmpty()
                )
            }
        }
        .toList()
}

private fun entryMatchesMyListSearchQuery(
    entry: MediaEntry,
    foldedQuery: String,
    normQuery: String,
    queryTokens: List<String>,
    exactYearQuery: String?,
    fuzzyEnabled: Boolean,
    extraAliases: Set<String>
): Boolean {
    if (exactYearQuery != null && entry.year?.toString() == exactYearQuery) {
        return true
    }

    if (candidateTitleMatches(entry.title, foldedQuery, normQuery, queryTokens, fuzzyEnabled)) {
        return true
    }
    val english = entry.titleEnglish
    if (!english.isNullOrBlank() &&
        candidateTitleMatches(english, foldedQuery, normQuery, queryTokens, fuzzyEnabled)
    ) {
        return true
    }
    val japanese = entry.titleJapanese
    if (!japanese.isNullOrBlank() &&
        candidateTitleMatches(japanese, foldedQuery, normQuery, queryTokens, fuzzyEnabled)
    ) {
        return true
    }
    if (extraAliases.isNotEmpty()) {
        for (alias in extraAliases) {
            if (alias == entry.title || alias == entry.titleEnglish || alias == entry.titleJapanese) continue
            if (candidateTitleMatches(alias, foldedQuery, normQuery, queryTokens, fuzzyEnabled)) {
                return true
            }
        }
    }
    return false
}

private fun candidateTitleMatches(
    candidate: String,
    foldedQuery: String,
    normQuery: String,
    queryTokens: List<String>,
    fuzzyEnabled: Boolean
): Boolean {
    if (candidate.isBlank()) return false

    val foldedCandidate = foldMyListSearchText(candidate)
    if (foldedCandidate.contains(foldedQuery)) return true

    if (normQuery.length >= 2) {
        val normCandidate = foldMyListSearchText(MediaIdentity.normalizedTitle(candidate))
        if (normCandidate.isNotEmpty() && normCandidate.contains(normQuery)) return true
    }

    val candidateTokens by lazy(LazyThreadSafetyMode.NONE) {
        foldedCandidate.split(SEARCH_TOKEN_SPLIT_REGEX).filter { it.isNotEmpty() }
    }

    if (queryTokens.size >= 2) {
        if (candidateTokens.isNotEmpty() && multiTokenMatches(candidateTokens, queryTokens, fuzzyEnabled)) {
            return true
        }
    }

    if (fuzzyEnabled && queryTokens.size == 1) {
        val singleToken = queryTokens[0]
        if (singleToken.length >= 3 && singleToken.none(Char::isDigit) && candidateTokens.isNotEmpty()) {
            // Baş harf kısaltması (ör. "aot" -> "Attack on Titan", "fmab" -> "Fullmetal Alchemist Brotherhood")
            if (candidateTokens.size >= 3) {
                val initials = buildString(candidateTokens.size) {
                    candidateTokens.forEach { append(it[0]) }
                }
                if (initials.contains(singleToken)) return true
            }
            // Kelime bazlı yazım hatası toleransı (rakam içeren sorgularda sezon/numara karışmaması için kapalı)
            if (singleToken.length >= 4 && singleWordTypoMatches(candidateTokens, singleToken)) {
                return true
            }
        }
    }

    return false
}

private fun multiTokenMatches(
    candidateTokens: List<String>,
    queryTokens: List<String>,
    fuzzyEnabled: Boolean
): Boolean = queryTokens.all { qToken ->
    when {
        qToken.all(Char::isDigit) -> candidateTokens.any { it == qToken }
        qToken.length == 1 -> candidateTokens.any { it.startsWith(qToken) }
        else -> candidateTokens.any { it.contains(qToken) } ||
            (fuzzyEnabled && qToken.length >= 4 && qToken.none(Char::isDigit) && singleWordTypoMatches(candidateTokens, qToken))
    }
}

private fun singleWordTypoMatches(candidateTokens: List<String>, token: String): Boolean {
    val tolerance = if (token.length <= 6) 1 else 2
    return candidateTokens.any { word ->
        word.none(Char::isDigit) &&
            kotlin.math.abs(word.length - token.length) <= tolerance &&
            boundedLevenshtein(word, token, tolerance) <= tolerance
    }
}

private fun boundedLevenshtein(a: String, b: String, maxDistance: Int): Int =
    KitsugiContentPrefs.levenshteinBounded(a, b, maxDistance)

internal fun groupMyListEntriesByStatus(
    entries: List<MediaEntry>
): List<Pair<WatchStatus, List<MediaEntry>>> {
    val statusOrder = listOf(
        WatchStatus.Watching,
        WatchStatus.Repeating,
        WatchStatus.Planned,
        WatchStatus.Paused,
        WatchStatus.Dropped,
        WatchStatus.Completed
    )
    return statusOrder.map { status -> status to entries.filter { it.status == status } }
        .filter { (_, statusEntries) -> statusEntries.isNotEmpty() }
}

/** Sıralama seçilmediğindeki varsayılan gezinme: durum bölümleri + bölüm içi son eklenen. */
internal const val MY_LIST_SORT_DEFAULT = "default"

/** Kullanıcı bilinçli bir sıralama seçti mi? (Varsayılan gezinme dışında her şey) */
internal fun isExplicitMyListSort(sortId: String): Boolean =
    sortId.isNotBlank() && sortId != MY_LIST_SORT_DEFAULT

/** "Son eklenen / İlk eklenen" tarih bölümleriyle (Bugün, Bu Hafta…) gösterilir. */
internal fun myListSortUsesAddedDateSections(sortId: String): Boolean =
    sortId == "newest" || sortId == "oldest"

/**
 * Liste düz (tek akış) mı, durum bölümlü mü çizilecek?
 *
 * KÖK NEDEN DÜZELTMESİ (2026-10-23): önceden sıralama önce uygulanıyor, sonra
 * liste HER ZAMAN durum bölümlerine (İzleniyor → Planlandı → … → Tamamlandı)
 * yeniden ayrılıyordu. Sonuç: kullanıcı "Son eklenen" (veya Puan, Ada göre…)
 * seçse bile global sıra bölümler tarafından eziliyor, sıralama yalnızca
 * bölüm İÇİNDE geçerli oluyordu — dün eklediğin "Tamamlandı" kaydı, aylar
 * önce eklenmiş "İzleniyor" kayıtlarının altında kalıyordu.
 *
 * Yeni sözleşme:
 * - Ayrılmış liste tarzı kapalıysa → her zaman düz (mevcut AniHyou paritesi).
 * - Tek bir durum filtresi aktifse → düz (tek bölümün başlığı gürültüdür;
 *   önceden yalnızca "Tamamlandı" için özel durum vardı, asimetri kalktı).
 * - Kullanıcı bir sıralama seçtiyse → düz; SIRALAMA KAZANIR, durum yalnızca
 *   filtre olarak kalır. "Son eklenen/İlk eklenen" düz akışta tarih
 *   bölümleriyle gösterilir ([myListSortUsesAddedDateSections]).
 * - Varsayılan sıralama + "Tümü" durumu → klasik durum bölümlü gezinme.
 */
internal fun myListUsesFlatSortedLayout(
    sortId: String,
    selectedStatusFilterId: String,
    separatedListStyle: Boolean
): Boolean {
    if (!separatedListStyle) return true
    if (selectedStatusFilterId != "all") return true
    return isExplicitMyListSort(sortId)
}

/** "Son eklenen" görünümünün tarih bölümleri. Etiketler strings.xml'den çözülür. */
internal enum class MyListAddedPeriod(@androidx.annotation.StringRes val titleRes: Int) {
    TODAY(R.string.mylist_added_today),
    YESTERDAY(R.string.mylist_added_yesterday),
    THIS_WEEK(R.string.mylist_added_this_week),
    LAST_WEEK(R.string.mylist_added_last_week),
    THIS_MONTH(R.string.mylist_added_this_month),
    LAST_MONTH(R.string.mylist_added_last_month),
    LAST_3_MONTHS(R.string.mylist_added_last_3_months),
    THIS_YEAR(R.string.mylist_added_this_year),
    LAST_YEAR(R.string.mylist_added_last_year),
    OLDER(R.string.mylist_added_older),
    UNKNOWN(R.string.mylist_added_unknown)
}

/**
 * Bir kaydın "etkin eklenme zamanı" (epoch saniye) — sıralama ve dönem bölümleri
 * **aynı** değeri görmek zorundadır, yoksa aynı satır bir sekmede tarihli, diğerinde
 * "Eklenme Tarihi Bilinmiyor" olarak görünür.
 *
 * Öncelik:
 * 1. `addedAt` — kaynağın gerçek/ilk-görülme eklenme zamanı.
 * 2. `updatedAt` — kaynak eklenme zamanı vermediğinde (veya eski satırlarda alan
 *    henüz doldurulmamışken) elde olan tek zaman damgası. "Son değişiklik" ile
 *    "eklenme" aynı şey değildir; bu yüzden bu bir **vekil**dir, ama bilinmeyen
 *    bir kovaya atmaktan iyidir: iki alanı da boş olan satırlar gerçekten
 *    bilinmiyordur ve sonda, ada göre deterministik sıralanır.
 *    (Kalıcı düzeltme `MyListViewModel.repairMissingAddedAt` tarafından
 *    yapılır; buradaki yedek yalnızca okuma yolunu korur.)
 */
internal fun MediaEntry.effectiveAddedSeconds(): Long {
    val created = com.kitsugi.animelist.model.normalizeToEpochSeconds(createdAt)
    if (created > 0L) return created
    val direct = com.kitsugi.animelist.model.normalizeToEpochSeconds(addedAt)
    if (direct > 0L) return direct
    return com.kitsugi.animelist.model.normalizeToEpochSeconds(updatedAt)
}

internal fun addedSortTimestamp(entry: MediaEntry): Long = entry.effectiveAddedSeconds()


/**
 * (Zaten eklenme tarihine göre sıralı) listeyi dönem bölümlerine ayırır.
 * Bölüm sırası, girdide ilk görülme sırasıdır — böylece azalan sıralamada
 * "Bugün" önce, artan sıralamada en eski dönem önce gelir; iki yönde de
 * ek bir bölüm-sırası tablosuna gerek yoktur.
 */
internal fun groupMyListEntriesByAddedPeriod(
    entries: List<MediaEntry>,
    nowEpochSeconds: Long = System.currentTimeMillis() / 1000L
): List<Pair<MyListAddedPeriod, List<MediaEntry>>> {
    if (entries.isEmpty()) return emptyList()
    val zone = java.time.ZoneId.systemDefault()
    val today = java.time.Instant.ofEpochSecond(nowEpochSeconds).atZone(zone).toLocalDate()
    val groups = LinkedHashMap<MyListAddedPeriod, MutableList<MediaEntry>>()
    entries.forEach { entry ->
        val addedSec = entry.effectiveAddedSeconds()
        val period = if (addedSec <= 0L) {
            MyListAddedPeriod.UNKNOWN
        } else {
            val date = java.time.Instant.ofEpochSecond(addedSec).atZone(zone).toLocalDate()
            val days = java.time.temporal.ChronoUnit.DAYS.between(date, today)
            when {
                days <= 0L -> MyListAddedPeriod.TODAY // saat dilimi farkıyla "yarın" damgalılar da bugün sayılır
                days == 1L -> MyListAddedPeriod.YESTERDAY
                days < 7L -> MyListAddedPeriod.THIS_WEEK
                days < 14L -> MyListAddedPeriod.LAST_WEEK
                date.year == today.year && date.month == today.month -> MyListAddedPeriod.THIS_MONTH
                date.year == today.minusMonths(1).year && date.month == today.minusMonths(1).month ->
                    MyListAddedPeriod.LAST_MONTH
                days < 90L -> MyListAddedPeriod.LAST_3_MONTHS
                date.year == today.year -> MyListAddedPeriod.THIS_YEAR
                date.year == today.year - 1 -> MyListAddedPeriod.LAST_YEAR
                else -> MyListAddedPeriod.OLDER
            }
        }
        groups.getOrPut(period) { mutableListOf() } += entry
    }
    return groups.entries.map { (period, periodEntries) -> period to periodEntries.toList() }
}

/** Sıralama anahtarları satır başına BİR kez hesaplanır; karşılaştırıcı yalnızca okur. */
private class MyListSortKeys(
    val entry: MediaEntry,
    /** Ekranda görülen başlığın (başlık dili uygulanmış) küçük harfli hali. */
    val titleKey: String,
    val addedAtSec: Long,
    val updatedAtSec: Long,
    /** Puanı olmayan (null/0) kayıt null — iki yönde de sona düşer. */
    val scoreKey: Int?,
    /** 0..1 arası tamamlanma oranı; toplam bilinmiyorsa null. */
    val progressRatio: Float?,
    val startDateKey: String?,
    val endDateKey: String?,
    val yearKey: Int?
)

/**
 * Tarih metnini sözlüksel karşılaştırmaya uygun `yyyy-MM-dd` biçimine normalize
 * eder (tek haneli ay/gün sıfırla doldurulur; saat eki atılır). Boş/geçersiz → null.
 */
private fun sortableDateKey(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty() || value.equals("null", ignoreCase = true)) return null
    val parts = value.split('-')
    if (parts.size < 3) return value // bilinmeyen biçim: olduğu gibi karşılaştır, "var" say
    val year = parts[0].takeWhile { it.isDigit() }.padStart(4, '0')
    val month = parts[1].takeWhile { it.isDigit() }.padStart(2, '0')
    val day = parts[2].takeWhile { it.isDigit() }.padStart(2, '0')
    if (year.isBlank() || month.isBlank() || day.isBlank()) return value
    return "$year-$month-$day"
}

/**
 * Listem sıralama motoru — her seçeneğin KENDİ mantığı vardır:
 *
 * - `newest` / `oldest` ("Son/İlk eklenen"): gerçek eklenme zamanı
 *   ([MediaEntry.addedAt]). Eskiden yerel satır `id`'si kullanılıyordu; toplu
 *   içe aktarımda id sırası kullanıcının ekleme sırası DEĞİLDİR (AniList sayfaları
 *   MEDIA_ID sırasıyla gelir) — "çalışmıyor" şikâyetinin kökü buydu. Eklenme
 *   zamanı bilinmeyen kayıt iki yönde de sona düşer; bilinmeyenler kendi içinde
 *   güncelleme zamanı + yerel id ile sıralanır (en iyi vekil).
 * - `title` / `title_desc`: EKRANDA GÖRÜLEN başlığa göre (başlık dili uygulanır);
 *   önceden hep depolanan romaji başlık kullanılıyordu, İngilizce başlık dilinde
 *   sıra ekrandakiyle uyuşmuyordu. Aynı adlı kayıtlarda (sezonlar) yayın yılı artan.
 * - `score` / `score_asc`: puansız kayıt iki yönde de SONDA (önceden artan
 *   sıralamada puansızlar başa kaçabiliyordu).
 * - `progress` / `progress_asc`: tamamlanma ORANI (progress/total). Önceden ham
 *   bölüm sayısıydı: 100/1000'lik manga, bitmiş 12/12'lik animenin üstündeydi.
 *   Toplamı bilinmeyen kayıt oran sırasında sonda, kendi içinde bölüm sayısına göre.
 * - `favorites`: favoriler önde; her küme kendi içinde son eklenene göre
 *   (önceden yalnızca ada göreydi — yeni favoriler arada kayboluyordu).
 * - `start_date_*` / `end_date_*` / `year_*` / `updated_*` / `priority_*`:
 *   değeri olmayan kayıt iki yönde de sonda.
 * - `updated_*`: Bangumi'nin milisaniye yazdığı değerler saniyeye normalize
 *   edilir; yoksa Bangumi satırları (×1000) her zaman haksızca en üstteydi.
 * - [MY_LIST_SORT_DEFAULT]: bölüm içi gezinme sırası = son eklenen mantığı.
 *
 * Tüm seçeneklerde son eşitlik bozma deterministiktir (başlık → kimlik);
 * anahtarlar satır başına bir kez hesaplanır (Schwartzian transform — karşılaştırıcı
 * içinde `lowercase()`/tarih ayrıştırma tekrar tekrar çalışmaz).
 */
internal fun applySort(
    entries: List<MediaEntry>,
    sortId: String,
    titleLanguage: String? = null
): List<MediaEntry> {
    // "Varsayılan": liste KENDİ sırasında bırakılır. Sıralama seçimi gruplamayı
    // kaldırmadığı için varsayılan görünüm (durum/tarih başlıkları) korunur.
    if (entries.size < 2 || sortId == "default" || sortId.isEmpty()) return entries

    val decorated = entries.map { entry ->
        val displayTitle = if (titleLanguage != null) {
            with(com.kitsugi.animelist.utils.PreferenceHelpers) { entry.getDisplayTitle(titleLanguage) }
        } else {
            entry.title
        }
        MyListSortKeys(
            entry = entry,
            titleKey = displayTitle.trim().lowercase(),
            addedAtSec = entry.effectiveAddedSeconds(),
            updatedAtSec = com.kitsugi.animelist.model.normalizeToEpochSeconds(entry.updatedAt),
            scoreKey = entry.score?.takeIf { it > 0 },
            progressRatio = entry.total?.takeIf { it > 0 }?.let { total ->
                (entry.progress.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            },
            startDateKey = sortableDateKey(entry.startDate),
            endDateKey = sortableDateKey(entry.endDate),
            yearKey = entry.year?.takeIf { it > 0 }
        )
    }

    // Ortak eşitlik zincirleri
    val byAddedDescThenTitle: Comparator<MyListSortKeys> =
        compareByDescending<MyListSortKeys> { it.addedAtSec }
            .thenBy { it.titleKey }
            .thenByDescending { it.entry.id }
    val byAddedAscThenTitle: Comparator<MyListSortKeys> =
        compareBy<MyListSortKeys> { it.addedAtSec <= 0L }
            .thenBy { it.addedAtSec }
            .thenBy { it.titleKey }
            .thenBy { it.entry.id }

    /**
     * "Son eklenen": gerçek eklenme zamanı azalan; zamanı BİLİNMEYENLER her iki yönde de
     * sonda ve kendi aralarında **ada göre** (yerel `id`'ye göre değil).
     *
     * Eski zincir `updatedAt` + yerel `id` ile kırılıyordu. İki sorun:
     *  1. `id` kaynak başına farklı uzaydan gelir (Simkl 150M+, Kitsu 300M+, AniList
     *     yerel autoincrement) → "Tümü" sekmesinde aynı saniyede eklenmiş kayıtlar
     *     kaynak karışımına göre anlamsız sıralanıyordu.
     *  2. `updatedAt` ile kırınca izleme ilerlemesi güncellenen eski bir kayıt, yeni
     *     eklenmiş gibi öne fırlıyordu.
     * Artık bilinmeyenler ada göre deterministik; bilinenler aynı saniyeye düşerse
     * başlık (ve son çare kimlik) ile kararlı biçimde sıralanır.
     */
    fun addedTimeThenTitle(descending: Boolean): Comparator<MyListSortKeys> =
        Comparator { a, b ->
            val aKnown = a.addedAtSec > 0L
            val bKnown = b.addedAtSec > 0L
            if (aKnown != bKnown) {
                // Bilinen her zaman bilinmeyenin önünde (unknownLast her iki yönde de true).
                return@Comparator if (aKnown) -1 else 1
            }
            if (!aKnown) {
                val byTitle = a.titleKey.compareTo(b.titleKey)
                return@Comparator if (byTitle != 0) byTitle else a.entry.id.compareTo(b.entry.id)
            }
            val byTime = if (descending) b.addedAtSec.compareTo(a.addedAtSec)
            else a.addedAtSec.compareTo(b.addedAtSec)
            if (byTime != 0) return@Comparator byTime
            val byTitle = a.titleKey.compareTo(b.titleKey)
            if (byTitle != 0) return@Comparator byTitle
            a.entry.id.compareTo(b.entry.id)
        }

    val newestComparator: Comparator<MyListSortKeys> = addedTimeThenTitle(descending = true)
    val oldestComparator: Comparator<MyListSortKeys> = addedTimeThenTitle(descending = false)

    val comparator: Comparator<MyListSortKeys> = when (sortId) {
        // ── Eklenme tarihi ────────────────────────────────────────────────
        "newest", "added" -> newestComparator
        "oldest" -> oldestComparator

        // ── Başlık ────────────────────────────────────────────────────────
        "title" ->
            compareBy<MyListSortKeys> { it.titleKey }
                .thenBy { it.yearKey == null }
                .thenBy { it.yearKey ?: 0 }
                .thenBy { it.entry.id }
        "title_desc" ->
            compareByDescending<MyListSortKeys> { it.titleKey }
                .thenBy { it.yearKey == null }
                .thenBy { it.yearKey ?: 0 }
                .thenBy { it.entry.id }

        // ── Puan (puansız her yönde sonda) ────────────────────────────────
        "score" ->
            compareByDescending<MyListSortKeys> { it.scoreKey ?: -1 }
                .then(byAddedDescThenTitle)
        "score_asc" ->
            compareBy<MyListSortKeys> { it.scoreKey == null }
                .thenBy { it.scoreKey ?: 0 }
                .then(byAddedDescThenTitle)

        // ── İlerleme (tamamlanma oranı; toplam bilinmiyorsa sonda) ───────
        "progress" ->
            compareByDescending<MyListSortKeys> { it.progressRatio ?: -1f }
                .thenByDescending { it.entry.progress }
                .then(byAddedDescThenTitle)
        "progress_asc" ->
            compareBy<MyListSortKeys> { it.progressRatio == null }
                .thenBy { it.progressRatio ?: 0f }
                .thenBy { it.entry.progress }
                .then(byAddedDescThenTitle)

        // ── Favoriler (önce favoriler; küme içinde son eklenen üste) ─────
        "favorites" ->
            compareByDescending<MyListSortKeys> { it.entry.isFavorite }
                .then(byAddedDescThenTitle)

        // ── Başlangıç / bitiş tarihi (tarihsiz her yönde sonda) ──────────
        "start_date_desc" ->
            compareByDescending<MyListSortKeys> { it.startDateKey ?: "" }
                .then(byAddedDescThenTitle)
        "start_date_asc" ->
            compareBy<MyListSortKeys> { it.startDateKey == null }
                .thenBy { it.startDateKey ?: "" }
                .then(byAddedAscThenTitle)
        "end_date_desc" ->
            compareByDescending<MyListSortKeys> { it.endDateKey ?: "" }
                .then(byAddedDescThenTitle)
        "end_date_asc" ->
            compareBy<MyListSortKeys> { it.endDateKey == null }
                .thenBy { it.endDateKey ?: "" }
                .then(byAddedAscThenTitle)

        // ── Yayın yılı (yılsız her yönde sonda; eşitlikte ada göre) ──────
        "year_desc" ->
            compareByDescending<MyListSortKeys> { it.yearKey ?: 0 }
                .thenBy { it.titleKey }
                .thenByDescending { it.entry.id }
        "year_asc" ->
            compareBy<MyListSortKeys> { it.yearKey == null }
                .thenBy { it.yearKey ?: 0 }
                .thenBy { it.titleKey }
                .thenBy { it.entry.id }

        // ── Son güncelleme (normalize edilmiş; zamansız sonda) ───────────
        "updated_desc" ->
            compareByDescending<MyListSortKeys> { it.updatedAtSec }
                .then(byAddedDescThenTitle)
        "updated_asc" ->
            compareBy<MyListSortKeys> { it.updatedAtSec <= 0L }
                .thenBy { it.updatedAtSec }
                .then(byAddedAscThenTitle)

        // ── Tekrar sayısı ─────────────────────────────────────────────────
        "repeat_desc" ->
            compareByDescending<MyListSortKeys> { it.entry.repeatCount }
                .then(byAddedDescThenTitle)
        "repeat_asc" ->
            compareBy<MyListSortKeys> { it.entry.repeatCount }
                .thenBy { it.titleKey }
                .thenBy { it.entry.id }

        // ── Öncelik (önceliksiz her yönde sonda; 0 gerçek değerdir) ──────
        "priority_desc" ->
            compareByDescending<MyListSortKeys> { it.entry.priority ?: -1 }
                .then(byAddedDescThenTitle)
        "priority_asc" ->
            compareBy<MyListSortKeys> { it.entry.priority == null }
                .thenBy { it.entry.priority ?: 0 }
                .thenBy { it.titleKey }
                .thenBy { it.entry.id }

        // Varsayılan gezinme ve bilinmeyen seçimler: son eklenen mantığı.
        else -> newestComparator
    }

    return decorated.sortedWith(comparator).map { it.entry }
}

/** Gruplama kimliğinin kısa, yerelleştirilmiş etiketi (liste başlık özetinde kullanılır). */
@Composable
internal fun myListGroupingLabel(groupId: String): String = when (MyListGroupingIds.normalize(groupId)) {
    MyListGroupingIds.STATUS -> stringResource(R.string.mylist_group_status_short)
    MyListGroupingIds.NONE -> stringResource(R.string.mylist_group_none)
    else -> stringResource(R.string.mylist_group_added)
}

@Composable
internal fun getSortTitle(sortId: String): String = when (sortId) {
    "default" -> stringResource(R.string.mylist_sort_default)
    "newest" -> stringResource(R.string.mylist_sort_added_desc)
    "oldest" -> stringResource(R.string.mylist_sort_added_asc)
    "score" -> "Puan (En yüksek)"
    "score_asc" -> "Puan (En düşük)"
    else -> stringResource(
        when (sortId) {
            MY_LIST_SORT_DEFAULT -> R.string.mylist_sort_default
            "newest", "added" -> R.string.mylist_sort_added_desc
            "oldest" -> R.string.mylist_sort_added_asc
            "title" -> R.string.mylist_sort_title_asc
            "title_desc" -> R.string.mylist_sort_title_desc
            "progress" -> R.string.mylist_sort_progress_high
            "progress_asc" -> R.string.mylist_sort_progress_low
            "favorites" -> R.string.mylist_sort_favorites
            "start_date_desc" -> R.string.mylist_sort_start_new
            "start_date_asc" -> R.string.mylist_sort_start_old
            "end_date_desc" -> R.string.mylist_sort_end_new
            "end_date_asc" -> R.string.mylist_sort_end_old
            "year_desc" -> R.string.mylist_sort_year_new
            "year_asc" -> R.string.mylist_sort_year_old
            "updated_desc" -> R.string.mylist_sort_updated
            "updated_asc" -> R.string.mylist_sort_updated_old
            "repeat_desc" -> R.string.mylist_sort_repeat_high
            "repeat_asc" -> R.string.mylist_sort_repeat_low
            "priority_desc" -> R.string.mylist_sort_priority_high
            "priority_asc" -> R.string.mylist_sort_priority_low
            else -> R.string.mylist_sort_default
        }
    )
}

internal fun cardSpacingForLayout(layoutId: String): Dp {
    return when (layoutId) {
        "compact"    -> 8.dp
        "large"      -> 18.dp
        "grid_2col"  -> 8.dp
        else         -> 12.dp  // comfortable (varsayılan)
    }
}
