package com.spideranim.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Ui {
    val Bg0 = Color(0xFF0C0D10)
    val Bg1 = Color(0xFF15161A)
    val Bg2 = Color(0xFF1D1F24)
    val Bg3 = Color(0xFF272A30)
    val Line = Color(0xFF2A2D34)
    val Text = Color(0xFFECEDEF)
    val TextDim = Color(0xFF9EA2AC)
    val TextFaint = Color(0xFF61656E)
    val Accent = Color(0xFFFF7A45)
    val AccentSoft = Color(0x2EFF7A45)
    val OnionPrev = Color(0xFFFF4D6A)
    val OnionNext = Color(0xFF2FC79A)

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
