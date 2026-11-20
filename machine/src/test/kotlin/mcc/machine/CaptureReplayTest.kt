package mcc.machine

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Replays strace captures of the stock app talking to the real board (2026-11-20). */
class CaptureReplayTest {
    private data class Capture(val tx: List<ByteArray>, val rx: List<RxFrame>)

    private val line = Regex("""(read|write)\(\d+, "((?:\\x[0-9a-f]{2})+)"""")

    private fun load(name: String): Capture {
        val text = javaClass.getResource("/captures/$name")!!.readText()
        val tx = ArrayList<ByteArray>()
        val reader = RxFrameReader()
        val rx = ArrayList<RxFrame>()
        for (m in line.findAll(text)) {
            val bytes = m.groupValues[2].split("\\x").filter { it.isNotEmpty() }.map { it.toInt(16).toByte() }.toByteArray()
            if (m.groupValues[1] == "write") tx.add(bytes) else rx.addAll(reader.feed(bytes))
        }
        return Capture(tx, rx)
    }

    private fun Capture.commonChecks(minTx: Int, minRx: Int) {
        assertTrue(tx.size >= minTx, "tx frames ${tx.size}")
        assertTrue(rx.size >= minRx, "rx frames ${rx.size}")
        for (f in tx) {
            val cmd = assertNotNull(TxCommand.decode(f), "undecodable tx ${f.joinToString(" ") { "%02x".format(it) }}")
            assertContentEquals(f, cmd.encode(), "re-encoding must be byte-identical")
        }
        assertTrue(rx.all { it.firmwareVersion == 4921 })
        assertTrue(rx.all { it.actualRpm == 0 }, "fw 4921 never reports measured RPM")
        assertTrue(rx.none { it.errorFlag || it.errorCode != 0 })
    }

    @Test fun idleFrameMatchesStockApp() {
        val expected = byteArrayOf(0x55, 0x0f, 0xa1.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x05, 0xaa.toByte())
        assertContentEquals(expected, TxCommand.STOP.encode())
    }

    @Test fun session1LidScaleMotorReverse() {
        val c = load("session1-lid-scale-motor.txt")
        c.commonChecks(minTx = 2000, minRx = 300)
        assertTrue(c.rx.any { it.lidOpen } && c.rx.any { !it.lidOpen }, "lid toggled")
        assertTrue(c.tx.mapNotNull { TxCommand.decode(it) }.any { it.tare }, "stock app sent tare")
        assertTrue(c.rx.any { it.weightGrams in 470..495 }, "485 g reference cup")
        val runs = c.tx.mapNotNull { TxCommand.decode(it) }.filter { it.command == WorkCommand.RUN }
        assertTrue(runs.any { it.speedLevel == 1 && !it.reverse } && runs.any { it.speedLevel == 1 && it.reverse })
        assertTrue(c.rx.any { it.running && it.reverseActive }, "reverse mirrored in RX bit3")
        assertTrue(c.rx.any { it.running && it.targetRpm == 559 })
    }

    @Test fun session2And3Speed3() {
        for (name in listOf("session2-speed3-5s.txt", "session3-speed3-long.txt")) {
            val c = load(name)
            c.commonChecks(minTx = 500, minRx = 100)
            assertTrue(c.tx.mapNotNull { TxCommand.decode(it) }.any { it == TxCommand(WorkCommand.RUN, 3, 0) }, name)
            assertTrue(c.rx.any { it.running && it.targetRpm == 1440 }, name)
            assertTrue(c.rx.filter { it.running }.all { it.weightGrams == 0 }, "board reports 0 g while running")
        }
    }

    @Test fun session4Heat50() {
        val c = load("session4-heat50.txt")
        c.commonChecks(minTx = 2000, minRx = 300)
        assertTrue(c.tx.mapNotNull { TxCommand.decode(it) }.any { it == TxCommand(WorkCommand.RUN, 1, 3) }, "50 °C = level 3")
        val running = c.rx.filter { it.running }
        assertEquals(setOf(0, 37, 45, 50), running.map { it.temperatureStepC }.toSet(), "quantised temperature steps")
        assertTrue(running.any { it.aboveTarget })
        assertTrue(c.rx.any { !it.running && it.weightGrams == 520 }, "520 g water before start")
    }
}
