package com.mangesh.reader.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import kotlin.math.abs
import kotlin.math.roundToInt

/** Where a section heading starts, as the page reports it: 0 is the start of the document, 1 the end. */
data class Section(val title: String, val fraction: Float)

/** The pure parts of scrubbing. No Compose, so unit-testable on the JVM. */
object Scrub {
    /** The page's onSections() payload: [{t: title, f: fraction}, …]. Anything malformed is no sections. */
    fun parse(json: String): List<Section> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getJSONObject(it) }.map { Section(it.optString("t"), it.optDouble("f", 0.0).toFloat()) }
    } catch (e: Exception) { emptyList() }

    /** Soft snap: within `radius` of where a section starts, land on it. */
    fun snap(f: Float, sections: List<Section>, radius: Float): Float {
        val nearest = sections.minByOrNull { abs(it.fraction - f) } ?: return f
        return if (abs(nearest.fraction - f) <= radius) nearest.fraction else f
    }

    /** The section a place falls in: the last one that starts at or before it. */
    fun sectionAt(f: Float, sections: List<Section>): Section? = sections.lastOrNull { it.fraction <= f + 0.0001f }

    /** The same rounding the page uses, so the label and the page agree. */
    fun pageAt(f: Float, pages: Int): Int = if (pages <= 1) 0 else (f * (pages - 1)).roundToInt().coerceIn(0, pages - 1)
}

const val SCRUBBER_TAG = "scrubber"
private val TRACK_HEIGHT = 46.dp
private val SNAP_RADIUS = 7.dp

/**
 * The reader's progress line, which is also the page edge you thumb: drag along it to riffle through
 * the document. Hairline track, the accent as far as you have read, a tick where each section starts,
 * and while a finger is down a label over it naming the section and the page. Dragging is absolute —
 * the place follows the finger — and snaps softly to the ticks.
 *
 * `scrub` is the place under the finger while dragging, null otherwise; the caller owns it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Scrubber(
    progress: Float,
    scrub: Float?,
    sections: List<Section>,
    pages: Int?,   // paged mode: for "12 / 33"; null in scroll mode, which shows a percentage
    onStart: () -> Unit,
    onScrub: (Float) -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val haptics = LocalHapticFeedback.current
    val currentSections by rememberUpdatedState(sections)
    val start by rememberUpdatedState(onStart)
    val move by rememberUpdatedState(onScrub)
    val end by rememberUpdatedState(onEnd)
    var width by remember { mutableIntStateOf(0) }
    var left by remember { mutableIntStateOf(0) }       // the track's place in the window, to keep the label on screen
    var window by remember { mutableIntStateOf(0) }
    val shown = (scrub ?: progress).coerceIn(0f, 1f)

    Box(modifier.height(TRACK_HEIGHT).testTag(SCRUBBER_TAG).onSizeChanged { width = it.width }
            .onGloballyPositioned { left = it.positionInRoot().x.roundToInt(); window = it.findRootCoordinates().size.width }
            .systemGestureExclusion()   // a drag that starts at the left end is not the system's back swipe
            .pointerInput(Unit) {
                var lastTick = -1f
                fun place(x: Float) {
                    val raw = (x / size.width).coerceIn(0f, 1f)
                    val f = Scrub.snap(raw, currentSections, SNAP_RADIUS.toPx() / size.width)
                    if (f != raw && f != lastTick) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    lastTick = if (f != raw) f else -1f
                    move(f)
                }
                detectHorizontalDragGestures(
                    onDragStart = { at -> lastTick = -1f; start(); place(at.x) },
                    onDragEnd = { end() },
                    onDragCancel = { end() },
                    onHorizontalDrag = { change, _ -> change.consume(); place(change.position.x) },
                )
            }
            .drawBehind {
                val y = size.height / 2
                val hair = 1.dp.toPx()
                drawLine(p.ruleColor, Offset(0f, y), Offset(size.width, y), hair)
                drawLine(p.accentColor, Offset(0f, y), Offset(size.width * shown, y), hair)
                val tick = 3.5.dp.toPx()
                for (s in sections) {
                    if (s.fraction <= 0.004f || s.fraction >= 0.996f) continue
                    val x = size.width * s.fraction
                    drawLine(if (s.fraction <= shown) p.accentColor else p.ruleColor, Offset(x, y - tick), Offset(x, y + tick), hair)
                }
                if (scrub != null) {   // the thumb, only while a finger is on the line
                    val x = size.width * shown
                    drawLine(p.accentColor, Offset(x, y - 8.dp.toPx()), Offset(x, y + 8.dp.toPx()), 2.dp.toPx())
                }
            }) {
        if (scrub != null) {
            val section = Scrub.sectionAt(scrub, sections)
            val figure = if (pages != null) "${Scrub.pageAt(scrub, pages) + 1} / $pages" else "${(scrub * 100).roundToInt()}%"
            // Floats above the line, centred on the finger. It takes no room of its own, so it may overhang
            // the track; what holds it is the screen, with the footer's own margin.
            Column(Modifier
                       .layout { measurable, _ ->
                           val label = measurable.measure(Constraints())
                           layout(0, 0) {
                               val margin = 16.dp.roundToPx()
                               val lo = margin - left
                               val hi = maxOf(lo, window - margin - left - label.width)
                               label.place((width * scrub - label.width / 2f).roundToInt().coerceIn(lo, hi), -label.height - 6.dp.roundToPx())
                           }
                       }
                       .widthIn(max = 248.dp)
                       .background(p.surfaceColor, CONTROL_SHAPE).border(1.dp, p.ruleColor, CONTROL_SHAPE)
                       .padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (section != null) Text(section.title, style = Type.scrubTitle, color = p.fgColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(figure, style = Type.figure, color = p.mutedColor)
            }
        }
    }
}
