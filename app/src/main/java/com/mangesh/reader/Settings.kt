package com.mangesh.reader

import android.content.Context

/** Preferences: where the Mac is, and how to read. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("reader", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString("serverUrl", DEFAULT_URL)!!
        set(v) = prefs.edit().putString("serverUrl", v.trim().trimEnd('/')).apply()

    var token: String
        get() = prefs.getString("token", "")!!
        set(v) = prefs.edit().putString("token", v.trim()).apply()

    var sort: String
        get() = prefs.getString("sort", "newest")!!
        set(v) = prefs.edit().putString("sort", v).apply()

    var filter: String
        get() = prefs.getString("filter", "all")!!
        set(v) = prefs.edit().putString("filter", v).apply()

    /** Index into TypeScale.sizes, 0–4; 2 is standard. Builds before the reader theme kept a pixel size. */
    var textSize: Int
        get() = if (prefs.contains("textStep")) prefs.getInt("textStep", 2)
                else (prefs.getInt("textSize", 17) - 15).coerceIn(0, 4)
        set(v) = prefs.edit().putInt("textStep", v.coerceIn(0, 4)).apply()

    /** compact | standard | generous */
    var lineSpacing: String
        get() = prefs.getString("lineSpacing", "standard")!!
        set(v) = prefs.edit().putString("lineSpacing", v).apply()

    var paged: Boolean
        get() = prefs.getBoolean("paged", true)
        set(v) = prefs.edit().putBoolean("paged", v).apply()

    var justify: Boolean
        get() = prefs.getBoolean("justify", false)
        set(v) = prefs.edit().putBoolean("justify", v).apply()

    /** system | light | sepia | dark */
    var theme: String
        get() = prefs.getString("theme", "system")!!
        set(v) = prefs.edit().putString("theme", v).apply()

    /** Where Bonjour last found the Mac (full URL incl. namespace); used when the name in serverUrl fails. */
    var discoveredUrl: String
        get() = prefs.getString("discoveredUrl", "")!!
        set(v) = prefs.edit().putString("discoveredUrl", v).apply()

    companion object {
        const val DEFAULT_URL = "http://starlight.local:8642/reader"
    }
}
