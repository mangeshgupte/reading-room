package com.mangesh.reader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * How the page looks: text size, line spacing, theme, justification, pages. The reader's
 * settings sheet and the Reading section of Settings are the same controls; every change
 * applies live to the page beneath.
 *
 * The sheet offers Light / Sepia / Dark and rings whichever is in force, so following the
 * system shows as the theme it resolved to; Settings adds System, the way back to following.
 */
@Composable
fun ReadingControls(vm: QueueViewModel, withSystemTheme: Boolean, spacing: Dp = 22.dp) {
    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        LabeledControl("Text size") {
            Segmented(TypeScale.sizes.indices.toList(), vm.textSize, { vm.chooseTextSize(it) }) { step, color ->
                Text("A", style = Type.segment.copy(fontSize = TypeScale.size(step).glyph.sp), color = color)
            }
        }
        LabeledControl("Line spacing") {
            TextSegmented(TypeScale.spacings.map { it.key to it.label }, vm.lineSpacing, { vm.chooseLineSpacing(it) })
        }
        LabeledControl("Theme") {
            val themes = Palettes.all.map { it.key to it.name }
            if (withSystemTheme) TextSegmented(listOf("system" to "System") + themes, vm.theme, { vm.chooseTheme(it) })
            else TextSegmented(themes, currentPalette(vm).key, { vm.chooseTheme(it) })
        }
        Column {
            Hairline()
            Spacer(Modifier.height(12.dp))
            ToggleRow("Justify text", vm.justify, { vm.chooseJustify(it) })
            ToggleRow("Pages instead of scrolling", vm.paged, { vm.choosePaged(it) })
        }
    }
}
