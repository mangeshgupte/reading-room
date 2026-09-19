package com.mangesh.reader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The handoff's tokens, pinned: a change here is a change to the design. */
class ThemeTokensTest {

    @Test
    fun paletteVarsCarryTheHandoffColours() {
        val light = Palettes.light.vars()
        assertEquals("#F4F1EA", light["--bg"])
        assertEquals("#23201C", light["--fg"])
        assertEquals("#4D4944", light["--fg2"])
        assertEquals("#74706A", light["--muted"])
        assertEquals("#D8D2C6", light["--rule"])
        assertEquals("#C2603C", light["--accent"])
        assertEquals("rgba(194,96,60,0.16)", light["--hl"])
        assertEquals("rgba(194,96,60,0.08)", light["--press"])

        val dark = Palettes.dark.vars()
        assertEquals("#1C1A17", dark["--bg"])
        assertEquals("#23211D", dark["--surface"])
        assertEquals("#E9E4DC", dark["--fg"])
        assertEquals("#9D968E", dark["--muted"])
        assertEquals("#3A3631", dark["--rule"])
        assertEquals("#E2825D", dark["--accent"])
        assertEquals("rgba(226,130,93,0.2)", dark["--hl"])
    }

    @Test
    fun themeFollowsTheSystemUnlessOverridden() {
        assertEquals("light", Palettes.resolve("system", systemDark = false).key)
        assertEquals("dark", Palettes.resolve("system", systemDark = true).key)
        assertEquals("sepia", Palettes.resolve("sepia", systemDark = true).key)
        assertEquals("light", Palettes.resolve("light", systemDark = true).key)
        assertEquals("dark", Palettes.resolve("dark", systemDark = false).key)
        // sepia is the light values on a warmer ground
        assertEquals(Palettes.light.fg, Palettes.sepia.fg)
        assertEquals(Palettes.light.accent, Palettes.sepia.accent)
    }

    @Test
    fun typeScalesMatchTheHandoff() {
        // compact (2a), standard (2b), generous (2c)
        assertEquals(mapOf("--fs" to "16px", "--title" to "28px", "--section" to "20px", "--tlh" to "1.12",
                           "--lh" to "1.5", "--gap" to "12px", "--pad" to "22px"), TypeScale.vars(1, "compact"))
        assertEquals(mapOf("--fs" to "17px", "--title" to "32px", "--section" to "22px", "--tlh" to "1.12",
                           "--lh" to "1.62", "--gap" to "14px", "--pad" to "24px"), TypeScale.vars(2, "standard"))
        assertEquals(mapOf("--fs" to "18px", "--title" to "36px", "--section" to "24px", "--tlh" to "1.1",
                           "--lh" to "1.72", "--gap" to "16px", "--pad" to "30px"), TypeScale.vars(3, "generous"))
        assertEquals(listOf(13, 15, 17, 19, 21), TypeScale.sizes.map { it.glyph })
        // out-of-range steps and unknown spacing fall back rather than throw
        assertEquals(TypeScale.vars(4, "standard"), TypeScale.vars(9, "nonsense"))
        assertEquals(TypeScale.vars(0, "standard"), TypeScale.vars(-3, "standard"))
    }

    @Test
    fun styleJsCallsApplyStyleWithVarsAndClasses() {
        val js = TypeScale.styleJs(Palettes.dark, TypeScale.STANDARD, "standard", justify = true)
        assertTrue(js.startsWith("if (window.applyStyle) applyStyle({"))
        assertTrue(js.contains("\"--fs\":\"17px\""))
        assertTrue(js.contains("\"--accent\":\"#E2825D\""))
        assertTrue(js.contains("\"dark\":true"))
        assertTrue(js.contains("\"justify\":true"))
    }
}
