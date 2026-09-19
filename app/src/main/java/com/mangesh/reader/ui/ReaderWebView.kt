package com.mangesh.reader.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.mangesh.reader.R
import com.mangesh.reader.data.Entry
import com.mangesh.reader.data.Store
import java.io.ByteArrayInputStream

/**
 * The reading surface: a WebView that loads only what the phone already has.
 * Every request is intercepted — clip images from the store, the fonts from
 * the app's resources, everything else blocked — so rendering is the same offline as at home.
 */
class ReaderWebView(
    context: Context,
    private val store: Store,
    bgColor: Int,
    private val onExternalLink: (String) -> Unit,
) {
    /** Set by whichever ReaderScreen is showing the view; callbacks go to the current one. */
    var bridge: Bridge? = null

    interface Bridge {
        fun onScroll(fraction: Float, dy: Float, y: Float)
        fun onLongPress(line: Int, quote: String)
        fun onDone()
        fun onDrop()
        fun onNext()
        fun onReplace(uuid: String)
        fun onTextStep(delta: Int)
        fun onPage(page: Int, pages: Int, fraction: Float, byUser: Boolean)
        fun onTap()
        /** Where the sections start, for the progress line's ticks: the page's JSON, see Scrub.parse. */
        fun onSections(json: String)
    }

    val view: WebView = WebView(context)
    private val main = Handler(Looper.getMainLooper())
    private var pageHtml: String? = null
    private var loaded: String? = null
    private var initialScroll = 0f
    private var pendingJson = "[]"
    private var styleJs = ""

    init {
        setup(context)
        view.setBackgroundColor(bgColor)
    }

    fun destroy() {
        (view.parent as? android.view.ViewGroup)?.removeView(view)
        view.destroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setup(context: Context) {
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(false)
            builtInZoomControls = false
            textZoom = 100
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        view.overScrollMode = View.OVER_SCROLL_NEVER
        // Opaque, in the page's own colour (set by the caller): a transparent WebView is
        // composited over the Compose surface every frame and is a known source of flicker.
        view.addJavascriptInterface(object {
            @JavascriptInterface fun onScroll(fraction: Double, dy: Double, y: Double) { main.post { bridge?.onScroll(fraction.toFloat(), dy.toFloat(), y.toFloat()) } }
            @JavascriptInterface fun onLongPress(line: Int, quote: String) { main.post { bridge?.onLongPress(line, quote) } }
            @JavascriptInterface fun done() { main.post { bridge?.onDone() } }
            @JavascriptInterface fun drop() { main.post { bridge?.onDrop() } }
            @JavascriptInterface fun next() { main.post { bridge?.onNext() } }
            @JavascriptInterface fun onReplace(uuid: String) { main.post { bridge?.onReplace(uuid) } }
            @JavascriptInterface fun onTextStep(delta: Int) { main.post { bridge?.onTextStep(delta) } }
            @JavascriptInterface fun onPage(page: Int, pages: Int, fraction: Double, byUser: Boolean) { main.post { bridge?.onPage(page, pages, fraction.toFloat(), byUser) } }
            @JavascriptInterface fun onTap() { main.post { bridge?.onTap() } }
            @JavascriptInterface fun onSections(json: String) { main.post { bridge?.onSections(json) } }
        }, "Android")
        view.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(v: WebView, request: WebResourceRequest): WebResourceResponse? {
                val u = request.url
                if (u.host == HOST) {
                    FONTS[u.path]?.let { return WebResourceResponse("font/ttf", null, context.resources.openRawResource(it)) }
                    when (u.path) {
                        "/report/index.html" -> pageHtml?.let {
                            return WebResourceResponse("text/html", "utf-8", ByteArrayInputStream(it.toByteArray(Charsets.UTF_8)))
                        }
                        "/report/asset" -> {
                            val f = u.getQueryParameter("path")?.let { store.assetFile(it) }
                            if (f?.isFile == true) return WebResourceResponse(mime(f.name), null, f.inputStream())
                        }
                        "/lib/mermaid.min.js" -> return WebResourceResponse("application/javascript", "utf-8", context.assets.open("mermaid.min.js"))
                        else -> katexAsset(u.path)?.let { (asset, type) ->
                            return WebResourceResponse(type, if (type.startsWith("font/")) null else "utf-8", context.assets.open(asset))
                        }
                    }
                }
                return blocked()
            }

            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith(PAGE_URL)) return false   // the page itself and its #anchors
                onExternalLink(url)
                return true
            }

            override fun onPageFinished(v: WebView, url: String?) {
                // position first, then reveal: no flash of the top of the page before the jump
                v.evaluateJavascript("$styleJs setScroll($initialScroll); insertPending($pendingJson); reveal();", null)
            }
        }
    }

    /** Load a page unless it is already showing; scroll to `scroll`, apply the look (`style`, a call to the
     *  page's applyStyle) and show the phone's own comments.
     *  The page is served to the WebView through the interceptor at a real URL (the same way
     *  WebViewAssetLoader works), so every request the page makes, the document included, is ours. */
    fun load(html: String, scroll: Float, pending: String, bgColor: Int, style: String) {
        view.setBackgroundColor(bgColor)
        initialScroll = scroll
        pendingJson = pending
        styleJs = style
        if (loaded == html) {
            view.evaluateJavascript("$styleJs insertPending($pendingJson);", null)
            return
        }
        loaded = html
        pageHtml = html
        view.loadUrl(PAGE_URL)
    }

    fun js(code: String) = view.evaluateJavascript(code, null)

    /** A change of look while the page is up: applied in place, and remembered for the next load. */
    fun restyle(style: String, bgColor: Int) {
        styleJs = style
        view.setBackgroundColor(bgColor)
        js(style)
    }

    private fun blocked() = WebResourceResponse("text/plain", "utf-8", 404, "blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))

    private fun mime(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        "avif" -> "image/avif"
        else -> "application/octet-stream"
    }

    companion object {
        const val HOST = "appassets.androidplatform.net"
        const val BASE = "https://$HOST/report/"
        const val PAGE_URL = BASE + "index.html"

        /** The theme's fonts, served from res/font so the app's chrome and the page share one copy. */
        private val FONTS = mapOf(
            "/font/playfair.ttf" to R.font.playfair_display,
            "/font/playfair-italic.ttf" to R.font.playfair_display_italic,
            "/font/worksans.ttf" to R.font.work_sans,
            "/font/worksans-italic.ttf" to R.font.work_sans_italic,
        )
        private const val FONT_FACES =
            """@font-face{font-family:"Playfair Display";src:url(/font/playfair.ttf) format("truetype");font-weight:400 900;font-style:normal;font-display:swap}""" +
            """@font-face{font-family:"Playfair Display";src:url(/font/playfair-italic.ttf) format("truetype");font-weight:400 900;font-style:italic;font-display:swap}""" +
            """@font-face{font-family:"Work Sans";src:url(/font/worksans.ttf) format("truetype");font-weight:100 900;font-style:normal;font-display:swap}""" +
            """@font-face{font-family:"Work Sans";src:url(/font/worksans-italic.ttf) format("truetype");font-weight:100 900;font-style:italic;font-display:swap}"""

        /** Wrap the Mac's HTML fragment in the page: stylesheet, fonts, end-of-report row, script.
         *  The look is baked in for the first paint; after that it changes through applyStyle(). */
        fun page(context: Context, fragment: String, palette: Palette, textStep: Int, lineSpacing: String, justify: Boolean,
                 paged: Boolean, entry: Entry, next: Entry?): String {
            val dark = palette.dark
            val vars = (palette.vars() + TypeScale.vars(textStep, lineSpacing)).entries.joinToString(";") { (k, v) -> "$k:$v" }
            val css = context.assets.open("reader.css").bufferedReader().readText()
            val js = context.assets.open("reader.js").bufferedReader().readText()
            val end = when (entry.state) {
                "queued" -> """<div class="endrow"><button class="primary" onclick="Android.done()">Mark as read</button>""" +
                    """<button onclick="Android.drop()">Drop</button></div>""" +
                    (next?.let { """<div class="nextrow" onclick="Android.next()"><div class="meta">Next · ${it.minutes} min</div>""" +
                        """<div class="title">${esc(it.title)}</div></div>""" } ?: "")
                "done" -> """<div class="endnote">Read ${esc(entry.closed)}</div>"""
                else -> """<div class="endnote">Dropped ${esc(entry.closed)}${if (entry.reason.isNotEmpty()) " — " + esc(entry.reason) else ""}</div>"""
            }
            // KaTeX, like mermaid below, only on pages that need it. Its script comes before ours: reader.js
            // typesets the formulae first thing, so the page is laid out (and paged) with them in place.
            val hasTex = fragment.contains("class=\"tex")
            val katexCss = if (hasTex) """<link rel="stylesheet" href="/lib/katex/katex.min.css">""" else ""
            val katexJs = if (hasTex) """<script src="/lib/katex/katex.min.js"></script>""" else ""
            // mermaid is 3.5 MB; only pages with a diagram pay for it
            val mermaid = if (fragment.contains("<pre class=\"mermaid\"")) """<script src="/lib/mermaid.min.js"></script>""" else ""
            val bodyClass = listOfNotNull("loading", if (dark) "dark" else null, if (justify) "justify" else null,
                                          if (paged) "paged" else null).joinToString(" ")
            // the palette goes on <html> too: the canvas outside the body takes its colour from there
            return """<!doctype html><html lang="en" style="$vars"><head><meta charset="utf-8">""" +
                """<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">""" +
                """$katexCss<style>$FONT_FACES$css</style>""" +
                """</head><body class="$bodyClass" style="$vars;--top:${NAV_BAR_DP + 28}px;--bottom:64px"><div id="viewport" class="vp"><div id="cols" class="cols"><div id="content">$fragment$end</div></div></div>$mermaid$katexJs<script>$js</script></body></html>"""
        }

        /**
         * KaTeX typesets the TeX the Mac passes through as class="tex". Its script, stylesheet and fonts are
         * bundled under assets/katex and served at /lib/katex/; the stylesheet asks for its fonts by relative
         * URL, so they arrive here too. Returns (asset path, media type), or null for anything else — only the
         * file names KaTeX ships are let through, never a path out of the folder.
         */
        fun katexAsset(path: String?): Pair<String, String>? {
            val name = path?.removePrefix("/lib/katex/")?.takeIf { it != path } ?: return null
            return when {
                name == "katex.min.js" -> "katex/$name" to "application/javascript"
                name == "katex.min.css" -> "katex/$name" to "text/css"
                KATEX_FONT.matches(name) -> "katex/$name" to "font/woff2"
                else -> null
            }
        }
        private val KATEX_FONT = Regex("""fonts/KaTeX_[A-Za-z0-9_-]+\.woff2""")

        /** The clip's original URL, from the header block the Mac renders. */
        fun sourceUrl(fragment: String): String? =
            Regex("class=\"source\"><a href=\"([^\"]+)\"").find(fragment)?.groupValues?.get(1)
                ?.replace("&amp;", "&")?.replace("&quot;", "\"")

        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    }
}
