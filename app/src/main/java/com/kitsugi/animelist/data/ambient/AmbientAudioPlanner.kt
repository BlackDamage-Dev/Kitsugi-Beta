package com.kitsugi.animelist.data.ambient

import java.text.Normalizer
import java.util.Locale

/**
 * Builds a title-first reader search, then conservative style/context searches from manga metadata.
 * Only the manga title and bounded mood terms are sent to selected audio catalogs; synopsis, chapter
 * text, page content, and account metadata are never sent.
 */
internal object AmbientAudioPlanner {
    private data class MoodSignal(
        val key: String,
        val label: String,
        val aliases: Set<String>,
        val queryTerms: List<String>,
        val reason: String,
    )

    private val signals = listOf(
        MoodSignal(
            key = "action",
            label = "Aksiyon",
            aliases = setOf("action", "aksiyon", "battle", "fight", "combat", "war", "savas", "dovus", "kavga", "kilic", "samurai", "戦闘", "戦争", "剣"),
            queryTerms = listOf("epic", "action", "cinematic", "orchestral"),
            reason = "yüksek tempolu aksiyon"
        ),
        MoodSignal(
            key = "fantasy",
            label = "Fantastik",
            aliases = setOf("fantasy", "fantastik", "magic", "magical", "wizard", "dragon", "myth", "mythology", "fantasia", "ファンタジー", "魔法"),
            queryTerms = listOf("fantasy", "cinematic", "orchestral"),
            reason = "fantastik atmosfer"
        ),
        MoodSignal(
            key = "adventure",
            label = "Macera",
            aliases = setOf("adventure", "macera", "quest", "journey", "exploration", "travel", "冒険"),
            queryTerms = listOf("adventure", "cinematic", "instrumental"),
            reason = "keşif ve macera hissi"
        ),
        MoodSignal(
            key = "romance",
            label = "Romantizm",
            aliases = setOf("romance", "romantic", "romantizm", "romantik", "love", "aşk", "ask", "couple", "恋愛", "ロマンス"),
            queryTerms = listOf("romantic", "soft piano", "emotional"),
            reason = "yumuşak romantik ton"
        ),
        MoodSignal(
            key = "drama",
            label = "Dram",
            aliases = setOf("drama", "dram", "emotional", "tragedy", "tragedya", "melancholy", "melankoli", "悲劇", "ドラマ"),
            queryTerms = listOf("emotional", "piano", "cinematic"),
            reason = "duygusal ve sakin anlatım"
        ),
        MoodSignal(
            key = "horror",
            label = "Korku",
            aliases = setOf("horror", "korku", "terror", "terror", "ghost", "hayalet", "vampire", "zombie", "paranormal", "ホラー", "怪談"),
            queryTerms = listOf("dark", "suspense", "ambient", "drone"),
            reason = "karanlık gerilim atmosferi"
        ),
        MoodSignal(
            key = "mystery",
            label = "Gizem",
            aliases = setOf("mystery", "gizem", "detective", "dedektif", "crime", "suc", "suç", "investigation", "investigative", "thriller", "gerilim", "ミステリー", "探偵"),
            queryTerms = listOf("mysterious", "suspense", "piano", "ambient"),
            reason = "gizem ve merak duygusu"
        ),
        MoodSignal(
            key = "sci-fi",
            label = "Bilim kurgu",
            aliases = setOf("sci-fi", "scifi", "science fiction", "bilim kurgu", "cyberpunk", "space", "uzay", "robot", "mecha", "alien", "futuristic", "sf", "SF", "サイエンスフィクション", "ロボット"),
            queryTerms = listOf("sci-fi", "synth", "space", "ambient"),
            reason = "bilim kurgu ve elektronik doku"
        ),
        MoodSignal(
            key = "slice-of-life",
            label = "Günlük yaşam",
            aliases = setOf("slice of life", "günlük yaşam", "gunluk yasam", "iyashikei", "cozy", "healing", "school", "okul", "日常", "学園"),
            queryTerms = listOf("cozy", "lo-fi", "soft piano", "relaxing"),
            reason = "rahat ve gündelik atmosfer"
        ),
        MoodSignal(
            key = "comedy",
            label = "Komedi",
            aliases = setOf("comedy", "komedi", "funny", "humor", "humour", "parody", "parodi", "gag", "コメディ", "ギャグ"),
            queryTerms = listOf("light", "playful", "quirky", "instrumental"),
            reason = "hafif ve neşeli ton"
        ),
        MoodSignal(
            key = "sports",
            label = "Spor",
            aliases = setOf("sports", "sport", "spor", "competition", "rekabet", "basketball", "football", "soccer", "競技"),
            queryTerms = listOf("energetic", "motivational", "sports", "instrumental"),
            reason = "enerjik ve motive edici ritim"
        ),
        MoodSignal(
            key = "historical",
            label = "Tarihi",
            aliases = setOf("historical", "history", "tarihi", "tarih", "period drama", "samurai", "時代劇", "歴史"),
            queryTerms = listOf("traditional", "orchestral", "cinematic", "instrumental"),
            reason = "tarihi ve geleneksel tını"
        ),
    )

    private val vocalOrSpeechTerms = setOf(
        "vocal", "vocals", "lyrics", "lyric", "singing", "sung", "voice", "voices",
        "speech", "spoken", "dialogue", "narration", "podcast", "audiobook", "interview",
        "sound effect", "sound effects", "sfx", "foley", "notification", "alarm"
    )

    fun createPlan(context: AmbientAudioContext, mode: AmbientAudioMode = AmbientAudioMode.AUTO): AmbientAudioPlan {
        val genreText = normalize(context.genres.joinToString(" "))
        val storyText = normalize(listOf(context.description, context.chapterTitle).joinToString(" ").take(5_000))

        val scoredSignals = signals.mapNotNull { signal ->
            val genreHits = signal.aliases.count { containsPhrase(genreText, normalize(it)) }
            val storyHits = signal.aliases.count { containsPhrase(storyText, normalize(it)) }
            val score = genreHits * 4 + storyHits
            signal.takeIf { score > 0 }?.let { it to score }
        }.sortedWith(compareByDescending<Pair<MoodSignal, Int>> { it.second }.thenBy { signals.indexOf(it.first) })

        val selected = scoredSignals.take(2).map { it.first }
        val terms = when {
            selected.isEmpty() -> when (mode) {
                AmbientAudioMode.AMBIENCE -> listOf("calm", "ambient", "soundscape")
                else -> listOf("cinematic", "instrumental", "ambient")
            }
            selected.size == 1 -> selected.first().queryTerms
            else -> {
                val combined = selected.flatMap { it.queryTerms }.distinct()
                // Keep the search concise enough to avoid over-constraining catalog results.
                combined.take(5)
            }
        }

        val query = when (mode) {
            AmbientAudioMode.AMBIENCE -> (listOf("ambient", "soundscape") + terms)
                .distinct().take(6).joinToString(" ") + " instrumental background"
            AmbientAudioMode.MUSIC -> (terms + listOf("instrumental", "background", "music"))
                .distinct().take(7).joinToString(" ")
            AmbientAudioMode.AUTO -> (terms + listOf("instrumental", "background", "music"))
                .distinct().take(7).joinToString(" ")
        }.take(120)

        val labels = selected.map { it.label }
        val reason = when {
            labels.isEmpty() -> "Tür/özet ipucu bulunamadı; genel, sözsüz bir okuma müziği aranıyor."
            else -> "${labels.joinToString(" + ")} tür ve özet ipuçlarından ${selected.joinToString(" ve ") { it.reason }} seçildi."
        }

        val titleQuery = context.title.cappedAmbientAudioValue().trim()

        return AmbientAudioPlan(
            query = query,
            moodLabel = labels.ifEmpty { listOf("Genel") }.joinToString(" · "),
            reason = reason,
            matchTerms = terms.map(::normalize).toSet(),
            titleQuery = titleQuery,
            titleMatchTerms = normalize(titleQuery).split(' ').filter(String::isNotBlank).toSet(),
        )
    }

    fun filterAndRank(
        tracks: List<AmbientAudioTrack>,
        plan: AmbientAudioPlan,
        recentlyPlayedIds: Set<String> = emptySet(),
    ): List<AmbientAudioTrack> {
        val sortedCandidates = tracks
            .asSequence()
            .filter(::isSafeCandidate)
            .map { it to score(it, plan, recentlyPlayedIds) }
            .sortedWith(compareByDescending<Pair<AmbientAudioTrack, Int>> { it.second }.thenBy { it.first.id })
            .map { it.first }
            .toList()
        val seenIdentities = HashSet<String>()
        // Suppress a duplicate if either its provider-qualified ID or normalized recording
        // identity has already appeared; one matching identity is enough. Keep the best-ranked copy.
        val candidates = sortedCandidates.filter { track ->
            val keys = track.historyKeys()
            if (keys.any(seenIdentities::contains)) false
            else {
                seenIdentities.addAll(keys)
                true
            }
        }

        return candidates
    }

    /** Rank the combined title/context candidates with title relevance and recent-play suppression intact. */
    fun orderForAutoplay(
        titleMatches: List<AmbientAudioTrack>,
        contextTracks: List<AmbientAudioTrack>,
        plan: AmbientAudioPlan,
        recentlyPlayedIds: Set<String> = emptySet(),
    ): List<AmbientAudioTrack> = filterAndRank(
        titleMatches + contextTracks,
        plan,
        recentlyPlayedIds,
    )

    fun shouldSearchMoodContext(titleMatchCount: Int): Boolean =
        titleMatchCount < MIN_TITLE_MATCHES_FOR_CONTEXT_FALLBACK

    fun isTitleMatch(track: AmbientAudioTrack, plan: AmbientAudioPlan): Boolean {
        val title = normalize(plan.titleQuery)
        if (title.isBlank()) return false
        val haystack = normalize(listOf(track.title, track.tags.joinToString(" ")).joinToString(" "))
        if (containsPhrase(haystack, title)) return true

        val terms = plan.titleMatchTerms.map(::normalize).filter(String::isNotBlank).toSet()
        if (terms.isEmpty()) return false
        val matched = terms.count { containsPhrase(haystack, it) }
        val minimumMatches = if (terms.size <= 2) terms.size else (terms.size * 0.6).toInt().coerceAtLeast(2)
        return matched >= minimumMatches
    }

    fun isSafeCandidate(track: AmbientAudioTrack): Boolean {
        if (!track.streamUrl.startsWith("https://", ignoreCase = true)) return false
        if (track.id.isBlank() || track.title.isBlank()) return false

        val mediaType = AmbientAudioFormats.canonicalType(
            track.fileType.ifBlank { track.streamUrl.substringBefore('?').substringAfterLast('.', "") }
        )
        if (mediaType !in AmbientAudioFormats.supportedPlaybackTypes) return false
        if (track.durationMs != null && track.durationMs < MINIMUM_BACKGROUND_LENGTH_MS) return false
        if (track.fileSizeBytes != null && track.fileSizeBytes > MAX_STREAM_FILE_BYTES) return false

        val contentText = normalize(listOf(track.title, track.tags.joinToString(" ")).joinToString(" "))
        if (vocalOrSpeechTerms.any { containsPhrase(contentText, normalize(it)) }) return false
        if (track.source.contains("jamendo", ignoreCase = true) || track.provider.contains("jamendo", ignoreCase = true)) return false
        return true
    }

    private fun score(
        track: AmbientAudioTrack,
        plan: AmbientAudioPlan,
        recentlyPlayedIds: Set<String>,
    ): Int {
        val text = normalize(listOf(track.title, track.tags.joinToString(" "), track.creator).joinToString(" "))
        var score = 0
        val normalizedTitle = normalize(plan.titleQuery)
        if (normalizedTitle.isNotBlank() && containsPhrase(text, normalizedTitle)) {
            score += 1_000
        } else if (plan.titleMatchTerms.isNotEmpty()) {
            score += plan.titleMatchTerms.count { term -> containsPhrase(text, normalize(term)) } * 120
        }

        plan.matchTerms.forEach { term ->
            if (term.isNotBlank() && containsPhrase(text, term)) score += if (' ' in term) 5 else 8
        }
        if (containsPhrase(text, "instrumental")) score += 12
        if (containsPhrase(text, "ambient")) score += 7
        if (containsPhrase(text, "background music")) score += 4
        if (containsPhrase(text, "soundtrack")) score += 3
        if (containsPhrase(text, "lo-fi") || containsPhrase(text, "lo fi")) score += 4
        if (containsPhrase(text, "no vocals")) score += 5
        if (track.provider.equals("radio_browser", ignoreCase = true)) score += 2

        val length = track.durationMs
        score += when {
            length == null -> -5
            length >= 180_000L -> 8
            length >= 90_000L -> 5
            else -> 0
        }
        if (track.historyKeys().any(recentlyPlayedIds::contains)) score -= 10_000
        return score
    }

    private fun normalize(value: String): String {
        val folded = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFKD)
            .replace("ı", "i")
            .replace("ß", "ss")
            .filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
        return folded.replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().replace(Regex("\\s+"), " ")
    }

    private fun containsPhrase(haystack: String, phrase: String): Boolean {
        if (haystack.isBlank() || phrase.isBlank()) return false
        return " $haystack ".contains(" $phrase ") || haystack.contains(phrase)
    }

    private const val MIN_TITLE_MATCHES_FOR_CONTEXT_FALLBACK = 5
    private const val MINIMUM_BACKGROUND_LENGTH_MS = 45_000L
    private const val MAX_STREAM_FILE_BYTES = 150L * 1024L * 1024L
}
