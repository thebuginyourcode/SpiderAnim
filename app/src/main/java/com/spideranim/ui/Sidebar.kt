package com.spideranim.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.spideranim.editor.BrushStyle
import com.spideranim.editor.ColorTarget
import com.spideranim.editor.EditorState
import com.spideranim.editor.ShapeKind
import com.spideranim.editor.Tool
import com.spideranim.ui.icons.Ic
import com.spideranim.ui.theme.Ui
import kotlin.math.roundToInt

fun Tool.icon() = when (this) {
    Tool.Transform -> Ic.Select
    Tool.Brush -> Ic.Brush
    Tool.Eraser -> Ic.Eraser
    Tool.Fill -> Ic.Bucket
    Tool.Shape -> Ic.Shape
    Tool.Eyedropper -> Ic.Dropper
}

private val panelShape = RoundedCornerShape(18.dp)

fun Modifier.floatingPanel() = this
    .clip(panelShape)
    .background(Ui.Bg1)
    .border(1.dp, Ui.Line, panelShape)

/** Opens to the left of the anchor (the sidebar sits on the right edge), or to its right if there is no room. */
private object LeftOfAnchor : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val gap = 20
        var x = anchorBounds.left - popupContentSize.width - gap
        if (x < gap) x = anchorBounds.right + gap
        x = x.coerceAtMost(windowSize.width - popupContentSize.width - gap).coerceAtLeast(gap)
        val y = (anchorBounds.top - 40).coerceAtMost(windowSize.height - popupContentSize.height - gap).coerceAtLeast(gap)
        return IntOffset(x, y)
    }
}

@Composable
private fun SidePopover(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Popup(popupPositionProvider = LeftOfAnchor, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Column(
            Modifier.width(272.dp).floatingPanel().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) { content() }
    }
}

/**
 * The right-hand sidebar: colour, tools, size and opacity, inspector. Tap the active tool again
 * for its options.
 */
@Composable
fun Sidebar(state: EditorState, compact: Boolean, onInspector: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.floatingPanel().verticalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ColorButton(state)
        Divider()
        Tool.entries.forEach { t -> ToolButton(state, t) }
        if (!compact && (state.tool == Tool.Brush || state.tool == Tool.Eraser)) {
            Divider()
            if (state.tool == Tool.Brush) {
                VSlider(state.brushSize, 1f..120f, { state.brushSize = it }, label = "${state.brushSize.roundToInt()}")
                VSlider(state.brushOpacity, 0.05f..1f, { state.brushOpacity = it }, label = "${(state.brushOpacity * 100).roundToInt()}%")
            } else {
                VSlider(state.eraserSize, 4f..200f, { state.eraserSize = it }, label = "${state.eraserSize.roundToInt()}")
            }
        }
        Divider()
        IconBtn(Ic.Inspector, onInspector, selected = !compact && state.inspectorOpen, size = 44.dp, description = "Inspector")
    }
}

@Composable
private fun Divider() {
    Box(Modifier.padding(vertical = 6.dp).width(24.dp).height(1.dp).background(Ui.Line))
}

@Composable
private fun ToolButton(state: EditorState, t: Tool) {
    val selected = state.tool == t
    Box(contentAlignment = Alignment.BottomCenter) {
        IconBtn(t.icon(), { state.selectTool(t) }, selected = selected, size = 44.dp, iconSize = 22.dp, description = t.label)
        // The dot says "tap again for options".
        if (selected && t != Tool.Eyedropper) {
            Box(Modifier.padding(bottom = 4.dp).size(3.dp).clip(CircleShape).background(Ui.Accent))
        }
        if (state.toolOptions == t) {
            SidePopover(onDismiss = { state.toolOptions = null }) { ToolOptionsContent(state, t) }
        }
    }
}

@Composable
private fun ToolOptionsContent(state: EditorState, t: Tool) {
    Text(t.label, style = Ui.title)
    when (t) {
        Tool.Brush -> {
            Segmented(BrushStyle.entries.map { it.label to it }, state.brushStyle, { state.brushStyle = it }, Modifier.fillMaxWidth())
            OptionSlider("Size", state.brushSize, 1f..120f, { state.brushSize = it }) { "${it.roundToInt()}" }
            OptionSlider("Opacity", state.brushOpacity, 0.05f..1f, { state.brushOpacity = it }) { "${(it * 100).roundToInt()}%" }
            OptionSlider("Smoothing", state.smoothing, 0f..1f, { state.smoothing = it }) { "${(it * 100).roundToInt()}%" }
            ToggleChip("Stylus pressure", state.pressure, { state.pressure = !state.pressure })
        }
        Tool.Eraser -> OptionSlider("Size", state.eraserSize, 4f..200f, { state.eraserSize = it }) { "${it.roundToInt()}" }
        Tool.Fill -> Note("Tap inside a closed shape. Small gaps between line ends are closed for you.")
        Tool.Shape -> {
            Segmented(listOf("Line" to ShapeKind.Line, "Rect" to ShapeKind.Rect, "Oval" to ShapeKind.Oval, "Poly" to ShapeKind.Polygon), state.shapeKind, { state.shapeKind = it }, Modifier.fillMaxWidth())
            OptionSlider("Line width", state.shapeWidth, 1f..40f, { state.shapeWidth = it }) { "${it.roundToInt()}" }
            ToggleChip(if (state.shapeFill) "Filled" else "Outline only", state.shapeFill, { state.shapeFill = !state.shapeFill })
            if (state.shapeKind == ShapeKind.Polygon) Note("Tap to add points. Tap the first point to close, or the last one to finish.")
        }
        Tool.Transform -> {
            Note("Drag to move. Corners scale, sides stretch, the top knob rotates. Changes between keyframes add a keyframe.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleChip("Reset", false, { state.resetTransform() })
                ToggleChip("Add keyframe", false, { state.insertKeyframe() })
            }
        }
        Tool.Eyedropper -> Note("Touch the art to pick up its colour.")
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = Ui.label.copy(fontSize = 12.sp, color = Ui.TextFaint, lineHeight = 17.sp))
}

@Composable
private fun OptionSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    format: (Float) -> String,
) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = Ui.label, modifier = Modifier.weight(1f))
            Text(format(value), style = Ui.num.copy(fontSize = 12.sp, color = Ui.TextDim))
        }
        MiniSlider(value, range, onChange, Modifier.fillMaxWidth())
    }
}

/** The stroke colour as a big swatch, with the fill colour tucked behind it. */
@Composable
private fun ColorButton(state: EditorState) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.size(46.dp).clip(CircleShape).clickable { open = true }, contentAlignment = Alignment.Center) {
        Box(Modifier.align(Alignment.BottomEnd).padding(3.dp).size(18.dp).clip(CircleShape).background(Color(state.fillColor)).border(2.dp, Ui.Bg1, CircleShape))
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                .padding(3.dp)
                .clip(CircleShape)
                .background(Color(state.inkColor)),
        )
        if (open) {
            SidePopover(onDismiss = { open = false }) {
                Segmented(
                    listOf("Stroke" to ColorTarget.Stroke, "Fill" to ColorTarget.Fill),
                    state.colorTarget,
                    { state.colorTarget = it },
                    Modifier.fillMaxWidth(),
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

/** Tall pill slider, filled from the bottom, like ToonSquid/Procreate's size and opacity bars. */
@Composable
fun VSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    label: String,
    height: Dp = 104.dp,
) {
    val change by rememberUpdatedState(onChange)
    var active by remember { mutableStateOf(false) }
    val fill by animateColorAsState(if (active) Ui.Accent else Ui.Bg3, label = "vsFill")
    val t = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    val current by rememberUpdatedState(t)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 4.dp)) {
        Canvas(
            Modifier
                .width(30.dp)
                .height(height)
                .pointerInput(range) {
                    val v0 = range.start
                    val span = range.endInclusive - range.start
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        active = true
                        val startT = current
                        var dy = 0f
                        drag(down.id) { c ->
                            // Relative drag, so a touch doesn't jump the value.
                            dy += c.position.y - c.previousPosition.y
                            change(v0 + (startT - dy / size.height).coerceIn(0f, 1f) * span)
                            c.consume()
                        }
                        active = false
                    }
                },
        ) {
            val r = CornerRadius(size.width / 2)
            drawRoundRect(Ui.Bg2, cornerRadius = r)
            val h = (size.height * t).coerceAtLeast(size.width)
            drawRoundRect(fill, topLeft = Offset(0f, size.height - h), size = Size(size.width, h), cornerRadius = r)
            drawLine(Color.White, Offset(size.width * 0.3f, size.height - h + 6.dp.toPx()), Offset(size.width * 0.7f, size.height - h + 6.dp.toPx()), strokeWidth = 2.dp.toPx())
        }
        Text(label, style = Ui.num.copy(fontSize = 10.sp, color = if (active) Ui.Accent else Ui.TextFaint), modifier = Modifier.padding(top = 3.dp))
    }
}

/** Zoom readout and fit button pinned to the stage corner. */
@Composable
fun ZoomBadge(state: EditorState, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Ui.Bg1.copy(alpha = 0.92f))
            .border(1.dp, Ui.Line, RoundedCornerShape(12.dp))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${(state.viewScale * 100).roundToInt()}%", style = Ui.num.copy(fontSize = 11.sp, color = Ui.TextDim), modifier = Modifier.padding(horizontal = 6.dp))
        IconBtn(Ic.Fit, { state.requestFit() }, size = 32.dp, iconSize = 16.dp, description = "Fit stage")
    }
}
