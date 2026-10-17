package com.spideranim.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.spideranim.editor.EditorState
import com.spideranim.editor.LiveStroke
import com.spideranim.editor.Tool
import com.spideranim.model.Bounds
import com.spideranim.model.Layer
import com.spideranim.model.Project
import com.spideranim.model.Pt
import com.spideranim.model.Stroke
import com.spideranim.model.StrokeKind
import com.spideranim.model.Xform
import com.spideranim.model.bounds
import com.spideranim.model.insidePolygon
import com.spideranim.model.ovalPoints
import com.spideranim.model.rectPoints
import com.spideranim.ui.theme.Ui
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import androidx.compose.ui.graphics.drawscope.Stroke as LineStyle

@Composable
fun Stage(state: EditorState, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val ctrl = remember(state) { StageController(state) }
    ctrl.dp = density
    var size by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(size, state.fitRequest) {
        if (size.width > 0 && size.height > 0) state.fitView(size.width.toFloat(), size.height.toFloat(), 28f * density)
    }

    Canvas(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged {
                size = it
                state.onCanvasSize(it.width.toFloat(), it.height.toFloat())
            }
            .pointerInput(state) { stageGestures(ctrl) },
    ) {
        val project = state.project
        val frame = state.frame
        val vs = state.viewScale
        val off = state.viewOffset
        val w = project.width.toFloat()
        val h = project.height.toFloat()
        val selected = state.selectedLayer
        val live = state.live
        val dp = density

        withTransform({
            translate(off.x, off.y)
            scale(vs, vs, Offset.Zero)
        }) {
            // Soft drop shadow under the artboard.
            for (i in 1..6) {
                val spread = i * 3f * dp / vs
                drawRect(
                    Color.Black.copy(alpha = 0.07f),
                    topLeft = Offset(-spread, -spread + 6f * dp / vs),
                    size = Size(w + spread * 2, h + spread * 2),
                )
            }
            drawRect(Color(project.background), size = Size(w, h))

            clipRect(0f, 0f, w, h) {
                if (state.onion && !state.playing) {
                    val only = selected?.id
                    for (d in 2 downTo 1) {
                        if (frame - d >= 0) drawScene(project, frame - d, only = only, tint = Ui.OnionPrev, alpha = 0.32f / d)
                    }
                    if (frame + 1 < project.length) drawScene(project, frame + 1, only = only, tint = Ui.OnionNext, alpha = 0.3f)
                }
                drawScene(project, frame) { layer ->
                    if (live != null && live.layerId == layer.id) drawMark(live.toStroke(), cached = false)
                    if (state.penLayerId == layer.id && state.penPoints.isNotEmpty()) drawPenPreview(state, vs)
                }
            }

            if (selected != null && selected.visible && !state.playing) {
                drawMotionPath(project, selected, vs, dp)
                drawSelection(state, ctrl, selected, vs, dp)
            }
        }
    }
}

private fun DrawScope.drawPenPreview(state: EditorState, vs: Float) {
    val pts = state.penPoints
    val xfScale = state.selectedLayer?.poseAt(state.frame)?.xf?.avgScale ?: 1f
    val unit = 1f / (vs * xfScale)
    val path = Path()
    pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
    drawPath(path, Color(state.strokeColor), style = LineStyle(width = state.shapeWidth / xfScale, cap = StrokeCap.Round))
    pts.forEachIndexed { i, p ->
        drawCircle(Color.White, radius = 5f * unit * 2f, center = Offset(p.x, p.y))
        drawCircle(if (i == 0) Ui.Accent else Ui.Bg2, radius = 3.5f * unit * 2f, center = Offset(p.x, p.y))
    }
}

private fun DrawScope.drawMotionPath(project: Project, layer: Layer, vs: Float, dp: Float) {
    if (layer.keyframes.size < 2) return
    val pts = List(project.length) { layer.poseAt(it).xf }
    if (pts.all { abs(it.x - pts[0].x) < 0.5f && abs(it.y - pts[0].y) < 0.5f }) return
    val unit = dp / vs
    val path = Path()
    pts.forEachIndexed { i, xf -> if (i == 0) path.moveTo(xf.x, xf.y) else path.lineTo(xf.x, xf.y) }
    drawPath(path, Ui.Accent.copy(alpha = 0.45f), style = LineStyle(width = 1.2f * unit, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * unit, 4f * unit))))
    pts.forEach { drawCircle(Ui.Accent.copy(alpha = 0.6f), radius = 1.6f * unit, center = Offset(it.x, it.y)) }
    layer.keyframes.forEach { k ->
        if (k.frame < project.length) {
            drawCircle(Color.White, radius = 4.5f * unit, center = Offset(k.xf.x, k.xf.y))
            drawCircle(Ui.Accent, radius = 3f * unit, center = Offset(k.xf.x, k.xf.y))
        }
    }
}

private fun DrawScope.drawSelection(state: EditorState, ctrl: StageController, layer: Layer, vs: Float, dp: Float) {
    val xf = layer.poseAt(state.frame).xf
    val unit = dp / vs
    // Registration point, like Animate's crosshair.
    val c = Offset(xf.x, xf.y)
    drawLine(Ui.Accent, c - Offset(6f * unit, 0f), c + Offset(6f * unit, 0f), strokeWidth = 1.2f * unit)
    drawLine(Ui.Accent, c - Offset(0f, 6f * unit), c + Offset(0f, 6f * unit), strokeWidth = 1.2f * unit)

    if (state.tool != Tool.Select && state.tool != Tool.Transform) return
    val b = state.project.drawingOf(layer, state.frame)?.bounds() ?: return
    val corners = boxCorners(b).map { xf.toStage(it).offset() }
    val path = Path().apply {
        moveTo(corners[0].x, corners[0].y)
        corners.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(path, Ui.Accent, style = LineStyle(width = 1.4f * unit))

    if (state.tool == Tool.Transform) {
        val handles = ctrl.handles(layer, inScreen = false)
        val rot = handles.first { it.first == Handle.Rotate }.second
        val top = handles.first { it.first == Handle.T }.second
        drawLine(Ui.Accent, top, rot, strokeWidth = 1.2f * unit)
        handles.forEach { (h, p) ->
            if (h == Handle.Rotate) {
                drawCircle(Color.White, radius = 6f * unit, center = p)
                drawCircle(Ui.Accent, radius = 6f * unit, center = p, style = LineStyle(width = 1.6f * unit))
            } else if (h != Handle.Move) {
                val s = 7f * unit
                drawRect(Color.White, topLeft = p - Offset(s / 2, s / 2), size = Size(s, s))
                drawRect(Ui.Accent, topLeft = p - Offset(s / 2, s / 2), size = Size(s, s), style = LineStyle(width = 1.4f * unit))
            }
        }
    }
}

private fun boxCorners(b: Bounds) = listOf(Pt(b.left, b.top), Pt(b.right, b.top), Pt(b.right, b.bottom), Pt(b.left, b.bottom))
private fun Pt.offset() = Offset(x, y)

enum class Handle { TL, T, TR, R, BR, B, BL, L, Rotate, Move }

private fun pressureOf(c: PointerInputChange): Float =
    if (c.type == PointerType.Stylus) c.pressure.coerceIn(0.05f, 1f) else 1f

/**
 * One finger runs the active tool. Two fingers pan and pinch-zoom the stage; a quick two-finger
 * tap undoes and a three-finger tap redoes.
 */
private suspend fun PointerInputScope.stageGestures(ctrl: StageController) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        val t0 = down.uptimeMillis
        var tEnd = t0
        var multi = false
        var maxPointers = 1
        var travel = 0f
        ctrl.begin(down.position, pressureOf(down))
        while (true) {
            val ev = awaitPointerEvent()
            val pressed = ev.changes.filter { it.pressed }
            ev.changes.firstOrNull()?.let { tEnd = it.uptimeMillis }
            if (pressed.isEmpty()) break
            maxPointers = max(maxPointers, pressed.size)
            if (pressed.size >= 2) {
                if (!multi) {
                    multi = true
                    ctrl.cancel()
                }
                val zoom = ev.calculateZoom()
                val pan = ev.calculatePan()
                travel += pan.getDistance() + abs(zoom - 1f) * 200f
                ctrl.state.panZoom(pan, zoom, ev.calculateCentroid(useCurrent = true))
            } else if (!multi) {
                val ch = pressed.firstOrNull { it.id == down.id } ?: pressed[0]
                ctrl.move(ch.position, pressureOf(ch))
            }
            ev.changes.forEach { it.consume() }
        }
        if (multi) {
            if (tEnd - t0 < 300 && travel < 30f * ctrl.dp) {
                if (maxPointers >= 3) ctrl.state.redo() else ctrl.state.undo()
            }
        } else {
            ctrl.end()
        }
    }
}

class StageController(val state: EditorState) {
    var dp = 1f

    private enum class Mode { None, Draw, Shape, Erase, Tap, Dropper, Move, Scale, Rotate, Pan }

    private var mode = Mode.None
    private var startScreen = Offset.Zero
    private var lastScreen = Offset.Zero
    private var travel = 0f
    private var layerId: Long? = null
    private var xf0 = Xform()
    private var shapeStart = Pt(0f, 0f)
    private var lastLocal = Pt(0f, 0f)
    private var handle = Handle.Move
    private var snapshot: Project? = null
    private var pushed = false

    private fun toStage(s: Offset) = Pt((s.x - state.viewOffset.x) / state.viewScale, (s.y - state.viewOffset.y) / state.viewScale)
    private fun toScreen(p: Pt) = Offset(p.x * state.viewScale + state.viewOffset.x, p.y * state.viewScale + state.viewOffset.y)

    /** Handle positions, in screen space for hit testing or stage space for drawing. */
    fun handles(layer: Layer, inScreen: Boolean): List<Pair<Handle, Offset>> {
        val xf = layer.poseAt(state.frame).xf
        val b = state.project.drawingOf(layer, state.frame)?.bounds() ?: return emptyList()
        val local = listOf(
            Handle.TL to Pt(b.left, b.top), Handle.T to Pt(b.cx, b.top), Handle.TR to Pt(b.right, b.top),
            Handle.R to Pt(b.right, b.cy), Handle.BR to Pt(b.right, b.bottom), Handle.B to Pt(b.cx, b.bottom),
            Handle.BL to Pt(b.left, b.bottom), Handle.L to Pt(b.left, b.cy),
        )
        val stage = local.map { (h, p) -> h to xf.toStage(p) }
        val top = stage[1].second
        val center = xf.toStage(Pt(b.cx, b.cy))
        val dx = top.x - center.x
        val dy = top.y - center.y
        val len = hypot(dx, dy).coerceAtLeast(1e-3f)
        val reach = 26f * dp / state.viewScale
        val rot = Pt(top.x + dx / len * reach, top.y + dy / len * reach)
        val all = stage + (Handle.Rotate to rot)
        return all.map { (h, p) -> h to if (inScreen) toScreen(p) else p.offset() }
    }

    private fun hitHandle(layer: Layer, screen: Offset): Handle? {
        val hs = handles(layer, inScreen = true)
        val near = hs.minByOrNull { (it.second - screen).getDistance() }
        if (near != null && (near.second - screen).getDistance() <= 22f * dp) return near.first
        val xf = layer.poseAt(state.frame).xf
        val b = state.project.drawingOf(layer, state.frame)?.bounds() ?: return null
        val corners = boxCorners(b).map { xf.toStage(it) }
        return if (insidePolygon(corners, toStage(screen).x, toStage(screen).y)) Handle.Move else null
    }

    private fun beginSnapshot() {
        snapshot = state.project
        pushed = false
    }

    private fun ensurePushed() {
        val s = snapshot ?: return
        if (!pushed) {
            state.pushHistory(s)
            pushed = true
        }
    }

    fun begin(pos: Offset, pressure: Float) {
        startScreen = pos
        lastScreen = pos
        travel = 0f
        mode = Mode.None
        val stage = toStage(pos)
        when (val tool = state.tool) {
            Tool.Brush, Tool.Pencil -> {
                val l = state.drawableLayer() ?: return
                xf0 = l.poseAt(state.frame).xf
                val size = if (tool == Tool.Brush) state.brushSize else state.pencilSize
                val kind = if (tool == Tool.Brush) StrokeKind.Brush else StrokeKind.Pencil
                val ls = LiveStroke(l.id, kind, state.strokeColor, size / xf0.avgScale, null, false)
                val p = xf0.toLocal(stage)
                ls.points.add(Pt(p.x, p.y, if (state.pressure) pressure else 1f))
                state.live = ls
                layerId = l.id
                mode = Mode.Draw
            }
            Tool.Line, Tool.Rect, Tool.Oval -> {
                val l = state.drawableLayer() ?: return
                xf0 = l.poseAt(state.frame).xf
                val kind = when (tool) {
                    Tool.Line -> StrokeKind.Line
                    Tool.Rect -> StrokeKind.Rect
                    else -> StrokeKind.Oval
                }
                val closed = kind != StrokeKind.Line
                state.live = LiveStroke(
                    l.id, kind, state.strokeColor, state.shapeWidth / xf0.avgScale,
                    if (closed && state.shapeFill) state.fillColor else null, closed,
                )
                shapeStart = xf0.toLocal(stage)
                layerId = l.id
                mode = Mode.Shape
            }
            Tool.Eraser -> {
                val l = state.drawableLayer() ?: return
                xf0 = l.poseAt(state.frame).xf
                layerId = l.id
                beginSnapshot()
                lastLocal = xf0.toLocal(stage)
                mode = Mode.Erase
                eraseAt(lastLocal)
            }
            Tool.Pen, Tool.Bucket, Tool.Zoom -> mode = Mode.Tap
            Tool.Dropper -> {
                mode = Mode.Dropper
                state.sampleAt(stage, 6f * dp / state.viewScale)
            }
            Tool.Hand -> mode = Mode.Pan
            Tool.Select, Tool.Transform -> beginSelect(pos, stage, tool == Tool.Transform)
        }
    }

    private fun beginSelect(pos: Offset, stage: Pt, transform: Boolean) {
        val current = state.selectedLayer
        if (transform && current != null && current.visible && !current.locked) {
            val h = hitHandle(current, pos)
            if (h != null) {
                startTransform(current, h)
                return
            }
        }
        val hit = state.layerAt(stage, 8f * dp / state.viewScale)
        if (hit == null) {
            mode = Mode.Tap
            return
        }
        state.selectLayer(hit.id)
        startTransform(hit, Handle.Move)
    }

    private fun startTransform(layer: Layer, h: Handle) {
        layerId = layer.id
        xf0 = layer.poseAt(state.frame).xf
        handle = h
        beginSnapshot()
        mode = when (h) {
            Handle.Move -> Mode.Move
            Handle.Rotate -> Mode.Rotate
            else -> Mode.Scale
        }
    }

    fun move(pos: Offset, pressure: Float) {
        travel += (pos - lastScreen).getDistance()
        val stage = toStage(pos)
        when (mode) {
            Mode.Draw -> {
                val ls = state.live ?: return
                val p = xf0.toLocal(stage)
                val last = ls.points.last()
                val minStep = 0.8f * dp / (state.viewScale * xf0.avgScale)
                if (hypot(p.x - last.x, p.y - last.y) < minStep) return
                val k = 1f - state.smoothing * 0.82f
                val pr = if (state.pressure) pressure else 1f
                ls.points.add(Pt(last.x + (p.x - last.x) * k, last.y + (p.y - last.y) * k, last.p + (pr - last.p) * 0.5f))
            }
            Mode.Shape -> {
                val ls = state.live ?: return
                val p = xf0.toLocal(stage)
                val pts = when (ls.kind) {
                    StrokeKind.Line -> listOf(shapeStart, p)
                    StrokeKind.Rect -> rectPoints(shapeStart.x, shapeStart.y, p.x, p.y)
                    else -> ovalPoints(shapeStart.x, shapeStart.y, p.x, p.y)
                }
                ls.points.clear()
                ls.points.addAll(pts)
            }
            Mode.Erase -> {
                val p = xf0.toLocal(stage)
                val step = (state.eraserSize / xf0.avgScale) * 0.35f
                val d = hypot(p.x - lastLocal.x, p.y - lastLocal.y)
                val n = (d / step.coerceAtLeast(0.5f)).toInt().coerceAtMost(64)
                for (i in 1..n) {
                    val t = i / (n + 1f)
                    eraseAt(Pt(lastLocal.x + (p.x - lastLocal.x) * t, lastLocal.y + (p.y - lastLocal.y) * t))
                }
                eraseAt(p)
                lastLocal = p
            }
            Mode.Dropper -> state.sampleAt(stage, 6f * dp / state.viewScale)
            Mode.Move -> {
                if (travel < 4f * dp && !pushed) return
                ensurePushed()
                val s0 = toStage(startScreen)
                state.setXform(layerId ?: return, xf0.copy(x = xf0.x + stage.x - s0.x, y = xf0.y + stage.y - s0.y))
            }
            Mode.Scale -> {
                ensurePushed()
                val u0 = xf0.toUnscaled(toStage(startScreen))
                val u = xf0.toUnscaled(stage)
                var sx = xf0.sx
                var sy = xf0.sy
                when (handle) {
                    Handle.L, Handle.R -> if (abs(u0.x) > 1e-3f) sx = xf0.sx * u.x / u0.x
                    Handle.T, Handle.B -> if (abs(u0.y) > 1e-3f) sy = xf0.sy * u.y / u0.y
                    else -> {
                        val d0 = hypot(u0.x, u0.y)
                        if (d0 > 1e-3f) {
                            val f = hypot(u.x, u.y) / d0
                            sx = xf0.sx * f
                            sy = xf0.sy * f
                        }
                    }
                }
                state.setXform(layerId ?: return, xf0.copy(sx = clampScale(sx), sy = clampScale(sy)))
            }
            Mode.Rotate -> {
                ensurePushed()
                val s0 = toStage(startScreen)
                val a0 = atan2(s0.y - xf0.y, s0.x - xf0.x)
                val a = atan2(stage.y - xf0.y, stage.x - xf0.x)
                state.setXform(layerId ?: return, xf0.copy(rot = xf0.rot + Math.toDegrees((a - a0).toDouble()).toFloat()))
            }
            Mode.Pan -> state.viewOffset = state.viewOffset + (pos - lastScreen)
            Mode.Tap, Mode.None -> Unit
        }
        lastScreen = pos
    }

    private fun clampScale(v: Float) = if (abs(v) < 0.02f) (if (v < 0) -0.02f else 0.02f) else v.coerceIn(-50f, 50f)

    private fun eraseAt(local: Pt) {
        val id = layerId ?: return
        val r = state.eraserSize / 2f / xf0.avgScale
        // Push the pre-gesture snapshot only once something is actually erased.
        if (state.eraseAt(id, local, r) && !pushed) {
            snapshot?.let { state.pushHistory(it) }
            pushed = true
        }
    }

    fun end() {
        val stage = toStage(lastScreen)
        val tapped = travel < 10f * dp
        when (mode) {
            Mode.Draw -> {
                val ls = state.live
                val id = layerId
                if (ls != null && id != null) {
                    val p = xf0.toLocal(stage)
                    val last = ls.points.last()
                    if (hypot(p.x - last.x, p.y - last.y) > 0.5f) ls.points.add(Pt(p.x, p.y, last.p))
                    if (ls.points.size == 1) ls.points.add(Pt(last.x + 0.05f, last.y, last.p))
                    state.addStroke(id, ls.toStroke())
                }
                state.live = null
            }
            Mode.Shape -> {
                val ls = state.live
                val id = layerId
                if (ls != null && id != null && ls.points.size >= 2 && !tapped) state.addStroke(id, ls.toStroke())
                state.live = null
            }
            Mode.Tap -> if (tapped) when (state.tool) {
                Tool.Bucket -> state.fillAt(stage)
                Tool.Zoom -> state.zoomAt(lastScreen, if (state.zoomOut) 1f / 1.6f else 1.6f)
                Tool.Pen -> {
                    val l = state.drawableLayer()
                    if (l != null) {
                        val xf = l.poseAt(state.frame).xf
                        state.penTap(l, xf.toLocal(stage), 14f * dp / (state.viewScale * xf.avgScale))
                    }
                }
                Tool.Select, Tool.Transform -> state.selectLayer(null)
                else -> Unit
            }
            else -> Unit
        }
        mode = Mode.None
        snapshot = null
        pushed = false
    }

    /** A second finger landed: drop whatever the first finger started. */
    fun cancel() {
        when (mode) {
            Mode.Draw, Mode.Shape -> state.live = null
            Mode.Erase, Mode.Move, Mode.Scale, Mode.Rotate -> snapshot?.let { state.revertGesture(it, pushed) }
            else -> Unit
        }
        mode = Mode.None
        snapshot = null
        pushed = false
    }
}
