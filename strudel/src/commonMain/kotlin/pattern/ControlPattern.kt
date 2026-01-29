package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel._applyControl
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Applies a control pattern to a source pattern.
 *
 * @param source The pattern defining the rhythm/structure (e.g. note("..."))
 * @param control The pattern defining the values (e.g. seq(0.5, 1.0))
 * @param mapper additional mapper from StrudelVoiceData to StrudelVoiceData
 * @param combiner Function to merge the source StrudelVoiceData with the control event's StrudelVoiceData
 */
internal class ControlPattern(
    val source: StrudelPattern,
    val control: StrudelPattern,
    val mapper: (StrudelVoiceData) -> StrudelVoiceData,
    val combiner: (StrudelVoiceData, StrudelVoiceData) -> StrudelVoiceData,
) : StrudelPattern {

    // Control patterns wrap a source pattern and should preserve its weight.
    // E.g. (bd@2).gain(0.5) should still have a weight of 2.
    override val weight: Double get() = source.weight

    override val numSteps: Rational? get() = source.numSteps

    override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        return source._applyControl(control, from, to, ctx) { src, ctrl ->
            if (ctrl != null) {
                val mappedControl = mapper(ctrl.data)
                val newData = combiner(src.data, mappedControl)
                src.copy(data = newData).prependLocation(ctrl.sourceLocations?.innermost)
            } else {
                src
            }
        }
    }
}
