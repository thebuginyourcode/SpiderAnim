package com.spideranim.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spideranim.editor.EditorState
import com.spideranim.ui.icons.Ic
import com.spideranim.ui.theme.Ui
import kotlinx.coroutines.isActive
import androidx.compose.ui.graphics.drawscope.Stroke as LineStyle

@Composable
fun EditorScreen(state: EditorState) {
    PlaybackEffect(state)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Ui.Bg0)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val wide = maxWidth >= 720.dp && maxHeight >= 520.dp
        if (wide) TabletLayout(state) else PhoneLayout(state)
        ExportOverlay(state)
    }
}

@Composable
private fun PlaybackEffect(state: EditorState) {
    LaunchedEffect(state.playing) {
        if (!state.playing) return@LaunchedEffect
        var last = -1L
        var acc = 0.0
        while (isActive && state.playing) {
            withFrameNanos { now ->
                if (last < 0) last = now
                acc += (now - last) / 1e9
                last = now
                val step = 1.0 / state.project.fps
                while (acc >= step && state.playing) {
                    acc -= step
                    state.advanceFrame()
                }
            }
        }
    }
}

@Composable
private fun TabletLayout(state: EditorState) {
    Column(Modifier.fillMaxSize()) {
        TopBar(state, compact = false)
        Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp)) {
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp))) {
                Stage(state)
                ZoomBadge(state, Modifier.align(Alignment.TopStart).padding(10.dp))
                FramePickerBar(state, Modifier.align(Alignment.BottomCenter).padding(10.dp))
                HintPill(state, Modifier.align(Alignment.TopCenter).padding(top = 14.dp))
            }
            AnimatedVisibility(state.inspectorOpen) {
                Row {
                    Spacer(Modifier.width(10.dp))
                    Inspector(state, Modifier.width(300.dp).fillMaxHeight().floatingPanel())
                }
            }
            Spacer(Modifier.width(10.dp))
            Sidebar(state, compact = false, onInspector = { state.inspectorOpen = !state.inspectorOpen }, modifier = Modifier.fillMaxHeight())
        }
        Timeline(state, compact = false, modifier = Modifier.fillMaxWidth().padding(10.dp))
    }
}

@Composable
private fun PhoneLayout(state: EditorState) {
    var sheet by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(state, compact = true)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Stage(state)
                ZoomBadge(state, Modifier.align(Alignment.TopStart).padding(8.dp))
                Sidebar(state, compact = true, onInspector = { sheet = true }, modifier = Modifier.align(Alignment.CenterEnd).padding(8.dp))
                FramePickerBar(state, Modifier.align(Alignment.BottomStart).padding(8.dp).padding(end = 64.dp))
                HintPill(state, Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
            }
            Timeline(state, compact = true, modifier = Modifier.fillMaxWidth().padding(8.dp))
        }
        InspectorSheet(state, sheet) { sheet = false }
    }
}

@Composable
private fun InspectorSheet(state: EditorState, visible: Boolean, onDismiss: () -> Unit) {
    BackHandler(enabled = visible, onBack = onDismiss)
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        val h = maxHeight * 0.68f
        AnimatedVisibility(
            visible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(h)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Ui.Bg1)
                    .border(1.dp, Ui.Line, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Ui.Bg3))
                }
                Inspector(state, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun TopBar(state: EditorState, compact: Boolean) {
    var renaming by remember { mutableStateOf(false) }
    var newConfirm by remember { mutableStateOf(false) }
    val p = state.project
    Row(
        Modifier.fillMaxWidth().height(54.dp).padding(start = 14.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LogoMark()
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { renaming = true }) {
            Text(p.name, style = Ui.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${p.width}×${p.height} · ${p.fps} fps", style = Ui.label.copy(fontSize = 10.sp, color = Ui.TextFaint), maxLines = 1)
        }
        IconBtn(Ic.Undo, { state.undo() }, enabled = state.canUndo, size = 38.dp, description = "Undo")
        IconBtn(Ic.Redo, { state.redo() }, enabled = state.canRedo, size = 38.dp, description = "Redo")
        if (!compact) {
            VHairline(Modifier.padding(horizontal = 6.dp))
            ExportButton(state)
        }
        Box {
            var open by remember { mutableStateOf(false) }
            IconBtn(Ic.More, { open = true }, size = 38.dp, description = "Menu")
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                val item: @Composable (String, () -> Unit) -> Unit = { label, action ->
                    DropdownMenuItem(text = { Text(label, style = Ui.body) }, onClick = { open = false; action() })
                }
                if (compact) item("Export GIF") { state.exportGif() }
                item("Rename project") { renaming = true }
                item("Stage 16:9 · 1920×1080") { state.setStageSize(1920, 1080) }
                item("Stage 1:1 · 1080×1080") { state.setStageSize(1080, 1080) }
                item("Stage 9:16 · 1080×1920") { state.setStageSize(1080, 1920) }
                item("New project") { newConfirm = true }
            }
        }
    }
    if (renaming) RenameDialog("Rename project", p.name, onDismiss = { renaming = false }) { state.renameProject(it) }
    if (newConfirm) ConfirmDialog(
        "Start a new project?",
        "The current scene is replaced. You can still undo.",
        onDismiss = { newConfirm = false },
    ) { state.newProject() }
}

@Composable
private fun ExportButton(state: EditorState) {
    Row(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Ui.Bg2)
            .clickable { state.exportGif() }
            .padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        androidx.compose.material3.Icon(Ic.Share, null, tint = Ui.TextDim, modifier = Modifier.size(17.dp))
        Text("Export GIF", style = Ui.body.copy(fontSize = 12.sp, color = Ui.TextDim))
    }
}

@Composable
private fun LogoMark() {
    Canvas(Modifier.size(28.dp)) {
        drawRoundRect(
            Brush.linearGradient(listOf(Color(0xFF5AA8FF), Ui.Accent, Color(0xFF1E6FE0)), Offset.Zero, Offset(size.width, size.height)),
            cornerRadius = CornerRadius(size.width * 0.3f),
        )
        val w = size.width
        // A motion curve ending in a keyframe: the app in one glyph.
        val curve = Path().apply {
            moveTo(w * 0.22f, w * 0.74f)
            cubicTo(w * 0.5f, w * 0.74f, w * 0.42f, w * 0.3f, w * 0.68f, w * 0.3f)
        }
        drawPath(curve, Color.White, style = LineStyle(width = w * 0.09f, cap = StrokeCap.Round))
        val k = Offset(w * 0.72f, w * 0.3f)
        val r = w * 0.11f
        val diamond = Path().apply {
            moveTo(k.x, k.y - r); lineTo(k.x + r, k.y); lineTo(k.x, k.y + r); lineTo(k.x - r, k.y); close()
        }
        drawPath(diamond, Color.White)
    }
}

@Composable
private fun HintPill(state: EditorState, modifier: Modifier = Modifier) {
    val text = state.hint
    AnimatedVisibility(
        text != null,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Ui.Bg3)
                .border(1.dp, Ui.Line, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 9.dp),
        ) {
            Text(text ?: "", style = Ui.body.copy(fontSize = 12.sp))
        }
    }
}

@Composable
private fun ExportOverlay(state: EditorState) {
    val progress = state.exportProgress ?: return
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(260.dp).floatingPanel().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Exporting GIF", style = Ui.title)
            Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                drawRoundRect(Ui.Bg3, cornerRadius = CornerRadius(size.height / 2))
                drawRoundRect(Ui.Accent, size = Size(size.width * progress, size.height), cornerRadius = CornerRadius(size.height / 2))
            }
            Text("${(progress * 100).toInt()}% · ${state.project.length} frames", style = Ui.label.copy(color = Ui.TextFaint))
        }
    }
}

@Composable
private fun ConfirmDialog(title: String, body: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(320.dp).floatingPanel().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = Ui.title)
            Text(body, style = Ui.body.copy(color = Ui.TextDim))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                DialogButton("Cancel", primary = false, onClick = onDismiss)
                DialogButton("New project", primary = true) {
                    onConfirm()
                    onDismiss()
                }
            }
        }
    }
}
