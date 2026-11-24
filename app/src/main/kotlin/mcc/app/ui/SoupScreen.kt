package mcc.app.ui

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import mcc.recipe.Soup

/**
 * The automatic soup: what kind of vegetables and how smooth. How much is not asked: the recipe weighs the vegetables
 * when they go in and works out the water and the time then ([Soup.measured]). This screen only builds the recipe and
 * opens it on the Cook screen; nothing moves until Start is held there.
 */
class SoupScreen(app: AppUi) : Screen(app) {
    override val title get() = S.soupTitle
    override val subtitle get() = S.soupSubtitle

    private var kind = runCatching { Soup.Kind.valueOf(app.prefs.soupKind) }.getOrDefault(Soup.Kind.MIXED)
    private var blend = app.prefs.soupBlend.coerceIn(0, Soup.BLEND_MAX)
    private var onion = app.prefs.soupOnion

    private lateinit var onionChip: TextView
    private lateinit var summary: TextView

    override val view: View = build()

    private fun build(): View {
        val page = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; setPadding(T.dp(16), T.dp(6), T.dp(16), T.dp(16)) }

        // left: how much is the scale's job
        val left = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = T.glass(24); setPadding(T.dp(22), T.dp(18), T.dp(22), T.dp(18)) }
        left.addView(IconView(c, Icon.SCALE, T.TEAL, 1.6f), LinearLayout.LayoutParams(T.dp(64), T.dp(64)))
        left.addView(T.text(c, S.soupVeg, 22f, T.TEXT, T.heavy).apply { gravity = Gravity.CENTER; setPadding(0, T.dp(10), 0, 0) })
        left.addView(T.text(c, S.soupVegHelp, 15f, T.TEXT_2, T.regular).apply { gravity = Gravity.CENTER; setLineSpacing(0f, 1.25f) },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = T.dp(10) })
        summary = T.text(c, "", 14f, T.TEXT_3, T.semibold).apply { gravity = Gravity.CENTER; setLineSpacing(0f, 1.2f) }
        left.addView(summary, LinearLayout.LayoutParams(-1, -2).apply { topMargin = T.dp(14) })
        page.addView(left, LinearLayout.LayoutParams(0, -1, 0.9f))

        // right: what and how
        val right = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(T.dp(18), 0, 0, 0) }
        right.addView(label(S.soupKind))
        right.addView(segment(listOf(Soup.Kind.TENDER.name to S.soupTender, Soup.Kind.MIXED.name to S.soupMixed, Soup.Kind.FIRM.name to S.soupFirm), kind.name) {
            kind = Soup.Kind.valueOf(it); app.prefs.soupKind = it; update()
        }, LinearLayout.LayoutParams(-1, T.dp(52)))
        right.addView(T.text(c, S.soupKindHelp, 13f, T.TEXT_2, T.regular).apply { setPadding(T.dp(4), T.dp(6), 0, 0) })
        right.addView(label(S.soupTexture), LinearLayout.LayoutParams(-1, -2).apply { topMargin = T.dp(12) })
        right.addView(segment(listOf("0" to S.soupChunky, "1" to S.soupRustic, "2" to S.soupSmooth, "3" to S.soupVelvety), "$blend") {
            blend = it.toInt(); app.prefs.soupBlend = blend; update()
        }, LinearLayout.LayoutParams(-1, T.dp(52)))
        onionChip = T.text(c, "", 15f, T.TEXT, T.bold).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(T.dp(16), 0, T.dp(16), 0)
            setOnClickListener { onion = !onion; app.prefs.soupOnion = onion; update() }
            T.press(this)
        }
        right.addView(onionChip, LinearLayout.LayoutParams(-1, T.dp(56)).apply { topMargin = T.dp(14) })
        right.addView(View(c), LinearLayout.LayoutParams(-1, 0, 1f))
        right.addView(HoldButton(c).apply {
            label = S.soupStart; icon = Icon.PLAY; holdMillis = 0; style(T.AMBER, T.EMBER_HOT)
            onConfirm = { app.cook(Soup.recipe(Soup.Options(null, kind, blend, onion), app.prefs.uiLanguage)) }
        }, LinearLayout.LayoutParams(-1, T.dp(64)))
        page.addView(right, LinearLayout.LayoutParams(0, -1, 1.5f))
        update()
        return page
    }

    private fun label(s: String) = T.text(c, s.uppercase(S.locale), 13f, T.TEXT_2, T.heavy).apply { letterSpacing = 0.08f; setPadding(T.dp(4), 0, 0, T.dp(6)) }

    private fun roundBtn(s: String, onClick: () -> Unit) = T.text(c, s, 26f, T.TEXT, T.bold).apply {
        gravity = Gravity.CENTER; background = T.pressable(T.glass(26))
        setOnClickListener { onClick() }
        T.press(this)
    }

    /** Segmented control: the selected option is an amber pill (as in Settings). */
    private fun segment(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit): LinearLayout = LinearLayout(c).apply seg@{
        orientation = LinearLayout.HORIZONTAL; background = T.glass(26); setPadding(T.dp(4), T.dp(4), T.dp(4), T.dp(4))
        for ((code, name) in options) {
            addView(T.text(c, name, 15f, T.TEXT, T.heavy).apply {
                gravity = Gravity.CENTER; tag = code; maxLines = 1
                setOnClickListener { pick(this@seg, code); onPick(code) }
            }, LinearLayout.LayoutParams(0, -1, 1f))
        }
        pick(this, selected)
    }

    private fun pick(seg: LinearLayout, code: String) {
        for (i in 0 until seg.childCount) {
            val v = seg.getChildAt(i) as TextView
            val sel = v.tag == code
            v.setTextColor(if (sel) T.ON_ACCENT else T.TEXT)
            v.background = if (sel) T.gradient(T.AMBER, T.EMBER, 22) else T.pressable(android.graphics.drawable.ColorDrawable(0))
        }
    }

    private fun update() {
        onionChip.text = (if (onion) "✓  " else "    ") + S.soupOnion + " · " + S.soupOnionHelp
        onionChip.background = if (onion) T.pressable(T.round(T.withAlpha(T.AMBER, 0x30), 20, T.AMBER, 2)) else T.pressable(T.glass(20))
        // an example, so the choices visibly matter
        val p = Soup.plan(Soup.Options(EXAMPLE_G, kind, blend, onion))
        summary.text = S.soupExample(EXAMPLE_G, p.waterGrams, p.cookSeconds / 60, p.portions)
    }

    private companion object { const val EXAMPLE_G = 800 }
}
