package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.StrudelVoiceValue
import io.peekandpoke.klang.strudel.lang.fast
import io.peekandpoke.klang.strudel.lang.late
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

/**
 * A pattern that slices the source into n parts and plays them according to indices.
 *
 * For each slice index, calculates slice boundaries and plays that portion of the source.
 * Implemented as zoom logic: early(start).fast(duration).late(position)
 *
 * For static values, pre-computes the slices for optimal performance.
 * For control patterns, pairs up overlapping n and index events.
 *
 * @param source The pattern to slice
 * @param nProvider Control value provider for the number of slices
 * @param indicesProvider Control value provider for which slice indices to play
 */
internal class BitePattern(
    val source: StrudelPattern,
    val nProvider: ControlValueProvider,
    val indicesProvider: ControlValueProvider,
) : StrudelPattern {
    companion object {
        /**
         * Create a BitePattern with static n and indices values.
         */
        fun static(source: StrudelPattern, n: Int, indices: Int): BitePattern {
            return BitePattern(
                source = source,
                nProvider = ControlValueProvider.Static(StrudelVoiceValue.Num(n.toDouble())),
                indicesProvider = ControlValueProvider.Static(StrudelVoiceValue.Num(indices.toDouble()))
            )
        }

        /**
         * Create a BitePattern with control patterns for n and indices.
         */
        fun control(
            source: StrudelPattern,
            nPattern: StrudelPattern,
            indicesPattern: StrudelPattern,
        ): BitePattern {
            return BitePattern(
                source = source,
                nProvider = ControlValueProvider.Pattern(nPattern),
                indicesProvider = ControlValueProvider.Pattern(indicesPattern)
            )
        }
    }

    override val weight: Double get() = source.weight

    override val steps: Rational? get() = source.steps

    override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        // For static values, we can optimize
        if (nProvider is ControlValueProvider.Static && indicesProvider is ControlValueProvider.Static) {
            val n = nProvider.value.asInt ?: 4
            val idx = indicesProvider.value.asInt ?: 0
            return queryWithStaticValues(from, to, ctx, n, idx)
        }

        // At least one is a pattern - create patterns for both sides
        val nPattern = when (nProvider) {
            is ControlValueProvider.Static -> {
                val value = nProvider.value.asInt ?: 4
                AtomicPattern(StrudelVoiceData.empty.copy(value = StrudelVoiceValue.Num(value.toDouble())))
            }

            is ControlValueProvider.Pattern -> nProvider.pattern
        }

        val indicesPattern = when (indicesProvider) {
            is ControlValueProvider.Static -> {
                val value = indicesProvider.value.asInt ?: 0
                AtomicPattern(StrudelVoiceData.empty.copy(value = StrudelVoiceValue.Num(value.toDouble())))
            }

            is ControlValueProvider.Pattern -> indicesProvider.pattern
        }

        return queryWithControlPatterns(from, to, ctx, nPattern, indicesPattern)
    }

    private fun queryWithStaticValues(
        from: Rational,
        to: Rational,
        ctx: QueryContext,
        n: Int,
        idx: Int,
    ): List<StrudelPatternEvent> {
        if (n <= 0) return emptyList()

        // Handle wrapping like JS .mod(1)
        val normalizedIdx = idx.mod(n)
        val safeIdx = if (normalizedIdx < 0) normalizedIdx + n else normalizedIdx

        val start = safeIdx.toDouble() / n
        val end = (safeIdx + 1.0) / n

        // Get the slice using zoom logic: early(start).fast(end - start)
        val duration = end - start
        if (duration <= 0.0) return emptyList()

        val slice = TempoModifierPattern.static(
            source = TimeShiftPattern.static(
                source = source,
                offset = start.toRational() * Rational.MINUS_ONE,
            ),
            factor = duration.toRational(),
            invertPattern = true,
        )

        return slice.queryArcContextual(from, to, ctx)
    }

    private fun queryWithControlPatterns(
        from: Rational,
        to: Rational,
        ctx: QueryContext,
        nPattern: StrudelPattern,
        indicesPattern: StrudelPattern,
    ): List<StrudelPatternEvent> {
        val nEvents = nPattern.queryArcContextual(from, to, ctx)
        val indexEvents = indicesPattern.queryArcContextual(from, to, ctx)

        if (nEvents.isEmpty() || indexEvents.isEmpty()) return emptyList()

        val result = mutableListOf<StrudelPatternEvent>()

        // Pair up n events with index events by their overlapping timespans
        for (nEvent in nEvents) {
            for (indexEvent in indexEvents) {
                // Check if events overlap
                if (nEvent.end <= indexEvent.begin || indexEvent.end <= nEvent.begin) continue

                val n = nEvent.data.value?.asInt ?: 4
                if (n <= 0) continue

                val idx = indexEvent.data.value?.asInt ?: 0

                // Handle wrapping like JS .mod(1)
                val normalizedIdx = idx.mod(n)
                val safeIdx = if (normalizedIdx < 0) normalizedIdx + n else normalizedIdx

                val start = safeIdx.toDouble() / n
                val end = (safeIdx + 1.0) / n

                // Calculate the overlap timespan
                val overlapBegin = maxOf(nEvent.begin, indexEvent.begin)
                val overlapEnd = minOf(nEvent.end, indexEvent.end)

                // Get the slice using zoom logic: early(start).fast(end - start)
                val duration = end - start
                if (duration <= 0.0) continue

                val slice = TempoModifierPattern.static(
                    source = TimeShiftPattern.static(
                        source = source,
                        offset = start.toRational() * Rational.MINUS_ONE,
                    ),
                    factor = duration.toRational(),
                    invertPattern = true,
                )

                val dur = (overlapEnd - overlapBegin).toDouble()
                if (dur <= 0.0) continue

                // Fit and position the slice
                val fitted = slice.fast(1.0 / dur).late(overlapBegin.toDouble())

                val events = fitted.queryArcContextual(overlapBegin, overlapEnd, ctx)
                result.addAll(events)
            }
        }

        return result
    }
}
