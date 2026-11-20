package mcc.machine

import kotlin.math.abs

/**
 * Calms the scale reading for display. The tablet sits on the machine, so every tap on the screen presses on
 * the load cell, and at rest the reading flickers by a gram or two. A new value is shown only when the board
 * calls the scale stable and two board frames in a row agree, and only if it moves more than [deadband] grams
 * from what is shown. Readings within [deadband] of zero show as 0.
 *
 * Display only: safety checks and the weighing gauge use the raw reading.
 */
class ScaleFilter(private val deadband: Int = 3, private val agree: Int = 2) {
    var shown: Int? = null
        private set
    private var candidate = 0
    private var count = 0
    private var lastFrame: RxFrame? = null

    /** [grams] is the board reading (null while unknown, e.g. running or after a tare); [frame] is the frame it came from. */
    fun update(grams: Int?, frame: RxFrame?): Int? {
        if (grams == null || frame == null) { shown = null; count = 0; lastFrame = null; return null }
        if (frame === lastFrame) return shown // one board frame every ~500 ms; the loop runs every 100 ms
        lastFrame = frame
        if (!frame.scaleStable) { count = 0; return shown }
        if (count > 0 && abs(grams - candidate) <= deadband) count++ else { candidate = grams; count = 1 }
        val s = shown
        if (s == null) { shown = snap(grams); return shown }
        if (count >= agree && abs(grams - s) > deadband) shown = snap(grams)
        return shown
    }

    private fun snap(g: Int) = if (abs(g) <= deadband) 0 else g
}
