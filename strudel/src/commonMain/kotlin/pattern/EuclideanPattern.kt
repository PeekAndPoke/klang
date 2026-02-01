package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.*
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelVoiceValue.Companion.asVoiceValue
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
 *
 * @param inner The pattern to apply the Euclidean rhythm to
 * @param pulsesProvider Control value provider for the number of pulses
 * @param stepsProvider Control value provider for the number of steps
 * @param rotationProvider Control value provider for rotation offset (optional)
 * @param legato If true, pulses are held until the next pulse (no gaps)
 */
@Suppress("DuplicatedCode")
internal class EuclideanPattern(
    val inner: StrudelPattern,
    val pulsesProvider: ControlValueProvider,
    val stepsProvider: ControlValueProvider,
    val rotationProvider: ControlValueProvider?,
    val legato: Boolean = false,
) : StrudelPattern {

    override val weight: Double get() = inner.weight

    override val numSteps: Rational?
        get() {
            return if (stepsProvider is ControlValueProvider.Static) {
                (stepsProvider.value.asInt ?: 0).toRational()
            } else {
                inner.numSteps
            }
        }

    override fun estimateCycleDuration(): Rational = Rational.ONE

    companion object {
        /**
         * Create a EuclideanPattern with static values.
         */
        fun static(
            inner: StrudelPattern,
            pulses: Int,
            steps: Int,
            rotation: Int = 0,
            legato: Boolean = false,
        ): EuclideanPattern {
            return EuclideanPattern(
                inner = inner,
                pulsesProvider = ControlValueProvider.Static(StrudelVoiceValue.Num(pulses.toDouble())),
                stepsProvider = ControlValueProvider.Static(StrudelVoiceValue.Num(steps.toDouble())),
                rotationProvider = ControlValueProvider.Static(StrudelVoiceValue.Num(rotation.toDouble())),
                legato = legato
            )
        }

        /**
         * Create a EuclideanPattern with control patterns.
         */
        fun control(
            inner: StrudelPattern,
            pulsesPattern: StrudelPattern,
            stepsPattern: StrudelPattern,
            rotationPattern: StrudelPattern?,
            legato: Boolean = false,
        ): EuclideanPattern {
            return EuclideanPattern(
                inner = inner,
                pulsesProvider = ControlValueProvider.Pattern(pulsesPattern),
                stepsProvider = ControlValueProvider.Pattern(stepsPattern),
                rotationProvider = rotationPattern?.let { ControlValueProvider.Pattern(it) },
                legato = legato
            )
        }

        /**
         * Creates a standard Euclidean Pattern (gated) - legacy method for internal use.
         * Returns the inner pattern if inputs are invalid (e.g. steps <= 0).
         */
        internal fun create(
            inner: StrudelPattern,
            pulses: Int,
            steps: Int,
            rotation: Int = 0,
        ): StrudelPattern {
            if (steps <= 0 || steps < abs(pulses)) return inner
            // pulses < 0 is valid (inversion)
            // pulses > steps is valid (returns all 1s)

            return static(inner, pulses, steps, rotation, legato = false)
        }

        /**
         * Creates a Legato Euclidean Pattern - legacy method for internal use.
         * In this version, pulses are held until the next pulse (no gaps).
         */
        internal fun createLegato(
            inner: StrudelPattern,
            pulses: Int,
            steps: Int,
            rotation: Int = 0,
        ): StrudelPattern {
            return static(inner, pulses, steps, rotation, legato = true)
        }

        /**
         * Internal implementation for static legato euclidean pattern.
         */
        private fun createLegatoStatic(
            inner: StrudelPattern,
            pulses: Int,
            steps: Int,
            rotation: Int,
        ): StrudelPattern {
            if (pulses <= 0 || steps <= 0) return EmptyPattern

            val bitmap = bjorklund(pulses, steps)
            val onsets = bitmap.mapIndexedNotNull { i, v -> if (v == 1) i else null }

            if (onsets.isEmpty()) return EmptyPattern

            // A pattern that always returns a single event filling the queried duration.
            // This ensures perfectly legato gates without granularity issues or cycle repetitions.
            val fillAtom = object : StrudelPattern {
                override val weight = 1.0

                override val numSteps: Rational = Rational.ONE

                override fun estimateCycleDuration(): Rational = Rational.ONE

                override fun queryArcContextual(
                    from: Rational,
                    to: Rational,
                    ctx: QueryContext,
                ): List<StrudelPatternEvent> {
                    val timeSpan = TimeSpan(begin = from, end = to)

                    return listOf(
                        StrudelPatternEvent(
                            part = timeSpan,
                            whole = timeSpan,
                            data = StrudelVoiceData.empty.copy(value = 1.asVoiceValue())
                        )
                    )
                }
            }

            val patterns = ArrayList<StrudelPattern>()
            val wrappedOnsets = onsets + (onsets[0] + steps)

            for (i in 0 until onsets.size) {
                val current = wrappedOnsets[i]
                val next = wrappedOnsets[i + 1]
                val duration = (next - current).toDouble()

                // Instead of (duration to pattern), we use weighted patterns.
                // PropertyOverridePattern is used directly to avoid import issues with .withWeight extension
                patterns.add(PropertyOverridePattern(fillAtom, weightOverride = duration))
            }

            // SequencePattern automatically squeezes the sequence into 1 cycle allocating time proportional to weights
            val structure = SequencePattern(patterns)

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
            if (steps <= 0) return emptyList()

            // Calculate base pattern
            val basePattern = if (k >= steps) {
                List(steps) { 1 }
            } else {
                val offs = steps - k

                val ones = List(k) { listOf(1) }
                val zeros = List(offs) { listOf(0) }

                val result = recursiveBjorklund(k, offs, ones, zeros)
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

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: QueryContext,
    ): List<StrudelPatternEvent> {
        val pulsesEvents = pulsesProvider.queryEvents(from, to, ctx)
        val stepsEvents = stepsProvider.queryEvents(from, to, ctx)

        val rotationEvents = rotationProvider?.queryEvents(from, to, ctx)
            ?: run {
                val timeSpan = TimeSpan(begin = from, end = to)

                listOf(
                    StrudelPatternEvent(
                        part = timeSpan,
                        whole = timeSpan,
                        data = StrudelVoiceData.empty.copy(
                            value = StrudelVoiceValue.Num(0.0),
                        )
                    )
                )
            }

        if (pulsesEvents.isEmpty() || stepsEvents.isEmpty() || rotationEvents.isEmpty()) {
            return emptyList()
        }

        val result = createEventList()

        // Find all overlapping combinations
        for (pulsesEvent in pulsesEvents) {
            for (stepsEvent in stepsEvents) {
                for (rotationEvent in rotationEvents) {
                    val overlapBegin: Rational =
                        maxOf(pulsesEvent.part.begin, stepsEvent.part.begin, rotationEvent.part.begin)

                    val overlapEnd: Rational =
                        minOf(pulsesEvent.part.end, stepsEvent.part.end, rotationEvent.part.end)

                    if (overlapEnd <= overlapBegin) continue

                    val pulses = pulsesEvent.data.value?.asInt ?: 0
                    val steps = stepsEvent.data.value?.asInt ?: 0
                    val rotation = rotationEvent.data.value?.asInt ?: 0

                    val events: List<StrudelPatternEvent> =
                        applyEuclideanRhythm(overlapBegin, overlapEnd, ctx, pulses, steps, rotation)

                    result.addAll(events)
                }
            }
        }

        return result
    }

    private fun applyEuclideanRhythm(
        from: Rational,
        to: Rational,
        ctx: QueryContext,
        pulses: Int,
        steps: Int,
        rotation: Int,
    ): List<StrudelPatternEvent> {
        return if (legato) {
            queryLegatoStatic(from, to, ctx, pulses, steps, rotation)
        } else {
            queryStatic(from, to, ctx, pulses, steps, rotation)
        }
    }

    private fun queryStatic(
        from: Rational,
        to: Rational,
        ctx: QueryContext,
        pulses: Int,
        steps: Int,
        rotation: Int,
    ): List<StrudelPatternEvent> {
        if (steps <= 0 || steps < abs(pulses)) return emptyList()

        val events = createEventList()
        val rhythm = rotateJs(bjorklundStrudel(pulses, steps), -rotation)

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
                            .sortedBy { it.part.begin }
                            .take(1)

                        events.addAll(innerEvents.map { ev ->
                            val timeSpan = TimeSpan(begin = intersectStart, end = intersectEnd)

                            ev.copy(
                                part = timeSpan,
                                whole = timeSpan  // Each Euclidean hit is independent
                            )
                        })
                    }
                }
            }
        }

        return events
    }

    private fun queryLegatoStatic(
        from: Rational,
        to: Rational,
        ctx: QueryContext,
        pulses: Int,
        steps: Int,
        rotation: Int,
    ): List<StrudelPatternEvent> {
        val legatoPattern = createLegatoStatic(inner, pulses, steps, rotation)
        return legatoPattern.queryArcContextual(from, to, ctx)
    }
}
