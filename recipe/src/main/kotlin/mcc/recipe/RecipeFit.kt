package mcc.recipe

/**
 * Makes a recipe fit this machine's limits, for recipes from outside (AI, other machines). The machine layer
 * would refuse an out-of-range step anyway; fixing it here means the recipe can still be cooked, and every
 * change is listed so the user can see it.
 */
object RecipeFit {
    const val MAX_SPEED_HEAT = 3
    const val MAX_SPEED_REVERSE = 3
    const val MAX_SECONDS = 99 * 60
    const val MAX_SECONDS_FAST = 15 * 60
    const val STEAM_C = 120

    data class Result(val recipe: Recipe, val changes: List<String>)

    /** [keep]: special modes switched on in this app; steps in other special modes become plain run steps. */
    fun fit(r: Recipe, keep: Set<Mode> = emptySet()): Result {
        val changes = ArrayList<String>()
        val steps = r.steps.mapIndexed { i, s ->
            if (s !is Step.Machine) return@mapIndexed s
            val n = i + 1
            var segs = s.segments
            if (s.mode in keep && s.mode != Mode.RUN && s.mode != Mode.TURBO) return@mapIndexed s // the machine layer checks these
            when (s.mode) {
                Mode.RUN, Mode.TURBO -> {}
                Mode.KNEAD -> { changes += "step $n: knead → speed 4 (knead mode is not enabled)"; segs = segs.map { it.copy(speed = 4, tempC = 0, reverse = false) } }
                Mode.ROAST -> { changes += "step $n: roast → speed 1 with heat (roast mode is not enabled)"; segs = segs.map { it.copy(speed = 1, tempC = if (it.tempC > 0) it.tempC else 120) } }
                Mode.STEAM -> { changes += "step $n: steam → $STEAM_C °C, speed 1 (steam mode is not enabled)"; segs = segs.map { it.copy(speed = 1, tempC = STEAM_C, reverse = false) } }
            }
            val mode = if (s.mode == Mode.TURBO) Mode.TURBO else Mode.RUN
            segs = segs.map { g ->
                var x = g.copy(speed = g.speed.coerceIn(0, 10), tempC = g.tempC.coerceIn(0, 130), seconds = g.seconds.coerceIn(1, MAX_SECONDS))
                if (x.tempC in 1..36) x = x.copy(tempC = 37)
                if (x.tempC > 0 && x.speed > MAX_SPEED_HEAT) { changes += "step $n: speed ${x.speed} → $MAX_SPEED_HEAT while heating"; x = x.copy(speed = MAX_SPEED_HEAT) }
                if (x.reverse && x.speed > MAX_SPEED_REVERSE) { changes += "step $n: reverse speed ${x.speed} → $MAX_SPEED_REVERSE"; x = x.copy(speed = MAX_SPEED_REVERSE) }
                if (x.speed > 6 && x.seconds > MAX_SECONDS_FAST) { changes += "step $n: ${x.seconds / 60} min at speed ${x.speed} → 15 min"; x = x.copy(seconds = MAX_SECONDS_FAST) }
                if (x.speed == 0 && x.tempC == 0 && mode == Mode.RUN) { changes += "step $n: a segment with no speed and no heat was dropped"; null } else x
            }.filterNotNull()
            if (segs.isEmpty()) Step.Instruction(s.text.ifBlank { "…" }) else Step.Machine(s.text, mode, segs)
        }
        return Result(r.copy(steps = steps), changes)
    }

    /** Problems the machine layer would refuse (empty when the recipe fits). */
    fun problems(r: Recipe, keep: Set<Mode> = emptySet()): List<String> = fit(r, keep).changes

    val SPECIAL = setOf(Mode.KNEAD, Mode.ROAST, Mode.STEAM)

    fun usesSpecial(r: Recipe) = r.steps.any { it is Step.Machine && it.mode in SPECIAL }
}
