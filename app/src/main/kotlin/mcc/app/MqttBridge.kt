package mcc.app

import android.os.SystemClock
import android.util.Log
import kotlinx.serialization.json.JsonPrimitive
import mcc.link.Command
import mcc.link.Discovery
import mcc.link.MachineEvent
import mcc.link.MachineState
import mcc.link.Status
import mcc.link.Topics
import mcc.recipe.RecipeRunner
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * MQTT link to Home Assistant, following the contract in HA's /config/mcc/README.md:
 *  - homeassistant/device/mcc/config  device discovery (retained)
 *  - mcc/state                         one JSON object with all fields (retained)
 *  - mcc/availability                  online / offline (offline is the last will)
 *  - mcc/cmd                           stop | pause | tare | start
 *  - mcc/event                         one-off events, not retained: {"event_type": "finished", ...}
 * "start" only starts a run that someone prepared and permitted at the machine (opt-in, single use, time-limited,
 * cancelled when the lid opens or the bowl contents change). Home Assistant can never choose what runs.
 */
class MqttBridge(private val service: MachineService, private val prefs: Prefs) {
    companion object {
        // the contract lives in the shared :link module (also used by the phone app)
        const val DISCOVERY = Topics.DISCOVERY
        const val STATE = Topics.STATE
        const val AVAILABILITY = Topics.AVAILABILITY
        const val CMD = Topics.CMD
        const val EVENT = Topics.EVENT
    }

    @Volatile var status: String = "starting"
        private set
    @Volatile private var closed = false
    @Volatile private var client: MqttClient? = null
    private var lastPayload: String? = null
    private var lastPublishAt = 0L
    /** The scale flickers by one 5 g step at rest; only report changes larger than that. */
    private var reportedWeight: Int? = null
    /** Finish time in epoch ms; only moved when it drifts more than 2 s, so it does not republish every tick. */
    private var reportedFinish: Long? = null
    private var thread: Thread? = null
    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    private val eventWorker = java.util.concurrent.Executors.newSingleThreadExecutor()

    /**
     * Publishes a one-off event (not retained, so a Home Assistant restart never replays it). Off the serial loop:
     * a QoS 1 publish waits for the broker. Dropped when not connected; the retained state still tells the story.
     */
    fun publishEvent(e: MachineEvent) {
        val payload = e.copy(at = iso.format(Date())).encode()
        Log.i(MachineService.TAG, "mqtt event $payload")
        if (closed) return
        eventWorker.execute {
            val c = client ?: return@execute
            try { if (c.isConnected) c.publish(EVENT, MqttMessage(payload.toByteArray()).apply { qos = 1; isRetained = false }) }
            catch (e: Exception) { Log.w(MachineService.TAG, "mqtt event not sent: ${e.message}") }
        }
    }

    fun start() {
        thread = Thread(::connectLoop, "mcc-mqtt").also { it.isDaemon = true; it.start() }
    }

    fun close() {
        closed = true
        eventWorker.shutdown()
        thread?.interrupt()
        try {
            client?.let {
                if (it.isConnected) {
                    it.publish(AVAILABILITY, "offline".toByteArray(), 1, true)
                    it.disconnect(1_000)
                }
                it.close()
            }
        } catch (_: Exception) {}
    }

    private fun connectLoop() {
        var backoff = 2_000L
        while (!closed) {
            val c = client
            if (c == null || !c.isConnected) {
                try {
                    status = "connecting to ${prefs.mqttHost}:${prefs.mqttPort}"
                    connect()
                    status = "connected"
                    backoff = 2_000L
                } catch (e: Exception) {
                    status = "error: ${e.message ?: e.javaClass.simpleName}"
                    Log.w(MachineService.TAG, "mqtt connect failed", e)
                    try { Thread.sleep(backoff) } catch (_: InterruptedException) { return }
                    backoff = minOf(backoff * 2, 60_000L)
                    continue
                }
            }
            try { Thread.sleep(5_000) } catch (_: InterruptedException) { return }
        }
    }

    private fun connect() {
        client?.let { try { it.close() } catch (_: Exception) {} }
        val c = MqttClient("tcp://${prefs.mqttHost}:${prefs.mqttPort}", "mcc-tablet", MemoryPersistence())
        val o = MqttConnectOptions().apply {
            isCleanSession = true
            connectionTimeout = 10
            keepAliveInterval = 30
            isAutomaticReconnect = false
            if (prefs.mqttUser.isNotBlank()) {
                userName = prefs.mqttUser
                password = prefs.mqttPassword.toCharArray()
            }
            setWill(AVAILABILITY, "offline".toByteArray(), 1, true)
        }
        c.connect(o)
        c.subscribe(CMD, 1) { _, m -> onCommand(String(m.payload)) }
        c.subscribe("homeassistant/status", 1) { _, m ->
            if (String(m.payload) == "online") { publishDiscovery(c); lastPayload = null }
        }
        publishDiscovery(c)
        c.publish(AVAILABILITY, "online".toByteArray(), 1, true)
        client = c
        lastPayload = null
        lastImage = null
        lastScale = null
    }

    /** Accepts a bare word ("stop") or JSON ({"command":"stop"}); see [Command]. Anything else is ignored. */
    internal fun onCommand(raw: String) {
        when (val cmd = Command.parse(raw)) {
            Command.Stop -> service.stop("stop from Home Assistant", remote = true)
            Command.Pause -> service.pauseFromRemote()
            Command.Tare -> service.tare()
            Command.Start -> service.remoteStart()
            Command.Cancel -> service.cancelRecipe(remote = true)
            is Command.Load -> service.remoteLoad(cmd.recipeId, cmd.servings)
            Command.Refresh -> service.refreshRecipes()
            null -> Log.w(MachineService.TAG, "ignored mqtt command '${raw.take(200)}'")
        }
        lastPayload = null
    }

    /** Called from the machine loop (every 100 ms); publishes on change, at most twice per second. */
    fun publishIfChanged() {
        val c = client ?: return
        if (!c.isConnected) return
        val now = SystemClock.elapsedRealtime()
        publishSide(c, now)
        if (now - lastPublishAt < 500) return
        val payload = statePayload().encode()
        if (payload == lastPayload && now - lastPublishAt < 60_000) return
        try {
            c.publish(STATE, MqttMessage(payload.toByteArray()).apply { qos = 0; isRetained = true })
            lastPayload = payload
            lastPublishAt = now
        } catch (e: Exception) {
            status = "publish failed: ${e.message}"
        }
    }

    private var lastScale: Int? = null
    private var lastScaleAt = 0L
    private var lastImage: String? = null

    /** The live scale (for the phone) and the recipe photo URL (for Home Assistant's image entity). */
    private fun publishSide(c: MqttClient, now: Long) {
        try {
            val g = MachineService.displayGrams
            if (g != lastScale && now - lastScaleAt >= 250) {
                c.publish(Topics.SCALE, MqttMessage((g?.toString() ?: "null").toByteArray()).apply { qos = 0; isRetained = false })
                lastScale = g; lastScaleAt = now
            }
            val img = imageUrl(service.recipe?.image)
            if (img != null && img != lastImage) {
                c.publish(Topics.RECIPE_IMAGE, MqttMessage(img.toByteArray()).apply { qos = 1; isRetained = true })
                lastImage = img
            }
        } catch (e: Exception) {
            status = "publish failed: ${e.message}"
        }
    }

    private fun statePayload(): MachineState {
        val s = MachineService.snapshot
        val f = s?.lastFrame
        val rs = MachineService.runnerState
        val recipe = service.recipe
        val nul = JsonPrimitive(null as String?)

        val st = when {
            s == null || !s.connected -> "disconnected"
            f?.error != null -> "error"
            s.running -> "running"
            rs?.phase == RecipeRunner.Phase.PAUSED || MachineService.pausedRun != null -> "paused"
            rs?.phase == RecipeRunner.Phase.DONE -> "finished"
            else -> "idle"
        }

        val g = MachineService.displayGrams
        reportedWeight = when {
            g == null || g < 10 -> null
            reportedWeight.let { it == null || kotlin.math.abs(it - g) >= 10 } -> g
            else -> reportedWeight
        }

        val remaining = when {
            rs?.phase == RecipeRunner.Phase.RUNNING -> rs.remainingMillis
            s?.running == true -> s.remainingMillis
            else -> null
        }
        if (remaining == null) reportedFinish = null
        else {
            val finish = System.currentTimeMillis() + remaining
            if (reportedFinish.let { it == null || kotlin.math.abs(it - finish) > 2_000 }) reportedFinish = finish
        }

        val step = rs?.step as? mcc.recipe.Step.Machine
        val total = when {
            (rs?.phase == RecipeRunner.Phase.RUNNING || rs?.phase == RecipeRunner.Phase.PAUSED) && step != null -> step.segments.sumOf { it.seconds }
            s?.running == true -> ((MachineService.resumedTotalMillis ?: s.request?.durationMillis ?: 0L) / 1000).toInt()
            MachineService.pausedRun != null -> (MachineService.pausedRun!!.totalMillis / 1000).toInt()
            else -> null
        }
        val pm = MachineService.remotePermit
        return MachineState(
            status = st,
            recipe = recipe?.title,
            step = if (recipe != null && rs != null) "${rs.stepIndex + 1}/${recipe.steps.size}" else null,
            stepText = rs?.step?.text?.take(200)?.ifBlank { null },
            temperature = f?.temperatureStepC,
            targetTemperature = if (s?.running == true) s.request?.temperatureC ?: 0 else 0,
            speed = if (s?.running == true) s.request?.speedLevel ?: 0 else 0,
            reverse = if (s?.running == true && s.request?.reverse == true) "on" else "off",
            finish = reportedFinish?.let { iso.format(Date(it)) },
            weight = reportedWeight,
            lid = if (f?.lidOpen == true) "open" else "closed",
            error = f?.error?.name?.lowercase() ?: "none",
            remoteStart = if (pm != null) "ready" else "off",
            remoteProgram = pm?.label,
            remoteUntil = pm?.let { iso.format(Date(it.untilWall)) },
            recipeId = recipe?.id,
            servings = recipe?.servings,
            runTotal = total,
            // whole seconds, so the payload only changes once a second
            // while paused the time left stays put (and finish is null)
            runLeft = (remaining ?: MachineService.pausedRun?.request?.durationMillis
                ?: rs?.takeIf { it.phase == RecipeRunner.Phase.PAUSED }?.remainingMillis)?.let { ((it + 999) / 1000).toInt() },
        )
    }

    /** A URL Home Assistant can fetch: remote images as they are, repository paths joined to the repository URL. */
    private fun imageUrl(image: String?): String? = when {
        image.isNullOrBlank() || image.startsWith("asset:") -> null
        image.startsWith("http://") || image.startsWith("https://") -> image
        prefs.repoUrl.isBlank() -> null
        else -> prefs.repoUrl.trimEnd('/') + "/" + image.trimStart('/')
    }

    private fun publishDiscovery(c: MqttClient) {
        val cfg = Discovery.config(prefs.haArea, BuildConfig.VERSION_NAME)
        c.publish(DISCOVERY, MqttMessage(cfg.toString().toByteArray()).apply { qos = 1; isRetained = true })
    }
}
