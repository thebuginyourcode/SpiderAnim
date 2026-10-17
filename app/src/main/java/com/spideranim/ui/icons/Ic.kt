package com.spideranim.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private class Part(val d: String, val filled: Boolean)

private fun s(d: String) = Part(d, false)
private fun f(d: String) = Part(d, true)

private fun circle(cx: Float, cy: Float, r: Float) =
    "M${cx} ${cy - r} A$r $r 0 1 1 ${cx} ${cy + r} A$r $r 0 1 1 ${cx} ${cy - r} Z"

private fun rounded(x: Float, y: Float, w: Float, h: Float, r: Float) =
    "M${x + r} $y H${x + w - r} A$r $r 0 0 1 ${x + w} ${y + r} V${y + h - r} A$r $r 0 0 1 ${x + w - r} ${y + h} " +
        "H${x + r} A$r $r 0 0 1 $x ${y + h - r} V${y + r} A$r $r 0 0 1 ${x + r} $y Z"

/** Line icons on a 24-unit grid, 1.6 stroke, round caps. Tinted by Icon(). */
private fun icon(name: String, vararg parts: Part): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply {
            for (p in parts) {
                if (p.filled) {
                    addPath(pathData = addPathNodes(p.d), fill = SolidColor(Color.White))
                } else {
                    addPath(
                        pathData = addPathNodes(p.d),
                        stroke = SolidColor(Color.White),
                        strokeLineWidth = 1.6f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
        }
        .build()

object Ic {
    // Tools
    val Select by lazy { icon("select", s("M6 3.8 L6 18.6 L9.7 15.1 L12.2 20.4 L14.6 19.3 L12.1 14 L17.2 13.7 Z")) }
    val Transform by lazy {
        icon(
            "transform", s("M7 7 H17 V17 H7 Z"),
            f("M5.2 5.2 H8.8 V8.8 H5.2 Z"), f("M15.2 5.2 H18.8 V8.8 H15.2 Z"),
            f("M5.2 15.2 H8.8 V18.8 H5.2 Z"), f("M15.2 15.2 H18.8 V18.8 H15.2 Z"),
        )
    }
    val Brush by lazy {
        icon(
            "brush", s("M19.5 4.5 L12.4 11.6"),
            s("M12 12 C9.6 10.8 7 12.3 7 15 C7 17.2 5.8 18.6 4.5 19.5 C8.3 20.3 13.4 18.9 12.9 14.9 C12.8 13.8 12.5 12.8 12 12 Z"),
        )
    }
    val Pencil by lazy { icon("pencil", s("M15.2 4.8 L19.2 8.8 L9 19 L4.5 19.5 L5 15 Z"), s("M13.2 6.8 L17.2 10.8")) }
    val Pen by lazy {
        icon(
            "pen", s("M12 3.5 L16.8 12.5 L13.6 19.5 H10.4 L7.2 12.5 Z"), s("M12 3.5 V10.6"),
            s(circle(12f, 12.4f, 1.4f)),
        )
    }
    val Line by lazy { icon("line", s("M5 19 L19 5")) }
    val Rect by lazy { icon("rect", s(rounded(4.5f, 6f, 15f, 12f, 2f))) }
    val Oval by lazy { icon("oval", s("M12 6 C16.4 6 20 8.7 20 12 C20 15.3 16.4 18 12 18 C7.6 18 4 15.3 4 12 C4 8.7 7.6 6 12 6 Z")) }
    val Bucket by lazy {
        icon(
            "bucket", s("M10.5 4 L18 11.5 L11.8 17.7 C10.6 18.9 8.7 18.9 7.5 17.7 L4.8 15 C3.6 13.8 3.6 11.9 4.8 10.7 Z"),
            s("M4.6 12.2 L17.4 12.1"),
            f("M20 14 C20 14 21.6 16 21.6 17.2 A1.6 1.6 0 0 1 18.4 17.2 C18.4 16 20 14 20 14 Z"),
        )
    }
    val Eraser by lazy {
        icon(
            "eraser",
            s("M14 4.6 C14.6 4 15.5 4 16.1 4.6 L19.4 7.9 C20 8.5 20 9.4 19.4 10 L10.4 19 H7.4 L4.6 16.2 C4 15.6 4 14.7 4.6 14.1 Z"),
            s("M9.2 9.4 L14.6 14.8"), s("M10.4 19 H19.5"),
        )
    }
    val Dropper by lazy {
        icon(
            "dropper", s("M5 19 L13.5 10.5"), s("M11.5 8.5 L15.5 12.5"),
            s("M13.5 10.5 L18 6 A2.12 2.12 0 0 0 15 3 L10.5 7.5"),
        )
    }
    val Hand by lazy {
        icon(
            "hand",
            s("M8 13 V6.5 A1.25 1.25 0 0 1 10.5 6.5 V11.5"),
            s("M10.5 11 V5 A1.25 1.25 0 0 1 13 5 V11"),
            s("M13 11 V6 A1.25 1.25 0 0 1 15.5 6 V11.5"),
            s("M15.5 11.5 V8.5 A1.25 1.25 0 0 1 18 8.5 V14 C18 17.6 15.6 20.5 12.2 20.5 C9.9 20.5 8.4 19.4 7.2 17.6 L5 14.2 A1.3 1.3 0 0 1 7.1 12.7 L8 13.8"),
        )
    }
    val Zoom by lazy { icon("zoom", s(circle(10.5f, 10.5f, 6f)), s("M15 15 L19.5 19.5")) }
    val ZoomIn by lazy { icon("zoomin", s(circle(10.5f, 10.5f, 6f)), s("M15 15 L19.5 19.5"), s("M8 10.5 H13"), s("M10.5 8 V13")) }
    val ZoomOut by lazy { icon("zoomout", s(circle(10.5f, 10.5f, 6f)), s("M15 15 L19.5 19.5"), s("M8 10.5 H13")) }

    // Transport & timeline
    val Play by lazy {
        icon("play", f("M8 6.2 C8 5.3 9 4.8 9.7 5.3 L18.4 11.1 C19.1 11.5 19.1 12.5 18.4 12.9 L9.7 18.7 C9 19.2 8 18.7 8 17.8 Z"))
    }
    val Pause by lazy { icon("pause", f(rounded(6.5f, 5f, 3.6f, 14f, 1.2f)), f(rounded(13.9f, 5f, 3.6f, 14f, 1.2f))) }
    val PrevKey by lazy {
        icon(
            "prevkey", s("M6.5 6 V18"),
            f("M18 6.8 V17.2 C18 17.9 17.2 18.3 16.6 17.9 L9.9 12.8 C9.4 12.4 9.4 11.6 9.9 11.2 L16.6 6.1 C17.2 5.7 18 6.1 18 6.8 Z"),
        )
    }
    val NextKey by lazy {
        icon(
            "nextkey", s("M17.5 6 V18"),
            f("M6 6.8 V17.2 C6 17.9 6.8 18.3 7.4 17.9 L14.1 12.8 C14.6 12.4 14.6 11.6 14.1 11.2 L7.4 6.1 C6.8 5.7 6 6.1 6 6.8 Z"),
        )
    }
    val Onion by lazy { icon("onion", s(circle(9f, 12f, 5.5f)), s(circle(15f, 12f, 5.5f))) }
    val Loop by lazy {
        icon(
            "loop", s("M17 3.5 L20 6.5 L17 9.5"), s("M4 11.5 V10.5 A4 4 0 0 1 8 6.5 H20"),
            s("M7 20.5 L4 17.5 L7 14.5"), s("M20 12.5 V13.5 A4 4 0 0 1 16 17.5 H4"),
        )
    }
    val Key by lazy { icon("key", s("M12 5 L19 12 L12 19 L5 12 Z")) }
    val KeyAdd by lazy { icon("keyadd", s("M10 6.5 L15.5 12 L10 17.5 L4.5 12 Z"), s("M19 4 V9"), s("M16.5 6.5 H21.5")) }
    val KeyRemove by lazy { icon("keyremove", s("M10 6.5 L15.5 12 L10 17.5 L4.5 12 Z"), s("M16.5 6.5 H21.5")) }
    val Tween by lazy { icon("tween", f(circle(5f, 12f, 2.2f)), s("M9 12 H19"), s("M16 9 L19 12 L16 15")) }

    // General
    val Undo by lazy { icon("undo", s("M9 6 L4.5 10.5 L9 15"), s("M4.5 10.5 H15 A4.5 4.5 0 0 1 15 19.5 H12")) }
    val Redo by lazy { icon("redo", s("M15 6 L19.5 10.5 L15 15"), s("M19.5 10.5 H9 A4.5 4.5 0 0 0 9 19.5 H12")) }
    val Plus by lazy { icon("plus", s("M12 5 V19"), s("M5 12 H19")) }
    val Minus by lazy { icon("minus", s("M5 12 H19")) }
    val Eye by lazy {
        icon(
            "eye", s("M2.5 12 C4.6 7.8 8 5.5 12 5.5 C16 5.5 19.4 7.8 21.5 12 C19.4 16.2 16 18.5 12 18.5 C8 18.5 4.6 16.2 2.5 12 Z"),
            s(circle(12f, 12f, 3f)),
        )
    }
    val EyeOff by lazy {
        icon(
            "eyeoff", s("M2.5 12 C4.6 7.8 8 5.5 12 5.5 C16 5.5 19.4 7.8 21.5 12 C19.4 16.2 16 18.5 12 18.5 C8 18.5 4.6 16.2 2.5 12 Z"),
            s("M4 4 L20 20"),
        )
    }
    val Lock by lazy { icon("lock", s(rounded(6f, 10.5f, 12f, 9f, 1.8f)), s("M8.5 10.5 V8 A3.5 3.5 0 0 1 15.5 8 V10.5")) }
    val Unlock by lazy { icon("unlock", s(rounded(6f, 10.5f, 12f, 9f, 1.8f)), s("M8.5 10.5 V8 A3.5 3.5 0 0 1 15.3 6.8")) }
    val Symbols by lazy { icon("symbols", s("M12 3.5 L19.5 7.75 V16.25 L12 20.5 L4.5 16.25 V7.75 Z"), s("M4.5 7.75 L12 12 L19.5 7.75"), s("M12 12 V20.5")) }
    val Picker by lazy {
        icon(
            "picker", s(rounded(4.5f, 4.5f, 6f, 6f, 1.4f)), s(rounded(13.5f, 4.5f, 6f, 6f, 1.4f)),
            s(rounded(4.5f, 13.5f, 6f, 6f, 1.4f)), s(rounded(13.5f, 13.5f, 6f, 6f, 1.4f)),
        )
    }
    val Curves by lazy {
        icon(
            "curves", s("M4.5 19.5 C10.5 19.5 13.5 4.5 19.5 4.5"), s("M4.5 19.5 H9"), s("M19.5 4.5 H15"),
            f(circle(10.2f, 19.5f, 1.7f)), f(circle(13.8f, 4.5f, 1.7f)),
        )
    }
    val Sliders by lazy {
        icon(
            "sliders", s("M4 7 H13"), s("M18 7 H20"), s(circle(15.5f, 7f, 2.5f)),
            s("M4 17 H6"), s("M11 17 H20"), s(circle(8.5f, 17f, 2.5f)),
        )
    }
    val More by lazy { icon("more", f(circle(6f, 12f, 1.6f)), f(circle(12f, 12f, 1.6f)), f(circle(18f, 12f, 1.6f))) }
    val Fit by lazy {
        icon(
            "fit", s("M4.5 9 V6 A1.5 1.5 0 0 1 6 4.5 H9"), s("M15 4.5 H18 A1.5 1.5 0 0 1 19.5 6 V9"),
            s("M19.5 15 V18 A1.5 1.5 0 0 1 18 19.5 H15"), s("M9 19.5 H6 A1.5 1.5 0 0 1 4.5 18 V15"),
        )
    }
    val Trash by lazy {
        icon(
            "trash", s("M4.5 7 H19.5"), s("M9.5 7 V5.5 A1 1 0 0 1 10.5 4.5 H13.5 A1 1 0 0 1 14.5 5.5 V7"),
            s("M6.5 7 L7.4 18.6 A1 1 0 0 0 8.4 19.5 H15.6 A1 1 0 0 0 16.6 18.6 L17.5 7"),
        )
    }
    val Duplicate by lazy {
        icon(
            "duplicate", s(rounded(8.5f, 8.5f, 11f, 11f, 2f)),
            s("M15.5 8.5 V6.5 A2 2 0 0 0 13.5 4.5 H6.5 A2 2 0 0 0 4.5 6.5 V13.5 A2 2 0 0 0 6.5 15.5 H8.5"),
        )
    }
    val ChevronUp by lazy { icon("up", s("M6 15 L12 9 L18 15")) }
    val ChevronDown by lazy { icon("down", s("M6 9 L12 15 L18 9")) }
    val Share by lazy {
        icon("share", s("M12 14.5 V4"), s("M8 7.5 L12 3.5 L16 7.5"), s("M5 12 V18 A1.5 1.5 0 0 0 6.5 19.5 H17.5 A1.5 1.5 0 0 0 19 18 V12"))
    }
    val Close by lazy { icon("close", s("M6 6 L18 18"), s("M18 6 L6 18")) }
    val Check by lazy { icon("check", s("M5 12.5 L10 17.5 L19 7")) }
    val Swap by lazy { icon("swap", s("M5 8 H18"), s("M15 5 L18 8 L15 11"), s("M19 16 H6"), s("M9 13 L6 16 L9 19")) }
    val Layers by lazy { icon("layers", s("M12 4 L20.5 8.5 L12 13 L3.5 8.5 Z"), s("M3.5 12.5 L12 17 L20.5 12.5"), s("M3.5 16.5 L12 21 L20.5 16.5")) }
    val Place by lazy { icon("place", s(rounded(4.5f, 4.5f, 15f, 15f, 3f)), s("M12 8.5 V15.5"), s("M8.5 12 H15.5")) }
    val Shape by lazy { icon("shape", s(circle(9.5f, 9.5f, 5.5f)), s(rounded(10.5f, 10.5f, 9f, 9f, 1.8f))) }
    val Marker by lazy { icon("marker", s("M6 20.5 V4.5"), s("M6 5 H17.5 L15 8.75 L17.5 12.5 H6")) }
    val Inspector by lazy { icon("inspector", s(rounded(3.5f, 4.5f, 17f, 15f, 2.5f)), s("M14.5 4.5 V19.5"), s("M16.8 8.5 H18"), s("M16.8 11.5 H18")) }
    val PrevFrame by lazy { icon("prevframe", s("M14.5 6.5 L9 12 L14.5 17.5")) }
    val NextFrame by lazy { icon("nextframe", s("M9.5 6.5 L15 12 L9.5 17.5")) }
    val Rename by lazy { icon("rename", s("M15.2 4.8 L19.2 8.8 L9 19 L4.5 19.5 L5 15 Z")) }
}
