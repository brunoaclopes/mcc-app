package mcc.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView

/** Design tokens. Warm dark theme: near-black surfaces, ember (heat) and teal (motion) accents. */
object T {
    // Colours are vars: [apply] swaps the whole palette for day or night before the UI is built.
    var dark = true; private set
    var BG = 0; var SURFACE = 0; var SURFACE_2 = 0; var SURFACE_3 = 0; var STROKE = 0
    var TEXT = 0; var TEXT_2 = 0; var TEXT_3 = 0
    var EMBER = 0; var EMBER_HOT = 0; var AMBER = 0; var TEAL = 0; var TEAL_DEEP = 0; var SKY = 0
    var OK = 0; var DANGER = 0; var DANGER_DEEP = 0
    /** Glass recipe (tint, sheen, rim) and the backdrop base. */
    var GLASS = 0; var GLASS_STRONG = 0; var SHEEN_TOP = 0; var SHEEN_BOTTOM = 0; var RIM_TOP = 0; var RIM_BOTTOM = 0
    var BACKDROP = 0; var SCRIM = 0; var DISC = 0; var BTN_GLASS = 0
    /** Faint "ink" for tracks, separators and idle segments: white at night, black by day. */
    var INK = 0
    /** End colour of the title gradient. */
    var TITLE_END = 0; var WEIGHT_END = 0; var COLD_END = 0
    /** Text and icons drawn on bright accent fills (always dark) and on photos / solid buttons (always light). */
    const val ON_ACCENT = 0xFF14110D.toInt()
    const val ON_PHOTO = 0xFFF7F4EF.toInt()
    const val ON_PHOTO_2 = 0xFFE6E0D6.toInt()

    init { apply(true) }

    fun apply(night: Boolean) {
        dark = night
        if (night) {
            BG = 0xFF0E0F11.toInt(); SURFACE = 0xFF17191C.toInt(); SURFACE_2 = 0xFF1F2226.toInt(); SURFACE_3 = 0xFF2A2E33.toInt()
            STROKE = 0xFF2C3036.toInt()
            TEXT = 0xFFF4F1EC.toInt(); TEXT_2 = 0xFFBDB8B0.toInt(); TEXT_3 = 0xFF8F8A84.toInt()
            EMBER = 0xFFFF7A1A.toInt(); EMBER_HOT = 0xFFFF3D2E.toInt(); AMBER = 0xFFFFB547.toInt()
            TEAL = 0xFF2ED3B7.toInt(); TEAL_DEEP = 0xFF1A9E8F.toInt(); SKY = 0xFF6FA8FF.toInt()
            OK = 0xFF46D17A.toInt(); DANGER = 0xFFFF4D4D.toInt(); DANGER_DEEP = 0xFFC62828.toInt()
            GLASS = 0x5E12151A; GLASS_STRONG = 0xE8151920.toInt(); SHEEN_TOP = 0x1CFFFFFF; SHEEN_BOTTOM = 0x05FFFFFF
            RIM_TOP = 0x40FFFFFF; RIM_BOTTOM = 0x0AFFFFFF
            BACKDROP = 0xFF0A0B0E.toInt(); SCRIM = 0xB0000000.toInt(); DISC = 0x80141820.toInt(); BTN_GLASS = 0x66181C22
            INK = 0xFFFFFFFF.toInt()
            TITLE_END = 0xFFFFC796.toInt(); WEIGHT_END = 0xFF8FF0DD.toInt(); COLD_END = 0xFF9DBBFF.toInt()
        } else {
            // Day: warm paper base, milky glass, deeper accents so they keep contrast on light surfaces.
            BG = 0xFFF3EEE7.toInt(); SURFACE = 0xFFFFFFFF.toInt(); SURFACE_2 = 0xFFF1ECE5.toInt(); SURFACE_3 = 0xFFE3DCD2.toInt()
            STROKE = 0xFFD9D1C6.toInt()
            TEXT = 0xFF1D1A16.toInt(); TEXT_2 = 0xFF4D4740.toInt(); TEXT_3 = 0xFF6F6860.toInt()
            EMBER = 0xFFEC6A0C.toInt(); EMBER_HOT = 0xFFE5392B.toInt(); AMBER = 0xFFE39612.toInt()
            TEAL = 0xFF12A88F.toInt(); TEAL_DEEP = 0xFF0B7F6E.toInt(); SKY = 0xFF3B7BE0.toInt()
            OK = 0xFF1F9D55.toInt(); DANGER = 0xFFE53935.toInt(); DANGER_DEEP = 0xFFB71C1C.toInt()
            GLASS = 0x9EFFFFFF.toInt(); GLASS_STRONG = 0xF4FBF8F4.toInt(); SHEEN_TOP = 0x66FFFFFF; SHEEN_BOTTOM = 0x10FFFFFF
            RIM_TOP = 0xE6FFFFFF.toInt(); RIM_BOTTOM = 0x1A6B5A48
            BACKDROP = 0xFFEFE8DF.toInt(); SCRIM = 0xA61E1710.toInt(); DISC = 0xB3FFFFFF.toInt(); BTN_GLASS = 0xB3FFFFFF.toInt()
            INK = 0xFF2A2117.toInt()
            TITLE_END = 0xFFD2620F.toInt(); WEIGHT_END = 0xFF0E8C78.toInt(); COLD_END = 0xFF3B6FD0.toInt()
        }
    }

    /** INK at the given alpha; day mode uses a little less, since dark-on-light reads stronger. */
    fun ink(a: Int) = withAlpha(INK, if (dark) a else (a * 0.6f).toInt())

    lateinit var regular: Typeface
    lateinit var semibold: Typeface
    lateinit var bold: Typeface
    lateinit var heavy: Typeface
    var density = 1f

    fun init(c: Context) {
        if (::regular.isInitialized) return
        density = c.resources.displayMetrics.density
        fun load(w: Int) = runCatching { Typeface.createFromAsset(c.assets, "fonts/manrope_$w.ttf") }.getOrDefault(Typeface.DEFAULT)
        regular = load(400); semibold = load(600); bold = load(700); heavy = load(800)
    }

    fun dp(v: Int) = (v * density + 0.5f).toInt()
    fun dpf(v: Float) = v * density

    fun round(color: Int, radiusDp: Int, strokeColor: Int = 0, strokeDp: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = dpf(radiusDp.toFloat())
        if (strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
    }

    fun gradient(c1: Int, c2: Int, radiusDp: Int, orientation: GradientDrawable.Orientation = GradientDrawable.Orientation.TL_BR) =
        GradientDrawable(orientation, intArrayOf(c1, c2)).apply { cornerRadius = dpf(radiusDp.toFloat()) }

    /** Background with a touch ripple. */
    fun pressable(bg: android.graphics.drawable.Drawable, ripple: Int = ink(0x33)): RippleDrawable {
        val mask = (bg as? GlassDrawable)?.let { GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = it.radiusPx } }
        return RippleDrawable(ColorStateList.valueOf(ripple), bg, mask)
    }

    /** Frosted glass panel. strong = denser tint for overlays (dialogs, sheets) that must stay readable. */
    fun glass(radiusDp: Int, strong: Boolean = false, accent: Int? = null) =
        if (strong) GlassDrawable(radiusDp, tint = GLASS_STRONG, sheenTop = if (dark) 0x22FFFFFF else 0x55FFFFFF, rimTop = if (dark) 0x50FFFFFF else RIM_TOP, accent = accent)
        else GlassDrawable(radiusDp, accent = accent)

    lateinit var backdrop: android.graphics.Bitmap

    fun text(c: Context, s: CharSequence = "", size: Float = 16f, color: Int = TEXT, face: Typeface = regular) = TextView(c).apply {
        text = s; textSize = size; setTextColor(color); typeface = face; includeFontPadding = false
    }

    fun lerpColor(a: Int, b: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        return Color.argb(
            (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * f).toInt(),
            (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt(),
        )
    }

    fun withAlpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a shl 24)

    /** Colour for a temperature: cool teal at 37 °C through amber to hot red at 130 °C. */
    fun tempColor(c: Int): Int = when {
        c <= 0 -> TEXT_3
        c < 70 -> lerpColor(SKY, AMBER, (c - 37) / 33f)
        else -> lerpColor(EMBER, EMBER_HOT, (c - 70) / 60f)
    }

    fun gradientText(v: TextView, c1: Int, c2: Int) {
        v.post {
            v.paint.shader = LinearGradient(0f, 0f, v.width.toFloat().coerceAtLeast(1f), 0f, c1, c2, Shader.TileMode.CLAMP)
            v.invalidate()
        }
    }

    fun press(v: View) {
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start()
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
            }
            false
        }
    }
}

/** Sets text only when it changed: avoids a re-layout on every 250 ms refresh. */
fun TextView.put(s: CharSequence) { if (text.toString() != s.toString()) text = s }
