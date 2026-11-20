package mcc.phone

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import mcc.link.Command
import mcc.link.MachineEvent
import mcc.link.MachineState
import mcc.link.Topics
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

/**
 * The phone's MQTT link: subscribes to the machine's retained state, its availability and its events, and sends
 * commands. One connection per process, owned by [LinkService]; the UI only reads the flows.
 */
object LinkClient {
    sealed class Conn {
        object Off : Conn()
        object Connecting : Conn()
        object Connected : Conn()
        data class Failed(val message: String) : Conn()
    }

    private const val TAG = "mcc-phone"
    private val _conn = MutableStateFlow<Conn>(Conn.Off)
    val conn: StateFlow<Conn> = _conn
    private val _state = MutableStateFlow<MachineState?>(null)
    @Volatile private var scale: Int? = null
    @Volatile private var scaleAt = -10_000L
    val state: StateFlow<MachineState?> = _state
    /** "online", "offline" or null (not heard yet). */
    private val _available = MutableStateFlow<String?>(null)
    val available: StateFlow<String?> = _available
    private val _events = MutableSharedFlow<MachineEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<MachineEvent> = _events

    @Volatile private var client: MqttClient? = null
    @Volatile private var thread: Thread? = null
    @Volatile private var running = false
    private val sender = java.util.concurrent.Executors.newSingleThreadExecutor()

    @Synchronized fun start(prefs: PhonePrefs) {
        if (running) return
        if (!prefs.configured) { _conn.value = Conn.Off; return }
        running = true
        thread = Thread({ loop(prefs) }, "mcc-phone-mqtt").also { it.isDaemon = true; it.start() }
    }

    @Synchronized fun stop() {
        running = false
        thread?.interrupt(); thread = null
        val c = client; client = null
        sender.execute { try { if (c?.isConnected == true) c.disconnect(1_000); c?.close() } catch (_: Exception) {} }
        _conn.value = Conn.Off
    }

    fun restart(prefs: PhonePrefs) { stop(); start(prefs) }

    /** Sends a command; false when not connected. */
    fun send(cmd: Command): Boolean {
        val c = client ?: return false
        if (!c.isConnected) return false
        sender.execute {
            try { c.publish(Topics.CMD, MqttMessage(cmd.encode().toByteArray()).apply { qos = 1; isRetained = false }) }
            catch (e: Exception) { Log.w(TAG, "send failed", e) }
        }
        return true
    }

    private fun loop(prefs: PhonePrefs) {
        var backoff = 2_000L
        while (running) {
            val c = client
            if (c == null || !c.isConnected) {
                try {
                    _conn.value = Conn.Connecting
                    connect(prefs)
                    _conn.value = Conn.Connected
                    backoff = 2_000L
                } catch (e: Exception) {
                    if (!running) return
                    Log.w(TAG, "connect failed: ${e.message}")
                    _conn.value = Conn.Failed(e.cause?.message ?: e.message ?: e.javaClass.simpleName)
                    try { Thread.sleep(backoff) } catch (_: InterruptedException) { return }
                    backoff = minOf(backoff * 2, 30_000L)
                    continue
                }
            }
            try { Thread.sleep(3_000) } catch (_: InterruptedException) { return }
        }
    }

    private fun connect(prefs: PhonePrefs) {
        client?.let { runCatching { it.close() } }
        val scheme = if (prefs.tls) "ssl" else "tcp"
        val c = MqttClient("$scheme://${prefs.host}:${prefs.port}", prefs.clientId, MemoryPersistence())
        val o = MqttConnectOptions().apply {
            isCleanSession = true
            connectionTimeout = 10
            keepAliveInterval = 60
            isAutomaticReconnect = false
            if (prefs.user.isNotBlank()) { userName = prefs.user; password = prefs.password.toCharArray() }
        }
        c.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) { if (running) _conn.value = Conn.Connecting }
            override fun messageArrived(topic: String, message: MqttMessage) = onMessage(topic, String(message.payload))
            override fun deliveryComplete(token: IMqttDeliveryToken?) {}
        })
        c.connect(o)
        client = c
        c.subscribe(arrayOf(Topics.STATE, Topics.AVAILABILITY, Topics.EVENT, Topics.SCALE), intArrayOf(1, 1, 1, 0))
    }

    private fun onMessage(topic: String, payload: String) {
        when (topic) {
            // the state's weight is calmed for Home Assistant; the live scale feed (newer machine app) wins while it flows
            Topics.STATE -> runCatching { MachineState.decode(payload) }
                .onSuccess { _state.value = if (android.os.SystemClock.elapsedRealtime() - scaleAt < 3_000) it.copy(weight = scale) else it }
                .onFailure { Log.w(TAG, "bad state: ${it.message}") }
            Topics.SCALE -> {
                scale = payload.trim().toIntOrNull(); scaleAt = android.os.SystemClock.elapsedRealtime()
                _state.value?.let { if (it.weight != scale) _state.value = it.copy(weight = scale) }
            }
            Topics.AVAILABILITY -> _available.value = payload.trim()
            Topics.EVENT -> runCatching { MachineEvent.decode(payload) }.onSuccess { _events.tryEmit(it) }
        }
    }
}
