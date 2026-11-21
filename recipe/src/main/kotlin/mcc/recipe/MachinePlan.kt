package mcc.recipe

import mcc.machine.RunMode
import mcc.machine.RunRequest

/**
 * Turns a recipe machine step into the run requests sent one after another. Special modes mirror the
 * stock app's presets (de.silpion.mc2 model/Presets.java). The machine layer still validates every request.
 */
object MachinePlan {
    const val TURBO_SPEED = 10
    const val STEAM_TEMP_C = 120 // stock preset: temperature level 17

    fun expand(step: Step.Machine): List<RunRequest> = when (step.mode) {
        Mode.RUN -> step.segments.map { RunRequest(it.speed, it.tempC, it.seconds * 1000L, it.reverse, RunMode.NORMAL) }
        Mode.TURBO -> step.segments.map { RunRequest(TURBO_SPEED, 0, it.seconds * 1000L, false, RunMode.TURBO) }
        Mode.KNEAD -> step.segments.flatMap { knead(it.seconds * 1000L) }
        Mode.ROAST -> step.segments.flatMap { roast(it.seconds * 1000L, if (it.tempC > 0) it.tempC else 130) }
        // stock Varoma preset: speed 1 in reverse (MachineConfiguration(3, 1, 1, 17)), the blunt side keeps the water moving
        Mode.STEAM -> step.segments.map { RunRequest(1, STEAM_TEMP_C, it.seconds * 1000L, true, RunMode.STEAM) }
    }

    /** Stock kneading: 15 s reverse at speed 4, a rest (T-85 s, at least 1 s), then forward for the rest of T. */
    fun knead(totalMillis: Long): List<RunRequest> {
        val first = minOf(15_000L, totalMillis)
        val rest = minOf(if (totalMillis > 85_000L) totalMillis - 85_000L else 1_000L, totalMillis - first)
        val last = totalMillis - first - rest
        return listOf(
            RunRequest(4, 0, first, reverse = true, mode = RunMode.KNEAD),
            RunRequest(0, 0, rest, reverse = false, mode = RunMode.KNEAD),
            RunRequest(4, 0, last, reverse = false, mode = RunMode.KNEAD),
        ).filter { it.durationMillis > 0 }
    }

    /**
     * Stock roasting: a 35 s cycle repeated for the whole time, 5 s stirring at speed 1 then 30 s heating with the blade
     * still (mc2 Presets.roastingCookingUnit; CookingManager picks the part by elapsed % cycle). The board's roast mode
     * does not stir by itself: checked on the machine.
     */
    fun roast(totalMillis: Long, tempC: Int): List<RunRequest> {
        val out = ArrayList<RunRequest>()
        var left = totalMillis
        while (left > 0) {
            val stir = minOf(5_000L, left); out += RunRequest(1, tempC, stir, mode = RunMode.ROAST); left -= stir
            val heat = minOf(30_000L, left); if (heat > 0) out += RunRequest(0, tempC, heat, mode = RunMode.ROAST); left -= heat
        }
        return out
    }

    fun describe(step: Step.Machine): String {
        val total = step.segments.sumOf { it.seconds }
        val time = formatSeconds(total)
        return when (step.mode) {
            Mode.RUN -> if (step.segments.size == 1) {
                val s = step.segments[0]
                buildString {
                    append("$time · speed ${s.speed}")
                    if (s.tempC > 0) append(" · ${s.tempC} °C")
                    if (s.reverse) append(" · reverse")
                }
            } else "ramp: speed ${step.segments.joinToString(" → ") { it.speed.toString() }} · $time"
            Mode.TURBO -> "turbo · $time"
            Mode.KNEAD -> "knead · $time"
            Mode.ROAST -> "roast · $time · ${step.segments.first().tempC.takeIf { it > 0 } ?: 130} °C"
            Mode.STEAM -> "steam · $time"
        }
    }

    /** A friendly sentence for machine steps that have no text of their own, e.g. "Chop for 20 seconds at speed 4." */
    fun sentence(step: Step.Machine): String {
        val total = step.segments.sumOf { it.seconds }
        val t = when {
            total < 60 -> "$total seconds"
            total % 60 == 0 -> if (total == 60) "1 minute" else "${total / 60} minutes"
            else -> "${total / 60} min ${total % 60} s"
        }
        val s0 = step.segments.first()
        return when (step.mode) {
            Mode.TURBO -> "Turbo burst for $t."
            Mode.KNEAD -> "Knead the dough for $t."
            Mode.STEAM -> "Steam for $t."
            Mode.ROAST -> "Roast at ${if (s0.tempC > 0) s0.tempC else 130} °C for $t."
            Mode.RUN -> when {
                step.segments.size > 1 -> "Speed up from ${s0.speed} to ${step.segments.last().speed} over $t."
                s0.tempC > 0 -> "Cook at ${s0.tempC} °C for $t" + (if (s0.speed > 0) ", speed ${s0.speed}" else "") + (if (s0.reverse) ", reverse" else "") + "."
                s0.speed >= 6 -> "Blend for $t at speed ${s0.speed}."
                s0.speed >= 4 -> "Chop for $t at speed ${s0.speed}."
                else -> "Stir for $t at speed ${s0.speed}" + (if (s0.reverse) ", reverse" else "") + "."
            }
        }
    }

    fun formatSeconds(s: Int): String = when {
        s < 60 -> "$s s"
        s % 60 == 0 -> "${s / 60} min"
        else -> "${s / 60} min ${s % 60} s"
    }
}
