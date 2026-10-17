package com.spideranim.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.spideranim.model.Drawing
import com.spideranim.model.Layer
import com.spideranim.model.Project
import com.spideranim.model.Pt
import com.spideranim.model.Stroke
import com.spideranim.model.Xform
import com.spideranim.model.bounds
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

fun DrawScope.drawInstance(
    d: Drawing,
    xf: Xform,
    tint: Color? = null,
    alpha: Float = 1f,
    cached: Boolean = true,
    extra: (DrawScope.() -> Unit)? = null,
) {
    withTransform({
        translate(xf.x, xf.y)
        rotate(xf.rot, Offset.Zero)
        scale(xf.sx, xf.sy, Offset.Zero)
    }) {
        val a = alpha * xf.alpha
        if (a < 0.999f && d.strokes.size > 1) {
            // Fade the instance as one image so overlapping strokes don't darken.
            val canvas = drawContext.canvas
            canvas.saveLayer(Rect(-1e5f, -1e5f, 1e5f, 1e5f), Paint().apply { this.alpha = a })
            d.strokes.forEach { drawMark(it, tint, 1f, cached) }
            extra?.invoke(this)
            canvas.restore()
        } else {
            d.strokes.forEach { drawMark(it, tint, a, cached) }
            extra?.invoke(this)
        }
    }
}

fun DrawScope.drawScene(
    project: Project,
    frame: Int,
    cached: Boolean = true,
    only: Long? = null,
    tint: Color? = null,
    alpha: Float = 1f,
    inLayer: (DrawScope.(Layer) -> Unit)? = null,
) {
    for (layer in project.layers) {
        if (!layer.visible) continue
        if (only != null && layer.id != only) continue
        val sym = project.symbol(layer.symbolId) ?: continue
        val pose = layer.poseAt(frame)
        val extra: (DrawScope.() -> Unit)? = inLayer?.let { f -> fun DrawScope.() { f(this, layer) } }
        drawInstance(sym.frameAt(pose.symbolFrame), pose.xf, tint, alpha, cached, extra)
    }
}

/** Draws [d] scaled to fit the current draw area, centred. Used for symbol thumbnails. */
fun DrawScope.drawFitted(d: Drawing, pad: Float) {
    val b = d.bounds() ?: return
    val s = min(
        (size.width - pad * 2f) / b.w.coerceAtLeast(1f),
        (size.height - pad * 2f) / b.h.coerceAtLeast(1f),
    )
    withTransform({
        translate(size.width / 2f, size.height / 2f)
        scale(s, s, Offset.Zero)
        translate(-b.cx, -b.cy)
    }) {
        d.strokes.forEach { drawMark(it) }
    }
}
