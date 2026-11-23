@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.animation.ExperimentalSharedTransitionApi::class,
)

package mcc.phone.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import mcc.app.i18n.L
import mcc.app.ui.Covers
import mcc.app.ui.Icon
import mcc.app.ui.visible
import mcc.link.Command
import mcc.phone.LinkClient
import mcc.phone.P
import mcc.phone.PhoneApp
import mcc.phone.Recipes
import mcc.recipe.Mode
import mcc.recipe.Recipe
import mcc.recipe.RecipeIndex
import mcc.recipe.Servings
import mcc.recipe.forPeople
import mcc.recipe.Step

/** Shared-element scopes for the photo that grows from the card into the recipe page (single-pane only). */
class SharedScopes(val shared: SharedTransitionScope, val anim: AnimatedVisibilityScope)
val LocalShared = compositionLocalOf<SharedScopes?> { null }

@Composable
fun Modifier.sharedCover(id: String): Modifier {
    val s = LocalShared.current ?: return this
    return with(s.shared) { this@sharedCover.sharedBounds(rememberSharedContentState("cover-$id"), s.anim) }
}

/** Warm photo placeholder colours, picked from the title (like the machine's covers). */
private fun palette(seed: String): Pair<Color, Color> {
    val p = listOf(0xFF8A3F12 to 0xFF3A1406, 0xFF1C6E62 to 0xFF0B2B27, 0xFF7A5A12 to 0xFF2E2006, 0xFF6B2A3A to 0xFF2A0E16, 0xFF2F5A7A to 0xFF0E2233)
    val (a, b) = p[(seed.hashCode() and 0x7fffffff) % p.size]
    return Color(a) to Color(b)
}

@Composable
fun Cover(id: String, title: String, tags: List<String>, image: String?, width: Int, modifier: Modifier) {
    val bmp = Images.load(PhoneApp.app.store, image, width)
    val (a, b) = palette(title)
    Box(modifier.background(Brush.linearGradient(listOf(a, b)))) {
        if (bmp != null) Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else McIcon(Covers.iconFor(id, title, tags), Mc.onPhoto.copy(alpha = 0.55f), 56.dp, 1.4f, Modifier.align(Alignment.Center))
    }
}

@Composable
private fun TimePill(min: Int, modifier: Modifier = Modifier) {
    Text(L.s.minutesShort(min), style = MaterialTheme.typography.labelMedium, color = Color.White,
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x8C000000)).padding(horizontal = 8.dp, vertical = 4.dp))
}

@Composable
fun RecipeList(modifier: Modifier, selected: String?, onOpen: (RecipeIndex.Entry) -> Unit) {
    val rs by Recipes.state.collectAsState()
    var q by rememberSaveable { mutableStateOf("") }
    var tag by rememberSaveable { mutableStateOf<String?>(null) }
    val S = P.s
    // each recipe once, in the app language (Settings → Language)
    val inLang = remember(rs.index) { rs.index.visible(L.s.code.takeIf { it == "en" } ?: "pt") }
    val tags = remember(inLang) { inLang.flatMap { it.tags }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(10).map { it.key } }
    val shown = inLang.filter { e ->
        (tag == null || tag in e.tags) && (q.isBlank() || e.title.contains(q.trim(), ignoreCase = true) || e.tags.any { it.contains(q.trim(), true) })
    }
    // a few photographed recipes, different every day
    val featured = remember(inLang) {
        val day = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
        inLang.filter { it.image != null && !it.id.startsWith("program-") }.shuffled(java.util.Random(day.toLong())).take(8)
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp), modifier = modifier,
        contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(L.s.recipesTitle, style = MaterialTheme.typography.headlineMedium, color = Mc.text)
                        Text(L.s.recipesCount(shown.size), style = MaterialTheme.typography.bodyMedium, color = Mc.text2)
                    }
                    IconButton(onClick = { mcc.phone.Importer.open() }) { McIcon(Icon.SPARK, Mc.amber) }
                    if (rs.loading) CircularProgressIndicator(Modifier.size(22.dp), color = Mc.amber, strokeWidth = 2.5.dp)
                    else IconButton(onClick = { Recipes.refresh(force = true) }) { McIcon(Icon.REFRESH, Mc.text2) }
                }
                OutlinedTextField(
                    value = q, onValueChange = { q = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(L.s.searchHint) }, leadingIcon = { McIcon(Icon.SEARCH, Mc.text2, 20.dp) },
                    trailingIcon = if (q.isNotEmpty()) ({ IconButton(onClick = { q = "" }) { McIcon(Icon.CLOSE, Mc.text2, 18.dp, 2.4f) } }) else null,
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Mc.amber, unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Mc.surface, unfocusedContainerColor = Mc.surface.copy(alpha = 0.85f)),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    tags.forEach { t -> FilterChip(selected = tag == t, onClick = { tag = if (tag == t) null else t }, label = { Text(t) }, colors = chipColors(), shape = RoundedCornerShape(12.dp)) }
                }
                rs.warning?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Mc.amber) }
            }
        }
        if (featured.size >= 3 && q.isBlank() && tag == null) item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Label(S.featured, Mc.amber, Icon.SPARK)
                Spacer(Modifier.height(10.dp))
                // Material 3 carousel: big item in front, smaller ones peeking
                HorizontalMultiBrowseCarousel(state = rememberCarouselState { featured.size }, preferredItemWidth = 250.dp, itemSpacing = 8.dp,
                    modifier = Modifier.fillMaxWidth().height(210.dp)) { i ->
                    val e = featured[i]
                    Box(Modifier.fillMaxSize().maskClip(RoundedCornerShape(28.dp)).clickable { onOpen(e) }) {
                        Cover(e.id, e.title, e.tags, e.image, 700, Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xD9000000))))
                        Text(e.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.BottomStart).padding(14.dp))
                    }
                }
            }
        }
        items(shown, key = { it.id }) { e -> RecipeCard(e, e.id == selected) { onOpen(e) } }
        if (shown.isEmpty() && !rs.loading) item(span = { GridItemSpan(maxLineSpan) }) {
            Text(if (rs.index.isEmpty()) S.noRepo else L.s.nothingMatches(q), color = Mc.text2, modifier = Modifier.padding(24.dp))
        }
    }
}

/** Photo card with the title on the photo, like the machine's recipe grid. */
@Composable
private fun RecipeCard(e: RecipeIndex.Entry, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier.fillMaxWidth().aspectRatio(0.8f).sharedCover(e.id).clip(shape)
            .border(if (selected) 3.dp else 0.dp, if (selected) Mc.amber else Color.Transparent, shape)
            .clickable(onClick = onClick)
    ) {
        Cover(e.id, e.title, e.tags, e.image, 420, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color(0xE0000000))))
        e.totalMinutes?.let { TimePill(it, Modifier.align(Alignment.TopEnd).padding(10.dp)) }
        Text(e.title, style = MaterialTheme.typography.titleSmall.copy(lineHeight = 19.sp), color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = Mc.amber, selectedLabelColor = Mc.onAccent, containerColor = Mc.surface.copy(alpha = 0.85f), labelColor = Mc.text2)

/** Sends "open this recipe" to the machine and waits up to 6 s for the machine to report it. */
private suspend fun openOnMachine(r: Recipe, servings: Int?, ui: Ui) {
    val S = P.s
    if (!LinkClient.send(Command.Load(r.id, servings))) { ui.say(S.connecting); return }
    val ok = withTimeoutOrNull(6_000) { LinkClient.state.first { it?.recipeId == r.id } } != null
    ui.say(if (ok) S.openedOnMachine else S.notOpened)
}

@Composable
fun RecipeDetail(e: RecipeIndex.Entry, onBack: (() -> Unit)?, onCook: (String) -> Unit) {
    val ui = LocalUi.current
    val S = P.s
    val st by LinkClient.state.collectAsState()
    val haptic = rememberHaptic()
    val recipe by produceState<Result<Recipe>?>(null, e.id) { value = null; value = Recipes.load(e) }
    val raw = recipe?.getOrNull()
    // how many people: what the machine cooks it for when it is open there, else the choice made here
    val base = raw?.let { Servings.base(it) }
    val machinePeople = if (raw != null && st?.recipeId == raw.id) Servings.parse(st?.servings)?.count else null
    var chosen by remember(e.id) { mutableIntStateOf(Recipes.people[e.id] ?: 0) }
    val people = machinePeople ?: chosen.takeIf { it > 0 } ?: base?.count
    val r = remember(raw, people) { raw?.let { if (people != null) it.forPeople(people) else it } }
    // predictive back: the page shrinks and rounds as the back gesture is drawn
    var back by remember { mutableFloatStateOf(0f) }
    if (onBack != null) PredictiveBackHandler { progress ->
        try { progress.collect { back = it.progress }; onBack() } catch (_: CancellationException) { back = 0f }
    }
    val onMachine = r != null && st?.recipeId == r.id
    val current = if (onMachine) st?.step?.substringBefore("/")?.toIntOrNull()?.minus(1) else null
    val list = rememberLazyListState()
    // follow the machine when it moves to another step (not on opening: the photo and buttons come first)
    var seen by remember(e.id) { mutableStateOf(current) }
    val stepsStart = 2 + if (r?.ingredients?.isNotEmpty() == true) 1 else 0 // hero, [ingredients], steps label
    LaunchedEffect(current) { if (current != null && current != seen) list.animateScrollToItem(current + stepsStart); seen = current }

    Box(Modifier.fillMaxSize().graphicsLayer {
        val k = 1f - 0.1f * back
        scaleX = k; scaleY = k; shape = RoundedCornerShape((32 * back).dp); clip = back > 0f
    }.background(if (back > 0f) Mc.bg else Color.Transparent)) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
            item {
                Box(Modifier.fillMaxWidth().aspectRatio(if (ui.wide) 1.7f else 1.05f).sharedCover(e.id).clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))) {
                    // parallax: the photo moves at half speed under the page
                    Cover(e.id, e.title, e.tags, r?.image ?: e.image, 1000, Modifier.fillMaxSize().graphicsLayer {
                        if (list.firstVisibleItemIndex == 0) translationY = list.firstVisibleItemScrollOffset * 0.5f
                    })
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x55000000), 0.3f to Color.Transparent, 1f to Color(0xE0000000))))
                    if (onBack != null) Box(
                        Modifier.padding(14.dp).size(46.dp).clip(CircleShape).background(Color(0x66000000)).clickable { onBack() },
                        contentAlignment = Alignment.Center,
                    ) { McIcon(Icon.BACK, Mc.onPhoto, 22.dp, 2.5f) }
                    Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                        if (onMachine) Text(S.machineIsOn.uppercase(L.s.locale) + (st?.step?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelMedium,
                            color = Color.White, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Mc.teal).padding(horizontal = 10.dp, vertical = 5.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(r?.title ?: e.title, style = MaterialTheme.typography.headlineMedium, color = Mc.onPhoto)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                            (r?.totalMinutes ?: e.totalMinutes)?.let { Fact(Icon.CLOCK, L.s.minutesShort(it)) }
                            r?.servings?.let { Fact(Icon.CHEF, S.servings(it)) }
                            r?.nutritionPerServing?.kcal?.let { Fact(Icon.FLAME, L.s.kcal(it)) }
                        }
                    }
                }
            }
            if (r == null) item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    recipe?.exceptionOrNull()?.let { Text(L.s.cannotOpen(it.message ?: ""), color = Mc.danger) } ?: CircularProgressIndicator(color = Mc.amber)
                }
            }
            if (r != null && r.ingredients.isNotEmpty()) item {
                Glass(Modifier.padding(16.dp).fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { Label(L.s.ingredients, Mc.teal, Icon.LIST) }
                            Text(S.addToList, style = MaterialTheme.typography.labelLarge, color = Mc.teal,
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                                    haptic(false); ui.say(S.addedToList(PhoneApp.app.shopping.add(r.ingredients.flatMap { g -> g.items.map { it.text } }, r.title)))
                                }.padding(6.dp))
                        }
                        if (base != null && people != null) ServingsStepper(people, Servings.range(base), r.servings ?: "$people", changed = people != base.count,
                            locked = machinePeople != null) { n -> haptic(false); chosen = n; Recipes.people[e.id] = n }
                        r.ingredients.forEach { g ->
                            g.group?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = Mc.text, modifier = Modifier.padding(top = 6.dp)) }
                            g.items.forEach {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(6.dp).clip(CircleShape).background(Mc.amber)); Spacer(Modifier.width(10.dp))
                                    Text(it.text, style = MaterialTheme.typography.bodyLarge, color = Mc.text2)
                                }
                            }
                        }
                    }
                }
            }
            if (r != null) {
                item { Box(Modifier.padding(start = 20.dp, top = 12.dp, bottom = 6.dp)) { Label(S.steps, Mc.amber, Icon.CHEF) } }
                itemsIndexed(r.steps) { i, step -> StepRow(i, step, current, last = i == r.steps.lastIndex) }
                if (r.notes.isNotEmpty()) item {
                    Column(Modifier.padding(20.dp)) {
                        Label(L.s.notesWord, Mc.text2)
                        r.notes.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = Mc.text2, modifier = Modifier.padding(top = 6.dp)) }
                    }
                }
            }
        }
        // pinned actions
        if (r != null) Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(14.dp)
                .clip(RoundedCornerShape(28.dp)).background(Mc.surface.copy(alpha = 0.94f)).border(1.dp, Mc.stroke, RoundedCornerShape(28.dp)).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = { haptic(true); ui.scope.launch { openOnMachine(r, people?.takeIf { base != null && it != base.count }, ui) } }, enabled = !onMachine && st?.running != true,
                modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Mc.ember, contentColor = Mc.onAccent)) {
                McIcon(Icon.POT, Mc.onAccent, 18.dp); Spacer(Modifier.width(8.dp)); Text(S.openOnMachine, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            FilledTonalButton(onClick = { onCook(r.id) }, modifier = Modifier.height(54.dp), shape = RoundedCornerShape(22.dp)) {
                McIcon(Icon.CHEF, Mc.text, 18.dp); Spacer(Modifier.width(6.dp)); Text(S.cookMode.substringBefore(" "), maxLines = 1)
            }
        }
    }
}

/** − 6 pessoas +, and a word that amounts changed but times did not. Locked while the machine has the recipe open. */
@Composable
private fun ServingsStepper(people: Int, range: IntRange, label: String, changed: Boolean, locked: Boolean, onChange: (Int) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.clip(RoundedCornerShape(22.dp)).background(Mc.surface3.copy(alpha = 0.6f)).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { onChange(people - 1) }, enabled = !locked && people > range.first, modifier = Modifier.size(40.dp)) {
                Text("−", style = MaterialTheme.typography.titleLarge)
            }
            Text(P.s.servings(label), style = MaterialTheme.typography.titleMedium, color = Mc.text, modifier = Modifier.padding(horizontal = 14.dp))
            FilledTonalIconButton(onClick = { onChange(people + 1) }, enabled = !locked && people < range.last, modifier = Modifier.size(40.dp)) {
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
        }
        if (locked) Text(P.s.servingsOnMachine, style = MaterialTheme.typography.bodySmall, color = Mc.text3, modifier = Modifier.padding(top = 6.dp))
        else if (changed) Text(L.s.scaledNote, style = MaterialTheme.typography.bodySmall, color = Mc.amber, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun Fact(icon: Icon, text: String) {
    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x33FFFFFF)).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        McIcon(icon, Color.White, 14.dp); Spacer(Modifier.width(6.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

/** What a step does, as an icon, a colour and a short chip ("5 min · speed 1 · 100 °C"). */
fun stepLook(step: Step): Triple<Icon, Color, String?> = when (step) {
    is Step.Weigh -> Triple(Icon.SCALE, Mc.teal, L.s.weighGrams(step.grams))
    is Step.Machine -> Triple(when (step.mode) { Mode.KNEAD -> Icon.KNEAD; Mode.STEAM -> Icon.STEAM; Mode.TURBO -> Icon.TURBO; Mode.ROAST -> Icon.FLAME
        else -> if (step.segments.any { it.tempC > 0 }) Icon.FLAME else Icon.DIAL }, if (step.segments.any { it.tempC > 0 }) Mc.ember else Mc.amber, L.s.stepShort(step))
    is Step.Instruction -> Triple(Icon.HAND, Mc.text2, null)
}

/** One step on the timeline: numbered dot joined to the next one; the machine's current step glows. */
@Composable
fun StepRow(i: Int, step: Step, current: Int?, last: Boolean = false) {
    val now = i == current
    val done = current != null && i < current
    val (icon, col, chip) = stepLook(step)
    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(36.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.size(34.dp).clip(CircleShape).background(when { done -> Mc.teal; now -> Mc.amber; else -> col.copy(alpha = 0.18f) }), contentAlignment = Alignment.Center) {
                if (done) McIcon(Icon.CHECK, Mc.onAccent, 18.dp, 2.6f)
                else Text("${i + 1}", color = if (now) Mc.onAccent else col, fontSize = 15.sp, style = MaterialTheme.typography.titleSmall)
            }
            if (!last) Box(Modifier.width(2.dp).weight(1f).background(if (done) Mc.teal else Mc.stroke))
        }
        Spacer(Modifier.width(10.dp))
        Column(
            Modifier.weight(1f).padding(vertical = 6.dp).pulse(Mc.amber, now).clip(RoundedCornerShape(20.dp))
                .background(if (now) Mc.amber.copy(alpha = 0.16f) else Mc.surface.copy(alpha = if (done) 0.45f else 0.9f))
                .border(if (now) 2.dp else 1.dp, if (now) Mc.amber else Mc.stroke, RoundedCornerShape(20.dp))
                .padding(14.dp),
        ) {
            if (chip != null) Row(verticalAlignment = Alignment.CenterVertically) {
                McIcon(icon, col, 16.dp); Spacer(Modifier.width(6.dp))
                Text(chip, style = MaterialTheme.typography.labelLarge, color = col)
            }
            if (step.text.isNotBlank()) Text(step.text, style = MaterialTheme.typography.bodyLarge, color = if (done) Mc.text3 else Mc.text,
                modifier = Modifier.padding(top = if (chip != null) 4.dp else 0.dp))
            if (now) Text(P.s.stepNow.uppercase(L.s.locale), style = MaterialTheme.typography.labelSmall, color = Mc.amber, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** Unfolded Machine tab, right pane: the recipe open on the machine, following its current step. */
@Composable
fun LoadedRecipePanel(modifier: Modifier, onOpen: (String) -> Unit) {
    val st by LinkClient.state.collectAsState()
    val id = st?.recipeId
    val written = rememberLoadedRecipe(id)
    val machinePeople = Servings.parse(st?.servings)?.count
    val r = remember(written, machinePeople) { written?.let { w -> machinePeople?.let { w.forPeople(it) } ?: w } }
    if (id == null || r == null) {
        Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                McIcon(Icon.BOOK, Mc.text3, 56.dp, 1.4f)
                Spacer(Modifier.height(12.dp))
                Text(if (id == null) P.s.noRecipeLoaded else (st?.recipe ?: ""), color = Mc.text2, style = MaterialTheme.typography.bodyLarge)
            }
        }
        return
    }
    val current = st?.step?.substringBefore("/")?.toIntOrNull()?.minus(1)
    val list = rememberLazyListState()
    LaunchedEffect(current) { if (current != null) list.animateScrollToItem(current.coerceAtLeast(0)) }
    Column(modifier) {
        Row(Modifier.padding(16.dp).clickable { onOpen(r.id) }, verticalAlignment = Alignment.CenterVertically) {
            Cover(r.id, r.title, r.tags, r.image, 200, Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Label(P.s.machineHere, Mc.teal)
                Text(r.title, style = MaterialTheme.typography.titleMedium, color = Mc.text, maxLines = 2)
            }
        }
        CancelRecipeButton(onPhoto = false, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
        LazyColumn(state = list, contentPadding = PaddingValues(bottom = 24.dp)) { itemsIndexed(r.steps) { i, s -> StepRow(i, s, current, i == r.steps.lastIndex) } }
    }
}
