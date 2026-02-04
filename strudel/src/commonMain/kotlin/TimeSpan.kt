package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.strudel.math.Rational
import kotlinx.serialization.Serializable

/**
 * Represents a time span with begin and end points
 */
@Serializable
data class TimeSpan(
    val begin: Rational,
    val end: Rational,
) {
    init {
        require(end >= begin) { "TimeSpan end ($end) must be >= begin ($begin)" }
    }

    val duration: Rational get() = end - begin

    /** Shift this timespan by an offset */
    fun shift(offset: Rational): TimeSpan =
        TimeSpan(begin + offset, end + offset)

    /** Scale this timespan by a factor (for tempo operations) */
    fun scale(factor: Rational): TimeSpan =
        TimeSpan(begin * factor, end * factor)

    /** Clip this timespan to bounds (for clipping operations) */
    fun clipTo(bounds: TimeSpan): TimeSpan? {
        return clipTo(bounds.begin, bounds.end)
    }

    /** Clip this timespan to bounds (for clipping operations) */
    fun clipTo(begin: Rational, end: Rational): TimeSpan? {
        val clippedBegin = maxOf(this.begin, begin)
        val clippedEnd = minOf(this.end, end)
        return if (clippedEnd > clippedBegin) {
            TimeSpan(clippedBegin, clippedEnd)
        } else {
            null
        }
    }

    /**
     * Transforms time relative to the cycle (sam = start of cycle = floor).
     * Takes cycle-local time, applies transformation, then adds sam back.
     */
    fun withCycle(funcTime: (Rational) -> Rational): TimeSpan {
        val sam = begin.floor()
        val b = sam + funcTime(begin - sam)
        val e = sam + funcTime(end - sam)
        return TimeSpan(b, e)
    }
}
