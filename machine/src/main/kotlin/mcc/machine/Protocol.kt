package mcc.machine

/**
 * Serial protocol of the Monsieur Cuisine Connect power board (SKMC 1200, power-board firmware 4921).
 * Port /dev/ttyMT0, 4800 baud 8N1. Layout from de.silpion.mc2 v292 PowerBoard.java, confirmed by live captures.
 */
object Protocol {
    const val START: Int = 0x55
    const val END: Int = 0xAA
    const val TX_LEN: Int = 15
    const val TX_CODE: Int = 0xA1
    const val RX_LEN: Int = 27
    const val RX_CODE: Int = 0xB1
    const val TARE_FLAG: Int = 0xA9
    const val BAUD: Int = 4800

    /** Sum of bytes [0, count) masked to one byte. */
    fun checksum(frame: ByteArray, count: Int): Int {
        var sum = 0
        for (i in 0 until count) sum += frame[i].toInt() and 0xFF
        return sum and 0xFF
    }
}

enum class WorkCommand(val code: Int) {
    OFF(0x00),
    /** Motor and heater at the given speed/temperature level (also used with speed 0 for heat only). */
    RUN(0x01),
    /** Stock app's TURBO mode. NOT a gentle "turn once". */
    TURBO(0xC6),
    STEAM(0x03),
    TOAST(0x04),
    KNEAD(0x05),
    SLEEP(0x0F);

    companion object {
        fun fromCode(code: Int): WorkCommand? = values().firstOrNull { it.code == code }
    }
}

enum class ScaleCalibration(val code: Int) { NONE(0x00), ENTER(0xE6), USER_ENTER(0xE7), START(0xE9) }

/** One 15-byte command frame sent to the power board every ~100 ms. */
data class TxCommand(
    val command: WorkCommand = WorkCommand.OFF,
    val speedLevel: Int = 0,
    val temperatureLevel: Int = 0,
    val reverse: Boolean = false,
    val tare: Boolean = false,
    val calibration: ScaleCalibration = ScaleCalibration.NONE,
) {
    init {
        require(speedLevel in 0..10) { "speedLevel $speedLevel out of 0..10" }
        require(temperatureLevel in 0..TemperatureTable.MAX_LEVEL) { "temperatureLevel $temperatureLevel out of range" }
    }

    fun encode(): ByteArray {
        val f = ByteArray(Protocol.TX_LEN)
        f[0] = Protocol.START.toByte()
        f[1] = Protocol.TX_LEN.toByte()
        f[2] = Protocol.TX_CODE.toByte()
        if (calibration == ScaleCalibration.NONE) {
            f[3] = command.code.toByte()
            f[4] = (if (tare) Protocol.TARE_FLAG else 0).toByte()
            f[5] = speedLevel.toByte()
            f[6] = temperatureLevel.toByte()
            f[11] = (if (reverse) 1 else 0).toByte()
        }
        // Like the stock app: during calibration bytes 3..11 are all zero.
        f[12] = calibration.code.toByte()
        f[13] = Protocol.checksum(f, 13).toByte()
        f[14] = Protocol.END.toByte()
        return f
    }

    companion object {
        val STOP = TxCommand()

        /** Parses a frame written by any client (used to verify against captures). Null if invalid. */
        fun decode(f: ByteArray): TxCommand? {
            if (f.size != Protocol.TX_LEN) return null
            val b = { i: Int -> f[i].toInt() and 0xFF }
            if (b(0) != Protocol.START || b(1) != Protocol.TX_LEN || b(2) != Protocol.TX_CODE || b(14) != Protocol.END) return null
            if (Protocol.checksum(f, 13) != b(13)) return null
            val cal = ScaleCalibration.values().firstOrNull { it.code == b(12) } ?: return null
            val cmd = WorkCommand.fromCode(b(3)) ?: return null
            if (b(5) > 10 || b(6) > TemperatureTable.MAX_LEVEL) return null
            return TxCommand(cmd, b(5), b(6), b(11) == 1, b(4) == Protocol.TARE_FLAG, cal)
        }
    }
}

/** One 27-byte status frame from the power board (~every 500 ms). Field meanings verified live unless noted. */
data class RxFrame(
    val raw: List<Int>,
) {
    private fun u(i: Int) = raw[i]
    private fun u16(i: Int) = (raw[i] shl 8) or raw[i + 1]

    /** 0 off, 1 motor(+heater), 2 heater only, 3 turbo. */
    val motorHeaterState: Int get() = u(3) and 0x03
    val running: Boolean get() = motorHeaterState != 0
    val scaleStable: Boolean get() = u(3) and 0x04 != 0
    /** Mirrors the reverse flag of the command (stock app calls it "clockwise"). Verified. */
    val reverseActive: Boolean get() = u(3) and 0x08 != 0
    val errorFlag: Boolean get() = u(3) and 0x10 != 0
    /** Set shortly before the target temperature is reached. Verified while heating to 50 °C. */
    val aboveTarget: Boolean get() = u(3) and 0x20 != 0
    /** Stock name "cup NTC detected"; stayed 0 while heating, meaning unknown. Do not rely on it. */
    val unknownBit6: Boolean get() = u(3) and 0x40 != 0
    val lidOpen: Boolean get() = u(3) and 0x80 != 0
    /** 3 steam, 4 toast, 5 knead. */
    val extendedState: Int get() = u(4)
    val errorCode: Int get() = u(5)
    val error: BoardError? get() = BoardError.fromCode(errorCode)
    val targetSpeedLevel: Int get() = u(6)
    val targetRpm: Int get() = u16(7)
    /** Always 0 on firmware 4921 (verified over 120 frames at speed 3). */
    val actualRpm: Int get() = u16(9)
    val temperatureLevel: Int get() = u(11)
    /** Stock name "target temperature"; drifts 56 -> 51 while heating to 50 °C. Probably a heater control value. */
    val heaterControlValue: Int get() = u16(12)
    /** Quantised to [TemperatureTable] steps: 0 means below 37 °C. */
    val temperatureStepC: Int get() = u16(14)
    /** Net weight in grams after the board's tare (signed). Verified with a 485 g reference. */
    val weightGrams: Int get() = (raw[16] shl 24) or (raw[17] shl 16) or (raw[18] shl 8) or raw[19]
    val firmwareVersion: Int get() = u16(20)
    val scaleStateBits: Int get() = u(22)
    val scaleOverload: Boolean get() = u(22) and 0x04 != 0
    val calibrationStep: Int get() = u(23)

    companion object {
        fun parse(f: ByteArray): RxFrame? {
            if (f.size != Protocol.RX_LEN) return null
            val r = f.map { it.toInt() and 0xFF }
            if (r[0] != Protocol.START || r[1] != Protocol.RX_LEN || r[2] != Protocol.RX_CODE || r[26] != Protocol.END) return null
            if (Protocol.checksum(f, 25) != r[25]) return null
            return RxFrame(r)
        }
    }
}

enum class BoardError(val code: Int) {
    SERIAL_COMMUNICATION(1),
    OVERVOLTAGE(3),
    UNDERVOLTAGE(4),
    MOTOR(5),
    NO_WATER(6),
    MOTOR_OVERHEAT(7),
    MAX_RUNTIME_EXCEEDED(8),
    UNKNOWN(-1);

    companion object {
        fun fromCode(code: Int): BoardError? = when (code) {
            0 -> null
            else -> values().firstOrNull { it.code == code } ?: UNKNOWN
        }
    }
}

/** Reassembles RX frames from an arbitrary byte stream (reads often contain 2 frames or partial ones). */
class RxFrameReader {
    private val buf = ArrayList<Byte>(256)

    fun feed(bytes: ByteArray, count: Int = bytes.size): List<RxFrame> {
        for (i in 0 until count) buf.add(bytes[i])
        val out = ArrayList<RxFrame>()
        while (true) {
            val start = findStart() ?: break
            if (start > 0) repeat(start) { buf.removeAt(0) }
            if (buf.size < Protocol.RX_LEN) break
            val candidate = ByteArray(Protocol.RX_LEN) { buf[it] }
            val frame = RxFrame.parse(candidate)
            if (frame != null) {
                out.add(frame)
                repeat(Protocol.RX_LEN) { buf.removeAt(0) }
            } else {
                buf.removeAt(0) // resync
            }
        }
        if (buf.size > 1024) buf.clear()
        return out
    }

    private fun findStart(): Int? {
        for (i in 0 until buf.size) {
            if ((buf[i].toInt() and 0xFF) != Protocol.START) continue
            if (i + 1 < buf.size && (buf[i + 1].toInt() and 0xFF) != Protocol.RX_LEN) continue
            return i
        }
        return null
    }
}
