package com.mangesh.reader.ui

/**
 * A reading colour scheme: page colours (as CSS variables) and the app chrome derived from them.
 * Tokens are from the reader theme handoff (Playfair Display + Work Sans, terracotta). The accent
 * is a stroke, an underline or a small figure; it is never a filled button or block.
 */
data class Palette(
    val key: String,
    val name: String,
    val dark: Boolean,
    val bg: Long, val surface: Long, val fg: Long, val fg2: Long, val muted: Long, val rule: Long, val accent: Long, val code: Long,
    /** Alpha of the accent tint behind a highlighted passage, and behind a pressed row. */
    val hlAlpha: Float, val pressAlpha: Float,
) {
    /** The page's stylesheet reads these variables; set inline on <html> and <body>. */
    fun vars(): Map<String, String> = linkedMapOf(
        "--bg" to hex(bg), "--surface" to hex(surface), "--fg" to hex(fg), "--fg2" to hex(fg2), "--muted" to hex(muted),
        "--rule" to hex(rule), "--accent" to hex(accent), "--code" to hex(code),
        "--hl" to tint(hlAlpha), "--press" to tint(pressAlpha),
    )

    private fun hex(v: Long) = "#%06X".format(v and 0xFFFFFF)
    private fun tint(alpha: Float) = "rgba(${(accent shr 16) and 0xFF},${(accent shr 8) and 0xFF},${accent and 0xFF},$alpha)"

    val bgArgb: Int get() = (0xFF000000L or bg).toInt()
}

object Palettes {
    val light = Palette("light", "Light", false,
        bg = 0xF4F1EA, surface = 0xFAF8F3, fg = 0x23201C, fg2 = 0x4D4944, muted = 0x74706A, rule = 0xD8D2C6, accent = 0xC2603C,
        code = 0xEBE7DE, hlAlpha = 0.16f, pressAlpha = 0.08f)

    /** Not designed yet: the light values on a warmer ground. */
    val sepia = Palette("sepia", "Sepia", false,
        bg = 0xF0E6D2, surface = 0xF6EFE0, fg = 0x23201C, fg2 = 0x4D4944, muted = 0x74706A, rule = 0xDBCFB7, accent = 0xC2603C,
        code = 0xE7DCC5, hlAlpha = 0.16f, pressAlpha = 0.08f)

    val dark = Palette("dark", "Dark", true,
        bg = 0x1C1A17, surface = 0x23211D, fg = 0xE9E4DC, fg2 = 0xC5BFB6, muted = 0x9D968E, rule = 0x3A3631, accent = 0xE2825D,
        code = 0x23211D, hlAlpha = 0.2f, pressAlpha = 0.1f)

    val all = listOf(light, sepia, dark)

    /** `theme` is system | light | sepia | dark; system follows the phone. */
    fun resolve(theme: String, systemDark: Boolean): Palette = when (theme) {
        "light" -> light
        "sepia" -> sepia
        "dark" -> dark
        else -> if (systemDark) dark else light
    }
}
