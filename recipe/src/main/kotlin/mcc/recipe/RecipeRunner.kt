package mcc.recipe

import mcc.machine.MachineApi
import mcc.machine.RunRequest
import mcc.machine.StartResult
import mcc.machine.StopReason

/**
 * Walks through a recipe. Never starts the machine by itself: a machine step only runs after [start]
 * (the user pressing Start). Ramp/preset segments then follow each other automatically, and any stop
 * that is not a normal completion pauses the step so the user can resume the remaining time.
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
    )

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
        if (phase != Phase.RUNNING) return
        val snap = machine.snapshot(nowMillis)
        if (snap.running) {
            lastSegmentRemaining = snap.remainingMillis
            return
        }
        when (snap.lastStop) {
            StopReason.COMPLETED -> {
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
            else -> {
                phase = Phase.PAUSED
                resumeMillis = lastSegmentRemaining.takeIf { it > 0 }
                message = "stopped: ${describe(snap.lastStop, snap.lastStopDetail)}"
                event = Event.Stopped(snap.lastStop, snap.lastStopDetail)
            }
        }
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
