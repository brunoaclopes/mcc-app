package mcc.app.ui

import mcc.recipe.RecipeIndex

/**
 * The recipes to show for a language filter ("pt", "en" or "all"). Most recipes exist in both languages
 * (ids ending -pt / -en); with "all" each one appears once, in [prefer] (the UI language) when it has it.
 */
fun RecipeIndex.visible(lang: String, prefer: String): List<RecipeIndex.Entry> = recipes.visible(lang, prefer)

fun List<RecipeIndex.Entry>.visible(lang: String, prefer: String): List<RecipeIndex.Entry> {
    val recipes = this
    if (lang != "all") return recipes.filter { it.language == lang }
    val base = Regex("-(pt|en)$")
    val seen = HashMap<String, Int>()
    val out = ArrayList<RecipeIndex.Entry>()
    for (e in recipes) {
        val key = e.id.replace(base, "")
        val i = seen[key]
        if (i == null) { seen[key] = out.size; out.add(e) }
        else if (e.language == prefer && out[i].language != prefer) out[i] = e
    }
    return out
}
