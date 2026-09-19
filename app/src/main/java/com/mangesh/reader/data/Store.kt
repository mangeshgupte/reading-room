package com.mangesh.reader.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Everything the phone knows, as plain JSON files under the app's files dir:
 * index.json (entries + sync state), outbox.json (pending actions),
 * refused.json (actions the Mac turned down), reports/<id>.html, assets/<path>.
 * Small enough that every change rewrites the file; no database.
 */
class Store(private val dir: File) {

    data class State(
        val entries: List<Entry> = emptyList(),
        val outbox: List<Action> = emptyList(),
        val refused: List<Refused> = emptyList(),
        val lastSync: Long = 0L,        // epoch ms of the last successful sync
        val lastSyncNote: String = "",
        val lastError: String = "",     // why the last attempt failed; "" if it succeeded
        val everSynced: Boolean = false,
    ) {
        /** Entries as the list should show them: server state overlaid with the outbox. */
        val visible: List<Entry> get() = SyncLogic.overlay(entries, outbox)
    }

    private val indexFile = File(dir, "index.json")
    private val outboxFile = File(dir, "outbox.json")
    private val refusedFile = File(dir, "refused.json")
    private val reportsDir = File(dir, "reports")
    private val assetsDir = File(dir, "assets")

    private val _state: MutableStateFlow<State>
    val state: StateFlow<State> get() = _state

    init {
        dir.mkdirs(); reportsDir.mkdirs(); assetsDir.mkdirs()
        _state = MutableStateFlow(load())
    }

    private fun load(): State {
        val index = readJson(indexFile)
        val entries = index?.optJSONArray("entries")?.let { arr ->
            (0 until arr.length()).map { Entry.fromJson(arr.getJSONObject(it)) }
        } ?: emptyList()
        val outbox = readArray(outboxFile).map { Action.fromJson(it) }
        val refused = readArray(refusedFile).map { Refused.fromJson(it) }
        return State(
            entries = entries, outbox = outbox, refused = refused,
            lastSync = index?.optLong("lastSync", 0L) ?: 0L,
            lastSyncNote = index?.str("lastSyncNote") ?: "",
            lastError = index?.str("lastError") ?: "",
            everSynced = index?.optBoolean("everSynced") ?: false,
        )
    }

    private fun readJson(f: File): JSONObject? =
        try { if (f.isFile) JSONObject(f.readText()) else null } catch (e: Exception) { null }

    private fun readArray(f: File): List<JSONObject> = try {
        if (!f.isFile) emptyList() else JSONArray(f.readText()).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
    } catch (e: Exception) { emptyList() }

    private fun persist(s: State) {
        val index = JSONObject().apply {
            put("entries", JSONArray(s.entries.map { it.toJson() }))
            put("lastSync", s.lastSync); put("lastSyncNote", s.lastSyncNote)
            put("lastError", s.lastError); put("everSynced", s.everSynced)
        }
        writeAtomic(indexFile, index.toString())
        writeAtomic(outboxFile, JSONArray(s.outbox.map { it.toJson() }).toString())
        writeAtomic(refusedFile, JSONArray(s.refused.map { it.toJson() }).toString())
    }

    private fun writeAtomic(f: File, text: String) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text)
        tmp.renameTo(f)
    }

    @Synchronized
    fun update(block: (State) -> State) {
        val next = block(_state.value)
        _state.value = next
        persist(next)
    }

    // --- entries ---

    fun setEntries(entries: List<Entry>) = update { it.copy(entries = entries) }

    fun updateEntry(id: Int, block: (Entry) -> Entry) =
        update { s -> s.copy(entries = s.entries.map { if (it.id == id) block(it) else it }) }

    fun setProgress(id: Int, fraction: Float) = updateEntry(id) { it.copy(progress = fraction.coerceIn(0f, 1f)) }

    fun markOpened(id: Int) = updateEntry(id) { it.copy(lastOpened = System.currentTimeMillis()) }

    fun syncSucceeded(note: String) =
        update { it.copy(lastSync = System.currentTimeMillis(), lastSyncNote = note, lastError = "", everSynced = true) }

    fun syncFailed(error: String) = update { it.copy(lastError = error) }

    // --- reports and assets ---

    fun reportHtml(id: Int): String? = File(reportsDir, "$id.html").takeIf { it.isFile }?.readText()

    fun saveReport(id: Int, html: String, hash: String) {
        writeAtomic(File(reportsDir, "$id.html"), html)
        updateEntry(id) { it.copy(htmlHash = hash) }
    }

    fun assetFile(path: String): File? {
        if (path.split('/').any { it == ".." || it.isEmpty() }) return null
        return File(assetsDir, path)
    }

    fun hasAsset(path: String): Boolean = assetFile(path)?.isFile == true

    fun saveAsset(path: String, bytes: ByteArray) {
        val f = assetFile(path) ?: return
        f.parentFile?.mkdirs()
        f.writeBytes(bytes)
    }

    fun cacheBytes(): Long = (reportsDir.walkTopDown() + assetsDir.walkTopDown()).filter { it.isFile }.sumOf { it.length() }

    fun clearCache() {
        reportsDir.deleteRecursively(); assetsDir.deleteRecursively()
        reportsDir.mkdirs(); assetsDir.mkdirs()
        update { s -> s.copy(entries = s.entries.map { it.copy(htmlHash = "") }) }
    }

    // --- outbox ---

    fun enqueue(action: Action) = update { it.copy(outbox = it.outbox + action) }

    /** Undo: drop a pending action before it reaches the Mac. */
    fun removePending(uuid: String) = update { s -> s.copy(outbox = s.outbox.filter { it.uuid != uuid }) }

    /** Apply the Mac's verdicts: acked actions leave the outbox, refused ones move aside with their reason. */
    fun ackActions(results: List<ActionResult>) = update { s ->
        val byUuid = results.associateBy { it.uuid }
        val stillPending = s.outbox.filter { byUuid[it.uuid] == null }
        val newlyRefused = s.outbox.mapNotNull { a -> byUuid[a.uuid]?.takeIf { !it.ok }?.let { Refused(a, it.message) } }
        s.copy(outbox = stillPending, refused = s.refused + newlyRefused)
    }

    fun retryRefused(uuid: String) = update { s ->
        val r = s.refused.firstOrNull { it.action.uuid == uuid } ?: return@update s
        s.copy(refused = s.refused - r, outbox = s.outbox + r.action)
    }

    fun dismissRefused(uuid: String) = update { s -> s.copy(refused = s.refused.filter { it.action.uuid != uuid }) }

    /** Re-anchor a refused comment to a fresh line/hash and send it again. */
    fun replaceRefusedComment(uuid: String, line: Int, hash: String) = update { s ->
        val r = s.refused.firstOrNull { it.action.uuid == uuid } ?: return@update s
        s.copy(refused = s.refused - r, outbox = s.outbox + r.action.copy(line = line, hash = hash))
    }

    fun pendingFor(id: Int): List<Action> = _state.value.outbox.filter { it.id == id }
    fun refusedFor(id: Int): List<Refused> = _state.value.refused.filter { it.action.id == id }
}
