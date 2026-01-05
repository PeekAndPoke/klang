package io.peekandpoke.klang.tones.note

import io.peekandpoke.klang.tones.pitch.*
import kotlin.math.pow

typealias NoteWithOctave = String
typealias PcName = String
typealias NoteName = String

data class Note(
    override val step: Int,
    override val alt: Int,
    override val oct: Int? = null,
    override val dir: Int? = null,
    val empty: Boolean = false,
    override val name: String = "",
    val letter: String = "",
    val acc: String = "",
    val pc: PcName = "",
    val chroma: Int = -1,
    val height: Int = -1,
    val coord: PitchCoordinates? = null,
    val midi: Int? = null,
    val freq: Double? = null,
) : NamedPitch, Pitch {
    companion object {
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
    }
}

private fun fillStr(s: String, n: Int): String {
    val count = kotlin.math.abs(n)
    return s.repeat(count)
}

fun stepToLetter(step: Int): String = "CDEFGAB".getOrNull(step)?.toString() ?: ""

fun altToAcc(alt: Int): String = if (alt < 0) fillStr("b", -alt) else fillStr("#", alt)

fun accToAlt(acc: String): Int = if (acc.isEmpty()) 0 else if (acc[0] == 'b') -acc.length else acc.length

private val REGEX = Regex("""^([a-gA-G]?)(#{1,}|b{1,}|x{1,}|)(-?\d*)\s*(.*)$""")

/**
 * @private
 */
fun tokenizeNote(str: String): List<String> {
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
 * @private
 */
fun coordToNote(noteCoord: PitchCoordinates): Note {
    return note(pitch(noteCoord))
}

private fun mod(n: Int, m: Int): Int = ((n % m) + m) % m

private val SEMI = intArrayOf(0, 2, 4, 5, 7, 9, 11)

fun note(src: Any?): Note {
    return when (src) {
        is String -> parse(src)
        is Note -> src
        is Pitch -> note(pitchName(src))
        is NamedPitch -> note(src.name)
        else -> Note.NoNote
    }
}

private fun parse(noteName: NoteName): Note {
    val tokens = tokenizeNote(noteName)
    if (tokens[0] == "" || tokens[3] != "") {
        return Note.NoNote
    }

    val letter = tokens[0]
    val acc = tokens[1]
    val octStr = tokens[2]

    val step = (letter[0].code + 3) % 7
    val alt = accToAlt(acc)
    val oct = if (octStr.isNotEmpty()) octStr.toInt() else null
    val coord = coordinates(Pitch(step, alt, oct))

    val name = letter + acc + octStr
    val pc = letter + acc
    val chroma = (SEMI[step] + alt + 120) % 12
    val height = if (oct == null) {
        mod(SEMI[step] + alt, 12) - 12 * 99
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

fun isNote(src: Any?): Boolean {
    return src is Note && !src.empty
}
