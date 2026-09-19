package com.mangesh.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.mangesh.reader.ReaderApp
import com.mangesh.reader.data.Action
import com.mangesh.reader.data.Entry
import com.mangesh.reader.data.Refused
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the Compose screens to PNGs at the mocks' size (360×740), to set beside the theme
 * handoff. Opt-in:  ./gradlew testDebugUnitTest -Pscreenshots  → app/build/screenshots/
 * The reading page itself is a WebView, which Robolectric does not paint; its chrome is what shows.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h740dp-xhdpi")
class ScreenshotTest {

    @get:Rule val compose = createComposeRule()

    private fun entry(id: Int, title: String, note: String, project: String, minutes: Int, added: String, source: String = "",
                      progress: Float = 0f, version: Int = 1, htmlHash: String = "h", lastOpened: Long = 0L) =
        Entry(id, "$project/$id.md", version, title, project, source, note, minutes * 250, minutes, "prism", added, "queued",
              "", "", false, "h", false, progress, lastOpened, htmlHash)

    private fun vm(theme: String): QueueViewModel {
        val app = ApplicationProvider.getApplicationContext<ReaderApp>()
        app.store.setEntries(listOf(
            entry(5, "On rereading", "The second time through a book, the plot stops mattering. You already know who lives. What is left is the sentence.",
                  "research", 16, "2026-09-12", progress = 0.42f, lastOpened = 9L),
            entry(4, "Field notes, Dungeness", "Shingle all the way to the water, and the power station humming behind it like a fridge in another room.",
                  "clips", 22, "2026-09-09", source = "lrb.co.uk"),
            entry(3, "What the editor cut", "Three paragraphs about the weather, one about my father, and the only joke. She was right about the weather.",
                  "sia", 25, "2026-09-02", progress = 0.88f, version = 2),
            entry(2, "Kitchen ledger, August", "Tomatoes finally worth eating. Bread failed twice, then didn't. A note on salt that I should have written years ago.",
                  "research", 15, "2026-08-31", htmlHash = ""),
            entry(1, "Letters to a younger cook", "Taste before you season. Then taste again, because you will have forgotten what it was like the first time.",
                  "investing", 31, "2026-08-24"),
        ))
        app.store.syncSucceeded("5 entries")
        app.store.saveReport(5, "<div class=\"detail\"><h2>On rereading</h2><div class=\"report\"><p data-line=\"1\">…</p></div></div>", "h")
        return QueueViewModel(app).also { it.chooseTheme(theme) }
    }

    private fun shot(name: String, vm: QueueViewModel, tabs: Boolean = true, content: @Composable () -> Unit) {
        compose.setContent {
            ReaderTheme(vm) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().weight(1f)) { content() }
                        if (tabs) AppTabs(vm)
                    }
                }
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("build/screenshots/$name.png")
    }

    @Test fun queueLight() = vm("light").let { vm -> shot("queue-light", vm) { QueueScreen(vm) } }
    @Test fun queueDark() = vm("dark").let { vm -> shot("queue-dark", vm) { QueueScreen(vm) } }
    @Test fun queueSepia() = vm("sepia").let { vm -> shot("queue-sepia", vm) { QueueScreen(vm) } }

    @Test fun settingsLight() = vm("light").let { vm -> shot("settings-light", vm) { SettingsScreen(vm) } }

    @Test fun settingsNeedsAttentionDark() = vm("dark").let { vm ->
        val a = Action("u1", "comment", 5, "2026-09-18T10:00:00Z", line = 12, hash = "old", text = "This is the paragraph to quote in the talk.")
        vm.store.update { it.copy(refused = listOf(Refused(a, "file changed since this was rendered"))) }
        shot("settings-attention-dark", vm) { SettingsScreen(vm) }
    }

    @Test fun readerChromeLight() = vm("light").let { vm ->
        vm.open(5)
        shot("reader-chrome-light", vm, tabs = false) { ReaderScreen(vm, 5, ReaderHost()) }
    }

    @Test fun settingsSheetDark() = vm("dark").let { vm ->
        vm.open(5)
        shot("sheet-dark", vm, tabs = false) {
            ReaderScreen(vm, 5, ReaderHost())
            ReaderSheet(onDismiss = {}) { ReadingControls(vm, withSystemTheme = false) }
        }
    }

    @Test fun settingsSheetLight() = vm("light").let { vm ->
        vm.open(5)
        vm.chooseJustify(true)
        shot("sheet-light", vm, tabs = false) {
            ReaderScreen(vm, 5, ReaderHost())
            ReaderSheet(onDismiss = {}) { ReadingControls(vm, withSystemTheme = false) }
        }
    }

    /** The progress line mid-riffle: thumb, section ticks, and the label over the finger. */
    @Test fun scrubbingDark() = vm("dark").let { vm ->
        val sections = listOf(Section("The September cluster, paper by paper", 0.0952f), Section("Four operating modes", 0.3333f),
                              Section("Scorecard", 0.7619f), Section("Running the play", 0.8095f), Section("What would change this", 0.9524f))
        shot("scrubbing-dark", vm) {
            val p = LocalPalette.current
            Column(Modifier.fillMaxSize().background(p.bgColor), verticalArrangement = Arrangement.Bottom) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("↩ 3", style = Type.figure, color = p.accentColor, modifier = Modifier.padding(end = 12.dp))
                    Scrubber(progress = 0.1f, scrub = 0.3333f, sections = sections, pages = 22, onStart = {}, onScrub = {}, onEnd = {},
                             modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Text("33% · 7 min left", style = Type.figure, color = p.mutedColor, textAlign = TextAlign.End,
                         modifier = Modifier.widthIn(min = 116.dp))
                }
            }
        }
    }

    /** A riffle through the real reader screen: what the page is told, what the line shows, and the way back. */
    @Test fun riffleThroughTheReader() = vm("light").let { vm ->
        vm.open(5)
        val host = ReaderHost()
        compose.setContent { ReaderTheme(vm) { Surface(Modifier.fillMaxSize()) { ReaderScreen(vm, 5, host) } } }
        compose.waitForIdle()
        val web = host.web!!
        val sent = mutableListOf<String>()
        fun lastJs() = Shadows.shadowOf(web.view).lastEvaluatedJavascript.also { sent += it }
        compose.runOnUiThread {   // what the page would report once laid out: on page 3 of 22, five sections
            web.bridge!!.onPage(2, 22, 2f / 21, false)
            web.bridge!!.onSections("""[{"t":"The September cluster, paper by paper","f":0.0952},{"t":"Four operating modes","f":0.3333},""" +
                                    """{"t":"Scorecard","f":0.7619},{"t":"Running the play","f":0.8095}]""")
        }
        compose.waitForIdle()

        val line = compose.onNodeWithTag(SCRUBBER_TAG)
        line.performTouchInput { down(Offset(width * 0.1f, centerY)); moveBy(Offset(60f, 0f)); moveTo(Offset(width * 0.765f, centerY)) }
        compose.waitForIdle()
        assertEquals("scrubTo(0.7619);", lastJs())                     // snapped to where "Scorecard" starts
        compose.onNodeWithText("Scorecard").assertIsDisplayed()
        compose.onNodeWithText("17 / 22").assertIsDisplayed()
        captureScreenRoboImage("build/screenshots/reader-riffle-light.png")

        line.performTouchInput { up() }
        compose.waitForIdle()
        assertEquals("scrubTo(0.7619); scrubEnd();", lastJs())
        compose.onNodeWithText("17 / 22").assertDoesNotExist()
        compose.onNodeWithText("↩ 3").assertIsDisplayed()                // the page the reading was on

        compose.runOnUiThread { repeat(2) { web.bridge!!.onPage(17 + it, 22, (17f + it) / 21, true) } }
        compose.waitForIdle()
        compose.onNodeWithText("↩ 3").assertIsDisplayed()                // survives a look around
        compose.onNodeWithText("↩ 3").performClick()
        compose.waitForIdle()
        assertTrue(lastJs(), lastJs().startsWith("scrubStart(); scrubTo(0.095"))
        assertTrue(lastJs().endsWith("scrubEnd();"))
        compose.onNodeWithText("↩ 3").assertDoesNotExist()
    }

    /** …and it goes once the reader has settled into the new place. */
    @Test fun theWayBackExpiresAfterThreePageTurns() = vm("light").let { vm ->
        vm.open(5)
        val host = ReaderHost()
        compose.setContent { ReaderTheme(vm) { Surface(Modifier.fillMaxSize()) { ReaderScreen(vm, 5, host) } } }
        compose.waitForIdle()
        val bridge = host.web!!.bridge!!
        compose.runOnUiThread { bridge.onPage(2, 22, 2f / 21, false) }
        compose.waitForIdle()
        compose.onNodeWithTag(SCRUBBER_TAG).performTouchInput { down(Offset(width * 0.1f, centerY)); moveBy(Offset(60f, 0f)); moveTo(Offset(width * 0.5f, centerY)); up() }
        compose.waitForIdle()
        compose.onNodeWithText("↩ 3").assertIsDisplayed()
        compose.runOnUiThread { repeat(3) { host.web!!.bridge!!.onPage(12 + it, 22, (12f + it) / 21, true) } }
        compose.waitForIdle()
        compose.onNodeWithText("↩ 3").assertDoesNotExist()
    }

    @Test fun dropSheetLight() = vm("light").let { vm ->
        shot("drop-sheet-light", vm) {
            QueueScreen(vm)
            DropSheet("Letters to a younger cook", onDismiss = {}) {}
        }
    }
}
