package io.peekandpoke.klang.tones.note

import io.peekandpoke.klang.tones.pitch.NamedPitch
import io.peekandpoke.klang.tones.pitch.Pitch
import io.peekandpoke.klang.tones.pitch.PitchCoordinates
import io.peekandpoke.klang.tones.pitch.TonalPitch
import kotlin.math.pow

typealias NoteWithOctave = String
typealias PcName = String
typealias NoteName = String

/**
 * Represents a musical note.
 */
data class Note(
    /** The step number (0 = C, 1 = D, ... 6 = B). */
    override val step: Int,
    /** The number of alterations (-2 = 'bb', -1 = 'b', 0 = '', 1 = '#', ...). */
    override val alt: Int,
    /** The octave number. */
    override val oct: Int? = null,
    /** The direction (for intervals, usually null for notes). */
    override val dir: Int? = null,
    /** Whether the note is empty (represents "no note"). */
    val empty: Boolean = false,
    /** The full name of the note (e.g., "C#4"). */
    override val name: String = "",
    /** The letter name of the note (e.g., "C"). */
    val letter: String = "",
    /** The accidental string (e.g., "#", "bb"). */
    val acc: String = "",
    /** The pitch class name (e.g., "C#"). */
    val pc: PcName = "",
    /** The chroma of the note (0-11). */
    val chroma: Int = -1,
    /** The height of the note (midi-like absolute value). */
    val height: Int = -1,
    /** The pitch coordinates. */
    val coord: PitchCoordinates? = null,
    /** The MIDI number of the note, if applicable. */
    val midi: Int? = null,
    /** The frequency of the note in Hz, if applicable. */
    val freq: Double? = null,
) : NamedPitch, Pitch {
    companion object {
        /**
         * Represents an empty or invalid note.
         */
        val NoNote = Note(
            step = -1,
            alt = -1,
            empty = true,
            name = "",
            letter = "",
            acc = "",
            pc = "",
            chroma = -1,
            height = -1,
            coord = null,
            midi = null,
            freq = null
        )

        /**
         * Returns a [Note] from a given source (string, Note, or Pitch).
         */
        fun get(src: Any?): Note {
            return when (src) {
                is String -> parse(src)
                is Note -> src
                is Pitch -> get(pitchName(src))
                is NamedPitch -> get(src.name)
                else -> NoNote
            }
        }

        /**
         * Tokenizes a note string into [letter, accidental, octave, rest].
         * @private
         */
        fun tokenize(str: String): List<String> {
            val m = REGEX.matchEntire(str)
            return if (m != null) {
                val groups = m.groupValues
                listOf(
                    groups[1].uppercase(),
                    groups[2].replace("x", "##"),
                    groups[3],
                    groups[4]
                )
            } else {
                listOf("", "", "", "")
            }
        }

        /**
         * Converts pitch coordinates to a [Note].
         * @private
         */
        fun fromCoord(noteCoord: PitchCoordinates): Note {
            return get(TonalPitch.pitch(noteCoord))
        }

        /**
         * Returns the enharmonic note of [noteName] that matches the [destination] pitch class.
         */
        fun enharmonic(noteName: String, destination: String = ""): String {
            val src = get(noteName)
            if (src.empty) return ""

            val dest = if (destination.isEmpty()) {
                val sharps = src.alt < 0
                get(fromMidi(src.midi ?: src.chroma, sharps = sharps, pitchClass = true))
            } else {
                get(destination)
            }

            if (dest.empty || dest.chroma != src.chroma) {
                return ""
            }

            if (src.oct == null) {
                return dest.pc
            }

            // detect any octave overflow
            // In TonalJS: const srcChroma = src.chroma - src.alt;
            // SEMI[src.step] is essentially src.chroma - src.alt
            val srcChroma = SEMI[src.step]
            val destChroma = SEMI[dest.step]

            val destOctOffset = when {
                srcChroma > 11 || destChroma < 0 -> -1
                srcChroma < 0 || destChroma > 11 -> 1
                // NEW: handle B# / Cb octave changes within the 0-11 range
                srcChroma == 0 && destChroma == 11 -> -1
                srcChroma == 11 && destChroma == 0 -> 1
                else -> 0
            }

            val destOct = src.oct!! + destOctOffset
            return dest.pc + destOct
        }

        /**
         * Returns the note name of a given midi number.
         */
        fun fromMidi(midi: Int, sharps: Boolean = false, pitchClass: Boolean = false): String {
            val chroma = (midi % 12 + 12) % 12
            val oct = (midi / 12) - 1

            val names = if (sharps) {
                arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
            } else {
                arrayOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
            }

            val resPc = names[chroma]

            return if (pitchClass) resPc else "$resPc$oct"
        }

        /**
         * Returns a list of sorted unique note names.
         */
        fun sortedUniqNames(notes: List<String>): List<String> {
            return notes.map { get(it) }
                .filter { !it.empty }
                .distinctBy { it.pc }
                .sortedWith(Ascending)
                .map { it.pc }
        }

        /**
         * Returns a list of note names.
         * If no argument is provided, returns natural note names ["C", "D", "E", "F", "G", "A", "B"].
         */
        fun names(notes: List<Any?>? = null): List<String> {
            if (notes == null) {
                return listOf("C", "D", "E", "F", "G", "A", "B")
            }
            return notes.map { get(it).name }.filter { it.isNotEmpty() }
        }

        /**
         * Comparator to sort notes by height in ascending order.
         */
        val Ascending: Comparator<Note> = compareBy { it.height }

        /**
         * Comparator to sort notes by height in descending order.
         */
        val Descending: Comparator<Note> = compareBy<Note> { it.height }.reversed()

        /**
         * Sorts a list of notes by height.
         */
        fun sortedNames(notes: List<String>): List<String> {
            return notes.map { get(it) }
                .filter { !it.empty }
                .sortedWith(Ascending)
                .map { it.name }
        }

        /**
         * Sorts a list of notes by height and removes duplicates.
         */
        fun sortedUniqNoteNames(notes: List<String>): List<String> {
            return sortedNames(notes).distinct()
        }

        /**
         * Simplify a note name.
         *
         * @param noteName The note name or [Note] object.
         * @return The simplified note name.
         */
        fun simplify(noteName: Any?): String {
            val n = get(noteName)
            if (n.empty) return ""
            return fromMidi(
                midi = n.midi ?: n.height,
                sharps = n.alt > 0,
                pitchClass = n.oct == null
            )
        }

        /**
         * Returns true if the object is a valid [Note].
         */
        fun isNote(src: Any?): Boolean {
            return src is Note && !src.empty
        }

        /**
         * Converts a step number to its letter representation.
         */
        fun stepToLetter(step: Int): String = "CDEFGAB".getOrNull(step)?.toString() ?: ""

        /**
         * Converts an alteration number to its accidental string representation.
         */
        fun altToAcc(alt: Int): String = if (alt < 0) "b".repeat(-alt) else "#".repeat(alt)

        /**
         * Converts an accidental string to its alteration number.
         */
        fun accToAlt(acc: String): Int = if (acc.isEmpty()) 0 else if (acc[0] == 'b') -acc.length else acc.length

        private val REGEX = Regex("""^([a-gA-G]?)(#{1,}|b{1,}|x{1,}|)(-?\d*)\s*(.*)$""")
        private val SEMI = intArrayOf(0, 2, 4, 5, 7, 9, 11)

        private fun parse(noteName: NoteName): Note {
            val tokens = tokenize(noteName)
            if (tokens[0] == "" || tokens[3] != "") {
                return NoNote
            }

            val letter = tokens[0]
            val acc = tokens[1]
            val octStr = tokens[2]

            val step = (letter[0].code + 3) % 7
            val alt = accToAlt(acc)
            val oct = if (octStr.isNotEmpty()) octStr.toInt() else null
            val coord = TonalPitch.coordinates(Pitch(step, alt, oct))

            val name = letter + acc + octStr
            val pc = letter + acc
            val chroma = (SEMI[step] + alt + 120) % 12
            val height = if (oct == null) {
                ((SEMI[step] + alt) % 12 + 12) % 12 - 12 * 99
            } else {
                SEMI[step] + alt + 12 * (oct + 1)
            }
            val midi = if (height in 0..127) height else null
            val freq = if (oct == null) null else 2.0.pow((height - 69).toDouble() / 12) * 440

            return Note(
                empty = false,
                acc = acc,
                alt = alt,
                chroma = chroma,
                coord = coord,
                freq = freq,
                height = height,
                letter = letter,
                midi = midi,
                name = name,
                oct = oct,
                pc = pc,
                step = step
            )
        }

        private fun pitchName(props: Pitch): NoteName {
            val step = props.step
            val alt = props.alt
            val oct = props.oct
            val letter = stepToLetter(step)
            if (letter.isEmpty()) {
                return ""
            }

            val pc = letter + altToAcc(alt)
            return if (oct != null) pc + oct.toString() else pc
        }
    }
}

private typealias mod = Double // Dummy to avoid issues, actually we don't need it if we used manual math
