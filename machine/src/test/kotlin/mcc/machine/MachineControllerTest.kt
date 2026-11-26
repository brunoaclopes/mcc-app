package mcc.machine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MachineControllerTest {
    /** Builds a valid RX frame like the real board (fw 4921). */
    private fun rx(lidOpen: Boolean = false, running: Boolean = false, weight: Int = 0, errorCode: Int = 0, stable: Boolean = true, tempC: Int = 0): ByteArray {
        val f = ByteArray(27)
        f[0] = 0x55; f[1] = 27; f[2] = 0xB1.toByte(); f[26] = 0xAA.toByte()
        var s = 0
        if (running) s = s or 1
        if (stable) s = s or 4
        if (errorCode != 0) s = s or 0x10
        if (lidOpen) s = s or 0x80
        f[3] = s.toByte(); f[5] = errorCode.toByte()
        val w = if (running) 0 else weight
        f[16] = (w ushr 24).toByte(); f[17] = (w ushr 16).toByte(); f[18] = (w ushr 8).toByte(); f[19] = w.toByte()
        f[14] = (tempC ushr 8).toByte(); f[15] = tempC.toByte()
        f[20] = 0x13; f[21] = 0x39
        f[25] = Protocol.checksum(f, 25).toByte()
        return f
    }

    private fun MachineController.feed(frame: ByteArray, now: Long) = onBytes(frame, frame.size, now)
    private fun decode(frame: ByteArray) = TxCommand.decode(frame)!!

    private val stops = ArrayList<StopReason>()
    private fun controller() = MachineController(SafetyLimits()) { r, _ -> stops.add(r) }

    @Test fun temperatureTableNeverRoundsUp() {
        assertEquals(0, TemperatureTable.levelAtMost(0))
        assertEquals(0, TemperatureTable.levelAtMost(36))
        assertEquals(1, TemperatureTable.levelAtMost(37))
        assertEquals(3, TemperatureTable.levelAtMost(52))
        assertEquals(19, TemperatureTable.levelAtMost(130))
        assertEquals(19, TemperatureTable.levelAtMost(500))
    }

    @Test fun idleSendsStop() {
        val c = controller()
        assertEquals(TxCommand.STOP, decode(c.tick(0)))
    }

    @Test fun rejectsWithoutCommunication() {
        val c = controller()
        assertIs<StartResult.Rejected>(c.start(RunRequest(1, 0, 5_000), 0))
        c.feed(rx(), 0)
        assertIs<StartResult.Rejected>(c.start(RunRequest(1, 0, 5_000), 2_000), "stale frame")
    }

    @Test fun rejectsLidOpenAndBoardError() {
        val c = controller()
        c.feed(rx(lidOpen = true), 0)
        assertIs<StartResult.Rejected>(c.start(RunRequest(1, 0, 5_000), 10))
        c.feed(rx(errorCode = 6), 20)
        assertIs<StartResult.Rejected>(c.start(RunRequest(1, 0, 5_000), 30))
    }

    @Test fun heatingNeedsReferenceAndContent() {
        val c = controller()
        c.feed(rx(weight = 2130), 0)
        assertIs<StartResult.Rejected>(c.start(RunRequest(1, 50, 60_000), 10), "no reference yet")
        assertTrue(c.setEmptyBowlReference(10))
        c.feed(rx(weight = 2160), 20) // only 30 g
        assertIs<StartResult.Rejected>(c.start(RunRequest(1, 50, 60_000), 30))
        c.feed(rx(weight = 2650), 40) // 520 g of water
        assertIs<StartResult.Started>(c.start(RunRequest(1, 50, 60_000), 50))
        assertEquals(TxCommand(WorkCommand.RUN, 1, 3), decode(c.tick(60)))
    }

    @Test fun heatedRestartWhileBoardStillReportsRunning() {
        val c = controller()
        c.feed(rx(weight = 2130), 0)
        assertTrue(c.setEmptyBowlReference(10))
        c.feed(rx(weight = 2650), 20) // 520 g of water
        assertIs<StartResult.Started>(c.start(RunRequest(1, 100, 60_000), 30))
        c.stop(40) // pause
        c.feed(rx(running = true), 50) // the board still says running for a moment: no weight
        assertIs<StartResult.Started>(c.start(RunRequest(0, 100, 60_000), 60), "resume uses the reading from just before")
        c.stop(70)
        c.feed(rx(weight = 2130), 80) // bowl emptied: idle frames show it
        assertIs<StartResult.Rejected>(c.start(RunRequest(1, 100, 60_000), 90))
    }

    @Test fun speedLimitsWithHeatAndReverse() {
        val c = controller()
        c.feed(rx(weight = 0), 0); c.setEmptyBowlReference(0); c.feed(rx(weight = 600), 10)
        assertIs<StartResult.Rejected>(c.start(RunRequest(4, 50, 60_000), 20))
        assertIs<StartResult.Rejected>(c.start(RunRequest(5, 0, 60_000, reverse = true), 20))
        assertIs<StartResult.Rejected>(c.start(RunRequest(7, 0, 16 * 60_000L), 20), "15 min max above speed 6")
        assertIs<StartResult.Rejected>(c.start(RunRequest(1, 131, 60_000), 20))
    }

    @Test fun longLowTemperatureRunsOnlyWhenSwitchedOnAndWithinTheRule() {
        val c = controller()
        c.feed(rx(weight = 0), 0); c.setEmptyBowlReference(0); c.feed(rx(weight = 1500), 10)
        val sixHours = 6 * 3600_000L
        val off = c.start(RunRequest(1, 90, sixHours, reverse = true), 20)
        assertIs<StartResult.Rejected>(off)
        assertEquals(RejectCode.LONG_RUNS_OFF, off.code)
        c.longRunsEnabled = true
        assertIs<StartResult.Rejected>(c.start(RunRequest(1, 100, sixHours), 20), "above 95 °C: 99 min still")
        assertIs<StartResult.Rejected>(c.start(RunRequest(2, 90, sixHours), 20), "above speed 1: 99 min still")
        assertIs<StartResult.Rejected>(c.start(RunRequest(1, 0, sixHours), 20), "without heat: 99 min still")
        assertIs<StartResult.Rejected>(c.start(RunRequest(0, 60, 13 * 3600_000L), 20), "12 h at most")
        assertIs<StartResult.Started>(c.start(RunRequest(0, 60, sixHours), 20), "sous-vide: heater only")
        // still stops on the usual checks, here the lid, hours into the run
        c.feed(rx(running = true), 3 * 3600_000L)
        assertEquals(WorkCommand.RUN, decode(c.tick(3 * 3600_000L)).command)
        c.feed(rx(running = true, lidOpen = true), 3 * 3600_000L + 500)
        assertEquals(TxCommand.STOP, decode(c.tick(3 * 3600_000L + 600)))
        assertEquals(listOf(StopReason.LID_OPENED), stops)
    }

    @Test fun longRunEndsAtItsTime() {
        val c = controller()
        c.longRunsEnabled = true
        c.feed(rx(weight = 0), 0); c.setEmptyBowlReference(0); c.feed(rx(weight = 1500), 10)
        val twoHours = 2 * 3600_000L
        assertIs<StartResult.Started>(c.start(RunRequest(1, 90, twoHours), 20))
        var t = 20L
        while (t < twoHours) { c.feed(rx(running = true), t); c.tick(t); t += 60_000 }
        assertTrue(stops.isEmpty(), "not stopped at 99 min")
        c.feed(rx(running = true), twoHours + 30); c.tick(twoHours + 30)
        assertEquals(listOf(StopReason.COMPLETED), stops)
    }

    @Test fun completesAfterDuration() {
        val c = controller()
        c.feed(rx(), 0)
        assertIs<StartResult.Started>(c.start(RunRequest(3, 0, 5_000), 0))
        var t = 0L
        while (t < 5_000) {
            if (t % 500 == 0L) c.feed(rx(running = true), t)
            assertEquals(TxCommand(WorkCommand.RUN, 3, 0), decode(c.tick(t)))
            t += 100
        }
        c.feed(rx(running = true), t)
        assertEquals(TxCommand.STOP, decode(c.tick(t)))
        assertEquals(listOf(StopReason.COMPLETED), stops)
    }

    @Test fun stopsWhenFramesStop() {
        val c = controller()
        c.feed(rx(), 0)
        c.start(RunRequest(1, 0, 60_000), 0)
        assertEquals(WorkCommand.RUN, decode(c.tick(1_000)).command)
        assertEquals(TxCommand.STOP, decode(c.tick(1_600)))
        assertEquals(listOf(StopReason.COMM_LOST), stops)
        assertEquals(TxCommand.STOP, decode(c.tick(1_700)), "stays stopped")
    }

    @Test fun stopsWhenLidOpensOrErrorAppears() {
        val c = controller()
        c.feed(rx(), 0); c.start(RunRequest(1, 0, 60_000), 0)
        c.feed(rx(running = true, lidOpen = true), 500)
        assertEquals(TxCommand.STOP, decode(c.tick(500)))
        c.feed(rx(), 600); c.start(RunRequest(1, 0, 60_000), 600)
        c.feed(rx(running = true, errorCode = 7), 1_000)
        assertEquals(TxCommand.STOP, decode(c.tick(1_000)))
        assertEquals(listOf(StopReason.LID_OPENED, StopReason.BOARD_ERROR), stops)
    }

    @Test fun readerResyncsOnGarbageAndSplitFrames() {
        val r = RxFrameReader()
        val f = rx(weight = 485)
        assertEquals(0, r.feed(byteArrayOf(0x00, 0x55, 0x12) + f.copyOfRange(0, 10)).size)
        val out = r.feed(f.copyOfRange(10, 27) + f)
        assertEquals(2, out.size)
        assertEquals(485, out[0].weightGrams)
    }

    @Test fun tareKeepsContentMeasurement() {
        val c = controller()
        c.feed(rx(weight = 2000), 0)
        assertTrue(c.setEmptyBowlReference(0))
        c.feed(rx(weight = 2300), 100)
        assertEquals(300, c.contentGrams(100))
        assertTrue(c.requestTare(100))
        assertEquals(null, c.contentGrams(150), "unknown while the tare is pending")
        val f = decode(c.tick(200))
        assertTrue(f.tare); assertEquals(WorkCommand.OFF, f.command)
        assertEquals(TxCommand.STOP, decode(c.tick(300)), "tare is a single frame")
        c.feed(rx(weight = 2300), 400) // board has not applied the new zero yet
        assertEquals(null, c.contentGrams(400), "unknown while settling")
        c.feed(rx(weight = 150), 2_000) // 150 g added after the tare
        assertEquals(450, c.contentGrams(2_000))
        assertEquals(150, c.snapshot(2_000).scaleGrams)
    }

    @Test fun tareRefusedWhileRunning() {
        val c = controller()
        c.feed(rx(), 0); c.start(RunRequest(2, 0, 5_000), 0)
        assertEquals(false, c.requestTare(10))
    }

    @Test fun nonNormalModesDisabledByDefault() {
        val c = controller()
        c.feed(rx(), 0)
        for (m in listOf(RunMode.TURBO, RunMode.KNEAD, RunMode.ROAST, RunMode.STEAM)) {
            val r = c.start(RunRequest(1, 0, 2_000, mode = m), 10)
            assertIs<StartResult.Rejected>(r, m.name)
        }
    }

    @Test fun turboRulesWhenEnabled() {
        val c = MachineController(SafetyLimits(enabledModes = RunMode.values().toSet())) { r, _ -> stops.add(r) }
        c.feed(rx(), 0)
        assertIs<StartResult.Rejected>(c.start(RunRequest(10, 0, 11_000, mode = RunMode.TURBO), 0))
        assertIs<StartResult.Rejected>(c.start(RunRequest(10, 0, 2_000, reverse = true, mode = RunMode.TURBO), 0))
        assertIs<StartResult.Started>(c.start(RunRequest(10, 0, 2_000, mode = RunMode.TURBO), 0))
        assertEquals(TxCommand(WorkCommand.TURBO, 10, 0), decode(c.tick(100)))
    }

    @Test fun specialModesNeedTheUserSwitchAndKeepTheirLimits() {
        val c = controller()
        c.feed(rx(), 0)
        assertIs<StartResult.Rejected>(c.start(RunRequest(4, 0, 2_000, mode = RunMode.KNEAD), 0)) // not switched on
        c.userEnabledModes = setOf(RunMode.TURBO, RunMode.KNEAD, RunMode.ROAST, RunMode.STEAM)
        assertIs<StartResult.Rejected>(c.start(RunRequest(4, 50, 2_000, mode = RunMode.KNEAD), 0)) // knead never heats
        assertIs<StartResult.Rejected>(c.start(RunRequest(5, 0, 2_000, mode = RunMode.KNEAD), 0)) // knead <= speed 4
        assertIs<StartResult.Rejected>(c.start(RunRequest(2, 120, 2_000, mode = RunMode.STEAM), 0)) // steam <= speed 1
        assertIs<StartResult.Rejected>(c.start(RunRequest(1, 120, 2_000, mode = RunMode.STEAM), 0)) // no empty-bowl reference
        assertIs<StartResult.Rejected>(c.start(RunRequest(10, 50, 2_000, mode = RunMode.TURBO), 0)) // turbo never heats
        assertIs<StartResult.Started>(c.start(RunRequest(4, 0, 2_000, mode = RunMode.KNEAD), 0))
        assertEquals(WorkCommand.KNEAD, decode(c.tick(100)).command)
    }

    @Test fun userTurboStillCappedAndBlockedWhenHot() {
        val c = controller()
        c.userEnabledModes = setOf(RunMode.TURBO)
        c.feed(rx(tempC = 60), 0)
        assertIs<StartResult.Rejected>(c.start(RunRequest(10, 0, 2_000, mode = RunMode.TURBO), 0))
    }

    @Test fun openRunCountsUpAndStopsAtTheCapForItsSpeed() {
        val c = controller()
        c.feed(rx(), 0)
        assertIs<StartResult.Started>(c.start(RunRequest(7, 0, 0, open = true), 0))
        assertEquals(15 * 60_000L, c.snapshot(0).request!!.durationMillis, "speed 7: 15 min cap")
        var t = 0L
        while (t < 15 * 60_000L) {
            if (t % 500 == 0L) c.feed(rx(running = true), t)
            assertEquals(WorkCommand.RUN, decode(c.tick(t)).command)
            t += 100
        }
        assertEquals(15 * 60_000L - 100, c.snapshot(t - 100).elapsedMillis)
        c.feed(rx(running = true), t)
        assertEquals(TxCommand.STOP, decode(c.tick(t)))
        assertEquals(listOf(StopReason.MAX_RUNTIME), stops, "an open run ends by its cap, never as 'completed'")
    }

    @Test fun openRunKeepsHeatLimitsAndOnlyNormalMode() {
        val c = controller()
        c.feed(rx(), 0)
        assertIs<StartResult.Rejected>(c.start(RunRequest(2, 90, 0, open = true), 0), "heat still needs the empty-bowl reference")
        assertIs<StartResult.Rejected>(c.start(RunRequest(5, 90, 0, open = true), 0))
        assertIs<StartResult.Rejected>(c.start(RunRequest(10, 0, 0, mode = RunMode.TURBO, open = true), 0))
        assertIs<StartResult.Started>(c.start(RunRequest(1, 0, 120_000, open = true), 0))
        assertEquals(120_000L, c.snapshot(0).request!!.durationMillis, "a resumed open run keeps its lower cap")
    }
}
