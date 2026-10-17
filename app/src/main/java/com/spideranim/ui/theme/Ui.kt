package com.spideranim.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Colours sampled from ToonSquid's editor. */
object Ui {
    val Bar = Color(0xFF232323)          // top bar, tool bars, panels
    val Workspace = Color(0xFF141414)    // around the canvas
    val Timeline = Color(0xFF1B1B1B)
    val Header = Color(0xFF292929)       // layer name cells
    val SubHeader = Color(0xFF2F2F2F)    // property name cells
    val Track = Color(0xFF2E2E2E)        // a layer's main row
    val SubTrack = Color(0xFF121212)     // property rows
    val Selected = Color(0xFF3B4F57)     // selected layer / property header
    val SelectedTrack = Color(0xFF2B434A)
    val Teal = Color(0xFF6FB8C9)         // playhead, current frame
    val TealButton = Color(0xFF4B7480)   // toggled buttons
    val Pressed = Color(0xFF3A3A3A)      // selected tool
    val Field = Color(0xFF333333)
    val Line = Color(0xFF3A3A3A)
    val Text = Color(0xFFE8E8E8)
    val Dim = Color(0xFFA3A3A3)
    val Faint = Color(0xFF6E6E6E)
    val Red = Color(0xFFFF6B7F)
    val OnionPrev = Color(0xFFFF5A5A)
    val OnionNext = Color(0xFF4FCB7B)

    val menu = TextStyle(fontSize = 17.sp, color = Color(0xFFD6D6D6))
    val body = TextStyle(fontSize = 15.sp, color = Text)
    val small = TextStyle(fontSize = 13.sp, color = Dim)
    val tiny = TextStyle(fontSize = 11.sp, color = Dim)
    val title = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
    val num = TextStyle(fontSize = 14.sp, color = Color(0xFFD0D0D0), fontFeatureSettings = "tnum")
}

@Composable
fun SpiderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Ui.Teal,
            background = Ui.Workspace,
            surface = Ui.Bar,
            onSurface = Ui.Text,
            surfaceContainer = Color(0xFF2C2C2C),
            outline = Ui.Line,
        ),
        content = content,
    )
}
