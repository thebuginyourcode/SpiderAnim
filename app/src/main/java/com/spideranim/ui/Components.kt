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
fun IconBtn(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    description: String? = null,
) {
    val bg by animateColorAsState(if (selected) Ui.AccentSoft else Color.Transparent, label = "btnBg")
    val fg by animateColorAsState(
        when {
            !enabled -> Ui.TextFaint.copy(alpha = 0.5f)
            selected -> Ui.Accent
            else -> Ui.TextDim
        },
        label = "btnFg",
    )
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = fg, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Ui.Line))
}

@Composable
fun VHairline(modifier: Modifier = Modifier, height: Dp = 20.dp) {
    Box(modifier.width(1.dp).height(height).background(Ui.Line))
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = Ui.caps, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun Section(title: String, trailing: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(title, trailing = trailing)
        content()
    }
}

@Composable
fun EmptyNote(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = Ui.title.copy(fontSize = 14.sp))
        Text(body, style = Ui.label.copy(fontSize = 12.sp, color = Ui.TextFaint, lineHeight = 17.sp))
    }
}

/**
 * Animate-style "hot text": drag sideways to scrub the value. Accumulates from the value at drag
 * start so fast drags don't drift against recomposition.
 */
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
    val border by animateColorAsState(if (active) Ui.Accent else Color.Transparent, label = "scrubBorder")
    Row(
        modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Ui.Bg2)
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
        Text(label, style = Ui.label.copy(color = Ui.TextFaint))
        Spacer(Modifier.weight(1f))
        Text(
            "%.${decimals}f".format(value) + suffix,
            style = Ui.num.copy(fontSize = 12.sp, color = if (active) Ui.Accent else Ui.Text),
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
        drawLine(Ui.Bg3, Offset(pad, y), Offset(size.width - pad, y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Ui.Accent, Offset(pad, y), Offset(x, y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(Color.Black.copy(alpha = 0.3f), radius = 8.dp.toPx(), center = Offset(x, y + 1.dp.toPx()))
        drawCircle(Color.White, radius = 7.dp.toPx(), center = Offset(x, y))
    }
}

@Composable
fun ToggleChip(text: String, checked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(if (checked) Ui.AccentSoft else Ui.Bg2, label = "chipBg")
    val fg by animateColorAsState(if (checked) Ui.Accent else Ui.TextDim, label = "chipFg")
    Box(
        modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Ui.label.copy(color = fg, fontSize = 12.sp))
    }
}

@Composable
fun <T> Segmented(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.height(30.dp).clip(RoundedCornerShape(10.dp)).background(Ui.Bg2).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (label, v) ->
            val on = v == selected
            val bg by animateColorAsState(if (on) Ui.Bg3 else Color.Transparent, label = "segBg")
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .clickable { onSelect(v) }
                    .padding(horizontal = 12.dp)
                    .height(26.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Ui.label.copy(color = if (on) Ui.Text else Ui.TextDim, fontSize = 12.sp))
            }
        }
    }
}

/** A text chip that opens a small menu. */
@Composable
fun MenuChip(text: String, options: List<Pair<String, () -> Unit>>, modifier: Modifier = Modifier, current: String? = null) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Box(
            Modifier
                .height(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Ui.Bg2)
                .clickable { open = true }
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = Ui.num.copy(fontSize = 12.sp, color = Ui.TextDim), maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (label, action) ->
                DropdownMenuItem(
                    text = {
                        Text(label, style = Ui.body.copy(color = if (label == current) Ui.Accent else Ui.Text))
                    },
                    onClick = {
                        open = false
                        action()
                    },
                )
            }
        }
    }
}

@Composable
fun ColorWell(color: Int?, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 28.dp, selected: Boolean = false) {
    val ring by animateColorAsState(if (selected) Ui.Accent else Ui.Line, label = "wellRing")
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .border(if (selected) 2.dp else 1.dp, ring, CircleShape)
            .padding(3.dp)
            .clip(CircleShape)
            .background(if (color != null) Color(color) else Ui.Bg2)
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
            style = Ui.num.copy(fontSize = 12.sp, color = Ui.TextDim),
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

/** Accent play button used in the transport bar. */
@Composable
fun PlayButton(playing: Boolean, onClick: () -> Unit, size: Dp = 38.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Ui.Accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (playing) com.spideranim.ui.icons.Ic.Pause else com.spideranim.ui.icons.Ic.Play, if (playing) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(18.dp))
    }
}
