package mcc.app.ui

import android.content.Context
import android.view.View
import mcc.app.MachineService
import mcc.app.Prefs
import mcc.app.RecipeStore
import mcc.recipe.Recipe
import mcc.recipe.RecipeIndex

/** What screens need from the activity. */
interface AppUi {
    val ctx: Context
    val store: RecipeStore
    val prefs: Prefs
    val service: MachineService?
    var index: RecipeIndex?
    fun go(screen: String)
    fun notify(msg: String, kind: Notice = Notice.INFO)
    fun confirm(title: String, msg: String, ok: String, danger: Boolean = false, dismiss: String? = null, onOk: () -> Unit)
    /** Like [confirm], with a text field; [onOk] gets the (non-empty) text. */
    fun ask(title: String, msg: String, hint: String, ok: String, onOk: (String) -> Unit)
    fun reloadIndex(refresh: Boolean)
    /** Loads a recipe into the runner and opens the Cook screen. */
    fun cook(recipe: Recipe)
    fun manualPreset(seconds: Int, speed: Int, tempC: Int, reverse: Boolean = false)
    /** Shows the unlock confirmation (from any locked Start button). */
    fun requestUnlock()
    /** "Is the bowl empty?" dialog that stores the empty-bowl weight used by the heating check. */
    fun askEmptyBowl(lead: String? = null)
    fun play(cue: mcc.app.Sounds.Cue)
    /** Confetti burst over the whole UI. */
    fun celebrate()
    /** Switches the UI language and rebuilds the screens. */
    fun setLanguage(code: String)
    /** "auto", "day" or "night"; rebuilds the UI with a cross-fade if the look changes. */
    fun setTheme(mode: String, force: Boolean = false)
}

enum class Notice { INFO, OK, WARN, ERROR }

abstract class Screen(val app: AppUi) {
    protected val S get() = mcc.app.i18n.L.s
    abstract val title: String
    open val subtitle: String get() = ""
    abstract val view: View
    open fun onShow() {}
    /** Called when another screen takes over, or the app goes to the background. */
    open fun onHide() {}
    /** Called every 250 ms while visible. */
    open fun render() {}
    /** Jog knob: 136 right, 137 left, 23 push. Return true if handled. */
    open fun onKnob(keyCode: Int): Boolean = false
    open fun onBack(): Boolean = false
    protected val c: Context get() = app.ctx
    protected val snap get() = MachineService.snapshot
}

fun fmtClock(ms: Long): String { val s = (ms + 999) / 1000; return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }

