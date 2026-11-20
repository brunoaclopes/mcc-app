package mcc.app.ui

import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import mcc.app.BuildConfig
import mcc.app.MachineService

class SettingsScreen(app: AppUi) : Screen(app) {
    override val title get() = S.settingsTitle
    override val subtitle get() = S.settingsSubtitle

    private lateinit var repo: EditText
    private lateinit var host: EditText
    private lateinit var port: EditText
    private lateinit var user: EditText
    private lateinit var pass: EditText
    private lateinit var mqttSwitch: SwitchView
    private lateinit var mqttDot: Dot
    private lateinit var mqttStatus: TextView
    private lateinit var repoStatus: TextView
    private lateinit var machineInfo: TextView
    private val kv = LinkedHashMap<String, TextView>()
    private val cards = ArrayList<View>()

    override val view: View = build()

    private fun card(title: String, icon: Icon, color: Int) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL; background = T.glass(24); setPadding(T.dp(24), T.dp(20), T.dp(24), T.dp(20))
        val h = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        h.addView(IconView(c, icon, color), LinearLayout.LayoutParams(T.dp(22), T.dp(22)))
        h.addView(T.text(c, title, 19f, T.TEXT, T.heavy).apply { setPadding(T.dp(10), 0, 0, 0) })
        addView(h, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = T.dp(10) })
        cards.add(this)
    }

    private fun field(parent: LinearLayout, label: String, value: String, type: Int = InputType.TYPE_CLASS_TEXT, hint: String = ""): EditText {
        parent.addView(T.text(c, label, 13f, T.TEXT_2, T.bold).apply { setPadding(0, T.dp(8), 0, T.dp(4)) })
        val e = EditText(c).apply {
            setText(value); this.hint = hint; setHintTextColor(T.TEXT_3); setTextColor(T.TEXT); textSize = 16f
            isSingleLine = true; inputType = type; typeface = T.semibold; background = T.glass(14); setPadding(T.dp(14), 0, T.dp(14), 0)
        }
        parent.addView(e, LinearLayout.LayoutParams(-1, T.dp(46)))
        return e
    }

    private lateinit var themeSeg: LinearLayout
    private lateinit var loc: EditText
    private lateinit var area: EditText

    private fun saveLocation() {
        val p = app.prefs
        if (loc.text.toString().trim() == p.location) return
        p.location = loc.text.toString()
        if (p.location.isNotEmpty() && p.latLon == null) { app.notify(S.locationHint, Notice.WARN); return }
        mcc.app.Sun.where = p.latLon
        refreshTheme()
        app.setTheme(p.theme, force = true)
    }
    private lateinit var themeHelp: TextView

    /** Segmented control: the selected option is an amber pill. */
    private fun segment(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit): LinearLayout = LinearLayout(c).apply seg@{
        orientation = LinearLayout.HORIZONTAL; background = T.glass(26); setPadding(T.dp(4), T.dp(4), T.dp(4), T.dp(4))
        for ((code, name) in options) {
            addView(T.text(c, name, 16f, T.TEXT, T.heavy).apply {
                gravity = Gravity.CENTER; tag = code
                setOnClickListener { selectSegment(this@seg, code); onPick(code) }
            }, LinearLayout.LayoutParams(0, -1, 1f))
        }
        selectSegment(this, selected)
    }

    private fun selectSegment(seg: LinearLayout, code: String) {
        for (i in 0 until seg.childCount) {
            val v = seg.getChildAt(i) as TextView
            val sel = v.tag == code
            v.setTextColor(if (sel) T.ON_ACCENT else T.TEXT)
            v.background = if (sel) T.gradient(T.AMBER, T.EMBER, 22) else T.pressable(android.graphics.drawable.ColorDrawable(0))
        }
    }

    /** Called when the mode changed without changing the look (e.g. day -> auto during the day). */
    fun refreshTheme() {
        val p = app.prefs
        selectSegment(themeSeg, p.theme)
        val (rise, set) = mcc.app.Sun.times()
        themeHelp.text = S.themeHelp(p.theme == "auto", !T.dark, mcc.app.Sun.fmt(rise), mcc.app.Sun.fmt(set))
    }

    private fun scroll(content: View) = ScrollView(c).apply {
        // the bottom space lives inside the content, so the fade ends exactly at the screen edge (no hard line)
        isVerticalScrollBarEnabled = false; isVerticalFadingEdgeEnabled = true; setFadingEdgeLength(T.dp(28))
        content.setPadding(content.paddingLeft, content.paddingTop, content.paddingRight, T.dp(24))
        addView(content)
    }

    private fun build(): View {
        val p = app.prefs
        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; setPadding(T.dp(16), T.dp(4), T.dp(16), 0) }

        val left = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        // language
        val lc = card(S.language, Icon.GLOBE, T.AMBER)
        lc.addView(segment(listOf("pt" to "Português", "en" to "English"), p.uiLanguage) { app.setLanguage(it) }, LinearLayout.LayoutParams(-1, T.dp(54)))
        left.addView(lc)

        // appearance: auto (sun) / day / night
        val ac = card(S.appearance, Icon.SUN, T.AMBER)
        themeSeg = segment(listOf("auto" to S.themeAuto, "day" to S.themeDay, "night" to S.themeNight), p.theme) { app.setTheme(it) }
        ac.addView(themeSeg, LinearLayout.LayoutParams(-1, T.dp(54)))
        themeHelp = T.text(c, "", 13.5f, T.TEXT_2, T.semibold).apply { setPadding(0, T.dp(10), 0, 0); setLineSpacing(0f, 1.2f) }
        ac.addView(themeHelp)
        loc = field(ac, S.location, p.location, InputType.TYPE_CLASS_TEXT, S.locationHint)
        loc.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        loc.setOnEditorActionListener { _, _, _ -> saveLocation(); false }
        loc.setOnFocusChangeListener { _, has -> if (!has) saveLocation() }
        left.addView(ac, LinearLayout.LayoutParams(-1, -2).apply { topMargin = T.dp(16) })
        refreshTheme()

        val rc = card(S.recipeServer, Icon.BOOK, T.AMBER)
        rc.addView(T.text(c, S.recipeServerHelp, 14f, T.TEXT_2).apply { setLineSpacing(0f, 1.2f) })
        repo = field(rc, S.address, p.repoUrl, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, "http://nas.local:8088/")
        val rrow = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, T.dp(14), 0, 0) }
        rrow.addView(HoldButton(c).apply { label = S.saveSync; icon = Icon.REFRESH; holdMillis = 0; style(T.AMBER, T.EMBER)
            onConfirm = { p.repoUrl = repo.text.toString(); app.reloadIndex(true); app.notify(S.syncing) } },
            LinearLayout.LayoutParams(T.dp(250), T.dp(54)))
        repoStatus = T.text(c, "", 14f, T.TEXT_2, T.semibold).apply { setPadding(T.dp(14), 0, 0, 0) }
        rrow.addView(repoStatus, LinearLayout.LayoutParams(0, -2, 1f))
        rc.addView(rrow)
        left.addView(rc, LinearLayout.LayoutParams(-1, -2).apply { topMargin = T.dp(16) })

        val mc = card(S.machine, Icon.DIAL, T.TEAL)
        for (k in listOf(S.link, S.powerBoard, S.serialLoop, S.emptyBowl, S.app)) {
            val r = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, T.dp(5), 0, T.dp(5)) }
            r.addView(T.text(c, k, 14f, T.TEXT_2, T.semibold), LinearLayout.LayoutParams(T.dp(150), -2))
            val v = T.text(c, "", 14f, T.TEXT, T.bold); r.addView(v, LinearLayout.LayoutParams(0, -2, 1f)); kv[k] = v
            mc.addView(r)
        }
        machineInfo = T.text(c, "", 14f, T.TEXT_2, T.semibold).apply { setLineSpacing(0f, 1.35f); setPadding(0, T.dp(12), 0, 0) }
        mc.addView(machineInfo)
        val tr = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, T.dp(14), 0, T.dp(2)) }
        tr.addView(IconView(c, Icon.TURBO, T.TEAL), LinearLayout.LayoutParams(T.dp(20), T.dp(20)))
        tr.addView(T.text(c, S.turboSetting, 15f, T.TEXT, T.bold).apply { setPadding(T.dp(8), 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
        tr.addView(SwitchView(c).apply {
            set(p.turboEnabled)
            onChange = { on -> p.turboEnabled = on; app.service?.applyModes() }
        }, LinearLayout.LayoutParams(T.dp(60), T.dp(34)))
        mc.addView(tr)
        mc.addView(T.text(c, S.turboSettingHelp, 13f, T.TEXT_2, T.semibold).apply { setLineSpacing(0f, 1.2f) })
        val sm = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, T.dp(16), 0, T.dp(2)) }
        sm.addView(IconView(c, Icon.KNEAD, T.TEAL), LinearLayout.LayoutParams(T.dp(20), T.dp(20)))
        sm.addView(T.text(c, S.specialModesSetting, 15f, T.TEXT, T.bold).apply { setPadding(T.dp(8), 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
        sm.addView(SwitchView(c).apply { set(p.specialModes); onChange = { on -> p.specialModes = on; app.service?.applyModes() } }, LinearLayout.LayoutParams(T.dp(60), T.dp(34)))
        mc.addView(sm)
        mc.addView(T.text(c, S.specialModesHelp, 13f, T.TEXT_2, T.semibold).apply { setLineSpacing(0f, 1.2f) })
        val sb = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, T.dp(16), 0, T.dp(2)) }
        sb.addView(IconView(c, Icon.MOON, T.TEAL), LinearLayout.LayoutParams(T.dp(20), T.dp(20)))
        sb.addView(T.text(c, S.standbySetting, 15f, T.TEXT, T.bold).apply { setPadding(T.dp(8), 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
        sb.addView(SwitchView(c).apply { set(p.standbyEnabled); onChange = { on -> p.standbyEnabled = on } }, LinearLayout.LayoutParams(T.dp(60), T.dp(34)))
        mc.addView(sb)
        mc.addView(T.text(c, S.standbyHelp, 13f, T.TEXT_2, T.semibold).apply { setLineSpacing(0f, 1.2f) })
        mc.addView(T.text(c, S.standbyAfter, 13f, T.TEXT_2, T.bold).apply { setPadding(0, T.dp(10), 0, T.dp(4)) })
        mc.addView(segment(listOf(3, 7, 15, 30).map { "$it" to "$it min" }, "${p.standbyMinutes}") { p.standbyMinutes = it.toInt() },
            LinearLayout.LayoutParams(-1, T.dp(48)))
        left.addView(mc, LinearLayout.LayoutParams(-1, -2).apply { topMargin = T.dp(16) })
        // light and sound
        val lsc = card(S.lightSound, Icon.SPARK, T.AMBER)
        lsc.addView(T.text(c, S.soundSetting, 13f, T.TEXT_2, T.bold).apply { setPadding(0, T.dp(2), 0, T.dp(4)) })
        lsc.addView(segment(listOf("0" to S.soundOff, "1" to S.soundQuiet, "2" to S.soundNormal), "${p.soundLevel}") {
            p.soundLevel = it.toInt(); app.play(mcc.app.Sounds.Cue.DONE)
        }, LinearLayout.LayoutParams(-1, T.dp(48)))
        val la = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, T.dp(16), 0, T.dp(2)) }
        la.addView(T.text(c, S.ledAnimSetting, 15f, T.TEXT, T.bold), LinearLayout.LayoutParams(0, -2, 1f))
        la.addView(SwitchView(c).apply { set(p.ledAnimated); onChange = { on -> p.ledAnimated = on } }, LinearLayout.LayoutParams(T.dp(60), T.dp(34)))
        lsc.addView(la)
        lsc.addView(T.text(c, S.ledAnimHelp, 13f, T.TEXT_2, T.semibold).apply { setLineSpacing(0f, 1.2f) })
        lsc.addView(T.text(c, S.ledBrightness, 13f, T.TEXT_2, T.bold).apply { setPadding(0, T.dp(10), 0, T.dp(4)) })
        lsc.addView(segment(listOf(25, 50, 100).map { "$it" to "$it %" }, "${p.ledBrightness}") { p.ledBrightness = it.toInt() },
            LinearLayout.LayoutParams(-1, T.dp(48)))
        left.addView(lsc, LinearLayout.LayoutParams(-1, -2).apply { topMargin = T.dp(16) })
        row.addView(scroll(left), LinearLayout.LayoutParams(0, -1, 1f))

        val hc = card(S.homeAssistant, Icon.WIFI, T.TEAL)
        val sw = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, T.dp(2), 0, T.dp(4)) }
        mqttDot = Dot(c).apply { pulsing = false }
        sw.addView(mqttDot, LinearLayout.LayoutParams(T.dp(14), T.dp(14)))
        mqttStatus = T.text(c, "", 15f, T.TEXT, T.bold).apply { setPadding(T.dp(8), 0, 0, 0) }
        sw.addView(mqttStatus, LinearLayout.LayoutParams(0, -2, 1f))
        mqttSwitch = SwitchView(c).apply { set(p.mqttEnabled) }
        sw.addView(mqttSwitch, LinearLayout.LayoutParams(T.dp(60), T.dp(34)))
        hc.addView(sw)
        host = field(hc, S.broker, p.mqttHost)
        port = field(hc, S.port, p.mqttPort.toString(), InputType.TYPE_CLASS_NUMBER)
        user = field(hc, S.user, p.mqttUser)
        area = field(hc, S.haArea, p.haArea, InputType.TYPE_CLASS_TEXT, S.haAreaHint)
        pass = field(hc, S.password, p.mqttPassword, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, S.passwordHint)
        hc.addView(HoldButton(c).apply { label = S.save; icon = Icon.CHECK; holdMillis = 0; style(T.AMBER, T.EMBER)
            onConfirm = {
                p.mqttEnabled = mqttSwitch.checked; p.mqttHost = host.text.toString(); p.mqttPort = port.text.toString().toIntOrNull() ?: 1883
                p.mqttUser = user.text.toString(); p.mqttPassword = pass.text.toString(); p.haArea = area.text.toString()
                app.service?.restartMqtt(); app.notify(S.haSaved, Notice.OK)
            } }, LinearLayout.LayoutParams(-1, T.dp(54)).apply { topMargin = T.dp(14) })
        hc.addView(T.text(c, S.haHelp, 13.5f, T.TEXT_2, T.semibold).apply { setPadding(0, T.dp(12), 0, 0); setLineSpacing(0f, 1.2f) })
        val rr = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, T.dp(18), 0, T.dp(4)) }
        rr.addView(IconView(c, Icon.WIFI, T.TEAL), LinearLayout.LayoutParams(T.dp(20), T.dp(20)))
        rr.addView(T.text(c, S.remoteSetting, 15f, T.TEXT, T.bold).apply { setPadding(T.dp(8), 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
        rr.addView(SwitchView(c).apply {
            set(p.remoteStart)
            onChange = { on -> p.remoteStart = on; app.service?.applyModes(); if (!on) app.service?.cancelRemote("switched off") }
        }, LinearLayout.LayoutParams(T.dp(60), T.dp(34)))
        hc.addView(rr)
        hc.addView(T.text(c, S.remoteSettingHelp, 13f, T.TEXT_2, T.semibold).apply { setLineSpacing(0f, 1.2f) })
        hc.addView(T.text(c, S.remoteWindow, 13f, T.TEXT_2, T.bold).apply { setPadding(0, T.dp(10), 0, T.dp(4)) })
        hc.addView(segment(listOf(15, 30, 60, 120).map { "$it" to "$it min" }, "${p.remoteMinutes}") { p.remoteMinutes = it.toInt() },
            LinearLayout.LayoutParams(-1, T.dp(48)))
        val rl = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, T.dp(18), 0, T.dp(4)) }
        rl.addView(IconView(c, Icon.BOOK, T.TEAL), LinearLayout.LayoutParams(T.dp(20), T.dp(20)))
        rl.addView(T.text(c, S.remoteLoadSetting, 15f, T.TEXT, T.bold).apply { setPadding(T.dp(8), 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
        rl.addView(SwitchView(c).apply { set(p.remoteLoad); onChange = { on -> p.remoteLoad = on } }, LinearLayout.LayoutParams(T.dp(60), T.dp(34)))
        hc.addView(rl)
        hc.addView(T.text(c, S.remoteLoadHelp, 13f, T.TEXT_2, T.semibold).apply { setLineSpacing(0f, 1.2f) })
        val ac2 = card(S.aiTitle, Icon.SPARK, T.AMBER)
        val aiUrl = field(ac2, S.aiAddress, p.aiUrl, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, S.aiAddressHint)
        val aiModel = field(ac2, S.aiModel, p.aiModel, InputType.TYPE_CLASS_TEXT, S.aiModelHint)
        val aiKey = field(ac2, S.aiKey, p.aiKey, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, S.aiKeyHint)
        ac2.addView(HoldButton(c).apply { label = S.aiTest; icon = Icon.CHECK; holdMillis = 0; style(T.AMBER, T.EMBER)
            onConfirm = {
                p.aiUrl = aiUrl.text.toString(); p.aiModel = aiModel.text.toString(); p.aiKey = aiKey.text.toString()
                if (p.aiUrl.isBlank()) app.notify(S.aiSaved, Notice.OK) else Thread {
                    val r = runCatching { mcc.app.AiClient(p).models() }
                    view.post { r.fold({ app.notify(S.aiOk(it.size), Notice.OK) }, { app.notify(S.aiFailed(it.message ?: ""), Notice.ERROR) }) }
                }.start()
            } }, LinearLayout.LayoutParams(-1, T.dp(54)).apply { topMargin = T.dp(14) })
        ac2.addView(T.text(c, S.aiHelp, 13.5f, T.TEXT_2, T.semibold).apply { setPadding(0, T.dp(12), 0, 0); setLineSpacing(0f, 1.2f) })
        val rightCol = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        rightCol.addView(hc)
        rightCol.addView(ac2, LinearLayout.LayoutParams(-1, -2).apply { topMargin = T.dp(16) })
        row.addView(scroll(rightCol), LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = T.dp(16) })
        return row
    }

    override fun onShow() { Anim.stagger(cards, step = 60) }

    override fun render() {
        val st = app.service?.mqttStatus ?: "off"
        mqttStatus.put(when { st == "off" -> S.notConnected; st == "connected" -> S.connectedWord; st.startsWith("connecting") -> S.connecting; else -> st })
        mqttDot.color = when { st == "connected" -> T.OK; st == "off" -> T.TEXT_3; else -> T.AMBER }
        repoStatus.put(S.recipesAvailable(app.index?.visible("all", app.prefs.uiLanguage)?.size ?: 0))
        val s = snap
        kv[S.link]?.put(if (s?.connected == true) S.connectedWord else if (MachineService.status.startsWith("BLOCKED")) S.stockApp else S.offline)
        kv[S.powerBoard]?.put(S.firmware(s?.lastFrame?.firmwareVersion?.toString() ?: "—"))
        kv[S.serialLoop]?.put(MachineService.loopTiming?.let { (a, m, l) -> S.loopStats(a, m, l) } ?: "—")
        kv[S.emptyBowl]?.put(if (s?.hasEmptyReference == true) S.saved else S.notSet)
        kv[S.app]?.put("mcc-app ${BuildConfig.VERSION_NAME}")
        machineInfo.put(buildString {
            append(S.safetyTitle).append('\n')
            S.safetyLines.forEach { append("• ").append(it).append('\n') }
            append('\n').append(S.fontCredit)
        })
    }
}
