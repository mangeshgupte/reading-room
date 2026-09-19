package com.mangesh.reader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** The WebView serves KaTeX from assets/katex at /lib/katex/ — those files and nothing else. */
class KatexAssetTest {

    @Test
    fun servesTheScriptTheStylesheetAndTheFonts() {
        assertEquals("katex/katex.min.js" to "application/javascript", ReaderWebView.katexAsset("/lib/katex/katex.min.js"))
        assertEquals("katex/katex.min.css" to "text/css", ReaderWebView.katexAsset("/lib/katex/katex.min.css"))
        assertEquals("katex/fonts/KaTeX_Main-Regular.woff2" to "font/woff2",
                     ReaderWebView.katexAsset("/lib/katex/fonts/KaTeX_Main-Regular.woff2"))
    }

    @Test
    fun nothingElseAndNeverAPathOutOfTheFolder() {
        for (path in listOf(null, "", "/lib/katex/", "/lib/katex/../reader.js", "/lib/katex/fonts/../../reader.css",
                            "/lib/katex/fonts/KaTeX_Main-Regular.ttf", "/lib/katex/LICENSE", "/lib/mermaid.min.js",
                            "/report/index.html", "/lib/katex/fonts/evil/KaTeX_x.woff2", "lib/katex/katex.min.js")) {
            assertNull(path, ReaderWebView.katexAsset(path))
        }
    }

    @Test
    fun everyFontTheStylesheetAsksForIsBundledAndServable() {
        val dir = File("src/main/assets/katex")
        val css = File(dir, "katex.min.css").readText()
        val fonts = Regex("""url\((fonts/[^)]+\.woff2)\)""").findAll(css).map { it.groupValues[1] }.toSet()
        assert(fonts.size >= 15) { "expected KaTeX's font set, found ${fonts.size}" }
        for (f in fonts) {
            assert(File(dir, f).isFile) { "$f is not bundled" }
            assertEquals("katex/$f" to "font/woff2", ReaderWebView.katexAsset("/lib/katex/$f"))
        }
    }
}
