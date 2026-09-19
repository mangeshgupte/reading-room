package com.mangesh.reader.ui

import org.json.JSONObject

/**
 * The reader's type scales, from the theme handoff. The handoff defines three bundles (compact,
 * standard, generous); the settings sheet splits them into two controls, so the bundle is split
 * the same way: text size carries the sizes, line spacing carries the air (leading, paragraph
 * gap, screen padding). Steps 1–3 are the designed scales; 0 and 4 continue the same intervals.
 */
object TypeScale {

    /** Pixel sizes on the page; `glyph` is the size of this step's "A" in the settings control. */
    data class Size(val body: Int, val title: Int, val section: Int, val glyph: Int)

    val sizes = listOf(
        Size(body = 15, title = 24, section = 18, glyph = 13),
        Size(body = 16, title = 28, section = 20, glyph = 15),   // compact
        Size(body = 17, title = 32, section = 22, glyph = 17),   // standard
        Size(body = 18, title = 36, section = 24, glyph = 19),   // generous
        Size(body = 19, title = 40, section = 26, glyph = 21),
    )
    const val STANDARD = 2

    data class Spacing(val key: String, val label: String, val lineHeight: Float, val gap: Int, val pad: Int)

    val spacings = listOf(
        Spacing("compact", "Compact", 1.5f, gap = 12, pad = 22),
        Spacing("standard", "Standard", 1.62f, gap = 14, pad = 24),
        Spacing("generous", "Generous", 1.72f, gap = 16, pad = 30),
    )

    fun size(step: Int): Size = sizes[step.coerceIn(0, sizes.lastIndex)]
    fun spacing(key: String): Spacing = spacings.firstOrNull { it.key == key } ?: spacings[1]

    /** The page's CSS variables for this combination. */
    fun vars(step: Int, spacingKey: String): Map<String, String> {
        val s = size(step)
        val sp = spacing(spacingKey)
        return linkedMapOf(
            "--fs" to "${s.body}px", "--title" to "${s.title}px", "--section" to "${s.section}px",
            "--tlh" to (if (s.title >= 36) "1.1" else "1.12"),
            "--lh" to sp.lineHeight.toString(), "--gap" to "${sp.gap}px", "--pad" to "${sp.pad}px",
        )
    }

    /** A call to the page's applyStyle(): every look setting at once, applied live with the place kept. */
    fun styleJs(palette: Palette, step: Int, spacingKey: String, justify: Boolean): String {
        val vars = JSONObject()
        for ((k, v) in palette.vars() + vars(step, spacingKey)) vars.put(k, v)
        val classes = JSONObject().put("dark", palette.dark).put("justify", justify)
        return "if (window.applyStyle) applyStyle($vars, $classes);"
    }
}
