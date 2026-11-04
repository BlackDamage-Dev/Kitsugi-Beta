package com.kitsugi.animelist.utils

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.R
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/** Wire roles of organizations; *names* are never translated, only role labels are. */
enum class TaxonomyOrgRole(val wireName: String) {
    STUDIO("studio"), PRODUCER("producer"), PUBLISHER("publisher"),
    NETWORK("network"), MAGAZINE("magazine");

    companion object {
        fun fromWire(value: String): TaxonomyOrgRole =
            entries.firstOrNull { it.wireName == value }
                ?: throw IllegalArgumentException("Unknown organization role: $value")
    }
}

/** Search aliases may be Cyrillic or CJK; only [tr]/[en] are ever shown to users. */
data class CatalogTerm(
    val key: String,
    val tr: String,
    val en: String,
    val aliases: List<String>,
    val malGenreId: Int? = null,
) {
    private val searchTerms = (listOf(tr, en) + aliases).map { TaxonomyCatalog.fold(it) }

    fun displayName(turkish: Boolean): String = if (turkish) tr else en

    fun matches(query: String): Boolean = matchesFolded(TaxonomyCatalog.fold(query))

    internal fun matchesFolded(needle: String): Boolean =
        needle.isNotEmpty() && searchTerms.any { it.contains(needle) }
}

data class CatalogOrganization(
    val canonicalKey: String,
    val role: TaxonomyOrgRole,
    /** Same Latin name in Turkish and English. */
    val name: String,
    val aliases: List<String>,
) {
    private val searchTerms = (listOf(name) + aliases).map { TaxonomyCatalog.fold(it) }

    fun matches(query: String): Boolean = matchesFolded(TaxonomyCatalog.fold(query))

    internal fun matchesFolded(needle: String): Boolean =
        needle.isNotEmpty() && searchTerms.any { it.contains(needle) }
}

/**
 * One immutable, generated catalog shared by the search facets. Parsed once off the UI
 * thread from res/raw/taxonomy_catalog.json. The Android UI never builds a second hardcoded
 * genre/tag list and never displays the source-language aliases. Generator: scripts/gen_taxonomy_catalog.py.
 */
class TaxonomyCatalog private constructor(
    val genres: List<CatalogTerm>,
    val tags: List<CatalogTerm>,
    val organizations: List<CatalogOrganization>,
) {
    private val organizationsByKey = organizations.associateBy { it.canonicalKey }

    fun organization(canonicalKey: String): CatalogOrganization? = organizationsByKey[canonicalKey]

    companion object {
        @Volatile private var cached: TaxonomyCatalog? = null
        private val combiningMarks = Regex("\\p{Mn}+")
        private val foreignScript = Regex("[\\u3040-\\u30ff\\u3400-\\u9fff\\uac00-\\ud7af\\u0400-\\u052f]")

        /** Case/diacritic-insensitive search including ё→е and Turkish dotted/dotless i. */
        fun fold(value: String): String = Normalizer.normalize(
            value.trim().lowercase(Locale.ROOT).replace('ı', 'i'), Normalizer.Form.NFD
        ).replace(combiningMarks, "")

        /**
         * Kataloğu yükler; başarılı sonuç **kalıcı** önbelleğe alınır.
         *
         * ⚠️ Başarısız okuma önbelleğe alınmaz (eski davranış `runCatching { load() }`
         * çağıranlarıyla birleşince ilk hata — ör. ilk açılışta düşük bellek / kısa
         * süreli I/O hatası — oturum boyunca "katalog yüklenemedi" olarak kalıyor ve
         * Kurumlar listesi 0 kayıt gösteriyordu). Ayrıca [invalidate] ile bellek
         * baskısında önbellek boşaltılabilir.
         */
        fun load(context: Context): TaxonomyCatalog {
            cached?.let { return it }
            return synchronized(this) {
                cached ?: context.resources.openRawResource(R.raw.taxonomy_catalog)
                    .bufferedReader(Charsets.UTF_8).use { parse(it.readText()) }
                    .also {
                        cached = it
                        Log.i("KitsugiSearch", "Taksonomi kataloğu: tür=${it.genres.size} " +
                            "etiket=${it.tags.size} kurum=${it.organizations.size}")
                    }
            }
        }

        /** Önbelleği bırakır; bir sonraki [load] diski yeniden okur. */
        fun invalidate() {
            cached = null
        }

        /** Önbellekte hazır katalog var mı (test/telemetri). */
        val isLoaded: Boolean get() = cached != null

        /** Pure parser for JVM tests (the caller handles I/O on Dispatchers.IO). */
        fun parse(json: String): TaxonomyCatalog {
            val root = JSONObject(json)
            require(root.getInt("version") == 1) { "Unknown taxonomy catalog schema" }
            val genres = root.getJSONArray("genres").terms()
            val tags = root.getJSONArray("tags").terms()
            val orgArray = root.getJSONArray("organizations")
            val organizations = List(orgArray.length()) { index ->
                val row = orgArray.getJSONObject(index)
                CatalogOrganization(
                    canonicalKey = row.getString("key"),
                    role = TaxonomyOrgRole.fromWire(row.getString("role")),
                    name = safeName(row.getString("name")),
                    aliases = row.getJSONArray("aliases").strings(),
                )
            }
            require(genres.size == 114 && tags.size >= 600 && organizations.size >= 9457) {
                "Incomplete search taxonomy: ${genres.size}/${tags.size}/${organizations.size}"
            }
            require(genres.map { it.key }.distinct().size == genres.size &&
                tags.map { it.key }.distinct().size == tags.size &&
                organizations.map { it.canonicalKey }.distinct().size == organizations.size) {
                "Duplicate taxonomy key"
            }
            return TaxonomyCatalog(genres, tags, organizations)
        }

        private fun safeName(value: String): String {
            require(value.isNotBlank() && !foreignScript.containsMatchIn(value)) {
                "Non-Latin/empty taxonomy display name"
            }
            return value
        }

        private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }

        private fun JSONArray.terms(): List<CatalogTerm> = List(length()) { index ->
            val row = getJSONObject(index)
            CatalogTerm(
                key = row.getString("key"),
                tr = safeName(row.getString("tr")),
                en = safeName(row.getString("en")),
                aliases = row.getJSONArray("aliases").strings(),
                malGenreId = if (row.isNull("malId")) null else row.getInt("malId"),
            )
        }
    }
}
