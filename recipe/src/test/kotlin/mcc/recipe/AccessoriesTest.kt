package mcc.recipe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccessoriesTest {
    private fun say(t: String) = Step.Instruction(t)
    private fun run(speed: Int, sec: Int = 30, mode: Mode = Mode.RUN) = Step.Machine("", mode, listOf(Segment(sec, speed)))
    private fun recipe(vararg s: Step) = Recipe(Recipe.FORMAT, "t", "T", "pt", steps = s.toList())

    @Test fun putAndRemoveInBothLanguages() {
        assertEquals(listOf(Accessory.MIXER to true), Accessories.mentions("Coloque o acessório batedor no recipiente de mistura."))
        assertEquals(listOf(Accessory.MIXER to false), Accessories.mentions("Retire o acessório batedor."))
        assertEquals(listOf(Accessory.STEAMER to false, Accessory.BASKET to true),
            Accessories.mentions("Remove the deep steamer basket and insert the cooking pot into the blender jug."))
        assertEquals(listOf(Accessory.CUP to false), Accessories.mentions("Remove the measuring beaker."))
        assertEquals(listOf(Accessory.STEAMER, Accessory.STEAMER_FLAT), Accessories.mentions("Retire o acessório para vapor completo.").map { it.first })
    }

    @Test fun basketOnTheLidIsASplashGuard() {
        assertTrue(Accessories.mentions("During the next step, put the cooking pot on the lid as a splash guard.").isEmpty())
        assertTrue(Accessories.mentions("Coloque o acessório de cozer na tampa como proteção.").isEmpty())
    }

    @Test fun mixerComesOutBeforeAFastStep() {
        val p = AccessoryPlan.of(recipe(say("Coloque o acessório batedor."), run(3), say("Junte as amêndoas."), run(8)))
        assertEquals(setOf(Accessory.MIXER), p.at(1).fitted)
        assertFalse(p.at(1).mustRemoveMixer)
        assertTrue(p.at(3).mustRemoveMixer)
        assertTrue(Accessory.MIXER !in p.at(3).fitted)
        assertEquals(listOf(Accessory.MIXER), p.needs)
    }

    @Test fun turboAndKneadingAlsoNeedTheMixerOut() {
        assertTrue(AccessoryPlan.of(recipe(say("Put in the mixer attachment."), run(0, 2, Mode.TURBO))).at(1).mustRemoveMixer)
        assertTrue(AccessoryPlan.of(recipe(say("Put in the mixer attachment."), run(0, 60, Mode.KNEAD))).at(1).mustRemoveMixer)
    }

    @Test fun saidRemovalIsNotImplied() {
        val p = AccessoryPlan.of(recipe(say("Coloque o acessório batedor."), run(4), say("Retire o acessório batedor."), run(10)))
        assertFalse(p.at(3).mustRemoveMixer)
        assertEquals(Accessory.MIXER to false, p.at(2).changes.single().let { it.accessory to it.put })
    }

    @Test fun shallowSteamerSitsOnTheDeepOne() {
        val p = AccessoryPlan.of(recipe(say("Put the fish in the shallow steamer basket.")))
        assertEquals(setOf(Accessory.STEAMER_FLAT, Accessory.STEAMER), p.at(0).fitted)
    }

    @Test fun steamModeWithoutTextNeedsTheSteamer() {
        val p = AccessoryPlan.of(recipe(Step.Machine("", Mode.STEAM, listOf(Segment(1200, 0, 120)))))
        assertEquals(listOf(Accessory.STEAMER), p.needs)
    }

    @Test fun cupIsShownButNotNeeded() {
        val p = AccessoryPlan.of(recipe(say("Retire o copo de medição da tampa.")))
        assertTrue(p.needs.isEmpty())
        assertEquals(Accessory.CUP, p.at(0).changes.single().accessory)
    }
}
