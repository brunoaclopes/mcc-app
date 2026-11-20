package mcc.link

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkTest {
    @Test fun stateKeepsTheFieldNamesHomeAssistantUses() {
        val s = MachineState(status = Status.RUNNING, stepText = "Add rice", targetTemperature = 100, remoteStart = "ready")
        val json = s.encode()
        for (key in listOf("status", "recipe", "step", "step_text", "temperature", "target_temperature", "speed", "reverse", "finish",
            "weight", "lid", "error", "remote_start", "remote_program", "remote_until"))
            assertTrue("\"$key\":" in json, "missing $key in $json")
        assertTrue("\"recipe\":null" in json, "nulls must be explicit for HA templates: $json")
        assertEquals(s, MachineState.decode(json))
    }

    @Test fun stateIgnoresUnknownFields() {
        assertEquals(Status.IDLE, MachineState.decode("""{"status":"idle","something_new":1}""").status)
    }

    @Test fun eventRoundTrip() {
        val e = MachineEvent(MachineEvent.NEEDS_YOU, recipe = "Risotto", step = "4/9", text = "Add the rice")
        assertTrue(e.encode().startsWith("{\"event_type\":\"needs_you\""))
        assertEquals(e, MachineEvent.decode(e.encode()))
    }

    @Test fun commands() {
        assertEquals(Command.Stop, Command.parse(" STOP "))
        assertEquals(Command.Pause, Command.parse("""{"command":"pause"}"""))
        assertEquals(Command.Load("pt-risotto"), Command.parse(Command.Load("pt-risotto").encode()))
        assertNull(Command.parse("""{"command":"load"}"""))
        assertNull(Command.parse("""{"command":"load","recipe":5}"""))
        assertNull(Command.parse("heat 100"))
        assertNull(Command.parse("{nonsense"))
    }
}
