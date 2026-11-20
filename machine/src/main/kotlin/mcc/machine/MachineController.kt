package mcc.machine

/** Hard limits enforced by the machine layer itself, independent of the UI or recipe layer. */
data class SafetyLimits(
    val maxTemperatureC: Int = 130,
    val maxSpeedWithHeat: Int = 3,          // stock app: SPEED_MAX_HEATER_ON
    val maxSpeedReverse: Int = 3,           // stock app: SPEED_MAX_REVERSE
    val maxRunMillis: Long = 99 * 60_000L,  // stock app: TIME_MAX_MILLIS
    val highSpeedAbove: Int = 6,
    val maxRunMillisHighSpeed: Long = 15 * 60_000L,
    /** Stop if no valid board frame for this long (board sends every ~500 ms). */
    val commTimeoutMillis: Long = 1_500L,
    /** Minimum content (grams above the confirmed empty-bowl reference) required to heat. */
    val minContentForHeatGrams: Int = 200,
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

/** What the user or a recipe step asks for. Temperatures in °C, 0 = no heat. */
data class RunRequest(
    val speedLevel: Int,
    val temperatureC: Int,
    val durationMillis: Long,
    val reverse: Boolean = false,
    val mode: RunMode = RunMode.NORMAL,
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
    ROAST_SPEED, STEAM_SPEED, STEAM_NO_TEMP, OTHER,
}

enum class StopReason { COMPLETED, USER, COMM_LOST, LID_OPENED, BOARD_ERROR, MAX_RUNTIME }

data class MachineSnapshot(
    val connected: Boolean,
    val lastFrame: RxFrame?,
    val running: Boolean,
    val request: RunRequest?,
    val remainingMillis: Long,
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

    private fun modeEnabled(m: RunMode) = m in limits.enabledModes || (m in userEnabledModes && m in limits.userEnableableModes)

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
        emptyReferenceGrams = f.weightGrams
        return true
    }

    fun clearEmptyBowlReference() { emptyReferenceGrams = null }

    fun contentGrams(nowMillis: Long): Int? {
        val ref = emptyReferenceGrams ?: return null
        if (!weightUsable(nowMillis)) return null // board reports 0 g while the motor/heater runs
        return lastFrame!!.weightGrams - ref
    }

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

    override fun start(req: RunRequest, nowMillis: Long): StartResult {
        val f = lastFrame
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
            req.durationMillis > maxRunFor(req.speedLevel) ->
                return reject("duration exceeds limit for speed ${req.speedLevel}", RejectCode.TOO_LONG, (maxRunFor(req.speedLevel) / 60_000).toInt())
            req.reverse && req.speedLevel > limits.maxSpeedReverse && req.mode != RunMode.KNEAD ->
                return reject("reverse allows speed <= ${limits.maxSpeedReverse}", RejectCode.REVERSE_SPEED, limits.maxSpeedReverse)
            req.mode == RunMode.NORMAL && req.speedLevel == 0 && req.temperatureC == 0 ->
                return reject("nothing to do (speed 0, no heat)", RejectCode.NOTHING_TO_DO)
        }
        modeRejection(req, f!!, nowMillis)?.let { return it }
        if (req.temperatureC > 0) {
            if (req.speedLevel > limits.maxSpeedWithHeat)
                return reject("heating allows speed <= ${limits.maxSpeedWithHeat}", RejectCode.HEAT_SPEED, limits.maxSpeedWithHeat)
            val content = contentGrams(nowMillis) ?: return reject("set the empty-bowl reference before heating", RejectCode.NO_REFERENCE)
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
            elapsed > maxRunFor(req.speedLevel) -> StopReason.MAX_RUNTIME to null
            req.mode == RunMode.TURBO && elapsed > limits.maxTurboMillis -> StopReason.MAX_RUNTIME to "turbo"
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
        contentGrams = contentGrams(nowMillis),
        scaleGrams = if (weightUsable(nowMillis)) lastFrame!!.weightGrams else null,
        hasEmptyReference = emptyReferenceGrams != null,
        lastStop = lastStop,
        lastStopDetail = lastStopDetail,
    )

    private fun maxRunFor(speed: Int) =
        if (speed > limits.highSpeedAbove) minOf(limits.maxRunMillis, limits.maxRunMillisHighSpeed) else limits.maxRunMillis

    private fun end(reason: StopReason, detail: String?) {
        if (request == null) return
        request = null
        lastStop = reason
        lastStopDetail = detail
        onStopped(reason, detail)
    }
}
