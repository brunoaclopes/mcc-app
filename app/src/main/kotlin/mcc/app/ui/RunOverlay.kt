package mcc.app.ui

import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import mcc.app.MachineService
import mcc.app.i18n.L
import mcc.recipe.RecipeRunner
import mcc.recipe.Step

/**
 * Full-screen running view: appears whenever the motor or heater runs, with countdown digits that can be
 * read from across the kitchen, plus Pause (recipes) and a large STOP. It can be minimised for this run.
 */
class RunOverlay(private val app: AppUi) {
    private val c = app.ctx
    private val S get() = L.s
    val view: FrameLayout = FrameLayout(c).apply { visibility = View.GONE; isClickable = true }
    private val digits: GradientText
    private val mode: TextView
    private val detail: TextView
    private val bar: View
    private val barTrack: FrameLayout
    private val pauseBtn: HoldButton
    private var minimisedForRun = false
    private var wasRunning = false
    private var shown = false

    init {
        view.background = BackdropSlice(view)
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(T.dp(30), T.dp(10), T.dp(30), T.dp(20)) }
        val top = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        mode = T.text(c, "", 22f, T.TEXT, T.heavy)
        top.addView(mode, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(HoldButton(c).apply { label = ""; icon = Icon.DOWN; holdMillis = 0; style(T.SURFACE_3, T.SURFACE_3); onConfirm = { minimise() } },
            LinearLayout.LayoutParams(T.dp(64), T.dp(56)))
        col.addView(top, LinearLayout.LayoutParams(-1, -2))
        digits = GradientText(c, T.TEXT, T.TEAL).apply { textSize = 150f; typeface = T.heavy; fontFeatureSettings = "tnum"; includeFontPadding = false; gravity = Gravity.CENTER }
        col.addView(digits, LinearLayout.LayoutParams(-1, 0, 1f))
        detail = T.text(c, "", 20f, T.TEXT_2, T.bold).apply { gravity = Gravity.CENTER }
        col.addView(detail, LinearLayout.LayoutParams(-1, -2))
        barTrack = FrameLayout(c).apply { background = T.round(T.ink(0x24), 6) }
        bar = View(c).apply { background = T.gradient(T.TEAL, T.TEAL_DEEP, 6, android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT); pivotX = 0f }
        barTrack.addView(bar, FrameLayout.LayoutParams(-1, -1))
        col.addView(barTrack, LinearLayout.LayoutParams(-1, T.dp(12)).apply { setMargins(T.dp(40), T.dp(18), T.dp(40), T.dp(22)) })
        val buttons = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        pauseBtn = HoldButton(c).apply { label = S.pause; icon = Icon.PAUSE; holdMillis = 0; style(T.SURFACE_3, T.SURFACE_3); onConfirm = { app.service?.recipePause() } }
        buttons.addView(pauseBtn, LinearLayout.LayoutParams(T.dp(220), T.dp(72)).apply { rightMargin = T.dp(16) })
        buttons.addView(HoldButton(c).apply { label = S.stop; icon = Icon.STOP; holdMillis = 0; style(T.DANGER, T.DANGER_DEEP); onConfirm = { app.service?.stop() } },
            LinearLayout.LayoutParams(T.dp(320), T.dp(72)))
        col.addView(buttons)
        view.addView(col, FrameLayout.LayoutParams(-1, -1))
    }

    fun isShown() = shown

    fun minimise() { minimisedForRun = true; hide() }

    /** Shows the running view again after [minimise] (tap on the status pill). */
    fun restore() { minimisedForRun = false; render() }

    private fun show() {
        if (shown) return
        shown = true
        // shown again while still fading out: carry on from where it is instead of jumping back to transparent
        if (view.visibility != View.VISIBLE) { view.alpha = 0f; view.translationY = T.dpf(40f) }
        view.visibility = View.VISIBLE
        view.animate().alpha(1f).translationY(0f).setDuration(280).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun hide() {
        if (!shown) return
        shown = false
        view.animate().alpha(0f).translationY(T.dpf(30f)).setDuration(220).withEndAction { view.visibility = View.GONE }.start()
    }

    fun render() {
        val s = MachineService.snapshot
        val running = s?.running == true
        if (running && !wasRunning) minimisedForRun = false
        wasRunning = running
        // turbo is held from the Manual screen: covering the button would hide what the finger is doing
        if (!running || minimisedForRun || s?.request?.mode == mcc.machine.RunMode.TURBO) { hide(); return }
        show()
        val req = s!!.request ?: return
        val rs = MachineService.runnerState
        val step = rs?.step as? Step.Machine
        val hot = req.temperatureC > 0
        val left = if (rs?.phase == RecipeRunner.Phase.RUNNING && rs.remainingMillis != null) rs.remainingMillis!! else s.remainingMillis
        val watch = if (req.open && rs?.phase != RecipeRunner.Phase.RUNNING) MachineService.stopwatchMillis(s) else null
        digits.put(if (watch != null) fmtElapsed(watch) else fmtClock(left))
        digits.colors(T.TEXT, if (hot) T.AMBER else T.TEAL)
        mode.put(if (step != null) S.modeName(step.mode, step.segments.maxOf { it.tempC }, step.segments[0].speed, step.segments.size > 1) + " · " + (app.service?.recipe?.title ?: "")
            else S.running)
        val f = s.lastFrame
        detail.put(buildString {
            append(S.speedN(req.speedLevel))
            if (req.reverse) append(" · ").append(S.reverse.lowercase())
            if (hot) append(" · ").append(S.heatingTo(req.temperatureC).lowercase())
            if (f != null && f.temperatureStepC > 0) append(" · ").append(S.bowlTemp(f.temperatureStepC))
            rs?.heatingTo?.let { append(" · ").append(S.waitingForHeat(it)) }
            if (watch != null) append(" · ").append(S.stopwatch.lowercase(S.locale))
        })
        val total = if (step != null) mcc.recipe.MachinePlan.expand(step).sumOf { it.durationMillis } else req.durationMillis
        bar.scaleX = if (watch != null) (watch % 60_000) / 60_000f else (1f - left / total.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
        bar.background = if (hot) T.gradient(T.AMBER, T.EMBER_HOT, 6, android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT)
            .takeIf { bar.tag != "hot" }?.also { bar.tag = "hot" } ?: bar.background
        else if (bar.tag == "hot") T.gradient(T.TEAL, T.TEAL_DEEP, 6, android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT).also { bar.tag = null } else bar.background
        pauseBtn.visibility = if (rs?.phase == RecipeRunner.Phase.RUNNING) View.VISIBLE else View.GONE
    }
}
