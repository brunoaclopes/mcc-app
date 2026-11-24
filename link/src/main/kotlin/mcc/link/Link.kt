package mcc.link

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The MQTT contract between the machine (mcc-app on the tablet) and anything watching it: Home Assistant and the
 * phone app. One definition, used by both sides, so they cannot drift apart. Documented in docs/HOME-ASSISTANT.md.
 */
object Topics {
    const val DISCOVERY = "homeassistant/device/mcc/config"
    /** [MachineState], retained. */
    const val STATE = "mcc/state"
    /** "online" / "offline" (last will), retained. */
    const val AVAILABILITY = "mcc/availability"
    /** [Command], not retained. */
    const val CMD = "mcc/cmd"
    /** [MachineEvent], not retained. */
    const val EVENT = "mcc/event"
    /** Live scale reading in grams (or "null"), not retained, up to 4 per second: for the phone's live readout. */
    const val SCALE = "mcc/scale"
    /** URL of the loaded recipe's photo, retained; only published when there is one (Home Assistant's image entity). */
    const val RECIPE_IMAGE = "mcc/recipe_image"
}

object LinkJson {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = true }
}

/** Retained on [Topics.STATE]. Times are ISO-8601 UTC ("2026-11-20T12:41:07Z"). */
@Serializable
data class MachineState(
    /** One of [Status]. */
    val status: String = Status.DISCONNECTED,
    val recipe: String? = null,
    /** "3/7" */
    val step: String? = null,
    @SerialName("step_text") val stepText: String? = null,
    val temperature: Int? = null,
    @SerialName("target_temperature") val targetTemperature: Int = 0,
    val speed: Int = 0,
    /** "on" / "off" */
    val reverse: String = "off",
    val finish: String? = null,
    /** Grams for Home Assistant: null once below 10 g for 3 s (empty scale, after a tare, bowl lifted), new value only on a 10 g change. Live readings are on [Topics.SCALE]. */
    val weight: Int? = null,
    /** "open" / "closed" */
    val lid: String = "closed",
    /** "none" or a board error name in lower case */
    val error: String = "none",
    /** "ready" / "off" */
    @SerialName("remote_start") val remoteStart: String = "off",
    @SerialName("remote_program") val remoteProgram: String? = null,
    @SerialName("remote_until") val remoteUntil: String? = null,
    /** Id of the loaded recipe (for the phone to show it); null without one. */
    @SerialName("recipe_id") val recipeId: String? = null,
    /** Who the loaded recipe is cooked for, as the recipe says it ("6 portions"); amounts are already scaled to it. */
    val servings: String? = null,
    /** Total and remaining time of the running step or manual run, in seconds (for progress); null when idle. */
    @SerialName("run_total") val runTotal: Int? = null,
    @SerialName("run_left") val runLeft: Int? = null,
    /** Seconds a manual run without a set time (stopwatch) has been going, or null; run_total/run_left are null then. */
    @SerialName("run_elapsed") val runElapsed: Int? = null,
    /** The tablet's screen: "on", or "off" (standby; still connected, `wake` turns it on). */
    val screen: String = "on",
    /** What a manual run is when the app started it for a purpose: "keep_warm" (after a heated recipe), else null. */
    val program: String? = null,
    /** Wi-Fi signal of the tablet in dBm (diagnostic), or null when unknown. */
    @SerialName("wifi_rssi") val wifiRssi: Int? = null,
    /** When the loaded recipe should be done (ISO UTC): machine time left plus about a minute per preparation step. */
    @SerialName("recipe_finish") val recipeFinish: String? = null,
) {
    val running get() = status == Status.RUNNING
    val lidOpen get() = lid == "open"
    val reversed get() = reverse == "on"
    val remoteReady get() = remoteStart == "ready"
    val hasError get() = error != "none"

    fun encode(): String = LinkJson.json.encodeToString(serializer(), this)

    companion object {
        fun decode(s: String): MachineState = LinkJson.json.decodeFromString(serializer(), s)
    }
}

object Status {
    const val DISCONNECTED = "disconnected"
    const val IDLE = "idle"
    const val RUNNING = "running"
    const val PAUSED = "paused"
    const val FINISHED = "finished"
    const val ERROR = "error"
    val ALL = listOf(DISCONNECTED, IDLE, RUNNING, PAUSED, FINISHED, ERROR)
}

/** Sent once on [Topics.EVENT]; fields not used by a type stay null. */
@Serializable
data class MachineEvent(
    @SerialName("event_type") val type: String,
    val at: String? = null,
    val recipe: String? = null,
    val step: String? = null,
    val text: String? = null,
    val grams: Int? = null,
    val reason: String? = null,
    val error: String? = null,
) {
    fun encode(): String = LinkJson.json.encodeToString(serializer(), this)

    companion object {
        const val FINISHED = "finished"
        const val NEEDS_YOU = "needs_you"
        const val RUN_DONE = "run_done"
        const val WEIGHED = "weighed"
        const val STOPPED = "stopped"
        const val PAUSED = "paused"
        const val ERROR = "error"
        val TYPES = listOf(FINISHED, NEEDS_YOU, RUN_DONE, WEIGHED, STOPPED, PAUSED, ERROR)

        fun decode(s: String): MachineEvent = LinkJson.json.decodeFromString(serializer(), s)
    }
}

/**
 * Commands on [Topics.CMD], as a bare word or {"command": "...", ...}. None of them can choose what the machine
 * runs: start only starts a run permitted at the machine, load only shows a recipe there (it never starts it).
 */
sealed class Command {
    object Stop : Command()
    object Pause : Command()
    object Tare : Command()
    object Start : Command()
    /** Closes the loaded recipe on the machine (stops it first if it runs). Only ever stops things. */
    object Cancel : Command()
    /** Re-reads the recipe server's index (a recipe was just published). Changes nothing else. */
    object Refresh : Command()
    /** Turns the machine's screen on (ends standby). Never unlocks the controls or starts anything. */
    object Wake : Command()
    /**
     * Opens a recipe on the machine's Cook screen (needs the machine's opt-in; refused while one is in progress).
     * [servings] scales its amounts for that many people (null: as written).
     */
    data class Load(val recipeId: String, val servings: Int? = null) : Command()

    fun encode(): String = when (this) {
        Stop -> "stop"; Pause -> "pause"; Tare -> "tare"; Start -> "start"; Cancel -> "cancel"; Refresh -> "refresh"; Wake -> "wake"
        is Load -> """{"command":"load","recipe":${JsonPrimitive(recipeId)}""" + (servings?.let { ""","servings":$it""" } ?: "") + "}"
    }

    companion object {
        fun parse(raw: String): Command? {
            val t = raw.trim()
            var word = t
            var recipe: String? = null
            var servings: Int? = null
            if (t.startsWith("{")) {
                val o = runCatching { LinkJson.json.parseToJsonElement(t).jsonObject }.getOrNull() ?: return null
                word = o["command"]?.jsonPrimitive?.content ?: return null
                recipe = (o["recipe"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                servings = (o["servings"] as? JsonPrimitive)?.content?.toIntOrNull()?.takeIf { it in 1..99 }
            }
            return when (word.lowercase()) {
                "stop" -> Stop; "pause" -> Pause; "tare" -> Tare; "start" -> Start; "cancel" -> Cancel; "refresh" -> Refresh; "wake" -> Wake
                "load" -> recipe?.takeIf { it.isNotBlank() && it.length <= 200 }?.let { Load(it, servings) }
                else -> null
            }
        }
    }
}
