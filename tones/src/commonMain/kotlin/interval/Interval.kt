package io.peekandpoke.klang.tones.interval

import io.peekandpoke.klang.tones.pitch.*

typealias IntervalName = String

enum class IntervalType {
    Perfectable, Majorable;

    override fun toString(): String = when (this) {
        Perfectable -> "perfectable"
        Majorable -> "majorable"
    }
}

data class Interval(
    override val step: Int,
    override val alt: Int,
    override val oct: Int = 0,
    override val dir: Int = 1,
    val empty: Boolean = false,
    override val name: String = "",
    val num: Int = Int.MIN_VALUE,
    val q: String = "",
    val type: IntervalType? = null,
    val simple: Int = Int.MIN_VALUE,
    val semitones: Int = Int.MIN_VALUE,
    val chroma: Int = -1,
    val coord: PitchCoordinates? = null,
) : NamedPitch, Pitch {
    companion object {
        val NoInterval = Interval(
            step = -1,
            alt = -1,
            empty = true,
            name = "",
            num = Int.MIN_VALUE,
            q = "",
            type = null,
            simple = Int.MIN_VALUE,
            semitones = Int.MIN_VALUE,
            chroma = -1,
            coord = null
        )
    }
}

private fun fillStr(s: String, n: Int): String {
    val count = kotlin.math.abs(n)
    return s.repeat(count)
}

// shorthand tonal notation (with quality after number)
private const val INTERVAL_TONAL_REGEX = """^([-+]?\d+)(d{1,4}|m|M|P|A{1,4})$"""

// standard shorthand notation (with quality before number)
private const val INTERVAL_SHORTHAND_REGEX = """^(AA|A|P|M|m|d|dd)([-+]?\d+)$"""

private val TONAL_REG = Regex(INTERVAL_TONAL_REGEX)
private val SHORTHAND_REG = Regex(INTERVAL_SHORTHAND_REGEX)

/**
 * @private
 */
fun tokenizeInterval(str: String?): List<String> {
    if (str == null) return listOf("", "")

    TONAL_REG.matchEntire(str)?.let { m ->
        return listOf(m.groupValues[1], m.groupValues[2])
    }
    SHORTHAND_REG.matchEntire(str)?.let { m ->
        return listOf(m.groupValues[2], m.groupValues[1])
    }
    return listOf("", "")
}

private val SIZES = intArrayOf(0, 2, 4, 5, 7, 9, 11)
private const val TYPES = "PMMPPMM"

fun interval(src: Any?): Interval {
    return when (src) {
        is String -> parse(src)
        is Interval -> src
        is Pitch -> interval(pitchName(src))
        is NamedPitch -> interval(src.name)
        else -> Interval.NoInterval
    }
}

private fun parse(str: String): Interval {
    val tokens = tokenizeInterval(str)
    if (tokens[0] == "") {
        return Interval.NoInterval
    }
    val num = tokens[0].toInt()
    val q = tokens[1]
    val step = (kotlin.math.abs(num) - 1) % 7
    val t = TYPES[step]
    if (t == 'M' && q == "P") {
        return Interval.NoInterval
    }
    val type = if (t == 'M') IntervalType.Majorable else IntervalType.Perfectable

    val name = "" + num + q
    val dir = if (num < 0) -1 else 1
    val simple = if (num == 8 || num == -8) num else dir * (step + 1)
    val alt = qToAlt(type, q)
    val oct = (kotlin.math.abs(num) - 1) / 7
    val semitones = dir * (SIZES[step] + alt + 12 * oct)
    val chroma = (((dir * (SIZES[step] + alt)) % 12) + 12) % 12
    val coord = coordinates(Pitch(step, alt, oct, dir))

    return Interval(
        empty = false,
        name = name,
        num = num,
        q = q,
        step = step,
        alt = alt,
        dir = dir,
        type = type,
        simple = simple,
        semitones = semitones,
        chroma = chroma,
        coord = coord,
        oct = oct
    )
}

private val A_REGEX = Regex("^A+$")
private val D_REGEX = Regex("^d+$")

private fun qToAlt(type: IntervalType, q: String): Int {
    return when {
        (q == "M" && type == IntervalType.Majorable) || (q == "P" && type == IntervalType.Perfectable) -> 0
        q == "m" && type == IntervalType.Majorable -> -1
        A_REGEX.matches(q) -> q.length
        D_REGEX.matches(q) -> -1 * (if (type == IntervalType.Perfectable) q.length else q.length + 1)
        else -> 0
    }
}

private fun pitchName(props: Pitch): String {
    val step = props.step
    val alt = props.alt
    val oct = props.oct ?: 0
    val dir = props.dir ?: 0

    if (dir == 0) {
        return ""
    }
    val calcNum = step + 1 + 7 * oct
    // this is an edge case: descending pitch class unison (see #243)
    val num = if (calcNum == 0) step + 1 else calcNum
    val d = if (dir < 0) "-" else ""
    val type = if (TYPES[step] == 'M') IntervalType.Majorable else IntervalType.Perfectable
    return d + num + altToQ(type, alt)
}

private fun altToQ(type: IntervalType, alt: Int): String {
    return when {
        alt == 0 -> if (type == IntervalType.Majorable) "M" else "P"
        alt == -1 && type == IntervalType.Majorable -> "m"
        alt > 0 -> fillStr("A", alt)
        else -> fillStr("d", if (type == IntervalType.Perfectable) alt else alt + 1)
    }
}

fun coordToInterval(
    coord: PitchCoordinates,
    forceDescending: Boolean = false,
): Interval {
    return when (coord) {
        is PitchCoordinates.PitchClass -> {
            val f = coord.fifths
            val isDescending = f * 7 < 0
            val ivl = if (forceDescending || isDescending) {
                PitchCoordinates.Interval(-f, 0, -1)
            } else {
                PitchCoordinates.Interval(f, 0, 1)
            }
            interval(pitch(ivl))
        }

        is PitchCoordinates.Note -> {
            val f = coord.fifths
            val o = coord.octaves
            val isDescending = f * 7 + o * 12 < 0
            val ivl = if (forceDescending || isDescending) {
                PitchCoordinates.Interval(-f, -o, -1)
            } else {
                PitchCoordinates.Interval(f, o, 1)
            }
            interval(pitch(ivl))
        }

        is PitchCoordinates.Interval -> {
            interval(pitch(coord))
        }
    }
}

fun isInterval(src: Any?): Boolean {
    return src is Interval && !src.empty
}
