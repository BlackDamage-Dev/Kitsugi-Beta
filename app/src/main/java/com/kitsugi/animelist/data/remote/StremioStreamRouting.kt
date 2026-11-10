package com.kitsugi.animelist.data.remote

/**
 * Stremio stream yönlendirme kuralları — tüm stream yollarının (AddonStreamRepository ve
 * StreamViewModel) TEK kaynağı. Android / Gson / Room bağımlılığı YOKTUR; birim testlenebilir.
 *
 * Stremio manifest sözleşmesi (Addon SDK / stremio-core):
 *  - `resources[]` kısa biçimde (`"stream"`) ise `types` ve `idPrefixes` manifest düzeyinden miras alınır.
 *  - Tam biçimde (`{ "name": "stream", "types": [...], "idPrefixes": [...] }`) ise bu alanlar
 *    manifest düzeyinin yerine geçer.
 *  - İstek yolu, addon'un ilan ettiği türü kullanır: `/stream/{type}/{id}.json`.
 *    Yalnızca `anime` ilan eden bir addon `/stream/series/...` isteğine boş döner
 *    (AIOStreams #930 ile aynı hata sınıfı). Bu yüzden istek tipi, addon'un beyanından seçilir.
 *  - ID prefix'i `kitsu` ile `kitsu:` aynı kimlik ailesidir; manifestler ikisini de kullanır.
 *    `startsWith` karşılaştırması ikisini de doğru eşler.
 */
object StremioStreamRouting {

    /**
     * Stream resource'un manifestten ayrıştırılmış hali.
     * `types == null`  → hiçbir yerde tür beyan edilmemiş (tür filtresi yok).
     * `idPrefixes == null` → prefix kısıtı yok (her ID kabul).
     */
    data class StreamResourceSpec(
        val types: List<String>?,
        val idPrefixes: List<String>?
    )

    /**
     * Stream resource var ama tür beyan etmiyor. Veritabanı sütununda "herhangi tür" anlamına gelir.
     * Gerçek bir Stremio tür adı değildir; `resolveRequestType` bunu içerik türüyle aynen eşler.
     */
    const val ANY_TYPE = "*"

    /** Series isteği için kabul edilen addon türleri, öncelik sırasıyla. */
    private val SERIES_ALIASES = listOf("series", "anime")

    /** Movie isteği için kabul edilen addon türleri, öncelik sırasıyla. */
    private val MOVIE_ALIASES = listOf("movie", "animemovie", "anime")

    /** Virgülle ayrılmış tür listesini normalize eder (küçük harf, boşluksuz, boş öğesiz). */
    fun parseCsv(csv: String?): List<String> =
        csv?.split(',')?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }.orEmpty()

    /**
     * Bu addon `contentType` (series / movie) için hangi tür adıyla istek atmalı?
     * Uyumsuzsa null döner (addon bu içerik türünü hiç sunmuyor).
     */
    fun resolveRequestType(streamTypesCsv: String?, contentType: String): String? {
        val declared = parseCsv(streamTypesCsv)
        if (declared.isEmpty() || ANY_TYPE in declared) return contentType
        val want = contentType.lowercase()
        if (want in declared) return contentType
        val aliases = if (want == "movie") MOVIE_ALIASES else SERIES_ALIASES
        return aliases.firstOrNull { it in declared }
    }

    /** Video ID'si addon'un prefix listesiyle uyuşuyor mu? Boş liste = her ID kabul. */
    fun idPrefixAccepts(prefixes: List<String>, videoId: String): Boolean =
        prefixes.isEmpty() || prefixes.any { prefix -> prefix.isNotBlank() && videoId.startsWith(prefix) }

    /**
     * `resources[]` dizisinden stream resource'unu çıkarır (kısa ve tam biçim).
     * Stream resource yoksa null.
     */
    fun parseStreamResource(
        resources: List<*>?,
        manifestTypes: List<String>,
        manifestIdPrefixes: List<String>
    ): StreamResourceSpec? {
        if (resources == null) return null
        for (res in resources) {
            if (res is String) {
                if (res == "stream") {
                    return StreamResourceSpec(
                        types = manifestTypes.takeIf { it.isNotEmpty() },
                        idPrefixes = manifestIdPrefixes.takeIf { it.isNotEmpty() }
                    )
                }
            } else if (res is Map<*, *>) {
                if (res["name"] != "stream") continue
                val ownTypes = stringList(res["types"])
                val ownPrefixes = stringList(res["idPrefixes"])
                return StreamResourceSpec(
                    types = ownTypes.takeIf { it.isNotEmpty() } ?: manifestTypes.takeIf { it.isNotEmpty() },
                    // A full resource object with no idPrefixes is explicitly unrestricted;
                    // only the short string form inherits the manifest-level prefix list.
                    idPrefixes = if (res.containsKey("idPrefixes")) ownPrefixes else null
                )
            }
        }
        return null
    }

    private fun stringList(value: Any?): List<String> =
        (value as? List<*>)
            ?.filterIsInstance<String>()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
}

/** Veritabanından okunan bir addon'un stream yönlendirme görünümü (Android bağımsız). */
data class StremioAddonRoute(
    val name: String,
    val manifestUrl: String,
    /** null = eski kayıt / tür filtresi yok; "*" = herhangi tür; aksi halde virgüllü liste. */
    val streamTypes: String?,
    /** Stream için geçerli ID prefix'leri. Boş = kısıt yok. */
    val idPrefixes: List<String>,
    /** Altyazı türleri. Stream resource'u olmayan altyazı-only eski kayıtları ayırt etmek için. */
    val subtitleTypes: String?
)

/** Bir addon'a bir video ID'si için atılacak tek bir stream isteği. */
data class StremioStreamTask(
    val addonName: String,
    val manifestUrl: String,
    /** `/stream/{requestType}/...` yolunda kullanılacak tür (addon'un beyan ettiği ad). */
    val requestType: String,
    val videoId: String
)

/**
 * Her addon × manifestte desteklediği video ID ailesi için istek görevi üretir.
 * Prefix'i olmayan kaynaklarda kanıtı olmayan özel namespace'leri tahmin etmez; yaygın IMDb/Kitsu
 * kimlikleriyle güvenli fallback yapar.
 */
fun buildStremioStreamTasks(
    routes: List<StremioAddonRoute>,
    contentType: String,
    imdbVideoId: String?,
    kitsuVideoId: String?,
    additionalVideoIds: List<String> = emptyList()
): List<StremioStreamTask> {
    val tasks = ArrayList<StremioStreamTask>()
    val allCandidates = (listOfNotNull(kitsuVideoId, imdbVideoId) + additionalVideoIds).distinct()
    for (route in routes) {
        val candidates = if (route.idPrefixes.isEmpty()) {
            // No manifest filter has no evidence that a plugin understands custom namespaces.
            // Preserve the common Kitsu/IMDb fallback, and only send other families by prefix.
            listOfNotNull(kitsuVideoId, imdbVideoId)
        } else {
            allCandidates
        }
        for (videoId in candidates) {
            val requestType = requestTypeFor(route, contentType, videoId) ?: continue
            tasks += StremioStreamTask(route.name, route.manifestUrl, requestType, videoId)
        }
    }
    return tasks
}

/** Tek bir addon/ID çifti için istek türü; uygun değilse null. */
internal fun requestTypeFor(route: StremioAddonRoute, contentType: String, videoId: String): String? {
    // Eski altyazı-only satırları: tür bilgisi "stream" değil altyazıya aitse stream sorgulanmaz.
    if (route.streamTypes == null && route.subtitleTypes != null) return null
    val requestType = StremioStreamRouting.resolveRequestType(route.streamTypes, contentType) ?: return null
    if (!StremioStreamRouting.idPrefixAccepts(route.idPrefixes, videoId)) return null
    return requestType
}
