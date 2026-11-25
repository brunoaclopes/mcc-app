package mcc.app

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mcc.recipe.Ingredient
import mcc.recipe.IngredientGroup
import mcc.recipe.Recipe
import mcc.recipe.Step
import org.junit.After
import org.junit.Test
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RecipePublisherTest {
    private val taken = mutableSetOf("web-sopa")
    /** Idempotency-Key -> key it was stored under, like the service. */
    private val seen = mutableMapOf<String, String>()
    private val bodies = mutableListOf<String>()
    private var auth: String? = null
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/v1/recipes") { x ->
            auth = x.requestHeaders.getFirst("Authorization")
            val body = x.requestBody.readBytes().toString(Charsets.UTF_8); bodies += body
            val key = Json.parseToJsonElement(body).jsonObject["key"]!!.jsonPrimitive.content
            val upKey = x.requestHeaders.getFirst("Idempotency-Key")
            val (code, reply) = when {
                auth != "Bearer t0k" -> 401 to """{"error":"bad token"}"""
                upKey != null && upKey in seen -> seen[upKey]!!.let { 201 to """{"key":"$it","ids":["$it-pt","$it-en"]}""" }
                key == "web-bad" -> 422 to """{"error":"steps: speed 11 is above 10"}"""
                key in taken -> 409 to """{"error":"exists"}"""
                else -> { taken += key; upKey?.let { seen[it] = key }; 201 to """{"key":"$key","ids":["$key-pt","$key-en"]}""" }
            }
            val b = reply.toByteArray(); x.sendResponseHeaders(code, b.size.toLong()); x.responseBody.use { it.write(b) }
        }
        createContext("/v1/transcript") { x ->
            val asked = Json.parseToJsonElement(x.requestBody.readBytes().toString(Charsets.UTF_8)).jsonObject["url"]!!.jsonPrimitive.content
            transcriptCalls++
            val (code, reply) = when {
                x.requestHeaders.getFirst("Authorization") != "Bearer t0k" -> 401 to """{"error":"missing or wrong token"}"""
                "slow" in asked -> 504 to """{"error":"reading the video took too long"}"""
                "broken" in asked -> 502 to """{"error":"the server could not finish this; try again later"}"""
                "youtu" !in asked -> 422 to """{"error":"that address is not a single video"}"""
                // a long video: the first answer has only the start, the next one the rest
                "long" in asked && transcriptCalls == 1 -> 200 to """{"title":"T","author":"","description":"","language":"pt",
                    "transcript":"Primeira parte.","source":"whisper","partial":true,"thumbnail":""}"""
                "long" in asked -> 200 to """{"title":"T","author":"","description":"","language":"pt",
                    "transcript":"Primeira parte.\nSegunda parte.","source":"whisper","partial":false,"thumbnail":""}"""
                else -> 200 to """{"title":"Arroz de pato","author":"Chef","description":"","language":"pt",
                    "transcript":"Juntem 300 gramas de arroz e deixem cozinhar 12 minutos.","source":"whisper","partial":false,"thumbnail":null}"""
            }
            val b = reply.toByteArray(); x.sendResponseHeaders(code, b.size.toLong()); x.responseBody.use { it.write(b) }
        }
        // a stand-in AI server: returns a fixed recipe and keeps what it was asked
        createContext("/ai/v1/chat/completions") { x ->
            prompts += x.requestBody.readBytes().toString(Charsets.UTF_8)
            val recipe = """{"title":"Arroz de pato","servings":"4 doses","ingredients":[{"group":null,"items":[{"text":"300 g de arroz"}]}],
                "steps":[{"type":"instruction","text":"Junte o arroz."}]}"""
            val reply = kotlinx.serialization.json.buildJsonObject {
                put("choices", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.buildJsonObject {
                    put("message", kotlinx.serialization.json.buildJsonObject { put("content", kotlinx.serialization.json.JsonPrimitive(recipe)) })
                }) })
            }.toString().toByteArray()
            x.sendResponseHeaders(200, reply.size.toLong()); x.responseBody.use { it.write(reply) }
        }
        start()
    }
    private val prompts = mutableListOf<String>()
    private var transcriptCalls = 0
    private val url = "http://127.0.0.1:${server.address.port}/"

    @After fun stop() = server.stop(0)

    private fun recipe(lang: String) = Recipe(
        format = Recipe.FORMAT, id = "my-sopa-123-$lang", title = if (lang == "pt") "Sopa" else "Soup", language = lang,
        tags = listOf(if (lang == "pt") mcc.app.i18n.Pt.tagMine else mcc.app.i18n.En.tagMine, "Sopas"),
        ingredients = listOf(IngredientGroup(null, listOf(Ingredient("1 cebola")))),
        steps = listOf(Step.Instruction("Corte."), Step.Machine("Coza.", segments = listOf(mcc.recipe.Segment(600, 1, 100)))),
    )

    @Test fun keyFollowsTheRepoStyle() {
        assertEquals("web-legumes-salteados-no-azeite", RecipePublisher.keyFor("Legumes salteados no azeite"))
        assertEquals("web-creme-brulee", RecipePublisher.keyFor("Crème brûlée!"))
    }

    @Test fun takenKeyGetsASuffixAndRepoIdsAndTags() {
        val key = RecipePublisher(url, "t0k").publish("web-sopa", listOf(recipe("pt"), recipe("en")))
        assertEquals("web-sopa-2", key)
        assertEquals(2, bodies.size)
        val sent = Json.parseToJsonElement(bodies.last()).jsonObject["recipes"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("web-sopa-2-pt", "web-sopa-2-en"), sent.map { it["id"]!!.jsonPrimitive.content })
        assertTrue(sent.all { r -> r["tags"]!!.jsonArray.map { it.jsonPrimitive.content } == listOf("Sopas") })
        // the repository's validator requires mode, also for the default (run)
        assertTrue(sent.all { r -> r["steps"]!!.jsonArray[1].jsonObject["mode"]?.jsonPrimitive?.content == "run" })
    }

    @Test fun retryWithTheSameUploadKeyGetsTheFirstKey() {
        val pub = RecipePublisher(url, "t0k")
        assertEquals("web-caldo", pub.publish("web-caldo", listOf(recipe("pt"), recipe("en")), "u-1234567890"))
        // the answer was lost and the user tries again: the service knows the key, no "web-caldo-2"
        assertEquals("web-caldo", pub.publish("web-caldo", listOf(recipe("pt"), recipe("en")), "u-1234567890"))
        // a different recipe with the same title still gets a free name
        assertEquals("web-caldo-2", pub.publish("web-caldo", listOf(recipe("pt"), recipe("en")), "u-other00000"))
    }

    @Test fun rejectionCarriesTheServersReason() {
        val e = assertFailsWith<RecipePublisher.Rejected> { RecipePublisher(url, "t0k").publish("web-bad", listOf(recipe("pt"))) }
        assertEquals("HTTP 422: steps: speed 11 is above 10", e.message)
        assertFailsWith<RecipePublisher.Rejected> { RecipePublisher(url, "wrong").publish("web-x", listOf(recipe("pt"))) }
    }

    @Test fun transcriptFromTheService() {
        val v = RecipePublisher(url, "t0k").transcript("https://youtu.be/wy8thXL5_CI")
        assertEquals("Arroz de pato", v.title)
        assertEquals("whisper", v.source)
        assertEquals(null, v.thumbnail)
        assertEquals(false, v.partial)
        assertTrue("300 gramas" in v.transcript)
    }

    @Test fun partialTranscriptIsAskedForAgain() {
        val v = RecipePublisher(url, "t0k").transcript("https://youtu.be/long0000000")
        assertEquals(2, transcriptCalls)
        assertEquals(false, v.partial)
        assertEquals("Primeira parte.\nSegunda parte.", v.transcript)
    }

    @Test fun videoProblemsAreForTheUserOtherFailuresFallBack() {
        val pub = RecipePublisher(url, "t0k")
        assertEquals("that address is not a single video", assertFailsWith<RecipePublisher.VideoProblem> { pub.transcript("https://example.org/") }.message)
        assertEquals("reading the video took too long", assertFailsWith<RecipePublisher.VideoProblem> { pub.transcript("https://youtu.be/slow0000000") }.message)
        assertTrue(assertFailsWith<RecipePublisher.VideoProblem> { RecipePublisher(url, "wrong").transcript("https://youtu.be/x") }.badToken)
        // a server error or no server at all: not the user's problem, the app reads the video page instead
        val e = assertFailsWith<java.io.IOException> { pub.transcript("https://youtu.be/broken00000") }
        assertTrue(e !is RecipePublisher.Rejected)
        assertFailsWith<java.io.IOException> { RecipePublisher("http://127.0.0.1:1/", "t0k").transcript("https://youtu.be/x") }
    }

    @Test fun videoImportGivesTheAiTheTranscript() {
        val ai = AiClient(url + "ai/v1", "m", "")
        assertTrue(ai.isVideo("https://youtu.be/wy8thXL5_CI"))
        val link = "https://youtu.be/wy8thXL5_CI"
        val imp = ai.importRecipe(link, "pt", RecipePublisher(url, "t0k").transcript(link))
        assertEquals("Arroz de pato", imp.recipe.title)
        assertEquals("https://i.ytimg.com/vi/wy8thXL5_CI/hqdefault.jpg", imp.recipe.image)
        assertEquals("whisper", imp.videoSource)
        assertTrue("Transcript (speech recognition" in imp.prompt && "300 gramas de arroz" in imp.prompt, imp.prompt)
        assertTrue("song lyrics" in imp.prompt)
        assertEquals(1, prompts.size)
    }
}
