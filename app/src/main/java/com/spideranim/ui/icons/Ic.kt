package com.spideranim.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private class Part(val d: String, val filled: Boolean, val w: Float = 1.8f)

private fun s(d: String, w: Float = 1.8f) = Part(d, false, w)
private fun f(d: String) = Part(d, true)

private fun circle(cx: Float, cy: Float, r: Float) =
    "M$cx ${cy - r} A$r $r 0 1 1 $cx ${cy + r} A$r $r 0 1 1 $cx ${cy - r} Z"

private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
    "M${x + r} $y H${x + w - r} A$r $r 0 0 1 ${x + w} ${y + r} V${y + h - r} A$r $r 0 0 1 ${x + w - r} ${y + h} " +
        "H${x + r} A$r $r 0 0 1 $x ${y + h - r} V${y + r} A$r $r 0 0 1 ${x + r} $y Z"

/** Glyphs on a 24-unit grid: filled shapes like ToonSquid's, white, tinted by Icon(). */
private fun icon(name: String, vararg parts: Part): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply {
            for (p in parts) {
                if (p.filled) {
                    addPath(pathData = addPathNodes(p.d), pathFillType = PathFillType.EvenOdd, fill = SolidColor(Color.White))
                } else {
                    addPath(
                        pathData = addPathNodes(p.d),
                        stroke = SolidColor(Color.White),
                        strokeLineWidth = p.w,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
        }
        .build()

object Ic {
    // ---- tools
    val Select by lazy { icon("select", f("M6 3 L6 19.5 L10 15.8 L12.8 21.2 L15.3 20 L12.6 14.8 L18 14.4 Z")) }
    val Lasso by lazy {
        icon(
            "lasso",
            s("M5 8 V5 H8", 2f), s("M11 5 H13", 2f), s("M16 5 H19 V8", 2f), s("M19 11 V13", 2f),
            s("M19 16 V19 H16", 2f), s("M13 19 H11", 2f), s("M8 19 H5 V16", 2f), s("M5 13 V11", 2f),
        )
    }
    val Brush by lazy {
        icon(
            "brush",
            f("M20.7 3.3 C21.2 3.8 21.1 4.6 20.6 5.1 L12.4 13.3 L10.7 11.6 L18.9 3.4 C19.4 2.9 20.2 2.8 20.7 3.3 Z"),
            f("M9.9 12.4 L11.6 14.1 C11.3 16.9 9.4 19.6 5 20.5 C4.3 20.6 3.7 20 3.9 19.3 C4.9 15.3 7 12.8 9.9 12.4 Z"),
        )
    }
    val Eraser by lazy {
        icon(
            "eraser",
            f("M14.2 3.6 C14.9 2.9 16.1 2.9 16.8 3.6 L20.4 7.2 C21.1 7.9 21.1 9.1 20.4 9.8 L13 17.2 L6.8 11 Z"),
            f("M5.8 12 L12 18.2 L10.6 19.6 C10.2 20 9.7 20.2 9.2 20.2 L7.1 20.2 C6.6 20.2 6.1 20 5.7 19.6 L3.6 17.5 C2.9 16.8 2.9 15.6 3.6 14.9 Z"),
        )
    }
    val Fill by lazy {
        icon(
            "fill",
            f("M10.6 3.3 L18.5 11.2 C19 11.7 19 12.5 18.5 13 L12.3 19.2 C11.1 20.4 9.2 20.4 8 19.2 L4.3 15.5 C3.1 14.3 3.1 12.4 4.3 11.2 L9.8 5.7 L8.3 4.2 C8 3.9 8 3.5 8.3 3.2 C8.6 2.9 9 2.9 9.3 3.2 Z"),
            f("M20.5 14.5 C20.5 14.5 22.3 16.7 22.3 18 A1.8 1.8 0 0 1 18.7 18 C18.7 16.7 20.5 14.5 20.5 14.5 Z"),
        )
    }
    val Eyedropper by lazy {
        icon(
            "eyedropper",
            f("M17.6 3.4 C18.6 2.4 20.2 2.4 21.1 3.4 C22.1 4.3 22.1 5.9 21.1 6.9 L19.3 8.7 L20 9.4 C20.3 9.7 20.3 10.2 20 10.5 C19.7 10.8 19.2 10.8 18.9 10.5 L13.5 5.1 C13.2 4.8 13.2 4.3 13.5 4 C13.8 3.7 14.3 3.7 14.6 4 L15.3 4.7 Z"),
            s("M15.3 8.7 L7 17 C6.5 17.5 6.3 18.3 6.5 19 L5 20.5"),
        )
    }
    val Text by lazy { icon("text", f("M5 4 H19 V8 H17.6 L17.1 6.1 H13.5 V18 L15.3 18.6 V20 H8.7 V18.6 L10.5 18 V6.1 H6.9 L6.4 8 H5 Z")) }
    val Pen by lazy {
        icon(
            "pen",
            f("M12 2.5 L17.8 11.5 C18.1 12 18.1 12.6 17.8 13.1 L15 18 H9 L6.2 13.1 C5.9 12.6 5.9 12 6.2 11.5 Z"),
            f("M8.6 19.2 H15.4 V21.6 H8.6 Z"),
        )
    }

    // ---- sidebar
    val Layers by lazy {
        icon(
            "layers", f("M12 2.8 L21.6 8 L12 13.2 L2.4 8 Z"),
            f("M4.4 11.1 L12 15.2 L19.6 11.1 L21.6 12.2 L12 17.4 L2.4 12.2 Z"),
            f("M4.4 15.3 L12 19.4 L19.6 15.3 L21.6 16.4 L12 21.6 L2.4 16.4 Z"),
        )
    }
    val Properties by lazy {
        icon(
            "properties", s("M3 7 H6.5"), s("M11.5 7 H21"), s(circle(9f, 7f, 2.5f)),
            s("M3 17 H12.5"), s("M17.5 17 H21"), s(circle(15f, 17f, 2.5f)),
        )
    }
    val Trash by lazy {
        icon(
            "trash", s("M4 6.5 H20"), s("M9.5 6.5 V4.5 H14.5 V6.5"),
            s("M6 6.5 L7 20 H17 L18 6.5"), s("M10 10 V16.5"), s("M14 10 V16.5"),
        )
    }
    val Undo by lazy { icon("undo", s("M9 5 L5 9 L9 13", 2f), s("M5 9 H14.5 A5 5 0 0 1 14.5 19 H11", 2f)) }
    val Redo by lazy { icon("redo", s("M15 5 L19 9 L15 13", 2f), s("M19 9 H9.5 A5 5 0 0 0 9.5 19 H13", 2f)) }

    // ---- top bar
    val Play by lazy { icon("play", f("M7 4.5 L19.5 12 L7 19.5 Z")) }
    val Pause by lazy { icon("pause", f("M6.5 4.5 H10 V19.5 H6.5 Z"), f("M14 4.5 H17.5 V19.5 H14 Z")) }
    val Magnet by lazy { icon("magnet", s("M17 6 H11 A6 6 0 0 0 11 18 H17", 3.2f), f("M17.5 3.8 H21 V8.2 H17.5 Z"), f("M17.5 15.8 H21 V20.2 H17.5 Z")) }
    val FlipH by lazy { icon("fliph", f("M10.8 4 V20 H3 Z"), s("M13.2 4 V20 H21 Z", 1.5f)) }
    val FlipV by lazy { icon("flipv", f("M4 10.8 H20 L4 3 Z"), s("M4 13.2 H20 L4 21 Z", 1.5f)) }
    val Expand by lazy { icon("expand", s("M14 4 H20 V10", 1.5f), s("M20 4 L14 10", 1.5f), s("M10 20 H4 V14", 1.5f), s("M4 20 L10 14", 1.5f)) }

    // ---- playback
    val PrevFrame by lazy { icon("prevframe", f("M4 5 H6.8 V19 H4 Z"), f("M20 5 V19 L8.8 12 Z")) }
    val NextFrame by lazy { icon("nextframe", f("M4 5 V19 L15.2 12 Z"), f("M17.2 5 H20 V19 H17.2 Z")) }
    val Rewind by lazy { icon("rewind", f("M12 6 V18 L3 12 Z"), f("M21 6 V18 L12 12 Z")) }
    val Forward by lazy { icon("forward", f("M3 6 V18 L12 12 Z"), f("M12 6 V18 L21 12 Z")) }
    val Onion by lazy {
        icon(
            "onion",
            f("M12 2.5 C12 2.5 12.8 5 15 6.6 C18 8.7 19.8 11 19.8 14 C19.8 18.2 16.3 21.3 12 21.3 C7.7 21.3 4.2 18.2 4.2 14 C4.2 11 6 8.7 9 6.6 C11.2 5 12 2.5 12 2.5 Z"),
        )
    }
    val Loop by lazy {
        icon(
            "loop", s("M17 3.5 L20 6.5 L17 9.5", 2f), s("M4 12 A6 6 0 0 1 10 6.5 H20", 2f),
            s("M7 20.5 L4 17.5 L7 14.5", 2f), s("M20 12 A6 6 0 0 1 14 17.5 H4", 2f),
        )
    }
    val Collapse by lazy { icon("collapse", f("M3.5 3.5 H20.5 V5.5 H3.5 Z"), f("M3.5 8 H20.5 L12 20 Z")) }
    val Uncollapse by lazy { icon("uncollapse", f("M3.5 18.5 H20.5 V20.5 H3.5 Z"), f("M3.5 16 H20.5 L12 4 Z")) }

    // ---- timeline bottom bar
    val Path by lazy { icon("path", s("M5 7 H9 A3 3 0 0 1 12 10 V14 A3 3 0 0 0 15 17 H19", 2f), f(circle(5f, 7f, 2.2f)), f(circle(19f, 17f, 2.2f))) }
    val Key by lazy { icon("key", f("M12 4.5 L19.5 12 L12 19.5 L4.5 12 Z")) }
    val Curve by lazy { icon("curve", s(rect(3f, 3f, 18f, 18f, 1.5f), 1.2f), s("M5 19 C12 19 12 5 19 5", 2f)) }

    // ---- general
    val Plus by lazy { icon("plus", s("M12 4.5 V19.5", 2f), s("M4.5 12 H19.5", 2f)) }
    val Minus by lazy { icon("minus", s("M5 12 H19", 2f)) }
    val Close by lazy { icon("close", s("M6 6 L18 18", 1.6f), s("M18 6 L6 18", 1.6f)) }
    val Back by lazy { icon("back", s("M15 4.5 L7.5 12 L15 19.5", 2f)) }
    val Chevron by lazy { icon("chevron", s("M9 5 L16 12 L9 19", 2f)) }
    val Search by lazy { icon("search", s(circle(10.5f, 10.5f, 6f), 2f), s("M15 15 L20 20", 2f)) }
    val Eye by lazy {
        icon(
            "eye",
            f("M12 5 C16.5 5 20.2 7.8 22 12 C20.2 16.2 16.5 19 12 19 C7.5 19 3.8 16.2 2 12 C3.8 7.8 7.5 5 12 5 Z M12 8.2 A3.8 3.8 0 1 0 12 15.8 A3.8 3.8 0 1 0 12 8.2 Z"),
            f(circle(12f, 12f, 2f)),
        )
    }
    val EyeOff by lazy {
        icon(
            "eyeoff",
            s("M2 12 C3.8 7.8 7.5 5 12 5 C16.5 5 20.2 7.8 22 12 C20.2 16.2 16.5 19 12 19 C7.5 19 3.8 16.2 2 12 Z", 1.6f),
            s("M4 4 L20 20", 1.6f),
        )
    }
    val Speaker by lazy {
        icon(
            "speaker", f("M3 9 H7 L12 4.5 V19.5 L7 15 H3 Z"),
            s("M15.5 8.5 A5 5 0 0 1 15.5 15.5", 2f), s("M18.3 5.8 A9 9 0 0 1 18.3 18.2", 2f),
        )
    }
    val Mute by lazy { icon("mute", f("M3 9 H7 L12 4.5 V19.5 L7 15 H3 Z"), s("M16 9 L21 15", 2f), s("M21 9 L16 15", 2f)) }
    val Camera by lazy { icon("camera", f(rect(2.5f, 8f, 13f, 10f, 2f)), f("M16.5 11.5 L21.5 8.5 V17.5 L16.5 14.5 Z"), f(circle(6.5f, 6f, 2.3f)), f(circle(12f, 6f, 2.3f))) }
    val Lock by lazy { icon("lock", f(rect(5.5f, 10.5f, 13f, 10f, 2f)), s("M8.5 10.5 V8 A3.5 3.5 0 0 1 15.5 8 V10.5", 2f)) }
    val More by lazy { icon("more", f(circle(5.5f, 12f, 1.8f)), f(circle(12f, 12f, 1.8f)), f(circle(18.5f, 12f, 1.8f))) }
    val Fit by lazy {
        icon(
            "fit", s("M4.5 9 V4.5 H9", 1.6f), s("M15 4.5 H19.5 V9", 1.6f),
            s("M19.5 15 V19.5 H15", 1.6f), s("M9 19.5 H4.5 V15", 1.6f),
        )
    }
    val Symbol by lazy { icon("symbol", s("M12 3 L20 7.5 V16.5 L12 21 L4 16.5 V7.5 Z", 1.6f), s("M4 7.5 L12 12 L20 7.5", 1.6f), s("M12 12 V21", 1.6f)) }
    val Marker by lazy { icon("marker", f("M6 3 H18 V20 L12 15.5 L6 20 Z")) }
}
