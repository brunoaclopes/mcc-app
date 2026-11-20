package mcc.link

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Home Assistant MQTT device discovery for the machine, published retained on [Topics.DISCOVERY]. Every entity reads
 * the one retained [MachineState] on [Topics.STATE]; buttons and the text entity publish [Command]s on [Topics.CMD].
 */
object Discovery {
    /** [area] is the Home Assistant area (also the entity id prefix, may be empty); [version] the app version. */
    fun config(area: String, version: String): JsonObject {
        // entity ids: <area>_monsieur_cuisine_<key>, e.g. sensor.kitchen_monsieur_cuisine_status
        val prefix = area.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').let { if (it.isEmpty()) "" else "${it}_" }
        return buildJsonObject {
            putJsonObject("dev") {
                putJsonArray("ids") { add(JsonPrimitive("mcc")) }
                put("name", "Monsieur Cuisine")
                put("mf", "Silvercrest")
                put("mdl", "Monsieur Cuisine Connect SKMC 1200")
                put("sw", "mcc-app ${version}")
                if (area.isNotEmpty()) put("sa", area)
            }
            putJsonObject("o") { put("name", "mcc-app"); put("sw", version) }
            put("avty_t", Topics.AVAILABILITY)
            put("stat_t", Topics.STATE)
            putJsonObject("cmps") {
                fun cmp(key: String, platform: String, name: String, block: JsonObjectBuilder.() -> Unit = {}) =
                    putJsonObject(key) {
                        put("p", platform)
                        put("name", name)
                        put("unique_id", "mcc_$key")
                        put("default_entity_id", "$platform.${prefix}monsieur_cuisine_$key")
                        if (platform != "button" && platform != "event" && platform != "image") put("value_template", "{{ value_json.$key }}")
                        block()
                    }
                cmp("status", "sensor", "Status") {
                    put("device_class", "enum")
                    putJsonArray("options") { Status.ALL.forEach { add(JsonPrimitive(it)) } }
                    put("icon", "mdi:chef-hat")
                }
                cmp("recipe", "sensor", "Recipe") { put("icon", "mdi:book-open-variant") }
                cmp("step", "sensor", "Step") {
                    put("icon", "mdi:format-list-numbered")
                    put("json_attributes_topic", Topics.STATE)
                    put("json_attributes_template", "{{ {'text': value_json.get('step_text')} | tojson }}")
                }
                // the board reports 0 below 37 °C, and target 0 means not heating: both show as unknown rather than a fake 0 °C
                cmp("temperature", "sensor", "Temperature") {
                    put("unit_of_measurement", "°C"); put("device_class", "temperature"); put("state_class", "measurement")
                    put("value_template", "{{ value_json.temperature if value_json.temperature else None }}")
                }
                cmp("target_temperature", "sensor", "Target temperature") {
                    put("unit_of_measurement", "°C"); put("device_class", "temperature")
                    put("value_template", "{{ value_json.target_temperature if value_json.target_temperature else None }}")
                }
                cmp("speed", "sensor", "Speed") { put("icon", "mdi:fan"); put("state_class", "measurement") }
                cmp("reverse", "binary_sensor", "Reverse") { put("payload_on", "on"); put("payload_off", "off"); put("icon", "mdi:rotate-left") }
                cmp("finish", "sensor", "Finish") { put("device_class", "timestamp") }
                cmp("weight", "sensor", "Scale") { put("unit_of_measurement", "g"); put("device_class", "weight"); put("state_class", "measurement") }
                cmp("lid", "binary_sensor", "Lid") { put("payload_on", "open"); put("payload_off", "closed"); put("device_class", "opening") }
                cmp("error", "sensor", "Error") { put("icon", "mdi:alert-circle-outline"); put("entity_category", "diagnostic") }
                cmp("remote_start", "binary_sensor", "Remote start ready") {
                    put("payload_on", "ready"); put("payload_off", "off"); put("icon", "mdi:remote")
                    put("json_attributes_topic", Topics.STATE)
                    put("json_attributes_template", "{{ {'program': value_json.get('remote_program'), 'until': value_json.get('remote_until')} | tojson }}")
                }
                cmp("event", "event", "Event") {
                    put("stat_t", Topics.EVENT)
                    putJsonArray("event_types") { MachineEvent.TYPES.forEach { add(JsonPrimitive(it)) } }
                    put("icon", "mdi:bell-ring-outline")
                }
                // derived from the same state, for dashboards and automations
                cmp("running", "binary_sensor", "Running") {
                    put("device_class", "running")
                    put("value_template", "{{ 'ON' if value_json.status == 'running' else 'OFF' }}")
                }
                cmp("problem", "binary_sensor", "Problem") {
                    put("device_class", "problem")
                    put("value_template", "{{ 'ON' if value_json.error != 'none' else 'OFF' }}")
                }
                cmp("time_left", "sensor", "Time left") {
                    put("device_class", "duration"); put("unit_of_measurement", "s")
                    put("value_template", "{{ value_json.run_left if value_json.run_left is not none else 0 }}")
                }
                cmp("progress", "sensor", "Step progress") {
                    put("unit_of_measurement", "%"); put("icon", "mdi:progress-clock")
                    put("value_template", "{{ (100 - 100 * value_json.run_left / value_json.run_total) | round(0) | int " +
                        "if value_json.run_total and value_json.run_left is not none else 0 }}")
                }
                cmp("recipe_progress", "sensor", "Recipe progress") {
                    put("unit_of_measurement", "%"); put("icon", "mdi:chart-donut")
                    put("value_template", "{% set s = (value_json.step or '0/0').split('/') %}" +
                        "{{ (100 * (s[0] | int - 1) / (s[1] | int)) | round(0) | int if s[1] | int > 0 else 0 }}")
                }
                cmp("recipe_image", "image", "Recipe photo") {
                    put("url_topic", Topics.RECIPE_IMAGE)
                }
                cmp("load_recipe", "text", "Load recipe") {
                    put("icon", "mdi:book-arrow-right")
                    put("value_template", "{{ value_json.recipe_id or '' }}")
                    put("command_topic", Topics.CMD)
                    put("command_template", "{\"command\":\"load\",\"recipe\":{{ value | tojson }}}")
                    put("min", 0); put("max", 200)
                }
                cmp("start", "button", "Start (permitted run)") { put("command_topic", Topics.CMD); put("payload_press", "start"); put("icon", "mdi:play-circle") }
                cmp("pause", "button", "Pause") { put("command_topic", Topics.CMD); put("payload_press", "pause"); put("icon", "mdi:pause-circle") }
                cmp("stop", "button", "Stop") { put("command_topic", Topics.CMD); put("payload_press", "stop"); put("icon", "mdi:stop-circle") }
                cmp("tare", "button", "Tare scale") { put("command_topic", Topics.CMD); put("payload_press", "tare"); put("icon", "mdi:scale-balance") }
            }
            put("qos", 1)
        }
    }
}
