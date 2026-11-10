package com.kitsugi.animelist.data.remote

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.R
import org.json.JSONArray
import org.json.JSONObject

/**
 * Paketlenmiş, açık kaynak Bangumi↔MAL anime eşlemesi.
 *
 * Veri yalnız ADAY üretir. Yazmadan önce Bangumi subject veya MAL kataloğu
 * başlık/tür/yıl doğrulaması yine zorunludur. `conflicts.json` içinde seçilen ve
 * reddedilen bütün tartışmalı çiftler güvenli tarafta kalmak için elenir.
 *
 * Kaynak/lisans: docs/bangumi/BANGUMI_MAL_CROSSWALK_SOURCES.md (CC BY 4.0).
 */
object BangumiMalCrosswalk {
    private const val TAG = "BangumiMalCrosswalk"

    internal data class Snapshot(
        val malToBangumi: Map<Int, List<Int>>,
        val bangumiToMal: Map<Int, Int>
    ) {
        companion object {
            val EMPTY = Snapshot(emptyMap(), emptyMap())
        }
    }

    @Volatile
    private var cached: Snapshot? = null

    fun bangumiSubjectsForMal(context: Context, malId: Int): List<Int> =
        snapshot(context).malToBangumi[malId].orEmpty()

    fun malIdForBangumi(context: Context, bangumiSubjectId: Int): Int? =
        snapshot(context).bangumiToMal[bangumiSubjectId]

    private fun snapshot(context: Context): Snapshot {
        cached?.let { return it }
        return synchronized(this) {
            cached?.let { return@synchronized it }
            val loaded = runCatching {
                val appContext = context.applicationContext
                val mapping = appContext.resources.openRawResource(R.raw.bangumi_mal_crosswalk)
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
                val conflicts = appContext.resources.openRawResource(R.raw.bangumi_mal_conflicts)
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
                parse(mapping, conflicts)
            }.getOrElse {
                Log.w(TAG, "Paketlenmiş Bangumi↔MAL eşlemesi okunamadı: ${it.message}")
                Snapshot.EMPTY
            }
            cached = loaded
            loaded
        }
    }

    internal fun parse(mappingJson: String, conflictsJson: String): Snapshot {
        val disputed = mutableSetOf<Pair<Int, Int>>()
        val conflictArray = JSONArray(conflictsJson)
        for (index in 0 until conflictArray.length()) {
            val row = conflictArray.optJSONObject(index) ?: continue
            val bangumiId = row.optInt("bangumi_id", 0).takeIf { it > 0 } ?: continue
            for (side in listOf("chosen", "rejected")) {
                val malId = row.optJSONObject(side)?.optInt("mal_id", 0)?.takeIf { it > 0 }
                if (malId != null) disputed += bangumiId to malId
            }
        }

        val forward = mutableMapOf<Int, MutableList<Int>>()
        val reverse = mutableMapOf<Int, Int>()
        val mapping = JSONObject(mappingJson)
        val keys = mapping.keys()
        while (keys.hasNext()) {
            val bangumiId = keys.next().toIntOrNull()?.takeIf { it > 0 } ?: continue
            if (mapping.isNull(bangumiId.toString())) continue
            val malId = mapping.optInt(bangumiId.toString(), 0).takeIf { it > 0 } ?: continue
            if ((bangumiId to malId) in disputed) continue
            forward.getOrPut(malId) { mutableListOf() }.add(bangumiId)
            reverse[bangumiId] = malId
        }
        return Snapshot(
            malToBangumi = forward.mapValues { (_, ids) -> ids.distinct().sorted() },
            bangumiToMal = reverse
        )
    }
}
