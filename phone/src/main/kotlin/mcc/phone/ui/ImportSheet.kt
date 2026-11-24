@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package mcc.phone.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import mcc.app.i18n.L
import mcc.app.ui.Icon
import mcc.phone.Importer
import mcc.phone.P
import mcc.recipe.Step

/** Link (or text) in, recipe out: import with the AI, then add it to the recipe server or keep it on the phone. */
@Composable
fun ImportSheet(onSettings: () -> Unit) {
    val st by Importer.state.collectAsState()
    val prefill by Importer.prefill.collectAsState()
    val ctx = LocalContext.current
    val S = P.s
    var input by remember { mutableStateOf("") }
    // a shared link wins; otherwise a link on the clipboard is offered, as that's how most people copy one
    LaunchedEffect(prefill) {
        val shared = prefill
        if (shared != null) { input = shared; Importer.prefill.value = null }
        else if (input.isBlank()) {
            val clip = runCatching { (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text?.toString() }.getOrNull()
            if (clip != null && clip.trim().startsWith("http")) input = clip.trim()
        }
    }
    ModalBottomSheet(onDismissRequest = { Importer.sheet.value = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Mc.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Label(S.importTitle, Mc.amber, Icon.SPARK)
            when (val s = st) {
                is Importer.State.Working -> Working(s)
                is Importer.State.Ready -> Ready(s)
                else -> {
                    if (!Importer.aiConfigured) {
                        Text(S.importNotSet, style = MaterialTheme.typography.bodyMedium, color = Mc.text2)
                        FilledTonalButton(onClick = { Importer.sheet.value = false; onSettings() }, shape = RoundedCornerShape(16.dp)) { Text(S.openSettings) }
                        return@Column
                    }
                    Text(S.importHelp, style = MaterialTheme.typography.bodySmall, color = Mc.text2)
                    OutlinedTextField(
                        value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth(), maxLines = 4,
                        placeholder = { Text(L.s.aiImportHint) }, shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { Importer.start(input) }),
                        trailingIcon = if (input.isNotEmpty()) ({ androidx.compose.material3.IconButton(onClick = { input = "" }) { McIcon(Icon.CLOSE, Mc.text2, 18.dp, 2.4f) } }) else null,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Mc.amber, unfocusedBorderColor = Mc.stroke),
                    )
                    (s as? Importer.State.Failed)?.let { Text(L.s.aiFailed(it.message), style = MaterialTheme.typography.bodySmall, color = Mc.danger) }
                    Button(onClick = { Importer.start(input) }, enabled = input.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Mc.ember, contentColor = Mc.onAccent)) { Text(S.importGo) }
                }
            }
        }
    }
}

@Composable
private fun Working(s: Importer.State.Working) {
    var sec by remember(s) { mutableIntStateOf(0) }
    LaunchedEffect(s) { while (true) { sec = ((System.currentTimeMillis() - s.since) / 1000).toInt(); delay(1000) } }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 18.dp)) {
        CircularProgressIndicator(Modifier.size(28.dp), color = Mc.amber, strokeWidth = 3.dp)
        Text(P.s.importStage(s.stage.ordinal, sec), style = MaterialTheme.typography.bodyLarge, color = Mc.text)
    }
    if (s.stage == Importer.Stage.VIDEO) Text(P.s.videoWait, style = MaterialTheme.typography.bodySmall, color = Mc.text2)
}

@Composable
private fun Ready(s: Importer.State.Ready) {
    val S = P.s
    // review in either language: the translation is what the other language's users will see
    var showOther by remember(s) { mutableStateOf(false) }
    val r = if (showOther && s.other != null) s.other else s.main
    var feedback by remember(s) { mutableStateOf("") }

    Text(r.title, style = MaterialTheme.typography.titleLarge, color = Mc.text)
    Text(S.importSummary(r.ingredients.sumOf { it.items.size }, r.steps.size, r.steps.count { it is Step.Machine }) +
        (r.servings?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodyMedium, color = Mc.text2)
    if (s.other != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(false to s.main.language, true to s.other.language).forEach { (other, code) ->
            FilterChip(selected = showOther == other, onClick = { showOther = other }, label = { Text(if (code == "pt") "Português" else "English") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Mc.amber, selectedLabelColor = Mc.onAccent))
        }
    }
    if (s.revisions > 0) Text(S.revised(s.revisions), style = MaterialTheme.typography.bodySmall, color = Mc.teal)
    when {
        s.imp.videoSource == "none" -> Text(S.videoDescriptionOnly, style = MaterialTheme.typography.bodySmall, color = Mc.amber)
        s.imp.partial -> Text(S.videoPartial, style = MaterialTheme.typography.bodySmall, color = Mc.amber)
    }

    // the whole recipe, so it can be checked before it goes anywhere
    Column(Modifier.fillMaxWidth().background(Mc.surface2, RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(L.s.ingredients, style = MaterialTheme.typography.labelLarge, color = Mc.amber)
        r.ingredients.forEach { g ->
            g.group?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = Mc.text) }
            g.items.forEach { Text("• ${it.text}", style = MaterialTheme.typography.bodyMedium, color = Mc.text) }
        }
        HorizontalDivider(color = Mc.stroke, modifier = Modifier.padding(vertical = 6.dp))
        Text(S.steps, style = MaterialTheme.typography.labelLarge, color = Mc.amber)
        r.steps.forEachIndexed { i, st ->
            Row {
                Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, color = Mc.text2, modifier = Modifier.width(28.dp))
                Column {
                    Text(st.text, style = MaterialTheme.typography.bodyMedium, color = Mc.text)
                    when (st) {
                        is Step.Machine -> Text(L.s.stepShort(st), style = MaterialTheme.typography.labelMedium, color = if (st.segments.any { it.tempC > 0 }) Mc.ember else Mc.teal)
                        is Step.Weigh -> Text("${st.grams} g", style = MaterialTheme.typography.labelMedium, color = Mc.teal)
                        else -> {}
                    }
                }
            }
        }
        if (r.notes.isNotEmpty()) {
            HorizontalDivider(color = Mc.stroke, modifier = Modifier.padding(vertical = 6.dp))
            r.notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Mc.text2) }
        }
    }
    if (s.changes.isNotEmpty()) {
        Text(S.adjustedTitle, style = MaterialTheme.typography.labelLarge, color = Mc.amber)
        s.changes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = Mc.text2) }
    }

    // feedback to the AI: it rewrites the recipe from the same source with these corrections
    Text(S.feedbackLabel, style = MaterialTheme.typography.titleSmall, color = Mc.text, modifier = Modifier.padding(top = 6.dp))
    // the keyboard covers the button below, so the box sends too: its arrow and the keyboard's action key
    OutlinedTextField(value = feedback, onValueChange = { feedback = it }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5,
        placeholder = { Text(S.feedbackHint) }, shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { Importer.revise(feedback) }),
        trailingIcon = if (feedback.isNotBlank()) ({ androidx.compose.material3.IconButton(onClick = { Importer.revise(feedback) }) { McIcon(Icon.NEXT, Mc.amber) } }) else null,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Mc.amber, unfocusedBorderColor = Mc.stroke))
    FilledTonalButton(onClick = { Importer.revise(feedback) }, enabled = feedback.isNotBlank(), modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)) { Text(S.feedbackSend) }
    Spacer(Modifier.height(4.dp))

    when {
        s.other == null -> Text(S.noSecondLanguage, style = MaterialTheme.typography.bodySmall, color = Mc.amber)
        !Importer.canPublish -> Text(S.publishOff, style = MaterialTheme.typography.bodySmall, color = Mc.text2)
        else -> {
            Text(S.publishHelp, style = MaterialTheme.typography.bodySmall, color = Mc.text2)
            Button(onClick = { Importer.publish() }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Mc.ember, contentColor = Mc.onAccent)) { Text(S.publish) }
        }
    }
    FilledTonalButton(onClick = { Importer.keepOnPhone() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text(S.keepOnPhone) }
    TextButton(onClick = { Importer.discard() }, modifier = Modifier.fillMaxWidth()) { Text(S.discard, color = Mc.text2) }
}
