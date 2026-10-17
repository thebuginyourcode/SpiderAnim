package com.spideranim.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spideranim.editor.Editor
import com.spideranim.editor.Panel
import com.spideranim.editor.Tool
import com.spideranim.model.Layer
import com.spideranim.model.LayerType
import com.spideranim.model.Marker
import com.spideranim.model.MarkerColors
import com.spideranim.model.Prop
import com.spideranim.model.clipBounds
import com.spideranim.model.strokeBounds
import com.spideranim.ui.icons.Ic
import com.spideranim.ui.theme.Ui
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.roundToInt

private val MainRow = 48.dp
private val PropRow = 30.dp
private val RulerH = 32.dp
private val MarkerH = 18.dp

private sealed class TRow(val layer: Layer, val height: Dp) {
    class Main(layer: Layer) : TRow(layer, MainRow)
    class Property(layer: Layer, val prop: Prop) : TRow(layer, PropRow)
}

private fun rowsOf(layers: List<Layer>): List<TRow> =
    layers.asReversed().flatMap { l -> listOf<TRow>(TRow.Main(l)) + l.animatedProps.map { TRow.Property(l, it) } }

@Composable
fun Timeline(ed: Editor, compact: Boolean, onAddAudio: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.background(Ui.Timeline)) {
        PlaybackBar(ed, compact)
        if (ed.timelineOpen) {
            TimelineGrid(ed, compact)
            BottomBar(ed, compact, onAddAudio)
        }
    }
}

// ------------------------------------------------------------------ playback bar

@Composable
private fun PlaybackBar(ed: Editor, compact: Boolean) {
    val b = if (compact) 40.dp else 46.dp
    var fpsMenu by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().height(if (compact) 46.dp else 54.dp).background(Ui.Bar).padding(horizontal = 6.dp)) {
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            BarButton(Ic.PrevFrame, { ed.step(-1) }, size = b, description = "Previous frame")
            BarButton(Ic.NextFrame, { ed.step(1) }, size = b, description = "Next frame")
            if (!compact) Spacer(Modifier.width(120.dp)) else Spacer(Modifier.width(4.dp))
            BarButton(Ic.Onion, { ed.onion = !ed.onion }, selected = ed.onion, teal = true, size = b, description = "Onion skin")
            if (!compact) BarButton(Ic.Loop, { ed.loop = !ed.loop }, selected = ed.loop, teal = true, size = b, description = "Loop")
        }
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            if (!compact) BarButton(Ic.Rewind, { ed.toStart() }, size = b, description = "Go to start")
            BarButton(if (ed.playing) Ic.Pause else Ic.Play, { ed.togglePlay() }, size = b, iconSize = 28.dp, description = "Play")
            if (!compact) BarButton(Ic.Forward, { ed.toEnd() }, size = b, description = "Go to end")
        }
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
            Breadcrumb(ed, compact)
            Spacer(Modifier.width(if (compact) 8.dp else 24.dp))
            Box {
                Text(
                    "${ed.project.fps} fps",
                    style = Ui.menu.copy(fontSize = if (compact) 14.sp else 17.sp),
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { fpsMenu = true }.padding(6.dp),
                )
                ActionMenu(fpsMenu, { fpsMenu = false }, listOf(8, 12, 15, 24, 25, 30, 60).map { f -> "$f fps" to { ed.setFps(f) } })
            }
            Spacer(Modifier.width(if (compact) 4.dp else 120.dp))
            BarButton(if (ed.timelineOpen) Ic.Collapse else Ic.Uncollapse, { ed.timelineOpen = !ed.timelineOpen }, size = b, description = "Collapse timeline")
        }
    }
}

/** "Scene 1" — or "Scene 1 › Mouth" while inside a symbol; tap a crumb to go back to it. */
@Composable
private fun Breadcrumb(ed: Editor, compact: Boolean) {
    val crumbs = ed.crumbs
    Row(verticalAlignment = Alignment.CenterVertically) {
        crumbs.forEachIndexed { i, c ->
            if (i > 0) Text(" › ", style = Ui.menu.copy(fontSize = if (compact) 14.sp else 17.sp, color = Ui.Faint))
            val last = i == crumbs.lastIndex
            Text(
                c.name,
                style = Ui.menu.copy(fontSize = if (compact) 14.sp else 17.sp, color = if (last) Color(0xFFD6D6D6) else Ui.Teal),
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = !last) { ed.exitTo(i) }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }
    }
}

// ------------------------------------------------------------------ grid

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineGrid(ed: Editor, compact: Boolean) {
    val density = LocalDensity.current
    val headerW = if (compact) 116.dp else 220.dp
    var cellPx by remember { mutableFloatStateOf(with(density) { (if (compact) 34.dp else 50.dp).toPx() }) }
    var scrollX by remember { mutableFloatStateOf(0f) }
    var scrollY by remember { mutableFloatStateOf(0f) }
    var gridW by remember { mutableFloatStateOf(0f) }
    var gridH by remember { mutableFloatStateOf(0f) }
    var markerEdit by remember { mutableStateOf<Marker?>(null) }
    var drawingMenu by remember { mutableStateOf<Pair<Long, Int>?>(null) }
    var layerMenu by remember { mutableStateOf<Layer?>(null) }
    var renaming by remember { mutableStateOf<Layer?>(null) }
    var rulerMenu by remember { mutableIntStateOf(-1) }
    val measurer = rememberTextMeasurer()
    val cameraPainter = rememberVectorPainter(Ic.Camera)

    val clip = ed.clip
    val rows = rowsOf(clip.layers)
    val rowPx = rows.map { with(density) { it.height.toPx() } }
    val tops = rowPx.runningFold(0f) { a, b -> a + b }
    val totalH = tops.last()
    val cap = if (compact) 160.dp else 230.dp
    val visibleH = with(density) { totalH.toDp() }.coerceIn(MainRow, cap)
    val hasMarkers = clip.markers.isNotEmpty()

    fun maxX() = max(0f, (ed.clip.length + 4) * cellPx - gridW)
    fun maxY() = max(0f, totalH - gridH)
    fun rowAt(y: Float): Int = tops.indexOfLast { it <= y }.coerceIn(0, (rows.size - 1).coerceAtLeast(0))

    LaunchedEffect(ed.frame, gridW, cellPx) {
        val x = ed.frame * cellPx
        if (gridW > 0 && (x < scrollX || x + cellPx > scrollX + gridW)) scrollX = (x - gridW * 0.3f).coerceIn(0f, maxX())
    }

    // Thumbnail framing per layer: one scale for the whole layer so drawings line up frame to frame.
    val layerBounds = remember(clip, ed.project) {
        clip.layers.associate { l ->
            l.id to when (l.type) {
                LayerType.Drawing -> l.exposures.mapNotNull { strokeBounds(it.strokes) }.reduceOrNull { a, b -> a.union(b) }
                LayerType.Symbol -> ed.project.clip(l.symbolId)?.let { s -> (0 until s.length).mapNotNull { ed.project.clipBounds(s, it) }.reduceOrNull { a, b -> a.union(b) } }
                else -> null
            }
        }
    }

    // ---- ruler
    Row(Modifier.fillMaxWidth().height(if (hasMarkers) RulerH + MarkerH else RulerH)) {
        Box(Modifier.width(headerW).fillMaxHeight().background(Ui.Timeline))
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clipToBounds()
                .pointerInput(ed, cellPx, hasMarkers) {
                    val markerPx = if (hasMarkers) MarkerH.toPx() else 0f
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val f = ((down.position.x + scrollX) / cellPx).toInt()
                        if (down.position.y < markerPx) {
                            val m = ed.clip.markers.firstOrNull { f in it.from..it.to }
                            if (m != null) {
                                down.consume()
                                markerEdit = m
                                return@awaitEachGesture
                            }
                        }
                        ed.stop()
                        fun set(x: Float) = ed.seek(((x + scrollX) / cellPx).toInt())
                        set(down.position.x)
                        down.consume()
                        val moved = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            drag(down.id) { set(it.position.x); it.consume() }
                            true
                        }
                        if (moved == null) {
                            // Held still: marker menu for this frame.
                            rulerMenu = f.coerceIn(0, ed.clip.length - 1)
                            drag(down.id) { it.consume() }
                        }
                    }
                },
        ) {
            val sx = scrollX.coerceIn(0f, maxX())
            val markerPx = if (hasMarkers) MarkerH.toPx() else 0f
            val first = (sx / cellPx).toInt()
            val last = ((sx + size.width) / cellPx).toInt() + 1
            val len = ed.clip.length
            // Markers above the numbers.
            ed.clip.markers.forEach { m ->
                val x0 = m.from * cellPx - sx + 1f
                val x1 = (m.to + 1) * cellPx - sx - 1f
                if (x1 < 0 || x0 > size.width) return@forEach
                drawRoundRect(Color(m.color), Offset(x0, 2f), Size(x1 - x0, markerPx - 4f), CornerRadius(4.dp.toPx()))
                val t = measurer.measure(m.comment, Ui.tiny.copy(color = Color(0xFF151515), fontWeight = FontWeight.SemiBold), maxLines = 1)
                clipRect(x0, 0f, x1 - 2f, markerPx) { drawText(t, topLeft = Offset(x0 + 5.dp.toPx(), (markerPx - t.size.height) / 2)) }
            }
            val step = when {
                cellPx >= 26.dp.toPx() -> 1
                cellPx >= 14.dp.toPx() -> 2
                else -> 5
            }
            val numTop = markerPx
            val numH = size.height - markerPx
            // Current frame cell, filled teal.
            val cx = ed.frame * cellPx - sx
            drawRect(Ui.Teal, Offset(cx, numTop), Size(cellPx, numH))
            for (f in first..last) {
                if (f >= len) break
                val x = f * cellPx - sx
                drawLine(Color(0xFF5A5A5A), Offset(x + cellPx, size.height - 6.dp.toPx()), Offset(x + cellPx, size.height), strokeWidth = 1.dp.toPx())
                if ((f + 1) % step == 0 || f == 0 || f == ed.frame) {
                    val cur = f == ed.frame
                    val t = measurer.measure("${f + 1}", Ui.num.copy(fontSize = 13.sp, color = if (cur) Color(0xFF111111) else Color(0xFFCFCFCF), fontWeight = if (cur) FontWeight.SemiBold else FontWeight.Normal))
                    drawText(t, topLeft = Offset(x + (cellPx - t.size.width) / 2, numTop + (numH - t.size.height) / 2 - 1.dp.toPx()))
                }
            }
            drawLine(Ui.Teal, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), strokeWidth = 1.5.dp.toPx())
        }
    }

    // ---- rows
    Row(Modifier.fillMaxWidth().height(visibleH)) {
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
                rows.forEach { r ->
                    when (r) {
                        is TRow.Main -> LayerHeader(ed, r.layer, compact, onLongPress = { layerMenu = r.layer })
                        is TRow.Property -> PropHeader(ed, r.layer, r.prop)
                    }
                }
            }
        }
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clipToBounds()
                .onSizeChanged {
                    gridW = it.width.toFloat()
                    gridH = it.height.toFloat()
                }
                .pointerInput(ed, rows.size, cellPx) {
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val y = down.position.y + scrollY
                        val idx = if (rows.isEmpty() || y > totalH) -1 else rowAt(y)
                        val row = rows.getOrNull(idx)
                        fun frameAt(x: Float) = ((x + scrollX) / cellPx).toInt()
                        val f0 = frameAt(down.position.x)
                        val keyHit = (row as? TRow.Property)?.let { r -> ed.clip.layer(r.layer.id)?.track(r.prop)?.keyAt(f0) }
                        val exposure = (row as? TRow.Main)?.let { r -> ed.clip.layer(r.layer.id)?.takeIf { it.type == LayerType.Drawing && !it.locked }?.exposureAt(f0) }
                        // 0 undecided, 1 scroll, 2 drag key, 3 drag drawing, 4 pinch
                        var mode = 0
                        var total = Offset.Zero
                        var at = f0
                        val grabOffset = exposure?.let { f0 - it.start } ?: 0
                        val snapshot = ed.project
                        var pushed = false

                        fun select() {
                            if (row != null) ed.selectLayer(row.layer.id, (row as? TRow.Property)?.prop)
                        }

                        if (exposure != null) {
                            // Long-press a drawing to move it; a still long-press opens its menu.
                            val early = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val ch = ev.changes.firstOrNull { it.id == down.id }
                                    if (ch == null || !ch.pressed) return@withTimeoutOrNull "up"
                                    if (ev.changes.count { it.pressed } > 1) return@withTimeoutOrNull "multi"
                                    total += ch.positionChange()
                                    if (total.getDistance() > slop) return@withTimeoutOrNull "moved"
                                }
                                @Suppress("UNREACHABLE_CODE") ""
                            }
                            when (early) {
                                null -> {
                                    select()
                                    ed.seek(f0)
                                    mode = 3
                                }
                                "up" -> {
                                    ed.stop()
                                    select()
                                    ed.seek(f0)
                                    return@awaitEachGesture
                                }
                                "multi" -> mode = 4
                                else -> mode = 1
                            }
                        }
                        var heldStill = mode == 3
                        while (true) {
                            val ev = awaitPointerEvent()
                            val pressed = ev.changes.filter { it.pressed }
                            if (pressed.isEmpty()) {
                                if (mode == 0) {
                                    ed.stop()
                                    select()
                                    ed.seek(f0)
                                }
                                if (mode == 3 && heldStill && row != null) drawingMenu = row.layer.id to f0
                                break
                            }
                            if (pressed.size > 1) {
                                mode = 4
                                val z = ev.calculateZoom()
                                cellPx = (cellPx * z).coerceIn(12.dp.toPx(), 120.dp.toPx())
                                scrollX = scrollX.coerceIn(0f, maxX())
                                ev.changes.forEach { it.consume() }
                                continue
                            }
                            val ch = pressed.firstOrNull { it.id == down.id } ?: pressed[0]
                            val d = ch.positionChange()
                            total += d
                            if (mode == 0 && total.getDistance() > slop) {
                                mode = if (keyHit != null && row != null && !row.layer.locked) 2 else 1
                                if (mode == 2) select()
                            }
                            when (mode) {
                                1 -> {
                                    scrollX = (scrollX - d.x).coerceIn(0f, maxX())
                                    scrollY = (scrollY - d.y).coerceIn(0f, maxY())
                                }
                                2 -> {
                                    val r = row as TRow.Property
                                    val nf = frameAt(ch.position.x).coerceAtLeast(0)
                                    if (nf != at && ed.clip.layer(r.layer.id)?.track(r.prop)?.keyAt(nf) == null) {
                                        if (!pushed) { ed.pushHistory(snapshot); pushed = true }
                                        ed.moveKey(r.layer.id, r.prop, at, nf)
                                        at = nf
                                        ed.seek(nf)
                                    }
                                }
                                3 -> {
                                    if (total.getDistance() > slop) heldStill = false
                                    val nf = (frameAt(ch.position.x) - grabOffset).coerceAtLeast(0)
                                    val e = exposure!!
                                    val cur = ed.clip.layer(row!!.layer.id)?.exposures?.firstOrNull { it.id == e.id }
                                    if (cur != null && nf != cur.start) {
                                        if (!pushed) { ed.pushHistory(snapshot); pushed = true }
                                        ed.moveExposure(row.layer.id, e.id, nf)
                                        ed.seek(nf + grabOffset)
                                    }
                                }
                            }
                            ch.consume()
                        }
                    }
                },
        ) {
            val sx = scrollX.coerceIn(0f, maxX())
            val sy = scrollY.coerceIn(0f, maxY())
            val len = ed.clip.length
            val first = (sx / cellPx).toInt().coerceAtLeast(0)
            val last = ((sx + size.width) / cellPx).toInt() + 1
            val bg = Color(ed.project.background)
            rows.forEachIndexed { i, r ->
                val top = tops[i] - sy
                val h = rowPx[i]
                if (top > size.height || top + h < 0) return@forEachIndexed
                val l = r.layer
                val selected = ed.selLayerId == l.id && when (r) {
                    is TRow.Main -> ed.selProp == null
                    is TRow.Property -> ed.selProp == r.prop
                }
                when (r) {
                    is TRow.Main -> {
                        drawRect(Ui.Track, Offset(0f, top), Size(size.width, h))
                        if (selected) drawLine(Ui.Teal, Offset(0f, top + 0.75f), Offset(size.width, top + 0.75f), strokeWidth = 1.5.dp.toPx())
                        val pad = 4.dp.toPx()
                        val cellTop = top + pad
                        val cellH = h - pad * 2
                        val dim = if (l.visible) 1f else 0.35f
                        when (l.type) {
                            LayerType.Drawing -> {
                                val lb = layerBounds[l.id]
                                for (e in l.exposures) {
                                    if (e.end <= first || e.start > last) continue
                                    val x0 = e.start * cellPx - sx + 1f
                                    val x1 = e.end * cellPx - sx - 1f
                                    drawRoundRect(Color(0xFF474747), Offset(x0, cellTop - 2f), Size(x1 - x0, cellH + 4f), CornerRadius(4.dp.toPx()))
                                    for (f in max(e.start, first)..minOf(e.end - 1, last)) {
                                        val cx = f * cellPx - sx
                                        val inset = 3.dp.toPx()
                                        val cw = cellPx - inset * 2
                                        drawRect(bg.copy(alpha = 0.9f * dim), Offset(cx + inset, cellTop), Size(cw, cellH))
                                        clipRect(cx + inset, cellTop, cx + inset + cw, cellTop + cellH) {
                                            translate(cx + inset, cellTop) {
                                                thumb(lb, cw, cellH) { e.strokes.forEach { drawMark(it, alpha = dim) } }
                                            }
                                        }
                                    }
                                }
                                // Empty frames show the transparency checker.
                                for (f in first..minOf(last, len - 1)) {
                                    if (l.exposureAt(f) != null) continue
                                    checker(Offset(f * cellPx - sx + 3.dp.toPx(), cellTop), Size(cellPx - 6.dp.toPx(), cellH))
                                }
                            }
                            LayerType.Symbol -> {
                                val sym = ed.project.clip(l.symbolId)
                                val lb = layerBounds[l.id]
                                if (sym != null) for (f in first..minOf(last, len - 1)) {
                                    val cx = f * cellPx - sx
                                    val inset = 2.dp.toPx()
                                    val cw = cellPx - inset * 2
                                    drawRect(bg.copy(alpha = 0.9f * dim), Offset(cx + inset, cellTop), Size(cw, cellH))
                                    clipRect(cx + inset, cellTop, cx + inset + cw, cellTop + cellH) {
                                        translate(cx + inset, cellTop) {
                                            thumb(lb, cw, cellH) { drawClip(ed.project, sym, ed.project.symbolFrame(l, f), RenderOptions(alpha = dim)) }
                                        }
                                    }
                                }
                            }
                            LayerType.Camera -> {
                                for (f in first..minOf(last, len - 1)) {
                                    val s = (cellH * 0.55f).coerceAtMost(cellPx * 0.6f)
                                    translate(f * cellPx - sx + (cellPx - s) / 2, cellTop + (cellH - s) / 2) {
                                        with(cameraPainter) { draw(Size(s, s), alpha = 0.55f, colorFilter = ColorFilter.tint(Color(0xFF9A9A9A))) }
                                    }
                                }
                            }
                            LayerType.Text -> {
                                val x0 = -sx + 1f
                                val x1 = len * cellPx - sx - 1f
                                drawRoundRect(Color(0xFF474747), Offset(x0, cellTop), Size(x1 - x0, cellH), CornerRadius(4.dp.toPx()))
                                val t = measurer.measure("T  ${l.text.lineSequence().first()}", Ui.small.copy(color = Ui.Text), maxLines = 1)
                                drawText(t, topLeft = Offset(max(x0, 0f) + 8.dp.toPx(), cellTop + (cellH - t.size.height) / 2))
                            }
                            LayerType.Audio -> {
                                val peaks = l.audio?.let { ed.audio.peaks[it] }
                                val mid = top + h / 2
                                if (peaks != null) {
                                    val perPx = 100f / (ed.project.fps * cellPx)
                                    var x = 0f
                                    while (x < size.width) {
                                        val i = ((x + sx) * perPx).toInt()
                                        if (i >= peaks.size) break
                                        val a = peaks[i] * (h / 2 - 4f)
                                        drawLine(Color(0xFF7CC6D8).copy(alpha = dim), Offset(x, mid - a), Offset(x, mid + a), strokeWidth = 1.2f)
                                        x += 1.5f
                                    }
                                } else {
                                    drawLine(Color(0xFF7CC6D8).copy(alpha = 0.5f), Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1f)
                                }
                            }
                        }
                    }
                    is TRow.Property -> {
                        drawRect(if (selected) Ui.SelectedTrack else Ui.SubTrack, Offset(0f, top), Size(size.width, h))
                        val t = l.track(r.prop) ?: return@forEachIndexed
                        val kr = 6.dp.toPx()
                        val cy = top + h / 2
                        t.keys.forEach { k ->
                            val c = Offset(k.frame * cellPx - sx + cellPx / 2, cy)
                            if (c.x < -kr || c.x > size.width + kr) return@forEach
                            val d = Path().apply { moveTo(c.x, c.y - kr); lineTo(c.x + kr, c.y); lineTo(c.x, c.y + kr); lineTo(c.x - kr, c.y); close() }
                            drawPath(d, Color.White)
                        }
                    }
                }
                drawLine(Ui.Timeline, Offset(0f, top + h - 0.5f), Offset(size.width, top + h - 0.5f), strokeWidth = 1f)
            }
            // Past the end of the clip.
            val endX = len * cellPx - sx
            if (endX < size.width) drawRect(Color.Black.copy(alpha = 0.45f), Offset(endX, 0f), Size(size.width - endX, size.height))
            // Current frame column, outlined in teal.
            val px = ed.frame * cellPx - sx
            drawRect(Ui.Teal, Offset(px, 0f), Size(cellPx, size.height), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()))
        }
    }

    markerEdit?.let { m -> MarkerDialog(ed, m) { markerEdit = null } }
    if (rulerMenu >= 0) {
        val f = rulerMenu
        val existing = ed.clip.markers.firstOrNull { f in it.from..it.to }
        ActionSheet("Frame ${f + 1}", { rulerMenu = -1 }, buildList {
            if (existing == null) add("Add Marker" to { ed.addMarker(f) }) else add("Edit Marker" to { markerEdit = existing })
        })
    }
    drawingMenu?.let { (layerId, f) ->
        ActionSheet("Drawing", { drawingMenu = null }, listOf(
            "Duplicate" to { ed.selectLayer(layerId); ed.seek(f); ed.addDrawing(copy = true) },
            "Extend" to { ed.selectLayer(layerId); ed.seek(f); ed.extendDrawing() },
            "Shorten" to { ed.selectLayer(layerId); ed.seek(f); ed.shortenDrawing() },
            "Add Marker Here" to {
                val e = ed.clip.layer(layerId)?.exposureAt(f)
                if (e != null) ed.addMarker(e.start)
            },
            "Delete Drawing" to { ed.selectLayer(layerId); ed.seek(f); ed.deleteDrawing() },
        ))
    }
    layerMenu?.let { l -> LayerMenu(ed, l, onRename = { renaming = l }) { layerMenu = null } }
    renaming?.let { l ->
        var name by remember(l.id) { mutableStateOf(l.name) }
        AppDialog("Rename Layer", { renaming = null }, listOf("Cancel" to { renaming = null }, "Rename" to { ed.renameLayer(l.id, name); renaming = null })) {
            TextInput(name, { name = it }, Modifier.fillMaxWidth())
        }
    }
}

/** Scales [block] (drawn in content space) so [bounds] fits a w×h cell. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.thumb(
    bounds: com.spideranim.model.Bounds?,
    w: Float,
    h: Float,
    block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
) {
    val b = bounds ?: return
    val pad = 2f
    val s = minOf((w - pad * 2) / b.w.coerceAtLeast(1f), (h - pad * 2) / b.h.coerceAtLeast(1f))
    withTransform({
        translate(w / 2f, h / 2f)
        scale(s, s, Offset.Zero)
        translate(-b.cx, -b.cy)
    }) { block() }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.checker(at: Offset, size: Size) {
    drawRect(Color(0xFF3C3C3C), at, size)
    val q = 5.dp.toPx()
    clipRect(at.x, at.y, at.x + size.width, at.y + size.height) {
        var y = 0
        var yy = at.y
        while (yy < at.y + size.height) {
            var x = 0
            var xx = at.x
            while (xx < at.x + size.width) {
                if ((x + y) % 2 == 0) drawRect(Color(0xFF4E4E4E), Offset(xx, yy), Size(q, q))
                xx += q
                x++
            }
            yy += q
            y++
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LayerHeader(ed: Editor, layer: Layer, compact: Boolean, onLongPress: () -> Unit) {
    val selected = ed.selLayerId == layer.id && ed.selProp == null
    Row(
        Modifier
            .fillMaxWidth()
            .height(MainRow)
            .background(if (selected) Ui.Selected else Ui.Header)
            .combinedClickable(onClick = { ed.selectLayer(layer.id) }, onLongClick = onLongPress, onDoubleClick = {
                if (layer.type == LayerType.Symbol) ed.enterSymbol(layer.symbolId) else onLongPress()
            })
            .padding(start = if (compact) 10.dp else 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (layer.type == LayerType.Symbol) {
            Icon(Ic.Symbol, null, tint = Ui.Dim, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            layer.name,
            style = Ui.body.copy(fontSize = if (compact) 14.sp else 17.sp, color = if (layer.visible) Color.White else Ui.Faint),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (layer.locked) Icon(Ic.Lock, "Locked", tint = Ui.Dim, modifier = Modifier.size(16.dp))
        val icon = if (layer.type == LayerType.Audio) (if (layer.visible) Ic.Speaker else Ic.Mute) else (if (layer.visible) Ic.Eye else Ic.EyeOff)
        BarButton(icon, { ed.toggleVisible(layer.id) }, size = 38.dp, iconSize = 24.dp, tint = if (layer.visible) Color.White else Ui.Faint, description = "Visibility")
    }
}

@Composable
private fun PropHeader(ed: Editor, layer: Layer, prop: Prop) {
    val selected = ed.selLayerId == layer.id && ed.selProp == prop
    Row(Modifier.fillMaxWidth().height(PropRow).background(Ui.Timeline).padding(start = 18.dp)) {
        Row(
            Modifier
                .fillMaxSize()
                .background(if (selected) Ui.Selected else Ui.SubHeader)
                .clickable { ed.selectLayer(layer.id, prop) }
                .padding(start = 18.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(prop.label, style = Ui.small.copy(fontSize = 14.sp, color = Color(0xFFD0D0D0)), modifier = Modifier.weight(1f), maxLines = 1)
            BarButton(Ic.Close, { ed.removeTrack(layer.id, prop) }, size = 28.dp, iconSize = 16.dp, tint = Ui.Dim, description = "Remove animation")
        }
    }
}


// ------------------------------------------------------------------ bottom bar

@Composable
private fun BottomBar(ed: Editor, compact: Boolean, onAddAudio: () -> Unit) {
    var addMenu by remember { mutableStateOf(false) }
    val l = ed.selLayer
    val b = if (compact) 40.dp else 46.dp
    Row(
        Modifier.fillMaxWidth().height(if (compact) 46.dp else 54.dp).background(Ui.Bar).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            BarButton(Ic.Plus, { addMenu = true }, size = b, description = "Add")
            ActionMenu(addMenu, { addMenu = false }, buildList {
                add("Drawing Layer" to { ed.addDrawingLayer() })
                add("Symbol…" to { ed.panel = Panel.Library })
                add("Text" to { ed.selectTool(Tool.Text); ed.showHint("Tap the canvas to place text") })
                if (!ed.inSymbol) add("Camera" to { ed.addCameraLayer() })
                if (!ed.inSymbol) add("Audio…" to onAddAudio)
                add("Marker" to { ed.addMarker() })
            })
        }
        if (!compact) Spacer(Modifier.width(150.dp))
        BarButton(Ic.Path, { ed.motionPaths = !ed.motionPaths }, selected = ed.motionPaths, teal = true, size = b, description = "Motion paths")
        BarButton(Ic.Key, { ed.autoKey = !ed.autoKey }, selected = ed.autoKey, teal = true, size = b, description = "Auto keyframe")
        BarButton(Ic.Curve, { ed.panel = if (ed.panel == Panel.Easing) null else Panel.Easing }, selected = ed.panel == Panel.Easing, teal = true, size = b, description = "Easing")
        Spacer(Modifier.weight(1f))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.End) {
            when {
                l == null -> TextAction("Add Layer", { ed.addDrawingLayer() })
                ed.selProp != null -> {
                    TextAction("Add Keyframe", { ed.addKeyframe() })
                    TextAction("Delete Keyframe", { ed.deleteKeyframe() }, enabled = l.track(ed.selProp!!)?.keyAt(ed.frame) != null)
                }
                l.type == LayerType.Drawing -> {
                    TextAction("Add Drawing", { ed.addDrawing() })
                    TextAction("Duplicate", { ed.addDrawing(copy = true) }, enabled = l.exposureAt(ed.frame) != null)
                    TextAction("Extend", { ed.extendDrawing() })
                }
                l.type == LayerType.Symbol -> {
                    TextAction("Edit Symbol", { ed.enterSymbol(l.symbolId) })
                    TextAction("Markers", { ed.panel = Panel.Markers })
                    TextAction("Add Keyframe", { ed.addKeyframe() })
                }
                l.type == LayerType.Audio -> TextAction("Delete Layer", { ed.deleteLayer(l.id) })
                else -> {
                    TextAction("Add Keyframe", { ed.addKeyframe() })
                    TextAction("Delete Keyframe", { ed.deleteKeyframe() })
                }
            }
        }
    }
}

// ------------------------------------------------------------------ menus & dialogs

@Composable
fun ActionSheet(title: String, onDismiss: () -> Unit, items: List<Pair<String, () -> Unit>>) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(300.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF2A2A2A)).padding(vertical = 8.dp)) {
            Text(title, style = Ui.small.copy(color = Ui.Faint), modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            items.forEach { (label, action) ->
                Text(
                    label,
                    style = Ui.body.copy(fontSize = 17.sp, color = if (label.startsWith("Delete")) Ui.Red else Ui.Text),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            action()
                        }
                        .padding(horizontal = 20.dp, vertical = 13.dp),
                )
            }
        }
    }
}

@Composable
private fun LayerMenu(ed: Editor, layer: Layer, onRename: () -> Unit, onDismiss: () -> Unit) {
    ActionSheet(layer.name, onDismiss, buildList {
        add("Rename" to onRename)
        if (layer.type == LayerType.Symbol) add("Edit Symbol" to { ed.enterSymbol(layer.symbolId) })
        if (layer.type == LayerType.Drawing || layer.type == LayerType.Text) add("Convert to Symbol" to { ed.convertToSymbol(layer.id) })
        add((if (layer.locked) "Unlock" else "Lock") to { ed.toggleLock(layer.id) })
        add("Move Up" to { ed.moveLayer(layer.id, up = true) })
        add("Move Down" to { ed.moveLayer(layer.id, up = false) })
        if (layer.type != LayerType.Camera) add("Duplicate Layer" to { ed.duplicateLayer(layer.id) })
        add("Delete Layer" to { ed.deleteLayer(layer.id) })
    })
}

/** Marker comment, colour and From/To frames (ToonSquid's marker editor). */
@Composable
fun MarkerDialog(ed: Editor, m: Marker, onDismiss: () -> Unit) {
    var comment by remember { mutableStateOf(m.comment) }
    var color by remember { mutableIntStateOf(m.color) }
    var from by remember { mutableIntStateOf(m.from) }
    var to by remember { mutableIntStateOf(m.to) }
    val last = ed.clip.length - 1
    AppDialog(
        "Marker",
        onDismiss,
        listOf(
            "Delete" to { ed.deleteMarker(m.id); onDismiss() },
            "Done" to { ed.updateMarker(m.copy(comment = comment.ifBlank { m.comment }, color = color, from = from, to = to)); onDismiss() },
        ),
    ) {
        TextInput(comment, { comment = it.take(40) }, Modifier.fillMaxWidth(), placeholder = "Comment")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MarkerColors.forEach { c -> ColorWell(c, { color = c }, size = 34.dp, selected = c == color) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stepper("From", from, Modifier.weight(1f)) { from = it.coerceIn(0, last); if (to < from) to = from }
            Stepper("To", to, Modifier.weight(1f)) { to = it.coerceIn(from, last) }
        }
    }
}

@Composable
fun Stepper(label: String, value: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    Row(modifier.height(44.dp).clip(RoundedCornerShape(10.dp)).background(Ui.Field), verticalAlignment = Alignment.CenterVertically) {
        BarButton(Ic.Minus, { onChange(value - 1) }, size = 40.dp, iconSize = 16.dp)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = Ui.tiny.copy(color = Ui.Faint))
            Text("${value + 1}", style = Ui.num)
        }
        BarButton(Ic.Plus, { onChange(value + 1) }, size = 40.dp, iconSize = 16.dp)
    }
}
