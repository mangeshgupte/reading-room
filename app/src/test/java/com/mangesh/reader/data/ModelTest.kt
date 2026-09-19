package com.mangesh.reader.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ModelTest {

    @Test
    fun entryFromServerHandlesNullsAndRoundTrips() {
        val o = JSONObject("""{"id": 64, "path": "clips/a.md", "version": 1, "title": "A", "project": "clips",
            "source": "example.org", "note": "why", "words": 3817, "minutes": 15, "by": "clip",
            "added": "2026-09-17", "state": "queued", "closed": null, "reason": "", "sent": false,
            "raw_hash": "abc", "missing": false}""")
        val e = Entry.fromServer(o)
        assertEquals("", e.closed)
        assertEquals("example.org", e.where)
        assertFalse(e.downloaded)
        val back = Entry.fromJson(e.copy(progress = 0.25f, htmlHash = "abc", lastOpened = 7L).toJson())
        assertEquals(0.25f, back.progress)
        assertEquals("abc", back.htmlHash)
        assertEquals(7L, back.lastOpened)
        assertEquals(11, back.minutesLeft)
    }

    @Test
    fun actionJsonCarriesCommentFieldsOnlyForComments() {
        val done = Action("u1", "done", 3, "t").toJson()
        assertFalse(done.has("line"))
        val c = Action("u2", "comment", 3, "t", line = 12, hash = "h", text = "hi")
        val back = Action.fromJson(c.toJson())
        assertEquals(c, back)
        val r = Refused.fromJson(Refused(c, "changed").toJson())
        assertEquals("changed", r.error)
        assertEquals(c, r.action)
    }
}
