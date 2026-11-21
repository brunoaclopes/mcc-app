package mcc.phone

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Foreground service that keeps the MQTT link open and shows the live notification. */
class LinkService : Service() {
    companion object {
        /** The user swiped the idle notification away (Android 14+ allows it); cleared when cooking starts. */
        @Volatile var idleHidden = false
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        val app = PhoneApp.app
        val first = app.notifier.live(null, LinkClient.conn.value, null, app.prefs.live)
        if (Build.VERSION.SDK_INT >= 34) startForeground(Notifier.LIVE_ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(Notifier.LIVE_ID, first)
        LinkClient.start(app.prefs)
        val nm = getSystemService(android.app.NotificationManager::class.java)
        // before 0.3.7 each event kind had its own slot (11..17): clear any left over, alerts now share one
        (11..17).forEach { nm.cancel(it) }
        scope.launch {
            var shown: String? = null
            combine(LinkClient.state, LinkClient.conn, LinkClient.available) { s, c, a -> Triple(s, c, a) }.collect { (s, c, a) ->
                val cooking = app.notifier.isCooking(s, c, a, app.prefs.live)
                if (cooking) idleHidden = false
                // swiped away while idle: stay hidden until cooking starts (the connection keeps running)
                if (idleHidden) { shown = null; return@collect }
                val key = if (cooking) s?.encode() else app.notifier.statusLine(s, c, a)
                if (key == shown) return@collect
                shown = key
                nm.notify(Notifier.LIVE_ID, app.notifier.live(s, c, a, app.prefs.live))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        LinkClient.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
