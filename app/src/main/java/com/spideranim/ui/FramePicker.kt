package com.spideranim.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.spideranim.editor.EditorState
import com.spideranim.model.Drawing
import com.spideranim.model.MarkerColors
import com.spideranim.ui.icons.Ic
import com.spideranim.ui.theme.Ui

/** What a drawing is called in the picker: its marker, or its number. */
fun Drawing.label(index: Int) = marker ?: "${index + 1}"

/**
 * The quick frame picker. One chip per drawing of the selected layer's symbol, labelled by its
 * marker. Tap a chip to show that drawing from the playhead on (ToonSquid's "Select from
 * Markers"). Long-press to label it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FramePickerBar(state: EditorState, modifier: Modifier = Modifier) {
    val layer = state.selectedLayer ?: return
    val sym = state.project.symbol(layer.symbolId) ?: return
    val current = layer.drawingAt(state.frame).coerceIn(0, sym.frames.size - 1)
    val picked = layer.pickAt(state.frame) != null
    var editing by remember { mutableStateOf<Int?>(null) }
    val list = rememberLazyListState()
    LaunchedEffect(current) { list.animateScrollToItem(current) }

    Row(
        modifier.widthIn(max = 640.dp).floatingPanel().padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(66.dp)) {
            Text("FRAMES", style = Ui.caps.copy(fontSize = 9.sp))
            Text(sym.name, style = Ui.label.copy(color = Ui.TextDim), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (picked) "Key ${state.frame + 1}" else "Held",
                style = Ui.label.copy(fontSize = 9.sp, color = if (picked) Ui.Accent else Ui.TextFaint),
            )
        }
        LazyRow(
            state = list,
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(sym.frames, key = { _, d -> d.id }) { i, d ->
                val active = i == current
                val bg by animateColorAsState(if (active) Ui.Bg3 else Ui.Bg2, label = "chipBg")
                val ring by animateColorAsState(if (active) Ui.Accent else Color.Transparent, label = "chipRing")
                Row(
                    Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bg)
                        .border(1.5.dp, ring, RoundedCornerShape(12.dp))
                        .combinedClickable(onClick = { state.pickDrawing(i) }, onLongClick = { editing = i })
                        .padding(start = 4.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SymbolThumb(d, state.project.background, Modifier.size(40.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Color(d.markerColor)))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        d.label(i),
                        style = Ui.body.copy(fontSize = 13.sp, color = if (active) Ui.Text else Ui.TextDim),
                        maxLines = 1,
                    )
                }
            }
        }
        IconBtn(Ic.Plus, { state.addDrawing(duplicate = false) }, size = 40.dp, description = "New drawing")
    }

    editing?.let { i ->
        if (i < sym.frames.size) {
            DrawingLabelDialog(state, i, sym.frames[i], onDismiss = { editing = null })
        }
    }
}

/** Name a drawing's marker, pick its colour, or duplicate/delete the drawing. */
@Composable
fun DrawingLabelDialog(state: EditorState, index: Int, d: Drawing, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(d.marker ?: "") }
    var color by remember { mutableIntStateOf(d.markerColor) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(320.dp).floatingPanel().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SymbolThumb(d, state.project.background, Modifier.size(56.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Drawing ${index + 1}", style = Ui.title)
                    Text("The marker name shows in the frame picker and on the timeline.", style = Ui.label.copy(color = Ui.TextFaint, lineHeight = 15.sp))
                }
            }
            BasicTextField(
                value = name,
                onValueChange = { name = it.take(24) },
                singleLine = true,
                textStyle = Ui.body.copy(fontSize = 15.sp),
                cursorBrush = SolidColor(Ui.Accent),
                decorationBox = { inner ->
                    Box {
                        if (name.isEmpty()) Text("Marker, e.g. A, E, Blink", style = Ui.body.copy(fontSize = 15.sp, color = Ui.TextFaint))
                        inner()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ui.Bg2)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MarkerColors.forEach { c ->
                    ColorWell(c, { color = c }, size = 32.dp, selected = c == color)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallAction("Duplicate") {
                    state.duplicateDrawing(index)
                    onDismiss()
                }
                SmallAction("Delete", danger = true) {
                    state.deleteDrawing(index)
                    onDismiss()
                }
                Spacer(Modifier.weight(1f))
                DialogButton("Save", primary = true) {
                    state.labelDrawing(index, name, color)
                    onDismiss()
                }
            }
        }
    }
}

@Composable
fun SmallAction(text: String, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Ui.Bg2)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Ui.body.copy(fontSize = 13.sp, color = if (danger) Ui.OnionPrev else Ui.TextDim))
    }
}
