package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.StrudelVoiceValue
import io.peekandpoke.klang.strudel.lang.early
import io.peekandpoke.klang.strudel.lang.fast
import io.peekandpoke.klang.strudel.lang.late
import io.peekandpoke.klang.strudel.lang.silence
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

/**
 * A pattern that focuses on a portion of each cycle, keeping the original timing.
 *
 * Implemented as: pattern.early(start).fast(1/(end-start)).late(start)
 * This speeds up the pattern and shifts it so only the desired portion is visible.
 *
 * For static values, pre-computes the transformation for optimal performance.
 * For control patterns, applies the transformation for each overlapping start/end pair.
 *
 * @param source The pattern to focus
 * @param startProvider Control value provider for the start position (0.0 to 1.0)
 * @param endProvider Control value provider for the end position (0.0 to 1.0)
 */
internal class FocusPattern(
    val source: StrudelPattern,
    val startProvider: ControlValueProvider,
    val endProvider: ControlValueProvider,
) : StrudelPattern {
    companion object {
        /**
         * Create a FocusPattern with static start and end values.
         */
        fun static(source: StrudelPattern, start: Rational, end: Rational): FocusPattern {
            return FocusPattern(
                source = source,
                startProvider = ControlValueProvider.Static(StrudelVoiceValue.Num(start.toDouble())),
                endProvider = ControlValueProvider.Static(StrudelVoiceValue.Num(end.toDouble()))
            )
        }

        /**
         * Create a FocusPattern with control patterns for start and end.
         */
        fun control(
            source: StrudelPattern,
            startPattern: StrudelPattern,
            endPattern: StrudelPattern,
        ): FocusPattern {
            return FocusPattern(
                source = source,
                startProvider = ControlValueProvider.Pattern(startPattern),
                endProvider = ControlValueProvider.Pattern(endPattern)
            )
        }
    }

    override val weight: Double get() = source.weight

    override val steps: Rational? get() = source.steps

    // Optimization: For pure static values, pre-compute the transformation
    private val staticTransformed: StrudelPattern? by lazy {
        if (startProvider is ControlValueProvider.Static && endProvider is ControlValueProvider.Static) {
            val start = (startProvider.value.asDouble ?: 0.0).toRational()
            val end = (endProvider.value.asDouble ?: 1.0).toRational()

            when {
                start >= end -> silence
                else -> source.early(start.floor())
                    .fast(Rational.ONE / (end - start))
                    .late(start)
            }
        } else {
            null
        }
    }

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        // For static values, use pre-computed transformation
        staticTransformed?.let { return it.queryArcContextual(from, to, ctx) }

        val startEvents = startProvider.queryEvents(from, to, ctx)
        val endEvents = endProvider.queryEvents(from, to, ctx)

        if (startEvents.isEmpty() || endEvents.isEmpty()) return emptyList()

        val result = createEventList()

        // Pair up start and end events by their overlapping timespans
        for (startEvent in startEvents) {
            for (endEvent in endEvents) {
                val start = (startEvent.data.value?.asDouble ?: 0.0).toRational()
                val end = (endEvent.data.value?.asDouble ?: 1.0).toRational()

                if (start >= end) continue

                // Calculate the overlap timespan
                val overlapRange = calculateOverlapRange(
                    startEvent.begin, startEvent.end,
                    endEvent.begin, endEvent.end
                ) ?: continue

                // Apply focus: early(start.floor()).fast(1/(end-start)).late(start)
                val focused = source.early(start.floor())
                    .fast(Rational.ONE / (end - start))
                    .late(start)

                val events = focused.queryArcContextual(overlapRange.first, overlapRange.second, ctx)
                result.addAll(events)
            }
        }

        return result
    }
}
