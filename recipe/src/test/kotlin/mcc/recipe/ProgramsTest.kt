package mcc.recipe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgramsTest {
    @Test fun everyProgramIsValidAndFitsTheMachine() {
        val all = Programs.all()
        assertTrue(all.size >= 18)
        assertEquals(all.size, all.map { it.id }.toSet().size, "ids must be unique")
        for (r in all) {
            assertEquals(emptyList(), r.validate(), r.id)
            assertEquals(emptyList(), RecipeFit.problems(r, RecipeFit.SPECIAL), r.id)
            val json = Recipe.json.encodeToString(Recipe.serializer(), r)
            assertEquals(r, Recipe.parse(json), r.id)
        }
    }

    @Test fun bothLanguagesHaveTheSamePrograms() {
        val ids = Programs.all().groupBy { it.language }.mapValues { (_, v) -> v.map { it.id.removeSuffix("-${it.language}") }.toSet() }
        assertEquals(ids["en"], ids["pt"])
    }

    @Test fun heatingProgramsAskForEnoughContent() {
        for (r in Programs.all()) {
            val heats = r.steps.any { it is Step.Machine && it.segments.any { s -> s.tempC > 0 } }
            if (!heats) continue
            val weigh = r.steps.filterIsInstance<Step.Weigh>().maxOfOrNull { it.grams }
            val text = r.steps.joinToString(" ") { it.text } + " " + r.notes.joinToString(" ") + " " +
                r.ingredients.flatMap { g -> g.items.map { it.text } }.joinToString(" ")
            val need = 50 // as the machine layer (SafetyLimits.minContentForHeatGrams / minContentForRoastGrams)
            assertTrue((weigh ?: 0) >= need || "$need g" in text || Regex("\\d kg").containsMatchIn(text), r.id)
        }
    }

    @Test fun everyProgramPhotoIsBundled() {
        val dir = java.io.File("../common/src/main/assets")
        for (r in Programs.all()) {
            val img = r.image ?: continue // drawn cover
            assertTrue(img.startsWith(Programs.ASSET), r.id)
            assertTrue(java.io.File(dir, img.removePrefix("asset:")).isFile, "missing ${img} for ${r.id}")
        }
    }

    @Test fun cleaningIsKeptOutOfTheCookingPrograms() {
        assertTrue(Programs.cooking().none { Programs.isCleaning(it) })
        assertEquals("pt", Programs.cleaning("pt").language)
        assertTrue(Programs.isCleaning(Programs.cleaning("en")))
    }
}
