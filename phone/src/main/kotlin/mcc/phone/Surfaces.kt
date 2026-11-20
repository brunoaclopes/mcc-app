package mcc.phone

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.FlowPreview
import mcc.link.MachineState
import mcc.phone.widget.MachineWidget

/** What the widget and the tile show; the same rules as the app ("offline" unless the link and the machine are up). */
data class Glance(val state: MachineState?, val online: Boolean)

object Surfaces {
    fun now(c: Context): Glance {
        val online = LinkClient.conn.value == LinkClient.Conn.Connected && LinkClient.available.value != "offline" &&
            LinkClient.state.value?.status.let { it != null && it != mcc.link.Status.DISCONNECTED }
        val st = LinkClient.state.value ?: runCatching { MachineState.decode(PhoneApp.app.prefs.lastState) }.getOrNull()
        return Glance(st, online)
    }

    @OptIn(FlowPreview::class)
    suspend fun follow(c: Context) {
        combine(LinkClient.state, LinkClient.conn, LinkClient.available) { s, _, _ -> s }
            .debounce(400)
            // only what the widget/tile display: no redraw every second for the countdown
            .distinctUntilChanged { a, b -> key(a) == key(b) }
            .collect { s ->
                if (s != null) PhoneApp.app.prefs.lastState = s.encode()
                runCatching { MachineWidget().updateAll(c) }
                runCatching { TileService.requestListeningState(c, ComponentName(c, MachineTile::class.java)) }
            }
    }

    private fun key(s: MachineState?) = listOf(s?.status, s?.recipe, s?.step, s?.finish, s?.lidOpen, s?.error,
        LinkClient.conn.value.javaClass, LinkClient.available.value)
}
