package mcc.app.ui

import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import mcc.app.MachineService
import mcc.recipe.MachinePlan
import mcc.recipe.Mode
import mcc.recipe.Recipe
import mcc.recipe.RecipeRunner
import mcc.recipe.Step

/** Guided cooking: one step at a time, with a live panel for machine and weighing steps. */
class CookScreen(app: AppUi) : Screen(app) {
    override val title: String get() = MachineService.runnerState?.let { st -> recipe?.let { S.stepOf(st.stepIndex + 1, it.steps.size) } } ?: S.cookTitle
    override val subtitle: String get() = recipe?.let { r -> r.title + (r.totalMinutes?.let { " · " + S.aboutMinutes(it) } ?: "") } ?: S.guidedCooking

    private val recipe: Recipe? get() = app.service?.recipe

    private lateinit var emptyState: LinearLayout
    private lateinit var doneState: LinearLayout
    private lateinit var main: LinearLayout
    private lateinit var stepBar: StepBar
    private lateinit var stepCard: LinearLayout
    private lateinit var kindPill: TextView
    private lateinit var stepNo: TextView
    private lateinit var stepText: TextView
    private lateinit var upNext: TextView
    private lateinit var machinePanel: LinearLayout
    private lateinit var ring: DialView
    private lateinit var action: HoldButton
    private lateinit var weighPanel: LinearLayout
    private lateinit var gauge: WeightGauge
    private lateinit var infoPanel: LinearLayout
    private lateinit var infoTitle: TextView
    private lateinit var infoText: TextView
    private lateinit var infoIcon: View
    private lateinit var message: TextView
    private lateinit var nextBtn: HoldButton
    private lateinit var backBtn: HoldButton
    private lateinit var ingredients: FrameLayout
    private lateinit var doneImage: RoundImage
    private var shownStep = -1
    private var shownRecipe: Recipe? = null
    private var notice: String? = null
    private var noticeUntil = 0L
    private var machineHint: String? = null
    private var lastPhase: RecipeRunner.Phase? = null
    private var lastPanel: View? = null

    override val view: View = build()

    private fun build(): View {
        val root = FrameLayout(c)
        main = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(T.dp(16), T.dp(2), T.dp(16), T.dp(14)) }
        stepBar = StepBar(c)
        main.addView(stepBar, LinearLayout.LayoutParams(-1, T.dp(10)).apply { bottomMargin = T.dp(14) })

        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        stepCard = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; background = T.glass(24); setPadding(T.dp(26), T.dp(22), T.dp(26), T.dp(18)) }
        val head = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        stepNo = GradientText(c, T.AMBER, T.EMBER).apply { textSize = 44f; typeface = T.heavy; includeFontPadding = false }
        head.addView(stepNo)
        kindPill = T.text(c, "", 13f, T.TEXT, T.heavy).apply { letterSpacing = 0.1f; setPadding(T.dp(12), T.dp(7), T.dp(12), T.dp(7)) }
        head.addView(kindPill, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = T.dp(14) })
        stepCard.addView(head)
        stepText = T.text(c, "", 28f, T.TEXT, T.bold).apply { setLineSpacing(0f, 1.18f) }
        stepCard.addView(ScrollView(c).apply { isVerticalScrollBarEnabled = false; isVerticalFadingEdgeEnabled = true; setFadingEdgeLength(T.dp(20)); addView(stepText) },
            LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = T.dp(12) })
        upNext = T.text(c, "", 15f, T.TEXT_2, T.semibold).apply { maxLines = 2 }
        stepCard.addView(upNext, LinearLayout.LayoutParams(-1, -2).apply { topMargin = T.dp(10) })
        row.addView(stepCard, LinearLayout.LayoutParams(0, -1, 1.25f))

        val panel = FrameLayout(c).apply { background = T.glass(24) }
        machinePanel = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(T.dp(18), T.dp(10), T.dp(18), T.dp(18)) }
        ring = DialView(c).apply { interactive = false }
        machinePanel.addView(ring, LinearLayout.LayoutParams(-1, 0, 1f))
        action = HoldButton(c).apply { onConfirm = { onAction() } }
        machinePanel.addView(action, LinearLayout.LayoutParams(-1, T.dp(66)))
        remoteBtn = HoldButton(c).apply { label = S.remote; icon = Icon.WIFI; holdMillis = 0; style(T.SURFACE_3, T.SURFACE_3); onConfirm = { toggleRemote() } }
        machinePanel.addView(remoteBtn, LinearLayout.LayoutParams(-1, T.dp(50)).apply { topMargin = T.dp(8) })
        panel.addView(machinePanel)
        weighPanel = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(T.dp(18), T.dp(18), T.dp(18), T.dp(18)) }
        weighPanel.addView(T.text(c, S.kindWeigh, 13f, T.TEAL, T.heavy).apply { letterSpacing = 0.12f })
        gauge = WeightGauge(c)
        weighPanel.addView(gauge, LinearLayout.LayoutParams(-1, 0, 1f))
        weighPanel.addView(HoldButton(c).apply { label = S.reZero; icon = Icon.REFRESH; holdMillis = 0; style(T.SURFACE_3, T.SURFACE_3)
            onConfirm = { val e = app.service?.tare(); app.notify(e ?: S.scaleZeroed, if (e == null) Notice.OK else Notice.WARN) } },
            LinearLayout.LayoutParams(T.dp(240), T.dp(54)))
        panel.addView(weighPanel)
        infoPanel = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(T.dp(26), T.dp(20), T.dp(26), T.dp(20)) }
        infoIcon = FrameLayout(c).apply { background = T.round(T.withAlpha(T.EMBER, 0x22), 60, T.withAlpha(T.EMBER, 0x70), 2) }
            .also { it.addView(IconView(c, Icon.HAND, T.AMBER, 1.8f), FrameLayout.LayoutParams(T.dp(56), T.dp(56), Gravity.CENTER)) }
        infoPanel.addView(infoIcon, LinearLayout.LayoutParams(T.dp(120), T.dp(120)))
        infoTitle = T.text(c, S.yourTurn, 24f, T.TEXT, T.heavy).apply { gravity = Gravity.CENTER; setPadding(0, T.dp(16), 0, T.dp(6)) }
        infoPanel.addView(infoTitle)
        infoText = T.text(c, "", 16f, T.TEXT_2, T.semibold).apply { gravity = Gravity.CENTER; setLineSpacing(0f, 1.25f) }
        infoPanel.addView(infoText)
        panel.addView(infoPanel)
        row.addView(panel, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = T.dp(16) })
        main.addView(row, LinearLayout.LayoutParams(-1, 0, 1f))

        message = T.text(c, "", 15f, T.AMBER, T.bold).apply { gravity = Gravity.CENTER; setPadding(0, T.dp(8), 0, T.dp(2)); maxLines = 2 }
        main.addView(message, LinearLayout.LayoutParams(-1, -2))

        val bottom = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        backBtn = HoldButton(c).apply { label = S.back; icon = Icon.BACK; holdMillis = 0; style(T.SURFACE_3, T.SURFACE_3); onConfirm = { go(-1) } }
        bottom.addView(backBtn, LinearLayout.LayoutParams(T.dp(170), T.dp(60)))
        ingredientsBtn = HoldButton(c).apply { label = S.ingredients; icon = Icon.LIST; holdMillis = 0; style(T.SURFACE_3, T.SURFACE_3); onConfirm = { toggleIngredients() } }
        bottom.addView(ingredientsBtn,
            LinearLayout.LayoutParams(T.dp(220), T.dp(60)).apply { leftMargin = T.dp(10) })
        bottom.addView(View(c), LinearLayout.LayoutParams(0, 1, 1f))
        nextBtn = HoldButton(c).apply { label = S.next; icon = Icon.NEXT; holdMillis = 0; style(T.AMBER, T.EMBER); onConfirm = { go(+1) } }
        bottom.addView(nextBtn, LinearLayout.LayoutParams(T.dp(230), T.dp(60)))
        main.addView(bottom, LinearLayout.LayoutParams(-1, -2).apply { topMargin = T.dp(6) })
        root.addView(main)

        ingredients = FrameLayout(c).apply { visibility = View.GONE; setBackgroundColor(T.withAlpha(T.SCRIM, if (T.dark) 0x99 else 0x70)); isClickable = true; setOnClickListener { toggleIngredients() } }
        root.addView(ingredients, FrameLayout.LayoutParams(-1, -1))

        emptyState = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            val ic = FrameLayout(c).apply { background = T.gradient(T.withAlpha(T.AMBER, 0x40), T.withAlpha(T.EMBER_HOT, 0x20), 70) }
            ic.addView(IconView(c, Icon.POT, T.AMBER, 1.8f), FrameLayout.LayoutParams(T.dp(70), T.dp(70), Gravity.CENTER))
            addView(ic, LinearLayout.LayoutParams(T.dp(140), T.dp(140)))
            addView(T.text(c, S.nothingCooking, 28f, T.TEXT, T.heavy).apply { gravity = Gravity.CENTER; setPadding(0, T.dp(22), 0, T.dp(8)) })
            addView(T.text(c, S.nothingCookingBody, 16f, T.TEXT_2, T.semibold).apply { gravity = Gravity.CENTER })
            addView(HoldButton(c).apply { label = S.browseRecipes; icon = Icon.BOOK; holdMillis = 0; style(T.AMBER, T.EMBER); onConfirm = { app.go("recipes") } },
                LinearLayout.LayoutParams(T.dp(280), T.dp(60)).apply { topMargin = T.dp(26) })
        }
        root.addView(emptyState, FrameLayout.LayoutParams(-1, -1))

        doneState = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(T.dp(40), T.dp(20), T.dp(40), T.dp(30)); visibility = View.GONE }
        doneState.background = BackdropSlice(doneState)
        doneImage = RoundImage(c, 28f).apply { placeholder(0xFF8A3F12.toInt(), T.SURFACE) }
        doneState.addView(doneImage, LinearLayout.LayoutParams(0, -1, 1f))
        val doneText = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(T.dp(36), 0, 0, 0) }
        doneTitle = GradientText(c, T.AMBER, T.EMBER_HOT).apply { text = S.bomApetite; textSize = 44f; typeface = T.heavy; includeFontPadding = false }
        doneText.addView(doneTitle)
        doneText.addView(T.text(c, S.allDone, 18f, T.TEXT_2, T.semibold).apply { setPadding(0, T.dp(12), 0, T.dp(30)) })
        doneText.addView(HoldButton(c).apply { label = S.cookElse; icon = Icon.BOOK; holdMillis = 0; style(T.AMBER, T.EMBER); onConfirm = { app.service?.loadRecipe(null); app.go("recipes") } },
            LinearLayout.LayoutParams(T.dp(340), T.dp(60)))
        doneText.addView(HoldButton(c).apply { label = S.backToLast; icon = Icon.BACK; holdMillis = 0; style(T.SURFACE_3, T.SURFACE_3); onConfirm = { app.service?.recipeBack() } },
            LinearLayout.LayoutParams(T.dp(340), T.dp(56)).apply { topMargin = T.dp(12) })
        doneState.addView(doneText, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(doneState, FrameLayout.LayoutParams(-1, -1))
        return root
    }

    private var direction = 1

    private fun go(dir: Int) {
        direction = dir
        if (dir > 0) app.service?.recipeNext() else app.service?.recipeBack()
    }

    private fun toggleIngredients() {
        val r = recipe ?: return
        if (ingredients.visibility == View.VISIBLE) {
            val sheet = ingredients.getChildAt(0)
            sheet?.animate()?.translationX(T.dpf(60f))?.setDuration(180)?.start()
            ingredients.animate().alpha(0f).setDuration(180).withEndAction { ingredients.visibility = View.GONE }.start(); return
        }
        ingredients.removeAllViews()
        val sheet = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; background = T.glass(24, strong = true); setPadding(T.dp(26), T.dp(18), T.dp(18), T.dp(18)); isClickable = true }
        val head = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(T.text(c, S.ingredientsFor(r) + (r.servings?.let { " · $it" } ?: ""), 22f, T.TEXT, T.heavy), LinearLayout.LayoutParams(0, -2, 1f))
        val close = FrameLayout(c).apply { background = T.pressable(T.glass(24)); setOnClickListener { toggleIngredients() } }
        close.addView(IconView(c, Icon.CLOSE, T.TEXT, 2.4f), FrameLayout.LayoutParams(T.dp(20), T.dp(20), Gravity.CENTER))
        T.press(close)
        head.addView(close, LinearLayout.LayoutParams(T.dp(48), T.dp(48)))
        sheet.addView(head)
        val list = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, T.dp(8), T.dp(8)) }
        RecipesScreen.fillIngredients(list, r)
        sheet.addView(ScrollView(c).apply { isVerticalFadingEdgeEnabled = true; setFadingEdgeLength(T.dp(24)); addView(list) }, LinearLayout.LayoutParams(-1, -1).apply { topMargin = T.dp(6) })
        ingredients.addView(sheet, FrameLayout.LayoutParams(T.dp(500), -1, Gravity.END).apply { setMargins(0, 0, T.dp(16), T.dp(14)) })
        ingredients.alpha = 0f; ingredients.visibility = View.VISIBLE
        sheet.translationX = T.dpf(60f)
        ingredients.animate().alpha(1f).setDuration(180).start()
        sheet.animate().translationX(0f).setDuration(240).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun onAction() {
        val s = app.service ?: return
        when (MachineService.runnerState?.phase) {
            RecipeRunner.Phase.RUNNING -> s.recipePause()
            else -> {
                if (!MachineService.armed) { app.requestUnlock(); return }
                s.recipeStart()?.let { flash(it) }
            }
        }
    }

    private fun flash(m: String) { notice = m; noticeUntil = System.currentTimeMillis() + 5000; render() }

    override fun onBack(): Boolean {
        if (ingredients.visibility == View.VISIBLE) { toggleIngredients(); return true }
        return false
    }

    /** Knob: turn = previous/next step (never while the machine runs), push = Next on non-machine steps. */
    override fun onKnob(keyCode: Int): Boolean {
        val st = MachineService.runnerState ?: return false
        if (st.phase == RecipeRunner.Phase.RUNNING) return true
        val r = recipe ?: return true
        when (keyCode) {
            136, 137 -> {
                // turning browses steps: one step per detent burst, and never past the last step into "finished"
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastKnobStep < 300) return true
                if (st.phase == RecipeRunner.Phase.DONE) return true
                if (keyCode == 136 && st.stepIndex >= r.steps.lastIndex) return true
                lastKnobStep = now
                go(if (keyCode == 136) +1 else -1)
            }
            else -> if (st.step !is Step.Machine) go(+1) // a press is deliberate: it may finish the recipe
        }
        return true
    }

    private var lastKnobStep = 0L

    // ------------------------------------------------------------------ render

    override fun render() {
        val r = recipe
        val st = MachineService.runnerState
        val none = r == null || st == null
        emptyState.visibility = if (none) View.VISIBLE else View.GONE
        if (r == null || st == null) { main.visibility = View.GONE; doneState.visibility = View.GONE; lastPhase = null; return }
        val done = st.phase == RecipeRunner.Phase.DONE
        if (done && lastPhase != RecipeRunner.Phase.DONE && lastPhase != null) app.celebrate()
        lastPhase = st.phase
        if (done) doneTitle.put(S.doneFor(r))
        ingredientsBtn.label = S.ingredientsFor(r)
        if (done && doneState.visibility != View.VISIBLE) {
            doneState.visibility = View.VISIBLE
            Anim.stagger(listOf(doneImage) + (0 until (doneState.getChildAt(1) as LinearLayout).childCount).map { (doneState.getChildAt(1) as LinearLayout).getChildAt(it) }, step = 70)
        } else if (!done) doneState.visibility = View.GONE
        main.visibility = if (done) View.GONE else View.VISIBLE
        if (done) {
            if (doneImage.tag != "${r.image}@${T.dp(500)}") ImageLoader.load(app.store, r.image, doneImage, T.dp(500))
            return
        }

        stepBar.count = r.steps.size; stepBar.current = st.stepIndex
        val step = st.step
        if (st.stepIndex != shownStep || r !== shownRecipe) {
            val first = shownRecipe !== r
            val dir = if (st.stepIndex >= shownStep) 1 else -1
            shownStep = st.stepIndex; shownRecipe = r
            stepNo.put("%02d".format(st.stepIndex + 1))
            val text = step?.text?.ifBlank { null } ?: (step as? Step.Machine)?.let { S.stepSentence(it) } ?: ""
            stepText.text = text
            stepText.textSize = when { text.length < 70 -> 32f; text.length < 140 -> 27f; text.length < 240 -> 23f; else -> 19.5f }
            val (kind, col) = when (step) {
                is Step.Machine -> S.kindMachine to (if (step.segments.any { it.tempC > 0 } || step.mode == Mode.ROAST || step.mode == Mode.STEAM) T.EMBER else T.TEAL)
                is Step.Weigh -> S.kindWeigh to T.TEAL
                else -> S.kindPrepare to T.TEXT_2
            }
            kindPill.text = kind
            kindPill.setTextColor(col)
            kindPill.background = T.round(T.withAlpha(col, 0x22), 15, T.withAlpha(col, 0x66), 1)
            val nxt = r.steps.getOrNull(st.stepIndex + 1)
            upNext.text = when (nxt) {
                null -> S.lastStep
                is Step.Machine -> S.upNext(S.stepShort(nxt))
                is Step.Weigh -> S.upNext(S.weighGrams(nxt.grams) + (nxt.text.takeIf { it.isNotBlank() }?.let { " · " + it.take(60) + if (it.length > 60) "…" else "" } ?: ""))
                else -> S.upNext(nxt.text.take(90) + if (nxt.text.length > 90) "…" else "")
            }
            // step change: the card content slides in from the side we're moving to
            if (!first) {
                listOf(stepNo, stepText, upNext).forEachIndexed { i, v ->
                    v.alpha = 0f; v.translationX = dir * T.dpf(40f)
                    v.animate().alpha(1f).translationX(0f).setStartDelay(i * 40L).setDuration(260).setInterpolator(DecelerateInterpolator()).start()
                }
            }
        }

        if (step !is Step.Machine) machineHint = null
        val panel = when (step) { is Step.Machine -> machinePanel; is Step.Weigh -> weighPanel; else -> infoPanel }
        machinePanel.visibility = if (panel === machinePanel) View.VISIBLE else View.GONE
        weighPanel.visibility = if (panel === weighPanel) View.VISIBLE else View.GONE
        infoPanel.visibility = if (panel === infoPanel) View.VISIBLE else View.GONE
        if (panel !== lastPanel) {
            if (lastPanel != null) { panel.alpha = 0f; panel.scaleX = 0.96f; panel.scaleY = 0.96f; panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start() }
            if (panel === infoPanel) Anim.pop(infoIcon)
            lastPanel = panel
        }

        when (step) {
            is Step.Machine -> renderMachine(step, st)
            is Step.Weigh -> { gauge.ofText = { "/ $it g" }; gauge.set(st.weighedGrams, step.grams) }
            else -> infoText.put(if (r.steps.getOrNull(st.stepIndex + 1) is Step.Machine) S.tapNextMachine else S.tapNextDone)
        }

        val running = st.phase == RecipeRunner.Phase.RUNNING
        nextBtn.isEnabled = !running; backBtn.isEnabled = !running && st.stepIndex > 0
        val machineReady = step is Step.Machine && (st.phase == RecipeRunner.Phase.READY || st.phase == RecipeRunner.Phase.PAUSED)
        nextBtn.label = when { st.stepIndex + 1 >= r.steps.size -> S.finish; machineReady -> S.skip; else -> S.next }
        // on machine steps the Start button is the hero; Skip becomes a quiet ghost button
        if (machineReady) nextBtn.style(T.SURFACE_3, T.SURFACE_3) else nextBtn.style(T.AMBER, T.EMBER)
        val n = notice?.takeIf { System.currentTimeMillis() < noticeUntil }
        message.put(n ?: machineHint ?: S.event(st.event) ?: "")
        message.setTextColor(if (n == null && machineHint == null && st.event == RecipeRunner.Event.StepDone) T.OK else T.AMBER)
    }

    private lateinit var ingredientsBtn: HoldButton
    private lateinit var doneTitle: GradientText
    private lateinit var remoteBtn: HoldButton

    private fun toggleRemote() {
        val s = app.service ?: return
        if (MachineService.remotePermit != null) { s.cancelRemote("cancelled at the machine"); app.notify(S.remoteCancelled); return }
        if (!MachineService.armed) { app.requestUnlock(); return }
        val st = MachineService.runnerState ?: return
        val step = st.step as? Step.Machine ?: return
        val what = (recipe?.title ?: "") + " · " + S.stepShort(step)
        app.confirm(S.remoteTitle, S.remoteBody(what, app.prefs.remoteMinutes), S.remoteAllow) {
            val e = s.allowRemote(null, what)
            app.notify(e ?: S.remoteArmed(app.prefs.remoteMinutes), if (e == null) Notice.OK else Notice.WARN)
        }
    }

    private fun renderMachine(step: Step.Machine, st: RecipeRunner.State) {
        val pm = MachineService.remotePermit
        val idle = st.phase == RecipeRunner.Phase.READY || st.phase == RecipeRunner.Phase.PAUSED
        remoteBtn.visibility = if ((app.prefs.remoteStart && idle) || (pm != null && pm.recipeStep != null)) View.VISIBLE else View.GONE
        remoteBtn.label = if (pm?.recipeStep != null) S.remoteLeft(((pm.untilElapsed - android.os.SystemClock.elapsedRealtime()) / 60_000 + 1).toInt()) else S.remote
        if (pm?.recipeStep != null) remoteBtn.style(T.TEAL, T.TEAL_DEEP) else remoteBtn.style(T.SURFACE_3, T.SURFACE_3)
        val heat = step.segments.maxOf { it.tempC }.let { if (step.mode == Mode.ROAST && it == 0) 130 else it }
        val hot = heat > 0 || step.mode == Mode.STEAM
        ring.colorA = if (hot) T.AMBER else T.TEAL
        ring.colorB = if (hot) T.EMBER_HOT else T.TEAL_DEEP
        val total = MachinePlan.expand(step).sumOf { it.durationMillis }
        val left = st.remainingMillis ?: total
        val name = S.modeName(step.mode, heat, step.segments[0].speed, step.segments.size > 1)
        ring.label = name
        ring.value = fmtClock(left)
        val s0 = step.segments[0]
        ring.unit = when (step.mode) {
            Mode.RUN -> buildString {
                append(if (step.segments.size > 1) "${S.speed.lowercase(S.locale)} ${step.segments.first().speed}→${step.segments.last().speed}" else "${S.speed.lowercase(S.locale)} ${s0.speed}")
                if (heat > 0) append(" · $heat°C")
                if (s0.reverse) append(" · ${S.revShort}")
            }
            Mode.ROAST -> "$heat°C"
            else -> S.modeUnit(step.mode)
        }
        val f = MachineService.snapshot?.lastFrame
        ring.sub = if (f != null && f.temperatureStepC > 0) S.bowlTemp(f.temperatureStepC) else ""
        ring.setFraction(if (total > 0) (total - left) / total.toFloat() else 0f)
        ring.progress = if (st.phase == RecipeRunner.Phase.RUNNING || st.phase == RecipeRunner.Phase.PAUSED) (total - left) / total.toFloat() else null
        val enabled = MachinePlan.expand(step).all { it.mode in mcc.machine.SafetyLimits().enabledModes }

        val armed = MachineService.armed
        when {
            st.phase == RecipeRunner.Phase.RUNNING -> { action.label = S.pause; action.icon = Icon.PAUSE; action.holdMillis = 0; action.style(T.SURFACE_3, T.SURFACE_3); action.isEnabled = true }
            !enabled -> { action.label = S.notEnabled(name); action.icon = Icon.LOCK; action.holdMillis = 0; action.style(T.SURFACE_3, T.SURFACE_3); action.isEnabled = false }
            !armed -> { action.label = S.unlock; action.icon = Icon.LOCK; action.holdMillis = 0; action.style(T.SURFACE_3, T.SURFACE_3); action.isEnabled = true }
            else -> {
                action.label = if (st.phase == RecipeRunner.Phase.PAUSED) S.holdToResume else S.holdToStart
                action.icon = Icon.PLAY; action.holdMillis = 900; action.style(ring.colorA, ring.colorB); action.isEnabled = true
            }
        }
        machineHint = if (!enabled && st.phase == RecipeRunner.Phase.READY) S.notEnabledHint(name) else null
    }
}
