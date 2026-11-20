package mcc.app

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Small synthesized sounds (soft bell / marimba tones), generated at start-up: no audio files to license, and the
 * tablet has no system UI sounds. Plays on the alarm stream so it is heard over a running motor.
 */
class Sounds(private val level: () -> Int) {
    enum class Cue { HELLO, KNOB, TAP, START, STOP, STEP, WEIGHED, DONE, FINISHED, ALERT, UNLOCK, LOCK, TICK, STANDBY }

    private val rate = 22050
    private val clips = HashMap<Cue, ShortArray>()
    private var track: AudioTrack? = null
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()

    private data class Note(val hz: Double, val at: Double, val len: Double, val vol: Double = 1.0)

    init {
        worker.execute {
            // notes: C5 523, E5 659, G5 784, A5 880, C6 1047, E6 1319, G6 1568
            clips[Cue.HELLO] = render(listOf(Note(784.0, 0.0, 0.25, 0.8), Note(1047.0, 0.1, 0.25, 0.8), Note(1319.0, 0.2, 0.3, 0.8),
                Note(1568.0, 0.3, 0.9), Note(2093.0, 0.3, 0.9, 0.35)))
            clips[Cue.KNOB] = render(listOf(Note(2000.0, 0.0, 0.028, 0.38)), bell = false)
            clips[Cue.TAP] = render(listOf(Note(1568.0, 0.0, 0.05, 0.35)), bell = false)
            clips[Cue.START] = render(listOf(Note(523.0, 0.0, 0.18), Note(784.0, 0.09, 0.3)))
            clips[Cue.STOP] = render(listOf(Note(784.0, 0.0, 0.16), Note(523.0, 0.09, 0.3)))
            clips[Cue.STEP] = render(listOf(Note(880.0, 0.0, 0.35), Note(1319.0, 0.14, 0.6)))
            clips[Cue.WEIGHED] = render(listOf(Note(1047.0, 0.0, 0.5), Note(1568.0, 0.0, 0.5, 0.4)))
            clips[Cue.DONE] = render(listOf(Note(659.0, 0.0, 0.3), Note(784.0, 0.12, 0.3), Note(1047.0, 0.24, 0.8)))
            clips[Cue.FINISHED] = render(listOf(Note(523.0, 0.0, 0.3), Note(659.0, 0.13, 0.3), Note(784.0, 0.26, 0.3),
                Note(1047.0, 0.39, 0.5), Note(784.0, 0.58, 0.25, 0.7), Note(1047.0, 0.72, 1.1), Note(1319.0, 0.72, 1.1, 0.5)))
            clips[Cue.ALERT] = render(listOf(Note(440.0, 0.0, 0.2), Note(440.0, 0.28, 0.2), Note(440.0, 0.56, 0.35)), bell = false)
            clips[Cue.UNLOCK] = render(listOf(Note(659.0, 0.0, 0.15, 0.7), Note(988.0, 0.07, 0.25, 0.7)))
            clips[Cue.LOCK] = render(listOf(Note(988.0, 0.0, 0.15, 0.7), Note(659.0, 0.07, 0.25, 0.7)))
            clips[Cue.TICK] = render(listOf(Note(1319.0, 0.0, 0.08, 0.5)), bell = false)
            clips[Cue.STANDBY] = render(listOf(Note(784.0, 0.0, 0.4, 0.6), Note(523.0, 0.18, 0.7, 0.6)))
        }
    }

    fun play(cue: Cue) {
        val lv = level()
        if (lv <= 0) return
        if (cue == Cue.KNOB) { knob(lv); return }
        worker.execute {
            val pcm = clips[cue] ?: return@execute
            try {
                track?.release()
                val t = AudioTrack(AudioManager.STREAM_ALARM, rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    pcm.size * 2, AudioTrack.MODE_STATIC)
                t.write(pcm, 0, pcm.size)
                val v = if (lv == 1) 0.35f else 1f
                @Suppress("DEPRECATION") t.setStereoVolume(v, v)
                t.play()
                track = t
            } catch (_: Exception) { }
        }
    }

    private var knobTrack: AudioTrack? = null
    private var lastKnob = 0L

    /** Knob clicks get their own reusable track, so turning never cuts off a chime that is playing. */
    private fun knob(lv: Int) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastKnob < 30) return
        lastKnob = now
        worker.execute {
            val pcm = clips[Cue.KNOB] ?: return@execute
            try {
                val t = knobTrack ?: AudioTrack(AudioManager.STREAM_ALARM, rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    pcm.size * 2, AudioTrack.MODE_STATIC).also { it.write(pcm, 0, pcm.size); knobTrack = it }
                val v = if (lv == 1) 0.35f else 1f
                @Suppress("DEPRECATION") t.setStereoVolume(v, v)
                t.stop(); t.reloadStaticData(); t.play()
            } catch (_: Exception) { }
        }
    }

    fun release() { worker.execute { track?.release(); track = null; knobTrack?.release(); knobTrack = null }; worker.shutdown() }

    /** Sine partials with an exponential decay ("bell"), or a plain soft-edged sine for beeps and ticks. */
    private fun render(notes: List<Note>, bell: Boolean = true): ShortArray {
        val total = notes.maxOf { it.at + it.len } + 0.05
        val out = DoubleArray((total * rate).toInt())
        for (n in notes) {
            val start = (n.at * rate).toInt(); val len = (n.len * rate).toInt()
            for (i in 0 until len) {
                val t = i / rate.toDouble()
                val attack = (t / 0.006).coerceAtMost(1.0)
                val env = if (bell) exp(-t * 5.5 / n.len) else attack * (1 - i / len.toDouble()).coerceAtLeast(0.0)
                val w = 2 * PI * n.hz * t
                val v = if (bell) sin(w) + 0.35 * sin(2 * w) * exp(-t * 9) + 0.12 * sin(3.01 * w) * exp(-t * 14) else sin(w)
                if (start + i < out.size) out[start + i] += v * env * attack * n.vol
            }
        }
        val peak = out.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1.0)
        return ShortArray(out.size) { (out[it] / peak * 0.8 * notes.maxOf { n -> n.vol } * Short.MAX_VALUE).toInt().toShort() }
    }
}
