package com.mangesh.reader.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// The reader theme's building blocks. Hairlines instead of cards, 6dp corners, no shadows;
// the accent is a stroke, an underline or a small figure, never a fill.

val CONTROL_SHAPE = RoundedCornerShape(6.dp)

/** 12px tracked uppercase label: section names, counts, dates. */
@Composable
fun MetaLabel(text: String, modifier: Modifier = Modifier, color: Color = LocalPalette.current.mutedColor) {
    Text(text.uppercase(), style = Type.meta, color = color, modifier = modifier)
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(LocalPalette.current.ruleColor))
}

/** Pressed state is an accent tint, never the platform ripple. */
fun Modifier.pressTint(enabled: Boolean = true, role: Role? = null, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val tint = LocalPalette.current.pressTint
    background(if (pressed && enabled) tint else Color.Transparent)
        .clickable(interactionSource = source, indication = null, enabled = enabled, role = role, onClick = onClick)
}

/** 1px hairline track with the accent marking how far along. */
@Composable
fun ProgressHairline(progress: Float, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Box(modifier.height(1.dp).background(p.ruleColor)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(1.dp).background(p.accentColor))
    }
}

/**
 * Outlined segments: hairline around and between, the selected one ringed in the accent with
 * accent text. No fill.
 */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (option: T, color: Color) -> Unit,
) {
    val p = LocalPalette.current
    // the ring sits inside the outer hairline, as an inset shadow does in the reference
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min).border(1.dp, p.ruleColor, CONTROL_SHAPE).padding(1.dp)
            .clip(RoundedCornerShape(5.dp))) {
        options.forEachIndexed { i, option ->
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(p.ruleColor))
            val on = option == selected
            Box(Modifier.weight(1f).fillMaxHeight()
                    .then(if (on) Modifier.border(1.dp, p.accentColor) else Modifier)
                    .pressTint(role = Role.RadioButton) { onSelect(option) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center) {
                label(option, if (on) p.accentColor else p.mutedColor)
            }
        }
    }
}

/** Segments whose labels are plain words. */
@Composable
fun TextSegmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Segmented(options.map { it.first }, selected, onSelect, modifier) { key, color ->
        Text(options.first { it.first == key }.second, style = Type.segment, color = color, maxLines = 1)
    }
}

/** A meta label over a control. */
@Composable
fun LabeledControl(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MetaLabel(label)
        content()
    }
}

/** 44×26 outlined toggle; the 18dp knob is muted when off and accent when on. */
@Composable
fun OutlineToggle(checked: Boolean, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val x by animateDpAsState(if (checked) 22.dp else 4.dp, label = "knob")
    Box(modifier.size(44.dp, 26.dp).border(1.dp, p.ruleColor, RoundedCornerShape(13.dp))) {
        Box(Modifier.offset(x = x, y = 4.dp).size(18.dp).background(if (checked) p.accentColor else p.mutedColor, CircleShape))
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                                           role = Role.Switch) { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Type.body, color = LocalPalette.current.fgColor, modifier = Modifier.weight(1f))
        OutlineToggle(checked)
    }
}

/** A button is an outline. The primary one takes the accent as its stroke and text. */
@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true) {
    val p = LocalPalette.current
    val stroke = if (primary && enabled) p.accentColor else p.ruleColor
    val ink = when {
        !enabled -> p.mutedColor.copy(alpha = 0.5f)
        primary -> p.accentColor
        else -> p.fgColor
    }
    Box(modifier.clip(CONTROL_SHAPE).border(1.dp, stroke, CONTROL_SHAPE)
            .pressTint(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center) {
        Text(text, style = Type.segment, color = ink, maxLines = 1)
    }
}

/** A quiet text action (Retry, Dismiss, Settings): underlined in the accent. */
@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Text(text, style = Type.segment, color = p.fgColor,
         modifier = modifier.pressTint(role = Role.Button, onClick = onClick).padding(vertical = 8.dp)
             .drawBehind {
                 val y = size.height - 1.dp.toPx()
                 drawLine(p.accentColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
             })
}

/** Text field: 1px hairline, 6dp corners; the focused stroke and the caret are the accent. */
@Composable
fun HairlineField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    textStyle: TextStyle = Type.body,
) {
    val p = LocalPalette.current
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    BasicTextField(
        value = value, onValueChange = onValueChange, singleLine = singleLine, minLines = minLines,
        textStyle = textStyle.copy(color = p.fgColor), cursorBrush = SolidColor(p.accentColor),
        visualTransformation = visualTransformation, keyboardOptions = keyboardOptions, interactionSource = source,
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().border(1.dp, if (focused) p.accentColor else p.ruleColor, CONTROL_SHAPE)
                    .padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (value.isEmpty()) Text(placeholder, style = textStyle, color = p.mutedColor, maxLines = 1)
                inner()
            }
        },
    )
}

private val SHEET_RADIUS = 24.dp

/**
 * Bottom sheet in the theme: sheet surface, 24dp top corners, a hairline along the top edge,
 * 36×3 grab handle, no shadow. What is beneath dims to 35%.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSheet(onDismiss: () -> Unit, spacing: Dp = 22.dp, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),   // open to its full height, as drawn
        shape = RoundedCornerShape(topStart = SHEET_RADIUS, topEnd = SHEET_RADIUS),
        containerColor = p.surfaceColor,
        contentColor = p.fgColor,
        tonalElevation = 0.dp,
        scrimColor = p.bgColor.copy(alpha = 0.65f),
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().drawBehind {
            // border-top only, following the corners
            val r = SHEET_RADIUS.toPx()
            val w = 1.dp.toPx()
            val path = Path().apply {
                moveTo(w / 2, r)
                arcTo(Rect(w / 2, w / 2, 2 * r, 2 * r), 180f, 90f, false)
                lineTo(size.width - r, w / 2)
                arcTo(Rect(size.width - 2 * r, w / 2, size.width - w / 2, 2 * r), 270f, 90f, false)
            }
            drawPath(path, p.ruleColor, style = Stroke(w))
        }.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(spacing)) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(36.dp, 3.dp).background(p.ruleColor, RoundedCornerShape(2.dp)))
            content()
        }
    }
}

/** Dropdown in the theme: sheet surface, hairline border, 6dp corners, no shadow. */
@Composable
fun ReaderMenu(expanded: Boolean, onDismiss: () -> Unit, items: List<Pair<String, () -> Unit>>, selected: String? = null) {
    val p = LocalPalette.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, shape = CONTROL_SHAPE, containerColor = p.surfaceColor,
                 tonalElevation = 0.dp, shadowElevation = 0.dp, border = BorderStroke(1.dp, p.ruleColor)) {
        for ((label, action) in items) {
            DropdownMenuItem(text = { Text(label, style = Type.body, color = if (label == selected) p.accentColor else p.fgColor) },
                             onClick = { onDismiss(); action() })
        }
    }
}

/** Snackbar in the theme: outlined on the sheet surface; the action is accent text. */
@Composable
fun ReaderSnackbar(data: SnackbarData) {
    val p = LocalPalette.current
    Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp).fillMaxWidth().clip(CONTROL_SHAPE).background(p.surfaceColor)
            .border(1.dp, p.ruleColor, CONTROL_SHAPE).padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(data.visuals.message, style = Type.body, color = p.fgColor, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
        data.visuals.actionLabel?.let { label ->
            Text(label, style = Type.body, color = p.accentColor,
                 modifier = Modifier.pressTint(role = Role.Button) { data.performAction() }.padding(horizontal = 14.dp, vertical = 12.dp))
        }
    }
}
