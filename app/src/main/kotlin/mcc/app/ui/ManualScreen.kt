package mcc.app.ui

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import mcc.app.MachineService
import mcc.machine.RunRequest
import mcc.machine.TemperatureTable

/** Manual control: time / speed / temperature dials, reverse, scale helpers, hold-to-start. */
class ManualScreen(app: AppUi) : Screen(app) {
    override val title get() = S.navManual
    override val subtitle: String get() = if (snap?.running == true) S.runningTapStop else S.manualSubtitle

    private val timeSteps: List<Int> = (5..55 step 5) + (60..285 step 15) + (300..5940 step 60)
    private var timeIdx = timeSteps.indexOf(60)
    private var speed = 2
    private var tempLevel = 0
    private var reverse = false
    private var selected = 0

    private val dials = ArrayList<DialView>()
    private val columns = ArrayList<View>()
    private lateinit var timeDial: DialView
    private lateinit var speedDial: DialView
    private lateinit var tempDial: DialView
    private lateinit var reverseChip: LinearLayout
    private lateinit var reverseIcon: IconView
    private lateinit var info: TextView
    private lateinit var startBtn: HoldButton
    private lateinit var turboChip: LinearLayout
    private lateinit var turboIcon: IconView
    private var turboHeld = false
    private lateinit var remoteChip: LinearLayout
    private lateinit var remoteText: TextView
    private var shownRemote: Boolean? = null
    private var shownTurbo: Boolean? = null
    private var wasRunning = false
    private var wasPaused = false
    private var shownReverse: Boolean? = null

    override val view: View = build()

    private fun build(): View {
        val page = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(T.dp(16), T.dp(4), T.dp(16), T.dp(14)) }
        val dialRow = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        timeDial = dialColumn(dialRow, 0, S.time, T.lerpColor(T.TEXT, T.TEXT_3, 0.25f), T.TEXT, { step(0, -1) }, { step(0, +1) })
        speedDial = dialColumn(dialRow, 1, S.speed, T.TEAL, T.TEAL_DEEP, { step(1, -1) }, { step(1, +1) })
        tempDial = dialColumn(dialRow, 2, S.temperature, T.AMBER, T.EMBER_HOT, { step(2, -1) }, { step(2, +1) })
        page.addView(dialRow, LinearLayout.LayoutParams(-1, 0, 1f))

        info = T.text(c, "", 15f, T.TEXT_2, T.semibold).apply { gravity = Gravity.CENTER; setPadding(0, T.dp(4), 0, T.dp(10)); maxLines = 2 }
        info.setOnClickListener { if (tempLevel > 0 && snap?.hasEmptyReference == false) app.askEmptyBowl() }
        page.addView(info, LinearLayout.LayoutParams(-1, -2))

        val bottom = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        reverseChip = actionChip(Icon.REVERSE, S.reverse) { toggleReverse() }
        reverseIcon = reverseChip.getChildAt(0) as IconView
        bottom.addView(reverseChip, chipLp(false))
        bottom.addView(actionChip(Icon.REFRESH, S.tare) {
            val e = app.service?.tare(); app.notify(e ?: S.scaleZeroed, if (e == null) Notice.OK else Notice.WARN)
        }, chipLp(true))
        turboChip = actionChip(Icon.TURBO, S.turbo) {}
        turboIcon = turboChip.getChildAt(0) as IconView
        turboChip.setOnTouchListener { v, e -> turboTouch(v, e) }
        bottom.addView(turboChip, chipLp(true))
        remoteChip = actionChip(Icon.WIFI, S.remote) { toggleRemote() }
        remoteText = remoteChip.getChildAt(1) as TextView
        bottom.addView(remoteChip, chipLp(true))
        startBtn = HoldButton(c).apply { onConfirm = { startOrStop() } }
        bottom.addView(startBtn, LinearLayout.LayoutParams(0, T.dp(64), 1f).apply { leftMargin = T.dp(12) })
        page.addView(bottom, LinearLayout.LayoutParams(-1, -2))
        refreshDials(false)
        return page
    }

    private fun dialColumn(row: LinearLayout, i: Int, label: String, a: Int, b: Int, minus: () -> Unit, plus: () -> Unit): DialView {
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(T.dp(12), 0, T.dp(12), 0) }
        val d = DialView(c).apply {
            this.label = label; colorA = a; colorB = b
            onTap = { select(i) }
            onDrag = { f -> select(i); dragTo(i, f) }
        }
        dials.add(d)
        d.reserveBelow = T.dp(66) // room for the − / + buttons; the dial and its buttons sit together, centred
        col.addView(d, LinearLayout.LayoutParams(-1, -2))
        val btns = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        btns.addView(roundBtn("−", minus), LinearLayout.LayoutParams(T.dp(68), T.dp(52)))
        btns.addView(roundBtn("+", plus), LinearLayout.LayoutParams(T.dp(68), T.dp(52)).apply { leftMargin = T.dp(16) })
        col.addView(btns, LinearLayout.LayoutParams(-1, -2).apply { topMargin = T.dp(12) })
        row.addView(col, LinearLayout.LayoutParams(0, -1, 1f))
        columns.add(col)
        return d
    }

    private fun roundBtn(s: String, onClick: () -> Unit) = T.text(c, s, 26f, T.TEXT, T.bold).apply {
        gravity = Gravity.CENTER; background = T.pressable(T.glass(26))
        setOnClickListener { onClick() }
        T.press(this)
    }

    private fun chipLp(gap: Boolean) = LinearLayout.LayoutParams(T.dp(84), T.dp(64)).apply { if (gap) leftMargin = T.dp(8) }

    /** Compact square-ish chip: icon over a short label. */
    private fun actionChip(icon: Icon, label: String, onClick: () -> Unit) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        background = T.pressable(T.glass(18)); setPadding(T.dp(4), T.dp(4), T.dp(4), T.dp(4))
        addView(IconView(c, icon, T.TEXT_2), LinearLayout.LayoutParams(T.dp(22), T.dp(22)))
        addView(T.text(c, label, 13f, T.TEXT, T.bold).apply { gravity = Gravity.CENTER; maxLines = 1; setPadding(0, T.dp(4), 0, 0) }, LinearLayout.LayoutParams(-1, -2))
        setOnClickListener { onClick() }
        T.press(this)
    }

    // ------------------------------------------------------------------ model

    private val seconds get() = timeSteps[timeIdx]
    private val tempC get() = TemperatureTable.celsius(tempLevel)
    private val running get() = snap?.running == true

    fun preset(sec: Int, spd: Int, temp: Int, rev: Boolean) {
        timeIdx = timeSteps.indexOfFirst { it >= sec }.let { if (it < 0) timeSteps.lastIndex else it }
        tempLevel = TemperatureTable.levelAtMost(temp)
        reverse = rev
        speed = spd
        clampSpeed(false)
        refreshDials(true)
    }

    private fun select(i: Int) { selected = i; dials.forEachIndexed { k, d -> d.active = k == i } }

    private fun step(i: Int, dir: Int) {
        if (running) return
        select(i)
        when (i) {
            0 -> timeIdx = (timeIdx + dir).coerceIn(0, timeSteps.lastIndex)
            1 -> speed = (speed + dir).coerceIn(0, maxSpeed())
            2 -> { tempLevel = (tempLevel + dir).coerceIn(0, TemperatureTable.MAX_LEVEL); clampSpeed(true) }
        }
        refreshDials(true)
    }

    private fun dragTo(i: Int, f: Float) {
        if (running) return
        when (i) {
            0 -> timeIdx = (f * timeSteps.lastIndex).toInt().coerceIn(0, timeSteps.lastIndex)
            1 -> speed = (f * 10 + 0.5f).toInt().coerceIn(0, maxSpeed())
            2 -> { tempLevel = (f * TemperatureTable.MAX_LEVEL + 0.5f).toInt(); clampSpeed(true) }
        }
        refreshDials(false)
    }

    private fun maxSpeed() = if (tempLevel > 0 || reverse) 3 else 10

    private fun clampSpeed(tell: Boolean) {
        if (speed > maxSpeed()) {
            speed = maxSpeed()
            if (tell) app.notify(if (reverse) S.reverseLimit else S.heatLimit, Notice.INFO)
        }
    }

    private fun toggleReverse() {
        if (running) return
        reverse = !reverse
        clampSpeed(true)
        refreshDials(true)
    }

    /** Turbo is a dead-man button, as on the stock app: full speed while held (at most 10 s), stops on release. */
    private fun turboTouch(v: View, e: android.view.MotionEvent): Boolean {
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                v.parent?.requestDisallowInterceptTouchEvent(true)
                val s = app.service ?: return true
                when {
                    running -> return true
                    !MachineService.armed -> { app.requestUnlock(); return true }
                }
                val err = s.turboStart()
                if (err != null) app.notify(err, Notice.WARN) else { turboHeld = true; v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY) }
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                if (turboHeld) { turboHeld = false; app.service?.turboEnd() }
            }
        }
        return true
    }

    override fun onHide() { if (turboHeld) { turboHeld = false; app.service?.turboEnd() } }

    private fun toggleRemote() {
        val s = app.service ?: return
        if (MachineService.remotePermit != null) { s.cancelRemote("cancelled at the machine"); app.notify(S.remoteCancelled); return }
        if (!MachineService.armed) { app.requestUnlock(); return }
        val what = describe()
        app.confirm(S.remoteTitle, S.remoteBody(what, app.prefs.remoteMinutes), S.remoteAllow) {
            val e = s.allowRemote(RunRequest(speed, tempC, seconds * 1000L, reverse), what)
            app.notify(e ?: S.remoteArmed(app.prefs.remoteMinutes), if (e == null) Notice.OK else Notice.WARN)
        }
    }

    private fun startOrStop() {
        val s = app.service ?: return
        if (running) { s.stop(); return }
        if (!MachineService.armed) { app.requestUnlock(); return }
        if (MachineService.pausedRun != null) { s.resumeManual()?.let { app.notify(it, Notice.WARN) }; return }
        if (tempLevel > 0 && snap?.hasEmptyReference == false) { app.askEmptyBowl(S.emptyBowlNeeded); return }
        val e = s.startManual(RunRequest(speed, tempC, seconds * 1000L, reverse))
        if (e != null) app.notify(e, Notice.WARN)
    }

    // ------------------------------------------------------------------ render

    private fun refreshDials(animate: Boolean) {
        val sec = seconds
        timeDial.value = if (sec < 60) "$sec" else if (sec % 60 == 0 && sec >= 600) "${sec / 60}" else "%d:%02d".format(sec / 60, sec % 60)
        timeDial.unit = if (sec < 60) S.seconds else S.minutes
        timeDial.setFraction(timeIdx / timeSteps.lastIndex.toFloat(), animate)
        speedDial.value = "$speed"
        speedDial.unit = if (speed == 0) S.stirringOff else if (reverse) "${S.reverse.lowercase(S.locale)} · ${S.ofMax(maxSpeed())}" else S.ofMax(maxSpeed())
        speedDial.setFraction(speed / 10f, animate)
        tempDial.value = if (tempLevel == 0) S.off else "$tempC"
        tempDial.unit = if (tempLevel == 0) S.noHeat else "°C"
        tempDial.setFraction(tempLevel / TemperatureTable.MAX_LEVEL.toFloat(), animate)
    }

    override fun onShow() { select(selected); refreshDials(false); Anim.stagger(columns, step = 70) }

    override fun render() {
        val s = snap
        val f = s?.lastFrame
        val run = s?.running == true
        val req = s?.request
        val paused = if (run) null else MachineService.pausedRun
        if (paused != null) {
            // a paused run: show what it will continue with; PARAR (top right) ends it
            val p = paused.request
            timeDial.value = fmtClock(p.durationMillis); timeDial.unit = S.paused.lowercase(S.locale)
            timeDial.progress = 1f - p.durationMillis / paused.totalMillis.toFloat()
            speedDial.value = "${p.speedLevel}"; speedDial.unit = if (p.reverse) S.reverse.lowercase(S.locale) else S.speed.lowercase(S.locale)
            speedDial.setFraction(p.speedLevel / 10f)
            tempDial.value = if (p.temperatureC == 0) S.off else "${p.temperatureC}"
            tempDial.unit = if (p.temperatureC == 0) S.noHeat else "°C ${S.target}"
            dials.forEach { it.interactive = false }
        } else if (run && req != null) {
            timeDial.value = fmtClock(s.remainingMillis); timeDial.unit = S.remaining
            timeDial.progress = 1f - s.remainingMillis / req.durationMillis.toFloat()
            speedDial.value = "${req.speedLevel}"; speedDial.unit = if (req.reverse) S.reverse.lowercase(S.locale) else S.speed.lowercase(S.locale)
            speedDial.setFraction(req.speedLevel / 10f)
            tempDial.value = if (req.temperatureC == 0) S.off else "${req.temperatureC}"
            tempDial.unit = if (req.temperatureC == 0) S.noHeat else "°C ${S.target}"
            dials.forEach { it.interactive = false }
        } else {
            if (wasRunning || wasPaused) refreshDials(true)
            timeDial.progress = null
            dials.forEach { it.interactive = true }
        }
        wasRunning = run
        wasPaused = paused != null
        val now = f?.temperatureStepC ?: 0
        tempDial.sub = if (f == null) "" else if (now == 0) S.bowlBelow37 else S.bowlTemp(now)
        tempDial.colorA = if (now >= 60) T.EMBER else T.AMBER

        if (reverse != shownReverse) {
            reverseChip.background = if (reverse) T.pressable(T.round(T.withAlpha(T.TEAL, 0x30), 20, T.TEAL, 2)) else T.pressable(T.glass(20))
            shownReverse = reverse
        }
        reverseIcon.set(Icon.REVERSE, if (reverse) T.TEAL else T.TEXT_2)
        val turboOn = run && req?.mode == mcc.machine.RunMode.TURBO
        if (turboOn != shownTurbo) {
            turboChip.background = if (turboOn) T.pressable(T.gradient(T.TEAL, T.TEAL_DEEP, 20)) else T.pressable(T.glass(20))
            shownTurbo = turboOn
        }
        turboIcon.set(Icon.TURBO, if (turboOn) T.ON_PHOTO else T.TEAL)
        turboChip.alpha = if (run && !turboOn) 0.45f else 1f
        val pm = MachineService.remotePermit
        remoteChip.visibility = if (app.prefs.remoteStart || pm != null) View.VISIBLE else View.GONE
        val manualPermit = pm != null && pm.manual != null
        if (manualPermit != shownRemote) {
            remoteChip.background = if (manualPermit) T.pressable(T.round(T.withAlpha(T.TEAL, 0x30), 20, T.TEAL, 2)) else T.pressable(T.glass(20))
            shownRemote = manualPermit
        }
        remoteText.put(if (pm != null && manualPermit) S.remoteLeft(((pm.untilElapsed - android.os.SystemClock.elapsedRealtime()) / 60_000 + 1).toInt()) else S.remote)

        val armed = MachineService.armed
        when {
            run -> { startBtn.label = S.stop; startBtn.icon = Icon.STOP; startBtn.holdMillis = 0; startBtn.style(T.DANGER, T.DANGER_DEEP); startBtn.isEnabled = true }
            paused != null && armed -> { startBtn.label = S.continueLabel; startBtn.icon = Icon.PLAY; startBtn.holdMillis = 0; startBtn.style(T.EMBER, T.EMBER_HOT)
                startBtn.isEnabled = s?.connected == true && f?.lidOpen != true }
            !armed -> { startBtn.label = S.unlock; startBtn.icon = Icon.LOCK; startBtn.holdMillis = 0; startBtn.style(T.SURFACE_3, T.SURFACE_3); startBtn.isEnabled = true }
            else -> {
                val content = s?.contentGrams
                val needMore = tempLevel > 0 && s?.hasEmptyReference == true && content != null && content < 200
                startBtn.label = if (needMore) S.heatAdd(content ?: 0) else S.holdToStart
                startBtn.icon = Icon.PLAY; startBtn.holdMillis = 900
                if (tempLevel > 0) startBtn.style(T.AMBER, T.EMBER_HOT) else startBtn.style(T.TEAL, T.TEAL_DEEP)
                startBtn.isEnabled = s?.connected == true && f?.lidOpen != true && !needMore
            }
        }

        info.put(when {
            s == null || !s.connected -> S.waitingMachine
            f?.lidOpen == true -> S.closeLidToStart
            f?.error != null -> S.machineReports(S.boardError(f.error))
            run -> S.runningAt(s.request?.speedLevel ?: 0)
            paused != null -> S.manualPausedInfo
            !armed -> S.unlockToStart
            tempLevel > 0 && !s.hasEmptyReference -> S.heatNeedsReference
            tempLevel > 0 && (s.contentGrams ?: 0) < 200 -> S.heatNeedsContent(s.contentGrams ?: 0)
            else -> S.pressHold(describe())
        })
        info.setTextColor(if (f?.lidOpen == true || f?.error != null) T.AMBER else T.TEXT_2)
    }

    private fun describe(): String = buildString {
        append(S.duration(seconds)); append(" · ${S.speed.lowercase(S.locale)} $speed")
        if (reverse) append(" · ${S.reverse.lowercase(S.locale)}")
        if (tempLevel > 0) append(" · $tempC °C")
    }

    override fun onKnob(keyCode: Int): Boolean {
        when (keyCode) {
            136 -> step(selected, +1)
            137 -> step(selected, -1)
            else -> select((selected + 1) % 3)
        }
        return true
    }
}
