package io.peekandpoke.klang.common.math

import kotlinx.serialization.Serializable

/**
 * A span of musical time `[begin, end)` measured in [CycleTime] ticks.
 *
 * Renamed from the former `TimeSpan` and moved to `common/math` alongside [CycleTime]/[Rational].
 * Arithmetic is delegated to [CycleTime], so `shift`/`duration`/`clipTo` are exact integer-tick ops
 * and only `scale` (tempo) rounds to the grid.
 */
@Serializable
data class CycleTimeSpan(
    val begin: CycleTime,
    val end: CycleTime,
) {
    val isValid = end >= begin

    val duration: CycleTime get() = end - begin

    /** Shift this span by an offset (exact). */
    fun shift(offset: CycleTime): CycleTimeSpan =
        CycleTimeSpan(begin + offset, end + offset)

    /** Scale this span by a dimensionless factor (for tempo operations). Rounds to the grid. */
    fun scale(factor: Double): CycleTimeSpan =
        CycleTimeSpan(begin.scaleBy(factor), end.scaleBy(factor))

    /** Clip this span to bounds (for clipping operations). */
    fun clipTo(bounds: CycleTimeSpan): CycleTimeSpan? {
        return clipTo(bounds.begin, bounds.end)
    }

    /** Clip this span to bounds (for clipping operations). */
    fun clipTo(begin: CycleTime, end: CycleTime): CycleTimeSpan? {
        val clippedBegin = this.begin.coerceAtLeast(begin)
        val clippedEnd = this.end.coerceAtMost(end)
        return if (clippedEnd > clippedBegin) {
            CycleTimeSpan(clippedBegin, clippedEnd)
        } else {
            null
        }
    }

    /**
     * Transforms time relative to the cycle (sam = start of cycle = floor).
     * Takes cycle-local time, applies transformation, then adds sam back.
     */
    fun withCycle(funcTime: (CycleTime) -> CycleTime): CycleTimeSpan {
        val sam = begin.floorToCycle()
        val b = sam + funcTime(begin - sam)
        val e = sam + funcTime(end - sam)
        return CycleTimeSpan(b, e)
    }
}
