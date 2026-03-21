package io.peekandpoke.klang.sprudel.lang.addons.pattern

import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.sprudel.*

/**
 * Pattern that applies solo to each event and fills silent gaps so the backend
 * knows the pattern is still alive in solo mode.
 *
 * For every query window `[from, to]`:
 * 1. Source events are decorated with the solo value sampled at `event.whole.begin`.
 * 2. Every gap between events (and at the leading / trailing edges of the window) is
 *    filled with a silent "sine" event at gain 0 carrying the same solo flag and the
 *    same `patternId` as the real events.  This prevents the backend from dropping the
 *    pattern from solo tracking between notes.
 */
class SoloPattern(
    private val source: StrudelPattern,
    private val soloControl: StrudelPattern,
) : StrudelPattern {

    companion object {
        private var counter = 0
        private fun nextId() = "solo-${++counter}"
    }

    /** Stable fallback id used when source events carry no patternId of their own. */
    private val fallbackPatternId = nextId()

    override val weight get() = source.weight
    override val numSteps get() = source.numSteps
    override fun estimateCycleDuration() = source.estimateCycleDuration()

    private var patternId: String? = null

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: StrudelPattern.QueryContext,
    ): List<StrudelPatternEvent> {
        // 1. Query and sort source events by visible start time
        val sourceEvents = source.queryArcContextual(from, to, ctx)
            .sortedBy { it.part.begin }

        // 2. Derive a stable patternId: prefer the one already on source events
        patternId = sourceEvents.firstOrNull()?.data?.patternId ?: patternId ?: fallbackPatternId

        val result = mutableListOf<StrudelPatternEvent>()
        var cursor = from

        // Sample the solo control pattern at the given time
        fun soloSampleAt(time: Rational): StrudelPatternEvent? = soloControl.sampleAt(time, ctx)

        // Silent filler event: keeps the backend's solo-tracker alive during rests
        fun filler(start: Rational, end: Rational, evt: StrudelPatternEvent?): StrudelPatternEvent {
            val span = TimeSpan(start, end)
            return StrudelPatternEvent(
                part = span,
                whole = span, // whole == part → isOnset = true, so the backend picks it up
                data = StrudelVoiceData.empty.copy(
                    note = "a",
                    freqHz = 440.0,
                    sound = "sine",
                    gain = 0.000001,
                    solo = evt?.data?.value?.asDouble?.coerceIn(0.0, 1.0),
                    patternId = patternId,
                ),
            ).prependLocations(evt?.sourceLocations)
        }

        for (event in sourceEvents) {
            val evStart = event.part.begin

            // Fill any gap before this event
            if (evStart > cursor) {
                result.add(
                    filler(start = cursor, end = evStart, evt = soloSampleAt(evStart))
                )
            }

            // Decorate the event with the solo value sampled at its onset time
            val solo = soloSampleAt(event.whole.begin)

            result.add(
                event.copy(
                    data = event.data.copy(
                        solo = solo?.data?.value?.asDouble?.coerceIn(0.0, 1.0),
                        patternId = patternId,
                    )
                ).prependLocations(solo?.sourceLocations),
            )

            cursor = maxOf(cursor, event.part.end)
        }

        // Fill any trailing gap after the last event
        if (cursor < to) {
            result.add(
                filler(start = cursor, end = to, evt = soloSampleAt(cursor))
            )
        }

        return result
    }
}
