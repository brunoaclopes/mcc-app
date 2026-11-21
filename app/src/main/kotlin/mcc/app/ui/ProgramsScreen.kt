package mcc.app.ui

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import mcc.recipe.Programs
import mcc.recipe.Recipe
import mcc.recipe.Step

/**
 * Automatic programs (eggs, rice, steaming, ..., cleaning) as big photo tiles. A tap only loads the program into
 * the Cook screen; nothing moves until Start is held there.
 */
class ProgramsScreen(app: AppUi) : Screen(app) {
    override val title get() = S.navPrograms
    override val subtitle get() = S.programsSubtitle

    private val tiles = ArrayList<View>()
    private val specials = ArrayList<View>()
    private var focus = 0
    private lateinit var scroll: android.widget.ScrollView
    private lateinit var list: List<Recipe>

    override val view: View = build()

    private fun build(): View {
        val lang = app.prefs.uiLanguage
        list = Programs.cooking().filter { it.language == lang } + Programs.cleanings(lang)
        val page = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(T.dp(16), T.dp(6), T.dp(16), T.dp(16)) }
        scroll = android.widget.ScrollView(c).apply { isVerticalScrollBarEnabled = false; isVerticalFadingEdgeEnabled = true; setFadingEdgeLength(T.dp(24)); addView(page) }
        val cols = 5
        list.chunked(cols).forEachIndexed { row, items ->
            val line = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
            items.forEachIndexed { i, r -> line.addView(tile(r), LinearLayout.LayoutParams(0, -1, 1f).apply { if (i > 0) leftMargin = T.dp(12) }) }
            repeat(cols - items.size) { line.addView(View(c), LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = T.dp(12) }) }
            page.addView(line, LinearLayout.LayoutParams(-1, T.dp(232)).apply { if (row > 0) topMargin = T.dp(12) })
        }
        return scroll
    }

    private fun tile(r: Recipe): View {
        val cleaning = Programs.isCleaning(r)
        val card = FrameLayout(c)
        val img = RoundImage(c, 20f).apply {
            scrim = 0.7f
            if (cleaning) placeholder(0xFF1C6E62.toInt(), 0xFF0B2B27.toInt()) else placeholder(0xFF8A3F12.toInt(), 0xFF3A1406.toInt())
            art = Covers.iconFor(r.id, r.title, r.tags)
        }
        card.addView(img, FrameLayout.LayoutParams(-1, -1))
        ImageLoader.load(app.store, r.image, img, T.dp(240))
        val info = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(T.dp(14), 0, T.dp(12), T.dp(14)) }
        info.addView(T.text(c, shortTitle(r), 17f, T.ON_PHOTO, T.heavy).apply { maxLines = 2; setShadowLayer(6f, 0f, 1f, 0xAA000000.toInt()) })
        info.addView(T.text(c, meta(r), 13f, T.ON_PHOTO_2, T.semibold).apply { setPadding(0, T.dp(5), 0, 0); maxLines = 1 })
        card.addView(info, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        if (cleaning) {
            val badge = T.text(c, S.cleanShort.uppercase(S.locale), 11f, T.ON_ACCENT, T.heavy).apply {
                letterSpacing = 0.08f; background = T.round(T.TEAL, 10); setPadding(T.dp(9), T.dp(4), T.dp(9), T.dp(4))
            }
            card.addView(badge, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(T.dp(10), T.dp(10), 0, 0) })
        }
        if (mcc.recipe.RecipeFit.usesSpecial(r)) specials += card
        card.setOnClickListener { open(r) }
        T.press(card)
        tiles.add(card)
        return card
    }

    private fun open(r: Recipe) {
        if (mcc.recipe.RecipeFit.usesSpecial(r) && !app.prefs.specialModes) { app.notify(S.specialModesOff, Notice.INFO); return }
        app.cook(r)
    }

    /** "Ovos cozidos, moles" -> "Ovos moles", "Boiled eggs, soft" -> "Soft eggs". */
    private fun shortTitle(r: Recipe): String {
        if (", " !in r.title) return r.title
        val (head, tail) = r.title.split(", ", limit = 2)
        return if (r.language == "pt") head.substringBefore(" ") + " " + tail
        else tail.replaceFirstChar { it.uppercase() } + " " + head.substringAfterLast(" ")
    }

    /** "16 min · 120 °C" from the program's own steps. */
    private fun meta(r: Recipe): String {
        val segs = r.steps.filterIsInstance<Step.Machine>().flatMap { it.segments }
        val temp = segs.maxOfOrNull { it.tempC } ?: 0
        return listOfNotNull(r.totalMinutes?.let { S.minutesShort(it) }, if (temp > 0) "$temp °C" else null).joinToString(" · ")
    }

    private fun highlight() {
        tiles.forEachIndexed { i, t -> t.foreground = if (i == focus) T.round(0, 20, T.AMBER, 3) else null }
        val t = tiles[focus]; val row = t.parent as View
        scroll.smoothScrollTo(0, (row.top - T.dp(20)).coerceAtLeast(0))
    }

    override fun onShow() {
        Anim.stagger(tiles, step = 35)
        view.postDelayed({ specials.forEach { it.animate().alpha(if (app.prefs.specialModes) 1f else 0.5f).setDuration(200).start() } }, 700)
    }

    override fun onHide() { tiles.forEach { it.foreground = null } }

    /** Knob: turn to pick a program, press to open it. */
    override fun onKnob(keyCode: Int): Boolean {
        when (keyCode) {
            136 -> focus = (focus + 1) % tiles.size
            137 -> focus = (focus - 1 + tiles.size) % tiles.size
            else -> { open(list[focus]); return true }
        }
        highlight()
        return true
    }
}
