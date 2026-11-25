package mcc.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Matrix
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Tick-ring dial (270° of ticks). Shows a set value; while the machine runs an inner ring shows progress.
 * Drag along the ring to set, tap the centre to select (the jog knob then adjusts the selected dial).
 */
class DialView(context: Context) : View(context) {
    /** Square-ish (no taller than wide), leaving [reserveBelow] px for the controls under it. */
    var reserveBelow = 0
    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        val avail = MeasureSpec.getSize(heightSpec) - reserveBelow
        val h = if (MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED) w else minOf(w, avail)
        setMeasuredDimension(w, h.coerceAtLeast(0))
    }
    var label = ""; set(v) { if (field != v) { field = v; invalidate() } }
    var value = ""; set(v) { if (field != v) { field = v; invalidate() } }
    var unit = ""; set(v) { if (field != v) { field = v; invalidate() } }
    var sub = ""; set(v) { if (field != v) { field = v; invalidate() } }
    var colorA = T.TEAL; set(v) { if (field != v) { field = v; invalidate() } }
    var colorB = T.TEAL_DEEP; set(v) { if (field != v) { field = v; invalidate() } }
    var active = false; set(v) { if (field != v) { field = v; invalidate() } }
    var interactive = true
    var onDrag: ((Float) -> Unit)? = null
    var onTap: (() -> Unit)? = null
    /** 0..1 inner progress ring, or null to hide. */
    var progress: Float? = null; set(v) { if (field != v) { field = v; invalidate() } }

    private var shown = 0f
    private var target = 0f
    private var anim: ValueAnimator? = null
    private val ticks = 54
    private val start = 135f
    private val sweep = 270f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private var dragging = false
    private var discSheen: Shader? = null
    private var discRim: Shader? = null
    private var discFor = -1f

    fun setFraction(f: Float, animate: Boolean = true) {
        val t = f.coerceIn(0f, 1f)
        if (t == target && anim?.isRunning != true) { if (!animate && shown != t) { shown = t; invalidate() }; return }
        target = t
        anim?.cancel()
        if (!animate || dragging) { shown = t; invalidate(); return }
        anim = ValueAnimator.ofFloat(shown, t).apply {
            duration = 260; interpolator = DecelerateInterpolator()
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f + T.dpf(6f)
        val r = minOf(width, height) / 2f - T.dpf(8f)
        val tickLen = r * 0.13f

        if (active) {
            paint.style = Paint.Style.FILL
            paint.shader = RadialGradient(cx, cy, r * 1.05f, T.withAlpha(colorA, 0x38), T.withAlpha(colorA, 0), Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r * 1.05f, paint)
            paint.shader = null
            // knob focus: a clear ring in the dial's colour
            paint.style = Paint.Style.STROKE; paint.strokeWidth = T.dpf(3f); paint.color = T.withAlpha(colorA, 0xCC)
            val rr = r - tickLen - T.dpf(2f)
            c.drawArc(RectF(cx - rr, cy - rr, cx + rr, cy + rr), 140f, 260f, false, paint) // open at the bottom, like the ticks, so the caption stays clear
            paint.style = Paint.Style.FILL
        }
        // disc: frosted glass (translucent tint + top sheen + light rim)
        val dr = r - tickLen - T.dpf(8f)
        paint.style = Paint.Style.FILL
        paint.color = T.DISC
        c.drawCircle(cx, cy, dr, paint)
        if (discSheen == null || discFor != dr) {
            discFor = dr
            discSheen = android.graphics.LinearGradient(0f, cy - dr, 0f, cy + dr, T.SHEEN_TOP, T.SHEEN_BOTTOM, Shader.TileMode.CLAMP)
            discRim = android.graphics.LinearGradient(0f, cy - dr, 0f, cy + dr, T.RIM_TOP, T.RIM_BOTTOM, Shader.TileMode.CLAMP)
        }
        paint.shader = discSheen; c.drawCircle(cx, cy, dr, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = T.dpf(1f); paint.shader = discRim
        c.drawCircle(cx, cy, dr - T.dpf(0.5f), paint)
        paint.shader = null

        // ticks
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = T.dpf(3f)
        val activeTicks = shown * ticks
        for (i in 0..ticks) {
            val a = Math.toRadians((start + sweep * i / ticks).toDouble())
            val on = i <= activeTicks && shown > 0f
            val major = i % 9 == 0
            val len = if (major) tickLen else tickLen * 0.7f
            paint.color = if (on) T.lerpColor(colorA, colorB, i / ticks.toFloat()) else T.withAlpha(T.TEXT_3, 0x70)
            paint.strokeWidth = T.dpf(if (on) 3.2f else 2.4f)
            val x1 = cx + (r - len) * cos(a).toFloat(); val y1 = cy + (r - len) * sin(a).toFloat()
            val x2 = cx + r * cos(a).toFloat(); val y2 = cy + r * sin(a).toFloat()
            c.drawLine(x1, y1, x2, y2, paint)
        }
        // knob dot
        if (shown > 0f) {
            val a = Math.toRadians((start + sweep * shown).toDouble())
            val kr = r - tickLen - T.dpf(16f)
            val kx = cx + kr * cos(a).toFloat(); val ky = cy + kr * sin(a).toFloat()
            paint.style = Paint.Style.FILL
            paint.shader = RadialGradient(kx, ky, T.dpf(14f), T.withAlpha(colorB, 0x90), T.withAlpha(colorB, 0), Shader.TileMode.CLAMP)
            c.drawCircle(kx, ky, T.dpf(14f), paint)
            paint.shader = null
            paint.color = T.TEXT
            c.drawCircle(kx, ky, T.dpf(4.5f), paint)
        }
        // progress ring
        progress?.let { p ->
            val pr = r - tickLen - T.dpf(8f)
            val rect = RectF(cx - pr, cy - pr, cx + pr, cy + pr)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = T.dpf(4f)
            paint.color = T.SURFACE_3
            c.drawArc(rect, 0f, 360f, false, paint)
            val sg = SweepGradient(cx, cy, intArrayOf(colorA, colorB, colorA), floatArrayOf(0f, 0.5f, 1f))
            sg.setLocalMatrix(Matrix().apply { setRotate(-90f, cx, cy) })
            paint.shader = sg
            c.drawArc(rect, -90f, 360f * p.coerceIn(0f, 1f), false, paint)
            paint.shader = null
        }
        // texts
        txt.typeface = T.semibold; txt.textSize = T.dpf(12.5f); txt.color = if (active) colorA else T.TEXT_2
        txt.letterSpacing = 0.12f
        c.drawText(label.uppercase(), cx, cy - r * 0.34f, txt)
        txt.letterSpacing = 0f
        txt.typeface = T.heavy; txt.color = T.TEXT
        txt.textSize = r * (if (value.length > 5) 0.34f else 0.42f)
        txt.fontFeatureSettings = "tnum"   // same-width digits: a running time doesn't jiggle
        c.drawText(value, cx, cy + r * 0.12f, txt)
        txt.fontFeatureSettings = null
        txt.typeface = T.semibold; txt.textSize = T.dpf(14f); txt.color = T.TEXT_2
        c.drawText(unit, cx, cy + r * 0.33f, txt)
        if (sub.isNotEmpty()) {
            txt.typeface = T.bold; txt.textSize = T.dpf(13f); txt.color = colorA
            c.drawText(sub, cx, cy + r - T.dpf(2f), txt) // in the open gap at the bottom of the ring, clear of the disc
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!interactive || !isEnabled) return false
        val cx = width / 2f; val cy = height / 2f + T.dpf(6f)
        val r = minOf(width, height) / 2f - T.dpf(8f)
        val d = hypot(e.x - cx, e.y - cy)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = d > r * 0.58f && d < r * 1.15f
                parent?.requestDisallowInterceptTouchEvent(true)
                if (dragging) dragTo(e.x - cx, e.y - cy)
                return true
            }
            MotionEvent.ACTION_MOVE -> if (dragging) dragTo(e.x - cx, e.y - cy)
            MotionEvent.ACTION_UP -> {
                if (!dragging) { performClick(); onTap?.invoke() } else onTap?.invoke()
                dragging = false
            }
            MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    private fun dragTo(dx: Float, dy: Float) {
        var deg = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        if (deg < 0) deg += 360f
        var rel = deg - start
        if (rel < 0) rel += 360f
        if (rel > sweep) rel = if (rel - sweep < (360f - sweep) / 2) sweep else 0f
        onDrag?.invoke(rel / sweep)
    }
}

/**
 * Press-and-hold button: fills while held and fires after [holdMillis]. A deliberate gesture for anything
 * that makes the machine move. With holdMillis = 0 it is an ordinary tap button.
 */
class HoldButton(context: Context) : View(context) {
    var label = ""; set(v) { if (field != v) { field = v; invalidate() } }
    var hint = ""; set(v) { if (field != v) { field = v; invalidate() } }
    var icon: Icon? = Icon.PLAY; set(v) { if (field != v) { field = v; invalidate() } }
    var colorA = T.EMBER; set(v) { if (field != v) { field = v; invalidate() } }
    var colorB = T.EMBER_HOT; set(v) { if (field != v) { field = v; invalidate() } }
    var holdMillis = 900L; set(v) { if (field != v) { field = v; invalidate() } }
    var onConfirm: (() -> Unit)? = null
    private var fill = 0f
    private var anim: ValueAnimator? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG)

    fun style(a: Int, b: Int) { colorA = a; colorB = b }

    override fun setEnabled(enabled: Boolean) { if (enabled != isEnabled) super.setEnabled(enabled) }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val rad = h / 2
        val rect = RectF(0f, 0f, w, h)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = if (isEnabled) T.withAlpha(colorA, 0x4A) else T.SURFACE_3
        val glassy = colorA == T.SURFACE_3 && colorB == T.SURFACE_3
        if (glassy) {
            paint.color = if (isEnabled) T.BTN_GLASS else T.withAlpha(T.BTN_GLASS, 0x40)
        } else if (holdMillis == 0L && isEnabled) {
            paint.color = android.graphics.Color.WHITE
            paint.shader = android.graphics.LinearGradient(0f, 0f, w, h, colorA, colorB, Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(rect, rad, rad, paint)
        if (isEnabled && (glassy || holdMillis == 0L)) {
            // glossy top sheen + light rim
            paint.color = android.graphics.Color.WHITE
            paint.shader = android.graphics.LinearGradient(0f, 0f, 0f, h, if (glassy) 0x1EFFFFFF else 0x38FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
            c.drawRoundRect(rect, rad, rad, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = T.dpf(1f)
            paint.shader = android.graphics.LinearGradient(0f, 0f, 0f, h, if (glassy) T.RIM_TOP else 0x60FFFFFF, if (glassy) T.RIM_BOTTOM else 0x08FFFFFF, Shader.TileMode.CLAMP)
            val i = T.dpf(0.5f)
            c.drawRoundRect(RectF(i, i, w - i, h - i), rad - i, rad - i, paint)
            paint.style = Paint.Style.FILL; paint.shader = null
        }
        if (isEnabled && holdMillis > 0L) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = T.dpf(1.5f); paint.color = T.withAlpha(colorA, 0xC0)
            val inset = T.dpf(0.75f)
            c.drawRoundRect(RectF(inset, inset, w - inset, h - inset), rad - inset, rad - inset, paint)
            paint.style = Paint.Style.FILL
        }
        if (fill > 0f && isEnabled) {
            paint.color = android.graphics.Color.WHITE
            paint.shader = android.graphics.LinearGradient(0f, 0f, w, 0f, colorA, colorB, Shader.TileMode.CLAMP)
            c.save()
            c.clipRect(0f, 0f, w * fill, h)
            c.drawRoundRect(rect, rad, rad, paint)
            c.restore()
            paint.shader = null
        }
        val tc = if (!isEnabled) T.TEXT_3 else if (holdMillis == 0L && !glassy) T.ON_PHOTO else T.TEXT
        txt.typeface = T.heavy; txt.textSize = minOf(h * 0.32f, T.dpf(24f)); txt.color = tc
        val tw = txt.measureText(label)
        val isz = txt.textSize * 1.05f
        val ic = icon
        val total = tw + if (ic != null) isz + (if (label.isEmpty()) 0f else T.dpf(10f)) else 0f
        var x = (w - total) / 2
        val baseY = if (hint.isEmpty()) h / 2 + txt.textSize * 0.36f else h / 2 + txt.textSize * 0.1f
        if (ic != null) { Icons.draw(c, ic, x, baseY - txt.textSize * 0.36f - isz / 2, isz, tc); x += isz + T.dpf(10f) }
        c.drawText(label, x, baseY, txt)
        if (hint.isNotEmpty()) {
            txt.typeface = T.semibold; txt.textSize = T.dpf(12f); txt.color = if (isEnabled) T.withAlpha(T.TEXT, 0xB0) else T.TEXT_3
            txt.textAlign = Paint.Align.CENTER
            c.drawText(hint, w / 2, h / 2 + T.dpf(20f), txt)
            txt.textAlign = Paint.Align.LEFT
        }
    }

    /**
     * What the current gesture started as. A hold that starts a run turns this button into STOP (holdMillis 0)
     * while the finger is still down; the release must not then count as a tap on STOP.
     */
    private var gestureIsTap = false
    private var gestureLive = false

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!isEnabled && e.actionMasked == MotionEvent.ACTION_DOWN) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                gestureLive = true
                gestureIsTap = holdMillis == 0L
                if (gestureIsTap) { animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).start(); return true }
                animateTo(1f, holdMillis) {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    gestureLive = false // this press is used up
                    onConfirm?.invoke()
                    animateTo(0f, 300)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val live = gestureLive
                gestureLive = false
                if (gestureIsTap) {
                    animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    if (live && isEnabled && e.actionMasked == MotionEvent.ACTION_UP) { performClick(); onConfirm?.invoke() }
                } else if (fill < 1f) animateTo(0f, 220)
            }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    private fun animateTo(t: Float, ms: Long, end: (() -> Unit)? = null) {
        anim?.cancel()
        anim = ValueAnimator.ofFloat(fill, t).apply {
            duration = (ms * kotlin.math.abs(t - fill)).toLong().coerceAtLeast(1)
            addUpdateListener { fill = it.animatedValue as Float; invalidate() }
            if (end != null) addListener(object : android.animation.AnimatorListenerAdapter() {
                var cancelled = false
                override fun onAnimationCancel(a: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(a: android.animation.Animator) { if (!cancelled) end() }
            })
            start()
        }
    }
}

/** Half-ring weighing gauge: fills towards the target, green when within 5 %. */
class WeightGauge(context: Context) : View(context) {
    var target = 0
    var ofText: (Int) -> String = { "/ $it g" }
    private var shown = 0f
    private var grams: Int? = null
    private var anim: ValueAnimator? = null
    private var reached = false
    private var glow = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    fun set(g: Int?, tgt: Int) {
        if (g == grams && tgt == target) return
        target = tgt; grams = g
        val v = (g ?: 0).toFloat()
        anim?.cancel()
        anim = ValueAnimator.ofFloat(shown, v).apply { duration = 300; addUpdateListener { shown = it.animatedValue as Float; invalidate() }; start() }
        val now = tgt > 0 && v >= tgt * 0.95f
        if (now && !reached) {
            // one-shot glow when the target is reached
            ValueAnimator.ofFloat(1f, 0f).apply { duration = 900; addUpdateListener { glow = it.animatedValue as Float; invalidate() }; start() }
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
        reached = now
    }

    override fun onDraw(c: Canvas) {
        val sw = T.dpf(16f)
        val r = minOf(width / 2f - sw, height * 0.72f - sw)
        val cx = width / 2f; val cy = height * 0.74f
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)
        paint.strokeWidth = sw
        paint.color = T.ink(0x24)
        c.drawArc(rect, 180f, 180f, false, paint)
        val max = (target * 1.25f).coerceAtLeast(1f)
        val f = (shown / max).coerceIn(0f, 1f)
        val ratio = if (target > 0) shown / target else 0f
        val col = when { ratio >= 0.95f && ratio <= 1.08f -> T.OK; ratio > 1.08f -> T.AMBER; else -> T.TEAL }
        if (glow > 0f) {
            paint.strokeWidth = sw + T.dpf(14f) * glow; paint.color = T.withAlpha(T.OK, (90 * glow).toInt())
            c.drawArc(rect, 180f, 180f, false, paint)
            paint.strokeWidth = sw
        }
        paint.color = col
        if (f > 0f) c.drawArc(rect, 180f, 180f * f, false, paint)
        // target marker sits on the arc
        val ta = Math.toRadians((180f + 180f * (target / max)).toDouble())
        paint.strokeWidth = T.dpf(3f); paint.color = T.TEXT
        c.drawLine(cx + (r - sw / 2 - T.dpf(2f)) * cos(ta).toFloat(), cy + (r - sw / 2 - T.dpf(2f)) * sin(ta).toFloat(),
            cx + (r + sw / 2 + T.dpf(2f)) * cos(ta).toFloat(), cy + (r + sw / 2 + T.dpf(2f)) * sin(ta).toFloat(), paint)
        val big = grams?.let { "${shown.toInt()}" } ?: "…"
        txt.typeface = T.heavy; txt.textSize = r * 0.4f; txt.color = T.TEXT
        txt.fontFeatureSettings = "tnum"
        val bw = txt.measureText(big)
        txt.textAlign = Paint.Align.LEFT
        val unitSize = r * 0.16f
        val total = bw + T.dpf(6f) + unitSize * 1.2f
        c.drawText(big, cx - total / 2, cy - r * 0.08f, txt)
        txt.fontFeatureSettings = null
        txt.typeface = T.bold; txt.textSize = unitSize; txt.color = T.TEXT_2
        c.drawText("g", cx - total / 2 + bw + T.dpf(6f), cy - r * 0.08f, txt)
        txt.textAlign = Paint.Align.CENTER
        txt.typeface = T.semibold; txt.textSize = T.dpf(15f); txt.color = T.TEXT_2
        c.drawText(ofText(target), cx, cy + T.dpf(12f), txt)
    }
}

/** Segmented recipe progress: done / current (pulsing) / upcoming. */
class StepBar(context: Context) : View(context) {
    var count = 1; set(v) { val n = v.coerceAtLeast(1); if (field != n) { field = n; invalidate() } }
    var current = 0; set(v) { if (field != v) { field = v; invalidate() } }
    var done = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pulse = ValueAnimator.ofFloat(0.45f, 1f).apply {
        duration = 900; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE
        addUpdateListener { invalidate() }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); syncAnim(pulse, isShown) }
    override fun onDetachedFromWindow() { pulse.cancel(); super.onDetachedFromWindow() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) { super.onVisibilityChanged(changedView, visibility); syncAnim(pulse, isShown) }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); syncAnim(pulse, isShown) }

    override fun onDraw(c: Canvas) {
        val full = height.toFloat()
        val thin = full * 0.6f
        val gap = if (count > 40) 0f else T.dpf(4f)
        val segW = (width - gap * (count - 1)) / count
        for (i in 0 until count) {
            val x = i * (segW + gap)
            val cur = i == current && !done
            val h = if (cur) full else thin
            val top = (full - h) / 2
            paint.color = when {
                cur -> T.lerpColor(T.EMBER, T.AMBER, 1f - (pulse.animatedValue as Float))
                done || i < current -> T.withAlpha(T.EMBER, 0x8C)
                else -> T.ink(0x1F)
            }
            c.drawRoundRect(RectF(x, top, x + segW, top + h), h / 2, h / 2, paint)
        }
    }
}

/** Small pulsing dot (live indicator). */
class Dot(context: Context) : View(context) {
    var color = T.OK; set(v) { if (field != v) { field = v; invalidate() } }
    /** Pulse animation; only runs while pulsing is on and the dot is actually on screen. */
    var pulsing = true; set(v) { if (field != v) { field = v; syncAnim(a, v && isShown); invalidate() } }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val a = ValueAnimator.ofFloat(0f, 1f).apply { duration = 1600; repeatCount = ValueAnimator.INFINITE; addUpdateListener { invalidate() } }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); syncAnim(a, pulsing && isShown) }
    override fun onDetachedFromWindow() { a.cancel(); super.onDetachedFromWindow() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) { super.onVisibilityChanged(changedView, visibility); syncAnim(a, pulsing && isShown) }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); syncAnim(a, pulsing && isShown) }
    override fun onDraw(c: Canvas) {
        val r = minOf(width, height) / 2f
        val t = a.animatedValue as Float
        if (pulsing && a.isRunning) { paint.color = T.withAlpha(color, ((1 - t) * 110).toInt()); c.drawCircle(width / 2f, height / 2f, r * (0.45f + 0.55f * t), paint) }
        paint.color = color
        c.drawCircle(width / 2f, height / 2f, r * 0.42f, paint)
    }
}

/** Runs an endless animator only while its view is visible, so hidden screens cost no frames. */
internal fun syncAnim(a: ValueAnimator, run: Boolean) {
    if (run && !a.isStarted) a.start() else if (!run && a.isStarted) a.cancel()
}

/** Animated on/off switch (track + sliding thumb). */
class SwitchView(context: Context) : View(context) {
    var checked = false
        private set
    var onChange: ((Boolean) -> Unit)? = null
    var accent = T.TEAL
    private var pos = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    init { setOnClickListener { set(!checked, true); onChange?.invoke(checked) } }
    fun set(v: Boolean, animate: Boolean = false) {
        checked = v
        if (!animate) { pos = if (v) 1f else 0f; invalidate(); return }
        ValueAnimator.ofFloat(pos, if (v) 1f else 0f).apply { duration = 180; addUpdateListener { pos = it.animatedValue as Float; invalidate() }; start() }
    }
    override fun onDraw(c: Canvas) {
        val h = height.toFloat(); val w = width.toFloat(); val r = h / 2
        paint.color = T.lerpColor(T.ink(0x33), accent, pos)
        c.drawRoundRect(RectF(0f, 0f, w, h), r, r, paint)
        paint.color = 0xFFFFFFFF.toInt()
        c.drawCircle(r + (w - 2 * r) * pos, r, r - T.dpf(4f), paint)
    }
}

/** One-shot confetti burst (about 1.8 s), used when a recipe is finished. Draws nothing when idle. */
class Celebration(context: Context) : View(context) {
    private class P(var x: Float, var y: Float, var vx: Float, var vy: Float, var rot: Float, val vr: Float, val color: Int, val w: Float, val h: Float)
    private val parts = ArrayList<P>()
    private var anim: ValueAnimator? = null
    private var last = 0L
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    init { visibility = GONE; isClickable = false }
    fun burst() {
        val rnd = java.util.Random()
        val cols = intArrayOf(T.EMBER, T.AMBER, T.TEAL, T.SKY, T.OK, 0xFFFF6FA8.toInt())
        parts.clear()
        repeat(70) {
            parts.add(P(width / 2f + (rnd.nextFloat() - 0.5f) * width * 0.3f, height * 0.55f,
                (rnd.nextFloat() - 0.5f) * T.dpf(900f), -T.dpf(500f + rnd.nextFloat() * 700f), rnd.nextFloat() * 360f,
                (rnd.nextFloat() - 0.5f) * 720f, cols[rnd.nextInt(cols.size)], T.dpf(6f + rnd.nextFloat() * 6f), T.dpf(3f + rnd.nextFloat() * 4f)))
        }
        visibility = VISIBLE
        last = 0L
        anim?.cancel()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800
            addUpdateListener { a ->
                val now = a.currentPlayTime
                val dt = ((now - last).coerceAtLeast(0) / 1000f).coerceAtMost(0.05f); last = now
                parts.forEach { p -> p.vy += T.dpf(1400f) * dt; p.x += p.vx * dt; p.y += p.vy * dt; p.rot += p.vr * dt }
                alpha = if (a.animatedFraction > 0.75f) (1f - a.animatedFraction) * 4f else 1f
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(an: android.animation.Animator) { visibility = GONE; parts.clear(); alpha = 1f }
            })
            start()
        }
    }
    override fun onDraw(c: Canvas) {
        parts.forEach { p ->
            paint.color = p.color
            c.save(); c.rotate(p.rot, p.x, p.y); c.drawRect(p.x - p.w / 2, p.y - p.h / 2, p.x + p.w / 2, p.y + p.h / 2, paint); c.restore()
        }
    }
}

/** Small reusable motion helpers (all transient: nothing here keeps running). */
object Anim {
    /** Counts an integer TextView from its current value to [to]. */
    fun count(tv: android.widget.TextView, from: Int, to: Int, fmt: (Int) -> CharSequence) {
        if (from == to) { tv.put(fmt(to)); return }
        ValueAnimator.ofInt(from, to).apply { duration = 420; interpolator = DecelerateInterpolator(); addUpdateListener { tv.put(fmt(it.animatedValue as Int)) }; start() }
    }
    /** Fades text out, swaps it, fades back in. */
    fun swapText(tv: android.widget.TextView, text: CharSequence) {
        if (tv.text.toString() == text.toString()) return
        tv.animate().cancel()
        tv.animate().alpha(0f).translationY(-T.dpf(6f)).setDuration(110).withEndAction {
            tv.text = text; tv.translationY = T.dpf(6f)
            tv.animate().alpha(1f).translationY(0f).setDuration(170).start()
        }.start()
    }
    /** Staggered entrance for a group of views. */
    fun stagger(views: List<View>, step: Long = 45, dy: Float = 14f) {
        views.forEachIndexed { i, v ->
            v.animate().cancel()
            v.alpha = 0f; v.translationY = T.dpf(dy)
            v.animate().alpha(1f).translationY(0f).setStartDelay(i * step).setDuration(260).setInterpolator(DecelerateInterpolator()).start()
        }
    }
    fun shake(v: View) {
        android.animation.ObjectAnimator.ofFloat(v, "translationX", 0f, -10f, 10f, -7f, 7f, -3f, 0f).apply { duration = 420; start() }
    }
    fun pop(v: View) {
        v.scaleX = 0.6f; v.scaleY = 0.6f; v.alpha = 0f
        v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(320).setInterpolator(android.view.animation.OvershootInterpolator(2.2f)).start()
    }
}

/**
 * Home status ring: a full clock face of 60 ticks filling clockwise from 12 o'clock (seconds when idle, progress
 * while running), with a big value and a small caption in a frosted disc. Display only.
 */
class ClockRing(context: Context) : View(context) {
    var value = ""; set(v) { if (field != v) { field = v; invalidate() } }
    var unit = ""; set(v) { if (field != v) { field = v; invalidate() } }
    var colorA = T.TEAL; set(v) { if (field != v) { field = v; invalidate() } }
    var colorB = T.TEAL_DEEP; set(v) { if (field != v) { field = v; invalidate() } }
    /** 0..1 */
    var fraction = 0f; set(v) { val t = v.coerceIn(0f, 1f); if (field != t) { field = t; invalidate() } }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private var sheen: Shader? = null
    private var rim: Shader? = null
    private var shaderFor = -1f

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f - T.dpf(4f)
        val tickLen = r * 0.12f

        val dr = r - tickLen - T.dpf(7f)
        paint.style = Paint.Style.FILL; paint.color = T.DISC
        c.drawCircle(cx, cy, dr, paint)
        if (shaderFor != dr) {
            shaderFor = dr
            sheen = android.graphics.LinearGradient(0f, cy - dr, 0f, cy + dr, T.SHEEN_TOP, T.SHEEN_BOTTOM, Shader.TileMode.CLAMP)
            rim = android.graphics.LinearGradient(0f, cy - dr, 0f, cy + dr, T.RIM_TOP, T.RIM_BOTTOM, Shader.TileMode.CLAMP)
        }
        paint.shader = sheen; c.drawCircle(cx, cy, dr, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = T.dpf(1f); paint.shader = rim
        c.drawCircle(cx, cy, dr - T.dpf(0.5f), paint)
        paint.shader = null

        val lit = (fraction * 60).toInt()
        for (i in 0 until 60) {
            val a = Math.toRadians(-90.0 + i * 6.0)
            val major = i % 5 == 0
            val on = i <= lit && fraction > 0f
            val len = if (major) tickLen else tickLen * 0.55f
            paint.color = when {
                i == lit && fraction > 0f -> colorB
                on -> T.lerpColor(colorA, colorB, i / 60f)
                else -> T.withAlpha(T.TEXT_3, if (major) 0x90 else 0x55)
            }
            paint.strokeWidth = T.dpf(if (i == lit && fraction > 0f) 4f else if (major) 3f else 2.2f)
            val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
            c.drawLine(cx + (r - len) * ca, cy + (r - len) * sa, cx + r * ca, cy + r * sa, paint)
        }

        txt.typeface = T.heavy; txt.color = T.TEXT; txt.letterSpacing = 0f
        txt.fontFeatureSettings = "tnum"
        txt.textSize = dr * (if (value.length > 5) 0.5f else 0.6f)
        c.drawText(value, cx, cy + txt.textSize * 0.3f, txt)
        if (unit.isNotEmpty()) {
            val big = txt.textSize
            txt.fontFeatureSettings = null
            txt.typeface = T.semibold; txt.textSize = T.dpf(13f); txt.color = T.TEXT_2
            c.drawText(unit, cx, cy + big * 0.3f + T.dpf(20f), txt)
        }
    }
}
