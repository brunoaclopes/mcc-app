package mcc.app.i18n

import mcc.machine.BoardError
import mcc.machine.RejectCode
import mcc.machine.StartResult
import mcc.machine.StopReason
import mcc.recipe.Mode
import mcc.recipe.RecipeRunner
import mcc.recipe.Step

/**
 * Every user-visible text. Abstract so the compiler refuses a language with a missing string; there is
 * no silent fallback to English. [L] is the active language (Settings > Language), English or European Portuguese.
 */
abstract class Strings {
    abstract val code: String
    abstract val locale: java.util.Locale

    // navigation / chrome
    abstract val navHome: String; abstract val navManual: String; abstract val navRecipes: String
    abstract val navCook: String; abstract val navSettings: String
    abstract val keepRecipe: String; abstract val cancelRecipe: String; abstract val cancelRecipeTitle: String; abstract fun cancelRecipeBody(title: String): String
    abstract val continueLabel: String; abstract val manualPausedInfo: String; abstract val switchRecipeTitle: String; abstract fun switchRecipeBody(old: String, step: Int, new: String): String; abstract val switchRecipeOk: String
    abstract val navPrograms: String; abstract val programsSubtitle: String
    abstract val emptyBowlNeeded: String
    abstract val stop: String; abstract val stopped: String
    abstract val ready: String; abstract val running: String; abstract val offline: String; abstract val stockApp: String
    abstract val lidOpen: String; abstract val lidClosed: String
    abstract val lidShortOpen: String; abstract val lidShortClosed: String
    abstract val locked: String; abstract val unlocked: String
    abstract val controlsLocked: String; abstract val controlsUnlocked: String
    abstract val unlockTitle: String; abstract val unlockBody: String; abstract val unlock: String
    abstract val cancel: String
    abstract fun greeting(hour: Int): String
    abstract val brand: String

    // home
    abstract val homeTitle: String; abstract val machine: String; abstract val manualControl: String
    abstract val scale: String; abstract val tare: String; abstract val scaleZeroed: String; abstract val cannotTare: String
    abstract val bowl: String; abstract val cooking: String; abstract val quickStart: String; abstract val ideas: String
    abstract val presetChop: String; abstract val presetBlend: String; abstract val presetStir: String; abstract val presetWarm: String
    abstract val stockAppUsing: String; abstract val waitingBoard: String; abstract val attention: String
    abstract fun machineReports(e: String): String
    abstract val paused: String; abstract val closeLidToCook: String
    abstract val readyUnlocked: String; abstract val readyLocked: String
    abstract val left: String
    abstract val notWhileRunning: String
    abstract fun foodInBowl(g: Int): String
    abstract val setEmptyBowlHint: String
    abstract fun heatingTo(c: Int): String
    abstract val hotCareful: String; abstract val cool: String; abstract val warm: String
    abstract val finishedEnjoy: String
    abstract fun stepOfTap(a: Int, b: Int): String
    abstract val whatCooking: String
    abstract fun recipesTapBrowse(n: Int): String
    abstract fun speedN(n: Int): String

    // manual
    abstract val manualSubtitle: String; abstract val runningTapStop: String
    abstract val time: String; abstract val speed: String; abstract val temperature: String
    abstract val reverse: String; abstract val emptyBowl: String
    abstract val reverseLimit: String; abstract val heatLimit: String
    abstract val tagMine: String; abstract val aiTitle: String; abstract val aiAddress: String; abstract val aiAddressHint: String
    abstract val aiModel: String; abstract val aiModelHint: String; abstract val aiKey: String; abstract val aiKeyHint: String
    abstract val aiHelp: String; abstract val aiTest: String; abstract fun aiOk(models: Int): String; abstract val aiSaved: String
    abstract val aiImport: String; abstract val aiImportBody: String; abstract val aiImportHint: String; abstract val aiImportGo: String
    abstract val aiWorking: String; abstract fun aiDone(title: String): String; abstract fun aiFailed(why: String): String
    abstract val aiNotSet: String; abstract val aiNoTranslation: String; abstract fun aiChanges(n: Int): String; abstract val deleteRecipe: String; abstract val deleteRecipeBody: String
    abstract val remote: String; abstract val remoteOffHint: String; abstract val remoteNoStep: String; abstract val remoteTitle: String
    abstract fun remoteBody(what: String, minutes: Int): String; abstract val remoteAllow: String; abstract fun remoteLeft(min: Int): String
    abstract val remoteCancelled: String; abstract val remoteSetting: String; abstract val remoteSettingHelp: String; abstract val remoteWindow: String
    abstract fun remoteArmed(minutes: Int): String
    abstract val remoteLoadSetting: String; abstract val remoteLoadHelp: String; abstract fun remoteLoaded(title: String): String
    abstract val standbyTitle: String; abstract fun standbyBody(s: Int): String; abstract val standbyNow: String
    abstract val lightSound: String; abstract val soundSetting: String; abstract val soundOff: String; abstract val soundQuiet: String; abstract val soundNormal: String
    abstract val ledAnimSetting: String; abstract val ledAnimHelp: String; abstract val ledBrightness: String
    abstract val standbySetting: String; abstract val standbyHelp: String; abstract val standbyAfter: String
    abstract val turbo: String
    abstract val emptyBowlTitle: String; abstract val emptyBowlBody: String; abstract val emptyBowlOk: String; abstract val emptyBowlSaved: String
    abstract val scaleNotReady: String
    abstract val lockedHint: String
    abstract val seconds: String; abstract val minutes: String
    abstract val stirringOff: String; abstract val off: String; abstract val noHeat: String
    abstract val remaining: String; abstract val target: String
    abstract fun bowlTemp(c: Int): String; abstract val bowlBelow37: String
    // stopwatch runs (Manual, time 0:00) and heated steps still heating after their time
    abstract val stopwatch: String; abstract val elapsed: String; abstract fun waitingForHeat(c: Int): String
    // the automatic soup
    abstract val soupTitle: String; abstract val soupSubtitle: String; abstract val soupVeg: String; abstract val soupVegHelp: String
    abstract val soupKind: String; abstract val soupTender: String; abstract val soupMixed: String; abstract val soupFirm: String; abstract val soupKindHelp: String
    abstract val soupTexture: String; abstract val soupChunky: String; abstract val soupRustic: String; abstract val soupSmooth: String; abstract val soupVelvety: String
    abstract val soupOnion: String; abstract val soupOnionHelp: String; abstract val soupStart: String; abstract val soupTile: String; abstract val soupTileMeta: String
    abstract fun soupExample(veg: Int, water: Int, minutes: Int, portions: Int): String
    abstract fun wrongAmount(g: Int?, min: Int, max: Int): String; abstract fun upTo(g: Int): String
    abstract fun recipeReadyAt(t: String): String; abstract val tagCooked: String
    abstract val autoWeighSetting: String; abstract val autoWeighHelp: String; abstract val keepWarm: String; abstract val keepWarmOn: String
    abstract val holdToStart: String; abstract val holdToResume: String; abstract val pause: String
    abstract val waitingMachine: String; abstract val closeLidToStart: String
    abstract val unlockToStart: String
    abstract val heatNeedsReference: String
    abstract fun heatNeedsContent(now: Int): String
    abstract fun pressHold(summary: String): String
    abstract fun runningAt(speed: Int): String
    abstract fun duration(sec: Int): String

    // recipes
    abstract val recipesTitle: String
    abstract fun recipesCount(n: Int): String
    abstract fun stepsCount(n: Int): String
    abstract val offlineCopy: String; abstract val serverUnreachable: String
    abstract val longRunsSetting: String; abstract val longRunsHelp: String
    abstract val onlineUnreachable: String; abstract val onlineSetting: String; abstract val onlineHelp: String; abstract val tagOnline: String
    abstract val searchHint: String; abstract val syncing: String; abstract val all: String
    abstract val noRecipes: String
    abstract fun nothingMatches(q: String): String
    abstract val nothingInFilter: String; abstract fun aiImporting(s: Int): String; abstract fun heatAdd(missing: Int): String; abstract val autoLocked: String
    abstract fun opening(t: String): String
    abstract fun cannotOpen(m: String): String
    abstract fun minutesShort(m: Int): String
    abstract fun ofMax(n: Int): String
    abstract fun kcal(n: Int): String
    abstract fun machineSteps(n: Int): String
    abstract val ingredients: String; abstract val converterNotes: String
    abstract val startCooking: String; abstract val nothingMoves: String; abstract val scaledNote: String

    // cook
    abstract val cookTitle: String; abstract val guidedCooking: String
    abstract fun stepOf(a: Int, b: Int): String
    abstract fun aboutMinutes(m: Int): String
    abstract val kindPrepare: String; abstract val kindWeigh: String; abstract val kindMachine: String
    abstract val reZero: String; abstract val back: String; abstract val next: String; abstract val skip: String; abstract val finish: String
    abstract val nothingCooking: String; abstract val nothingCookingBody: String; abstract val browseRecipes: String
    abstract val cleanShort: String
    abstract val youNeed: String; abstract val notesWord: String
    fun ingredientsFor(r: mcc.recipe.Recipe?) = ingredients
    fun notesFor(r: mcc.recipe.Recipe?) = if (kind(r) == 0) converterNotes else notesWord
    abstract val startProgram: String; abstract val startCleaning: String; abstract val doneProgram: String; abstract val doneCleaning: String
    abstract val finishedProgram: String; abstract val finishedCleaning: String
    /** Wording by what is loaded: the cleaning program, another program, or a recipe. */
    private fun kind(r: mcc.recipe.Recipe?) = when { r == null || r.source?.kind != "program" -> 0; r.id.startsWith("program-clean") -> 2; else -> 1 }
    fun startFor(r: mcc.recipe.Recipe?) = when (kind(r)) { 2 -> startCleaning; 1 -> startProgram; else -> startCooking }
    fun doneFor(r: mcc.recipe.Recipe?) = when (kind(r)) { 2 -> doneCleaning; 1 -> doneProgram; else -> bomApetite }
    fun finishedFor(r: mcc.recipe.Recipe?) = when (kind(r)) { 2 -> finishedCleaning; 1 -> finishedProgram; else -> finishedEnjoy }
    abstract val bomApetite: String; abstract val allDone: String; abstract val cookElse: String; abstract val backToLast: String
    abstract val lastStep: String
    abstract fun upNext(s: String): String
    abstract fun weighGrams(g: Int): String
    abstract fun ofGrams(g: Int): String
    abstract val tapNextMachine: String; abstract val tapNextDone: String
    abstract val stepDone: String
    abstract val yourTurn: String
    abstract fun modeName(m: Mode, heat: Int, speed: Int, ramp: Boolean): String
    abstract fun modeUnit(m: Mode): String
    abstract fun notEnabled(mode: String): String
    abstract fun notEnabledHint(mode: String): String
    abstract fun stepSentence(s: Step.Machine): String
    abstract fun stepShort(s: Step.Machine): String
    abstract val revShort: String

    // settings
    abstract val settingsTitle: String; abstract val settingsSubtitle: String
    abstract val recipeServer: String; abstract val recipeServerHelp: String; abstract val address: String; abstract val saveSync: String
    abstract fun recipesAvailable(n: Int): String
    abstract val link: String; abstract val powerBoard: String; abstract val serialLoop: String; abstract val app: String
    abstract fun firmware(v: String): String
    abstract val saved: String; abstract val notSet: String
    abstract val homeAssistant: String; abstract val broker: String; abstract val port: String; abstract val user: String; abstract val password: String
    abstract val passwordHint: String; abstract val save: String; abstract val haSaved: String; abstract val haHelp: String
    abstract val on: String; abstract val notConnected: String; abstract val connectedWord: String; abstract val connecting: String
    abstract val safetyTitle: String; abstract val safetyLines: List<String>; abstract val fontCredit: String
    abstract val language: String; abstract val about: String
    abstract val location: String; abstract val locationHint: String; abstract val haArea: String; abstract val haAreaHint: String
    abstract val appearance: String; abstract val themeAuto: String; abstract val themeDay: String; abstract val themeNight: String
    abstract fun themeHelp(auto: Boolean, day: Boolean, sunrise: String, sunset: String): String
    abstract fun loopStats(avg: Long, max: Long, late: Int): String

    // service / machine messages
    abstract val notificationTitle: String; abstract val notificationText: String
    abstract val recipeRunning: String; abstract val stopMachineFirst: String
    abstract fun reject(r: StartResult.Rejected): String
    abstract fun stopReason(r: StopReason?): String
    abstract fun boardError(e: BoardError?): String
    abstract val modeTurbo: String; abstract val modeKnead: String; abstract val modeRoast: String; abstract val modeSteam: String

    /** Runner event as a sentence for the Cook screen. */
    fun event(e: RecipeRunner.Event?): String? = when (e) {
        null -> null
        RecipeRunner.Event.Finished -> finishedEnjoy
        RecipeRunner.Event.StepDone -> "✓ $stepDone"
        RecipeRunner.Event.Paused -> paused
        is RecipeRunner.Event.Stopped -> stopReason(e.reason)
        is RecipeRunner.Event.CannotStart -> reject(e.rejected)
        is RecipeRunner.Event.WrongAmount -> wrongAmount(e.grams, e.min, e.max)
    }

    protected fun runMode(ordinal: Int) = when (mcc.machine.RunMode.values().getOrNull(ordinal)) {
        mcc.machine.RunMode.TURBO -> modeTurbo; mcc.machine.RunMode.KNEAD -> modeKnead
        mcc.machine.RunMode.ROAST -> modeRoast; mcc.machine.RunMode.STEAM -> modeSteam
        else -> "?"
    }
}

/** Active language. */
object L {
    @Volatile var s: Strings = Pt
    fun set(code: String) { s = if (code == "en") En else Pt }
}

object En : Strings() {
    override val code = "en"
    override val locale: java.util.Locale = java.util.Locale.UK
    override val navHome = "Home"; override val navManual = "Manual"; override val navRecipes = "Recipes"
    override val navCook = "Cook"; override val navSettings = "Settings"
    override val keepRecipe = "Keep recipe"; override val cancelRecipe = "Cancel recipe"; override val cancelRecipeTitle = "Cancel this recipe?"
    override fun cancelRecipeBody(title: String) = "$title closes and the machine stops if it is running. You can open the recipe again from Recipes."
    override val continueLabel = "Continue"; override val manualPausedInfo = "Paused · Continue carries on with the time left, STOP ends it"; override val switchRecipeTitle = "Leave the current recipe?"
    override fun switchRecipeBody(old: String, step: Int, new: String) = "“$old” is at step $step. Open “$new” instead?"
    override val switchRecipeOk = "Open the new one"
    override val navPrograms = "Programs"; override val programsSubtitle = "Tap one to prepare it · nothing starts until you hold Start"
    override val emptyBowlNeeded = "Before heating, the app needs the weight of the empty bowl."
    override val stop = "STOP"; override val stopped = "Stopped"
    override val ready = "Ready"; override val running = "Running"; override val offline = "Offline"; override val stockApp = "Stock app"
    override val lidOpen = "Lid open"; override val lidClosed = "Lid closed"
    override val lidShortOpen = "Open"; override val lidShortClosed = "Closed"
    override val locked = "Locked"; override val unlocked = "Lock"
    override val controlsLocked = "Controls locked"; override val controlsUnlocked = "Controls unlocked"
    override val unlockTitle = "Unlock controls?"
    override val unlockBody = "The motor and heater can then be started from this screen with a press-and-hold. Stay with the machine while it runs."
    override val unlock = "Unlock"; override val cancel = "Cancel"
    override fun greeting(hour: Int) = when (hour) { in 5..11 -> "Good morning"; in 12..18 -> "Good afternoon"; else -> "Good evening" }
    override val brand = "Monsieur Cuisine"

    override val homeTitle = "Kitchen"; override val machine = "Machine"; override val manualControl = "Manual control"
    override val scale = "Scale"; override val tare = "Tare"; override val scaleZeroed = "Scale zeroed"; override val cannotTare = "Can't zero the scale right now"
    override val bowl = "Bowl"; override val cooking = "In progress"; override val quickStart = "Quick start"; override val ideas = "Ideas"
    override val presetChop = "Chop"; override val presetBlend = "Blend"; override val presetStir = "Stir"; override val presetWarm = "Warm"
    override val stockAppUsing = "The original cooking app is using the machine."
    override val waitingBoard = "Waiting for the power board…"; override val attention = "Attention"
    override fun machineReports(e: String) = "The machine reports: $e"
    override val paused = "Paused"; override val closeLidToCook = "Close the lid to cook."
    override val readyUnlocked = "Controls unlocked."; override val readyLocked = "Controls locked."
    override val left = "left"
    override val notWhileRunning = "Not available while running"
    override fun foodInBowl(g: Int) = "$g g of food in the bowl"
    override val setEmptyBowlHint = "Tare zeroes the scale for weighing"
    override fun heatingTo(c: Int) = "Heating to $c °C"
    override val hotCareful = "Hot, careful"; override val cool = "Cool"; override val warm = "Warm"
    override val finishedEnjoy = "Finished. Enjoy your meal!"
    override fun stepOfTap(a: Int, b: Int) = "Step $a of $b · tap to continue"
    override val whatCooking = "What are we cooking?"
    override fun recipesTapBrowse(n: Int) = "$n recipes · tap to browse"
    override fun speedN(n: Int) = "Speed $n"

    override val manualSubtitle = "Drag a dial or turn the knob"; override val runningTapStop = "Running · tap Stop to end"
    override val time = "Time"; override val speed = "Speed"; override val temperature = "Temperature"
    override val reverse = "Reverse"; override val emptyBowl = "Empty bowl"
    override val reverseLimit = "Reverse runs at speed 3 or less"; override val heatLimit = "Heating runs at speed 3 or less"
    override val tagMine = "My recipes"; override val aiTitle = "AI assistant"
    override val aiAddress = "Server address (OpenAI-compatible)"; override val aiAddressHint = "e.g. http://nas.local:8080/v1"
    override val aiModel = "Model"; override val aiModelHint = "e.g. gpt-4o-mini or llama3.1"
    override val aiKey = "API key"; override val aiKeyHint = "API key (optional on your own server)"
    override val aiHelp = "Used to turn web pages or pasted text into guided recipes. It writes recipes only: it can never start the machine, and every step is checked against the machine limits."
    override val aiTest = "Save & test"; override fun aiOk(models: Int) = "AI server reachable ($models models)"; override val aiSaved = "AI settings saved"
    override val aiImport = "Import with AI"
    override val aiImportBody = "Paste a recipe's web address or the recipe text. Settings from other machines (\"10 min/100°C/speed 1\") are converted."
    override val aiImportHint = "https://… or recipe text"; override val aiImportGo = "Import"
    override val aiWorking = "The AI is writing the recipe… (this can take a minute)"
    override fun aiDone(title: String) = "Saved to My recipes: $title"
    override val aiNoTranslation = "the Portuguese copy could not be made"
    override fun aiFailed(why: String) = "Import failed: $why"
    override val aiNotSet = "Set up the AI server in Settings first"
    override fun aiChanges(n: Int) = if (n == 1) "1 step was adjusted to the machine limits" else "$n steps were adjusted to the machine limits"
    override val deleteRecipe = "Delete"; override val deleteRecipeBody = "Delete this recipe from the machine?"
    override val standbyTitle = "Standby"
    override fun standbyBody(s: Int) = "Not used for a while. The screen turns off in $s s and the controls lock. Touch anywhere to keep using it."
    override val standbyNow = "Turn off now"
    override val lightSound = "Light and sound"; override val soundSetting = "Sounds"; override val soundOff = "Off"; override val soundQuiet = "Quiet"; override val soundNormal = "Normal"
    override val ledAnimSetting = "Animated light ring"
    override val ledAnimHelp = "The ring follows the blade speed and the heat, fills green while you weigh and plays a rainbow when a recipe ends. Off: the original plain colours."
    override val ledBrightness = "Ring brightness"
    override val standbySetting = "Standby when idle"
    override val standbyHelp = "The screen turns off and the controls lock when nothing runs. The lid, the scale, the knob or the power button wake it."
    override val standbyAfter = "After"
    override val turbo = "Turbo"
    override val emptyBowlTitle = "Is the bowl empty?"
    override val emptyBowlBody = "Fit the empty bowl with the lid on. Heating then needs at least 50 g of food or liquid above this weight."
    override val emptyBowlOk = "Bowl is empty"; override val emptyBowlSaved = "Empty bowl saved"
    override val scaleNotReady = "Scale not ready: lid on, motor off, wait for a steady reading"
    override val lockedHint = "Controls are locked: tap the lock at the top"
    override val seconds = "seconds"; override val minutes = "minutes"
    override val stirringOff = "no stirring"; override val off = "Off"; override val noHeat = "no heat"
    override val remaining = "remaining"; override val target = "target"
    override fun bowlTemp(c: Int) = "bowl $c°"; override val bowlBelow37 = "bowl below 37°"
    override val stopwatch = "Stopwatch"; override val elapsed = "elapsed"; override fun waitingForHeat(c: Int) = "heating on to $c°"
    override val soupTitle = "Vegetable soup"; override val soupSubtitle = "Any vegetables, worked out from their weight"; override val soupVeg = "Vegetables, peeled"
    override val soupVegHelp = "No need to weigh anything: put the vegetables in when the recipe asks, as many as you like (300 g to 1.2 kg). The machine weighs them and works out the water, the time and the portions."
    override val soupKind = "Vegetables"; override val soupTender = "Tender"; override val soupMixed = "Mixed"; override val soupFirm = "Firm"
    override val soupKindHelp = "Tender: courgette, leek, spinach, tomato. Firm: potato, carrot, pumpkin, turnip."
    override val soupTexture = "Texture"; override val soupChunky = "Chunky"; override val soupRustic = "Rustic"; override val soupSmooth = "Smooth"; override val soupVelvety = "Velvety"
    override val soupOnion = "Onion and garlic base"; override val soupOnionHelp = "Softened in olive oil first: more flavour."
    override val soupStart = "Prepare the soup"; override val soupTile = "Any-vegetable soup"; override val soupTileMeta = "by weight · 100 °C"
    override fun recipeReadyAt(t: String) = "ready about $t"; override val tagCooked = "Cooked lately"
    override val autoWeighSetting = "Hands-free weighing"
    override val autoWeighHelp = "A weigh step moves on by itself once the amount has been in the bowl for 2 seconds. It never starts the machine."
    override val keepWarm = "Keep warm · 30 min at 70 °C"; override val keepWarmOn = "Keeping warm for 30 minutes"
    override fun soupExample(veg: Int, water: Int, minutes: Int, portions: Int) = "For example, $veg g of vegetables: $water g of water, $minutes min, about $portions portions"
    override fun wrongAmount(g: Int?, min: Int, max: Int) = when {
        g == null -> "Wait a moment for the scale to settle, then tap Next."
        g < min -> "Only $g g in the bowl: add vegetables, at least $min g."
        else -> "$g g is too much for the bowl: take some out, at most $max g."
    }
    override fun upTo(g: Int) = "up to $g g"
    override val holdToStart = "Hold to start"; override val holdToResume = "Hold to resume"; override val pause = "Pause"
    override val waitingMachine = "Waiting for the machine…"; override val closeLidToStart = "Close the lid to start"
    override val unlockToStart = "Controls locked · tap the lock at the top to unlock"
    override val heatNeedsReference = "To heat, the app needs the empty bowl's weight · tap here"
    override fun heatNeedsContent(now: Int) = "Heating needs at least 50 g in the bowl (now $now g)"
    override fun pressHold(summary: String) = "$summary · press and hold to start"
    override fun runningAt(speed: Int) = "Running at speed $speed"
    override fun duration(sec: Int) = when { sec < 60 -> "$sec s"; sec % 60 == 0 -> "${sec / 60} min"; else -> "${sec / 60} min ${sec % 60} s" }

    override val recipesTitle = "Recipes"
    override fun recipesCount(n: Int) = if (n == 1) "1 recipe" else "$n recipes"
    override fun stepsCount(n: Int) = "$n steps"
    override val offlineCopy = "offline copy"; override val serverUnreachable = "server unreachable, offline copy"
    override val onlineUnreachable = "Monsieur Cuisine online unreachable, offline copy"
    override val longRunsSetting = "Long programs"
    override val longRunsHelp = "Slow cooking, sous-vide and fermentation: up to 12 hours, only at 95 °C or less and speed 0–1. The lid and board errors still stop it. Stay nearby the first time."
    override val onlineSetting = "Monsieur Cuisine online recipes"
    override val onlineHelp = "Also lists the official recipes from the Monsieur Cuisine website. A recipe is downloaded when you open it, then works offline. Recipes your server already has are not shown twice."
    override val tagOnline = "Online"
    override val searchHint = "Search recipes, tags or ingredients (chicken, rice)"; override val syncing = "Syncing recipes…"; override val all = "All"
    override val noRecipes = "No recipes yet.\nSet the recipe server in Settings."
    override fun nothingMatches(q: String) = "Nothing matches “$q”."
    override val nothingInFilter = "No recipes with this filter.\nTap here to show all."
    override fun aiImporting(s: Int) = "Importing… $s s"
    override fun heatAdd(missing: Int) = "Add $missing g more to heat"
    override val autoLocked = "Controls locked after a few idle minutes"
    override fun opening(t: String) = "Opening $t…"
    override fun cannotOpen(m: String) = "Can't open the recipe: $m"
    override fun ofMax(n: Int) = "of $n"
    override fun minutesShort(m: Int) = if (m < 90) "$m min" else if (m % 60 == 0) "${m / 60} h" else "${m / 60} h ${m % 60}"
    override fun kcal(n: Int) = "$n kcal"
    override fun machineSteps(n: Int) = "$n machine"
    override val ingredients = "Ingredients"; override val converterNotes = "Converter notes"
    override val startCooking = "Start cooking"; override val nothingMoves = "Nothing moves until you press and hold Start on a machine step."
    override val scaledNote = "Amounts adjusted. Times and temperatures stay as written; keep below the bowl's MAX mark."

    override val cookTitle = "Cook"; override val guidedCooking = "Guided cooking"
    override fun stepOf(a: Int, b: Int) = "Step $a of $b"
    override fun aboutMinutes(m: Int) = "about $m min in total"
    override val kindPrepare = "PREPARE"; override val kindWeigh = "WEIGH"; override val kindMachine = "MACHINE"
    override val reZero = "Re-zero scale"; override val back = "Back"; override val next = "Next"; override val skip = "Skip"; override val finish = "Finish"
    override val nothingCooking = "Nothing cooking yet"
    override val nothingCookingBody = "Pick a recipe and the machine guides you step by step."
    override val browseRecipes = "Browse recipes"
    override val bomApetite = "Enjoy your meal!"; override val allDone = "All steps done."
    override val cleanShort = "Clean"
    override val youNeed = "You need"; override val notesWord = "Tip"
    override val startProgram = "Start program"; override val startCleaning = "Start cleaning"
    override val doneProgram = "Done!"; override val doneCleaning = "Bowl clean!"
    override val finishedProgram = "Program finished."; override val finishedCleaning = "Cleaning finished."
    override val cookElse = "Cook something else"; override val backToLast = "Back to the last step"
    override val lastStep = "Last step"
    override fun upNext(s: String) = "Up next · $s"
    override fun weighGrams(g: Int) = "weigh $g g"
    override fun ofGrams(g: Int) = "of $g g"
    override val tapNextMachine = "When you're ready, tap Next.\nThe machine step after this one waits for your Start."
    override val tapNextDone = "When you're done, tap Next\nor push the knob."
    override val stepDone = "Step done"
    override val yourTurn = "Your turn"
    override fun modeName(m: Mode, heat: Int, speed: Int, ramp: Boolean) = when (m) {
        Mode.TURBO -> "Turbo"; Mode.KNEAD -> "Knead"; Mode.ROAST -> "Roast"; Mode.STEAM -> "Steam"
        Mode.RUN -> when { ramp -> "Ramp"; heat > 0 -> "Cook"; speed >= 6 -> "Blend"; speed >= 4 -> "Chop"; else -> "Stir" }
    }
    override fun modeUnit(m: Mode) = when (m) { Mode.TURBO -> "full-speed burst"; Mode.KNEAD -> "dough program"; Mode.STEAM -> "steaming"; else -> "" }
    override fun notEnabled(mode: String) = "$mode not enabled yet"
    override fun notEnabledHint(mode: String) = "$mode is not available in this app yet. Skip this step, or use Manual at speed 10 for a few seconds."
    override fun stepSentence(s: Step.Machine): String {
        val t = longTime(s.segments.sumOf { it.seconds }); val s0 = s.segments.first()
        return when (s.mode) {
            Mode.TURBO -> "Turbo burst for $t."; Mode.KNEAD -> "Knead the dough for $t."; Mode.STEAM -> "Steam for $t."
            Mode.ROAST -> "Roast at ${if (s0.tempC > 0) s0.tempC else 130} °C for $t."
            Mode.RUN -> when {
                s.segments.size > 1 -> "Speed up from ${s0.speed} to ${s.segments.last().speed} over $t."
                s0.tempC > 0 -> "Cook at ${s0.tempC} °C for $t" + (if (s0.speed > 0) ", speed ${s0.speed}" else "") + (if (s0.reverse) ", reverse" else "") + "."
                s0.speed >= 6 -> "Blend for $t at speed ${s0.speed}."
                s0.speed >= 4 -> "Chop for $t at speed ${s0.speed}."
                else -> "Stir for $t at speed ${s0.speed}" + (if (s0.reverse) ", reverse" else "") + "."
            }
        }
    }
    override fun stepShort(s: Step.Machine): String {
        val t = duration(s.segments.sumOf { it.seconds }); val s0 = s.segments.first()
        return when (s.mode) {
            Mode.RUN -> if (s.segments.size > 1) "ramp ${s0.speed}→${s.segments.last().speed} · $t"
                else "$t · speed ${s0.speed}" + (if (s0.tempC > 0) " · ${s0.tempC} °C" else "") + (if (s0.reverse) " · reverse" else "")
            Mode.TURBO -> "turbo · $t"; Mode.KNEAD -> "knead · $t"; Mode.STEAM -> "steam · $t"
            Mode.ROAST -> "roast · $t · ${if (s0.tempC > 0) s0.tempC else 130} °C"
        }
    }
    private fun longTime(sec: Int) = when { sec < 60 -> "$sec seconds"; sec == 60 -> "1 minute"; sec % 60 == 0 -> "${sec / 60} minutes"; else -> "${sec / 60} min ${sec % 60} s" }
    override val revShort = "rev"

    override val settingsTitle = "Settings"; override val settingsSubtitle = "Language, machine and connections"
    override val recipeServer = "Recipe server"
    override val recipeServerHelp = "A folder with index.json, recipes/ and images/ on any web server (a NAS, a Raspberry Pi…)."
    override val address = "Address"; override val saveSync = "Save & sync"
    override fun recipesAvailable(n: Int) = "$n recipes available"
    override val link = "Link"; override val powerBoard = "Power board"; override val serialLoop = "Serial loop"; override val app = "App"
    override fun firmware(v: String) = "firmware $v"
    override val saved = "saved"; override val notSet = "not set"
    override val homeAssistant = "Home Assistant"; override val broker = "Broker"; override val port = "Port"; override val user = "User"; override val password = "Password"
    override val passwordHint = "MQTT password"; override val save = "Save"
    override val haSaved = "Home Assistant settings saved"
    override val haHelp = "Home Assistant can see the machine and stop, pause or tare it. It can start only a run you prepared and permitted here (switch below)."
    override val remote = "Remote"
    override val remoteOffHint = "Remote start is off. Switch it on in Settings → Home Assistant."
    override val remoteNoStep = "Go to a machine step of the recipe first"
    override val remoteTitle = "Allow remote start?"
    override fun remoteBody(what: String, minutes: Int) = "Home Assistant may start this once in the next $minutes min:\n$what\n\nLeave the lid closed and the bowl as it is: opening the lid, changing the weight, locking or pressing STOP cancels it."
    override val remoteAllow = "Allow"
    override fun remoteLeft(min: Int) = "Remote · $min min"
    override val remoteCancelled = "Remote start cancelled"
    override val remoteSetting = "Allow remote start"
    override val remoteSettingHelp = "Prepare a run or recipe step at the machine and tap Remote. Home Assistant can then start that run once, within the time below. All machine limits still apply."
    override val remoteWindow = "Permit lasts"
    override fun remoteArmed(minutes: Int) = "Home Assistant may start it in the next $minutes min"
    override val remoteLoadSetting = "Recipes from the phone"
    override val remoteLoadHelp = "The phone app can open a recipe here, ready to cook. It never starts anything: you still press Start at the machine. Refused while another recipe is in progress."
    override fun remoteLoaded(title: String) = "Sent from the phone: $title"
    override val on = "ON"; override val notConnected = "Not connected"; override val connectedWord = "Connected"; override val connecting = "Connecting…"
    override val safetyTitle = "Safety limits, always enforced"
    override val safetyLines = listOf(
        "Heating: speed ≤ 3, at least 50 g in the bowl, max 130 °C",
        "Reverse: speed ≤ 3 · max 99 min, 15 min above speed 6",
        "Stops on lid open, board error or lost contact (1.5 s)",
        "Turbo: only while held, max 10 s, never with heat or above 60 °C",
        "Knead, brown and Varoma: the stock presets, with the same limits",
    )
    override val fontCredit = "Font: Manrope (SIL Open Font License)"
    override val language = "Language"; override val about = "About this machine"
    override val location = "Location (for sunrise and sunset)"; override val locationHint = "latitude, longitude  e.g. 38.72, -9.14"
    override val haArea = "Area"; override val haAreaHint = "e.g. Kitchen (optional)"
    override val appearance = "Appearance"; override val themeAuto = "Auto"; override val themeDay = "Day"; override val themeNight = "Night"
    override fun themeHelp(auto: Boolean, day: Boolean, sunrise: String, sunset: String) =
        if (auto) "Follows the sun: day from $sunrise, night from $sunset. Now: ${if (day) "day" else "night"}."
        else "Fixed ${if (day) "day" else "night"} look. Auto switches at sunrise ($sunrise) and sunset ($sunset)."
    override fun loopStats(avg: Long, max: Long, late: Int) = "avg $avg ms · max $max ms · $late late"

    override val notificationTitle = "Monsieur Cuisine"; override val notificationText = "Connected to the machine"
    override val recipeRunning = "A recipe step is running"; override val stopMachineFirst = "Stop the machine first"
    override fun reject(r: StartResult.Rejected) = when (r.code) {
        RejectCode.BUSY -> "The machine is already running"
        RejectCode.NO_COMM -> "No contact with the power board"
        RejectCode.LID_OPEN -> "The lid is open"
        RejectCode.BOARD_ERROR -> "The machine reports an error (${r.value})"
        RejectCode.MODE_DISABLED -> notEnabled(runMode(r.value))
        RejectCode.BAD_SPEED -> "Speed must be 0 to 10"
        RejectCode.BAD_TEMP -> "Temperature must be 0 to ${r.value} °C"
        RejectCode.BAD_DURATION -> "Set a time first"
        RejectCode.TOO_LONG -> "Too long for this speed (max ${r.value} min)"
        RejectCode.LONG_RUNS_OFF -> "Long programs are off (max ${r.value} min): Settings → Machine"
        RejectCode.REVERSE_SPEED -> "Reverse runs at speed ${r.value} or less"
        RejectCode.NOTHING_TO_DO -> "Set a speed or a temperature"
        RejectCode.HEAT_SPEED -> "Heating runs at speed ${r.value} or less"
        RejectCode.NO_REFERENCE -> "Save the empty bowl before heating"
        RejectCode.TOO_LITTLE_CONTENT -> "Heating needs at least ${r.value} g in the bowl"
        RejectCode.TURBO_HEAT -> "Turbo can't heat"
        RejectCode.TURBO_REVERSE -> "Turbo only runs forward"
        RejectCode.TURBO_HOT -> "Turbo is blocked from ${r.value} °C"
        RejectCode.TURBO_TOO_LONG -> "Turbo is limited to ${r.value} s"
        RejectCode.KNEAD_HEAT -> "Kneading can't heat"
        RejectCode.KNEAD_SPEED -> "Kneading runs at speed ${r.value} or less"
        RejectCode.KNEAD_TOO_MUCH -> "Knead at most ${r.value} g of dough"
        RejectCode.ROAST_SPEED, RejectCode.STEAM_SPEED -> "This mode runs at speed ${r.value} or less"
        RejectCode.STEAM_NO_TEMP -> "Steaming needs a temperature"
        RejectCode.OTHER -> r.reason
    }
    override fun stopReason(r: StopReason?) = when (r) {
        StopReason.USER -> "Stopped"; StopReason.COMM_LOST -> "Stopped: lost contact with the power board"
        StopReason.LID_OPENED -> "Stopped: the lid was opened"; StopReason.BOARD_ERROR -> "Stopped: the machine reported an error"
        StopReason.MAX_RUNTIME -> "Stopped: maximum run time reached"; StopReason.COMPLETED -> stepDone; null -> stopped
    }
    override fun boardError(e: BoardError?) = when (e) {
        null -> "none"; BoardError.NO_WATER -> "no water / dry heating"; BoardError.MOTOR -> "motor fault"
        BoardError.MOTOR_OVERHEAT -> "motor overheated"; BoardError.OVERVOLTAGE -> "overvoltage"; BoardError.UNDERVOLTAGE -> "undervoltage"
        BoardError.SERIAL_COMMUNICATION -> "communication error"; BoardError.MAX_RUNTIME_EXCEEDED -> "maximum run time exceeded"; BoardError.UNKNOWN -> "unknown error"
    }
    override val modeTurbo = "Turbo"; override val modeKnead = "Kneading"; override val modeRoast = "Roasting"; override val modeSteam = "Steaming"
}

object Pt : Strings() {
    override val code = "pt"
    override val locale: java.util.Locale = java.util.Locale("pt", "PT")
    override val navHome = "Início"; override val navManual = "Manual"; override val navRecipes = "Receitas"
    override val navCook = "Cozinhar"; override val navSettings = "Definições"
    override val keepRecipe = "Manter receita"; override val cancelRecipe = "Cancelar receita"; override val cancelRecipeTitle = "Cancelar esta receita?"
    override fun cancelRecipeBody(title: String) = "$title fecha e a máquina para, se estiver a trabalhar. Pode voltar a abri-la em Receitas."
    override val continueLabel = "Continuar"; override val manualPausedInfo = "Em pausa · Continuar retoma com o tempo que falta, PARAR termina"; override val switchRecipeTitle = "Deixar a receita atual?"
    override fun switchRecipeBody(old: String, step: Int, new: String) = "“$old” está no passo $step. Abrir “$new” em vez disso?"
    override val switchRecipeOk = "Abrir a nova"
    override val navPrograms = "Programas"; override val programsSubtitle = "Toque num para o preparar · nada arranca sem manter Iniciar premido"
    override val emptyBowlNeeded = "Antes de aquecer, a app precisa do peso do copo vazio."
    override val stop = "PARAR"; override val stopped = "Parado"
    override val ready = "Pronta"; override val running = "A trabalhar"; override val offline = "Sem ligação"; override val stockApp = "App original"
    override val lidOpen = "Tampa aberta"; override val lidClosed = "Tampa fechada"
    override val lidShortOpen = "Aberta"; override val lidShortClosed = "Fechada"
    override val locked = "Bloqueado"; override val unlocked = "Bloquear"
    override val controlsLocked = "Comandos bloqueados"; override val controlsUnlocked = "Comandos desbloqueados"
    override val unlockTitle = "Desbloquear os comandos?"
    override val unlockBody = "O motor e o aquecimento passam a poder ser ligados neste ecrã, mantendo o botão premido. Fique junto da máquina enquanto trabalha."
    override val unlock = "Desbloquear"; override val cancel = "Cancelar"
    override fun greeting(hour: Int) = when (hour) { in 5..11 -> "Bom dia"; in 12..19 -> "Boa tarde"; else -> "Boa noite" }
    override val brand = "Monsieur Cuisine"

    override val homeTitle = "Cozinha"; override val machine = "Máquina"; override val manualControl = "Controlo manual"
    override val scale = "Balança"; override val tare = "Tarar"; override val scaleZeroed = "Balança a zero"; override val cannotTare = "Não é possível tarar agora"
    override val bowl = "Copo"; override val cooking = "Em curso"; override val quickStart = "Início rápido"; override val ideas = "Ideias"
    override val presetChop = "Picar"; override val presetBlend = "Triturar"; override val presetStir = "Mexer"; override val presetWarm = "Aquecer"
    override val stockAppUsing = "A aplicação original está a usar a máquina."
    override val waitingBoard = "À espera da placa de potência…"; override val attention = "Atenção"
    override fun machineReports(e: String) = "A máquina indica: $e"
    override val paused = "Em pausa"; override val closeLidToCook = "Feche a tampa para cozinhar."
    override val readyUnlocked = "Comandos desbloqueados."; override val readyLocked = "Comandos bloqueados."
    override val left = "restante"
    override val notWhileRunning = "Indisponível enquanto trabalha"
    override fun foodInBowl(g: Int) = "$g g de alimentos no copo"
    override val setEmptyBowlHint = "Tarar põe a balança a zero para pesar"
    override fun heatingTo(c: Int) = "A aquecer até $c °C"
    override val hotCareful = "Quente, cuidado"; override val cool = "Frio"; override val warm = "Morno"
    override val finishedEnjoy = "Concluído. Bom apetite!"
    override fun stepOfTap(a: Int, b: Int) = "Passo $a de $b · toque para continuar"
    override val whatCooking = "O que vamos cozinhar?"
    override fun recipesTapBrowse(n: Int) = "$n receitas · toque para explorar"
    override fun speedN(n: Int) = "Velocidade $n"

    override val manualSubtitle = "Rode os mostradores ou o botão"; override val runningTapStop = "A trabalhar · toque em Parar para terminar"
    override val time = "Tempo"; override val speed = "Velocidade"; override val temperature = "Temperatura"
    override val reverse = "Inverso"; override val emptyBowl = "Copo vazio"
    override val reverseLimit = "O sentido inverso vai até à velocidade 3"; override val heatLimit = "Com aquecimento, a velocidade vai até 3"
    override val tagMine = "As minhas receitas"; override val aiTitle = "Assistente IA"
    override val aiAddress = "Endereço do servidor (compatível com OpenAI)"; override val aiAddressHint = "p. ex. http://nas.local:8080/v1"
    override val aiModel = "Modelo"; override val aiModelHint = "p. ex. gpt-4o-mini ou llama3.1"
    override val aiKey = "Chave da API"; override val aiKeyHint = "Chave da API (opcional no seu servidor)"
    override val aiHelp = "Serve para transformar páginas web ou texto colado em receitas guiadas. Só escreve receitas: nunca pode ligar a máquina, e cada passo é verificado contra os limites da máquina."
    override val aiTest = "Guardar e testar"; override fun aiOk(models: Int) = "Servidor IA acessível ($models modelos)"; override val aiSaved = "Definições da IA guardadas"
    override val aiImport = "Importar com IA"
    override val aiImportBody = "Cole o endereço de uma receita ou o texto da receita. As definições de outras máquinas (\"10 min/100°C/vel 1\") são convertidas."
    override val aiImportHint = "https://… ou texto da receita"; override val aiImportGo = "Importar"
    override val aiWorking = "A IA está a escrever a receita… (pode demorar um minuto)"
    override fun aiDone(title: String) = "Guardada em As minhas receitas: $title"
    override val aiNoTranslation = "não foi possível fazer a cópia em inglês"
    override fun aiFailed(why: String) = "A importação falhou: $why"
    override val aiNotSet = "Configure primeiro o servidor IA nas Definições"
    override fun aiChanges(n: Int) = if (n == 1) "1 passo foi ajustado aos limites da máquina" else "$n passos foram ajustados aos limites da máquina"
    override val deleteRecipe = "Apagar"; override val deleteRecipeBody = "Apagar esta receita da máquina?"
    override val lightSound = "Luz e som"; override val soundSetting = "Sons"; override val soundOff = "Sem som"; override val soundQuiet = "Baixo"; override val soundNormal = "Normal"
    override val ledAnimSetting = "Anel de luz animado"
    override val ledAnimHelp = "O anel acompanha a velocidade e o calor, enche-se de verde enquanto pesa e faz um arco-íris no fim da receita. Desligado: as cores simples do original."
    override val ledBrightness = "Brilho do anel"
    override val standbyTitle = "Repouso"
    override fun standbyBody(s: Int) = "Sem utilização há algum tempo. O ecrã desliga-se em $s s e os comandos ficam bloqueados. Toque em qualquer lado para continuar."
    override val standbyNow = "Desligar já"
    override val standbySetting = "Repouso automático"
    override val standbyHelp = "O ecrã desliga-se e os comandos bloqueiam quando nada está a funcionar. A tampa, a balança, o botão rotativo ou o botão de ligar acordam-no."
    override val standbyAfter = "Depois de"
    override val turbo = "Turbo"
    override val emptyBowlTitle = "O copo está vazio?"
    override val emptyBowlBody = "Coloque o copo vazio com a tampa. Para aquecer serão depois precisos pelo menos 50 g de alimentos ou líquido acima deste peso."
    override val emptyBowlOk = "O copo está vazio"; override val emptyBowlSaved = "Copo vazio guardado"
    override val scaleNotReady = "Balança não está pronta: tampa colocada, motor parado, espere por um valor estável"
    override val lockedHint = "Comandos bloqueados: toque no cadeado em cima"
    override val seconds = "segundos"; override val minutes = "minutos"
    override val stirringOff = "sem mexer"; override val off = "Desl."; override val noHeat = "sem calor"
    override val remaining = "restante"; override val target = "alvo"
    override fun bowlTemp(c: Int) = "copo $c°"; override val bowlBelow37 = "copo abaixo de 37°"
    override val stopwatch = "Cronómetro"; override val elapsed = "decorrido"; override fun waitingForHeat(c: Int) = "a aquecer até $c°"
    override val soupTitle = "Sopa de legumes"; override val soupSubtitle = "Quaisquer legumes, calculada pelo peso"; override val soupVeg = "Legumes descascados"
    override val soupVegHelp = "Não precisa de pesar nada: junte os legumes quando a receita pedir, os que quiser (300 g a 1,2 kg). A máquina pesa-os e calcula a água, o tempo e as doses."
    override val soupKind = "Legumes"; override val soupTender = "Tenros"; override val soupMixed = "Mistos"; override val soupFirm = "Rijos"
    override val soupKindHelp = "Tenros: courgette, alho-francês, espinafres, tomate. Rijos: batata, cenoura, abóbora, nabo."
    override val soupTexture = "Textura"; override val soupChunky = "Com pedaços"; override val soupRustic = "Rústica"; override val soupSmooth = "Cremosa"; override val soupVelvety = "Aveludada"
    override val soupOnion = "Base de cebola e alho"; override val soupOnionHelp = "Refogada em azeite primeiro: mais sabor."
    override val soupStart = "Preparar a sopa"; override val soupTile = "Sopa de quaisquer legumes"; override val soupTileMeta = "pelo peso · 100 °C"
    override fun recipeReadyAt(t: String) = "pronta por volta das $t"; override val tagCooked = "Feitas há pouco"
    override val autoWeighSetting = "Pesagem sem mãos"
    override val autoWeighHelp = "Um passo de pesar avança sozinho quando a quantidade está no copo há 2 segundos. Nunca liga a máquina."
    override val keepWarm = "Manter quente · 30 min a 70 °C"; override val keepWarmOn = "A manter quente durante 30 minutos"
    override fun soupExample(veg: Int, water: Int, minutes: Int, portions: Int) = "Por exemplo, $veg g de legumes: $water g de água, $minutes min, cerca de $portions doses"
    override fun wrongAmount(g: Int?, min: Int, max: Int) = when {
        g == null -> "Espere um momento até a balança estabilizar e toque em Seguinte."
        g < min -> "Só $g g no copo: junte legumes, pelo menos $min g."
        else -> "$g g é demais para o copo: retire alguns, no máximo $max g."
    }
    override fun upTo(g: Int) = "até $g g"
    override val holdToStart = "Mantenha para iniciar"; override val holdToResume = "Mantenha para retomar"; override val pause = "Pausa"
    override val waitingMachine = "À espera da máquina…"; override val closeLidToStart = "Feche a tampa para começar"
    override val unlockToStart = "Comandos bloqueados · toque no cadeado em cima para desbloquear"
    override val heatNeedsReference = "Para aquecer, a app precisa do peso do copo vazio · toque aqui"
    override fun heatNeedsContent(now: Int) = "Para aquecer são precisos pelo menos 50 g no copo (agora $now g)"
    override fun pressHold(summary: String) = "$summary · mantenha premido para iniciar"
    override fun runningAt(speed: Int) = "A trabalhar na velocidade $speed"
    override fun duration(sec: Int) = when { sec < 60 -> "$sec s"; sec % 60 == 0 -> "${sec / 60} min"; else -> "${sec / 60} min ${sec % 60} s" }

    override val recipesTitle = "Receitas"
    override fun recipesCount(n: Int) = if (n == 1) "1 receita" else "$n receitas"
    override fun stepsCount(n: Int) = "$n passos"
    override val offlineCopy = "cópia local"; override val serverUnreachable = "servidor indisponível, cópia local"
    override val onlineUnreachable = "Monsieur Cuisine online indisponível, cópia local"
    override val longRunsSetting = "Programas longos"
    override val longRunsHelp = "Cozedura lenta, sous-vide e fermentação: até 12 horas, só a 95 °C ou menos e velocidade 0–1. A tampa e os erros da placa continuam a parar. Fique por perto da primeira vez."
    override val onlineSetting = "Receitas online do Monsieur Cuisine"
    override val onlineHelp = "Mostra também as receitas oficiais do site Monsieur Cuisine. Cada receita é transferida quando a abre e depois funciona sem internet. As receitas que o seu servidor já tem não aparecem duas vezes."
    override val tagOnline = "Online"
    override val searchHint = "Procurar receitas, etiquetas ou ingredientes (frango, arroz)"; override val syncing = "A sincronizar receitas…"; override val all = "Todas"
    override val noRecipes = "Ainda não há receitas.\nIndique o servidor de receitas nas Definições."
    override fun nothingMatches(q: String) = "Nada corresponde a “$q”."
    override val nothingInFilter = "Sem receitas com este filtro.\nToque aqui para ver todas."
    override fun aiImporting(s: Int) = "A importar… $s s"
    override fun heatAdd(missing: Int) = "Junte mais $missing g para aquecer"
    override val autoLocked = "Comandos bloqueados após alguns minutos sem uso"
    override fun opening(t: String) = "A abrir $t…"
    override fun cannotOpen(m: String) = "Não foi possível abrir a receita: $m"
    override fun ofMax(n: Int) = "de $n"
    override fun minutesShort(m: Int) = if (m < 90) "$m min" else if (m % 60 == 0) "${m / 60} h" else "${m / 60} h ${m % 60}"
    override fun kcal(n: Int) = "$n kcal"
    override fun machineSteps(n: Int) = "$n na máquina"
    override val ingredients = "Ingredientes"; override val converterNotes = "Notas da conversão"
    override val startCooking = "Começar a cozinhar"; override val nothingMoves = "Nada se mexe até manter premido Iniciar num passo da máquina."
    override val scaledNote = "Quantidades ajustadas. Tempos e temperaturas mantêm-se; não passe a marca MAX do copo."

    override val cookTitle = "Cozinhar"; override val guidedCooking = "Cozinha guiada"
    override fun stepOf(a: Int, b: Int) = "Passo $a de $b"
    override fun aboutMinutes(m: Int) = "cerca de $m min no total"
    override val kindPrepare = "PREPARAR"; override val kindWeigh = "PESAR"; override val kindMachine = "MÁQUINA"
    override val reZero = "Pôr a zero"; override val back = "Anterior"; override val next = "Seguinte"; override val skip = "Saltar"; override val finish = "Terminar"
    override val nothingCooking = "Ainda nada ao lume"
    override val nothingCookingBody = "Escolha uma receita e a máquina guia-o passo a passo."
    override val browseRecipes = "Ver receitas"
    override val bomApetite = "Bom apetite!"; override val allDone = "Todos os passos concluídos."
    override val cleanShort = "Limpar"
    override val youNeed = "Vai precisar de"; override val notesWord = "Dica"
    override val startProgram = "Iniciar programa"; override val startCleaning = "Começar limpeza"
    override val doneProgram = "Pronto!"; override val doneCleaning = "Copo limpo!"
    override val finishedProgram = "Programa concluído."; override val finishedCleaning = "Limpeza concluída."
    override val cookElse = "Cozinhar outra coisa"; override val backToLast = "Voltar ao último passo"
    override val lastStep = "Último passo"
    override fun upNext(s: String) = "A seguir · $s"
    override fun weighGrams(g: Int) = "pesar $g g"
    override fun ofGrams(g: Int) = "de $g g"
    override val tapNextMachine = "Quando estiver pronto, toque em Seguinte.\nO passo da máquina a seguir espera que o inicie."
    override val tapNextDone = "Quando terminar, toque em Seguinte\nou carregue no botão rotativo."
    override val stepDone = "Passo concluído"
    override val yourTurn = "A sua vez"
    override fun modeName(m: Mode, heat: Int, speed: Int, ramp: Boolean) = when (m) {
        Mode.TURBO -> "Turbo"; Mode.KNEAD -> "Amassar"; Mode.ROAST -> "Refogar"; Mode.STEAM -> "Vapor"
        Mode.RUN -> when { ramp -> "Rampa"; heat > 0 -> "Cozinhar"; speed >= 6 -> "Triturar"; speed >= 4 -> "Picar"; else -> "Mexer" }
    }
    override fun modeUnit(m: Mode) = when (m) { Mode.TURBO -> "impulso máximo"; Mode.KNEAD -> "programa de massa"; Mode.STEAM -> "cozedura a vapor"; else -> "" }
    override fun notEnabled(mode: String) = "$mode ainda não ativo"
    override fun notEnabledHint(mode: String) = "$mode ainda não está disponível nesta app. Salte este passo ou use o Manual na velocidade 10 durante alguns segundos."
    override fun stepSentence(s: Step.Machine): String {
        val t = longTime(s.segments.sumOf { it.seconds }); val s0 = s.segments.first()
        return when (s.mode) {
            Mode.TURBO -> "Turbo durante $t."; Mode.KNEAD -> "Amassar a massa durante $t."; Mode.STEAM -> "Cozer a vapor durante $t."
            Mode.ROAST -> "Refogar a ${if (s0.tempC > 0) s0.tempC else 130} °C durante $t."
            Mode.RUN -> when {
                s.segments.size > 1 -> "Acelerar da velocidade ${s0.speed} até ${s.segments.last().speed} em $t."
                s0.tempC > 0 -> "Cozinhar a ${s0.tempC} °C durante $t" + (if (s0.speed > 0) ", velocidade ${s0.speed}" else "") + (if (s0.reverse) ", sentido inverso" else "") + "."
                s0.speed >= 6 -> "Triturar durante $t na velocidade ${s0.speed}."
                s0.speed >= 4 -> "Picar durante $t na velocidade ${s0.speed}."
                else -> "Mexer durante $t na velocidade ${s0.speed}" + (if (s0.reverse) ", sentido inverso" else "") + "."
            }
        }
    }
    override fun stepShort(s: Step.Machine): String {
        val t = duration(s.segments.sumOf { it.seconds }); val s0 = s.segments.first()
        return when (s.mode) {
            Mode.RUN -> if (s.segments.size > 1) "rampa ${s0.speed}→${s.segments.last().speed} · $t"
                else "$t · vel. ${s0.speed}" + (if (s0.tempC > 0) " · ${s0.tempC} °C" else "") + (if (s0.reverse) " · inverso" else "")
            Mode.TURBO -> "turbo · $t"; Mode.KNEAD -> "amassar · $t"; Mode.STEAM -> "vapor · $t"
            Mode.ROAST -> "refogar · $t · ${if (s0.tempC > 0) s0.tempC else 130} °C"
        }
    }
    private fun longTime(sec: Int) = when { sec < 60 -> "$sec segundos"; sec == 60 -> "1 minuto"; sec % 60 == 0 -> "${sec / 60} minutos"; else -> "${sec / 60} min ${sec % 60} s" }
    override val revShort = "inv."

    override val settingsTitle = "Definições"; override val settingsSubtitle = "Idioma, máquina e ligações"
    override val recipeServer = "Servidor de receitas"
    override val recipeServerHelp = "Uma pasta com index.json, recipes/ e images/ em qualquer servidor web (um NAS, um Raspberry Pi…)."
    override val address = "Endereço"; override val saveSync = "Guardar e sincronizar"
    override fun recipesAvailable(n: Int) = "$n receitas disponíveis"
    override val link = "Ligação"; override val powerBoard = "Placa de potência"; override val serialLoop = "Ciclo série"; override val app = "Aplicação"
    override fun firmware(v: String) = "firmware $v"
    override val saved = "guardado"; override val notSet = "não definido"
    override val homeAssistant = "Home Assistant"; override val broker = "Broker"; override val port = "Porta"; override val user = "Utilizador"; override val password = "Palavra-passe"
    override val passwordHint = "Palavra-passe MQTT"; override val save = "Guardar"
    override val haSaved = "Definições do Home Assistant guardadas"
    override val haHelp = "O Home Assistant vê a máquina e pode pará-la, pô-la em pausa ou tarar. Só pode ligar uma execução que preparou e autorizou aqui (interruptor abaixo)."
    override val remote = "Remoto"
    override val remoteOffHint = "O início remoto está desligado. Ligue-o em Definições → Home Assistant."
    override val remoteNoStep = "Avance primeiro até um passo da máquina na receita"
    override val remoteTitle = "Permitir início remoto?"
    override fun remoteBody(what: String, minutes: Int) = "O Home Assistant pode iniciar isto uma vez nos próximos $minutes min:\n$what\n\nDeixe a tampa fechada e o copo como está: abrir a tampa, mudar o peso, bloquear ou carregar em PARAR cancela."
    override val remoteAllow = "Permitir"
    override fun remoteLeft(min: Int) = "Remoto · $min min"
    override val remoteCancelled = "Início remoto cancelado"
    override val remoteSetting = "Permitir início remoto"
    override val remoteSettingHelp = "Prepare uma execução ou um passo de receita na máquina e toque em Remoto. O Home Assistant pode então iniciá-la uma vez, dentro do tempo abaixo. Todos os limites da máquina continuam a aplicar-se."
    override val remoteWindow = "A autorização dura"
    override fun remoteArmed(minutes: Int) = "O Home Assistant pode iniciar nos próximos $minutes min"
    override val remoteLoadSetting = "Receitas do telemóvel"
    override val remoteLoadHelp = "A app do telemóvel pode abrir aqui uma receita, pronta a cozinhar. Nunca inicia nada: continua a carregar em Iniciar na máquina. Recusado se outra receita estiver a meio."
    override fun remoteLoaded(title: String) = "Enviada do telemóvel: $title"
    override val on = "LIG."; override val notConnected = "Sem ligação"; override val connectedWord = "Ligado"; override val connecting = "A ligar…"
    override val safetyTitle = "Limites de segurança, sempre aplicados"
    override val safetyLines = listOf(
        "Aquecimento: velocidade ≤ 3, pelo menos 50 g no copo, máx. 130 °C",
        "Inverso: velocidade ≤ 3 · máx. 99 min, 15 min acima da velocidade 6",
        "Para com a tampa aberta, erro da placa ou perda de ligação (1,5 s)",
        "Turbo: só enquanto premido, máx. 10 s, nunca com calor nem acima de 60 °C",
        "Amassar, alourar e Varoma: as predefinições originais, com os mesmos limites",
    )
    override val fontCredit = "Tipo de letra: Manrope (SIL Open Font License)"
    override val language = "Idioma"; override val about = "Sobre a máquina"
    override val location = "Localização (para o nascer e o pôr do sol)"; override val locationHint = "latitude, longitude  p. ex. 38.72, -9.14"
    override val haArea = "Divisão"; override val haAreaHint = "p. ex. Cozinha (opcional)"
    override val appearance = "Aspeto"; override val themeAuto = "Auto"; override val themeDay = "Dia"; override val themeNight = "Noite"
    override fun themeHelp(auto: Boolean, day: Boolean, sunrise: String, sunset: String) =
        if (auto) "Segue o sol: dia a partir das $sunrise, noite a partir das $sunset. Agora: ${if (day) "dia" else "noite"}."
        else "Aspeto fixo de ${if (day) "dia" else "noite"}. O modo Auto muda ao nascer ($sunrise) e ao pôr do sol ($sunset)."
    override fun loopStats(avg: Long, max: Long, late: Int) = "média $avg ms · máx. $max ms · $late atrasados"

    override val notificationTitle = "Monsieur Cuisine"; override val notificationText = "Ligado à máquina"
    override val recipeRunning = "Está a decorrer um passo da receita"; override val stopMachineFirst = "Pare primeiro a máquina"
    override fun reject(r: StartResult.Rejected) = when (r.code) {
        RejectCode.BUSY -> "A máquina já está a trabalhar"
        RejectCode.NO_COMM -> "Sem ligação à placa de potência"
        RejectCode.LID_OPEN -> "A tampa está aberta"
        RejectCode.BOARD_ERROR -> "A máquina indica um erro (${r.value})"
        RejectCode.MODE_DISABLED -> notEnabled(runMode(r.value))
        RejectCode.BAD_SPEED -> "A velocidade tem de ser de 0 a 10"
        RejectCode.BAD_TEMP -> "A temperatura tem de ser de 0 a ${r.value} °C"
        RejectCode.BAD_DURATION -> "Defina primeiro o tempo"
        RejectCode.TOO_LONG -> "Demasiado tempo para esta velocidade (máx. ${r.value} min)"
        RejectCode.LONG_RUNS_OFF -> "Programas longos desligados (máx. ${r.value} min): Definições → Máquina"
        RejectCode.REVERSE_SPEED -> "O sentido inverso vai até à velocidade ${r.value}"
        RejectCode.NOTHING_TO_DO -> "Defina uma velocidade ou uma temperatura"
        RejectCode.HEAT_SPEED -> "Com aquecimento, a velocidade vai até ${r.value}"
        RejectCode.NO_REFERENCE -> "Guarde o copo vazio antes de aquecer"
        RejectCode.TOO_LITTLE_CONTENT -> "Para aquecer são precisos pelo menos ${r.value} g no copo"
        RejectCode.TURBO_HEAT -> "O turbo não aquece"
        RejectCode.TURBO_REVERSE -> "O turbo só funciona para a frente"
        RejectCode.TURBO_HOT -> "O turbo fica bloqueado a partir de ${r.value} °C"
        RejectCode.TURBO_TOO_LONG -> "O turbo está limitado a ${r.value} s"
        RejectCode.KNEAD_HEAT -> "O modo amassar não aquece"
        RejectCode.KNEAD_SPEED -> "O modo amassar vai até à velocidade ${r.value}"
        RejectCode.KNEAD_TOO_MUCH -> "Amasse no máximo ${r.value} g de massa"
        RejectCode.ROAST_SPEED, RejectCode.STEAM_SPEED -> "Este modo vai até à velocidade ${r.value}"
        RejectCode.STEAM_NO_TEMP -> "O vapor precisa de uma temperatura"
        RejectCode.OTHER -> r.reason
    }
    override fun stopReason(r: StopReason?) = when (r) {
        StopReason.USER -> "Parado"; StopReason.COMM_LOST -> "Parado: perdeu-se a ligação à placa de potência"
        StopReason.LID_OPENED -> "Parado: a tampa foi aberta"; StopReason.BOARD_ERROR -> "Parado: a máquina indicou um erro"
        StopReason.MAX_RUNTIME -> "Parado: atingido o tempo máximo"; StopReason.COMPLETED -> stepDone; null -> stopped
    }
    override fun boardError(e: BoardError?) = when (e) {
        null -> "nenhum"; BoardError.NO_WATER -> "sem água / aquecimento a seco"; BoardError.MOTOR -> "avaria do motor"
        BoardError.MOTOR_OVERHEAT -> "motor sobreaquecido"; BoardError.OVERVOLTAGE -> "sobretensão"; BoardError.UNDERVOLTAGE -> "subtensão"
        BoardError.SERIAL_COMMUNICATION -> "erro de comunicação"; BoardError.MAX_RUNTIME_EXCEEDED -> "tempo máximo excedido"; BoardError.UNKNOWN -> "erro desconhecido"
    }
    override val modeTurbo = "Turbo"; override val modeKnead = "Amassar"; override val modeRoast = "Refogar"; override val modeSteam = "Vapor"
}
