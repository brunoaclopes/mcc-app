package mcc.phone.ui

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Rounded rectangle with continuous ("squircle") corners: the curve eases into the straight edge instead of meeting
 * it abruptly, like the corners of the phone itself. Only on Android 13+, where clipping to a path is anti-aliased;
 * older phones get ordinary rounded corners.
 */
fun smooth(radius: Dp): Shape = if (Build.VERSION.SDK_INT >= 33) SmoothShape(radius) else RoundedCornerShape(radius)

/** Corner smoothing as in design tools (0 = a plain circular corner, 0.6 = the usual "iOS" look). */
class SmoothShape(private val radius: Dp, private val smoothing: Float = 0.6f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radius.toPx() }
        if (r <= 0f || size.minDimension <= 0f) return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        return Outline.Generic(smoothRect(size.width, size.height, r, smoothing))
    }

    override fun equals(other: Any?) = other is SmoothShape && other.radius == radius && other.smoothing == smoothing
    override fun hashCode() = radius.hashCode() * 31 + smoothing.hashCode()
}

/**
 * The path: each corner is a circular arc of radius [r], shortened by [smoothing], with a cubic curve on each side
 * leading into the edges (the geometry Figma publishes for its corner smoothing).
 */
internal fun smoothRect(w: Float, h: Float, r0: Float, smoothing0: Float): Path {
    val maxP = min(w, h) / 2
    val r = min(r0, maxP)
    // not enough room for the full smoothing: use what fits
    val smoothing = min(smoothing0, (maxP / r - 1).coerceAtLeast(0f))
    val p = min((1 + smoothing) * r, maxP)
    val rad = Math.PI / 180
    val arcMeasure = 90 * (1 - smoothing)
    val arcSection = (sin(rad * arcMeasure / 2) * r * sqrt(2.0)).toFloat()
    val alpha = (90 - arcMeasure) / 2
    val p3p4 = (r * tan(rad * alpha / 2)).toFloat()
    val beta = 45 * smoothing
    val c = (p3p4 * cos(rad * beta)).toFloat()
    val d = (c * tan(rad * beta)).toFloat()
    val b = (p - arcSection - c - d) / 3
    val a = 2 * b
    val s = a + b + c
    val al = alpha.toFloat(); val sweep = arcMeasure.toFloat()
    return Path().apply {
        moveTo(p, 0f)
        // top right
        lineTo(w - p, 0f)
        cubicTo(w - p + a, 0f, w - p + a + b, 0f, w - p + s, d)
        arcTo(Rect(w - 2 * r, 0f, w, 2 * r), -90f + al, sweep, false)
        cubicTo(w, p - a - b, w, p - a, w, p)
        // bottom right
        lineTo(w, h - p)
        cubicTo(w, h - p + a, w, h - p + a + b, w - d, h - p + s)
        arcTo(Rect(w - 2 * r, h - 2 * r, w, h), al, sweep, false)
        cubicTo(w - p + a + b, h, w - p + a, h, w - p, h)
        // bottom left
        lineTo(p, h)
        cubicTo(p - a, h, p - a - b, h, p - s, h - d)
        arcTo(Rect(0f, h - 2 * r, 2 * r, h), 90f + al, sweep, false)
        cubicTo(0f, h - p + a + b, 0f, h - p + a, 0f, h - p)
        // top left
        lineTo(0f, p)
        cubicTo(0f, p - a, 0f, p - a - b, d, p - s)
        arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f + al, sweep, false)
        cubicTo(p - a - b, 0f, p - a, 0f, p, 0f)
        close()
    }
}
