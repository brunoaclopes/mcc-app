package mcc.app

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import mcc.recipe.Recipe
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sends a finished recipe (both languages) to the recipe server's upload service on the NAS, which checks it,
 * commits it to the recipe repository and redeploys the server. Contract (docs/RECIPE-UPLOAD.md):
 *
 *     POST <address>/v1/recipes   Authorization: Bearer <token>
 *     {"key": "web-…", "recipes": [<pt recipe>, <en recipe>]}
 *     201 {"key": "…", "ids": ["…-pt", "…-en"]}   409 key taken   422 {"error": "…"}   401 bad token
 *
 * Call from a background thread.
 */
class RecipePublisher(private val address: String, private val token: String) {
    class Taken : IOException("key already exists")
    class Rejected(msg: String) : IOException(msg)

    val configured get() = address.isNotBlank()

    /** Publishes [recipes] (one per language, same structure) under a free key based on [base]; returns the key used. */
    fun publish(base: String, recipes: List<Recipe>): String {
        for (n in 1..9) {
            val key = if (n == 1) base else "$base-$n"
            try { post(key, recipes.map { forRepo(it, key) }); return key } catch (_: Taken) { }
        }
        throw Rejected("no free name for $base")
    }

    private fun post(key: String, recipes: List<Recipe>) {
        val body = buildJsonObject {
            put("key", key)
            put("recipes", JsonArray(recipes.map { Recipe.json.encodeToJsonElement(Recipe.serializer(), it) }))
        }.toString()
        val c = URL(address.trim().trimEnd('/') + "/v1/recipes").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.connectTimeout = 10_000; c.readTimeout = 120_000; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("User-Agent", "mcc-app")
        token.trim().takeIf { it.isNotBlank() }?.let { c.setRequestProperty("Authorization", "Bearer $it") }
        try {
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            val text = runCatching { (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull() ?: ""
            when {
                code in 200..299 -> return
                code == 409 -> throw Taken()
                else -> {
                    val err = runCatching { json.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
                    throw Rejected("HTTP $code" + ((err ?: text.take(200)).takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""))
                }
            }
        } finally { c.disconnect() }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Repository key for a recipe title, in the repo's style for recipes taken from web pages: web-<slug>. */
        fun keyFor(title: String): String {
            val slug = java.text.Normalizer.normalize(title.lowercase(), java.text.Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), "-").trim('-').take(50).trim('-').ifEmpty { "recipe" }
            return "web-$slug"
        }

        /** The repository copy: id <key>-<language>, without this device's "My recipes" tag. */
        fun forRepo(r: Recipe, key: String): Recipe {
            val mine = setOf(mcc.app.i18n.En.tagMine, mcc.app.i18n.Pt.tagMine)
            return r.copy(id = "$key-${r.language}", tags = r.tags.filter { it !in mine })
        }
    }
}
