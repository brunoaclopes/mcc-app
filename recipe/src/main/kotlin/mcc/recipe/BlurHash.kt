package mcc.recipe

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.withSign

/**
 * BlurHash decoder (https://blurha.sh): turns the index's "blurhash" of a recipe photo into a few pixels the apps
 * stretch into a blurred placeholder while the photo loads. Encoded by recipe-repo/tools/blurhash.py.
 */
object BlurHash {
    private const val CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~"

    private fun d83(s: String, from: Int, to: Int): Int {
        var v = 0
        for (i in from until to) {
            val d = CHARS.indexOf(s[i])
            require(d >= 0) { "not a BlurHash" }
            v = v * 83 + d
        }
        return v
    }

    private fun toLinear(v: Int): Float { val x = v / 255f; return if (x <= 0.04045f) x / 12.92f else ((x + 0.055f) / 1.055f).pow(2.4f) }

    private fun toSrgb(v: Float): Int {
        val x = v.coerceIn(0f, 1f)
        return (if (x <= 0.0031308f) x * 12.92f * 255f + 0.5f else (1.055f * x.pow(1 / 2.4f) - 0.055f) * 255f + 0.5f).toInt().coerceIn(0, 255)
    }

    /** ARGB pixels ([width] x [height], row by row), or null when [hash] is not a valid BlurHash. */
    fun decode(hash: String, width: Int, height: Int): IntArray? = runCatching {
        require(hash.length >= 6 && width > 0 && height > 0)
        val flag = d83(hash, 0, 1)
        val nx = flag % 9 + 1; val ny = flag / 9 + 1
        require(hash.length == 4 + 2 * nx * ny)
        val maxValue = (d83(hash, 1, 2) + 1) / 166f
        val colors = Array(nx * ny) { FloatArray(3) }
        val dc = d83(hash, 2, 6)
        colors[0][0] = toLinear(dc shr 16); colors[0][1] = toLinear((dc shr 8) and 255); colors[0][2] = toLinear(dc and 255)
        for (i in 1 until nx * ny) {
            val v = d83(hash, 4 + i * 2, 6 + i * 2)
            val q = intArrayOf(v / 361, (v / 19) % 19, v % 19)
            for (c in 0..2) { val f = (q[c] - 9) / 9f; colors[i][c] = (f * f).withSign(f) * maxValue }
        }
        val cosX = Array(nx) { i -> FloatArray(width) { x -> cos(PI * i * x / width).toFloat() } }
        val cosY = Array(ny) { j -> FloatArray(height) { y -> cos(PI * j * y / height).toFloat() } }
        IntArray(width * height) { p ->
            val x = p % width; val y = p / width
            var r = 0f; var g = 0f; var b = 0f
            for (j in 0 until ny) for (i in 0 until nx) {
                val f = cosX[i][x] * cosY[j][y]
                val c = colors[i + j * nx]
                r += c[0] * f; g += c[1] * f; b += c[2] * f
            }
            (0xFF shl 24) or (toSrgb(r) shl 16) or (toSrgb(g) shl 8) or toSrgb(b)
        }
    }.getOrNull()

}
