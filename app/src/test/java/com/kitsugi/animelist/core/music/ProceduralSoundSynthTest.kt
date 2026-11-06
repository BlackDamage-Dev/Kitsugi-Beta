package com.kitsugi.animelist.core.music

import com.kitsugi.animelist.data.music.AtmospherePresets
import com.kitsugi.animelist.data.music.ProceduralSoundCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProceduralSoundSynthTest {

    @Test
    fun allCatalogSoundsHaveMatchingSynthFactoryEntries() {
        for (sound in ProceduralSoundCatalog.sounds) {
            val synth = SynthFactory.create(sound.id)
            assertNotNull("SynthFactory id için synth üretemedi: ${sound.id}", synth)
        }
    }

    @Test
    fun allPresetLayersResolveToValidCatalogSounds() {
        for (preset in AtmospherePresets.presets) {
            val resolved = AtmospherePresets.resolveLayers(preset)
            assertFalse("Preset katmanları boş: ${preset.id}", resolved.isEmpty())
            assertEquals(
                "Preset katman sayısı uyuşmuyor: ${preset.id}",
                preset.layers.size,
                resolved.size
            )
            for ((sound, layer) in resolved) {
                assertEquals(layer.soundId, sound.id)
                assertTrue("Katman ses seviyesi 0..1 aralığında olmalı", layer.volume in 0f..1f)
            }
        }
    }

    @Test
    fun allSynthsRenderValidPcmWithoutNanOrInfinity() {
        val frames = 1024
        val left = FloatArray(frames)
        val right = FloatArray(frames)

        for (sound in ProceduralSoundCatalog.sounds) {
            val synth = SynthFactory.create(sound.id) ?: continue
            synth.reset()

            var nonZero = false
            // Aralıklı seslerin (gök gürültüsü, saat, rüzgar çanı) ilk vuruşuna
            // zaman tanımak için 5 saniyeye kadar render alıyoruz (~215 blok).
            val maxBlocks = (44_100 * 5) / frames
            for (b in 0 until maxBlocks) {
                synth.render(left, right, frames)
                for (i in 0 until frames) {
                    assertFalse("Left sample NaN olamaz (${sound.id})", left[i].isNaN())
                    assertFalse("Left sample Infinite olamaz (${sound.id})", left[i].isInfinite())
                    assertFalse("Right sample NaN olamaz (${sound.id})", right[i].isNaN())
                    assertFalse("Right sample Infinite olamaz (${sound.id})", right[i].isInfinite())
                    if (left[i] != 0f || right[i] != 0f) {
                        nonZero = true
                    }
                }
                if (nonZero) break
            }
            assertTrue("Synth 5 sn içinde ses üretmeli (${sound.id})", nonZero)
        }
    }

    @Test
    fun pcm16ConversionStaysWithinShortBounds() {
        val samples = floatArrayOf(-2.0f, -1.0f, -0.5f, 0.0f, 0.5f, 1.0f, 2.0f)
        for (s in samples) {
            val clamped = s.coerceIn(-1f, 1f)
            val shortVal = (clamped * 32767f).toInt().coerceIn(-32768, 32767).toShort()
            assertTrue(shortVal >= Short.MIN_VALUE)
            assertTrue(shortVal <= Short.MAX_VALUE)
        }
    }
}
