@file:Suppress("NOTHING_TO_INLINE")

package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import kotlin.math.floor

/**
 * Helper utilities for pattern query operations.
 *
 * These functions centralize common allocation hotspots and repetitive logic
 * to prepare for future GC-free implementation with object pooling.
 *
 * Design principles:
 * - No new allocations in helper APIs (where possible)
 * - Encapsulate repeated patterns across Pattern implementations
 * - Make it easy to replace with pooled versions later
 */

// ============================================================================
// Event List Creation
// ============================================================================

/**
 * Creates a mutable event list.
 * Centralized to enable future pooling/reuse strategies.
 */
internal inline fun createEventList(): MutableList<StrudelPatternEvent> {
    return mutableListOf()
}

/**
 * Creates a mutable event list with expected capacity hint.
 * Reduces allocations when result size is predictable.
 */
internal inline fun createEventList(capacityHint: Int): MutableList<StrudelPatternEvent> {
    return ArrayList(capacityHint)
}

// ============================================================================
// Overlap / Clipping Detection
// ============================================================================

/**
 * Checks if an event overlaps with a query range.
 *
 * Returns true if [eventBegin, eventEnd) intersects with [queryFrom, queryTo).
 */
internal inline fun hasOverlap(
    eventBegin: Rational,
    eventEnd: Rational,
    queryFrom: Rational,
    queryTo: Rational,
): Boolean {
    return eventEnd > queryFrom && eventBegin < queryTo
}

/**
 * Checks if an event overlaps with a query range, using epsilon tolerance.
 *
 * Adjusts query boundaries inward by epsilon to avoid floating-point edge cases.
 */
internal inline fun hasOverlapWithEpsilon(
    eventBegin: Rational,
    eventEnd: Rational,
    queryFrom: Rational,
    queryTo: Rational,
    epsilon: Rational,
): Boolean {
    val fromPlusEps = queryFrom + epsilon
    val toMinusEps = queryTo - epsilon
    return eventEnd > fromPlusEps && eventBegin <= toMinusEps
}

/**
 * Calculates the overlap range between two time spans.
 *
 * Returns a Pair of (overlapBegin, overlapEnd) if they overlap, null otherwise.
 */
internal inline fun calculateOverlapRange(
    begin1: Rational,
    end1: Rational,
    begin2: Rational,
    end2: Rational,
): Pair<Rational, Rational>? {
    // Check if they overlap
    if (end1 <= begin2 || end2 <= begin1) {
        return null
    }

    val overlapBegin = maxOf(begin1, begin2)
    val overlapEnd = minOf(end1, end2)

    return if (overlapEnd <= overlapBegin) null else Pair(overlapBegin, overlapEnd)
}

// ============================================================================
// Time Mapping / Scaling
// ============================================================================

/**
 * Maps event time coordinates by dividing by a scale factor.
 *
 * Used for patterns that compress time (faster playback).
 * Scales both part and whole by the same factor.
 * Returns (scaledPart, scaledWhole).
 */
internal inline fun mapEventTimeByScale(
    event: StrudelPatternEvent,
    scale: Rational,
): Pair<io.peekandpoke.klang.strudel.TimeSpan, io.peekandpoke.klang.strudel.TimeSpan?> {
    return Pair(
        event.part.scale(Rational.ONE / scale),
        event.whole?.scale(Rational.ONE / scale)
    )
}

/**
 * Maps event time coordinates by adding an offset.
 *
 * Used for patterns that shift time (early/late).
 * Returns (shiftedPart, shiftedWhole).
 */
internal inline fun offsetEventTime(
    event: StrudelPatternEvent,
    offset: Rational,
): Pair<io.peekandpoke.klang.strudel.TimeSpan, io.peekandpoke.klang.strudel.TimeSpan?> {
    return Pair(
        event.part.shift(offset),
        event.whole?.shift(offset)
    )
}

/**
 * Maps event time using span-based compression within a cycle.
 *
 * Used for patterns like compress/focus that squeeze events into a portion of a cycle.
 *
 * @param event The event to map
 * @param cycleBase The cycle start (e.g., cycle number as Rational)
 * @param compressedStart The start of the compressed region
 * @param span The span of the compressed region (end - start)
 * @return (mappedPart, mappedWhole)
 */
internal inline fun mapEventTimeBySpan(
    event: StrudelPatternEvent,
    cycleBase: Rational,
    compressedStart: Rational,
    span: Rational,
): Pair<io.peekandpoke.klang.strudel.TimeSpan, io.peekandpoke.klang.strudel.TimeSpan?> {
    val mappedPart = event.part.shift(-cycleBase).scale(span).shift(compressedStart)
    val mappedWhole = event.whole?.shift(-cycleBase)?.scale(span)?.shift(compressedStart)

    return Pair(mappedPart, mappedWhole)
}

/**
 * Scales a time range by a factor (multiplication).
 *
 * Used to transform query ranges before passing to inner patterns.
 * Returns (scaledFrom, scaledTo).
 */
internal inline fun scaleTimeRange(
    from: Rational,
    to: Rational,
    scale: Rational,
): Pair<Rational, Rational> {
    return Pair(from * scale, to * scale)
}

/**
 * Scales a time range by a factor with epsilon adjustment.
 *
 * Expands the range outward by epsilon to avoid boundary precision issues.
 * Returns (scaledFrom + epsilon, scaledTo - epsilon).
 */
internal inline fun scaleTimeRangeWithEpsilon(
    from: Rational,
    to: Rational,
    scale: Rational,
    epsilon: Rational,
): Pair<Rational, Rational> {
    return Pair(
        (from * scale) + epsilon,
        (to * scale) - epsilon
    )
}

// ============================================================================
// Multi-Cycle Iteration
// ============================================================================

/**
 * Calculates the range of cycle indices that overlap with [from, to).
 *
 * Returns a LongRange from floor(from) to floor(to), inclusive.
 * Used for patterns that operate per-cycle.
 */
internal inline fun calculateCycleBounds(from: Rational, to: Rational): LongRange {
    val cycleStart = floor(from.toDouble()).toLong()
    val cycleEnd = floor(to.toDouble()).toLong()
    return cycleStart..cycleEnd
}

/**
 * Checks if a query range [queryFrom, queryTo) intersects with a cycle region [regionStart, regionEnd).
 *
 * Returns true if there's any overlap.
 */
internal inline fun cycleRegionIntersects(
    queryFrom: Rational,
    queryTo: Rational,
    regionStart: Rational,
    regionEnd: Rational,
): Boolean {
    return queryTo > regionStart && queryFrom < regionEnd
}
