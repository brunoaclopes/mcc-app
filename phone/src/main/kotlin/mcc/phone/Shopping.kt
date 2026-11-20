package mcc.phone

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Shopping list built from recipe ingredients, kept on the phone. */
class Shopping(private val prefs: PhonePrefs) {
    @Serializable data class Item(val text: String, val recipe: String? = null, val checked: Boolean = false)

    private val json = Json { ignoreUnknownKeys = true }
    private val ser = ListSerializer(Item.serializer())
    private val _items = MutableStateFlow(runCatching { json.decodeFromString(ser, prefs.shopping) }.getOrDefault(emptyList()))
    val items: StateFlow<List<Item>> = _items

    private fun save(l: List<Item>) { _items.value = l; prefs.shopping = json.encodeToString(ser, l) }

    /** Adds what is not on the list yet (same text from the same recipe); returns how many were added. */
    fun add(texts: List<String>, recipe: String): Int {
        val cur = _items.value
        val new = texts.filter { t -> cur.none { it.text == t && it.recipe == recipe } }.map { Item(it, recipe) }
        if (new.isNotEmpty()) save(cur + new)
        return new.size
    }

    fun toggle(i: Item) = save(_items.value.map { if (it == i) it.copy(checked = !it.checked) else it })
    fun remove(i: Item) = save(_items.value - i)
    /** Undo of [remove]. */
    fun restore(i: Item) { if (i !in _items.value) save(_items.value + i) }
    fun clearChecked() = save(_items.value.filterNot { it.checked })

    fun asText(title: String): String = buildString {
        append(title).append('\n')
        _items.value.filterNot { it.checked }.groupBy { it.recipe }.forEach { (r, l) ->
            if (r != null) append('\n').append(r).append('\n')
            l.forEach { append("• ").append(it.text).append('\n') }
        }
    }
}
