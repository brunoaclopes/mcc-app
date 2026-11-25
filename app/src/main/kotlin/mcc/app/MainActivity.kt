package mcc.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import mcc.app.i18n.L
import mcc.app.ui.Anim
import mcc.app.ui.AppUi
import mcc.app.ui.Backdrop
import mcc.app.ui.Celebration
import mcc.app.ui.CookScreen
import mcc.app.ui.Dot
import mcc.app.ui.GradientText
import mcc.app.ui.HoldButton
import mcc.app.ui.HomeScreen
import mcc.app.ui.Icon
import mcc.app.ui.IconView
import mcc.app.ui.ManualScreen
import mcc.app.ui.Notice
import mcc.app.ui.RecipesScreen
import mcc.app.ui.RunOverlay
import mcc.app.ui.Screen
import mcc.app.ui.SettingsScreen
import mcc.app.ui.T
import mcc.app.ui.put
import mcc.machine.StopReason
import mcc.recipe.Recipe
import mcc.recipe.RecipeIndex
import mcc.recipe.RecipeRunner
import java.util.Calendar

/**
 * Shell: navigation rail, live status bar with an always-visible STOP, screen host, running view, toasts and
 * dialogs. All machine logic lives in [MachineService]; screens only call its command methods.
 */
class MainActivity : Activity(), AppUi {
    override val ctx: Context get() = this
    override lateinit var store: RecipeStore
    override lateinit var prefs: Prefs
    override val service: MachineService? get() = MachineService.instance
    override var index: RecipeIndex? = null

    private val S get() = L.s
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable { override fun run() { render(); handler.postDelayed(this, 250) } }
    private var lastThemeCheck = 0L

    companion object {
        private var helloPlayed = false
        /** Picture of the previous UI, handed across recreate() for the cross-fade. */
        private var fadeFrom: android.graphics.Bitmap? = null
    }
    private lateinit var root: FrameLayout
    private lateinit var glow: View
    private var glowColor = 0
    private lateinit var content: FrameLayout
    private lateinit var titleView: GradientText
    private lateinit var subtitleView: TextView
    private val order = listOf("home", "programs", "manual", "recipes", "cook", "settings")
    private val screens = LinkedHashMap<String, Screen>()
    private val railItems = HashMap<String, Pair<LinearLayout, IconView>>()
    private lateinit var railIndicator: View
    private var current = "home"
    private lateinit var runOverlay: RunOverlay
    private lateinit var celebration: Celebration

    private lateinit var linkDot: Dot
    private lateinit var linkText: TextView
    private lateinit var lidChip: LinearLayout
    private lateinit var weightChip: LinearLayout
    private lateinit var tempChip: LinearLayout
    private lateinit var lidIcon: IconView
    private lateinit var lidText: TextView
    private lateinit var weightText: TextView
    private lateinit var tempText: TextView
    private lateinit var tempIcon: IconView
    private lateinit var lockBtn: LinearLayout
    private lateinit var lockIcon: IconView
    private lateinit var lockText: TextView
    private lateinit var stopBtn: HoldButton
    private var stopActive = false
    private var shownStopActive: Boolean? = null

    private var toast: TextView? = null
    private var sounds: Sounds? = null
    private var lastTick = -1L
    private var wasWeighed = false
    private var lastPhase: RecipeRunner.Phase? = null
    private var wasRunning = false
    private var lastArmed: Boolean? = null
    private var lastLid: Boolean? = null
    private var shownWeight: Int? = null
    private var closeDialog: (() -> Unit)? = null
    private val dialogOpen get() = closeDialog != null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        T.init(this)
        prefs = Prefs(this)
        L.set(prefs.uiLanguage)
        Sun.where = prefs.latLon
        T.apply(night = wantNight())
        store = RecipeStore(this) { prefs.repoUrl }
        sounds = Sounds { prefs.soundLevel }
        if (!helloPlayed) { helloPlayed = true; play(Sounds.Cue.HELLO) } // once per app start, not on theme or language rebuilds
        startService(Intent(this, MachineService::class.java))

        screens["home"] = HomeScreen(this)
        screens["programs"] = mcc.app.ui.ProgramsScreen(this)
        screens["manual"] = ManualScreen(this)
        screens["recipes"] = RecipesScreen(this)
        screens["cook"] = CookScreen(this)
        screens["settings"] = SettingsScreen(this)
        screens["soup"] = mcc.app.ui.SoupScreen(this) // opened from Programs; not on the rail

        window.setBackgroundDrawable(Backdrop.create(this)) // aurora, rendered once; the only full-screen layer
        root = FrameLayout(this).apply { isFocusableInTouchMode = true } // takes initial focus so no text field grabs it
        glow = View(this).apply { alpha = 0f }
        root.addView(glow, FrameLayout.LayoutParams(-1, -1))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        // static chrome is cached as GPU textures, so scrolling only redraws the content area
        row.addView(rail().apply { setLayerType(View.LAYER_TYPE_HARDWARE, null) },
            LinearLayout.LayoutParams(T.dp(84), -1).apply { setMargins(T.dp(10), T.dp(10), 0, T.dp(10)) })
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(topBar().apply { setLayerType(View.LAYER_TYPE_HARDWARE, null) }, LinearLayout.LayoutParams(-1, T.dp(72)))
        content = FrameLayout(this)
        screens.values.forEach { s -> content.addView(s.view, FrameLayout.LayoutParams(-1, -1)); s.view.visibility = View.GONE }
        runOverlay = RunOverlay(this)
        content.addView(runOverlay.view, FrameLayout.LayoutParams(-1, -1))
        col.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        row.addView(col, LinearLayout.LayoutParams(0, -1, 1f))
        root.addView(row)
        celebration = Celebration(this)
        root.addView(celebration, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        root.requestFocus()
        // after a day/night or language switch: the old UI's picture fades out over the new one
        fadeFrom?.let { bmp ->
            fadeFrom = null
            val shot = android.widget.ImageView(this).apply { setImageBitmap(bmp); scaleType = android.widget.ImageView.ScaleType.FIT_XY }
            root.addView(shot, FrameLayout.LayoutParams(-1, -1))
            shot.animate().alpha(0f).setStartDelay(80).setDuration(450).withEndAction { root.removeView(shot); bmp.recycle() }.start()
        }
        // the keyboard (or a swipe) brings the system bars back; hide them again a moment later
        @Suppress("DEPRECATION")
        window.decorView.setOnSystemUiVisibilityChangeListener { vis ->
            if (vis and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION == 0) handler.postDelayed({ immersive() }, 2500)
        }
        reloadIndex(refresh = true)   // first, so opening the first screen doesn't ask the server a second time
        go(savedInstanceState?.getString("screen") ?: "home", animate = false)
    }

    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); out.putString("screen", current) }
    override fun onResume() { super.onResume(); immersive(); lastUse = SystemClock.elapsedRealtime(); handler.post(tick) }
    override fun onPause() { handler.removeCallbacks(tick); screens[current]?.onHide(); super.onPause() }
    override fun onDestroy() { sounds?.release(); super.onDestroy() }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) immersive() }

    /** Full screen like an appliance; swipe up from the bottom edge to reach the system navigation. */
    private fun immersive() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }

    // ------------------------------------------------------------------ layout

    private fun rail(): View {
        val rail = FrameLayout(this).apply { background = T.glass(28) }
        railIndicator = View(this).apply { background = T.gradient(T.withAlpha(T.EMBER, 0x5A), T.withAlpha(T.AMBER, 0x12), 20) }
        rail.addView(railIndicator, FrameLayout.LayoutParams(T.dp(70), T.dp(62), Gravity.CENTER_HORIZONTAL))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, T.dp(12), 0, T.dp(10)) }
        val logo = FrameLayout(this).apply { background = T.gradient(T.AMBER, T.EMBER_HOT, 16) }
        logo.addView(IconView(this, Icon.CHEF, T.ON_ACCENT, 2.2f), FrameLayout.LayoutParams(T.dp(28), T.dp(28), Gravity.CENTER))
        col.addView(logo, LinearLayout.LayoutParams(T.dp(48), T.dp(48)).apply { bottomMargin = T.dp(10) })
        val labels = listOf(S.navHome, S.navPrograms, S.navManual, S.navRecipes, S.navCook, S.navSettings)
        val icons = listOf(Icon.HOME, Icon.GRID, Icon.DIAL, Icon.BOOK, Icon.POT, Icon.GEAR)
        order.forEachIndexed { i, key ->
            val item = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setOnClickListener { go(key) } }
            val icon = IconView(this, icons[i], T.TEXT_2)
            item.addView(icon, LinearLayout.LayoutParams(T.dp(26), T.dp(26)))
            item.addView(T.text(this, labels[i], 12.5f, T.TEXT_2, T.semibold).apply {
                gravity = Gravity.CENTER; setPadding(0, T.dp(5), 0, 0); maxLines = 1
            }, LinearLayout.LayoutParams(-1, -2))
            T.press(item)
            col.addView(item, LinearLayout.LayoutParams(T.dp(80), T.dp(62)).apply { bottomMargin = T.dp(2) })
            railItems[key] = item to icon
        }
        rail.addView(col, FrameLayout.LayoutParams(-1, -1))
        // quick day/night toggle at the foot of the rail (switches to a fixed look; Settings has Auto)
        val mode = FrameLayout(this).apply {
            background = T.pressable(T.glass(22))
            setOnClickListener { setTheme(if (T.dark) "day" else "night") }
        }
        mode.addView(IconView(this, if (T.dark) Icon.SUN else Icon.MOON, T.TEXT_2), FrameLayout.LayoutParams(T.dp(22), T.dp(22), Gravity.CENTER))
        T.press(mode)
        rail.addView(mode, FrameLayout.LayoutParams(T.dp(48), T.dp(48), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = T.dp(12) })
        return rail
    }

    private fun chip(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        background = T.glass(22); setPadding(T.dp(10), 0, T.dp(12), 0)
    }

    private fun topBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(T.dp(18), T.dp(10), T.dp(14), T.dp(6))
        }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleView = GradientText(this, T.TEXT, T.TITLE_END).apply {
            textSize = 26f; typeface = T.heavy; includeFontPadding = false; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        subtitleView = T.text(this, "", 12.5f, T.TEXT_2, T.semibold).apply { setPadding(0, T.dp(2), 0, 0); maxLines = 2; setLineSpacing(0f, 0.95f); ellipsize = android.text.TextUtils.TruncateAt.END }
        titles.addView(titleView); titles.addView(subtitleView)
        bar.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        val lp = { LinearLayout.LayoutParams(-2, T.dp(44)).apply { leftMargin = T.dp(6) } }

        val link = chip()
        linkDot = Dot(this); link.addView(linkDot, LinearLayout.LayoutParams(T.dp(14), T.dp(14)))
        linkText = T.text(this, "", 14f, T.TEXT, T.bold).apply { setPadding(T.dp(8), 0, 0, 0) }
        link.addView(linkText); bar.addView(link, lp())
        // while something runs, the status pill brings back the big running view after it was minimised
        link.background = T.pressable(link.background)
        link.setOnClickListener { if (MachineService.snapshot?.running == true) runOverlay.restore() }

        lidChip = chip()
        lidIcon = IconView(this, Icon.LID, T.TEXT_2); lidChip.addView(lidIcon, LinearLayout.LayoutParams(T.dp(20), T.dp(20)))
        lidText = T.text(this, "", 14f, T.TEXT, T.bold).apply { setPadding(T.dp(7), 0, 0, 0) }
        lidChip.addView(lidText); bar.addView(lidChip, lp())

        val w = chip(); weightChip = w
        w.addView(IconView(this, Icon.SCALE, T.TEAL), LinearLayout.LayoutParams(T.dp(20), T.dp(20)))
        weightText = T.text(this, "", 14f, T.TEXT, T.bold).apply {
            text = "— g"; setPadding(T.dp(7), 0, 0, 0); fontFeatureSettings = "tnum"; gravity = Gravity.END
            minWidth = (paint.measureText("8888 g") + T.dp(7)).toInt() // the chip keeps its width as the number changes
        }
        w.addView(weightText); bar.addView(w, lp())

        val t = chip(); tempChip = t
        tempIcon = IconView(this, Icon.THERMO, T.AMBER); t.addView(tempIcon, LinearLayout.LayoutParams(T.dp(20), T.dp(20)))
        tempText = T.text(this, "", 14f, T.TEXT, T.bold).apply {
            setPadding(T.dp(5), 0, 0, 0); fontFeatureSettings = "tnum"; minWidth = (paint.measureText("≤37°") + T.dp(5)).toInt()
        }
        t.addView(tempText); bar.addView(t, lp())

        // The lock is a real button (50 dp): amber "Unlock" while locked, solid teal while unlocked.
        lockBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(T.dp(14), 0, T.dp(16), 0)
            setOnClickListener { toggleLock() }
        }
        lockIcon = IconView(this, Icon.LOCK, T.AMBER, 2.2f); lockBtn.addView(lockIcon, LinearLayout.LayoutParams(T.dp(20), T.dp(20)))
        lockText = T.text(this, "", 15f, T.TEXT, T.heavy).apply { setPadding(T.dp(8), 0, 0, 0) }
        lockBtn.addView(lockText); T.press(lockBtn)
        bar.addView(lockBtn, LinearLayout.LayoutParams(-2, T.dp(50)).apply { leftMargin = T.dp(10) })

        // STOP always works, but only looks urgent when something is running or paused
        stopBtn = HoldButton(this).apply {
            label = S.stop; icon = Icon.STOP; holdMillis = 0; style(T.SURFACE_3, T.SURFACE_3)
            onConfirm = { val active = stopActive; service?.stop(); if (active) notify(S.stopped, Notice.WARN) }
        }
        bar.addView(stopBtn, LinearLayout.LayoutParams(T.dp(136), T.dp(54)).apply { leftMargin = T.dp(10) })
        return bar
    }

    // ------------------------------------------------------------------ navigation & rendering

    override fun go(screen: String) = go(screen, animate = true)

    private fun go(screen: String, animate: Boolean) {
        val s = screens[screen] ?: return
        hideKeyboard()
        val old = screens[current]
        val dir = if (order.indexOf(screen) >= order.indexOf(current)) 1 else -1
        if (old != null && old !== s) {
            old.onHide()
            if (animate) old.view.animate().alpha(0f).translationY(-dir * T.dpf(12f)).setDuration(140)
                .withEndAction { old.view.visibility = View.GONE; old.view.translationY = 0f; old.view.alpha = 1f }.start()
            else old.view.visibility = View.GONE
        }
        current = screen
        if (screen == "recipes" || screen == "home") revalidateIndex()
        val wasLeaving = s.view.visibility == View.VISIBLE   // back to a screen that is still fading out
        s.view.visibility = View.VISIBLE
        // Home shows weight and temperature in big tiles already
        val chips = if (screen == "home") View.GONE else View.VISIBLE
        weightChip.visibility = chips; tempChip.visibility = chips
        if (animate) {
            if (!wasLeaving) { s.view.alpha = 0f; s.view.translationY = dir * T.dpf(18f) }
            s.view.animate().alpha(1f).translationY(0f).setStartDelay(60).setDuration(240).setInterpolator(DecelerateInterpolator()).start()
        }
        s.onShow()
        // the selection pill slides to the new item
        railItems[screen]?.first?.let { item ->
            item.post {
                val y = (item.parent as View).top + item.top.toFloat()
                if (animate) railIndicator.animate().translationY(y).setDuration(260).setInterpolator(DecelerateInterpolator()).start()
                else railIndicator.translationY = y
            }
        }
        railItems.forEach { (k, p) ->
            val sel = k == screen
            p.second.set(p.second.icon, if (sel) T.EMBER else T.TEXT_2)
            (p.first.getChildAt(1) as TextView).setTextColor(if (sel) T.TEXT else T.TEXT_2)
        }
        render()
    }

    private var remoteSeen = MachineService.remoteLoaded?.first ?: 0
    private var refreshSeen = MachineService.recipesRefreshed

    private fun render() {
        if (MachineService.recipesRefreshed != refreshSeen) { refreshSeen = MachineService.recipesRefreshed; reloadIndex(false) }
        MachineService.remoteLoaded?.let { (n, title) ->
            if (n != remoteSeen) {
                remoteSeen = n
                lastTouch = android.os.SystemClock.elapsedRealtime()
                go("cook")
                notify(S.remoteLoaded(title), Notice.INFO)
                play(Sounds.Cue.STEP)
                val r = service?.recipe
                val heats = r?.steps?.any { st -> st is mcc.recipe.Step.Machine && st.segments.any { it.tempC > 0 } } == true
                if (heats && MachineService.snapshot?.hasEmptyReference == false) askEmptyBowl(S.emptyBowlNeeded)
            }
        }
        val s = screens[current] ?: return
        titleView.put(s.title)
        subtitleView.put(s.subtitle.ifEmpty { "${S.greeting(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))} · ${S.brand}" })
        val snap = MachineService.snapshot
        val f = snap?.lastFrame
        val connected = snap?.connected == true
        linkDot.color = when { !connected -> T.DANGER; snap?.running == true -> T.EMBER; else -> T.OK }
        linkDot.pulsing = snap?.running == true
        linkText.put(when { !connected -> if (MachineService.status.startsWith("BLOCKED")) S.stockApp else S.offline; snap?.running == true -> S.running; MachineService.pausedRun != null -> S.paused; else -> S.ready })
        val lid = f?.lidOpen
        lidText.put(if (lid == null) "—" else if (lid) S.lidShortOpen else S.lidShortClosed)
        lidIcon.set(Icon.LID, if (lid == true) T.AMBER else T.TEXT_2)
        lidText.setTextColor(if (lid == true) T.AMBER else T.TEXT)
        if (lid == true && lastLid == false) { Anim.shake(lidChip); play(Sounds.Cue.LID_OPEN) }
        if (lid == false && lastLid == true) play(Sounds.Cue.LID_CLOSED)
        lastLid = lid
        val g = MachineService.displayGrams
        if (g != shownWeight) {
            val from = shownWeight
            shownWeight = g
            if (g == null || from == null) weightText.put(g?.let { "$it g" } ?: "— g") else Anim.count(weightText, from, g) { "$it g" }
        }
        val tc = f?.temperatureStepC ?: 0
        tempText.put(if (f == null) "—" else if (tc == 0) small("≤", "37°") else "$tc°")
        tempIcon.set(Icon.THERMO, if (tc == 0) T.TEXT_2 else T.tempColor(tc))
        stopActive = snap?.running == true || MachineService.runnerState?.phase == RecipeRunner.Phase.PAUSED || MachineService.pausedRun != null
        if (stopActive != shownStopActive) {
            if (stopActive) stopBtn.style(T.DANGER, T.DANGER_DEEP) else stopBtn.style(T.SURFACE_3, T.SURFACE_3)
            shownStopActive = stopActive
        }
        val armed = MachineService.armed
        if (armed != lastArmed) {
            lockBtn.background = if (armed) T.pressable(T.gradient(T.TEAL, T.TEAL_DEEP, 25))
            else T.pressable(T.round(T.withAlpha(T.AMBER, 0x1E), 25, T.withAlpha(T.AMBER, 0x99), 2))
            lockIcon.set(if (armed) Icon.UNLOCK else Icon.LOCK, if (armed) T.ON_PHOTO else T.AMBER)
            lockText.text = if (armed) S.unlocked else S.unlock
            lockText.setTextColor(if (armed) T.ON_PHOTO else T.TEXT)
            if (lastArmed != null) Anim.pop(lockIcon)
            lastArmed = armed
        }
        ambient(snap)
        autoTheme()
        standbyTick()
        runOverlay.render()
        s.render()
        sounds()
    }

    private fun small(prefix: String, rest: String): CharSequence =
        SpannableString(prefix + rest).apply { setSpan(RelativeSizeSpan(0.75f), 0, prefix.length, 0) }

    /**
     * Ambient light behind the whole UI: warm while heating, teal while the motor runs. It fades only on a state
     * change, and the renderer skips it entirely when it's invisible (alpha 0), so it costs nothing at rest.
     */
    private fun ambient(snap: mcc.machine.MachineSnapshot?) {
        val target = when {
            snap?.running != true -> 0
            (snap.request?.temperatureC ?: 0) > 0 -> T.EMBER_HOT
            else -> T.TEAL
        }
        if (target == glowColor) return
        glowColor = target
        if (target != 0) {
            val w = root.width.coerceAtLeast(1).toFloat()
            glow.background = android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.RectShape()).apply {
                paint.shader = android.graphics.RadialGradient(w * 0.55f, root.height * 1.05f, w * 0.75f,
                    intArrayOf(T.withAlpha(target, 0x55), T.withAlpha(target, 0x18), T.withAlpha(target, 0)), floatArrayOf(0f, 0.5f, 1f),
                    android.graphics.Shader.TileMode.CLAMP)
            }
        }
        glow.animate().alpha(if (target != 0) 1f else 0f).setDuration(700).start()
    }

    override fun play(cue: Sounds.Cue) { sounds?.play(cue) }

    /** Kitchen-timer style sounds: start, last seconds, step done, weight reached, finished, or a stop that needs attention. */
    private fun sounds() {
        val rs = MachineService.runnerState
        val snap = MachineService.snapshot
        val phase = rs?.phase
        val running = snap?.running == true
        val recipeWasRunning = lastPhase == RecipeRunner.Phase.RUNNING
        val turbo = snap?.request?.mode == mcc.machine.RunMode.TURBO
        if (running && !wasRunning && !turbo) play(Sounds.Cue.START)
        if (running && !turbo && snap != null && snap.request?.open != true) { // soft ticks for the last three seconds of a timed run
            val left = (snap.remainingMillis + 999) / 1000
            if (left in 1..3 && left != lastTick) { lastTick = left; play(Sounds.Cue.TICK) }
        } else lastTick = -1
        if (recipeWasRunning && phase != RecipeRunner.Phase.RUNNING) {
            when (val e = rs?.event) {
                is RecipeRunner.Event.Stopped -> play(if (e.reason == StopReason.USER) Sounds.Cue.STOP else Sounds.Cue.ALERT)
                RecipeRunner.Event.Paused -> play(Sounds.Cue.STOP)
                RecipeRunner.Event.Finished -> play(Sounds.Cue.FINISHED)
                null -> {}
                else -> play(Sounds.Cue.STEP)
            }
        } else if (lastPhase != null && lastPhase != RecipeRunner.Phase.DONE && phase == RecipeRunner.Phase.DONE) {
            play(Sounds.Cue.FINISHED) // finished with a prepare step
        } else if (!recipeWasRunning && wasRunning && !running) {
            when (snap?.lastStop) {
                StopReason.COMPLETED -> if (!turbo) play(Sounds.Cue.DONE)
                StopReason.USER, null -> play(Sounds.Cue.STOP)
                else -> play(Sounds.Cue.ALERT)
            }
        }
        val step = rs?.step
        val weighed = step is mcc.recipe.Step.Weigh && (rs.weighedGrams ?: 0) >= step.grams * 0.95
        if (weighed && !wasWeighed) play(Sounds.Cue.WEIGHED)
        wasWeighed = weighed
        lastPhase = phase
        wasRunning = running
    }

    private fun toggleLock() {
        val s = service ?: return
        if (MachineService.armed) { s.setArmed(false); play(Sounds.Cue.LOCK); notify(S.controlsLocked, Notice.OK); return }
        requestUnlock()
    }

    // ------------------------------------------------------------------ AppUi

    override fun requestUnlock() {
        if (MachineService.armed) return
        confirm(S.unlockTitle, S.unlockBody, S.unlock) { service?.setArmed(true); play(Sounds.Cue.UNLOCK); notify(S.controlsUnlocked, Notice.WARN) }
    }

    override fun celebrate() { celebration.burst() }

    override fun setLanguage(code: String) {
        if (code == prefs.uiLanguage) return
        prefs.uiLanguage = code
        L.set(code)
        rebuild()
    }

    override fun setTheme(mode: String, force: Boolean) {
        if (mode == prefs.theme && !force) return
        prefs.theme = mode
        if (wantNight() != T.dark) rebuild() else (screens["settings"] as? SettingsScreen)?.refreshTheme()
    }

    private fun wantNight() = when (prefs.theme) { "day" -> false; "night" -> true; else -> !Sun.isDay() }

    /** Auto mode: checked twice a minute; never while a field is being edited or a dialog is open. */
    private fun autoTheme() {
        val now = System.currentTimeMillis()
        if (now - lastThemeCheck < 30_000) return
        lastThemeCheck = now
        if (prefs.theme != "auto" || wantNight() == T.dark) return
        if (currentFocus is android.widget.EditText || dialogOpen) return
        rebuild()
    }

    /** Re-creates the activity (screens are built with the active palette and language) behind a cross-fade. */
    private fun rebuild() {
        fadeFrom?.recycle()
        fadeFrom = runCatching {
            android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888).also { b ->
                val cv = android.graphics.Canvas(b)
                window.decorView.background?.let { it.setBounds(0, 0, root.width, root.height); it.draw(cv) }
                root.draw(cv)
            }
        }.getOrNull()
        recreate()
    }

    override fun notify(msg: String, kind: Notice) {
        toast?.let { root.removeView(it) }
        val color = when (kind) { Notice.OK -> T.OK; Notice.WARN -> T.AMBER; Notice.ERROR -> T.DANGER; Notice.INFO -> T.TEXT_2 }
        val v = T.text(this, msg, 16f, T.TEXT, T.bold).apply {
            background = T.glass(26, strong = true, accent = color)
            setPadding(T.dp(22), T.dp(14), T.dp(22), T.dp(14)); gravity = Gravity.CENTER
            alpha = 0f; translationY = -T.dpf(16f); elevation = T.dpf(8f); maxWidth = T.dp(640)
        }
        root.addView(v, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = T.dp(82); leftMargin = T.dp(94) })
        toast = v
        v.animate().alpha(1f).translationY(0f).setDuration(200).setInterpolator(DecelerateInterpolator()).start()
        handler.postDelayed({ v.animate().alpha(0f).translationY(-T.dpf(10f)).setDuration(250).withEndAction { root.removeView(v); if (toast === v) toast = null }.start() }, 2800)
    }

    /** Modal confirmation. The scrim starts below the top bar, so STOP is never dimmed or blocked. */
    override fun confirm(title: String, msg: String, ok: String, danger: Boolean, dismiss: String?, onOk: () -> Unit) =
        dialog(title, msg, ok, danger, null, dismiss) { onOk() }.let {}

    override fun ask(title: String, msg: String, hint: String, ok: String, onOk: (String) -> Unit) =
        dialog(title, msg, ok, false, hint, onOk = onOk).let {}

    /** Returns the message view, so a caller can update it (the standby countdown). */
    private fun dialog(title: String, msg: String, ok: String, danger: Boolean, inputHint: String?, dismiss: String? = null, onOk: (String) -> Unit): TextView {
        val scrim = FrameLayout(this).apply { setBackgroundColor(T.SCRIM); isClickable = true; alpha = 0f }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = T.glass(24, strong = true)
            setPadding(T.dp(28), T.dp(26), T.dp(28), T.dp(22)); elevation = T.dpf(12f); isClickable = true
        }
        card.addView(T.text(this, title, 24f, T.TEXT, T.heavy))
        val msgView = T.text(this, msg, 16f, T.TEXT_2, T.regular).apply { setPadding(0, T.dp(12), 0, T.dp(if (inputHint == null) 22 else 12)); setLineSpacing(0f, 1.25f) }
        card.addView(msgView)
        val input = inputHint?.let { h ->
            android.widget.EditText(this).apply {
                hint = h; setHintTextColor(T.TEXT_3); setTextColor(T.TEXT); textSize = 16f; typeface = T.semibold
                background = T.glass(14); setPadding(T.dp(14), T.dp(10), T.dp(14), T.dp(10)); maxLines = 4
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_VARIATION_URI
            }.also { card.addView(it, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = T.dp(18) }) }
        }
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        var closed = false
        var self: (() -> Unit)? = null
        fun close() {
            if (closed) return
            closed = true
            if (closeDialog === self) closeDialog = null
            card.animate().scaleX(0.96f).scaleY(0.96f).setDuration(140).start()
            scrim.animate().alpha(0f).setDuration(150).withEndAction { root.removeView(scrim) }.start()
        }
        buttons.addView(HoldButton(this).apply { label = dismiss ?: S.cancel; icon = null; holdMillis = 0; style(T.SURFACE_3, T.SURFACE_3); onConfirm = { close() } },
            LinearLayout.LayoutParams(T.dp(150), T.dp(56)))
        buttons.addView(HoldButton(this).apply {
            label = ok; icon = null; holdMillis = 0
            if (danger) style(T.DANGER, T.DANGER_DEEP) else style(T.AMBER, T.EMBER)
            onConfirm = { val text = input?.text?.toString()?.trim() ?: ""; if (input == null || text.isNotEmpty()) { hideKeyboard(); close(); onOk(text) } }
        }, LinearLayout.LayoutParams(T.dp(210), T.dp(56)).apply { leftMargin = T.dp(12) })
        card.addView(buttons)
        // with a text field, sit high so the on-screen keyboard does not cover the card
        scrim.addView(card, if (input == null) FrameLayout.LayoutParams(T.dp(540), -2, Gravity.CENTER)
            else FrameLayout.LayoutParams(T.dp(640), -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = T.dp(8) })
        scrim.setOnClickListener { close() }
        closeDialog?.invoke()
        self = { close() }
        closeDialog = self
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1).apply { topMargin = T.dp(72) })
        input?.let { e ->
            e.requestFocus()
            e.postDelayed({ (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(e, 0) }, 250)
        }
        card.scaleX = 0.92f; card.scaleY = 0.92f; card.translationY = T.dpf(12f)
        scrim.animate().alpha(1f).setDuration(160).start()
        card.animate().scaleX(1f).scaleY(1f).translationY(0f).setDuration(240).setInterpolator(android.view.animation.OvershootInterpolator(1.1f)).start()
        return msgView
    }

    // ------------------------------------------------------------------ standby (like the stock app)

    private var lastUse = SystemClock.elapsedRealtime()
    private var lastActive = 0L
    private var lastTouch = SystemClock.elapsedRealtime()
    private val RELOCK_MS = 3 * 60_000L
    private var standbyMsg: TextView? = null
    private var standbyAt = 0L

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean { lastUse = SystemClock.elapsedRealtime(); lastTouch = lastUse; return super.dispatchTouchEvent(ev) }
    override fun dispatchKeyEvent(ev: KeyEvent): Boolean {
        lastUse = SystemClock.elapsedRealtime(); lastTouch = lastUse
        // the wheel goes to our screens before any focused view: otherwise a focused field or button swallows the press
        // the wheel: turning sends F6/F7 (136/137), pressing sends F5 (135, seen with getevent); 23 kept for adb tests
        if (ev.keyCode == 135 || ev.keyCode == 136 || ev.keyCode == 137 || ev.keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            if (closeDialog == null) {
                val code = if (ev.keyCode == 135) KeyEvent.KEYCODE_DPAD_CENTER else ev.keyCode
                if (ev.action == KeyEvent.ACTION_DOWN) onKeyDown(code, ev)
                return true
            }
        }
        return super.dispatchKeyEvent(ev)
    }

    /** After [Prefs.standbyMinutes] without use and with nothing running: a 30 s countdown, then screen off and controls locked. */
    private fun standbyTick() {
        val now = SystemClock.elapsedRealtime()
        val svc = service
        val snap = MachineService.snapshot
        val busy = snap?.running == true || MachineService.runnerState?.phase == mcc.recipe.RecipeRunner.Phase.RUNNING || MachineService.remotePermit != null
        if (busy) lastActive = now
        // controls lock again after a few minutes without touches or runs, standby or not
        if (MachineService.armed && svc != null && !busy && now - maxOf(lastTouch, lastActive) > RELOCK_MS) {
            svc.setArmed(false); play(Sounds.Cue.LOCK); notify(S.autoLocked, Notice.INFO)
        }
        if (!prefs.standbyEnabled || svc == null || busy) {
            lastUse = now
            if (standbyMsg != null) { standbyMsg = null; closeDialog?.invoke() }
            return
        }
        val msg = standbyMsg
        if (msg != null) {
            if (closeDialog == null) { standbyMsg = null; lastUse = now; return } // cancelled (or "turn off now" was pressed)
            val left = ((standbyAt - now + 999) / 1000).toInt()
            if (left <= 0) { standbyMsg = null; closeDialog?.invoke(); lastUse = now; svc.standby() }
            else msg.put(S.standbyBody(left))
            return
        }
        if (now - lastUse >= prefs.standbyMinutes * 60_000L) {
            closeDialog?.invoke()
            standbyAt = now + 30_000
            play(Sounds.Cue.STANDBY)
            standbyMsg = dialog(S.standbyTitle, S.standbyBody(30), S.standbyNow, false, null) { svc.standby() }
        }
    }

    /** When the index was last asked from the server (elapsedRealtime), to refresh a stale list now and then. */
    @Volatile private var indexFetchedAt = 0L

    /**
     * Stale-while-revalidate: with [refresh], the copy on the tablet shows at once (when nothing is shown yet), and
     * the server's answer replaces it when it comes, so a slow or offline server never leaves the list empty.
     */
    override fun reloadIndex(refresh: Boolean) {
        val empty = index == null
        if (refresh) indexFetchedAt = SystemClock.elapsedRealtime()
        Thread {
            if (refresh && empty) store.loadIndex(false).first?.let { cached -> runOnUiThread { if (index == null) showIndex(cached, null) } }
            val (idx, warning) = store.loadIndex(refresh)
            runOnUiThread { showIndex(idx, warning) }
        }.start()
    }

    /** Asks the server again when the list is older than 15 minutes (on opening Home or Recipes). */
    private fun revalidateIndex() {
        if (SystemClock.elapsedRealtime() - indexFetchedAt > 15 * 60_000L) reloadIndex(true)
    }

    private fun showIndex(idx: RecipeIndex?, warning: String?) {
        if (idx != null) index = idx
        (screens["recipes"] as? RecipesScreen)?.onIndex(warning)
        (screens["home"] as? HomeScreen)?.onIndex()
    }

    override fun cook(recipe: Recipe) {
        val cur = service?.recipe
        val st = MachineService.runnerState
        if (cur != null && cur.id != recipe.id && st != null && st.phase != RecipeRunner.Phase.DONE && (st.stepIndex > 0 || st.phase == RecipeRunner.Phase.PAUSED)) {
            confirm(S.switchRecipeTitle, S.switchRecipeBody(cur.title, st.stepIndex + 1, recipe.title), S.switchRecipeOk) { loadAndCook(recipe) }
            return
        }
        loadAndCook(recipe)
    }

    private fun loadAndCook(recipe: Recipe) {
        val err = service?.loadRecipe(recipe)
        if (err != null) { notify(err, Notice.WARN); return }
        go("cook")
        // the empty-bowl weight is only useful before anything goes in, so ask when a heating recipe is loaded
        val heats = recipe.steps.any { st -> st is mcc.recipe.Step.Machine && st.segments.any { it.tempC > 0 } }
        if (heats && MachineService.snapshot?.hasEmptyReference == false) askEmptyBowl(S.emptyBowlNeeded)
    }

    override fun askEmptyBowl(lead: String?) {
        confirm(S.emptyBowlTitle, listOfNotNull(lead, S.emptyBowlBody).joinToString("\n\n"), S.emptyBowlOk) {
            val e = service?.setEmptyBowl()
            notify(e ?: S.emptyBowlSaved, if (e == null) Notice.OK else Notice.WARN)
        }
    }

    override fun manualPreset(seconds: Int, speed: Int, tempC: Int, reverse: Boolean) {
        (screens["manual"] as ManualScreen).preset(seconds, speed, tempC, reverse)
        go("manual")
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == 136 || keyCode == 137 || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            val press = keyCode == KeyEvent.KEYCODE_DPAD_CENTER
            if (event?.repeatCount ?: 0 > 0) return true // holding the wheel down is one press
            play(if (press) Sounds.Cue.TAP else Sounds.Cue.KNOB) // a soft detent click per step, a tap per press
            if (runOverlay.isShown()) {
                // over the running view a press pauses (it only ever stops things); turning does nothing
                if (press) {
                    val s = service
                    if (MachineService.runnerState?.phase == RecipeRunner.Phase.RUNNING) s?.recipePause() else s?.pauseManual("knob")
                }
                return true
            }
            if (screens[current]?.onKnob(keyCode) == true) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onBackPressed() {
        closeDialog?.let { it(); return }
        if (runOverlay.isShown()) { runOverlay.minimise(); return }
        if (screens[current]?.onBack() == true) return
        if (current == "soup") go("programs") else if (current != "home") go("home") else super.onBackPressed()
    }

    private fun hideKeyboard() {
        currentFocus?.let { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(it.windowToken, 0); it.clearFocus() }
        immersive()
    }
}
