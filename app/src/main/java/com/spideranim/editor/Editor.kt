package com.spideranim.editor

import android.app.Application
import android.content.Intent
import android.net.Uri
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
import com.spideranim.export.Exporter
import com.spideranim.model.Clip
import com.spideranim.model.Ease
import com.spideranim.model.Edits
import com.spideranim.model.Ids
import com.spideranim.model.Layer
import com.spideranim.model.LayerType
import com.spideranim.model.Marker
import com.spideranim.model.MarkerColors
import com.spideranim.model.Project
import com.spideranim.model.ProjectInfo
import com.spideranim.model.ProjectStore
import com.spideranim.model.Prop
import com.spideranim.model.Pt
import com.spideranim.model.Stroke
import com.spideranim.model.StrokeKind
import com.spideranim.model.V2
import com.spideranim.model.Xf
import com.spideranim.model.contentBounds
import com.spideranim.model.eraseStrokes
import com.spideranim.model.fillable
import com.spideranim.model.insidePolygon
import com.spideranim.model.nearPolyline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

enum class Tool(val label: String) {
    Select("Select"),
    Lasso("Lasso"),
    Brush("Brush"),
    Eraser("Eraser"),
    Fill("Fill"),
    Eyedropper("Eyedropper"),
    Text("Text"),
    Pen("Pen"),
}

enum class BrushStyle(val label: String, val kind: StrokeKind) {
    Ink("Ink", StrokeKind.Ink),
    Pencil("Pencil", StrokeKind.Pencil),
    Marker("Marker", StrokeKind.Marker),
}

/** Pages of the right-hand panel. */
enum class Panel { Color, Layers, Properties, Markers, Library, Easing }

enum class ColorTarget { Stroke, Fill }

/** A stroke being drawn, in the target layer's local space. */
class LiveStroke(val layerId: Long, val kind: StrokeKind, val color: Int, val width: Float) {
    val points = mutableStateListOf<Pt>()
    fun toStroke() = Stroke(kind, points.toList(), color, width)
}

data class TextPrompt(val at: Pt, val layerId: Long? = null, val initial: String = "")

/** Strokes picked with the lasso, by index into the drawing at the current frame. */
data class LassoSelection(val layerId: Long, val exposureId: Long, val indices: Set<Int>)

class Editor(app: Application) : AndroidViewModel(app) {

    val store = ProjectStore(File(app.filesDir, "projects"))
    val audioDir = File(app.filesDir, "audio").apply { mkdirs() }
    val audio = AudioEngine(this)

    // ---------------------------------------------------------------- gallery
    var gallery by mutableStateOf(removeDemo())
        private set
    var isOpen by mutableStateOf(false)
        private set
    var thumbVersion by mutableIntStateOf(0)
        private set

    // ---------------------------------------------------------------- document
    var project by mutableStateOf(Project.blank("Untitled", 1920, 1080, 24))
        private set
    /** Clip ids entered from the scene; empty means editing the scene. */
    var path by mutableStateOf(listOf<Long>())
        private set
    var frame by mutableIntStateOf(0)
        private set
    private val savedFrames = HashMap<Long, Int>()

    var selLayerId by mutableStateOf<Long?>(null)
        private set
    /** The selected property row in the timeline; null means the layer's main row. */
    var selProp by mutableStateOf<Prop?>(null)
        private set
    var lasso by mutableStateOf<LassoSelection?>(null)

    // ---------------------------------------------------------------- tools
    var tool by mutableStateOf(Tool.Brush)
        private set
    var toolOptions by mutableStateOf<Tool?>(null)
    var brushStyle by mutableStateOf(BrushStyle.Ink)
    var brushSize by mutableFloatStateOf(12f)
    var brushOpacity by mutableFloatStateOf(1f)
    var smoothing by mutableFloatStateOf(0.4f)
    var pressure by mutableStateOf(true)
    var eraserSize by mutableFloatStateOf(30f)
    var eraseWhole by mutableStateOf(false)
    var penWidth by mutableFloatStateOf(6f)
    var penFill by mutableStateOf(true)
    var textSize by mutableFloatStateOf(120f)
    var strokeColor by mutableIntStateOf(0xFF000000.toInt())
    var fillColor by mutableIntStateOf(0xFF6FB8C9.toInt())
    var colorTarget by mutableStateOf(ColorTarget.Stroke)

    // ---------------------------------------------------------------- view & playback
    var panel by mutableStateOf<Panel?>(null)
    var timelineOpen by mutableStateOf(true)
    var fullscreen by mutableStateOf(false)
    var preview by mutableStateOf(false)
    var onion by mutableStateOf(false)
    var loop by mutableStateOf(true)
    var playing by mutableStateOf(false)
        private set
    var motionPaths by mutableStateOf(true)
    var autoKey by mutableStateOf(false)
    var snap by mutableStateOf(true)

    var viewScale by mutableFloatStateOf(0.3f)
    var viewOffset by mutableStateOf(Offset.Zero)
    var fitRequest by mutableIntStateOf(0)
        private set

    var live by mutableStateOf<LiveStroke?>(null)
    val penPoints = mutableStateListOf<Pt>()
    var penLayerId: Long? = null
        private set
    val lassoPath = mutableStateListOf<Pt>()

    /** Text tool: where new text goes, or the text layer being edited. */
    var textPrompt by mutableStateOf<TextPrompt?>(null)

    var hint by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf<String?>(null)
        private set
    var busyProgress by mutableFloatStateOf(0f)
        private set

    private val undoStack = ArrayDeque<Project>()
    private val redoStack = ArrayDeque<Project>()
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    private var saveJob: Job? = null
    private var hintJob: Job? = null

    // ---------------------------------------------------------------- derived

    val clipId: Long get() = path.lastOrNull() ?: project.scene.id
    val clip: Clip get() = project.clip(clipId) ?: project.scene
    val inSymbol: Boolean get() = path.isNotEmpty()
    val selLayer: Layer? get() = clip.layer(selLayerId)

    /** Stroke colour with brush opacity applied. */
    val inkColor: Int
        get() {
            val a = ((strokeColor ushr 24) * brushOpacity).toInt().coerceIn(0, 255)
            return (a shl 24) or (strokeColor and 0xFFFFFF)
        }

    /** Breadcrumb of the clips being edited, scene first. */
    val crumbs: List<Clip> get() = listOf(project.scene) + path.mapNotNull { project.clip(it) }

    // ---------------------------------------------------------------- projects

    /** Earlier builds put a lip-sync demo in the gallery; take it out once. */
    private fun removeDemo(): List<ProjectInfo> {
        val flag = File(getApplication<Application>().filesDir, "demo-removed")
        if (!flag.exists()) {
            store.list().filter { it.name == "Lip Sync Demo" }.forEach { store.delete(it.id) }
            runCatching { flag.writeText("1") }
        }
        return store.list()
    }

    fun refreshGallery() {
        gallery = store.list()
    }

    fun createProject(name: String, w: Int, h: Int, fps: Int) {
        val p = Project.blank(name.ifBlank { "Untitled" }, w, h, fps)
        store.save(p)
        open(p)
    }

    fun openProject(id: Long) {
        val p = store.load(id) ?: return showHint("Couldn't open that project")
        open(p)
    }

    private fun open(p: Project) {
        project = p
        path = emptyList()
        frame = 0
        savedFrames.clear()
        undoStack.clear()
        redoStack.clear()
        syncHistory()
        selLayerId = p.scene.layers.lastOrNull { it.type == LayerType.Drawing }?.id ?: p.scene.layers.lastOrNull()?.id
        selProp = null
        lasso = null
        panel = null
        playing = false
        isOpen = true
        fitRequest++
        audio.load(p)
    }

    fun closeProject() {
        stop()
        saveNow()
        isOpen = false
        audio.release()
        viewModelScope.launch {
            withContext(Dispatchers.Default) { runCatching { Exporter.writeThumbnail(project, store.thumbnail(project.id)) } }
            thumbVersion++
            refreshGallery()
        }
    }

    fun renameProject(id: Long, name: String) {
        if (name.isBlank()) return
        if (isOpen && project.id == id) {
            commit(project.copy(name = name.trim()))
        } else {
            store.load(id)?.let { store.save(it.copy(name = name.trim())) }
            refreshGallery()
        }
    }

    fun duplicateProject(id: Long) {
        val p = store.load(id) ?: return
        val copy = p.copy(id = Ids.next(), name = "${p.name} copy")
        store.save(copy)
        runCatching { store.thumbnail(id).copyTo(store.thumbnail(copy.id), overwrite = true) }
        refreshGallery()
    }

    fun deleteProject(id: Long) {
        store.delete(id)
        refreshGallery()
    }

    // ---------------------------------------------------------------- history

    fun checkpoint() = pushHistory(project)

    fun pushHistory(snapshot: Project) {
        undoStack.addLast(snapshot)
        if (undoStack.size > 100) undoStack.removeFirst()
        redoStack.clear()
        syncHistory()
    }

    fun update(p: Project) {
        project = p
        scheduleSave()
    }

    fun commit(p: Project) {
        if (p == project) return
        checkpoint()
        update(p)
    }

    fun revertGesture(snapshot: Project, pushed: Boolean) {
        project = snapshot
        if (pushed && undoStack.isNotEmpty()) undoStack.removeLast()
        syncHistory()
    }

    fun undo() {
        val p = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(project)
        project = p
        afterJump()
    }

    fun redo() {
        val p = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(project)
        project = p
        afterJump()
    }

    private fun afterJump() {
        path = path.filter { project.clip(it) != null }
        if (clip.layer(selLayerId) == null) selLayerId = clip.layers.lastOrNull()?.id
        lasso = null
        penPoints.clear()
        frame = frame.coerceIn(0, clip.length - 1)
        syncHistory()
        scheduleSave()
    }

    private fun syncHistory() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    /** Applies [f] to the clip being edited. */
    private fun editClip(history: Boolean = true, f: (Clip) -> Clip) {
        val p = project.mapClip(clipId) { Edits.fitLength(f(it)) }
        if (history) commit(p) else update(p)
    }

    private fun editLayer(id: Long, history: Boolean = true, f: (Layer) -> Layer) = editClip(history) { c -> c.mapLayer(id, f) }

    // ---------------------------------------------------------------- context

    fun enterSymbol(symbolId: Long) {
        if (project.clip(symbolId) == null) return
        stop()
        finishPen()
        savedFrames[clipId] = frame
        path = path + symbolId
        frame = savedFrames[symbolId] ?: 0
        selLayerId = clip.layers.lastOrNull()?.id
        selProp = null
        lasso = null
        if (panel == Panel.Markers) panel = null
        fitRequest++
    }

    /** Back to the clip at [depth] of the breadcrumb (0 = scene). */
    fun exitTo(depth: Int) {
        if (depth >= path.size) return
        stop()
        finishPen()
        savedFrames[clipId] = frame
        val leaving = path.getOrNull(depth)
        path = path.take(depth)
        frame = (savedFrames[clipId] ?: 0).coerceIn(0, clip.length - 1)
        selLayerId = clip.layers.firstOrNull { it.type == LayerType.Symbol && it.symbolId == leaving }?.id ?: clip.layers.lastOrNull()?.id
        selProp = null
        lasso = null
        fitRequest++
    }

    // ---------------------------------------------------------------- playback

    fun seek(f: Int) {
        frame = f.coerceIn(0, clip.length - 1)
        if (playing) audio.sync(frame)
    }

    fun step(d: Int) {
        stop()
        val n = clip.length
        seek(((frame + d) % n + n) % n)
    }

    fun toStart() { stop(); seek(0) }
    fun toEnd() { stop(); seek(clip.length - 1) }

    fun togglePlay() = if (playing) stop() else play()

    fun play() {
        if (!loop && frame >= clip.length - 1) frame = 0
        finishPen()
        playing = true
        toolOptions = null
        audio.play(frame)
    }

    fun stop() {
        if (!playing) return
        playing = false
        audio.pause()
    }

    fun advance() {
        if (frame + 1 >= clip.length) {
            if (loop) {
                frame = 0
                audio.sync(0)
            } else {
                stop()
            }
        } else {
            frame++
        }
    }

    // ---------------------------------------------------------------- selection & tools

    fun selectTool(t: Tool) {
        if (t == tool) {
            toolOptions = if (toolOptions == t) null else t
            return
        }
        finishPen()
        if (t != Tool.Lasso && t != Tool.Select) lasso = null
        tool = t
        toolOptions = null
    }

    fun selectLayer(id: Long?, prop: Prop? = null) {
        if (id != penLayerId) finishPen()
        if (id != selLayerId) lasso = null
        selLayerId = id
        selProp = prop
    }

    fun showHint(text: String) {
        hint = text
        hintJob?.cancel()
        hintJob = viewModelScope.launch {
            delay(1800)
            hint = null
        }
    }

    // ---------------------------------------------------------------- view

    fun fitView(w: Float, h: Float, pad: Float) {
        val s = min((w - 2 * pad) / project.width, (h - 2 * pad) / project.height).coerceAtLeast(0.02f)
        viewScale = s
        viewOffset = Offset((w - project.width * s) / 2f, (h - project.height * s) / 2f)
    }

    fun panZoom(pan: Offset, zoom: Float, centroid: Offset) {
        val ns = (viewScale * zoom).coerceIn(0.03f, 30f)
        val f = ns / viewScale
        viewOffset = centroid - (centroid - viewOffset) * f + pan
        viewScale = ns
    }

    fun requestFit() {
        fitRequest++
    }

    /** View (stage) space -> the edited clip's space: through the camera in the scene, centred in a symbol. */
    fun viewToClip(p: Pt): Pt {
        if (inSymbol) return Pt(p.x - project.width / 2f, p.y - project.height / 2f, p.p)
        val cam = project.scene.camera ?: return p
        return cam.xfAt(frame).toStage(p)
    }

    fun clipToView(p: Pt): Pt {
        if (inSymbol) return Pt(p.x + project.width / 2f, p.y + project.height / 2f, p.p)
        val cam = project.scene.camera ?: return p
        return cam.xfAt(frame).toLocal(p)
    }

    // ---------------------------------------------------------------- layers

    private fun nextName(prefix: String, names: List<String>): String {
        val n = names.mapNotNull { it.removePrefix("$prefix ").toIntOrNull() }.maxOrNull() ?: 0
        return "$prefix ${n + 1}"
    }

    private fun insertLayer(layer: Layer) {
        editClip { c ->
            val i = c.layers.indexOfFirst { it.id == selLayerId }
            val list = c.layers.toMutableList()
            if (i < 0 || layer.type == LayerType.Camera) list.add(layer) else list.add(i + 1, layer)
            c.copy(layers = list)
        }
        selectLayer(layer.id)
    }

    /** Where a new layer's origin goes: the middle of what the view shows. */
    private fun centre(): Pt = if (inSymbol) Pt(0f, 0f) else Pt(project.width / 2f, project.height / 2f)

    fun addDrawingLayer() {
        val c = centre()
        insertLayer(Layer(name = nextName("Drawing", clip.layers.map { it.name }), type = LayerType.Drawing, x = c.x, y = c.y, ax = c.x, ay = c.y))
    }

    fun addCameraLayer() {
        if (inSymbol) return showHint("The camera belongs to the scene")
        if (project.scene.camera != null) return showHint("The scene already has a camera")
        val c = centre()
        insertLayer(Layer(name = "Camera", type = LayerType.Camera, x = c.x, y = c.y, ax = c.x, ay = c.y))
    }

    fun addTextLayer(at: Pt, text: String) {
        if (text.isBlank()) return
        insertLayer(Layer(name = text.lineSequence().first().take(20), type = LayerType.Text, x = at.x, y = at.y, text = text, textSize = textSize, textColor = strokeColor))
    }

    fun editText(layerId: Long, text: String) {
        if (text.isBlank()) return
        editLayer(layerId) { it.copy(text = text, name = text.lineSequence().first().take(20)) }
    }

    fun placeSymbol(symbolId: Long) {
        if (Edits.wouldNest(project, clipId, symbolId)) return showHint("A symbol can't contain itself")
        val c = centre()
        insertLayer(Edits.symbolLayer(project, symbolId, c.x, c.y))
        panel = null
    }

    fun deleteLayer(id: Long) {
        val i = clip.layers.indexOfFirst { it.id == id }
        if (i < 0) return
        editClip { c -> c.copy(layers = c.layers.filter { it.id != id }) }
        selectLayer(clip.layers.getOrNull((i - 1).coerceAtLeast(0))?.id ?: clip.layers.lastOrNull()?.id)
    }

    fun duplicateLayer(id: Long) {
        val l = clip.layer(id) ?: return
        if (l.type == LayerType.Camera) return
        val copy = l.copy(id = Ids.next(), name = "${l.name} copy", exposures = l.exposures.map { it.copy(id = Ids.next()) })
        insertLayer(copy)
    }

    fun moveLayer(id: Long, up: Boolean) {
        editClip { c ->
            val list = c.layers.toMutableList()
            val i = list.indexOfFirst { it.id == id }
            val j = if (up) i + 1 else i - 1
            if (i >= 0 && j in list.indices) list[i] = list[j].also { list[j] = list[i] }
            c.copy(layers = list)
        }
    }

    fun renameLayer(id: Long, name: String) {
        if (name.isNotBlank()) editLayer(id) { it.copy(name = name.trim()) }
    }

    fun toggleVisible(id: Long) = editLayer(id) { it.copy(visible = !it.visible) }
    fun toggleLock(id: Long) = editLayer(id) { it.copy(locked = !it.locked) }

    fun convertToSymbol(id: Long) {
        val (p, sym) = Edits.convertToSymbol(project, clipId, id, frame) ?: return showHint("This layer can't become a symbol")
        commit(p)
        showHint("${sym.name} is now a symbol")
    }

    // ---------------------------------------------------------------- library

    fun newSymbol() {
        val (p, sym) = Edits.newSymbol(project, nextName("Symbol", project.symbols.map { it.name }))
        commit(p)
        panel = null
        enterSymbol(sym.id)
        showHint("Draw your symbol, then go back to the scene")
    }

    fun renameSymbol(id: Long, name: String) {
        if (name.isBlank()) return
        commit(project.mapClip(id) { it.copy(name = name.trim()) })
    }

    fun duplicateSymbol(id: Long) {
        val s = project.clip(id) ?: return
        val copy = s.copy(id = Ids.next(), name = "${s.name} copy", layers = s.layers.map { l -> l.copy(id = Ids.next(), exposures = l.exposures.map { it.copy(id = Ids.next()) }) })
        commit(project.copy(symbols = project.symbols + copy))
    }

    fun deleteSymbol(id: Long) {
        val n = project.usages(id)
        if (n > 0) return showHint("Used by $n layer${if (n == 1) "" else "s"}")
        if (path.contains(id)) return showHint("Leave the symbol first")
        commit(project.copy(symbols = project.symbols.filter { it.id != id }))
    }

    // ---------------------------------------------------------------- frame by frame

    private fun drawingLayer(): Layer? {
        val l = selLayer
        if (l == null || l.type != LayerType.Drawing) {
            showHint("Select a drawing layer")
            return null
        }
        return l
    }

    fun addDrawing(copy: Boolean = false) {
        val l = drawingLayer() ?: return
        val (updated, at) = Edits.addDrawing(l, frame, copy)
        editLayer(l.id) { updated }
        seek(at)
    }

    fun extendDrawing() {
        val l = drawingLayer() ?: return
        editLayer(l.id) { Edits.extend(it, frame) }
        val e = clip.layer(l.id)?.exposureAt(frame)
        if (e != null) seek(e.end - 1)
    }

    fun shortenDrawing() {
        val l = drawingLayer() ?: return
        editLayer(l.id) { Edits.shorten(it, frame) }
    }

    fun deleteDrawing() {
        val l = drawingLayer() ?: return
        if (l.exposureAt(frame) == null) return showHint("No drawing on this frame")
        editLayer(l.id) { Edits.deleteDrawing(it, frame) }
    }

    fun moveExposure(layerId: Long, id: Long, start: Int) = editLayer(layerId, history = false) { Edits.moveExposure(it, id, start) }

    // ---------------------------------------------------------------- keyframes

    private fun keyProps(l: Layer): List<Prop> = when (l.type) {
        LayerType.Audio -> emptyList()
        LayerType.Camera -> listOf(Prop.Position, Prop.Rotation, Prop.Scale)
        else -> listOf(Prop.Position, Prop.Rotation, Prop.Scale)
    }

    /** Adds a keyframe for the selected property row, or for the layer's transform on its main row. */
    fun addKeyframe() {
        val l = selLayer ?: return showHint("Select a layer")
        val props = selProp?.let { listOf(it) } ?: keyProps(l)
        if (props.isEmpty()) return
        editLayer(l.id) { layer -> props.fold(layer) { acc, p -> acc.withKey(p, frame, acc.value(p, frame)) } }
        if (selProp == null) selProp = props.first()
    }

    fun deleteKeyframe() {
        val l = selLayer ?: return
        val props = selProp?.let { listOf(it) } ?: l.animatedProps
        if (props.none { l.track(it)?.keyAt(frame) != null }) return showHint("No keyframe on this frame")
        editLayer(l.id) { layer -> props.fold(layer) { acc, p -> acc.withoutKey(p, frame) } }
        if (selProp != null && clip.layer(l.id)?.track(selProp!!) == null) selProp = null
    }

    fun keyProp(layerId: Long, p: Prop) = editLayer(layerId) { it.withKey(p, frame, it.value(p, frame)) }

    fun removeTrack(layerId: Long, p: Prop) {
        editLayer(layerId) { it.withoutTrack(p, frame) }
        if (selLayerId == layerId && selProp == p) selProp = null
    }

    fun moveKey(layerId: Long, p: Prop, from: Int, to: Int) = editLayer(layerId, history = false) { Edits.moveKey(it, p, from, to) }

    /** The keyframe the easing editor works on: at the playhead, else the one the tween leaves from. */
    fun easingKey(): Pair<Prop, Int>? {
        val l = selLayer ?: return null
        val props = selProp?.let { listOf(it) } ?: l.animatedProps
        for (p in props) {
            if (p == Prop.Time) continue
            val t = l.track(p) ?: continue
            val k = t.keyAt(frame) ?: t.keys.lastOrNull { it.frame < frame && t.keys.any { n -> n.frame > frame } }
            if (k != null) return p to k.frame
        }
        return null
    }

    fun setEase(p: Prop, keyFrame: Int, ease: Ease, history: Boolean) {
        val l = selLayer ?: return
        editLayer(l.id, history) { Edits.setEase(it, p, keyFrame, ease) }
    }

    /** Sets a property value at the playhead (no history; callers checkpoint once per gesture). */
    fun setValue(layerId: Long, p: Prop, v: V2) = editLayer(layerId, history = false) { it.set(p, frame, v, autoKey) }

    fun setXf(layerId: Long, xf: Xf) {
        editLayer(layerId, history = false) { l ->
            var u = l
            val old = l.xfAt(frame)
            if (xf.x != old.x || xf.y != old.y) u = u.set(Prop.Position, frame, V2(xf.x, xf.y), autoKey)
            if (xf.rot != old.rot) u = u.set(Prop.Rotation, frame, V2(xf.rot), autoKey)
            if (xf.sx != old.sx || xf.sy != old.sy) u = u.set(Prop.Scale, frame, V2(xf.sx, xf.sy), autoKey)
            u
        }
    }

    fun flip(horizontal: Boolean) {
        val l = selLayer ?: return showHint("Select a layer")
        val s = l.value(Prop.Scale, frame)
        checkpoint()
        setValue(l.id, Prop.Scale, if (horizontal) V2(-s.a, s.b) else V2(s.a, -s.b))
    }

    // ---------------------------------------------------------------- symbol time & markers

    /** "Select from Markers": key the symbol's Time to the marker's first frame. */
    fun pickMarker(m: Marker) {
        val l = selLayer ?: return
        if (l.type != LayerType.Symbol) return
        editLayer(l.id) { it.withKey(Prop.Time, frame, V2(m.from.toFloat())) }
        selProp = Prop.Time
    }

    fun addMarker(at: Int = frame): Marker {
        val m = Marker(from = at, to = at, comment = "Marker ${clip.markers.size + 1}", color = MarkerColors[clip.markers.size % MarkerColors.size])
        editClip { it.copy(markers = (it.markers + m).sortedBy { x -> x.from }) }
        return m
    }

    fun updateMarker(m: Marker) {
        val last = clip.length - 1
        val from = m.from.coerceIn(0, last)
        val fixed = m.copy(from = from, to = m.to.coerceIn(from, last))
        editClip { it.copy(markers = it.markers.map { x -> if (x.id == m.id) fixed else x }.sortedBy { x -> x.from }) }
    }

    fun deleteMarker(id: Long) = editClip { it.copy(markers = it.markers.filter { x -> x.id != id }) }

    // ---------------------------------------------------------------- drawing

    /** The selected drawing layer, creating one when there is none. */
    fun drawTarget(): Layer? {
        val l = selLayer
        if (l != null && l.type == LayerType.Drawing) {
            if (!l.visible) { showHint("${l.name} is hidden"); return null }
            if (l.locked) { showHint("${l.name} is locked"); return null }
            return l
        }
        if (l != null && l.type == LayerType.Symbol) {
            showHint("Double-tap the symbol to draw inside it")
            return null
        }
        addDrawingLayer()
        return selLayer
    }

    fun addStroke(layerId: Long, s: Stroke) = editLayer(layerId) { Edits.addStroke(it, frame, s) }

    fun eraseAt(layerId: Long, local: Pt, r: Float): Boolean {
        val l = clip.layer(layerId) ?: return false
        val e = l.exposureAt(frame) ?: return false
        val result = if (eraseWhole) {
            val keep = e.strokes.filterNot { nearPolyline(it.points, it.closed, local.x, local.y, r + it.width / 2f) }
            if (keep.size == e.strokes.size) null else keep
        } else {
            eraseStrokes(e.strokes, local.x, local.y, r)
        } ?: return false
        editLayer(layerId, history = false) { Edits.setStrokes(it, frame, result) }
        return true
    }

    fun fillAt(clipPt: Pt) {
        val l = drawTarget() ?: return
        val xf = l.xfAt(frame)
        val local = xf.toLocal(clipPt)
        val e = l.exposureAt(frame) ?: return showHint("Nothing to fill on this frame")
        val gap = 28f / (viewScale * xf.avgScale)
        val hit = e.strokes.lastOrNull { it.fillable(gap) && insidePolygon(it.points, local.x, local.y) }
            ?: return showHint("Tap inside a closed shape")
        val filled = hit.with(fill = fillColor, closed = true)
        editLayer(l.id) { Edits.setStrokes(it, frame, e.strokes.map { s -> if (s === hit) filled else s }) }
    }

    fun sampleAt(clipPt: Pt, tolerance: Float) {
        for (l in clip.layers.asReversed()) {
            if (!l.visible || l.type != LayerType.Drawing) continue
            val xf = l.xfAt(frame)
            val local = xf.toLocal(clipPt)
            val e = l.exposureAt(frame) ?: continue
            for (s in e.strokes.asReversed()) {
                if (nearPolyline(s.points, s.closed, local.x, local.y, s.width / 2f + tolerance / xf.avgScale)) {
                    strokeColor = s.color or (0xFF shl 24)
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

    /** Topmost visible, unlocked layer whose content covers [clipPt]. */
    fun layerAt(clipPt: Pt, pad: Float): Layer? {
        for (l in clip.layers.asReversed()) {
            if (!l.visible || l.locked || l.type == LayerType.Camera || l.type == LayerType.Audio) continue
            val xf = l.xfAt(frame)
            val local = xf.toLocal(clipPt)
            val b = project.contentBounds(l, frame) ?: continue
            if (b.contains(local.x, local.y, pad / xf.avgScale)) return l
        }
        return null
    }

    // ---------------------------------------------------------------- lasso

    fun lassoSelect(local: List<Pt>, layerId: Long) {
        val l = clip.layer(layerId) ?: return
        val e = l.exposureAt(frame) ?: return
        val picked = e.strokes.indices.filter { i -> e.strokes[i].points.any { insidePolygon(local, it.x, it.y) } }.toSet()
        lasso = if (picked.isEmpty()) null else LassoSelection(layerId, e.id, picked)
        if (picked.isEmpty()) showHint("Nothing inside the lasso")
    }

    fun lassoStrokes(): List<Stroke> {
        val s = lasso ?: return emptyList()
        val e = clip.layer(s.layerId)?.exposures?.firstOrNull { it.id == s.exposureId } ?: return emptyList()
        return s.indices.mapNotNull { e.strokes.getOrNull(it) }
    }

    fun moveLasso(dx: Float, dy: Float) {
        val s = lasso ?: return
        editLayer(s.layerId, history = false) { l ->
            l.mapExposure(s.exposureId) { e -> e.copy(strokes = e.strokes.mapIndexed { i, st -> if (i in s.indices) st.moved(dx, dy) else st }) }
        }
    }

    fun deleteLasso() {
        val s = lasso ?: return
        editLayer(s.layerId) { l -> l.mapExposure(s.exposureId) { e -> e.copy(strokes = e.strokes.filterIndexed { i, _ -> i !in s.indices }) } }
        lasso = null
    }

    // ---------------------------------------------------------------- pen

    fun penTap(layer: Layer, local: Pt, closeRadius: Float) {
        if (penLayerId != layer.id) {
            finishPen()
            penLayerId = layer.id
        }
        val first = penPoints.firstOrNull()
        val last = penPoints.lastOrNull()
        when {
            first != null && penPoints.size >= 3 && kotlin.math.hypot(local.x - first.x, local.y - first.y) <= closeRadius -> finishPen(closed = true)
            last != null && kotlin.math.hypot(local.x - last.x, local.y - last.y) <= closeRadius -> finishPen()
            else -> penPoints.add(local)
        }
    }

    fun finishPen(closed: Boolean = false) {
        val id = penLayerId
        if (id != null && penPoints.size >= 2) {
            val scale = clip.layer(id)?.xfAt(frame)?.avgScale ?: 1f
            addStroke(id, Stroke(StrokeKind.Path, penPoints.toList(), strokeColor, penWidth / scale, if (closed && penFill) fillColor else null, closed))
        }
        penPoints.clear()
        penLayerId = null
    }

    // ---------------------------------------------------------------- trash

    fun trash() {
        when {
            lasso != null -> deleteLasso()
            selProp != null && selLayer?.track(selProp!!)?.keyAt(frame) != null -> deleteKeyframe()
            selLayer?.type == LayerType.Drawing && selLayer?.exposureAt(frame) != null -> deleteDrawing()
            selLayer != null -> deleteLayer(selLayer!!.id)
        }
    }

    // ---------------------------------------------------------------- document settings

    fun setFps(fps: Int) = commit(project.copy(fps = fps))

    fun setClipLength(n: Int) {
        commit(project.mapClip(clipId) { it.copy(length = maxOf(n, Edits.contentEnd(it), 1)) })
        seek(frame)
    }

    fun setBackground(c: Int) = commit(project.copy(background = c))

    // ---------------------------------------------------------------- audio

    fun importAudio(uri: Uri) {
        if (inSymbol) return showHint("Add audio in the scene")
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { audio.copyIn(uri) } ?: return@launch showHint("Couldn't read that audio file")
            insertLayer(Layer(name = "Audio", type = LayerType.Audio, audio = name))
            audio.load(project)
        }
    }

    // ---------------------------------------------------------------- persistence & export

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(800)
            val p = project
            withContext(Dispatchers.IO) { runCatching { store.save(p) } }
        }
    }

    fun saveNow() {
        if (!isOpen) return
        saveJob?.cancel()
        val p = project
        viewModelScope.launch(Dispatchers.IO) { runCatching { store.save(p) } }
    }

    fun export(gif: Boolean) {
        if (busy != null) return
        stop()
        val p = project
        val ctx = getApplication<Application>()
        busy = if (gif) "Exporting GIF" else "Exporting video"
        busyProgress = 0f
        viewModelScope.launch {
            val safe = p.name.replace(Regex("[^A-Za-z0-9_-]+"), "_").ifBlank { "animation" }
            val out = File(File(ctx.cacheDir, "exports").apply { mkdirs() }, "$safe.${if (gif) "gif" else "mp4"}")
            val ok = withContext(Dispatchers.Default) {
                runCatching {
                    val progress: (Float) -> Unit = { f -> viewModelScope.launch(Dispatchers.Main) { busyProgress = f } }
                    if (gif) Exporter.gif(p, out, progress) else Exporter.mp4(p, out, progress)
                }.onFailure { it.printStackTrace() }.isSuccess
            }
            busy = null
            if (!ok) return@launch showHint("Export failed")
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", out)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = if (gif) "image/gif" else "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(send, "Share ${p.name}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun onCleared() {
        audio.release()
        super.onCleared()
    }
}
