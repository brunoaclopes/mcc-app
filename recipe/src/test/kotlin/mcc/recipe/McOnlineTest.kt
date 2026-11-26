package mcc.recipe

import mcc.recipe.online.McOnline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A made-up recipe in the shape of the online API (no real catalogue content). */
class McOnlineTest {
    private val detail = """
    {"code":0,"message":"A recipe is returned","data":{"recipe":{
      "id":1001,"language":"pt-PT","name":"Sopa de teste &amp; pão","source":"official","url":"https://example.org/r/1001",
      "complexity":"Fácil","defaultServingSizeId":2,
      "thumbnail":{"landscape":"https://mc-web-content-cdn.tecpal.com/wp-content/uploads/2021/02/1/24407rezeptfoto-6.jpg"},
      "categories":[{"id":1,"name":"Sopas &amp; guisados"}],
      "nutrients":[{"name":"Calories","amount":149},{"name":"Protein","amount":6.4}],
      "servingSizes":[
        {"id":1,"amount":2,"servingUnit":"pessoas","steps":[{"order":0,"description":"wrong size"}]},
        {"id":2,"amount":4,"servingUnit":"pessoas","duration":29,
         "ingredientGroups":[{"id":77,"name":"E AINDA"}],
         "ingredients":[
           {"order":0,"amount":"1","unit":"","name":"cebola (70 g)","ingredientGroupId":null},
           {"order":1,"amount":"1","unit":"pitada","name":"sal","ingredientGroupId":77}],
         "steps":[
           {"order":0,"name":"Deite a cebola.","description":"Deite a cebola.","deviceSetting":{"mode":"scale","settings":[{"weight":70}]}},
           {"order":1,"name":"Mode","description":null,"deviceSetting":{"reverse":false,"mode":"ramp","settings":[
              {"order":0,"speed":1,"temperature":90,"time":900},{"order":1,"speed":5,"temperature":0,"time":2},{"order":2,"speed":9,"temperature":0,"time":2}]}},
           {"order":2,"name":"Mode","deviceSetting":{"reverse":true,"turbo":true,"mode":"turbo","settings":[{"time":12}]}},
           {"order":3,"name":"Mode","deviceSetting":{"reverse":true,"mode":"customized","settings":[{"speed":5,"time":20}]}},
           {"order":4,"name":"Mode","deviceSetting":{"mode":"customized","settings":[{"speed":2,"temperature":42,"time":20}]}},
           {"order":5,"name":"Sirva.","description":"Sirva.","deviceSetting":null}]}]}}}
    """.trimIndent()

    private val list = """
    {"code":0,"data":{"total":2,"totalPage":1,"currentPage":1,"recipes":[
      {"id":1001,"name":"Sopa de teste","source":"official","duration":29,"thumbnail":{"landscape":"https://x.org/a/24407rezeptfoto.jpg"},
       "categories":[{"name":"Sopas"}],"deviceTypes":["MC2.0","MC3.0"]},
      {"id":1002,"name":"De um membro","source":"member"}]}}
    """.trimIndent()

    @Test fun convertsMetadataAndIngredients() {
        val r = McOnline.convert(McOnline.parseDetail(detail), "pt")
        assertEquals(emptyList(), r.validate())
        assertEquals("mco-1001-pt", r.id)
        assertEquals("Sopa de teste & pão", r.title)
        assertEquals("4 pessoas", r.servings)
        assertEquals(29, r.totalMinutes)
        assertEquals("fácil", r.difficulty)
        assertEquals(listOf("Sopas & guisados"), r.tags)
        assertEquals(Nutrition(kcal = 149, proteinG = 6), r.nutritionPerServing)
        assertEquals(listOf(IngredientGroup(null, listOf(Ingredient("1 cebola (70 g)"))), IngredientGroup("E ainda", listOf(Ingredient("1 pitada sal")))), r.ingredients)
        assertEquals("https://mc-recipe-web-prod.storage.googleapis.com/wp-content/uploads/2021/02/1/24407rezeptfoto-6.jpg", r.image)
        assertEquals("monsieur-cuisine-online", r.source?.kind)
    }

    @Test fun convertsStepsWithMachineRules() {
        val s = McOnline.convert(McOnline.parseDetail(detail), "pt").steps
        assertEquals(Step.Weigh("Deite a cebola.", 70), s[0])
        // the heated cook and the blend ramp become two steps
        assertEquals(listOf(Segment(900, 1, 90)), (s[1] as Step.Machine).segments)
        assertEquals(listOf(5, 9), (s[2] as Step.Machine).segments.map { it.speed })
        assertEquals("", s[2].text) // "Mode" is a label, not step text
        assertEquals(Step.Machine("", Mode.TURBO, listOf(Segment(10), Segment(2))), s[3])
        assertFalse((s[4] as Step.Machine).segments[0].reverse) // reverse at speed 5 dropped
        // 42 °C is not a machine level: it runs at the level below (37 °C), with a note
        assertEquals(listOf(Segment(20, 2, 37)), (s[5] as Step.Machine).segments)
        assertEquals(Step.Instruction("Sirva."), s[6])
        assertEquals(7, s.size)
        assertTrue(McOnline.convert(McOnline.parseDetail(detail), "pt").notes.any { "42" in it })
    }

    private fun withStep(json: String) = McOnline.parseDetail(detail.replace(
        "{\"order\":5,\"name\":\"Sirva.\"", "{\"order\":4,\"name\":\"Mode\",\"deviceSetting\":$json},{\"order\":5,\"name\":\"Sirva.\""))

    @Test fun smartProgramsBecomeMachineSteps() {
        val slow = McOnline.convert(withStep("{\"mode\":\"slow_cooking\",\"settings\":[{\"temperature\":90,\"time\":14400}]}"), "pt").steps
        assertTrue(slow.contains(Step.Machine("Cozedura lenta: 4 h a 90 °C.", Mode.RUN, listOf(Segment(14400, 1, 90, true)))))
        val sv = McOnline.convert(withStep("{\"mode\":\"sousVide\",\"settings\":[{\"temperature\":62,\"time\":5400}]}"), "en")
        assertTrue(sv.steps.contains(Step.Machine("Sous-vide: 1 h 30 min at 60 °C.", Mode.RUN, listOf(Segment(5400, 0, 60)))), "62 °C runs at the 60 °C level")
        assertTrue(sv.notes.any { "62" in it })
        assertEquals(emptyList(), sv.validate(), "a 90 min heated segment at speed 0 is a valid long segment")
        val sm = McOnline.convert(withStep("{\"mode\":\"smoothie\",\"settings\":[{\"time\":60}]}"), "pt").steps
        assertTrue(sm.contains(Step.Machine("Batido: 1 min.", Mode.RUN, listOf(Segment(10, 4), Segment(10, 6), Segment(40, 9)))))
    }

    @Test fun attachmentsThisMachineDoesNotHaveStayVisible() {
        val pt = McOnline.convert(withStep("{\"mode\":\"food_processor\",\"settings\":[{\"time\":120}]}"), "pt").steps
        assertTrue(pt.any { it is Step.Instruction && it.text.startsWith("Programa \"Processador de alimentos\" do Monsieur Cuisine Smart: 2 min.") })
        assertEquals("Monsieur Cuisine Smart program \"Air fryer\": 10 min at 200 °C. This machine has no such program: do this step by hand.",
            McOnline.foreignProgram("air_fryer", McOnline.Setting(temperature = 200, time = 600), "en"))
    }

    @Test fun levels() {
        assertEquals(37, McOnline.level(40)); assertEquals(60, McOnline.level(62)); assertEquals(130, McOnline.level(165)); assertEquals(0, McOnline.level(0))
    }

    @Test fun listEntriesSkipMemberRecipes() {
        val e = McOnline.entries(McOnline.parseList(list), "en")
        assertEquals(1, e.size)
        assertEquals("mco-1001-en", e[0].id)
        assertEquals("online:1001", e[0].file)
        assertEquals(29, e[0].totalMinutes)
        assertEquals("24407", McOnline.recipeNumber(e[0].image))
    }

    @Test fun recipeNumber() {
        assertEquals("24386", McOnline.recipeNumber("https://x/thumbnail24386rezeptfoto.jpg"))
        assertEquals("24392", McOnline.recipeNumber("https://x/24392_Rezeptfoto_1623849450.jpg"))
        assertNull(McOnline.recipeNumber("https://x/sunday-rolls-2-1.jpg"))
    }

    @Test fun urls() {
        assertTrue(McOnline.listUrl(2).contains("/page/2/size/100?"))
        assertTrue(McOnline.listUrl(1).contains("MC2.0") && McOnline.listUrl(1).contains("MC3.0"))
        assertEquals("en-US", McOnline.headers("en")["Accept-Language"])
        assertEquals("web", McOnline.headers("pt")["device-type"])
    }
}
