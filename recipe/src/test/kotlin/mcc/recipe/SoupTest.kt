package mcc.recipe

import mcc.machine.SafetyLimits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SoupTest {
    private val combos = Soup.Kind.values().flatMap { k ->
        (listOf<Int?>(null) + (Soup.MIN_VEG..Soup.MAX_VEG step 100)).flatMap { g -> (0..Soup.BLEND_MAX).flatMap { b -> listOf(true, false).map { Soup.Options(g, k, b, it) } } }
    }

    @Test fun everyCombinationFitsTheBowlAndTheLimits() {
        val limits = SafetyLimits()
        for (o in combos) for (lang in listOf("en", "pt")) {
            val p = Soup.plan(o)
            val r = Soup.recipe(o, lang)
            assertTrue(p.vegGrams + p.waterGrams + (if (o.onionBase) 140 else 0) <= Soup.MAX_TOTAL, "$o overfills the bowl")
            assertTrue(p.waterGrams >= 300, "$o: too little water to cook")
            assertTrue(p.cookSeconds in 600..2100, "$o: ${p.cookSeconds} s")
            // the same check the repository build does: speeds, temperatures and times the machine accepts
            assertEquals(emptyList(), r.validate(), "$o")
            assertEquals(emptyList(), RecipeFit.problems(r, RecipeFit.SPECIAL), "$o")
            assertEquals(r, Recipe.parse(Recipe.json.encodeToString(Recipe.serializer(), r)), "$o round-trips")
            for (s in r.steps.filterIsInstance<Step.Machine>()) for (req in MachinePlan.expand(s)) {
                if (req.temperatureC > 0) assertTrue(req.speedLevel <= limits.maxSpeedWithHeat, "$o: speed with heat")
                assertTrue(req.durationMillis <= (if (req.speedLevel > limits.highSpeedAbove) limits.maxRunMillisHighSpeed else limits.maxRunMillis))
                if (req.reverse) assertTrue(req.speedLevel <= limits.maxSpeedReverse)
            }
            // enough is weighed in before anything heats
            var weighed = 0
            for (s in r.steps) {
                if (s is Step.Weigh) weighed += s.grams
                if (s is Step.Machine && s.segments.any { it.tempC > 0 }) assertTrue(weighed >= if (s.mode == Mode.ROAST) 30 else 50, "$o: heats with $weighed g weighed")
            }
        }
    }

    @Test fun moreAndFirmerTakesLonger() {
        val small = Soup.plan(Soup.Options(400, Soup.Kind.TENDER, 2))
        val big = Soup.plan(Soup.Options(1000, Soup.Kind.TENDER, 2))
        val firm = Soup.plan(Soup.Options(400, Soup.Kind.FIRM, 2))
        assertTrue(big.cookSeconds > small.cookSeconds)
        assertTrue(firm.cookSeconds > small.cookSeconds)
        assertTrue(big.portions > small.portions)
    }

    @Test fun chunkySoupIsNotBlendedAndStirsInReverse() {
        val r = Soup.recipe(Soup.Options(600, Soup.Kind.MIXED, blend = 0), "pt")
        val machine = r.steps.filterIsInstance<Step.Machine>()
        assertTrue(machine.none { s -> s.segments.any { it.speed > 5 } }, "no blending")
        assertTrue(machine.last().segments.single().reverse)
        val smooth = Soup.recipe(Soup.Options(600, Soup.Kind.MIXED, blend = 3), "en")
        assertEquals(10, smooth.steps.filterIsInstance<Step.Machine>().last().segments.last().speed)
    }

    @Test fun theVegetableStepMeasuresAndTheRestFollowsTheWeight() {
        val draft = Soup.recipe(Soup.Options(kind = Soup.Kind.FIRM, blend = 1, onionBase = false), "pt")
        val measure = draft.steps.filterIsInstance<Step.Weigh>().single { it.measure }
        assertEquals(Soup.MAX_VEG, measure.grams)
        assertEquals(null, draft.servings, "no portions before the vegetables are weighed")
        assertTrue(java.io.File("../common/src/main/assets/" + draft.image!!.removePrefix("asset:")).exists(), "photo is bundled")
        val r = Soup.measured(draft, 900)!!
        assertEquals(draft.steps.size, r.steps.size)
        val p = Soup.plan(Soup.Options(900, Soup.Kind.FIRM, 1, false))
        assertTrue(r.steps.any { it is Step.Weigh && !it.measure && it.grams == p.waterGrams })
        assertTrue(r.ingredients.single().items.any { "900 g" in it.text })
        assertEquals(null, Soup.measured(draft, 2000), "too much for the bowl")
        assertEquals(null, Soup.measured(draft, 100))
        assertEquals(Soup.measured(draft, 900), Soup.measured(r, 900), "the options survive a re-measure")
    }
}
