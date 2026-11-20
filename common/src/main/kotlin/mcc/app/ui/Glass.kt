package mcc.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.TextView
import java.util.Random

/**
 * Frosted-glass panel without a live blur (too slow on this GPU): a dark translucent tint, a soft white
 * sheen from the top, and a light-catching gradient rim. Over the soft aurora backdrop this reads as glass.
 * Shaders are rebuilt only when the bounds change, so a redraw costs two round-rect fills and one stroke.
 */
class GlassDrawable(
    radiusDp: Int,
    private val tint: Int = T.GLASS,
    private val sheenTop: Int = T.SHEEN_TOP,
    private val sheenBottom: Int = T.SHEEN_BOTTOM,
    private val rimTop: Int = T.RIM_TOP,
    private val rimBottom: Int = T.RIM_BOTTOM,
    private val accent: Int? = null,
) : Drawable() {
    val radiusPx = T.dpf(radiusDp.toFloat())
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = T.dpf(1f) }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val rimRect = RectF()

    override fun onBoundsChange(b: Rect) {
        rect.set(b)
        val h = b.height().toFloat().coerceAtLeast(1f)
        fill.color = tint
        sheen.shader = LinearGradient(0f, b.top.toFloat(), 0f, b.top + h, sheenTop, sheenBottom, Shader.TileMode.CLAMP)
        rim.shader = LinearGradient(0f, b.top.toFloat(), 0f, b.top + h, rimTop, rimBottom, Shader.TileMode.CLAMP)
        val inset = rim.strokeWidth / 2
        rimRect.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset)
        accent?.let {
            glow.shader = RadialGradient(b.left + b.width() * 0.15f, b.top.toFloat(), b.width() * 0.9f,
                T.withAlpha(it, 0x30), T.withAlpha(it, 0), Shader.TileMode.CLAMP)
        }
    }

    override fun draw(c: Canvas) {
        c.drawRoundRect(rect, radiusPx, radiusPx, fill)
        if (accent != null) c.drawRoundRect(rect, radiusPx, radiusPx, glow)
        c.drawRoundRect(rect, radiusPx, radiusPx, sheen)
        c.drawRoundRect(rimRect, radiusPx, radiusPx, rim)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/**
 * The aurora backdrop: dark base, a few large soft colour glows and a fine film grain. Rendered once at
 * half resolution (it is all gradients, so upscaling is invisible) and used as the window background.
 */
object Backdrop {
    fun create(c: Context): Drawable {
        val dm = c.resources.displayMetrics
        val w = (dm.widthPixels / 2).coerceAtLeast(64)
        val h = (dm.heightPixels / 2).coerceAtLeast(64)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.drawColor(T.BACKDROP)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        fun blob(fx: Float, fy: Float, fr: Float, color: Int, a: Int) {
            val x = w * fx; val y = h * fy; val r = w * fr
            p.shader = RadialGradient(x, y, r, intArrayOf(T.withAlpha(color, a), T.withAlpha(color, a / 3), T.withAlpha(color, 0)),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            cv.drawCircle(x, y, r, p)
        }
        if (T.dark) {
            blob(0.12f, 0.02f, 0.62f, T.EMBER, 0x8C)       // warm light behind the logo/header
            blob(0.60f, 0.38f, 0.50f, 0xFF6B4DFF.toInt(), 0x58) // violet haze
            blob(0.96f, 1.02f, 0.62f, T.TEAL, 0x6A)         // teal from the bottom right
            blob(0.22f, 1.05f, 0.48f, T.SKY, 0x4C)          // cool blue bottom left
            blob(0.88f, 0.05f, 0.30f, T.AMBER, 0x22)
        } else {
            // daylight: soft peach sun top-left, mint and sky washes below, all pale so text stays crisp
            blob(0.10f, 0.00f, 0.70f, 0xFFFF9A4D.toInt(), 0x70)
            blob(0.62f, 0.35f, 0.50f, 0xFFB9A6FF.toInt(), 0x38)
            blob(0.98f, 1.04f, 0.62f, 0xFF5FE0C6.toInt(), 0x60)
            blob(0.20f, 1.06f, 0.50f, 0xFF8DB8FF.toInt(), 0x50)
            blob(0.88f, 0.04f, 0.32f, 0xFFFFD27A.toInt(), 0x50)
        }
        // film grain: breaks up gradient banding and gives the glass a frosted texture
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val rnd = Random(7)
        for (i in px.indices) {
            val n = if (T.dark) rnd.nextInt(9) - 4 else rnd.nextInt(7) - 3
            val col = px[i]
            val r = ((col shr 16 and 0xFF) + n).coerceIn(0, 255)
            val g = ((col shr 8 and 0xFF) + n).coerceIn(0, 255)
            val b = ((col and 0xFF) + n).coerceIn(0, 255)
            px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
        // 16-bit copy: half the memory bandwidth for the one layer drawn every frame (grain hides banding)
        val small = bmp.copy(Bitmap.Config.RGB_565, false)
        bmp.recycle()
        T.backdrop = small
        return BitmapDrawable(c.resources, small).apply { isFilterBitmap = true; setDither(false) }
    }
}

/**
 * Draws the part of the window backdrop that lies behind [owner], so full-screen overlays (recipe detail,
 * finished screen) look continuous with the window instead of a flat fill.
 */
class BackdropSlice(private val owner: android.view.View) : Drawable() {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()
    override fun draw(c: Canvas) {
        val bmp = T.backdrop
        // Layout position, not getLocationInWindow(): the latter includes running slide-in translations, and
        // the recorded drawing is not refreshed when the animation ends.
        var x = 0; var y = 0
        var v: android.view.View? = owner
        val root = owner.rootView
        while (v != null && v !== root) {
            x += v.left - v.scrollX; y += v.top - v.scrollY
            v = v.parent as? android.view.View
        }
        dst.set(-x.toFloat(), -y.toFloat(), root.width - x.toFloat(), root.height - y.toFloat())
        c.save(); c.clipRect(bounds)
        c.drawColor(T.BACKDROP)
        c.drawBitmap(bmp, null, dst, paint)
        c.restore()
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.OPAQUE
}

/** TextView with a horizontal gradient fill that follows its size (for titles and big numbers). */
class GradientText(context: Context, private var a: Int, private var b: Int) : TextView(context, null as AttributeSet?) {
    fun colors(c1: Int, c2: Int) { if (c1 != a || c2 != b) { a = c1; b = c2; applyShader() } }
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { super.onSizeChanged(w, h, ow, oh); applyShader() }
    override fun onTextChanged(text: CharSequence?, start: Int, before: Int, after: Int) { super.onTextChanged(text, start, before, after); applyShader() }
    private fun applyShader() {
        val tw = (paint.measureText(text?.toString() ?: "") + paddingLeft).coerceAtLeast(1f)
        paint.shader = LinearGradient(paddingLeft.toFloat(), 0f, tw, 0f, a, b, Shader.TileMode.CLAMP)
        invalidate()
    }
}
