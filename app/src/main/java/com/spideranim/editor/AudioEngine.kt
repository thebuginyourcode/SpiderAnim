package com.spideranim.editor

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.viewModelScope
import com.spideranim.model.Ids
import com.spideranim.model.LayerType
import com.spideranim.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder
import kotlin.math.abs

/** Audio layers: copies files in, decodes a waveform for the timeline, plays along with the scene. */
class AudioEngine(private val editor: Editor) {

    private val players = HashMap<String, MediaPlayer>()

    /** Peak level per 10 ms, by file name. */
    val peaks = mutableStateMapOf<String, FloatArray>()

    fun file(name: String) = File(editor.audioDir, name)

    fun copyIn(uri: Uri): String? = runCatching {
        val resolver = editor.getApplication<android.app.Application>().contentResolver
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(resolver.getType(uri)) ?: "m4a"
        val name = "${Ids.next()}.$ext"
        resolver.openInputStream(uri)!!.use { input -> file(name).outputStream().use { input.copyTo(it) } }
        name
    }.getOrNull()

    fun load(p: Project) {
        release()
        for (l in p.scene.layers) {
            val name = l.audio ?: continue
            if (l.type != LayerType.Audio || players.containsKey(name)) continue
            val f = file(name)
            if (!f.exists()) continue
            runCatching {
                players[name] = MediaPlayer().apply {
                    setDataSource(f.path)
                    prepare()
                }
            }
            if (!peaks.containsKey(name)) {
                editor.viewModelScope.launch {
                    val data = withContext(Dispatchers.Default) { runCatching { decodePeaks(f) }.getOrNull() }
                    if (data != null) peaks[name] = data
                }
            }
        }
    }

    private fun audible(): List<MediaPlayer> {
        if (editor.inSymbol) return emptyList()
        return editor.project.scene.layers
            .filter { it.type == LayerType.Audio && it.visible && it.audio != null }
            .mapNotNull { players[it.audio] }
    }

    private fun ms(frame: Int) = (frame * 1000L / editor.project.fps).toInt()

    fun play(frame: Int) {
        audible().forEach {
            runCatching {
                if (ms(frame) < it.duration) {
                    it.seekTo(ms(frame))
                    it.start()
                }
            }
        }
    }

    fun sync(frame: Int) {
        audible().forEach { runCatching { if (ms(frame) < it.duration) it.seekTo(ms(frame)) else it.pause() } }
    }

    fun pause() {
        players.values.forEach { runCatching { if (it.isPlaying) it.pause() } }
    }

    fun release() {
        players.values.forEach { runCatching { it.release() } }
        players.clear()
    }

    private fun decodePeaks(f: File): FloatArray {
        val ex = MediaExtractor()
        ex.setDataSource(f.path)
        val track = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("audio/") }
        ex.selectTrack(track)
        val fmt = ex.getTrackFormat(track)
        val rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, 0)
        codec.start()
        val perBucket = (rate / 100 * channels).coerceAtLeast(1)
        val out = ArrayList<Float>()
        var cur = 0f
        var n = 0
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var idle = 0
        try {
            while (idle < 500) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!
                        val size = ex.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(i, 0, size, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o < 0) {
                    idle++
                    continue
                }
                idle = 0
                val buf = codec.getOutputBuffer(o)!!
                buf.position(info.offset)
                buf.limit(info.offset + info.size)
                val shorts = buf.order(ByteOrder.nativeOrder()).asShortBuffer()
                while (shorts.hasRemaining()) {
                    val v = abs(shorts.get().toInt()) / 32768f
                    if (v > cur) cur = v
                    if (++n >= perBucket) {
                        out.add(cur)
                        cur = 0f
                        n = 0
                    }
                }
                codec.releaseOutputBuffer(o, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        } finally {
            codec.stop()
            codec.release()
            ex.release()
        }
        return out.toFloatArray()
    }
}
