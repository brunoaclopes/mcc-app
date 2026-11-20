package mcc.phone

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import mcc.app.i18n.L
import mcc.link.MachineEvent
import mcc.link.MachineState
import mcc.link.Status

/** Builds the two kinds of notification: the quiet ongoing one (live progress) and alerts for machine events. */
class Notifier(private val c: Context) {
    companion object {
        /** Cooking progress: visible (status bar icon, lock screen) but silent. */
        const val LIVE = "cooking"
        /** "Connected" while idle: minimised. */
        const val LINK = "link"
        const val ALERTS = "alerts"
        const val LIVE_ID = 1
        private const val PROMOTED = "android.requestPromotedOngoing" // Notification.EXTRA_REQUEST_PROMOTED_ONGOING (API 36)
    }

    private val nm = c.getSystemService(NotificationManager::class.java)
    private val S get() = P.s

    fun channels() {
        nm.createNotificationChannel(NotificationChannel(LIVE, S.channelLive, NotificationManager.IMPORTANCE_DEFAULT).apply {
            setShowBadge(false); setSound(null, null); enableVibration(false)
        })
        nm.createNotificationChannel(NotificationChannel(LINK, S.channelLink, NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) })
        nm.createNotificationChannel(NotificationChannel(ALERTS, S.channelAlerts, NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true); vibrationPattern = longArrayOf(0, 250, 120, 250)
        })
    }

    private fun open(): PendingIntent = PendingIntent.getActivity(c, 0,
        Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun action(what: String, code: Int): PendingIntent = PendingIntent.getBroadcast(c, code,
        Intent(c, ActionReceiver::class.java).setAction(what), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** The foreground-service notification: live progress while cooking, a quiet status line otherwise. */
    fun live(st: MachineState?, conn: LinkClient.Conn, available: String?, liveOn: Boolean): Notification {
        val running = st != null && st.running && available != "offline" && conn == LinkClient.Conn.Connected
        if (running && liveOn && android.os.Build.VERSION.SDK_INT >= 36) return liveUpdate(st!!)
        val b = NotificationCompat.Builder(c, if (running && liveOn) LIVE else LINK)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentIntent(open())
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setColor(0xFFE8761C.toInt())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (running && liveOn) {
            val total = st!!.runTotal ?: 0
            val left = st.runLeft ?: 0
            b.setContentTitle(st.recipe ?: S.manualRun)
                .setContentText(st.stepText ?: detail(st))
                .setSubText(st.step?.let { val (a, z) = it.split("/").map(String::toInt); L.s.stepOf(a, z) })
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .addAction(0, L.s.pause, action(ActionReceiver.PAUSE, 1))
                .addAction(0, L.s.stop, action(ActionReceiver.STOP, 2))
            if (total > 0) {
                b.setProgress(total, (total - left).coerceIn(0, total), false)
                b.setUsesChronometer(true).setChronometerCountDown(true).setShowWhen(true).setWhen(System.currentTimeMillis() + left * 1000L)
            }
            b.setStyle(NotificationCompat.BigTextStyle().bigText(st.stepText ?: detail(st)))
            b.extras.putBoolean(PROMOTED, true)
        } else {
            b.setContentTitle(c.getString(R.string.app_name))
                .setContentText(statusLine(st, conn, available))
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setShowWhen(false)
        }
        return b.build()
    }

    /**
     * Android 16 Live Update: promoted ongoing notification with ProgressStyle. One segment per recipe step (the
     * current one fills with the countdown), a time-left chip in the status bar, on the lock screen and, on One UI 8,
     * in the Now Bar.
     */
    @androidx.annotation.RequiresApi(36)
    private fun liveUpdate(st: MachineState): Notification {
        val total = st.runTotal ?: 0
        val left = st.runLeft ?: 0
        val step = st.step?.split("/")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 2 && it[1] > 0 }
        val hot = st.targetTemperature > 0
        val col = if (hot) 0xFFE8761C.toInt() else 0xFF1FA394.toInt()
        val unit = 1000
        val frac = if (total > 0) ((total - left).toFloat() / total).coerceIn(0f, 1f) else 0f
        val style = Notification.ProgressStyle().setStyledByProgress(true)
        if (step != null) {
            style.setProgressSegments((1..step[1]).map { Notification.ProgressStyle.Segment(unit).setColor(if (it == step[0]) col else 0xFF9A8B80.toInt()) })
            style.setProgress(((step[0] - 1) * unit + frac * unit).toInt())
            style.setProgressPoints((1 until step[1]).map { Notification.ProgressStyle.Point(it * unit).setColor(0xFFE09A2C.toInt()) })
        } else {
            style.setProgressSegments(listOf(Notification.ProgressStyle.Segment(unit).setColor(col)))
            style.setProgress((frac * unit).toInt())
        }
        style.setProgressTrackerIcon(android.graphics.drawable.Icon.createWithResource(c, R.drawable.ic_stat))
        val b = Notification.Builder(c, LIVE)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentIntent(open())
            .setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentTitle(st.recipe ?: S.manualRun)
            .setContentText(st.stepText ?: detail(st))
            .setSubText(step?.let { L.s.stepOf(it[0], it[1]) })
            .setStyle(style)
            .setRequestPromotedOngoing(true)
            .addAction(Notification.Action.Builder(null, L.s.pause, action(ActionReceiver.PAUSE, 1)).build())
            .addAction(Notification.Action.Builder(null, L.s.stop, action(ActionReceiver.STOP, 2)).build())
        if (total > 0) {
            // status-bar chip: "4:59"-style countdown
            b.setUsesChronometer(true).setChronometerCountDown(true).setShowWhen(true).setWhen(System.currentTimeMillis() + left * 1000L)
            b.setShortCriticalText(mcc.phone.ui.fmtLeft(left))
        } else b.setShortCriticalText(S.runningWord)
        return b.build()
    }

    fun statusLine(st: MachineState?, conn: LinkClient.Conn, available: String?): String = when {
        conn == LinkClient.Conn.Off && !PhoneApp.app.prefs.configured -> S.notConfigured
        conn is LinkClient.Conn.Failed -> S.brokerError(conn.message)
        conn != LinkClient.Conn.Connected -> S.connecting
        available == "offline" || st == null || st.status == Status.DISCONNECTED -> S.machineOffline
        else -> statusWord(st.status) + (st.recipe?.let { " · $it" } ?: "")
    }

    fun statusWord(status: String) = when (status) {
        Status.IDLE -> S.idle; Status.RUNNING -> S.runningWord; Status.PAUSED -> S.pausedWord
        Status.FINISHED -> S.finished; Status.ERROR -> S.errorWord; else -> S.disconnected
    }

    fun detail(st: MachineState): String = listOfNotNull(
        if (st.speed > 0) L.s.speedN(st.speed) + if (st.reversed) " · ${L.s.revShort}" else "" else null,
        if (st.targetTemperature > 0) "${st.targetTemperature} °C" else null,
    ).joinToString(" · ").ifEmpty { S.runningWord }

    fun event(e: MachineEvent) {
        val p = PhoneApp.app.prefs
        val (title, text, on) = when (e.type) {
            MachineEvent.FINISHED -> Triple(S.evFinished, S.evFinishedText(e.recipe), p.notifyFinished)
            MachineEvent.NEEDS_YOU -> Triple(S.evNeedsYou, e.text ?: e.step?.let { val (a, z) = it.split("/").map(String::toInt); L.s.stepOf(a, z) } ?: "", p.notifyNeedsYou)
            MachineEvent.WEIGHED -> Triple(S.evWeighed, S.evWeighedText(e.grams), p.notifyWeighed)
            MachineEvent.RUN_DONE -> Triple(S.evRunDone, S.evRunDoneText, p.notifyFinished)
            MachineEvent.STOPPED -> Triple(S.evStopped, L.s.stopReason(runCatching { mcc.machine.StopReason.valueOf(e.reason!!.uppercase()) }.getOrNull()), p.notifyProblems)
            MachineEvent.ERROR -> Triple(S.evError, L.s.boardError(runCatching { mcc.machine.BoardError.valueOf(e.error!!.uppercase()) }.getOrNull()), p.notifyProblems)
            else -> return
        }
        if (!on) return
        if (c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val n = NotificationCompat.Builder(c, ALERTS)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(0xFFE8761C.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(e.recipe)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (e.type == MachineEvent.ERROR || e.type == MachineEvent.STOPPED) NotificationCompat.CATEGORY_ERROR else NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        // one notification per kind: a newer "needs you" replaces the older one
        nm.notify(10 + MachineEvent.TYPES.indexOf(e.type), n)
    }
}
