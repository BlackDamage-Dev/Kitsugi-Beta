package com.kitsugi.animelist.data.manga

import android.content.Context
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import java.util.Locale

/** Reads the real ConfigurableSource contract instead of inventing Mihon-wide credential keys. */
internal object MihonSourceAuthentication {

    fun fields(context: Context, source: ConfigurableSource): List<MangaSourceCredentialField> =
        runCatching {
            flatten(preferenceScreen(context, source))
                .filterIsInstance<EditTextPreference>()
                .filter { isCredential(it) }
                .map { preference ->
                    MangaSourceCredentialField(
                        key = preference.key,
                        title = preference.title?.toString()?.takeIf { it.isNotBlank() }
                            ?: preference.key,
                        value = preference.text.orEmpty(),
                        isSecret = isSecret(preference),
                    )
                }
                .distinctBy { it.key }
                .toList()
        }.getOrDefault(emptyList())

    fun save(context: Context, source: ConfigurableSource, values: Map<String, String>) {
        val allowedKeys = fields(context, source).map { it.key }.toSet()
        require(allowedKeys.containsAll(values.keys)) {
            "Eklenti hesap alanları yeniden okunamadı; değerler kaydedilmedi"
        }
        source.getSourcePreferences().edit().apply {
            values.forEach { (key, value) ->
                if (key in allowedKeys) putString(key, value)
            }
        }.apply()
    }

    private fun preferenceScreen(context: Context, source: ConfigurableSource): PreferenceScreen {
        // Mirror MangaExtensionLoader's setup exactly, including the source_<id> preference file.
        val managerClass = PreferenceManager::class.java
        val constructor = managerClass.getDeclaredConstructor(Context::class.java)
        constructor.isAccessible = true
        val manager = constructor.newInstance(context)
        managerClass.getMethod("setSharedPreferencesName", String::class.java)
            .invoke(manager, "source_${source.id}")
        managerClass.getMethod("setSharedPreferencesMode", Int::class.javaPrimitiveType)
            .invoke(manager, Context.MODE_PRIVATE)
        val screen = managerClass.getMethod("createPreferenceScreen", Context::class.java)
            .invoke(manager, context) as PreferenceScreen
        source.setupPreferenceScreen(screen)
        return screen
    }

    private fun flatten(root: PreferenceGroup): Sequence<Preference> = sequence {
        for (index in 0 until root.preferenceCount) {
            val preference = root.getPreference(index)
            yield(preference)
            if (preference is PreferenceGroup) yieldAll(flatten(preference))
        }
    }

    private fun isCredential(preference: EditTextPreference): Boolean {
        val key = normalize(preference.key)
        if ("user_agent" in key || "useragent" in key) return false
        val title = normalize(preference.title?.toString().orEmpty())
        val explicitAuthKey = key == "auth" || key == "authentication" ||
            key.startsWith("auth_") || key.endsWith("_auth")
        return explicitAuthKey || CREDENTIAL_MARKERS.any { it in key } || TITLE_MARKERS.any { it in title }
    }

    private fun isSecret(preference: EditTextPreference): Boolean {
        val text = normalize("${preference.key} ${preference.title?.toString().orEmpty()}")
        return SECRET_MARKERS.any { it in text }
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9çğıöşü]+"), "_")

    private val CREDENTIAL_MARKERS = listOf(
        "username", "user_name", "login", "email", "e_mail", "password", "passwd",
        "token", "secret", "bearer", "apikey", "api_key", "session", "cookie", "authorization",
        "kullanici", "kullanıcı", "sifre", "şifre", "parola", "eposta", "oturum",
    )
    private val TITLE_MARKERS = listOf(
        "username", "login", "email", "password", "token", "secret", "bearer", "session", "cookie",
        "authentication", "authorization", "giriş", "giris", "kullanici", "kullanıcı",
        "sifre", "şifre", "parola", "eposta", "oturum", "hesap",
    )
    private val SECRET_MARKERS = listOf(
        "password", "passwd", "token", "secret", "bearer", "apikey", "api_key", "session", "cookie",
        "sifre", "şifre", "parola",
    )
}
