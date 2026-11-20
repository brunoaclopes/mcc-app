package mcc.app.ui

/** Picks a cover illustration for recipes without a photo, from the id, title and tags. */
object Covers {
    private val rules: List<Pair<Regex, Icon>> = listOf(
        "egg|ovo" to Icon.EGG,
        "rice|arroz|risot" to Icon.RICE,
        "steam|vapor|varoma" to Icon.STEAM,
        "chocolat" to Icon.CHOCO,
        "smoothie|batido|juice|sumo|shake|drink|bebida|cocktail" to Icon.GLASS,
        "dough|massa|bread|p[aã]o|pizza|knead|amass" to Icon.KNEAD,
        "soup|sopa|creme|cream|reheat|reaquec|sauce|molho|stew|guisado" to Icon.SOUP,
        "clean|limpeza" to Icon.DROP,
        "meat|carne|chicken|frango|beef|pork|porco" to Icon.FLAME,
    ).map { (k, v) -> Regex(k, RegexOption.IGNORE_CASE) to v }

    fun iconFor(id: String, title: String, tags: List<String>): Icon {
        val text = "$id $title ${tags.joinToString(" ")}"
        return rules.firstOrNull { it.first.containsMatchIn(text) }?.second ?: Icon.POT
    }
}
