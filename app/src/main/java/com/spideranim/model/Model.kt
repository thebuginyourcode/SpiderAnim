package com.spideranim.model

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Ids {
    private var next = System.currentTimeMillis()

    @Synchronized
    fun next(): Long = ++next

    @Synchronized
    fun seen(id: Long) {
        if (id >= next) next = id + 1
    }
}

/** A sampled input point in symbol-local space. [p] is stylus pressure (1 for touch). */
data class Pt(val x: Float, val y: Float, val p: Float = 1f)

enum class StrokeKind { Brush, Pencil, Line, Rect, Oval, Pen }

/**
 * An immutable vector mark. Deliberately not a data class: render caches key on identity,
 * and every edit produces a new instance.
 */
class Stroke(
    val kind: StrokeKind,
    val points: List<Pt>,
    val color: Int,
    val width: Float,
    val fill: Int? = null,
    val closed: Boolean = false,
) {
    /** Brush marks are drawn as a pressure/taper outline instead of a constant-width line. */
    val tapered: Boolean get() = kind == StrokeKind.Brush
    val smooth: Boolean get() = kind == StrokeKind.Brush || kind == StrokeKind.Pencil

    fun with(
        points: List<Pt> = this.points,
        color: Int = this.color,
        fill: Int? = this.fill,
        closed: Boolean = this.closed,
    ) = Stroke(kind, points, color, width, fill, closed)
}

/** One drawing inside a symbol. A symbol's drawings are what the Symbol Picker chooses between. */
data class Drawing(val id: Long = Ids.next(), val strokes: List<Stroke> = emptyList())

data class Symbol(
    val id: Long = Ids.next(),
    val name: String,
    val frames: List<Drawing> = listOf(Drawing()),
) {
    fun frameAt(index: Int): Drawing = frames[index.coerceIn(0, frames.size - 1)]

    fun mapFrame(index: Int, f: (Drawing) -> Drawing): Symbol {
        val i = index.coerceIn(0, frames.size - 1)
        return copy(frames = frames.mapIndexed { k, d -> if (k == i) f(d) else d })
    }
}

/** CSS-style cubic-bezier timing curve from (0,0) to (1,1). */
data class Ease(val x1: Float, val y1: Float, val x2: Float, val y2: Float) {

    fun apply(t: Float): Float {
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f
        if (x1 == y1 && x2 == y2) return t
        var u = t
        for (i in 0 until 8) {
            val err = bez(u, x1, x2) - t
            if (abs(err) < 1e-5f) return bez(u, y1, y2)
            val d = bezSlope(u, x1, x2)
            if (abs(d) < 1e-6f) break
            u -= err / d
        }
        var lo = 0f
        var hi = 1f
        u = t
        for (i in 0 until 40) {
            val x = bez(u, x1, x2)
            if (abs(x - t) < 1e-5f) break
            if (x < t) lo = u else hi = u
            u = (lo + hi) / 2f
        }
        return bez(u, y1, y2)
    }

    companion object {
        val Linear = Ease(0f, 0f, 1f, 1f)
        val In = Ease(0.42f, 0f, 1f, 1f)
        val Out = Ease(0f, 0f, 0.58f, 1f)
        val InOut = Ease(0.42f, 0f, 0.58f, 1f)
        val Back = Ease(0.34f, 1.56f, 0.64f, 1f)
        val Anticipate = Ease(0.6f, -0.28f, 0.74f, 0.05f)
        val Snap = Ease(0.85f, 0f, 0.15f, 1f)
        val Soft = Ease(0.25f, 0.1f, 0.25f, 1f)

        val presets = listOf(
            "Linear" to Linear,
            "Ease in" to In,
            "Ease out" to Out,
            "In & out" to InOut,
            "Soft" to Soft,
            "Back" to Back,
            "Anticipate" to Anticipate,
            "Snap" to Snap,
        )

        fun bez(u: Float, a: Float, b: Float): Float {
            val v = 1f - u
            return 3f * v * v * u * a + 3f * v * u * u * b + u * u * u
        }

        private fun bezSlope(u: Float, a: Float, b: Float): Float {
            val v = 1f - u
            return 3f * v * v * a + 6f * v * u * (b - a) + 3f * u * u * (1f - b)
        }
    }
}

/** Instance transform: local point -> scale -> rotate (degrees) -> translate. */
data class Xform(
    val x: Float = 0f,
    val y: Float = 0f,
    val sx: Float = 1f,
    val sy: Float = 1f,
    val rot: Float = 0f,
    val alpha: Float = 1f,
) {
    val avgScale: Float get() = sqrt(abs(sx * sy)).coerceAtLeast(0.01f)

    fun lerp(o: Xform, t: Float) = Xform(
        x + (o.x - x) * t,
        y + (o.y - y) * t,
        sx + (o.sx - sx) * t,
        sy + (o.sy - sy) * t,
        rot + (o.rot - rot) * t,
        alpha + (o.alpha - alpha) * t,
    )

    fun toStage(p: Pt): Pt {
        val r = Math.toRadians(rot.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        val ax = p.x * sx
        val ay = p.y * sy
        return Pt(x + ax * c - ay * s, y + ax * s + ay * c, p.p)
    }

    fun toLocal(p: Pt): Pt {
        val r = Math.toRadians(rot.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        val dx = p.x - x
        val dy = p.y - y
        val rx = dx * c + dy * s
        val ry = -dx * s + dy * c
        return Pt(rx / safe(sx), ry / safe(sy), p.p)
    }

    /** Stage point -> rotated-but-unscaled offset from the registration point. */
    fun toUnscaled(p: Pt): Pt {
        val r = Math.toRadians(rot.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        val dx = p.x - x
        val dy = p.y - y
        return Pt(dx * c + dy * s, -dx * s + dy * c)
    }

    private fun safe(v: Float) = if (abs(v) < 1e-4f) (if (v < 0) -1e-4f else 1e-4f) else v
}

data class Keyframe(
    val frame: Int,
    val xf: Xform,
    /** Which drawing of the layer's symbol is shown from this key on (held, never tweened). */
    val symbolFrame: Int = 0,
    /** Ease applied on the tween leaving this keyframe. */
    val ease: Ease = Ease.Linear,
    val tween: Boolean = true,
)

data class Pose(val xf: Xform, val symbolFrame: Int)

data class Layer(
    val id: Long = Ids.next(),
    val name: String,
    val symbolId: Long,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val keyframes: List<Keyframe>,
) {
    fun keyAt(frame: Int): Keyframe? = keyframes.firstOrNull { it.frame == frame }

    /** The keyframe at or before [frame] and the one after it, if any. */
    fun segmentAt(frame: Int): Pair<Keyframe, Keyframe?>? {
        if (keyframes.isEmpty()) return null
        val i = keyframes.indexOfLast { it.frame <= frame }
        if (i < 0) return keyframes[0] to keyframes.getOrNull(1)
        return keyframes[i] to keyframes.getOrNull(i + 1)
    }

    fun poseAt(frame: Int): Pose {
        if (keyframes.isEmpty()) return Pose(Xform(), 0)
        val i = keyframes.indexOfLast { it.frame <= frame }
        if (i < 0) return Pose(keyframes[0].xf, keyframes[0].symbolFrame)
        val a = keyframes[i]
        val b = keyframes.getOrNull(i + 1)
        if (b == null || !a.tween || frame == a.frame) return Pose(a.xf, a.symbolFrame)
        val t = (frame - a.frame).toFloat() / (b.frame - a.frame)
        return Pose(a.xf.lerp(b.xf, a.ease.apply(t)), a.symbolFrame)
    }

    /** Returns a copy whose keyframe at [frame] is [f] applied to the existing key, or to a key created from the current pose. */
    fun withKey(frame: Int, f: (Keyframe) -> Keyframe): Layer {
        val existing = keyAt(frame)
        val base = existing ?: run {
            val pose = poseAt(frame)
            val seg = keyframes.lastOrNull { it.frame <= frame }
            Keyframe(frame, pose.xf, pose.symbolFrame, seg?.ease ?: Ease.Linear, seg?.tween ?: true)
        }
        val updated = f(base)
        return copy(keyframes = (keyframes.filter { it.frame != frame } + updated).sortedBy { it.frame })
    }
}

data class Project(
    val name: String = "Untitled",
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 24,
    val length: Int = 48,
    val background: Int = 0xFFFFFFFF.toInt(),
    val symbols: List<Symbol> = emptyList(),
    /** Bottom to top. */
    val layers: List<Layer> = emptyList(),
) {
    fun symbol(id: Long): Symbol? = symbols.firstOrNull { it.id == id }
    fun layer(id: Long?): Layer? = if (id == null) null else layers.firstOrNull { it.id == id }

    fun mapLayer(id: Long, f: (Layer) -> Layer) = copy(layers = layers.map { if (it.id == id) f(it) else it })
    fun mapSymbol(id: Long, f: (Symbol) -> Symbol) = copy(symbols = symbols.map { if (it.id == id) f(it) else it })

    /** The drawing a layer shows at [frame], i.e. the one new strokes go into. */
    fun drawingOf(layer: Layer, frame: Int): Drawing? =
        symbol(layer.symbolId)?.frameAt(layer.poseAt(frame).symbolFrame)
}
