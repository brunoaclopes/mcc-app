package mcc.recipe

import mcc.recipe.online.Moulinex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Shapes of the Moulinex recipe API (format=mobile), written by hand: no Moulinex content in this repository. */
class MoulinexTest {
    private fun step(desc: String, ops: String, kitchenware: String? = null, ingredients: String = "") = """
        {"applicationDescription": ${"\"" + desc + "\""},
         ${kitchenware?.let { "\"kitchenwares\":[{\"key\":\"$it\"}]," } ?: ""}
         "ingredients":[$ingredients],
         "sequences":[{"applianceGroup":{"key":"APPLIANCE_GROUP_16","name":"Companion"},"operations":[$ops]}]}"""
    private fun op(program: String, vararg p: Pair<String, String?>) =
        """{"program":{"name":"$program"},"parameters":[${p.joinToString(",") { (k, v) -> "{\"commonKey\":\"$k\",\"value\":${v?.let { "\"$it\"" } ?: "null"}}" }}]}"""
    private fun recipe(vararg steps: String) = """
        {"fid":{"functionalId":"111"},"title":"Sopa &amp; teste","yield":{"quantityDisplay":"4","unit":{"pluralName":"pessoas"}},
         "durations":{"prepTime":10,"totalTime":40},"difficulty":25,"seoUrl":"/PRO/sopa-teste/999",
         "resourceMedias":[{"isCover":true,"media":{"original":"https://x.org/a.jpg"}}],
         "ingredients":[{"applicationDescription":"300 g de cenoura"}],
         "steps":[${steps.joinToString(",")}]}"""
    private val grams = """{"applicationDescription":"300 g de cenoura","unit":{"key":"UNIT_27"},"weight":{"quantity":300}}"""

    @Test fun manualOnTheBladeScalesSpeedAndWeighs() {
        val r = Moulinex.convert(recipe(step("Coloque a cenoura na taça do robot equipado com a lâmina picadora Ultrablade e prima 'Start'.",
            op("Manual P", "SPEED" to "V11", "DURATION" to "10", "TEMPERATURE" to null), "KITCHENWARE_140", grams)), "pt")
        assertEquals("mlx-111-pt", r.id); assertEquals("Sopa & teste", r.title); assertEquals("4 pessoas", r.servings)
        assertEquals(Step.Weigh("300 g de cenoura", 300), r.steps[0])
        val m = r.steps[1] as Step.Machine
        assertEquals(listOf(Segment(10, 9)), m.segments)
        assertEquals("Coloque a cenoura na taça.", m.text)
        assertTrue(r.validate().isEmpty())
        assertEquals("https://www.moulinex.pt/receitas/detail/PRO/sopa-teste/999", r.source?.url)
    }

    @Test fun paddleBecomesGentleReverseAndHeatCapsSpeed() {
        val r = Moulinex.convert(recipe(step("Substitua a lâmina picadora pelo acessório misturador. Junte o azeite.",
            op("Manual P", "SPEED" to "V5", "DURATION" to "300", "TEMPERATURE" to "100"), "KITCHENWARE_141")), "pt")
        val m = r.steps.single() as Step.Machine
        assertEquals(listOf(Segment(300, 2, 100, true)), m.segments)
        assertEquals("Junte o azeite.", m.text)
    }

    @Test fun whiskIsTheMixerAttachmentAtFourAtMost() {
        val r = Moulinex.convert(recipe(step("Coloque o batedor e junte as natas.", op("Manual P", "SPEED" to "V7", "DURATION" to "120"), "KITCHENWARE_6")), "pt")
        assertEquals(4, (r.steps.single() as Step.Machine).segments.single().speed)
        assertEquals(listOf(Accessory.MIXER), AccessoryPlan.of(r).needs)
    }

    @Test fun alarmsSplitTheStep() {
        val r = Moulinex.convert(recipe(step("Junte o azeite.", op("Slow cook P1", "TEMPERATURE" to "130", "DURATION" to "420") + "," +
            op("Alert P", "ALERT_TEXT" to "Junte o arroz", "COUNTDOWN" to "240") + "," + op("Alert P", "ALERT_TEXT" to "Junte o vinho", "COUNTDOWN" to "60"),
            "KITCHENWARE_141")), "pt")
        assertEquals(listOf("machine", "Junte o arroz", "machine", "Junte o vinho", "machine"), r.steps.map { if (it is Step.Machine) "machine" else it.text })
        assertEquals(listOf(180, 180, 60), r.steps.filterIsInstance<Step.Machine>().map { it.segments.single().seconds })
        assertTrue(r.steps.filterIsInstance<Step.Machine>().all { it.mode == Mode.ROAST })
    }

    @Test fun programsBecomeMachineSteps() {
        val soup = Moulinex.convert(recipe(step("Junte a água.", op("Soup P1", "TEMPERATURE" to "100", "DURATION" to "2400"))), "pt")
        assertEquals(2, soup.steps.count { it is Step.Machine })
        val slow = Moulinex.convert(recipe(step("Junte o caldo.", op("Slow cook P2", "TEMPERATURE" to "95", "DURATION" to "3000"), "KITCHENWARE_141")), "pt")
        assertEquals(Segment(3000, 1, 95, true), (slow.steps.single() as Step.Machine).segments.single())
        val dough = Moulinex.convert(recipe(step("Junte a farinha.", op("Pastry P1", "DURATION" to "150"), "KITCHENWARE_142")), "pt")
        assertEquals(Mode.KNEAD, (dough.steps.single() as Step.Machine).mode)
        val steam = Moulinex.convert(recipe(step("Coloque os legumes na panela de vapor.", op("Steam P2", "DURATION" to "1500"), "KITCHENWARE_10001")), "pt")
        assertEquals(Mode.STEAM, (steam.steps.last() as Step.Machine).mode)
        assertEquals(listOf(Accessory.STEAMER), AccessoryPlan.of(steam).needs)
        val wait = Moulinex.convert(recipe(step("Leve ao frio.", op("Timer", "DURATION" to "7200"))), "pt")
        assertEquals("Aguarde 2 h.", wait.steps.last().text)
    }

    @Test fun translatedProgramNamesGoByKey() {
        val op = """{"program":{"key":"PROGRAM_000008","name":"Vapor elevado P2"},"parameters":[{"commonKey":"DURATION","value":"1200"}]}"""
        val r = Moulinex.convert(recipe(step("Coloque os legumes.", op, "KITCHENWARE_10001")), "pt")
        assertEquals(Mode.STEAM, (r.steps.last() as Step.Machine).mode)
    }

    @Test fun companionNeoProgramsByName() {
        val op = """{"program":{"key":"PROGRAM_202010","name":"Vapor elevado P2"},"parameters":[{"commonKey":"DURATION","value":"2700"}]}"""
        val r = Moulinex.convert(recipe(step("Coloque os legumes.", op)), "pt")
        assertEquals(Mode.STEAM, (r.steps.last() as Step.Machine).mode)
        assertTrue(r.steps.any { "acessório para vapor fundo" in it.text })
    }

    @Test fun unknownProgramStaysAsText() {
        val r = Moulinex.convert(recipe(step("Faça o pão.", op("Bread P9", "DURATION" to "600"))), "pt")
        assertTrue(r.steps.none { it is Step.Machine })
        assertTrue(r.notes.any { "Bread P9" in it })
    }

    @Test fun listEntries() {
        val page = Moulinex.parseList("""{"page":{"totalElements":2,"totalPages":1},"content":[
            {"identifier":{"functionalId":"488664"},"title":"Fondant de nozes","durations":{"totalTime":60},"courses":["DESSERT"],"seoUrl":"/PRO/fondant-de-nozes/488664",
             "resourceMedias":[{"isCover":true,"media":{"original":"https://x.org/f.jpg"}}]},
            {"identifier":{"functionalId":"974083"},"title":"Pastinaga","durations":{"totalTime":21}},
            {"identifier":{"functionalId":"/v1/variants/1/"},"title":"Old"}]}""", "pt", "Moulinex")
        assertEquals(1, page.entries.size)
        val e = page.entries.single()
        assertEquals("mlx-488664-pt", e.id); assertEquals("moulinex:488664", e.file); assertEquals(listOf("Moulinex", "Sobremesa"), e.tags)
        assertEquals("https://x.org/f.jpg", e.image)
        assertTrue("PRO_COM" in Moulinex.searchBody("pt") && "GS_PT" in Moulinex.searchBody("pt") && "GS_GB" in Moulinex.searchBody("en"))
    }

    @Test fun portugueseGender() {
        assertEquals("Coloque a cebola na máquina e ligue a máquina.", Moulinex.cleanText("Coloque a cebola no robot e ligue o robot.", "pt"))
    }

    @Test fun englishText() {
        assertEquals("Add the carrots.", Moulinex.cleanText("Add the carrots and press \"start\".", "en"))
        assertEquals("Put in the mixer attachment.", Moulinex.cleanText("Replace the ultrablade knife with the whisk.", "en"))
    }
}
