package com.kitsugi.animelist.ui.screens.detail

import com.kitsugi.animelist.data.local.TranslationOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SynopsisTranslationControllerTest {
    private val entry = SynopsisTranslationEntry("1:anilist:123", "anilist", 123, "Anime")
    private val tr = SynopsisTranslationSettings(true, "auto", "tr")

    @Test
    fun automaticTranslationStartsFromTheStoredListSynopsis() = runBlocking {
        var calls = 0
        val controller = SynopsisTranslationController(this, { tr }, { _, _, _ -> null }, { _, _, _, _ -> }) { text, _, _ ->
            calls++
            TranslationOutcome("Türkçe: $text", true)
        }
        controller.reset(entry)
        controller.submit("An English synopsis")
        val state = withTimeout(2_000) { controller.state.first { it.complete } }
        assertEquals("An English synopsis", state.original)
        assertEquals("Türkçe: An English synopsis", state.translated)
        assertEquals(1, calls)
        assertFalse(state.translating)
        controller.toggleOriginal()
        assertTrue(controller.state.value.showingOriginal)
        controller.toggleOriginal()
        assertFalse(controller.state.value.showingOriginal)
    }

    @Test
    fun partialFailureIsVisibleButNeverCachedAsACompleteTranslation() = runBlocking {
        var calls = 0
        var writes = 0
        val controller = SynopsisTranslationController(this, { tr }, { _, _, _ -> null }, { _, _, _, _ -> writes++ }) { _, _, _ ->
            calls++
            if (calls == 1) TranslationOutcome("Kısmi metin", false)
            else TranslationOutcome("Tam metin", true)
        }
        controller.reset(entry)
        controller.submit("English synopsis")
        withTimeout(2_000) { controller.state.first { it.translated == "Kısmi metin" && !it.translating } }
        assertFalse(controller.state.value.complete)
        assertEquals(0, writes)
        controller.submit("English synopsis")
        withTimeout(2_000) { controller.state.first { it.complete } }
        assertEquals("Tam metin", controller.state.value.translated)
        assertEquals(2, calls)
        assertEquals(1, writes)
    }

    @Test
    fun slowOldSynopsisCannotOverwriteNewDetailOrCache() = runBlocking {
        val oldResponse = CompletableDeferred<TranslationOutcome>()
        val newResponse = CompletableDeferred<TranslationOutcome>()
        var oldPartial: ((String) -> Unit)? = null
        val writes = mutableListOf<String>()
        val controller = SynopsisTranslationController(this, { tr }, { _, _, _ -> null }, { _, raw, _, _ -> writes += raw }) { raw, _, partial ->
            if (raw == "old") {
                oldPartial = partial
                withContext(NonCancellable) { oldResponse.await() }
            } else newResponse.await()
        }
        controller.reset(entry)
        controller.submit("old")
        withTimeout(2_000) { controller.state.first { it.translating } }
        controller.submit("new")
        withTimeout(2_000) { controller.state.first { it.original == "new" && it.translating } }
        oldPartial?.invoke("bayat parça")
        oldResponse.complete(TranslationOutcome("eski çeviri", true))
        yield()
        assertEquals("new", controller.state.value.original)
        assertNull(controller.state.value.translated)
        newResponse.complete(TranslationOutcome("yeni çeviri", true))
        withTimeout(2_000) { controller.state.first { it.complete } }
        assertEquals("yeni çeviri", controller.state.value.translated)
        assertEquals(listOf("new"), writes)
    }

    @Test
    fun disabledSettingDoesNotShowAnExistingCachedTranslationAndCanBeEnabledLater() = runBlocking {
        var settings = tr.copy(enabled = false)
        var calls = 0
        val controller = SynopsisTranslationController(this, { settings }, { _, _, _ -> "Eski önbellek" }, { _, _, _, _ -> }) { _, _, _ ->
            calls++
            TranslationOutcome("Yeni çeviri", true)
        }
        controller.reset(entry)
        controller.submit("English")
        yield()
        assertEquals("English", controller.state.value.original)
        assertNull(controller.state.value.translated)
        assertEquals(0, calls)
        settings = tr
        controller.submit("English", force = true)
        withTimeout(2_000) { controller.state.first { it.complete } }
        assertEquals("Yeni çeviri", controller.state.value.translated)
        controller.disable()
        assertNull(controller.state.value.translated)
        assertFalse(controller.state.value.translating)
    }

    @Test
    fun lateFallbackNeverReplacesADetailOrNewlyStoredSynopsis() {
        assertEquals("detail", bestAvailableSynopsis("detail", "stored", "late fallback"))
        assertEquals("stored", bestAvailableSynopsis(null, "stored", "late fallback"))
        assertEquals("late fallback", bestAvailableSynopsis("  ", "", "late fallback"))
        assertNull(bestAvailableSynopsis(null, "  ", null))
    }

    @Test
    fun cacheIdentitySeparatesTextTypeAndBothLanguages() {
        val first = synopsisTranslationCacheIdentity(entry, "A synopsis", tr)
        assertNotEquals(first, synopsisTranslationCacheIdentity(entry, "A revised synopsis", tr))
        assertNotEquals(first, synopsisTranslationCacheIdentity(entry.copy(mediaType = "Manga"), "A synopsis", tr))
        assertNotEquals(first, synopsisTranslationCacheIdentity(entry, "A synopsis", tr.copy(targetLanguage = "en")))
        assertNotEquals(first, synopsisTranslationCacheIdentity(entry, "A synopsis", tr.copy(sourceLanguage = "ja")))
    }

    @Test
    fun changingLanguageWhileTranslatingCannotPublishAnOlderLanguage() = runBlocking {
        var settings = tr
        val oldResponse = CompletableDeferred<TranslationOutcome>()
        val newResponse = CompletableDeferred<TranslationOutcome>()
        val writes = mutableListOf<String>()
        val controller = SynopsisTranslationController(this, { settings }, { _, _, _ -> null },
            { _, _, usedSettings, _ -> writes += usedSettings.targetLanguage }) { _, usedSettings, _ ->
            if (usedSettings.targetLanguage == "tr") withContext(NonCancellable) { oldResponse.await() }
            else newResponse.await()
        }
        controller.reset(entry)
        controller.submit("Story")
        withTimeout(2_000) { controller.state.first { it.translating } }
        settings = tr.copy(targetLanguage = "en")
        controller.submit("Story", force = true)
        withTimeout(2_000) { controller.state.first { it.translating } }
        oldResponse.complete(TranslationOutcome("eski dil", true))
        yield()
        assertNull(controller.state.value.translated)
        newResponse.complete(TranslationOutcome("new language", true))
        withTimeout(2_000) { controller.state.first { it.complete } }
        assertEquals("new language", controller.state.value.translated)
        assertEquals(listOf("en"), writes)
    }

    @Test
    fun delayedPartialCannotReplaceACompletedResult() = runBlocking {
        var partialCallback: ((String) -> Unit)? = null
        val controller = SynopsisTranslationController(this, { tr }, { _, _, _ -> null }, { _, _, _, _ -> }) { _, _, partial ->
            partialCallback = partial
            TranslationOutcome("tam çeviri", true)
        }
        controller.reset(entry)
        controller.submit("Story")
        withTimeout(2_000) { controller.state.first { it.complete && !it.translating } }
        partialCallback?.invoke("gecikmiş parça")
        yield()
        assertEquals("tam çeviri", controller.state.value.translated)
    }

    @Test
    fun changingEntriesCancelsOldResultsAndKeepsOriginalOnFailure() = runBlocking {
        val blocked = CompletableDeferred<TranslationOutcome>()
        val controller = SynopsisTranslationController(this, { tr }, { _, _, _ -> null }, { _, _, _, _ -> }) { raw, _, _ ->
            if (raw == "first") withContext(NonCancellable) { blocked.await() }
            else throw IllegalStateException("offline")
        }
        controller.reset(entry)
        controller.submit("first")
        withTimeout(2_000) { controller.state.first { it.translating } }
        controller.reset(entry.copy(key = "2:kitsu:42", source = "kitsu", stableId = 42))
        controller.submit("second")
        withTimeout(2_000) { controller.state.first { it.original == "second" && !it.translating } }
        blocked.complete(TranslationOutcome("first translation", true))
        yield()
        assertEquals("second", controller.state.value.original)
        assertNull(controller.state.value.translated)
    }
}
