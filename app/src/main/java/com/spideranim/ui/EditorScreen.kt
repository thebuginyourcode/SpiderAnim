package com.spideranim.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.spideranim.editor.BrushStyle
import com.spideranim.editor.Editor
import com.spideranim.editor.Panel
import com.spideranim.editor.Tool
import com.spideranim.model.LayerType
import com.spideranim.ui.icons.Ic
import com.spideranim.ui.theme.Ui
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

fun Tool.icon() = when (this) {
    Tool.Select -> Ic.Select
    Tool.Lasso -> Ic.Lasso
    Tool.Brush -> Ic.Brush
    Tool.Eraser -> Ic.Eraser
    Tool.Fill -> Ic.Fill
    Tool.Eyedropper -> Ic.Eyedropper
    Tool.Text -> Ic.Text
    Tool.Pen -> Ic.Pen
}

@Composable
fun EditorScreen(ed: Editor) {
    PlaybackLoop(ed)
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(ed::importAudio) }
    val addAudio = { audioPicker.launch(arrayOf("audio/*")) }

    BackHandler {
        when {
            ed.preview -> ed.preview = false
            ed.panel != null -> ed.panel = null
            ed.inSymbol -> ed.exitTo(ed.path.size - 1)
            else -> ed.closeProject()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Ui.Bar).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val compact = maxWidth < 700.dp || maxHeight < 500.dp
        val chrome = !ed.fullscreen
        Column(Modifier.fillMaxSize()) {
            if (chrome) TopBar(ed, compact)
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (chrome) LeftToolbar(ed, compact)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    CanvasView(ed)
                    HintPill(ed, Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
                    if (ed.fullscreen) {
                        BarButton(Ic.Expand, { ed.fullscreen = false }, Modifier.align(Alignment.TopEnd).padding(8.dp), description = "Exit full screen")
                    }
                    // Phones: the panel slides over the canvas.
                    val page = ed.panel
                    if (compact && page != null && chrome) {
                        RightPanel(ed, page, Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(minOf(330.dp, maxWidth * 0.8f)))
                    }
                }
                val page = ed.panel
                if (!compact && page != null && chrome) RightPanel(ed, page, Modifier.width(340.dp).fillMaxHeight())
                if (chrome) RightSidebar(ed, compact)
            }
            if (chrome) Timeline(ed, compact, addAudio)
        }
        if (ed.preview) Preview(ed)
        ed.textPrompt?.let { TextDialog(ed) }
        BusyOverlay(ed)
    }
}

@Composable
private fun PlaybackLoop(ed: Editor) {
    LaunchedEffect(ed.playing) {
        if (!ed.playing) return@LaunchedEffect
        var last = -1L
        var acc = 0.0
        while (isActive && ed.playing) {
            withFrameNanos { now ->
                if (last < 0) last = now
                acc += (now - last) / 1e9
                last = now
                val step = 1.0 / ed.project.fps
                while (acc >= step && ed.playing) {
                    acc -= step
                    ed.advance()
                }
            }
        }
    }
}

// ------------------------------------------------------------------ top bar

@Composable
private fun TopBar(ed: Editor, compact: Boolean) {
    var actions by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    val h = if (compact) 46.dp else 54.dp
    Row(Modifier.fillMaxWidth().height(h).padding(horizontal = if (compact) 4.dp else 18.dp), verticalAlignment = Alignment.CenterVertically) {
        MenuText("Projects", compact) { ed.closeProject() }
        MenuText("Library", compact, active = ed.panel == Panel.Library) { ed.panel = if (ed.panel == Panel.Library) null else Panel.Library }
        Box {
            MenuText("Actions", compact) { actions = true }
            ActionMenu(actions, { actions = false }, listOf(
                "Export Video" to { ed.export(gif = false) },
                "Export GIF" to { ed.export(gif = true) },
                "Clip Length…" to { dialog = "length" },
                "Canvas Size…" to { dialog = "size" },
                "Background…" to { dialog = "background" },
                "Rename Project…" to { dialog = "rename" },
            ))
        }
        Spacer(Modifier.weight(1f))
        BarButton(Ic.Play, { ed.preview = true; ed.play() }, size = 40.dp, iconSize = 18.dp, description = "Preview")
        if (!compact) Spacer(Modifier.width(18.dp))
        ContextControls(ed, compact)
        if (!compact) Spacer(Modifier.width(18.dp))
        BarButton(Ic.Expand, { ed.fullscreen = true }, size = 40.dp, iconSize = 20.dp, tint = Ui.Dim, description = "Full screen")
    }
    when (dialog) {
        "length" -> LengthDialog(ed) { dialog = null }
        "size" -> SizeDialog(ed) { dialog = null }
        "background" -> BackgroundDialog(ed) { dialog = null }
        "rename" -> {
            var name by remember { mutableStateOf(ed.project.name) }
            AppDialog("Rename Project", { dialog = null }, listOf("Cancel" to { dialog = null }, "Rename" to { ed.renameProject(ed.project.id, name); dialog = null })) {
                TextInput(name, { name = it }, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun MenuText(text: String, compact: Boolean, active: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        style = Ui.menu.copy(fontSize = if (compact) 14.sp else 17.sp, color = if (active) Ui.Teal else Color(0xFFD6D6D6)),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 8.dp else 14.dp, vertical = 8.dp),
    )
}

/** What the top bar shows for the current tool: brush sliders, or snapping and flips for selection. */
@Composable
private fun ContextControls(ed: Editor, compact: Boolean) {
    val sliderW = if (compact) 0.dp else 220.dp
    when (ed.tool) {
        Tool.Brush -> if (!compact) {
            KnobSlider(ed.brushSize, 1f..200f, { ed.brushSize = it }, Modifier.width(sliderW))
            Spacer(Modifier.width(18.dp))
            KnobSlider(ed.brushOpacity, 0.05f..1f, { ed.brushOpacity = it }, Modifier.width(sliderW))
        }
        Tool.Eraser -> if (!compact) KnobSlider(ed.eraserSize, 4f..300f, { ed.eraserSize = it }, Modifier.width(sliderW))
        Tool.Select -> {
            val b = if (compact) 40.dp else 56.dp
            Box(Modifier.width(if (compact) 46.dp else 120.dp).height(44.dp).clip(RoundedCornerShape(8.dp)).background(if (ed.snap) Ui.TealButton else Color.Transparent).clickable { ed.snap = !ed.snap }, contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(Ic.Magnet, "Snapping", tint = Color.White, modifier = Modifier.size(24.dp))
            }
            val sel = ed.selLayer
            if (sel != null && sel.type != LayerType.Audio) {
                BarButton(Ic.FlipH, { ed.flip(horizontal = true) }, size = b, description = "Flip horizontally")
                BarButton(Ic.FlipV, { ed.flip(horizontal = false) }, size = b, description = "Flip vertically")
            }
        }
        Tool.Lasso -> if (ed.lasso != null) TextAction("Delete Selection", { ed.deleteLasso() })
        Tool.Pen -> if (ed.penPoints.isNotEmpty()) TextAction("Done", { ed.finishPen() })
        else -> Unit
    }
}

// ------------------------------------------------------------------ left tool bar

@Composable
private fun LeftToolbar(ed: Editor, compact: Boolean) {
    val b = if (compact) 44.dp else 56.dp
    Column(
        Modifier.fillMaxHeight().width(if (compact) 50.dp else 64.dp).verticalScroll(rememberScrollState()).padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp),
    ) {
        Tool.entries.forEach { t ->
            Box {
                BarButton(t.icon(), { ed.selectTool(t) }, selected = ed.tool == t, size = b, iconSize = if (compact) 24.dp else 30.dp, description = t.label)
                if (ed.toolOptions == t) ToolPopover(ed, t)
            }
        }
    }
}

/** Opens to the right of the anchor, kept inside the window. */
private object RightOfAnchor : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = (anchorBounds.right + 12).coerceAtMost(windowSize.width - popupContentSize.width - 8).coerceAtLeast(8)
        val y = anchorBounds.top.coerceAtMost(windowSize.height - popupContentSize.height - 8).coerceAtLeast(8)
        return IntOffset(x, y)
    }
}

@Composable
private fun ToolPopover(ed: Editor, t: Tool) {
    Popup(popupPositionProvider = RightOfAnchor, onDismissRequest = { ed.toolOptions = null }, properties = PopupProperties(focusable = true)) {
        Column(
            Modifier.width(290.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF2A2A2A)).border(1.dp, Ui.Line, RoundedCornerShape(14.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(t.label, style = Ui.body.copy(fontSize = 18.sp, color = Color.White))
            when (t) {
                Tool.Brush -> {
                    Segmented(BrushStyle.entries.map { it.label to it }, ed.brushStyle, { ed.brushStyle = it })
                    Labeled("Size", "${ed.brushSize.roundToInt()}") { MiniSlider(ed.brushSize, 1f..200f, { ed.brushSize = it }, Modifier.fillMaxWidth()) }
                    Labeled("Opacity", "${(ed.brushOpacity * 100).roundToInt()}%") { MiniSlider(ed.brushOpacity, 0.05f..1f, { ed.brushOpacity = it }, Modifier.fillMaxWidth()) }
                    Labeled("Stabilizer", "${(ed.smoothing * 100).roundToInt()}%") { MiniSlider(ed.smoothing, 0f..1f, { ed.smoothing = it }, Modifier.fillMaxWidth()) }
                    Toggle("Stylus pressure", ed.pressure) { ed.pressure = it }
                }
                Tool.Eraser -> {
                    Labeled("Size", "${ed.eraserSize.roundToInt()}") { MiniSlider(ed.eraserSize, 4f..300f, { ed.eraserSize = it }, Modifier.fillMaxWidth()) }
                    Toggle("Erase whole strokes", ed.eraseWhole) { ed.eraseWhole = it }
                }
                Tool.Pen -> {
                    Labeled("Line width", "${ed.penWidth.roundToInt()}") { MiniSlider(ed.penWidth, 1f..60f, { ed.penWidth = it }, Modifier.fillMaxWidth()) }
                    Toggle("Fill closed shapes", ed.penFill) { ed.penFill = it }
                    Text("Tap to place points. Tap the first point to close the shape, or the last one to finish.", style = Ui.small)
                }
                Tool.Text -> Labeled("Size", "${ed.textSize.roundToInt()}") { MiniSlider(ed.textSize, 12f..400f, { ed.textSize = it }, Modifier.fillMaxWidth()) }
                Tool.Fill -> Text("Tap inside a closed shape. Small gaps between line ends are closed for you.", style = Ui.small)
                Tool.Lasso -> Text("Draw around strokes to select them, then drag to move. The trash deletes them.", style = Ui.small)
                Tool.Select -> Text("Drag to move, corners scale, the knob below rotates. Double-tap a symbol to edit it.", style = Ui.small)
                Tool.Eyedropper -> Text("Touch the art to pick up its colour.", style = Ui.small)
            }
        }
    }
}

@Composable
private fun Labeled(label: String, value: String, content: @Composable () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = Ui.small, modifier = Modifier.weight(1f))
            Text(value, style = Ui.num.copy(fontSize = 13.sp))
        }
        content()
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onChange(!on) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Ui.body.copy(fontSize = 15.sp), modifier = Modifier.weight(1f))
        Box(Modifier.width(46.dp).height(28.dp).clip(RoundedCornerShape(14.dp)).background(if (on) Ui.Teal else Ui.Field), contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.padding(3.dp).size(22.dp).clip(CircleShape).background(Color.White))
        }
    }
}

// ------------------------------------------------------------------ right sidebar

@Composable
private fun RightSidebar(ed: Editor, compact: Boolean) {
    val b = if (compact) 44.dp else 56.dp
    fun toggle(p: Panel) {
        ed.panel = if (ed.panel == p || (p == Panel.Properties && ed.panel == Panel.Markers)) null else p
    }
    Column(
        Modifier.fillMaxHeight().width(if (compact) 50.dp else 64.dp).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
    ) {
        Box(Modifier.size(b).clip(RoundedCornerShape(8.dp)).clickable { toggle(Panel.Color) }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(if (compact) 32.dp else 40.dp)) {
                drawCircle(Color(ed.strokeColor))
                drawCircle(Color(0xFF8A8A8A), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()))
            }
        }
        BarButton(Ic.Layers, { toggle(Panel.Layers) }, selected = ed.panel == Panel.Layers, teal = true, size = b, iconSize = 28.dp, description = "Layers")
        BarButton(Ic.Properties, { toggle(Panel.Properties) }, selected = ed.panel == Panel.Properties || ed.panel == Panel.Markers, teal = true, size = b, iconSize = 28.dp, description = "Properties")
        BarButton(Ic.Trash, { ed.trash() }, size = b, iconSize = 28.dp, description = "Delete")
        Spacer(Modifier.weight(1f))
        BarButton(Ic.Undo, { ed.undo() }, enabled = ed.canUndo, size = b, iconSize = 28.dp, description = "Undo")
        BarButton(Ic.Redo, { ed.redo() }, enabled = ed.canRedo, size = b, iconSize = 28.dp, description = "Redo")
    }
}

// ------------------------------------------------------------------ overlays & dialogs

@Composable
private fun HintPill(ed: Editor, modifier: Modifier) {
    val text = ed.hint
    AnimatedVisibility(text != null, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        Text(
            text ?: "",
            style = Ui.body.copy(fontSize = 14.sp),
            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xE62A2A2A)).padding(horizontal = 16.dp, vertical = 9.dp),
        )
    }
}

/** Full-screen playback; tap anywhere to stop. */
@Composable
private fun Preview(ed: Editor) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                ed.stop()
                ed.preview = false
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val p = ed.project
            val s = minOf(size.width / p.width, size.height / p.height)
            val ox = (size.width - p.width * s) / 2
            val oy = (size.height - p.height * s) / 2
            androidx.compose.ui.graphics.drawscope.withTransform({
                translate(ox, oy)
                scale(s, s, androidx.compose.ui.geometry.Offset.Zero)
            }) {
                androidx.compose.ui.graphics.drawscope.clipRect(0f, 0f, p.width.toFloat(), p.height.toFloat()) {
                    if (ed.inSymbol) {
                        drawRect(Color(p.background), size = Size(p.width.toFloat(), p.height.toFloat()))
                        androidx.compose.ui.graphics.drawscope.withTransform({ translate(p.width / 2f, p.height / 2f) }) { drawClip(p, ed.clip, ed.frame) }
                    } else {
                        drawSceneFrame(p, ed.frame)
                    }
                }
            }
        }
    }
}

@Composable
private fun TextDialog(ed: Editor) {
    val prompt = ed.textPrompt ?: return
    var text by remember(prompt) { mutableStateOf(prompt.initial) }
    val close = { ed.textPrompt = null }
    AppDialog(if (prompt.layerId == null) "Add Text" else "Edit Text", close, listOf(
        "Cancel" to close,
        "Done" to {
            if (prompt.layerId == null) ed.addTextLayer(prompt.at, text) else ed.editText(prompt.layerId, text)
            close()
        },
    )) {
        TextInput(text, { text = it }, Modifier.fillMaxWidth().height(120.dp), placeholder = "Type something", singleLine = false)
    }
}

@Composable
private fun BusyOverlay(ed: Editor) {
    val label = ed.busy ?: return
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.width(280.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF2A2A2A)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(label, style = Ui.body.copy(fontSize = 18.sp, color = Color.White))
            Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                drawRoundRect(Ui.Field, cornerRadius = CornerRadius(size.height / 2))
                drawRoundRect(Ui.Teal, size = Size(size.width * ed.busyProgress, size.height), cornerRadius = CornerRadius(size.height / 2))
            }
            Text("${(ed.busyProgress * 100).toInt()}%", style = Ui.small)
        }
    }
}

@Composable
private fun LengthDialog(ed: Editor, onDismiss: () -> Unit) {
    var n by remember { mutableIntStateOf(ed.clip.length) }
    AppDialog("${ed.clip.name} Length", onDismiss, listOf("Cancel" to onDismiss, "Done" to { ed.setClipLength(n); onDismiss() })) {
        Text("${n} frames · ${"%.1f".format(n / ed.project.fps.toFloat())} s", style = Ui.body)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(-12, -1, 1, 12).forEach { d ->
                TextAction(if (d > 0) "+$d" else "$d", { n = (n + d).coerceIn(1, 5000) }, Modifier.clip(RoundedCornerShape(8.dp)).background(Ui.Field))
            }
        }
    }
}

@Composable
private fun SizeDialog(ed: Editor, onDismiss: () -> Unit) {
    AppDialog("Canvas Size", onDismiss, listOf("Close" to onDismiss)) {
        listOf("1920 × 1080" to (1920 to 1080), "1080 × 1080" to (1080 to 1080), "1080 × 1920" to (1080 to 1920), "1280 × 720" to (1280 to 720)).forEach { (label, s) ->
            val on = ed.project.width == s.first && ed.project.height == s.second
            Text(
                label,
                style = Ui.body.copy(color = if (on) Ui.Teal else Ui.Text),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                    ed.commit(ed.project.copy(width = s.first, height = s.second))
                    ed.requestFit()
                    onDismiss()
                }.padding(vertical = 10.dp, horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun BackgroundDialog(ed: Editor, onDismiss: () -> Unit) {
    var c by remember { mutableIntStateOf(ed.project.background) }
    AppDialog("Background", onDismiss, listOf("Cancel" to onDismiss, "Done" to { ed.setBackground(c); onDismiss() })) {
        ColorPicker(c, { c = it })
    }
}
