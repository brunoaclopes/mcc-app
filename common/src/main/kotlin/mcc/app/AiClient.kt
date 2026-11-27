package mcc.app

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import mcc.recipe.Recipe
import mcc.recipe.RecipeFit
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to an OpenAI-compatible chat API (OpenAI, or a server on your own network such as LocalAI, Ollama,
 * vLLM or LiteLLM). It only ever produces recipe text: it has no way to start the machine. Every recipe it
 * makes is checked by [Recipe.parse] and fitted to the machine limits by [RecipeFit], and machine steps still
 * need the user's hold on Start. Call from a background thread.
 */
class AiClient(private val address: String, private val model: String, private val key: String) {
    class AiException(msg: String) : Exception(msg)

    /**
     * [prompt] is what the AI was given (the source), kept so the user can ask for changes with [revise]. For a video
     * transcribed by the upload service, [videoSource] is its "source" (subtitles, whisper, none) and [partial] whether
     * the transcript covered only the start.
     */
    data class Import(val recipe: Recipe, val changes: List<String>, val prompt: String = "",
                      val videoSource: String? = null, val partial: Boolean = false)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val configured get() = address.isNotBlank() && model.isNotBlank()

    /** Lists the server's models, as a connection test. */
    fun models(): List<String> {
        val body = http("GET", url("models"), null, 15_000)
        val data = json.parseToJsonElement(body).jsonObject["data"]?.jsonArray ?: return emptyList()
        return data.mapNotNull { (it as? JsonObject)?.get("id")?.jsonPrimitive?.content }
    }

    /** A video's text from the upload service (`POST /v1/transcript`): what is said in it, not only the description. */
    data class Video(val title: String, val author: String, val description: String, val transcript: String,
                     val source: String, val thumbnail: String?, val partial: Boolean = false)

    /**
     * [input] is a web address or pasted recipe text. [lang] is "pt" or "en". [served] is the video's text from the
     * upload service ([isVideo] links); without it a YouTube video is read from its page (title and description),
     * and other links as web pages.
     */
    fun importRecipe(input: String, lang: String, served: Video? = null): Import {
        val isUrl = input.startsWith("http://") || input.startsWith("https://")
        val video = if (isUrl) youtubeId(input) else null
        val (source, photo) = when {
            served != null -> videoText(served) to (served.thumbnail ?: video?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" })
            video != null -> youtube(video)
            isUrl -> http("GET", input, null, 20_000, ai = false).let { pageText(it) to pageImage(it, input) }
            else -> input to null
        }
        val user = "Recipe language: ${if (lang == "pt") "European Portuguese (pt-PT)" else "English"}.\n" +
            (if (input.startsWith("http")) "Source URL: $input\n" else "") + "\n" + source.take(MAX_INPUT)
        val messages = mutableListOf(msg("system", SYSTEM), msg("user", user))
        var last: String? = null
        repeat(2) { attempt ->
            val reply = chat(messages)
            val problem = try {
                return finish(reply, overrides(lang, input, photo)).copy(prompt = user, videoSource = served?.source, partial = served?.partial == true)
            } catch (e: AiException) { throw e } catch (e: Exception) { e.message ?: "invalid" }
            last = problem
            if (attempt == 0) { messages += msg("assistant", reply); messages += msg("user", "That JSON was rejected: $problem\nReply with the corrected JSON object only.") }
        }
        throw AiException("the AI's recipe was not valid: $last")
    }

    /** Fields the app sets itself, whatever the AI wrote: format, id, language, photo and source. */
    private fun overrides(lang: String, input: String, photo: String?): (JsonObject) -> Map<String, kotlinx.serialization.json.JsonElement> = { obj ->
        val title = obj["title"]?.jsonPrimitive?.content ?: "recipe"
        val slug = java.text.Normalizer.normalize(title.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).ifEmpty { "recipe" }
        mapOf(
            "format" to JsonPrimitive(Recipe.FORMAT),
            // -pt / -en: the translated copy gets the same id with the other ending, so the list shows one of them
            "id" to JsonPrimitive("my-$slug-${System.currentTimeMillis() / 1000 % 100000}-$lang"),
            "language" to JsonPrimitive(lang),
            "image" to JsonPrimitive(photo), // the page's own photo (cached on first view), or none
            "source" to buildJsonObject {
                put("kind", "ai-import")
                if (input.startsWith("http")) put("url", input.take(500))
            },
        )
    }

    private fun finish(reply: String, fields: (JsonObject) -> Map<String, kotlinx.serialization.json.JsonElement>): Import {
        val start = reply.indexOf('{'); val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) throw IllegalArgumentException("no JSON object in the reply")
        val obj = json.parseToJsonElement(reply.substring(start, end + 1)).jsonObject
        obj["error"]?.let { throw AiException(it.jsonPrimitive.content) }
        val r = Recipe.parse(JsonObject(obj + fields(obj)).toString())
        val fit = RecipeFit.fit(r, RecipeFit.SPECIAL)
        return Import(fit.recipe, fit.changes)
    }

    /**
     * The user's corrections to an imported recipe ("there's no garlic in the video", "brown it 2 min longer"). The AI
     * gets the original source again, its own recipe and the feedback, and returns the whole recipe; it goes through
     * the same checks. Id, language, photo and source stay as they were.
     */
    fun revise(current: Import, feedback: String): Import {
        val mine = Recipe.json.encodeToJsonElement(Recipe.serializer(), current.recipe).jsonObject
        val keep: (JsonObject) -> Map<String, kotlinx.serialization.json.JsonElement> = {
            listOf("format", "id", "language", "image", "source").mapNotNull { k -> mine[k]?.let { k to it } }.toMap()
        }
        val messages = mutableListOf(msg("system", SYSTEM), msg("user", current.prompt), msg("assistant", mine.toString()),
            msg("user", "Change the recipe as follows and keep everything else as it is:\n${feedback.take(2000)}\n" +
                "Reply with the complete corrected JSON object only."))
        var last: String? = null
        repeat(2) { attempt ->
            val reply = chat(messages)
            val problem = try {
                return finish(reply, keep).copy(prompt = current.prompt, videoSource = current.videoSource, partial = current.partial)
            } catch (e: AiException) { throw e } catch (e: Exception) { e.message ?: "invalid" }
            last = problem
            if (attempt == 0) { messages += msg("assistant", reply); messages += msg("user", "That JSON was rejected: $problem\nReply with the corrected JSON object only.") }
        }
        throw AiException("the AI's recipe was not valid: $last")
    }

    /**
     * Improves a recipe the user already has ("less salt", "for 6", "no lactose", "brown the onion longer", "make the steps
     * clearer"). The AI gets the recipe as it is and the request, and returns the whole recipe in the same language; it goes
     * through the same checks as an import, and [revise] works on the result. With [keepId] (one of my recipes) it keeps its
     * id and replaces it when saved; otherwise it becomes a new recipe of mine whose source names the original
     * ("ai-tweak", ref = the original's key), and the apps then show the improved copy instead of the original.
     */
    fun tweak(r: Recipe, request: String, keepId: Boolean): Import {
        val lang = r.language ?: "pt"
        val original = Recipe.json.encodeToJsonElement(Recipe.serializer(), r).jsonObject
        val key = r.id.replace(Regex("-(pt|en)$"), "")
        val user = "Recipe language: ${if (lang == "pt") "European Portuguese (pt-PT)" else "English"}.\n" +
            "Here is a guided recipe for this machine, as JSON:\n$original\n\n" +
            "Improve it as follows, keep everything else as it is, keep it in the same language and follow the machine limits:\n" +
            request.take(2000) + "\nReply with the complete JSON object only."
        val fields: (JsonObject) -> Map<String, kotlinx.serialization.json.JsonElement> = {
            val base = key.removePrefix("my-").replace(Regex("-t\\d+$"), "").take(40)
            mapOf(
                "format" to JsonPrimitive(Recipe.FORMAT),
                "id" to JsonPrimitive(if (keepId) r.id else "my-$base-t${System.currentTimeMillis() / 1000 % 100000}-$lang"),
                "language" to JsonPrimitive(lang),
                "image" to (original["image"] ?: JsonPrimitive(null as String?)),
                "source" to if (keepId) (original["source"] ?: buildJsonObject { put("kind", "ai-import") }) else buildJsonObject {
                    put("kind", TWEAK); put("ref", key); r.source?.url?.let { put("url", it) }
                },
            )
        }
        val messages = mutableListOf(msg("system", SYSTEM), msg("user", user))
        var last: String? = null
        repeat(2) { attempt ->
            val reply = chat(messages)
            val problem = try {
                return finish(reply, fields).copy(prompt = user)
            } catch (e: AiException) { throw e } catch (e: Exception) { e.message ?: "invalid" }
            last = problem
            if (attempt == 0) { messages += msg("assistant", reply); messages += msg("user", "That JSON was rejected: $problem\nReply with the corrected JSON object only.") }
        }
        throw AiException("the AI's recipe was not valid: $last")
    }

    /** Whether [input] is a video link (YouTube and the other sites below) that the upload service can transcribe. */
    fun isVideo(input: String): Boolean = youtubeId(input) != null || isVideoLink(input)

    /** Links to video sites other than YouTube that the upload service can transcribe. */
    internal fun isVideoLink(u: String): Boolean =
        Regex("""^https?://(?:[a-z0-9-]+\.)*(?:tiktok\.com|vimeo\.com|fb\.watch|dailymotion\.com)/|^https?://(?:www\.)?(?:instagram\.com/(?:reel|reels|p)/|facebook\.com/.+/videos/|facebook\.com/reel/)""")
            .containsMatchIn(u.trim())

    private fun videoText(v: Video) = buildString {
        append("A cooking video. You only get its text, not the video. Use the transcript and the description; ")
        append("where amounts or times are missing, choose sensible ones and say so in notes. If none of it is a recipe, reply with the error.\n")
        append(TRANSCRIPT_NOTE)
        if (v.partial) append("The transcript covers only the start of the video; the description may have the rest.\n")
        append('\n')
        append("Title: ").append(v.title).append('\n')
        append("Channel: ").append(v.author).append("\n\nDescription:\n").append(v.description.ifBlank { "(none)" })
        if (v.transcript.isNotBlank()) {
            append("\n\nTranscript (").append(if (v.source == "whisper") "speech recognition" else "subtitles")
            append(", may contain mistakes):\n").append(v.transcript)
        }
    }

    /** The 11-character id of a YouTube video link (watch, youtu.be, shorts, live, embed), or null. */
    internal fun youtubeId(u: String): String? =
        Regex("""^https?://(?:www\.|m\.|music\.)?(?:youtube\.com/(?:watch\?(?:[^#]*&)?v=|shorts/|live/|embed/)|youtu\.be/)([A-Za-z0-9_-]{11})""")
            .find(u.trim())?.groupValues?.get(1)

    /**
     * What a YouTube video says about its recipe: title, channel, description (where most cooks write the recipe) and,
     * when YouTube serves them, the subtitles. The AI can't watch the video; it gets this text.
     */
    internal fun youtube(id: String): Pair<String, String?> {
        val html = http("GET", "https://www.youtube.com/watch?v=$id", null, 20_000, ai = false)
        val player = jsonAfter(html, "ytInitialPlayerResponse") ?: throw AiException("could not read the YouTube page")
        val details = player["videoDetails"] as? JsonObject
        fun str(o: JsonObject?, k: String) = (o?.get(k) as? JsonPrimitive)?.content.orEmpty()
        val tracks = runCatching {
            player["captions"]!!.jsonObject["playerCaptionsTracklistRenderer"]!!.jsonObject["captionTracks"]!!.jsonArray.map { it.jsonObject }
        }.getOrDefault(emptyList())
        // written subtitles beat automatic ones
        val track = tracks.firstOrNull { str(it, "kind") != "asr" } ?: tracks.firstOrNull()
        val transcript = track?.let { t -> runCatching { captionText(http("GET", str(t, "baseUrl"), null, 20_000, ai = false)) }.getOrNull() }
            ?.takeIf { it.length > 40 }
        val text = buildString {
            append("A YouTube cooking video. You only get its text, not the video. Use the description and the transcript; ")
            append("where amounts or times are missing, choose sensible ones and say so in notes. If none of it is a recipe, reply with the error.\n")
            append(TRANSCRIPT_NOTE).append('\n')
            append("Title: ").append(str(details, "title")).append('\n')
            append("Channel: ").append(str(details, "author")).append("\n\nDescription:\n").append(str(details, "shortDescription").ifBlank { "(none)" })
            transcript?.let { append("\n\nTranscript (subtitles, may contain mistakes):\n").append(it) }
        }
        return text to "https://i.ytimg.com/vi/$id/hqdefault.jpg"
    }

    /** The JSON object assigned to [name] in a page script ("var name = {…};"). */
    internal fun jsonAfter(html: String, name: String): JsonObject? {
        val at = Regex("""\b${Regex.escape(name)}\s*=\s*\{""").find(html) ?: return null
        val start = at.range.last
        var depth = 0; var inStr = false; var esc = false
        for (i in start until html.length) {
            val ch = html[i]
            if (inStr) { if (esc) esc = false else if (ch == '\\') esc = true else if (ch == '"') inStr = false; continue }
            when (ch) {
                '"' -> inStr = true
                '{' -> depth++
                '}' -> if (--depth == 0) return runCatching { json.parseToJsonElement(html.substring(start, i + 1)).jsonObject }.getOrNull()
            }
        }
        return null
    }

    /** Plain text of YouTube subtitles (timedtext XML). */
    internal fun captionText(xml: String): String =
        Regex("""<(?:text|p)\b[^>]*>(.*?)</(?:text|p)>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { m ->
            m.groupValues[1].replace(Regex("<[^>]+>"), "").replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"")
                .replace("&lt;", "<").replace("&gt;", ">").replace("\n", " ").trim()
        }.filter { it.isNotEmpty() }.joinToString(" ").take(MAX_INPUT)

    /**
     * The same recipe in [to] ("pt" or "en"). Only the words go to the AI; steps, grams, times and temperatures are
     * copied from [r], so a translation can never change what the machine does.
     */
    fun translate(r: Recipe, to: String): Recipe {
        val texts = buildJsonObject {
            put("title", r.title); r.servings?.let { put("servings", it) }
            put("tags", JsonArray(r.tags.map { JsonPrimitive(it) }))
            put("ingredients", JsonArray(r.ingredients.map { g -> buildJsonObject {
                g.group?.let { put("group", it) }; put("items", JsonArray(g.items.map { JsonPrimitive(it.text) }))
            } }))
            put("steps", JsonArray(r.steps.map { JsonPrimitive(it.text) }))
            put("notes", JsonArray(r.notes.map { JsonPrimitive(it) }))
        }
        val target = if (to == "pt") "European Portuguese (pt-PT, as used in Portugal: \"frigorífico\", \"natas\", \"c. de sopa\")" else "British English"
        val messages = mutableListOf(
            msg("system", "You translate recipes. Reply with one JSON object of exactly the same shape and the same number of " +
                "items in every list, every string translated to $target. Keep numbers and units as they are. Translate tags as a " +
                "cookbook would. Empty strings stay empty."),
            msg("user", texts.toString()),
        )
        var last: String? = null
        repeat(2) { attempt ->
            val reply = chat(messages)
            try {
                return applyTexts(r, reply, to)
            } catch (e: AiException) { throw e } catch (e: Exception) { last = e.message ?: "invalid" }
            if (attempt == 0) { messages += msg("assistant", reply); messages += msg("user", "Rejected: $last\nReply with the corrected JSON object only.") }
        }
        throw AiException("the translation was not valid: $last")
    }

    private fun applyTexts(r: Recipe, reply: String, to: String): Recipe {
        val o = json.parseToJsonElement(reply.substring(reply.indexOf('{'), reply.lastIndexOf('}') + 1)).jsonObject
        fun str(k: String) = o[k]?.jsonPrimitive?.content
        fun list(e: kotlinx.serialization.json.JsonElement?) = (e as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
        val groups = (o["ingredients"] as? JsonArray).orEmpty()
        val steps = list(o["steps"]); val notes = list(o["notes"])
        require(groups.size == r.ingredients.size) { "ingredients must have ${r.ingredients.size} groups" }
        require(steps.size == r.steps.size) { "steps must have ${r.steps.size} items" }
        require(notes.size == r.notes.size) { "notes must have ${r.notes.size} items" }
        val ingredients = r.ingredients.mapIndexed { i, g ->
            val t = groups[i].jsonObject; val items = list(t["items"])
            require(items.size == g.items.size) { "ingredient group ${i + 1} must have ${g.items.size} items" }
            g.copy(group = g.group?.let { t["group"]?.jsonPrimitive?.content ?: it }, items = items.map { mcc.recipe.Ingredient(it) })
        }
        val out = r.copy(
            id = r.id.replace(Regex("-(pt|en)$"), "") + "-$to", language = to,
            title = str("title")?.takeIf { it.isNotBlank() } ?: r.title, servings = r.servings?.let { str("servings") ?: it },
            tags = list(o["tags"]).ifEmpty { r.tags }, ingredients = ingredients, notes = notes,
            steps = r.steps.mapIndexed { i, s -> when (s) {
                is mcc.recipe.Step.Instruction -> s.copy(text = steps[i])
                is mcc.recipe.Step.Weigh -> s.copy(text = steps[i])
                is mcc.recipe.Step.Machine -> s.copy(text = steps[i])
            } },
        )
        require(out.validate().isEmpty()) { out.validate().joinToString("; ") }
        return out
    }

    private fun msg(role: String, content: String) = buildJsonObject { put("role", role); put("content", content) }

    private fun chat(messages: List<JsonObject>): String {
        fun body(jsonMode: Boolean) = buildJsonObject {
            put("model", model)
            put("temperature", 0.2)
            put("messages", JsonArray(messages))
            if (jsonMode) put("response_format", buildJsonObject { put("type", "json_object") })
        }.toString()
        val text = try {
            http("POST", url("chat/completions"), body(true), 240_000)
        } catch (e: HttpError) {
            if (e.code != 400 && e.code != 422) throw e
            http("POST", url("chat/completions"), body(false), 240_000) // server without JSON mode
        }
        val root = json.parseToJsonElement(text).jsonObject
        return root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
            ?: throw AiException("unexpected reply from the AI server")
    }

    /** Recipe part of a web page: its schema.org JSON-LD if it has one, else the visible text. */
    /** The page's main photo: schema.org Recipe image, else og:image. Absolute http(s) URL or null. */
    private fun pageImage(html: String, base: String): String? {
        val ld = Regex("\"image\"\\s*:\\s*(?:\\[\\s*)?(?:\\{[^}]*?\"url\"\\s*:\\s*)?\"([^\"]+)\"").find(html)?.groupValues?.get(1)
        val og = Regex("<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
            ?: Regex("<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+property=[\"']og:image", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
        val raw = (og ?: ld)?.replace("\\/", "/")?.replace("&amp;", "&") ?: return null
        return runCatching { URL(URL(base), raw).toString() }.getOrNull()?.takeIf { it.startsWith("http") }
    }

    private fun pageText(html: String): String {
        val ld = Regex("<script[^>]*application/ld\\+json[^>]*>(.*?)</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .findAll(html).map { it.groupValues[1].trim() }.firstOrNull { "recipeIngredient" in it }
        if (ld != null) return "Recipe data from the page (schema.org):\n$ld"
        return html.replace(Regex("(?is)<(script|style|noscript|svg|nav|footer|header)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?i)<br\\s*/?>|</(p|li|h[1-6]|div|tr)>"), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
            .replace(Regex("[ \\t]+"), " ").replace(Regex("\\s*\\n\\s*"), "\n").trim()
    }

    private class HttpError(val code: Int, msg: String) : IOException(msg)

    private fun url(path: String) = address.trim().trimEnd('/') + "/" + path

    private fun http(method: String, address: String, body: String?, timeout: Int, ai: Boolean = true): String {
        val c = URL(address).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 10_000; c.readTimeout = timeout
        c.setRequestProperty("User-Agent", "mcc-app")
        if (ai) {
            c.setRequestProperty("Content-Type", "application/json")
            key.trim().takeIf { it.isNotBlank() }?.let { c.setRequestProperty("Authorization", "Bearer $it") }
        }
        try {
            if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toByteArray()) } }
            val code = c.responseCode
            if (code !in 200..299) {
                val err = runCatching { c.errorStream?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()?.take(200) ?: ""
                throw HttpError(code, "HTTP $code${if (err.isNotBlank()) ": $err" else ""}")
            }
            return c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally { c.disconnect() }
    }

    companion object {
        /** Source kind of a recipe improved with [tweak]; its ref is the key of the recipe it replaces. */
        const val TWEAK = "ai-tweak"

        /** Transcripts are noisy; the AI must not take song lyrics or caption fragments for a recipe. */
        private const val TRANSCRIPT_NOTE = "Transcripts are noisy: automatic captions break sentences into short lines and mishear words " +
            "(ingredient names, numbers), and a video with only background music can come back as song lyrics. Ignore anything " +
            "that isn't about the cooking; if the transcript isn't about cooking at all, rely on the title and description.\n"

        private const val MAX_INPUT = 14_000

        private val SYSTEM = """
You turn recipes into guided recipes for the Monsieur Cuisine Connect, a Thermomix-like kitchen machine:
a 3 litre bowl (2.5 litres of liquid at most at speed 10 or turbo) with a fixed blade, heater 37-130 °C, speeds 1-10,
reverse, a built-in scale, and these original accessories (use exactly these names in the step texts):
- "mixer attachment" / "acessório batedor": a whisk clipped onto the blade, for whipping cream (at least 200 g) and egg
  whites (at least 4) or gentle stirring; speed 1-4 ONLY, never turbo, never knead, never the spatula while it is in;
- "cooking pot" / "acessório de cozer": a basket that sits in the bowl over the blade, for rice, potatoes, eggs; at least
  500 g of water under it; speed 0-1 while it is in; it also goes on the lid as a splash guard instead of the beaker;
- "deep steamer basket" / "acessório para vapor fundo": locks onto the bowl IN PLACE OF THE LID, with 1000 g of water in
  the bowl; "shallow steamer basket" / "acessório para vapor plano" only sits on top of the deep one (both always go on);
  the steamer has its own lid; meat and fish go in the deep basket so juices don't drip onto the rest;
- "measuring beaker" / "copo de medição": closes the hole in the lid; take it out only at speed 1-3;
- "spatula" / "espátula": to scrape down; its hook lifts the cooking pot out.

Reply with ONE JSON object and nothing else:
{"format":"mcc-recipe/1","id":"x","title":"…","servings":"4 portions","activeMinutes":15,"totalMinutes":45,
 "difficulty":"easy","tags":["Soups"],
 "ingredients":[{"group":null,"items":[{"text":"1 onion (100 g)"}]}],
 "steps":[
  {"type":"instruction","text":"Peel the onion."},
  {"type":"weigh","text":"Put the onion in the bowl.","grams":100},
  {"type":"machine","text":"Chop.","mode":"run","segments":[{"seconds":5,"speed":5,"tempC":0,"reverse":false}]}
 ],
 "notes":[]}

Step types: "instruction" (text), "weigh" (text, grams 1-5000: the user adds that much to the bowl on the scale),
"machine" (text, mode "run", segments run back to back; several segments make a speed ramp).
Use "mode":"turbo" only for short full-speed bursts (each segment 1-10 s).
Special modes (the machine runs its own pattern; give one segment with the total seconds): "knead" (dough, at most 600 g,
speed and tempC 0), "roast" (browning/sautéing at tempC 100-130), "steam" (steaming with the steamer baskets, tempC 120,
at most 3600 s).

Hard machine limits (never exceed them):
- while heating (tempC > 0): speed 0-3;  reverse: speed 1-3;
- tempC is 0 (no heat) or 37-130; 100 = boiling, 120 = steaming / browning (Thermomix "Varoma");
- with the mixer attachment in: speed 1-4 and no turbo or knead; say "remove the mixer attachment" before any faster step;
- one segment lasts at most 5940 s, and at most 900 s above speed 6;
- heating needs at least 50 g in the bowl; speed 0 with a temperature heats without the blade turning.

Converting other machines' settings (Thermomix / Bimby notation "time/temperature/speed"):
- "5 sec/speed 5" -> {"seconds":5,"speed":5}; "10 min/100°C/speed 1" -> {"seconds":600,"speed":1,"tempC":100};
- "Varoma" temperature -> 120; "speed spoon" / "soft" / "colher" -> speed 1; "reverse" / "inversa" -> "reverse":true;
- Thermomix "butterfly whisk" / "borboleta" -> mixer attachment; "simmering basket" / "cesto" -> cooking pot;
  "Varoma" dish/tray -> deep steamer basket (and its tray -> shallow steamer basket), set on the bowl in place of the lid;
  Moulinex Companion "mixer" / "misturador" accessory -> mixer attachment at speed 1-3, or the blade in reverse at speed 1-2
  for stews; Companion "chopping blade" / "lâmina picadora" -> the blade; Companion speeds 1-12 -> scale to 1-10;
- "Turbo/1 sec" x3 -> a turbo step with three 1 s segments; "dough mode" / "amassar" N min -> run speed 4 for N min;
- a speed above 3 while heating becomes speed 3.
For ordinary recipes, decide sensible machine steps yourself: chop onion/garlic 5 s speed 5, sauté in oil
5 min 120 °C speed 1, soups 20-25 min 100 °C speed 1 then blend 1 min rising from speed 5 to 9, etc.

Write every text in the requested language, short and clear, one action per step. Weigh steps for what goes
into the bowl by weight. Say when an accessory goes in or comes out, in its own step ("Put in the mixer attachment.",
"Remove the mixer attachment."), and where the measuring beaker goes; never leave the mixer attachment in for a faster step.
Keep the ingredient list with quantities. 1-3 short tags in the recipe language. No image.
If the input is not a recipe, reply {"error":"short reason"}.
""".trimIndent()
    }
}
