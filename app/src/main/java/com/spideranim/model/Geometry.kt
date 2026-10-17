package com.spideranim.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val w: Float get() = right - left
    val h: Float get() = bottom - top
    val cx: Float get() = (left + right) / 2f
    val cy: Float get() = (top + bottom) / 2f

    fun contains(x: Float, y: Float, pad: Float = 0f) =
        x >= left - pad && x <= right + pad && y >= top - pad && y <= bottom + pad
}

fun Drawing.bounds(): Bounds? {
    var l = Float.MAX_VALUE
    var t = Float.MAX_VALUE
    var r = -Float.MAX_VALUE
    var b = -Float.MAX_VALUE
    for (s in strokes) {
        val h = s.width / 2f
        for (p in s.points) {
            l = min(l, p.x - h); t = min(t, p.y - h)
            r = max(r, p.x + h); b = max(b, p.y + h)
        }
    }
    return if (l > r) null else Bounds(l, t, r, b)
}

fun distToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax
    val dy = by - ay
    val len2 = dx * dx + dy * dy
    if (len2 < 1e-8f) return hypot(px - ax, py - ay)
    val t = (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
    return hypot(px - (ax + t * dx), py - (ay + t * dy))
}

fun nearPolyline(pts: List<Pt>, closed: Boolean, x: Float, y: Float, r: Float): Boolean {
    if (pts.isEmpty()) return false
    if (pts.size == 1) return hypot(pts[0].x - x, pts[0].y - y) <= r
    for (i in 0 until pts.size - 1) {
        val a = pts[i]
        val b = pts[i + 1]
        if (distToSegment(x, y, a.x, a.y, b.x, b.y) <= r) return true
    }
    if (closed) {
        val a = pts.last()
        val b = pts.first()
        if (distToSegment(x, y, a.x, a.y, b.x, b.y) <= r) return true
    }
    return false
}

/** Even-odd ray cast. The polygon is implicitly closed. */
fun insidePolygon(pts: List<Pt>, x: Float, y: Float): Boolean {
    if (pts.size < 3) return false
    var inside = false
    var j = pts.size - 1
    for (i in pts.indices) {
        val a = pts[i]
        val b = pts[j]
        if ((a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) inside = !inside
        j = i
    }
    return inside
}

/** Resamples so no two neighbours are further apart than [step], interpolating pressure. */
fun densify(pts: List<Pt>, step: Float): List<Pt> {
    if (pts.size < 2) return pts
    val out = ArrayList<Pt>(pts.size * 2)
    out.add(pts[0])
    for (i in 1 until pts.size) {
        val a = pts[i - 1]
        val b = pts[i]
        val d = hypot(b.x - a.x, b.y - a.y)
        val n = (d / step).toInt()
        for (k in 1..n) {
            val t = k / (n + 1f)
            out.add(Pt(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.p + (b.p - a.p) * t))
        }
        out.add(b)
    }
    return out
}

fun rectPoints(x0: Float, y0: Float, x1: Float, y1: Float): List<Pt> {
    val l = min(x0, x1); val r = max(x0, x1)
    val t = min(y0, y1); val b = max(y0, y1)
    return listOf(Pt(l, t), Pt(r, t), Pt(r, b), Pt(l, b))
}

fun ovalPoints(x0: Float, y0: Float, x1: Float, y1: Float, n: Int = 72): List<Pt> {
    val cx = (x0 + x1) / 2f
    val cy = (y0 + y1) / 2f
    val rx = kotlin.math.abs(x1 - x0) / 2f
    val ry = kotlin.math.abs(y1 - y0) / 2f
    return List(n) { i ->
        val a = 2.0 * PI * i / n
        Pt(cx + (cos(a) * rx).toFloat(), cy + (sin(a) * ry).toFloat())
    }
}

/** A stroke that is closed, or whose ends nearly meet, can be filled by the paint bucket. */
fun Stroke.fillable(gap: Float): Boolean {
    if (closed) return true
    if (points.size < 3) return false
    val a = points.first()
    val b = points.last()
    return hypot(a.x - b.x, a.y - b.y) <= max(gap, width * 2f)
}

/**
 * Vector eraser. Open strokes are cut where the eraser touches them; closed shapes and lines are
 * removed whole. Returns null when nothing changed.
 */
fun eraseStrokes(strokes: List<Stroke>, x: Float, y: Float, radius: Float): List<Stroke>? {
    var changed = false
    val out = ArrayList<Stroke>(strokes.size + 2)
    for (s in strokes) {
        val hitR = radius + s.width / 2f
        val solidHit = s.fill != null && s.closed && insidePolygon(s.points, x, y)
        if (!solidHit && !nearPolyline(s.points, s.closed, x, y, hitR)) {
            out.add(s)
            continue
        }
        changed = true
        val whole = s.closed || s.fill != null || s.kind == StrokeKind.Line || s.kind == StrokeKind.Pen
        if (whole) continue
        val dense = densify(s.points, (hitR * 0.5f).coerceAtLeast(0.5f))
        var piece = ArrayList<Pt>()
        for (p in dense) {
            if (hypot(p.x - x, p.y - y) <= hitR) {
                if (piece.size >= 2) out.add(s.with(points = piece))
                piece = ArrayList()
            } else {
                piece.add(p)
            }
        }
        if (piece.size >= 2) out.add(s.with(points = piece))
    }
    return if (changed) out else null
}
