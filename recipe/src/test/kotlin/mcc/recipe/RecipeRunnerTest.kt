package mcc.recipe

import mcc.machine.MachineController
import mcc.machine.Protocol
import mcc.machine.RunMode
import mcc.machine.SafetyLimits
import mcc.machine.TxCommand
import mcc.machine.WorkCommand
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Simulated power board: answers every 500 ms like fw 4921, applies tare, reports 0 g while running. */
private class FakeBoard {
    var lidOpen = false
    var grossGrams = 2000
    private var tareOffset = 0
    var running = false
    /** Bowl temperature the board reports (quantised °C, 0 = below 37). */
    var tempC = 0
    val sentCommands = ArrayList<TxCommand>()

    fun receive(frame: ByteArray) {
        val c = TxCommand.decode(frame)!!
        sentCommands.add(c)
        if (c.tare) tareOffset = grossGrams
        running = c.command != WorkCommand.OFF
    }

    fun frame(): ByteArray {
        val f = ByteArray(27)
        f[0] = 0x55; f[1] = 27; f[2] = 0xB1.toByte(); f[26] = 0xAA.toByte()
        f[3] = ((if (running) 1 else 0) or 4 or (if (lidOpen) 0x80 else 0)).toByte()
        val w = if (running) 0 else grossGrams - tareOffset
        f[16] = (w ushr 24).toByte(); f[17] = (w ushr 16).toByte(); f[18] = (w ushr 8).toByte(); f[19] = w.toByte()
        f[14] = (tempC ushr 8).toByte(); f[15] = tempC.toByte()
        f[20] = 0x13; f[21] = 0x39
        f[25] = Protocol.checksum(f, 25).toByte()
        return f
    }
}

class RecipeRunnerTest {
    private val board = FakeBoard()
    private val controller = MachineController(SafetyLimits())
    private var now = 0L

    /** Advances time in 100 ms ticks, like MachineService. */
    private fun run(runner: RecipeRunner?, millis: Long) {
        val end = now + millis
        while (now < end) {
            if (now % 500 == 0L) board.frame().let { controller.onBytes(it, it.size, now) }
            board.receive(controller.tick(now))
            runner?.tick(now)
            now += 100
        }
    }

    private fun recipe(vararg steps: Step) = Recipe(format = Recipe.FORMAT, id = "t", title = "t", steps = steps.toList())

    @Test fun rampSegmentsRunBackToBackThenAdvance() {
        run(null, 1_000)
        val r = RecipeRunner(recipe(
            Step.Machine(segments = listOf(Segment(2, speed = 3), Segment(2, speed = 5), Segment(2, speed = 7))),
            Step.Instruction("serve"),
        ), controller)
        assertEquals(RecipeRunner.Phase.READY, r.phase, "never starts by itself")
        run(r, 1_000)
        assertTrue(board.sentCommands.all { it.command == WorkCommand.OFF })
        assertTrue(r.start(now))
        run(r, 7_000)
        val speeds = board.sentCommands.filter { it.command == WorkCommand.RUN }.map { it.speedLevel }.distinct()
        assertEquals(listOf(3, 5, 7), speeds)
        val runFrames = board.sentCommands.count { it.command == WorkCommand.RUN }
        assertTrue(runFrames in 58..62, "about 6 s of RUN frames, got $runFrames")
        assertEquals(1, r.stepIndex)
        assertEquals(RecipeRunner.Phase.READY, r.phase)
    }

    @Test fun lidOpenPausesAndResumeFinishesRemainingTime() {
        run(null, 1_000)
        val r = RecipeRunner(recipe(Step.Machine(segments = listOf(Segment(10, speed = 2)))), controller)
        r.start(now)
        run(r, 4_000)
        board.lidOpen = true
        run(r, 1_000)
        assertEquals(RecipeRunner.Phase.PAUSED, r.phase)
        assertTrue(r.state(now).message!!.contains("lid"))
        val left = r.state(now).remainingMillis!!
        assertTrue(left in 5_000..6_500, "remaining $left")
        assertTrue(!r.start(now), "refused while the lid is open")
        board.lidOpen = false
        run(r, 1_000)
        board.sentCommands.clear()
        assertTrue(r.start(now))
        run(r, 8_000)
        val resumed = board.sentCommands.count { it.command == WorkCommand.RUN }
        assertTrue(resumed in (left / 100 - 2)..(left / 100 + 2), "resumed for $resumed ticks, expected ~${left / 100}")
        assertEquals(RecipeRunner.Phase.DONE, r.phase)
    }

    @Test fun pauseKeepsRemainingTime() {
        run(null, 1_000)
        val r = RecipeRunner(recipe(Step.Machine(segments = listOf(Segment(10, speed = 2)))), controller)
        r.start(now); run(r, 3_000)
        r.pause(now); run(r, 500)
        assertEquals(RecipeRunner.Phase.PAUSED, r.phase)
        assertEquals(WorkCommand.OFF, board.sentCommands.last().command)
        assertTrue(r.state(now).remainingMillis!! in 6_800..7_200)
    }

    @Test fun weighStepTaresAndTracksGrams() {
        run(null, 1_000)
        val r = RecipeRunner(recipe(Step.Instruction("prep"), Step.Weigh("add flour", 300)), controller)
        r.next(now)
        run(r, 2_500)
        assertTrue(board.sentCommands.any { it.tare })
        assertEquals(0, r.state(now).weighedGrams)
        board.grossGrams += 280
        run(r, 1_000)
        assertEquals(280, r.state(now).weighedGrams)
    }

    @Test fun heatingWithoutReferenceIsRefusedNotStarted() {
        run(null, 1_000)
        val r = RecipeRunner(recipe(Step.Machine(segments = listOf(Segment(60, speed = 1, tempC = 100)))), controller)
        assertTrue(!r.start(now))
        assertEquals(RecipeRunner.Phase.READY, r.phase)
        assertTrue(r.state(now).message!!.contains("empty-bowl"))
        run(r, 1_000)
        assertTrue(board.sentCommands.none { it.command != WorkCommand.OFF })
    }

    @Test fun disabledModeIsRefused() {
        run(null, 1_000)
        val r = RecipeRunner(recipe(Step.Machine(mode = Mode.TURBO, segments = listOf(Segment(2)))), controller)
        assertTrue(!r.start(now))
        assertTrue(r.state(now).message!!.contains("not enabled"))
    }

    @Test fun presetExpansionMatchesStockApp() {
        val k = MachinePlan.knead(120_000)
        assertEquals(listOf(15_000L, 35_000L, 70_000L), k.map { it.durationMillis })
        assertEquals(listOf(true, false, false), k.map { it.reverse })
        assertTrue(k.all { it.mode == RunMode.KNEAD })
        assertEquals(45_000L, MachinePlan.knead(45_000).sumOf { it.durationMillis })
        assertEquals(listOf(5_000L), MachinePlan.knead(5_000).map { it.durationMillis })
        val roast = MachinePlan.roast(420_000, 130)
        assertEquals(List(12) { listOf(1, 0) }.flatten(), roast.map { it.speedLevel }) // stir 5 s, heat 30 s, repeated
        assertEquals(420_000L, roast.sumOf { it.durationMillis })
    }

    /** Every recipe in recipe-repo/recipes (or -Pmcc.recipes.dir) must parse and expand to requests the encoder accepts. */
    @Test fun allRepositoryRecipesParse() {
        val dir = File(System.getProperty("mcc.recipes.dir") ?: return)
        val files = dir.listFiles { f -> f.extension == "json" } ?: return
        var machineSteps = 0
        for (f in files) {
            val r = Recipe.parse(f.readText())
            assertEquals(f.nameWithoutExtension, r.id)
            r.steps.filterIsInstance<Step.Machine>().forEach { s ->
                machineSteps++
                val plan = MachinePlan.expand(s)
                assertTrue(plan.isNotEmpty())
                assertEquals(s.segments.sumOf { it.seconds } * 1000L, plan.sumOf { it.durationMillis }, "${r.id} ${s}")
                MachinePlan.describe(s)
            }
        }
        println("parsed ${files.size} recipes, $machineSteps machine steps")
    }

    /** Empty-bowl reference with 1000 g of content, so heated steps may start. */
    private fun readyToHeat() {
        board.grossGrams = 500; run(null, 1_000)
        assertTrue(controller.setEmptyBowlReference(now))
        board.grossGrams = 1500; run(null, 1_000)
    }

    @Test fun heatedStepRunsOnUntilTheBowlIsHot() {
        readyToHeat()
        val r = RecipeRunner(recipe(Step.Machine(segments = listOf(Segment(60, speed = 1, tempC = 100))), Step.Instruction("next")), controller)
        assertTrue(r.start(now))
        board.tempC = 80
        run(r, 61_000)
        assertEquals(0, r.stepIndex, "still heating: the bowl is at 80 °C")
        assertEquals(RecipeRunner.Phase.RUNNING, r.phase)
        assertEquals(100, r.state(now).heatingTo)
        board.tempC = 95
        run(r, 2_000)
        assertEquals(1, r.stepIndex, "moves on as soon as the bowl is at temperature, without waiting for the chunk")
        assertEquals(null, r.state(now).heatingTo)
    }

    @Test fun heatExtensionIsCapped() {
        readyToHeat()
        val r = RecipeRunner(recipe(Step.Machine(segments = listOf(Segment(60, speed = 1, tempC = 100))), Step.Instruction("next")), controller)
        assertTrue(r.start(now))
        board.tempC = 60
        run(r, 60_000 + 120_000 + 3_000)
        assertEquals(1, r.stepIndex, "gives up after the cap (2 min for a 1 min step)")
    }

    @Test fun noExtensionWhenHotOrUnheated() {
        readyToHeat()
        board.tempC = 100
        val r = RecipeRunner(recipe(Step.Machine(segments = listOf(Segment(30, speed = 1, tempC = 100))), Step.Instruction("next")), controller)
        assertTrue(r.start(now)); run(r, 31_000)
        assertEquals(1, r.stepIndex)
        board.tempC = 0
        val r2 = RecipeRunner(recipe(Step.Machine(segments = listOf(Segment(10, speed = 4))), Step.Instruction("next")), controller)
        assertTrue(r2.start(now)); run(r2, 11_000)
        assertEquals(1, r2.stepIndex)
    }

    @Test fun handsFreeWeighingMovesOnWhenSteadyButNeverStartsTheMachine() {
        run(null, 1_000)
        val r = RecipeRunner(recipe(Step.Weigh("rice", 250), Step.Machine(segments = listOf(Segment(10, speed = 2)))), controller)
        r.autoAdvanceWeigh = true
        run(r, 1_500) // the step's tare settles
        board.grossGrams += 200; run(r, 4_000)
        assertEquals(0, r.stepIndex, "200 g of 250 g: stays")
        board.grossGrams += 50; run(r, 1_500)
        assertEquals(0, r.stepIndex, "not steady long enough yet")
        run(r, 2_000)
        assertEquals(1, r.stepIndex, "moves on at the amount")
        run(r, 5_000)
        assertEquals(RecipeRunner.Phase.READY, r.phase, "the machine step waits for Start")
        assertTrue(board.sentCommands.none { it.command == WorkCommand.RUN })
    }

    @Test fun weighingWaitsForNextWhenHandsFreeIsOff() {
        run(null, 1_000)
        val r = RecipeRunner(recipe(Step.Weigh("rice", 250), Step.Instruction("next")), controller)
        run(r, 1_500); board.grossGrams += 300; run(r, 6_000)
        assertEquals(0, r.stepIndex)
    }
}
