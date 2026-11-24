package mcc.app

import mcc.link.MachineEvent

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import mcc.machine.MachineController
import mcc.machine.MachineSnapshot
import mcc.machine.Protocol
import mcc.machine.RunMode
import mcc.machine.ScaleFilter
import mcc.machine.RunRequest
import mcc.machine.StartResult
import mcc.machine.TxCommand
import mcc.recipe.MachinePlan
import mcc.recipe.Recipe
import mcc.recipe.Servings
import mcc.recipe.RecipeRunner
import mcc.app.i18n.L

/**
 * Owns /dev/ttyMT0 and the single [MachineController]. Every 100 ms: controller tick -> write frame ->
 * recipe runner tick, all under [lock]. Refuses to open the port while another process (the stock app's
 * machine service) holds it.
 *
 * Starting anything requires [armed], which is in memory only: every app start begins locked.
 */
class MachineService : Service() {
    companion object {
        const val TAG = "mcc"
        const val PORT = "/dev/ttyMT0"

        @Volatile var instance: MachineService? = null
            private set
        @Volatile var status: String = "starting"
            private set
        @Volatile var snapshot: MachineSnapshot? = null
            private set
        @Volatile var runnerState: RecipeRunner.State? = null
            private set
        @Volatile var lastEvent: String? = null
            private set
        @Volatile var armed: Boolean = false
            private set
        /** Serial loop timing over the last 10 s: the board expects a frame every 100 ms. */
        @Volatile var loopStats: String = "—"
            private set
        /** avg ms, max ms, late frames over the last 10 s window (for the translated Settings line). */
        @Volatile var loopTiming: Triple<Long, Long, Int>? = null
            private set
        /** Scale reading for display, calmed by [ScaleFilter] (screen taps press on the load cell). */
        @Volatile var displayGrams: Int? = null
            private set
        /** The run Home Assistant may start (permitted at the machine), or null. */
        @Volatile var remotePermit: RemotePermit? = null
            private set
        /** A manual run paused with time left (resume at the machine or with an armed Start), or null. */
        @Volatile var pausedRun: PausedRun? = null
            private set
        /** Full length of a resumed manual run (its request only holds what was left), for progress; null otherwise. */
        @Volatile var resumedTotalMillis: Long? = null
            private set
        /** Time an open (stopwatch) manual run had already counted before it was paused and resumed; 0 otherwise. */
        @Volatile var resumedElapsedMillis: Long = 0
            private set
        /** The manual run going (or last started) is the keep-warm run; reported to Home Assistant as `program`. */
        @Volatile var keepWarmRun = false
            private set
        /** What a running open manual run shows: its time so far, across pauses. */
        fun stopwatchMillis(s: MachineSnapshot): Long = s.elapsedMillis + resumedElapsedMillis
        /** Title of the last recipe the phone opened here, and a counter the UI watches to show it. */
        @Volatile var remoteLoaded: Pair<Int, String>? = null
            private set
        /** Bumped when the recipe index was re-downloaded here, so the UI reloads its list. */
        @Volatile var recipesRefreshed = 0
            private set
    }

    private val lock = Object()
    private val controller = MachineController { reason, detail ->
        lastEvent = "stopped: $reason${detail?.let { " ($it)" } ?: ""}"
        Log.w(TAG, "stop $reason $detail")
    }
    @Volatile private var runner: RecipeRunner? = null
    @Volatile private var running = true
    private var worker: Thread? = null
    private var mqtt: MqttBridge? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null
    private val led = Led()
    private val scaleFilter = ScaleFilter()
    private var ledWasRunning = false
    private var ledPhase: RecipeRunner.Phase? = null
    private val prefs by lazy { Prefs(this) }
    @Volatile private var screenOn = true
    private var screenCheckedAt = 0L

    val recipe: Recipe? get() = runner?.recipe

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), 0)
        @Suppress("DEPRECATION")
        val n = Notification.Builder(this)
            .setContentTitle(L.s.notificationTitle)
            .setContentText(L.s.notificationText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(open)
            .build()
        startForeground(1, n)
        acquireWake()
        worker = Thread(::loop, "mcc-machine").also { it.start() }
        led.start()
        restartMqtt()
        applyModes()
        restoreEmptyBowl()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        running = false
        worker?.interrupt()
        mqtt?.close()
        led.stop()
        releaseWake()
        instance = null
        status = "stopped"
        super.onDestroy()
    }

    fun restartMqtt() {
        mqtt?.close()
        val p = Prefs(this)
        mqtt = if (p.mqttEnabled && p.mqttHost.isNotBlank()) MqttBridge(this, p).also { it.start() } else null
    }

    // ---- remote start: prepared and permitted at the machine, started once from Home Assistant ----

    /**
     * [manual] is the exact run to start, or null for "the recipe's current machine step" ([recipeStep]).
     * [grams] is the scale reading when permitted: a change of more than [REMOTE_WEIGHT_TOLERANCE] g cancels it.
     */
    private val REMOTE_WEIGHT_TOLERANCE = 30

    data class RemotePermit(val manual: RunRequest?, val recipeStep: Int?, val untilElapsed: Long, val untilWall: Long, val grams: Int?, val label: String,
                            /** resumes [pausedRun] instead of starting [manual] afresh */ val resume: Boolean = false)

    /** [request] holds the time that was left; [totalMillis] the run's full length. */
    data class PausedRun(val request: RunRequest, val totalMillis: Long, val sinceElapsed: Long,
                         /** For an open (stopwatch) run: the time it had counted when paused. */ val elapsedMillis: Long = 0)

    /** A paused manual run is dropped after this long (food sitting warm; start again on purpose). */
    private val PAUSE_LIMIT_MS = 60 * 60_000L
    private val KEEP_WARM_C = 70
    private val KEEP_WARM_MS = 30 * 60_000L

    /** Returns null when permitted, else a message for the user. */
    fun allowRemote(manual: RunRequest?, label: String): String? = synchronized(lock) {
        val p = Prefs(this)
        val now = SystemClock.elapsedRealtime()
        val s = controller.snapshot(now)
        val f = s.lastFrame
        val resume = manual == null && pausedRun != null && runner?.phase != RecipeRunner.Phase.PAUSED && runner?.phase != RecipeRunner.Phase.READY
        val step = if (manual == null && !resume) runner?.let { r ->
            val st = r.state(now)
            if (st.step is mcc.recipe.Step.Machine && (r.phase == RecipeRunner.Phase.READY || r.phase == RecipeRunner.Phase.PAUSED)) st.stepIndex else null
        } else null
        val err = when {
            !p.remoteStart -> L.s.remoteOffHint
            !armed -> L.s.lockedHint
            !s.connected -> L.s.waitingMachine
            f?.lidOpen == true -> L.s.closeLidToStart
            f?.error != null -> L.s.machineReports(L.s.boardError(f.error))
            s.running -> L.s.stopMachineFirst
            manual == null && step == null && !resume -> L.s.remoteNoStep
            else -> null
        }
        if (err != null) return err
        val ms = p.remoteMinutes * 60_000L
        remotePermit = RemotePermit(if (resume) pausedRun!!.request else manual, step, now + ms, System.currentTimeMillis() + ms, s.scaleGrams, label, resume)
        lastEvent = "remote start permitted: $label"
        null
    }

    fun cancelRemote(why: String) {
        if (remotePermit != null) { remotePermit = null; lastEvent = "remote start cancelled: $why"; Log.i(TAG, "remote permit cancelled: $why") }
    }

    /** From Home Assistant. Starts the permitted run once; everything else is refused. */
    fun remoteStart(): String? {
        val pm = remotePermit ?: return "no remote start permitted at the machine".also { Log.w(TAG, it) }
        remotePermit = null
        if (!Prefs(this).remoteStart) return "remote start is switched off"
        val err = if (pm.resume) resumeManual() else if (pm.manual != null) startManual(pm.manual) else synchronized(lock) {
            val now = SystemClock.elapsedRealtime()
            val r = runner
            if (r == null || r.state(now).stepIndex != pm.recipeStep) "recipe moved on" else recipeStart()
        }
        val ev = if (err == null) "started from Home Assistant: ${pm.label}" else "remote start refused: $err"
        lastEvent = ev
        Log.i(TAG, ev)
        return err
    }

    /** Called from the machine loop: a permit ends when anything about the prepared bowl changes. */
    private fun checkPermit(s: MachineSnapshot, now: Long) {
        val pm = remotePermit ?: return
        val f = s.lastFrame
        val why = when {
            now > pm.untilElapsed -> "window expired"
            !armed -> "controls locked"
            !remoteEnabled -> "switched off"
            s.running -> "machine started"
            f?.lidOpen == true -> "lid opened"
            f?.error != null -> "board error"
            pm.grams != null && s.scaleGrams != null && kotlin.math.abs(s.scaleGrams!! - pm.grams) > REMOTE_WEIGHT_TOLERANCE -> "bowl contents changed"
            pm.recipeStep != null && runner?.state(now)?.stepIndex != pm.recipeStep -> "recipe moved on"
            pm.recipeStep != null && runner == null -> "recipe closed"
            pm.resume && pausedRun == null -> "paused run cancelled"
            else -> null
        }
        if (why != null) cancelRemote(why)
    }

    /** Turbo and the firmware work modes are tested and always on; the controller still applies their limits. */
    fun applyModes() {
        val p = Prefs(this)
        controller.userEnabledModes = setOf(RunMode.TURBO, RunMode.KNEAD, RunMode.ROAST, RunMode.STEAM)
        remoteEnabled = p.remoteStart
    }

    @Volatile private var remoteEnabled = false

    /** Estimated time until the loaded recipe is done, or null (none loaded, or finished). */
    fun recipeRemainingMillis(): Long? = synchronized(lock) { runner?.remainingRecipeMillis(SystemClock.elapsedRealtime()) }

    /** Settings changed hands-free weighing: the loaded recipe follows at once. */
    fun setAutoWeigh(on: Boolean) = synchronized(lock) { prefs.autoWeigh = on; runner?.autoAdvanceWeigh = on }

    /**
     * Keep warm after a heated recipe: 30 min at 70 °C, speed 1 in reverse (stirs without cutting, nothing sticks).
     * An ordinary manual run: the user holds Start for it, and every heating check applies.
     */
    fun keepWarm(): String? = startManual(RunRequest(1, KEEP_WARM_C, KEEP_WARM_MS, reverse = true)).also { if (it == null) keepWarmRun = true }

    /** Turbo runs while the button is held: start on press (capped by the controller), [turboEnd] on release. */
    fun turboStart(): String? = startManual(RunRequest(MachinePlan.TURBO_SPEED, 0, controller.limits.maxTurboMillis, false, RunMode.TURBO))

    fun turboEnd() = synchronized(lock) {
        val now = SystemClock.elapsedRealtime()
        if (controller.snapshot(now).request?.mode == RunMode.TURBO) { controller.stop(now); lastEvent = "turbo released" }
    }

    /** Whether the machine accepts this mode now: always-on modes plus the ones switched on in Settings. */
    fun modeEnabled(m: RunMode) = controller.modeEnabled(m)

    val mqttStatus: String get() = mqtt?.status ?: "off"

    // ---- commands from the UI / MQTT (all return null on success, else a message for the user) ----

    /** Locking only prevents new starts; a run in progress continues (STOP is always available). */
    fun setArmed(on: Boolean) {
        armed = on
    }

    /**
     * STOP. A running recipe step pauses (as before: resumable, a "paused" event); a manual run, or a paused one, ends
     * and sends a "stopped" event (reason stop_button / remote_stop) so automations can tell it from "finished".
     */
    fun stop(why: String = "stop pressed", remote: Boolean = false) = synchronized(lock) {
        val now = SystemClock.elapsedRealtime()
        cancelRemote(why)
        val r = runner
        val reason = if (remote) "remote_stop" else "stop_button"
        if (r != null && r.phase == RecipeRunner.Phase.RUNNING) {
            r.pause(now); runnerState = r.state(now)
            publishPaused(reason, now)
        } else {
            val s = controller.snapshot(now)
            val cutShort = (s.running && s.request?.mode != RunMode.TURBO) || pausedRun != null
            controller.stop(now)
            pausedRun = null; resumedTotalMillis = null; resumedElapsedMillis = 0
            if (cutShort) mqtt?.publishEvent(MachineEvent(MachineEvent.STOPPED, reason = reason, recipe = recipe?.title))
        }
        lastEvent = why
    }

    /** Pause from Home Assistant or the phone: a recipe step or a manual run pauses with its time kept. */
    fun pauseFromRemote() = synchronized(lock) {
        val now = SystemClock.elapsedRealtime()
        val r = runner
        if (r != null && r.phase == RecipeRunner.Phase.RUNNING) {
            r.pause(now); runnerState = r.state(now); lastEvent = "paused from Home Assistant"
            publishPaused("remote", now)
        } else pauseManual("remote", now)
    }

    /** Pauses a manual run (not turbo, which is hold-to-run) keeping the time left; null when it paused. */
    fun pauseManual(reason: String, now: Long = SystemClock.elapsedRealtime()): Boolean = synchronized(lock) {
        val s = controller.snapshot(now)
        val req = s.request
        if (!s.running || req == null) return false
        if (req.mode == RunMode.TURBO || s.remainingMillis < 1_000) { controller.stop(now); return false }
        pausedRun = PausedRun(req.copy(durationMillis = s.remainingMillis), resumedTotalMillis ?: req.durationMillis, now,
            elapsedMillis = if (req.open) stopwatchMillis(s) else 0)
        controller.stop(now)
        lastEvent = "manual run paused ($reason)"
        publishPaused(reason, now)
        true
    }

    /** Resumes [pausedRun] (from the machine, or an armed Start); null on success, else a message for the user. */
    fun resumeManual(): String? = synchronized(lock) {
        val p = pausedRun ?: return null
        val e = startManual(p.request, resume = true)
        if (e == null) { resumedTotalMillis = p.totalMillis; resumedElapsedMillis = p.elapsedMillis; pausedRun = null; lastEvent = "manual run resumed" }
        e
    }

    private fun publishPaused(reason: String, now: Long) {
        val r = recipe
        val st = runner?.state(now)
        mqtt?.publishEvent(MachineEvent(MachineEvent.PAUSED, reason = reason, recipe = r?.title,
            step = if (r != null && st != null) "${st.stepIndex + 1}/${r.steps.size}" else null))
    }

    private fun checkPaused(now: Long) {
        val p = pausedRun ?: return
        if (now - p.sinceElapsed > PAUSE_LIMIT_MS) {
            pausedRun = null; resumedTotalMillis = null; lastEvent = "paused run dropped after an hour"
            mqtt?.publishEvent(MachineEvent(MachineEvent.STOPPED, reason = "pause_expired"))
        }
    }

    /**
     * The phone asked to open a recipe (by id) on the Cook screen. It never starts anything. Refused when switched off,
     * while the machine runs, or while another recipe is under way.
     */
    fun remoteLoad(id: String, servings: Int? = null) {
        if (!prefs.remoteLoad) { Log.i(TAG, "remote load '$id' ignored: switched off"); return }
        Thread {
            val busy = synchronized(lock) {
                val st = runnerState
                snapshot?.running == true || (runner != null && runner?.recipe?.id != id && st != null &&
                    st.phase != RecipeRunner.Phase.DONE && (st.stepIndex > 0 || st.phase != RecipeRunner.Phase.READY))
            }
            if (busy) { Log.i(TAG, "remote load '$id' refused: busy"); lastEvent = "remote load refused (busy)"; return@Thread }
            val store = RecipeStore(this) { prefs.repoUrl }
            // not in the cached index: it may have just been published, so ask the server once
            val entry = store.loadIndex(false).first?.recipes?.firstOrNull { it.id == id }
                ?: store.loadIndex(true).first?.recipes?.firstOrNull { it.id == id }?.also { recipesRefreshed++ }
            val r = entry?.let { runCatching { store.loadRecipe(it) }.getOrNull() }?.let { if (servings != null) Servings.scale(it, servings) else it }
            when {
                r == null -> Log.w(TAG, "remote load: recipe '$id' not found")
                loadRecipe(r) == null -> {
                    remoteLoaded = ((remoteLoaded?.first ?: 0) + 1) to r.title
                    lastEvent = "recipe opened from the phone"
                    wakeScreen()
                }
            }
        }.start()
    }

    /** Downloads the recipe index again (the phone published a recipe); the UI picks it up through [recipesRefreshed]. */
    fun refreshRecipes() {
        Thread {
            val (_, warning) = RecipeStore(this) { prefs.repoUrl }.loadIndex(true)
            Log.i(TAG, "recipe index refreshed from the phone${warning?.let { ": $it" } ?: ""}")
            recipesRefreshed++
        }.start()
    }

    // The empty-bowl reference survives an app restart (updates, crashes) but not a reboot: after a power cut the
    // board zeroes its scale again, so an old reading could let an empty bowl heat. Then the app asks again.
    private val bootId by lazy { runCatching { java.io.File("/proc/sys/kernel/random/boot_id").readText().trim() }.getOrDefault("") }
    private var savedRef: Int? = null

    private fun restoreEmptyBowl() {
        val (boot, grams) = prefs.emptyBowl.split(":").takeIf { it.size == 2 } ?: return
        val g = grams.toIntOrNull() ?: return
        if (bootId.isNotEmpty() && boot == bootId) synchronized(lock) { controller.restoreEmptyReference(g); savedRef = g }
    }

    /** Called from the serial loop: saves the reference whenever it changes (set, tare, cleared). */
    private fun keepEmptyBowl() {
        val ref = controller.emptyReference
        if (ref == savedRef || bootId.isEmpty()) return
        savedRef = ref
        prefs.emptyBowl = if (ref == null) "" else "$bootId:$ref"
    }

    fun setEmptyBowl(): String? = synchronized(lock) {
        if (controller.setEmptyBowlReference(SystemClock.elapsedRealtime())) null
        else L.s.scaleNotReady
    }

    fun tare(): String? = synchronized(lock) {
        if (controller.requestTare(SystemClock.elapsedRealtime())) null else L.s.cannotTare
    }

    fun startManual(req: RunRequest, resume: Boolean = false): String? = synchronized(lock) {
        if (!armed) return L.s.lockedHint
        if (!resume) keepWarmRun = false
        if (runner?.phase == RecipeRunner.Phase.RUNNING) return L.s.recipeRunning
        when (val r = controller.start(req, SystemClock.elapsedRealtime())) {
            is StartResult.Started -> {
                lastEvent = "running"
                // a new manual run replaces a paused one (turbo pulses don't)
                if (!resume && req.mode != RunMode.TURBO) { pausedRun = null; resumedTotalMillis = null; resumedElapsedMillis = 0 }
                null
            }
            is StartResult.Rejected -> L.s.reject(r)
        }
    }

    /**
     * Cancels the loaded recipe: stops the machine if a step runs and closes the recipe. Allowed while locked and from
     * Home Assistant or the phone, like STOP. A recipe under way sends "stopped" (reason recipe_cancelled).
     */
    fun cancelRecipe(remote: Boolean): Boolean = synchronized(lock) {
        val r = runner ?: return false
        val now = SystemClock.elapsedRealtime()
        val st = r.state(now)
        // an interruption only if the machine ran something for it: browsing the steps and closing is not
        val underway = st.phase != RecipeRunner.Phase.DONE && recipeRan
        cancelRemote("recipe cancelled")
        if (controller.snapshot(now).running) controller.stop(now)
        runner = null; runnerState = null
        lastEvent = if (remote) "recipe cancelled remotely" else "recipe cancelled"
        if (underway) mqtt?.publishEvent(MachineEvent(MachineEvent.STOPPED, reason = "recipe_cancelled", recipe = r.recipe.title))
        true
    }

    fun loadRecipe(recipe: Recipe?): String? = synchronized(lock) {
        val now = SystemClock.elapsedRealtime()
        if (snapshot?.running == true) return L.s.stopMachineFirst
        runner = recipe?.let { RecipeRunner(it, controller).apply { autoAdvanceWeigh = prefs.autoWeigh } }
        recipeRan = false
        runnerState = runner?.state(now)
        null
    }

    fun recipeNext() = withRunner { r, now -> r.next(now) }
    fun recipeBack() = withRunner { r, now -> r.back(now) }
    fun recipePause() = withRunner { r, now -> val was = r.phase == RecipeRunner.Phase.RUNNING; r.pause(now); if (was) publishPaused("machine", now); true }
    fun recipeStart(): String? {
        if (!armed) return L.s.lockedHint
        var msg: String? = null
        withRunner { r, now -> r.start(now).also { if (!it) msg = L.s.event(r.state(now).event) ?: L.s.stopped } }
        return msg
    }

    private inline fun withRunner(block: (RecipeRunner, Long) -> Boolean): Boolean = synchronized(lock) {
        val r = runner ?: return false
        val now = SystemClock.elapsedRealtime()
        val ok = block(r, now)
        runnerState = r.state(now)
        ok
    }

    // ---- serial loop ----

    private fun loop() {
        while (running) {
            val holders = PortGuard.otherHolders(this, PORT)
            if (holders.isNotEmpty()) {
                led.suspended = true
                status = "BLOCKED: port used by ${holders.joinToString { "${it.name} (${it.pid})" }}"
                sleep(2_000)
                continue
            }
            try {
                SerialPort.open(PORT, Protocol.BAUD).use { port -> session(port) }
            } catch (e: Exception) {
                status = "error: ${e.message}"
                Log.e(TAG, "port session failed", e)
                sleep(2_000)
            }
        }
    }

    private fun session(port: SerialPort) {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)
        led.suspended = false
        status = "connected"
        Log.i(TAG, "port opened")
        val reader = Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)
            val buf = ByteArray(256)
            while (running && !Thread.currentThread().isInterrupted) {
                val n = try { port.read(buf) } catch (e: Exception) { Log.e(TAG, "read failed", e); -1 }
                if (n < 0) break
                if (n > 0) synchronized(lock) { controller.onBytes(buf, n, SystemClock.elapsedRealtime()) }
            }
        }, "mcc-reader")
        reader.start()

        var lastGuard = 0L
        var next = SystemClock.elapsedRealtime()
        var lastWrite = 0L
        var maxGap = 0L
        var late = 0
        var sumGap = 0L
        var gaps = 0
        var windowStart = next
        try {
            while (running && reader.isAlive) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastGuard > 2_000) {
                    lastGuard = now
                    val holders = PortGuard.otherHolders(this, PORT)
                    if (holders.isNotEmpty()) {
                        synchronized(lock) { controller.stop(now) }
                        status = "BLOCKED: another process opened the port, closing"
                        Log.w(TAG, "other holder appeared: $holders")
                        return
                    }
                }
                val frame = synchronized(lock) { controller.tick(now) }
                port.write(frame)
                if (lastWrite != 0L) {
                    val gap = now - lastWrite
                    maxGap = maxOf(maxGap, gap); sumGap += gap; gaps++
                    if (gap > 150) late++
                }
                lastWrite = now
                if (now - windowStart >= 10_000) {
                    loopStats = "avg ${if (gaps > 0) sumGap / gaps else 0} ms · max $maxGap ms · $late late"
                    loopTiming = Triple(if (gaps > 0) sumGap / gaps else 0L, maxGap, late)
                    if (maxGap > 250) Log.w(TAG, "serial loop gap $maxGap ms")
                    maxGap = 0; late = 0; sumGap = 0; gaps = 0; windowStart = now
                }
                synchronized(lock) {
                    runner?.let { it.tick(now); runnerState = it.state(now) }
                    val s = controller.snapshot(now)
                    snapshot = s
                    displayGrams = scaleFilter.update(s.scaleGrams, s.lastFrame)
                    checkPermit(s, now)
                    checkPaused(now)
                    keepEmptyBowl()
                    updateLed(s, now)
                    trackEvents(s)
                }
                mqtt?.publishIfChanged()
                // fixed-rate 100 ms schedule (not 100 ms + work time); resync after a long stall
                next += 100
                val wait = next - SystemClock.elapsedRealtime()
                if (wait > 0) sleep(wait) else if (wait < -500) next = SystemClock.elapsedRealtime()
            }
        } finally {
            reader.interrupt()
            synchronized(lock) { controller.stop(SystemClock.elapsedRealtime()) }
            try { port.write(TxCommand.STOP.encode()) } catch (_: Exception) {}
            status = "disconnected"
        }
    }

    // ---- standby (screen off), as the stock app does after 7 min without use ----

    @Volatile private var standbyLid: Boolean? = null
    @Volatile private var standbyGrams: Int? = null
    @Volatile private var inStandby = false

    /** Turns the screen off and locks the controls. Refused while anything runs. */
    fun standby(): Boolean = synchronized(lock) {
        val s = controller.snapshot(SystemClock.elapsedRealtime())
        if (s.running || runner?.phase == RecipeRunner.Phase.RUNNING) return false
        armed = false
        standbyLid = s.lastFrame?.lidOpen; standbyGrams = s.scaleGrams; inStandby = true; sleptAt = SystemClock.elapsedRealtime()
        lastEvent = "standby"
        power("goToSleep")
    }

    /** Screen back on (lid opened or closed, something put on the scale, a board error, a run started). */
    fun wakeScreen() { inStandby = false; power("wakeUp") }

    /** `wake` from Home Assistant: screen on. The controls stay as they were (locked after standby); nothing starts. */
    fun wakeFromRemote() { wakeScreen(); "screen woken from Home Assistant".let { lastEvent = it; Log.i(TAG, it) } }

    /** Whether the tablet's screen is on (checked once a second by the loop). */
    val screenIsOn: Boolean get() = screenOn

    private fun power(method: String): Boolean = try {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        pm.javaClass.getMethod(method, Long::class.javaPrimitiveType).invoke(pm, SystemClock.uptimeMillis())
        true
    } catch (e: Exception) { Log.w(TAG, "power $method failed", e); false }

    private fun checkWake(s: MachineSnapshot) {
        if (!inStandby) return
        // the screen came back on by itself (power button, knob): standby is over
        if (screenOn && SystemClock.elapsedRealtime() - sleptAt > 3_000) { inStandby = false; return }
        val f = s.lastFrame
        val g = s.scaleGrams; val g0 = standbyGrams
        val why = when {
            s.running -> "run started"
            f?.error != null -> "board error"
            f != null && standbyLid != null && f.lidOpen != standbyLid -> "lid"
            g != null && g0 != null && kotlin.math.abs(g - g0) > 20 -> "scale"
            else -> null
        }
        if (why != null) { Log.i(TAG, "wake from standby: $why"); wakeScreen() }
    }

    @Volatile private var sleptAt = 0L

    // ---- one-off events for Home Assistant (mcc/event) ----
    private var evWasRunning = false
    private var evStepIndex = -1
    private var evPhase: RecipeRunner.Phase? = null
    private var evWeighed = false
    /** Whether the loaded recipe has run anything on the machine (only then is closing it an interruption). */
    @Volatile private var recipeRan = false
    private var evError: Any? = null

    /** Edge detection on the loop's state: what a phone notification should say, sent once. */
    private fun trackEvents(s: MachineSnapshot) {
        val m = mqtt
        val rs = runnerState
        val r = recipe
        val title = r?.title
        val err = s.lastFrame?.error
        if (err != null && evError == null) m?.publishEvent(MachineEvent(MachineEvent.ERROR, error = err.name.lowercase(), recipe = title))
        evError = err

        if (rs != null && r != null) {
            if (rs.phase == RecipeRunner.Phase.RUNNING) recipeRan = true
            when {
                rs.phase == RecipeRunner.Phase.DONE && evPhase != RecipeRunner.Phase.DONE && evPhase != null ->
                    { m?.publishEvent(MachineEvent(MachineEvent.FINISHED, recipe = title)); prefs.addCooked(r.id) }
                // a machine step ended and the recipe moved on: every next step waits for someone at the machine
                evPhase == RecipeRunner.Phase.RUNNING && rs.phase == RecipeRunner.Phase.READY && rs.stepIndex != evStepIndex ->
                    m?.publishEvent(MachineEvent(MachineEvent.NEEDS_YOU, recipe = title, step = "${rs.stepIndex + 1}/${r.steps.size}",
                        text = rs.step?.text?.take(200)?.ifBlank { null }))
            }
            val step = rs.step
            // a steady reading only: a jolt while pouring can read high for a moment
            val done = step is mcc.recipe.Step.Weigh && rs.weighedGrams != null && rs.weighedGrams!! >= step.grams * 0.95 && s.lastFrame?.scaleStable == true
            if (done && !evWeighed && rs.stepIndex == evStepIndex) m?.publishEvent(MachineEvent(MachineEvent.WEIGHED, recipe = title, grams = (step as mcc.recipe.Step.Weigh).grams))
            evWeighed = done
            evStepIndex = rs.stepIndex
        } else { evWeighed = false; evStepIndex = -1 }

        if (evWasRunning && !s.running && rs?.phase != RecipeRunner.Phase.RUNNING) {
            when (val why = s.lastStop) {
                mcc.machine.StopReason.COMPLETED -> if (evPhase != RecipeRunner.Phase.RUNNING) m?.publishEvent(MachineEvent(MachineEvent.RUN_DONE)) // manual run, not a recipe step
                mcc.machine.StopReason.USER, null -> {}
                else -> m?.publishEvent(MachineEvent(MachineEvent.STOPPED, reason = why.name.lowercase(), recipe = title))
            }
        }
        evWasRunning = s.running
        evPhase = rs?.phase
    }

    private fun updateLed(s: MachineSnapshot, now: Long) {
        if (now - screenCheckedAt > 1_000) {
            screenCheckedAt = now
            screenOn = (getSystemService(POWER_SERVICE) as PowerManager).isInteractive
        }
        checkWake(s)
        val f = s.lastFrame
        val rs = runnerState
        // events
        if (ledWasRunning && !s.running && rs?.phase != RecipeRunner.Phase.RUNNING) {
            when (s.lastStop) {
                mcc.machine.StopReason.COMPLETED -> if (rs?.phase != RecipeRunner.Phase.DONE) led.flash(Led.GREEN)
                mcc.machine.StopReason.USER, null -> {}
                else -> led.flash(Led.RED)
            }
        }
        if (rs?.phase == RecipeRunner.Phase.DONE && ledPhase != RecipeRunner.Phase.DONE && ledPhase != null) {
            if (prefs.ledAnimated) led.rainbow() else led.flash(Led.GREEN)
        }
        ledPhase = rs?.phase
        ledWasRunning = s.running
        led.brightness = prefs.ledBrightness / 100f
        // base state
        val step = rs?.step
        val weighed = rs?.weighedGrams
        val rich = prefs.ledAnimated
        val req = s.request
        val bowl = f?.temperatureStepC ?: 0
        when {
            !s.connected -> led.set(Led.Pattern.OFF, Led.OFF)
            f?.error != null -> led.set(Led.Pattern.BLINK, Led.RED, 1000)
            s.running && rich && req?.mode == mcc.machine.RunMode.TURBO -> led.set(Led.Pattern.STROBE, Led.WHITE.mix(Led.CYAN, 0.5f), 150)
            s.running && rich -> {
                // pulse speed follows the motor (2.6 s at speed 0-1 down to 0.5 s at 10); colour follows the bowl
                val spd = req?.speedLevel ?: 0
                val color = if ((req?.temperatureC ?: 0) > 0) Led.forTemperature(maxOf(bowl, 50)) else Led.WHITE
                led.set(Led.Pattern.PULSE, color, (2600 - spd * 210).toLong())
            }
            s.running -> led.set(Led.Pattern.PULSE, if ((req?.temperatureC ?: 0) > 0) Led.AMBER else Led.WHITE)
            rs?.phase == RecipeRunner.Phase.PAUSED -> led.set(if (rich) Led.Pattern.BREATHE else Led.Pattern.STEADY, Led.AMBER, 3000)
            step is mcc.recipe.Step.Weigh && weighed != null && weighed >= step.grams * 0.95 -> led.set(Led.Pattern.STEADY, Led.GREEN)
            // weighing: the ring fills from white to green as the target gets close
            rich && step is mcc.recipe.Step.Weigh && weighed != null && weighed > 0 -> led.set(Led.Pattern.STEADY, Led.WHITE.mix(Led.GREEN, weighed / step.grams.toFloat()))
            !screenOn -> led.set(Led.Pattern.OFF, Led.OFF)
            rich && remotePermit != null -> led.set(Led.Pattern.BREATHE, Led.CYAN, 3200)
            rich && bowl >= 60 -> led.set(Led.Pattern.BREATHE, Led.forTemperature(bowl), 2600) // hot bowl: careful
            rich && f?.lidOpen == true -> led.set(Led.Pattern.STEADY, Led.WHITE.scaled(0.4f))
            else -> led.set(Led.Pattern.STEADY, Led.WHITE)
        }
    }

    /**
     * Keeps the CPU and Wi-Fi awake for as long as the service runs (the machine is mains powered): the 100 ms command
     * stream never stalls, the lid and scale are watched in standby, and Home Assistant keeps its connection with the
     * screen off. An earlier lock with a 100-minute timeout let the tablet fall asleep in a long standby, and Home
     * Assistant lost the machine until someone touched it.
     */
    private fun acquireWake() {
        if (wakeLock?.isHeld != true) wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mcc:link").apply { setReferenceCounted(false); acquire() }
        if (wifiLock?.isHeld != true) wifiLock = try {
            (applicationContext.getSystemService(WIFI_SERVICE) as android.net.wifi.WifiManager)
                .createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "mcc:link").apply { setReferenceCounted(false); acquire() }
        } catch (e: Exception) { Log.w(TAG, "no wifi lock", e); null }
    }

    private fun releaseWake() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        wifiLock?.let { if (it.isHeld) it.release() }
        wifiLock = null
    }

    private fun sleep(ms: Long) = try { Thread.sleep(ms) } catch (_: InterruptedException) {}
}
