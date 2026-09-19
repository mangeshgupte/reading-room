package com.mangesh.reader.data

/** The pure parts of syncing and of what the list shows. No I/O, so unit-testable on the JVM. */
object SyncLogic {

    /** Server list is truth for server fields; the phone's own fields survive by id.
     *  Entries the server no longer lists (superseded versions) disappear. */
    fun merge(local: List<Entry>, server: List<Entry>): List<Entry> {
        val byId = local.associateBy { it.id }
        return server.map { s ->
            byId[s.id]?.let { l -> s.copy(progress = l.progress, lastOpened = l.lastOpened, htmlHash = l.htmlHash) } ?: s
        }
    }

    /** Ids whose HTML must be fetched: never downloaded, or the file changed since. */
    fun planFetches(entries: List<Entry>): List<Int> =
        entries.filter { !it.missing && it.htmlHash != it.rawHash }.map { it.id }

    /** What the list shows between syncs: server state with pending done/drop applied. */
    fun overlay(entries: List<Entry>, outbox: List<Action>): List<Entry> {
        val pending = outbox.filter { it.type == "done" || it.type == "drop" }.associateBy { it.id }
        return entries.map { e ->
            val a = pending[e.id]
            if (a != null && e.state == "queued") {
                e.copy(state = if (a.type == "done") "done" else "dropped", closed = a.ts.take(10), reason = a.reason)
            } else e
        }
    }

    fun filter(entries: List<Entry>, filter: String): List<Entry> = when (filter) {
        "all" -> entries.filter { it.state == "queued" }
        "short" -> entries.filter { it.state == "queued" && it.minutes <= 10 }
        "web" -> entries.filter { it.state == "queued" && it.isClip }
        "done" -> entries.filter { it.state == "done" }
        "dropped" -> entries.filter { it.state == "dropped" }
        else -> entries.filter { it.state == "queued" && it.project == filter }
    }

    /** The search box: every word must appear somewhere in the title, note, project or source. */
    fun search(entries: List<Entry>, query: String): List<Entry> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return entries
        return entries.filter { e ->
            val hay = "${e.title}\n${e.note}\n${e.project}\n${e.source}".lowercase()
            words.all { it in hay }
        }
    }

    fun sort(entries: List<Entry>, sort: String): List<Entry> = when (sort) {
        "shortest" -> entries.sortedWith(compareBy<Entry> { it.minutes }.thenByDescending { it.id })
        "oldest" -> entries.sortedWith(compareBy<Entry> { it.added }.thenBy { it.id })
        else -> entries.sortedByDescending { it.id }
    }

    /** Project chips: projects present in the queue, most items first. */
    fun projects(entries: List<Entry>): List<String> =
        entries.filter { it.state == "queued" && !it.isClip }
            .groupingBy { it.project }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }

    /** The item to resume: the most recently opened queued entry that is part-read. */
    fun continueCandidate(entries: List<Entry>): Entry? =
        entries.filter { it.state == "queued" && it.started }.maxByOrNull { it.lastOpened }
}
