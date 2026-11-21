package mcc.recipe

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Cooking a recipe for more or fewer people. Amounts are scaled in the ingredient list, the weigh steps (grams and
 * text) and the step texts. Machine times and temperatures are never touched, and neither is anything that is not an
 * amount: "1,5 cm", "180 ºC", "45 minutos", "(tipo 405)", "3,5 % de gordura".
 *
 * Step texts are scaled only where a number is followed by a weight/volume unit ("200 g de farinha") or by a word an
 * ingredient line starts with ("3 gemas", "1 pitada de sal"), so plain counts like "corte em 4 pedaços" stay as written.
 */
object Servings {
    /** "4 portions" → (4, "portions"); null when the recipe does not say how many it serves. */
    data class Base(val count: Int, val unit: String)

    fun base(r: Recipe): Base? = parse(r.servings)

    fun parse(s: String?): Base? {
        val m = Regex("""^\s*(\d{1,3})\s*(.*)$""").find(s ?: return null) ?: return null
        val n = m.groupValues[1].toInt().takeIf { it > 0 } ?: return null
        return Base(n, m.groupValues[2].trim())
    }

    /** Choices offered for this recipe: 1 up to three times the original. */
    fun range(base: Base): IntRange = 1..maxOf(base.count * 3, 4).coerceAtMost(36)

    /** The recipe scaled from its own servings to [people]; the recipe itself when it has no servings or nothing changes. */
    fun scale(r: Recipe, people: Int): Recipe {
        val base = base(r) ?: return r
        if (people == base.count || people < 1) return r
        val k = people.toDouble() / base.count
        val comma = r.language == "pt" || r.language == "de" || (r.language == null && looksComma(r))
        val heads = r.ingredients.flatMap { it.items }.mapNotNull { headWord(it.text) }.map(::stem).toSet()
        val s = Scaler(k, comma, heads, r.language ?: if (comma) "pt" else "en")
        return r.copy(
            servings = "$people ${base.unit}".trim(),
            ingredients = r.ingredients.map { g ->
                g.copy(group = g.group?.let { s.group(it, base, people) }, items = g.items.map { Ingredient(s.ingredient(it.text)) })
            },
            steps = r.steps.map { st ->
                when (st) {
                    is Step.Instruction -> st.copy(text = s.step(st.text))
                    is Step.Weigh -> st.copy(text = s.step(st.text), grams = s.grams(st.grams))
                    is Step.Machine -> st.copy(text = s.step(st.text))
                }
            },
        )
    }

    private fun looksComma(r: Recipe) = r.ingredients.any { g -> g.items.any { Regex("""\d,\d""").containsMatchIn(it.text) } }

    // ---- amounts ----

    private const val FRACTIONS = "½¼¾⅓⅔"
    /** 1,5 · 1.5 · 1½ · 1 ½ · 1/2 · ½ · 12 */
    private const val NUM = """(?:\d+[.,]\d+|\d+\s?[½¼¾⅓⅔]|\d+/\d+|\d+|[½¼¾⅓⅔])"""
    private val UNITS_SMALL = setOf("g", "gr", "ml", "cl", "dl")
    private val UNITS_BIG = setOf("kg", "l")
    /** Words that make a number something other than an amount, even if an ingredient line happens to start with one. */
    private val NEVER = setOf("cm", "mm", "min", "minuto", "minute", "segundo", "second", "seg", "sec", "hora", "hour", "h", "dia", "day",
        "vez", "veze", "time", "grau", "degree", "velocidade", "speed", "nível", "nivel", "level", "passo", "step", "°c", "ºc", "°", "º", "%", "x")
    private val NOT_AFTER = setOf("tipo", "type", "classe", "class", "velocidade", "speed", "nível", "nivel", "level", "passo", "step", "nº", "no.")
    private val FILLER = setOf("de", "of", "do", "da", "dos", "das")

    private fun value(t: String): Double {
        val s = t.replace(" ", "")
        val f = s.lastOrNull()?.takeIf { it in FRACTIONS }
        val frac = when (f) { '½' -> 0.5; '¼' -> 0.25; '¾' -> 0.75; '⅓' -> 1 / 3.0; '⅔' -> 2 / 3.0; else -> 0.0 }
        val whole = if (f != null) s.dropLast(1) else s
        return frac + when {
            whole.isEmpty() -> 0.0
            '/' in whole -> whole.split('/').let { it[0].toDouble() / it[1].toDouble().coerceAtLeast(1.0) }
            else -> whole.replace(',', '.').toDouble()
        }
    }

    /** The word an ingredient line starts with after its amount: "10 batatas…" → batatas, "200 g de farinha" → farinha. */
    private fun headWord(text: String): String? {
        val m = Regex("""^\s*$NUM\s*(?:[-–]\s*$NUM\s*)?(.*)$""").find(text) ?: return null
        val words = m.groupValues[1].split(Regex("""\s+""")).filter { it.isNotBlank() }
        var i = 0
        if (i < words.size && clean(words[i]) in UNITS_SMALL + UNITS_BIG) i++
        if (i < words.size && clean(words[i]) in FILLER) i++
        val w = words.getOrNull(i) ?: return null
        return clean(w).takeIf { (it.length >= 2 || w.endsWith(".")) && it.isNotEmpty() && stem(it) !in NEVER }
    }

    private fun clean(w: String) = w.lowercase().trim(',', ';', ':', '(', ')', '!', '?', '.')
    private fun stem(w: String) = clean(w).removeSuffix("s")

    private class Scaler(val k: Double, val comma: Boolean, val heads: Set<String>, val lang: String) {
        private val amount = Regex("""(?<![\d.,/])($NUM)(\s*[-–]\s*($NUM))?(?![\d/])(\s*)([^\s\d]*)""")

        fun grams(g: Int) = mass(g * k).roundToInt().coerceIn(1, 5000)

        /** The leading amount, whatever it counts, plus every weight or volume elsewhere ("(1 kg)", "(sumo; 80 g)"). */
        fun ingredient(t: String) = replace(t) { start, unit -> start == 0 || isUnit(unit) }

        fun step(t: String) = replace(t) { start, unit ->
            isUnit(unit) || (stem(unit) in heads && stem(unit) !in NEVER && !afterNot(t, start))
        }

        /** "Para 4 doses (forma 31 x 24 cm)" → "Para 6 doses (…)"; weights in a heading scale too. */
        fun group(t: String, base: Base, people: Int): String {
            val unit = base.unit.lowercase().split(' ').firstOrNull().orEmpty()
            val counted = Regex("""(?<![\d.,])${base.count}(\s+)(${Regex.escape(unit)}\b)""", RegexOption.IGNORE_CASE)
            val t2 = if (unit.isNotEmpty()) t.replace(counted) { "$people${it.groupValues[1]}${it.groupValues[2]}" } else t
            return replace(t2) { _, u -> isUnit(u) }
        }

        private fun afterNot(t: String, start: Int): Boolean {
            val before = t.substring(0, start).trimEnd().substringAfterLast(' ').lowercase().trim('(')
            return before in NOT_AFTER
        }

        private fun isUnit(w: String) = clean(w) in UNITS_SMALL || clean(w) in UNITS_BIG

        private inline fun replace(t: String, crossinline want: (Int, String) -> Boolean): String = amount.replace(t) { m ->
            val unit = m.groupValues[5]
            if (!want(m.range.first, unit)) return@replace m.value
            val u = clean(unit)
            val kind = when {
                u in UNITS_SMALL -> Kind.MASS; u in UNITS_BIG -> Kind.BIG
                stem(u) in WHOLE || (stem(u) == "egg" || stem(u) == "large" || stem(u) == "medium") && "egg" in t.lowercase() -> Kind.WHOLE
                else -> Kind.COUNT
            }
            val old = m.groupValues[3].ifEmpty { m.groupValues[1] }.let(::value)
            val new = old * k
            val a = fmt(value(m.groupValues[1]) * k, kind)
            val b = m.groupValues[3].takeIf { it.isNotEmpty() }?.let { "–" + fmt(value(it) * k, kind) } ?: ""
            val now = if (kind == Kind.WHOLE) whole(new) else rounded(new)
            val word = if ((kind == Kind.COUNT || kind == Kind.WHOLE) && (old > 1) != (now > 1)) inflect(unit, now > 1, t.substring(m.range.last + 1)) else unit
            a + b + m.groupValues[4] + word
        }

        /** "1 ovo" → "2 ovos", "2 dentes" → "1 dente", "1 egg (medium)" → "2 eggs (medium)". Only the counted word itself. */
        private fun inflect(raw: String, plural: Boolean, after: String): String {
            val core = raw.trimEnd(',', ';', ':', '.', ')', '!', '?')
            val tail = raw.substring(core.length)
            if (core.length < 3 || !core.all { it.isLetter() } || clean(core) in ABBREV) return raw
            val w = if (lang == "pt") {
                if (plural) when {
                    core.last().lowercaseChar() in "aeiouéóá" -> core + "s"
                    core.last().lowercaseChar() in "rz" -> core + "es"
                    else -> core
                } else when {
                    core.endsWith("res") || core.endsWith("zes") -> core.dropLast(2)
                    core.endsWith("s") && core[core.length - 2].lowercaseChar() in "aeiouéóá" && !core.endsWith("ões") && !core.endsWith("ães") -> core.dropLast(1)
                    else -> core
                }
            } else {
                // English puts adjectives first ("1 garlic clove"): only inflect a word the amount ends with
                val next = after.trimStart()
                if (tail.isEmpty() && !(next.isEmpty() || next[0] in "(,.;" || next.startsWith("of ") || next.startsWith("each"))) return raw
                if (plural) when {
                    Regex("(s|x|ch|sh)$").containsMatchIn(core) -> core + "es"
                    core.endsWith("y") && core[core.length - 2] !in "aeiou" -> core.dropLast(1) + "ies"
                    core.endsWith("o") && core[core.length - 2] !in "aeiou" -> core + "es"
                    else -> core + "s"
                } else when {
                    core.endsWith("ies") -> core.dropLast(3) + "y"
                    core.endsWith("oes") && core.length > 4 -> core.dropLast(2)
                    Regex("(x|ch|sh|ss)es$").containsMatchIn(core) -> core.dropLast(2)
                    core.endsWith("s") && !core.endsWith("ss") -> core.dropLast(1)
                    else -> core
                }
            }
            return w + tail
        }

        private fun fmt(v: Double, kind: Kind): String = when (kind) {
            Kind.MASS -> mass(v).roundToInt().toString()
            Kind.BIG -> {
                val r = (v * 10).roundToInt() / 10.0
                if (r == floor(r)) r.toInt().toString() else r.toString().let { if (comma) it.replace('.', ',') else it }
            }
            Kind.COUNT -> count(v)
            Kind.WHOLE -> whole(v).toInt().toString()
        }
    }

    private enum class Kind { MASS, BIG, COUNT, WHOLE }
    /** Things nobody halves: eggs, yolks and whites are rounded to whole ones (at least 1). */
    private val WHOLE = setOf("ovo", "gema", "clara", "egg", "yolk")
    private fun whole(v: Double) = maxOf(1.0, floor(v + 0.5))
    private val ABBREV = setOf("tsp", "tbsp", "emb", "pkt", "pck", "sachet")

    private fun rounded(v: Double): Double = when {
        v < 1 -> maxOf(0.25, (v * 4).roundToInt() / 4.0)
        v < 10 -> (v * 2).roundToInt() / 2.0
        else -> v.roundToInt().toDouble()
    }

    /** Grams or millilitres the way a recipe would write them: whole numbers, 5s from 20, 10s from 200. */
    private fun mass(v: Double): Double = when {
        v < 20 -> maxOf(1.0, v.roundToInt().toDouble())
        v < 200 -> (v / 5).roundToInt() * 5.0
        else -> (v / 10).roundToInt() * 10.0
    }

    /** Eggs, spoons, pinches: quarters below 1, halves below 10, whole numbers above. */
    private fun count(v: Double): String {
        val q = rounded(v)
        val whole = floor(q).toInt()
        val frac = when { abs(q - whole - 0.5) < 1e-6 -> "½"; abs(q - whole - 0.25) < 1e-6 -> "¼"; abs(q - whole - 0.75) < 1e-6 -> "¾"; else -> "" }
        return if (whole == 0) frac.ifEmpty { "¼" } else "$whole$frac"
    }
}

/** This recipe for [people]. */
fun Recipe.forPeople(people: Int): Recipe = Servings.scale(this, people)
