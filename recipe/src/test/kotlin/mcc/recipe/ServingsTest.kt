package mcc.recipe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ServingsTest {
    private fun recipe(lang: String, servings: String?, items: List<String>, steps: List<Step>, group: String? = null) = Recipe(
        format = Recipe.FORMAT, id = "x", title = "x", language = lang, servings = servings,
        ingredients = listOf(IngredientGroup(group, items.map { Ingredient(it) })), steps = steps,
    )

    private val gnocchi = recipe(
        "pt", "4 doses",
        listOf("1 c. de chá de sal", "10 batatas farinhentas médias (1 kg)", "3 gemas de ovo (classe M)", "200 g de farinha (tipo 405)",
            "3 pitadas de pimenta", "250 ml de leite (leite fresco, 3,5 % de gordura)", "½ c. de chá de pimenta", "Farinha para a superfície"),
        listOf(
            Step.Weigh("Descasque 10 batatas farinhentas médias, lave-as e corte-as em quartos (1000 g).", 1000),
            Step.Instruction("Deite as batatas cozidas e 3 gemas de ovo (classe M) no recipiente."),
            Step.Weigh("Adicione 200 g de farinha.", 200),
            Step.Instruction("Forme um rolo de 1,5 cm de espessura e corte em 4 pedaços. Pré-aqueça a 180 ºC durante 45 minutos."),
            Step.Instruction("Adicione 3 pitadas de pimenta."),
            Step.Machine("Cozer", segments = listOf(Segment(600, 1, 100))),
        ),
        group = "Para 4 doses (forma de empadão 31 x 24 cm)",
    )

    @Test fun parsesServings() {
        assertEquals(Servings.Base(4, "doses"), Servings.parse("4 doses"))
        assertEquals(Servings.Base(1, "jar"), Servings.parse("1 jar"))
        assertEquals(null, Servings.parse("a few"))
        assertEquals(null, Servings.parse(null))
    }

    @Test fun sameOrUnknownIsUntouched() {
        assertSame(gnocchi, gnocchi.forPeople(4))
        val none = gnocchi.copy(servings = null)
        assertSame(none, none.forPeople(6))
    }

    @Test fun scalesUp() {
        val r = gnocchi.forPeople(6)
        assertEquals("6 doses", r.servings)
        assertEquals("Para 6 doses (forma de empadão 31 x 24 cm)", r.ingredients[0].group)
        assertEquals(
            listOf("1½ c. de chá de sal", "15 batatas farinhentas médias (1,5 kg)", "5 gemas de ovo (classe M)", "300 g de farinha (tipo 405)",
                "4½ pitadas de pimenta", "380 ml de leite (leite fresco, 3,5 % de gordura)", "¾ c. de chá de pimenta", "Farinha para a superfície"),
            r.ingredients[0].items.map { it.text },
        )
        val w = r.steps[0] as Step.Weigh
        assertEquals(1500, w.grams)
        assertEquals("Descasque 15 batatas farinhentas médias, lave-as e corte-as em quartos (1500 g).", w.text)
        assertEquals("Deite as batatas cozidas e 5 gemas de ovo (classe M) no recipiente.", r.steps[1].text)
        assertEquals(300, (r.steps[2] as Step.Weigh).grams)
        assertEquals("Adicione 300 g de farinha.", r.steps[2].text)
        // sizes, temperatures, times and plain counts stay
        assertEquals(gnocchi.steps[3].text, r.steps[3].text)
        assertEquals("Adicione 4½ pitadas de pimenta.", r.steps[4].text)
        assertEquals(gnocchi.steps[5], r.steps[5])
    }

    @Test fun scalesDown() {
        val r = gnocchi.forPeople(2)
        assertEquals(listOf("½ c. de chá de sal", "5 batatas farinhentas médias (0,5 kg)", "2 gemas de ovo (classe M)", "100 g de farinha (tipo 405)"),
            r.ingredients[0].items.take(4).map { it.text })
        assertEquals("¼ c. de chá de pimenta", r.ingredients[0].items[6].text)
        assertEquals(500, (r.steps[0] as Step.Weigh).grams)
    }

    @Test fun englishDecimalsAndRanges() {
        val r = recipe("en", "4 portions", listOf("1.5 kg potatoes", "2-3 cloves garlic", "125 g sugar"),
            listOf(Step.Weigh("Weigh in 125 g sugar.", 125))).forPeople(2)
        assertEquals(listOf("0.8 kg potatoes", "1–1½ cloves garlic", "65 g sugar"), r.ingredients[0].items.map { it.text })
        assertEquals(65, (r.steps[0] as Step.Weigh).grams)
        assertEquals("Weigh in 65 g sugar.", r.steps[0].text)
    }

    @Test fun plurals() {
        val pt = recipe("pt", "2 doses", listOf("1 ovo (classe M)", "1 dente de alho", "2 c. de sopa de azeite", "1 colher de mel"),
            listOf(Step.Instruction("Adicione 1 ovo e 1 ½ c. de chá de sal.")))
        assertEquals(listOf("2 ovos (classe M)", "2 dentes de alho", "4 c. de sopa de azeite", "2 colheres de mel"), pt.forPeople(4).ingredients[0].items.map { it.text })
        assertEquals("Adicione 2 ovos e 3 c. de chá de sal.", pt.forPeople(4).steps[0].text)
        assertEquals(listOf("1 ovo (classe M)", "1 dente de alho"),
            recipe("pt", "4 doses", listOf("2 ovos (classe M)", "2 dentes de alho"), listOf(Step.Instruction("x"))).forPeople(2).ingredients[0].items.map { it.text })
        assertEquals(listOf("1 egg (medium)", "2 egg yolks"), recipe("en", "4 portions", listOf("3 eggs (medium)", "6 egg yolks"), listOf(Step.Instruction("x"))).forPeople(1).ingredients[0].items.map { it.text })
        val en = recipe("en", "2 portions", listOf("1 egg (medium)", "1 garlic clove", "1 pinch of salt", "1 tbsp oil", "1 potato (200 g)"), listOf(Step.Instruction("x")))
        assertEquals(listOf("2 eggs (medium)", "2 garlic clove", "2 pinches of salt", "2 tbsp oil", "2 potatoes (400 g)"), en.forPeople(4).ingredients[0].items.map { it.text })
    }

    @Test fun weighStaysInRange() {
        val r = recipe("en", "1 jar", listOf("2000 g water"), listOf(Step.Weigh("Add 2000 g water.", 2000))).forPeople(3)
        assertEquals(5000, (r.steps[0] as Step.Weigh).grams)
        assertEquals(1..4, Servings.range(Servings.Base(1, "jar")))
        assertEquals(1..12, Servings.range(Servings.Base(4, "portions")))
    }
}
