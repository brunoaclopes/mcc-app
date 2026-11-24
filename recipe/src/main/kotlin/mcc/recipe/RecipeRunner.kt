package mcc.recipe

import mcc.machine.MachineApi
import mcc.machine.RunMode
import mcc.machine.RunRequest
import mcc.machine.RxFrame
import mcc.machine.StartResult
import mcc.machine.StopReason

/**
 * Walks through a recipe. Never starts the machine by itself: a machine step only runs after [start]
 * (the user pressing Start). Ramp/preset segments then follow each other automatically, and any stop
 * that is not a normal completion pauses the step so the user can resume the remaining time.
 *
 * A heated step whose time runs out before the bowl reaches its temperature (a big or cold bowl) keeps heating, in
 * short extensions, until it gets there, at most [heatWaitCap] longer. Each extension is an ordinary start, so the
 * machine layer checks every limit again.
 *
 * Not thread-safe: call everything from the thread that also ticks the machine controller.
 */
class RecipeRunner(val recipe: Recipe, private val machine: MachineApi) {
    enum class Phase { READY, RUNNING, PAUSED, DONE }

    /** Structured version of [State.message] for translated UIs. */
    sealed class Event {
        object Finished : Event()
        object StepDone : Event()
        object Paused : Event()
        data class Stopped(val reason: StopReason?, val detail: String?) : Event()
        data class CannotStart(val rejected: StartResult.Rejected) : Event()
    }

    data class State(
        val stepIndex: Int,
        val step: Step?,
        val phase: Phase,
        /** Index in the expanded run list of the current machine step. */
        val segmentIndex: Int,
        val segmentCount: Int,
        /** Remaining time of the whole machine step, or null if not a machine step. */
        val remainingMillis: Long?,
        /** For weigh steps: grams on the scale since the step's tare (null while unknown). */
        val weighedGrams: Int?,
        val message: String?,
        val event: Event? = null,
        /** °C the step is still heating towards after its time ran out, or null. */
        val heatingTo: Int? = null,
    )

    companion object {
        /** Length of one heat extension. */
        const val HEAT_WAIT_CHUNK_MILLIS = 30_000L

        /** How much longer a heated segment may run to reach its temperature: half its time, 2 to 10 minutes. */
        fun heatWaitCap(segment: RunRequest): Long = (segment.durationMillis / 2).coerceIn(120_000L, 600_000L)

        /**
         * The bowl is at temperature: the board's "near target" flag, or its reading at the target. Food with water
         * boils at about 100 °C whatever the setting, so targets above 95 °C count as reached at 95 °C.
         */
        /** How long the weight must sit at the amount before a hands-free weigh step moves on. */
        const val AUTO_WEIGH_MILLIS = 2_500L

        fun reached(targetC: Int, f: RxFrame?): Boolean = f != null && (f.aboveTarget || f.temperatureStepC >= minOf(targetC, 95))
    }

    var stepIndex = 0
        private set
    var phase = Phase.READY
        private set
    private var plan: List<RunRequest> = emptyList()
    private var segmentIndex = 0
    /** Remaining time of the current segment when it was paused (null = start it fresh). */
    private var resumeMillis: Long? = null
    private var lastSegmentRemaining: Long = 0
    private var tareWanted = false
    /**
     * Hands-free weighing: a weigh step moves on by itself once the scale has been steady at (or just under) its
     * amount for [AUTO_WEIGH_MILLIS]. It never starts the machine: a machine step still waits for Start.
     */
    var autoAdvanceWeigh = false
    private var weighHeldSince: Long? = null
    /** Extending the current segment because the bowl is not at temperature yet. */
    private var heatWaiting = false
    private var heatWaited = 0L
    private var message: String? = null
    private var event: Event? = null

    init { enterStep(0, nowMillis = 0) }

    val currentStep: Step? get() = recipe.steps.getOrNull(stepIndex)

    fun next(nowMillis: Long): Boolean {
        if (phase == Phase.RUNNING || phase == Phase.DONE) return false
        if (stepIndex + 1 >= recipe.steps.size) {
            phase = Phase.DONE
            message = "recipe finished"; event = Event.Finished
            return true
        }
        enterStep(stepIndex + 1, nowMillis)
        return true
    }

    fun back(nowMillis: Long): Boolean {
        if (phase == Phase.RUNNING || stepIndex == 0) return false
        enterStep(stepIndex - 1, nowMillis)
        return true
    }

    /** User pressed Start (or Resume) on a machine step. */
    fun start(nowMillis: Long): Boolean {
        val step = currentStep as? Step.Machine ?: return false
        if (phase != Phase.READY && phase != Phase.PAUSED) return false
        if (plan.isEmpty()) plan = MachinePlan.expand(step)
        return startSegment(nowMillis)
    }

    /** User pressed Pause/Stop. The remaining time of the step is kept. */
    fun pause(nowMillis: Long) {
        if (phase != Phase.RUNNING) return
        val snap = machine.snapshot(nowMillis)
        if (snap.running) resumeMillis = snap.remainingMillis
        machine.stop(nowMillis)
        phase = Phase.PAUSED
        message = "paused"; event = Event.Paused
    }

    /** Call after every machine controller tick. */
    fun tick(nowMillis: Long) {
        if (tareWanted && machine.requestTare(nowMillis)) tareWanted = false
        if (phase == Phase.READY) autoWeigh(nowMillis)
        if (phase != Phase.RUNNING) return
        val snap = machine.snapshot(nowMillis)
        if (snap.running) {
            lastSegmentRemaining = snap.remainingMillis
            // an extension ends as soon as the bowl is hot enough
            if (heatWaiting && reached(plan[segmentIndex].temperatureC, snap.lastFrame)) {
                machine.stop(nowMillis)
                segmentCompleted(nowMillis, snap.lastFrame)
            }
            return
        }
        when (snap.lastStop) {
            StopReason.COMPLETED -> segmentCompleted(nowMillis, snap.lastFrame)
            else -> {
                phase = Phase.PAUSED
                resumeMillis = lastSegmentRemaining.takeIf { it > 0 }
                message = "stopped: ${describe(snap.lastStop, snap.lastStopDetail)}"
                event = Event.Stopped(snap.lastStop, snap.lastStopDetail)
            }
        }
    }

    private fun autoWeigh(nowMillis: Long) {
        val step = currentStep as? Step.Weigh
        if (!autoAdvanceWeigh || step == null || tareWanted || step.grams <= 0) { weighHeldSince = null; return }
        val snap = machine.snapshot(nowMillis)
        val g = snap.scaleGrams
        // 97 %: the scale moves in 5 g steps, and a spoonful short is close enough to move on
        if (g == null || snap.lastFrame?.scaleStable != true || g < step.grams * 0.97) { weighHeldSince = null; return }
        val since = weighHeldSince ?: nowMillis.also { weighHeldSince = it }
        if (nowMillis - since >= AUTO_WEIGH_MILLIS) { weighHeldSince = null; next(nowMillis) }
    }

    private fun segmentCompleted(nowMillis: Long, f: RxFrame?) {
        val seg = plan[segmentIndex]
        if (needsHeat(seg, f)) {
            // not at temperature yet: heat on for another short while
            heatWaiting = true
            val chunk = minOf(HEAT_WAIT_CHUNK_MILLIS, heatWaitCap(seg) - heatWaited)
            heatWaited += chunk
            resumeMillis = chunk
            startSegment(nowMillis)
            return
        }
        heatWaiting = false; heatWaited = 0
        segmentIndex++
        resumeMillis = null
        if (segmentIndex < plan.size) {
            startSegment(nowMillis)
        } else {
            message = "step done"; event = Event.StepDone
            if (stepIndex + 1 < recipe.steps.size) enterStep(stepIndex + 1, nowMillis, keepMessage = true)
            else { phase = Phase.DONE; message = "recipe finished"; event = Event.Finished }
        }
    }

    /**
     * Whether [seg] should be extended: a heated normal run that ended below its temperature, with extension time left,
     * and not followed by a segment that keeps heating at least as hot (that one carries on heating anyway).
     */
    private fun needsHeat(seg: RunRequest, f: RxFrame?): Boolean {
        if (seg.mode != RunMode.NORMAL || seg.temperatureC <= 0 || f == null) return false
        val next = plan.getOrNull(segmentIndex + 1)
        if (next != null && next.mode == RunMode.NORMAL && next.temperatureC >= seg.temperatureC) return false
        return !reached(seg.temperatureC, f) && heatWaited < heatWaitCap(seg)
    }

    fun state(nowMillis: Long): State {
        val step = currentStep
        val snap = machine.snapshot(nowMillis)
        val remaining = if (step is Step.Machine) {
            val p = if (plan.isEmpty()) MachinePlan.expand(step) else plan
            val current = when {
                phase == Phase.RUNNING && snap.running -> snap.remainingMillis
                resumeMillis != null -> resumeMillis!!
                else -> p.getOrNull(segmentIndex)?.durationMillis ?: 0
            }
            current + p.drop(segmentIndex + 1).sumOf { it.durationMillis }
        } else null
        return State(
            stepIndex = stepIndex,
            step = step,
            phase = phase,
            segmentIndex = segmentIndex,
            segmentCount = if (step is Step.Machine) (if (plan.isEmpty()) MachinePlan.expand(step) else plan).size else 0,
            remainingMillis = remaining,
            weighedGrams = if (step is Step.Weigh && !tareWanted) snap.scaleGrams else null,
            message = message,
            event = event,
            heatingTo = if (heatWaiting) plan.getOrNull(segmentIndex)?.temperatureC else null,
        )
    }

    private fun startSegment(nowMillis: Long): Boolean {
        val base = plan[segmentIndex]
        val req = resumeMillis?.let { base.copy(durationMillis = it) } ?: base
        return when (val r = machine.start(req, nowMillis)) {
            is StartResult.Started -> {
                phase = Phase.RUNNING
                lastSegmentRemaining = req.durationMillis
                message = null; event = null
                true
            }
            is StartResult.Rejected -> {
                phase = if (segmentIndex == 0 && resumeMillis == null) Phase.READY else Phase.PAUSED
                if (phase == Phase.PAUSED && resumeMillis == null) resumeMillis = base.durationMillis
                message = "cannot start: ${r.reason}"; event = Event.CannotStart(r)
                false
            }
        }
    }

    private fun enterStep(index: Int, nowMillis: Long, keepMessage: Boolean = false) {
        stepIndex = index
        phase = Phase.READY
        plan = emptyList()
        segmentIndex = 0
        resumeMillis = null
        heatWaiting = false; heatWaited = 0; weighHeldSince = null
        if (!keepMessage) { message = null; event = null }
        tareWanted = currentStep is Step.Weigh
        if (tareWanted && machine.requestTare(nowMillis)) tareWanted = false
    }

    private fun describe(r: StopReason?, detail: String?): String = when (r) {
        StopReason.USER -> "stopped by user"
        StopReason.COMM_LOST -> "lost contact with the power board"
        StopReason.LID_OPENED -> "lid opened"
        StopReason.BOARD_ERROR -> "board error ${detail ?: ""}".trim()
        StopReason.MAX_RUNTIME -> "maximum run time reached"
        StopReason.COMPLETED, null -> "unknown"
    }
}
