package com.kitsugi.animelist.data.manga

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class MangaSeriesPreferencesStoreTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var store: MangaSeriesPreferencesStore

    @Before
    fun setup() {
        fakePrefs = FakeSharedPreferences()
        store = MangaSeriesPreferencesStore(fakePrefs)
    }

    @Test
    fun returnsNullWhenNoCustomSettingsExist() {
        assertNull(store.get("https://example.com/manga/1"))
    }

    @Test
    fun savesAndRetrievesSeriesSpecificSettings() {
        val mangaUrl = "https://example.com/manga/solo-leveling"
        val custom = MangaSeriesPreferences(
            hasCustomSettings = true,
            readingMode = ReadingMode.Webtoon,
            colorFilter = ColorFilterType.Grayscale,
            fitMode = MangaFitMode.FitWidth,
            options = MangaReaderOptions(
                cropProfile = MangaCropProfile.White,
                cropWebtoonBorders = true,
                novelFontSizeSp = 22,
                bionicReading = true,
            )
        )

        store.save(mangaUrl, custom)

        val retrieved = store.get(mangaUrl)
        assertNotNull(retrieved)
        assertTrue(retrieved!!.hasCustomSettings)
        assertEquals(ReadingMode.Webtoon, retrieved.readingMode)
        assertEquals(ColorFilterType.Grayscale, retrieved.colorFilter)
        assertEquals(MangaFitMode.FitWidth, retrieved.fitMode)
        assertEquals(true, retrieved.options?.cropBorders)
        assertEquals(true, retrieved.options?.cropWebtoonBorders)
        assertEquals(22, retrieved.options?.novelFontSizeSp)
        assertEquals(true, retrieved.options?.bionicReading)
    }

    @Test
    fun isolatesSettingsBetweenDifferentSeries() {
        val series1 = "https://example.com/manga/one-piece"
        val series2 = "https://example.com/manga/naruto"

        store.save(series1, MangaSeriesPreferences(hasCustomSettings = true, readingMode = ReadingMode.RightToLeft))
        store.save(series2, MangaSeriesPreferences(hasCustomSettings = true, readingMode = ReadingMode.LeftToRight))

        assertEquals(ReadingMode.RightToLeft, store.get(series1)?.readingMode)
        assertEquals(ReadingMode.LeftToRight, store.get(series2)?.readingMode)
    }

    @Test
    fun clearRemovesSeriesCustomization() {
        val series = "https://example.com/manga/bleach"
        store.save(series, MangaSeriesPreferences(hasCustomSettings = true, readingMode = ReadingMode.Vertical))
        assertNotNull(store.get(series))

        store.clear(series)
        assertNull(store.get(series))
    }

    class FakeSharedPreferences : SharedPreferences {
        private val map = ConcurrentHashMap<String, Any>()

        override fun getAll(): MutableMap<String, *> = HashMap(map)
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(map)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class FakeEditor(private val target: ConcurrentHashMap<String, Any>) : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) {
                    if (value != null) pending[key] = value else pending[key] = null
                }
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = null
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                target.clear()
                return this
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                pending.forEach { (k, v) ->
                    if (v == null) target.remove(k) else target[k] = v
                }
            }
        }
    }
}
