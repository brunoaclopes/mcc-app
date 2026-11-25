@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package mcc.phone.ui

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mcc.app.ui.Icon
import mcc.phone.P
import mcc.phone.PhoneApp
import mcc.phone.Recipes
import mcc.phone.Shopping

@Composable
fun ShoppingScreen(modifier: Modifier) {
    val shop = PhoneApp.app.shopping
    val items by shop.items.collectAsState()
    val index = Recipes.state.collectAsState().value.index
    val ctx = LocalContext.current
    val ui = LocalUi.current
    val haptic = rememberHaptic()
    val S = P.s
    val done = items.count { it.checked }
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(S.listTitle, style = MaterialTheme.typography.headlineMedium, color = Mc.text, modifier = Modifier.weight(1f))
                    if (done > 0) TextButton(onClick = {
                        haptic(false)
                        val gone = shop.clearChecked()
                        ui.scope.launch {
                            ui.snackbar.currentSnackbarData?.dismiss()
                            if (ui.snackbar.showSnackbar(S.clearedN(gone.size), S.undo, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed)
                                shop.putBack(gone)
                        }
                    }) { Text(S.clearChecked, color = Mc.teal) }
                    if (items.isNotEmpty()) TextButton(onClick = {
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, shop.asText(S.listTitle)), S.share))
                    }) { Text(S.share, color = Mc.amber) }
                }
                if (items.isNotEmpty()) {
                    Text(S.bought(done, items.size), style = MaterialTheme.typography.bodyMedium, color = Mc.text2)
                    Spacer(Modifier.height(10.dp))
                    LinearWavyProgressIndicator(progress = { done / items.size.toFloat() }, color = Mc.teal, trackColor = Mc.surface3,
                        modifier = Modifier.fillMaxWidth().height(10.dp))
                }
            }
        }
        if (items.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(top = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(110.dp).clip(smooth(36.dp)).background(Mc.surface), contentAlignment = Alignment.Center) {
                    McIcon(Icon.LIST, Mc.amber, 52.dp, 1.6f)
                }
                Spacer(Modifier.size(16.dp))
                Text(S.listEmpty, color = Mc.text2, style = MaterialTheme.typography.bodyLarge)
            }
        }
        items.groupBy { it.recipe }.forEach { (recipe, group) ->
            if (recipe != null) item(key = "h-$recipe") {
                val e = index.firstOrNull { it.title == recipe }
                Row(Modifier.padding(top = 14.dp, bottom = 2.dp).animateItem(), verticalAlignment = Alignment.CenterVertically) {
                    if (e != null) Cover(e.id, e.title, e.tags, e.image, 160, Modifier.size(40.dp).clip(smooth(12.dp)))
                    else Box(Modifier.size(40.dp).clip(smooth(12.dp)).background(Mc.surface3), contentAlignment = Alignment.Center) { McIcon(Icon.BOOK, Mc.text3, 20.dp) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(recipe, style = MaterialTheme.typography.titleSmall, color = Mc.text)
                        Text(S.bought(group.count { it.checked }, group.size), style = MaterialTheme.typography.bodySmall, color = Mc.text2)
                    }
                }
            }
            items(group, key = { "${it.recipe}|${it.text}" }) { item ->
                ItemRow(item, Modifier.animateItem(), onToggle = { haptic(!item.checked); shop.toggle(item) }) {
                    val gone = shop.remove(item)
                    ui.scope.launch {
                        ui.snackbar.currentSnackbarData?.dismiss()
                        if (ui.snackbar.showSnackbar(S.removed(item.text), S.undo, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed)
                            shop.putBack(gone)
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(it: Shopping.Item, modifier: Modifier, onToggle: () -> Unit, onRemove: () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(state, modifier = modifier, enableDismissFromStartToEnd = false, onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) onRemove() },
        backgroundContent = {
            Box(Modifier.fillMaxSize().clip(smooth(18.dp)).background(Mc.danger).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterEnd) {
                McIcon(Icon.TRASH, androidx.compose.ui.graphics.Color.White, 22.dp)
            }
        }) {
        val box by animateColorAsState(if (it.checked) Mc.teal else Mc.surface3, label = "check")
        Row(
            Modifier.fillMaxWidth().clip(smooth(18.dp)).background(Mc.surface.copy(alpha = if (it.checked) 0.6f else 0.95f)).clickable(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(26.dp).clip(smooth(8.dp)).background(box), contentAlignment = Alignment.Center) {
                if (it.checked) McIcon(Icon.CHECK, Mc.onAccent, 16.dp, 2.8f)
            }
            Spacer(Modifier.width(12.dp))
            Text(it.text, style = MaterialTheme.typography.bodyLarge, color = if (it.checked) Mc.text3 else Mc.text,
                textDecoration = if (it.checked) TextDecoration.LineThrough else null, modifier = Modifier.weight(1f))
        }
    }
}
