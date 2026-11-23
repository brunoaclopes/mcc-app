package mcc.phone

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mcc.app.AiClient
import mcc.app.RecipePublisher
import mcc.link.Command
import mcc.recipe.Recipe

/**
 * Recipe import on the phone: a web page (or pasted text) becomes a machine recipe through the AI proxy, in the
 * phone's language and translated to the other one, then goes to the recipe repository through the NAS upload
 * service (or stays on this phone). Same code and machine checks as the import on the machine ([AiClient]).
 * Runs in the app scope, so closing the sheet doesn't stop it.
 */
object Importer {
    enum class Stage { READING, TRANSLATING, PUBLISHING, WAITING }

    sealed class State {
        object Idle : State()
        data class Working(val stage: Stage, val since: Long) : State()
        data class Ready(val main: Recipe, val other: Recipe?, val changes: List<String>) : State()
        data class Failed(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state
    /** Whether the import sheet is showing. */
    val sheet = MutableStateFlow(false)
    /** Link shared from another app, to fill in. */
    val prefill = MutableStateFlow<String?>(null)
    /** One-off message for the snackbar, and a recipe id to open; the UI clears them. */
    val message = MutableStateFlow<String?>(null)
    val openId = MutableStateFlow<String?>(null)

    private val app get() = PhoneApp.app
    private fun ai() = app.prefs.let { AiClient(it.aiUrl, it.aiModel, it.aiKey) }
    val aiConfigured get() = ai().configured
    val canPublish get() = app.prefs.publishUrl.isNotBlank()

    fun open(link: String? = null) {
        if (link != null) prefill.value = link
        if (_state.value is State.Failed) _state.value = State.Idle
        sheet.value = true
    }

    fun start(input: String) {
        if (_state.value is State.Working || input.isBlank()) return
        val lang = app.prefs.languageCode()
        val other = if (lang == "pt") "en" else "pt"
        _state.value = State.Working(Stage.READING, System.currentTimeMillis())
        app.scope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val c = ai()
                    val imp = c.importRecipe(input.trim(), lang)
                    _state.value = State.Working(Stage.TRANSLATING, System.currentTimeMillis())
                    // the repository needs both languages; one retry, as translations sometimes come back short
                    val tr = runCatching { c.translate(imp.recipe, other) }.recoverCatching { c.translate(imp.recipe, other) }.getOrNull()
                    State.Ready(imp.recipe, tr, imp.changes)
                }
            }
            _state.value = res.getOrElse { State.Failed(it.message ?: it.javaClass.simpleName) }
            if (!sheet.value) message.value = when (val s = _state.value) {
                is State.Ready -> P.s.importReady(s.main.title); is State.Failed -> mcc.app.i18n.L.s.aiFailed(s.message); else -> null
            }
        }
    }

    /** Sends both languages to the repository, asks the machine to re-read the index and waits until the server lists it. */
    fun publish() {
        val ready = _state.value as? State.Ready ?: return
        val other = ready.other ?: return
        val pub = app.prefs.let { RecipePublisher(it.publishUrl, it.publishToken) }
        _state.value = State.Working(Stage.PUBLISHING, System.currentTimeMillis())
        app.scope.launch {
            val pt = if (ready.main.language == "pt") ready.main else other
            val sent = withContext(Dispatchers.IO) { runCatching { pub.publish(RecipePublisher.keyFor(pt.title), listOf(ready.main, other)) } }
            val key = sent.getOrElse { e ->
                _state.value = ready
                message.value = P.s.publishFailed(e.message ?: e.javaClass.simpleName)
                return@launch
            }
            _state.value = State.Working(Stage.WAITING, System.currentTimeMillis())
            val id = "$key-${ready.main.language}"
            LinkClient.send(Command.Refresh)
            // the NAS rebuilds and redeploys the server: usually under a minute or two
            var live = false
            for (i in 0 until 30) {
                live = withContext(Dispatchers.IO) { runCatching { app.store.loadIndex(true).first?.recipes?.any { it.id == id } == true }.getOrDefault(false) }
                if (live) break
                delay(10_000)
            }
            Recipes.refresh(force = false)
            if (live) LinkClient.send(Command.Refresh)
            _state.value = State.Idle
            sheet.value = false
            message.value = if (live) P.s.published(ready.main.title) else P.s.publishedPending(ready.main.title)
            if (live) openId.value = id
        }
    }

    /** Keeps the recipe only on this phone ("My recipes"), as the machine does with its own imports. */
    fun keepOnPhone() {
        val ready = _state.value as? State.Ready ?: return
        app.scope.launch {
            val saved = withContext(Dispatchers.IO) { app.store.saveMine(ready.main).also { r -> ready.other?.let { app.store.saveMine(it) } } }
            Recipes.refresh(force = false)
            _state.value = State.Idle
            sheet.value = false
            message.value = P.s.savedOnPhone(saved.title)
            openId.value = saved.id
        }
    }

    fun discard() { if (_state.value !is State.Working) _state.value = State.Idle }
}
