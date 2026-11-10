package com.kitsugi.animelist.ui.screens.detail

import com.kitsugi.animelist.data.local.TranslationOutcome
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class SynopsisTranslationSettings(
    val enabled: Boolean,
    val sourceLanguage: String,
    val targetLanguage: String
)

internal data class SynopsisTranslationEntry(
    val key: String,
    val source: String,
    val stableId: Int,
    val mediaType: String
)

internal data class SynopsisTranslationState(
    val original: String? = null,
    val translated: String? = null,
    val translating: Boolean = false,
    val showingOriginal: Boolean = false,
    val complete: Boolean = false,
    val error: Boolean = false
)

/** Prefer a loaded detail, then the latest accepted synopsis, over a late fallback. */
internal fun bestAvailableSynopsis(detail: String?, current: String?, fallback: String?): String? =
    detail?.takeIf { it.isNotBlank() }
        ?: current?.takeIf { it.isNotBlank() }
        ?: fallback?.takeIf { it.isNotBlank() }

/** Cache identity covers the actual text, media type and both languages, not just the ID. */
internal fun synopsisTranslationCacheIdentity(
    entry: SynopsisTranslationEntry,
    raw: String,
    settings: SynopsisTranslationSettings
): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(raw.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return "v2:${entry.mediaType}:${settings.sourceLanguage}:${settings.targetLanguage}:$digest"
}

/**
 * Owns the list-detail synopsis request. Detail and fallback results may arrive in either
 * order; only the current raw text/entry/generation may publish or populate the cache.
 * Dependencies are injected so cancellation, partial failures and settings can be tested
 * without a device or a network connection.
 */
internal class SynopsisTranslationController(
    private val scope: CoroutineScope,
    private val readSettings: suspend () -> SynopsisTranslationSettings,
    private val readCache: (SynopsisTranslationEntry, String, SynopsisTranslationSettings) -> String?,
    private val writeCache: (SynopsisTranslationEntry, String, SynopsisTranslationSettings, String) -> Unit,
    private val translate: suspend (String, SynopsisTranslationSettings, (String) -> Unit) -> TranslationOutcome
) {
    private val mutableState = MutableStateFlow(SynopsisTranslationState())
    val state: StateFlow<SynopsisTranslationState> = mutableState.asStateFlow()

    private var entry: SynopsisTranslationEntry? = null
    private var job: Job? = null
    private var generation = 0

    fun reset(newEntry: SynopsisTranslationEntry) {
        invalidate()
        entry = newEntry
        mutableState.value = SynopsisTranslationState()
    }

    fun submit(rawText: String, force: Boolean = false) {
        val currentEntry = entry ?: return
        if (rawText.isBlank()) return
        val old = mutableState.value
        if (!force && old.original == rawText && (job?.isActive == true || old.complete)) return

        invalidate()
        val requestGeneration = generation
        // Do not keep a translation of an older/shorter synopsis while a new one loads.
        mutableState.value = SynopsisTranslationState(original = rawText)
        job = scope.launch {
            try {
                val settings = readSettings()
                val isCyrillic = rawText.any { it in '\u0400'..'\u04FF' }
                if (!settings.enabled && !isCyrillic && !force) return@launch
                if (!isCurrent(currentEntry, rawText, requestGeneration)) return@launch

                val cached = if (force) null else try {
                    readCache(currentEntry, rawText, settings)
                } catch (_: Exception) {
                    null
                }
                if (!cached.isNullOrBlank()) {
                    mutableState.update { it.copy(translated = cached, complete = true) }
                    return@launch
                }

                mutableState.update { it.copy(translating = true) }
                val outcome = translate(rawText, settings) { partial ->
                    // TranslationManager calls back on IO. Publish on the owning scope so
                    // an old job can never race a reset/new request on the UI thread.
                    scope.launch {
                        if (isCurrent(currentEntry, rawText, requestGeneration) && mutableState.value.translating && partial.isNotBlank()) {
                            mutableState.update { it.copy(translated = partial.takeIf { text -> text != rawText }) }
                        }
                    }
                }
                if (!isCurrent(currentEntry, rawText, requestGeneration)) return@launch
                val translated = outcome.text.takeIf { it.isNotBlank() && it != rawText }
                if (outcome.fullyTranslated && translated != null) {
                    try {
                        writeCache(currentEntry, rawText, settings, translated)
                    } catch (_: Exception) {
                        // A cache failure must not hide a successful translation.
                    }
                }
                mutableState.update {
                    it.copy(translated = translated, complete = outcome.fullyTranslated)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep the original/partial text visible. A later visit can retry.
            } finally {
                if (isCurrent(currentEntry, rawText, requestGeneration)) {
                    mutableState.update { it.copy(translating = false) }
                }
            }
        }
    }

    fun disable() {
        invalidate()
        mutableState.update {
            it.copy(translated = null, translating = false, showingOriginal = false, complete = false)
        }
    }

    fun toggleOriginal() {
        mutableState.update { if (it.translated != null) it.copy(showingOriginal = !it.showingOriginal) else it }
    }

    fun showErrorIfEmpty() {
        mutableState.update { if (it.original.isNullOrBlank()) it.copy(error = true) else it }
    }

    private fun invalidate() {
        generation++
        job?.cancel()
        job = null
    }

    private fun isCurrent(expected: SynopsisTranslationEntry, raw: String, version: Int): Boolean =
        entry == expected && generation == version && mutableState.value.original == raw
}
