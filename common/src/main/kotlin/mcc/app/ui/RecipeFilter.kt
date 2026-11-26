package mcc.app.ui

import mcc.recipe.RecipeIndex

/**
 * The recipes to show, each one once, in the app language. Recipes exist in both languages (ids ending -pt / -en);
 * one that only has the other language still shows, in that language, rather than disappearing.
 */
fun RecipeIndex.visible(prefer: String): List<RecipeIndex.Entry> = recipes.visible(prefer)

fun List<RecipeIndex.Entry>.visible(prefer: String): List<RecipeIndex.Entry> {
    val base = Regex("-(pt|en)$")
    val seen = HashMap<String, Int>()
    val out = ArrayList<RecipeIndex.Entry>()
    for (e in this) {
        val key = e.id.replace(base, "")
        val i = seen[key]
        if (i == null) { seen[key] = out.size; out.add(e) }
        else if (e.language == prefer && out[i].language != prefer) out[i] = e
    }
    return out
}

/**
 * One name per category: the online recipes split some that the recipe repository keeps together
 * ("Vegano" and "Vegetariano" are "Vegetariano e vegano"; "One-Pot" and "All-in-One" are "One-Pot / All-in-One").
 */
fun canonicalTag(tag: String): String = TAG_ALIASES[tag] ?: tag

private val TAG_ALIASES = mapOf(
    "Vegano" to "Vegetariano e vegano", "Vegetariano" to "Vegetariano e vegano",
    "Vegan" to "Vegetarian & Vegan", "Vegetarian" to "Vegetarian & Vegan",
    "One-Pot" to "One-Pot / All-in-One", "All-in-One" to "One-Pot / All-in-One",
)
