@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package mcc.phone.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import mcc.app.i18n.L
import mcc.app.ui.Icon
import mcc.phone.BuildConfig
import mcc.phone.LinkClient
import mcc.phone.P
import mcc.phone.PhoneApp
import mcc.phone.Recipes
import kotlinx.coroutines.launch

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, hint: String? = null, password: Boolean = false, number: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        placeholder = hint?.let { { Text(it) } },
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = when { password -> KeyboardType.Password; number -> KeyboardType.Number; else -> KeyboardType.Uri }),
        shape = smooth(16.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Mc.amber, unfocusedBorderColor = Mc.stroke, focusedLabelColor = Mc.amber),
    )
}

@Composable
private fun Toggle(title: String, on: Boolean, help: String? = null, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Mc.text)
            help?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Mc.text2) }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = on, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Mc.teal, checkedThumbColor = Mc.onAccent))
    }
}

@Composable
fun SettingsScreen(modifier: Modifier) {
    val app = PhoneApp.app
    val p = app.prefs
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val S = P.s
    val conn by LinkClient.conn.collectAsState()
    val st by LinkClient.state.collectAsState()
    val avail by LinkClient.available.collectAsState()
    var host by remember { mutableStateOf(p.host) }
    var port by remember { mutableStateOf(p.port.toString()) }
    var user by remember { mutableStateOf(p.user) }
    var pass by remember { mutableStateOf(p.password) }
    var tls by remember { mutableStateOf(p.tls) }
    var repo by remember { mutableStateOf(p.repoUrl) }
    var online by remember { mutableStateOf(p.onlineRecipes) }
    var aiUrl by remember { mutableStateOf(p.aiUrl) }
    var aiModel by remember { mutableStateOf(p.aiModel) }
    var aiKey by remember { mutableStateOf(p.aiKey) }
    var pubUrl by remember { mutableStateOf(p.publishUrl) }
    var pubToken by remember { mutableStateOf(p.publishToken) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var bg by remember { mutableStateOf(p.background) }
    var live by remember { mutableStateOf(p.live) }
    var nNeeds by remember { mutableStateOf(p.notifyNeedsYou) }
    var nDone by remember { mutableStateOf(p.notifyFinished) }
    var nWeigh by remember { mutableStateOf(p.notifyWeighed) }
    var nProb by remember { mutableStateOf(p.notifyProblems) }
    var lang by remember { mutableStateOf(p.language) }
    val pm = ctx.getSystemService(PowerManager::class.java)
    var batteryOk by remember { mutableStateOf(pm.isIgnoringBatteryOptimizations(ctx.packageName)) }

    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(L.s.settingsTitle, style = MaterialTheme.typography.headlineMedium, color = Mc.text)

        Glass(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Label(S.connection, Mc.teal, Icon.WIFI)
                Text(app.notifier.statusLine(st, conn, avail), style = MaterialTheme.typography.bodyMedium,
                    color = if (conn == LinkClient.Conn.Connected && avail != "offline") Mc.ok else Mc.text2)
                Field(L.s.broker, host, { host = it }, "homeassistant.local")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) { Field(L.s.port, port, { port = it.filter(Char::isDigit).take(5) }, number = true) }
                    Column(Modifier.weight(2f)) { Field(L.s.user, user, { user = it }) }
                }
                Field(L.s.password, pass, { pass = it }, password = true)
                Toggle(S.tls, tls) { tls = it; if (it && port == "1883") port = "8883" else if (!it && port == "8883") port = "1883" }
                Button(onClick = {
                    p.host = host; p.port = port.toIntOrNull() ?: 1883; p.user = user; p.password = pass; p.tls = tls
                    LinkClient.stop(); app.syncLink(); ui.say(S.saved)
                }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = smooth(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Mc.ember, contentColor = Mc.onAccent)) { Text(S.saveConnect) }
            }
        }

        Glass(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Label(L.s.recipeServer, Mc.amber, Icon.BOOK)
                Text(S.repoHint, style = MaterialTheme.typography.bodySmall, color = Mc.text2)
                Field(L.s.address, repo, { repo = it }, "https://…")
                FilledTonalButton(onClick = { p.repoUrl = repo; Recipes.refresh(force = true); ui.say(S.saved) },
                    modifier = Modifier.fillMaxWidth(), shape = smooth(16.dp)) { Text(L.s.saveSync) }
                Toggle(L.s.onlineSetting, online, L.s.onlineHelp) { online = it; p.onlineRecipes = it; Recipes.refresh(force = true) }
            }
        }

        Glass(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Label(S.importSettings, Mc.amber, Icon.SPARK)
                Text(S.importSettingsHelp, style = MaterialTheme.typography.bodySmall, color = Mc.text2)
                Field(L.s.aiAddress, aiUrl, { aiUrl = it }, "http://nas.local:8080/v1")
                Field(L.s.aiModel, aiModel, { aiModel = it }, "gpt-4o-mini")
                Field(L.s.aiKey, aiKey, { aiKey = it }, password = true)
                Field(S.publishAddress, pubUrl, { pubUrl = it }, "http://nas.local:8090/")
                Field(S.publishToken, pubToken, { pubToken = it }, password = true)
                FilledTonalButton(onClick = {
                    p.aiUrl = aiUrl; p.aiModel = aiModel; p.aiKey = aiKey; p.publishUrl = pubUrl; p.publishToken = pubToken
                    if (aiUrl.isBlank()) ui.say(S.saved) else scope.launch {
                        val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            runCatching { mcc.app.AiClient(aiUrl, aiModel, aiKey).models() }
                        }
                        ui.say(r.fold({ L.s.aiOk(it.size) }, { L.s.aiFailed(it.message ?: "") }))
                    }
                }, modifier = Modifier.fillMaxWidth(), shape = smooth(16.dp)) { Text(S.testAi) }
            }
        }

        Glass(Modifier.fillMaxWidth()) {
            Column {
                Label(S.notifications, Mc.ember, Icon.SPARK)
                Spacer(Modifier.height(6.dp))
                Toggle(S.notifNeedsYou, nNeeds) { nNeeds = it; p.notifyNeedsYou = it }
                Toggle(S.notifFinished, nDone) { nDone = it; p.notifyFinished = it }
                Toggle(S.notifWeighed, nWeigh) { nWeigh = it; p.notifyWeighed = it }
                Toggle(S.notifProblems, nProb) { nProb = it; p.notifyProblems = it }
                Toggle(S.notifLive, live, S.notifLiveHelp) { live = it; p.live = it }
                Toggle(S.background, bg, S.backgroundHelp) { bg = it; p.background = it; app.syncLink() }
                if (bg) {
                    Spacer(Modifier.height(6.dp))
                    if (batteryOk) Text(S.batteryDone, style = MaterialTheme.typography.bodySmall, color = Mc.ok)
                    else FilledTonalButton(onClick = {
                        runCatching {
                            ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
                        }
                        batteryOk = pm.isIgnoringBatteryOptimizations(ctx.packageName)
                    }, modifier = Modifier.fillMaxWidth(), shape = smooth(16.dp)) { Text(S.battery) }
                    Spacer(Modifier.height(8.dp))
                    Text(S.idleNotifHelp, style = MaterialTheme.typography.bodySmall, color = Mc.text2)
                    TextButton(onClick = {
                        runCatching {
                            ctx.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, mcc.phone.Notifier.LINK))
                        }
                    }) { Text(S.idleNotifButton, color = Mc.amber) }
                    if (Build.MANUFACTURER.equals("huawei", true) || Build.MANUFACTURER.equals("honor", true))
                        Text(S.huaweiHelp, style = MaterialTheme.typography.bodySmall, color = Mc.amber, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        if (Build.VERSION.SDK_INT >= 31) Glass(Modifier.fillMaxWidth()) {
            Column {
                Label(S.look, Mc.amber, Icon.SUN)
                Spacer(Modifier.height(6.dp))
                Toggle(S.wallpaperColors, Mc.dynamicOn.value, S.wallpaperColorsHelp) { p.dynamicColor = it; Mc.dynamicOn.value = it }
            }
        }

        Glass(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label(S.language, Mc.text2, Icon.GLOBE)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("" to S.systemDefault, "pt" to "Português", "en" to "English").forEach { (k, label) ->
                        FilterChip(selected = lang == k, onClick = {
                            lang = k; p.language = k; P.set(p.languageCode()); app.notifier.channels(); (ctx as? Activity)?.recreate()
                        }, label = { Text(label) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Mc.amber, selectedLabelColor = Mc.onAccent))
                    }
                }
            }
        }

        Text("${S.about}: mcc-phone ${BuildConfig.VERSION_NAME} · ${L.s.fontCredit}", style = MaterialTheme.typography.bodySmall, color = Mc.text3)
        Text(S.safetyNote, style = MaterialTheme.typography.bodySmall, color = Mc.text3)
    }
}
