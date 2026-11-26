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
 * the list of official recipes for this machine family, and one recipe at a time when it is opened. Nothing
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

    fun listUrl(page: Int): String =
        "$API/api/v1/recipes/page/$page/size/$PAGE_SIZE?filters%5Bsource%5D%5B%5D=official" +
            DEVICES.joinToString("") { "&filters%5BdeviceTypes%5D%5B%5D=$it" }

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

    /** Index entries for one list page, official recipes only, in [lang]. */
    fun entries(page: ListData, lang: String): List<RecipeIndex.Entry> = page.recipes.filter { it.source == null || it.source == "official" }.map { s ->
        RecipeIndex.Entry(
            id = "$ID_PREFIX${s.id}-$lang", title = clean(s.name), language = lang,
            tags = s.categories.mapNotNull { it.name?.let(::clean) }.filter { it.isNotBlank() }.distinct().take(4),
            totalMinutes = s.duration?.takeIf { it > 0 }, image = imageUrl(s.thumbnail?.landscape), file = "$FILE_PREFIX${s.id}",
        )
    }

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

    private val modeOf = mapOf("customized" to Mode.RUN, "ramp" to Mode.RUN, "turbo" to Mode.TURBO, "kneading" to Mode.KNEAD,
        "roasting" to Mode.ROAST, "steaming" to Mode.STEAM)
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
        val mode = modeOf[ds.mode] ?: run {
            // a program this machine does not have (slow cooking, sous-vide, an accessory…): say what it was,
            // so the step does not silently disappear, and leave it to the cook
            notes += "$where: ${ds.mode} is not a program of this machine; kept as text"
            return asText() + Step.Instruction(foreignProgram(ds.mode, params.firstOrNull(), lang))
        }
        var reverse = ds.reverse == true
        val segs = ArrayList<Segment>()
        for (p in params) {
            var sec = p.time ?: 0
            val speed = (p.speed ?: 0.0).toInt()
            val temp = p.temperature ?: 0
            if (sec <= 0) continue
            if (temp !in TEMPS_C) { notes += "$where: $temp °C is not a machine level; kept as text"; return asText() }
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
        "slow_cooking" to ("Slow cooking" to "Cozedura lenta"), "sous_vide" to ("Sous-vide" to "Sous-vide"),
        "smoothie" to ("Smoothie" to "Smoothie"), "food_processor" to ("Food processor" to "Processador de alimentos"),
        "fermenting" to ("Fermenting" to "Fermentar"), "egg_boiler" to ("Egg boiler" to "Cozer ovos"),
        "cleaning" to ("Cleaning" to "Limpeza"), "rice_cooker" to ("Rice cooker" to "Cozer arroz"),
    )

    /** "Monsieur Cuisine Smart program "Slow cooking": 4 h at 90 °C. …" in the recipe's language. */
    fun foreignProgram(mode: String, p: Setting?, lang: String): String {
        val en = lang == "en"
        val name = programNames[mode]?.let { if (en) it.first else it.second } ?: mode.replace('_', ' ')
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
