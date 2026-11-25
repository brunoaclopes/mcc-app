package mcc.app

import android.content.Context
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log

/**
 * Brings Wi-Fi back by itself. On this tablet (Android 6, MediaTek) a lost IP configuration drops the connection, and
 * with the screen off the vendor's Wi-Fi code stops trying to reconnect after 12 minutes (`SLEEP_POLICY_STOP_SCAN`),
 * whatever the sleep policy says: Home Assistant and the phone lost the machine until someone touched it. Every
 * [CHECK_MS] this checks the link on its own thread (never on the serial loop) and, after [GRACE_MS] without a
 * connection, asks for a scan and a reconnect, at most once a minute.
 */
class WifiWatchdog(context: Context) {
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val thread = HandlerThread("mcc-wifi").apply { start() }
    private val handler = Handler(thread.looper)
    private var downSince = 0L
    private var lastKick = 0L
    /** How many reconnects were asked for (shown in the log; useful when looking at a dropout afterwards). */
    @Volatile var kicks = 0
        private set

    private val check = object : Runnable {
        override fun run() {
            try { tick(SystemClock.elapsedRealtime()) } catch (e: Exception) { Log.w(MachineService.TAG, "wifi watchdog", e) }
            handler.postDelayed(this, CHECK_MS)
        }
    }

    fun start() { handler.removeCallbacks(check); handler.postDelayed(check, CHECK_MS) }

    fun stop() { handler.removeCallbacksAndMessages(null); thread.quitSafely() }

    private fun tick(now: Long) {
        if (!wifi.isWifiEnabled) { downSince = 0; return }   // switched off on purpose: leave it alone
        val info = wifi.connectionInfo
        val connected = info != null && info.supplicantState == SupplicantState.COMPLETED && info.ipAddress != 0
        if (connected) {
            if (downSince != 0L) Log.i(MachineService.TAG, "wifi back after ${(now - downSince) / 1000} s")
            downSince = 0
            return
        }
        if (downSince == 0L) { downSince = now; return }
        if (now - downSince < GRACE_MS || now - lastKick < KICK_EVERY_MS) return
        lastKick = now
        kicks++
        Log.w(MachineService.TAG, "wifi down for ${(now - downSince) / 1000} s (${info?.supplicantState}): scan and reconnect")
        wifi.startScan()
        if (!wifi.reconnect()) wifi.reassociate()
    }

    companion object {
        const val CHECK_MS = 15_000L
        const val GRACE_MS = 45_000L
        const val KICK_EVERY_MS = 60_000L
    }
}
