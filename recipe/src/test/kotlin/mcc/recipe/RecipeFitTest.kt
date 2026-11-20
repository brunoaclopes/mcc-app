package mcc.recipe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecipeFitTest {
    private fun r(vararg s: Step) = Recipe(Recipe.FORMAT, "x", "X", steps = s.toList())

    @Test fun clampsSpeedWhileHeatingAndInReverse() {
        val out = RecipeFit.fit(r(Step.Machine("", Mode.RUN, listOf(Segment(60, 5, 100), Segment(30, 6, 0, reverse = true)))))
        val segs = (out.recipe.steps[0] as Step.Machine).segments
        assertEquals(3, segs[0].speed); assertEquals(3, segs[1].speed)
        assertEquals(2, out.changes.size)
    }

    @Test fun disabledModesBecomeRunSteps() {
        val out = RecipeFit.fit(r(Step.Machine("", Mode.STEAM, listOf(Segment(600)))))
        val m = out.recipe.steps[0] as Step.Machine
        assertEquals(Mode.RUN, m.mode); assertEquals(Segment(600, 1, 120), m.segments[0])
    }

    @Test fun capsLongFastRunsAndDropsEmptySegments() {
        val out = RecipeFit.fit(r(Step.Machine("x", Mode.RUN, listOf(Segment(1800, 8))), Step.Machine("y", Mode.RUN, listOf(Segment(10, 0, 0)))))
        assertEquals(900, (out.recipe.steps[0] as Step.Machine).segments[0].seconds)
        assertTrue(out.recipe.steps[1] is Step.Instruction)
    }

    @Test fun fittingRecipeIsUnchanged() {
        val x = r(Step.Machine("", Mode.RUN, listOf(Segment(60, 2, 100))))
        assertEquals(x, RecipeFit.fit(x).recipe)
        assertEquals(emptyList(), RecipeFit.problems(x))
    }
}
