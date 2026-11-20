@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package mcc.phone.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mcc.app.ui.Icon
import mcc.phone.LinkClient
import mcc.phone.P
import mcc.phone.PhoneApp
import mcc.phone.Recipes
import mcc.recipe.RecipeIndex

enum class Tab { MACHINE, RECIPES, LIST, SETTINGS }

/** Set by app shortcuts / the widget ("machine", "recipes", "list"); Root switches to it. */
val pendingTab = androidx.compose.runtime.mutableStateOf<String?>(null)

/** Snackbar access for every screen. */
class Ui(val snackbar: SnackbarHostState, val scope: CoroutineScope, val wide: Boolean) {
    fun say(s: String) { scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(s) } }
}

val LocalUi = staticCompositionLocalOf<Ui> { error("no Ui") }

@Composable
fun Root(size: WindowSizeClass) {
    // Fold 7 unfolded (and tablets, landscape): rail + two panes. Folded / P30 Pro: bottom bar, one pane.
    val wide = size.widthSizeClass != WindowWidthSizeClass.Compact
    var tab by rememberSaveable { mutableStateOf(Tab.MACHINE) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var cookId by rememberSaveable { mutableStateOf<String?>(null) }
    // keeps the last recipe on screen while cook mode slides away
    var shownCook by remember { mutableStateOf<String?>(null) }
    if (cookId != null) shownCook = cookId
    androidx.compose.runtime.LaunchedEffect(pendingTab.value) {
        when (pendingTab.value) { "machine" -> tab = Tab.MACHINE; "recipes" -> tab = Tab.RECIPES; "list" -> tab = Tab.LIST }
        pendingTab.value = null
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ui = remember(wide) { Ui(snackbar, scope, wide) }
    val st by LinkClient.state.collectAsState()
    val items by PhoneApp.app.shopping.items.collectAsState()
    val recipes by Recipes.state.collectAsState()
    val open: RecipeIndex.Entry? = openId?.let { id -> recipes.index.firstOrNull { it.id == id } }

    // the recipe page handles its own (predictive) back; this one leaves other tabs
    BackHandler(enabled = tab != Tab.MACHINE && (openId == null || wide)) { tab = Tab.MACHINE }

    val S = P.s
    val tabs = listOf(
        Triple(Tab.MACHINE, Icon.POT, S.tabMachine), Triple(Tab.RECIPES, Icon.BOOK, S.tabRecipes),
        Triple(Tab.LIST, Icon.LIST, S.tabList), Triple(Tab.SETTINGS, Icon.GEAR, S.tabSettings),
    )
    val pending = items.count { !it.checked }
    val cooking = st?.running == true

    @Composable
    fun tabIcon(t: Tab, icon: Icon, selected: Boolean) {
        val col = if (selected) Mc.onAccent else Mc.text2
        when {
            t == Tab.LIST && pending > 0 -> BadgedBox(badge = { Badge(containerColor = Mc.teal, contentColor = Mc.onAccent) { Text("$pending") } }) { McIcon(icon, col) }
            t == Tab.MACHINE && cooking -> BadgedBox(badge = { Badge(containerColor = Mc.ember) }) { McIcon(icon, col) }
            else -> McIcon(icon, col)
        }
    }

    val online = rememberOnline().second
    CompositionLocalProvider(LocalUi provides ui) {
      Box(Modifier.fillMaxSize()) {
        Ambient(mood(st, online).let { if (!online || st?.running != true) Mc.amber else it })
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (!wide) NavigationBar(containerColor = Mc.surface.copy(alpha = 0.92f), tonalElevation = 0.dp) {
                    tabs.forEach { (t, icon, label) ->
                        NavigationBarItem(selected = tab == t, onClick = { if (tab == t && t == Tab.RECIPES) openId = null; tab = t },
                            icon = { tabIcon(t, icon, tab == t) }, label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = Mc.amber, selectedTextColor = Mc.text, unselectedTextColor = Mc.text2))
                    }
                }
            },
        ) { pad ->
            Row(Modifier.fillMaxSize().padding(pad)) {
                if (wide) {
                    NavigationRail(containerColor = Mc.surface.copy(alpha = 0.7f), modifier = Modifier.fillMaxHeight().windowInsetsPadding(WindowInsets.safeDrawing)) {
                        Spacer(Modifier.padding(top = 12.dp))
                        tabs.forEach { (t, icon, label) ->
                            NavigationRailItem(selected = tab == t, onClick = { tab = t }, icon = { tabIcon(t, icon, tab == t) }, label = { Text(label) },
                                colors = NavigationRailItemDefaults.colors(indicatorColor = Mc.amber, selectedTextColor = Mc.text, unselectedTextColor = Mc.text2))
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    AnimatedContent(targetState = tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab") { t ->
                        when (t) {
                            Tab.MACHINE -> if (wide) Row {
                                MachineScreen(Modifier.weight(1f))
                                VerticalDivider(color = Mc.stroke)
                                LoadedRecipePanel(Modifier.weight(1f)) { id -> openId = id; tab = Tab.RECIPES }
                            } else MachineScreen(Modifier.fillMaxSize(), onOpenRecipe = { id -> openId = id; tab = Tab.RECIPES }, onCook = { cookId = it })
                            Tab.RECIPES -> if (wide) Row {
                                RecipeList(Modifier.weight(1f), selected = openId) { openId = it.id }
                                VerticalDivider(color = Mc.stroke)
                                Box(Modifier.weight(1.15f)) {
                                    if (open != null) androidx.compose.runtime.key(open.id) { RecipeDetail(open, onBack = null, onCook = { cookId = it }) } else EmptyDetail()
                                }
                            } else androidx.compose.animation.SharedTransitionLayout {
                                // single pane: the card's photo grows into the recipe page
                                AnimatedContent(targetState = open, label = "recipe", transitionSpec = { fadeIn() togetherWith fadeOut() },
                                    contentKey = { it?.id }) { o ->
                                    CompositionLocalProvider(LocalShared provides SharedScopes(this@SharedTransitionLayout, this@AnimatedContent)) {
                                        if (o != null) RecipeDetail(o, onBack = { openId = null }, onCook = { cookId = it })
                                        else RecipeList(Modifier.fillMaxSize(), selected = null) { openId = it.id }
                                    }
                                }
                            }
                            Tab.LIST -> ShoppingScreen(Modifier.fillMaxSize())
                            Tab.SETTINGS -> SettingsScreen(Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
        androidx.compose.animation.AnimatedVisibility(cookId != null, enter = androidx.compose.animation.slideInVertically { it } + fadeIn(),
            exit = androidx.compose.animation.slideOutVertically { it } + fadeOut()) {
            shownCook?.let { id -> CookMode(id) { cookId = null } }
        }
      }
    }
}

@Composable
private fun EmptyDetail() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
        McIcon(Icon.BOOK, Mc.text3, 64.dp, 1.4f)
    }
}

