package com.mangesh.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncLogicTest {

    private fun entry(id: Int, state: String = "queued", minutes: Int = 5, project: String = "research",
                      source: String = "", added: String = "2026-09-0$id", rawHash: String = "h$id",
                      htmlHash: String = "", progress: Float = 0f, lastOpened: Long = 0L, missing: Boolean = false) =
        Entry(id, "$project/$id.md", 1, "T$id", project, source, "", minutes * 250, minutes, "test", added, state,
              "", "", false, rawHash, missing, progress, lastOpened, htmlHash)

    @Test
    fun mergeKeepsLocalFieldsAndDropsGoneEntries() {
        val local = listOf(entry(1, progress = 0.4f, htmlHash = "h1", lastOpened = 9L), entry(2))
        val server = listOf(entry(1, rawHash = "h1b", minutes = 9), entry(3))
        val merged = SyncLogic.merge(local, server)
        assertEquals(listOf(1, 3), merged.map { it.id })
        assertEquals(0.4f, merged[0].progress)
        assertEquals("h1", merged[0].htmlHash)
        assertEquals("h1b", merged[0].rawHash)
        assertEquals(9, merged[0].minutes)
        assertEquals(9L, merged[0].lastOpened)
    }

    @Test
    fun planFetchesNewAndChangedButNotMissing() {
        val entries = listOf(entry(1, htmlHash = "h1"), entry(2), entry(3, htmlHash = "old"), entry(4, missing = true))
        assertEquals(listOf(2, 3), SyncLogic.planFetches(entries))
    }

    @Test
    fun overlayAppliesPendingDoneAndDrop() {
        val entries = listOf(entry(1), entry(2), entry(3, state = "done"))
        val outbox = listOf(
            Action("a", "done", 1, "2026-09-17T10:00:00Z"),
            Action("b", "drop", 2, "2026-09-17T10:00:00Z", reason = "meh"),
            Action("c", "comment", 3, "2026-09-17T10:00:00Z", line = 3, hash = "x", text = "hi"),
        )
        val shown = SyncLogic.overlay(entries, outbox)
        assertEquals("done", shown[0].state)
        assertEquals("2026-09-17", shown[0].closed)
        assertEquals("dropped", shown[1].state)
        assertEquals("meh", shown[1].reason)
        assertEquals("done", shown[2].state)
    }

    @Test
    fun filterAndSort() {
        val entries = listOf(
            entry(1, minutes = 3), entry(2, minutes = 30, project = "investing"),
            entry(3, minutes = 8, source = "example.org"), entry(4, state = "done"), entry(5, state = "dropped"),
        )
        assertEquals(listOf(1, 2, 3), SyncLogic.filter(entries, "all").map { it.id })
        assertEquals(listOf(1, 3), SyncLogic.filter(entries, "short").map { it.id })
        assertEquals(listOf(3), SyncLogic.filter(entries, "web").map { it.id })
        assertEquals(listOf(4), SyncLogic.filter(entries, "done").map { it.id })
        assertEquals(listOf(5), SyncLogic.filter(entries, "dropped").map { it.id })
        assertEquals(listOf(2), SyncLogic.filter(entries, "investing").map { it.id })
        val q = SyncLogic.filter(entries, "all")
        assertEquals(listOf(3, 2, 1), SyncLogic.sort(q, "newest").map { it.id })
        assertEquals(listOf(1, 3, 2), SyncLogic.sort(q, "shortest").map { it.id })
        assertEquals(listOf(1, 2, 3), SyncLogic.sort(q, "oldest").map { it.id })
        assertEquals(listOf("investing", "research"), SyncLogic.projects(entries))
    }

    @Test
    fun searchMatchesEveryWordAcrossTitleNoteProjectAndSource() {
        val entries = listOf(
            entry(1).copy(title = "On rereading", note = "Marginalia comes from second readings"),
            entry(2, project = "sia").copy(title = "Thesis engine"),
            entry(3, source = "theamericanscholar.org").copy(title = "Solitude and leadership"),
        )
        assertEquals(listOf(1, 2, 3), SyncLogic.search(entries, "  ").map { it.id })
        assertEquals(listOf(1), SyncLogic.search(entries, "MARGINALIA").map { it.id })
        assertEquals(listOf(1), SyncLogic.search(entries, "second rereading").map { it.id })
        assertEquals(listOf(2), SyncLogic.search(entries, "sia").map { it.id })
        assertEquals(listOf(3), SyncLogic.search(entries, "scholar").map { it.id })
        assertEquals(emptyList<Int>(), SyncLogic.search(entries, "rereading thesis").map { it.id })
    }

    @Test
    fun continueCandidateIsMostRecentPartReadQueuedItem() {
        val entries = listOf(
            entry(1, progress = 0.5f, lastOpened = 5L),
            entry(2, progress = 0.3f, lastOpened = 9L),
            entry(3, progress = 0.99f, lastOpened = 20L),
            entry(4, progress = 0.5f, lastOpened = 30L, state = "done"),
        )
        assertEquals(2, SyncLogic.continueCandidate(entries)?.id)
        assertNull(SyncLogic.continueCandidate(listOf(entry(1))))
    }
}
