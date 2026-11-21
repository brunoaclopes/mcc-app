package mcc.app.ui

import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.DecelerateInterpolator
import android.view.animation.GridLayoutAnimationController
import android.view.animation.TranslateAnimation
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import mcc.recipe.Recipe
import mcc.recipe.RecipeIndex
import mcc.recipe.Servings
import mcc.recipe.Step
import mcc.recipe.forPeople

class RecipesScreen(app: AppUi) : Screen(app) {
    override val title get() = S.recipesTitle
    override val subtitle: String get() = detailRecipe?.let { r -> listOfNotNull(r.servings, r.totalMinutes?.let { S.minutesShort(it) }, r.tags.firstOrNull()).joinToString(" · ") }
        ?: (S.recipesCount(shown.size) + (warning?.let { " · $it" } ?: ""))

    private val shown = ArrayList<RecipeIndex.Entry>()
    /** The selected tag chip (null = all). Not called "tag": inside a View.apply block that name means View.tag. */
    private var selectedTag: String? = null
    private var warning: String? = null
    /** The open recipe as it will be cooked, scaled to [people]. */
    private var detailRecipe: Recipe? = null
    private var people = 0
    private var onPeople: ((Int) -> Unit)? = null

    private lateinit var search: EditText
    private val clear by lazy { FrameLayout(c) }
    private lateinit var tagRow: LinearLayout
    private lateinit var grid: GridView
    private lateinit var empty: TextView
    private lateinit var detail: FrameLayout
    private val adapter = Cards()

    override val view: View = build()

    private fun build(): View {
        val root = FrameLayout(c)
        val page = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(T.dp(16), T.dp(4), T.dp(16), 0) }

        val top = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = T.glass(25); setPadding(T.dp(16), 0, T.dp(12), 0)
        }
        box.addView(IconView(c, Icon.SEARCH, T.TEXT_2), LinearLayout.LayoutParams(T.dp(22), T.dp(22)))
        search = EditText(c).apply {
            hint = S.searchHint; setHintTextColor(T.TEXT_3); setTextColor(T.TEXT); textSize = 17f
            typeface = T.semibold; background = null; isSingleLine = true; imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(T.dp(10), 0, 0, 0)
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { clear.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE; filter(animate = false) }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        box.addView(search, LinearLayout.LayoutParams(0, -1, 1f))
        clear.apply {
            background = T.pressable(T.round(T.ink(0x1A), 18)); visibility = View.GONE
            addView(IconView(c, Icon.CLOSE, T.TEXT_2, 2.4f), FrameLayout.LayoutParams(T.dp(16), T.dp(16), Gravity.CENTER))
            setOnClickListener { search.setText("") }
            T.press(this)
        }
        box.addView(clear, LinearLayout.LayoutParams(T.dp(36), T.dp(36)).apply { leftMargin = T.dp(6) })
        top.addView(box, LinearLayout.LayoutParams(0, T.dp(52), 1f))
        val refresh = FrameLayout(c).apply { background = T.pressable(T.glass(26)); setOnClickListener { app.reloadIndex(true); app.notify(S.syncing) } }
        refresh.addView(IconView(c, Icon.REFRESH, T.TEXT_2), FrameLayout.LayoutParams(T.dp(22), T.dp(22), Gravity.CENTER))
        T.press(refresh)
        top.addView(refresh, LinearLayout.LayoutParams(T.dp(52), T.dp(52)).apply { leftMargin = T.dp(10) })
        val ai = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = T.pressable(T.glass(26)); setPadding(T.dp(14), 0, T.dp(18), 0)
            addView(IconView(c, Icon.SPARK, T.AMBER), LinearLayout.LayoutParams(T.dp(22), T.dp(22)))
            aiLabel = T.text(c, S.aiImport, 15f, T.TEXT, T.bold).apply { setPadding(T.dp(8), 0, 0, 0) }
            addView(aiLabel)
            setOnClickListener { if (aiStarted == 0L) importAi() else app.notify(S.aiWorking) }
        }
        T.press(ai)
        top.addView(ai, LinearLayout.LayoutParams(-2, T.dp(52)).apply { leftMargin = T.dp(10) })
        page.addView(top)

        tagRow = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        page.addView(HorizontalScrollView(c).apply { isHorizontalScrollBarEnabled = false; addView(tagRow) },
            LinearLayout.LayoutParams(-1, T.dp(58)).apply { topMargin = T.dp(4) })

        grid = GridView(c).apply {
            numColumns = 3; horizontalSpacing = T.dp(16); verticalSpacing = T.dp(16)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH; adapter = this@RecipesScreen.adapter
            selector = GradientDrawable(); clipToPadding = false; setPadding(0, T.dp(4), 0, T.dp(16))
            isVerticalScrollBarEnabled = false
            setOnItemClickListener { _, v, pos, _ -> shown.getOrNull(pos)?.let { open(it, v) } }
            // cards cascade in (column- and row-staggered) whenever the list is refiltered
            val a = AnimationSet(true).apply {
                addAnimation(AlphaAnimation(0f, 1f)); addAnimation(TranslateAnimation(0f, 0f, T.dpf(24f), 0f))
                duration = 260; interpolator = DecelerateInterpolator()
            }
            layoutAnimation = GridLayoutAnimationController(a, 0.12f, 0.18f)
        }
        page.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(page)
        empty = T.text(c, "", 18f, T.TEXT_2, T.semibold).apply { gravity = Gravity.CENTER; visibility = View.GONE; setLineSpacing(0f, 1.3f)
            setOnClickListener { if (app.index != null) { selectedTag = null; search.setText(""); styleTags(); filter() } } }
        root.addView(empty, FrameLayout.LayoutParams(-1, -1))
        detail = FrameLayout(c).apply { visibility = View.GONE; isClickable = true }
        detail.background = BackdropSlice(detail)
        root.addView(detail, FrameLayout.LayoutParams(-1, -1))
        return root
    }

    fun onIndex(w: String?) {
        warning = w
        buildTags()
        filter()
    }

    private fun buildTags() {
        tagRow.removeAllViews()
        val idx = app.index ?: return
        val counts = HashMap<String, Int>()
        idx.visible(app.prefs.uiLanguage).forEach { e -> e.tags.forEach { counts[it] = (counts[it] ?: 0) + 1 } }
        // programs and the user's own recipes always come first
        val pinned = listOf(S.tagMine, mcc.recipe.Programs.TAG_PT, mcc.recipe.Programs.TAG_EN).filter { it in counts }
        val tags = listOf<String?>(null) + pinned + counts.entries.filter { it.key !in pinned }.sortedByDescending { it.value }.take(14 - pinned.size).map { it.key }
        for (t in tags) {
            val chip = T.text(c, t ?: S.all, 15f, T.TEXT, T.bold).apply {
                gravity = Gravity.CENTER; setPadding(T.dp(18), 0, T.dp(18), 0)
                setOnClickListener { selectedTag = t; styleTags(); filter() }
                this.tag = t ?: ""
            }
            tagRow.addView(chip, LinearLayout.LayoutParams(-2, T.dp(46)).apply { rightMargin = T.dp(8); topMargin = T.dp(6) })
        }
        if (selectedTag != null && selectedTag !in counts) selectedTag = null
        styleTags()
    }

    private fun styleTags() {
        for (i in 0 until tagRow.childCount) {
            val v = tagRow.getChildAt(i) as TextView
            val sel = (v.tag as String) == (selectedTag ?: "")
            v.background = if (sel) T.gradient(T.AMBER, T.EMBER, 23) else T.pressable(T.glass(23))
            v.setTextColor(if (sel) T.ON_ACCENT else T.TEXT)
        }
    }

    private fun filter(animate: Boolean = true) {
        val q = search.text.toString().trim().lowercase()
        shown.clear()
        app.index?.visible(app.prefs.uiLanguage)?.filter { e ->
            (selectedTag == null || selectedTag in e.tags) &&
                (q.isEmpty() || e.title.lowercase().contains(q) || e.tags.any { it.lowercase().contains(q) })
        }?.let { shown.addAll(it) }
        adapter.notifyDataSetChanged()
        if (animate) { grid.setSelection(0); grid.scheduleLayoutAnimation() }
        empty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        empty.text = when {
            app.index == null -> S.noRecipes
            q.isNotEmpty() -> S.nothingMatches(q)
            else -> S.nothingInFilter
        }
        knobPos = -1
    }

    override fun onShow() { if (tagRow.childCount == 0) buildTags(); filter() }

    override fun onBack(): Boolean {
        if (detail.visibility == View.VISIBLE) { closeDetail(); return true }
        return false
    }

    override fun onKnob(keyCode: Int): Boolean {
        val inDetail = detail.visibility == View.VISIBLE
        when (keyCode) {
            136, 137 -> if (inDetail) {
                // in a recipe the knob picks how many people it is for
                onPeople?.invoke(people + if (keyCode == 136) 1 else -1)
            } else if (shown.isNotEmpty()) {
                // turning moves a highlight card by card; a press opens it
                knobPos = (knobPos + if (keyCode == 136) 1 else -1).coerceIn(0, shown.lastIndex)
                grid.smoothScrollToPosition(knobPos)
                adapter.notifyDataSetChanged()
            }
            else -> {
                val r = detailRecipe
                if (inDetail && r != null) app.cook(r)
                else if (!inDetail && knobPos in shown.indices) open(shown[knobPos], null)
                else return false
            }
        }
        return true
    }

    // ------------------------------------------------------------------ cards

    private inner class Cards : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(p: Int) = shown[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, convert: View?, parent: ViewGroup): View {
            val card = (convert as? FrameLayout) ?: makeCard()
            val e = shown[p]
            card.foreground = if (p == knobPos) T.round(0, 22, T.AMBER, 3) else null
            val img = card.getChildAt(0) as RoundImage
            val (pa, pb) = palette(e.title)
            img.placeholder(pa, pb)
            img.art = Covers.iconFor(e.id, e.title, e.tags)
            ImageLoader.load(app.store, e.image, img, T.dp(300))
            val info = card.getChildAt(1) as LinearLayout
            (info.getChildAt(0) as TextView).text = e.title
            (info.getChildAt(1) as TextView).text = listOfNotNull(e.totalMinutes?.let { "⏱ " + S.minutesShort(it) }, e.tags.firstOrNull()).joinToString("   ·   ")
            return card
        }
    }

    private fun makeCard(): FrameLayout {
        val card = FrameLayout(c).apply { layoutParams = ViewGroup.LayoutParams(-1, T.dp(214)) }
        card.addView(RoundImage(c, 22f).apply { scrim = 0.62f }, FrameLayout.LayoutParams(-1, -1))
        val info = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(T.dp(16), 0, T.dp(14), T.dp(14)) }
        info.addView(T.text(c, "", 17f, T.ON_PHOTO, T.heavy).apply { maxLines = 2; setLineSpacing(0f, 1.05f); setShadowLayer(6f, 0f, 1f, 0xAA000000.toInt()) })
        info.addView(T.text(c, "", 13f, T.ON_PHOTO_2, T.semibold).apply { setPadding(0, T.dp(6), 0, 0); maxLines = 1 })
        card.addView(info, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        T.press(card)
        return card
    }

    private fun palette(title: String): Pair<Int, Int> {
        val palettes = listOf(0xFF8A3F12.toInt() to 0xFF3A1406.toInt(), 0xFF1C6E62.toInt() to 0xFF0B2B27.toInt(),
            0xFF35548A.toInt() to 0xFF111B33.toInt(), 0xFF8A6420.toInt() to 0xFF352304.toInt())
        return palettes[(title.hashCode() and 0x7fffffff) % palettes.size]
    }

    // ------------------------------------------------------------------ detail

    private fun open(e: RecipeIndex.Entry, from: View?) {
        from?.animate()?.scaleX(0.96f)?.scaleY(0.96f)?.setDuration(90)?.withEndAction { from.animate().scaleX(1f).scaleY(1f).setDuration(120).start() }?.start()
        Thread {
            val r = runCatching { app.store.loadRecipe(e) }
            view.post {
                r.fold(onSuccess = { showDetail(e, it) }, onFailure = { app.notify(S.cannotOpen(it.message ?: ""), Notice.ERROR) })
            }
        }.start()
    }

    private fun showDetail(e: RecipeIndex.Entry, r: Recipe) {
        detailRecipe = r
        val base = Servings.base(r)
        people = base?.count ?: 0
        detail.removeAllViews()
        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; setPadding(T.dp(16), T.dp(4), T.dp(16), T.dp(16)) }

        val hero = FrameLayout(c)
        val img = RoundImage(c, 24f).apply { scrim = 0.55f; val (pa, pb) = palette(r.title); placeholder(pa, pb); art = Covers.iconFor(r.id, r.title, r.tags) }
        hero.addView(img, FrameLayout.LayoutParams(-1, -1))
        ImageLoader.load(app.store, r.image ?: e.image, img, T.dp(520))
        val back = FrameLayout(c).apply { background = T.pressable(T.round(0xA6000000.toInt(), 26, 0x40FFFFFF, 1)); setOnClickListener { closeDetail() } }
        back.addView(IconView(c, Icon.BACK, T.ON_PHOTO, 2.5f), FrameLayout.LayoutParams(T.dp(24), T.dp(24), Gravity.CENTER))
        T.press(back)
        hero.addView(back, FrameLayout.LayoutParams(T.dp(52), T.dp(52)).apply { leftMargin = T.dp(14); topMargin = T.dp(14) })
        val heroInfo = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(T.dp(22), 0, T.dp(22), T.dp(20)) }
        heroInfo.addView(T.text(c, r.title, 27f, T.ON_PHOTO, T.heavy).apply { setLineSpacing(0f, 1.05f); setShadowLayer(8f, 0f, 1f, 0xCC000000.toInt()) })
        val meta = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, T.dp(12), 0, 0) }
        val machineSteps = r.steps.count { it is Step.Machine }
        listOfNotNull(
            r.totalMinutes?.let { Icon.CLOCK to S.minutesShort(it) },
            r.servings?.takeIf { base == null }?.let { Icon.CHEF to it },
            r.nutritionPerServing?.kcal?.let { Icon.FLAME to S.kcal(it) },
            Icon.DIAL to S.machineSteps(machineSteps),
        ).forEach { (ic, t) -> meta.addView(metaChip(ic, t), LinearLayout.LayoutParams(-2, T.dp(34)).apply { rightMargin = T.dp(8) }) }
        heroInfo.addView(HorizontalScrollView(c).apply { isHorizontalScrollBarEnabled = false; addView(meta) })
        hero.addView(heroInfo, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        row.addView(hero, LinearLayout.LayoutParams(0, -1, 1.1f))

        val right = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(T.dp(22), T.dp(6), 0, 0) }
        val head = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(T.text(c, S.ingredientsFor(r), 21f, T.TEXT, T.heavy), LinearLayout.LayoutParams(0, -2, 1f))
        right.addView(head, LinearLayout.LayoutParams(-1, T.dp(52)))
        val scaledNote = T.text(c, S.scaledNote, 13f, T.AMBER, T.semibold).apply { setPadding(0, T.dp(4), 0, 0); visibility = View.GONE }
        right.addView(scaledNote)
        val list = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(0, T.dp(8), 0, T.dp(8)) }
        fun fill(rr: Recipe) {
            list.removeAllViews()
            fillIngredients(list, rr)
            if (rr.notes.isNotEmpty()) list.addView(T.text(c, "${S.notesFor(rr)}: " + rr.notes.joinToString("; "), 13f, T.TEXT_3).apply { setPadding(0, T.dp(14), 0, 0) })
        }
        fill(r)
        if (base != null) {
            val range = Servings.range(base)
            val label = T.text(c, "", 16f, T.TEXT, T.heavy).apply { gravity = Gravity.CENTER; maxLines = 1 }
            fun stepBtn(sign: String, d: Int) = FrameLayout(c).apply {
                background = T.pressable(T.round(T.ink(0x1A), 22))
                addView(T.text(c, sign, 24f, T.TEXT, T.heavy).apply { gravity = Gravity.CENTER; includeFontPadding = false }, FrameLayout.LayoutParams(-1, -1))
                setOnClickListener { onPeople?.invoke(people + d) }
                T.press(this)
            }
            val minus = stepBtn("−", -1); val plus = stepBtn("+", 1)
            val pill = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = T.glass(26); setPadding(T.dp(4), T.dp(4), T.dp(4), T.dp(4)) }
            pill.addView(minus, LinearLayout.LayoutParams(T.dp(44), T.dp(44)))
            pill.addView(label, LinearLayout.LayoutParams(-2, -1).apply { leftMargin = T.dp(12); rightMargin = T.dp(12) })
            pill.addView(plus, LinearLayout.LayoutParams(T.dp(44), T.dp(44)))
            head.addView(pill, LinearLayout.LayoutParams(-2, T.dp(52)))
            onPeople = { n ->
                val v = n.coerceIn(range.first, range.last)
                if (v != people || label.text.isEmpty()) {
                    people = v
                    val rr = r.forPeople(v)
                    detailRecipe = rr
                    label.text = rr.servings
                    minus.alpha = if (v > range.first) 1f else 0.35f
                    plus.alpha = if (v < range.last) 1f else 0.35f
                    scaledNote.visibility = if (v != base.count) View.VISIBLE else View.GONE
                    fill(rr)
                }
            }
            onPeople?.invoke(base.count)
        } else onPeople = null
        right.addView(ScrollView(c).apply { isVerticalScrollBarEnabled = false; isVerticalFadingEdgeEnabled = true; setFadingEdgeLength(T.dp(24)); addView(list) },
            LinearLayout.LayoutParams(-1, 0, 1f))
        right.addView(HoldButton(c).apply { label = S.startFor(r); icon = Icon.NEXT; holdMillis = 0; style(T.AMBER, T.EMBER); onConfirm = { app.cook(detailRecipe ?: r) } },
            LinearLayout.LayoutParams(-1, T.dp(64)).apply { topMargin = T.dp(10) })
        right.addView(T.text(c, S.nothingMoves, 13f, T.TEXT_2, T.semibold).apply { gravity = Gravity.CENTER; setPadding(0, T.dp(8), 0, 0) })
        if (app.store.isMine(e)) {
            val del = FrameLayout(c).apply { background = T.pressable(T.round(0xA6000000.toInt(), 26, 0x40FFFFFF, 1)) }
            del.addView(IconView(c, Icon.TRASH, T.ON_PHOTO, 2f), FrameLayout.LayoutParams(T.dp(22), T.dp(22), Gravity.CENTER))
            del.setOnClickListener {
                app.confirm(S.deleteRecipe, S.deleteRecipeBody, S.deleteRecipe, danger = true) { app.store.deleteMine(e); closeDetail(); app.reloadIndex(false) }
            }
            T.press(del)
            hero.addView(del, FrameLayout.LayoutParams(T.dp(52), T.dp(52), Gravity.END).apply { rightMargin = T.dp(14); topMargin = T.dp(14) })
        }
        row.addView(right, LinearLayout.LayoutParams(0, -1, 1f))
        detail.addView(row, FrameLayout.LayoutParams(-1, -1))
        detail.visibility = View.VISIBLE
        detail.alpha = 0f; detail.scaleX = 0.97f; detail.scaleY = 0.97f
        detail.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(240).setInterpolator(DecelerateInterpolator()).start()
        Anim.stagger((0 until list.childCount).map { list.getChildAt(it) }.take(14), step = 25, dy = 8f)
    }

    private fun metaChip(icon: Icon, text: String) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        background = T.round(0x80000000.toInt(), 17, 0x40FFFFFF, 1); setPadding(T.dp(10), 0, T.dp(12), 0)
        addView(IconView(c, icon, 0xFFFFB547.toInt()), LinearLayout.LayoutParams(T.dp(16), T.dp(16)))
        addView(T.text(c, text, 13f, T.ON_PHOTO, T.bold).apply { setPadding(T.dp(6), 0, 0, 0); maxLines = 1 })
    }

    private var knobPos = -1
    private lateinit var aiLabel: TextView
    private var aiStarted = 0L
    private val aiTick = object : Runnable {
        override fun run() {
            if (aiStarted == 0L) { aiLabel.text = S.aiImport; return }
            aiLabel.text = S.aiImporting(((android.os.SystemClock.elapsedRealtime() - aiStarted) / 1000).toInt())
            view.postDelayed(this, 1000)
        }
    }

    private fun importAi() {
        val ai = mcc.app.AiClient(app.prefs)
        if (!ai.configured) { app.notify(S.aiNotSet, Notice.WARN); return }
        app.ask(S.aiImport, S.aiImportBody, S.aiImportHint, S.aiImportGo) { input ->
            app.notify(S.aiWorking)
            aiStarted = android.os.SystemClock.elapsedRealtime(); aiTick.run()
            val lang = app.prefs.uiLanguage
            Thread {
                val res = runCatching { ai.importRecipe(input, lang) }
                // and the other language straight away, so it shows whichever language the machine is set to
                val other = if (lang == "pt") "en" else "pt"
                val translated = res.getOrNull()?.let { imp -> runCatching { ai.translate(imp.recipe, other) } }
                view.post {
                    aiStarted = 0L; aiTick.run()
                    res.fold(onSuccess = { imp ->
                        val saved = app.store.saveMine(imp.recipe)
                        translated?.getOrNull()?.let { app.store.saveMine(it) }
                        app.reloadIndex(false)
                        app.notify(S.aiDone(saved.title) + (if (imp.changes.isEmpty()) "" else " · " + S.aiChanges(imp.changes.size)) +
                            (if (translated?.isSuccess == true) "" else " · " + S.aiNoTranslation), if (translated?.isSuccess == true) Notice.OK else Notice.WARN)
                        showDetail(RecipeIndex.Entry(saved.id, saved.title, saved.language, saved.tags, saved.totalMinutes, null, "mine/${saved.id}.json"), saved)
                    }, onFailure = { app.notify(S.aiFailed(it.message ?: it.javaClass.simpleName), Notice.ERROR) })
                }
            }.start()
        }
    }

    private fun closeDetail() {
        detailRecipe = null; onPeople = null
        detail.animate().alpha(0f).scaleX(0.97f).scaleY(0.97f).setDuration(180).withEndAction { detail.visibility = View.GONE }.start()
    }

    companion object {
        /** Ingredient list with ember bullets and wrapped lines aligned under the text (shared with the Cook sheet). */
        fun fillIngredients(list: LinearLayout, r: Recipe) {
            val c = list.context
            r.ingredients.forEach { g ->
                g.group?.let { list.addView(T.text(c, it.uppercase(), 12.5f, T.AMBER, T.heavy).apply { letterSpacing = 0.08f; setPadding(0, T.dp(14), 0, T.dp(6)) }) }
                g.items.forEach { i ->
                    val line = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, T.dp(7), 0, T.dp(7)) }
                    line.addView(View(c).apply { background = T.round(T.EMBER, 4) }, LinearLayout.LayoutParams(T.dp(7), T.dp(7)).apply { topMargin = T.dp(8); rightMargin = T.dp(12) })
                    line.addView(T.text(c, i.text, 16.5f, T.TEXT, T.regular).apply { setLineSpacing(0f, 1.15f) }, LinearLayout.LayoutParams(0, -2, 1f))
                    list.addView(line)
                }
            }
        }
    }
}
