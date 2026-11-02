package com.kitsugi.animelist.data.local

import android.content.Context
import com.kitsugi.animelist.core.network.KitsugiHttpClient
import com.kitsugi.animelist.utils.KitsugiTranslatePrefs
import com.kitsugi.animelist.utils.cleanShikimoriBbCode
import com.kitsugi.animelist.utils.isLikelyTurkishText
import com.kitsugi.animelist.utils.isTechnicalTranslationValue
import com.kitsugi.animelist.utils.translationMatchesSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TranslationManager(context: Context) {
    companion object {
        /**
         * Eşzamanlı çeviri kapısı. Arka plan isim/yorum çevirilerinin aktif detay
         * sayfası açıklamasını/biyografisini kilitlememesi için 4 izin kullanılır.
         */
        private val requestGate = Semaphore(4)

        /**
         * Çeviri istekleri için özel OkHttp istemcisi:
         * Genel [KitsugiHttpClient.client] içindeki RetryInterceptor bir uç nokta 429
         * döndüğünde aynı URL'yi 3 kez bekleyerek tekrar deniyordu. Burada interceptor
         * zinciri temizlenerek uç noktalar arası anında yedekli geçiş (fallback) sağlanır.
         */
        private val translationHttpClient: OkHttpClient by lazy {
            KitsugiHttpClient.client.newBuilder()
                .apply { interceptors().clear() }
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .writeTimeout(8, TimeUnit.SECONDS)
                .build()
        }

        private const val MAX_CHUNK_CHARS = 1400

        private const val WEB_MAX_CHUNK_CHARS = 3600

        private const val MEMORY_CACHE_MAX_ENTRIES = 600
        private val memoryCache: MutableMap<String, String> = java.util.Collections.synchronizedMap(
            object : LinkedHashMap<String, String>(128, 0.75f, true) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<String, String>?
                ): Boolean = size > MEMORY_CACHE_MAX_ENTRIES
            }
        )

        private fun memoryGet(hash: String): String? = memoryCache[hash]?.takeIf { it.isNotBlank() }

        private fun memoryPut(hash: String, value: String) {
            if (value.isNotBlank()) memoryCache[hash] = value
        }

        private val MEDIA_EMBED_REGEX = Regex(
            """(?:!\[[^\]]*\]\((https?://[^\s)]+)\)|(?:img\d*|webm|youtube)\s*\(\s*([^\s)]+)\s*\))""",
            RegexOption.IGNORE_CASE
        )
        private val LINK_REGEX = Regex("""\[([^\]]+)\]\((https?://[^\s)]+)\)""")
        private val RAW_URL_REGEX = Regex("""https?://[^\s<>"'\)\]]+""")
        private val WIKI_TOKEN_REGEX = Regex(
            """\[\[[^\]]+\]\]|\{\{[^}]+\}\}|\b(?:File|Image|Category|Template|Dosya|Resim|Kategori|Şablon)\s*:\s*[^\s|]+|\b[\w.+-]+@[\w-]+\.[A-Za-z]{2,}\b""",
            RegexOption.IGNORE_CASE
        )
        private val WIKI_NAMESPACE_WORDS = listOf(
            "file", "image", "category", "template", "dosya", "resim", "kategori", "şablon"
        )


        /** Akışlı (streaming) çeviri sırasında UI'yı saniyede onlarca kez yeniden çizip
         * KitsugiMarkdownText regex ayrıştırmasını kilitlememek için minimum kısmi yayın aralığı. */
        private const val PARTIAL_EMIT_MIN_INTERVAL_MS = 250L

        /**
         * "Bu metin zaten hedef dilde" bilgisinin bellek içi defteri.
         *
         * Aynı Türkçe biyografi her açılışta Google'a gönderilmesin diye tutulur; kalıcı
         * değildir (uygulama yeniden başlarsa ilk istekte `src` alanından yeniden öğrenilir).
         * Sınırlı boyut: en fazla [ALREADY_TARGET_MEMORY] kayıt.
         */
        private const val ALREADY_TARGET_MEMORY = 256

        /**
         * accessOrder = true: okunan kayıt sona taşınır; böylece tahliye gerçekten
         * "en uzun süredir kullanılmayan" kaydı atar.
         */
        private val alreadyTargetCache = java.util.Collections.synchronizedMap(
            LinkedHashMap<String, Boolean>(64, 0.75f, true)
        )

        private fun alreadyTargetKey(text: String, targetLang: String): String =
            "${targetLang.lowercase()}|${text.trim().lowercase()}"

        /** Sınırı aşınca en eski kayıtları atar (bellek şişmesini engeller). */
        private fun pruneAlreadyTargetCache() {
            while (alreadyTargetCache.size > ALREADY_TARGET_MEMORY) {
                val eldest = alreadyTargetCache.keys.firstOrNull() ?: return
                alreadyTargetCache.remove(eldest)
            }
        }
    }

    private val dbContext = context.applicationContext
    private val db = KitsugiDatabase.getDatabase(dbContext)
    private val dao = db.translationCacheDao()

    private suspend fun ensurePrefsInitialized() {
        if (!KitsugiTranslatePrefs.isInitialized) {
            runCatching {
                com.kitsugi.animelist.data.settings.SettingsDataStore(dbContext).settingsFlow.first()
            }
        }
    }

    /**
     * Verilen metnin büyük olasılıkla Türkçe olup olmadığını kontrol eder.
     *
     * Yerel sezgi yalnızca gereksiz ağ isteğini önleyen bir **ön kontrol**tür. Kesin karar
     * [translateTo] içinde Google'ın döndürdüğü algılanan kaynak dille verilir; bu yüzden
     * buradaki sezgi yanılsa bile Türkçe metin Türkçeye "çevrilmiş" gibi gösterilmez.
     */
    private fun isLikelyTurkish(text: String): Boolean = isLikelyTurkishText(text)

    /** Metin daha önce "zaten hedef dilde" olarak işaretlendi mi? (ağ isteğini atla) */
    private fun alreadyTargetLanguage(text: String, targetLang: String): Boolean {
        if (text.length > 4000) return false // uzun metinlerde hızlı yol aramaya değmez
        val key = alreadyTargetKey(text, targetLang)
        return alreadyTargetCache[key] == true
    }

    /** [text] için "zaten hedef dilde" bilgisini bellekte tut. */
    private fun rememberAlreadyTarget(text: String, targetLang: String) {
        if (text.length > 4000) return
        runCatching {
            alreadyTargetCache[alreadyTargetKey(text, targetLang)] = true
            pruneAlreadyTargetCache()
        }
    }

    suspend fun translate(text: String?): String = withContext(Dispatchers.IO) {
        if (text.isNullOrBlank()) return@withContext ""
        ensurePrefsInitialized()
        val sourceLang = KitsugiTranslatePrefs.sourceLanguage.ifBlank { "auto" }
        val targetLang = KitsugiTranslatePrefs.resolvedTarget()
        translateTo(text, sourceLang, targetLang)
    }

    suspend fun translateTo(
        text: String?,
        sourceLang: String,
        targetLang: String,
        onPartial: ((String) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        if (text.isNullOrBlank()) return@withContext ""

        val resolvedSource = sourceLang.trim().ifBlank { "auto" }
        val resolvedTarget = targetLang.trim().ifBlank { "tr" }
        val trimmed = text.trim().cleanShikimoriBbCode()
        if (trimmed.isBlank()) return@withContext ""

        // Kaynak dil ile hedef dil aynıysa çeviri anlamsızdır (ör. kaynak ayarı "tr",
        // hedef "tr"): istek atılmaz, metin olduğu gibi döner.
        if (resolvedSource.equals(resolvedTarget, ignoreCase = true)) return@withContext trimmed

        // Teknik metin (yalnızca URL, dosya adı, `File:` ad alanı değeri…) çevrilmez.
        // Google Translate bu değerlere boşluk ekler / yol parçasını çevirir ve bağlantı
        // erişilemez hâle gelir; hiç istek yapmadan orijinali döndürmek en güvenli yol.
        if (isTechnicalTranslationValue(trimmed)) return@withContext trimmed

        // Metin zaten Türkçeyse çevirme — gereksiz API çağrısını önle.
        // (Yerel sezgi yanılsa bile aşağıdaki "algılanan dil == hedef dil" denetimi
        //  aynı metnin çeviri gibi gösterilmesini engeller.)
        if (resolvedTarget.equals("tr", ignoreCase = true) && isLikelyTurkish(trimmed)) {
            return@withContext trimmed
        }
        if (alreadyTargetLanguage(trimmed, resolvedTarget)) return@withContext trimmed

        val hash = md5("${trimmed}_${resolvedSource}_${resolvedTarget}")

        // 1. Check local Room database cache.
        //    Önbellek değeri kaynak metnin birebir aynısıysa bu bayat bir kayıttır
        //    (eski sürümlerde orijinal metin "çeviri" olarak yazılmış olabilir) —
        //    yok sayıp yeniden çevir; aksi hâlde açıklama sonsuza dek orijinal dilde kalır.
        val cached = runCatching { dao.getTranslation(hash) }.getOrNull()
        if (!cached.isNullOrBlank()) {
            val cleanedCached = cached.cleanShikimoriBbCode()
            if (cleanedCached.trim() != trimmed && !translationMatchesSource(trimmed, cleanedCached)) {
                return@withContext cleanedCached
            }
        }

        // 2. If not cached, fetch translation from Google Translate.
        //    Uzun metinler parçalara bölünür; her parça tamamlandıkça [onPartial]
        //    ile çevrilen kısım UI'ya akıtılır (kullanıcı beklemeden okumaya başlar).
        val result = fetchFromGoogle(trimmed, resolvedSource, resolvedTarget, onPartial)
        val translated = result.text.cleanShikimoriBbCode()

        // 3. Kaynak metin zaten hedef dildeyse çeviri sayılmaz.
        //    Bu, "Türkçe metni tekrar Türkçeye çevirme" hatasının ana düzeltmesidir:
        //    Google `src` alanında "tr" döndürürse sonuç kullanıcıya çeviri olarak
        //    gösterilmez (orijinal metin döner) ve önbelleğe yazılmaz.
        if (result.alreadyInTargetLanguage &&
            result.detectedSourceLang.orEmpty().equals(resolvedTarget, ignoreCase = true)
        ) {
            rememberAlreadyTarget(trimmed, resolvedTarget)
            return@withContext trimmed
        }
        if (translationMatchesSource(trimmed, translated)) {
            rememberAlreadyTarget(trimmed, resolvedTarget)
            return@withContext trimmed
        }

        // 4. Cache the success response in DB — YALNIZCA tam başarıda.
        //    Kısmî çeviriyi tam metin anahtarına yazmayız; sonraki açılışta kalan parçalar tamamlanır.
        if (result.fullyTranslated && translated.isNotBlank() && translated != trimmed) {
            runCatching {
                dao.insertTranslation(TranslationCacheEntity(hash, translated))
            }
            return@withContext translated
        }

        return@withContext if (translated.isBlank()) trimmed else translated
    }

    /** DOM translation needs an explicit failure signal, and must not run BBCode cleanup
     * on literal web text. Uses the same Google transport, request gate and Room cache.
     * External translator-app preferences are intents, not background service providers.
     */
    suspend fun translateWebPageText(text: String, sourceLang: String, targetLang: String): String? =
        withContext(Dispatchers.IO) {
            val original = text.trim()
            if (original.isEmpty()) return@withContext original
            val source = sourceLang.trim().ifBlank { "auto" }
            val target = targetLang.trim().ifBlank { "tr" }
            if (source.equals(target, true) || isTechnicalTranslationValue(original)) return@withContext original
            val hash = md5("webdom-v1_${source}_${target}_$original")
            memoryGet(hash)?.takeIf { it.trim() != original && !translationMatchesSource(original, it) }?.let {
                return@withContext it
            }
            val cached = try {
                dao.getTranslation(hash)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            // Bayat kayıt: önbellekteki değer kaynak metnin aynısıysa (eski sürümlerde
            // orijinal metin "çeviri" olarak yazılmış olabilir) yok sayıp yeniden çevir.
            if (!cached.isNullOrBlank() && cached.trim() != original && !translationMatchesSource(original, cached)) {
                memoryPut(hash, cached)
                return@withContext cached
            }
            if (target.equals("tr", true) && isLikelyTurkish(original)) return@withContext original
            if (alreadyTargetLanguage(original, target)) return@withContext original
            val chunks = splitIntoChunks(original, WEB_MAX_CHUNK_CHARS)
            val results = if (chunks.size == 1) {
                listOf(fetchFromGoogle(chunks[0], source, target, null, WEB_MAX_CHUNK_CHARS))
            } else {
                coroutineScope {
                    chunks.map { chunk ->
                        async { fetchFromGoogle(chunk, source, target, null, WEB_MAX_CHUNK_CHARS) }
                    }.awaitAll()
                }
            }
            if (results.any { !it.fullyTranslated || it.text.isBlank() }) return@withContext null
            var detected: String? = null
            for (res in results) {
                detected = detected ?: res.detectedSourceLang
            }
            if (detected.orEmpty().equals(target, ignoreCase = true)) {
                rememberAlreadyTarget(original, target)
                return@withContext original
            }
            val translated = results.joinToString("\n\n") { it.text }
            if (translated.length > minOf(40_000, original.length * 6 + 256)) return@withContext null
            if (translationMatchesSource(original, translated)) {
                rememberAlreadyTarget(original, target)
                return@withContext original
            }
            memoryPut(hash, translated)
            try {
                dao.insertTranslation(TranslationCacheEntity(hash, translated))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Cache failure must not discard a valid translation.
            }
            translated
        }

    suspend fun cachedWebPageText(text: String, sourceLang: String, targetLang: String): String? =
        withContext(Dispatchers.IO) {
            val original = text.trim()
            if (original.isEmpty()) return@withContext original
            val source = sourceLang.trim().ifBlank { "auto" }
            val target = targetLang.trim().ifBlank { "tr" }
            if (source.equals(target, true) || isTechnicalTranslationValue(original)) return@withContext original
            if (target.equals("tr", true) && isLikelyTurkish(original)) return@withContext original
            if (alreadyTargetLanguage(original, target)) return@withContext original
            val hash = md5("webdom-v1_${source}_${target}_$original")
            memoryGet(hash)?.takeIf { it.trim() != original && !translationMatchesSource(original, it) }?.let {
                return@withContext it
            }
            val cached = try {
                dao.getTranslation(hash)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            val validCached = cached?.takeIf { it.isNotBlank() && it.trim() != original && !translationMatchesSource(original, it) }
            if (validCached != null) memoryPut(hash, validCached)
            validCached
        }

    suspend fun storeWebPageTexts(
        pairs: List<Pair<String, String>>,
        sourceLang: String,
        targetLang: String
    ) = withContext(Dispatchers.IO) {
        if (pairs.isEmpty()) return@withContext
        val source = sourceLang.trim().ifBlank { "auto" }
        val target = targetLang.trim().ifBlank { "tr" }
        val entities = ArrayList<TranslationCacheEntity>(pairs.size)
        pairs.forEach { (sourceText, translated) ->
            val orig = sourceText.trim()
            if (orig.isBlank() || translated.isBlank()) return@forEach
            val hash = md5("webdom-v1_${source}_${target}_$orig")
            memoryPut(hash, translated)
            entities.add(TranslationCacheEntity(hash, translated))
        }
        if (entities.isNotEmpty()) {
            try {
                dao.insertTranslations(entities)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Cache failure must not discard translations.
            }
        }
    }

    suspend fun translateTo(text: String?, targetLang: String): String = translateTo(text, "auto", targetLang)

    /**
     * Metni KULLANICININ ayarladığı hedef dile çevirir (Ayarlar → Çeviri → Hedef dil;
     * ayar yoksa uygulama dili).
     */
    suspend fun translateToTarget(text: String?, onPartial: ((String) -> Unit)? = null): String {
        ensurePrefsInitialized()
        val sourceLang = KitsugiTranslatePrefs.sourceLanguage.ifBlank { "auto" }
        val targetLang = KitsugiTranslatePrefs.resolvedTarget()
        return translateTo(text, sourceLang, targetLang, onPartial)
    }

    suspend fun translateToEnglish(text: String?): String = translateTo(text, "en")

    /**
     * Maskelenmiş metin + çeviri sonrası geri kurma ve bütünlük denetimi.
     *
     * [isIntact] çevirinin korunan jetonları **kaybetmediğini** doğrular. Google bir jetonu
     * düşürürse korunan URL/dosya adı metinden tamamen silinirdi; bu durumda çeviri
     * reddedilir ve o parça orijinal hâliyle gösterilir (kopuk bağlantı gösterilmez).
     */
    private class MaskedMarkdown(
        val masked: String,
        val unmask: (String) -> String,
        val isIntact: (String) -> Boolean
    )

    /**
     * Markdown linklerini, medya etiketlerini (img/webm/youtube), spoiler işaretlerini
     * ve URL'leri Google Translate'in bozmasını önlemek için harf içermeyen sembolik
     * yer tutucularla maskeler (böylece Google Translate yer tutucu içindeki kelimeleri çeviremez).
     */
        private fun needsMasking(text: String): Boolean {
        if (text.length < 5) return false
        val hasBracket = text.indexOf('[') >= 0
        val hasBrace = text.indexOf('{') >= 0
        val hasColon = text.indexOf(':') >= 0
        val hasHttp = text.contains("http", ignoreCase = true)
        val hasAt = text.indexOf('@') >= 0
        val hasBang = text.indexOf('!') >= 0
        val hasParenthesis = text.indexOf('(') >= 0
        if (!hasBracket && !hasBrace && !hasColon && !hasHttp && !hasAt && !hasBang && !hasParenthesis) {
            return false
        }
        return true
    }

    private fun maskMarkdown(text: String): MaskedMarkdown {
        if (!needsMasking(text)) return MaskedMarkdown(text, { it }, { true })

        var masked = text

        // 1. AniList / Markdown görsel ve video gömmeleri: img220(https://...), webm(https://...), ![](https://...)
        val mediaEmbedRegex = MEDIA_EMBED_REGEX
        val rawTokens = mutableListOf<String>()
        masked = mediaEmbedRegex.replace(masked) { match ->
            val idx = rawTokens.size
            rawTokens.add(match.value)
            "⟪@$idx⟫"
        }

        // 2. Markdown linkleri: [Etiket](https://...)
        val linkRegex = LINK_REGEX
        val links = mutableListOf<Pair<String, String>>() // (label, url)
        masked = linkRegex.replace(masked) { match ->
            val idx = links.size
            val label = match.groupValues[1].trim()
            val url = match.groupValues[2].trim()
            links.add(Pair(label, url))
            "⟪#$idx⟫$label⟪/$idx⟫"
        }

        // 3. Korunacak bağımsız URL'ler
        val rawUrlRegex = RAW_URL_REGEX
        masked = rawUrlRegex.replace(masked) { match ->
            val idx = rawTokens.size
            rawTokens.add(match.value.trim())
            "⟪@$idx⟫"
        }

        // 3.5 Wiki'ye özgü korunacak jetonlar: [[Sayfa|Etiket]], {{şablon}}, File:/Image:/
        //     Category: ad alanı değerleri ve e-posta adresleri. Bunlar çevrilirse bağlantı
        //     hedefi ya da şablon adı bozulur; kullanıcı yanlış sayfaya düşer.
        val wikiTokenRegex = WIKI_TOKEN_REGEX
        masked = wikiTokenRegex.replace(masked) { match ->
            val idx = rawTokens.size
            rawTokens.add(match.value.trim())
            "⟪@$idx⟫"
        }

        // 4. AniList spoiler sınırları (~!...!~): iç metin çevrilsin ama ~! ve !~ bozulmasın
        val spoilerOpenToken = "⟪!9990⟫"
        val spoilerCloseToken = "⟪!9991⟫"
        val hasAniListSpoilers = masked.contains("~!") && masked.contains("!~")
        if (hasAniListSpoilers) {
            masked = masked
                .replace("~!", spoilerOpenToken)
                .replace("!~", spoilerCloseToken)
        }

        val unmasker: (String) -> String = { translated ->
            var res = translated

            if (hasAniListSpoilers) {
                res = res
                    .replace(Regex("""⟪\s*!\s*9990\s*⟫"""), "~!")
                    .replace(Regex("""⟪\s*!\s*9991\s*⟫"""), "!~")
                    .replace(Regex("""~\s+!"""), "~!")
                    .replace(Regex("""!\s+~"""), "!~")
            }

            // Ham URL ve medya gömmelerini geri yükle
            rawTokens.forEachIndexed { i, token ->
                res = res.replace(Regex("""⟪\s*@\s*${i}\s*⟫"""), Regex.escapeReplacement(token))
            }

            // Markdown linklerini geri yükle: etiket çevrildi, URL korundu
            links.forEachIndexed { i, (origLabel, url) ->
                val pattern = Regex("""⟪\s*#\s*${i}\s*⟫(.*?)⟪\s*/\s*${i}\s*⟫""", RegexOption.DOT_MATCHES_ALL)
                if (pattern.containsMatchIn(res)) {
                    res = pattern.replace(res) { m ->
                        val translatedLabel = m.groupValues[1].trim().ifBlank { origLabel }
                        "[$translatedLabel]($url)"
                    }
                } else {
                    val openRegex = Regex("""⟪\s*#\s*${i}\s*⟫""")
                    val closeRegex = Regex("""⟪\s*/\s*${i}\s*⟫""")
                    if (openRegex.containsMatchIn(res) && closeRegex.containsMatchIn(res)) {
                        res = res.replace(openRegex, "[").replace(closeRegex, "]($url)")
                    } else {
                        res = res.replace(openRegex, "").replace(closeRegex, " ($url)")
                    }
                }
            }

            // Eski önbellekten veya bozulmuş yanıttan kalabilecek artık yer tutucuları temizle
            res = res
                .replace(Regex("""⟪\s*[#@/!]?\s*\d+\s*⟫"""), "")
                .replace(Regex("""⟦\s*L_\d+_(?:START|END|BAŞLANGIÇ|SON)\s*⟧""", RegexOption.IGNORE_CASE), "")
            res
        }

        // Bütünlük denetimi: korunan jetonlardan biri çeviri yanıtında kaybolduysa
        // URL/dosya adı metinden tamamen silinmiş demektir → çeviri reddedilir.
        val isIntact: (String) -> Boolean = { translated ->
            rawTokens.indices.all { index ->
                Regex("⟪\\s*@\\s*$index\\s*⟫").containsMatchIn(translated)
            } && links.indices.all { index ->
                Regex("⟪\\s*#\\s*$index\\s*⟫").containsMatchIn(translated)
            }
        }

        return MaskedMarkdown(masked, unmasker, isIntact)
    }

    /**
     * Uzun metinleri Google Translate istek boyutu sınırını aşmayacak parçalara böler.
     */
    private fun splitIntoChunks(text: String, maxChunkSize: Int = MAX_CHUNK_CHARS): List<String> {
        if (text.length <= maxChunkSize) return listOf(text)
        val chunks = mutableListOf<String>()
        var currentChunk = StringBuilder()

        fun flush() {
            if (currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toString())
                currentChunk = StringBuilder()
            }
        }

        fun addPiece(piece: String, separator: String) {
            if (piece.isEmpty()) return
            if (currentChunk.isNotEmpty() && currentChunk.length + separator.length + piece.length > maxChunkSize) {
                flush()
            }
            if (currentChunk.isNotEmpty()) currentChunk.append(separator)
            currentChunk.append(piece)
        }

        for (paragraph in text.split("\n\n")) {
            if (paragraph.length > maxChunkSize) {
                for (line in paragraph.split("\n")) {
                    if (line.length > maxChunkSize) {
                        for (sentence in splitOversizedLine(line, maxChunkSize)) {
                            addPiece(sentence, " ")
                        }
                    } else {
                        addPiece(line, "\n")
                    }
                }
            } else {
                addPiece(paragraph, "\n\n")
            }
        }
        flush()

        return if (chunks.isEmpty()) listOf(text) else chunks
    }

    /**
     * Sınırı aşan tek satırı cümle sonlarından böler.
     * CJK noktalamaları (。！？；…) sonrasında boşluk olmasa bile cümle sonu sayılır;
     * Latin noktalamalarında (.!?) ise "3.14" veya "Mr." bölünmesin diye boşluk/parantez/tırnak aranır.
     */
    private fun splitOversizedLine(line: String, maxChunkSize: Int): List<String> {
        val cjkEnders = "…۔۔。！？；"
        val asciiEnders = ".!?"

        val sentences = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < line.length) {
            val c = line[i]
            sb.append(c)
            val isCjkEnder = cjkEnders.indexOf(c) >= 0
            val isAsciiEnder = asciiEnders.indexOf(c) >= 0
            if (isCjkEnder || isAsciiEnder) {
                val next = line.getOrNull(i + 1)
                val isBoundary = if (isCjkEnder) {
                    true
                } else {
                    next == null || next.isWhitespace() || next == '"' || next == '\'' || next == ')' || next == ']' || next == '”'
                }
                if (isBoundary) {
                    val s = sb.toString().trim()
                    if (s.isNotEmpty()) sentences.add(s)
                    sb.setLength(0)
                }
            }
            i++
        }
        if (sb.isNotBlank()) sentences.add(sb.toString().trim())
        sentences.removeAll { it.isBlank() }
        if (sentences.isEmpty()) return listOf(line)

        val pieces = mutableListOf<String>()
        for (sentence in sentences) {
            if (sentence.length <= maxChunkSize) {
                pieces.add(sentence)
                continue
            }
            var wordChunk = StringBuilder()
            for (word in sentence.split(" ")) {
                if (wordChunk.isNotEmpty() && wordChunk.length + 1 + word.length > maxChunkSize) {
                    pieces.add(wordChunk.toString())
                    wordChunk = StringBuilder()
                }
                if (wordChunk.isNotEmpty()) wordChunk.append(' ')
                wordChunk.append(word)
                while (wordChunk.length > maxChunkSize) {
                    pieces.add(wordChunk.substring(0, maxChunkSize))
                    wordChunk.delete(0, maxChunkSize)
                }
            }
            if (wordChunk.isNotEmpty()) pieces.add(wordChunk.toString())
        }
        return pieces
    }

    private suspend fun Call.executeCancellable(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation {
            runCatching { cancel() }
        }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) {
                    cont.resume(response)
                } else {
                    response.close()
                }
            }
        })
    }

    /**
     * Google Translate yanıtı: çevrilmiş metin + **algılanan kaynak dil**.
     *
     * Algılanan dil, "zaten hedef dilde olan metni tekrar çevirme" hatasının kesin
     * çözümüdür: Google `sl=auto` ile kendi dil tespitini yapar ve `src` alanında
     * döndürür. Kaynak dil == hedef dil ise sonuç çeviri olarak kullanılmaz.
     */
    private class ParsedTranslation(val text: String, val detectedSourceLang: String?)

    /**
     * Google Translate'in farklı uç noktalarından dönen JSON yanıtlarını (hem `dj=1`
     * nesne biçimi hem klasik iç içe dizi biçimi) ayrıştırır.
     */
    private fun parseTranslationResponse(responseText: String): ParsedTranslation? {
        val trimmed = responseText.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("<")) return null

        if (trimmed.startsWith("{")) {
            val obj = JSONObject(trimmed)
            val sentences = obj.optJSONArray("sentences")
            if (sentences != null && sentences.length() > 0) {
                val sb = StringBuilder()
                for (i in 0 until sentences.length()) {
                    val item = sentences.optJSONObject(i) ?: continue
                    val trans = item.optString("trans", "")
                    if (trans.isNotEmpty()) sb.append(trans)
                }
                if (sb.isNotEmpty()) {
                    val src = obj.optString("src", "").trim().ifBlank { null }
                    return ParsedTranslation(sb.toString(), src)
                }
            }
        }

        if (trimmed.startsWith("[")) {
            val jsonArray = JSONArray(trimmed)
            val firstElem = jsonArray.opt(0)
            // Klasik biçimde 3. eleman algılanan dildir: [[...], null, "en", ...]
            val detected = jsonArray.optString(2, "").trim().ifBlank { null }
            if (firstElem is JSONArray) {
                val sb = StringBuilder()
                for (i in 0 until firstElem.length()) {
                    val item = firstElem.opt(i)
                    when (item) {
                        is JSONArray -> {
                            val part = item.optString(0, "")
                            if (part.isNotEmpty()) sb.append(part)
                        }
                        is String -> {
                            if (i == 0 && item.isNotEmpty()) sb.append(item)
                        }
                    }
                }
                if (sb.isNotEmpty()) return ParsedTranslation(sb.toString(), detected)
            } else if (firstElem is String && firstElem.isNotEmpty()) {
                return ParsedTranslation(firstElem, detected)
            }
        }

        return null
    }

    private fun buildEndpointRequest(
        endpointIndex: Int,
        text: String,
        sourceLang: String,
        targetLang: String
    ): Request {
        val formBody = FormBody.Builder()
            .add("q", text)
            .build()

        return when (endpointIndex % 4) {
            0 -> Request.Builder()
                .url("https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sourceLang&tl=$targetLang&dt=t&dj=1")
                .post(formBody)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .build()
            1 -> Request.Builder()
                .url("https://translate.google.com/translate_a/single?client=at&sl=$sourceLang&tl=$targetLang&dt=t&dj=1")
                .post(formBody)
                .header("User-Agent", "GoogleTranslate/6.29.0.05.421483610 (Linux; U; Android 13; Pixel 7)")
                .build()
            2 -> Request.Builder()
                .url("https://translate.googleapis.com/translate_a/single?client=dict-chrome-ex&sl=$sourceLang&tl=$targetLang&dt=t&dj=1")
                .post(formBody)
                .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .build()
            else -> {
                val encoded = URLEncoder.encode(text, "UTF-8")
                if (encoded.length <= 3600) {
                    Request.Builder()
                        .url("https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sourceLang&tl=$targetLang&dt=t&q=$encoded")
                        .get()
                        .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15")
                        .build()
                } else {
                    Request.Builder()
                        .url("https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=$sourceLang&tl=$targetLang&dt=t")
                        .post(formBody)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                        .build()
                }
            }
        }
    }

    /**
     * Tek parça çevirisi ister. Birincil uç nokta 429/403/5xx döndürürse beklemeden
     * yedek Google Translate uç noktalarına geçer.
     */
    private suspend fun fetchSingleChunk(
        text: String,
        sourceLang: String,
        targetLang: String,
        startEndpointIndex: Int = 0
    ): ParsedTranslation? {
        for (offset in 0 until 4) {
            val endpointIdx = (startEndpointIndex + offset) % 4
            val request = buildEndpointRequest(endpointIdx, text, sourceLang, targetLang)
            try {
                val parsed = translationHttpClient.newCall(request).executeCancellable().use { response ->
                    if (!response.isSuccessful) {
                        android.util.Log.w(
                            "TranslationManager",
                            "Çeviri uç noktası #$endpointIdx başarısız: HTTP ${response.code} (uzunluk=${text.length})"
                        )
                        null
                    } else {
                        val body = response.body?.string()
                        if (body.isNullOrBlank()) null else parseTranslationResponse(body)
                    }
                }
                if (parsed != null && parsed.text.isNotBlank()) {
                    return parsed
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                android.util.Log.w("TranslationManager", "Çeviri uç noktası #$endpointIdx istisna: ${e.message}")
            }
        }
        return null
    }

    /**
     * Tek parça için yeniden deneme + parça bazlı Room önbelleği.
     * Başarılı olan her parça kendi özet anahtarıyla da önbelleğe yazılır; böylece
     * çok parçalı uzun biyografilerde/incelemelerde yarıda kesilme olsa bile
     * çevrilmiş parçalar kaybolmaz.
     */
    private suspend fun fetchSingleChunkWithRetry(
        text: String,
        sourceLang: String,
        targetLang: String,
        maxAttempts: Int = 3
    ): ParsedTranslation? {
        val chunkHash = md5("chunk_${text}_${sourceLang}_${targetLang}")
        val cachedChunk = runCatching { dao.getTranslation(chunkHash) }.getOrNull()
        if (!cachedChunk.isNullOrBlank()) {
            // Önbellekte algılanan dil yok; dil denetimi bir üst katmanda metin
            // karşılaştırmasıyla ([translationMatchesSource]) yapılır.
            return ParsedTranslation(cachedChunk, null)
        }

        repeat(maxAttempts) { attempt ->
            val result = requestGate.withPermit {
                fetchSingleChunk(text, sourceLang, targetLang, startEndpointIndex = attempt)
            }
            if (result != null && result.text.isNotBlank()) {
                if (result.text != text) {
                    runCatching { dao.insertTranslation(TranslationCacheEntity(chunkHash, result.text)) }
                }
                return result
            }
            if (attempt < maxAttempts - 1) {
                delay(600L * (attempt + 1))
            }
        }
        android.util.Log.e("TranslationManager", "Çeviri parçası $maxAttempts denemede çevrilemedi (uzunluk=${text.length})")
        return null
    }

    /**
     * Google çevirisi sonucu: birleşik metin + tüm parçaların başarıp başarılmadığı +
     * Google'ın algıladığı kaynak dil.
     *
     * [detectedSourceLang] hedef dile eşitse kaynak metin zaten hedef dildeydi; [text]
     * bu durumda orijinal metni taşır ve [alreadyInTargetLanguage] true olur.
     */
    private class GoogleTranslationResult(
        val text: String,
        val fullyTranslated: Boolean,
        val detectedSourceLang: String? = null,
        val alreadyInTargetLanguage: Boolean = false
    )

    private suspend fun fetchFromGoogle(
        text: String,
        sourceLang: String,
        targetLang: String,
        onPartial: ((String) -> Unit)? = null,
        chunkChars: Int = MAX_CHUNK_CHARS
    ): GoogleTranslationResult {
        return try {
            val mask = maskMarkdown(text)
            val chunks = splitIntoChunks(mask.masked, maxChunkSize = chunkChars)
            val translatedChunks = mutableListOf<String>()
            var allSucceeded = true
            var detectedLang: String? = null

            var lastPartialEmitMs = 0L

            for ((index, chunk) in chunks.withIndex()) {
                val raw = fetchSingleChunkWithRetry(chunk, sourceLang, targetLang)
                val chunkLang = raw?.detectedSourceLang
                if (!chunkLang.isNullOrBlank()) {
                    detectedLang = when {
                        detectedLang == null -> chunkLang
                        detectedLang?.equals(chunkLang, ignoreCase = true) == true -> detectedLang
                        // Parçalar farklı dillerde: tek bir kaynak dilden söz edilemez.
                        else -> ""
                    }
                }
                // Korumalı jeton (URL/dosya adı/bağlantı) kaybı varsa çeviri reddedilir:
                // bozuk bağlantı göstermektense kaynak metin korunur.
                val translated = if (raw == null || !mask.isIntact(raw.text)) {
                    if (raw != null) {
                        android.util.Log.w(
                            "TranslationManager",
                            "Çeviri korunan bağlantı jetonunu kaybetti; parça orijinal bırakıldı (uzunluk=${chunk.length})"
                        )
                    }
                    allSucceeded = false
                    chunk
                } else {
                    raw.text
                }
                translatedChunks.add(translated)
                if (onPartial != null && index < chunks.lastIndex) {
                    val nowMs = android.os.SystemClock.uptimeMillis()
                    if (nowMs - lastPartialEmitMs >= PARTIAL_EMIT_MIN_INTERVAL_MS) {
                        lastPartialEmitMs = nowMs
                        val remaining = chunks.subList(index + 1, chunks.size).joinToString("\n\n")
                        runCatching {
                            onPartial(mask.unmask(translatedChunks.joinToString("\n\n") + "\n\n" + remaining))
                        }
                    }
                }
            }

            val alreadyInTarget = detectedLang?.equals(targetLang.trim(), ignoreCase = true) == true
            // Kaynak metin zaten hedef dildeyse çeviri metni değil **orijinal** metni döndür.
            val finalText = if (alreadyInTarget) text
            else mask.unmask(translatedChunks.joinToString("\n\n"))

            GoogleTranslationResult(
                text = finalText,
                fullyTranslated = allSucceeded,
                detectedSourceLang = detectedLang?.takeIf { it.isNotBlank() },
                alreadyInTargetLanguage = alreadyInTarget
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            android.util.Log.e("TranslationManager", "fetchFromGoogle failed: ${e.message}", e)
            GoogleTranslationResult(text, false)
        }
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
