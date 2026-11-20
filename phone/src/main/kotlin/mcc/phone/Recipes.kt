package mcc.phone

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mcc.recipe.Recipe
import mcc.recipe.RecipeIndex

/** The recipe index from the repository (shared [mcc.app.RecipeStore]: same files and cache rules as the machine). */
object Recipes {
    data class State(val index: List<RecipeIndex.Entry> = emptyList(), val loading: Boolean = false, val warning: String? = null)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    private val opened = HashMap<String, Recipe>()

    fun refresh(force: Boolean = false) {
        val app = PhoneApp.app
        if (_state.value.loading) return
        _state.value = _state.value.copy(loading = true)
        app.scope.launch {
            val (idx, warn) = withContext(Dispatchers.IO) { app.store.loadIndex(force) }
            // the machine's cleaning program makes no sense to browse on the phone
            val list = idx?.recipes.orEmpty().filterNot { it.id.startsWith("program-clean-") }
            _state.value = State(list, false, warn ?: if (app.prefs.repoUrl.isBlank()) P.s.noRepo else null)
        }
    }

    suspend fun load(e: RecipeIndex.Entry): Result<Recipe> = opened[e.id]?.let { Result.success(it) } ?: withContext(Dispatchers.IO) {
        runCatching { PhoneApp.app.store.loadRecipe(e) }.onSuccess { opened[e.id] = it }
    }

    suspend fun byId(id: String): Recipe? = _state.value.index.firstOrNull { it.id == id }?.let { load(it).getOrNull() }
}
