package com.mangesh.reader.ui

import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mangesh.reader.data.Entry
import com.mangesh.reader.data.Store
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

private data class CommentTarget(val line: Int, val quote: String)

/**
 * After a riffle, the way back to where the reading was — the finger kept in the old page. It survives
 * a look around (`wander`, in pages) and goes once the reader has plainly settled into the new place.
 */
private data class WayBack(val fraction: Float, val label: String, val wander: Float = 0f) {
    fun wandered(pages: Float): WayBack? = copy(wander = wander + pages).takeIf { it.wander < WAY_BACK_PAGES }
}
private const val WAY_BACK_PAGES = 3f
/** Scroll mode's measure of a page, in CSS px, for the same allowance. */
private const val SCREEN_PX = 600f

/** Height of the overlaid nav row; the page reserves the same inset (and the design's 28 below it) so nothing hides under it at the top. */
const val NAV_BAR_DP = 48

/**
 * Chrome auto-hide with hysteresis: hide after HIDE_PX of continuous downward
 * scroll, show after SHOW_PX upward; a direction change resets the count; at
 * most one toggle per TOGGLE_GAP_MS; always shown near the top of the page.
 * The bar overlays the WebView, so toggling never resizes the page.
 */
private class ChromeGate {
    private var accum = 0f
    private var lastToggle = 0L
    fun onScroll(dy: Float, y: Float, fraction: Float, visible: Boolean): Boolean {
        val now = SystemClock.uptimeMillis()
        if (y < NAV_BAR_DP || fraction > 0.985f) { accum = 0f; if (!visible) lastToggle = now; return true }
        accum = if ((dy > 0f && accum < 0f) || (dy < 0f && accum > 0f)) dy else accum + dy
        if (now - lastToggle < TOGGLE_GAP_MS) return visible
        return when {
            visible && accum > HIDE_PX -> { accum = 0f; lastToggle = now; false }
            !visible && accum < -SHOW_PX -> { accum = 0f; lastToggle = now; true }
            else -> visible
        }
    }
    companion object { const val HIDE_PX = 32f; const val SHOW_PX = 48f; const val TOGGLE_GAP_MS = 400L }
}

@Composable
fun ReaderScreen(vm: QueueViewModel, id: Int, host: ReaderHost) {
    val context = LocalContext.current
    val state by vm.store.state.collectAsState()
    val entry = state.visible.firstOrNull { it.id == id }
    if (entry == null) {
        LaunchedEffect(id) { vm.back() }
        return
    }
    val fragment = remember(id, entry.htmlHash) { vm.store.reportHtml(id) }
    val palette = currentPalette(vm)
    val next = remember(id, state.entries, state.outbox, vm.sort, vm.filter) { vm.nextAfter(id) }
    // The look (theme, sizes, spacing, justify) is applied live in the page, so it is not a key here —
    // except that mermaid draws a diagram in the theme of the moment, so a page with one reloads on light/dark.
    val hasDiagram = remember(fragment) { fragment?.contains("<pre class=\"mermaid\"") == true }
    val pageHtml = remember(fragment, vm.paged, next?.id, entry.state, if (hasDiagram) palette.dark else null) {
        fragment?.let { ReaderWebView.page(context, it, palette, vm.textSize, vm.lineSpacing, vm.justify, vm.paged, entry, next) }
    }
    val styleJs = TypeScale.styleJs(palette, vm.textSize, vm.lineSpacing, vm.justify)
    val sourceUrl = remember(fragment) { fragment?.let { ReaderWebView.sourceUrl(it) } }

    val gate = remember { ChromeGate() }
    var progress by remember(id) { mutableFloatStateOf(entry.progress) }
    var pageInfo by remember(id) { mutableStateOf<Pair<Int, Int>?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var lookOpen by remember { mutableStateOf(false) }
    // Quick scroll: the progress line is a scrubber. `scrub` is the place under the finger while it is down.
    var sections by remember(id) { mutableStateOf(emptyList<Section>()) }
    var scrub by remember(id) { mutableStateOf<Float?>(null) }
    var scrubStart by remember(id) { mutableFloatStateOf(0f) }
    var scrubSent by remember(id) { mutableFloatStateOf(-1f) }
    var wayBack by remember(id) { mutableStateOf<WayBack?>(null) }
    var commentTarget by remember { mutableStateOf<CommentTarget?>(null) }
    var dropOpen by remember { mutableStateOf(false) }

    fun openExternal(url: String) {
        try { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url)) }
        catch (e: Exception) { Toast.makeText(context, "No browser for $url", Toast.LENGTH_SHORT).show() }
    }
    // The session's one WebView: created once, re-attached on every visit, never reloaded for a tab switch.
    val web = remember { host.web ?: ReaderWebView(context, vm.store, palette.bgArgb, ::openExternal).also { host.web = it } }
    fun refreshPending() = web.js("insertPending(${pendingJson(vm.store, id)});")

    SideEffect {
        web.bridge = object : ReaderWebView.Bridge {
            override fun onScroll(fraction: Float, dy: Float, y: Float) {
                if (scrub != null) return   // the app is driving; toggling the bars now would move the line under the finger
                progress = fraction
                vm.reportProgress(id, fraction)
                if (dy != 0f) wayBack = wayBack?.wandered(abs(dy) / SCREEN_PX)
                val show = gate.onScroll(dy, y, fraction, vm.chromeVisible)
                if (show != vm.chromeVisible) vm.chromeVisible = show
            }
            override fun onLongPress(line: Int, quote: String) {
                val replacing = vm.replaceUuid
                if (replacing != null) {
                    vm.replaceComment(entry, replacing, line)
                    web.js("clearHighlight();")
                    refreshPending()
                } else {
                    commentTarget = CommentTarget(line, quote)
                }
            }
            override fun onDone() { vm.done(entry); vm.back() }
            override fun onDrop() { dropOpen = true }
            override fun onNext() { next?.let { vm.open(it.id) } ?: vm.back() }
            override fun onReplace(uuid: String) {
                vm.replaceUuid = uuid
                Toast.makeText(context, "Long-press a paragraph to place this comment again", Toast.LENGTH_LONG).show()
            }
            override fun onTextStep(delta: Int) { vm.chooseTextSize(vm.textSize + delta) }
            override fun onPage(page: Int, pages: Int, fraction: Float, byUser: Boolean) {
                pageInfo = page to pages
                if (scrub == null) {
                    progress = fraction
                    vm.reportProgress(id, fraction)
                }
                if (byUser) wayBack = wayBack?.wandered(1f)
                if (byUser && vm.chromeVisible) vm.chromeVisible = false
            }
            override fun onTap() { vm.chromeVisible = !vm.chromeVisible }
            override fun onSections(json: String) { sections = Scrub.parse(json) }
        }
    }

    // One load per document; a revisit finds the page already there. Changes of look restyle it in place.
    LaunchedEffect(pageHtml) {
        if (pageHtml != null) web.load(pageHtml, progress, pendingJson(vm.store, id), palette.bgArgb, styleJs)
    }
    LaunchedEffect(styleJs) { web.restyle(styleJs, palette.bgArgb) }

    var tabsHeight by remember { mutableIntStateOf(0) }
    val chromeShown = vm.chromeVisible || pageHtml == null
    // The bottom group (progress line + tabs) slides as one unit by the tabs' height,
    // so the progress line rides on the bar and rests at the screen edge when it is gone.
    val bottomShift by animateFloatAsState(if (chromeShown) 0f else tabsHeight.toFloat(), label = "bottomChrome")

    Scaffold { padding ->
        Box(Modifier.padding(padding).fillMaxSize().clipToBounds()) {
            if (pageHtml == null) {
                NotDownloaded(entry, vm)
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                        (web.view.parent as? ViewGroup)?.removeView(web.view)   // re-attach the session view
                        web.view
                    },
                    update = { },
                )
            }
            // Bottom chrome floats over the page: the progress footer rides on the tabs, moving together.
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().graphicsLayer { translationY = bottomShift }) {
                // The progress line is the page edge you thumb: drag along it to riffle (see Scrubber).
                Row(Modifier.fillMaxWidth().background(palette.bgColor).padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    val pages = if (vm.paged) pageInfo?.second else null
                    wayBack?.let { back ->
                        // the finger you kept in the old page
                        Text("↩ ${back.label}", style = Type.figure, color = palette.accentColor,
                             modifier = Modifier.pressTint(role = Role.Button) {
                                 wayBack = null
                                 progress = back.fraction
                                 web.js("scrubStart(); scrubTo(${back.fraction}); scrubEnd();")
                             }.padding(top = 12.dp, bottom = 12.dp, end = 12.dp))
                    }
                    Scrubber(
                        progress = progress, scrub = scrub, sections = sections, pages = pages,
                        modifier = Modifier.weight(1f),
                        onStart = { scrubStart = progress; scrubSent = -1f; scrub = progress; web.js("scrubStart();") },
                        onScrub = { f ->
                            scrub = f
                            // paged: only when the page changes; scroll: not for every pixel of finger
                            val changed = scrubSent < 0f ||
                                (if (pages != null) Scrub.pageAt(f, pages) != Scrub.pageAt(scrubSent, pages) else abs(f - scrubSent) >= 0.002f)
                            if (changed) { scrubSent = f; web.js("scrubTo($f);") }
                        },
                        onEnd = {
                            val f = scrub
                            scrub = null
                            if (f != null) {
                                web.js("scrubTo($f); scrubEnd();")
                                val moved = if (pages != null) Scrub.pageAt(f, pages) != Scrub.pageAt(scrubStart, pages)
                                            else abs(f - scrubStart) > 0.02f
                                // a second riffle keeps the first way back: it is where the reading was
                                if (moved && wayBack == null) wayBack = WayBack(scrubStart,
                                    if (pages != null) "${Scrub.pageAt(scrubStart, pages) + 1}" else "${(scrubStart * 100).toInt()}%")
                                else if (moved) wayBack = wayBack?.copy(wander = 0f)
                                progress = f
                            }
                        },
                    )
                    Spacer(Modifier.width(12.dp))
                    val at = scrub ?: progress
                    val left = Math.round(entry.minutes * (1f - at)).coerceAtLeast(0)
                    Text("${(at * 100).toInt()}% · $left min left", style = Type.figure, color = palette.mutedColor,
                         textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 116.dp))   // steady, so the line does not resize mid-drag
                }
                Box(Modifier.fillMaxWidth().wrapContentHeight().onSizeChanged { tabsHeight = it.height }) { AppTabs(vm) }
            }
            // The nav row floats over the page; the page keeps a matching top inset.
            AnimatedVisibility(
                visible = chromeShown,
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                enter = slideInVertically { -it },
                exit = slideOutVertically { -it },
            ) {
                Row(Modifier.fillMaxWidth().height(NAV_BAR_DP.dp).background(palette.bgColor).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("‹ Reading Room", style = Type.body, color = palette.mutedColor,
                         modifier = Modifier.pressTint(role = Role.Button) { vm.back() }.padding(horizontal = 12.dp, vertical = 12.dp))
                    Spacer(Modifier.weight(1f))
                    val actions = buildList {
                        if (entry.state == "queued") {
                            add("Mark as read" to { vm.done(entry); vm.back() })
                            add("Drop…" to { dropOpen = true })
                        }
                        if (sourceUrl != null) add("Open original" to { openExternal(sourceUrl) })
                    }
                    if (actions.isNotEmpty()) Box {
                        Text("⋯", style = Type.glyph, color = palette.mutedColor,
                             modifier = Modifier.pressTint(role = Role.Button) { menuOpen = true }.padding(horizontal = 12.dp, vertical = 10.dp))
                        ReaderMenu(menuOpen, onDismiss = { menuOpen = false }, items = actions)
                    }
                    // the page's look: text size, line spacing, theme, justify
                    Text("Aa", style = Type.glyph, color = palette.accentColor,
                         modifier = Modifier.pressTint(role = Role.Button) { lookOpen = true }.padding(horizontal = 12.dp, vertical = 10.dp))
                }
            }
        }
    }

    if (lookOpen) ReaderSheet(onDismiss = { lookOpen = false }) { ReadingControls(vm, withSystemTheme = false) }
    commentTarget?.let { t ->
        CommentSheet(t.quote, onDismiss = { commentTarget = null; web.js("clearHighlight();") }) { text ->
            vm.comment(entry, t.line, text)
            commentTarget = null
            web.js("clearHighlight();")
            refreshPending()
        }
    }
    if (dropOpen) {
        DropSheet(entry.title, onDismiss = { dropOpen = false }) { reason -> dropOpen = false; vm.drop(entry, reason); vm.back() }
    }
}

/** The phone's own comments on this report, for insertPending(): pending ones and refused ones. */
private fun pendingJson(store: Store, id: Int): String {
    val arr = JSONArray()
    for (a in store.pendingFor(id)) if (a.type == "comment") {
        arr.put(JSONObject().put("line", a.line).put("text", a.text).put("state", "pending"))
    }
    for (r in store.refusedFor(id)) if (r.action.type == "comment") {
        arr.put(JSONObject().put("line", r.action.line).put("text", r.action.text).put("state", "failed")
                    .put("error", r.error).put("uuid", r.action.uuid))
    }
    return arr.toString()
}

@Composable
private fun NotDownloaded(entry: Entry, vm: QueueViewModel) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize().padding(top = NAV_BAR_DP.dp).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
           verticalArrangement = Arrangement.Center) {
        Text(if (entry.missing) "This file is missing on the Mac." else "Not downloaded yet.", style = Type.sheetTitle, color = p.fgColor)
        Spacer(Modifier.height(6.dp))
        Text(if (entry.missing) "Run `reading-list refresh` there." else "Sync on home Wi-Fi to fetch it.",
             style = Type.excerpt, color = p.fg2Color)
        Spacer(Modifier.height(18.dp))
        if (!entry.missing) OutlineButton(if (vm.syncing) "Syncing…" else "Retry", onClick = { vm.sync(manual = true) },
                                          primary = true, enabled = !vm.syncing)
    }
}

@Composable
private fun CommentSheet(quote: String, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val p = LocalPalette.current
    ReaderSheet(onDismiss = onDismiss, spacing = 16.dp) {
        MetaLabel("Comment")
        // the passage, marked the way the page marks it
        Text("“${quote.take(120)}${if (quote.length > 120) "…" else ""}”", style = Type.excerpt, color = p.fg2Color,
             maxLines = 3, overflow = TextOverflow.Ellipsis)
        HairlineField(value = text, onValueChange = { text = it }, placeholder = "Your note", singleLine = false, minLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlineButton("Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
            OutlineButton("Comment", onClick = { onSubmit(text) }, modifier = Modifier.weight(1f), primary = true, enabled = text.isNotBlank())
        }
    }
}
