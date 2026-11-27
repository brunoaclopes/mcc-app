package mcc.app

import android.content.SharedPreferences

/**
 * Favourite recipes, by [recipeKey] (so both languages of a recipe count), most recently added first. Kept per device
 * in the app's preferences; programs can be favourites too.
 */
class Favourites(private val sp: SharedPreferences) {
    val keys: List<String> get() = (sp.getString(KEY, "") ?: "").split(';').filter { it.isNotBlank() }

    fun has(id: String) = recipeKey(id) in keys

    /** Adds or removes the recipe; returns whether it is a favourite now. */
    fun toggle(id: String): Boolean {
        val k = recipeKey(id)
        val now = k !in keys
        save(if (now) listOf(k) + keys else keys - k)
        return now
    }

    fun add(id: String) { val k = recipeKey(id); if (k !in keys) save(listOf(k) + keys) }

    private fun save(list: List<String>) = sp.edit().putString(KEY, list.take(200).joinToString(";")).apply()

    private companion object { const val KEY = "favourites" }
}
