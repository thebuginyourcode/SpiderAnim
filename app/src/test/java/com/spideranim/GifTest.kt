package com.spideranim

import com.spideranim.export.GifWriter
import com.spideranim.export.Lzw
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class GifTest {

    /** Straightforward GIF LZW decoder, used only to check the encoder. */
    private fun decode(blocks: ByteArray, minCodeSize: Int): ByteArray {
        val data = ByteArrayOutputStream()
        var i = 0
        while (i < blocks.size) {
            val n = blocks[i].toInt() and 0xFF
            i++
            if (n == 0) break
            data.write(blocks, i, n)
            i += n
        }
        val bytes = data.toByteArray()
        val clear = 1 shl minCodeSize
        val eoi = clear + 1
        val out = ByteArrayOutputStream()
        val dict = ArrayList<ByteArray>()
        fun reset() {
            dict.clear()
            for (c in 0 until clear) dict.add(byteArrayOf(c.toByte()))
            dict.add(ByteArray(0))
            dict.add(ByteArray(0))
        }
        reset()
        var codeSize = minCodeSize + 1
        var bitPos = 0
        var prev: ByteArray? = null
        while (true) {
            var code = 0
            for (b in 0 until codeSize) {
                val byte = bytes[(bitPos + b) / 8].toInt() and 0xFF
                if ((byte shr ((bitPos + b) % 8)) and 1 == 1) code = code or (1 shl b)
            }
            bitPos += codeSize
            if (code == clear) {
                reset()
                codeSize = minCodeSize + 1
                prev = null
                continue
            }
            if (code == eoi) break
            val entry = when {
                code < dict.size -> dict[code]
                prev != null -> prev + prev[0]
                else -> error("bad code $code")
            }
            out.write(entry)
            if (prev != null && dict.size < 4096) dict.add(prev + entry[0])
            prev = entry
            if (dict.size == (1 shl codeSize) && codeSize < 12) codeSize++
        }
        return out.toByteArray()
    }

    private fun roundTrip(input: ByteArray) {
        val out = ByteArrayOutputStream()
        Lzw.encode(input, 8, out)
        assertArrayEquals(input, decode(out.toByteArray(), 8))
    }

    @Test
    fun lzwRoundTripsFlatArt() {
        // Long runs and a few colours, like cartoon frames; forces many dictionary resets.
        val r = Random(7)
        val data = ByteArray(300_000) { i -> ((i / 97 + (if (r.nextInt(50) == 0) 3 else 0)) % 6).toByte() }
        roundTrip(data)
    }

    @Test
    fun lzwRoundTripsNoise() {
        val r = Random(42)
        roundTrip(ByteArray(120_000) { r.nextInt(256).toByte() })
    }

    @Test
    fun lzwRoundTripsTinyInputs() {
        roundTrip(byteArrayOf(5))
        roundTrip(byteArrayOf(1, 1, 1, 1, 1, 1, 1))
    }

    @Test
    fun gifHasValidFraming() {
        val out = ByteArrayOutputStream()
        val gif = GifWriter(out, 4, 2)
        gif.header(intArrayOf(0xFFFFFF, 0x000000))
        gif.frame(ByteArray(8) { (it % 2).toByte() }, 4)
        gif.end()
        val b = out.toByteArray()
        assertEquals("GIF89a", String(b, 0, 6, Charsets.US_ASCII))
        assertEquals(4, b[6].toInt() or (b[7].toInt() shl 8))
        assertEquals(0x3B, b.last().toInt())
    }
}
