package com.spideranim.editor

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spideranim.export.GifExporter
import com.spideranim.model.Drawing
import com.spideranim.model.Ease
import com.spideranim.model.Keyframe
import com.spideranim.model.Layer
import com.spideranim.model.Project
import com.spideranim.model.ProjectJson
import com.spideranim.model.Pt
import com.spideranim.model.Stroke
import com.spideranim.model.StrokeKind
import com.spideranim.model.Symbol
import com.spideranim.model.Xform
import com.spideranim.model.bounds
import com.spideranim.model.eraseStrokes
import com.spideranim.model.fillable
import com.spideranim.model.insidePolygon
import com.spideranim.model.nearPolyline
import com.spideranim.model.ovalPoints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot
import kotlin.math.min

enum class Tool(val label: String, val key: String) {
    Select("Selection", "V"),
    Transform("Free Transform", "Q"),
    Brush("Brush", "B"),
    Pencil("Pencil", "Y"),
    Pen("Pen", "P"),
    Line("Line", "N"),
    Rect("Rectangle", "R"),
    Oval("Oval", "O"),
    Bucket("Paint Bucket", "K"),
    Eraser("Eraser", "E"),
    Dropper("Eyedropper", "I"),
    Hand("Hand", "H"),
    Zoom("Zoom", "Z"),
}

val ToolGroups = listOf(
    listOf(Tool.Select, Tool.Transform),
    listOf(Tool.Brush, Tool.Pencil, Tool.Pen, Tool.Line, Tool.Rect, Tool.Oval),
    listOf(Tool.Bucket, Tool.Eraser, Tool.Dropper),
    listOf(Tool.Hand, Tool.Zoom),
)

enum class PanelTab(val label: String) { Properties("Properties"), Symbols("Symbols"), Picker("Picker"), Curves("Curves") }

enum class ColorTarget { Stroke, Fill }

/** A stroke being drawn, in the target layer's symbol-local space. */
class LiveStroke(
    val layerId: Long,
    val kind: StrokeKind,
    val color: Int,
    val width: Float,
    val fill: Int?,
    val closed: Boolean,
) {
    val points = mutableStateListOf<Pt>()
    fun toStroke() = Stroke(kind, points.toList(), color, width, fill, closed)
}

class EditorState(app: Application) : AndroidViewModel(app) {

    private val file = File(app.filesDir, "project.json")

    var project by mutableStateOf(load() ?: Demo.project())
        private set
    var frame by mutableIntStateOf(0)
        private set
    var selectedLayerId by mutableStateOf(project.layers.lastOrNull()?.id)
        private set

    var tool by mutableStateOf(Tool.Brush)
        private set
    var strokeColor by mutableIntStateOf(0xFF1C1D22.toInt())
    var fillColor by mutableIntStateOf(0xFFFF7A45.toInt())
    var colorTarget by mutableStateOf(ColorTarget.Stroke)
    var brushSize by mutableFloatStateOf(10f)
    var pencilSize by mutableFloatStateOf(3f)
    var shapeWidth by mutableFloatStateOf(5f)
    var eraserSize by mutableFloatStateOf(28f)
    var smoothing by mutableFloatStateOf(0.45f)
    var pressure by mutableStateOf(true)
    var shapeFill by mutableStateOf(true)
    var zoomOut by mutableStateOf(false)

    var onion by mutableStateOf(false)
    var loop by mutableStateOf(true)
    var playing by mutableStateOf(false)

    var panel by mutableStateOf(PanelTab.Properties)

    var live by mutableStateOf<LiveStroke?>(null)
    val penPoints = mutableStateListOf<Pt>()
    var penLayerId by mutableStateOf<Long?>(null)
        private set

    var viewScale by mutableFloatStateOf(0.3f)
    var viewOffset by mutableStateOf(Offset.Zero)
    var canvasW = 0f
        private set
    var canvasH = 0f
        private set
    var fitRequest by mutableIntStateOf(0)
        private set

    var hint by mutableStateOf<String?>(null)
        private set
    var exportProgress by mutableStateOf<Float?>(null)
        private set

    private val undoStack = ArrayDeque<Project>()
    private val redoStack = ArrayDeque<Project>()
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    private var saveJob: Job? = null
    private var hintJob: Job? = null

    val selectedLayer: Layer? get() = project.layer(selectedLayerId)

    // ---------------------------------------------------------------- history

    /** Records the current project as an undo step. Call once before a run of [update]s. */
    fun checkpoint() = pushHistory(project)

    fun pushHistory(snapshot: Project) {
        undoStack.addLast(snapshot)
        if (undoStack.size > 120) undoStack.removeFirst()
        redoStack.clear()
        syncHistory()
    }

    /** Replace the project without recording history (mid-gesture updates). */
    fun update(p: Project) {
        project = p
        scheduleSave()
    }

    fun commit(p: Project) {
        if (p == project) return
        checkpoint()
        update(p)
    }

    /** Abandons a gesture that already pushed [snapshot] as its undo step. */
    fun revertGesture(snapshot: Project, pushed: Boolean) {
        project = snapshot
        if (pushed && undoStack.isNotEmpty()) undoStack.removeLast()
        syncHistory()
    }

    fun undo() {
        val p = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(project)
        project = p
        afterHistoryJump()
    }

    fun redo() {
        val p = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(project)
        project = p
        afterHistoryJump()
    }

    private fun afterHistoryJump() {
        if (project.layer(selectedLayerId) == null) selectedLayerId = project.layers.lastOrNull()?.id
        frame = frame.coerceIn(0, project.length - 1)
        penPoints.clear()
        syncHistory()
        scheduleSave()
    }

    private fun syncHistory() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    // ---------------------------------------------------------------- navigation

    fun seek(f: Int) {
        frame = f.coerceIn(0, project.length - 1)
    }

    fun advanceFrame() {
        if (frame + 1 >= project.length) {
            if (loop) frame = 0 else playing = false
        } else {
            frame++
        }
    }

    fun togglePlay() {
        if (!playing && !loop && frame >= project.length - 1) frame = 0
        playing = !playing
    }

    private fun keyFrames(): List<Int> {
        val l = selectedLayer
        return (l?.keyframes ?: project.layers.flatMap { it.keyframes }).map { it.frame }.distinct().sorted()
    }

    fun prevKey() {
        playing = false
        seek(keyFrames().lastOrNull { it < frame } ?: 0)
    }

    fun nextKey() {
        playing = false
        seek(keyFrames().firstOrNull { it > frame } ?: (project.length - 1))
    }

    fun selectTool(t: Tool) {
        if (tool == Tool.Pen && t != Tool.Pen) finishPen(closed = false)
        tool = t
    }

    fun selectLayer(id: Long?) {
        if (id != penLayerId) finishPen(closed = false)
        selectedLayerId = id
    }

    // ---------------------------------------------------------------- view

    fun onCanvasSize(w: Float, h: Float) {
        canvasW = w
        canvasH = h
    }

    fun requestFit() {
        fitRequest++
    }

    fun fitView(w: Float, h: Float, pad: Float) {
        val s = min((w - 2 * pad) / project.width, (h - 2 * pad) / project.height).coerceAtLeast(0.02f)
        viewScale = s
        viewOffset = Offset((w - project.width * s) / 2f, (h - project.height * s) / 2f)
    }

    fun panZoom(pan: Offset, zoom: Float, centroid: Offset) {
        val ns = (viewScale * zoom).coerceIn(0.03f, 24f)
        val f = ns / viewScale
        viewOffset = centroid - (centroid - viewOffset) * f + pan
        viewScale = ns
    }

    fun zoomAt(screen: Offset, factor: Float) = panZoom(Offset.Zero, factor, screen)

    fun zoomTo(scale: Float) = panZoom(Offset.Zero, scale / viewScale, Offset(canvasW / 2f, canvasH / 2f))

    // ---------------------------------------------------------------- feedback

    fun showHint(text: String) {
        hint = text
        hintJob?.cancel()
        hintJob = viewModelScope.launch {
            delay(1800)
            hint = null
        }
    }

    // ---------------------------------------------------------------- layers

    /** The selected layer if it can take new strokes, creating a first layer when the stage is empty. */
    fun drawableLayer(): Layer? {
        var l = selectedLayer
        if (l == null) {
            if (project.layers.isEmpty()) {
                addLayer()
                l = selectedLayer
            } else {
                showHint("Select a layer to draw on")
                return null
            }
        }
        if (l == null) return null
        if (!l.visible) { showHint("${l.name} is hidden"); return null }
        if (l.locked) { showHint("${l.name} is locked"); return null }
        return l
    }

    private fun nextName(prefix: String, existing: List<String>): String {
        val n = existing.mapNotNull { it.removePrefix("$prefix ").toIntOrNull() }.maxOrNull() ?: 0
        return "$prefix ${n + 1}"
    }

    private fun newLayerFor(p: Project, sym: Symbol, name: String? = null) = Layer(
        name = name ?: nextName("Layer", p.layers.map { it.name }),
        symbolId = sym.id,
        keyframes = listOf(Keyframe(0, Xform(p.width / 2f, p.height / 2f))),
    )

    private fun Project.insertAboveSelection(layer: Layer): Project {
        val i = layers.indexOfFirst { it.id == selectedLayerId }
        val list = layers.toMutableList()
        if (i < 0) list.add(layer) else list.add(i + 1, layer)
        return copy(layers = list)
    }

    /** New layer holding a fresh, empty symbol. */
    fun addLayer() {
        val sym = Symbol(name = nextName("Symbol", project.symbols.map { it.name }))
        val p = project.copy(symbols = project.symbols + sym)
        val layer = newLayerFor(p, sym)
        commit(p.insertAboveSelection(layer))
        selectedLayerId = layer.id
    }

    /** Places an instance of an existing symbol on a new layer (drag from Library in Animate). */
    fun placeSymbol(symbolId: Long) {
        val sym = project.symbol(symbolId) ?: return
        val layer = newLayerFor(project, sym, sym.name)
        commit(project.insertAboveSelection(layer))
        selectedLayerId = layer.id
        showHint("Placed ${sym.name}")
    }

    fun deleteLayer(id: Long) {
        val i = project.layers.indexOfFirst { it.id == id }
        if (i < 0) return
        commit(project.copy(layers = project.layers.filter { it.id != id }))
        selectedLayerId = project.layers.getOrNull((i - 1).coerceAtLeast(0))?.id ?: project.layers.lastOrNull()?.id
    }

    fun duplicateLayer(id: Long) {
        val l = project.layer(id) ?: return
        val copy = l.copy(id = com.spideranim.model.Ids.next(), name = "${l.name} copy")
        val list = project.layers.toMutableList()
        list.add(list.indexOfFirst { it.id == id } + 1, copy)
        commit(project.copy(layers = list))
        selectedLayerId = copy.id
    }

    fun moveLayer(id: Long, up: Boolean) {
        val list = project.layers.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = if (up) i + 1 else i - 1
        if (i < 0 || j !in list.indices) return
        list[i] = list[j].also { list[j] = list[i] }
        commit(project.copy(layers = list))
    }

    fun renameLayer(id: Long, name: String) {
        if (name.isBlank()) return
        commit(project.mapLayer(id) { it.copy(name = name.trim()) })
    }

    fun toggleVisible(id: Long) = commit(project.mapLayer(id) { it.copy(visible = !it.visible) })
    fun toggleLock(id: Long) = commit(project.mapLayer(id) { it.copy(locked = !it.locked) })

    // ---------------------------------------------------------------- symbols

    fun newSymbol() {
        val sym = Symbol(name = nextName("Symbol", project.symbols.map { it.name }))
        val p = project.copy(symbols = project.symbols + sym)
        val layer = newLayerFor(p, sym, sym.name)
        commit(p.insertAboveSelection(layer))
        selectedLayerId = layer.id
        showHint("${sym.name} created — draw to fill it")
    }

    fun duplicateSymbol(id: Long) {
        val s = project.symbol(id) ?: return
        val copy = Symbol(name = "${s.name} copy", frames = s.frames.map { Drawing(strokes = it.strokes) })
        commit(project.copy(symbols = project.symbols + copy))
    }

    fun renameSymbol(id: Long, name: String) {
        if (name.isBlank()) return
        commit(project.mapSymbol(id) { it.copy(name = name.trim()) })
    }

    fun usages(symbolId: Long) = project.layers.count { it.symbolId == symbolId }

    fun deleteSymbol(id: Long) {
        val n = usages(id)
        if (n > 0) {
            showHint("Used by $n layer${if (n == 1) "" else "s"} — swap or delete those first")
            return
        }
        commit(project.copy(symbols = project.symbols.filter { it.id != id }))
    }

    /** Animate's "Swap Symbol": the layer keeps its motion but shows another symbol. */
    fun swapSymbol(layerId: Long, symbolId: Long) {
        val sym = project.symbol(symbolId) ?: return
        commit(project.mapLayer(layerId) { l ->
            l.copy(symbolId = sym.id, keyframes = l.keyframes.map { it.copy(symbolFrame = it.symbolFrame.coerceIn(0, sym.frames.size - 1)) })
        })
    }

    // ---------------------------------------------------------------- symbol picker

    fun pickSymbolFrame(index: Int) {
        val l = selectedLayer ?: return
        commit(project.mapLayer(l.id) { it.withKey(frame) { k -> k.copy(symbolFrame = index) } })
    }

    fun addSymbolFrame(duplicate: Boolean) {
        val l = selectedLayer ?: return
        val sym = project.symbol(l.symbolId) ?: return
        val current = sym.frameAt(l.poseAt(frame).symbolFrame)
        val d = if (duplicate) Drawing(strokes = current.strokes) else Drawing()
        val index = sym.frames.size
        val p = project.mapSymbol(sym.id) { it.copy(frames = it.frames + d) }
            .mapLayer(l.id) { it.withKey(frame) { k -> k.copy(symbolFrame = index) } }
        commit(p)
    }

    fun deleteSymbolFrame(index: Int) {
        val l = selectedLayer ?: return
        val sym = project.symbol(l.symbolId) ?: return
        if (sym.frames.size <= 1) {
            showHint("A symbol needs at least one drawing")
            return
        }
        var p = project.mapSymbol(sym.id) { s -> s.copy(frames = s.frames.filterIndexed { i, _ -> i != index }) }
        p = p.copy(layers = p.layers.map { layer ->
            if (layer.symbolId != sym.id) layer else layer.copy(keyframes = layer.keyframes.map { k ->
                when {
                    k.symbolFrame > index -> k.copy(symbolFrame = k.symbolFrame - 1)
                    k.symbolFrame == index -> k.copy(symbolFrame = (index - 1).coerceAtLeast(0))
                    else -> k
                }
            })
        })
        commit(p)
    }

    // ---------------------------------------------------------------- keyframes

    fun insertKeyframe() {
        val l = selectedLayer ?: return showHint("Select a layer first")
        if (l.keyAt(frame) != null) return showHint("Already a keyframe")
        commit(project.mapLayer(l.id) { it.withKey(frame) { k -> k } })
    }

    fun deleteKeyframe() {
        val l = selectedLayer ?: return
        if (l.keyAt(frame) == null) return showHint("No keyframe on this frame")
        if (l.keyframes.size <= 1) return showHint("A layer keeps at least one keyframe")
        commit(project.mapLayer(l.id) { it.copy(keyframes = it.keyframes.filter { k -> k.frame != frame }) })
    }

    fun toggleTween() {
        val l = selectedLayer ?: return
        val (a, _) = l.segmentAt(frame) ?: return
        commit(project.mapLayer(l.id) { it.withKey(a.frame) { k -> k.copy(tween = !k.tween) } })
    }

    fun setEase(layerId: Long, keyFrame: Int, ease: Ease) {
        update(project.mapLayer(layerId) { it.withKey(keyFrame) { k -> k.copy(ease = ease) } })
    }

    fun moveKeyframe(layerId: Long, from: Int, to: Int) {
        update(project.mapLayer(layerId) { l ->
            if (l.keyAt(to) != null) l
            else l.copy(keyframes = l.keyframes.map { if (it.frame == from) it.copy(frame = to) else it }.sortedBy { it.frame })
        })
    }

    /** Auto-keys: editing a transform between keys creates a key on the current frame. */
    fun setXform(layerId: Long, xf: Xform) {
        update(project.mapLayer(layerId) { it.withKey(frame) { k -> k.copy(xf = xf) } })
    }

    fun resetTransform() {
        val l = selectedLayer ?: return
        val cur = l.poseAt(frame).xf
        commit(project.mapLayer(l.id) { it.withKey(frame) { k -> k.copy(xf = Xform(cur.x, cur.y)) } })
    }

    // ---------------------------------------------------------------- drawing

    fun addStroke(layerId: Long, stroke: Stroke) {
        val l = project.layer(layerId) ?: return
        val idx = l.poseAt(frame).symbolFrame
        commit(project.mapSymbol(l.symbolId) { s -> s.mapFrame(idx) { d -> d.copy(strokes = d.strokes + stroke) } })
    }

    /** Applies the eraser without history; returns whether anything was removed. */
    fun eraseAt(layerId: Long, local: Pt, radius: Float): Boolean {
        val l = project.layer(layerId) ?: return false
        val idx = l.poseAt(frame).symbolFrame
        val d = project.symbol(l.symbolId)?.frameAt(idx) ?: return false
        val result = eraseStrokes(d.strokes, local.x, local.y, radius) ?: return false
        update(project.mapSymbol(l.symbolId) { s -> s.mapFrame(idx) { it.copy(strokes = result) } })
        return true
    }

    fun fillAt(stage: Pt) {
        val l = drawableLayer() ?: return
        val xf = l.poseAt(frame).xf
        val local = xf.toLocal(stage)
        val idx = l.poseAt(frame).symbolFrame
        val d = project.drawingOf(l, frame) ?: return
        val gap = 24f / (viewScale * xf.avgScale)
        val hit = d.strokes.lastOrNull { it.fillable(gap) && insidePolygon(it.points, local.x, local.y) }
        if (hit == null) {
            showHint("Tap inside a closed shape")
            return
        }
        val filled = hit.with(fill = fillColor, closed = true)
        commit(project.mapSymbol(l.symbolId) { s ->
            s.mapFrame(idx) { it.copy(strokes = it.strokes.map { st -> if (st === hit) filled else st }) }
        })
    }

    /** Eyedropper: strokes set the stroke colour, fills set the fill colour, like Animate. */
    fun sampleAt(stage: Pt, tolerance: Float) {
        for (l in project.layers.asReversed()) {
            if (!l.visible) continue
            val xf = l.poseAt(frame).xf
            val local = xf.toLocal(stage)
            val d = project.drawingOf(l, frame) ?: continue
            for (s in d.strokes.asReversed()) {
                if (nearPolyline(s.points, s.closed, local.x, local.y, s.width / 2f + tolerance / xf.avgScale)) {
                    strokeColor = s.color
                    colorTarget = ColorTarget.Stroke
                    return
                }
                val f = s.fill
                if (f != null && insidePolygon(s.points, local.x, local.y)) {
                    fillColor = f
                    colorTarget = ColorTarget.Fill
                    return
                }
            }
        }
    }

    /** Topmost visible, unlocked layer whose current drawing covers [stage]. */
    fun layerAt(stage: Pt, pad: Float): Layer? {
        for (l in project.layers.asReversed()) {
            if (!l.visible || l.locked) continue
            val xf = l.poseAt(frame).xf
            val local = xf.toLocal(stage)
            val b = project.drawingOf(l, frame)?.bounds() ?: continue
            if (b.contains(local.x, local.y, pad / xf.avgScale)) return l
        }
        return null
    }

    // ---------------------------------------------------------------- pen tool

    fun penTap(layer: Layer, local: Pt, closeRadius: Float) {
        if (penLayerId != layer.id) {
            finishPen(closed = false)
            penLayerId = layer.id
        }
        val first = penPoints.firstOrNull()
        val last = penPoints.lastOrNull()
        when {
            first != null && penPoints.size >= 3 && hypot(local.x - first.x, local.y - first.y) <= closeRadius -> finishPen(closed = true)
            last != null && hypot(local.x - last.x, local.y - last.y) <= closeRadius -> finishPen(closed = false)
            else -> penPoints.add(local)
        }
    }

    fun finishPen(closed: Boolean) {
        val id = penLayerId
        if (id != null && penPoints.size >= 2) {
            val l = project.layer(id)
            val scale = l?.poseAt(frame)?.xf?.avgScale ?: 1f
            addStroke(id, Stroke(
                kind = StrokeKind.Pen,
                points = penPoints.toList(),
                color = strokeColor,
                width = shapeWidth / scale,
                fill = if (closed && shapeFill) fillColor else null,
                closed = closed,
            ))
        }
        penPoints.clear()
        penLayerId = null
    }

    // ---------------------------------------------------------------- document

    fun setFps(fps: Int) = commit(project.copy(fps = fps))

    fun setLength(length: Int) {
        commit(project.copy(length = length.coerceIn(1, 2000)))
        seek(frame)
    }

    fun setBackground(color: Int) = commit(project.copy(background = color))

    fun setStageSize(w: Int, h: Int) {
        val dx = (w - project.width) / 2f
        val dy = (h - project.height) / 2f
        commit(project.copy(width = w, height = h, layers = project.layers.map { l ->
            l.copy(keyframes = l.keyframes.map { k -> k.copy(xf = k.xf.copy(x = k.xf.x + dx, y = k.xf.y + dy)) })
        }))
        requestFit()
    }

    fun renameProject(name: String) {
        if (name.isNotBlank()) commit(project.copy(name = name.trim()))
    }

    fun newProject() {
        checkpoint()
        val sym = Symbol(name = "Symbol 1")
        val p = Project(symbols = listOf(sym))
        val layer = newLayerFor(p, sym, "Layer 1")
        update(p.copy(layers = listOf(layer)))
        selectedLayerId = layer.id
        frame = 0
        playing = false
        penPoints.clear()
        requestFit()
    }

    // ---------------------------------------------------------------- persistence & export

    private fun load(): Project? = runCatching {
        if (file.exists()) ProjectJson.read(file.readText()) else null
    }.getOrNull()

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(700)
            writeNow(project)
        }
    }

    fun saveNow() {
        saveJob?.cancel()
        val p = project
        viewModelScope.launch { writeNow(p) }
    }

    private suspend fun writeNow(p: Project) = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(file.parentFile, "project.json.tmp")
            tmp.writeText(ProjectJson.write(p))
            tmp.renameTo(file)
        }
    }

    fun exportGif() {
        if (exportProgress != null) return
        playing = false
        val p = project
        val ctx = getApplication<Application>()
        exportProgress = 0f
        viewModelScope.launch {
            val safeName = p.name.replace(Regex("[^A-Za-z0-9_-]+"), "_").ifBlank { "animation" }
            val out = File(File(ctx.cacheDir, "exports"), "$safeName.gif")
            val ok = withContext(Dispatchers.Default) {
                runCatching {
                    GifExporter.export(p, out) { f -> viewModelScope.launch(Dispatchers.Main) { exportProgress = f } }
                }.isSuccess
            }
            exportProgress = null
            if (!ok) {
                showHint("Export failed")
                return@launch
            }
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", out)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/gif"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(send, "Share ${p.name}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/** The first-run scene: a squash-and-stretch ball, so the timeline and curves have something to show. */
private object Demo {
    fun project(): Project {
        val ink = 0xFF1C1D22.toInt()
        val ball = Symbol(
            name = "Ball",
            frames = listOf(
                Drawing(strokes = listOf(Stroke(StrokeKind.Oval, ovalPoints(-70f, -70f, 70f, 70f), ink, 6f, 0xFFFF7A45.toInt(), true))),
                Drawing(strokes = listOf(
                    Stroke(StrokeKind.Oval, ovalPoints(-70f, -70f, 70f, 70f), ink, 6f, 0xFF4F7CFF.toInt(), true),
                    Stroke(StrokeKind.Oval, ovalPoints(-40f, -44f, -8f, -20f), 0xFFFFFFFF.toInt(), 2f, 0xFFFFFFFF.toInt(), true),
                )),
            ),
        )
        val ground = Symbol(
            name = "Ground",
            frames = listOf(Drawing(strokes = listOf(
                Stroke(StrokeKind.Line, listOf(Pt(-520f, 0f), Pt(520f, 0f)), 0xFFD5D6DC.toInt(), 6f),
            ))),
        )
        val groundLayer = Layer(name = "Ground", symbolId = ground.id, keyframes = listOf(Keyframe(0, Xform(960f, 846f))))
        val ballLayer = Layer(
            name = "Ball",
            symbolId = ball.id,
            keyframes = listOf(
                Keyframe(0, Xform(960f, 300f), ease = Ease.In),
                Keyframe(12, Xform(960f, 782f, 1.18f, 0.84f), ease = Ease.Out),
                Keyframe(24, Xform(960f, 300f)),
            ),
        )
        return Project(name = "Bouncing ball", length = 24, symbols = listOf(ball, ground), layers = listOf(groundLayer, ballLayer))
    }
}
