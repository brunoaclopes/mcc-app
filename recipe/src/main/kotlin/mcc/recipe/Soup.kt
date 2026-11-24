package mcc.recipe

import kotlin.math.roundToInt

/**
 * The automatic soup: any mix of vegetables, worked out from their weight. It builds an ordinary recipe (weigh steps,
 * machine steps), so it runs through the same runner and machine limits as any other, and a heated step that is
 * not boiling yet when its time ends keeps going until it is ([RecipeRunner]).
 */
object Soup {
    /** How long the vegetables take: courgette, leek, spinach, tomato... / a mix / potato, carrot, pumpkin, turnip. */
    enum class Kind { TENDER, MIXED, FIRM }

    /** 0 = chunky (not blended), 1 = rustic, 2 = smooth, 3 = velvety. */
    const val BLEND_MAX = 3

    const val MIN_VEG = 300
    const val MAX_VEG = 1000
    /** Most the bowl should hold with a boiling soup. */
    const val MAX_TOTAL = 2000
    /** Onion base: onion, garlic and olive oil. */
    private const val BASE_GRAMS = 140

    data class Options(val vegGrams: Int, val kind: Kind = Kind.MIXED, val blend: Int = 2, val onionBase: Boolean = true)

    data class Plan(val vegGrams: Int, val waterGrams: Int, val cookSeconds: Int, val portions: Int)

    /** Water, cooking time and portions for [o]. */
    fun plan(o: Options): Plan {
        val veg = o.vegGrams.coerceIn(MIN_VEG, MAX_VEG)
        val extras = if (o.onionBase) BASE_GRAMS else 0
        // blended soups are thick with about as much water as vegetables; a chunky soup is more of a broth
        val ratio = if (o.blend > 0) 1.0 else 1.4
        val water = (minOf(veg * ratio, (MAX_TOTAL - veg - extras).toDouble()) / 50).roundToInt() * 50
        val base = when (o.kind) { Kind.TENDER -> 12.0; Kind.MIXED -> 18.0; Kind.FIRM -> 22.0 }
        // chopped by the machine first (soups to be blended) cooks faster than pieces cut by hand
        val cut = if (o.blend > 0) 0.85 else 1.0
        // more in the bowl takes longer to come to the boil: about a minute per extra 250 g over 1 kg
        val heatUp = (veg + water + extras - 1000).coerceAtLeast(0) / 250.0
        val minutes = (base * cut + heatUp).coerceIn(10.0, 35.0)
        val total = veg + water + extras
        return Plan(veg, water, (minutes * 60).roundToInt() / 30 * 30, maxOf(1, (total / 300.0).roundToInt()))
    }

    fun blendSegments(level: Int): List<Segment> = when (level.coerceIn(1, BLEND_MAX)) {
        1 -> listOf(Segment(10, 4), Segment(20, 6))
        2 -> listOf(Segment(10, 4), Segment(10, 6), Segment(40, 8))
        else -> listOf(Segment(10, 4), Segment(10, 6), Segment(10, 8), Segment(60, 10))
    }

    fun recipe(o: Options, lang: String): Recipe {
        fun t(en: String, pt: String) = if (lang == "pt") pt else en
        val p = plan(o)
        val blended = o.blend > 0
        val steps = ArrayList<Step>()
        val ingredients = ArrayList<String>()
        if (o.onionBase) {
            ingredients += t("1 onion (100 g), halved", "1 cebola (100 g) em metades")
            ingredients += t("2 garlic cloves", "2 dentes de alho")
            ingredients += t("30 g olive oil", "30 g de azeite")
            steps += Step.Instruction(t("Put the onion and garlic in the empty bowl and close the lid.", "Coloque a cebola e o alho no copo vazio e feche a tampa."))
            steps += Step.Machine(t("Chop.", "Picar."), Mode.RUN, listOf(Segment(5, 5)))
            steps += Step.Weigh(t("Scrape down the sides with the spatula and add the olive oil.", "Raspe as paredes com a espátula e junte o azeite."), 30)
            steps += Step.Machine(t("Soften the onion (the blade turns now and then).", "Refogar a cebola (a lâmina roda de vez em quando)."), Mode.ROAST, listOf(Segment(240, 0, 120)))
        }
        ingredients += t("${p.vegGrams} g vegetables, peeled", "${p.vegGrams} g de legumes descascados")
        ingredients += t("${p.waterGrams} g water", "${p.waterGrams} g de água")
        ingredients += t("Salt (about 1 tsp per litre) and olive oil to serve", "Sal (cerca de 1 c. chá por litro) e azeite para servir")
        if (blended) {
            steps += Step.Weigh(t("Add the vegetables in rough pieces (about 4 cm).", "Junte os legumes em pedaços grosseiros (cerca de 4 cm)."), p.vegGrams)
            steps += Step.Machine(t("Chop the vegetables, so they cook faster.", "Picar os legumes, para cozerem mais depressa."), Mode.RUN, listOf(Segment(6, 5)))
        } else {
            steps += Step.Weigh(t("Add the vegetables cut into small cubes (1 to 2 cm), the firm ones the smallest.",
                "Junte os legumes cortados em cubos pequenos (1 a 2 cm), os mais rijos mais pequenos."), p.vegGrams)
        }
        steps += Step.Weigh(t("Add the water and the salt.", "Junte a água e o sal."), p.waterGrams)
        // chunky: reverse at speed 1, the blunt side of the blade stirs without cutting the pieces
        steps += Step.Machine(t("Cook the soup.", "Cozer a sopa."), Mode.RUN, listOf(Segment(p.cookSeconds, 1, 100, reverse = !blended)))
        if (blended) {
            steps += Step.Instruction(t("Put the measuring cup in the lid: the soup is hot.", "Coloque o copo medidor na tampa: a sopa está quente."))
            steps += Step.Machine(t("Blend, speeding up gradually.", "Triturar, aumentando a velocidade aos poucos."), Mode.RUN, blendSegments(o.blend))
        }
        steps += Step.Instruction(t("Taste, adjust the salt and serve with a drizzle of olive oil.", "Prove, rectifique o sal e sirva com um fio de azeite."))
        val total = p.vegGrams + p.waterGrams + (if (o.onionBase) BASE_GRAMS else 0)
        return Recipe(
            format = Recipe.FORMAT, id = "program-soup-$lang", title = t("Vegetable soup", "Sopa de legumes"), language = lang,
            servings = t("${p.portions} portions", "${p.portions} doses"),
            totalMinutes = (p.cookSeconds / 60) + (if (o.onionBase) 6 else 0) + 5,
            tags = listOf(if (lang == "pt") Programs.TAG_PT else Programs.TAG_EN), source = Source(kind = "program"),
            ingredients = listOf(IngredientGroup(items = ingredients.map { Ingredient(it) })), steps = steps,
            notes = listOf(t("Worked out for $total g in the bowl. If the vegetables are still firm, cook 5 more minutes at 100 °C, speed 1, in Manual.",
                "Calculada para $total g no copo. Se os legumes ainda estiverem rijos, coza mais 5 minutos a 100 °C, velocidade 1, no Manual.")),
        )
    }
}
