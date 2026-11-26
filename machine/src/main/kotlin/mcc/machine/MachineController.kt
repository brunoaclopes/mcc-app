package mcc.machine

/** Hard limits enforced by the machine layer itself, independent of the UI or recipe layer. */
data class SafetyLimits(
    val maxTemperatureC: Int = 130,
    val maxSpeedWithHeat: Int = 3,          // stock app: SPEED_MAX_HEATER_ON
    val maxSpeedReverse: Int = 3,           // stock app: SPEED_MAX_REVERSE
    val maxRunMillis: Long = 99 * 60_000L,  // stock app: TIME_MAX_MILLIS
    val highSpeedAbove: Int = 6,
    val maxRunMillisHighSpeed: Long = 15 * 60_000L,
    /**
     * Long low-temperature runs (slow cooking, sous-vide, fermentation, as on the Monsieur Cuisine Smart): a normal,
     * heated run at [longRunMaxTempC] or less and speed [longRunMaxSpeed] or less may last [maxLongRunMillis],
     * but only while [MachineController.longRunsEnabled] is on (Settings, after a supervised test). Every other
     * check (lid, board error, lost frames, the heating minimum) still applies for the whole run.
     */
    val maxLongRunMillis: Long = 12 * 3600_000L,
    val longRunMaxTempC: Int = 95,
    val longRunMaxSpeed: Int = 1,
    /** Stop if no valid board frame for this long (board sends every ~500 ms). */
    val commTimeoutMillis: Long = 1_500L,
    /**
     * Minimum content (grams above the confirmed empty-bowl reference) required to heat. The stock app has no such
     * minimum (the board itself reports dry heating, error 6, which stops any run); this only refuses a bowl that
     * is still empty. The scale reads to a few grams.
     */
    val minContentForHeatGrams: Int = 50,
    /** Roasting heats a little oil + onions on purpose; still refuse a (nearly) empty bowl. */
    val minContentForRoastGrams: Int = 50,
    /**
     * Modes the controller will send. NORMAL (work command 01) frames are byte-identical to captured stock
     * app frames; its first live run from this app still needs a supervised test. The other modes mirror the
     * stock app but stay disabled until each one has had a supervised test.
     */
    val enabledModes: Set<RunMode> = setOf(RunMode.NORMAL),
    /** Modes a user may switch on in Settings once tested. Turbo never heats and is capped at [maxTurboMillis]. */
    val userEnableableModes: Set<RunMode> = setOf(RunMode.TURBO, RunMode.KNEAD, RunMode.ROAST, RunMode.STEAM),
    /** Our own cap per turbo press (recipes use 2-5 s pulses). */
    val maxTurboMillis: Long = 10_000L,
    /** Stock app blocks turbo at a measured temperature >= 60 °C. */
    val turboBlockedFromC: Int = 60,
    val maxKneadSpeed: Int = 4,
    /** Stock app: kneading at most 600 g of dough. */
    val maxKneadContentGrams: Int = 600,
    /** After a tare the board needs a moment before its weight readings use the new zero. */
    val tareSettleMillis: Long = 1_500L,
)

/** Work modes of the power board, mapped to the work command byte. */
enum class RunMode(val command: WorkCommand) {
    NORMAL(WorkCommand.RUN),
    TURBO(WorkCommand.TURBO),
    KNEAD(WorkCommand.KNEAD),
    ROAST(WorkCommand.TOAST),
    STEAM(WorkCommand.STEAM),
}

/**
 * What the user or a recipe step asks for. Temperatures in °C, 0 = no heat.
 * [open]: no set time, like the stock app's time 0:00. It runs until stopped, counting up, and the controller caps it
 * at the maximum run time for its speed ([durationMillis], when above 0, lowers that cap, e.g. for a resumed run).
 * Only normal runs can be open.
 */
data class RunRequest(
    val speedLevel: Int,
    val temperatureC: Int,
    val durationMillis: Long,
    val reverse: Boolean = false,
    val mode: RunMode = RunMode.NORMAL,
    val open: Boolean = false,
)

sealed class StartResult {
    object Started : StartResult()
    /** [reason] is English for logs/tests; UIs translate [code] (with [value], e.g. a limit or grams). */
    data class Rejected(val reason: String, val code: RejectCode = RejectCode.OTHER, val value: Int = 0) : StartResult()
}

enum class RejectCode {
    BUSY, NO_COMM, LID_OPEN, BOARD_ERROR, MODE_DISABLED, BAD_SPEED, BAD_TEMP, BAD_DURATION, TOO_LONG,
    REVERSE_SPEED, NOTHING_TO_DO, HEAT_SPEED, NO_REFERENCE, TOO_LITTLE_CONTENT,
    TURBO_HEAT, TURBO_REVERSE, TURBO_HOT, TURBO_TOO_LONG, KNEAD_HEAT, KNEAD_SPEED, KNEAD_TOO_MUCH,
    ROAST_SPEED, STEAM_SPEED, STEAM_NO_TEMP, LONG_RUNS_OFF, OTHER,
}

enum class StopReason { COMPLETED, USER, COMM_LOST, LID_OPENED, BOARD_ERROR, MAX_RUNTIME }

data class MachineSnapshot(
    val connected: Boolean,
    val lastFrame: RxFrame?,
    val running: Boolean,
    val request: RunRequest?,
    val remainingMillis: Long,
    /** Time since the running request started (0 when idle); what an open run shows. */
    val elapsedMillis: Long,
    /** Grams above the empty-bowl reference; null if unknown (no reference, running, or tare settling). */
    val contentGrams: Int?,
    /** Board weight since the last tare; null while running or while a tare is settling. */
    val scaleGrams: Int?,
    val hasEmptyReference: Boolean,
    val lastStop: StopReason?,
    val lastStopDetail: String?,
)

/** What the recipe layer and the UI need from the machine. Implemented by [MachineController]. */
interface MachineApi {
    fun snapshot(nowMillis: Long): MachineSnapshot
    fun start(req: RunRequest, nowMillis: Long): StartResult
    fun stop(nowMillis: Long)
    fun requestTare(nowMillis: Long): Boolean
}

/**
 * Owns the command stream to the power board. Pure logic: the caller feeds received bytes via [onBytes]
 * and calls [tick] every 100 ms, writing the returned frame to the serial port. Every tick re-checks all
 * limits; any violation turns the output into STOP and ends the run.
 */
class MachineController(
    val limits: SafetyLimits = SafetyLimits(),
    private val onStopped: (StopReason, String?) -> Unit = { _, _ -> },
) : MachineApi {
    private val reader = RxFrameReader()
    private var lastFrame: RxFrame? = null
    private var lastFrameAt: Long = Long.MIN_VALUE
    private var request: RunRequest? = null
    private var startedAt: Long = 0
    private var emptyReferenceGrams: Int? = null
    private var tarePending = false
    private var tareSettleUntil: Long = Long.MIN_VALUE
    private var lastStop: StopReason? = null
    private var lastStopDetail: String? = null

    /**
     * Extra modes the user switched on after testing them (Settings). Only modes listed in
     * [SafetyLimits.userEnableableModes] count, and every per-mode limit still applies.
     */
    @Volatile var userEnabledModes: Set<RunMode> = emptySet()

    fun modeEnabled(m: RunMode) = m in limits.enabledModes || (m in userEnabledModes && m in limits.userEnableableModes)

    /** Long low-temperature runs allowed ([SafetyLimits.maxLongRunMillis]); the user turns this on in Settings. */
    @Volatile var longRunsEnabled: Boolean = false

    /** Whether [req] is the kind of run the long-run rule covers (whether or not it is switched on). */
    fun isLongRunKind(req: RunRequest) = req.mode == RunMode.NORMAL && !req.open &&
        req.temperatureC in 1..limits.longRunMaxTempC && req.speedLevel <= limits.longRunMaxSpeed

    fun onBytes(bytes: ByteArray, count: Int, nowMillis: Long) {
        val frames = reader.feed(bytes, count)
        if (frames.isNotEmpty()) {
            lastFrame = frames.last()
            lastFrameAt = nowMillis
        }
    }

    fun isConnected(nowMillis: Long): Boolean =
        lastFrame != null && nowMillis - lastFrameAt <= limits.commTimeoutMillis

    private fun weightUsable(nowMillis: Long): Boolean {
        val f = lastFrame ?: return false
        return isConnected(nowMillis) && !f.running && !tarePending && nowMillis >= tareSettleUntil
    }

    /** User confirms the bowl is empty (lid on). Content for the heat check is measured from here. */
    fun setEmptyBowlReference(nowMillis: Long): Boolean {
        val f = lastFrame ?: return false
        if (!weightUsable(nowMillis) || !f.scaleStable) return false
        emptyReferenceGrams = f.weightGrams; lastContentGrams = null
        return true
    }

    fun clearEmptyBowlReference() { emptyReferenceGrams = null; lastContentGrams = null }

    /** The empty-bowl reading (board grams), for keeping it across an app restart; null when not set. */
    val emptyReference: Int? get() = emptyReferenceGrams

    /** Puts back a reference saved by this same boot (the board keeps its scale zero while powered). */
    fun restoreEmptyReference(grams: Int) { emptyReferenceGrams = grams }

    fun contentGrams(nowMillis: Long): Int? {
        val ref = emptyReferenceGrams ?: return null
        if (!weightUsable(nowMillis)) return null // board reports 0 g while the motor/heater runs
        return (lastFrame!!.weightGrams - ref).also { lastContentGrams = it }
    }

    /**
     * The last content reading taken while the scale was usable. A heated start right after a pause, or the next part
     * of a step, comes while the board still reports "running" and gives no weight; nothing was added or removed in
     * those seconds, so the reading from just before counts. Lifting the bowl is still seen: the board is idle then.
     */
    private var lastContentGrams: Int? = null

    private fun contentForStart(nowMillis: Long): Int? = contentGrams(nowMillis) ?: lastContentGrams?.takeIf { emptyReferenceGrams != null }

    /**
     * Zeroes the board scale with the next idle frame. The empty-bowl reference is shifted by the current
     * reading so the content measurement survives the tare.
     */
    override fun requestTare(nowMillis: Long): Boolean {
        val f = lastFrame ?: return false
        if (request != null || !weightUsable(nowMillis)) return false
        emptyReferenceGrams = emptyReferenceGrams?.let { it - f.weightGrams }
        tarePending = true
        return true
    }

    override fun start(asked: RunRequest, nowMillis: Long): StartResult {
        val f = lastFrame
        if (asked.open && asked.mode != RunMode.NORMAL) return StartResult.Rejected("only normal runs can be open", RejectCode.BAD_DURATION)
        val cap = maxRunFor(asked)
        val req = if (asked.open) asked.copy(durationMillis = if (asked.durationMillis > 0) minOf(asked.durationMillis, cap) else cap) else asked
        fun reject(r: String, code: RejectCode, v: Int = 0) = StartResult.Rejected(r, code, v)
        when {
            request != null -> return reject("already running", RejectCode.BUSY)
            f == null || !isConnected(nowMillis) -> return reject("no communication with the power board", RejectCode.NO_COMM)
            f.lidOpen -> return reject("lid is open", RejectCode.LID_OPEN)
            f.errorFlag || f.errorCode != 0 -> return reject("board reports error ${f.error}", RejectCode.BOARD_ERROR, f.errorCode)
            !modeEnabled(req.mode) -> return reject("${req.mode.name.lowercase()} mode is not enabled yet", RejectCode.MODE_DISABLED, req.mode.ordinal)
            req.speedLevel !in 0..10 -> return reject("speed must be 0..10", RejectCode.BAD_SPEED)
            req.temperatureC < 0 || req.temperatureC > limits.maxTemperatureC ->
                return reject("temperature must be 0..${limits.maxTemperatureC} °C", RejectCode.BAD_TEMP, limits.maxTemperatureC)
            req.durationMillis <= 0 -> return reject("duration must be > 0", RejectCode.BAD_DURATION)
            req.durationMillis > maxRunFor(req) && isLongRunKind(req) && !longRunsEnabled && req.durationMillis <= limits.maxLongRunMillis ->
                return reject("long low-temperature runs are switched off", RejectCode.LONG_RUNS_OFF, (limits.maxRunMillis / 60_000).toInt())
            req.durationMillis > maxRunFor(req) ->
                return reject("duration exceeds limit for speed ${req.speedLevel}", RejectCode.TOO_LONG, (maxRunFor(req) / 60_000).toInt())
            req.reverse && req.speedLevel > limits.maxSpeedReverse && req.mode != RunMode.KNEAD ->
                return reject("reverse allows speed <= ${limits.maxSpeedReverse}", RejectCode.REVERSE_SPEED, limits.maxSpeedReverse)
            req.mode == RunMode.NORMAL && req.speedLevel == 0 && req.temperatureC == 0 ->
                return reject("nothing to do (speed 0, no heat)", RejectCode.NOTHING_TO_DO)
        }
        modeRejection(req, f!!, nowMillis)?.let { return it }
        if (req.temperatureC > 0) {
            if (req.speedLevel > limits.maxSpeedWithHeat)
                return reject("heating allows speed <= ${limits.maxSpeedWithHeat}", RejectCode.HEAT_SPEED, limits.maxSpeedWithHeat)
            val content = contentForStart(nowMillis) ?: return reject("set the empty-bowl reference before heating", RejectCode.NO_REFERENCE)
            val min = if (req.mode == RunMode.ROAST) limits.minContentForRoastGrams else limits.minContentForHeatGrams
            if (content < min) return reject("only $content g in the bowl; heating needs >= $min g", RejectCode.TOO_LITTLE_CONTENT, min)
        }
        request = req
        startedAt = nowMillis
        lastStop = null
        lastStopDetail = null
        return StartResult.Started
    }

    private fun modeRejection(req: RunRequest, f: RxFrame, nowMillis: Long): StartResult.Rejected? {
        fun r(s: String, c: RejectCode, v: Int = 0) = StartResult.Rejected(s, c, v)
        return when (req.mode) {
            RunMode.NORMAL -> null
            RunMode.TURBO -> when {
                req.temperatureC > 0 -> r("turbo cannot heat", RejectCode.TURBO_HEAT)
                req.reverse -> r("turbo only runs forward", RejectCode.TURBO_REVERSE)
                f.temperatureStepC >= limits.turboBlockedFromC -> r("turbo is blocked at ${f.temperatureStepC} °C", RejectCode.TURBO_HOT, limits.turboBlockedFromC)
                req.durationMillis > limits.maxTurboMillis -> r("turbo is limited to ${limits.maxTurboMillis / 1000} s", RejectCode.TURBO_TOO_LONG, (limits.maxTurboMillis / 1000).toInt())
                else -> null
            }
            RunMode.KNEAD -> when {
                req.temperatureC > 0 -> r("kneading cannot heat", RejectCode.KNEAD_HEAT)
                req.speedLevel > limits.maxKneadSpeed -> r("kneading allows speed <= ${limits.maxKneadSpeed}", RejectCode.KNEAD_SPEED, limits.maxKneadSpeed)
                (contentGrams(nowMillis) ?: 0) > limits.maxKneadContentGrams -> r("kneading allows <= ${limits.maxKneadContentGrams} g", RejectCode.KNEAD_TOO_MUCH, limits.maxKneadContentGrams)
                else -> null
            }
            RunMode.ROAST -> if (req.speedLevel > 1) r("roasting allows speed <= 1", RejectCode.ROAST_SPEED, 1) else null
            RunMode.STEAM -> when {
                req.speedLevel > 1 -> r("steaming allows speed <= 1", RejectCode.STEAM_SPEED, 1)
                req.temperatureC == 0 -> r("steaming needs a temperature", RejectCode.STEAM_NO_TEMP)
                else -> null
            }
        }
    }

    override fun stop(nowMillis: Long) = end(StopReason.USER, null)

    /** Call every ~100 ms; returns the frame to write. */
    fun tick(nowMillis: Long): ByteArray {
        val req = request
        if (req == null) {
            if (tarePending) {
                tarePending = false
                tareSettleUntil = nowMillis + limits.tareSettleMillis
                return TxCommand(tare = true).encode()
            }
            return TxCommand.STOP.encode()
        }
        val f = lastFrame
        val elapsed = nowMillis - startedAt
        val violation: Pair<StopReason, String?>? = when {
            !isConnected(nowMillis) -> StopReason.COMM_LOST to "no board frame for ${nowMillis - lastFrameAt} ms"
            f!!.lidOpen -> StopReason.LID_OPENED to null
            f.errorFlag || f.errorCode != 0 -> StopReason.BOARD_ERROR to "${f.error} (code ${f.errorCode})"
            elapsed > maxRunFor(req) -> StopReason.MAX_RUNTIME to null
            req.mode == RunMode.TURBO && elapsed > limits.maxTurboMillis -> StopReason.MAX_RUNTIME to "turbo"
            req.open && elapsed >= req.durationMillis -> StopReason.MAX_RUNTIME to "open run"
            elapsed >= req.durationMillis -> StopReason.COMPLETED to null
            else -> null
        }
        if (violation != null) {
            end(violation.first, violation.second)
            return TxCommand.STOP.encode()
        }
        return TxCommand(
            command = req.mode.command,
            speedLevel = req.speedLevel,
            temperatureLevel = TemperatureTable.levelAtMost(req.temperatureC),
            reverse = req.reverse,
        ).encode()
    }

    override fun snapshot(nowMillis: Long) = MachineSnapshot(
        connected = isConnected(nowMillis),
        lastFrame = lastFrame,
        running = request != null,
        request = request,
        remainingMillis = request?.let { (it.durationMillis - (nowMillis - startedAt)).coerceAtLeast(0) } ?: 0,
        elapsedMillis = if (request != null) nowMillis - startedAt else 0,
        contentGrams = contentGrams(nowMillis),
        scaleGrams = if (weightUsable(nowMillis)) lastFrame!!.weightGrams else null,
        hasEmptyReference = emptyReferenceGrams != null,
        lastStop = lastStop,
        lastStopDetail = lastStopDetail,
    )

    private fun maxRunFor(req: RunRequest): Long = when {
        longRunsEnabled && isLongRunKind(req) -> limits.maxLongRunMillis
        req.speedLevel > limits.highSpeedAbove -> minOf(limits.maxRunMillis, limits.maxRunMillisHighSpeed)
        else -> limits.maxRunMillis
    }

    private fun end(reason: StopReason, detail: String?) {
        if (request == null) return
        request = null
        lastStop = reason
        lastStopDetail = detail
        onStopped(reason, detail)
    }
}
