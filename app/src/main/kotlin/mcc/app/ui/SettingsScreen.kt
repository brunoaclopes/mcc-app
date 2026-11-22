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
    private val folded = HashSet<View>()

    override val view: View = build()

    /** A glass card whose title row folds it open or shut; [key] remembers the choice. */
    private fun card(key: String, title: String, icon: Icon, color: Int) = LinearLayout(c).apply card@{
        orientation = LinearLayout.VERTICAL; background = T.glass(24); setPadding(T.dp(24), T.dp(20), T.dp(24), T.dp(20))
        val h = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        h.addView(IconView(c, icon, color), LinearLayout.LayoutParams(T.dp(22), T.dp(22)))
        h.addView(T.text(c, title, 19f, T.TEXT, T.heavy).apply { setPadding(T.dp(10), 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
        val chevron = IconView(c, Icon.DOWN, T.TEXT_2)
        h.addView(chevron, LinearLayout.LayoutParams(T.dp(22), T.dp(22)))
        val hp = LinearLayout.LayoutParams(-1, T.dp(34)).apply { bottomMargin = T.dp(10) }
        addView(h, hp)
        h.background = T.pressable(android.graphics.drawable.ColorDrawable(0))
        h.setOnClickListener {
            val shut = key !in app.prefs.collapsed
            app.prefs.collapsed = if (shut) app.prefs.collapsed + key else app.prefs.collapsed - key
            fold(this@card, shut, animate = true)
            app.play(mcc.app.Sounds.Cue.TAP)
        }
        tag = key
        cards.add(this)
    }

    /** Hides or shows everything below the title row; views hidden for their own reasons stay hidden. */
    private fun fold(card: LinearLayout, shut: Boolean, animate: Boolean) {
        val h = card.getChildAt(0)
        (h.layoutParams as LinearLayout.LayoutParams).bottomMargin = if (shut) 0 else T.dp(10)
        h.layoutParams = h.layoutParams
        for (i in 1 until card.childCount) {
            val v = card.getChildAt(i)
            if (shut) { if (v.visibility == View.VISIBLE) { v.visibility = View.GONE; folded.add(v) } }
            else if (folded.remove(v)) v.visibility = View.VISIBLE
        }
        val chevron = (h as LinearLayout).getChildAt(2)
        val angle = if (shut) -90f else 0f
        if (animate) chevron.animate().rotation(angle).setDuration(180).start() else chevron.rotation = angle
    }

    /** True while a view is hidden only because its card is folded. */
    private fun foldedAway(v: View) = v in folded

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

    private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

    /** Small bold label above a control. */
    private fun label(parent: LinearLayout, text: String, top: Int = 10) =
        T.text(c, text, 13f, T.TEXT_2, T.bold).apply { setPadding(0, T.dp(top), 0, T.dp(4)) }.also { parent.addView(it) }

    private fun help(parent: LinearLayout, text: String) =
        T.text(c, text, 13f, T.TEXT_2, T.semibold).apply { setLineSpacing(0f, 1.2f) }.also { parent.addView(it) }

    /** Icon, title and switch on one line, a short explanation below. */
    private fun toggle(parent: LinearLayout, icon: Icon, color: Int, title: String, helpText: String, on: Boolean, first: Boolean = false,
                       onChange: (Boolean) -> Unit): SwitchView {
        val r = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, T.dp(if (first) 2 else 18), 0, T.dp(2)) }
        r.addView(IconView(c, icon, color), lp(T.dp(20), T.dp(20)))
        r.addView(T.text(c, title, 15f, T.TEXT, T.bold).apply { setPadding(T.dp(8), 0, 0, 0) }, lp(0, -2, 1f))
        val sw = SwitchView(c).apply { set(on); this.onChange = onChange }
        r.addView(sw, lp(T.dp(60), T.dp(34)))
        parent.addView(r)
        help(parent, helpText)
        return sw
    }

    private fun column(vararg views: View) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        views.forEachIndexed { i, v -> addView(v, lp(-1, -2).apply { if (i > 0) topMargin = T.dp(16) }) }
    }

    private fun build(): View {
        val p = app.prefs

        // ---- left: how the machine looks and behaves ----
        val lc = card("language", S.language, Icon.GLOBE, T.AMBER)
        lc.addView(segment(listOf("pt" to "Português", "en" to "English"), p.uiLanguage) { app.setLanguage(it) }, lp(-1, T.dp(54)))

        val ac = card("appearance", S.appearance, Icon.SUN, T.AMBER)
        themeSeg = segment(listOf("auto" to S.themeAuto, "day" to S.themeDay, "night" to S.themeNight), p.theme) { app.setTheme(it) }
        ac.addView(themeSeg, lp(-1, T.dp(54)))
        themeHelp = T.text(c, "", 13.5f, T.TEXT_2, T.semibold).apply { setPadding(0, T.dp(10), 0, 0); setLineSpacing(0f, 1.2f) }
        ac.addView(themeHelp)
        loc = field(ac, S.location, p.location, InputType.TYPE_CLASS_TEXT, S.locationHint)
        loc.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        loc.setOnEditorActionListener { _, _, _ -> saveLocation(); false }
        loc.setOnFocusChangeListener { _, has -> if (!has) saveLocation() }
        refreshTheme()

        val mc = card("machine", S.machine, Icon.DIAL, T.TEAL)
        lateinit var after: List<View>
        toggle(mc, Icon.MOON, T.TEAL, S.standbySetting, S.standbyHelp, p.standbyEnabled, first = true) { on ->
            p.standbyEnabled = on; after.forEach { if (!foldedAway(it)) it.visibility = if (on) View.VISIBLE else View.GONE }
        }
        val afterLabel = label(mc, S.standbyAfter)
        val afterSeg = segment(listOf(3, 7, 15, 30).map { "$it" to "$it min" }, "${p.standbyMinutes}") { p.standbyMinutes = it.toInt() }
        mc.addView(afterSeg, lp(-1, T.dp(48)))
        after = listOf(afterLabel, afterSeg)
        if (!p.standbyEnabled) after.forEach { it.visibility = View.GONE }

        val lsc = card("light", S.lightSound, Icon.SPARK, T.AMBER)
        label(lsc, S.soundSetting, top = 2)
        lsc.addView(segment(listOf("0" to S.soundOff, "1" to S.soundQuiet, "2" to S.soundNormal), "${p.soundLevel}") {
            p.soundLevel = it.toInt(); app.play(mcc.app.Sounds.Cue.DONE)
        }, lp(-1, T.dp(48)))
        toggle(lsc, Icon.SPARK, T.AMBER, S.ledAnimSetting, S.ledAnimHelp, p.ledAnimated) { p.ledAnimated = it }
        label(lsc, S.ledBrightness)
        lsc.addView(segment(listOf(25, 50, 100).map { "$it" to "$it %" }, "${p.ledBrightness}") { p.ledBrightness = it.toInt() }, lp(-1, T.dp(48)))

        // ---- right: connections, then status ----
        val rc = card("recipes", S.recipeServer, Icon.BOOK, T.AMBER)
        help(rc, S.recipeServerHelp)
        repo = field(rc, S.address, p.repoUrl, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, "http://nas.local:8088/")
        val rrow = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, T.dp(14), 0, 0) }
        rrow.addView(HoldButton(c).apply { label = S.saveSync; icon = Icon.REFRESH; holdMillis = 0; style(T.AMBER, T.EMBER)
            onConfirm = { p.repoUrl = repo.text.toString(); app.reloadIndex(true); app.notify(S.syncing) } }, lp(T.dp(250), T.dp(54)))
        repoStatus = T.text(c, "", 14f, T.TEXT_2, T.semibold).apply { setPadding(T.dp(14), 0, 0, 0) }
        rrow.addView(repoStatus, lp(0, -2, 1f))
        rc.addView(rrow)

        val hc = card("ha", S.homeAssistant, Icon.WIFI, T.TEAL)
        val sw = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, T.dp(2), 0, T.dp(4)) }
        mqttDot = Dot(c).apply { pulsing = false }
        sw.addView(mqttDot, lp(T.dp(14), T.dp(14)))
        mqttStatus = T.text(c, "", 15f, T.TEXT, T.bold).apply { setPadding(T.dp(8), 0, 0, 0) }
        sw.addView(mqttStatus, lp(0, -2, 1f))
        mqttSwitch = SwitchView(c).apply { set(p.mqttEnabled) }
        sw.addView(mqttSwitch, lp(T.dp(60), T.dp(34)))
        hc.addView(sw)
        host = field(hc, S.broker, p.mqttHost)
        port = field(hc, S.port, p.mqttPort.toString(), InputType.TYPE_CLASS_NUMBER)
        user = field(hc, S.user, p.mqttUser)
        pass = field(hc, S.password, p.mqttPassword, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, S.passwordHint)
        area = field(hc, S.haArea, p.haArea, InputType.TYPE_CLASS_TEXT, S.haAreaHint)
        hc.addView(HoldButton(c).apply { label = S.save; icon = Icon.CHECK; holdMillis = 0; style(T.AMBER, T.EMBER)
            onConfirm = {
                p.mqttEnabled = mqttSwitch.checked; p.mqttHost = host.text.toString(); p.mqttPort = port.text.toString().toIntOrNull() ?: 1883
                p.mqttUser = user.text.toString(); p.mqttPassword = pass.text.toString(); p.haArea = area.text.toString()
                app.service?.restartMqtt(); app.notify(S.haSaved, Notice.OK)
            } }, lp(-1, T.dp(54)).apply { topMargin = T.dp(14) })
        hc.addView(T.text(c, S.haHelp, 13.5f, T.TEXT_2, T.semibold).apply { setPadding(0, T.dp(12), 0, 0); setLineSpacing(0f, 1.2f) })
        toggle(hc, Icon.WIFI, T.TEAL, S.remoteSetting, S.remoteSettingHelp, p.remoteStart) { on ->
            p.remoteStart = on; app.service?.applyModes(); if (!on) app.service?.cancelRemote("switched off")
        }
        label(hc, S.remoteWindow)
        hc.addView(segment(listOf(15, 30, 60, 120).map { "$it" to "$it min" }, "${p.remoteMinutes}") { p.remoteMinutes = it.toInt() }, lp(-1, T.dp(48)))
        toggle(hc, Icon.BOOK, T.TEAL, S.remoteLoadSetting, S.remoteLoadHelp, p.remoteLoad) { p.remoteLoad = it }

        val ai = card("ai", S.aiTitle, Icon.SPARK, T.AMBER)
        val aiUrl = field(ai, S.aiAddress, p.aiUrl, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, S.aiAddressHint)
        val aiModel = field(ai, S.aiModel, p.aiModel, InputType.TYPE_CLASS_TEXT, S.aiModelHint)
        val aiKey = field(ai, S.aiKey, p.aiKey, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, S.aiKeyHint)
        ai.addView(HoldButton(c).apply { label = S.aiTest; icon = Icon.CHECK; holdMillis = 0; style(T.AMBER, T.EMBER)
            onConfirm = {
                p.aiUrl = aiUrl.text.toString(); p.aiModel = aiModel.text.toString(); p.aiKey = aiKey.text.toString()
                if (p.aiUrl.isBlank()) app.notify(S.aiSaved, Notice.OK) else Thread {
                    val r = runCatching { mcc.app.AiClient(p).models() }
                    view.post { r.fold({ app.notify(S.aiOk(it.size), Notice.OK) }, { app.notify(S.aiFailed(it.message ?: ""), Notice.ERROR) }) }
                }.start()
            } }, lp(-1, T.dp(54)).apply { topMargin = T.dp(14) })
        ai.addView(T.text(c, S.aiHelp, 13.5f, T.TEXT_2, T.semibold).apply { setPadding(0, T.dp(12), 0, 0); setLineSpacing(0f, 1.2f) })

        val about = card("about", S.about, Icon.DIAL, T.TEXT_2)
        for (k in listOf(S.link, S.powerBoard, S.emptyBowl, S.serialLoop, S.app)) {
            val r = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, T.dp(5), 0, T.dp(5)) }
            r.addView(T.text(c, k, 14f, T.TEXT_2, T.semibold), lp(T.dp(150), -2))
            val v = T.text(c, "", 14f, T.TEXT, T.bold); r.addView(v, lp(0, -2, 1f)); kv[k] = v
            about.addView(r)
        }
        label(about, S.safetyTitle, top = 14)
        machineInfo = T.text(c, "", 13f, T.TEXT_2, T.semibold).apply { setLineSpacing(0f, 1.35f) }
        about.addView(machineInfo)
        about.addView(T.text(c, S.fontCredit, 12f, T.TEXT_3, T.semibold).apply { setPadding(0, T.dp(12), 0, 0) })

        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; setPadding(T.dp(16), T.dp(4), T.dp(16), 0) }
        val shut = p.collapsed
        for (cd in cards) (cd as LinearLayout).let { if (it.tag in shut) fold(it, true, animate = false) }
        row.addView(scroll(column(lc, ac, mc, lsc)), lp(0, -1, 1f))
        row.addView(scroll(column(rc, hc, ai, about)), lp(0, -1, 1f).apply { leftMargin = T.dp(16) })
        return row
    }

    override fun onShow() { Anim.stagger(cards, step = 60) }

    override fun render() {
        val st = app.service?.mqttStatus ?: "off"
        mqttStatus.put(when { st == "off" -> S.notConnected; st == "connected" -> S.connectedWord; st.startsWith("connecting") -> S.connecting; else -> st })
        mqttDot.color = when { st == "connected" -> T.OK; st == "off" -> T.TEXT_3; else -> T.AMBER }
        repoStatus.put(S.recipesAvailable(app.index?.visible(app.prefs.uiLanguage)?.size ?: 0))
        val s = snap
        kv[S.link]?.put(if (s?.connected == true) S.connectedWord else if (MachineService.status.startsWith("BLOCKED")) S.stockApp else S.offline)
        kv[S.powerBoard]?.put(S.firmware(s?.lastFrame?.firmwareVersion?.toString() ?: "—"))
        kv[S.serialLoop]?.put(MachineService.loopTiming?.let { (a, m, l) -> S.loopStats(a, m, l) } ?: "—")
        kv[S.emptyBowl]?.put(if (s?.hasEmptyReference == true) S.saved else S.notSet)
        kv[S.app]?.put("mcc-app ${BuildConfig.VERSION_NAME}")
        machineInfo.put(S.safetyLines.joinToString("\n") { "• $it" })
    }
}
