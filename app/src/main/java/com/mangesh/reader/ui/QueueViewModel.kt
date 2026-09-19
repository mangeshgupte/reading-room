package com.mangesh.reader.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangesh.reader.ReaderApp
import com.mangesh.reader.Settings
import com.mangesh.reader.data.Action
import com.mangesh.reader.data.Api
import com.mangesh.reader.data.Discovery
import com.mangesh.reader.data.Entry
import com.mangesh.reader.data.Store
import com.mangesh.reader.data.Sync
import com.mangesh.reader.data.SyncLogic
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/** One view model for the whole app: navigation, sync, and the three verbs. */
class QueueViewModel(app: Application) : AndroidViewModel(app) {

    enum class Tab { Queue, Reading, Settings }

    data class Snack(val message: String, val undo: Action? = null)

    private val readerApp = app as ReaderApp
    val store: Store get() = readerApp.store
    val settings: Settings get() = readerApp.settings

    var tab by mutableStateOf(Tab.Queue)
        private set
    /** The article the Reading tab shows: the last one opened. */
    var currentId by mutableStateOf<Int?>(null)
        private set
    /** Reader chrome (top bar, tabs): hidden while reading, revealed by scrolling up. */
    var chromeVisible by mutableStateOf(true)
    /** Full-screen reading: the Reading tab with an article open. */
    val readingMode: Boolean get() = tab == Tab.Reading && currentId != null
    var syncing by mutableStateOf(false)
        private set
    var syncProgress by mutableStateOf("")
        private set
    var sort by mutableStateOf(settings.sort)
        private set
    var filter by mutableStateOf(settings.filter)
        private set
    var textSize by mutableStateOf(settings.textSize)
        private set
    var theme by mutableStateOf(settings.theme)
        private set
    var justify by mutableStateOf(settings.justify)
        private set
    var paged by mutableStateOf(settings.paged)
        private set
    var lineSpacing by mutableStateOf(settings.lineSpacing)
        private set
    /** The queue's search box; not kept between launches. */
    var query by mutableStateOf("")
    /** A refused comment waiting to be placed again by the next long-press. */
    var replaceUuid by mutableStateOf<String?>(null)

    private val snackChannel = Channel<Snack>(Channel.BUFFERED)
    val snacks = snackChannel.receiveAsFlow()
    private var lastSyncAttempt = 0L

    // --- navigation ---

    fun open(id: Int) {
        flushProgress()
        store.markOpened(id)
        currentId = id
        chromeVisible = true
        tab = Tab.Reading
    }

    fun back() {
        flushProgress()
        replaceUuid = null
        tab = Tab.Queue
    }

    fun openSettings() = selectTab(Tab.Settings)

    fun selectTab(t: Tab) {
        if (t == tab) return
        flushProgress()
        if (t == Tab.Reading) {
            if (currentId == null) currentId = SyncLogic.continueCandidate(store.state.value.visible)?.id
            chromeVisible = true
        }
        tab = t
    }

    // --- sync ---

    fun syncOnOpen() {
        if (System.currentTimeMillis() - lastSyncAttempt < 60_000) return
        sync(manual = false)
    }

    fun sync(manual: Boolean) {
        if (syncing) return
        syncing = true
        lastSyncAttempt = System.currentTimeMillis()
        viewModelScope.launch {
            syncProgress = "looking for the Mac…"
            val result = Sync(store, Api(resolveBase(), settings.token)).run { syncProgress = it }
            syncing = false
            syncProgress = ""
            if (manual && !result.ok) snackChannel.send(Snack(result.message))
        }
    }

    /**
     * The server URL to use right now. A `.local` name in Settings means "find the Mac
     * over Bonjour"; the last address found is the fallback, the name itself the last resort.
     * An IP typed into Settings is used as-is.
     */
    suspend fun resolveBase(): String {
        val configured = settings.serverUrl
        val afterScheme = configured.substringAfter("://")
        val host = afterScheme.substringBefore("/").substringBefore(":")
        if (!host.endsWith(".local", ignoreCase = true)) return configured
        val path = afterScheme.substringAfter("/", "").trimEnd('/').let { if (it.isEmpty()) "" else "/$it" }
        val found = Discovery.find(getApplication())
        if (found != null) {
            settings.discoveredUrl = found + path
            return found + path
        }
        return settings.discoveredUrl.ifEmpty { configured }
    }

    // --- the three verbs ---

    fun done(entry: Entry) {
        val a = Action(uuid(), "done", entry.id, now())
        store.enqueue(a)
        snack("Marked as read", a)
    }

    fun drop(entry: Entry, reason: String) {
        val a = Action(uuid(), "drop", entry.id, now(), reason = reason.trim())
        store.enqueue(a)
        snack("Dropped", a)
    }

    fun undo(a: Action) = store.removePending(a.uuid)

    fun comment(entry: Entry, line: Int, text: String) {
        val t = text.trim().replace(Regex("\\s+"), " ")
        if (t.isEmpty()) return
        store.enqueue(Action(uuid(), "comment", entry.id, now(), line = line, hash = entry.htmlHash, text = t))
    }

    /** Re-anchor a refused comment on the current copy of the report. */
    fun replaceComment(entry: Entry, uuid: String, line: Int) {
        store.replaceRefusedComment(uuid, line, entry.htmlHash)
        replaceUuid = null
    }

    private fun snack(message: String, undo: Action?) {
        viewModelScope.launch { snackChannel.send(Snack(message, undo)) }
    }

    // --- reading position: in-memory at once, on disk after a pause ---

    private var progressJob: Job? = null
    private var pendingProgress: Pair<Int, Float>? = null

    fun reportProgress(id: Int, fraction: Float) {
        pendingProgress = id to fraction
        if (progressJob?.isActive == true) return
        progressJob = viewModelScope.launch {
            delay(700)
            flushProgress()
        }
    }

    private fun flushProgress() {
        pendingProgress?.let { (id, f) -> store.setProgress(id, f) }
        pendingProgress = null
    }

    // --- preferences ---

    fun chooseSort(v: String) { sort = v; settings.sort = v }
    fun chooseFilter(v: String) { filter = v; settings.filter = v }
    fun chooseTextSize(v: Int) { settings.textSize = v; textSize = settings.textSize }
    fun chooseTheme(v: String) { theme = v; settings.theme = v }
    fun chooseJustify(v: Boolean) { justify = v; settings.justify = v }
    fun choosePaged(v: Boolean) { paged = v; settings.paged = v }
    fun chooseLineSpacing(v: String) { lineSpacing = v; settings.lineSpacing = v }

    // --- derived ---

    fun visibleList(entries: List<Entry>): List<Entry> =
        SyncLogic.sort(SyncLogic.search(SyncLogic.filter(entries, filter), query), sort)

    /** The next queued item after `id` in the current view, wrapping to the top. */
    fun nextAfter(id: Int): Entry? {
        val list = visibleList(store.state.value.visible).filter { it.state == "queued" }
        val i = list.indexOfFirst { it.id == id }
        return if (i >= 0) list.drop(i + 1).firstOrNull() ?: list.take(i).firstOrNull()
        else list.firstOrNull { it.id != id }
    }

    private fun uuid() = UUID.randomUUID().toString()
    private fun now(): String = Instant.now().toString()
}
