package com.spideranim.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.spideranim.editor.ColorTarget
import com.spideranim.editor.EditorState
import com.spideranim.editor.Tool
import com.spideranim.editor.ToolGroups
import com.spideranim.ui.icons.Ic
import com.spideranim.ui.theme.Ui
import kotlin.math.roundToInt

fun Tool.icon() = when (this) {
    Tool.Select -> Ic.Select
    Tool.Transform -> Ic.Transform
    Tool.Brush -> Ic.Brush
    Tool.Pencil -> Ic.Pencil
    Tool.Pen -> Ic.Pen
    Tool.Line -> Ic.Line
    Tool.Rect -> Ic.Rect
    Tool.Oval -> Ic.Oval
    Tool.Bucket -> Ic.Bucket
    Tool.Eraser -> Ic.Eraser
    Tool.Dropper -> Ic.Dropper
    Tool.Hand -> Ic.Hand
    Tool.Zoom -> Ic.Zoom
}

private val panelShape = RoundedCornerShape(18.dp)

fun Modifier.floatingPanel() = this
    .clip(panelShape)
    .background(Ui.Bg1)
    .border(1.dp, Ui.Line, panelShape)

/** Vertical tool rail for tablets, grouped like Animate's Tools panel. */
@Composable
fun ToolRail(state: EditorState, modifier: Modifier = Modifier) {
    Column(
        modifier.floatingPanel().verticalScroll(rememberScrollState()).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ToolGroups.forEachIndexed { gi, group ->
            if (gi > 0) Box(Modifier.padding(vertical = 5.dp).width(22.dp).height(1.dp).background(Ui.Line))
            group.forEach { t ->
                IconBtn(t.icon(), { state.selectTool(t) }, selected = state.tool == t, size = 42.dp, description = t.label)
            }
        }
        Box(Modifier.padding(vertical = 6.dp).width(22.dp).height(1.dp).background(Ui.Line))
        ColorDuo(state)
    }
}

/** Horizontal tool strip for phones. */
@Composable
fun ToolStrip(state: EditorState, modifier: Modifier = Modifier) {
    Row(
        modifier.floatingPanel().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ColorDuo(state)
        ToolGroups.forEach { group ->
            Box(Modifier.padding(horizontal = 5.dp).width(1.dp).height(22.dp).background(Ui.Line))
            group.forEach { t ->
                IconBtn(t.icon(), { state.selectTool(t) }, selected = state.tool == t, size = 40.dp, description = t.label)
            }
        }
    }
}

/** Animate's overlapping stroke/fill swatches. Tapping one opens the colour popover for it. */
@Composable
fun ColorDuo(state: EditorState) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.size(42.dp)) {
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(24.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color(state.fillColor))
                .border(if (state.colorTarget == ColorTarget.Fill) 2.dp else 1.dp, if (state.colorTarget == ColorTarget.Fill) Ui.Accent else Ui.Line, RoundedCornerShape(7.dp))
                .clickable {
                    state.colorTarget = ColorTarget.Fill
                    open = true
                },
        )
        Box(
            Modifier
                .align(Alignment.TopStart)
                .size(24.dp)
                .clip(CircleShape)
                .background(Ui.Bg1)
                .border(if (state.colorTarget == ColorTarget.Stroke) 2.dp else 1.dp, if (state.colorTarget == ColorTarget.Stroke) Ui.Accent else Ui.Line, CircleShape)
                .padding(4.dp)
                .clip(CircleShape)
                .background(Color(state.strokeColor))
                .clickable {
                    state.colorTarget = ColorTarget.Stroke
                    open = true
                },
        )
        if (open) {
            Popup(
                popupPositionProvider = BesideAnchor,
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier
                        .width(260.dp)
                        .floatingPanel()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Segmented(
                        listOf("Stroke" to ColorTarget.Stroke, "Fill" to ColorTarget.Fill),
                        state.colorTarget,
                        { state.colorTarget = it },
                    )
                    if (state.colorTarget == ColorTarget.Stroke) {
                        ColorPicker(state.strokeColor, { state.strokeColor = it })
                    } else {
                        ColorPicker(state.fillColor, { state.fillColor = it })
                    }
                }
            }
        }
    }
}

/** Opens to the right of the anchor, or above it when there is no room (phone tool strip). */
private object BesideAnchor : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val gap = 16
        var x = anchorBounds.right + gap
        if (x + popupContentSize.width > windowSize.width) x = anchorBounds.left
        x = x.coerceAtMost(windowSize.width - popupContentSize.width - gap).coerceAtLeast(gap)
        var y = anchorBounds.top
        if (y + popupContentSize.height > windowSize.height) y = anchorBounds.top - popupContentSize.height - gap
        y = y.coerceAtMost(windowSize.height - popupContentSize.height).coerceAtLeast(gap)
        return IntOffset(x, y)
    }
}

/** Contextual options for the active tool, like Animate's Properties-for-tool row. */
@Composable
fun ToolOptions(state: EditorState, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Ui.Bg1.copy(alpha = 0.97f))
            .border(1.dp, Ui.Line, RoundedCornerShape(14.dp))
            .horizontalScroll(rememberScrollState())
            .height(44.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(state.tool.label, style = Ui.title.copy(fontSize = 13.sp))
        Text(state.tool.key, style = Ui.num.copy(fontSize = 10.sp, color = Ui.TextFaint))
        VHairline()
        when (state.tool) {
            Tool.Brush -> {
                OptSlider("Size", state.brushSize, 1f..120f, { state.brushSize = it }) { "${it.roundToInt()}" }
                OptSlider("Smooth", state.smoothing, 0f..1f, { state.smoothing = it }) { "${(it * 100).roundToInt()}%" }
                ToggleChip("Pressure", state.pressure, { state.pressure = !state.pressure })
            }
            Tool.Pencil -> {
                OptSlider("Size", state.pencilSize, 1f..40f, { state.pencilSize = it }) { "${it.roundToInt()}" }
                OptSlider("Smooth", state.smoothing, 0f..1f, { state.smoothing = it }) { "${(it * 100).roundToInt()}%" }
            }
            Tool.Line -> OptSlider("Stroke", state.shapeWidth, 1f..40f, { state.shapeWidth = it }) { "${it.roundToInt()}" }
            Tool.Pen, Tool.Rect, Tool.Oval -> {
                OptSlider("Stroke", state.shapeWidth, 1f..40f, { state.shapeWidth = it }) { "${it.roundToInt()}" }
                ToggleChip("Fill", state.shapeFill, { state.shapeFill = !state.shapeFill })
                if (state.tool == Tool.Pen) {
                    Text("Tap to add points · tap first point to close", style = Ui.label.copy(color = Ui.TextFaint))
                    if (state.penPoints.isNotEmpty()) ToggleChip("Done", false, { state.finishPen(closed = false) })
                }
            }
            Tool.Bucket -> Text("Tap inside a closed shape to fill it", style = Ui.label.copy(color = Ui.TextFaint))
            Tool.Eraser -> OptSlider("Size", state.eraserSize, 4f..200f, { state.eraserSize = it }) { "${it.roundToInt()}" }
            Tool.Dropper -> Text("Drag over the art to sample a colour", style = Ui.label.copy(color = Ui.TextFaint))
            Tool.Select, Tool.Transform -> {
                val l = state.selectedLayer
                Text(
                    l?.name ?: "Tap an object",
                    style = Ui.label.copy(color = if (l != null) Ui.TextDim else Ui.TextFaint),
                )
                if (l != null) {
                    Text(
                        if (l.keyAt(state.frame) != null) "Keyframe" else "Moving creates a key",
                        style = Ui.label.copy(color = Ui.TextFaint, fontSize = 10.sp),
                    )
                }
            }
            Tool.Hand -> ZoomControls(state)
            Tool.Zoom -> {
                Segmented(listOf("In" to false, "Out" to true), state.zoomOut, { state.zoomOut = it })
                ZoomControls(state)
            }
        }
    }
}

@Composable
private fun ZoomControls(state: EditorState) {
    Text("${(state.viewScale * 100).roundToInt()}%", style = Ui.num.copy(fontSize = 12.sp, color = Ui.TextDim))
    ToggleChip("Fit", false, { state.requestFit() })
    ToggleChip("100%", false, { state.zoomTo(1f) })
}

@Composable
private fun OptSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    format: (Float) -> String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = Ui.label.copy(color = Ui.TextFaint))
        MiniSlider(value, range, onChange, Modifier.width(96.dp))
        Text(format(value), style = Ui.num.copy(fontSize = 12.sp, color = Ui.TextDim), modifier = Modifier.width(34.dp))
    }
}

/** Small zoom readout pinned to the stage corner. */
@Composable
fun ZoomBadge(state: EditorState, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Ui.Bg1.copy(alpha = 0.9f))
            .border(1.dp, Ui.Line, RoundedCornerShape(12.dp))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${(state.viewScale * 100).roundToInt()}%",
            style = Ui.num.copy(fontSize = 11.sp, color = Ui.TextDim),
            modifier = Modifier.padding(horizontal = 6.dp).offset(y = 0.dp),
        )
        IconBtn(Ic.Fit, { state.requestFit() }, size = 32.dp, iconSize = 16.dp, description = "Fit stage")
    }
}
