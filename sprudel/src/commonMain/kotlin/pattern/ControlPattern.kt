package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.SprudelVoiceData
import io.peekandpoke.klang.sprudel._applyControl

/**
 * Applies a control pattern to a source pattern.
 *
 * @param source The pattern defining the rhythm/structure (e.g. note("..."))
 * @param control The pattern defining the values (e.g. seq(0.5, 1.0))
 * @param mapper additional mapper from SprudelVoiceData to SprudelVoiceData
 * @param combiner Function to merge the source SprudelVoiceData with the control event's SprudelVoiceData
 */
internal class ControlPattern(
    val source: SprudelPattern,
    val control: SprudelPattern,
    val mapper: (SprudelVoiceData) -> SprudelVoiceData,
    val combiner: (SprudelVoiceData, SprudelVoiceData) -> SprudelVoiceData,
) : SprudelPattern {

    // Control patterns wrap a source pattern and should preserve its weight.
    // E.g. (bd@2).gain(0.5) should still have a weight of 2.
    override val weight: Double get() = source.weight

    override val numSteps: Rational? get() = source.numSteps

    override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<SprudelPatternEvent> {
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
