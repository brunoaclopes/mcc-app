package mcc.recipe.online

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mcc.recipe.Ingredient
import mcc.recipe.IngredientGroup
import mcc.recipe.Mode
import mcc.recipe.Nutrition
import mcc.recipe.Recipe
import mcc.recipe.RecipeIndex
import mcc.recipe.Segment
import mcc.recipe.Source
import mcc.recipe.Step

/**
 * The public recipe catalogue of the Monsieur Cuisine website (the same API its recipe pages use), read live:
 * the list of recipes for this machine family (official ones, or also the ones members published), and one recipe
 * at a time when it is opened. Nothing
 * from it is stored in this repository; the apps only cache what the user opens. See docs/ARCHITECTURE.md.
 *
 * This file only knows the JSON shapes and the conversion to mcc-recipe/1 (pure Kotlin, tested on the JVM);
 * the HTTP calls live in the apps' RecipeStore.
 */
object McOnline {
    const val API = "https://mc-api.tecpal.com"
    /** Recipes for the Monsieur Cuisine Connect (MC2.0) and the Smart (MC3.0), whose steps this machine can run. */
    val DEVICES = listOf("MC2.0", "MC3.0")
    const val PAGE_SIZE = 100
    /** Headers the website sends; without device-type the API leaves the ingredient names out. */
    fun headers(lang: String) = mapOf("Accept-Language" to apiLanguage(lang), "device-type" to "web")
    fun apiLanguage(lang: String) = if (lang == "en") "en-US" else "pt-PT"

    /** Recipe sources of the API: the website's own recipes, and the ones members published from the Smart app. */
    const val OFFICIAL = "official"
    const val MEMBER = "member"

    /** One list page: official recipes only, or with [members] every source (official and member). */
    fun listUrl(page: Int, members: Boolean = false): String =
        "$API/api/v1/recipes/page/$page/size/$PAGE_SIZE?" + (if (members) "" else "filters%5Bsource%5D%5B%5D=$OFFICIAL&") +
            DEVICES.joinToString("&") { "filters%5BdeviceTypes%5D%5B%5D=$it" }

    fun detailUrl(id: Long) = "$API/api/v2/recipes/$id"

    /** Index entries use this file prefix; the number after it is the online recipe id. */
    const val FILE_PREFIX = "online:"
    const val ID_PREFIX = "mco-"

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    // ---- JSON shapes (only the fields we use) ----

    @Serializable data class ListResponse(val code: Int = -1, val message: String? = null, val data: ListData? = null)
    @Serializable data class ListData(val total: Int = 0, val totalPage: Int = 0, val currentPage: Int = 0, val recipes: List<Summary> = emptyList())
    @Serializable data class Summary(
        val id: Long, val name: String = "", val language: String? = null, val source: String? = null,
        val thumbnail: Image? = null, val duration: Int? = null, val categories: List<Named> = emptyList(),
        val deviceTypes: List<String> = emptyList(),
    )
    @Serializable data class Image(val landscape: String? = null, val portrait: String? = null)
    @Serializable data class Named(val id: Long? = null, val name: String? = null)

    @Serializable data class DetailResponse(val code: Int = -1, val message: String? = null, val data: DetailData? = null)
    @Serializable data class DetailData(val recipe: Detail? = null)
    @Serializable data class Detail(
        val id: Long, val language: String? = null, val name: String = "", val source: String? = null,
        val url: String? = null, val complexity: String? = null, val thumbnail: Image? = null, val detailsImage: Image? = null,
        val categories: List<Named> = emptyList(), val defaultServingSizeId: Int? = null,
        val servingSizes: List<ServingSize> = emptyList(), val nutrients: List<Nutrient> = emptyList(),
    )
    @Serializable data class ServingSize(
        val id: Int = 1, val amount: Double? = null, val servingUnit: String? = null, val instruction: String? = null,
        val preparationDuration: Int? = null, val duration: Int? = null,
        val steps: List<ApiStep> = emptyList(), val ingredientGroups: List<Named> = emptyList(),
        val ingredients: List<ApiIngredient> = emptyList(),
    )
    @Serializable data class ApiStep(val order: Int = 0, val name: String? = null, val description: String? = null, val deviceSetting: DeviceSetting? = null)
    @Serializable data class DeviceSetting(val reverse: Boolean? = false, val turbo: Boolean? = false, val mode: String? = null, val settings: List<Setting> = emptyList())
    @Serializable data class Setting(val order: Int = 0, val speed: Double? = null, val temperature: Int? = null, val time: Int? = null, val weight: Int? = null)
    @Serializable data class ApiIngredient(val order: Int = 0, val amount: String? = null, val unit: String? = null, val name: String? = null, val ingredientGroupId: Long? = null)
    @Serializable data class Nutrient(val name: String? = null, val amount: Double? = null)

    fun parseList(text: String): ListData {
        val r = json.decodeFromString(ListResponse.serializer(), text)
        return r.data ?: throw IllegalStateException(r.message ?: "no data")
    }

    fun parseDetail(text: String): Detail {
        val r = json.decodeFromString(DetailResponse.serializer(), text)
        return r.data?.recipe ?: throw IllegalStateException(r.message ?: "no recipe")
    }

    // ---- index entries ----

    /**
     * Index entries for one list page in [lang]: official recipes, and with [members] also member recipes, which
     * get [memberTag] as their first tag. Other sources are left out.
     */
    fun entries(page: ListData, lang: String, members: Boolean = false, memberTag: String = "Member"): List<RecipeIndex.Entry> =
        page.recipes.filter { isOfficial(it.source) || (members && it.source == MEMBER) }.map { s ->
        val tags = s.categories.mapNotNull { it.name?.let(::clean) }.filter { it.isNotBlank() }.distinct()
        RecipeIndex.Entry(
            id = "$ID_PREFIX${s.id}-$lang", title = clean(s.name), language = lang,
            tags = (if (s.source == MEMBER) listOf(memberTag) + tags else tags).take(4),
            totalMinutes = s.duration?.takeIf { it > 0 }, image = imageUrl(s.thumbnail?.landscape), file = "$FILE_PREFIX${s.id}",
        )
    }

    private fun isOfficial(source: String?) = source == null || source == OFFICIAL

    /**
     * The stock-app recipe number in a photo's file name ("24407rezeptfoto-6.jpg" -> "24407"). The same number
     * keys the mc2-/mc3- recipes of a recipe repository, so the apps can hide online copies of recipes they already have.
     */
    fun recipeNumber(image: String?): String? {
        val name = image?.substringAfterLast('/') ?: return null
        return Regex("^(?:thumbnail)?(\\d{5})[_-]?rezept", RegexOption.IGNORE_CASE).find(name)?.groupValues?.get(1)
    }

    /**
     * The photo CDN (mc-web-content-cdn.tecpal.com) sends an incomplete certificate chain that Android rejects;
     * the same files are served from Google Cloud Storage, which works on old Android versions too.
     */
    fun imageUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val cdn = Regex("^https://mc-web-content-cdn\\.tecpal\\.com/wp-content/")
        return if (cdn.containsMatchIn(url)) url.replace(cdn, "https://mc-recipe-web-prod.storage.googleapis.com/wp-content/") else url
    }

    // ---- conversion to mcc-recipe/1 ----

    /** Mode names come as "slow_cooking" or "slowCooking": compare them without case, "_" or "-". */
    private fun key(mode: String) = mode.lowercase().replace("_", "").replace("-", "")
    private val modeOf = mapOf("customized" to Mode.RUN, "ramp" to Mode.RUN, "turbo" to Mode.TURBO, "kneading" to Mode.KNEAD,
        "kneadingsoliddough" to Mode.KNEAD, "kneadingsoftdough" to Mode.KNEAD, "kneadingliquiddough" to Mode.KNEAD,
        "roasting" to Mode.ROAST, "roastinggentle" to Mode.ROAST, "roastingintense" to Mode.ROAST, "steaming" to Mode.STEAM)
    /** Attachments this machine does not have. */
    private val hardware = setOf("foodprocessor", "citrusjuicer", "airfryer", "contactgrill")

    /** The machine level at or below [t] (37 °C at least when heating, 130 °C at most). */
    fun level(t: Int): Int = if (t <= 0) 0 else TEMPS_C.filter { it in 1..minOf(t, 130) }.maxOrNull() ?: 37
    /** Temperature levels the machine has (the same table as recipe-repo/converters/mcc_format.py). */
    val TEMPS_C = setOf(0, 37, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100, 105, 110, 115, 120, 125, 130)
    const val TURBO_MAX_S = 10

    /**
     * Converts one recipe, using its default serving size. Same rules as recipe-repo's converters: a heated
     * segment followed by unheated ones becomes two steps (the cook finishes, the user starts the blend),
     * turbo longer than [TURBO_MAX_S] is split into pulses, reverse above speed 3 is dropped, and settings
     * the machine does not have are kept as text with a note. The machine layer enforces its limits anyway.
     */
    fun convert(d: Detail, lang: String): Recipe {
        val notes = ArrayList<String>()
        if (d.source == MEMBER) notes += memberNote(lang)
        val size = d.servingSizes.firstOrNull { it.id == d.defaultServingSizeId } ?: d.servingSizes.firstOrNull()
        var steps = size?.steps.orEmpty().sortedBy { it.order }.flatMap { convertStep(it, lang, notes) }
        if (steps.isEmpty() && !size?.instruction.isNullOrBlank()) {
            steps = clean(size!!.instruction).split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotEmpty() }.map { Step.Instruction(it) }
            notes += "no guided steps in source; instructions are text only"
        }
        if (steps.isEmpty()) steps = listOf(Step.Instruction(d.url ?: clean(d.name)))

        val groups = LinkedHashMap<Long?, MutableList<Ingredient>>()
        groups[null] = ArrayList()
        size?.ingredientGroups.orEmpty().forEach { g -> groups[g.id] = ArrayList() }
        for (i in size?.ingredients.orEmpty().sortedBy { it.order }) {
            var text = clean(i.name)
            val amount = listOf(clean(i.amount), clean(i.unit)).filter { it.isNotEmpty() }.joinToString(" ")
            if (amount.isNotEmpty() && !text.startsWith(clean(i.amount))) text = "$amount $text".trim()
            if (text.isNotEmpty()) (groups[i.ingredientGroupId] ?: groups.getValue(null)).add(Ingredient(text))
        }
        val names = size?.ingredientGroups.orEmpty().associate { it.id to groupTitle(it.name) }
        val ingredients = groups.filterValues { it.isNotEmpty() }.map { (id, items) -> IngredientGroup(id?.let { names[it] }, items) }

        val nut = d.nutrients.associate { (it.name ?: "").lowercase() to it.amount?.let(Math::round)?.toInt() }
        val nutrition = Nutrition(nut["calories"], nut["protein"], nut["carbohydrate"], nut["fat"]).takeIf { n ->
            listOf(n.kcal, n.proteinG, n.carbsG, n.fatG).any { it != null }
        }
        val servings = size?.amount?.let { a -> listOf(if (a % 1.0 == 0.0) a.toInt().toString() else a.toString(), clean(size.servingUnit)).filter { it.isNotEmpty() }.joinToString(" ") }
        val image = imageUrl(d.detailsImage?.landscape ?: d.thumbnail?.landscape)
        return Recipe(
            format = Recipe.FORMAT, id = "$ID_PREFIX${d.id}-$lang", title = clean(d.name).ifBlank { "Recipe ${d.id}" }, language = lang,
            servings = servings, activeMinutes = size?.preparationDuration?.takeIf { it > 0 }, totalMinutes = size?.duration?.takeIf { it > 0 },
            difficulty = clean(d.complexity).lowercase().ifBlank { null },
            tags = d.categories.mapNotNull { it.name?.let(::clean) }.filter { it.isNotBlank() }.distinct().take(4),
            image = image, source = Source("monsieur-cuisine-online", d.id.toString(), d.url),
            nutritionPerServing = nutrition, ingredients = ingredients, steps = steps, notes = notes,
        )
    }

    /** The first note of a member recipe, in the recipe's language. */
    fun memberNote(lang: String) =
        if (lang == "en") "Member recipe: written by a Monsieur Cuisine user; its settings are not checked by Monsieur Cuisine. Read each step before you start it."
        else "Receita de membro: escrita por um utilizador do Monsieur Cuisine; as definições não são verificadas pelo Monsieur Cuisine. Leia cada passo antes de o iniciar."

    private val modeLabels = setOf("mode", "modo", "customized", "steaming", "kneading", "roasting", "turbo", "ramp", "scale",
        "cozedura ajustada", "cozer a vapor", "amassar", "dourar", "pesar", "rampa")

    private fun convertStep(s: ApiStep, lang: String, notes: MutableList<String>): List<Step> {
        var text = clean(s.description)
        val name = clean(s.name)
        val ds = s.deviceSetting
        if (text.isEmpty() && name.isNotEmpty() && ds == null && name.lowercase() !in modeLabels) text = name
        val where = "step ${s.order}"
        fun asText(): List<Step> = if (text.isNotEmpty()) listOf(Step.Instruction(text)) else emptyList()
        if (ds?.mode == null) return asText()
        val params = ds.settings.sortedBy { it.order }
        if (ds.mode == "scale") {
            val grams = params.firstOrNull()?.weight ?: 0
            return if (grams in 1..5000) listOf(Step.Weigh(text, grams)) else asText()
        }
        val k = key(ds.mode)
        if (k in hardware) {
            // an attachment this machine does not have: say what it was, so the step does not silently disappear
            notes += "$where: ${ds.mode} needs an attachment this machine does not have; kept as text"
            return asText() + Step.Instruction(foreignProgram(k, params.firstOrNull(), lang))
        }
        program(k, params.firstOrNull(), lang, notes, where)?.let { steps ->
            if (text.isEmpty()) return steps
            // the source's own text goes on the first machine step
            return steps.mapIndexed { i, st -> if (i == 0 && st is Step.Machine) st.copy(text = text) else st }
        }
        val mode = modeOf[k] ?: run {
            notes += "$where: ${ds.mode} is not a program of this machine; kept as text"
            return asText() + Step.Instruction(foreignProgram(k, params.firstOrNull(), lang))
        }
        var reverse = ds.reverse == true
        val segs = ArrayList<Segment>()
        for (p in params) {
            var sec = p.time ?: 0
            val speed = (p.speed ?: 0.0).toInt()
            val asked = p.temperature ?: 0
            if (sec <= 0) continue
            val temp = level(asked)
            if (temp != asked) notes += "$where: $asked °C runs at the machine level $temp °C"
            when (mode) {
                Mode.RUN -> {
                    if (reverse && speed > 3) { notes += "$where: reverse at speed $speed is not allowed; reverse dropped"; reverse = false }
                    if (speed == 0 && temp == 0) continue
                    if (speed !in 0..10) { notes += "$where: speed $speed kept as text"; return asText() }
                    segs += Segment(minOf(sec, 99 * 60), speed, temp, reverse)
                }
                Mode.ROAST -> segs += Segment(minOf(sec, 99 * 60), tempC = if (temp > 0) temp else 130)
                Mode.TURBO -> while (sec > 0) { segs += Segment(minOf(sec, TURBO_MAX_S)); sec -= TURBO_MAX_S }
                else -> segs += Segment(minOf(sec, 99 * 60))
            }
        }
        if (segs.isEmpty()) { notes += "$where: ${ds.mode} without usable settings kept as text"; return asText() }
        if (mode != Mode.RUN) return listOf(Step.Machine(text, mode, segs))
        // a heated cook followed by an unheated blend: two steps, so the user starts the blending
        val chunks = ArrayList<MutableList<Segment>>()
        chunks += mutableListOf(segs[0])
        for (g in segs.drop(1)) {
            if (chunks.last().last().tempC > 0 && g.tempC == 0) chunks += mutableListOf<Segment>()
            chunks.last() += g
        }
        return chunks.mapIndexed { k, c -> Step.Machine(if (k == 0) text else "", Mode.RUN, c) }
    }

    private val programNames = mapOf(
        "slowcooking" to ("Slow cooking" to "Cozedura lenta"), "sousvide" to ("Sous-vide" to "Sous-vide"),
        "fermentation" to ("Fermentation" to "Fermentação"), "smoothie" to ("Smoothie" to "Batido"),
        "puree" to ("Purée" to "Puré"), "cookingeggs" to ("Boiled eggs" to "Cozer ovos"),
        "ricecooking" to ("Rice" to "Cozer arroz"), "precleaning" to ("Pre-clean" to "Pré-limpeza"),
        "heatwater" to ("Heat water" to "Aquecer água"), "heatingup" to ("Heating up" to "Aquecer"),
        "opencooking" to ("Open cooking" to "Cozinhar sem tampa"), "kneadingprovedough" to ("Proving dough" to "Levedar massa"),
        "foodprocessor" to ("Food processor" to "Processador de alimentos"), "citrusjuicer" to ("Citrus juicer" to "Espremedor de citrinos"),
        "airfryer" to ("Air fryer" to "Fritadeira de ar"), "contactgrill" to ("Contact grill" to "Grelhador de contacto"),
    )

    /**
     * The Monsieur Cuisine Smart programs, built from what this machine can do (the same pieces as the built-in
     * Programs). Slow cooking, sous-vide and fermentation are long low-temperature runs: the machine runs them
     * only with long programs switched on, else it says so when the step starts. Null: not a program.
     */
    private fun program(k: String, p: Setting?, lang: String, notes: MutableList<String>, where: String): List<Step>? {
        val sec = p?.time ?: 0
        val asked = p?.temperature ?: 0
        val t = level(asked)
        fun label() = programLabel(k, sec, t, lang)
        fun run(vararg g: Segment) = listOf(Step.Machine(label(), Mode.RUN, g.toList()))
        fun long(max: Int, speed: Int, reverse: Boolean): List<Step>? {
            if (sec <= 0 || t <= 0) return null
            if (t != asked) notes += "$where: $asked °C runs at the machine level $t °C"
            if (sec > max) notes += "$where: ${sec / 60} min shortened to ${max / 60} min"
            return run(Segment(minOf(sec, max), speed, minOf(t, Recipe.LONG_MAX_TEMP_C), reverse))
        }
        return when (k) {
            // stirs gently in reverse, like a long simmer; 8 h at most, as on the Smart
            "slowcooking" -> long(8 * 3600, 1, true)
            // bags in water: blade off, the heater holds the level
            "sousvide" -> long(Recipe.MAX_LONG_SECONDS, 0, false)
            "fermentation" -> long(Recipe.MAX_LONG_SECONDS, 0, false)
            "kneadingprovedough" -> if (sec > 0) run(Segment(minOf(sec, Recipe.MAX_SECONDS), 0, 37)) else null
            "smoothie" -> if (sec <= 0) null else if (sec >= 30) run(Segment(10, 4), Segment(10, 6), Segment(sec - 20, 9)) else run(Segment(sec, 8))
            "puree" -> if (sec <= 0) null else if (sec >= 30) run(Segment(10, 5), Segment(10, 7), Segment(minOf(sec - 20, 15 * 60), 10)) else run(Segment(sec, 8))
            // the built-in egg program: blade off, boiling (times include heating the water)
            "cookingeggs" -> { val s = if (sec in 1..59) sec * 60 else sec; if (s > 0) run(Segment(minOf(s, Recipe.MAX_SECONDS), 0, 120)) else null }
            // rice and water in the bowl, stirred gently in reverse
            "ricecooking" -> if (sec > 0) run(Segment(minOf(sec, Recipe.MAX_SECONDS), 1, 100, true)) else null
            // the built-in cleaning: warm soak, then short bursts
            "precleaning" -> { val s = if (sec > 0) sec else 120
                run(Segment(maxOf(s - 30, 30), 2, 60), Segment(10, 5), Segment(5, 2), Segment(10, 6), Segment(5, 2)) }
            // until hot: the runner keeps heating past the time until the bowl reaches the temperature
            "heatwater" -> if (t > 0) run(Segment(if (sec > 0) minOf(sec, Recipe.MAX_SECONDS) else 8 * 60, 1, t)) else null
            "heatingup" -> if (t > 0) run(Segment(if (sec > 0) minOf(sec, Recipe.MAX_SECONDS) else 8 * 60, ((p?.speed ?: 1.0).toInt()).coerceIn(1, 3), t)) else null
            "opencooking" -> if (sec > 0 && t > 0) {
                if (asked > 130) notes += "$where: $asked °C runs at 130 °C, this machine's maximum"
                if (sec > Recipe.MAX_SECONDS) notes += "$where: ${sec / 60} min shortened to 99 min"
                run(Segment(minOf(sec, Recipe.MAX_SECONDS), 1, t))
            } else null
            else -> null
        }
    }

    /** "Cozedura lenta: 6 h a 90 °C." (the step text when the source has none). */
    fun programLabel(k: String, sec: Int, t: Int, lang: String): String {
        val en = lang == "en"
        val name = programNames[k]?.let { if (en) it.first else it.second } ?: k
        val parts = listOfNotNull(sec.takeIf { it > 0 }?.let(::duration), t.takeIf { it > 0 }?.let { "$it °C" })
        return if (parts.isEmpty()) "$name." else "$name: " + parts.joinToString(if (en) " at " else " a ") + "."
    }

    /** "Monsieur Cuisine Smart program "Slow cooking": 4 h at 90 °C. …" in the recipe's language. */
    fun foreignProgram(mode: String, p: Setting?, lang: String): String {
        val en = lang == "en"
        val name = programNames[key(mode)]?.let { if (en) it.first else it.second } ?: mode.replace('_', ' ')
        val parts = ArrayList<String>()
        val sec = p?.time ?: 0
        if (sec > 0) parts += duration(sec)
        if ((p?.temperature ?: 0) > 0) parts += "${p!!.temperature} °C"
        p?.speed?.toInt()?.takeIf { it > 0 }?.let { parts += if (en) "speed $it" else "velocidade $it" }
        val settings = if (parts.isEmpty()) "" else ": " + parts.joinToString(if (en) " at " else " a ")
        return if (en) "Monsieur Cuisine Smart program \"$name\"$settings. This machine has no such program: do this step by hand."
        else "Programa \"$name\" do Monsieur Cuisine Smart$settings. Esta máquina não tem este programa: faça este passo à mão."
    }

    private fun duration(sec: Int): String {
        val h = sec / 3600; val m = sec % 3600 / 60; val s = sec % 60
        return listOfNotNull(h.takeIf { it > 0 }?.let { "$it h" }, m.takeIf { it > 0 }?.let { "$it min" }, s.takeIf { it > 0 }?.let { "$it s" }).joinToString(" ")
    }

    private fun groupTitle(name: String?): String? {
        val n = clean(name).trimEnd(':').trim()
        if (n.isEmpty()) return null
        return if (n == n.uppercase() && n != n.lowercase()) n.lowercase().replaceFirstChar { it.titlecase() } else n
    }

    private val entities = mapOf("&amp;" to "&", "&quot;" to "\"", "&#039;" to "'", "&#39;" to "'", "&lt;" to "<", "&gt;" to ">", "&nbsp;" to " ")
    fun clean(t: String?): String {
        var s = (t ?: "").replace("\r\n", "\n")
        entities.forEach { (k, v) -> s = s.replace(k, v) }
        s = s.trim()
        return if (s == "null" || s == "None") "" else s
    }
}
