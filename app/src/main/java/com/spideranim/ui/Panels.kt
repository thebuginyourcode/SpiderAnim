package com.spideranim.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spideranim.editor.ColorTarget
import com.spideranim.editor.Editor
import com.spideranim.editor.Panel
import com.spideranim.model.Clip
import com.spideranim.model.Ease
import com.spideranim.model.Layer
import com.spideranim.model.LayerType
import com.spideranim.model.Project
import com.spideranim.model.Prop
import com.spideranim.model.V2
import com.spideranim.model.clipBounds
import com.spideranim.model.clipBoundsAll
import com.spideranim.model.strokeBounds
import com.spideranim.ui.icons.Ic
import com.spideranim.ui.theme.Ui
import androidx.compose.ui.graphics.drawscope.Stroke as LineStyle

@Composable
fun RightPanel(ed: Editor, page: Panel, modifier: Modifier = Modifier) {
    Column(modifier.background(Ui.Bar)) {
        when (page) {
            Panel.Color -> ColorPage(ed)
            Panel.Layers -> LayersPage(ed)
            Panel.Properties -> PropertiesPage(ed)
            Panel.Markers -> MarkersPage(ed)
            Panel.Library -> LibraryPage(ed)
            Panel.Easing -> EasingPage(ed)
        }
    }
}

@Composable
private fun PageHeader(title: String, back: String? = null, onBack: () -> Unit = {}, trailing: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 8.dp)) {
        if (back != null) {
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Ic.Back, null, tint = Ui.Dim, modifier = Modifier.size(20.dp))
                Text(back, style = Ui.menu.copy(color = Ui.Dim))
            }
            Spacer(Modifier.height(6.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = Ui.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            trailing()
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = Ui.small.copy(fontSize = 14.sp, lineHeight = 20.sp), modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
}

// ------------------------------------------------------------------ color

@Composable
private fun ColorPage(ed: Editor) {
    PageHeader("Color")
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Segmented(listOf("Stroke" to ColorTarget.Stroke, "Fill" to ColorTarget.Fill), ed.colorTarget, { ed.colorTarget = it })
        if (ed.colorTarget == ColorTarget.Stroke) {
            ColorPicker(ed.strokeColor, { ed.strokeColor = it })
        } else {
            ColorPicker(ed.fillColor, { ed.fillColor = it })
        }
        Spacer(Modifier.height(12.dp))
    }
}

// ------------------------------------------------------------------ layers

@Composable
private fun LayersPage(ed: Editor) {
    var menu by remember { mutableStateOf<Layer?>(null) }
    PageHeader("Layers") {
        BarButton(Ic.Plus, { ed.addDrawingLayer() }, size = 40.dp, description = "New drawing layer")
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(ed.clip.layers.asReversed(), key = { it.id }) { l ->
            val sel = ed.selLayerId == l.id
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (sel) Ui.Selected else Color.Transparent)
                    .clickable { ed.selectLayer(l.id) }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LayerThumb(ed, l, Modifier.width(64.dp).height(40.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(l.name, style = Ui.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(l.type.name, style = Ui.tiny)
                }
                BarButton(if (l.visible) Ic.Eye else Ic.EyeOff, { ed.toggleVisible(l.id) }, size = 36.dp, iconSize = 20.dp, tint = if (l.visible) Color.White else Ui.Faint)
                BarButton(Ic.More, { menu = l }, size = 36.dp, iconSize = 20.dp, description = "Layer options")
            }
        }
    }
    menu?.let { l ->
        ActionSheet(l.name, { menu = null }, buildList {
            if (l.type == LayerType.Symbol) add("Edit Symbol" to { ed.enterSymbol(l.symbolId) })
            if (l.type == LayerType.Drawing || l.type == LayerType.Text) add("Convert to Symbol" to { ed.convertToSymbol(l.id) })
            add((if (l.locked) "Unlock" else "Lock") to { ed.toggleLock(l.id) })
            add("Move Up" to { ed.moveLayer(l.id, up = true) })
            add("Move Down" to { ed.moveLayer(l.id, up = false) })
            if (l.type != LayerType.Camera) add("Duplicate Layer" to { ed.duplicateLayer(l.id) })
            add("Delete Layer" to { ed.deleteLayer(l.id) })
        })
    }
}

@Composable
fun LayerThumb(ed: Editor, l: Layer, modifier: Modifier) {
    val p = ed.project
    val f = ed.frame
    Canvas(modifier.clip(RoundedCornerShape(6.dp)).background(Color(p.background))) {
        when (l.type) {
            LayerType.Drawing -> l.exposureAt(f)?.let { e -> drawFitted(strokeBounds(e.strokes), 4f) { e.strokes.forEach { drawMark(it) } } }
            LayerType.Symbol -> p.clip(l.symbolId)?.let { s ->
                val sf = p.symbolFrame(l, f)
                drawFitted(p.clipBounds(s, sf), 4f) { drawClip(p, s, sf) }
            }
            else -> Unit
        }
    }
}

/** A symbol at [frame], fitted to the box, on the stage colour. */
@Composable
fun ClipThumb(p: Project, clip: Clip, frame: Int, modifier: Modifier) {
    val bounds = remember(p, clip) { p.clipBoundsAll(clip) }
    Canvas(modifier.background(Color(p.background))) {
        drawFitted(bounds, size.minDimension * 0.1f) { drawClip(p, clip, frame) }
    }
}

// ------------------------------------------------------------------ properties

@Composable
private fun PropertiesPage(ed: Editor) {
    val l = ed.selLayer
    PageHeader("Properties")
    if (l == null) {
        Note("Select a layer to see its properties.")
        return
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(l.name, style = Ui.body.copy(color = Ui.Dim), modifier = Modifier.padding(start = 4.dp))
        if (l.type != LayerType.Audio) {
            val pos = l.value(Prop.Position, ed.frame)
            PropRow(ed, l, Prop.Position) {
                ScrubField("X", pos.a, { ed.setValue(l.id, Prop.Position, V2(it, pos.b)) }, Modifier.weight(1f), onStart = { ed.checkpoint() })
                ScrubField("Y", pos.b, { ed.setValue(l.id, Prop.Position, V2(pos.a, it)) }, Modifier.weight(1f), onStart = { ed.checkpoint() })
            }
            val rot = l.value(Prop.Rotation, ed.frame)
            PropRow(ed, l, Prop.Rotation) {
                ScrubField("°", rot.a, { ed.setValue(l.id, Prop.Rotation, V2(it)) }, Modifier.weight(1f), perPx = 0.5f, onStart = { ed.checkpoint() })
            }
            val sc = l.value(Prop.Scale, ed.frame)
            PropRow(ed, l, Prop.Scale) {
                ScrubField("W", sc.a * 100, { ed.setValue(l.id, Prop.Scale, V2(it / 100f, sc.b)) }, Modifier.weight(1f), suffix = "%", perPx = 0.5f, onStart = { ed.checkpoint() })
                ScrubField("H", sc.b * 100, { ed.setValue(l.id, Prop.Scale, V2(sc.a, it / 100f)) }, Modifier.weight(1f), suffix = "%", perPx = 0.5f, onStart = { ed.checkpoint() })
            }
            if (l.type != LayerType.Camera) {
                val op = l.value(Prop.Opacity, ed.frame)
                PropRow(ed, l, Prop.Opacity) {
                    ScrubField("", op.a * 100, { ed.setValue(l.id, Prop.Opacity, V2((it / 100f).coerceIn(0f, 1f))) }, Modifier.weight(1f), suffix = "%", perPx = 0.4f, onStart = { ed.checkpoint() })
                }
            }
        }
        if (l.type == LayerType.Symbol) {
            val sym = ed.project.clip(l.symbolId)
            val sf = ed.project.symbolFrame(l, ed.frame)
            val marker = sym?.markers?.firstOrNull { sf in it.from..it.to }
            // ToonSquid: Time › opens the Markers page.
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ui.Field).clickable { ed.panel = Panel.Markers }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Time", style = Ui.body, modifier = Modifier.weight(1f))
                Text(marker?.comment ?: "Frame ${sf + 1}", style = Ui.body.copy(color = Ui.Dim))
                Spacer(Modifier.width(6.dp))
                Icon(Ic.Chevron, null, tint = Ui.Dim, modifier = Modifier.size(18.dp))
            }
            TextAction("Edit Symbol", { ed.enterSymbol(l.symbolId) })
        }
        if (l.type == LayerType.Text) {
            TextAction("Edit Text", { ed.textPrompt = com.spideranim.editor.TextPrompt(com.spideranim.model.Pt(l.x, l.y), l.id, l.text) })
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** A property row: label, its fields, and a diamond that keys it at the playhead. */
@Composable
private fun PropRow(ed: Editor, l: Layer, p: Prop, fields: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val keyed = l.track(p)?.keyAt(ed.frame) != null
    val animated = l.track(p) != null
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.label, style = Ui.body.copy(fontSize = 14.sp, color = Ui.Dim), modifier = Modifier.weight(1f).padding(start = 4.dp))
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).clickable { if (keyed) ed.selectLayer(l.id, p) else ed.keyProp(l.id, p) },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(14.dp)) {
                    val c = Offset(size.width / 2, size.height / 2)
                    val d = Path().apply { moveTo(c.x, 0f); lineTo(size.width, c.y); lineTo(c.x, size.height); lineTo(0f, c.y); close() }
                    if (keyed) drawPath(d, Color.White) else drawPath(d, if (animated) Ui.Teal else Ui.Faint, style = LineStyle(width = 1.5.dp.toPx()))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = fields)
    }
}

// ------------------------------------------------------------------ markers (Select from Markers)

@Composable
private fun MarkersPage(ed: Editor) {
    val l = ed.selLayer
    val sym = l?.takeIf { it.type == LayerType.Symbol }?.let { ed.project.clip(it.symbolId) }
    PageHeader("Markers", back = "Properties", onBack = { ed.panel = Panel.Properties })
    if (l == null || sym == null) {
        Note("Select a symbol layer to pick its frames by marker.")
        return
    }
    if (sym.markers.isEmpty()) {
        Note("${sym.name} has no markers yet. Open it, then long-press a frame number in its timeline to add a marker for each drawing (A, E, O…).")
        TextAction("Edit ${sym.name}", { ed.enterSymbol(sym.id) }, Modifier.padding(horizontal = 8.dp))
        return
    }
    var query by remember { mutableStateOf("") }
    TextInput(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), placeholder = "Search")
    val current = ed.project.symbolFrame(l, ed.frame)
    val shown = sym.markers.filter { query.isBlank() || it.comment.contains(query.trim(), ignoreCase = true) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(shown, key = { it.id }) { m ->
            val active = current in m.from..m.to
            Column(Modifier.clickable { ed.pickMarker(m) }) {
                ClipThumb(
                    ed.project, sym, m.from,
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .border(if (active) 3.dp else 0.dp, if (active) Ui.Teal else Color.Transparent, RoundedCornerShape(6.dp)),
                )
                Spacer(Modifier.height(6.dp))
                Text(m.comment, style = Ui.body.copy(fontSize = 17.sp, color = Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ------------------------------------------------------------------ library

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryPage(ed: Editor) {
    var menu by remember { mutableStateOf<Clip?>(null) }
    var renaming by remember { mutableStateOf<Clip?>(null) }
    PageHeader("Library") {
        BarButton(Ic.Plus, { ed.newSymbol() }, size = 40.dp, description = "New symbol")
    }
    if (ed.project.symbols.isEmpty()) {
        Note("Symbols are reusable animations with their own timeline. Tap + to make one, or long-press a layer and choose Convert to Symbol.")
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(ed.project.symbols, key = { it.id }) { s ->
            Column(Modifier.combinedClickable(onClick = { ed.placeSymbol(s.id) }, onLongClick = { menu = s })) {
                ClipThumb(ed.project, s, 0, Modifier.fillMaxWidth().aspectRatio(1.4f).clip(RoundedCornerShape(8.dp)))
                Spacer(Modifier.height(6.dp))
                Text(s.name, style = Ui.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${s.length} frames · ${s.markers.size} markers", style = Ui.tiny)
            }
        }
    }
    menu?.let { s ->
        ActionSheet(s.name, { menu = null }, listOf(
            "Place on Canvas" to { ed.placeSymbol(s.id) },
            "Edit" to { ed.enterSymbol(s.id) },
            "Rename" to { renaming = s },
            "Duplicate" to { ed.duplicateSymbol(s.id) },
            "Delete" to { ed.deleteSymbol(s.id) },
        ))
    }
    renaming?.let { s ->
        var name by remember(s.id) { mutableStateOf(s.name) }
        AppDialog("Rename Symbol", { renaming = null }, listOf("Cancel" to { renaming = null }, "Rename" to { ed.renameSymbol(s.id, name); renaming = null })) {
            TextInput(name, { name = it }, Modifier.fillMaxWidth())
        }
    }
}

// ------------------------------------------------------------------ easing

@Composable
private fun EasingPage(ed: Editor) {
    PageHeader("Easing")
    val l = ed.selLayer
    val target = ed.easingKey()
    if (l == null || target == null) {
        Note("Select a keyframe that starts a tween (a property row in the timeline), then shape its timing here.")
        return
    }
    val (prop, keyFrame) = target
    val ease = l.track(prop)?.keyAt(keyFrame)?.ease ?: Ease.Linear
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("${prop.label} · keyframe ${keyFrame + 1}", style = Ui.body.copy(color = Ui.Dim))
        CurveEditor(ease, onStart = { ed.checkpoint() }, onChange = { ed.setEase(prop, keyFrame, it, history = false) })
        Ease.presets.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (name, e) ->
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (e == ease) Ui.TealButton else Ui.Field)
                            .clickable { ed.setEase(prop, keyFrame, e, history = true) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(name, style = Ui.body.copy(fontSize = 14.sp)) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun CurveEditor(ease: Ease, onStart: () -> Unit, onChange: (Ease) -> Unit) {
    val current by rememberUpdatedState(ease)
    val change by rememberUpdatedState(onChange)
    val start by rememberUpdatedState(onStart)
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(Ui.Workspace)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val pad = 24.dp.toPx()
                    val gw = size.width - pad * 2
                    val gh = size.height * 0.6f
                    val bottom = size.height * 0.8f
                    fun px(x: Float, y: Float) = Offset(pad + x * gw, bottom - y * gh)
                    val e0 = current
                    val first = (down.position - px(e0.x1, e0.y1)).getDistance() <= (down.position - px(e0.x2, e0.y2)).getDistance()
                    start()
                    drag(down.id) { c ->
                        val x = ((c.position.x - pad) / gw).coerceIn(0f, 1f)
                        val y = ((bottom - c.position.y) / gh).coerceIn(-0.6f, 1.6f)
                        val e = current
                        change(if (first) e.copy(x1 = x, y1 = y) else e.copy(x2 = x, y2 = y))
                        c.consume()
                    }
                }
            },
    ) {
        val pad = 24.dp.toPx()
        val gw = size.width - pad * 2
        val gh = size.height * 0.6f
        val bottom = size.height * 0.8f
        fun px(x: Float, y: Float) = Offset(pad + x * gw, bottom - y * gh)
        for (i in 0..4) {
            drawLine(Color(0xFF2A2A2A), px(i / 4f, 0f), px(i / 4f, 1f), strokeWidth = 1f)
            drawLine(Color(0xFF2A2A2A), px(0f, i / 4f), px(1f, i / 4f), strokeWidth = 1f)
        }
        val p0 = px(0f, 0f)
        val p3 = px(1f, 1f)
        val p1 = px(ease.x1, ease.y1)
        val p2 = px(ease.x2, ease.y2)
        drawLine(Ui.Faint, p0, p3, strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        drawLine(Ui.Dim, p0, p1, strokeWidth = 1.5.dp.toPx())
        drawLine(Ui.Dim, p3, p2, strokeWidth = 1.5.dp.toPx())
        drawPath(Path().apply { moveTo(p0.x, p0.y); cubicTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y) }, Color.White, style = LineStyle(width = 3.dp.toPx(), cap = StrokeCap.Round))
        listOf(p1, p2).forEach {
            drawCircle(Ui.Teal, radius = 9.dp.toPx(), center = it)
            drawCircle(Color.White, radius = 9.dp.toPx(), center = it, style = LineStyle(width = 2.dp.toPx()))
        }
    }
}
