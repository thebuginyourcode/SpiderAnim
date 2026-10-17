package com.spideranim.model

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** Plain org.json persistence so the model stays free of serialization plugins. */
object ProjectJson {
    const val VERSION = 3

    fun write(p: Project): String = JSONObject().apply {
        put("version", VERSION)
        put("id", p.id)
        put("name", p.name)
        put("width", p.width)
        put("height", p.height)
        put("fps", p.fps)
        put("background", p.background)
        put("scene", clip(p.scene))
        put("symbols", JSONArray().apply { p.symbols.forEach { put(clip(it)) } })
    }.toString()

    fun read(text: String): Project {
        val o = JSONObject(text)
        if (o.optInt("version", 0) < VERSION) error("Unsupported project version")
        return Project(
            id = o.getLong("id").also(Ids::seen),
            name = o.optString("name", "Untitled"),
            width = o.optInt("width", 1920),
            height = o.optInt("height", 1080),
            fps = o.optInt("fps", 24),
            background = o.optInt("background", 0xFFFFFFFF.toInt()),
            scene = readClip(o.getJSONObject("scene")),
            symbols = o.getJSONArray("symbols").objects().map(::readClip),
        )
    }

    private fun clip(c: Clip) = JSONObject().apply {
        put("id", c.id)
        put("name", c.name)
        put("length", c.length)
        put("layers", JSONArray().apply { c.layers.forEach { put(layer(it)) } })
        put("markers", JSONArray().apply {
            c.markers.forEach { m ->
                put(JSONObject().apply {
                    put("id", m.id); put("from", m.from); put("to", m.to)
                    put("comment", m.comment); put("color", m.color)
                })
            }
        })
    }

    private fun readClip(o: JSONObject) = Clip(
        id = o.getLong("id").also(Ids::seen),
        name = o.optString("name", "Symbol"),
        length = o.optInt("length", 24).coerceAtLeast(1),
        layers = o.getJSONArray("layers").objects().map(::readLayer),
        markers = o.optJSONArray("markers")?.objects()?.map { m ->
            Marker(
                id = m.getLong("id").also(Ids::seen),
                from = m.getInt("from"),
                to = m.getInt("to"),
                comment = m.optString("comment", ""),
                color = m.optInt("color", MarkerColors[0]),
            )
        } ?: emptyList(),
    )

    private fun layer(l: Layer) = JSONObject().apply {
        put("id", l.id)
        put("name", l.name)
        put("type", l.type.name)
        put("visible", l.visible)
        put("locked", l.locked)
        put("x", l.x.toDouble()); put("y", l.y.toDouble()); put("rot", l.rot.toDouble())
        put("sx", l.sx.toDouble()); put("sy", l.sy.toDouble()); put("alpha", l.alpha.toDouble())
        put("time", l.time.toDouble()); put("ax", l.ax.toDouble()); put("ay", l.ay.toDouble())
        put("tracks", JSONArray().apply {
            l.tracks.forEach { t ->
                put(JSONObject().apply {
                    put("prop", t.prop.name)
                    put("keys", JSONArray().apply {
                        t.keys.forEach { k ->
                            put(JSONArray().apply {
                                put(k.frame); put(k.v.a.toDouble()); put(k.v.b.toDouble())
                                put(k.ease.x1.toDouble()); put(k.ease.y1.toDouble())
                                put(k.ease.x2.toDouble()); put(k.ease.y2.toDouble())
                            })
                        }
                    })
                })
            }
        })
        put("exposures", JSONArray().apply {
            l.exposures.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id); put("start", e.start); put("length", e.length)
                    put("strokes", JSONArray().apply { e.strokes.forEach { put(stroke(it)) } })
                })
            }
        })
        put("symbol", l.symbolId)
        put("text", l.text)
        put("textSize", l.textSize.toDouble())
        put("textColor", l.textColor)
        l.audio?.let { put("audio", it) }
    }

    private fun readLayer(o: JSONObject): Layer {
        fun f(k: String, d: Float) = o.optDouble(k, d.toDouble()).toFloat()
        return Layer(
            id = o.getLong("id").also(Ids::seen),
            name = o.optString("name", "Layer"),
            type = runCatching { LayerType.valueOf(o.getString("type")) }.getOrDefault(LayerType.Drawing),
            visible = o.optBoolean("visible", true),
            locked = o.optBoolean("locked", false),
            x = f("x", 0f), y = f("y", 0f), rot = f("rot", 0f),
            sx = f("sx", 1f), sy = f("sy", 1f), alpha = f("alpha", 1f),
            time = f("time", 0f), ax = f("ax", 0f), ay = f("ay", 0f),
            tracks = o.optJSONArray("tracks")?.objects()?.mapNotNull { t ->
                val prop = runCatching { Prop.valueOf(t.getString("prop")) }.getOrNull() ?: return@mapNotNull null
                val keys = t.getJSONArray("keys").let { a ->
                    List(a.length()) { i ->
                        val k = a.getJSONArray(i)
                        Key(
                            k.getInt(0),
                            V2(k.getDouble(1).toFloat(), k.getDouble(2).toFloat()),
                            Ease(k.getDouble(3).toFloat(), k.getDouble(4).toFloat(), k.getDouble(5).toFloat(), k.getDouble(6).toFloat()),
                        )
                    }
                }.sortedBy { it.frame }
                Track(prop, keys)
            } ?: emptyList(),
            exposures = o.optJSONArray("exposures")?.objects()?.map { e ->
                Exposure(
                    id = e.getLong("id").also(Ids::seen),
                    start = e.getInt("start"),
                    length = e.optInt("length", 1).coerceAtLeast(1),
                    strokes = e.getJSONArray("strokes").objects().map(::readStroke),
                )
            }?.sortedBy { it.start } ?: emptyList(),
            symbolId = o.optLong("symbol", 0),
            text = o.optString("text", ""),
            textSize = f("textSize", 96f),
            textColor = o.optInt("textColor", 0xFF000000.toInt()),
            audio = if (o.has("audio")) o.getString("audio") else null,
        )
    }

    private fun stroke(s: Stroke) = JSONObject().apply {
        put("kind", s.kind.name)
        put("color", s.color)
        put("width", s.width.toDouble())
        s.fill?.let { put("fill", it) }
        put("closed", s.closed)
        put("pts", JSONArray().apply {
            s.points.forEach { put(round(it.x)); put(round(it.y)); put(round(it.p)) }
        })
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

    private fun round(v: Float): Double = (v * 100f).roundToInt() / 100.0

    private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
}
