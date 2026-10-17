package com.spideranim.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spideranim.editor.Editor
import com.spideranim.model.ProjectInfo
import com.spideranim.ui.icons.Ic
import com.spideranim.ui.theme.Ui
import java.text.DateFormat
import java.util.Date

/** The Projects screen: every animation as a card, newest first. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(ed: Editor) {
    var creating by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<ProjectInfo?>(null) }
    var renaming by remember { mutableStateOf<ProjectInfo?>(null) }
    var deleting by remember { mutableStateOf<ProjectInfo?>(null) }

    Column(Modifier.fillMaxSize().background(Ui.Workspace).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().height(64.dp).background(Ui.Bar).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Projects", style = Ui.body.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White), modifier = Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).background(Ui.TealButton).clickable { creating = true }.padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Ic.Plus, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("New Project", style = Ui.body.copy(color = Color.White))
            }
        }
        if (ed.gallery.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("No projects yet", style = Ui.body.copy(fontSize = 20.sp, color = Color.White))
                    Text("Tap New Project to start animating.", style = Ui.small)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(240.dp),
                contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                items(ed.gallery, key = { it.id }) { info ->
                    Column(Modifier.combinedClickable(onClick = { ed.openProject(info.id) }, onLongClick = { menu = info })) {
                        val thumb = remember(info.id, info.modified, ed.thumbVersion) {
                            runCatching { BitmapFactory.decodeFile(ed.store.thumbnail(info.id).path)?.asImageBitmap() }.getOrNull()
                        }
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(info.width.toFloat() / info.height.coerceAtLeast(1)).clip(RoundedCornerShape(10.dp)).background(Color.White),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (thumb != null) Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(info.name, style = Ui.body.copy(color = Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(info.modified)), style = Ui.tiny)
                    }
                }
            }
        }
    }

    if (creating) NewProjectDialog(ed) { creating = false }
    menu?.let { info ->
        ActionSheet(info.name, { menu = null }, listOf(
            "Open" to { ed.openProject(info.id) },
            "Rename" to { renaming = info },
            "Duplicate" to { ed.duplicateProject(info.id) },
            "Delete" to { deleting = info },
        ))
    }
    renaming?.let { info ->
        var name by remember(info.id) { mutableStateOf(info.name) }
        AppDialog("Rename Project", { renaming = null }, listOf("Cancel" to { renaming = null }, "Rename" to { ed.renameProject(info.id, name); renaming = null })) {
            TextInput(name, { name = it }, Modifier.fillMaxWidth())
        }
    }
    deleting?.let { info ->
        AppDialog("Delete \"${info.name}\"?", { deleting = null }, listOf("Cancel" to { deleting = null }, "Delete" to { ed.deleteProject(info.id); deleting = null })) {
            Text("This can't be undone.", style = Ui.small)
        }
    }
}

@Composable
private fun NewProjectDialog(ed: Editor, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var size by remember { mutableStateOf(1920 to 1080) }
    var fps by remember { mutableStateOf(24) }
    AppDialog("New Project", onDismiss, listOf("Cancel" to onDismiss, "Create" to {
        ed.createProject(name.ifBlank { "Animation ${ed.gallery.size + 1}" }, size.first, size.second, fps)
        onDismiss()
    })) {
        TextInput(name, { name = it }, Modifier.fillMaxWidth(), placeholder = "Animation ${ed.gallery.size + 1}")
        Text("Canvas", style = Ui.small)
        Segmented(listOf("16:9" to (1920 to 1080), "1:1" to (1080 to 1080), "9:16" to (1080 to 1920), "4:3" to (1440 to 1080)), size, { size = it })
        Text("Frame rate", style = Ui.small)
        Segmented(listOf("12" to 12, "24" to 24, "30" to 30, "60" to 60), fps, { fps = it })
    }
}
