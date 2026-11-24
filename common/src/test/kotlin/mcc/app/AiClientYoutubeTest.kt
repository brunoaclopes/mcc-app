package mcc.app

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiClientYoutubeTest {
    private val ai = AiClient("", "", "")

    @Test fun recognisesVideoLinks() {
        assertEquals("wy8thXL5_CI", ai.youtubeId("https://www.youtube.com/watch?v=wy8thXL5_CI"))
        assertEquals("wy8thXL5_CI", ai.youtubeId("https://m.youtube.com/watch?feature=share&v=wy8thXL5_CI&t=3"))
        assertEquals("wy8thXL5_CI", ai.youtubeId("https://youtu.be/wy8thXL5_CI?si=abc"))
        assertEquals("wy8thXL5_CI", ai.youtubeId("https://youtube.com/shorts/wy8thXL5_CI"))
        assertNull(ai.youtubeId("https://www.receiteria.com.br/receita/legumes/"))
        assertNull(ai.youtubeId("https://www.youtube.com/@channel"))
        assertTrue(ai.isVideoLink("https://www.tiktok.com/@chef/video/7300000000000000000"))
        assertTrue(ai.isVideoLink("https://www.instagram.com/reel/C1abcDEF/"))
        assertTrue(ai.isVideoLink("https://vimeo.com/123456"))
        assertFalse(ai.isVideoLink("https://www.receiteria.com.br/receita/legumes/"))
        assertFalse(ai.isVideoLink("https://www.instagram.com/chef/"))
    }

    @Test fun readsThePlayerObjectWithBracesInStrings() {
        val html = """<script>var ytInitialPlayerResponse = {"videoDetails":{"title":"Sopa {fácil}","shortDescription":"1 \"cebola\"\n2 cenouras"}};var x = {"a":1};</script>"""
        val o = ai.jsonAfter(html, "ytInitialPlayerResponse")!!
        val d = o["videoDetails"]!!.jsonObject
        assertEquals("Sopa {fácil}", d["title"]!!.jsonPrimitive.content)
        assertEquals("1 \"cebola\"\n2 cenouras", d["shortDescription"]!!.jsonPrimitive.content)
    }

    @Test fun subtitlesBecomePlainText() {
        val xml = """<?xml version="1.0"?><transcript><text start="0" dur="2">corte a &amp;#39;cebola&amp;#39;</text><text start="2" dur="2">e junte 30 g de azeite</text></transcript>"""
        assertTrue(ai.captionText(xml).startsWith("corte a"))
        assertTrue(ai.captionText(xml).endsWith("e junte 30 g de azeite"))
    }

    /** A real page saved with: curl -A mcc-app https://www.youtube.com/watch?v=… (skipped when the file isn't there). */
    @Test fun realPageIfAvailable() {
        val f = File(System.getenv("YT_PAGE") ?: return).takeIf { it.exists() } ?: return
        val o = ai.jsonAfter(f.readText(), "ytInitialPlayerResponse")!!
        val title = o["videoDetails"]!!.jsonObject["title"]!!.jsonPrimitive.content
        println("real page title: $title")
        assertTrue(title.isNotBlank())
    }
}
