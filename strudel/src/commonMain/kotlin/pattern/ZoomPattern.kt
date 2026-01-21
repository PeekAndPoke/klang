package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.StrudelVoiceValue
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

/**
 * A pattern that zooms into portions of the source pattern.
 *
 * Zoom works by shifting the pattern earlier and then speeding it up.
 * Implemented as: pattern.early(start).fast(end - start)
 *
 * For static values, pre-computes the transformation for optimal performance.
 * For control patterns, applies the transformation for each overlapping start/end pair.
 *
 * @param inner The pattern to zoom into
 * @param startProvider Control value provider for the start position (0.0 to 1.0)
 * @param endProvider Control value provider for the end position (0.0 to 1.0)
 */
internal class ZoomPattern(
    val inner: StrudelPattern,
    val startProvider: ControlValueProvider,
    val endProvider: ControlValueProvider,
) : StrudelPattern {
    companion object {
        /**
         * Create a ZoomPattern with static start and end values.
         */
        fun static(inner: StrudelPattern, start: Rational, end: Rational): ZoomPattern {
            return ZoomPattern(
                inner = inner,
                startProvider = ControlValueProvider.Static(StrudelVoiceValue.Num(start.toDouble())),
                endProvider = ControlValueProvider.Static(StrudelVoiceValue.Num(end.toDouble()))
            )
        }

        /**
         * Create a ZoomPattern with control patterns for start and end.
         */
        fun control(
            inner: StrudelPattern,
            startPattern: StrudelPattern,
            endPattern: StrudelPattern,
        ): ZoomPattern {
            return ZoomPattern(
                inner = inner,
                startProvider = ControlValueProvider.Pattern(startPattern),
                endProvider = ControlValueProvider.Pattern(endPattern)
            )
        }
    }

    override val weight: Double get() = inner.weight

    override val steps: Rational? get() = inner.steps

    // For static values, pre-compute the transformation
    private val staticTransformed: StrudelPattern? by lazy {
        if (startProvider is ControlValueProvider.Static && endProvider is ControlValueProvider.Static) {
            val start = (startProvider.value.asDouble ?: 0.0).toRational()
            val end = (endProvider.value.asDouble ?: 1.0).toRational()
            val duration = end - start

            if (duration.toDouble() <= 0.0) {
                null // Invalid range
            } else {
                // Apply zoom: early(start).fast(duration)
                val shifted = TimeShiftPattern.static(source = inner, offset = start * Rational.MINUS_ONE)
                TempoModifierPattern.static(shifted, factor = duration, invertPattern = true)
            }
        } else {
            null
        }
    }

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        // For static values, use pre-computed transformation
        staticTransformed?.let { return it.queryArcContextual(from, to, ctx) }

        // At least one parameter is a pattern - sample values and find overlaps
        val startEvents = when (startProvider) {
            is ControlValueProvider.Static -> {
                listOf(
                    StrudelPatternEvent(
                        begin = from,
                        end = to,
                        dur = to - from,
                        data = StrudelVoiceData.empty.copy(value = startProvider.value)
                    )
                )
            }

            is ControlValueProvider.Pattern -> startProvider.pattern.queryArcContextual(from, to, ctx)
        }

        val endEvents = when (endProvider) {
            is ControlValueProvider.Static -> {
                listOf(
                    StrudelPatternEvent(
                        begin = from,
                        end = to,
                        dur = to - from,
                        data = StrudelVoiceData.empty.copy(value = endProvider.value)
                    )
                )
            }

            is ControlValueProvider.Pattern -> endProvider.pattern.queryArcContextual(from, to, ctx)
        }

        if (startEvents.isEmpty() || endEvents.isEmpty()) return emptyList()

        val result = mutableListOf<StrudelPatternEvent>()

        // Pair up start and end events by their overlapping timespans
        for (startEvent in startEvents) {
            for (endEvent in endEvents) {
                // Check if events overlap
                if (startEvent.end <= endEvent.begin || endEvent.end <= startEvent.begin) continue

                val start = startEvent.data.value?.asDouble ?: 0.0
                val end = endEvent.data.value?.asDouble ?: 1.0
                val duration = end - start

                if (duration <= 0.0) continue

                // Calculate the overlap timespan
                val overlapBegin = maxOf(startEvent.begin, endEvent.begin)
                val overlapEnd = minOf(startEvent.end, endEvent.end)

                // Apply zoom: early(start).fast(duration)
                val zoomed = TimeShiftPattern.static(source = inner, offset = start.toRational() * Rational.MINUS_ONE)
                val final = TempoModifierPattern.static(zoomed, factor = duration.toRational(), invertPattern = true)
                val events = final.queryArcContextual(overlapBegin, overlapEnd, ctx)

                result.addAll(events)
            }
        }

        return result
    }
}
