package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.VoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.lang.fast
import io.peekandpoke.klang.strudel.lang.late
import io.peekandpoke.klang.strudel.lang.struct
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational
import io.peekandpoke.klang.strudel.math.bjorklund
import io.peekandpoke.klang.strudel.math.recursiveBjorklund
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
    val nPulses: Int,
    val nSteps: Int,
    val nRotation: Int,
) : StrudelPattern {

    override val weight: Double get() = inner.weight

    override val steps: Rational get() = nSteps.toRational()

    // JS Implementation calls: rotate(bjorklund(...), -rotation)
    // We must replicate JS rotate behavior which relies on Array.slice behavior for out-of-bounds indices
    private val rhythm = rotateJs(bjorklundStrudel(nPulses, nSteps), -nRotation)

    companion object {
        /**
         * Creates a standard Euclidean Pattern (gated).
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

        /**
         * Creates a Legato Euclidean Pattern.
         * In this version, pulses are held until the next pulse (no gaps).
         */
        fun createLegato(
            inner: StrudelPattern,
            pulses: Int,
            steps: Int,
            rotation: Int = 0,
        ): StrudelPattern {
            if (pulses <= 0 || steps <= 0) return EmptyPattern

            val bitmap = bjorklund(pulses, steps)
            val onsets = bitmap.mapIndexedNotNull { i, v -> if (v == 1) i else null }

            if (onsets.isEmpty()) return EmptyPattern

            // A pattern that always returns a single event filling the queried duration.
            // This ensures perfectly legato gates without granularity issues or cycle repetitions.
            val fillAtom = object : StrudelPattern {
                override val weight = 1.0
                override val steps: Rational = Rational.ONE

                override fun queryArcContextual(
                    from: Rational,
                    to: Rational,
                    ctx: QueryContext,
                ): List<StrudelPatternEvent> {
                    return listOf(
                        StrudelPatternEvent(
                            begin = from,
                            end = to,
                            dur = to - from,
                            data = VoiceData.empty.copy(value = 1.asVoiceValue())
                        )
                    )
                }
            }

            val segments = ArrayList<Pair<Double, StrudelPattern>>()
            val wrappedOnsets = onsets + (onsets[0] + steps)

            for (i in 0 until onsets.size) {
                val current = wrappedOnsets[i]
                val next = wrappedOnsets[i + 1]
                val duration = (next - current).toDouble()
                // Use fillAtom directly. ArrangementPattern will query it for the full segment duration,
                // and it will return a single event filling that duration.
                segments.add(duration to fillAtom)
            }

            val structure = ArrangementPattern(segments).fast(steps.toDouble())

            val rotatedStructure = if (rotation != 0) {
                structure.late(rotation.toDouble() / steps)
            } else {
                structure
            }

            return inner.struct(rotatedStructure)
        }

        // Replicates JS Array.prototype.slice behavior exactly
        private fun <T> List<T>.jsSlice(start: Int, end: Int? = null): List<T> {
            val len = this.size
            if (len == 0) return emptyList()

            // JS slice logic:
            // If index is negative, it's len + index.
            // If it's still negative (index < -len), it clamps to 0.
            val s = if (start < 0) max(len + start, 0) else min(start, len)

            val e = if (end == null) {
                len
            } else {
                if (end < 0) max(len + end, 0) else min(end, len)
            }

            if (s >= e) return emptyList()
            return this.subList(s, e)
        }

        // Replicates Strudel's rotate function: array.slice(n).concat(array.slice(0, n))
        private fun rotateJs(list: List<Int>, n: Int): List<Int> {
            return list.jsSlice(n) + list.jsSlice(0, n)
        }

        private fun bjorklundStrudel(pulses: Int, steps: Int): List<Int> {
            val k = abs(pulses)
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
    }

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val events = mutableListOf<StrudelPatternEvent>()

        val startCycle = from.floor().toInt()
        val endCycle = to.ceil().toInt()

        // EXACT step duration using rational arithmetic
        val stepDuration = Rational.ONE / Rational(nSteps)

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
