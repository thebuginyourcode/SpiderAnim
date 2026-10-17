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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spideranim.ui.theme.Ui
import androidx.compose.ui.graphics.drawscope.Stroke as LineStyle

@Composable
fun ScrubField(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String = "",
    perPx: Float = 1f,
    decimals: Int = 0,
    onStart: () -> Unit = {},
) {
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    val start by rememberUpdatedState(onStart)
    var active by remember { mutableStateOf(false) }
    val border by animateColorAsState(if (active) Ui.Teal else Color.Transparent, label = "scrubBorder")
    Row(
        modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Ui.Field)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val v0 = current
                    var dx = 0f
                    var started = false
                    drag(down.id) { c ->
                        dx += c.position.x - c.previousPosition.x
                        if (!started) {
                            started = true
                            active = true
                            start()
                        }
                        change(v0 + dx * perPx)
                        c.consume()
                    }
                    active = false
                }
            }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Ui.small.copy(color = Ui.Faint))
        Spacer(Modifier.weight(1f))
        Text(
            "%.${decimals}f".format(value) + suffix,
            style = Ui.num.copy(fontSize = 12.sp, color = if (active) Ui.Teal else Ui.Text),
        )
    }
}

@Composable
fun MiniSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onStart: () -> Unit = {},
) {
    val change by rememberUpdatedState(onChange)
    val start by rememberUpdatedState(onStart)
    val t = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    Canvas(
        modifier
            .height(28.dp)
            .pointerInput(range) {
                val pad = 8.dp.toPx()
                fun set(x: Float) {
                    val k = ((x - pad) / (size.width - pad * 2)).coerceIn(0f, 1f)
                    change(range.start + k * (range.endInclusive - range.start))
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    start()
                    set(down.position.x)
                    down.consume()
                    drag(down.id) {
                        set(it.position.x)
                        it.consume()
                    }
                }
            },
    ) {
        val pad = 8.dp.toPx()
        val y = size.height / 2
        val x = pad + t * (size.width - pad * 2)
        drawLine(Ui.Pressed, Offset(pad, y), Offset(size.width - pad, y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Ui.Teal, Offset(pad, y), Offset(x, y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(Color.Black.copy(alpha = 0.3f), radius = 8.dp.toPx(), center = Offset(x, y + 1.dp.toPx()))
        drawCircle(Color.White, radius = 7.dp.toPx(), center = Offset(x, y))
    }
}

@Composable
fun <T> Segmented(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.height(30.dp).clip(RoundedCornerShape(10.dp)).background(Ui.Field).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (label, v) ->
            val on = v == selected
            val bg by animateColorAsState(if (on) Ui.Pressed else Color.Transparent, label = "segBg")
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .clickable { onSelect(v) }
                    .padding(horizontal = 12.dp)
                    .height(26.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Ui.small.copy(color = if (on) Ui.Text else Ui.Dim, fontSize = 12.sp))
            }
        }
    }
}

/** A text chip that opens a small menu. */
@Composable
fun ColorWell(color: Int?, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 28.dp, selected: Boolean = false) {
    val ring by animateColorAsState(if (selected) Ui.Teal else Ui.Line, label = "wellRing")
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .border(if (selected) 2.dp else 1.dp, ring, CircleShape)
            .padding(3.dp)
            .clip(CircleShape)
            .background(if (color != null) Color(color) else Ui.Field)
            .clickable(onClick = onClick),
    ) {
        if (color == null) {
            Canvas(Modifier.size(size)) {
                drawLine(Ui.OnionPrev, Offset(this.size.width * 0.2f, this.size.height * 0.8f), Offset(this.size.width * 0.8f, this.size.height * 0.2f), strokeWidth = 1.5.dp.toPx())
            }
        }
    }
}

val Swatches = listOf(
    0xFF1C1D22, 0xFF4A4D57, 0xFF9EA2AC, 0xFFFFFFFF, 0xFFF4EDE1, 0xFFFFD166,
    0xFFFF7A45, 0xFFFF4D6A, 0xFFC04BFF, 0xFF4F7CFF, 0xFF2FC7D9, 0xFF2FC79A,
).map { it.toInt() }

/** HSV picker: saturation/value field, hue rail, and a curated swatch row. */
@Composable
fun ColorPicker(color: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var hue by remember { mutableFloatStateOf(0f) }
    var sat by remember { mutableFloatStateOf(0f) }
    var value by remember { mutableFloatStateOf(0f) }
    val change by rememberUpdatedState(onChange)

    LaunchedEffect(color) {
        // Only resync when the colour changed from outside; keeps hue stable on greys.
        if (Color.hsv(hue, sat, value).toArgb() != color) {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(color, hsv)
            if (hsv[1] > 0.001f && hsv[2] > 0.001f) hue = hsv[0]
            sat = hsv[1]
            value = hsv[2]
        }
    }

    fun emit() = change(Color.hsv(hue, sat, value).toArgb())

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    fun set(p: Offset) {
                        sat = (p.x / size.width).coerceIn(0f, 1f)
                        value = 1f - (p.y / size.height).coerceIn(0f, 1f)
                        emit()
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        set(down.position)
                        drag(down.id) {
                            set(it.position)
                            it.consume()
                        }
                    }
                },
        ) {
            drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val c = Offset(sat * size.width, (1f - value) * size.height)
            drawCircle(Color.Black.copy(alpha = 0.25f), radius = 9.dp.toPx(), center = c, style = LineStyle(width = 4.dp.toPx()))
            drawCircle(Color.White, radius = 8.dp.toPx(), center = c, style = LineStyle(width = 2.dp.toPx()))
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .pointerInput(Unit) {
                    fun set(x: Float) {
                        hue = (x / size.width).coerceIn(0f, 1f) * 360f
                        emit()
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        set(down.position.x)
                        drag(down.id) {
                            set(it.position.x)
                            it.consume()
                        }
                    }
                },
        ) {
            drawRect(Brush.horizontalGradient(List(7) { Color.hsv(it * 60f % 360f, 1f, 1f) }.let { it.dropLast(1) + Color.hsv(0f, 1f, 1f) }))
            val x = hue / 360f * size.width
            drawCircle(Color.White, radius = size.height / 2 - 1.dp.toPx(), center = Offset(x.coerceIn(size.height / 2, size.width - size.height / 2), size.height / 2), style = LineStyle(width = 2.dp.toPx()))
        }
        Swatches.chunked(6).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { sw -> ColorWell(sw, onClick = { change(sw) }, size = 30.dp, selected = sw == color) }
            }
        }
        Text(
            "#%06X".format(color and 0xFFFFFF),
            style = Ui.num.copy(fontSize = 12.sp, color = Ui.Dim),
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

/** Accent play button used in the transport bar. */

/** Toolbar button: a glyph that gets a dark (or teal) square behind it when selected. */
@Composable
fun BarButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    teal: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 44.dp,
    iconSize: Dp = 24.dp,
    tint: Color = Color.White,
    description: String? = null,
) {
    val bg = when {
        selected && teal -> Ui.TealButton
        selected -> Ui.Pressed
        else -> Color.Transparent
    }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(icon, description, tint = if (enabled) tint else Ui.Faint, modifier = Modifier.size(iconSize))
    }
}

/** Plain text button, as in the timeline's bottom bar ("Add Keyframe"). */
@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Ui.body.copy(fontSize = 16.sp, color = if (enabled) Color(0xFFD8D8D8) else Ui.Faint), maxLines = 1)
    }
}

/** The top bar's slider: thin track, white fill, big white knob. */
@Composable
fun KnobSlider(value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val change by rememberUpdatedState(onChange)
    val t = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    Canvas(
        modifier
            .height(40.dp)
            .pointerInput(range) {
                val pad = 16.dp.toPx()
                fun set(x: Float) {
                    val k = ((x - pad) / (size.width - pad * 2)).coerceIn(0f, 1f)
                    change(range.start + k * (range.endInclusive - range.start))
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    set(down.position.x)
                    down.consume()
                    drag(down.id) {
                        set(it.position.x)
                        it.consume()
                    }
                }
            },
    ) {
        val pad = 16.dp.toPx()
        val y = size.height / 2
        val x = pad + t * (size.width - pad * 2)
        drawLine(Color(0xFF4A4A4A), Offset(pad, y), Offset(size.width - pad, y), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Color(0xFFE8E8E8), Offset(pad, y), Offset(x, y), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(Color.Black.copy(alpha = 0.35f), radius = 15.dp.toPx(), center = Offset(x, y + 1.dp.toPx()))
        drawCircle(Color.White, radius = 14.dp.toPx(), center = Offset(x, y))
    }
}

@Composable
fun TextInput(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "", singleLine: Boolean = true) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        textStyle = Ui.body.copy(fontSize = 16.sp),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Ui.Teal),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = Ui.body.copy(fontSize = 16.sp, color = Ui.Faint))
                inner()
            }
        },
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(Ui.Field).padding(horizontal = 14.dp, vertical = 11.dp),
    )
}

/** A dialog card in the app's style, with right-aligned text buttons. */
@Composable
fun AppDialog(
    title: String,
    onDismiss: () -> Unit,
    actions: List<Pair<String, () -> Unit>>,
    content: @Composable ColumnScope.() -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(340.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF2A2A2A))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = Ui.body.copy(fontSize = 19.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = Color.White))
            content()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                actions.forEach { (label, action) -> TextAction(label, action) }
            }
        }
    }
}

/** A dropdown of text actions anchored to [anchor]. */
@Composable
fun ActionMenu(open: Boolean, onDismiss: () -> Unit, items: List<Pair<String, () -> Unit>>) {
    androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        items.forEach { (label, action) ->
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(label, style = Ui.body) },
                onClick = {
                    onDismiss()
                    action()
                },
            )
        }
    }
}
