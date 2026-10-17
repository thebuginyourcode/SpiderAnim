package com.spideranim.model

/** Pure editing operations. The editor wraps these with history and selection. */
object Edits {

    // ------------------------------------------------------------------ frame by frame

    /** Draws into the drawing at [frame]; an empty frame gets a new one-frame drawing. */
    fun addStroke(layer: Layer, frame: Int, stroke: Stroke): Layer {
        val e = layer.exposureAt(frame)
        return if (e != null) {
            layer.mapExposure(e.id) { it.copy(strokes = it.strokes + stroke) }
        } else {
            layer.copy(exposures = (layer.exposures + Exposure(start = frame, strokes = listOf(stroke))).sortedBy { it.start })
        }
    }

    fun setStrokes(layer: Layer, frame: Int, strokes: List<Stroke>): Layer {
        val e = layer.exposureAt(frame) ?: return layer
        return layer.mapExposure(e.id) { it.copy(strokes = strokes) }
    }

    /**
     * "Add Drawing": a new drawing right after the current one, pushing later drawings back a
     * frame. On an empty frame the drawing goes on that frame. Returns the layer and the new
     * drawing's frame.
     */
    fun addDrawing(layer: Layer, frame: Int, copy: Boolean): Pair<Layer, Int> {
        val cur = layer.exposureAt(frame)
        if (cur == null) {
            val prev = layer.exposures.lastOrNull { it.end <= frame }
            val strokes = if (copy && prev != null) prev.strokes else emptyList()
            val e = Exposure(start = frame, strokes = strokes)
            return layer.copy(exposures = (layer.exposures + e).sortedBy { it.start }) to frame
        }
        val at = cur.end
        val shifted = layer.shiftExposures(at, 1)
        val e = Exposure(start = at, strokes = if (copy) cur.strokes else emptyList())
        return shifted.copy(exposures = (shifted.exposures + e).sortedBy { it.start }) to at
    }

    /** "Extend": holds the current drawing one frame longer, pushing later drawings back. */
    fun extend(layer: Layer, frame: Int): Layer {
        val cur = layer.exposureAt(frame)
        if (cur == null) {
            // On an empty frame, stretch the previous drawing up to here.
            val prev = layer.exposures.lastOrNull { it.end <= frame } ?: return layer
            return layer.mapExposure(prev.id) { it.copy(length = frame - it.start + 1) }
        }
        return layer.shiftExposures(cur.end, 1).mapExposure(cur.id) { it.copy(length = it.length + 1) }
    }

    /** Holds the current drawing one frame shorter, pulling later drawings forward. */
    fun shorten(layer: Layer, frame: Int): Layer {
        val cur = layer.exposureAt(frame) ?: return layer
        if (cur.length <= 1) return layer
        return layer.mapExposure(cur.id) { it.copy(length = it.length - 1) }.shiftExposures(cur.end, -1)
    }

    fun deleteDrawing(layer: Layer, frame: Int): Layer {
        val cur = layer.exposureAt(frame) ?: return layer
        return layer.copy(exposures = layer.exposures.filter { it.id != cur.id })
    }

    /** Moves a drawing to start at [start] if every frame it would cover is free. */
    fun moveExposure(layer: Layer, id: Long, start: Int): Layer {
        val e = layer.exposures.firstOrNull { it.id == id } ?: return layer
        if (start < 0) return layer
        val clash = layer.exposures.any { it.id != id && it.start < start + e.length && start < it.end }
        if (clash) return layer
        return layer.copy(exposures = layer.exposures.map { if (it.id == id) it.copy(start = start) else it }.sortedBy { it.start })
    }

    fun moveKey(layer: Layer, prop: Prop, from: Int, to: Int): Layer {
        val t = layer.track(prop) ?: return layer
        if (to < 0 || t.keyAt(to) != null) return layer
        val moved = t.copy(keys = t.keys.map { if (it.frame == from) it.copy(frame = to) else it }.sortedBy { it.frame })
        return layer.copy(tracks = layer.tracks.map { if (it.prop == prop) moved else it })
    }

    fun setEase(layer: Layer, prop: Prop, frame: Int, ease: Ease): Layer =
        layer.copy(tracks = layer.tracks.map { t ->
            if (t.prop != prop) t else t.copy(keys = t.keys.map { if (it.frame == frame) it.copy(ease = ease) else it })
        })

    /** Last frame any drawing or key in [clip] reaches, so the clip can grow to fit. */
    fun contentEnd(clip: Clip): Int = clip.layers.maxOfOrNull { l ->
        maxOf(l.exposures.maxOfOrNull { it.end } ?: 0, l.tracks.maxOfOrNull { t -> (t.keys.maxOfOrNull { it.frame } ?: -1) + 1 } ?: 0)
    } ?: 0

    fun fitLength(clip: Clip): Clip = clip.copy(length = maxOf(clip.length, contentEnd(clip)))

    // ------------------------------------------------------------------ symbols

    fun newSymbol(p: Project, name: String): Pair<Project, Clip> {
        val sym = Clip(
            name = name,
            length = p.fps,
            layers = listOf(Layer(name = "Drawing 1", type = LayerType.Drawing)),
        )
        return p.copy(symbols = p.symbols + sym) to sym
    }

    /** A layer showing [symbolId], with its origin at ([x], [y]) in the parent clip. */
    fun symbolLayer(p: Project, symbolId: Long, x: Float, y: Float): Layer {
        val name = p.clip(symbolId)?.name ?: "Symbol"
        return Layer(name = name, type = LayerType.Symbol, symbolId = symbolId, x = x, y = y)
    }

    /**
     * Moves a layer into a new symbol and puts a symbol layer in its place. The symbol's origin is
     * the centre of the layer's art at [frame], so it scales and rotates about its middle.
     */
    fun convertToSymbol(p: Project, clipId: Long, layerId: Long, frame: Int): Pair<Project, Clip>? {
        val clip = p.clip(clipId) ?: return null
        val layer = clip.layer(layerId) ?: return null
        if (layer.type == LayerType.Camera || layer.type == LayerType.Audio) return null
        val b = p.contentBounds(layer, frame)?.let { layer.xfAt(frame).mapBounds(it) }
        val cx = b?.cx ?: 0f
        val cy = b?.cy ?: 0f
        val inner = layer.copy(
            id = Ids.next(),
            x = layer.x - cx,
            y = layer.y - cy,
            tracks = layer.tracks.map { t ->
                if (t.prop != Prop.Position) t else t.copy(keys = t.keys.map { it.copy(v = V2(it.v.a - cx, it.v.b - cy)) })
            },
        )
        val sym = Clip(name = layer.name, length = clip.length, layers = listOf(inner))
        val replacement = Layer(id = layer.id, name = layer.name, type = LayerType.Symbol, symbolId = sym.id, x = cx, y = cy)
        val updated = p.copy(symbols = p.symbols + sym).mapClip(clipId) { c -> c.mapLayer(layerId) { replacement } }
        return updated to sym
    }

    /** Symbols that (directly or not) contain [symbolId]; placing one of them inside it would loop. */
    fun wouldNest(p: Project, outerClipId: Long, symbolId: Long): Boolean {
        if (outerClipId == symbolId) return true
        val seen = HashSet<Long>()
        fun contains(clipId: Long): Boolean {
            if (clipId == outerClipId) return true
            if (!seen.add(clipId)) return false
            val c = p.clip(clipId) ?: return false
            return c.layers.any { it.type == LayerType.Symbol && contains(it.symbolId) }
        }
        return contains(symbolId)
    }
}
