package com.spideranim.export

import java.io.OutputStream

/** Minimal looping GIF89a writer with a single global 256-colour palette. */
class GifWriter(private val out: OutputStream, private val width: Int, private val height: Int) {

    fun header(palette: IntArray) {
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        short(width)
        short(height)
        out.write(0xF7) // global colour table, 8 bits colour resolution, 256 entries
        out.write(0)
        out.write(0)
        for (i in 0 until 256) {
            val c = if (i < palette.size) palette[i] else 0
            out.write((c shr 16) and 0xFF)
            out.write((c shr 8) and 0xFF)
            out.write(c and 0xFF)
        }
        // NETSCAPE2.0 application extension: loop forever.
        out.write(0x21); out.write(0xFF); out.write(11)
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(3); out.write(1); short(0); out.write(0)
    }

    fun frame(indices: ByteArray, delayCentis: Int) {
        out.write(0x21); out.write(0xF9); out.write(4)
        out.write(0x04) // disposal: leave in place
        short(delayCentis)
        out.write(0); out.write(0)

        out.write(0x2C)
        short(0); short(0); short(width); short(height)
        out.write(0)

        out.write(MIN_CODE_SIZE)
        Lzw.encode(indices, MIN_CODE_SIZE, out)
        out.write(0)
    }

    fun end() {
        out.write(0x3B)
        out.flush()
    }

    private fun short(v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
    }

    companion object {
        const val MIN_CODE_SIZE = 8
    }
}

/** Variable-length-code LZW as GIF wants it, written straight into 255-byte sub-blocks. */
object Lzw {
    private const val TABLE = 8209 // prime, ~2x the 4096 code space
    private const val MAX_CODE = 4096

    fun encode(data: ByteArray, minCodeSize: Int, out: OutputStream) {
        val clear = 1 shl minCodeSize
        val eoi = clear + 1
        var codeSize = minCodeSize + 1
        var nextCode = eoi + 1

        val keys = IntArray(TABLE) { -1 }
        val codes = IntArray(TABLE)

        val block = ByteArray(255)
        var blockLen = 0
        var bitBuf = 0L
        var bitCount = 0

        fun byte(b: Int) {
            block[blockLen++] = b.toByte()
            if (blockLen == 255) {
                out.write(255)
                out.write(block, 0, 255)
                blockLen = 0
            }
        }

        fun emit(code: Int) {
            bitBuf = bitBuf or (code.toLong() shl bitCount)
            bitCount += codeSize
            while (bitCount >= 8) {
                byte((bitBuf and 0xFF).toInt())
                bitBuf = bitBuf ushr 8
                bitCount -= 8
            }
        }

        fun slot(key: Int): Int {
            var i = ((key xor (key ushr 11)) and 0x7FFFFFFF) % TABLE
            while (keys[i] != -1 && keys[i] != key) {
                i++
                if (i == TABLE) i = 0
            }
            return i
        }

        emit(clear)
        if (data.isNotEmpty()) {
            var prefix = data[0].toInt() and 0xFF
            for (n in 1 until data.size) {
                val k = data[n].toInt() and 0xFF
                val key = (prefix shl 8) or k
                val s = slot(key)
                if (keys[s] == key) {
                    prefix = codes[s]
                    continue
                }
                emit(prefix)
                if (nextCode < MAX_CODE) {
                    keys[s] = key
                    codes[s] = nextCode++
                    if (nextCode > (1 shl codeSize) && codeSize < 12) codeSize++
                } else {
                    emit(clear)
                    keys.fill(-1)
                    codeSize = minCodeSize + 1
                    nextCode = eoi + 1
                }
                prefix = k
            }
            emit(prefix)
        }
        emit(eoi)
        if (bitCount > 0) byte((bitBuf and 0xFF).toInt())
        if (blockLen > 0) {
            out.write(blockLen)
            out.write(block, 0, blockLen)
        }
    }
}

/**
 * Popularity palette over a 15-bit colour histogram. Flat cartoon artwork rarely needs more than
 * a few dozen colours, so this keeps edges clean without dithering noise.
 */
class Quantizer {
    private val counts = IntArray(32768)
    private val sumR = LongArray(32768)
    private val sumG = LongArray(32768)
    private val sumB = LongArray(32768)

    fun add(pixels: IntArray, step: Int = 1) {
        var i = 0
        while (i < pixels.size) {
            val c = pixels[i]
            val k = key(c)
            counts[k]++
            sumR[k] += ((c shr 16) and 0xFF).toLong()
            sumG[k] += ((c shr 8) and 0xFF).toLong()
            sumB[k] += (c and 0xFF).toLong()
            i += step
        }
    }

    fun build(): Palette {
        val used = (0 until 32768).filter { counts[it] > 0 }.sortedByDescending { counts[it] }.take(256)
        val colors = IntArray(256)
        val lookup = IntArray(32768) { -1 }
        used.forEachIndexed { i, k ->
            val n = counts[k]
            val r = (sumR[k] / n).toInt()
            val g = (sumG[k] / n).toInt()
            val b = (sumB[k] / n).toInt()
            colors[i] = (r shl 16) or (g shl 8) or b
            lookup[k] = i
        }
        return Palette(colors, used.size.coerceAtLeast(1), lookup)
    }

    companion object {
        fun key(c: Int): Int = (((c shr 19) and 0x1F) shl 10) or (((c shr 11) and 0x1F) shl 5) or ((c shr 3) and 0x1F)
    }
}

class Palette(val colors: IntArray, private val size: Int, private val lookup: IntArray) {
    fun index(pixels: IntArray, out: ByteArray) {
        for (i in pixels.indices) {
            val k = Quantizer.key(pixels[i])
            var idx = lookup[k]
            if (idx < 0) {
                idx = nearest(pixels[i])
                lookup[k] = idx
            }
            out[i] = idx.toByte()
        }
    }

    private fun nearest(c: Int): Int {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        var best = 0
        var bestD = Int.MAX_VALUE
        for (i in 0 until size) {
            val p = colors[i]
            val dr = r - ((p shr 16) and 0xFF)
            val dg = g - ((p shr 8) and 0xFF)
            val db = b - (p and 0xFF)
            val d = 2 * dr * dr + 4 * dg * dg + 3 * db * db
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }
}
