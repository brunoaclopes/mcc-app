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
    private val bodies = mutableListOf<String>()
    private var auth: String? = null
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/v1/recipes") { x ->
            auth = x.requestHeaders.getFirst("Authorization")
            val body = x.requestBody.readBytes().toString(Charsets.UTF_8); bodies += body
            val key = Json.parseToJsonElement(body).jsonObject["key"]!!.jsonPrimitive.content
            val (code, reply) = when {
                auth != "Bearer t0k" -> 401 to """{"error":"bad token"}"""
                key == "web-bad" -> 422 to """{"error":"steps: speed 11 is above 10"}"""
                key in taken -> 409 to """{"error":"exists"}"""
                else -> 201 to """{"key":"$key","ids":["$key-pt","$key-en"]}"""
            }
            val b = reply.toByteArray(); x.sendResponseHeaders(code, b.size.toLong()); x.responseBody.use { it.write(b) }
        }
        start()
    }
    private val url = "http://127.0.0.1:${server.address.port}/"

    @After fun stop() = server.stop(0)

    private fun recipe(lang: String) = Recipe(
        format = Recipe.FORMAT, id = "my-sopa-123-$lang", title = if (lang == "pt") "Sopa" else "Soup", language = lang,
        tags = listOf(if (lang == "pt") mcc.app.i18n.Pt.tagMine else mcc.app.i18n.En.tagMine, "Sopas"),
        ingredients = listOf(IngredientGroup(null, listOf(Ingredient("1 cebola")))), steps = listOf(Step.Instruction("Corte.")),
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
    }

    @Test fun rejectionCarriesTheServersReason() {
        val e = assertFailsWith<RecipePublisher.Rejected> { RecipePublisher(url, "t0k").publish("web-bad", listOf(recipe("pt"))) }
        assertEquals("HTTP 422: steps: speed 11 is above 10", e.message)
        assertFailsWith<RecipePublisher.Rejected> { RecipePublisher(url, "wrong").publish("web-x", listOf(recipe("pt"))) }
    }
}
