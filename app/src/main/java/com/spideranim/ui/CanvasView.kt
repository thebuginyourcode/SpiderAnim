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
import androidx.compose.runtime.mutableIntStateOf
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
import com.spideranim.editor.Editor
import com.spideranim.editor.LiveStroke
import com.spideranim.editor.TextPrompt
import com.spideranim.editor.Tool
import com.spideranim.model.Bounds
import com.spideranim.model.Layer
import com.spideranim.model.LayerType
import com.spideranim.model.Project
import com.spideranim.model.Prop
import com.spideranim.model.Pt
import com.spideranim.model.Xf
import com.spideranim.model.contentBounds
import com.spideranim.model.insidePolygon
import com.spideranim.model.strokeBounds
import com.spideranim.ui.theme.Ui
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import androidx.compose.ui.graphics.drawscope.Stroke as LineStyle

private val BoxColor = Color(0xFF3A3A3A)

@Composable
fun CanvasView(ed: Editor, modifier: Modifier = Modifier) {
    val dp = LocalDensity.current.density
    val ctrl = remember(ed) { CanvasController(ed) }
    ctrl.dp = dp
    var size by remember { mutableStateOf(IntSize.Zero) }
    var fitted by remember { mutableIntStateOf(-1) }
    // Fit only when asked (opening, entering a symbol, the fit button); panels opening or closing
    // just keep the stage centred where it was instead of re-zooming.
    LaunchedEffect(size, ed.fitRequest) {
        if (size.width > 0 && size.height > 0 && fitted != ed.fitRequest) {
            ed.fitView(size.width.toFloat(), size.height.toFloat(), 0f)
            fitted = ed.fitRequest
        }
    }

    Canvas(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { n ->
                if (size != IntSize.Zero && fitted == ed.fitRequest) {
                    ed.viewOffset += Offset((n.width - size.width) / 2f, (n.height - size.height) / 2f)
                }
                size = n
            }
            .pointerInput(ed) { canvasGestures(ctrl) },
    ) {
        val p = ed.project
        val clip = ed.clip
        val frame = ed.frame
        val vs = ed.viewScale
        val off = ed.viewOffset
        val w = p.width.toFloat()
        val h = p.height.toFloat()
        val sel = ed.selLayer
        val live = ed.live

        drawRect(Ui.Workspace)
        withTransform({
            translate(off.x, off.y)
            scale(vs, vs, Offset.Zero)
        }) {
            drawRect(Color(p.background), size = Size(w, h))
            clipRect(0f, 0f, w, h) {
                clipSpace(ed) {
                    if (ed.onion && !ed.playing && sel != null && sel.type == LayerType.Drawing) drawOnion(p, sel, frame)
                    drawClip(p, clip, frame, RenderOptions(inLayer = { layer ->
                        if (live != null && live.layerId == layer.id) drawMark(live.toStroke(), cached = false)
                        if (ed.penLayerId == layer.id && ed.penPoints.isNotEmpty()) drawPolyPreview(ed.penPoints, Color(ed.strokeColor), ed.penWidth / layer.xfAt(frame).avgScale)
                    }))
                    if (ed.inSymbol) {
                        // The symbol's origin.
                        val r = 14f / vs
                        drawLine(Ui.Teal, Offset(-r, 0f), Offset(r, 0f), strokeWidth = 1.5f / vs)
                        drawLine(Ui.Teal, Offset(0f, -r), Offset(0f, r), strokeWidth = 1.5f / vs)
                    }
                }
            }
        }
        if (!ed.playing && !ed.preview) {
            if (sel != null && ed.motionPaths) drawMotionPath(ed, ctrl, sel, dp)
            if (sel != null && sel.visible && (ed.tool == Tool.Select)) drawSelection(ed, ctrl, sel, dp)
            drawLasso(ed, ctrl, dp)
            ctrl.guide?.let { g ->
                val a = ctrl.screenOf(g.first)
                val b = ctrl.screenOf(g.second)
                drawLine(Ui.Teal, a, b, strokeWidth = 1f * dp, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * dp, 4f * dp)))
            }
        }
    }
}

/** Switches from view (stage) space into the space of the clip being edited. */
private fun DrawScope.clipSpace(ed: Editor, block: DrawScope.() -> Unit) {
    if (ed.inSymbol) {
        withTransform({ translate(ed.project.width / 2f, ed.project.height / 2f) }, block)
    } else {
        withCamera(ed.project.scene, ed.frame, block)
    }
}

private fun DrawScope.drawOnion(p: Project, layer: Layer, frame: Int) {
    val cur = layer.exposureAt(frame)
    val prev = layer.exposures.lastOrNull { it.end <= (cur?.start ?: frame) }
    val next = layer.exposures.firstOrNull { it.start >= (cur?.end ?: (frame + 1)) }
    withXf(layer.xfAt(frame)) {
        prev?.strokes?.forEach { drawMark(it, Ui.OnionPrev, 0.35f) }
        next?.strokes?.forEach { drawMark(it, Ui.OnionNext, 0.35f) }
    }
}

private fun DrawScope.drawPolyPreview(pts: List<Pt>, color: Color, width: Float) {
    val path = Path()
    pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
    drawPath(path, color, style = LineStyle(width = width, cap = StrokeCap.Round))
    pts.forEachIndexed { i, p ->
        drawCircle(Color.White, radius = width * 0.9f + 6f, center = Offset(p.x, p.y))
        drawCircle(if (i == 0) Ui.Teal else BoxColor, radius = width * 0.6f + 4f, center = Offset(p.x, p.y))
    }
}

private fun DrawScope.drawMotionPath(ed: Editor, ctrl: CanvasController, layer: Layer, dp: Float) {
    val t = layer.track(Prop.Position) ?: return
    if (t.keys.size < 2) return
    val pts = (0 until ed.clip.length).map { ctrl.screenOf(Pt(layer.value(Prop.Position, it).a, layer.value(Prop.Position, it).b)) }
    pts.forEach { drawCircle(Color(0xFFB8B8B8), radius = 3.5f * dp, center = it) }
    t.keys.forEach { k ->
        if (k.frame >= ed.clip.length) return@forEach
        val c = ctrl.screenOf(Pt(k.v.a, k.v.b))
        val r = 7f * dp
        val d = Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close() }
        drawPath(d, Color.White)
        drawPath(d, Color.Black, style = LineStyle(width = 1.6f * dp))
    }
}

private fun DrawScope.drawSelection(ed: Editor, ctrl: CanvasController, layer: Layer, dp: Float) {
    if (layer.type == LayerType.Audio) return
    val hs = ctrl.handles(layer) ?: return
    val corners = listOf(Handle.TL, Handle.TR, Handle.BR, Handle.BL).map { h -> hs.first { it.first == h }.second }
    val box = Path().apply {
        moveTo(corners[0].x, corners[0].y)
        corners.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(box, BoxColor, style = LineStyle(width = 1.5f * dp))
    val b = hs.first { it.first == Handle.B }.second
    val rot = hs.first { it.first == Handle.Rotate }.second
    drawLine(BoxColor, b, rot, strokeWidth = 1.5f * dp)
    hs.forEach { (h, p) ->
        if (h == Handle.Move) return@forEach
        drawCircle(Color.White, radius = 7f * dp, center = p)
        drawCircle(BoxColor, radius = 7f * dp, center = p, style = LineStyle(width = 1.8f * dp))
    }
}

private fun DrawScope.drawLasso(ed: Editor, ctrl: CanvasController, dp: Float) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(6f * dp, 5f * dp))
    if (ed.lassoPath.size > 1) {
        val path = Path()
        ed.lassoPath.forEachIndexed { i, p -> val s = ctrl.screenOf(p); if (i == 0) path.moveTo(s.x, s.y) else path.lineTo(s.x, s.y) }
        path.close()
        drawPath(path, Ui.Teal.copy(alpha = 0.12f))
        drawPath(path, Ui.Teal, style = LineStyle(width = 1.5f * dp, pathEffect = dash))
    }
    val sel = ed.lasso ?: return
    val layer = ed.clip.layer(sel.layerId) ?: return
    val b = strokeBounds(ed.lassoStrokes()) ?: return
    val xf = layer.xfAt(ed.frame)
    val pts = b.corners.map { ctrl.screenOf(xf.toStage(it)) }
    val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close() }
    drawPath(path, Ui.Teal, style = LineStyle(width = 1.5f * dp, pathEffect = dash))
}

enum class Handle { TL, T, TR, R, BR, B, BL, L, Rotate, Move }

private fun pressureOf(c: PointerInputChange): Float =
    if (c.type == PointerType.Stylus) c.pressure.coerceIn(0.05f, 1f) else 1f

/**
 * One finger runs the tool. Two fingers pan and pinch-zoom; a quick two-finger tap undoes, a
 * three-finger tap redoes.
 */
private suspend fun PointerInputScope.canvasGestures(ctrl: CanvasController) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        val t0 = down.uptimeMillis
        var tEnd = t0
        var multi = false
        var maxPointers = 1
        var travel = 0f
        ctrl.begin(down.position, pressureOf(down), t0)
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
                ctrl.ed.panZoom(pan, zoom, ev.calculateCentroid(useCurrent = true))
            } else if (!multi) {
                val ch = pressed.firstOrNull { it.id == down.id } ?: pressed[0]
                ctrl.move(ch.position, pressureOf(ch))
            }
            ev.changes.forEach { it.consume() }
        }
        if (multi) {
            if (tEnd - t0 < 300 && travel < 30f * ctrl.dp) {
                if (maxPointers >= 3) ctrl.ed.redo() else ctrl.ed.undo()
            }
        } else {
            ctrl.end(tEnd)
        }
    }
}

class CanvasController(val ed: Editor) {
    var dp = 1f

    private enum class Mode { None, Draw, Erase, Tap, SymbolTap, Dropper, Move, Scale, Rotate, Lasso, LassoMove }

    private var mode = Mode.None
    private var startScreen = Offset.Zero
    private var lastScreen = Offset.Zero
    private var travel = 0f
    private var layerId: Long? = null
    private var xf0 = Xf()
    private var lastLocal = Pt(0f, 0f)
    private var handle = Handle.Move
    private var center0 = Pt(0f, 0f)
    private var centerLocal = Pt(0f, 0f)
    private var snapshot: Project? = null
    private var pushed = false
    private var lastTapTime = 0L
    private var lastTapPos = Offset.Zero

    /** A snapping guide line, in clip space, while dragging. */
    var guide by mutableStateOf<Pair<Pt, Pt>?>(null)
        private set

    private fun viewOf(s: Offset) = Pt((s.x - ed.viewOffset.x) / ed.viewScale, (s.y - ed.viewOffset.y) / ed.viewScale)
    fun clipOf(s: Offset): Pt = ed.viewToClip(viewOf(s))
    fun screenOf(clipPt: Pt): Offset {
        val v = ed.clipToView(clipPt)
        return Offset(v.x * ed.viewScale + ed.viewOffset.x, v.y * ed.viewScale + ed.viewOffset.y)
    }

    /** Clip units per screen pixel. */
    private fun unit(): Float {
        val a = clipOf(Offset.Zero)
        val b = clipOf(Offset(100f, 0f))
        return hypot(b.x - a.x, b.y - a.y) / 100f
    }

    private fun boundsOfLayer(layer: Layer): Bounds? {
        if (layer.type == LayerType.Camera) return null
        return ed.project.contentBounds(layer, ed.frame)
    }

    /** Handles in screen space: corners, sides, the rotate knob below, and the box itself. */
    fun handles(layer: Layer): List<Pair<Handle, Offset>>? {
        val b = boundsOfLayer(layer) ?: return null
        val xf = layer.xfAt(ed.frame)
        val pad = 10f * dp * unit() / xf.avgScale
        val l = b.left - pad
        val t = b.top - pad
        val r = b.right + pad
        val bt = b.bottom + pad
        val cx = (l + r) / 2
        val cy = (t + bt) / 2
        val local = listOf(
            Handle.TL to Pt(l, t), Handle.T to Pt(cx, t), Handle.TR to Pt(r, t), Handle.R to Pt(r, cy),
            Handle.BR to Pt(r, bt), Handle.B to Pt(cx, bt), Handle.BL to Pt(l, bt), Handle.L to Pt(l, cy),
        )
        val screen = local.map { (h, p) -> h to screenOf(xf.toStage(p)) }
        val bottom = screen[5].second
        val mid = screenOf(xf.toStage(Pt(cx, cy)))
        val dx = bottom.x - mid.x
        val dy = bottom.y - mid.y
        val len = hypot(dx, dy).coerceAtLeast(1e-3f)
        val knob = Offset(bottom.x + dx / len * 30f * dp, bottom.y + dy / len * 30f * dp)
        return screen + (Handle.Rotate to knob)
    }

    private fun hitHandle(layer: Layer, s: Offset): Handle? {
        val hs = handles(layer) ?: return null
        val near = hs.minByOrNull { (it.second - s).getDistance() }
        if (near != null && (near.second - s).getDistance() <= 24f * dp) return near.first
        val corners = listOf(Handle.TL, Handle.TR, Handle.BR, Handle.BL).map { h -> hs.first { it.first == h }.second }
        return if (insidePolygon(corners.map { Pt(it.x, it.y) }, s.x, s.y)) Handle.Move else null
    }

    private fun beginSnapshot() {
        snapshot = ed.project
        pushed = false
    }

    private fun ensurePushed() {
        val s = snapshot ?: return
        if (!pushed) {
            ed.pushHistory(s)
            pushed = true
        }
    }

    fun begin(pos: Offset, pressure: Float, time: Long) {
        startScreen = pos
        lastScreen = pos
        travel = 0f
        mode = Mode.None
        guide = null
        val c = clipOf(pos)
        val sel = ed.selLayer
        if (ed.tool != Tool.Select && sel != null && sel.type == LayerType.Symbol) {
            // Drawing tools on a symbol layer: double-tap goes inside the symbol to draw there.
            if (time - lastTapTime < 400 && (pos - lastTapPos).getDistance() < 40f * dp) {
                lastTapTime = 0
                ed.enterSymbol(sel.symbolId)
                return
            }
            ed.showHint("Double-tap to edit ${sel.name}")
            mode = Mode.SymbolTap
            return
        }
        when (ed.tool) {
            Tool.Brush -> {
                val l = ed.drawTarget() ?: return
                xf0 = l.xfAt(ed.frame)
                val ls = LiveStroke(l.id, ed.brushStyle.kind, ed.inkColor, ed.brushSize / xf0.avgScale)
                val p = xf0.toLocal(c)
                ls.points.add(Pt(p.x, p.y, if (ed.pressure) pressure else 1f))
                ed.live = ls
                layerId = l.id
                mode = Mode.Draw
            }
            Tool.Eraser -> {
                val l = ed.drawTarget() ?: return
                xf0 = l.xfAt(ed.frame)
                layerId = l.id
                beginSnapshot()
                lastLocal = xf0.toLocal(c)
                mode = Mode.Erase
                erase(lastLocal)
            }
            Tool.Fill, Tool.Pen, Tool.Text -> mode = Mode.Tap
            Tool.Eyedropper -> {
                mode = Mode.Dropper
                ed.sampleAt(c, 6f * dp * unit())
            }
            Tool.Lasso -> {
                val l = ed.selLayer
                val sel = ed.lasso
                if (l != null && sel != null && sel.layerId == l.id) {
                    val b = strokeBounds(ed.lassoStrokes())
                    val local = l.xfAt(ed.frame).toLocal(c)
                    if (b != null && b.contains(local.x, local.y, 12f * dp * unit())) {
                        xf0 = l.xfAt(ed.frame)
                        lastLocal = local
                        layerId = l.id
                        beginSnapshot()
                        mode = Mode.LassoMove
                        return
                    }
                }
                ed.lasso = null
                ed.lassoPath.clear()
                ed.lassoPath.add(c)
                mode = Mode.Lasso
            }
            Tool.Select -> beginSelect(pos, c, time)
        }
    }

    private fun beginSelect(pos: Offset, c: Pt, time: Long) {
        val cur = ed.selLayer
        // Double-tap a symbol to go inside it.
        if (time - lastTapTime < 350 && (pos - lastTapPos).getDistance() < 30f * dp) {
            val hit = ed.layerAt(c, 8f * dp * unit())
            if (hit != null && hit.type == LayerType.Symbol) {
                lastTapTime = 0
                ed.enterSymbol(hit.symbolId)
                return
            }
            if (hit != null && hit.type == LayerType.Text) {
                lastTapTime = 0
                ed.textPrompt = TextPrompt(c, hit.id, hit.text)
                return
            }
        }
        if (cur != null && cur.visible && !cur.locked) {
            if (cur.type == LayerType.Camera) {
                startTransform(cur, Handle.Move)
                return
            }
            val h = hitHandle(cur, pos)
            if (h != null) {
                startTransform(cur, h)
                return
            }
        }
        val hit = ed.layerAt(c, 8f * dp * unit())
        if (hit == null) {
            mode = Mode.Tap
            return
        }
        ed.selectLayer(hit.id)
        startTransform(hit, Handle.Move)
    }

    private fun startTransform(layer: Layer, h: Handle) {
        layerId = layer.id
        xf0 = layer.xfAt(ed.frame)
        handle = h
        val b = boundsOfLayer(layer)
        centerLocal = if (b != null) Pt(b.cx, b.cy) else Pt(xf0.ax, xf0.ay)
        center0 = xf0.toStage(centerLocal)
        beginSnapshot()
        mode = when (h) {
            Handle.Move -> Mode.Move
            Handle.Rotate -> Mode.Rotate
            else -> Mode.Scale
        }
    }

    /** Position that keeps the art's centre fixed under a new rotation/scale. */
    private fun pinned(xf: Xf): Xf {
        val off = xf.copy(x = 0f, y = 0f).toStage(centerLocal)
        return xf.copy(x = center0.x - off.x, y = center0.y - off.y)
    }

    fun move(pos: Offset, pressure: Float) {
        travel += (pos - lastScreen).getDistance()
        lastScreen = pos
        val c = clipOf(pos)
        when (mode) {
            Mode.Draw -> {
                val ls = ed.live ?: return
                val p = xf0.toLocal(c)
                val last = ls.points.last()
                val minStep = 1f * dp * unit() / xf0.avgScale
                if (hypot(p.x - last.x, p.y - last.y) < minStep) return
                val k = 1f - ed.smoothing * 0.82f
                val pr = if (ed.pressure) pressure else 1f
                ls.points.add(Pt(last.x + (p.x - last.x) * k, last.y + (p.y - last.y) * k, last.p + (pr - last.p) * 0.5f))
            }
            Mode.Erase -> {
                val p = xf0.toLocal(c)
                val step = (ed.eraserSize / xf0.avgScale) * 0.35f
                val d = hypot(p.x - lastLocal.x, p.y - lastLocal.y)
                val n = (d / step.coerceAtLeast(0.5f)).toInt().coerceAtMost(64)
                for (i in 1..n) {
                    val t = i / (n + 1f)
                    erase(Pt(lastLocal.x + (p.x - lastLocal.x) * t, lastLocal.y + (p.y - lastLocal.y) * t))
                }
                erase(p)
                lastLocal = p
            }
            Mode.Dropper -> ed.sampleAt(c, 6f * dp * unit())
            Mode.Lasso -> ed.lassoPath.add(c)
            Mode.LassoMove -> {
                ensurePushed()
                val l = ed.clip.layer(layerId) ?: return
                val p = l.xfAt(ed.frame).toLocal(c)
                ed.moveLasso(p.x - lastLocal.x, p.y - lastLocal.y)
                lastLocal = p
            }
            Mode.Move -> {
                if (travel < 4f * dp && !pushed) return
                ensurePushed()
                val s0 = clipOf(startScreen)
                val id = layerId ?: return
                if (ed.clip.layer(id)?.type == LayerType.Camera) {
                    ed.setXf(id, xf0.copy(x = xf0.x - (c.x - s0.x), y = xf0.y - (c.y - s0.y)))
                    return
                }
                var nx = xf0.x + c.x - s0.x
                var ny = xf0.y + c.y - s0.y
                guide = null
                if (ed.snap) {
                    // Snap the art's centre to the middle of the stage.
                    val mid = if (ed.inSymbol) Pt(0f, 0f) else Pt(ed.project.width / 2f, ed.project.height / 2f)
                    val tol = 10f * dp * unit()
                    val cx = center0.x + (nx - xf0.x)
                    val cy = center0.y + (ny - xf0.y)
                    val big = 1e5f
                    if (abs(cx - mid.x) < tol) { nx += mid.x - cx; guide = Pt(mid.x, -big) to Pt(mid.x, big) }
                    if (abs(cy - mid.y) < tol) { ny += mid.y - cy; guide = Pt(-big, mid.y) to Pt(big, mid.y) }
                }
                ed.setXf(id, xf0.copy(x = nx, y = ny))
            }
            Mode.Scale -> {
                ensurePushed()
                val r = Math.toRadians(-xf0.rot.toDouble())
                val cs = kotlin.math.cos(r).toFloat()
                val sn = kotlin.math.sin(r).toFloat()
                fun u(p: Pt) = Pt((p.x - center0.x) * cs - (p.y - center0.y) * sn, (p.x - center0.x) * sn + (p.y - center0.y) * cs)
                val u0 = u(clipOf(startScreen))
                val u1 = u(c)
                var sx = xf0.sx
                var sy = xf0.sy
                when (handle) {
                    Handle.L, Handle.R -> if (abs(u0.x) > 1e-3f) sx = xf0.sx * u1.x / u0.x
                    Handle.T, Handle.B -> if (abs(u0.y) > 1e-3f) sy = xf0.sy * u1.y / u0.y
                    else -> {
                        val d0 = hypot(u0.x, u0.y)
                        if (d0 > 1e-3f) {
                            val f = hypot(u1.x, u1.y) / d0
                            sx = xf0.sx * f
                            sy = xf0.sy * f
                        }
                    }
                }
                ed.setXf(layerId ?: return, pinned(xf0.copy(sx = clampScale(sx), sy = clampScale(sy))))
            }
            Mode.Rotate -> {
                ensurePushed()
                val s0 = clipOf(startScreen)
                val a0 = atan2(s0.y - center0.y, s0.x - center0.x)
                val a = atan2(c.y - center0.y, c.x - center0.x)
                ed.setXf(layerId ?: return, pinned(xf0.copy(rot = xf0.rot + Math.toDegrees((a - a0).toDouble()).toFloat())))
            }
            Mode.Tap, Mode.SymbolTap, Mode.None -> Unit
        }
    }

    private fun clampScale(v: Float) = if (abs(v) < 0.02f) (if (v < 0) -0.02f else 0.02f) else v.coerceIn(-50f, 50f)

    private fun erase(local: Pt) {
        val id = layerId ?: return
        val r = ed.eraserSize / 2f / xf0.avgScale
        if (ed.eraseAt(id, local, r) && !pushed) {
            snapshot?.let { ed.pushHistory(it) }
            pushed = true
        }
    }

    fun end(time: Long) {
        val c = clipOf(lastScreen)
        val tapped = travel < 10f * dp
        when (mode) {
            Mode.Draw -> {
                val ls = ed.live
                val id = layerId
                if (ls != null && id != null) {
                    val p = xf0.toLocal(c)
                    val last = ls.points.last()
                    if (hypot(p.x - last.x, p.y - last.y) > 0.5f) ls.points.add(Pt(p.x, p.y, last.p))
                    if (ls.points.size == 1) ls.points.add(Pt(last.x + 0.05f, last.y, last.p))
                    ed.addStroke(id, ls.toStroke())
                }
                ed.live = null
            }
            Mode.Lasso -> {
                val l = ed.selLayer
                if (l != null && l.type == LayerType.Drawing && ed.lassoPath.size > 2) {
                    val xf = l.xfAt(ed.frame)
                    ed.lassoSelect(ed.lassoPath.map { xf.toLocal(it) }, l.id)
                } else if (ed.lassoPath.size > 2) {
                    ed.showHint("Select a drawing layer first")
                }
                ed.lassoPath.clear()
            }
            Mode.Tap -> if (tapped) when (ed.tool) {
                Tool.Fill -> ed.fillAt(c)
                Tool.Pen -> {
                    val l = ed.drawTarget()
                    if (l != null) {
                        val xf = l.xfAt(ed.frame)
                        ed.penTap(l, xf.toLocal(c), 16f * dp * unit() / xf.avgScale)
                    }
                }
                Tool.Text -> {
                    val hit = ed.layerAt(c, 8f * dp * unit())
                    ed.textPrompt = if (hit != null && hit.type == LayerType.Text) TextPrompt(c, hit.id, hit.text) else TextPrompt(c)
                }
                Tool.Select -> {
                    lastTapTime = time
                    lastTapPos = lastScreen
                    if (ed.selLayer?.type != LayerType.Camera) ed.selectLayer(null)
                }
                else -> Unit
            }
            Mode.Move, Mode.SymbolTap -> if (tapped) {
                lastTapTime = time
                lastTapPos = lastScreen
            }
            else -> Unit
        }
        mode = Mode.None
        snapshot = null
        pushed = false
        guide = null
    }

    /** A second finger landed: drop whatever the first finger started. */
    fun cancel() {
        when (mode) {
            Mode.Draw -> ed.live = null
            Mode.Lasso -> ed.lassoPath.clear()
            Mode.Erase, Mode.Move, Mode.Scale, Mode.Rotate, Mode.LassoMove -> snapshot?.let { ed.revertGesture(it, pushed) }
            else -> Unit
        }
        mode = Mode.None
        snapshot = null
        pushed = false
        guide = null
    }
}
