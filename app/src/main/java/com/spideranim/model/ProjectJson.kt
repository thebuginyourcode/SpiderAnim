package com.spideranim.model

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** Plain org.json persistence so the model stays free of serialization plugins. */
object ProjectJson {

    fun write(p: Project): String = JSONObject().apply {
        put("version", 1)
        put("name", p.name)
        put("width", p.width)
        put("height", p.height)
        put("fps", p.fps)
        put("length", p.length)
        put("background", p.background)
        put("symbols", JSONArray().apply { p.symbols.forEach { put(symbol(it)) } })
        put("layers", JSONArray().apply { p.layers.forEach { put(layer(it)) } })
    }.toString()

    fun read(text: String): Project {
        val o = JSONObject(text)
        val symbols = o.getJSONArray("symbols").objects().map(::readSymbol)
        val layers = o.getJSONArray("layers").objects().map(::readLayer)
        return Project(
            name = o.optString("name", "Untitled"),
            width = o.optInt("width", 1920),
            height = o.optInt("height", 1080),
            fps = o.optInt("fps", 24),
            length = o.optInt("length", 48),
            background = o.optInt("background", 0xFFFFFFFF.toInt()),
            symbols = symbols,
            layers = layers.filter { l -> symbols.any { it.id == l.symbolId } },
        )
    }

    private fun symbol(s: Symbol) = JSONObject().apply {
        put("id", s.id)
        put("name", s.name)
        put("frames", JSONArray().apply {
            s.frames.forEach { d ->
                put(JSONObject().apply {
                    put("id", d.id)
                    put("strokes", JSONArray().apply { d.strokes.forEach { put(stroke(it)) } })
                })
            }
        })
    }

    private fun stroke(s: Stroke) = JSONObject().apply {
        put("kind", s.kind.name)
        put("color", s.color)
        put("width", s.width.toDouble())
        s.fill?.let { put("fill", it) }
        put("closed", s.closed)
        put("pts", JSONArray().apply {
            s.points.forEach {
                put(round(it.x)); put(round(it.y)); put(round(it.p))
            }
        })
    }

    private fun layer(l: Layer) = JSONObject().apply {
        put("id", l.id)
        put("name", l.name)
        put("symbol", l.symbolId)
        put("visible", l.visible)
        put("locked", l.locked)
        put("keys", JSONArray().apply {
            l.keyframes.forEach { k ->
                put(JSONObject().apply {
                    put("f", k.frame)
                    put("x", k.xf.x.toDouble()); put("y", k.xf.y.toDouble())
                    put("sx", k.xf.sx.toDouble()); put("sy", k.xf.sy.toDouble())
                    put("r", k.xf.rot.toDouble()); put("a", k.xf.alpha.toDouble())
                    put("sf", k.symbolFrame)
                    put("tw", k.tween)
                    put("ease", JSONArray().apply {
                        put(k.ease.x1.toDouble()); put(k.ease.y1.toDouble())
                        put(k.ease.x2.toDouble()); put(k.ease.y2.toDouble())
                    })
                })
            }
        })
    }

    private fun readSymbol(o: JSONObject): Symbol {
        val id = o.getLong("id").also(Ids::seen)
        val frames = o.getJSONArray("frames").objects().map { d ->
            Drawing(
                id = d.getLong("id").also(Ids::seen),
                strokes = d.getJSONArray("strokes").objects().map(::readStroke),
            )
        }
        return Symbol(id, o.optString("name", "Symbol"), frames.ifEmpty { listOf(Drawing()) })
    }

    private fun readStroke(o: JSONObject): Stroke {
        val a = o.getJSONArray("pts")
        val pts = ArrayList<Pt>(a.length() / 3)
        var i = 0
        while (i + 2 < a.length()) {
            pts.add(Pt(a.getDouble(i).toFloat(), a.getDouble(i + 1).toFloat(), a.getDouble(i + 2).toFloat()))
            i += 3
        }
        return Stroke(
            kind = runCatching { StrokeKind.valueOf(o.getString("kind")) }.getOrDefault(StrokeKind.Pencil),
            points = pts,
            color = o.getInt("color"),
            width = o.getDouble("width").toFloat(),
            fill = if (o.has("fill")) o.getInt("fill") else null,
            closed = o.optBoolean("closed", false),
        )
    }

    private fun readLayer(o: JSONObject): Layer {
        val keys = o.getJSONArray("keys").objects().map { k ->
            val e = k.optJSONArray("ease")
            Keyframe(
                frame = k.getInt("f"),
                xf = Xform(
                    k.getDouble("x").toFloat(), k.getDouble("y").toFloat(),
                    k.getDouble("sx").toFloat(), k.getDouble("sy").toFloat(),
                    k.getDouble("r").toFloat(), k.getDouble("a").toFloat(),
                ),
                symbolFrame = k.optInt("sf", 0),
                ease = if (e != null && e.length() == 4) Ease(
                    e.getDouble(0).toFloat(), e.getDouble(1).toFloat(),
                    e.getDouble(2).toFloat(), e.getDouble(3).toFloat(),
                ) else Ease.Linear,
                tween = k.optBoolean("tw", true),
            )
        }.sortedBy { it.frame }
        return Layer(
            id = o.getLong("id").also(Ids::seen),
            name = o.optString("name", "Layer"),
            symbolId = o.getLong("symbol"),
            visible = o.optBoolean("visible", true),
            locked = o.optBoolean("locked", false),
            keyframes = keys.ifEmpty { listOf(Keyframe(0, Xform())) },
        )
    }

    private fun round(v: Float): Double = (v * 100f).roundToInt() / 100.0

    private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
}
