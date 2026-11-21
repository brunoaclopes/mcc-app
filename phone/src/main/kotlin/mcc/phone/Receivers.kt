package mcc.phone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import mcc.link.Command

/** Pause / Stop buttons on the live notification. */
class ActionReceiver : BroadcastReceiver() {
    companion object {
        const val STOP = "mcc.phone.STOP"
        const val PAUSE = "mcc.phone.PAUSE"
        const val HIDE_IDLE = "mcc.phone.HIDE_IDLE"
    }

    override fun onReceive(c: Context, i: Intent) {
        if (i.action == HIDE_IDLE) { LinkService.idleHidden = true; return }
        val (cmd, ok) = when (i.action) {
            STOP -> Command.Stop to P.s.stopSent
            PAUSE -> Command.Pause to P.s.pauseSent
            else -> return
        }
        Toast.makeText(c, if (LinkClient.send(cmd)) ok else P.s.connecting, Toast.LENGTH_SHORT).show()
    }
}

/** Reconnects after a reboot or an app update, when background notifications are on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        // PhoneApp.onCreate already ran and called syncLink(); nothing else to do
        PhoneApp.app.syncLink()
    }
}
