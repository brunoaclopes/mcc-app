@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package mcc.phone.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import mcc.app.i18n.L
import mcc.app.ui.ClockRing
import mcc.app.ui.HoldButton
import mcc.app.ui.Icon
import mcc.app.ui.T
import mcc.link.Command
import mcc.link.MachineState
import mcc.link.Status
import mcc.phone.LinkClient
import mcc.phone.P
import mcc.phone.PhoneApp
import mcc.phone.Recipes
import mcc.recipe.Recipe
import mcc.recipe.Step
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
fun parseIso(s: String?): Date? = s?.let { runCatching { iso.parse(it) }.getOrNull() }
fun hhmm(d: Date): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(d)
fun fmtLeft(sec: Int): String = if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, sec / 60 % 60, sec % 60) else "%d:%02d".format(sec / 60, sec % 60)
private fun Color.argb() = android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

@Composable
fun Glass(modifier: Modifier = Modifier, accent: Color? = null, content: @Composable () -> Unit) {
    val shape = smooth(26.dp)
    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(T.SURFACE).copy(alpha = if (T.dark) 0.86f else 0.9f), Color(T.SURFACE_2).copy(alpha = 0.86f))))
            .border(1.dp, (accent ?: Color(T.RIM_TOP)).copy(alpha = if (accent != null) 0.55f else 1f), shape)
            .padding(18.dp)
    ) { content() }
}

@Composable
fun Label(text: String, color: Color = Mc.text2, icon: Icon? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { McIcon(icon, color, 16.dp); Spacer(Modifier.width(7.dp)) }
        Text(text.uppercase(L.s.locale), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** The machine state and whether the phone has a live view of it. */
@Composable
fun rememberOnline(): Pair<MachineState?, Boolean> {
    val st by LinkClient.state.collectAsState()
    val conn by LinkClient.conn.collectAsState()
    val avail by LinkClient.available.collectAsState()
    return st to (conn == LinkClient.Conn.Connected && avail != "offline" && st != null && st?.status != Status.DISCONNECTED)
}

/** The recipe open on the machine (from the repository), or null. */
@Composable
fun rememberLoadedRecipe(id: String?): Recipe? {
    val known = Recipes.state.collectAsState().value.index.size
    return produceState<Recipe?>(null, id, known) { value = id?.let { Recipes.byId(it) } }.value
}

/** Live machine view. On the folded / small phone it is the whole screen; unfolded it sits next to the recipe. */
@Composable
fun MachineScreen(modifier: Modifier, onOpenRecipe: ((String) -> Unit)? = null, onCook: ((String) -> Unit)? = null) {
    val (st, online) = rememberOnline()
    val conn by LinkClient.conn.collectAsState()
    val avail by LinkClient.available.collectAsState()
    val s = st ?: MachineState()
    val recipe = rememberLoadedRecipe(if (online) s.recipeId else null)
    val stepIdx = s.step?.substringBefore("/")?.toIntOrNull()?.minus(1)
    val weighTarget = (recipe?.steps?.getOrNull(stepIdx ?: -1) as? Step.Weigh)?.grams
    val tabletop = rememberTabletop()

    if (tabletop) {
        // Flex mode: status on the upper half, gauges and buttons on the lower half (the hinge in between)
        Column(modifier.padding(16.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { StatusCard(s, online, big = true) }
            Spacer(Modifier.height(28.dp))
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Gauges(s, online, weighTarget, Modifier.weight(1f))
                Controls(s, online)
            }
        }
        return
    }

    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Header(PhoneApp.app.notifier.statusLine(st, conn, avail), online, conn)
        StatusCard(s, online)
        AnimatedVisibility(online && s.recipe != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            CookingCard(s, recipe, onOpenRecipe, onCook)
        }
        Gauges(s, online, weighTarget, Modifier)
        AnimatedVisibility(online && s.remoteReady) { RemoteCard(s) }
        Controls(s, online)
        Text(P.s.safetyNote, style = MaterialTheme.typography.bodySmall, color = Mc.text3)
    }
}

@Composable
private fun Header(status: String, online: Boolean, conn: LinkClient.Conn) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(greeting(), style = MaterialTheme.typography.titleMedium, color = Mc.amber)
            Text("Monsieur Cuisine", style = MaterialTheme.typography.headlineMedium, color = Mc.text)
            Text(status, style = MaterialTheme.typography.bodyMedium, color = Mc.text2, maxLines = 2)
        }
        ConnDot(online, conn)
    }
}

@Composable
private fun ConnDot(online: Boolean, conn: LinkClient.Conn) {
    val col by animateColorAsState(when { online -> Mc.ok; conn is LinkClient.Conn.Failed -> Mc.danger; else -> Mc.amber }, label = "dot")
    Box(Modifier.size(28.dp).pulse(col, online), contentAlignment = Alignment.Center) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(col))
    }
}

@Composable
private fun StatusCard(s: MachineState, online: Boolean, big: Boolean = false) {
    val S = P.s
    val notifier = PhoneApp.app.notifier
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000L - now % 1000L) } }
    val hot = s.targetTemperature > 0
    val idleOk = online && !s.running && !s.hasError && !s.lidOpen && s.status != Status.FINISHED && s.status != Status.PAUSED
    val col by animateColorAsState(when { idleOk -> Mc.ok; online && s.lidOpen && !s.running -> Mc.amber; else -> mood(s, online) }, label = "status")
    Glass(Modifier.fillMaxWidth(), accent = if (s.running && online) col else null) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label(L.s.machine)
                    Spacer(Modifier.height(8.dp))
                    Text(if (online) notifier.statusWord(s.status) else S.disconnected, color = col, fontSize = if (big) 44.sp else 34.sp,
                        style = MaterialTheme.typography.displaySmall, maxLines = 1)
                    val detail = when {
                        !online -> ""
                        s.hasError -> L.s.boardError(runCatching { mcc.machine.BoardError.valueOf(s.error.uppercase()) }.getOrNull())
                        s.running -> notifier.detail(s)
                        s.lidOpen -> L.s.lidOpen
                        s.status == Status.FINISHED -> L.s.finishedEnjoy
                        else -> S.idleDetail
                    }
                    if (detail.isNotEmpty()) Text(detail, color = Mc.text2, style = MaterialTheme.typography.bodyLarge)
                    parseIso(s.finish)?.let { if (s.running) Text(S.readyAt(hhmm(it)), color = Mc.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp)) }
                }
                // the machine's own clock ring (shared view from :common)
                AndroidView(factory = { ClockRing(it) }, modifier = Modifier.size(if (big) 168.dp else 132.dp), update = { r ->
                    val c = java.util.Calendar.getInstance().apply { timeInMillis = now }
                    if (online && s.running && s.runLeft != null) {
                        r.value = fmtLeft(s.runLeft!!); r.unit = L.s.left
                        r.fraction = s.runTotal?.takeIf { it > 0 }?.let { 1f - s.runLeft!! / it.toFloat() } ?: 0f
                        r.colorA = if (hot) T.AMBER else T.TEAL; r.colorB = if (hot) T.EMBER_HOT else T.TEAL_DEEP
                    } else if (online && s.running && s.runElapsed != null) {
                        // a manual run without a set time counts up (stopwatch)
                        r.value = fmtLeft(s.runElapsed!!); r.unit = L.s.elapsed
                        r.fraction = (s.runElapsed!! % 60) / 60f
                        r.colorA = if (hot) T.AMBER else T.TEAL; r.colorB = if (hot) T.EMBER_HOT else T.TEAL_DEEP
                    } else {
                        r.value = "%02d:%02d".format(c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
                        r.unit = SimpleDateFormat("EEE d MMM", L.s.locale).format(c.time)
                        r.fraction = c.get(java.util.Calendar.SECOND) / 60f
                        r.colorA = T.lerpColor(col.argb(), T.TEXT, 0.15f); r.colorB = col.argb()
                    }
                })
            }
            if (online && s.running && (s.runTotal ?: 0) > 0) {
                Spacer(Modifier.height(14.dp))
                // Material 3 Expressive wavy progress: it ripples while the machine works
                LinearWavyProgressIndicator(progress = { 1f - (s.runLeft ?: 0) / s.runTotal!!.toFloat() }, color = col, trackColor = Mc.surface3,
                    modifier = Modifier.fillMaxWidth().height(10.dp))
            }
            if (online) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s.running) Chip(Icon.FAN, if (s.speed > 0) L.s.speedN(s.speed) + if (s.reversed) " · ${L.s.revShort}" else "" else L.s.stirringOff, Mc.teal)
                    Chip(Icon.LID, if (s.lidOpen) L.s.lidShortOpen else L.s.lidShortClosed, if (s.lidOpen) Mc.amber else Mc.text2)
                }
            }
        }
    }
}

/** The recipe on the machine, on its own photo. */
@Composable
private fun CookingCard(s: MachineState, recipe: Recipe?, onOpenRecipe: ((String) -> Unit)?, onCook: ((String) -> Unit)?) {
    val S = P.s
    val step = s.step?.split("/")?.mapNotNull { it.toIntOrNull() }
    val shape = smooth(26.dp)
    Box(
        Modifier.fillMaxWidth().clip(shape)
            .let { m -> if (onOpenRecipe != null && s.recipeId != null) m.clickable { onOpenRecipe(s.recipeId!!) } else m }
    ) {
        if (recipe != null) Cover(recipe.id, recipe.title, recipe.tags, recipe.image, 900, Modifier.matchParentSize())
        else Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(Color(0xFF8A3F12), Color(0xFF3A1406)))))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color(0x55000000), 1f to Color(0xE6000000))))
        Column(Modifier.padding(18.dp).padding(top = 56.dp)) {
            Text((if (step != null && step.size == 2) L.s.stepOf(step[0], step[1]) else S.whatsCooking).uppercase(L.s.locale),
                style = MaterialTheme.typography.labelMedium, color = Color(0xFFFFC27A))
            Spacer(Modifier.height(4.dp))
            Text(s.recipe ?: "", style = MaterialTheme.typography.headlineSmall, color = Mc.onPhoto, maxLines = 2, overflow = TextOverflow.Ellipsis)
            s.stepText?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Mc.onPhoto.copy(alpha = 0.85f), modifier = Modifier.padding(top = 6.dp), maxLines = 3) }
            if (step != null && step.size == 2 && step[1] > 0) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(step[1]) { i ->
                        Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(when {
                            i < step[0] - 1 -> Color(0xFF5ED6C2); i == step[0] - 1 -> Color(0xFFFFB347); else -> Color(0x40FFFFFF) }))
                    }
                }
            }
            if (s.recipeId != null) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onCook != null) Button(onClick = { onCook(s.recipeId!!) }, shape = smooth(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF), contentColor = Mc.onPhoto)) {
                        McIcon(Icon.CHEF, Mc.onPhoto, 18.dp); Spacer(Modifier.width(8.dp)); Text(S.cookMode)
                    }
                    CancelRecipeButton(onPhoto = true)
                }
            }
        }
    }
}

/** Temperature and scale gauges side by side; the scale fills towards the target during a weighing step. */
@Composable
private fun Gauges(s: MachineState, online: Boolean, weighTarget: Int?, modifier: Modifier) {
    val S = P.s
    val haptic = rememberHaptic()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val t = s.temperature
        val tCol = when { !online || t == null -> Mc.text3; t >= 90 -> Mc.emberHot; t >= 60 -> Mc.ember; t > 37 -> Mc.amber; else -> Mc.teal }
        GaugeCard(S.temperature, Icon.THERMO, tCol, if (online && t != null) ((t - 37) / (130f - 37f)) else 0f,
            if (online && t != null) (if (t <= 37) "≤37" else "$t") else "—", "°C",
            if (online && s.targetTemperature > 0) "→ ${s.targetTemperature} °C" else null, Modifier.weight(1f))
        val w = s.weight
        val frac = if (weighTarget != null && w != null) w / weighTarget.toFloat() else 0f
        val wCol = if (weighTarget != null && frac >= 0.95f) Mc.ok else Mc.teal
        GaugeCard(S.scale, Icon.SCALE, wCol, frac, if (online && w != null) "$w" else "—", "g",
            weighTarget?.let { L.s.weighGrams(it) }, Modifier.weight(1f),
            action = if (online && !s.running) ({ haptic(false); LinkClient.send(Command.Tare) }) else null)
    }
}

@Composable
private fun GaugeCard(title: String, icon: Icon, color: Color, fraction: Float, value: String, unit: String, sub: String?, modifier: Modifier, action: (() -> Unit)? = null) {
    Glass(modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Label(title, color, icon) }
                if (action != null) Text(L.s.tare, style = MaterialTheme.typography.labelLarge, color = Mc.teal,
                    modifier = Modifier.clip(smooth(10.dp)).background(Mc.teal.copy(alpha = 0.14f)).clickable(onClick = action).padding(horizontal = 10.dp, vertical = 5.dp))
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(0.82f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                Gauge(fraction, color, Modifier.fillMaxSize(), 11.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Rolling(value, MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold), Mc.text)
                    Text(unit, style = MaterialTheme.typography.labelLarge, color = Mc.text2)
                }
            }
            Text(sub ?: " ", style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
        }
    }
}

@Composable
private fun Controls(s: MachineState, online: Boolean) {
    val S = P.s
    val ui = LocalUi.current
    val haptic = rememberHaptic()
    // Stop always first and red while something runs or waits
    val active = online && (s.running || s.status == Status.PAUSED)
    val stopCol by animateColorAsState(if (active) Mc.danger else Mc.surface3, label = "stop")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = { haptic(true); if (LinkClient.send(Command.Stop)) ui.say(S.stopSent) else ui.say(S.connecting) },
            enabled = online, modifier = Modifier.weight(1.3f).height(64.dp), shape = smooth(22.dp),
            colors = ButtonDefaults.buttonColors(containerColor = stopCol, contentColor = if (active) Color.White else Mc.text),
        ) { McIcon(Icon.STOP, if (active) Color.White else Mc.text, 20.dp); Spacer(Modifier.width(8.dp)); Text(L.s.stop, fontSize = 18.sp) }
        Button(
            onClick = { haptic(false); if (LinkClient.send(Command.Pause)) ui.say(S.pauseSent) },
            enabled = online && s.running, modifier = Modifier.weight(1f).height(64.dp), shape = smooth(22.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Mc.surface3, contentColor = Mc.text),
        ) { McIcon(Icon.PAUSE, Mc.text, 20.dp); Spacer(Modifier.width(8.dp)); Text(L.s.pause) }
    }
}

@Composable
fun Chip(icon: Icon, text: String, color: Color, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.clip(smooth(14.dp)).background(Mc.surface3.copy(alpha = 0.7f))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        McIcon(icon, color, 16.dp)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = Mc.text)
    }
}

@Composable
private fun RemoteCard(s: MachineState) {
    val S = P.s
    val ui = LocalUi.current
    Glass(Modifier.fillMaxWidth(), accent = Mc.teal) {
        Column {
            Label(S.remoteReady, Mc.teal, Icon.WIFI)
            Spacer(Modifier.height(6.dp))
            Text(s.remoteProgram ?: "", style = MaterialTheme.typography.titleMedium, color = Mc.text)
            parseIso(s.remoteUntil)?.let { Text(S.remoteUntil(hhmm(it)), style = MaterialTheme.typography.bodyMedium, color = Mc.text2) }
            Spacer(Modifier.height(12.dp))
            // the machine's own press-and-hold button (shared view): a deliberate gesture for anything that moves
            AndroidView(factory = { c ->
                HoldButton(c).apply {
                    label = S.holdToStart; icon = Icon.PLAY; holdMillis = 900; style(T.TEAL, T.TEAL_DEEP)
                    onConfirm = { if (!LinkClient.send(Command.Start)) ui.say(S.connecting) }
                }
            }, modifier = Modifier.fillMaxWidth().height(58.dp))
        }
    }
}

/** Cancels the recipe loaded on the machine, after a confirmation. A pill like the other card buttons. */
@Composable
fun CancelRecipeButton(onPhoto: Boolean, modifier: Modifier = Modifier) {
    val S = P.s
    var ask by remember { mutableStateOf(false) }
    val fg = if (onPhoto) Mc.onPhoto else Mc.danger
    Button(onClick = { ask = true }, modifier = modifier, shape = smooth(16.dp),
        contentPadding = PaddingValues(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (onPhoto) Color(0x33FFFFFF) else Mc.danger.copy(alpha = 0.12f), contentColor = fg)) {
        McIcon(Icon.CLOSE, fg, 16.dp, 2.4f); Spacer(Modifier.width(8.dp)); Text(S.cancelRecipe)
    }
    if (ask) androidx.compose.material3.AlertDialog(
        onDismissRequest = { ask = false },
        title = { Text(S.cancelRecipeTitle) }, text = { Text(S.cancelRecipeText) },
        confirmButton = { TextButton(onClick = { ask = false; mcc.phone.LinkClient.send(mcc.link.Command.Cancel) }) { Text(S.cancelRecipe, color = Mc.danger) } },
        dismissButton = { TextButton(onClick = { ask = false }) { Text(S.keepRecipe) } },
    )
}
