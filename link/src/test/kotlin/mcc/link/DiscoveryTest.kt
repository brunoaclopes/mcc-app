package mcc.link

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiscoveryTest {
    private val cfg = Discovery.config("Kitchen", "9.9")
    private val cmps = cfg["cmps"]!!.jsonObject

    @Test fun `entity ids carry the area prefix`() {
        assertEquals("sensor.kitchen_monsieur_cuisine_status", cmps["status"]!!.jsonObject["default_entity_id"]!!.jsonPrimitive.content)
        assertEquals("sensor.monsieur_cuisine_status",
            Discovery.config("", "9.9")["cmps"]!!.jsonObject["status"]!!.jsonObject["default_entity_id"]!!.jsonPrimitive.content)
    }

    @Test fun `every command has a button`() {
        val pressed = cmps.values.map { it.jsonObject }.filter { it["p"]!!.jsonPrimitive.content == "button" }
            .map { it["payload_press"]!!.jsonPrimitive.content }.toSet()
        assertEquals(setOf("start", "pause", "stop", "tare", "cancel"), pressed)
        pressed.forEach { assertTrue(Command.parse(it) != null, it) }
    }

    @Test fun `unique ids are unique`() {
        val ids = cmps.values.map { it.jsonObject["unique_id"]!!.jsonPrimitive.content }
        assertEquals(ids.size, ids.toSet().size)
    }

    /** Writes the payload for checking against a real Home Assistant (see docs/HOME-ASSISTANT.md). */
    @Test fun `dump payload`() {
        File("build/discovery.json").apply { parentFile.mkdirs() }.writeText(cfg.toString())
    }
}
