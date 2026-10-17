package com.spideranim.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Ui {
    // Neutral greys; the artwork and the track colours carry the colour, not the chrome.
    val Bg0 = Color(0xFF1C1D20) // workspace around the stage
    val Bg1 = Color(0xFF26272B) // panels
    val Bg2 = Color(0xFF303135) // controls
    val Bg3 = Color(0xFF3B3C41) // pressed / selected controls
    val Line = Color(0xFF38393E)
    val Text = Color(0xFFF4F4F6)
    val TextDim = Color(0xFFB3B5BC)
    val TextFaint = Color(0xFF7A7C84)
    val Accent = Color(0xFF2F8CFF)
    val AccentSoft = Color(0x332F8CFF)
    val OnionPrev = Color(0xFFFF5C7A)
    val OnionNext = Color(0xFF34C77B)

    val title = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Text, letterSpacing = 0.1.sp)
    val body = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, color = Text)
    val label = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextDim, letterSpacing = 0.3.sp)
    val caps = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = TextFaint, letterSpacing = 1.2.sp)
    val num = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Text, fontFeatureSettings = "tnum")
}

@Composable
fun SpiderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Ui.Accent,
            onPrimary = Color.White,
            background = Ui.Bg0,
            onBackground = Ui.Text,
            surface = Ui.Bg1,
            onSurface = Ui.Text,
            surfaceVariant = Ui.Bg2,
            onSurfaceVariant = Ui.TextDim,
            surfaceContainer = Ui.Bg2,
            outline = Ui.Line,
        ),
        content = content,
    )
}
