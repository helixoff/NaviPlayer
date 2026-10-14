package app.naviplayer

import android.content.Context
import org.json.JSONArray
import java.time.LocalDate

/** Persists the session and lightweight, device-local preferences. */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("navi", Context.MODE_PRIVATE)

    fun load(): Credentials? {
        val url = prefs.getString("url", null) ?: return null
        val user = prefs.getString("user", null) ?: return null
        val pass = prefs.getString("pass", null) ?: return null
        return Credentials(url, user, pass)
    }

    fun save(c: Credentials) =
        prefs.edit().putString("url", c.url).putString("user", c.user).putString("pass", c.password).apply()

    fun clear() = prefs.edit()
        .remove("url")
        .remove("user")
        .remove("pass")
        .apply()

    /** A stable salt keeps authenticated artwork URLs cacheable between launches. */
    fun salt(): String {
        prefs.getString("salt", null)?.let { return it }
        val s = newSalt()
        prefs.edit().putString("salt", s).apply()
        return s
    }

    // Search history
    fun history(): List<String> = try {
        val a = JSONArray(prefs.getString("history", "[]"))
        List(a.length()) { a.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }

    fun addHistory(q: String) {
        if (q.isBlank()) return
        val list = (listOf(q) + history().filter { !it.equals(q, ignoreCase = true) }).take(10)
        prefs.edit().putString("history", JSONArray(list).toString()).apply()
    }

    fun clearHistory() = prefs.edit().remove("history").apply()

    fun loadAppearance(): AndroidAppearanceSettings = AndroidAppearanceSettings(
        themeMode = prefs.getString("theme_mode", null)
            ?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
            ?: AppThemeMode.System,
        accent = prefs.getString("accent", null)
            ?.let { runCatching { AppAccent.valueOf(it) }.getOrNull() }
            ?: AppAccent.Dynamic,
        amoled = prefs.getBoolean("amoled", false),
    )

    fun saveAppearance(settings: AndroidAppearanceSettings) {
        prefs.edit()
            .putString("theme_mode", settings.themeMode.name)
            .putString("accent", settings.accent.name)
            .putBoolean("amoled", settings.amoled)
            .apply()
    }

    // Local profile customisation
    fun avatarUri(): String? = prefs.getString("avatar_uri", null)

    fun setAvatarUri(uri: String?) {
        val edit = prefs.edit()
        if (uri == null) edit.remove("avatar_uri") else edit.putString("avatar_uri", uri)
        edit.apply()
    }

    // Daily mix cache
    fun cached(key: String): String? {
        val v = prefs.getString("c_$key", null) ?: return null
        val i = v.indexOf('|')
        if (i < 0 || v.substring(0, i) != LocalDate.now().toString()) return null
        return v.substring(i + 1)
    }

    fun clearCache() {
        val e = prefs.edit()
        prefs.all.keys.filter { it.startsWith("c_") }.forEach { e.remove(it) }
        e.apply()
    }

    fun putCache(key: String, value: String) =
        prefs.edit().putString("c_$key", LocalDate.now().toString() + "|" + value).apply()
}
