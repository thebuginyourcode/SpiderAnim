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

/** A sampled point in layer-local space. [p] is stylus pressure (1 for touch). */
data class Pt(val x: Float, val y: Float, val p: Float = 1f)

enum class StrokeKind { Ink, Pencil, Marker, Line, Rect, Oval, Path }

/**
 * An immutable vector mark. Identity equality on purpose: render caches key on the instance
 * and every edit makes a new one.
 */
class Stroke(
    val kind: StrokeKind,
    val points: List<Pt>,
    val color: Int,
    val width: Float,
    val fill: Int? = null,
    val closed: Boolean = false,
) {
    val tapered: Boolean get() = kind == StrokeKind.Ink
    val smooth: Boolean get() = kind == StrokeKind.Ink || kind == StrokeKind.Pencil || kind == StrokeKind.Marker

    fun with(
        points: List<Pt> = this.points,
        color: Int = this.color,
        fill: Int? = this.fill,
        closed: Boolean = this.closed,
    ) = Stroke(kind, points, color, width, fill, closed)

    fun moved(dx: Float, dy: Float) = with(points = points.map { Pt(it.x + dx, it.y + dy, it.p) })
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
            val d = slope(u, x1, x2)
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

        val presets = listOf(
            "Linear" to Linear,
            "Ease In" to In,
            "Ease Out" to Out,
            "Ease In Out" to InOut,
            "Overshoot" to Back,
            "Anticipate" to Anticipate,
        )

        fun bez(u: Float, a: Float, b: Float): Float {
            val v = 1f - u
            return 3f * v * v * u * a + 3f * v * u * u * b + u * u * u
        }

        private fun slope(u: Float, a: Float, b: Float): Float {
            val v = 1f - u
            return 3f * v * v * a + 6f * v * u * (b - a) + 3f * u * u * (1f - b)
        }
    }
}

/** Animatable layer properties. Each one with keyframes shows as its own row in the timeline. */
enum class Prop(val label: String) {
    Position("Position"),
    Rotation("Rotation"),
    Scale("Scale"),
    Opacity("Opacity"),
    /** Which frame of a symbol is shown. Held between keys, never interpolated. */
    Time("Time"),
}

/** One or two values: (x, y) for Position and Scale, otherwise just [a]. */
data class V2(val a: Float, val b: Float = 0f)

data class Key(val frame: Int, val v: V2, val ease: Ease = Ease.Linear)

data class Track(val prop: Prop, val keys: List<Key>) {
    fun keyAt(frame: Int): Key? = keys.firstOrNull { it.frame == frame }

    fun eval(frame: Int): V2? {
        if (keys.isEmpty()) return null
        val i = keys.indexOfLast { it.frame <= frame }
        if (i < 0) return keys[0].v
        val a = keys[i]
        val b = keys.getOrNull(i + 1)
        if (b == null || prop == Prop.Time || frame == a.frame) return a.v
        val t = a.ease.apply((frame - a.frame).toFloat() / (b.frame - a.frame))
        return V2(a.v.a + (b.v.a - a.v.a) * t, a.v.b + (b.v.b - a.v.b) * t)
    }

    fun withKey(frame: Int, v: V2): Track {
        val old = keyAt(frame)
        val ease = old?.ease ?: keys.lastOrNull { it.frame < frame }?.ease ?: Ease.Linear
        return copy(keys = (keys.filter { it.frame != frame } + Key(frame, v, ease)).sortedBy { it.frame })
    }
}

/**
 * Placement of a layer: local point -> minus anchor -> scale -> rotate (degrees) -> plus position.
 * The position is where the anchor lands.
 */
data class Xf(
    val x: Float = 0f,
    val y: Float = 0f,
    val rot: Float = 0f,
    val sx: Float = 1f,
    val sy: Float = 1f,
    val alpha: Float = 1f,
    val ax: Float = 0f,
    val ay: Float = 0f,
) {
    val avgScale: Float get() = sqrt(abs(sx * sy)).coerceAtLeast(0.01f)

    fun toStage(p: Pt): Pt {
        val r = Math.toRadians(rot.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        val lx = (p.x - ax) * sx
        val ly = (p.y - ay) * sy
        return Pt(x + lx * c - ly * s, y + lx * s + ly * c, p.p)
    }

    fun toLocal(p: Pt): Pt {
        val r = Math.toRadians(rot.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        val dx = p.x - x
        val dy = p.y - y
        val rx = dx * c + dy * s
        val ry = -dx * s + dy * c
        return Pt(rx / safe(sx) + ax, ry / safe(sy) + ay, p.p)
    }

    private fun safe(v: Float) = if (abs(v) < 1e-4f) (if (v < 0) -1e-4f else 1e-4f) else v
}

enum class LayerType { Drawing, Symbol, Text, Camera, Audio }

/** A drawing on a drawing layer, shown for [length] frames from [start] (frame-by-frame exposure). */
data class Exposure(
    val id: Long = Ids.next(),
    val start: Int,
    val length: Int = 1,
    val strokes: List<Stroke> = emptyList(),
) {
    val end: Int get() = start + length
    fun covers(frame: Int) = frame >= start && frame < end
}

data class Layer(
    val id: Long = Ids.next(),
    val name: String,
    val type: LayerType,
    val visible: Boolean = true,
    val locked: Boolean = false,
    // Static values, used for any property without keyframes.
    val x: Float = 0f,
    val y: Float = 0f,
    val rot: Float = 0f,
    val sx: Float = 1f,
    val sy: Float = 1f,
    val alpha: Float = 1f,
    val time: Float = 0f,
    val ax: Float = 0f,
    val ay: Float = 0f,
    val tracks: List<Track> = emptyList(),
    // Drawing layers
    val exposures: List<Exposure> = emptyList(),
    // Symbol layers
    val symbolId: Long = 0,
    // Text layers
    val text: String = "",
    val textSize: Float = 96f,
    val textColor: Int = 0xFF000000.toInt(),
    // Audio layers: file name inside the app's audio folder
    val audio: String? = null,
) {
    fun track(p: Prop): Track? = tracks.firstOrNull { it.prop == p && it.keys.isNotEmpty() }

    fun base(p: Prop): V2 = when (p) {
        Prop.Position -> V2(x, y)
        Prop.Rotation -> V2(rot)
        Prop.Scale -> V2(sx, sy)
        Prop.Opacity -> V2(alpha)
        Prop.Time -> V2(time)
    }

    fun withBase(p: Prop, v: V2): Layer = when (p) {
        Prop.Position -> copy(x = v.a, y = v.b)
        Prop.Rotation -> copy(rot = v.a)
        Prop.Scale -> copy(sx = v.a, sy = v.b)
        Prop.Opacity -> copy(alpha = v.a.coerceIn(0f, 1f))
        Prop.Time -> copy(time = v.a)
    }

    fun value(p: Prop, frame: Int): V2 = track(p)?.eval(frame) ?: base(p)

    fun xfAt(frame: Int): Xf {
        val pos = value(Prop.Position, frame)
        val sc = value(Prop.Scale, frame)
        return Xf(pos.a, pos.b, value(Prop.Rotation, frame).a, sc.a, sc.b, value(Prop.Opacity, frame).a.coerceIn(0f, 1f), ax, ay)
    }

    /** Sets a property at [frame]: a keyframe if it is animated (or [autoKey]), else its static value. */
    fun set(p: Prop, frame: Int, v: V2, autoKey: Boolean): Layer =
        if (track(p) != null || autoKey) withKey(p, frame, v) else withBase(p, v)

    fun withKey(p: Prop, frame: Int, v: V2): Layer {
        val t = tracks.firstOrNull { it.prop == p } ?: Track(p, emptyList())
        val updated = t.withKey(frame, v)
        return copy(tracks = (tracks.filter { it.prop != p } + updated).sortedBy { it.prop.ordinal })
    }

    fun withoutKey(p: Prop, frame: Int): Layer {
        val t = track(p) ?: return this
        val keys = t.keys.filter { it.frame != frame }
        // The last keyframe going away leaves the property at that value.
        val base = if (keys.isEmpty()) withBase(p, t.eval(frame) ?: base(p)) else this
        return base.copy(tracks = tracks.map { if (it.prop == p) it.copy(keys = keys) else it }.filter { it.keys.isNotEmpty() })
    }

    /** Removes a property's animation, keeping the value it has at [frame]. */
    fun withoutTrack(p: Prop, frame: Int): Layer = withBase(p, value(p, frame)).copy(tracks = tracks.filter { it.prop != p })

    fun exposureAt(frame: Int): Exposure? = exposures.firstOrNull { it.covers(frame) }

    fun mapExposure(id: Long, f: (Exposure) -> Exposure) = copy(exposures = exposures.map { if (it.id == id) f(it) else it })

    /** Moves every exposure starting at or after [from] by [by] frames. */
    fun shiftExposures(from: Int, by: Int) =
        copy(exposures = exposures.map { if (it.start >= from) it.copy(start = it.start + by) else it })

    val animatedProps: List<Prop> get() = tracks.filter { it.keys.isNotEmpty() }.map { it.prop }
}

/** A timeline marker shown above the frame numbers, covering frames [from]..[to]. */
data class Marker(
    val id: Long = Ids.next(),
    val from: Int,
    val to: Int,
    val comment: String,
    val color: Int = MarkerColors[0],
)

val MarkerColors = listOf(0xFF6FB8C9, 0xFFFF6B7F, 0xFFFFB547, 0xFF5BD08A, 0xFFA889FF, 0xFFE8E8E8).map { it.toInt() }

/** A timeline with layers: the scene, or a symbol in the library. */
data class Clip(
    val id: Long = Ids.next(),
    val name: String,
    val length: Int,
    /** Bottom to top. */
    val layers: List<Layer> = emptyList(),
    val markers: List<Marker> = emptyList(),
) {
    fun layer(id: Long?): Layer? = if (id == null) null else layers.firstOrNull { it.id == id }
    fun mapLayer(id: Long, f: (Layer) -> Layer) = copy(layers = layers.map { if (it.id == id) f(it) else it })
    val camera: Layer? get() = layers.firstOrNull { it.type == LayerType.Camera }
}

data class Project(
    val id: Long = Ids.next(),
    val name: String,
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 24,
    val background: Int = 0xFFFFFFFF.toInt(),
    val scene: Clip,
    val symbols: List<Clip> = emptyList(),
) {
    fun clip(id: Long): Clip? = if (scene.id == id) scene else symbols.firstOrNull { it.id == id }

    fun mapClip(id: Long, f: (Clip) -> Clip): Project =
        if (scene.id == id) copy(scene = f(scene)) else copy(symbols = symbols.map { if (it.id == id) f(it) else it })

    /** The frame of its symbol a symbol layer shows at [frame]: keyed Time, or the symbol looping. */
    fun symbolFrame(layer: Layer, frame: Int): Int {
        val len = (clip(layer.symbolId)?.length ?: 1).coerceAtLeast(1)
        val t = layer.track(Prop.Time)
        return if (t != null) {
            (t.eval(frame)?.a ?: 0f).toInt().coerceIn(0, len - 1)
        } else {
            Math.floorMod(frame + layer.time.toInt(), len)
        }
    }

    fun usages(symbolId: Long): Int =
        (listOf(scene) + symbols).sumOf { c -> c.layers.count { it.type == LayerType.Symbol && it.symbolId == symbolId } }

    companion object {
        fun blank(name: String, width: Int, height: Int, fps: Int): Project {
            val scene = Clip(
                name = "Scene 1",
                length = fps * 2,
                layers = listOf(Layer(name = "Drawing 1", type = LayerType.Drawing, x = width / 2f, y = height / 2f, ax = width / 2f, ay = height / 2f)),
            )
            return Project(name = name, width = width, height = height, fps = fps, scene = scene)
        }
    }
}

private const val MaxDepth = 8

/** Bounds of what [layer] shows at [frame], in the layer's own (local) space. */
fun Project.contentBounds(layer: Layer, frame: Int, depth: Int = 0): Bounds? = when (layer.type) {
    LayerType.Drawing -> layer.exposureAt(frame)?.let { strokeBounds(it.strokes) }
    LayerType.Symbol -> if (depth >= MaxDepth) null else clip(layer.symbolId)?.let { clipBounds(it, symbolFrame(layer, frame), depth + 1) }
    LayerType.Text -> {
        val lines = layer.text.ifEmpty { " " }.split('\n')
        val w = lines.maxOf { it.length }.coerceAtLeast(1) * layer.textSize * 0.56f
        val h = lines.size * layer.textSize * 1.2f
        Bounds(-w / 2, -h / 2, w / 2, h / 2)
    }
    LayerType.Camera -> Bounds(0f, 0f, width.toFloat(), height.toFloat())
    LayerType.Audio -> null
}

/** Union of the visible content of [clip] at [frame], in the clip's space. */
fun Project.clipBounds(clip: Clip, frame: Int, depth: Int = 0): Bounds? {
    var out: Bounds? = null
    for (l in clip.layers) {
        if (!l.visible || l.type == LayerType.Camera || l.type == LayerType.Audio) continue
        val b = contentBounds(l, frame, depth) ?: continue
        val m = l.xfAt(frame).mapBounds(b)
        out = m.union(out)
    }
    return out
}

/** Union of [clip]'s content over all its frames (stable framing for thumbnails). */
fun Project.clipBoundsAll(clip: Clip): Bounds? {
    var out: Bounds? = null
    val step = (clip.length / 120).coerceAtLeast(1)
    for (f in 0 until clip.length step step) out = clipBounds(clip, f)?.union(out) ?: out
    return out
}
