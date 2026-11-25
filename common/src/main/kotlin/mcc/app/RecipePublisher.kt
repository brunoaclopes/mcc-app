package mcc.app

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
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
 *     POST <address>/v1/recipes   Authorization: Bearer <token>   Idempotency-Key: <one per recipe>
 *     {"key": "web-…", "recipes": [<pt recipe>, <en recipe>]}
 *     201 {"key": "…", "ids": ["…-pt", "…-en"]}   409 key taken   422 {"error": "…"}   401 bad token
 *
 * Call from a background thread.
 */
class RecipePublisher(private val address: String, private val token: String) {
    class Taken : IOException("key already exists")
    class Rejected(msg: String) : IOException(msg)
    /** The service turned the video down (422, 504) or the token (401): [message] is meant for the user, as is. */
    class VideoProblem(msg: String, val badToken: Boolean = false) : Exception(msg)

    val configured get() = address.isNotBlank()

    /**
     * Publishes [recipes] (one per language, same structure) under a free key based on [base]; returns the key used.
     * [uploadKey] stays the same for every try of the same recipe: when an earlier try was added but its answer got
     * lost (a timeout), the service answers with that key instead of adding a copy as "<base>-2".
     */
    fun publish(base: String, recipes: List<Recipe>, uploadKey: String? = null): String {
        for (n in 1..9) {
            val key = if (n == 1) base else "$base-$n"
            try { return post(key, recipes.map { forRepo(it, key) }, uploadKey) } catch (_: Taken) { }
        }
        throw Rejected("no free name for $base")
    }

    /**
     * A video's title, description and transcript (subtitles, or Whisper when it has none), for the AI import:
     * `POST <address>/v1/transcript {"url": …}`. Whisper can take minutes; the service answers within 600 s, with
     * "partial": true when it is still finishing in the background, so a partial transcript is asked for again (up to
     * [retries] times) to get the rest. Throws [VideoProblem] for answers meant for the user (401, 422, 504) and
     * IOException when the service can't be reached or fails otherwise (the caller then reads the page itself).
     */
    fun transcript(url: String, retries: Int = 2): AiClient.Video {
        var v = transcriptOnce(url)
        repeat(retries) { if (v.partial) v = runCatching { transcriptOnce(url) }.getOrElse { return v } }
        return v
    }

    private fun transcriptOnce(url: String): AiClient.Video {
        val reply = try {
            request("/v1/transcript", buildJsonObject { put("url", url) }.toString(), 660_000)
        } catch (e: Failed) {
            when (e.code) {
                401 -> throw VideoProblem(e.error ?: "wrong token", badToken = true)
                422, 504 -> throw VideoProblem(e.error ?: "HTTP ${e.code}")
                else -> throw IOException(e.message)
            }
        }
        val o = json.parseToJsonElement(reply).jsonObject
        fun s(k: String) = o[k]?.jsonPrimitive?.contentOrNull.orEmpty()
        return AiClient.Video(s("title"), s("author"), s("description"), s("transcript"), s("source"), s("thumbnail").ifBlank { null },
            partial = o["partial"]?.jsonPrimitive?.booleanOrNull == true)
    }

    /** A non-2xx answer other than 409: [error] is the service's message. */
    private class Failed(val code: Int, val error: String?, text: String) :
        IOException("HTTP $code" + ((error ?: text.take(200)).takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""))

    /** Returns the key the service stored it under (an earlier try's key, when [uploadKey] was seen before). */
    private fun post(key: String, recipes: List<Recipe>, uploadKey: String?): String {
        val body = buildJsonObject {
            put("key", key)
            put("recipes", JsonArray(recipes.map { Recipe.json.encodeToJsonElement(Recipe.serializer(), it) }))
        }.toString()
        val reply = try { request("/v1/recipes", body, 120_000, uploadKey) } catch (e: Failed) { throw Rejected(e.message ?: "HTTP ${e.code}") }
        return runCatching { json.parseToJsonElement(reply).jsonObject["key"]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: key
    }

    /** POSTs [body]; returns the reply on 2xx, throws [Taken] on 409 and [Failed] (with the service's message) otherwise. */
    private fun request(path: String, body: String, readTimeout: Int, uploadKey: String? = null): String {
        val c = URL(address.trim().trimEnd('/') + path).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.connectTimeout = 10_000; c.readTimeout = readTimeout; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("User-Agent", "mcc-app")
        token.trim().takeIf { it.isNotBlank() }?.let { c.setRequestProperty("Authorization", "Bearer $it") }
        uploadKey?.let { c.setRequestProperty("Idempotency-Key", it) }
        try {
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            val text = runCatching { (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull() ?: ""
            when {
                code in 200..299 -> return text
                code == 409 -> throw Taken()
                else -> {
                    val err = runCatching { json.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
                    throw Failed(code, err, text)
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
