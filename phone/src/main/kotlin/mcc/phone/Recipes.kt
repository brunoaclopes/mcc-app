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
    /** How many people each recipe was last set to on this phone (while the app runs); cook along uses it too. */
    val people = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /** When the index was last asked from the server (elapsedRealtime). */
    @Volatile private var fetchedAt = 0L

    /**
     * Reads the index; [force] asks the server. Stale-while-revalidate: the copy on the phone shows at once (when the
     * list is still empty), and the server's answer replaces it when it comes.
     */
    fun refresh(force: Boolean = false) {
        val app = PhoneApp.app
        if (_state.value.loading) return
        _state.value = _state.value.copy(loading = true)
        if (force) fetchedAt = android.os.SystemClock.elapsedRealtime()
        app.scope.launch {
            if (force && _state.value.index.isEmpty()) {
                val (cached, _) = withContext(Dispatchers.IO) { app.store.loadIndex(false) }
                visible(cached).takeIf { it.isNotEmpty() }?.let { _state.value = State(it, loading = true) }
            }
            val (idx, warn) = withContext(Dispatchers.IO) { app.store.loadIndex(force) }
            _state.value = State(visible(idx), false, warn ?: if (app.prefs.repoUrl.isBlank()) P.s.noRepo else null)
        }
    }

    /** Asks the server again when the list is older than 15 minutes: at start and whenever the app comes back. */
    fun revalidate() {
        if (android.os.SystemClock.elapsedRealtime() - fetchedAt > 15 * 60_000L || fetchedAt == 0L) refresh(force = true)
    }

    // the machine's cleaning program makes no sense to browse on the phone
    private fun visible(idx: RecipeIndex?) = idx?.recipes.orEmpty().filterNot { it.id.startsWith("program-clean-") }

    suspend fun load(e: RecipeIndex.Entry): Result<Recipe> = opened[e.id]?.let { Result.success(it) } ?: withContext(Dispatchers.IO) {
        runCatching { PhoneApp.app.store.loadRecipe(e) }.onSuccess { opened[e.id] = it }
    }

    suspend fun byId(id: String): Recipe? = _state.value.index.firstOrNull { it.id == id }?.let { load(it).getOrNull() }
}
