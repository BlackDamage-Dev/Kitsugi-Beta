package com.kitsugi.animelist.ui.screens.manga

import android.speech.tts.TextToSpeech

/** Apply a saved voice if it exists, otherwise try to restore the engine default. */
internal fun applyNovelSpeechVoice(engine: TextToSpeech, id: String): Boolean {
    val defaultVoice = try { engine.defaultVoice } catch (_: Exception) { null }
    if (id.isEmpty()) {
        return defaultVoice != null && try { engine.setVoice(defaultVoice) == TextToSpeech.SUCCESS } catch (_: Exception) { false }
    }
    val requested = try { engine.voices?.firstOrNull { it.name == id } } catch (_: Exception) { null }
    if (requested != null && try { engine.setVoice(requested) == TextToSpeech.SUCCESS } catch (_: Exception) { false }) return true
    if (defaultVoice != null) try { engine.setVoice(defaultVoice) } catch (_: Exception) { /* best effort */ }
    return false
}
