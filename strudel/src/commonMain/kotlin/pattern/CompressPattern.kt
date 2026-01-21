package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.StrudelVoiceValue
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational
import kotlin.math.floor

/**
 * Compresses a pattern into a specific timespan within each cycle.
 *
 * For example, compress(0.25, 0.75) will compress the pattern to play only
 * in the middle half of each cycle, leaving the first and last quarters silent.
 *
 * For static values, applies uniform compression across the entire pattern.
 * For control patterns, pairs up overlapping start/end events and compresses each segment.
 *
 * @param source The source pattern to compress
 * @param startProvider Control value provider for the start position (0.0 to 1.0)
 * @param endProvider Control value provider for the end position (0.0 to 1.0)
 */
internal class CompressPattern(
    val source: StrudelPattern,
    val startProvider: ControlValueProvider,
    val endProvider: ControlValueProvider,
) : StrudelPattern {
    companion object {
        /**
         * Create a CompressPattern with static start and end values.
         */
        fun static(source: StrudelPattern, start: Rational, end: Rational): CompressPattern {
            return CompressPattern(
                source = source,
                startProvider = ControlValueProvider.Static(StrudelVoiceValue.Num(start.toDouble())),
                endProvider = ControlValueProvider.Static(StrudelVoiceValue.Num(end.toDouble()))
            )
        }

        /**
         * Create a CompressPattern with control patterns for start and end.
         */
        fun control(
            source: StrudelPattern,
            startPattern: StrudelPattern,
            endPattern: StrudelPattern,
        ): CompressPattern {
            return CompressPattern(
                source = source,
                startProvider = ControlValueProvider.Pattern(startPattern),
                endProvider = ControlValueProvider.Pattern(endPattern)
            )
        }
    }

    override val weight: Double get() = source.weight

    override val steps: Rational? get() = source.steps

    override fun estimateCycleDuration(): Rational {
        return source.estimateCycleDuration()
    }

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        // For static values, we can optimize with a single query
        if (startProvider is ControlValueProvider.Static && endProvider is ControlValueProvider.Static) {
            val start = (startProvider.value.asDouble ?: 0.0).toRational()
            val end = (endProvider.value.asDouble ?: 1.0).toRational()
            return queryWithStaticValues(from, to, ctx, start, end)
        }

        // At least one is a pattern - create patterns for both sides
        val startPattern = when (startProvider) {
            is ControlValueProvider.Static -> {
                // Convert static value to a constant pattern
                val value = startProvider.value.asDouble ?: 0.0
                AtomicPattern(StrudelVoiceData.empty.copy(value = StrudelVoiceValue.Num(value)))
            }

            is ControlValueProvider.Pattern -> startProvider.pattern
        }

        val endPattern = when (endProvider) {
            is ControlValueProvider.Static -> {
                // Convert static value to a constant pattern
                val value = endProvider.value.asDouble ?: 1.0
                AtomicPattern(StrudelVoiceData.empty.copy(value = StrudelVoiceValue.Num(value)))
            }

            is ControlValueProvider.Pattern -> endProvider.pattern
        }

        return queryWithControlPatterns(from, to, ctx, startPattern, endPattern)
    }

    private fun queryWithStaticValues(
        from: Rational,
        to: Rational,
        ctx: QueryContext,
        start: Rational,
        end: Rational,
    ): List<StrudelPatternEvent> {
        val span = end - start

        // Handle edge case where span is zero or negative
        if (span <= Rational.ZERO) {
            return emptyList()
        }

        val result = mutableListOf<StrudelPatternEvent>()

        // Determine which cycles we need to query
        val cycleStart = floor(from.toDouble()).toLong()
        val cycleEnd = floor(to.toDouble()).toLong()

        for (cycle in cycleStart..cycleEnd) {
            val cycleRat = cycle.toRational()

            // The compressed region in this cycle
            val compressedStart = cycleRat + start
            val compressedEnd = cycleRat + end

            // Check if our query range intersects with the compressed region
            if (to <= compressedStart || from >= compressedEnd) {
                continue // No intersection
            }

            // Query the source pattern for the full cycle (0 to 1)
            // We need to map the entire cycle content into our compressed region
            val sourceEvents = source.queryArcContextual(cycleRat, cycleRat + Rational.ONE, ctx)

            // Map each event from source cycle [0,1) to compressed region [start,end)
            for (ev in sourceEvents) {
                // Map event times from [cycle, cycle+1) to [compressedStart, compressedEnd)
                val relativeBegin = ev.begin - cycleRat // Position within source cycle [0,1)
                val relativeEnd = ev.end - cycleRat

                val mappedBegin = compressedStart + (relativeBegin * span)
                val mappedEnd = compressedStart + (relativeEnd * span)
                val mappedDur = ev.dur * span

                // Only include if it intersects with our query range
                if (mappedEnd > from && mappedBegin < to) {
                    result.add(
                        ev.copy(
                            begin = mappedBegin,
                            end = mappedEnd,
                            dur = mappedDur
                        )
                    )
                }
            }
        }

        return result
    }

    private fun queryWithControlPatterns(
        from: Rational,
        to: Rational,
        ctx: QueryContext,
        startPattern: StrudelPattern,
        endPattern: StrudelPattern,
    ): List<StrudelPatternEvent> {
        val startEvents = startPattern.queryArcContextual(from, to, ctx)
        val endEvents = endPattern.queryArcContextual(from, to, ctx)

        if (startEvents.isEmpty() || endEvents.isEmpty()) return emptyList()

        val result = mutableListOf<StrudelPatternEvent>()

        // Pair up start and end events by their timespans
        for (startEvent in startEvents) {
            for (endEvent in endEvents) {
                // Check if events overlap
                if (startEvent.end <= endEvent.begin || endEvent.end <= startEvent.begin) continue

                val start = startEvent.data.value?.asDouble ?: 0.0
                val end = endEvent.data.value?.asDouble ?: 1.0

                if (start >= end) continue

                // Calculate the overlap timespan
                val overlapBegin = maxOf(startEvent.begin, endEvent.begin)
                val overlapEnd = minOf(startEvent.end, endEvent.end)

                // Apply compress with these start/end values for this segment
                val events = queryWithStaticValues(
                    overlapBegin,
                    overlapEnd,
                    ctx,
                    start.toRational(),
                    end.toRational()
                )
                result.addAll(events)
            }
        }

        return result
    }
}
