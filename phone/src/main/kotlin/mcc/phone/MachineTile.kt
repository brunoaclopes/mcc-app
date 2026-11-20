package mcc.phone

import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import mcc.link.Command
import mcc.phone.ui.hhmm
import mcc.phone.ui.parseIso

/** Quick Settings tile: the machine's state; tap while it runs to stop it (after a confirmation), otherwise opens the app. */
class MachineTile : TileService() {
    override fun onStartListening() { refresh() }

    private fun refresh() {
        val t = qsTile ?: return
        val g = Surfaces.now(this)
        val s = g.state
        val n = PhoneApp.app.notifier
        t.label = getString(R.string.tile_label)
        t.icon = Icon.createWithResource(this, R.drawable.ic_stat)
        t.state = when { !g.online -> Tile.STATE_UNAVAILABLE; s?.running == true -> Tile.STATE_ACTIVE; else -> Tile.STATE_INACTIVE }
        if (Build.VERSION.SDK_INT >= 29) t.subtitle = when {
            !g.online -> P.s.disconnected
            s == null -> P.s.idle
            s.running -> parseIso(s.finish)?.let { P.s.readyAt(hhmm(it)) } ?: n.statusWord(s.status)
            else -> n.statusWord(s.status)
        }
        t.updateTile()
    }

    override fun onClick() {
        val g = Surfaces.now(this)
        if (g.online && g.state?.running == true) {
            showDialog(AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(P.s.stopConfirm).setMessage(P.s.stopConfirmText)
                .setPositiveButton(mcc.app.i18n.L.s.stop) { _, _ -> LinkClient.send(Command.Stop); refresh() }
                .setNegativeButton(P.s.cancel, null).create())
            return
        }
        val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("tab", "machine")
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 3, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        else @Suppress("DEPRECATION") startActivityAndCollapse(i)
    }
}
