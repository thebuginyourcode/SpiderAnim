package com.spideranim.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spideranim.editor.EditorState
import com.spideranim.model.Layer
import com.spideranim.ui.icons.Ic
import com.spideranim.ui.theme.Ui
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.drawscope.Stroke as LineStyle

@Composable
fun Timeline(state: EditorState, compact: Boolean, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .background(Ui.Bg1)
            .border(1.dp, Ui.Line, shape)
            .animateContentSize(),
    ) {
        TransportBar(state, compact, expanded) { expanded = !expanded }
        if (expanded) {
            Hairline()
            TimelineBody(
                state,
                cellW = if (compact) 14.dp else 16.dp,
                rowH = if (compact) 32.dp else 34.dp,
                nameW = if (compact) 112.dp else 156.dp,
                modifier = Modifier.height(if (compact) 132.dp else 172.dp),
            )
        }
    }
}

@Composable
private fun TransportBar(state: EditorState, compact: Boolean, expanded: Boolean, onToggle: () -> Unit) {
    val btn = if (compact) 32.dp else 36.dp
    val p = state.project
    val seg = state.selectedLayer?.segmentAt(state.frame)
    Row(
        Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBtn(Ic.PrevKey, { state.prevKey() }, size = btn, description = "Previous keyframe")
        Spacer(Modifier.width(2.dp))
        PlayButton(state.playing, { state.togglePlay() }, size = if (compact) 34.dp else 38.dp)
        Spacer(Modifier.width(2.dp))
        IconBtn(Ic.NextKey, { state.nextKey() }, size = btn, description = "Next keyframe")
        Spacer(Modifier.width(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${state.frame + 1}", style = Ui.num.copy(fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
            Text(" / ${p.length}", style = Ui.num.copy(fontSize = 12.sp, color = Ui.TextFaint), modifier = Modifier.padding(bottom = 1.dp))
        }
        if (!compact) {
            Spacer(Modifier.width(14.dp))
            MenuChip(
                "${p.fps} fps",
                listOf(8, 12, 15, 24, 30, 60).map { f -> "$f fps" to { state.setFps(f) } },
                current = "${p.fps} fps",
            )
            Spacer(Modifier.width(6.dp))
            MenuChip(
                "${p.length} frames",
                listOf(12, 24, 48, 72, 96, 120, 240).map { n -> "$n frames" to { state.setLength(n) } } +
                    ("Add 12 frames" to { state.setLength(p.length + 12) }),
                current = "${p.length} frames",
            )
        }
        Spacer(Modifier.width(6.dp))
        IconBtn(Ic.Onion, { state.onion = !state.onion }, selected = state.onion, size = btn, description = "Onion skin")
        if (!compact) IconBtn(Ic.Loop, { state.loop = !state.loop }, selected = state.loop, size = btn, description = "Loop")
        Spacer(Modifier.weight(1f))
        IconBtn(Ic.KeyAdd, { state.insertKeyframe() }, size = btn, description = "Insert keyframe")
        IconBtn(Ic.KeyRemove, { state.deleteKeyframe() }, size = btn, description = "Remove keyframe")
        if (compact) {
            TimelineMoreMenu(state, btn)
        } else {
            IconBtn(Ic.Tween, { state.toggleTween() }, selected = seg?.second != null && seg.first.tween, enabled = seg?.second != null, size = btn, description = "Tween")
            VHairline(Modifier.padding(horizontal = 6.dp))
            IconBtn(Ic.Layers, { state.addLayer() }, size = btn, description = "New layer")
        }
        IconBtn(if (expanded) Ic.ChevronDown else Ic.ChevronUp, onToggle, size = btn, description = "Toggle timeline")
    }
}

@Composable
private fun TimelineMoreMenu(state: EditorState, btn: Dp) {
    var open by remember { mutableStateOf(false) }
    val p = state.project
    Box {
        IconBtn(Ic.More, { open = true }, size = btn, description = "Timeline options")
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val item: @Composable (String, () -> Unit) -> Unit = { label, action ->
                DropdownMenuItem(text = { Text(label, style = Ui.body) }, onClick = { open = false; action() })
            }
            item("Toggle tween") { state.toggleTween() }
            item(if (state.loop) "Loop: on" else "Loop: off") { state.loop = !state.loop }
            item("New layer") { state.addLayer() }
            listOf(12, 24, 30).forEach { f -> item("$f fps" + if (p.fps == f) "  ✓" else "") { state.setFps(f) } }
            item("Add 12 frames (${p.length})") { state.setLength(p.length + 12) }
            if (p.length > 12) item("Remove 12 frames") { state.setLength(p.length - 12) }
        }
    }
}

@Composable
private fun TimelineBody(state: EditorState, cellW: Dp, rowH: Dp, nameW: Dp, modifier: Modifier) {
    val density = LocalDensity.current
    val cellPx = with(density) { cellW.toPx() }
    val rowPx = with(density) { rowH.toPx() }
    var scrollX by remember { mutableFloatStateOf(0f) }
    var scrollY by remember { mutableFloatStateOf(0f) }
    var gridW by remember { mutableFloatStateOf(0f) }
    var gridH by remember { mutableFloatStateOf(0f) }
    val measurer = rememberTextMeasurer()

    fun maxX() = max(0f, (state.project.length + 6) * cellPx - gridW)
    fun maxY() = max(0f, state.project.layers.size * rowPx - gridH)

    // Keep the playhead in view while playing or stepping.
    LaunchedEffect(state.frame, gridW) {
        val x = state.frame * cellPx
        if (gridW > 0 && (x < scrollX || x + cellPx > scrollX + gridW)) {
            scrollX = (x - gridW * 0.25f).coerceIn(0f, maxX())
        }
    }

    val rows = state.project.layers.asReversed()

    Column(modifier) {
        Row(Modifier.height(26.dp)) {
            Box(Modifier.width(nameW).fillMaxHeight().padding(start = 12.dp), contentAlignment = Alignment.CenterStart) {
                Text("LAYERS", style = Ui.caps)
            }
            Canvas(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(state, cellPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            state.playing = false
                            fun set(x: Float) = state.setFrame(((x + scrollX) / cellPx).toInt())
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
                val labelStyle = Ui.num.copy(fontSize = 10.sp, color = Ui.TextFaint)
                for (f in first..last) {
                    val x = f * cellPx - sx
                    val n = f + 1
                    if (f < state.project.length) {
                        drawLine(Ui.Line, Offset(x, size.height - 5.dp.toPx()), Offset(x, size.height), strokeWidth = 1f)
                    }
                    if ((n == 1 || n % 5 == 0) && f != state.frame) {
                        val layout = measurer.measure(n.toString(), labelStyle)
                        drawText(layout, topLeft = Offset(x + cellPx / 2 - layout.size.width / 2f, (size.height - layout.size.height) / 2 - 2.dp.toPx()))
                    }
                }
                // Playhead flag.
                val px = state.frame * cellPx - sx + cellPx / 2
                val layout = measurer.measure((state.frame + 1).toString(), Ui.num.copy(fontSize = 10.sp, color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
                val w = max(layout.size.width + 10.dp.toPx(), 20.dp.toPx())
                val h = 18.dp.toPx()
                drawRoundRect(Ui.Accent, Offset(px - w / 2, (size.height - h) / 2 - 2.dp.toPx()), Size(w, h), CornerRadius(h / 2))
                drawText(layout, topLeft = Offset(px - layout.size.width / 2f, (size.height - layout.size.height) / 2 - 2.dp.toPx()))
                drawLine(Ui.Accent, Offset(px, size.height - 4.dp.toPx()), Offset(px, size.height), strokeWidth = 1.5.dp.toPx())
            }
        }
        Hairline()
        Row(Modifier.weight(1f)) {
            Box(
                Modifier
                    .width(nameW)
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
                    rows.forEach { layer -> LayerNameRow(state, layer, rowH) }
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
                            val row = ((down.position.y + scrollY) / rowPx).toInt()
                            val layer = layers.getOrNull(row)
                            fun frameAt(x: Float) = ((x + scrollX) / cellPx).toInt()
                            val f0 = frameAt(down.position.x)
                            val key = layer?.keyAt(f0)
                            var mode = 0 // 0 undecided, 1 scroll, 2 drag keyframe
                            var total = Offset.Zero
                            var keyFrame = key?.frame ?: 0
                            val snapshot = state.project
                            var pushed = false
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) {
                                    if (mode == 0) {
                                        state.playing = false
                                        if (layer != null) state.selectLayer(layer.id)
                                        state.setFrame(f0)
                                    }
                                    break
                                }
                                val d = ch.positionChange()
                                total += d
                                if (mode == 0 && total.getDistance() > slop) {
                                    mode = if (key != null && layer != null && !layer.locked) 2 else 1
                                    if (mode == 2) state.selectLayer(layer!!.id)
                                }
                                when (mode) {
                                    1 -> {
                                        scrollX = (scrollX - d.x).coerceIn(0f, maxX())
                                        scrollY = (scrollY - d.y).coerceIn(0f, maxY())
                                        ch.consume()
                                    }
                                    2 -> {
                                        val nf = frameAt(ch.position.x).coerceIn(0, state.project.length - 1)
                                        val l = state.project.layer(layer!!.id)
                                        if (l != null && nf != keyFrame && l.keyAt(nf) == null) {
                                            if (!pushed) {
                                                state.pushHistory(snapshot)
                                                pushed = true
                                            }
                                            state.moveKeyframe(l.id, keyFrame, nf)
                                            keyFrame = nf
                                            state.setFrame(nf)
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
                // Every fifth frame gets a faint column, as in Animate.
                for (f in first..last) {
                    if ((f + 1) % 5 == 0 && f < length) {
                        drawRect(Ui.Bg2.copy(alpha = 0.7f), Offset(f * cellPx - sx, 0f), Size(cellPx, size.height))
                    }
                }
                val barH = rowPx * 0.46f
                val r = 4.5.dp.toPx()
                rows.forEachIndexed { i, layer ->
                    val top = i * rowPx - sy
                    if (top > size.height || top + rowPx < 0) return@forEachIndexed
                    val sel = layer.id == state.selectedLayerId
                    if (sel) drawRect(Ui.Accent.copy(alpha = 0.06f), Offset(0f, top), Size(size.width, rowPx))
                    drawLine(Ui.Line.copy(alpha = 0.7f), Offset(0f, top + rowPx - 0.5f), Offset(size.width, top + rowPx - 0.5f), strokeWidth = 1f)
                    val cy = top + rowPx / 2
                    val dim = if (layer.visible) 1f else 0.4f
                    val ks = layer.keyframes
                    ks.forEachIndexed { k, kf ->
                        val next = ks.getOrNull(k + 1)
                        val x0 = kf.frame * cellPx - sx + cellPx / 2
                        val x1 = if (next != null) next.frame * cellPx - sx + cellPx / 2 else length * cellPx - sx
                        if (x1 > x0) {
                            if (next != null && kf.tween) {
                                drawRoundRect(Ui.AccentSoft.copy(alpha = Ui.AccentSoft.alpha * dim), Offset(x0, cy - barH / 2), Size(x1 - x0, barH), CornerRadius(barH / 2))
                                drawLine(Ui.Accent.copy(alpha = 0.55f * dim), Offset(x0 + r + 2, cy), Offset(x1 - r - 2, cy), strokeWidth = 1.2.dp.toPx())
                            } else {
                                drawRoundRect(Ui.Bg3.copy(alpha = 0.8f * dim), Offset(x0, cy - barH / 2), Size(x1 - x0, barH), CornerRadius(barH / 2))
                            }
                        }
                    }
                    ks.forEachIndexed { k, kf ->
                        val cx = kf.frame * cellPx - sx + cellPx / 2
                        val c = Offset(cx, cy)
                        val color = if (layer.locked) Ui.TextFaint else Ui.Accent
                        drawCircle(color.copy(alpha = dim), radius = r, center = c)
                        // A white pip marks keys that change which symbol drawing is shown.
                        val prev = ks.getOrNull(k - 1)
                        if (prev != null && prev.symbolFrame != kf.symbolFrame) drawCircle(Color.White, radius = 1.6.dp.toPx(), center = c)
                        if (sel && kf.frame == state.frame) drawCircle(Ui.Text, radius = r + 2.5.dp.toPx(), center = c, style = LineStyle(width = 1.5.dp.toPx()))
                    }
                }
                val endX = length * cellPx - sx
                if (endX < size.width) drawRect(Ui.Bg0.copy(alpha = 0.5f), Offset(endX, 0f), Size(size.width - endX, size.height))
                val px = state.frame * cellPx - sx + cellPx / 2
                drawLine(Ui.Accent, Offset(px, 0f), Offset(px, size.height), strokeWidth = 1.5.dp.toPx())
            }
        }
    }
}

@Composable
private fun LayerNameRow(state: EditorState, layer: Layer, rowH: Dp) {
    val sel = layer.id == state.selectedLayerId
    Row(
        Modifier
            .fillMaxWidth()
            .height(rowH)
            .background(if (sel) Ui.Accent.copy(alpha = 0.08f) else Color.Transparent)
            .clickable { state.selectLayer(layer.id) }
            .padding(start = 10.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(14.dp).clip(RoundedCornerShape(2.dp)).background(if (sel) Ui.Accent else Ui.Bg3))
        Spacer(Modifier.width(8.dp))
        Text(
            layer.name,
            style = Ui.body.copy(fontSize = 12.sp, color = if (!layer.visible) Ui.TextFaint else if (sel) Ui.Text else Ui.TextDim),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
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
