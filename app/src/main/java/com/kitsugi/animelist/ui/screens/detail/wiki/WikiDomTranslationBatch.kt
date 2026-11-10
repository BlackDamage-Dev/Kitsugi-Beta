package com.kitsugi.animelist.ui.screens.detail.wiki

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal data class WikiDomText(val id: Int, val revision: Int, val text: String)
internal data class WikiDomResult(val item: WikiDomText, val translated: String?)

/** Batches nearby text slots for context; validates every delimiter before assigning a
 * result. A damaged/reordered delimiter can never put text into a different link/node.
 *
 * Hız katmanı (sözleşme değişmeden):
 *  - Metinler önce **tek tek** önbelleğe sorulur; yalnızca eksikler paketlenip ağa gider.
 *    Sayfaya dönüşte veya paket dizilişi değiştiğinde çevrilmiş paragraflar yeniden
 *    çevrilmez.
 *  - Paketteki aynı metinler tekilleştirilir (tekrarlayan etiketler tek çeviri paylaşır).
 *  - Başarılı çeviriler tek tek önbelleğe yazılır; böylece önbellek paket dizilişinden
 *    bağımsız çalışır.
 *  - Ayraç bozulursa yapılan tekil yedek çeviriler paralel koşar (ağ kapısı çevirmen
 *    katmanında zaten sınırlıdır), sıra korunur.
 */
internal object WikiDomTranslationBatch {
    /** JS tarafındaki `take()` paket sınırıyla aynı tutulmalı (dom-translator.js). */
    const val MAX_ROWS = 16

    private val marker = Regex("⟪(/?)K([0-9]+)⟫")

    fun pack(rows: List<WikiDomText>): String = packTexts(rows.map { it.text })

    private fun packTexts(texts: List<String>): String = texts.mapIndexed { index, text ->
        "⟪K$index⟫$text⟪/K$index⟫"
    }.joinToString("\n")

    fun unpack(text: String, count: Int): List<String>? {
        val matches = marker.findAll(text).toList()
        if (matches.size != count * 2) return null
        val values = ArrayList<String>(count)
        var previousEnd = 0
        for (index in 0 until count) {
            val open = matches[index * 2]
            val close = matches[index * 2 + 1]
            if (open.value != "⟪K$index⟫" || close.value != "⟪/K$index⟫") return null
            if (text.substring(previousEnd, open.range.first).isNotBlank()) return null
            val value = text.substring(open.range.last + 1, close.range.first).trim()
            if (value.isBlank()) return null
            values.add(value)
            previousEnd = close.range.last + 1
        }
        return values.takeIf { text.substring(previousEnd).isBlank() }
    }

    suspend fun translate(
        rows: List<WikiDomText>,
        cached: suspend (String) -> String? = { null },
        store: suspend (List<Pair<String, String>>) -> Unit = {},
        translate: suspend (String) -> String?
    ): List<WikiDomResult> {
        require(rows.size in 1..MAX_ROWS)
        require(rows.all { it.text.length in 1..8000 })

        val distinctTexts = rows.map { it.text }.distinct()
        val translatedByText = HashMap<String, String?>()

        // 1) Ağsız önbellek: bulunan metinler pakete hiç girmez.
        val cacheHits = coroutineScope {
            distinctTexts.map { text ->
                async {
                    val hit = try {
                        cached(text)?.takeIf { it.isNotBlank() }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                    text to hit
                }
            }.awaitAll()
        }
        val misses = ArrayList<String>()
        for ((text, hit) in cacheHits) {
            if (hit != null) translatedByText[text] = hit else misses.add(text)
        }

        // 2) Eksikler: tek metin doğrudan, çok metin işaretli paketle (tek istek).
        if (misses.isNotEmpty()) {
            val fresh: Map<String, String?> = when {
                misses.size == 1 -> mapOf(misses[0] to translate(misses[0]))
                // Literal marker text must not be confused with our transport markers.
                misses.none { marker.containsMatchIn(it) } -> {
                    val response = translate(packTexts(misses))
                    val values = response?.let { unpack(it, misses.size) }
                    when {
                        // Outage: do not fan out sixteen more requests.
                        response == null -> misses.associateWith { null }
                        values != null -> misses.mapIndexed { i, text -> text to values[i] }.toMap()
                        // Provider changed delimiters: bounded, individual fallback,
                        // never positional guessing.
                        else -> translateIndividually(misses, translate)
                    }
                }
                else -> translateIndividually(misses, translate)
            }
            translatedByText.putAll(fresh)

            // 3) Yeni çevirileri tek tek sakla; yazım hatası çeviriyi düşürmez.
            val toStore = misses.mapNotNull { text ->
                fresh[text]?.takeIf { it.isNotBlank() }?.let { text to it }
            }
            if (toStore.isNotEmpty()) {
                try {
                    store(toStore)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Cache failure must not discard valid translations.
                }
            }
        }

        return rows.map { WikiDomResult(it, translatedByText[it.text]) }
    }

    private suspend fun translateIndividually(
        texts: List<String>,
        translate: suspend (String) -> String?
    ): Map<String, String?> = coroutineScope {
        texts.map { text -> async { text to translate(text) } }.awaitAll().toMap()
    }
}
