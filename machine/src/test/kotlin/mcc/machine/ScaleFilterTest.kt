package mcc.machine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScaleFilterTest {
    private fun frame(stable: Boolean = true): RxFrame {
        val f = ByteArray(27)
        f[0] = 0x55; f[1] = 27; f[2] = 0xB1.toByte(); f[26] = 0xAA.toByte()
        f[3] = (if (stable) 4 else 0).toByte()
        f[25] = Protocol.checksum(f, 25).toByte()
        return RxFrameReader().feed(f, f.size).single()
    }

    @Test fun firstReadingShownAndNearZeroSnaps() {
        val s = ScaleFilter()
        assertEquals(0, s.update(2, frame()))
        assertEquals(0, s.update(-1, frame()))
    }

    @Test fun singleSpikeFromATapIsIgnored() {
        val s = ScaleFilter()
        s.update(500, frame())
        assertEquals(500, s.update(540, frame()))      // one frame only
        assertEquals(500, s.update(501, frame()))
        assertEquals(500, s.update(560, frame(stable = false)))
    }

    @Test fun realChangeShowsAfterTwoAgreeingFrames() {
        val s = ScaleFilter()
        s.update(500, frame())
        assertEquals(500, s.update(700, frame()))
        assertEquals(701, s.update(701, frame()))
    }

    @Test fun smallDriftStaysPut() {
        val s = ScaleFilter()
        s.update(500, frame())
        repeat(5) { assertEquals(500, s.update(502, frame())) }
    }

    @Test fun sameFrameCountsOnceAndUnknownClears() {
        val s = ScaleFilter()
        val f = frame()
        s.update(500, frame())
        s.update(700, f); s.update(700, f); s.update(700, f)
        assertEquals(500, s.shown)
        assertNull(s.update(null, null))
    }
}
