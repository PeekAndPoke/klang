package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import kotlin.math.abs

/**
 * Euclidean Pattern: Applies a Euclidean rhythm to an inner pattern.
 * Syntax: `pattern(pulses, steps)` e.g., `bd(3,8)`
 *
 * This pattern acts as a gate (`struct`). It only allows the inner pattern to play
 * during the active steps of the Euclidean rhythm.
 * Events are strictly clipped to the step duration to ensure the rhythmic structure is preserved.
 */
internal class EuclideanPattern private constructor(
    val inner: StrudelPattern,
    val pulses: Int,
    val steps: Int,
    val rotation: Int,
) : StrudelPattern {

    override val weight: Double get() = inner.weight

    // JS Implementation calls: rotate(bjorklund(...), -rotation)
    // We must replicate JS rotate behavior which relies on Array.slice behavior for out-of-bounds indices
    private val rhythm = rotateJs(bjorklundStrudel(pulses, steps), -rotation)

    companion object {
        /**
         * Creates a Euclidean Pattern.
         * Returns the inner pattern if inputs are invalid (e.g. steps <= 0).
         */
        fun create(
            inner: StrudelPattern,
            pulses: Int,
            steps: Int,
            rotation: Int = 0,
        ): StrudelPattern {
            if (steps <= 0 || steps < abs(pulses)) return inner
            // pulses < 0 is valid (inversion)
            // pulses > steps is valid (returns all 1s)

            return EuclideanPattern(inner, pulses, steps, rotation)
        }

        // Replicates JS Array.prototype.slice behavior exactly
        private fun <T> List<T>.jsSlice(start: Int, end: Int? = null): List<T> {
            val len = this.size
            if (len == 0) return emptyList()

            // JS slice logic:
            // If index is negative, it's len + index.
            // If it's still negative (index < -len), it clamps to 0.
            val s = if (start < 0) kotlin.math.max(len + start, 0) else kotlin.math.min(start, len)

            val e = if (end == null) {
                len
            } else {
                if (end < 0) kotlin.math.max(len + end, 0) else kotlin.math.min(end, len)
            }

            if (s >= e) return emptyList()
            return this.subList(s, e)
        }

        // Replicates Strudel's rotate function: array.slice(n).concat(array.slice(0, n))
        private fun rotateJs(list: List<Int>, n: Int): List<Int> {
            return list.jsSlice(n) + list.jsSlice(0, n)
        }

        private fun bjorklundStrudel(pulses: Int, steps: Int): List<Int> {
            val k = kotlin.math.abs(pulses)
            val n = steps
            if (n <= 0) return emptyList()

            // Calculate base pattern
            val basePattern = if (k >= n) {
                List(n) { 1 }
            } else {
                val ons = k
                val offs = n - k

                val ones = List(ons) { listOf(1) }
                val zeros = List(offs) { listOf(0) }

                val result = recursiveBjorklund(ons, offs, ones, zeros)
                result.first.flatten() + result.second.flatten()
            }

            // Handle negative pulses by inverting the pattern (1 -> 0, 0 -> 1)
            return if (pulses < 0) {
                basePattern.map { 1 - it }
            } else {
                basePattern
            }
        }

        private fun recursiveBjorklund(
            ons: Int,
            offs: Int,
            xs: List<List<Int>>,
            ys: List<List<Int>>,
        ): Pair<List<List<Int>>, List<List<Int>>> {
            // JS logic: Math.min(ons, offs) <= 1 ? [n, x] ...
            // Note: The JS source uses Math.min(ons, offs) <= 1.
            if (kotlin.math.min(ons, offs) <= 1) return xs to ys

            if (ons > offs) {
                val offsCount = offs
                val xsPrefix = xs.take(offsCount)
                val xsSuffix = xs.drop(offsCount)
                val newXs = xsPrefix.zip(ys) { a, b -> a + b }
                val newYs = xsSuffix
                return recursiveBjorklund(offs, ons - offs, newXs, newYs)
            } else {
                val onsCount = ons
                val ysPrefix = ys.take(onsCount)
                val ysSuffix = ys.drop(onsCount)
                val newXs = xs.zip(ysPrefix) { a, b -> a + b }
                val newYs = ysSuffix
                return recursiveBjorklund(ons, offs - ons, newXs, newYs)
            }
        }
    }

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val events = mutableListOf<StrudelPatternEvent>()

        val startCycle = from.floor().toInt()
        val endCycle = to.ceil().toInt()

        // EXACT step duration using rational arithmetic
        val stepDuration = Rational.ONE / Rational(steps)

        for (cycle in startCycle until endCycle) {
            val cycleOffset = Rational(cycle)

            rhythm.forEachIndexed { stepIndex, isActive ->
                if (isActive == 1) {
                    val stepStart = cycleOffset + (Rational(stepIndex) * stepDuration)
                    val stepEnd = stepStart + stepDuration

                    // Check intersection with query arc
                    val intersectStart = maxOf(from, stepStart)
                    val intersectEnd = minOf(to, stepEnd)

                    if (intersectEnd > intersectStart) {
                        val innerEvents = inner.queryArcContextual(intersectStart, intersectEnd, ctx)
                            .sortedBy { it.begin }
                            .take(1)

                        events.addAll(innerEvents.map { ev ->
                            ev.copy(
                                begin = intersectStart,
                                end = intersectEnd,
                                dur = intersectEnd - intersectStart,
                            )
                        })
                    }
                }
            }
        }

        return events
    }
}
