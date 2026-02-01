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
        val clippedBegin = maxOf(begin, bounds.begin)
        val clippedEnd = minOf(end, bounds.end)
        return if (clippedEnd > clippedBegin) {
            TimeSpan(clippedBegin, clippedEnd)
        } else {
            null
        }
    }
}
