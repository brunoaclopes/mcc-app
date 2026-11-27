package mcc.recipe

/**
 * The original accessories of the Monsieur Cuisine Connect (manual IAN 290976, "Lieferumfang"), with the names Lidl's
 * own recipes use. The blade, bowl, lid and spatula are always there and are not tracked.
 */
enum class Accessory(val en: String, val pt: String) {
    /** Rühraufsatz: speeds 1 to 4 only, never Turbo, never the spatula (manual pp. 7, 12, 13). */
    MIXER("mixer attachment", "acessório batedor"),
    /** Kocheinsatz: sits in the bowl over the blade; at least 500 ml of water to cook or steam in it (p. 13, 24). */
    BASKET("cooking pot", "acessório de cozer"),
    /** Dampfgaraufsatz tief: locks onto the bowl in place of the lid, 1 litre of water in the bowl (p. 23). */
    STEAMER("deep steamer basket", "acessório para vapor fundo"),
    /** Dampfgaraufsatz flach: only on top of the deep one (p. 24). */
    STEAMER_FLAT("shallow steamer basket", "acessório para vapor plano"),
    /** Messbecher: in the lid opening; take it out only at speed 1 to 3 (p. 13). */
    CUP("measuring beaker", "copo de medição");

    fun label(lang: String?) = if (lang == "en") en else pt

    companion object {
        /** The fastest the mixer attachment may turn. */
        const val MIXER_MAX_SPEED = 4
    }
}

/**
 * What a recipe asks for, worked out from its step text (the same words the stock recipes, the converters and the AI
 * import use), so every recipe gets it without a format change.
 *
 * [steps] has one entry per recipe step: what to put in or take out before or during that step, and what is fitted
 * while it runs. A machine step the mixer attachment cannot do (above speed 4, Turbo, kneading) means it must come out
 * first even when the text doesn't say so: that removal is marked [Change.implied], and the cook screen asks for it
 * before such a step starts.
 */
data class AccessoryPlan(val needs: List<Accessory>, val steps: List<StepAccessories>) {
    data class Change(val accessory: Accessory, val put: Boolean, val implied: Boolean = false)
    data class StepAccessories(val changes: List<Change> = emptyList(), val fitted: Set<Accessory> = emptySet()) {
        /** The mixer attachment must come out before this step starts (speed above 4, Turbo or kneading). */
        val mustRemoveMixer get() = changes.any { it.accessory == Accessory.MIXER && !it.put && it.implied }
    }

    fun at(i: Int) = steps.getOrNull(i) ?: StepAccessories()

    companion object {
        fun of(r: Recipe): AccessoryPlan = Accessories.plan(r)
    }
}

object Accessories {
    private class Name(val regex: Regex, val accessories: List<Accessory>)

    // folded text (lower case, no accents); longer names first, an overlapping shorter match is ignored
    private val names = listOf(
        "acessorio para vapor completo|acessorios para vapor" to listOf(Accessory.STEAMER, Accessory.STEAMER_FLAT),
        "acessorio (?:para|de) vapor plano|vapor plano|shallow steamer(?: basket| insert| tray)?|flat steam(?:er|ing)(?: basket| insert| tray)?|dampfgaraufsatz flach|tabuleiro de vapor" to listOf(Accessory.STEAMER_FLAT),
        "acessorio (?:para|de) vapor(?: fundo)?|vapor fundo|recipiente (?:de|para) vapor|deep steamer(?: basket)?|steamer (?:attachment|basket)|steaming (?:attachment|tray|basket)|varoma|dampfgaraufsatz(?: tief)?" to listOf(Accessory.STEAMER),
        "acessorio batedor|batedor borboleta|borboleta|mixer attachment|mixing attachment|stirring attachment|whisk attachment|butterfly(?: whisk)?|ruhraufsatz" to listOf(Accessory.MIXER),
        "acessorio de cozer|acessorio para cozer|cesto(?: de cozedura| para cozer| de cozer)?|cooking (?:pot|attachment|basket|insert)|simmering basket|kocheinsatz|garkorb" to listOf(Accessory.BASKET),
        "copo (?:de medicao|medidor|doseador)|measuring (?:beaker|cup)|messbecher" to listOf(Accessory.CUP),
    ).map { (re, a) -> Name(Regex("\\b(?:$re)\\b"), a) }

    private val removeVerb = Regex("\\b(retire|retirar|retira|remova|remover|tire|tirar|levante|desencaixe|remove|take (?:it )?(?:out|off)|lift|detach|pull out)\\b")
    private val putVerb = Regex("\\b(coloque|colocar|coloca|insira|inserir|encaixe|encaixar|ponha|por|introduza|monte|volte a colocar|put|place|insert|attach|fit|set|replace|return)\\b")
    // the cooking attachment as a splash guard on the lid, the beaker used to measure: not in the bowl
    private val onLid = Regex("^[^.;]{0,25}\\b(na tampa|sobre a tampa|em cima da tampa|on (?:top of )?the (?:lid|mixing bowl lid|blender jug lid)|onto the lid)\\b")
    private val boundary = Regex("[.;:!?\\n]|,\\s|\\s(?:e|and|then|depois|seguidamente)\\s")

    private val marks = Regex("\\p{M}+")
    fun fold(s: String): String = marks.replace(java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD), "")

    /** Accessory mentions in [text], in order: which ones, and whether the text puts them in (true), takes them out (false) or only uses them (null). */
    fun mentions(text: String): List<Pair<Accessory, Boolean?>> {
        val t = fold(text)
        val hits = ArrayList<Triple<IntRange, Name, Boolean?>>()
        for (n in names) for (m in n.regex.findAll(t)) {
            if (hits.any { it.first.first <= m.range.last && m.range.first <= it.first.last }) continue
            // the clause before the name says what happens to it
            val start = boundary.findAll(t.substring(0, m.range.first)).lastOrNull()?.range?.last?.plus(1) ?: 0
            val before = t.substring(start, m.range.first)
            val r = removeVerb.findAll(before).lastOrNull()
            val p = putVerb.findAll(before).lastOrNull()
            val verb: Boolean? = when {
                r != null && (p == null || r.range.first > p.range.first) -> false
                p != null -> true
                else -> null
            }
            hits += Triple(m.range, n, verb)
        }
        return hits.sortedBy { it.first.first }.flatMap { (range, n, verb) ->
            val guard = n.accessories == listOf(Accessory.BASKET) && verb != false && onLid.containsMatchIn(t.substring(range.last + 1))
            if (guard) emptyList() else n.accessories.map { it to verb }
        }
    }

    fun plan(r: Recipe): AccessoryPlan {
        val fitted = LinkedHashSet<Accessory>()
        val needs = LinkedHashSet<Accessory>()
        val out = r.steps.map { s ->
            val changes = ArrayList<AccessoryPlan.Change>()
            fun put(a: Accessory, implied: Boolean = false) { if (fitted.add(a)) changes += AccessoryPlan.Change(a, true, implied) }
            fun take(a: Accessory, implied: Boolean = false) { if (fitted.remove(a)) changes += AccessoryPlan.Change(a, false, implied) }
            for ((a, verb) in mentions(s.text)) {
                if (a != Accessory.CUP) needs += a
                when {
                    // the beaker goes in and out all the time: shown when a step says so, never tracked as fitted
                    a == Accessory.CUP -> if (verb != null) changes += AccessoryPlan.Change(a, verb)
                    verb == false -> take(a)
                    else -> {
                        put(a)
                        // the shallow steamer only sits on the deep one
                        if (a == Accessory.STEAMER_FLAT) put(Accessory.STEAMER, implied = true)
                    }
                }
            }
            if (s is Step.Machine) {
                val fast = s.mode == Mode.TURBO || s.mode == Mode.KNEAD || s.segments.any { it.speed > Accessory.MIXER_MAX_SPEED }
                if (fast) {
                    take(Accessory.MIXER, implied = true)
                    take(Accessory.BASKET, implied = true)
                    take(Accessory.STEAMER_FLAT, implied = true); take(Accessory.STEAMER, implied = true)
                }
                if (s.mode == Mode.STEAM && Accessory.STEAMER !in fitted && Accessory.BASKET !in fitted) { put(Accessory.STEAMER, implied = true); needs += Accessory.STEAMER }
            }
            AccessoryPlan.StepAccessories(changes, fitted.toSet())
        }
        return AccessoryPlan(Accessory.entries.filter { it in needs }, out)
    }
}
