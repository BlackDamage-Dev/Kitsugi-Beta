package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.utils.KitsugiSpoilerRules
import com.kitsugi.animelist.utils.isUnresolvedForeignLabel
import com.kitsugi.animelist.utils.taxonomyCanonicalKey
import com.kitsugi.animelist.utils.toDisplayOrgName
import com.kitsugi.animelist.utils.toLocalizedTaxonomyLabel
import java.util.Locale

/**
 * **Tüm kaynakların tür / etiket / kurum verisini birleştiren motor.**
 *
 * Uygulama yedi detay kaynağını destekler (AniList, MAL/Jikan, Bangumi, Shikimori, Kitsu,
 * Simkl, TMDB). Bugüne kadar birleştirme *"birincil kaynakta varsa onu kullan, yoksa
 * ikincile düş"* biçimindeydi; bu yüzden **birleşim değil yerine koyma** oluyordu:
 * Bangumi'de `genres` boşsa MAL türleri geliyor, ama Bangumi'nin kendi kurumları ve
 * etiketleri diğer kaynaklarda yoksa **kayboluyordu**.
 *
 * Bu nesne:
 *  • **Türler** için diller arası kanonik anahtar (`Экшен` ≡ `Action` ≡ `动作` ≡ `Aksiyon`)
 *    üzerinden tekilleştirilmiş **birleşim** üretir; birincil kaynağın özgün yazımı korunur,
 *    gösterim [toLocalizedTaxonomyLabel] ile yapılır.
 *  • **Etiketler** için spoiler bayrağı ve `rank` bilgisini koruyarak birleştirir
 *    ([KitsugiSpoilerRules.mergeTagsPreservingSpoilers]).
 *  • **Kurumlar** (stüdyo / yapımcı / yayıncı / kanal / dergi) için Latin ada göre
 *    tekilleştirir; aynı kurum birden fazla kaynakta geçiyorsa **kimliği olan** kayıt
 *    (tıklanabilir stüdyo sayfası) tercih edilir.
 *
 * Hiçbir adım istisna fırlatmaz: bozuk/eksik veri sessizce atlanır, sayfa açılışı bozulmaz.
 */
internal object KitsugiTaxonomyMerge {

    /**
     * Bir kaynağın tür listesini diğerleriyle **birleştirir** (yerine koymaz).
     *
     * Sıra korunur: `primary` → `extras` sırayla. Tekilleştirme kanonik anahtar üzerinden
     * yapıldığı için `Action`, `Экшен`, `アクション`, `Aksiyon` tek çip olur; ekranda
     * birincil kaynağın özgün yazımı kalır.
     *
     * Sözlükte karşılığı olmayan Çince/Japonca türler listeye **alınmaz** (ekranda
     * okunamaz metin görünmesin); Rusça değerler her zaman Latin harfine çevrilebilir.
     */
    fun mergeGenres(
        primary: List<String>,
        vararg extras: List<String>,
        limit: Int = 160
    ): List<String> {
        if (primary.isEmpty() && extras.all { it.isEmpty() }) return emptyList()
        val result = ArrayList<String>(limit)
        val seen = HashSet<String>(limit * 2)

        fun add(list: List<String>) {
            for (raw in list) {
                if (result.size >= limit) return
                val value = raw.trim()
                if (value.isEmpty()) continue
                if (isUnresolvedForeignLabel(value)) continue
                val key = value.taxonomyCanonicalKey().ifEmpty { value.lowercase(Locale.ROOT) }
                if (!seen.add(key)) continue
                result.add(value)
            }
        }

        add(primary)
        for (extra in extras) add(extra)
        return result
    }

    /**
     * Etiketleri kaynak sırasına göre birleştirir.
     *
     * - Her grupta önce gelen kayıt korunur (özgün ad + `source`), eşleşen kayıtlardan
     *   spoiler bayrağı ve en yüksek `rank` aktarılır.
     * - Diller arası eşleşme ([KitsugiSpoilerRules.canonicalTagKey]) sayesinde
     *   `Повседневность` + `Slice of Life` + `日常` tek etiket olur.
     * - `limit` aşımında spoiler etiketleri kesilmez.
     */
    fun mergeTags(vararg groups: List<KitsugiTag>, limit: Int = 600): List<KitsugiTag> {
        if (groups.all { it.isEmpty() }) return emptyList()
        var merged = emptyList<KitsugiTag>()
        for (group in groups) {
            if (group.isEmpty()) continue
            merged = if (merged.isEmpty()) {
                KitsugiSpoilerRules.normalizeAndMergeTags(group)
            } else {
                KitsugiSpoilerRules.mergeTagsPreservingSpoilers(merged, group, limit = limit)
            }
        }
        return KitsugiSpoilerRules.applySpoilerFlags(merged)
    }

    /**
     * Kurum listelerini (stüdyo/yapımcı/ağ/dergi) Latin ada göre birleştirir.
     *
     * Tekilleştirme anahtarı [orgDedupKey]'dir: yerelleştirilmiş Latin ad + rol.
     * Aynı ad iki kaynakta görünüyorsa **kimliği (`id > 0`) ve görseli olan** kayıt seçilir;
     * böylece stüdyo çipine tıklanınca gerçek stüdyo sayfası açılır.
     */
    fun mergeOrgs(vararg groups: List<KitsugiStudio>, limit: Int = 256): List<KitsugiStudio> {
        if (groups.all { it.isEmpty() }) return emptyList()
        val result = ArrayList<KitsugiStudio>(limit)
        val indexByKey = HashMap<String, Int>(limit * 2)

        for (group in groups) {
            for (org in group) {
                val name = org.name.trim()
                if (name.isEmpty()) continue
                val key = orgDedupKey(org)
                if (key.isEmpty()) continue
                val existingIdx = indexByKey[key]
                if (existingIdx == null) {
                    if (result.size >= limit) continue
                    indexByKey[key] = result.size
                    result.add(org.copy(name = name))
                } else {
                    result[existingIdx] = preferOrg(result[existingIdx], org.copy(name = name))
                }
            }
        }
        return result
    }

    /**
     * Yapımcı listesinden, stüdyo listesinde **zaten bulunan** kurumları çıkarır.
     *
     * **Kullanılmıyor.** Bu eleme kurum verisini eksiltiyordu: aynı şirket hem
     * `studios` hem `producers` altında geldiğinde yapımcı kaydı siliniyor, böylece
     * "Yapımcılar" listesi olduğundan az görünüyordu. Roller artık
     * [orgDedupKey] içinde ayrıştırıldığı için aynı kurum her rolde korunur.
     * Yalnızca eski çağrıların derlenmesi için tutulur.
     */
    @Deprecated("Kurum verisini eksiltiyor; roller orgDedupKey ile ayrıştırılıyor.")
    fun dropProducersThatAreStudios(
        producers: List<KitsugiStudio>,
        studios: List<KitsugiStudio>
    ): List<KitsugiStudio> {
        if (producers.isEmpty() || studios.isEmpty()) return producers
        val studioKeys = studios.map { orgDedupKey(it) }.filter { it.isNotEmpty() }.toHashSet()
        if (studioKeys.isEmpty()) return producers
        return producers.filter { orgDedupKey(it) !in studioKeys }
    }

    /**
     * Kurum tekilleştirme anahtarı: **rol + yerelleştirilmiş ad**, boşluk/noktalama
     * sadeleştirilmiş.
     *
     * Rol anahtara dahildir çünkü aynı şirket birden fazla rolde geçebilir
     * (stüdyo + yapımcı + lisans veren). Rolü anahtardan çıkarmak, sonraki rolün
     * ilk kayda yutulmasına ve kurumun listeden düşmesine yol açıyordu.
     */
    fun orgDedupKey(org: KitsugiStudio): String {
        val base = orgDedupKey(org.name)
        if (base.isEmpty()) return ""
        return org.role.name.lowercase(Locale.ROOT) + "|" + base
    }

    /** Ad üzerinden kurum tekilleştirme anahtarı. */
    fun orgDedupKey(rawName: String?): String {
        val name = rawName?.trim().orEmpty()
        if (name.isEmpty()) return ""
        val display = runCatching { name.toDisplayOrgName() }.getOrDefault(name)
        return display
            .lowercase(Locale.ROOT)
            .replace(Regex("[’'`´]"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), "")
            .trim()
    }

    /** İki kurum kaydından daha zengin olanı seçer (kimlik → görsel → ana/yan rol). */
    private fun preferOrg(existing: KitsugiStudio, candidate: KitsugiStudio): KitsugiStudio {
        if (existing.id > 0 && candidate.id <= 0) return existing
        if (candidate.id > 0 && existing.id <= 0) {
            return candidate.copy(
                isMain = existing.isMain || candidate.isMain,
                role = existing.role,
                imageUrl = candidate.imageUrl ?: existing.imageUrl
            )
        }
        return existing.copy(
            isMain = existing.isMain || candidate.isMain,
            imageUrl = existing.imageUrl ?: candidate.imageUrl,
            role = existing.role
        )
    }

    /**
     * Detayın **tür / etiket / kurum** alanlarını ikincil kaynaklarla birleştirir.
     *
     * [primary] açılan sayfanın kaynağıdır; özgün yazımlar ve kaynak adları korunur.
     * `secondary` boşsa [primary] aynen döner (gereksiz kopya üretilmez).
     */
    fun mergeDetail(
        primary: KitsugiMediaDetail,
        secondary: KitsugiMediaDetail?,
        genresLimit: Int = 160,
        tagsLimit: Int = 600
    ): KitsugiMediaDetail {
        if (secondary == null) return primary

        val mergedGenres = mergeGenres(primary.genres, secondary.genres, limit = genresLimit)
        val mergedTags = mergeTags(primary.tags, secondary.tags, limit = tagsLimit)
        val mergedStudios = mergeOrgs(primary.studios, secondary.studios)
        // Aynı şirket hem stüdyo hem yapımcı olabilir (ör. MAPPA). Eskiden
        // `dropProducersThatAreStudios` bu kaydı yapımcılardan SİLİYORDU; bu yüzden
        // iki rolde geçen kurumlar "kayıp" görünüyordu. Artık tekilleştirme rol
        // duyarlıdır: kurum her rolünde ayrı kayıt olarak korunur.
        val mergedProducers = mergeOrgs(primary.producers, secondary.producers)
        val mergedNetworks = mergeOrgs(primary.networks, secondary.networks)
        val mergedSerializations = mergeOrgs(primary.serializations, secondary.serializations)
        val mergedDemographics = mergeGenres(primary.demographics, secondary.demographics, limit = 32)
        val mergedThemes = mergeGenres(primary.themes, secondary.themes, limit = 64)

        if (mergedGenres == primary.genres &&
            mergedTags == primary.tags &&
            mergedStudios == primary.studios &&
            mergedProducers == primary.producers &&
            mergedNetworks == primary.networks &&
            mergedSerializations == primary.serializations &&
            mergedDemographics == primary.demographics &&
            mergedThemes == primary.themes
        ) {
            return primary
        }

        return primary.copy(
            genres = mergedGenres,
            tags = mergedTags,
            studios = mergedStudios,
            producers = mergedProducers,
            networks = mergedNetworks,
            serializations = mergedSerializations,
            demographics = mergedDemographics,
            themes = mergedThemes
        )
    }

    /**
     * Kurum listelerini modele **yazmadan önce** normalleştirir:
     * Rusça/Çince adlar Latin ada çevrilir, aynı kurum tek kez kalır.
     */
    fun normalizeOrgNames(orgs: List<KitsugiStudio>): List<KitsugiStudio> {
        if (orgs.isEmpty()) return orgs
        val normalized = orgs.map { org -> org.copy(name = org.name.toDisplayOrgName()) }
        return mergeOrgs(normalized)
    }

    /** Tür/etiket adlarını gösterim dilinde döndürür (testler ve teşhis için). */
    fun localizedLabels(values: List<String>): List<String> =
        values.map { it.toLocalizedTaxonomyLabel() }.filter { it.isNotBlank() }
}
