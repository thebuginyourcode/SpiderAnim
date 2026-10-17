package com.spideranim.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.spideranim.editor.EditorState
import com.spideranim.model.Layer
import com.spideranim.model.Marker
import com.spideranim.model.MarkerColors
import com.spideranim.ui.icons.Ic
import com.spideranim.ui.theme.Ui
import kotlin.math.max
import kotlin.math.roundToInt

private val RulerMarkers = 18.dp
private val RulerNumbers = 24.dp

@Composable
fun Timeline(state: EditorState, compact: Boolean, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    Column(modifier.floatingPanel().animateContentSize()) {
        PlaybackBar(state, compact, expanded) { expanded = !expanded }
        if (expanded) {
            Hairline()
            TimelineBody(
                state,
                cellW = if (compact) 15.dp else 18.dp,
                rowH = 50.dp,
                headerW = if (compact) 116.dp else 176.dp,
                // Grows with layers up to four rows, then scrolls (as ToonSquid's does).
                rowsShown = state.project.layers.size.coerceIn(1, if (compact) 2 else 4),
            )
        }
    }
}

@Composable
private fun PlaybackBar(state: EditorState, compact: Boolean, expanded: Boolean, onToggle: () -> Unit) {
    val btn = if (compact) 34.dp else 38.dp
    val p = state.project
    Row(
        Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!compact) {
            IconBtn(Ic.Plus, { state.addLayer() }, size = btn, description = "New layer")
            IconBtn(Ic.Onion, { state.onion = !state.onion }, selected = state.onion, size = btn, description = "Onion skin")
            IconBtn(Ic.Loop, { state.loop = !state.loop }, selected = state.loop, size = btn, description = "Loop")
            Spacer(Modifier.weight(1f))
        }
        IconBtn(Ic.PrevFrame, { state.step(-1) }, size = btn, description = "Previous frame")
        PlayButton(state.playing, { state.togglePlay() }, size = if (compact) 36.dp else 40.dp)
        IconBtn(Ic.NextFrame, { state.step(1) }, size = btn, description = "Next frame")
        Spacer(Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.width(if (compact) 58.dp else 72.dp)) {
            Text("${state.frame + 1}", style = Ui.num.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
            Text(" / ${p.length}", style = Ui.num.copy(fontSize = 11.sp, color = Ui.TextFaint), modifier = Modifier.padding(bottom = 2.dp))
        }
        Spacer(Modifier.weight(1f))
        if (compact) IconBtn(Ic.Onion, { state.onion = !state.onion }, selected = state.onion, size = btn, description = "Onion skin")
        IconBtn(Ic.Marker, { state.addMarker() }, size = btn, description = "Add marker")
        IconBtn(Ic.KeyAdd, { state.insertKeyframe() }, size = btn, description = "Add keyframe")
        TimelineMenu(state, compact, btn)
        IconBtn(if (expanded) Ic.ChevronDown else Ic.ChevronUp, onToggle, size = btn, description = "Collapse timeline")
    }
}

@Composable
private fun TimelineMenu(state: EditorState, compact: Boolean, btn: Dp) {
    var open by remember { mutableStateOf(false) }
    val p = state.project
    Box {
        IconBtn(Ic.More, { open = true }, size = btn, description = "Timeline options")
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val item: @Composable (String, () -> Unit) -> Unit = { label, action ->
                DropdownMenuItem(text = { Text(label, style = Ui.body) }, onClick = { open = false; action() })
            }
            item("Remove keyframe") { state.deleteKeyframe() }
            item("Remove frame pick") { state.removePick() }
            if (compact) {
                item("New layer") { state.addLayer() }
                item(if (state.loop) "Loop: on" else "Loop: off") { state.loop = !state.loop }
            }
            listOf(12, 24, 30).forEach { f -> item("$f fps" + if (p.fps == f) "  ✓" else "") { state.setFps(f) } }
            item("Add 12 frames (${p.length})") { state.setLength(p.length + 12) }
            if (p.length > 12) item("Remove 12 frames") { state.setLength(p.length - 12) }
        }
    }
}

@Composable
private fun TimelineBody(state: EditorState, cellW: Dp, rowH: Dp, headerW: Dp, rowsShown: Int) {
    val density = LocalDensity.current
    val cellPx = with(density) { cellW.toPx() }
    val rowPx = with(density) { rowH.toPx() }
    val markerPx = with(density) { RulerMarkers.toPx() }
    var scrollX by remember { mutableFloatStateOf(0f) }
    var scrollY by remember { mutableFloatStateOf(0f) }
    var gridW by remember { mutableFloatStateOf(0f) }
    var gridH by remember { mutableFloatStateOf(0f) }
    var editingMarker by remember { mutableStateOf<Marker?>(null) }
    val measurer = rememberTextMeasurer()

    fun maxX() = max(0f, (state.project.length + 6) * cellPx - gridW)
    fun maxY() = max(0f, state.project.layers.size * rowPx - gridH)

    LaunchedEffect(state.frame, gridW) {
        val x = state.frame * cellPx
        if (gridW > 0 && (x < scrollX || x + cellPx > scrollX + gridW)) {
            scrollX = (x - gridW * 0.25f).coerceIn(0f, maxX())
        }
    }

    val rows = state.project.layers.asReversed()

    Column {
        // ---- ruler: markers above frame numbers
        Row(Modifier.height(RulerMarkers + RulerNumbers)) {
            Box(Modifier.width(headerW).fillMaxHeight().padding(start = 14.dp, bottom = 5.dp), contentAlignment = Alignment.BottomStart) {
                Text("LAYERS", style = Ui.caps)
            }
            Canvas(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(state, cellPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val f = ((down.position.x + scrollX) / cellPx).toInt()
                            if (down.position.y < markerPx) {
                                val m = state.project.markers.firstOrNull { f in it.from..it.to }
                                if (m != null) {
                                    down.consume()
                                    editingMarker = m
                                    return@awaitEachGesture
                                }
                            }
                            state.playing = false
                            fun set(x: Float) = state.seek(((x + scrollX) / cellPx).toInt())
                            set(down.position.x)
                            down.consume()
                            drag(down.id) {
                                set(it.position.x)
                                it.consume()
                            }
                        }
                    },
            ) {
                val sx = scrollX.coerceIn(0f, maxX())
                val first = (sx / cellPx).toInt()
                val last = ((sx + size.width) / cellPx).toInt() + 1
                val numTop = markerPx
                val numH = size.height - markerPx

                clipRect(0f, 0f, size.width, markerPx) {
                    state.project.markers.forEach { m ->
                        val x0 = m.from * cellPx - sx + 1.dp.toPx()
                        val x1 = (m.to + 1) * cellPx - sx - 1.dp.toPx()
                        if (x1 < 0 || x0 > size.width) return@forEach
                        val top = 2.dp.toPx()
                        val h = markerPx - 4.dp.toPx()
                        drawRoundRect(Color(m.color), Offset(x0, top), Size(x1 - x0, h), CornerRadius(4.dp.toPx()))
                        val label = measurer.measure(m.comment, Ui.label.copy(fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.SemiBold), maxLines = 1)
                        if (x1 - x0 > 14.dp.toPx()) {
                            clipRect(x0, top, x1 - 3.dp.toPx(), top + h) {
                                drawText(label, topLeft = Offset(x0 + 5.dp.toPx(), top + (h - label.size.height) / 2))
                            }
                        }
                    }
                }

                val labelStyle = Ui.num.copy(fontSize = 10.sp, color = Ui.TextFaint)
                for (f in first..last) {
                    val x = f * cellPx - sx
                    val n = f + 1
                    if (f < state.project.length) {
                        drawLine(Ui.Line, Offset(x, size.height - 4.dp.toPx()), Offset(x, size.height), strokeWidth = 1f)
                    }
                    if ((n == 1 || n % 5 == 0) && f != state.frame && f < state.project.length) {
                        val layout = measurer.measure(n.toString(), labelStyle)
                        drawText(layout, topLeft = Offset(x + cellPx / 2 - layout.size.width / 2f, numTop + (numH - layout.size.height) / 2 - 2.dp.toPx()))
                    }
                }
                val px = state.frame * cellPx - sx + cellPx / 2
                val layout = measurer.measure((state.frame + 1).toString(), Ui.num.copy(fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.SemiBold))
                val w = max(layout.size.width + 10.dp.toPx(), 22.dp.toPx())
                val h = 18.dp.toPx()
                val top = numTop + (numH - h) / 2 - 2.dp.toPx()
                drawRoundRect(Ui.Accent, Offset(px - w / 2, top), Size(w, h), CornerRadius(h / 2))
                drawText(layout, topLeft = Offset(px - layout.size.width / 2f, top + (h - layout.size.height) / 2))
            }
        }
        Hairline()
        // ---- layers
        Row(Modifier.height(rowH * rowsShown)) {
            Box(
                Modifier
                    .width(headerW)
                    .fillMaxHeight()
                    .clipToBounds()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { c, dy ->
                            c.consume()
                            scrollY = (scrollY - dy).coerceIn(0f, maxY())
                        }
                    },
            ) {
                Column(
                    Modifier
                        .wrapContentHeight(align = Alignment.Top, unbounded = true)
                        .offset { IntOffset(0, -scrollY.coerceIn(0f, maxY()).roundToInt()) },
                ) {
                    rows.forEach { layer -> LayerHeader(state, layer, rowH) }
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(Ui.Line))
            Canvas(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onSizeChanged {
                        gridW = it.width.toFloat()
                        gridH = it.height.toFloat()
                    }
                    .pointerInput(state, cellPx, rowPx) {
                        val slop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val layers = state.project.layers.asReversed()
                            val yIn = down.position.y + scrollY
                            val layer = layers.getOrNull((yIn / rowPx).toInt())
                            val inPickLane = (yIn % rowPx) > rowPx * 0.5f
                            fun frameAt(x: Float) = ((x + scrollX) / cellPx).toInt()
                            val f0 = frameAt(down.position.x)
                            val key = layer?.keyAt(f0)?.takeIf { !inPickLane }
                            val pick = layer?.pickAt(f0)?.takeIf { inPickLane }
                            // 0 undecided, 1 scroll, 2 drag keyframe, 3 drag frame pick
                            var mode = 0
                            var total = Offset.Zero
                            var at = f0
                            val snapshot = state.project
                            var pushed = false
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) {
                                    if (mode == 0) {
                                        state.playing = false
                                        if (layer != null) state.selectLayer(layer.id)
                                        state.seek(f0)
                                    }
                                    break
                                }
                                val d = ch.positionChange()
                                total += d
                                if (mode == 0 && total.getDistance() > slop) {
                                    mode = when {
                                        layer == null || layer.locked -> 1
                                        key != null -> 2
                                        pick != null -> 3
                                        else -> 1
                                    }
                                    if (mode >= 2) state.selectLayer(layer!!.id)
                                }
                                when (mode) {
                                    1 -> {
                                        scrollX = (scrollX - d.x).coerceIn(0f, maxX())
                                        scrollY = (scrollY - d.y).coerceIn(0f, maxY())
                                        ch.consume()
                                    }
                                    2, 3 -> {
                                        val nf = frameAt(ch.position.x).coerceIn(0, state.project.length - 1)
                                        val l = state.project.layer(layer!!.id)
                                        val free = if (mode == 2) l?.keyAt(nf) == null else l?.pickAt(nf) == null
                                        if (l != null && nf != at && free) {
                                            if (!pushed) {
                                                state.pushHistory(snapshot)
                                                pushed = true
                                            }
                                            if (mode == 2) state.moveKeyframe(l.id, at, nf) else state.movePick(l.id, at, nf)
                                            at = nf
                                            state.seek(nf)
                                        }
                                        ch.consume()
                                    }
                                }
                            }
                        }
                    },
            ) {
                val sx = scrollX.coerceIn(0f, maxX())
                val sy = scrollY.coerceIn(0f, maxY())
                val length = state.project.length
                val first = (sx / cellPx).toInt()
                val last = ((sx + size.width) / cellPx).toInt() + 1
                for (f in first..last) {
                    if ((f + 1) % 5 == 0 && f < length) {
                        drawRect(Ui.Bg2.copy(alpha = 0.55f), Offset(f * cellPx - sx, 0f), Size(cellPx, size.height))
                    }
                }
                // Timeline markers tint their frames faintly through every row.
                state.project.markers.forEach { m ->
                    drawRect(Color(m.color).copy(alpha = 0.06f), Offset(m.from * cellPx - sx, 0f), Size((m.to - m.from + 1) * cellPx, size.height))
                }
                val keyR = 5.dp.toPx()
                val pickH = 18.dp.toPx()
                val labelStyle = Ui.label.copy(fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                rows.forEachIndexed { i, layer ->
                    val top = i * rowPx - sy
                    if (top > size.height || top + rowPx < 0) return@forEachIndexed
                    val sel = layer.id == state.selectedLayerId
                    if (sel) drawRect(Color(layer.color).copy(alpha = 0.08f), Offset(0f, top), Size(size.width, rowPx))
                    drawLine(Ui.Line, Offset(0f, top + rowPx - 0.5f), Offset(size.width, top + rowPx - 0.5f), strokeWidth = 1f)
                    val dim = if (layer.visible) 1f else 0.4f
                    val trackColor = Color(layer.color)

                    // Keyframe lane: diamonds joined by a line where they tween.
                    val ky = top + rowPx * 0.27f
                    val ks = layer.keyframes
                    ks.forEachIndexed { k, kf ->
                        val next = ks.getOrNull(k + 1) ?: return@forEachIndexed
                        val x0 = kf.frame * cellPx - sx + cellPx / 2
                        val x1 = next.frame * cellPx - sx + cellPx / 2
                        if (kf.tween) drawLine(trackColor.copy(alpha = 0.7f * dim), Offset(x0, ky), Offset(x1, ky), strokeWidth = 2.dp.toPx())
                    }
                    ks.forEach { kf ->
                        val c = Offset(kf.frame * cellPx - sx + cellPx / 2, ky)
                        val diamond = Path().apply {
                            moveTo(c.x, c.y - keyR); lineTo(c.x + keyR, c.y); lineTo(c.x, c.y + keyR); lineTo(c.x - keyR, c.y); close()
                        }
                        drawPath(diamond, (if (layer.locked) Ui.TextFaint else Color.White).copy(alpha = dim))
                        if (sel && kf.frame == state.frame) {
                            drawCircle(Ui.Accent, radius = keyR + 3.dp.toPx(), center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()))
                        }
                    }

                    // Frame-picker lane: one block per pick, coloured and named by its drawing's marker.
                    val sym = state.project.symbol(layer.symbolId)
                    val py = top + rowPx * 0.52f
                    val picks = layer.picks
                    picks.forEachIndexed { k, pk ->
                        val end = picks.getOrNull(k + 1)?.frame ?: length
                        val x0 = pk.frame * cellPx - sx + 1.dp.toPx()
                        val x1 = end * cellPx - sx - 1.dp.toPx()
                        if (x1 < 0 || x0 > size.width || x1 <= x0) return@forEachIndexed
                        val d = sym?.frames?.getOrNull(pk.drawing)
                        val color = Color(d?.markerColor ?: MarkerColors[0])
                        drawRoundRect(color.copy(alpha = 0.9f * dim), Offset(x0, py), Size(x1 - x0, pickH), CornerRadius(5.dp.toPx()))
                        if (sel && pk.frame == state.frame) {
                            drawRoundRect(Color.White, Offset(x0, py), Size(x1 - x0, pickH), CornerRadius(5.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()))
                        }
                        if (x1 - x0 > 10.dp.toPx() && d != null) {
                            val text = measurer.measure(d.label(pk.drawing), labelStyle, maxLines = 1)
                            clipRect(x0, py, x1 - 2.dp.toPx(), py + pickH) {
                                drawText(text, topLeft = Offset(x0 + 5.dp.toPx(), py + (pickH - text.size.height) / 2))
                            }
                        }
                    }
                }
                val endX = length * cellPx - sx
                if (endX < size.width) drawRect(Ui.Bg0.copy(alpha = 0.5f), Offset(endX, 0f), Size(size.width - endX, size.height))
                val px = state.frame * cellPx - sx + cellPx / 2
                drawLine(Ui.Accent, Offset(px, 0f), Offset(px, size.height), strokeWidth = 1.5.dp.toPx())
            }
        }
    }

    editingMarker?.let { m ->
        MarkerDialog(state, m, onDismiss = { editingMarker = null })
    }
}

@Composable
private fun LayerHeader(state: EditorState, layer: Layer, rowH: Dp) {
    val sel = layer.id == state.selectedLayerId
    val sym = state.project.symbol(layer.symbolId)
    val drawingIndex = layer.drawingAt(state.frame)
    val drawing = sym?.frames?.getOrNull(drawingIndex)
    Row(
        Modifier
            .fillMaxWidth()
            .height(rowH)
            .background(if (sel) Color(layer.color).copy(alpha = 0.12f) else Color.Transparent)
            .clickable { state.selectLayer(layer.id) }
            .padding(start = 8.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(30.dp).clip(RoundedCornerShape(2.dp)).background(Color(layer.color).copy(alpha = if (sel) 1f else 0.6f)))
        Spacer(Modifier.width(7.dp))
        if (drawing != null) SymbolThumb(drawing, state.project.background, Modifier.size(34.dp))
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Text(
                layer.name,
                style = Ui.body.copy(fontSize = 12.sp, color = if (!layer.visible) Ui.TextFaint else if (sel) Ui.Text else Ui.TextDim),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (drawing != null && sym != null && sym.frames.size > 1) {
                Text(drawing.label(drawingIndex), style = Ui.label.copy(fontSize = 10.sp, color = Color(drawing.markerColor)), maxLines = 1)
            }
        }
        RowToggle(if (layer.visible) Ic.Eye else Ic.EyeOff, !layer.visible) { state.toggleVisible(layer.id) }
        RowToggle(if (layer.locked) Ic.Lock else Ic.Unlock, layer.locked) { state.toggleLock(layer.id) }
    }
}

@Composable
private fun RowToggle(icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (active) Ui.Accent else Ui.TextFaint, modifier = Modifier.size(15.dp))
    }
}

/** Edit a timeline marker: comment, colour and the From/To frames it covers. */
@Composable
private fun MarkerDialog(state: EditorState, m: Marker, onDismiss: () -> Unit) {
    var comment by remember { mutableStateOf(m.comment) }
    var color by remember { mutableIntStateOf(m.color) }
    var from by remember { mutableIntStateOf(m.from) }
    var to by remember { mutableIntStateOf(m.to) }
    val last = state.project.length - 1
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(320.dp).floatingPanel().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Marker", style = Ui.title)
            BasicTextField(
                value = comment,
                onValueChange = { comment = it.take(40) },
                singleLine = true,
                textStyle = Ui.body.copy(fontSize = 15.sp),
                cursorBrush = SolidColor(Ui.Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ui.Bg2)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MarkerColors.forEach { c -> ColorWell(c, { color = c }, size = 32.dp, selected = c == color) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FrameStepper("From", from, Modifier.weight(1f)) { from = it.coerceIn(0, last); if (to < from) to = from }
                FrameStepper("To", to, Modifier.weight(1f)) { to = it.coerceIn(from, last) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallAction("Delete", danger = true) {
                    state.deleteMarker(m.id)
                    onDismiss()
                }
                Spacer(Modifier.weight(1f))
                DialogButton("Save", primary = true) {
                    state.updateMarker(m.copy(comment = comment.ifBlank { m.comment }, color = color, from = from, to = to))
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun FrameStepper(label: String, value: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    Row(
        modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(12.dp)).background(Ui.Bg2).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBtn(Ic.Minus, { onChange(value - 1) }, size = 30.dp, iconSize = 14.dp)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = Ui.label.copy(fontSize = 9.sp, color = Ui.TextFaint))
            Text("${value + 1}", style = Ui.num.copy(fontSize = 13.sp))
        }
        IconBtn(Ic.Plus, { onChange(value + 1) }, size = 30.dp, iconSize = 14.dp)
    }
}
