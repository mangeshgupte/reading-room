package com.mangesh.reader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrubTest {

    private val sections = listOf(Section("One", 0.1f), Section("Two", 0.3333f), Section("Three", 0.7619f))

    @Test
    fun parsesThePagesPayloadAndShrugsAtGarbage() {
        assertEquals(listOf(Section("The September cluster", 0.0952f), Section("Scorecard", 0.7619f)),
                     Scrub.parse("""[{"t":"The September cluster","f":0.0952},{"t":"Scorecard","f":0.7619}]"""))
        assertEquals(emptyList<Section>(), Scrub.parse("[]"))
        assertEquals(emptyList<Section>(), Scrub.parse("not json"))
    }

    @Test
    fun snapsOnlyWithinTheRadiusAndToTheNearest() {
        assertEquals(0.3333f, Scrub.snap(0.34f, sections, 0.02f))
        assertEquals(0.3333f, Scrub.snap(0.32f, sections, 0.02f))
        assertEquals(0.5f, Scrub.snap(0.5f, sections, 0.02f))
        assertEquals(0.36f, Scrub.snap(0.36f, sections, 0.02f))
        assertEquals(0.2f, Scrub.snap(0.2f, emptyList(), 0.5f))
    }

    @Test
    fun aPlaceBelongsToTheLastSectionThatHasStarted() {
        assertNull(Scrub.sectionAt(0.05f, sections))   // the opening, before any heading
        assertEquals("One", Scrub.sectionAt(0.1f, sections)?.title)
        assertEquals("Two", Scrub.sectionAt(0.5f, sections)?.title)
        assertEquals("Three", Scrub.sectionAt(1f, sections)?.title)
    }

    @Test
    fun pagesRoundTheWayThePageDoes() {
        assertEquals(0, Scrub.pageAt(0f, 22))
        assertEquals(21, Scrub.pageAt(1f, 22))
        assertEquals(16, Scrub.pageAt(0.7619f, 22))   // Math.round(0.7619 * 21) in reader.js
        assertEquals(11, Scrub.pageAt(0.5f, 22))
        assertEquals(0, Scrub.pageAt(0.7f, 1))
        assertEquals(0, Scrub.pageAt(0.7f, 0))
    }
}
