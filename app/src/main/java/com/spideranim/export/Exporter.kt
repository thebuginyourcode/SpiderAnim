package com.spideranim.export

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.spideranim.model.Project
import com.spideranim.ui.drawSceneFrame
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Renders the scene off screen: GIF, MP4 and gallery thumbnails. Blocking; call off the main thread. */
object Exporter {

    private class Frames(val project: Project, maxSide: Int, even: Boolean) {
        val scale = min(1f, maxSide.toFloat() / max(project.width, project.height))
        val w = (project.width * scale).roundToInt().let { if (even) it / 2 * 2 else it }.coerceAtLeast(2)
        val h = (project.height * scale).roundToInt().let { if (even) it / 2 * 2 else it }.coerceAtLeast(2)
        val image = ImageBitmap(w, h, ImageBitmapConfig.Argb8888)
        private val canvas = Canvas(image)
        private val scope = CanvasDrawScope()
        val pixels = IntArray(w * h)

        fun render(frame: Int) {
            scope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
                withTransform({ scale(w / project.width.toFloat(), h / project.height.toFloat(), Offset.Zero) }) {
                    clipRect(0f, 0f, project.width.toFloat(), project.height.toFloat()) {
                        drawSceneFrame(project, frame, cached = false)
                    }
                }
            }
        }

        fun readPixels() = image.readPixels(pixels)
    }

    fun writeThumbnail(p: Project, out: File) {
        val f = Frames(p, 480, even = false)
        f.render(0)
        out.outputStream().use { f.image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 90, it) }
    }

    fun gif(p: Project, out: File, progress: (Float) -> Unit) {
        val frames = Frames(p, 720, even = false)
        val n = p.scene.length
        val quantizer = Quantizer()
        for (i in 0 until n) {
            frames.render(i)
            frames.readPixels()
            quantizer.add(frames.pixels, step = 3)
            progress(0.4f * (i + 1) / n)
        }
        val palette = quantizer.build()
        val indices = ByteArray(frames.w * frames.h)
        val delay = (100f / p.fps).roundToInt().coerceAtLeast(2)
        out.outputStream().buffered().use { os ->
            val gif = GifWriter(os, frames.w, frames.h)
            gif.header(palette.colors)
            for (i in 0 until n) {
                frames.render(i)
                frames.readPixels()
                palette.index(frames.pixels, indices)
                gif.frame(indices, delay)
                progress(0.4f + 0.6f * (i + 1) / n)
            }
            gif.end()
        }
    }

    /** H.264 through the encoder's input surface. Timestamps are rewritten to exact frame times. */
    fun mp4(p: Project, out: File, progress: (Float) -> Unit) {
        val frames = Frames(p, 1280, even = true)
        val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, frames.w, frames.h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, (frames.w * frames.h * 5).coerceAtLeast(500_000))
            setInteger(MediaFormat.KEY_FRAME_RATE, p.fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = enc.createInputSurface()
        enc.start()
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var written = 0
        val info = MediaCodec.BufferInfo()

        fun drain(end: Boolean) {
            var waits = 0
            while (true) {
                val i = enc.dequeueOutputBuffer(info, 10_000)
                when {
                    i == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!end || ++waits > 300) return
                    }
                    i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(enc.outputFormat)
                        muxer.start()
                    }
                    i >= 0 -> {
                        val buf = enc.getOutputBuffer(i)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && track >= 0) {
                            info.presentationTimeUs = written * 1_000_000L / p.fps
                            written++
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            muxer.writeSampleData(track, buf, info)
                        }
                        enc.releaseOutputBuffer(i, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }

        try {
            val n = p.scene.length
            val bmp = frames.image.asAndroidBitmap()
            for (i in 0 until n) {
                frames.render(i)
                val c = surface.lockHardwareCanvas()
                c.drawBitmap(bmp, 0f, 0f, null)
                surface.unlockCanvasAndPost(c)
                drain(false)
                progress((i + 1f) / n)
            }
            enc.signalEndOfInputStream()
            drain(true)
        } finally {
            runCatching { enc.stop() }
            enc.release()
            surface.release()
            runCatching { if (track >= 0) muxer.stop() }
            muxer.release()
        }
        if (written == 0) error("Encoder produced no frames")
    }
}
