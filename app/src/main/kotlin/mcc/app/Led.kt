package mcc.app

import android.os.SystemClock
import android.util.Log
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * The RGB ring LED (sysfs leds-mt65xx, 0..255 per channel, owned by uid system). One colour for the whole ring,
 * so the animations are colour and brightness over time. Runs on its own thread so it never delays the serial
 * loop, and only writes a channel when its value changes.
 *
 * Base states follow the stock app (steady white idle, pulsing while cooking, green when done, red on errors);
 * [MachineService] adds richer ones when animations are on (pulse speed follows the motor, colour follows the
 * bowl temperature, a weighing fill, a rainbow when a recipe ends, a strobe for turbo).
 */
class Led {
    data class Rgb(val r: Int, val g: Int, val b: Int) {
        fun scaled(f: Float) = Rgb((r * f).toInt(), (g * f).toInt(), (b * f).toInt())
        fun mix(o: Rgb, t: Float) = Rgb(lerp(r, o.r, t), lerp(g, o.g, t), lerp(b, o.b, t))
        private fun lerp(a: Int, b: Int, t: Float) = (a + (b - a) * t.coerceIn(0f, 1f)).toInt()
    }

    enum class Pattern { OFF, STEADY, PULSE, BLINK, STROBE, BREATHE }

    companion object {
        // Stock values: white is blue-only at 50 on this hardware (the diffuser makes it look white).
        val WHITE = Rgb(0, 0, 50)
        val GREEN = Rgb(0, 60, 0)
        val RED = Rgb(75, 0, 0)
        val AMBER = Rgb(80, 22, 0)
        val CYAN = Rgb(0, 45, 45)
        val OFF = Rgb(0, 0, 0)
        private const val BASE = "/sys/class/leds"

        /** Bowl temperature as a colour: cool white → amber (60 °C) → red (100 °C and up). */
        fun forTemperature(c: Int): Rgb = when {
            c <= 37 -> WHITE
            c < 60 -> WHITE.mix(AMBER, (c - 37) / 23f)
            else -> AMBER.mix(RED, (c - 60) / 40f)
        }

        private fun hue(h: Float): Rgb { // h in 0..1, full saturation, kept dim like the stock colours
            val x = (h - h.toInt()) * 6f
            val f = x - x.toInt()
            val (r, g, b) = when (x.toInt()) {
                0 -> Triple(1f, f, 0f); 1 -> Triple(1 - f, 1f, 0f); 2 -> Triple(0f, 1f, f)
                3 -> Triple(0f, 1 - f, 1f); 4 -> Triple(f, 0f, 1f); else -> Triple(1f, 0f, 1 - f)
            }
            return Rgb((r * 75).toInt(), (g * 60).toInt(), (b * 60).toInt())
        }
    }

    @Volatile private var color = WHITE
    @Volatile private var pattern = Pattern.STEADY
    @Volatile private var periodMs = 2000L
    /** A temporary effect (blink or rainbow) that overrides the base state until it expires. */
    @Volatile private var flashUntil = 0L
    @Volatile private var flashColor: Rgb? = GREEN // null = rainbow
    @Volatile private var flashStart = 0L
    @Volatile private var running = true
    /** Brightness multiplier, 0..1 (Settings). */
    @Volatile var brightness = 1f
    private val last = intArrayOf(-1, -1, -1)
    private var failed = false
    private val thread = Thread(::loop, "mcc-led").apply { isDaemon = true }

    fun start() {
        rainbow(1_600) // hello
        thread.start()
    }

    fun stop() {
        running = false
        thread.interrupt()
        write(OFF)
    }

    fun set(p: Pattern, c: Rgb, period: Long = 2000) { pattern = p; color = c; periodMs = period.coerceAtLeast(90) }

    /** While another app (the stock app) owns the machine, leave the LED alone. */
    @Volatile var suspended = false
        set(v) { field = v; if (v) last.fill(-1) }

    /** Blinks [c] for [millis], then returns to the base state (stock: done = green, error = red, 3 s). */
    fun flash(c: Rgb, millis: Long = 3_000) { flashColor = c; flashStart = SystemClock.elapsedRealtime(); flashUntil = flashStart + millis }

    /** A colour wheel that fades out after [millis]: hello at start-up, and a finished recipe. */
    fun rainbow(millis: Long = 4_000) { flashColor = null; flashStart = SystemClock.elapsedRealtime(); flashUntil = flashStart + millis }

    private fun loop() {
        while (running) {
            val now = SystemClock.elapsedRealtime()
            val fc = flashColor
            val p = periodMs
            val out = if (now < flashUntil) {
                if (fc == null) hue((now - flashStart) / 1600f).scaled(((flashUntil - now) / 600f).coerceAtMost(1f))
                else if ((now / 250) % 2 == 0L) fc else OFF
            } else when (pattern) {
                Pattern.OFF -> OFF
                Pattern.STEADY -> color
                Pattern.BLINK -> if ((now / (p / 2)) % 2 == 0L) color else OFF
                Pattern.STROBE -> if (now % p < p / 3) color else OFF
                Pattern.PULSE -> color.scaled(0.15f + 0.85f * (0.5f + 0.5f * sin(2 * PI * (now % p) / p.toDouble()).toFloat()))
                // slow triangle, never fully off: "waiting for you" states
                Pattern.BREATHE -> color.scaled(0.3f + 0.7f * (1f - abs(1f - 2f * ((now % p) / p.toFloat()))))
            }
            write(if (brightness >= 0.999f) out else out.scaled(brightness))
            val fast = now < flashUntil || pattern != Pattern.STEADY && pattern != Pattern.OFF
            try { Thread.sleep(if (fast) 40 else 150) } catch (_: InterruptedException) { }
        }
    }

    private fun write(c: Rgb) {
        if (failed || suspended) return
        val v = intArrayOf(c.r, c.g, c.b)
        val names = arrayOf("red", "green", "blue")
        for (i in 0..2) {
            if (v[i] == last[i]) continue
            try {
                File("$BASE/${names[i]}/brightness").writeText(v[i].coerceIn(0, 255).toString())
                last[i] = v[i]
            } catch (e: Exception) {
                failed = true
                Log.w(MachineService.TAG, "LED not writable: ${e.message}")
                return
            }
        }
    }
}
