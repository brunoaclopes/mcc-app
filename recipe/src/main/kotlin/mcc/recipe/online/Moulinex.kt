package mcc.recipe.online

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import mcc.recipe.Ingredient
import mcc.recipe.IngredientGroup
import mcc.recipe.Mode
import mcc.recipe.Recipe
import mcc.recipe.RecipeIndex
import mcc.recipe.Segment
import mcc.recipe.Source
import mcc.recipe.Step

/**
 * Moulinex Companion recipes (moulinex.pt), read live from the recipe API the Moulinex website itself
 * calls (Groupe SEB's "DCP" platform, with the key the website sends to every browser). Like [McOnline], nothing is
 * stored in this repository; the apps only cache what the user opens. The HTTP calls live in the apps' RecipeStore.
 *
 * The Companion is a cooking food processor of the same kind with other accessories and programs, so each step is
 * converted by what it does: the chopping and kneading blades become the blade (speeds 1-12 scaled to 1-10), the
 * stirring paddle ("misturador") becomes gentle reverse, the whisk the mixer attachment (speed 4 at most), the inner
 * steam basket the cooking pot and the steamer the deep steamer basket; the Companion programs become machine steps
 * that do the same (slow cook, soup, sauce, pastry, steam), and its timed alarms ("add the rice") split a step in two.
 * Ingredients with a weight become weigh steps. Whatever does not fit stays as text with a note.
 */
object Moulinex {
    const val API = "https://sebplatform.api.groupe-seb.com/common-api"
    /** The key the Moulinex website sends with every recipe call (in its page config, `dcp.apiKey`). */
    const val KEY = "dce684c2e06f345386fdf9326374b304"
    /** The Companion family's recipe domain. */
    const val DOMAIN = "PRO_COM"
    /** One page holds the whole catalogue: the API's pages overlap (about a fifth of the recipes never showed with pages of 500). */
    const val PAGE_SIZE = 2000
    const val FILE_PREFIX = "moulinex:"
    const val ID_PREFIX = "mlx-"
    /**
     * The catalogue the apps list: moulinex.pt has about 1400 Companion recipes with machine settings; moulinex.co.uk
     * only a few dozen old ones without (plain text), which [parseList] skips. English stays possible for later.
     */
    const val LANG = "pt"

    fun headers() = mapOf("ApiKey" to KEY, "Content-Type" to "application/json")
    /** Portuguese from moulinex.pt (about 1400 Companion recipes); English from moulinex.co.uk (a few dozen). */
    fun market(lang: String) = if (lang == "en") "GS_GB" else "GS_PT"
    fun apiLang(lang: String) = if (lang == "en") "en" else "pt"

    fun searchUrl(page: Int, lang: String) =
        "$API/v4/search/recipes?groupBy=topRecipe&q=&page=$page&size=$PAGE_SIZE&market=${market(lang)}&lang=${apiLang(lang)}"

    fun searchBody(lang: String): String = buildJsonObject {
        put("fieldList", buildJsonArray { listOf("title", "id", "durations.totalTime", "resourceMedias", "courses", "seoUrl").forEach { add(JsonPrimitive(it)) } })
        put("fieldFilters", buildJsonArray {
            fun f(field: String, v: String) = add(buildJsonObject { put("field", field); put("values", buildJsonArray { add(JsonPrimitive(v)) }) })
            f("lang.key", apiLang(lang)); f("market.key", market(lang)); f("status.key", "PUBLISHED"); f("domain.key", DOMAIN)
        })
    }.toString()

    fun detailUrl(id: String) = "$API/v3/recipes/PRO/$id?format=mobile"
    fun webUrl(lang: String, seo: String?) = seo?.let { (if (lang == "en") "https://www.moulinex.co.uk/recipes/detail" else "https://www.moulinex.pt/receitas/detail") + it }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ---- small JSON helpers ----
    private operator fun JsonElement?.get(k: String): JsonElement? = (this as? JsonObject)?.get(k)
    private val JsonElement?.str: String? get() = (this as? JsonPrimitive)?.contentOrNull
    private val JsonElement?.num: Double? get() = (this as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }
    private val JsonElement?.list: List<JsonElement> get() = (this as? JsonArray) ?: emptyList()

    // ---- list ----

    data class Page(val total: Int, val pages: Int, val entries: List<RecipeIndex.Entry>)

    /** One search page as index entries in [lang], tagged [tag] (and the course, in the recipe language). */
    fun parseList(text: String, lang: String, tag: String = "Moulinex"): Page {
        val d = json.parseToJsonElement(text)
        val page = d["page"] ?: throw IllegalStateException(d["message"].str?.take(200) ?: "no page")
        val entries = d["content"].list.mapNotNull { c ->
            val id = c["identifier"]["functionalId"].str ?: return@mapNotNull null
            // older recipes come with a path instead of a number; they would need another call to open, so skip them
            if (!id.all { it.isDigit() }) return@mapNotNull null
            // "food cooking" guides (steam 100-500 g of one food, one per cut) have no web page; they would bury the recipes
            if (c["seoUrl"].str.isNullOrBlank()) return@mapNotNull null
            val title = McOnline.clean(c["title"].str).ifBlank { return@mapNotNull null }
            val courses = c["courses"].list.mapNotNull { it.str?.let { k -> course(k, lang) } }
            RecipeIndex.Entry(
                id = "$ID_PREFIX$id-$lang", title = title, language = lang, tags = (listOf(tag) + courses).distinct().take(4),
                totalMinutes = c["durations"]["totalTime"].num?.toInt()?.takeIf { it > 0 }, image = cover(c["resourceMedias"]), file = "$FILE_PREFIX$id",
            )
        }
        return Page(page["totalElements"].num?.toInt() ?: 0, page["totalPages"].num?.toInt() ?: 0, entries)
    }

    private fun cover(medias: JsonElement?): String? {
        val m = medias.list.firstOrNull { it["isCover"].str == "true" } ?: medias.list.firstOrNull()
        return m["media"]["original"].str ?: m["media"]["thumbnail"].str
    }

    private val courses = mapOf(
        "MAIN_COURSE" to ("Main course" to "Prato principal"), "STARTER" to ("Starter" to "Entrada"), "DESSERT" to ("Dessert" to "Sobremesa"),
        "SIDE_DISH" to ("Side dish" to "Acompanhamento"), "SNACK" to ("Snack" to "Petisco"), "SAUCE" to ("Sauce" to "Molho"),
        "SOUP" to ("Soup" to "Sopa"), "SMOOTHIE_JUICE" to ("Smoothies and juices" to "Batidos e sumos"), "COCKTAIL" to ("Cocktail" to "Cocktail"),
        "BREAD_BRIOCHE" to ("Bread" to "Pão"), "BREAKFAST" to ("Breakfast" to "Pequeno-almoço"), "CAKE" to ("Cake" to "Bolo"),
        "BASIC_PREPARATION" to ("Basics" to "Bases"), "MILKSHAKE" to ("Milkshake" to "Batido"),
    )
    private fun course(key: String, lang: String) = courses[key]?.let { if (lang == "en") it.first else it.second }

    // ---- conversion ----

    /** Companion accessories, by the platform's kitchenware keys. */
    private enum class Tool { BLADE, KNEAD_BLADE, PADDLE, WHISK, STEAM_BASKET, STEAMER, MINI_BOWL, AIR_FRYER, PASTRY_KIT }
    private val stepOnly = setOf(Tool.AIR_FRYER, Tool.PASTRY_KIT, Tool.MINI_BOWL)
    private val tools = mapOf(
        "KITCHENWARE_140" to Tool.BLADE, "KITCHENWARE_142" to Tool.KNEAD_BLADE, "KITCHENWARE_141" to Tool.PADDLE, "KITCHENWARE_6" to Tool.WHISK,
        "KITCHENWARE_82" to Tool.STEAM_BASKET, "KITCHENWARE_10001" to Tool.STEAMER, "KITCHENWARE_10010" to Tool.MINI_BOWL,
        "KITCHENWARE_10280" to Tool.AIR_FRYER, "KITCHENWARE_10022" to Tool.PASTRY_KIT,
    )

    /** Companion speed 1-12 on the blade, as this machine's 1-10 (by what it does: chop, blend, mix). */
    fun bladeSpeed(v: Int) = intArrayOf(0, 1, 1, 2, 3, 4, 4, 5, 6, 7, 8, 9, 10)[v.coerceIn(0, 12)]

    private fun t(lang: String, en: String, pt: String) = if (lang == "en") en else pt

    fun convert(text: String, lang: String): Recipe {
        val d = json.parseToJsonElement(text)
        if (d["fid"] == null) throw IllegalStateException(d["message"].str?.take(200) ?: "no recipe")
        val id = d["fid"]["functionalId"].str ?: throw IllegalStateException("no id")
        val notes = ArrayList<String>()
        val steps = ArrayList<Step>()
        var tool = Tool.BLADE
        var steamSetUp: Tool? = null
        if (d["steps"].list.any { s -> s["kitchenwares"].list.any { tools[it["key"].str] == Tool.AIR_FRYER } })
            notes += t(lang, "Needs the Companion air fryer extension: those steps stay as text (use your oven instead).",
                "Precisa da extensão Air Fryer da Companion: esses passos ficam como texto (use o forno).")
        d["steps"].list.forEachIndexed { n, s ->
            val where = "step ${n + 1}"
            // the blade, paddle and whisk stay on until another goes on; the air fryer, pastry kit and mini bowl only for their step
            val own = s["kitchenwares"].list.mapNotNull { tools[it["key"].str] }
            own.firstOrNull { it !in stepOnly }?.let { tool = it }
            val stepTool = own.firstOrNull { it in stepOnly && (it != Tool.MINI_BOWL || own.size == 1) } ?: tool
            val ops = s["sequences"].list.firstOrNull { it["applianceGroup"]["key"].str == "APPLIANCE_GROUP_16" }?.get("operations").list
                .ifEmpty { s["sequences"].list.firstOrNull()?.get("operations").list }
            val desc = cleanText(s["applicationDescription"].str, lang)
            // what goes into the bowl by weight, before the machine step
            val weighed = if (ops.isNotEmpty()) s["ingredients"].list.mapNotNull { i -> weigh(i) } else emptyList()
            steps += weighed
            val converted = convertOps(ops, stepTool, desc, lang, notes, where, waterWeighed = weighed.any { water.containsMatchIn(it.text) }, steamReady = steamSetUp == stepTool)
            steamSetUp = if (converted.any { it is Step.Machine && it.mode == Mode.STEAM }) stepTool else if (converted.any { it is Step.Machine }) null else steamSetUp
            steps += converted
        }
        if (steps.isEmpty()) steps += d["instructions"].list.mapNotNull { it.str?.let { s -> cleanText(s, lang) }?.takeIf { s -> s.isNotBlank() }?.let { s -> Step.Instruction(s) } }
        if (steps.isEmpty()) steps += Step.Instruction(McOnline.clean(d["title"].str))

        val ingredients = d["ingredients"].list.mapNotNull { i -> McOnline.clean(i["applicationDescription"].str).trim().ifBlank { null }?.let { Ingredient(it) } }
        val y = d["yield"]
        val servings = listOfNotNull(y["quantityDisplay"].str ?: y["quantity"].num?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() },
            y["unit"]["pluralName"].str ?: y["unit"]["name"].str).joinToString(" ").ifBlank { null }
        val difficulty = d["difficulty"].num?.toInt()?.let { if (it <= 25) "easy" else if (it <= 50) "medium" else "hard" }
        val tips = d["tips"].list.mapNotNull { it.str ?: it["description"].str }.map { cleanText(it, lang) }.filter { it.isNotBlank() }
        val tag = "Moulinex"
        return Recipe(
            format = Recipe.FORMAT, id = "$ID_PREFIX$id-$lang", title = McOnline.clean(d["title"].str).ifBlank { "Moulinex $id" }, language = lang,
            servings = servings, activeMinutes = d["durations"]["prepTime"].num?.toInt()?.takeIf { it > 0 },
            totalMinutes = d["durations"]["totalTime"].num?.toInt()?.takeIf { it > 0 }, difficulty = difficulty,
            tags = (listOf(tag) + d["courses"].list.mapNotNull { (it["key"].str ?: it.str)?.let { k -> course(k, lang) } }).distinct().take(4),
            image = cover(d["resourceMedias"]), source = Source("moulinex", id, webUrl(lang, d["seoUrl"].str)),
            ingredients = if (ingredients.isEmpty()) emptyList() else listOf(IngredientGroup(null, ingredients)),
            steps = steps, notes = listOf(convertedNote(lang)) + tips + notes,
        )
    }

    fun convertedNote(lang: String) = t(lang,
        "Converted from a Moulinex Companion recipe: check each step before you start it.",
        "Convertida de uma receita Moulinex Companion: verifique cada passo antes de o iniciar.")

    private val massUnits = setOf("UNIT_27", "UNIT_56", "UNIT_35", "UNIT_14", "UNIT_15", "UNIT_13", "UNIT_34")

    /** An ingredient given by weight or volume (g, kg, ml, cl, dl, l), as a weigh step; spoons, pinches and pieces are not weighed. */
    private fun weigh(i: JsonElement): Step.Weigh? {
        if (i["unit"]["key"].str !in massUnits) return null
        val g = (i["weight"]["quantity"].num ?: return null).toInt()
        if (g !in 5..5000) return null
        val text = McOnline.clean(i["applicationDescription"].str).trim().ifBlank { return null }
        return Step.Weigh(text, g)
    }

    private fun params(op: JsonElement): Map<String, String?> =
        op["parameters"].list.associate { p -> (p["commonKey"].str ?: p["type"].str ?: "") to p["value"].str }

    /** Companion programs by their key (the names are translated: "Vapor elevado P2" is Steam P2). */
    private val programs = mapOf(
        "PROGRAM_000001" to "Sauce P", "PROGRAM_000002" to "Soup P1", "PROGRAM_000003" to "Soup P2", "PROGRAM_000004" to "Slow cook P1",
        "PROGRAM_000005" to "Slow cook P2", "PROGRAM_000006" to "Slow cook P3", "PROGRAM_000007" to "Steam P1", "PROGRAM_000008" to "Steam P2",
        "PROGRAM_000009" to "Pastry P1", "PROGRAM_000010" to "Pastry P2", "PROGRAM_000011" to "Pastry P3", "PROGRAM_000012" to "Dessert P",
        "PROGRAM_000013" to "Manual P", "PROGRAM_000014" to "Alert P", "PROGRAM_000015" to "Timer",
    )
    /** By key, else (the Companion NEO has its own keys) by the name in any of the site's languages. */
    private fun programName(op: JsonElement): String {
        programs[op["program"]["key"].str]?.let { return it }
        val raw = op["program"]["name"].str ?: return ""
        val n = mcc.recipe.Accessories.fold(raw)
        val digit = Regex("p\\s*([123])\\b").find(n)?.groupValues?.get(1)
        return when {
            Regex("manual").containsMatchIn(n) -> "Manual P"
            Regex("alert|alarm").containsMatchIn(n) -> "Alert P"
            Regex("timer|temporiz|minuteur").containsMatchIn(n) -> "Timer"
            Regex("vapor|steam|vapeur").containsMatchIn(n) -> if (digit == "2" || Regex("elevad|high|intens|fort").containsMatchIn(n)) "Steam P2" else "Steam P1"
            Regex("sopa|soup|soupe|velout").containsMatchIn(n) -> if (digit == "2") "Soup P2" else "Soup P1"
            Regex("molho|sauce").containsMatchIn(n) -> "Sauce P"
            Regex("lent|slow|mijot").containsMatchIn(n) -> "Slow cook P${digit ?: "2"}"
            Regex("pastel|pastry|patiss|massa|dough|pate").containsMatchIn(n) -> "Pastry P${digit ?: "1"}"
            Regex("sobremesa|dessert").containsMatchIn(n) -> "Dessert P"
            else -> raw
        }
    }

    private val water = Regex("(?i)\\b(água|water)\\b")

    private fun convertOps(ops: List<JsonElement>, tool: Tool, desc: String, lang: String, notes: MutableList<String>, where: String,
                           waterWeighed: Boolean = false, steamReady: Boolean = false): List<Step> {
        if (ops.isEmpty()) return if (desc.isNotBlank()) listOf(Step.Instruction(desc)) else emptyList()
        val main = ops.firstOrNull { !programName(it).startsWith("Alert") }
        // alarms during the step: "COUNTDOWN" is the time left when it rings
        val alerts = ops.filter { programName(it).startsWith("Alert") }
            .mapNotNull { a -> val p = params(a); p["COUNTDOWN"]?.toIntOrNull()?.let { it to cleanText(p["ALERT_TEXT"], lang) } }
            .sortedByDescending { it.first }
        if (main == null) return listOfNotNull(desc.takeIf { it.isNotBlank() }?.let { Step.Instruction(it) }) + alerts.map { Step.Instruction(it.second) }
        val name = programName(main)
        val p = params(main)
        val sec = p["DURATION"]?.toIntOrNull() ?: 0
        val asked = p["TEMPERATURE"]?.toIntOrNull() ?: 0
        val temp = McOnline.level(asked)
        if (asked > 0 && temp != asked) notes += "$where: $asked °C runs at the machine level $temp °C"
        val speedRaw = p["SPEED"]
        val v = speedRaw?.removePrefix("V")?.toIntOrNull()
        fun heatCap(s: Int) = if (temp > 0 && s > 3) { notes += "$where: speed $s while heating runs at speed 3"; 3 } else s

        if (tool == Tool.AIR_FRYER) {
            notes += "$where: Companion air fryer extension; kept as text"
            return listOfNotNull(desc.takeIf { it.isNotBlank() }?.let { Step.Instruction(it) }, Step.Instruction(foreign(name, sec, asked, lang)))
        }

        // one machine step for the whole time, or split where the alarms ring
        fun machine(mode: Mode, make: (Int) -> List<Segment>): List<Step> {
            if (sec <= 0) return listOf(Step.Instruction(desc.ifBlank { foreign(name, sec, asked, lang) }))
            val cuts = alerts.map { it.first }.filter { it in 1 until sec }
            if (cuts.isEmpty()) return listOf(Step.Machine(desc, mode, make(sec)))
            val out = ArrayList<Step>()
            var left = sec
            for ((cut, msg) in alerts.filter { it.first in 1 until sec }) {
                if (left - cut > 0) out += Step.Machine(if (out.isEmpty()) desc else "", mode, make(left - cut))
                out += Step.Instruction(msg)
                left = cut
            }
            out += Step.Machine("", mode, make(left))
            return out
        }
        fun cap(s: Int) = minOf(s, Recipe.MAX_SECONDS)

        return when {
            // the dessert program (custards, creams) runs like a sauce: heat with the given speed
            name.startsWith("Manual") || name.startsWith("Sauce") || name.startsWith("Dessert") -> when {
                speedRaw == "PULSE" -> {
                    // short bursts, as the Companion's pulse
                    val n = (sec / 6).coerceIn(2, 10)
                    listOf(Step.Machine(desc, Mode.TURBO, List(n) { Segment(1) }))
                }
                speedRaw == "TURBO" -> machine(Mode.RUN) { s -> listOf(Segment(minOf(s, 15 * 60), 10)) }
                else -> {
                    val (speed, reverse) = when (tool) {
                        // the stirring paddle: the blade's blunt side in reverse, gently
                        Tool.PADDLE -> (when { (v ?: 1) <= 3 -> 1; (v ?: 1) <= 6 -> 2; else -> 3 }) to true
                        // the whisk: the mixer attachment, speed 4 at most
                        Tool.WHISK -> (when { (v ?: 4) <= 3 -> 2; (v ?: 4) <= 6 -> 3; else -> 4 }) to false
                        else -> bladeSpeed(v ?: if (name.startsWith("Manual")) 0 else 4) to false
                    }
                    val s = heatCap(speed)
                    if (s == 0 && temp == 0) listOfNotNull(desc.takeIf { it.isNotBlank() }?.let { Step.Instruction(it) })
                    else machine(Mode.RUN) { left -> listOf(Segment(cap(left), s, temp, reverse && s <= 3)) }
                }
            }
            // P1 browns at 130 °C stirring now and then; P2 and P3 simmer, stirring gently
            name.startsWith("Slow cook P1") -> machine(Mode.ROAST) { left -> listOf(Segment(cap(left), 0, if (temp > 0) temp else 130)) }
            name.startsWith("Slow cook") -> machine(Mode.RUN.also {
                val hot = (if (temp > 0) temp else 95) > Recipe.LONG_MAX_TEMP_C
                if (hot && sec > Recipe.MAX_SECONDS) notes += "$where: ${sec / 60} min above 95 °C shortened to 99 min"
            }) { left ->
                val t2 = if (temp > 0) temp else 95
                listOf(Segment(minOf(left, if (t2 <= Recipe.LONG_MAX_TEMP_C) Recipe.MAX_LONG_SECONDS else Recipe.MAX_SECONDS), 1, t2, true))
            }
            name.startsWith("Soup") -> {
                val cook = machine(Mode.RUN) { left -> listOf(Segment(cap(left), 1, if (temp > 0) temp else 100)) }
                // Soup P1 is a smooth soup: blend at the end, rising
                if (name.startsWith("Soup P1")) cook + Step.Machine(t(lang, "Blend.", "Triturar."), Mode.RUN, listOf(Segment(10, 5), Segment(10, 7), Segment(40, 9)))
                else cook.also { notes += "$where: $name kept chunky (no blending)" }
            }
            // bread and pastry doughs: the knead program; cake batters: mixed at speed 4
            name.startsWith("Pastry P1") || name.startsWith("Pastry P2") -> machine(Mode.KNEAD) { left -> listOf(Segment(minOf(left, 6 * 60))) }
            name.startsWith("Pastry") -> machine(Mode.RUN) { left -> listOf(Segment(cap(left), 4)) }
            // steaming: in the cooking pot or the steamer baskets, on the steaming program (60 min at most)
            name.startsWith("Steam") -> {
                if (sec > 3600) notes += "$where: ${sec / 60} min of steaming shortened to 60 min"
                // no steam accessory named: P2 steams high (the steamer on top), P1 low (the basket in the bowl)
                val deep = tool == Tool.STEAMER || (tool != Tool.STEAM_BASKET && name.endsWith("P2"))
                val waterText = if (waterWeighed) "" else t(lang, if (deep) "At least 1000 g of water in the bowl. " else "At least 500 g of water in the bowl. ",
                    if (deep) "Pelo menos 1000 g de água na taça. " else "Pelo menos 500 g de água na taça. ")
                val pre = if (steamReady) emptyList() else listOf(Step.Instruction(waterText + t(lang,
                    if (deep) "Lock the deep steamer basket onto the bowl in place of the lid and put the steamer lid on."
                    else "Put the cooking pot in the bowl and close the lid with the measuring beaker.",
                    if (deep) "Encaixe o acessório para vapor fundo na taça, no lugar da tampa, e coloque a tampa do vapor."
                    else "Coloque o acessório de cozer na taça e feche a tampa com o copo de medição.")))
                pre + machine(Mode.STEAM) { left -> listOf(Segment(minOf(left, 3600), 0, 120)) }
            }
            name.startsWith("Timer") -> listOfNotNull(desc.takeIf { it.isNotBlank() }?.let { Step.Instruction(it) },
                Step.Instruction(t(lang, "Wait ${minutes(sec)}.", "Aguarde ${minutes(sec)}.")))
            else -> {
                notes += "$where: Companion program \"$name\" kept as text"
                listOfNotNull(desc.takeIf { it.isNotBlank() }?.let { Step.Instruction(it) }, Step.Instruction(foreign(name, sec, asked, lang)))
            }
        }
    }

    private fun minutes(sec: Int) = if (sec >= 3600 && sec % 3600 == 0) "${sec / 3600} h" else if (sec >= 60) "${sec / 60} min" else "$sec s"

    private fun foreign(name: String, sec: Int, temp: Int, lang: String): String {
        val parts = listOfNotNull(sec.takeIf { it > 0 }?.let { minutes(it) }, temp.takeIf { it > 0 }?.let { "$it °C" })
        val settings = if (parts.isEmpty()) "" else ": " + parts.joinToString(if (lang == "en") " at " else " a ")
        return t(lang, "Moulinex Companion program \"$name\"$settings. This machine has no such program: do this step by hand.",
            "Programa \"$name\" da Moulinex Companion$settings. Esta máquina não tem este programa: faça este passo à mão.")
    }

    // ---- text: the Companion's words for this machine's ----

    private val whisk = Regex("(?i)batedor|whisk")
    private val swapPt = Regex("(?i)\\b(substitua|troque|mude)\\s+(?:o|a)\\s+(.+?)\\s+(?:pel[oa]|por)\\s+(?:o |a )?(.+?)(?=[,.;!]|\\s+e\\s+|$)")
    private val swapEn = Regex("(?i)\\b(replace|swap|change)\\s+the\\s+(.+?)\\s+(?:with|for|by)\\s+the\\s+(.+?)(?=[,.;!]|\\s+and\\s+|$)")
    // putting in or taking out the Companion's stirring paddle: nothing to do on this machine (it stirs in reverse)
    private val paddlePt = Regex("(?i)\\b(coloque|encaixe|insira|retire|tire)\\s+o\\s+(?:acessório\\s+)?misturador[^,.;!]*")
    private val paddleEn = Regex("(?i)\\b(fit|insert|attach|put in|remove)\\s+the\\s+(?:mixing paddle|mixer)(?: accessory| attachment)?[^,.;!]*")

    private val replacePt = listOf(
        "\\s*e prima (novamente |de novo )?(em )?(o botão )?[\"'“”‘’]?start[\"'“”‘’]?" to "",
        "prima (novamente |de novo )?(em )?(o botão )?[\"'“”‘’]?start[\"'“”‘’]?" to "inicie",
        "(a )?tampa reguladora de vapor" to "o copo de medição",
        // the Companion's metal shaft holds its accessories; this machine's blade is fixed
        "\\s*(equipad[oa] )?com o eixo metálico( e)?" to "",
        "\\s*equipad[oa] com a lâminar?( picadora| (para|de) amassar ?/ ?triturar)?( ultrablade)?" to "",
        "lâminar? picadorar?( ultrablade)?|lâminar? (para|de) amassar ?/ ?triturar|lâminar? ultrablade" to "lâmina",
        "(acessório )?batedor" to "acessório batedor",
        "cesto (de|do) (cozer a |cozedura a )?vapor|cesto de cozedura" to "acessório de cozer",
        "\\b([nd])a panela de vapor" to "$1o acessório para vapor fundo",
        "\\ba panela de vapor" to "o acessório para vapor fundo",
        "panela de vapor" to "acessório para vapor fundo",
        "\\s*(com |e )?(o )?acessório misturador" to "",
        "mini taça( da companion)?" to "taça",
        "taça do robot|taça da companion" to "taça",
        // "o robot" is masculine, "a máquina" feminine
        "\\bno (robot|companion)\\b" to "na máquina", "\\bdo (robot|companion)\\b" to "da máquina",
        "\\bao (robot|companion)\\b" to "à máquina", "\\bpelo (robot|companion)\\b" to "pela máquina",
        "\\bo (robot|companion)\\b" to "a máquina", "\\bseu (robot|companion)\\b" to "sua máquina",
        "\\brobot\\b|\\bcompanion\\b" to "máquina",
    ).map { (a, b) -> Regex("(?i)$a") to b }
    private val replaceEn = listOf(
        "\\s*(and )?press (the )?[\"'“”‘’]?start[\"'“”‘’]?( button)?" to "",
        "press (the )?[\"'“”‘’]?start[\"'“”‘’]?( button)?" to "start",
        "(the )?steam(-| )?(regulator|release)( cap| lid)?" to "the measuring beaker",
        "\\s*(fitted |equipped )?with the metal (shaft|spindle)( and)?" to "",
        "\\s*(fitted|equipped) with the (ultrablade( knife)?|chopping blade|kneading/crushing blade|kneading blade)" to "",
        "ultrablade( knife)?|chopping blade|kneading/crushing blade|kneading blade" to "blade",
        "(inner |internal )?steam(ing)? basket" to "cooking pot",
        "\\bsteamer\\b" to "deep steamer basket",
        "\\bwhisk( accessory| attachment)?\\b" to "mixer attachment",
        "food processor bowl|processor bowl|\\bcompanion bowl\\b" to "bowl",
        "\\bcompanion\\b" to "machine",
    ).map { (a, b) -> Regex("(?i)$a") to b }

    fun cleanText(raw: String?, lang: String): String {
        var s = McOnline.clean(raw).replace("\r", "").replace('\u00A0', ' ').trim()
        if (s.isEmpty()) return s
        val en = lang == "en"
        s = (if (en) paddleEn else paddlePt).replace(s, "")
        // accessory swaps: to the whisk = put in the mixer attachment, from it = take it out, blade to blade = nothing
        s = (if (en) swapEn else swapPt).replace(s) { m ->
            val from = m.groupValues[2]; val to = m.groupValues[3]
            when {
                whisk.containsMatchIn(to) -> t(lang, "put in the mixer attachment", "coloque o acessório batedor")
                whisk.containsMatchIn(from) -> t(lang, "remove the mixer attachment", "retire o acessório batedor")
                else -> ""
            }
        }
        for ((re, to) in if (en) replaceEn else replacePt) s = re.replace(s, to)
        // tidy what the removals left: ", , ", ". e deite", "acessório acessório", spaces before punctuation
        s = s.replace(Regex("(?i)\\bacessório (o )?acessório\\b"), "acessório").replace(Regex("(?i)\\b(o|a) o copo"), "o copo")
            .replace(Regex("(?m)(^|[.!?]\\s*)[,;]?\\s*(?:e|and)?\\s*,\\s*"), "$1")
            .replace(Regex("(?i)(^|[.!?]\\s+)(?:e|and)\\s+"), "$1")
            .replace(Regex("\\s+([.,;!?])"), "$1").replace(Regex("([.,;!?])\\1+"), "$1").replace(Regex("^[\\s.,;]+"), "")
            .replace(Regex("[ \\t]{2,}"), " ").trim()
        // sentence starts in upper case again
        s = Regex("(^|[.!?]\\s+)(\\p{Ll})").replace(s) { it.groupValues[1] + it.groupValues[2].uppercase() }
        return s
    }
}
