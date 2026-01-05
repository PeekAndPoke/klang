package io.peekandpoke.klang.tones.midi

import io.peekandpoke.klang.tones.note.Note
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.round

object Midi {
    /**
     * Returns true if the argument is a valid MIDI number (0-127).
     */
    fun isMidi(n: Number): Boolean {
        val d = n.toDouble()
        return d >= 0 && d <= 127
    }

    fun isMidi(s: String): Boolean {
        val n = s.toDoubleOrNull() ?: return false
        return n >= 0 && n <= 127
    }

    /**
     * Get the note MIDI number (a number between 0 and 127).
     * Returns null if not a valid note name.
     */
    fun toMidi(note: String): Int? {
        if (isMidi(note)) return note.toDouble().toInt()
        val n = Note.get(note)
        return if (n.empty) null else n.midi
    }

    fun toMidi(note: Number): Int? {
        if (isMidi(note)) return note.toInt()
        return null
    }

    fun toMidi(note: Note): Int? = note.midi

    /**
     * Get the frequency in hertz from MIDI number.
     *
     * @param midi The MIDI note number.
     * @param tuning A4 tuning frequency in Hz (440 by default).
     */
    fun midiToFreq(midi: Double, tuning: Double = 440.0): Double {
        return 2.0.pow((midi - 69.0) / 12.0) * tuning
    }

    /**
     * Get the MIDI number from a frequency in hertz.
     * The MIDI number can contain decimals (with two digits precision).
     */
    fun freqToMidi(freq: Double): Double {
        val v = (12.0 * log2(freq / 440.0)) + 69.0
        return round(v * 100.0) / 100.0
    }

    /** List of sharp pitch classes. */
    private val SHARPS = "C C# D D# E F F# G G# A A# B".split(" ")

    /** List of flat pitch classes. */
    private val FLATS = "C Db D Eb E F Gb G Ab A Bb B".split(" ")

    /**
     * Given a MIDI number, returns a note name.
     */
    fun midiToNoteName(midi: Double, sharps: Boolean = false, pitchClass: Boolean = false): String {
        if (midi.isNaN() || midi.isInfinite()) return ""
        val m = round(midi).toInt()
        val pcs = if (sharps) SHARPS else FLATS
        val pc = pcs[(m % 12 + 12) % 12]
        if (pitchClass) {
            return pc
        }
        val o = floor(m.toDouble() / 12.0).toInt() - 1
        return pc + o
    }

    /**
     * Returns the chroma of a MIDI number (0-11).
     */
    fun chroma(midi: Double): Int {
        return (round(midi).toInt() % 12 + 12) % 12
    }

    /**
     * Given a list of MIDI numbers or a chroma string, returns the pitch class set (unique chroma numbers).
     */
    fun pcSet(chroma: String): List<Int> {
        // If it's a chroma string, return indices of '1's
        return chroma.mapIndexedNotNull { index, c ->
            if (index < 12 && c == '1') index else null
        }
    }

    fun pcSet(notes: List<Number>): List<Int> {
        // If it's a list, return unique chromas
        return notes.map { chroma(it.toDouble()) }
            .distinct()
            .sorted()
    }

    /**
     * Returns a function that finds the nearest MIDI note of a pitch class set.
     */
    fun pcSetNearest(chroma: String): (Double) -> Double? = createNearest(pcSet(chroma))
    fun pcSetNearest(notes: List<Number>): (Double) -> Double? = createNearest(pcSet(notes))

    private fun createNearest(set: List<Int>): (Double) -> Double? {
        return { midi ->
            val ch = chroma(midi)
            var found: Double? = null
            // Look for the closest chroma by searching both directions (up and down)
            for (i in 0 until 12) {
                val chUp = (ch + i) % 12
                val chDown = (ch - i + 12) % 12
                if (chUp in set) {
                    found = midi + i
                    break
                }
                if (chDown in set) {
                    found = midi - i
                    break
                }
            }
            found
        }
    }

    /**
     * Returns a function to map a pitch class set over any note.
     * step 0 means the first note, step 1 the second, and so on.
     */
    fun pcSetSteps(chroma: String, tonic: Double): (Int) -> Double = createSteps(pcSet(chroma), tonic)
    fun pcSetSteps(notes: List<Number>, tonic: Double): (Int) -> Double = createSteps(pcSet(notes), tonic)

    private fun createSteps(set: List<Int>, tonic: Double): (Int) -> Double {
        val len = set.size
        return { step ->
            if (len == 0) {
                tonic + step
            } else {
                val index = if (step < 0) (len - (-step % len)) % len else step % len
                val octaves = floor(step.toDouble() / len).toInt()
                (set[index] + octaves * 12 + tonic)
            }
        }
    }

    /**
     * Returns a function to map a pitch class set over any note.
     * Same as pcsetSteps, but returns null for degree 0.
     */
    fun pcSetDegrees(chroma: String, tonic: Double): (Int) -> Double? {
        val steps = pcSetSteps(chroma, tonic)
        return { degree ->
            if (degree == 0) null else steps(if (degree > 0) degree - 1 else degree)
        }
    }

    fun pcSetDegrees(notes: List<Number>, tonic: Double): (Int) -> Double? {
        val steps = pcSetSteps(notes, tonic)
        return { degree ->
            if (degree == 0) null else steps(if (degree > 0) degree - 1 else degree)
        }
    }
}
