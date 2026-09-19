package com.mangesh.reader.data

/**
 * One sync: flush the outbox, pull the queue, fetch changed reports. Runs only
 * on app open and pull-to-refresh. The Mac being unreachable is the normal
 * case: the first request decides, and nothing local changes on failure.
 */
class Sync(private val store: Store, private val api: Api) {

    data class Result(val ok: Boolean, val message: String)

    suspend fun run(progress: (String) -> Unit = {}): Result {
        var applied = 0
        var refused = 0
        val outbox = store.state.value.outbox
        if (outbox.isNotEmpty()) {
            progress("sending ${outbox.size} action${if (outbox.size == 1) "" else "s"}…")
            val results = try { api.actions(outbox) } catch (e: Api.ApiException) { return fail(e) }
            store.ackActions(results)
            applied = results.count { it.ok }
            refused = results.count { !it.ok }
        }

        progress("checking the queue…")
        val server = try { api.queue() } catch (e: Api.ApiException) { return fail(e) }
        val merged = SyncLogic.merge(store.state.value.entries, server)
        store.setEntries(merged)

        val toFetch = SyncLogic.planFetches(merged)
        var downloaded = 0
        var failed = 0
        toFetch.forEachIndexed { i, id ->
            progress("downloading ${i + 1}/${toFetch.size}…")
            try {
                val r = api.report(id)
                for (path in r.assets) {
                    if (!store.hasAsset(path)) {
                        try { store.saveAsset(path, api.asset(path)) } catch (_: Api.ApiException) { /* image stays remote */ }
                    }
                }
                store.saveReport(id, r.html, r.rawHash)
                downloaded++
            } catch (e: Api.ApiException) {
                val f = e.failure
                if (f is Api.Failure.Http && f.code == 410) store.updateEntry(id) { it.copy(missing = true) }
                else failed++
            }
        }

        val queued = merged.count { it.state == "queued" }
        val note = buildString {
            append("$queued queued")
            if (downloaded > 0) append(" · $downloaded downloaded")
            if (failed > 0) append(" · $failed failed")
            if (applied > 0) append(" · $applied applied")
            if (refused > 0) append(" · $refused refused")
        }
        store.syncSucceeded(note)
        return Result(failed == 0, note)
    }

    private fun fail(e: Api.ApiException): Result {
        store.syncFailed(e.failure.detail)
        return Result(false, e.failure.detail)
    }
}
