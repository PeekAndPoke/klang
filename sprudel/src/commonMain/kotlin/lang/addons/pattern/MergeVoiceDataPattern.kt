package io.peekandpoke.klang.strudel.lang.addons.pattern

import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.sampleAt

/**
 * Merges voice data from a control pattern into the source pattern's events (Outer Join semantics).
 *
 * For each event in the source pattern, the control pattern is sampled at the event's onset time.
 * If the control pattern has an event at that time, the source event's voice data is merged with
 * the control event's voice data via [StrudelVoiceData.merge].
 *
 * Events for which the control pattern has no sample are dropped.
 */
internal class MergePattern(
    val source: StrudelPattern,
    val control: StrudelPattern,
) : StrudelPattern {
    override val weight: Double get() = source.weight
    override val numSteps: Rational? get() = source.numSteps
    override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val sourceEvents = source.queryArcContextual(from, to, ctx)
        if (sourceEvents.isEmpty()) return emptyList()

        val result = mutableListOf<StrudelPatternEvent>()

        for (srcEvt in sourceEvents) {
            val sampleTime = srcEvt.whole.begin

            val toAdd = when (val ctrlEvt = control.sampleAt(sampleTime, ctx)) {
                null -> srcEvt
                else -> srcEvt.copy(
                    data = srcEvt.data.merge(ctrlEvt.data),
                ).prependLocations(ctrlEvt.sourceLocations)
            }

            result.add(toAdd)
        }

        return result
    }
}
