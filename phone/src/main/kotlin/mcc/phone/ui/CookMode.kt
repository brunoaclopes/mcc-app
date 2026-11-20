package mcc.phone.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import mcc.app.i18n.L
import mcc.app.ui.Icon
import mcc.phone.LinkClient
import mcc.phone.P
import mcc.phone.Recipes
import mcc.recipe.Recipe
import mcc.recipe.Step
import kotlin.math.absoluteValue

/**
 * Cook along: one step per page in big type, the screen stays on. When the recipe is open on the machine the pages
 * follow the machine's step; you can still swipe ahead to read what comes next.
 */
@Composable
fun CookMode(recipeId: String, onClose: () -> Unit) {
    val r by produceState<Recipe?>(null, recipeId) { value = Recipes.byId(recipeId) }
    val view = LocalView.current
    DisposableEffect(view) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    BackHandler { onClose() }
    val S = P.s
    val haptic = rememberHaptic()
    val st by LinkClient.state.collectAsState()

    Box(Modifier.fillMaxSize()) {
        Ambient(if (st?.recipeId == recipeId && st?.running == true) mood(st, true) else Mc.amber)
        val recipe = r
        if (recipe == null) { CircularProgressIndicator(Modifier.align(Alignment.Center), color = Mc.amber); return@Box }
        val onMachine = st?.recipeId == recipe.id
        val machineStep = if (onMachine) st?.step?.substringBefore("/")?.toIntOrNull()?.minus(1)?.coerceIn(0, recipe.steps.lastIndex) else null
        val pager = rememberPagerState(initialPage = machineStep ?: 0) { recipe.steps.size }
        val scope = rememberCoroutineScope()
        LaunchedEffect(machineStep) { if (machineStep != null && machineStep != pager.currentPage) { pager.animateScrollToPage(machineStep); haptic(true) } }

        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            // top bar
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(Mc.surface).clickable { onClose() }, contentAlignment = Alignment.Center) {
                    McIcon(Icon.CLOSE, Mc.text, 20.dp, 2.4f)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(recipe.title, style = MaterialTheme.typography.titleMedium, color = Mc.text, maxLines = 1)
                    Text(L.s.stepOf(pager.currentPage + 1, recipe.steps.size), style = MaterialTheme.typography.bodyMedium, color = Mc.text2)
                }
            }
            // progress dashes
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(recipe.steps.size) { i ->
                    val c by animateColorAsState(when { machineStep != null && i < machineStep -> Mc.teal; i == pager.currentPage -> Mc.amber; i == machineStep -> Mc.teal; else -> Mc.surface3 }, label = "dash")
                    Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c))
                }
            }
            HorizontalPager(pager, Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp), pageSpacing = 12.dp) { page ->
                val step = recipe.steps[page]
                val (icon, col, chip) = stepLook(step)
                val off = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue
                Column(
                    Modifier.fillMaxSize().padding(vertical = 20.dp)
                        .graphicsLayer { val k = 1f - 0.08f * off.coerceAtMost(1f); scaleX = k; scaleY = k; alpha = 1f - 0.5f * off.coerceAtMost(1f) }
                        .clip(RoundedCornerShape(32.dp)).background(Mc.surface.copy(alpha = 0.92f))
                        .verticalScroll(rememberScrollState()).padding(26.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp).clip(CircleShape).background(col.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { McIcon(icon, col, 28.dp) }
                        Spacer(Modifier.width(14.dp))
                        Text("${page + 1}", style = MaterialTheme.typography.displaySmall, color = col)
                        if (page == machineStep) {
                            Spacer(Modifier.width(12.dp))
                            Text(S.stepNow.uppercase(L.s.locale), style = MaterialTheme.typography.labelLarge, color = Mc.onAccent,
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Mc.teal).padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                    if (chip != null) {
                        Spacer(Modifier.height(18.dp))
                        Text(chip, style = MaterialTheme.typography.headlineSmall, color = col)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(step.text, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold, lineHeight = 40.sp), color = Mc.text)
                    // weighing on the machine right now: show the scale live
                    if (step is Step.Weigh && page == machineStep && st?.weight != null) {
                        Spacer(Modifier.height(24.dp))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(170.dp), contentAlignment = Alignment.Center) {
                                val w = st?.weight ?: 0
                                Gauge(w / step.grams.toFloat(), if (w >= step.grams * 0.95f) Mc.ok else Mc.teal, Modifier.fillMaxSize(), 14.dp)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Rolling("$w", MaterialTheme.typography.displaySmall, Mc.text)
                                    Text("/ ${step.grams} g", style = MaterialTheme.typography.titleMedium, color = Mc.text2)
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { haptic(false); scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }, enabled = pager.currentPage > 0,
                    modifier = Modifier.weight(1f).height(60.dp), shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Mc.surface3, contentColor = Mc.text)) { McIcon(Icon.BACK, Mc.text, 18.dp); Spacer(Modifier.width(6.dp)); Text(S.previous) }
                if (machineStep != null && machineStep != pager.currentPage)
                    Button(onClick = { scope.launch { pager.animateScrollToPage(machineStep) } }, modifier = Modifier.weight(1.4f).height(60.dp), shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Mc.teal, contentColor = Mc.onAccent)) { Text(S.goToMachineStep, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                else Button(onClick = { haptic(false); scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, enabled = pager.currentPage < recipe.steps.lastIndex,
                    modifier = Modifier.weight(1.4f).height(60.dp), shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Mc.ember, contentColor = Mc.onAccent)) { Text(S.next); Spacer(Modifier.width(6.dp)); McIcon(Icon.NEXT, Mc.onAccent, 18.dp) }
            }
        }
    }
}
