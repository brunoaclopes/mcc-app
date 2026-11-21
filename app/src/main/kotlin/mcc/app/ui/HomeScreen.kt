package mcc.app.ui

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import mcc.app.MachineService
import mcc.recipe.RecipeIndex
import mcc.recipe.RecipeRunner

/** Dashboard: live machine tile, scale, bowl temperature, continue cooking, quick presets, automatic programs. */
class HomeScreen(app: AppUi) : Screen(app) {
    override val title get() = S.homeTitle

    private lateinit var statusWord: GradientText
    private lateinit var statusDetail: TextView
    private lateinit var statusRing: ClockRing
    private lateinit var weightBig: GradientText
    private lateinit var weightSub: TextView
    private lateinit var tempBig: GradientText
    private lateinit var tempSub: TextView
    private lateinit var tempIcon: IconView
    private lateinit var heatBar: HeatBar
    private lateinit var cookTitle: TextView
    private lateinit var cookSub: TextView
    private lateinit var cookImage: RoundImage
    private lateinit var cookCta: HoldButton
    private lateinit var cookLabel: TextView
    private lateinit var idleActions: LinearLayout
    private lateinit var pausedActions: LinearLayout
    private lateinit var remoteBtn: HoldButton
    private val tiles = ArrayList<View>()
    private var cookImageFor: String? = null
    private var shownGrams: Int? = null
    private var lastWord: String? = null

    override val view: View = build()

    private fun tile() = FrameLayout(c).apply { background = T.glass(24) }.also { tiles.add(it) }

    private fun label(s: String, color: Int = T.TEXT_2) = T.text(c, s.uppercase(S.locale), 12.5f, color, T.heavy).apply { letterSpacing = 0.1f }

    private fun header(icon: Icon, color: Int, text: String) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(IconView(c, icon, color), LinearLayout.LayoutParams(T.dp(20), T.dp(20)))
        addView(label(text).apply { setPadding(T.dp(8), 0, 0, 0) })
    }

    private fun build(): View {
        val page = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(T.dp(16), T.dp(4), T.dp(16), T.dp(16)) }
        val gap = T.dp(16)
        // one 2:1:1 grid for both rows so the columns line up
        fun lp(w: Float, first: Boolean) = LinearLayout.LayoutParams(0, -1, w).apply { if (!first) leftMargin = gap }

        // ---- row 1: machine | scale | bowl
        val row1 = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        val status = tile()
        val sl = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; setPadding(T.dp(22), T.dp(20), T.dp(12), T.dp(18)) }
        val st = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        st.addView(label(S.machine))
        statusWord = GradientText(c, T.OK, T.OK).apply { textSize = 34f; typeface = T.heavy; includeFontPadding = false; setPadding(0, T.dp(10), 0, T.dp(6)) }
        st.addView(statusWord)
        statusDetail = T.text(c, "", 15f, T.TEXT_2, T.semibold).apply { setLineSpacing(0f, 1.2f); maxLines = 3 }
        st.addView(statusDetail)
        st.addView(View(c), LinearLayout.LayoutParams(1, 0, 1f))
        val actions = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(HoldButton(c).apply { label = S.navManual; icon = Icon.DIAL; holdMillis = 0; style(T.SURFACE_3, T.SURFACE_3); onConfirm = { app.go("manual") } },
            LinearLayout.LayoutParams(0, T.dp(52), 1f))
        // cleaning is machine care, not a recipe: it has its own button here
        actions.addView(HoldButton(c).apply { label = S.cleanShort; icon = Icon.DROP; holdMillis = 0; style(T.SURFACE_3, T.SURFACE_3)
            onConfirm = { app.cook(mcc.recipe.Programs.cleaning(app.prefs.uiLanguage)) } },
            LinearLayout.LayoutParams(0, T.dp(52), 1f).apply { leftMargin = T.dp(10) })
        st.addView(actions, LinearLayout.LayoutParams(-1, -2))
        idleActions = actions
        // a paused manual run: continue here, or permit Home Assistant to continue it
        pausedActions = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; visibility = View.GONE }
        pausedActions.addView(HoldButton(c).apply { label = S.continueLabel; icon = Icon.PLAY; holdMillis = 0; style(T.EMBER, T.EMBER_HOT)
            onConfirm = {
                if (!MachineService.armed) app.requestUnlock()
                else app.service?.resumeManual()?.let { app.notify(it, Notice.WARN) }
            } }, LinearLayout.LayoutParams(0, T.dp(52), 1f))
        remoteBtn = HoldButton(c).apply { label = S.remote; icon = Icon.WIFI; holdMillis = 0; style(T.SURFACE_3, T.SURFACE_3); onConfirm = { toggleRemote() } }
        pausedActions.addView(remoteBtn, LinearLayout.LayoutParams(0, T.dp(52), 1f).apply { leftMargin = T.dp(10) })
        st.addView(pausedActions, LinearLayout.LayoutParams(-1, -2))
        sl.addView(st, LinearLayout.LayoutParams(0, -1, 1f))
        statusRing = ClockRing(c)
        sl.addView(statusRing, LinearLayout.LayoutParams(T.dp(150), -1).apply { leftMargin = T.dp(10) })
        status.addView(sl)
        row1.addView(status, lp(2f, true))

        val scale = tile()
        val sc = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(T.dp(20), T.dp(20), T.dp(20), T.dp(18)) }
        sc.addView(header(Icon.SCALE, T.TEAL, S.scale))
        weightBig = GradientText(c, T.TEXT, T.WEIGHT_END).apply { text = "—"; textSize = 44f; typeface = T.heavy; fontFeatureSettings = "tnum"; includeFontPadding = false; setPadding(0, T.dp(12), 0, T.dp(4)) }
        sc.addView(weightBig)
        weightSub = T.text(c, "", 13.5f, T.TEXT_2, T.semibold).apply { maxLines = 3; setLineSpacing(0f, 1.15f) }
        sc.addView(weightSub)
        sc.addView(View(c), LinearLayout.LayoutParams(1, 0, 1f))
        sc.addView(HoldButton(c).apply { label = S.tare; icon = Icon.REFRESH; holdMillis = 0; style(T.TEAL, T.TEAL_DEEP)
            onConfirm = { val e = app.service?.tare(); app.notify(e ?: S.scaleZeroed, if (e == null) Notice.OK else Notice.WARN) } },
            LinearLayout.LayoutParams(-1, T.dp(50)))
        scale.addView(sc)
        row1.addView(scale, lp(1f, false))

        val temp = tile()
        val tc = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(T.dp(20), T.dp(20), T.dp(20), T.dp(18)) }
        val th = header(Icon.THERMO, T.AMBER, S.bowl)
        tempIcon = th.getChildAt(0) as IconView
        tc.addView(th)
        tempBig = GradientText(c, T.TEXT, T.AMBER).apply { text = "—"; textSize = 44f; typeface = T.heavy; includeFontPadding = false; setPadding(0, T.dp(12), 0, T.dp(4)) }
        tc.addView(tempBig)
        tempSub = T.text(c, "", 13.5f, T.TEXT_2, T.semibold)
        tc.addView(tempSub)
        tc.addView(View(c), LinearLayout.LayoutParams(1, 0, 1f))
        heatBar = HeatBar(c)
        tc.addView(heatBar, LinearLayout.LayoutParams(-1, T.dp(34)))
        temp.addView(tc)
        row1.addView(temp, lp(1f, false))
        page.addView(row1, LinearLayout.LayoutParams(-1, 0, 1f))

        // ---- row 2: cooking | quick start | programs
        val row2 = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        val cook = FrameLayout(c).apply { setOnClickListener { if (app.service?.recipe != null) app.go("cook") else app.go("recipes") } }.also { tiles.add(it) }
        cookImage = RoundImage(c, 24f).apply { scrim = 0.95f; scrimLeft = true; scrimColor = 0xF5000000.toInt() }
        cook.addView(cookImage, FrameLayout.LayoutParams(-1, -1))
        val cl = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(T.dp(22), T.dp(20), T.dp(22), T.dp(20)) }
        cookLabel = label(S.cooking, T.AMBER); cl.addView(cookLabel)
        cookTitle = T.text(c, "", 26f, T.ON_PHOTO, T.heavy).apply { maxLines = 2; setPadding(0, T.dp(10), 0, T.dp(6)); setShadowLayer(8f, 0f, 1f, 0xCC000000.toInt()) }
        cl.addView(cookTitle, LinearLayout.LayoutParams(T.dp(320), -2))
        cookSub = T.text(c, "", 15f, T.ON_PHOTO, T.semibold).apply { setShadowLayer(6f, 0f, 1f, 0xCC000000.toInt()) }
        cl.addView(cookSub)
        cl.addView(View(c), LinearLayout.LayoutParams(1, 0, 1f))
        cookCta = HoldButton(c).apply { label = S.browseRecipes; icon = Icon.NEXT; holdMillis = 0; style(T.AMBER, T.EMBER)
            onConfirm = { if (app.service?.recipe != null) app.go("cook") else app.go("recipes") } }
        cl.addView(cookCta, LinearLayout.LayoutParams(T.dp(240), T.dp(50)))
        cook.addView(cl)
        T.press(cook)
        row2.addView(cook, lp(2f, true))

        val quick = tile()
        val ql = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(T.dp(16), T.dp(18), T.dp(16), T.dp(16)) }
        ql.addView(label(S.quickStart))
        val grid1 = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        val grid2 = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        fun preset(row: LinearLayout, icon: Icon, name: String, detail: String, col: Int, first: Boolean, go: () -> Unit) {
            val b = LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; background = T.pressable(T.glass(16)); setPadding(T.dp(12), T.dp(4), T.dp(6), T.dp(4))
                setOnClickListener { go() }
            }
            b.addView(IconView(c, icon, col), LinearLayout.LayoutParams(T.dp(20), T.dp(20)))
            b.addView(T.text(c, name, 15f, T.TEXT, T.heavy).apply { setPadding(0, T.dp(5), 0, T.dp(2)); maxLines = 1 })
            b.addView(T.text(c, detail, 13f, T.TEXT_2, T.semibold).apply { maxLines = 1 })
            T.press(b)
            row.addView(b, LinearLayout.LayoutParams(0, -1, 1f).apply { if (!first) leftMargin = T.dp(10) })
        }
        preset(grid1, Icon.FAN, S.presetChop, "5 s · 5", T.TEAL, true) { app.manualPreset(5, 5, 0) }
        preset(grid1, Icon.BOLT, S.presetBlend, "1 min · 8", T.TEAL, false) { app.manualPreset(60, 8, 0) }
        preset(grid2, Icon.REVERSE, S.presetStir, "5 min · 1", T.TEAL, true) { app.manualPreset(300, 1, 0, true) }
        preset(grid2, Icon.FLAME, S.presetWarm, "10 min · 60°", T.EMBER, false) { app.manualPreset(600, 1, 60) }
        ql.addView(grid1, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = T.dp(12) })
        ql.addView(grid2, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = T.dp(10) })
        quick.addView(ql)
        row2.addView(quick, lp(1f, false))

        // automatic programs: one tap loads the program; nothing moves until Start is held on the Cook screen
        val prog = tile()
        val pl = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(T.dp(16), T.dp(18), T.dp(12), T.dp(10)) }
        val lang = app.prefs.uiLanguage
        pl.addView(label(if (lang == "pt") mcc.recipe.Programs.TAG_PT else mcc.recipe.Programs.TAG_EN))
        val list = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, T.dp(4), T.dp(8)) }
        mcc.recipe.Programs.cooking().filter { it.language == lang }.forEach { r -> list.addView(programRow(r), LinearLayout.LayoutParams(-1, T.dp(46)).apply { topMargin = T.dp(6) }) }
        pl.addView(android.widget.ScrollView(c).apply { isVerticalScrollBarEnabled = false; isVerticalFadingEdgeEnabled = true; setFadingEdgeLength(T.dp(18)); addView(list) },
            LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = T.dp(4) })
        prog.addView(pl)
        row2.addView(prog, lp(1f, false))
        page.addView(row2, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = gap })
        return page
    }

    fun onIndex() {}

    private fun programRow(r: mcc.recipe.Recipe) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        background = T.pressable(T.glass(14)); setPadding(T.dp(6), 0, T.dp(10), 0)
        setOnClickListener { app.cook(r) }
        val img = RoundImage(c, 9f).apply { placeholder(T.withAlpha(T.EMBER, 0x40), T.withAlpha(T.AMBER, 0x20)) }
        addView(img, LinearLayout.LayoutParams(T.dp(34), T.dp(34)))
        ImageLoader.load(app.store, r.image, img, T.dp(68))
        // "Ovos cozidos, moles" -> "Ovos moles", "Boiled eggs, soft" -> "Soft eggs": the variants stay distinct in a narrow row
        val label = if (", " !in r.title) r.title else {
            val (head, tail) = r.title.split(", ", limit = 2)
            if (r.language == "pt") head.substringBefore(" ") + " " + tail
            else tail.replaceFirstChar { it.uppercase() } + " " + head.substringAfterLast(" ")
        }
        addView(T.text(c, label, 13.5f, T.TEXT, T.bold).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(T.dp(10), 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
        r.totalMinutes?.let { addView(T.text(c, S.minutesShort(it), 12f, T.TEXT_3, T.semibold)) }
        T.press(this)
    }

    override fun onShow() { Anim.stagger(tiles) }

    private fun toggleRemote() {
        val s = app.service ?: return
        if (MachineService.remotePermit != null) { s.cancelRemote("cancelled at the machine"); app.notify(S.remoteCancelled); return }
        if (!MachineService.armed) { app.requestUnlock(); return }
        val p = MachineService.pausedRun ?: return
        val what = S.paused + " · " + fmtClock(p.request.durationMillis) + " " + S.left
        app.confirm(S.remoteTitle, S.remoteBody(what, app.prefs.remoteMinutes), S.remoteAllow) {
            val e = s.allowRemote(null, what)
            app.notify(e ?: S.remoteArmed(app.prefs.remoteMinutes), if (e == null) Notice.OK else Notice.WARN)
        }
    }

    override fun onKnob(keyCode: Int): Boolean {
        if (keyCode != 23) return false
        app.go(if (app.service?.recipe != null) "cook" else "recipes")
        return true
    }

    override fun render() {
        val s = snap
        val f = s?.lastFrame
        val rs = MachineService.runnerState
        val blocked = MachineService.status.startsWith("BLOCKED")
        val (word, detail, col) = when {
            s == null || !s.connected -> Triple(if (blocked) S.stockApp else S.offline, if (blocked) S.stockAppUsing else S.waitingBoard, T.DANGER)
            f?.error != null -> Triple(S.attention, S.machineReports(S.boardError(f.error)), T.DANGER)
            s.running -> Triple(S.running, s.request?.let { r ->
                S.speedN(r.speedLevel) + (if (r.temperatureC > 0) " · ${r.temperatureC} °C" else "") + (if (r.reverse) " · ${S.reverse.lowercase(S.locale)}" else "")
            } ?: "", T.EMBER)
            rs?.phase == RecipeRunner.Phase.PAUSED -> Triple(S.paused, S.event(rs.event) ?: "", T.AMBER)
            MachineService.pausedRun != null -> Triple(S.paused, MachineService.pausedRun!!.request.let { r ->
                S.speedN(r.speedLevel) + (if (r.temperatureC > 0) " · ${r.temperatureC} °C" else "") + (if (r.reverse) " · ${S.reverse.lowercase(S.locale)}" else "")
            }, T.AMBER)
            f?.lidOpen == true -> Triple(S.lidOpen, S.closeLidToCook, T.AMBER)
            else -> Triple(S.ready, if (MachineService.armed) S.readyUnlocked else S.readyLocked, T.OK)
        }
        if (word != lastWord) { if (lastWord != null) Anim.swapText(statusWord, word) else statusWord.put(word); lastWord = word }
        statusWord.colors(T.lerpColor(col, T.TEXT, 0.35f), col)
        statusDetail.put(detail)

        if (s?.running == true && s.request != null) {
            val hot = (s.request?.temperatureC ?: 0) > 0
            statusRing.colorA = if (hot) T.AMBER else T.TEAL
            statusRing.colorB = if (hot) T.EMBER_HOT else T.TEAL_DEEP
            statusRing.value = fmtClock(s.remainingMillis); statusRing.unit = S.left
            statusRing.fraction = 1f - s.remainingMillis / s.request!!.durationMillis.toFloat()
        } else if (MachineService.pausedRun != null) {
            val p = MachineService.pausedRun!!
            statusRing.colorA = T.AMBER; statusRing.colorB = T.EMBER
            statusRing.value = fmtClock(p.request.durationMillis); statusRing.unit = S.left
            statusRing.fraction = 1f - p.request.durationMillis / p.totalMillis.toFloat()
        } else {
            val cal = java.util.Calendar.getInstance()
            statusRing.colorA = T.lerpColor(col, T.TEXT, 0.15f); statusRing.colorB = col
            statusRing.value = "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
            statusRing.unit = java.text.SimpleDateFormat("EEE d MMM", S.locale).format(cal.time)
            statusRing.fraction = cal.get(java.util.Calendar.SECOND) / 60f
        }

        val paused = MachineService.pausedRun != null && s?.running != true
        idleActions.visibility = if (paused) View.GONE else View.VISIBLE
        pausedActions.visibility = if (paused) View.VISIBLE else View.GONE
        if (paused) {
            val pm = MachineService.remotePermit
            remoteBtn.visibility = if (app.prefs.remoteStart || pm != null) View.VISIBLE else View.GONE
            remoteBtn.label = if (pm?.resume == true) S.remoteLeft(((pm.untilElapsed - android.os.SystemClock.elapsedRealtime()) / 60_000 + 1).toInt()) else S.remote
            if (pm?.resume == true) remoteBtn.style(T.TEAL, T.TEAL_DEEP) else remoteBtn.style(T.SURFACE_3, T.SURFACE_3)
        }

        val g = MachineService.displayGrams
        if (g != shownGrams) {
            val from = shownGrams; shownGrams = g
            if (g == null || from == null) weightBig.put(withUnit(g?.toString() ?: "—", "g")) else Anim.count(weightBig, from, g) { withUnit("$it", "g") }
        }
        weightSub.put(when {
            s?.running == true -> S.notWhileRunning
            s?.contentGrams != null -> S.foodInBowl(s.contentGrams!!)
            else -> S.setEmptyBowlHint
        })

        val t = f?.temperatureStepC ?: 0
        tempBig.put(if (f == null) "—" else if (t == 0) withUnit("≤37", "°C", prefixSmall = true) else withUnit("$t", "°C"))
        if (t == 0) tempBig.colors(T.TEXT, T.COLD_END) else tempBig.colors(T.lerpColor(T.TEXT, T.tempColor(t), 0.4f), T.tempColor(t))
        tempIcon.set(Icon.THERMO, if (t == 0) T.TEXT_2 else T.tempColor(t))
        tempSub.put(when {
            s?.running == true && (s.request?.temperatureC ?: 0) > 0 -> S.heatingTo(s.request!!.temperatureC)
            t >= 60 -> S.hotCareful
            t >= 40 -> S.warm
            else -> S.cool
        })
        heatBar.set(t, if (s?.running == true) s.request?.temperatureC ?: 0 else 0)

        val r = app.service?.recipe
        if (r != null && rs != null) {
            cookTitle.put(r.title)
            cookSub.put(if (rs.phase == RecipeRunner.Phase.DONE) S.finishedFor(r) else S.stepOfTap(rs.stepIndex + 1, r.steps.size))
            cookCta.label = S.continueLabel
            cookLabel.put(S.cooking.uppercase(S.locale))
            if (cookImageFor != r.id) { cookImageFor = r.id; ImageLoader.load(app.store, r.image, cookImage, T.dp(560)) }
        } else {
            cookTitle.put(S.whatCooking)
            cookSub.put(S.recipesTapBrowse(app.index?.visible(app.prefs.uiLanguage)?.size ?: 0))
            cookCta.label = S.browseRecipes
            cookLabel.put(S.navRecipes.uppercase(S.locale))
            val hero = app.index?.visible(app.prefs.uiLanguage)?.filter { it.image != null && !it.id.startsWith("program-") }
                ?.let { l -> if (l.isEmpty()) null else l[((System.currentTimeMillis() / 86_400_000L).toInt() and 0x7fffffff) % l.size] }
            if (hero != null && cookImageFor != hero.id) { cookImageFor = hero.id; ImageLoader.load(app.store, hero.image, cookImage, T.dp(560)) }
        }
    }

    private fun withUnit(n: String, unit: String, prefixSmall: Boolean = false): CharSequence {
        val sp = SpannableString("$n $unit")
        sp.setSpan(RelativeSizeSpan(0.42f), n.length, sp.length, 0)
        if (prefixSmall) sp.setSpan(RelativeSizeSpan(0.6f), 0, 1, 0)
        return sp
    }
}

/** Static bowl temperature scale (37–130 °C) with the current value and the target marked. Redraws only on change. */
class HeatBar(context: android.content.Context) : View(context) {
    private var temp = 0
    private var target = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = T.dpf(11.5f); color = T.TEXT_3 }
    fun set(t: Int, tgt: Int) { if (t != temp || tgt != target) { temp = t; target = tgt; invalidate() } }
    override fun onDraw(c: Canvas) {
        txt.typeface = T.semibold
        val h = T.dpf(8f); val y = T.dpf(4f); val w = width.toFloat()
        paint.shader = LinearGradient(0f, 0f, w, 0f, intArrayOf(T.SKY, T.AMBER, T.EMBER, T.EMBER_HOT), null, Shader.TileMode.CLAMP)
        paint.alpha = 0x55
        c.drawRoundRect(RectF(0f, y, w, y + h), h / 2, h / 2, paint)
        val f = ((temp - 37) / 93f).coerceIn(0f, 1f)
        paint.alpha = 255
        if (temp > 0) c.drawRoundRect(RectF(0f, y, (w * f).coerceAtLeast(h), y + h), h / 2, h / 2, paint)
        paint.shader = null
        if (target > 0) {
            val tx = w * ((target - 37) / 93f).coerceIn(0f, 1f)
            paint.color = T.TEXT; c.drawRect(tx - T.dpf(1.5f), y - T.dpf(3f), tx + T.dpf(1.5f), y + h + T.dpf(3f), paint)
        }
        c.drawText("37°", 0f, y + h + T.dpf(15f), txt)
        val m = "130°"; c.drawText(m, w - txt.measureText(m), y + h + T.dpf(15f), txt)
    }
}
