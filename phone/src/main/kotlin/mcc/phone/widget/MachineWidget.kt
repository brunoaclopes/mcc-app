package mcc.phone.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import mcc.app.i18n.L
import mcc.link.Status
import mcc.phone.ActionReceiver
import mcc.phone.MainActivity
import mcc.phone.P
import mcc.phone.PhoneApp
import mcc.phone.R
import mcc.phone.Surfaces
import mcc.phone.ui.hhmm
import mcc.phone.ui.parseIso

/** Home-screen widget: what the machine is doing, when it will be ready, and a Stop button while it runs. */
class MachineWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Content(context) }
    }

    @Composable
    private fun Content(c: Context) {
        val g = Surfaces.now(c)
        val s = g.state
        val n = PhoneApp.app.notifier
        val running = g.online && s?.running == true
        // light/dark like the app: warm cream by day, deep brown at night
        val bg = ColorProvider(day = Color(0xFFFBF3EA), night = Color(0xFF231B16))
        val text = ColorProvider(day = Color(0xFF2B211B), night = Color(0xFFF6ECE3))
        val text2 = ColorProvider(day = Color(0xFF7A6A5E), night = Color(0xFFB9A89A))
        val accent = when {
            !g.online -> Color(0xFF9A8B80)
            s?.hasError == true -> Color(0xFFE5484D)
            running && (s?.targetTemperature ?: 0) > 0 -> Color(0xFFE8761C)
            running -> Color(0xFF1FA394)
            s?.status == Status.FINISHED -> Color(0xFF2FA35A)
            else -> Color(0xFFE09A2C)
        }
        Column(GlanceModifier.fillMaxSize().background(bg).cornerRadius(26.dp).padding(16.dp)
            .clickable(actionStartActivity(Intent(c, MainActivity::class.java).putExtra("tab", "machine")))) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                Box(GlanceModifier.size(34.dp).background(ColorProvider(accent, accent)).cornerRadius(17.dp), contentAlignment = Alignment.Center) {
                    Image(ImageProvider(R.drawable.ic_stat), null, GlanceModifier.size(20.dp))
                }
                Spacer(GlanceModifier.width(10.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text("Monsieur Cuisine", style = TextStyle(color = text2, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                    Text(if (!g.online) P.s.disconnected else n.statusWord(s?.status ?: Status.IDLE),
                        style = TextStyle(color = ColorProvider(accent, accent), fontSize = 20.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
            }
            Spacer(GlanceModifier.height(8.dp))
            val line1 = when { !g.online -> null; s?.recipe != null -> s.recipe; running -> n.detail(s!!); else -> P.s.nothingCooking }
            line1?.let { Text(it, style = TextStyle(color = text, fontSize = 15.sp, fontWeight = FontWeight.Medium), maxLines = 2) }
            val line2 = when {
                running -> parseIso(s?.finish)?.let { P.s.readyAt(hhmm(it)) }
                g.online && s?.step != null -> s.step!!.split("/").mapNotNull { it.toIntOrNull() }.takeIf { it.size == 2 }?.let { L.s.stepOf(it[0], it[1]) }
                else -> null
            }
            line2?.let { Text(it, style = TextStyle(color = text2, fontSize = 13.sp), maxLines = 1) }
            if (running) {
                Spacer(GlanceModifier.defaultWeight())
                Box(GlanceModifier.fillMaxWidth().height(40.dp).background(ColorProvider(Color(0xFFE5484D), Color(0xFFE5484D))).cornerRadius(20.dp)
                    .clickable(actionSendBroadcast(Intent(c, ActionReceiver::class.java).setAction(ActionReceiver.STOP))), contentAlignment = Alignment.Center) {
                    Text(L.s.stop, style = TextStyle(color = ColorProvider(Color.White, Color.White), fontSize = 15.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

class MachineWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MachineWidget()
}
