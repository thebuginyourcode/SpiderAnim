package com.spideranim.export

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.spideranim.model.Project
import com.spideranim.ui.drawScene
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object GifExporter {

    /** Renders every frame twice: once to build the palette, once to encode. Blocking; call off the main thread. */
    fun export(project: Project, file: File, maxSide: Int = 720, progress: (Float) -> Unit) {
        val scale = min(1f, maxSide.toFloat() / max(project.width, project.height))
        val w = max(1, (project.width * scale).roundToInt())
        val h = max(1, (project.height * scale).roundToInt())
        val bitmap = ImageBitmap(w, h, ImageBitmapConfig.Argb8888)
        val canvas = Canvas(bitmap)
        val scope = CanvasDrawScope()
        val pixels = IntArray(w * h)
        val frames = project.length.coerceAtLeast(1)

        fun render(frame: Int) {
            scope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
                drawRect(Color(project.background))
                withTransform({ scale(scale, scale, Offset.Zero) }) {
                    clipRect(0f, 0f, project.width.toFloat(), project.height.toFloat()) {
                        drawScene(project, frame, cached = false)
                    }
                }
            }
            bitmap.readPixels(pixels)
        }

        val quantizer = Quantizer()
        for (f in 0 until frames) {
            render(f)
            quantizer.add(pixels, step = 3)
            progress(0.4f * (f + 1) / frames)
        }
        val palette = quantizer.build()

        val delay = (100f / project.fps).roundToInt().coerceAtLeast(2)
        val indices = ByteArray(w * h)
        file.parentFile?.mkdirs()
        file.outputStream().buffered().use { os ->
            val gif = GifWriter(os, w, h)
            gif.header(palette.colors)
            for (f in 0 until frames) {
                render(f)
                palette.index(pixels, indices)
                gif.frame(indices, delay)
                progress(0.4f + 0.6f * (f + 1) / frames)
            }
            gif.end()
        }
    }
}
