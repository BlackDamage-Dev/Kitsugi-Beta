package com.kitsugi.animelist.utils

import com.kitsugi.animelist.data.local.TitleScriptPolicy
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.data.remote.BangumiIdNamespace
import com.kitsugi.animelist.data.remote.BangumiTitleCache
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.data.remote.KitsugiRelation
import com.kitsugi.animelist.data.remote.KitsugiCharacterMediaAppearance
import com.kitsugi.animelist.data.remote.KitsugiStaffMediaWork
import com.kitsugi.animelist.data.remote.KitsugiStaffCharacterRole
import com.kitsugi.animelist.KitsugiApplication
import com.kitsugi.animelist.R

object PreferenceHelpers {
    fun hasCjkCharacters(text: String?): Boolean = TitleScriptPolicy.hasCjkCharacters(text)

    fun isLatinText(text: String?): Boolean = TitleScriptPolicy.isLatinText(text)

    /**
     * Başlık dili seçimini tüm kaynaklar için tek yerde uygular.
     *
     * `titleRomaji`, Bangumi gibi English ve Latin özgün başlığın farklı gelebildiği
     * kaynaklarda zorunludur. Alan mevcut değilse eski model davranışı korunur.
     */
    fun getDisplayTitle(
        title: String,
        titleEnglish: String?,
        titleJapanese: String?,
        titleLanguage: String,
        titleRomaji: String? = null
    ): String {
        fun nonBlank(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
        val english = nonBlank(titleEnglish)
        val native = nonBlank(titleJapanese)
        val romaji = nonBlank(titleRomaji)
        val raw = nonBlank(title)
        return when (titleLanguage) {
            "ENGLISH" -> english ?: romaji ?: raw ?: native ?: ""
            "NATIVE", "JAPANESE_STAFF" -> native ?: raw ?: romaji ?: english ?: ""
            else -> {
                // ROMAJI: Latin özgün ad varsa onu kullan. Kaynak romaji sağlamıyorsa
                // İngilizce Latin ad güvenli geri dönüş olur; uydurma transliterasyon yapılmaz.
                romaji
                    ?: raw?.takeIf { !hasCjkCharacters(it) }
                    ?: english?.takeIf { !hasCjkCharacters(it) }
                    ?: raw
                    ?: native
                    ?: ""
            }
        }
    }

    fun MediaEntry.getDisplayTitle(titleLanguage: String): String {
        if (MediaTitleResolver.isLatinPreferredSource(source)) {
            return when (titleLanguage) {
                "NATIVE", "JAPANESE_STAFF" -> titleJapanese?.takeIf { it.isNotBlank() } ?: title
                "ENGLISH" -> MediaTitleResolver.latin(titleEnglish) ?: MediaTitleResolver.latin(title) ?: title
                else -> MediaTitleResolver.latin(title)
                    ?: MediaTitleResolver.latin(titleEnglish)
                    ?: title
            }
        }
        val resolved = PreferenceHelpers.getDisplayTitle(title, titleEnglish, titleJapanese, titleLanguage)
        return bangumiCachedTitleOrNull(titleLanguage, resolved) ?: resolved
    }

    /**
     * Bangumi kaynaklı kayıtlar için önbellekten dile uygun ad.
     *
     * Bangumi'nin koleksiyon/liste uçları YALNIZCA özgün adı (`name`, çoğu zaman Japonca)
     * döndürür: içe aktarılan satırlarda `titleEnglish` boş kalır. Bu yüzden "Listem" ve
     * benzeri listeler, kullanıcı Romaji/İngilizce başlık dilini seçse bile CJK adla
     * çiziliyordu. Latin adlar (romaji/İngilizce) detay sayfası, keşfet veya
     * [com.kitsugi.animelist.data.remote.BangumiLibraryTitleHydrator] tarafından öğrenilir
     * ve [BangumiTitleCache]'e yazılır; burada yalnızca bellek içi önbellek okunur —
     * ekran bekletilmez, ağ isteği yapılmaz.
     *
     * @param resolved mevcut alanlardan çözülen ad. Seçili dili DB alanları zaten
     *        karşılıyorsa önbelleğe hiç bakılmaz (önbellek yalnızca eksiği tamamlar):
     *        `ENGLISH` için karşılama ölçütü, gösterimin gerçek (Latin) `titleEnglish`
     *        alanından gelmesidir — romaji yedeği gösteriliyorsa önbellekteki ayrı
     *        İngilizce ad kazanır. Diğer Latin dillerde (ROMAJI) Latin gösterim yeterlidir.
     */
    private fun MediaEntry.bangumiCachedTitleOrNull(titleLanguage: String, resolved: String): String? {
        val wantsLatin = !titleLanguage.equals("NATIVE", ignoreCase = true) &&
            !titleLanguage.equals("JAPANESE_STAFF", ignoreCase = true)
        if (!wantsLatin) return null
        val satisfiedByFields = if (titleLanguage.equals("ENGLISH", ignoreCase = true)) {
            !titleEnglish.isNullOrBlank() && isLatinText(titleEnglish)
        } else {
            resolved.isNotBlank() && !hasCjkCharacters(resolved)
        }
        if (satisfiedByFields) return null
        if (!BangumiIdNamespace.SOURCE.equals(source.trim(), ignoreCase = true)) return null
        val rawId = BangumiIdNamespace.rawIdOrNull(malId) ?: return null
        val cached = BangumiTitleCache.localizedTitleFor(rawId, title, titleLanguage) ?: return null
        // Önbellek adayı hâlihazırda gösterilen adın aynısıysa (örn. romaji) dokunma.
        return cached.takeIf { !it.equals(resolved, ignoreCase = true) }
    }

    fun JikanSearchResult.getDisplayTitle(titleLanguage: String): String {
        if (MediaTitleResolver.isLatinPreferredSource(source)) {
            return when (titleLanguage) {
                "NATIVE", "JAPANESE_STAFF" -> titleJapanese?.takeIf { it.isNotBlank() } ?: title
                "ENGLISH" -> MediaTitleResolver.latin(titleEnglish) ?: MediaTitleResolver.latin(titleRomaji) ?: MediaTitleResolver.latin(title) ?: title
                else -> MediaTitleResolver.latin(titleRomaji)
                    ?: MediaTitleResolver.latin(title)
                    ?: MediaTitleResolver.latin(titleEnglish)
                    ?: MediaTitleResolver.latin(titleJapanese)
                    ?: title
            }
        }
        return PreferenceHelpers.getDisplayTitle(title, titleEnglish, titleJapanese, titleLanguage, titleRomaji)
    }

    fun KitsugiRelation.getDisplayTitle(titleLanguage: String): String {
        if (MediaTitleResolver.isLatinPreferredSource(source)) {
            return when (titleLanguage) {
                "NATIVE", "JAPANESE_STAFF" -> titleJapanese?.takeIf { it.isNotBlank() } ?: title
                "ENGLISH" -> MediaTitleResolver.latin(titleEnglish) ?: MediaTitleResolver.latin(titleRomaji) ?: MediaTitleResolver.latin(title) ?: title
                else -> MediaTitleResolver.latin(titleRomaji)
                    ?: MediaTitleResolver.latin(title)
                    ?: MediaTitleResolver.latin(titleEnglish)
                    ?: title
            }
        }
        return PreferenceHelpers.getDisplayTitle(title, titleEnglish, titleJapanese, titleLanguage, titleRomaji)
    }

    fun KitsugiCharacterMediaAppearance.getDisplayTitle(titleLanguage: String): String {
        return PreferenceHelpers.getDisplayTitle(title, titleEnglish, titleJapanese, titleLanguage, titleRomaji)
    }

    fun KitsugiStaffMediaWork.getDisplayTitle(titleLanguage: String): String {
        return PreferenceHelpers.getDisplayTitle(mediaTitle, titleEnglish, titleJapanese, titleLanguage, titleRomaji)
    }

    fun KitsugiStaffCharacterRole.getDisplayMediaTitle(titleLanguage: String): String {
        return PreferenceHelpers.getDisplayTitle(
            mediaTitle,
            mediaTitleEnglish,
            mediaTitleJapanese,
            titleLanguage,
            mediaTitleRomaji
        )
    }

    fun formatScore(score: Int?, scoreFormat: String, hideScores: Boolean): String {
        val context = KitsugiApplication.getInstance()
        if (hideScores) {
            return context?.getString(R.string.label_unknown) ?: "-"
        }
        if (score == null || score == 0) {
            return context?.getString(R.string.score_unrated) ?: "unrated"
        }
        return when (scoreFormat) {
            "POINT_100" -> "${score * 10}"
            "POINT_10_DECIMAL" -> "${score}.0/10"
            "POINT_5" -> {
                val fullStars = score / 2
                val hasHalf = (score % 2) != 0
                val emptyStars = 5 - fullStars - (if (hasHalf) 1 else 0)
                buildString {
                    repeat(fullStars) { append("★") }
                    if (hasHalf) append("½")
                    repeat(emptyStars) { append("☆") }
                }
            }
            "POINT_3" -> {
                when {
                    score >= 8 -> "😊"
                    score >= 5 -> "😐"
                    else -> "🙁"
                }
            }
            "STARS" -> {
                buildString {
                    repeat(score) { append("★") }
                    repeat(10 - score) { append("☆") }
                }
            }
            else -> "$score/10"
        }
    }

    fun MediaEntry.getDisplayScore(scoreFormat: String, hideScores: Boolean): String {
        return formatScore(score, scoreFormat, hideScores)
    }

    fun JikanSearchResult.getDisplayScore(scoreFormat: String, hideScores: Boolean): String {
        return formatScore(score, scoreFormat, hideScores)
    }
}
