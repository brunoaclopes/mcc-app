package mcc.phone.ui

import android.app.Activity
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import mcc.link.MachineState
import mcc.link.Status
import kotlin.math.cos
import kotlin.math.sin

/** The machine's mood as a colour: teal while mixing, ember while heating, green when done, red on errors. */
fun mood(s: MachineState?, online: Boolean): Color = when {
    !online || s == null -> Mc.text3
    s.hasError -> Mc.danger
    s.running && s.targetTemperature > 0 -> Mc.ember
    s.running -> Mc.teal
    s.status == Status.PAUSED -> Mc.amber
    s.status == Status.FINISHED -> Mc.ok
    else -> Mc.amber
}

/**
 * Slowly drifting warm light behind every screen (the phone take on the tablet's glass backdrop).
 * Three soft blobs; their colour follows the machine.
 */
@Composable
fun Ambient(color: Color, modifier: Modifier = Modifier.fillMaxSize()) {
    val c by animateColorAsState(color, tween(1200), label = "ambient")
    val t = rememberInfiniteTransition(label = "drift")
    val a by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(26_000, easing = LinearEasing)), label = "a")
    val base = Mc.bg
    val warm = Mc.backdrop
    val dark = mcc.app.ui.T.dark
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(warm, base)))
        val w = size.width; val h = size.height; val r = maxOf(w, h)
        fun blob(cx: Float, cy: Float, rad: Float, col: Color, alpha: Float) =
            drawCircle(Brush.radialGradient(listOf(col.copy(alpha = alpha), Color.Transparent), Offset(cx, cy), rad), rad, Offset(cx, cy))
        val k = if (dark) 0.22f else 0.30f
        blob(w * (0.15f + 0.1f * cos(a)), h * (0.12f + 0.06f * sin(a)), r * 0.55f, c, k)
        blob(w * (0.9f + 0.08f * sin(a * 2)), h * (0.45f + 0.08f * cos(a)), r * 0.5f, Mc.amber, k * 0.6f)
        blob(w * (0.4f + 0.15f * sin(a)), h * (1.0f + 0.05f * cos(a * 2)), r * 0.6f, c, k * 0.7f)
    }
}

/** Text that rolls when it changes (temperature, weight, time). */
@Composable
fun Rolling(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    AnimatedContent(text, modifier, transitionSpec = {
        val up = (targetState.filter(Char::isDigit).toIntOrNull() ?: 0) >= (initialState.filter(Char::isDigit).toIntOrNull() ?: 0)
        (slideInVertically { if (up) it / 2 else -it / 2 } + fadeIn(tween(220))) togetherWith (slideOutVertically { if (up) -it / 2 else it / 2 } + fadeOut(tween(160)))
    }, label = "roll") { Text(it, style = style, color = color, maxLines = 1) }
}

/** 270° arc gauge. */
@Composable
fun Gauge(fraction: Float, color: Color, modifier: Modifier, stroke: Dp = 10.dp, track: Color = Mc.surface3) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700, easing = FastOutSlowInEasing), label = "gauge")
    Canvas(modifier) {
        val sw = stroke.toPx()
        val tl = Offset(sw / 2, sw / 2); val sz = Size(size.minDimension - sw, size.minDimension - sw)
        drawArc(track, 135f, 270f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
        if (f > 0.005f) drawArc(Brush.sweepGradient(listOf(color.copy(alpha = 0.55f), color, color)), 135f, 270f * f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
    }
}

/** Soft breathing glow around the current step. */
fun Modifier.pulse(color: Color, on: Boolean): Modifier = composed {
    if (!on) return@composed this
    val t = rememberInfiniteTransition(label = "pulse")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "p")
    drawBehind {
        val grow = 6.dp.toPx() * p
        drawRoundRect(color.copy(alpha = 0.18f * (1 - p) + 0.06f), Offset(-grow, -grow), Size(size.width + grow * 2, size.height + grow * 2),
            androidx.compose.ui.geometry.CornerRadius(24.dp.toPx() + grow))
    }
}

/** A short, crisp tick for confirmations (Stop, Pause, Tare, ticking items). */
@Composable
fun rememberHaptic(): (strong: Boolean) -> Unit {
    val v = LocalView.current
    return remember(v) {
        { strong: Boolean ->
            v.performHapticFeedback(when {
                Build.VERSION.SDK_INT >= 30 && strong -> HapticFeedbackConstants.CONFIRM
                Build.VERSION.SDK_INT >= 30 -> HapticFeedbackConstants.GESTURE_START
                else -> HapticFeedbackConstants.VIRTUAL_KEY
            })
        }
    }
}

/**
 * Fold posture: true when the phone stands half-open like a laptop (Fold "Flex mode"): the screen is split by a
 * horizontal hinge. The machine screen then shows the status on top and the controls on the bottom half.
 */
@Composable
fun rememberTabletop(): Boolean {
    val act = LocalContext.current as? Activity ?: return false
    var tabletop by remember { mutableStateOf(false) }
    LaunchedEffect(act) {
        WindowInfoTracker.getOrCreate(act).windowLayoutInfo(act).collect { info ->
            tabletop = info.displayFeatures.filterIsInstance<FoldingFeature>().any {
                it.state == FoldingFeature.State.HALF_OPENED && it.orientation == FoldingFeature.Orientation.HORIZONTAL
            }
        }
    }
    return tabletop
}

/** Morning / afternoon / evening, for the header. */
fun greeting(): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val S = mcc.phone.P.s
    return when (h) { in 5..11 -> S.greetMorning; in 12..19 -> S.greetAfternoon; else -> S.greetEvening }
}
