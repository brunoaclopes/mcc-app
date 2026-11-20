package mcc.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView

/**
 * Photo with rounded corners, a placeholder gradient and an optional dark scrim, drawn in one pass.
 * Avoids outline clipping and stacked overlay views, which are expensive on this Mali-400 GPU.
 */
class RoundImage(context: Context, private val radiusDp: Float) : ImageView(context) {
    /** Scrim: fraction of the height (from the bottom, or from the left when [scrimLeft]) covered by the dark gradient. */
    var scrim = 0f
    var scrimLeft = false
    var scrimColor = 0xEE000000.toInt()
    var placeholderA = T.SURFACE_2
    var placeholderB = T.SURFACE_3
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val m = Matrix()
    private var shaderFor: Bitmap? = null
    private var shaderW = 0
    private var shaderH = 0

    /** Drawn large and faint over the placeholder when there is no photo. */
    var art: Icon? = null
        set(v) { if (field != v) { field = v; invalidate() } }

    companion object {
        private val artCache = android.util.LruCache<String, Bitmap>(12)

        /** Android 6 draws scaled outlines from a small texture (blurry), so covers are rendered once at full size. */
        fun artBitmap(icon: Icon, size: Int): Bitmap {
            val key = "$icon@$size"
            artCache.get(key)?.let { return it }
            val b = Bitmap.createBitmap(size.coerceAtLeast(1), size.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            Icons.draw(Canvas(b), icon, 0f, 0f, size.toFloat(), 0x8CFFFFFF.toInt(), 1.4f)
            artCache.put(key, b)
            return b
        }
    }

    fun placeholder(a: Int, b: Int) { placeholderA = a; placeholderB = b; invalidate() }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val r = T.dpf(radiusDp)
        rect.set(0f, 0f, w, h)
        val bmp = (drawable as? BitmapDrawable)?.bitmap
        if (bmp != null) {
            if (bmp !== shaderFor || shaderW != width || shaderH != height) {
                val s = maxOf(w / bmp.width, h / bmp.height)
                m.reset(); m.setScale(s, s); m.postTranslate((w - bmp.width * s) / 2, (h - bmp.height * s) / 2)
                paint.shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) }
                shaderFor = bmp; shaderW = width; shaderH = height
            }
            paint.alpha = imageAlpha // view alpha (fade-in) is applied by the renderer; never bake it into the paint
        } else {
            paint.shader = LinearGradient(0f, 0f, w, h, placeholderA, placeholderB, Shader.TileMode.CLAMP)
            shaderFor = null
            paint.alpha = 255
        }
        c.drawRoundRect(rect, r, r, paint)
        val a = art
        if (bmp == null && a != null) {
            // illustrated cover: a soft disc and the icon, upper right, faint enough for text on top
            val s = minOf(w, h) * 0.62f
            val cx = w - s * 0.62f; val cy = h * 0.40f
            scrimPaint.shader = android.graphics.RadialGradient(cx, cy, s * 0.85f, 0x2EFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, s * 0.85f, scrimPaint)
            scrimPaint.shader = null
            c.drawBitmap(artBitmap(a, s.toInt()), cx - s / 2, cy - s / 2, null)
        }
        if (scrim > 0f) {
            // One pass over the whole rounded shape: the CLAMPed gradient is transparent outside the scrim zone.
            // (Drawing a round rect plus a filler rect doubled the alpha where they overlapped: a visible band.)
            val clear = scrimColor and 0x00FFFFFF
            scrimPaint.shader = if (scrimLeft) LinearGradient(0f, 0f, w * scrim, 0f, scrimColor, clear, Shader.TileMode.CLAMP)
            else LinearGradient(0f, h * (1 - scrim), 0f, h, clear, scrimColor, Shader.TileMode.CLAMP)
            c.drawRoundRect(rect, r, r, scrimPaint)
        }
    }
}
