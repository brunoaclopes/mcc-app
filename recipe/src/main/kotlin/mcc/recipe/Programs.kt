package mcc.recipe

/**
 * Built-in automatic programs. Each one is an ordinary recipe made only of `run` steps, so it goes through the
 * same runner and the same machine limits as any recipe (speed <= 3 while heating, >= 200 g to heat, 99 min).
 * Heating without the blade is speed 0 with a temperature.
 */
object Programs {
    const val TAG_EN = "Programs"
    const val TAG_PT = "Programas"
    /** Photos ship inside the app (app/src/main/assets/programs/, credits in THIRD-PARTY.md). */
    const val ASSET = "asset:programs/"
    /** Programs without a bundled photo yet: they get a drawn cover. */
    private val DRAWN = setOf("knead", "brown", "varoma")

    fun all(): List<Recipe> = listOf("en", "pt").flatMap { lang -> defs.map { it(Txt(lang)) } }

    /** Cooking programs (shown with the recipes); cleaning is machine care and has its own button. */
    fun cooking(): List<Recipe> = all().filter { !isCleaning(it) }

    fun cleaning(lang: String): Recipe = all().first { isCleaning(it) && it.language == lang }

    fun isCleaning(r: Recipe) = r.id.startsWith("program-clean-")

    private class Txt(val lang: String) {
        fun t(en: String, pt: String) = if (lang == "pt") pt else en
    }

    private fun recipe(x: Txt, id: String, title: String, minutes: Int, ingredients: List<String>, vararg steps: Step, notes: List<String> = emptyList()) = Recipe(
        format = Recipe.FORMAT, id = "program-$id-${x.lang}", title = title, language = x.lang, totalMinutes = minutes,
        image = if (id in DRAWN) null else "$ASSET$id.jpg",
        tags = listOf(x.t(TAG_EN, TAG_PT)), source = Source(kind = "program"),
        ingredients = listOf(IngredientGroup(items = ingredients.map { Ingredient(it) })), steps = steps.toList(), notes = notes,
    )

    private fun say(text: String) = Step.Instruction(text)
    private fun weigh(text: String, g: Int) = Step.Weigh(text, g)
    private fun run(text: String, vararg s: Segment) = Step.Machine(text, Mode.RUN, s.toList())
    private fun mode(m: Mode, text: String, minutes: Double, temp: Int = 0) = Step.Machine(text, m, listOf(seg(minutes, 0, temp)))
    private fun seg(min: Double, speed: Int, temp: Int = 0, reverse: Boolean = false) = Segment((min * 60).toInt(), speed, temp, reverse)
    private fun sec(s: Int, speed: Int, temp: Int = 0) = Segment(s, speed, temp)

    private fun eggs(x: Txt, kind: String, en: String, pt: String, minutes: Int) = recipe(x, "eggs-$kind", x.t("Boiled eggs, $en", "Ovos cozidos, $pt"), minutes + 3,
        listOf(x.t("1 to 6 eggs", "1 a 6 ovos"), x.t("500 g water", "500 g de água")),
        weigh(x.t("Pour the water into the bowl.", "Deite a água no copo."), 500),
        say(x.t("Put the eggs in the simmering basket, put the basket in the bowl and close the lid.",
            "Coloque os ovos no cesto, ponha o cesto no copo e feche a tampa.")),
        run(x.t("Cook the eggs (blade off). The time includes bringing the water to the boil.",
            "Cozer os ovos (lâmina parada). O tempo inclui levantar fervura."), seg(minutes.toDouble(), 0, 120)),
        say(x.t("Lift the basket out with the spatula hook and cool the eggs in cold water.",
            "Retire o cesto com o gancho da espátula e arrefeça os ovos em água fria.")),
        notes = listOf(x.t("Times are for medium eggs straight from the fridge. Add a minute for large eggs.",
            "Tempos para ovos médios tirados do frigorífico. Junte um minuto para ovos grandes.")))

    private val defs: List<(Txt) -> Recipe> = listOf(
        { x -> eggs(x, "soft", "soft", "moles", 13) },
        { x -> eggs(x, "medium", "medium", "médios", 15) },
        { x -> eggs(x, "hard", "hard", "duros", 19) },
        { x -> recipe(x, "rice", x.t("White rice", "Arroz branco"), 30,
            listOf(x.t("250 g long-grain rice", "250 g de arroz agulha"), x.t("1000 g water", "1000 g de água"), x.t("1 tsp salt", "1 c. chá de sal")),
            weigh(x.t("Pour the water into the bowl and add the salt.", "Deite a água no copo e junte o sal."), 1000),
            say(x.t("Rinse the rice in the simmering basket under the tap, then put the basket in the bowl and close the lid.",
                "Lave o arroz no cesto em água corrente, ponha o cesto no copo e feche a tampa.")),
            run(x.t("Cook the rice.", "Cozer o arroz."), seg(22.0, 1, 120)),
            say(x.t("Lift the basket out with the spatula hook, let it drain and fluff the rice with a fork.",
                "Retire o cesto com o gancho da espátula, deixe escorrer e solte o arroz com um garfo."))) },
        { x -> recipe(x, "steam-veg", x.t("Steamed vegetables", "Legumes ao vapor"), 30,
            listOf(x.t("500 to 800 g vegetables, in even pieces", "500 a 800 g de legumes em pedaços iguais"), x.t("750 g water", "750 g de água")),
            weigh(x.t("Pour the water into the bowl.", "Deite a água no copo."), 750),
            say(x.t("Put the vegetables in the steaming tray (Varoma), set it on the lid and cover it.",
                "Coloque os legumes no recipiente de vapor (Varoma), encaixe-o na tampa e tape-o.")),
            run(x.t("Steam. Firm vegetables (carrots, potatoes) may need 5 minutes more.", "Cozer a vapor. Legumes rijos (cenoura, batata) podem precisar de mais 5 minutos."),
                seg(25.0, 1, 120)),
            say(x.t("Open the steaming lid away from you: the steam is very hot.", "Abra a tampa do vapor para o lado oposto a si: o vapor queima."))) },
        { x -> recipe(x, "melt-chocolate", x.t("Melt chocolate", "Derreter chocolate"), 6,
            listOf(x.t("200 to 400 g chocolate, in pieces", "200 a 400 g de chocolate em pedaços")),
            weigh(x.t("Put the chocolate in the bowl (at least 200 g).", "Coloque o chocolate no copo (pelo menos 200 g)."), 200),
            run(x.t("Grate.", "Ralar."), sec(10, 7)),
            run(x.t("Melt gently.", "Derreter lentamente."), seg(4.0, 2, 50)),
            say(x.t("Scrape down the sides. If it is not fully melted, run 1 more minute at 50 °C, speed 2 in Manual.",
                "Raspe as paredes. Se não estiver todo derretido, mais 1 minuto a 50 °C, velocidade 2, no Manual."))) },
        { x -> recipe(x, "smoothie", x.t("Smoothie", "Batido"), 3,
            listOf(x.t("300 to 600 g fruit", "300 a 600 g de fruta"), x.t("200 to 400 g milk, yoghurt or juice", "200 a 400 g de leite, iogurte ou sumo"), x.t("Ice (optional)", "Gelo (opcional)")),
            say(x.t("Put the fruit and the liquid in the bowl (1 litre at most) and close the lid with the measuring cup in.",
                "Coloque a fruta e o líquido no copo (no máximo 1 litro) e feche a tampa com o copo medidor.")),
            run(x.t("Blend, speeding up gradually.", "Triturar, aumentando a velocidade aos poucos."), sec(10, 4), sec(10, 6), sec(40, 9))) },
        { x -> recipe(x, "proof-dough", x.t("Proof dough", "Levedar massa"), 30,
            listOf(x.t("Kneaded yeast dough (at least 200 g)", "Massa lêveda amassada (pelo menos 200 g)")),
            say(x.t("Leave the dough in the bowl (at least 200 g), lid on.", "Deixe a massa no copo (pelo menos 200 g), com a tampa.")),
            run(x.t("Keep warm while it rises (blade off).", "Manter morno enquanto leveda (lâmina parada)."), seg(30.0, 0, 37)),
            say(x.t("If it has not doubled, repeat the step.", "Se ainda não dobrou de volume, repita o passo."))) },
        { x -> recipe(x, "reheat", x.t("Reheat soup or sauce", "Reaquecer sopa ou molho"), 10,
            listOf(x.t("300 to 1500 g soup or sauce", "300 a 1500 g de sopa ou molho")),
            say(x.t("Pour the soup or sauce into the bowl (at least 200 g) and close the lid.", "Deite a sopa ou o molho no copo (pelo menos 200 g) e feche a tampa.")),
            run(x.t("Reheat, stirring.", "Reaquecer, mexendo."), seg(8.0, 2, 90))) },
        // the stock app's three special modes (firmware work modes), copied from its presets; see MachinePlan
        { x -> recipe(x, "knead", x.t("Knead dough", "Amassar massa"), 3,
            listOf(x.t("Dough ingredients, at most 600 g in total (e.g. 300 g flour, 180 g water, 1 tsp salt, 7 g dry yeast)",
                "Ingredientes da massa, no máximo 600 g no total (p. ex. 300 g de farinha, 180 g de água, 1 c. chá de sal, 7 g de fermento seco)")),
            say(x.t("Put the dough ingredients in the bowl (600 g at most) and close the lid.",
                "Coloque os ingredientes da massa no copo (no máximo 600 g) e feche a tampa.")),
            mode(Mode.KNEAD, x.t("Knead: the blade turns back and forth at speed 4.", "Amassar: a lâmina roda nos dois sentidos na velocidade 4."), 2.0),
            say(x.t("Tip the dough onto a floured surface. For a smoother dough, knead another 2 minutes.",
                "Vire a massa para uma bancada enfarinhada. Para uma massa mais lisa, amasse mais 2 minutos."))) },
        { x -> recipe(x, "brown", x.t("Brown onion and garlic", "Alourar cebola e alho"), 9,
            listOf(x.t("1 onion (100 g), halved", "1 cebola (100 g) em metades"), x.t("2 garlic cloves", "2 dentes de alho"), x.t("30 g olive oil", "30 g de azeite")),
            say(x.t("Put the onion and garlic in the bowl and close the lid.", "Coloque a cebola e o alho no copo e feche a tampa.")),
            run(x.t("Chop.", "Picar."), sec(5, 5)),
            weigh(x.t("Scrape down the sides with the spatula and add the olive oil.", "Raspe as paredes com a espátula e junte o azeite."), 30),
            mode(Mode.ROAST, x.t("Brown at 130 °C, stirring now and then (the blade turns 5 s every 35 s).",
                "Alourar a 130 °C, mexendo de vez em quando (a lâmina roda 5 s a cada 35 s)."), 7.0, 130),
            say(x.t("Ready: carry on with your recipe, or take it out.", "Pronto: continue a receita ou retire.")),
            notes = listOf(x.t("Browning needs at least 50 g in the bowl.", "Alourar precisa de pelo menos 50 g no copo."))) },
        { x -> recipe(x, "varoma", x.t("Varoma steaming", "Cozer a vapor (Varoma)"), 25,
            listOf(x.t("500 g water", "500 g de água"), x.t("Food for the Varoma: vegetables, fish, chicken, dumplings…", "Alimentos para a Varoma: legumes, peixe, frango…")),
            weigh(x.t("Pour the water into the bowl.", "Deite a água no copo."), 500),
            say(x.t("Close the lid, put the Varoma on top with the food and its lid on.", "Feche a tampa, coloque a Varoma por cima com os alimentos e a tampa.")),
            mode(Mode.STEAM, x.t("Steam (Varoma mode, 120 °C).", "Cozer a vapor (modo Varoma, 120 °C)."), 20.0, 120),
            say(x.t("Lift the Varoma off carefully: the steam is very hot. Open its lid away from you.",
                "Retire a Varoma com cuidado: o vapor queima. Abra a tampa para longe de si."))) },
        { x -> recipe(x, "clean", x.t("Clean the bowl", "Limpeza do copo"), 8,
            listOf(x.t("1000 g warm water", "1000 g de água morna"), x.t("1 drop of washing-up liquid", "1 gota de detergente da loiça")),
            say(x.t("Empty the bowl and scrape off any big leftovers.", "Esvazie o copo e retire os restos maiores.")),
            weigh(x.t("Pour in the warm water and add one drop of washing-up liquid (more makes foam).",
                "Deite a água morna e junte uma gota de detergente (mais faz espuma)."), 1000),
            say(x.t("Close the lid and put the measuring cup in.", "Feche a tampa e coloque o copo medidor.")),
            run(x.t("Soak and stir warm.", "Amolecer e mexer a quente."), seg(3.0, 2, 60)),
            run(x.t("Scrub: short bursts.", "Esfregar: impulsos curtos."), sec(10, 5), sec(5, 2), sec(10, 6), sec(5, 2), sec(10, 5)),
            say(x.t("Empty the bowl, then rinse it and the lid under the tap. The blade unit can be cleaned with a brush; dry everything.",
                "Esvazie o copo e passe-o, com a tampa, por água. As lâminas limpam-se com uma escova; seque tudo.")),
            notes = listOf(x.t("For burnt-on food, repeat with a tablespoon of vinegar instead of the washing-up liquid.",
                "Para restos queimados, repita com uma colher de sopa de vinagre em vez do detergente."))) },
    )
}
