package com.spideranim.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.spideranim.model.Bounds
import com.spideranim.model.Clip
import com.spideranim.model.Layer
import com.spideranim.model.LayerType
import com.spideranim.model.Project
import com.spideranim.model.Xf
import com.spideranim.model.Pt
import com.spideranim.model.Stroke
import java.util.WeakHashMap
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.ui.graphics.drawscope.Stroke as LineStyle

class StrokeShape(val line: Path, val outline: Path?, val fill: Path?)

// Stroke has identity equality, so this caches per immutable instance.
private val shapeCache = WeakHashMap<Stroke, StrokeShape>()

fun shapeOf(s: Stroke, cached: Boolean = true): StrokeShape {
    if (!cached) return buildShape(s)
    synchronized(shapeCache) { shapeCache[s]?.let { return it } }
    val shape = buildShape(s)
    synchronized(shapeCache) { shapeCache[s] = shape }
    return shape
}

fun buildShape(s: Stroke): StrokeShape {
    val pts = s.points
    val line = if (s.smooth) smoothPath(pts, s.closed) else polyPath(pts, s.closed)
    val outline = if (s.tapered && pts.size >= 2) taperedOutline(pts, s.width) else null
    val fill = if (s.fill != null && pts.size >= 3) {
        if (s.smooth) smoothPath(pts, true) else polyPath(pts, true)
    } else null
    return StrokeShape(line, outline, fill)
}

private fun polyPath(pts: List<Pt>, closed: Boolean): Path {
    val path = Path()
    if (pts.isEmpty()) return path
    path.moveTo(pts[0].x, pts[0].y)
    if (pts.size == 1) path.lineTo(pts[0].x + 0.01f, pts[0].y)
    for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
    if (closed) path.close()
    return path
}

/** Quadratic segments through the midpoints: cheap, and removes the polyline "kinks". */
fun smoothPath(pts: List<Pt>, closed: Boolean): Path {
    val path = Path()
    if (pts.isEmpty()) return path
    path.moveTo(pts[0].x, pts[0].y)
    if (pts.size < 3) {
        if (pts.size == 1) path.lineTo(pts[0].x + 0.01f, pts[0].y) else path.lineTo(pts[1].x, pts[1].y)
        if (closed) path.close()
        return path
    }
    for (i in 1 until pts.size - 1) {
        val a = pts[i]
        val b = pts[i + 1]
        path.quadraticBezierTo(a.x, a.y, (a.x + b.x) / 2f, (a.y + b.y) / 2f)
    }
    val last = pts.last()
    path.lineTo(last.x, last.y)
    if (closed) path.close()
    return path
}

private fun smoothstep(t: Float) = t * t * (3f - 2f * t)

/** Filled outline whose half-width follows pressure and tapers softly at both ends. */
private fun taperedOutline(pts: List<Pt>, width: Float): Path {
    val n = pts.size
    val left = ArrayList<Offset>(n)
    val right = ArrayList<Offset>(n)
    val radii = FloatArray(n)
    val taperSpan = (n / 3).coerceIn(1, 8).toFloat()
    for (i in 0 until n) {
        val p = pts[i]
        val a = pts[max(0, i - 1)]
        val b = pts[min(n - 1, i + 1)]
        var tx = b.x - a.x
        var ty = b.y - a.y
        val len = sqrt(tx * tx + ty * ty)
        if (len < 1e-4f) { tx = 1f; ty = 0f } else { tx /= len; ty /= len }
        val edge = min(i, n - 1 - i).toFloat()
        val taper = if (n < 4) 1f else smoothstep((edge / taperSpan).coerceIn(0f, 1f))
        val r = width * 0.5f * p.p.coerceIn(0.08f, 1f) * (0.3f + 0.7f * taper)
        radii[i] = r
        left.add(Offset(p.x - ty * r, p.y + tx * r))
        right.add(Offset(p.x + ty * r, p.y - tx * r))
    }
    val path = Path()
    path.moveTo(left[0].x, left[0].y)
    path.through(left)
    path.cap(Offset(pts[n - 1].x, pts[n - 1].y), left[n - 1], radii[n - 1])
    path.through(right.asReversed())
    path.cap(Offset(pts[0].x, pts[0].y), right[0], radii[0])
    path.close()
    return path
}

private fun Path.through(p: List<Offset>) {
    lineTo(p[0].x, p[0].y)
    for (i in 1 until p.size - 1) {
        val a = p[i]
        val b = p[i + 1]
        quadraticBezierTo(a.x, a.y, (a.x + b.x) / 2f, (a.y + b.y) / 2f)
    }
    lineTo(p.last().x, p.last().y)
}

/** Half circle around [c] starting at [from], turning the same way for both ends. */
private fun Path.cap(c: Offset, from: Offset, r: Float) {
    val a0 = atan2(from.y - c.y, from.x - c.x)
    for (k in 1..6) {
        val a = a0 - PI.toFloat() * k / 6f
        lineTo(c.x + cos(a) * r, c.y + sin(a) * r)
    }
}

fun DrawScope.drawMark(s: Stroke, tint: Color? = null, alpha: Float = 1f, cached: Boolean = true) {
    val shape = shapeOf(s, cached)
    val fillColor = s.fill
    if (shape.fill != null && fillColor != null) {
        drawPath(shape.fill, tint ?: Color(fillColor), alpha = if (tint != null) alpha * 0.35f else alpha)
    }
    val c = tint ?: Color(s.color)
    if (shape.outline != null) {
        drawPath(shape.outline, c, alpha = alpha)
    } else {
        drawPath(shape.line, c, alpha = alpha, style = LineStyle(width = s.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}


private val textPaint = ThreadLocal.withInitial {
    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
        textAlign = android.graphics.Paint.Align.CENTER
    }
}

/** Text centred on the layer origin, one line per '\n'. */
fun DrawScope.drawTextLayer(layer: Layer, tint: Color? = null) {
    val paint = textPaint.get()!!
    paint.textSize = layer.textSize
    paint.color = (tint ?: Color(layer.textColor)).toArgb()
    val lines = layer.text.ifEmpty { " " }.split('\n')
    val lh = layer.textSize * 1.2f
    val top = -lines.size * lh / 2f
    drawIntoCanvas { c ->
        lines.forEachIndexed { i, line ->
            c.nativeCanvas.drawText(line, 0f, top + i * lh + layer.textSize * 0.95f, paint)
        }
    }
}

fun DrawScope.withXf(xf: Xf, block: DrawScope.() -> Unit) {
    withTransform({
        translate(xf.x, xf.y)
        rotate(xf.rot, Offset.Zero)
        scale(xf.sx, xf.sy, Offset.Zero)
        translate(-xf.ax, -xf.ay)
    }, block)
}

/** Faded layers are drawn into their own buffer so overlapping strokes don't darken. */
private inline fun DrawScope.faded(alpha: Float, block: () -> Unit) {
    if (alpha >= 0.999f) {
        block()
        return
    }
    val canvas = drawContext.canvas
    canvas.saveLayer(Rect(-1e5f, -1e5f, 1e5f, 1e5f), Paint().apply { this.alpha = alpha })
    block()
    canvas.restore()
}

class RenderOptions(
    val cached: Boolean = true,
    /** Overrides every colour (onion skin). */
    val tint: Color? = null,
    val alpha: Float = 1f,
    /** Only this layer of the top clip. */
    val only: Long? = null,
    /** Called inside a top-level layer's transform after its content (live strokes, previews). */
    val inLayer: (DrawScope.(Layer) -> Unit)? = null,
)

/** Draws [clip] at [frame] in clip space, symbols included recursively. */
fun DrawScope.drawClip(project: Project, clip: Clip, frame: Int, opt: RenderOptions = RenderOptions(), depth: Int = 0) {
    if (depth > 8) return
    for (layer in clip.layers) {
        if (!layer.visible) continue
        if (depth == 0 && opt.only != null && layer.id != opt.only) continue
        if (layer.type == LayerType.Camera || layer.type == LayerType.Audio) continue
        val xf = layer.xfAt(frame)
        val a = xf.alpha * if (depth == 0) opt.alpha else 1f
        if (a <= 0.001f) continue
        withXf(xf) {
            faded(if (opt.tint != null) 1f else a) {
                val strokeAlpha = if (opt.tint != null) a else 1f
                when (layer.type) {
                    LayerType.Drawing -> layer.exposureAt(frame)?.strokes?.forEach { drawMark(it, opt.tint, strokeAlpha, opt.cached) }
                    LayerType.Symbol -> project.clip(layer.symbolId)?.let { sym ->
                        drawClip(project, sym, project.symbolFrame(layer, frame), RenderOptions(opt.cached, opt.tint, strokeAlpha), depth + 1)
                    }
                    LayerType.Text -> drawTextLayer(layer, opt.tint)
                    else -> Unit
                }
                if (depth == 0) opt.inLayer?.invoke(this, layer)
            }
        }
    }
}

/** Maps the scene into the camera's view (identity when there is no camera layer). */
fun DrawScope.withCamera(clip: Clip, frame: Int, block: DrawScope.() -> Unit) {
    val cam = clip.camera
    if (cam == null) {
        block()
        return
    }
    val c = cam.xfAt(frame)
    withTransform({
        translate(c.ax, c.ay)
        scale(1f / c.sx.coerceAtLeast(0.01f), 1f / c.sy.coerceAtLeast(0.01f), Offset.Zero)
        rotate(-c.rot, Offset.Zero)
        translate(-c.x, -c.y)
    }, block)
}

/** A whole frame of the scene as the camera sees it, on the stage background. */
fun DrawScope.drawSceneFrame(project: Project, frame: Int, cached: Boolean = true) {
    drawRect(Color(project.background), size = androidx.compose.ui.geometry.Size(project.width.toFloat(), project.height.toFloat()))
    withCamera(project.scene, frame) { drawClip(project, project.scene, frame, RenderOptions(cached = cached)) }
}

/** Draws strokes or a clip frame scaled to fit the draw area (thumbnails). */
fun DrawScope.drawFitted(bounds: Bounds?, pad: Float, block: DrawScope.() -> Unit) {
    val b = bounds ?: return
    val s = min((size.width - pad * 2f) / b.w.coerceAtLeast(1f), (size.height - pad * 2f) / b.h.coerceAtLeast(1f))
    withTransform({
        translate(size.width / 2f, size.height / 2f)
        scale(s, s, Offset.Zero)
        translate(-b.cx, -b.cy)
    }, block)
}
