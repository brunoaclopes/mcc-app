package mcc.recipe

import mcc.machine.RunMode
import kotlin.test.Test
import kotlin.test.assertEquals

class MachinePlanTest {
    @Test fun roastRepeatsStirAndHeat() {
        val plan = MachinePlan.roast(7 * 60_000L, 130)
        assertEquals(7 * 60_000L, plan.sumOf { it.durationMillis })
        assertEquals(24, plan.size) // 12 cycles of 35 s = 420 s
        plan.forEachIndexed { i, r ->
            assertEquals(RunMode.ROAST, r.mode); assertEquals(130, r.temperatureC)
            if (i % 2 == 0) { assertEquals(1, r.speedLevel); assertEquals(5_000L, r.durationMillis) }
            else { assertEquals(0, r.speedLevel); assertEquals(30_000L, r.durationMillis) }
        }
        // a time that isn't a whole number of cycles ends on a shorter part
        assertEquals(listOf(5_000L, 30_000L, 5_000L, 10_000L), MachinePlan.roast(50_000L, 130).map { it.durationMillis })
    }
}
