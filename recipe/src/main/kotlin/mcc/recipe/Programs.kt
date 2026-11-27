package mcc.recipe

/**
 * Built-in automatic programs. Each one is an ordinary recipe made only of `run` steps, so it goes through the
 * same runner and the same machine limits as any recipe (speed <= 3 while heating, >= 50 g to heat, 99 min).
 * Heating without the blade is speed 0 with a temperature.
 */
object Programs {
    const val TAG_EN = "Programs"
    const val TAG_PT = "Programas"
    /** Photos ship inside the app (app/src/main/assets/programs/, credits in THIRD-PARTY.md). */
    const val ASSET = "asset:programs/"
    /** Programs without a bundled photo yet: they get a drawn cover. */
    private val DRAWN = emptySet<String>()

    fun all(): List<Recipe> = listOf("en", "pt").flatMap { lang -> defs.map { it(Txt(lang)) } }

    /** Cooking programs (shown with the recipes); cleaning is machine care and has its own button. */
    fun cooking(): List<Recipe> = all().filter { !isCleaning(it) }

    /** The everyday cleaning (Home's Clean button). */
    fun cleaning(lang: String): Recipe = all().first { it.id == "program-clean-$lang" }

    /** Everyday and intensive cleaning, for the Programs screen. */
    fun cleanings(lang: String): List<Recipe> = all().filter { isCleaning(it) && it.language == lang }

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

    /**
     * The Monsieur Cuisine steamer (manual 12.2): the deep basket locks onto the bowl in place of the lid, the shallow
     * one only on top of it, and both go on even when only the shallow one holds food, so no steam escapes.
     */
    private fun steamerOn(x: Txt, food: String) = x.t(
        "Put $food in the deep steamer basket (heavier food and meat or fish at the bottom; more on the shallow steamer basket on top). " +
            "Take the lid off the bowl, lock the deep steamer basket onto the bowl in its place (it clicks) and put the steamer lid on.",
        "Coloque $food no acessório para vapor fundo (o mais pesado, a carne e o peixe em baixo; o resto no acessório para vapor plano por cima). " +
            "Tire a tampa do copo, encaixe o acessório para vapor fundo no copo no lugar dela (ouve-se um clique) e coloque a tampa do vapor.")
    private fun steamerOff(x: Txt) = x.t(
        "Open the steamer lid slowly, away from you: the steam is very hot. Then lift the steamer baskets off.",
        "Abra a tampa do vapor devagar, para o lado oposto a si: o vapor queima. Depois retire o acessório para vapor.")
    private fun weigh(text: String, g: Int) = Step.Weigh(text, g)
    private fun run(text: String, vararg s: Segment) = Step.Machine(text, Mode.RUN, s.toList())
    private fun mode(m: Mode, text: String, minutes: Double, temp: Int = 0) = Step.Machine(text, m, listOf(seg(minutes, 0, temp)))
    private fun seg(min: Double, speed: Int, temp: Int = 0, reverse: Boolean = false) = Segment((min * 60).toInt(), speed, temp, reverse)
    private fun sec(s: Int, speed: Int, temp: Int = 0) = Segment(s, speed, temp)

    private fun eggs(x: Txt, kind: String, en: String, pt: String, minutes: Int) = recipe(x, "eggs-$kind", x.t("Boiled eggs, $en", "Ovos cozidos, $pt"), minutes + 3,
        listOf(x.t("1 to 6 eggs", "1 a 6 ovos"), x.t("500 g water", "500 g de água")),
        weigh(x.t("Pour the water into the bowl.", "Deite a água no copo."), 500),
        say(x.t("Put the eggs in the cooking pot, put it in the bowl and close the lid with the measuring beaker.",
            "Coloque os ovos no acessório de cozer, coloque-o no copo e feche a tampa com o copo de medição.")),
        run(x.t("Cook the eggs (blade off). The time includes bringing the water to the boil.",
            "Cozer os ovos (lâmina parada). O tempo inclui levantar fervura."), seg(minutes.toDouble(), 0, 120)),
        say(x.t("Lift the cooking pot out with the spatula hook and cool the eggs in cold water.",
            "Retire o acessório de cozer com o gancho da espátula e arrefeça os ovos em água fria.")),
        notes = listOf(x.t("Times are for medium eggs straight from the fridge. Add a minute for large eggs.",
            "Tempos para ovos médios tirados do frigorífico. Junte um minuto para ovos grandes.")))

    private val defs: List<(Txt) -> Recipe> = listOf(
        { x -> eggs(x, "soft", "soft", "moles", 13) },
        { x -> eggs(x, "medium", "medium", "médios", 15) },
        { x -> eggs(x, "hard", "hard", "duros", 19) },
        { x -> recipe(x, "rice", x.t("White rice", "Arroz branco"), 30,
            listOf(x.t("250 g long-grain rice", "250 g de arroz agulha"), x.t("1000 g water", "1000 g de água"), x.t("1 tsp salt", "1 c. chá de sal")),
            weigh(x.t("Pour the water into the bowl and add the salt.", "Deite a água no copo e junte o sal."), 1000),
            say(x.t("Rinse the rice in the cooking pot under the tap, then put it in the bowl and close the lid with the measuring beaker.",
                "Lave o arroz no acessório de cozer em água corrente, coloque-o no copo e feche a tampa com o copo de medição.")),
            run(x.t("Cook the rice.", "Cozer o arroz."), seg(22.0, 1, 120)),
            say(x.t("Lift the cooking pot out with the spatula hook, let it drain and fluff the rice with a fork.",
                "Retire o acessório de cozer com o gancho da espátula, deixe escorrer e solte o arroz com um garfo."))) },
        { x -> recipe(x, "steam-veg", x.t("Steamed vegetables", "Legumes ao vapor"), 30,
            listOf(x.t("500 to 800 g vegetables, in even pieces", "500 a 800 g de legumes em pedaços iguais"), x.t("1000 g water", "1000 g de água")),
            weigh(x.t("Pour the water into the bowl (up to the first mark, I).", "Deite a água no copo (até à primeira marca, I)."), 1000),
            say(steamerOn(x, x.t("the vegetables", "os legumes"))),
            mode(Mode.STEAM, x.t("Steam. Firm vegetables (carrots, potatoes) may need 5 minutes more.", "Cozer a vapor. Legumes rijos (cenoura, batata) podem precisar de mais 5 minutos."), 25.0, 120),
            say(steamerOff(x))) },
        { x -> recipe(x, "melt-chocolate", x.t("Melt chocolate", "Derreter chocolate"), 6,
            listOf(x.t("100 to 400 g chocolate, in pieces", "100 a 400 g de chocolate em pedaços")),
            weigh(x.t("Put the chocolate in the bowl (at least 100 g).", "Coloque o chocolate no copo (pelo menos 100 g)."), 100),
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
            listOf(x.t("Kneaded yeast dough", "Massa lêveda amassada")),
            say(x.t("Leave the dough in the bowl (at least 50 g), lid on.", "Deixe a massa no copo (pelo menos 50 g), com a tampa.")),
            run(x.t("Keep warm while it rises (blade off).", "Manter morno enquanto leveda (lâmina parada)."), seg(30.0, 0, 37)),
            say(x.t("If it has not doubled, repeat the step.", "Se ainda não dobrou de volume, repita o passo."))) },
        // the Monsieur Cuisine Smart's long programs: one long low-temperature run (Settings → Machine → Long programs)
        { x -> recipe(x, "slow-cook", x.t("Slow cooking, 4 hours", "Cozedura lenta, 4 horas"), 250,
            listOf(x.t("Meat and vegetables in pieces, with stock or water to cover (1 to 2 kg in total)",
                "Carne e legumes em pedaços, com caldo ou água a cobrir (1 a 2 kg no total)")),
            say(x.t("Put everything in the bowl: the liquid must cover it. Close the lid with the measuring cup in.",
                "Coloque tudo no copo: o líquido tem de cobrir. Feche a tampa com o copo medidor.")),
            run(x.t("Slow cook at 90 °C, stirring gently in reverse.", "Cozer lentamente a 90 °C, mexendo devagar em inverso."),
                seg(240.0, 1, 90, reverse = true)),
            say(x.t("Season and serve. Tougher cuts (beef cheek, shoulder) like 6 hours: run the step again for 2 more.",
                "Tempere e sirva. Cortes mais rijos (bochecha, pá) gostam de 6 horas: repita o passo mais 2.")),
            notes = listOf(x.t("Needs Long programs on (Settings → Machine). Stay nearby the first time.",
                "Precisa de Programas longos ligados (Definições → Máquina). Fique por perto da primeira vez."))) },
        { x -> recipe(x, "sous-vide", x.t("Sous-vide, 60 °C", "Sous-vide, 60 °C"), 100,
            listOf(x.t("Chicken breast, fish or pork, vacuum-sealed (up to 600 g)", "Peito de frango, peixe ou porco embalado em vácuo (até 600 g)"),
                x.t("1500 g warm water", "1500 g de água morna")),
            weigh(x.t("Pour the warm water into the bowl.", "Deite a água morna no copo."), 1500),
            say(x.t("Put the sealed bags in the simmering basket (they must not touch the blade), put it in the bowl and close the lid.",
                "Coloque os sacos no cesto (não podem tocar na lâmina), ponha o cesto no copo e feche a tampa.")),
            run(x.t("Cook gently at 60 °C (blade off).", "Cozinhar suavemente a 60 °C (lâmina parada)."), seg(90.0, 0, 60)),
            say(x.t("Take the bags out, open them and sear the meat briefly in a very hot pan.",
                "Retire os sacos, abra-os e sele a carne rapidamente numa frigideira bem quente.")),
            notes = listOf(x.t("The machine holds 60 °C (its heat levels go in 5 °C steps). Needs Long programs on (Settings → Machine).",
                "A máquina mantém 60 °C (os níveis de calor vão de 5 em 5 °C). Precisa de Programas longos ligados (Definições → Máquina)."))) },
        { x -> recipe(x, "yoghurt", x.t("Yoghurt", "Iogurte"), 560,
            listOf(x.t("1000 g milk", "1000 g de leite"), x.t("150 g plain yoghurt with live cultures", "150 g de iogurte natural com culturas vivas")),
            weigh(x.t("Pour the milk into the bowl.", "Deite o leite no copo."), 1000),
            run(x.t("Heat the milk to 90 °C.", "Aquecer o leite a 90 °C."), seg(12.0, 2, 90)),
            say(x.t("Take the measuring cup out and let the milk cool to about 40 °C (about 45 minutes; the screen shows the bowl temperature).",
                "Tire o copo medidor e deixe o leite arrefecer até cerca de 40 °C (uns 45 minutos; o ecrã mostra a temperatura do copo).")),
            weigh(x.t("Add the yoghurt.", "Junte o iogurte."), 150),
            run(x.t("Stir in.", "Misturar."), sec(10, 3)),
            run(x.t("Ferment at 37 °C for 8 hours (blade off). Don't open the lid.", "Fermentar a 37 °C durante 8 horas (lâmina parada). Não abra a tampa."),
                seg(480.0, 0, 37)),
            say(x.t("Pour into jars and chill for at least 4 hours: it thickens in the fridge.",
                "Passe para frascos e leve ao frigorífico pelo menos 4 horas: engrossa no frio.")),
            notes = listOf(x.t("Needs Long programs on (Settings → Machine). Keep 2 tbsp as the starter for the next batch.",
                "Precisa de Programas longos ligados (Definições → Máquina). Guarde 2 colheres como fermento para a próxima vez."))) },
        { x -> recipe(x, "puree", x.t("Purée", "Puré"), 3,
            listOf(x.t("300 to 1500 g cooked vegetables, fruit or soup", "300 a 1500 g de legumes cozidos, fruta ou sopa")),
            say(x.t("Put the cooked food in the bowl and close the lid with the measuring cup in. Let very hot food cool a little first.",
                "Coloque os alimentos cozidos no copo e feche a tampa com o copo medidor. Deixe arrefecer um pouco o que estiver muito quente.")),
            run(x.t("Purée, speeding up gradually.", "Triturar, aumentando a velocidade aos poucos."), sec(10, 5), sec(10, 7), sec(60, 10))) },
        { x -> recipe(x, "reheat", x.t("Reheat soup or sauce", "Reaquecer sopa ou molho"), 10,
            listOf(x.t("100 to 1500 g soup or sauce", "100 a 1500 g de sopa ou molho")),
            say(x.t("Pour the soup or sauce into the bowl (at least 50 g) and close the lid.", "Deite a sopa ou o molho no copo (pelo menos 50 g) e feche a tampa.")),
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
        // id "varoma" kept so the cooking history still finds it
        { x -> recipe(x, "varoma", x.t("Steaming", "Cozer a vapor"), 25,
            listOf(x.t("1000 g water", "1000 g de água"), x.t("Food to steam: vegetables, fish, chicken, dumplings…", "Alimentos para cozer a vapor: legumes, peixe, frango…")),
            weigh(x.t("Pour the water into the bowl (up to the first mark, I).", "Deite a água no copo (até à primeira marca, I)."), 1000),
            say(steamerOn(x, x.t("the food", "os alimentos"))),
            mode(Mode.STEAM, x.t("Steam (steaming program, 120 °C).", "Cozer a vapor (programa de vapor, 120 °C)."), 20.0, 120),
            say(steamerOff(x))) },
        { x -> recipe(x, "clean", x.t("Clean the bowl", "Limpeza do copo"), 8,
            listOf(x.t("1000 g warm water", "1000 g de água morna"), x.t("1 drop of washing-up liquid", "1 gota de detergente da loiça"), x.t("500 g clean water to rinse", "500 g de água limpa para enxaguar")),
            say(x.t("Empty the bowl and scrape off any big leftovers.", "Esvazie o copo e retire os restos maiores.")),
            weigh(x.t("Pour in the warm water and add one drop of washing-up liquid (more makes foam).",
                "Deite a água morna e junte uma gota de detergente (mais faz espuma)."), 1000),
            say(x.t("Close the lid and put the measuring cup in.", "Feche a tampa e coloque o copo medidor.")),
            // one step, nothing to do in between: warm soak, then short scrubbing bursts
            run(x.t("Soak warm (3 min), then scrub with short bursts.", "Amolecer a quente (3 min) e depois esfregar com impulsos curtos."),
                seg(3.0, 2, 60), sec(10, 5), sec(5, 2), sec(10, 6), sec(5, 2), sec(10, 5)),
            // rinse, so no washing-up liquid is left on the blade for the next recipe
            say(x.t("Pour out the soapy water and put the bowl back.", "Despeje a água com detergente e volte a colocar o copo.")),
            weigh(x.t("Pour in clean water.", "Deite água limpa."), 500),
            run(x.t("Rinse.", "Enxaguar."), sec(10, 4), sec(10, 6)),
            say(x.t("Empty the bowl and dry it and the lid. Burnt-on bits on the blade come off with a brush.",
                "Esvazie o copo e seque-o, com a tampa. Restos agarrados às lâminas saem com uma escova.")),
            notes = listOf(x.t("For burnt-on food, use the intensive cleaning.",
                "Para restos queimados, use a limpeza intensiva."))) },
        { x -> recipe(x, "clean-deep", x.t("Intensive cleaning", "Limpeza intensiva"), 12,
            listOf(x.t("1000 g hot water", "1000 g de água quente"), x.t("2 tbsp white vinegar", "2 c. de sopa de vinagre branco"),
                x.t("500 g clean water to rinse", "500 g de água limpa para enxaguar")),
            say(x.t("Empty the bowl and scrape off what comes away easily.", "Esvazie o copo e retire o que sair com facilidade.")),
            weigh(x.t("Pour in the hot water and add the vinegar (no washing-up liquid).",
                "Deite a água quente e junte o vinagre (sem detergente)."), 1000),
            say(x.t("Close the lid and put the measuring cup in.", "Feche a tampa e coloque o copo medidor.")),
            // a long hot soak loosens burnt-on food; then the same scrubbing bursts, done twice
            run(x.t("Soak hot (8 min), then scrub with short bursts.", "Amolecer bem quente (8 min) e depois esfregar com impulsos curtos."),
                seg(8.0, 2, 80), sec(10, 5), sec(5, 2), sec(10, 6), sec(5, 2), sec(10, 5), sec(5, 2), sec(10, 6), sec(5, 2)),
            say(x.t("Pour out the water and put the bowl back.", "Despeje a água e volte a colocar o copo.")),
            weigh(x.t("Pour in clean water.", "Deite água limpa."), 500),
            run(x.t("Rinse.", "Enxaguar."), sec(10, 4), sec(10, 6)),
            say(x.t("Empty the bowl and dry it and the lid. Anything still stuck to the blade comes off with a brush.",
                "Esvazie o copo e seque-o, com a tampa. O que ainda estiver agarrado às lâminas sai com uma escova.")),
            notes = listOf(x.t("If it is still not clean, run it once more.", "Se ainda não estiver limpo, repita uma vez."))) },
        { x -> recipe(x, "clean-smell", x.t("Remove smells", "Tirar cheiros"), 16,
            listOf(x.t("500 g water", "500 g de água"), x.t("1 lemon, quartered (or 2 tbsp white vinegar)", "1 limão em quartos (ou 2 c. de sopa de vinagre branco)"),
                x.t("500 g clean water to rinse", "500 g de água limpa para enxaguar")),
            say(x.t("Take the seal out of the lid, wash it with soapy water and put it back: it holds most of the smell.",
                "Tire o vedante da tampa, lave-o com água e detergente e volte a colocá-lo: é ele que guarda a maior parte do cheiro.")),
            weigh(x.t("Pour the water into the empty bowl.", "Deite a água no copo vazio."), 500),
            say(x.t("Add the lemon quarters (or the vinegar), close the lid and put the measuring cup in.",
                "Junte os quartos de limão (ou o vinagre), feche a tampa e coloque o copo medidor.")),
            // the steam reaches the lid and its seal, where the smell sits
            run(x.t("Chop the lemon, then simmer for 10 minutes: the steam freshens the lid and the seal.",
                "Picar o limão e ferver 10 minutos: o vapor refresca a tampa e o vedante."), sec(5, 5), seg(10.0, 1, 100)),
            say(x.t("Pour out the lemon water and put the bowl back.", "Despeje a água com limão e volte a colocar o copo.")),
            weigh(x.t("Pour in clean water.", "Deite água limpa."), 500),
            run(x.t("Rinse.", "Enxaguar."), sec(10, 4), sec(10, 6)),
            say(x.t("Empty the bowl and leave it to dry with the lid off: a closed damp bowl keeps smells.",
                "Esvazie o copo e deixe-o secar sem tampa: um copo húmido e fechado guarda cheiros."))) },
    )
}
