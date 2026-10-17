package com.spideranim.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.spideranim.editor.ColorTarget
import com.spideranim.editor.EditorState
import com.spideranim.editor.InspectorTab
import com.spideranim.model.MarkerColors
import com.spideranim.model.Drawing
import com.spideranim.model.Ease
import com.spideranim.model.Symbol
import com.spideranim.ui.icons.Ic
import com.spideranim.ui.theme.Ui
import androidx.compose.ui.graphics.drawscope.Stroke as LineStyle


/**
 * The inspector, beside the sidebar. Like ToonSquid's, it switches between the selected layer
 * (transform, easing) and its drawings (markers for the frame picker).
 */
@Composable
fun Inspector(state: EditorState, modifier: Modifier = Modifier) {
    Column(modifier) {
        Segmented(
            InspectorTab.entries.map { it.label to it },
            state.inspectorTab,
            { state.inspectorTab = it },
            Modifier.fillMaxWidth().padding(12.dp),
        )
        when (state.inspectorTab) {
            InspectorTab.Layer -> LayerInspector(state)
            InspectorTab.Drawing -> DrawingInspector(state)
        }
    }
}

// ------------------------------------------------------------------ layer

@Composable
private fun LayerInspector(state: EditorState) {
    val layer = state.selectedLayer
    var renaming by remember { mutableStateOf(false) }
    if (layer == null) {
        EmptyNote("No layer selected", "Tap something on the stage, or a layer in the timeline.", Modifier.fillMaxWidth())
        return
    }
    val sym = state.project.symbol(layer.symbolId)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Section("Layer") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(layer.name, style = Ui.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconBtn(Ic.Rename, { renaming = true }, size = 32.dp, iconSize = 16.dp, description = "Rename layer")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MarkerColors.forEach { c -> ColorWell(c, { state.setLayerColor(layer.id, c) }, size = 28.dp, selected = layer.color == c) }
            }
            if (sym != null) SymbolSwapRow(state, layer.id, sym)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PillAction(Ic.ChevronUp, "Up") { state.moveLayer(layer.id, up = true) }
                PillAction(Ic.ChevronDown, "Down") { state.moveLayer(layer.id, up = false) }
                PillAction(Ic.Duplicate, "Copy") { state.duplicateLayer(layer.id) }
                PillAction(Ic.Trash, "Delete") { state.deleteLayer(layer.id) }
            }
        }

        val xf = layer.xfAt(state.frame)
        val keyed = layer.keyAt(state.frame) != null
        Section("Transform", trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(if (keyed) Ui.Accent else Ui.Bg3))
                Text(if (keyed) "Keyframe ${state.frame + 1}" else "Between keys", style = Ui.label.copy(fontSize = 10.sp))
            }
        }) {
            val start = { state.checkpoint() }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScrubField("X", xf.x, { state.setXform(layer.id, xf.copy(x = it)) }, Modifier.weight(1f), onStart = start)
                ScrubField("Y", xf.y, { state.setXform(layer.id, xf.copy(y = it)) }, Modifier.weight(1f), onStart = start)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScrubField("W", xf.sx * 100f, { state.setXform(layer.id, xf.copy(sx = it / 100f)) }, Modifier.weight(1f), suffix = "%", perPx = 0.5f, onStart = start)
                ScrubField("H", xf.sy * 100f, { state.setXform(layer.id, xf.copy(sy = it / 100f)) }, Modifier.weight(1f), suffix = "%", perPx = 0.5f, onStart = start)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScrubField("Rotation", xf.rot, { state.setXform(layer.id, xf.copy(rot = it)) }, Modifier.weight(1f), suffix = "°", perPx = 0.5f, onStart = start)
                ScrubField("Opacity", xf.alpha * 100f, { state.setXform(layer.id, xf.copy(alpha = (it / 100f).coerceIn(0f, 1f))) }, Modifier.weight(1f), suffix = "%", perPx = 0.4f, onStart = start)
            }
        }

        EasingSection(state)

        Section("Stage") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Background", style = Ui.body.copy(color = Ui.TextDim), modifier = Modifier.weight(1f))
                listOf(0xFFFFFFFF, 0xFFF4EDE1, 0xFFE3E6EC, 0xFF2B2D33, 0xFF101114).forEach {
                    val c = it.toInt()
                    ColorWell(c, { state.setBackground(c) }, size = 24.dp, selected = state.project.background == c)
                }
            }
        }
    }
    if (renaming) RenameDialog("Rename layer", layer.name, onDismiss = { renaming = false }) { state.renameLayer(layer.id, it) }
}

@Composable
private fun EasingSection(state: EditorState) {
    val layer = state.selectedLayer ?: return
    val seg = layer.segmentAt(state.frame)
    val a = seg?.first
    val b = seg?.second
    Section("Easing", trailing = {
        if (a != null && b != null) ToggleChip(if (a.tween) "Tween" else "Hold", a.tween, { state.setTween(!a.tween) })
    }) {
        if (a == null || b == null) {
            Text(
                "Add a second keyframe to tween between them, then shape the timing here.",
                style = Ui.label.copy(fontSize = 12.sp, color = Ui.TextFaint, lineHeight = 17.sp),
            )
            return@Section
        }
        Text("Frames ${a.frame + 1} → ${b.frame + 1}", style = Ui.label.copy(color = Ui.TextFaint))
        CurveEditor(ease = a.ease, onStart = { state.checkpoint() }, onChange = { state.setEase(layer.id, a.frame, it) })
        EasePreview(a.ease)
        Ease.presets.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (name, e) ->
                    PresetTile(name, e, e == a.ease, Modifier.weight(1f)) {
                        state.checkpoint()
                        state.setEase(layer.id, a.frame, e)
                    }
                }
            }
        }
    }
}

@Composable
private fun SymbolSwapRow(state: EditorState, layerId: Long, sym: Symbol) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ui.Bg2).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SymbolThumb(sym.frames.first(), state.project.background, Modifier.size(40.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(sym.name, style = Ui.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val uses = state.project.layers.count { it.symbolId == sym.id }
            Text(
                "${sym.frames.size} drawing${if (sym.frames.size == 1) "" else "s"}" + if (uses > 1) " · on $uses layers" else "",
                style = Ui.label.copy(fontSize = 10.sp, color = Ui.TextFaint),
            )
        }
        Box {
            IconBtn(Ic.Swap, { open = true }, size = 34.dp, iconSize = 18.dp, description = "Symbol options")
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text("Place another copy", style = Ui.body) }, onClick = { open = false; state.placeSymbol(sym.id) })
                state.project.symbols.filter { it.id != sym.id }.forEach { s ->
                    DropdownMenuItem(text = { Text("Show ${s.name} instead", style = Ui.body) }, onClick = { open = false; state.swapSymbol(layerId, s.id) })
                }
            }
        }
    }
}

@Composable
private fun PillAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .width(58.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Ui.Bg2)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, label, tint = Ui.TextDim, modifier = Modifier.size(16.dp))
        Text(label, style = Ui.label.copy(fontSize = 10.sp))
    }
}

// ------------------------------------------------------------------ drawing

@Composable
private fun DrawingInspector(state: EditorState) {
    val layer = state.selectedLayer
    val sym = state.selectedSymbol
    var editing by remember { mutableStateOf<Int?>(null) }
    var renaming by remember { mutableStateOf(false) }
    if (layer == null || sym == null) {
        EmptyNote("No layer selected", "Select a layer to see its drawings.", Modifier.fillMaxWidth())
        return
    }
    val current = layer.drawingAt(state.frame)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(sym.name, style = Ui.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Label drawings to pick them by marker.", style = Ui.label.copy(fontSize = 11.sp, color = Ui.TextFaint))
            }
            IconBtn(Ic.Rename, { renaming = true }, size = 32.dp, iconSize = 16.dp, description = "Rename symbol")
        }
        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
        ) {
            itemsIndexed(sym.frames, key = { _, d -> d.id }) { i, d ->
                val active = i == current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (active) Ui.Bg3 else Ui.Bg2)
                        .border(1.5.dp, if (active) Ui.Accent else Color.Transparent, RoundedCornerShape(14.dp))
                        .clickable { state.pickDrawing(i) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SymbolThumb(d, state.project.background, Modifier.size(52.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(d.markerColor)))
                            Spacer(Modifier.width(6.dp))
                            Text(d.marker ?: "No marker", style = Ui.body.copy(color = if (d.marker != null) Ui.Text else Ui.TextFaint))
                        }
                        Text("Drawing ${i + 1}" + if (active) " · showing" else "", style = Ui.label.copy(fontSize = 10.sp, color = Ui.TextFaint))
                    }
                    IconBtn(Ic.Marker, { editing = i }, size = 34.dp, iconSize = 17.dp, description = "Label drawing")
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallAction("New drawing") { state.addDrawing(duplicate = false) }
            SmallAction("Duplicate") { state.duplicateDrawing(current) }
        }
    }
    editing?.let { i -> if (i < sym.frames.size) DrawingLabelDialog(state, i, sym.frames[i], onDismiss = { editing = null }) }
    if (renaming) RenameDialog("Rename symbol", sym.name, onDismiss = { renaming = false }) { state.renameSymbol(sym.id, it) }
}

@Composable
fun SymbolThumb(d: Drawing, background: Int, modifier: Modifier = Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(10.dp)).background(Color(background))) {
        if (d.strokes.isEmpty()) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.minDimension * 0.12f
            drawLine(Ui.TextFaint, c - Offset(r, 0f), c + Offset(r, 0f), strokeWidth = 1.dp.toPx())
            drawLine(Ui.TextFaint, c - Offset(0f, r), c + Offset(0f, r), strokeWidth = 1.dp.toPx())
        } else {
            drawFitted(d, size.minDimension * 0.12f)
        }
    }
}


// ------------------------------------------------------------------ curves

private class GraphArea(val left: Float, val bottom: Float, val w: Float, val h: Float) {
    fun toPx(x: Float, y: Float) = Offset(left + x * w, bottom - y * h)
    fun fromPx(p: Offset) = Pair((p.x - left) / w, (bottom - p.y) / h)
}

private fun DrawScope.graphArea(): GraphArea {
    val pad = 18.dp.toPx()
    // Leave headroom above and below for overshooting curves like Back/Anticipate.
    return GraphArea(pad, size.height * 0.8f, size.width - pad * 2, size.height * 0.6f)
}

@Composable
private fun CurveEditor(ease: Ease, onStart: () -> Unit, onChange: (Ease) -> Unit) {
    val current by rememberUpdatedState(ease)
    val change by rememberUpdatedState(onChange)
    val start by rememberUpdatedState(onStart)
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.15f)
            .clip(RoundedCornerShape(16.dp))
            .background(Ui.Bg2)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val pad = 18.dp.toPx()
                    val g = GraphArea(pad, size.height * 0.8f, size.width - pad * 2, size.height * 0.6f)
                    val e0 = current
                    val p1 = g.toPx(e0.x1, e0.y1)
                    val p2 = g.toPx(e0.x2, e0.y2)
                    val first = (down.position - p1).getDistance() <= (down.position - p2).getDistance()
                    start()
                    drag(down.id) { c ->
                        val (x, y) = g.fromPx(c.position)
                        val cx = x.coerceIn(0f, 1f)
                        val cy = y.coerceIn(-0.6f, 1.6f)
                        val e = current
                        change(if (first) e.copy(x1 = cx, y1 = cy) else e.copy(x2 = cx, y2 = cy))
                        c.consume()
                    }
                }
            },
    ) {
        val g = graphArea()
        val grid = Ui.Line
        for (i in 0..4) {
            val x = g.left + g.w * i / 4f
            val y = g.bottom - g.h * i / 4f
            drawLine(grid, Offset(x, g.bottom - g.h), Offset(x, g.bottom), strokeWidth = 1f)
            drawLine(grid, Offset(g.left, y), Offset(g.left + g.w, y), strokeWidth = 1f)
        }
        val p0 = g.toPx(0f, 0f)
        val p3 = g.toPx(1f, 1f)
        val p1 = g.toPx(ease.x1, ease.y1)
        val p2 = g.toPx(ease.x2, ease.y2)
        drawLine(Ui.TextFaint, p0, p3, strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        drawLine(Ui.TextDim, p0, p1, strokeWidth = 1.2.dp.toPx())
        drawLine(Ui.TextDim, p3, p2, strokeWidth = 1.2.dp.toPx())
        val path = Path().apply {
            moveTo(p0.x, p0.y)
            cubicTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
        }
        drawPath(path, Ui.Accent, style = LineStyle(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(Ui.Text, radius = 3.5.dp.toPx(), center = p0)
        drawCircle(Ui.Text, radius = 3.5.dp.toPx(), center = p3)
        listOf(p1, p2).forEach {
            drawCircle(Ui.Accent.copy(alpha = 0.18f), radius = 14.dp.toPx(), center = it)
            drawCircle(Color.White, radius = 7.dp.toPx(), center = it)
            drawCircle(Ui.Accent, radius = 7.dp.toPx(), center = it, style = LineStyle(width = 2.dp.toPx()))
        }
    }
}

@Composable
private fun EasePreview(ease: Ease) {
    val t by rememberInfiniteTransition(label = "easePreview").animateFloat(
        initialValue = 0f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(1900, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    Canvas(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(17.dp)).background(Ui.Bg2)) {
        val pad = 17.dp.toPx()
        val y = size.height / 2
        val k = t.coerceAtMost(1f)
        drawLine(Ui.Bg3, Offset(pad, y), Offset(size.width - pad, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        val lin = pad + k * (size.width - pad * 2)
        drawCircle(Ui.TextFaint.copy(alpha = 0.5f), radius = 4.dp.toPx(), center = Offset(lin, y))
        val x = pad + ease.apply(k) * (size.width - pad * 2)
        drawCircle(Ui.Accent, radius = 8.dp.toPx(), center = Offset(x, y))
    }
}

@Composable
private fun PresetTile(name: String, ease: Ease, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val border by animateColorAsState(if (selected) Ui.Accent else Color.Transparent, label = "presetBorder")
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Ui.Bg2)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1.3f)) {
            val h = size.height * 0.62f
            val bottom = size.height * 0.81f
            fun p(x: Float, y: Float) = Offset(x * size.width, bottom - y * h)
            val path = Path().apply {
                val a = p(0f, 0f)
                moveTo(a.x, a.y)
                val c1 = p(ease.x1, ease.y1)
                val c2 = p(ease.x2, ease.y2)
                val e = p(1f, 1f)
                cubicTo(c1.x, c1.y, c2.x, c2.y, e.x, e.y)
            }
            drawRoundRect(Ui.Bg3, topLeft = Offset(0f, bottom - h), size = Size(size.width, h), cornerRadius = CornerRadius(4.dp.toPx()), alpha = 0.35f)
            drawPath(path, if (selected) Ui.Accent else Ui.TextDim, style = LineStyle(width = 1.8.dp.toPx(), cap = StrokeCap.Round))
        }
        Text(name, style = Ui.label.copy(fontSize = 10.sp, color = if (selected) Ui.Accent else Ui.TextDim), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ------------------------------------------------------------------ dialogs

@Composable
fun RenameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(320.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Ui.Bg1)
                .border(1.dp, Ui.Line, RoundedCornerShape(20.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = Ui.title)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = Ui.body.copy(fontSize = 15.sp),
                cursorBrush = SolidColor(Ui.Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ui.Bg2)
                    .border(1.dp, Ui.Line, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                DialogButton("Cancel", primary = false, onClick = onDismiss)
                DialogButton("Save", primary = true) {
                    onConfirm(text)
                    onDismiss()
                }
            }
        }
    }
}

@Composable
fun DialogButton(text: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (primary) Ui.Accent else Ui.Bg2)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Ui.body.copy(color = if (primary) Color.White else Ui.TextDim, fontSize = 13.sp))
    }
}
