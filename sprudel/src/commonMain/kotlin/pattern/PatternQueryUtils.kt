@file:Suppress("NOTHING_TO_INLINE")

package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.CycleTime
import io.peekandpoke.klang.common.math.CycleTimeSpan
import io.peekandpoke.klang.sprudel.SprudelPatternEvent

/**
 * Helper utilities for pattern query operations.
 *
 * These functions centralize common allocation hotspots and repetitive logic.
 * Time values are [CycleTime] (fixed-point ticks); scale/span factors are dimensionless [Double].
 */

// ============================================================================
// Event List Creation
// ============================================================================

/** Creates a mutable event list. Centralized to enable future pooling/reuse strategies. */
internal inline fun createEventList(): MutableList<SprudelPatternEvent> {
    return mutableListOf()
}

/** Creates a mutable event list with expected capacity hint. */
internal inline fun createEventList(capacityHint: Int): MutableList<SprudelPatternEvent> {
    return ArrayList(capacityHint)
}

// ============================================================================
// Overlap / Clipping Detection
// ============================================================================

/** Returns true if [eventBegin, eventEnd) intersects with [queryFrom, queryTo). */
internal inline fun hasOverlap(
    eventBegin: CycleTime,
    eventEnd: CycleTime,
    queryFrom: CycleTime,
    queryTo: CycleTime,
): Boolean {
    return eventEnd > queryFrom && eventBegin < queryTo
}

/** Overlap test with an inward epsilon tolerance on the query boundaries. */
internal inline fun hasOverlapWithEpsilon(
    eventBegin: CycleTime,
    eventEnd: CycleTime,
    queryFrom: CycleTime,
    queryTo: CycleTime,
    epsilon: CycleTime,
): Boolean {
    val fromPlusEps = queryFrom + epsilon
    val toMinusEps = queryTo - epsilon
    return eventEnd > fromPlusEps && eventBegin <= toMinusEps
}

/** Overlap range between two spans, or null if they don't overlap. */
internal inline fun calculateOverlapRange(
    begin1: CycleTime,
    end1: CycleTime,
    begin2: CycleTime,
    end2: CycleTime,
): Pair<CycleTime, CycleTime>? {
    if (end1 <= begin2 || end2 <= begin1) {
        return null
    }

    val overlapBegin = begin1.coerceAtLeast(begin2)
    val overlapEnd = end1.coerceAtMost(end2)

    return if (overlapEnd <= overlapBegin) null else Pair(overlapBegin, overlapEnd)
}

// ============================================================================
// Time Mapping / Scaling
// ============================================================================

/**
 * Maps event time by dividing by a scale factor (faster playback). Scales part and whole equally.
 * Returns (scaledPart, scaledWhole).
 */
internal inline fun mapEventTimeByScale(
    event: SprudelPatternEvent,
    scale: Double,
): Pair<CycleTimeSpan, CycleTimeSpan> {
    val inv = 1.0 / scale
    return Pair(
        event.part.scale(inv),
        event.whole.scale(inv),
    )
}

/** Maps event time by adding an offset (early/late). Returns (shiftedPart, shiftedWhole). */
internal inline fun offsetEventTime(
    event: SprudelPatternEvent,
    offset: CycleTime,
): Pair<CycleTimeSpan, CycleTimeSpan> {
    return Pair(
        event.part.shift(offset),
        event.whole.shift(offset)
    )
}

/**
 * Maps event time using span-based compression within a cycle (compress/focus/fastGap).
 *
 * @param cycleBase The cycle start
 * @param compressedStart The start of the compressed region
 * @param span The span of the compressed region as a fraction of a cycle (Double)
 */
internal inline fun mapEventTimeBySpan(
    event: SprudelPatternEvent,
    cycleBase: CycleTime,
    compressedStart: CycleTime,
    span: Double,
): Pair<CycleTimeSpan, CycleTimeSpan> {
    val mappedPart = event.part.shift(-cycleBase).scale(span).shift(compressedStart)
    val mappedWhole = event.whole.shift(-cycleBase).scale(span).shift(compressedStart)

    return Pair(mappedPart, mappedWhole)
}

/**
 * Scales a query time range by a factor (multiplication). Returns (scaledFrom, scaledTo).
 */
internal inline fun scaleTimeRange(
    from: CycleTime,
    to: CycleTime,
    scale: Double,
): Pair<CycleTime, CycleTime> {
    return Pair(from.scaleBy(scale), to.scaleBy(scale))
}

// ============================================================================
// Multi-Cycle Iteration
// ============================================================================

/**
 * Calculates the range of cycle indices that overlap with [from, to), inclusive of both ends.
 */
internal inline fun calculateCycleBounds(from: CycleTime, to: CycleTime): IntRange {
    return from.cycleIndex()..to.cycleIndex()
}

/** Returns true if [queryFrom, queryTo) intersects the cycle region [regionStart, regionEnd). */
internal inline fun cycleRegionIntersects(
    queryFrom: CycleTime,
    queryTo: CycleTime,
    regionStart: CycleTime,
    regionEnd: CycleTime,
): Boolean {
    return queryTo > regionStart && queryFrom < regionEnd
}
